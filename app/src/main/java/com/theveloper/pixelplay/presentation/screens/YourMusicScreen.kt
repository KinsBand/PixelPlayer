package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedVisibility
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
 * Liked Songs + All Songs in one place. Compact top row: shuffle | search. Below it a
 * horizontally scrolling row of single-select mix filters, led by a sparkle button that saves custom filters.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
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

    var showCustomFilterDialog by rememberSaveable { mutableStateOf(false) }
    var songForOptions by remember { mutableStateOf<Song?>(null) }
    var showPlaylistSheet by remember { mutableStateOf(false) }

    val allFilters = remember(controls.customFilters) { MusicVibeFilters.presets + controls.customFilters }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Your Music", fontFamily = GoogleSansRounded, maxLines = 1)
                        Text(
                            text = when {
                                activeMix != null -> "${activeMix.label} mix · ${songs.size} songs"
                                state.hasActiveFilters -> "${songs.size} of ${state.totalCount} songs"
                                else -> "${state.totalCount} songs · ${state.likedCount} liked"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = GoogleSansRounded),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        onClick = onBackClick
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Compact row: shuffle | search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { shuffleAll() },
                    enabled = songs.isNotEmpty(),
                    modifier = Modifier.size(38.dp)
                ) { Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(18.dp)) }

                CompactSearchField(
                    query = controls.query,
                    onQueryChange = viewModel::setQuery,
                    onSearch = { focusManager.clearFocus() },
                    modifier = Modifier.weight(1f)
                )
            }

            // Filter pills
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
                        onClick = { showCustomFilterDialog = true },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) { Icon(Icons.Rounded.AutoAwesome, contentDescription = "Add a custom filter", modifier = Modifier.size(18.dp)) }
                }
                if (controls.likedOnly || controls.selectedFilterId != null) {
                    item(key = "clear") {
                        TextButton(onClick = { viewModel.clearFilters() }) { Text("Clear") }
                    }
                }
                item(key = "liked") {
                    FilterChip(
                        selected = controls.likedOnly,
                        onClick = viewModel::toggleLikedOnly,
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
                        onClick = { viewModel.selectFilter(filter.id) },
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
                                    .clickable {
                                        viewModel.deleteCustomFilter(filter.id)
                                    }
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
                            onClick = { viewModel.selectFilter(filter.id) },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = activeMix != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                activeMix?.let { filter ->
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filter.label} – ${filter.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            Spacer(Modifier.height(6.dp))

            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                songs.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MusicOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize()) {
                    val showScrollBar = LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = if (showScrollBar) 24.dp else 12.dp,
                            bottom = MiniPlayerHeight + bottomInset + 16.dp
                        )
                    ) {
                        itemsIndexed(
                            songs,
                            key = { index, _ -> songKeys.getOrElse(index) { "your_music_$index" } },
                            contentType = { _, _ -> "song" }
                        ) { _, song ->
                            EnhancedSongListItem(
                                song = song,
                                isPlaying = playerState.isPlaying,
                                isCurrentSong = playerState.currentSong?.id == song.id,
                                onMoreOptionsClick = {
                                    playerViewModel.selectSongForInfo(it)
                                    songForOptions = it
                                },
                                onClick = {
                                    focusManager.clearFocus()
                                    playerViewModel.playSongs(songs, song, queueName)
                                }
                            )
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
        SongInfoBottomSheet(
            song = song,
            isFavorite = song.id in favoriteIds,
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(song) },
            onDismiss = { songForOptions = null },
            onPlaySong = { playerViewModel.showAndPlaySong(song) },
            onAddToQueue = { playerViewModel.addSongToQueue(song) },
            onAddNextToQueue = { playerViewModel.addSongNextToQueue(song) },
            onAddToPlayList = { showPlaylistSheet = true },
            onDeleteFromDevice = playerViewModel::deleteFromDevice,
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
                    route = Screen.ArtistDetail.createRoute(song.artistId),
                    patternToPop = Screen.ArtistDetail.route
                )
            },
            onNavigateToArtistById = { artistId ->
                songForOptions = null
                navController.navigateSafelyReplacing(
                    route = Screen.ArtistDetail.createRoute(artistId),
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
            removeFromListTrigger = {}
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
