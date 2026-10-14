package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import com.theveloper.pixelplay.ui.theme.MotionTokens

/*
 * Screen-to-screen motion.
 *
 * A plain crossfade (what Spotify does) has both screens half-visible at once, so text and
 * covers "ghost" through each other mid-way. These use Material's *fade through* timing
 * instead: the outgoing screen is gone in the first 30% of the transition and only then does
 * the incoming one fade in, so there is never a double exposure.
 *
 *  - Tabs (Home / Radio / Search / Friends / Library): fade through + a small 94% → 100%
 *    scale-up on the incoming screen.
 *  - Pushing / popping a screen: shared axis X — the same fade through plus a short horizontal
 *    drift (8% of the width) that says "forward" or "back".
 *
 * Cheap by design: only alpha, scale and a small placement offset animate (all draw-phase /
 * placement work, no relayout), and the outgoing screen is fully transparent — so skipped by
 * the renderer — for 70% of the transition. 300 ms total, scaled by the system animator setting.
 */

// Standardized M3 Easing Curves (kept for existing callers)
val M3EmphasizedEasing: CubicBezierEasing = MotionTokens.EmphasizedEasing
val EmphasizedEasing: CubicBezierEasing = MotionTokens.EmphasizedEasing
val EmphasizedDecelerateEasing: CubicBezierEasing = MotionTokens.EmphasizedDecelerateEasing
val EmphasizedAccelerateEasing: CubicBezierEasing = MotionTokens.EmphasizedAccelerateEasing

/** Whole transition. */
const val AOSP_TRANSITION_DURATION = MotionTokens.DurationMedium2 // 300 ms
const val TRANSITION_DURATION = AOSP_TRANSITION_DURATION
const val NAV_TRANSITION_DURATION = AOSP_TRANSITION_DURATION

/** The outgoing screen fades out over the first 30%… */
private const val FADE_OUT_DURATION = 90
/** …and the incoming one fades in over the remaining 70%. */
private const val FADE_IN_DELAY = FADE_OUT_DURATION
private const val FADE_IN_DURATION = AOSP_TRANSITION_DURATION - FADE_IN_DELAY

/** Horizontal drift for pushes / pops, as a fraction of the screen width (~30 dp on a phone). */
private const val AXIS_DRIFT_FRACTION = 0.08f
/** Starting scale of a tab coming in. */
private const val FADE_THROUGH_START_SCALE = 0.94f

private fun fadeOutSpec(): FiniteAnimationSpec<Float> =
    tween(durationMillis = FADE_OUT_DURATION, easing = MotionTokens.StandardAccelerateEasing)

private fun fadeInSpec(): FiniteAnimationSpec<Float> =
    tween(
        durationMillis = FADE_IN_DURATION,
        delayMillis = FADE_IN_DELAY,
        easing = MotionTokens.StandardDecelerateEasing
    )

private fun driftSpec(): FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedEasing)

private fun drift(width: Int): Int = (width * AXIS_DRIFT_FRACTION).toInt()

// ---------------------------------------------------------------------------------------------
// Fade through — bottom-nav tab switches
// ---------------------------------------------------------------------------------------------

fun fadeThroughEnter(): EnterTransition =
    fadeIn(animationSpec = fadeInSpec()) +
        scaleIn(
            initialScale = FADE_THROUGH_START_SCALE,
            animationSpec = tween(
                durationMillis = FADE_IN_DURATION,
                delayMillis = FADE_IN_DELAY,
                easing = MotionTokens.EmphasizedDecelerateEasing
            )
        )

fun fadeThroughExit(): ExitTransition = fadeOut(animationSpec = fadeOutSpec())

// ---------------------------------------------------------------------------------------------
// Shared axis X — pushing and popping screens
// ---------------------------------------------------------------------------------------------

/** New screen comes in: drifts in from the right while fading in. */
fun aospSharedAxisEnter(): EnterTransition =
    slideInHorizontally(animationSpec = driftSpec(), initialOffsetX = { drift(it) }) +
        fadeIn(animationSpec = fadeInSpec())

/** Screen being covered: drifts left while it fades out. */
fun aospSharedAxisExit(): ExitTransition =
    slideOutHorizontally(animationSpec = driftSpec(), targetOffsetX = { -drift(it) }) +
        fadeOut(animationSpec = fadeOutSpec())

/** Going back: the previous screen drifts in from the left. */
fun aospSharedAxisPopEnter(): EnterTransition =
    slideInHorizontally(animationSpec = driftSpec(), initialOffsetX = { -drift(it) }) +
        fadeIn(animationSpec = fadeInSpec())

/** Going back: the top screen drifts right while it fades out. */
fun aospSharedAxisPopExit(): ExitTransition =
    slideOutHorizontally(animationSpec = driftSpec(), targetOffsetX = { drift(it) }) +
        fadeOut(animationSpec = fadeOutSpec())

// Aliases kept so older call sites keep compiling; all share the same motion.
fun enterTransition(): EnterTransition = aospSharedAxisEnter()
fun exitTransition(): ExitTransition = aospSharedAxisExit()
fun popEnterTransition(): EnterTransition = aospSharedAxisPopEnter()
fun popExitTransition(): ExitTransition = aospSharedAxisPopExit()

fun screenEnterTransition(): EnterTransition = aospSharedAxisEnter()
fun screenExitTransition(): ExitTransition = aospSharedAxisExit()
fun screenPopEnterTransition(): EnterTransition = aospSharedAxisPopEnter()
fun screenPopExitTransition(): ExitTransition = aospSharedAxisPopExit()
