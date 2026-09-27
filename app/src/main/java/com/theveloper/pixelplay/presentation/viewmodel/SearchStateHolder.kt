package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages search state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Search query execution
 * - Search filter management
 * - Search history CRUD operations
 */
@Singleton
class SearchStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val prewarmScheduler: com.theveloper.pixelplay.data.stream.StreamPrewarmScheduler? = null,
) {
    private data class SearchRequest(
        val query: String,
        val requestId: Long,
        val filter: SearchFilterType,
        val startedNanos: Long = System.nanoTime(),
    )

    // Search State
    private val _searchResults = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    val searchResults = _searchResults.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter = _selectedSearchFilter.asStateFlow()

    private val _searchHistory = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    val searchHistory = _searchHistory.asStateFlow()

    private val _searchError = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val searchError: SharedFlow<String> = _searchError.asSharedFlow()

    private val searchRequests = MutableSharedFlow<SearchRequest>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val latestSearchRequestId = AtomicLong(0L)

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    /**
     * Initialize with ViewModel scope.
     */
    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        observeSearchRequests()
    }

    private fun observeSearchRequests() {
        searchJob?.cancel()
        searchJob = scope?.launch {
            searchRequests
                .collectLatest { request ->
                    val normalizedQuery = request.query

                    if (normalizedQuery.isBlank()) {
                        if (_searchResults.value.isNotEmpty()) {
                            _searchResults.value = persistentListOf()
                        }
                        return@collectLatest
                    }

                    try {
                        var reportedFirstResults = false
                        val currentFilter = request.filter
                        musicRepository.searchAll(normalizedQuery, currentFilter).collect { resultsList ->
                            // Sort: prioritize Song/Album matches over Artist/Playlist matches
                            val sortedResults = resultsList.sortedWith(
                                compareBy { result ->
                                    when (result) {
                                        is SearchResultItem.SongItem -> 0
                                        is SearchResultItem.AlbumItem -> 1
                                        is SearchResultItem.ArtistItem -> 2
                                        is SearchResultItem.PlaylistItem -> 3
                                    }
                                }
                            )

                            if (request.requestId != latestSearchRequestId.get()) {
                                return@collect
                            }

                            val immutableResults = sortedResults.toImmutableList()
                            if (!reportedFirstResults && immutableResults.isNotEmpty()) {
                                reportedFirstResults = true
                                Timber.tag("StreamingLatency").d("search_first_results_ms=%d", (System.nanoTime() - request.startedNanos) / 1_000_000)
                            }
                            if (_searchResults.value != immutableResults) {
                                _searchResults.value = immutableResults
                                // The top online song results are the likeliest next tap.
                                prewarmScheduler?.onSearchResults(
                                    immutableResults.mapNotNull { (it as? SearchResultItem.SongItem)?.song?.streamVideoId() }
                                )
                            }
                        }
                    } catch (_: CancellationException) {
                        // Superseded by a newer query; ignore.
                    } catch (e: Exception) {
                        if (request.requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "Error performing search for query: $normalizedQuery")
                            _searchResults.value = persistentListOf()
                            val errorMsg = when {
                                e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "No internet connection"
                                e.message?.contains("timeout", ignoreCase = true) == true -> "Search timed out. Please try again"
                                else -> "Online search failed: ${e.message ?: "Unknown error"}"
                            }
                            _searchError.tryEmit(errorMsg)
                        }
                    }
                }
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        if (_selectedSearchFilter.value == filterType) return
        _selectedSearchFilter.value = filterType
        searchRequests.replayCache.lastOrNull()?.query?.let { performSearch(it) }
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    musicRepository.getRecentSearchHistory(limit)
                }
                _searchHistory.value = history.toImmutableList()
            } catch (e: Exception) {
                Timber.e(e, "Error loading search history")
            }
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        musicRepository.addSearchHistoryItem(query)
                    }
                    loadSearchHistory()
                } catch (e: Exception) {
                    Timber.e(e, "Error adding search history item")
                }
            }
        }
    }

    /** A song row is being pressed: start its stream work before the tap lands. */
    fun onSongPressed(song: com.theveloper.pixelplay.data.model.Song) {
        song.streamVideoId()?.let { prewarmScheduler?.onPress(it) }
    }

    fun onSongPressCancelled(song: com.theveloper.pixelplay.data.model.Song) {
        song.streamVideoId()?.let { prewarmScheduler?.onPressCancelled(it) }
    }

    private fun com.theveloper.pixelplay.data.model.Song.streamVideoId(): String? =
        if (contentUriString.startsWith("youtube://")) youtubeId ?: contentUriString.removePrefix("youtube://") else null

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()
        val previous = searchRequests.replayCache.lastOrNull()
        // The screen re-requests on every (query, filter) change and the filter chip also
        // re-requests; don't restart an identical search that already has results.
        if (normalizedQuery.isNotBlank() && previous?.query == normalizedQuery &&
            previous.filter == _selectedSearchFilter.value && _searchResults.value.isNotEmpty()) return
        val requestId = latestSearchRequestId.incrementAndGet()
        if (normalizedQuery.isBlank() || previous?.query != normalizedQuery || previous.filter != _selectedSearchFilter.value) {
            if (_searchResults.value.isNotEmpty()) {
                _searchResults.value = persistentListOf()
            }
        }

        searchRequests.tryEmit(SearchRequest(normalizedQuery, requestId, _selectedSearchFilter.value))
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.deleteSearchHistoryItemByQuery(query)
                }
                loadSearchHistory()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting search history item")
            }
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.clearSearchHistory()
                }
                _searchHistory.value = persistentListOf()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing search history")
            }
        }
    }

    fun onCleared() {
        searchJob?.cancel()
        scope = null
    }
}
