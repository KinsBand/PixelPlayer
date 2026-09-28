package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.ui.text.font.FontWeight
import com.theveloper.pixelplay.data.database.TrackAnalysisEntity
import com.theveloper.pixelplay.data.lyrics.SongSection
import com.theveloper.pixelplay.data.lyrics.SongSectionKind
import com.theveloper.pixelplay.data.lyrics.SongStructure
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.presentation.components.activeLyricWeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricExpressionTest {

    private val song = Song(
        id = "1",
        title = "Song",
        artist = "Artist",
        genre = "Pop",
        albumArtUriString = null,
        artistId = 1L,
        albumId = 1L,
        contentUriString = "content://dummy/1",
        duration = 100_000L,
        bitrate = null,
        sampleRate = null,
        album = "Album",
        path = "path",
        mimeType = "audio/mpeg",
        trackNumber = 0,
        discNumber = null
    )

    /** Quiet first half, loud second half. */
    private fun analysis(energy: Float = 0.6f, valence: Float = 0.6f, bpm: Int = 100) = TrackAnalysisEntity(
        trackId = 1L,
        bpm = bpm,
        musicKey = "C major",
        keyCamelot = "8B",
        loudness = -10f,
        energy = energy,
        valence = valence,
        waveform = ByteArray(1000) { if (it < 500) 60 else (230).toByte() }
    )

    private fun lineExpr(text: String, startMs: Long, profile: SongExpressionProfile) =
        buildLineExpression(
            text = text,
            words = expressionWords(text, SyncedLine(time = startMs.toInt(), line = text), startMs + 2_000, null, null),
            lineStartMs = startMs,
            profile = profile,
            restWeight = FontWeight.Normal,
            activeFor = ::activeLyricWeight
        )

    @Test
    fun `louder moments get heavier words`() {
        val profile = LyricExpressionEngine.build(song, analysis(), null, null, 100_000L)
        val quiet = lineExpr("hello there", 10_000, profile).restSpans.first().item.fontWeight!!.weight
        val loud = lineExpr("hello there", 80_000, profile).restSpans.first().item.fontWeight!!.weight
        assertTrue(loud > quiet, "loud=$loud quiet=$quiet")
    }

    @Test
    fun `current line is always heavier than the same word at rest`() {
        val profile = LyricExpressionEngine.build(song, analysis(), null, null, 100_000L)
        val expr = lineExpr("one two three", 80_000, profile)
        expr.restSpans.zip(expr.activeSpans).forEach { (r, a) ->
            assertTrue(a.item.fontWeight!!.weight > r.item.fontWeight!!.weight)
            assertEquals(r.start, a.start)
            assertEquals(r.end, a.end)
        }
    }

    @Test
    fun `backing vocals in brackets sit back`() {
        val profile = LyricExpressionEngine.build(song, analysis(), null, null, 100_000L)
        val expr = lineExpr("sing (sing)", 80_000, profile)
        val main = expr.restSpans[0].item.fontWeight!!.weight
        val backing = expr.restSpans[1].item.fontWeight!!.weight
        assertTrue(backing < main, "main=$main backing=$backing")
    }

    @Test
    fun `chorus lines lift compared with verses at the same loudness`() {
        val structure = SongStructure(
            sections = listOf(
                SongSection(SongSectionKind.VERSE, "Verse", 0, 50_000),
                SongSection(SongSectionKind.CHORUS, "Chorus", 50_000, 100_000),
            ),
            source = "tags",
            lyricsFingerprint = "x"
        )
        val flat = analysis().copy(waveform = ByteArray(1000) { 150.toByte() })
        val profile = LyricExpressionEngine.build(song, flat, null, structure, 100_000L)
        val verse = lineExpr("words here", 20_000, profile).restSpans.first().item.fontSize.value
        val chorus = lineExpr("words here", 70_000, profile).restSpans.first().item.fontSize.value
        assertTrue(chorus > verse, "chorus=$chorus verse=$verse")
    }

    @Test
    fun `calm songs start lighter than driving songs`() {
        val calm = LyricExpressionEngine.build(song, analysis(energy = 0.2f), null, null, 100_000L)
        val driving = LyricExpressionEngine.build(song, analysis(energy = 0.9f), null, null, 100_000L)
        assertTrue(calm.baseWeightShift < driving.baseWeightShift)
        assertTrue(calm.baseTracking > driving.baseTracking)
    }

    @Test
    fun `songs without analysis still produce readable spans`() {
        val profile = LyricExpressionEngine.build(song, null, null, null, 100_000L)
        val expr = lineExpr("PLAIN words!", 10_000, profile)
        assertEquals(2, expr.restSpans.size)
        expr.restSpans.forEach { r ->
            val w = r.item.fontWeight!!.weight
            assertTrue(w in 200..800)
        }
    }
}
