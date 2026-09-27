package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Musical key estimation via a chromagram correlated against
 * Krumhansl-Schmuckler key profiles. Pure Kotlin.
 *
 *  - STFT window 4096, hop 2048 at the working sample rate.
 *  - FFT bins between ~55 Hz and ~5 kHz are mapped onto 12 pitch classes
 *    (nearest semitone, 0 = C) and accumulated per frame.
 *  - The per-frame chromas are combined by MEDIAN across frames (more
 *    robust to percussion than summing), then L2-normalized.
 *  - Pearson correlation against all 24 rotations of the KS major/minor
 *    profiles; the best wins. Degenerate (silent / atonal / noise) input
 *    yields null.
 *
 * Both profiles are stored root-anchored: index 0 is the tonic, so the
 * minor profile (6.33 at the tonic, 5.38 at the minor third, 4.75 at the
 * fifth) matches the classic A-minor-anchored listing.
 */
object KeyDetector {

    private const val WINDOW_SIZE = 4096
    private const val HOP_SIZE = 2048
    private const val MIN_FREQ_HZ = 55.0
    private const val MAX_FREQ_HZ = 5000.0
    private const val MIN_CORRELATION = 0.2
    private const val MIN_CHROMA_SPREAD = 0.05

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val MAJOR_PROFILE = doubleArrayOf(
        6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88
    )
    private val MINOR_PROFILE = doubleArrayOf(
        6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17
    )

    // Standard Camelot wheel, indexed by pitch class (0 = C).
    private val MAJOR_CAMELOT = arrayOf("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")
    private val MINOR_CAMELOT = arrayOf("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")

    data class KeyResult(val key: String, val camelot: String)

    fun detectKey(pcm: FloatArray, sampleRate: Int): KeyResult? {
        if (sampleRate <= 0 || pcm.size < WINDOW_SIZE * 2) return null
        val frameCount = (pcm.size - WINDOW_SIZE) / HOP_SIZE + 1
        if (frameCount < 3) return null

        // Precompute FFT bin -> pitch class (-1 = outside the analyzed band).
        val binPc = IntArray(WINDOW_SIZE / 2)
        for (k in binPc.indices) {
            val freq = k * sampleRate.toDouble() / WINDOW_SIZE
            binPc[k] = if (freq < MIN_FREQ_HZ || freq > MAX_FREQ_HZ) {
                -1
            } else {
                val midi = 69 + (12 * log2(freq / 440.0)).roundToInt()
                ((midi % 12) + 12) % 12
            }
        }

        val frame = FloatArray(WINDOW_SIZE)
        val frames = Array(frameCount) { DoubleArray(12) }
        for (fr in 0 until frameCount) {
            val offset = fr * HOP_SIZE
            pcm.copyInto(frame, 0, offset, offset + WINDOW_SIZE)
            val mag = Fft.magnitudes(frame)
            val chroma = frames[fr]
            for (k in mag.indices) {
                val pc = binPc[k]
                if (pc >= 0) chroma[pc] += mag[k]
            }
        }

        // Median across frames per pitch class.
        val chroma = DoubleArray(12)
        val column = DoubleArray(frameCount)
        for (pc in 0 until 12) {
            for (fr in 0 until frameCount) column[fr] = frames[fr][pc]
            column.sort()
            chroma[pc] = column[frameCount / 2]
        }

        if (chroma.sum() <= 1e-9) return null
        val norm = sqrt(chroma.sumOf { it * it })
        if (norm <= 1e-12) return null
        for (i in chroma.indices) chroma[i] /= norm

        // Near-uniform chroma means noise / atonal input.
        if (chroma.max() - chroma.min() < MIN_CHROMA_SPREAD) return null

        var bestCorr = -1.0
        var bestRoot = -1
        var bestMinor = false
        for (root in 0 until 12) {
            val corrMajor = pearsonRotated(chroma, MAJOR_PROFILE, root)
            if (corrMajor > bestCorr) {
                bestCorr = corrMajor
                bestRoot = root
                bestMinor = false
            }
            val corrMinor = pearsonRotated(chroma, MINOR_PROFILE, root)
            if (corrMinor > bestCorr) {
                bestCorr = corrMinor
                bestRoot = root
                bestMinor = true
            }
        }
        if (bestRoot < 0 || bestCorr < MIN_CORRELATION) return null

        val key = NOTE_NAMES[bestRoot] + if (bestMinor) "m" else ""
        val camelot = if (bestMinor) MINOR_CAMELOT[bestRoot] else MAJOR_CAMELOT[bestRoot]
        return KeyResult(key, camelot)
    }

    /**
     * Pearson correlation between the (L2-normalized) chroma and the
     * profile rotated so its tonic (index 0) sits on [root].
     */
    private fun pearsonRotated(chroma: DoubleArray, profile: DoubleArray, root: Int): Double {
        var meanC = 0.0
        var meanP = 0.0
        for (c in 0 until 12) {
            meanC += chroma[c]
            meanP += profile[(c - root + 12) % 12]
        }
        meanC /= 12.0
        meanP /= 12.0

        var num = 0.0
        var denC = 0.0
        var denP = 0.0
        for (c in 0 until 12) {
            val dc = chroma[c] - meanC
            val dp = profile[(c - root + 12) % 12] - meanP
            num += dc * dp
            denC += dc * dc
            denP += dp * dp
        }
        val den = sqrt(denC * denP)
        return if (den <= 1e-12) 0.0 else num / den
    }
}
