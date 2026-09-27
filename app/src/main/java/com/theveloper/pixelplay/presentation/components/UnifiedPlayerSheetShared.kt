@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.theveloper.pixelplay.presentation.components.player.MiniPalette
import com.theveloper.pixelplay.presentation.components.player.MiniSongWaveState
import com.theveloper.pixelplay.presentation.components.player.MiniWaveButtonDip
import com.theveloper.pixelplay.presentation.components.player.MiniWaveContentNudge
import com.theveloper.pixelplay.presentation.components.player.MiniWaveCoverMinScale
import com.theveloper.pixelplay.presentation.components.player.MiniWaveGeometry
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

internal val LocalMaterialTheme = compositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

val MiniPlayerHeight = 64.dp
const val ANIMATION_DURATION_MS = 255
val MiniPlayerBottomSpacer = 8.dp

@Composable
fun getNavigationBarHeight(): Dp {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    return sanitizeNavigationBarBottomInset(insets.calculateBottomPadding())
}

@Composable
internal fun MiniPlayerContentInternal(
    song: Song,
    isPlaying: Boolean,
    isCastConnecting: Boolean,
    isPreparingPlayback: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canScroll: Boolean = true,
    /** Non-null + enabled → the left→right song-change wave renders the row. */
    songWave: MiniSongWaveState? = null,
    isQueueEnded: Boolean = false
) {
    if (songWave != null && songWave.enabled && songWave.basePalette != null && songWave.baseSong != null) {
        MiniPlayerWaveRow(
            wave = songWave,
            isPlaying = isPlaying,
            isCastConnecting = isCastConnecting,
            isPreparingPlayback = isPreparingPlayback,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = modifier,
            canScroll = canScroll,
            isQueueEnded = isQueueEnded
        )
        return
    }
    val hapticFeedback = LocalHapticFeedback.current
    val controlsEnabled = !isCastConnecting && !isPreparingPlayback

    val previousInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val miniPlayerIndication = remember { ripple(bounded = false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val albumArtModel = song.albumArtUriString?.takeIf { it.isNotBlank() }
        Box(contentAlignment = Alignment.Center) {
            key(song.id) {
                SmartImage(
                    model = albumArtModel,
                    contentDescription = "Carátula de ${song.title}",
                    shape = CircleShape,
                    targetSize = Size(150, 150),
                    modifier = Modifier.size(44.dp),
                    songId = song.id
                )
            }
            if (isCastConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = LocalMaterialTheme.current.onPrimaryContainer
                )
            } else if (isPreparingPlayback) {
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            val titleStyle = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer
            )
            val artistStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                letterSpacing = 0.sp,
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f)
            )

            AutoScrollingText(
                text = when {
                    isCastConnecting -> "Connecting to device…"
                    isPreparingPlayback -> "Preparing playback…"
                    else -> song.title
                },
                style = titleStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
                canScroll = canScroll
            )
            AutoScrollingText(
                text = if (isPreparingPlayback) "Loading audio…" else song.displayArtist,
                style = artistStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
                canScroll = canScroll
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalMaterialTheme.current.onPrimary)
                .clickable(
                    interactionSource = previousInteraction,
                    indication = miniPlayerIndication,
                    enabled = controlsEnabled
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPrevious()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Anterior",
                tint = LocalMaterialTheme.current.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalMaterialTheme.current.primary)
                .clickable(
                    interactionSource = playPauseInteraction,
                    indication = miniPlayerIndication,
                    enabled = controlsEnabled
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isQueueEnded -> Icons.Rounded.Replay
                    isPlaying -> Icons.Rounded.Pause
                    else -> Icons.Rounded.PlayArrow
                },
                contentDescription = when {
                    isQueueEnded -> "Replay"
                    isPlaying -> "Pausar"
                    else -> "Reproducir"
                },
                tint = LocalMaterialTheme.current.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalMaterialTheme.current.onPrimary)
                .clickable(
                    interactionSource = nextInteraction,
                    indication = miniPlayerIndication,
                    enabled = controlsEnabled
                ) { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Siguiente",
                tint = LocalMaterialTheme.current.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}


