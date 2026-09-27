package com.theveloper.pixelplay.data.network.lyrics.wordsync

import android.util.Log
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.network.lyrics.NetEaseLyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Where a word-timed result came from. Order = preference when several sources match. */
enum class WordLyricsSource(val key: String) {
    AMLL("amll"),
    MUSIXMATCH("musixmatch"),
    QQ("qq"),
    NETEASE("netease_yrc"),
    KUGOU("kugou")
}

/** A song found by a word-timed provider's metadata search (no lyrics downloaded yet). */
data class WordLyricsCandidate(
    val source: WordLyricsSource,
    val id: String,
    val name: String,
    val artistName: String,
    val albumName: String,
    val durationSeconds: Double,
    val accessKey: String? = null
)

/**
 * A downloaded word-timed result. [rawTtml] is set when the source was a TTML document (AMLL),
 * so it can be stored verbatim; otherwise the caller stores [lyrics] as native timing JSON.
 */
data class WordLyricsResult(
    val lyrics: Lyrics,
    val source: WordLyricsSource,
    val candidate: WordLyricsCandidate,
    val rawTtml: String? = null
)

/**
 * A free source of word-timed lyrics. Implementations throw [IOException] for network problems
 * (so callers can tell "offline" from "no match") and return empty / null for "not found".
 */
interface WordLyricsProvider {
    val source: WordLyricsSource
    suspend fun search(title: String, artist: String, durationMs: Long): List<WordLyricsCandidate>
    suspend fun fetch(candidate: WordLyricsCandidate): WordLyricsResult?
}

private const val TAG = "WordLyrics"

/** Runs [request] and returns the body, null for 4xx "not found" answers; throws for 429/5xx/IO. */
private fun OkHttpClient.fetchText(request: Request): String? {
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            if (response.code == 429 || response.code >= 500) throw IOException("HTTP ${response.code} ${request.url.host}")
            return null
        }
        return response.body?.string()
    }
}

// ── AMLL TTML DB (community word/syllable TTML, keyed by NetEase / QQ id) ────────────────────

/**
 * Reads the AMLL TTML database (github.com/Steve-xmh/amll-ttml-db). Community entries are CC0.
 * Not searchable on its own: it is looked up with ids found by the NetEase and QQ searches.
 */
