package com.theveloper.pixelplay.data.recognition.livesync

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small audio features for lining a microphone clip up with a song's own audio.
 *
 * Two features per 20 ms frame (16 kHz mono):
 *  - chroma: 12 pitch classes, mean-centred and normalised, so it follows the harmony and
 *    ignores loudness / room colour;
 *  - onset strength: positive change in 8 log-spaced bands, which follows the rhythm.
 * Both survive a phone speaker, a room and a phone mic far better than raw waveforms.
 */
internal class AudioFeatures(val chroma: Array<FloatArray>, val onset: FloatArray) {
    val frames: Int get() = onset.size
}

internal object AudioFingerprint {
    const val SAMPLE_RATE = 16_000
    const val HOP = 320 // 20 ms
    const val FRAME_MS = 1000.0 * HOP / SAMPLE_RATE
    private const val N = 1024 // 64 ms window: fast enough to fingerprint a whole song in about a second
    private const val MIN_HZ = 110.0
    private const val MAX_HZ = 5_000.0
    private const val BANDS = 8
    private const val FLOOR_HALF_WIDTH = 15

    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
    private val pitchClassOfBin = IntArray(N / 2) { k ->
        val f = k.toDouble() * SAMPLE_RATE / N
        if (f < MIN_HZ || f > MAX_HZ) -1 else ((12 * log2(f / 440.0) + 69).roundToInt() % 12 + 12) % 12
    }
    private val bandOfBin = IntArray(N / 2) { k ->
        val f = k.toDouble() * SAMPLE_RATE / N
        if (f < MIN_HZ || f > 6_000.0) -1
        else ((ln(f / MIN_HZ) / ln(6_000.0 / MIN_HZ)) * BANDS).toInt().coerceIn(0, BANDS - 1)
    }

    fun features(samples: FloatArray): AudioFeatures {
        val count = if (samples.size < N) 0 else (samples.size - N) / HOP + 1
        val chroma = Array(count) { FloatArray(12) }
        val onset = FloatArray(count)
        val re = DoubleArray(N)
        val im = DoubleArray(N)
        val bands = DoubleArray(BANDS)
        val logMag = DoubleArray(N / 2)
        val floorPrefix = DoubleArray(N / 2 + 1)
        val previous = DoubleArray(BANDS)
        for (t in 0 until count) {
            val start = t * HOP
            for (i in 0 until N) {
                re[i] = (samples[start + i] * window[i]).toDouble()
                im[i] = 0.0
            }
            fft(re, im)
            bands.fill(0.0)
            val c = chroma[t]
            for (k in 0 until N / 2) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                logMag[k] = ln(1.0 + 100.0 * mag)
                val b = bandOfBin[k]
                if (b >= 0) bands[b] += mag
            }
            // Only peaks above the local spectral floor count towards pitch: room noise spreads
            // over many high bins and would otherwise drown the notes.
            floorPrefix[0] = 0.0
            for (k in 0 until N / 2) floorPrefix[k + 1] = floorPrefix[k] + logMag[k]
            for (k in 1 until N / 2) {
                val pc = pitchClassOfBin[k]
                if (pc < 0) continue
                val lo = maxOf(0, k - FLOOR_HALF_WIDTH)
                val hi = minOf(N / 2, k + FLOOR_HALF_WIDTH + 1)
                val floor = (floorPrefix[hi] - floorPrefix[lo]) / (hi - lo)
                val peak = logMag[k] - floor
                if (peak > 0) c[pc] += peak.toFloat()
            }
            // Centre + normalise the chroma (cosine of centred vectors).
            val mean = c.average().toFloat()
            var norm = 0f
            for (i in 0 until 12) { c[i] -= mean; norm += c[i] * c[i] }
            norm = sqrt(norm)
            if (norm > 1e-6f) for (i in 0 until 12) c[i] /= norm
            var flux = 0.0
            for (b in 0 until BANDS) {
                val e = ln(1.0 + bands[b])
                if (t > 0) flux += max(0.0, e - previous[b])
                previous[b] = e
            }
            onset[t] = flux.toFloat()
        }
        return AudioFeatures(chroma, onset)
    }

    /** Root-mean-square level, to tell music from silence. */
    fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s * s
        return sqrt(sum / samples.size).toFloat()
    }

    /** In-place radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val aRe = re[i + k]; val aIm = im[i + k]
                    val bRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val bIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = aRe + bRe; im[i + k] = aIm + bIm
                    re[i + k + len / 2] = aRe - bRe; im[i + k + len / 2] = aIm - bIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Where a clip sits in a song, and how sure that is. */
internal data class AlignmentMatch(
    /** Song position (ms) of the clip's first sample. */
    val offsetMs: Double,
    /** Best combined score (0..1-ish). */
    val score: Double,
    /** Standard deviations above the average offset: how much the best one stands out. */
    val zScore: Double,
    /** Best score anywhere more than 1.5 s away from the match, as a fraction of [score]. */
    val runnerUpRatio: Double,
) {
    /**
     * Strict on purpose: a wrong position is worse than none. The match must stand far out
     * from every other place in the song and clearly beat the next best one.
     */
    val isConfident: Boolean get() = score >= 0.20 && zScore >= 6.0 && runnerUpRatio <= 0.85
}

