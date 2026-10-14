package com.theveloper.pixelplay.presentation.components.scoped

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.core.view.HapticFeedbackConstantsCompat
import com.theveloper.pixelplay.presentation.components.SwipeStage
import com.theveloper.pixelplay.presentation.components.swipeStage
import com.theveloper.pixelplay.presentation.utils.AppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private enum class QueueDismissDragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

/**
 * Manages swipe-to-dismiss gesture for queue items with a multi-phase approach
 * that prevents accidental dismisses. Mirrors the MiniPlayer dismiss handler logic:
 *
 * 1. TENSION – drag up to [tensionThresholdDp] (60dp) with dampened visual feedback (max 20dp offset).
 *    The item resists movement, making accidental swipes while scrolling very unlikely.
 * 2. SNAPPING – once the tension threshold is exceeded, haptic fires and the item snaps
 *    to the real drag position with a spring animation.
 * 3. FREE_DRAG – the item tracks the finger 1:1 with a stiff spring.
 * 4. On release, if the accumulated drag exceeds 40% of the item width the item is dismissed;
 *    otherwise it bounces back.
 *
 * Left (end-to-start) = dismiss. When [rightEnabled], right swipes go through the same tension
 * phases and pick a stage like the search swipe row ([swipeStage]): past 96 dp = Next, past
 * 176 dp (with [soonEnabled]) = Soon, max 220 dp. Releasing springs back and calls [onRightRelease].
 */
