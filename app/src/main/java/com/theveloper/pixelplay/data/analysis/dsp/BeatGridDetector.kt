package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BeatGridDetector {

    /**
     * Estimates beat timestamps by aligning a grid of the given BPM to the onset envelope.
     *
     * @param onset The onset strength envelope.
     * @param envelopeSampleRate The frame rate of the onset envelope in Hz.
     * @param bpm The estimated BPM of the track.
     * @param durationMs The total duration of the track in milliseconds.
     * @return A list of beat timestamps in seconds.
     */
    fun detectBeatGrid(
        onset: FloatArray,
        envelopeSampleRate: Float,
        bpm: Int,
        durationMs: Long
    ): List<Float> {
        if (onset.isEmpty() || envelopeSampleRate <= 0f || bpm <= 0) return emptyList()

        val durationSeconds = durationMs / 1000f
        val beatInterval = 60.0f / bpm // Duration of one beat in seconds
        val beatIntervalFrames = (beatInterval * envelopeSampleRate).roundToInt()

        if (beatIntervalFrames <= 0) return emptyList()

        // Scan phase offsets (0..beatIntervalFrames) to find the one that overlaps best with onset peaks.
        var bestOffsetFrames = 0
        var maxScore = -Float.MAX_VALUE

        // Scan step of 1 frame is cheap enough since beatIntervalFrames is usually small (e.g. at 120bpm, 100fps -> 50 frames)
        for (offset in 0 until beatIntervalFrames) {
            var score = 0f
            var count = 0
            
            var frame = offset
            while (frame < onset.size) {
                // Sum the onset value at each expected beat position
                score += onset[frame]
                count++
                frame += beatIntervalFrames
            }
            
            // Normalize by number of beats checked to avoid bias towards fewer beats
            val avgScore = if (count > 0) score / count else 0f
            if (avgScore > maxScore) {
                maxScore = avgScore
                bestOffsetFrames = offset
            }
        }

        // Generate timestamps
        val startOffsetSeconds = bestOffsetFrames / envelopeSampleRate
        val beats = mutableListOf<Float>()
        var t = startOffsetSeconds
        while (t < durationSeconds) {
            // Keep precision to 3 decimal places (milliseconds)
            val roundedTimestamp = (t * 1000f).roundToInt() / 1000f
            beats.add(roundedTimestamp)
            t += beatInterval
        }

        return beats
    }
}
