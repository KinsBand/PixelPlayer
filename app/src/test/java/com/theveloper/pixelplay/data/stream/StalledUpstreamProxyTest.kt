package com.theveloper.pixelplay.data.stream

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Player → local proxy → a CDN whose connections stall mid-transfer, like on a weak signal. */
class StalledUpstreamProxyTest {
    private val size = 3_000_000
    private val data = ByteArray(size) { ((it * 31) xor (it shr 7)).toByte() }
    private val upstreamRanges = Collections.synchronizedList(mutableListOf<String>())
    private val requests = AtomicInteger()

    /** Per upstream request (0-based): bytes sent before the connection goes silent, or null. */
    @Volatile private var stallAfter: (Int) -> Int? = { null }

    private val cdn = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
        routing {
            get("/audio") {
                val index = requests.getAndIncrement()
                val range = call.request.headers["Range"]!!.removePrefix("bytes=")
                upstreamRanges += range
                val start = range.substringBefore('-').toInt()
                val end = minOf(range.substringAfter('-').toInt(), size - 1)
                val stall = stallAfter(index)
                call.response.header("Content-Range", "bytes $start-$end/$size")
                call.respondBytesWriter(ContentType.parse("audio/webm"), HttpStatusCode.PartialContent, (end - start + 1).toLong()) {
                    val body = data.copyOfRange(start, end + 1)
                    if (stall == null) {
                        writeFully(body, 0, body.size)
                    } else {
                        writeFully(body, 0, minOf(stall, body.size))
                        flush()
                        delay(STALL_MS)
                    }
                }
            }
        }
    }
    private lateinit var proxy: TestProxy
    private val player = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()

    private inner class TestProxy(client: OkHttpClient, stallTimeoutMs: Long = 400) :
        CloudStreamProxy<String>(client, upstreamStallTimeoutMs = stallTimeoutMs, resumeBudgetMs = 2_000) {
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
        override suspend fun resolveStreamUrl(id: String) = "http://cdn.test:$cdnPort/audio?clen=$size"
        override fun knownContentLength(url: String) = url.toHttpUrl().queryParameter("clen")!!.toLong()
        override fun knownContentType(url: String) = "audio/webm"
        // Keep the URL: re-resolving is the subclass's business, not what's tested here.
        override fun onUpstreamFailure(id: String, httpStatus: Int?, consecutiveFailures: Int) {}
    }

    @BeforeEach fun setUp() = runBlocking {
        cdn.start(wait = false)
        proxy = startProxy()
    }

    @AfterEach fun tearDown() {
        proxy.stop()
        cdn.stop(0, 0)
    }

    private fun startProxy(stallTimeoutMs: Long = 400): TestProxy = runBlocking {
        val cdnDns = Dns { host -> if (host == "cdn.test") listOf(InetAddress.getLoopbackAddress()) else Dns.SYSTEM.lookup(host) }
        TestProxy(OkHttpClient.Builder().dns(cdnDns).build(), stallTimeoutMs).also {
            it.cdnPort = cdn.engine.resolvedConnectors().first().port
            assertTrue(it.ensureReady(5_000))
        }
    }

    private fun request(range: String, via: TestProxy = proxy) =
        player.newCall(Request.Builder().url(via.getProxyUrl(ID)).header("Range", range).build())

    @Test fun `bytes reach the player as they arrive, not in 1 MiB bursts`() {
        stallAfter = { index -> if (index == 0) 200_000 else null }
        val patient = startProxy(stallTimeoutMs = 10_000)
        try {
            val started = System.nanoTime()
            request("bytes=0-", patient).execute().use { response ->
                val input = response.body.byteStream()
                val buffer = ByteArray(8192)
                var read = 0
                while (read < 200_000) read += input.read(buffer, 0, minOf(buffer.size, 200_000 - read)).also { check(it > 0) }
            }
            val tookMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(tookMs < 1_500, "bytes held back for $tookMs ms")
        } finally {
            patient.stop()
        }
    }

    @Test fun `a stalled connection is replaced from the same byte without the player noticing`() {
        stallAfter = { index -> if (index == 0) 100_000 else null }
        val started = System.nanoTime()
        val body = request("bytes=0-").execute().use { it.body.bytes() }
        val tookMs = (System.nanoTime() - started) / 1_000_000

        assertArrayEquals(data, body)
        assertEquals("100000-2999999", upstreamRanges[1])
        assertTrue(tookMs < STALL_MS, "waited out the stall: $tookMs ms")
    }

    @Test fun `a response the player has left is not resumed`() {
        stallAfter = { index -> if (index == 0) 65_536 else null }
        val first = request("bytes=0-")
        first.execute().use { response ->
            val input = response.body.byteStream()
            var read = 0
            val buffer = ByteArray(8192)
            while (read < 65_536) read += input.read(buffer, 0, minOf(buffer.size, 65_536 - read)).also { check(it > 0) }
            // The player gives up on the silent connection and reopens where it stopped.
            first.cancel()
        }
        val rest = request("bytes=65536-").execute().use { it.body.bytes() }
        Thread.sleep(1_000) // Past the first response's stall timeout.

        assertArrayEquals(data.copyOfRange(65_536, size), rest)
        assertFalse("65536-2999999" in upstreamRanges, "abandoned response reconnected: $upstreamRanges")
    }

    @Test fun `a connection that never recovers ends the response instead of hanging`() {
        stallAfter = { index -> if (index == 0) 65_536 else 0 }
        val started = System.nanoTime()
        assertThrows(IOException::class.java) {
            request("bytes=0-").execute().use { it.body.bytes() }
        }
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(tookMs < 6_000, "took $tookMs ms")
        assertTrue(upstreamRanges.size in 2..5, "upstream requests: $upstreamRanges")
    }

    private companion object {
        const val ID = "abcdefghijk"
        const val STALL_MS = 3_000L
    }
}
