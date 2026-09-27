package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.abs

/**
 * Detects leading and trailing silence in mono PCM audio.
 */
object SilenceDetector {

    /** Default silence threshold: -60 dBFS (amplitude 0.001f). */
    private const val DEFAULT_SILENCE_THRESHOLD = 0.001f
    private const val FRAME_SIZE_MS = 50

    data class SilenceResult(
        val silenceAtStartMs: Long,
        val silenceAtEndMs: Long
    )

    fun detect(
        samples: FloatArray,
        sampleRate: Int,
        threshold: Float = DEFAULT_SILENCE_THRESHOLD
    ): SilenceResult {
        if (samples.isEmpty() || sampleRate <= 0) {
            return SilenceResult(0L, 0L)
        }

        val frameSamples = (sampleRate * FRAME_SIZE_MS) / 1000
        if (frameSamples <= 0 || samples.size < frameSamples) {
            return SilenceResult(0L, 0L)
        }

        val totalFrames = samples.size / frameSamples

        // Detect start silence
        var startFrame = 0
        while (startFrame < totalFrames) {
            val offset = startFrame * frameSamples
            if (isFrameActive(samples, offset, frameSamples, threshold)) {
                break
            }
            startFrame++
        }

        // Detect end silence
        var endFrame = totalFrames - 1
        while (endFrame >= startFrame) {
            val offset = endFrame * frameSamples
            if (isFrameActive(samples, offset, frameSamples, threshold)) {
                break
            }
            endFrame--
        }

        val startMs = (startFrame.toLong() * FRAME_SIZE_MS)
        val endMs = ((totalFrames - 1 - endFrame).toLong() * FRAME_SIZE_MS)

        return SilenceResult(
            silenceAtStartMs = startMs,
            silenceAtEndMs = endMs
        )
    }

    private fun isFrameActive(
        samples: FloatArray,
        offset: Int,
        length: Int,
        threshold: Float
    ): Boolean {
        var sumSquare = 0.0
        val end = minOf(offset + length, samples.size)
        for (i in offset until end) {
            val s = samples[i]
            sumSquare += s * s
        }
        val rms = kotlin.math.sqrt(sumSquare / (end - offset)).toFloat()
        return rms > threshold
    }
}
