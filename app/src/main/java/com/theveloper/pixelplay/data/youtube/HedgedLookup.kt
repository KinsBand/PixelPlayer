package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Give the primary a head start, then accept the first useful response and cancel the loser. */
internal suspend fun <T> hedgedLookup(
    headStartMs: Long = 250,
    primary: suspend () -> List<T>,
    fallback: suspend () -> List<T>,
): List<T> = coroutineScope {
    val replies = Channel<Result<List<T>>>(2)
    val startFallback = CompletableDeferred<Unit>()
    suspend fun lookup(block: suspend () -> List<T>, isPrimary: Boolean) {
        val result = try { Result.success(block()) }
        catch (e: CancellationException) {
            if (currentCoroutineContext().isActive) this@coroutineScope.cancel(e)
            throw e
        }
        catch (e: Exception) { Result.failure(e) }
        if (isPrimary && result.getOrNull().isNullOrEmpty()) startFallback.complete(Unit)
        replies.send(result)
    }
    val first = launch { lookup(primary, true) }
    val second = launch {
        withTimeoutOrNull(headStartMs) { startFallback.await() }
        lookup(fallback, false)
    }
    try {
        var failure: Throwable? = null
        var successfulEmpty = false
        repeat(2) {
            val result = replies.receive()
            result.getOrNull()?.let { items ->
                if (items.isNotEmpty()) return@coroutineScope items
                successfulEmpty = true
            }
            result.exceptionOrNull()?.let { failure = it }
        }
        if (!successfulEmpty) failure?.let { throw it }
        emptyList()
    } finally { first.cancel(); second.cancel() }
}
