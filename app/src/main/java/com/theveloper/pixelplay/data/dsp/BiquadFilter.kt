package com.theveloper.pixelplay.data.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filter type for biquad IIR filters.
 */
enum class FilterType {
    PEAKING,
    LOW_SHELF,
    HIGH_SHELF,
    HIGH_PASS,
    LOW_PASS,
    NOTCH,
    BAND_PASS
}

/**
 * Normalized filter coefficients for a Direct Form II Transposed biquad filter.
 * Transfer function: H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2)
 */
data class BiquadCoefficients(
    val b0: Float = 1f,
    val b1: Float = 0f,
    val b2: Float = 0f,
    val a1: Float = 0f,
    val a2: Float = 0f
) {
    companion object {
        val BYPASS = BiquadCoefficients(1f, 0f, 0f, 0f, 0f)

        /**
         * Calculates Peaking (Bell) EQ coefficients using the RBJ Audio EQ Cookbook formulas.
         */
        fun calculatePeaking(
            sampleRate: Float,
            frequency: Float,
            gainDb: Float,
            q: Float = 1.414f
        ): BiquadCoefficients {
            if (sampleRate <= 0f || frequency <= 0f || q <= 0f) return BYPASS
            if (kotlin.math.abs(gainDb) < 0.001f) return BYPASS

            val nyquist = sampleRate * 0.499f
            val f0 = frequency.coerceIn(10f, nyquist)
            val w0 = (2.0 * PI * f0 / sampleRate).toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val a = 10.0.pow((gainDb / 40.0).toDouble()).toFloat()
            val alpha = sinW0 / (2f * q.coerceAtLeast(0.1f))

            val b0Raw = 1f + alpha * a
            val b1Raw = -2f * cosW0
            val b2Raw = 1f - alpha * a
            val a0Raw = 1f + alpha / a
            val a1Raw = -2f * cosW0
            val a2Raw = 1f - alpha / a

            if (a0Raw == 0f) return BYPASS
            val invA0 = 1f / a0Raw
            return BiquadCoefficients(
                b0 = b0Raw * invA0,
                b1 = b1Raw * invA0,
                b2 = b2Raw * invA0,
                a1 = a1Raw * invA0,
                a2 = a2Raw * invA0
            )
        }

        /**
         * Calculates Low Shelf filter coefficients.
         */
        fun calculateLowShelf(
            sampleRate: Float,
            frequency: Float,
            gainDb: Float,
            q: Float = 0.7071f
        ): BiquadCoefficients {
            if (sampleRate <= 0f || frequency <= 0f) return BYPASS
            if (kotlin.math.abs(gainDb) < 0.001f) return BYPASS

            val nyquist = sampleRate * 0.499f
            val f0 = frequency.coerceIn(10f, nyquist)
            val w0 = (2.0 * PI * f0 / sampleRate).toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val a = 10.0.pow((gainDb / 40.0).toDouble()).toFloat()
            val alpha = sinW0 / (2f * q.coerceAtLeast(0.1f))
            val twoSqrtAAlpha = 2f * sqrt(a) * alpha

            val b0Raw = a * ((a + 1f) - (a - 1f) * cosW0 + twoSqrtAAlpha)
            val b1Raw = 2f * a * ((a - 1f) - (a + 1f) * cosW0)
            val b2Raw = a * ((a + 1f) - (a - 1f) * cosW0 - twoSqrtAAlpha)
            val a0Raw = (a + 1f) + (a - 1f) * cosW0 + twoSqrtAAlpha
            val a1Raw = -2f * ((a - 1f) + (a + 1f) * cosW0)
            val a2Raw = (a + 1f) + (a - 1f) * cosW0 - twoSqrtAAlpha

            if (a0Raw == 0f) return BYPASS
            val invA0 = 1f / a0Raw
            return BiquadCoefficients(
                b0 = b0Raw * invA0,
                b1 = b1Raw * invA0,
                b2 = b2Raw * invA0,
                a1 = a1Raw * invA0,
                a2 = a2Raw * invA0
            )
        }

        /**
         * Calculates High Shelf filter coefficients.
         */
        fun calculateHighShelf(
            sampleRate: Float,
            frequency: Float,
            gainDb: Float,
            q: Float = 0.7071f
        ): BiquadCoefficients {
            if (sampleRate <= 0f || frequency <= 0f) return BYPASS
            if (kotlin.math.abs(gainDb) < 0.001f) return BYPASS

            val nyquist = sampleRate * 0.499f
            val f0 = frequency.coerceIn(10f, nyquist)
            val w0 = (2.0 * PI * f0 / sampleRate).toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val a = 10.0.pow((gainDb / 40.0).toDouble()).toFloat()
            val alpha = sinW0 / (2f * q.coerceAtLeast(0.1f))
            val twoSqrtAAlpha = 2f * sqrt(a) * alpha

            val b0Raw = a * ((a + 1f) + (a - 1f) * cosW0 + twoSqrtAAlpha)
            val b1Raw = -2f * a * ((a - 1f) + (a + 1f) * cosW0)
            val b2Raw = a * ((a + 1f) + (a - 1f) * cosW0 - twoSqrtAAlpha)
            val a0Raw = (a + 1f) - (a - 1f) * cosW0 + twoSqrtAAlpha
            val a1Raw = 2f * ((a - 1f) - (a + 1f) * cosW0)
            val a2Raw = (a + 1f) - (a - 1f) * cosW0 - twoSqrtAAlpha

            if (a0Raw == 0f) return BYPASS
            val invA0 = 1f / a0Raw
            return BiquadCoefficients(
                b0 = b0Raw * invA0,
                b1 = b1Raw * invA0,
                b2 = b2Raw * invA0,
                a1 = a1Raw * invA0,
                a2 = a2Raw * invA0
            )
        }

        /**
         * Calculates High Pass filter coefficients (2nd order Butterworth, Q=0.7071).
         */
        fun calculateHighPass(
            sampleRate: Float,
            frequency: Float,
            q: Float = 0.7071f
        ): BiquadCoefficients {
            if (sampleRate <= 0f || frequency <= 0f) return BYPASS
            val nyquist = sampleRate * 0.499f
            val f0 = frequency.coerceIn(10f, nyquist)
            val w0 = (2.0 * PI * f0 / sampleRate).toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2f * q.coerceAtLeast(0.1f))

            val b0Raw = (1f + cosW0) * 0.5f
            val b1Raw = -(1f + cosW0)
            val b2Raw = (1f + cosW0) * 0.5f
            val a0Raw = 1f + alpha
            val a1Raw = -2f * cosW0
            val a2Raw = 1f - alpha

            if (a0Raw == 0f) return BYPASS
            val invA0 = 1f / a0Raw
            return BiquadCoefficients(
                b0 = b0Raw * invA0,
                b1 = b1Raw * invA0,
                b2 = b2Raw * invA0,
                a1 = a1Raw * invA0,
                a2 = a2Raw * invA0
            )
        }

        /**
         * Calculates Low Pass filter coefficients (2nd order Butterworth, Q=0.7071).
         */
        fun calculateLowPass(
            sampleRate: Float,
            frequency: Float,
            q: Float = 0.7071f
        ): BiquadCoefficients {
            if (sampleRate <= 0f || frequency <= 0f) return BYPASS
            val nyquist = sampleRate * 0.499f
            val f0 = frequency.coerceIn(10f, nyquist)
            val w0 = (2.0 * PI * f0 / sampleRate).toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2f * q.coerceAtLeast(0.1f))

            val b0Raw = (1f - cosW0) * 0.5f
            val b1Raw = 1f - cosW0
            val b2Raw = (1f - cosW0) * 0.5f
            val a0Raw = 1f + alpha
            val a1Raw = -2f * cosW0
            val a2Raw = 1f - alpha

            if (a0Raw == 0f) return BYPASS
            val invA0 = 1f / a0Raw
            return BiquadCoefficients(
                b0 = b0Raw * invA0,
                b1 = b1Raw * invA0,
                b2 = b2Raw * invA0,
                a1 = a1Raw * invA0,
                a2 = a2Raw * invA0
            )
        }

        /**
         * Calculates Notch (Band-Stop) filter coefficients.
         */
        fun calculateNotch(
            sampleRate: Float,
            frequency: Float,
            q: Float = 2.0f
        ): BiquadCoefficients {
            if (sampleRate <= 0f || frequency <= 0f) return BYPASS
            val nyquist = sampleRate * 0.499f
            val f0 = frequency.coerceIn(10f, nyquist)
            val w0 = (2.0 * PI * f0 / sampleRate).toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2f * q.coerceAtLeast(0.1f))

            val b0Raw = 1f
            val b1Raw = -2f * cosW0
            val b2Raw = 1f
            val a0Raw = 1f + alpha
            val a1Raw = -2f * cosW0
            val a2Raw = 1f - alpha

            if (a0Raw == 0f) return BYPASS
            val invA0 = 1f / a0Raw
            return BiquadCoefficients(
                b0 = b0Raw * invA0,
                b1 = b1Raw * invA0,
                b2 = b2Raw * invA0,
                a1 = a1Raw * invA0,
                a2 = a2Raw * invA0
            )
        }

        /**
         * Calculates Band Pass filter coefficients.
         */
        fun calculateBandPass(
            sampleRate: Float,
            frequency: Float,
            q: Float = 1.414f
        ): BiquadCoefficients {
            if (sampleRate <= 0f || frequency <= 0f) return BYPASS
            val nyquist = sampleRate * 0.499f
            val f0 = frequency.coerceIn(10f, nyquist)
            val w0 = (2.0 * PI * f0 / sampleRate).toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2f * q.coerceAtLeast(0.1f))

            val b0Raw = alpha
            val b1Raw = 0f
            val b2Raw = -alpha
            val a0Raw = 1f + alpha
            val a1Raw = -2f * cosW0
            val a2Raw = 1f - alpha

            if (a0Raw == 0f) return BYPASS
            val invA0 = 1f / a0Raw
            return BiquadCoefficients(
                b0 = b0Raw * invA0,
                b1 = b1Raw * invA0,
                b2 = b2Raw * invA0,
                a1 = a1Raw * invA0,
                a2 = a2Raw * invA0
            )
        }

        /**
         * Creates coefficients based on filter type.
         */
        fun create(
            type: FilterType,
            sampleRate: Float,
            frequency: Float,
            gainDb: Float,
            q: Float
        ): BiquadCoefficients {
            return when (type) {
                FilterType.PEAKING -> calculatePeaking(sampleRate, frequency, gainDb, q)
                FilterType.LOW_SHELF -> calculateLowShelf(sampleRate, frequency, gainDb, q)
                FilterType.HIGH_SHELF -> calculateHighShelf(sampleRate, frequency, gainDb, q)
                FilterType.HIGH_PASS -> calculateHighPass(sampleRate, frequency, q)
                FilterType.LOW_PASS -> calculateLowPass(sampleRate, frequency, q)
                FilterType.NOTCH -> calculateNotch(sampleRate, frequency, q)
                FilterType.BAND_PASS -> calculateBandPass(sampleRate, frequency, q)
            }
        }
    }

    /**
     * Calculates the frequency response magnitude in dB at frequency [f] for sample rate [sampleRate].
     */
    fun magnitudeResponseDb(f: Float, sampleRate: Float): Float {
        val w = (2.0 * PI * f / sampleRate).toFloat()
        val cosW = cos(w)
        val sinW = sin(w)
        val cos2W = cos(2f * w)
        val sin2W = sin(2f * w)

        val numReal = b0 + b1 * cosW + b2 * cos2W
        val numImag = -b1 * sinW - b2 * sin2W
        val denReal = 1f + a1 * cosW + a2 * cos2W
        val denImag = -a1 * sinW - a2 * sin2W

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        if (denMagSq <= 1e-12f) return 0f
        val magSq = numMagSq / denMagSq
        if (magSq <= 1e-12f) return -120f
        return (10.0 * log10(magSq.toDouble())).toFloat()
    }
}

