package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.theveloper.pixelplay.data.model.Playlist
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistBottomSheet(
    playlistUiState: PlaylistUiState,
    songs: List<Song>,
    onDismiss: () -> Unit,
    bottomBarHeight: Dp,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    currentPlaylistId: String? = null
) {
    val playlistCreatedAndSongsAddedMessage = stringResource(R.string.playlist_sheet_created_and_songs_added)
    val setAiProviderApiKeyFirstMessage = stringResource(R.string.library_toast_set_ai_provider_api_key_first)
    val songAddedToPlaylistsMessage = stringResource(R.string.playlist_sheet_song_added_to_playlists)
    val commonSavedMessage = stringResource(R.string.common_saved)
    val saveActionText = stringResource(R.string.common_save)

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    val sheetScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val filteredPlaylists = remember(searchQuery, playlistUiState.playlists) {
        if (searchQuery.isBlank()) playlistUiState.playlists
        else playlistUiState.playlists.filter { it.name.contains(searchQuery, true) }
    }
    val hasActiveAiProviderApiKey by playerViewModel.hasActiveAiProviderApiKey.collectAsStateWithLifecycle()
    val addableServiceIds by playlistViewModel.addableServicePlaylistIds.collectAsStateWithLifecycle()
    val reducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    // Spotify / Apple Music playlists are add-only (matched on the service), never pre-checked.
    val addOnlyServiceIds = remember(playlistUiState.playlists) {
        playlistUiState.playlists.filter { it.source == "SPOTIFY" || it.source == "APPLE_MUSIC" }
            .mapTo(HashSet()) { it.id }
    }

    val selectedPlaylists = remember {
        mutableStateMapOf<String, Boolean>().apply {
            if (songs.size == 1) {
                // Single song: pre-select playlists containing it
                val songId = songs.first().id
                filteredPlaylists.forEach {
                    put(it.id, it.id !in addOnlyServiceIds && it.songIds.contains(songId))
                }
            } else {
                // Multiple songs: start empty (additive only)
                filteredPlaylists.forEach {
                    put(it.id, false)
                }
            }
        }
    }

    val isAnyPlaylistSelected = selectedPlaylists.values.any { it }

    val alpha by animateFloatAsState(
        targetValue = if (isAnyPlaylistSelected) 1f else 0.4f,
        label = "fab_alpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets } // Manejo de insets como el teclado
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Box(modifier = Modifier.fillMaxSize()) {

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                     Text(
                        if (songs.size > 1) {
                            stringResource(R.string.playlist_sheet_add_songs_title, songs.size)
                        } else {
                            stringResource(R.string.playlist_sheet_select_playlists)
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = GoogleSansRounded
                    )
                }
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
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.playlist_sheet_search_playlists_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = CircleShape,
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = {
                            searchQuery = ""
                        }) { Icon(Icons.Filled.Clear, null) }
                    }
                )




                // "New playlist" plus one jump chip per connected service with playlists you own.
                val listState = rememberLazyListState()
                val sections = remember(filteredPlaylists, addableServiceIds) {
                    buildAddToPlaylistSections(filteredPlaylists, addableServiceIds)
                }
                val sectionStartIndex = remember(sections) {
                    var index = 0
                    sections.associate { section ->
                        val start = index
                        index += 1 + section.playlists.size
                        section.source to start
                    }
                }
                val activeSource by remember(sections, sectionStartIndex) {
                    derivedStateOf {
                        val first = listState.firstVisibleItemIndex
                        sections.lastOrNull { (sectionStartIndex[it.source] ?: Int.MAX_VALUE) <= first }?.source
                    }
                }
                val scrollScope = rememberCoroutineScope()
                AddToPlaylistHeaderRow(
                    services = sections.map { it.source }.filter { it != SOURCE_PIXELPLAYER },
                    activeSource = activeSource,
                    reducedMotion = reducedMotion,
                    onNewPlaylist = { showCreatePlaylistDialog = true },
                    onJump = { source ->
                        val target = sectionStartIndex[source] ?: return@AddToPlaylistHeaderRow
                        scrollScope.launch {
                            if (reducedMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
                        }
                    },
                    modifier = Modifier.padding(top = 10.dp, start = 14.dp, end = 14.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (playlistUiState.isLoading && sections.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                } else if (sections.isEmpty()) {
                    Text(
                        text = "No playlists found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomBarHeight + 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sections.forEach { section ->
                            stickyHeader(key = "header_${section.source}") {
                                AddToPlaylistSectionHeader(section.source)
                            }
                            items(section.playlists, key = { "pl_${it.id}" }) { playlist ->
                                Box(Modifier.animateItem()) {
                                    PlaylistItem(
                                        playlist = playlist,
                                        playerViewModel = playerViewModel,
                                        onClick = {
                                            selectedPlaylists[playlist.id] = !(selectedPlaylists[playlist.id] ?: false)
                                        },
                                        isAddingToPlaylist = true,
                                        selectedPlaylists = selectedPlaylists
                                    )
                                }
                            }
                        }
                    }
                }

                if (showCreatePlaylistDialog) {
                    CreatePlaylistDialogRedesigned(
                        onDismiss = { showCreatePlaylistDialog = false },
                        onCreate = { name ->
                            // Pass all selected songs to the new playlist
                            playlistViewModel.createPlaylist(name, songIds = songs.map { it.id })
                            showCreatePlaylistDialog = false
                            onDismiss() // Close sheet after creation + add
                            playerViewModel.sendToast(playlistCreatedAndSongsAddedMessage)
                        },
                        onGenerateClick = {
                            showCreatePlaylistDialog = false
                            if (hasActiveAiProviderApiKey) {
                                playerViewModel.showAiPlaylistSheet()
                            } else {
                                playerViewModel.sendToast(setAiProviderApiKeyFirstMessage)
                            }
                        }
                    )
                }
            }

            MediumExtendedFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp, end = 8.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                    },
                shape = CircleShape,
                onClick = {
                    if (!isAnyPlaylistSelected) return@MediumExtendedFloatingActionButton

                    val selectedIds = selectedPlaylists.filter { it.value }.keys.toList()
                    // Spotify / Apple Music: matched and added on the service; they report their own result.
                    val serviceIds = selectedIds.filter { it in addOnlyServiceIds }
                    val otherIds = selectedIds.filterNot { it in addOnlyServiceIds }
                    if (songs.size == 1) {
                         playlistViewModel.addOrRemoveSongFromPlaylists(
                            songs.first().id,
                            otherIds,
                            currentPlaylistId
                        )
                    } else if (otherIds.isNotEmpty()) {
                         // Batch add
                         playlistViewModel.addSongsToPlaylists(
                             songs.map { it.id },
                             otherIds
                         )
                    }
                    if (serviceIds.isNotEmpty()) {
                        playlistViewModel.addSongsToServicePlaylists(songs, serviceIds) { message ->
                            playerViewModel.sendToast(message)
                        }
                    }
                    // Hide first, then remove the sheet (keeps the exit animation).
                    sheetScope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    if (otherIds.isNotEmpty() || songs.size == 1 && serviceIds.isEmpty()) {
                        playerViewModel.sendToast(if (songs.size > 1) songAddedToPlaylistsMessage else commonSavedMessage)
                    }
                    playerViewModel.multiSelectionStateHolder.clearSelection()
                },
                icon = { Icon(Icons.Rounded.Save, saveActionText) },
                text = { Text(if (songs.size > 1) stringResource(R.string.common_add) else saveActionText) },
            )
        }
    }
}


