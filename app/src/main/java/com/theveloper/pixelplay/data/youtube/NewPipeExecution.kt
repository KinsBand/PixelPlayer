package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import java.io.InterruptedIOException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Bridge the blocking extractor to coroutine cancellation, including the HTTP calls it creates. */
internal object NewPipeExecution {
    /**
     * Blocking extractor work is split into two pools so optional work (recommendations,
     * collection searches, speculative prewarms) can never occupy every thread while the
     * user is waiting for a song they tapped.
     */
    enum class Lane { PLAYBACK, BACKGROUND }

    /** Add to a coroutine context to run the extractor calls it makes in [lane]. */
    class LaneElement(val lane: Lane) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<LaneElement>
    }

    val Background = LaneElement(Lane.BACKGROUND)

    private val playbackWorkers = Dispatchers.IO.limitedParallelism(3)
    private val backgroundWorkers = Dispatchers.IO.limitedParallelism(3)
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

    /** Runs [block] in [lane], or in the caller's [LaneElement] lane, or else the playback lane. */
    suspend fun <T> run(lane: Lane? = null, block: () -> T): T {
        val effectiveLane = lane ?: currentCoroutineContext()[LaneElement]?.lane ?: Lane.PLAYBACK
        val workers = if (effectiveLane == Lane.BACKGROUND) backgroundWorkers else playbackWorkers
        return suspendCancellableCoroutine { continuation ->
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
}
