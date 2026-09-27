package com.theveloper.pixelplay.data.ytmusic

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val YOUTUBE_MUSIC_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

data class YouTubeMusicPlaylist(val id: String, val title: String, val coverUrl: String?, val trackCount: Int)
data class YouTubeMusicTrack(val videoId: String, val title: String, val artistName: String, val albumName: String, val durationMs: Int, val coverUrl: String?,
    /** Per-row id YouTube Music needs to remove or move this entry inside a playlist. */
    val setVideoId: String? = null)

/**
 * Library shelves that YouTube Music exposes as playlists but which are not music playlists
 * (podcast "Episodes for Later", Shorts sounds, the Liked Music mirror).
 */
internal fun isHiddenYouTubeMusicPlaylist(id: String, title: String): Boolean {
    val bareId = id.removePrefix("VL")
    if (bareId == "LM" || bareId == "SE" || bareId == "LL" || bareId.startsWith("RDPN")) return true
    val normalized = title.lowercase(java.util.Locale.ROOT).trim()
    return normalized == "episodes for later" ||
        normalized.contains("episodes for later") ||
        (normalized.contains("short") && normalized.contains("sound"))
}

/** Parse renderer boundaries, never collect every video ID (menus include unrelated IDs). */
internal object MusicBrowseParser {
    fun objects(value: Any?, key: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> node.keys().forEach { name ->
                    val child = node.opt(name)
                    if (name == key && child is JSONObject) result.add(child) else visit(child)
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
            }
        }
        visit(value)
        return result
    }
    fun text(obj: JSONObject?): String {
        val runs = obj?.optJSONArray("runs") ?: return obj?.optString("simpleText").orEmpty()
        return (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") }
    }
    fun thumbnail(obj: JSONObject): String? = objects(obj, "musicThumbnailRenderer").firstOrNull()
        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let { it.optJSONObject(it.length() - 1)?.optString("url") }
        ?.let { com.theveloper.pixelplay.data.metadata.ArtworkUrls.upgrade(it) }
    private fun playlistScope(obj: JSONObject): JSONObject = objects(obj, "musicPlaylistShelfRenderer").firstOrNull()
        ?: objects(obj, "musicPlaylistShelfContinuation").firstOrNull()
        ?: objects(obj, "appendContinuationItemsAction").firstOrNull()
        ?: obj
    fun legacyContinuation(obj: JSONObject): Boolean = objects(playlistScope(obj), "continuationItemRenderer").isEmpty()
    fun continuation(obj: JSONObject): String? {
        val scope = playlistScope(obj)
        return objects(scope, "continuationItemRenderer").firstOrNull()?.let { objects(it, "continuationCommand").firstOrNull()?.optString("token") }
            ?: objects(scope, "nextContinuationData").firstOrNull()?.optString("continuation")
    }
    fun playlists(obj: JSONObject): List<YouTubeMusicPlaylist> = objects(obj, "musicTwoRowItemRenderer").mapNotNull {
        val browse = it.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
        val title = text(it.optJSONObject("title"))
        if (!browse.startsWith("VL") || isHiddenYouTubeMusicPlaylist(browse, title)) null
        else YouTubeMusicPlaylist(browse.removePrefix("VL"), title, thumbnail(it), 0)
    }.distinctBy { it.id }
    fun tracks(obj: JSONObject): List<YouTubeMusicTrack> = objects(playlistScope(obj), "musicResponsiveListItemRenderer").mapIndexed { index, row ->
        val flex = objects(row.optJSONArray("flexColumns"), "musicResponsiveListItemFlexColumnRenderer").map { it.optJSONObject("text") }
        val fixed = objects(row.optJSONArray("fixedColumns"), "musicResponsiveListItemFixedColumnRenderer").map { text(it.optJSONObject("text")) }
        val setVideoId = row.optJSONObject("playlistItemData")?.optString("playlistSetVideoId")?.takeIf { it.isNotBlank() }
        val id = row.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: objects(flex.firstOrNull(), "watchEndpoint").firstOrNull()?.optString("videoId")
        val artistRuns = flex.getOrNull(1)?.optJSONArray("runs")
        val artist = if (artistRuns == null) "" else (0 until artistRuns.length()).mapNotNull { i ->
            val run = artistRuns.getJSONObject(i)
            val browseId = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if (browseId.startsWith("UC")) run.optString("text") else null
        }.joinToString(", ").ifBlank { text(flex.getOrNull(1)).substringBefore(" • ") }
        val timePattern = Regex("\\d+(?::\\d{2}){1,2}")
        // Usually in a fixed column; some layouts (albums, "Liked") put it in the subtitle
        // ("Artist • Album • 3:45") instead, and without it the song showed no time.
        val duration = fixed.map { it.trim() }.firstOrNull { it.matches(timePattern) }
            ?: flex.drop(1).flatMap { text(it).split(" • ", "•") }.map { it.trim() }.lastOrNull { it.matches(timePattern) }
        val seconds = duration?.split(':')?.fold(0L) { acc, part -> acc * 60 + part.toInt() } ?: 0
        YouTubeMusicTrack(id ?: "unavailable:$index:${text(flex.firstOrNull()).hashCode()}",
            text(flex.firstOrNull()).ifBlank { "Unavailable track" }, artist, text(flex.getOrNull(2)),
            (seconds * 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), thumbnail(row), setVideoId)
    }
}

