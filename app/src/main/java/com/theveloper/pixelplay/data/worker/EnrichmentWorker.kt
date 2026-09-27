package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.theveloper.pixelplay.data.database.EnrichmentDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.enrichment.EnrichmentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Runs the metadata-enrichment pipeline (online metadata, audio analysis,
 * lyrics) over a set of songs in the background.
 *
 * Modes ([KEY_MODE]):
 *  - [MODE_MISSING]: every song missing an analysis row OR an editorial row.
 *  - [MODE_ALL]: the whole library, re-running both stages.
 *  - [MODE_IDS]: an explicit id list ([KEY_SONG_IDS]), re-running both stages.
 *
 * No ForegroundInfo: like [SyncWorker], this runs as a plain background
 * WorkManager job (battery-not-low constraint; unmetered network when the
 * user asked for wifi-only enrichment).
 */
@HiltWorker
class EnrichmentWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicDao: MusicDao,
    private val enrichmentDao: EnrichmentDao,
    private val enrichmentRepository: EnrichmentRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val mode = inputData.getString(KEY_MODE) ?: MODE_MISSING
            val songIds: List<Long> = when (mode) {
                MODE_ALL -> musicDao.getAllSongIds()
                MODE_IDS -> inputData.getLongArray(KEY_SONG_IDS)?.toList().orEmpty()
                else -> (
                    enrichmentDao.getTrackIdsMissingAnalysis() +
                        enrichmentDao.getTrackIdsMissingEditorial()
                    ).distinct()
            }

            if (songIds.isEmpty()) {
                Timber.tag(TAG).i("enrichment work ($mode): nothing to do")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(OUTPUT_PROCESSED, 0)
                        .putInt(OUTPUT_SUCCEEDED, 0)
                        .putInt(OUTPUT_FAILED, 0)
                        .build()
                )
            }

            // Explicit lists and full-library runs refresh everything; the
            // "missing" mode only fills gaps.
            val force = mode != MODE_MISSING
            Timber.tag(TAG).i("enrichment work ($mode): ${songIds.size} songs (force=$force)")

            val result = enrichmentRepository.enrichBatch(
                songIds = songIds,
                forceOnline = force,
                forceAnalysis = force,
                onProgress = { processed, total, currentTitle ->
                    setProgress(
                        Data.Builder()
                            .putInt(PROGRESS_PROCESSED, processed)
                            .putInt(PROGRESS_TOTAL, total)
                            .putString(PROGRESS_CURRENT_TITLE, currentTitle)
                            .build()
                    )
                }
            )

            Timber.tag(TAG).i(
                "enrichment work ($mode) done: processed=%d succeeded=%d failed=%d",
                result.processed, result.succeeded, result.failed
            )
            Result.success(
                Data.Builder()
                    .putInt(OUTPUT_PROCESSED, result.processed)
                    .putInt(OUTPUT_SUCCEEDED, result.succeeded)
                    .putInt(OUTPUT_FAILED, result.failed)
                    .build()
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "enrichment work failed catastrophically")
            if (runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "EnrichmentWorker"
        private const val MAX_RUN_ATTEMPTS = 3

        const val WORK_NAME = "metadata_enrichment"

        const val KEY_MODE = "mode"
        const val KEY_SONG_IDS = "song_ids"
        const val MODE_MISSING = "missing"
        const val MODE_ALL = "all"
        const val MODE_IDS = "ids"

        const val PROGRESS_PROCESSED = "processed"
        const val PROGRESS_TOTAL = "total"
        const val PROGRESS_CURRENT_TITLE = "current_title"

        const val OUTPUT_PROCESSED = "output_processed"
        const val OUTPUT_SUCCEEDED = "output_succeeded"
        const val OUTPUT_FAILED = "output_failed"

        /** Only songs that still lack enrichment artifacts. */
        fun missingWork(wifiOnly: Boolean): OneTimeWorkRequest =
            request(MODE_MISSING, wifiOnly = wifiOnly)

        /** The whole library, refreshing every stage. */
        fun allWork(wifiOnly: Boolean): OneTimeWorkRequest =
            request(MODE_ALL, wifiOnly = wifiOnly)

        /** An explicit set of songs, refreshing every stage. */
        fun songsWork(songIds: LongArray, wifiOnly: Boolean): OneTimeWorkRequest =
            request(MODE_IDS, wifiOnly = wifiOnly, songIds = songIds)

        private fun request(
            mode: String,
            wifiOnly: Boolean,
            songIds: LongArray? = null
        ): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()
            val input = Data.Builder().putString(KEY_MODE, mode)
            if (songIds != null) input.putLongArray(KEY_SONG_IDS, songIds)
            return OneTimeWorkRequestBuilder<EnrichmentWorker>()
                .setConstraints(constraints)
                .setInputData(input.build())
                .addTag(WORK_NAME)
                .build()
        }
    }
}