private const val SOURCE_PIXELPLAYER = "LOCAL"

/** One section of the add-to-playlist list: PixelPlayer, Spotify, YouTube Music or Apple Music. */
private data class AddToPlaylistSection(val source: String, val playlists: List<Playlist>)

private fun buildAddToPlaylistSections(playlists: List<Playlist>, addableServiceIds: Set<String>): List<AddToPlaylistSection> {
    val local = playlists.filter { (it.source == "LOCAL" || it.source.isBlank()) && it.friendId == null }
    val service = playlists.filter { it.id in addableServiceIds }
    return buildList {
        if (local.isNotEmpty()) add(AddToPlaylistSection(SOURCE_PIXELPLAYER, local))
        listOf("SPOTIFY", "YOUTUBE_MUSIC", "APPLE_MUSIC").forEach { source ->
            val list = service.filter { it.source == source }
            if (list.isNotEmpty()) add(AddToPlaylistSection(source, list))
        }
    }
}

private fun addToPlaylistServiceLabel(source: String) = when (source) {
    "SPOTIFY" -> "Spotify"
    "YOUTUBE_MUSIC" -> "YouTube Music"
    "APPLE_MUSIC" -> "Apple Music"
    else -> "PixelPlayer"
}

private fun addToPlaylistServiceIcon(source: String): Int? = when (source) {
    "SPOTIFY" -> R.drawable.ic_source_spotify
    "YOUTUBE_MUSIC" -> R.drawable.ic_source_youtube_music
    "APPLE_MUSIC" -> R.drawable.ic_source_apple_music
    else -> null
}

/** "New playlist" and, next to it, a chip per service that jumps to that service's playlists. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddToPlaylistHeaderRow(
    services: List<String>,
    activeSource: String?,
    reducedMotion: Boolean,
    onNewPlaylist: () -> Unit,
    onJump: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalIconButton(
            onClick = onNewPlaylist,
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = stringResource(R.string.library_cd_create_new_playlist)
            )
        }
        services.forEach { source ->
            val isSelected = activeSource == source
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
            val container by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = if (reducedMotion) snap() else effects,
                label = "serviceChipContainer"
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = if (reducedMotion) snap() else effects,
                label = "serviceChipContent"
            )
            val label = addToPlaylistServiceLabel(source)
            FilledTonalIconButton(
                onClick = { onJump(source) },
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = container, contentColor = content),
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        selected = isSelected
                    }
            ) {
                addToPlaylistServiceIcon(source)?.let { icon ->
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = "Jump to $label playlists",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistSectionHeader(source: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        addToPlaylistServiceIcon(source)?.let { icon ->
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = addToPlaylistServiceLabel(source),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
