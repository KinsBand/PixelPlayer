package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.CloudSongEntity
import com.theveloper.pixelplay.data.model.DownloadState
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackSource
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped owner of every user-started download.
 *
 * Downloads used to run in the song-options sheet's ViewModel scope, so closing the sheet or
 * leaving the screen cancelled the transfer half way (and the cancellation was swallowed, so it
 * looked like downloads "just didn't work"). Everything now runs here, outlives the UI, shows a
 * notification and reports state that the player title badge and Settings observe.
 */
@Singleton
class DownloadCoordinator @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val songDownloadManager: SongDownloadManager,
    private val notifications: DownloadNotificationManager,
    private val cloudSongDao: CloudSongDao,
    private val musicRepository: MusicRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ids (cloud song ids such as `yt_…` / `spotify_…`) whose audio is saved on the device. */
    val downloadedIds: StateFlow<Set<String>> = cloudSongDao.getDownloadedSongIds()
        .map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val progress: StateFlow<Map<String, DownloadProgress>> = songDownloadManager.downloadProgressMap

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Emits to [events] and shows a toast, since downloads outlive whichever screen started them. */
    private fun announce(message: String) {
        _events.tryEmit(message)
        scope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    data class BulkDownloadState(
        val total: Int,
        val completed: Int = 0,
        val failed: Int = 0,
        val running: Boolean = true,
    )

    private val _bulkState = MutableStateFlow<BulkDownloadState?>(null)
    val bulkState: StateFlow<BulkDownloadState?> = _bulkState.asStateFlow()
    private var bulkJob: Job? = null

    /** "Download all liked songs" only runs on Wi-Fi (unmetered) when this is on. */
    val wifiOnly: StateFlow<Boolean> = userPreferencesRepository.likedDownloadsWifiOnlyFlow
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** An approved bulk download is waiting for Wi-Fi. */
    val waitingForWifi: StateFlow<Boolean> = userPreferencesRepository.likedDownloadsWaitingForWifiFlow
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
    @Volatile private var onUnmetered: Boolean? = null
    @Volatile private var started = false

    private fun isOnUnmetered(): Boolean {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Watches the connection (called once from the Application). Joining Wi-Fi while a
     * Wi-Fi-only bulk download is waiting asks for approval with a notification; leaving Wi-Fi
     * mid-run pauses it until the next time.
     */
    fun start() {
        if (started) return
        started = true
        connectivity?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                onConnectionChanged(caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            }

            override fun onLost(network: android.net.Network) = onConnectionChanged(false)
        })
    }

    private fun onConnectionChanged(unmetered: Boolean) {
        val previous = onUnmetered
        onUnmetered = unmetered
        if (previous == unmetered) return
        scope.launch {
            if (unmetered) {
                if (bulkJob?.isActive == true) return@launch
                if (!userPreferencesRepository.likedDownloadsWaitingForWifiFlow.first()) return@launch
                val pending = likedSongsToDownload().size
                if (pending == 0) userPreferencesRepository.setLikedDownloadsWaitingForWifi(false)
                else notifications.showWifiApproval(pending)
            } else if (previous == true) {
                notifications.cancelWifiApproval()
                if (bulkJob?.isActive == true && userPreferencesRepository.likedDownloadsWifiOnlyFlow.first()) {
                    cancelBulk()
                    userPreferencesRepository.setLikedDownloadsWaitingForWifi(true)
                    notifications.showBulkFinished("Paused until you're back on Wi-Fi")
                }
            }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setLikedDownloadsWifiOnly(enabled)
            // Already approved and only waiting for Wi-Fi: nothing to wait for any more.
            if (!enabled && userPreferencesRepository.likedDownloadsWaitingForWifiFlow.first()) {
                approveWaitingBulkDownload()
            }
        }
    }

    /**
     * The Settings "Download all liked songs" confirmation. Starts now, or (Wi-Fi only and not
     * on Wi-Fi) remembers the approval and asks again when Wi-Fi connects.
     */
    fun requestDownloadAllLiked() {
        scope.launch {
            if (userPreferencesRepository.likedDownloadsWifiOnlyFlow.first() && !isOnUnmetered()) {
                userPreferencesRepository.setLikedDownloadsWaitingForWifi(true)
                announce("Liked songs will download when you're on Wi-Fi")
            } else {
                downloadAllLiked()
            }
        }
    }

    /** "Download" on the Wi-Fi notification. */
    fun approveWaitingBulkDownload() {
        notifications.cancelWifiApproval()
        scope.launch {
            userPreferencesRepository.setLikedDownloadsWaitingForWifi(false)
            downloadAllLiked()
        }
    }

    /** "Not now" (or swiped away): keep waiting and ask again next time Wi-Fi connects. */
    fun postponeWaitingBulkDownload() {
        notifications.cancelWifiApproval()
    }

    /** Settings: stop waiting for Wi-Fi. */
    fun cancelWaitingForWifi() {
        notifications.cancelWifiApproval()
        scope.launch { userPreferencesRepository.setLikedDownloadsWaitingForWifi(false) }
    }

    fun isOnlineSong(song: Song): Boolean =
        song.youtubeId != null || song.id.startsWith("yt_") || song.id.startsWith("spotify_") ||
            song.contentUriString.startsWith("youtube://") || song.contentUriString.startsWith("spotify://") ||
            song.source == TrackSource.YOUTUBE_MUSIC

    fun isDownloaded(song: Song): Boolean =
        song.downloadState == DownloadState.DOWNLOADED || song.id in downloadedIds.value

    /** High / Medium / Low with sizes, for the full player's download menu. */
    suspend fun downloadOptions(song: Song): List<DownloadOption> = songDownloadManager.downloadOptions(song)

    fun estimateSeconds(bytes: Long): Long = songDownloadManager.estimateSeconds(bytes)

    /** Starts (or joins) a download for [song]. Safe to call repeatedly. */
    fun download(song: Song, quality: DownloadQuality? = null) {
        if (!isOnlineSong(song)) {
            announce("This song is already on your device")
            return
        }
        scope.launch {
            val ok = downloadWithNotification(song, notifyUser = true, quality = quality)
            // Keep the liked-downloads playlist current once the user has created it.
            if (ok && playlistPreferencesRepository.getPlaylistsOnce().any { it.id == LIKED_DOWNLOADS_PLAYLIST_ID }) {
                syncLikedDownloadsPlaylist()
            }
        }
    }

    /**
     * @param songNotification Per-song notification; off for the bulk run, which has one
     *   notification for the whole batch.
     */
    private suspend fun downloadWithNotification(
        song: Song,
        notifyUser: Boolean,
        songNotification: Boolean = true,
        quality: DownloadQuality? = null,
    ): Boolean {
        try { musicRepository.saveCloudSong(song) } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Timber.tag(TAG).w(e, "Could not persist cloud song %s", song.id) }

        if (!songNotification) return songDownloadManager.downloadSong(song).isSuccess
        val notifId = notifications.showResolving(song.title, song.id)
        val progressJob = scope.launch {
            var lastPercent = -1
            songDownloadManager.downloadProgressMap.collect { map ->
                val p = map[song.id] as? DownloadProgress.Downloading ?: return@collect
                if (p.percent != lastPercent) {
                    lastPercent = p.percent
                    notifications.showProgress(song.title, p.percent, notifId)
                }
            }
        }
        return try {
            val result = songDownloadManager.downloadSong(song, quality)
            result.fold(
                onSuccess = {
                    notifications.showCompleted(song.title, notifId)
                    if (notifyUser) announce("Downloaded: ${song.title}")
                    true
                },
                onFailure = { error ->
                    val message = error.message ?: "Unknown error"
                    notifications.showError(song.title, message, notifId)
                    if (notifyUser) announce("Download failed: $message")
                    false
                }
            )
        } catch (e: CancellationException) {
            notifications.cancel(notifId)
            throw e
        } finally {
            progressJob.cancel()
        }
    }

    /** Liked online songs that are not saved on the device yet. */
    suspend fun likedSongsToDownload(): List<Song> =
        cloudSongDao.getFavoritedCloudSongsOnce()
            .filter { !it.isDownloaded && (it.youtubeId != null || it.id.startsWith("yt_") || it.id.startsWith("spotify_")) }
            .map { it.toDownloadSong() }

    /** Number of liked songs already downloaded (for the Settings summary line). */
    suspend fun likedSongsDownloadedCount(): Int =
        cloudSongDao.getFavoritedCloudSongsOnce().count { it.isDownloaded }

    /**
     * Downloads every liked online song in one go (after the user approved it once in Settings)
     * and collects all downloaded liked songs in the [LIKED_DOWNLOADS_PLAYLIST_NAME] playlist.
     */
    fun downloadAllLiked() {
        if (bulkJob?.isActive == true) return
        bulkJob = scope.launch {
            val pending = likedSongsToDownload()
            _bulkState.value = BulkDownloadState(total = pending.size, running = pending.isNotEmpty())
            if (pending.isNotEmpty()) {
                notifications.showBulkProgress(0, pending.size, 0)
                val gate = Semaphore(BULK_PARALLELISM)
                coroutineScope {
                    pending.map { song ->
                        async {
                            gate.withPermit {
                                val ok = downloadWithNotification(song, notifyUser = false, songNotification = false)
                                val state = _bulkState.updateAndGet { current ->
                                    current?.copy(
                                        completed = current.completed + if (ok) 1 else 0,
                                        failed = current.failed + if (ok) 0 else 1
                                    )
                                }
                                if (state != null) {
                                    notifications.showBulkProgress(state.completed + state.failed, state.total, state.failed)
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
            syncLikedDownloadsPlaylist()
            val final = _bulkState.value
            _bulkState.value = final?.copy(running = false)
            val summary = when {
                final == null || final.total == 0 -> "All liked songs are already downloaded"
                final.failed == 0 -> "Downloaded ${final.completed} liked songs"
                else -> "Downloaded ${final.completed} liked songs, ${final.failed} failed"
            }
            if (final != null && final.total > 0) notifications.showBulkFinished(summary) else notifications.cancelBulk()
            announce(summary)
        }
    }

    fun cancelBulk() {
        bulkJob?.cancel()
        _bulkState.update { it?.copy(running = false) }
        notifications.cancelBulk()
    }

    /**
     * Makes the "Liked songs (downloaded)" playlist contain the local copy of every liked song
     * that has been downloaded, so the playlist plays fully offline.
     */
    suspend fun syncLikedDownloadsPlaylist() {
        try {
            val localIds = cloudSongDao.getFavoritedCloudSongsOnce()
                .filter { it.isDownloaded }
                .map { SongDownloadManager.localLibraryId(it.id).toString() }
            if (localIds.isEmpty()) return
            val existing = playlistPreferencesRepository.getPlaylistsOnce()
                .firstOrNull { it.id == LIKED_DOWNLOADS_PLAYLIST_ID }
            if (existing == null) {
                playlistPreferencesRepository.createPlaylist(
                    name = LIKED_DOWNLOADS_PLAYLIST_NAME,
                    songIds = localIds,
                    customId = LIKED_DOWNLOADS_PLAYLIST_ID
                )
            } else {
                playlistPreferencesRepository.addSongsToPlaylist(existing.id, localIds)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not update the liked downloads playlist")
        }
    }

    private fun CloudSongEntity.toDownloadSong(): Song {
        val videoId = youtubeId?.removePrefix("yt_") ?: id.takeIf { it.startsWith("yt_") }?.removePrefix("yt_")
        return Song.emptySong().copy(
            id = id,
            title = title,
            artist = artist,
            album = album.orEmpty(),
            duration = duration,
            albumArtUriString = thumbnailUrl,
            contentUriString = contentUriString.ifBlank { videoId?.let { "youtube://$it" }.orEmpty() },
            youtubeId = videoId,
            isFavorite = true,
            mimeType = "audio/mp4",
        )
    }

    companion object {
        private const val TAG = "DownloadCoordinator"
        private const val BULK_PARALLELISM = 2
        const val LIKED_DOWNLOADS_PLAYLIST_ID = "pixelplay_liked_downloads"
        const val LIKED_DOWNLOADS_PLAYLIST_NAME = "Liked songs (downloaded)"
    }
}
