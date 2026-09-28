package com.theveloper.pixelplay.data.service.player

/** ExoPlayer [androidx.media3.exoplayer.DefaultLoadControl] buffer durations (ms) for a build of the player. */
internal data class LoadControlBufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
    /**
     * Played audio kept behind the playhead. [SmartResumePolicy] rewinds a few seconds when a
     * long pause ends; without a back buffer that rewind discarded everything buffered ahead
     * and, for a stream, waited on a new network request before the song could resume.
     */
    val backBufferMs: Int = RESUME_BACK_BUFFER_MS,
)

/** Covers [SmartResumePolicy.REWIND_MS] plus the pause position's rounding. */
internal const val RESUME_BACK_BUFFER_MS = 10_000

/**
 * Bounds each player's buffer target by both duration and bytes. Byte targets take priority
 * for high-bitrate audio so overlapping players do not retain a full minute each. Low-RAM
 * devices use smaller targets; both profiles retain the same short startup thresholds.
 * These are allocator targets, not a cap on the application's total memory consumption.
 */
internal fun loadControlBufferProfileFor(
    isLowRamDevice: Boolean,
    heapLimitBytes: Long = Long.MAX_VALUE
): LoadControlBufferProfile {
    val base = baseLoadControlBufferProfileFor(isLowRamDevice)
    if (heapLimitBytes == Long.MAX_VALUE) return base
    // ExoPlayer's DefaultAllocator buffers live on the Java heap. Two players overlap during
    // crossfades (plus the preloaded next item), so 24 MB each was ~20% of a 256 MB heap just
    // for read-ahead. Cap each player at 1/20 of the heap (12.8 MB on 256 MB, 16 MB max).
    val heapCap = (heapLimitBytes / 20).coerceIn(4L * 1024 * 1024, 16L * 1024 * 1024).toInt()
    return base.copy(targetBufferBytes = minOf(base.targetBufferBytes, heapCap))
}

private fun baseLoadControlBufferProfileFor(isLowRamDevice: Boolean): LoadControlBufferProfile {
    return if (isLowRamDevice) {
        LoadControlBufferProfile(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 250,
            bufferForPlaybackAfterRebufferMs = 1_000,
            targetBufferBytes = 12 * 1024 * 1024
        )
    } else {
        LoadControlBufferProfile(
            minBufferMs = 30_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 250,
            bufferForPlaybackAfterRebufferMs = 1_000,
            targetBufferBytes = 24 * 1024 * 1024
        )
    }
}
