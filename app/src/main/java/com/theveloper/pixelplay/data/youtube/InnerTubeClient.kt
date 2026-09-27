package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.model.*
import com.theveloper.pixelplay.data.stream.awaitResponse
import com.theveloper.pixelplay.data.ytmusic.MusicBrowseParser
import com.theveloper.pixelplay.di.YouTubeOkHttpClient
import com.theveloper.pixelplay.utils.KeyedMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Public music metadata and direct audio. NewPipe handles extraction when signatures are required. */
@Singleton
class InnerTubeClient @Inject constructor(@YouTubeOkHttpClient client: OkHttpClient) {
    private val http = client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build()
    private val locks = KeyedMutex<String>()
    private data class Cached(val items: List<SearchResultItem>, val expiry: Long)
    private val cache = object : LinkedHashMap<String, Cached>(48, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>) = size > 48
    }.also { map ->
        com.theveloper.pixelplay.data.diagnostics.HeapPressure.register("innertube-search") { synchronized(map) { map.clear() } }
    }
    @Volatile private var version: String? = null
    private fun key(query: String, filter: SearchFilterType) = "${filter.name}:${query.trim().lowercase(Locale.ROOT)}"
    fun cached(query: String, filter: SearchFilterType): List<SearchResultItem> = synchronized(cache) {
        cache[key(query, filter)]?.takeIf { it.expiry > System.currentTimeMillis() }?.items.orEmpty()
    }

    private suspend fun clientVersion(): String = locks.withKey("clientVersion") {
        version?.let { return@withKey it }
        http.newCall(Request.Builder().url("https://music.youtube.com/").build()).awaitResponse().use { response ->
            check(response.isSuccessful) { "YouTube Music is unavailable (${response.code})" }
            val html = response.body.string()
            val found = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"")
                .find(html)?.groupValues?.get(1)
            check(!found.isNullOrBlank()) { "YouTube Music client configuration unavailable" }
            version = found
            found
        }
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject {
        val clientVersion = clientVersion()
        body.put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB_REMIX").put("clientVersion", clientVersion)
            .put("hl", "en").put("gl", Locale.getDefault().country.ifBlank { "US" })))
        val request = Request.Builder().url("https://music.youtube.com/youtubei/v1/$path?prettyPrint=false")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-Youtube-Client-Name", "67").header("X-Youtube-Client-Version", clientVersion)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return http.newCall(request).awaitResponse().use { response ->
            if (response.code == 400) version = null
            check(response.isSuccessful) { "YouTube Music request failed (${response.code})" }
            JSONObject(response.body.string()).also { check(!it.has("error")) { "YouTube Music returned an error" } }
        }
    }

    suspend fun search(query: String, filter: SearchFilterType): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        locks.withKey(key(query, filter)) {
            synchronized(cache) { cache[key(query, filter)] }?.takeIf { it.expiry > System.currentTimeMillis() }
                ?.let { return@withKey it.items }
            val body = JSONObject().put("query", query.trim())
            val params = when (filter) {
                SearchFilterType.ALL -> null
                SearchFilterType.SONGS -> "EgWKAQIIAWoMEA4QChADEAQQCRAF"
                SearchFilterType.ARTISTS -> "EgWKAQIgAWoMEA4QChADEAQQCRAF"
                // ytmusicapi: EgWKAQ + filter (II songs, IQ videos, IY albums, Ig artists) + AWoMEA4QChADEAQQCRAF.
                // "IQ" (videos) was used here before, so the Albums tab only ever got videos back.
                SearchFilterType.ALBUMS -> "EgWKAQIYAWoMEA4QChADEAQQCRAF"
                // Community playlists (ytmusicapi "EgeKAQQoA" + "EA" + "BagwQDhAKEAMQBBAJEAU=").
                SearchFilterType.PLAYLISTS -> "EgeKAQQoAEABagwQDhAKEAMQBBAJEAU="
            }
            if (params != null) body.put("params", params)
            val root = post("search", body)
            check(root.has("contents")) { "Unrecognized YouTube Music search response" }
            val results = InnerTubeParser.results(root).filter { item ->
                when (filter) {
                    SearchFilterType.ALL -> true
                    SearchFilterType.SONGS -> item is SearchResultItem.SongItem
                    SearchFilterType.ARTISTS -> item is SearchResultItem.ArtistItem
                    SearchFilterType.ALBUMS -> item is SearchResultItem.AlbumItem
                    SearchFilterType.PLAYLISTS -> item is SearchResultItem.PlaylistItem
                }
            }
            synchronized(cache) { cache[key(query, filter)] = Cached(results, System.currentTimeMillis() + 300_000) }
            results
        }
    }

    data class BrowsePage(val songs: List<Song>, val continuation: String?)
    suspend fun browse(id: String, continuation: String? = null): BrowsePage = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9_-]{2,200}")))
        val body = JSONObject()
        if (continuation == null) body.put("browseId", id) else body.put("continuation", continuation)
        val root = post("browse", body)
        BrowsePage(InnerTubeParser.results(root).filterIsInstance<SearchResultItem.SongItem>().map { it.song },
            MusicBrowseParser.continuation(root))
    }

    suspend fun directStreams(id: String): List<YouTubeAudioStream> = withContext(Dispatchers.IO) {
        // Playback must not bootstrap the Music homepage / WEB_REMIX search client.
        // Use the maintained native VISIONOS request (including visitor data and UA).
        // NewPipeExecution cancels its blocking HTTP calls when a skip or fallback wins.
        val nonce = org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.generateContentPlaybackNonce()
        val response = NewPipeExecution.run {
            org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper.getVisionOsPlayerResponse(
                org.schabi.newpipe.extractor.localization.ContentCountry.DEFAULT,
                org.schabi.newpipe.extractor.localization.Localization.DEFAULT,
                id,
                nonce
            )
        }
        val root = JSONObject(com.grack.nanojson.JsonWriter.string(response))
        if (root.optJSONObject("playabilityStatus")?.optString("status") != "OK") return@withContext emptyList()
        if (root.optJSONObject("videoDetails")?.optString("videoId") != id) return@withContext emptyList()
        InnerTubeParser.directStreams(root, System.currentTimeMillis()).map { stream ->
            stream.copy(url = stream.url.toHttpUrlOrNull()!!.newBuilder()
                .setQueryParameter("cpn", nonce).build().toString())
        }
    }
}

