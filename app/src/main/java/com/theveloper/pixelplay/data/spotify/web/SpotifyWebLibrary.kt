package com.theveloper.pixelplay.data.spotify.web

import com.theveloper.pixelplay.data.spotify.SpotifyPlaylist
import com.theveloper.pixelplay.data.spotify.SpotifyTrack
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Your Spotify library through the web-player API: every playlist (folders flattened), tracks, likes. */
@Singleton
class SpotifyWebLibrary @Inject constructor(private val pathfinder: SpotifyPathfinder, private val session: SpotifyWebSession) {

    data class Profile(val username: String, val displayName: String, val avatarUrl: String?)

    suspend fun me(): Profile {
        val profile = pathfinder.query(SpotifyQueries.PROFILE, JSONObject())
            .optJSONObject("me")?.optJSONObject("profile") ?: throw SpotifyWebException("Spotify didn't return your profile.")
        val result = Profile(
            username = profile.optString("username").ifBlank { profile.optString("uri").substringAfterLast(':') },
            displayName = profile.optString("name"),
            avatarUrl = SpotifyWebParsers.bestImage(profile.optJSONObject("avatar")?.optJSONArray("sources"))
        )
        session.rememberProfile(result.username, result.displayName)
        return result
    }

    suspend fun myPlaylists(): List<SpotifyPlaylist> {
        val out = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        while (true) {
            val page = pathfinder.query(SpotifyQueries.LIBRARY, JSONObject()
                .put("filters", JSONArray().put("Playlists"))
                .put("order", JSONObject.NULL)
                .put("textFilter", "")
                .put("features", JSONArray())
                .put("limit", 50)
                .put("offset", offset)
                .put("flatten", true)
                .put("expandedFolders", JSONArray())
                .put("folderUri", JSONObject.NULL)
                .put("includeFoldersWhenFlattening", true))
                .optJSONObject("me")?.optJSONObject("libraryV3") ?: break
            val items = page.optJSONArray("items") ?: JSONArray()
            out += SpotifyWebParsers.libraryPlaylists(items)
            offset += 50
            if (items.length() == 0 || offset >= page.optInt("totalCount", 0)) break
        }
        return out.distinctBy { it.id }
    }

    suspend fun playlist(id: String): SpotifyPlaylist {
        val data = pathfinder.query(SpotifyQueries.PLAYLIST, playlistVars(id, 0, 1)).optJSONObject("playlistV2")
            ?: throw SpotifyWebException("Spotify couldn't find that playlist.", 404)
        if (data.optString("__typename") == "NotFound") throw SpotifyWebException("Spotify couldn't find that playlist. It may be private.", 404)
        return SpotifyWebParsers.playlist(data)
    }

    suspend fun playlistTracks(id: String): List<SpotifyTrack> {
        val out = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (true) {
            val data = pathfinder.query(SpotifyQueries.PLAYLIST, playlistVars(id, offset, 100)).optJSONObject("playlistV2")
                ?: throw SpotifyWebException("Spotify couldn't find that playlist.", 404)
            if (data.optString("__typename") == "NotFound") throw SpotifyWebException("Spotify couldn't find that playlist. It may be private.", 404)
            val content = data.optJSONObject("content") ?: break
            val items = content.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                out += SpotifyWebParsers.track(item.optJSONObject("itemV2")?.optJSONObject("data"), "$id:${offset + i}")
            }
            offset += 100
            if (items.length() == 0 || offset >= content.optInt("totalCount", 0)) break
        }
        return out
    }

    suspend fun likedSongs(): List<SpotifyTrack> {
        val out = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (true) {
            val tracks = pathfinder.query(SpotifyQueries.LIBRARY_TRACKS, JSONObject().put("offset", offset).put("limit", 50))
                .optJSONObject("me")?.optJSONObject("library")?.optJSONObject("tracks") ?: break
            val items = tracks.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val track = items.optJSONObject(i)?.optJSONObject("track")
                out += SpotifyWebParsers.track(track?.optJSONObject("data"), "liked:${offset + i}", track?.optString("_uri"))
            }
            offset += 50
            if (items.length() == 0 || offset >= tracks.optInt("totalCount", 0)) break
        }
        return out
    }

    private fun playlistVars(id: String, offset: Int, limit: Int) = JSONObject()
        .put("uri", "spotify:playlist:$id")
        .put("offset", offset)
        .put("limit", limit)
        .put("enableWatchFeedEntrypoint", false)
}