internal class QueueItemDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticView: View,
    private val appHapticsConfig: AppHapticsConfig,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val itemWidthPx: Float,
    private val onDismiss: () -> Unit,
    private val rightEnabled: Boolean = false,
    private val soonEnabled: Boolean = false,
    private val onRightRelease: (SwipeStage) -> Unit = {},
) {
    private val nextPx = 96f * density.density
    private val soonPx = 176f * density.density
    private val maxRightPx = 220f * density.density

    /** Stage of a right swipe in progress (NONE / NEXT / SOON); drives the label behind the row. */
    var rightStage: SwipeStage by mutableStateOf(SwipeStage.NONE)
        private set
    private var dragPhase: QueueDismissDragPhase = QueueDismissDragPhase.IDLE
    private var accumulatedDragX: Float = 0f

    /** Whether the drag has crossed into the commit zone (past dismiss threshold). */
    var isInDismissZone: Boolean by mutableStateOf(false)
        private set

    /** Whether a dismiss animation is currently running. */
    var isDismissing: Boolean by mutableStateOf(false)
        private set

    fun onDragStart() {
        if (isDismissing) return
        dragPhase = QueueDismissDragPhase.TENSION
        accumulatedDragX = 0f
        isInDismissZone = false
        rightStage = SwipeStage.NONE
        scope.launch { offsetAnimatable.stop() }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        if (isDismissing) return
        accumulatedDragX += dragAmount
        // Right swipes only when enabled; otherwise only end-to-start (negative / left) swipes.
        if (accumulatedDragX > 0f && !rightEnabled) {
            accumulatedDragX = 0f
            scope.launch { offsetAnimatable.snapTo(0f) }
            return
        }
        if (accumulatedDragX > maxRightPx) accumulatedDragX = maxRightPx
        val direction = if (accumulatedDragX >= 0f) 1f else -1f

        when (dragPhase) {
            QueueDismissDragPhase.TENSION -> {
                val tensionThresholdPx = 60f * density.density
                if (abs(accumulatedDragX) < tensionThresholdPx) {
                    // Dampened feedback: max 20dp visual offset while in tension zone
                    val maxTensionOffsetPx = 20f * density.density
                    val dragFraction = (abs(accumulatedDragX) / tensionThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = maxTensionOffsetPx * dragFraction
                    scope.launch {
                        offsetAnimatable.snapTo(direction * tensionOffset)
                    }
                } else {
                    dragPhase = QueueDismissDragPhase.SNAPPING
                }
            }

            QueueDismissDragPhase.SNAPPING -> {
                performAppCompatHapticFeedback(
                    hapticView,
                    appHapticsConfig,
                    HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                )
                scope.launch {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                dragPhase = QueueDismissDragPhase.FREE_DRAG
            }

            QueueDismissDragPhase.FREE_DRAG -> {
                // Right side: a stage per threshold, a tick each time it changes.
                val stage = if (accumulatedDragX > 0f) swipeStage(accumulatedDragX, nextPx, soonPx, soonEnabled) else SwipeStage.NONE
                if (stage != rightStage) {
                    rightStage = stage
                    performAppCompatHapticFeedback(
                        hapticView,
                        appHapticsConfig,
                        if (stage != SwipeStage.NONE) HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                        else HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE
                    )
                }
                val dismissThreshold = itemWidthPx * 0.40f
                val nowInZone = accumulatedDragX < 0f && abs(accumulatedDragX) > dismissThreshold
                if (nowInZone != isInDismissZone) {
                    isInDismissZone = nowInZone
                    performAppCompatHapticFeedback(
                        hapticView,
                        appHapticsConfig,
                        if (nowInZone) HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                        else HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE
                    )
                }
                scope.launch {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    )
                }
            }

            QueueDismissDragPhase.IDLE -> Unit
        }
    }

    fun onDragEnd() {
        if (isDismissing) return
        val wasFree = dragPhase == QueueDismissDragPhase.FREE_DRAG
        dragPhase = QueueDismissDragPhase.IDLE
        val dismissThreshold = itemWidthPx * 0.40f

        if (accumulatedDragX > 0f) {
            // Right swipe: spring back, then Next / Soon (only once past the tension zone).
            val stage = if (wasFree) swipeStage(accumulatedDragX, nextPx, soonPx, soonEnabled) else SwipeStage.NONE
            rightStage = SwipeStage.NONE
            scope.launch {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            if (stage == SwipeStage.NEXT || stage == SwipeStage.SOON) {
                performAppCompatHapticFeedback(hapticView, appHapticsConfig, HapticFeedbackConstantsCompat.GESTURE_END)
                onRightRelease(stage)
            }
            return
        }

        if (abs(accumulatedDragX) > dismissThreshold) {
            // Dismiss: animate off-screen to the left
            isDismissing = true
            performAppCompatHapticFeedback(
                hapticView,
                appHapticsConfig,
                HapticFeedbackConstantsCompat.GESTURE_END
            )
            scope.launch {
                offsetAnimatable.animateTo(
                    targetValue = -itemWidthPx,
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutSlowInEasing
                    )
                )
                onDismiss()
                // Delay resetting the translation to give the VM time to process the removal.
                // If the item is removed, this coroutine is cancelled. If not, it slides back.
                kotlinx.coroutines.delay(1000)
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                isDismissing = false
                isInDismissZone = false
            }
        } else {
            // Spring back
            isInDismissZone = false
            scope.launch {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    fun onDragCancel() {
        if (isDismissing) return
        dragPhase = QueueDismissDragPhase.IDLE
        isInDismissZone = false
        rightStage = SwipeStage.NONE
        scope.launch {
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
}

@Composable
internal fun rememberQueueItemDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    hapticView: View,
    appHapticsConfig: AppHapticsConfig,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    itemWidthPx: Float,
    onDismiss: () -> Unit
): QueueItemDismissGestureHandler {
    return remember(scope, density, hapticView, appHapticsConfig, offsetAnimatable, itemWidthPx, onDismiss) {
        QueueItemDismissGestureHandler(
            scope = scope,
            density = density,
            hapticView = hapticView,
            appHapticsConfig = appHapticsConfig,
            offsetAnimatable = offsetAnimatable,
            itemWidthPx = itemWidthPx,
            onDismiss = onDismiss
        )
    }
}
