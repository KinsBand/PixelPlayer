package com.theveloper.pixelplay.data.stream

import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.http.ContentType
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

/** End-to-end: player → local proxy → fake CDN, with the head cache in between. */
class HeadCacheProxyTest {
    @TempDir lateinit var dir: File

    private val size = 3_000_000
    private val data = ByteArray(size) { ((it * 31) xor (it shr 7)).toByte() }
    private val upstreamRanges = Collections.synchronizedList(mutableListOf<String>())
    private val cdn = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
        routing {
            get("/audio") {
                val range = call.request.headers["Range"]!!.removePrefix("bytes=")
                upstreamRanges += range
                val start = range.substringBefore('-').toInt()
                val end = minOf(range.substringAfter('-').toInt(), size - 1)
                call.response.header("Content-Range", "bytes $start-$end/$size")
                call.respondBytes(data.copyOfRange(start, end + 1), ContentType.parse("audio/webm"), HttpStatusCode.PartialContent)
            }
        }
    }
    private lateinit var proxy: TestProxy
    private val player = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()

    private inner class TestProxy(client: OkHttpClient) : CloudStreamProxy<String>(client) {
        @Volatile var contentLength = size.toLong()
        @Volatile var resolveDelayMs = 0L
        var cdnPort = 0
        override val allowedHostSuffixes = setOf("cdn.test")
        override val cacheExpirationMs = 0L
        override val proxyTag = "TestProxy"
        override val routePath = "/t/{id}"
        override val routeParamName = "id"
        override val uriScheme = "test"
        override val routePrefix = "/t"
        override fun parseRouteParam(value: String) = value
        override fun validateId(id: String) = id.length == 11
        override fun formatIdForUrl(id: String) = id
        override suspend fun resolveStreamUrl(id: String): String {
            delay(resolveDelayMs)
            return "http://cdn.test:$cdnPort/audio?clen=$contentLength"
        }
        override fun knownContentLength(url: String) = url.toHttpUrl().queryParameter("clen")!!.toLong()
        override fun knownContentType(url: String) = "audio/webm"
        public override val headCache = StreamHeadCache(dir, headBytes = 512 * 1024)
        override fun headKeyFor(id: String, url: String) =
            StreamHeadCache.Key(id, 251, knownContentLength(url), 0, "audio/webm")
        override fun cachedHeadFor(id: String) = headCache.lookup(id)
        suspend fun prefetch(id: String, bytes: Int) = prefetchHead(id, bytes)
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

    private fun fetch(range: String): Pair<Long, ByteArray> {
        val started = System.nanoTime()
        val request = Request.Builder().url(proxy.getProxyUrl(ID)).header("Range", range).build()
        player.newCall(request).execute().use { response ->
            val input = response.body.byteStream()
            val first = input.read()
            val firstByteMs = (System.nanoTime() - started) / 1_000_000
            return firstByteMs to (byteArrayOf(first.toByte()) + input.readBytes())
        }
    }

    @Test fun `cold stream uses a small first range and fills the head cache`() {
        assertArrayEquals(data, fetch("bytes=0-").second)
        assertEquals("0-1048575", upstreamRanges.first())
        assertEquals(512 * 1024, proxy.headCache.lookup(ID)?.length)
    }

    @Test fun `a cached song starts before its url is resolved`() {
        fetch("bytes=0-")
        proxy.resolveDelayMs = 1_500
        val (firstByteMs, body) = fetch("bytes=0-")
        assertArrayEquals(data, body)
        assertTrue(firstByteMs < 1_000, "first byte took $firstByteMs ms")
    }

    @Test fun `ranges inside the head are served locally`() {
        fetch("bytes=0-")
        upstreamRanges.clear()
        assertArrayEquals(data.copyOfRange(1_000, 2_000), fetch("bytes=1000-1999").second)
        assertTrue(upstreamRanges.isEmpty())
        assertArrayEquals(data.copyOfRange(2_000_000, size), fetch("bytes=2000000-").second)
    }

    @Test fun `a changed rendition aborts the response and drops the head`() {
        fetch("bytes=0-")
        proxy.contentLength = (size - 1).toLong()
        val result = runCatching { fetch("bytes=0-") }
        assertTrue(result.isFailure || result.getOrThrow().second.size < size)
        assertNull(proxy.headCache.lookup(ID))
    }

    @Test fun `prefetch stores the requested head`() = runBlocking {
        assertTrue(proxy.prefetch(ID, 256 * 1024))
        assertEquals(256 * 1024, proxy.headCache.lookup(ID)?.length)
        assertNotNull(upstreamRanges.firstOrNull { it == "0-262143" })
    }

    private companion object {
        const val ID = "abcdefghijk"
    }
}
