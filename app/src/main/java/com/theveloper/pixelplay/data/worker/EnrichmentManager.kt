package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Progress snapshot of the background enrichment job, mirroring [SyncProgress]. */
data class EnrichmentProgress(
    val isRunning: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val currentTitle: String = "",
    val isCompleted: Boolean = false
) {
    val fraction: Float
        get() = if (total > 0) processed.toFloat() / total else 0f
}

/**
 * Entry point for scheduling metadata-enrichment work ([EnrichmentWorker]).
 * Mirrors [SyncManager]: non-suspend public triggers launch on an internal
 * scope and REPLACE the running job; the automatic after-sync trigger is a
 * suspending KEEP enqueue so a user-started job is never clobbered.
 */
@Singleton
class EnrichmentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val workManager = WorkManager.getInstance(context)
    private val sharingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Raw WorkInfo stream for the unique enrichment work. */
    val workInfos: Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(EnrichmentWorker.WORK_NAME)

    /** True while a job is running or freshly enqueued (same logic as SyncManager.isSyncing). */
    val isRunning: Flow<Boolean> =
        workInfos
            .map { infos ->
                infos.any { it.state == WorkInfo.State.RUNNING } ||
                    infos.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 0 }
            }
            .distinctUntilChanged()
            .shareIn(sharingScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Detailed progress for UI surfaces (counts + current song title). */
    val progress: Flow<EnrichmentProgress> =
        workInfos
            .map { infos ->
                val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                val succeeded = infos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                val freshEnqueue = infos.firstOrNull {
                    it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 0
                }
                when {
                    running != null -> EnrichmentProgress(
                        isRunning = true,
                        processed = running.progress.getInt(EnrichmentWorker.PROGRESS_PROCESSED, 0),
                        total = running.progress.getInt(EnrichmentWorker.PROGRESS_TOTAL, 0),
                        currentTitle = running.progress.getString(EnrichmentWorker.PROGRESS_CURRENT_TITLE).orEmpty()
                    )
                    succeeded != null -> EnrichmentProgress(
                        processed = succeeded.outputData.getInt(EnrichmentWorker.OUTPUT_PROCESSED, 0),
                        total = succeeded.outputData.getInt(EnrichmentWorker.OUTPUT_PROCESSED, 0),
                        isCompleted = true
                    )
                    freshEnqueue != null -> EnrichmentProgress(isRunning = true)
                    else -> EnrichmentProgress()
                }
            }
            .distinctUntilChanged()
            .shareIn(sharingScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Enrich every song that still lacks artifacts. Replaces any running job. */
    fun enqueueMissing() {
        sharingScope.launch {
            val wifiOnly = userPreferencesRepository.enrichmentWifiOnlyFlow.first()
            workManager.enqueueUniqueWork(
                EnrichmentWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                EnrichmentWorker.missingWork(wifiOnly)
            )
        }
    }

    /** Re-run the full pipeline for the whole library. Replaces any running job. */
    fun enqueueAll() {
        sharingScope.launch {
            val wifiOnly = userPreferencesRepository.enrichmentWifiOnlyFlow.first()
            workManager.enqueueUniqueWork(
                EnrichmentWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                EnrichmentWorker.allWork(wifiOnly)
            )
        }
    }

    /** Re-run the full pipeline for an explicit set of songs. Replaces any running job. */
    fun enqueueSongs(songIds: LongArray) {
        if (songIds.isEmpty()) return
        sharingScope.launch {
            val wifiOnly = userPreferencesRepository.enrichmentWifiOnlyFlow.first()
            workManager.enqueueUniqueWork(
                EnrichmentWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                EnrichmentWorker.songsWork(songIds, wifiOnly)
            )
        }
    }

    /**
     * Automatic trigger (e.g. after a library sync completes): enqueues a
     * "missing" run only when the user enabled both enrichment in general and
     * the auto-after-sync option. KEEP so an in-flight job is left alone.
     */
    suspend fun enqueueMissingIfEnabled() {
        val autoEnabled = userPreferencesRepository.autoEnrichAfterSyncFlow.first()
        val enrichmentEnabled = userPreferencesRepository.enrichmentEnabledFlow.first()
        if (!autoEnabled || !enrichmentEnabled) return

        val wifiOnly = userPreferencesRepository.enrichmentWifiOnlyFlow.first()
        Timber.tag(TAG).i("Auto-enqueueing metadata enrichment (missing mode)")
        workManager.enqueueUniqueWork(
            EnrichmentWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            EnrichmentWorker.missingWork(wifiOnly)
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(EnrichmentWorker.WORK_NAME)
    }

    companion object {
        private const val TAG = "EnrichmentManager"
    }
}
