package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * What the mini player's row needs from the sheet host, without threading it through every
 * layer: the sideways song-swipe offset (cover + title + artist move with the finger), the
 * collapsed sheet's bottom corner radius (the timeline follows that curve) and the song progress.
 * Provided by UnifiedPlayerSheetV2 through [LocalMiniPlayerChrome]; null = none of it.
 */
@Stable
class MiniPlayerChrome(
    val swipeOffset: Animatable<Float, AnimationVector1D>,
    val widthPx: Float,
    val bottomRadius: () -> Dp,
    /** 0..1 of the current song played. */
    val progress: () -> Float,
)

val LocalMiniPlayerChrome = staticCompositionLocalOf<MiniPlayerChrome?> { null }

/**
 * The cover + title + artist group of the mini player follows the song swipe: it slides with
 * the finger and fades as it leaves, then the next song's slides in from the other side.
 * Read in the layer, so a swipe never recomposes the row.
 */
fun Modifier.miniPlayerSwipeContent(chrome: MiniPlayerChrome?): Modifier =
    if (chrome == null) this else graphicsLayer {
        val offset = chrome.swipeOffset.value
        translationX = offset
        alpha = (1f - abs(offset) / (chrome.widthPx * 0.45f)).coerceIn(0f, 1f)
    }

/**
 * A thin song timeline in the cover's colours along the mini player's bottom edge. It starts
 * at the very left, runs around the bottom-left curve, along the bottom and up the bottom-right
 * curve to the very right, so it follows whatever corner radius the player has right now (the
 * path is rebuilt from [MiniPlayerChrome.bottomRadius] each frame it changes, so it morphs with
 * the shape). Progress is read in the draw phase only.
 */
fun Modifier.miniPlayerTimeline(chrome: MiniPlayerChrome?, color: Color, trackColor: Color): Modifier =
    if (chrome == null) this else drawWithCache {
        val path = Path()
        val segment = Path()
        val measure = PathMeasure()
        val stroke = 3.dp.toPx()
        onDrawWithContent {
            drawContent()
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@onDrawWithContent
            val inset = stroke / 2f
            val r = min(chrome.bottomRadius().toPx(), min(h / 2f, w / 2f))
            path.reset()
            if (r <= inset + 0.5f) {
                path.moveTo(0f, h - inset)
                path.lineTo(w, h - inset)
            } else {
                val inner = r - inset
                path.moveTo(inset, h - r)
                path.arcTo(Rect(inset, h - inset - 2 * inner, inset + 2 * inner, h - inset), 180f, -90f, false)
                path.lineTo(w - inset - inner, h - inset)
                path.arcTo(Rect(w - inset - 2 * inner, h - inset - 2 * inner, w - inset, h - inset), 90f, -90f, false)
            }
            drawPath(path, trackColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
            val p = chrome.progress().coerceIn(0f, 1f)
            if (p > 0f) {
                measure.setPath(path, false)
                segment.reset()
                measure.getSegment(0f, measure.length * p, segment, true)
                drawPath(segment, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
    }

/**
 * Sideways swipe on the collapsed mini player changes song: left = next (everything leaves to
 * the left, the next song comes in from the right), right = previous (the other way round).
 * Below the threshold it springs back. A light haptic marks the threshold.
 */
class MiniPlayerSongSwipeHandler(
    private val scope: CoroutineScope,
    private val hapticFeedback: HapticFeedback,
    private val offset: Animatable<Float, AnimationVector1D>,
    private val widthPx: Float,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) {
    private var dragX = 0f
    private var passedThreshold = false
    private var job: Job? = null
    private val threshold get() = widthPx * 0.22f

    fun onDragStart() {
        dragX = offset.value
        passedThreshold = false
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.stop() }
    }

    fun onDrag(delta: Float) {
        dragX += delta
        // Follows the finger 1:1 up to the threshold, then with growing resistance.
        val over = abs(dragX) - threshold
        val shown = if (over <= 0f) dragX else (threshold + over * 0.35f) * if (dragX < 0) -1f else 1f
        val past = abs(dragX) >= threshold
        if (past && !passedThreshold) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        passedThreshold = past
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(shown) }
    }

    fun onDragEnd() {
        val goNext = dragX <= -threshold
        val goPrevious = dragX >= threshold
        dragX = 0f
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!goNext && !goPrevious) {
                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                return@launch
            }
            val out = widthPx * 0.5f * if (goNext) -1f else 1f
            offset.animateTo(out, tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
            if (goNext) onNext() else onPrevious()
            // The new song arrives from the opposite side.
            offset.snapTo(-out * 0.7f)
            offset.animateTo(0f, tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate))
        }
    }

    fun onDragCancel() {
        dragX = 0f
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
}

@Composable
fun rememberMiniPlayerSongSwipeHandler(
    scope: CoroutineScope,
    hapticFeedback: HapticFeedback,
    offset: Animatable<Float, AnimationVector1D>,
    widthPx: Float,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): MiniPlayerSongSwipeHandler {
    val next = rememberUpdatedState(onNext)
    val previous = rememberUpdatedState(onPrevious)
    return remember(scope, hapticFeedback, offset, widthPx) {
        MiniPlayerSongSwipeHandler(scope, hapticFeedback, offset, widthPx, { next.value() }, { previous.value() })
    }
}

fun Modifier.miniPlayerSongSwipeGesture(enabled: Boolean, handler: MiniPlayerSongSwipeHandler): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}
