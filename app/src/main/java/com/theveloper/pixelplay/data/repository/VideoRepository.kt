package com.theveloper.pixelplay.data.repository

import android.content.Context
import com.theveloper.pixelplay.BuildConfig
import com.theveloper.pixelplay.data.model.TrackVideo
import com.theveloper.pixelplay.data.model.VideoType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    @ApplicationContext context: Context,
) {
    private val choices = context.getSharedPreferences("track_video_choices", Context.MODE_PRIVATE)
    private data class Cached(val videos: List<TrackVideo>, val expiresAt: Long)
    private val cache = object : LinkedHashMap<String, Cached>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>) = size > 64
    }
    private val locks = Array(16) { Mutex() }

    internal fun trackKey(artist: String, title: String, type: VideoType) =
        "${artist.trim().lowercase(Locale.ROOT)}|${title.trim().lowercase(Locale.ROOT)}|${type.name}"

    fun rememberChoice(artist: String, title: String, type: VideoType, id: String) {
        choices.edit().putString(trackKey(artist, title, type), id).apply()
    }

    suspend fun searchVideosForTrack(
        artistName: String,
        trackTitle: String,
        type: VideoType,
        refresh: Boolean = false,
        /** The song's length, to prefer videos of about the same length (0 = unknown). */
        durationMs: Long = 0,
    ): List<TrackVideo> = withContext(Dispatchers.IO) {
        require(trackTitle.isNotBlank()) { "This song needs a title before videos can be found." }
        val key = trackKey(artistName, trackTitle, type)
        locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            val cached = synchronized(cache) { cache[key] }
            val videos = if (!refresh && cached != null && cached.expiresAt > System.currentTimeMillis()) {
                cached.videos
            } else {
                val cleanTitle = videoSearchTitle(trackTitle, type)
                val primaryArtist = videoSearchArtist(artistName)
                fun search(q: String) = if (BuildConfig.YOUTUBE_API_KEY.isBlank()) searchNative(q) else searchApi(q)
                val found = search("$primaryArtist - $cleanTitle ${type.searchQuerySuffix}".trim())
                ensureActive()
                // Strict match first; then relax the version keyword; then a plain search.
                var ranked = rankTrackVideos(found, primaryArtist, cleanTitle, type, durationMs)
                if (ranked.isEmpty() && type != VideoType.ALL) {
                    ranked = rankTrackVideos(found, primaryArtist, cleanTitle, type, durationMs, requireVersion = false)
                }
                if (ranked.isEmpty()) {
                    val plain = runCatching { search("$primaryArtist $cleanTitle") }.getOrDefault(emptyList())
                    ensureActive()
                    ranked = rankTrackVideos(plain, primaryArtist, cleanTitle, type, durationMs, requireVersion = false)
                }
                synchronized(cache) {
                    // Video ids for a song rarely change: keep hits for 6 h so re-opening Video is instant.
                    cache[key] = Cached(ranked, System.currentTimeMillis() + if (ranked.isEmpty()) 60_000 else 6 * 60 * 60_000L)
                }
                ranked
            }
            val preferred = choices.getString(key, null)
            videos.sortedByDescending { it.id == preferred }
        }
    }

    /**
     * The artist's official music videos, for the artist page. Only videos whose title or channel
     * names the artist are kept. Cached with the track results (6 h).
     */
    suspend fun searchArtistVideos(
        artistName: String,
        /** MUSIC_VIDEO (default) or LIVE_PERFORMANCE (the artist page's Live performances). */
        type: VideoType = VideoType.MUSIC_VIDEO,
    ): List<TrackVideo> = withContext(Dispatchers.IO) {
        if (artistName.isBlank()) return@withContext emptyList()
        val key = "${artistName.trim().lowercase(Locale.ROOT)}|<artist>|${type.name}"
        locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            val cached = synchronized(cache) { cache[key] }
            if (cached != null && cached.expiresAt > System.currentTimeMillis()) return@withLock cached.videos
            val query = "$artistName ${type.searchQuerySuffix}"
            val found = if (BuildConfig.YOUTUBE_API_KEY.isBlank()) searchNative(query) else searchApi(query)
            ensureActive()
            val needle = artistName.lowercase(Locale.ROOT)
            val videos = found
                .filter { video ->
                    val title = video.title.lowercase(Locale.ROOT)
                    val channel = video.channel.lowercase(Locale.ROOT)
                    (needle in title || needle in channel) &&
                        listOf("reaction", "karaoke", "cover", "tutorial", "lesson").none { it in title } &&
                        // Live performances must say so in the title.
                        (type != VideoType.LIVE_PERFORMANCE || LIVE_WORDS.any { it in title })
                }
                .distinctBy { it.id }
            synchronized(cache) {
                cache[key] = Cached(videos, System.currentTimeMillis() + if (videos.isEmpty()) 60_000 else 6 * 60 * 60_000L)
            }
            videos
        }
    }

    private fun searchNative(query: String): List<TrackVideo> {
        // General video search, not the audio-only music_songs filter.
        val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query, listOf("videos"), "")
        return SearchInfo.getInfo(ServiceList.YouTube, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val id = item.url.substringAfter("watch?v=", "").substringBefore('&')
                if (!Regex("[A-Za-z0-9_-]{11}").matches(id)) return@mapNotNull null
                TrackVideo(id, item.name.orEmpty(), item.uploaderName.orEmpty(),
                    item.thumbnails.lastOrNull()?.url.orEmpty(), durationSec = item.duration.coerceAtLeast(0))
            }.take(SEARCH_RESULTS)
    }

    private fun searchApi(query: String): List<TrackVideo> {
        val url = "https://www.googleapis.com/youtube/v3/search".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet")
            .addQueryParameter("q", query)
            .addQueryParameter("type", "video")
            .addQueryParameter("videoEmbeddable", "true")
            .addQueryParameter("maxResults", SEARCH_RESULTS.toString())
            .addQueryParameter("key", BuildConfig.YOUTUBE_API_KEY).build()
        return httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) {
                when (response.code) {
                    403 -> "YouTube search is unavailable. Check the API key or search quota."
                    429 -> "YouTube is busy. Try again shortly."
                    else -> "YouTube search failed (${response.code}). Try again."
                }
            }
            val items = JSONObject(response.body.string()).getJSONArray("items")
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val snippet = item.getJSONObject("snippet")
                    add(TrackVideo(item.getJSONObject("id").getString("videoId"),
                        snippet.getString("title"), snippet.getString("channelTitle"),
                        snippet.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty()))
                }
            }
        }
    }
}

