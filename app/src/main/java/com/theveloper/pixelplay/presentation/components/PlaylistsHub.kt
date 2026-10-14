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
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.GroupWork
import androidx.compose.material.icons.rounded.Update
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
import androidx.compose.ui.unit.sp
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
private val AppleMusicRed = Color(0xFFFA243C)

// ---- Spotify | YouTube Music -------------------------------------------------------------

/** One connected service on the Library's Playlists tab. */
data class PlatformCardInfo(val source: String, val count: Int)

private data class PlatformLook(val label: String, val iconRes: Int, val brand: Color)

private fun platformLook(source: String): PlatformLook = when (source) {
    "SPOTIFY" -> PlatformLook("Spotify", R.drawable.ic_source_spotify, SpotifyGreen)
    "APPLE_MUSIC" -> PlatformLook("Apple Music", R.drawable.ic_source_apple_music, AppleMusicRed)
    else -> PlatformLook("YT Music", R.drawable.ic_source_youtube_music, YouTubeRed)
}

/**
 * The connected services, adapting to how many there are:
 *  - none: one card with the three logos stacked and a centred Connect button,
 *  - one: a full-width card (logo, name, count) straight above Practice,
 *  - two: 50 / 50, three: a third each.
 * Tapping a service opens its playlists screen.
 */
@Composable
fun PlatformPlaylistCards(
    connected: List<PlatformCardInfo>,
    onOpenPlatform: (source: String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (connected.size) {
        0 -> ConnectServicesCard(onConnect, modifier)
        1 -> {
            val info = connected.single()
            val look = platformLook(info.source)
            PlatformWideCard(look, info.count, { onOpenPlatform(info.source) }, modifier)
        }
        else -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            connected.forEach { info ->
                val look = platformLook(info.source)
                PlatformButton(look.label, look.iconRes, look.brand, info.count,
                    { onOpenPlatform(info.source) }, Modifier.weight(1f))
            }
        }
    }
}

/** Nothing connected: the three logos overlapping, then one Connect button. */
@Composable
private fun ConnectServicesCard(onConnect: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                    listOf("SPOTIFY", "YOUTUBE_MUSIC", "APPLE_MUSIC").forEach { source ->
                        val look = platformLook(source)
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(look.brand),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(painterResource(look.iconRes), contentDescription = look.label, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                androidx.compose.ui.res.stringResource(R.string.library_connect_services_title),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onConnect) {
                Text(androidx.compose.ui.res.stringResource(R.string.library_connect_services_action))
            }
        }
    }
}

