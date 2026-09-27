package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class DirectPlayerRequestTest {
    @Test fun `cold audio lookup only requests visitor and native player with matching nonce`() = runBlocking {
        val requests = mutableListOf<Request>()
        val original = NewPipe.getDownloader()
        val downloader = object : Downloader() {
            override fun execute(request: Request): Response {
                requests.add(request)
                val body = if (request.url().contains("/visitor_id?")) {
                    """{"responseContext":{"visitorData":"test-visitor-data-for-playback"}}"""
                } else {
                    val expiry = System.currentTimeMillis() / 1000 + 3600
                    """{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk"},
                        "streamingData":{"adaptiveFormats":[{"url":"https://r1.googlevideo.com/videoplayback?expire=$expiry&c=VISIONOS&itag=140&clen=1024",
                        "mimeType":"audio/mp4","contentLength":"1024","bitrate":128000}]}}"""
                }
                return Response(200, "OK", mapOf("Content-Type" to listOf("application/json")), body, request.url())
            }
        }
        NewPipe.init(downloader)
        try {
            val stream = InnerTubeClient(OkHttpClient()).directStreams("abcdefghijk").single()
            assertEquals(2, requests.size)
            assertTrue(requests.first().url().contains("/visitor_id?"))
            val player = requests.last()
            assertTrue(player.url().contains("/player?"))
            val payload = JSONObject(String(player.dataToSend()!!, Charsets.UTF_8))
            assertEquals("VISIONOS", payload.getJSONObject("context").getJSONObject("client").getString("clientName"))
            assertEquals(payload.getString("cpn"), stream.url.toHttpUrl().queryParameter("cpn"))
            assertEquals(player.headers()["User-Agent"]?.first(), YouTubeHttp.userAgentFor(stream.url))
            assertEquals(1024, stream.contentLength)
            assertTrue(stream.expiresAt > System.currentTimeMillis())
        } finally {
            if (original != null) NewPipe.init(original)
        }
    }
}
