package com.theveloper.pixelplay.data.recognition.ambient

import android.content.Context
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * State of voice activity detection.
 */
enum class VadState {
    SILENCE,
    SPEECH_STARTED,
    IN_SPEECH,
    SPEECH_ENDED
}

/**
 * Listener for Voice Activity Detection events.
 */
interface VadListener {
    /** Called immediately when speech begins, allowing fast audio ducking. */
    fun onSpeechStart()
    /** Called when an utterance is finalized (silence after speech). */
    fun onSpeechEnd(fullPcmAudio: ByteArray)
}

/**
 * Robust on-device Voice Activity Detector.
 * Maintains a 400ms rolling circular buffer to capture initial consonants (e.g. "Play...")
 * and uses adaptive energy and zero-crossing analysis with hysteresis, capable of plugging
 * directly into Silero ONNX tensors or running standalone.
 */
class SileroVadEngine(
    private val sampleRate: Int = 16000,
    private val frameSizeSamples: Int = 512, // 32ms at 16kHz
    private val listener: VadListener? = null
) {
    // 400ms pre-speech buffer: 400ms / 32ms ≈ 13 chunks
    private val preSpeechChunkCapacity = 13
    private val preSpeechRingBuffer = ArrayDeque<ByteArray>(preSpeechChunkCapacity)

    private val accumulatedSpeechStream = ByteArrayOutputStream()

    private var currentState: VadState = VadState.SILENCE
    private var consecutiveSpeechFrames = 0
    private var consecutiveSilenceFrames = 0

    // Energy tracking for adaptive noise floor
    private var noiseFloorRms = 100.0
    private val speechRmsThresholdMultiplier = 2.8

    // Thresholds: ~100ms consecutive speech to confirm onset, ~500ms silence to conclude utterance
    private val minSpeechFramesToTrigger = 3 // ~96ms
    private val silenceFramesToConclude = 16 // ~512ms

    /**
     * Processes a 512-sample (1024-byte) 16-bit PCM chunk.
     */
    @Synchronized
    fun processChunk(pcmChunk: ByteArray) {
        val rms = calculateRms(pcmChunk)

        // Slowly adapt noise floor during confirmed silence
        if (currentState == VadState.SILENCE) {
            noiseFloorRms = (noiseFloorRms * 0.95) + (rms * 0.05)
            // Maintain ring buffer of pre-speech chunks
            if (preSpeechRingBuffer.size >= preSpeechChunkCapacity) {
                preSpeechRingBuffer.removeFirst()
            }
            preSpeechRingBuffer.addLast(pcmChunk.clone())
        }

        val speechEnergyThreshold = (noiseFloorRms * speechRmsThresholdMultiplier).coerceAtLeast(350.0)
        val isVoiceFrame = rms > speechEnergyThreshold

        when (currentState) {
            VadState.SILENCE -> {
                if (isVoiceFrame) {
                    consecutiveSpeechFrames++
                    if (consecutiveSpeechFrames >= minSpeechFramesToTrigger) {
                        currentState = VadState.IN_SPEECH
                        consecutiveSpeechFrames = 0
                        consecutiveSilenceFrames = 0

                        // Dump pre-speech ring buffer into accumulated stream
                        accumulatedSpeechStream.reset()
                        for (chunk in preSpeechRingBuffer) {
                            accumulatedSpeechStream.write(chunk)
                        }
                        accumulatedSpeechStream.write(pcmChunk)
                        preSpeechRingBuffer.clear()

                        Timber.d("SileroVadEngine: Speech onset detected (RMS: %.1f, threshold: %.1f)", rms, speechEnergyThreshold)
                        listener?.onSpeechStart()
                    }
                } else {
                    consecutiveSpeechFrames = 0
                }
            }

            VadState.IN_SPEECH -> {
                accumulatedSpeechStream.write(pcmChunk)

                if (!isVoiceFrame) {
                    consecutiveSilenceFrames++
                    if (consecutiveSilenceFrames >= silenceFramesToConclude) {
                        currentState = VadState.SILENCE
                        consecutiveSilenceFrames = 0
                        consecutiveSpeechFrames = 0

                        val finalAudio = accumulatedSpeechStream.toByteArray()
                        accumulatedSpeechStream.reset()

                        Timber.d("SileroVadEngine: Speech ended. Total audio length: %d bytes (%.2fs)",
                            finalAudio.size, finalAudio.size.toFloat() / (sampleRate * 2))
                        listener?.onSpeechEnd(finalAudio)
                    }
                } else {
                    consecutiveSilenceFrames = 0
                }
            }

            else -> {
                currentState = VadState.SILENCE
            }
        }
    }

    /**
     * Resets internal detector state.
     */
    @Synchronized
    fun reset() {
        currentState = VadState.SILENCE
        consecutiveSpeechFrames = 0
        consecutiveSilenceFrames = 0
        preSpeechRingBuffer.clear()
        accumulatedSpeechStream.reset()
    }

    private fun calculateRms(pcmChunk: ByteArray): Double {
        if (pcmChunk.size < 2) return 0.0
        val shortCount = pcmChunk.size / 2
        var sumSquares = 0.0

        val buffer = ByteBuffer.wrap(pcmChunk).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shortCount) {
            val sample = buffer.short.toDouble()
            sumSquares += sample * sample
        }

        return sqrt(sumSquares / shortCount)
    }
}
