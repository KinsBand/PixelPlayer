package com.theveloper.pixelplay.data.network.lyrics.wordsync

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.utils.TtmlLyricsParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Unison (https://unison.boidu.dev), Better Lyrics' community lyrics database. Entries are voted
 * on and keyed by the YouTube video they were timed against, so a lookup by the video id of the
 * recording being played gives lyrics timed to that exact audio. Songs without a video id, or
 * whose video has no entry, are looked up by song, artist and duration (the server matches
 * normalized names within ±2 s).
 *
 * The data is ODbL-1.0: wherever Unison lyrics are shown, [ATTRIBUTION] has to be shown too.
 */
class UnisonLyricsClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: HttpUrl = BASE_URL.toHttpUrl(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    data class Entry(
        val id: Long,
        val videoId: String?,
        val song: String,
        val artist: String,
        val album: String?,
        val isrc: String?,
        val lyrics: String,
        /** `ttml`, `lrc` or `plain`. */
        val format: String,
        /** `richsync` (word timed), `linesync` or `plain`. */
        val syncType: String,
        val language: String?,
        val effectiveScore: Double?,
        val voteCount: Int,
        /** Found by the video id that was asked for, not by song and artist. */
        val matchedByVideoId: Boolean
    ) {
        val isWordSynced: Boolean get() = syncType == SYNC_RICH
        val isSynced: Boolean get() = syncType == SYNC_RICH || syncType == SYNC_LINE
    }

    private class Cached(val entry: Entry?, val at: Long)

    private val cache = object : LinkedHashMap<String, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>) = size > CACHE_SIZE
    }
    /** Lookups in progress. The word and line pipelines ask about a song at nearly the same time. */
    private val inFlight = HashMap<String, CompletableDeferred<Result<Entry?>?>>()
    @Volatile private var blockedUntil = 0L

    /**
     * The best entry for the song, or null when Unison has none. Throws [IOException] when
     * Unison can't be reached or is rate limiting, so callers can tell that from "no lyrics".
     */
    suspend fun lookup(query: WordLyricsQuery): Entry? {
        val key = cacheKey(query) ?: return null
        while (true) {
            synchronized(cache) { cache[key] }?.takeIf { clock() - it.at < CACHE_TTL_MS }?.let { return it.entry }
            var owner = false
            val pending = synchronized(inFlight) {
                inFlight.getOrPut(key) { CompletableDeferred<Result<Entry?>?>().also { owner = true } }
            }
            if (!owner) {
                // null: whoever asked first was cancelled, so ask again.
                val result = pending.await() ?: continue
                return result.getOrThrow()
            }
            try {
                if (clock() < blockedUntil) throw IOException("Unison is rate limiting; paused")
                val entry = withContext(Dispatchers.IO) { request(query) }
                synchronized(cache) { cache[key] = Cached(entry, clock()) }
                pending.complete(Result.success(entry))
                return entry
            } catch (e: CancellationException) {
                pending.complete(null)
                throw e
            } catch (e: Exception) {
                pending.complete(Result.failure(e))
                throw e
            } finally {
                synchronized(inFlight) { if (inFlight[key] === pending) inFlight.remove(key) }
            }
        }
    }

    private fun request(query: WordLyricsQuery): Entry? {
        val videoId = query.videoId?.takeIf { VIDEO_ID.matches(it) }
        if (videoId != null) {
            get(baseUrl.newBuilder().addQueryParameter("v", videoId).build())
                ?.let { body -> parseEntry(body, matchedByVideoId = true)?.let { return it } }
        }
        val song = query.title.trim()
        val artist = query.artist.trim()
        if (song.isEmpty() || artist.isEmpty()) return null
        val url = baseUrl.newBuilder()
            .addQueryParameter("song", song)
            .addQueryParameter("artist", artist)
            .apply {
                val seconds = (query.durationMs + 500) / 1000
                if (seconds in 1..3600) addQueryParameter("duration", seconds.toString())
                query.album?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("album", it) }
            }
            .build()
        return get(url)?.let { parseEntry(it, matchedByVideoId = false) }
    }

    /** Body of a 200 answer, null for 404 / 400 ("no entry"); throws for 429, 5xx and I/O. */
    private fun get(url: HttpUrl): String? {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        okHttpClient.newCall(request).execute().use { response ->
            return when {
                response.isSuccessful -> response.body.string()
                response.code == 429 -> {
                    val retryAfterMs = response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)
                    blockedUntil = clock() + (retryAfterMs ?: RATE_LIMIT_PAUSE_MS).coerceIn(1_000L, 15 * 60_000L)
                    throw IOException("Unison HTTP 429")
                }
                response.code >= 500 -> throw IOException("Unison HTTP ${response.code}")
                else -> null
            }
        }
    }

    companion object {
        const val BASE_URL = "https://unison.betterlyrics.org/lyrics"

        /** Wording required by Unison's licence wherever its lyrics are shown. */
        const val ATTRIBUTION = "Lyrics from Unison (https://unison.boidu.dev)"
        const val ATTRIBUTION_URL = "https://unison.boidu.dev"

        const val SYNC_RICH = "richsync"
        const val SYNC_LINE = "linesync"

        private const val CACHE_SIZE = 32
        private const val CACHE_TTL_MS = 10L * 60 * 1000
        private const val RATE_LIMIT_PAUSE_MS = 60_000L
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

        private fun cacheKey(query: WordLyricsQuery): String? {
            val videoId = query.videoId?.takeIf { VIDEO_ID.matches(it) }.orEmpty()
            val song = query.title.trim().lowercase()
            val artist = query.artist.trim().lowercase()
            if (videoId.isEmpty() && (song.isEmpty() || artist.isEmpty())) return null
            return "$videoId|$song|$artist|${(query.durationMs + 500) / 1000}|${query.album?.trim()?.lowercase().orEmpty()}"
        }

        /** The `data` object of a lookup answer, or null when it has no usable lyrics. */
        internal fun parseEntry(body: String, matchedByVideoId: Boolean): Entry? {
            val data = try {
                JSONObject(body).optJSONObject("data")
            } catch (e: JSONException) {
                null
            } ?: return null
            val lyrics = data.optString("lyrics").takeIf { it.isNotBlank() } ?: return null
            val format = data.optString("format").lowercase().takeIf { it in FORMATS } ?: return null
            if (data.optBoolean("hidden", false)) return null
            return Entry(
                id = data.optLong("id", -1L),
                videoId = data.optString("videoId").takeIf { it.isNotBlank() },
                song = data.optString("song"),
                artist = data.optString("artist"),
                album = data.optString("album").takeIf { it.isNotBlank() },
                isrc = data.optString("isrc").takeIf { it.isNotBlank() },
                lyrics = lyrics,
                format = format,
                syncType = data.optString("syncType").lowercase(),
                language = data.optString("language").takeIf { it.isNotBlank() },
                effectiveScore = data.optDouble("effectiveScore").takeIf { !it.isNaN() },
                voteCount = data.optInt("voteCount", 0),
                matchedByVideoId = matchedByVideoId
            )
        }

        private val FORMATS = setOf("ttml", "lrc", "plain")

        /**
         * The entry's lyrics. TTML is read with the app's TTML parser; LRC (including word
         * timed "richsync" LRC) and plain text go through [parseLrc], the app's lyrics parser.
         */
        fun toLyrics(entry: Entry, parseLrc: (String) -> Lyrics): Lyrics? {
            val parsed = when (entry.format) {
                "ttml" -> runCatching { TtmlLyricsParser.parseDetailed(entry.lyrics) }.getOrNull()
                else -> runCatching { parseLrc(entry.lyrics) }.getOrNull()
            } ?: return null
            if (parsed.synced.isNullOrEmpty() && parsed.plain.isNullOrEmpty()) return null
            return parsed.copy(areFromRemote = true)
        }
    }
}

