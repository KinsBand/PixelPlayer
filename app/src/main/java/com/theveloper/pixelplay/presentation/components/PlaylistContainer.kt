package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.PlayerSheetCollapsedCornerRadius
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.LikedSongsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistSelectionStateHolder
import com.theveloper.pixelplay.utils.formatSongCount
import androidx.compose.ui.platform.LocalContext
import com.theveloper.pixelplay.data.practice.PracticeStore
import com.theveloper.pixelplay.presentation.screens.practiceSummary
import com.theveloper.pixelplay.presentation.library.isFavoritesPlaylistName
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistContainer(
    playlistUiState: PlaylistUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    currentSong: Song? = null,
    navController: NavController?,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    filteredPlaylists: List<Playlist> = playlistUiState.playlists,
    currentSortOption: SortOption? = null,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {},
    playlistSelectionStateHolder: PlaylistSelectionStateHolder? = null
) {

    Column(modifier = Modifier.fillMaxSize()) {
        if (isAddingToPlaylist && playlistUiState.isLoading && filteredPlaylists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        if (isAddingToPlaylist && filteredPlaylists.isEmpty() && !playlistUiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 28.dp,
                        end = 28.dp,
                        bottom = bottomBarHeight + MiniPlayerHeight + 24.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                                Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_container_no_playlist_created),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.playlist_container_new_playlist_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (isAddingToPlaylist) {
                PlaylistItems(
                    currentSong = currentSong,
                    bottomBarHeight = bottomBarHeight,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    isAddingToPlaylist = true,
                    filteredPlaylists = filteredPlaylists,
                    selectedPlaylists = selectedPlaylists,
                    currentSortOption = currentSortOption
                )
            } else {
                val playlistPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = playlistPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = playlistPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    PlaylistItems(
                        bottomBarHeight = bottomBarHeight,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        filteredPlaylists = filteredPlaylists,
                        currentSortOption = currentSortOption,
                        isSelectionMode = isSelectionMode,
                        selectedPlaylistIds = selectedPlaylistIds,
                        onPlaylistLongPress = onPlaylistLongPress,
                        onPlaylistSelectionToggle = onPlaylistSelectionToggle
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaylistItems(
    bottomBarHeight: Dp,
    navController: NavController?,
    currentSong: Song? = null,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    filteredPlaylists: List<Playlist>,
    currentSortOption: SortOption? = null,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {}
) {
    val hasCurrentSong by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong != null && it.currentSong != Song.emptySong() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val listState = rememberLazyListState()
    val showPinnedPlaylists = !isAddingToPlaylist
    val pinnedCount = if (showPinnedPlaylists) PINNED_PLAYLIST_ITEM_COUNT + HUB_ITEM_COUNT else 0
    var lastHandledPlaylistSortKey by remember { mutableStateOf(currentSortOption?.storageKey) }
    var pendingPlaylistSortScrollReset by remember { mutableStateOf(false) }

    LaunchedEffect(currentSortOption) {
        val currentSortKey = currentSortOption?.storageKey ?: return@LaunchedEffect
        if (currentSortKey == lastHandledPlaylistSortKey) return@LaunchedEffect
        lastHandledPlaylistSortKey = currentSortKey
        pendingPlaylistSortScrollReset = true
        listState.scrollToItem(0)
    }

    LaunchedEffect(filteredPlaylists, pendingPlaylistSortScrollReset) {
        if (!pendingPlaylistSortScrollReset) return@LaunchedEffect
        listState.scrollToItem(0)
        pendingPlaylistSortScrollReset = false
    }

    val effectivePlaylists = remember(filteredPlaylists, isAddingToPlaylist) {
        if (isAddingToPlaylist) {
            // YouTube Music playlists are editable too (saved locally and pushed to the account).
            filteredPlaylists.filter { it.source == "LOCAL" || it.source.isBlank() || (it.source == "YOUTUBE_MUSIC" && it.friendId == null) }
        } else {
            filteredPlaylists
        }
    }
    val userPlaylists = remember(effectivePlaylists, isAddingToPlaylist) {
        // "Favourites"-style playlists are merged into Your Music's liked songs, so they
        // don't show as separate cards (they stay available when adding songs).
        effectivePlaylists.filter { it.friendId == null && (isAddingToPlaylist || !isFavoritesPlaylistName(it.name)) }
    }
    val friendPlaylists = remember(effectivePlaylists) {
        effectivePlaylists.filter { it.friendId != null }
    }
    // Playlists tab layout: Spotify | YT Music cards, Friends dropdown, then your PixelPlayer playlists.
    // (Adding-to-playlist keeps the plain list.)
    val spotifyPlaylists = remember(userPlaylists, showPinnedPlaylists) {
        if (showPinnedPlaylists) userPlaylists.filter { it.source.equals("SPOTIFY", true) } else emptyList()
    }
    val youtubePlaylists = remember(userPlaylists, showPinnedPlaylists) {
        if (showPinnedPlaylists) userPlaylists.filter { it.source.equals("YOUTUBE_MUSIC", true) } else emptyList()
    }
    // Friends' playlists pinned (liked) from the Friends screen sit with your own playlists,
    // still showing the friend and platform.
    val friendsViewModel: com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel = hiltViewModel()
    val pinnedFriendIds by friendsViewModel.pinnedPlaylistIds.collectAsStateWithLifecycle()
    val friendsById by friendsViewModel.friendsById.collectAsStateWithLifecycle()
    val localPlaylists = remember(userPlaylists, showPinnedPlaylists, friendPlaylists, pinnedFriendIds) {
        if (showPinnedPlaylists) {
            userPlaylists.filterNot { it.source.equals("SPOTIFY", true) || it.source.equals("YOUTUBE_MUSIC", true) } +
                friendPlaylists.filter { it.id in pinnedFriendIds }
        } else userPlaylists
    }
    val playlistFastScrollLabelProvider = remember(localPlaylists, currentSortOption, pinnedCount) {
        { index: Int ->
            playlistFastScrollLabel(
                playlist = localPlaylists.getOrNull(index - pinnedCount),
                sortOption = currentSortOption
            )
        }
    }
    val openPlaylist: (Playlist) -> Unit = { playlist ->
        if (isSelectionMode) onPlaylistSelectionToggle(playlist)
        else navController?.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .padding(start = 12.dp, end = if (LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward)) 22.dp else 12.dp, bottom = 6.dp)
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = PlayerSheetCollapsedCornerRadius,
                        bottomEnd = PlayerSheetCollapsedCornerRadius
                    )
                ),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
        ) {
            if (showPinnedPlaylists) {
                // Spotify | YT Music, Friends | Practice, then your own playlists (Your Music is in the top row).
                item(key = "platform_cards", contentType = "platform_cards") {
                    PlatformPlaylistCards(
                        spotifyCount = spotifyPlaylists.size,
                        youtubeCount = youtubePlaylists.size,
                        onOpenPlatform = { source ->
                            if (!isSelectionMode) navController?.navigateSafely(Screen.PlatformPlaylists.createRoute(source))
                        }
                    )
                }
                item(key = "friends_practice", contentType = "friends_practice") {
                    val context = LocalContext.current
                    val practiceStore = remember { PracticeStore.get(context) }
                    val practice by practiceStore.entries.collectAsStateWithLifecycle()
                    FriendsPracticeTiles(
                        navController = navController,
                        practiceSummary = practiceSummary(practice),
                        onOpenPractice = { navController?.navigateSafely(Screen.Practice.route) },
                        enabled = !isSelectionMode
                    )
                }
                item(key = "your_playlists_header", contentType = "section_header") {
                    Text(
                        text = "Your playlists",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
                    )
                }
            }

            if (showPinnedPlaylists && localPlaylists.isEmpty()) {
                item(key = "playlists_empty_hint", contentType = "empty_hint") {
                    Text(
                        text = stringResource(R.string.playlist_container_new_playlist_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }
            }

            items(localPlaylists, key = { it.id }) { playlist ->
                val rememberedOnClick = remember(playlist.id) {
                    {
                        if (isAddingToPlaylist && currentSong != null && selectedPlaylists != null) {
                            val currentSelection = selectedPlaylists[playlist.id] ?: false
                            selectedPlaylists[playlist.id] = !currentSelection
                        } else if (isSelectionMode) {
                            onPlaylistSelectionToggle(playlist)
                        } else {
                            navController?.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                        }
                    }
                }
                val selectionIndex = remember(playlist.id, selectedPlaylistIds) {
                    if (selectedPlaylistIds.contains(playlist.id)) {
                        selectedPlaylistIds.toList().indexOf(playlist.id)
                    } else {
                        -1
                    }
                }
                val pinnedFriend = playlist.friendId?.let { friendsById[it] }
                PlaylistItem(
                    playlist = playlist,
                    playerViewModel = playerViewModel,
                    onClick = { rememberedOnClick() },
                    isAddingToPlaylist = isAddingToPlaylist,
                    selectedPlaylists = selectedPlaylists,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPlaylistIds.contains(playlist.id),
                    selectionIndex = selectionIndex,
                    onLongPress = { onPlaylistLongPress(playlist) },
                    onPlaylistSelectionToggle = { onPlaylistSelectionToggle(playlist) },
                    friendName = if (playlist.friendId != null) pinnedFriend?.name ?: playlist.ownerName.ifBlank { "Friend" } else null,
                    friendAvatarUrl = pinnedFriend?.avatarUrl,
                    modifier = Modifier.animateItem()
                )
            }

            if (!showPinnedPlaylists && friendPlaylists.isNotEmpty()) {
                item(key = "friends_section_header", contentType = "section_header") {
                    Text(
                        text = "Friends' Playlists",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                friendPlaylists.groupBy { it.friendId }.forEach { (friendId, playlistsForFriend) ->
                    val friendName = playlistsForFriend.firstOrNull()?.ownerName?.ifBlank { "Friend" } ?: "Friend"
                    item(key = "friend_header_${friendId ?: "default"}", contentType = "friend_header") {
                        Text(
                            text = friendName,
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(playlistsForFriend, key = { it.id }) { playlist ->
                        val rememberedOnClick = remember(playlist.id) {
                            {
                                if (isAddingToPlaylist && currentSong != null && selectedPlaylists != null) {
                                    val currentSelection = selectedPlaylists[playlist.id] ?: false
                                    selectedPlaylists[playlist.id] = !currentSelection
                                } else if (isSelectionMode) {
                                    onPlaylistSelectionToggle(playlist)
                                } else {
                                    navController?.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                                }
                            }
                        }
                        val selectionIndex = remember(playlist.id, selectedPlaylistIds) {
                            if (selectedPlaylistIds.contains(playlist.id)) {
                                selectedPlaylistIds.toList().indexOf(playlist.id)
                            } else {
                                -1
                            }
                        }
                        PlaylistItem(
                            playlist = playlist,
                            playerViewModel = playerViewModel,
                            onClick = { rememberedOnClick() },
                            isAddingToPlaylist = isAddingToPlaylist,
                            selectedPlaylists = selectedPlaylists,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedPlaylistIds.contains(playlist.id),
                            selectionIndex = selectionIndex,
                            onLongPress = { onPlaylistLongPress(playlist) },
                            onPlaylistSelectionToggle = { onPlaylistSelectionToggle(playlist) }
                        )
                    }
                }
            }
        }
        
        val bottomPadding = if (hasCurrentSong) 
            bottomBarHeight + MiniPlayerHeight + 16.dp 
        else 
            bottomBarHeight + 16.dp 

        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
            listState = listState,
            dragLabelProvider = playlistFastScrollLabelProvider
        )
    }
}

private const val PINNED_PLAYLIST_ITEM_COUNT = 0
/** Spotify | YT Music buttons, Friends dropdown, Practice button, "Your playlists" header. */
private const val HUB_ITEM_COUNT = 4

/**
 * A pinned, non-editable playlist shown at the top of the Playlists tab
 * ("Liked Songs" and "All Songs").
 */
@Composable
private fun PinnedSystemPlaylistItem(
    title: String,
    songCount: Int,
    subtitle: String? = null,
    iconRes: Int,
    containerColor: Color,
    iconTint: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle ?: formatSongCount(songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaylistItem(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    isAddingToPlaylist: Boolean,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int = -1,
    onLongPress: () -> Unit = {},
    onPlaylistSelectionToggle: () -> Unit = {},
    /** Set for a friend's pinned playlist: shows "by {friend}" with their picture. */
    friendName: String? = null,
    friendAvatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val playlistPreviewSongIds = remember(playlist.songIds) {
        playlist.songIds.take(4)
    }
    val playlistSongsInitialValue = remember(playlistPreviewSongIds) {
        if (playlistPreviewSongIds.isEmpty()) emptyList<Song>() else null
    }
    val playlistSongs by remember(playlistPreviewSongIds, playerViewModel) {
        playerViewModel.observeSongs(playlistPreviewSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = playlistSongsInitialValue)

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "playlistSelectionScaleAnimation"
    )

    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "playlistSelectionBorderAnimation"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isAddingToPlaylist -> MaterialTheme.colorScheme.surfaceContainerHigh
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 300),
        label = "playlistContainerColorAnimation"
    )

    val selectionBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 250),
        label = "playlistBorderColorAnimation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .then(
                if (isSelected && !isAddingToPlaylist) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = selectionBorderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onPlaylistSelectionToggle()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongPress()
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isThisPlaying = rememberIsPlaylistPlaying(playlist.id, playerViewModel)
            val playerIsPlaying by playerViewModel.stablePlayerState
                .map { it.isPlaying }
                .collectAsStateWithLifecycle(initialValue = false)
            Box {
                PlaylistCover(
                    playlist = playlist,
                    playlistSongs = playlistSongs ?: emptyList(),
                    size = 48.dp
                )
                PlaylistPlayingBadge(
                    visible = isThisPlaying,
                    isPlaying = playerIsPlaying,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
                    size = 20.dp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        color = if (isThisPlaying) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playlist.isAiGenerated) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.gemini_ai),
                            contentDescription = "AI Generated",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (friendName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (friendAvatarUrl != null) {
                            SmartImage(
                                model = friendAvatarUrl,
                                contentDescription = "$friendName's playlist",
                                modifier = Modifier.size(16.dp),
                                shape = CircleShape
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = friendName.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "by $friendName · ${formatSongCount(playlist.songIds.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = formatSongCount(playlist.songIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (playlist.source.equals("SPOTIFY", ignoreCase = true) || playlist.source.equals("YOUTUBE_MUSIC", ignoreCase = true)) {
                Spacer(modifier = Modifier.width(8.dp))
                SourceBadge(source = playlist.source)
            }

            if (isSelected && isSelectionMode) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectionIndex >= 0) {
                        Text(
                            text = (selectionIndex + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.common_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (isAddingToPlaylist && selectedPlaylists != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(
                    checked = selectedPlaylists[playlist.id] ?: false,
                    onCheckedChange = { isChecked -> selectedPlaylists[playlist.id] = isChecked }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialogRedesigned(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onGenerateClick: () -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.playlist_container_create_playlist_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.playlist_container_create_playlist_name_label)) },
                    placeholder = { Text(stringResource(R.string.playlist_container_create_playlist_name_placeholder)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.common_cancel), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = { onCreate(playlistName) },
                        modifier = Modifier.weight(1f),
                        enabled = playlistName.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.common_create), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
