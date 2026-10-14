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
        val profile = LyricExpressionEngine.build(song, analysis(), null, null, null, 100_000L)
        val quiet = lineExpr("hello there", 10_000, profile).restSpans.first().item.fontWeight!!.weight
        val loud = lineExpr("hello there", 80_000, profile).restSpans.first().item.fontWeight!!.weight
        assertTrue(loud > quiet, "loud=$loud quiet=$quiet")
    }

    @Test
    fun `current line is always heavier than the same word at rest`() {
        val profile = LyricExpressionEngine.build(song, analysis(), null, null, null, 100_000L)
        val expr = lineExpr("one two three", 80_000, profile)
        expr.restSpans.zip(expr.activeSpans).forEach { (r, a) ->
            assertTrue(a.item.fontWeight!!.weight > r.item.fontWeight!!.weight)
            assertEquals(r.start, a.start)
            assertEquals(r.end, a.end)
        }
    }

    @Test
    fun `backing vocals in brackets sit back`() {
        val profile = LyricExpressionEngine.build(song, analysis(), null, null, null, 100_000L)
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
        val profile = LyricExpressionEngine.build(song, flat, null, null, structure, 100_000L)
        val verse = lineExpr("words here", 20_000, profile).restSpans.first().item.fontSize.value
        val chorus = lineExpr("words here", 70_000, profile).restSpans.first().item.fontSize.value
        assertTrue(chorus > verse, "chorus=$chorus verse=$verse")
    }

    @Test
    fun `calm songs start lighter than driving songs`() {
        val calm = LyricExpressionEngine.build(song, analysis(energy = 0.2f), null, null, null, 100_000L)
        val driving = LyricExpressionEngine.build(song, analysis(energy = 0.9f), null, null, null, 100_000L)
        assertTrue(calm.baseWeightShift < driving.baseWeightShift)
        assertTrue(calm.baseTracking > driving.baseTracking)
    }

    @Test
    fun `songs without analysis still produce readable spans`() {
        val profile = LyricExpressionEngine.build(song, null, null, null, null, 100_000L)
        val expr = lineExpr("PLAIN words!", 10_000, profile)
        assertEquals(2, expr.restSpans.size)
        expr.restSpans.forEach { r ->
            val w = r.item.fontWeight!!.weight
            assertTrue(w in 200..800)
        }
    }

    private fun syncedLyrics(lines: List<String>, gapMs: Int) = com.theveloper.pixelplay.data.model.Lyrics(
        synced = lines.mapIndexed { i, text -> SyncedLine(time = i * gapMs, line = text) }
    )

    @Test
    fun `songs without analysis still get their own voice from genre and pacing`() {
        val ballad = LyricExpressionEngine.build(
            song.copy(id = "2", genre = "Ballad"),
            null,
            null,
            syncedLyrics(List(12) { "slow and gentle" }, gapMs = 4_000),
            null,
            100_000L
        )
        val rap = LyricExpressionEngine.build(
            song.copy(id = "3", genre = "Hip Hop"),
            null,
            null,
            syncedLyrics(List(12) { "fast words packed in tight every single bar LOUD" }, gapMs = 2_000),
            null,
            100_000L
        )
        assertTrue(rap.energy > ballad.energy, "rap=${rap.energy} ballad=${ballad.energy}")
        assertTrue(rap.baseWeightShift > ballad.baseWeightShift)
        assertTrue(rap.sizeScale > ballad.sizeScale)
        assertTrue(rap.motion.wordLift > ballad.motion.wordLift)
        assertTrue(rap.motion.lineStiffness > ballad.motion.lineStiffness)
    }

    @Test
    fun `the song voice changes the whole line style`() {
        val driving = LyricExpressionEngine.build(song, analysis(energy = 0.95f), null, null, null, 100_000L)
        val base = androidx.compose.ui.text.TextStyle(
            fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp),
            lineHeight = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = FontWeight.Normal
        )
        val voiced = driving.applyTo(base)
        assertTrue(voiced.fontSize.value > base.fontSize.value)
        assertTrue(voiced.fontWeight!!.weight > FontWeight.Normal.weight)
    }

    @Test
    fun `sung words lift and settle back without jumps`() {
        assertEquals(0f, wordLiftAmount(0f))
        assertEquals(0f, wordLiftAmount(1f))
        assertTrue(wordLiftAmount(0.5f) > 0.9f)
        assertTrue(wordLiftAmount(0.02f) < 0.3f)
        assertTrue(wordLiftAmount(0.98f) < 0.1f)
    }

    @Test
    fun `the letter wave follows the cursor and fades behind it`() {
        assertEquals(0f, letterLiftAmount(-0.5f, 2f))
        assertEquals(1f, letterLiftAmount(1f, 2f), 1e-4f)
        assertTrue(letterLiftAmount(2f, 2f) in 0.01f..0.99f)
        assertEquals(0f, letterLiftAmount(3.5f, 2f))
    }

    // ─── Every song: streamed, long, plain ─────────────────────────────────────────────

    private val streamedSong = song.copy(
        id = "yt_abc123",
        genre = null,
        contentUriString = "youtube://abc123",
        youtubeId = "abc123",
        duration = 200_000L
    )

    /** A measured voice: quiet first half, loud second half, over the whole 200 s song. */
    private fun voice(energy: Float = 0.7f) = com.theveloper.pixelplay.data.lyrics.voice.SongVoice(
        songId = streamedSong.id,
        envelope = FloatArray(2_000) { if (it < 1_000) 0.45f else 0.85f },
        stepMs = 100,
        energy = energy,
        dynamics = 0.6f,
        bpm = 128,
        musicKey = "Am",
        analysedAt = 0L
    )

    @Test
    fun `streamed songs with a measured voice follow their loudness`() {
        val profile = LyricExpressionEngine.build(streamedSong, null, voice(), null, null, 200_000L)
        assertEquals(ExpressionSource.MEASURED_VOICE, profile.source)
        assertEquals(128, profile.bpm)
        assertTrue(profile.hasEnvelope)
        val quiet = lineExpr("hello there", 30_000, profile).restSpans.first().item.fontWeight!!.weight
        val loud = lineExpr("hello there", 160_000, profile).restSpans.first().item.fontWeight!!.weight
        assertTrue(loud > quiet, "loud=$loud quiet=$quiet")
    }

    @Test
    fun `streamed songs use the measured energy`() {
        val calm = LyricExpressionEngine.build(streamedSong, null, voice(energy = 0.15f), null, null, 200_000L)
        val driving = LyricExpressionEngine.build(streamedSong, null, voice(energy = 0.95f), null, null, 200_000L)
        assertTrue(calm.baseWeightShift < driving.baseWeightShift)
        assertTrue(calm.sizeScale < driving.sizeScale)
    }

    @Test
    fun `streamed songs without audio use carried metadata`() {
        val tagged = streamedSong.copy(
            mixIntelligence = com.theveloper.pixelplay.data.model.MixIntelligence(energy = 0.9f, valence = 0.8f)
        )
        val profile = LyricExpressionEngine.build(tagged, null, null, null, null, 200_000L)
        assertEquals(ExpressionSource.METADATA, profile.source)
        assertEquals(0.9f, profile.energy, 1e-4f)
    }

    @Test
    fun `the library waveform of a long song only covers its analysed head`() {
        val long = song.copy(duration = 400_000L)
        val profile = LyricExpressionEngine.build(long, analysis(), null, null, null, 400_000L)
        // 150 s sits in the loud half of the 180 s window (mapped onto 400 s it would read quiet).
        assertTrue(profile.intensityAt(150_000) > profile.intensityAt(60_000))
        // Past the analysed window there's no loudness data: neutral.
        assertEquals(0f, profile.intensityAt(300_000))
    }

    @Test
    fun `plain lyrics still give pacing`() {
        val plain = com.theveloper.pixelplay.data.model.Lyrics(plain = List(20) { "words in a plain line here" })
        val stats = LyricExpressionEngine.lyricStats(plain, 200_000L)
        assertTrue(stats != null)
        assertTrue(stats!!.wordsPerSecond > 0f)
    }

    @Test
    fun `plain lines get word cues without timing`() {
        val profile = LyricExpressionEngine.build(song, null, null, null, null, 100_000L)
        val text = "sing it LOUD (softly)"
        val spans = buildUntimedLineExpression(text, profile, FontWeight.Normal)
        val loud = spans.first { text.substring(it.start, it.end) == "LOUD" }.item.fontWeight!!.weight
        val soft = spans.first { text.substring(it.start, it.end) == "(softly)" }.item.fontWeight!!.weight
        assertTrue(loud > FontWeight.Normal.weight)
        assertTrue(soft < FontWeight.Normal.weight)
        assertTrue(spans.none { text.substring(it.start, it.end) == "sing" })
    }
}
