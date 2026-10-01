package com.theveloper.pixelplay.data.lyrics.autosync

import com.theveloper.pixelplay.data.model.SyncedLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * End-to-end checks of the automatic lyrics offset: synthetic songs are rendered to PCM, run
 * through [VocalFeatureExtractor] exactly as the app streams decoded audio, and scored by
 * [LyricsOffsetEstimator] against line timestamps that are deliberately early, late, correct,
 * or wrong.
 *
 * The accompaniment is built to be hard: a plucked harmonic note on every beat (sustained,
 * pitched onsets in the vocal band on the same grid as the lines), a kick-like click on every
 * beat and a chord pad that changes every bar. The "singer" is a vibrato sawtooth with a short
 * consonant burst at each syllable, and every line time carries up to ±40 ms of typing error.
 */
class LyricsOffsetEstimatorTest {

    // ─── Line filtering ──────────────────────────────────────────────────────────────────

    @Test
    fun `lineStarts skips title, blank, marker, header and credit lines and merges duplicates`() {
        val lines = listOf(
            SyncedLine(0, "Song Title - Artist"),
            SyncedLine(1_000, "作词 : Somebody"),
            SyncedLine(5_000, "First line"),
            SyncedLine(5_000, "First line (translation)"),
            SyncedLine(8_000, "♪"),
            SyncedLine(9_000, ""),
            SyncedLine(10_000, "[Chorus]"),
            SyncedLine(12_000, "(Instrumental)"),
            SyncedLine(15_000, "Second line"),
        )
        assertEquals(listOf(5_000L, 15_000L), LyricsOffsetEstimator.lineStarts(lines).toList())
    }

    @Test
    fun `too few lines is unsupported`() {
        val song = SyntheticSong(seed = 1, sampleRate = 22_050)
        val estimate = LyricsOffsetEstimator.estimate(song.features(), song.lyricTimes(offsetMs = 0).take(5).toLongArray())
        assertEquals(OffsetEstimate.Verdict.UNSUPPORTED, estimate.verdict)
    }

    // ─── Offsets are found ───────────────────────────────────────────────────────────────

    @Test
    fun `late lyrics get a positive offset`() {
        val song = SyntheticSong(seed = 2, sampleRate = 22_050)
        val estimate = LyricsOffsetEstimator.estimate(song.features(), song.lyricTimes(offsetMs = 600).toLongArray())
        assertEquals(OffsetEstimate.Verdict.APPLY, estimate.verdict, estimate.toString())
        assertTrue(abs(estimate.offsetMs - 600) <= TOLERANCE_MS, estimate.toString())
    }

    @Test
    fun `early lyrics get a negative offset`() {
        val song = SyntheticSong(seed = 3, sampleRate = 22_050)
        val estimate = LyricsOffsetEstimator.estimate(song.features(), song.lyricTimes(offsetMs = -900).toLongArray())
        assertEquals(OffsetEstimate.Verdict.APPLY, estimate.verdict, estimate.toString())
        assertTrue(abs(estimate.offsetMs + 900) <= TOLERANCE_MS, estimate.toString())
    }

    @Test
    fun `lyrics that already follow the singing are left alone`() {
        val song = SyntheticSong(seed = 4, sampleRate = 22_050)
        val estimate = LyricsOffsetEstimator.estimate(song.features(), song.lyricTimes(offsetMs = 0).toLongArray())
        assertNotEquals(OffsetEstimate.Verdict.UNSURE, estimate.verdict, estimate.toString())
        assertTrue(abs(estimate.measuredOffsetMs) <= TOLERANCE_MS, estimate.toString())
        assertTrue(abs(estimate.offsetMs) <= TOLERANCE_MS, estimate.toString())
    }

    @Test
    fun `sample rate does not change the answer`() {
        val low = SyntheticSong(seed = 5, sampleRate = 22_050)
        val high = SyntheticSong(seed = 5, sampleRate = 44_100)
        val times = low.lyricTimes(offsetMs = 400).toLongArray()
        val a = LyricsOffsetEstimator.estimate(low.features(), times)
        val b = LyricsOffsetEstimator.estimate(high.features(), times)
        assertTrue(abs(a.measuredOffsetMs - b.measuredOffsetMs) <= 15, "$a vs $b")
        assertTrue(abs(b.measuredOffsetMs - 400) <= TOLERANCE_MS, b.toString())
    }

    // ─── Nothing is applied without evidence ─────────────────────────────────────────────

    @Test
    fun `lyrics of another song are not applied`() {
        val song = SyntheticSong(seed = 6, sampleRate = 22_050)
        val random = Random(99)
        val times = (0 until song.lineCount).map { random.nextLong(3_000L, song.durationMs - 3_000L) }.sorted()
        val estimate = LyricsOffsetEstimator.estimate(song.features(), times.toLongArray())
        assertNotEquals(OffsetEstimate.Verdict.APPLY, estimate.verdict, estimate.toString())
    }

    @Test
    fun `an instrumental is not shifted`() {
        val song = SyntheticSong(seed = 7, sampleRate = 22_050, withVocals = false)
        val estimate = LyricsOffsetEstimator.estimate(song.features(), song.lyricTimes(offsetMs = 700).toLongArray())
        assertNotEquals(OffsetEstimate.Verdict.APPLY, estimate.verdict, estimate.toString())
    }

