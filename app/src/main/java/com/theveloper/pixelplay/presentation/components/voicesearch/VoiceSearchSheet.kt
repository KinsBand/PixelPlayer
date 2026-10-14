package com.theveloper.pixelplay.presentation.components.voicesearch

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.HeardSongItem
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.recognition.VoiceSearchMode
import com.theveloper.pixelplay.data.recognition.livesync.LiveLyricsState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import com.theveloper.pixelplay.data.recognition.shizuku.ShizukuStatus
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.theveloper.pixelplay.presentation.components.SmartImage

/**
 * The voice sheet, kept minimal: one "Identify a song" button (Google's "Search a song", which
 * takes humming, singing or music playing nearby) and, once something is recognised, its match.
 *
 * Spoken words never show here: dictation types straight into the search bar above the sheet.
 */
@Composable
fun VoiceSearchSheet(
    activeAction: VoiceSearchMode?,
    recognizedSong: Song?,
    syncedLyrics: Lyrics?,
    /** Lyrics following the music playing nearby (see AmbientLyricsSync). */
    liveLyrics: LiveLyricsState = LiveLyricsState.Idle,
    onHumOrSing: () -> Unit,
    onListen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onQueueSong: (Song) -> Unit,
    onSeekToLyric: (Int) -> Unit,
    onSearchAgain: () -> Unit,
    modifier: Modifier = Modifier,
    /** True when the song search reader should be explained first (once, without Shizuku). */
    shouldExplainResultReader: () -> Boolean = { false },
    onResultReaderExplained: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    // The button tapped while the one-time explainer is showing ("Not now" still runs it).
    var explainBefore by remember { mutableStateOf<VoiceSearchMode?>(null) }
    val runAction: (VoiceSearchMode) -> Unit = { mode ->
        if (mode == VoiceSearchMode.HUM_AND_SING) onHumOrSing() else onListen()
    }
    val requestAction: (VoiceSearchMode) -> Unit = { mode ->
        if (shouldExplainResultReader()) {
            onResultReaderExplained()
            explainBefore = mode
        } else {
            runAction(mode)
        }
    }

    explainBefore?.let { pendingMode ->
        AlertDialog(
            onDismissRequest = { explainBefore = null },
            icon = { Icon(Icons.Rounded.Hearing, contentDescription = null) },
            title = { Text(stringResource(R.string.song_search_reader_dialog_title)) },
            text = { Text(stringResource(R.string.song_search_reader_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    explainBefore = null
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) {
                    Text(stringResource(R.string.song_search_reader_dialog_turn_on))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    explainBefore = null
                    runAction(pendingMode)
                }) {
                    Text(stringResource(R.string.song_search_reader_dialog_not_now))
                }
            }
        )
    }

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
                    liveLyrics = liveLyrics,
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
                // One button: Google's "Search a song" handles humming, singing and music
                // playing nearby all in one, so there's no separate Hum / Listen choice.
                VoiceActionButton(
                    label = if (activeAction != null) "Opening Google\u2026" else "Identify a song",
                    icon = Icons.Rounded.Mic,
                    active = activeAction != null,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        requestAction(VoiceSearchMode.HUM_AND_SING)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
                Text(
                    "Hum, sing or play it nearby",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
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
    liveLyrics: LiveLyricsState,
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
            live = liveLyrics,
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
    live: LiveLyricsState,
    onSeekToLyric: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val syncedLines = lyrics?.synced.orEmpty()

    if (syncedLines.isNotEmpty()) {
        LiveLyricsStatus(live)
        Spacer(Modifier.height(6.dp))
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(if (live is LiveLyricsState.Live) 150.dp else 120.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerHigh.copy(alpha = 0.6f)
    ) {
        if (syncedLines.isNotEmpty()) {
            // The current line, following the music nearby (only once the sync is confirmed).
            var activeIndex by remember { mutableIntStateOf(-1) }
            LaunchedEffect(live, syncedLines) {
                if (live !is LiveLyricsState.Live) { activeIndex = -1; return@LaunchedEffect }
                while (true) {
                    withFrameNanos { now ->
                        val position = live.positionNow(now)
                        activeIndex = syncedLines.indexOfLast { it.time <= position }
                    }
                }
            }
            val listState = rememberLazyListState()
            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(syncedLines) { index, line ->
                    val isActive = index == activeIndex
                    val color by animateColorAsState(
                        when {
                            activeIndex < 0 -> colors.onSurface
                            isActive -> colors.primary
                            index < activeIndex -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                            else -> colors.onSurfaceVariant
                        },
                        label = "liveLyricColor"
                    )
                    Text(
                        text = line.line,
                        style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = color,
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

/** "Syncing to the music…" / "Live" / why it couldn't sync, above the lyrics. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LiveLyricsStatus(live: LiveLyricsState) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
    ) {
        when (live) {
            LiveLyricsState.Idle -> Unit
            LiveLyricsState.Syncing -> {
                androidx.compose.material3.LoadingIndicator(Modifier.size(22.dp), color = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text("Syncing to the music\u2026", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            is LiveLyricsState.Live -> {
                val pulse = rememberInfiniteTransition(label = "livePulse").animateFloat(
                    initialValue = 1f, targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "livePulseAlpha"
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = pulse.value }
                        .clip(CircleShape)
                        .background(colors.error)
                )
                Spacer(Modifier.width(8.dp))
                Text("Live \u00B7 following the music", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
            }
            is LiveLyricsState.Unavailable -> {
                Text(live.reason, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}
