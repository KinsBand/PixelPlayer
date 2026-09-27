package com.theveloper.pixelplay.data.youtube

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class YouTubeAudioStreamTest {
    @Test
    fun `quality selection uses bits per second and available renditions`() {
        val streams = listOf(44_000, 128_000, 160_000).map {
            YouTubeAudioStream("https://example.com/audio", "audio/webm", it, Long.MAX_VALUE)
        }
        assertEquals(160_000, YouTubeAudioStream.select(streams, AudioQualityPreset.HIGH)?.bitrate)
        assertEquals(160_000, YouTubeAudioStream.select(streams, AudioQualityPreset.AUTO)?.bitrate)
        assertEquals(128_000, YouTubeAudioStream.select(streams, AudioQualityPreset.NORMAL)?.bitrate)
        assertEquals(44_000, YouTubeAudioStream.select(streams, AudioQualityPreset.DATA_SAVER)?.bitrate)
        assertNull(YouTubeAudioStream.select(emptyList(), AudioQualityPreset.AUTO))
    }

    @Test
    fun `download extension follows the source container`() {
        assertEquals("webm", YouTubeAudioStream("", "audio/webm", 160_000, 0).fileExtension)
        assertEquals("m4a", YouTubeAudioStream("", "audio/mp4", 128_000, 0).fileExtension)
    }

    @Test
    fun `advertised expiry is honoured with refresh margin`() {
        assertEquals(1_940_000, YouTubeAudioStream.expiry("https://example.com/a?expire=2000", 1_000_000))
        assertTrue(YouTubeAudioStream.expiry("https://example.com/a?expire=1000", 1_000_000) < 1_000_000)
    }

    @Test
    fun `missing or malformed expiry gets a short bounded lifetime`() {
        val now = 1_000_000L
        for (query in listOf("", "?expire=invalid", "?expire=9223372036854775807")) {
            assertEquals(now + 14 * 60_000, YouTubeAudioStream.expiry("https://example.com/a$query", now))
        }
    }
}
