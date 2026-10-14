package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.MutatorMutex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Centralizes sheet motion updates so animation/snap logic lives in one place.
 *
 * Rules that keep the sheet from getting stuck part-way:
 *  - An expand/collapse already running toward the same target is not restarted, so the
 *    drag-end fling (which carries the finger's speed) is not replaced by the state-change
 *    effect's zero-velocity call.
 *  - When the collapsed anchor moves mid-animation (nav bar appearing / resizing), the running
 *    animation is retargeted with its current speed instead of being cancelled by a snap.
 */
internal class SheetMotionController(
    private val translationY: Animatable<Float, AnimationVector1D>,
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val mutex: MutatorMutex,
    private val defaultAnimationSpec: AnimationSpec<Float>,
    private val expandedY: Float = 0f
) {
    /** Target of the expand/collapse in flight, or null when none is running. */
    private var inFlightTarget: Boolean? = null
    private var inFlightCollapsedY = Float.NaN
    private var inFlightCanExpand = true
    private var inFlightSpec: AnimationSpec<Float> = defaultAnimationSpec
    private var token = 0

    init {
        // A release velocity can push a spring past its end; the content fraction never leaves
        // 0..1 (the sheet's Y may still rubber-band a little, as it does while dragging).
        expansionFraction.updateBounds(lowerBound = 0f, upperBound = 1f)
    }

    suspend fun animateTo(
        targetExpanded: Boolean,
        canExpand: Boolean,
        collapsedY: Float,
        animationSpec: AnimationSpec<Float> = defaultAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        val targetFraction = if (canExpand && targetExpanded) 1f else 0f
        val targetY = if (targetExpanded) expandedY else collapsedY
        val velocityScale = (collapsedY - expandedY).coerceAtLeast(1f)

        if (
            translationY.value == targetY &&
            expansionFraction.value == targetFraction &&
            !translationY.isRunning &&
            !expansionFraction.isRunning
        ) {
            return
        }
        // Same destination already on its way: let it finish with its own speed.
        if (inFlightTarget == targetExpanded &&
            inFlightCollapsedY == collapsedY &&
            inFlightCanExpand == canExpand
        ) {
            return
        }

        val mine = ++token
        inFlightTarget = targetExpanded
        inFlightCollapsedY = collapsedY
        inFlightCanExpand = canExpand
        inFlightSpec = animationSpec
        try {
            mutex.mutate {
                coroutineScope {
                    launch {
                        translationY.animateTo(
                            targetValue = targetY,
                            initialVelocity = initialVelocity,
                            animationSpec = animationSpec
                        )
                    }
                    launch {
                        // Fraction runs opposite to Y (down = collapsing = fraction falling).
                        expansionFraction.animateTo(
                            targetValue = targetFraction,
                            initialVelocity = -initialVelocity / velocityScale,
                            animationSpec = animationSpec
                        )
                    }
                }
            }
        } finally {
            if (token == mine) inFlightTarget = null
        }
    }

    suspend fun stop() {
        inFlightTarget = null
        token++
        mutex.mutate {
            translationY.stop()
            expansionFraction.stop()
        }
    }

    suspend fun snapTo(translationYValue: Float, expansionFractionValue: Float) {
        inFlightTarget = null
        token++
        mutex.mutate {
            translationY.snapTo(translationYValue)
            expansionFraction.snapTo(expansionFractionValue)
        }
    }

    suspend fun snapCollapsed(collapsedY: Float) {
        snapTo(
            translationYValue = collapsedY,
            expansionFractionValue = 0f
        )
    }

    suspend fun syncToExpansion(collapsedY: Float) {
        val target = inFlightTarget
        if (target != null) {
            // The anchor moved under a running expand/collapse: retarget, keep the speed.
            if (inFlightCollapsedY != collapsedY) {
                animateTo(
                    targetExpanded = target,
                    canExpand = inFlightCanExpand,
                    collapsedY = collapsedY,
                    animationSpec = inFlightSpec,
                    initialVelocity = translationY.velocity
                )
            }
            return
        }
        val adjustedY = collapsedY + (expandedY - collapsedY) * expansionFraction.value
        if (translationY.value == adjustedY && !translationY.isRunning) return
        mutex.mutate {
            translationY.snapTo(adjustedY)
        }
    }
}
