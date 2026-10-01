@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.launch
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

/** Live-listening green, for the Friends screen. */
internal val FriendsLiveGreen: Color get() = SpotifyGreen
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

// ---- Friends card (Library) ------------------------------------------------------------------

/**
 * Full-width Friends card in the Playlists tab. Tapping it opens the Friends screen. Shows who's
 * listening now and who you're following; says nothing when nobody's live.
 */
@Composable
fun FriendsCard(
    navController: NavController?,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val live = friends.count { it.isOnline }
    val subtitle = when {
        friends.isEmpty() -> "Add a friend's playlist to start"
        live == 1 -> "1 listening now"
        live > 1 -> "$live listening now"
        else -> null
    }
    Card(
        onClick = { navController?.navigateSafely(Screen.Friends.route) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Group, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Friends", style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.Bold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                following?.let { session ->
                    Text("Following ${session.friendName}", style = MaterialTheme.typography.labelMedium,
                        color = SpotifyGreen, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ActiveFriendsPill(live, friends.size)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Open friends",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Friends | Practice tiles (Library) -------------------------------------------------------

/**
 * Friends and Practice side by side under Spotify | YT Music, in the same tile style.
 * Both tiles stretch to the taller one so the row stays even.
 */
@Composable
fun FriendsPracticeTiles(
    navController: NavController?,
    practiceSummary: String,
    onOpenPractice: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FriendsTile(
            onClick = { if (enabled) navController?.navigateSafely(Screen.Friends.route) },
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        HubTile(
            label = "Practice",
            subtitle = practiceSummary,
            icon = Icons.Rounded.School,
            onClick = { if (enabled) onOpenPractice() },
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun FriendsTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val live = friends.count { it.isOnline }
    val session = following
    val (subtitle, accent) = when {
        session != null -> "Following ${session.friendName}" to true
        friends.isEmpty() -> "Add a friend" to false
        live == 1 -> "1 listening now" to true
        live > 1 -> "$live listening now" to true
        friends.size == 1 -> "1 friend" to false
        else -> "${friends.size} friends" to false
    }
    HubTile(
        label = "Friends",
        subtitle = subtitle,
        subtitleColor = if (accent) SpotifyGreen else null,
        icon = Icons.Rounded.Group,
        showLiveDot = live > 0,
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Friends, $live of ${friends.size} online"
        }
    )
}

@Composable
private fun HubTile(
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleColor: Color? = null,
    showLiveDot: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(52.dp)) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(28.dp))
                }
                if (showLiveDot) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(2.dp)
                            .clip(CircleShape).background(SpotifyGreen)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (subtitleColor != null) FontWeight.SemiBold else null,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ActiveFriendsPill(live: Int, total: Int) {
    val active = live > 0
    Surface(
        shape = CircleShape,
        color = if (active) SpotifyGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$live of $total friends online" }
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) SpotifyGreen else MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(6.dp))
            Text("$live/$total", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---- Friends Mix -------------------------------------------------------------------------------

/** Full-width button at the top of the Friends screen: plays everything friends played this week. */
@Composable
internal fun FriendsMixButton(
    songCount: Int,
    friends: List<FriendUi>,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !loading && songCount > 0
    val label = when {
        loading -> "Gathering this week…"
        songCount == 0 -> "Nothing played this week yet"
        songCount == 1 -> "1 song this week"
        else -> "$songCount songs this week"
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (enabled) "Play Friends Mix, $songCount songs from this week" else "Friends Mix, $label"
            }
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Up to three friends' pictures, overlapping.
            val shown = friends.take(3)
            if (shown.isNotEmpty()) {
                Box(Modifier.width(28.dp + 16.dp * (shown.size - 1)).height(28.dp)) {
                    shown.forEachIndexed { index, friend ->
                        Box(Modifier.padding(start = 16.dp * index).size(28.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer).padding(2.dp)) {
                            FriendAvatar(friend, size = 24.dp, showPresence = false)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Friends Mix", style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.Bold)
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        (slideInVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) { it / 2 } +
                            fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate))) togetherWith
                            (slideOutVertically(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) { -it / 2 } +
                                fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)))
                    },
                    label = "friendsMixCount"
                ) { text ->
                    Text(text, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 0.8f else 0.6f))
                }
            }
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

