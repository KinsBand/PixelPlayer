package com.theveloper.pixelplay.data.analysis

import com.theveloper.pixelplay.data.analysis.dsp.BpmDetector
import com.theveloper.pixelplay.data.analysis.dsp.OnsetEnvelope
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class BpmDetectorTest {

    /** Envelope frame rate at the working sample rate: 22050 / 512 ≈ 43.07 Hz. */
    private val envRate = 22050f / OnsetEnvelope.HOP_SIZE

    /** Sparse impulse train (with short decay tails) at a known tempo. */
    private fun impulseTrain(bpm: Double, seconds: Int = 30): FloatArray {
        val n = (envRate * seconds).toInt()
        val env = FloatArray(n)
        val period = envRate * 60.0 / bpm
        var t = 0.0
        while (t < n - 2) {
            val i = t.roundToInt()
            env[i] = max(env[i], 1.0f)
            if (i - 1 >= 0) env[i - 1] = max(env[i - 1], 0.4f)
            if (i + 1 < n) env[i + 1] = max(env[i + 1], 0.4f)
            if (i + 2 < n) env[i + 2] = max(env[i + 2], 0.15f)
            t += period
        }
        return env
    }

    private fun assertBpm(expected: Int, detected: Int?, tolerance: Int) {
        assertTrue(detected != null, "expected ~$expected BPM but got null")
        assertTrue(
            abs(detected!! - expected) <= tolerance,
            "expected ~$expected BPM (+/-$tolerance) but got $detected"
        )
    }

    @Test
    fun `detects 100 bpm impulse train`() {
        assertBpm(100, BpmDetector.detectBpm(impulseTrain(100.0), envRate), 2)
    }

    @Test
    fun `detects 128 bpm impulse train`() {
        assertBpm(128, BpmDetector.detectBpm(impulseTrain(128.0), envRate), 2)
    }

    @Test
    fun `detects 174 bpm impulse train`() {
        assertBpm(174, BpmDetector.detectBpm(impulseTrain(174.0), envRate), 2)
    }

    @Test
    fun `silence yields null`() {
        assertNull(BpmDetector.detectBpm(FloatArray(2000), envRate))
    }

    @Test
    fun `constant envelope yields null`() {
        assertNull(BpmDetector.detectBpm(FloatArray(2000) { 0.5f }, envRate))
    }

    /**
     * End-to-end-ish: 120 BPM click track (decaying noise bursts on a
     * seeded RNG, deterministic) -> OnsetEnvelope -> BpmDetector.
     */
    @Test
    fun `detects bpm of synthesized click track end to end`() {
        val sampleRate = 22050
        val seconds = 25
        val n = sampleRate * seconds
        val pcm = FloatArray(n)
        val rng = Random(7)
        val periodSamples = sampleRate / 2 // 120 BPM
        val burstLength = 900

        var beat = 0
        while (beat + burstLength < n) {
            for (j in 0 until burstLength) {
                val amp = exp(-j / 120.0) * 0.9
                pcm[beat + j] += ((rng.nextFloat() * 2f - 1f) * amp).toFloat()
            }
            beat += periodSamples
        }

        val onset = OnsetEnvelope.compute(pcm, sampleRate)
        assertTrue(onset.envelope.isNotEmpty(), "onset envelope should not be empty")
        assertBpm(120, BpmDetector.detectBpm(onset.envelope, onset.envelopeSampleRate), 3)
    }
}
