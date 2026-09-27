package com.theveloper.pixelplay.data.connectivity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProximityTrackerTest {

    @Test
    fun `first sample sets the zone directly`() {
        val t = ProximityTracker()
        t.record("AA", null, -60, now = 0)
        assertEquals(ProximityZone.NEAR, t.readings.value["AA"]?.zone)
    }

    @Test
    fun `zone needs to be clearly crossed for two samples before it changes`() {
        val t = ProximityTracker(alpha = 1.0) // no smoothing, so we test hysteresis only
        t.record("AA", null, -65, now = 0)
        t.record("AA", null, -72, now = 100) // past -70 but not by 3 dB
        assertEquals(ProximityZone.NEAR, t.readings.value["AA"]!!.zone)
        t.record("AA", null, -76, now = 200) // clearly past, first time
        assertEquals(ProximityZone.NEAR, t.readings.value["AA"]!!.zone)
        t.record("AA", null, -76, now = 300) // second time
        assertEquals(ProximityZone.ROOM, t.readings.value["AA"]!!.zone)
    }

    @Test
    fun `single big spike is ignored`() {
        val t = ProximityTracker()
        repeat(5) { t.record("AA", null, -60, now = it * 100L) }
        t.record("AA", null, -95, now = 600)
        assertEquals(-60.0, t.readings.value["AA"]!!.smoothedRssi, 0.01)
    }

    @Test
    fun `silent devices go out of range`() {
        val t = ProximityTracker(staleAfterMs = 1_000)
        t.record("AA", "Pixel Buds", -55, now = 0)
        t.tick(now = 2_000)
        val r = t.readings.value["AA"]!!
        assertTrue(r.isStale)
        assertEquals("Out of range", r.label)
        assertEquals(0, r.bars)
    }

    @Test
    fun `readings are also keyed by name`() {
        val t = ProximityTracker()
        t.record("11:22", "Pixel Buds Pro", -50, now = 0)
        assertNotNull(t.readings.value["name:pixel buds pro"])
    }

    @Test
    fun `distance uses calibration when set`() {
        val t = ProximityTracker()
        t.setCalibration("AA", -50)
        t.record("AA", null, -50, now = 0)
        assertEquals(1.0, t.readings.value["AA"]!!.approxMeters, 0.01)
    }
}
