package com.theveloper.pixelplay.data.stream

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AwaitResponseTest {
    @Test fun `cancelled request cancels socket and closes a late body`() = runTest {
        val callback = slot<Callback>()
        val call = mockk<Call>(relaxed = true)
        every { call.enqueue(capture(callback)) } just Runs
        val job = launch { call.awaitResponse().close() }
        runCurrent()
        job.cancelAndJoin()
        verify(exactly = 1) { call.cancel() }
        val response = mockk<Response>(relaxed = true)
        callback.captured.onResponse(call, response)
        verify(exactly = 1) { response.close() }
    }

    @Test fun `transport failure reaches the caller`() = runTest {
        val call = mockk<Call>()
        every { call.enqueue(any()) } answers { firstArg<Callback>().onFailure(call, IOException("dropped")) }
        assertThrows(IOException::class.java) { runBlocking { call.awaitResponse() } }
    }
}
