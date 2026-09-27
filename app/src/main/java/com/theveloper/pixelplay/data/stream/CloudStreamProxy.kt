package com.theveloper.pixelplay.data.stream

import android.net.Uri
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract base class for local HTTP proxy servers that stream cloud music audio.
 *
 * Subclasses define the route, ID type, validation, allowed hosts, and URL resolution.
 * The base class handles the full Ktor CIO server lifecycle, URL caching, and OkHttp
 * proxying with security checks via [CloudStreamSecurity].
 *
 * @param K The song identifier type (e.g. [String] for YouTube videoId)
 */
abstract class CloudStreamProxy<K : Any>(
    private val okHttpClient: OkHttpClient
) {
    private val streamingClient = okHttpClient.newBuilder()
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // ─── Subclass Configuration ────────────────────────────────────────

    protected abstract val allowedHostSuffixes: Set<String>
    protected abstract val cacheExpirationMs: Long
    protected abstract val proxyTag: String

    /** Route path registered with Ktor, e.g. "/youtube/{videoId}" */
    protected abstract val routePath: String
    /** The parameter name inside the route path, e.g. "videoId" */
    protected abstract val routeParamName: String
    /** URI scheme this proxy handles, e.g. "youtube" */
    protected abstract val uriScheme: String
    /** URL path prefix for proxy URLs, e.g. "/youtube" */
    protected abstract val routePrefix: String

    /** Parse the raw route parameter string into the typed ID, or null if invalid */
    protected abstract fun parseRouteParam(value: String): K?
    /** Validate whether the given ID is acceptable */
    protected abstract fun validateId(id: K): Boolean
    /** Convert the ID to a string for use in URLs */
    protected abstract fun formatIdForUrl(id: K): String
    /** Resolve the actual streaming URL for the given song ID */
    protected abstract suspend fun resolveStreamUrl(id: K): String?

    /**
     * Builds the upstream request for [url]. [range] is a normalized "bytes=a-b" header or null.
     * Override when the origin needs special headers/methods (e.g. googlevideo).
     */
    protected open fun buildUpstreamRequest(url: String, range: String?): Request =
        Request.Builder().url(url).apply {
            header("Accept-Encoding", "identity")
            range?.let { header("Range", it) }
        }.build()

    /**
     * Exact size of the resource behind [url] when it is known without a request (e.g. the
     * `clen` parameter of googlevideo URLs). A non-null value switches the proxy to chunked
     * mode: the proxy answers the player's range itself and fetches it upstream in
     * [upstreamChunkBytes]-sized requests, transparently resuming a failed chunk.
     */
    protected open fun knownContentLength(url: String): Long? = null

    /** Content type to report in chunked mode when upstream does not send a usable one. */
    protected open fun knownContentType(url: String): String? = null

    protected open val upstreamChunkBytes: Long = 10L * 1024L * 1024L

    // ─── Server State ──────────────────────────────────────────────────

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var actualPort: Int = 0
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    private val urlCache = ConcurrentHashMap<K, CachedUrl>()

    private data class CachedUrl(val url: String, val timestamp: Long, val expirationMs: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp >= expirationMs
    }

    // ─── Public API ────────────────────────────────────────────────────

    fun isReady(): Boolean = actualPort > 0

    fun startIfNeeded() {
        if (isReady() || startJob?.isActive == true) return
        start()
    }

    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true
        val stepMs = 50L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (isReady()) return true
            delay(stepMs)
            elapsed += stepMs
        }
        return false
    }

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean {
        startIfNeeded()
        return awaitReady(timeoutMs)
    }

    fun getProxyUrl(id: K): String {
        if (actualPort == 0) return ""
        if (!validateId(id)) return ""
        return "http://127.0.0.1:$actualPort$routePrefix/${formatIdForUrl(id)}"
    }

    /**
     * Parse a cloud URI (e.g. "youtube://xxxx") and return
     * the local proxy URL. Returns null if the URI doesn't match this proxy's scheme.
     */
    fun resolveUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != uriScheme) return null
        val rawId = extractIdFromUri(uri) ?: return null
        val id = parseRouteParam(rawId) ?: return null
        if (!validateId(id)) return null
        return getProxyUrl(id)
    }

    fun start() {
        startJob?.cancel()
        startJob = proxyScope.launch {
            try {
                val freePort = ServerSocket(0).use { it.localPort }
                val createdServer = createServer(freePort)
                createdServer.start(wait = false)
                server = createdServer
                actualPort = freePort
                Timber.d("$proxyTag started on port $actualPort")
            } catch (_: CancellationException) {
                Timber.d("$proxyTag start cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start $proxyTag")
            }
        }
    }

    fun stop() {
        startJob?.cancel()
        startJob = null
        proxyScope.coroutineContext.cancelChildren()
        server?.stop(1000, 2000)
        server = null
        actualPort = 0
        urlCache.clear()
        Timber.d("$proxyTag stopped")
    }

    // ─── Overridable Hooks ─────────────────────────────────────────────

    /** Extract the raw ID string from a parsed URI. Override for custom URI layouts. */
    protected open fun extractIdFromUri(uri: Uri): String? = uri.host

    open fun invalidateStream(id: K) {
        urlCache.remove(id)
    }

    // ─── Internal ──────────────────────────────────────────────────────

    protected suspend fun getOrFetchStreamUrl(id: K): String? {
        urlCache[id]?.let { cached ->
            if (!cached.isExpired()) return cached.url
        }
        return resolveStreamUrl(id)?.also { url ->
            if (cacheExpirationMs > 0) urlCache[id] = CachedUrl(url, System.currentTimeMillis(), cacheExpirationMs)
        }
    }

    private class Upstream(val url: String, val response: okhttp3.Response)

    // Retry only before downstream headers/bytes are sent. Media3 reopens its original
    // DataSpec after a socket failure, preserving the byte offset; never splice renditions.
    private suspend fun openUpstream(id: K, range: String?): Upstream {
        for (attempt in 0..2) {
            try {
                val url = getOrFetchStreamUrl(id) ?: throw java.io.IOException("Stream unavailable")
                if (!CloudStreamSecurity.isSafeRemoteStreamUrl(url, allowedHostSuffixes, true)) {
                    throw java.io.IOException("Rejected upstream URL")
                }
                val response = streamingClient.newCall(buildUpstreamRequest(url, range)).awaitResponse()
                if (attempt == 2 || !StreamRetryPolicy.retryStatus(response.code)) return Upstream(url, response)
                Timber.tag(proxyTag).w("Upstream HTTP %d (attempt %d), re-resolving", response.code, attempt + 1)
                response.close()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: java.io.IOException) {
                if (attempt == 2) throw error
            }
            invalidateStream(id)
            delay(StreamRetryPolicy.delayMs(attempt))
        }
        error("Unreachable retry state")
    }

    /** Thrown for upstream read failures so they can be retried; downstream errors are not. */
    private class UpstreamReadException(cause: java.io.IOException) : java.io.IOException(cause)

    private suspend fun openChunk(id: K, start: Long, endInclusive: Long, total: Long): Upstream {
        val upstream = openUpstream(id, "bytes=$start-$endInclusive")
        val code = upstream.response.code
        val sameResource = knownContentLength(upstream.url) == total
        if (!sameResource || (code != 206 && !(code == 200 && start == 0L))) {
            upstream.response.close()
            throw java.io.IOException("Upstream chunk rejected: HTTP $code sameResource=$sameResource")
        }
        return upstream
    }

    /**
     * Serves [validation]'s range of a resource of known [total] size, fetching it upstream in
     * bounded chunks. Only the first chunk is opened before headers are committed so failures
     * still map to a proper status code for the player.
     */
    private suspend fun serveChunked(
        call: ApplicationCall,
        id: K,
        total: Long,
        validation: CloudStreamSecurity.RangeHeaderValidation,
        requestStartedNanos: Long
    ) {
        val from: Long
        val to: Long
        when {
            validation.normalizedHeader == null -> { from = 0L; to = total - 1 }
            validation.isSuffixRange -> {
                from = (total - (validation.endInclusive ?: 0L)).coerceAtLeast(0L); to = total - 1
            }
            else -> {
                from = validation.startInclusive ?: 0L
                to = minOf(validation.endInclusive ?: (total - 1), total - 1)
            }
        }
        if (from >= total || from > to) {
            call.response.header("Content-Range", "bytes */$total")
            call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Range not satisfiable")
            return
        }

        val chunk = upstreamChunkBytes.coerceAtLeast(64L * 1024L)
        var position = from
        val first = openChunk(id, position, minOf(position + chunk - 1, to), total)
        var pending: Upstream? = first
        val contentTypeHeader = first.response.header("Content-Type")
            ?.takeIf { it.substringBefore(';').trim().startsWith("audio/") }
            ?: knownContentType(first.url)
        if (!CloudStreamSecurity.isSupportedAudioContentType(contentTypeHeader)) {
            first.response.close()
            call.respond(HttpStatusCode.BadGateway, "Unsupported stream content type")
            return
        }
        val contentType = contentTypeHeader?.substringBefore(';')?.trim()
            ?.let { runCatching { ContentType.parse(it) }.getOrNull() } ?: ContentType.Audio.Any

        val partial = validation.normalizedHeader != null
        call.response.header("Accept-Ranges", "bytes")
        if (partial) call.response.header("Content-Range", "bytes $from-$to/$total")

        try {
            call.respondBytesWriter(
                contentType = contentType,
                status = if (partial) HttpStatusCode.PartialContent else HttpStatusCode.OK,
                contentLength = to - from + 1
            ) {
                withContext(Dispatchers.IO) {
                    val buffer = ByteArray(64 * 1024)
                    var failures = 0
                    while (position <= to) {
                        val chunkEnd = minOf(position + chunk - 1, to)
                        try {
                            val upstream = pending ?: openChunk(id, position, chunkEnd, total)
                            pending = null
                            upstream.response.use { response ->
                                val input = response.body.byteStream()
                                var remaining = chunkEnd - position + 1
                                while (remaining > 0) {
                                    val read = try {
                                        input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    } catch (e: java.io.IOException) {
                                        throw UpstreamReadException(e)
                                    }
                                    if (read < 0) throw UpstreamReadException(java.io.IOException("Upstream chunk ended early"))
                                    writeFully(buffer, 0, read)
                                    if (position == from) {
                                        flush()
                                        Timber.tag("StreamingLatency").d("proxy_first_bytes_ms=%d",
                                            (System.nanoTime() - requestStartedNanos) / 1_000_000)
                                    }
                                    position += read
                                    remaining -= read
                                }
                            }
                            failures = 0
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: UpstreamReadException) {
                            if (++failures > 3) throw e
                            Timber.tag(proxyTag).w(e, "Chunk failed at %d/%d, resuming", position, total)
                            invalidateStream(id)
                            delay(StreamRetryPolicy.delayMs(failures - 1))
                        }
                    }
                }
            }
        } finally {
            pending?.response?.close()
        }
    }

    private fun createServer(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        return embeddedServer(CIO, port = port, host = "127.0.0.1") {
            routing {
                get(routePath) {
                    val requestStartedNanos = System.nanoTime()
                    val rawParam = call.parameters[routeParamName]
                    val id = rawParam?.let { parseRouteParam(it) }
                    if (id == null || !validateId(id)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                        return@get
                    }

                    try {
                        val rangeValidation = CloudStreamSecurity.validateRangeHeader(
                            call.request.headers["Range"]
                        )
                        if (!rangeValidation.isValid) {
                            call.respond(
                                HttpStatusCode(416, "Range Not Satisfiable"),
                                "Invalid range header"
                            )
                            return@get
                        }

                        val total = getOrFetchStreamUrl(id)?.let { knownContentLength(it) }
                        if (total != null) {
                            serveChunked(call, id, total, rangeValidation, requestStartedNanos)
                            return@get
                        }

                        val response = openUpstream(id, rangeValidation.normalizedHeader).response

                        response.use { upstream ->
                            if (upstream.code != 200 && upstream.code != 206) {
                                call.respond(
                                    CloudStreamSecurity.mapUpstreamStatusToProxyStatus(upstream.code),
                                    "Upstream stream request failed"
                                )
                                return@get
                            }

                            val body = upstream.body
                            val contentTypeHeader = upstream.header("Content-Type")

                            if (!CloudStreamSecurity.isSupportedAudioContentType(contentTypeHeader)) {
                                call.respond(
                                    HttpStatusCode.BadGateway,
                                    "Unsupported stream content type"
                                )
                                return@get
                            }

                            val contentLength = upstream.header("Content-Length")
                            if (!CloudStreamSecurity.isAcceptableContentLength(contentLength)) {
                                call.respond(
                                    HttpStatusCode(413, "Payload Too Large"),
                                    "Stream content too large"
                                )
                                return@get
                            }

                            val contentRange = upstream.header("Content-Range")
                            val acceptRanges = upstream.header("Accept-Ranges")
                            val responseContentType = contentTypeHeader
                                ?.substringBefore(';')
                                ?.trim()
                                ?.let { raw ->
                                    runCatching { ContentType.parse(raw) }.getOrNull()
                                }
                                ?: ContentType.Audio.Any

                            if (upstream.code == 206) {
                                call.response.status(HttpStatusCode.PartialContent)
                            } else {
                                call.response.status(HttpStatusCode.OK)
                            }
                            call.response.header("Accept-Ranges", acceptRanges ?: "bytes")
                            contentRange?.let { call.response.header("Content-Range", it) }

                            // Pass the length to the content object: setting the header manually
                            // made Ktor add "Transfer-Encoding: chunked" next to Content-Length.
                            call.respondBytesWriter(
                                contentType = responseContentType,
                                contentLength = contentLength?.toLongOrNull()
                            ) {
                                withContext(Dispatchers.IO) {
                                    body.byteStream().use { input ->
                                        val buffer = ByteArray(64 * 1024)
                                        var bytesRead: Int
                                        var firstWrite = true
                                        while (input.read(buffer)
                                                .also { bytesRead = it } != -1
                                        ) {
                                            writeFully(buffer, 0, bytesRead)
                                            if (firstWrite) {
                                                firstWrite = false
                                                flush()
                                                Timber.tag("StreamingLatency").d("proxy_first_bytes_ms=%d",
                                                    (System.nanoTime() - requestStartedNanos) / 1_000_000)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val msg = e.toString()
                        if (msg.contains("ChannelWriteException") ||
                            msg.contains("ClosedChannelException") ||
                            msg.contains("Broken pipe") ||
                            msg.contains("JobCancellationException")
                        ) {
                            // Client disconnected, normal behavior
                        } else {
                            Timber.w(e, "$proxyTag stream failed")
                            if (!call.response.isCommitted) call.respond(HttpStatusCode.BadGateway, "Stream unavailable")
                            else throw e
                        }
                    }
                }
            }
        }
    }
}
