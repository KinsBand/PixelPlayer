package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.library.LibraryInsightsRepository
import com.theveloper.pixelplay.data.search.ArtistSearchSuggestionHub
import com.theveloper.pixelplay.data.search.SearchDiscovery
import com.theveloper.pixelplay.data.search.SearchDiscoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import timber.log.Timber
import javax.inject.Inject

/**
 * Search home's "Because you listened" cards. They rotate through your top artists every
 * [SearchDiscovery.ROTATION_PERIOD_MS] (two at a time), reshuffle their similar artists and
 * mood searches each rotation, and rebuild when your top artists change. Nothing runs while
 * Search isn't on screen (the state is only shared while collected).
 */
@HiltViewModel
class SearchDiscoveryViewModel @Inject constructor(
    insightsRepository: LibraryInsightsRepository,
    private val repository: SearchDiscoveryRepository
) : ViewModel() {

    data class UiState(
        val hubs: List<ArtistSearchSuggestionHub> = emptyList(),
        /** Your listening insights or the first cards are still being built. */
        val loading: Boolean = true
    )

    /** The current rotation; ticks at each boundary while collected. */
    private val slots: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(SearchDiscovery.rotationSlot(now))
            delay(SearchDiscovery.msUntilNextSlot(now) + 1_000L)
        }
    }.distinctUntilChanged()

    /** Top artists by recent listening; null until insights have been built once. Keyed on ids. */
    private val pool: Flow<List<LibraryInsightsRepository.ArtistCard>?> = insightsRepository.insights
        .map { insights ->
            if (insights.computedAt == 0L && insights.topArtists.isEmpty()) null
            else insights.topArtists.take(SearchDiscovery.ANCHOR_POOL)
        }
        .distinctUntilChanged { old, new -> old?.map { it.id } == new?.map { it.id } }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState> = combine(pool, slots) { artists, slot -> artists to slot }
        .transformLatest { (artists, slot) ->
            if (artists == null) {
                emit(UiState(loading = true))
                return@transformLatest
            }
            if (artists.isEmpty()) {
                emit(UiState(emptyList(), loading = false))
                return@transformLatest
            }
            val anchors = SearchDiscovery.anchorsFor(artists, slot, count = 2)
            val keepIds = artists.mapTo(HashSet()) { it.id }
            val exclude = artists.mapTo(HashSet()) { it.name }
            try {
                // Instant paint from what's cached, then the fetched version.
                repository.hubs(anchors, keepIds, exclude, slot, allowNetwork = false)
                    ?.let { emit(UiState(it, loading = false)) }
                val full = repository.hubs(anchors, keepIds, exclude, slot, allowNetwork = true).orEmpty()
                emit(UiState(full, loading = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Search discovery cards failed")
                emit(UiState(emptyList(), loading = false))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}
