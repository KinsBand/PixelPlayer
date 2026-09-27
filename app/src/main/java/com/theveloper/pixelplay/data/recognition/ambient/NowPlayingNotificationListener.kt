package com.theveloper.pixelplay.data.recognition.ambient

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.HeardSongsRepository
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class NowPlayingNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var heardSongsRepository: HeardSongsRepository

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return

        // Check if notification is from Pixel Ambient Services or Google
        if (pkg == "com.google.android.as" || pkg == "com.google.intelligence.sense") {
            extractAndPostSong(notification)
        }
    }

    private fun extractAndPostSong(notification: Notification) {
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()

        if (!title.isNullOrBlank()) {
            Timber.d("NowPlayingNotification: Detected ambient song: %s by %s", title, text)
            val song = Song.emptySong().copy(
                id = UUID.randomUUID().toString(),
                title = title,
                artist = text ?: "Unknown Artist"
            )
            heardSongsRepository.addOrUpvote(
                song = song,
                isOnlineMatch = true,
                rawPhrase = "Pixel Now Playing Notification"
            )
        }
    }
}
