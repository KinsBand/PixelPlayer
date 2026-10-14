package com.theveloper.pixelplay.data.offline

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackSource
import com.theveloper.pixelplay.data.stream.YouTubeStreamProxy
import com.theveloper.pixelplay.data.youtube.DownloadCoordinator
import com.theveloper.pixelplay.presentation.viewmodel.ConnectivityStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What can still play with no network: downloaded songs (by song id) and songs whose whole
 * stream is cached (by video id). The cache set is re-read on every connectivity change, so
 * it's fresh at the moment the device goes offline, which is the only time it matters.
 */
@Singleton
class OfflineAvailability @Inject constructor(
    downloadCoordinator: DownloadCoordinator,
    private val youTubeStreamProxy: YouTubeStreamProxy,
    connectivityStateHolder: ConnectivityStateHolder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cachedVideoIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        // A StateFlow only emits real changes already.
        connectivityStateHolder.isOnline
            .onEach { cachedVideoIds.value = runCatching { youTubeStreamProxy.fullyCachedIds() }.getOrDefault(emptySet()) }
            .launchIn(scope)
    }

    /** Song ids that are downloaded plus video ids that are fully cached. */
    val playableIds: StateFlow<Set<String>> =
        combine(downloadCoordinator.downloadedIds, cachedVideoIds) { downloaded, cached -> downloaded + cached }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())
}

/** True for songs that stream from the network (YouTube / Spotify / Drive / web). */
fun Song.needsNetwork(): Boolean =
    youtubeId != null || id.startsWith("yt_") || id.startsWith("spotify_") ||
        contentUriString.startsWith("youtube://") || contentUriString.startsWith("spotify://") ||
        source == TrackSource.YOUTUBE_MUSIC || source == TrackSource.GDRIVE || source == TrackSource.OTHER

/** Whether [song] plays right now: always when online; offline only if it's on the device. */
fun Song.isPlayableOffline(playableIds: Set<String>): Boolean {
    if (!needsNetwork() || isDownloaded) return true
    if (id in playableIds) return true
    val videoId = youtubeId ?: id.takeIf { it.startsWith("yt_") }?.removePrefix("yt_")
    return videoId != null && videoId in playableIds
}
