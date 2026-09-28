package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/**
 * [delegate] with [RebufferBackoff] on top: after repeated rebuffers on a slow connection,
 * playback resumes only once more audio is buffered. Start-up and seeks are unchanged, and
 * media that has finished loading always plays (the player doesn't ask then).
 *
 * One instance per player, as the backoff keeps that player's rebuffer history.
 */
@UnstableApi
internal class RebufferBackoffLoadControl(
    private val delegate: LoadControl,
    private val backoff: RebufferBackoff = RebufferBackoff()
) : LoadControl {

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        if (!delegate.shouldStartPlayback(parameters)) return false
        if (!parameters.rebuffering) return true
        val requiredMs = backoff.requiredBufferMs(parameters.lastRebufferRealtimeMs)
        if (requiredMs <= 0L) return true
        val speed = parameters.playbackSpeed.takeIf { it > 0f } ?: 1f
        return parameters.bufferedDurationUs / speed >= requiredMs * 1_000f
    }

    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<ExoTrackSelection?>
    ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldContinueLoading(parameters)

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long
    ): Boolean = delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)
}
