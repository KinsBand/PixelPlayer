package com.theveloper.pixelplay.data.spotify

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyPlaylist(val id: String, val title: String, val coverUrl: String?, val totalTracks: Int,
    val ownerId: String = "", val ownerName: String = "", val collaborative: Boolean = false)
data class SpotifyTrack(val id: String, val title: String, val artistName: String, val albumName: String,
    val durationMs: Int, val isrc: String?, val coverUrl: String?)
class SpotifyAccessException(val status: Int) : java.io.IOException(when (status) {
    401 -> "Spotify session expired. Reconnect in Accounts."
    403 -> "Spotify has restricted this playlist. Development apps can only read owned or collaborative playlist contents."
    429 -> "Spotify request limit reached. Try syncing later."
    else -> "Spotify request failed ($status). Try syncing again."
})

/**
 * Spotify library access. Uses the web-player session (sign-in with Spotify, no developer app)
 * when it's connected, and the official Web API with a developer app otherwise.
 */
@Singleton
class SpotifyRepository @Inject constructor(private val spotifyAuthManager: SpotifyAuthManager, private val okHttpClient: OkHttpClient,
    private val webSession: com.theveloper.pixelplay.data.spotify.web.SpotifyWebSession,
    private val webLibrary: com.theveloper.pixelplay.data.spotify.web.SpotifyWebLibrary) {
    private val useWeb: Boolean get() = webSession.hasSession

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrl()
        require(parsed.scheme == "https" && parsed.host == "api.spotify.com")
        val token = spotifyAuthManager.refreshAccessTokenIfNeeded() ?: throw SpotifyAccessException(401)
        okHttpClient.newCall(Request.Builder().url(parsed).header("Authorization", "Bearer $token").build()).execute().use {
            if (!it.isSuccessful) throw SpotifyAccessException(it.code)
            JSONObject(it.body.string())
        }
    }
    private suspend fun pages(path: String): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        var next: String? = "https://api.spotify.com/v1/$path"
        val visited = hashSetOf<String>()
        while (next != null) {
            check(visited.add(next)) { "Spotify returned a repeated page. Previous library kept." }
            val page = get(next)
            val items = page.getJSONArray("items")
            for (i in 0 until items.length()) results.add(items.optJSONObject(i) ?: JSONObject())
            next = page.optString("next").takeUnless { it.isBlank() || it == "null" }
        }
        return results
    }
    suspend fun getCurrentUserId(): String =
        if (useWeb) webLibrary.me().username else get("https://api.spotify.com/v1/me").getString("id")
    suspend fun getUserPlaylists(): List<SpotifyPlaylist> =
        if (useWeb) webLibrary.myPlaylists() else pages("me/playlists?limit=50").map { parsePlaylist(it) }
    suspend fun getPlaylist(id: String): SpotifyPlaylist {
        require(id.matches(Regex("[a-zA-Z0-9]+")))
        if (useWeb) return webLibrary.playlist(id)
        return parsePlaylist(get("https://api.spotify.com/v1/playlists/$id"))
    }
    suspend fun getPlaylistTracks(playlistId: String): List<SpotifyTrack> {
        require(playlistId.matches(Regex("[a-zA-Z0-9]+")))
        if (useWeb) return webLibrary.playlistTracks(playlistId)
        val items = try { pages("playlists/$playlistId/items?limit=50") }
            catch (e: SpotifyAccessException) { if (e.status != 404) throw e; pages("playlists/$playlistId/tracks?limit=50") }
        return items.mapIndexed { index, item -> parseTrack(item, "$playlistId:$index") }
    }
    suspend fun getLikedSongs(): List<SpotifyTrack> = if (useWeb) webLibrary.likedSongs() else pages("me/tracks?limit=50").mapIndexed { index, item -> parseTrack(item, "liked:$index") }
    internal fun parsePlaylist(obj: JSONObject): SpotifyPlaylist = SpotifyPlaylist(obj.getString("id"), obj.getString("name"),
        obj.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
        (obj.optJSONObject("items") ?: obj.optJSONObject("tracks"))?.optInt("total") ?: 0,
        obj.optJSONObject("owner")?.optString("id").orEmpty(), obj.optJSONObject("owner")?.optString("display_name").orEmpty(), obj.optBoolean("collaborative"))
    internal fun parseTrack(item: JSONObject, fallbackId: String): SpotifyTrack {
        val track = item.optJSONObject("item") ?: item.optJSONObject("track")
        val id = track?.optString("id")?.takeUnless { it.isBlank() || it == "null" }
        val album = track?.optJSONObject("album")
        val artists = track?.optJSONArray("artists")
        return SpotifyTrack(id ?: "unavailable:$fallbackId", track?.optString("name")?.takeIf { it.isNotBlank() } ?: "Unavailable track",
            if (artists == null) "Unavailable" else (0 until artists.length()).joinToString(", ") { artists.getJSONObject(it).optString("name") },
            album?.optString("name").orEmpty(), track?.optInt("duration_ms") ?: 0,
            track?.optJSONObject("external_ids")?.optString("isrc")?.takeIf { it.isNotBlank() },
            album?.optJSONArray("images")?.optJSONObject(0)?.optString("url"))
    }
}
fun SpotifyTrack.toSong(): Song = Song(id = "spotify_$id", title = title, artist = artistName,
    artistId = artistName.hashCode().toLong(), album = albumName, albumId = albumName.hashCode().toLong(),
    contentUriString = if (id.startsWith("unavailable:")) "" else "spotify://$id", albumArtUriString = coverUrl,
    duration = durationMs.toLong(), genre = "Spotify", path = "spotify://$id", isFavorite = false,
    trackNumber = 0, year = 0, dateAdded = System.currentTimeMillis() / 1000L,
    creditsAndRelease = com.theveloper.pixelplay.data.model.CreditsAndRelease(isrc = isrc))