/** Parse only result row endpoints: menu and recommendation IDs must never become playable results. */
internal object InnerTubeParser {
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    private val duration = Regex("\\d+(?::\\d{2}){1,2}")
    private fun id(value: String): Long = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))).long or Long.MIN_VALUE

    fun results(root: JSONObject): List<SearchResultItem> {
        val rows = MusicBrowseParser.objects(root, "musicResponsiveListItemRenderer") +
            MusicBrowseParser.objects(root, "musicTwoRowItemRenderer")
        return rows.mapNotNull { row ->
            val flex = MusicBrowseParser.objects(row.optJSONArray("flexColumns"), "musicResponsiveListItemFlexColumnRenderer")
                .mapNotNull { it.optJSONObject("text") }
            val titleObject = flex.firstOrNull() ?: row.optJSONObject("title")
            val title = MusicBrowseParser.text(titleObject).trim()
            if (title.isBlank()) return@mapNotNull null
            val subtitle = flex.drop(1).map(MusicBrowseParser::text).joinToString(" • ")
                .ifBlank { MusicBrowseParser.text(row.optJSONObject("subtitle")) }
            val browse = row.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?: MusicBrowseParser.objects(titleObject, "browseEndpoint").firstOrNull()
            val browseId = browse?.optString("browseId").orEmpty()
            val pageType = browse?.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
            val art = MusicBrowseParser.thumbnail(row)
            val artist = flex.drop(1).flatMap { MusicBrowseParser.objects(it, "browseEndpoint") }
            val artistText = flex.drop(1).flatMap { column ->
                val runs = column.optJSONArray("runs")
                (0 until (runs?.length() ?: 0)).mapNotNull { index ->
                    val run = runs!!.optJSONObject(index) ?: return@mapNotNull null
                    val endpoint = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    if (endpoint?.optString("browseId")?.startsWith("UC") == true) run.optString("text") else null
                }
            }.distinct().joinToString(", ").ifBlank {
                subtitle.split(" • ").firstOrNull { it !in setOf("Song", "Video", "Album", "Single", "EP") && !duration.matches(it) }.orEmpty()
            }
            when {
                pageType == "MUSIC_PAGE_TYPE_ARTIST" && browseId.startsWith("UC") ->
                    SearchResultItem.ArtistItem(Artist(id(browseId), title, 0, art), browseId)
                pageType == "MUSIC_PAGE_TYPE_ALBUM" && browseId.isNotBlank() ->
                    SearchResultItem.AlbumItem(Album(id(browseId), title, artistText, 0, 0, art, 0), browseId)
                (pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL")) && browseId.isNotBlank() ->
                    SearchResultItem.PlaylistItem(Playlist("ytbrowse_$browseId", title, emptyList(),
                        coverImageUri = art, source = "YOUTUBE_MUSIC", ownerName = artistText), browseId)
                else -> {
                    val watch = row.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { videoId.matches(it) }
                        ?: row.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                        ?: MusicBrowseParser.objects(titleObject, "watchEndpoint").firstOrNull()?.optString("videoId")
                    if (watch == null || !videoId.matches(watch)) return@mapNotNull null
                    val durationText = (subtitle.split(" • ") + MusicBrowseParser.objects(row.optJSONArray("fixedColumns"),
                        "musicResponsiveListItemFixedColumnRenderer").map { MusicBrowseParser.text(it.optJSONObject("text")) })
                        .firstOrNull { duration.matches(it) }
                    val seconds = durationText?.split(':')?.fold(0L) { total, part -> total * 60 + part.toLong() } ?: 0L
                    val artistBrowseId = artist.firstOrNull { it.optString("browseId").startsWith("UC") }?.optString("browseId")
                    val artistId = id(artistBrowseId ?: artistText)
                    SearchResultItem.SongItem(Song.emptySong().copy(id = "yt_$watch", title = title,
                        artist = artistText.ifBlank { "Unknown Artist" }, artistId = artistId,
                        artists = listOf(ArtistRef(artistId, artistText, isPrimary = true)),
                        album = "YouTube Music", albumArtUriString = art, duration = seconds * 1000,
                        contentUriString = "youtube://$watch", youtubeId = watch, mimeType = "audio/mp4"), artistBrowseId)
                }
            }
        }.distinctBy { it.searchIdentity() }
    }

    fun directStreams(root: JSONObject, now: Long): List<YouTubeAudioStream> {
        val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return emptyList()
        return (0 until formats.length()).mapNotNull { index ->
            val format = formats.optJSONObject(index) ?: return@mapNotNull null
            val mime = format.optString("mimeType").substringBefore(';')
            val url = format.optString("url").toHttpUrlOrNull() ?: return@mapNotNull null
            // Ciphered and throttled ('n') URLs need NewPipe's player-script transforms.
            if (mime !in setOf("audio/mp4", "audio/webm") || url.scheme != "https" ||
                !url.host.endsWith(".googlevideo.com") || url.queryParameter("n") != null ||
                format.optString("type") == "FORMAT_STREAM_TYPE_OTF") return@mapNotNull null
            val length = format.optString("contentLength").toLongOrNull() ?: return@mapNotNull null
            val expiry = YouTubeAudioStream.expiry(url.toString(), now)
            if (length <= 0 || expiry <= now) return@mapNotNull null
            YouTubeAudioStream(url.toString(), mime, format.optInt("bitrate"), expiry, length)
        }.distinctBy { it.url }
    }
}

internal fun SearchResultItem.searchIdentity(): String = when (this) {
    is SearchResultItem.SongItem -> "song:${song.id}"
    is SearchResultItem.ArtistItem -> "artist:${browseId ?: artist.id}"
    is SearchResultItem.AlbumItem -> "album:${browseId ?: album.id}"
    is SearchResultItem.PlaylistItem -> "playlist:${browseId ?: playlist.id}"
}
