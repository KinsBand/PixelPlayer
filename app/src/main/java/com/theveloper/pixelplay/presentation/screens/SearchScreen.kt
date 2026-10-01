package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely
import androidx.compose.foundation.clickable
import com.theveloper.pixelplay.data.search.MusicSearchQuery
import com.theveloper.pixelplay.presentation.screens.search.components.SearchBrowseSheet
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter

import com.theveloper.pixelplay.presentation.components.onPressObserved
import com.theveloper.pixelplay.presentation.components.MultiSelectionBottomSheet
import com.theveloper.pixelplay.presentation.components.AlbumMultiSelectionOptionSheet
import com.theveloper.pixelplay.presentation.components.PlaylistMultiSelectionBottomSheet
import com.theveloper.pixelplay.presentation.components.GenreMultiSelectionOptionSheet
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionActionRow
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionCountPill
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.theveloper.pixelplay.presentation.components.StandardScreenTopBar
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import android.util.Log
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.PlaylistCover
import com.theveloper.pixelplay.presentation.components.resolveMainScreenBottomGradientHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.sanitizeNavigationBarBottomInset
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.search.components.GenreCategoriesGrid
import com.theveloper.pixelplay.presentation.screens.search.components.SearchHomeContent
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.utils.formatSongCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import androidx.compose.ui.res.stringResource

private const val MAX_ALBUM_MULTI_SELECTION = 6

