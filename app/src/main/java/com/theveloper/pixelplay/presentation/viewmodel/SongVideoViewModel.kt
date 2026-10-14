package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackVideo
import com.theveloper.pixelplay.data.model.VideoType
import com.theveloper.pixelplay.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.net.ConnectivityManager
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.youtube.VideoRendition
import com.theveloper.pixelplay.data.youtube.VideoStreams
import com.theveloper.pixelplay.data.youtube.YouTubeChunkedDataSource
import com.theveloper.pixelplay.data.youtube.YouTubeVideoStreamResolver
import com.theveloper.pixelplay.data.youtube.pickVideoRendition
import dagger.hilt.android.qualifiers.ApplicationContext

data class SongVideoUiState(
    val songId: String? = null,
    val type: VideoType = VideoType.MUSIC_VIDEO,
    val loading: Boolean = false,
    val candidates: List<TrackVideo> = emptyList(),
    val selected: TrackVideo? = null,
    val error: String? = null,
)

/**
 * Which player plays the selected video, and at what quality. [streams] non-null = ExoPlayer
 * with a real quality list; [fallback] = the YouTube embed (extraction failed or the stream
 * errored), where quality can't change and the button is hidden.
 */
data class VideoQualityState(
    val videoId: String? = null,
    val resolving: Boolean = false,
    val streams: VideoStreams? = null,
    val fallback: Boolean = false,
    /** This session's pick; null = Auto (or the starred quality when there is one). */
    val pickedHeight: Int? = null,
    /** True when the user explicitly picked Auto, so the star doesn't override it. */
    val pickedAuto: Boolean = false,
    val starredHeight: Int? = null,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
class SongVideoViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val streamResolver: YouTubeVideoStreamResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SongVideoUiState())
    val state = mutableState.asStateFlow()

    private val mutableQuality = MutableStateFlow(VideoQualityState())
    val quality = mutableQuality.asStateFlow()
    private var resolveJob: Job? = null

    /** Feeds this video's streams to ExoPlayer. */
    val videoDataSourceFactory = YouTubeChunkedDataSource.Factory(streamResolver.okHttpClient)

    init {
        viewModelScope.launch {
            userPreferencesRepository.preferredVideoQualityFlow.collect { starred ->
                mutableQuality.value = mutableQuality.value.copy(starredHeight = starred)
            }
        }
    }

    /** Looks up [videoId]'s real streams once; falls back to the embed when that fails. */
    fun resolveStreams(videoId: String) {
        val current = mutableQuality.value
        if (current.videoId == videoId && (current.resolving || current.streams != null || current.fallback)) return
        resolveJob?.cancel()
        // A new video: the session pick resets, so the starred quality applies again.
        mutableQuality.value = current.copy(
            videoId = videoId, resolving = true, streams = null, fallback = false,
            pickedHeight = null, pickedAuto = false
        )
        resolveJob = viewModelScope.launch {
            val streams = streamResolver.resolve(videoId)
            if (mutableQuality.value.videoId != videoId) return@launch
            mutableQuality.value = mutableQuality.value.copy(
                resolving = false, streams = streams, fallback = streams == null
            )
        }
    }

    /** ExoPlayer couldn't play the stream (expired URL, network): use the embed instead. */
    fun exoPlaybackFailed(videoId: String) {
        streamResolver.invalidate(videoId)
        if (mutableQuality.value.videoId != videoId) return
        mutableQuality.value = mutableQuality.value.copy(resolving = false, streams = null, fallback = true)
    }

    /** Picks a quality for now; null = Auto. */
    fun pickQuality(height: Int?) {
        mutableQuality.value = mutableQuality.value.copy(pickedHeight = height, pickedAuto = height == null)
    }

    /** Stars [height] (used every time a video loads), or clears the star if it's already starred. */
    fun toggleStar(height: Int) {
        val starred = mutableQuality.value.starredHeight
        val next = if (starred == height) null else height
        if (next != null) pickQuality(next)
        viewModelScope.launch { userPreferencesRepository.setPreferredVideoQuality(next) }
    }

    /** The quality to play right now (pick, then star, then Auto by network). */
    fun renditionFor(q: VideoQualityState): VideoRendition? {
        val streams = q.streams ?: return null
        val requested = when {
            q.pickedAuto -> null
            q.pickedHeight != null -> q.pickedHeight
            else -> q.starredHeight
        }
        return pickVideoRendition(streams, requested, isUnmetered())
    }

    private fun isUnmetered(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return !cm.isActiveNetworkMetered
    }
    private var searchJob: Job? = null
    private var generation = 0
    private var track: Song? = null
    private val failedIds = mutableSetOf<String>()

    /**
     * Warms the repository cache for [song]'s default preset so tapping Video is instant.
     * Never touches UI state; failures are ignored.
     */
    fun prefetch(song: Song) {
        if (song.title.isBlank()) return
        val current = state.value
        if (current.songId == song.id && (current.loading || current.selected != null)) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            try { repository.searchVideosForTrack(song.displayArtist, song.title, state.value.type, durationMs = song.duration) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
    }
    private var prefetchJob: Job? = null

    fun search(song: Song, type: VideoType = state.value.type, refresh: Boolean = false) {
        val current = state.value
        // Re-entering Video for the same song keeps the loaded (or loading) result instead of
        // resetting to a spinner and searching again.
        if (!refresh && current.songId == song.id && current.type == type &&
            (current.loading || current.selected != null)) {
            track = song
            return
        }
        track = song
        searchJob?.cancel()
        val request = ++generation
        failedIds.clear()
        mutableState.value = SongVideoUiState(songId = song.id, type = type, loading = true)
        searchJob = viewModelScope.launch {
            try {
                val results = repository.searchVideosForTrack(song.displayArtist, song.title, type, refresh, durationMs = song.duration)
                if (request != generation) return@launch
                mutableState.value = state.value.copy(loading = false, candidates = results,
                    selected = results.firstOrNull(),
                    error = if (results.isEmpty()) "No matching versions found. Try another preset." else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == generation) mutableState.value = state.value.copy(loading = false,
                    error = if (e is java.io.IOException) "Unable to reach YouTube. Check your connection and retry."
                    else e.message ?: "Could not find videos. Try again.")
            }
        }
    }

    fun select(video: TrackVideo) {
        if (video !in state.value.candidates) return
        failedIds.remove(video.id)
        mutableState.value = state.value.copy(selected = video, error = null)
        track?.let { repository.rememberChoice(it.displayArtist, it.title, state.value.type, video.id) }
    }

    fun playbackFailed(id: String, message: String, tryNext: Boolean) {
        if (state.value.selected?.id != id) return
        failedIds.add(id)
        val next = if (tryNext) state.value.candidates.firstOrNull { it.id !in failedIds } else null
        mutableState.value = state.value.copy(selected = next, error = if (next == null) message else null)
    }

    fun cancelSearch() {
        generation++
        searchJob?.cancel()
        // A search cancelled mid-flight must not leave "loading" behind: search() would then
        // treat it as in progress and never start a new one.
        if (state.value.loading) mutableState.value = SongVideoUiState()
    }
}
