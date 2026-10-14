package com.theveloper.pixelplay.data.recognition.livesync

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class AudioAlignerTest {
    private val rate = AudioFingerprint.SAMPLE_RATE

    /** A fake song: a new 3-note chord every 0.5 s plus a click on each beat. */
    private fun song(seconds: Int, seed: Int): FloatArray {
        val random = Random(seed)
        val out = FloatArray(seconds * rate)
        val step = rate / 2
        var chord = IntArray(3)
        for (i in out.indices) {
            if (i % step == 0) chord = IntArray(3) { 48 + random.nextInt(24) }
            var v = 0.0
            for (note in chord) v += sin(2 * PI * 440.0 * 2.0.pow((note - 69) / 12.0) * i / rate)
            val beat = i % step
            if (beat < 200) v += (random.nextDouble() - 0.5) * 2 * (1 - beat / 200.0)
            out[i] = (v / 4).toFloat()
        }
        return out
    }

    /** What a phone mic hears: quieter, with room noise. */
    private fun micClip(source: FloatArray, startSec: Double, seconds: Double, seed: Int): FloatArray {
        val random = Random(seed)
        val start = (startSec * rate).toInt()
        return FloatArray((seconds * rate).toInt()) { i ->
            (source[start + i] * 0.3f + (random.nextFloat() - 0.5f) * 0.08f)
        }
    }

    @Test
    fun `finds where a noisy clip sits in the song`() {
        val audio = song(90, seed = 1)
        val songFeatures = AudioFingerprint.features(audio)
        val match = AudioAligner.align(AudioFingerprint.features(micClip(audio, 41.3, 6.0, seed = 9)), songFeatures)
        assertNotNull(match)
        assertTrue(match!!.isConfident, "should be confident: $match")
        assertTrue(abs(match.offsetMs - 41_300) < 60, "offset ${match.offsetMs}")
    }

    @Test
    fun `a different song is never a confident match`() {
        val songFeatures = AudioFingerprint.features(song(90, seed = 1))
        val other = song(20, seed = 2)
        val match = AudioAligner.align(AudioFingerprint.features(micClip(other, 5.0, 6.0, seed = 3)), songFeatures)
        assertFalse(match?.isConfident == true, "must not lock onto another song: $match")
    }

    @Test
    fun `silence is never a confident match`() {
        val songFeatures = AudioFingerprint.features(song(60, seed = 4))
        val quiet = FloatArray(6 * rate) { (Random(it).nextFloat() - 0.5f) * 0.001f }
        val match = AudioAligner.align(AudioFingerprint.features(quiet), songFeatures)
        assertFalse(match?.isConfident == true)
    }
}
