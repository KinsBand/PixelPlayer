package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.database.LyricsDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.network.NetworkAccessPolicy
import com.theveloper.pixelplay.data.network.NetworkDecision
import com.theveloper.pixelplay.data.network.NetworkPurpose
import com.theveloper.pixelplay.data.repository.LyricsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Library-wide synced-lyrics backfill.
 *
 * Every run walks the songs that do not have synced lyrics in the lyrics table yet and asks
 * [LyricsRepository.prefetchLyrics] to find them (stored copies, sidecar .lrc, LRCLIB, NetEase).
 * Songs that were searched without success are skipped by the repository's growing back-off,
 * so each run spends its network budget on songs that have a real chance of a new result.
 *
 * Runs periodically on an unmetered network with battery not low, and honours offline mode and
 * the lyrics integration switch (NetworkAccessPolicy).
 */
@HiltWorker
class LyricsBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicDao: MusicDao,
    private val lyricsDao: LyricsDao,
    private val lyricsRepository: LyricsRepository,
    private val networkAccessPolicy: NetworkAccessPolicy
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (networkAccessPolicy.getDecision(NetworkPurpose.Lyrics) != NetworkDecision.Allowed) {
                Timber.tag(TAG).i("lyrics backfill skipped: lyrics network access not allowed")
                return@withContext Result.success()
            }

            val allIds = musicDao.getAllSongIds()
            val syncedIds = allIds
                .chunked(900)
                .flatMap { chunk -> lyricsDao.getSongIdsWithSyncedLyrics(chunk) }
                .toHashSet()
            val pending = allIds.filterNot { it in syncedIds }.shuffled()
            if (pending.isEmpty()) {
                Timber.tag(TAG).i("lyrics backfill: every song already has synced lyrics")
                return@withContext Result.success()
            }

            var inspected = 0
            var found = 0
            val startedAt = System.currentTimeMillis()
            for (songId in pending) {
                if (isStopped) break
                if (inspected >= MAX_SONGS_PER_RUN) break
                if (System.currentTimeMillis() - startedAt > MAX_RUN_MS) break
                // Background work yields to playback/UI when the Java heap is tight.
                if (com.theveloper.pixelplay.data.diagnostics.HeapPressure.isElevated()) {
                    Timber.tag(TAG).w("lyrics backfill paused: heap pressure")
                    break
                }

                val entity = runCatching { musicDao.getSongByIdOnce(songId) }.getOrNull() ?: continue
                inspected++
                val hasSynced = try {
                    lyricsRepository.prefetchLyrics(entity.toSong())
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "backfill failed for song %d", songId)
                    false
                }
                if (hasSynced) found++

                setProgress(
                    Data.Builder()
                        .putInt(PROGRESS_INSPECTED, inspected)
                        .putInt(PROGRESS_FOUND, found)
                        .build()
                )
                delay(SPACING_MS)
            }

            Timber.tag(TAG).i(
                "lyrics backfill done: pending=%d inspected=%d nowSynced=%d",
                pending.size, inspected, found
            )
            Result.success(
                Data.Builder()
                    .putInt(OUTPUT_INSPECTED, inspected)
                    .putInt(OUTPUT_FOUND, found)
                    .build()
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "lyrics backfill failed")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LyricsBackfillWorker"
        const val WORK_NAME = "synced_lyrics_backfill"

        /** Songs looked at per run (most are cheap: stored or in back-off). */
        private const val MAX_SONGS_PER_RUN = 300
        /** Hard cap well below WorkManager's 10-minute execution limit. */
        private const val MAX_RUN_MS = 8L * 60L * 1000L
        /** Spacing between songs, keeps LRCLIB/NetEase traffic polite. */
        private const val SPACING_MS = 400L

        const val PROGRESS_INSPECTED = "inspected"
        const val PROGRESS_FOUND = "found"
        const val OUTPUT_INSPECTED = "output_inspected"
        const val OUTPUT_FOUND = "output_found"

        /**
         * Schedules the periodic backfill (every 6 hours, unmetered network, battery not low).
         * KEEP: calling this on every app start never resets the schedule.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<LyricsBackfillWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
