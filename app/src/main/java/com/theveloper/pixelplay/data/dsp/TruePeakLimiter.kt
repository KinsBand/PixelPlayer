package com.theveloper.pixelplay.data.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.tanh

/**
 * True Peak Limiter and Soft Saturation processor.
 *
 * Catches transients smoothly without harsh digital clipping or square-wave crackle:
 * 1. Soft Saturation: Uses an analog tape-style transfer function that is 100% linear below
 *    the knee threshold (0.85 = ~-1.4 dBFS), then smoothly compresses toward 1.0 (-0.001 dBFS).
 * 2. Peak Limiter: Fast-envelope tracking with instantaneous attack and smooth exponential decay,
 *    preventing inter-sample and true peak overshoots.
 */
class TruePeakLimiter(
    var isEnabled: Boolean = true,
    var isSoftSaturationEnabled: Boolean = true
) {
    companion object {
        private const val KNEE_THRESHOLD = 0.85f
        private const val KNEE_RANGE = 0.15f // 1.0f - KNEE_THRESHOLD
        private const val CEILING = 0.999f
    }

    private var envelope = 0f
    private var releaseCoeff = 0.999f // set by sampleRate

    fun setSampleRate(sampleRate: Float, releaseTimeSec: Float = 0.05f) {
        if (sampleRate > 0f && releaseTimeSec > 0f) {
            // Decay coefficient for exponential release: coeff = exp(-1 / (sampleRate * releaseTime))
            releaseCoeff = exp(-1.0 / (sampleRate.toDouble() * releaseTimeSec.toDouble())).toFloat()
        }
    }

    /**
     * Applies soft saturation to a single sample.
     */
    fun saturate(x: Float): Float {
        if (!isSoftSaturationEnabled) return x.coerceIn(-1f, 1f)

        val absX = abs(x)
        if (absX <= KNEE_THRESHOLD) {
            return x
        }

        val over = (absX - KNEE_THRESHOLD) / KNEE_RANGE
        val saturatedAbs = KNEE_THRESHOLD + KNEE_RANGE * tanh(over.toDouble()).toFloat()
        return sign(x) * saturatedAbs.coerceAtMost(CEILING)
    }

    /**
     * Processes a single stereo frame (L and R) with limiter and saturation.
     *
     * Convenience/test API only: it returns a [Pair], which boxes both floats. Never call it
     * from the audio thread — [processInterleaved] is the allocation-free hot path. (Calling
     * this per frame allocated ~3 objects per frame, i.e. ~150k objects/s at 48 kHz, which
     * drove constant GC on the playback thread and was the allocation that finally hit OOM.)
     */
    fun processFrame(left: Float, right: Float): Pair<Float, Float> {
        if (!isEnabled) return Pair(left, right)
        val gain = updateEnvelopeAndGain(max(abs(left), abs(right)))
        return Pair(saturate(left * gain), saturate(right * gain))
    }

    /** Advances the peak envelope with [peak] and returns the gain to apply. No allocation. */
    private fun updateEnvelopeAndGain(peak: Float): Float {
        envelope = if (peak > envelope) {
            peak // instantaneous attack
        } else {
            envelope * releaseCoeff + peak * (1f - releaseCoeff)
        }
        return if (envelope > CEILING) CEILING / envelope else 1f
    }

    /**
     * In-place interleaved buffer processing. Allocation-free: runs on ExoPlayer's playback
     * thread for every decoded buffer.
     */
    fun processInterleaved(buffer: FloatArray, frameCount: Int, channels: Int) {
        if (!isEnabled) return

        if (channels == 2) {
            var idx = 0
            for (i in 0 until frameCount) {
                val l = buffer[idx]
                val r = buffer[idx + 1]
                val gain = updateEnvelopeAndGain(max(abs(l), abs(r)))
                buffer[idx] = saturate(l * gain)
                buffer[idx + 1] = saturate(r * gain)
                idx += 2
            }
        } else {
            for (i in 0 until frameCount) {
                val sample = buffer[i]
                val gain = updateEnvelopeAndGain(abs(sample))
                buffer[i] = saturate(sample * gain)
            }
        }
    }

    fun reset() {
        envelope = 0f
    }
}
