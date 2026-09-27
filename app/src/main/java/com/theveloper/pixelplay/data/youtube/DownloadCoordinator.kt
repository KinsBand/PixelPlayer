package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.CloudSongEntity
import com.theveloper.pixelplay.data.model.DownloadState
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackSource
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    fun isOnlineSong(song: Song): Boolean =
        song.youtubeId != null || song.id.startsWith("yt_") || song.id.startsWith("spotify_") ||
            song.contentUriString.startsWith("youtube://") || song.contentUriString.startsWith("spotify://") ||
            song.source == TrackSource.YOUTUBE_MUSIC

    fun isDownloaded(song: Song): Boolean =
        song.downloadState == DownloadState.DOWNLOADED || song.id in downloadedIds.value

    /** Starts (or joins) a download for [song]. Safe to call repeatedly. */
    fun download(song: Song) {
        if (!isOnlineSong(song)) {
            announce("This song is already on your device")
            return
        }
        scope.launch {
            val ok = downloadWithNotification(song, notifyUser = true)
            // Keep the liked-downloads playlist current once the user has created it.
            if (ok && playlistPreferencesRepository.getPlaylistsOnce().any { it.id == LIKED_DOWNLOADS_PLAYLIST_ID }) {
                syncLikedDownloadsPlaylist()
            }
        }
    }

    private suspend fun downloadWithNotification(song: Song, notifyUser: Boolean): Boolean {
        try { musicRepository.saveCloudSong(song) } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Timber.tag(TAG).w(e, "Could not persist cloud song %s", song.id) }

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
            val result = songDownloadManager.downloadSong(song)
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
                val gate = Semaphore(BULK_PARALLELISM)
                coroutineScope {
                    pending.map { song ->
                        async {
                            gate.withPermit {
                                val ok = downloadWithNotification(song, notifyUser = false)
                                _bulkState.update { state ->
                                    state?.copy(
                                        completed = state.completed + if (ok) 1 else 0,
                                        failed = state.failed + if (ok) 0 else 1
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
            syncLikedDownloadsPlaylist()
            val final = _bulkState.value
            _bulkState.value = final?.copy(running = false)
            announce(
                when {
                    final == null || final.total == 0 -> "All liked songs are already downloaded"
                    final.failed == 0 -> "Downloaded ${final.completed} liked songs"
                    else -> "Downloaded ${final.completed} liked songs, ${final.failed} failed"
                }
            )
        }
    }

    fun cancelBulk() {
        bulkJob?.cancel()
        _bulkState.update { it?.copy(running = false) }
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
