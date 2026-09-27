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

@Composable
fun VoiceSearchSheet(
    currentMode: VoiceSearchMode,
    isListening: Boolean,
    recognizedSong: Song?,
    syncedLyrics: Lyrics?,
    shizukuStatus: ShizukuStatus,
    nowPlayingHistory: List<HeardSongItem>,
    onSwitchMode: (VoiceSearchMode) -> Unit,
    onTriggerHumOrSing: () -> Unit,
    onTriggerSoundSearch: () -> Unit,
    onRequestShizuku: () -> Unit,
    onSelectSong: (Song) -> Unit,
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

            Spacer(modifier = Modifier.height(8.dp))

            // Two-Mode Segmented Control
            VoiceSearchSegmentedBar(
                currentMode = currentMode,
                onModeSelected = { mode ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSwitchMode(mode)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Shizuku Privilege Banner / Badge
            ShizukuStatusBadge(
                status = shizukuStatus,
                onRequestShizuku = onRequestShizuku
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (recognizedSong != null) {
                // The Recognized Song Card layout
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
                // Listening or Ready State depending on active mode
                when (currentMode) {
                    VoiceSearchMode.HUM_AND_SING -> {
                        HumAndSingContent(
                            isListening = isListening,
                            onTriggerSearch = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTriggerHumOrSing()
                            }
                        )
                    }
                    VoiceSearchMode.LISTEN_AND_NOW_PLAYING -> {
                        ListenAndNowPlayingContent(
                            isListening = isListening,
                            nowPlayingHistory = nowPlayingHistory,
                            onTriggerListen = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTriggerSoundSearch()
                            },
                            onSelectSong = { song ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectSong(song)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSearchSegmentedBar(
    currentMode: VoiceSearchMode,
    onModeSelected: (VoiceSearchMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modes = listOf(
                VoiceSearchMode.HUM_AND_SING to "🎤 Hum & Sing",
                VoiceSearchMode.LISTEN_AND_NOW_PLAYING to "🎶 Listen & Now Playing"
            )

            for ((mode, label) in modes) {
                val isSelected = currentMode == mode
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) colors.primary else Color.Transparent,
                    animationSpec = tween(220),
                    label = "segmentBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                    animationSpec = tween(220),
                    label = "segmentText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(bgColor)
                        .clickable { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ShizukuStatusBadge(
    status: ShizukuStatus,
    onRequestShizuku: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    when (status) {
        ShizukuStatus.READY -> {
            Row(
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Shizuku Privileged Mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        ShizukuStatus.NEEDS_PERMISSION -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colors.tertiaryContainer.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Authorize Shizuku",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onTertiaryContainer
                        )
                        Text(
                            text = "Unlocks Google Sound Search & Pixel Now Playing",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    FilledTonalButton(
                        onClick = onRequestShizuku,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.tertiary,
                            contentColor = colors.onTertiary
                        )
                    ) {
                        Text("Grant", fontSize = 12.sp)
                    }
                }
            }
        }
        ShizukuStatus.UNAVAILABLE -> Unit
    }
}

@Composable
private fun HumAndSingContent(
    isListening: Boolean,
    onTriggerSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by if (isListening) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(colors.primaryContainer)
                .clickable { onTriggerSearch() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = "Hum or Sing",
                tint = colors.primary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (isListening) "Listening with Google..." else "Tap to Hum or Sing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Hum a melody or sing lyrics to find any song with Google",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun ListenAndNowPlayingContent(
    isListening: Boolean,
    nowPlayingHistory: List<HeardSongItem>,
    onTriggerListen: () -> Unit,
    onSelectSong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        // Quick Listen Button Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.primaryContainer.copy(alpha = 0.4f))
                .clickable { onTriggerListen() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isListening) "Identifying Room Music..." else "Search Music Playing Nearby",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = "Launch Google Sound Search to listen",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pixel Now Playing History Section
        Text(
            text = "Already heard",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        if (nowPlayingHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing heard yet.\nSongs you've already heard will show up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(nowPlayingHistory, key = { it.id }) { item ->
                    NowPlayingHistoryRow(
                        item = item,
                        onClick = { onSelectSong(item.song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingHistoryRow(
    item: HeardSongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = colors.surfaceContainerHigh.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.song.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Rounded.Hearing,
                contentDescription = null,
                tint = colors.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
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
