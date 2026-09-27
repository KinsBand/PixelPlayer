package com.theveloper.pixelplay.data.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class WaveformGeneratorTest {

    private fun unsigned(b: Byte): Int = b.toInt() and 0xFF

    @Test
    fun `ramp produces monotone buckets ending at 255`() {
        val samples = FloatArray(1000) { it / 999f }
        val waveform = WaveformGenerator.generate(samples, points = 10)

        assertEquals(10, waveform.size)
        var previous = 0
        for (b in waveform) {
            val v = unsigned(b)
            assertTrue(v in 0..255, "value out of byte range: $v")
            assertTrue(v >= previous, "ramp waveform should be non-decreasing")
            previous = v
        }
        assertEquals(255, unsigned(waveform.last()))
    }

    @Test
    fun `silence produces all zeros`() {
        val waveform = WaveformGenerator.generate(FloatArray(5000), points = 100)
        assertEquals(100, waveform.size)
        assertTrue(waveform.all { unsigned(it) == 0 })
    }

    @Test
    fun `empty input produces all zeros`() {
        val waveform = WaveformGenerator.generate(FloatArray(0), points = 100)
        assertEquals(100, waveform.size)
        assertTrue(waveform.all { unsigned(it) == 0 })
    }

    @Test
    fun `sine peaks respect amplitude quantization`() {
        val n = 22050
        val samples = FloatArray(n) { (sin(2.0 * PI * 440.0 * it / n) * 0.5).toFloat() }
        val waveform = WaveformGenerator.generate(samples, points = 1000)

        assertEquals(1000, waveform.size)
        val values = waveform.map(::unsigned)
        assertTrue(values.all { it in 0..128 }, "0.5-amplitude sine must not exceed 128")
        assertTrue(values.max() >= 120, "some bucket should capture the sine peak")
    }

    @Test
    fun `minMax packs two bytes per bucket`() {
        val samples = FloatArray(1000) { it / 999f }
        val waveform = WaveformGenerator.minMax(samples, points = 10)
        assertEquals(20, waveform.size)
    }
}
