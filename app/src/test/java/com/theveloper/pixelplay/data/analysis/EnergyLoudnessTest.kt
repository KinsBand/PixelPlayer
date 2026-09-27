package com.theveloper.pixelplay.data.analysis

import com.theveloper.pixelplay.data.analysis.dsp.EnergyLoudness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class EnergyLoudnessTest {

    private fun sine(amplitude: Double, samples: Int = 22050): FloatArray =
        FloatArray(samples) { (sin(2.0 * PI * 440.0 * it / 22050.0) * amplitude).toFloat() }

    @Test
    fun `silence hits the dBFS floor`() {
        assertEquals(EnergyLoudness.DB_FLOOR, EnergyLoudness.rmsDb(FloatArray(1000)))
        assertEquals(EnergyLoudness.DB_FLOOR, EnergyLoudness.rmsDb(FloatArray(0)))
    }

    @Test
    fun `full-scale sine measures about -3 dBFS`() {
        val db = EnergyLoudness.rmsDb(sine(1.0))
        assertEquals(-3.01f, db, 0.05f)
    }

    @Test
    fun `energy is monotone with amplitude`() {
        val loud = EnergyLoudness.energy(sine(0.5))
        val quiet = EnergyLoudness.energy(sine(0.25))
        val silent = EnergyLoudness.energy(FloatArray(22050))

        assertTrue(loud > quiet, "louder signal should have higher energy ($loud <= $quiet)")
        assertTrue(quiet > silent, "quiet signal should beat silence")
        assertEquals(0f, silent)
    }

    @Test
    fun `full-scale sine saturates energy at 1`() {
        assertEquals(1f, EnergyLoudness.energy(sine(1.0)))
    }
}
