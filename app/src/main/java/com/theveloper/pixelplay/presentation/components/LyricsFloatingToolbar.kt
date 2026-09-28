package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Lyrics
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.style.TextOverflow

import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

enum class PerformanceView {
    /** Lyrics page. */
    Lyrics,
    /** Instruments page: guitar, bass, drums… tabs with practice tools. */
    Instruments,
}

class BubbleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val width = size.width
        val height = size.height
        val arrowWidth = with(density) { 14.dp.toPx() }
        val arrowHeight = with(density) { 8.dp.toPx() }
        val cornerRadius = with(density) { 20.dp.toPx() }

        val path = Path().apply {
            val rectHeight = height - arrowHeight
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, width, rectHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            )
            // Triangle pointer pointing down in the middle of the bottom
            moveTo(width / 2f - arrowWidth / 2f, rectHeight)
            lineTo(width / 2f, height)
            lineTo(width / 2f + arrowWidth / 2f, rectHeight)
            close()
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsFloatingToolbar(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    showSyncedLyrics: Boolean?,
    onShowSyncedLyricsChange: (Boolean) -> Unit,
    hasSyncedLyrics: Boolean,
    onMoreClick: () -> Unit,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    performanceView: PerformanceView,
    onPerformanceViewChange: (PerformanceView) -> Unit,
    /** Instruments page: the practice area under the toolbar is open. */
    instrumentsPanelOpen: Boolean = false,
    /** Instruments page: the Instruments button opens / closes the practice area. */
    onInstrumentsClick: () -> Unit = {},
    // Draw-phase lambda: 0f = fully visible, 1f = dismissed. Read inside graphicsLayer to avoid recomposition per frame.
    backProgressProvider: () -> Float = { 0f },
    /** Add Song: opens Search to pick a song to play / queue. Hidden when null. */
    onAddSongClick: (() -> Unit)? = null
) {
    // Always shown, so the Instruments page is reachable even for songs without lyrics.
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val backInteractionSource = remember { MutableInteractionSource() }
        val isBackPressed by backInteractionSource.collectIsPressedAsState()

        // Animate scale on press: shrinks on press, springs back on release.
        val backPressScale by animateFloatAsState(
            targetValue = if (isBackPressed) 0.82f else 1f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMedium,
                dampingRatio = Spring.DampingRatioMediumBouncy
            ),
            label = "backPressScale"
        )

        IconButton(
            modifier = Modifier.graphicsLayer {
                // Combine press scale with predictive back gesture scale.
                val gestureScale = lerp(1f, 0.7f, backProgressProvider())
                val combined = backPressScale * gestureScale
                scaleX = combined
                scaleY = combined
            },
            interactionSource = backInteractionSource,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = backgroundColor,
                contentColor = onBackgroundColor
            ),
            onClick = onNavigateBack
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = onBackgroundColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        PageButtons(
            modifier = Modifier.weight(1f),
            showSyncedLyrics = showSyncedLyrics,
            hasSyncedLyrics = hasSyncedLyrics,
            onShowSyncedLyricsChange = onShowSyncedLyricsChange,
            backgroundColor = backgroundColor,
            onBackgroundColor = onBackgroundColor,
            accentColor = accentColor,
            onAccentColor = onAccentColor,
            performanceView = performanceView,
            onPerformanceViewChange = onPerformanceViewChange,
            instrumentsPanelOpen = instrumentsPanelOpen,
            onInstrumentsClick = onInstrumentsClick,
        )
        
        Spacer(modifier = Modifier.width(8.dp))

        if (onAddSongClick != null) {
            IconButton(
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = backgroundColor,
                    contentColor = onBackgroundColor
                ),
                onClick = onAddSongClick
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add a song",
                    tint = onBackgroundColor
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        IconButton(
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = backgroundColor,
                contentColor = onBackgroundColor
            ),
            onClick = onMoreClick
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.lyrics_options),
                tint = onBackgroundColor
            )
        }
    }
}

/**
 * The two page buttons between Back and the options menu, as one piece that morphs:
 *
 * - Lyrics page: [Synced / Static] [Instruments →]. Instruments opens the Instruments page.
 * - Instruments page: [♪ back to lyrics] [Instruments ▲]. The small round button returns to the
 *   lyrics; Instruments opens and closes the practice area underneath (no swipe needed).
 *
 * Switching pages is one spring: the Synced pill shrinks into the round lyrics button while
 * Instruments grows into the space, its arrow turning into the open / close chevron.
 */