internal object AudioAligner {
    private const val CHROMA_WEIGHT = 0.7
    private const val ONSET_WEIGHT = 0.3

    /** The best place anywhere in the song. */
    fun align(clip: AudioFeatures, song: AudioFeatures): AlignmentMatch? {
        val scores = scores(clip, song) ?: return null
        var best = 0
        for (o in 1 until scores.size) if (scores[o] > scores[best]) best = o
        return matchAt(scores, best)
    }

    /**
     * The best place within [windowMs] of [expectedMs] (rechecks while live). A chorus that
     * repeats elsewhere doesn't matter here: only the neighbourhood of where the song should
     * be is considered, and that spot must still stand far out from the song as a whole.
     */
    fun alignNear(clip: AudioFeatures, song: AudioFeatures, expectedMs: Double, windowMs: Double): AlignmentMatch? {
        val scores = scores(clip, song) ?: return null
        val center = (expectedMs / AudioFingerprint.FRAME_MS).toInt()
        val radius = (windowMs / AudioFingerprint.FRAME_MS).toInt()
        val lo = (center - radius).coerceAtLeast(0)
        val hi = (center + radius).coerceAtMost(scores.size - 1)
        if (lo > hi) return null
        var best = lo
        for (o in lo..hi) if (scores[o] > scores[best]) best = o
        val match = matchAt(scores, best)
        // Locally there is no runner-up to beat; it only has to stand out from the whole song.
        return match.copy(runnerUpRatio = 0.0)
    }

    private fun matchAt(scores: DoubleArray, best: Int): AlignmentMatch {
        val positions = scores.size
        val mean = scores.average()
        var sq = 0.0
        for (s in scores) sq += (s - mean) * (s - mean)
        val std = sqrt(sq / positions).coerceAtLeast(1e-9)
        val exclusion = (1_500.0 / AudioFingerprint.FRAME_MS).toInt()
        var runnerUp = Double.NEGATIVE_INFINITY
        for (o in 0 until positions) if (kotlin.math.abs(o - best) > exclusion && scores[o] > runnerUp) runnerUp = scores[o]
        // Sub-frame refinement (parabola through the peak and its neighbours).
        var refined = best.toDouble()
        if (best in 1 until positions - 1) {
            val l = scores[best - 1]; val c = scores[best]; val rr = scores[best + 1]
            val denom = l - 2 * c + rr
            if (denom < 0) refined += (0.5 * (l - rr) / denom).coerceIn(-0.5, 0.5)
        }
        val bestScore = scores[best]
        return AlignmentMatch(
            offsetMs = refined * AudioFingerprint.FRAME_MS,
            score = bestScore,
            zScore = (bestScore - mean) / std,
            runnerUpRatio = if (bestScore > 0 && runnerUp.isFinite()) (runnerUp / bestScore).coerceAtLeast(0.0) else 0.0,
        )
    }

    private fun scores(clip: AudioFeatures, song: AudioFeatures): DoubleArray? {
        val q = clip.frames
        val r = song.frames
        if (q < 100 || r <= q) return null
        val positions = r - q + 1
        val scores = DoubleArray(positions)
        // Onset correlation: z-normalised clip against each window of the song.
        val qOnset = zNormalise(clip.onset)
        val prefix = DoubleArray(r + 1)
        val prefixSq = DoubleArray(r + 1)
        for (i in 0 until r) {
            prefix[i + 1] = prefix[i] + song.onset[i]
            prefixSq[i + 1] = prefixSq[i] + song.onset[i].toDouble() * song.onset[i]
        }
        for (o in 0 until positions) {
            var chroma = 0.0
            var onsetDot = 0.0
            for (i in 0 until q) {
                val a = clip.chroma[i]
                val b = song.chroma[o + i]
                var d = 0f
                for (p in 0 until 12) d += a[p] * b[p]
                chroma += d
                onsetDot += qOnset[i] * song.onset[o + i]
            }
            val mean = (prefix[o + q] - prefix[o]) / q
            val variance = ((prefixSq[o + q] - prefixSq[o]) / q - mean * mean).coerceAtLeast(1e-9)
            val onsetCorr = (onsetDot / q) / sqrt(variance)
            scores[o] = CHROMA_WEIGHT * (chroma / q) + ONSET_WEIGHT * onsetCorr
        }
        return scores
    }

    private fun zNormalise(values: FloatArray): DoubleArray {
        val mean = values.average()
        var sq = 0.0
        for (v in values) sq += (v - mean) * (v - mean)
        val std = sqrt(sq / values.size).coerceAtLeast(1e-9)
        return DoubleArray(values.size) { (values[it] - mean) / std }
    }
}
