package com.theveloper.pixelplay.data.radio.timeshift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioTimeShiftIndexTest {

    @Test
    fun `128 kbps is 16 bytes per millisecond`() {
        val index = RadioTimeShiftIndex(bitrateKbps = 128, maxDurationMs = 60_000)
        index.onWritten(16_000)
        assertEquals(1_000L, index.liveEdgeMs)
    }

    @Test
    fun `the oldest whole segments are dropped once past the window`() {
        // 8 kbps floor is 16 → 2 bytes/ms; one-minute segments are 120 000 bytes (min 64 KiB).
        val index = RadioTimeShiftIndex(bitrateKbps = 16, maxDurationMs = 120_000, segmentDurationMs = 60_000)
        val segment = index.segmentBytes
        val dropped = ArrayList<Long>()
        repeat(4) { dropped += index.onWritten(segment.toInt()) }
        // 4 minutes written, 2 kept: segments 0 and 1 go (the one holding the cut stays).
        assertEquals(listOf(0L, 1L), dropped)
        assertEquals(index.segmentStart(2), index.firstAvailable)
    }

    @Test
    fun `a start point is clamped into what is kept`() {
        val index = RadioTimeShiftIndex(bitrateKbps = 128, maxDurationMs = 60_000)
        index.onWritten(16 * 30_000) // 30 s
        assertEquals(0L, index.startOffsetFor(-5_000))
        assertEquals(index.written, index.startOffsetFor(10 * 60_000))
        assertEquals(index.offsetAt(12_000), index.startOffsetFor(12_000))
    }

    @Test
    fun `live starts a few seconds back from the edge`() {
        val index = RadioTimeShiftIndex(bitrateKbps = 128, maxDurationMs = 60_000)
        index.onWritten(16 * 30_000)
        val live = index.startOffsetFor(null)
        assertEquals(index.offsetAt(30_000 - RadioTimeShiftIndex.DEFAULT_LIVE_LEAD_MS), live)
    }

    @Test
    fun `the bitrate header only counts before audio arrives`() {
        val index = RadioTimeShiftIndex(bitrateKbps = 0, maxDurationMs = 60_000)
        index.setBitrateIfEmpty(64)
        assertEquals(8.0, index.bytesPerMs)
        index.onWritten(100)
        index.setBitrateIfEmpty(320)
        assertEquals(8.0, index.bytesPerMs)
        assertTrue(index.segmentBytes >= 64 * 1024)
    }

    @Test
    fun `ICY titles are read from the metadata block`() {
        val block = "StreamTitle='Daft Punk - One More Time';StreamUrl='';".toByteArray() + ByteArray(6)
        assertEquals("Daft Punk - One More Time", RadioTimeShiftRecorder.parseStreamTitle(block))
        assertEquals("", RadioTimeShiftRecorder.parseStreamTitle("StreamTitle='';".toByteArray()))
        assertEquals("Guns N' Roses - Patience", RadioTimeShiftRecorder.parseStreamTitle("StreamTitle='Guns N' Roses - Patience';".toByteArray()))
        assertEquals(null, RadioTimeShiftRecorder.parseStreamTitle("StreamUrl='x';".toByteArray()))
    }
}
