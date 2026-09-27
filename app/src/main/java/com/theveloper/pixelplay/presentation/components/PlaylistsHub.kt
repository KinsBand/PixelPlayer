@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.social.FriendPresence
import com.theveloper.pixelplay.data.social.FriendTrack
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely
import com.theveloper.pixelplay.presentation.viewmodel.FriendPlaylistUi
import com.theveloper.pixelplay.presentation.viewmodel.FriendUi
import com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SpotifyGreen = Color(0xFF1DB954)
private val YouTubeRed = Color(0xFFFF0000)

// ---- Spotify | YouTube Music -------------------------------------------------------------

/**
 * Two side-by-side buttons: Spotify (left) and YouTube Music (right), each showing only its logo,
 * name and playlist count. Tapping one opens that service's playlists screen.
 */
@Composable
fun PlatformPlaylistCards(
    spotifyCount: Int,
    youtubeCount: Int,
    onOpenPlatform: (source: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlatformButton("Spotify", R.drawable.ic_source_spotify, SpotifyGreen, spotifyCount,
            { onOpenPlatform("SPOTIFY") }, Modifier.weight(1f))
        PlatformButton("YT Music", R.drawable.ic_source_youtube_music, YouTubeRed, youtubeCount,
            { onOpenPlatform("YOUTUBE_MUSIC") }, Modifier.weight(1f))
    }
}

@Composable
private fun PlatformButton(
    label: String, iconRes: Int, brand: Color, count: Int,
    onClick: () -> Unit, modifier: Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(brand.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(iconRes), contentDescription = label, tint = brand, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.Bold)
            Text(if (count == 1) "1 playlist" else "$count playlists",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Practice ---------------------------------------------------------------------------------

/** Full-width button under Friends that opens the Practice screen (want / learning / finished). */
@Composable
fun PracticeButton(summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.School, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Practice", style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.Bold)
                Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Friends dropdown ------------------------------------------------------------------------

/** Full-width Friends card; the whole card toggles the dropdown. */
@Composable
fun FriendsDropdownCard(
    navController: NavController?,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    // The song whose action sheet is open, with the friend who played it.
    var actionFor by remember { mutableStateOf<Pair<String, FriendTrack>?>(null) }
    var historyFor by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    // Poll friend activity only while the list or a history sheet is on screen (and the app is started).
    if (expanded || historyFor != null) viewModel.polling.collectAsStateWithLifecycle(Unit)

    val live = friends.count { it.presence == FriendPresence.LISTENING_NOW }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "friendsArrow")
    val search: (FriendTrack) -> Unit = { track ->
        playerViewModel.updateSearchQuery(listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" "))
        navController?.navigateToTopLevelSafely(Screen.Search.route)
    }
    // Actions on a friend's song; search only when it can't be played.
    val onTrackAction: (FriendTrack, FriendTrackAction) -> Unit = { track, action ->
        val song = viewModel.songFor(track)
        when {
            action == FriendTrackAction.ARTIST -> {
                track.artist.takeIf { it.isNotBlank() }?.let { navController?.navigateSafely(Screen.ArtistDetail.createRouteForName(it)) }
            }
            song == null || action == FriendTrackAction.SEARCH -> search(track)
            action == FriendTrackAction.PLAY -> playerViewModel.playSongs(listOf(song), song, "Friends")
            action == FriendTrackAction.NEXT -> playerViewModel.addSongNextToQueue(song)
            action == FriendTrackAction.QUEUE -> playerViewModel.addSongToQueue(song)
            action == FriendTrackAction.LIKE -> playerViewModel.toggleFavoriteSpecificSong(song, removing = song.id in favoriteIds)
        }
    }
    val canPlay: (FriendTrack) -> Boolean = { viewModel.songFor(it) != null }
    val openTrack: (FriendUi, FriendTrack) -> Unit = { friend, track ->
        if (canPlay(track)) {
            actionFor = friend.id to track
        } else {
            search(track)
        }
    }

    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.animateContentSize()) {
            // The header is the whole card while collapsed, so the full card toggles it.
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Group, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Friends", style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.Bold)
                    Text(when {
                        friends.isEmpty() -> "Add a friend's playlist to start"
                        live == 1 -> "1 listening now"
                        live > 1 -> "$live listening now"
                        else -> "No one's listening right now"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    following?.let { session ->
                        Text("Following ${session.friendName}", style = MaterialTheme.typography.labelMedium,
                            color = SpotifyGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
                ActiveFriendsPill(live, friends.size)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, if (expanded) "Collapse friends" else "Expand friends", Modifier.rotate(arrow))
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!viewModel.hasLiveSource) {
                        Text("Live listening and history appear once Spotify friend activity is connected.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    friends.forEachIndexed { index, friend ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        FriendSection(
                            friend = friend,
                            onRename = { viewModel.rename(friend.id, it) },
                            onTrackClick = { track -> openTrack(friend, track) },
                            onExpand = { viewModel.gatherPlaylists(friend) },
                            onOpenPlaylist = { playlist ->
                                val id = playlist.playlistId
                                if (id != null) navController?.navigateSafely(Screen.PlaylistDetail.createRoute(id))
                                else viewModel.savePublicPlaylist(friend, playlist) { saved -> navController?.navigateSafely(Screen.PlaylistDetail.createRoute(saved)) }
                            },
                            onHistory = { historyFor = friend.id },
                        )
                    }
                    OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add friend's playlist") }
                }
            }
        }
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
                onPlay = { track -> onTrackAction(track, FriendTrackAction.PLAY) },
                onMore = { track -> openTrack(friend, track) },
                onFollow = { viewModel.toggleFollow(friend) },
                onShuffle = {
                    val songs = history.mapNotNull(viewModel::songFor).distinctBy { it.id }.shuffled()
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
                actionFor = null
                if (action == FriendTrackAction.SEARCH || action == FriendTrackAction.ARTIST) historyFor = null
                onTrackAction(track, action)
            },
            onDismiss = { actionFor = null },
        )
    }
    if (adding) AddFriendPlaylistDialog(onAdd = { name, link, done -> viewModel.addPlaylist(name, link, done) }, onDismiss = { adding = false })
    notice?.let {
        AlertDialog(onDismissRequest = { viewModel.notice.value = null }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { viewModel.notice.value = null }) { Text("OK") } })
    }
}

