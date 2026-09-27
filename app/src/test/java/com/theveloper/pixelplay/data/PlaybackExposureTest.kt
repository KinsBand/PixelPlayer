package com.theveloper.pixelplay.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaybackExposureTest {
    @Test fun `seeking to final second cannot count as completion`() {
        val tracker = PlaybackExposure(0, 0, true)
        tracker.discontinuity(1000, 199000, 1000, true)
        tracker.sample(200000, 2000, false)
        assertEquals(2000, tracker.uniqueMs)
        assertEquals(0.01, tracker.coverage(200000), 0.0001)
    }
    @Test fun `pauses buffering and stalled playheads contribute nothing`() {
        val tracker = PlaybackExposure(0, 0, true)
        tracker.sample(5000, 5000, false)
        tracker.sample(5000, 65000, true)
        tracker.sample(5000, 70000, true)
        tracker.sample(10000, 75000, false)
        assertEquals(10000, tracker.activeMs)
        assertEquals(10000, tracker.uniqueMs)
    }
    @Test fun `replayed overlapping intervals count once toward coverage`() {
        val tracker = PlaybackExposure(0, 0, true)
        tracker.discontinuity(10000, 5000, 10000, true)
        tracker.sample(15000, 20000, true)
        assertEquals(15000, tracker.uniqueMs)
        assertEquals(5000, tracker.repeatedMs)
        assertEquals(20000, tracker.activeMs)
    }
    @Test fun `speed separates media and wall time`() {
        val tracker = PlaybackExposure(0, 0, true)
        tracker.sample(20000, 10000, false, 2f)
        assertEquals(20000, tracker.uniqueMs)
        assertEquals(10000, tracker.activeMs)
    }
    @Test fun `unreported seek and backward clock are censored`() {
        val tracker = PlaybackExposure(0, 1000, true)
        tracker.sample(180000, 2000, true)
        tracker.sample(181000, 1000, false)
        assertEquals(0, tracker.uniqueMs)
        assertEquals(0, tracker.activeMs)
    }
    @Test fun `a bridging interval merges all earlier sections`() {
        val tracker = PlaybackExposure(0, 0, true)
        tracker.discontinuity(10000, 20000, 10000, true)
        tracker.discontinuity(30000, 5000, 20000, true)
        tracker.sample(25000, 40000, false)
        assertEquals(30000, tracker.uniqueMs)
        assertEquals(10000, tracker.repeatedMs)
    }
}
