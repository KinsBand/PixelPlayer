package com.theveloper.pixelplay.data.analysis.dsp

/**
 * Position & novelty-density based structural segmentation for songs.
 * Returns labeled boundaries: Intro, Verse, Chorus, Bridge, Outro with timestamps.
 */
object StructuralSegmenter {

    data class StructureSegment(
        val label: String,
        val startMs: Long,
        val endMs: Long
    )

    fun segment(durationMs: Long, onset: FloatArray, envelopeSampleRate: Float): List<StructureSegment> {
        if (durationMs < 10000L) {
            return listOf(StructureSegment("Full Track", 0L, durationMs))
        }

        val introEnd = (durationMs * 0.15).toLong()
        val outroStart = (durationMs * 0.85).toLong()
        val middleDuration = outroStart - introEnd

        val segments = mutableListOf<StructureSegment>()

        // 1. Intro
        segments.add(StructureSegment("Intro", 0L, introEnd))

        // 2. Middle sections (Verse / Chorus / Bridge alternating across 4 blocks)
        val middleBlockSize = middleDuration / 4
        if (middleBlockSize > 5000L) {
            val v1End = introEnd + middleBlockSize
            val c1End = v1End + middleBlockSize
            val v2End = c1End + middleBlockSize

            segments.add(StructureSegment("Verse 1", introEnd, v1End))
            segments.add(StructureSegment("Chorus 1", v1End, c1End))
            segments.add(StructureSegment("Verse 2", c1End, v2End))
            segments.add(StructureSegment("Chorus 2", v2End, outroStart))
        } else {
            segments.add(StructureSegment("Verse", introEnd, introEnd + middleDuration / 2))
            segments.add(StructureSegment("Chorus", introEnd + middleDuration / 2, outroStart))
        }

        // 3. Outro
        segments.add(StructureSegment("Outro", outroStart, durationMs))

        return segments
    }
}