private data class SearchUiSlice(
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf()
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavHostController,
    collectionViewModel: com.theveloper.pixelplay.presentation.viewmodel.LibraryCollectionViewModel = hiltViewModel()
) {
    val searchQuery = playerViewModel.searchQuery
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Multi-selection state for songs
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSongSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }

    // Multi-selection state for albums
    var selectedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val selectedAlbumIds = remember(selectedAlbums) { selectedAlbums.map { it.id }.toSet() }
    val isAlbumSelectionMode = selectedAlbums.isNotEmpty()
    var showAlbumMultiSelectionSheet by remember { mutableStateOf(false) }

    // Multi-selection state for playlists
    val playlistSelectionState = playerViewModel.playlistSelectionStateHolder
    val selectedPlaylists by playlistSelectionState.selectedPlaylists.collectAsStateWithLifecycle()
    val isPlaylistSelectionMode by playlistSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedPlaylistIds by playlistSelectionState.selectedPlaylistIds.collectAsStateWithLifecycle()
    var showPlaylistMultiSelectionSheet by remember { mutableStateOf(false) }

    // Multi-selection state for genres
    var selectedGenres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    val selectedGenreIds = remember(selectedGenres) { selectedGenres.map { it.id }.toSet() }
    val isGenreSelectionMode = selectedGenres.isNotEmpty()
    var showGenreMultiSelectionSheet by remember { mutableStateOf(false) }

    // Playlist bottom sheet songs helper state
    var playlistSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Any selection mode check
    val anySelectionMode = isSongSelectionMode || isPlaylistSelectionMode || isAlbumSelectionMode || isGenreSelectionMode

    LaunchedEffect(Unit) {
        playerViewModel.searchError.collect { error ->
            playerViewModel.sendToast(error)
        }
    }

    // BackHandler to clear selections
    BackHandler(enabled = anySelectionMode) {
        multiSelectionState.clearSelection()
        playlistSelectionState.clearSelection()
        selectedAlbums = emptyList()
        selectedGenres = emptyList()
    }

    // Long press and toggle callbacks for songs
    val onSongLongPress: (Song) -> Unit = remember(multiSelectionState, haptic) {
        { song -> 
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            multiSelectionState.toggleSelection(song) 
        }
    }
    val searchUiState by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState ->
                SearchUiSlice(
                    selectedSearchFilter = uiState.selectedSearchFilter,
                    searchResults = uiState.searchResults
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = SearchUiSlice())
    val currentFilter = searchUiState.selectedSearchFilter
    val genres by playerViewModel.genres.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Trigger genre categorization whenever user visits search screen
    LaunchedEffect(Unit) {
        playerViewModel.triggerGenreCategorization()
        playerViewModel.onSearchScreenShown()
    }

    // Search debouncing is centralized in SearchStateHolder.
    LaunchedEffect(searchQuery, currentFilter) {
        playerViewModel.performSearch(searchQuery)
    }
    val searchResults = searchUiState.searchResults
    val handleSongMoreOptionsClick: (Song) -> Unit = { song ->
        playerViewModel.selectSongForInfo(song)
        showSongInfoBottomSheet = true
    }

    val searchbarCornerRadius = 28.dp

    val dm = LocalPixelPlayDarkTheme.current

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }
    val colorScheme = MaterialTheme.colorScheme
    val bottomGradientBrush = remember(colorScheme.surfaceContainerLowest) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.2f to Color.Transparent,
                0.8f to colorScheme.surfaceContainerLowest,
                1.0f to colorScheme.surfaceContainerLowest
            )
        )
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradientBrush)
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            StandardScreenTopBar(
                title = "Search",
                onSettingsClick = {
                    navController.navigateSafely(Screen.Settings.route)
                },
                titleContent = { SearchQueryTitle(query = searchQuery) }
            )

            val headerContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            Column(
                modifier = Modifier
                    .background(color = headerContainerColor)
                    .fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 34.dp,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBL = 0.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusBR = 0.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusTR = 34.dp,
                        smoothnessAsPercentTL = 60
                    )
                ) {

            val showGenreBrowse by remember(searchQuery) { derivedStateOf { searchQuery.isBlank() } }
            AnimatedContent(
                targetState = showGenreBrowse,
                transitionSpec = {
                    val switchingToGenre = targetState
                    val enter = fadeIn(animationSpec = tween(durationMillis = 320, delayMillis = 70)) +
                        slideInVertically(animationSpec = tween(durationMillis = 320)) { fullHeight ->
                            if (switchingToGenre) -fullHeight / 10 else fullHeight / 10
                        }
                    val exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 220)) { fullHeight ->
                            if (switchingToGenre) fullHeight / 12 else -fullHeight / 12
                        }
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "search_mode_transition"
            ) { isGenreMode ->
                if (isGenreMode) {
                    // Genres moved to Library › Genres. Before you type, Search shows recent
                    // searches, quick ways into your music and searches from your listening.
                    val searchHistory by remember(playerViewModel) {
                        playerViewModel.playerUiState.map { it.searchHistory }.distinctUntilChanged()
                    }.collectAsStateWithLifecycle(initialValue = persistentListOf())
                    val insights by collectionViewModel.insights.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) {
                        playerViewModel.loadSearchHistory()
                        collectionViewModel.onShelvesVisible()
                    }
                    val suggestions = remember(insights, searchHistory) {
                        val searched = searchHistory.mapTo(HashSet()) { it.query.trim().lowercase() }
                        (insights.topArtists.map { it.name } + insights.newReleases.map { it.artist })
                            .map { it.trim() }
                            .filter { it.isNotBlank() && it.lowercase() !in searched }
                            .distinctBy { it.lowercase() }
                    }
                    val openLibraryTab: (String) -> Unit = { tabKey ->
                        playerViewModel.requestLibraryTab(tabKey)
                        navController.navigateToTopLevelSafely(Screen.Library.route)
                    }
                    val recentlyHeardViewModel: com.theveloper.pixelplay.presentation.viewmodel.RecentlyHeardViewModel = hiltViewModel()
                    val recentlyHeardEntries by recentlyHeardViewModel.entries.collectAsStateWithLifecycle()
                    val latestHeard = recentlyHeardEntries.firstOrNull()
                    SearchHomeContent(
                        recentlyHeard = latestHeard,
                        recentlyHeardTime = latestHeard?.let {
                            com.theveloper.pixelplay.presentation.screens.formatHeardWhen(context, it.heardAtEpochMs)
                        }.orEmpty(),
                        onRecentlyHeard = { navController.navigateSafely(Screen.RecentlyHeard.route) },
                        history = searchHistory,
                        suggestions = suggestions,
                        bottomPadding = bottomBarHeightDp + MiniPlayerHeight + 24.dp,
                        onQuery = { query -> playerViewModel.updateSearchQuery(query) },
                        onDeleteHistory = { query -> playerViewModel.deleteSearchHistoryItem(query) },
                        onClearHistory = { playerViewModel.clearSearchHistory() },
                        onNewReleases = { openLibraryTab("ARTIST") },
                        onCharts = { navController.navigateSafely(Screen.Stats.route) },
                        onGenresAndMoods = { openLibraryTab("GENRES") }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        if (anySelectionMode) {
                            val count = when {
                                isSongSelectionMode -> selectedSongs.size
                                isPlaylistSelectionMode -> selectedPlaylists.size
                                isAlbumSelectionMode -> selectedAlbums.size
                                else -> 0
                            }
                            SelectionActionRow(
                                selectedCount = count,
                                onSelectAll = {
                                    when {
                                        isSongSelectionMode -> {
                                            val songsToSelect = searchResults.filterIsInstance<SearchResultItem.SongItem>().map { it.song }
                                            multiSelectionState.selectAll(songsToSelect)
                                        }
                                        isPlaylistSelectionMode -> {
                                            val playlistsToSelect = searchResults.filterIsInstance<SearchResultItem.PlaylistItem>().filter { it.browseId == null && !playerViewModel.isConnectedSearchPlaylist(it.playlist.id) }.map { it.playlist }
                                            playlistSelectionState.selectAll(playlistsToSelect)
                                        }
                                        isAlbumSelectionMode -> {
                                            val albumsToSelect = searchResults.filterIsInstance<SearchResultItem.AlbumItem>().filter { it.browseId == null }.map { it.album }
                                            val remaining = MAX_ALBUM_MULTI_SELECTION - selectedAlbums.size
                                            if (remaining <= 0) {
                                                playerViewModel.sendToast(
                                                    context.getString(
                                                        R.string.library_toast_max_albums_selection,
                                                        MAX_ALBUM_MULTI_SELECTION
                                                    )
                                                )
                                            } else {
                                                val toAdd = albumsToSelect.filterNot { selectedAlbumIds.contains(it.id) }.take(remaining)
                                                selectedAlbums = selectedAlbums + toAdd
                                            }
                                        }
                                    }
                                },
                                onDeselect = {
                                    multiSelectionState.clearSelection()
                                    playlistSelectionState.clearSelection()
                                    selectedAlbums = emptyList()
                                },
                                onOptionsClick = {
                                    when {
                                        isSongSelectionMode -> showMultiSelectionSheet = true
                                        isPlaylistSelectionMode -> showPlaylistMultiSelectionSheet = true
                                        isAlbumSelectionMode -> showAlbumMultiSelectionSheet = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
                            )
                        } else {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item { SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel) }
                                item { SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel) }
                                item { SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel) }
                                item { SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel) }
                                item { SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel) }
                            }
                        }
                        val searchHint = remember(searchQuery) { MusicSearchQuery.parse(searchQuery).hint }
                        searchHint?.let { hint ->
                            Text(hint, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            Crossfade(
                                targetState = searchResults.isEmpty(),
                                animationSpec = tween(durationMillis = 190),
                                label = "search_results_fade"
                            ) { isEmpty ->
                                if (isEmpty) {
                                    Column {
                                        if (currentFilter == SearchFilterType.ALL || currentFilter == SearchFilterType.ARTISTS) {
                                            SearchResultSectionHeader(title = "Artists")
                                            Text("Artist matches will appear here", modifier = Modifier.padding(horizontal = 16.dp),
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        EmptySearchResults(searchQuery = searchQuery, colorScheme = colorScheme)
                                    }
                                } else {
                                    SearchResultsList(
                                        results = searchResults,
                                        searchQuery = searchQuery,
                                        currentFilter = currentFilter,
                                        playerViewModel = playerViewModel,
                                        onItemSelected = {
                                            if (searchQuery.isNotBlank()) {
                                                playerViewModel.onSearchQuerySubmitted(searchQuery)
                                            }
                                        },
                                        currentPlayingSongId = stablePlayerState.currentSong?.id,
                                        isPlaying = stablePlayerState.isPlaying,
                                        onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                        navController = navController,
                                        isSelectionMode = isSongSelectionMode,
                                        selectedSongIds = selectedSongIds,
                                        getSelectionIndex = { songId -> multiSelectionState.getSelectionIndex(songId) },
                                        onSongLongPress = onSongLongPress,
                                        selectedAlbums = selectedAlbums,
                                        selectedPlaylists = selectedPlaylists,
                                        isAlbumSelectionMode = isAlbumSelectionMode,
                                        isPlaylistSelectionMode = isPlaylistSelectionMode,
                                        onAlbumLongPress = { album ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (selectedAlbums.any { it.id == album.id }) {
                                                selectedAlbums = selectedAlbums.filterNot { it.id == album.id }
                                            } else if (selectedAlbums.size >= MAX_ALBUM_MULTI_SELECTION) {
                                                playerViewModel.sendToast(
                                                    context.getString(
                                                        R.string.library_toast_max_albums_selection,
                                                        MAX_ALBUM_MULTI_SELECTION
                                                    )
                                                )
                                            } else {
                                                selectedAlbums = selectedAlbums + album
                                            }
                                        },
                                        onAlbumSelectionToggle = { album ->
                                            if (selectedAlbums.any { it.id == album.id }) {
                                                selectedAlbums = selectedAlbums.filterNot { it.id == album.id }
                                            } else if (selectedAlbums.size >= MAX_ALBUM_MULTI_SELECTION) {
                                                playerViewModel.sendToast(
                                                    context.getString(
                                                        R.string.library_toast_max_albums_selection,
                                                        MAX_ALBUM_MULTI_SELECTION
                                                    )
                                                )
                                            } else {
                                                selectedAlbums = selectedAlbums + album
                                            }
                                        },
                                        getAlbumSelectionIndex = { albumId ->
                                            val idx = selectedAlbums.indexOfFirst { it.id == albumId }
                                            if (idx >= 0) idx + 1 else null
                                        },
                                        onPlaylistLongPress = { playlist ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            playlistSelectionState.toggleSelection(playlist)
                                        },
                                        onPlaylistSelectionToggle = { playlist ->
                                            playlistSelectionState.toggleSelection(playlist)
                                        },
                                        getPlaylistSelectionIndex = { playlistId ->
                                            playlistSelectionState.getSelectionIndex(playlistId)
                                        }
                                    )
                                }
                            }

                            val count = when {
                                isSongSelectionMode -> selectedSongs.size
                                isPlaylistSelectionMode -> selectedPlaylists.size
                                isAlbumSelectionMode -> selectedAlbums.size
                                else -> 0
                            }
                            SelectionCountPill(
                                selectedCount = count,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .zIndex(2f)
                                    .padding(top = 16.dp)
                            )
                        }
                    }
                }
            }
                    }
                }
            }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(brush = bottomGradientBrush)
        )


    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteSongIds) {
            derivedStateOf {
                currentSong?.let { favoriteSongIds.contains(it.id) }
            }
        }.value ?: false
        val removeFromListTrigger = remember(currentSong) {
            {
                playerViewModel.updateSearchQuery(playerViewModel.searchQuery + " ")
            }
        }

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRouteForSong(currentSong),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRouteForSongArtist(currentSong, artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistByName = { artistName ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRouteForName(artistName),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newAlbumArtist,
                        newComposer,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
            )
        }
    }

    // Multi-Selection Bottom Sheet
    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        val activity = context as? android.app.Activity
        val favoriteIds = favoriteSongIds.toSet()

        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = favoriteIds,
            onDismiss = { showMultiSelectionSheet = false },
            onPlayAll = {
                playerViewModel.playSelectedSongs(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedToQueue(selectedSongs)
                showMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAsNext(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                playlistSheetSongs = selectedSongs
                showMultiSelectionSheet = false
                showPlaylistBottomSheet = true
            },
            onToggleLikeAll = { shouldLike ->
                if (shouldLike) {
                    playerViewModel.likeSelectedSongs(selectedSongs)
                } else {
                    playerViewModel.unlikeSelectedSongs(selectedSongs)
                }
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedAsZip(selectedSongs)
                showMultiSelectionSheet = false
            },
            onDeleteAll = { _, onComplete ->
                activity?.let {
                    playerViewModel.deleteSelectedFromDevice(it, selectedSongs) {
                        showMultiSelectionSheet = false
                        onComplete(true)
                    }
                }
            },
            onBatchEdit = {
                showMultiSelectionSheet = false
            }
        )
    }

    // Album Multi-Selection Option Sheet
    if (showAlbumMultiSelectionSheet && selectedAlbums.isNotEmpty()) {
        AlbumMultiSelectionOptionSheet(
            selectedAlbums = selectedAlbums,
            maxSelection = MAX_ALBUM_MULTI_SELECTION,
            onDismiss = { showAlbumMultiSelectionSheet = false },
            onPlay = {
                playerViewModel.playSelectedAlbums(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAlbumsAsNext(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedAlbumsToQueue(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                scope.launch {
                    val songs = playerViewModel.getSongsForAlbums(selectedAlbums)
                    playlistSheetSongs = songs
                    showPlaylistBottomSheet = true
                    selectedAlbums = emptyList()
                    showAlbumMultiSelectionSheet = false
                }
            }
        )
    }

    // Playlist Multi-Selection Bottom Sheet
    if (showPlaylistMultiSelectionSheet && selectedPlaylists.isNotEmpty()) {
        val activity = context as? android.app.Activity

        PlaylistMultiSelectionBottomSheet(
            selectedPlaylists = selectedPlaylists,
            onDismiss = {
                showPlaylistMultiSelectionSheet = false
            },
            onDeleteAll = {
                playlistViewModel.deletePlaylistsInBatch(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            onExportAll = {
                playlistViewModel.exportPlaylistsAsM3u(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            onMergeAll = {
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            onShareAll = {
                activity?.let {
                    playlistViewModel.shareSelectedPlaylistsAsZip(selectedPlaylistIds.toList(), it)
                }
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            }
        )
    }

    // Genre Multi-Selection Option Sheet
    if (showGenreMultiSelectionSheet && selectedGenres.isNotEmpty()) {
        GenreMultiSelectionOptionSheet(
            selectedGenres = selectedGenres,
            onDismiss = { showGenreMultiSelectionSheet = false },
            onPlay = {
                playerViewModel.playSelectedGenres(selectedGenres)
                selectedGenres = emptyList()
                showGenreMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedGenresAsNext(selectedGenres)
                selectedGenres = emptyList()
                showGenreMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedGenresToQueue(selectedGenres)
                selectedGenres = emptyList()
                showGenreMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                scope.launch {
                    val songs = playerViewModel.getSongsForGenres(selectedGenres)
                    playlistSheetSongs = songs
                    showPlaylistBottomSheet = true
                    selectedGenres = emptyList()
                    showGenreMultiSelectionSheet = false
                }
            }
        )
    }

    // Playlist Bottom Sheet (Single or Multi additions)
    if (showPlaylistBottomSheet) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        val songsToAddToPlaylist = if (playlistSheetSongs.isNotEmpty()) {
            playlistSheetSongs
        } else {
            selectedSongForInfo?.let { listOf(it) } ?: emptyList()
        }

        if (songsToAddToPlaylist.isNotEmpty()) {
            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = songsToAddToPlaylist,
                onDismiss = {
                    showPlaylistBottomSheet = false
                    playlistSheetSongs = emptyList()
                },
                bottomBarHeight = bottomBarHeightDp,
                playerViewModel = playerViewModel,
            )
        }
    }
}

@Composable
fun SearchResultSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SearchHistoryList(
    historyItems: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val localDensity = LocalDensity.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.search_recent_searches),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (historyItems.isNotEmpty()) {
                TextButton(onClick = onClearAllHistory) {
                    Text(stringResource(R.string.search_action_clear_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
            )
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }, contentType = { "search_history" }) { item ->
                SearchHistoryListItem(
                    item = item,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete
                )
            }
        }
    }
}

@Composable
fun SearchHistoryListItem(
    item: SearchHistoryItem,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onHistoryClick(item.query) }) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.search_cd_search_history_icon),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onHistoryDelete(item.query) }) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.search_cd_delete_search_history_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.search_cd_no_results),
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = if (searchQuery.isNotBlank()) {
                stringResource(R.string.search_no_results_for_query, searchQuery)
            } else {
                stringResource(R.string.search_nothing_found)
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.search_try_different_or_filters),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchResultsList(
    results: List<SearchResultItem>,
    searchQuery: String,
    currentFilter: SearchFilterType = SearchFilterType.ALL,
    playerViewModel: PlayerViewModel,
    onItemSelected: () -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    navController: NavHostController,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    getSelectionIndex: (String) -> Int? = { null },
    onSongLongPress: (Song) -> Unit = {},
    selectedAlbums: List<Album> = emptyList(),
    selectedPlaylists: List<Playlist> = emptyList(),
    isAlbumSelectionMode: Boolean = false,
    isPlaylistSelectionMode: Boolean = false,
    onAlbumLongPress: (Album) -> Unit = {},
    onAlbumSelectionToggle: (Album) -> Unit = {},
    getAlbumSelectionIndex: (Long) -> Int? = { null },
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {},
    getPlaylistSelectionIndex: (String) -> Int? = { null }
) {
    val localDensity = LocalDensity.current
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.search_no_results_found), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val groupedResults = remember(results) {
        results.filterNot { it is SearchResultItem.FriendItem }.groupBy { item ->
            when (item) {
                is SearchResultItem.SongItem -> SearchFilterType.SONGS
                is SearchResultItem.AlbumItem -> SearchFilterType.ALBUMS
                is SearchResultItem.ArtistItem -> SearchFilterType.ARTISTS
                is SearchResultItem.PlaylistItem -> SearchFilterType.PLAYLISTS
                is SearchResultItem.FriendItem -> SearchFilterType.PLAYLISTS // filtered out above
            }
        }
    }
    val friendResults = remember(results) { results.filterIsInstance<SearchResultItem.FriendItem>() }
    var openedFriend by remember { mutableStateOf<SearchResultItem.FriendItem?>(null) }
    val songResultsQueue = remember(groupedResults) {
        buildList {
            groupedResults[SearchFilterType.SONGS]
                ?.forEach { item ->
                    val song = (item as? SearchResultItem.SongItem)?.song ?: return@forEach
                    add(song)
                }
        }
    }
    val searchQueueName = remember(searchQuery) {
        searchQuery.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "Search: $it" }
            ?: "Search Results"
    }
    // During the lyrics screen's Add Song, the tap opens the Play / Next / Soon / Queue sheet.
    val songTap = com.theveloper.pixelplay.presentation.components.LocalSongPrimaryTap.current
    val onSongResultClick = remember(playerViewModel, onItemSelected, songResultsQueue, searchQueueName, songTap) {
        { song: Song ->
            val playbackQueue = if (songResultsQueue.any { it.id == song.id }) {
                songResultsQueue
            } else {
                listOf(song)
            }
            if (songTap != null) {
                songTap.onSongTap(song, playbackQueue, searchQueueName)
            } else {
                playerViewModel.showAndPlaySong(song, playbackQueue, searchQueueName)
                onItemSelected()
            }
        }
    }

    var remoteCollection by remember { mutableStateOf<Pair<String, String>?>(null) }
    remoteCollection?.let { (id, title) ->
        SearchBrowseSheet(id = id, title = title, player = playerViewModel, onDismiss = { remoteCollection = null })
    }

    // Online playlists open like library playlists: their tracks are fetched and shown on the
    // normal Playlist page (not the quick browse sheet), and Play plays the whole playlist.
    val remotePlaylistLoader: com.theveloper.pixelplay.presentation.viewmodel.SearchBrowseViewModel = hiltViewModel()
    val remotePlaylistScope = rememberCoroutineScope()
    var openingRemotePlaylistId by remember { mutableStateOf<String?>(null) }
    fun openRemotePlaylist(browseId: String, playlist: Playlist, play: Boolean) {
        if (openingRemotePlaylistId != null) return
        openingRemotePlaylistId = browseId
        remotePlaylistScope.launch {
            val songs = try {
                remotePlaylistLoader.loadAllSongs(browseId)
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                openingRemotePlaylistId = null
                throw cancelled
            } catch (e: Exception) {
                emptyList()
            }
            openingRemotePlaylistId = null
            if (songs.isEmpty()) {
                playerViewModel.sendToast("Couldn't load this playlist")
                return@launch
            }
            if (play) {
                playerViewModel.playSongs(songs, songs.first(), playlist.name)
            } else {
                val page = Playlist(
                    id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}search:$browseId",
                    name = playlist.name,
                    songIds = songs.map { it.id },
                    coverImageUri = playlist.coverImageUri
                )
                PlaylistViewModel.registerTransientPlaylist(page, songs)
                navController.navigateSafely(Screen.PlaylistDetail.createRoute(page.id))
            }
            onItemSelected()
        }
    }
    /**
     * Connected-service and friend playlists: a friend's public playlist is saved first (like
     * opening it from Friends), then the playlist page opens, or its songs start playing.
     */
    suspend fun openSocialPlaylist(playlist: Playlist, play: Boolean) {
        if (openingRemotePlaylistId != null) return
        openingRemotePlaylistId = playlist.id
        val resolved = try {
            playerViewModel.resolveSearchPlaylist(playlist)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            openingRemotePlaylistId = null
            throw cancelled
        } catch (e: Exception) {
            Timber.tag("SearchScreen").w(e, "Couldn't open playlist ${playlist.id}")
            null
        }
        openingRemotePlaylistId = null
        if (resolved == null) {
            playerViewModel.sendToast("Couldn't open this playlist")
            return
        }
        val (id, songs) = resolved
        if (play) {
            if (songs.isEmpty()) {
                playerViewModel.sendToast("Empty playlist")
                return
            }
            playerViewModel.playSongs(songs, songs.first(), playlist.name)
        } else {
            openedFriend = null
            navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
        }
        onItemSelected()
    }

    val artistResults = remember(results, currentFilter) {
        // Only the "All" tab mixes credited song artists into the shelf; the category tabs show
        // exactly the category that was asked for.
        val credited = if (currentFilter != SearchFilterType.ALL) emptyList() else
            results.filterIsInstance<SearchResultItem.SongItem>().mapNotNull { item ->
            val localArtist = item.song.id.toLongOrNull() != null && item.song.youtubeId == null
            if ((item.artistBrowseId == null && !localArtist) || item.song.artist.isBlank()) null
            else SearchResultItem.ArtistItem(Artist(item.song.artistId, item.song.artist, 0), item.artistBrowseId)
        }
        (results.filterIsInstance<SearchResultItem.ArtistItem>() + credited)
            .distinctBy { it.browseId ?: "local:${it.artist.id}" }
    }
    val sectionOrder = when (currentFilter) {
        SearchFilterType.ALL -> listOf(SearchFilterType.SONGS, SearchFilterType.ALBUMS, SearchFilterType.PLAYLISTS)
        SearchFilterType.ARTISTS -> listOf(SearchFilterType.ARTISTS)
        else -> listOf(currentFilter)
    }
    val showArtistShelf = currentFilter == SearchFilterType.ALL

    // Height of the keyboard above the system navigation bar; tracks the IME animation.
    val imeBottomAboveNavBar = with(localDensity) {
        (WindowInsets.ime.getBottom(localDensity) - WindowInsets.navigationBars.getBottom(localDensity))
            .coerceAtLeast(0)
            .toDp()
    }
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val navBarOccupiedHeight = resolveNavBarOccupiedHeight(
        sanitizeNavigationBarBottomInset(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        navBarCompactMode
    )
    // The bottom bar and the mini player are pushed up with the keyboard; keep the
    // last items clear of the keyboard + search bar + mini player stack.
    val miniPlayerSpace = if (playerStableState.currentSong != null) MiniPlayerHeight else 0.dp

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clip(
                RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp
                )
            ),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = imeBottomAboveNavBar + navBarOccupiedHeight + miniPlayerSpace + 8.dp
        )
    ) {
        if (showArtistShelf) item(key = "artist_shelf") {
            Column {
                SearchResultSectionHeader(title = "Artists")
                if (artistResults.isEmpty()) {
                    Text("No matching artists", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                } else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artistResults, key = { it.browseId ?: "local:${it.artist.id}" }) { result ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).clickable {
                                // Every artist opens the artist page: library artists by id,
                                // online ones (YouTube Music browse id) by name.
                                navController.navigateSafelyReplacing(
                                    route = Screen.ArtistDetail.createRouteForArtist(
                                        artistId = result.artist.id,
                                        artistName = result.artist.name,
                                        isLibraryArtist = result.browseId == null
                                    ),
                                    patternToPop = Screen.ArtistDetail.route
                                )
                                onItemSelected()
                            }.padding(vertical = 8.dp)) {
                            SmartImage(model = result.artist.effectiveImageUrl, contentDescription = result.artist.name,
                                targetSize = SmartImageListTargetSize, modifier = Modifier.size(88.dp).clip(CircleShape))
                            Text(result.artist.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        sectionOrder.forEach { filterType ->
            val itemsForSection = groupedResults[filterType] ?: emptyList()

            if (itemsForSection.isNotEmpty()) {
                item(key = "header_${filterType.name}") {
                    SearchResultSectionHeader(
                        title = when (filterType) {
                            SearchFilterType.SONGS -> "Songs"
                            SearchFilterType.ALBUMS -> "Albums"
                            SearchFilterType.ARTISTS -> "Artists"
                            SearchFilterType.PLAYLISTS -> "Playlists"
                            else -> "Results"
                        }
                    )
                }

                items(
                    count = itemsForSection.size,
                    key = { index ->
                        val item = itemsForSection[index]
                        when (item) {
                            is SearchResultItem.SongItem -> "song_${item.song.id}"
                            is SearchResultItem.AlbumItem -> "album_${item.browseId ?: item.album.id}_${index}"
                            is SearchResultItem.ArtistItem -> "artist_${item.browseId ?: item.artist.id}_${index}"
                            is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}_${index}"
                            is SearchResultItem.FriendItem -> "friend_${item.friendId}"
                        }
                    },
                    contentType = { index ->
                        when (itemsForSection[index]) {
                            is SearchResultItem.SongItem -> "search_song"
                            is SearchResultItem.AlbumItem -> "search_album"
                            is SearchResultItem.ArtistItem -> "search_artist"
                            is SearchResultItem.PlaylistItem -> "search_playlist"
                            is SearchResultItem.FriendItem -> "search_friend"
                        }
                    }
                ) { index ->
                    val item = itemsForSection[index]
                    val pressModifier = if (item is SearchResultItem.SongItem) {
                        Modifier.onPressObserved(
                            key = item.song.id,
                            onPress = { playerViewModel.onSongPressed(item.song) },
                            onCancel = { playerViewModel.onSongPressCancelled(item.song) }
                        )
                    } else Modifier
                    Box(modifier = Modifier.animateItem().padding(bottom = 12.dp).then(pressModifier)) {
                        when (item) {
                            is SearchResultItem.SongItem -> {
                                val isSelected = selectedSongIds.contains(item.song.id)
                                val selectionIndex = getSelectionIndex(item.song.id)
                                EnhancedSongListItem(
                                    song = item.song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentPlayingSongId == item.song.id,
                                    onMoreOptionsClick = onSongMoreOptionsClick,
                                    onClick = { onSongResultClick(item.song) },
                                    isSelected = isSelected,
                                    selectionIndex = selectionIndex,
                                    isSelectionMode = isSelectionMode,
                                    onLongPress = { onSongLongPress(item.song) }
                                )
                            }

                            is SearchResultItem.AlbumItem -> {
                                val onPlayClick = remember(item.album, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Album clicked: ${item.album.title}")
                                        playerViewModel.playAlbum(item.album)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.album,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.AlbumDetail.createRoute(item.album.id),
                                            patternToPop = Screen.AlbumDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                val isSelected = selectedAlbums.any { it.id == item.album.id }
                                val selectionIndex = getAlbumSelectionIndex(item.album.id)
                                SearchResultAlbumItem(
                                    album = item.album,
                                    onPlayClick = { if (item.browseId != null) remoteCollection = item.browseId to item.album.title else onPlayClick() },
                                    onOpenClick = { if (item.browseId != null) remoteCollection = item.browseId to item.album.title else onOpenClick() },
                                    isSelected = isSelected,
                                    selectionIndex = selectionIndex,
                                    isSelectionMode = isAlbumSelectionMode,
                                    onLongPress = { if (item.browseId == null) onAlbumLongPress(item.album) },
                                    onSelectionToggle = { if (item.browseId == null) onAlbumSelectionToggle(item.album) }
                                )
                            }

                            is SearchResultItem.ArtistItem -> {
                                val onPlayClick = remember(item.artist, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Artist clicked: ${item.artist.name}")
                                        playerViewModel.playArtist(item.artist)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.artist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.ArtistDetail.createRouteForArtist(
                                                artistId = item.artist.id,
                                                artistName = item.artist.name,
                                                isLibraryArtist = item.browseId == null
                                            ),
                                            patternToPop = Screen.ArtistDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultArtistItem(
                                    artist = item.artist,
                                    onPlayClick = { if (item.browseId != null) { remoteCollection = item.browseId to item.artist.name; onItemSelected() } else onPlayClick() },
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.PlaylistItem -> {
                                val playlistSongs by remember(item.playlist.songIds, playerViewModel) {
                                    playerViewModel.observeSongs(item.playlist.songIds)
                                }.collectAsStateWithLifecycle(initialValue = emptyList())
                                val coroutineScope = rememberCoroutineScope()
                                val onPlayClick: () -> Unit = {
                                    coroutineScope.launch {
                                        if (playerViewModel.isConnectedSearchPlaylist(item.playlist.id)) {
                                            openSocialPlaylist(item.playlist, play = true)
                                            return@launch
                                        }
                                        val songs = playerViewModel.getSongs(item.playlist.songIds)
                                        if (songs.isNotEmpty()) {
                                            playerViewModel.playSongs(
                                                songs,
                                                songs.first(),
                                                item.playlist.name
                                            )
                                            if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                        } else {
                                            playerViewModel.sendToast("Empty playlist")
                                        }
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.playlist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        if (playerViewModel.isConnectedSearchPlaylist(item.playlist.id)) {
                                            coroutineScope.launch { openSocialPlaylist(item.playlist, play = false) }
                                        } else {
                                            navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.playlist.id))
                                            onItemSelected()
                                        }
                                    }
                                }
                                val isSelected = selectedPlaylists.any { it.id == item.playlist.id }
                                val selectionIndex = getPlaylistSelectionIndex(item.playlist.id)
                                SearchResultPlaylistItem(
                                    playlist = item.playlist,
                                    playlistSongs = playlistSongs,
                                    onPlayClick = { val id = item.browseId; if (id != null) openRemotePlaylist(id, item.playlist, play = true) else onPlayClick() },
                                    onOpenClick = { val id = item.browseId; if (id != null) openRemotePlaylist(id, item.playlist, play = false) else onOpenClick() },
                                    isSelected = isSelected,
                                    selectionIndex = selectionIndex,
                                    isSelectionMode = isPlaylistSelectionMode,
                                    onLongPress = { if (item.browseId == null && !playerViewModel.isConnectedSearchPlaylist(item.playlist.id)) onPlaylistLongPress(item.playlist) },
                                    onSelectionToggle = { if (item.browseId == null && !playerViewModel.isConnectedSearchPlaylist(item.playlist.id)) onPlaylistSelectionToggle(item.playlist) }
                                )
                                // Fetching an online playlist's tracks before its page opens.
                                if ((item.browseId != null && openingRemotePlaylistId == item.browseId) ||
                                    openingRemotePlaylistId == item.playlist.id) {
                                    androidx.compose.material3.LinearProgressIndicator(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                    )
                                }
                            }

                            is SearchResultItem.FriendItem -> Unit // rendered in the Friends section
                        }
                    }
                }
                if (filterType == SearchFilterType.SONGS) {
                    item(key = "songs_show_more", contentType = "search_show_more") {
                        SearchShowMoreSongsFooter(
                            playerViewModel = playerViewModel,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
        if (friendResults.isNotEmpty() &&
            (currentFilter == SearchFilterType.ALL || currentFilter == SearchFilterType.PLAYLISTS)) {
            item(key = "header_FRIENDS") {
                Box(Modifier.animateItem()) { SearchResultSectionHeader(title = "Friends") }
            }
            items(friendResults, key = { "friend_${it.friendId}" }, contentType = { "search_friend" }) { friend ->
                SearchResultFriendItem(
                    friend = friend,
                    onClick = { openedFriend = friend },
                    modifier = Modifier.animateItem().padding(bottom = 12.dp)
                )
            }
        }
    }

    openedFriend?.let { friend ->
        SearchFriendPlaylistsSheet(
            friend = friend,
            openingPlaylistId = openingRemotePlaylistId,
            onOpen = { playlist -> remotePlaylistScope.launch { openSocialPlaylist(playlist, play = false) } },
            onPlay = { playlist -> remotePlaylistScope.launch { openSocialPlaylist(playlist, play = true) } },
            onDismiss = { openedFriend = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultAlbumItem(
    album: Album,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "albumSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "albumSelectionBorder"
    )

    Card(
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = MaterialTheme.colorScheme.primary,
                        shape = itemShape
                    )
                } else {
                    Modifier
                }
            )
            .clip(itemShape)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectionToggle()
                    } else {
                        onOpenClick()
                    }
                },
                onLongClick = onLongPress
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = album.albumArtUriString,
                    contentDescription = "Album Art: ${album.title}",
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(itemShape)
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.common_play_album), modifier = Modifier.size(24.dp))
                }
            }
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectionIndex?.toString() ?: "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultArtistItem(
    artist: Artist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!artist.effectiveImageUrl.isNullOrBlank()) {
                SmartImage(
                    model = artist.effectiveImageUrl,
                    contentDescription = "Artist: ${artist.name}",
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_artist_24),
                    contentDescription = "Artist",
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(artist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Artist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultPlaylistItem(
    playlist: Playlist,
    playlistSongs: List<Song>,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "playlistSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "playlistSelectionBorder"
    )

    Card(
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = MaterialTheme.colorScheme.primary,
                        shape = itemShape
                    )
                } else {
                    Modifier
                }
            )
            .clip(itemShape)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectionToggle()
                    } else {
                        onOpenClick()
                    }
                },
                onLongClick = onLongPress
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistCover(
                    playlist = playlist,
                    playlistSongs = playlistSongs,
                    size = 56.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val serviceLabel = searchServiceLabel(playlist.source)
                    Text(
                        text = listOfNotNull(
                            formatSongCount(playlist.songIds.size).takeIf { playlist.songIds.isNotEmpty() || serviceLabel == null },
                            playlist.ownerName.takeIf { playlist.friendId != null && it.isNotBlank() }?.let { "by $it" },
                            serviceLabel
                        ).joinToString(" \u00B7 "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Playlist", modifier = Modifier.size(24.dp))
                }
            }
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectionIndex?.toString() ?: "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val selected = filterType == currentFilter

    val labelResId = when (filterType) {
        SearchFilterType.ALL -> R.string.common_all
        SearchFilterType.SONGS -> R.string.library_tab_songs
        SearchFilterType.ALBUMS -> R.string.library_tab_albums
        SearchFilterType.ARTISTS -> R.string.library_tab_artists
        SearchFilterType.PLAYLISTS -> R.string.library_tab_playlists
    }

    FilterChip(
        selected = selected,
        onClick = { playerViewModel.updateSearchFilter(filterType) },
        label = { Text(stringResource(labelResId)) },
        modifier = modifier,
        shape = CircleShape,
        border = BorderStroke(
            width = 0.dp,
            color = Color.Transparent
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor =  MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
         leadingIcon = if (selected) {
             {
                 Icon(
                     painter = painterResource(R.drawable.rounded_check_circle_24),
                     contentDescription = "Selected",
                     tint = MaterialTheme.colorScheme.onPrimary,
                     modifier = Modifier.size(FilterChipDefaults.IconSize)
                 )
             }
         } else {
             null
         }
    )
}


/**
 * Search header: "Search" while the bar is empty, otherwise the live query under a small
 * "Results for" label. Only the empty <-> non-empty switch animates; keystrokes just update text.
 */
@Composable
private fun SearchQueryTitle(query: String) {
    val trimmed = query.trim()
    val hasQuery = trimmed.isNotEmpty()
    // Keep the last non-empty text so the outgoing title doesn't blank out while it fades.
    var lastQuery by remember { mutableStateOf(trimmed) }
    androidx.compose.runtime.SideEffect { if (hasQuery) lastQuery = trimmed }
    val shownQuery = if (hasQuery) trimmed else lastQuery
    val reducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    AnimatedContent(
        targetState = hasQuery,
        transitionSpec = {
            if (reducedMotion) {
                androidx.compose.animation.EnterTransition.None togetherWith
                    androidx.compose.animation.ExitTransition.None
            } else {
                val enterMs = com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium2
                val exitMs = com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4
                val enter = fadeIn(tween(enterMs, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate)) +
                    slideInVertically(tween(enterMs, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate)) { it / 4 }
                val exit = fadeOut(tween(exitMs, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate)) +
                    slideOutVertically(tween(exitMs, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate)) { -it / 4 }
                (enter togetherWith exit).using(SizeTransform(clip = false))
            }
        },
        label = "search_query_title"
    ) { showQuery ->
        if (!showQuery) {
            Text(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .semantics { heading() },
                text = "Search",
                fontFamily = com.theveloper.pixelplay.ui.theme.GoogleSansRounded,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 40.sp,
                letterSpacing = 1.sp,
                maxLines = 1
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .semantics(mergeDescendants = true) {
                        heading()
                        contentDescription = "Search results for $shownQuery"
                    }
            ) {
                Text(
                    text = "Results for",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = shownQuery,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = com.theveloper.pixelplay.ui.theme.GoogleSansRounded,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


/**
 * "Show more" after the last song result: a tonal button that swaps to a spinner while the
 * next page loads, a retry label if it failed, and disappears once there's nothing more.
 */
@Composable
private fun SearchShowMoreSongsFooter(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val paging by playerViewModel.searchSongPaging.collectAsStateWithLifecycle()
    val reducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    val visible = paging.hasMore || paging.isLoading || paging.failed
    val view = androidx.compose.ui.platform.LocalView.current
    // Announce how many songs arrived once a load finishes.
    LaunchedEffect(paging.pagesLoaded) {
        if (paging.pagesLoaded > 0 && paging.lastLoadedCount > 0) {
            view.announceForAccessibility("${paging.lastLoadedCount} more songs loaded")
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = if (reducedMotion) androidx.compose.animation.EnterTransition.None else
            fadeIn(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium1,
                easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate)),
        exit = if (reducedMotion) androidx.compose.animation.ExitTransition.None else
            fadeOut(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort3,
                easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = when {
                    paging.isLoading -> 1
                    paging.failed -> 2
                    else -> 0
                },
                transitionSpec = {
                    if (reducedMotion) {
                        androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                    } else {
                        fadeIn(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4,
                            easing = com.theveloper.pixelplay.ui.theme.MotionTokens.Emphasized)) togetherWith
                            fadeOut(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort3,
                                easing = com.theveloper.pixelplay.ui.theme.MotionTokens.Emphasized))
                    }
                },
                contentAlignment = Alignment.Center,
                label = "search_show_more_state"
            ) { state ->
                when (state) {
                    1 -> Box(
                        modifier = Modifier
                            .height(48.dp)
                            .semantics { contentDescription = "Loading more songs" },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                    }
                    2 -> androidx.compose.material3.FilledTonalButton(
                        onClick = { playerViewModel.loadMoreSearchSongs() },
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Couldn't load more \u2013 Retry", style = MaterialTheme.typography.labelLarge)
                    }
                    else -> androidx.compose.material3.FilledTonalButton(
                        onClick = { playerViewModel.loadMoreSearchSongs() },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Show more songs" }
                    ) {
                        Text("Show more", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}


private fun searchServiceLabel(source: String): String? = when (source.uppercase()) {
    "SPOTIFY" -> "Spotify"
    "YOUTUBE_MUSIC", "YOUTUBE" -> "YouTube Music"
    "APPLE_MUSIC" -> "Apple Music"
    else -> null
}

/** A friend matched by name: avatar, name and how many playlists they have. */
@Composable
private fun SearchResultFriendItem(
    friend: SearchResultItem.FriendItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val count = friend.playlists.size
    val subtitle = listOfNotNull(
        if (count == 1) "1 playlist" else "$count playlists",
        searchServiceLabel(friend.source)
    ).joinToString(" \u00B7 ")
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClickLabel = "Show ${friend.name}'s playlists", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Friend ${friend.name}, $subtitle"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (friend.avatarUrl.isNullOrBlank()) {
                    Text(
                        text = friend.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    SmartImage(
                        model = friend.avatarUrl,
                        contentDescription = null,
                        targetSize = SmartImageListTargetSize,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A friend's playlists (saved and public), opened from a friend search result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFriendPlaylistsSheet(
    friend: SearchResultItem.FriendItem,
    openingPlaylistId: String?,
    onOpen: (Playlist) -> Unit,
    onPlay: (Playlist) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sheetScope = rememberCoroutineScope()
    val hideThen: (() -> Unit) -> Unit = { after ->
        sheetScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) { onDismiss(); after() }
        }
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "${friend.name}'s playlists",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .semantics { heading() }
            )
            if (friend.playlists.isEmpty()) {
                Text(
                    text = "No public playlists yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(friend.playlists, key = { it.id }) { playlist ->
                        Box(Modifier.animateItem()) {
                            SearchResultPlaylistItem(
                                playlist = playlist,
                                playlistSongs = emptyList(),
                                onOpenClick = { hideThen { onOpen(playlist) } },
                                onPlayClick = { onPlay(playlist) }
                            )
                            if (openingPlaylistId == playlist.id) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
