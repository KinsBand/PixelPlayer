package com.theveloper.pixelplay.data.lyrics.align

import java.text.Normalizer

/**
 * The letters a CTC acoustic model can emit, and their token ids.
 *
 * [WAV2VEC2_960H] is the `vocab.json` of `facebook/wav2vec2-base-960h` (English, upper-case
 * A–Z plus apostrophe): `<pad>` (0) is the CTC blank and `|` (4) separates words.
 */
class CtcVocabulary(
    val blank: Int,
    val wordSeparator: Int,
    private val letters: Map<Char, Int>,
    val size: Int
) {
    fun idOf(c: Char): Int? = letters[c]

    companion object {
        val WAV2VEC2_960H = CtcVocabulary(
            blank = 0,
            wordSeparator = 4,
            letters = mapOf(
                'E' to 5, 'T' to 6, 'A' to 7, 'O' to 8, 'N' to 9, 'I' to 10, 'H' to 11, 'S' to 12,
                'R' to 13, 'D' to 14, 'L' to 15, 'U' to 16, 'M' to 17, 'W' to 18, 'C' to 19, 'F' to 20,
                'G' to 21, 'Y' to 22, 'P' to 23, 'B' to 24, 'V' to 25, 'K' to 26, '\'' to 27, 'X' to 28,
                'J' to 29, 'Q' to 30, 'Z' to 31
            ),
            size = 32
        )
    }
}

/**
 * One on-screen word of a lyric line, with the model tokens it is sung as.
 *
 * [text] is the word exactly as displayed (a whitespace-separated token of the line, the same
 * split the lyrics renderers use). [tokens] is empty when nothing in the word can be aligned:
 * numbers ("24/7" is sung as words the model would spell differently), other scripts, symbols,
 * and words in parentheses (backing vocals, usually quieter than the lead or not sung at all).
 * Those words get times interpolated from their neighbours.
 */
class AlignWord(
    val text: String,
    val tokens: IntArray,
    /** For each token, the index in [text] of the character it came from. */
    val charIndex: IntArray
) {
    val alignable: Boolean get() = tokens.isNotEmpty()
}

class AlignLine(val words: List<AlignWord>, val englishLetterShare: Float) {
    val alignableWords: Int get() = words.count { it.alignable }
    val tokenCount: Int get() = words.sumOf { it.tokens.size }
}

/**
 * Turns a lyric line into model tokens while remembering which on-screen character each token
 * came from, so letter timings can be drawn back onto the line exactly as displayed.
 *
 * English only: accents are folded (é → E), curly apostrophes become `'`, and a line whose
 * letters are mostly outside A–Z is reported through [AlignLine.englishLetterShare] so the
 * caller can leave it with its source timing.
 */
object AlignmentTextNormalizer {

    fun normalize(line: String, vocab: CtcVocabulary = CtcVocabulary.WAV2VEC2_960H): AlignLine {
        val words = ArrayList<AlignWord>()
        var parenDepth = 0
        var latinLetters = 0
        var allLetters = 0

        for (raw in line.split(WHITESPACE)) {
            if (raw.isEmpty()) continue
            val openedHere = raw.count { it == '(' || it == '[' }
            val closedHere = raw.count { it == ')' || it == ']' }
            // A word is "in parentheses" if it opens, sits inside, or closes a bracket.
            val inParens = parenDepth > 0 || openedHere > 0
            parenDepth = (parenDepth + openedHere - closedHere).coerceAtLeast(0)

            val tokens = ArrayList<Int>(raw.length)
            val chars = ArrayList<Int>(raw.length)
            var hasDigit = false
            for ((index, c) in raw.withIndex()) {
                if (c.isDigit()) hasDigit = true
                if (c.isLetter()) {
                    allLetters++
                    val folded = fold(c)
                    if (folded != null) latinLetters++
                    val id = folded?.let(vocab::idOf)
                    if (id != null) {
                        tokens += id
                        chars += index
                    }
                } else if (c in APOSTROPHES) {
                    // Only inside a word ("don't", "rock'n'roll"); a leading or trailing quote is silent.
                    val prev = raw.getOrNull(index - 1)
                    val next = raw.getOrNull(index + 1)
                    if (prev?.isLetter() == true && next?.isLetter() == true) {
                        vocab.idOf('\'')?.let { tokens += it; chars += index }
                    }
                }
            }
            val alignable = !hasDigit && !inParens && tokens.isNotEmpty()
            words += if (alignable) {
                AlignWord(raw, tokens.toIntArray(), chars.toIntArray())
            } else {
                AlignWord(raw, IntArray(0), IntArray(0))
            }
        }
        val share = if (allLetters == 0) 0f else latinLetters.toFloat() / allLetters
        return AlignLine(words, share)
    }

    /** Upper-case A–Z for [c] after removing accents, or null when it isn't a Latin letter. */
    internal fun fold(c: Char): Char? {
        val upper = c.uppercaseChar()
        if (upper in 'A'..'Z') return upper
        val base = Normalizer.normalize(upper.toString(), Normalizer.Form.NFD)
            .firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return base?.takeIf { it in 'A'..'Z' }
    }

    private val WHITESPACE = Regex("\\s+")
    private val APOSTROPHES = setOf('\'', '’', '‘', '`', '´')
}
