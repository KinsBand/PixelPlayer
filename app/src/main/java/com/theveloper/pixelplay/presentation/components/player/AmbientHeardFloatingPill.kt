package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.model.HeardSongItem
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Floating interactive pill displayed above the player when a song suggestion
 * is picked up from conversation. Allows quick Approval (✓ Add to Queue),
 * Play Next, or Dismissal (✗). Auto-dismisses into the Queue list after 6 seconds.
 */
@Composable
fun AmbientHeardFloatingPill(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    var activeItem by remember { mutableStateOf<HeardSongItem?>(null) }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(viewModel) {
        viewModel.latestHeardSongEvent.collectLatest { item ->
            activeItem = item
            delay(6000)
            if (activeItem?.id == item.id) {
                activeItem = null
            }
        }
    }

    AnimatedVisibility(
        visible = activeItem != null,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring()) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = spring()) + fadeOut(),
        modifier = modifier
    ) {
        val item = activeItem ?: return@AnimatedVisibility

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = colors.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading album art thumbnail
                SmartImage(
                    model = item.song.albumArtUriString,
                    contentDescription = null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.width(10.dp))

                // Title, artist, indicators
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hearing,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Heard Suggestion",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (item.mentionCount > 1) {
                            Text(
                                text = "🔥 ${item.mentionCount}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = item.song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (item.song.isFavorite) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = "Liked",
                                tint = colors.error,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        if (item.isOnlineMatch || !item.song.isLocalOrDownloaded) {
                            Icon(
                                imageVector = Icons.Rounded.Cloud,
                                contentDescription = "Online Song",
                                tint = colors.secondary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = item.song.displayArtist,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Action buttons: ✓ Add to queue, Next, ✗ Dismiss
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Approve (✓)
                    FilledTonalIconButton(
                        onClick = {
                            viewModel.approveHeardSong(item)
                            activeItem = null
                        },
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Add to Queue",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Play Next
                    FilledTonalIconButton(
                        onClick = {
                            viewModel.playNextHeardSong(item)
                            activeItem = null
                        },
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Play Next",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Dismiss (✗)
                    IconButton(
                        onClick = {
                            viewModel.dismissHeardSong(item.id)
                            activeItem = null
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