@Composable
private fun ActiveFriendsPill(live: Int, total: Int) {
    val active = live > 0
    Surface(shape = CircleShape, color = if (active) SpotifyGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) SpotifyGreen else MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(6.dp))
            Text("$live/$total", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FriendSection(
    friend: FriendUi,
    onRename: (String) -> Unit,
    onTrackClick: (FriendTrack) -> Unit,
    onExpand: () -> Unit,
    onOpenPlaylist: (FriendPlaylistUi) -> Unit,
    onHistory: () -> Unit,
) {
    var editing by remember(friend.id) { mutableStateOf(false) }
    // Playlists stay hidden until the name is tapped (long-press the name to rename).
    var showPlaylists by rememberSaveable(friend.id) { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (showPlaylists) 180f else 0f, label = "friendPlaylistsArrow")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FriendAvatar(friend)
            Spacer(Modifier.width(10.dp))
            if (editing) {
                var draft by remember(friend.id) { mutableStateOf(friend.name) }
                val save = { if (draft.isNotBlank() && draft.trim() != friend.name) onRename(draft.trim()); editing = false }
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall, placeholder = { Text(friend.platformName) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }))
                IconButton(onClick = save) { Icon(Icons.Rounded.Check, "Save name") }
                IconButton(onClick = { editing = false }) { Icon(Icons.Rounded.Close, "Cancel") }
            } else {
                Row(
                    Modifier.widthIn(max = 160.dp).clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = {
                                showPlaylists = !showPlaylists
                                if (showPlaylists) onExpand()
                            },
                            onLongClick = { editing = true },
                            onClickLabel = if (showPlaylists) "Hide playlists and history" else "Show playlists and history",
                            onLongClickLabel = "Rename"
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(friend.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                FriendTrackLabel(friend, onTrackClick, Modifier.weight(1f))
            }
        }
        // History only shows with the friend's playlists open, at the bottom of that section.
        AnimatedVisibility(showPlaylists, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (friend.playlists.isEmpty()) {
                    Text("No public playlists yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(friend.playlists, key = { it.source + ":" + it.remoteId }) { playlist -> FriendPlaylistCard(playlist) { onOpenPlaylist(playlist) } }
                    }
                }
                FilledTonalButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.History, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("History")
                }
            }
        }
    }
}

