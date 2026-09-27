package com.theveloper.pixelplay.data.youtube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
        }
    }

    fun showProgress(songTitle: String, percent: Int, notifId: Int = notificationId): Int {
        val id = notifId
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(songTitle)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        notificationManager.notify(id, notification)
        return id
    }

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

    companion object {
        private const val CHANNEL_ID = "pixelplay_downloads"
        private const val NOTIFICATION_ID_BASE = 9000
    }
}
