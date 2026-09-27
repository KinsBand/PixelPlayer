package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.lyrics.SongSection
import com.theveloper.pixelplay.data.lyrics.SongSectionKind
import com.theveloper.pixelplay.data.lyrics.SongStructure
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The section playing now, as a State that only changes when the section changes (so the
 * chips don't recompose on every position tick).
 */
@Composable
internal fun rememberCurrentSectionIndex(
    structure: SongStructure,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long?,
): State<Int> {
    val position = playbackPositionFlow.collectAsState()
    val override by rememberUpdatedState(positionOverrideMs)
    val offset by rememberUpdatedState(lyricsSyncOffset)
    return remember(structure) {
        derivedStateOf { structure.indexAt((override ?: position.value) + offset) }
    }
}

/**
 * A header with a second part attached underneath it, drawn as one connected shape: the
 * header keeps its pill-round top, the bottom part is at least as wide as the header and
 * gets softer corners. When [bottom] has nothing to show, it's just the pill.
 */
@Composable
internal fun ConnectedHeader(
    backgroundColor: Color,
    header: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minBottomWidth: Dp = 220.dp,
    bottomCorner: Dp = 22.dp,
) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val headerPlaceables = subcompose("header", header).map { it.measure(loose) }
        val headerW = headerPlaceables.maxOfOrNull { it.width } ?: 0
        val headerH = headerPlaceables.sumOf { it.height }

        val rowW = max(headerW, minBottomWidth.roundToPx()).coerceAtMost(constraints.maxWidth)
        val bottomPlaceables = subcompose("bottom", bottom).map {
            it.measure(Constraints(minWidth = 0, maxWidth = rowW, minHeight = 0, maxHeight = Constraints.Infinity))
        }
        val bottomH = bottomPlaceables.sumOf { it.height }
        val width = if (bottomH > 0) rowW else headerW
        val height = headerH + bottomH

        // Bottom corners go from pill-round to [bottomCorner] as the strip grows in.
        val pillRadius = headerH / 2f
        val grow = (bottomH / 24.dp.toPx()).coerceIn(0f, 1f)
        val bottomRadius = pillRadius + (min(bottomCorner.toPx(), pillRadius) - pillRadius) * grow
        val bg = subcompose("bg") {
            Box(
                Modifier.drawBehind {
                    drawShape(backgroundColor, size, pillRadius, bottomRadius)
                }
            )
        }.map { it.measure(Constraints.fixed(width, height)) }

        layout(width, height) {
            bg.forEach { it.place(0, 0) }
            var y = 0
            headerPlaceables.forEach { it.place(0, y); y += it.height }
            bottomPlaceables.forEach { it.place((width - it.width) / 2, y); y += it.height }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShape(
    color: Color,
    size: Size,
    topRadius: Float,
    bottomRadius: Float,
) {
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(topRadius),
                topRightCornerRadius = CornerRadius(topRadius),
                bottomRightCornerRadius = CornerRadius(bottomRadius),
                bottomLeftCornerRadius = CornerRadius(bottomRadius),
            )
        )
    }
    drawPath(path, color)
}

/**
 * The song's full structure as a row of parts under the lyrics header. The part playing now is
 * filled with the accent colour and shows how far through it you are; the others blur and fade
 * the further they are from it, and everything springs and un-blurs as the song moves on.
 * Tapping a part jumps to it.
 */
@Composable
internal fun SongStructureStrip(
    structure: SongStructure,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long?,
    accentColor: Color,
    onAccentColor: Color,
    contentColor: Color,
    onSectionClick: (SongSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex by rememberCurrentSectionIndex(structure, playbackPositionFlow, lyricsSyncOffset, positionOverrideMs)
    val position = playbackPositionFlow.collectAsState()
    val override by rememberUpdatedState(positionOverrideMs)
    val offset by rememberUpdatedState(lyricsSyncOffset)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))

    // Keep the current part centred.
    LaunchedEffect(currentIndex, structure) {
        if (currentIndex < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == currentIndex }
        if (item == null) {
            listState.scrollToItem(currentIndex)
        }
        val now = listState.layoutInfo
        val target = now.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: return@LaunchedEffect
        val viewportCentre = (now.viewportStartOffset + now.viewportEndOffset) / 2f
        val delta = target.offset + target.size / 2f - viewportCentre
        if (abs(delta) > 1f) {
            listState.animateScrollBy(delta, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow))
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            // Soft fade at both ends.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = (22.dp.toPx() / size.width).coerceIn(0f, 0.3f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        fade to Color.Black,
                        1f - fade to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn
                )
            },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(structure.sections, key = { i, s -> "$i-${s.startMs}" }) { index, section ->
            val distance = if (currentIndex < 0) 3 else abs(index - currentIndex)
            val active = index == currentIndex
            SectionChip(
                section = section,
                active = active,
                past = currentIndex >= 0 && index < currentIndex,
                distance = distance,
                accentColor = accentColor,
                onAccentColor = onAccentColor,
                contentColor = contentColor,
                progress = {
                    if (!active) 0f else {
                        val p = (override ?: position.value) + offset
                        ((p - section.startMs).toFloat() / (section.endMs - section.startMs).coerceAtLeast(1))
                            .coerceIn(0f, 1f)
                    }
                },
                onClick = { onSectionClick(section) },
            )
        }
    }
}