class AmllTtmlClient(private val okHttpClient: OkHttpClient) {
    suspend fun fetch(folder: String, id: String): String? = withContext(Dispatchers.IO) {
        if (id.isBlank() || !id.all { it.isDigit() }) return@withContext null
        val url = "$BASE/$folder/$id.ttml"
        runCatching {
            okHttpClient.fetchText(Request.Builder().url(url).header("Accept", "application/xml,text/plain").build())
        }.onFailure { Log.d(TAG, "AMLL $folder/$id: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.contains("<tt", ignoreCase = true) }
    }

    companion object {
        const val BASE = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main"
        const val NETEASE_FOLDER = "ncm-lyrics"
        const val QQ_FOLDER = "qq-lyrics"
    }
}

/** Parses AMLL TTML with the app's own TTML parser; returns a result only if it is word timed. */
private fun amllResult(ttml: String?, candidate: WordLyricsCandidate): WordLyricsResult? {
    ttml ?: return null
    val parsed = runCatching { com.theveloper.pixelplay.utils.TtmlLyricsParser.parseDetailed(ttml) }.getOrNull() ?: return null
    val normalized = parsed.copy(synced = parsed.synced?.let(WordLyricsParsers::normalize), areFromRemote = true)
    if (!WordLyricsParsers.isWordTimed(normalized)) return null
    return WordLyricsResult(normalized, WordLyricsSource.AMLL, candidate, rawTtml = ttml)
}

// ── NetEase YRC ───────────────────────────────────────────────────────────────────────────────

class NetEaseYrcProvider(
    private val okHttpClient: OkHttpClient,
    private val amll: AmllTtmlClient,
    private val search: NetEaseLyricsProvider = NetEaseLyricsProvider(okHttpClient)
) : WordLyricsProvider {
    override val source = WordLyricsSource.NETEASE

    override suspend fun search(title: String, artist: String, durationMs: Long): List<WordLyricsCandidate> {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        return search.search(query, limit = 8).map {
            WordLyricsCandidate(source, it.id.toString(), it.name, it.artistName, it.albumName, it.durationSeconds)
        }
    }

    override suspend fun fetch(candidate: WordLyricsCandidate): WordLyricsResult? = withContext(Dispatchers.IO) {
        amllResult(amll.fetch(AmllTtmlClient.NETEASE_FOLDER, candidate.id), candidate)?.let { return@withContext it }
        val yrc = fetchYrcPlain(candidate.id) ?: runCatching { fetchYrcEapi(candidate.id) }
            .onFailure { Log.d(TAG, "NetEase eapi failed: ${it.message}") }
            .getOrNull()
        val lyrics = yrc?.let(WordLyricsParsers::parseYrc)?.takeIf(WordLyricsParsers::isWordTimed) ?: return@withContext null
        WordLyricsResult(lyrics, source, candidate)
    }

    private fun lyricParams(id: String): Map<String, String> = linkedMapOf(
        "id" to id, "cp" to "false", "lv" to "0", "kv" to "0", "tv" to "0",
        "rv" to "0", "yv" to "0", "ytv" to "0", "yrv" to "0"
    )

    private fun fetchYrcPlain(id: String): String? {
        val url = "https://music.163.com/api/song/lyric/v1".toHttpUrl().newBuilder().apply {
            lyricParams(id).forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val body = okHttpClient.fetchText(
            Request.Builder().url(url).header("Referer", "https://music.163.com/").header("Accept", "application/json").build()
        ) ?: return null
        return extractYrc(body)
    }

    private fun fetchYrcEapi(id: String): String? {
        val path = "/api/song/lyric/v1"
        val header = JSONObject()
            .put("os", "pc")
            .put("appver", "3.0.18")
            .put("requestId", "${System.currentTimeMillis()}_${(0..999).random().toString().padStart(4, '0')}")
            .toString()
        val data = JSONObject()
        lyricParams(id).forEach { (k, v) -> data.put(k, v) }
        data.put("header", header)
        val params = NetEaseEapi.params(path, data.toString())
        val request = Request.Builder()
            .url("https://interface3.music.163.com/eapi/song/lyric/v1")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=3.0.18")
            .post(FormBody.Builder().add("params", params).build())
            .build()
        return extractYrc(okHttpClient.fetchText(request) ?: return null)
    }

    private fun extractYrc(body: String): String? = runCatching {
        val json = JSONObject(body)
        if (json.optInt("code", 200) != 200) return null
        json.optJSONObject("yrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

// ── QQ Music QRC ──────────────────────────────────────────────────────────────────────────────

class QqMusicQrcProvider(
    private val okHttpClient: OkHttpClient,
    private val amll: AmllTtmlClient
) : WordLyricsProvider {
    override val source = WordLyricsSource.QQ

    override suspend fun search(title: String, artist: String, durationMs: Long): List<WordLyricsCandidate> =
        withContext(Dispatchers.IO) {
            val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
            if (query.isBlank()) return@withContext emptyList()
            val payload = JSONObject().put(
                "req_1", JSONObject()
                    .put("method", "DoSearchForQQMusicDesktop")
                    .put("module", "music.search.SearchCgiService")
                    .put("param", JSONObject().put("num_per_page", 10).put("page_num", 1).put("query", query).put("search_type", 0))
            )
            val request = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("Referer", "https://y.qq.com/")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val body = okHttpClient.fetchText(request) ?: return@withContext emptyList()
            val list = JSONObject(body).optJSONObject("req_1")?.optJSONObject("data")?.optJSONObject("body")
                ?.optJSONObject("song")?.optJSONArray("list") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until list.length()) {
                    val song = list.optJSONObject(i) ?: continue
                    val id = song.optLong("id", -1L).takeIf { it > 0 } ?: continue
                    val name = song.optString("name").ifBlank { song.optString("title") }.takeIf { it.isNotBlank() } ?: continue
                    val singers = song.optJSONArray("singer").names("name")
                    add(
                        WordLyricsCandidate(
                            source = source,
                            id = id.toString(),
                            name = name,
                            artistName = singers.joinToString(", "),
                            albumName = song.optJSONObject("album")?.optString("name").orEmpty(),
                            durationSeconds = song.optDouble("interval", 0.0)
                        )
                    )
                }
            }
        }

    override suspend fun fetch(candidate: WordLyricsCandidate): WordLyricsResult? = withContext(Dispatchers.IO) {
        amllResult(amll.fetch(AmllTtmlClient.QQ_FOLDER, candidate.id), candidate)?.let { return@withContext it }
        val request = Request.Builder()
            .url("https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg")
            .header("Referer", "https://c.y.qq.com/")
            .post(
                FormBody.Builder()
                    .add("version", "15")
                    .add("miniversion", "82")
                    .add("lrctype", "4")
                    .add("musicid", candidate.id)
                    .build()
            )
            .build()
        val body = okHttpClient.fetchText(request)?.replace("<!--", "")?.replace("-->", "") ?: return@withContext null
        val hex = CONTENT.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        val decrypted = QrcDecrypter.decrypt(hex) ?: return@withContext null
        val lyrics = WordLyricsParsers.parseQrc(decrypted)?.takeIf(WordLyricsParsers::isWordTimed) ?: return@withContext null
        WordLyricsResult(lyrics, source, candidate)
    }

    companion object {
        /** The original-language QRC block (not `contentts` translation / `contentroma`). */
        private val CONTENT = Regex("""<content\b[^>]*>\s*(?:<!\[CDATA\[)?\s*([0-9A-Fa-f]+)\s*(?:]]>)?\s*</content>""")
    }
}

// ── Kugou KRC ─────────────────────────────────────────────────────────────────────────────────

class KugouKrcProvider(private val okHttpClient: OkHttpClient) : WordLyricsProvider {
    override val source = WordLyricsSource.KUGOU

    override suspend fun search(title: String, artist: String, durationMs: Long): List<WordLyricsCandidate> =
        withContext(Dispatchers.IO) {
            val keyword = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" - ")
            if (keyword.isBlank()) return@withContext emptyList()
            val url = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("man", "yes")
                .addQueryParameter("client", "pc")
                .addQueryParameter("keyword", keyword)
                .apply { if (durationMs > 0) addQueryParameter("duration", durationMs.toString()) }
                .addQueryParameter("hash", "")
                .build()
            val body = okHttpClient.fetchText(Request.Builder().url(url).build()) ?: return@withContext emptyList()
            val candidates = JSONObject(body).optJSONArray("candidates") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until candidates.length()) {
                    val c = candidates.optJSONObject(i) ?: continue
                    val id = c.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val key = c.optString("accesskey").takeIf { it.isNotBlank() } ?: continue
                    add(
                        WordLyricsCandidate(
                            source = source,
                            id = id,
                            name = c.optString("song").ifBlank { title },
                            artistName = c.optString("singer").ifBlank { artist },
                            albumName = "",
                            durationSeconds = c.optLong("duration", 0L) / 1000.0,
                            accessKey = key
                        )
                    )
                }
            }
        }

    override suspend fun fetch(candidate: WordLyricsCandidate): WordLyricsResult? = withContext(Dispatchers.IO) {
        val key = candidate.accessKey ?: return@withContext null
        val url = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("client", "pc")
            .addQueryParameter("id", candidate.id)
            .addQueryParameter("accesskey", key)
            .addQueryParameter("fmt", "krc")
            .addQueryParameter("charset", "utf8")
            .build()
        val body = okHttpClient.fetchText(Request.Builder().url(url).build()) ?: return@withContext null
        val content = JSONObject(body).optString("content").takeIf { it.isNotBlank() } ?: return@withContext null
        val krc = KrcDecrypter.decrypt(content) ?: return@withContext null
        val lyrics = WordLyricsParsers.parseKrc(krc)?.takeIf(WordLyricsParsers::isWordTimed) ?: return@withContext null
        WordLyricsResult(lyrics, source, candidate)
    }
}

// ── Musixmatch RichSync (opt-in) ──────────────────────────────────────────────────────────────

/**
 * Uses the Musixmatch desktop app's anonymous token. This is against Musixmatch's terms and is
 * heavily rate limited, so it is off by default and only used when the user enables it.
 */
class MusixmatchRichSyncProvider(private val okHttpClient: OkHttpClient) : WordLyricsProvider {
    override val source = WordLyricsSource.MUSIXMATCH

