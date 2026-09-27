package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tempo estimation from an onset strength envelope via autocorrelation
 * with a log-Gaussian tempo prior. Pure Kotlin.
 *
 * Method:
 *  1. Mean-center the envelope, autocorrelate over the lag range
 *     corresponding to [MIN_BPM]..[MAX_BPM] (normalized by overlap length).
 *  2. Weight the autocorrelation by a log-Gaussian prior centered at
 *     120 BPM (standard practice — damps the octave/half-tempo bias).
 *  3. Strongest weighted peak wins; its height is compared against the
 *     total envelope energy (prominence) to reject silence/noise.
 *  4. Octave sanity: a sub-90 BPM winner is doubled when the double-time
 *     lag correlates nearly as well (>= 80%), and symmetrically for
 *     halving a >190 BPM winner.
 *  5. Sub-frame refinement by parabolic interpolation.
 */
object BpmDetector {

    const val MIN_BPM = 50.0
    const val MAX_BPM = 220.0

    private const val PRIOR_CENTER_BPM = 120.0
    private const val PRIOR_WIDTH_OCTAVES = 0.9
    private const val MIN_PROMINENCE = 0.25
    private const val MIN_RAW_ONSET_MEAN = 0.02f
    private const val OCTAVE_RATIO = 0.8

    /**
     * @param onset onset strength envelope (any scaling; mean is removed internally).
     * @param envelopeSampleRate frame rate of [onset] in Hz.
     * @param rawOnsetMean absolute activity level of the raw flux envelope
     *        (see [OnsetEnvelope.Result.rawMean]); below [MIN_RAW_ONSET_MEAN]
     *        the signal is treated as near-silence. Defaults to +inf
     *        (check skipped) for synthetic envelopes.
     * @return rounded BPM, or null when no reliable tempo is present.
     */
    fun detectBpm(
        onset: FloatArray,
        envelopeSampleRate: Float,
        rawOnsetMean: Float = Float.POSITIVE_INFINITY
    ): Int? {
        if (onset.isEmpty() || envelopeSampleRate <= 0f) return null
        if (rawOnsetMean < MIN_RAW_ONSET_MEAN) return null

        val lagMin = max(2, floor(envelopeSampleRate * 60.0 / MAX_BPM).toInt())
        val lagMax = (envelopeSampleRate * 60.0 / MIN_BPM).toInt()
        val n = onset.size
        if (lagMax <= lagMin + 2 || n < lagMax + 2) return null

        // Mean-center (autocorrelation of periodicity, not DC).
        var mean = 0.0
        for (v in onset) mean += v
        mean /= n
        val x = DoubleArray(n) { onset[it] - mean }

        val zeroLag = autocorr(x, 0)
        if (zeroLag <= 1e-9) return null // silence / constant envelope

        val lagCount = lagMax - lagMin + 1
        val raw = DoubleArray(lagCount) { autocorr(x, lagMin + it) / (n - (lagMin + it)) }

        // Log-Gaussian tempo prior centered at 120 BPM.
        val weighted = DoubleArray(lagCount)
        for (i in 0 until lagCount) {
            val bpm = 60.0 * envelopeSampleRate / (lagMin + i)
            val z = log2(bpm / PRIOR_CENTER_BPM) / PRIOR_WIDTH_OCTAVES
            weighted[i] = raw[i] * exp(-0.5 * z * z)
        }

        var peak = 0
        for (i in 1 until lagCount) if (weighted[i] > weighted[peak]) peak = i

        // Prominence: peak correlation vs. total envelope energy.
        val prominence = raw[peak] / (zeroLag / n)
        if (prominence < MIN_PROMINENCE) return null

        var refinedLag = lagMin + refineLag(weighted, peak)
        var winnerBpm = 60.0 * envelopeSampleRate / refinedLag

        // Octave sanity: prefer double-time when it explains the envelope
        // nearly as well as a suspiciously slow winner...
        if (winnerBpm < 90.0) {
            val doubleLag = (refinedLag / 2.0).roundToInt()
            if (doubleLag >= lagMin &&
                normalizedAutocorr(x, doubleLag) >= OCTAVE_RATIO * raw[peak]
            ) {
                refinedLag = lagMin + refineLag(weighted, doubleLag - lagMin)
                winnerBpm = 60.0 * envelopeSampleRate / refinedLag
            }
        }
        // ...and half-time for a suspiciously fast winner.
        if (winnerBpm > 190.0) {
            val halfLag = (refinedLag * 2.0).roundToInt()
            if (halfLag <= lagMax &&
                normalizedAutocorr(x, halfLag) >= OCTAVE_RATIO * raw[peak]
            ) {
                refinedLag = lagMin + refineLag(weighted, halfLag - lagMin)
            }
        }

        val bpm = (60.0 * envelopeSampleRate / refinedLag).roundToInt()
        // Allow a small overshoot above MAX_BPM from the lag grid.
        return bpm.takeIf { it in MIN_BPM.toInt()..(MAX_BPM.toInt() + 15) }
    }

    private fun normalizedAutocorr(x: DoubleArray, lag: Int): Double =
        autocorr(x, lag) / (x.size - lag)

    private fun autocorr(x: DoubleArray, lag: Int): Double {
        var sum = 0.0
        for (i in 0 until x.size - lag) sum += x[i] * x[i + lag]
        return sum
    }

    /** Sub-lag peak refinement by parabolic interpolation around index [idx]. */
    private fun refineLag(values: DoubleArray, idx: Int): Double {
        if (idx <= 0 || idx >= values.size - 1) return idx.toDouble()
        val y0 = values[idx - 1]
        val y1 = values[idx]
        val y2 = values[idx + 1]
        val denom = y0 - 2.0 * y1 + y2
        if (abs(denom) < 1e-12) return idx.toDouble()
        return idx + (0.5 * (y0 - y2) / denom).coerceIn(-0.5, 0.5)
    }
}
