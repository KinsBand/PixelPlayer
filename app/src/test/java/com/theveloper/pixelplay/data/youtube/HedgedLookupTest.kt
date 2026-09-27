package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HedgedLookupTest {
    @Test fun `fast primary avoids fallback traffic`() = runTest {
        var fallbackCalled = false
        assertEquals(listOf("primary"), hedgedLookup(primary = { listOf("primary") }, fallback = {
            fallbackCalled = true; listOf("fallback")
        }))
        assertFalse(fallbackCalled)
    }
    @Test fun `slow primary cannot hold a successful fallback hostage`() = runTest {
        var primaryCancelled = false
        val results = hedgedLookup(primary = {
            try { awaitCancellation() } finally { primaryCancelled = true }
        }, fallback = { listOf("fallback") })
        assertEquals(listOf("fallback"), results)
        assertEquals(250L, currentTime)
        assertTrue(primaryCancelled)
    }
    @Test fun `empty primary starts fallback immediately`() = runTest {
        assertEquals(listOf(42), hedgedLookup(primary = { emptyList() }, fallback = { listOf(42) }))
        assertEquals(0L, currentTime)
    }
    @Test fun `one failing provider does not discard another success`() = runTest {
        assertEquals(listOf(42), hedgedLookup(primary = { error("primary failed") }, fallback = { listOf(42) }))
    }
    @Test fun `caller cancellation cancels both providers`() = runTest {
        var stopped = 0
        val job = launch {
            hedgedLookup<Int>(primary = { try { awaitCancellation() } finally { stopped++ } },
                fallback = { try { awaitCancellation() } finally { stopped++ } })
        }
        advanceTimeBy(251); runCurrent()
        job.cancelAndJoin()
        assertEquals(2, stopped)
    }
}
