package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.components.subcomps.rememberShuffleSpin
import com.theveloper.pixelplay.presentation.components.subcomps.shuffleSpin
import com.theveloper.pixelplay.presentation.components.rememberAnimatedSheetDismiss
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
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.Edit
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
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
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import android.content.Intent
import android.net.Uri
import com.theveloper.pixelplay.data.accounts.MusicSources
import com.theveloper.pixelplay.presentation.components.PlaylistExportBottomSheet
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Slider
import androidx.compose.material3.FilledIconButton
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.stateDescription
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
        val pinnedIds by friendsViewModel.pinnedPlaylistKeys.collectAsStateWithLifecycle()
        (playlist.id in pinnedIds) to { pin: Boolean -> friendsViewModel.setPinned(playlist.id, pin) }
    }
    // YouTube Music playlists can be edited: changes are saved in the app and pushed to the account.
    val isEditableConnectedPlaylist = currentPlaylist?.source == "YOUTUBE_MUSIC"
    val isReadOnlyPlaylist = isFolderPlaylist || isGeneratedMix || isSystemPlaylist ||
        (isConnectedPlaylist && !isEditableConnectedPlaylist)
    val canDeletePlaylist = !isFolderPlaylist && !isGeneratedMix && !isSystemPlaylist
    val songsInPlaylist = uiState.currentPlaylistSongs

    val syncTargets by playlistViewModel.playlistSyncTargets.collectAsStateWithLifecycle()
    val currentSyncTarget = syncTargets[playlistId]
    val isSpotifyConnected by playlistViewModel.isSpotifyConnected.collectAsStateWithLifecycle()
    val isYouTubeMusicConnected by playlistViewModel.isYouTubeMusicConnected.collectAsStateWithLifecycle()
    var showExportSheet by remember { mutableStateOf(false) }
    var isExportingToService by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }
    // Transfer (TuneMyMusic): offer to save the playlist file first, then open the site.
    var showTransferPrompt by remember { mutableStateOf(false) }
    var openTuneMyMusicAfterSave by remember { mutableStateOf(false) }
    val openTuneMyMusic: () -> Unit = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tunemymusic.com/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Blend pages (a friend's blend or the custom blend): members on the cover, friends deck ----
    val blendPage = com.theveloper.pixelplay.presentation.components.parseBlendPageId(playlistId)
    val blendFriendsViewModel: com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel? =
        if (blendPage != null) hiltViewModel<com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel>() else null
    var showBlendPicker by remember { mutableStateOf(false) }
    // Friends changed from this page: the cover plays the building animation until the new blend
    // is ready, then the page is swapped for it. 0 = not rebuilding.
    var blendRebuildStartedAt by remember { mutableStateOf(0L) }
    val blendMembers: List<Pair<String, String?>>? = blendFriendsViewModel?.let { vm ->
        val blendFriends by vm.friends.collectAsStateWithLifecycle()
        val customIds by vm.customBlendFriendIds.collectAsStateWithLifecycle()
        when (blendPage) {
            is com.theveloper.pixelplay.presentation.components.BlendPageId.Friend ->
                blendFriends.filter { it.id == blendPage.friendId }.map { it.name to it.avatarUrl }
            com.theveloper.pixelplay.presentation.components.BlendPageId.Custom ->
                blendFriends.filter { it.id in customIds }.map { it.name to it.avatarUrl }
            // Friends Mix keeps the regular cover and tools; only the filter row is shared.
            com.theveloper.pixelplay.presentation.components.BlendPageId.FriendsMix, null -> null
        }
    }
    // A blend can be liked into Your playlists: it stays a daily mix (rebuilt every day).
    val blendPinned: Pair<Boolean, (Boolean) -> Unit>? = blendFriendsViewModel?.let { vm ->
        val key = when (blendPage) {
            is com.theveloper.pixelplay.presentation.components.BlendPageId.Friend ->
                com.theveloper.pixelplay.data.social.PinnedBlends.friendKey(blendPage.friendId)
            com.theveloper.pixelplay.presentation.components.BlendPageId.Custom -> com.theveloper.pixelplay.data.social.PinnedBlends.CUSTOM
            com.theveloper.pixelplay.presentation.components.BlendPageId.FriendsMix, null -> null
        } ?: return@let null
        val pinnedKeys by vm.pinnedBlendKeys.collectAsStateWithLifecycle()
        (key in pinnedKeys) to { pin: Boolean -> vm.setBlendPinned(key, pin) }
    }
    var blendSelectedPeople by rememberSaveable(
        playlistId,
        stateSaver = androidx.compose.runtime.saveable.listSaver(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf<Set<String>>(emptySet()) }
    var blendLikedOnly by rememberSaveable(playlistId) { mutableStateOf(false) }
    var blendVibe by rememberSaveable(playlistId) { mutableStateOf<String?>(null) }
    val blendFiltering = blendSelectedPeople.isNotEmpty() || blendLikedOnly || blendVibe != null
    // A vibe chip re-mixes the blend for that mood from everyone's songs that fit it (your
    // listening and library, each friend's history and playlists), instead of hiding the songs
    // of today's blend that don't fit. Until the mix is ready, today's blend narrowed to the mood shows.
    val blendMoodFriendId = (blendPage as? com.theveloper.pixelplay.presentation.components.BlendPageId.Friend)?.friendId
    // Friends Mix has no "You" side to re-mix, so its vibe chips only filter.
    val blendMoodEnabled = blendPage != com.theveloper.pixelplay.presentation.components.BlendPageId.FriendsMix
    LaunchedEffect(blendFriendsViewModel, blendMoodFriendId, blendVibe, blendMoodEnabled) {
        blendFriendsViewModel?.setBlendMood(blendMoodFriendId, blendVibe.takeIf { blendMoodEnabled })
    }
    val moodBlend: com.theveloper.pixelplay.presentation.viewmodel.MoodBlend? = blendFriendsViewModel?.let { vm ->
        val mood by vm.moodBlend.collectAsStateWithLifecycle()
        mood?.takeIf { blendMoodEnabled && !it.building && it.vibeId == blendVibe && it.friendId == blendMoodFriendId }
    }
    // The mood's name joins the queue name ("J · M · K · Chill") while a mood blend plays.
    val moodLabel = remember(moodBlend?.vibeId) {
        moodBlend?.vibeId?.let { id -> com.theveloper.pixelplay.presentation.library.MusicVibeFilters.presets.firstOrNull { it.id == id }?.label }
    }
    fun blendQueueName(base: String): String = moodLabel?.let { "$base · $it" } ?: base
    // Who added each song to the blend (song id -> "me" / friend id / "both"), for the filter chips.
    val dailyBlendContributors: Map<String, String> = blendFriendsViewModel?.let { vm ->
        when (blendPage) {
            is com.theveloper.pixelplay.presentation.components.BlendPageId.Friend -> {
                val friendBlends by vm.blends.collectAsStateWithLifecycle()
                friendBlends.find { it.friendId == blendPage.friendId }?.contributors.orEmpty()
            }
            com.theveloper.pixelplay.presentation.components.BlendPageId.Custom -> {
                val custom by vm.customBlend.collectAsStateWithLifecycle()
                custom?.contributors.orEmpty()
            }
            // Friends Mix: each song is "added" by the friend who played it most recently.
            com.theveloper.pixelplay.presentation.components.BlendPageId.FriendsMix -> {
                val mix by vm.friendsMix.collectAsStateWithLifecycle()
                remember(mix) { mix.associate { it.song.id to it.friend.id } }
            }
            null -> emptyMap()
        }
    } ?: emptyMap()
    val blendContributors: Map<String, String> = moodBlend?.contributors ?: dailyBlendContributors
    val blendFilterChips: List<Pair<String, String>> = blendFriendsViewModel?.let { vm ->
        val chipFriends by vm.friends.collectAsStateWithLifecycle()
        val youLabel = stringResource(R.string.blend_filter_you)
        remember(blendContributors, chipFriends, youLabel) {
            com.theveloper.pixelplay.presentation.components.blendFilterChips(blendContributors, chipFriends, youLabel)
        }
    }.orEmpty()
    // Per-song badge on blend pages: your picture, the friend's, or a sparkle for songs you share.
    val blendBadges: Map<String, com.theveloper.pixelplay.presentation.components.BlendSongSource> = blendFriendsViewModel?.let { vm ->
        val badgeFriends by vm.friends.collectAsStateWithLifecycle()
        val myAvatar by vm.myAvatarUrl.collectAsStateWithLifecycle()
        val custom by vm.customBlend.collectAsStateWithLifecycle()
        val youLabel = stringResource(R.string.blend_filter_you)
        val shared = moodBlend?.sharedSongIds
            ?: if (blendPage == com.theveloper.pixelplay.presentation.components.BlendPageId.Custom) custom?.sharedSongIds.orEmpty() else emptySet()
        remember(blendContributors, badgeFriends, myAvatar, shared, youLabel) {
            val byId = badgeFriends.associateBy { it.id }
            blendContributors.mapValues { (songId, who) ->
                when {
                    who == com.theveloper.pixelplay.data.social.BlendMixGenerator.BOTH || songId in shared ->
                        com.theveloper.pixelplay.presentation.components.BlendSongSource.Shared
                    who == com.theveloper.pixelplay.data.social.BlendMixGenerator.ME ->
                        com.theveloper.pixelplay.presentation.components.BlendSongSource.Person(youLabel, myAvatar)
                    else -> byId[who].let { f ->
                        com.theveloper.pixelplay.presentation.components.BlendSongSource.Person(f?.name ?: "Friend", f?.avatarUrl)
                    }
                }
            }
        }
    }.orEmpty()
    LaunchedEffect(blendFilterChips) {
        val validIds = blendFilterChips.map { it.first }.toSet()
        val pruned = blendSelectedPeople.filter { it in validIds }.toSet()
        if (pruned != blendSelectedPeople) {
            blendSelectedPeople = pruned
        }
    }
    fun openBlendPicker() {
        val vm = blendFriendsViewModel ?: return
        // From a friend's blend, that friend starts ticked (the custom blend grows from here).
        if (blendPage is com.theveloper.pixelplay.presentation.components.BlendPageId.Friend &&
            blendPage.friendId !in vm.customBlendFriendIds.value
        ) {
            vm.toggleCustomBlendFriend(blendPage.friendId)
        }
        showBlendPicker = true
    }
    if (blendFriendsViewModel != null) {
        val pickerFriends by blendFriendsViewModel.friends.collectAsStateWithLifecycle()
        val pickerIds by blendFriendsViewModel.customBlendFriendIds.collectAsStateWithLifecycle()
        val pickerCounts by blendFriendsViewModel.friendSongCounts.collectAsStateWithLifecycle()
        val rebuilt by blendFriendsViewModel.customBlend.collectAsStateWithLifecycle()
        if (showBlendPicker) {
            com.theveloper.pixelplay.presentation.components.CustomBlendSheet(
                friends = pickerFriends,
                selectedIds = pickerIds,
                songCounts = pickerCounts,
                onToggle = blendFriendsViewModel::toggleCustomBlendFriend,
                onSetAll = blendFriendsViewModel::setCustomBlendFriends,
                buildLabel = if (blendPage == com.theveloper.pixelplay.presentation.components.BlendPageId.Custom) "Rebuild blend" else "Build blend",
                onBuild = {
                    showBlendPicker = false
                    blendRebuildStartedAt = System.currentTimeMillis()
                },
                onDismiss = { showBlendPicker = false }
            )
        }
        LaunchedEffect(blendRebuildStartedAt) {
            val started = blendRebuildStartedAt
            if (started == 0L) return@LaunchedEffect
            kotlinx.coroutines.delay((1_400L - (System.currentTimeMillis() - started)).coerceAtLeast(0L))
            val built = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                androidx.compose.runtime.snapshotFlow { rebuilt }
                    .first { it != null && it.songs.isNotEmpty() && !it.isBuilding }
            }
            blendRebuildStartedAt = 0L
            if (built == null) {
                playerViewModel.sendToast("Your blend fills in once your friends have listened to a few songs.")
                return@LaunchedEffect
            }
            blendFriendsViewModel.prepareCustomBlend()
            val newId = com.theveloper.pixelplay.presentation.components.registerCustomBlendPlaylist(
                built, com.theveloper.pixelplay.presentation.components.customBlendTitle(built, pickerFriends)
            )
            if (newId == playlistId) {
                playlistViewModel.loadPlaylistDetails(playlistId)
            } else {
                kotlinx.coroutines.delay(MotionTokens.DurationMedium2.toLong())
                val replaceThisPage = blendPage == com.theveloper.pixelplay.presentation.components.BlendPageId.Custom
                val here = navController.currentBackStackEntry?.destination?.id
                navController.navigateSafely(Screen.PlaylistDetail.createRoute(newId)) {
                    if (replaceThisPage && here != null) popUpTo(here) { inclusive = true }
                }
            }
        }
    }

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
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deleteAlsoRemote by remember { mutableStateOf(false) }
    // The download menu (High / Medium / Low with per-quality counts).
    var showQualitySheet by remember { mutableStateOf(false) }

    // ---- Edit mode (the header's Edit button): select + reorder in one ----
    // Holding a song outside Edit opens its options. Inside Edit, tap/hold toggles a song and
    // the handles drag; dragging a selected song moves every selected song with it.
    // Selection is by row key (not song id) so duplicate YouTube Music entries select on their own.
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
        // Saved for a transfer: straight on to TuneMyMusic to upload it.
        if (openTuneMyMusicAfterSave) {
            openTuneMyMusicAfterSave = false
            if (uri != null) openTuneMyMusic()
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
    // Rows keep the key they were given when the playlist loaded, so a key never changes while
    // a song is being dragged (positional keys for duplicates would).
    var playlistRows by remember(songsInPlaylist) {
        mutableStateOf(uniqueSongKeys(songsInPlaylist).zip(songsInPlaylist) { key, song -> PlaylistRow(key, song) })
    }
    val localReorderableSongs = remember(playlistRows) { playlistRows.map { it.song } }
    // A ready mood blend replaces the list (its own row keys, so rows animate rather than clash).
    val moodRows: List<PlaylistRow>? = remember(moodBlend) {
        moodBlend?.songs?.let { songs -> uniqueSongKeys(songs).zip(songs) { key, song -> PlaylistRow("mood:$key", song) } }
    }
    // Blend pages: contributor chips show only those people's songs (and play only those).
    val displayRows = remember(playlistRows, moodRows, blendSelectedPeople, blendContributors, blendLikedOnly, blendVibe, favoriteIds) {
        val base = moodRows ?: playlistRows
        if (!blendFiltering) base
        else base.filter { row ->
            com.theveloper.pixelplay.presentation.components.blendSongMatchesAny(blendContributors[row.song.id], blendSelectedPeople) &&
                (!blendLikedOnly || row.song.id in favoriteIds) &&
                // Mood blend rows already fit the mood; today's blend is narrowed while it mixes.
                (moodRows != null || com.theveloper.pixelplay.presentation.components.blendSongMatchesVibe(row.song, blendVibe))
        }
    }
    val displaySongs = remember(displayRows) { displayRows.map { it.song } }
    val localSongKeys = remember(playlistRows) { playlistRows.map { it.key } }
    val selectedSongs = remember(selectedIds, playlistRows, moodRows) {
        (moodRows ?: playlistRows).filter { it.key in selectedIds }.map { it.song }.distinctBy { it.id }
    }

    val listState = rememberLazyListState()
    // The top bar floats over the list: a pinned blend filter row sits below it.
    val topBarHeightForStickyHeader = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val reducedMotion = rememberSystemReducedMotion()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    // Set while a drag is in progress: the dragged row's key, and whether it carries the other
    // selected rows with it (it does when it is selected and 2+ rows are).
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragCarriesSelection by remember { mutableStateOf(false) }
    var orderChanged by remember { mutableStateOf(false) }

    // Moves are resolved by row KEY, never by LazyColumn index: the list has a header (and could
    // gain other rows) above the songs, and an index offset was what made reordering move the
    // wrong song.
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromIndex = playlistRows.indexOfFirst { it.key == from.key }
            val toIndex = playlistRows.indexOfFirst { it.key == to.key }
            if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
            playlistRows = playlistRows.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            orderChanged = true
        }
    )

    fun commitOrder() {
        val playlist = currentPlaylist ?: return
        if (!isReadOnlyPlaylist) playlistViewModel.setPlaylistOrder(playlist.id, playlistRows.map { it.song })
    }

    // Drop: gather the carried selection around the dropped row (keeping their order), then save
    // the whole order once.
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (reorderableState.isAnyItemDragging) return@LaunchedEffect
        val anchor = draggingKey
        if (anchor != null && dragCarriesSelection) {
            playlistRows = gatherSelectedRows(playlistRows, selectedIds, anchor)
            orderChanged = true
        }
        if (orderChanged) commitOrder()
        draggingKey = null
        dragCarriesSelection = false
        orderChanged = false
    }

    fun moveRowBy(key: String, delta: Int) {
        val from = playlistRows.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in playlistRows.indices) return
        playlistRows = playlistRows.toMutableList().apply { add(to, removeAt(from)) }
        commitOrder()
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
            // Everything else opens the download menu: pick High / Medium / Low, see how many
            // songs are at each, and optionally change the ones already downloaded.
            else -> showQualitySheet = true
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
                // Blend pages refresh too: pulling re-checks friends' listening, which the blend is built from.
                val blendRefreshing = blendFriendsViewModel?.refreshing?.collectAsStateWithLifecycle()?.value == true
                MaybePullToRefresh(
                    enabled = isConnectedPlaylist || currentSyncTarget != null || blendFriendsViewModel != null,
                    isRefreshing = isRefreshing || blendRefreshing,
                    onRefresh = {
                        if (!isSelectionMode) {
                            if (blendFriendsViewModel != null) blendFriendsViewModel.refreshNow()
                            else if (isConnectedPlaylist) playlistViewModel.syncConnectedPlaylist(currentPlaylist.id)
                            else if (currentSyncTarget != null) {
                                playlistViewModel.syncPlaylistNow(currentPlaylist.id) { msg ->
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
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
                                syncTarget = currentSyncTarget,
                                ownerName = friendOwner?.name ?: currentPlaylist.ownerName.takeIf { currentPlaylist.friendId != null && it.isNotBlank() },
                                ownerAvatarUrl = friendOwner?.avatarUrl,
                                accent = animatedAccent,
                                surfaceColor = surfaceColor,
                                fallbackName = fallbackPlaylistName,
                                downloadState = downloadState,
                                onDownloadClick = onDownloadClick,
                                // The big button shuffles (Shuffle was removed from the options menu).
                                onShuffleClick = {
                                    val isFiltered = (blendPage != null && blendFiltering) || displaySongs.size != localReorderableSongs.size
                                    val toShuffle = if (isFiltered) displaySongs else localReorderableSongs
                                    if (toShuffle.isNotEmpty()) {
                                        if (moodBlend != null) blendFriendsViewModel?.prepareMoodBlend(toShuffle)
                                        playerViewModel.playSongsShuffled(
                                            songsToPlay = toShuffle,
                                            queueName = if (isFiltered) blendQueueName(currentPlaylist.name) else currentPlaylist.name,
                                            playlistId = currentPlaylist.id,
                                            startAtZero = true,
                                        )
                                    }
                                },
                                shuffleEnabled = if (blendFiltering) displaySongs.isNotEmpty() else localReorderableSongs.isNotEmpty(),
                                shuffleLabel = shuffleLabel,
                                onMoreClick = { showPlaylistOptionsSheet = true },
                                moreOptionsLabel = moreOptionsLabel,
                                canEditSongs = !isReadOnlyPlaylist,
                                canEdit = localReorderableSongs.isNotEmpty(),
                                addLabel = addLabel,
                                sortLabel = sortSongsLabel,
                                onAddClick = { showAddSongsSheet = true },
                                // Edit toggles: pressing it again (it reads "Done") leaves edit mode.
                                isEditing = isSelectionMode,
                                onEditClick = {
                                    if (isSelectionMode) {
                                        exitSelection()
                                    } else {
                                        selectedIds = emptySet()
                                        isSelectionMode = true
                                    }
                                },
                                onSortClick = { playerViewModel.showSortingSheet() },
                                blendMembers = blendMembers,
                                saves = friendOwner?.playlists?.firstOrNull { it.playlistId == currentPlaylist.id || (it.source + ":" + it.remoteId) == currentPlaylist.id }?.saves,
                                blendBuilding = blendRebuildStartedAt != 0L,
                                onBlendFriendsClick = { openBlendPicker() }
                            )
                        }

                        // Blend: All / You / each friend. Scrolls with the page, then sticks under the top bar.
                        if (blendPage != null && localReorderableSongs.isNotEmpty()) {
                            stickyHeader(key = "blend_filter", contentType = "blend_filter") {
                                val stuck by remember {
                                    derivedStateOf { listState.firstVisibleItemIndex >= 1 }
                                }
                                com.theveloper.pixelplay.presentation.components.BlendContributorFilterRow(
                                    chips = blendFilterChips,
                                    selectedPeople = blendSelectedPeople,
                                    onTogglePerson = { id ->
                                        blendSelectedPeople = if (id in blendSelectedPeople) blendSelectedPeople - id else blendSelectedPeople + id
                                    },
                                    stuck = stuck,
                                    topInset = if (stuck) topBarHeightForStickyHeader else 0.dp,
                                    likedOnly = blendLikedOnly,
                                    onToggleLiked = { blendLikedOnly = !blendLikedOnly },
                                    vibeId = blendVibe,
                                    onSelectVibe = { blendVibe = it },
                                )
                            }
                            if (blendFiltering && displayRows.isEmpty()) {
                                item(key = "blend_filter_empty", contentType = "empty") {
                                    val names = blendSelectedPeople.mapNotNull { id ->
                                        blendFilterChips.firstOrNull { it.first == id }?.second
                                    }
                                    val nameText = if (names.isNotEmpty()) names.joinToString(", ") else null
                                    com.theveloper.pixelplay.presentation.components.BlendFilterEmpty(
                                        // Only a person picked: "No songs contributed by X"; otherwise a general line.
                                        name = nameText.takeIf { !blendLikedOnly && blendVibe == null },
                                        onClear = {
                                            blendSelectedPeople = emptySet()
                                            blendLikedOnly = false
                                            blendVibe = null
                                        }
                                    )
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
                                    } else if (blendPage == com.theveloper.pixelplay.presentation.components.BlendPageId.FriendsMix) {
                                        stringResource(R.string.friends_mix_empty)
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
                            displayRows,
                            // YouTube Music playlists can contain the same video more than once,
                            // so the song id alone is not a unique LazyColumn key.
                            key = { _, row -> row.key },
                            contentType = { _, _ -> "playlist_song" }) { index, row ->
                            val song = row.song
                            val canDrag = isSelectionMode && !isReadOnlyPlaylist && playlistRows.size > 1
                            ReorderableItem(
                                state = reorderableState,
                                key = row.key,
                                enabled = canDrag
                            ) { isDragging ->
                                val isSelected = row.key in selectedIds
                                // While a selected song is dragged, the other selected songs dim:
                                // they join it where it is dropped.
                                val isCarried = dragCarriesSelection && isSelected && row.key != draggingKey
                                val liftSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.04f else if (isCarried) 0.97f else 1f,
                                    animationSpec = if (reducedMotion) snap<Float>() else liftSpec,
                                    label = "dragScale"
                                )
                                val carriedAlpha by animateFloatAsState(
                                    targetValue = if (isCarried) 0.45f else 1f,
                                    animationSpec = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.fastEffectsSpec<Float>(),
                                    label = "carriedAlpha"
                                )
                                fun toggle() {
                                    selectedIds = if (isSelected) selectedIds - row.key else selectedIds + row.key
                                }

                                QueuePlaylistSongItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = carriedAlpha
                                        }
                                        .then(
                                            if (canDrag) Modifier.semantics {
                                                customActions = listOfNotNull(
                                                    if (index > 0) CustomAccessibilityAction("Move up") { moveRowBy(row.key, -1); true } else null,
                                                    if (index < playlistRows.lastIndex) CustomAccessibilityAction("Move down") { moveRowBy(row.key, 1); true } else null,
                                                )
                                            } else Modifier
                                        ),
                                    onClick = {
                                        if (isSelectionMode) {
                                            toggle()
                                        } else {
                                            // Add Song from lyrics: opens the action sheet instead.
                                            songTap.handle(song, displaySongs, blendQueueName(currentPlaylist.name)) {
                                                if (moodBlend != null) blendFriendsViewModel?.prepareMoodBlend(displaySongs)
                                                playerViewModel.playSongs(
                                                    displaySongs,
                                                    song,
                                                    blendQueueName(currentPlaylist.name),
                                                    currentPlaylist.id
                                                )
                                            }
                                        }
                                    },
                                    // Outside Edit: null = hold opens the song's options.
                                    onLongClick = if (isSelectionMode) {
                                        {
                                            performAppCompatHapticFeedback(
                                                view,
                                                appHapticsConfig,
                                                HapticFeedbackConstantsCompat.LONG_PRESS
                                            )
                                            toggle()
                                        }
                                    } else null,
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
                                    trailingContent = blendBadges[song.id]?.let { source ->
                                        { com.theveloper.pixelplay.presentation.components.BlendSongBadge(source) }
                                    },
                                    isReorderModeEnabled = canDrag,
                                    isDragHandleVisible = canDrag,
                                    isRemoveButtonVisible = false,
                                    onMoreOptionsClick = stableOnMoreOptionsClick,
                                    dragHandle = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        draggingKey = row.key
                                                        dragCarriesSelection = isSelected && selectedIds.size > 1
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
                                                .size(48.dp)
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
                            Icon(Icons.Rounded.Check, contentDescription = "Done editing")
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        ) {
                            Text(
                                text = if (selectedIds.isEmpty()) "Select songs" else "${selectedIds.size} selected",
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                                maxLines = 1
                            )
                            if (!isReadOnlyPlaylist && playlistRows.size > 1) {
                                Text(
                                    text = if (selectedIds.size > 1) "Drag a selected song to move them all" else "Drag ⠿ to reorder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        val allIds = remember(playlistRows) { playlistRows.mapTo(hashSetOf()) { it.key } }
                        val allSelected = allIds.isNotEmpty() && selectedIds.containsAll(allIds)
                        TextButton(
                            onClick = {
                                selectedIds = if (allSelected) emptySet() else allIds
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
                        (friendPinned ?: blendPinned)?.let { (pinned, setPinned) ->
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
                    enter = if (reducedMotion) androidx.compose.animation.fadeIn(snap()) else
                        androidx.compose.animation.slideInVertically(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) { it } +
                            androidx.compose.animation.fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)),
                    exit = if (reducedMotion) androidx.compose.animation.fadeOut(snap()) else
                        androidx.compose.animation.slideOutVertically(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) { it } +
                            androidx.compose.animation.fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
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
                            playlistViewModel.removeSongsFromPlaylist(currentPlaylist.id, selectedSongs.mapTo(hashSetOf()) { it.id })
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
        // Actions slide the sheet down before it is removed (no abrupt vanish).
        val closeOptionsSheet = rememberAnimatedSheetDismiss(sheetState) { showPlaylistOptionsSheet = false }

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
                            closeOptionsSheet()
                            playlistViewModel.syncConnectedPlaylist(currentPlaylist.id)
                        }
                    )
                } else if (currentSyncTarget != null) {
                    val serviceLabel = if (currentSyncTarget.service == "SPOTIFY") "Spotify" else "YouTube Music"
                    PlaylistActionItem(
                        icon = rememberVectorPainter(Icons.Rounded.Sync),
                        label = "Sync now with $serviceLabel",
                        onClick = {
                            closeOptionsSheet()
                            playlistViewModel.syncPlaylistNow(currentPlaylist.id) { msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    PlaylistActionItem(
                        icon = rememberVectorPainter(Icons.Rounded.LinkOff),
                        label = "Unlink from $serviceLabel",
                        onClick = {
                            closeOptionsSheet()
                            playlistViewModel.unlinkSyncTarget(currentPlaylist.id)
                            android.widget.Toast.makeText(context, "Unlinked from $serviceLabel", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                // Queue · Play · Next (the whole playlist), then Auto | Export, then Delete.
                // Holding Next slides the "after which song" (Soon) slider up over Auto / Export / Delete.
                val canQueuePlaylist = localReorderableSongs.isNotEmpty()
                var soonOpen by remember { mutableStateOf(false) }
                val playlistQueueName = currentPlaylist.name.ifBlank { fallbackPlaylistName }
                fun queueWholePlaylist(
                    placement: com.theveloper.pixelplay.presentation.viewmodel.PlaybackDispatchStateHolder.PlaylistQueuePlacement,
                    shuffled: Boolean = false
                ) {
                    closeOptionsSheet()
                    playerViewModel.queuePlaylist(localReorderableSongs, playlistQueueName, currentPlaylist.id, placement, shuffled)
                }
                if (canQueuePlaylist) {
                    com.theveloper.pixelplay.presentation.components.QueuePlayNextRow(
                        onQueue = { queueWholePlaylist(com.theveloper.pixelplay.presentation.viewmodel.PlaybackDispatchStateHolder.PlaylistQueuePlacement.End) },
                        onPlay = {
                            closeOptionsSheet()
                            val first = displaySongs.firstOrNull()
                            if (first != null) {
                                // Same path as tapping a row: mood blends get ready first.
                                if (moodBlend != null) blendFriendsViewModel?.prepareMoodBlend(displaySongs)
                                playerViewModel.playSongs(displaySongs, first, blendQueueName(currentPlaylist.name), currentPlaylist.id)
                            }
                        },
                        onNext = { queueWholePlaylist(com.theveloper.pixelplay.presentation.viewmodel.PlaybackDispatchStateHolder.PlaylistQueuePlacement.Next) },
                        onNextLongClick = { soonOpen = !soonOpen },
                        soonOpen = soonOpen,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                androidx.compose.animation.AnimatedContent(
                    targetState = soonOpen && canQueuePlaylist,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) { it } +
                                fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate))) togetherWith
                                fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                        } else {
                            fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                                (slideOutVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) { it } +
                                    fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)))
                        }
                    },
                    label = "playlistOptionsSoon"
                ) { showSoon ->
                    if (showSoon) {
                        com.theveloper.pixelplay.presentation.components.PlaylistSoonPanel(
                            playerViewModel = playerViewModel,
                            onBack = { soonOpen = false },
                            onConfirm = { afterUpcoming ->
                                queueWholePlaylist(
                                    com.theveloper.pixelplay.presentation.viewmodel.PlaybackDispatchStateHolder.PlaylistQueuePlacement.AfterUpcoming(afterUpcoming)
                                )
                            },
                            onConfirmShuffled = if (localReorderableSongs.size > 1) {
                                { afterUpcoming ->
                                    queueWholePlaylist(
                                        com.theveloper.pixelplay.presentation.viewmodel.PlaybackDispatchStateHolder.PlaylistQueuePlacement.AfterUpcoming(afterUpcoming),
                                        shuffled = true
                                    )
                                }
                            } else null
                        )
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Auto: download songs added to this playlist later. Usable once
                                // something from it is on the device.
                                PlaylistOptionButton(
                                    label = stringResource(R.string.playlist_option_auto),
                                    icon = rememberVectorPainter(Icons.Rounded.DownloadDone),
                                    onClick = {
                                        closeOptionsSheet()
                                        playlistViewModel.setKeepPlaylistInSync(!downloadState.keepInSync)
                                    },
                                    selected = downloadState.keepInSync,
                                    enabled = downloadState.downloaded > 0 || downloadState.keepInSync,
                                    stateLabel = if (downloadState.keepInSync) "On" else "Off",
                                    modifier = Modifier.weight(1f)
                                )
                                PlaylistOptionButton(
                                    label = exportPlaylistLabel,
                                    icon = painterResource(R.drawable.rounded_attach_file_24),
                                    onClick = {
                                        closeOptionsSheet()
                                        showExportSheet = true

                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (canDeletePlaylist) {
                                Button(
                                    onClick = {
                                        closeOptionsSheet()
                                        deleteAlsoRemote = false
                                        showDeleteConfirmation = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .heightIn(min = 48.dp),
                                    shape = MaterialTheme.shapes.large,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.rounded_delete_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isConnectedPlaylist) "Remove playlist" else deletePlaylistLabel,
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

    if (showQualitySheet && currentPlaylist != null) {
        var breakdown by remember { mutableStateOf<com.theveloper.pixelplay.presentation.viewmodel.PlaylistQualityBreakdown?>(null) }
        // Recounted as downloads finish while the menu is open.
        LaunchedEffect(downloadState.downloaded, downloadState.downloadable) {
            breakdown = playlistViewModel.downloadQualityBreakdown()
        }
        PlaylistDownloadQualitySheet(
            breakdown = breakdown,
            downloadable = downloadState.downloadable,
            keepInSync = downloadState.keepInSync,
            initialWifiOnly = downloadWifiOnly,
            onKeepInSyncChange = { playlistViewModel.setKeepPlaylistInSync(it) },
            onDownload = { quality, wifiOnly, changeDownloaded ->
                playlistViewModel.downloadPlaylistAt(quality, wifiOnly, changeDownloaded)
            },
            onDismiss = { showQualitySheet = false }
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
            // Transition settings moved here from the options sheet. Unsaved edits are dropped.
            onEditTransitionClick = if (!isReadOnlyPlaylist) {
                {
                    showEditPlaylistDialog = false
                    navController.navigateSafely(Screen.EditTransition.createRoute(playlistId))
                }
            } else null,
            onSave = { name, imageUri, color, icon, scale, panX, panY, shapeType, d1, d2, d3, d4, imageEdits ->
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
                    coverShapeDetail4 = d4,
                    coverFlipHorizontal = imageEdits.flipHorizontal,
                    coverFilterId = imageEdits.filterId,
                    coverRotationDegrees = imageEdits.rotationDegrees
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

    if (showExportSheet && currentPlaylist != null) {
        PlaylistExportBottomSheet(
            visible = showExportSheet,
            onDismiss = { showExportSheet = false },
            playlistName = currentPlaylist.name.ifBlank { fallbackPlaylistName },
            songCount = songsInPlaylist.size,
            isSpotifyConnected = isSpotifyConnected,
            isYouTubeMusicConnected = isYouTubeMusicConnected,
            onExportFile = {
                showExportSheet = false
                val sanitizedName = PlaylistViewModel.sanitizeFileName(currentPlaylist.name.ifBlank { fallbackPlaylistName })
                m3uExportLauncher.launch("$sanitizedName.m3u")
            },
            onExportSpotify = { autoSync ->
                val pName = currentPlaylist.name.ifBlank { fallbackPlaylistName }
                isExportingToService = true
                exportStatusMessage = "Creating Spotify playlist & matching songs..."
                playlistViewModel.exportPlaylistToService(
                    playlistId = playlistId,
                    name = pName,
                    songs = songsInPlaylist,
                    service = MusicSources.SPOTIFY,
                    autoSync = autoSync,
                    onProgress = { step -> exportStatusMessage = step }
                ) { result ->
                    isExportingToService = false
                    exportStatusMessage = null
                    showExportSheet = false
                    result.onSuccess { msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    }.onFailure { err ->
                        android.widget.Toast.makeText(context, "Export failed: ${err.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            onExportYouTubeMusic = { autoSync ->
                val pName = currentPlaylist.name.ifBlank { fallbackPlaylistName }
                isExportingToService = true
                exportStatusMessage = "Creating YouTube Music playlist & adding songs..."
                playlistViewModel.exportPlaylistToService(
                    playlistId = playlistId,
                    name = pName,
                    songs = songsInPlaylist,
                    service = MusicSources.YOUTUBE_MUSIC,
                    autoSync = autoSync,
                    onProgress = { step -> exportStatusMessage = step }
                ) { result ->
                    isExportingToService = false
                    exportStatusMessage = null
                    showExportSheet = false
                    result.onSuccess { msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    }.onFailure { err ->
                        android.widget.Toast.makeText(context, "Export failed: ${err.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            onOpenTuneMyMusic = {
                showExportSheet = false
                showTransferPrompt = true
            },
            isExporting = isExportingToService,
            exportStatusText = exportStatusMessage
        )
    }

    if (showTransferPrompt && currentPlaylist != null) {
        // TuneMyMusic imports a playlist file: save one first (then the site opens), or just go.
        com.theveloper.pixelplay.presentation.components.ServiceSheet(
            title = stringResource(R.string.playlist_transfer_title),
            status = stringResource(R.string.playlist_transfer_body),
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.AutoMirrored.Rounded.OpenInNew),
            brandColor = MaterialTheme.colorScheme.primary,
            onDismiss = { showTransferPrompt = false },
        ) {
            com.theveloper.pixelplay.presentation.components.ServiceSheetPrimaryButton(
                text = stringResource(R.string.playlist_transfer_save_and_open),
                onClick = {
                    showTransferPrompt = false
                    openTuneMyMusicAfterSave = true
                    val sanitizedName = PlaylistViewModel.sanitizeFileName(currentPlaylist.name.ifBlank { fallbackPlaylistName })
                    m3uExportLauncher.launch("$sanitizedName.m3u")
                }
            )
            androidx.compose.material3.TextButton(
                onClick = {
                    showTransferPrompt = false
                    openTuneMyMusic()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.playlist_transfer_just_open)) }
        }
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
                onStartSongRadio = { seedSong -> playerViewModel.startSongRadio(seedSong) },
                // Select: enter Edit with this song ticked.
                onSelect = {
                    val key = (moodRows ?: playlistRows).firstOrNull { it.song.id == currentSong.id }?.key
                    selectedIds = setOfNotNull(key)
                    isSelectionMode = true
                },
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

/** One playlist row: its stable LazyColumn key and the song. */
@androidx.compose.runtime.Immutable
internal data class PlaylistRow(val key: String, val song: Song)

/**
 * After a selected row is dropped: pulls every other selected row next to it, keeping the
 * selection's relative order, with the block placed where the dropped row landed.
 */
internal fun gatherSelectedRows(rows: List<PlaylistRow>, selectedKeys: Set<String>, anchorKey: String): List<PlaylistRow> {
    val group = rows.filter { it.key in selectedKeys }
    if (group.size < 2 || group.none { it.key == anchorKey }) return rows
    val rest = rows.filter { it.key !in selectedKeys || it.key == anchorKey }
    val anchorAt = rest.indexOfFirst { it.key == anchorKey }
    return rest.toMutableList().apply {
        removeAt(anchorAt)
        addAll(anchorAt, group)
    }
}

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
    syncTarget: com.theveloper.pixelplay.data.preferences.RemoteSyncTarget? = null,
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
    /** Shows Edit (select + reorder) when the playlist has songs. */
    canEdit: Boolean,
    addLabel: String,
    sortLabel: String,
    onAddClick: () -> Unit,
    onEditClick: () -> Unit,
    onSortClick: () -> Unit,
    /** Edit mode is on: the Edit button shows its selected "Done" state. */
    isEditing: Boolean = false,
    /** Blend pages: the members' pictures on the cover and the friends button in the deck. */
    blendMembers: List<Pair<String, String?>>? = null,
    /** The blend is being rebuilt with new friends: the cover plays the building animation. */
    blendBuilding: Boolean = false,
    onBlendFriendsClick: () -> Unit = {},
    /** A friend's playlist: its saves on the platform, when known. */
    saves: Int? = null,
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
                if (blendMembers != null) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = blendBuilding,
                        enter = fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                        exit = fadeOut(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        com.theveloper.pixelplay.presentation.components.BlendBuildingCover(blendMembers, Modifier.fillMaxSize())
                    }
                    // Members bottom-centre: the art stays clear; tapping them changes who's in it.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = blendMembers.isNotEmpty() && !blendBuilding,
                        enter = fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)),
                        exit = fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                    ) {
                        com.theveloper.pixelplay.presentation.components.BlendAvatarRow(
                            members = blendMembers,
                            size = 34.dp,
                            onClick = onBlendFriendsClick
                        )
                    }
                }
                // This playlist is what's playing: the bars badge, bottom-right like everywhere else.
                com.theveloper.pixelplay.presentation.components.PlaylistPlayingBadge(
                    visible = com.theveloper.pixelplay.presentation.components.rememberIsPlaylistPlaying(playlist.id),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    size = 32.dp,
                    ringColor = surfaceColor
                )
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
                // "[♪] 42 · 1h 59m", then "· [♥] saves" for a friend's playlist when known.
                val totalMs = remember(songs) { songs.sumOf { it.duration } }
                com.theveloper.pixelplay.presentation.components.CompactCountBadge(
                    count = songs.size,
                    trailing = totalMs.takeIf { it > 0 }?.let(com.theveloper.pixelplay.presentation.components.CompactMeta::duration),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (syncTarget != null) {
                    val serviceColor = if (syncTarget.service == "SPOTIFY") Color(0xFF1DB954) else Color(0xFFFF0000)
                    val serviceName = if (syncTarget.service == "SPOTIFY") "Spotify" else "YT Music"
                    Text(
                        text = com.theveloper.pixelplay.presentation.components.CompactMeta.SEPARATOR,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = serviceColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = serviceName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold),
                            color = serviceColor
                        )
                    }
                }
                if (saves != null && saves >= 0) {
                    val savesSpoken = pluralStringResource(R.plurals.friend_playlist_saves, saves, saves)
                    Text(
                        text = com.theveloper.pixelplay.presentation.components.CompactMeta.SEPARATOR,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = savesSpoken }
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = saves.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                val shuffleSpin = rememberShuffleSpin()
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (shuffleEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        .clickable(enabled = shuffleEnabled, onClick = { shuffleSpin.play(); onShuffleClick() }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = shuffleLabel,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(36.dp).shuffleSpin(shuffleSpin)
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

            // Second row. Blends: friends · Edit (centred) · filter. Otherwise Add + Edit, Sort as an icon.
            Spacer(Modifier.height(16.dp))
            if (blendMembers != null) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Same size on both sides, so Edit sits exactly in the middle.
                    FilledTonalIconButton(onClick = onBlendFriendsClick, modifier = Modifier.size(48.dp)) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.Group,
                            contentDescription = stringResource(R.string.blend_choose_friends)
                        )
                    }
                    if (canEdit) {
                        PlaylistEditToggleButton(isEditing = isEditing, onClick = onEditClick)
                    }
                    FilledTonalIconButton(onClick = onSortClick, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = sortLabel)
                    }
                }
            } else {
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
                    }
                    // Edit: select songs and drag them into a new order, in one mode. Tap again to leave.
                    if (canEdit) {
                        PlaylistEditToggleButton(isEditing = isEditing, onClick = onEditClick)
                    }
                    FilledTonalIconButton(onClick = onSortClick) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = sortLabel)
                    }
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
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaybePullToRefresh(
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    if (enabled) {
        // Same M3 Expressive shape indicator as Friends' pull to refresh.
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) { content() }
    } else {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/**
 * The header's Edit button. Pressing it turns edit mode on and it switches to its selected look
 * ("Done", a tick, primary colours); pressing it again turns edit mode off.
 */
@Composable
private fun PlaylistEditToggleButton(isEditing: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val colorSpec = tween<Color>(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized)
    val container by animateColorAsState(
        targetValue = if (isEditing) colors.primary else colors.secondaryContainer,
        animationSpec = colorSpec,
        label = "editToggleContainer"
    )
    val content by animateColorAsState(
        targetValue = if (isEditing) colors.onPrimary else colors.onSecondaryContainer,
        animationSpec = colorSpec,
        label = "editToggleContent"
    )
    val editLabel = stringResource(R.string.playlist_edit_songs)
    val doneLabel = stringResource(R.string.playlist_edit_done)
    val editCd = stringResource(R.string.playlist_edit_songs_cd)
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = content),
        modifier = Modifier.semantics {
            contentDescription = editCd
            stateDescription = if (isEditing) doneLabel else editLabel
        }
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = isEditing,
            transitionSpec = {
                (fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                    androidx.compose.animation.scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.8f)) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
            },
            label = "editToggleLabel"
        ) { editing ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (editing) Icons.Rounded.Check else Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (editing) doneLabel else editLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * One button in the playlist options grid: icon over a short label, equal widths in its row.
 * [selected] gives it the toggled look (Soon while its panel is open, Auto while it's on).
 */
@Composable
private fun PlaylistOptionButton(
    label: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    stateLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val colorSpec = tween<Color>(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized)
    val container by animateColorAsState(
        if (selected) colors.primaryContainer else colors.surfaceContainerHighest, colorSpec, label = "optionContainer"
    )
    val content by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurface, colorSpec, label = "optionContent"
    )
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics { if (stateLabel != null) stateDescription = stateLabel },
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Soon panel: see components/PlaylistSoonPanel.kt (shared with the playlist multi-select sheet).

/**
 * The playlist download menu: High / Medium / Low cards, each with how many of this playlist's
 * songs are already downloaded at it. Downloading fills in the missing songs at the picked
 * quality; "Also change" re-downloads the ones at another quality (upgrade or downgrade).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDownloadQualitySheet(
    breakdown: com.theveloper.pixelplay.presentation.viewmodel.PlaylistQualityBreakdown?,
    downloadable: Int,
    keepInSync: Boolean,
    initialWifiOnly: Boolean,
    onKeepInSyncChange: (Boolean) -> Unit,
    onDownload: (com.theveloper.pixelplay.data.youtube.DownloadQuality, wifiOnly: Boolean, changeDownloaded: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val qualities = com.theveloper.pixelplay.data.youtube.DownloadQuality.entries
    // Start on the quality the playlist is already all at; otherwise Medium.
    var selected by rememberSaveable { mutableStateOf<com.theveloper.pixelplay.data.youtube.DownloadQuality?>(null) }
    val defaultQuality = breakdown?.counts?.filterValues { it > 0 }?.keys?.singleOrNull()
        ?: com.theveloper.pixelplay.data.youtube.DownloadQuality.MEDIUM
    val quality = selected ?: defaultQuality
    var wifiOnly by rememberSaveable { mutableStateOf(initialWifiOnly) }
    var changeDownloaded by rememberSaveable { mutableStateOf(false) }
    val missing = breakdown?.missing ?: 0
    val atOther = breakdown?.let { b -> b.counts.filterKeys { it != quality }.values.sum() + b.unknown } ?: 0
    val toChange = if (changeDownloaded) atOther else 0
    val actionLabel = when {
        missing > 0 && toChange > 0 -> "Download $missing · change $toChange"
        missing > 0 -> "Download $missing at ${quality.label}"
        toChange > 0 -> "Change $toChange to ${quality.label}"
        else -> "All set at ${quality.label}"
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Download playlist", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Text(
                text = if (breakdown == null) "Checking downloads…"
                else "${breakdown.downloaded} of $downloadable online songs on this device",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qualities.forEach { q ->
                    DownloadQualityCard(
                        quality = q,
                        hint = when (q) {
                            com.theveloper.pixelplay.data.youtube.DownloadQuality.HIGH -> "Best · Opus ~160 kbps"
                            com.theveloper.pixelplay.data.youtube.DownloadQuality.MEDIUM -> "AAC ~128 kbps"
                            com.theveloper.pixelplay.data.youtube.DownloadQuality.LOW -> "Smallest · ~48–70 kbps"
                        },
                        songCount = breakdown?.counts?.get(q),
                        selected = q == quality,
                        onClick = { selected = q },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (breakdown != null && breakdown.unknown > 0) {
                Text(
                    "${breakdown.unknown} downloaded at an unknown quality",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            // Upgrade / downgrade what's already downloaded (only when something is at another quality).
            androidx.compose.animation.AnimatedVisibility(visible = atOther > 0) {
                DownloadSheetToggleRow(
                    title = "Also change $atOther downloaded song${if (atOther == 1) "" else "s"} to ${quality.label}",
                    subtitle = "Each keeps playing from its current file until the new one is ready",
                    checked = changeDownloaded,
                    onCheckedChange = { changeDownloaded = it }
                )
            }
            DownloadSheetToggleRow(
                title = "Only download on Wi-Fi",
                subtitle = null,
                checked = wifiOnly,
                onCheckedChange = { wifiOnly = it }
            )
            DownloadSheetToggleRow(
                title = "Keep in sync",
                subtitle = "Download songs added to this playlist later",
                checked = keepInSync,
                onCheckedChange = onKeepInSyncChange
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val chosen = quality
                    val wifi = wifiOnly
                    val change = changeDownloaded
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDownload(chosen, wifi, change)
                        onDismiss()
                    }
                },
                enabled = breakdown != null && (missing > 0 || toChange > 0),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One quality card: name, what it means, and "x songs" already downloaded at it. */
@Composable
private fun DownloadQualityCard(
    quality: com.theveloper.pixelplay.data.youtube.DownloadQuality,
    hint: String,
    songCount: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val colorSpec = tween<Color>(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized)
    val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, colorSpec, label = "qualityCard")
    val content by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurface, colorSpec, label = "qualityCardContent")
    val countLabel = when (songCount) {
        null -> "…"
        1 -> "1 song"
        else -> "$songCount songs"
    }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .semantics {
                stateDescription = (if (selected) "Selected, " else "") + "$countLabel downloaded at ${quality.label}"
            }
            .heightIn(min = 96.dp)
            .padding(12.dp)
    ) {
        Text(quality.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = content)
        Text(hint, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.75f), maxLines = 2)
        Spacer(Modifier.weight(1f, fill = false))
        Spacer(Modifier.height(8.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = countLabel,
            transitionSpec = {
                (slideInVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) { it / 2 } +
                    fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate))) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
            },
            label = "qualityCount"
        ) { label ->
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = content)
        }
    }
}

/** A whole-row switch for the download menu (the row toggles; the switch shows the state). */
@Composable
private fun DownloadSheetToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