/** One service connected: full width, logo on the left, name + count, chevron. */
@Composable
private fun PlatformWideCard(look: PlatformLook, count: Int, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(look.brand.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(look.iconRes), contentDescription = null, tint = look.brand, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(look.label, style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.Bold)
                Text(if (count == 1) "1 playlist" else "$count playlists",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/**
 * Full-width card at the top of the Friends screen. Tapping the card opens Friends Mix as a
 * playlist page (everything friends played this week, filterable by friend); the round play
 * button on the right plays it straight away. While that start is pending the card traces its
 * outline and the button spins; a failed start shakes the card and the arrow comes back.
 */
@Composable
internal fun FriendsMixButton(
    songCount: Int,
    friends: List<FriendUi>,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Friends Mix is the current queue: the play arrow becomes the moving bars. */
    isCurrentQueue: Boolean = false,
    /** Plays the mix (or play / pause when it's already the queue); null = the card only opens it. */
    onPlay: (() -> Unit)? = null,
    /** The song the last play started with, for the starting / failed feedback. */
    startSongId: String? = null,
) {
    val enabled = !loading
    val canPlay = !loading && songCount > 0
    val label = when {
        loading -> "Gathering this week…"
        songCount == 0 -> "Nothing played this week yet"
        songCount == 1 -> "1 song this week"
        else -> "$songCount songs this week"
    }
    val shape = MaterialTheme.shapes.extraLarge
    val phase = rememberSongStartPhase(startSongId)
    val openLabel = androidx.compose.ui.res.stringResource(R.string.friends_mix_open)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .songStartFeedback(startSongId, shape, shakeAmplitude = 6.dp)
            .semantics(mergeDescendants = false) {
                contentDescription = "$openLabel, $label"
            }
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (songCount > 0) 0.8f else 0.6f))
                }
            }
            // 0 = play arrow, 1 = starting spinner, 2 = playing bars.
            val iconState = when {
                phase == com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase.STARTING -> 1
                isCurrentQueue -> 2
                else -> 0
            }
            val playLabel = when {
                isCurrentQueue -> "Play or pause Friends Mix"
                else -> "Play Friends Mix, $songCount songs from this week"
            }
            IconButton(
                onClick = { onPlay?.invoke() },
                enabled = canPlay && onPlay != null,
                modifier = Modifier.size(48.dp).semantics { contentDescription = playLabel }
            ) {
                AnimatedContent(
                    targetState = iconState,
                    transitionSpec = {
                        (fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                            scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.7f)) togetherWith
                            fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
                    },
                    label = "friendsMixPlayIcon"
                ) { state ->
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        when (state) {
                            1 -> SongCoverLoadingIndicator(size = 20.dp, color = MaterialTheme.colorScheme.primary)
                            // Friends Mix playing on this phone: yours, so the theme colour.
                            2 -> PlayingEqIcon(
                                Modifier.size(width = 20.dp, height = 18.dp),
                                color = MaterialTheme.colorScheme.primary,
                                isPlaying = rememberPlayerIsPlaying(),
                                phaseDurationMillis = 2400
                            )
                            else -> Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
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
    /** PixelPlayer song id a friend's track plays as (start / failed feedback); null = unknown. */
    songIdFor: (FriendTrack) -> String? = { null },
    /** The song playing in PixelPlayer now. */
    currentSongId: String? = null,
    /** Pinned to the top of its section: a pin badge on the picture's top-right. */
    pinned: Boolean = false,
    /** "SOURCE:remoteId" of playlists being cached in the background (sync animation on the card). */
    cachingPlaylists: Set<String> = emptySet(),
    /** Whether you follow this friend: followed friends' playlists save themselves (no "Tap to save"). */
    isFriendFollowed: (friendId: String) -> Boolean = { false },
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
    LaunchedEffect(friend.playlists.isEmpty()) { if (friend.playlists.isEmpty()) showPlaylists = false }
    // Long press starts selecting with a haptic tick (not a navigation to history).
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val startSelection = {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        onStartSelection()
    }
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(rowColor)
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() },
                onLongClick = { if (selectionMode) onToggleSelect() else startSelection() },
                onLongClickLabel = "Select",
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val pinnedState = stringResource(R.string.friends_pinned_cd)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          // Outer box isn't clipped, so the pin badge can sit on the picture's edge.
          Box(Modifier.semantics { if (pinned) stateDescription = pinnedState }) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelect() else onAvatarClick() },
                        onLongClick = { if (selectionMode) onToggleSelect() else startSelection() },
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
            androidx.compose.animation.AnimatedVisibility(
                visible = pinned && !selected,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                    fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
                exit = scaleOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) +
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(12.dp))
                }
            }
          }
            Spacer(Modifier.width(8.dp))
            // No public playlists: no arrow and nothing to expand; history stays on the picture.
            val canExpand = friend.playlists.isNotEmpty()
            Row(
                Modifier.widthIn(max = if (friend.isMutual) 184.dp else 160.dp).clip(MaterialTheme.shapes.small)
                    .combinedClickable(
                        enabled = canExpand || selectionMode,
                        onClick = {
                            if (selectionMode) onToggleSelect()
                            else {
                                showPlaylists = !showPlaylists
                                if (showPlaylists) onExpand()
                            }
                        },
                        onLongClick = { if (selectionMode) onToggleSelect() else startSelection() },
                        onClickLabel = if (showPlaylists) "Hide playlists and history" else "Show playlists and history",
                        onLongClickLabel = "Select"
                    )
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(friend.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (friend.isMutual) {
                    Spacer(Modifier.width(6.dp))
                    MutualFriendsBadge(showLabel = false)
                }
                if (canExpand) Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            FriendTrackLabel(
                friend,
                onTrackClick = { if (selectionMode) onToggleSelect() else onTrackClick(it) },
                Modifier.weight(1f),
                songId = friend.track?.let(songIdFor),
                currentSongId = currentSongId,
            )
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
                            // Keys are "SOURCE:remoteId" (a saved playlist's id), so unsaved ones match too.
                            val pinned = (playlist.source + ":" + playlist.remoteId) in pinnedPlaylistIds ||
                                (playlist.playlistId != null && playlist.playlistId in pinnedPlaylistIds)
                            FriendPlaylistCard(
                                playlist = playlist,
                                friendName = friend.name,
                                friendPlatformName = friend.platformName,
                                pinned = pinned,
                                onClick = { onOpenPlaylist(playlist) },
                                onTogglePin = { onTogglePin(playlist, !pinned) },
                                isCaching = (playlist.source + ":" + playlist.remoteId) in cachingPlaylists,
                                autoSync = isFriendFollowed(friend.id),
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
private fun FriendTrackLabel(
    friend: FriendUi,
    onTrackClick: (FriendTrack) -> Unit,
    modifier: Modifier,
    songId: String? = null,
    currentSongId: String? = null,
) {
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
    // Played from its sheet: the label traces while starting, shakes if it fails, and shows the
    // bars once it plays here.
    val phase = rememberSongStartPhase(songId)
    val starting = phase == com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase.STARTING
    val playingHere = !starting && songId != null && songId == currentSongId
    val labelShape = MaterialTheme.shapes.small
    Row(modifier.clip(labelShape)
        .songStartFeedback(songId, labelShape, shakeAmplitude = 6.dp)
        .clickable { onTrackClick(track) }
        .padding(vertical = 2.dp, horizontal = if (phase != null) 4.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = when {
                starting -> 1
                live || playingHere -> 2
                else -> 0
            },
            transitionSpec = {
                (fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                    scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.7f)) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
            },
            label = "friendTrackLead"
        ) { lead ->
            when (lead) {
                1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    SongCoverLoadingIndicator(size = 20.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                }
                2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayingEqIcon(
                        Modifier.size(width = 14.dp, height = 12.dp),
                        color = if (playingHere) MaterialTheme.colorScheme.primary else FriendsLiveGreen,
                        isPlaying = true,
                        phaseDurationMillis = 2400
                    )
                    Spacer(Modifier.width(6.dp))
                }
                else -> Spacer(Modifier.width(0.dp))
            }
        }
        Text(song, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                playingHere -> MaterialTheme.colorScheme.primary
                live -> FriendsLiveGreen
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!live) {
            Text(" · ${agoLabel(track.playedAt)}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

internal enum class FriendTrackAction { PLAY, NEXT, QUEUE, LIKE, ARTIST, ALBUM, SEARCH }

/**
 * Who this song came from, for the song options header: the friend's picture, then
 * "‹name› is listening now" (friends' live green) or "‹name› · 2 h ago". Tap opens the friend.
 */
@Composable
internal fun FriendListeningChip(
    friend: FriendUi,
    track: FriendTrack,
    onClick: (() -> Unit)? = null,
) {
    val live = friend.presence == FriendPresence.LISTENING_NOW && friend.track?.key == track.key
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FriendAvatar(friend, size = 18.dp, showPresence = false)
        Spacer(Modifier.width(6.dp))
        if (live) {
            com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon(
                modifier = Modifier.size(width = 12.dp, height = 10.dp),
                color = FriendsLiveGreen,
                isPlaying = true
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            if (live) "${friend.name} is listening now" else "${friend.name} · ${agoLabel(track.playedAt)}",
            style = MaterialTheme.typography.labelMedium,
            color = if (live) FriendsLiveGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A friend's playlist. Titles always take two lines so cards line up; the platform badge (and a
 * collaborative badge when it is one) sit top-right on the cover, the playing bars bottom-right.
 * Under the title: the grey description, "[♪] 42 · 2h 14m", "[↻] 3d · [♥] 120" and "By curator".
 * Tap opens it; long press pins it into Your playlists (no heart on the card face).
 */
@Composable
internal fun FriendPlaylistCard(
    playlist: FriendPlaylistUi,
    friendName: String,
    friendPlatformName: String,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    /** Being saved in the background right now: spinner over the cover, "Syncing…" line. */
    isCaching: Boolean = false,
    /** A followed friend: unsaved playlists save themselves, so no "Tap to save" prompt. */
    autoSync: Boolean = false,
) {
    val isCounting = playlist.songCount <= 0 && playlist.playlistId == null
    val syncingLabel = androidx.compose.ui.res.stringResource(R.string.friend_playlist_syncing)
    val compactDuration = playlist.durationMs?.let(::durationLabel)
    val countingLabel = androidx.compose.ui.res.stringResource(R.string.compact_count_counting)
    val songsSpoken = androidx.compose.ui.res.pluralStringResource(R.plurals.compact_count_songs, playlist.songCount, playlist.songCount)
    val accessibleSubtitle = if (isCounting) countingLabel else listOfNotNull(songsSpoken, compactDuration).joinToString(", ")
    val platform = when (playlist.source) {
        "SPOTIFY" -> "Spotify"; "YOUTUBE_MUSIC" -> "YouTube Music"; "APPLE_MUSIC" -> "Apple Music"; else -> ""
    }
    // Curator credit only when someone other than the friend made it ("By Spotify").
    val curator = playlist.ownerName?.trim()?.takeIf {
        it.isNotBlank() && !it.equals(friendName, ignoreCase = true) && !it.equals(friendPlatformName, ignoreCase = true)
    }
    val curatorLabel = curator?.let { androidx.compose.ui.res.stringResource(R.string.friend_playlist_by, it) }
    val updated = playlist.lastUpdated?.let { CompactMeta.relativeTime(it) }
    val updatedSpoken = updated?.let { androidx.compose.ui.res.stringResource(R.string.friend_playlist_updated_cd, it) }
    val savesSpoken = playlist.saves.takeIf { it >= 0 }?.let {
        androidx.compose.ui.res.pluralStringResource(R.plurals.friend_playlist_saves, it, it)
    }
    val collaborativeLabel = androidx.compose.ui.res.stringResource(R.string.friend_playlist_collaborative_cd)
    val pinnedState = androidx.compose.ui.res.stringResource(if (pinned) R.string.friend_playlist_pinned else R.string.friend_playlist_not_pinned)
    val pinLabel = androidx.compose.ui.res.stringResource(if (pinned) R.string.friend_playlist_unpin else R.string.friend_playlist_pin)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    CollectionCard(
        title = playlist.title,
        subtitle = accessibleSubtitle,
        coverModel = playlist.coverUrl,
        onClick = onClick,
        description = listOfNotNull(
            playlist.title, playlist.description, accessibleSubtitle, updatedSpoken, savesSpoken, curatorLabel,
            collaborativeLabel.takeIf { playlist.collaborative }, platform.ifBlank { null }, pinnedState
        ).joinToString(", "),
        onLongClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onTogglePin()
        },
        onLongClickLabel = pinLabel,
        extraLine = when {
            isCaching -> syncingLabel
            playlist.playlistId == null && !autoSync -> "Tap to save"
            else -> null
        },
        coverOverlay = {
            // Background caching: a dimmed cover with the app's loading indicator.
            androidx.compose.animation.AnimatedVisibility(
                visible = isCaching,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(tween(MotionTokens.DurationShort4)),
                exit = fadeOut(tween(MotionTokens.DurationShort4)),
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    SongCoverLoadingIndicator(size = 30.dp, color = Color.White)
                }
            }
            // Pinned into Your playlists: a filled heart top-left (long press toggles it).
            androidx.compose.animation.AnimatedVisibility(
                visible = pinned,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                enter = scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                    fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
                exit = scaleOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) +
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
            ) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp))
                }
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (playlist.collaborative) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.GroupWork, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(13.dp))
                    }
                }
                Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    SourceBadge(playlist.source, iconSize = 12.dp, containerSize = 22.dp)
                }
            }
            // Same place and behaviour as every other playlist cover: bottom-right, bars rest when paused.
            PlaylistPlayingBadge(
                visible = rememberIsPlaylistPlaying(playlist.playlistId),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                size = 26.dp
            )
        },
        subtitleContent = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                playlist.description?.let { bio ->
                    Text(bio, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CompactCountBadge(count = playlist.songCount, trailing = compactDuration, isLoading = isCounting)
                if (updated != null || playlist.saves >= 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
                        if (updated != null) {
                            Icon(Icons.Rounded.Update, null, Modifier.size(13.dp), tint = metaColor)
                            Spacer(Modifier.width(3.dp))
                            Text(updated, style = MaterialTheme.typography.labelMedium, color = metaColor, maxLines = 1)
                        }
                        if (updated != null && playlist.saves >= 0) {
                            Text(CompactMeta.SEPARATOR, style = MaterialTheme.typography.labelMedium, color = metaColor)
                        }
                        if (playlist.saves >= 0) {
                            Icon(Icons.Rounded.Favorite, null, Modifier.size(12.dp), tint = metaColor)
                            Spacer(Modifier.width(3.dp))
                            Text(playlist.saves.toString(), style = MaterialTheme.typography.labelMedium, color = metaColor, maxLines = 1)
                        }
                    }
                }
                curatorLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    )
}

private val FriendPlaylistCardWidth = 148.dp

// ---- History bottom sheet: see FriendHistorySheet.kt ----------------------------------------

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

/** Compact age for friend activity: 5m, 6H, 3D, 2M (months), 1Y. */
internal fun agoLabel(at: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - at) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}H"
        else -> {
            val days = minutes / (24 * 60)
            when {
                days < 30 -> "${days}D"
                days < 365 -> "${days / 30}M"
                else -> "${days / 365}Y"
            }
        }
    }
}

private fun durationLabel(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

internal fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMM"))
    }
}
