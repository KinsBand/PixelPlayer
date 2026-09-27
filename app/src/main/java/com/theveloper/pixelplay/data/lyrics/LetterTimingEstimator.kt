package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.SyncedPhoneme
import com.theveloper.pixelplay.data.model.SyncedWord

/**
 * Stand-in letter timing for Letter mode until on-device alignment exists.
 *
 * Splits a word's *measured* [SyncedWord.time]..[SyncedWord.endTime] across its letters, giving
 * vowels more time than consonants (sung vowels are held; consonants are short). Every span is
 * tagged `alphabet = "estimated-grapheme"` and `soundType = "estimated"`, so it is never mistaken
 * for measured alignment, and it is only used when the user explicitly picks Letter mode.
 */
object LetterTimingEstimator {
    const val ALPHABET = "estimated-grapheme"

    private const val VOWEL_WEIGHT = 3f
    private const val CONSONANT_WEIGHT = 1f
    private const val OTHER_LETTER_WEIGHT = 1.5f // CJK, kana, hangul …: one sung unit each

    private val cache = object : LinkedHashMap<Triple<String, Int, Int>, List<SyncedPhoneme>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<String, Int, Int>, List<SyncedPhoneme>>?) = size > 512
    }.also { map ->
        com.theveloper.pixelplay.data.diagnostics.HeapPressure.register("letter-timing") { synchronized(map) { map.clear() } }
    }

    /** Estimated letter spans, or empty when the word has no end time or no letters. */
    fun estimate(word: SyncedWord): List<SyncedPhoneme> {
        val end = word.endTime ?: return emptyList()
        if (end <= word.time || word.word.isEmpty()) return emptyList()
        val key = Triple(word.word, word.time, end)
        synchronized(cache) { cache[key]?.let { return it } }
        val result = compute(word.word, word.time, end)
        synchronized(cache) { cache[key] = result }
        return result
    }

    internal fun compute(text: String, start: Int, end: Int): List<SyncedPhoneme> {
        // Group each weighted character with the punctuation/marks that follow it, so marks never
        // light up on their own.
        data class Unit(val from: Int, var to: Int, val weight: Float)
        val units = ArrayList<Unit>(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            val weight = weightOf(cp)
            if (weight > 0f || units.isEmpty()) {
                units.add(Unit(i, i + len, weight.coerceAtLeast(0.01f)))
            } else {
                units.last().to = i + len
            }
            i += len
        }
        val total = units.sumOf { it.weight.toDouble() }.takeIf { it > 0.0 } ?: return emptyList()
        val duration = (end - start).toDouble()
        var cursor = start.toDouble()
        return units.mapIndexed { index, unit ->
            val spanStart = cursor.toInt()
            cursor += duration * unit.weight / total
            val spanEnd = if (index == units.lastIndex) end else cursor.toInt().coerceAtLeast(spanStart + 1).coerceAtMost(end)
            SyncedPhoneme(
                phoneme = text.substring(unit.from, unit.to),
                alphabet = ALPHABET,
                time = spanStart.coerceAtMost(end - 1),
                endTime = spanEnd.coerceAtLeast(spanStart.coerceAtMost(end - 1) + 1),
                characterStart = unit.from,
                characterEnd = unit.to,
                soundType = "estimated"
            )
        }
    }

    private fun weightOf(codePoint: Int): Float {
        if (!Character.isLetterOrDigit(codePoint)) return 0f
        val lower = Character.toLowerCase(codePoint)
        if (lower < 0x250) {
            val base = stripAccent(lower)
            return if (base in "aeiouy") VOWEL_WEIGHT else CONSONANT_WEIGHT
        }
        return OTHER_LETTER_WEIGHT
    }

    private fun stripAccent(cp: Int): Char {
        val s = java.text.Normalizer.normalize(String(Character.toChars(cp)), java.text.Normalizer.Form.NFD)
        return s.firstOrNull() ?: cp.toChar()
    }
}
