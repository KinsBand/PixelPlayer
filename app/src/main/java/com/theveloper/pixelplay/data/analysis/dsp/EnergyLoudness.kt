package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Loudness / energy descriptors on working-rate mono PCM. Pure Kotlin.
 *
 * NOTE: [rmsDb] is plain RMS dBFS — not K-weighted LUFS (the two standard
 * K-weighting biquads are intentionally skipped). Stored analysis rows
 * therefore carry "RMS dBFS" loudness semantics.
 */
object EnergyLoudness {

    const val DB_FLOOR = -100f

    // Energy mapping window: RMS levels from QUIET_DBFS to LOUD_DBFS map
    // linearly to 0..1 (then a perceptual gamma is applied).
    private const val QUIET_DBFS = -40f
    private const val LOUD_DBFS = -5f
    private const val ENERGY_GAMMA = 0.75f

    /** RMS level in dBFS, floored at [DB_FLOOR] (silence -> -100). */
    fun rmsDb(samples: FloatArray): Float {
        if (samples.isEmpty()) return DB_FLOOR
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        val rms = sqrt(sum / samples.size)
        if (rms <= 0.0) return DB_FLOOR
        val db = 20.0 * log10(rms)
        return if (db < DB_FLOOR) DB_FLOOR else db.toFloat()
    }

    /**
     * Perceptual-ish energy 0..1: the RMS dBFS level is mapped linearly
     * across the [QUIET_DBFS]..[LOUD_DBFS] window (clamped), then raised to
     * [ENERGY_GAMMA] to expand resolution at the quiet end.
     */
    fun energy(samples: FloatArray): Float {
        val db = rmsDb(samples)
        val t = ((db - QUIET_DBFS) / (LOUD_DBFS - QUIET_DBFS)).coerceIn(0f, 1f)
        return t.pow(ENERGY_GAMMA)
    }

    /**
     * EBU R128 Integrated Loudness in LUFS.
     * Applies K-weighting pre-filter & RLB high-pass filter, then 400ms gating.
     */
    fun lufsIntegrated(samples: FloatArray, sampleRate: Int): Float =
        lufsIntegrated(sampleRate, samples, null, 0)

    /**
     * Integrated loudness over [first] followed by [second] (from [secondFrom]) as if they were
     * one concatenated signal — without building that concatenation.
     *
     * Memory: the old version allocated two full-length filtered copies of the input (and the
     * tail analysis concatenated head + tail first), i.e. 3-4x the PCM size — ~70 MB transient
     * for a long track on a 256 MB heap. This streams the two K-weighting biquads and keeps
     * only one 400 ms block of squared samples plus one Double per 100 ms step. The arithmetic
     * (float filter outputs, float squares, double block sums in index order) is unchanged, so
     * results are bit-identical to the previous implementation.
     */
    fun lufsIntegrated(sampleRate: Int, first: FloatArray, second: FloatArray?, secondFrom: Int): Float {
        val secondStart = secondFrom.coerceIn(0, second?.size ?: 0)
        val secondCount = if (second == null) 0 else second.size - secondStart
        val total = first.size + secondCount
        if (total == 0 || sampleRate <= 0) return DB_FLOOR

        // 400ms block size with 100ms step (75% overlap)
        val blockSize = (sampleRate * 0.400).toInt()
        val stepSize = (sampleRate * 0.100).toInt()
        if (total < blockSize || blockSize <= 0 || stepSize <= 0) {
            return rmsDbOf(first, second, secondStart)
        }

        // BS.1770 K-weighting, with coefficients derived for the actual sample rate.
        // The published table is only valid at 48 kHz; applying it to the 22.05 kHz
        // working rate moved the shelf to ~770 Hz and the high-pass to ~17 Hz and
        // biased the result by up to ~2.4 LU on bass-heavy material.
        val k = kWeightingCoefficients(sampleRate.toDouble())
        val shelf = StreamingBiquad(k.shelfB, k.shelfA)
        val highPass = StreamingBiquad(k.highPassB, k.highPassA)

        val blockCount = (total - blockSize) / stepSize + 1
        val blockEnergies = DoubleArray(blockCount)
        var blocks = 0
        val ring = FloatArray(blockSize) // squared K-weighted samples of the current block
        var n = 0

        fun push(x: Float) {
            val stage1 = shelf.process(x)
            val s = highPass.process(stage1)
            ring[n % blockSize] = s * s
            n++
            if (n >= blockSize && (n - blockSize) % stepSize == 0 && blocks < blockCount) {
                // Sum the block in original index order (oldest first) for identical rounding.
                val start = n % blockSize
                var sumSquare = 0.0
                for (i in start until blockSize) sumSquare += ring[i]
                for (i in 0 until start) sumSquare += ring[i]
                blockEnergies[blocks++] = sumSquare / blockSize
            }
        }

        for (x in first) push(x)
        if (second != null) for (i in secondStart until second.size) push(second[i])

        if (blocks == 0) return DB_FLOOR

        // Absolute threshold: -70 LUFS
        // LUFS = -0.691 + 10 * log10(z) -> z_abs = 10^((-70 + 0.691) / 10) = 1.05435e-7
        val absThresholdZ = 1.0543516568285514e-07
        var absSum = 0.0
        var absCount = 0
        for (i in 0 until blocks) {
            val z = blockEnergies[i]
            if (z >= absThresholdZ) { absSum += z; absCount++ }
        }
        if (absCount == 0) return DB_FLOOR

        val absMeanZ = absSum / absCount
        val absLoudness = -0.691 + 10.0 * log10(absMeanZ)

        // Relative threshold: absLoudness - 10 LU
        val relThresholdLoudness = absLoudness - 10.0
        val relThresholdZ = 10.0.pow((relThresholdLoudness + 0.691) / 10.0)
        var relSum = 0.0
        var relCount = 0
        for (i in 0 until blocks) {
            val z = blockEnergies[i]
            if (z >= absThresholdZ && z >= relThresholdZ) { relSum += z; relCount++ }
        }
        if (relCount == 0) return absLoudness.toFloat()

        val relMeanZ = relSum / relCount
        val integratedLufs = -0.691 + 10.0 * log10(relMeanZ)
        return if (integratedLufs.isNaN() || integratedLufs < DB_FLOOR) DB_FLOOR else integratedLufs.toFloat()
    }

