package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide download queue (3 at a time). Tracks which song ids are waiting or running so a
 * playlist can show "18/42" and cancel only its own songs without touching other downloads.
 */
@Singleton
class DownloadQueue @Inject constructor(
    private val songDownloadManager: SongDownloadManager,
    private val notifications: DownloadNotificationManager
) {
    private val maxConcurrentDownloads = 3
    private val maxPendingDownloads = 1_000
    data class Admission(val accepted: Int, val deferred: Int)
    private var generation = 0L

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueChannel = Channel<Song>(1_000)

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _activeDownloads = MutableStateFlow(0)
    val activeDownloads: StateFlow<Int> = _activeDownloads.asStateFlow()

    /** Song ids waiting in the queue or downloading right now. */
    private val _pendingIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingIds: StateFlow<Set<String>> = _pendingIds.asStateFlow()

    /** Song ids whose download failed in this app session (cleared when re-queued). */
    private val _failedIds = MutableStateFlow<Set<String>>(emptySet())
    val failedIds: StateFlow<Set<String>> = _failedIds.asStateFlow()

    private val cancelledIds = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** Label + totals for the single batch notification ("Downloading Liked music · 18/42"). */
    private var batchLabel: String? = null
    private var batchTotal = 0
    private var batchDone = 0
    private val batchNotificationId = 9_999

    private var isStarted = false

    @Synchronized
    private fun startWorkersIfNeeded() {
        if (isStarted) return
        isStarted = true

        val workerGeneration = generation
        val channel = queueChannel
        repeat(maxConcurrentDownloads) { workerId ->
            scope.launch {
                for (song in channel) {
                    val shouldDownload = synchronized(this@DownloadQueue) {
                        if (workerGeneration != generation) return@launch
                        _queueSize.update { (it - 1).coerceAtLeast(0) }
                        if (cancelledIds.remove(song.id)) {
                            _pendingIds.update { it - song.id }
                            onBatchItemFinished()
                            false
                        } else {
                            _activeDownloads.update { it + 1 }
                            true
                        }
                    }
                    if (!shouldDownload) continue
                    var failed = false
                    try {
                        failed = songDownloadManager.downloadSong(song).isFailure
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed = true
                        Timber.e(e, "Worker $workerId failed downloading song: ${song.title}")
                    } finally {
                        synchronized(this@DownloadQueue) {
                            // Old cancelled workers must not modify a newly started batch.
                            if (workerGeneration == generation) {
                                if (failed) _failedIds.update { (it + song.id).toList().takeLast(1_000).toSet() }
                                _activeDownloads.update { (it - 1).coerceAtLeast(0) }
                                _pendingIds.update { it - song.id }
                                onBatchItemFinished()
                            }
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    fun enqueue(song: Song): Admission = enqueueAll(listOf(song))

    /** Queues [songs] (skipping ones already queued). [label] names the batch in the notification. */
    @Synchronized
    fun enqueueAll(songs: List<Song>, label: String? = null): Admission {
        val pending = _pendingIds.value.toMutableSet()
        val accepted = ArrayList<Song>(minOf(songs.size, maxPendingDownloads))
        val seen = HashSet<String>()
        var deferred = 0
        for (song in songs) {
            if (!seen.add(song.id) || song.id in pending) continue
            if (pending.size >= maxPendingDownloads) { deferred++; continue }
            pending.add(song.id)
            accepted.add(song)
        }
        if (accepted.isEmpty()) return Admission(0, deferred)
        startWorkersIfNeeded()
        // Publish once before workers can enter their synchronized state transition.
        _pendingIds.value = pending
        _failedIds.update { it - accepted.map { song -> song.id }.toSet() }
        _queueSize.update { it + accepted.size }
        if (label != null || batchLabel == null) batchLabel = label ?: batchLabel
        batchTotal += accepted.size
        for (song in accepted) {
            cancelledIds.remove(song.id)
            check(queueChannel.trySend(song).isSuccess)
        }
        updateBatchNotification()
        return Admission(accepted.size, deferred)
    }

    /** Cancels queued songs among [songIds]. A song that is already downloading finishes. */
    @Synchronized
    fun cancel(songIds: Collection<String>) {
        val pending = _pendingIds.value
        songIds.filter { it in pending }.forEach { cancelledIds.add(it) }
    }

    @Synchronized
    fun cancelAll() {
        generation++
        queueChannel.cancel()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        queueChannel = Channel(1_000)
        isStarted = false
        cancelledIds.clear()
        _queueSize.value = 0
        _activeDownloads.value = 0
        _pendingIds.value = emptySet()
        resetBatch()
    }

    @Synchronized
    private fun onBatchItemFinished() {
        if (batchTotal == 0) return
        batchDone++
        if (_pendingIds.value.isEmpty()) resetBatch() else updateBatchNotification()
    }

    private fun updateBatchNotification() {
        if (batchTotal <= 1) return
        val title = batchLabel?.let { "$it · $batchDone/$batchTotal" } ?: "$batchDone/$batchTotal songs"
        runCatching {
            notifications.showProgress(title, (batchDone * 100 / batchTotal.coerceAtLeast(1)), batchNotificationId)
        }
    }

    private fun resetBatch() {
        if (batchTotal > 1) runCatching { notifications.cancel(batchNotificationId) }
        batchLabel = null
        batchTotal = 0
        batchDone = 0
    }
}