    // ─── Synthetic song ──────────────────────────────────────────────────────────────────

    private class SyntheticSong(seed: Int, private val sampleRate: Int, withVocals: Boolean = true) {
        private val random = Random(seed)
        private val beat = 60.0 / random.nextDouble(88.0, 128.0)
        private val bar = 4 * beat
        private val bars = 30
        val durationMs: Long = ((bars * bar + 1.5) * 1000).toLong()
        private val audio = FloatArray((durationMs * sampleRate / 1000).toInt())
        private val trueLineStarts = ArrayList<Double>()
        private val jitter = Random(seed * 31 + 7)

        /** Separate stream for noise samples so the song's structure doesn't depend on the sample rate. */
        private val noise = Random(seed * 17 + 3)

        val lineCount: Int get() = trueLineStarts.size

        init {
            val roots = doubleArrayOf(110.0, 98.0, 130.8, 87.3)
            val pattern = IntArray(4) { random.nextInt(3, 7) }
            for (b in 0 until bars) {
                val barStart = 0.5 + b * bar
                val root = roots[b % roots.size]
                // Chord pad for the whole bar.
                for (mult in doubleArrayOf(2.0, 2.52, 3.0)) addTone(barStart, bar, root * mult, 4, 0.03, attack = 0.02, decay = 0.0)
                for (q in 0 until 4) {
                    val t = barStart + q * beat
                    addNoise(t, 0.03, 0.25) // kick / click
                    addTone(t, beat * 0.95, root * pattern[q], 6, 0.10, attack = 0.003, decay = 6.0) // pluck
                    addTone(t, beat * 0.9, root, 4, 0.12, attack = 0.005, decay = 3.0) // bass
                }
            }
            // Verses in bars 3..12 and 17..27; an instrumental break in between.
            val sungBars = (3..12) + (17..27)
            for (b in sungBars) {
                val start = 0.5 + b * bar + random.nextInt(-1, 2) * beat * 0.5 + random.nextDouble(-0.02, 0.02)
                trueLineStarts += start
                if (withVocals) addLine(start, bar * random.nextDouble(0.55, 0.8))
            }
        }

        fun lyricTimes(offsetMs: Int): List<Long> =
            trueLineStarts.map { (it * 1000 + offsetMs + jitter.nextDouble(-40.0, 40.0)).toLong() }

        fun features(): VocalFeatureTrack {
            val extractor = VocalFeatureExtractor(sampleRate, 0.0, durationMs)
            var i = 0
            while (i < audio.size) {
                val n = minOf(4_096, audio.size - i)
                extractor.push(audio, i, n)
                i += n
            }
            return extractor.finish()
        }

        private fun addLine(start: Double, length: Double) {
            val syllables = random.nextInt(4, 8)
            val each = length / syllables
            var t = start
            val base = random.nextDouble(190.0, 300.0)
            repeat(syllables) { s ->
                if (s == 0 || random.nextDouble() < 0.6) addNoise(t, 0.035, 0.08, highPass = true)
                val f0 = base * Math.pow(2.0, random.nextInt(-3, 5) / 12.0)
                addVoice(t + 0.03, each * 0.9, f0)
                t += each
            }
        }

        private fun addVoice(start: Double, duration: Double, f0: Double) {
            val from = (start * sampleRate).toInt()
            val n = (duration * sampleRate).toInt()
            var phase = 0.0
            for (k in 0 until n) {
                val idx = from + k
                if (idx !in audio.indices) break
                val time = k.toDouble() / sampleRate
                val vibrato = 1 + 0.018 * sin(2 * PI * 5.8 * time) * (time / 0.2).coerceIn(0.0, 1.0)
                phase += f0 * vibrato / sampleRate
                val saw = 2 * (phase % 1.0) - 1
                val env = minOf(1.0, time / 0.025) * minOf(1.0, (duration - time) / 0.06)
                audio[idx] += (0.22 * saw * env).toFloat()
            }
        }

        private fun addTone(start: Double, duration: Double, f0: Double, harmonics: Int, gain: Double, attack: Double, decay: Double) {
            val from = (start * sampleRate).toInt()
            val n = (duration * sampleRate).toInt()
            for (k in 0 until n) {
                val idx = from + k
                if (idx !in audio.indices) break
                val time = k.toDouble() / sampleRate
                var v = 0.0
                for (h in 1..harmonics) v += Math.pow(0.55, (h - 1).toDouble()) * sin(2 * PI * f0 * h * time)
                val env = minOf(1.0, time / attack) * minOf(1.0, (duration - time) / 0.03) * exp(-decay * time)
                audio[idx] += (gain * v * env).toFloat()
            }
        }

        private fun addNoise(start: Double, duration: Double, gain: Double, highPass: Boolean = false) {
            val from = (start * sampleRate).toInt()
            val n = (duration * sampleRate).toInt()
            var previous = 0.0
            for (k in 0 until n) {
                val idx = from + k
                if (idx !in audio.indices) break
                val white = noise.nextDouble(-1.0, 1.0)
                val sample = if (highPass) white - previous else white
                previous = white
                audio[idx] += (gain * sample * (1.0 - k.toDouble() / n)).toFloat()
            }
        }
    }

    private companion object {
        const val TOLERANCE_MS = 60
    }
}
