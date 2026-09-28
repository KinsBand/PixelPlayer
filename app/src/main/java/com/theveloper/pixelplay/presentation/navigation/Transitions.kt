package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import com.theveloper.pixelplay.ui.theme.MotionTokens

// Standardized M3 Easing Curves
val M3EmphasizedEasing: CubicBezierEasing = MotionTokens.EmphasizedEasing
val EmphasizedEasing: CubicBezierEasing = MotionTokens.EmphasizedEasing
val EmphasizedDecelerateEasing: CubicBezierEasing = MotionTokens.EmphasizedDecelerateEasing
val EmphasizedAccelerateEasing: CubicBezierEasing = MotionTokens.EmphasizedAccelerateEasing

// Unified durations standardized to 350ms (MotionTokens.DurationMedium3)
const val AOSP_TRANSITION_DURATION = MotionTokens.DurationMedium3
const val TRANSITION_DURATION = MotionTokens.DurationMedium3
const val NAV_TRANSITION_DURATION = MotionTokens.DurationMedium3

// Equivale a sud_slide_next_in
fun aospSharedAxisEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it / 3 }, // Arranca desde 1/3 de la pantalla hacia la derecha
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing)
    )
}

// Equivale a sud_slide_next_out
fun aospSharedAxisExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it / 3 }, // Se desplaza 1/3 de la pantalla hacia la izquierda
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing)
    ) + scaleOut(
        targetScale = 0.92f, // Escala hacia abajo para dar efecto de profundidad sin desaparecer por completo
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing)
    ) + fadeOut(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing)
    )
}

// Equivale a sud_slide_back_in (cuando volvés atrás, la pantalla previa reaparece desde la izquierda)
fun aospSharedAxisPopEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { -it / 3 }, // Reaparece desde 1/3 de la izquierda
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing)
    )
}

// Equivale a sud_slide_back_out (la pantalla secundaria se destruye deslizándose a la derecha)
fun aospSharedAxisPopExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it }, // Se desliza completamente fuera de la pantalla (100%)
        animationSpec = tween(
            durationMillis = AOSP_TRANSITION_DURATION,
            easing = MotionTokens.EmphasizedAccelerateEasing
        )
    ) + scaleOut(
        targetScale = 0.85f, // Se achica más (efecto de profundidad más pronunciado)
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing)
    ) + fadeOut(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing)
    )
}

// Push: Enter from Right — slides in 50% of screen width + slight scale up
fun enterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.5f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing),
    initialScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing)
)

// Push: Exit to Left — recedes 25% (parallax)
fun exitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing)
)

// Pop: Enter from Left — parallax slide-in 25% + subtle scale up
fun popEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.25f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing),
    initialScale = 0.95f
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedDecelerateEasing)
)

// Pop: Exit to Right — slides out full 100% + slight scale down and synchronized fadeout
fun popExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing),
    targetOffsetX = { it }
) + scaleOut(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing),
    targetScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION, easing = MotionTokens.EmphasizedAccelerateEasing)
)

// Unified Screen Transition aliases conforming to Interface Contract
fun screenEnterTransition(): EnterTransition = enterTransition()
fun screenExitTransition(): ExitTransition = exitTransition()
fun screenPopEnterTransition(): EnterTransition = popEnterTransition()
fun screenPopExitTransition(): ExitTransition = popExitTransition()
