package com.theveloper.pixelplay.data.youtube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var notificationId = NOTIFICATION_ID_BASE

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Song Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when downloading songs"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
            // Questions ("download now?") must actually be seen, unlike silent progress.
            notificationManager.createNotificationChannel(
                NotificationChannel(PROMPT_CHANNEL_ID, "Download requests", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Asks before starting Wi-Fi-only downloads"
                    setShowBadge(false)
                }
            )
        }
    }

    fun showProgress(songTitle: String, percent: Int, notifId: Int = notificationId, songId: String? = null): Int {
        val id = notifId
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(songTitle)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
        if (songId != null) {
            builder.addAction(0, "Pause", songAction(DownloadActionReceiver.ACTION_PAUSE, songId, id * 4))
            builder.addAction(0, "Cancel", songAction(DownloadActionReceiver.ACTION_CANCEL, songId, id * 4 + 1))
        }
        notificationManager.notify(id, builder.build())
        return id
    }

    /** A paused download: stays until resumed or cancelled. */
    fun showPaused(songTitle: String, percent: Int, notifId: Int, songId: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download paused")
            .setContentText(songTitle)
            .setProgress(100, percent, false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Resume", songAction(DownloadActionReceiver.ACTION_RESUME, songId, notifId * 4 + 2))
            .addAction(0, "Cancel", songAction(DownloadActionReceiver.ACTION_CANCEL, songId, notifId * 4 + 3))
            .build()
        notificationManager.notify(notifId, notification)
    }

    /** Removes the notification of a single-song download (same id [showResolving] uses). */
    fun cancelForSong(songId: String) {
        notificationManager.cancel(kotlin.math.abs(songId.hashCode()) % 10000 + NOTIFICATION_ID_BASE)
    }

    private fun songAction(action: String, songId: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, DownloadActionReceiver::class.java)
                .setAction(action)
                .putExtra(DownloadActionReceiver.EXTRA_SONG_ID, songId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun showResolving(songTitle: String, songId: String? = null): Int {
        val id = songId?.hashCode()?.let { kotlin.math.abs(it) % 10000 + NOTIFICATION_ID_BASE } ?: notificationId
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Preparing download")
            .setContentText(songTitle)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        notificationManager.notify(id, notification)
        return id
    }

    fun showCompleted(songTitle: String, notifId: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(songTitle)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notifId, notification)
        // Prepare next notification ID for next download
        notificationId = notifId + 1
    }

    fun showError(songTitle: String, error: String, notifId: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("$songTitle: $error")
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notifId, notification)
        notificationId = notifId + 1
    }

    fun cancel(notifId: Int) {
        notificationManager.cancel(notifId)
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, DownloadActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** One notification for the whole "download all liked songs" run, with a Cancel button. */
    fun showBulkProgress(done: Int, total: Int, failed: Int) {
        val text = buildString {
            append("$done of $total songs")
            if (failed > 0) append(" · $failed failed")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading liked songs")
            .setContentText(text)
            .setProgress(total.coerceAtLeast(1), done.coerceAtMost(total), total == 0)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", actionIntent(DownloadActionReceiver.ACTION_CANCEL_BULK, BULK_ID))
            .build()
        notificationManager.notify(BULK_ID, notification)
    }

    fun showBulkFinished(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Liked songs")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(BULK_ID, notification)
    }

    fun cancelBulk() = notificationManager.cancel(BULK_ID)

    /** Wi-Fi is back and a Wi-Fi-only bulk download is waiting: ask before starting. */
    fun showWifiApproval(pendingCount: Int) {
        val notification = NotificationCompat.Builder(context, PROMPT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Connected to Wi-Fi")
            .setContentText("Download $pendingCount liked ${if (pendingCount == 1) "song" else "songs"} now?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setDeleteIntent(actionIntent(DownloadActionReceiver.ACTION_POSTPONE_WIFI_BULK, WIFI_APPROVAL_ID + 2))
            .addAction(0, "Download", actionIntent(DownloadActionReceiver.ACTION_APPROVE_WIFI_BULK, WIFI_APPROVAL_ID))
            .addAction(0, "Not now", actionIntent(DownloadActionReceiver.ACTION_POSTPONE_WIFI_BULK, WIFI_APPROVAL_ID + 1))
            .build()
        notificationManager.notify(WIFI_APPROVAL_ID, notification)
    }

    fun cancelWifiApproval() = notificationManager.cancel(WIFI_APPROVAL_ID)

    companion object {
        private const val CHANNEL_ID = "pixelplay_downloads"
        private const val PROMPT_CHANNEL_ID = "pixelplay_download_prompts"
        private const val NOTIFICATION_ID_BASE = 9000
        // Below the per-song range (9000 + up to 9999).
        private const val BULK_ID = 8800
        private const val WIFI_APPROVAL_ID = 8810
    }
}
