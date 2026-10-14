package com.theveloper.pixelplay.data.spotify.web

import com.theveloper.pixelplay.data.repository.ArtistCatalog
import com.theveloper.pixelplay.data.tour.ArtistShow
import org.json.JSONArray
import org.json.JSONObject

/** A playlist shown in the artist page's "On Spotify" row (the artist's own, "This Is", featuring…). */
data class SpotifyArtistPlaylist(val id: String, val title: String, val subtitle: String, val coverUrl: String?)

/** One merch item from the artist's Spotify page (opens in the browser). */
data class SpotifyMerchItem(
    val id: String,
    val name: String,
    val description: String?,
    val price: String?,
    val imageUrl: String?,
    val url: String,
)

/** A related artist from Spotify ("Similar" on the artist page). */
data class SpotifyRelatedArtist(val id: String, val name: String, val imageUrl: String?)

/** Everything the artist page takes from one Spotify artist-page call. Cached for 12 h. */
data class SpotifyArtistExtras(
    val artistId: String,
    val playlists: List<SpotifyArtistPlaylist> = emptyList(),
    val concerts: List<ArtistShow> = emptyList(),
    val merch: List<SpotifyMerchItem> = emptyList(),
    val relatedArtists: List<SpotifyRelatedArtist> = emptyList(),
    val fetchedAt: Long = 0L,
)

/**
 * Pure JSON → model mapping for the web player's artist search and artist page. The exact shape of
 * the artist page isn't documented and changes, so items are found by what they are (their
 * `spotify:playlist:` / `spotify:artist:` / `spotify:concert:` uri) inside the right block rather
 * than by one fixed path. A bad item is skipped and a missing block gives an empty list; nothing
 * here throws.
 */
