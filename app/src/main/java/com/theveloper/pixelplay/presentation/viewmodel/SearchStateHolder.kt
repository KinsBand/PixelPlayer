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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import com.theveloper.pixelplay.data.search.SocialPlaylistSearch
import com.theveloper.pixelplay.data.search.SearchVersionGrouping
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    /** Online autocomplete (YouTube Music's search suggestions) while typing. */
    private val youTubeRepository: com.theveloper.pixelplay.data.youtube.YouTubeRepository? = null,
    /** Spotify people search and who you follow, for the Profiles filter. */
    private val socialGraph: com.theveloper.pixelplay.data.social.FriendSocialGraph? = null,
) {
    /** Typed text -> online autocomplete suggestions for it ("deftons" -> "deftones", ...). */
    data class QuerySuggestions(val query: String = "", val suggestions: List<String> = emptyList())
    private val _querySuggestions = MutableStateFlow(QuerySuggestions())
    val querySuggestions = _querySuggestions.asStateFlow()
    private val suggestionInputs = MutableStateFlow("")
    private var suggestionJob: Job? = null

    private data class SearchRequest(
        val query: String,
        val requestId: Long,
        val filter: SearchFilterType,
        val startedNanos: Long = System.nanoTime(),
    )

    /**
     * Paging state for the current query's results (songs on All / Songs, otherwise the filter's
     * own kind). The results list loads the next page by itself near the end.
     */
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

    /** Results from the live search flow, before any further pages are appended. */
    private var baseResults: List<SearchResultItem> = emptyList()
    /** Items appended by further pages for the current (query, filter). */
    private var extraItems: List<SearchResultItem> = emptyList()
    /** Profiles filter: how many people the online search has returned (the next page's offset). */
    @Volatile private var profileOffset = 0
    /** Profiles filter: Spotify said there's another page after the first. */
    @Volatile private var profileFirstPageHasMore = false
    private val _songPaging = MutableStateFlow(SongPagingState())
    val songPaging = _songPaging.asStateFlow()
    private var loadMoreJob: Job? = null

    /**
     * True from a new query (or filter) until its first results arrive, so the screen shows
     * skeleton rows instead of "No results" while the search is still running. Falls back to
     * false after [SEARCHING_TIMEOUT_MS] (the result flows never complete on their own).
     */
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    /** Last ungrouped results (base + pages): what a filter switch shows instantly. */
    private var lastRawResults: List<SearchResultItem> = emptyList()
    /** Bumped per publish, so a slower grouping pass can't overwrite a newer one. */
    private var publishGeneration = 0L

    /**
     * Publishes base + extra pages, with each song's other versions folded under it
     * ([SearchVersionGrouping]). Grouping runs off the main thread.
     */
    private suspend fun publishResults() {
        val merged = if (extraItems.isEmpty()) baseResults else {
            val baseIds = baseResults.mapTo(HashSet()) { it.searchIdentity() }
            val extras = extraItems.filter { it.searchIdentity() !in baseIds }
            // Each kind's extra items go right after the last base item of that kind, so they
            // stay in their section (songs after songs, albums after albums...).
            val out = baseResults.toMutableList()
            extras.groupBy { it::class }.forEach { (kind, group) ->
                val last = out.indexOfLast { it::class == kind }
                if (last < 0) out.addAll(group) else out.addAll(last + 1, group)
            }
            out
        }
        lastRawResults = merged
        val generation = ++publishGeneration
        val query = searchRequests.replayCache.lastOrNull()?.query.orEmpty()
        val grouped = withContext(Dispatchers.Default) {
            try {
                SearchVersionGrouping.group(merged, query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never lose results over a grouping bug: show them ungrouped.
                Timber.w(e, "Version grouping failed")
                merged
            }
        }
        if (generation != publishGeneration) return
        val immutable = grouped.toImmutableList()
        if (_searchResults.value != immutable) _searchResults.value = immutable
    }

    /**
     * What [filter] can show straight away from the results already on screen (a filter
     * switch for the same query), until its own search answers.
     */
    private fun seedFor(filter: SearchFilterType, from: List<SearchResultItem>): List<SearchResultItem> = from.filter { item ->
        when (filter) {
            SearchFilterType.ALL -> item !is SearchResultItem.ProfileItem
            SearchFilterType.SONGS -> item is SearchResultItem.SongItem
            SearchFilterType.ALBUMS -> item is SearchResultItem.AlbumItem
            SearchFilterType.ARTISTS -> item is SearchResultItem.ArtistItem
            SearchFilterType.PLAYLISTS -> item is SearchResultItem.PlaylistItem || item is SearchResultItem.FriendItem
            SearchFilterType.PROFILES -> item is SearchResultItem.ProfileItem
        }
    }
    /** Seed for the request in flight; merged under its results so the list never shrinks back. */
    private var seedItems: List<SearchResultItem> = emptyList()

    private fun resetPaging() {
        loadMoreJob?.cancel()
        loadMoreJob = null
        extraItems = emptyList()
        profileOffset = 0
        profileFirstPageHasMore = false
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
        observeSuggestionInputs()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeSuggestionInputs() {
        suggestionJob?.cancel()
        val repo = youTubeRepository ?: return
        suggestionJob = scope?.launch {
            suggestionInputs
                .debounce(120)
                .distinctUntilChanged()
                .collectLatest { input ->
                    if (input.isBlank()) {
                        _querySuggestions.value = QuerySuggestions()
                        return@collectLatest
                    }
                    val online = runCatching { repo.searchSuggestions(input) }.getOrDefault(emptyList())
                    if (suggestionInputs.value.trim() == input) {
                        _querySuggestions.value = QuerySuggestions(input, online)
                    }
                }
        }
    }

    private fun observeSearchRequests() {
        searchJob?.cancel()
        searchJob = scope?.launch {
            searchRequests
                .collectLatest { request ->
                    val normalizedQuery = request.query

                    if (normalizedQuery.isBlank()) {
                        baseResults = emptyList()
                        seedItems = emptyList()
                        resetPaging()
                        _isSearching.value = false
                        if (_searchResults.value.isNotEmpty()) {
                            _searchResults.value = persistentListOf()
                        }
                        return@collectLatest
                    }

                    try {
                        var reportedFirstResults = false
                        val currentFilter = request.filter
                        val seed = seedItems
                        coroutineScope {
                            // The result flows never complete; stop showing skeletons after a while.
                            val searchingTimeout = launch {
                                delay(SEARCHING_TIMEOUT_MS)
                                if (request.requestId == latestSearchRequestId.get()) _isSearching.value = false
                            }
                            combine(
                                musicRepository.searchAll(normalizedQuery, currentFilter),
                                socialResults(normalizedQuery, currentFilter),
                                profileResults(normalizedQuery, currentFilter)
                            ) { base, social, profiles ->
                                if (social.isEmpty() && profiles.isEmpty()) base
                                else (base + social + profiles).distinctBy { it.searchIdentity() }
                            }.collect { fresh ->
                                // A filter switch showed what was already loaded; keep those under
                                // the new results until this request is replaced.
                                val resultsList = if (seed.isEmpty()) fresh else {
                                    val ids = fresh.mapTo(HashSet()) { it.searchIdentity() }
                                    fresh + seed.filter { it.searchIdentity() !in ids }
                                }
                                // Sort: prioritize Song/Album matches over Artist/Playlist matches
                                val sortedResults = resultsList.sortedWith(
                                    compareBy { result ->
                                        when (result) {
                                            is SearchResultItem.SongItem -> 0
                                            is SearchResultItem.AlbumItem -> 1
                                            is SearchResultItem.ArtistItem -> 2
                                            is SearchResultItem.PlaylistItem -> 3
                                            is SearchResultItem.FriendItem -> 4
                                            is SearchResultItem.ProfileItem -> 5
                                        }
                                    }
                                )

                                if (request.requestId != latestSearchRequestId.get()) {
                                    return@collect
                                }

                                val immutableResults = sortedResults.toImmutableList()
                                if (fresh.isNotEmpty() && _isSearching.value) {
                                    _isSearching.value = false
                                    searchingTimeout.cancel()
                                }
                                if (!reportedFirstResults && immutableResults.isNotEmpty()) {
                                    reportedFirstResults = true
                                    Timber.tag("StreamingLatency").d("search_first_results_ms=%d", (System.nanoTime() - request.startedNanos) / 1_000_000)
                                }
                                if (baseResults != immutableResults) {
                                    baseResults = immutableResults
                                    publishResults()
                                    // Results of the paged kind exist and nothing has been paged yet:
                                    // more can load as the list nears its end.
                                    val paging = _songPaging.value
                                    if (paging.pagesLoaded == 0 && !paging.isLoading && pagingAllowed(request.filter)) {
                                        val canPage = firstPageCanPage(request.filter, immutableResults)
                                        if (paging.hasMore != canPage) _songPaging.value = paging.copy(hasMore = canPage)
                                    }
                                    // The top online song results are the likeliest next tap.
                                    prewarmScheduler?.onSearchResults(
                                        immutableResults.mapNotNull { (it as? SearchResultItem.SongItem)?.song?.streamVideoId() }
                                    )
                                }
                            }
                        }
                    } catch (_: CancellationException) {
                        // Superseded by a newer query; ignore.
                    } catch (e: Exception) {
                        if (request.requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "Error performing search for query: $normalizedQuery")
                            baseResults = emptyList()
                            seedItems = emptyList()
                            _isSearching.value = false
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

    /** A new query starts on All, without re-running the previous search (that's why this isn't [updateSearchFilter]). */
    fun resetFilterForNewQuery() {
        _selectedSearchFilter.value = SearchFilterType.ALL
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
        suggestionInputs.value = normalizedQuery
        val previous = searchRequests.replayCache.lastOrNull()
        // The screen re-requests on every (query, filter) change and the filter chip also
        // re-requests; don't restart an identical search that already has results.
        if (normalizedQuery.isNotBlank() && previous?.query == normalizedQuery &&
            previous.filter == _selectedSearchFilter.value && _searchResults.value.isNotEmpty()) return
        val requestId = latestSearchRequestId.incrementAndGet()
        if (normalizedQuery.isBlank() || previous?.query != normalizedQuery || previous.filter != _selectedSearchFilter.value) {
            // Same query, other filter: show what's already loaded for that filter at once
            // (no blank list while its own search runs). A new query starts empty.
            val filterSwitch = normalizedQuery.isNotBlank() && previous?.query == normalizedQuery
            seedItems = if (filterSwitch) seedFor(_selectedSearchFilter.value, lastRawResults) else emptyList()
            // New query or filter: pagination starts over.
            baseResults = seedItems
            resetPaging()
            _isSearching.value = normalizedQuery.isNotBlank()
            if (seedItems.isNotEmpty()) {
                // Grouped synchronously here (small list) so the switch is instant.
                val grouped = runCatching { SearchVersionGrouping.group(seedItems, normalizedQuery) }.getOrDefault(seedItems)
                lastRawResults = seedItems
                ++publishGeneration
                _searchResults.value = grouped.toImmutableList()
            } else if (_searchResults.value.isNotEmpty()) {
                ++publishGeneration
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
     * Profiles: people you follow / who follow you and friends whose name or username matches,
     * then Spotify's own people search (first page). A profile link or spotify:user: URI goes
     * straight to that person, and also shows on the All tab.
     */
    private fun profileResults(query: String, filter: SearchFilterType): Flow<List<SearchResultItem>> {
        val graph = socialGraph ?: return flowOf(emptyList())
        val isProfileLink = com.theveloper.pixelplay.data.social.FriendSocialGraph.profileIdIn(query) != null
        if (filter != SearchFilterType.PROFILES && !(filter == SearchFilterType.ALL && isProfileLink)) return flowOf(emptyList())
        return kotlinx.coroutines.flow.flow {
            val local = localProfiles(query, graph)
            emit(local)
            try {
                val page = graph.searchUsers(query, offset = 0)
                profileOffset = page.people.size
                profileFirstPageHasMore = page.hasMore
                // Online copies first in the merge (they have pictures and counts), then local
                // matches it missed; then everything ranked: your friends and the closest names first.
                val merged = (page.people + local.map { (it as SearchResultItem.ProfileItem).person }).distinctBy { it.userId }
                // A pasted profile link isn't a name: show what came back as is.
                val ordered = if (isProfileLink) merged
                    else com.theveloper.pixelplay.data.social.ProfileRanking.rank(query, merged, profileRankContext(graph))
                emit(ordered.map { SearchResultItem.ProfileItem(it) })
                // The first page's paging flag only arrives now; offer more if Spotify has it.
                val paging = _songPaging.value
                if (filter == SearchFilterType.PROFILES && paging.pagesLoaded == 0 && !paging.isLoading && page.hasMore) {
                    _songPaging.value = paging.copy(hasMore = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Spotify people search failed")
            }
        }.flowOn(Dispatchers.IO)
    }

    /** People in your Spotify graph (friends with names, plus followers / following ids) matching [query]. */
    private fun localProfiles(query: String, graph: com.theveloper.pixelplay.data.social.FriendSocialGraph): List<SearchResultItem> {
        val q = query.trim().lowercase(java.util.Locale.ROOT)
        if (q.isBlank()) return emptyList()
        val linked = com.theveloper.pixelplay.data.social.FriendSocialGraph.profileIdIn(query)?.lowercase(java.util.Locale.ROOT)
        val graphIds = graph.myFollowing.value + graph.myFollowers.value
        val named = friendActivity?.profiles?.value.orEmpty().mapNotNull { (friendId, profile) ->
            val id = com.theveloper.pixelplay.data.social.FriendSocialGraph.spotifyUserId(friendId) ?: return@mapNotNull null
            val name = profile.displayName.takeIf { it.isNotBlank() } ?: id
            id to com.theveloper.pixelplay.data.social.SocialPerson(id, name, profile.avatarUrl, -1)
        }.toMap()
        val people = (named.values + graphIds.filter { it !in named }.map {
            com.theveloper.pixelplay.data.social.SocialPerson(it, it, null, -1)
        })
        // Accent-insensitive, matches your aliases too (ProfileRanking); a pasted link always matches.
        val ctx = profileRankContext(graph)
        val linkedPeople = people.filter { it.userId.lowercase(java.util.Locale.ROOT) == linked }
        val ranked = com.theveloper.pixelplay.data.social.ProfileRanking.rank(query, people, ctx)
        return (linkedPeople + ranked).distinctBy { it.userId }
            .take(LOCAL_PROFILE_LIMIT).map { SearchResultItem.ProfileItem(it) }
    }

    /** Names you know people by (alias + platform name), mutuals and who you follow, for ranking. */
    private fun profileRankContext(graph: com.theveloper.pixelplay.data.social.FriendSocialGraph): com.theveloper.pixelplay.data.social.ProfileRankContext {
        val aliases = connectedLibrary?.snapshot?.value?.aliases.orEmpty()
        val hidden = connectedLibrary?.snapshot?.value?.hiddenFriends.orEmpty().toSet()
        val names = HashMap<String, List<String>>()
        friendActivity?.profiles?.value.orEmpty().forEach { (friendId, profile) ->
            if (friendId in hidden) return@forEach
            val id = com.theveloper.pixelplay.data.social.FriendSocialGraph.spotifyUserId(friendId) ?: return@forEach
            names[id] = listOfNotNull(aliases[friendId], profile.displayName.takeIf { it.isNotBlank() }).distinct()
        }
        return com.theveloper.pixelplay.data.social.ProfileRankContext(
            friendNames = names,
            mutual = graph.mutualFriendIds.value.mapNotNullTo(HashSet()) {
                com.theveloper.pixelplay.data.social.FriendSocialGraph.spotifyUserId(it)
            },
            following = graph.myFollowing.value,
        )
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

    /** Every filter pages now (All pages its songs; the others their own kind). */
    @Suppress("UNUSED_PARAMETER")
    private fun pagingAllowed(filter: SearchFilterType) = true

    /** Whether the first page of [filter]'s results can be followed by more. */
    private fun firstPageCanPage(filter: SearchFilterType, results: List<SearchResultItem>): Boolean = when (filter) {
        SearchFilterType.ALL, SearchFilterType.SONGS -> results.any { it is SearchResultItem.SongItem }
        SearchFilterType.ALBUMS -> results.any { it is SearchResultItem.AlbumItem }
        SearchFilterType.ARTISTS -> results.any { it is SearchResultItem.ArtistItem }
        SearchFilterType.PLAYLISTS -> results.any { it is SearchResultItem.PlaylistItem }
        SearchFilterType.PROFILES -> profileFirstPageHasMore && results.any { it is SearchResultItem.ProfileItem }
    }

    /** Kept for older callers: same as [loadMoreResults]. */
    fun loadMoreSongs() = loadMoreResults()

    /**
     * Appends the next page of results for the current query and filter: songs on All / Songs,
     * otherwise more albums, artists, playlists or people. No-op while loading or at the end.
     */
    fun loadMoreResults() {
        val request = searchRequests.replayCache.lastOrNull() ?: return
        val paging = _songPaging.value
        if (paging.isLoading || !paging.hasMore || request.query.isBlank() || !pagingAllowed(request.filter)) return
        val requestId = request.requestId
        _songPaging.value = paging.copy(isLoading = true, failed = false)
        loadMoreJob = scope?.launch {
            try {
                // Folded versions count as shown, so a page doesn't bring them back as "new".
                val shownIds = _searchResults.value.mapTo(HashSet()) { it.searchIdentity() }
                _searchResults.value.forEach { item ->
                    (item as? SearchResultItem.SongItem)?.versions?.forEach { shownIds += "song:${it.id}" }
                }
                val (added, hasMore) = when (request.filter) {
                    SearchFilterType.ALL, SearchFilterType.SONGS -> {
                        val shown = HashSet<String>()
                        _searchResults.value.forEach { item ->
                            if (item is SearchResultItem.SongItem) {
                                shown += item.song.id
                                item.versions.forEach { shown += it.id }
                            }
                        }
                        val page = musicRepository.loadMoreSearchSongs(
                            query = request.query,
                            shownSongIds = shown,
                            reset = paging.pagesLoaded == 0
                        )
                        page.songs.filter { it.id !in shown }.map { SearchResultItem.SongItem(it) } to page.hasMore
                    }
                    SearchFilterType.ALBUMS, SearchFilterType.ARTISTS, SearchFilterType.PLAYLISTS -> {
                        val page = musicRepository.moreCollections(
                            query = request.query,
                            filter = request.filter,
                            reset = paging.pagesLoaded == 0
                        )
                        page.items.filter { it.searchIdentity() !in shownIds } to page.hasMore
                    }
                    SearchFilterType.PROFILES -> {
                        val graph = socialGraph
                        if (graph == null) {
                            emptyList<SearchResultItem>() to false
                        } else {
                            val page = graph.searchUsers(request.query, offset = profileOffset)
                            profileOffset += page.people.size
                            page.people.map { SearchResultItem.ProfileItem(it) }
                                .filter { it.searchIdentity() !in shownIds } to page.hasMore
                        }
                    }
                }
                if (requestId != latestSearchRequestId.get()) return@launch
                extraItems = extraItems + added
                publishResults()
                _songPaging.value = SongPagingState(
                    hasMore = hasMore && added.isNotEmpty(),
                    isLoading = false,
                    failed = false,
                    lastLoadedCount = added.size,
                    pagesLoaded = paging.pagesLoaded + 1
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Loading more search results failed")
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

    private companion object {
        /** Local (followers / following / friends) matches shown before the online people. */
        const val LOCAL_PROFILE_LIMIT = 8
        /** Longest the skeleton rows show for a query that finds nothing. */
        const val SEARCHING_TIMEOUT_MS = 6_000L
    }

    fun onCleared() {
        searchJob?.cancel()
        suggestionJob?.cancel()
        loadMoreJob?.cancel()
        scope = null
    }
}
