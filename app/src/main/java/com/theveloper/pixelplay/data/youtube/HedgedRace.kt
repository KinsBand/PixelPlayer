package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One way of getting a result, started [startAfterMs] into the race unless it starts earlier. */
internal class HedgedAttempt<T : Any>(
    val name: String,
    val startAfterMs: Long,
    val timeoutMs: Long,
    val block: suspend () -> T?,
)

/**
 * How an attempt ended, with how long it ran (from its own start, not the race's). Attempts
 * cancelled because another one won are not reported.
 */
internal sealed interface HedgedOutcome {
    val elapsedMs: Long
    data class Success(override val elapsedMs: Long) : HedgedOutcome
    /** Finished without a usable result. */
    data class Empty(override val elapsedMs: Long) : HedgedOutcome
    data class TimedOut(override val elapsedMs: Long) : HedgedOutcome
    data class Failed(override val elapsedMs: Long, val error: Throwable) : HedgedOutcome
}

/**
 * Runs [attempts] as a staggered race and returns the first non-null result with its attempt's
 * name; the others are cancelled. Attempt *i* starts at its [HedgedAttempt.startAfterMs], or at
 * once when every attempt started before it has already finished without a result, so a fast
 * failure never leaves the next attempt waiting for its hedge delay.
 *
 * Generalises [resolveWithHedgedFallback] to any number of attempts. [onOutcome] is called for
 * every attempt that finished (not for losers cancelled after another won).
 */
internal suspend fun <T : Any> raceHedged(
    attempts: List<HedgedAttempt<T>>,
    onOutcome: (name: String, outcome: HedgedOutcome) -> Unit = { _, _ -> },
): Pair<String, T>? {
    if (attempts.isEmpty()) return null
    return coroutineScope {
        val finishedEmpty = MutableStateFlow(0)
        val results = Channel<Pair<Int, T?>>(attempts.size)

        val jobs = attempts.mapIndexed { index, attempt ->
            launch {
                if (index > 0 && attempt.startAfterMs > 0) {
                    // Start early once all attempts before this one came back empty.
                    withTimeoutOrNull(attempt.startAfterMs) { finishedEmpty.first { it >= index } }
                }
                val attemptStarted = System.nanoTime()
                fun elapsedMs() = (System.nanoTime() - attemptStarted) / 1_000_000
                val holder: Holder<T>? = try {
                    withTimeoutOrNull(attempt.timeoutMs) { Holder(attempt.block()) }
                } catch (cancelled: CancellationException) {
                    // Cancelled because another attempt won (or the caller left): report nothing.
                    if (!currentCoroutineContext().isActive) throw cancelled
                    // A provider cancelled on its own: that is a failure of this attempt only.
                    onOutcome(attempt.name, HedgedOutcome.Failed(elapsedMs(), cancelled))
                    finishedEmpty.update { it + 1 }
                    results.send(index to null)
                    return@launch
                } catch (error: Exception) {
                    onOutcome(attempt.name, HedgedOutcome.Failed(elapsedMs(), error))
                    finishedEmpty.update { it + 1 }
                    results.send(index to null)
                    return@launch
                }
                val result = holder?.value
                when {
                    result != null -> onOutcome(attempt.name, HedgedOutcome.Success(elapsedMs()))
                    holder == null -> onOutcome(attempt.name, HedgedOutcome.TimedOut(elapsedMs()))
                    else -> onOutcome(attempt.name, HedgedOutcome.Empty(elapsedMs()))
                }
                if (result == null) finishedEmpty.update { it + 1 }
                results.send(index to result)
            }
        }
        try {
            repeat(attempts.size) {
                val (index, value) = results.receive()
                if (value != null) return@coroutineScope attempts[index].name to value
            }
            null
        } finally {
            jobs.forEach { it.cancel() }
            results.cancel()
        }
    }
}

/** Tells "finished with null" apart from withTimeoutOrNull's "timed out". */
private class Holder<T>(val value: T?)
