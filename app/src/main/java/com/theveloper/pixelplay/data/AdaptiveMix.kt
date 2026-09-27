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
class AdaptiveMix @Inject constructor(private val music: MusicRepository, private val youtube: YouTubeRepository, private val connected: com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository, private val playlists: com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository, private val cloud: com.theveloper.pixelplay.data.database.CloudSongDao, private val feedback: MixFeedback, private val learning: MixLearning, private val gatherer: com.theveloper.pixelplay.data.metadata.SongMetadataGatherer, private val enrichment: com.theveloper.pixelplay.data.database.EnrichmentDao) {
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
        val embeddings: Map<String, FloatArray> = emptyMap()
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
        val library = (music.getAllSongsOnce() + connectedSnapshot.playlists.flatMap { it.songs } + connectedSnapshot.spotifyLikes +
            connectedSnapshot.youtubeLikes + favorites).distinctBy { it.id }
            .map { withAnalysis(gatherer.applyCached(it), features) }
        LibrarySnapshot(library, explicitFavorites, android.os.SystemClock.elapsedRealtime(), features, embeddings).also { snapshot = it }
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
        if (!keepPrompt) promptSeeds = emptyList()
        if (!keepVibe) vibe = null
        synchronized(offVibe) { offVibe.clear() }
        synchronized(skipped) { skipped.clear() }
        _insights.value = emptyMap()
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
    fun dislikeEverywhere(song: Song) = feedback.dislike(song, null)
    val revision get() = feedback.revision
    @Volatile internal var lastDecisions: List<MixSequencePlanner.Decision> = emptyList()
        private set
    var discoveryBalance: Double
        get() = feedback.discoveryBalance()
        set(value) { feedback.setDiscoveryBalance(value) }
    /** Energy the listener asked for (Calm 0.25 · Steady 0.55 · Hype 0.85), or null to follow the music. */
    var energyTarget: Double?
        get() = feedback.energyTarget()
        set(value) { feedback.setEnergyTarget(value) }

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
            "sessionFit" to "Fits what's playing"
        )
        val best = phrases.filter { (key, phrase) -> phrase != null && (c[key] ?: 0.0) > 0.5 }
            .maxByOrNull { (key, _) -> c[key] ?: 0.0 }?.second
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
    fun moreLike(song: Song) { direction = song; feedback.invalidate() }

    /** The song "More like this" was used on, if any (shown as a steer the user can clear). */
    val moreLikeSong: Song? get() = direction

    /**
     * Songs matching what the user typed in the queue's prompt box ("more of this song/artist").
     * They join the seeds and the first one sets the direction, like "More like this", for the
     * rest of the mix session or until cleared.
     */
    @Volatile private var promptSeeds: List<Song> = emptyList()

    fun steerTowards(songs: List<Song>) {
        promptSeeds = songs.take(MAX_PROMPT_SEEDS)
        feedback.invalidate()
    }

    /** Clears every explicit steer: the typed prompt and "More like this". */
    fun clearPromptSteer() {
        if (promptSeeds.isEmpty() && direction == null) return
        promptSeeds = emptyList()
        direction = null
        feedback.invalidate()
    }

    /**
     * Songs for a typed song or artist: library matches first (artist, then title), topped up
     * with an online search when the library has none. Best match first.
     */
    suspend fun resolvePrompt(query: String): List<Song> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val needle = q.lowercase(java.util.Locale.ROOT)
        val snapshot = connected.snapshot.value
        val library = (music.getAllSongsOnce() + snapshot.playlists.flatMap { it.songs } + snapshot.spotifyLikes + snapshot.youtubeLikes)
            .filter { it.contentUriString.isNotBlank() && !feedback.isDisliked(it) }
            .distinctBy(::mixIdentity)
        fun rank(song: Song): Int {
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
        val local = library.map { it to rank(it) }.filter { it.second >= 0 }
            .sortedBy { it.second }.map { it.first }.take(MAX_PROMPT_SEEDS)
        if (local.isNotEmpty()) return@withContext local
        discover { youtube.searchSongs(q) }
            .filter { it.contentUriString.isNotBlank() || it.youtubeId != null }
            .map(gatherer::applyCached)
            .take(MAX_PROMPT_SEEDS)
    }

    /**
     * Songs removed from the queue during this mix, newest last. Removing a song is a soft
     * "doesn't fit the vibe" signal: unlike a dislike it is never stored, never excludes the
     * artist, and only lowers songs that resemble it for the rest of this mix session.
     */
    private val offVibe = ArrayDeque<Song>()

    fun markOffVibe(song: Song) {
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
        val history = try { learning.recent() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
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

    fun markSkipped(song: Song) {
        synchronized(skipped) {
            skipped.removeAll { mixIdentity(it) == mixIdentity(song) }
            skipped.addLast(song)
            while (skipped.size > MAX_SKIPPED) skipped.removeFirst()
        }
        feedback.invalidate()
    }

    /** A skipped song was played again / queued by hand: it wasn't a rejection after all. */
    fun clearSkipped(song: Song) {
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
    fun heardTooMuch(song: Song) = feedback.heardTooMuch(song)
    suspend fun resetLearning() { direction = null; learning.reset() }
    fun resetFeedback() = feedback.reset()
    fun decisionSummary(): String = lastDecisions.take(5).joinToString("\n\n") {
        "${it.song.title}: ${it.source}\n" + it.components.entries.joinToString { (key, value) -> "$key=${"%.1f".format(value)}" }
    }


    suspend fun attribute(song: Song, item: androidx.media3.common.MediaItem, queueRevision: Long): androidx.media3.common.MediaItem {
        val decision = lastDecisions.firstOrNull { it.song.id == song.id }
        decision?.let { d ->
            val insight = MixInsight(explain(d), d.source == "discovery")
            _insights.value = (_insights.value - song.id + (song.id to insight)).entries.toList()
                .takeLast(MAX_INSIGHTS).associate { it.key to it.value }
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

    fun dislike(song: Song) = feedback.dislike(song)
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
        val lockedVibe = vibe
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
        val candidates = if (flavor == MixFlavor.NORMAL) library else {
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
            // Discoveries usually arrive with no genre / BPM / mood. Look up the most relevant
            // ones now (Deezer + iTunes, cached on disk) so the next plan can match on them.
            val affinity = MixAffinityIndex(acceptedSeeds)
            gatherer.gatherInBackground(fresh.filter(gatherer::needsGathering).sortedByDescending(affinity::affinity).take(24))
            library + fresh
        }
        val removed = offVibeSongs()
        val skips = skippedSongs()
        val removedKeys = removed.flatMapTo(HashSet(), ::recordingKeys)
        val eligibleAll = candidates.asSequence()
            // Online results sometimes carry only a video id; they're playable too.
            .filter { it.contentUriString.isNotBlank() || !it.youtubeId.isNullOrBlank() }
            .filter { song -> recordingKeys(song).none { it in excluded || it in removedKeys } }
            .filter { !feedback.isDisliked(it, mixId) }
            .distinctBy { "rec:" + gatherer.recordingKey(it) }.toList()
        val (eligible, vibeBoosts) = applyVibe(lockedVibe, eligibleAll, limit)
        val history = try { learning.recent() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
        val seedsAnalysed = acceptedSeeds.map { libraryState.byId[it.id] ?: withAnalysis(it, libraryState.features) }
        val weights = MixWeights.DEFAULT.withVariety(variety)
        val decisions = MixSequencePlanner.plan(eligible, seedsAnalysed, explicitFavorites,
            libraryState.identities, history, mixId,
            // Normal Mix stays in the library, but the slider still decides how many unplayed
            // library songs vs. familiar favourites it picks.
            discoveryBalance, System.currentTimeMillis(), catalogue = library + eligible + seedsAnalysed, random = kotlin.random.Random.Default, limit = effectiveLimit, direction = steer, directionWeight = directionWeight,
            penalties = eligible.associate { it.id to feedback.removalPenalty(it) + offVibePenalty(it, removed) + offVibePenalty(it, skips) * SKIP_PENALTY_SHARE },
            boosts = vibeBoosts,
            identity = { song -> "rec:" + gatherer.recordingKey(song) },
            weights = weights,
            sessionId = learning.sessionId,
            extras = mapOf("soundAlike" to soundAlike(eligible, seedsAnalysed, libraryState.embeddings, weights.soundAlike)),
            energyTarget = energyTarget)
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
        val history = try { learning.recent() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
        val library = librarySnapshot()
        val keep = songs.map { library.byId[it.id] ?: withAnalysis(gatherer.applyCached(it), library.features) }
            .filterNot { feedback.isDisliked(it, mixId) }
        val seedsAnalysed = seeds.map { library.byId[it.id] ?: withAnalysis(gatherer.applyCached(it), library.features) }
        val weights = MixWeights.DEFAULT.withVariety(variety)
        val (pool, boosts) = applyVibe(vibe, keep, Int.MAX_VALUE / 4)
        val removed = offVibeSongs()
        val skips = skippedSongs()
        MixSequencePlanner.plan(pool, seedsAnalysed, library.explicitFavorites,
            library.identities, history, mixId,
            discovery = discoveryBalance,
            now = System.currentTimeMillis(), limit = pool.size, catalogue = library.library + pool,
            penalties = pool.associate { it.id to feedback.removalPenalty(it) + offVibePenalty(it, removed) + offVibePenalty(it, skips) * SKIP_PENALTY_SHARE },
            direction = direction ?: promptSeeds.firstOrNull(),
            boosts = boosts,
            identity = { song -> "rec:" + gatherer.recordingKey(song) },
            weights = weights,
            sessionId = learning.sessionId,
            extras = mapOf("soundAlike" to soundAlike(pool, seedsAnalysed, library.embeddings, weights.soundAlike)),
            energyTarget = energyTarget
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
        const val MAX_OFF_VIBE = 8
        const val MAX_SKIPPED = 12
        /** A skip counts half as much as removing the song from the queue. */
        const val SKIP_PENALTY_SHARE = 0.5
        const val MAX_PROMPT_SEEDS = 5
        const val LIBRARY_TTL_MS = 90_000L
        /** A snapshot older than this is rebuilt before planning instead of in the background. */
        const val LIBRARY_MAX_STALE_MS = 30 * 60_000L
        /** How long a plan waits for an uncached online lookup (it keeps going in the background). */
        const val DISCOVERY_WAIT_MS = 2_500L
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
