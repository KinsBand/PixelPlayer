@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.theveloper.pixelplay.presentation.components.FriendTrackSheet
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

/**
 * Friends, as its own screen: Friends Mix on top, then who's online, then everyone else.
 * Tap a picture for that friend's week, tap a name for their playlists, long-press to select
 * friends (remove several, or rename one).
 */
@Composable
fun FriendsScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val online by viewModel.onlineFriends.collectAsStateWithLifecycle()
    val offline by viewModel.offlineFriends.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val pinnedIds by viewModel.pinnedPlaylistIds.collectAsStateWithLifecycle()
    val mix by viewModel.friendsMix.collectAsStateWithLifecycle()
    val blends by viewModel.blends.collectAsStateWithLifecycle()
    val customBlend by viewModel.customBlend.collectAsStateWithLifecycle()
    val customBlendIds by viewModel.customBlendFriendIds.collectAsStateWithLifecycle()
    val friendSongCounts by viewModel.friendSongCounts.collectAsStateWithLifecycle()
    var showCustomBlend by rememberSaveable { mutableStateOf(false) }
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    // Keep friend activity fresh while this screen is showing.
    viewModel.polling.collectAsStateWithLifecycle(Unit)

    // The song whose action sheet is open, with the friend who played it.
    var actionFor by remember { mutableStateOf<Pair<String, FriendTrack>?>(null) }
    var historyFor by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var renaming by remember { mutableStateOf<FriendUi?>(null) }
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
        playerViewModel.updateSearchQuery(listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" "))
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
            action == FriendTrackAction.ARTIST -> {
                track.artist.takeIf { it.isNotBlank() }?.let { navController.navigateSafely(Screen.ArtistDetail.createRouteForName(it)) }
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
        val id = playlist.playlistId
        if (id != null) navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
        else viewModel.savePublicPlaylist(friend, playlist) { saved -> navController.navigateSafely(Screen.PlaylistDetail.createRoute(saved)) }
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
                                Icon(Icons.Rounded.Close, contentDescription = "Stop selecting")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { renaming = friends.find { it.id == selectedIds.singleOrNull() } },
                                enabled = selectedIds.size == 1
                            ) { Icon(Icons.Rounded.Edit, contentDescription = "Rename friend") }
                            IconButton(onClick = { confirmRemove = true }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove friends")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Friends", fontFamily = GoogleSansRounded, maxLines = 1)
                                following?.let { session ->
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
                        },
                        navigationIcon = {
                            FilledTonalIconButton(
                                modifier = Modifier.padding(start = 8.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                onClick = onBack
                            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                        },
                        actions = {
                            // Add friend's playlist lives in the header, left of the friend count.
                            FilledTonalIconButton(
                                onClick = { adding = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) { Icon(Icons.Rounded.Add, contentDescription = "Add friend's playlist") }
                            Box(Modifier.padding(start = 4.dp, end = 12.dp)) { ActiveFriendsPill(live, friends.size) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }
    ) { innerPadding ->
        val placement = tween<IntOffset>(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)
        val fadeInSpec = tween<Float>(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)
        val fadeOutSpec = tween<Float>(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (viewModel.hasLiveSource) {
                item(key = "friends_mix", contentType = "friends_mix") {
                    FriendsMixButton(
                        songCount = mix.size,
                        friends = remember(mix) { mix.map { it.friend }.distinctBy { it.id } },
                        loading = false,
                        onClick = {
                            val songs = viewModel.prepareFriendsMix()
                            songs.firstOrNull()?.let { playerViewModel.playSongs(songs, it, FRIENDS_MIX_QUEUE_NAME) }
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
                                onClick = { showCustomBlend = true }
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
                            onToggleSelect = { toggleSelect(friend) },
                            onStartSelection = { selectedIds = listOf(friend.id) },
                            onAvatarClick = { historyFor = friend.id },
                            onTrackClick = { track -> openTrack(friend, track) },
                            onExpand = { viewModel.gatherPlaylists(friend) },
                            onOpenPlaylist = { playlist -> openPlaylist(friend, playlist) },
                            onTogglePin = { playlist, pin -> viewModel.setPinned(friend, playlist, pin) },
                            onHistory = { historyFor = friend.id },
                        )
                        if (friend != list.last()) {
                            HorizontalDivider(Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            section("Online", "header_online", online)
            section("Offline", "header_offline", offline)

            if (friends.isEmpty()) {
                item(key = "empty", contentType = "notice") {
                    Text("Add a friend's playlist to start.",
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showCustomBlend) {
        com.theveloper.pixelplay.presentation.components.CustomBlendSheet(
            friends = friends,
            selectedIds = customBlendIds,
            songCounts = friendSongCounts,
            blend = customBlend,
            onToggle = viewModel::toggleCustomBlendFriend,
            onSetAll = viewModel::setCustomBlendFriends,
            onPlay = {
                val songs = viewModel.prepareCustomBlend()
                songs.firstOrNull()?.let { playerViewModel.playSongs(songs, it, com.theveloper.pixelplay.presentation.components.CUSTOM_BLEND_NAME) }
                showCustomBlend = false
            },
            onOpen = {
                customBlend?.takeIf { it.songs.isNotEmpty() }?.let { blend ->
                    viewModel.prepareCustomBlend() // so rows played from the page show whose song it is
                    val id = com.theveloper.pixelplay.presentation.components.registerCustomBlendPlaylist(blend)
                    showCustomBlend = false
                    navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
                }
            },
            onDismiss = { showCustomBlend = false }
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
            )
        }
    }
    actionFor?.let { (friendId, track) ->
        val friend = friends.find { it.id == friendId }
        val song = remember(track) { viewModel.songFor(track) }
        FriendTrackSheet(
            track = track,
            friend = friend,
            isLiked = song != null && song.id in favoriteIds,
            onAction = { action ->
                if (action != FriendTrackAction.LIKE) actionFor = null
                if (action == FriendTrackAction.SEARCH || action == FriendTrackAction.ARTIST) historyFor = null
                onTrackAction(friend, track, action)
            },
            onDismiss = { actionFor = null },
        )
    }
    renaming?.let { friend ->
        var draft by remember(friend.id) { mutableStateOf(friend.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename friend") },
            text = {
                OutlinedTextField(draft, { draft = it }, singleLine = true,
                    placeholder = { Text(friend.platformName) }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = draft.isNotBlank(), onClick = {
                    if (draft.trim() != friend.name) viewModel.rename(friend.id, draft.trim())
                    renaming = null
                    selectedIds = emptyList()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
    if (confirmRemove) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (count == 1) "Remove friend?" else "Remove $count friends?") },
            text = { Text("Their saved playlists are removed from your library. Nothing changes on Spotify or other platforms.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFriends(selectedIds.toSet())
                    selectedIds = emptyList()
                    confirmRemove = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
    if (adding) AddFriendPlaylistDialog(onAdd = { name, link, done -> viewModel.addPlaylist(name, link, done) }, onDismiss = { adding = false })
    notice?.let {
        AlertDialog(onDismissRequest = { viewModel.notice.value = null }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { viewModel.notice.value = null }) { Text("OK") } })
    }
}
