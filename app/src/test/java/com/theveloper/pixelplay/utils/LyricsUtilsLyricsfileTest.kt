package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.lyrics.Lyricsfile
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Lyricsfile documents going through the app's lyrics parser (stored, imported, sidecar, remote). */
class LyricsUtilsLyricsfileTest {
    private val document = """
        version: '1.0'
        metadata:
          title: Song
          artist: Artist
          language: en
        lines:
          - text: Hello there
            start_ms: 1000
            end_ms: 2400
            words:
              - {text: 'Hello ', start_ms: 1000, end_ms: 1600}
              - {text: there, start_ms: 1600, end_ms: 2400}
          - text: Hola
            start_ms: 1000
            end_ms: 2400
          - text: Second line
            start_ms: 3000
    """.trimIndent()

    @Test fun `word timing, translations and language survive parsing`() {
        val lyrics = LyricsUtils.parseLyrics(document)
        val lines = requireNotNull(lyrics.synced)
        assertEquals(2, lines.size)
        assertEquals("Hello there", lines[0].line)
        assertEquals("Hola", lines[0].translation)
        assertEquals(listOf(1_600, 2_400), lines[0].words!!.map { it.endTime })
        assertEquals(2_400, lines[0].endTime)
        assertEquals("en", lyrics.timing?.language)
        assertEquals(Lyricsfile.SOURCE, lyrics.timing?.source)
    }

    @Test fun `what the app writes it reads back`() {
        val original = Lyrics(
            synced = listOf(
                SyncedLine(
                    500, "One two", endTime = 1_500,
                    words = listOf(SyncedWord(500, "One", endTime = 900), SyncedWord(900, "two", endTime = 1_500)),
                    translation = "Uno dos"
                ),
                SyncedLine(2_000, "Three")
            )
        )
        val parsed = LyricsUtils.parseLyrics(Lyricsfile.serialize(original, Lyricsfile.Metadata(title = "t", artist = "a")))
        val lines = requireNotNull(parsed.synced)
        assertEquals(listOf("One two", "Three"), lines.map { it.line })
        assertEquals("Uno dos", lines[0].translation)
        assertEquals(original.synced!![0].words, lines[0].words)
    }

    @Test fun `a broken Lyricsfile is not shown as plain text`() {
        val broken = "version: '1.0'\nmetadata:\n  title: [unclosed\nlines:\n  - text: x\n"
        val lyrics = LyricsUtils.parseLyrics(broken)
        assertTrue(lyrics.synced.isNullOrEmpty())
        assertTrue(lyrics.plain.isNullOrEmpty())
    }

    @Test fun `LRC and plain text are untouched`() {
        val lrc = LyricsUtils.parseLyrics("[00:01.00]version: 1\n[00:02.00]metadata: none")
        assertEquals(listOf("version: 1", "metadata: none"), lrc.synced!!.map { it.line })
        assertNull(lrc.timing)
        assertEquals(listOf("just words"), LyricsUtils.parseLyrics("just words").plain)
    }
}
