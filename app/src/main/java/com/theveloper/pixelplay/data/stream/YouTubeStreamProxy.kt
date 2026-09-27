package com.theveloper.pixelplay.data.stream

import android.net.Uri
import com.theveloper.pixelplay.data.youtube.YouTubeHttp
import com.theveloper.pixelplay.data.youtube.YouTubeStreamExtractor
import com.theveloper.pixelplay.di.YouTubeOkHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import com.theveloper.pixelplay.data.youtube.renditionKey
import com.theveloper.pixelplay.data.youtube.selectRendition
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeStreamProxy @Inject constructor(
    @YouTubeOkHttpClient okHttpClient: OkHttpClient,
    private val youTubeStreamExtractor: YouTubeStreamExtractor
) : CloudStreamProxy<String>(okHttpClient) {

    private val renditionKeys = object : LinkedHashMap<String, String>(100, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 100
    }
    private val compatibleIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Called only when the player restarts extraction at a time position, not a byte offset. */
    fun useCompatibleRendition(id: String) {
        compatibleIds.add(id)
        synchronized(renditionKeys) { renditionKeys.remove(id) }
        invalidateStream(id)
    }

    override val allowedHostSuffixes: Set<String> = setOf(
        "googlevideo.com",
        "youtube.com",
        "ytimg.com",
        "googleusercontent.com",
        "gvt1.com"
    )

    override val cacheExpirationMs: Long = 0L // The extractor owns signed URL expiry.
    override val proxyTag: String = "YouTubeProxy"

    override val routePath: String = "/youtube/{videoId}"
    override val routeParamName: String = "videoId"
    override val uriScheme: String = "youtube"
    override val routePrefix: String = "/youtube"

    private val videoIdRegex = Regex("^[A-Za-z0-9_-]{11}\$")

    override fun parseRouteParam(value: String): String? {
        val clean = value.removePrefix("yt_")
        return if (validateId(clean)) clean else null
    }

    override fun validateId(id: String): Boolean {
        return videoIdRegex.matches(id.removePrefix("yt_"))
    }

    override fun formatIdForUrl(id: String): String {
        return id.removePrefix("yt_")
    }

    override fun invalidateStream(id: String) {
        super.invalidateStream(id)
        youTubeStreamExtractor.invalidate(id)
    }

    suspend fun prewarm(id: String) {
        youTubeStreamExtractor.streamManifest(id)
    }

    override suspend fun resolveStreamUrl(id: String): String? {
        val cleanId = id.removePrefix("yt_")
        val streams = youTubeStreamExtractor.streamManifest(cleanId)
        return synchronized(renditionKeys) {
            val stream = selectRendition(streams, renditionKeys[cleanId], cleanId in compatibleIds)
                ?: return@synchronized null
            renditionKeys[cleanId] = stream.renditionKey()
            // What's really playing, for the file info under the player's timeline.
            com.theveloper.pixelplay.data.youtube.StreamFormatRegistry.record(cleanId, stream)
            stream.url
        }
    }

    // googlevideo needs NewPipe's request shape (UA per client, POST "x\0", rn, Range).
    override fun buildUpstreamRequest(url: String, range: String?): Request {
        if (!YouTubeHttp.isVideoPlaybackUrl(url)) return super.buildUpstreamRequest(url, range)
        val spec = range?.removePrefix("bytes=")
        if (range != null && spec!!.startsWith("-")) {
            // Suffix range with unknown length: forward it verbatim.
            return YouTubeHttp.playbackRequest(url, 0L, null).newBuilder().header("Range", range).build()
        }
        val start = spec?.substringBefore('-')?.toLongOrNull() ?: 0L
        val end = spec?.substringAfter('-', "")?.toLongOrNull()
        return YouTubeHttp.playbackRequest(url, start, end)
    }

    override fun knownContentLength(url: String): Long? = YouTubeHttp.contentLengthOf(url)

    override fun knownContentType(url: String): String? = YouTubeHttp.mimeTypeOf(url)

    override val upstreamChunkBytes: Long = YouTubeHttp.CHUNK_BYTES

    override fun extractIdFromUri(uri: Uri): String? {
        val raw = uri.host?.takeIf { it.isNotEmpty() }
            ?: uri.schemeSpecificPart?.removePrefix("//")?.substringBefore('/')?.takeIf { it.isNotEmpty() }
        return raw?.removePrefix("yt_")
    }
}
