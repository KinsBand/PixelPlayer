package com.theveloper.pixelplay.data.service.player

enum class PlaybackMode { PROCESSED, BIT_PERFECT }
enum class PlaybackEventKind { PLAY, PAUSE, INTERRUPTION, SEEK, SKIP, NATURAL_END, ERROR }
enum class InterruptionCause { MANUAL, AUDIO_FOCUS, OUTPUT_DISCONNECTED }

data class PlaybackEvent(
    val kind: PlaybackEventKind,
    val mediaId: String?,
    val positionMs: Long,
    val elapsedRealtimeMs: Long,
    val cause: InterruptionCause? = null,
)

/** Decoded format is not proof of the format reaching the DAC. Unknown values stay null. */
data class PlaybackCapabilities(
    val mode: PlaybackMode = PlaybackMode.PROCESSED,
    val decodedSampleRate: Int? = null,
    val decodedPcmEncoding: Int? = null,
    val verifiedOutputSampleRate: Int? = null,
    val bitPerfectVerified: Boolean = false,
    val independentPitchTempoAvailable: Boolean = true,
    val directOutputUnavailableReason: String? = "Direct hardware output has not been verified for this route",
)

data class ResumeAction(val positionMs: Long, val fadeInMs: Long)

/** Monotonic time only. This policy never issues play, so a manual pause stays paused. */
class SmartResumePolicy {
    private data class Pause(val entry: String, val position: Long, val at: Long)
    private var pause: Pause? = null

    fun paused(entry: String, positionMs: Long, nowMs: Long) {
        if (pause == null) pause = Pause(entry, positionMs.coerceAtLeast(0), nowMs)
    }

    fun clear() { pause = null }

    fun resume(entry: String, positionMs: Long, nowMs: Long, phraseStartMs: Long? = null): ResumeAction? {
        val saved = pause ?: return null
        clear()
        // Seeking while paused is an explicit position choice; don't override it.
        if (entry != saved.entry || kotlin.math.abs(positionMs - saved.position) > 250L) return null
        val elapsed = (nowMs - saved.at).coerceAtLeast(0)
        if (elapsed < 30_000L) return ResumeAction(positionMs, 0)
        val target = if (elapsed > 300_000L && phraseStartMs != null && phraseStartMs in 0..positionMs) {
            phraseStartMs
        } else (positionMs - REWIND_MS).coerceAtLeast(0)
        return ResumeAction(target, 300L)
    }

    companion object {
        /** How far a long pause rewinds; the players keep this much audio behind the playhead. */
        const val REWIND_MS = 7_000L
    }
}
