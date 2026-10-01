package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.library.MusicVibeFilters
import com.theveloper.pixelplay.presentation.library.VibeCategory
import com.theveloper.pixelplay.presentation.library.VibeFilter
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.YourMusicViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar

const val YOUR_MUSIC_QUEUE_NAME = "Your Music"

/**
 * Liked Songs + All Songs in one place, laid out like a playlist: a header bar (back, and search
 * in the top-right), the hero (cover, counts, Shuffle), then the filter row pinned directly above
 * the first song. Each recording shows once; its other versions open as a tree under it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun YourMusicScreen(
    playerViewModel: PlayerViewModel,
    navController: NavController,
    onBackClick: () -> Unit,
    viewModel: YourMusicViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val controls = state.controls
    val songs = state.songs
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var showCustomFilterDialog by rememberSaveable { mutableStateOf(false) }
    var songForOptions by remember { mutableStateOf<Song?>(null) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    // Search lives behind the icon in the top-right of the header; open while there's a query.
    var isSearchOpen by rememberSaveable { mutableStateOf(controls.query.isNotEmpty()) }
    // The field owns its text so typing never waits on the (async) list rebuild; the list
    // follows shortly after typing pauses.
    var searchText by rememberSaveable { mutableStateOf(controls.query) }
    LaunchedEffect(searchText) {
        if (searchText.isNotEmpty()) kotlinx.coroutines.delay(180)
        viewModel.setQuery(searchText)
    }
    // Bottom nav hides while searching and comes back when search closes or the screen leaves.
    LaunchedEffect(isSearchOpen) {
        com.theveloper.pixelplay.presentation.navigation.NavBarVisibility.hiddenByScreen = isSearchOpen
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { com.theveloper.pixelplay.presentation.navigation.NavBarVisibility.hiddenByScreen = false }
    }
    androidx.activity.compose.BackHandler(enabled = isSearchOpen) {
        isSearchOpen = false
        searchText = ""
        focusManager.clearFocus()
    }
    // Families whose versions tree is open.
    var expandedFamilies by rememberSaveable { mutableStateOf(emptyList<String>()) }

    // Only one filter can be on; its card explains the vibe and offers a fresh mix.
    val activeMix = state.activeMix
    val songKeys = remember(songs) { uniqueSongKeys(songs) }

    val queueName = activeMix?.let { "${it.label} mix" } ?: YOUR_MUSIC_QUEUE_NAME

    fun shuffleAll() {
        if (songs.isEmpty()) return
        if (activeMix != null) {
            // A vibe mix is already ordered (artists spread out, best matches first), so it
            // plays in that order, then carries on as a mix locked to the same vibe.
            playerViewModel.playVibeMix(songs, activeMix)
        } else {
            playerViewModel.playSongsShuffled(songsToPlay = songs, queueName = queueName, startAtZero = true)
        }
    }

    fun play(song: Song) {
        focusManager.clearFocus()
        playerViewModel.playSongs(songs, song, queueName)
    }

    fun openOptions(song: Song) {
        playerViewModel.selectSongForInfo(song)
        songForOptions = song
    }

    val isHeaderCollapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val colors = MaterialTheme.colorScheme
    val topBarColor by animateColorAsState(
        targetValue = if (isHeaderCollapsed || isSearchOpen) colors.surfaceContainer else colors.primaryContainer,
        animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
        label = "yourMusicTopBar"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        // ---- Header bar: back (left), title once scrolled, search (right) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarColor)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh),
                onClick = {
                    if (isSearchOpen) {
                        isSearchOpen = false
                        searchText = ""
                        focusManager.clearFocus()
                    } else onBackClick()
                }
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = if (isSearchOpen) "Close search" else "Back") }

            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                        expandHorizontally(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate), expandFrom = Alignment.End),
                    exit = fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) +
                        shrinkHorizontally(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate), shrinkTowards = Alignment.End)
                ) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        // Wait a frame so the field is attached before asking for focus.
                        kotlinx.coroutines.delay(50)
                        runCatching { focusRequester.requestFocus() }
                    }
                    CompactSearchField(
                        query = searchText,
                        onQueryChange = { searchText = it },
                        onSearch = { focusManager.clearFocus() },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSearchOpen && isHeaderCollapsed,
                    enter = fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
                    exit = fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                ) {
                    Text(
                        "Your Music",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isSearchOpen) {
                // Order: Latest (newest like / newest download), A–Z, Z–A, most / least / last played.
                var sortMenu by remember { mutableStateOf(false) }
                Box {
                    FilledTonalIconButton(
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh),
                        onClick = { sortMenu = true }
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = "Order songs, now ${controls.sort.label}"
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = sortMenu,
                        onDismissRequest = { sortMenu = false },
                        shape = MaterialTheme.shapes.large
                    ) {
                        com.theveloper.pixelplay.presentation.viewmodel.YourMusicSort.entries.forEach { option ->
                            val label = if (option == com.theveloper.pixelplay.presentation.viewmodel.YourMusicSort.LATEST)
                                (if (controls.likedOnly) "Latest liked" else "Latest downloaded") else option.label
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                trailingIcon = {
                                    if (option == controls.sort) Icon(androidx.compose.material.icons.Icons.Rounded.Check, contentDescription = "Selected")
                                },
                                onClick = {
                                    sortMenu = false
                                    viewModel.setSort(option)
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh),
                    onClick = { isSearchOpen = true }
                ) { Icon(Icons.Rounded.Search, contentDescription = "Search your music") }
            }
        }

        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(Modifier.fillMaxSize()) {
            val showScrollBar = LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = MiniPlayerHeight + bottomInset + 16.dp)
            ) {
                item(key = "your_music_hero", contentType = "hero") {
                    YourMusicHero(
                        subtitle = when {
                            activeMix != null -> "${activeMix.label} mix · ${songs.size} songs"
                            state.hasActiveFilters -> "${songs.size} of ${state.totalCount} songs"
                            else -> "${state.totalCount} songs · ${state.likedCount} liked"
                        },
                        shuffleEnabled = songs.isNotEmpty(),
                        onShuffle = { shuffleAll() }
                    )
                }

                // Filters sit right above the first song and stay pinned under the header.
                stickyHeader(key = "your_music_filters", contentType = "filters") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(bottom = 6.dp)
                    ) {
                        YourMusicFilterRow(
                            controls = controls,
                            onAddCustom = { showCustomFilterDialog = true },
                            onClear = viewModel::clearFilters,
                            onToggleLiked = viewModel::toggleLikedOnly,
                            onSelect = viewModel::selectFilter,
                            onDeleteCustom = viewModel::deleteCustomFilter
                        )
                        AnimatedVisibility(
                            visible = activeMix != null,
                            enter = fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                                expandVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
                            exit = fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) +
                                shrinkVertically(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                        ) {
                            activeMix?.let { filter ->
                                Row(
                                    modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${filter.label} – ${filter.description}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = viewModel::regenerateMix) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("New mix")
                                    }
                                }
                            }
                        }
                    }
                }

                when {
                    state.isLoading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() }
                    }
                    songs.isEmpty() -> item(key = "empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.MusicOff, null, Modifier.size(48.dp), tint = colors.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    activeMix != null -> "Couldn't build a ${activeMix.label} mix"
                                    state.hasActiveFilters -> "No songs match"
                                    else -> "No songs in your library yet"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (state.hasActiveFilters) {
                                Text(
                                    "Mixes start from songs with matching genre tags, titles or analysed audio features.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                    state.families.isNotEmpty() -> items(
                        state.families,
                        key = { family -> "family_" + family.key },
                        contentType = { "family" }
                    ) { family ->
                        val head = family.head.song
                        val isExpanded = family.key in expandedFamilies
                        Column(
                            Modifier
                                .padding(start = 12.dp, end = if (showScrollBar) 24.dp else 12.dp)
                                .animateItem()
                        ) {
                            EnhancedSongListItem(
                                song = head,
                                isPlaying = playerState.isPlaying,
                                isCurrentSong = playerState.currentSong?.id == head.id,
                                extraTrailing = if (family.versions.isEmpty()) null else {
                                    {
                                        VersionsButton(
                                            count = family.versions.size,
                                            expanded = isExpanded,
                                            title = head.title,
                                            onClick = {
                                                expandedFamilies = if (isExpanded) expandedFamilies - family.key
                                                else expandedFamilies + family.key
                                            }
                                        )
                                    }
                                },
                                onMoreOptionsClick = { openOptions(it) },
                                onClick = { play(head) }
                            )
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) +
                                    expandVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                                exit = fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) +
                                    shrinkVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate))
                            ) {
                                VersionTree(
                                    versions = family.versions.map { it.song },
                                    currentSongId = playerState.currentSong?.id,
                                    onPlay = { play(it) },
                                    onOptions = { openOptions(it) }
                                )
                            }
                        }
                    }
                    else -> itemsIndexed(
                        songs,
                        key = { index, _ -> songKeys.getOrElse(index) { "your_music_$index" } },
                        contentType = { _, _ -> "song" }
                    ) { _, song ->
                        EnhancedSongListItem(
                            modifier = Modifier
                                .padding(start = 12.dp, end = if (showScrollBar) 24.dp else 12.dp)
                                .animateItem(),
                            song = song,
                            isPlaying = playerState.isPlaying,
                            isCurrentSong = playerState.currentSong?.id == song.id,
                            onMoreOptionsClick = { openOptions(it) },
                            onClick = { play(song) }
                        )
                    }
                }
            }
            ExpressiveScrollBar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        end = 6.dp,
                        top = 8.dp,
                        bottom = if (playerState.currentSong != null) MiniPlayerHeight + bottomInset + 20.dp else bottomInset + 16.dp
                    )
            )
        }
    }

    if (showCustomFilterDialog) {
        CustomFilterDialog(
            existing = controls.customFilters,
            onDismiss = { showCustomFilterDialog = false },
            onSave = { text ->
                viewModel.addCustomFilter(text)
                showCustomFilterDialog = false
            }
        )
    }

    songForOptions?.let { song ->
        val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
        val bottomBarHeight = resolveNavBarOccupiedHeight(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            navBarCompactMode
        )
        // A row can stand for several copies (local file, download, streamed like): it's liked if
        // any copy is, and unliking removes the like from every copy.
        val copies = state.copies[song.id].orEmpty().ifEmpty { listOf(song) }
        val isLiked = song.isFavorite || copies.any { it.id in favoriteIds }
        SongInfoBottomSheet(
            song = song,
            isFavorite = isLiked,
            onToggleFavorite = {
                if (isLiked) copies.forEach { copy -> playerViewModel.toggleFavoriteSpecificSong(copy, removing = true) }
                else playerViewModel.toggleFavoriteSpecificSong(song)
            },
            onDismiss = { songForOptions = null },
            onPlaySong = { playerViewModel.showAndPlaySong(song) },
            onAddToQueue = { playerViewModel.addSongToQueue(song) },
            onAddNextToQueue = { playerViewModel.addSongNextToQueue(song) },
            onAddToPlayList = { showPlaylistSheet = true },
            // Delete here removes the song completely: its file, download and like, every copy.
            onDeleteFromDevice = { activity, target, onResult ->
                playerViewModel.deleteFromYourMusic(activity, target, copies, onResult)
            },
            onNavigateToAlbum = {
                songForOptions = null
                navController.navigateSafelyReplacing(
                    route = Screen.AlbumDetail.createRoute(song.albumId),
                    patternToPop = Screen.AlbumDetail.route
                )
            },
            onNavigateToArtist = {
                songForOptions = null
                navController.navigateSafelyReplacing(
                    route = Screen.ArtistDetail.createRouteForSong(song),
                    patternToPop = Screen.ArtistDetail.route
                )
            },
            onNavigateToArtistById = { artistId ->
                songForOptions = null
                navController.navigateSafelyReplacing(
                    route = Screen.ArtistDetail.createRouteForSongArtist(song, artistId),
                    patternToPop = Screen.ArtistDetail.route
                )
            },
            onNavigateToGenre = {
                songForOptions = null
                song.genre?.let {
                    navController.navigateSafelyReplacing(
                        route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                        patternToPop = Screen.GenreDetail.route
                    )
                }
            },
            onEditSong = { title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, trackGain, albumGain, cover ->
                playerViewModel.editSongMetadata(song, title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, trackGain, albumGain, cover)
            },
            removeFromListTrigger = {},
            alwaysShowDelete = true
        )
        if (showPlaylistSheet) {
            val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = listOf(song),
                onDismiss = { showPlaylistSheet = false },
                currentPlaylistId = null,
                bottomBarHeight = bottomBarHeight,
                playerViewModel = playerViewModel,
            )
        }
    }
}

/** Playlist-style header: tinted backdrop, heart cover, title, counts and the big Shuffle button. */
@Composable
private fun YourMusicHero(subtitle: String, shuffleEnabled: Boolean, onShuffle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to colors.primaryContainer,
                    0.55f to colors.primaryContainer.copy(alpha = 0.35f),
                    1f to colors.surface
                )
            )
    ) {
        val coverSize = (maxWidth * 0.46f).coerceAtMost(220.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(coverSize)
                    .shadow(12.dp, MaterialTheme.shapes.large)
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(coverSize * 0.45f)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Your Music",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (shuffleEnabled) colors.onSurface else colors.onSurface.copy(alpha = 0.3f))
                    .clickable(enabled = shuffleEnabled, onClick = onShuffle)
                    .semantics { contentDescription = "Shuffle Your Music" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = colors.surface, modifier = Modifier.size(32.dp))
            }
        }
    }
}

