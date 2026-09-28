package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.presentation.components.SyncedLyricsList
import com.theveloper.pixelplay.presentation.components.resolveCurrentLineIndex
import kotlinx.coroutines.flow.StateFlow

/**
 * Face-to-face lyrics: shown in immersive mode when the "Face-to-face lyrics" setting is on.
 *
 * Portrait, top → bottom:
 *   minimised song bar (upright, read by the person at the bottom edge, at their far end)
 *   lyrics turned 180° (read by the person at the top edge)
 *   centre divider: section pills either side of a small play / pause button
 *   lyrics upright (read by the person at the bottom edge)
 *   minimised song bar turned 180° (read by the person at the top edge, at their far end)
 *
 * Landscape ([landscape]), left | centre | right:
 *   the left half is the right half turned 180° as a whole (for the person at the top edge)
 *   a vertical divider with the play / pause button in the middle
 *   the right half, upright (for the person at the bottom edge): the section pill at its top,
 *   the lyrics filling it, and the minimised song bar at its bottom turned 180° so the person
 *   across the table reads it at their far end.
 *
 * Tapping the current song in either bar leaves face-to-face mode ([headerPill] wires that up).
 *
 * Each half is the same [SyncedLyricsList] the main sheet uses, so every lyrics setting
 * (animated lyrics, blur + strength, "disable blur all over", highlight mode, alignment,
 * translation, romanization, font, size, sync offset, autoscroll speed, preview seeking,
 * intro/outro bubbles, word-by-word highlight) applies here exactly as it does full screen.
 * The current line is kept in the centre of each half, with the previous line above it and
 * the next line below it.
 */
@Composable
internal fun SplitFaceLyricsView(
    lines: List<SyncedLine>,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long?,
    accentColor: Color,
    textStyle: TextStyle,
    autoscrollAnimationSpec: AnimationSpec<Float>,
    useAnimatedLyrics: Boolean,
    highlightMode: LyricsHighlightMode,
    animatedLyricsBlurEnabled: Boolean,
    animatedLyricsBlurStrength: Float,
    lyricsAlignment: String,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onLineClick: (SyncedLine) -> Unit,
    onSeekTo: (Long) -> Unit,
    onBackgroundTap: () -> Unit,
    /** The minimised song bar (cover, title, next song). */
    headerPill: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The song part playing now (from the song structure), given the most width it may use.
     * Portrait: beside the centre divider, upright on its left for the person at the bottom and
     * turned on its right for the person at the top. Landscape: at the top of each half.
     */
    sectionChip: (@Composable (maxWidth: Dp) -> Unit)? = null,
    /** Re-centres both halves together when the user's typography changes. */
    relayoutKey: Any? = null,
    /** Small play / pause button on the centre divider. Hidden when null. */
    isPlaying: Boolean = false,
    onPlayPause: (() -> Unit)? = null,
    playPauseContainer: Color = accentColor,
    playPauseContent: Color = Color.White,
    /** Side-by-side halves for a phone lying sideways between two people. */
    landscape: Boolean = false,
) {
    val tapModifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onBackgroundTap
    )
    val half: @Composable (Modifier, Boolean) -> Unit = { halfModifier, rotated ->
        LyricsHalf(
            modifier = halfModifier,
            rotated = rotated,
            lines = lines,
            playbackPositionFlow = playbackPositionFlow,
            lyricsSyncOffset = lyricsSyncOffset,
            positionOverrideMs = positionOverrideMs,
            accentColor = accentColor,
            textStyle = textStyle,
            autoscrollAnimationSpec = autoscrollAnimationSpec,
            useAnimatedLyrics = useAnimatedLyrics,
            highlightMode = highlightMode,
            animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
            animatedLyricsBlurStrength = animatedLyricsBlurStrength,
            lyricsAlignment = lyricsAlignment,
            showTranslation = showTranslation,
            showRomanization = showRomanization,
            onLineClick = onLineClick,
            onSeekTo = onSeekTo,
            relayoutKey = relayoutKey,
        )
    }
    val playPause: @Composable () -> Unit = {
        if (onPlayPause != null) {
            CentrePlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPause,
                containerColor = playPauseContainer,
                contentColor = playPauseContent
            )
        }
    }

    if (landscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .then(tapModifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top person: the right half's layout turned 180° as a whole.
            LandscapePanel(
                rotated = true,
                half = half,
                headerPill = headerPill,
                sectionChip = sectionChip
            )
            VerticalCentreDivider(accentColor = accentColor, playPause = playPause)
            // Bottom person: upright.
            LandscapePanel(
                rotated = false,
                half = half,
                headerPill = headerPill,
                sectionChip = sectionChip
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(tapModifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Song bar for the bottom viewer, at their far end (physical top).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            headerPill(Modifier)
        }

        half(Modifier.weight(1f).fillMaxWidth(), true)        // top viewer, turned 180°
        CentreDivider(accentColor, sectionChip, playPause, hasPlayPause = onPlayPause != null)
        half(Modifier.weight(1f).fillMaxWidth(), false)       // bottom viewer, upright

        // Song bar for the top viewer, at their far end (physical bottom). Replaces the
        // "show controls" arrow; tapping the song in it leaves face-to-face mode.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 4.dp)
                .graphicsLayer { rotationZ = 180f }
                .clearAndSetSemantics { },
            contentAlignment = Alignment.CenterStart
        ) {
            headerPill(Modifier)
        }
    }
}

