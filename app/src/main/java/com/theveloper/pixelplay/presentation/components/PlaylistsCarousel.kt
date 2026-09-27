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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun PlaylistsCarousel(
    likedSongs: List<Song>,
    likedPlaylistId: String?,
    navController: NavController,
    playlists: List<Playlist> = emptyList(),
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OliveCream,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // LazyRow Carousel
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Card 1: Your Likes
            item(key = "card_your_likes") {
                GridCollageCard(
                    likedSongs = likedSongs,
                    likedPlaylistId = likedPlaylistId,
                    onYourLikesClick = { id ->
                        if (id != null) {
                            navController.navigateSafely(Screen.PlaylistDetail.createRoute(id))
                        }
                    }
                )
            }

            // Real user playlists
            items(
                items = playlists,
                key = { "carousel_${it.id}" }
            ) { playlist ->
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

@Composable
private fun GridCollageCard(
    likedSongs: List<Song>,
    likedPlaylistId: String?,
    onYourLikesClick: (String?) -> Unit
) {
    Box(
        modifier = Modifier
            .size(200.dp, 240.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(OliveDarker)
            .clickable { onYourLikesClick(likedPlaylistId) }
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
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "Your Likes",
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
                    text = "Your Likes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OliveCream
                )
                Text(
                    text = "${likedSongs.size} songs",
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
