package com.theveloper.pixelplay.data.metadata

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.lastfm.LastFmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Fills in what a gathered song is missing — genre, duration, album, album artist, artist,
 * year, track / disc number, BPM, ISRC, label, explicit flag, a high-resolution cover and
 * mood — so songs pulled in by mixes, searches and likes carry the same information as
 * library files.
 *
 * Sources (all free, no key), queried **in parallel**:
 * - Deezer: exact `/track/isrc:` lookup when the ISRC is known, otherwise a scored search;
 *   then track + album details fetched together (BPM, ISRC, gain, label, UPC, genre, cover_xl).
 * - iTunes Search: one call gives genre, track / disc number, release date, explicit flag,
 *   copyright-free metadata and the best free cover (up to 3000 px; we ask for 1400).
 * Then Last.fm tags when the user has a Last.fm key (genre fallback and mood). If no source
 * gives a mood it is estimated from genre + BPM and marked as estimated.
 *
 * Only blank fields are ever filled; nothing the file or the user set is overwritten.
 * Results are cached on disk by title + artist, so each song is looked up once.
 */
@Singleton
class SongMetadataGatherer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lastFm: LastFmRepository,
    private val metadataStore: SongMetadataStore,
    okHttpClient: okhttp3.OkHttpClient
) {
    /** What was found for one song. Any field may be null. */
    data class Gathered(
        val genre: String? = null,
        val album: String? = null,
        val artist: String? = null,
        val durationMs: Long? = null,
        val year: Int? = null,
        val bpm: Float? = null,
        val mood: String? = null,
        val moodEstimated: Boolean = false,
        val albumArtist: String? = null,
        val trackNumber: Int? = null,
        val discNumber: Int? = null,
        /** Release date as "yyyy-MM-dd" (or a prefix of it). */
        val releaseDate: String? = null,
        /** Best cover found, already sized to [ArtworkUrls.DEFAULT_SIZE] where the host allows. */
        val coverUrl: String? = null,
        val isrc: String? = null,
        val label: String? = null,
        val upc: String? = null,
        val explicit: Boolean? = null,
        /** Deezer's loudness gain in dB (informational; not used for playback). */
        val gainDb: Float? = null,
        val sources: List<String> = emptyList(),
        /** When the lookup ran; failed lookups are retried after [RETRY_MISS_MS]. */
        val at: Long = System.currentTimeMillis(),
        val found: Boolean = true
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = com.theveloper.pixelplay.utils.BoundedCache<String, Gathered>(MAX_CACHE)
    private val inFlight = ConcurrentHashMap<String, Deferred<Gathered?>>()
    private val network = Semaphore(3)
    private val deezerPace = kotlinx.coroutines.sync.Mutex()
    @Volatile private var lastDeezerCallAt = 0L
    /** v2 adds track / disc number, covers, ISRC, label etc.; v1 entries are simply re-gathered. */
    private val file = File(context.filesDir, "gathered_song_metadata_v2.json")
    @Volatile private var iTunesBlockedUntil = 0L
    @Volatile private var saveJob: Job? = null

    init {
        scope.launch { load() }
    }

    /** True when [song] lacks anything this class can fill. */
    fun needsGathering(song: Song): Boolean =
        song.genre.isPlaceholderGenre() || song.album.isPlaceholderAlbum() || song.duration <= 0 ||
            song.artist.isBlankOrUnknown() || song.musicalFeatures.bpm == null || song.mixIntelligence.mood.isNullOrBlank() ||
            (!song.isLocal && (song.trackNumber <= 0 || ArtworkUrls.isLowQuality(song.albumArtUriString)))

    /** What was gathered for [song] (cached only, no network), or null. */
    fun cachedFor(song: Song): Gathered? = cache[keyOf(song)]?.takeIf { it.found }

    /**
     * Title + artist with upload noise removed ("(Official Video)", "feat. …", "- Topic").
     * Mixes use it to spot the same recording arriving as a library file and as a stream.
     */
    fun recordingKey(song: Song): String {
        // Called for every candidate on every mix plan; the regex clean-up is memoised.
        val raw = song.title + "\u0000" + song.artist
        recordingKeys[raw]?.let { return it }
        if (recordingKeys.size > 20_000) recordingKeys.clear()
        return keyOf(song).also { recordingKeys[raw] = it }
    }
    private val recordingKeys = ConcurrentHashMap<String, String>().also { map ->
        com.theveloper.pixelplay.data.diagnostics.HeapPressure.register("recording-keys") { map.clear() }
    }

    /**
     * True when the mood [song] carries was estimated from genre + BPM (no source had one).
     * Cheap: cache lookup only.
     */
    fun isMoodEstimated(song: Song): Boolean {
        val g = cache[keyOf(song)]?.takeIf { it.found && it.moodEstimated } ?: return false
        return song.mixIntelligence.mood.equals(g.mood, ignoreCase = true)
    }

    /** Fills blanks from what is already known. Cheap, no network — safe on any thread. */
    fun applyCached(song: Song): Song {
        val g = cache[keyOf(song)]?.takeIf { it.found } ?: return song
        return merge(song, g)
    }

    /**
     * Gathers whatever [song] is missing and returns it filled in. Waits at most [timeoutMs];
     * a lookup that takes longer finishes in the background and is used next time.
     */
    suspend fun gather(song: Song, timeoutMs: Long = 3_500): Song {
        val cached = applyCached(song)
        if (!needsGathering(cached)) return cached
        val key = keyOf(song)
        cache[key]?.let { if (!it.found && System.currentTimeMillis() - it.at < RETRY_MISS_MS) return cached }
        cache[key]?.let { if (it.found && System.currentTimeMillis() - it.at < REFRESH_MS) return cached }
        val pending = synchronized(inFlight) {
            inFlight[key] ?: run {
                if (inFlight.size >= 24) return cached
                scope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    try { withTimeoutOrNull(30_000) { lookup(song) } }
                    finally { synchronized(inFlight) { inFlight.remove(key) } }
                }.also { inFlight[key] = it }
            }
        }
        pending.start()
        val result = withTimeoutOrNull(timeoutMs) { pending.await() } ?: return cached
        return merge(song, result)
    }

    /**
     * True when [song] is missing one of the basics every song should show: duration, genre,
     * album or artist. (BPM and mood are extras and don't count here.)
     */
    fun needsBasics(song: Song): Boolean =
        song.duration <= 0 || song.genre.isPlaceholderGenre() || song.album.isPlaceholderAlbum() ||
            song.artist.isBlankOrUnknown()

    /**
     * Fills in duration, genre, album and artist for a whole list (a playlist, including
     * YouTube Music playlists), in list order so the top of the list fills first.
     *
     * Everything already known is applied straight away (one call to [onUpdated] with all of
     * them). Songs still missing basics are then looked up — only online songs (streams and
     * downloads); library files keep their own tags — and [onUpdated] is called with batches
     * of filled-in songs as they arrive. Results are cached on disk, so a playlist opened again
     * fills instantly and nothing is searched twice.
     */
    suspend fun gatherBasics(songs: List<Song>, onUpdated: (Map<String, Song>) -> Unit) {
        if (songs.isEmpty()) return
        val originals = songs.associateBy { it.id }
        val cached = originals.mapValues { (_, song) -> applyCached(song) }
        val changed = cached.filter { (id, song) -> originals[id] !== song }
        if (changed.isNotEmpty()) onUpdated(changed)

        // One lookup per title + artist (a playlist can hold the same song more than once).
        val byKey = cached.values.groupBy(::keyOf)
        val targets = byKey.values.mapNotNull { group -> group.firstOrNull { !it.isLocal && needsBasics(it) } }
        if (targets.isEmpty()) return

        val pending = ConcurrentHashMap<String, Song>()
        kotlinx.coroutines.coroutineScope {
            val flusher = launch {
                while (true) {
                    delay(600)
                    flush(pending, onUpdated)
                }
            }
            val next = java.util.concurrent.atomic.AtomicInteger(0)
            List(minOf(3, targets.size)) {
                async {
                    while (true) {
                        val song = targets.getOrNull(next.getAndIncrement()) ?: break
                        val filled = try { gather(song, timeoutMs = 30_000) }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { continue }
                        byKey[keyOf(song)].orEmpty().forEach { current ->
                            val merged = merge(current, filled)
                            if (merged != current) pending[current.id] = merged
                        }
                    }
                }
            }.forEach { it.await() }
            flusher.cancel()
            flush(pending, onUpdated)
        }
    }

    private fun flush(pending: ConcurrentHashMap<String, Song>, onUpdated: (Map<String, Song>) -> Unit) {
        if (pending.isEmpty()) return
        val batch = HashMap(pending)
        batch.keys.forEach { pending.remove(it) }
        onUpdated(batch)
    }

    /** Starts gathering in the background (no waiting). */
    fun gatherInBackground(songs: Collection<Song>) {
        songs.filter(::needsGathering).distinctBy(::keyOf).take(40).forEach { song ->
            scope.launch { runCatching { gather(song, timeoutMs = 20_000) } }
        }
    }

    fun gatherInBackground(song: Song) = gatherInBackground(listOf(song))

    // ── Lookup ─────────────────────────────────────────────────────────────────────────

    private suspend fun lookup(song: Song): Gathered? {
        if (!isOnline()) return null
        val title = cleanTitle(song.title)
        val artist = cleanArtist(song.artist)
        if (title.isBlank()) return null
        val isrc = song.creditsAndRelease.isrc?.trim()?.takeIf { it.length == 12 }

        var deezerFailed = false
        val result = network.withPermit {
            // Both catalogues at once: the slower one no longer adds to the wait.
            val (dz, itn) = kotlinx.coroutines.coroutineScope {
                val d = async {
                    runCatching { deezer(title, artist, song.duration, isrc) }
                        .onFailure {
                            deezerFailed = true
                            Timber.tag(TAG).d("Deezer lookup failed for %s: %s", title, it.message)
                        }.getOrNull()
                }
                val i = async {
                    runCatching { iTunes(title, artist, song.duration) }
                        .onFailure { Timber.tag(TAG).d("iTunes lookup failed for %s: %s", title, it.message) }
                        .getOrNull()
                }
                d.await() to i.await()
            }
            var g = combine(dz, itn)

            // Last.fm tags (only when the user set a Last.fm key): genre fallback + mood.
            if (g.genre == null || g.mood == null) {
                val tags = runCatching { lastFm.getTrackInfo(g.artist ?: artist, title)?.tags }.getOrNull().orEmpty()
                if (tags.isNotEmpty()) {
                    g = g.copy(
                        genre = g.genre ?: tags.firstOrNull { it.lowercase() in KNOWN_GENRE_WORDS || KNOWN_GENRE_WORDS.any { w -> it.lowercase().contains(w) } }
                            ?.replaceFirstChar { it.titlecase(Locale.ROOT) },
                        mood = g.mood ?: moodFromTags(tags),
                        sources = g.sources + "Last.fm",
                        found = true
                    )
                }
            }
            if (g.mood == null) {
                estimateMood(g.genre ?: song.genre, g.bpm ?: song.musicalFeatures.bpm)?.let {
                    g = g.copy(mood = it, moodEstimated = true)
                }
            }
            g.copy(at = System.currentTimeMillis())
        }
        // A miss caused by a network error or Deezer's rate limit isn't a real miss: don't
        // remember it, so the song is tried again next time instead of after 24 h.
        if (deezerFailed && !result.found) return null
        cache[keyOf(song)] = result
        scheduleSave()
        if (result.found) recordClaims(song, result)
        return result
    }

    /**
     * Deezer is preferred for identity and audio facts (ISRC, BPM, label, gain), iTunes for
     * the cover (bigger) and track / disc numbers (more reliable). Either fills the other's gaps.
     */
    private fun combine(dz: Gathered?, itn: Gathered?): Gathered {
        if (dz == null && itn == null) return Gathered(found = false)
        if (dz == null) return itn!!
        if (itn == null) return dz
        return Gathered(
            genre = dz.genre ?: itn.genre,
            album = dz.album ?: itn.album,
            artist = dz.artist ?: itn.artist,
            durationMs = dz.durationMs ?: itn.durationMs,
            year = dz.year ?: itn.year,
            bpm = dz.bpm,
            albumArtist = dz.albumArtist ?: itn.albumArtist,
            trackNumber = itn.trackNumber ?: dz.trackNumber,
            discNumber = itn.discNumber ?: dz.discNumber,
            releaseDate = dz.releaseDate ?: itn.releaseDate,
            coverUrl = itn.coverUrl ?: dz.coverUrl,
            isrc = dz.isrc,
            label = dz.label,
            upc = dz.upc,
            explicit = dz.explicit ?: itn.explicit,
            gainDb = dz.gainDb,
            sources = dz.sources + itn.sources,
            found = true
        )
    }

    /**
     * Deezer: exact ISRC lookup when possible, else the best search match by title / artist /
     * duration. Track and album details are then fetched together.
     */
    private suspend fun deezer(title: String, artist: String, durationMs: Long, isrc: String?): Gathered? = withContext(Dispatchers.IO) {
        var trackFromIsrc = isrc?.let { deezerJson("https://api.deezer.com/track/isrc:$it") }
            ?.takeIf { it.optLong("id") > 0 }
        // An ISRC hit still has to be the same song (bad ISRCs exist on YouTube uploads).
        if (trackFromIsrc != null && matchScore(trackFromIsrc, title, artist, durationMs) < MIN_MATCH) trackFromIsrc = null

        val best: JSONObject = trackFromIsrc ?: run {
            val q = if (artist.isNotBlank()) "artist:\"$artist\" track:\"$title\"" else title
            val search = deezerJson("https://api.deezer.com/search?limit=8&q=" + URLEncoder.encode(q, "UTF-8"))
                ?: throw java.io.IOException("Deezer search unavailable")
            var hits = search.optJSONArray("data") ?: JSONArray()
            if (hits.length() == 0 && artist.isNotBlank()) {
                hits = deezerJson("https://api.deezer.com/search?limit=8&q=" + URLEncoder.encode("$artist $title", "UTF-8"))
                    ?.optJSONArray("data") ?: JSONArray()
            }
            (0 until hits.length()).mapNotNull { hits.optJSONObject(it) }
                .map { it to matchScore(it, title, artist, durationMs) }
                .filter { it.second >= MIN_MATCH }
                .maxByOrNull { it.second }?.first
        } ?: return@withContext null

        val trackId = best.optLong("id")
        val albumId = best.optJSONObject("album")?.optLong("id") ?: 0L
        val (track, album) = kotlinx.coroutines.coroutineScope {
            val t = async { if (trackFromIsrc != null) trackFromIsrc else if (trackId > 0) deezerJson("https://api.deezer.com/track/$trackId") else null }
            val a = async { if (albumId > 0) deezerJson("https://api.deezer.com/album/$albumId") else null }
            t.await() to a.await()
        }

        val genre = album?.optJSONObject("genres")?.optJSONArray("data")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }.firstOrNull()
        }
        val bpm = track?.optDouble("bpm")?.takeIf { !it.isNaN() && it in 40.0..260.0 }?.toFloat()
        val durationSec = (track?.optInt("duration") ?: 0).takeIf { it > 0 } ?: best.optInt("duration")
        val releaseDate = track?.optString("release_date")?.takeIf { it.length >= 4 }
            ?: album?.optString("release_date")?.takeIf { it.length >= 4 }
        val albumObj = track?.optJSONObject("album") ?: best.optJSONObject("album")
        val cover = album?.optString("cover_xl")?.takeIf(String::isNotBlank)
            ?: albumObj?.optString("cover_xl")?.takeIf(String::isNotBlank)
        Gathered(
            genre = genre,
            album = albumObj?.optString("title")?.takeIf(String::isNotBlank),
            artist = best.optJSONObject("artist")?.optString("name")?.takeIf(String::isNotBlank),
            durationMs = durationSec.takeIf { it > 0 }?.times(1000L),
            year = releaseDate?.take(4)?.toIntOrNull(),
            bpm = bpm,
            albumArtist = album?.optJSONObject("artist")?.optString("name")?.takeIf(String::isNotBlank),
            trackNumber = track?.optInt("track_position")?.takeIf { it > 0 },
            discNumber = track?.optInt("disk_number")?.takeIf { it > 0 },
            releaseDate = releaseDate,
            coverUrl = ArtworkUrls.upgrade(cover),
            isrc = track?.optString("isrc")?.takeIf { it.length == 12 },
            label = album?.optString("label")?.takeIf(String::isNotBlank),
            upc = album?.optString("upc")?.takeIf(String::isNotBlank),
            explicit = track?.takeIf { it.has("explicit_lyrics") }?.optBoolean("explicit_lyrics"),
            gainDb = track?.optDouble("gain")?.takeIf { !it.isNaN() && it != 0.0 }?.toFloat(),
            sources = listOf("Deezer"),
            found = true
        )
    }

    /**
     * iTunes Search: one call, no key. Apple asks for roughly 20 calls a minute per device;
     * when it answers 403 / 429 it is skipped for a few minutes and Deezer carries on alone.
     */
    private suspend fun iTunes(title: String, artist: String, durationMs: Long): Gathered? = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() < iTunesBlockedUntil) return@withContext null
        val term = URLEncoder.encode(listOf(artist, title).filter(String::isNotBlank).joinToString(" "), "UTF-8")
        val country = Locale.getDefault().country.takeIf { it.length == 2 }?.let { "&country=$it" }.orEmpty()
        val (code, json) = getJsonWithCode("https://itunes.apple.com/search?media=music&entity=song&limit=8$country&term=$term")
        if (code == 403 || code == 429) {
            iTunesBlockedUntil = System.currentTimeMillis() + ITUNES_BACKOFF_MS
            return@withContext null
        }
        val results = json?.optJSONArray("results") ?: return@withContext null
        val best = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .filter { it.optString("wrapperType") == "track" }
            .map { hit ->
                // Reuse Deezer's scorer: same shape once the fields are renamed.
                val probe = JSONObject()
                    .put("title", hit.optString("trackName"))
                    .put("duration", (hit.optLong("trackTimeMillis") / 1000).toInt())
                    .put("artist", JSONObject().put("name", hit.optString("artistName")))
                hit to matchScore(probe, title, artist, durationMs)
            }
            .filter { it.second >= MIN_MATCH }
            .maxByOrNull { it.second }?.first ?: return@withContext null

        val releaseDate = best.optString("releaseDate").takeIf { it.length >= 4 }?.take(10)
        Gathered(
            genre = best.optString("primaryGenreName").takeIf { it.isNotBlank() && it != "Music" },
            album = best.optString("collectionName").takeIf(String::isNotBlank)
                ?.replace(Regex("""\s*-\s*(Single|EP)$"""), ""),
            artist = best.optString("artistName").takeIf(String::isNotBlank),
            durationMs = best.optLong("trackTimeMillis").takeIf { it > 0 },
            year = releaseDate?.take(4)?.toIntOrNull(),
            albumArtist = best.optString("collectionArtistName").takeIf(String::isNotBlank),
            trackNumber = best.optInt("trackNumber").takeIf { it > 0 },
            discNumber = best.optInt("discNumber").takeIf { it > 0 },
            releaseDate = releaseDate,
            coverUrl = ArtworkUrls.upgrade(best.optString("artworkUrl100").takeIf(String::isNotBlank)),
            explicit = best.optString("trackExplicitness").takeIf(String::isNotBlank)?.let { it == "explicit" },
            sources = listOf("iTunes"),
            found = true
        )
    }

    private fun matchScore(hit: JSONObject, title: String, artist: String, durationMs: Long): Double {
        val t = norm(hit.optString("title"))
        val a = norm(hit.optJSONObject("artist")?.optString("name").orEmpty())
        val wantT = norm(title)
        val wantA = norm(artist)
        var score = when {
            t == wantT -> 3.0
            t.startsWith(wantT) || wantT.startsWith(t) -> 2.0
            t.contains(wantT) || wantT.contains(t) -> 1.2
            else -> 0.0
        }
        if (wantA.isNotBlank()) {
            score += when {
                a == wantA -> 3.0
                a.contains(wantA) || wantA.contains(a) -> 2.0
                else -> -2.0
            }
        }
        val sec = hit.optInt("duration")
        if (durationMs > 0 && sec > 0) {
            val diff = abs(sec - durationMs / 1000.0)
            score += when {
                diff <= 3 -> 1.0
                diff <= 10 -> 0.4
                diff > 40 -> -1.5
                else -> 0.0
            }
        }
        return score
    }

    /**
     * Deezer allows about 50 requests per 5 s. A big playlist would blow through that, so
     * calls are spaced out (≈ 7 per second across the whole app).
     */
    private suspend fun deezerJson(url: String): JSONObject? {
        deezerPace.withLock {
            val wait = lastDeezerCallAt + DEEZER_SPACING_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastDeezerCallAt = System.currentTimeMillis()
        }
        return getJson(url)
    }

    private fun getJson(url: String): JSONObject? = getJsonWithCode(url).second

    /**
     * Shares the app's connection pool, so Deezer / iTunes lookups reuse warm HTTP/2
     * connections instead of a new TLS handshake per request, and honour offline mode.
     */
    private val http = okHttpClient.newBuilder()
        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun getJsonWithCode(url: String): Pair<Int, JSONObject?> {
        val request = okhttp3.Request.Builder().url(url).header("Accept", "application/json").build()
        return try {
            http.newCall(request).execute().use { response ->
                val code = response.code
                if (code !in 200..299) code to null
                else code to JSONObject(response.body.string()).takeIf { !it.has("error") }
            }
        } catch (e: Exception) {
            -1 to null
        }
    }

    // ── Merge / mood ───────────────────────────────────────────────────────────────────

    /** Copies what [filled] found into [song] (same title + artist), blanks only. */
    private fun merge(song: Song, filled: Song): Song = song.copy(
        genre = if (song.genre.isPlaceholderGenre() && !filled.genre.isPlaceholderGenre()) filled.genre else song.genre,
        album = if (song.album.isPlaceholderAlbum() && !filled.album.isPlaceholderAlbum()) filled.album else song.album,
        artist = if (song.artist.isBlankOrUnknown() && !filled.artist.isBlankOrUnknown()) filled.artist else song.artist,
        duration = if (song.duration <= 0 && filled.duration > 0) filled.duration else song.duration,
        year = if (song.year <= 0) filled.year else song.year,
        albumArtist = if (song.albumArtist.isBlankOrUnknown()) filled.albumArtist ?: song.albumArtist else song.albumArtist,
        trackNumber = if (song.trackNumber <= 0) filled.trackNumber else song.trackNumber,
        discNumber = song.discNumber?.takeIf { it > 0 } ?: filled.discNumber,
        albumArtUriString = if (!song.isLocal && !ArtworkUrls.isLowQuality(filled.albumArtUriString) &&
            ArtworkUrls.isLowQuality(song.albumArtUriString)) filled.albumArtUriString else song.albumArtUriString,
    )

    private fun merge(song: Song, g: Gathered): Song {
        if (!g.found) return song
        return song.copy(
            genre = if (song.genre.isPlaceholderGenre()) g.genre ?: song.genre else song.genre,
            album = if (song.album.isPlaceholderAlbum()) g.album ?: song.album else song.album,
            artist = if (song.artist.isBlankOrUnknown()) g.artist ?: song.artist else song.artist,
            duration = if (song.duration <= 0) g.durationMs ?: song.duration else song.duration,
            year = if (song.year <= 0) g.year ?: song.year else song.year,
            musicalFeatures = if (song.musicalFeatures.bpm == null && g.bpm != null) song.musicalFeatures.copy(bpm = g.bpm) else song.musicalFeatures,
            mixIntelligence = if (song.mixIntelligence.mood.isNullOrBlank() && g.mood != null) song.mixIntelligence.copy(mood = g.mood) else song.mixIntelligence,
            albumArtist = if (song.albumArtist.isBlankOrUnknown()) g.albumArtist ?: song.albumArtist else song.albumArtist,
            trackNumber = if (song.trackNumber <= 0) g.trackNumber ?: song.trackNumber else song.trackNumber,
            discNumber = song.discNumber?.takeIf { it > 0 } ?: g.discNumber ?: song.discNumber,
            // Only online songs get a catalogue cover, and only over a missing, video-frame or small one.
            albumArtUriString = if (!song.isLocal && g.coverUrl != null && ArtworkUrls.isLowQuality(song.albumArtUriString))
                g.coverUrl else song.albumArtUriString,
            songInformation = song.songInformation.let { info ->
                info.copy(
                    explicit = info.explicit || g.explicit == true,
                    releaseDate = info.releaseDate ?: g.releaseDate?.let(::parseDate)
                )
            },
            // ISRC is deliberately NOT copied onto the song: it would change mixIdentity mid-mix.
            // It stays in the cache (see cachedFor) for downloads and song info.
            creditsAndRelease = song.creditsAndRelease.let { c ->
                c.copy(
                    recordLabel = c.recordLabel ?: g.label,
                    upcEan = c.upcEan ?: g.upc
                )
            }
        )
    }

    private fun parseDate(s: String): Long? = runCatching {
        val parts = s.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(parts[0], (parts.getOrElse(1) { 1 } - 1).coerceIn(0, 11), parts.getOrElse(2) { 1 }.coerceIn(1, 31))
        }.timeInMillis
    }.getOrNull()

    private fun moodFromTags(tags: List<String>): String? {
        val lower = tags.map { it.lowercase() }
        return MOOD_WORDS.entries.firstOrNull { (_, words) -> lower.any { tag -> words.any { tag.contains(it) } } }?.key
    }

    /** Rough, clearly-estimated mood when no source has one. */
    private fun estimateMood(genre: String?, bpm: Float?): String? {
        val g = genre?.lowercase().orEmpty()
        return when {
            listOf("metal", "punk", "hardcore", "drill", "hard rock").any { g.contains(it) } -> "Intense"
            listOf("ambient", "lo-fi", "lofi", "chill", "classical", "acoustic", "sleep", "new age").any { g.contains(it) } -> "Calm"
            listOf("dance", "edm", "house", "techno", "electro", "disco", "latin", "reggaeton", "k-pop").any { g.contains(it) } ->
                if ((bpm ?: 120f) >= 110f) "Energetic" else "Upbeat"
            listOf("blues", "soul", "r&b", "rnb", "jazz").any { g.contains(it) } -> "Smooth"
            bpm != null && bpm >= 125f -> "Energetic"
            bpm != null && bpm < 85f -> "Calm"
            g.isNotBlank() || bpm != null -> "Upbeat"
            else -> null
        }
    }

    private suspend fun recordClaims(song: Song, g: Gathered) {
        val now = System.currentTimeMillis()
        val src = g.sources.distinct().filter { it != "Last.fm" }.joinToString(" / ").ifBlank { "Deezer" }
        val claims = buildMap {
            g.genre?.let { put("classification.genres", MetadataClaim(it, g.sources.distinct().joinToString(" / "), now)) }
            g.album?.let { put("release.album_title", MetadataClaim(it, src, now)) }
            g.albumArtist?.let { put("release.album_artist", MetadataClaim(it, src, now)) }
            g.durationMs?.let { put("asset.duration", MetadataClaim(it.toString(), src, now, unit = "ms")) }
            g.bpm?.let { put("performance.tempo", MetadataClaim(it.toString(), "Deezer", now, unit = "BPM")) }
            g.trackNumber?.let { put("release.track_number", MetadataClaim(it.toString(), src, now)) }
            g.discNumber?.let { put("release.disc_number", MetadataClaim(it.toString(), src, now)) }
            g.isrc?.let { put("identity.isrc", MetadataClaim(it, "Deezer", now)) }
            g.upc?.let { put("release.barcode", MetadataClaim(it, "Deezer", now)) }
            g.label?.let { put("release.label", MetadataClaim(it, "Deezer", now)) }

            g.mood?.let {
                put("classification.mood", MetadataClaim(it, if (g.moodEstimated) "Estimated from genre + BPM" else "Last.fm tags", now,
                    state = if (g.moodEstimated) MetadataState.ESTIMATED else MetadataState.KNOWN))
            }
        }
        if (claims.isNotEmpty()) runCatching { metadataStore.record(song, claims) }
    }

    // ── Cache on disk ──────────────────────────────────────────────────────────────────

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            root.keys().forEach { key ->
                val o = root.optJSONObject(key) ?: return@forEach
                cache[key] = Gathered(
                    genre = o.optString("genre").ifBlank { null },
                    album = o.optString("album").ifBlank { null },
                    artist = o.optString("artist").ifBlank { null },
                    durationMs = o.optLong("duration").takeIf { it > 0 },
                    year = o.optInt("year").takeIf { it > 0 },
                    bpm = o.optDouble("bpm").takeIf { !it.isNaN() && it > 0 }?.toFloat(),
                    mood = o.optString("mood").ifBlank { null },
                    moodEstimated = o.optBoolean("moodEstimated"),
                    albumArtist = o.optString("albumArtist").ifBlank { null },
                    trackNumber = o.optInt("track").takeIf { it > 0 },
                    discNumber = o.optInt("disc").takeIf { it > 0 },
                    releaseDate = o.optString("releaseDate").ifBlank { null },
                    coverUrl = o.optString("cover").ifBlank { null },
                    isrc = o.optString("isrc").ifBlank { null },
                    label = o.optString("label").ifBlank { null },
                    upc = o.optString("upc").ifBlank { null },
                    explicit = if (o.has("explicit")) o.optBoolean("explicit") else null,
                    gainDb = o.optDouble("gain").takeIf { !it.isNaN() }?.toFloat(),
                    sources = o.optString("sources").split(',').filter(String::isNotBlank),
                    at = o.optLong("at"),
                    found = o.optBoolean("found", true)
                )
            }
        }.onFailure { Timber.tag(TAG).w(it, "Could not read gathered metadata cache") }
    }

    @Synchronized
    private fun scheduleSave() {
        if (saveJob?.isActive == true) return
        saveJob = scope.launch {
            delay(2_000)
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                com.google.gson.stream.JsonWriter(tmp.bufferedWriter()).use { writer ->
                writer.beginObject()
                cache.snapshot().entries.sortedByDescending { it.value.at }.take(MAX_CACHE).forEach { (key, g) ->
                    val record = JSONObject().apply {
                        g.genre?.let { put("genre", it) }
                        g.album?.let { put("album", it) }
                        g.artist?.let { put("artist", it) }
                        g.durationMs?.let { put("duration", it) }
                        g.year?.let { put("year", it) }
                        g.bpm?.let { put("bpm", it.toDouble()) }
                        g.mood?.let { put("mood", it) }
                        put("moodEstimated", g.moodEstimated)
                        g.albumArtist?.let { put("albumArtist", it) }
                        g.trackNumber?.let { put("track", it) }
                        g.discNumber?.let { put("disc", it) }
                        g.releaseDate?.let { put("releaseDate", it) }
                        g.coverUrl?.let { put("cover", it) }
                        g.isrc?.let { put("isrc", it) }
                        g.label?.let { put("label", it) }
                        g.upc?.let { put("upc", it) }
                        g.explicit?.let { put("explicit", it) }
                        g.gainDb?.let { put("gain", it.toDouble()) }
                        if (g.sources.isNotEmpty()) put("sources", g.sources.distinct().joinToString(","))
                        put("at", g.at)
                        put("found", g.found)
                    }
                    writer.name(key).jsonValue(record.toString())
                }
                writer.endObject()
                }
                check(tmp.renameTo(file)) { "Could not replace gathered metadata cache" }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────

    private fun isOnline(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)

    private fun keyOf(song: Song) = norm(cleanTitle(song.title)) + "|" + norm(cleanArtist(song.artist))

    private fun cleanTitle(title: String): String = title
        .replace(Regex("""\s*[(\[][^)\]]*(official|video|audio|lyric|lyrics|visualizer|hd|4k|remaster(ed)?|explicit|clean)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(ft\.?|feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun cleanArtist(artist: String): String = artist
        .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*(,|&| x | feat\.? | ft\.? ).*$""", RegexOption.IGNORE_CASE), "")
        .trim()
        .takeUnless { it.equals("Unknown Artist", true) }
        .orEmpty()

    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    private fun String?.isBlankOrUnknown() = this.isNullOrBlank() || this.equals("unknown", true) ||
        this.equals("<unknown>", true) || this.equals("Unknown Artist", true) || this.equals("Unknown Genre", true)

    private fun String.isPlaceholderAlbum() = isBlank() || this in PLACEHOLDER_ALBUMS

    /** YouTube Music songs arrive with "YouTube Music" as their genre; that isn't a genre. */
    private fun String?.isPlaceholderGenre() = isBlankOrUnknown() || this in PLACEHOLDER_GENRES

    private companion object {
        const val TAG = "SongMetadataGatherer"
        const val MIN_MATCH = 3.0
        const val RETRY_MISS_MS = 24 * 60 * 60 * 1000L
        const val REFRESH_MS = 30L * 24 * 60 * 60 * 1000L
        const val MAX_CACHE = 5_000
        val PLACEHOLDER_ALBUMS = setOf("YouTube Music", "Unknown Album", "<unknown>", "Unknown", "Spotify")
        val PLACEHOLDER_GENRES = setOf("YouTube Music", "YouTube", "Spotify", "Other", "Unknown Genre")
        const val DEEZER_SPACING_MS = 110L // ≈ 45 calls / 5 s, under Deezer's 50
        const val ITUNES_BACKOFF_MS = 3 * 60 * 1000L
        val KNOWN_GENRE_WORDS = setOf(
            "pop", "rock", "hip hop", "hip-hop", "rap", "r&b", "rnb", "soul", "jazz", "blues", "country", "folk",
            "electronic", "dance", "edm", "house", "techno", "trance", "dubstep", "drum and bass", "metal", "punk",
            "indie", "alternative", "classical", "reggae", "reggaeton", "latin", "k-pop", "j-pop", "funk", "disco",
            "ambient", "lo-fi", "trap", "grime", "drill", "gospel", "afrobeats", "amapiano", "soundtrack"
        )
        val MOOD_WORDS = linkedMapOf(
            "Happy" to listOf("happy", "feel good", "feelgood", "cheerful", "fun"),
            "Sad" to listOf("sad", "heartbreak", "melanchol", "depress", "crying"),
            "Chill" to listOf("chill", "relax", "mellow", "calm", "laid back", "chillout"),
            "Energetic" to listOf("energetic", "party", "workout", "hype", "upbeat", "banger"),
            "Romantic" to listOf("romantic", "love", "sensual", "sexy"),
            "Dark" to listOf("dark", "moody", "brooding", "haunting"),
            "Angry" to listOf("angry", "aggressive", "rage"),
            "Dreamy" to listOf("dreamy", "ethereal", "atmospheric"),
            "Uplifting" to listOf("uplifting", "inspiring", "motivational", "euphoric")
        )
    }
}
