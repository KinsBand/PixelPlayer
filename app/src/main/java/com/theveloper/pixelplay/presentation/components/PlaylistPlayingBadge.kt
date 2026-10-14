package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.playback.NowPlayingPlaylist
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** True while the current queue came from [playlistId] — works for own and friends' playlists. */
@Composable
fun rememberIsPlaylistPlaying(playlistId: String?, playerViewModel: PlayerViewModel? = null): Boolean {
    val entry by NowPlayingPlaylist.current.collectAsStateWithLifecycle()
    if (playlistId.isNullOrBlank() || entry?.playlistId != playlistId) return false
    if (playerViewModel == null) return true
    val ui by playerViewModel.playerUiState.collectAsStateWithLifecycle()
    return ui.currentQueueSourceName == entry?.queueName
}

/** True while the player is playing (not paused), for badges that have no PlayerViewModel. */
@Composable
fun rememberPlayerIsPlaying(): Boolean {
    val playing by NowPlayingPlaylist.isPlaying.collectAsStateWithLifecycle()
    return playing
}

/** True while the current queue came from any of [playlistIds]. */
@Composable
fun rememberIsAnyPlaylistPlaying(playlistIds: Collection<String>): Boolean {
    val entry by NowPlayingPlaylist.current.collectAsStateWithLifecycle()
    val id = entry?.playlistId ?: return false
    return id in playlistIds
}

/**
 * Small "now playing" badge for a playlist cover. A circle keeps it correct on every cover
 * shape (circle, star, smooth rect). Bars animate while playing and rest while paused.
 * [isPlaying] null = follow the player's own play/pause state.
 */
@Composable
fun PlaylistPlayingBadge(
    visible: Boolean,
    isPlaying: Boolean? = null,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    ringColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.6f) +
            fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
        exit = scaleOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate), targetScale = 0.6f) +
            fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
    ) {
        val playing = isPlaying ?: rememberPlayerIsPlaying()
        val stateLabel = androidx.compose.ui.res.stringResource(
            if (playing) com.theveloper.pixelplay.R.string.playing_badge_now_playing
            else com.theveloper.pixelplay.R.string.playing_badge_paused
        )
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, ringColor, CircleShape)
                .semantics { stateDescription = stateLabel },
            contentAlignment = Alignment.Center
        ) {
            PlayingEqIcon(
                Modifier.size(width = size * 0.5f, height = size * 0.42f),
                color = MaterialTheme.colorScheme.onPrimary,
                isPlaying = playing,
                phaseDurationMillis = 2400
            )
        }
    }
}