    @Volatile private var token: String? = null
    @Volatile private var blockedUntil = 0L

    override suspend fun search(title: String, artist: String, durationMs: Long): List<WordLyricsCandidate> =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || System.currentTimeMillis() < blockedUntil) return@withContext emptyList()
            val body = call(
                "matcher.track.get",
                buildMap {
                    put("q_track", title)
                    if (artist.isNotBlank()) put("q_artist", artist)
                    if (durationMs > 0) put("q_duration", (durationMs / 1000).toString())
                }
            ) ?: return@withContext emptyList()
            val track = body.optJSONObject("track") ?: return@withContext emptyList()
            if (track.optInt("has_richsync", 0) != 1 || track.optInt("instrumental", 0) == 1) return@withContext emptyList()
            val trackId = track.optLong("track_id", -1L).takeIf { it > 0 } ?: return@withContext emptyList()
            listOf(
                WordLyricsCandidate(
                    source = source,
                    id = trackId.toString(),
                    name = track.optString("track_name").ifBlank { title },
                    artistName = track.optString("artist_name").ifBlank { artist },
                    albumName = track.optString("album_name"),
                    durationSeconds = track.optDouble("track_length", 0.0)
                )
            )
        }

    override suspend fun fetch(candidate: WordLyricsCandidate): WordLyricsResult? = withContext(Dispatchers.IO) {
        val body = call("track.richsync.get", mapOf("track_id" to candidate.id)) ?: return@withContext null
        val richsync = body.optJSONObject("richsync")?.optString("richsync_body")?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val lyrics = WordLyricsParsers.parseMusixmatchRichSync(richsync)?.takeIf(WordLyricsParsers::isWordTimed)
            ?: return@withContext null
        WordLyricsResult(lyrics, source, candidate)
    }

    /** Returns `message.body` for a 200 answer, null for "not found"; throws on network errors. */
    private fun call(method: String, params: Map<String, String>, retried: Boolean = false): JSONObject? {
        val userToken = token ?: fetchToken() ?: return null
        val url = "$ROOT$method".toHttpUrl().newBuilder().apply {
            addQueryParameter("format", "json")
            addQueryParameter("app_id", APP_ID)
            addQueryParameter("usertoken", userToken)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("t", System.currentTimeMillis().toString())
        }.build()
        val text = okHttpClient.fetchText(request(url.toString())) ?: return null
        val message = JSONObject(text).optJSONObject("message") ?: return null
        val status = message.optJSONObject("header")?.optInt("status_code", 0) ?: 0
        return when (status) {
            200 -> message.optJSONObject("body")
            401 -> {
                token = null
                if (!retried) call(method, params, retried = true) else { backOff(); null }
            }
            else -> null
        }
    }

    private fun fetchToken(): String? {
        if (System.currentTimeMillis() < blockedUntil) return null
        val url = "${ROOT}token.get?app_id=$APP_ID&user_language=en&t=${System.currentTimeMillis()}"
        val text = okHttpClient.fetchText(request(url)) ?: return null
        val message = JSONObject(text).optJSONObject("message") ?: return null
        if (message.optJSONObject("header")?.optInt("status_code", 0) != 200) {
            backOff()
            return null
        }
        return message.optJSONObject("body")?.optString("user_token")
            ?.takeIf { it.isNotBlank() && !it.contains("UpgradeOnlyUpgradeOnly") }
            ?.also { token = it }
    }

    private fun backOff() {
        blockedUntil = System.currentTimeMillis() + BLOCK_MS
        Log.d(TAG, "Musixmatch refused the anonymous token; pausing for ${BLOCK_MS / 60000} min")
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("Cookie", "AWSELBCORS=0; AWSELB=0; x-mxm-token-guid=")
        .header("Accept", "application/json")
        .build()

    companion object {
        private const val ROOT = "https://apic-desktop.musixmatch.com/ws/1.1/"
        private const val APP_ID = "web-desktop-app-v1.0"
        private const val BLOCK_MS = 30L * 60L * 1000L
    }
}

private fun JSONArray?.names(key: String): List<String> = buildList {
    val array = this@names ?: return@buildList
    for (i in 0 until array.length()) array.optJSONObject(i)?.optString(key)?.takeIf { it.isNotBlank() }?.let(::add)
}
