package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.youtube.resolver.ResolverOrchestrator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResolverOrchestratorTest {
    private val extractor = mockk<YouTubeStreamExtractor>(relaxed = true)
    private val dao = mockk<CloudSongDao> {
        coEvery { getById(any()) } returns null
    }

    @Test
    fun `playback keeps manifest metadata and reuses valid resolution`() = runTest {
        val expiry = System.currentTimeMillis() + 60_000
        coEvery { extractor.getStream(any(), any(), any()) } returns
            YouTubeAudioStream("https://example.com/audio", "audio/webm", 160_000, expiry)
        val resolver = ResolverOrchestrator(extractor, dao)
        val first = resolver.resolveVideoId("yt_abcdefghijk")
        assertEquals("audio/webm", first?.mimeType)
        assertEquals(160, first?.bitrate)
        assertEquals(expiry, first?.expiresAt)
        assertEquals(first, resolver.resolveVideoId("abcdefghijk"))
        coVerify(exactly = 1) { extractor.getStream(any(), any(), any()) }
        resolver.invalidate("yt_abcdefghijk")
        verify { extractor.invalidate("yt_abcdefghijk") }
        resolver.resolveVideoId("abcdefghijk")
        coVerify(exactly = 2) { extractor.getStream(any(), any(), any()) }
    }

    @Test
    fun `expired resolution is fetched again`() = runTest {
        coEvery { extractor.getStream(any(), any(), any()) } returns
            YouTubeAudioStream("https://example.com/audio", "audio/mp4", 128_000, 1L)
        val resolver = ResolverOrchestrator(extractor, dao)
        resolver.resolveVideoId("abcdefghijk")
        resolver.resolveVideoId("abcdefghijk")
        coVerify(exactly = 2) { extractor.getStream(any(), any(), any()) }
    }
}
