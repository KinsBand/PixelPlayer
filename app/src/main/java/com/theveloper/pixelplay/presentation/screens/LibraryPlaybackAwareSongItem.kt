package com.theveloper.pixelplay.presentation.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.lyrics.training.LyricsSidecar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Immutable
internal data class LibrarySongPlaybackUiState(
    val isCurrentSong: Boolean = false,
    val isPlaying: Boolean = false
)

/** Which song is playing, shared by a whole list (see [rememberLibraryPlaybackSnapshot]). */
@Immutable
internal data class LibraryPlaybackSnapshot(
    val currentSongId: String? = null,
    val isPlaying: Boolean = false
)

/**
 * One playback collector for a whole list. Rows given this state derive their own
 * "is this me?" from it instead of each starting a Flow collector as they scroll in, which
 * added a coroutine (and lifecycle observer) per row on every fast scroll.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun rememberLibraryPlaybackSnapshot(playerViewModel: PlayerViewModel): State<LibraryPlaybackSnapshot> =
    remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { state -> LibraryPlaybackSnapshot(state.currentSong?.id, state.isPlaying) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = LibraryPlaybackSnapshot())

@OptIn(UnstableApi::class)
@Composable
internal fun LibraryPlaybackAwareSongItem(
    song: Song,
    playerViewModel: PlayerViewModel,
    /** The list's shared playback state; null = this row watches the player itself. */
    playback: State<LibraryPlaybackSnapshot>? = null,
    albumArtSize: Dp = 50.dp,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    /** Shown just left of the options button (e.g. the synced-lyrics badge in Folders). */
    extraTrailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val playbackUiState by if (playback != null) {
        // Only rows whose own value changes recompose (derivedStateOf compares the result).
        remember(song.id, playback) {
            derivedStateOf {
                val snapshot = playback.value
                val isCurrentSong = snapshot.currentSongId == song.id
                LibrarySongPlaybackUiState(
                    isCurrentSong = isCurrentSong,
                    isPlaying = isCurrentSong && snapshot.isPlaying
                )
            }
        }
    } else {
        remember(song.id, playerViewModel) {
            playerViewModel.stablePlayerState
                .map { state ->
                    val isCurrentSong = state.currentSong?.id == song.id
                    LibrarySongPlaybackUiState(
                        isCurrentSong = isCurrentSong,
                        isPlaying = isCurrentSong && state.isPlaying
                    )
                }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = LibrarySongPlaybackUiState())
    }

    EnhancedSongListItem(
        song = song,
        isPlaying = playbackUiState.isPlaying,
        isCurrentSong = playbackUiState.isCurrentSong,
        isLoading = false,
        albumArtSize = albumArtSize,
        isSelected = isSelected,
        selectionIndex = selectionIndex,
        isSelectionMode = isSelectionMode,
        onLongPress = onLongPress,
        onMoreOptionsClick = onMoreOptionsClick,
        extraTrailing = extraTrailing,
        onClick = onClick
    )
}

/**
 * Small "LRC" badge for a song that has a synced .lrc saved next to its file (downloads get one
 * automatically). Checked off the main thread and cached, so scrolling stays smooth.
 */
@Composable
internal fun SyncedLyricsBadge(path: String?) {
    val hasSynced by produceState(initialValue = false, path) {
        value = withContext(Dispatchers.IO) { LyricsSidecar.hasSynced(path) }
    }
    if (!hasSynced) return
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = Modifier
            .padding(end = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Synced lyrics saved" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_lyrics_24),
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text("LRC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
