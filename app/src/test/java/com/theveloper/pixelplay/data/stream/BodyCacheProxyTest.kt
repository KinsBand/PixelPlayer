package com.theveloper.pixelplay.data.stream

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Player → local proxy → fake CDN, with the whole-song cache and source switching in between. */
class BodyCacheProxyTest {
    @TempDir lateinit var dir: File

    private val size = 3_000_000
    private val data = ByteArray(size) { ((it * 31) xor (it shr 7)).toByte() }
    private val upstreamRanges = Collections.synchronizedList(mutableListOf<String>())
    private val cdn = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
        routing {
            get("/{path}") {
                val range = call.request.headers["Range"]!!.removePrefix("bytes=")
                if (call.parameters["path"] == "silent") {
                    // Accepts the request, then never answers (a URL that will not work).
                    delay(10_000)
                    return@get
                }
                upstreamRanges += range
                val start = range.substringBefore('-').toInt()
                val end = minOf(range.substringAfter('-').toInt(), size - 1)
                call.response.header("Content-Range", "bytes $start-$end/$size")
                call.respondBytes(data.copyOfRange(start, end + 1), ContentType.parse("audio/webm"), HttpStatusCode.PartialContent)
            }
        }
    }
    private lateinit var proxy: TestProxy
    private val player = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build()

    private inner class TestProxy(client: OkHttpClient) :
        CloudStreamProxy<String>(client, firstByteTimeoutMs = 500) {
        var cdnPort = 0
        @Volatile var resolveFails = false
        /** The path the next resolution points at ("audio" works, "silent" stalls). */
        @Volatile var path = "audio"
        val stalls = AtomicInteger()
        val resolutions = AtomicInteger()
        override val allowedHostSuffixes = setOf("cdn.test")
        override val cacheExpirationMs = 60_000L
        override val proxyTag = "TestProxy"
        override val routePath = "/t/{id}"
        override val routeParamName = "id"
        override val uriScheme = "test"
        override val routePrefix = "/t"
        override fun parseRouteParam(value: String) = value
        override fun validateId(id: String) = id.length == 11
        override fun formatIdForUrl(id: String) = id
        override suspend fun resolveStreamUrl(id: String): String? {
            resolutions.incrementAndGet()
            if (resolveFails) return null
            return "http://cdn.test:$cdnPort/$path?clen=$size"
        }
        override fun knownContentLength(url: String) = url.toHttpUrl().queryParameter("clen")!!.toLong()
        override fun knownContentType(url: String) = "audio/webm"
        override fun headKeyFor(id: String, url: String) =
            StreamHeadCache.Key(id, 251, knownContentLength(url), 160_000, "audio/webm")
        public override val bodyCache = StreamBodyCache(dir)
        override fun cachedBodyFor(id: String) = bodyCache.lookup(id)
        override fun onFirstByteStall(id: String) {
            stalls.incrementAndGet()
            path = "audio" // Another source for the same rendition.
            invalidateStream(id)
        }
        suspend fun prefetchAll(id: String) = prefetchBody(id)
    }

    @BeforeEach fun setUp() = runBlocking {
        cdn.start(wait = false)
        val cdnDns = Dns { host -> if (host == "cdn.test") listOf(InetAddress.getLoopbackAddress()) else Dns.SYSTEM.lookup(host) }
        proxy = TestProxy(OkHttpClient.Builder().dns(cdnDns).build())
        proxy.cdnPort = cdn.engine.resolvedConnectors().first().port
        assertTrue(proxy.ensureReady(5_000))
    }

    @AfterEach fun tearDown() {
        proxy.stop()
        cdn.stop(0, 0)
    }

    private fun fetch(range: String? = null): ByteArray {
        val request = Request.Builder().url(proxy.getProxyUrl(ID)).apply { range?.let { header("Range", it) } }.build()
        return player.newCall(request).execute().use { it.body.bytes() }
    }

    @Test fun `a song streamed to the end plays again with no upstream request at all`() {
        assertArrayEquals(data, fetch("bytes=0-"))
        assertNotNull(proxy.bodyCache.lookup(ID))

        upstreamRanges.clear()
        proxy.resolveFails = true // No stream URL (or no network) any more.
        assertArrayEquals(data, fetch("bytes=0-"))
        assertArrayEquals(data.copyOfRange(1_234_567, 2_000_000), fetch("bytes=1234567-1999999"))
        assertArrayEquals(data.copyOfRange(size - 100, size), fetch("bytes=-100"))
        assertArrayEquals(data, fetch())
        assertTrue(upstreamRanges.isEmpty(), "upstream was asked for $upstreamRanges")
    }

    @Test fun `a partly played song is not served from the cache until the rest arrives`() {
        // The player read only the start (a bounded range, as after a skip).
        assertArrayEquals(data.copyOfRange(0, 500_000), fetch("bytes=0-499999"))
        Thread.sleep(200)
        assertNull(proxy.bodyCache.lookup(ID))
        val key = StreamHeadCache.Key(ID, 251, size.toLong(), 160_000, "audio/webm")
        assertEquals(500_000L, proxy.bodyCache.storedLength(key), "partial copy kept")

        // Picking up exactly where it stopped continues the partial copy to the end.
        assertArrayEquals(data.copyOfRange(500_000, size), fetch("bytes=500000-"))
        Thread.sleep(200)
        assertArrayEquals(data, proxy.bodyCache.lookup(ID)!!.file.readBytes())
    }

    @Test fun `prefetch stores the whole song in chunks`() = runBlocking {
        assertTrue(proxy.prefetchAll(ID))
        assertArrayEquals(data, proxy.bodyCache.lookup(ID)!!.file.readBytes())
        upstreamRanges.clear()
        assertArrayEquals(data, fetch("bytes=0-"))
        assertTrue(upstreamRanges.isEmpty())
    }

    @Test fun `a source that never sends a first byte is switched quickly`() {
        proxy.path = "silent"
        val started = System.nanoTime()
        val body = fetch("bytes=0-")
        val tookMs = (System.nanoTime() - started) / 1_000_000

        assertArrayEquals(data, body)
        assertEquals(1, proxy.stalls.get())
        assertTrue(tookMs < 5_000, "took $tookMs ms to switch source")
    }

    private companion object {
        const val ID = "abcdefghijk"
    }
}
