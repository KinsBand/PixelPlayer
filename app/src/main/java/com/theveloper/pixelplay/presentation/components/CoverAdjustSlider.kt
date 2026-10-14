package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** One step of the ruler every this many units; a long tick every [MAJOR_EVERY]. */
private const val TICK_EVERY = 5
private const val MAJOR_EVERY = 25

/** Height of the Adjust screen's value bubble and reset button (same as the Apply pill). */
val CoverAdjustControlSize = 56.dp

/**
 * Everything the Adjust screen's slider controls need about the open tool or preset strength:
 * the value, its range and default, how to show it, and where changes go.
 */
@Stable
class CoverSliderSpec(
    val key: String,
    val value: Int,
    val range: IntRange,
    val default: Int,
    val icon: ImageVector,
    val label: String,
    val onValueChange: (Int) -> Unit,
    /** Called with the final value when a drag, fling, reset or accessibility change ends. */
    val onValueChangeFinished: (Int) -> Unit = {},
) {
    val moved: Boolean get() = value != default
}

/**
 * Runs the smooth reset: the value counts back to its default over
 * tween(DurationMedium2, Emphasized), emitting every whole step so the cover, the number and the
 * ruler all glide together. A drag, another reset or a tool switch cancels it where it is.
 */
@Stable
class CoverSliderResetter(private val scope: CoroutineScope) {
    private var job: Job? = null
    /** True while a reset is running (the ruler follows the value exactly instead of gliding). */
    var isResetting by mutableStateOf(false)
        private set

    fun reset(spec: CoverSliderSpec) {
        cancel()
        val from = spec.value
        val to = spec.default
        if (from == to) return
        isResetting = true
        job = scope.launch {
            try {
                var last = from
                Animatable(from.toFloat()).animateTo(
                    to.toFloat(),
                    tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)
                ) {
                    val stepped = value.roundToInt()
                    if (stepped != last) {
                        last = stepped
                        spec.onValueChange(stepped)
                    }
                }
                if (last != to) spec.onValueChange(to)
                spec.onValueChangeFinished(to)
            } finally {
                isResetting = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        isResetting = false
    }
}

@Composable
fun rememberCoverSliderResetter(): CoverSliderResetter {
    val scope = rememberCoroutineScope()
    return remember(scope) { CoverSliderResetter(scope) }
}

/**
 * The value ruler on its own, with no pill behind it: ticks scroll under a fixed centre line and
 * fade out towards both edges. Drag or fling; values are whole steps clamped to the range. An
 * outside change glides the ruler (or follows exactly during a reset).
 */
@Composable
fun CoverAdjustRuler(
    spec: CoverSliderSpec,
    resetter: CoverSliderResetter,
    modifier: Modifier = Modifier,
) {
    AdjustRuler(
        value = spec.value,
        range = spec.range,
        default = spec.default,
        label = spec.label,
        onValueChange = spec.onValueChange,
        onValueChangeFinished = spec.onValueChangeFinished,
        followExactly = resetter.isResetting,
        onDragStart = resetter::cancel,
        modifier = modifier
            .fillMaxWidth()
            .height(CoverAdjustControlSize)
    )
}

/** Left of Apply: the tool's icon while unchanged, its value once moved. */
@Composable
fun CoverAdjustValueBubble(spec: CoverSliderSpec, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(CoverAdjustControlSize)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .semantics(mergeDescendants = true) {
                contentDescription = if (spec.moved) "${spec.label} ${spec.value}" else "${spec.label}, unchanged"
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = spec.moved,
            transitionSpec = {
                fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized)) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
            },
            label = "adjust_value_bubble"
        ) { showValue ->
            if (showValue) {
                Text(
                    text = spec.value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1
                )
            } else {
                Icon(spec.icon, contentDescription = null, tint = colors.onSurface)
            }
        }
    }
}

/** Right of Apply: glides the value back to its default (dimmed when already there). */
@Composable
fun CoverAdjustResetButton(
    spec: CoverSliderSpec,
    resetter: CoverSliderResetter,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    FilledTonalIconButton(
        onClick = { resetter.reset(spec) },
        enabled = spec.moved,
        modifier = modifier.size(CoverAdjustControlSize),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            disabledContainerColor = colors.surfaceContainerHigh.copy(alpha = 0.6f),
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = "Reset ${spec.label}")
    }
}

