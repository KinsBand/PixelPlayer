package com.theveloper.pixelplay.data.spotify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards TrackMatching after its patterns were made compile-once (they were rebuilt on every call,
 * which exhausted native memory in blends: U_MEMORY_ALLOCATION_ERROR). Same results as before.
 */
class TrackMatchingTest {
    @Test fun `normalize strips brackets, suffixes, featuring and punctuation`() {
        assertEquals("song title", TrackMatching.normalize("Song Title (Remastered 2011) - Live"))
        assertEquals("song", TrackMatching.normalize("Song feat. Someone"))
        assertEquals("hello world", TrackMatching.normalize("  Hello,   World!! [Official] "))
    }

    @Test fun `accents are removed, plain ascii is unchanged`() {
        assertEquals("Beyonce", TrackMatching.stripAccents("Beyoncé"))
        assertEquals("plain", TrackMatching.stripAccents("plain"))
    }

    @Test fun `artists overlap on shared names`() {
        assertTrue(TrackMatching.artistsOverlap("Dua Lipa feat. DaBaby", "Dua Lipa"))
        assertFalse(TrackMatching.artistsOverlap("Lorde", "Adele"))
    }

    @Test fun `many calls in a row stay fine`() {
        repeat(20_000) { TrackMatching.normalize("Track $it (Live) - Remix") }
    }
}
