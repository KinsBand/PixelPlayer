package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.SongQuickAction

/**
 * Intercepts the *primary* tap on a song card (the tap that would normally start playing it).
 * Provided only while the lyrics screen's Add Song flow is active; null otherwise, so every screen
 * keeps its normal behaviour. Dedicated buttons on a card (⋮, artist, album…) never go through it.
 */
@Stable
fun interface SongPrimaryTapInterceptor {
    fun onSongTap(song: Song, contextQueue: List<Song>, queueName: String)
}

val LocalSongPrimaryTap = compositionLocalOf<SongPrimaryTapInterceptor?> { null }

/**
 * Call sites use this instead of playing straight away:
 * `onClick = { songTap.handle(song, queue, name) { playerViewModel.showAndPlaySong(song, queue, name) } }`
 */
fun SongPrimaryTapInterceptor?.handle(
    song: Song,
    contextQueue: List<Song>,
    queueName: String,
    default: () -> Unit,
) {
    if (this != null) onSongTap(song, contextQueue, queueName) else default()
}

/** What the song action sheet is currently showing. */
data class SongActionRequest(val song: Song, val contextQueue: List<Song>, val queueName: String)

/**
 * Compact bottom sheet: the song, then exactly four choices (Play · Next · Soon · Queue).
 * One row when there's room for four comfortable tiles, otherwise 2 × 2. Never scrolls.
 * Swipe down / tap outside only closes the sheet (the caller stays where it is).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionSheet(
    request: SongActionRequest,
    onAction: (SongQuickAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SongActionHeader(request.song)
            SongActionTiles(onAction = onAction)
        }
    }
}

@Composable
private fun SongActionHeader(song: Song) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmartImage(
            model = song.albumArtUriString ?: R.drawable.rounded_album_24,
            contentDescription = null,
            shape = RoundedCornerShape(10.dp),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.displayArtist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class TileSpec(val action: SongQuickAction, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val Tiles = listOf(
    TileSpec(SongQuickAction.PLAY, "Play", Icons.Rounded.PlayArrow),
    TileSpec(SongQuickAction.NEXT, "Next", Icons.AutoMirrored.Rounded.PlaylistPlay),
    TileSpec(SongQuickAction.SOON, "Soon", Icons.Rounded.Schedule),
    TileSpec(SongQuickAction.QUEUE, "Queue", Icons.AutoMirrored.Rounded.QueueMusic),
)

@Composable
private fun SongActionTiles(onAction: (SongQuickAction) -> Unit) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    // Widest label at the current font scale, plus the tile's side padding.
    val widestLabel = remember(labelStyle, density) {
        with(density) {
            Tiles.maxOf { measurer.measure(it.label, labelStyle).size.width }.toDp() + 24.dp
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val perTileInRow = (maxWidth - gap * 3) / 4
        val oneRow = perTileInRow >= MinTileWidth && perTileInRow >= widestLabel
        val tile: @Composable (TileSpec, Modifier) -> Unit = { spec, modifier ->
            ActionTile(
                icon = spec.icon,
                label = spec.label,
                modifier = modifier,
                primary = spec.action == SongQuickAction.PLAY,
                height = 72.dp,
                onClick = { onAction(spec.action) }
            )
        }
        if (oneRow) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                Tiles.forEach { tile(it, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { tile(it, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private val MinTileWidth = 72.dp
