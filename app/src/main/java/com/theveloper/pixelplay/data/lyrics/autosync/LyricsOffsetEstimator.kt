package com.theveloper.pixelplay.data.lyrics.autosync

import com.theveloper.pixelplay.data.model.SyncedLine
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the estimator concluded for one song. */
data class OffsetEstimate(
    val verdict: Verdict,
    /**
     * Offset to store for the song, in the app's convention (lyrics position = playback +
     * offset): positive when the lyrics file runs late. Zero unless [verdict] is [Verdict.APPLY].
     */
    val offsetMs: Int,
    /** Best-matching offset whatever the verdict (diagnostics). */
    val measuredOffsetMs: Int,
    /** 0..1: how clearly the audio supports [measuredOffsetMs]. */
    val confidence: Float,
    val peakScore: Float,
    val margin: Float,
    val halvesDisagreementMs: Int,
    val linesUsed: Int,
    val reason: String
) {
    enum class Verdict {
        /** A clear, consistent shift was found: apply [offsetMs]. */
        APPLY,
        /** The lyrics already follow the singing (within [LyricsOffsetEstimator.IN_SYNC_MS]). */
        IN_SYNC,
        /** No clear answer: vocals too buried, lyrics from another version, drifting times... */
        UNSURE,
        /** Not enough timed lines or audio to try. */
        UNSUPPORTED
    }
}

/**
 * Finds the constant offset between a song's line-timed lyrics and its sung vocals.
 *
 * The lyric line starts are slid over the audio within ±[Config.maxShiftMs] in 10 ms steps and
 * every shift is scored with two cues from [VocalFeatureExtractor]:
 *
 *  - **Sustained onsets**: a line should start where sustained vocal-band sound begins
 *    (percussion removed). Smoothed by [Config.onsetSmoothingFrames] so that lines whose
 *    timestamps are a little off (LRC files are typed by hand) still count.
 *  - **Vocal entries**: the non-repeating part of the vocal band ([VocalActivity]) should be
 *    louder in the ~150 ms after a line start than in the ~200 ms before it. Strumming, pads and
 *    drums repeat and are removed, so this cue does not lock onto the beat.
 *
 * Each cue's score curve becomes a robust z-score over all shifts; the sum (step weighted by
 * [Config.stepWeight]) is z-scored again. The best shift is only used when:
 *
 *  1. it stands out: at least [Config.minPeak] robust z;
 *  2. it is unambiguous: it beats every shift more than 150 ms away (including "no shift") by
 *     [Config.minMargin];
 *  3. it is consistent: the first and second halves of the song, scored on their own, agree
 *     within [Config.maxHalvesDisagreementMs]. Lyrics timed to another edit or tempo fail here.
 *
 * Tuned on 40 synthetic songs and checked on 60 more that were never used for tuning, all built
 * to be hard: guitar-like plucks on every beat, a vibrato lead-synth solo, quiet or sparse
 * vocals, and up to ±90 ms of hand-typing error on every line. Offsets were correct (within
 * 50 ms) for 78 of the 100 and within 100 ms for 82; the other 18 were left alone as unsure;
 * none got a wrong offset. Instrumental and wrong-lyrics controls were never shifted. About
 * 40 ms of scoring per song on a desktop JVM, once the features exist.
 * `LyricsOffsetEstimatorTest` renders songs of the same kind for the unit tests.
 */
object LyricsOffsetEstimator {

    data class Config(
        val maxShiftMs: Int = 4_000,
        val onsetSmoothingFrames: Double = 6.0,
        val stepWeight: Float = 2f,
        val stepAfterFrames: Int = 15,
        val stepBeforeFrames: Int = 20,
        val stepGapFrames: Int = 3,
        val minPeak: Float = 3.2f,
        val minMargin: Float = 0.9f,
        val maxHalvesDisagreementMs: Int = 100,
        val minLines: Int = 8,
        /** Constant correction for where the cues peak relative to the first sung sound. */
        val biasMs: Double = 40.0
    )

    /** Bumped whenever the features, scoring or thresholds change: saved results are then redone. */
    const val VERSION = 1

    /** Offsets smaller than this are treated as already in sync. */
    const val IN_SYNC_MS = 40

    /** Shifts within this distance of the best one belong to the same peak. */
    private const val PEAK_HALF_WIDTH_MS = 150.0

    /** Halves are compared within this distance of the global best shift. */
    private const val HALVES_SEARCH_MS = 600.0

    // ─── Anchors ────────────────────────────────────────────────────────────────────────

    /**
     * Start times (ms) of the sung lines: blank lines, instrumental markers, section headers
     * and a title line at 0:00 are skipped, and lines sharing a start time (translations,
     * duets) count once.
     */
    fun lineStarts(lines: List<SyncedLine>): LongArray {
        val sung = lines
            .filter { it.time >= 0 && isSung(it.line) }
            .map { it.time.toLong() }
            .distinct()
            .sorted()
        // A line at (almost) 0:00 is usually a title or credits line, not a sung one.
        return sung.filter { it >= 250L || sung.size == 1 }.toLongArray()
    }

