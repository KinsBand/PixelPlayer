package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Iterative radix-2 Cooley-Tukey FFT. Pure Kotlin, no allocations in the
 * transform itself (in-place on the provided arrays).
 */
object Fft {

    /**
     * Forward transform (negative exponent), in place.
     * [re]/[im] must have equal power-of-two length >= 2.
     */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(im.size == n) { "re/im size mismatch" }
        require(n >= 2 && n.countOneBits() == 1) { "FFT size must be a power of two, was $n" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }

        // Butterfly stages. Twiddles advance by complex recurrence (error
        // accumulation is negligible at the sizes used here, <= 4096).
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val angle = -2.0 * PI / len
            val wLenRe = cos(angle).toFloat()
            val wLenIm = kotlin.math.sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vRe = re[b] * wRe - im[b] * wIm
                    val vIm = re[b] * wIm + im[b] * wRe
                    re[b] = re[a] - vRe
                    im[b] = im[a] - vIm
                    re[a] += vRe
                    im[a] += vIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun nextPowerOfTwo(x: Int): Int {
        var p = 1
        while (p < x) p = p shl 1
        return p
    }

    /**
     * Magnitude spectrum of [frame]: applies a Hann window internally
     * (window length == frame size), zero-pads to the next power of two,
     * FFTs, and returns the magnitudes of the first n/2 bins (positive
     * frequencies below Nyquist). Bin k of the result corresponds to
     * frequency k * sampleRate / n.
     */
    fun magnitudes(frame: FloatArray): FloatArray {
        val n = nextPowerOfTwo(frame.size)
        val re = FloatArray(n)
        val im = FloatArray(n)
        val size = frame.size
        if (size > 1) {
            val windowScale = 2.0 * PI / (size - 1)
            for (i in 0 until size) {
                val w = 0.5f * (1f - cos(windowScale * i).toFloat())
                re[i] = frame[i] * w
            }
        } else if (size == 1) {
            re[0] = frame[0]
        }
        fft(re, im)
        val half = n / 2
        return FloatArray(half) { k -> sqrt(re[k] * re[k] + im[k] * im[k]) }
    }
}
