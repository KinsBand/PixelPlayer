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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import com.theveloper.pixelplay.data.search.SocialPlaylistSearch
import com.theveloper.pixelplay.data.youtube.searchIdentity
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
    /** Your Spotify / YouTube Music / Apple Music playlists and saved friend playlists. */
    private val connectedLibrary: com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository? = null,
    /** Friend names, avatars and public playlists. */
    private val friendActivity: com.theveloper.pixelplay.data.social.FriendActivityRepository? = null,
) {
    private data class SearchRequest(
        val query: String,
        val requestId: Long,
        val filter: SearchFilterType,
        val startedNanos: Long = System.nanoTime(),
    )

    /** "Show more" state for the song results of the current query. */
    data class SongPagingState(
        val hasMore: Boolean = false,
        val isLoading: Boolean = false,
        val failed: Boolean = false,
        /** Songs added by the most recent successful load (for the TalkBack announcement). */
        val lastLoadedCount: Int = 0,
        val pagesLoaded: Int = 0
    )

    // Search State
    private val _searchResults = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    val searchResults = _searchResults.asStateFlow()

    /** Results from the live search flow, before any "Show more" songs are appended. */
    private var baseResults: List<SearchResultItem> = emptyList()
    /** Songs appended by "Show more" for the current (query, filter). */
    private var extraSongs: List<SearchResultItem.SongItem> = emptyList()
    private val _songPaging = MutableStateFlow(SongPagingState())
    val songPaging = _songPaging.asStateFlow()
    private var loadMoreJob: Job? = null

    private fun publishResults() {
        val merged = if (extraSongs.isEmpty()) baseResults else {
            val baseIds = baseResults.mapNotNullTo(HashSet()) { (it as? SearchResultItem.SongItem)?.song?.id }
            val extras = extraSongs.filter { it.song.id !in baseIds }
            // Keep extra songs right after the last base song so they stay in the Songs section.
            val lastSong = baseResults.indexOfLast { it is SearchResultItem.SongItem }
            if (lastSong < 0) extras + baseResults
            else baseResults.subList(0, lastSong + 1) + extras + baseResults.subList(lastSong + 1, baseResults.size)
        }
        val immutable = merged.toImmutableList()
        if (_searchResults.value != immutable) _searchResults.value = immutable
    }

    private fun resetPaging() {
        loadMoreJob?.cancel()
        loadMoreJob = null
        extraSongs = emptyList()
        _songPaging.value = SongPagingState()
    }

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
                        baseResults = emptyList()
                        resetPaging()
                        if (_searchResults.value.isNotEmpty()) {
                            _searchResults.value = persistentListOf()
                        }
                        return@collectLatest
                    }

                    try {
                        var reportedFirstResults = false
                        val currentFilter = request.filter
                        combine(
                            musicRepository.searchAll(normalizedQuery, currentFilter),
                            socialResults(normalizedQuery, currentFilter)
                        ) { base, social ->
                            if (social.isEmpty()) base
                            else (base + social).distinctBy { it.searchIdentity() }
                        }.collect { resultsList ->
                            // Sort: prioritize Song/Album matches over Artist/Playlist matches
                            val sortedResults = resultsList.sortedWith(
                                compareBy { result ->
                                    when (result) {
                                        is SearchResultItem.SongItem -> 0
                                        is SearchResultItem.AlbumItem -> 1
                                        is SearchResultItem.ArtistItem -> 2
                                        is SearchResultItem.PlaylistItem -> 3
                                        is SearchResultItem.FriendItem -> 4
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
                            if (baseResults != immutableResults) {
                                baseResults = immutableResults
                                publishResults()
                                // Songs exist and nothing has been paged yet: offer "Show more".
                                val paging = _songPaging.value
                                if (paging.pagesLoaded == 0 && !paging.isLoading && pagingAllowed(request.filter)) {
                                    val hasSongs = immutableResults.any { it is SearchResultItem.SongItem }
                                    if (paging.hasMore != hasSongs) _songPaging.value = paging.copy(hasMore = hasSongs)
                                }
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
                            baseResults = emptyList()
                            resetPaging()
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

    /** Search is on screen: open the online connections while the user types. */
    fun onSearchScreenShown() {
        prewarmScheduler?.onSearchOpened()
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
            // New query or filter: pagination starts over.
            baseResults = emptyList()
            resetPaging()
            if (_searchResults.value.isNotEmpty()) {
                _searchResults.value = persistentListOf()
            }
        }

        searchRequests.tryEmit(SearchRequest(normalizedQuery, requestId, _selectedSearchFilter.value))
    }

    /**
     * Connected-service playlists, friends' playlists and friends by name. Local-only data,
     * so it updates instantly while typing; friend lists refresh as the snapshots change.
     */
    private fun socialResults(query: String, filter: SearchFilterType): Flow<List<SearchResultItem>> {
        val library = connectedLibrary
        if (library == null || (filter != SearchFilterType.ALL && filter != SearchFilterType.PLAYLISTS)) {
            return flowOf(emptyList())
        }
        val profiles = friendActivity?.profiles ?: flowOf(emptyMap())
        return combine(library.snapshot, profiles) { snapshot, friendProfiles ->
            SocialPlaylistSearch.search(query, snapshot, friendProfiles)
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    /**
     * Songs to play for a playlist found in search. Connected playlists come from the synced
     * snapshot; a friend's public playlist is saved first (the same as opening it from Friends).
     * Returns the playlist id to open plus its songs.
     */
    suspend fun resolveSearchPlaylist(playlist: com.theveloper.pixelplay.data.model.Playlist):
        Pair<String, List<com.theveloper.pixelplay.data.model.Song>> {
        val library = connectedLibrary ?: return playlist.id to emptyList()
        val publicRef = SocialPlaylistSearch.parseFriendPublicId(playlist.id)
        val id = if (publicRef != null) {
            val (source, remoteId) = publicRef
            val link = com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(source, remoteId)
                ?: error("This playlist can't be opened.")
            library.addFriendPlaylist(playlist.ownerName.ifBlank { "Friend" }, link, knownFriendId = playlist.friendId)
        } else playlist.id
        val songs = library.snapshot.value.playlists.find { it.id == id }?.songs.orEmpty()
        return id to songs
    }

    /** True for playlists that come from the connected-services snapshot (not the local DB). */
    fun isConnectedPlaylist(playlistId: String): Boolean =
        SocialPlaylistSearch.parseFriendPublicId(playlistId) != null ||
            connectedLibrary?.isConnected(playlistId) == true

    private fun pagingAllowed(filter: SearchFilterType) =
        filter == SearchFilterType.ALL || filter == SearchFilterType.SONGS

    /** "Show more" at the end of the song results: appends the next page of songs. */
    fun loadMoreSongs() {
        val request = searchRequests.replayCache.lastOrNull() ?: return
        val paging = _songPaging.value
        if (paging.isLoading || !paging.hasMore || request.query.isBlank() || !pagingAllowed(request.filter)) return
        val requestId = request.requestId
        _songPaging.value = paging.copy(isLoading = true, failed = false)
        loadMoreJob = scope?.launch {
            try {
                val shown = _searchResults.value.mapNotNullTo(HashSet()) { (it as? SearchResultItem.SongItem)?.song?.id }
                val page = musicRepository.loadMoreSearchSongs(
                    query = request.query,
                    shownSongIds = shown,
                    reset = paging.pagesLoaded == 0
                )
                if (requestId != latestSearchRequestId.get()) return@launch
                val added = page.songs.filter { it.id !in shown }.map { SearchResultItem.SongItem(it) }
                extraSongs = extraSongs + added
                publishResults()
                _songPaging.value = SongPagingState(
                    hasMore = page.hasMore && added.isNotEmpty(),
                    isLoading = false,
                    failed = false,
                    lastLoadedCount = added.size,
                    pagesLoaded = paging.pagesLoaded + 1
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Loading more songs failed")
                if (requestId == latestSearchRequestId.get()) {
                    _songPaging.value = _songPaging.value.copy(isLoading = false, failed = true)
                }
            }
        }
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
        loadMoreJob?.cancel()
        scope = null
    }
}
