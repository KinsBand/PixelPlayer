package com.theveloper.pixelplay.data.lyrics.align

import kotlin.math.ln

/**
 * Builds synthetic CTC emissions: every frame is blank-dominated unless a token is placed on it.
 * 20 ms frames, wav2vec2-960h vocabulary.
 */
internal class FakeEmissions(val frames: Int, private val vocab: CtcVocabulary = CtcVocabulary.WAV2VEC2_960H) {
    private val data = FloatArray(frames * vocab.size)

    init {
        for (f in 0 until frames) setFrame(f, vocab.blank, 0.97f)
    }

    /** Makes [token] the likely output at [frame] with probability [p]. */
    fun setFrame(frame: Int, token: Int, p: Float) {
        val rest = ln(((1f - p) / (vocab.size - 1)).toDouble()).toFloat()
        for (v in 0 until vocab.size) data[frame * vocab.size + v] = rest
        data[frame * vocab.size + token] = ln(p.toDouble()).toFloat()
    }

    /**
     * Places [text] (letters of words, `|` between words) starting at [startMs], one letter every
     * [letterMs], a separator after each word, with confidence [p].
     * Returns the start ms of each word.
     */
    fun sing(text: String, startMs: Int, letterMs: Int = 100, p: Float = 0.95f): List<Int> {
        val starts = ArrayList<Int>()
        var ms = startMs
        for (word in text.split(' ')) {
            starts += ms
            for (c in word) {
                val id = AlignmentTextNormalizer.fold(c)?.let(vocab::idOf) ?: if (c == '\'') vocab.idOf('\'') else null
                if (id != null) setFrame(ms / 20, id, p)
                ms += letterMs
            }
            setFrame(ms / 20, vocab.wordSeparator, p)
            ms += letterMs
        }
        return starts
    }

    fun build(): CtcEmissions = CtcEmissions(data, frames, vocab.size, 20.0)
}
