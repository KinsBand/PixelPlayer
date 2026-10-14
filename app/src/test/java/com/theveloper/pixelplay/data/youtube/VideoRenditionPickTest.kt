package com.theveloper.pixelplay.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoRenditionPickTest {
    private fun r(h: Int) = VideoRendition(h, 30, "v$h", "video/mp4", "a")
    private val streams = VideoStreams("id", listOf(r(1080), r(720), r(480), r(360)))

    @Test fun `exact starred height is used`() =
        assertEquals(720, pickVideoRendition(streams, 720, unmetered = true)?.height)

    @Test fun `missing height falls to the nearest lower`() =
        assertEquals(480, pickVideoRendition(streams, 600, unmetered = true)?.height)

    @Test fun `below every height uses the lowest`() =
        assertEquals(360, pickVideoRendition(streams, 144, unmetered = true)?.height)

    @Test fun `auto depends on the network`() {
        assertEquals(1080, pickVideoRendition(streams, null, unmetered = true)?.height)
        assertEquals(480, pickVideoRendition(streams, null, unmetered = false)?.height)
    }
}
