package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.di.YouTubeOkHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One playable picture quality of a video. [audioUrl] is null when the stream is muxed. */
data class VideoRendition(
    val height: Int,
    val fps: Int,
    val videoUrl: String,
    val mimeType: String,
    val audioUrl: String?,
)

/** Every quality a video offers, highest first. */
data class VideoStreams(val videoId: String, val renditions: List<VideoRendition>) {
    /** Distinct heights for the quality menu, highest first. */
    val heights: List<Int> get() = renditions.map { it.height }.distinct()
}

/**
 * Real video streams (with their qualities) for Video mode, through NewPipe. The YouTube
 * embed can't change quality (its setPlaybackQuality is a no-op), so Video mode plays these
 * in ExoPlayer instead and keeps the embed only as a fallback when extraction fails.
 */
@Singleton
class YouTubeVideoStreamResolver @Inject constructor(
    @YouTubeOkHttpClient val okHttpClient: OkHttpClient,
) {
    private val cache = object : LinkedHashMap<String, Pair<Long, VideoStreams>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, VideoStreams>>) = size > 16
    }

    /** Streams for [videoId], or null when extraction fails or takes longer than [timeoutMs]. */
    suspend fun resolve(videoId: String, timeoutMs: Long = 8_000L): VideoStreams? {
        val now = System.currentTimeMillis()
        synchronized(cache) { cache[videoId] }?.let { (at, streams) -> if (now - at < CACHE_MS) return streams }
        return try {
            withTimeoutOrNull(timeoutMs) { extract(videoId) }
                ?.takeIf { it.renditions.isNotEmpty() }
                ?.also { synchronized(cache) { cache[videoId] = now to it } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Video streams unavailable for %s", videoId)
            null
        }
    }

    /** Signed URLs expire and belong to the network they were made on. */
    fun invalidate(videoId: String) {
        synchronized(cache) { cache.remove(videoId) }
    }

    private suspend fun extract(videoId: String): VideoStreams {
        val (videoOnly, muxed, audio) = NewPipeExecution.run {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            Triple(extractor.videoOnlyStreams.orEmpty(), extractor.videoStreams.orEmpty(), extractor.audioStreams.orEmpty())
        }
        fun progressive(isUrl: Boolean, method: DeliveryMethod?) = isUrl && method == DeliveryMethod.PROGRESSIVE_HTTP

        val audioByMime = audio
            .filter { progressive(it.isUrl, it.deliveryMethod) }
            .sortedByDescending { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
        val m4aAudio = audioByMime.firstOrNull { it.format?.mimeType == "audio/mp4" }?.content
        val webmAudio = audioByMime.firstOrNull { it.format?.mimeType == "audio/webm" }?.content
        val anyAudio = audioByMime.firstOrNull()?.content

        val renditions = buildList {
            videoOnly.filter { progressive(it.isUrl, it.deliveryMethod) && it.height > 0 }.forEach { stream ->
                val mime = stream.format?.mimeType ?: return@forEach
                val audioUrl = when (mime) {
                    "video/mp4" -> m4aAudio ?: anyAudio
                    "video/webm" -> webmAudio ?: anyAudio
                    else -> anyAudio
                } ?: return@forEach
                add(VideoRendition(stream.height, stream.fps, stream.content, mime, audioUrl))
            }
            muxed.filter { progressive(it.isUrl, it.deliveryMethod) && it.height > 0 }.forEach { stream ->
                val mime = stream.format?.mimeType ?: return@forEach
                add(VideoRendition(stream.height, stream.fps, stream.content, mime, null))
            }
        }
            // One per height: prefer MP4 (hardware AVC everywhere), then the higher frame rate.
            .groupBy { it.height }
            .map { (_, sameHeight) ->
                sameHeight.sortedWith(
                    compareByDescending<VideoRendition> { it.mimeType == "video/mp4" }
                        .thenByDescending { it.fps }
                        .thenBy { it.audioUrl == null } // separate audio is the better quality
                ).first()
            }
            .sortedByDescending { it.height }
        return VideoStreams(videoId, renditions)
    }

    private companion object {
        const val TAG = "VideoStreamResolver"
        const val CACHE_MS = 30L * 60L * 1000L
    }
}

/**
 * The rendition to play. [requestedHeight] is the user's pick (or the starred quality); a
 * height this video doesn't have falls to the nearest lower one (or the lowest available).
 * Null means Auto: up to 1080p on unmetered networks, up to 480p on mobile data.
 */
fun pickVideoRendition(streams: VideoStreams, requestedHeight: Int?, unmetered: Boolean): VideoRendition? {
    val list = streams.renditions
    if (list.isEmpty()) return null
    val cap = requestedHeight ?: if (unmetered) 1080 else 480
    return list.firstOrNull { it.height <= cap } ?: list.last()
}
