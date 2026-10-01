package com.theveloper.pixelplay.presentation.components.voicesearch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.model.HeardSongItem
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.recognition.VoiceSearchMode
import com.theveloper.pixelplay.data.recognition.shizuku.ShizukuStatus
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.theveloper.pixelplay.presentation.components.SmartImage

/**
 * The voice sheet, kept minimal: two buttons and, once something is recognised, its match.
 *
 * - **Hum & sing** opens Google's "Search a song".
 * - **Listen** uses Android's Now Playing (its notification is captured into Recently heard).
 *
 * Spoken words never show here: dictation types straight into the search bar above the sheet.
 */
@Composable
fun VoiceSearchSheet(
    activeAction: VoiceSearchMode?,
    recognizedSong: Song?,
    syncedLyrics: Lyrics?,
    onHumOrSing: () -> Unit,
    onListen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onQueueSong: (Song) -> Unit,
    onSeekToLyric: (Int) -> Unit,
    onSearchAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        color = colors.surfaceContainerLow,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle (swipe down anywhere on the sheet to close)
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.outlineVariant)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (recognizedSong != null) {
                RecognizedSongCard(
                    song = recognizedSong,
                    lyrics = syncedLyrics,
                    onToggleFavorite = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleFavorite()
                    },
                    onPlay = { onPlaySong(recognizedSong) },
                    onQueue = { onQueueSong(recognizedSong) },
                    onSeekToLyric = onSeekToLyric,
                    onSearchAgain = onSearchAgain
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VoiceActionButton(
                        label = if (activeAction == VoiceSearchMode.HUM_AND_SING) "Opening Google\u2026" else "Hum & sing",
                        icon = Icons.Rounded.Mic,
                        active = activeAction == VoiceSearchMode.HUM_AND_SING,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onHumOrSing()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    VoiceActionButton(
                        label = if (activeAction == VoiceSearchMode.LISTEN_AND_NOW_PLAYING) "Listening\u2026" else "Listen",
                        icon = Icons.Rounded.GraphicEq,
                        active = activeAction == VoiceSearchMode.LISTEN_AND_NOW_PLAYING,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onListen()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceActionButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (active) colors.primaryContainer else colors.surfaceContainerHigh,
        animationSpec = tween(220),
        label = "voice_action_bg"
    )
    val content by animateColorAsState(
        targetValue = if (active) colors.onPrimaryContainer else colors.onSurface,
        animationSpec = tween(220),
        label = "voice_action_fg"
    )
    // No live level comes back from Google / Now Playing, so the waves pulse on their own.
    val pulse = if (active) {
        rememberInfiniteTransition(label = "voice_action_pulse").animateFloat(
            initialValue = 0.25f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "voice_action_level"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VoiceMicButton(
                onClick = onClick,
                listening = active,
                level = { pulse.value },
                size = 48.dp,
                iconSize = 26.dp,
                icon = icon,
                contentDescription = null
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The Recognized Song Card layout specified:
 * - Cover art to the left (88dp).
 * - Song title on top, Artist name and Genre chip next to it in the middle vertically.
 * - Love / heart / like button on the right side.
 * - Synced lyrics below the music row.
 */
@Composable
private fun RecognizedSongCard(
    song: Song,
    lyrics: Lyrics?,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onSeekToLyric: (Int) -> Unit,
    onSearchAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        // Main Music Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover to the left (88dp)
            SmartImage(
                model = song.albumArtUriString ?: song.contentUriString,
                contentDescription = song.title,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(88.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Middle: vertically centered Song Title and Artist with Genre chip
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    val genreText = song.genre?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                    if (genreText != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.secondaryContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = genreText,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSecondaryContainer,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Love Heart / Like button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (song.isFavorite) colors.error else colors.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Like Song",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Synced Lyrics Below Music Row
        SyncedLyricsSection(
            lyrics = lyrics,
            onSeekToLyric = onSeekToLyric
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPlay,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play")
            }

            OutlinedButton(
                onClick = onQueue,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Queue")
            }

            IconButton(
                onClick = onSearchAgain,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Search Again",
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyncedLyricsSection(
    lyrics: Lyrics?,
    onSeekToLyric: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val syncedLines = lyrics?.synced.orEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerHigh.copy(alpha = 0.6f)
    ) {
        if (syncedLines.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(syncedLines) { line ->
                    Text(
                        text = line.line,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeekToLyric(line.time) }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        } else {
            val plainLines = lyrics?.plain.orEmpty()
            if (plainLines.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    items(plainLines) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Synced lyrics will synchronize while playing",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
