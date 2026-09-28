package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HedgedStreamResolverTest {
    @Test fun `fast cold lookup completes without starting full extraction`() = runTest {
        var fallbackCalls = 0
        val result = resolveWithHedgedFallback(
            direct = { delay(100); "direct" },
            fallback = { fallbackCalls++; "fallback" }
        )
        assertEquals("direct", result)
        assertEquals(100, currentTime)
        assertEquals(0, fallbackCalls)
    }

    @Test fun `slow direct request no longer adds its timeout before fallback`() = runTest {
        var directCancelled = false
        val result = resolveWithHedgedFallback(
            direct = { try { awaitCancellation() } finally { directCancelled = true } },
            fallback = { delay(100); "fallback" }
        )
        assertEquals("fallback", result)
        assertEquals(300, currentTime) // 200 ms hedge + 100 ms fallback, not 700 + 100.
        assertTrue(directCancelled)
    }

    @Test fun `direct result still wins while full extraction is in flight`() = runTest {
        // The usual cold lookup on a phone: direct takes longer than the hedge, so extraction
        // has started. Cancelling that loser must not throw away the winner's manifest.
        var fallbackCancelled = false
        val result = resolveWithHedgedFallback(
            direct = { delay(400); "direct" },
            fallback = { try { awaitCancellation() } finally { fallbackCancelled = true } }
        )
        assertEquals("direct", result)
        assertEquals(400, currentTime)
        assertTrue(fallbackCancelled)
    }

    @Test fun `empty direct result starts fallback without waiting for hedge`() = runTest {
        assertEquals("fallback", resolveWithHedgedFallback(
            direct = { delay(20); null }, fallback = { delay(50); "fallback" }
        ))
        assertEquals(70, currentTime)
    }

    @Test fun `failure in fallback still allows direct result and cancels neither prematurely`() = runTest {
        val result = resolveWithHedgedFallback(
            direct = { delay(400); "direct" }, fallback = { throw java.io.IOException("offline") }
        )
        assertEquals("direct", result)
        assertEquals(400, currentTime)
    }

    @Test fun `direct timeout and empty fallback terminate without hanging`() = runTest {
        val result = resolveWithHedgedFallback<String>(direct = { awaitCancellation() }, fallback = { null })
        assertNull(result)
        assertEquals(1500, currentTime)
    }

    @Test fun `skip cancellation cancels both active providers`() = runTest {
        var stopped = 0
        val request = launch {
            resolveWithHedgedFallback<String>(
                direct = { try { awaitCancellation() } finally { stopped++ } },
                fallback = { try { awaitCancellation() } finally { stopped++ } }
            )
        }
        advanceTimeBy(201)
        runCurrent()
        request.cancelAndJoin()
        assertEquals(2, stopped)
    }

    @Test fun `direct exception falls back immediately`() = runTest {
        assertEquals("fallback", resolveWithHedgedFallback(
            direct = { throw java.io.IOException("unavailable") }, fallback = { "fallback" }
        ))
        assertEquals(0, currentTime)
    }

    @Test fun `provider cancellation terminates resolution instead of waiting for a missing result`() = runTest {
        val request = async {
            resolveWithHedgedFallback<String>(
                direct = { throw CancellationException("superseded") }, fallback = { null }
            )
        }
        assertTrue(runCatching { request.await() }.exceptionOrNull() is CancellationException)
    }
}
