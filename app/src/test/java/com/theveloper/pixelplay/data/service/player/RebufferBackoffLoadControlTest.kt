package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RebufferBackoffLoadControlTest {
    /** Starts playback at 1 s of buffer, like the app's after-rebuffer threshold. */
    private class OneSecond : LoadControl {
        override fun getAllocator(playerId: PlayerId): Allocator = throw UnsupportedOperationException()
        override fun shouldStartPlayback(parameters: LoadControl.Parameters) = parameters.bufferedDurationUs >= 1_000_000
    }

    private val control = RebufferBackoffLoadControl(OneSecond())

    private fun params(bufferedMs: Long, rebufferStartedMs: Long?, speed: Float = 1f) = LoadControl.Parameters(
        PlayerId.UNSET,
        Timeline.EMPTY,
        MediaSource.MediaPeriodId(Any()),
        /* playbackPositionUs= */ 0L,
        /* bufferedDurationUs= */ bufferedMs * 1_000,
        speed,
        /* playWhenReady= */ true,
        /* rebuffering= */ rebufferStartedMs != null,
        /* targetLiveOffsetUs= */ Long.MIN_VALUE + 1,
        /* lastRebufferRealtimeMs= */ rebufferStartedMs ?: (Long.MIN_VALUE + 1)
    )

    @Test fun `start-up and seeks keep the normal threshold`() {
        assertTrue(control.shouldStartPlayback(params(bufferedMs = 1_000, rebufferStartedMs = null)))
        assertFalse(control.shouldStartPlayback(params(bufferedMs = 500, rebufferStartedMs = null)))
    }

    @Test fun `repeated rebuffers wait for more audio`() {
        // First rebuffer: resumes at the normal second.
        assertTrue(control.shouldStartPlayback(params(bufferedMs = 1_000, rebufferStartedMs = 100_000)))
        // Dry again 8 s later: 3 s this time.
        assertFalse(control.shouldStartPlayback(params(bufferedMs = 1_000, rebufferStartedMs = 108_000)))
        assertFalse(control.shouldStartPlayback(params(bufferedMs = 2_900, rebufferStartedMs = 108_000)))
        assertTrue(control.shouldStartPlayback(params(bufferedMs = 3_000, rebufferStartedMs = 108_000)))
        // And again: 6 s, counted in playout time at the current speed.
        assertFalse(control.shouldStartPlayback(params(bufferedMs = 5_000, rebufferStartedMs = 116_000)))
        assertTrue(control.shouldStartPlayback(params(bufferedMs = 9_000, rebufferStartedMs = 116_000, speed = 1.5f)))
    }

    @Test fun `the delegate still has the last word`() {
        control.shouldStartPlayback(params(bufferedMs = 1_000, rebufferStartedMs = 100_000))
        assertFalse(control.shouldStartPlayback(params(bufferedMs = 500, rebufferStartedMs = 100_000)))
    }
}