private const val SEARCH_RESULTS = 25

/** Words a live-performance title has to contain to count as one. */
private val LIVE_WORDS = listOf("live", "concert", "session", "festival", "tour", "unplugged", "tiny desk")

/** "Song (feat. X) - Remastered 2011 [Live]" → "Song" (keeps "live"/"acoustic"/… when that's the version asked for). */
internal fun videoSearchTitle(title: String, type: VideoType): String {
    var t = title
    t = t.replace(Regex("""\s*[(\[](?:feat\.?|ft\.?|featuring|with)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("""\s+(?:feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("""\s*-\s*(?:\d{4}\s+)?remaster(?:ed)?.*$""", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("""\s*[(\[][^)\]]*(?:remaster|radio edit|single version|album version|mono|stereo)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
    if (type != VideoType.LIVE_PERFORMANCE) t = t.replace(Regex("""\s*-\s*live\b.*$""", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("""\s*[(\[](?:official\s+)?(?:music\s+)?(?:video|audio|lyrics?)[)\]]""", RegexOption.IGNORE_CASE), "")
    return t.replace(Regex("""\s+"""), " ").trim().ifBlank { title.trim() }
}

/** "Artist, Other & Third" → "Artist" (the search works best on the main artist). */
internal fun videoSearchArtist(artist: String): String =
    artist.split(Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|;)\s*""", RegexOption.IGNORE_CASE))
        .firstOrNull { it.isNotBlank() }?.trim() ?: artist.trim()

/** Alternate performances can have different intros and durations. */
internal fun rankTrackVideos(
    videos: List<TrackVideo>,
    artist: String,
    title: String,
    type: VideoType,
    durationMs: Long = 0,
    requireVersion: Boolean = true,
): List<TrackVideo> {
    fun words(value: String) = value.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }.toSet()
    val titleWords = words(title)
    val artistWords = words(artist)
    val artistKey = artist.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
    val songSec = durationMs / 1000
    return videos.distinctBy { it.id }.filter { video ->
        val name = words(video.title)
        val versionMatches = !requireVersion || when (type) {
            VideoType.LIVE_PERFORMANCE -> name.any { it in setOf("live", "concert", "session", "performance") }
            VideoType.ACOUSTIC -> name.any { it in setOf("acoustic", "unplugged") }
            VideoType.LYRIC_VIDEO -> name.any { it in setOf("lyric", "lyrics") }
            VideoType.REMIX -> name.any { it in setOf("remix", "mix", "remixed", "edit") }
            VideoType.COVER -> name.any { it in setOf("cover", "tribute") }
            else -> true
        }
        versionMatches && titleWords.isNotEmpty() && titleWords.count { it in name } >= (titleWords.size + 1) / 2
    }.sortedByDescending { video ->
        val name = words(video.title)
        val channel = words(video.channel)
        val channelKey = video.channel.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
        var score = titleWords.count { it in name } * 5 + artistWords.count { it in name || it in channel } * 3
        // The artist's own channel or their VEVO is almost always the real video.
        if (artistKey.isNotEmpty() && (channelKey == artistKey || channelKey == artistKey + "vevo" ||
                channelKey == artistKey + "official" || channelKey.endsWith("vevo") && artistKey in channelKey)) score += 6
        val noise = setOf("reaction", "reacts", "karaoke", "tutorial", "lesson", "nightcore", "slowed", "reverb",
            "sped", "8d", "visualizer", "visualiser", "instrumental", "bass", "boosted")
        if (type != VideoType.COVER && type != VideoType.REMIX) score -= name.count { it in noise } * 6
        if (type == VideoType.MUSIC_VIDEO) {
            if ("official" in name) score += 5
            if ("video" in name) score += 3
            if (name.any { it in setOf("live", "cover", "remix", "lyrics", "audio") }) score -= 8
            // "Artist - Topic" channels are audio-only uploads with a still image.
            if (video.channel.endsWith("- Topic", ignoreCase = true)) score -= 10
        }
        // About the song's length: a full video, not a teaser or an hour-long loop.
        if (songSec > 0 && video.durationSec > 0) {
            val ratio = video.durationSec.toDouble() / songSec
            score += when {
                ratio in 0.85..1.35 -> 4
                ratio in 0.6..2.0 -> 0
                else -> -6
            }
        }
        score
    }
}
