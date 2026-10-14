package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.presentation.components.subcomps.rememberShuffleSpin
import com.theveloper.pixelplay.presentation.components.subcomps.shuffleSpin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Soon: where in Up next the playlist should start. The slider picks "after song k" (0 = right
 * after the song playing now) and shows that song, since the playlist plays after it. The tick at
 * the top right confirms; the shuffle button beside it (several songs only) confirms with the
 * songs in a random order instead of top to bottom.
 */
@Composable
fun PlaylistSoonPanel(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onConfirm: (afterUpcoming: Int) -> Unit,
    /** Several playlists queued together: the copy says "these playlists". */
    plural: Boolean = false,
    /**
     * One song instead of a playlist: song wording, and that song is left out of Up next, so
     * "after song k" means the same once an already-queued copy has been moved.
     */
    forSong: com.theveloper.pixelplay.data.model.Song? = null,
    /** Same as [onConfirm] but the songs go in shuffled. Null (single songs) = no shuffle button. */
    onConfirmShuffled: ((afterUpcoming: Int) -> Unit)? = null,
    /** Own wording (e.g. a Home shuffle): title, and the "then …" lines for now / after a song. */
    titleOverride: String? = null,
    thenNowOverride: String? = null,
    thenAfterOverride: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val queue by remember(playerViewModel) {
        playerViewModel.playerUiState.map { it.currentPlaybackQueue }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = kotlinx.collections.immutable.persistentListOf())
    val stable by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentIndex = stable.currentMediaItemIndex
    val upcoming = remember(queue, currentIndex, forSong?.id) {
        val ahead = if (currentIndex in queue.indices) queue.subList(currentIndex + 1, queue.size) else queue
        if (forSong == null) ahead else ahead.filterNot { it.id == forSong.id }
    }
    var after by rememberSaveable {
        mutableStateOf(
            minOf(com.theveloper.pixelplay.presentation.viewmodel.PLAY_SOON_MAX_AHEAD - 1, upcoming.size)
        )
    }
    val clampedAfter = after.coerceIn(0, upcoming.size)
    val anchor = if (clampedAfter == 0) stable.currentSong else upcoming.getOrNull(clampedAfter - 1)
    val positionLabel = when {
        upcoming.isEmpty() -> "Nothing up next: it plays right after this song"
        clampedAfter == 0 -> "Right after the song playing now"
        else -> "After song $clampedAfter of ${upcoming.size} up next"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(if (forSong != null) R.string.song_option_soon_back else R.string.playlist_option_soon_back)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    titleOverride ?: stringResource(
                        when {
                            forSong != null -> R.string.song_option_soon_title
                            plural -> R.string.playlist_option_soon_title_plural
                            else -> R.string.playlist_option_soon_title
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(positionLabel, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onConfirmShuffled != null) {
                val spin = rememberShuffleSpin()
                FilledTonalIconButton(
                    onClick = { spin.play(); onConfirmShuffled(clampedAfter) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle in after ${anchor?.title ?: "this song"}",
                        modifier = Modifier.shuffleSpin(spin)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            FilledIconButton(
                onClick = { onConfirm(clampedAfter) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Queue after ${anchor?.title ?: "this song"}")
            }
        }
        if (upcoming.isNotEmpty()) {
            Slider(
                value = clampedAfter.toFloat(),
                onValueChange = { after = kotlin.math.round(it).toInt().coerceIn(0, upcoming.size) },
                valueRange = 0f..upcoming.size.toFloat(),
                // Tick marks only while they stay readable.
                steps = if (upcoming.size <= 20) (upcoming.size - 1).coerceAtLeast(0) else 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = positionLabel }
            )
        }
        if (anchor != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = anchor.albumArtUriString,
                    contentDescription = null,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val songThenNow = stringResource(R.string.song_soon_then_now)
                    val songThenAfter = stringResource(R.string.song_soon_then_after)
                    Text(
                        when {
                            thenNowOverride != null && clampedAfter == 0 -> thenNowOverride
                            thenAfterOverride != null && clampedAfter > 0 -> thenAfterOverride
                            forSong != null && clampedAfter == 0 -> songThenNow
                            forSong != null -> songThenAfter
                            plural && clampedAfter == 0 -> "Playing now, then these playlists"
                            plural -> "Then these playlists, after"
                            clampedAfter == 0 -> "Playing now, then this playlist"
                            else -> "Then this playlist, after"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                    Text(anchor.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(anchor.displayArtist, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
