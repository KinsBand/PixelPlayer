package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.radio.RadioHeardTrack
import com.theveloper.pixelplay.data.radio.RadioNowPlaying
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.NoLyricsFoundException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Lyrics for the song playing on the radio (the one the station names, or Listen found).
 *
 * - Follows [RadioNowPlaying.current]: each new song is looked up (library first, then online)
 *   and its lyrics fetched with the normal lyrics repository (so offline mode / the lyrics
 *   setting still apply).
 * - [position] is how far into that song the radio is: the playhead on the radio timeline minus
 *   where the song started there, so synced lyrics follow the radio (and a rewind).
 * - Its own lyrics search state, so searching from the radio lyrics view looks up the radio
 *   song, not the station.
 */
@Singleton
class RadioLyricsStateHolder @Inject constructor(
    private val radioNowPlaying: RadioNowPlaying,
    private val lyricsRepository: LyricsRepository,
) {
    data class State(
        val track: RadioHeardTrack? = null,
        /** The looked-up song (or the heard title as a song when it couldn't be found). */
        val song: Song? = null,
        val lyrics: Lyrics? = null,
        val isLoading: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _searchState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val searchState: StateFlow<LyricsSearchUiState> = _searchState.asStateFlow()

    /** How far into the current radio song playback is (ms), ticking while collected. */
    val position: StateFlow<Long> = flow {
        while (true) {
            emit(positionNow())
            delay(POSITION_TICK_MS)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(1_000), 0L)

    private var searchJob: Job? = null

    init {
        scope.launch {
            radioNowPlaying.current
                // The same entry with a new cover or end time: no new lookup.
                .distinctUntilChangedBy { it?.id }
                .collectLatest { track -> load(track) }
        }
    }

    private fun positionNow(): Long {
        val start = _state.value.track?.startPosMs ?: return 0L
        return (radioNowPlaying.timelineNowMs() - start).coerceAtLeast(0L)
    }

    private suspend fun load(track: RadioHeardTrack?) {
        _searchState.value = LyricsSearchUiState.Idle
        if (track == null) {
            _state.value = State()
            return
        }
        _state.value = State(track = track, song = track.toSong(), isLoading = true)
        try {
            val song = radioNowPlaying.findSong(track) ?: track.toSong()
            _state.value = _state.value.copy(song = song)
            val lyrics = withContext(Dispatchers.IO) {
                lyricsRepository.getLyrics(song, LyricsSourcePreference.API_FIRST)
            }
            _state.value = _state.value.copy(lyrics = lyrics, isLoading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Radio lyrics lookup failed")
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    /** "Search lyrics" for the radio song: every match to pick from. */
    fun search() {
        val song = _state.value.song ?: return
        runSearch { lyricsRepository.searchRemote(song) }
    }

    /** Search with an edited title / artist. */
    fun searchManually(title: String, artist: String?) {
        runSearch { lyricsRepository.searchRemoteByQuery(title, artist) }
    }

    fun pick(result: LyricsSearchResult) {
        _state.value = _state.value.copy(lyrics = result.lyrics, isLoading = false)
        _searchState.value = LyricsSearchUiState.Idle
    }

    fun dismissSearch() {
        searchJob?.cancel()
        _searchState.value = LyricsSearchUiState.Idle
    }

    private fun runSearch(block: suspend () -> Result<Pair<String, List<LyricsSearchResult>>>) {
        searchJob?.cancel()
        _searchState.value = LyricsSearchUiState.Loading
        searchJob = scope.launch {
            val result = withContext(Dispatchers.IO) { block() }
            _searchState.value = result.fold(
                onSuccess = { (query, results) ->
                    if (results.isEmpty()) LyricsSearchUiState.NotFound("No lyrics found for $query")
                    else LyricsSearchUiState.PickResult(query, results)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (error is NoLyricsFoundException) LyricsSearchUiState.NotFound("No lyrics found")
                    else LyricsSearchUiState.Error(error.message ?: "Lyrics search failed")
                }
            )
        }
    }

    companion object {
        private const val POSITION_TICK_MS = 100L

        /** For Compose code, which can't take constructor injection. */
        fun get(context: Context): RadioLyricsStateHolder =
            EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).radioLyricsStateHolder()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun radioLyricsStateHolder(): RadioLyricsStateHolder
    }
}
