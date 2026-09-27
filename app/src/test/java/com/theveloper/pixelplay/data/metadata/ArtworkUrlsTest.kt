package com.theveloper.pixelplay.data.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtworkUrlsTest {
    @Test fun `list and grid requests share size buckets and preserve provider flags`() {
        val google = "https://lh3.googleusercontent.com/cover=w1400-h1400-l90-rj"
        assertEquals("https://lh3.googleusercontent.com/cover=w256-h256-l90-rj", ArtworkUrls.forDisplay(google, 96, 128))
        assertEquals("https://lh3.googleusercontent.com/cover=w512-h512-l90-rj", ArtworkUrls.forDisplay(google, 300, 300))
        assertEquals("https://is1-ssl.mzstatic.com/image/256x256bb.jpg", ArtworkUrls.forDisplay("https://is1-ssl.mzstatic.com/image/1400x1400bb.jpg", 128, 128))
        assertEquals("https://e-cdns-images.dzcdn.net/images/cover/id/512x512-000000-80-0-0.jpg", ArtworkUrls.forDisplay("https://e-cdns-images.dzcdn.net/images/cover/id/1000x1000-000000-80-0-0.jpg", 300, 300))
    }

    @Test fun `original large signed local unknown and small sources are preserved`() {
        val original = "https://lh3.googleusercontent.com/cover=w1400-h1400-l90-rj"
        assertEquals(original, ArtworkUrls.forDisplay(original, 2048, 2048))
        assertEquals(original, ArtworkUrls.forDisplay(original, 0, 128))
        listOf(
            "$original?signature=secret",
            "$original#fragment",
            "content://media/external/audio/albumart/1",
            "pixelplay_local_art://song/1",
            "https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg",
            "https://mzstatic.com.evil.test/1400x1400bb.jpg",
            "https://example.com/mzstatic.com/1400x1400bb.jpg",
            "https://is1-ssl.mzstatic.com/image/100x100bb.jpg",
            "not a valid URI"
        ).forEach { assertEquals(it, ArtworkUrls.forDisplay(it, 128, 128)) }
    }
}
