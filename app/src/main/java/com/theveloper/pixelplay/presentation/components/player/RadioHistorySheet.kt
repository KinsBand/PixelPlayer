package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.radio.RadioHeardTrack
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.screens.radio.StationLogo
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The radio player's History: every song caught while you listened this session (from the
 * station's own titles, or with Listen), newest first, grouped by station. Laid out like a
 * friend's history: who/what's on, two big buttons, then the list. Tap a song to play it
 * (this leaves the radio); ⋮ or a long press for more.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun RadioHistorySheet(
    station: RadioStation?,
    tracks: List<RadioHeardTrack>,
    current: RadioHeardTrack?,
    isListening: Boolean,
    isPlaying: Boolean,
    listenedMs: Long,
    onListen: () -> Unit,
    onPlay: (RadioHeardTrack) -> Unit,
    /** Hold: the song's normal options sheet (the same one every song card opens). */
    onOptions: (RadioHeardTrack) -> Unit,
    /** Whether a heard song is in Liked (by its looked-up song id). */
    isLiked: (RadioHeardTrack) -> Boolean,
    onToggleLike: (RadioHeardTrack) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    // Consecutive songs from the same station share a header.
    val groups = remember(tracks) {
        val out = ArrayList<Pair<String, MutableList<RadioHeardTrack>>>()
        tracks.forEach { t ->
            val name = t.stationName ?: "Radio"
            if (out.isNotEmpty() && out.last().first == name) out.last().second += t else out += name to mutableListOf(t)
        }
        out
    }
    val accent = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (station != null) {
                    StationLogo(station, Modifier.size(52.dp))
                } else {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Radio, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        station?.name ?: "Radio",
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val status = when {
                        isListening -> "Listening for the song…"
                        current != null -> "On now: ${current.title}" + (current.artist.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
                        else -> "Listening for ${listenedLabel(listenedMs)}"
                    }
                    Text(
                        status, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (current != null || isListening) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (current != null || isListening) FontWeight.SemiBold else FontWeight.Normal
                    )
                    // "[♪] 5 in session", like every other song count.
                    val sessionSpoken = "${tracks.size} ${if (tracks.size == 1) "song" else "songs"} this session"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = sessionSpoken }
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "${tracks.size} in session",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onListen, enabled = !isListening, modifier = Modifier.weight(1f).height(48.dp)) {
                    if (isListening) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Rounded.Hearing, null, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isListening) "Listening…" else "Listen")
                }
                FilledTonalButton(onClick = onClear, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
            }
            AnimatedVisibility(current == null && !isListening) {
                Text(
                    "Songs show up here when the station names them, or when you tap Listen.",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (tracks.isEmpty()) {
            Text(
                "Nothing caught yet this session.",
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
            return@ModalBottomSheet
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            groups.forEachIndexed { index, (stationName, groupTracks) ->
                stickyHeader(key = "station:$index") {
                    Surface(color = BottomSheetDefaults.ContainerColor, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stationName, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                            fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                items(groupTracks, key = { it.id }) { track ->
                    // The song the stream says is on now (its newest entry).
                    val isNow = current != null && track === tracks.firstOrNull() && current.key == track.key
                    Row(
                        Modifier.fillMaxWidth()
                            .animateItem()
                            .combinedClickable(onClick = { onPlay(track) }, onLongClick = { onOptions(track) }, onLongClickLabel = "Song options")
                            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                            if (track.coverUrl != null) {
                                SmartImage(model = track.coverUrl, contentDescription = null, modifier = Modifier.size(56.dp), shape = RoundedCornerShape(12.dp))
                            }
                            if (isNow) {
                                Box(
                                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PlayingEqIcon(Modifier.size(width = 18.dp, height = 16.dp), color = accent, isPlaying = isPlaying, phaseDurationMillis = 2400)
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (isNow) accent else MaterialTheme.colorScheme.onSurface
                            )
                            val time = if (isNow) "Now" else Instant.ofEpochMilli(track.heardAt).atZone(zone).toLocalTime().format(timeFormat)
                            val how = if (track.source == RadioHeardTrack.Source.LISTEN) "heard" else null
                            Text(
                                listOfNotNull(track.artist.takeIf { it.isNotBlank() }, time, how).joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Like / unlike this song (hold the row for its full options).
                        val liked = isLiked(track)
                        val likeLabel = stringResource(
                            if (liked) R.string.radio_unlike_song_cd else R.string.radio_like_song_cd,
                            track.title
                        )
                        IconButton(
                            onClick = { onToggleLike(track) },
                            modifier = Modifier.semantics { contentDescription = likeLabel }
                        ) {
                            Crossfade(
                                targetState = liked,
                                animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Standard),
                                label = "radioHistoryLike"
                            ) { isLiked ->
                                Icon(
                                    painter = painterResource(if (isLiked) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24),
                                    contentDescription = null,
                                    tint = if (isLiked) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun listenedLabel(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "under a minute"
        // "45m" / "1h 59m", like playlist lengths.
        else -> com.theveloper.pixelplay.presentation.components.CompactMeta.duration(ms)
    }
}
