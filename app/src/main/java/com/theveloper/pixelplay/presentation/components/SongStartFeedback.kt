package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.subcomps.LocalSongStartRequest
import com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase
import com.theveloper.pixelplay.presentation.viewmodel.SongStartReducer
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion

/** The start state of the song card for [songId] (null = nothing pending for it). */
@Composable
fun rememberSongStartPhase(songId: String?): SongStartPhase? {
    val request = LocalSongStartRequest.current.value
    return if (songId == null) null else SongStartReducer.phaseFor(request, songId)
}

/**
 * The tapped-song feedback for any song card that isn't [subcomps.EnhancedSongListItem]
 * (playlist and queue rows, quick picks, history pills, artist rows…):
 * - **Starting** (tapped, audio not playing yet): a thin primary trace runs round the card.
 * - **Failed**: an error tint and outline, and one small shake.
 * Values are read in the draw phase, so a running trace doesn't recompose the card.
 * Put it after the card's clip, with the card's own [shape].
 */
@Composable
fun Modifier.songStartFeedback(
    songId: String?,
    shape: Shape,
    /** How far the failed shake moves; big cards (Friends Mix) use a little more. */
    shakeAmplitude: androidx.compose.ui.unit.Dp = 4.dp,
): Modifier {
    val phase = rememberSongStartPhase(songId)
    val isStarting = phase == SongStartPhase.STARTING
    val isFailed = phase == SongStartPhase.FAILED
    val colors = MaterialTheme.colorScheme
    val reduced = rememberSystemReducedMotion()
    val failed = animateFloatAsState(
        targetValue = if (isFailed) 1f else 0f,
        animationSpec = if (isFailed) {
            tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)
        } else {
            tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)
        },
        label = "songStartFailedTint"
    )
    val shake = remember { Animatable(0f) }
    LaunchedEffect(isFailed, reduced) {
        shake.snapTo(0f)
        if (isFailed && !reduced) shake.animateTo(1f, tween(MotionTokens.DurationMedium1))
    }
    val shakePx = with(LocalDensity.current) { shakeAmplitude.toPx() }
    val startingLabel = stringResource(R.string.song_card_starting)
    val failedLabel = stringResource(R.string.song_card_failed)
    val error = colors.error
    return this
        .graphicsLayer {
            val p = shake.value
            translationX = if (p <= 0f || p >= 1f) 0f
            else kotlin.math.sin(p * 3f * kotlin.math.PI.toFloat()) * (1f - p) * shakePx
        }
        .loadingOutlineTrace(active = isStarting, color = colors.primary) { shape }
        .drawWithContent {
            drawContent()
            val f = failed.value
            if (f <= 0f) return@drawWithContent
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, error.copy(alpha = 0.16f * f))
            drawOutline(outline, error.copy(alpha = f), style = Stroke(ConnectivityStrokeWidth.toPx()))
        }
        .semantics {
            when (phase) {
                SongStartPhase.STARTING -> stateDescription = startingLabel
                SongStartPhase.FAILED -> {
                    stateDescription = failedLabel
                    liveRegion = LiveRegionMode.Polite
                }
                null -> Unit
            }
        }
}

/**
 * The spinner shown on a song's cover while it starts: the M3 Expressive loading indicator, the
 * same on every song card (EnhancedSongListItem, friend history rows) and on the Friends screen.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongCoverLoadingIndicator(
    size: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.LoadingIndicator(
        modifier = modifier.then(Modifier.size(size)),
        color = color
    )
}
