package com.theveloper.pixelplay.data.lyrics.align

import kotlin.math.exp
import kotlin.math.max

/**
 * Frame-level log-probabilities from a CTC acoustic model, row-major (`frames × vocabSize`).
 * Frame `i` starts at `i × frameMs` of the song.
 */
class CtcEmissions(
    val logProbs: FloatArray,
    val frames: Int,
    val vocabSize: Int,
    val frameMs: Double
) {
    init {
        require(logProbs.size >= frames * vocabSize) { "logProbs too small for $frames frames" }
    }

    fun at(frame: Int, token: Int): Float = logProbs[frame * vocabSize + token]

    fun frameAt(ms: Double): Int = (ms / frameMs).toInt()

    fun msAt(frame: Int): Double = frame * frameMs

    /** Best log-probability of any token at [frame]. */
    fun best(frame: Int): Float {
        val base = frame * vocabSize
        var m = logProbs[base]
        for (v in 1 until vocabSize) if (logProbs[base + v] > m) m = logProbs[base + v]
        return m
    }
}

/**
 * CTC forced alignment: finds when each token of a known transcript is sung inside a window of
 * frames, by a Viterbi pass over the usual blank-interleaved CTC states.
 *
 * The window is allowed to hold audio that isn't part of the transcript before and after it
 * (the tail of the previous line, an intro, the start of the next line): two "star" states
 * absorb those frames. A star frame scores the best token at that frame plus [starPenalty]
 * (or silence, slightly penalised), so frames where the transcript really is sung are always
 * cheaper to explain with the transcript, and the rest fall to the stars.
 *
 * Pure Kotlin; memory is one byte per (frame, state) for back-pointers, about 100 KB for a
 * 15 s line of 60 letters.
 */
object CtcForcedAligner {

    /** Where one transcript token was sung, in absolute frames (inclusive). */
    class TokenSpan(
        val firstFrame: Int,
        val lastFrame: Int,
        /** Model probability of the token at its best frame, 0..1. */
        val confidence: Float
    )

    class Result(
        /** One span per transcript token, in order. */
        val spans: List<TokenSpan>,
        /** Frame of the transcript's first token. */
        val startFrame: Int,
        /** First frame after the transcript (where the trailing star begins, or the window end). */
        val endFrame: Int,
        /**
         * For each token, the first frame after it that belongs to another token or the trailing
         * star: the token "holds" through the blank frames up to there.
         */
        val holdUntilFrame: IntArray
    )

    private const val NEG = Double.NEGATIVE_INFINITY

    // Back-pointer codes.
    private const val FROM_SELF: Byte = 0
    private const val FROM_PREV: Byte = 1
    private const val FROM_SKIP: Byte = 2
    private const val FROM_STAR: Byte = 3
    private const val FROM_END_BLANK: Byte = 4
    private const val FROM_END_TOKEN: Byte = 5

