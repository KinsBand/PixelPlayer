package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.SyncedPhoneme
import com.theveloper.pixelplay.data.model.SyncedWord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LyricsHighlightModeTest {
    private val word = SyncedWord(1000, "light", endTime = 4000, phonemes = listOf(
        SyncedPhoneme("l", "IPA", 1000, 1100, 0, 1),
        SyncedPhoneme("aɪ", "IPA", 1100, 3900, 1, 4, "vowel"),
        SyncedPhoneme("t", "IPA", 3900, 4000, 4, 5)))

    @Test fun `sustained vowel reveals its mapped letters but keeps upcoming consonant grey`() {
        assertEquals(listOf(0..0, 1..3), highlightedLyricRanges(word, 2500, LyricsHighlightMode.PHONEME))
        assertEquals(listOf(0..0, 1..3, 4..4), highlightedLyricRanges(word, 3900, LyricsHighlightMode.PHONEME))
    }
    @Test fun `seek backwards restores unspoken state`() {
        assertTrue(highlightedLyricRanges(word, 999, LyricsHighlightMode.PHONEME).isEmpty())
        assertEquals(listOf(0..0), highlightedLyricRanges(word, 1000, LyricsHighlightMode.PHONEME))
    }
    @Test fun `word mode reveals the whole word at its actual onset`() {
        assertEquals(listOf(0..4), highlightedLyricRanges(word, 1000, LyricsHighlightMode.WORD))
    }
    @Test fun `missing phonemes fall back to word timing without invented letters`() {
        assertEquals(listOf(0..4), highlightedLyricRanges(word.copy(phonemes = null), 1100, LyricsHighlightMode.PHONEME))
        assertEquals(LyricsHighlightMode.AUTO, LyricsHighlightMode.fromName("bad-value"))
    }
}
