package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.presentation.components.subcomps.rememberShuffleSpin
import com.theveloper.pixelplay.presentation.components.subcomps.shuffleSpin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.animation.togetherWith
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.RemoveDone
import com.theveloper.pixelplay.data.model.HeardSongItem
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.components.player.AnimatedPlaybackControls
import com.theveloper.pixelplay.presentation.viewmodel.PlayerUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import com.theveloper.pixelplay.presentation.components.scoped.QueueItemDismissGestureHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import coil.size.Size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.RandomAccess
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.math.abs

private data class QueueUndoBarProjection(
    val isVisible: Boolean = false,
    val removedSongTitle: String = ""
)

private fun PlayerUiState.toQueueUndoBarProjection(): QueueUndoBarProjection =
    QueueUndoBarProjection(
        isVisible = showQueueItemUndoBar,
        removedSongTitle = lastRemovedQueueSong?.title.orEmpty()
    )

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun QueueBottomSheet(
    viewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    friendsViewModel: com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel = hiltViewModel(),
    queue: List<Song>,
    currentQueueSourceName: String,
    currentSongId: String?,
    currentMediaItemIndex: Int = -1,
    isVisible: Boolean,
    isPlaying: Boolean,
    repeatMode: Int,
    isShuffleOn: Boolean,
    onDismiss: () -> Unit,
    onSongInfoClick: (Song) -> Unit,
    onPlaySong: (Song, Int) -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClearQueue: () -> Unit,
    activeTimerValueDisplay: androidx.compose.runtime.State<String?>,
    activeTimerDurationMinutes: androidx.compose.runtime.State<Int?>,
    playCount: androidx.compose.runtime.State<Float>,
    isEndOfTrackTimerActive: androidx.compose.runtime.State<Boolean>,
    onSetPredefinedTimer: (minutes: Int) -> Unit,
    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit,
    onCancelCountedPlay: () -> Unit,
    onPlayCounter: (count: Int) -> Unit,
    onRequestSaveAsPlaylist: (
        songs: List<Song>,
        defaultName: String,
        onConfirm: (String, Set<String>) -> Unit
    ) -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    predictiveBackProgress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    predictiveBackSwipeEdge: androidx.compose.runtime.State<Int?>,
    queueSheetOffset: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 10.dp,
    shape: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    /** Opens the album / playlist / artist / Friends page the queue came from. */
    onOpenQueueSource: (QueueSourceLink) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    var showTimerOptions by rememberSaveable { mutableStateOf(false) }
    var showClearQueueDialog by remember { mutableStateOf(false) }
    var showFriendsInRoomSheet by rememberSaveable { mutableStateOf(false) }
    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    // History above the playing song stays folded behind a full-width button until opened.
    var isHistoryExpanded by rememberSaveable { mutableStateOf(false) }
    // Heard (songs picked up by Listen / Now Playing) opens the same way, from the other half.
    var isHeardExpanded by rememberSaveable { mutableStateOf(false) }
    // Hoist resource strings at composition time so they react to locale changes
    // and can be safely captured in onClick lambdas.
    val queueNamedSuffixTemplate = stringResource(R.string.queue_save_playlist_named)
    val queueCurrentLabel = stringResource(R.string.queue_save_playlist_current)

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            showTimerOptions = false
            showClearQueueDialog = false
            showFriendsInRoomSheet = false
            isFabExpanded = false
            isHistoryExpanded = false
            isHeardExpanded = false
        }
    }

    BackHandler(enabled = isVisible && isFabExpanded) {
        isFabExpanded = false
    }

    // Use the real player index from MediaController if available to resolve duplicates.
    // Fall back to ID search only if index is invalid (-1).
    val currentSongIndex = remember(queue, currentSongId, currentMediaItemIndex) {
        if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == currentSongId) {
            currentMediaItemIndex
        } else {
            queue.indexOfFirst { it.id == currentSongId }
        }
    }

    // "Show queue history" now only shows or hides the History button below; the queue
    // itself always starts at the playing song.
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val showQueueHistory = settingsState.showQueueHistory
    val heardSongs by viewModel.heardSongs.collectAsStateWithLifecycle()
    val ambientSuggestionsEnabled by viewModel.ambientSuggestionsEnabled.collectAsStateWithLifecycle()
    val setListenEnabled = rememberListenAction()

    // The page this queue came from (album, playlist, friends' playlist, artist…), if any.
    // Keyed on the queue's size, not its contents, so a long queue isn't compared each frame.
    val nowPlayingPlaylist by com.theveloper.pixelplay.data.playback.NowPlayingPlaylist.current
        .collectAsStateWithLifecycle()
    val queueSourceLink = remember(currentQueueSourceName, nowPlayingPlaylist, queue.size) {
        QueueSourceLinks.resolve(currentQueueSourceName, nowPlayingPlaylist, queue)
    }

    // Offset to convert display indices to queue indices (songs before the current one are
    // never listed; they're in the History section instead).
    val queueIndexOffset = if (currentSongIndex < 0) 0 else currentSongIndex

    // Only from the current song. Use subList when possible to avoid copying large queues.
    val displaySongs = remember(queue, queueIndexOffset) {
        if (queueIndexOffset == 0) {
            queue
        } else if (queue is RandomAccess) {
            queue.subList(queueIndexOffset, queue.size)
        } else {
            queue.drop(queueIndexOffset)
        }
    }

    // Calculate the display index of the current song (depends on whether we show history or not).
    val currentSongDisplayIndex = remember(currentSongIndex, queueIndexOffset) {
        if (currentSongIndex < 0) -1 else currentSongIndex - queueIndexOffset
    }
    // The playing card's height, so the chip strip under it starts and ends where its curve does.
    var currentCardHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // Deep right swipe on an up-next row: the "after which song" (Soon) slider opens under it.
    var queueSoonFor by remember { mutableStateOf<String?>(null) }
    // Playing card's artist tapped with 2+ artists: the sideways picker (combined page first).
    var artistPickerFor by remember { mutableStateOf<Song?>(null) }
    val openArtistsOf: (Song) -> Unit = { song ->
        if (!com.theveloper.pixelplay.data.radio.RadioPlayback.isRadio(song)) {
            val fromTags = song.artists.takeIf { it.size > 1 }.orEmpty()
                .map { com.theveloper.pixelplay.data.model.Artist(id = it.id, name = it.name, songCount = 0) }
            val artists = com.theveloper.pixelplay.presentation.components.player.pickerArtistsFor(song, fromTags)
            if (artists.size > 1) {
                artistPickerFor = song
            } else {
                // One artist: straight to their page (same path as the full player).
                val only = artists.singleOrNull()
                onDismiss()
                viewModel.triggerArtistNavigationFromPlayer(only?.id ?: song.artistId, only?.name ?: song.primaryArtist.name)
            }
        }
    }
    artistPickerFor?.let { pickerSong ->
        val fromTags = pickerSong.artists.takeIf { it.size > 1 }.orEmpty()
            .map { com.theveloper.pixelplay.data.model.Artist(id = it.id, name = it.name, songCount = 0) }
        val pickerArtists = remember(pickerSong.id) {
            com.theveloper.pixelplay.presentation.components.player.pickerArtistsFor(pickerSong, fromTags)
        }
        val pickerSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        com.theveloper.pixelplay.presentation.components.player.PlayerArtistPickerBottomSheet(
            song = pickerSong,
            artists = pickerArtists,
            sheetState = pickerSheetState,
            onDismiss = { artistPickerFor = null },
            onArtistClick = { artist ->
                artistPickerFor = null
                onDismiss()
                viewModel.triggerArtistNavigationFromPlayer(artist.id, artist.name)
            }
        )
    }

    val listState = rememberLazyListState()
    val queueCoroutineScope = rememberCoroutineScope()
    val displaySongCount = displaySongs.size

    // History: songs played before the current one, above it in the list. Songs the queue
    // itself still shows above the current song (or the current song) aren't repeated.
    val playHistory by viewModel.playHistory.collectAsStateWithLifecycle()
    // Why the mix picked each of its songs, and which are discoveries (sparkle unless liked).
    val mixInsights by viewModel.mixSongInsights.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val historySongs = remember(playHistory, displaySongs, currentSongDisplayIndex, currentSongId, showQueueHistory) {
        if (!showQueueHistory) return@remember emptyList<Song>()
        val shownAbove = displaySongs.take((currentSongDisplayIndex + 1).coerceAtLeast(0))
            .mapTo(HashSet()) { it.id }
        playHistory.filterNot { it.id in shownAbove || it.id == currentSongId }
    }
    // LazyColumn rows the history adds before the queue: the History button alone while folded,
    // the header + songs while open.
    // History | Heard bar: History on one half, Heard on the other (only while there are heard
    // songs). Opening one turns the other half into its Clear button.
    val showHistoryHeardBar = historySongs.isNotEmpty() || heardSongs.isNotEmpty()
    // Nothing left to show: close it (the half goes away with it).
    LaunchedEffect(heardSongs.isEmpty()) { if (heardSongs.isEmpty()) isHeardExpanded = false }
    LaunchedEffect(historySongs.isEmpty()) { if (historySongs.isEmpty()) isHistoryExpanded = false }
    // Landscape: a sidebar. The left half is fixed (header, the playing song with its mix chips,
    // the History button, then as many upcoming songs as fit); the queue carries on in the right
    // half, which scrolls, with the toolbar at its bottom.
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var leftSlotsAreaPx by remember { mutableIntStateOf(0) }
    var leftSlotRowPx by remember { mutableIntStateOf(0) }
    val queueDensity = LocalDensity.current
    val leftSlotSpacingPx = with(queueDensity) { 8.dp.roundToPx() }
    val leftSlotRowEstimatePx = with(queueDensity) { 72.dp.roundToPx() }
    val leftQueueSlots = if (!isLandscape || leftSlotsAreaPx <= 0) 0 else {
        val rowPx = if (leftSlotRowPx > 0) leftSlotRowPx else leftSlotRowEstimatePx
        ((leftSlotsAreaPx + leftSlotSpacingPx) / (rowPx + leftSlotSpacingPx)).coerceAtLeast(0)
    }
    val leftSlotStart = if (currentSongDisplayIndex >= 0) currentSongDisplayIndex + 1 else 0
    val leftSlotEnd = (leftSlotStart + leftQueueSlots).coerceIn(leftSlotStart.coerceAtMost(displaySongCount), displaySongCount)
    // First display index the scrolling list shows (0 in portrait: it shows everything).
    val listStartIndex = if (isLandscape) leftSlotEnd.coerceAtLeast(0) else (currentSongDisplayIndex + 1).coerceIn(0, displaySongCount)

    // Opening/closing the history adds/removes rows ABOVE the History button. Keep the button
    // where it is on screen, so the history grows upwards from it (newest right above it).
    var historyAnchorPx by remember { mutableStateOf<Int?>(null) }
    fun toggleHistory(open: Boolean) {
        historyAnchorPx = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == "play_history_header" }?.offset
        isHistoryExpanded = open
        if (open) isHeardExpanded = false
        // Landscape: the button is on the left and the history rows open at the top of the
        // right half, so bring them into view.
        if (isLandscape && open) {
            queueCoroutineScope.launch {
                withFrameNanos { }
                runCatching { listState.animateScrollToItem(0) }
            }
        }
    }
    fun toggleHeard(open: Boolean) {
        historyAnchorPx = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == "play_history_header" }?.offset
        isHeardExpanded = open
        if (open) isHistoryExpanded = false
        if (isLandscape && open) {
            queueCoroutineScope.launch {
                withFrameNanos { }
                runCatching { listState.animateScrollToItem(0) }
            }
        }
    }
    LaunchedEffect(isHistoryExpanded, isHeardExpanded) {
        val anchor = historyAnchorPx ?: return@LaunchedEffect
        historyAnchorPx = null
        withFrameNanos { } // let the rows be composed first
        // spacer + the open list's rows come before the bar.
        val headerIndex = 1 + (if (isHistoryExpanded) historySongs.size else 0) +
            (if (isHeardExpanded) heardSongs.size else 0)
        runCatching {
            listState.scrollToItem(headerIndex)
            if (anchor > 0) listState.scrollBy(-anchor.toFloat())
        }
    }

    // Local order used only while previewing a drag reorder.
    var reorderPreviewOrder by remember { mutableStateOf<androidx.compose.runtime.snapshots.SnapshotStateList<Int>?>(null) }
    var reorderPreviewKeys by remember { mutableStateOf<androidx.compose.runtime.snapshots.SnapshotStateList<Long>?>(null) }
    val dragKeyToIndex = remember { HashMap<Long, Int>() }
    var reorderPreviewBaseQueue by remember { mutableStateOf<List<Song>?>(null) }
    var pendingReorderExpectedIds by remember { mutableStateOf<List<String>?>(null) }
    var pendingReorderGraceUpdates by remember { mutableIntStateOf(0) }

    // Stable keys for queue rows to prevent state recycling glitches on remove/reorder.
    // Start empty so opening the sheet does not eagerly allocate IDs/keys for the entire queue.
    var committedDisplaySongIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var committedDisplayKeys by remember { mutableStateOf<List<Long>>(emptyList()) }
    var nextStableQueueItemKey by remember { mutableLongStateOf(0L) }

    // Track queue order by content (not list identity) to avoid clearing preview
    // when upstream emits equivalent list instances during drag.
    var reorderPreviewQueueSignature by remember { mutableStateOf<Int?>(null) }
    val displaySongsSignature = remember(displaySongs, queueIndexOffset) {
        (queueIndexOffset * 31) + System.identityHashCode(displaySongs)
    }

    // --- REORDER STATE ---
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }
    var reorderHandleInUse by remember { mutableStateOf(false) }
    val updatedReorderHandleInUse by rememberUpdatedState(reorderHandleInUse)

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (reorderPreviewOrder == null) {
                reorderPreviewBaseQueue = queue
                reorderPreviewOrder = androidx.compose.runtime.mutableStateListOf<Int>().apply {
                    addAll(List(displaySongCount) { queueIndexOffset + it })
                }
                reorderPreviewKeys = androidx.compose.runtime.mutableStateListOf<Long>().apply {
                    addAll(committedDisplayKeys.takeIf { it.size == displaySongCount }
                        ?: List(displaySongCount) { (queueIndexOffset + it).toLong() })
                }
                dragKeyToIndex.clear()
                reorderPreviewKeys!!.forEachIndexed { index, key -> dragKeyToIndex[key] = index }
            }
            val currentOrder = reorderPreviewOrder ?: return@rememberReorderableLazyListState
            val currentKeys = reorderPreviewKeys ?: return@rememberReorderableLazyListState
            val fromLocalIndex = dragKeyToIndex[from.key] ?: return@rememberReorderableLazyListState
            val toLocalIndex = dragKeyToIndex[to.key] ?: return@rememberReorderableLazyListState
            if (fromLocalIndex == toLocalIndex) return@rememberReorderableLazyListState
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                currentOrder.add(toLocalIndex, currentOrder.removeAt(fromLocalIndex))
                currentKeys.add(toLocalIndex, currentKeys.removeAt(fromLocalIndex))
            }
            for (index in minOf(fromLocalIndex, toLocalIndex)..maxOf(fromLocalIndex, toLocalIndex)) {
                dragKeyToIndex[currentKeys[index]] = index
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = fromLocalIndex
            }
            lastMovedTo = toLocalIndex
        },
    )
    val isReordering by remember {
        derivedStateOf { reorderableState.isAnyItemDragging }
    }
    val updatedIsReordering by rememberUpdatedState(isReordering)

    fun remapCommittedKeysForDisplay(newSongs: List<Song>) {
        // Fast path: common queue-skip case where display list is just a suffix of previous display list.
        if (committedDisplaySongIds.isNotEmpty() && newSongs.isNotEmpty()) {
            val firstNewId = newSongs.first().id
            val startIndex = committedDisplaySongIds.indexOf(firstNewId)
            if (startIndex >= 0 && startIndex + newSongs.size <= committedDisplaySongIds.size) {
                var suffixMatches = true
                for (i in newSongs.indices) {
                    if (committedDisplaySongIds[startIndex + i] != newSongs[i].id) {
                        suffixMatches = false
                        break
                    }
                }
                if (suffixMatches) {
                    committedDisplaySongIds = committedDisplaySongIds.subList(startIndex, startIndex + newSongs.size).toList()
                    committedDisplayKeys = committedDisplayKeys.subList(startIndex, startIndex + newSongs.size).toList()
                    return
                }
            }
        }

        val reusableKeysBySongId = mutableMapOf<String, ArrayDeque<Long>>()
        committedDisplaySongIds.forEachIndexed { index, songId ->
            val key = committedDisplayKeys.getOrNull(index) ?: return@forEachIndexed
            reusableKeysBySongId.getOrPut(songId) { ArrayDeque() }.addLast(key)
        }

        var nextKey = nextStableQueueItemKey
        if (committedDisplaySongIds.isEmpty() && committedDisplayKeys.isEmpty()) {
            nextKey = queueIndexOffset.toLong()
        }
        val newKeys = ArrayList<Long>(newSongs.size)
        newSongs.forEach { song ->
            val bucket = reusableKeysBySongId[song.id]
            val reusedKey = if (bucket != null && bucket.isNotEmpty()) bucket.removeFirst() else null
            if (reusedKey != null) {
                newKeys.add(reusedKey)
            } else {
                newKeys.add(nextKey)
                nextKey++
            }
        }

        committedDisplaySongIds = newSongs.map { it.id }
        committedDisplayKeys = newKeys
        nextStableQueueItemKey = nextKey
    }

    // Reset local reorder preview only when the queue truly changes to something new.
    if (reorderPreviewQueueSignature != displaySongsSignature) {
        val expectedIds = pendingReorderExpectedIds
        var isProcessed = false

        if (expectedIds != null) {
            val currentDisplayIds = displaySongs.map { it.id }
            if (currentDisplayIds == expectedIds) {
                reorderPreviewKeys
                    ?.takeIf { it.size == displaySongs.size }
                    ?.let { previewKeys ->
                        committedDisplaySongIds = currentDisplayIds
                        committedDisplayKeys = previewKeys.toList()
                    }
                reorderPreviewOrder = null
                reorderPreviewKeys = null
                reorderPreviewBaseQueue = null
                pendingReorderExpectedIds = null
                pendingReorderGraceUpdates = 0
                remapCommittedKeysForDisplay(displaySongs)
                reorderPreviewQueueSignature = displaySongsSignature
                isProcessed = true
            } else if (reorderPreviewOrder != null && pendingReorderGraceUpdates > 0) {
                pendingReorderGraceUpdates -= 1
                reorderPreviewQueueSignature = displaySongsSignature
                isProcessed = true
            } else {
                pendingReorderExpectedIds = null
                pendingReorderGraceUpdates = 0
                reorderPreviewOrder = null
                reorderPreviewKeys = null
                reorderPreviewBaseQueue = null
            }
        }

        if (!isProcessed) {
            if (reorderPreviewQueueSignature != null) {
                // Queue data changed from external source - safe to clear preview
                reorderPreviewOrder = null
                reorderPreviewKeys = null
                reorderPreviewBaseQueue = null
            }
            remapCommittedKeysForDisplay(displaySongs)
            reorderPreviewQueueSignature = displaySongsSignature
        }
    }

    // Rows that arrive while the sheet is open (a mix filling up, "add to queue", …) slide
    // in one after another; opening the sheet never replays the whole queue.
    val entranceTracker = remember { QueueEntranceTracker() }
    remember(committedDisplayKeys, isVisible, displaySongCount) {
        entranceTracker.update(
            keys = committedDisplayKeys,
            animate = isVisible && committedDisplayKeys.size == displaySongCount
        )
    }
    val isMixWorking by viewModel.isMixWorking.collectAsStateWithLifecycle()
    // Mix building / songs just added → Gemini-style glow rising from the bottom edge.
    val isQueueBusy by viewModel.isQueueBusy.collectAsStateWithLifecycle()
    val queueGlowState = rememberQueueGlowState(active = isMixWorking || isQueueBusy)
    val mixStatusText by viewModel.mixStatus.collectAsStateWithLifecycle()

    // Portrait: the playing song is fixed above the list, so a new song just brings up next
    // back to its top.
    LaunchedEffect(currentSongId) {
        if (!isLandscape && !isReordering && !reorderHandleInUse && listState.firstVisibleItemIndex > 0) {
            runCatching { listState.animateScrollToItem(0) }
        }
    }

    val canDragSheetFromList by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val updatedCanDragSheet by rememberUpdatedState(canDragSheetFromList)
    var draggingSheetFromList by remember { mutableStateOf(false) }
    var listDragAccumulated by remember { mutableStateOf(0f) }
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    val updatedOnQueueDragStart by rememberUpdatedState(onQueueDragStart)
    val updatedOnQueueDrag by rememberUpdatedState(onQueueDrag)
    val updatedOnQueueRelease by rememberUpdatedState(onQueueRelease)

    val isAnyItemDragging = reorderableState.isAnyItemDragging
    var wasDragging by remember { mutableStateOf(false) }

    if (wasDragging && !isAnyItemDragging) {
        wasDragging = false
        val fromIndex = lastMovedFrom
        val toIndex = lastMovedTo

        lastMovedFrom = null
        lastMovedTo = null

        if (fromIndex != null && toIndex != null) {
            // Convert display indices to queue indices by adding the offset
            val fromQueueIndex = fromIndex + queueIndexOffset
            val toQueueIndex = toIndex + queueIndexOffset

            val fromWithinQueue = fromQueueIndex in queue.indices
            val toWithinQueue = toQueueIndex in queue.indices

            if (fromWithinQueue && toWithinQueue && fromQueueIndex != toQueueIndex) {
                val previewBase = reorderPreviewBaseQueue ?: queue
                val expectedIds = reorderPreviewOrder
                    ?.mapNotNull { previewBase.getOrNull(it)?.id }
                    ?.takeIf { it.size == displaySongCount }
                pendingReorderExpectedIds = expectedIds
                pendingReorderGraceUpdates = if (expectedIds != null) 6 else 0
                // Keep reorderPreviewOrder alive so items don't snap back
                // while we wait for the new queue data to propagate.
                onReorder(fromQueueIndex, toQueueIndex)
            } else {
                reorderPreviewOrder = null
                reorderPreviewKeys = null
                reorderPreviewBaseQueue = null
                pendingReorderExpectedIds = null
                pendingReorderGraceUpdates = 0
            }
        } else {
            reorderPreviewOrder = null
            reorderPreviewKeys = null
            reorderPreviewBaseQueue = null
            pendingReorderExpectedIds = null
            pendingReorderGraceUpdates = 0
        }
    } else if (isAnyItemDragging) {
        wasDragging = true
    }

    val activeKeys = reorderPreviewKeys
        ?: committedDisplayKeys.takeIf { it.size == displaySongCount }
    val activeSongSource = reorderPreviewBaseQueue ?: queue
    // Playlists queued as a whole: a turnover card sits above the first song of each block.
    val queueGroupStarts by remember(viewModel) {
        viewModel.playerUiState.map { it.queueGroupStarts }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = kotlinx.collections.immutable.persistentListOf())
    val groupStartByQueueIndex = remember(queueGroupStarts, queue) {
        queueGroupStarts.filter { it.queueIndex in queue.indices }.associateBy { it.queueIndex }
    }
    fun activeQueueIndexAt(index: Int): Int =
        reorderPreviewOrder?.getOrNull(index) ?: (queueIndexOffset + index)

    fun activeKeyAt(index: Int): Long =
        activeKeys?.getOrNull(index) ?: (queueIndexOffset + index).toLong()

    val useLightweightQueueListShape by remember {
        derivedStateOf {
            listState.isScrollInProgress ||
                draggingSheetFromList ||
                isReordering ||
                reorderHandleInUse
        }
    }
    val queueListShape = remember(useLightweightQueueListShape) {
        if (useLightweightQueueListShape) {
            RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        } else {
            AbsoluteSmoothCornerShape(
                cornerRadiusTR = 26.dp,
                smoothnessAsPercentTR = 60,
                cornerRadiusTL = 26.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusBR = 0.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = 0.dp,
                smoothnessAsPercentBL = 60
            )
        }
    }

    fun finalizeListDrag(velocity: Float = 0f) {
        if (draggingSheetFromList) {
            updatedOnQueueRelease(listDragAccumulated, velocity)
            draggingSheetFromList = false
            listDragAccumulated = 0f
        }
    }

    val listDragConnection = remember(updatedCanDragSheet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (updatedIsReordering || updatedReorderHandleInUse) return Offset.Zero

                if (draggingSheetFromList) {
                    // While dragging the sheet from the list, keep consuming vertical
                    // movement in BOTH directions so an upward drag can pull the sheet
                    // back up and cancel the gesture (like a normal bottom sheet).
                    // Only once the sheet is fully expanded again do we release control
                    // back to the list so it can scroll its contents.
                    if (available.y < 0f && queueSheetOffset.value <= 0.5f) {
                        finalizeListDrag()
                        return Offset.Zero
                    }
                    listDragAccumulated += available.y
                    updatedOnQueueDrag(available.y)
                    return available
                }

                if (available.y > 0 && updatedCanDragSheet) {
                    if (!draggingSheetFromList) {
                        draggingSheetFromList = true
                        listDragAccumulated = 0f
                        updatedOnQueueDragStart()
                    }
                    listDragAccumulated += available.y
                    updatedOnQueueDrag(available.y)
                    return Offset(0f, available.y)
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (updatedIsReordering || updatedReorderHandleInUse) return Velocity.Zero

                if (draggingSheetFromList && available.y < 0f) {
                    finalizeListDrag(available.y)
                    return Velocity.Zero
                }

                if (available.y > 0 && updatedCanDragSheet) {
                    if (!draggingSheetFromList) {
                        draggingSheetFromList = true
                        listDragAccumulated = 0f
                        updatedOnQueueDragStart()
                    }
                    updatedOnQueueRelease(listDragAccumulated, available.y)
                    draggingSheetFromList = false
                    listDragAccumulated = 0f
                    return available
                }
                return Velocity.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (updatedIsReordering || updatedReorderHandleInUse) return Offset.Zero

                if (draggingSheetFromList && source == NestedScrollSource.UserInput && available.y != 0f) {
                    listDragAccumulated += available.y
                    updatedOnQueueDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (updatedIsReordering || updatedReorderHandleInUse) return Velocity.Zero

                if (draggingSheetFromList) return available.also { finalizeListDrag(available.y) }
                return Velocity.Zero
            }
        }
    }

    val directSheetDragModifier =
        if (updatedIsReordering || updatedReorderHandleInUse) {
            Modifier
        } else {
            Modifier.pointerInput(updatedOnQueueDragStart, updatedOnQueueDrag, updatedOnQueueRelease) {
                var dragTotal = 0f
                val dragVelocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        dragTotal = 0f
                        dragVelocityTracker.resetTracking()
                        updatedOnQueueDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                        dragVelocityTracker.addPosition(change.uptimeMillis, change.position)
                        updatedOnQueueDrag(dragAmount)
                    },
                    onDragEnd = {
                        val velocity = dragVelocityTracker.calculateVelocity().y
                        updatedOnQueueRelease(dragTotal, velocity)
                    },
                    onDragCancel = {
                        val velocity = dragVelocityTracker.calculateVelocity().y
                        updatedOnQueueRelease(dragTotal, velocity)
                    }
                )
            }
        }

    Surface(
        modifier = modifier
            .graphicsLayer {
                val p = predictiveBackProgress.value
                val offsetVal = queueSheetOffset.value
                val y = offsetVal.roundToInt()
                
                if (p > 0f) {
                    val scale = 1f - (p * 0.1f)
                    scaleX = scale
                    scaleY = scale
                    translationY = p * 80.dp.toPx()
                    
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        pivotFractionX = 0.5f,
                        pivotFractionY = 1.0f
                    )
                    
                    val cornerRadius = androidx.compose.ui.unit.lerp(28.dp, 48.dp, p)
                    clip = true
                    this.shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                } else if (y < 0) {
                    val h = size.height
                    if (h > 0f) {
                        scaleY = (h - y) / h
                        scaleX = 1f
                        translationY = 0f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            pivotFractionX = 0.5f,
                            pivotFractionY = 1.0f
                        )
                    }
                } else {
                    scaleX = 1f
                    scaleY = 1f
                    translationY = 0f
                }
            },
        shape = shape,
        tonalElevation = tonalElevation,
        color = colors.surfaceContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val headerTopPadding = WindowInsets.statusBars
                .asPaddingValues()
                .calculateTopPadding() + 10.dp

            // Header ("Next up", tracks lined up, Listen, source) and the live mix strip.
            val headerBlock: @Composable () -> Unit = {
                QueueHeaderSection(
                    isPlaying = isPlaying,
                    queueSourceName = currentQueueSourceName,
                    queueSourceLink = queueSourceLink,
                    onOpenQueueSource = onOpenQueueSource,
                    queueCount = displaySongCount,
                    topPadding = headerTopPadding,
                    ambientSuggestionsEnabled = ambientSuggestionsEnabled,
                    onToggleAmbientSuggestions = { setListenEnabled(!ambientSuggestionsEnabled) },
                    onPrevious = { viewModel.previousSong() },
                    onPlayPause = { viewModel.playPause() },
                    onNext = { viewModel.nextSong() },
                    onLocateCurrentSong = {
                        if (isLandscape) {
                            queueCoroutineScope.launch { listState.animateScrollToItem(0) }
                        } else {
                            // Portrait: the playing song is always on screen; close History /
                            // Heard and bring up next back to its top.
                            isHistoryExpanded = false
                            isHeardExpanded = false
                            queueCoroutineScope.launch { runCatching { listState.animateScrollToItem(0) } }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(directSheetDragModifier)
                )

                // Live "the mix is adding songs" strip.
                AnimatedVisibility(
                    visible = isMixWorking,
                    enter = androidx.compose.animation.expandVertically() + fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + fadeOut()
                ) {
                    QueueMixWorkingStrip(
                        label = mixStatusText?.takeIf { it.isNotBlank() } ?: "Mix",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    )
                }

            }

            // The scrolling queue list (portrait: everything; landscape: what the left half
            // doesn't show).
            val queueListBlock: @Composable (Modifier) -> Unit = { listModifier ->
                if (displaySongCount == 0 && heardSongs.isEmpty()) {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.queue_empty_label),
                            color = colors.onSurface
                        )
                    }
                } else {
                    val listEdgeColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    val glowColors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.inversePrimary
                    )
                    val listBottomEdge = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp + 70.dp + 28.dp
                    Box(
                        modifier = listModifier
                            .clip(queueListShape)
                            // Rows blur and fade under the header (top) and the floating
                            // toolbar (bottom) instead of being cut off.
                            .queueEdgeBlur(
                                top = 28.dp,
                                bottom = listBottomEdge,
                                fadeColor = listEdgeColor
                            )
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape = queueListShape)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = queueListShape
                                )
                                // Behind the rows, above the list background.
                                .queueAssistantGlow(
                                    state = queueGlowState,
                                    colors = glowColors,
                                    cornerRadius = 36.dp
                                )
                                .then(
                                    if (isReordering || reorderHandleInUse) {
                                        Modifier
                                    } else {
                                        Modifier.nestedScroll(listDragConnection)
                                    }
                                ),
                            userScrollEnabled = !(isReordering || reorderHandleInUse),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                start = 0.dp,
                                end = if (LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward)) 26.dp else 0.dp,
                                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
                            )
                        ) {
                            item("queue_top_spacer") {
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Heard (opened from the bar's Heard half): same rows as History, newest
                            // right above the bar, plus ✓ (queue) and Next. Tap plays now, swipe
                            // dismisses, hold opens the song's options.
                            if (isLandscape && heardSongs.isNotEmpty() && isHeardExpanded) {
                                items(
                                    items = heardSongs.asReversed(),
                                    key = { item -> "heard_${item.id}" },
                                    contentType = { "heard_song" }
                                ) { item ->
                                    QueuePlaylistSongItem(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(),
                                        onClick = { viewModel.playHeardSongNow(item) },
                                        song = item.song,
                                        isCurrentSong = false,
                                        isPlaying = false,
                                        isDragging = false,
                                        onRemoveClick = { viewModel.dismissHeardSong(item.id) },
                                        isReorderModeEnabled = false,
                                        isDragHandleVisible = false,
                                        isRemoveButtonVisible = false,
                                        enableSwipeToDismiss = true,
                                        swipeStateIdentity = "heard_${item.id}".hashCode().toLong(),
                                        onDismissSong = { viewModel.dismissHeardSong(item.id) },
                                        isFromPlaylist = true,
                                        onMoreOptionsClick = { onSongInfoClick(item.song) },
                                        mixReason = if (item.mentionCount > 1) "🔥 ${item.mentionCount}x heard" else null,
                                        dragHandle = {},
                                        trailingContent = {
                                            HeardRowActions(
                                                onApprove = { viewModel.approveHeardSong(item) },
                                                onPlayNext = { viewModel.playNextHeardSong(item) }
                                            )
                                        }
                                    )
                                }
                            }

                            if (isLandscape && historySongs.isNotEmpty() && isHistoryExpanded) {
                                items(
                                    items = historySongs,
                                    key = { song -> "play_history_${song.id}" },
                                    contentType = { "history_song" }
                                ) { song ->
                                    // Played songs: same row, actions and options menu as a queued
                                    // song, a little dimmer. Tap plays it now (inserted after the
                                    // current song); swipe removes it from the history.
                                    QueuePlaylistSongItem(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem()
                                            .graphicsLayer { alpha = 0.78f },
                                        onClick = { viewModel.playFromHistory(song) },
                                        song = song,
                                        isCurrentSong = false,
                                        isPlaying = false,
                                        isDragging = false,
                                        onRemoveClick = { viewModel.removeFromPlayHistory(song.id) },
                                        isReorderModeEnabled = false,
                                        isDragHandleVisible = false,
                                        isRemoveButtonVisible = false,
                                        enableSwipeToDismiss = true,
                                        swipeStateIdentity = "play_history_${song.id}".hashCode().toLong(),
                                        onDismissSong = { viewModel.removeFromPlayHistory(song.id) },
                                        isFromPlaylist = true,
                                        onMoreOptionsClick = { onSongInfoClick(song) },
                                        dragHandle = {}
                                    )
                                }
                            }

                            // The History | Heard bar is never in this list: it sits in the fixed
                            // playing block (portrait) or the fixed left half (landscape).
                            // Landscape: the songs already shown in the left half are skipped.
                            items(
                                count = (displaySongCount - listStartIndex).coerceAtLeast(0),
                                key = { rowIndex -> activeKeyAt(rowIndex + listStartIndex) },
                                contentType = { "queue_song" }
                            ) { rowIndex ->
                                val index = rowIndex + listStartIndex
                                val queueIndex = activeQueueIndexAt(index)
                                if (queueIndex !in activeSongSource.indices) return@items
                                val itemStableKey = activeKeyAt(index)
                                val song = activeSongSource[queueIndex]
                                val canReorder = index > currentSongDisplayIndex
                                // Entrance for a freshly added row: slides in from the side,
                                // fades and settles, staggered with the rest of its batch, and
                                // shows an accent marker that fades out.
                                val entranceDelay = remember(itemStableKey) { entranceTracker.claim(itemStableKey) }
                                val entrance = remember(itemStableKey) { Animatable(if (entranceDelay != null) 0f else 1f) }
                                val newMarker = remember(itemStableKey) { Animatable(0f) }
                                if (entranceDelay != null) {
                                    LaunchedEffect(itemStableKey) {
                                        delay(entranceDelay.toLong())
                                        launch {
                                            newMarker.snapTo(1f)
                                            delay(900)
                                            newMarker.animateTo(0f, tween(durationMillis = 700))
                                        }
                                        entrance.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f)
                                        )
                                    }
                                }
                                val markerColor = MaterialTheme.colorScheme.primary
                                ReorderableItem(
                                    state = reorderableState,
                                    key = itemStableKey,
                                    enabled = canReorder,
                                    animateItemModifier = when {
                                        isReordering || reorderHandleInUse || reorderPreviewOrder != null -> Modifier.animateItem(
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        else -> Modifier.animateItem(
                                            fadeInSpec = tween(durationMillis = 140),
                                            fadeOutSpec = tween(durationMillis = 120),
                                            placementSpec = tween(durationMillis = 180)
                                        )
                                    }
                                ) { isDragging ->
                                    val scale by animateFloatAsState(
                                        targetValue = if (isDragging) 1.015f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "scaleAnimation"
                                    )

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // A queued playlist starts here: its turnover card, then its songs.
                                        val groupStart = if (reorderPreviewOrder == null && !isReordering && index > currentSongDisplayIndex) {
                                            groupStartByQueueIndex[queueIndex]
                                        } else null
                                        if (groupStart != null) {
                                            QueuePlaylistTurnoverCard(
                                                group = groupStart,
                                                onRemove = { viewModel.removeQueuedPlaylist(groupStart.groupId) }
                                            )
                                        }
                                        QueuePlaylistSongItem(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 0.dp)
                                                .graphicsLayer {
                                                    val e = entrance.value
                                                    val s = scale * (0.94f + 0.06f * e)
                                                    scaleX = s
                                                    scaleY = s
                                                    alpha = e.coerceIn(0f, 1f)
                                                    translationX = (1f - e) * 56.dp.toPx()
                                                }
                                                .drawWithContent {
                                                    drawContent()
                                                    val m = newMarker.value
                                                    if (m > 0.01f) {
                                                        val barH = size.height * 0.5f
                                                        drawRoundRect(
                                                            color = markerColor.copy(alpha = m),
                                                            topLeft = androidx.compose.ui.geometry.Offset(2.dp.toPx(), (size.height - barH) / 2f),
                                                            size = androidx.compose.ui.geometry.Size(4.dp.toPx(), barH),
                                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                                        )
                                                    }
                                                },
                                            onClick = { onPlaySong(song, queueIndex) },
                                            song = song,
                                            mixReason = mixInsights[song.id]?.reason?.takeIf { index > currentSongDisplayIndex },
                                            isDiscovery = mixInsights[song.id]?.discovery == true &&
                                                !song.isFavorite && song.id !in likedSongIds,
                                            isCurrentSong = index == currentSongDisplayIndex,
                                            isPlaying = isPlaying && isVisible,
                                            isDragging = isDragging,
                                            onRemoveClick = { onRemoveSong(song.id) },
                                            isReorderModeEnabled = false,
                                            isDragHandleVisible = canReorder,
                                            isRemoveButtonVisible = false,
                                            enableSwipeToDismiss = canReorder,
                                            swipeStateIdentity = itemStableKey,
                                            onDismissSong = { onRemoveSong(song.id) },
                                            // Up next: swipe right = move to Play next, further = Play soon.
                                            // (Already right after the playing song: Next has nothing to do.)
                                            onSwipeNext = if (canReorder) ({
                                                if (index != currentSongDisplayIndex + 1) viewModel.addSongNextToQueue(song)
                                            }) else null,
                                            onSwipeSoon = if (canReorder) ({ queueSoonFor = song.id }) else null,
                                            onCardHeightChanged = if (index == currentSongDisplayIndex) ({ h -> currentCardHeightPx = h }) else null,
                                            onArtistClick = if (index == currentSongDisplayIndex) ({ openArtistsOf(song) }) else null,
                                            isFromPlaylist = true,
                                            onMoreOptionsClick = { onSongInfoClick(song) },
                                            dragHandle = {
                                                IconButton(
                                                    onClick = {},
                                                    modifier = Modifier
                                                        .draggableHandle(
                                                            onDragStarted = {
                                                                draggingSheetFromList = false
                                                                reorderHandleInUse = true
                                                                performAppCompatHapticFeedback(
                                                                    view,
                                                                    appHapticsConfig,
                                                                    HapticFeedbackConstantsCompat.GESTURE_START
                                                                )
                                                            },
                                                            onDragStopped = {
                                                                reorderHandleInUse = false
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
                                                        contentDescription = stringResource(R.string.queue_cd_reorder_song),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        )
                                        if (canReorder) {
                                            InlineSoonPanel(
                                                visible = queueSoonFor == song.id,
                                                song = { song },
                                                playerViewModel = viewModel,
                                                onClose = { queueSoonFor = null },
                                                onConfirm = { k ->
                                                    queueSoonFor = null
                                                    // Moves this queued copy after the k-th upcoming song.
                                                    viewModel.addSongAfterUpcoming(song, k)
                                                }
                                            )
                                        }
                                        // Mix buttons hang under the playing card in a strip joined to it,
                                        // spanning only where the card's sides are straight (between the
                                        // curves), so the two read as one piece and move together.
                                        if (index == currentSongDisplayIndex) {
                                            val density = LocalDensity.current
                                            val cardHalf = with(density) { (currentCardHeightPx / 2).toDp() }
                                            val curve = if (currentCardHeightPx > 0) minOf(60.dp, cardHalf) else 40.dp
                                            QueueMixChipsRow(
                                                viewModel = viewModel,
                                                horizontalInset = 12.dp + curve,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
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
                                    top = 24.dp,
                                    end = 14.dp,
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 14.dp
                                )
                        )
                    }
                }
            }

            // A queue row without reordering, for the fixed left half in landscape.
            val staticQueueRow: @Composable (Int, Modifier) -> Unit = { index, rowModifier ->
                val queueIndex = activeQueueIndexAt(index)
                val song = activeSongSource.getOrNull(queueIndex)
                if (song != null) Column(Modifier.fillMaxWidth()) {
                    QueuePlaylistSongItem(
                        modifier = rowModifier.fillMaxWidth(),
                        onClick = { onPlaySong(song, queueIndex) },
                        song = song,
                        mixReason = mixInsights[song.id]?.reason?.takeIf { index > currentSongDisplayIndex },
                        isDiscovery = mixInsights[song.id]?.discovery == true &&
                            !song.isFavorite && song.id !in likedSongIds,
                        isCurrentSong = index == currentSongDisplayIndex,
                        isPlaying = isPlaying && isVisible,
                        isDragging = false,
                        onRemoveClick = { onRemoveSong(song.id) },
                        isReorderModeEnabled = false,
                        isDragHandleVisible = false,
                        isRemoveButtonVisible = false,
                        enableSwipeToDismiss = index > currentSongDisplayIndex,
                        swipeStateIdentity = activeKeyAt(index),
                        onDismissSong = { onRemoveSong(song.id) },
                        onSwipeNext = if (index > currentSongDisplayIndex) ({
                            if (index != currentSongDisplayIndex + 1) viewModel.addSongNextToQueue(song)
                        }) else null,
                        onSwipeSoon = if (index > currentSongDisplayIndex) ({ queueSoonFor = song.id }) else null,
                        onArtistClick = if (index == currentSongDisplayIndex) ({ openArtistsOf(song) }) else null,
                        onCardHeightChanged = if (index == currentSongDisplayIndex) ({ h -> currentCardHeightPx = h }) else null,
                        isFromPlaylist = true,
                        onMoreOptionsClick = { onSongInfoClick(song) },
                        dragHandle = {}
                    )
                    if (index > currentSongDisplayIndex) {
                        InlineSoonPanel(
                            visible = queueSoonFor == song.id,
                            song = { song },
                            playerViewModel = viewModel,
                            onClose = { queueSoonFor = null },
                            onConfirm = { k ->
                                queueSoonFor = null
                                viewModel.addSongAfterUpcoming(song, k)
                            }
                        )
                    }
                }
            }

            if (!isLandscape) {
                // Portrait: the History | Heard bar, the playing song and its mix chips are one
                // fixed block. Closed, it sits on top and up next scrolls under it. With History
                // or Heard open it slides to the bottom and the past songs scroll above it
                // (newest right above the bar), so the playing song never leaves the screen.
                val pastOpen = isHistoryExpanded || isHeardExpanded
                val reducedQueueMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
                val pastFraction = animateFloatAsState(
                    targetValue = if (pastOpen) 1f else 0f,
                    animationSpec = if (reducedQueueMotion) androidx.compose.animation.core.snap() else spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "queuePastFraction"
                )
                // Which past list is shown; kept while it slides away after closing.
                var shownPast by remember { mutableStateOf<String?>(null) }
                if (isHistoryExpanded && shownPast != "history") shownPast = "history"
                if (isHeardExpanded && shownPast != "heard") shownPast = "heard"
                val pastListState = rememberLazyListState()
                LaunchedEffect(pastOpen, isHistoryExpanded) {
                    if (pastOpen) {
                        // Reverse layout: item 0 (the newest) sits right above the bar.
                        runCatching { pastListState.scrollToItem(0) }
                    } else {
                        // Closed: up next back to its top as the block returns.
                        runCatching { listState.scrollToItem(0) }
                        androidx.compose.runtime.snapshotFlow { pastFraction.value }.first { it <= 0.001f }
                        shownPast = null
                    }
                }
                val pastRowsColor = MaterialTheme.colorScheme.surfaceContainerHigh
                Column {
                    headerBlock()
                    androidx.compose.ui.layout.Layout(
                        contents = listOf<@Composable () -> Unit>(
                            {
                                val which = shownPast
                                if (which != null) {
                                    LazyColumn(
                                        state = pastListState,
                                        reverseLayout = true,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(queueListShape)
                                            .background(pastRowsColor, queueListShape),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp)
                                    ) {
                                        if (which == "heard") {
                                            items(
                                                items = heardSongs,
                                                key = { item -> "heard_${item.id}" },
                                                contentType = { "heard_song" }
                                            ) { item ->
                                                QueuePlaylistSongItem(
                                                    modifier = Modifier.fillMaxWidth().animateItem(),
                                                    onClick = { viewModel.playHeardSongNow(item) },
                                                    song = item.song,
                                                    isCurrentSong = false,
                                                    isPlaying = false,
                                                    isDragging = false,
                                                    onRemoveClick = { viewModel.dismissHeardSong(item.id) },
                                                    isReorderModeEnabled = false,
                                                    isDragHandleVisible = false,
                                                    isRemoveButtonVisible = false,
                                                    enableSwipeToDismiss = true,
                                                    swipeStateIdentity = "heard_${item.id}".hashCode().toLong(),
                                                    onDismissSong = { viewModel.dismissHeardSong(item.id) },
                                                    isFromPlaylist = true,
                                                    onMoreOptionsClick = { onSongInfoClick(item.song) },
                                                    mixReason = if (item.mentionCount > 1) "\uD83D\uDD25 ${item.mentionCount}x heard" else null,
                                                    dragHandle = {},
                                                    trailingContent = {
                                                        HeardRowActions(
                                                            onApprove = { viewModel.approveHeardSong(item) },
                                                            onPlayNext = { viewModel.playNextHeardSong(item) }
                                                        )
                                                    }
                                                )
                                            }
                                        } else {
                                            items(
                                                items = historySongs.asReversed(),
                                                key = { song -> "play_history_${song.id}" },
                                                contentType = { "history_song" }
                                            ) { song ->
                                                QueuePlaylistSongItem(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .animateItem()
                                                        .graphicsLayer { alpha = 0.78f },
                                                    onClick = { viewModel.playFromHistory(song) },
                                                    song = song,
                                                    isCurrentSong = false,
                                                    isPlaying = false,
                                                    isDragging = false,
                                                    onRemoveClick = { viewModel.removeFromPlayHistory(song.id) },
                                                    isReorderModeEnabled = false,
                                                    isDragHandleVisible = false,
                                                    isRemoveButtonVisible = false,
                                                    enableSwipeToDismiss = true,
                                                    swipeStateIdentity = "play_history_${song.id}".hashCode().toLong(),
                                                    onDismissSong = { viewModel.removeFromPlayHistory(song.id) },
                                                    isFromPlaylist = true,
                                                    onMoreOptionsClick = { onSongInfoClick(song) },
                                                    dragHandle = {}
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            {
                                // The fixed block: bar, playing song, mix chips (in that order, top
                                // to bottom, wherever the block sits).
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (showHistoryHeardBar) {
                                        QueueHistoryHeardBar(
                                            historyCount = historySongs.size,
                                            heardCount = heardSongs.size,
                                            historyOpen = isHistoryExpanded,
                                            heardOpen = isHeardExpanded,
                                            onToggleHistory = { toggleHistory(!isHistoryExpanded) },
                                            onToggleHeard = { toggleHeard(!isHeardExpanded) },
                                            onClearHistory = {
                                                viewModel.clearPlayHistory()
                                                isHistoryExpanded = false
                                            },
                                            onClearHeard = {
                                                viewModel.clearAllHeardSongs()
                                                isHeardExpanded = false
                                            }
                                        )
                                    }
                                    if (currentSongDisplayIndex in 0 until displaySongCount) {
                                        key(activeKeyAt(currentSongDisplayIndex)) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                staticQueueRow(currentSongDisplayIndex, Modifier)
                                                // Its own row under the card (not a strip joined to it),
                                                // so the chips never run into the card's curve.
                                                QueueMixChipsRow(
                                                    viewModel = viewModel,
                                                    horizontalInset = 8.dp,
                                                    containerColor = Color.Transparent,
                                                    attached = false
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            { queueListBlock(Modifier.fillMaxSize()) }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                    ) { measurables, constraints ->
                        val (pastMeasurables, fixedMeasurables, upMeasurables) = measurables
                        val width = constraints.maxWidth
                        val height = constraints.maxHeight
                        val fixed = fixedMeasurables.firstOrNull()
                            ?.measure(androidx.compose.ui.unit.Constraints(maxWidth = width, maxHeight = height))
                        val fixedHeight = fixed?.height ?: 0
                        val rest = (height - fixedHeight).coerceAtLeast(0)
                        // Read in the layout phase only: the slide re-lays out, it doesn't recompose.
                        val p = pastFraction.value.coerceIn(0f, 1f)
                        val pastHeight = (rest * p).roundToInt()
                        val upHeight = rest - pastHeight
                        val past = pastMeasurables.firstOrNull()?.takeIf { pastHeight > 0 }
                            ?.measure(androidx.compose.ui.unit.Constraints.fixed(width, pastHeight))
                        val up = upMeasurables.firstOrNull()?.takeIf { upHeight > 0 }
                            ?.measure(androidx.compose.ui.unit.Constraints.fixed(width, upHeight))
                        layout(width, height) {
                            past?.place(0, 0)
                            fixed?.place(0, pastHeight)
                            up?.place(0, pastHeight + fixedHeight)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left half, fixed: header, the playing song + mix chips, History, then the
                    // next songs until the half is full.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        headerBlock()
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(
                                    start = 8.dp,
                                    end = 4.dp,
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                                )
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(top = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (currentSongDisplayIndex in 0 until displaySongCount) {
                                key(activeKeyAt(currentSongDisplayIndex)) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        staticQueueRow(currentSongDisplayIndex, Modifier)
                                        val density = LocalDensity.current
                                        val cardHalf = with(density) { (currentCardHeightPx / 2).toDp() }
                                        QueueMixChipsRow(
                                            viewModel = viewModel,
                                            horizontalInset = 12.dp + (if (currentCardHeightPx > 0) minOf(60.dp, cardHalf) else 40.dp),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                }
                            }
                            if (showHistoryHeardBar) {
                                QueueHistoryHeardBar(
                                    historyCount = historySongs.size,
                                    heardCount = heardSongs.size,
                                    historyOpen = isHistoryExpanded,
                                    heardOpen = isHeardExpanded,
                                    onToggleHistory = { toggleHistory(!isHistoryExpanded) },
                                    onToggleHeard = { toggleHeard(!isHeardExpanded) },
                                    onClearHistory = {
                                        viewModel.clearPlayHistory()
                                        isHistoryExpanded = false
                                    },
                                    onClearHeard = {
                                        viewModel.clearAllHeardSongs()
                                        isHeardExpanded = false
                                    }
                                )
                            }
                            // As many upcoming songs as fit; the rest continue on the right.
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clipToBounds()
                                    .onSizeChanged { leftSlotsAreaPx = it.height }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (index in leftSlotStart until leftSlotEnd) {
                                        key(activeKeyAt(index)) {
                                            staticQueueRow(
                                                index,
                                                if (index == leftSlotStart) {
                                                    Modifier.onSizeChanged { leftSlotRowPx = it.height }
                                                } else Modifier
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Right half: the rest of the queue over the full height, scrolling.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = headerTopPadding, start = 4.dp)
                    ) {
                        queueListBlock(Modifier.fillMaxSize())
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val fabSpacing = 16.dp
                val menuSpacing = 20.dp
                val fabRotation by animateFloatAsState(
                    targetValue = if (isFabExpanded) 45f else 0f,
                    label = "fabRotation"
                )

                val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                var toolbarWidthPx by remember { mutableIntStateOf(0) }
                val toolbarWidthDp = with(LocalDensity.current) { toolbarWidthPx.toDp() }

                Row(
                    modifier = Modifier
                        // Landscape: fixed at the bottom of the right half, centred in it.
                        .align(if (isLandscape) Alignment.BottomEnd else Alignment.BottomCenter)
                        .then(
                            if (isLandscape) {
                                Modifier.offset(x = -(LocalConfiguration.current.screenWidthDp.dp / 4) + (toolbarWidthDp / 2))
                            } else Modifier
                        )
                        .padding(bottom = fabSpacing + navigationBarHeight)
                        .height(70.dp)
                        .onSizeChanged { toolbarWidthPx = it.width }
                        .then(directSheetDragModifier),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isTimerActiveDerived = remember {
                        derivedStateOf { activeTimerValueDisplay.value != null }
                    }
                    val activeMixFlavor by viewModel.activeMixFlavor.collectAsStateWithLifecycle()
                    QueueControlsToolbar(
                        isShuffleOn = isShuffleOn,
                        activeMixFlavor = activeMixFlavor,
                        repeatMode = repeatMode,
                        isTimerActive = isTimerActiveDerived,
                        // Same mix button as the full player: tap cycles No mix → Normal Mix →
                        // Smart Mix, hold goes straight to Smart Mix.
                        onMixCycle = { viewModel.cycleMixMode() },
                        onSmartMix = { viewModel.activateSmartMix() },
                        onToggleRepeat = onToggleRepeat,
                        onTimerClick = { showTimerOptions = true }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    MediumFloatingActionButton(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f),
                        onClick = { isFabExpanded = !isFabExpanded },
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTR = 50.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusTL = 8.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBR = 50.dp,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusBL = 8.dp,
                            smoothnessAsPercentBL = 60
                        ),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.queue_cd_more_action),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(20f)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isFabExpanded = false
                            }
                    )
                }

                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    )
                                )
                            )
                            .clickable {
                                isFabExpanded = !isFabExpanded
                            }
                            .zIndex(30f),
                        // Landscape: the menu opens in the right half, above its toolbar.
                        contentAlignment = if (isLandscape) Alignment.BottomEnd else Alignment.BottomCenter
                    ) {
                        // Options: a row of actions (Locate, Clear, Save as playlist) above a
                        // full-width prompt row with a back button. The prompt row takes the
                        // toolbar's place at the bottom, or sits right above the keyboard.
                        val optionsDensity = LocalDensity.current
                        val imeBottom = with(optionsDensity) {
                            WindowInsets.ime.getBottom(optionsDensity).toDp()
                        }
                        val optionsBottom = if (imeBottom > navigationBarHeight) {
                            imeBottom + 12.dp
                        } else {
                            navigationBarHeight + fabSpacing + 7.dp
                        }
                        val activeMixPrompt by viewModel.mixPrompt.collectAsStateWithLifecycle()
                        val activeSteers by viewModel.mixSteers.collectAsStateWithLifecycle()
                        // Options open: get on-device Gemini Nano ready for the prompt box.
                        LaunchedEffect(Unit) { viewModel.warmUpMixPromptAi() }
                        val roomFriends by friendsViewModel.friends.collectAsStateWithLifecycle()
                        val roomIds by friendsViewModel.inRoomIds.collectAsStateWithLifecycle()
                        val inRoomFriends = remember(roomFriends, roomIds) { roomFriends.filter { it.id in roomIds } }
                        QueueOptionsBar(
                            inRoomFriends = inRoomFriends,
                            onFriendsClick = {
                                isFabExpanded = false
                                showFriendsInRoomSheet = true
                            },
                            canLocate = currentSongDisplayIndex >= 0 && currentSongDisplayIndex < displaySongCount,
                            activePrompt = activeMixPrompt,
                            onLocate = {
                                isFabExpanded = false
                                // The playing song is pinned (left half in landscape, the fixed
                                // block in portrait): close History / Heard, up next to its top.
                                isHistoryExpanded = false
                                isHeardExpanded = false
                                queueCoroutineScope.launch { listState.animateScrollToItem(0) }
                            },
                            onClearQueue = {
                                isFabExpanded = false
                                showClearQueueDialog = true
                            },
                            onSubmitPrompt = { text ->
                                viewModel.steerMixWithPrompt(text)
                                isFabExpanded = false
                            },
                            onClearPrompt = { viewModel.clearMixPrompt() },
                            steers = activeSteers,
                            onRemoveSteer = { id -> viewModel.removeMixSteer(id) },
                            onClose = { isFabExpanded = false },
                            onSaveAsPlaylist = {
                                isFabExpanded = false
                                val defaultName = if (currentQueueSourceName.isNotBlank()) {
                                    queueNamedSuffixTemplate.format(currentQueueSourceName)
                                } else {
                                    queueCurrentLabel
                                }
                                onRequestSaveAsPlaylist(
                                    queue,
                                    defaultName
                                ) { name, selectedIds ->
                                    val orderedSelection = queue
                                        .filter { selectedIds.contains(it.id) }
                                        .map { it.id }
                                    if (orderedSelection.isNotEmpty()) {
                                        playlistViewModel.createPlaylist(
                                            name = name,
                                            songIds = orderedSelection,
                                            isQueueGenerated = true
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth(if (isLandscape) 0.5f else 1f)
                                .padding(start = 16.dp, end = 16.dp, bottom = optionsBottom)
                        )
                    }
                }
            }

            val queueUndoBarState by remember(viewModel) {
                viewModel.playerUiState
                    .map { it.toQueueUndoBarProjection() }
                    .distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = QueueUndoBarProjection())
            AnimatedVisibility(
                visible = queueUndoBarState.isVisible,
                modifier = Modifier
                    // Landscape: centred in the right half, where the list and toolbar are.
                    .then(
                        if (isLandscape) {
                            Modifier
                                .align(Alignment.BottomEnd)
                                .fillMaxWidth(0.5f)
                                .wrapContentWidth(Alignment.CenterHorizontally)
                        } else {
                            Modifier.align(Alignment.BottomCenter)
                        }
                    )
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
                    )
                    .zIndex(50f),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.inverseSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = queueUndoBarState.removedSongTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inverseOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.queue_song_removed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inverseOnSurface.copy(alpha = 0.7f),
                        )
                        TextButton(
                            onClick = { viewModel.undoRemoveSongFromQueue() }
                        ) {
                            Text(
                                text = stringResource(R.string.common_undo),
                                color = colors.inversePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (showTimerOptions) {
            TimerOptionsBottomSheet(
                onPlayCounter = onPlayCounter,
                activeTimerValueDisplay = activeTimerValueDisplay.value,
                activeTimerDurationMinutes = activeTimerDurationMinutes.value,
                playCount = playCount.value,
                isEndOfTrackTimerActive = isEndOfTrackTimerActive.value,
                onDismiss = { showTimerOptions = false },
                onSetPredefinedTimer = onSetPredefinedTimer,
                onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                onOpenCustomTimePicker = onOpenCustomTimePicker,
                onCancelCountedPlay = onCancelCountedPlay,
                onCancelTimer = onCancelTimer
            )
        }

        if (showFriendsInRoomSheet) {
            val sheetFriends by friendsViewModel.friends.collectAsStateWithLifecycle()
            val sheetSelected by friendsViewModel.inRoomIds.collectAsStateWithLifecycle()
            val sheetMode by friendsViewModel.inRoomMode.collectAsStateWithLifecycle()
            val sheetShare by friendsViewModel.inRoomShare.collectAsStateWithLifecycle()
            FriendsInRoomSheet(
                friends = sheetFriends,
                selected = sheetSelected,
                onSave = { ids -> friendsViewModel.setInRoom(ids) },
                onDismiss = { showFriendsInRoomSheet = false },
                mode = sheetMode,
                onModeChange = friendsViewModel::setInRoomMode,
                share = sheetShare,
                onShareChange = friendsViewModel::setInRoomShare
            )
        }

        if (showClearQueueDialog) {
            AlertDialog(
                onDismissRequest = { showClearQueueDialog = false },
                title = { Text(stringResource(R.string.queue_dialog_clear_queue_title)) },
                text = { Text(stringResource(R.string.queue_dialog_clear_queue_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearQueue()
                            showClearQueueDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.common_clear), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearQueueDialog = false }
                    ) {
                        Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        }
    }
}

/**
 * The queue's options menu, two rows:
 *  - actions: Locate, Clear queue and Save as playlist, sharing the width;
 *  - the mix prompt on its own full-width row at the bottom, with a back button on its left
 *    that closes the menu (swiping the menu down, or tapping outside it, still works too).
 */
@Composable
private fun QueueOptionsBar(
    canLocate: Boolean,
    activePrompt: String?,
    onLocate: () -> Unit,
    onClearQueue: () -> Unit,
    onSubmitPrompt: (String) -> Unit,
    onClearPrompt: () -> Unit,
    onClose: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    inRoomFriends: List<com.theveloper.pixelplay.presentation.viewmodel.FriendUi> = emptyList(),
    onFriendsClick: () -> Unit = {},
    steers: List<com.theveloper.pixelplay.data.AdaptiveMix.SteerChip> = emptyList(),
    onRemoveSteer: (String) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val closeDistancePx = with(density) { 56.dp.toPx() }
    Column(
        // Taps between the buttons are swallowed here instead of reaching the scrim (which
        // closes the menu); a downward swipe closes it.
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { change, amount ->
                        dragged += amount
                        if (dragged > 0f) change.consume()
                    },
                    onDragEnd = { if (dragged > closeDistancePx) onClose() },
                    onDragCancel = { dragged = 0f }
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = colors.surfaceContainer,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QueueOptionActionButton(
                    icon = Icons.Rounded.MyLocation,
                    label = "Locate",
                    contentDescription = stringResource(R.string.queue_action_locate_current_song),
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    enabled = canLocate,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLocate()
                    },
                    modifier = Modifier.weight(1f)
                )
                QueueOptionActionButton(
                    icon = Icons.Filled.ClearAll,
                    label = "Clear",
                    contentDescription = stringResource(R.string.queue_action_clear_queue),
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearQueue()
                    },
                    modifier = Modifier.weight(1f)
                )
                QueueOptionActionButton(
                    icon = Icons.Filled.LibraryAdd,
                    label = "Save",
                    contentDescription = stringResource(R.string.queue_action_save_as_playlist),
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSaveAsPlaylist()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Friends in the room: who's here in person (their playlists feed the mix).
        FriendsInRoomRow(
            friends = inRoomFriends,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onFriendsClick()
            }
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = colors.surfaceContainer,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // No back button: the prompt spans the full width. The menu still closes with the
                    // system back gesture, a swipe down, or a tap outside it.
                    QueueMixPromptField(
                        activePrompt = activePrompt,
                        onSubmit = onSubmitPrompt,
                        onClearPrompt = onClearPrompt,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Active steers (up to 3): each one's ✕ removes just that steer.
                if (steers.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        items(steers, key = { it.id }) { steer ->
                            androidx.compose.material3.FilterChip(
                                selected = true,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onRemoveSteer(steer.id)
                                },
                                label = { Text(steer.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove ${steer.label}",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** An action in the options menu's top row: icon over a short label, sharing the row's width. */
@Composable
private fun QueueOptionActionButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(60.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun QueueOptionIconButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/**
 * Type anything in plain English (a song, an artist, a tempo, mood, genre, era or country, or
 * several at once: "upbeat 80s rock like Queen") to steer the running mix, or start Smart Mix
 * from the playing song. While a prompt is steering the mix it shows as the hint, with an ✕ to stop.
 */
@Composable
private fun QueueMixPromptField(
    activePrompt: String?,
    onSubmit: (String) -> Unit,
    onClearPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    fun submit() {
        val query = text.trim()
        if (query.isEmpty()) return
        focusManager.clearFocus()
        onSubmit(query)
        text = ""
    }
    Surface(
        modifier = modifier.height(52.dp),
        shape = CircleShape,
        color = colors.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = colors.tertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = activePrompt?.let { "Steering: $it" } ?: "Tempo, mood, genre, era, artist…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (activePrompt != null) colors.tertiary else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { submit() },
                        onDone = { submit() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when {
                text.isNotBlank() -> IconButton(
                    onClick = { submit() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Steer the mix",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                activePrompt != null -> IconButton(
                    onClick = onClearPrompt,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Stop steering the mix",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                else -> Spacer(Modifier.width(10.dp))
            }
        }
    }
}

/** "<Mix> · adding songs" with an indeterminate bar, shown while the mix fills the queue. */
@Composable
private fun QueueMixWorkingStrip(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.primaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label · adding songs",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onPrimaryContainer,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = colors.primary,
            trackColor = colors.primary.copy(alpha = 0.18f)
        )
    }
}

@Composable
private fun QueueHeaderSection(
    isPlaying: Boolean,
    queueSourceName: String,
    queueSourceLink: QueueSourceLink? = null,
    onOpenQueueSource: (QueueSourceLink) -> Unit = {},
    queueCount: Int,
    topPadding: Dp,
    ambientSuggestionsEnabled: Boolean = false,
    onToggleAmbientSuggestions: () -> Unit = {},
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    colorScheme: ColorScheme? = null,
    onLocateCurrentSong: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp, bottom = 14.dp)
                .width(42.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.onSurface.copy(alpha = 0.14f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            QueueHeader(
                queueSourceName = queueSourceName,
                queueSourceLink = queueSourceLink,
                onOpenQueueSource = onOpenQueueSource,
                queueCount = queueCount,
                ambientSuggestionsEnabled = ambientSuggestionsEnabled,
                onToggleAmbientSuggestions = onToggleAmbientSuggestions,
                modifier = Modifier.fillMaxWidth(),
                onLocateCurrentSong = onLocateCurrentSong
            )
        }
    }
}

@Composable
private fun QueueHeader(
    queueSourceName: String,
    queueSourceLink: QueueSourceLink? = null,
    onOpenQueueSource: (QueueSourceLink) -> Unit = {},
    queueCount: Int,
    ambientSuggestionsEnabled: Boolean = false,
    onToggleAmbientSuggestions: () -> Unit = {},
    modifier: Modifier = Modifier,
    onLocateCurrentSong: () -> Unit = {}
) {
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    // One row: "Next up" · track count (centred between) · source.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                performAppCompatHapticFeedback(
                    view,
                    appHapticsConfig,
                    HapticFeedbackConstantsCompat.GESTURE_START
                )
                onLocateCurrentSong()
            },
            text = stringResource(R.string.queue_next_up_label),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (queueCount > 0) {
                // "[♪] 24", like every other song count.
                CompactCountBadge(
                    count = queueCount,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Listen is hidden for now (unreliable); the code stays for when it's fixed.
            if (SHOW_QUEUE_LISTEN_BUTTON) FilledTonalIconButton(
                onClick = onToggleAmbientSuggestions,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (ambientSuggestionsEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (ambientSuggestionsEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hearing,
                    contentDescription = if (ambientSuggestionsEnabled) "Stop listening for song mentions" else "Listen for song mentions",
                    modifier = Modifier.size(20.dp)
                )
            }
            QueueSourceBadge(
                queueSourceName = queueSourceName,
                onClick = queueSourceLink?.let { link -> { onOpenQueueSource(link) } }
            )
        }
    }
}

/** The Listen (song mentions) button in the queue header. Off until Listen is reliable. */
private const val SHOW_QUEUE_LISTEN_BUTTON = false

@Composable
private fun QueueSourceBadge(
    queueSourceName: String,
    modifier: Modifier = Modifier,
    /** Opens the page the queue came from; null = plain label. */
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val title = queueSourceName.ifBlank { stringResource(R.string.queue_source_fallback_label) }
    val openLabel = stringResource(R.string.queue_source_open_cd, title)
    Surface(
        modifier = modifier
            .widthIn(max = 190.dp)
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            onClickLabel = openLabel,
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClick = onClick
                        )
                        .semantics { contentDescription = openLabel }
                } else Modifier
            ),
        shape = CircleShape,
        color = colors.surfaceContainerHighest.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = if (onClick != null) 6.dp else 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
            // Slides to the new title when the queue turns over to a queued playlist (or mix).
            AnimatedContent(
                modifier = Modifier.weight(1f, fill = false),
                targetState = title,
                transitionSpec = {
                    ((slideInVertically(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) { it / 2 } +
                        fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate))) togetherWith
                        (slideOutVertically(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) { -it / 2 } +
                            fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))))
                        .using(SizeTransform(clip = true) { _, _ -> tween(MotionTokens.DurationMedium1, easing = MotionTokens.Emphasized) })
                },
                label = "queueSourceTitle"
            ) { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Above the first song of a playlist queued as a whole: "Then: <playlist>" with its cover and song
 * count. Long-press offers removing the whole playlist from the queue.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuePlaylistTurnoverCard(
    group: com.theveloper.pixelplay.presentation.viewmodel.QueueGroupStart,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = "Remove playlist from queue"
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "Then ${group.name}, ${group.songCount} songs"
                },
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = group.coverUri,
                    contentDescription = null,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Then: ${group.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (group.songCount == 1) "1 song" else "${group.songCount} songs",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Remove playlist from queue") },
                onClick = {
                    menuOpen = false
                    onRemove()
                }
            )
        }
    }
}

@Composable
private fun QueueControlsToolbar(
    isShuffleOn: Boolean,
    activeMixFlavor: com.theveloper.pixelplay.data.MixFlavor?,
    repeatMode: Int,
    isTimerActive: androidx.compose.runtime.State<Boolean>,
    onMixCycle: () -> Unit,
    onSmartMix: () -> Unit,
    onToggleRepeat: () -> Unit,
    onTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
    val inactiveColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTR = 8.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTL = 50.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusBR = 8.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = 50.dp,
            smoothnessAsPercentBL = 60
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            QueueMixModeButton(
                activeMixFlavor = activeMixFlavor,
                isShuffleOn = isShuffleOn,
                onClick = onMixCycle,
                onLongClick = onSmartMix
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalIconButton(
                onClick = onToggleRepeat,
                colors = if (repeatMode != Player.REPEAT_MODE_OFF) activeColors else inactiveColors,
                modifier = Modifier.size(48.dp)
            ) {
                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                }
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = stringResource(R.string.queue_cd_toggle_repeat_action),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalIconButton(
                onClick = onTimerClick,
                colors = if (isTimerActive.value) activeColors else inactiveColors,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = stringResource(R.string.queue_cd_sleep_timer_action),
                )
            }
        }
    }
}

/**
 * The mix button in the queue toolbar, matching the full player's: shuffle icon for No mix /
 * Normal Mix (filled while on), sparkle on the tertiary colour for Smart Mix. Tap cycles the
 * modes; hold starts Smart Mix.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueMixModeButton(
    activeMixFlavor: com.theveloper.pixelplay.data.MixFlavor?,
    isShuffleOn: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val spin = rememberShuffleSpin()
    val isSmart = activeMixFlavor == com.theveloper.pixelplay.data.MixFlavor.SMART
    val isOn = activeMixFlavor != null || isShuffleOn
    val container by animateColorAsState(
        targetValue = when {
            isSmart -> colors.tertiary
            isOn -> colors.primary
            else -> colors.surfaceContainer
        },
        label = "queueMixButtonContainer"
    )
    val content by animateColorAsState(
        targetValue = when {
            isSmart -> colors.onTertiary
            isOn -> colors.onPrimary
            else -> colors.onSurfaceVariant
        },
        label = "queueMixButtonContent"
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(container)
            .combinedClickable(
                onClick = { spin.play(); onClick() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    spin.play()
                    onLongClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.shuffleSpin(spin),
            imageVector = if (isSmart) Icons.Rounded.AutoAwesome else Icons.Rounded.Shuffle,
            contentDescription = when (activeMixFlavor) {
                com.theveloper.pixelplay.data.MixFlavor.SMART -> "Smart Mix (tap to turn off)"
                com.theveloper.pixelplay.data.MixFlavor.NORMAL -> "Normal Mix (tap for Smart Mix)"
                null -> "No mix (tap for Normal Mix, hold for Smart Mix)"
            },
            tint = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveQueueAsPlaylistSheet(
    songs: List<Song>,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Set<String>) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
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

    var playlistName by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaultName, selection = TextRange(defaultName.length)))
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedSongIds = remember(songs) {
        mutableStateMapOf<String, Boolean>().apply {
            songs.forEach { put(it.id, true) }
        }
    }

    val filteredSongs = remember(searchQuery, songs) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true)
        }
    }

    val hasSelection by remember {
        derivedStateOf { selectedSongIds.any { it.value } }
    }
    val allSelected by remember {
        derivedStateOf { selectedSongIds.isNotEmpty() && selectedSongIds.all { it.value } }
    }

    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
    }

    BackHandler(onBack = { onDismiss() })

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column {
                        MediumTopAppBar(
                            title = {
                                Text(
                                    modifier = Modifier.padding(start = 4.dp),
                                    text = stringResource(R.string.queue_save_as_playlist_sheet_title),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                FilledTonalIconButton(
                                    modifier = Modifier.padding(start = 8.dp),
                                    onClick = { onDismiss() },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close))
                                }
                            },
                            actions = {
                                val animatedContainerColor by animateColorAsState(
                                    targetValue = if (allSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    label = "selectBtnContainer"
                                )
                                val animatedContentColor by animateColorAsState(
                                    targetValue = if (allSelected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                                    label = "selectBtnContent"
                                )
                                val animatedCornerPercent by animateIntAsState(
                                    targetValue = if (allSelected) 50 else 15,
                                    label = "selectBtnShape"
                                )

                                Surface(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .height(40.dp)
                                        .clickable {
                                            if (allSelected) {
                                                selectedSongIds.keys.forEach {
                                                    selectedSongIds[it] = false
                                                }
                                            } else {
                                                selectedSongIds.keys.forEach {
                                                    selectedSongIds[it] = true
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(animatedCornerPercent),
                                    color = animatedContainerColor,
                                    contentColor = animatedContentColor
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (allSelected) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (allSelected) {
                                                stringResource(R.string.queue_save_as_playlist_deselect_all)
                                            } else {
                                                stringResource(R.string.common_select_all)
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = playlistName,
                                onValueChange = { playlistName = it },
                                label = { Text(stringResource(R.string.queue_save_as_playlist_name_label)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = Color.Transparent,
                                )
                            )

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.queue_save_as_playlist_search_placeholder)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                Icons.Filled.Clear,
                                                contentDescription = stringResource(R.string.common_clear_search)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = CircleShape,
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                )
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                },
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.ime)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 6.dp,
                            shadowElevation = 4.dp,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.queue_save_as_playlist_n_songs_selected,
                                            selectedSongIds.count { it.value },
                                            selectedSongIds.count { it.value }
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = if (playlistName.text.isNotBlank()) {
                                            stringResource(
                                                R.string.queue_save_as_playlist_format,
                                                playlistName.text
                                            )
                                        } else {
                                            stringResource(R.string.queue_save_as_playlist_name_placeholder)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                            alpha = 0.8f
                                        )
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (hasSelection) {
                                            val finalName =
                                                playlistName.text.ifBlank { defaultName }
                                            val chosenIds = selectedSongIds
                                                .filterValues { it }
                                                .keys
                                            onConfirm(finalName, chosenIds)
                                            onDismiss()
                                        }
                                    },
                                    enabled = hasSelection,
                                    modifier = Modifier.height(48.dp),
                                    shape = CircleShape,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 20.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.common_save))
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding()
                        )
                        .consumeWindowInsets(innerPadding)
                        .imePadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredSongs.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.queue_save_as_playlist_search_no_match,
                                        searchQuery
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredSongs, key = { it.id }) { song ->
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
                                        targetSize = Size(168, 168),
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
                    }
                }
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueuePlaylistSongItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean? = null,
    isDragging: Boolean,
    onRemoveClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
    isReorderModeEnabled: Boolean,
    onMoreOptionsClick: (song: Song) -> Unit,
    isDragHandleVisible: Boolean,
    isRemoveButtonVisible: Boolean,
    enableSwipeToDismiss: Boolean = false,
    swipeStateIdentity: Long = 0L,
    onDismissSong: () -> Unit = {},
    isFromPlaylist: Boolean,
    /** Long-press handler. Null = hold opens [onMoreOptionsClick] (when [isFromPlaylist]). */
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    /** Why the mix picked this song ("Fits Workout", "New for you · Sounds like what's playing"). */
    mixReason: String? = null,
    /** A mix discovery the listener hasn't liked yet: shows a sparkle. */
    isDiscovery: Boolean = false,
    /** Show the friend's picture + name when this song was queued because of a friend. */
    showFriendAttribution: Boolean = true,
    /** Extra actions at the end of the row (Heard rows: add to queue / play next). */
    trailingContent: (@Composable () -> Unit)? = null,
    /** Swipe right = move to Play next (up-next rows only); null = no right swipe. */
    onSwipeNext: (() -> Unit)? = null,
    /** Swipe further right = move to Play soon. */
    onSwipeSoon: (() -> Unit)? = null,
    /** The card's measured height (px), so the playing card's chip strip can line up with its curve. */
    onCardHeightChanged: ((Int) -> Unit)? = null,
    /** Playing card only: tapping the artist line (2+ artists → picker). Null = not tappable. */
    onArtistClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val friendTags by com.theveloper.pixelplay.data.social.FriendQueueAttribution.entries.collectAsState()
    val friendTag = if (showFriendAttribution) friendTags[song.id] else null

    val cornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong) 60.dp else 22.dp,
        label = "cornerRadiusAnimation"
    )

    val itemShape = RoundedCornerShape(cornerRadius)

    val albumCornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong) 60.dp else 8.dp,
        label = "cornerRadiusAnimation"
    )

    val albumShape = RoundedCornerShape(albumCornerRadius)

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 4.dp else 1.dp,
        label = "elevationAnimation"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.secondaryContainer else colors.surfaceContainerLowest,
        label = "selectionBackground"
    )
    val mvContainerColor = if (isCurrentSong) colors.tertiaryContainer else colors.surfaceContainerHigh
    val mvContentColor = if (isCurrentSong) colors.onTertiaryContainer else colors.onSurface
    val hapticView = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    val dismissScope = rememberCoroutineScope()
    val dismissEnabled = enableSwipeToDismiss && !isDragging
    val density = LocalDensity.current

    val dismissOffsetAnimatable = remember(swipeStateIdentity) { Animatable(0f) }
    var itemWidthPx by remember { mutableStateOf(0f) }

    val latestSwipeNext by rememberUpdatedState(onSwipeNext)
    val latestSwipeSoon by rememberUpdatedState(onSwipeSoon)
    val rightEnabled = onSwipeNext != null
    val soonEnabled = onSwipeSoon != null
    val dismissHandler = remember(swipeStateIdentity, dismissEnabled, itemWidthPx, rightEnabled, soonEnabled) {
        if (dismissEnabled && itemWidthPx > 0f) {
            QueueItemDismissGestureHandler(
                scope = dismissScope,
                density = density,
                hapticView = hapticView,
                appHapticsConfig = appHapticsConfig,
                offsetAnimatable = dismissOffsetAnimatable,
                itemWidthPx = itemWidthPx,
                onDismiss = onDismissSong,
                rightEnabled = rightEnabled,
                soonEnabled = soonEnabled,
                onRightRelease = { stage ->
                    when (stage) {
                        SwipeStage.NEXT -> latestSwipeNext?.invoke()
                        SwipeStage.SOON -> latestSwipeSoon?.invoke()
                        else -> Unit
                    }
                }
            )
        } else null
    }

    val isSwipeTargeted = dismissHandler?.isInDismissZone == true
    val currentOffsetPx = dismissOffsetAnimatable.value
    val revealWidthPx = (-currentOffsetPx).coerceAtLeast(0f)
    val revealProgress = if (density.density > 0f) {
        (revealWidthPx / (56.dp.value * density.density)).coerceIn(0f, 1f)
    } else 0f

    val dismissBackgroundColor by animateColorAsState(
        targetValue = if (isSwipeTargeted) colors.errorContainer else colors.errorContainer.copy(alpha = 0.82f),
        animationSpec = tween(durationMillis = 150),
        label = "dismissBackgroundColor"
    )
    val dismissIconAlpha by animateFloatAsState(
        targetValue = revealProgress * if (isSwipeTargeted) 1f else 0.88f,
        animationSpec = tween(durationMillis = 120),
        label = "dismissIconAlpha"
    )
    val dismissIconScale by animateFloatAsState(
        targetValue = if (isSwipeTargeted) 1.08f else 0.95f,
        animationSpec = tween(durationMillis = 120),
        label = "dismissIconScale"
    )

    var surfaceHeightPx by remember { mutableStateOf(0f) }
    val moveNextLabel = stringResource(R.string.queue_action_move_next)
    val moveSoonLabel = stringResource(R.string.queue_action_move_soon)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val measuredWidth = coordinates.size.width.toFloat()
                if (measuredWidth != itemWidthPx) itemWidthPx = measuredWidth
            }
    ) {
        if (revealWidthPx > 0f && surfaceHeightPx > 0f) {
            val revealWidthDp = with(density) { revealWidthPx.toDp() }
            val surfaceHeightDp = with(density) { surfaceHeightPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .height(surfaceHeightDp)
                    .width(revealWidthDp)
                    .clip(CircleShape)
                    .background(dismissBackgroundColor),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_close_24),
                    contentDescription = stringResource(R.string.queue_cd_dismiss_song),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .graphicsLayer {
                            alpha = dismissIconAlpha
                            scaleX = dismissIconScale
                            scaleY = dismissIconScale
                        },
                    tint = colors.onErrorContainer
                )
            }
        }

        // Right swipe: "Next" → "Soon" behind the row on the left (same look as the search swipe row).
        if (currentOffsetPx > 0f && surfaceHeightPx > 0f && dismissHandler != null) {
            val stage = dismissHandler.rightStage
            val soon = stage == SwipeStage.SOON
            val revealContainer by animateColorAsState(
                if (soon) colors.tertiaryContainer else colors.primaryContainer,
                tween(MotionTokens.DurationShort3), label = "queueSwipeReveal"
            )
            val revealContent = if (soon) colors.onTertiaryContainer else colors.onPrimaryContainer
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .height(with(density) { surfaceHeightPx.toDp() })
                    .width(with(density) { currentOffsetPx.toDp() })
                    .clip(CircleShape)
                    .background(revealContainer),
                contentAlignment = Alignment.CenterStart
            ) {
                androidx.compose.animation.Crossfade(targetState = soon, label = "queueSwipeLabel") { s ->
                    Text(
                        stringResource(if (s) R.string.playlist_option_soon else R.string.playlist_option_next),
                        modifier = Modifier
                            .padding(start = 18.dp)
                            .graphicsLayer { alpha = if (stage == SwipeStage.NONE) 0.6f else 1f },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = revealContent,
                        maxLines = 1
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .graphicsLayer { translationX = currentOffsetPx }
                .onGloballyPositioned { coordinates ->
                    val h = coordinates.size.height.toFloat()
                    if (h != surfaceHeightPx) {
                        surfaceHeightPx = h
                        onCardHeightChanged?.invoke(coordinates.size.height)
                    }
                }
                .semantics {
                    // TalkBack: the right-swipe moves as actions.
                    if (onSwipeNext != null || onSwipeSoon != null) {
                        customActions = listOfNotNull(
                            onSwipeNext?.let { action -> CustomAccessibilityAction(moveNextLabel) { action(); true } },
                            onSwipeSoon?.let { action -> CustomAccessibilityAction(moveSoonLabel) { action(); true } }
                        )
                    }
                }
                .padding(horizontal = 12.dp)
                .clip(itemShape)
                .songStartFeedback(song.id, itemShape)
                // No ⋮ button: hold opens the song's options (or the caller's own long press,
                // e.g. toggling selection in a playlist's Edit mode).
                .combinedClickable(
                    enabled = currentOffsetPx == 0f,
                    onClick = onClick,
                    onLongClick = onLongClick ?: if (isFromPlaylist && !isSelectionMode) {
                        { onMoreOptionsClick(song) }
                    } else null,
                    onLongClickLabel = if (onLongClick == null && isFromPlaylist && !isSelectionMode) "Song options" else null
                ),
            shape = itemShape,
            color = backgroundColor,
            tonalElevation = elevation,
            shadowElevation = elevation
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The reorder handle is on the right (end) of the row; see below.
                val dismissGestureModifier = if (dismissEnabled && dismissHandler != null) {
                    Modifier.pointerInput(swipeStateIdentity, dismissHandler) {
                        detectHorizontalDragGestures(
                            onDragStart = { dismissHandler.onDragStart() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dismissHandler.onHorizontalDrag(dragAmount)
                            },
                            onDragEnd = { dismissHandler.onDragEnd() },
                            onDragCancel = { dismissHandler.onDragCancel() }
                        )
                    }
                } else Modifier

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(dismissGestureModifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(12.dp))

                    Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        SmartImage(
                            model = song.albumArtUriString,
                            shape = albumShape,
                            contentDescription = stringResource(R.string.common_album_art_for_title, song.title),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(albumShape),
                            contentScale = ContentScale.Crop,
                            targetSize = SmartImageCompactListTargetSize
                        )
                        // Multi-select: a tick over the artwork for selected rows.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isSelectionMode,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.7f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(albumShape)
                                    .background(if (isSelected) colors.primary.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = colors.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .size(20.dp)
                                            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (isCurrentSong) colors.primary else colors.onSurface,
                            fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // One badge rule app-wide: cloud, cloud-heart, heart (on device + liked) or nothing.
                            com.theveloper.pixelplay.presentation.components.subcomps.TrackStatusBadges(
                                isFavorite = song.isFavorite,
                                source = song.source,
                                downloadState = song.downloadState,
                                spacing = 0.dp
                            )
                            if (isDiscovery) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "New for you",
                                    tint = colors.tertiary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            if (friendTag != null) {
                                QueueFriendTag(friendTag, isCurrentSong)
                            }
                            Text(
                                song.displayArtist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrentSong) colors.primary.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                                modifier = if (onArtistClick != null && isCurrentSong) {
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(
                                            onClickLabel = stringResource(R.string.queue_open_artist),
                                            role = Role.Button,
                                            onClick = onArtistClick
                                        )
                                } else Modifier
                            )
                        }
                        if (!mixReason.isNullOrBlank() && !isCurrentSong) {
                            Text(
                                mixReason, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDiscovery) colors.tertiary else colors.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }

                    if (isCurrentSong) {
                        if (isPlaying != null) {
                            PlayingEqIcon(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(width = 18.dp, height = 16.dp),
                                color = colors.secondary,
                                isPlaying = isPlaying
                            )
                            Spacer(Modifier.width(4.dp))
                            if (!isRemoveButtonVisible) {
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    trailingContent?.invoke()

                    AnimatedVisibility(visible = isRemoveButtonVisible && !enableSwipeToDismiss) {
                        FilledIconButton(
                            onClick = onRemoveClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.surfaceContainer,
                                contentColor = colors.onSurface
                            ),
                            modifier = Modifier
                                .width(40.dp)
                                .height(40.dp)
                                .padding(start = 4.dp, end = 8.dp)
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                painter = painterResource(R.drawable.rounded_close_24),
                                contentDescription = stringResource(R.string.queue_cd_remove_from_playlist),
                            )
                        }
                    }
                }
                // Reorder handle on the right, where the thumb is.
                AnimatedVisibility(visible = isDragHandleVisible) {
                    dragHandle()
                }
            }
        }
    }
}

/**
 * A row of mix buttons under the playing song. "Similar" builds up next from songs close to
 * the playing one; the rest are the Your Music vibe filters (presets, then the user's own).
 * Tapping one rebuilds up next (keeping songs you queued yourself during a mix). A vibe
 * button also locks the mix to that vibe until it is tapped again or the mix stops.
 */
@Composable
private fun QueueMixChipsRow(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    /** From the list edge to where the playing card's curve starts (card padding + its radius). */
    horizontalInset: Dp = 52.dp,
    /** The playing card's colour, so the strip reads as part of it. */
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    /** True: a strip joined under the card (flat top). False: a free-standing row of chips. */
    attached: Boolean = true,
) {
    val selectedId by viewModel.queueMixFilterId.collectAsStateWithLifecycle()
    val busy by viewModel.queueMixBusy.collectAsStateWithLifecycle()
    val filters = remember(viewModel) { viewModel.queueMixFilters() }
    val similarId = com.theveloper.pixelplay.presentation.library.QueueVibeMix.SIMILAR_ID
    val reduced = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    var shown by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { shown = true }
    androidx.compose.animation.AnimatedVisibility(
        visible = shown,
        enter = if (reduced) androidx.compose.animation.EnterTransition.None else
            androidx.compose.animation.expandVertically(
                tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate),
                expandFrom = Alignment.Top
            ) + androidx.compose.animation.fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset)
    ) {
        // Flat top (it continues the card), rounded bottom; chips scroll inside its width only.
        Surface(
            color = containerColor,
            shape = if (attached) RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().clipToBounds(),
                // As little height as possible: compact 30 dp pills with 4 dp above and below.
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = similarId) {
                    QueueMixChip(
                        label = "Similar",
                        selected = selectedId == similarId,
                        loading = busy && selectedId == similarId,
                        icon = androidx.compose.material.icons.Icons.Rounded.AutoAwesome,
                        onClick = { viewModel.onQueueMixChip(similarId) }
                    )
                }
                items(filters.size, key = { "vibe_" + filters[it].id }) { i ->
                    val filter = filters[i]
                    val locked = selectedId == filter.id
                    QueueMixChip(
                        label = filter.label,
                        selected = locked,
                        loading = busy && locked,
                        // A selected vibe is locked for the rest of the mix; tap it again to unlock.
                        icon = if (locked) androidx.compose.material.icons.Icons.Rounded.Lock else null,
                        onClick = { viewModel.onQueueMixChip(filter.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueMixChip(
    label: String,
    selected: Boolean,
    loading: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit
) {
    // A compact pill (30 dp) instead of a FilterChip with its 48 dp touch box, so the strip under
    // the playing card stays as short as possible.
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized), label = "queueChipBg"
    )
    val content = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            loading -> androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = content
            )
            icon != null -> Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
        }
        Text(text = label, maxLines = 1, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/**
 * History | Heard, side by side above the playing song. Heard only appears while there are heard
 * songs. Tapping a half opens its list above the bar (tap it again to close; there's no ✕). While
 * one is open, the OTHER half becomes that list's Clear button, in the same space. With only one
 * half, it takes the full width; the halves ease in and out on a spring.
 */
@Composable
private fun QueueHistoryHeardBar(
    historyCount: Int,
    heardCount: Int,
    historyOpen: Boolean,
    heardOpen: Boolean,
    onToggleHistory: () -> Unit,
    onToggleHeard: () -> Unit,
    onClearHistory: () -> Unit,
    onClearHeard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Left half: History, or "Clear heard" while Heard is open. Right half: Heard, or
    // "Clear history" while History is open (shown even when there's no Heard half otherwise).
    // Both halves always show, side by side; an empty one just doesn't open.
    val leftVisible = true
    val rightVisible = true
    val halfSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val leftWeight by animateFloatAsState(if (leftVisible) 1f else 0f, halfSpec, label = "historyHalf")
    val rightWeight by animateFloatAsState(if (rightVisible) 1f else 0f, halfSpec, label = "heardHalf")
    if (leftWeight < 0.01f && rightWeight < 0.01f) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leftWeight >= 0.01f) {
            Box(
                Modifier
                    .weight(leftWeight)
                    .clipToBounds()
                    .graphicsLayer { alpha = leftWeight.coerceIn(0f, 1f) }
            ) {
                QueueBarSlot(
                    showClear = heardOpen,
                    clearLabel = "Clear heard",
                    onClear = onClearHeard
                ) {
                    QueueListToggle(
                        icon = Icons.Rounded.History,
                        label = "History",
                        count = historyCount,
                        open = historyOpen,
                        onClick = { if (historyCount > 0 || historyOpen) onToggleHistory() }
                    )
                }
            }
        }
        if (leftWeight >= 0.01f && rightWeight >= 0.01f) {
            Spacer(Modifier.width(8.dp * minOf(leftWeight, rightWeight).coerceIn(0f, 1f)))
        }
        if (rightWeight >= 0.01f) {
            Box(
                Modifier
                    .weight(rightWeight)
                    .clipToBounds()
                    .graphicsLayer { alpha = rightWeight.coerceIn(0f, 1f) }
            ) {
                QueueBarSlot(
                    showClear = historyOpen,
                    clearLabel = "Clear history",
                    onClear = onClearHistory
                ) {
                    QueueListToggle(
                        icon = Icons.Rounded.Hearing,
                        label = "Heard",
                        count = heardCount,
                        open = heardOpen,
                        onClick = { if (heardCount > 0 || heardOpen) onToggleHeard() }
                    )
                }
            }
        }
    }
}

/** One half of the History | Heard bar: its toggle, or (while the other list is open) a Clear button. */
@Composable
private fun QueueBarSlot(
    showClear: Boolean,
    clearLabel: String,
    onClear: () -> Unit,
    toggle: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = showClear,
        transitionSpec = {
            (fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.92f)) togetherWith
                fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
        },
        label = "queueBarSlot"
    ) { clear ->
        if (clear) {
            val colors = MaterialTheme.colorScheme
            FilledTonalButton(
                onClick = onClear,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(clearLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            toggle()
        }
    }
}

/** History / Heard toggle: icon, label, count and a chevron. Open = tonal selected look; tap again closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueListToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    open: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (open) colors.secondaryContainer else colors.surfaceContainerLowest,
        animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
        label = "queueListToggleContainer"
    )
    // Nothing in it: dimmed and no chevron (a tap does nothing).
    val empty = count == 0 && !open
    val content = (if (open) colors.onSecondaryContainer else colors.onSurface).let { if (empty) it.copy(alpha = 0.5f) else it }
    Surface(
        onClick = onClick,
        enabled = !empty,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .semantics { stateDescription = if (empty) "Empty" else if (open) "Open" else "Closed" },
        shape = RoundedCornerShape(26.dp),
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (open) content else if (empty) colors.primary.copy(alpha = 0.5f) else colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f, fill = false)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            if (!empty) {
                Icon(
                    imageVector = if (open) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** ✓ (add to queue) and Next on a Heard row. */
@Composable
private fun HeardRowActions(onApprove: () -> Unit, onPlayNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = onApprove,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            )
        ) {
            Icon(Icons.Rounded.Check, contentDescription = "Add to queue", modifier = Modifier.size(18.dp))
        }
        FilledTonalIconButton(
            onClick = onPlayNext,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            )
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Play next", modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * Lays the content out [top]/[bottom] shorter than it measures, shifted up by [top]. Used to drop
 * the chips' invisible touch-target margin so the mix row sits flush under the playing song.
 */
private fun Modifier.trimVertical(top: Dp, bottom: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val topPx = top.roundToPx()
    val bottomPx = bottom.roundToPx()
    val height = (placeable.height - topPx - bottomPx).coerceAtLeast(0)
    layout(placeable.width, height) { placeable.place(0, -topPx) }
}

/**
 * "Added because of a friend" chip for a queue row: the friend's picture, then their name when
 * the row has room (the name gives way to the artist first; the picture always stays).
 */
@Composable
private fun RowScope.QueueFriendTag(tag: com.theveloper.pixelplay.data.social.FriendAttribution, isCurrentSong: Boolean) {
    val colors = MaterialTheme.colorScheme
    val avatarSize = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
    Row(
        modifier = Modifier
            .weight(1f, fill = false)
            .semantics(mergeDescendants = true) { contentDescription = "Added from ${tag.friendName}'s listening" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tag.avatarUrl != null) {
            SmartImage(
                model = tag.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(avatarSize),
                shape = CircleShape
            )
        } else {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tag.friendName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            tag.friendName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isCurrentSong) colors.primary else colors.onSurfaceVariant
        )
        Text(
            " ·",
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
    }
}
