package com.theveloper.pixelplay.data.youtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Request rules for `googlevideo.com/videoplayback` URLs produced by NewPipeExtractor.
 *
 * Mirrors what NewPipe's own player (YoutubeHttpDataSource) does for the same extractor
 * version, which is also what Spotube's YouTube audio plugin relies on through its host
 * YouTubeEngine:
 *  - visionOS client URLs must be fetched with the visionOS user agent, everything else with
 *    a desktop browser user agent. A foreign UA (e.g. "PixelPlayer/1.0") gets HTTP 403.
 *  - requests are POSTs with the 2-byte body `x\0`, an incrementing `rn` query parameter and a
 *    normal Range header.
 *  - web client URLs additionally need Origin/Referer/Sec-Fetch headers.
 *  - large ranges are split into [CHUNK_BYTES] requests (yt-dlp uses the same 10 MiB chunk
 *    size) because googlevideo throttles or cuts long open-ended responses.
 */
object YouTubeHttp {
    /** Same value as NewPipe's DownloaderImpl.USER_AGENT. */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    const val CHUNK_BYTES: Long = 10L * 1024L * 1024L

    private const val YOUTUBE_BASE_URL = "https://www.youtube.com"
    private val POST_BODY = byteArrayOf(0x78, 0x00)
    private val requestNumber = AtomicInteger(0)

    fun isVideoPlaybackUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.host.endsWith("googlevideo.com") && parsed.encodedPath.startsWith("/videoplayback")
    }

    fun userAgentFor(url: String): String =
        if (runCatching { YoutubeParsingHelper.isVisionOsStreamingUrl(url) }.getOrDefault(false)) {
            YoutubeParsingHelper.getVisionOsUserAgent(null)
        } else {
            DESKTOP_USER_AGENT
        }

    /** Total byte size advertised by googlevideo in the `clen` parameter, if present. */
    fun contentLengthOf(url: String): Long? =
        url.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 }

    /** MIME type advertised by googlevideo in the `mime` parameter, if present. */
    fun mimeTypeOf(url: String): String? =
        url.toHttpUrlOrNull()?.queryParameter("mime")?.takeIf { it.startsWith("audio/") }

    /**
     * Builds a request for one byte range of a videoplayback URL.
     * [endInclusive] null means open-ended (only used when the total length is unknown).
     */
    fun playbackRequest(url: String, startInclusive: Long, endInclusive: Long?): Request {
        val requestUrl = if (isVideoPlaybackUrl(url) && !url.contains("&rn=")) {
            "$url&rn=${requestNumber.incrementAndGet()}"
        } else url
        val builder = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", userAgentFor(url))
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=$startInclusive-${endInclusive ?: ""}")
        if (runCatching { YoutubeParsingHelper.isWebStreamingUrl(url) }.getOrDefault(false)) {
            builder.header("Origin", YOUTUBE_BASE_URL)
                .header("Referer", YOUTUBE_BASE_URL)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
        }
        return if (isVideoPlaybackUrl(url)) {
            builder.post(POST_BODY.toRequestBody(null)).build()
        } else {
            builder.get().build()
        }
    }

    /** Next chunk end (inclusive) for a transfer from [position] up to [lastByteInclusive]. */
    fun chunkEnd(position: Long, lastByteInclusive: Long): Long =
        minOf(position + CHUNK_BYTES - 1, lastByteInclusive)
}
