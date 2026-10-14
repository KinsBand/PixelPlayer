package com.theveloper.pixelplay.data.lyrics.align

import com.theveloper.pixelplay.data.lyrics.LyricsTiming
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class LyricsAlignmentBuilderTest {

    private fun build(lyrics: Lyrics, em: CtcEmissions, offset: Int = 0) =
        LyricsAlignmentBuilder.build(lyrics, em, offset, durationMs = em.frames * 20L, modelVersion = "test")

    @Test fun `line-synced lyrics that start late become word and letter synced at the sung time`() {
        val em = FakeEmissions(800)
        val sungA = em.sing("HELLO WORLD", startMs = 2000)
        val sungB = em.sing("GOOD NIGHT", startMs = 6000)
        val sungC = em.sing("SEE YOU", startMs = 10000)
        // Source lyrics are 700 ms late.
        val lyrics = Lyrics(synced = listOf(
            SyncedLine(2700, "Hello world"),
            SyncedLine(6700, "Good night"),
            SyncedLine(10700, "See you")
        ))
        val result = build(lyrics, em.build())
        val out = assertNotNull(result.lyrics).let { result.lyrics!! }
        assertEquals(3, result.letterLines, result.reason)
        assertEquals(sungA, out.synced!![0].words!!.map { it.time })
        assertEquals(sungB, out.synced!![1].words!!.map { it.time })
        assertEquals(sungC, out.synced!![2].words!!.map { it.time })
        assertEquals(2000, out.synced!![0].time)
        val hello = out.synced!![0].words!![0]
        assertEquals(5, hello.phonemes!!.size)
        assertEquals("grapheme", hello.phonemes!![0].alphabet)
        assertEquals(listOf(2000, 2100, 2200, 2300, 2400), hello.phonemes!!.map { it.time })
        assertEquals(LyricsAlignmentBuilder.SOURCE, out.timing!!.source)
        assertTrue(LyricsTiming.validate(out).isEmpty())
    }

    @Test fun `known offset moves windows and failed lines keep offset-corrected timing`() {
        val em = FakeEmissions(800)
        em.sing("HELLO WORLD", startMs = 1000)
        em.sing("GOOD NIGHT", startMs = 5000)
        // Third line is never sung clearly: nothing in the audio for it.
        val lyrics = Lyrics(synced = listOf(
            SyncedLine(5000, "Hello world"),
            SyncedLine(9000, "Good night"),
            SyncedLine(13000, "Sleep tight")
        ))
        // Player offset +4000: audio time = lyric time - 4000.
        val result = build(lyrics, em.build(), offset = 4000)
        val lines = result.lyrics!!.synced!!
        assertEquals(1000, lines[0].time)
        assertEquals(5000, lines[1].time)
        assertEquals(LyricsAlignmentBuilder.Level.LINE, result.outcomes[2].level)
        assertEquals(9000, lines[2].time)
        assertNull(lines[2].words)
    }

    @Test fun `numbers and bracketed words get interpolated times`() {
        val em = FakeEmissions(600)
        em.sing("WE GO", startMs = 1000)          // "We (oh) go"
        em.sing("LOVE YOU", startMs = 5000)
        val lyrics = Lyrics(synced = listOf(SyncedLine(1000, "We (oh) go"), SyncedLine(5000, "Love you")))
        val out = build(lyrics, em.build()).lyrics!!
        val words = out.synced!![0].words!!
        assertEquals(listOf("We", "(oh)", "go"), words.map { it.word })
        assertTrue(words[1].time >= words[0].time && words[1].time <= words[2].time)
        assertNull(words[1].phonemes)
        assertTrue(LyricsTiming.validate(out).isEmpty())
    }

    @Test fun `low-confidence audio keeps word timing but drops letters`() {
        val em = FakeEmissions(600)
        em.sing("HELLO WORLD", startMs = 1000, p = 0.4f)
        em.sing("GOOD NIGHT", startMs = 5000, p = 0.4f)
        val lyrics = Lyrics(synced = listOf(SyncedLine(1000, "Hello world"), SyncedLine(5000, "Good night")))
        val result = build(lyrics, em.build())
        assertEquals(2, result.wordLines, result.reason)
        assertNull(result.lyrics!!.synced!![0].words!![0].phonemes)
    }

    @Test fun `songs that mostly fail to align are not published`() {
        val em = FakeEmissions(600) // silence: blank everywhere
        val lyrics = Lyrics(synced = listOf(SyncedLine(1000, "Hello world"), SyncedLine(5000, "Good night"), SyncedLine(9000, "See you")))
        val result = build(lyrics, em.build())
        assertNull(result.lyrics, result.reason)
    }

    @Test fun `non-English lines are left alone`() {
        val em = FakeEmissions(600)
        em.sing("HELLO WORLD", startMs = 1000)
        em.sing("GOOD NIGHT", startMs = 8000)
        val lyrics = Lyrics(synced = listOf(SyncedLine(1000, "Hello world"), SyncedLine(4000, "こんにちは 世界"), SyncedLine(8000, "Good night")))
        val result = build(lyrics, em.build())
        val lines = result.lyrics!!.synced!!
        assertEquals(4000, lines[1].time)
        assertNull(lines[1].words)
        assertTrue(abs(lines[2].time - 8000) <= 20)
    }

    @Test fun `shift moves every time and keeps the lyrics valid`() {
        val em = FakeEmissions(600)
        em.sing("HELLO WORLD", startMs = 1000)
        em.sing("GOOD NIGHT", startMs = 5000)
        val lyrics = Lyrics(synced = listOf(SyncedLine(1000, "Hello world"), SyncedLine(5000, "Good night")))
        val aligned = build(lyrics, em.build()).lyrics!!
        val later = LyricsAlignmentBuilder.shift(aligned, 300)
        assertEquals(1300, later.synced!![0].time)
        assertEquals(aligned.synced!![1].words!![1].phonemes!![0].time + 300, later.synced!![1].words!![1].phonemes!![0].time)
        val earlier = LyricsAlignmentBuilder.shift(aligned, -1500)
        assertEquals(0, earlier.synced!![0].time)
        assertNull(earlier.synced!![0].words)
        assertEquals(3500, earlier.synced!![1].time)
        assertTrue(LyricsTiming.validate(earlier).isEmpty())
    }
}
