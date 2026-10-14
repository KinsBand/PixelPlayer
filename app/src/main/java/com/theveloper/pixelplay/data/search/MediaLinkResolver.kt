package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.NetworkAccessPolicy
import com.theveloper.pixelplay.data.network.NetworkDecision
import com.theveloper.pixelplay.data.network.NetworkPurpose
import com.theveloper.pixelplay.data.spotify.SpotifyRepository
import com.theveloper.pixelplay.data.spotify.toSong
import com.theveloper.pixelplay.data.youtube.InnerTubeClient
import com.theveloper.pixelplay.data.youtube.YouTubeMusicApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a pasted Spotify / YouTube Music link points at, ready for Search to show and open.
 * [searchText] + [filter] is the search that lists the same item on top.
 */
sealed interface LinkResolution {
    val service: MediaService
    val title: String
    val subtitle: String?
    val coverUrl: String?
    val searchText: String
    val filter: SearchFilterType

    data class Artist(
        override val service: MediaService,
        val name: String,
        override val coverUrl: String?,
    ) : LinkResolution {
        override val title get() = name
        override val subtitle: String? get() = null
        override val searchText get() = name
        override val filter get() = SearchFilterType.ARTISTS
    }

    /** [songs] when the tracklist could be read (YouTube Music); else it opens by title + artist. */
    data class Album(
        override val service: MediaService,
        override val title: String,
        val artist: String?,
        override val coverUrl: String?,
        val songs: List<Song> = emptyList(),
    ) : LinkResolution {
        override val subtitle get() = artist
        override val searchText get() = listOfNotNull(title, artist).joinToString(" ")
        override val filter get() = SearchFilterType.ALBUMS
    }

    data class Playlist(
        override val service: MediaService,
        override val title: String,
        val owner: String?,
        override val coverUrl: String?,
        val songs: List<Song> = emptyList(),
    ) : LinkResolution {
        override val subtitle get() = owner
        override val searchText get() = title
        override val filter get() = SearchFilterType.PLAYLISTS
    }

    /** [song] is the playable match (same YouTube video when the link was one), when found. */
    data class Track(
        override val service: MediaService,
        override val title: String,
        val artist: String?,
        override val coverUrl: String?,
        val song: Song?,
    ) : LinkResolution {
        override val subtitle get() = artist
        override val searchText get() = listOfNotNull(title, artist).joinToString(" ")
        override val filter get() = SearchFilterType.SONGS
    }
}

/**
 * Turns a [ParsedMediaUrl] into a [LinkResolution] without needing a sign-in:
 *  - Spotify: the public open.spotify.com page's Open Graph tags (name, artist, cover). A
 *    playlist's songs are read through your Spotify sign-in when there is one.
 *  - YouTube Music: YouTube's public oEmbed (title, channel) and the YouTube Music browse page
 *    for album / playlist tracklists.
 * Only runs when the app isn't in offline mode. Returns null when nothing usable came back.
 */
