package com.theveloper.pixelplay.data.network.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Secondary synced-lyrics source used when LRCLIB has no time-synced match.
 *
 * NetEase Cloud Music exposes public (unauthenticated) search and lyric endpoints with a very
 * large LRC catalogue, including many Western releases. Results are mapped onto
 * [LrcLibResponse] so they flow through the same matching/ranking code as LRCLIB results.
 *
 * Requests go through the app's shared OkHttpClient, so offline mode and the lyrics
 * integration switch (NetworkAccessPolicy) apply exactly as they do to LRCLIB.
 */
class NetEaseLyricsProvider(
    private val okHttpClient: OkHttpClient
) {
    data class Candidate(
        val id: Long,
        val name: String,
        val artistName: String,
        val albumName: String,
        val durationSeconds: Double
    )

    /** Metadata-only search. Throws on network failure so callers can tell "no match" from "offline". */
    suspend fun search(query: String, limit: Int = 10): List<Candidate> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "1")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", limit.toString())
            .build()
        val body = execute(Request.Builder().url(url).applyHeaders().build()) ?: return@withContext emptyList()
        val root = JSONObject(body)
        val code = root.optInt("code", 200)
        if (code != 200) throw java.io.IOException("NetEase search refused (code $code)")
        val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: return@withContext emptyList()

        buildList {
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val id = song.optLong("id", -1L).takeIf { it > 0 } ?: continue
                val name = song.optString("name").takeIf { it.isNotBlank() } ?: continue
                val artists = song.optJSONArray("artists")
                val artistName = buildList {
                    if (artists != null) {
                        for (a in 0 until artists.length()) {
                            artists.optJSONObject(a)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.joinToString(", ")
                val albumName = song.optJSONObject("album")?.optString("name").orEmpty()
                val durationMs = song.optLong("duration", song.optLong("dt", 0L))
                add(Candidate(id, name, artistName, albumName, durationMs / 1000.0))
            }
        }
    }

    /**
     * Returns cleaned LRC text for a NetEase song id, or null when the song has no time-synced
     * lyrics (instrumental, uncollected, or credits only).
     */
    suspend fun fetchSyncedLrc(id: Long): String? = withContext(Dispatchers.IO) {
        val url = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
            .addQueryParameter("id", id.toString())
            .addQueryParameter("lv", "-1")
            .addQueryParameter("kv", "-1")
            .addQueryParameter("tv", "-1")
            .build()
        val body = execute(Request.Builder().url(url).applyHeaders().build()) ?: return@withContext null
        val json = JSONObject(body)
        if (json.optBoolean("nolyric") || json.optBoolean("uncollected")) return@withContext null
        val raw = json.optJSONObject("lrc")?.optString("lyric").orEmpty()
        cleanNetEaseLrc(raw)
    }

    fun toResponse(candidate: Candidate, syncedLrc: String?): LrcLibResponse =
        LrcLibResponse(
            // Negative ids keep NetEase rows distinct from LRCLIB ids in distinctBy() calls.
            id = -((candidate.id % Int.MAX_VALUE).toInt().coerceAtLeast(1)),
            name = candidate.name,
            artistName = candidate.artistName,
            albumName = candidate.albumName,
            duration = candidate.durationSeconds,
            plainLyrics = null,
            syncedLyrics = syncedLrc
        )

    private fun Request.Builder.applyHeaders(): Request.Builder =
        header("Referer", "https://music.163.com/")
            .header("Accept", "application/json")

    private fun execute(request: Request): String? {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 429 || response.code >= 500) {
                    throw java.io.IOException("NetEase HTTP ${response.code}")
                }
                Log.d(TAG, "NetEase HTTP ${response.code} for ${request.url.encodedPath}")
                return null
            }
            return response.body?.string()
        }
    }

    companion object {
        private const val TAG = "NetEaseLyrics"
        private val TIMESTAMP_LINE = Regex("""^\s*\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]\s*(.*)$""")
        private val CREDIT_LINE = Regex(
            """^\s*(作词|作曲|编曲|制作人|制作|监制|混音|母带|和声|和音|吉他|贝斯|鼓|键盘|弦乐|录音|混音师|出品|发行|企划|统筹|OP|SP|词|曲|""" +
                """lyrics?\s*by|written\s*by|composed?\s*by|composer|lyricist|producers?|produced\s*by|arranged\s*by|arranger|""" +
                """mix(?:ed|ing)?\s*(?:by|engineer)?|master(?:ed|ing)?\s*(?:by|engineer)?|recording|vocals?\s*by)\s*[:：]""",
            RegexOption.IGNORE_CASE
        )
        private val PURE_MUSIC = Regex("""纯音乐|此歌曲为没有填词的纯音乐""")

        /** Minimum real timed lines for a NetEase result to count as synced lyrics. */
        private const val MIN_TIMED_LINES = 4

        internal fun cleanNetEaseLrc(raw: String): String? {
            if (raw.isBlank() || PURE_MUSIC.containsMatchIn(raw)) return null
            val kept = raw.lineSequence()
                .map { it.trimEnd() }
                // Newer NetEase payloads prepend JSON credit rows: {"t":0,"c":[...]}
                .filterNot { it.trimStart().startsWith("{") }
                .filter { line ->
                    val match = TIMESTAMP_LINE.matchEntire(line) ?: return@filter false
                    val text = match.groupValues[4]
                    !CREDIT_LINE.containsMatchIn(text)
                }
                .toList()
            val timedTextTimes = kept.mapNotNull { line ->
                val match = TIMESTAMP_LINE.matchEntire(line) ?: return@mapNotNull null
                if (match.groupValues[4].isBlank()) return@mapNotNull null
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                minutes * 60_000L + seconds * 1_000L
            }
            if (timedTextTimes.size < MIN_TIMED_LINES) return null
            // "Fake synced" uploads stamp every line with the same time; they would render as a
            // single frozen line, so treat them as having no synced lyrics.
            if (timedTextTimes.distinct().size < MIN_TIMED_LINES) return null
            if ((timedTextTimes.maxOrNull() ?: 0L) < 10_000L) return null
            return kept.joinToString("\n")
        }
    }
}
