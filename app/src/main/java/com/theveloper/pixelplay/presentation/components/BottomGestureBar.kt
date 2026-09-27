package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A bottom-anchored gesture bar divided into two equal zones:
 * - Left zone: opens Audio Output / Bluetooth sheet on swipe up or tap.
 * - Right zone: interactively moves and opens Playback Queue sheet on swipe up or tap.
 */
@Composable
fun BottomGestureBar(
    onOpenAudioOutput: () -> Unit,
    onOpenQueue: () -> Unit,
    onQueueDragStart: () -> Unit = {},
    onQueueDrag: (Float) -> Unit = {},
    onQueueRelease: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 36.dp.toPx() } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Gesture Zone (Audio Output / Bluetooth)
        LeftGestureZone(
            modifier = Modifier
                .weight(1f)
                .height(84.dp),
            onOpenAudioOutput = onOpenAudioOutput,
            swipeThresholdPx = swipeThresholdPx,
            enabled = enabled
        )

        // Right Gesture Zone (Playback Queue with interactive drag)
        RightGestureZone(
            modifier = Modifier
                .weight(1f)
                .height(84.dp),
            onOpenQueue = onOpenQueue,
            onQueueDragStart = onQueueDragStart,
            onQueueDrag = onQueueDrag,
            onQueueRelease = onQueueRelease,
            swipeThresholdPx = swipeThresholdPx,
            enabled = enabled
        )
    }
}

@Composable
private fun LeftGestureZone(
    onOpenAudioOutput: () -> Unit,
    swipeThresholdPx: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val currentOnOpen by rememberUpdatedState(onOpenAudioOutput)

    Box(
        modifier = modifier
            .semantics {
                this.contentDescription = "Swipe up to open Audio Output settings"
                onClick(label = "Swipe up") {
                    if (enabled) {
                        currentOnOpen()
                        true
                    } else {
                        false
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val deadZonePx = 6.dp.toPx()
                detectTapAndVerticalDrag(
                    onTap = null,
                    onDragStart = { },
                    onVerticalDrag = { _, _ -> },
                    onDragEnd = { totalDragY, velocityY ->
                        val isSwipeThresholdMet = totalDragY < -swipeThresholdPx
                        val isFlick = velocityY < -450f
                        if (isSwipeThresholdMet || isFlick) {
                            currentOnOpen()
                        }
                    },
                    filterStart = { offset ->
                        // Exclude dead zone around the center boundary
                        offset.x <= (size.width - deadZonePx)
                    }
                )
            }
    )
}

@Composable
private fun RightGestureZone(
    onOpenQueue: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    swipeThresholdPx: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val currentOnOpen by rememberUpdatedState(onOpenQueue)
    val currentOnDragStart by rememberUpdatedState(onQueueDragStart)
    val currentOnDrag by rememberUpdatedState(onQueueDrag)
    val currentOnRelease by rememberUpdatedState(onQueueRelease)

    Box(
        modifier = modifier
            .semantics {
                this.contentDescription = "Swipe up to open Playback Queue"
                onClick(label = "Swipe up") {
                    if (enabled) {
                        currentOnOpen()
                        true
                    } else {
                        false
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val deadZonePx = 6.dp.toPx()
                detectTapAndVerticalDrag(
                    onTap = null,
                    onDragStart = { currentOnDragStart() },
                    onVerticalDrag = { _, dragAmount -> currentOnDrag(dragAmount) },
                    onDragEnd = { totalDragY, velocityY ->
                        val isSwipeThresholdMet = totalDragY < -swipeThresholdPx
                        val isFlick = velocityY < -450f
                        if (isSwipeThresholdMet || isFlick) {
                            currentOnOpen()
                        } else {
                            currentOnRelease(totalDragY, velocityY)
                        }
                    },
                    onDragCancel = {
                        currentOnRelease(0f, 0f)
                    },
                    filterStart = { offset ->
                        // Exclude dead zone around the center boundary
                        offset.x >= deadZonePx
                    }
                )
            }
    )
}

/**
 * Handles unified tap and dominant vertical drag gestures within a pointer scope.
 * Only UPWARD vertical movements are consumed for swipe-up gestures.
 * Downward drags and horizontal swipes are NOT consumed so sheet collapse and carousel gestures work.
 * Taps are only consumed if an explicit onTap handler is provided.
 *
 * Once a drag has started it is always finished: [onDragEnd] is called exactly once, whether the
 * finger lifts normally, a child consumed the up event, the pointer vanished from the stream, or
 * the gesture coroutine was cancelled (e.g. the pointerInput restarted). Without this, an
 * interactive drag (the queue sheet) could be left half open.
 */
internal suspend fun PointerInputScope.detectTapAndVerticalDrag(
    onTap: (() -> Unit)? = null,
    onDragStart: (Offset) -> Unit = {},
    onVerticalDrag: (change: PointerInputChange, dragAmount: Float) -> Unit = { _, _ -> },
    onDragEnd: (totalDragY: Float, velocityY: Float) -> Unit = { _, _ -> },
    onDragCancel: () -> Unit = {},
    filterStart: (Offset) -> Boolean = { true }
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!filterStart(down.position)) return@awaitEachGesture

        val pointerId = down.id
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var totalDragY = 0f
        var totalDragX = 0f
        var isDragging = false
        var dragFinished = false

        fun finishDrag(velocityY: Float) {
            if (isDragging && !dragFinished) {
                dragFinished = true
                onDragEnd(totalDragY, velocityY)
            }
        }

        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }

                // Pointer gone from the stream: treat as a release.
                if (change == null) {
                    finishDrag(velocityTracker.calculateVelocity().y)
                    break
                }

                // Finger lifted. Checked with `pressed`, not changedToUp(), so a child that
                // consumed the up event (a button under the finger) can't leave us mid-drag.
                if (!change.pressed) {
                    if (isDragging) {
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        finishDrag(velocityTracker.calculateVelocity().y)
                    } else if (onTap != null && !change.isConsumed) {
                        change.consume()
                        onTap()
                    }
                    break
                }

                if (!isDragging && change.isConsumed) {
                    // A child (slider, pager, scroller) already owns this gesture.
                    onDragCancel()
                    break
                }

                // Raw delta: once we own the drag, keep following the finger even if a child
                // also consumed the move.
                val delta = change.position - change.previousPosition
                totalDragX += delta.x
                totalDragY += delta.y
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!isDragging) {
                    val touchSlop = viewConfiguration.touchSlop
                    if (totalDragY < -touchSlop && abs(totalDragY) > abs(totalDragX) * 1.1f) {
                        isDragging = true
                        change.consume()
                        onDragStart(down.position)
                        onVerticalDrag(change, totalDragY)
                    } else if (totalDragY > touchSlop && totalDragY > abs(totalDragX)) {
                        // Downward motion: user wants to collapse player sheet, let parent handle
                        onDragCancel()
                        break
                    } else if (abs(totalDragX) > touchSlop) {
                        // Dominant horizontal motion: cancel so carousel / predictive back can handle
                        onDragCancel()
                        break
                    }
                } else {
                    change.consume()
                    onVerticalDrag(change, delta.y)
                }
            }
        } finally {
            // Cancelled mid-drag (pointerInput restarted, node detached): still release so the
            // sheet snaps open or closed instead of freezing where the finger left it.
            finishDrag(0f)
        }
    }
}

