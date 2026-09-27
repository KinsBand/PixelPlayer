package com.theveloper.pixelplay.data.youtube

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playlists the user downloaded in full ("Keep in sync": new songs added later are downloaded
 * too) and the Wi-Fi-only preference for playlist downloads.
 */
@Singleton
class OfflinePlaylistStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("offline_playlists", Context.MODE_PRIVATE)

    private val _offlineIds = MutableStateFlow(prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet())
    val offlineIds: StateFlow<Set<String>> = _offlineIds.asStateFlow()

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun setKeepInSync(playlistId: String, keep: Boolean) {
        val next = if (keep) _offlineIds.value + playlistId else _offlineIds.value - playlistId
        prefs.edit().putStringSet(KEY_IDS, next).apply()
        _offlineIds.value = next
    }

    fun setWifiOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        _wifiOnly.value = value
    }

    /** True on Wi-Fi / ethernet / any network Android reports as not metered. */
    fun isOnUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun canDownloadNow(): Boolean = !_wifiOnly.value || isOnUnmeteredNetwork()

    companion object {
        private const val KEY_IDS = "offline_playlist_ids"
        private const val KEY_WIFI_ONLY = "wifi_only"

        /** Songs that need a download: online (YouTube / Spotify) and not already on the device. */
        fun needsDownload(song: Song, downloadedIds: Set<String>): Boolean {
            if (song.id in downloadedIds) return false
            val online = song.id.startsWith("yt_") || song.id.startsWith("spotify_") ||
                song.contentUriString.startsWith("youtube://") || song.contentUriString.startsWith("spotify://")
            return online && song.contentUriString.isNotBlank() && !song.id.startsWith("yt_unavailable:")
        }

        /** Rough size estimate for the confirm dialog (~4 MB per song, AAC ~128-160 kbps). */
        fun estimateMegabytes(songs: List<Song>): Int =
            songs.sumOf { s -> if (s.duration > 0) (s.duration / 1000.0 * 20_000 / 1_000_000).coerceAtLeast(1.0) else 4.0 }.toInt()
    }
}
