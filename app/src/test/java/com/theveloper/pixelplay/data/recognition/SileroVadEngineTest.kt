package com.theveloper.pixelplay.data.recognition

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.recognition.ambient.SileroVadEngine
import com.theveloper.pixelplay.data.recognition.ambient.VadListener
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SileroVadEngineTest {

    private fun generatePcmChunk(amplitude: Short, sizeSamples: Int = 512): ByteArray {
        val buffer = ByteBuffer.allocate(sizeSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sizeSamples) {
            // Alternating sign to simulate AC audio waveform
            val sample = if (i % 2 == 0) amplitude else (-amplitude).toShort()
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    @Test
    fun `does not trigger speech start on silence`() {
        var speechStarted = false
        val listener = object : VadListener {
            override fun onSpeechStart() { speechStarted = true }
            override fun onSpeechEnd(fullPcmAudio: ByteArray) {}
        }
        val engine = SileroVadEngine(listener = listener)

        val silentChunk = generatePcmChunk(10) // Very low amplitude
        for (i in 0 until 20) {
            engine.processChunk(silentChunk)
        }

        assertThat(speechStarted).isFalse()
    }

    @Test
    fun `triggers speech start and end on voiced utterance`() {
        var speechStarted = false
        var speechEnded = false
        var capturedAudio: ByteArray? = null

        val listener = object : VadListener {
            override fun onSpeechStart() { speechStarted = true }
            override fun onSpeechEnd(fullPcmAudio: ByteArray) {
                speechEnded = true
                capturedAudio = fullPcmAudio
            }
        }
        val engine = SileroVadEngine(listener = listener)

        val silentChunk = generatePcmChunk(10)
        val loudChunk = generatePcmChunk(2000) // Loud speech level

        // 1. Send silence first to establish noise floor
        for (i in 0 until 15) {
            engine.processChunk(silentChunk)
        }
        assertThat(speechStarted).isFalse()

        // 2. Send 5 loud speech chunks (onset trigger requires 3 frames)
        for (i in 0 until 5) {
            engine.processChunk(loudChunk)
        }
        assertThat(speechStarted).isTrue()
        assertThat(speechEnded).isFalse()

        // 3. Send 20 silence chunks to conclude utterance (requires 16 silence frames)
        for (i in 0 until 20) {
            engine.processChunk(silentChunk)
        }
        assertThat(speechEnded).isTrue()
        assertThat(capturedAudio).isNotNull()
        assertThat(capturedAudio!!.size).isGreaterThan(0)
    }
}
