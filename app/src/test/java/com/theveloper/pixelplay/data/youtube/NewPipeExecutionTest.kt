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
}
