package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import com.theveloper.pixelplay.data.network.lyrics.LrcLibResponse
import com.theveloper.pixelplay.data.network.lyrics.preferredRawLyrics
import com.theveloper.pixelplay.data.network.lyrics.wordTimedLyricsfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class LyricsfileTest {

    // The word-synced example from the Lyricsfile description.
    private val wordSynced = """
        version: '1.0'
        metadata:
          title: 'Shape of You'
          artist: 'Ed Sheeran'
          duration_ms: 235000

        lines:
          - text: "The club isn't the best place to find a lover"
            start_ms: 12450
            end_ms: 18200
            words:
              - text: 'The '
                start_ms: 12450
                end_ms: 12900
              - text: 'club '
                start_ms: 12900
                end_ms: 13500
              - text: "isn't "
                start_ms: 13500
                end_ms: 14200
              - text: 'the '
                start_ms: 14200
                end_ms: 14600
              - text: 'best '
                start_ms: 14600
                end_ms: 15200
              - text: 'place '
                start_ms: 15200
                end_ms: 15800
              - text: 'to '
                start_ms: 15800
                end_ms: 16200
              - text: 'find '
                start_ms: 16200
                end_ms: 16800
              - text: 'a '
                start_ms: 16800
                end_ms: 17100
              - text: 'lover'
                start_ms: 17100
                end_ms: 18200
          - text: 'So the bar is where I go'
            start_ms: 18500
            end_ms: 22100
            words:
              - text: 'So '
                start_ms: 18500
                end_ms: 19000
              - text: 'the '
                start_ms: 19000
                end_ms: 19400
              - text: 'bar '
                start_ms: 19400
                end_ms: 20000
              - text: 'is '
                start_ms: 20000
                end_ms: 20400
              - text: 'where '
                start_ms: 20400
                end_ms: 21000
              - text: 'I '
                start_ms: 21000
                end_ms: 21400
              - text: 'go'
                start_ms: 21400
                end_ms: 22100

        plain: |
          [Verse 1]
          The club isn't the best place to find a lover
          So the bar is where I go
    """.trimIndent()

    // What LRCLIB's legacy conversion writes (serde_yaml layout) for
    // "[00:01.5][00:03.25]Repeated\n[00:03.25]Repeated\n[01:35.492]Later".
    private val lrcLibLineLevel = """
        version: '1.0'
        metadata:
          title: Test Song
          artist: Test Artist
          album: Test Album
          duration_ms: 95492
          instrumental: false
        lines:
        - text: Repeated
          start_ms: 1500
          end_ms: 3250
        - text: Repeated
          start_ms: 3250
          end_ms: 3250
        - text: Repeated
          start_ms: 3250
          end_ms: 95492
        - text: Later
          start_ms: 95492
        plain: |-
          Later
          Repeated
          Repeated
    """.trimIndent()

    @Test fun `word synced lines keep word timing and spacing`() {
        val document = requireNotNull(Lyricsfile.parse(wordSynced))
        assertTrue(document.hasWordTiming)
        assertEquals("Shape of You", document.metadata.title)
        assertEquals(235_000L, document.metadata.durationMs)
        val lines = requireNotNull(document.lyrics.synced)
        assertEquals(2, lines.size)
        val first = lines[0]
        assertEquals(12_450, first.time)
        assertEquals(18_200, first.endTime)
        assertEquals("The club isn't the best place to find a lover", first.line)
        val words = requireNotNull(first.words)
        assertEquals(10, words.size)
        assertEquals(listOf("The", "club", "isn't"), words.take(3).map { it.word })
        assertTrue(words.all { it.startsNewWord })
        assertEquals(12_900, words[0].endTime)
        assertEquals(18_200, words.last().endTime)
        assertEquals(listOf(18_500, 19_000), lines[1].words!!.take(2).map { it.time })
    }

    @Test fun `LRCLIB line documents read like their LRC`() {
        val document = requireNotNull(Lyricsfile.parse(lrcLibLineLevel))
        assertFalse(document.hasWordTiming)
        assertEquals("Test Album", document.metadata.album)
        val lines = requireNotNull(document.lyrics.synced)
        assertEquals(listOf(1_500, 3_250, 3_250, 95_492), lines.map { it.time })
        assertEquals(listOf("Repeated", "Repeated", "Repeated", "Later"), lines.map { it.line })
        // A zero-length line has no end of its own; the others keep theirs.
        assertEquals(listOf(3_250, null, 95_492, null), lines.map { it.endTime })
    }

    @Test fun `plain only documents keep blank lines and headers`() {
        val lyrics = requireNotNull(Lyricsfile.parse(
            """
            version: '1.0'
            metadata:
              title: 'New Song'
              artist: 'Unknown Artist'
              instrumental: false

            lines: []

            plain: |
              [Verse 1]
              These lyrics haven't been synced yet

              [Chorus]
              La la la
            """.trimIndent()
        )).lyrics
        assertNull(lyrics.synced)
        assertEquals(
            listOf("[Verse 1]", "These lyrics haven't been synced yet", "", "[Chorus]", "La la la"),
            lyrics.plain
        )
    }

    @Test fun `LRCGET's null lines and missing plain fall back to line texts`() {
        val lyrics = requireNotNull(Lyricsfile.parse(
            "version: \"1.0\"\nmetadata:\n  title: A\n  artist: B\n  instrumental: false\nlines: null\nplain: \"one\\ntwo\"\n"
        )).lyrics
        assertEquals(listOf("one", "two"), lyrics.plain)

        val untimed = requireNotNull(Lyricsfile.parse(
            "version: '1.0'\nmetadata: {title: A, artist: B}\nlines:\n  - text: first\n  - text: second\n"
        )).lyrics
        assertNull(untimed.synced)
        assertEquals(listOf("first", "second"), untimed.plain)
    }

    @Test fun `instrumental documents have no lyrics`() {
        val document = requireNotNull(Lyricsfile.parse(
            "version: '1.0'\nmetadata:\n  title: 'Adagio in G Minor'\n  artist: 'Tomaso Albinoni'\n  instrumental: true\nlines:\n  - text: stray\n    start_ms: 10\nplain: ''\n"
        ))
        assertTrue(document.metadata.instrumental)
        assertTrue(document.lyrics.synced.isNullOrEmpty())
        assertTrue(document.lyrics.plain.isNullOrEmpty())
    }

    @Test fun `offset moves every time and never below zero`() {
        val lines = requireNotNull(Lyricsfile.parse(
            """
            version: '1.0'
            metadata: {title: A, artist: B, offset_ms: -500}
            lines:
              - text: early
                start_ms: 200
              - text: late
                start_ms: 3000
                end_ms: 4000
                words:
                  - {text: 'la ', start_ms: 3000, end_ms: 3500}
                  - {text: 'te', start_ms: 3500, end_ms: 4000}
            """.trimIndent()
        )?.lyrics?.synced)
        assertEquals(listOf(0, 2_500), lines.map { it.time })
        assertEquals(3_500, lines[1].endTime)
        assertEquals(listOf(2_500, 3_000), lines[1].words!!.map { it.time })
    }

    @Test fun `words without an end run until the next word and CJK words join`() {
        val line = requireNotNull(Lyricsfile.parse(
            """
            version: '1.0'
            metadata: {title: 夜, artist: 歌手}
            lines:
              - text: 夜に駆ける
                start_ms: 1000
                words:
                  - {text: 夜, start_ms: 1000}
                  - {text: に, start_ms: 1400}
                  - {text: 駆ける, start_ms: 1700, end_ms: 2600}
            """.trimIndent()
        )?.lyrics?.synced?.single())
        val words = requireNotNull(line.words)
        assertEquals("夜に駆ける", line.line)
        assertEquals(listOf(true, false, false), words.map { it.startsNewWord })
        assertEquals(listOf(1_400, 1_700, 2_600), words.map { it.endTime })
    }

    @Test fun `a line whose words lack starts keeps its text without word timing`() {
        val line = requireNotNull(Lyricsfile.parse(
            "version: '1.0'\nmetadata: {title: A, artist: B}\nlines:\n  - text: ignored\n    start_ms: 5\n    words:\n      - {text: 'Hello '}\n      - {text: world, start_ms: 9}\n"
        )?.lyrics?.synced?.single())
        assertEquals("Hello world", line.line)
        assertNull(line.words)
    }

    @Test fun `written documents read back the same`() {
        val original = Lyrics(
            synced = listOf(
                SyncedLine(
                    time = 1_000, line = "Hello there", endTime = 2_400,
                    words = listOf(
                        SyncedWord(1_000, "Hello", startsNewWord = true, endTime = 1_600),
                        SyncedWord(1_600, "there", startsNewWord = true, endTime = 2_400)
                    ),
                    translation = "Hola"
                ),
                SyncedLine(time = 3_000, line = "")
            )
        )
        val yaml = Lyricsfile.serialize(original, Lyricsfile.Metadata(title = "Song", artist = "Artist", durationMs = 200_000))

        val root = Load(LoadSettings.builder().build()).loadFromString(yaml) as Map<*, *>
        assertEquals("1.0", root["version"])
        val lines = root["lines"] as List<*>
        // The translation is its own line at the same time, as LRCLIB writes translated LRC.
        assertEquals(3, lines.size)
        val words = (lines[0] as Map<*, *>)["words"] as List<*>
        assertEquals(listOf("Hello ", "there"), words.map { (it as Map<*, *>)["text"] })
        assertEquals("Hola", (lines[1] as Map<*, *>)["text"])
        assertEquals(1_000, (lines[1] as Map<*, *>)["start_ms"])

        val back = requireNotNull(Lyricsfile.parse(yaml)).lyrics.synced!!
        assertEquals(listOf(1_000, 1_000, 3_000), back.map { it.time })
        assertEquals(original.synced!![0].words, back[0].words)
        assertEquals(2_400, back[0].endTime)
        assertEquals("", back[2].line)
    }

    @Test fun `written plain lyrics drop generated romanization`() {
        val yaml = Lyricsfile.serialize(
            Lyrics(plain = listOf("こんにちは\nKonnichiwa", "second")),
            Lyricsfile.Metadata(title = "t", artist = "a")
        )
        val document = requireNotNull(Lyricsfile.parse(yaml))
        assertEquals(listOf("こんにちは", "second"), document.lyrics.plain)
        assertTrue(yaml.contains("plain: |"), yaml)
    }

    @Test fun `recognises Lyricsfiles and nothing else`() {
        assertTrue(Lyricsfile.looksLikeLyricsfile(wordSynced))
        assertTrue(Lyricsfile.looksLikeLyricsfile("﻿" + lrcLibLineLevel))
        assertTrue(Lyricsfile.looksLikeLyricsfile("""{"version":"1.0","metadata":{"title":"a","artist":"b"},"lines":[]}"""))
        assertFalse(Lyricsfile.looksLikeLyricsfile("[ti:Song]\n[00:01.00]version: 1 of me\n[00:02.00]lines: two"))
        assertFalse(Lyricsfile.looksLikeLyricsfile("""<tt xmlns="http://www.w3.org/ns/ttml"><body/></tt>"""))
        assertFalse(Lyricsfile.looksLikeLyricsfile("""{"plain":["a"],"synced":[],"areFromRemote":false}"""))
        assertFalse(Lyricsfile.looksLikeLyricsfile("Plain lyrics\nversion 2\nmetadata of a heart"))
    }

    @Test fun `the JSON form is a Lyricsfile too`() {
        val lyrics = requireNotNull(Lyricsfile.parse(
            """{"version":"1.0","metadata":{"title":"a","artist":"b"},"lines":[{"text":"x","start_ms":10}]}"""
        )).lyrics
        assertEquals(10, lyrics.synced!!.single().time)
    }

    @Test fun `rejects other versions, broken YAML and alias bombs`() {
        assertNull(Lyricsfile.parse("version: '2.0'\nmetadata: {title: a, artist: b}\nlines: []\n"))
        val broken = "version: '1.0'\nmetadata:\n  title: [unclosed\nlines:\n"
        assertNull(Lyricsfile.parse(broken))
        assertTrue(Lyricsfile.isDefinitelyLyricsfile(broken))
        val bomb = buildString {
            append("version: '1.0'\nmetadata: {title: a, artist: b}\n")
            append("a: &a [\"lol\",\"lol\",\"lol\",\"lol\",\"lol\",\"lol\",\"lol\",\"lol\",\"lol\"]\n")
            for (level in 'b'..'k') {
                val previous = level - 1
                append("$level: &$level [*$previous,*$previous,*$previous,*$previous,*$previous,*$previous,*$previous,*$previous,*$previous]\n")
            }
            append("lines: []\n")
        }
        assertNull(Lyricsfile.parse(bomb))
    }

    @Test fun `LRCLIB records give their Lyricsfile only when it is word timed`() {
        fun record(lyricsfile: String?) = LrcLibResponse(
            id = 1, name = "Song", artistName = "Artist", albumName = "", duration = 200.0,
            plainLyrics = "plain", syncedLyrics = "[00:01.00]synced", lyricsfile = lyricsfile
        )
        val manyWordLines = (0 until 4).joinToString("\n") { i ->
            val start = 1_000 + i * 3_000
            """
            |  - text: 'word pair'
            |    start_ms: $start
            |    words:
            |      - {text: 'word ', start_ms: $start, end_ms: ${start + 500}}
            |      - {text: 'pair', start_ms: ${start + 500}, end_ms: ${start + 1_000}}
            """.trimMargin()
        }
        val wordTimed = "version: '1.0'\nmetadata: {title: Song, artist: Artist}\nlines:\n$manyWordLines\n"

        assertEquals(wordTimed, record(wordTimed).wordTimedLyricsfile())
        assertEquals(wordTimed, record(wordTimed).preferredRawLyrics())
        // Line-level documents add nothing to the synced LRC.
        assertNull(record(lrcLibLineLevel).wordTimedLyricsfile())
        assertEquals("[00:01.00]synced", record(lrcLibLineLevel).preferredRawLyrics())
        // A single word-timed line isn't enough to prefer it (same rule as other word sources).
        assertNull(record(wordSynced).wordTimedLyricsfile())
        assertNotNull(Lyricsfile.parse(wordSynced))
        assertEquals("plain", record(null).copy(syncedLyrics = " ").preferredRawLyrics())
    }
}