@Composable
private fun PageButtons(
    modifier: Modifier,
    showSyncedLyrics: Boolean?,
    hasSyncedLyrics: Boolean,
    onShowSyncedLyricsChange: (Boolean) -> Unit,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    performanceView: PerformanceView,
    onPerformanceViewChange: (PerformanceView) -> Unit,
    instrumentsPanelOpen: Boolean,
    onInstrumentsClick: () -> Unit,
) {
    val onInstruments = performanceView == PerformanceView.Instruments
    val progress by animateFloatAsState(
        targetValue = if (onInstruments) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
        label = "pageMorph",
    )
    val chevronTurn by animateFloatAsState(
        targetValue = if (onInstruments && instrumentsPanelOpen) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "chevronTurn",
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    BoxWithConstraints(modifier = modifier.height(50.dp)) {
        val hasToggle = showSyncedLyrics != null
        val gap = 8.dp
        val round = 50.dp
        val lyricsLeft = if (hasToggle) (maxWidth - gap) * (1f / 2.2f) else 0.dp
        val p = progress.coerceIn(0f, 1.05f)
        val leftW = androidx.compose.ui.unit.lerp(lyricsLeft, round, p).coerceAtLeast(0.dp)
        val gapW = if (hasToggle) gap else androidx.compose.ui.unit.lerp(0.dp, gap, progress.coerceIn(0f, 1f))
        val rightW = (maxWidth - leftW - gapW).coerceAtLeast(round)

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leftW > 1.dp) {
                val synced = showSyncedLyrics == true
                val t = progress.coerceIn(0f, 1f)
                val leftBg = androidx.compose.ui.graphics.lerp(if (synced) accentColor else backgroundColor, backgroundColor, t)
                val leftFg = androidx.compose.ui.graphics.lerp(if (synced) onAccentColor else onBackgroundColor, onBackgroundColor, t)
                val corner = androidx.compose.ui.unit.lerp(if (synced) 25.dp else 8.dp, 25.dp, t)
                val enabled = onInstruments || hasSyncedLyrics
                Box(
                    modifier = Modifier
                        .width(leftW)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(corner))
                        .background(leftBg)
                        .clickable(enabled = enabled) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            if (onInstruments) onPerformanceViewChange(PerformanceView.Lyrics)
                            else onShowSyncedLyricsChange(!synced)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (t < 0.98f && hasToggle) {
                        Text(
                            text = if (synced) stringResource(R.string.lyrics_mode_synced) else stringResource(R.string.lyrics_mode_static),
                            color = leftFg,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .graphicsLayer {
                                    alpha = ((1f - t * 1.8f).coerceIn(0f, 1f)) * (if (hasSyncedLyrics || onInstruments) 1f else 0.38f)
                                    scaleX = 1f - 0.25f * t
                                    scaleY = 1f - 0.25f * t
                                },
                        )
                    }
                    if (t > 0.02f) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = "Back to lyrics",
                            tint = leftFg,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    val a = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                                    alpha = a
                                    scaleX = 0.6f + 0.4f * a
                                    scaleY = 0.6f + 0.4f * a
                                    rotationZ = -30f * (1f - a)
                                },
                        )
                    }
                }
                Spacer(Modifier.width(gapW))
            }

            // Instruments: opens the page, then opens / closes the practice area.
            Row(
                modifier = Modifier
                    .width(rightW)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(25.dp))
                    .background(accentColor)
                    .clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (onInstruments) onInstrumentsClick() else onPerformanceViewChange(PerformanceView.Instruments)
                    }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GuitarGlyph(onAccentColor, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Instruments",
                    color = onAccentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    val t = progress.coerceIn(0f, 1f)
                    // Lyrics page: "go there" arrow. Instruments page: chevron that flips when open.
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = onAccentColor,
                        modifier = Modifier.size(16.dp).graphicsLayer {
                            alpha = 1f - t
                            translationX = 10.dp.toPx() * t
                        },
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = if (instrumentsPanelOpen) "Close practice tools" else "Open practice tools",
                        tint = onAccentColor,
                        modifier = Modifier.size(20.dp).graphicsLayer {
                            alpha = t
                            translationY = -8.dp.toPx() * (1f - t)
                            rotationZ = chevronTurn
                        },
                    )
                }
            }
        }
    }
}

/** Small guitar for the Instruments button. */
@Composable
private fun GuitarGlyph(tint: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.1f)
        drawCircle(tint, radius = w * 0.21f, center = androidx.compose.ui.geometry.Offset(w * 0.33f, h * 0.7f), style = st)
        drawCircle(tint, radius = w * 0.15f, center = androidx.compose.ui.geometry.Offset(w * 0.47f, h * 0.5f), style = st)
        drawCircle(tint, radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.37f, h * 0.65f))
        drawLine(tint, androidx.compose.ui.geometry.Offset(w * 0.53f, h * 0.45f), androidx.compose.ui.geometry.Offset(w * 0.9f, h * 0.08f), w * 0.1f)
    }
}
