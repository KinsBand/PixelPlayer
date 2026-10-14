package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.account.CloudAccountConfig
import com.theveloper.pixelplay.data.account.CloudAccountRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Saves the library to the PixelPlay account in the background (Settings → Profile → Auto-save). */
@HiltWorker
class CloudAccountSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val account: CloudAccountRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        account.autoSaveIfEnabled()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: IllegalStateException) {
        // Signed out / sign-in expired / server not set up: nothing to retry.
        Timber.w(e, "Account auto-save skipped")
        Result.success()
    } catch (e: Exception) {
        Timber.w(e, "Account auto-save failed")
        if (runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "pixelplay_account_auto_save"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<CloudAccountSyncWorker>(
                CloudAccountConfig.AUTO_SAVE_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
