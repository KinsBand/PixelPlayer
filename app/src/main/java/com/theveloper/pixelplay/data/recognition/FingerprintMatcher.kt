package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.data.recognition.RecognitionConstants.COVERAGE_WINDOW_QUANTA
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.MIN_COVERAGE_FRACTION
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.OFFSET_QUANTUM_MS
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.SAMPLE_RATE
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlinx.coroutines.ensureActive

/**
 * One candidate from a fingerprint query.
 *
 * [positionMs] is where in the track the microphone was listening. It falls
 * out of the alignment histogram for free and is what lets the result sheet
 * open synced lyrics on the right line without playing anything.
 *
 * Treat it as approximate: its resolution is one offset quantum (64 ms), and
 * on a track with a repeated chorus the histogram can legitimately peak on a
 * different repeat of the same musical material. [positionConfident] says
 * whether one alignment clearly dominated; when it is false the caller should
 * show lyrics from the top rather than claim a position.
 *
 * [coverageWindows] out of [queryWindows] is how much of the query's duration
 * actually contributed to this alignment — see [FingerprintResult.isConfident].
 */
data class FingerprintCandidate(
    val songId: String,
    val songIndex: Int,
    val score: Int,
    val coverageWindows: Int,
    val queryWindows: Int,
    val positionMs: Long,
    val positionConfident: Boolean
) {
    val coverageFraction: Float
        get() = if (queryWindows == 0) 0f else coverageWindows.toFloat() / queryWindows
}

data class FingerprintResult(
    val candidates: List<FingerprintCandidate>,
    val queryHashCount: Int,
    val querySeconds: Float
) {
    val best: FingerprintCandidate? get() = candidates.firstOrNull()

    /**
     * Two independent gates, both required.
     *
     * **Score** — the tallest alignment bin must clear a length-scaled
     * threshold.
     *
     * **Temporal coverage** — the hashes that voted for that alignment must be
     * spread across most of the query's duration, not clumped in one stretch.
     *
     * Coverage is the gate that actually works, and finding that out took a
     * measurement. On a 40-track synthetic index queried at 10 dB SNR with
     * 8-second excerpts:
     *
     * | | score | coverage (of 8 windows) |
     * | --- | --- | --- |
     * | true matches | min 7, p5 12, median 37, max 117 | **min 4, median 8** |
     * | tracks not in the index | min 6, median 12, max **68** | min 1, median 3, **max 6** |
     *
     * Score alone cannot separate those: one unrelated track reached 68, well
     * above the true-match median. Coverage separates them almost completely,
     * because a spurious alignment peak is built from hashes that happen to
     * agree over a short stretch, while a genuine match agrees throughout.
     *
     * Together at the tuned settings: 81 % true accept, 3 % false accept.
     * Score-only at the same true-accept rate was 16 % false accept.
     *
     * Also tried and rejected, recorded so they are not re-invented:
     *  - **Histogram concentration** (peak ÷ total votes for that song) is
     *    *backwards*: false matches accumulate few total votes, which inflates
     *    their concentration. True median 0.041 vs false p95 0.052.
     *  - **The runner-up ratio** separates poorly (true p5 1.1, false median
     *    1.2). Kept only as a weak tie-break.
     *
     * These numbers come from synthetic audio. They rank the design choices
     * credibly; they do not certify a real-world false-accept rate.
     */
    val isConfident: Boolean
        get() {
            val top = best ?: return false
            if (top.score < RecognitionConstants.minimumScoreFor(querySeconds)) return false
            val needed = ceil(top.queryWindows * MIN_COVERAGE_FRACTION).toInt().coerceAtLeast(2)
            if (top.coverageWindows < needed) return false
            val runnerUp = candidates.getOrNull(1)?.score ?: 0
            if (runnerUp == 0) return true
            return top.score.toFloat() / runnerUp >= RecognitionConstants.MIN_RUNNER_UP_RATIO
        }
}

