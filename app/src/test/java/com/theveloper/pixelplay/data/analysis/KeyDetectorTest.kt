package com.theveloper.pixelplay.data.analysis

import com.theveloper.pixelplay.data.analysis.dsp.KeyDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class KeyDetectorTest {

    private val sampleRate = 22050

    /**
     * Sustained triad (root + third + fifth) with light harmonics, a gentle
     * raised-cosine amplitude envelope, and a fixed-seed noise floor for
     * determinism.
     */
    private fun triad(
        rootHz: Double,
        thirdHz: Double,
        fifthHz: Double,
        seconds: Double = 12.0
    ): FloatArray {
        val n = (seconds * sampleRate).toInt()
        val out = FloatArray(n)
        val rng = Random(123)
        val harmonics = listOf(1.0 to 1.0, 2.0 to 0.5, 3.0 to 0.25, 4.0 to 0.125)
        val freqs = listOf(rootHz, thirdHz, fifthHz)
        val fadeSeconds = 0.5

        for (i in 0 until n) {
            val t = i / sampleRate.toDouble()
            val attack = min(1.0, t / fadeSeconds)
            val release = min(1.0, (seconds - t) / fadeSeconds)
            val env = 0.5 - 0.5 * cos(PI * min(attack, release))
            var v = 0.0
            for (f in freqs) {
                for ((h, a) in harmonics) {
                    v += a * sin(2.0 * PI * f * h * t)
                }
            }
            out[i] = (v * env / 6.0 + (rng.nextDouble() - 0.5) * 0.002).toFloat()
        }
        return out
    }

    @Test
    fun `C major triad detected as C major (8B)`() {
        // C4 + E4 + G4
        val pcm = triad(261.63, 329.63, 392.00)
        val result = KeyDetector.detectKey(pcm, sampleRate)
        assertNotNull(result, "expected a key for a clear C major triad")
        assertEquals("C", result!!.key)
        assertEquals("8B", result.camelot)
    }

    @Test
    fun `A minor triad detected as A minor (8A)`() {
        // A3 + C4 + E4
        val pcm = triad(220.0, 261.63, 329.63)
        val result = KeyDetector.detectKey(pcm, sampleRate)
        assertNotNull(result, "expected a key for a clear A minor triad")
        assertEquals("Am", result!!.key)
        assertEquals("8A", result.camelot)
    }

    @Test
    fun `silence yields null`() {
        assertNull(KeyDetector.detectKey(FloatArray(sampleRate * 5), sampleRate))
    }

    @Test
    fun `too short input yields null`() {
        assertNull(KeyDetector.detectKey(FloatArray(1000), sampleRate))
    }
}
