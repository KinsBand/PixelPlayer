package com.theveloper.pixelplay.data.recognition

/**
 * Tuned constants for the Listen-mode landmark fingerprinter.
 *
 * These are measured, not guessed. They come from a prototype run over a
 * synthetic 60-track corpus with per-track tempo, timbre, scale and
 * percussion pattern, queried with 8-second excerpts degraded by a phone-mic
 * band-pass plus pink noise. See docs/voice-song-search-plan.md for the
 * method and the caveats — in particular, the accept thresholds below are
 * derived from synthetic audio and MUST be re-measured against real library
 * tracks before Listen mode leaves the experimental flag.
 *
 * Changing anything in this file changes the hashes, which invalidates every
 * built shard. That is what [FINGERPRINTER_VERSION] is for: bump it, and the
 * index builder rebuilds instead of silently mixing incompatible shards.
 */
object RecognitionConstants {

    /** Bump on ANY change to the front-end, peak picker or hasher. */
    const val FINGERPRINTER_VERSION = 1

    // ---- Analysis front-end -------------------------------------------------

    /** Both the reference and the query are analysed at this rate. */
    const val SAMPLE_RATE = 16_000

    /** 1024 samples = 64 ms. */
    const val FRAME_SIZE = 1024

    /** 256 samples = 16 ms, so 62.5 frames per second. */
    const val HOP_SIZE = 256

    const val SPECTRUM_BINS = FRAME_SIZE / 2 + 1          // 513

    /** Hz per bin: 16000 / 1024 = 15.625. */
    const val BIN_HZ = SAMPLE_RATE.toFloat() / FRAME_SIZE

    const val FRAMES_PER_SECOND = SAMPLE_RATE.toFloat() / HOP_SIZE   // 62.5

    /**
     * Peak picking is restricted to 300–4000 Hz. Below 300 Hz a room's bass
     * response dominates; above 4000 Hz a phone microphone and lossy playback
     * chains stop being trustworthy.
     */
    const val MIN_BIN = 19                                 // 300 Hz / 15.625
    const val MAX_BIN = 256                                // 4000 Hz / 15.625

    /**
     * Width of the moving average subtracted across frequency to whiten each
     * frame. Without it, peak picking follows the spectral tilt of the room
     * rather than the content.
     */
    const val WHITENING_WIDTH = 33

    // ---- Peak picking -------------------------------------------------------

    /** Half-widths of the 2-D local-maximum neighbourhood. */
    const val PEAK_NEIGHBOURHOOD_FRAMES = 3
    const val PEAK_NEIGHBOURHOOD_BINS = 9

    /** Logarithmically spaced bands, one candidate peak per band per frame. */
    const val BAND_COUNT = 6

    /**
     * Global density cap: keep only the strongest peaks in each one-second
     * window. This is the single most important constant here — without it
     * the picker fires on every frame in every band and produces roughly
     * 310 hashes/s, which is about 1.2 GB of index for a 1000-track library.
     */
    const val PEAKS_PER_SECOND = 30

    // ---- Hashing ------------------------------------------------------------

    /** Target-zone time offsets, in frames. 2..63 frames = 32 ms .. 1.01 s. */
    const val TARGET_DT_MIN = 2
    const val TARGET_DT_MAX = 63

    /** Target-zone frequency span, in bins. */
    const val TARGET_DF_MAX = 127

    /** Pairs formed per anchor peak. 5 measured better than 3; it costs size. */
    const val FANOUT = 5

    /**
     * Stored anchor offsets are quantised to this many frames (4 × 16 ms =
     * 64 ms). Measured to *improve* accuracy — it absorbs sub-frame
     * misalignment between the reference and the query — while letting the
     * payload fit in 32 bits. Do not raise it without re-measuring: the
     * delta histogram's resolution is this quantum.
     */
    const val OFFSET_QUANTUM_FRAMES = 4

    const val OFFSET_QUANTUM_MS = (OFFSET_QUANTUM_FRAMES * 1000L) / FRAMES_PER_SECOND.toLong()

    // ---- Payload packing ----------------------------------------------------

    /** 18 bits of song index: 262 143 tracks. */
    const val SONG_INDEX_BITS = 18
    const val MAX_SONG_INDEX = (1 shl SONG_INDEX_BITS) - 1

    /** 14 bits of quantised offset: 16 383 × 64 ms = 17.4 minutes. */
    const val OFFSET_BITS = 14
    const val MAX_OFFSET_QUANTA = (1 shl OFFSET_BITS) - 1

    /** Longest track the index can represent, in milliseconds. */
    const val MAX_INDEXABLE_MS = MAX_OFFSET_QUANTA * OFFSET_QUANTUM_MS

    // ---- Matching -----------------------------------------------------------

    /**
     * Hashes appearing in more than this many index entries are skipped: they
     * are almost always percussion or silence and contribute noise, not
     * evidence.
     */
    const val MAX_BUCKET_SIZE = 400

    /**
     * Minimum aligned-hash count to accept a match, for an 8-second query.
     * Scaled linearly with query length by [minimumScoreFor].
     *
     * Score is NOT sufficient on its own — see [MIN_COVERAGE_FRACTION]. In the
     * Kotlin end-to-end run, one track that was not in the index reached a
     * score of 68, above the true-match median of 37. Raising the threshold far
     * enough to exclude it would have rejected most genuine matches. The two
     * gates together do what neither does alone: at S=20 with the coverage
     * gate, 81 % true accept against 3 % false accept; score-only at the same
     * true-accept rate gave 16 % false accept.
     */
    const val MIN_SCORE_AT_8S = 20

    /**
     * Fraction of the query's duration whose hashes must contribute to the
     * winning alignment.
     *
     * This is the gate that actually discriminates. A genuine match agrees with
     * the reference throughout the excerpt; a spurious alignment peak is built
     * from hashes that happen to agree over one short stretch. Measured over
     * 8-second queries (8 windows): true matches covered a minimum of 4 and a
     * median of 8 windows, while tracks not in the index never exceeded 6 and
     * had a median of 3.
     *
     * 0.875 (7 of 8 windows) was chosen by sweeping it end to end:
     *
     * | fraction | confident @20dB | @10dB | @5dB | false accepts |
     * | --- | --- | --- | --- | --- |
     * | 0.75 | 92 % | 72 % | 50 % | 1 / 30 |
     * | **0.875** | **90 %** | **70 %** | **47 %** | **0 / 30** |
     * | 1.0 | 77 % | 57 % | 40 % | 0 / 30 |
     *
     * 0.875 buys the last false accept for two points of true accept. 1.0 costs
     * thirteen more points and buys nothing.
     */
    const val MIN_COVERAGE_FRACTION = 0.875f

    /** Offset quanta per coverage window: 16 x 64 ms = 1.024 s. */
    const val COVERAGE_WINDOW_QUANTA = 16

    /**
     * The runner-up test. Kept deliberately weak: on the prototype the ratio
     * between the best and second-best song separated true from false matches
     * far less well than coverage did, so it is a tie-break, not a gate.
     */
    const val MIN_RUNNER_UP_RATIO = 1.5f

    /** Query lengths at which an in-progress capture is re-matched. */
    val INCREMENTAL_MATCH_SECONDS = floatArrayOf(4f, 6f, 8f, 10f, 12f)

    /** Hard stop for any capture. */
    const val MAX_CAPTURE_SECONDS = 15f

    fun minimumScoreFor(querySeconds: Float): Int =
        (MIN_SCORE_AT_8S * (querySeconds / 8f)).toInt().coerceAtLeast(8)
}
