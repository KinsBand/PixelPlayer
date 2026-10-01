package com.theveloper.pixelplay.data.recognition.ambient

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.recognition.RecentlyHeardRepository
import com.theveloper.pixelplay.data.recognition.RecentlyHeardSource
import com.theveloper.pixelplay.data.repository.HeardSongsRepository
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Captures Android / Pixel "Now Playing" notifications — both the always-on ambient ones and
 * the on-demand "Search song" results — into Recently heard.
 */
@AndroidEntryPoint
class NowPlayingNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var heardSongsRepository: HeardSongsRepository

    @Inject
    lateinit var recentlyHeardRepository: RecentlyHeardRepository

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Pick up a Now Playing notification that was already showing when access was granted.
        runCatching { activeNotifications }.getOrNull()?.forEach { handle(it, live = false) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handle(sbn ?: return, live = true)
    }

    private fun handle(sbn: StatusBarNotification, live: Boolean) {
        val pkg = sbn.packageName ?: return
        if (!isNowPlayingPackage(pkg)) return
        val parsed = parse(sbn.notification ?: return) ?: return
        val (title, artist) = parsed
        Timber.d("NowPlayingNotification: %s by %s (%s)", title, artist, pkg)

        recentlyHeardRepository.record(
            title = title,
            artist = artist,
            heardAtEpochMs = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            source = RecentlyHeardSource.NOW_PLAYING,
            announce = live
        )
        if (live) {
            heardSongsRepository.addOrUpvote(
                song = Song.emptySong().copy(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    artist = artist.ifBlank { "Unknown Artist" }
                ),
                isOnlineMatch = true,
                rawPhrase = "Pixel Now Playing Notification"
            )
        }
    }

    companion object {
        private val NOW_PLAYING_PACKAGES = setOf(
            "com.google.android.as",              // Android System Intelligence (current Now Playing)
            "com.google.intelligence.sense"       // Older Pixel Ambient Services
        )

        private val GENERIC_TEXT = Regex(
            "(tap to|song history|now playing|search|listening|recogni|history)",
            RegexOption.IGNORE_CASE
        )

        fun isNowPlayingPackage(pkg: String): Boolean =
            pkg in NOW_PLAYING_PACKAGES || pkg.contains("nowplaying", ignoreCase = true) ||
                pkg.contains("ambientmusic", ignoreCase = true)

        /**
         * Returns (title, artist). Handles both layouts Now Playing has used:
         * title "Yellow by Coldplay" (text is a hint like "Tap to see your song history"), and
         * title "Yellow" with text "Coldplay". Non-music notifications from the same package
         * (System Intelligence posts others) are rejected.
         */
        fun parse(notification: Notification): Pair<String, String>? {
            val extras = notification.extras ?: return null
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                ?.removePrefix("♪")?.trim().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (title.isBlank()) return null

            val channel = notification.channelId.orEmpty().lowercase()
            val looksLikeMusicChannel = listOf("ambient", "music", "now_playing", "nowplaying", "song")
                .any { channel.contains(it) }

            val byIndex = title.lastIndexOf(" by ")
            if (byIndex > 0 && (text.isBlank() || GENERIC_TEXT.containsMatchIn(text))) {
                val songTitle = title.substring(0, byIndex).trim()
                val artist = title.substring(byIndex + 4).trim()
                if (songTitle.isNotBlank()) return songTitle to artist
            }
            if (looksLikeMusicChannel && text.isNotBlank() && !GENERIC_TEXT.containsMatchIn(text) &&
                !GENERIC_TEXT.containsMatchIn(title)
            ) {
                return title to text
            }
            return null
        }

        fun isAccessGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun component(context: Context) = ComponentName(context, NowPlayingNotificationListener::class.java)
    }
}
