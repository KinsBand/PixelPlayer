package com.theveloper.pixelplay.data.service.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Main-thread owned, with at most one speculative lookup at a time. The next song gets
 * [prepare]; after it, the songs queued behind it get the cheaper [prepareLater] one by one,
 * so a double skip also lands on a song whose manifest is ready.
 *
 * Nothing new starts while the playing song is still loading (`networkBusy`): on a slow
 * connection the next song's bytes would be taken from the song being listened to. Work that
 * already started is left to finish.
 *
 * Last, [prepareFully] may fetch the whole next song (e.g. into the song cache on Wi-Fi), so
 * its transition and playback no longer depend on the network at all. It gets a longer time
 * budget and is cancelled like the rest when the next song changes.
 */
internal class NextStreamPrewarmer<T>(
    private val scope: CoroutineScope,
    private val prepare: suspend (T) -> Unit,
    private val onFailure: (Exception) -> Unit = {},
    private val prepareLater: suspend (T) -> Unit = {},
    private val prepareFully: suspend (T) -> Unit = {},
    private val fullTimeoutMs: Long = FULL_PREPARE_TIMEOUT_MS,
) {
    private var job: Job? = null
    private var preparedId: String? = null

    fun update(
        currentId: String?,
        nextId: String?,
        next: T?,
        readyToPlay: Boolean,
        later: List<T> = emptyList(),
        networkBusy: Boolean = false
    ) {
        // A skip may promote the speculative lookup to foreground work. Let it finish:
        // the foreground extractor waits on the same per-video mutex and reuses its result.
        if (job?.isActive == true && preparedId == currentId && !readyToPlay) return
        if (preparedId != nextId || !readyToPlay) {
            job?.cancel()
            job = null
            preparedId = null
        }
        if (!readyToPlay || nextId == null || next == null || preparedId == nextId) return
        if (networkBusy) return // Asked again once the playing song has loaded.
        preparedId = nextId
        job = scope.launch {
            delay(200) // Coalesce rapid queue changes before starting network work.
            if (!attempt { prepare(next) }) {
                preparedId = null // A later playback event may retry a failed or timed-out lookup.
                return@launch
            }
            // One at a time and only after the next song: the lookahead never competes with it.
            for (item in later) attempt { prepareLater(item) }
            attempt(fullTimeoutMs) { prepareFully(next) }
        }
    }

    private suspend fun attempt(timeoutMs: Long = PREPARE_TIMEOUT_MS, block: suspend () -> Unit): Boolean = try {
        withTimeoutOrNull(timeoutMs) { block() } != null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onFailure(failure)
        false
    }

    private companion object {
        const val PREPARE_TIMEOUT_MS = 10_000L
        const val FULL_PREPARE_TIMEOUT_MS = 120_000L
    }
}
