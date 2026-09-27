package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.stream.StreamRetryPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

class StreamReliabilityTest {
    private fun stream(itag: Int, type: String, bitrate: Int) =
        YouTubeAudioStream("https://cdn.googlevideo.com/audio?itag=$itag", type, bitrate, Long.MAX_VALUE)

    @Test fun `byte range refresh retains rendition when a better one appears`() {
        val original = stream(140, "audio/mp4", 128000)
        val better = stream(251, "audio/webm", 160000)
        assertEquals(original, selectRendition(listOf(better, original), original.renditionKey()))
        assertNull(selectRendition(listOf(better), original.renditionKey()))
    }

    @Test fun `codec fallback only selects actual compatible audio`() {
        val aac = stream(140, "audio/mp4", 128000)
        val opus = stream(251, "audio/webm", 160000)
        assertEquals(opus, selectRendition(listOf(aac, opus), null))
        assertEquals(aac, selectRendition(listOf(aac, opus), null, mp4Only = true))
        assertNull(selectRendition(listOf(opus), null, mp4Only = true))
    }

    @Test fun `only transient statuses and expiry are retried`() {
        listOf(401, 403, 408, 429, 500, 503).forEach { assertTrue(StreamRetryPolicy.retryStatus(it)) }
        listOf(200, 206, 400, 404, 416).forEach { assertFalse(StreamRetryPolicy.retryStatus(it)) }
        assertEquals(300L, StreamRetryPolicy.delayMs(0))
        assertEquals(600L, StreamRetryPolicy.delayMs(1))
    }

    @Test fun `empty and truncated downloads cannot be published`() {
        assertThrows(IOException::class.java) { DownloadIntegrity.requireComplete(0, -1) }
        assertThrows(IOException::class.java) { DownloadIntegrity.requireComplete(99, 100) }
        assertThrows(IOException::class.java) { DownloadIntegrity.requireComplete(101, 100) }
        assertDoesNotThrow { DownloadIntegrity.requireComplete(100, 100) }
        assertDoesNotThrow { DownloadIntegrity.requireComplete(100, -1) }
    }
}
