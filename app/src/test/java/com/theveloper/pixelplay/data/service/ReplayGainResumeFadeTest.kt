package com.theveloper.pixelplay.data.service

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import io.mockk.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReplayGainResumeFadeTest {
    @Test fun `fade waits for audible playback and multiplies late volume targets`() = runTest {
        mockkStatic(SystemClock::class)
        try {
            every { SystemClock.elapsedRealtime() } answers { testScheduler.currentTime }
            val player = mockk<ExoPlayer>(relaxed = true)
            val engine = mockk<DualPlayerEngine>(relaxed = true)
            val item = MediaItem.Builder().setMediaId("track").build()
            var volume = 0.5f
            var playing = false
            every { player.volume } answers { volume }
            every { player.volume = any() } answers { volume = firstArg() }
            every { player.playWhenReady } returns true
            every { player.isPlaying } answers { playing }
            every { player.currentMediaItem } returns item
            every { engine.masterPlayer } returns player
            val processor = ReplayGainProcessor(engine, mockk(relaxed = true), this, { item })
            processor.fadeInOnResume(300)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(0f, volume)
            assertTrue(processor.isResumeFading)
            playing = true
            advanceTimeBy(160)
            runCurrent()
            assertTrue(volume > 0f && volume < 0.5f)
            processor.captureUserVolume(0.8f)
            processor.apply(item)
            advanceUntilIdle()
            assertEquals(0.8f, volume)
            assertFalse(processor.isResumeFading)
        } finally { unmockkStatic(SystemClock::class) }
    }

    @Test fun `cancellation restores base volume and an old job cannot cancel a new fade`() = runTest {
        mockkStatic(SystemClock::class)
        try {
            every { SystemClock.elapsedRealtime() } answers { testScheduler.currentTime }
            val player = mockk<ExoPlayer>(relaxed = true)
            val engine = mockk<DualPlayerEngine>(relaxed = true)
            var volume = 0.4f
            every { player.volume } answers { volume }
            every { player.volume = any() } answers { volume = firstArg() }
            every { player.playWhenReady } returns true
            every { player.isPlaying } returns true
            every { engine.masterPlayer } returns player
            val processor = ReplayGainProcessor(engine, mockk(relaxed = true), this, { null })
            processor.fadeInOnResume(300)
            runCurrent()
            processor.cancelResumeFade()
            assertEquals(0.4f, volume)
            processor.fadeInOnResume(300)
            runCurrent()
            assertTrue(processor.isResumeFading)
            advanceUntilIdle()
            assertEquals(0.4f, volume)
        } finally { unmockkStatic(SystemClock::class) }
    }
}