/** The horizontal filter row: custom-filter sparkle, Clear, Liked, custom filters, then presets. */
@Composable
private fun YourMusicFilterRow(
    controls: com.theveloper.pixelplay.presentation.viewmodel.YourMusicControls,
    onAddCustom: () -> Unit,
    onClear: () -> Unit,
    onToggleLiked: () -> Unit,
    onSelect: (String) -> Unit,
    onDeleteCustom: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "sparkle") {
            FilledTonalIconButton(
                onClick = onAddCustom,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) { Icon(Icons.Rounded.AutoAwesome, contentDescription = "Add a custom filter", modifier = Modifier.size(18.dp)) }
        }
        if (controls.likedOnly || controls.selectedFilterId != null) {
            item(key = "clear") {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        item(key = "liked") {
            FilterChip(
                selected = controls.likedOnly,
                onClick = onToggleLiked,
                label = { Text("Liked") },
                leadingIcon = {
                    Icon(
                        if (controls.likedOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
        items(controls.customFilters, key = { it.id }) { filter ->
            InputChip(
                selected = filter.id == controls.selectedFilterId,
                onClick = { onSelect(filter.id) },
                label = { Text(filter.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize))
                },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Delete ${filter.label}",
                        modifier = Modifier
                            .size(InputChipDefaults.IconSize)
                            .clip(CircleShape)
                            .clickable { onDeleteCustom(filter.id) }
                    )
                }
            )
        }
        VibeCategory.values().filter { it != VibeCategory.CUSTOM }.forEach { category ->
            item(key = "divider_${category.name}") {
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            }
            items(MusicVibeFilters.presets.filter { it.category == category }, key = { it.id }) { filter ->
                FilterChip(
                    selected = filter.id == controls.selectedFilterId,
                    onClick = { onSelect(filter.id) },
                    label = { Text(filter.label) }
                )
            }
        }
    }
}

/** Left of the options button when a song has other versions: shows the count, opens the tree. */
@Composable
private fun VersionsButton(count: Int, expanded: Boolean, title: String, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
        label = "versionsRotation"
    )
    Box(contentAlignment = Alignment.TopEnd) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(36.dp)
                .semantics {
                    contentDescription = if (expanded) "Hide versions" else "Show $count versions of $title"
                },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (expanded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (expanded) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                Icons.Rounded.AccountTree,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationX = rotation }
            )
        }
        Box(
            Modifier
                .offset(x = 4.dp, y = (-4).dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiary,
                maxLines = 1
            )
        }
    }
}