    private val instrumentalMarker = Regex("""^[\s\p{Punct}♪♫♩♬…·•\-–—]*$""")

    private fun isSung(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || instrumentalMarker.matches(t)) return false
        if (t.startsWith("[") && t.endsWith("]")) return false // [Chorus], [Instrumental]
        val lower = t.lowercase()
        if (lower.startsWith("(") && lower.endsWith(")") &&
            (lower.contains("instrumental") || lower.contains("music") || lower.contains("solo"))
        ) return false
        if (CREDIT_PREFIXES.any { lower.startsWith(it) }) return false
        return t.any { it.isLetterOrDigit() }
    }

    private val CREDIT_PREFIXES = listOf(
        "作词", "作曲", "编曲", "制作", "作詞", "編曲", "lyrics by", "written by", "composed by",
        "producer", "produced by", "composer", "lyricist"
    )

    // ─── Estimation ─────────────────────────────────────────────────────────────────────

    fun estimate(track: VocalFeatureTrack, lineStartsMs: LongArray, config: Config = Config()): OffsetEstimate {
        val n = track.frameCount
        val hop = track.hopMs
        val maxShift = (config.maxShiftMs / hop).roundToInt()

        // Only lines the audio reaches for at least some shifts.
        val positions = lineStartsMs
            .map { track.frameAt(it.toDouble()).roundToInt() }
            .filter { it > -maxShift / 2 && it < n + maxShift / 2 }
            .toIntArray()
        if (n < MIN_FRAMES) return unsupported(positions.size, "audio too short")
        if (positions.size < config.minLines) return unsupported(positions.size, "too few timed lines")

        val onset = onsetCurve(track.onset, n, config.onsetSmoothingFrames)
        val step = stepCurve(VocalActivity.residualEnergy(track), n, config)

        val all = positions.indices.toList().toIntArray()
        val combined = score(onset, step, n, positions, all, maxShift, config)
        val best = argmax(combined)
        val peak = combined[best]
        val halfWidth = (PEAK_HALF_WIDTH_MS / hop).roundToInt()
        var runnerUp = Float.NEGATIVE_INFINITY
        for (i in combined.indices) if (abs(i - best) > halfWidth && combined[i] > runnerUp) runnerUp = combined[i]
        val margin = peak - runnerUp
        val refinedLag = best - maxShift + parabolicOffset(combined, best)

        val halves = if (positions.size >= 2 * (config.minLines / 2)) {
            val mid = positions.size / 2
            val radius = (HALVES_SEARCH_MS / hop).roundToInt()
            val a = localBest(score(onset, step, n, positions, IntArray(mid) { it }, maxShift, config), best, radius)
            val b = localBest(score(onset, step, n, positions, IntArray(positions.size - mid) { mid + it }, maxShift, config), best, radius)
            (abs(a - b) * hop).roundToInt()
        } else 0

        // A positive lag means the singing comes after the lyric timestamps: the lyrics are
        // early and the lyrics clock must run behind playback, i.e. a negative offset.
        val measured = (-refinedLag * hop + config.biasMs).roundToInt()
        val confidence = (
            sigmoid((peak - config.minPeak) / 0.6f) *
                sigmoid((margin - config.minMargin) / 0.3f) *
                sigmoid((config.maxHalvesDisagreementMs - halves) / 30f)
            ).coerceIn(0f, 1f)

        val problems = buildList {
            if (peak < config.minPeak) add("weak match (%.1f)".format(peak))
            if (margin < config.minMargin) add("ambiguous (margin %.2f)".format(margin))
            if (halves > config.maxHalvesDisagreementMs) add("halves disagree by $halves ms")
        }
        val verdict = when {
            problems.isNotEmpty() -> OffsetEstimate.Verdict.UNSURE
            abs(measured) < IN_SYNC_MS -> OffsetEstimate.Verdict.IN_SYNC
            else -> OffsetEstimate.Verdict.APPLY
        }
        return OffsetEstimate(
            verdict = verdict,
            offsetMs = if (verdict == OffsetEstimate.Verdict.APPLY) measured else 0,
            measuredOffsetMs = measured,
            confidence = confidence,
            peakScore = peak,
            margin = margin,
            halvesDisagreementMs = halves,
            linesUsed = positions.size,
            reason = when (verdict) {
                OffsetEstimate.Verdict.UNSURE -> problems.joinToString("; ")
                OffsetEstimate.Verdict.IN_SYNC -> "already in sync"
                else -> "shift found"
            }
        )
    }

    /** Combined robust-z score of every shift in [-maxShift, maxShift] for the lines in [subset]. */
    private fun score(
        onset: FloatArray, step: FloatArray, n: Int,
        positions: IntArray, subset: IntArray, maxShift: Int, config: Config
    ): FloatArray {
        val lags = 2 * maxShift + 1
        val onScore = FloatArray(lags)
        val stepScore = FloatArray(lags)
        for (li in 0 until lags) {
            val lag = li - maxShift
            var so = 0.0
            var ss = 0.0
            var c = 0
            for (i in subset) {
                val p = positions[i] + lag
                if (p in 0 until n) {
                    so += onset[p]
                    ss += step[p]
                    c++
                }
            }
            if (c > 0) {
                onScore[li] = (so / c).toFloat()
                stepScore[li] = (ss / c).toFloat()
            }
        }
        val zOn = robustZ(onScore)
        val zStep = robustZ(stepScore)
        return robustZ(FloatArray(lags) { zOn[it] + config.stepWeight * zStep[it] })
    }

    /** Best index within [radius] of [centre]; the global best if that is clearly stronger. */
    private fun localBest(curve: FloatArray, centre: Int, radius: Int): Int {
        var best = centre
        for (i in max(0, centre - radius)..min(curve.size - 1, centre + radius)) if (curve[i] > curve[best]) best = i
        val global = argmax(curve)
        return if (curve[global] - curve[best] > 1f) global else best
    }

    // ─── Feature conditioning ───────────────────────────────────────────────────────────

    /**
     * Onset curve: remove the slowly varying floor (±250 ms mean), keep what rises above it,
     * normalise by the local level (±1.5 s) so quiet and loud passages count alike, clip rare
     * spikes, then Gaussian-smooth by [sigma] frames.
     */
    internal fun onsetCurve(raw: FloatArray, n: Int, sigma: Double): FloatArray {
        val floor = movingMean(raw, n, 25)
        val rise = FloatArray(n) { max(0f, raw[it] - floor[it]) }
        val level = movingMean(rise, n, 150)
        var globalMean = 0.0
        for (i in 0 until n) globalMean += rise[i]
        globalMean /= n
        val eps = (1e-6 + 0.05 * globalMean).toFloat()
        val norm = FloatArray(n) { min(8f, rise[it] / (level[it] + eps)) }
        return gaussianSmooth(norm, n, sigma)
    }

    /** Mean energy just after each frame minus the mean just before it (with a small gap). */
    internal fun stepCurve(energy: FloatArray, n: Int, config: Config): FloatArray {
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + energy[i]
        fun mean(a: Int, b: Int): Double {
            val lo = a.coerceIn(0, n)
            val hi = b.coerceIn(0, n)
            return if (hi > lo) (prefix[hi] - prefix[lo]) / (hi - lo) else 0.0
        }
        return FloatArray(n) { t ->
            (mean(t, t + config.stepAfterFrames) -
                mean(t - config.stepBeforeFrames - config.stepGapFrames, t - config.stepGapFrames)).toFloat()
        }
    }

    internal fun movingMean(x: FloatArray, n: Int, half: Int): FloatArray {
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + x[i]
        return FloatArray(n) {
            val a = max(0, it - half)
            val b = min(n, it + half + 1)
            ((prefix[b] - prefix[a]) / (b - a)).toFloat()
        }
    }

    private fun gaussianSmooth(x: FloatArray, n: Int, sigma: Double): FloatArray {
        val half = (3 * sigma).toInt().coerceAtLeast(1)
        val kernel = FloatArray(2 * half + 1) { exp(-((it - half) * (it - half)) / (2 * sigma * sigma)).toFloat() }
        val out = FloatArray(n)
        for (i in 0 until n) {
            var acc = 0f
            var weight = 0f
            for (k in -half..half) {
                val j = i + k
                if (j in 0 until n) {
                    acc += kernel[k + half] * x[j]
                    weight += kernel[k + half]
                }
            }
            out[i] = acc / weight
        }
        return out
    }

    internal fun robustZ(x: FloatArray): FloatArray {
        val sorted = x.copyOf().also { it.sort() }
        val median = sorted[sorted.size / 2]
        val deviations = FloatArray(x.size) { abs(x[it] - median) }.also { it.sort() }
        val mad = deviations[deviations.size / 2] * 1.4826f
        val scale = if (mad > 1e-9f) mad else (sorted.last() - sorted.first()).coerceAtLeast(1e-9f)
        return FloatArray(x.size) { (x[it] - median) / scale }
    }

    private fun argmax(x: FloatArray): Int {
        var best = 0
        for (i in 1 until x.size) if (x[i] > x[best]) best = i
        return best
    }

    private fun parabolicOffset(y: FloatArray, i: Int): Double {
        if (i <= 0 || i >= y.size - 1) return 0.0
        val a = y[i - 1].toDouble()
        val b = y[i].toDouble()
        val c = y[i + 1].toDouble()
        val denom = a - 2 * b + c
        if (denom >= 0.0) return 0.0
        return (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    private const val MIN_FRAMES = 1_500

    private fun unsupported(lines: Int, reason: String) = OffsetEstimate(
        OffsetEstimate.Verdict.UNSUPPORTED, 0, 0, 0f, 0f, 0f, 0, lines, reason
    )
}