    /**
     * Aligns [labels] inside frames `[from, to)` of [emissions]. Returns null when the window is
     * too short to hold the transcript or nothing scores.
     */
    fun align(
        emissions: CtcEmissions,
        from: Int,
        to: Int,
        labels: IntArray,
        blank: Int,
        starPenalty: Float = -0.6f,
        silencePenalty: Float = -0.05f
    ): Result? {
        val start = from.coerceAtLeast(0)
        val end = to.coerceAtMost(emissions.frames)
        val frames = end - start
        val l = labels.size
        if (l == 0 || frames <= 0) return null
        // CTC needs one frame per token, plus a blank between equal neighbours.
        var minFrames = l
        for (i in 1 until l) if (labels[i] == labels[i - 1]) minFrames++
        if (frames < minFrames) return null

        val s = 2 * l + 1                 // blank-interleaved states
        val width = s + 2                 // + leading star (0) and trailing star (s + 1)
        val post = s + 1
        val back = ByteArray(frames * width)
        var prev = DoubleArray(width) { NEG }
        var cur = DoubleArray(width)

        fun emit(frame: Int, state: Int): Double {
            val k = state - 1
            val token = if (k % 2 == 0) blank else labels[k / 2]
            return emissions.at(frame, token).toDouble()
        }

        // A star frame is either someone else's sound (best token, minus starPenalty) or silence
        // (blank, minus a sliver so a held last note stays with the line rather than the star).
        fun star(frame: Int): Double =
            max(emissions.best(frame) + starPenalty, emissions.at(frame, blank) + silencePenalty).toDouble()

        // t = 0
        run {
            val f = start
            prev[0] = star(f)
            prev[1] = emit(f, 1)
            prev[2] = emit(f, 2)
            back[0 * width + 1] = FROM_STAR
            back[0 * width + 2] = FROM_STAR
        }

        for (t in 1 until frames) {
            val f = start + t
            val row = t * width
            val star = star(f)
            java.util.Arrays.fill(cur, NEG)

            cur[0] = prev[0] + star
            back[row] = FROM_SELF

            for (state in 1..s) {
                val k = state - 1
                var best = prev[state]
                var code = FROM_SELF
                if (k >= 1 && prev[state - 1] > best) { best = prev[state - 1]; code = FROM_PREV }
                if (k % 2 == 1 && k >= 3 && labels[k / 2] != labels[k / 2 - 1] && prev[state - 2] > best) {
                    best = prev[state - 2]; code = FROM_SKIP
                }
                if (k <= 1 && prev[0] > best) { best = prev[0]; code = FROM_STAR }
                if (best == NEG) continue
                cur[state] = best + emit(f, state)
                back[row + state] = code
            }

            // Trailing star: stay, or leave the transcript from its last blank / last token.
            var bestPost = prev[post]
            var postCode = FROM_SELF
            if (prev[s] > bestPost) { bestPost = prev[s]; postCode = FROM_END_BLANK }
            if (prev[s - 1] > bestPost) { bestPost = prev[s - 1]; postCode = FROM_END_TOKEN }
            if (bestPost != NEG) {
                cur[post] = bestPost + star
                back[row + post] = postCode
            }

            val swap = prev; prev = cur; cur = swap
        }

        // Finish in the last blank, the last token, or the trailing star.
        var state = post
        var score = prev[post]
        if (prev[s] > score) { score = prev[s]; state = s }
        if (prev[s - 1] > score) { score = prev[s - 1]; state = s - 1 }
        if (score == NEG || score.isNaN()) return null

        // Backtrack into a per-frame state path.
        val path = IntArray(frames)
        for (t in frames - 1 downTo 0) {
            path[t] = state
            if (t == 0) break
            state = when (back[t * width + state]) {
                FROM_SELF -> state
                FROM_PREV -> state - 1
                FROM_SKIP -> state - 2
                FROM_STAR -> 0
                FROM_END_BLANK -> s
                FROM_END_TOKEN -> s - 1
                else -> return null
            }
        }
        if (path[0] != 0 && path[0] != 1 && path[0] != 2) return null

        val first = IntArray(l) { -1 }
        val last = IntArray(l) { -1 }
        val peak = FloatArray(l) { Float.NEGATIVE_INFINITY }
        var endFrame = end
        for (t in 0 until frames) {
            val st = path[t]
            if (st == post) { if (endFrame == end) endFrame = start + t; continue }
            if (st == 0) continue
            val k = st - 1
            if (k % 2 == 1) {
                val i = k / 2
                if (first[i] < 0) first[i] = start + t
                last[i] = start + t
                val lp = emissions.at(start + t, labels[i])
                if (lp > peak[i]) peak[i] = lp
            }
        }
        if (first.any { it < 0 }) return null
        val startFrame = first[0]

        val hold = IntArray(l)
        for (i in 0 until l) hold[i] = if (i + 1 < l) first[i + 1] else endFrame
        val spans = List(l) { i -> TokenSpan(first[i], last[i], exp(peak[i].toDouble()).toFloat()) }
        return Result(spans, startFrame, endFrame, hold)
    }
}
