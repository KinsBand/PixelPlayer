package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
 * Top → bottom:
 *   header pill (upright, read by the person at the bottom edge, at their far end)
 *   lyrics turned 180° (read by the person at the top edge)
 *   centre divider
 *   lyrics upright (read by the person at the bottom edge)
 *   header pill turned 180° (read by the person at the top edge, at their far end)
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
    headerPill: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The song part playing now (from the song structure), given the most width it may use.
     * Shown twice beside the centre divider: in the gap to its left, upright, for the person at
     * the bottom; and in the gap to its right, turned 180°, for the person at the top.
     */
    sectionChip: (@Composable (maxWidth: Dp) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBackgroundTap
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header for the bottom viewer, at their far end (physical top).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            headerPill(Modifier)
        }

        val half: @Composable ColumnScope.(Boolean) -> Unit = { rotated ->
            LyricsHalf(
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
            )
        }

        half(true)        // top viewer, turned 180°
        CentreDivider(accentColor, sectionChip)
        half(false)       // bottom viewer, upright

        // Header for the top viewer, at their far end (physical bottom). Replaces the
        // "show controls" arrow; tapping it brings the controls back like any other tap.
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

private val EdgeFade: Dp = 28.dp

@Composable
private fun ColumnScope.LyricsHalf(
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
) {
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
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
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
        )
    }
}

@Composable
private fun CentreDivider(
    accentColor: Color,
    sectionChip: (@Composable (maxWidth: Dp) -> Unit)?,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (sectionChip != null) 34.dp else 14.dp)
    ) {
        // Lines are 20 % of the width each, so the gaps either side are ~30 % minus the dot.
        val lineWidth = maxWidth * 0.2f
        val dotBlock = 22.dp
        val gap = (maxWidth - lineWidth * 2 - dotBlock) / 2
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
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .size(6.dp)
                    .background(accentColor.copy(alpha = 0.6f), CircleShape)
            )
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