@Composable
private fun FriendAvatar(friend: FriendUi, size: androidx.compose.ui.unit.Dp = 38.dp, showPresence: Boolean = true) {
    Box(Modifier.size(size)) {
        if (friend.avatarUrl != null) {
            SmartImage(model = friend.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), shape = CircleShape)
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Text(friend.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        val dot = if (!showPresence) null else when (friend.presence) {
            FriendPresence.LISTENING_NOW -> SpotifyGreen
            FriendPresence.RECENT -> MaterialTheme.colorScheme.tertiary
            else -> null
        }
        if (dot != null) Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(2.dp).clip(CircleShape).background(dot))
    }
}

@Composable
private fun FriendTrackLabel(friend: FriendUi, onTrackClick: (FriendTrack) -> Unit, modifier: Modifier) {
    val track = friend.track
    if (track == null) {
        Text(if (friend.presence == FriendPresence.HIDDEN) "Activity hidden" else "No recent activity", modifier,
            style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        return
    }
    val live = friend.presence == FriendPresence.LISTENING_NOW
    val song = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" · ")
    Row(modifier.clip(RoundedCornerShape(6.dp))
        .clickable { onTrackClick(track) }
        .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (live) {
            PlayingEqIcon(Modifier.size(width = 14.dp, height = 12.dp), color = SpotifyGreen, isPlaying = true, phaseDurationMillis = 2400)
            Spacer(Modifier.width(6.dp))
        }
        Text(song, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
            color = if (live) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!live) {
            Text(" · ${agoLabel(track.playedAt)}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

internal enum class FriendTrackAction { PLAY, NEXT, QUEUE, LIKE, ARTIST, SEARCH }

/**
 * Actions for one of a friend's songs, in the app's own sheet style: the song and who played
 * it on top, Play / Play next / Add to queue as big tiles, then like, artist and search.
 */
@Composable
private fun FriendTrackSheet(
    track: FriendTrack,
    friend: FriendUi?,
    isLiked: Boolean,
    onAction: (FriendTrackAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmartImage(model = track.coverUrl, contentDescription = null, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (track.artist.isNotBlank()) {
                        Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (friend != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FriendAvatar(friend, size = 18.dp, showPresence = false)
                            Spacer(Modifier.width(6.dp))
                            val live = friend.presence == FriendPresence.LISTENING_NOW && friend.track?.key == track.key
                            Text(if (live) "${friend.name} is listening now" else "${friend.name} · ${agoLabel(track.playedAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (live) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                IconButton(onClick = { onAction(FriendTrackAction.LIKE) }) {
                    Icon(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FriendActionTile(Icons.Rounded.PlayArrow, "Play", Modifier.weight(1f), primary = true) { onAction(FriendTrackAction.PLAY) }
                FriendActionTile(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", Modifier.weight(1f)) { onAction(FriendTrackAction.NEXT) }
                FriendActionTile(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", Modifier.weight(1f)) { onAction(FriendTrackAction.QUEUE) }
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    if (track.artist.isNotBlank()) {
                        FriendActionRow(Icons.Rounded.Person, "Go to ${track.artist}") { onAction(FriendTrackAction.ARTIST) }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    FriendActionRow(Icons.Rounded.Search, "Search in PixelPlayer") { onAction(FriendTrackAction.SEARCH) }
                }
            }
        }
    }
}

@Composable
private fun FriendActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier,
    primary: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(76.dp), shape = RoundedCornerShape(20.dp),
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun FriendActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FriendPlaylistCard(playlist: FriendPlaylistUi, onClick: () -> Unit) {
    Column(Modifier.width(128.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box {
            SmartImage(model = playlist.coverUrl, contentDescription = null, modifier = Modifier.size(128.dp), shape = RoundedCornerShape(16.dp))
            SourceBadge(playlist.source, Modifier.align(Alignment.TopEnd).padding(6.dp), iconSize = 12.dp, containerSize = 22.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(playlist.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (playlist.songCount <= 0 && playlist.playlistId == null) "Counting songs…"
            else listOfNotNull("${playlist.songCount} songs", playlist.durationMs?.let(::durationLabel)).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (playlist.playlistId == null) {
            Text("Tap to save", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---- History bottom sheet ---------------------------------------------------------------------

/**
 * A friend's last 7 days: who they are and what they're doing now, Shuffle (their whole week)
 * and Follow (their new songs play next as they start them), then every play by day. Tap a
 * song to play it; ⋮ or a long press for more.
 */
@Composable
private fun FriendHistorySheet(
    friend: FriendUi,
    history: List<FriendTrack>,
    isFollowing: Boolean,
    canPlay: (FriendTrack) -> Boolean,
    onPlay: (FriendTrack) -> Unit,
    onMore: (FriendTrack) -> Unit,
    onFollow: () -> Unit,
    onShuffle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val days = remember(history) {
        history.sortedByDescending { it.playedAt }.groupBy { Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate() }
    }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val live = friend.presence == FriendPresence.LISTENING_NOW
    val newest = remember(history) { history.maxByOrNull { it.playedAt } }
    val playableCount = remember(history) { history.count(canPlay) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(friend, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(friend.name, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val status = when {
                        live && friend.track != null -> "Listening to ${friend.track.title}"
                        newest != null -> "Last played ${agoLabel(newest.playedAt)}"
                        else -> "No plays seen yet"
                    }
                    Text(status, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (live) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal)
                    Text("${history.size} ${if (history.size == 1) "play" else "plays"} this week",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onShuffle, enabled = playableCount > 0, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
                val followColors = if (isFollowing) ButtonDefaults.filledTonalButtonColors(
                    containerColor = SpotifyGreen.copy(alpha = 0.18f), contentColor = MaterialTheme.colorScheme.onSurface)
                else ButtonDefaults.filledTonalButtonColors()
                FilledTonalButton(onClick = onFollow, colors = followColors, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(if (isFollowing) Icons.Rounded.Check else Icons.Rounded.PersonAdd, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isFollowing) "Following" else "Follow")
                }
            }
            AnimatedVisibility(isFollowing) {
                Text("Songs ${friend.name} starts will play next.",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (history.isEmpty()) {
            Text("Nothing yet. This fills in as PixelPlayer sees ${friend.name} play songs.",
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
            return@ModalBottomSheet
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            days.forEach { (day, tracks) ->
                stickyHeader(key = "day:$day") {
                    Surface(color = BottomSheetDefaults.ContainerColor, modifier = Modifier.fillMaxWidth()) {
                        Text(dayLabel(day), Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(tracks, key = { "${it.playedAt}:${it.key}" }) { track ->
                    val isNow = live && track == newest
                    val playable = canPlay(track)
                    Row(Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = { if (playable) onPlay(track) else onMore(track) },
                            onLongClick = { onMore(track) },
                        )
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            SmartImage(model = track.coverUrl, contentDescription = null, modifier = Modifier.size(56.dp), shape = RoundedCornerShape(12.dp))
                            if (isNow) {
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                                    PlayingEqIcon(Modifier.size(width = 18.dp, height = 16.dp), color = SpotifyGreen, isPlaying = true, phaseDurationMillis = 2400)
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (isNow) SpotifyGreen else MaterialTheme.colorScheme.onSurface)
                            val time = if (isNow) "Now" else Instant.ofEpochMilli(track.playedAt).atZone(zone).toLocalTime().format(timeFormat)
                            Text(listOf(track.artist, time).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onMore(track) }) {
                            Icon(Icons.Rounded.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFriendPlaylistDialog(onAdd: (String, String, () -> Unit) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Add friend's playlist") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Friend's name") }, singleLine = true)
            OutlinedTextField(link, { link = it }, label = { Text("Public playlist link") }, singleLine = true)
            Text("Use the same name to group another playlist with this friend.", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        TextButton(enabled = !busy && name.isNotBlank() && link.isNotBlank(), onClick = { busy = true; onAdd(name, link) { onDismiss() } }) { Text("Add") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}

// ---- Formatting --------------------------------------------------------------------------------

private fun agoLabel(at: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - at) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> (minutes / 60).let { if (it == 1L) "1 hr ago" else "$it hrs ago" }
        else -> (minutes / (24 * 60)).let { if (it == 1L) "1 day ago" else "$it days ago" }
    }
}

private fun durationLabel(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours hr"
        else -> "$hours hr $minutes min"
    }
}

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMM"))
    }
}