@Singleton
class MediaLinkResolver @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val innerTube: InnerTubeClient,
    private val youTubeMusic: YouTubeMusicApiService,
    private val spotifyRepository: SpotifyRepository,
    private val networkPolicy: NetworkAccessPolicy,
) {
    suspend fun resolve(link: ParsedMediaUrl): LinkResolution? = withContext(Dispatchers.IO) {
        if (networkPolicy.getDecision(NetworkPurpose.Enrichment) == NetworkDecision.OfflineMode) return@withContext null
        try {
            when (link.service) {
                MediaService.SPOTIFY -> resolveSpotify(link)
                MediaService.YOUTUBE_MUSIC -> resolveYouTube(link)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d("Link not resolved: ${e.javaClass.simpleName}")
            null
        }
    }

    // ── Spotify ──────────────────────────────────────────────────────────────

    private suspend fun resolveSpotify(link: ParsedMediaUrl): LinkResolution? {
        val html = fetch(link.cleanUrl, browserUa = true) ?: return null
        val tags = SpotifyPageTags.parse(html)
        val title = tags.title ?: return null
        return when (link.type) {
            MediaTargetType.ARTIST -> LinkResolution.Artist(MediaService.SPOTIFY, title, tags.image)
            MediaTargetType.ALBUM -> LinkResolution.Album(MediaService.SPOTIFY, title, tags.artist, tags.image)
            MediaTargetType.TRACK -> LinkResolution.Track(
                MediaService.SPOTIFY, title, tags.artist, tags.image,
                song = bestSongMatch(title, tags.artist, preferVideoId = null)
            )
            MediaTargetType.PLAYLIST -> {
                val songs = try {
                    spotifyRepository.getPlaylistTracks(link.id, maxTracks = PLAYLIST_MAX).map { it.toSong() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList() // not signed in to Spotify: open by search instead
                }
                LinkResolution.Playlist(MediaService.SPOTIFY, title, tags.owner, tags.image, songs)
            }
        }
    }

    // ── YouTube Music ────────────────────────────────────────────────────────

    private suspend fun resolveYouTube(link: ParsedMediaUrl): LinkResolution? = when (link.type) {
        MediaTargetType.TRACK -> {
            val meta = oEmbed("https://www.youtube.com/watch?v=${link.id}")
            val title = meta?.first ?: return null
            val artist = meta.second?.removeSuffix(" - Topic")
            LinkResolution.Track(
                MediaService.YOUTUBE_MUSIC, title, artist,
                coverUrl = "https://i.ytimg.com/vi/${link.id}/hqdefault.jpg",
                song = bestSongMatch(title, artist, preferVideoId = link.id)
            )
        }
        MediaTargetType.ALBUM -> {
            val songs = browseSongs("VL${link.id}")
            val first = songs.firstOrNull()
            val title = first?.album?.takeIf { it.isNotBlank() } ?: oEmbed("https://www.youtube.com/playlist?list=${link.id}")?.first
            if (title == null) null
            else LinkResolution.Album(MediaService.YOUTUBE_MUSIC, title, first?.displayArtist, first?.albumArtUriString, songs)
        }
        MediaTargetType.PLAYLIST -> {
            val meta = oEmbed("https://www.youtube.com/playlist?list=${link.id}")
            val songs = browseSongs("VL${link.id}")
            val title = meta?.first ?: return null
            LinkResolution.Playlist(MediaService.YOUTUBE_MUSIC, title, meta.second, songs.firstOrNull()?.albumArtUriString, songs)
        }
        MediaTargetType.ARTIST -> {
            val songs = browseSongs(link.id)
            val name = songs.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key?.takeIf { it.isNotBlank() }
            name?.let { LinkResolution.Artist(MediaService.YOUTUBE_MUSIC, it, null) }
        }
    }

    private suspend fun browseSongs(browseId: String): List<Song> {
        val songs = LinkedHashMap<String, Song>()
        var continuation: String? = null
        repeat(BROWSE_PAGES) {
            val page = innerTube.browse(browseId, continuation)
            youTubeMusic.hydrate(page.songs).forEach { songs.putIfAbsent(it.id, it) }
            continuation = page.continuation ?: return songs.values.toList()
        }
        return songs.values.toList()
    }

    /** The playable YouTube Music song for [title] by [artist]: the same video when known, else the best title match. */
    private suspend fun bestSongMatch(title: String, artist: String?, preferVideoId: String?): Song? {
        val query = listOfNotNull(title, artist).joinToString(" ")
        val results = youTubeMusic.searchSongs(query).getOrNull().orEmpty()
        if (preferVideoId != null) results.firstOrNull { it.youtubeId == preferVideoId || it.id.endsWith(preferVideoId) }?.let { return it }
        val wanted = fold(title)
        return results.firstOrNull { fold(it.title) == wanted }
            ?: results.firstOrNull { wanted.isNotBlank() && wanted in fold(it.title) }
            ?: results.firstOrNull()
    }

    /** YouTube oEmbed: (title, channel name). */
    private fun oEmbed(url: String): Pair<String, String?>? {
        val body = fetch("https://www.youtube.com/oembed?format=json&url=" + java.net.URLEncoder.encode(url, "UTF-8"), browserUa = false)
            ?: return null
        val json = JSONObject(body)
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
        return title to json.optString("author_name").takeIf { it.isNotBlank() }
    }

    private fun fetch(url: String, browserUa: Boolean): String? {
        val request = Request.Builder().url(url).get().apply {
            if (browserUa) header("User-Agent", BROWSER_UA)
            header("Accept-Language", "en")
        }.build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun fold(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private companion object {
        const val PLAYLIST_MAX = 200
        const val BROWSE_PAGES = 5
        const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}

/**
 * The Open Graph / music tags of a public Spotify page. Pure (unit-tested): [title] is the item's
 * name, [artist] the musician(s) for tracks and albums, [owner] a playlist's maker.
 */
data class SpotifyPageTags(val title: String?, val artist: String?, val owner: String?, val image: String?) {
    companion object {
        fun parse(html: String): SpotifyPageTags {
            fun meta(key: String): String? {
                val pattern = Regex(
                    """<meta[^>]+(?:property|name)=["']${Regex.escape(key)}["'][^>]*content=["']([^"']*)["']""",
                    RegexOption.IGNORE_CASE
                )
                val reversed = Regex(
                    """<meta[^>]+content=["']([^"']*)["'][^>]*(?:property|name)=["']${Regex.escape(key)}["']""",
                    RegexOption.IGNORE_CASE
                )
                return (pattern.find(html) ?: reversed.find(html))?.groupValues?.get(1)?.let(::unescape)?.trim()?.takeIf { it.isNotBlank() }
            }
            val title = meta("og:title")
            val description = meta("og:description")
            // "Song · Artist · 2015", "Artist · Album · 2020 · 12 songs", "Playlist · Owner · 50 items"
            val parts = description?.substringAfterLast(". ", description)?.split(" · ")?.map { it.trim() }.orEmpty()
            val artist = meta("music:musician_description")
                ?: when {
                    parts.size >= 2 && parts[0].equals("Song", true) -> parts[1]
                    parts.size >= 2 && parts[0].equals("Album", true) -> parts[1]
                    parts.size >= 2 && parts[0].equals("Single", true) -> parts[1]
                    parts.size >= 2 && parts[0].equals("EP", true) -> parts[1]
                    else -> null
                }
            val owner = if (parts.size >= 2 && parts[0].equals("Playlist", true)) parts[1] else null
            return SpotifyPageTags(title, artist, owner, meta("og:image"))
        }

        private fun unescape(s: String): String = s
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
