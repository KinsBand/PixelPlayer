package com.theveloper.pixelplay.data.lyrics.align

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlignmentTextNormalizerTest {
    private val vocab = CtcVocabulary.WAV2VEC2_960H

    @Test fun `words keep their on-screen text and map letters back to it`() {
        val line = AlignmentTextNormalizer.normalize("Don’t stop, café!")
        assertEquals(listOf("Don’t", "stop,", "café!"), line.words.map { it.text })
        val dont = line.words[0]
        assertArrayEquals(listOf('D', 'O', 'N', '\'', 'T').map { vocab.idOf(it)!! }.toIntArray(), dont.tokens)
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4), dont.charIndex)
        assertArrayEquals(listOf('C', 'A', 'F', 'E').map { vocab.idOf(it)!! }.toIntArray(), line.words[2].tokens)
        assertEquals(1f, line.englishLetterShare, 0.001f)
    }

    @Test fun `numbers, brackets and edge quotes are not aligned`() {
        val line = AlignmentTextNormalizer.normalize("'Cause 24/7 (oh yeah) we go")
        assertEquals(6, line.words.size)
        assertTrue(line.words[0].alignable)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5), line.words[0].charIndex) // leading quote skipped
        assertFalse(line.words[1].alignable)   // 24/7
        assertFalse(line.words[2].alignable)   // (oh
        assertFalse(line.words[3].alignable)   // yeah)
        assertTrue(line.words[4].alignable)
        assertEquals(3, line.alignableWords)
    }

    @Test fun `non-Latin lines report a low English share`() {
        assertEquals(0f, AlignmentTextNormalizer.normalize("こんにちは 世界").englishLetterShare, 0.001f)
        assertTrue(AlignmentTextNormalizer.normalize("Привет love").englishLetterShare < 0.5f)
    }
}
