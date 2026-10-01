package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely

/** A friend shown as a filter next to the Playlists title: tapping them shows only their playlists. */
@androidx.compose.runtime.Immutable
data class PlaylistFriendFilter(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val playlistIds: Set<String>,
)

@Composable
fun PlaylistsCarousel(
    likedSongs: List<Song>,
    likedPlaylistId: String?,
    navController: NavController,
    playlists: List<Playlist> = emptyList(),
    modifier: Modifier = Modifier,
    friendFilters: List<PlaylistFriendFilter> = emptyList(),
) {
    val listState = rememberLazyListState()
    // Only friends who actually have a playlist in this row get a filter.
    val shownIds = remember(playlists) { playlists.mapTo(HashSet()) { it.id } }
    val filters = remember(friendFilters, shownIds) { friendFilters.filter { f -> f.playlistIds.any { it in shownIds } } }
    var selectedFriendId by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val selected = filters.firstOrNull { it.id == selectedFriendId }
    val visiblePlaylists = remember(playlists, selected) {
        if (selected == null) playlists else playlists.filter { it.id in selected.playlistIds }
    }
    // Back to the start of the row whenever the filter changes.
    androidx.compose.runtime.LaunchedEffect(selected?.id) { listState.animateScrollToItem(0) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section header: title, then the friends' pictures as filters.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OliveCream,
            )
            if (filters.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    items(filters, key = { "friend_filter_" + it.id }) { friend ->
                        FriendFilterAvatar(
                            friend = friend,
                            selected = friend.id == selectedFriendId,
                            dimmed = selectedFriendId != null && friend.id != selectedFriendId,
                            onClick = { selectedFriendId = if (selectedFriendId == friend.id) null else friend.id },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // LazyRow Carousel
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Card 1: Your Music (every song and every like); hidden while a friend filter is on.
            if (selected == null) {
                item(key = "card_your_music") {
                    Box(Modifier.animateItem()) {
                        GridCollageCard(
                            likedSongs = likedSongs,
                            onClick = { navController.navigateSafely(Screen.YourMusic.route) }
                        )
                    }
                }
            }

            // Real user playlists (only the picked friend's while filtered)
            items(
                items = visiblePlaylists,
                key = { "carousel_${it.id}" }
            ) { playlist ->
                Box(Modifier.animateItem()) {
                    PlaylistCarouselCard(
                        playlist = playlist,
                        onClick = {
                            navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendFilterAvatar(
    friend: PlaylistFriendFilter,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(
            com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4,
            easing = com.theveloper.pixelplay.ui.theme.MotionTokens.Emphasized
        ),
        label = "friendFilterRing"
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dimmed) 0.45f else 1f,
        animationSpec = androidx.compose.animation.core.tween(
            com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4,
            easing = com.theveloper.pixelplay.ui.theme.MotionTokens.Emphasized
        ),
        label = "friendFilterAlpha"
    )
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                onClickLabel = if (selected) "Show all playlists" else "Show ${friend.name}'s playlists",
                onClick = onClick
            )
            .border(2.dp, ring, CircleShape)
            .padding(3.dp)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        if (friend.avatarUrl != null) {
            SmartImage(model = friend.avatarUrl, contentDescription = friend.name, modifier = Modifier.fillMaxSize(), shape = CircleShape)
        } else {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(friend.name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun GridCollageCard(
    likedSongs: List<Song>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(200.dp, 240.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(OliveDarker)
            .clickable(onClickLabel = "Open Your Music", onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 3x3 Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    for (row in 0..2) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            for (col in 0..2) {
                                val index = row * 3 + col
                                val song = likedSongs.getOrNull(index)
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    if (song != null) {
                                        SmartImage(
                                            model = song.albumArtUriString,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RectangleShape
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF171B0F)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = OliveCream.copy(alpha = 0.1f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Semi-transparent circular overlay in the center with a cream heart icon
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = "Your Music",
                        tint = OliveCream,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Text info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF252B1B))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Your Music",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OliveCream
                )
                Text(
                    text = if (likedSongs.size == 1) "1 liked song" else "${likedSongs.size} liked songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = OliveCream.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun PlaylistCarouselCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(200.dp, 240.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(OliveDarker)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Color(0xFF171B0F))
            ) {
                if (playlist.coverImageUri != null) {
                    SmartImage(
                        model = playlist.coverImageUri,
                        contentDescription = playlist.name,
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                playlist.coverColorArgb?.let { Color(it) } ?: Color(0xFF1F2414)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = OliveCream.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                if (playlist.source.equals("SPOTIFY", ignoreCase = true) || playlist.source.equals("YOUTUBE_MUSIC", ignoreCase = true)) {
                    SourceBadge(
                        source = playlist.source,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
            }

            // Text info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF252B1B))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OliveCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songIds.size} songs" + if (playlist.ownerName.isNotBlank()) " · ${playlist.ownerName}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = OliveCream.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
