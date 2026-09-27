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

data class SongVideoUiState(
    val songId: String? = null,
    val type: VideoType = VideoType.MUSIC_VIDEO,
    val loading: Boolean = false,
    val candidates: List<TrackVideo> = emptyList(),
    val selected: TrackVideo? = null,
    val error: String? = null,
)

@HiltViewModel
class SongVideoViewModel @Inject constructor(private val repository: VideoRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SongVideoUiState())
    val state = mutableState.asStateFlow()
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
            try { repository.searchVideosForTrack(song.displayArtist, song.title, state.value.type) }
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
                val results = repository.searchVideosForTrack(song.displayArtist, song.title, type, refresh)
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