/**
 * Matches a captured query against the fingerprint index.
 *
 * The scoring is the standard alignment histogram: every (queryHash,
 * indexEntry) pair votes for `referenceOffset - queryOffset`, and a genuine
 * match piles its votes into one bin because the whole excerpt is offset by
 * one constant amount. Noise scatters across bins. On top of that this tracks
 * *where in the query* each vote came from, which is what
 * [FingerprintResult.isConfident] leans on.
 */
class FingerprintMatcher(private val reader: FingerprintIndex.Reader) {

    /**
     * @param samples mono PCM at [SAMPLE_RATE].
     * @param topN how many candidates to return.
     */
    suspend fun match(samples: FloatArray, topN: Int = 5): FingerprintResult {
        val querySeconds = samples.size.toFloat() / SAMPLE_RATE
        val peaks = SpectralFrontEnd.pickPeaks(SpectralFrontEnd.spectrogram(samples))
        val landmarks = LandmarkHasher.hash(peaks, anchorStride = 1)
        if (landmarks.count == 0) {
            return FingerprintResult(emptyList(), 0, querySeconds)
        }

        var maxQueryWindow = 0
        for (i in 0 until landmarks.count) {
            val w = landmarks.offsets[i] / COVERAGE_WINDOW_QUANTA
            if (w > maxQueryWindow) maxQueryWindow = w
        }
        val queryWindows = maxQueryWindow + 1

        // (songIndex, delta) -> votes, and -> bitmask of contributing query
        // windows. Packed into one Long key so the hot loop does not allocate
        // a Pair per vote.
        val votes = HashMap<Long, Int>(landmarks.count * 2)
        val coverage = HashMap<Long, Int>(landmarks.count * 2)
        val scratch = ArrayList<Int>(64)

        for (i in 0 until landmarks.count) {
            if (i and 0x3F == 0) coroutineContext.ensureActive()
            scratch.clear()
            reader.lookup(landmarks.hashes[i], scratch)
            if (scratch.isEmpty()) continue

            val queryOffset = landmarks.offsets[i]
            val windowBit = 1 shl ((queryOffset / COVERAGE_WINDOW_QUANTA).coerceIn(0, 31))
            for (payload in scratch) {
                val songIndex = LandmarkHasher.payloadSongIndex(payload)
                val delta = LandmarkHasher.payloadOffsetQuanta(payload) - queryOffset
                val key = (songIndex.toLong() shl 32) or (delta.toLong() and 0xFFFFFFFFL)
                votes[key] = (votes[key] ?: 0) + 1
                coverage[key] = (coverage[key] ?: 0) or windowBit
            }
        }
        if (votes.isEmpty()) return FingerprintResult(emptyList(), landmarks.count, querySeconds)

        // Best alignment per song, plus the runner-up alignment within that
        // song — that is what tells us whether the position is trustworthy.
        class Best {
            var score = 0
            var delta = 0
            var secondScore = 0
            var mask = 0
        }
        val bestPerSong = HashMap<Int, Best>()
        for ((key, count) in votes) {
            val songIndex = (key ushr 32).toInt()
            val delta = key.toInt()
            val cur = bestPerSong.getOrPut(songIndex) { Best() }
            when {
                count > cur.score -> {
                    cur.secondScore = cur.score
                    cur.score = count
                    cur.delta = delta
                    cur.mask = coverage[key] ?: 0
                }
                count > cur.secondScore -> cur.secondScore = count
            }
        }

        val candidates = bestPerSong.entries
            .sortedByDescending { it.value.score }
            .take(topN)
            .mapNotNull { (songIndex, v) ->
                val songId = reader.songIdAt(songIndex) ?: return@mapNotNull null
                FingerprintCandidate(
                    songId = songId,
                    songIndex = songIndex,
                    score = v.score,
                    coverageWindows = Integer.bitCount(v.mask),
                    queryWindows = queryWindows,
                    positionMs = (v.delta.toLong() * OFFSET_QUANTUM_MS).coerceAtLeast(0L),
                    // One alignment must clearly dominate the others inside the
                    // same song; a repeated chorus produces two comparable peaks.
                    positionConfident = v.secondScore == 0 || v.score >= v.secondScore * 2
                )
            }

        return FingerprintResult(candidates, landmarks.count, querySeconds)
    }
}
