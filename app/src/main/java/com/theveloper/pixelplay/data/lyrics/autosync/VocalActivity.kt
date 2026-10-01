package com.theveloper.pixelplay.data.lyrics.autosync

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Second pass over the stored mel spectrogram: an estimate of how much *non-repeating* sound
 * there is in the vocal band at each frame.
 *
 * Accompaniment repeats (a drum pattern every bar, a chord cycle every few bars) and a sung
 * melody with words mostly does not. This is the idea behind REPET (Rafii & Pardo, 2013): find
 * the song's repeating period from the beat spectrum, model each frame's accompaniment as the
 * median of the frames one, two and three periods before and after, and keep what is louder
 * than that model. What remains is dominated by the voice, which makes "the singing starts
 * here" visible even when a guitar strums on the same beat.
 */
internal object VocalActivity {

    /** Repeating periods considered, in frames (10 ms): 1.2 s to 10 s covers bars and phrases. */
    private const val MIN_PERIOD = 120
    private const val MAX_PERIOD = 1_000
    private const val COARSE = 5
    private const val REPEATS = 3

    /** Per-frame log energy of the non-repeating part of the vocal band. */
    fun residualEnergy(track: VocalFeatureTrack): FloatArray {
        val n = track.frameCount
        val bands = track.bands
        val out = FloatArray(n)
        if (n <= 0) return out
        val period = repeatingPeriod(track)
        val column = FloatArray(n)
        val neighbours = FloatArray(2 * REPEATS)
        for (b in 0 until bands) {
            for (t in 0 until n) column[t] = track.mel(t, b)
            for (t in 0 until n) {
                var count = 0
                if (period > 0) {
                    for (k in -REPEATS..REPEATS) {
                        if (k == 0) continue
                        val u = t + k * period
                        if (u in 0 until n) neighbours[count++] = column[u]
                    }
                }
                if (count == 0) continue
                val model = min(column[t], VocalFeatureExtractor.median(neighbours, count))
                out[t] += ln(1f + (column[t] - model))
            }
        }
        return out
    }

    /**
     * The song's main repeating period in frames: the lag with the strongest average
     * autocorrelation of the (mean-removed) log-mel bands, searched coarsely at 50 ms and then
     * refined to 10 ms. Returns 0 when the song is too short to repeat.
     */
    fun repeatingPeriod(track: VocalFeatureTrack): Int {
        val n = track.frameCount
        val bands = track.bands
        if (n < 2 * MIN_PERIOD) return 0
        val maxLag = min(MAX_PERIOD - 1, n / 2)

        // Per-band means, and a 50 ms-averaged, mean-removed copy for the coarse search.
        val means = FloatArray(bands)
        for (b in 0 until bands) {
            var sum = 0.0
            for (t in 0 until n) sum += track.logMelValue(t, b)
            means[b] = (sum / n).toFloat()
        }
        val nc = n / COARSE
        val coarse = Array(bands) { b ->
            FloatArray(nc) { i ->
                var s = 0f
                for (j in 0 until COARSE) s += track.logMelValue(i * COARSE + j, b)
                s / COARSE - means[b]
            }
        }
        var bestCoarse = -1
        var bestValue = Double.NEGATIVE_INFINITY
        for (lag in MIN_PERIOD / COARSE..maxLag / COARSE) {
            val v = autocorrelation(coarse, nc, lag)
            if (v > bestValue) { bestValue = v; bestCoarse = lag }
        }
        if (bestCoarse < 0) return 0

        // Refine at full resolution around the coarse answer.
        var best = bestCoarse * COARSE
        bestValue = Double.NEGATIVE_INFINITY
        val row = FloatArray(n)
        val lo = max(MIN_PERIOD, best - COARSE - 1)
        val hi = min(maxLag, best + COARSE + 1)
        val sums = DoubleArray(hi - lo + 1)
        for (b in 0 until bands) {
            for (t in 0 until n) row[t] = track.logMelValue(t, b) - means[b]
            for (lag in lo..hi) {
                var s = 0.0
                for (t in 0 until n - lag) s += row[t] * row[t + lag]
                sums[lag - lo] += s
            }
        }
        for (lag in lo..hi) {
            val v = sums[lag - lo] / (n - lag)
            if (v > bestValue) { bestValue = v; best = lag }
        }
        return best
    }

    private fun autocorrelation(rows: Array<FloatArray>, n: Int, lag: Int): Double {
        var total = 0.0
        for (row in rows) {
            var s = 0.0
            for (t in 0 until n - lag) s += row[t] * row[t + lag]
            total += s
        }
        return total / (n - lag)
    }
}
