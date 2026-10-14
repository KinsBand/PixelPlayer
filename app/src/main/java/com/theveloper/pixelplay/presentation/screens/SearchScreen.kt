package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.rounded.Mic
import com.theveloper.pixelplay.presentation.components.voicesearch.VoiceCapturePhase
import com.theveloper.pixelplay.presentation.components.voicesearch.VoiceMicButton
import com.theveloper.pixelplay.presentation.components.voicesearch.VoiceSpeechCaptureState
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
import com.theveloper.pixelplay.ui.theme.MotionTokens
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
import com.theveloper.pixelplay.presentation.viewmodel.AlbumDetailViewModel
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
    collectionViewModel: com.theveloper.pixelplay.presentation.viewmodel.LibraryCollectionViewModel = hiltViewModel(),
    /** Header mic: opens voice search, or speaks again while it's open. */
    onVoiceSearchClick: () -> Unit = {},
    /** Non-null while voice search is open: the header shows the live transcript state. */
    voiceCapture: VoiceSpeechCaptureState? = null
) {
    val searchQuery = playerViewModel.searchQuery

    // A pasted Spotify / YouTube Music link (artist, album, song, playlist): it's read, then the
    // same item is searched for (filtered to its kind) and a card on top opens exactly it.
    val linkViewModel: com.theveloper.pixelplay.presentation.viewmodel.MediaLinkViewModel = hiltViewModel()
    val linkState by linkViewModel.state.collectAsStateWithLifecycle()
    val songSearchReaderEnabled by playerViewModel.songSearchReaderEnabled.collectAsStateWithLifecycle()
    LaunchedEffect(searchQuery, songSearchReaderEnabled) {
        // Off in setup / settings: links are searched as plain text.
        val parsed = if (songSearchReaderEnabled) com.theveloper.pixelplay.data.search.MediaUrlParser.parse(searchQuery) else null
        if (parsed != null) {
            linkViewModel.resolve(parsed)
        } else {
            val resolvedText = (linkViewModel.state.value as? com.theveloper.pixelplay.presentation.viewmodel.MediaLinkUiState.Resolved)
                ?.result?.searchText
            val keep = resolvedText != null && searchQuery.trim().equals(resolvedText.trim(), ignoreCase = true)
            if (!keep && linkViewModel.state.value !is com.theveloper.pixelplay.presentation.viewmodel.MediaLinkUiState.None) {
                linkViewModel.clear()
            }
        }
    }
    LaunchedEffect(linkState) {
        val resolved = linkState as? com.theveloper.pixelplay.presentation.viewmodel.MediaLinkUiState.Resolved ?: return@LaunchedEffect
        playerViewModel.submitSearch(resolved.result.searchText)
        playerViewModel.updateSearchFilter(resolved.result.filter)
    }
    val openLinkResult: (com.theveloper.pixelplay.data.search.LinkResolution) -> Unit = { result ->
        fun openSongsPage(title: String, cover: String?, songs: List<Song>, key: String) {
            val page = Playlist(
                id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}link:$key",
                name = title,
                songIds = songs.map { it.id },
                coverImageUri = cover
            )
            PlaylistViewModel.registerTransientPlaylist(page, songs)
            navController.navigateSafely(Screen.PlaylistDetail.createRoute(page.id))
        }
        when (result) {
            is com.theveloper.pixelplay.data.search.LinkResolution.Artist ->
                navController.navigateSafely(Screen.ArtistDetail.createRouteForName(result.name))
            is com.theveloper.pixelplay.data.search.LinkResolution.Album ->
                if (result.songs.isNotEmpty()) {
                    openSongsPage(result.title, result.coverUrl, result.songs, "album:" + result.title)
                } else {
                    navController.navigateSafely(Screen.AlbumDetail.createRouteForName(albumTitle = result.title, artistName = result.artist))
                }
            is com.theveloper.pixelplay.data.search.LinkResolution.Playlist ->
                if (result.songs.isNotEmpty()) openSongsPage(result.title, result.coverUrl, result.songs, "playlist:" + result.title)
            is com.theveloper.pixelplay.data.search.LinkResolution.Track ->
                result.song?.let { song -> playerViewModel.playSongs(listOf(song), song, song.title) }
        }
    }
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
    val focusManager = LocalFocusManager.current
    val queryFocusRequester = remember { FocusRequester() }
    var queryFieldFocused by remember { mutableStateOf(false) }
    val voiceActive = voiceCapture != null

    // The query is typed straight into the header (there is no search bar any more). Opening
    // Search puts the cursor there and brings the keyboard up, unless voice search opened it
    // (the microphone types the words in, so no keyboard).
    LaunchedEffect(Unit) {
        if (voiceActive) return@LaunchedEffect
        delay(380L) // let the navigation transition settle before the keyboard slides up
        runCatching { queryFocusRequester.requestFocus() }
        keyboardController?.show()
    }
    // Tapping the Search tab while already here: back to typing.
    val latestVoiceCapture by androidx.compose.runtime.rememberUpdatedState(voiceCapture)
    LaunchedEffect(playerViewModel) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            if (latestVoiceCapture == null) {
                runCatching { queryFocusRequester.requestFocus() }
                keyboardController?.show()
            }
        }
    }
    LaunchedEffect(voiceActive) {
        if (voiceActive) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }
    // Keyboard dismissed (Back / swipe): leave typing mode so the header stops showing the cursor.
    val imeDensity = LocalDensity.current
    val imeInsets = WindowInsets.ime
    // While typing the nav bar and mini player step aside, so lists only need to clear the
    // keyboard; otherwise they clear the nav bar + mini player.
    val imeBottomDp = with(imeDensity) { imeInsets.getBottom(imeDensity).toDp() }
    val contentBottomPadding = if (imeBottomDp > 0.dp) imeBottomDp + 16.dp
        else bottomBarHeightDp + MiniPlayerHeight + 24.dp
    // Typing strip (matching songs docked at the bottom): where it rests while the keyboard is
    // closed. Mirrors the mini player: above the nav bar (or just the system inset when scrolling
    // hid the bar), plus the mini player when a song is loaded, plus a small gap.
    val liveStripNavPart by animateDpAsState(
        targetValue = if (com.theveloper.pixelplay.presentation.navigation.NavBarVisibility.hiddenByScroll) systemNavBarInset
            else bottomBarHeightDp,
        animationSpec = tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized),
        label = "liveStripNav"
    )
    val liveStripRestBottom = liveStripNavPart +
        (if (stablePlayerState.currentSong != null) MiniPlayerHeight + com.theveloper.pixelplay.presentation.components.MiniPlayerBottomSpacer else 0.dp) +
        8.dp
    var liveStripExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(imeInsets) {
        var wasImeVisible = false
        androidx.compose.runtime.snapshotFlow { imeInsets.getBottom(imeDensity) > 0 }
            .collect { visible ->
                if (wasImeVisible && !visible && queryFieldFocused) focusManager.clearFocus()
                wasImeVisible = visible
            }
    }
    // Leaving the app drops the focus and the keyboard, so coming back doesn't act as if the
    // query were being typed in.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            com.theveloper.pixelplay.presentation.components.TabHeader(
                title = "Search",
                onSettingsClick = {
                    navController.navigateSafely(Screen.Settings.route)
                },
                titleContent = {
                    SearchQueryField(
                        query = searchQuery,
                        onQueryChange = { playerViewModel.updateSearchQuery(it) },
                        onSearch = {
                            playerViewModel.submitSearch()
                            keyboardController?.hide()
                        },
                        focused = queryFieldFocused,
                        onFocusChanged = { queryFieldFocused = it },
                        focusRequester = queryFocusRequester,
                        voiceCapture = voiceCapture
                    )
                },
                actions = {
                    SearchHeaderMicButton(
                        onClick = onVoiceSearchClick,
                        voiceCapture = voiceCapture
                    )
                }
            )

            // No tinted band under the header any more (shared TabHeader style).
            Column(
                modifier = Modifier.fillMaxSize()
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
                    LaunchedEffect(Unit) {
                        playerViewModel.loadSearchHistory()
                        collectionViewModel.onShelvesVisible()
                    }
                    // "Because you listened": two of your top artists (rotating every few hours),
                    // each with similar artists and searches for their mood / genre / era.
                    val discoveryViewModel: com.theveloper.pixelplay.presentation.viewmodel.SearchDiscoveryViewModel = hiltViewModel()
                    val discovery by discoveryViewModel.state.collectAsStateWithLifecycle()
                    val artistHubs = discovery.hubs
                    // Insights build in the background; don't show the skeleton forever if
                    // that never finishes (it can fail quietly). The cards' own network
                    // lookups time out after 8 s, so this only catches a stuck build.
                    var hubWaitOver by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(10_000L)
                        hubWaitOver = true
                    }
                    val hubsLoading = discovery.loading && !hubWaitOver
                    val fallbackQueries = listOf(
                        stringResource(R.string.search_hub_query_new_releases),
                        stringResource(R.string.search_hub_query_top_charts),
                        stringResource(R.string.search_hub_query_trending)
                    )
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
                        artistHubs = artistHubs,
                        hubsLoading = hubsLoading,
                        fallbackQueries = fallbackQueries,
                        onArtistClick = { artist ->
                            navController.navigateSafely(
                                Screen.ArtistDetail.createRouteForArtist(
                                    artistId = artist.id,
                                    artistName = artist.name,
                                    isLibraryArtist = true
                                )
                            )
                        },
                        bottomPadding = contentBottomPadding,
                        onQuery = { query -> playerViewModel.submitSearch(query) },
                        onDeleteHistory = { query -> playerViewModel.deleteSearchHistoryItem(query) },
                        onClearHistory = { playerViewModel.clearSearchHistory() },
                        onNewReleases = { openLibraryTab("ARTIST") },
                        // The card is "Live events" now (your top artists' upcoming shows).
                        onCharts = { navController.navigateSafely(Screen.LiveEvents.route) },
                        onGenresAndMoods = { openLibraryTab("GENRES") }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // A pasted link: what it is, and one tap to open exactly that.
                        if (linkState !is com.theveloper.pixelplay.presentation.viewmodel.MediaLinkUiState.None) {
                            com.theveloper.pixelplay.presentation.screens.search.components.MediaLinkCard(
                                state = linkState,
                                onOpen = openLinkResult,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        // Typing (not entered yet): live songs + suggestions drop in from the top.
                        // Entered (keyboard Search / a suggestion): the full results below.
                        val liveMode = !anySelectionMode &&
                            searchQuery.trim() != playerViewModel.submittedSearchQuery
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
                            // Docked under the header (the results scroll below it, never under
                            // it). Each chip shows how many results of its kind are loaded.
                            val chipPaging by playerViewModel.searchSongPaging.collectAsStateWithLifecycle()
                            val filterCounts = remember(searchQuery.trim()) {
                                androidx.compose.runtime.mutableStateMapOf<SearchFilterType, Int>()
                            }
                            LaunchedEffect(searchResults, currentFilter) {
                                if (searchResults.isEmpty()) return@LaunchedEffect
                                val counts = searchResultCounts(searchResults)
                                if (currentFilter == SearchFilterType.ALL) {
                                    counts.forEach { (kind, count) -> filterCounts[kind] = count }
                                } else {
                                    filterCounts[currentFilter] = counts[currentFilter] ?: 0
                                }
                            }
                            // While typing (not entered yet) the chips fold away; they come back
                            // once the search is entered.
                            val chipsReducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !liveMode,
                                enter = if (chipsReducedMotion) androidx.compose.animation.EnterTransition.None else
                                    androidx.compose.animation.expandVertically(
                                        tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate),
                                        expandFrom = Alignment.Top
                                    ) + fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                                exit = if (chipsReducedMotion) androidx.compose.animation.ExitTransition.None else
                                    androidx.compose.animation.shrinkVertically(
                                        tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate),
                                        shrinkTowards = Alignment.Top
                                    ) + fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)),
                                label = "search_filter_chips"
                            ) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(SearchFilterType.entries.toList(), key = { it.name }) { filter ->
                                        SearchFilterChip(
                                            filterType = filter,
                                            currentFilter = currentFilter,
                                            playerViewModel = playerViewModel,
                                            count = filterCounts[filter]?.takeIf { filter != SearchFilterType.ALL && it > 0 },
                                            hasMore = filter == currentFilter && chipPaging.hasMore
                                        )
                                    }
                                }
                            }
                        }
                        val searchHint = remember(searchQuery) { MusicSearchQuery.parse(searchQuery).hint }
                        searchHint?.let { hint ->
                            Text(hint, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                        AnimatedContent(
                            targetState = liveMode,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(durationMillis = 260, delayMillis = 40)) +
                                    slideInVertically(animationSpec = tween(durationMillis = 300)) { -it / 8 }) togetherWith
                                    fadeOut(animationSpec = tween(durationMillis = 140))
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "search_live_mode"
                        ) { live ->
                        if (live) {
                            val liveHistory by remember(playerViewModel) {
                                playerViewModel.playerUiState.map { it.searchHistory }.distinctUntilChanged()
                            }.collectAsStateWithLifecycle(initialValue = persistentListOf())
                            val liveSongs = remember(searchResults) {
                                searchResults.filterIsInstance<SearchResultItem.SongItem>().map { it.song }.take(24)
                            }
                            // Online autocomplete (YouTube Music) for what's typed, spelling-corrected.
                            // Only used while it belongs to the current text; until the new list
                            // arrives the local completions fill in, so the list never blanks.
                            val querySuggestions by playerViewModel.searchQuerySuggestions.collectAsStateWithLifecycle()
                            val onlineSuggestions = if (querySuggestions.query == searchQuery.trim()) {
                                querySuggestions.suggestions
                            } else emptyList()
                            val liveSuggestions = remember(searchQuery, searchResults, liveHistory, onlineSuggestions) {
                                com.theveloper.pixelplay.presentation.screens.search.components.buildLiveSuggestions(
                                    query = searchQuery,
                                    results = searchResults,
                                    history = liveHistory,
                                    online = onlineSuggestions
                                )
                            }
                            // Add Song from the lyrics screen: typing-phase taps open the action sheet too.
                            val liveSongTap = com.theveloper.pixelplay.presentation.components.LocalSongPrimaryTap.current
                            com.theveloper.pixelplay.presentation.screens.search.components.LiveSearchContent(
                                query = searchQuery,
                                songs = liveSongs,
                                suggestions = liveSuggestions,
                                currentSongId = stablePlayerState.currentSong?.id,
                                onSongClick = { song ->
                                    val queueName = "Search: ${searchQuery.trim()}"
                                    if (liveSongTap != null) {
                                        liveSongTap.onSongTap(song, liveSongs, queueName)
                                    } else {
                                        // A song tapped in Search plays with a mix of songs like it
                                        // (not the rest of the search results).
                                        playerViewModel.startSongRadio(song, openQueue = false)
                                        playerViewModel.onSearchQuerySubmitted(searchQuery.trim())
                                    }
                                },
                                onSongLongClick = handleSongMoreOptionsClick,
                                onSuggestionClick = { suggestion ->
                                    keyboardController?.hide()
                                    playerViewModel.submitSearch(suggestion.query)
                                },
                                onSuggestionFill = { suggestion ->
                                    playerViewModel.updateSearchQuery(suggestion.query + " ")
                                },
                                restBottom = liveStripRestBottom,
                                stripExpanded = liveStripExpanded,
                                onStripExpandedChange = { liveStripExpanded = it }
                            )
                        } else
                        Box(modifier = Modifier.fillMaxSize()) {
                            Crossfade(
                                targetState = searchResults.isEmpty(),
                                animationSpec = tween(durationMillis = 190),
                                label = "search_results_fade"
                            ) { isEmpty ->
                                val searchInProgress by playerViewModel.searchInProgress.collectAsStateWithLifecycle()
                                if (isEmpty && searchInProgress && searchQuery.isNotBlank()) {
                                    com.theveloper.pixelplay.presentation.screens.search.components.SearchResultsSkeleton(
                                        showHero = currentFilter == SearchFilterType.ALL
                                    )
                                } else if (isEmpty) {
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

        val infoSongTap = com.theveloper.pixelplay.presentation.components.LocalSongPrimaryTap.current
        if (currentSong != null) {
            SongInfoBottomSheet(
                onSelect = { multiSelectionState.toggleSelection(currentSong) },
                onStartSongRadio = { seedSong -> playerViewModel.startSongRadio(seedSong) },
                song = currentSong,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    if (infoSongTap != null) {
                        // During Add Song, "Play" offers Play / Next / Soon / Queue like a tap does.
                        showSongInfoBottomSheet = false
                        infoSongTap.onSongTap(currentSong, listOf(currentSong), "Search")
                    } else {
                        playerViewModel.showAndPlaySong(currentSong)
                    }
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                },
                // Hold Next → Soon (the "after which song" slider).
                soonPlayer = playerViewModel,
                onAddSoon = { k -> playerViewModel.addSongAfterUpcoming(currentSong, k) },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    Screen.AlbumDetail.createRouteForSong(currentSong)?.let { route ->
                        navController.navigateSafelyReplacing(
                            route = route,
                            patternToPop = Screen.AlbumDetail.route
                        )
                    }
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
            onDelete = { playlists ->
                playlistViewModel.deletePlaylistsInBatch(playlists.map { it.id })
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            onMerge = {
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            // One playlist: a single .m3u; several: a .zip of .m3u files.
            onShareFiles = { playlists ->
                activity?.let {
                    playlistViewModel.shareSelectedPlaylistsAsZip(playlists.map { p -> p.id }, it)
                }
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            // Queue / Next / Soon: the active playlists as one block, in the order shown.
            onQueue = { playlists, placement ->
                scope.launch {
                    val songs = playlistViewModel.getPlaylistsWithSongs(playlists.map { it.id }).flatMap { it.second }
                    val name = when (playlists.size) {
                        1 -> playlists.first().name
                        2 -> playlists.joinToString(" + ") { it.name }
                        else -> "${playlists.size} playlists"
                    }
                    if (songs.isNotEmpty()) playerViewModel.queuePlaylist(songs, name, playlists.singleOrNull()?.id, placement)
                }
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            // Soon's shuffle: the same block, in a random order.
            onQueueShuffled = { playlists, placement ->
                scope.launch {
                    val songs = playlistViewModel.getPlaylistsWithSongs(playlists.map { it.id }).flatMap { it.second }
                    val name = when (playlists.size) {
                        1 -> playlists.first().name
                        2 -> playlists.joinToString(" + ") { it.name }
                        else -> "${playlists.size} playlists"
                    }
                    if (songs.isNotEmpty()) {
                        playerViewModel.queuePlaylist(songs, name, playlists.singleOrNull()?.id, placement, shuffled = true)
                    }
                }
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            // Play: the active playlists as one queue, in the order shown, from the first song.
            onPlay = { playlists ->
                scope.launch {
                    val songs = playlistViewModel.getPlaylistsWithSongs(playlists.map { it.id }).flatMap { it.second }
                    val name = when (playlists.size) {
                        1 -> playlists.first().name
                        2 -> playlists.joinToString(" + ") { it.name }
                        else -> "${playlists.size} playlists"
                    }
                    songs.firstOrNull()?.let { first -> playerViewModel.playSongs(songs, first, name, playlists.singleOrNull()?.id) }
                }
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            // Download: .m3u files into Music/PixelPlayer Exports (the view model toasts the result).
            onExport = { playlists ->
                playlistViewModel.exportPlaylistsAsM3u(playlists.map { it.id })
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            playerViewModel = playerViewModel,
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
                is SearchResultItem.ProfileItem -> SearchFilterType.PROFILES
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
                // Plays the song with a mix of songs like it, not the whole search as a queue.
                playerViewModel.startSongRadio(song, openQueue = false)
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

    var openingRemoteAlbumId by remember { mutableStateOf<String?>(null) }
    fun openRemoteAlbum(browseId: String, album: Album, play: Boolean) {
        if (openingRemoteAlbumId != null) return
        openingRemoteAlbumId = browseId
        remotePlaylistScope.launch {
            val songs = try {
                remotePlaylistLoader.loadAllSongs(browseId)
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                openingRemoteAlbumId = null
                throw cancelled
            } catch (e: Exception) {
                emptyList()
            }
            openingRemoteAlbumId = null
            if (songs.isEmpty()) {
                playerViewModel.sendToast("Couldn't load this album")
                return@launch
            }
            val streamId = com.theveloper.pixelplay.data.library.CollectionKeys.streamAlbumId(
                com.theveloper.pixelplay.data.library.CollectionKeys.albumKey(album.artist, album.title)
            )
            val albumSongs = songs.mapIndexed { index, song ->
                song.copy(
                    album = album.title,
                    albumId = streamId,
                    albumArtist = album.albumArtist ?: album.artist,
                    albumArtUriString = song.albumArtUriString ?: album.albumArtUriString,
                    trackNumber = if (song.trackNumber > 0) song.trackNumber else (index + 1)
                )
            }
            AlbumDetailViewModel.registerTransientAlbum(browseId, album, albumSongs)
            if (play) {
                playerViewModel.playSongs(albumSongs, albumSongs.first(), album.title)
            } else {
                navController.navigateSafelyReplacing(
                    route = Screen.AlbumDetail.createRouteForBrowse(
                        browseId = browseId,
                        title = album.title,
                        artist = album.artist,
                        artUrl = album.albumArtUriString,
                        year = album.year
                    ),
                    patternToPop = Screen.AlbumDetail.route
                )
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
        // Profiles only show on All for a pasted profile link / spotify:user: URI.
        SearchFilterType.ALL -> listOf(SearchFilterType.SONGS, SearchFilterType.ALBUMS, SearchFilterType.PLAYLISTS, SearchFilterType.PROFILES)
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
    // Typing: the nav bar and mini player hide, so only the keyboard needs clearing.
    // Otherwise: clear the nav bar + mini player.
    val miniPlayerSpace = if (playerStableState.currentSong != null) MiniPlayerHeight else 0.dp
    val imeFullBottom = with(localDensity) { WindowInsets.ime.getBottom(localDensity).toDp() }
    val resultsBottomPadding = if (imeFullBottom > 0.dp) imeFullBottom + 8.dp
        else navBarOccupiedHeight + miniPlayerSpace + 8.dp

    // Every search (and filter change) starts at the top. Results arrive in pieces, and a lazy
    // list keeps its first visible item anchored, so sections landing above it (the artist
    // shelf, songs) would otherwise end up scrolled out of view.
    val resultsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hasResults = results.isNotEmpty()
    // Infinite scroll on every filter: within 3 rows of the end, the next page loads by itself.
    val searchPaging by playerViewModel.searchSongPaging.collectAsStateWithLifecycle()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = resultsListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, searchPaging.hasMore, searchPaging.isLoading) {
        if (shouldLoadMore && !searchPaging.isLoading && !searchPaging.failed && searchPaging.hasMore) {
            playerViewModel.loadMoreSearchResults()
        }
    }
    // People results (Profiles): follow state from the Friends view model.
    val socialViewModel: com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel = hiltViewModel()
    val myFollowingIds by socialViewModel.myFollowingIds.collectAsStateWithLifecycle()
    val mutualIds by socialViewModel.mutualFriendIds.collectAsStateWithLifecycle()
    val confirmUnfollow = com.theveloper.pixelplay.presentation.components.rememberUnfollowConfirmation(socialViewModel, mutualIds)
    LaunchedEffect(searchQuery, currentFilter, hasResults) {
        resultsListState.scrollToItem(0)
    }

    // Songs whose "+N versions" list is open (reset per query).
    var expandedVersions by remember(searchQuery) { mutableStateOf(emptySet<String>()) }
    // "Added to queue" pill after a swipe; the key restarts its timer on every swipe.
    var queueFeedback by remember { mutableStateOf<String?>(null) }
    var queueFeedbackKey by remember { mutableStateOf(0) }
    LaunchedEffect(queueFeedbackKey) {
        if (queueFeedback != null) {
            delay(1_600L)
            queueFeedback = null
        }
    }
    val playNextMessage = stringResource(R.string.search_added_play_next)
    val addedToQueueMessage = stringResource(R.string.search_added_to_queue)
    // Song whose Soon slider is open under it (swiped right past Next); one at a time.
    var soonOpenFor by rememberSaveable(searchQuery) { mutableStateOf<String?>(null) }
    LaunchedEffect(isSelectionMode) { if (isSelectionMode) soonOpenFor = null }
    val resources = LocalContext.current.resources
    // Top result: an artist / song / album the query names (almost) exactly. All tab only.
    val topResult = remember(results, searchQuery, currentFilter) {
        if (currentFilter != SearchFilterType.ALL) null
        else com.theveloper.pixelplay.data.search.SearchTopResult.pick(
            MusicSearchQuery.parse(searchQuery).terms.ifBlank { searchQuery },
            results
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = resultsListState,
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
            bottom = resultsBottomPadding
        )
    ) {
        topResult?.let { pick ->
            item(key = "top_result", contentType = "search_top_result") {
                val topItem = pick.item
                Box(modifier = Modifier.animateItem().padding(bottom = 12.dp)) {
                    when (topItem) {
                        is SearchResultItem.ArtistItem -> {
                            val artist = topItem.artist
                            val openArtist = {
                                navController.navigateSafelyReplacing(
                                    route = Screen.ArtistDetail.createRouteForArtist(
                                        artistId = artist.id,
                                        artistName = artist.name,
                                        isLibraryArtist = topItem.browseId == null
                                    ),
                                    patternToPop = Screen.ArtistDetail.route
                                )
                                onItemSelected()
                            }
                            com.theveloper.pixelplay.presentation.screens.search.components.SearchTopResultCard(
                                kind = com.theveloper.pixelplay.presentation.screens.search.components.TopResultKind.ARTIST,
                                title = artist.name,
                                subtitle = stringResource(R.string.search_top_result_artist_subtitle),
                                imageModel = artist.effectiveImageUrl,
                                onClick = openArtist,
                                onPlay = {
                                    val browseId = topItem.browseId
                                    if (browseId == null) {
                                        playerViewModel.playArtist(artist)
                                        onItemSelected()
                                    } else {
                                        // Online artist: play their songs from these results,
                                        // or open their quick-play sheet if none are here.
                                        val main = com.theveloper.pixelplay.data.library.RecordingKeys.primaryArtist(artist.name)
                                        val theirs = songResultsQueue.filter {
                                            com.theveloper.pixelplay.data.library.RecordingKeys.primaryArtist(it.artist) == main
                                        }
                                        if (theirs.isNotEmpty()) {
                                            playerViewModel.playSongs(theirs, theirs.first(), artist.name)
                                            onItemSelected()
                                        } else {
                                            remoteCollection = browseId to artist.name
                                            onItemSelected()
                                        }
                                    }
                                }
                            )
                        }
                        is SearchResultItem.SongItem -> {
                            val song = topItem.song
                            com.theveloper.pixelplay.presentation.screens.search.components.SearchTopResultCard(
                                kind = com.theveloper.pixelplay.presentation.screens.search.components.TopResultKind.SONG,
                                title = song.title,
                                subtitle = stringResource(R.string.search_top_result_song_subtitle, song.displayArtist),
                                imageModel = song.albumArtUriString,
                                onClick = { onSongResultClick(song) },
                                onPlay = { onSongResultClick(song) }
                            )
                        }
                        is SearchResultItem.AlbumItem -> {
                            val album = topItem.album
                            val openAlbum = {
                                val browseId = topItem.browseId
                                if (browseId != null) {
                                    navController.navigateSafelyReplacing(
                                        route = Screen.AlbumDetail.createRouteForBrowse(
                                            browseId = browseId,
                                            title = album.title,
                                            artist = album.artist,
                                            artUrl = album.albumArtUriString,
                                            year = album.year
                                        ),
                                        patternToPop = Screen.AlbumDetail.route
                                    )
                                } else {
                                    navController.navigateSafelyReplacing(
                                        route = Screen.AlbumDetail.createRoute(album.id),
                                        patternToPop = Screen.AlbumDetail.route
                                    )
                                }
                                onItemSelected()
                            }
                            com.theveloper.pixelplay.presentation.screens.search.components.SearchTopResultCard(
                                kind = com.theveloper.pixelplay.presentation.screens.search.components.TopResultKind.ALBUM,
                                title = album.title,
                                subtitle = stringResource(R.string.search_top_result_album_subtitle, album.artist),
                                imageModel = album.albumArtUriString,
                                onClick = openAlbum,
                                onPlay = {
                                    val browseId = topItem.browseId
                                    if (browseId != null) {
                                        openRemoteAlbum(browseId, album, play = true)
                                    } else {
                                        playerViewModel.playAlbum(album)
                                    }
                                    onItemSelected()
                                }
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
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
                            SearchFilterType.PROFILES -> "Profiles"
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
                            is SearchResultItem.ProfileItem -> "profile_${item.person.userId}"
                        }
                    },
                    contentType = { index ->
                        when (itemsForSection[index]) {
                            is SearchResultItem.SongItem -> "search_song"
                            is SearchResultItem.AlbumItem -> "search_album"
                            is SearchResultItem.ArtistItem -> "search_artist"
                            is SearchResultItem.PlaylistItem -> "search_playlist"
                            is SearchResultItem.FriendItem -> "search_friend"
                            is SearchResultItem.ProfileItem -> "search_profile"
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
                                val versionsOpen = item.song.id in expandedVersions
                                Column {
                                    // Swipe right: Next, further: Soon (slider under the song). Swipe left: Queue.
                                    com.theveloper.pixelplay.presentation.components.SwipeToQueueRow(
                                        enabled = !isSelectionMode,
                                        onPlayNext = {
                                            playerViewModel.addSongNextToQueue(item.song)
                                            queueFeedback = playNextMessage
                                            queueFeedbackKey++
                                        },
                                        onAddToQueue = {
                                            playerViewModel.addSongToQueue(item.song)
                                            queueFeedback = addedToQueueMessage
                                            queueFeedbackKey++
                                        },
                                        onSoon = { soonOpenFor = item.song.id }
                                    ) {
                                        // Typed val: an `if (…) null else @Composable { }` argument
                                        // is parsed as an annotated block (Unit), not a lambda.
                                        val versionsPill: @Composable () -> Unit = {
                                            com.theveloper.pixelplay.presentation.screens.search.components.SearchVersionsPill(
                                                count = item.versions.size,
                                                songTitle = item.song.title,
                                                expanded = versionsOpen,
                                                onToggle = {
                                                    expandedVersions = if (versionsOpen) expandedVersions - item.song.id
                                                    else expandedVersions + item.song.id
                                                }
                                            )
                                        }
                                        EnhancedSongListItem(
                                            song = item.song,
                                            isPlaying = isPlaying,
                                            isCurrentSong = currentPlayingSongId == item.song.id,
                                            onMoreOptionsClick = onSongMoreOptionsClick,
                                            onClick = { onSongResultClick(item.song) },
                                            isSelected = isSelected,
                                            selectionIndex = selectionIndex,
                                            isSelectionMode = isSelectionMode,
                                            onLongPress = { onSongLongPress(item.song) },
                                            belowSubtitle = versionsPill.takeIf { item.versions.isNotEmpty() }
                                        )
                                    }
                                    com.theveloper.pixelplay.presentation.components.InlineSoonPanel(
                                        visible = soonOpenFor == item.song.id && !isSelectionMode,
                                        song = { item.song },
                                        playerViewModel = playerViewModel,
                                        onClose = { soonOpenFor = null },
                                        onConfirm = { k ->
                                            // The pill below says where it went (no toast on top).
                                            playerViewModel.addSongAfterUpcoming(item.song, k, announce = false)
                                            soonOpenFor = null
                                            queueFeedback = if (k <= 0) playNextMessage
                                                else resources.getQuantityString(R.plurals.search_swipe_soon_after, k, k)
                                            queueFeedbackKey++
                                        }
                                    )
                                    if (item.versions.isNotEmpty()) {
                                        com.theveloper.pixelplay.presentation.screens.search.components.SearchVersionsList(
                                            expanded = versionsOpen && !isSelectionMode,
                                            canonical = item.song,
                                            versions = item.versions,
                                            currentSongId = currentPlayingSongId,
                                            onPlay = { version ->
                                                // Same search queue, with this version in the song's place.
                                                val versionQueue = songResultsQueue.map { if (it.id == item.song.id) version else it }
                                                    .ifEmpty { listOf(version) }
                                                if (songTap != null) {
                                                    songTap.onSongTap(version, versionQueue, searchQueueName)
                                                } else {
                                                    playerViewModel.startSongRadio(version, openQueue = false)
                                                    onItemSelected()
                                                }
                                            },
                                            onOptions = onSongMoreOptionsClick
                                        )
                                    }
                                }
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
                                    item.browseId,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        val browseId = item.browseId
                                        if (browseId != null) {
                                            navController.navigateSafelyReplacing(
                                                route = Screen.AlbumDetail.createRouteForBrowse(
                                                    browseId = browseId,
                                                    title = item.album.title,
                                                    artist = item.album.artist,
                                                    artUrl = item.album.albumArtUriString,
                                                    year = item.album.year
                                                ),
                                                patternToPop = Screen.AlbumDetail.route
                                            )
                                        } else {
                                            navController.navigateSafelyReplacing(
                                                route = Screen.AlbumDetail.createRoute(item.album.id),
                                                patternToPop = Screen.AlbumDetail.route
                                            )
                                        }
                                        onItemSelected()
                                    }
                                }
                                val isSelected = selectedAlbums.any { it.id == item.album.id }
                                val selectionIndex = getAlbumSelectionIndex(item.album.id)
                                SearchResultAlbumItem(
                                    album = item.album,
                                    onPlayClick = {
                                        if (item.browseId != null) {
                                            openRemoteAlbum(item.browseId, item.album, play = true)
                                        } else {
                                            onPlayClick()
                                        }
                                    },
                                    onOpenClick = onOpenClick,
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

                            // A Spotify person: Follow / Following / Friends right here.
                            is SearchResultItem.ProfileItem -> {
                                val person = item.person
                                com.theveloper.pixelplay.presentation.components.SocialPersonCard(
                                    person = person,
                                    isMe = person.userId == socialViewModel.myUserId,
                                    following = person.userId in myFollowingIds,
                                    mutual = person.friendId in mutualIds,
                                    onToggleFollow = { follow ->
                                        if (follow) socialViewModel.setPersonFollowing(person, true) else confirmUnfollow(person)
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        // Next page: loads by itself near the end (spinner), or offers a retry if it failed.
        item(key = "search_paging_footer", contentType = "search_show_more") {
            SearchPagingFooter(
                playerViewModel = playerViewModel,
                modifier = Modifier.animateItem()
            )
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
    com.theveloper.pixelplay.presentation.screens.search.components.SearchQueueFeedbackPill(
        message = queueFeedback,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = resultsBottomPadding + 8.dp)
    )
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
    modifier: Modifier = Modifier,
    /** Results of this kind loaded for the query; null = don't show a count. */
    count: Int? = null,
    /** More can load (shown as "24+"). */
    hasMore: Boolean = false
) {
    val selected = filterType == currentFilter

    val labelResId = when (filterType) {
        SearchFilterType.ALL -> R.string.common_all
        SearchFilterType.SONGS -> R.string.library_tab_songs
        SearchFilterType.ALBUMS -> R.string.library_tab_albums
        SearchFilterType.ARTISTS -> R.string.library_tab_artists
        SearchFilterType.PLAYLISTS -> R.string.library_tab_playlists
        SearchFilterType.PROFILES -> R.string.search_filter_profiles
    }

    val name = stringResource(labelResId)
    val label = when {
        count == null -> name
        hasMore -> stringResource(R.string.search_filter_with_count_more, name, count)
        else -> stringResource(R.string.search_filter_with_count, name, count)
    }
    // Switching filters shows what's already loaded for that kind at once
    // (SearchStateHolder seeds it) while its own search refines it.
    com.theveloper.pixelplay.presentation.screens.search.components.SearchCountFilterChip(
        label = label,
        selected = selected,
        onClick = { playerViewModel.updateSearchFilter(filterType) },
        modifier = modifier
    )
}

/** How many rows of each kind [results] holds (songs count once per folded family). */
private fun searchResultCounts(results: List<SearchResultItem>): Map<SearchFilterType, Int> {
    val counts = HashMap<SearchFilterType, Int>()
    results.forEach { item ->
        val kind = when (item) {
            is SearchResultItem.SongItem -> SearchFilterType.SONGS
            is SearchResultItem.AlbumItem -> SearchFilterType.ALBUMS
            is SearchResultItem.ArtistItem -> SearchFilterType.ARTISTS
            is SearchResultItem.PlaylistItem -> SearchFilterType.PLAYLISTS
            is SearchResultItem.ProfileItem -> SearchFilterType.PROFILES
            is SearchResultItem.FriendItem -> return@forEach
        }
        counts[kind] = (counts[kind] ?: 0) + 1
    }
    SearchFilterType.entries.forEach { if (it != SearchFilterType.ALL) counts.putIfAbsent(it, 0) }
    return counts
}


/**
 * The Search header is the search field: there is no separate bar. Idle and empty it reads
 * "Search" like every other tab title; tap it (or open Search) and it becomes the live query
 * with a cursor, a small status label above ("Typing" / "Results for" / "Listening…") and an
 * underline that grows in while typing, so it's always clear the header is taking input.
 */
@Composable
private fun SearchQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    voiceCapture: VoiceSpeechCaptureState?
) {
    val colors = MaterialTheme.colorScheme
    val voicePhase = voiceCapture?.phase
    val voiceLive = voicePhase == VoiceCapturePhase.Listening || voicePhase == VoiceCapturePhase.Hearing
    val voiceProblem = voicePhase == VoiceCapturePhase.Error || voicePhase == VoiceCapturePhase.Unavailable
    val hasQuery = query.isNotBlank()
    // Big "Search" title only when idle and empty; otherwise the query size.
    val showBigTitle = !focused && !hasQuery && voiceCapture == null

    val label: String? = when {
        voiceProblem -> voiceCapture?.errorMessage?.substringBefore(".") ?: "Didn't catch that"
        voiceLive -> "Listening\u2026"
        voicePhase == VoiceCapturePhase.Processing -> "Finding it\u2026"
        voicePhase == VoiceCapturePhase.NeedsPermission -> "Allow microphone access"
        voicePhase == VoiceCapturePhase.Idle && !hasQuery -> "Tap the mic to speak"
        focused -> "Typing"
        hasQuery -> "Results for"
        else -> null
    }

    val reducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    val sizeSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> = if (reducedMotion) {
        androidx.compose.animation.core.snap<Float>()
    } else {
        tween<Float>(
            com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium2,
            easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate
        )
    }
    val fontSizeSp by animateFloatAsState(
        targetValue = if (showBigTitle) 32f else 28f, // headlineLarge idle, like every tab title
        animationSpec = sizeSpec,
        label = "search_field_size"
    )
    val underlineFraction by animateFloatAsState(
        targetValue = if (focused || voiceLive) 1f else 0f,
        animationSpec = sizeSpec,
        label = "search_field_underline"
    )

    Column(
        modifier = Modifier
            .padding(end = 4.dp)
            .fillMaxWidth()
            .semantics { heading() }
    ) {
        androidx.compose.animation.AnimatedVisibility(visible = label != null) {
            var lastLabel by remember { mutableStateOf(label ?: "") }
            androidx.compose.runtime.SideEffect { if (label != null) lastLabel = label }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (focused || voiceLive) {
                    // A small live dot next to the label marks input mode.
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(6.dp)
                            .background(if (voiceLive) colors.tertiary else colors.primary, CircleShape)
                    )
                }
                Text(
                    text = label ?: lastLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (voiceProblem) colors.error
                        else if (focused || voiceLive) colors.primary
                        else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = colors.primary,
                    fontSize = fontSizeSp.sp,
                    fontFamily = com.theveloper.pixelplay.ui.theme.GoogleSansRounded,
                    fontWeight = FontWeight.ExtraBold
                ),
                cursorBrush = SolidColor(colors.primary),
                // One line that scrolls sideways as you type; the header never grows taller.
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = if (showBigTitle) "Search" else stringResource(R.string.search_placeholder),
                                fontFamily = com.theveloper.pixelplay.ui.theme.GoogleSansRounded,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = fontSizeSp.sp,
                                letterSpacing = 0.sp,
                                color = if (showBigTitle) colors.primary else colors.primary.copy(alpha = 0.38f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.search_cd_clear_search_query),
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Typing-mode underline: grows in from the start while the header takes input.
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .fillMaxWidth(underlineFraction.coerceIn(0f, 1f))
                .height(3.dp)
                .graphicsLayer { alpha = underlineFraction }
                .background(
                    if (voiceLive) colors.tertiary else colors.primary,
                    RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * Mic next to Settings in the Search header. Idle it matches the Settings button; while voice
 * search is listening it fills and ripples with the voice level.
 */
@Composable
private fun SearchHeaderMicButton(
    onClick: () -> Unit,
    voiceCapture: VoiceSpeechCaptureState?
) {
    val phase = voiceCapture?.phase
    val live = phase == VoiceCapturePhase.Listening || phase == VoiceCapturePhase.Hearing
    val problem = phase == VoiceCapturePhase.Error || phase == VoiceCapturePhase.Unavailable
    // Same round 56 dp style as the settings button next to it (TabSettingsButton).
    val dark = com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme.current
    val circle = if (dark) MaterialTheme.colorScheme.surfaceContainerLowest
        else MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(circle, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        VoiceMicButton(
            onClick = onClick,
            listening = live,
            level = { voiceCapture?.level ?: 0f },
            processing = phase == VoiceCapturePhase.Processing,
            error = problem,
            size = 56.dp,
            iconSize = 24.dp,
            contentDescription = if (voiceCapture != null) "Speak again" else "Voice search"
        )
    }
}


/**
 * The end of the search results: a 24 dp spinner while the next page loads by itself, or a
 * "Couldn't load more – Retry" pill if it failed. Nothing once there's no more.
 */
@Composable
private fun SearchPagingFooter(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val paging by playerViewModel.searchSongPaging.collectAsStateWithLifecycle()
    val reducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    val visible = paging.isLoading || paging.failed
    val view = androidx.compose.ui.platform.LocalView.current
    // Announce how many results arrived once a load finishes.
    LaunchedEffect(paging.pagesLoaded) {
        if (paging.pagesLoaded > 0 && paging.lastLoadedCount > 0) {
            view.announceForAccessibility("${paging.lastLoadedCount} more results loaded")
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
            if (paging.failed && !paging.isLoading) {
                com.theveloper.pixelplay.presentation.components.LoadMoreRetryPill(
                    onClick = { playerViewModel.loadMoreSearchResults() }
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .semantics { contentDescription = "Loading more results" },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
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

