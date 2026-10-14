package com.theveloper.pixelplay.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.theveloper.pixelplay.R
import timber.log.Timber

/**
 * Indices of the next [max] songs after [current], following [next] (the timeline's play order:
 * shuffle respected, repeat ignored so the list never wraps back to the playing song). Stops at
 * the end, at an unset index, or on a repeat. Pure; tested in UpNextIndicesTest.
 */
internal fun upcomingIndices(current: Int, max: Int = 3, next: (Int) -> Int): List<Int> {
    if (current < 0) return emptyList()
    val out = ArrayList<Int>(max)
    val seen = hashSetOf(current)
    var index = current
    while (out.size < max) {
        index = next(index)
        if (index < 0 || !seen.add(index)) break
        out += index
    }
    return out
}

/**
 * The optional "Up next" notification (Settings → Appearance): a quiet, low-importance
 * notification with the next 3 songs, separate from the media notification. Posted while
 * something is loaded and there's at least one upcoming song; cancelled otherwise.
 * Created and driven by [MusicService] from its player listener.
 */
class UpNextNotifier(
    private val context: Context,
    private val openApp: PendingIntent,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val window = Timeline.Window()
    private var lastLines: List<String>? = null
    private var channelReady = false

    fun update(player: Player, enabled: Boolean) {
        if (!enabled || player.currentMediaItem == null ||
            player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED
        ) {
            cancel()
            return
        }
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            cancel()
            return
        }
        val shuffle = player.shuffleModeEnabled
        val indices = upcomingIndices(player.currentMediaItemIndex) { i ->
            if (i >= timeline.windowCount) C.INDEX_UNSET
            else timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        val lines = indices.mapNotNull { i ->
            val meta = timeline.getWindow(i, window).mediaItem.mediaMetadata
            val title = meta.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artist = meta.artist?.toString()?.takeIf { it.isNotBlank() }
            if (artist != null) "$title · $artist" else title
        }
        if (lines.isEmpty()) {
            cancel()
            return
        }
        if (lines == lastLines) return
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.rounded_queue_music_24)
            .setContentTitle(context.getString(R.string.up_next_notification_title))
            .setContentText(lines.first())
            .setStyle(style)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
            lastLines = lines
        } catch (e: SecurityException) {
            // Notification permission revoked while running.
            Timber.w(e, "Up next notification not allowed")
        }
    }

    fun cancel() {
        if (lastLines == null) return
        manager.cancel(NOTIFICATION_ID)
        lastLines = null
    }

    private fun ensureChannel() {
        if (channelReady) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.up_next_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        system.createNotificationChannel(channel)
        channelReady = true
    }

    companion object {
        const val CHANNEL_ID = "up_next"
        /** Not used by any other notification in the app (checked: 101, 1001, 1002, 7402). */
        const val NOTIFICATION_ID = 4107
    }
}
