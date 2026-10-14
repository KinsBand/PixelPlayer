package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class QueueActionKind { NEXT, QUEUE, SOON }

/** One "added" moment: what was done, and to what. */
@Immutable
data class QueueActionBubbleData(
    val id: Long,
    val kind: QueueActionKind,
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
    /** Soon: how many songs it waits for (null otherwise). */
    val afterSongs: Int? = null,
    /** A whole playlist was queued ([subtitle] then says how many songs). */
    val isPlaylist: Boolean = false,
)

/**
 * Queue / Next / Soon report here; the player sheet shows it as a bubble growing out of the
 * mini player ([QueueActionBubbleHost]). The newest one replaces whatever is showing.
 */
object QueueActionAnnouncer {
    private val _current = MutableStateFlow<QueueActionBubbleData?>(null)
    val current: StateFlow<QueueActionBubbleData?> = _current.asStateFlow()
    private var nextId = 0L

    fun announce(
        kind: QueueActionKind,
        title: String,
        subtitle: String,
        coverUrl: String?,
        afterSongs: Int? = null,
        isPlaylist: Boolean = false,
    ) {
        _current.value = QueueActionBubbleData(++nextId, kind, title, subtitle, coverUrl, afterSongs, isPlaylist)
    }

    internal fun clear(id: Long) {
        if (_current.value?.id == id) _current.value = null
    }
}

/** How long a bubble stays up before it sinks back into the mini player. */
private const val HoldMs = 2_400L

/**
 * Draws the latest [QueueActionAnnouncer] bubble centred above the mini player, joined to it by
 * a short flared neck, so it reads as coming out of the mini player. It grows up out of it, holds,
 * then sinks back. [anchorTopPx] = the mini player's top edge in this layout's coordinates (read in
 * the layout phase only). When the mini player isn't showing ([canShow] false), a short toast says
 * the same thing instead, unless [quiet] (e.g. the queue itself is open and shows the change).
 */
@Composable
internal fun QueueActionBubbleHost(
    anchorTopPx: () -> Float,
    canShow: () -> Boolean,
    quiet: () -> Boolean,
    container: Color,
    content: Color,
) {
    val current by QueueActionAnnouncer.current.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduced = rememberSystemReducedMotion()
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<QueueActionBubbleData?>(null) }
    val latestCanShow by rememberUpdatedState(canShow)
    val latestQuiet by rememberUpdatedState(quiet)
    val actionLabels = mapOf(
        QueueActionKind.NEXT to stringResource(R.string.queue_bubble_next),
        QueueActionKind.QUEUE to stringResource(R.string.queue_bubble_queue),
        QueueActionKind.SOON to stringResource(R.string.queue_bubble_soon),
    )

    LaunchedEffect(current?.id) {
        val bubble = current ?: return@LaunchedEffect
        if (!latestCanShow()) {
            if (!latestQuiet()) {
                android.widget.Toast.makeText(
                    context, "${actionLabels[bubble.kind]} · ${bubble.title}", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            QueueActionAnnouncer.clear(bubble.id)
            return@LaunchedEffect
        }
        shown = bubble
        if (reduced) progress.snapTo(1f) else progress.animateTo(
            1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        )
        delay(HoldMs)
        if (reduced) progress.snapTo(0f) else progress.animateTo(
            0f, tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)
        )
        shown = null
        QueueActionAnnouncer.clear(bubble.id)
    }

    val bubble = shown ?: return
    val shape = remember { BubbleWithNeckShape(neckHeight = 10.dp, cornerRadius = 22.dp) }
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, placeable.height) {
                    // Bottom of the neck overlaps the mini player's top by 2 dp so they join.
                    val y = (anchorTopPx() - placeable.height + 2.dp.toPx()).toInt()
                    placeable.place((constraints.maxWidth - placeable.width) / 2, y)
                }
            }
    ) {
        Column(
            Modifier
                .widthIn(min = 220.dp, max = 340.dp)
                .graphicsLayer {
                    val p = progress.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleX = 0.55f + 0.45f * p
                    scaleY = 0.2f + 0.8f * p.coerceAtMost(1.08f)
                    alpha = p.coerceIn(0f, 1f)
                }
                .clip(shape)
                .background(container)
                .padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            AnimatedContent(
                targetState = bubble,
                contentKey = { it.id },
                transitionSpec = {
                    fadeIn(tween(MotionTokens.DurationShort4)) togetherWith fadeOut(tween(MotionTokens.DurationShort3))
                },
                label = "queueBubbleContent"
            ) { b ->
                Column(Modifier.graphicsLayer { alpha = (progress.value * 1.4f - 0.4f).coerceIn(0f, 1f) }) {
                    val header = buildString {
                        append(actionLabels[b.kind])
                        if (b.kind == QueueActionKind.SOON && b.afterSongs != null && b.afterSongs > 0) append(" · ")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            header,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = GoogleSansRounded),
                            fontWeight = FontWeight.Bold,
                            color = content
                        )
                        if (b.kind == QueueActionKind.SOON && b.afterSongs != null && b.afterSongs > 0) {
                            Text(
                                pluralStringResource(R.plurals.queue_bubble_after_songs, b.afterSongs, b.afterSongs),
                                style = MaterialTheme.typography.labelLarge,
                                color = content.copy(alpha = 0.75f)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmartImage(
                            model = b.coverUrl,
                            contentDescription = null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                b.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
                                fontWeight = FontWeight.SemiBold,
                                color = content,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                b.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = content.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A pill whose bottom centre runs down into a short neck that flares out at its base, like a
 * drop pulled up out of the mini player. The neck takes the bottom [neckHeight] of the bounds.
 */
private class BubbleWithNeckShape(
    private val neckHeight: androidx.compose.ui.unit.Dp,
    private val cornerRadius: androidx.compose.ui.unit.Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val neck = with(density) { neckHeight.toPx() }
        val bodyBottom = (size.height - neck).coerceAtLeast(0f)
        val radius = with(density) { cornerRadius.toPx() }.coerceAtMost(bodyBottom / 2f)
        val cx = size.width / 2f
        val neckHalf = (size.width * 0.16f).coerceAtLeast(neck * 2f)
        val flare = neck * 1.6f
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, bodyBottom, CornerRadius(radius, radius)))
            // Neck: straight down from the body, curving out into a wider base.
            moveTo(cx - neckHalf, bodyBottom - 1f)
            lineTo(cx + neckHalf, bodyBottom - 1f)
            quadraticTo(cx + neckHalf, size.height, cx + neckHalf + flare, size.height)
            lineTo(cx - neckHalf - flare, size.height)
            quadraticTo(cx - neckHalf, size.height, cx - neckHalf, bodyBottom - 1f)
            close()
        }
        return Outline.Generic(path)
    }
}