/**
 * Swipe up from the left or right side of the player, anywhere *below the album cover*:
 * left opens Audio output, right drags the queue up with the finger (and opens it).
 *
 * Unlike [BottomGestureBar] this is attached to the player's root, not laid over it, so it
 * never blocks anything: the album cover's own swipes, buttons and sliders get the touch first
 * and only an upward, mostly vertical drag that nothing else used opens a sheet. Starting on
 * the cover itself does nothing here, so the cover keeps all its gestures.
 *
 * @param zoneTopPx given the root's height, the y (px, in the root) where the zone starts.
 */
fun Modifier.sideSwipeUpGestures(
    enabled: Boolean,
    zoneTopPx: (height: Float) -> Float,
    onOpenAudioOutput: () -> Unit,
    onOpenQueue: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
): Modifier = composed {
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 32.dp.toPx() } }
    val centreDeadZonePx = remember(density) { with(density) { 10.dp.toPx() } }
    val zoneTop by rememberUpdatedState(zoneTopPx)
    val openAudio by rememberUpdatedState(onOpenAudioOutput)
    val openQueue by rememberUpdatedState(onOpenQueue)
    val dragStart by rememberUpdatedState(onQueueDragStart)
    val drag by rememberUpdatedState(onQueueDrag)
    val release by rememberUpdatedState(onQueueRelease)
    val isEnabled by rememberUpdatedState(enabled)
    // Keyed on Unit, not `enabled`: starting a queue drag shows the queue sheet, which flips
    // `enabled` to false. Keying on it restarted the pointerInput mid-drag, cancelling the
    // gesture before release, so the queue froze half open. Enabled is now only checked when
    // a new gesture starts; a drag in progress always runs to its release.
    pointerInput(Unit) {
        var rightSide = false
        var queueDragStarted = false
        detectTapAndVerticalDrag(
            onTap = null,
            onDragStart = {
                if (rightSide) {
                    queueDragStarted = true
                    dragStart()
                }
            },
            onVerticalDrag = { _, amount -> if (rightSide) drag(amount) },
            onDragEnd = { totalDragY, velocityY ->
                val opened = totalDragY < -swipeThresholdPx || velocityY < -450f
                if (rightSide) {
                    if (opened) openQueue() else release(totalDragY, velocityY)
                    queueDragStarted = false
                } else if (opened) {
                    openAudio()
                }
            },
            onDragCancel = {
                if (queueDragStarted) release(0f, 0f)
                queueDragStarted = false
            },
            filterStart = { offset ->
                val half = size.width / 2f
                rightSide = offset.x >= half
                queueDragStarted = false
                isEnabled &&
                    offset.y >= zoneTop(size.height.toFloat()) &&
                    kotlin.math.abs(offset.x - half) > centreDeadZonePx
            }
        )
    }
}