/**
 * One side of the landscape face-to-face layout, read by the person at the bottom edge when
 * upright: section pill at the top (their far end), lyrics in the middle, and the song bar at the
 * bottom turned 180° for the person across the table. [rotated] turns the whole panel.
 */
@Composable
private fun RowScope.LandscapePanel(
    rotated: Boolean,
    half: @Composable (Modifier, Boolean) -> Unit,
    headerPill: @Composable (Modifier) -> Unit,
    sectionChip: (@Composable (maxWidth: Dp) -> Unit)?,
) {
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(
                if (rotated) {
                    Modifier
                        .graphicsLayer { rotationZ = 180f }
                        .clearAndSetSemantics { }
                } else Modifier
            )
    ) {
        val panelWidth = maxWidth
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The status / navigation bars sit on the long edges in landscape.
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sectionChip != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    sectionChip(panelWidth * 0.6f)
                }
            }
            // The panel's own rotation already faces the right reader, so the lyrics inside it
            // are drawn upright.
            half(Modifier.weight(1f).fillMaxWidth(), false)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .graphicsLayer { rotationZ = 180f }
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.CenterStart
            ) {
                headerPill(Modifier)
            }
        }
    }
}

/**
 * Small play / pause on the divider. The shape morphs (circle while paused, rounded square while
 * playing) like the big button, and the icon swaps with a scale-and-fade.
 */
@Composable
private fun CentrePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 11.dp else 18.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "splitPlayPauseCorner"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "splitPlayPausePress"
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(corner))
            .background(containerColor)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.6f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.6f))
            },
            label = "splitPlayPauseIcon"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Landscape divider: a vertical line through the middle of the screen, play / pause at its centre. */
@Composable
private fun VerticalCentreDivider(
    accentColor: Color,
    playPause: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .width(48.dp)
    ) {
        val lineHeight = maxHeight * 0.22f
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .width(1.5.dp)
                    .height(lineHeight)
                    .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
            )
            Box(Modifier.padding(vertical = 8.dp)) { playPause() }
            Box(
                Modifier
                    .width(1.5.dp)
                    .height(lineHeight)
                    .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
            )
        }
    }
}

private val EdgeFade: Dp = 28.dp

