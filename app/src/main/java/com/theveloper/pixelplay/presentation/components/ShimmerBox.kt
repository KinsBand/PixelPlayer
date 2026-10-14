package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

/**
 * Placeholder block with a soft highlight that sweeps across it, left to right, then rests a
 * beat. The sweep is sized to the box (the old fixed 1000px gradient barely moved on small
 * blocks), and the value is read only in the draw phase, so frames redraw without recomposing.
 * At animator duration scale 0 (Remove animations) the block stays still.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    // Use MaterialTheme colors for proper dark/light mode support
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val shimmerColors = remember(baseColor, highlightColor) {
        listOf(baseColor, highlightColor, baseColor)
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SHIMMER_SWEEP_MS,
                delayMillis = SHIMMER_REST_MS,
                easing = com.theveloper.pixelplay.ui.theme.MotionTokens.Standard
            ),
        ),
        label = "shimmerSweep"
    )

    Box(
        modifier = modifier.drawBehind {
            drawRect(baseColor)
            val band = size.width.coerceAtLeast(size.height) * 0.9f
            // From fully off the left edge to fully off the right edge.
            val x = -band + (size.width + band * 2f) * progress.value
            drawRect(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(x - band / 2f, 0f),
                    end = Offset(x + band / 2f, size.height * 0.4f)
                )
            )
        }
    )
}

private const val SHIMMER_SWEEP_MS = 1100
private const val SHIMMER_REST_MS = 250
