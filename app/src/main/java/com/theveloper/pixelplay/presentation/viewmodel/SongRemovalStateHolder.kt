package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Activity
import android.content.IntentSender
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.utils.MediaStorePermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Callbacks supplied by [PlayerViewModel] so the device-deletion flow can reach
 * ViewModel-owned state (toasts, the media-controller queue, and the full
 * "remove song from library + player" routine) and the ViewModel's
 * [CoroutineScope] without [SongRemovalStateHolder] depending on the ViewModel.
 * Mirrors the lambda-callback pattern already used by [MetadataEditCallbacks].
 */
class SongRemovalCallbacks(
    val scope: CoroutineScope,
    val sendToast: (String) -> Unit,
    val removeFromMediaControllerQueue: (String) -> Unit,
    val removeSong: suspend (Song) -> Unit,
)

@ViewModelScoped
class SongRemovalStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val libraryStateHolder: LibraryStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val multiSelectionStateHolder: MultiSelectionStateHolder,
    private val cloudSongDao: com.theveloper.pixelplay.data.database.CloudSongDao,
    private val yourMusicRemovals: com.theveloper.pixelplay.data.library.YourMusicRemovals,
    @param:ApplicationContext private val context: android.content.Context
) {

    // MediaStore delete-permission request (Android 11+ system delete dialog).
    // Owned here because only the deletion cluster emits/consumes it; the ViewModel re-exposes it.
    private val _deletePermissionRequest = MutableSharedFlow<IntentSender>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val deletePermissionRequest: SharedFlow<IntentSender> = _deletePermissionRequest.asSharedFlow()

    // Deletions parked while waiting for the user's MediaStore delete-permission decision.
    private var pendingBatchDeleteSongs: List<Song>? = null
    private var pendingBatchDeleteSkippedCount: Int = 0
    private var pendingBatchDeleteOnComplete: (() -> Unit)? = null
    private var pendingBatchDeleteOnFinished: ((Boolean) -> Unit)? = null
    private var pendingDeleteSong: Song? = null
    private var pendingDeleteCallback: ((Boolean) -> Unit)? = null

    suspend fun showDeleteConfirmation(activity: Activity, song: Song): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@withContext false
                }

                val userChoice = CompletableDeferred<Boolean>()
                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.song_removal_dialog_delete_song_title))
                    .setMessage(
                        activity.getString(
                            R.string.song_removal_dialog_delete_song_message,
                            song.title,
                            song.displayArtist
                        )
                    )
                    .setPositiveButton(activity.getString(R.string.common_delete)) { _, _ ->
                        userChoice.complete(true)
                    }
                    .setNegativeButton(activity.getString(R.string.common_cancel)) { _, _ ->
                        userChoice.complete(false)
                    }
                    .setOnCancelListener {
                        userChoice.complete(false)
                    }
                    .setCancelable(true)
                    .create()

                dialog.show()
                userChoice.await()
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun deleteSongFile(song: Song): Boolean {
        return metadataEditStateHolder.deleteSong(song)
    }

    suspend fun removeSongFromLibrary(song: Song) {
        libraryStateHolder.removeSong(song.id)
        // Downloaded online songs have non-numeric ids ("yt_…"); toLong() threw for them.
        song.id.toLongOrNull()?.let { musicRepository.deleteById(it) }
        playlistPreferencesRepository.removeSongFromAllPlaylists(song.id)
    }

    // region Device-deletion cluster (moved from PlayerViewModel)

    /**
     * Deletes all selected songs from device with confirmation.
     * Shows a single confirmation dialog for all songs.
     */
    fun deleteSelectedFromDevice(
        activity: Activity,
        songs: List<Song>,
        onComplete: () -> Unit,
        cb: SongRemovalCallbacks,
        /** Told whether any file was deleted (false when the user cancelled or nothing could go). */
        onFinished: ((deletedAny: Boolean) -> Unit)? = null,
    ) {
        cb.scope.launch {
            // The playing song can be deleted too: it is dropped from the queue (playback
            // moves on to the next song) before or as soon as its file is removed.
            val deletableSongs = songs
            if (deletableSongs.isEmpty()) {
                onComplete()
                onFinished?.invoke(false)
                return@launch
            }

            val skippedCount = 0

            // On Android 11+, use system batch delete dialog
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val deleteRequests = withContext(Dispatchers.IO) {
                    deletableSongs.mapNotNull { song ->
                        MediaStorePermissionHelper
                            .resolveDeleteRequestUri(
                                context = activity,
                                songId = song.id.toLongOrNull(),
                                contentUriString = song.contentUriString,
                                filePath = song.path,
                            )?.let { uri -> song to uri }
                    }
                }
                if (deleteRequests.size == deletableSongs.size) {
                    val uris = deleteRequests.map { it.second }.distinctBy { it.toString() }
                    val deleteRequest = try {
                        MediaStorePermissionHelper.createDeleteRequest(activity, uris)
                    } catch (e: Throwable) {
                        null
                    }
                    if (deleteRequest != null) {
                        val acceptedUriStrings = deleteRequest.acceptedUris
                            .mapTo(mutableSetOf()) { it.toString() }
                        val acceptedSongs = deleteRequests
                            .filter { (_, uri) -> uri.toString() in acceptedUriStrings }
                            .map { it.first }
                        val invalidRequestCount = deletableSongs.size - acceptedSongs.size

                        pendingBatchDeleteSongs = acceptedSongs
                        pendingBatchDeleteSkippedCount = skippedCount + invalidRequestCount
                        pendingBatchDeleteOnComplete = onComplete
                        pendingBatchDeleteOnFinished = onFinished
                        _deletePermissionRequest.emit(deleteRequest.intentSender)
                        return@launch
                    }
                }
            }

            // Fallback for older Android or non-MediaStore songs
            val confirmed = showMultiDeleteConfirmation(activity, deletableSongs.size)
            if (!confirmed) {
                onComplete()
                onFinished?.invoke(false)
                return@launch
            }

            var successCount = 0
            deletableSongs.forEach { song ->
                val success = deleteSongFile(song)
                if (success) {
                    // Removing the playing item makes the player move straight on to the
                    // next one; the already-open file handle keeps audio going until then.
                    cb.removeFromMediaControllerQueue(song.id)
                    cb.removeSong(song)
                    successCount++
                }
            }

            when {
                successCount == deletableSongs.size && skippedCount == 0 ->
                    cb.sendToast(
                        context.resources.getQuantityString(R.plurals.song_removal_n_files_deleted, successCount, successCount),
                    )
                successCount == deletableSongs.size && skippedCount > 0 ->
                    cb.sendToast(
                        context.getString(
                            R.string.song_removal_batch_delete_files_deleted_skipped_format,
                            successCount,
                            skippedCount,
                        ),
                    )
                successCount > 0 ->
                    cb.sendToast(
                        context.getString(
                            R.string.song_removal_batch_delete_partial_format,
                            successCount,
                            deletableSongs.size,
                        ),
                    )
                else ->
                    cb.sendToast(context.getString(R.string.song_removal_delete_files_failed))
            }

            multiSelectionStateHolder.clearSelection()
            onComplete()
            onFinished?.invoke(successCount > 0)
        }
    }

    private suspend fun showMultiDeleteConfirmation(activity: Activity, count: Int): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@withContext false
                }

                val userChoice = CompletableDeferred<Boolean>()

                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle(
                        context.resources.getQuantityString(
                            R.plurals.song_removal_delete_songs_confirmation_title,
                            count,
                            count,
                        ),
                    )
                    .setMessage(context.getString(R.string.song_removal_delete_songs_permanent_message))
                    .setPositiveButton(context.getString(R.string.common_delete)) { _, _ ->
                        userChoice.complete(true)
                    }
                    .setNegativeButton(context.getString(R.string.common_cancel)) { _, _ ->
                        userChoice.complete(false)
                    }
                    .setOnCancelListener {
                        userChoice.complete(false)
                    }
                    .setCancelable(true)
                    .create()

                dialog.show()
                userChoice.await()
            } catch (e: Exception) {
                false
            }
        }
    }

    fun deleteFromDevice(
        activity: Activity,
        song: Song,
        onResult: (Boolean) -> Unit = {},
        cb: SongRemovalCallbacks,
    ) {
        cb.scope.launch {
            // Deleting the playing song is allowed: once the file is gone the song is taken
            // out of the queue, so playback continues with the next one (or stops if it was
            // the last). See PlayerViewModel.removeSong for how the player UI reacts.

            // On Android 11+, use the system delete confirmation dialog via MediaStore.createDeleteRequest()
            // which both confirms AND handles deletion in one step (no MANAGE_EXTERNAL_STORAGE needed).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intentSender = withContext(Dispatchers.IO) {
                    try {
                        MediaStorePermissionHelper
                            .resolveDeleteRequestUri(
                                context = activity,
                                songId = song.id.toLongOrNull(),
                                contentUriString = song.contentUriString,
                                filePath = song.path,
                            )?.let { uri ->
                                MediaStorePermissionHelper
                                    .createDeleteRequestIntentSender(activity, listOf(uri))
                            }
                    } catch (e: Throwable) {
                        null
                    }
                }
                if (intentSender != null) {
                    pendingDeleteSong = song
                    pendingDeleteCallback = onResult
                    _deletePermissionRequest.emit(intentSender)
                    return@launch
                }
            }

            // Fallback for older Android or files not in MediaStore
            val userConfirmed = showDeleteConfirmation(activity, song)
            if (!userConfirmed) {
                onResult(false)
                return@launch
            }

            val success = deleteSongFile(song)
            if (success) {
                cb.sendToast(context.getString(R.string.song_removal_file_deleted))
                cb.removeFromMediaControllerQueue(song.id)
                cb.removeSong(song)
                onResult(true)
            } else {
                cb.sendToast(context.getString(R.string.song_removal_delete_file_not_found))
                onResult(false)
            }
        }
    }

    /** Called from the UI after the user approves or denies the MediaStore delete request. */
    fun onDeletePermissionResult(granted: Boolean, cb: SongRemovalCallbacks) {
        // Handle batch delete
        val batchSongs = pendingBatchDeleteSongs
        if (batchSongs != null) {
            val skippedCount = pendingBatchDeleteSkippedCount
            val onComplete = pendingBatchDeleteOnComplete
            val onFinished = pendingBatchDeleteOnFinished
            pendingBatchDeleteSongs = null
            pendingBatchDeleteSkippedCount = 0
            pendingBatchDeleteOnComplete = null
            pendingBatchDeleteOnFinished = null
            cb.scope.launch {
                if (granted) {
                    // System already deleted the files — clean up library
                    batchSongs.forEach { song ->
                        cb.removeFromMediaControllerQueue(song.id)
                        cb.removeSong(song)
                    }
                    val count = batchSongs.size
                    if (skippedCount > 0) {
                        cb.sendToast(
                            context.getString(
                                R.string.song_removal_batch_delete_files_deleted_skipped_format,
                                count,
                                skippedCount,
                            ),
                        )
                    } else {
                        cb.sendToast(
                            context.resources.getQuantityString(R.plurals.song_removal_n_files_deleted, count, count),
                        )
                    }
                } else {
                    cb.sendToast(context.getString(R.string.song_removal_deletion_cancelled))
                }
                multiSelectionStateHolder.clearSelection()
                onComplete?.invoke()
                onFinished?.invoke(granted && batchSongs.isNotEmpty())
            }
            return
        }

        // Handle single delete
        val song = pendingDeleteSong ?: return
        val callback = pendingDeleteCallback
        pendingDeleteSong = null
        pendingDeleteCallback = null
        cb.scope.launch {
            if (granted) {
                // The system already deleted the file — just clean up the library
                cb.sendToast(context.getString(R.string.song_removal_file_deleted))
                cb.removeFromMediaControllerQueue(song.id)
                cb.removeSong(song)
                callback?.invoke(true)
            } else {
                callback?.invoke(false)
            }
        }
    }

    // endregion

    // region Your Music: delete every copy of a song

    /**
     * Deletes a Your Music row completely: every copy it stands for ([copies]: the local file,
     * the app's download, the streamed / liked copy) is removed, so the song is really gone.
     *
     *  1. Files on the device go through the normal delete (the system's delete dialog on
     *     Android 11+, which is also the confirmation). With no file on the device, a plain
     *     confirmation dialog asks first. Cancelling stops everything.
     *  2. The app's downloads of the song are deleted (file and download record).
     *  3. Every copy is unliked, taken out of the queue, out of the library and out of all
     *     playlists ([SongRemovalCallbacks.removeSong]).
     *  4. The song is hidden from Your Music, so a service that keeps reporting it as liked
     *     (Spotify, a mirrored "Favourites" playlist) can't bring it back. Liking it again does.
     */
    fun deleteFromYourMusic(
        activity: Activity,
        song: Song,
        copies: List<Song>,
        onResult: (Boolean) -> Unit,
        cb: SongRemovalCallbacks,
    ) {
        cb.scope.launch {
            val all = (listOf(song) + copies).distinctBy { it.id }
            val deviceFiles = all.filter { it.isLocal && !isAppDownloadCopy(it) && it.path.isNotBlank() }
            // File deletion reports through its own toasts; this flow shows one at the end.
            val quiet = SongRemovalCallbacks(cb.scope, sendToast = {}, cb.removeFromMediaControllerQueue, cb.removeSong)

            val confirmed = if (deviceFiles.isNotEmpty()) {
                val done = CompletableDeferred<Boolean>()
                deleteSelectedFromDevice(activity, deviceFiles, onComplete = {}, cb = quiet) { deleted -> done.complete(deleted) }
                done.await()
            } else {
                showYourMusicDeleteConfirmation(activity, song)
            }
            if (!confirmed) {
                onResult(false)
                return@launch
            }

            deleteDownloadsOf(all)
            all.forEach { copy ->
                cb.removeFromMediaControllerQueue(copy.id)
                cb.removeSong(copy)
            }
            yourMusicRemovals.remove(all)
            cb.sendToast(context.getString(R.string.song_removal_deleted_from_your_music))
            onResult(true)
        }
    }

    /** The song was liked (or downloaded) again after being deleted: show it in Your Music again. */
    fun restoreToYourMusic(song: Song) = yourMusicRemovals.restore(song)

    private fun isAppDownloadCopy(song: Song): Boolean =
        com.theveloper.pixelplay.data.database.SourceType.isAppDownload(song.path) ||
            com.theveloper.pixelplay.data.database.SourceType.isAppDownload(song.contentUriString)

    /** Deletes the app's downloads of any of [songs]: the audio file and the download record. */
    private suspend fun deleteDownloadsOf(songs: List<Song>) = withContext(Dispatchers.IO) {
        val ids = songs.mapTo(HashSet()) { it.id }
        val videoIds = songs.mapNotNullTo(HashSet()) { s ->
            (s.youtubeId ?: s.id.takeIf { it.startsWith("yt_") })?.removePrefix("yt_")?.takeIf { it.isNotBlank() }
        }
        val paths = songs.mapNotNullTo(HashSet()) { it.path.takeIf { p -> p.isNotBlank() } }
        val downloads = try {
            cloudSongDao.getAllOnce().filter { it.isDownloaded }
        } catch (_: Exception) {
            emptyList()
        }
        downloads.filter { row ->
            row.id in ids ||
                row.youtubeId?.removePrefix("yt_") in videoIds ||
                row.localFilePath in paths ||
                com.theveloper.pixelplay.data.library.DownloadedLibraryIndexer.libraryId(row.id).toString() in ids
        }.forEach { row ->
            (row.localFilePath ?: row.localSongId)?.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { java.io.File(path).takeIf { it.isFile }?.delete() }
            }
            // The library indexer sees the download is gone and removes its library row.
            runCatching { cloudSongDao.updateDownloadStatus(row.id, false, null, null) }
        }
    }

    private suspend fun showYourMusicDeleteConfirmation(activity: Activity, song: Song): Boolean =
        withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) return@withContext false
                val userChoice = CompletableDeferred<Boolean>()
                MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.song_removal_your_music_title))
                    .setMessage(activity.getString(R.string.song_removal_your_music_message, song.title, song.displayArtist))
                    .setPositiveButton(activity.getString(R.string.common_delete)) { _, _ -> userChoice.complete(true) }
                    .setNegativeButton(activity.getString(R.string.common_cancel)) { _, _ -> userChoice.complete(false) }
                    .setOnCancelListener { userChoice.complete(false) }
                    .setCancelable(true)
                    .create()
                    .show()
                userChoice.await()
            } catch (_: Exception) {
                false
            }
        }

    // endregion
}
