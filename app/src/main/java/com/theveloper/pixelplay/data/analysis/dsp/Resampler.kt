package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Small general-purpose resampler. Pure Kotlin.
 */
object Resampler {

    /**
     * Linear-interpolation resampling, intended for decimation
     * ([sourceRate] > [targetRate], e.g. 44100 -> 22050). If the rates are
     * equal the input is returned as-is; callers should avoid upsampling
     * (analysis works natively at the source rate in that case).
     */
    fun resampleLinear(samples: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        require(sourceRate > 0 && targetRate > 0) { "sample rates must be positive" }
        if (sourceRate == targetRate || samples.isEmpty()) return samples

        val ratio = sourceRate.toDouble() / targetRate
        val outSize = maxOf(1, (samples.size / ratio).toInt())
        val out = FloatArray(outSize)
        for (i in 0 until outSize) {
            val pos = i * ratio
            val idx = pos.toInt()
            val frac = (pos - idx).toFloat()
            val next = minOf(idx + 1, samples.size - 1)
            out[i] = samples[idx] * (1f - frac) + samples[next] * frac
        }
        return out
    }

    /** Kernel zero crossings on each side of the centre tap (quality vs. cost). */
    private const val ZERO_CROSSINGS = 8

    /** Kernel table resolution: samples per unit of source-sample distance. */
    private const val TABLE_OVERSAMPLE = 256

    /** Passband edge as a fraction of the target Nyquist (leaves a transition band). */
    private const val CUTOFF_FRACTION = 0.92

    /**
     * Band-limited decimation: a Blackman-windowed sinc low-pass evaluated at each
     * output position, so content above the target Nyquist is removed instead of
     * folding back into the band (which [resampleLinear] does, and which smears the
     * chroma used for key detection and the flux used for onsets).
     *
     * Cost is ~2 x [ZERO_CROSSINGS] x ratio multiply-adds per output sample (≈37 for
     * 44.1 kHz → 22.05 kHz). Falls back to [resampleLinear] when not decimating.
     */
    fun resample(samples: FloatArray, sourceRate: Int, targetRate: Int): FloatArray =
        resample(samples, samples.size, sourceRate, targetRate)

    /**
     * Same as [resample] over the first [length] entries of [samples], so a caller holding an
     * over-allocated decode buffer does not need a trimmed copy first (saves up to 16 MB).
     */
    fun resample(samples: FloatArray, length: Int, sourceRate: Int, targetRate: Int): FloatArray {
        require(sourceRate > 0 && targetRate > 0) { "sample rates must be positive" }
        require(length in 0..samples.size) { "length out of range" }
        if (sourceRate <= targetRate || length == 0) {
            val exact = if (length == samples.size) samples else samples.copyOf(length)
            return resampleLinear(exact, sourceRate, targetRate)
        }

        val ratio = sourceRate.toDouble() / targetRate
        // Cutoff in cycles per *source* sample.
        val cutoff = 0.5 / ratio * CUTOFF_FRACTION
        val halfWidth = ceil(ZERO_CROSSINGS / (2.0 * cutoff)).toInt()
        val table = buildKernelTable(cutoff, halfWidth)

        val outSize = maxOf(1, (length / ratio).toInt())
        val out = FloatArray(outSize)
        val last = length - 1
        for (i in 0 until outSize) {
            val pos = i * ratio
            val center = floor(pos).toInt()
            val frac = pos - center
            var acc = 0.0
            var weightSum = 0.0
            val from = maxOf(center - halfWidth + 1, 0)
            val to = minOf(center + halfWidth, last)
            for (j in from..to) {
                val distance = kotlin.math.abs(j - center - frac)
                if (distance >= halfWidth) continue
                val t = distance * TABLE_OVERSAMPLE
                val idx = t.toInt()
                val f = t - idx
                val w = table[idx] + (table[idx + 1] - table[idx]) * f
                acc += samples[j] * w
                weightSum += w
            }
            // Normalising by the summed weights keeps unity DC gain, including at the
            // edges where the kernel is truncated.
            out[i] = if (weightSum > 1e-12) (acc / weightSum).toFloat() else 0f
        }
        return out
    }

    private fun buildKernelTable(cutoff: Double, halfWidth: Int): DoubleArray {
        val size = halfWidth * TABLE_OVERSAMPLE + 2
        return DoubleArray(size) { k ->
            val d = k.toDouble() / TABLE_OVERSAMPLE
            if (d >= halfWidth) {
                0.0
            } else {
                val x = 2.0 * cutoff * d
                val sinc = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)
                // Blackman window over [-halfWidth, halfWidth], evaluated at the centre offset d.
                val n = 0.5 + d / (2.0 * halfWidth)
                val window = 0.42 - 0.5 * cos(2.0 * PI * n) + 0.08 * cos(4.0 * PI * n)
                2.0 * cutoff * sinc * window
            }
        }
    }
}
