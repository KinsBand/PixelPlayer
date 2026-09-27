package com.theveloper.pixelplay.data.stream

import com.theveloper.pixelplay.data.youtube.YouTubeRateLimit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections

class StreamPrewarmSchedulerTest {
    private val calls = Collections.synchronizedList(mutableListOf<Pair<String, Int>>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var metered = false
    private var now = 1_000_000L
    private var gate: CompletableDeferred<Unit>? = null

    private val scheduler = StreamPrewarmScheduler(
        prewarm = { id, headBytes -> calls += id to headBytes; gate?.await() },
        isMetered = { metered },
        clock = { now },
        scope = scope,
    )

    private fun ids(count: Int, prefix: Char = 'a') = List(count) { index -> "$prefix${"%010d".format(index)}" }

    private fun awaitIdle() = runBlocking {
        withTimeout(2_000) { while (scheduler.pendingCount() > 0) delay(5) }
    }

    @BeforeEach fun reset() = YouTubeRateLimit.resetForTest()
    @AfterEach fun tearDown() { scope.cancel(); YouTubeRateLimit.resetForTest() }

    @Test fun `search prewarms only the top results with head bytes on unmetered networks`() {
        scheduler.onSearchResults(ids(5))
        awaitIdle()
        assertEquals(ids(2).map { it to StreamPrewarmScheduler.SEARCH_HEAD_BYTES }, calls.sortedBy { it.first })
    }

    @Test fun `metered networks get the manifest only`() {
        metered = true
        scheduler.onPress("dQw4w9WgXcQ")
        awaitIdle()
        assertEquals(listOf("dQw4w9WgXcQ" to 0), calls)
    }

    @Test fun `nothing is speculated while youtube is rate limiting`() {
        YouTubeRateLimit.report(now)
        scheduler.onSearchResults(ids(2))
        scheduler.onPress("dQw4w9WgXcQ")
        awaitIdle()
        assertTrue(calls.isEmpty())
    }

    @Test fun `search speculation is capped per minute but presses are not`() {
        repeat(15) { batch -> scheduler.onSearchResults(ids(2, 'a' + batch)); awaitIdle() }
        assertEquals(StreamPrewarmScheduler.MAX_PER_MINUTE, calls.size)
        scheduler.onPress("dQw4w9WgXcQ")
        awaitIdle()
        assertEquals(StreamPrewarmScheduler.MAX_PER_MINUTE + 1, calls.size)
        now += 61_000
        scheduler.onSearchResults(ids(1, 'z'))
        awaitIdle()
        assertEquals(StreamPrewarmScheduler.MAX_PER_MINUTE + 2, calls.size)
    }

    @Test fun `recently warmed songs are not warmed again`() {
        scheduler.onPress("dQw4w9WgXcQ")
        awaitIdle()
        scheduler.onPress("dQw4w9WgXcQ")
        awaitIdle()
        assertEquals(1, calls.size)
    }

    @Test fun `a new search drops results still waiting for a slot`() = runBlocking {
        gate = CompletableDeferred()
        scheduler.onPress("pppppppppp1")
        scheduler.onPress("pppppppppp2")
        withTimeout(2_000) { while (calls.size < 2) delay(5) }
        scheduler.onSearchResults(ids(2, 'a'))
        scheduler.onSearchResults(ids(2, 'b'))
        gate!!.complete(Unit)
        awaitIdle()
        assertTrue(calls.none { it.first.startsWith("a") }, calls.toString())
        assertEquals(4, calls.size)
    }
}
