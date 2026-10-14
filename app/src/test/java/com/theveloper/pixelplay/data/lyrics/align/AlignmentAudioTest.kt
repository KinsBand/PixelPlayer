package com.theveloper.pixelplay.data.lyrics.align

import com.theveloper.pixelplay.data.analysis.dsp.Resampler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class AlignmentAudioTest {

    @Test fun `streaming resampler matches resampling the whole signal`() {
        for (rate in listOf(44_100, 48_000, 22_050)) {
            val n = rate * 7 + 123
            val signal = FloatArray(n) { (sin(it * 0.013) * 0.5 + sin(it * 0.21) * 0.2).toFloat() }
            val whole = Resampler.resample(signal, rate, 16_000)
            val streamed = ArrayList<Float>()
            val r = StreamingResampler(rate, 16_000) { s, c -> for (i in 0 until c) streamed += s[i] }
            val rnd = Random(rate)
            var pos = 0
            while (pos < n) {
                val c = minOf(n - pos, rnd.nextInt(1, 9000))
                r.push(signal, pos, c)
                pos += c
            }
            r.finish()
            assertTrue(abs(streamed.size - whole.size) <= 1, "$rate: ${streamed.size} vs ${whole.size}")
            var worst = 0f
            for (i in 0 until minOf(streamed.size, whole.size)) worst = maxOf(worst, abs(streamed[i] - whole[i]))
            assertTrue(worst < 1e-4f, "$rate: max difference $worst")
        }
    }

    /** Emits token 5 on frames whose 400-sample window holds a click; blank otherwise. */
    private class ClickModel : CtcAcousticModel {
        override val vocabSize = 32
        override val hopSamples = 320
        override val sampleRate = 16_000
        var calls = 0
        override fun logProbs(samples: FloatArray, length: Int): FloatArray {
            calls++
            val frames = (length - 400) / 320 + 1
            val out = FloatArray(frames * 32) { -10f }
            for (f in 0 until frames) {
                // A click is the sample at the start of a frame (the window's first hop).
                val click = samples[f * 320] > 5f
                out[f * 32 + if (click) 5 else 0] = 0f
            }
            return out
        }
        override fun close() {}
    }

    @Test fun `chunked emissions keep frame indices on the song timeline across chunks`() {
        val model = ClickModel()
        val emissions = ChunkedEmissions(model, coreFrames = 100, contextFrames = 10)
        val total = 16_000 * 9 + 777
        val clickFrames = listOf(3, 99, 100, 101, 250, 251, 399, 400)
        val audio = FloatArray(total)
        for (f in clickFrames) audio[f * 320] = 1f
        var pos = 0
        while (pos < total) {
            val c = minOf(total - pos, 4096)
            emissions.push(audio.copyOfRange(pos, pos + c), c)
            pos += c
        }
        val em = emissions.finish(20.0)
        assertTrue(model.calls > 3)
        assertTrue(abs(em.frames - total / 320) <= 2, "frames ${em.frames}")
        val found = (0 until em.frames).filter { em.at(it, 5) == 0f }
        assertEquals(clickFrames, found)
    }
}
