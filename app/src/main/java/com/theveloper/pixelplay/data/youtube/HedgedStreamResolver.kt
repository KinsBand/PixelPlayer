package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** First usable result wins; a slow fast path must not serialize fallback behind its timeout. */
internal suspend fun <T : Any> resolveWithHedgedFallback(
    hedgeDelayMs: Long = 200,
    directTimeoutMs: Long = 1_500,
    direct: suspend () -> T?,
    fallback: suspend () -> T?
): T? = coroutineScope {
    val directFinished = CompletableDeferred<Unit>()
    val results = Channel<T?>(2)
    suspend fun attempt(block: suspend () -> T?): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        // A provider may itself be cancelled; don't leave a receiver waiting for its result.
        // A loser cancelled below because the other branch won must not cancel the scope too:
        // that threw the winning manifest away and failed the play.
        if (currentCoroutineContext().isActive) this@coroutineScope.cancel(cancelled)
        throw cancelled
    } catch (_: Exception) {
        null
    }
    val fast = launch {
        val result = attempt { withTimeoutOrNull(directTimeoutMs) { direct() } }
        results.send(result)
        if (result == null) directFinished.complete(Unit)
    }
    val backup = launch {
        // Empty/error responses start fallback immediately. Otherwise give direct a head start.
        withTimeoutOrNull(hedgeDelayMs) { directFinished.await() }
        results.send(attempt(fallback))
    }
    try {
        repeat(2) { results.receive()?.let { return@coroutineScope it } }
        null
    } finally {
        fast.cancel()
        backup.cancel()
        results.cancel()
    }
}
