package com.theveloper.pixelplay.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Guards the playlist download menu's per-quality counts for songs downloaded before qualities were recorded. */
class DownloadQualityClassifyTest {
    @Test fun `opus webm is high`() {
        assertEquals(DownloadQuality.HIGH, classifyDownloadQuality("webm", 160))
        assertEquals(DownloadQuality.HIGH, classifyDownloadQuality("audio/webm; codecs=\"opus\" webm", null))
    }

    @Test fun `aac m4a is medium`() {
        assertEquals(DownloadQuality.MEDIUM, classifyDownloadQuality("m4a", 128))
        assertEquals(DownloadQuality.MEDIUM, classifyDownloadQuality("audio/mp4 mp4", 130))
    }

    @Test fun `anything under 90 kbps is low whatever the container`() {
        assertEquals(DownloadQuality.LOW, classifyDownloadQuality("webm", 50))
        assertEquals(DownloadQuality.LOW, classifyDownloadQuality("m4a", 48))
    }

    @Test fun `unknown container is unknown`() {
        assertNull(classifyDownloadQuality("flac", 900))
        assertNull(classifyDownloadQuality(null, null))
    }
}
