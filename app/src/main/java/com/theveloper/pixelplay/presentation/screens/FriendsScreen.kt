@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.rounded.PushPin
import com.theveloper.pixelplay.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.social.FRIENDS_MIX_QUEUE_NAME
import com.theveloper.pixelplay.data.social.FriendTrack
import com.theveloper.pixelplay.presentation.components.ActiveFriendsPill
import com.theveloper.pixelplay.presentation.components.AddFriendPlaylistDialog
import com.theveloper.pixelplay.presentation.components.FriendHistorySheet
import com.theveloper.pixelplay.presentation.components.FriendRow
import com.theveloper.pixelplay.presentation.components.FriendTrackAction
import com.theveloper.pixelplay.presentation.components.FriendsLiveGreen
import com.theveloper.pixelplay.presentation.components.FriendsMixButton
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely
import com.theveloper.pixelplay.presentation.viewmodel.FriendPlaylistUi
import com.theveloper.pixelplay.presentation.viewmodel.FriendUi
import com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Friends, as its own screen: Friends Mix on top, then who's online, then everyone else.
 * Tap a picture for that friend's week, tap a name for their playlists, long-press to select
 * friends (remove several, or rename one).
 */
@Composable
fun FriendsScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    // Friends is a bottom-nav tab, so there's no back arrow; kept for any caller that pushes it.
    onBack: (() -> Unit)? = null,
    viewModel: FriendsViewModel = hiltViewModel(),
    playlistViewModel: com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val online by viewModel.onlineFriends.collectAsStateWithLifecycle()
    val offline by viewModel.offlineFriends.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val checkingActivity by viewModel.checkingActivity.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    // Includes pins still being saved, so the pinned mark shows at once.
    val pinnedIds by viewModel.pinnedPlaylistKeys.collectAsStateWithLifecycle()
    val mix by viewModel.friendsMix.collectAsStateWithLifecycle()
    val blends by viewModel.blends.collectAsStateWithLifecycle()
    val customBlend by viewModel.customBlend.collectAsStateWithLifecycle()
    val customBlendIds by viewModel.customBlendFriendIds.collectAsStateWithLifecycle()
    val friendSongCounts by viewModel.friendSongCounts.collectAsStateWithLifecycle()
    var showCustomBlend by rememberSaveable { mutableStateOf(false) }
    // "Build blend" pressed: the custom card plays its building animation, morphs into the built
    // card, then the blend page opens. 0 = not building.
    var customBuildStartedAt by rememberSaveable { mutableStateOf(0L) }
    fun openCustomBlendPage(blend: com.theveloper.pixelplay.data.social.CustomBlend) {
        viewModel.prepareCustomBlend() // so rows played from the page show whose song it is
        val id = com.theveloper.pixelplay.presentation.components.registerCustomBlendPlaylist(
            blend, com.theveloper.pixelplay.presentation.components.customBlendTitle(blend, friends)
        )
        navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
    }
    LaunchedEffect(customBuildStartedAt) {
        val started = customBuildStartedAt
        if (started == 0L) return@LaunchedEffect
        // Long enough for the building animation to read, even when the mix is ready at once.
        kotlinx.coroutines.delay((CUSTOM_BLEND_MIN_BUILD_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0L))
        val built = kotlinx.coroutines.withTimeoutOrNull(CUSTOM_BLEND_BUILD_TIMEOUT_MS) {
            snapshotFlow { customBlend }.first { it != null && it.songs.isNotEmpty() && !it.isBuilding }
        }
        customBuildStartedAt = 0L // the card morphs from building to built
        if (built != null) {
            kotlinx.coroutines.delay(MotionTokens.DurationMedium2.toLong() + 120L)
            openCustomBlendPage(built)
        } else {
            viewModel.notice.value = "Your blend fills in once your friends have listened to a few songs. It refreshes every day."
        }
    }
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    // Friends Mix is the current queue (same check as MainActivity's Friends tab dot).
    val friendsMixIsCurrent by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { it.currentQueueSourceName == FRIENDS_MIX_QUEUE_NAME }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    // Keep friend activity fresh while this screen is showing.
    viewModel.polling.collectAsStateWithLifecycle(Unit)
    val cachingPlaylists by viewModel.cachingPlaylists.collectAsStateWithLifecycle()
    val followingSession by viewModel.following.collectAsStateWithLifecycle()
    val myFollowingIds by viewModel.myFollowingIds.collectAsStateWithLifecycle()

    // First song of the last Friends Mix play, for the card's starting / failed feedback.
    var mixStartSongId by remember { mutableStateOf<String?>(null) }
    // The song whose action sheet is open, with the friend who played it.
    var actionFor by remember { mutableStateOf<Pair<String, FriendTrack>?>(null) }
    var historyFor by rememberSaveable { mutableStateOf<String?>(null) }
    var profileFor by remember { mutableStateOf<com.theveloper.pixelplay.data.social.SocialPerson?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingByLink by remember { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var renaming by remember { mutableStateOf<List<FriendUi>?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var followMenu by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    // Selected friends that went away (removed) drop out of the selection.
    val presentIds = remember(friends) { friends.mapTo(hashSetOf()) { it.id } }
    LaunchedEffect(presentIds) {
        if (friends.isNotEmpty() && selectedIds.any { it !in presentIds }) selectedIds = selectedIds.filter { it in presentIds }
    }

    BackHandler(enabled = selectionMode) { selectedIds = emptyList() }

    val toggleSelect: (FriendUi) -> Unit = { friend ->
        selectedIds = if (friend.id in selectedIds) selectedIds - friend.id else selectedIds + friend.id
    }
    val search: (FriendTrack) -> Unit = { track ->
        playerViewModel.submitSearch(listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" "))
        navController.navigateToTopLevelSafely(Screen.Search.route)
    }
    // Actions on a friend's song; search only when it can't be played. Anything played or
    // queued is tagged with the friend so the queue shows who it came from.
    val onTrackAction: (FriendUi?, FriendTrack, FriendTrackAction) -> Unit = { friend, track, action ->
        val song = viewModel.songFor(track)
        if (song != null && friend != null && action in setOf(FriendTrackAction.PLAY, FriendTrackAction.NEXT, FriendTrackAction.QUEUE)) {
            viewModel.tag(friend, listOf(song))
        }
        when {
            // Artist / album pages: the matched song's own route, else by name, else a search.
            action == FriendTrackAction.ARTIST -> when {
                song != null -> navController.navigateSafely(Screen.ArtistDetail.createRouteForSong(song))
                track.artist.isNotBlank() -> navController.navigateSafely(Screen.ArtistDetail.createRouteForName(track.artist))
                else -> search(track)
            }
            action == FriendTrackAction.ALBUM -> {
                val albumTitle = track.album.ifBlank { track.title }
                val route = song?.let { Screen.AlbumDetail.createRouteForSong(it) }
                    ?: albumTitle.takeIf { it.isNotBlank() }?.let {
                        Screen.AlbumDetail.createRouteForName(albumTitle = it, artistName = track.artist.ifBlank { null })
                    }
                if (route != null) navController.navigateSafely(route)
                else {
                    playerViewModel.submitSearch(track.artist)
                    navController.navigateToTopLevelSafely(Screen.Search.route)
                }
            }
            song == null || action == FriendTrackAction.SEARCH -> search(track)
            action == FriendTrackAction.PLAY -> playerViewModel.playSongs(listOf(song), song, friend?.let { "${it.name}'s listening" } ?: "Friends")
            action == FriendTrackAction.NEXT -> playerViewModel.addSongNextToQueue(song)
            action == FriendTrackAction.QUEUE -> playerViewModel.addSongToQueue(song)
            action == FriendTrackAction.LIKE -> playerViewModel.toggleFavoriteSpecificSong(song, removing = song.id in favoriteIds)
        }
    }
    val canPlay: (FriendTrack) -> Boolean = { viewModel.songFor(it) != null }
    val openTrack: (FriendUi, FriendTrack) -> Unit = { friend, track ->
        if (canPlay(track)) actionFor = friend.id to track else search(track)
    }
    val openPlaylist: (FriendUi, FriendPlaylistUi) -> Unit = { friend, playlist ->
        val id = playlist.playlistId ?: "${playlist.source}:${playlist.remoteId}"
        if (playlist.playlistId == null) {
            viewModel.savePublicPlaylist(friend, playlist) { /* cached in background */ }
        }
        navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
    }

    val live = online.size
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Clear the floating bottom nav (always shown here) plus the mini player when something plays,
    // so the last friend can scroll fully into view.
    val navBarClearance = 96.dp
    val bottomPadding = navBarClearance + bottomInset + 24.dp +
        (if (playerState.currentSong != null) MiniPlayerHeight + 8.dp else 0.dp)

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = selectionMode,
                transitionSpec = {
                    (fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                        slideInVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) { -it / 4 }) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                },
                label = "friendsTopBar"
            ) { selecting ->
                if (selecting) {
                    TopAppBar(
                        title = { Text("${selectedIds.size} selected", fontFamily = GoogleSansRounded, maxLines = 1) },
                        navigationIcon = {
                            IconButton(onClick = { selectedIds = emptyList() }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.friends_deselect_all))
                            }
                        },
                        actions = {
                            val selected = friends.filter { it.id in selectedIds }
                            // Pin if any selected friend isn't pinned yet, else Unpin.
                            val pin = selected.any { !it.pinned }
                            IconButton(onClick = {
                                viewModel.setPinned(selectedIds.toSet(), pin)
                                selectedIds = emptyList()
                            }) {
                                // Pin, or a crossed-out pin when everything selected is already pinned.
                                if (pin) {
                                    Icon(Icons.Rounded.PushPin, contentDescription = stringResource(R.string.friends_pin))
                                } else {
                                    UnpinIcon(
                                        contentDescription = stringResource(R.string.friends_unpin),
                                        gapColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                }
                            }
                            // Rename works for one or many (one sheet, a field each).
                            IconButton(onClick = { renaming = selected.takeIf { it.isNotEmpty() } }) {
                                Icon(Icons.Rounded.Edit, contentDescription = pluralStringResource(R.plurals.friends_rename_title, selected.size, selected.size))
                            }
                            IconButton(onClick = { confirmRemove = true }) {
                                Icon(Icons.Rounded.Delete, contentDescription = pluralStringResource(R.plurals.friends_remove_title, selected.size, selected.size))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                } else {
                    // Shared tab header: "Friends" (+ who you're following) · add · live pill · settings.
                    val session = following
                    val followingLine: (@Composable () -> Unit)? = if (session == null) null else {
                        {
                            Box {
                                Text(
                                    "Following ${session.friendName}",
                                    modifier = Modifier.clickable(onClickLabel = "Following options") { followMenu = true },
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = GoogleSansRounded),
                                    color = FriendsLiveGreen, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                DropdownMenu(expanded = followMenu, onDismissRequest = { followMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Stop following") },
                                        onClick = { followMenu = false; viewModel.stopFollowing() }
                                    )
                                }
                            }
                        }
                    }
                    com.theveloper.pixelplay.presentation.components.TabHeader(
                        title = "Friends",
                        onSettingsClick = { navController.navigateSafely(Screen.Settings.route) },
                        subtitle = followingLine,
                        // Pushed (not as a tab): a back arrow in front of the title.
                        titleContent = if (onBack == null) null else {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalIconButton(
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        onClick = onBack
                                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                                    Column(Modifier.padding(start = 8.dp)) {
                                        com.theveloper.pixelplay.presentation.components.TabHeaderTitle("Friends")
                                        followingLine?.invoke()
                                    }
                                }
                            }
                        },
                        actions = {
                            // Live friends pill, left of settings. "Add a friend" is the last row of the list.
                            ActiveFriendsPill(live, friends.size)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        val placement = tween<IntOffset>(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)
        val fadeInSpec = tween<Float>(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)
        val fadeOutSpec = tween<Float>(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)
        // Pull down to check friends' status and playlists now (same expressive indicator as the
        // song cards' starting state).
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refreshNow() },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (viewModel.hasLiveSource) {
                item(key = "friends_mix", contentType = "friends_mix") {
                    FriendsMixButton(
                        songCount = mix.size,
                        friends = remember(mix) { mix.map { it.friend }.distinctBy { it.id } },
                        loading = false,
                        isCurrentQueue = friendsMixIsCurrent,
                        startSongId = mixStartSongId,
                        // The card opens Friends Mix as a playlist page with the blend filters.
                        onClick = {
                            val songs = viewModel.prepareFriendsMixPage()
                            val id = com.theveloper.pixelplay.presentation.components.registerFriendsMixPlaylist(songs)
                            navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
                        },
                        // The play button plays it here (play / pause once it's the queue).
                        onPlay = {
                            if (friendsMixIsCurrent) {
                                playerViewModel.playPause()
                            } else {
                                val songs = viewModel.prepareFriendsMix()
                                songs.firstOrNull()?.let {
                                    mixStartSongId = it.id
                                    playerViewModel.playSongs(songs, it, FRIENDS_MIX_QUEUE_NAME)
                                }
                            }
                        },
                        modifier = Modifier.padding(bottom = 8.dp).animateItem(fadeInSpec, placement, fadeOutSpec)
                    )
                }
            } else {
                item(key = "no_live_source", contentType = "notice") {
                    Text("Live listening and history appear once Spotify friend activity is connected.",
                        Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Blends: right under Friends Mix, one daily mix of your listening and each friend's.
            // Always shown when you have friends; a friend with little history shows "Building…".
            if (friends.isNotEmpty() && !selectionMode) {
                item(key = "blends", contentType = "blends") {
                    com.theveloper.pixelplay.presentation.components.BlendsRow(
                        blends = blends,
                        placeholderFriends = if (blends.isEmpty()) friends else emptyList(),
                        onOpen = { blend ->
                            if (blend.songs.isNotEmpty() && blend.hasFriendData) {
                                val id = com.theveloper.pixelplay.presentation.components.registerBlendPlaylist(blend)
                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
                            } else {
                                viewModel.notice.value = "Your blend with ${blend.friendName} fills in once they've listened to a few songs. It refreshes every day."
                            }
                        },
                        // Last card: you + every friend you tick, in one playlist of up to 100 songs.
                        trailing = {
                            com.theveloper.pixelplay.presentation.components.CustomBlendCard(
                                blend = customBlend,
                                pickedFriends = remember(friends, customBlendIds) { friends.filter { it.id in customBlendIds } },
                                building = customBuildStartedAt != 0L,
                                onClick = {
                                    val built = customBlend
                                    when {
                                        customBuildStartedAt != 0L -> Unit
                                        // Made before: straight to the built blend.
                                        customBlendIds.isNotEmpty() && built != null && built.songs.isNotEmpty() && !built.isBuilding ->
                                            openCustomBlendPage(built)
                                        customBlendIds.isNotEmpty() && built?.isBuilding == true ->
                                            viewModel.notice.value = "Your blend fills in once your friends have listened to a few songs. It refreshes every day."
                                        // First time: just the friend list to tick.
                                        else -> showCustomBlend = true
                                    }
                                },
                                onAvatarsClick = { showCustomBlend = true }
                            )
                        },
                        modifier = Modifier
                            .padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
                            .animateItem(fadeInSpec, placement, fadeOutSpec)
                    )
                }
            }

            fun section(title: String, key: String, list: List<FriendUi>) {
                if (list.isEmpty()) return
                item(key = key, contentType = "section_header") {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                            .semantics { heading() }
                            .animateItem(fadeInSpec, placement, fadeOutSpec)
                    )
                }
                items(list, key = { it.id }, contentType = { "friend" }) { friend ->
                    Column(Modifier.animateItem(fadeInSpec, placement, fadeOutSpec)) {
                        FriendRow(
                            friend = friend,
                            selectionMode = selectionMode,
                            selected = friend.id in selectedIds,
                            pinnedPlaylistIds = pinnedIds,
                            pinned = friend.pinned,
                            cachingPlaylists = cachingPlaylists,
                            isFriendFollowed = { id -> followingSession?.friendId == id || id in myFollowingIds },
                            onToggleSelect = { toggleSelect(friend) },
                            onStartSelection = { selectedIds = listOf(friend.id) },
                            onAvatarClick = { historyFor = friend.id },
                            onTrackClick = { track -> openTrack(friend, track) },
                            onExpand = { viewModel.gatherPlaylists(friend) },
                            onOpenPlaylist = { playlist -> openPlaylist(friend, playlist) },
                            onTogglePin = { playlist, pin -> viewModel.setPinned(friend, playlist, pin) },
                            onHistory = { historyFor = friend.id },
                            songIdFor = { track -> viewModel.songFor(track)?.id },
                            currentSongId = playerState.currentSong?.id,
                        )
                        if (friend != list.last()) {
                            HorizontalDivider(Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            // First check after opening: who's online / playing right now.
            if (checkingActivity && friends.isNotEmpty()) {
                item(key = "checking_activity", contentType = "notice") {
                    CheckingFriendsActivity(Modifier.animateItem(fadeInSpec, placement, fadeOutSpec))
                }
            }
            section("Online", "header_online", online)
            section("Offline", "header_offline", offline)

            // Always the very end of the list (after offline friends, or alone when empty).
            item(key = "add_friend_skeleton", contentType = "add_friend") {
                com.theveloper.pixelplay.presentation.components.AddFriendSkeletonRow(
                    onClick = { adding = true },
                    modifier = Modifier
                        .padding(top = if (friends.isEmpty()) 12.dp else 4.dp)
                        .animateItem(fadeInSpec, placement, fadeOutSpec)
                )
            }
        }
        }
    }

    if (showCustomBlend) {
        com.theveloper.pixelplay.presentation.components.CustomBlendSheet(
            friends = friends,
            selectedIds = customBlendIds,
            songCounts = friendSongCounts,
            onToggle = viewModel::toggleCustomBlendFriend,
            onSetAll = viewModel::setCustomBlendFriends,
            onBuild = {
                showCustomBlend = false
                customBuildStartedAt = System.currentTimeMillis()
            },
            onDismiss = { showCustomBlend = false }
        )
    }

    // Someone from Add friend who isn't a friend yet: their profile (counts, followers / following,
    // public playlists) in the same sheet the friend history uses.
    profileFor?.let { person ->
        val asFriend = remember(person.userId) {
            FriendUi(
                id = person.friendId,
                name = person.name,
                platformName = person.name,
                source = "SPOTIFY",
                avatarUrl = person.avatarUrl,
                presence = com.theveloper.pixelplay.data.social.FriendPresence.UNKNOWN,
                track = null,
                playlists = emptyList(),
            )
        }
        FriendHistorySheet(
            friend = asFriend,
            history = emptyList(),
            isFollowing = false,
            canPlay = { false },
            onPlay = {},
            onMore = {},
            onFollow = {},
            onShuffle = {},
            onDismiss = { profileFor = null },
            socialViewModel = viewModel,
            asProfile = true,
            onOpenAccountPlaylist = { ownerId, playlist ->
                viewModel.openAccountPlaylist(ownerId, playlist) { id ->
                    profileFor = null
                    adding = false
                    navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
                }
            },
        )
    }

    historyFor?.let { id ->
        val friend = friends.find { it.id == id }
        if (friend != null) {
            val history by remember(id) { viewModel.history(id) }.collectAsStateWithLifecycle(emptyList())
            FriendHistorySheet(
                friend = friend,
                history = history,
                isFollowing = following?.friendId == friend.id,
                canPlay = canPlay,
                onPlay = { track -> onTrackAction(friend, track, FriendTrackAction.PLAY) },
                onMore = { track -> openTrack(friend, track) },
                onFollow = { viewModel.toggleFollow(friend) },
                onShuffle = {
                    val songs = history.mapNotNull(viewModel::songFor).distinctBy { it.id }.shuffled()
                    viewModel.tag(friend, songs)
                    songs.firstOrNull()?.let { playerViewModel.playSongs(songs, it, "${friend.name}'s history") }
                },
                onDismiss = { historyFor = null },
                songIdFor = { track -> viewModel.songFor(track)?.id },
                currentSongId = playerState.currentSong?.id,
                isLiked = { track -> viewModel.songFor(track)?.id?.let { it in favoriteIds } == true },
                // Heart: likes the matched song; a song PixelPlayer can't match yet opens search.
                onToggleLike = { track ->
                    if (viewModel.songFor(track) == null) historyFor = null // goes to Search
                    onTrackAction(friend, track, FriendTrackAction.LIKE)
                },
                onArtistClick = { artist ->
                    historyFor = null
                    navController.navigateSafely(Screen.ArtistDetail.createRouteForName(artist))
                },
                // Soon (deep right swipe) opens the "after which song" slider under the row.
                soonPlayer = playerViewModel,
                soonSong = { track -> viewModel.songFor(track) },
                onSoonConfirm = { track, k ->
                    viewModel.songFor(track)?.let { song ->
                        viewModel.tag(friend, listOf(song))
                        playerViewModel.addSongAfterUpcoming(song, k)
                    }
                },
                // Swipes: left = end of queue, right = next, further right = play soon.
                onSwipe = { track, swipe ->
                    viewModel.songFor(track)?.let { song ->
                        viewModel.tag(friend, listOf(song))
                        when (swipe) {
                            com.theveloper.pixelplay.presentation.components.FriendSwipe.NEXT -> playerViewModel.addSongNextToQueue(song)
                            com.theveloper.pixelplay.presentation.components.FriendSwipe.SOON -> playerViewModel.addSongSoon(song)
                            com.theveloper.pixelplay.presentation.components.FriendSwipe.QUEUE -> playerViewModel.addSongToQueue(song)
                        }
                    }
                },
                socialViewModel = viewModel,
                // A public playlist on someone's profile (reached through followers / following).
                onOpenAccountPlaylist = { ownerId, playlist ->
                    viewModel.openAccountPlaylist(ownerId, playlist) { id ->
                        historyFor = null
                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
                    }
                },
            )
        }
    }
    // Holding a friend's song opens the same song options as everywhere else in the app, with
    // "‹friend› is listening now / played 2 h ago" under the artist. Anything played or queued
    // from here is still tagged with the friend so the queue shows who it came from.
    var friendPlaylistSheetFor by remember { mutableStateOf<com.theveloper.pixelplay.data.model.Song?>(null) }
    actionFor?.let { (friendId, track) ->
        val friend = friends.find { it.id == friendId }
        val song = remember(track) { viewModel.songFor(track) }
        if (song == null) {
            // No match to offer options for (openTrack only opens matched songs; this covers a
            // match that went away while the sheet was opening).
            LaunchedEffect(track) { actionFor = null; search(track) }
        } else {
            val tagged: () -> Unit = { if (friend != null) viewModel.tag(friend, listOf(song)) }
            val closeAndGo: (String?) -> Unit = { route ->
                actionFor = null
                historyFor = null
                if (route != null) navController.navigateSafely(route)
            }
            com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet(
                song = song,
                isFavorite = song.id in favoriteIds,
                onToggleFavorite = { onTrackAction(friend, track, FriendTrackAction.LIKE) },
                onDismiss = { actionFor = null },
                onPlaySong = { onTrackAction(friend, track, FriendTrackAction.PLAY) },
                onAddToQueue = { onTrackAction(friend, track, FriendTrackAction.QUEUE) },
                onAddNextToQueue = { onTrackAction(friend, track, FriendTrackAction.NEXT) },
                // Hold Next → Soon, tagged with the friend like Play / Next / Queue.
                soonPlayer = playerViewModel,
                onAddSoon = { k ->
                    tagged()
                    playerViewModel.addSongAfterUpcoming(song, k)
                },
                onStartSongRadio = { seed -> playerViewModel.startSongRadio(seed) },
                onAddToPlayList = { friendPlaylistSheetFor = song },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    actionFor = null
                    historyFor = null
                    onTrackAction(friend, track, FriendTrackAction.ALBUM)
                },
                onNavigateToArtist = {
                    actionFor = null
                    historyFor = null
                    onTrackAction(friend, track, FriendTrackAction.ARTIST)
                },
                onNavigateToArtistById = { artistId -> closeAndGo(Screen.ArtistDetail.createRoute(artistId)) },
                onNavigateToArtistByName = { name -> closeAndGo(Screen.ArtistDetail.createRouteForName(name)) },
                onNavigateToGenre = {
                    closeAndGo(
                        song.genre?.takeIf { it.isNotBlank() }?.let {
                            Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8"))
                        }
                    )
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        song, newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics,
                        newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate
                    )
                },
                // Nothing to remove a friend's play from.
                removeFromListTrigger = { actionFor = null },
                headerExtra = if (friend != null) {
                    {
                        com.theveloper.pixelplay.presentation.components.FriendListeningChip(
                            friend = friend,
                            track = track,
                            onClick = {
                                actionFor = null
                                historyFor = friend.id
                            }
                        )
                    }
                } else null,
            )
        }
    }
    friendPlaylistSheetFor?.let { song ->
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = listOf(song),
            onDismiss = { friendPlaylistSheetFor = null },
            bottomBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            playerViewModel = playerViewModel,
        )
    }
    renaming?.let { list ->
        com.theveloper.pixelplay.presentation.components.FriendsRenameSheet(
            friends = list,
            onSave = { changes ->
                viewModel.renameFriends(changes)
                selectedIds = emptyList()
            },
            onDismiss = { renaming = null }
        )
    }
    if (confirmRemove) {
        val toRemove = friends.filter { it.id in selectedIds }
        com.theveloper.pixelplay.presentation.components.FriendsRemoveSheet(
            friends = toRemove,
            canUnfollow = toRemove.any { viewModel.supportsSocial(it.id) },
            onRemove = { unfollow ->
                viewModel.removeFriends(selectedIds.toSet(), unfollowOnSpotify = unfollow)
                selectedIds = emptyList()
            },
            onDismiss = { confirmRemove = false }
        )
    }
    val unfollowFailed by viewModel.unfollowFailed.collectAsStateWithLifecycle()
    if (unfollowFailed > 0) {
        AlertDialog(
            onDismissRequest = { viewModel.unfollowFailed.value = 0 },
            text = { Text(pluralStringResource(R.plurals.friends_unfollow_failed, unfollowFailed, unfollowFailed)) },
            confirmButton = { TextButton(onClick = { viewModel.unfollowFailed.value = 0 }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
    // Add a friend: Spotify people search; the playlist-link dialog stays one tap away.
    if (adding) {
        com.theveloper.pixelplay.presentation.components.AddFriendSheet(
            viewModel = viewModel,
            onAddByLink = { adding = false; addingByLink = true },
            onDismiss = { adding = false },
            // Tap a person: a friend opens their week; anyone else opens their profile.
            onOpenPerson = { person ->
                if (friends.any { it.id == person.friendId }) historyFor = person.friendId
                else profileFor = person
            },
        )
    }
    if (addingByLink) AddFriendPlaylistDialog(onAdd = { name, link, done -> viewModel.addPlaylist(name, link, done) }, onDismiss = { addingByLink = false })
    notice?.let {
        AlertDialog(onDismissRequest = { viewModel.notice.value = null }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { viewModel.notice.value = null }) { Text("OK") } })
    }
}

/** Shown while the first poll checks who's online and what they're playing. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckingFriendsActivity(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.LoadingIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Checking who's listening…",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold
            )
            Text(
                "Seeing which friends are online and what they're playing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shortest the custom blend card shows its building animation after "Build blend". */
private const val CUSTOM_BLEND_MIN_BUILD_MS = 1_400L

/** Give up waiting for a built custom blend after this long (too little listening yet). */
private const val CUSTOM_BLEND_BUILD_TIMEOUT_MS = 8_000L


/** A push pin with a slash through it (Unpin). The slash has a gap in [gapColor] so it reads on the pin. */
@Composable
private fun UnpinIcon(contentDescription: String, gapColor: androidx.compose.ui.graphics.Color) {
    val tint = androidx.compose.material3.LocalContentColor.current
    Box(
        Modifier
            .size(24.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.PushPin, contentDescription = null)
        androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            val start = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.14f)
            val end = androidx.compose.ui.geometry.Offset(size.width * 0.86f, size.height * 0.86f)
            drawLine(gapColor, start, end, strokeWidth = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(tint, start, end, strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
