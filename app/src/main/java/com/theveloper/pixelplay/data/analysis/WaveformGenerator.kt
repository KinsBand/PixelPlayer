package com.theveloper.pixelplay.data.analysis

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compact waveform overviews, quantized to bytes. Pure Kotlin.
 */
object WaveformGenerator {

    /**
     * Peak waveform: [points] buckets, each the absolute peak of its slice
     * of [samples] mapped to 0..255 (peaks are clamped to [-1, 1] first).
     * Silent or empty input yields all zeros.
     */
    fun generate(samples: FloatArray, points: Int = 1000): ByteArray {
        require(points > 0) { "points must be positive" }
        val out = ByteArray(points)
        if (samples.isEmpty()) return out

        val bucketSize = samples.size.toDouble() / points
        for (b in 0 until points) {
            val start = (b * bucketSize).toInt()
            val end = minOf(((b + 1) * bucketSize).toInt(), samples.size)
            var peak = 0f
            for (i in start until end) {
                val v = abs(samples[i])
                if (v > peak) peak = v
            }
            out[b] = quantize(peak)
        }
        return out
    }

    /**
     * Min/max waveform: [points] buckets, each contributing two bytes —
     * quantized min then max of the bucket (signed range [-1, 1] mapped to
     * 0..255). Output size is 2 * [points].
     */
    fun minMax(samples: FloatArray, points: Int = 500): ByteArray {
        require(points > 0) { "points must be positive" }
        val out = ByteArray(points * 2)
        if (samples.isEmpty()) return out

        val bucketSize = samples.size.toDouble() / points
        for (b in 0 until points) {
            val start = (b * bucketSize).toInt()
            val end = minOf(((b + 1) * bucketSize).toInt(), samples.size)
            var min = 0f
            var max = 0f
            for (i in start until end) {
                val v = samples[i]
                if (v < min) min = v
                if (v > max) max = v
            }
            out[b * 2] = quantizeSigned(min)
            out[b * 2 + 1] = quantizeSigned(max)
        }
        return out
    }

    /** Peak [0, 1] -> byte 0..255. */
    private fun quantize(peak: Float): Byte =
        (peak.coerceIn(0f, 1f) * 255f).roundToInt().toByte()

    /** Signed [-1, 1] -> byte 0..255 (128 ~= 0). */
    private fun quantizeSigned(value: Float): Byte =
        ((value.coerceIn(-1f, 1f) + 1f) * 127.5f).roundToInt().toByte()
}
