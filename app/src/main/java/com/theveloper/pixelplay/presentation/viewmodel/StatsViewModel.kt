package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository,
    private val colorSchemeProcessor: ColorSchemeProcessor
) : ViewModel() {

    data class StatsUiState(
        val selectedRange: StatsTimeRange = StatsTimeRange.WEEK,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val summary: PlaybackStatsSummary? = null,
        val availableRanges: List<StatsTimeRange> = StatsTimeRange.entries,
        /** Inclusive dates picked with "Custom"; when set the summary is a custom range. */
        val customRange: Pair<LocalDate, LocalDate>? = null
    )

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _weeklyOverview = MutableStateFlow<PlaybackStatsSummary?>(null)
    val weeklyOverview: StateFlow<PlaybackStatsSummary?> = _weeklyOverview.asStateFlow()

    private val _homeOverview = MutableStateFlow<PlaybackStatsSummary?>(null)
    val homeOverview: StateFlow<PlaybackStatsSummary?> = _homeOverview.asStateFlow()

    @Volatile
    private var cachedSongs: List<Song>? = null

    init {
        observeStatsRefreshFlow()
        refreshRange(
            range = StatsTimeRange.WEEK,
            showLoading = true,
            updateWeeklyOverview = true
        )
        refreshHomeOverview()
    }

    fun onRangeSelected(range: StatsTimeRange) {
        val state = _uiState.value
        if (range == state.selectedRange && state.customRange == null && !state.isLoading) {
            return
        }
        _uiState.update { it.copy(customRange = null) }
        refreshRange(
            range = range,
            showLoading = true,
            updateWeeklyOverview = range == StatsTimeRange.WEEK
        )
    }

    /** Shows stats for [start]..[end] (inclusive days). */
    fun onCustomRangeSelected(start: LocalDate, end: LocalDate) {
        val (from, to) = if (end.isBefore(start)) end to start else start to end
        _uiState.update { it.copy(customRange = from to to) }
        refreshRange(range = StatsTimeRange.ALL, showLoading = true)
    }

    /** Route for an artist row: the local artist page when the name matches one, else the online profile. */
    suspend fun artistRoute(name: String): String {
        val id = runCatching { musicRepository.getArtistIdByName(name) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
        return if (id != null) Screen.ArtistDetail.createRoute(id) else Screen.ArtistDetail.createRouteForName(name)
    }

    /** Album-art colours for share cards (cached by [ColorSchemeProcessor]). */
    suspend fun shareColorScheme(albumArtUri: String): ColorSchemePair? = withContext(Dispatchers.IO) {
        runCatching { colorSchemeProcessor.getPreviewColorScheme(albumArtUri, AlbumArtPaletteStyle.default) }
            .onFailure { if (it is CancellationException) throw it else Timber.w(it, "Share colours failed") }
            .getOrNull()
    }

    /** Library songs for [ids], in the given order (used by "Play your new finds"). */
    suspend fun songsForIds(ids: List<String>): List<Song> = withContext(Dispatchers.IO) {
        val byId = loadSongs().associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }

    fun refreshWeeklyOverview() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    playbackStatsRepository.loadSummary(StatsTimeRange.WEEK, songs)
                }
            }.onSuccess { summary ->
                _weeklyOverview.value = summary
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to load weekly stats overview")
                _weeklyOverview.value = null
            }
        }
    }

    fun refreshHomeOverview() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    for (range in HomeOverviewRanges) {
                        val summary = playbackStatsRepository.loadSummary(range, songs)
                        if (summary.hasListeningActivity()) {
                            return@withContext summary
                        }
                    }
                    null
                }
            }.onSuccess { summary ->
                _homeOverview.value = summary
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to load home stats overview")
                _homeOverview.value = null
            }
        }
    }

    private fun refreshRange(
        range: StatsTimeRange,
        showLoading: Boolean = true,
        updateWeeklyOverview: Boolean = false
    ) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, isRefreshing = false, selectedRange = range) }
            } else {
                _uiState.update { it.copy(isRefreshing = true, selectedRange = range) }
            }
            val customBounds = _uiState.value.customRange?.let { (start, end) -> customBoundsFor(start, end) }
            val summary = runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    playbackStatsRepository.loadSummary(range, songs, customBounds = customBounds)
                }
            }
            summary.getOrNull()?.let { loaded ->
                if (updateWeeklyOverview && customBounds == null) {
                    _weeklyOverview.value = loaded
                }
            }
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    summary = summary.getOrNull(),
                    selectedRange = range
                )
            }
            summary.exceptionOrNull()?.let { Timber.e(it, "Failed to load stats for range %s", range) }
        }
    }

    private fun observeStatsRefreshFlow() {
        viewModelScope.launch {
            playbackStatsRepository.refreshFlow
                .drop(1)
                .collectLatest {
                    val selectedRange = _uiState.value.selectedRange
                    refreshRange(
                        range = selectedRange,
                        showLoading = false,
                        updateWeeklyOverview = selectedRange == StatsTimeRange.WEEK
                    )
                    if (selectedRange != StatsTimeRange.WEEK) {
                        refreshWeeklyOverview()
                    }
                    refreshHomeOverview()
                }
        }
    }

    fun requestStatsRefresh() {
        playbackStatsRepository.requestRefresh()
    }

    fun forceRegenerateStats() {
        cachedSongs = null
        playbackStatsRepository.requestRefresh()
    }

    private suspend fun loadSongs(): List<Song> {
        cachedSongs?.let { existing ->
            if (existing.isNotEmpty()) return existing
        }
        val songs = musicRepository.getAllSongsOnce()
        cachedSongs = songs
        return songs
    }

    private fun customBoundsFor(start: LocalDate, end: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val endMillis = minOf(endOfDay, System.currentTimeMillis()).coerceAtLeast(startMillis)
        return startMillis to endMillis
    }

    private fun PlaybackStatsSummary.hasListeningActivity(): Boolean {
        return totalDurationMs > 0L ||
            totalPlayCount > 0 ||
            uniqueSongs > 0 ||
            activeDays > 0 ||
            totalSessions > 0
    }

    private companion object {
        val HomeOverviewRanges = listOf(
            StatsTimeRange.WEEK,
            StatsTimeRange.MONTH,
            StatsTimeRange.YEAR,
            StatsTimeRange.ALL
        )
    }
}
