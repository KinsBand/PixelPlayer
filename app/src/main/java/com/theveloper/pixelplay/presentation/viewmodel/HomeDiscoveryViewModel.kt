package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackHistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The full play history behind Home's discovery shelves ("Right now", Back again, Fresh Finds).
 * The player's own history is capped for Recently played; these shelves need months and years of
 * plays (Forgotten, On this day), so this reads every recorded play and reloads whenever new
 * stats are recorded. Null until the first load finishes, so placeholders aren't cached.
 */
@HiltViewModel
class HomeDiscoveryViewModel @Inject constructor(
    private val statsRepository: PlaybackStatsRepository
) : ViewModel() {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val deepHistory: StateFlow<List<PlaybackHistoryEntry>?> = statsRepository.refreshFlow
        .mapLatest<Long, List<PlaybackHistoryEntry>?> { statsRepository.loadFullPlaybackHistory() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
