package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import com.theveloper.pixelplay.presentation.library.MusicVibeFilters
import com.theveloper.pixelplay.presentation.library.VibeFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.roundToInt

enum class MixFlavor(val title: String) { NORMAL("Normal Mix"), SMART("Smart Mix") }

internal fun mixIdentity(song: Song) = song.creditsAndRelease.isrc?.trim()?.takeIf { it.isNotBlank() }?.let { "isrc:${it.uppercase(java.util.Locale.ROOT)}" } ?: song.title.trim().lowercase(java.util.Locale.ROOT) + "|" + song.artist.trim().lowercase(java.util.Locale.ROOT)

/** Album names that say nothing about the release, so they never count as "same album". */
private val PLACEHOLDER_ALBUMS = setOf("YouTube Music", "Unknown Album")

/** Conservative metadata relevance; no claim of acoustic similarity for unanalysed streams. */
internal fun mixAffinity(song: Song, seeds: List<Song>): Int = seeds.maxOfOrNull { seed ->
    (if (song.artist.equals(seed.artist, true)) 6 else 0) +
        GenreTaxonomy.affinity(song.genre, seed.genre) +
        (if (song.album.isNotBlank() && song.album !in PLACEHOLDER_ALBUMS && song.album.equals(seed.album, true) && song.artist.equals(seed.artist, true)) 2 else 0)
} ?: 0

/**
 * [mixAffinity] against a fixed seed list, for ranking many songs at once.
 *
 * Gives the same answer as `mixAffinity(song, seeds)` but costs a map lookup per song
 * instead of a pass over every seed. Ranking a library against a few hundred liked songs
 * used to take seconds on the main thread (and churned through memory); this is linear.
 */
internal class MixAffinityIndex(seeds: List<Song>) {
    private val isEmpty = seeds.isEmpty()
    private val byArtist: Map<String, List<Song>> = seeds.groupBy { it.artist.lowercase(java.util.Locale.ROOT) }
    private val genreIds: Set<String> = seeds.mapNotNullTo(HashSet()) { GenreTaxonomy.match(it.genre)?.id }
    private val families: Set<String> = seeds.mapNotNullTo(HashSet()) { GenreTaxonomy.match(it.genre)?.family }

    fun affinity(song: Song): Int {
        if (isEmpty) return 0
        val genre = GenreTaxonomy.match(song.genre)
        // Best genre-only score over all seeds (a different-artist seed contributes just this).
        val genreOnly = when {
            genre == null -> 0
            genre.id in genreIds -> 4
            genre.family in families -> 2
            else -> 0
        }
        val sameArtist = byArtist[song.artist.lowercase(java.util.Locale.ROOT)]
            ?.let { mixAffinity(song, it) } ?: 0
        return maxOf(genreOnly, sameArtist)
    }
}

@Singleton
class AdaptiveMix @Inject constructor(private val music: MusicRepository, private val youtube: YouTubeRepository, private val connected: com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository, private val playlists: com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository, private val cloud: com.theveloper.pixelplay.data.database.CloudSongDao, private val feedback: MixFeedback, private val learning: MixLearning, private val gatherer: com.theveloper.pixelplay.data.metadata.SongMetadataGatherer, private val enrichment: com.theveloper.pixelplay.data.database.EnrichmentDao, private val friendsInRoom: dagger.Lazy<com.theveloper.pixelplay.data.social.FriendsInRoomController>) {
    init {
        // Moods the gatherer only guessed from genre + BPM don't count as mood evidence.
        MixVibe.isMoodEstimated = gatherer::isMoodEstimated
        com.theveloper.pixelplay.data.diagnostics.HeapPressure.register("adaptive-mix") { level ->
            found.clear()
            // The snapshot is a full copy of the library + embeddings; it is rebuilt on the
            // next plan (served stale meanwhile), so drop it only when memory is critical.
            if (level == com.theveloper.pixelplay.data.diagnostics.HeapPressure.Level.CRITICAL) snapshot = null
        }
    }

    // ── Cached listening history ─────────────────────────────────────────────────────
    // Every plan read up to 2 000 attempts (and the micro-skip cooldowns) from the database.
    // They only change when a play finishes, so they are kept until then (or [HISTORY_TTL_MS]).
    private class Timed<T>(val at: Long, val value: T)
    @Volatile private var historyCache: Timed<List<MixAttempt>>? = null
    @Volatile private var cooldownCache: Timed<List<MicroSkipCooldown>>? = null

