package com.theveloper.pixelplay.data.service.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RebufferBackoffTest {
    private val unset = Long.MIN_VALUE + 1 // C.TIME_UNSET

    @Test fun `first rebuffer keeps the player's own threshold`() {
        val backoff = RebufferBackoff()
        assertEquals(0L, backoff.requiredBufferMs(10_000))
        // Asked again and again during the same rebuffer: still the first one.
        assertEquals(0L, backoff.requiredBufferMs(10_000))
    }

    @Test fun `rebuffers in quick succession wait for more audio each time`() {
        val backoff = RebufferBackoff()
        assertEquals(0L, backoff.requiredBufferMs(10_000))
        assertEquals(3_000L, backoff.requiredBufferMs(18_000))
        assertEquals(6_000L, backoff.requiredBufferMs(30_000))
        assertEquals(10_000L, backoff.requiredBufferMs(45_000))
        assertEquals(10_000L, backoff.requiredBufferMs(70_000))
    }

    @Test fun `smooth playback resets the backoff`() {
        val backoff = RebufferBackoff()
        backoff.requiredBufferMs(10_000)
        assertEquals(3_000L, backoff.requiredBufferMs(20_000))
        assertEquals(0L, backoff.requiredBufferMs(20_000 + 61_000))
        assertEquals(3_000L, backoff.requiredBufferMs(90_000))
    }

    @Test fun `unknown rebuffer start leaves the default threshold and keeps history`() {
        val backoff = RebufferBackoff()
        backoff.requiredBufferMs(10_000)
        assertEquals(0L, backoff.requiredBufferMs(unset))
        assertEquals(3_000L, backoff.requiredBufferMs(15_000))
    }
}
