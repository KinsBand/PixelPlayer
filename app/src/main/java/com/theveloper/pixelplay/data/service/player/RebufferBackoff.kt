package com.theveloper.pixelplay.data.service.player

/**
 * How much audio has to be buffered again before playback resumes after the buffer ran dry.
 *
 * The first rebuffer resumes at the player's normal threshold. When the buffer runs dry again
 * soon after (a new rebuffer starting within [windowMs] of the previous one's start), the
 * connection is not keeping up, and resuming with a second of audio only stops again a second
 * later. Each such rebuffer waits for more audio ([stepsMs]), so playback goes on in longer
 * stretches with fewer stops. A rebuffer after [windowMs] resets to the first step.
 *
 * Used from one player's playback thread only.
 */
internal class RebufferBackoff(
    private val stepsMs: LongArray = longArrayOf(0L, 3_000L, 6_000L, 10_000L),
    private val windowMs: Long = 60_000L
) {
    private var rebufferStartedMs = NONE
    private var streak = 0

    /**
     * Audio (ms) to have buffered before resuming from the rebuffer that started at
     * [startedRealtimeMs] (`LoadControl.Parameters.lastRebufferRealtimeMs`, the same value
     * for every check during one rebuffer). 0 leaves it to the player's default threshold, as
     * does an unknown start time (negative, e.g. `C.TIME_UNSET`).
     */
    fun requiredBufferMs(startedRealtimeMs: Long): Long {
        if (startedRealtimeMs < 0) return 0L
        if (startedRealtimeMs != rebufferStartedMs) {
            val soonAfterPrevious = rebufferStartedMs != NONE && startedRealtimeMs - rebufferStartedMs <= windowMs
            streak = if (soonAfterPrevious) streak + 1 else 1
            rebufferStartedMs = startedRealtimeMs
        }
        return stepsMs[(streak - 1).coerceIn(0, stepsMs.lastIndex)]
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}
