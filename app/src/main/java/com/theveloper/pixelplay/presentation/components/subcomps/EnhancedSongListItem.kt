package com.theveloper.pixelplay.presentation.components.subcomps

import com.theveloper.pixelplay.presentation.components.offlineVeil
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.CircularProgressIndicator
import com.theveloper.pixelplay.presentation.components.loadingOutlineTrace
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.SmartImage

@Immutable
private data class EnhancedSongAnimationTarget(
    val isHighlighted: Boolean = false,
    val isSelected: Boolean = false
)

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

/**
 * Enhanced song list item with multi-selection support.
 * 
 * @param song The song to display
 * @param isPlaying Whether this song is currently playing
 * @param isCurrentSong Whether this is the current song in the queue (may be paused)
 * @param isLoading Whether to show loading shimmer state
 * @param showAlbumArt Whether to show the album art
 * @param albumArtSize Size of the album art thumbnail when shown
 * @param customShape Optional custom shape for the surface
 * @param isSelected Whether this item is selected in multi-selection mode
 * @param isSelectionMode Whether multi-selection mode is active
 * @param onLongPress Callback for long press gesture (activates selection)
 * @param onMoreOptionsClick Opens the song's options; called on long press (cards have no ⋮ button)
 * @param onClick Callback for tap gesture
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean = false,
    isLoading: Boolean = false,
    showAlbumArt: Boolean = true,
    albumArtSize: Dp = 50.dp,
    customShape: androidx.compose.ui.graphics.Shape? = null,
    containerColorOverride: Color? = null,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    /** False = this card has no options menu; hold then toggles selection instead. */
    showMoreOptionsButton: Boolean = true,
    onLongPress: () -> Unit = {},
    /** Optional action shown just left of the options button (e.g. Versions). */
    extraTrailing: (@Composable () -> Unit)? = null,
    /** Optional content under the artist line (Search's "+N versions" pill). */
    belowSubtitle: (@Composable () -> Unit)? = null,
    /**
     * This card was just tapped: STARTING until audio starts (a trace runs round the card),
     * FAILED if it couldn't (error colours; tapping again retries). Null = normal.
     */
    startPhase: com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase? =
        com.theveloper.pixelplay.presentation.viewmodel.SongStartReducer.phaseFor(LocalSongStartRequest.current.value, song.id),
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val albumArtTargetSizePx = with(LocalDensity.current) { albumArtSize.roundToPx() }
    val isHighlighted = isCurrentSong && !isLoading
    val transition = updateTransition(
        targetState = EnhancedSongAnimationTarget(
            isHighlighted = isHighlighted,
            isSelected = isSelected
        ),
        label = "EnhancedSongListItemTransition"
    )

    // Share one transition across the item and derive the visual properties from a
    // couple of progress values instead of animating each color/radius independently.
    val highlightProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400) },
        label = "highlightProgress"
    ) { state ->
        if (state.isHighlighted) 1f else 0f
    }
    val selectionVisualProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 250) },
        label = "selectionVisualProgress"
    ) { state ->
        if (state.isSelected) 1f else 0f
    }
    val selectionScaleProgress by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "selectionScaleProgress"
    ) { state ->
        if (state.isSelected) 1f else 0f
    }

    val animatedCornerRadius = lerpDp(22.dp, 50.dp, highlightProgress)
    val animatedAlbumCornerRadius = lerpDp(10.dp, 50.dp, highlightProgress)
    val selectionScale = lerpFloat(1f, 0.98f, selectionScaleProgress)
    val selectionBorderWidth = lerpDp(0.dp, 2.5.dp, selectionVisualProgress)

    val surfaceShape = remember(animatedCornerRadius, customShape, isHighlighted) {
        if (customShape != null && !isHighlighted) {
            customShape
        } else {
            RoundedCornerShape(animatedCornerRadius)
        }
    }

    val albumShape = remember(animatedAlbumCornerRadius) {
        RoundedCornerShape(animatedAlbumCornerRadius)
    }

    val colors = MaterialTheme.colorScheme
    val baseContainerColor = containerColorOverride ?: colors.surfaceContainerLow
    val playbackContainerColor = lerpColor(baseContainerColor, colors.primaryContainer, highlightProgress)
    val containerColor = lerpColor(playbackContainerColor, colors.secondaryContainer, selectionVisualProgress)

    val baseContentColor = colors.onSurface
    val playbackContentColor = lerpColor(baseContentColor, colors.onPrimaryContainer, highlightProgress)
    val contentColor = lerpColor(playbackContentColor, colors.onSecondaryContainer, selectionVisualProgress)

    val selectionBorderColor = lerpColor(colors.primary.copy(alpha = 0f), colors.primary, selectionVisualProgress)
    val mvContainerColor = lerpColor(colors.onSurface, colors.primaryContainer, highlightProgress)
    val mvContentColor = lerpColor(colors.surfaceContainerHigh, colors.onPrimaryContainer, highlightProgress)
    val selectionOverlayColor = lerpColor(
        Color.Transparent,
        colors.primary.copy(alpha = 0.7f),
        selectionVisualProgress
    )
    val selectionOverlayContentColor = lerpColor(
        Color.Transparent,
        colors.onPrimary,
        selectionVisualProgress
    )
    val showSelectionDecoration = selectionVisualProgress > 0.001f

    if (isLoading) {
        // Shimmer Placeholder Layout
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape),
            shape = surfaceShape,
            color = containerColorOverride ?: colors.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if(showAlbumArt) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(albumArtSize)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if(showAlbumArt) 0.dp else 4.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            }
        }
    } else {
        // Actual Song Item Layout
        // Offline and not on the device: blurred with "Offline", tap shakes instead of playing.
        val offlineBlocked = com.theveloper.pixelplay.presentation.components.rememberSongOfflineBlocked(song) && !isSelectionMode
        val start = rememberSongStartVisuals(startPhase)
        val startedContainer = lerpColor(containerColor, colors.primaryContainer, start.starting.value * 0.7f)
        val cardContainerColor = lerpColor(startedContainer, colors.errorContainer, start.failed.value)
        val cardContentColor = lerpColor(contentColor, colors.onErrorContainer, start.failed.value)
        val startingLabel = stringResource(R.string.song_card_starting)
        val failedLabel = stringResource(R.string.song_card_failed)
        val shakePx = with(LocalDensity.current) { SongStartShake.toPx() }
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = start.shakeOffset() * shakePx }
                .scale(selectionScale)
                .clip(surfaceShape)
                .loadingOutlineTrace(
                    active = startPhase == com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase.STARTING,
                    color = colors.primary
                ) { surfaceShape }
                .offlineVeil(offlineBlocked, surfaceShape)
                .semantics {
                    when (startPhase) {
                        com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase.STARTING -> stateDescription = startingLabel
                        com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase.FAILED -> {
                            stateDescription = failedLabel
                            liveRegion = LiveRegionMode.Polite
                        }
                        null -> Unit
                    }
                }
                .then(
                    if (showSelectionDecoration) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = selectionBorderColor,
                            shape = surfaceShape
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics {
                    if (!isSelectionMode && showMoreOptionsButton) {
                        onLongClick(label = "Song options") { onMoreOptionsClick(song); true }
                    }
                }
                .pointerInput(isSelectionMode, showMoreOptionsButton) {
                    detectTapGestures(
                        onTap = { 
                            if (isSelectionMode) {
                                // In selection mode, tap toggles selection
                                onLongPress()
                            } else {
                                onClick() 
                            }
                        },
                        onLongPress = {
                            // Hold opens the song's options (no ⋮ button on the card any more).
                            // In selection mode, or where a card has no options, hold toggles selection.
                            if (!isSelectionMode && showMoreOptionsButton) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMoreOptionsClick(song)
                            } else {
                                onLongPress()
                            }
                        }
                    )
                },
            shape = surfaceShape,
            color = cardContainerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAlbumArt) {
                    Box(
                        modifier = Modifier
                            .size(albumArtSize)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = song.title,
                            shape = albumShape,
                            targetSize = Size(albumArtTargetSizePx, albumArtTargetSizePx),
                            modifier = Modifier.fillMaxSize(),
                            songId = song.id
                        )

                        // Starting: a tinted scrim with a small spinner over the cover.
                        if (start.starting.value > 0.001f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = start.starting.value }
                                    .background(colors.primary.copy(alpha = 0.55f), albumShape),
                                contentAlignment = Alignment.Center
                            ) {
                                // Same expressive indicator as the friend history rows and the Friends screen.
                                com.theveloper.pixelplay.presentation.components.SongCoverLoadingIndicator(
                                    size = albumArtSize * 0.56f,
                                    color = colors.onPrimary
                                )
                            }
                        }
                        
                        // Selection check overlay on album art
                        if (showSelectionDecoration) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = selectionOverlayColor,
                                        shape = albumShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectionIndex != null && selectionIndex >= 0) {
                                    Text(
                                        text = selectionIndex.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = selectionOverlayContentColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.common_selected),
                                        tint = selectionOverlayContentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    if (isHighlighted && !isSelectionMode) {
                        AutoScrollingText(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            gradientEdgeColor = containerColor,
                        )

                    } else {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = cardContentColor,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (startPhase == com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase.FAILED) {
                        Text(
                            text = "$failedLabel · ${stringResource(R.string.song_card_retry)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cardContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackStatusBadges(
                            isFavorite = song.isFavorite,
                            source = song.source,
                            downloadState = song.downloadState
                        )
                        Text(
                            text = song.displayArtist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (belowSubtitle != null && !isSelectionMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        belowSubtitle()
                    }
                }
                
                val showPlayingIndicator = isCurrentSong && !isSelectionMode
                val showTrailingAction = extraTrailing != null && !isSelectionMode

                if (showPlayingIndicator) {
                     PlayingEqIcon(
                         modifier = Modifier
                             .padding(start = 8.dp)
                             .size(width = 18.dp, height = 16.dp),
                         color = contentColor,
                         isPlaying = isPlaying
                     )
                }

                if (showPlayingIndicator || showTrailingAction) {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (showTrailingAction && extraTrailing != null) {
                    extraTrailing()
                    Spacer(modifier = Modifier.width(8.dp))
                }

            }
        }
    }
}

