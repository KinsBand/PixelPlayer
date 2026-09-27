package com.theveloper.pixelplay.data.recognition.ambient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Started by a visible UI action after RECORD_AUDIO is granted, or re-started when the app
 * comes to the foreground while Listen is left on. Never restarted at boot.
 */
@AndroidEntryPoint
class AmbientListeningService : Service() {
    @Inject lateinit var controller: AmbientSuggestionController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Only an explicit stop (toggle or notification) turns Listen off for good.
            controller.persistEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (running.value) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Song suggestions", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.rounded_queue_music_24)
            .setContentTitle("Listen is on")
            .setContentText("Song mentions appear in your queue for approval")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Stop listening", stop)
            .build()
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            running.value = true
            controller.persistEnabled(true)
            controller.start { reason ->
                Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
                stopSelf()
            }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Microphone foreground service could not start")
            Toast.makeText(this, "Open PixelPlayer and grant microphone access to start Listen.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running.value = false
        controller.stop()
        super.onDestroy()
    }

    companion object {
        private val running = MutableStateFlow(false)
        val isRunning = running.asStateFlow()
        const val ACTION_STOP = "com.theveloper.pixelplay.STOP_LISTENING"
        private const val CHANNEL = "ambient_song_suggestions"
        private const val NOTIFICATION_ID = 7402
    }
}
