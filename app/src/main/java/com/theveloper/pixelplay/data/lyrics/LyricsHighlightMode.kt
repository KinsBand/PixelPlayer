package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.SyncedWord

enum class LyricsHighlightMode(val label: String) {
    AUTO("Synced · best available"), WORD("Word by word"),
    PHONEME("Vowels / letters"), LINE("Sentence by sentence");

    companion object {
        fun fromName(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/**
 * Only real timing events reveal characters in Auto and Word modes. Letter mode ([PHONEME]) uses
 * measured letter spans when a word has them; when it doesn't but the word has a measured end
 * time, it falls back to [LetterTimingEstimator] (spans tagged `estimated-grapheme`), because the
 * user explicitly asked for letters. Auto never shows estimated letters.
 */
fun highlightedLyricRanges(word: SyncedWord, positionMs: Long, mode: LyricsHighlightMode): List<IntRange> {
    if (positionMs < word.time || word.word.isEmpty()) return emptyList()
    if (word.endTime != null && positionMs >= word.endTime) return listOf(word.word.indices)
    if (mode == LyricsHighlightMode.WORD) return listOf(word.word.indices)
    val measured = word.phonemes?.takeIf { it.isNotEmpty() }
    val estimated = if (measured == null && mode == LyricsHighlightMode.PHONEME) {
        LetterTimingEstimator.estimate(word).takeIf { it.isNotEmpty() }
    } else {
        null
    }
    val phones = measured ?: estimated ?: return listOf(word.word.indices)
    return phones.filter { it.time <= positionMs }
        .mapNotNull { phone ->
            val start = phone.characterStart
            val end = phone.characterEnd
            if (start >= 0 && end <= word.word.length && start < end) start until end else null
        }
}
