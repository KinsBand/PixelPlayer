package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DirectPlayerRequestTest {
    private val requests = CopyOnWriteArrayList<Request>()
    /** Player answer per request: override to simulate bot checks, masks, delays. */
    private var playerAnswer: (Request) -> Response = { okPlayer(it) }
    private var visitorCount = 0
    private val releaseOtherRequests = CountDownLatch(1)
    private var original: Downloader? = null

    @BeforeEach fun installDownloader() {
        original = NewPipe.getDownloader()
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                requests.add(request)
                val url = request.url()
                return when {
                    url.contains("/visitor_id?") -> {
                        visitorCount++
                        json(request, """{"responseContext":{"visitorData":"visitor-$visitorCount-for-playback-tests"}}""")
                    }
                    url.contains("/player?") -> playerAnswer(request)
                    else -> {
                        // Full extraction (watch page etc.): hangs like a slow network.
                        releaseOtherRequests.await(5, TimeUnit.SECONDS)
                        Response(404, "Not Found", emptyMap(), "", url)
                    }
                }
            }
        })
    }

    @AfterEach fun restoreDownloader() {
        releaseOtherRequests.countDown()
        original?.let { NewPipe.init(it) }
    }

    @Test fun `cold audio lookup fetches visitor data from the player host then a masked player request`() = runBlocking {
        val stream = InnerTubeClient(OkHttpClient()).directStreams("abcdefghijk").single()

        assertEquals(2, requests.size)
        val visitor = requests.first().url().toHttpUrl()
        val player = requests.last()
        assertEquals("/youtubei/v1/visitor_id", visitor.encodedPath)
        // Same host as the player request, so fetching visitor data opens its connection.
        assertEquals(player.url().toHttpUrl().host, visitor.host)
        assertTrue(player.url().contains("/player?"))
        val payload = JSONObject(String(player.dataToSend()!!, Charsets.UTF_8))
        val client = payload.getJSONObject("context").getJSONObject("client")
        assertEquals("VISIONOS", client.getString("clientName"))
        assertEquals("visitor-1-for-playback-tests", client.getString("visitorData"))
        assertEquals("abcdefghijk", payload.getString("videoId"))
        assertTrue(payload.getBoolean("contentCheckOk") && payload.getBoolean("racyCheckOk"))
        assertEquals(payload.getString("cpn"), stream.url.toHttpUrl().queryParameter("cpn"))
        assertEquals(player.headers()["User-Agent"]?.first(), YouTubeHttp.userAgentFor(stream.url))
        assertEquals(VisionOsPlayer.FIELD_MASK, player.headers()["X-Goog-FieldMask"]?.single())
        assertEquals(1024, stream.contentLength)
        assertTrue(stream.expiresAt > System.currentTimeMillis())
    }

    @Test fun `later lookups reuse visitor data and send only the player request`() = runBlocking {
        val client = InnerTubeClient(OkHttpClient())
        client.directStreams("abcdefghijk")
        requests.clear()

        assertEquals(1, client.directStreams("bcdefghijkl").size)

        assertEquals(1, requests.size)
        assertEquals("visitor-1-for-playback-tests", visitorIn(requests.single()))
    }

    @Test fun `visitor data kept on disk serves the first lookup after a restart`() = runBlocking {
        val store = MemoryStore()
        InnerTubeClient(OkHttpClient(), store).directStreams("abcdefghijk")
        requests.clear()

        // A new process: nothing in memory, only the store.
        InnerTubeClient(OkHttpClient(), store).directStreams("bcdefghijkl")

        assertEquals(listOf("/youtubei/v1/player"), requests.map { it.url().toHttpUrl().encodedPath })
        assertEquals("visitor-1-for-playback-tests", visitorIn(requests.single()))
    }

    @Test fun `a sign-in check renews remembered visitor data once`() = runBlocking {
        val store = MemoryStore().apply { saveVisitor("retired-visitor-data-value", System.currentTimeMillis()) }
        playerAnswer = { request ->
            if (visitorIn(request) == "retired-visitor-data-value") {
                json(request, """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm that you're not a bot"},"videoDetails":{"videoId":"abcdefghijk"}}""")
            } else okPlayer(request)
        }

        val streams = InnerTubeClient(OkHttpClient(), store).directStreams("abcdefghijk")

        assertEquals(1, streams.size)
        assertEquals(listOf("/player", "/visitor_id", "/player"), requests.map { it.url().toHttpUrl().encodedPath.substringAfterLast("/v1") })
        assertEquals("visitor-1-for-playback-tests", store.loadVisitor()?.first)
    }

    @Test fun `a sign-in check with freshly fetched visitor data is not retried`() = runBlocking {
        playerAnswer = { request ->
            json(request, """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm your age"},"videoDetails":{"videoId":"abcdefghijk"}}""")
        }

        assertTrue(InnerTubeClient(OkHttpClient()).directStreams("abcdefghijk").isEmpty())

        assertEquals(2, requests.size) // visitor + one player request; extraction takes over from here
    }

    @Test fun `a rejected field mask falls back to full responses for the session`() = runBlocking {
        playerAnswer = { request ->
            if (request.headers().containsKey("X-Goog-FieldMask")) {
                json(request, """{"error":{"code":400,"message":"Request contains an invalid argument.","status":"INVALID_ARGUMENT","details":[{"fieldViolations":[{"field":"mask"}]}]}}""", 400)
            } else okPlayer(request)
        }
        val client = InnerTubeClient(OkHttpClient())

        assertEquals(1, client.directStreams("abcdefghijk").size)
        requests.clear()
        assertEquals(1, client.directStreams("bcdefghijkl").size)

        assertEquals(1, requests.size)
        assertFalse(requests.single().headers().containsKey("X-Goog-FieldMask"))
    }

    @Test fun `a request that also fails without the mask keeps the mask`() = runBlocking {
        playerAnswer = { request -> json(request, """{"error":{"code":400,"message":"bad video id, padded to a realistic length"}}""", 400) }
        val client = InnerTubeClient(OkHttpClient())

        assertTrue(client.directStreams("abcdefghijk").isEmpty())
        playerAnswer = { okPlayer(it) }
        requests.clear()
        client.directStreams("bcdefghijkl")

        assertEquals(VisionOsPlayer.FIELD_MASK, requests.single().headers()["X-Goog-FieldMask"]?.single())
    }

    @Test fun `visitor data older than its lifetime is renewed`() = runBlocking {
        val issued = System.currentTimeMillis() - VisionOsPlayer.VISITOR_MAX_AGE_MS - 1
        val store = MemoryStore().apply { saveVisitor("old-visitor-data-value-xx", issued) }

        InnerTubeClient(OkHttpClient(), store).directStreams("abcdefghijk")

        assertEquals(listOf("/visitor_id", "/player"), requests.map { it.url().toHttpUrl().encodedPath.substringAfterLast("/v1") })
        assertEquals("visitor-1-for-playback-tests", visitorIn(requests.last()))
    }

    @Test fun `warm up contacts the player host once while its connection stays pooled`() = runBlocking {
        val client = InnerTubeClient(OkHttpClient())

        client.warmUpPlayback()
        client.warmUpPlayback()
        assertEquals(1, requests.size)
        // The first tap then needs only the player request.
        client.directStreams("abcdefghijk")
        assertEquals(listOf("/visitor_id", "/player"), requests.map { it.url().toHttpUrl().encodedPath.substringAfterLast("/v1") })
    }

    @Test fun `manifest arriving after the hedge is kept while full extraction is abandoned`() = runBlocking {
        // Direct takes longer than the 200 ms hedge, so NewPipe's full extraction has started
        // (and hangs here). The direct manifest must still be returned and cached.
        playerAnswer = { request -> Thread.sleep(400); okPlayer(request) }
        val extractor = YouTubeStreamExtractor(InnerTubeClient(OkHttpClient()))

        val streams = extractor.streamManifest("abcdefghijk")

        assertEquals(1, streams.size)
        // More than the direct path's two requests: NewPipe's full extraction was under way.
        assertTrue(requests.size > 2, "full extraction should have started: " + requests.map { it.url().substringBefore("?") })
        val playerRequests = requests.count { it.url().contains("/player?") }
        assertEquals(streams, extractor.streamManifest("abcdefghijk"))
        assertEquals(playerRequests, requests.count { it.url().contains("/player?") })
    }

    @Test fun `a network change drops IP-bound manifests and cooldowns earned on the old network`() = runBlocking {
        val extractor = YouTubeStreamExtractor(InnerTubeClient(OkHttpClient()))
        extractor.streamManifest("abcdefghijk")
        extractor.invalidate("bcdefghijkl") // as after a 403: full extraction for a while
        requests.clear()

        extractor.onNetworkChanged()
        assertEquals(1, extractor.streamManifest("abcdefghijk").size)
        assertEquals(1, extractor.streamManifest("bcdefghijkl").size)

        // Both were resolved again, directly: one player request each and no full extraction.
        assertEquals(listOf("/player", "/player"), requests.map { it.url().toHttpUrl().encodedPath.substringAfterLast("/v1") })
    }

    @Test fun `a manifest resolved across a network change is used but not cached`() = runBlocking {
        val extractor = YouTubeStreamExtractor(InnerTubeClient(OkHttpClient()))
        playerAnswer = { request -> extractor.onNetworkChanged(); okPlayer(request) }
        assertEquals(1, extractor.streamManifest("abcdefghijk").size)

        playerAnswer = { okPlayer(it) }
        requests.clear()
        extractor.streamManifest("abcdefghijk")
        assertEquals(1, requests.count { it.url().contains("/player?") })
    }

    private class MemoryStore : InnerTubeVersionStore(null) {
        private var visitor: Pair<String, Long>? = null
        override fun loadVisitor() = visitor
        override fun saveVisitor(visitorData: String, now: Long) { visitor = visitorData to now }
        override fun clearVisitor() { visitor = null }
    }

    private companion object {
        fun visitorIn(request: Request): String = JSONObject(String(request.dataToSend()!!, Charsets.UTF_8))
            .getJSONObject("context").getJSONObject("client").getString("visitorData")

        fun json(request: Request, body: String, code: Int = 200) =
            Response(code, "", mapOf("Content-Type" to listOf("application/json")), body, request.url())

        fun okPlayer(request: Request): Response {
            val id = JSONObject(String(request.dataToSend()!!, Charsets.UTF_8)).getString("videoId")
            val expiry = System.currentTimeMillis() / 1000 + 3600
            return json(request, """{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"$id"},
                "streamingData":{"adaptiveFormats":[{"url":"https://r1.googlevideo.com/videoplayback?expire=$expiry&c=VISIONOS&itag=140&clen=1024",
                "mimeType":"audio/mp4","contentLength":"1024","bitrate":128000}]}}""")
        }
    }
}