/**
 * Mini player row driven by [MiniSongWaveState]: same layout as the legacy row, but every
 * element reads its colour / alpha / offset from the wave front in draw or layer lambdas, so a
 * song change costs one recomposition at the start and one at the end, none per frame.
 *
 * Content is stacked in layers keyed by wave id (base layer inherits the finishing wave's
 * key), so the incoming title / cover keep their composables when the wave commits.
 */
@Composable
private fun MiniPlayerWaveRow(
    wave: MiniSongWaveState,
    isPlaying: Boolean,
    isCastConnecting: Boolean,
    isPreparingPlayback: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canScroll: Boolean = true,
    isQueueEnded: Boolean = false
) {
    val hapticFeedback = LocalHapticFeedback.current
    val controlsEnabled = !isCastConnecting && !isPreparingPlayback
    val geometry = wave.geometry
    val nudgePx = with(LocalDensity.current) { MiniWaveContentNudge.toPx() }

    val previousInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val miniPlayerIndication = remember { ripple(bounded = false) }

    // State reads here only change when a wave starts / ends.
    val layers = wave.contentLayers()
    val waveRunning = wave.isActive
    val shownSong = wave.displayedSong

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .onPlaced { c ->
                geometry.rowRootX = c.positionInRoot().x
                geometry.rowWidth = c.size.width.toFloat()
            }
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Cover ──
        Box(
            modifier = Modifier
                .size(44.dp)
                .onPlaced { c ->
                    geometry.set(MiniWaveGeometry.SLOT_COVER, c.positionInRoot().x, c.size.width.toFloat())
                },
            contentAlignment = Alignment.Center
        ) {
            layers.forEach { (layerKey, layerSong) ->
                key(layerKey) {
                    SmartImage(
                        model = layerSong.albumArtUriString?.takeIf { it.isNotBlank() },
                        contentDescription = "Carátula de ${layerSong.title}",
                        shape = CircleShape,
                        targetSize = Size(150, 150),
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                val v = wave.layerVisibility(layerKey, MiniWaveGeometry.SLOT_COVER)
                                alpha = v
                                val sc = MiniWaveCoverMinScale + (1f - MiniWaveCoverMinScale) * v
                                scaleX = sc
                                scaleY = sc
                            },
                        songId = layerSong.id
                    )
                }
            }
            if (isCastConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = LocalMaterialTheme.current.onPrimaryContainer
                )
            } else if (isPreparingPlayback) {
                // "Preparing" is shown only here now; the title always shows the real song.
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))

        // ── Title / artist ──
        Box(
            modifier = Modifier
                .weight(1f)
                .onPlaced { c ->
                    geometry.set(MiniWaveGeometry.SLOT_TEXT, c.positionInRoot().x, c.size.width.toFloat())
                }
                .clearAndSetSemantics {
                    contentDescription = shownSong?.let { "${it.title}, ${it.displayArtist}" }.orEmpty()
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.CenterStart
        ) {
            layers.forEach { (layerKey, layerSong) ->
                key(layerKey) {
                    val palette = wave.layerPalette(layerKey)
                    if (palette != null) {
                        MiniWaveTextLayer(
                            title = if (isCastConnecting) "Connecting to device…" else layerSong.title,
                            artist = layerSong.displayArtist,
                            palette = palette,
                            canScroll = canScroll && !waveRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val inP = wave.layerIn(layerKey, MiniWaveGeometry.SLOT_TEXT)
                                    val outP = wave.layerOut(layerKey, MiniWaveGeometry.SLOT_TEXT)
                                    alpha = inP * (1f - outP)
                                    translationX = -nudgePx * (1f - inP) + nudgePx * outP
                                }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))

        // ── Controls ──
        MiniWaveButton(
            wave = wave,
            slot = MiniWaveGeometry.SLOT_PREV,
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Anterior",
            filled = false,
            enabled = controlsEnabled,
            interactionSource = previousInteraction,
            indication = miniPlayerIndication
        ) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPrevious()
        }
        Spacer(modifier = Modifier.width(8.dp))
        MiniWaveButton(
            wave = wave,
            slot = MiniWaveGeometry.SLOT_PLAY,
            icon = when {
                isQueueEnded -> Icons.Rounded.Replay
                isPlaying -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            },
            contentDescription = when {
                isQueueEnded -> "Replay"
                isPlaying -> "Pausar"
                else -> "Reproducir"
            },
            filled = true,
            enabled = controlsEnabled,
            interactionSource = playPauseInteraction,
            indication = miniPlayerIndication
        ) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPlayPause()
        }
        Spacer(modifier = Modifier.width(8.dp))
        MiniWaveButton(
            wave = wave,
            slot = MiniWaveGeometry.SLOT_NEXT,
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Siguiente",
            filled = false,
            enabled = controlsEnabled,
            interactionSource = nextInteraction,
            indication = miniPlayerIndication
        ) { onNext() }
    }
}