/** Unison's word-timed ("richsync") entries for the word-timed pipeline. */
class UnisonWordLyricsProvider(
    private val client: UnisonLyricsClient,
    private val parseLrc: (String) -> Lyrics
) : WordLyricsProvider {
    override val source = WordLyricsSource.UNISON

    // Lookups return the lyrics too; kept here until fetch() asks for them.
    private val found = object : LinkedHashMap<String, UnisonLyricsClient.Entry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, UnisonLyricsClient.Entry>) = size > 8
    }

    override suspend fun search(title: String, artist: String, durationMs: Long): List<WordLyricsCandidate> =
        search(WordLyricsQuery(title, artist, durationMs))

    override suspend fun search(query: WordLyricsQuery): List<WordLyricsCandidate> {
        val entry = client.lookup(query)?.takeIf { it.isWordSynced } ?: return emptyList()
        val id = entry.id.toString()
        synchronized(found) { found[id] = entry }
        return listOf(
            WordLyricsCandidate(
                source = source,
                id = id,
                name = entry.song,
                artistName = entry.artist,
                albumName = entry.album.orEmpty(),
                // A song match is within ±2 s of the duration asked for; answers carry no duration.
                durationSeconds = query.durationMs / 1000.0,
                exactMatch = entry.matchedByVideoId
            )
        )
    }

    override suspend fun fetch(candidate: WordLyricsCandidate): WordLyricsResult? {
        val entry = synchronized(found) { found[candidate.id] } ?: return null
        val lyrics = UnisonLyricsClient.toLyrics(entry, parseLrc) ?: return null
        val normalized = lyrics.copy(synced = lyrics.synced?.let(WordLyricsParsers::normalize))
        if (!WordLyricsParsers.isWordTimed(normalized)) return null
        // No raw TTML: stored as native timing JSON, which keeps the "online:unison" source the
        // attribution is shown for.
        return WordLyricsResult(normalized, source, candidate)
    }
}
