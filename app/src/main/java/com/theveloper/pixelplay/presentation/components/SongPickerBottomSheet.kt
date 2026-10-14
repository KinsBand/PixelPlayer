package com.theveloper.pixelplay.presentation.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import com.theveloper.pixelplay.data.model.StorageFilter
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.ui.theme.ShapeCache
import kotlinx.coroutines.flow.map
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun SongPickerBottomSheet(
    initiallySelectedSongIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedSongIds = remember {
        mutableStateMapOf<String, Boolean>().apply {
            initiallySelectedSongIds.forEach { put(it, true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        SongPickerContent(
            selectedSongIds = selectedSongIds,
            onConfirm = onConfirm,
            playerViewModel = playerViewModel
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SongPickerContent(
    selectedSongIds: MutableMap<String, Boolean>,
    onConfirm: (Set<String>) -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    /** False when the host screen already shows a title (Create playlist's "Add songs" step). */
    showTitle: Boolean = true,
    confirmContentDescription: String = stringResource(R.string.song_picker_cd_confirm_add_songs),
) {
    val storageFilter by playerViewModel.playlistPickerStorageFilter.collectAsStateWithLifecycle()
    val hasCloudSongs by playerViewModel.hasCloudSongsFlow.collectAsStateWithLifecycle()

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSong = stablePlayerState.currentSong

    // Without cloud songs there's nothing to show under "Online": fall back to everything.
    LaunchedEffect(hasCloudSongs, storageFilter) {
        if (hasCloudSongs == false && storageFilter == StorageFilter.ONLINE) {
            playerViewModel.setPlaylistPickerStorageFilter(StorageFilter.ALL)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            if (showTitle) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.song_picker_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = GoogleSansRounded
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentSong != null) {
                    NowPlayingQuickAddCard(
                        song = currentSong,
                        isSelected = selectedSongIds[currentSong.id] == true,
                        onToggle = {
                            val isCurrentSelected = selectedSongIds[currentSong.id] == true
                            selectedSongIds[currentSong.id] = !isCurrentSelected
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                FilledIconButton(
                    onClick = { onConfirm(selectedSongIds.filterValues { it }.keys) },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = confirmContentDescription,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        SongPickerSelectionPane(
            selectedSongIds = selectedSongIds,
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = 120.dp,
                top = 8.dp
            ),
            playerViewModel = playerViewModel
        )
    }
}

/**
 * The song playing right now, with a one-tap add: tap the card or the button to tick or untick
 * it for this playlist (the same selection as the list above).
 */
@Composable
private fun NowPlayingQuickAddCard(
    song: Song,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = song.title,
                shape = RoundedCornerShape(10.dp),
                targetSize = Size(112, 112),
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = if (isSelected) "Remove ${song.title} from selection" else "Add ${song.title}",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SongPickerSelectionPane(
    selectedSongIds: MutableMap<String, Boolean>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp, top = 20.dp),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    val storageFilter by playerViewModel.playlistPickerStorageFilter.collectAsStateWithLifecycle()
    
    val pagedSongs = playerViewModel.playlistPickerSongs.collectAsLazyPagingItems()
    // Liked songs are only loaded when the Liked chip is on (collected in that branch below),
    // so opening the picker runs one list query instead of two.

    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val searchResultsInitialValue: List<Song>? = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList() else null
    }
    val hasCloudSongs by playerViewModel.hasCloudSongsFlow.collectAsStateWithLifecycle()
    // ALL (the default) keeps local and cloud songs together; the chips narrow it.
    val searchResults by remember(searchQuery, playerViewModel, storageFilter) {
        playerViewModel.searchSongs(searchQuery)
            .map { songs ->
                when (storageFilter) {
                    StorageFilter.OFFLINE -> songs.filter { s ->
                        s.gdriveFileId == null
                    }

                    StorageFilter.ONLINE -> songs.filter { s ->
                        s.gdriveFileId != null
                    }

                    else -> songs
                }
            }
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = searchResultsInitialValue)
    // While a new query runs, keep showing the previous results instead of a spinner, so
    // typing feels instant (local songs come straight from the database).
    var lastSearchResults by remember { mutableStateOf<List<Song>?>(null) }
    LaunchedEffect(searchResults) {
        searchResults?.let { lastSearchResults = it }
    }

    val animatedAlbumCornerRadius = 60.dp
    val albumShape = remember(animatedAlbumCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedAlbumCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedAlbumCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedAlbumCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedAlbumCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        SongPickerSearchField(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveFilterChip(
                label = stringResource(R.string.song_picker_filter_favorites),
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                leadingIcon = {
                    Icon(
                        painter = painterResource(
                            if (favoritesOnly) R.drawable.round_favorite_24
                            else R.drawable.round_favorite_border_24
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            // Local / Online narrow the list; tapping the selected one goes back to everything.
            StorageFilterChip(
                selected = storageFilter == StorageFilter.OFFLINE,
                label = stringResource(R.string.library_storage_filter_offline),
                onClick = {
                    playerViewModel.setPlaylistPickerStorageFilter(
                        if (storageFilter == StorageFilter.OFFLINE) StorageFilter.ALL else StorageFilter.OFFLINE
                    )
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_phonef),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            if (hasCloudSongs != false) {
                StorageFilterChip(
                    selected = storageFilter == StorageFilter.ONLINE,
                    label = stringResource(R.string.library_storage_filter_online),
                    onClick = {
                        playerViewModel.setPlaylistPickerStorageFilter(
                            if (storageFilter == StorageFilter.ONLINE) StorageFilter.ALL else StorageFilter.ONLINE
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        when {
            searchQuery.isNotBlank() -> {
                val shownResults = searchResults ?: lastSearchResults
                val displayed = (shownResults ?: emptyList()).let { results ->
                    if (favoritesOnly) results.filter { it.id in favoriteIds } else results
                }
                SongPickerList(
                    filteredSongs = displayed,
                    isLoading = shownResults == null,
                    selectedSongIds = selectedSongIds,
                    albumShape = albumShape,
                    searchQuery = searchQuery,
                    modifier = Modifier.weight(1f),
                    contentPadding = contentPadding,
                    onClearSearch = { searchQuery = "" }
                )
            }
            favoritesOnly -> {
                val pagedFavoriteSongs = playerViewModel.playlistPickerFavoriteSongs.collectAsLazyPagingItems()
                SongPickerPagingList(
                    pagedSongs = pagedFavoriteSongs,
                    selectedSongIds = selectedSongIds,
                    albumShape = albumShape,
                    tabId = LibraryTabId.LIKED,
                    storageFilter = storageFilter,
                    modifier = Modifier.weight(1f),
                    contentPadding = contentPadding
                )
            }
            else -> {
                SongPickerPagingList(
                    pagedSongs = pagedSongs,
                    selectedSongIds = selectedSongIds,
                    albumShape = albumShape,
                    tabId = LibraryTabId.SONGS,
                    storageFilter = storageFilter,
                    modifier = Modifier.weight(1f),
                    contentPadding = contentPadding
                )
            }
        }
    }
}

@Composable
private fun StorageFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    ExpressiveFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        leadingIcon = icon
    )
}

@Composable
private fun SongPickerSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = searchQuery,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedTrailingIconColor = Color.Transparent,
            focusedSupportingTextColor = Color.Transparent,
        ),
        onValueChange = onSearchQueryChange,
        label = { Text(stringResource(R.string.song_picker_search_label)) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = CircleShape,
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Filled.Clear, null)
                }
            }
        }
    )
}

@OptIn(UnstableApi::class)
@Composable
fun SongPickerPagingList(
    pagedSongs: LazyPagingItems<Song>,
    selectedSongIds: MutableMap<String, Boolean>,
    albumShape: androidx.compose.ui.graphics.Shape,
    tabId: LibraryTabId,
    storageFilter: StorageFilter,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp, top = 20.dp)
) {
    when {
        pagedSongs.loadState.refresh is LoadState.Loading && pagedSongs.itemCount == 0 -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        pagedSongs.loadState.refresh is LoadState.Error && pagedSongs.itemCount == 0 -> {
            val error = (pagedSongs.loadState.refresh as LoadState.Error).error
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = error.localizedMessage ?: stringResource(R.string.song_picker_error_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { pagedSongs.retry() }) {
                        Text(stringResource(R.string.library_action_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        pagedSongs.itemCount == 0 && pagedSongs.loadState.refresh is LoadState.NotLoading && pagedSongs.loadState.append.endOfPaginationReached -> {
            SongPickerEmptyState(
                tabId = tabId,
                storageFilter = storageFilter,
                modifier = modifier.padding(bottom = contentPadding.calculateBottomPadding())
            )
        }

        else -> {
            val listState = rememberLazyListState()
            Box(
                modifier = modifier
                    .padding(horizontal = 14.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = contentPadding.calculateBottomPadding(),
                        top = contentPadding.calculateTopPadding(),
                        start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                        end = if (LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward)) 12.dp else 0.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = pagedSongs.itemCount,
                        key = { index -> pagedSongs.peek(index)?.id ?: "song_picker_paged_$index" },
                        contentType = pagedSongs.itemContentType { "song_picker_song" }
                    ) { index ->
                        val song = pagedSongs[index]
                        if (song != null) {
                            SongPickerRow(
                                song = song,
                                selectedSongIds = selectedSongIds,
                                albumShape = albumShape
                            )
                        } else {
                            SongPickerPlaceholderRow()
                        }
                    }

                    if (pagedSongs.loadState.append is LoadState.Loading) {
                        item(key = "song_picker_append_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    if (pagedSongs.loadState.append is LoadState.Error) {
                        item(key = "song_picker_append_error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(onClick = { pagedSongs.retry() }) {
                                    Text(stringResource(R.string.song_picker_load_more), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                ExpressiveScrollBar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            bottom = contentPadding.calculateBottomPadding(),
                            top = contentPadding.calculateTopPadding() + 10.dp
                        )
                )
            }
        }
    }
}

@Composable
private fun SongPickerRow(
    song: Song,
    selectedSongIds: MutableMap<String, Boolean>,
    albumShape: androidx.compose.ui.graphics.Shape
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable {
                val currentSelection = selectedSongIds[song.id] ?: false
                selectedSongIds[song.id] = !currentSelection
            }
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = CircleShape
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selectedSongIds[song.id] ?: false,
            onCheckedChange = { isChecked ->
                selectedSongIds[song.id] = isChecked
            }
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
        ) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = song.title,
                shape = albumShape,
                // 36 dp cover: 112 px is enough at 3x and decodes ~2.2x less than 168 px.
                targetSize = Size(112, 112),
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SongPickerPlaceholderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = CircleShape
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(18.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
        )
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(14.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun SongPickerEmptyState(
    tabId: LibraryTabId,
    storageFilter: StorageFilter,
    modifier: Modifier = Modifier
) {
    val spec = when (tabId) {
        LibraryTabId.LIKED -> when (storageFilter) {
            StorageFilter.ALL -> Triple(R.drawable.round_favorite_24, R.string.library_empty_liked_all_title, R.string.library_empty_liked_all_subtitle)
            StorageFilter.OFFLINE -> Triple(R.drawable.round_favorite_24, R.string.library_empty_liked_offline_title, R.string.library_empty_liked_offline_subtitle)
            StorageFilter.ONLINE -> Triple(R.drawable.round_favorite_24, R.string.library_empty_liked_online_title, R.string.library_empty_liked_online_subtitle)
        }
        else -> when (storageFilter) {
            StorageFilter.ALL -> Triple(R.drawable.rounded_music_off_24, R.string.library_empty_songs_all_title, R.string.library_empty_songs_all_subtitle)
            StorageFilter.OFFLINE -> Triple(R.drawable.rounded_music_off_24, R.string.library_empty_songs_offline_title, R.string.library_empty_songs_offline_subtitle)
            StorageFilter.ONLINE -> Triple(R.drawable.rounded_music_off_24, R.string.library_empty_songs_online_title, R.string.library_empty_songs_online_subtitle)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = spec.first),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(spec.second),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(spec.third),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SongPickerList(
    filteredSongs: List<Song>,
    isLoading: Boolean,
    selectedSongIds: MutableMap<String, Boolean>,
    albumShape: androidx.compose.ui.graphics.Shape,
    searchQuery: String = "",
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp, top = 20.dp),
    /** Shown as a "Clear search" button under the no-results message. */
    onClearSearch: (() -> Unit)? = null,
) {
    if (isLoading) {
        Box(
            modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (filteredSongs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    tonalElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_search_24),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank())
                            stringResource(R.string.search_no_results_for_query, searchQuery)
                        else
                            stringResource(R.string.search_no_results_found),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
                if (onClearSearch != null && searchQuery.isNotBlank()) {
                    androidx.compose.material3.FilledTonalButton(onClick = onClearSearch) {
                        Text(stringResource(R.string.song_picker_clear_search))
                    }
                }
            }
        }
    } else {
        val listState = rememberLazyListState()
        Box(
            modifier = modifier
                .padding(horizontal = 14.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding(),
                    top = contentPadding.calculateTopPadding(),
                    start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = if (LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward)) 12.dp else 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    SongPickerRow(
                        song = song,
                        selectedSongIds = selectedSongIds,
                        albumShape = albumShape
                    )
                }
            }

            ExpressiveScrollBar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        bottom = contentPadding.calculateBottomPadding(),
                        top = contentPadding.calculateTopPadding() + 10.dp
                    )
            )
        }
    }
}