@Composable
private fun SectionChip(
    section: SongSection,
    active: Boolean,
    past: Boolean,
    distance: Int,
    accentColor: Color,
    onAccentColor: Color,
    contentColor: Color,
    progress: () -> Float,
    onClick: () -> Unit,
) {
    val motion = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val scale by animateFloatAsState(if (active) 1f else 0.9f, motion, label = "sectionScale")
    val alpha by animateFloatAsState(
        when {
            active -> 1f
            else -> (0.85f - 0.14f * distance - if (past) 0.1f else 0f).coerceAtLeast(0.28f)
        },
        tween(420),
        label = "sectionAlpha"
    )
    val blur by animateDpAsState(
        if (active) 0.dp else (distance * 0.9f).coerceAtMost(3.2f).dp,
        tween(520),
        label = "sectionBlur"
    )
    val container by animateColorAsState(
        if (active) accentColor else contentColor.copy(alpha = 0.08f),
        tween(420),
        label = "sectionContainer"
    )
    val text by animateColorAsState(
        if (active) onAccentColor else contentColor,
        tween(420),
        label = "sectionText"
    )
    val dot = sectionColor(section.kind, accentColor)

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .blur(blur, BlurredEdgeTreatment.Unbounded)
            .clip(CircleShape)
            .background(container)
            .drawBehind {
                // How far through the current part we are: a thin line along the bottom.
                val p = progress()
                if (p > 0f) {
                    val h = 2.5.dp.toPx()
                    drawRoundRect(
                        color = onAccentColor.copy(alpha = 0.55f),
                        topLeft = Offset(size.height / 2f, size.height - h - 3.dp.toPx()),
                        size = Size((size.width - size.height) * p, h),
                        cornerRadius = CornerRadius(h / 2f)
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(6.dp)
                    .background(if (active) onAccentColor else dot, CircleShape)
            )
            Text(
                text = section.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                ),
                color = text,
                maxLines = 1,
            )
        }
    }
}

/**
 * Just the part playing now, for face-to-face mode (sits beside the centre divider). The label
 * swaps with a blurred scale-and-fade as the song moves between parts.
 */
@Composable
internal fun CurrentSectionChip(
    structure: SongStructure,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long?,
    accentColor: Color,
    onAccentColor: Color,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val currentIndex by rememberCurrentSectionIndex(structure, playbackPositionFlow, lyricsSyncOffset, positionOverrideMs)
    val section = structure.sections.getOrNull(currentIndex) ?: return
    AnimatedContent(
        targetState = section,
        transitionSpec = {
            (fadeIn(tween(420)) + scaleIn(initialScale = 0.82f, animationSpec = tween(460)))
                .togetherWith(fadeOut(tween(260)) + scaleOut(targetScale = 1.12f, animationSpec = tween(300)))
        },
        contentKey = { it.startMs },
        modifier = modifier,
        label = "currentSection"
    ) { shown ->
        val blur by transition.animateDp(
            transitionSpec = { tween(420) },
            label = "currentSectionBlur"
        ) { state -> if (state == EnterExitState.Visible) 0.dp else 10.dp }
        Row(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .blur(blur, BlurredEdgeTreatment.Unbounded)
                .background(accentColor.copy(alpha = 0.18f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(6.dp)
                    .background(sectionColor(shown.kind, accentColor), CircleShape)
            )
            Text(
                text = shown.label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small colour per kind of part, taken from the accent so it suits every theme. */
private fun sectionColor(kind: SongSectionKind, accent: Color): Color = when (kind) {
    SongSectionKind.CHORUS, SongSectionKind.HOOK, SongSectionKind.POST_CHORUS -> accent
    SongSectionKind.PRE_CHORUS -> accent.copy(alpha = 0.75f)
    SongSectionKind.VERSE -> accent.copy(alpha = 0.5f)
    SongSectionKind.BRIDGE, SongSectionKind.BREAKDOWN, SongSectionKind.DROP -> accent.copy(alpha = 0.9f)
    else -> accent.copy(alpha = 0.3f)
}