    private suspend fun history(): List<MixAttempt> {
        val now = android.os.SystemClock.elapsedRealtime()
        historyCache?.takeIf { now - it.at < HISTORY_TTL_MS }?.let { return it.value }
        val fresh = try { learning.recent() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { return historyCache?.value ?: emptyList() }
        historyCache = Timed(now, fresh)
        return fresh
    }

    private suspend fun cooldowns(): List<MicroSkipCooldown> {
        val now = android.os.SystemClock.elapsedRealtime()
        cooldownCache?.takeIf { now - it.at < HISTORY_TTL_MS }?.let { return it.value }
        val fresh = try { learning.microSkipCooldowns() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { return cooldownCache?.value ?: emptyList() }
        cooldownCache = Timed(now, fresh)
        return fresh
    }

    /** Plays that ended, as recorded (skips, full listens); the runtime reacts to them live. */
    val finishedAttempts get() = learning.finished

    /**
     * Every key the same recording goes by. The raw mix identity differs between
     * "Song (Official Video)" by "Artist - Topic" and the album track by "Artist"; the cleaned
     * title|artist the metadata gatherer uses doesn't, so both are checked.
     */
    fun recordingKeys(song: Song): Set<String> = setOf(mixIdentity(song), "rec:" + gatherer.recordingKey(song), "id:" + song.id)

    /** Background work that must outlive one plan: library rebuilds and online discovery. */
    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    init {
        // A finished play changes the history every plan reads: drop the cached copy.
        background.launch { learning.finished.collect { historyCache = null; cooldownCache = null } }
    }

    /**
     * Library, playlists, likes, favourites and downloads, merged. Rebuilding it reads several
     * tables, so it is kept for [LIBRARY_TTL_MS] instead of being reloaded on every refill.
     * Cached catalogue metadata (genre, BPM, mood from Deezer / iTunes) is applied here once.
     */
    private data class LibrarySnapshot(
        val library: List<Song>,
        val explicitFavorites: Set<String>,
        val builtAt: Long,
        /** Local analysis (BPM, key, energy…) by track id, from `track_analysis`. */
        val features: Map<Long, com.theveloper.pixelplay.data.database.TrackMixFeatures> = emptyMap(),
        /** Unit-length VGGish embeddings by song id, from `track_embeddings`. */
        val embeddings: Map<String, FloatArray> = emptyMap(),
        /** Songs that are yours (local, likes, favourites, your own playlists), by id. */
        val ownIds: Set<String> = emptySet(),
        /** Songs only in a friend's saved playlist: song id → friend id (tagged with that friend). */
        val friendSongs: Map<String, String> = emptyMap(),
    ) {
        val byId: Map<String, Song> by lazy { library.associateBy { it.id } }
        /** Mix identities of the whole library (was rebuilt on every plan). */
        val identities: Set<String> by lazy { library.mapTo(HashSet(), ::mixIdentity) }
    }
    @Volatile private var snapshot: LibrarySnapshot? = null
    /** Set when the library may have changed; the next plan uses the old one while it rebuilds. */
    @Volatile private var snapshotStale = false
    private var rebuildJob: kotlinx.coroutines.Job? = null
    private val snapshotLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The library for planning. Rebuilding it reads several tables and every embedding, which
     * used to add a second or more to each mode switch. A stale or expired snapshot is served
     * straight away (up to [LIBRARY_MAX_STALE_MS]) while a fresh one is built in the background.
     */
    private suspend fun librarySnapshot(): LibrarySnapshot {
        val current = snapshot
        if (current != null) {
            val age = android.os.SystemClock.elapsedRealtime() - current.builtAt
            if (snapshotStale || age >= LIBRARY_TTL_MS) rebuildSnapshotInBackground()
            if (age < LIBRARY_MAX_STALE_MS) return current
        }
        return buildSnapshot()
    }

    private val rebuildLock = Any()

    private fun rebuildSnapshotInBackground() {
        synchronized(rebuildLock) {
            if (rebuildJob?.isActive == true) return
            rebuildJob = background.launch {
                try { buildSnapshot(force = true) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { }
            }
        }
    }

    private suspend fun buildSnapshot(force: Boolean = false): LibrarySnapshot = snapshotLock.withLock {
        if (!force) snapshot?.takeIf { !snapshotStale && android.os.SystemClock.elapsedRealtime() - it.builtAt < LIBRARY_TTL_MS }?.let { return@withLock it }
        // Cleared before reading, so a change during the build marks the result stale again.
        snapshotStale = false
        val connectedSnapshot = connected.snapshot.value
        val explicitFavorites = music.getFavoriteSongIdsFlow().first()
        val favoriteIds = (explicitFavorites + playlists.userPlaylistsFlow.first().flatMap { it.songIds } + cloud.getDownloadedSongIds().first()).distinct()
        val favorites = if (favoriteIds.isEmpty()) emptyList() else favoriteIds.chunked(900).flatMap { music.getSongsByIds(it).first() }
        // The on-device analysis lives in its own tables; songs don't carry it, so mixes used to
        // sequence local files without their BPM, key or energy. It is joined in here, once.
        val features = try { enrichment.getMixFeatures().associateBy { it.trackId } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { emptyMap() }
        val embeddings = try { loadEmbeddings() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { emptyMap() }
        val (friendPlaylists, ownPlaylists) = connectedSnapshot.playlists.partition { it.friendId != null }
        val own = music.getAllSongsOnce() + ownPlaylists.flatMap { it.songs } + connectedSnapshot.spotifyLikes +
            connectedSnapshot.youtubeLikes + favorites
        val ownIds = own.mapTo(HashSet()) { it.id }
        // Friend playlists you saved stay in your pool, but their songs carry the friend's tag.
        val friendSongs = HashMap<String, String>()
        for (playlist in friendPlaylists) for (song in playlist.songs) {
            if (song.id !in ownIds) friendSongs.putIfAbsent(song.id, playlist.friendId!!)
        }
        val library = (own + friendPlaylists.flatMap { it.songs }).distinctBy { it.id }
            .map { withAnalysis(gatherer.applyCached(it), features) }
        LibrarySnapshot(library, explicitFavorites, android.os.SystemClock.elapsedRealtime(), features, embeddings,
            ownIds, friendSongs).also { snapshot = it }
    }

    /** VGGish vectors, normalised; all-zero or all-non-positive rows (old fallback vectors) are skipped. */
    private suspend fun loadEmbeddings(): Map<String, FloatArray> = enrichment.getAllEmbeddings().mapNotNull { row ->
        val v = row.embedding
        if (v.size != 128 || v.none { it > 0f }) return@mapNotNull null
        var norm = 0.0
        for (x in v) norm += x * x
        if (norm <= 1e-9) return@mapNotNull null
        val scale = (1.0 / kotlin.math.sqrt(norm)).toFloat()
        row.trackId.toString() to FloatArray(v.size) { v[it] * scale }
    }.toMap()

    /** Fills a song's missing BPM, key and energy features from its local analysis. */
    private fun withAnalysis(song: Song, features: Map<Long, com.theveloper.pixelplay.data.database.TrackMixFeatures>): Song {
        val f = song.id.toLongOrNull()?.let(features::get) ?: return song
        val mf = song.musicalFeatures
        val mi = song.mixIntelligence
        val bpm = mf.bpm ?: f.bpm?.takeIf { it > 0 }?.toFloat()
        val key = mf.key ?: f.keyCamelot ?: f.musicKey
        if (bpm == mf.bpm && key == mf.key && mi.energy != null && mi.valence != null) return song
        return song.copy(
            musicalFeatures = mf.copy(bpm = bpm, key = key),
            mixIntelligence = mi.copy(
                energy = mi.energy ?: f.energy,
                valence = mi.valence ?: f.valence,
                danceability = mi.danceability ?: f.danceability,
                acousticness = mi.acousticness ?: f.acousticness,
                instrumentalness = mi.instrumentalness ?: f.instrumentalness
            )
        )
    }

    /** [songs] with their local analysis joined in (for the queue buttons and Your Music). */
    suspend fun analysed(songs: List<Song>): List<Song> {
        val features = librarySnapshot().features
        if (features.isEmpty()) return songs
        return songs.map { withAnalysis(it, features) }
    }

    /**
     * "Sounds like": for each candidate with an embedding, its best cosine similarity to the
     * last few seeds, turned into a within-batch percentile so it doesn't depend on the scale of
     * VGGish similarities: the top half earns 0…[MixWeights.soundAlike], the bottom half 0, and
     * a song without an embedding gets the average (a quarter).
     */
    private fun soundAlike(candidates: List<Song>, seeds: List<Song>, embeddings: Map<String, FloatArray>, weight: Double): Map<String, Double> {
        if (embeddings.isEmpty()) return emptyMap()
        val seedVectors = seeds.takeLast(3).mapNotNull { embeddings[it.id] }
        if (seedVectors.isEmpty()) return emptyMap()
        val similarity = candidates.mapNotNull { song ->
            val v = embeddings[song.id] ?: return@mapNotNull null
            song.id to seedVectors.maxOf { seed -> var dot = 0f; for (i in v.indices) dot += v[i] * seed[i]; dot }
        }
        if (similarity.size < 4) return emptyMap()
        val ranked = similarity.sortedBy { it.second }
        val percentile = ranked.mapIndexed { index, (id, _) -> id to index.toDouble() / (ranked.size - 1) }.toMap()
        return candidates.associate { song ->
            song.id to (percentile[song.id]?.let { ((it - 0.5) * 2).coerceAtLeast(0.0) } ?: 0.25) * weight
        }
    }

    /** The library changed (a like, a download, a scan): re-read it in the background. */
    fun invalidateLibrary() {
        snapshotStale = true
        rebuildSnapshotInBackground()
    }

    // ── Online discovery (Smart Mix) ─────────────────────────────────────────────────
    private data class Found(val at: Long, val songs: List<Song>)
    /** Recent search / related results by query, so switching to Smart Mix is instant. */
    private val found = java.util.concurrent.ConcurrentHashMap<String, Found>()
    private val inflight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<List<Song>>>()

    /**
     * Cached results for [key] when there are any (refreshed in the background once they are
     * [DISCOVERY_REFRESH_MS] old). Otherwise the lookup starts in the background and is awaited
     * for at most [waitMs]: null means it's still running and will be cached for the next plan.
     */
    private suspend fun discoverCached(key: String, waitMs: Long, fetch: suspend () -> List<Song>): List<Song>? {
        val now = android.os.SystemClock.elapsedRealtime()
        found[key]?.takeIf { now - it.at < DISCOVERY_MAX_AGE_MS }?.let { hit ->
            if (now - hit.at > DISCOVERY_REFRESH_MS) startDiscovery(key, fetch)
            return hit.songs
        }
        val lookup = startDiscovery(key, fetch)
        return kotlinx.coroutines.withTimeoutOrNull(waitMs) { lookup.await() }
    }

    private fun startDiscovery(key: String, fetch: suspend () -> List<Song>): kotlinx.coroutines.Deferred<List<Song>> {
        inflight[key]?.takeIf { it.isActive }?.let { return it }
        val lookup = background.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val songs = try { kotlinx.coroutines.withTimeoutOrNull(DISCOVERY_FETCH_TIMEOUT_MS) { fetch() }.orEmpty() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
            // Empty answers (offline, a hiccup) aren't cached, so the next plan asks again.
            if (songs.isNotEmpty()) {
                found[key] = Found(android.os.SystemClock.elapsedRealtime(), songs)
                if (found.size > MAX_DISCOVERY_ENTRIES) found.entries.minByOrNull { it.value.at }?.let { found.remove(it.key) }
            }
            songs
        }
        inflight[key] = lookup
        if (inflight.size > MAX_DISCOVERY_ENTRIES) inflight.entries.removeAll { !it.value.isActive }
        lookup.start()
        return lookup
    }

    private suspend fun discover(fetch: suspend () -> List<Song>): List<Song> = try {
        kotlinx.coroutines.withTimeoutOrNull(4_000) { fetch() }.orEmpty()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Starts a new mix session. [keepPrompt] keeps a typed "more of this" steer that was set just
     * before the mix started (the queue prompt box starts Smart Mix when nothing is playing).
     * [keepVibe] keeps a vibe lock set just before (a queue mix button or a Your Music filter).
     */
    fun activate(mixId: String, keepPrompt: Boolean = false, keepVibe: Boolean = false) {
        learning.sessionId = java.util.UUID.randomUUID().toString()
        direction = null
        if (!keepPrompt) { promptSeeds = emptyList(); promptVibe = null }
        if (!keepVibe) vibe = null
        synchronized(offVibe) { offVibe.clear() }
        synchronized(skipped) { skipped.clear() }
        _insights.value = emptyMap()
        sessionVibe.reset()
        synchronized(friendPicks) { friendPicks.clear() }
        energyFrom = null
        energyChangedAtSong = 0
        synchronized(steerList) {
            // A prompt typed just before the mix started keeps its steers; everything else starts fresh.
            if (!keepPrompt) steerList.clear() else steerList.removeAll { it.group == GROUP_MORE_LIKE }
            steerList.filter { it.movesVibe }.forEach { sessionVibe.steer(it.influence) }
        }
        publishSteers()
        feedback.startSession()
        // Likes / downloads since the last mix are picked up by a background rebuild; the
        // first plan doesn't wait for it.
        invalidateLibrary()
        feedback.activeMixId = mixId
        learning.mixId = mixId
    }

    /**
     * Switches a running mix between Normal and Smart without starting a new session: skips,
     * removals, the typed steer, "More like this" and a locked vibe all carry over. Plans in
     * flight are discarded (the revision changes).
     */
    fun switchFlavor(mixId: String) {
        feedback.activeMixId = mixId
        learning.mixId = mixId
        feedback.invalidate()
    }
    fun deactivate() { learning.mixId = null }

    // ── Vibe lock ───────────────────────────────────────────────────────────────────
    /**
     * A vibe filter (Workout, Chill, a custom filter…) the whole mix keeps to. Set by a queue
     * mix button or by playing a Your Music filter; it lasts until cleared or the mix stops.
     * Songs must fit it (or be closely related to songs that do) and fit adds to the score.
     */
    @Volatile var vibe: VibeFilter? = null
        private set

    fun lockVibe(filter: VibeFilter) {
        vibe = filter
        feedback.invalidate()
    }

    fun clearVibe() {
        if (vibe == null) return
        vibe = null
        feedback.invalidate()
    }

    /** "Normal Mix · Workout" while a vibe is locked, else the flavour's own title. */
    fun statusTitle(flavor: MixFlavor): String = vibe?.let { "${flavor.title} · ${it.label}" } ?: flavor.title
    fun undo() = feedback.undo()
    fun dislikeEverywhere(song: Song) {
        sessionVibe.push("disliked:" + mixIdentity(song), feelOf(song), SessionVibe.DISLIKED)
        feedback.dislike(song, null)
    }
    val revision get() = feedback.revision
    @Volatile internal var lastDecisions: List<MixSequencePlanner.Decision> = emptyList()
        private set
    var discoveryBalance: Double
        get() = feedback.discoveryBalance()
        set(value) { feedback.setDiscoveryBalance(value) }
    /** Energy the listener asked for (Calm 0.25 · Steady 0.55 · Hype 0.85), or null to follow the music. */
    var energyTarget: Double?
        get() = feedback.energyTarget()
        set(value) {
            // A new setting eases in over 3 songs from where the energy is now.
            energyFrom = energyAt(0)
            energyChangedAtSong = sessionVibe.songCount
            feedback.setEnergyTarget(value)
        }

    @Volatile private var energyFrom: Double? = null
    @Volatile private var energyChangedAtSong = 0

    /**
     * Energy wanted at a planned position (0 = the next slot). Calm / Steady / Hype ease in over
     * [ENERGY_EASE_SONGS] songs; Follow is the average of the last 3 listened songs, moving at
     * most 0.08 a song, so the mix can't creep up or down on its own. Relative steers
     * ("calmer", "more energy") are added on top. Null before anything is known.
     */
    internal fun energyAt(position: Int): Double? {
        val shift = sessionVibe.energyShiftNow()
        val chosen = feedback.energyTarget()
        if (chosen == null) {
            val follow = sessionVibe.followEnergy ?: sessionVibe.target()?.energy ?: return null
            return (follow + shift).coerceIn(0.0, 1.0)
        }
        val from = energyFrom ?: chosen
        val songsSince = (sessionVibe.songCount - energyChangedAtSong).coerceAtLeast(0)
        val progress = ((songsSince + position + 1).toDouble() / ENERGY_EASE_SONGS).coerceIn(0.0, 1.0)
        return (from + (chosen - from) * progress + shift).coerceIn(0.0, 1.0)
    }

    /** Weight of [energyAt]: a chosen setting pulls harder than Follow. */
    private fun energyWeight(weights: MixWeights): Double =
        if (feedback.energyTarget() != null) weights.energyTarget else weights.energyTarget * FOLLOW_ENERGY_SHARE

    /** Why the mix picked a queued song, and whether it's new to the listener. */
    data class MixInsight(val reason: String, val discovery: Boolean)
    private val _insights = kotlinx.coroutines.flow.MutableStateFlow<Map<String, MixInsight>>(emptyMap())
    /** By song id, for the mix's own songs in the queue ("why this song", discovery sparkle). */
    val insights: kotlinx.coroutines.flow.StateFlow<Map<String, MixInsight>> = _insights

    /** A short, plain reason for a pick, from its strongest score components. */
    private fun explain(decision: MixSequencePlanner.Decision): String {
        val c = decision.components
        val phrases = listOf(
            "vibeFit" to vibe?.let { "Fits ${it.label}" },
            "explicitDirection" to (direction ?: promptSeeds.firstOrNull())?.let { "More like ${it.title}" },
            "soundAlike" to "Sounds like what's playing",
            "coListen" to "Often played with what's on",
            "sessionBandit" to "Like what you've enjoyed today",
            "enjoyment" to "You usually finish this one",
            "explicitFavorite" to "One of your likes",
            "longTermTaste" to "Matches your taste",
            "recentTaste" to "Matches what you've played lately",
            "sessionFit" to "Fits what's playing",
            "pickedVibe" to "Like the songs you queued",
            "feelFit" to "Fits the vibe",
            "energyTarget" to "Fits the energy"
        )
        val best = phrases.filter { (key, phrase) -> phrase != null && (c[key] ?: 0.0) > 0.5 }
            .maxByOrNull { (key, _) -> c[key] ?: 0.0 }?.second
        decision.friendId?.let { friendId ->
            val name = try { friendsInRoom.get().displayOf(friendId).first } catch (_: Exception) { "a friend" }
            return listOfNotNull("From $name's playlists", best?.replaceFirstChar { it.lowercase() }).joinToString(" · ")
        }
        return when (decision.source) {
            "discovery" -> listOfNotNull("New for you", best).joinToString(" · ")
            "rediscovery" -> "Not heard in a while"
            else -> best ?: "Fits what's playing"
        }
    }

    /** 0 = focused … 1 = varied; sets how soon an artist may return ([MixWeights.withVariety]). */
    var variety: Double
        get() = feedback.variety()
        set(value) { feedback.setVariety(value) }

    /**
     * How the mix has been doing over the last [days] days, from real plays: songs, early
     * skips, finished, and ranking accuracy (see [MixReplay]). Blank when there is too little.
     */
    suspend fun qualityReport(days: Int = 14): String = withContext(Dispatchers.Default) {
        val since = System.currentTimeMillis() - days * 86_400_000L
        val (recommendations, attempts) = try {
            learning.recommendations() to learning.recent()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext ""
        }
        val summaries = MixReplay.summarize(recommendations, attempts, since)
        val total = summaries.sumOf { it.plays }
        if (total < 5) return@withContext ""
        val current = summaries.firstOrNull { it.modelVersion == MixSequencePlanner.MODEL_VERSION }
        val all = summaries.let { list ->
            val skips = list.sumOf { it.earlySkipRate * it.plays } / total
            val finished = list.sumOf { it.finishRate * it.plays } / total
            "Last $days days · $total mix songs · ${(skips * 100).toInt()}% skipped early · ${(finished * 100).toInt()}% finished"
        }
        val accuracy = MixReplay.auc(recommendations, attempts, since)?.let { " · ranking accuracy ${"%.2f".format(it)}" }.orEmpty()
        val model = current?.let { "\nThis version (${it.modelVersion}): ${it.plays} songs · ${(it.earlySkipRate * 100).toInt()}% skipped early" }.orEmpty()
        all + accuracy + model
    }
    @Volatile private var direction: Song? = null
    /** "More like this": a 35 % steer toward the song's feel, shown as a chip with ✕. */
    fun moreLike(song: Song) {
        direction = song
        addSteer(Steer(SteerChip("more-like", "More like ${song.title}"), GROUP_MORE_LIKE,
            SessionVibe.Influence("steer:more-like", feelOf(song), SessionVibe.STEER_DEFAULT), songs = listOf(song)))
    }

    /** The song "More like this" was used on, if any (shown as a steer the user can clear). */
    val moreLikeSong: Song? get() = direction

    /**
     * Songs matching what the user typed in the queue's prompt box ("more of this song/artist").
     * They join the seeds and the first one sets the direction, like "More like this", for the
     * rest of the mix session or until cleared.
     */
    @Volatile private var promptSeeds: List<Song> = emptyList()

    /**
     * The sound typed in the prompt box ("upbeat 80s rock"), applied like a locked vibe for the
     * rest of the mix session. A vibe locked with a queue mix button takes precedence.
     */
    @Volatile private var promptVibe: VibeFilter? = null

    /** The vibe the mix keeps to right now: a locked vibe, else the typed prompt's sound. */
    private val activeVibe: VibeFilter? get() = vibe ?: promptVibe

    fun steerTowards(songs: List<Song>, filter: VibeFilter? = null) {
        if (songs.isEmpty()) return
        val label = filter?.label?.takeIf { it.isNotBlank() } ?: songs.first().artist
        addSteer(Steer(SteerChip("toward:" + label.lowercase(java.util.Locale.ROOT), label), GROUP_TOWARD,
            SessionVibe.Influence("steer:toward", averageFeel(songs.take(MAX_PROMPT_INTENT_SEEDS)), SessionVibe.STEER_DEFAULT),
            songs = songs.take(if (filter != null) MAX_PROMPT_INTENT_SEEDS else MAX_PROMPT_SEEDS), filter = filter))
    }

    /** Clears every explicit steer: the typed prompt, its chips and "More like this". */
    fun clearPromptSteer() {
        val had = synchronized(steerList) { steerList.isNotEmpty().also { steerList.clear() } }
        if (!had && promptSeeds.isEmpty() && direction == null && promptVibe == null) return
        sessionVibe.removeAll { it.startsWith("steer:") }
        rebuildSteerState()
    }

    // ── Steers ("any direction") ────────────────────────────────────────────────────
    /** A steer as the queue shows it: a chip with ✕. */
    data class SteerChip(val id: String, val label: String)

    private class Steer(
        val chip: SteerChip,
        /** Steers in the same group replace each other (a new "calmer" replaces "faster"'s energy twin…). */
        val group: String,
        val influence: SessionVibe.Influence,
        val songs: List<Song> = emptyList(),
        val filter: VibeFilter? = null,
        val awayArtists: Set<String> = emptySet(),
        val lessArtists: Set<String> = emptySet(),
        val lessGenres: List<String> = emptyList(),
    ) {
        val movesVibe: Boolean get() = influence.rate > 0.0
    }

    private val steerList = ArrayList<Steer>()
    private val _steers = kotlinx.coroutines.flow.MutableStateFlow<List<SteerChip>>(emptyList())
    /** Active steers, oldest first, for the chips under the prompt box. */
    val steers: kotlinx.coroutines.flow.StateFlow<List<SteerChip>> = _steers

    @Volatile private var awayArtists: Set<String> = emptySet()
    @Volatile private var lessArtists: Set<String> = emptySet()
    @Volatile private var lessGenres: List<String> = emptyList()

    private fun addSteer(steer: Steer) {
        synchronized(steerList) {
            val replaced = steerList.filter { it.group == steer.group || it.chip.id == steer.chip.id }
            replaced.forEach { sessionVibe.remove(it.influence.id) }
            steerList.removeAll(replaced)
            steerList += steer
            // At most [MAX_STEERS] at once: the oldest goes.
            while (steerList.size > MAX_STEERS) sessionVibe.remove(steerList.removeAt(0).influence.id)
        }
        // Each steer has its own vibe influence id so stacked steers don't overwrite each other.
        // Away / less steers don't move the vibe; they only filter and penalise.
        if (steer.movesVibe) sessionVibe.steer(steer.influence)
        rebuildSteerState()
    }

    /** Removes one steer chip (✕). Returns false when it wasn't there. */
    fun removeSteer(id: String): Boolean {
        val removed = synchronized(steerList) { steerList.filter { it.chip.id == id }.also { steerList.removeAll(it) } }
        if (removed.isEmpty()) return false
        removed.forEach { sessionVibe.remove(it.influence.id) }
        if (removed.any { it.group == GROUP_MORE_LIKE }) direction = null
        rebuildSteerState()
        return true
    }

    /** Seeds, typed vibe, exclusions and chips follow the steers that are left. */
    private fun rebuildSteerState() {
        val list = synchronized(steerList) { steerList.toList() }
        promptSeeds = list.filter { it.group != GROUP_MORE_LIKE }.flatMap { it.songs }.distinctBy(::mixIdentity)
            .take(MAX_PROMPT_INTENT_SEEDS)
        promptVibe = list.lastOrNull { it.filter != null }?.filter
        if (list.none { it.group == GROUP_MORE_LIKE }) direction = null
        awayArtists = list.flatMapTo(HashSet()) { it.awayArtists }
        lessArtists = list.flatMapTo(HashSet()) { it.lessArtists }
        lessGenres = list.flatMap { it.lessGenres }.distinct()
        publishSteers()
        feedback.invalidate()
    }

    private fun publishSteers() {
        _steers.value = synchronized(steerList) { steerList.map { it.chip } }
    }

    /** Drops steers whose time is up ("for a few songs", relative shifts after 10 songs). */
    private fun expireSteers() {
        val active = sessionVibe.activeIds()
        val expired = synchronized(steerList) { steerList.filter { it.movesVibe && it.influence.id !in active }.also { steerList.removeAll(it) } }
        if (expired.isNotEmpty()) rebuildSteerState()
    }

    private fun artistKey(song: Song) = song.artist.trim().lowercase(java.util.Locale.ROOT)

    /** "no <artist>" for this session. */
    internal fun isSteeredAway(song: Song): Boolean {
        val away = awayArtists
        if (away.isEmpty()) return false
        val artist = artistKey(song)
        return away.any { it == artist || (it.length >= 4 && artist.contains(it)) }
    }

    /** Extra penalty for "less <artist>" / "less <genre>". */
    private fun lessPenalty(song: Song): Double {
        var penalty = 0.0
        if (lessArtists.isNotEmpty() && artistKey(song) in lessArtists) penalty += LESS_ARTIST_PENALTY
        val genres = lessGenres
        if (genres.isNotEmpty()) {
            val tags = (song.genre.orEmpty() + " " + song.songInformation.subgenre.orEmpty()).lowercase(java.util.Locale.ROOT)
            if (genres.any { tag -> Regex("(?<![a-z0-9])" + Regex.escape(tag) + "(?![a-z0-9])").containsMatchIn(tags) }) penalty += LESS_GENRE_PENALTY
        }
        return penalty
    }

    /**
     * Turns a resolved prompt into steers: toward what it named (songs, artists, a sound), away
     * from artists ("no X"), less of something, a blend of two, a path from one to another, and
     * relative shifts ("calmer", "faster"). Returns the labels applied (empty = nothing to do).
     */
    fun applyPrompt(resolution: PromptResolution): List<String> {
        val intent = resolution.intent ?: MixPromptParser.parse(resolution.query)
        val strength = intent.strength ?: SessionVibe.STEER_DEFAULT
        val scale = strength / SessionVibe.STEER_DEFAULT
        val temporary = intent.temporarySongs
        val applied = mutableListOf<String>()
        val label = intent.labels.joinToString(" · ").ifBlank { resolution.query }
        if (resolution.songs.isNotEmpty() && (intent.hasTarget || !intent.hasDirection)) {
            addSteer(Steer(SteerChip("toward", resolution.labels.joinToString(" · ").ifBlank { label }), GROUP_TOWARD,
                SessionVibe.Influence("steer:toward", averageFeel(resolution.songs), strength, lastsSongs = temporary),
                songs = resolution.songs.take(if (resolution.filter != null) MAX_PROMPT_INTENT_SEEDS else MAX_PROMPT_SEEDS),
                filter = resolution.filter))
            applied += resolution.labels.ifEmpty { listOf(label) }
        }
        if (resolution.blendA.isNotEmpty() && resolution.blendB.isNotEmpty() && intent.blend.size >= 2) {
            val mid = Feel.toward(averageFeel(resolution.blendA), averageFeel(resolution.blendB) ?: return applied, 0.5)
            addSteer(Steer(SteerChip("blend", "Between ${intent.blend[0]} & ${intent.blend[1]}"), GROUP_BLEND,
                SessionVibe.Influence("steer:blend", mid, strength, lastsSongs = temporary),
                songs = interleave(resolution.blendA, resolution.blendB)))
            applied += "between ${intent.blend[0]} & ${intent.blend[1]}"
        }
        if (resolution.pathA.isNotEmpty() && resolution.pathB.isNotEmpty() && intent.pathFrom != null && intent.pathTo != null) {
            val start = averageFeel(resolution.pathA)
            val end = averageFeel(resolution.pathB)
            if (start != null && end != null) {
                addSteer(Steer(SteerChip("path", "${intent.pathFrom} → ${intent.pathTo}"), GROUP_PATH,
                    SessionVibe.Influence("steer:path", start, maxOf(strength, SessionVibe.STEER_DEFAULT), pathTo = end, pathSongs = PATH_SONGS),
                    songs = resolution.pathA.take(3) + resolution.pathB.take(3)))
                applied += "${intent.pathFrom} → ${intent.pathTo}"
            }
        }
        fun shift(axis: SessionVibe.Axis, amount: Double?, word: String) {
            amount ?: return
            val sign = if (amount > 0) "+" else "−"
            addSteer(Steer(SteerChip("shift:${axis.name.lowercase(java.util.Locale.ROOT)}", word), "shift:${axis.name}",
                SessionVibe.Influence("steer:shift:${axis.name}", null, 1.0, shiftAxis = axis, shift = amount * scale,
                    // Relative moves fade back over about 10 songs (or last "for a few songs").
                    fadeSongs = if (temporary == null) SessionVibe.SHIFT_FADE_SONGS else null, lastsSongs = temporary)))
            applied += "$word ($sign)"
        }
        val shiftLabels = intent.labels
        fun labelFor(vararg keys: String, fallback: String) = shiftLabels.firstOrNull { l -> keys.any { l.contains(it) } } ?: fallback
        shift(SessionVibe.Axis.ENERGY, intent.energyShift, labelFor("calm", "chill", "soft", "mellow", "quiet", "gentl", "light", "heav", "loud", "hard", "energ", "hype", "wild", "liv", "intense", fallback = if ((intent.energyShift ?: 0.0) < 0) "Calmer" else "More energy").replaceFirstChar { it.uppercase() })
        shift(SessionVibe.Axis.TEMPO, intent.tempoShift, labelFor("fast", "quick", "slow", fallback = if ((intent.tempoShift ?: 0.0) < 0) "Slower" else "Faster").replaceFirstChar { it.uppercase() })
        shift(SessionVibe.Axis.VALENCE, intent.valenceShift, labelFor("happ", "bright", "cheer", "sad", "dark", "mood", fallback = if ((intent.valenceShift ?: 0.0) < 0) "Sadder" else "Happier").replaceFirstChar { it.uppercase() })
        for (artist in intent.awayArtists) {
            val key = artist.trim().lowercase(java.util.Locale.ROOT)
            addSteer(Steer(SteerChip("away:$key", "No $artist"), "away:$key",
                SessionVibe.Influence("steer:away:$key", null, 0.0), awayArtists = setOf(key)))
            applied += "no $artist"
        }
        for (artist in intent.lessArtists) {
            val key = artist.trim().lowercase(java.util.Locale.ROOT)
            addSteer(Steer(SteerChip("less:$key", "Less $artist"), "less:$key",
                SessionVibe.Influence("steer:less:$key", null, 0.0), lessArtists = setOf(key)))
            applied += "less $artist"
        }
        if (intent.lessGenres.isNotEmpty()) {
            val word = intent.labels.firstOrNull { it.startsWith("less ") } ?: "less " + intent.lessGenres.first()
            addSteer(Steer(SteerChip("lessgenre", word.replaceFirstChar { it.uppercase() }), "lessgenre",
                SessionVibe.Influence("steer:lessgenre", null, 0.0), lessGenres = intent.lessGenres))
            applied += word
        }
        return applied
    }

    private fun interleave(a: List<Song>, b: List<Song>): List<Song> =
        (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }

    // ── Session vibe ────────────────────────────────────────────────────────────────
    /** The running target feel every control nudges (see [SessionVibe]). */
    private val sessionVibe = SessionVibe()

    private fun analysedNow(song: Song): Song {
        val snap = snapshot
        return snap?.byId?.get(song.id) ?: withAnalysis(gatherer.applyCached(song), snap?.features.orEmpty())
    }

    internal fun feelOf(song: Song): Feel = Feel.of(analysedNow(song), snapshot?.embeddings.orEmpty())

    private fun averageFeel(songs: List<Song>): Feel? = Feel.average(songs.take(10).map(::feelOf))

    /** A new song started playing: fades move on, expired steers go. */
    fun noteSongStarted() {
        sessionVibe.advanceSong()
        expireSteers()
    }

    /** A song was listened to (reward from [MixRewards], ≥ 0.4): the vibe follows it. */
    fun noteListened(song: Song, reward: Double) {
        sessionVibe.listened(feelOf(song), reward)
    }

    /** A song you queued played through: it pulls the vibe another 10 %. */
    fun notePickPlayed(song: Song) {
        sessionVibe.pull("pick-played:" + mixIdentity(song), feelOf(song), SessionVibe.PICK_PLAYED)
    }

    /** 0…1: how close [song] feels to where the mix is heading. */
    fun vibeFit(song: Song): Double = sessionVibe.fit(feelOf(song))

    /**
     * Where the mix is heading, in words, for the Tune this mix sheet
     * ("Steady energy · chill, happy · about 118 BPM · rock, pop"), or null before anything is known.
     */
    fun headingSummary(): String? {
        val target = sessionVibe.target() ?: return null
        val parts = mutableListOf<String>()
        energyAt(0)?.let { e ->
            parts += when {
                e < 0.35 -> "Calm energy"
                e < 0.6 -> "Steady energy"
                e < 0.78 -> "Lively energy"
                else -> "High energy"
            }
        }
        target.moods.entries.sortedByDescending { it.value }.filter { it.value >= 0.15 }.take(2)
            .map { it.key }.takeIf { it.isNotEmpty() }?.let { parts += it.joinToString(", ") }
        target.tempo?.let { parts += "about ${(2.0.pow(it)).roundToInt()} BPM" }
        target.families.entries.sortedByDescending { it.value }.filter { it.value >= 0.15 }.take(2)
            .map { family -> familyLabel(family.key) }.takeIf { it.isNotEmpty() }?.let { parts += it.joinToString(", ") }
        return parts.joinToString(" · ").ifBlank { null }
    }

    private fun familyLabel(family: String): String = when (family) {
        "hip_hop" -> "hip hop"
        "rnb" -> "R&B"
        "new_age" -> "new age"
        else -> family.replace('_', ' ')
    }

    /** "feelFit" per candidate: closeness to the session vibe, centred on an average song. */
    private fun feelFitScores(candidates: List<Song>, embeddings: Map<String, FloatArray>, weights: MixWeights): Map<String, Double> {
        val target = sessionVibe.target() ?: return emptyMap()
        return candidates.associate { song ->
            song.id to (Feel.fit(Feel.of(song, embeddings), target) - MixVibeSimilarity.PRIOR) * weights.feelFit
        }
    }

    // ── Friends in the room ─────────────────────────────────────────────────────────
    /** Friend songs the mix queued this session, per friend (for fair turns). */
    private val friendPicks = HashMap<String, Int>()

    private fun friendDeficit(friendIds: Collection<String>): Map<String, Double> {
        if (friendIds.isEmpty()) return emptyMap()
        val counts = synchronized(friendPicks) { HashMap(friendPicks) }
        val total = friendIds.sumOf { counts[it] ?: 0 }
        val fair = total.toDouble() / friendIds.size
        return friendIds.associateWith { fair - (counts[it] ?: 0) }
    }

    /** What the queue prompt box resolved to: seed songs and the labels it understood. */
    data class PromptResolution(
        val songs: List<Song>,
        val labels: List<String>,
        val filter: VibeFilter?,
        /** What was typed, and how it was read (directions: away, less, blend, path, shifts). */
        val query: String = "",
        val intent: PromptIntent? = null,
        /** Songs for each side of "between X and Y" / "start with X, end with Y". */
        val blendA: List<Song> = emptyList(),
        val blendB: List<Song> = emptyList(),
        val pathA: List<Song> = emptyList(),
        val pathB: List<Song> = emptyList(),
    ) {
        /** True when there's something to steer with (songs to move toward, or a direction). */
        val isUsable: Boolean get() = songs.isNotEmpty() || intent?.hasDirection == true
    }

    /**
     * Songs for a typed prompt. A plain song or artist name works as before (library first, then
     * an online search). Anything with a sound in it ("upbeat 80s rock like Queen", "slow sad
     * acoustic", "fast aussie hip hop but no rap") is read by [MixPromptParser] and every part is
     * scored together over the whole library, so songs matching more parts rank higher. [refined]
     * is an optional richer reading of the same prompt (the on-device AI); it only fills gaps.
     */
    suspend fun resolvePrompt(query: String, refined: PromptIntent? = null): PromptResolution = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext PromptResolution(emptyList(), emptyList(), null, q)
        val parsed = MixPromptParser.parse(q)
        val intent = refined?.let { parsed.mergedWith(it) } ?: parsed
        // Only a direction ("calmer", "no Drake", "between X and Y"): nothing to match directly.
        val target = if (intent.hasTarget || !intent.hasDirection) resolveTarget(q, intent)
            else PromptResolution(emptyList(), intent.labels, null)
        suspend fun side(name: String?): List<Song> = name?.let { resolveTarget(it, MixPromptParser.parse(it)).songs }.orEmpty()
        val blendA = side(intent.blend.getOrNull(0))
        val blendB = side(intent.blend.getOrNull(1))
        val pathA = side(intent.pathFrom)
        val pathB = side(intent.pathTo)
        target.copy(query = q, intent = intent, blendA = blendA, blendB = blendB, pathA = pathA, pathB = pathB,
            labels = intent.labels.ifEmpty { target.labels })
    }

    /** Songs matching what to move toward (names and / or a sound); see [resolvePrompt]. */
    private suspend fun resolveTarget(q: String, intent: PromptIntent): PromptResolution {
        val snapshotNow = connected.snapshot.value
        val library = (music.getAllSongsOnce() + snapshotNow.playlists.flatMap { it.songs } + snapshotNow.spotifyLikes + snapshotNow.youtubeLikes)
            .filter { it.contentUriString.isNotBlank() && !feedback.isDisliked(it) }
            .distinctBy(::mixIdentity)

        fun rank(song: Song, needle: String): Int {
            val artist = song.artist.lowercase(java.util.Locale.ROOT)
            val title = song.title.lowercase(java.util.Locale.ROOT)
            return when {
                artist == needle -> 0
                title == needle -> 1
                "$title $artist".contains(needle) || "$artist $title".contains(needle) -> 2
                artist.contains(needle) -> 3
                title.contains(needle) -> 4
                else -> -1
            }
        }
        fun lookup(needle: String, limit: Int): List<Song> {
            val n = needle.trim().lowercase(java.util.Locale.ROOT)
            if (n.length < 2) return emptyList()
            return library.map { it to rank(it, n) }.filter { it.second >= 0 }
                .sortedBy { it.second }.map { it.first }.take(limit)
        }

        // Only a name ("Queen", "Mr Brightside"): the old, exact behaviour.
        if (!intent.hasVibe) {
            val names = intent.artists + intent.songs
            // Direction words ("calmer", "a little", "no X") were read already; look up what's left.
            val words = if (intent.hasDirection || intent.strength != null || intent.temporarySongs != null)
                intent.freeWords.joinToString(" ").ifBlank { q } else q
            val local = if (names.isEmpty()) lookup(words, MAX_PROMPT_SEEDS)
                else names.flatMap { lookup(it, MAX_PROMPT_SEEDS) }.distinctBy(::mixIdentity).take(MAX_PROMPT_SEEDS)
            val labels = intent.labels.ifEmpty { listOf("more like $q") }
            if (local.isNotEmpty()) return PromptResolution(local, labels, null)
            val online = discover { youtube.searchSongs(names.firstOrNull() ?: words) }
                .filter { it.contentUriString.isNotBlank() || it.youtubeId != null }
                .map(gatherer::applyCached)
                .take(MAX_PROMPT_SEEDS)
            return PromptResolution(online, labels, null)
        }

        // A sound / era / mood (plus maybe names): score the whole library on all parts at once.
        val snap = snapshot
        val features = snap?.features.orEmpty()
        fun analysed(song: Song): Song = snap?.byId?.get(song.id) ?: withAnalysis(gatherer.applyCached(song), features)

        val artistNames = intent.artists.map { it.lowercase(java.util.Locale.ROOT) }.toMutableSet()
        // Words typed without "like" can still be an artist in the library ("queen rock").
        if (intent.freeWords.isNotEmpty()) {
            val libraryArtists = library.mapTo(HashSet()) { it.artist.trim().lowercase(java.util.Locale.ROOT) }
            val words = intent.freeWords
            for (size in 3 downTo 1) for (i in 0..(words.size - size)) {
                val phrase = words.subList(i, i + size).joinToString(" ")
                if (phrase in libraryArtists) artistNames += phrase
            }
        }
        val songSeeds = intent.songs.flatMap { lookup(it, 2) }.distinctBy(::mixIdentity)
        val artistSeeds = library.filter { it.artist.trim().lowercase(java.util.Locale.ROOT) in artistNames }
        val affinity = MixAffinityIndex((songSeeds + artistSeeds.take(6)).distinctBy(::mixIdentity))
        val filter = intent.copy(freeWords = intent.freeWords.filterNot { w -> artistNames.any { it.contains(w) } }).toFilter()
        val excluded = intent.excludeGenres

        fun isExcluded(song: Song): Boolean {
            if (excluded.isEmpty()) return false
            val tags = (song.genre.orEmpty() + " " + song.songInformation.subgenre.orEmpty()).lowercase(java.util.Locale.ROOT)
            return excluded.any { tag -> Regex("(?<![a-z0-9])" + Regex.escape(tag) + "(?![a-z0-9])").containsMatchIn(tags) }
        }

        val scored = library.asSequence()
            .filterNot(::isExcluded)
            .map(::analysed)
            .map { song ->
                var score = MusicVibeFilters.score(song, filter)
                if (song.artist.trim().lowercase(java.util.Locale.ROOT) in artistNames) score += 4.0
                score += affinity.affinity(song) * 0.35
                song to score
            }
            .filter { it.second >= MusicVibeFilters.MATCH_THRESHOLD }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
        // A few per artist so one artist doesn't take the whole steer.
        val perArtist = HashMap<String, Int>()
        val picked = (songSeeds + scored).distinctBy(::mixIdentity).filter { song ->
            val key = song.artist.trim().lowercase(java.util.Locale.ROOT)
            val n = perArtist.getOrDefault(key, 0)
            if (n >= 3) false else { perArtist[key] = n + 1; true }
        }.take(MAX_PROMPT_INTENT_SEEDS)

        if (picked.isNotEmpty()) return PromptResolution(picked, intent.labels, filter)
        // Nothing in the library fits: search online with the names first, then the words typed.
        val onlineQuery = (intent.artists + intent.songs).firstOrNull() ?: q
        val online = discover { youtube.searchSongs(onlineQuery) }
            .filter { it.contentUriString.isNotBlank() || it.youtubeId != null }
            .filterNot(::isExcluded)
            .map(gatherer::applyCached)
            .take(MAX_PROMPT_SEEDS)
        return PromptResolution(online, intent.labels, filter.takeIf { online.isNotEmpty() })
    }

    /**
     * Songs removed from the queue during this mix, newest last. Removing a song is a soft
     * "doesn't fit the vibe" signal: unlike a dislike it is never stored, never excludes the
     * artist, and only lowers songs that resemble it for the rest of this mix session.
     */
    private val offVibe = ArrayDeque<Song>()

    /** Removals and hand-queued songs by *feel* (energy, mood, tempo, sound…), mix or not. */
    private val signals = MixVibeSignals()

    /**
     * The user queued [song] themselves: "more of this vibe". Similar-feeling songs rise in
     * every plan for the next [MixVibeSignals.TTL_MS] (and a skip of it is forgotten).
     */
    fun markPicked(song: Song) {
        signals.markPicked(song)
        clearSkipped(song)
        // The vibe tilts toward it: 25 % if it already fits, 15 % if it's a different vibe.
        val feel = feelOf(song)
        val rate = if (sessionVibe.target() == null || sessionVibe.fit(feel) >= SessionVibe.PICK_FIT_THRESHOLD)
            SessionVibe.PICK_FITS else SessionVibe.PICK_DIFFERENT
        sessionVibe.pull("pick:" + mixIdentity(song), feel, rate)
        sessionVibe.remove("removed:" + mixIdentity(song))
        feedback.invalidate()
    }

    /**
     * A song taken out of a queue that isn't a mix: still a hint about the vibe the listener
     * wants (used by friends-in-the-room picks and by the next mix), but not stored for later.
     */
    fun noteRemovedOutsideMix(song: Song) {
        signals.markRemoved(song)
        feedback.invalidate()
    }

    fun markOffVibe(song: Song) {
        signals.markRemoved(song)
        sessionVibe.remove("pick:" + mixIdentity(song))
        sessionVibe.push("removed:" + mixIdentity(song), feelOf(song), SessionVibe.REMOVED)
        synchronized(offVibe) {
            offVibe.removeAll { mixIdentity(it) == mixIdentity(song) }
            offVibe.addLast(song)
            while (offVibe.size > MAX_OFF_VIBE) offVibe.removeFirst()
        }
        // Also kept for a week, as a fading penalty in later sessions.
        feedback.recordRemoval(song)
    }

    /** Undo of a queue removal: the song fits after all. */
    fun clearOffVibe(song: Song) {
        signals.unmarkRemoved(song)
        sessionVibe.remove("removed:" + mixIdentity(song))
        synchronized(offVibe) { offVibe.removeAll { mixIdentity(it) == mixIdentity(song) } }
        feedback.forgetRemoval(song)
        feedback.invalidate()
    }

    /** Recent feedback (exclusions, snoozes, removals) for the Tune this mix sheet. */
    val feedbackLog get() = feedback.log

    /** Undoes one item of [feedbackLog]. */
    fun undoFeedback(id: String) = feedback.undo(id)

    /**
     * What the mix has learned about each of [songs], as a score adjustment for the one-shot
     * mix builders (queue mix buttons, Your Music vibe mixes) so they share the continuous
     * mix's memory: long-term and recent taste from listening, minus snoozes, past queue
     * removals and this session's removals and skips. Roughly −4…+3.
     */
    suspend fun personalAdjustments(songs: List<Song>): Map<String, Double> = withContext(Dispatchers.Default) {
        if (songs.isEmpty()) return@withContext emptyMap()
        feedback.awaitReady()
        val history = history()
        val favorites = try { music.getFavoriteSongIdsFlow().first() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { emptySet() }
        val taste = MixTasteProfile(songs, history, favorites, feedback.activeMixId, System.currentTimeMillis())
        val removed = offVibeSongs()
        val skips = skippedSongs()
        songs.associate { song ->
            val components = taste.components(song)
            val learned = ((components["longTermTaste"] ?: 0.0) + (components["recentTaste"] ?: 0.0)) * 0.35 +
                (components["artistFatigue"] ?: 0.0) * 0.5
            val penalty = feedback.removalPenalty(song) * 0.5 + offVibePenalty(song, removed) * 0.5 +
                offVibePenalty(song, skips) * 0.25
            song.id to (learned - penalty).coerceIn(-4.0, 3.0)
        }
    }

    internal fun offVibeSongs(): List<Song> = synchronized(offVibe) { offVibe.toList() }

    /**
     * Songs skipped early in this mix session, newest last. A skip is a weaker "not this"
     * than a removal: similar songs get half the removal penalty, for this session only.
     */
    private val skipped = ArrayDeque<Song>()

    fun markSkipped(song: Song, friendSong: Boolean = false) {
        // A skipped friend song is a weaker "not this" (half), and says nothing about the friend.
        sessionVibe.push("skipped:" + mixIdentity(song), feelOf(song),
            if (friendSong) SessionVibe.SKIPPED * 0.5 else SessionVibe.SKIPPED)
        if (friendSong) { feedback.invalidate(); return }
        synchronized(skipped) {
            skipped.removeAll { mixIdentity(it) == mixIdentity(song) }
            skipped.addLast(song)
            while (skipped.size > MAX_SKIPPED) skipped.removeFirst()
        }
        feedback.invalidate()
    }

    /** A skipped song was played again / queued by hand: it wasn't a rejection after all. */
    fun clearSkipped(song: Song) {
        sessionVibe.remove("skipped:" + mixIdentity(song))
        val removed = synchronized(skipped) { skipped.removeAll { mixIdentity(it) == mixIdentity(song) } }
        if (removed) feedback.invalidate()
    }

    internal fun skippedSongs(): List<Song> = synchronized(skipped) { skipped.toList() }

    /**
     * How much [song] resembles what was removed from the queue. Recent removals count most,
     * and plain genre overlap counts little so one removal can't empty a single-genre mix:
     * same artist ≈ 3–5, same genre ≈ 1, unrelated 0.
     */
    internal fun offVibePenalty(song: Song, removed: List<Song> = offVibeSongs()): Double {
        if (removed.isEmpty()) return 0.0
        return removed.asReversed().mapIndexed { age, gone ->
            val overlap = (mixAffinity(song, listOf(gone)) - 2).coerceAtLeast(0)
            overlap * 0.5 * (1.0 / (1 + age * 0.35))
        }.sum().coerceAtMost(8.0)
    }
    /**
     * Live vibe signals as two planner components per song id:
     * - "removedVibe" (≤ 0): how much more a song feels like something removed from the queue
     *   (or skipped early, at half strength) than like what's playing. Only the *excess* over
     *   the context counts, so removing one song never drags down the whole current vibe.
     * - "pickedVibe": how much a song feels like the songs queued by hand this session,
     *   centred on an average pair, newest pick counting most.
     */
    private fun vibeSignalComponents(
        candidates: List<Song>, seeds: List<Song>, embeddings: Map<String, FloatArray>
    ): Map<String, Map<String, Double>> {
        val removed = signals.removedSongs()
        val skips = skippedSongs()
        val picks = signals.pickedSongs()
        if (removed.isEmpty() && skips.isEmpty() && picks.isEmpty()) return emptyMap()
        val removedScores = HashMap<String, Double>(candidates.size * 2)
        val pickedScores = HashMap<String, Double>(candidates.size * 2)
        for (song in candidates) {
            if (removed.isNotEmpty() || skips.isNotEmpty()) {
                val context = MixVibeSimilarity.toContext(song, seeds, embeddings)
                var penalty = 0.0
                removed.asReversed().forEachIndexed { age, gone ->
                    val excess = MixVibeSimilarity.similarity(song, gone, embeddings) - context
                    if (excess > 0) penalty += excess * REMOVED_VIBE_WEIGHT / (1 + age * 0.3)
                }
                skips.asReversed().take(6).forEachIndexed { age, gone ->
                    val excess = MixVibeSimilarity.similarity(song, gone, embeddings) - context
                    if (excess > 0) penalty += excess * REMOVED_VIBE_WEIGHT * 0.5 / (1 + age * 0.3)
                }
                removedScores[song.id] = -penalty.coerceAtMost(8.0)
            }
            if (picks.isNotEmpty()) {
                var total = 0.0
                var weights = 0.0
                picks.forEachIndexed { index, pick ->
                    val w = (index + 1).toDouble()
                    total += (MixVibeSimilarity.similarity(song, pick, embeddings) - MixVibeSimilarity.PRIOR) * w
                    weights += w
                }
                pickedScores[song.id] = (total / weights) * PICKED_VIBE_WEIGHT
            }
        }
        return buildMap {
            if (removedScores.isNotEmpty()) put("removedVibe", removedScores)
            if (pickedScores.isNotEmpty()) put("pickedVibe", pickedScores)
        }
    }

    /**
     * Cuts a large candidate list down to the few hundred worth planning, cheaply (one map
     * lookup per song, one 128-float dot product where there's an embedding). The planner
     * scores every candidate with every component, so planning over a whole library of
     * thousands of songs was the slowest step of a refill. Kept: the best metadata matches to
     * what's playing / queued / the locked vibe, the best sound-alikes, fresh online finds,
     * and a random slice so discoveries and variety still get a chance.
     */
    private fun shortlist(
        pool: List<Song>, seeds: List<Song>, boosts: Map<String, Double>, favorites: Set<String>,
        embeddings: Map<String, FloatArray>, fresh: Set<String>, steer: Song?,
        friendOf: Map<String, String> = emptyMap(),
    ): List<Song> {
        val size = SHORTLIST_TOP + SHORTLIST_SOUND + SHORTLIST_RANDOM
        if (pool.size <= size) return pool
        val anchors = seeds.takeLast(4) + signals.pickedSongs().takeLast(3) + listOfNotNull(steer)
        val affinity = MixAffinityIndex(anchors)
        val removed = MixAffinityIndex(signals.removedSongs())
        val keep = LinkedHashMap<String, Song>(size * 2)
        pool.asSequence().filter { it.id in fresh }.take(SHORTLIST_FRESH).forEach { keep[it.id] = it }
        // Friends in the room: each friend's best-feeling songs always reach the planner (an
        // equal slice each, so a friend with a huge library can't crowd out the others).
        if (friendOf.isNotEmpty()) {
            val target = sessionVibe.target()
            val byFriend = pool.filter { it.id in friendOf }.groupBy { friendOf.getValue(it.id) }
            val each = (SHORTLIST_FRIENDS / byFriend.size.coerceAtLeast(1)).coerceAtLeast(20)
            for (songs in byFriend.values) {
                songs.sortedByDescending { song -> target?.let { Feel.fit(Feel.of(song, embeddings), it) } ?: affinity.affinity(song).toDouble() }
                    .take(each).forEach { keep.putIfAbsent(it.id, it) }
            }
        }
        pool.sortedByDescending { song ->
            affinity.affinity(song) + (boosts[song.id] ?: 0.0) +
                (if (song.id in favorites || song.isFavorite) 2.0 else 0.0) - removed.affinity(song) * 0.5
        }.take(SHORTLIST_TOP).forEach { keep.putIfAbsent(it.id, it) }
        val seedVectors = anchors.mapNotNull { embeddings[it.id] }
        if (seedVectors.isNotEmpty()) {
            pool.mapNotNull { song ->
                if (song.id in keep) return@mapNotNull null
                val v = embeddings[song.id] ?: return@mapNotNull null
                song to seedVectors.maxOf { seed -> var dot = 0f; for (i in v.indices) dot += v[i] * seed[i]; dot }
            }.sortedByDescending { it.second }.take(SHORTLIST_SOUND).forEach { keep[it.first.id] = it.first }
        }
        pool.filter { it.id !in keep }.shuffled().take(SHORTLIST_RANDOM).forEach { keep[it.id] = it }
        return keep.values.toList()
    }

    /**
     * How well each candidate fits *right now*, for picks made outside the planner (friends in
     * the room). Synchronous and cheap: uses the cached library snapshot and in-memory feedback.
     * Higher is better; songs the listener excluded (not for this mix, exclude everywhere,
     * heard too much) are left out. Honours every Tune this mix control that applies to a
     * single pick: More like this / typed steer, a locked vibe, Energy, Focused ↔ Varied,
     * queue removals, skips and hand-queued songs.
     */
    fun vibeFitNow(candidates: List<Song>, context: List<Song>): Map<String, Double> {
        if (candidates.isEmpty()) return emptyMap()
        val snap = snapshot
        val embeddings = snap?.embeddings.orEmpty()
        val features = snap?.features.orEmpty()
        fun analysed(song: Song): Song = snap?.byId?.get(song.id)
            ?: withAnalysis(gatherer.applyCached(song), features)
        val ctx = context.map(::analysed)
        val lockedVibe = activeVibe
        val steer = direction ?: promptSeeds.firstOrNull()
        val energy = energyTarget
        val varietyValue = variety
        val recentArtists = ctx.takeLast(2 + (varietyValue * 3).toInt()).mapTo(HashSet()) { it.artist.trim().lowercase() }
        val removedList = offVibeSongs()
        val songs = candidates.filterNot { feedback.isDisliked(it) || isSteeredAway(it) }.map(::analysed)
        val signalParts = vibeSignalComponents(songs, ctx, embeddings)
        val target = sessionVibe.target()
        val wantedEnergy = energyAt(0)
        return songs.associate { song ->
            var score = MixVibeSimilarity.toContext(song, ctx, embeddings) * 8.0
            target?.let { score += (Feel.fit(Feel.of(song, embeddings), it) - MixVibeSimilarity.PRIOR) * MixWeights.DEFAULT.feelFit }
            score -= lessPenalty(song)
            ctx.lastOrNull()?.let { previous -> score += MixVibe.components(previous, song).values.sum() * 0.4 }
            for (part in signalParts.values) score += part[song.id] ?: 0.0
            lockedVibe?.let { score += MusicVibeFilters.score(song, it).coerceIn(-3.0, 8.0) * 0.6 }
            steer?.let { score += (MixVibeSimilarity.similarity(song, it, embeddings) - MixVibeSimilarity.PRIOR) * 4.0 }
            (energy ?: wantedEnergy)?.let { wanted ->
                val e = song.mixIntelligence.energy?.takeIf { it.isFinite() && it in 0f..1f }?.toDouble()
                    ?: Feel.of(song).energy
                score -= (if (e == null) 0.3 else kotlin.math.abs(e - wanted)) * (if (energy != null) 3.0 else 2.0)
            }
            if (song.artist.trim().lowercase() in recentArtists) score -= 1.5 + varietyValue * 4.0
            score -= feedback.removalPenalty(song) * 0.5 + offVibePenalty(song, removedList) * 0.5
            song.id to score
        }
    }

    /**
     * True when [song] feels almost the same as one of [removed] (energy, mood, tempo, genre,
     * sound): an upcoming mix song that close to what was just taken out goes too.
     */
    fun feelsLike(song: Song, removed: List<Song>): Boolean {
        if (removed.isEmpty()) return false
        val snap = snapshot
        val embeddings = snap?.embeddings.orEmpty()
        val features = snap?.features.orEmpty()
        fun analysed(s: Song): Song = snap?.byId?.get(s.id) ?: withAnalysis(gatherer.applyCached(s), features)
        val candidate = analysed(song)
        return removed.any { MixVibeSimilarity.similarity(candidate, analysed(it), embeddings) >= FEELS_LIKE_REMOVED }
    }

    fun heardTooMuch(song: Song) = feedback.heardTooMuch(song)
    suspend fun resetLearning() { direction = null; learning.reset(); historyCache = null; cooldownCache = null; signals.clear(); sessionVibe.reset(); synchronized(steerList) { steerList.clear() }; rebuildSteerState() }
    fun resetFeedback() = feedback.reset()
    fun decisionSummary(): String = lastDecisions.take(5).joinToString("\n\n") {
        "${it.song.title}: ${it.source}\n" + it.components.entries.joinToString { (key, value) -> "$key=${"%.1f".format(value)}" }
    }


    suspend fun attribute(song: Song, item: androidx.media3.common.MediaItem, queueRevision: Long): androidx.media3.common.MediaItem {
        val decision = lastDecisions.firstOrNull { it.song.id == song.id }
        decision?.let { d ->
            val insight = MixInsight(explain(d), d.source == "discovery" && d.friendId == null)
            _insights.value = (_insights.value - song.id + (song.id to insight)).entries.toList()
                .takeLast(MAX_INSIGHTS).associate { it.key to it.value }
        }
        // A friend's song (in the room, or from a friend playlist you saved): their picture on the row.
        val friendId = decision?.friendId ?: snapshot?.friendSongs?.get(song.id)
        if (friendId != null) {
            try {
                val (name, avatar) = friendsInRoom.get().displayOf(friendId)
                com.theveloper.pixelplay.data.social.FriendQueueAttribution.tag(listOf(song.id), friendId, name, avatar)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { }
            if (decision?.friendId != null) synchronized(friendPicks) { friendPicks[friendId] = (friendPicks[friendId] ?: 0) + 1 }
        }
        val id = java.util.UUID.randomUUID().toString()
        val entry = MixRecommendation(id, song.id, mixIdentity(song), learning.sessionId,
            feedback.activeMixId, System.currentTimeMillis(), decision?.source ?: "seed",
            MixSequencePlanner.MODEL_VERSION, queueRevision, decision?.selectionProbability ?: 1.0,
            decision?.explored ?: false, com.google.gson.Gson().toJson(decision?.components.orEmpty()))
        // Never let history storage failures prevent playback; absence of attribution is explicit.
        try { learning.recordRecommendation(entry) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { return MixQueueMetadata.tag(item, "", learning.sessionId, mixIdentity(song)) }
        return MixQueueMetadata.tag(item, id, learning.sessionId, mixIdentity(song))
    }

    fun dislike(song: Song) {
        sessionVibe.push("disliked:" + mixIdentity(song), feelOf(song), SessionVibe.DISLIKED)
        feedback.dislike(song)
    }
    fun isDisliked(song: Song) = feedback.isDisliked(song)

    /**
     * Plans up to [limit] songs. Never gives up because feedback changed while planning (the
     * caller discards stale plans itself). Smart Mix uses cached online results when it has
     * them; when a lookup is still running it returns a short first batch ([PARTIAL_LIMIT]) so
     * songs start arriving at once, and the next call includes what the lookup found.
     */
    suspend fun next(flavor: MixFlavor, seeds: List<Song>, excluded: Set<String>, userPicks: List<Song> = emptyList(), limit: Int = 12): List<Song> = withContext(Dispatchers.IO) {
        feedback.awaitReady()
        val mixId = feedback.activeMixId
        val lockedVibe = activeVibe
        var effectiveLimit = limit
        // "More like this" wins; otherwise the song the user most recently queued steers the mix.
        // A typed prompt counts as explicit too; "More like this" on a song still wins.
        val prompt = promptSeeds
        val explicit = direction ?: prompt.firstOrNull()
        val steer = explicit ?: userPicks.lastOrNull { !feedback.isDisliked(it) }
        // A queued song nudges the mix rather than taking it over: it pulls harder when it
        // already fits the session and only a little when it's a different vibe, so the mix
        // drifts toward it over a few songs instead of jumping.
        val directionWeight = when {
            steer == null -> 0.0
            explicit != null -> 1.5
            else -> {
                val context = seeds.filterNot { mixIdentity(it) in userPicks.map(::mixIdentity) }
                if (context.isEmpty() || mixAffinity(steer, context) >= 4) 0.9 else 0.45
            }
        }
        val acceptedSeeds = (seeds + prompt + listOfNotNull(steer)).filterNot(feedback::isDisliked).distinctBy(::mixIdentity)
            .map(gatherer::applyCached)
        // Seeds without a genre/BPM/mood get looked up now, so the next pass can match on them.
        gatherer.gatherInBackground(acceptedSeeds)
        val libraryState = librarySnapshot()
        val explicitFavorites = libraryState.explicitFavorites
        val library = libraryState.library
        // A fresh session starts its vibe from the seeds.
        if (sessionVibe.target() == null) {
            sessionVibe.seed(Feel.average(acceptedSeeds.takeLast(4).map { seed ->
                Feel.of(libraryState.byId[seed.id] ?: withAnalysis(seed, libraryState.features), libraryState.embeddings)
            }))
        }
        // Friends in the room (Mix it in): their songs join the pool, tagged with the friend.
        val friendController = try { friendsInRoom.get() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }
        val friendPool = try { friendController?.mixPool().orEmpty() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { emptyMap() }
        val friendOf = HashMap<String, String>()
        for ((friendId, songs) in friendPool) for (song in songs) if (song.id !in libraryState.ownIds) friendOf.putIfAbsent(song.id, friendId)
        val friendShare = if (friendOf.isEmpty()) 0.0 else friendController?.mixShare() ?: 0.0
        val friendCandidates = friendPool.values.asSequence().flatten().filter { it.id in friendOf }.distinctBy { it.id }
            .map { libraryState.byId[it.id] ?: withAnalysis(gatherer.applyCached(it), libraryState.features) }.toList()
        var freshIds: Set<String> = emptySet()
        val candidates = (if (flavor == MixFlavor.NORMAL) library else {
            val libraryKeys = library.flatMapTo(HashSet(), ::recordingKeys)
            // A locked vibe searches for itself first ("workout songs", "edm songs"…).
            val vibeQueries = lockedVibe?.let { v ->
                listOf(v.query ?: "${v.label} songs") + v.genres.take(1).map { "$it songs" }
            }.orEmpty()
            val queries = (vibeQueries + acceptedSeeds.takeLast(4).map { it.artist.trim() } + acceptedSeeds.mapNotNull { it.genre?.takeIf(String::isNotBlank)?.let { genre -> "$genre songs" } }).filter { it.isNotBlank() && it != "Unknown Artist" }.distinct()
                // Nothing to go on (no seeds): still find something rather than nothing.
                .ifEmpty { DEFAULT_DISCOVERY_QUERIES }
            val discovered = coroutineScope {
                val related = (acceptedSeeds.takeLast(2) + listOfNotNull(steer)).mapNotNull { it.youtubeId }.distinct().take(3).map { id ->
                    async { discoverCached("related:$id", DISCOVERY_WAIT_MS) { youtube.relatedSongs(id) } }
                }
                val searched = queries.take(3).map { query ->
                    async {
                        discoverCached("search:" + query.lowercase(java.util.Locale.ROOT), DISCOVERY_WAIT_MS) { youtube.searchSongs(query) }
                            ?: youtube.cachedSongs(query).takeIf { it.isNotEmpty() }
                    }
                }
                val results = (related + searched).awaitAll()
                // A lookup is still running: plan a short batch now, the rest once it lands.
                if (results.any { it == null }) effectiveLimit = minOf(limit, PARTIAL_LIMIT)
                results.filterNotNull().flatten().map(gatherer::applyCached)
            }
            val fresh = discovered.filterNot { song -> recordingKeys(song).any { it in libraryKeys } }
            freshIds = fresh.mapTo(HashSet()) { it.id }
            // Discoveries usually arrive with no genre / BPM / mood. Look up the most relevant
            // ones now (Deezer + iTunes, cached on disk) so the next plan can match on them.
            val affinity = MixAffinityIndex(acceptedSeeds)
            gatherer.gatherInBackground(fresh.filter(gatherer::needsGathering).sortedByDescending(affinity::affinity).take(24))
            library + fresh
        }) + friendCandidates
        val removed = offVibeSongs()
        val skips = skippedSongs()
        val removedKeys = removed.flatMapTo(HashSet(), ::recordingKeys)
        val eligibleAll = candidates.asSequence()
            // Online results sometimes carry only a video id; they're playable too.
            .filter { it.contentUriString.isNotBlank() || !it.youtubeId.isNullOrBlank() }
            .filter { song -> recordingKeys(song).none { it in excluded || it in removedKeys } }
            .filter { !feedback.isDisliked(it, mixId) }
            .filterNot(::isSteeredAway)
            .distinctBy { "rec:" + gatherer.recordingKey(it) }.toList()
        val (vibePool, vibeBoosts) = applyVibe(lockedVibe, eligibleAll, limit)
        val history = history()
        val seedsAnalysed = acceptedSeeds.map { libraryState.byId[it.id] ?: withAnalysis(it, libraryState.features) }
        // Only the few hundred songs worth planning go through the full planner.
        val eligible = shortlist(vibePool, seedsAnalysed, vibeBoosts, explicitFavorites, libraryState.embeddings, freshIds, steer,
            friendOf = if (friendShare > 0.0) friendOf else emptyMap())
        val weights = MixWeights.DEFAULT.withVariety(variety)
        val decisions = MixSequencePlanner.plan(eligible, seedsAnalysed, explicitFavorites,
            libraryState.identities, history, mixId,
            // Normal Mix stays in the library, but the slider still decides how many unplayed
            // library songs vs. familiar favourites it picks.
            discoveryBalance, System.currentTimeMillis(), catalogue = library + eligible + seedsAnalysed, random = kotlin.random.Random.Default, limit = effectiveLimit, direction = steer, directionWeight = directionWeight,
            penalties = eligible.associate { it.id to feedback.removalPenalty(it) + offVibePenalty(it, removed) + offVibePenalty(it, skips) * SKIP_PENALTY_SHARE + lessPenalty(it) },
            boosts = vibeBoosts,
            identity = { song -> "rec:" + gatherer.recordingKey(song) },
            weights = weights,
            sessionId = learning.sessionId,
            extras = mapOf("soundAlike" to soundAlike(eligible, seedsAnalysed, libraryState.embeddings, weights.soundAlike),
                "feelFit" to feelFitScores(eligible, libraryState.embeddings, weights)) +
                vibeSignalComponents(eligible, seedsAnalysed, libraryState.embeddings),
            microSkipCooldowns = cooldowns(),
            energyAt = { position -> this@AdaptiveMix.energyAt(position) },
            energyWeight = energyWeight(weights),
            friendOf = if (friendShare > 0.0) friendOf else emptyMap(),
            friendShare = friendShare,
            friendDeficit = friendDeficit(friendPool.keys),
            artistGaps = lessArtists.associateWith { LESS_ARTIST_GAP })
        // Stale plans (feedback changed meanwhile) are discarded by the caller, not here: an
        // empty answer used to read as "no suitable songs".
        lastDecisions = decisions
        decisions.map { it.song }
    }

    /**
     * The best order for songs already in the queue (the mix's own upcoming songs), given what
     * is playing now and everything learned this session. Nothing is fetched or resolved, so it
     * is cheap enough to run after every song. Returns song ids, best first; songs excluded
     * since they were queued are left out (the caller leaves them where they are).
     */
    suspend fun rerank(songs: List<Song>, seeds: List<Song>): List<String> = withContext(Dispatchers.Default) {
        if (songs.size < 2) return@withContext songs.map { it.id }
        feedback.awaitReady()
        val mixId = feedback.activeMixId
        val history = history()
        val library = librarySnapshot()
        val keep = songs.map { library.byId[it.id] ?: withAnalysis(gatherer.applyCached(it), library.features) }
            .filterNot { feedback.isDisliked(it, mixId) || isSteeredAway(it) }
        val seedsAnalysed = seeds.map { library.byId[it.id] ?: withAnalysis(gatherer.applyCached(it), library.features) }
        val weights = MixWeights.DEFAULT.withVariety(variety)
        val (pool, boosts) = applyVibe(activeVibe, keep, Int.MAX_VALUE / 4)
        val removed = offVibeSongs()
        val skips = skippedSongs()
        MixSequencePlanner.plan(pool, seedsAnalysed, library.explicitFavorites,
            library.identities, history, mixId,
            discovery = discoveryBalance,
            now = System.currentTimeMillis(), limit = pool.size, catalogue = library.library + pool,
            penalties = pool.associate { it.id to feedback.removalPenalty(it) + offVibePenalty(it, removed) + offVibePenalty(it, skips) * SKIP_PENALTY_SHARE + lessPenalty(it) },
            direction = direction ?: promptSeeds.firstOrNull(),
            boosts = boosts,
            identity = { song -> "rec:" + gatherer.recordingKey(song) },
            weights = weights,
            sessionId = learning.sessionId,
            extras = mapOf("soundAlike" to soundAlike(pool, seedsAnalysed, library.embeddings, weights.soundAlike),
                "feelFit" to feelFitScores(pool, library.embeddings, weights)) +
                vibeSignalComponents(pool, seedsAnalysed, library.embeddings),
            microSkipCooldowns = cooldowns(),
            energyAt = { position -> this@AdaptiveMix.energyAt(position) },
            energyWeight = energyWeight(weights),
            artistGaps = lessArtists.associateWith { LESS_ARTIST_GAP }
        ).map { it.song.id }
    }

    /**
     * Keeps [candidates] to a locked vibe. A song passes when it fits the filter, or when it is
     * by the same artist as songs that do and doesn't clash with it,
     * so streams with thin metadata can still join. If that leaves too few songs for a batch,
     * nothing is removed and the vibe only steers through the score. Returns the candidates and
     * each one's "vibeFit" boost.
     */
    private fun applyVibe(
        filter: VibeFilter?,
        candidates: List<Song>,
        limit: Int
    ): Pair<List<Song>, Map<String, Double>> {
        if (filter == null || candidates.isEmpty()) return candidates to emptyMap()
        val fit = candidates.associate { it.id to MusicVibeFilters.score(it, filter) }
        val matches = candidates.filter { (fit[it.id] ?: 0.0) >= MusicVibeFilters.MATCH_THRESHOLD }
            .sortedByDescending { fit[it.id] }.take(60)
        val related = MixAffinityIndex(matches)
        val kept = candidates.filter { song ->
            val score = fit[song.id] ?: 0.0
            // Same artist as a matching song (affinity ≥ 6); a shared broad catalogue genre
            // ("Pop") alone is too loose to let a song in, it only earns part credit below.
            score >= MusicVibeFilters.MATCH_THRESHOLD || (score >= 0.0 && related.affinity(song) >= 6)
        }
        val pool = if (kept.size >= limit * 2) kept else candidates
        return pool to pool.associate { song ->
            val score = fit[song.id] ?: 0.0
            // Close relatives of matching songs get part credit; a clash is a penalty.
            val relation = if (score < MusicVibeFilters.MATCH_THRESHOLD) related.affinity(song) * 0.25 else 0.0
            song.id to (score.coerceIn(-3.0, 8.0) * 0.8 + relation)
        }
    }

    private companion object {
        const val GROUP_MORE_LIKE = "more-like"
        const val GROUP_TOWARD = "toward"
        const val GROUP_BLEND = "blend"
        const val GROUP_PATH = "path"
        /** Steer chips at once; the oldest goes when a fourth is added. */
        const val MAX_STEERS = 3
        /** "less <artist>": score penalty, and the artist can't return for [LESS_ARTIST_GAP] songs. */
        const val LESS_ARTIST_PENALTY = 3.0
        const val LESS_ARTIST_GAP = 10
        const val LESS_GENRE_PENALTY = 1.5
        /** "start with X, end with Y" moves over this many songs. */
        const val PATH_SONGS = 20
        /** A new Energy setting eases in over this many songs. */
        const val ENERGY_EASE_SONGS = 3.0
        /** Energy Follow pulls at this share of a chosen setting's weight. */
        const val FOLLOW_ENERGY_SHARE = 0.67
        /** Friend songs kept in the shortlist (so the planner can see them). */
        const val SHORTLIST_FRIENDS = 160
        const val MAX_OFF_VIBE = 8
        const val MAX_SKIPPED = 12
        /** A skip counts half as much as removing the song from the queue. */
        const val SKIP_PENALTY_SHARE = 0.5
        const val MAX_PROMPT_SEEDS = 5
        /** Seeds kept for a typed prompt with a sound in it (more parts → wider steer). */
        const val MAX_PROMPT_INTENT_SEEDS = 12
        const val HISTORY_TTL_MS = 60_000L
        /** Candidates the full planner scores (best cheap matches + a random slice for variety). */
        const val SHORTLIST_TOP = 320
        const val SHORTLIST_SOUND = 80
        const val SHORTLIST_RANDOM = 100
        const val SHORTLIST_FRESH = 150
        /** Weight of "sounds like a song removed from the queue, more than like what's playing". */
        const val REMOVED_VIBE_WEIGHT = 7.0
        /** Weight of "feels like the songs queued by hand this session". */
        const val PICKED_VIBE_WEIGHT = 5.0
        /** Feel similarity at which an upcoming mix song counts as "the same as" a removed one. */
        const val FEELS_LIKE_REMOVED = 0.78
        const val LIBRARY_TTL_MS = 90_000L
        /** A snapshot older than this is rebuilt before planning instead of in the background. */
        const val LIBRARY_MAX_STALE_MS = 30 * 60_000L
        /** How long a plan waits for an uncached online lookup (it keeps going in the background). */
        const val DISCOVERY_WAIT_MS = 1_200L
        const val DISCOVERY_FETCH_TIMEOUT_MS = 12_000L
        const val DISCOVERY_REFRESH_MS = 10 * 60_000L
        const val DISCOVERY_MAX_AGE_MS = 60 * 60_000L
        const val MAX_DISCOVERY_ENTRIES = 200
        /** First batch while online lookups are still running. */
        const val PARTIAL_LIMIT = 6
        val DEFAULT_DISCOVERY_QUERIES = listOf("top hits", "new music this week")
        const val MAX_INSIGHTS = 300
    }
}
