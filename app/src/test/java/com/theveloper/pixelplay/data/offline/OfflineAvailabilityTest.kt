package com.theveloper.pixelplay.data.offline

import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfflineAvailabilityTest {
    private val local = Song.emptySong().copy(id = "1", contentUriString = "content://media/1")
    private val online = Song.emptySong().copy(id = "yt_abc", youtubeId = "abc", contentUriString = "youtube://abc")

    @Test fun `local songs always play`() = assertTrue(local.isPlayableOffline(emptySet()))

    @Test fun `online songs need a download or a full cache`() {
        assertFalse(online.isPlayableOffline(emptySet()))
        assertTrue(online.isPlayableOffline(setOf("yt_abc")))
        assertTrue(online.isPlayableOffline(setOf("abc")))
    }
}