/** How far the card moves sideways when it shakes on a failed start. */
private val SongStartShake = 4.dp

/** Animated values for a song card's start state (see [EnhancedSongListItem]'s startPhase). */
private class SongStartVisuals(
    val starting: androidx.compose.runtime.State<Float>,
    val failed: androidx.compose.runtime.State<Float>,
    private val shake: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
) {
    /** -1..1 sideways offset; a decaying wobble while the shake runs. Read in the draw phase. */
    fun shakeOffset(): Float {
        val p = shake.value
        if (p <= 0f || p >= 1f) return 0f
        return kotlin.math.sin(p * 3f * kotlin.math.PI.toFloat()) * (1f - p)
    }
}

@Composable
private fun rememberSongStartVisuals(
    phase: com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase?
): SongStartVisuals {
    val reduced = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    val isStarting = phase == com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase.STARTING
    val isFailed = phase == com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase.FAILED
    val starting = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isStarting) 1f else 0f,
        animationSpec = if (isStarting) {
            tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.Emphasized)
        } else {
            tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort3, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate)
        },
        label = "songStartStarting"
    )
    val failed = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFailed) 1f else 0f,
        animationSpec = if (isFailed) {
            tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium2, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate)
        } else {
            tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate)
        },
        label = "songStartFailed"
    )
    val shake = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(isFailed, reduced) {
        if (isFailed && !reduced) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium1))
        } else {
            shake.snapTo(0f)
        }
    }
    return remember(starting, failed, shake) { SongStartVisuals(starting, failed, shake) }
}

/**
 * The song card that was just tapped (PlayerViewModel.songStart), provided once by MainActivity
 * as a State, so only the cards that read it recompose when it changes. Previews get null.
 */
val LocalSongStartRequest = androidx.compose.runtime.staticCompositionLocalOf<
    androidx.compose.runtime.State<com.theveloper.pixelplay.presentation.viewmodel.SongStartRequest?>
    > { androidx.compose.runtime.mutableStateOf(null) }
