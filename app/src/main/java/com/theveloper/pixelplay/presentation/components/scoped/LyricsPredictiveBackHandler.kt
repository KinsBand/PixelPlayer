package com.theveloper.pixelplay.presentation.components.scoped

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Predictive back for the lyrics sheet.
 *
 * [progress] should be the same Animatable that drives the sheet's own enter animation, so a
 * gesture started while the sheet is still springing in simply takes over (Animatable's
 * mutator cancels the enter spring) instead of two animations writing the same value and
 * flickering.
 *
 * Once a gesture commits, [onBack] is guaranteed to run exactly once — even if the finishing
 * animation is interrupted — and the handler stops intercepting Back, so a second press while
 * the sheet animates out goes to the next handler (collapse player) instead of being swallowed.
 */
@Composable
fun LyricsPredictiveBackHandler(
    enabled: Boolean,
    onProgressChanged: (Float) -> Unit,
    animationDurationMs: Int = 350,
    progress: Animatable<Float, AnimationVector1D>? = null,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val progressAnim = progress ?: remember { Animatable(0f) }
    val latestOnBack by rememberUpdatedState(onBack)
    var committed by remember { mutableStateOf(false) }
    // Re-arm when the sheet is shown again (e.g. reopened while it was animating out).
    LaunchedEffect(enabled) { if (enabled) committed = false }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = enabled && !committed) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    progressAnim.snapTo(backEvent.progress)
                    onProgressChanged(backEvent.progress)
                }
                committed = true
                scope.launch {
                    try {
                        progressAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(animationDurationMs / 2)
                        ) { onProgressChanged(value) }
                    } finally {
                        withContext(NonCancellable) {
                            onProgressChanged(1f)
                            latestOnBack()
                        }
                    }
                }
            } catch (_: CancellationException) {
                scope.launch {
                    try {
                        progressAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(animationDurationMs)
                        ) { onProgressChanged(value) }
                    } catch (_: CancellationException) {
                        // A new gesture took over; it owns the progress now.
                    }
                }
            }
        }
    } else {
        BackHandler(enabled = enabled && !committed) {
            committed = true
            onBack()
        }
    }
}