/** Pure JSON → model mapping, kept separate so it can be unit-tested with saved responses. */
internal object SpotifyWebParsers {
    fun bestImage(sources: JSONArray?): String? {
        if (sources == null) return null
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (i in 0 until sources.length()) {
            val s = sources.optJSONObject(i) ?: continue
            val url = s.optString("url").takeIf { it.startsWith("http") } ?: continue
            // Prefer ~300 px: sharp in lists without downloading full-size art.
            val width = s.optInt("width", 0)
            val score = if (width <= 0) 1000 else kotlin.math.abs(width - 300)
            if (score < bestScore) { best = url; bestScore = score }
        }
        return best
    }

    fun libraryPlaylists(items: JSONArray): List<SpotifyPlaylist> = (0 until items.length()).mapNotNull { i ->
        val wrapper = items.optJSONObject(i)?.optJSONObject("item") ?: return@mapNotNull null
        val data = wrapper.optJSONObject("data") ?: return@mapNotNull null
        if (data.optString("__typename") != "Playlist") return@mapNotNull null // skips Liked Songs, folders, episodes
        playlist(data)
    }

    fun playlist(data: JSONObject): SpotifyPlaylist {
        val uri = data.optString("uri")
        val owner = data.optJSONObject("ownerV2")?.optJSONObject("data")
        val images = data.optJSONObject("images")?.optJSONArray("items")?.optJSONObject(0)?.optJSONArray("sources")
        val canEdit = data.optJSONObject("currentUserCapabilities")?.optBoolean("canEditItems") == true
        return SpotifyPlaylist(
            id = uri.substringAfterLast(':'),
            title = data.optString("name").ifBlank { "Untitled playlist" },
            coverUrl = bestImage(images),
            totalTracks = data.optJSONObject("content")?.optInt("totalCount") ?: 0,
            ownerId = owner?.optString("username")?.ifBlank { null } ?: owner?.optString("uri")?.substringAfterLast(':').orEmpty(),
            ownerName = owner?.optString("name").orEmpty(),
            collaborative = canEdit
        )
    }

    fun track(data: JSONObject?, fallbackId: String, fallbackUri: String? = null): SpotifyTrack {
        val uri = data?.optString("uri")?.ifBlank { null } ?: fallbackUri
        val id = uri?.takeIf { it.startsWith("spotify:track:") }?.substringAfterLast(':')
        if (data == null || id == null || data.optString("__typename", "Track") != "Track") {
            return SpotifyTrack("unavailable:$fallbackId", data?.optString("name")?.ifBlank { null } ?: "Unavailable track",
                "Unavailable", "", 0, null, null)
        }
        val artists = artistNames(data.optJSONObject("artists")?.optJSONArray("items"))
            .ifEmpty { artistNames(data.optJSONObject("firstArtist")?.optJSONArray("items")) + artistNames(data.optJSONObject("otherArtists")?.optJSONArray("items")) }
        val album = data.optJSONObject("albumOfTrack")
        val duration = (data.optJSONObject("trackDuration") ?: data.optJSONObject("duration"))?.optLong("totalMilliseconds") ?: 0L
        return SpotifyTrack(
            id = id,
            title = data.optString("name").ifBlank { "Unknown title" },
            artistName = artists.joinToString(", ").ifBlank { "Unknown artist" },
            albumName = album?.optString("name").orEmpty(),
            durationMs = duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            isrc = null,
            coverUrl = bestImage(album?.optJSONObject("coverArt")?.optJSONArray("sources"))
        )
    }

    private fun artistNames(items: JSONArray?): List<String> =
        if (items == null) emptyList() else (0 until items.length()).mapNotNull { i ->
            items.optJSONObject(i)?.optJSONObject("profile")?.optString("name")?.takeIf { it.isNotBlank() }
        }
}