/**
 * The versions of a song as a family tree: a trunk line down the left with a branch to each
 * version, which shows its label (Live, Remastered, Cover…), title and artist.
 */
@Composable
private fun VersionTree(
    versions: List<Song>,
    currentSongId: String?,
    onPlay: (Song) -> Unit,
    onOptions: (Song) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        versions.forEachIndexed { index, version ->
            val isLast = index == versions.lastIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trunk + branch.
                Box(
                    Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .drawBehind {
                            val x = 24.dp.toPx()
                            val stroke = 2.dp.toPx()
                            val midY = size.height / 2f
                            drawLine(lineColor, Offset(x, 0f), Offset(x, if (isLast) midY else size.height), stroke, cap = StrokeCap.Round)
                            drawLine(lineColor, Offset(x, midY), Offset(size.width, midY), stroke, cap = StrokeCap.Round)
                        }
                )
                VersionRow(
                    song = version,
                    isCurrent = version.id == currentSongId,
                    onClick = { onPlay(version) },
                    onOptions = { onOptions(version) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun VersionRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = remember(song.title) { com.theveloper.pixelplay.data.library.SongUnifier.versionLabel(song) ?: "Version" }
    Surface(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label version, ${song.title}" },
        shape = MaterialTheme.shapes.large,
        color = if (isCurrent) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (isCurrent) colors.onPrimaryContainer else colors.onSurface
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = null,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
                        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOptions) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options for ${song.title}")
            }
        }
    }
}

@Composable
private fun CustomFilterDialog(existing: List<VibeFilter>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val duplicate = existing.any { it.label.equals(text.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
        title = { Text("Custom filter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Describe a vibe, genre, artist, decade or activity — e.g. “late night lofi”, “90s rock”, “rainy day”. It stays saved in your filter row.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    label = { Text("Filter") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave(text) }),
                    supportingText = if (duplicate) {
                        { Text("Already saved — it will be switched on.") }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A slim (40dp) pill search field; Material TextField can't go below 56dp without clipping. */
@Composable
private fun CompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier.height(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Search your music",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
