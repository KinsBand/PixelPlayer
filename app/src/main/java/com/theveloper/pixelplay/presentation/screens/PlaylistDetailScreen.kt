package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.components.LocalSongPrimaryTap
import com.theveloper.pixelplay.presentation.components.handle
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import com.theveloper.pixelplay.presentation.components.SourceBadge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.QueuePlaylistSongItem
import com.theveloper.pixelplay.presentation.components.SongPickerBottomSheet
import com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel.Companion.FOLDER_PLAYLIST_PREFIX
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistSongsOrderMode
import com.theveloper.pixelplay.utils.formatSongCount
import com.theveloper.pixelplay.utils.formatTotalDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.theveloper.pixelplay.presentation.components.LibrarySortBottomSheet
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.PlaylistShapeType
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.graphics.drawable.toBitmap
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.youtube.OfflinePlaylistStore
import com.theveloper.pixelplay.presentation.components.PlaylistCover
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistDownloadState

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onDeletePlayListClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val songTap = LocalSongPrimaryTap.current
    val downloadState by playlistViewModel.downloadState.collectAsStateWithLifecycle()
    val downloadWifiOnly by playlistViewModel.downloadWifiOnly.collectAsStateWithLifecycle()
    val syncingPlaylistIds by playlistViewModel.syncingPlaylistIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fallbackPlaylistName = stringResource(R.string.common_playlist)
    val sortSongsLabel = stringResource(R.string.playlist_sort_songs_title)
    val moreOptionsLabel = stringResource(R.string.playlist_more_options_title)
    val shuffleLabel = stringResource(R.string.common_shuffle)
    val addLabel = stringResource(R.string.playlist_action_add_songs)
    val reorderLabel = stringResource(R.string.playlist_action_reorder_songs)
    val reorderSongCd = stringResource(R.string.playlist_cd_reorder_songs)
    val playlistEmptyTitle = stringResource(R.string.playlist_empty_title)
    val playlistEmptyFolder = stringResource(R.string.playlist_empty_folder_label)
    val playlistEmptyAddHint = stringResource(R.string.playlist_empty_add_hint)
    val playlistOptionsTitle = stringResource(R.string.playlist_options_title)
    val editPlaylistLabel = stringResource(R.string.playlist_action_edit_playlist)
    val deletePlaylistLabel = stringResource(R.string.playlist_action_delete_playlist)
    val setDefaultTransitionLabel = stringResource(R.string.playlist_action_set_default_transition)
    val exportPlaylistLabel = stringResource(R.string.playlist_action_export_playlist)
    val deletePlaylistConfirmTitle = stringResource(R.string.playlist_dialog_delete_title)
    val deletePlaylistConfirmBody = stringResource(R.string.playlist_dialog_delete_body)
    val sortSheetTitle = stringResource(R.string.playlist_sort_songs_title)
    val toastAddedToQueue = stringResource(R.string.library_toast_added_to_queue)
    val toastPlayingNext = stringResource(R.string.library_toast_playing_next)
    val currentPlaylist = uiState.currentPlaylistDetails
    val isFolderPlaylist = currentPlaylist?.id?.startsWith(FOLDER_PLAYLIST_PREFIX) == true
    val isGeneratedMix = currentPlaylist?.id?.startsWith(PlaylistViewModel.GENERATED_MIX_PREFIX) == true
    val isSystemPlaylist = PlaylistViewModel.isSystemPlaylistId(currentPlaylist?.id)
    val isLikedPlaylist = playlistId == PlaylistViewModel.LIKED_PLAYLIST_ID
    val isConnectedPlaylist = currentPlaylist?.source == "SPOTIFY" || currentPlaylist?.source == "YOUTUBE_MUSIC"
    // A friend's playlist shows who it belongs to (avatar + name) on the details line.
    val friendOwner = currentPlaylist?.friendId?.let { friendId ->
        val friendsViewModel: com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel = hiltViewModel()
        val friends by friendsViewModel.friends.collectAsStateWithLifecycle()
        friends.find { it.id == friendId }
    }
    // A friend's playlist can be pinned (liked) into Your playlists; it stays theirs and keeps syncing.
    val friendPinned: Pair<Boolean, (Boolean) -> Unit>? = currentPlaylist?.takeIf { it.friendId != null }?.let { playlist ->
        val friendsViewModel: com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel = hiltViewModel()
        val pinnedIds by friendsViewModel.pinnedPlaylistIds.collectAsStateWithLifecycle()
        (playlist.id in pinnedIds) to { pin: Boolean -> friendsViewModel.setPinned(playlist.id, pin) }
    }
    // YouTube Music playlists can be edited: changes are saved in the app and pushed to the account.
    val isEditableConnectedPlaylist = currentPlaylist?.source == "YOUTUBE_MUSIC"
    val isReadOnlyPlaylist = isFolderPlaylist || isGeneratedMix || isSystemPlaylist ||
        (isConnectedPlaylist && !isEditableConnectedPlaylist)
    val canDeletePlaylist = !isFolderPlaylist && !isGeneratedMix && !isSystemPlaylist
    val songsInPlaylist = uiState.currentPlaylistSongs

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    // Keep the pinned "Liked Songs" playlist in sync when songs are liked/unliked.
    if (isLikedPlaylist) {
        val likedIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
        var lastLikedIds by remember { mutableStateOf(likedIds) }
        LaunchedEffect(likedIds) {
            if (likedIds != lastLikedIds) {
                lastLikedIds = likedIds
                playlistViewModel.loadPlaylistDetails(playlistId)
            }
        }
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }
    var isReorderModeEnabled by remember { mutableStateOf(false) }
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deleteAlsoRemote by remember { mutableStateOf(false) }
    var showDownloadConfirm by remember { mutableStateOf<List<Song>?>(null) }
    var showDownloadedSheet by remember { mutableStateOf(false) }

    // ---- Multi-select (hold a song to start) ----
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSelectionPlaylistSheet by remember { mutableStateOf(false) }
    fun exitSelection() {
        isSelectionMode = false
        selectedIds = emptySet()
    }
    LaunchedEffect(playlistId) { exitSelection() }
    BackHandler(enabled = isSelectionMode) { exitSelection() }

    val snackbarHostState = remember { SnackbarHostState() }

    val m3uExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        uri?.let {
            currentPlaylist?.let { playlist ->
                playlistViewModel.exportM3u(playlist, it, context)
            }
        }
    }

    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var localReorderableSongs by remember(songsInPlaylist) { mutableStateOf(songsInPlaylist) }
    val localSongKeys = remember(localReorderableSongs) { uniqueSongKeys(localReorderableSongs) }
    val selectedSongs = remember(selectedIds, localReorderableSongs) {
        localReorderableSongs.filter { it.id in selectedIds }.distinctBy { it.id }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    // The header is list item 0, so song rows sit at index + HEADER_ITEM_COUNT.
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromIndex = from.index - HEADER_ITEM_COUNT
            val toIndex = to.index - HEADER_ITEM_COUNT
            if (fromIndex !in localReorderableSongs.indices || toIndex !in localReorderableSongs.indices) return@rememberReorderableLazyListState
            localReorderableSongs = localReorderableSongs.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = fromIndex
            }
            lastMovedTo = toIndex
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging, isReadOnlyPlaylist) {
        if (!isReadOnlyPlaylist && !reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
            currentPlaylist?.let {
                playlistViewModel.reorderSongsInPlaylist(it.id, lastMovedFrom!!, lastMovedTo!!)
            }
            lastMovedFrom = null
            lastMovedTo = null
        } else if (isReadOnlyPlaylist && !reorderableState.isAnyItemDragging) {
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    // Collapsed top bar shows the playlist name once the big title has scrolled away.
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { 320.dp.toPx() }
    val isHeaderCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
    }

    // Header gradient colour: taken from the cover (YouTube Music style), purple for Liked Songs.
    val surfaceColor = MaterialTheme.colorScheme.surface
    val fallbackAccent = MaterialTheme.colorScheme.primary
    val coverColorModel: Any? = when {
        isLikedPlaylist -> null
        currentPlaylist?.coverImageUri != null -> currentPlaylist.coverImageUri
        currentPlaylist?.coverColorArgb != null -> null
        else -> songsInPlaylist.firstOrNull { !it.albumArtUriString.isNullOrBlank() }?.albumArtUriString
    }
    var extractedAccent by remember(coverColorModel) { mutableStateOf<Color?>(null) }
    LaunchedEffect(coverColorModel) {
        if (coverColorModel != null) extractedAccent = extractAverageColor(context, coverColorModel)
    }
    val headerAccent = when {
        isLikedPlaylist -> LikedGradientTop
        currentPlaylist?.coverColorArgb != null && currentPlaylist.coverImageUri == null -> Color(currentPlaylist.coverColorArgb)
        else -> extractedAccent ?: fallbackAccent
    }
    val animatedAccent by animateColorAsState(targetValue = headerAccent, label = "headerAccent")

    val topBarContainerColor by animateColorAsState(
        targetValue = if (isHeaderCollapsed || isSelectionMode) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        label = "topBarContainer"
    )

    val onDownloadClick: () -> Unit = {
        when {
            downloadState.isDownloading -> playlistViewModel.cancelPlaylistDownload()
            downloadState.allLocal -> playerViewModel.sendToast("All songs are already on this device")
            downloadState.isComplete -> showDownloadedSheet = true
            else -> scope.launch {
                val missing = playlistViewModel.songsMissingDownload()
                if (missing.isEmpty()) {
                    showDownloadedSheet = true
                } else if (missing.size > 50 || !playlistViewModel.isOnUnmeteredNetwork()) {
                    showDownloadConfirm = missing
                } else {
                    playlistViewModel.downloadPlaylist(missing, downloadWifiOnly)
                }
            }
        }
    }

    val isRefreshing = currentPlaylist?.id?.let { it in syncingPlaylistIds } == true

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(
                    bottom = if (isSelectionMode) 96.dp else if (playerStableState.currentSong != null) MiniPlayerHeight else 0.dp
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading && currentPlaylist == null) {
                com.theveloper.pixelplay.presentation.components.DelayedSkeleton {
                    com.theveloper.pixelplay.presentation.components.DetailScreenSkeleton(headerHeight = 220.dp, showRowArt = true)
                }
            } else if (uiState.playlistNotFound) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(id = R.string.playlist_not_found)) }
            } else if (currentPlaylist == null) {
                com.theveloper.pixelplay.presentation.components.DelayedSkeleton {
                    com.theveloper.pixelplay.presentation.components.DetailScreenSkeleton(headerHeight = 220.dp, showRowArt = true)
                }
            } else {
                MaybePullToRefresh(
                    enabled = isConnectedPlaylist,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        if (!isSelectionMode && !isReorderModeEnabled) playlistViewModel.syncConnectedPlaylist(currentPlaylist.id)
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(
                            bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                if (isSelectionMode) 104.dp else 16.dp,
                            end = if (LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward)) 24.dp else 0.dp
                        )
                    ) {
                        item(key = "playlist_header", contentType = "playlist_header") {
                            PlaylistHeroHeader(
                                playlist = currentPlaylist,
                                songs = songsInPlaylist,
                                isLikedPlaylist = isLikedPlaylist,
                                isConnectedPlaylist = isConnectedPlaylist,
                                ownerName = friendOwner?.name ?: currentPlaylist.ownerName.takeIf { currentPlaylist.friendId != null && it.isNotBlank() },
                                ownerAvatarUrl = friendOwner?.avatarUrl,
                                accent = animatedAccent,
                                surfaceColor = surfaceColor,
                                fallbackName = fallbackPlaylistName,
                                downloadState = downloadState,
                                onDownloadClick = onDownloadClick,
                                // The big button shuffles (Shuffle was removed from the options menu).
                                onShuffleClick = {
                                    if (localReorderableSongs.isNotEmpty()) {
                                        playerViewModel.playSongsShuffled(
                                            songsToPlay = localReorderableSongs,
                                            queueName = currentPlaylist.name,
                                            playlistId = currentPlaylist.id,
                                            startAtZero = true,
                                        )
                                    }
                                },
                                shuffleEnabled = localReorderableSongs.isNotEmpty(),
                                shuffleLabel = shuffleLabel,
                                onMoreClick = { showPlaylistOptionsSheet = true },
                                moreOptionsLabel = moreOptionsLabel,
                                canEditSongs = !isReadOnlyPlaylist,
                                canReorder = !isReadOnlyPlaylist && localReorderableSongs.size > 1,
                                addLabel = addLabel,
                                reorderLabel = reorderLabel,
                                sortLabel = sortSongsLabel,
                                onAddClick = { showAddSongsSheet = true },
                                onReorderClick = {
                                    exitSelection()
                                    isReorderModeEnabled = true
                                },
                                onSortClick = { playerViewModel.showSortingSheet() }
                            )
                        }

                        if (isReorderModeEnabled) {
                            item(key = "reorder_banner", contentType = "banner") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.drag_order_icon),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Drag songs to reorder",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { isReorderModeEnabled = false }) {
                                        Text("Done")
                                    }
                                }
                            }
                        }

                        if (localReorderableSongs.isEmpty()) {
                            item(key = "empty_state", contentType = "empty") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 32.dp, start = 24.dp, end = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.MusicOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text(playlistEmptyTitle, style = MaterialTheme.typography.titleMedium)
                                    val emptyMessage = if (isFolderPlaylist) {
                                        playlistEmptyFolder
                                    } else if (isGeneratedMix) {
                                        "No songs in this mix"
                                    } else if (isLikedPlaylist) {
                                        "Songs you like will show up here"
                                    } else if (isSystemPlaylist) {
                                        "No songs in your library yet"
                                    } else {
                                        playlistEmptyAddHint
                                    }
                                    Text(
                                        emptyMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    if (!isReadOnlyPlaylist) {
                                        Spacer(Modifier.height(16.dp))
                                        FilledTonalButton(onClick = { showAddSongsSheet = true }) {
                                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(addLabel)
                                        }
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            localReorderableSongs,
                            // YouTube Music playlists can contain the same video more than once,
                            // so the song id alone is not a unique LazyColumn key.
                            key = { index, _ -> localSongKeys.getOrElse(index) { "playlist_song_$index" } },
                            contentType = { _, _ -> "playlist_song" }) { index, song ->
                            ReorderableItem(
                                state = reorderableState,
                                key = localSongKeys.getOrElse(index) { "playlist_song_$index" },
                                enabled = isReorderModeEnabled
                            ) { isDragging ->
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.05f else 1f,
                                    label = "dragScale"
                                )
                                val isSelected = song.id in selectedIds

                                QueuePlaylistSongItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - song.id else selectedIds + song.id
                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                        } else {
                                            // Add Song from lyrics: opens the action sheet instead.
                                            songTap.handle(song, localReorderableSongs, currentPlaylist.name) {
                                                playerViewModel.playSongs(
                                                    localReorderableSongs,
                                                    song,
                                                    currentPlaylist.name,
                                                    currentPlaylist.id
                                                )
                                            }
                                        }
                                    },
                                    onLongClick = if (isReorderModeEnabled) null else {
                                        {
                                            performAppCompatHapticFeedback(
                                                view,
                                                appHapticsConfig,
                                                HapticFeedbackConstantsCompat.LONG_PRESS
                                            )
                                            isSelectionMode = true
                                            selectedIds = if (isSelected) selectedIds - song.id else selectedIds + song.id
                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                        }
                                    },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    song = song,
                                    isCurrentSong = playerStableState.currentSong?.id == song.id,
                                    isPlaying = playerStableState.isPlaying,
                                    isDragging = isDragging,
                                    onRemoveClick = {
                                        if (!isReadOnlyPlaylist) {
                                            playlistViewModel.removeSongFromPlaylist(currentPlaylist.id, song.id)
                                        }
                                    },
                                    isFromPlaylist = true,
                                    showFriendAttribution = false,
                                    isReorderModeEnabled = isReorderModeEnabled,
                                    isDragHandleVisible = isReorderModeEnabled,
                                    isRemoveButtonVisible = false,
                                    onMoreOptionsClick = stableOnMoreOptionsClick,
                                    dragHandle = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        performAppCompatHapticFeedback(
                                                            view,
                                                            appHapticsConfig,
                                                            HapticFeedbackConstantsCompat.GESTURE_START
                                                        )
                                                    },
                                                    onDragStopped = {
                                                        performAppCompatHapticFeedback(
                                                            view,
                                                            appHapticsConfig,
                                                            HapticFeedbackConstantsCompat.GESTURE_END
                                                        )
                                                    }
                                                )
                                                .size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = reorderSongCd,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                ExpressiveScrollBar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            bottom = if (playerStableState.currentSong != null) MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                            end = 14.dp,
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
                        )
                )

                // ---- Top bar: back + collapsed title, or the selection bar ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(topBarContainerColor)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Exit selection")
                        }
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                            maxLines = 1
                        )
                        val allIds = remember(localReorderableSongs) { localReorderableSongs.mapTo(hashSetOf()) { it.id } }
                        val allSelected = allIds.isNotEmpty() && selectedIds.containsAll(allIds)
                        TextButton(
                            onClick = {
                                if (allSelected) exitSelection() else selectedIds = allIds
                            }
                        ) {
                            Text(if (allSelected) "Deselect all" else "Select all")
                        }
                    } else {
                        FilledTonalIconButton(
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isHeaderCollapsed) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Black.copy(alpha = 0.25f),
                                contentColor = if (isHeaderCollapsed) MaterialTheme.colorScheme.onSurface else Color.White
                            ),
                            onClick = onBackClick
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isHeaderCollapsed,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = currentPlaylist.name.ifBlank { fallbackPlaylistName },
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp)
                            )
                        }
                        if (!isHeaderCollapsed) Spacer(Modifier.weight(1f))
                        // Friend's playlist: pin it into Your playlists (keeps their name and picture).
                        friendPinned?.let { (pinned, setPinned) ->
                            FilledTonalIconButton(
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isHeaderCollapsed) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Black.copy(alpha = 0.25f),
                                    contentColor = if (isHeaderCollapsed) MaterialTheme.colorScheme.onSurface else Color.White
                                ),
                                onClick = { setPinned(!pinned) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = pinned,
                                    transitionSpec = {
                                        (androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate)) +
                                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate))) togetherWith
                                            (androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort3, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate)) +
                                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort3, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate)))
                                    },
                                    label = "friendPlaylistPinHeader"
                                ) { isPinned ->
                                    Icon(
                                        imageVector = if (isPinned) androidx.compose.material.icons.Icons.Rounded.Favorite else androidx.compose.material.icons.Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (isPinned) "Unpin from your playlists" else "Pin to your playlists"
                                    )
                                }
                            }
                        }
                        // Edit playlist (name / cover), top right of the playlist.
                        if (!isReadOnlyPlaylist) {
                            FilledTonalIconButton(
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isHeaderCollapsed) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Black.copy(alpha = 0.25f),
                                    contentColor = if (isHeaderCollapsed) MaterialTheme.colorScheme.onSurface else Color.White
                                ),
                                onClick = { showEditPlaylistDialog = true }
                            ) {
                                Icon(painterResource(R.drawable.rounded_edit_24), contentDescription = editPlaylistLabel)
                            }
                        }
                    }
                }

                // ---- Selection action bar ----
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                (if (playerStableState.currentSong != null) MiniPlayerHeight else 0.dp) + 10.dp
                        )
                ) {
                    val allSelectedLiked = selectedSongs.isNotEmpty() && selectedSongs.all { it.id in favoriteIds }
                    SelectionActionBar(
                        enabled = selectedSongs.isNotEmpty(),
                        allLiked = allSelectedLiked,
                        canRemove = !isReadOnlyPlaylist,
                        onLike = {
                            val songs = selectedSongs
                            val like = !allSelectedLiked
                            scope.launch {
                                val changed = playlistViewModel.setSongsLiked(songs, like)
                                exitSelection()
                                val skipped = songs.size - changed.size
                                val message = when {
                                    changed.isEmpty() && like -> "All ${songs.size} already liked"
                                    changed.isEmpty() -> "None of these were liked"
                                    like -> "Liked ${changed.size} song${if (changed.size == 1) "" else "s"}" +
                                        if (skipped > 0) " ($skipped already liked)" else ""
                                    else -> "Unliked ${changed.size} song${if (changed.size == 1) "" else "s"}"
                                }
                                val result = snackbarHostState.showSnackbar(
                                    message = message,
                                    actionLabel = if (changed.isNotEmpty()) "Undo" else null,
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    playlistViewModel.setSongsLiked(changed, !like)
                                }
                            }
                        },
                        onDownload = {
                            playlistViewModel.downloadSongs(selectedSongs)
                            exitSelection()
                        },
                        onAddToPlaylist = { showSelectionPlaylistSheet = true },
                        onPlayNext = {
                            playerViewModel.addSelectedAsNext(selectedSongs)
                            exitSelection()
                        },
                        onRemove = {
                            playlistViewModel.removeSongsFromPlaylist(currentPlaylist.id, selectedIds)
                            exitSelection()
                        }
                    )
                }
            }
        }
    }

    if (showSelectionPlaylistSheet && selectedSongs.isNotEmpty()) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = selectedSongs,
            onDismiss = {
                showSelectionPlaylistSheet = false
                exitSelection()
            },
            currentPlaylistId = playlistId,
            bottomBarHeight = bottomBarHeightDp,
            playerViewModel = playerViewModel,
        )
    }

    if (showAddSongsSheet && currentPlaylist != null && !isReadOnlyPlaylist) {
        SongPickerBottomSheet(
            initiallySelectedSongIds = currentPlaylist.songIds.toSet(),
            onDismiss = { showAddSongsSheet = false },
            onConfirm = { selectedIdsToAdd ->
                playlistViewModel.addSongsToPlaylist(currentPlaylist.id, selectedIdsToAdd.toList())
                showAddSongsSheet = false
            }
        )
    }

    if (showPlaylistOptionsSheet && currentPlaylist != null) {
        @Suppress("DEPRECATION")
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showPlaylistOptionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp,
        ) {
            com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = playlistOptionsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentPlaylist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Shuffle, Add songs, Reorder and Sort are buttons on the playlist header now;
                // Edit is the pencil at the top right; select songs by long-pressing a song.
                if (isConnectedPlaylist) {
                    PlaylistActionItem(
                        icon = rememberVectorPainter(Icons.Rounded.Sync),
                        label = if (currentPlaylist.source == "SPOTIFY") "Sync with Spotify" else "Sync with YouTube Music",
                        onClick = {
                            showPlaylistOptionsSheet = false
                            playlistViewModel.syncConnectedPlaylist(currentPlaylist.id)
                        }
                    )
                }
                if (downloadState.downloaded > 0 || downloadState.keepInSync) {
                    PlaylistActionItem(
                        icon = rememberVectorPainter(Icons.Rounded.DownloadDone),
                        label = if (downloadState.keepInSync) "Stop downloading new songs" else "Download new songs automatically",
                        onClick = {
                            showPlaylistOptionsSheet = false
                            playlistViewModel.setKeepPlaylistInSync(!downloadState.keepInSync)
                        }
                    )
                }
                if (!isReadOnlyPlaylist) {
                    PlaylistActionItem(
                        icon = painterResource(R.drawable.outline_graph_1_24),
                        label = setDefaultTransitionLabel,
                        onClick = {
                            showPlaylistOptionsSheet = false
                            navController.navigateSafely(Screen.EditTransition.createRoute(playlistId))
                        }
                    )
                }
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_attach_file_24),
                    label = exportPlaylistLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        val sanitizedName = PlaylistViewModel.sanitizeFileName(currentPlaylist.name.ifBlank { fallbackPlaylistName })
                        m3uExportLauncher.launch("$sanitizedName.m3u")
                    }
                )
                if (canDeletePlaylist) {
                    PlaylistActionItem(
                        icon = painterResource(R.drawable.rounded_delete_24),
                        label = if (isConnectedPlaylist) "Remove playlist" else deletePlaylistLabel,
                        onClick = {
                            showPlaylistOptionsSheet = false
                            deleteAlsoRemote = false
                            showDeleteConfirmation = true
                        }
                    )
                }
            }
        }
    }

    if (showDownloadedSheet && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDownloadedSheet = false },
            icon = { Icon(Icons.Rounded.DownloadDone, contentDescription = null) },
            title = { Text("Downloaded") },
            text = {
                Column {
                    Text(
                        "All ${downloadState.downloadable} online songs in this playlist are on your device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { playlistViewModel.setKeepPlaylistInSync(!downloadState.keepInSync) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Keep in sync", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Download songs added to this playlist later",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = downloadState.keepInSync,
                            onCheckedChange = { playlistViewModel.setKeepPlaylistInSync(it) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadedSheet = false }) { Text("Done") }
            }
        )
    }

    showDownloadConfirm?.let { missing ->
        var wifiOnly by remember(missing) { mutableStateOf(downloadWifiOnly) }
        val megabytes = remember(missing) { OfflinePlaylistStore.estimateMegabytes(missing) }
        AlertDialog(
            onDismissRequest = { showDownloadConfirm = null },
            icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
            title = { Text("Download ${missing.size} song${if (missing.size == 1) "" else "s"}?") },
            text = {
                Column {
                    Text(
                        "About ${if (megabytes >= 1000) String.format(java.util.Locale.US, "%.1f GB", megabytes / 1000f) else "$megabytes MB"}. " +
                            "Songs added to this playlist later will download too.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { wifiOnly = !wifiOnly },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                        Text("Only download on Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    playlistViewModel.downloadPlaylist(missing, wifiOnly)
                    showDownloadConfirm = null
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadConfirm = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showEditPlaylistDialog && currentPlaylist != null) {
        val initialShapeType = try {
            currentPlaylist.coverShapeType?.let { PlaylistShapeType.valueOf(it) } ?: PlaylistShapeType.Circle
        } catch (e: Exception) {
            PlaylistShapeType.Circle
        }
        
        EditPlaylistDialog(
            visible = showEditPlaylistDialog,
            currentName = currentPlaylist.name,
            currentImageUri = currentPlaylist.coverImageUri,
            currentColor = currentPlaylist.coverColorArgb,
            currentIconName = currentPlaylist.coverIconName,
            currentShapeType = initialShapeType,
            currentShapeDetail1 = currentPlaylist.coverShapeDetail1,
            currentShapeDetail2 = currentPlaylist.coverShapeDetail2,
            currentShapeDetail3 = currentPlaylist.coverShapeDetail3,
            currentShapeDetail4 = currentPlaylist.coverShapeDetail4,
            onDismiss = { showEditPlaylistDialog = false },
            onSave = { name, imageUri, color, icon, scale, panX, panY, shapeType, d1, d2, d3, d4 ->
                playlistViewModel.updatePlaylistParameters(
                    playlistId = currentPlaylist.id,
                    name = name,
                    coverImageUri = imageUri,
                    coverColor = color,
                    coverIcon = icon,
                    cropScale = scale,
                    cropPanX = panX,
                    cropPanY = panY,
                    coverShapeType = shapeType,
                    coverShapeDetail1 = d1,
                    coverShapeDetail2 = d2,
                    coverShapeDetail3 = d3,
                    coverShapeDetail4 = d4
                )
                showEditPlaylistDialog = false
            }
        )
    }
    if (showDeleteConfirmation && currentPlaylist != null) {
        val isYouTubeMusic = currentPlaylist.source == "YOUTUBE_MUSIC"
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(if (isConnectedPlaylist) "Remove playlist?" else deletePlaylistConfirmTitle) },
            text = {
                if (isConnectedPlaylist) {
                    Column {
                        Text(
                            "\"${currentPlaylist.name}\" will be removed from PixelPlayer and stay hidden after every sync. " +
                                "You can bring it back from Accounts → Hidden playlists.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (isYouTubeMusic && currentPlaylist.friendId == null) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { deleteAlsoRemote = !deleteAlsoRemote },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = deleteAlsoRemote, onCheckedChange = { deleteAlsoRemote = it })
                                Column(Modifier.weight(1f)) {
                                    Text("Also delete from YouTube Music", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Deletes it from your account if it's yours, or removes it from your library if you saved someone else's. This can't be undone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(deletePlaylistConfirmBody)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistViewModel.deletePlaylist(currentPlaylist.id, alsoRemote = deleteAlsoRemote)
                        onDeletePlayListClick()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(
                        if (isConnectedPlaylist) "Remove" else stringResource(R.string.common_delete),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) {
            derivedStateOf {
                currentSong?.let {
                    favoriteIds.contains(
                        it.id
                    )
                }
            }
        }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    // Directly use PlayerViewModel's method to toggle, which should handle UserPreferencesRepository
                    playerViewModel.toggleFavoriteSpecificSong(currentSong) // Assumes such a method exists or will be added to PlayerViewModel
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong) // Assumes such a method exists or will be added
                    playerViewModel.sendToast(toastAddedToQueue)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    playerViewModel.sendToast(toastPlayingNext)
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
                        navController.navigateSafelyReplacing(
                            route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                            patternToPop = Screen.GenreDetail.route
                        )
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
                removeFromListTrigger = {
                    playlistViewModel.removeSongFromPlaylist(playlistId, currentSong.id)
                }
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(currentSong),
                    onDismiss = {
                        showPlaylistBottomSheet = false
                    },
                    currentPlaylistId = playlistId,
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }

    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()

    if (isSortSheetVisible) {
        // Check if playlist is in Manual mode (which corresponds to Default Order)
        val isManualMode = uiState.playlistSongsOrderMode is PlaylistSongsOrderMode.Manual
        val rawOption = uiState.currentPlaylistSongsSortOption
        // If in Manual mode, show SongDefaultOrder as selected; otherwise use the stored sort option
        val currentSortOption = if (isManualMode) {
            SortOption.SongDefaultOrder
        } else if (currentPlaylist != null) {
            rawOption
        } else {
            SortOption.SongTitleAZ
        }

        // Build options list inline to avoid potential static initialization issues
        val songSortOptions = listOf(
            SortOption.SongDefaultOrder,
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongArtistDesc,
            SortOption.SongAlbum,
            SortOption.SongAlbumDesc,
            SortOption.SongDateAdded,
            SortOption.SongDateAddedAsc,
            SortOption.SongDuration,
            SortOption.SongDurationAsc
        )

        LibrarySortBottomSheet(
            title = sortSheetTitle,
            options = songSortOptions,
            selectedOption = currentSortOption,
            onDismiss = { playerViewModel.hideSortingSheet() },
            onOptionSelected = { option ->
                 playlistViewModel.sortPlaylistSongs(option)
                 playerViewModel.hideSortingSheet()
                 // Auto-scroll to first item after sorting (delay to allow list to update)
                 scope.launch {
                     kotlinx.coroutines.delay(100)
                     listState.animateScrollToItem(0)
                 }
            },
            onDirectionToggle = { option ->
                playlistViewModel.sortPlaylistSongs(option)
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(0)
                }
            },
            showViewToggle = false 
        )
    }
}


@Composable
private fun PlaylistActionItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Stable, unique keys for song rows even when a playlist repeats the same track. */
internal fun uniqueSongKeys(songs: List<Song>): List<String> {
    val seen = HashMap<String, Int>(songs.size)
    return songs.map { song ->
        val occurrence = (seen[song.id] ?: 0) + 1
        seen[song.id] = occurrence
        if (occurrence == 1) song.id else "${song.id}#$occurrence"
    }
}

/** Number of LazyColumn items above the first song row (the hero header). */
private const val HEADER_ITEM_COUNT = 1

private val LikedGradientTop = Color(0xFF9B6BF2)
private val LikedGradientBottom = Color(0xFFFF5FAE)

/** Average colour of a cover image (small software bitmap), used to tint the header gradient. */
private suspend fun extractAverageColor(context: android.content.Context, model: Any): Color? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val request = coil.request.ImageRequest.Builder(context)
                .data(model)
                .size(48)
                .allowHardware(false)
                .build()
            val result = coil.Coil.imageLoader(context).execute(request)
            val bitmap = (result as? coil.request.SuccessResult)?.drawable?.toBitmap(24, 24)
                ?: return@withContext null
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (x in 0 until bitmap.width) for (y in 0 until bitmap.height) {
                val p = bitmap.getPixel(x, y)
                val pr = (p shr 16) and 0xFF; val pg = (p shr 8) and 0xFF; val pb = p and 0xFF
                // Skip near-black / near-white pixels so borders don't wash the colour out.
                val max = maxOf(pr, pg, pb); val min = minOf(pr, pg, pb)
                if (max < 24 || min > 235) continue
                r += pr; g += pg; b += pb; n++
            }
            if (n == 0L) null else {
                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
                // Boost saturation a little and keep it mid-bright so white text stays readable.
                hsv[1] = (hsv[1] * 1.25f).coerceIn(0.25f, 0.9f)
                hsv[2] = hsv[2].coerceIn(0.35f, 0.7f)
                Color(android.graphics.Color.HSVToColor(hsv))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

/**
 * YouTube Music–style header: big centred cover on a gradient taken from the cover, title,
 * "[friend ·] songs · duration" line, then Download · Shuffle · More.
 */
@Composable
private fun PlaylistHeroHeader(
    playlist: Playlist,
    songs: List<Song>,
    isLikedPlaylist: Boolean,
    isConnectedPlaylist: Boolean,
    /** Set for a friend's playlist: shown with [ownerAvatarUrl] before the song count. */
    ownerName: String?,
    ownerAvatarUrl: String?,
    accent: Color,
    surfaceColor: Color,
    fallbackName: String,
    downloadState: PlaylistDownloadState,
    onDownloadClick: () -> Unit,
    onShuffleClick: () -> Unit,
    shuffleEnabled: Boolean,
    shuffleLabel: String,
    onMoreClick: () -> Unit,
    moreOptionsLabel: String,
    canEditSongs: Boolean,
    canReorder: Boolean,
    addLabel: String,
    reorderLabel: String,
    sortLabel: String,
    onAddClick: () -> Unit,
    onReorderClick: () -> Unit,
    onSortClick: () -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.85f),
                    0.45f to accent.copy(alpha = 0.35f),
                    0.8f to surfaceColor,
                    1f to surfaceColor
                )
            )
    ) {
        val coverSize = (maxWidth * 0.56f).coerceAtMost(300.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop + 64.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLikedPlaylist -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(LikedGradientTop, LikedGradientBottom))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ThumbUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(coverSize * 0.5f)
                        )
                    }
                    // A blank cover (some connected playlists report "") falls through to the collage.
                    !playlist.coverImageUri.isNullOrBlank() -> SmartImage(
                        model = playlist.coverImageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        targetSize = Size(720, 720)
                    )
                    else -> PlaylistCover(
                        playlist = playlist.copy(coverShapeType = null),
                        playlistSongs = songs,
                        size = coverSize
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = playlist.name.ifBlank { fallbackName },
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ownerName != null) {
                    if (ownerAvatarUrl != null) {
                        SmartImage(
                            model = ownerAvatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                ownerName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = ownerName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "  ·  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(
                        R.string.playlist_song_duration_line,
                        formatSongCount(songs.size),
                        formatTotalDuration(songs)
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isConnectedPlaylist) {
                    Spacer(Modifier.width(8.dp))
                    SourceBadge(playlist.source)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistDownloadButton(state = downloadState, onClick = onDownloadClick)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (shuffleEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        .clickable(enabled = shuffleEnabled, onClick = onShuffleClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = shuffleLabel,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onMoreClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = moreOptionsLabel)
                }
            }

            // Second row: Add songs + Reorder side by side, and Sort as an icon.
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canEditSongs) {
                    FilledTonalButton(onClick = onAddClick) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(addLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (canReorder) {
                        FilledTonalButton(onClick = onReorderClick) {
                            Icon(painterResource(R.drawable.drag_order_icon), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(reorderLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                FilledTonalIconButton(onClick = onSortClick) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = sortLabel)
                }
            }
        }
    }
}

/** Download button: arrow → progress ring "18/42" (tap to cancel) → tick when everything is on the device. */
@Composable
private fun PlaylistDownloadButton(state: PlaylistDownloadState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val complete = state.isComplete && !state.isDownloading
    val container by animateColorAsState(
        targetValue = if (complete) colors.primary else colors.surfaceContainerHigh,
        label = "downloadContainer"
    )
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isDownloading -> {
                val progress = if (state.downloadable > 0) state.downloaded.toFloat() / state.downloadable else 0f
                val animated by animateFloatAsState(targetValue = progress, label = "downloadProgress")
                CircularProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.size(46.dp),
                    strokeWidth = 3.dp,
                    trackColor = colors.onSurface.copy(alpha = 0.15f)
                )
                Text(
                    text = "${state.downloaded}/${state.downloadable}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (state.downloadable >= 100) 8.sp else 10.sp,
                    maxLines = 1
                )
            }
            complete -> Icon(
                imageVector = Icons.Rounded.DownloadDone,
                contentDescription = "Downloaded",
                tint = colors.onPrimary
            )
            else -> Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Download playlist",
                    tint = colors.onSurface
                )
                if (state.isPartial || state.failed > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-4).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.failed > 0) colors.error else colors.primary)
                    )
                }
            }
        }
    }
}

/** Bottom bar shown while songs are selected. */
@Composable
private fun SelectionActionBar(
    enabled: Boolean,
    allLiked: Boolean,
    canRemove: Boolean,
    onLike: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SelectionAction(
                icon = if (allLiked) Icons.Rounded.HeartBroken else Icons.Rounded.Favorite,
                label = if (allLiked) "Unlike" else "Like",
                enabled = enabled,
                highlight = !allLiked,
                onClick = onLike
            )
            SelectionAction(Icons.Rounded.Download, "Download", enabled, onClick = onDownload)
            SelectionAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to", enabled, onClick = onAddToPlaylist)
            SelectionAction(Icons.Rounded.SkipNext, "Play next", enabled, onClick = onPlayNext)
            if (canRemove) SelectionAction(Icons.Rounded.RemoveCircleOutline, "Remove", enabled, onClick = onRemove)
        }
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        highlight -> colors.error
        else -> colors.onSurface
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/** Pull down to sync for connected playlists; a plain box otherwise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaybePullToRefresh(
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    if (enabled) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) { content() }
    } else {
        Box(Modifier.fillMaxSize()) { content() }
    }
}