/**
 * High-performance biquad filter implementation using Transposed Direct Form II.
 * Direct Form II Transposed difference equations:
 * y[n] = b0 * x[n] + d1
 * d1   = b1 * x[n] - a1 * y[n] + d2
 * d2   = b2 * x[n] - a2 * y[n]
 *
 * This form has optimal numerical precision and lowest roundoff error in 32-bit float audio.
 */
class BiquadFilter(
    var coefficients: BiquadCoefficients = BiquadCoefficients.BYPASS
) {
    // State variables for Left (or Mono) channel
    private var d1L = 0f
    private var d2L = 0f

    // State variables for Right channel
    private var d1R = 0f
    private var d2R = 0f

    /**
     * Process a single sample on Left channel.
     */
    fun processLeft(x: Float): Float {
        val y = coefficients.b0 * x + d1L
        d1L = coefficients.b1 * x - coefficients.a1 * y + d2L
        d2L = coefficients.b2 * x - coefficients.a2 * y
        return if (y.isNaN() || y.isInfinite()) {
            resetLeft()
            0f
        } else {
            y
        }
    }

    /**
     * Process a single sample on Right channel.
     */
    fun processRight(x: Float): Float {
        val y = coefficients.b0 * x + d1R
        d1R = coefficients.b1 * x - coefficients.a1 * y + d2R
        d2R = coefficients.b2 * x - coefficients.a2 * y
        return if (y.isNaN() || y.isInfinite()) {
            resetRight()
            0f
        } else {
            y
        }
    }

    fun resetLeft() {
        d1L = 0f
        d2L = 0f
    }

    fun resetRight() {
        d1R = 0f
        d2R = 0f
    }

    fun reset() {
        resetLeft()
        resetRight()
    }
}
