package com.theveloper.pixelplay.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KeyedMutexTest {
    @Test fun `unrelated colliding keys do not wait for network work`() = runTest {
        val locks = KeyedMutex<String>()
        val release = CompletableDeferred<Unit>()
        var otherFinished = false
        // Java String hashes collide, including in the previous 16-bucket implementation.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        launch { locks.withKey("Aa") { release.await() } }
        launch { locks.withKey("BB") { otherFinished = true } }
        runCurrent()
        assertTrue(otherFinished)
        release.complete(Unit)
    }

    @Test fun `same key serializes and cancellation does not strand the lock`() = runTest {
        val locks = KeyedMutex<String>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<Int>()
        launch { locks.withKey("song") { events += 1; release.await(); events += 2 } }
        val cancelled = launch { locks.withKey("song") { fail<Unit>("Cancelled waiter ran") } }
        launch { locks.withKey("song") { events += 3 } }
        runCurrent()
        assertEquals(listOf(1), events)
        cancelled.cancel()
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2, 3), events)
        locks.withKey("song") { events += 4 }
        assertEquals(listOf(1, 2, 3, 4), events)
    }
}
