package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.youtube.DownloadCoordinator
import com.theveloper.pixelplay.data.youtube.DownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Thin UI bridge to [DownloadCoordinator] for the player title badge and Settings. */
@HiltViewModel
class PlayerDownloadViewModel @Inject constructor(
    private val coordinator: DownloadCoordinator,
) : ViewModel() {
    val downloadedIds: StateFlow<Set<String>> = coordinator.downloadedIds
    val progress: StateFlow<Map<String, DownloadProgress>> = coordinator.progress
    val bulkState: StateFlow<DownloadCoordinator.BulkDownloadState?> = coordinator.bulkState

    fun isOnlineSong(song: Song): Boolean = coordinator.isOnlineSong(song)
    fun download(song: Song) = coordinator.download(song)

    suspend fun likedSongsToDownloadCount(): Int = coordinator.likedSongsToDownload().size
    suspend fun likedSongsDownloadedCount(): Int = coordinator.likedSongsDownloadedCount()
    fun downloadAllLiked() = coordinator.requestDownloadAllLiked()
    fun cancelBulk() = coordinator.cancelBulk()

    val wifiOnly: StateFlow<Boolean> = coordinator.wifiOnly
    val waitingForWifi: StateFlow<Boolean> = coordinator.waitingForWifi
    fun setWifiOnly(enabled: Boolean) = coordinator.setWifiOnly(enabled)
    fun cancelWaitingForWifi() = coordinator.cancelWaitingForWifi()
}
