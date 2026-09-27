package com.theveloper.pixelplay.data.applemusic

import com.theveloper.pixelplay.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AppleMusicPlaylist(
    /** Library ids look like `p.xxxx`; catalog (public) ids like `pl.xxxx` / `pl.u-xxxx`. */
    val id: String,
    val title: String,
    val coverUrl: String?,
    val curatorName: String = "",
    val canEdit: Boolean = false,
    /** Catalog id when a library playlist is a copy of a public one. */
    val catalogId: String? = null
)

data class AppleMusicTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val albumName: String,
    val durationMs: Int,
    val isrc: String?,
    val coverUrl: String?
)

/** Apple Music library and public playlists via the web-player API (amp-api.music.apple.com). */
@Singleton
class AppleMusicRepository @Inject constructor(private val session: AppleMusicWebSession) {

    suspend fun getUserPlaylists(): List<AppleMusicPlaylist> =
        pages("/v1/me/library/playlists?limit=100").mapNotNull { AppleMusicParsers.playlist(it) }

    suspend fun getPlaylistTracks(id: String): List<AppleMusicTrack> {
        require(id.matches(Regex("[A-Za-z0-9._-]+")))
        val sf = session.storefront
        val items = if (id.startsWith("p.")) {
            // include=catalog brings the catalog song (with ISRC) alongside each library song.
            runCatching { pages("/v1/me/library/playlists/$id/tracks?limit=100&include=catalog") }
                .getOrElse { e -> if (e is AppleMusicException && e.status == 400) pages("/v1/me/library/playlists/$id/tracks?limit=100") else throw e }
        } else {
            pages("/v1/catalog/$sf/playlists/$id/tracks?limit=100")
        }
        return items.mapIndexed { index, item -> AppleMusicParsers.track(item, "$id:$index") }
    }

    /** Metadata for a public (catalog) playlist link. */
    suspend fun getCatalogPlaylist(id: String, storefront: String = session.storefront): AppleMusicPlaylist {
        require(id.matches(Regex("[A-Za-z0-9._-]+")))
        val data = session.get("/v1/catalog/$storefront/playlists/$id").optJSONArray("data")?.optJSONObject(0)
            ?: throw AppleMusicException("Apple Music couldn't find that playlist.", 404)
        return AppleMusicParsers.playlist(data) ?: throw AppleMusicException("Apple Music couldn't read that playlist.")
    }

    /**
     * Favourite songs. Apple keeps them in an automatic "Favourite Songs" playlist in the library;
     * there's no endpoint that lists every rating, so we read that playlist when it exists.
     */
    suspend fun getFavoriteSongs(playlists: List<AppleMusicPlaylist>? = null): List<AppleMusicTrack> {
        val all = playlists ?: getUserPlaylists()
        val favorites = all.firstOrNull { AppleMusicParsers.isFavoritesPlaylist(it.title) } ?: return emptyList()
        return getPlaylistTracks(favorites.id)
    }

    /** Follows Apple's `next` paths until the list ends (capped, to stay polite). */
    private suspend fun pages(first: String, maxPages: Int = 100): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var next: String? = first
        var count = 0
        val seen = hashSetOf<String>()
        while (next != null && count < maxPages) {
            if (!seen.add(next)) break
            val page = session.get(next)
            val data = page.optJSONArray("data") ?: JSONArray()
            for (i in 0 until data.length()) data.optJSONObject(i)?.let { out += it }
            next = page.optString("next").takeIf { it.isNotBlank() && it != "null" }?.let { path ->
                // Keep the include/extend options on later pages.
                val extra = first.substringAfter('?', "").split('&').filter { it.startsWith("include=") }
                if (extra.isEmpty() || extra.all { path.contains(it) }) path
                else path + (if (path.contains('?')) "&" else "?") + extra.joinToString("&")
            }
            count++
        }
        return out
    }
}

/** Pure JSON mapping for Apple Music responses; unit-tested with sample JSON. */
internal object AppleMusicParsers {
    private val FAVORITES_NAMES = setOf("favorite songs", "favourite songs", "loved songs", "favourites", "favorites")

    fun isFavoritesPlaylist(title: String) = title.trim().lowercase() in FAVORITES_NAMES

    fun artwork(attributes: JSONObject?, size: Int = 600): String? =
        attributes?.optJSONObject("artwork")?.optString("url")?.takeIf { it.startsWith("http") }
            ?.replace("{w}", size.toString())?.replace("{h}", size.toString())?.replace("{f}", "jpg")

    fun playlist(item: JSONObject): AppleMusicPlaylist? {
        val id = item.optString("id").ifBlank { return null }
        val a = item.optJSONObject("attributes") ?: JSONObject()
        return AppleMusicPlaylist(
            id = id,
            title = a.optString("name").ifBlank { "Untitled playlist" },
            coverUrl = artwork(a),
            curatorName = a.optString("curatorName"),
            canEdit = a.optBoolean("canEdit", false),
            catalogId = a.optJSONObject("playParams")?.optString("globalId")?.ifBlank { null }
        )
    }

    fun track(item: JSONObject, fallbackId: String): AppleMusicTrack {
        val a = item.optJSONObject("attributes")
        val catalog = item.optJSONObject("relationships")?.optJSONObject("catalog")?.optJSONArray("data")?.optJSONObject(0)
        val ca = catalog?.optJSONObject("attributes")
        // Catalog ids are stable across users and devices; prefer them over library ids.
        val id = catalog?.optString("id")?.ifBlank { null }
            ?: a?.optJSONObject("playParams")?.optString("catalogId")?.ifBlank { null }
            ?: item.optString("id").ifBlank { null }
        if (a == null || id == null) {
            return AppleMusicTrack("unavailable:$fallbackId", "Unavailable track", "Unavailable", "", 0, null, null)
        }
        return AppleMusicTrack(
            id = id,
            title = a.optString("name").ifBlank { ca?.optString("name").orEmpty() }.ifBlank { "Unknown title" },
            artistName = a.optString("artistName").ifBlank { ca?.optString("artistName").orEmpty() }.ifBlank { "Unknown artist" },
            albumName = a.optString("albumName").ifBlank { ca?.optString("albumName").orEmpty() },
            durationMs = a.optInt("durationInMillis", ca?.optInt("durationInMillis") ?: 0),
            isrc = (a.optString("isrc").ifBlank { null } ?: ca?.optString("isrc")?.ifBlank { null }),
            coverUrl = artwork(a) ?: artwork(ca)
        )
    }
}

fun AppleMusicTrack.toSong(): Song = Song(id = "applemusic_$id", title = title, artist = artistName,
    artistId = artistName.hashCode().toLong(), album = albumName, albumId = albumName.hashCode().toLong(),
    contentUriString = if (id.startsWith("unavailable:")) "" else "applemusic://$id", albumArtUriString = coverUrl,
    duration = durationMs.toLong(), genre = "Apple Music", path = "applemusic://$id", isFavorite = false,
    trackNumber = 0, year = 0, dateAdded = System.currentTimeMillis() / 1000L,
    creditsAndRelease = com.theveloper.pixelplay.data.model.CreditsAndRelease(isrc = isrc))