@Singleton
class YouTubeMusicRepository @Inject constructor(private val authManager: YouTubeMusicAuthManager, private val okHttpClient: OkHttpClient) {
    @Volatile private var clientVersion: String? = null
    private fun version(): String {
        clientVersion?.let { return it }
        val scrapedVersion = try {
            val request = Request.Builder()
                .url("https://music.youtube.com/")
                .header("User-Agent", YOUTUBE_MUSIC_USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body.string()
                    Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                } else null
            }
        } catch (_: Exception) {
            null
        }

        val version = scrapedVersion ?: run {
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            "1.$dateStr.01.00"
        }
        clientVersion = version
        return version
    }
    private fun currentSession() = YouTubeMusicSession(authManager.cookieHeader, authManager.accountIndex)
    private fun contextBody(): JSONObject = JSONObject().put("context", JSONObject().put("client", JSONObject()
        .put("clientName", "WEB_REMIX").put("clientVersion", version()).put("hl", "en")))
    private fun authorizedRequest(url: okhttp3.HttpUrl, body: JSONObject, session: YouTubeMusicSession): Request =
        Request.Builder().url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "SAPISIDHASH ${authManager.generateSapisidHash(session.sapisid)}")
            .header("Cookie", session.cookie).header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com").header("X-Goog-AuthUser", session.account)
            .header("Referer", "https://music.youtube.com/").header("User-Agent", YOUTUBE_MUSIC_USER_AGENT)
            .header("X-Youtube-Client-Name", "67").header("X-Youtube-Client-Version", version()).build()

    /** Authenticated write call (edit_playlist, like, ...). Returns the parsed response. */
    private fun action(path: String, body: JSONObject, session: YouTubeMusicSession = currentSession()): JSONObject {
        check(session.sapisid.isNotBlank()) { "Connect YouTube Music in Accounts first." }
        val url = "https://music.youtube.com/youtubei/v1/$path?prettyPrint=false".toHttpUrl()
        okHttpClient.newCall(authorizedRequest(url, body, session)).execute().use {
            check(it.isSuccessful) { "YouTube Music rejected the change (${it.code})." }
            val json = JSONObject(it.body.string().ifBlank { "{}" })
            check(!json.has("error")) { "YouTube Music rejected the change." }
            return json
        }
    }

    private fun browse(id: String, continuation: String? = null, legacy: Boolean = false,
        session: YouTubeMusicSession = currentSession()): JSONObject {
        check(session.sapisid.isNotBlank()) { "Connect YouTube Music in Accounts first." }
        val body = contextBody()
        if (continuation == null || legacy) body.put("browseId", id) else body.put("continuation", continuation)
        val url = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false".toHttpUrl().newBuilder()
        if (legacy && continuation != null) url.addQueryParameter("ctoken", continuation).addQueryParameter("continuation", continuation)
        val request = authorizedRequest(url.build(), body, session)
        okHttpClient.newCall(request).execute().use {
            check(it.isSuccessful) { "YouTube Music request failed (${it.code}). Refresh the browser session in Accounts." }
            val json = JSONObject(it.body.string())
            check(!json.has("error") && json.optJSONObject("responseContext")?.optBoolean("loggedOut") != true && MusicBrowseParser.objects(json, "mainAppWebResponseContext").none { context -> context.optBoolean("loggedOut") }) { "YouTube Music session expired. Reconnect in Accounts." }
            check(json.has("contents") || json.has("continuationContents") || json.has("onResponseReceivedActions") || json.has("onResponseReceivedEndpoints")) { "YouTube Music returned no library. Check the session and account index." }
            return json
        }
    }
    suspend fun validateSession(session: YouTubeMusicSession) = withContext(Dispatchers.IO) {
        browse("FEmusic_liked_playlists", session = session)
        Unit
    }
    private suspend fun <T> pages(id: String, parse: (JSONObject) -> List<T>): List<T> = withContext(Dispatchers.IO) {
        val result = mutableListOf<T>()
        val seen = hashSetOf<String>()
        var continuation: String? = null
        var legacy = false
        do {
            val page = browse(id, continuation, legacy)
            result.addAll(parse(page))
            continuation = MusicBrowseParser.continuation(page)?.takeIf { it.isNotBlank() }
            legacy = MusicBrowseParser.legacyContinuation(page)
            check(continuation == null || seen.add(continuation)) { "YouTube Music repeated a page. Previous library kept." }
        } while (continuation != null)
        result
    }
    suspend fun getUserPlaylists(): List<YouTubeMusicPlaylist> = pages("FEmusic_liked_playlists", MusicBrowseParser::playlists).distinctBy { it.id }
    suspend fun getLikedSongs(): List<YouTubeMusicTrack> = getPlaylistTracks("LM")
    suspend fun getPlaylistTracks(playlistId: String): List<YouTubeMusicTrack> {
        require(playlistId.matches(Regex("[a-zA-Z0-9_-]+")))
        return pages(if (playlistId.startsWith("VL")) playlistId else "VL$playlistId", MusicBrowseParser::tracks)
    }
    suspend fun getPlaylist(id: String): YouTubeMusicPlaylist = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[a-zA-Z0-9_-]+")))
        val page = browse("VL${id.removePrefix("VL")}")
        val header = MusicBrowseParser.objects(page, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: MusicBrowseParser.objects(page, "musicDetailHeaderRenderer").firstOrNull()
        YouTubeMusicPlaylist(id, MusicBrowseParser.text(header?.optJSONObject("title")).ifBlank { "YouTube Music playlist" }, header?.let { MusicBrowseParser.thumbnail(it) }, 0)
    }

    /** Applies raw edit_playlist actions (ACTION_ADD_VIDEO, ACTION_REMOVE_VIDEO, ACTION_MOVE_VIDEO_BEFORE, ACTION_SET_PLAYLIST_NAME). */
    suspend fun editPlaylist(playlistId: String, actions: JSONArray) = withContext(Dispatchers.IO) {
        val bareId = playlistId.removePrefix("VL")
        require(bareId.matches(Regex("[a-zA-Z0-9_-]+")))
        if (actions.length() == 0) return@withContext
        // YouTube Music accepts batches, but keep them modest so one bad row does not sink a big edit.
        for (start in 0 until actions.length() step 50) {
            val chunk = JSONArray()
            for (i in start until minOf(actions.length(), start + 50)) chunk.put(actions.get(i))
            val response = action("browse/edit_playlist", contextBody().put("playlistId", bareId).put("actions", chunk))
            val status = response.optString("status")
            check(status.isBlank() || status == "STATUS_SUCCEEDED") { "YouTube Music could not update the playlist." }
        }
    }

    /**
     * True when the signed-in account owns [playlistId] (YouTube Music shows an editable header
     * only to the owner). Saved playlists by other people return false.
     */
    suspend fun isOwnedPlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        val bareId = playlistId.removePrefix("VL")
        require(bareId.matches(Regex("[a-zA-Z0-9_-]+")))
        val page = browse("VL$bareId")
        MusicBrowseParser.objects(page, "musicEditablePlaylistDetailHeaderRenderer").isNotEmpty()
    }

    /**
     * Removes a playlist from the YouTube Music account: deletes it when the account owns it,
     * otherwise removes the saved copy from the library. Returns true when it was deleted.
     */
    suspend fun deleteOrRemovePlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        val bareId = playlistId.removePrefix("VL")
        require(bareId.matches(Regex("[a-zA-Z0-9_-]+")))
        val owned = isOwnedPlaylist(bareId)
        if (owned) action("playlist/delete", contextBody().put("playlistId", bareId))
        else action("like/removelike", contextBody().put("target", JSONObject().put("playlistId", bareId)))
        owned
    }

    /** Likes or un-likes a video on the signed-in YouTube Music account. */
    suspend fun setLiked(videoId: String, liked: Boolean) = withContext(Dispatchers.IO) {
        require(videoId.matches(Regex("[a-zA-Z0-9_-]+")))
        action(if (liked) "like/like" else "like/removelike", contextBody().put("target", JSONObject().put("videoId", videoId)))
        Unit
    }
}
fun YouTubeMusicTrack.toSong(): Song = Song(id = "yt_$videoId", title = title, artist = artistName,
    artistId = artistName.hashCode().toLong(), album = albumName, albumId = albumName.hashCode().toLong(),
    contentUriString = if (videoId.startsWith("unavailable:")) "" else "youtube://$videoId", albumArtUriString = coverUrl,
    duration = durationMs.toLong(), genre = "YouTube Music", path = "youtube://$videoId", isFavorite = false,
    trackNumber = 0, year = 0, dateAdded = System.currentTimeMillis() / 1000L,
    youtubeId = videoId.takeUnless { it.startsWith("unavailable:") })



