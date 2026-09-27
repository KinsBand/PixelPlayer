package com.theveloper.pixelplay.data.songsterr

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.theveloper.pixelplay.data.songsterr.model.RevisionTrack
import com.theveloper.pixelplay.data.songsterr.model.SongsterrSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Songsterr access.
 *
 * Flow: search → `GET /api/meta/{songId}` (revision, CDN image token, track list, popular
 * tracks) → `GET https://{cdn}/{songId}/{revisionId}/{image}/{trackIndex}.json`.
 * The page HTML is only scraped if the meta API fails. Parts are cached on disk, so a tab
 * that loaded once opens instantly and offline.
 */
object SongsterrClient {
    private const val TAG = "SongsterrClient"
    private const val BASE_URL = "https://www.songsterr.com/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // CDN host buckets used by the Songsterr player.
    private val NORMAL_HOSTS = listOf("dqsljvtekg760", "d34shlm8p2ums2", "d3cqchs6g3b5ew")
    private const val STAGE_HOST = "d3d3l6a6rcgkaf"
    private val LEGACY_HOSTS = listOf("d3rrfvx08uyjp1", "dodkcbujl0ebx", "dj1usja78sinh")

    private const val META_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private val VERSION_WORDS = Regex("\\b(solo|live|acoustic|riff|cover|fingerstyle|lesson|intro|ukulele|piano|remix|instrumental|standard tuning)\\b")

    data class ResolvedSongUrl(
        val songId: Long,
        val url: String,
    )

    /** A part that is ready to render, plus the song's track list for the track picker. */
    data class LoadedTab(
        val meta: SongsterrJson.SongMeta,
        val trackIndex: Int,
        val family: TabParser.InstrumentFamily,
        val track: RevisionTrack,
        val songUrl: String,
    ) {
        val metaTrack: SongsterrJson.MetaTrack? get() = meta.tracks.getOrNull(trackIndex)
    }

    /**
     * A tab the user picked by hand: another Songsterr listing of the song ([songId]) and/or an
     * older version of it ([revisionId]). Null fields fall back to the automatic choice.
     */
    data class TabSource(
        val songId: Long? = null,
        val revisionId: Long? = null,
        /** CDN image token of [revisionId], from the revisions list (used if its meta can't be read). */
        val revisionImage: String? = null,
    )

    /** Another Songsterr listing that matches the playing song. */
    data class Alternative(
        val songId: Long,
        val title: String,
        val artist: String,
        val parts: Int,
        val hasDrums: Boolean,
        val url: String,
    )

