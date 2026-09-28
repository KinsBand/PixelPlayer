package com.theveloper.pixelplay.data.service.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NextStreamPrewarmerTest {
    @Test fun `cold startup waits for readiness and rapid queue changes prepare only final next item`() = runTest {
        val calls = mutableListOf<String>()
        val warmer = NextStreamPrewarmer<String>(backgroundScope, { calls.add(it) })
        warmer.update("a", "b", "b", false)
        advanceTimeBy(500)
        assertTrue(calls.isEmpty())
        warmer.update("a", "b", "b", true)
        advanceTimeBy(100)
        warmer.update("a", "c", "c", true)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("c"), calls)
        warmer.update("a", "c", "c", true)
        advanceTimeBy(500)
        assertEquals(listOf("c"), calls)
    }

    @Test fun `promoting next song preserves its running lookup but unrelated skip cancels it`() = runTest {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val warmer = NextStreamPrewarmer<String>(backgroundScope, {
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        })
        warmer.update("a", "b", "b", true)
        advanceTimeBy(200)
        runCurrent()
        assertTrue(started.isCompleted)
        warmer.update("b", "c", "c", false)
        runCurrent()
        assertFalse(cancelled)
        warmer.update("d", "e", "e", false)
        runCurrent()
        assertTrue(cancelled)
    }

    @Test fun `pause cancels speculative work and resume may warm again`() = runTest {
        val calls = mutableListOf<String>()
        var cancelled = 0
        val warmer = NextStreamPrewarmer<String>(backgroundScope, {
            calls.add(it)
            try { awaitCancellation() } finally { cancelled++ }
        })
        warmer.update("a", "b", "b", true)
        advanceTimeBy(200)
        runCurrent()
        warmer.update("a", "b", "b", false)
        runCurrent()
        assertEquals(1, cancelled)
        warmer.update("a", "b", "b", true)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("b", "b"), calls)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, cancelled)
    }

    @Test fun `promoted song becoming ready allows its successor to prepare`() = runTest {
        val calls = mutableListOf<String>()
        val warmer = NextStreamPrewarmer<String>(backgroundScope, {
            calls.add(it)
            awaitCancellation()
        })
        warmer.update("a", "b", "b", true)
        advanceTimeBy(200)
        runCurrent()
        warmer.update("b", "c", "c", false)
        warmer.update("b", "c", "c", true)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("b", "c"), calls)
    }

    @Test fun `failed preparation can retry on a later playback event`() = runTest {
        var attempts = 0
        var failures = 0
        val warmer = NextStreamPrewarmer<String>(backgroundScope, {
            attempts++
            throw IllegalStateException("offline")
        }, { failures++ })
        repeat(2) {
            warmer.update("a", "b", "b", true)
            advanceTimeBy(200)
            runCurrent()
        }
        assertEquals(2, attempts)
        assertEquals(2, failures)
    }

    @Test fun `songs after the next one get the lighter preparation in queue order`() = runTest {
        val calls = mutableListOf<String>()
        val warmer = NextStreamPrewarmer<String>(backgroundScope, { calls.add("next:$it") },
            prepareLater = { calls.add("later:$it") })
        warmer.update("a", "b", "b", true, later = listOf("c", "d"))
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("next:b", "later:c", "later:d"), calls)
    }

    @Test fun `lookahead waits for the next song and is skipped when it fails`() = runTest {
        val calls = mutableListOf<String>()
        val nextDone = CompletableDeferred<Unit>()
        val warmer = NextStreamPrewarmer<String>(backgroundScope, { nextDone.await() },
            prepareLater = { calls.add(it) })
        warmer.update("a", "b", "b", true, later = listOf("c"))
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(calls.isEmpty())
        nextDone.complete(Unit)
        runCurrent()
        assertEquals(listOf("c"), calls)

        val failing = NextStreamPrewarmer<String>(backgroundScope, { throw IllegalStateException("offline") },
            prepareLater = { calls.add("unexpected:$it") })
        failing.update("a", "b", "b", true, later = listOf("c"))
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("c"), calls)
    }

    @Test fun `a skip elsewhere cancels the lookahead but promoting the next song keeps it`() = runTest {
        var laterCancelled = false
        val laterStarted = CompletableDeferred<Unit>()
        val warmer = NextStreamPrewarmer<String>(backgroundScope, { }, prepareLater = {
            laterStarted.complete(Unit)
            try { awaitCancellation() } finally { laterCancelled = true }
        })
        warmer.update("a", "b", "b", true, later = listOf("c"))
        advanceTimeBy(200)
        runCurrent()
        assertTrue(laterStarted.isCompleted)
        warmer.update("b", "c", "c", false) // skipped to the prepared song; it is buffering
        runCurrent()
        assertFalse(laterCancelled)
        warmer.update("x", "y", "y", false) // jumped somewhere else
        runCurrent()
        assertTrue(laterCancelled)
    }
}
