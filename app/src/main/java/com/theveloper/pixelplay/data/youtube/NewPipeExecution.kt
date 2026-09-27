package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import java.io.InterruptedIOException
import kotlin.coroutines.EmptyCoroutineContext

/** Bridge the blocking extractor to coroutine cancellation, including the HTTP calls it creates. */
internal object NewPipeExecution {
    private val workers = Dispatchers.IO.limitedParallelism(4)
    val current = ThreadLocal<RequestGroup>()

    class RequestGroup {
        private var cancelled = false
        private val calls = mutableSetOf<Call>()
        @Synchronized fun register(call: Call) {
            if (cancelled) { call.cancel(); throw InterruptedIOException("Extraction cancelled") }
            calls.add(call)
        }
        @Synchronized fun remove(call: Call) { calls.remove(call) }
        @Synchronized fun cancel() { cancelled = true; calls.forEach { it.cancel() } }
    }

    suspend fun <T> run(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        val group = RequestGroup()
        continuation.invokeOnCancellation { group.cancel() }
        workers.dispatch(EmptyCoroutineContext, Runnable {
            if (!continuation.isActive) return@Runnable
            current.set(group)
            try { continuation.resumeWith(runCatching(block)) }
            finally { current.remove() }
        })
    }
}