// ---- Friend row ----------------------------------------------------------------------------------

/**
 * One friend on the Friends screen. Tap the picture for their history, tap the name for their
 * playlists. Long-press anywhere starts selecting (remove / rename); while selecting, taps toggle.
 */
@Composable
internal fun FriendRow(
    friend: FriendUi,
    selectionMode: Boolean,
    selected: Boolean,
    pinnedPlaylistIds: Set<String>,
    onToggleSelect: () -> Unit,
    onStartSelection: () -> Unit,
    onAvatarClick: () -> Unit,
    onTrackClick: (FriendTrack) -> Unit,
    onExpand: () -> Unit,
    onOpenPlaylist: (FriendPlaylistUi) -> Unit,
    onTogglePin: (FriendPlaylistUi, Boolean) -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Playlists stay hidden until the name is tapped.
    var showPlaylists by rememberSaveable(friend.id) { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        if (showPlaylists) 180f else 0f,
        tween(MotionTokens.DurationMedium1, easing = MotionTokens.Emphasized), label = "friendPlaylistsArrow"
    )
    val rowColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized), label = "friendRowSelected"
    )
    LaunchedEffect(selectionMode) { if (selectionMode) showPlaylists = false }
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(rowColor)
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() },
                onLongClick = { if (selectionMode) onToggleSelect() else onStartSelection() },
                onLongClickLabel = "Select",
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelect() else onAvatarClick() },
                        onLongClick = { if (selectionMode) onToggleSelect() else onStartSelection() },
                        onClickLabel = if (selectionMode) null else "Open ${friend.name}'s history",
                    )
                    .semantics { contentDescription = "Open ${friend.name}'s history" },
                contentAlignment = Alignment.Center
            ) {
                FriendAvatar(friend, size = 40.dp)
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                        fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
                    exit = scaleOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) +
                        fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.widthIn(max = 160.dp).clip(MaterialTheme.shapes.small)
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) onToggleSelect()
                            else {
                                showPlaylists = !showPlaylists
                                if (showPlaylists) onExpand()
                            }
                        },
                        onLongClick = { if (selectionMode) onToggleSelect() else onStartSelection() },
                        onClickLabel = if (showPlaylists) "Hide playlists and history" else "Show playlists and history",
                        onLongClickLabel = "Select"
                    )
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(friend.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            FriendTrackLabel(friend, onTrackClick = { if (selectionMode) onToggleSelect() else onTrackClick(it) }, Modifier.weight(1f))
        }
        // History only shows with the friend's playlists open, at the bottom of that section.
        AnimatedVisibility(
            showPlaylists,
            enter = expandVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) +
                fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
            exit = shrinkVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) +
                fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (friend.playlists.isEmpty()) {
                    Text("No public playlists yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(friend.playlists, key = { it.source + ":" + it.remoteId }) { playlist ->
                            val pinned = playlist.playlistId != null && playlist.playlistId in pinnedPlaylistIds
                            FriendPlaylistCard(
                                playlist = playlist,
                                pinned = pinned,
                                onClick = { onOpenPlaylist(playlist) },
                                onTogglePin = { onTogglePin(playlist, !pinned) },
                            )
                        }
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
internal fun FriendAvatar(friend: FriendUi, size: androidx.compose.ui.unit.Dp = 38.dp, showPresence: Boolean = true) {
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
        // Hidden activity: show how many playlists they have instead (when they have any).
        val count = friend.playlists.size
        if (friend.presence == FriendPresence.HIDDEN && count > 0) {
            Text(if (count == 1) "1 playlist" else "$count playlists", modifier,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        } else {
            Text(if (friend.presence == FriendPresence.HIDDEN) "Activity hidden" else "No recent activity", modifier,
                style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        return
    }
    val live = friend.presence == FriendPresence.LISTENING_NOW
    val song = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" · ")
    Row(modifier.clip(MaterialTheme.shapes.small)
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
internal fun FriendTrackSheet(
    track: FriendTrack,
    friend: FriendUi?,
    isLiked: Boolean,
    onAction: (FriendTrackAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Close with the sheet's own hide animation, then run the action.
    val act: (FriendTrackAction) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { onAction(action) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmartImage(model = track.coverUrl, contentDescription = null, modifier = Modifier.size(64.dp), shape = MaterialTheme.shapes.medium)
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
                // Like doesn't close the sheet, so the heart can be seen changing.
                IconButton(onClick = { onAction(FriendTrackAction.LIKE) }) {
                    Icon(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.Rounded.PlayArrow, "Play", Modifier.weight(1f), primary = true) { act(FriendTrackAction.PLAY) }
                ActionTile(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", Modifier.weight(1f)) { act(FriendTrackAction.NEXT) }
                ActionTile(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", Modifier.weight(1f)) { act(FriendTrackAction.QUEUE) }
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    if (track.artist.isNotBlank()) {
                        FriendActionRow(Icons.Rounded.Person, "Go to ${track.artist}") { act(FriendTrackAction.ARTIST) }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    FriendActionRow(Icons.Rounded.Search, "Search in PixelPlayer") { act(FriendTrackAction.SEARCH) }
                }
            }
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

/**
 * A friend's playlist. Text sits inside the card's padding (the old card clipped the whole
 * column to rounded corners, which cut the first digit of "16 songs"), titles always take two
 * lines so the cards line up, and the platform badge has a solid backing so it shows on any cover.
 * Heart = pin into Your playlists (long-press does the same).
 */
@Composable
private fun FriendPlaylistCard(
    playlist: FriendPlaylistUi,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val songsLabel = if (playlist.songCount <= 0 && playlist.playlistId == null) "Counting songs…"
        else listOfNotNull(if (playlist.songCount == 1) "1 song" else "${playlist.songCount} songs",
            playlist.durationMs?.let(::durationLabel)).joinToString(" · ")
    val platform = when (playlist.source) {
        "SPOTIFY" -> "Spotify"; "YOUTUBE_MUSIC" -> "YouTube Music"; "APPLE_MUSIC" -> "Apple Music"; else -> ""
    }
    Column(
        Modifier
            .width(FriendPlaylistCardWidth)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onTogglePin,
                onLongClickLabel = if (pinned) "Unpin from your playlists" else "Pin to your playlists")
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(playlist.title, songsLabel, platform).filter { it.isNotBlank() }.joinToString(", ")
            }
            .padding(8.dp)
    ) {
        Box {
            SmartImage(model = playlist.coverUrl, contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = MaterialTheme.shapes.medium)
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                SourceBadge(playlist.source, iconSize = 12.dp, containerSize = 22.dp)
            }
            PlaylistPlayingBadge(
                visible = rememberIsPlaylistPlaying(playlist.playlistId),
                isPlaying = true,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                size = 26.dp
            )
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onTogglePin, onClickLabel = if (pinned) "Unpin from your playlists" else "Pin to your playlists"),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = pinned,
                    transitionSpec = {
                        (scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                            fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate))) togetherWith
                            (scaleOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) +
                                fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)))
                    },
                    label = "friendPlaylistPin"
                ) { isPinned ->
                    Icon(
                        if (isPinned) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isPinned) "Pinned" else "Not pinned",
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(songsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (playlist.playlistId == null) {
            Text("Tap to save", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private val FriendPlaylistCardWidth = 148.dp

// ---- History bottom sheet ---------------------------------------------------------------------

/**
 * A friend's last 7 days: who they are and what they're doing now, Shuffle (their whole week)
 * and Follow (their new songs play next as they start them), then every play by day. Tap a
 * song to play it; ⋮ or a long press for more.
 */
@Composable
internal fun FriendHistorySheet(
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
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
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
internal fun AddFriendPlaylistDialog(onAdd: (String, String, () -> Unit) -> Unit, onDismiss: () -> Unit) {
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
