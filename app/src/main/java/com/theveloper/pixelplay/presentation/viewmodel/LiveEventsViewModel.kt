package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.library.LibraryInsightsRepository
import com.theveloper.pixelplay.data.network.NetworkAccessPolicy
import com.theveloper.pixelplay.data.network.NetworkDecision
import com.theveloper.pixelplay.data.network.NetworkPurpose
import com.theveloper.pixelplay.data.tour.ArtistShow
import com.theveloper.pixelplay.data.tour.TourRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** One show on the Live events page, with whose show it is. */
@Immutable
data class LiveEvent(
    val show: ArtistShow,
    val artistId: Long,
    val artistName: String,
    val artistImageUrl: String?,
)

@Immutable
data class LiveEventsUiState(
    val loading: Boolean = true,
    val events: List<LiveEvent> = emptyList(),
    /** No tour-dates key in this build. */
    val notConfigured: Boolean = false,
    /** Offline mode or online extras turned off. */
    val offline: Boolean = false,
    /** None of your listening yet, so no top artists to look up. */
    val noArtists: Boolean = false,
)

/**
 * Search → Live events: upcoming shows for the artists you play most (Library insights' top
 * artists, last 30 days), from the same tour lookup the artist page uses (cached a day per
 * artist), merged and sorted by date.
 */
@HiltViewModel
class LiveEventsViewModel @Inject constructor(
    private val insights: LibraryInsightsRepository,
    private val tours: TourRepository,
    private val networkPolicy: NetworkAccessPolicy,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveEventsUiState())
    val state: StateFlow<LiveEventsUiState> = _state.asStateFlow()

    init { load(force = false) }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        if (!tours.isConfigured) {
            _state.value = LiveEventsUiState(loading = false, notConfigured = true)
            return@launch
        }
        if (networkPolicy.getDecision(NetworkPurpose.Enrichment) != NetworkDecision.Allowed) {
            _state.value = LiveEventsUiState(loading = false, offline = true)
            return@launch
        }
        insights.ensureFresh()
        // Insights fill in shortly after a cold start; wait a moment for the top artists.
        val artists = insights.insights.value.topArtists.ifEmpty {
            withTimeoutOrNull(INSIGHTS_WAIT_MS) {
                insights.insights.first { it.topArtists.isNotEmpty() }.topArtists
            }.orEmpty()
        }
        if (artists.isEmpty()) {
            _state.value = LiveEventsUiState(loading = false, noArtists = true)
            return@launch
        }
        val gate = Semaphore(CONCURRENCY)
        val events = coroutineScope {
            artists.take(MAX_ARTISTS).map { artist ->
                async {
                    gate.withPermit {
                        val tour = try {
                            tours.getTour(artist.name, forceRefresh = force)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                        tour?.upcoming.orEmpty().map { LiveEvent(it, artist.id, artist.name, artist.imageUrl) }
                    }
                }
            }.awaitAll().flatten()
        }.sortedWith(compareBy({ it.show.date }, { it.show.time ?: "" }, { it.artistName }))
        _state.value = LiveEventsUiState(loading = false, events = events)
    }

    private companion object {
        const val MAX_ARTISTS = 12
        const val CONCURRENCY = 3
        const val INSIGHTS_WAIT_MS = 4_000L
    }
}
