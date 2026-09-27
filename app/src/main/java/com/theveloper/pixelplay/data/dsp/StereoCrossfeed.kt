package com.theveloper.pixelplay.data.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Crossfeed strength setting.
 */
enum class CrossfeedStrength(val displayName: String, val gainDb: Float, val delayUs: Float) {
    OFF("Off", -120f, 0f),
    SUBTLE("Subtle", -9.5f, 250f),
    MEDIUM("Medium", -6.0f, 280f),
    STRONG("Strong", -4.5f, 320f);

    companion object {
        fun fromOrdinal(ordinal: Int): CrossfeedStrength {
            return values().getOrElse(ordinal.coerceIn(0, values().size - 1)) { OFF }
        }
    }
}

/**
 * Bauer / Meier Stereo Crossfeed simulation for natural headphone listening.
 *
 * Traditional headphone reproduction has 100% stereo channel separation, which causes
 * listener fatigue ("in-head localization") especially on early stereo recordings with
 * extreme hard-panning.
 *
 * This processor simulates acoustic loudspeaker listening:
 * 1. Sound from the left channel travels to the right ear delayed by ~280 microseconds (interaural time difference).
 * 2. Sound traveling around the human head is low-passed at ~700 Hz (head-shadow effect).
 * 3. The crossfeed signal is blended into the opposite channel.
 */
class StereoCrossfeed(
    var strength: CrossfeedStrength = CrossfeedStrength.OFF
) {
    private var sampleRate: Float = 44100f
    private var delaySamples: Int = 12
    private var crossfeedGain: Float = 0f

    // Delay ring buffers for Left and Right channels (max 2 ms buffer is 192 samples at 96 kHz)
    private var delayBufferL = FloatArray(256)
    private var delayBufferR = FloatArray(256)
    private var bufferMask = 255
    private var writeIndex = 0

    // 1st-order Low-pass filter state for crossfeed path (cutoff ~700 Hz)
    private var lpCoeff = 0.1f
    private var lpStateL = 0f
    private var lpStateR = 0f

    fun setSampleRate(rate: Float) {
        if (rate <= 0f) return
        sampleRate = rate
        updateParameters()
    }

    fun setCrossfeedStrength(newStrength: CrossfeedStrength) {
        strength = newStrength
        updateParameters()
    }

    private fun updateParameters() {
        if (strength == CrossfeedStrength.OFF) {
            crossfeedGain = 0f
            return
        }

        // Delay in samples = delaySec * sampleRate
        val delaySec = strength.delayUs / 1_000_000f
        delaySamples = (delaySec * sampleRate).roundToInt().coerceIn(1, 200)

        // Crossfeed linear gain from dB
        crossfeedGain = 10.0.pow(strength.gainDb / 20.0).toFloat()

        // 1-pole Low-pass filter at 700 Hz: coeff = 1 - exp(-2 * PI * fc / Fs)
        val fc = 700f
        lpCoeff = (1.0 - exp(-2.0 * PI * fc / sampleRate.toDouble())).toFloat().coerceIn(0.01f, 0.99f)
    }

    /**
     * Processes a single stereo frame (L and R).
     *
     * Convenience/test API only (the returned [Pair] boxes both floats). The audio thread uses
     * the allocation-free [processInterleaved].
     */
    fun processFrame(left: Float, right: Float): Pair<Float, Float> {
        if (strength == CrossfeedStrength.OFF || crossfeedGain <= 0.001f) {
            return Pair(left, right)
        }
        val scratch = frameScratch
        scratch[0] = left
        scratch[1] = right
        processInterleaved(scratch, 1, 2)
        return Pair(scratch[0], scratch[1])
    }

    private val frameScratch = FloatArray(2)

    /**
     * In-place stereo buffer processing. Allocation-free (runs on the playback thread).
     */
    fun processInterleaved(buffer: FloatArray, frameCount: Int, channels: Int) {
        if (channels != 2 || strength == CrossfeedStrength.OFF || crossfeedGain <= 0.001f) return

        val bufL = delayBufferL
        val bufR = delayBufferR
        val mask = bufferMask
        val size = bufL.size
        val delay = delaySamples
        val coeff = lpCoeff
        val gain = crossfeedGain
        val directScale = 1f - (gain * 0.35f)
        var wIdx = writeIndex
        var stateL = lpStateL
        var stateR = lpStateR

        var idx = 0
        for (i in 0 until frameCount) {
            val left = buffer[idx]
            val right = buffer[idx + 1]

            // Write current inputs into the delay line, then read the delayed samples.
            bufL[wIdx] = left
            bufR[wIdx] = right
            val rIdx = (wIdx - delay + size) and mask
            val delayedL = bufL[rIdx]
            val delayedR = bufR[rIdx]
            wIdx = (wIdx + 1) and mask

            // Head-shadow low-pass on the crossfeed path.
            stateL += coeff * (delayedL - stateL)
            stateR += coeff * (delayedR - stateR)

            // Direct signal (slightly attenuated to keep loudness constant) + opposite channel.
            buffer[idx] = left * directScale + stateR * gain
            buffer[idx + 1] = right * directScale + stateL * gain
            idx += 2
        }

        writeIndex = wIdx
        lpStateL = stateL
        lpStateR = stateR
    }

    fun reset() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        writeIndex = 0
        lpStateL = 0f
        lpStateR = 0f
    }
}
