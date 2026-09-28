package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The direct-client ladder: primary visionOS, backup visionOS 0.1 on YouTube Music, then extraction. */
class ClientLadderTest {
    private val requests = CopyOnWriteArrayList<Request>()
    private var primaryAnswer: (Request) -> Response = { okPlayer(it, "VISIONOS") }
    private var musicAnswer: (Request) -> Response = { okPlayer(it, "VISIONOS", cver = "0.1") }
    private val releaseOtherRequests = CountDownLatch(1)
    private var original: Downloader? = null

    @BeforeEach fun installDownloader() {
        original = NewPipe.getDownloader()
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                requests.add(request)
                val url = request.url()
                return when {
                    url.contains("/visitor_id?") ->
                        json(request, """{"responseContext":{"visitorData":"visitor-for-ladder-tests-000"}}""")
                    url.startsWith(VisionOsMusicPlayer.URL) -> musicAnswer(request)
                    url.contains("/player?") -> primaryAnswer(request)
                    else -> {
                        // Full extraction: hangs like a slow network.
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

    private fun musicRequests() = requests.filter { it.url().startsWith(VisionOsMusicPlayer.URL) }

    @Test fun `the backup client answers when the primary has nothing`() = runBlocking {
        primaryAnswer = { json(it, """{"playabilityStatus":{"status":"UNPLAYABLE"},"videoDetails":{"videoId":"abcdefghijk"}}""") }
        val extractor = YouTubeStreamExtractor(InnerTubeClient(OkHttpClient()))

        val streams = extractor.streamManifest("abcdefghijk")

        assertEquals(1, streams.size)
        assertEquals(StreamClients.VISIONOS_MUSIC, streams.single().client)
        val music = musicRequests().single()
        val payload = JSONObject(String(music.dataToSend()!!, Charsets.UTF_8))
        val client = payload.getJSONObject("context").getJSONObject("client")
        assertEquals("VISIONOS", client.getString("clientName"))
        assertEquals("0.1", client.getString("clientVersion"))
        assertEquals("visitor-for-ladder-tests-000", client.getString("visitorData"))
        assertEquals(VisionOsMusicPlayer.USER_AGENT, music.headers()["User-Agent"]?.single())
        assertEquals("https://music.youtube.com", music.headers()["Origin"]?.single())
        // Its URLs must be fetched with the same identity.
        assertEquals(VisionOsMusicPlayer.USER_AGENT, YouTubeHttp.userAgentFor(streams.single().url))
        assertEquals(payload.optString("cpn", ""), "") // the nonce goes on the URL, not the body
        assertTrue(streams.single().url.toHttpUrl().queryParameter("cpn")!!.isNotBlank())
    }

    @Test fun `a refused URL moves that song to the other direct client, not to extraction`() = runBlocking {
        val extractor = YouTubeStreamExtractor(InnerTubeClient(OkHttpClient()))
        assertEquals(StreamClients.VISIONOS, extractor.streamManifest("abcdefghijk").single().client)
        requests.clear()

        extractor.invalidate("abcdefghijk") // as after an HTTP 403 on its URL
        val streams = extractor.streamManifest("abcdefghijk")

        assertEquals(StreamClients.VISIONOS_MUSIC, streams.single().client)
        assertEquals(listOf(VisionOsMusicPlayer.URL), requests.map { it.url() })
        // Other songs still use the primary.
        assertEquals(StreamClients.VISIONOS, extractor.streamManifest("bcdefghijkl").single().client)
    }

    @Test fun `a stall is blamed on the client that produced the URL`() = runBlocking {
        val extractor = YouTubeStreamExtractor(InnerTubeClient(OkHttpClient()))
        extractor.streamManifest("abcdefghijk")
        extractor.reportStall("abcdefghijk")
        assertEquals(setOf(StreamClients.VISIONOS), extractor.health.excludedFor("abcdefghijk"))
        assertEquals(StreamClients.VISIONOS_MUSIC, extractor.streamManifest("abcdefghijk").single().client)
    }

    @Test fun `a primary failing repeatedly is tried after the backup`() = runBlocking {
        primaryAnswer = { json(it, """{"error":{"code":500}}""", 500) }
        val extractor = YouTubeStreamExtractor(InnerTubeClient(OkHttpClient()))
        extractor.streamManifest("aaaaaaaaaaa")
        // One failure (maybe about that song only) doesn't change the order.
        assertEquals(StreamClients.DIRECT, extractor.health.order(StreamClients.DIRECT))
        extractor.streamManifest("bbbbbbbbbbb")
        assertEquals(StreamClients.DIRECT.reversed(), extractor.health.order(StreamClients.DIRECT))
        requests.clear()

        assertEquals(StreamClients.VISIONOS_MUSIC, extractor.streamManifest("ddddddddddd").single().client)
        assertEquals(VisionOsMusicPlayer.URL, requests.first().url(), "backup asked first")
    }

    @Test fun `the backup's non-OK status with real formats is accepted but a preview is not`() {
        val formats = """"streamingData":{"adaptiveFormats":[{"url":"https://r1.googlevideo.com/videoplayback?expire=9999999999&c=VISIONOS&itag=251&clen=1024",
            "mimeType":"audio/webm","contentLength":"1024","bitrate":160000,"approxDurationMs":"%d"}]}"""
        fun root(status: String, durationMs: Long) = JSONObject("""{"playabilityStatus":{"status":"$status"},
            "videoDetails":{"videoId":"abcdefghijk","lengthSeconds":"200"},${formats.format(durationMs)}}""")

        assertTrue(InnerTubeParser.isUsableMusicClientResponse(root("LOGIN_REQUIRED", 199_000), "abcdefghijk"))
        assertFalse(InnerTubeParser.isUsableMusicClientResponse(root("OK", 30_000), "abcdefghijk"))
        assertFalse(InnerTubeParser.isUsableMusicClientResponse(root("OK", 199_000), "bcdefghijkl"))
        assertFalse(InnerTubeParser.isUsableMusicClientResponse(
            JSONObject("""{"playabilityStatus":{"status":"ERROR"}}"""), "abcdefghijk"))
    }

    private companion object {
        fun json(request: Request, body: String, code: Int = 200) =
            Response(code, "", mapOf("Content-Type" to listOf("application/json")), body, request.url())

        fun okPlayer(request: Request, client: String, cver: String? = null): Response {
            val id = JSONObject(String(request.dataToSend()!!, Charsets.UTF_8)).getString("videoId")
            val expiry = System.currentTimeMillis() / 1000 + 3600
            val version = cver?.let { "&cver=$it" }.orEmpty()
            return json(request, """{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"$id"},
                "streamingData":{"adaptiveFormats":[{"url":"https://r1.googlevideo.com/videoplayback?expire=$expiry&c=$client$version&itag=251&clen=2048&id=$id",
                "mimeType":"audio/webm","contentLength":"2048","bitrate":160000}]}}""")
        }
    }
}
