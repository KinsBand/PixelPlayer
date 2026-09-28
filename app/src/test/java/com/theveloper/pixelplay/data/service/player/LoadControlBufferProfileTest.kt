package com.theveloper.pixelplay.data.service.player

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LoadControlBufferProfileTest {

    @Test
    fun normalDevice_usesFullPrefetchProfile() {
        val profile = loadControlBufferProfileFor(isLowRamDevice = false)

        assertThat(profile.minBufferMs).isEqualTo(30_000)
        assertThat(profile.maxBufferMs).isEqualTo(60_000)
        assertThat(profile.bufferForPlaybackMs).isEqualTo(250)
        assertThat(profile.bufferForPlaybackAfterRebufferMs).isEqualTo(1_000)
    }

    @Test
    fun lowRamDevice_cutsPrefetchWindow() {
        val normal = loadControlBufferProfileFor(isLowRamDevice = false)
        val lowRam = loadControlBufferProfileFor(isLowRamDevice = true)

        assertThat(lowRam.maxBufferMs).isLessThan(normal.maxBufferMs)
        assertThat(lowRam.minBufferMs).isLessThan(normal.minBufferMs)
    }

    @Test
    fun lowRamDevice_keepsStartLatencyIdenticalToNormal() {
        // The whole point: capping RAM must not regress the cross-format start normalization.
        val normal = loadControlBufferProfileFor(isLowRamDevice = false)
        val lowRam = loadControlBufferProfileFor(isLowRamDevice = true)

        assertThat(lowRam.bufferForPlaybackMs).isEqualTo(normal.bufferForPlaybackMs)
        assertThat(lowRam.bufferForPlaybackAfterRebufferMs)
            .isEqualTo(normal.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun bothProfiles_satisfyDefaultLoadControlConstraints() {
        for (isLowRam in listOf(false, true)) {
            val profile = loadControlBufferProfileFor(isLowRam)

            assertThat(profile.targetBufferBytes * 2).isAtMost(48 * 1024 * 1024)
            assertThat(profile.targetBufferBytes).isAtLeast(12 * 1024 * 1024)
            // DefaultLoadControl.Builder.build() asserts these; violating them crashes at runtime.
            assertThat(profile.minBufferMs).isAtLeast(profile.bufferForPlaybackMs)
            assertThat(profile.minBufferMs).isAtLeast(profile.bufferForPlaybackAfterRebufferMs)
            assertThat(profile.maxBufferMs).isAtLeast(profile.minBufferMs)
        }
    }

    @Test
    fun bothProfiles_keepTheResumeRewindInMemory() {
        // A long pause resumes 7 s earlier. That audio must still be buffered, or the rewind
        // throws away the read-ahead and a stream waits on the network before playing again.
        for (isLowRam in listOf(false, true)) {
            val profile = loadControlBufferProfileFor(isLowRam, heapLimitBytes = 256L * 1024 * 1024)

            assertThat(profile.backBufferMs.toLong()).isAtLeast(SmartResumePolicy.REWIND_MS + 1_000)
            // Kept small: it is allocated from the same byte budget as the read-ahead.
            assertThat(profile.backBufferMs).isAtMost(profile.minBufferMs)
        }
    }
}
