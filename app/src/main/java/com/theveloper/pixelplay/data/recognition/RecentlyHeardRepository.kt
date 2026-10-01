package com.theveloper.pixelplay.data.recognition

import android.content.Context
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class RecentlyHeardSource { NOW_PLAYING, NOW_PLAYING_HISTORY, SONG_SEARCH }

/** One song recognised around you (Pixel / Android Now Playing, or a song search). */
@Immutable
data class RecentlyHeardEntry(
    val id: String,
    val title: String,
    val artist: String,
    val heardAtEpochMs: Long,
    val source: RecentlyHeardSource,
    /** Cover found when the song was matched to your library or online. */
    val artUri: String? = null,
    /** Id of the matched [com.theveloper.pixelplay.data.model.Song], once resolved. */
    val songId: String? = null,
    /** True once a lookup ran (found or not), so it isn't repeated on every launch. */
    val lookedUp: Boolean = false
) {
    val key: String get() = keyOf(title, artist)

    companion object {
        fun keyOf(title: String, artist: String) =
            "${title.trim().lowercase(Locale.ROOT)}|${artist.trim().lowercase(Locale.ROOT)}"
    }
}

/**
 * Persistent "Recently heard" list: every song Now Playing picks up (its notification, or its
 * history via Shizuku). Kept separate from [com.theveloper.pixelplay.data.repository.HeardSongsRepository],
 * which holds the short-lived songs mentioned in conversation.
 *
 * Stored as JSON in `filesDir/recently_heard_v1.json`, newest first, capped at [MAX_ENTRIES].
 */
@Singleton
class RecentlyHeardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: VoiceSearchResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file get() = File(context.filesDir, FILE_NAME)
    private val writeLock = Mutex()
    private val lookupSlots = Semaphore(2)
    private val lookupsInFlight = mutableSetOf<String>()

    private val _entries = MutableStateFlow<List<RecentlyHeardEntry>>(emptyList())
    val entries: StateFlow<List<RecentlyHeardEntry>> = _entries.asStateFlow()

    /** Live recognitions only (not history imports) — the voice sheet shows these as a match. */
    private val _newlyHeard = MutableSharedFlow<RecentlyHeardEntry>(extraBufferCapacity = 4)
    val newlyHeard: SharedFlow<RecentlyHeardEntry> = _newlyHeard.asSharedFlow()

    init {
        scope.launch {
            _entries.value = load()
            _entries.value.filter { !it.lookedUp }.take(20).forEach { enqueueLookup(it) }
        }
    }

    /**
     * Adds a song. The same song within [DUPLICATE_WINDOW_MS] of an existing entry is treated as
     * the same hearing (Now Playing re-posts its notification and repeats history rows).
     */
    fun record(
        title: String,
        artist: String,
        heardAtEpochMs: Long = System.currentTimeMillis(),
        source: RecentlyHeardSource,
        announce: Boolean = false
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        val cleanArtist = artist.trim()
        val key = RecentlyHeardEntry.keyOf(cleanTitle, cleanArtist)
        var added: RecentlyHeardEntry? = null
        var existingMatch: RecentlyHeardEntry? = null
        _entries.update { current ->
            val duplicate = current.firstOrNull {
                it.key == key && kotlin.math.abs(it.heardAtEpochMs - heardAtEpochMs) < DUPLICATE_WINDOW_MS
            }
            if (duplicate != null) {
                existingMatch = duplicate
                current
            } else {
                val entry = RecentlyHeardEntry(
                    id = UUID.randomUUID().toString(),
                    title = cleanTitle,
                    artist = cleanArtist,
                    heardAtEpochMs = heardAtEpochMs,
                    source = source,
                    // Reuse what an earlier hearing of the same song already found.
                    artUri = current.firstOrNull { it.key == key && it.artUri != null }?.artUri,
                    songId = current.firstOrNull { it.key == key && it.songId != null }?.songId,
                    lookedUp = current.any { it.key == key && it.lookedUp }
                )
                added = entry
                (current + entry).sortedByDescending { it.heardAtEpochMs }.take(MAX_ENTRIES)
            }
        }
        added?.let {
            Timber.d("RecentlyHeard: %s by %s (%s)", it.title, it.artist, source)
            persist()
            if (!it.lookedUp) enqueueLookup(it)
        }
        if (announce) (added ?: existingMatch)?.let { _newlyHeard.tryEmit(it) }
    }

    fun remove(id: String) {
        _entries.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clear() {
        _entries.value = emptyList()
        persist()
    }

    /** Stores the matched song so the cover shows and like/options act on the right song. */
    fun attachSong(entryId: String, songId: String?, artUri: String?) {
        _entries.update { list ->
            val key = list.firstOrNull { it.id == entryId }?.key ?: return@update list
            list.map {
                if (it.key == key) {
                    it.copy(
                        songId = songId ?: it.songId,
                        artUri = artUri?.takeIf { uri -> uri.isNotBlank() } ?: it.artUri,
                        lookedUp = true
                    )
                } else it
            }
        }
        persist()
    }

    private fun enqueueLookup(entry: RecentlyHeardEntry) {
        synchronized(lookupsInFlight) { if (!lookupsInFlight.add(entry.key)) return }
        scope.launch {
            try {
                lookupSlots.withPermit {
                    val song = runCatching { resolver.findSong(entry.title, entry.artist) }.getOrNull()
                    attachSong(entry.id, song?.id, song?.albumArtUriString)
                }
            } finally {
                synchronized(lookupsInFlight) { lookupsInFlight.remove(entry.key) }
            }
        }
    }

    private fun persist() {
        val snapshot = _entries.value
        scope.launch {
            writeLock.withLock {
                runCatching {
                    val array = JSONArray()
                    snapshot.forEach { e ->
                        array.put(
                            JSONObject()
                                .put("id", e.id)
                                .put("title", e.title)
                                .put("artist", e.artist)
                                .put("at", e.heardAtEpochMs)
                                .put("source", e.source.name)
                                .put("art", e.artUri ?: JSONObject.NULL)
                                .put("songId", e.songId ?: JSONObject.NULL)
                                .put("lookedUp", e.lookedUp)
                        )
                    }
                    val tmp = File(context.filesDir, "$FILE_NAME.tmp")
                    tmp.writeText(array.toString())
                    if (!tmp.renameTo(file)) {
                        file.writeText(array.toString())
                        tmp.delete()
                    }
                }.onFailure { Timber.w(it, "RecentlyHeard: save failed") }
            }
        }
    }

    private fun load(): List<RecentlyHeardEntry> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            RecentlyHeardEntry(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = o.optString("title"),
                artist = o.optString("artist"),
                heardAtEpochMs = o.optLong("at"),
                source = runCatching { RecentlyHeardSource.valueOf(o.optString("source")) }
                    .getOrDefault(RecentlyHeardSource.NOW_PLAYING),
                artUri = o.optString("art").takeIf { !o.isNull("art") && it.isNotBlank() },
                songId = o.optString("songId").takeIf { !o.isNull("songId") && it.isNotBlank() },
                lookedUp = o.optBoolean("lookedUp")
            )
        }.filter { it.title.isNotBlank() }.sortedByDescending { it.heardAtEpochMs }
    }.getOrElse {
        Timber.w(it, "RecentlyHeard: load failed")
        emptyList()
    }

    companion object {
        private const val FILE_NAME = "recently_heard_v1.json"
        private const val MAX_ENTRIES = 1000
        private const val DUPLICATE_WINDOW_MS = 10 * 60_000L
    }
}
