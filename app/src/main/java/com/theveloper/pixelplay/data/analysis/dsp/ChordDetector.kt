package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.sqrt

/**
 * Chord progression estimation via chromagram template matching against 24 major/minor triad templates.
 * Reuses STFT pitch class distribution over sliding windows (~0.5s hop).
 */
object ChordDetector {

    private val CHORD_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
        "Cm", "C#m", "Dm", "D#m", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "A#m", "Bm"
    )

    // 24 binary templates for Major (root, +4, +7 semitones) and Minor (root, +3, +7 semitones)
    private val CHORD_TEMPLATES: Array<DoubleArray> = Array(24) { index ->
        val root = index % 12
        val isMinor = index >= 12
        val template = DoubleArray(12)
        template[root] = 1.0
        template[(root + if (isMinor) 3 else 4) % 12] = 1.0
        template[(root + 7) % 12] = 1.0
        // Normalize template
        val norm = sqrt(template.sumOf { it * it })
        for (i in template.indices) template[i] /= norm
        template
    }

    data class ChordSegment(
        val chord: String,
        val timestampMs: Long
    )

    fun detectProgression(pcm: FloatArray, sampleRate: Int): Pair<String?, List<ChordSegment>> {
        if (pcm.isEmpty() || sampleRate <= 0) return null to emptyList()

        val windowSize = 4096
        val hopSize = sampleRate / 2 // 500ms hop
        val frameCount = (pcm.size - windowSize) / hopSize + 1
        if (frameCount < 2) return null to emptyList()

        val frame = FloatArray(windowSize)
        val chordSequence = mutableListOf<ChordSegment>()
        val chordCounts = mutableMapOf<String, Int>()

        for (fr in 0 until frameCount) {
            val offset = fr * hopSize
            val length = minOf(windowSize, pcm.size - offset)
            if (length < windowSize / 2) break

            pcm.copyInto(frame, 0, offset, offset + length)
            // Zero out remaining if partial
            for (i in length until windowSize) frame[i] = 0f

            val mag = Fft.magnitudes(frame)
            val chroma = DoubleArray(12)

            for (k in 1 until mag.size) {
                val freq = k * sampleRate.toDouble() / windowSize
                if (freq < 65.0 || freq > 2100.0) continue // C2 to C7
                val midi = 69 + (12 * kotlin.math.log2(freq / 440.0)).toInt()
                val pc = ((midi % 12) + 12) % 12
                chroma[pc] += mag[k].toDouble()
            }

            val sum = chroma.sum()
            if (sum <= 1e-6) continue

            val norm = sqrt(chroma.sumOf { it * it })
            if (norm > 1e-6) {
                for (i in chroma.indices) chroma[i] /= norm
            }

            var bestScore = -1.0
            var bestIdx = -1

            for (cIdx in 0 until 24) {
                val score = cosineSimilarity(chroma, CHORD_TEMPLATES[cIdx])
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = cIdx
                }
            }

            if (bestIdx >= 0 && bestScore > 0.4) {
                val chordName = CHORD_NAMES[bestIdx]
                val timeMs = (fr.toLong() * hopSize * 1000L) / sampleRate
                chordSequence.add(ChordSegment(chordName, timeMs))
                chordCounts[chordName] = (chordCounts[chordName] ?: 0) + 1
            }
        }

        if (chordSequence.isEmpty()) return null to emptyList()

        // Deduplicate adjacent identical chords
        val deduped = mutableListOf<ChordSegment>()
        for (seg in chordSequence) {
            if (deduped.isEmpty() || deduped.last().chord != seg.chord) {
                deduped.add(seg)
            }
        }

        // Summary representation of progression (e.g. "C - G - Am - F")
        val topChords = chordCounts.entries.sortedByDescending { it.value }.take(4).map { it.key }
        val progressionSummary = topChords.joinToString(" - ")

        return progressionSummary to deduped
    }

    private fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