@Composable
private fun AdjustRuler(
    value: Int,
    range: IntRange,
    default: Int,
    label: String,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: (Int) -> Unit,
    /** A reset is driving the value frame by frame: follow it exactly instead of gliding. */
    followExactly: Boolean = false,
    /** A finger touched the ruler (cancels a running reset). */
    onDragStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val min = range.first.toFloat()
    val max = range.last.toFloat()

    // Shown position (in units). Outside a drag it follows [value] with a glide.
    val glide = remember { Animatable(value.toFloat()) }
    // True from finger down until the fling has settled (outside changes wait until then).
    var dragging by remember { mutableStateOf(false) }
    var flinging by remember { mutableStateOf(false) }
    var gesture by remember { mutableIntStateOf(0) }
    var dragPosition by remember { mutableFloatStateOf(value.toFloat()) }
    var lastEmitted by remember { mutableIntStateOf(value) }

    LaunchedEffect(value, range.first, range.last) {
        if (dragging) return@LaunchedEffect
        glide.updateBounds(min, max)
        if (value != lastEmitted) {
            lastEmitted = value
            if (followExactly) {
                glide.snapTo(value.toFloat())
            } else {
                glide.animateTo(value.toFloat(), tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized))
            }
        } else if (glide.value.roundToInt() != value) {
            glide.snapTo(value.toFloat())
        }
    }

    fun emit(position: Float) {
        val stepped = position.roundToInt().coerceIn(range)
        if (stepped != lastEmitted) {
            val previous = lastEmitted
            lastEmitted = stepped
            currentOnChange(stepped)
            val crossedMajor = stepped % MAJOR_EVERY == 0 ||
                (previous < default) != (stepped < default) || stepped == default
            if (crossedMajor) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val unitPx = with(androidx.compose.ui.platform.LocalDensity.current) { (8.dp / TICK_EVERY).toPx() }

    val dragState = rememberDraggableState { delta ->
        // The tape moves with the finger: dragging left raises the value.
        dragPosition = (dragPosition - delta / unitPx).coerceIn(min, max)
        emit(dragPosition)
    }

    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = label
                stateDescription = value.toString()
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = min..max,
                    steps = (range.last - range.first - 1).coerceAtLeast(0)
                )
                setProgress { target ->
                    val v = target.roundToInt().coerceIn(range)
                    if (v != value) {
                        lastEmitted = v
                        currentOnChange(v)
                        currentOnFinished(v)
                    }
                    true
                }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                // A touch during a fling catches the tape at once.
                startDragImmediately = flinging,
                onDragStarted = {
                    currentOnDragStart()
                    val id = ++gesture
                    glide.stop()
                    if (id == gesture) {
                        dragPosition = glide.value
                        flinging = false
                        dragging = true
                    }
                },
                onDragStopped = { velocity ->
                    val id = gesture
                    try {
                        glide.updateBounds(min, max)
                        glide.snapTo(dragPosition)
                        flinging = true
                        // Fling: units/s from px/s, opposite direction to the finger.
                        glide.animateDecay(-velocity / unitPx, exponentialDecay(frictionMultiplier = 3f)) {
                            emit(this.value)
                        }
                        val settled = glide.value.roundToInt().coerceIn(range)
                        emit(settled.toFloat())
                        glide.animateTo(settled.toFloat(), spring(stiffness = 800f))
                        currentOnFinished(settled)
                    } finally {
                        // A new touch during the fling owns the state now.
                        if (id == gesture) {
                            flinging = false
                            dragging = false
                        }
                    }
                }
            )
    ) {
        val position = if (dragging && !flinging) dragPosition else glide.value
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minorH = size.height * 0.26f
        val majorH = size.height * 0.4f
        val lo = minOf(default.toFloat(), position)
        val hi = maxOf(default.toFloat(), position)
        val first = (range.first / TICK_EVERY) * TICK_EVERY
        var u = first
        while (u <= range.last) {
            val x = cx + (u - position) * unitPx
            if (x >= -8f && x <= size.width + 8f) {
                val inSpan = u >= lo - 0.01f && u <= hi + 0.01f && hi - lo >= 0.5f
                val major = u % MAJOR_EVERY == 0
                val fade = (1f - abs(x - cx) / (size.width / 2f)).coerceIn(0f, 1f).pow(0.6f)
                val baseH = if (major) majorH else minorH
                val h = if (inSpan) baseH * 1.45f else baseH
                val color = if (inSpan) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f)
                drawLine(
                    color = color.copy(alpha = color.alpha * fade),
                    start = Offset(x, cy - h / 2f),
                    end = Offset(x, cy + h / 2f),
                    strokeWidth = if (inSpan) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            u += TICK_EVERY
        }
        // Fixed centre indicator.
        val indicatorH = size.height * 0.72f
        drawLine(
            color = colors.primary,
            start = Offset(cx, cy - indicatorH / 2f),
            end = Offset(cx, cy + indicatorH / 2f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
