package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.data.recognition.RecognitionConstants.FANOUT
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.MAX_OFFSET_QUANTA
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.OFFSET_BITS
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.OFFSET_QUANTUM_FRAMES
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.TARGET_DF_MAX
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.TARGET_DT_MAX
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.TARGET_DT_MIN
import kotlin.math.abs

/**
 * Landmark pairs for one signal. [hashes] and [offsets] are parallel;
 * [offsets] are anchor times in QUANTISED units (see
 * [RecognitionConstants.OFFSET_QUANTUM_FRAMES]), not raw frames.
 */
class LandmarkSet(
    @JvmField val hashes: IntArray,
    @JvmField val offsets: IntArray,
    @JvmField val count: Int
)

/**
 * Pairs each anchor peak with up to [FANOUT] later peaks inside the target
 * zone and packs (anchorBin, deltaBin, deltaFrames) into a 24-bit key.
 *
 * ## Why 24 bits and not 32
 *
 * The obvious packing puts the 9-bit anchor bin at the top of the word, which
 * sets bit 31 for any anchor above bin 255 and makes the key a NEGATIVE Int.
 * The index is a sorted array searched with a signed binary search, so half
 * the keys would sort below zero and never be found. Keeping the whole key
 * inside 24 bits makes every hash positive by construction.
 *
 * The layout is a bijection of the one the prototype measured, so the
 * collision statistics behind the tuned constants still hold.
 */
object LandmarkHasher {

    private const val DT_SHIFT = 18
    private const val DF_SHIFT = 9
    private const val NINE_BITS = 0x1FF

    /** `dt` occupies 6 bits, so the target zone may not exceed 63 frames. */
    init {
        require(TARGET_DT_MAX <= 63) { "TARGET_DT_MAX must fit in 6 bits" }
    }

    fun pack(anchorBin: Int, deltaBin: Int, deltaFrames: Int): Int =
        (deltaFrames shl DT_SHIFT) or
            ((deltaBin and NINE_BITS) shl DF_SHIFT) or
            (anchorBin and NINE_BITS)

    fun unpackAnchorBin(hash: Int): Int = hash and NINE_BITS
    fun unpackDeltaFrames(hash: Int): Int = hash ushr DT_SHIFT

    /**
     * Builds landmark pairs from [peaks], which must be sorted by frame.
     *
     * [anchorStride] takes every Nth peak as an anchor. It exists as a size
     * lever: stride 2 halves the index (measured 255 MB → 128 MB per 1000
     * tracks) at no cost to top-1 accuracy, but it also halves the margin
     * between true and false match scores, which is the margin that keeps the
     * app from confidently naming the wrong song. Leave it at 1 unless the
     * accept threshold is re-measured for the chosen stride.
     */
    fun hash(peaks: PeakSet, anchorStride: Int = 1): LandmarkSet {
        val n = peaks.count
        if (n == 0) return LandmarkSet(IntArray(0), IntArray(0), 0)

        val capacity = (n / anchorStride + 1) * FANOUT
        val hashes = IntArray(capacity)
        val offsets = IntArray(capacity)
        var out = 0

        var i = 0
        while (i < n) {
            val anchorFrame = peaks.frames[i]
            val anchorBin = peaks.bins[i]
            val quantised = anchorFrame / OFFSET_QUANTUM_FRAMES
            // Tracks longer than MAX_INDEXABLE_MS are truncated rather than
            // wrapped: a wrapped offset would produce confident matches at the
            // wrong position.
            if (quantised > MAX_OFFSET_QUANTA) break

            var paired = 0
            var j = i + 1
            while (j < n && paired < FANOUT) {
                val dt = peaks.frames[j] - anchorFrame
                if (dt > TARGET_DT_MAX) break
                if (dt >= TARGET_DT_MIN) {
                    val df = peaks.bins[j] - anchorBin
                    if (abs(df) <= TARGET_DF_MAX) {
                        hashes[out] = pack(anchorBin, df, dt)
                        offsets[out] = quantised
                        out++
                        paired++
                    }
                }
                j++
            }
            i += anchorStride
        }
        return LandmarkSet(hashes, offsets, out)
    }

    /** Packs a song index and quantised offset into one 32-bit payload. */
    fun packPayload(songIndex: Int, offsetQuanta: Int): Int =
        (songIndex shl OFFSET_BITS) or (offsetQuanta and MAX_OFFSET_QUANTA)

    /** Unsigned shift: song indices stay positive even past 2^17. */
    fun payloadSongIndex(payload: Int): Int = payload ushr OFFSET_BITS

    fun payloadOffsetQuanta(payload: Int): Int = payload and MAX_OFFSET_QUANTA
}
