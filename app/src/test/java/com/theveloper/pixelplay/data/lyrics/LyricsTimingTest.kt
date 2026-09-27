package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.*
import com.theveloper.pixelplay.utils.LyricsUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LyricsTimingTest {
    @Test fun `TTML retains millisecond start end and voice`() {
        val lyrics = LyricsUtils.parseLyrics("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div><p begin="1.123" end="4.987" ttm:agent="lead"><span begin="1.123" end="3.456">light</span></p></div></body></tt>""")
        val line = lyrics.synced!!.single()
        assertEquals(1123, line.time)
        assertEquals(4987, line.endTime)
        assertEquals(3456, line.words!!.single().endTime)
        assertEquals("lead", line.voiceId)
    }
    @Test fun `phoneme highlighting follows sustained vowel duration and rewinds on seek`() {
        val word = SyncedWord(1000, "light", endTime = 4000, phonemes = listOf(
            SyncedPhoneme("l", "IPA", 1000, 1100, 0, 1, "consonant"),
            SyncedPhoneme("aɪ", "IPA", 1100, 3900, 1, 4, "vowel"),
            SyncedPhoneme("t", "IPA", 3900, 4000, 4, 5, "consonant")))
        assertEquals(0.5f, LyricsTiming.progress(word, 2500, 5000), 0.001f)
        assertEquals(1f, LyricsTiming.progress(word, 4100, 5000))
        assertEquals(0f, LyricsTiming.progress(word, 999, 5000))
    }
    @Test fun `structured timing round trip preserves evidence and rejects invalid phone boundaries`() {
        val lyrics = Lyrics(synced = listOf(SyncedLine(0, "Hello", listOf(SyncedWord(0, "Hello", endTime = 1000)))),
            timing = LyricsTimingEvidence(assetHash = "hash", verified = true))
        assertEquals(lyrics, LyricsTiming.parse(LyricsTiming.encode(lyrics)))
        val bad = lyrics.copy(synced = listOf(SyncedLine(0, "a", listOf(SyncedWord(0, "a", endTime = 100,
            phonemes = listOf(SyncedPhoneme("a", "IPA", 0, 200, 0, 1)))))))
        assertNull(LyricsTiming.parse(LyricsTiming.encode(bad)))
    }
    @Test fun `plain line lyrics are not fabricated into word timing`() {
        assertNull(LyricsUtils.parseLyrics("[00:01.00]Hello world").synced!!.single().words)
    }
    @Test fun `real end time completes before the next word starts`() {
        assertEquals(1f, LyricsTiming.progress(SyncedWord(100, "Hi", endTime = 300), 400, 900))
    }
}