    private fun rmsDbOf(first: FloatArray, second: FloatArray?, secondStart: Int): Float {
        if (second == null || secondStart >= second.size) return rmsDb(first)
        val count = first.size + (second.size - secondStart)
        if (count == 0) return DB_FLOOR
        var sum = 0.0
        for (s in first) sum += s.toDouble() * s
        for (i in secondStart until second.size) { val s = second[i]; sum += s.toDouble() * s }
        val rms = sqrt(sum / count)
        if (rms <= 0.0) return DB_FLOOR
        val db = 20.0 * log10(rms)
        return if (db < DB_FLOOR) DB_FLOOR else db.toFloat()
    }

    /** One biquad with persistent state; output rounded to Float like the old array filter. */
    private class StreamingBiquad(b: DoubleArray, a: DoubleArray) {
        private val b0 = b[0]
        private val b1 = b[1]
        private val b2 = b[2]
        private val a1 = a[1]
        private val a2 = a[2]
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(input: Float): Float {
            val x0 = input.toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            return y0.toFloat()
        }
    }

    /** ReplayGain in dB based on EBU R128 integrated LUFS (-18 LUFS target standard). */
    fun calculateReplayGain(lufs: Float): Float {
        if (lufs <= DB_FLOOR) return 0f
        return -18.0f - lufs
    }

    /** Dynamic Range (DR14 estimation) based on peak-to-RMS ratio over 3s blocks. */
    fun dynamicRange(samples: FloatArray, sampleRate: Int): Float {
        if (samples.isEmpty() || sampleRate <= 0) return 0f
        val blockSize = sampleRate * 3
        if (samples.size < blockSize) return 0f

        val crestFactors = mutableListOf<Float>()
        var offset = 0
        while (offset + blockSize <= samples.size) {
            var maxPeak = 0f
            var sumSquare = 0.0
            for (i in offset until offset + blockSize) {
                val absS = kotlin.math.abs(samples[i])
                if (absS > maxPeak) maxPeak = absS
                sumSquare += absS.toDouble() * absS
            }
            val rms = sqrt(sumSquare / blockSize).toFloat()
            if (rms > 1e-6f && maxPeak > 1e-6f) {
                val cfDb = 20f * log10(maxPeak / rms)
                crestFactors.add(cfDb)
            }
            offset += blockSize
        }

        if (crestFactors.isEmpty()) return 0f
        crestFactors.sortDescending()
        // Top 20% average crest factor
        val topCount = maxOf(1, (crestFactors.size * 0.2).toInt())
        val topAvg = crestFactors.take(topCount).average().toFloat()
        return if (topAvg.isNaN()) 0f else topAvg
    }

    /** Normalised biquad coefficients (a[0] = 1) for the two K-weighting stages. */
    internal class KWeighting(
        val shelfB: DoubleArray,
        val shelfA: DoubleArray,
        val highPassB: DoubleArray,
        val highPassA: DoubleArray
    )

    /**
     * K-weighting pre-filter for any sample rate (libebur128 derivation of the
     * ITU-R BS.1770 analogue prototypes). At 48 kHz this reproduces the published
     * coefficient table exactly.
     */
    internal fun kWeightingCoefficients(sampleRate: Double): KWeighting {
        // Stage 1: high shelf (+4 dB above ~1.7 kHz, head-related).
        var f0 = 1681.974450955533
        val gainDb = 3.999843853973347
        var q = 0.7071752369554196
        var k = tan(PI * f0 / sampleRate)
        val vh = 10.0.pow(gainDb / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val a0 = 1.0 + k / q + k * k
        val shelfB = doubleArrayOf(
            (vh + vb * k / q + k * k) / a0,
            2.0 * (k * k - vh) / a0,
            (vh - vb * k / q + k * k) / a0
        )
        val shelfA = doubleArrayOf(1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)

        // Stage 2: RLB high-pass (~38 Hz).
        f0 = 38.13547087602444
        q = 0.5003270373238773
        k = tan(PI * f0 / sampleRate)
        val d = 1.0 + k / q + k * k
        val highPassB = doubleArrayOf(1.0, -2.0, 1.0)
        val highPassA = doubleArrayOf(1.0, 2.0 * (k * k - 1.0) / d, (1.0 - k / q + k * k) / d)
        return KWeighting(shelfB, shelfA, highPassB, highPassA)
    }
}

