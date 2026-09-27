package com.theveloper.pixelplay.data

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one feedback store every mix surface reads (continuous mix, queue mix buttons, Your Music
 * vibe mixes). Rules stay independent of ranking; rejecting a track never rejects its artist.
 *
 * What it holds:
 * - **Not for this mix** (session): memory only, forgotten when a new mix session starts.
 * - **Exclude everywhere** (global): no automatic pick ever uses the song, until undone.
 * - **Heard too much** (snooze): excluded from automatic picks for [SNOOZE_MS], then back.
 * - **Removed from the queue**: a soft penalty on the song and, a little, its artist, fading
 *   out over [REMOVAL_MS]. The current session also steers away through [AdaptiveMix].
 * - Legacy per-flavour exclusions (`mix:normal` / `mix:smart`) from before the session scope.
 *
 * Persisted in the mix learning database (table `feedback`); the older SharedPreferences sets
 * are imported once. Reads are served from memory, so they are cheap on any thread; writes go
 * to the database in order on a background queue. Every entry can be undone on its own.
 */
@Singleton
class MixFeedback @Inject constructor(
    @ApplicationContext context: Context,
    private val gatherer: com.theveloper.pixelplay.data.metadata.SongMetadataGatherer,
    private val learning: MixLearning
) {
    enum class Kind { EXCLUDE, SNOOZE, REMOVE }

    /** One row of the feedback history shown in Tune this mix, newest first. */
    data class LogItem(val id: String, val title: String, val artist: String, val label: String, val at: Long)

    private val preferences = context.getSharedPreferences("mix_feedback", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<suspend (MixFeedbackDao) -> Unit>(Channel.UNLIMITED)
    private val ready = CompletableDeferred<Unit>()

    /** Saved entries, newest first. */
    private val entries = ArrayList<MixFeedbackEntry>()
    /** "Not for this mix" entries of the current session (never saved). */
    private val session = ArrayList<MixFeedbackEntry>()
    /** Entry ids in the order they were made, for "Undo last". */
    private val undoStack = ArrayDeque<String>()
    /** Active exclusions and snoozes by scope ("global", "mix:normal", …) → aliases. */
    private var excluded: Map<String, Set<String>> = emptyMap()
    private var sessionAliases: Set<String> = emptySet()
    private var removals: List<MixFeedbackEntry> = emptyList()
    private var sessionStartedAt = System.currentTimeMillis()

    private val _log = MutableStateFlow<List<LogItem>>(emptyList())
    /** Recent feedback for the Tune this mix sheet (each item can be undone). */
    val log: StateFlow<List<LogItem>> = _log.asStateFlow()

    @Volatile var revision: Long = 0
        private set
    @Volatile var activeMixId: String = "normal"

    init {
        scope.launch {
            for (write in writes) {
                try { write(learning.feedbackDao()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Timber.w(error, "Mix feedback write failed") }
            }
        }
        scope.launch { load() }
    }

    /** Waits (briefly) until saved feedback is loaded, so a first plan can't miss exclusions. */
    suspend fun awaitReady() { withTimeoutOrNull(2_000) { ready.await() } }

    private suspend fun load() {
        try {
            val dao = learning.feedbackDao()
            val now = System.currentTimeMillis()
            dao.deleteExpired(now)
            val legacy = legacyEntries(now)
            if (legacy.isNotEmpty()) {
                dao.saveAll(legacy)
                val editor = preferences.edit()
                preferences.all.keys.filter { it !in SETTING_KEYS }.forEach { editor.remove(it) }
                editor.apply()
            }
            val stored = dao.all()
            synchronized(this) {
                // Anything recorded while loading is kept (its write is queued behind this).
                val merged = (entries + stored).distinctBy { it.id }.sortedByDescending { it.createdAt }
                entries.clear()
                entries.addAll(merged)
                reindex()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.w(error, "Could not load mix feedback")
        } finally {
            ready.complete(Unit)
        }
    }

    /** The SharedPreferences sets used before v3, as entries (one per stored name). */
    private fun legacyEntries(now: Long): List<MixFeedbackEntry> {
        val out = ArrayList<MixFeedbackEntry>()
        preferences.all.forEach { (key, value) ->
            if (key in SETTING_KEYS) return@forEach
            val names = (value as? Set<*>)?.filterIsInstance<String>().orEmpty()
            names.forEach { name ->
                when {
                    key == "fatigue" -> {
                        val at = name.substringBefore('|').toLongOrNull()
                        if (at != null && at + SNOOZE_MS > now) {
                            out += legacyEntry(Kind.SNOOZE, "global", name.substringAfter('|'), at, at + SNOOZE_MS)
                        }
                    }
                    key == "rejected" -> out += legacyEntry(Kind.EXCLUDE, "global", name, 0L, null)
                    key.startsWith("mix:") -> out += legacyEntry(Kind.EXCLUDE, key, name, 0L, null)
                }
            }
        }
        return out
    }

    private fun legacyEntry(kind: Kind, scope: String, alias: String, at: Long, expiresAt: Long?): MixFeedbackEntry {
        val plain = if (alias.startsWith("rec:")) alias.removePrefix("rec:") else alias
        val pair = '|' in plain && !plain.startsWith("isrc:")
        val title = if (pair) plain.substringBefore('|') else plain
        val artist = if (pair) plain.substringAfter('|') else ""
        return MixFeedbackEntry(java.util.UUID.randomUUID().toString(), kind.name, scope, alias, "", title, artist, null, at, expiresAt)
    }

    /**
     * Every name the same recording goes by: the mix identity (ISRC or title|artist), the
     * YouTube id, and the cleaned title|artist the metadata gatherer uses, so an
     * "(Official Video)" upload and the album track count as one song.
     */
    private fun aliases(song: Song) = buildSet {
        add(mixIdentity(song))
        song.youtubeId?.let { add("youtube:$it") }
        gatherer.recordingKey(song).takeIf { it.length > 1 }?.let { add("rec:$it") }
    }

    private fun MixFeedbackEntry.names(): List<String> = aliases.split('\n')

    private fun newEntry(song: Song, kind: Kind, scope: String, expiresAt: Long?) = MixFeedbackEntry(
        id = java.util.UUID.randomUUID().toString(), kind = kind.name, scope = scope,
        aliases = aliases(song).joinToString("\n"), songId = song.id, title = song.title,
        artist = song.artist, genre = song.genre, createdAt = System.currentTimeMillis(), expiresAt = expiresAt
    )

    /** Rebuilds the lookup sets and the history list. Call with the monitor held. */
    private fun reindex() {
        val now = System.currentTimeMillis()
        val byScope = HashMap<String, HashSet<String>>()
        entries.forEach { entry ->
            if (entry.kind == Kind.REMOVE.name) return@forEach
            val expiry = entry.expiresAt
            if (expiry != null && expiry <= now) return@forEach
            byScope.getOrPut(entry.scope) { HashSet() }.addAll(entry.names())
        }
        excluded = byScope
        sessionAliases = session.flatMapTo(HashSet()) { it.names() }
        removals = entries.filter { it.kind == Kind.REMOVE.name && (it.expiresAt ?: Long.MAX_VALUE) > now }
        _log.value = (session + entries).sortedByDescending { it.createdAt }.take(MAX_LOG).map(::logItem)
        revision++
    }

    private fun logItem(entry: MixFeedbackEntry): LogItem {
        val label = when {
            entry.scope == SESSION -> "Not in this mix"
            entry.kind == Kind.SNOOZE.name -> "Snoozed until " + (entry.expiresAt?.let {
                java.text.SimpleDateFormat("d MMM", Locale.getDefault()).format(java.util.Date(it))
            } ?: "later")
            entry.kind == Kind.REMOVE.name -> "Removed from the queue"
            entry.scope == "global" -> "Excluded from mixes"
            else -> "Excluded from " + entry.scope.removePrefix("mix:").replaceFirstChar { it.titlecase(Locale.ROOT) } + " Mix"
        }
        return LogItem(entry.id, entry.title, entry.artist, label, entry.createdAt)
    }

    private fun save(entry: MixFeedbackEntry) {
        entries.add(0, entry)
        undoStack.addLast(entry.id)
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        writes.trySend { it.save(entry) }
        reindex()
    }

    @Synchronized fun invalidate() { revision++ }

    /** A new mix session: "Not for this mix" is forgotten; removals so far become history. */
    @Synchronized fun startSession() {
        sessionStartedAt = System.currentTimeMillis()
        val ids = session.map { it.id }.toSet()
        session.clear()
        undoStack.removeAll { it in ids }
        reindex()
    }

    /** "Not for this mix": excluded for the rest of this mix session only. */
    @Synchronized fun dislike(song: Song) {
        val entry = newEntry(song, Kind.EXCLUDE, SESSION, null)
        session.add(0, entry)
        undoStack.addLast(entry.id)
        reindex()
    }

    /** null = "Exclude everywhere" (global). A mix id writes a per-flavour rule. */
    @Synchronized fun dislike(song: Song, mixId: String?) {
        save(newEntry(song, Kind.EXCLUDE, mixId?.let { "mix:$it" } ?: "global", null))
    }

    /** "Heard too much": out of automatic picks for [SNOOZE_MS]. */
    @Synchronized fun heardTooMuch(song: Song) {
        val names = aliases(song)
        entries.filter { it.kind == Kind.SNOOZE.name && it.names().any(names::contains) }.forEach { remove(it.id) }
        save(newEntry(song, Kind.SNOOZE, "global", System.currentTimeMillis() + SNOOZE_MS))
    }

    /** The song was taken out of the queue during a mix (kept for [REMOVAL_MS]). */
    @Synchronized fun recordRemoval(song: Song) {
        val names = aliases(song)
        entries.filter { it.kind == Kind.REMOVE.name && it.names().any(names::contains) }.forEach { remove(it.id) }
        save(newEntry(song, Kind.REMOVE, "global", System.currentTimeMillis() + REMOVAL_MS))
    }

    /** Undo of a queue removal. */
    @Synchronized fun forgetRemoval(song: Song) {
        val names = aliases(song)
        val gone = entries.filter { it.kind == Kind.REMOVE.name && it.names().any(names::contains) }
        if (gone.isEmpty()) return
        gone.forEach { remove(it.id) }
        reindex()
    }

    /**
     * Soft penalty from queue removals in earlier sessions (this session's removals are
     * handled by [AdaptiveMix.offVibePenalty]): the same recording ≈ 4, the same artist ≈ 1,
     * the same genre ≈ 0.25, fading to 0 over [REMOVAL_MS]. Capped at 5.
     */
    fun removalPenalty(song: Song): Double {
        val (past, started) = synchronized(this) { removals to sessionStartedAt }
        if (past.isEmpty()) return 0.0
        val now = System.currentTimeMillis()
        val names = aliases(song)
        val genre = GenreTaxonomy.match(song.genre)?.id
        var total = 0.0
        for (entry in past) {
            if (entry.createdAt >= started) continue
            val fade = (1.0 - (now - entry.createdAt).toDouble() / REMOVAL_MS).coerceIn(0.0, 1.0)
            total += fade * when {
                entry.names().any(names::contains) -> 4.0
                entry.artist.isNotBlank() && entry.artist.equals(song.artist, true) -> 1.0
                genre != null && GenreTaxonomy.match(entry.genre)?.id == genre -> 0.25
                else -> 0.0
            }
        }
        return total.coerceAtMost(5.0)
    }

    /** Removes one entry (memory + database). Call with the monitor held, then [reindex]. */
    private fun remove(id: String): Boolean {
        undoStack.remove(id)
        if (session.removeAll { it.id == id }) return true
        if (!entries.removeAll { it.id == id }) return false
        writes.trySend { it.delete(id) }
        return true
    }

    /** Reverts the most recent piece of feedback. Can be repeated. */
    @Synchronized fun undo(): Boolean {
        while (undoStack.isNotEmpty()) {
            if (remove(undoStack.last())) {
                reindex()
                return true
            }
        }
        return false
    }

    /** Reverts one item from the feedback history. */
    @Synchronized fun undo(id: String): Boolean {
        if (!remove(id)) return false
        reindex()
        return true
    }

    @Synchronized fun isDisliked(song: Song): Boolean = isDisliked(song, activeMixId)
    @Synchronized fun isDisliked(song: Song, mixId: String): Boolean {
        val global = excluded["global"].orEmpty()
        val flavour = excluded["mix:$mixId"].orEmpty()
        return aliases(song).any { it in sessionAliases || it in global || it in flavour }
    }

    /** Excluded or snoozed everywhere (not session or per-flavour rules): for non-mix surfaces. */
    @Synchronized fun isExcludedEverywhere(song: Song): Boolean {
        val global = excluded["global"].orEmpty()
        return aliases(song).any { it in global }
    }

    fun discoveryBalance(): Double = preferences.getStringSet(DISCOVERY_KEY, emptySet())
        ?.firstOrNull()?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.35
    @Synchronized fun setDiscoveryBalance(value: Double) {
        if (!value.isFinite()) return
        preferences.edit().putStringSet(DISCOVERY_KEY, setOf(value.coerceIn(0.0, 1.0).toString())).apply()
        revision++
    }

    /** Wanted energy 0…1, or null = follow the music (the default). */
    fun energyTarget(): Double? = preferences.getString(ENERGY_KEY, null)?.toDoubleOrNull()?.coerceIn(0.0, 1.0)
    @Synchronized fun setEnergyTarget(value: Double?) {
        val editor = preferences.edit()
        if (value == null || !value.isFinite()) editor.remove(ENERGY_KEY) else editor.putString(ENERGY_KEY, value.coerceIn(0.0, 1.0).toString())
        editor.apply()
        revision++
    }

    /** Variety: 0 = focused (artists may return after 2 songs) … 1 = varied (after 8). */
    fun variety(): Double = preferences.getString(VARIETY_KEY, null)?.toDoubleOrNull()?.coerceIn(0.0, 1.0)
        ?: MixWeights.DEFAULT_VARIETY
    @Synchronized fun setVariety(value: Double) {
        if (!value.isFinite()) return
        preferences.edit().putString(VARIETY_KEY, value.coerceIn(0.0, 1.0).toString()).apply()
        revision++
    }

    /** Clears every exclusion, snooze and removal (the discovery and variety settings stay). */
    @Synchronized fun reset() {
        entries.clear()
        session.clear()
        undoStack.clear()
        writes.trySend { it.clear() }
        val editor = preferences.edit()
        preferences.all.keys.filter { it !in SETTING_KEYS }.forEach { editor.remove(it) }
        editor.apply()
        reindex()
    }

    private companion object {
        const val SESSION = "session"
        const val DISCOVERY_KEY = "discovery"
        const val VARIETY_KEY = "variety"
        const val ENERGY_KEY = "energy"
        val SETTING_KEYS = setOf(DISCOVERY_KEY, VARIETY_KEY, ENERGY_KEY)
        const val MAX_UNDO = 20
        const val MAX_LOG = 40
        const val SNOOZE_MS = 14L * 86_400_000
        const val REMOVAL_MS = 7L * 86_400_000
    }
}
