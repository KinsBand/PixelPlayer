package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Spectral-flux onset strength envelope. Pure Kotlin.
 *
 * STFT with [WINDOW_SIZE] 1024 and [HOP_SIZE] 512; per frame the
 * log-compressed magnitude spectrum is compared to the previous frame and
 * the positive (half-wave rectified) differences are summed across bins —
 * that sum is the onset strength for the frame.
 */
object OnsetEnvelope {

    const val WINDOW_SIZE = 1024
    const val HOP_SIZE = 512

    /** Log compression factor: ln(1 + GAMMA * |X|). */
    private const val LOG_GAMMA = 1000f

    /** Result of [compute]. Not a data class (array identity semantics are fine). */
    class Result(
        /** Onset strength, mean 0 and ~unit variance (all zeros when degenerate). */
        val envelope: FloatArray,
        /** Frame rate of [envelope] in Hz = sampleRate / [HOP_SIZE]. */
        val envelopeSampleRate: Float,
        /** Mean of the raw (pre-normalization) flux — an absolute activity level. */
        val rawMean: Float
    )

    fun compute(samples: FloatArray, sampleRate: Int): Result {
        val envelopeRate = sampleRate.toFloat() / HOP_SIZE
        if (samples.size < WINDOW_SIZE || sampleRate <= 0) {
            return Result(FloatArray(0), envelopeRate, 0f)
        }

        val frameCount = (samples.size - WINDOW_SIZE) / HOP_SIZE + 1
        val flux = FloatArray(frameCount)
        var prevLogMag = FloatArray(0)
        val frame = FloatArray(WINDOW_SIZE)

        for (f in 0 until frameCount) {
            val offset = f * HOP_SIZE
            samples.copyInto(frame, 0, offset, offset + WINDOW_SIZE)
            val mag = Fft.magnitudes(frame)
            val logMag = FloatArray(mag.size) { ln(1f + LOG_GAMMA * mag[it]) }
            if (prevLogMag.isNotEmpty()) {
                var sum = 0f
                for (k in logMag.indices) {
                    val d = logMag[k] - prevLogMag[k]
                    if (d > 0f) sum += d
                }
                flux[f] = sum
            }
            prevLogMag = logMag
        }

        var mean = 0f
        for (v in flux) mean += v
        mean /= flux.size

        var varAcc = 0f
        for (v in flux) {
            val d = v - mean
            varAcc += d * d
        }
        val std = sqrt(varAcc / flux.size)

        // Degenerate (silent) input: leave as zeros so downstream detectors
        // see zero energy and bail out.
        val normalized = FloatArray(flux.size)
        if (std > 1e-8f) {
            for (i in flux.indices) normalized[i] = (flux[i] - mean) / std
        }

        return Result(normalized, envelopeRate, mean)
    }
}
