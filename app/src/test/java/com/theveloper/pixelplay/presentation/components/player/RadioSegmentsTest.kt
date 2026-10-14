package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.ui.graphics.Color
import com.theveloper.pixelplay.data.radio.RadioHeardTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioSegmentsTest {

    private fun track(start: Long?, end: Long?, duration: Long = 0L, approximate: Boolean = false) = RadioHeardTrack(
        id = "$start", title = "t", artist = "a", stationUuid = null, stationName = null, heardAt = 0L,
        source = RadioHeardTrack.Source.STREAM, startPosMs = start, endPosMs = end,
        durationMs = duration, approximateStart = approximate,
    )

    private fun segments(vararg t: RadioHeardTrack, start: Long = 0, end: Long = 1_000, now: Long = 1_000) =
        radioTimelineSegments(t.toList(), start, end, now) { Color.Red }

    @Test
    fun `talk after a song isn't coloured once the song's length is known`() {
        // Title at 100, next title (talk) at 600, but the song itself is 300 long.
        val s = segments(track(100, 600, duration = 300)).single()
        assertEquals(0.1f, s.start, 0.0001f)
        assertEquals(0.4f, s.end, 0.0001f)
    }

    @Test
    fun `a song still on runs to now`() {
        val s = segments(track(200, null), now = 800).single()
        assertEquals(0.8f, s.end, 0.0001f)
    }

    @Test
    fun `listen-identified songs aren't cut by their length`() {
        val s = segments(track(100, 900, duration = 100, approximate = true)).single()
        assertEquals(0.9f, s.end, 0.0001f)
    }

    @Test
    fun `songs from before the kept window are dropped, partly kept ones are clamped`() {
        val list = segments(track(0, 100), track(150, 400), start = 200, end = 1_200)
        assertEquals(1, list.size)
        assertEquals(0f, list.single().start, 0.0001f)
        assertTrue(list.single().end > 0f)
    }

    @Test
    fun `tracks without a position aren't drawn`() {
        assertTrue(segments(track(null, null)).isEmpty())
    }
}