@Composable
private fun MiniWaveTextLayer(
    title: String,
    artist: String,
    palette: MiniPalette,
    canScroll: Boolean,
    modifier: Modifier = Modifier
) {
    val titleStyle = MaterialTheme.typography.titleSmall.copy(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        fontFamily = GoogleSansRounded,
        color = palette.onContainer
    )
    val artistStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        letterSpacing = 0.sp,
        fontFamily = GoogleSansRounded,
        color = palette.onContainer.copy(alpha = 0.7f)
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        // Same-song metadata updates (online enrichment) crossfade quickly instead of popping.
        Crossfade(targetState = title, animationSpec = tween(150), label = "miniTitle") { t ->
            AutoScrollingText(
                text = t,
                style = titleStyle,
                gradientEdgeColor = palette.container,
                canScroll = canScroll
            )
        }
        Crossfade(targetState = artist, animationSpec = tween(150), label = "miniArtist") { a ->
            AutoScrollingText(
                text = a,
                style = artistStyle,
                gradientEdgeColor = palette.container,
                canScroll = canScroll
            )
        }
    }
}

/**
 * 36 dp control. Container, icon tint and the small "dip" are all read from the wave in the
 * draw / layer phase — the icon is painted from a [rememberVectorPainter] with a tint filter
 * so a changing colour never recomposes.
 */
@Composable
private fun MiniWaveButton(
    wave: MiniSongWaveState,
    slot: Int,
    icon: ImageVector,
    contentDescription: String,
    filled: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    indication: Indication,
    onClick: () -> Unit
) {
    val painter = rememberVectorPainter(icon)
    val iconSizePx = with(LocalDensity.current) { 22.dp.toPx() }
    Box(
        modifier = Modifier
            .size(36.dp)
            .onPlaced { c -> wave.geometry.set(slot, c.positionInRoot().x, c.size.width.toFloat()) }
            .graphicsLayer {
                val s = 1f - MiniWaveButtonDip * wave.dipAt(slot)
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .drawBehind {
                val palette = wave.paletteAt(slot) ?: return@drawBehind
                val container = if (filled) palette.primary else palette.onPrimary
                val tint = if (filled) palette.onPrimary else palette.primary
                drawRect(container)
                translate(
                    left = (size.width - iconSizePx) / 2f,
                    top = (size.height - iconSizePx) / 2f
                ) {
                    with(painter) {
                        draw(
                            size = androidx.compose.ui.geometry.Size(iconSizePx, iconSizePx),
                            colorFilter = ColorFilter.tint(tint)
                        )
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription }
    )
}
