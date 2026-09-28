package com.theveloper.pixelplay.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamClientHealthTest {
    private var now = 1_000_000L
    private val health = StreamClientHealth { now }

    @Test fun `equally healthy clients keep the preferred order`() {
        assertEquals(listOf("a", "b"), health.order(listOf("a", "b")))
        health.recordSuccess("b", 100)
        assertEquals(listOf("a", "b"), health.order(listOf("a", "b")))
    }

    @Test fun `a client failing twice in a row drops behind a working one`() {
        health.recordFailure("a", StreamClientHealth.Failure.ERROR)
        assertEquals(listOf("a", "b"), health.order(listOf("a", "b")), "one failure keeps the order")
        health.recordFailure("a", StreamClientHealth.Failure.ERROR)
        assertEquals(listOf("b", "a"), health.order(listOf("a", "b")))
        health.recordSuccess("a", 100)
        assertEquals(listOf("a", "b"), health.order(listOf("a", "b")))
    }

    @Test fun `three failures in a row rest a client and a success restores it`() {
        repeat(StreamClientHealth.COOLDOWN_AFTER_FAILURES) { health.recordFailure("a", StreamClientHealth.Failure.STALL) }
        assertTrue(health.isCoolingDown("a"))
        // Tried last while resting, even behind a client that is struggling too.
        repeat(2) { health.recordFailure("b", StreamClientHealth.Failure.ERROR) }
        assertEquals(listOf("b", "a"), health.order(listOf("a", "b")))

        now += StreamClientHealth.MIN_COOLDOWN_MS + 1
        assertFalse(health.isCoolingDown("a"))
        health.recordSuccess("a", 200)
        assertFalse(health.isCoolingDown("a"))
    }

    @Test fun `cooldowns double while failures continue`() {
        repeat(3) { health.recordFailure("a", StreamClientHealth.Failure.ERROR) }
        now += StreamClientHealth.MIN_COOLDOWN_MS + 1
        repeat(3) { health.recordFailure("a", StreamClientHealth.Failure.ERROR) }
        now += StreamClientHealth.MIN_COOLDOWN_MS + 1
        assertTrue(health.isCoolingDown("a"), "second cooldown should last twice as long")
    }

    @Test fun `hedge delays follow the client's own latency once known`() {
        assertEquals(StreamClientHealth.DEFAULT_SECONDARY_DELAY_MS, health.secondaryDelayMs("a"))
        assertEquals(StreamClientHealth.DEFAULT_FALLBACK_DELAY_MS, health.fallbackDelayMs("a"))
        assertEquals(StreamClientHealth.DEFAULT_DIRECT_TIMEOUT_MS, health.directTimeoutMs("a"))
        listOf(300L, 320L, 340L, 360L, 380L, 400L, 420L, 440L, 460L, 900L).forEach { health.recordSuccess("a", it) }

        assertEquals(510L, health.secondaryDelayMs("a")) // p90 (460) + 50
        assertEquals(690L, health.fallbackDelayMs("a")) // p95 (460) × 1.5
        assertEquals(StreamClientHealth.MIN_DIRECT_TIMEOUT_MS, health.directTimeoutMs("a"))
    }

    @Test fun `hedge delays stay within bounds`() {
        repeat(10) { health.recordSuccess("fast", 10) }
        repeat(10) { health.recordSuccess("slow", 5_000) }
        assertEquals(StreamClientHealth.MIN_SECONDARY_DELAY_MS, health.secondaryDelayMs("fast"))
        assertEquals(StreamClientHealth.MIN_FALLBACK_DELAY_MS, health.fallbackDelayMs("fast"))
        assertEquals(StreamClientHealth.MAX_SECONDARY_DELAY_MS, health.secondaryDelayMs("slow"))
        assertEquals(StreamClientHealth.MAX_FALLBACK_DELAY_MS, health.fallbackDelayMs("slow"))
        assertEquals(StreamClientHealth.MAX_DIRECT_TIMEOUT_MS, health.directTimeoutMs("slow"))
    }

    @Test fun `per-video exclusions expire`() {
        health.excludeForVideo("abcdefghijk", "a")
        assertEquals(setOf("a"), health.excludedFor("abcdefghijk"))
        assertTrue(health.excludedFor("bcdefghijkl").isEmpty())
        now += StreamClientHealth.VIDEO_EXCLUSION_MS
        assertTrue(health.excludedFor("abcdefghijk").isEmpty())
    }

    @Test fun `a network change clears what was learned on the old one`() {
        repeat(3) { health.recordFailure("a", StreamClientHealth.Failure.ERROR) }
        repeat(10) { health.recordSuccess("b", 700) }
        health.excludeForVideo("abcdefghijk", "b")

        health.onNetworkChanged()

        assertFalse(health.isCoolingDown("a"))
        assertTrue(health.excludedFor("abcdefghijk").isEmpty())
        assertNull(health.latencyPercentile("b", 50))
    }
}