    class TabLoadException(message: String, val songUrl: String? = null) : Exception(message)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.songsterr.com/")
                .build()
            chain.proceed(request)
        }
        .build()

    /** Browser-like headers for the (fallback) HTML page. */
    private val htmlClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.songsterr.com/")
                .build()
            chain.proceed(request)
        }
        .build()

    private val cloudFrontClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, */*")
                .header("Origin", "https://www.songsterr.com")
                .header("Referer", "https://www.songsterr.com/")
                .build()
            chain.proceed(request)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: SongsterrApiService = retrofit.create(SongsterrApiService::class.java)

    /** "title|artist" → search result, so switching tracks doesn't search again. */
    private val resolvedCache = ConcurrentHashMap<String, ResolvedSongUrl>()

    // ─── Search ───────────────────────────────────────────────────────────────

    private fun cleanSearchQuery(query: String): String {
        return query.replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^]]*\\]"), "")
            .replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring)\\b.*$"), "")
            .replace(Regex("(?i)\\s-\\s.*(remaster|live|version|edit|mix).*$"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalize(s: String): String = cleanSearchQuery(s).lowercase()
        .replace("'", "")
        .removePrefix("the ")
        .replace("&", "and")
        .trim()

    private fun similarity(a: String, b: String): Int {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0
        if (x == y) return 3
        if (x.contains(y) || y.contains(x)) return 2
        val xs = x.split(' ').filter { it.length > 1 }.toSet()
        val ys = y.split(' ').filter { it.length > 1 }.toSet()
        return if (xs.intersect(ys).isNotEmpty()) 1 else 0
    }

    private fun sanitizeSlug(input: String): String {
        return input.trim().lowercase()
            .replace("&", "and")
            .replace("[^a-z0-9\\s-]".toRegex(), "")
            .trim()
            .replace("\\s+".toRegex(), "-")
            .replace("-+".toRegex(), "-")
    }

    fun songUrl(songId: Long, artist: String, title: String): String =
        "https://www.songsterr.com/a/wsa/${sanitizeSlug(artist)}-${sanitizeSlug(title)}-tab-s$songId"

    suspend fun getSong(songId: Long): SongsterrSong? {
        return try {
            apiService.getSong(songId)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching song by ID: $songId", e)
            null
        }
    }

    /**
     * Finds the Songsterr song that best matches [title] and [artist]. When [preferDrums] is set,
     * a match that has a drum part wins over one that doesn't.
     */
    suspend fun resolveSongUrl(title: String, artist: String, preferDrums: Boolean = false): ResolvedSongUrl? {
        val cacheKey = "${normalize(title)}|${normalize(artist)}|$preferDrums"
        resolvedCache[cacheKey]?.let { return it }
        try {
            val cleanedTitle = cleanSearchQuery(title)
            val cleanedArtist = cleanSearchQuery(artist)
            val queries = listOf("$cleanedArtist $cleanedTitle".trim(), cleanedTitle, title)
                .filter { it.isNotBlank() }
                .distinct()

            var best: SongsterrSong? = null
            var bestScore = Int.MIN_VALUE
            for (q in queries) {
                val results = runCatching { apiService.searchSongs(q) }.getOrElse {
                    Log.w(TAG, "Search failed for \"$q\": ${it.message}")
                    emptyList()
                }
                for ((rank, song) in results.withIndex()) {
                    val t = similarity(song.title, title)
                    val a = if (artist.isBlank()) 1 else similarity(song.artist, artist)
                    if (t == 0) continue
                    val hasDrums = song.tracks.any { it.instrumentId == 1024 }
                    var score = t * 10 + a * 8 - rank
                    if (preferDrums && hasDrums) score += 5
                    if (song.isJunk) score -= 15
                    // Prefer the full song over "(Solo)", "(Live)", "(Acoustic)", covers and lessons,
                    // unless the playing title asks for that version.
                    val extra = VERSION_WORDS.find(song.title.lowercase())?.value
                    if (extra != null && !title.lowercase().contains(extra)) score -= 6
                    if (song.title.trim().equals(title.trim(), ignoreCase = true)) score += 4
                    if (score > bestScore) {
                        best = song
                        bestScore = score
                    }
                }
                // A strong title + artist match needs no further queries.
                if (best != null && bestScore >= 3 * 10 + 2 * 8 - 3) break
            }
            val song = best ?: return null
            return ResolvedSongUrl(song.songId, songUrl(song.songId, song.artist, song.title))
                .also { resolvedCache[cacheKey] = it }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving song URL", e)
        }
        return null
    }

    suspend fun fetchDrumSheetForSong(title: String, artist: String): String? {
        return resolveSongUrl(title, artist, preferDrums = true)?.url
    }

    // ─── Main entry point ────────────────────────────────────────────────────

    /**
     * Loads a part for the current song.
     *
     * @param trackIndex a specific track from [LoadedTab.meta] (track picker); null picks the
     *   most popular part of the wanted instrument.
     * @param cacheDir usually `context.cacheDir`; enables the offline part cache.
     */
    suspend fun loadTab(
        title: String,
        artist: String,
        preferDrums: Boolean,
        trackIndex: Int? = null,
        cacheDir: File? = null,
        /** Preferred instrument when no [trackIndex] is given (overrides [preferDrums]). */
        family: TabParser.InstrumentFamily? = null,
        /** A listing / version the user picked; null = the best match, latest version. */
        source: TabSource? = null,
    ): Result<LoadedTab> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = resolveFor(title, artist, preferDrums, source)
            val meta = metaFor(resolved, source, cacheDir)

            val chosen = trackIndex?.let { meta.tracks.getOrNull(it) }
                ?: family?.let { pickTrackFor(meta, it) }
                ?: pickTrack(meta, preferDrums)
                ?: pickTrack(meta, !preferDrums)
                ?: throw TabLoadException(
                    if (preferDrums) "This Songsterr tab has no drum part." else "This Songsterr tab has no guitar or bass part.",
                    resolved.url,
                )

            val json = fetchPartJson(meta, chosen.index, cacheDir)
                ?: throw TabLoadException("Couldn't download the ${chosen.displayName} part.", resolved.url)
            val track = SongsterrJson.parseTrack(json)
                ?: throw TabLoadException("Songsterr sent a part this app couldn't read.", resolved.url)

            val family = when {
                chosen.family != TabParser.InstrumentFamily.OTHER -> chosen.family
                track.instrumentId == 1024 -> TabParser.InstrumentFamily.DRUMS
                else -> TabParser.familyOf(track.instrumentId).takeIf { it != TabParser.InstrumentFamily.OTHER }
                    ?: TabParser.InstrumentFamily.GUITAR
            }
            Log.d(TAG, "Loaded ${meta.artist} – ${meta.title}: track ${chosen.index} (${chosen.displayName}), ${track.measures.size} bars")
            LoadedTab(meta, chosen.index, family, track, resolved.url)
        }.onFailure { Log.w(TAG, "loadTab failed: ${it.message}") }
    }

    /** Just the song's track list (for the instrument picker), from cache when possible. */
    suspend fun loadMeta(
        title: String,
        artist: String,
        cacheDir: File? = null,
        source: TabSource? = null,
    ): Result<Pair<SongsterrJson.SongMeta, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = resolveFor(title, artist, false, source)
            val meta = metaFor(resolved, source, cacheDir)
            meta to resolved.url
        }
    }

    private suspend fun resolveFor(title: String, artist: String, preferDrums: Boolean, source: TabSource?): ResolvedSongUrl {
        source?.songId?.let { return ResolvedSongUrl(it, songUrl(it, artist, title)) }
        return resolveSongUrl(title, artist, preferDrums)
            ?: throw TabLoadException("Couldn't find this song on Songsterr.")
    }

    private suspend fun metaFor(resolved: ResolvedSongUrl, source: TabSource?, cacheDir: File?): SongsterrJson.SongMeta {
        val latest = fetchMeta(resolved.songId, resolved.url, cacheDir)
        val rev = source?.revisionId
        if (rev == null || rev == latest?.revisionId) {
            return latest ?: throw TabLoadException("Couldn't load the tab details from Songsterr.", resolved.url)
        }
        return fetchMetaAt(resolved.songId, rev, cacheDir)
            // The revision's own meta couldn't be read: reuse the latest track list with that
            // revision's id and CDN token (the part list rarely changes between versions).
            ?: latest?.takeIf { source?.revisionImage != null }?.copy(revisionId = rev, image = source?.revisionImage)
            ?: throw TabLoadException("Couldn't load that version of the tab.", resolved.url)
    }

    /** Meta of one specific (older) revision. Revisions never change, so the cache never expires. */
    private suspend fun fetchMetaAt(songId: Long, revisionId: Long, cacheDir: File?): SongsterrJson.SongMeta? =
        withContext(Dispatchers.IO) {
            val cacheFile = cacheDir?.let { File(File(it, "songsterr"), "meta_${songId}_$revisionId.json") }
            cacheFile?.takeIf { it.exists() }?.let { f -> runCatching { SongsterrJson.parseMeta(f.readText()) }.getOrNull() }?.let { return@withContext it }
            val body = httpGet(okHttpClient, "${BASE_URL}api/meta/$songId/$revisionId") ?: return@withContext null
            val meta = SongsterrJson.parseMeta(body)?.takeIf { it.revisionId == revisionId } ?: return@withContext null
            cacheFile?.let { f -> runCatching { f.parentFile?.mkdirs(); f.writeText(body) } }
            meta
        }

    /** Every saved version of a song's tab, newest first (cached for a day). */
    suspend fun fetchRevisions(songId: Long, cacheDir: File? = null): List<SongsterrJson.Revision> =
        withContext(Dispatchers.IO) {
            val cacheFile = cacheDir?.let { File(File(it, "songsterr"), "revisions_$songId.json") }
            cacheFile?.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < 24L * 60 * 60 * 1000 }
                ?.let { f -> SongsterrJson.parseRevisions(f.readText()).takeIf { it.isNotEmpty() }?.let { return@withContext it } }
            val body = httpGet(okHttpClient, "${BASE_URL}api/meta/$songId/revisions")
            val list = body?.let { SongsterrJson.parseRevisions(it) }.orEmpty()
            if (list.isNotEmpty() && body != null) {
                cacheFile?.let { f -> runCatching { f.parentFile?.mkdirs(); f.writeText(body) } }
                return@withContext list
            }
            // Offline: an old list is better than none.
            cacheFile?.takeIf { it.exists() }?.let { f -> runCatching { SongsterrJson.parseRevisions(f.readText()) }.getOrNull() }.orEmpty()
        }

    /**
     * Other Songsterr listings of the playing song (different uploads, live versions, covers…),
     * best match first. Used when the automatic match has the wrong tab.
     */
    suspend fun searchAlternatives(title: String, artist: String): List<Alternative> = withContext(Dispatchers.IO) {
        val cleanedTitle = cleanSearchQuery(title)
        val cleanedArtist = cleanSearchQuery(artist)
        val queries = listOf("$cleanedArtist $cleanedTitle".trim(), cleanedTitle).filter { it.isNotBlank() }.distinct()
        val seen = LinkedHashMap<Long, Pair<Int, SongsterrSong>>()
        for (q in queries) {
            val results = runCatching { apiService.searchSongs(q) }.getOrElse { emptyList() }
            for ((rank, song) in results.withIndex()) {
                val t = similarity(song.title, title)
                if (t == 0) continue
                val a = if (artist.isBlank()) 1 else similarity(song.artist, artist)
                var score = t * 10 + a * 8 - rank
                if (song.isJunk) score -= 15
                val prev = seen[song.songId]
                if (prev == null || prev.first < score) seen[song.songId] = score to song
            }
        }
        seen.values.sortedByDescending { it.first }.take(25).map { (_, song) ->
            Alternative(
                songId = song.songId,
                title = song.title,
                artist = song.artist,
                parts = song.tracks.size,
                hasDrums = song.tracks.any { it.instrumentId == 1024 },
                url = songUrl(song.songId, song.artist, song.title),
            )
        }
    }

    /** Most popular part of the wanted instrument, skipping empty and vocal tracks. */
    fun pickTrack(meta: SongsterrJson.SongMeta, preferDrums: Boolean): SongsterrJson.MetaTrack? {
        val usable = meta.tracks.filter { !it.isEmpty && !it.isVocal }
        fun popular(index: Int?, test: (SongsterrJson.MetaTrack) -> Boolean) =
            index?.let { i -> usable.firstOrNull { it.index == i && test(it) } }
        fun mostViewed(test: (SongsterrJson.MetaTrack) -> Boolean) =
            usable.filter(test).maxByOrNull { it.views }

        return if (preferDrums) {
            popular(meta.popularTrackDrum) { it.isDrums } ?: mostViewed { it.isDrums }
        } else {
            popular(meta.popularTrackGuitar) { it.isGuitar }
                ?: mostViewed { it.isGuitar }
                ?: popular(meta.popularTrackBass) { it.isBass }
                ?: mostViewed { it.isBass }
                ?: usable.firstOrNull { !it.isDrums && it.tuning.isNotEmpty() }
        }
    }

    /** Most popular part of [family] (bass falls back to guitar and the reverse). */
    fun pickTrackFor(meta: SongsterrJson.SongMeta, family: TabParser.InstrumentFamily): SongsterrJson.MetaTrack? {
        val usable = meta.tracks.filter { !it.isEmpty && !it.isVocal }
        fun popular(index: Int?, test: (SongsterrJson.MetaTrack) -> Boolean) =
            index?.let { i -> usable.firstOrNull { it.index == i && test(it) } }
        fun mostViewed(test: (SongsterrJson.MetaTrack) -> Boolean) = usable.filter(test).maxByOrNull { it.views }
        return when (family) {
            TabParser.InstrumentFamily.DRUMS -> popular(meta.popularTrackDrum) { it.isDrums } ?: mostViewed { it.isDrums }
            TabParser.InstrumentFamily.BASS -> popular(meta.popularTrackBass) { it.isBass } ?: mostViewed { it.isBass }
                ?: pickTrack(meta, false)
            else -> pickTrack(meta, false)
        }
    }

    /**
     * Older entry point, kept for existing callers.
     */
    suspend fun fetchRevisionForSong(
        title: String,
        artist: String,
        preferDrums: Boolean,
    ): Pair<RevisionTrack, TabParser.InstrumentFamily>? =
        loadTab(title, artist, preferDrums).getOrNull()?.let { it.track to it.family }

    // ─── Meta ────────────────────────────────────────────────────────────────

    suspend fun fetchMeta(songId: Long, pageUrl: String? = null, cacheDir: File? = null): SongsterrJson.SongMeta? =
        withContext(Dispatchers.IO) {
            val cacheFile = cacheDir?.let { File(File(it, "songsterr"), "meta_$songId.json") }
            val fresh = cacheFile?.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < META_CACHE_MAX_AGE_MS }
            fresh?.let { f -> runCatching { SongsterrJson.parseMeta(f.readText()) }.getOrNull() }?.let { return@withContext it }

            val body = httpGet(okHttpClient, "${BASE_URL}api/meta/$songId")
            val meta = body?.let { SongsterrJson.parseMeta(it) }
            if (meta != null) {
                cacheFile?.let { f -> runCatching { f.parentFile?.mkdirs(); f.writeText(body) } }
                return@withContext meta
            }

            // Fallback 1: the tab page's embedded state.
            val url = pageUrl ?: songUrl(songId, "", "")
            fetchPageStateMeta(url, songId)?.let { return@withContext it }

            // Fallback 2: a stale cache is better than nothing (offline).
            cacheFile?.takeIf { it.exists() }?.let { f -> runCatching { SongsterrJson.parseMeta(f.readText()) }.getOrNull() }
        }

    private fun fetchPageStateMeta(url: String, songId: Long): SongsterrJson.SongMeta? {
        val html = httpGet(htmlClient, url) ?: return null
        val json = extractStateJson(html) ?: return null
        return runCatching {
            val current = JsonParser.parseString(json).asJsonObject
                .getAsJsonObject("meta")?.getAsJsonObject("current") ?: return null
            if (!current.has("songId")) current.addProperty("songId", songId)
            SongsterrJson.metaFrom(current)
        }.getOrNull()
    }

    // ─── Video sync points ───────────────────────────────────────────────────

    /** Bar start times in YouTube recordings of this revision (empty if none). Cached on disk. */
    suspend fun fetchVideoPoints(songId: Long, revisionId: Long, cacheDir: File? = null): List<SongsterrJson.VideoPoints> =
        withContext(Dispatchers.IO) {
            val cacheFile = cacheDir?.let { File(File(it, "songsterr"), "points_${songId}_$revisionId.json") }
            cacheFile?.takeIf { it.exists() && it.length() > 0 }?.let { f ->
                runCatching { SongsterrJson.parseVideoPoints(f.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
            }
            val body = httpGet(okHttpClient, "${BASE_URL}api/video-points/$songId/$revisionId/list") ?: return@withContext emptyList()
            val list = SongsterrJson.parseVideoPoints(body)
            if (list.isNotEmpty()) cacheFile?.let { f -> runCatching { f.parentFile?.mkdirs(); f.writeText(body) } }
            list
        }

    /**
     * The sync points to use for the song that is playing: the same YouTube video if the song
     * came from YouTube, else Songsterr's main recording, else the first one.
     */
    fun pickVideoPoints(all: List<SongsterrJson.VideoPoints>, playingSongId: String?): SongsterrJson.VideoPoints? {
        if (all.isEmpty()) return null
        val id = playingSongId.orEmpty()
        all.firstOrNull { it.videoId.length >= 8 && id.contains(it.videoId) && (it.feature == null || it.feature == "alternative") }?.let { return it }
        return all.firstOrNull { it.feature == null } ?: all.firstOrNull { it.feature == "alternative" } ?: all.first()
    }

    // ─── Part ────────────────────────────────────────────────────────────────

    private fun partUrls(songId: Long, revisionId: Long, image: String?, index: Int): List<String> = when {
        image == null -> LEGACY_HOSTS.map { "https://$it.cloudfront.net/part/$revisionId/$index" }
        image.endsWith("-stage") -> listOf("https://$STAGE_HOST.cloudfront.net/$songId/$revisionId/$image/$index.json")
        else -> NORMAL_HOSTS.map { "https://$it.cloudfront.net/$songId/$revisionId/$image/$index.json" }
    }

    suspend fun fetchPartJson(meta: SongsterrJson.SongMeta, index: Int, cacheDir: File? = null): String? =
        withContext(Dispatchers.IO) {
            val cacheFile = cacheDir?.let {
                File(File(it, "songsterr"), "${meta.songId}_${meta.revisionId}_${meta.image ?: "legacy"}_$index.json")
            }
            cacheFile?.takeIf { it.exists() && it.length() > 0 }?.let { f ->
                runCatching { f.readText() }.getOrNull()?.let { return@withContext it }
            }
            for (url in partUrls(meta.songId, meta.revisionId, meta.image, index)) {
                val body = httpGet(cloudFrontClient, url) ?: continue
                if (!body.trimStart().startsWith("{")) continue
                cacheFile?.let { f ->
                    runCatching {
                        f.parentFile?.mkdirs()
                        f.writeText(body)
                        trimCache(f.parentFile)
                    }
                }
                return@withContext body
            }
            null
        }

    /** Older entry point: fetch by explicit ids. `partId` is the track's index in the meta list. */
    suspend fun fetchRevisionJson(songId: Long, revisionId: Long, image: String, partId: Int): String? =
        withContext(Dispatchers.IO) {
            partUrls(songId, revisionId, image.ifBlank { null }, partId).firstNotNullOfOrNull { url ->
                httpGet(cloudFrontClient, url)?.takeIf { it.trimStart().startsWith("{") }
            }
        }

    fun parseRevision(json: String): RevisionTrack? = SongsterrJson.parseTrack(json).also {
        if (it == null) Log.e(TAG, "Error parsing revision JSON")
    }

    private fun trimCache(dir: File?, maxFiles: Int = 120) {
        val files = dir?.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }.take(files.size - maxFiles).forEach { it.delete() }
    }

    // ─── Page state (fallback, and older callers) ────────────────────────────

    data class PageState(
        val revisionId: Long,
        val image: String,
        val tracks: List<PageStateTrack>,
    )

    data class PageStateTrack(
        val partId: Int,
        val instrumentId: Int,
        val hash: String,
    )

    fun parsePageState(jsonStr: String): PageState? {
        return try {
            val root = JsonParser.parseString(jsonStr).asJsonObject
            val current = root.getAsJsonObject("meta")?.getAsJsonObject("current") ?: return null
            val revisionId = current.get("revisionId")?.asLong ?: return null
            val image = current.get("image")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val tracks = current.getAsJsonArray("tracks")?.mapIndexedNotNull { i, elem ->
                val t = elem as? JsonObject ?: return@mapIndexedNotNull null
                PageStateTrack(
                    partId = t.get("partId")?.takeIf { !it.isJsonNull }?.asInt ?: i,
                    instrumentId = t.get("instrumentId")?.takeIf { !it.isJsonNull }?.asInt ?: -1,
                    hash = t.get("hash")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                )
            }.orEmpty()
            PageState(revisionId, image, tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing page state JSON", e)
            null
        }
    }

    private fun extractStateJson(html: String): String? {
        val pattern = Regex("""<script[^>]*id="state"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(html)?.groupValues?.get(1)
    }

    // ─── HTTP ────────────────────────────────────────────────────────────────

    private fun httpGet(client: OkHttpClient, url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "GET $url → ${response.code}")
                null
            } else {
                val bytes = response.body.bytes()
                if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                    GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
                } else {
                    String(bytes, Charsets.UTF_8)
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "GET $url failed: ${e.message}")
        null
    }
}
