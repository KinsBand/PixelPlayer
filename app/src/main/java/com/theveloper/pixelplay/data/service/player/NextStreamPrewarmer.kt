package com.theveloper.pixelplay.data.service.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Main-thread owned, with at most one speculative manifest lookup at a time. */
internal class NextStreamPrewarmer<T>(
    private val scope: CoroutineScope,
    private val prepare: suspend (T) -> Unit,
    private val onFailure: (Exception) -> Unit = {}
) {
    private var job: Job? = null
    private var preparedId: String? = null

    fun update(currentId: String?, nextId: String?, next: T?, readyToPlay: Boolean) {
        // A skip may promote the speculative lookup to foreground work. Let it finish:
        // the foreground extractor waits on the same per-video mutex and reuses its result.
        if (job?.isActive == true && preparedId == currentId && !readyToPlay) return
        if (preparedId != nextId || !readyToPlay) {
            job?.cancel()
            job = null
            preparedId = null
        }
        if (!readyToPlay || nextId == null || next == null || preparedId == nextId) return
        preparedId = nextId
        job = scope.launch {
            delay(200) // Coalesce rapid queue changes before starting network work.
            try {
                if (withTimeoutOrNull(10_000) { prepare(next) } == null) {
                    preparedId = null // A later playback event may retry a timed-out lookup.
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                preparedId = null
                onFailure(failure)
            }
        }
    }
}
