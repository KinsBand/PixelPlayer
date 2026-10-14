package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.ui.graphics.Color

/**
 * Each heard song's stretch of the radio timeline as a 0..1 range: from its start to its end,
 * or (still on) to [nowMs], capped at the song's length when known, so the talk after a song
 * isn't coloured. Songs outside the kept window are dropped.
 */
internal fun radioTimelineSegments(
    tracks: List<com.theveloper.pixelplay.data.radio.RadioHeardTrack>,
    axisStart: Long,
    axisEnd: Long,
    nowMs: Long,
    colorFor: (String?) -> Color,
): List<com.theveloper.pixelplay.presentation.components.TimelineSegment> {
    val span = (axisEnd - axisStart).toFloat()
    if (span <= 0f) return emptyList()
    return tracks.mapNotNull { t ->
        val start = t.startPosMs ?: return@mapNotNull null
        var end = t.endPosMs ?: nowMs
        if (t.durationMs > 0 && !t.approximateStart) end = minOf(end, start + t.durationMs)
        if (end <= axisStart || end <= start) return@mapNotNull null
        com.theveloper.pixelplay.presentation.components.TimelineSegment(
            start = ((start - axisStart) / span).coerceIn(0f, 1f),
            end = ((end - axisStart) / span).coerceIn(0f, 1f),
            color = colorFor(t.coverUrl),
        )
    }
}