internal object SpotifyArtistParsers {
    const val MAX_PLAYLISTS = 20
    const val MAX_MERCH = 30
    const val MAX_RELATED = 20
    private const val MAX_DEPTH = 10
    private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}""")

    private fun JSONObject.path(vararg keys: String): JSONObject? {
        var cur: JSONObject? = this
        for (k in keys) cur = cur?.optJSONObject(k) ?: return null
        return cur
    }

    private fun idOf(uri: String?, prefix: String): String? =
        uri?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { it.isNotBlank() }

    /** Visits every object under [node] (depth-capped). */
    private fun walk(node: Any?, depth: Int = 0, visit: (JSONObject) -> Unit) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JSONObject -> {
                visit(node)
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), depth + 1, visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), depth + 1, visit)
        }
    }

    /** First value of [key] found under [obj] (depth-capped), as given. */
    private fun find(obj: JSONObject, key: String, depth: Int = 0): Any? {
        if (depth > 4) return null
        obj.opt(key)?.takeIf { it != JSONObject.NULL }?.let { return it }
        val keys = obj.keys()
        while (keys.hasNext()) {
            when (val child = obj.opt(keys.next())) {
                is JSONObject -> find(child, key, depth + 1)?.let { return it }
                is JSONArray -> for (i in 0 until minOf(child.length(), 3)) {
                    (child.opt(i) as? JSONObject)?.let { o -> find(o, key, depth + 1)?.let { return it } }
                }
            }
        }
        return null
    }

    private fun findString(obj: JSONObject, key: String): String? =
        (find(obj, key) as? String)?.takeIf { it.isNotBlank() }

    private fun imageOf(obj: JSONObject): String? = SpotifyWebParsers.bestImage(find(obj, "sources") as? JSONArray)

    /** The search hit whose name is exactly [artistName] (accents / case ignored), as a Spotify id. */
    fun artistIdFromSearch(data: JSONObject?, artistName: String): String? {
        val block = data?.path("searchV2") ?: return null
        val wanted = ArtistCatalog.fold(artistName)
        var found: String? = null
        walk(block) { obj ->
            if (found != null) return@walk
            val id = idOf(obj.optString("uri"), "spotify:artist:") ?: return@walk
            val name = obj.path("profile")?.optString("name").orEmpty().ifBlank { obj.optString("name") }
            if (ArtistCatalog.fold(name) == wanted) found = id
        }
        return found
    }

    fun extras(data: JSONObject?, artistId: String, now: Long): SpotifyArtistExtras {
        val artist = data?.optJSONObject("artistUnion") ?: return SpotifyArtistExtras(artistId, fetchedAt = now)
        return SpotifyArtistExtras(
            artistId = artistId,
            playlists = playlists(artist),
            concerts = concerts(artist),
            merch = merch(artist),
            relatedArtists = related(artist, artistId),
            fetchedAt = now,
        )
    }

    /** Top-level keys of the artist object (for a debug log when a part comes back empty). */
    fun describe(data: JSONObject?): String {
        val artist = data?.optJSONObject("artistUnion") ?: return "no artistUnion"
        val keys = artist.keys().asSequence().toList()
        val related = artist.optJSONObject("relatedContent")?.keys()?.asSequence()?.toList().orEmpty()
        val goods = artist.optJSONObject("goods")?.keys()?.asSequence()?.toList().orEmpty()
        return "artistUnion=$keys relatedContent=$related goods=$goods"
    }

    fun playlists(artist: JSONObject): List<SpotifyArtistPlaylist> {
        val out = LinkedHashMap<String, SpotifyArtistPlaylist>()
        val visit: (JSONObject) -> Unit = { obj ->
            val id = idOf(obj.optString("uri"), "spotify:playlist:")
            val name = obj.optString("name").takeIf { it.isNotBlank() }
            if (id != null && name != null && id !in out) {
                val owner = obj.path("ownerV2", "data")?.optString("name")?.takeIf { it.isNotBlank() }
                out[id] = SpotifyArtistPlaylist(id, name, owner.orEmpty(), imageOf(obj))
            }
        }
        // The artist's own playlists first, then "featuring" / "discovered on".
        walk(artist.optJSONObject("profile"), visit = visit)
        walk(artist.optJSONObject("relatedContent"), visit = visit)
        return out.values.take(MAX_PLAYLISTS)
    }

    fun related(artist: JSONObject, selfId: String = ""): List<SpotifyRelatedArtist> {
        val block = artist.path("relatedContent", "relatedArtists") ?: return emptyList()
        val out = LinkedHashMap<String, SpotifyRelatedArtist>()
        walk(block) { obj ->
            val id = obj.optString("id").takeIf { it.isNotBlank() && obj.optString("uri").isBlank() }
                ?: idOf(obj.optString("uri"), "spotify:artist:") ?: return@walk
            if (id == selfId || id in out) return@walk
            val name = obj.path("profile")?.optString("name")?.takeIf { it.isNotBlank() } ?: return@walk
            out[id] = SpotifyRelatedArtist(id, name, SpotifyWebParsers.bestImage(obj.path("visuals", "avatarImage")?.optJSONArray("sources")) ?: imageOf(obj))
        }
        return out.values.take(MAX_RELATED)
    }

    fun concerts(artist: JSONObject): List<ArtistShow> {
        val goods = artist.optJSONObject("goods") ?: return emptyList()
        val out = LinkedHashMap<String, ArtistShow>()
        walk(goods) { c ->
            val id = idOf(c.optString("uri"), "spotify:concert:") ?: return@walk
            if (id in out) return@walk
            val iso = findString(c, "isoString") ?: findString(c, "startDateIsoString") ?: findString(c, "date")
                ?: return@walk
            val date = ISO_DATE.find(iso)?.value ?: return@walk
            val time = iso.substringAfter('T', "").take(5).takeIf { it.length == 5 && it[2] == ':' }
            val location = find(c, "location") as? JSONObject
            val city = location?.let { findString(it, "city") }
                ?: location?.optString("name")?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() }
                ?: findString(c, "city")
                ?: return@walk
            val venue = when (val v = c.opt("venue")) {
                is String -> v.takeIf { it.isNotBlank() }
                is JSONObject -> v.optString("name").takeIf { it.isNotBlank() }
                else -> null
            } ?: location?.optString("name")?.takeIf { it.isNotBlank() && !it.startsWith(city) }
                ?: c.optString("title")
            val lat = (find(c, "latitude") as? Number)?.toDouble()
            val lon = (find(c, "longitude") as? Number)?.toDouble()
            out[id] = ArtistShow(
                id = "sp_$id",
                date = date,
                time = time,
                venue = venue,
                city = city,
                region = null,
                country = location?.let { findString(it, "country") }.orEmpty(),
                countryCode = "",
                latitude = lat,
                longitude = lon,
                ticketUrl = "https://open.spotify.com/concert/$id",
            )
        }
        return out.values.toList()
    }

    fun merch(artist: JSONObject): List<SpotifyMerchItem> {
        val goods = artist.optJSONObject("goods") ?: return emptyList()
        // Only the merch block: concerts carry their own named links (ticket sellers).
        val block = goods.optJSONObject("merch") ?: return emptyList()
        val out = LinkedHashMap<String, SpotifyMerchItem>()
        walk(block) { m ->
            val uri = m.optString("uri")
            if (uri.startsWith("spotify:concert:") || uri.startsWith("spotify:artist:")) return@walk
            val name = m.optString("name").takeIf { it.isNotBlank() } ?: return@walk
            val url = listOf("url", "uri", "link", "shopUrl").map { m.optString(it) }.firstOrNull { it.startsWith("https://") }
                ?: return@walk
            val id = uri.ifBlank { url }
            if (id in out) return@walk
            out[id] = SpotifyMerchItem(
                id = id,
                name = name,
                description = m.optString("description").takeIf { it.isNotBlank() },
                price = when (val p = m.opt("price")) {
                    is String -> p.takeIf { it.isNotBlank() }
                    is JSONObject -> p.optString("formatted").ifBlank { p.optString("amount") }.takeIf { it.isNotBlank() }
                    else -> null
                },
                imageUrl = imageOf(m),
                url = url,
            )
        }
        return out.values.take(MAX_MERCH)
    }
}
