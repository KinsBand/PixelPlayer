package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HedgedRaceTest {
    private val started = Collections.synchronizedList(mutableListOf<String>())
    private val outcomes = Collections.synchronizedList(mutableListOf<Pair<String, HedgedOutcome>>())

    private fun attempt(name: String, startAfter: Long, timeout: Long = 10_000, block: suspend () -> String?) =
        HedgedAttempt(name, startAfter, timeout) { started += name; block() }

    @Test fun `a fast primary wins before any hedge starts`() = runTest {
        val result = raceHedged(listOf(
            attempt("a", 0) { delay(100); "A" },
            attempt("b", 250) { "B" },
            attempt("c", 600) { "C" },
        )) { name, outcome -> outcomes += name to outcome }

        assertEquals("a" to "A", result)
        assertEquals(listOf("a"), started)
        assertEquals(100L, currentTime)
    }

    @Test fun `a slow primary is hedged and the first answer wins`() = runTest {
        var cancelled = false
        val result = raceHedged(listOf(
            attempt("a", 0) { try { delay(1_000); "A" } catch (e: CancellationException) { cancelled = true; throw e } },
            attempt("b", 250) { delay(100); "B" },
            attempt("c", 600) { "C" },
        )) { name, outcome -> outcomes += name to outcome }

        assertEquals("b" to "B", result)
        assertEquals(listOf("a", "b"), started)
        assertEquals(350L, currentTime)
        assertTrue(cancelled, "the loser is cancelled")
        // Losers cancelled after another won are not reported as failures.
        assertEquals(listOf("b"), outcomes.map { it.first })
    }

    @Test fun `failures start the next attempt without waiting for its delay`() = runTest {
        val result = raceHedged(listOf(
            attempt("a", 0) { delay(50); null },
            attempt("b", 250) { delay(20); throw java.io.IOException("boom") },
            attempt("c", 600) { delay(10); "C" },
        )) { name, outcome -> outcomes += name to outcome }

        assertEquals("c" to "C", result)
        assertEquals(80L, currentTime)
        assertTrue(outcomes[0].second is HedgedOutcome.Empty)
        assertTrue(outcomes[1].second is HedgedOutcome.Failed)
        assertTrue(outcomes[2].second is HedgedOutcome.Success)
    }

    @Test fun `timeouts are reported and nothing usable returns null`() = runTest {
        val result = raceHedged(listOf(
            attempt("a", 0, timeout = 300) { awaitCancellation() },
            attempt("b", 100) { null },
        )) { name, outcome -> outcomes += name to outcome }

        assertNull(result)
        assertEquals(setOf("a", "b"), outcomes.map { it.first }.toSet())
        assertTrue(outcomes.single { it.first == "a" }.second is HedgedOutcome.TimedOut)
        assertTrue(outcomes.single { it.first == "b" }.second is HedgedOutcome.Empty)
    }

    @Test fun `a provider cancelling itself is a failure of that attempt only`() = runTest {
        val result = raceHedged(listOf(
            attempt("a", 0) { throw CancellationException("provider gave up") },
            attempt("b", 250) { "B" },
        ))
        assertEquals("b" to "B", result)
        assertEquals(0L, currentTime)
    }

    @Test fun `an empty race returns null`() = runTest {
        assertNull(raceHedged(emptyList<HedgedAttempt<String>>()))
    }
}
