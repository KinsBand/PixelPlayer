package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

enum class TrackListMode {
    Recent, Liked
}

val PillColors = listOf(
    Color(0xFF2D3C4D), // Dark Blue
    Color(0xFF4D332D), // Dark Terracotta
    Color(0xFF4D432D), // Dark Amber
    Color(0xFF333E3E), // Dark Slate
    Color(0xFF412D4D), // Dark Purple
    Color(0xFF2D4D36)  // Dark Forest
)

@Composable
fun RecentTracksList(
    recentSongs: List<Song>,
    likedSongs: List<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int = 3
) {
    var mode by rememberSaveable { mutableStateOf(TrackListMode.Recent) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val displaySongs = remember(mode, recentSongs, likedSongs, maxItems) {
        if (mode == TrackListMode.Recent) recentSongs.take(maxItems) else likedSongs.take(maxItems)
    }

    val headerShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 0.dp,
            smoothnessAsPercentTL = 0,
            cornerRadiusTR = 34.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 0.dp,
            smoothnessAsPercentBL = 0,
            cornerRadiusBR = 0.dp,
            smoothnessAsPercentBR = 0
        )
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Toggle Pills Header (Yellow full-width block flush with top-right)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(headerShape)
                .background(OliveAccentYellow)
                .clickable { expanded = !expanded }
        ) {
            if (!expanded) {
                // Collapsed: single tab text centered
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (mode == TrackListMode.Recent) "Recent" else "Liked",
                        style = MaterialTheme.typography.titleMedium,
                        color = OliveDarker,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Expand Mode Selector",
                        tint = OliveDarker,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // Expanded: side-by-side tabs in the same yellow container
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Recent Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (mode == TrackListMode.Recent) Color.Black.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable {
                                mode = TrackListMode.Recent
                                expanded = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Recent",
                            style = MaterialTheme.typography.titleMedium,
                            color = OliveDarker,
                            fontWeight = if (mode == TrackListMode.Recent) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // Divider line
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.6f)
                            .background(OliveDarker.copy(alpha = 0.2f))
                    )

                    // Liked Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (mode == TrackListMode.Liked) Color.Black.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable {
                                mode = TrackListMode.Liked
                                expanded = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Liked",
                            style = MaterialTheme.typography.titleMedium,
                            color = OliveDarker,
                            fontWeight = if (mode == TrackListMode.Liked) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // List Area with Padding for content items (so they stay inset)
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                        slideInVertically(animationSpec = tween(220, delayMillis = 90), initialOffsetY = { it / 2 }))
                    .togetherWith(fadeOut(animationSpec = tween(90)) +
                            slideOutVertically(animationSpec = tween(90), targetOffsetY = { it / 2 }))
            },
            label = "TrackListModeSwitch",
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) { targetMode ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (displaySongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (targetMode == TrackListMode.Recent) "No recent tracks" else "No liked tracks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OliveCream.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    displaySongs.forEachIndexed { index, song ->
                        RecentTrackPillItem(
                            song = song,
                            bgColor = PillColors[index % PillColors.size],
                            isCurrentSong = song.id == currentSongId,
                            isPlaying = isPlaying,
                            onClick = { onSongClick(song) },
                            onMoreClick = { onSongMoreClick(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTrackPillItem(
    song: Song,
    bgColor: Color,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isCurrentSong) OliveAccentYellow.copy(alpha = 0.25f) else bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail image
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = null,
            shape = CircleShape,
            modifier = Modifier.size(30.dp)
        )
        
        Spacer(Modifier.width(8.dp))

        // Title and Artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCurrentSong) OliveAccentYellow else OliveCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = (if (isCurrentSong) OliveAccentYellow else OliveCream).copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // More actions button
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More actions",
                tint = (if (isCurrentSong) OliveAccentYellow else OliveCream).copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
