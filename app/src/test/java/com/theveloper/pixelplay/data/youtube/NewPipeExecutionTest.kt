package com.theveloper.pixelplay.data.youtube

import io.mockk.*
import kotlinx.coroutines.*
import okhttp3.Call
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NewPipeExecutionTest {
    @Test fun `cancelling a search cancels its HTTP call and rejects subsequent calls`() = runBlocking {
        val registered = CompletableDeferred<NewPipeExecution.RequestGroup>()
        val release = CountDownLatch(1)
        val call = mockk<Call>(relaxed = true)
        val job = launch {
            NewPipeExecution.run {
                val group = NewPipeExecution.current.get()!!
                group.register(call)
                registered.complete(group)
                release.await(5, TimeUnit.SECONDS)
                group.remove(call)
            }
        }
        try {
            val group = withTimeout(2_000) { registered.await() }
            withTimeout(2_000) { job.cancelAndJoin() }
            verify(exactly = 1) { call.cancel() }
            assertThrows(java.io.InterruptedIOException::class.java) { group.register(mockk(relaxed = true)) }
        } finally { release.countDown(); job.cancel() }
        Unit
    }

    @Test fun `saturated background lane does not delay playback extraction`() = runBlocking {
        val release = CountDownLatch(1)
        val started = CountDownLatch(3)
        val blockers = List(3) {
            launch(Dispatchers.IO + NewPipeExecution.Background) {
                NewPipeExecution.run { started.countDown(); release.await(5, TimeUnit.SECONDS) }
            }
        }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val result = withTimeout(1_000) { NewPipeExecution.run { "playback" } }
            assertEquals("playback", result)
            // An explicit lane wins over the caller's context.
            val explicit = withTimeout(1_000) {
                withContext(NewPipeExecution.Background) { NewPipeExecution.run(NewPipeExecution.Lane.PLAYBACK) { 1 } }
            }
            assertEquals(1, explicit)
        } finally {
            release.countDown()
            blockers.forEach { it.cancel() }
        }
        Unit
    }
}
