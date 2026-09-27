package com.theveloper.pixelplay.data.analysis.dsp

/**
 * Novelty curve analysis for fade-in and fade-out marker detection.
 */
object FadeDetector {

    data class FadeResult(
        val fadeInEndMs: Long,
        val fadeOutStartMs: Long
    )

    fun detectFades(samples: FloatArray, sampleRate: Int, durationMs: Long): FadeResult {
        if (samples.isEmpty() || sampleRate <= 0 || durationMs <= 0) {
            return FadeResult(0L, durationMs)
        }

        val frameSamples = (sampleRate * 0.1).toInt() // 100ms frames
        if (frameSamples <= 0 || samples.size < frameSamples) {
            return FadeResult(0L, durationMs)
        }

        val totalFrames = samples.size / frameSamples

        // Compute RMS per frame
        val frameRms = FloatArray(totalFrames)
        for (i in 0 until totalFrames) {
            var sumSquare = 0.0
            val offset = i * frameSamples
            for (j in 0 until frameSamples) {
                val s = samples[offset + j]
                sumSquare += s * s
            }
            frameRms[i] = kotlin.math.sqrt(sumSquare / frameSamples).toFloat()
        }

        val maxRms = frameRms.maxOrNull() ?: 1f
        if (maxRms < 1e-4f) return FadeResult(0L, durationMs)

        // Fade in end: frame where RMS reaches 80% of maxRms within first 20% of track
        val firstQuarter = (totalFrames * 0.2).toInt()
        var fadeInFrame = 0
        for (i in 0 until minOf(firstQuarter, totalFrames)) {
            if (frameRms[i] >= maxRms * 0.7f) {
                fadeInFrame = i
                break
            }
        }

        // Fade out start: frame after 80% mark where RMS drops below 70% of maxRms monotonically
        val lastQuarter = (totalFrames * 0.8).toInt()
        var fadeOutFrame = totalFrames - 1
        for (i in totalFrames - 1 downTo maxOf(lastQuarter, 0)) {
            if (frameRms[i] >= maxRms * 0.7f) {
                fadeOutFrame = i
                break
            }
        }

        val fadeInMs = (fadeInFrame.toLong() * 100L)
        val fadeOutMs = (fadeOutFrame.toLong() * 100L)

        return FadeResult(
            fadeInEndMs = fadeInMs,
            fadeOutStartMs = if (fadeOutMs > fadeInMs) fadeOutMs else durationMs
        )
    }
}
