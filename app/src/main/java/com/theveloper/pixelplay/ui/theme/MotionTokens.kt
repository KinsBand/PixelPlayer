package com.theveloper.pixelplay.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Material 3 Motion Tokens for PixelPlayer.
 *
 * Implements canonical Material Design 3 easing curves and duration tokens
 * to guarantee coherent, expressive, and predictable animations across
 * navigation, dialogs, bottom sheets, and chrome components.
 */
object MotionTokens {
    // Standard Material 3 cubic bezier easing curves
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val StandardAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    // Shorthand aliases for convenience
    val Emphasized = EmphasizedEasing
    val EmphasizedDecelerate = EmphasizedDecelerateEasing
    val EmphasizedAccelerate = EmphasizedAccelerateEasing
    val Standard = StandardEasing
    val StandardDecelerate = StandardDecelerateEasing
    val StandardAccelerate = StandardAccelerateEasing

    // Material 3 Duration Tokens (in milliseconds)
    const val DurationShort1 = 50
    const val DurationShort2 = 100
    const val DurationShort3 = 150
    const val DurationShort4 = 200
    const val DurationMedium1 = 250
    const val DurationMedium2 = 300
    const val DurationMedium3 = 350
    const val DurationMedium4 = 400
    const val DurationLong1 = 400
    const val DurationLong2 = 500
}