@Composable
private fun LyricsHalf(
    modifier: Modifier,
    rotated: Boolean,
    lines: List<SyncedLine>,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long?,
    accentColor: Color,
    textStyle: TextStyle,
    autoscrollAnimationSpec: AnimationSpec<Float>,
    useAnimatedLyrics: Boolean,
    highlightMode: LyricsHighlightMode,
    animatedLyricsBlurEnabled: Boolean,
    animatedLyricsBlurStrength: Float,
    lyricsAlignment: String,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onLineClick: (SyncedLine) -> Unit,
    onSeekTo: (Long) -> Unit,
    relayoutKey: Any?,
) {
    // Both halves read the same position StateFlow, so within a frame they always resolve the
    // same current line; they only differ if someone scrolls one half by hand, and the next
    // line (or a relayout) snaps both back together.
    // Start already on the current line so the half doesn't scroll in from the top.
    val initialIndex = remember(lines) {
        // Same rows as the list itself (instrumental breaks included).
        val lines = com.theveloper.pixelplay.presentation.components.withInstrumentalBreaks(lines)
        val pos = (playbackPositionFlow.value + lyricsSyncOffset).coerceAtLeast(0L)
        val hasIntro = (lines.firstOrNull()?.time ?: 0) >= 1500 && lines.firstOrNull()?.line?.isNotBlank() == true
        val resolved = resolveCurrentLineIndex(lines = lines, position = pos)
        if (resolved >= 0) resolved + (if (hasIntro) 1 else 0) else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .then(
                if (rotated) {
                    Modifier
                        // Pointer input is transformed too, so scrolling and tapping lines
                        // feel natural from the other side.
                        .graphicsLayer { rotationZ = 180f }
                        // The upright half is read by TalkBack; skip the copy.
                        .clearAndSetSemantics { }
                } else Modifier
            )
            // Soft fade at the top and bottom so lines ease in and out instead of being cut.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = EdgeFade.toPx().coerceAtMost(size.height / 3f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        fade / size.height to Color.Black,
                        1f - fade / size.height to Color.Black,
                        1f to Color.Transparent,
                        startY = 0f,
                        endY = size.height
                    ),
                    topLeft = Offset.Zero,
                    size = size,
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        // Half the viewport of padding on each side lets the first and last lines sit in the
        // centre; the snap offset of 0 keeps the current line centred.
        val verticalPad = maxHeight / 2 - 24.dp
        SyncedLyricsList(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(
                top = verticalPad.coerceAtLeast(0.dp),
                bottom = verticalPad.coerceAtLeast(0.dp)
            ),
            lines = lines,
            listState = listState,
            playbackPositionFlow = playbackPositionFlow,
            lyricsSyncOffset = lyricsSyncOffset,
            positionOverrideMs = positionOverrideMs,
            accentColor = accentColor,
            textStyle = textStyle,
            onLineClick = onLineClick,
            highlightZoneFraction = 0.08f,
            highlightOffsetDp = 0.dp,
            autoscrollAnimationSpec = autoscrollAnimationSpec,
            useAnimatedLyrics = useAnimatedLyrics,
            highlightMode = highlightMode,
            animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
            animatedLyricsBlurStrength = animatedLyricsBlurStrength,
            immersiveMode = true,
            lyricsAlignment = lyricsAlignment,
            showTranslation = showTranslation,
            showRomanization = showRomanization,
            onSeekTo = onSeekTo,
            relayoutKey = relayoutKey,
        )
    }
}

@Composable
private fun CentreDivider(
    accentColor: Color,
    sectionChip: (@Composable (maxWidth: Dp) -> Unit)?,
    playPause: @Composable () -> Unit,
    hasPlayPause: Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                when {
                    hasPlayPause -> 44.dp
                    sectionChip != null -> 34.dp
                    else -> 14.dp
                }
            )
    ) {
        // Lines are 20 % of the width each, so the gaps either side are ~30 % minus the button.
        val lineWidth = maxWidth * 0.2f
        val centreBlock = if (hasPlayPause) 52.dp else 22.dp
        val gap = (maxWidth - lineWidth * 2 - centreBlock) / 2
        val chipMax = (gap - 20.dp).coerceAtLeast(48.dp)

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(lineWidth)
                    .height(1.5.dp)
                    .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
            )
            if (hasPlayPause) {
                // Where the dot was: a small play / pause between the two dashes.
                Box(Modifier.padding(horizontal = 8.dp)) { playPause() }
            } else {
                Box(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .size(6.dp)
                        .background(accentColor.copy(alpha = 0.6f), CircleShape)
                )
            }
            Box(
                Modifier
                    .width(lineWidth)
                    .height(1.5.dp)
                    .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
            )
        }

        if (sectionChip != null) {
            // Bottom person: left of the divider, upright.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .width((gap - 12.dp).coerceAtLeast(0.dp)),
                contentAlignment = Alignment.Center
            ) {
                sectionChip(chipMax)
            }
            // Top person: right of the divider, turned to face them.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .width((gap - 12.dp).coerceAtLeast(0.dp))
                    .graphicsLayer { rotationZ = 180f }
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                sectionChip(chipMax)
            }
        }
    }
}
