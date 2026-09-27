package com.theveloper.pixelplay.presentation.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/*
 * Material 3 Expressive touches for the queue sheet:
 *  - a Gemini-style glow that rises from the bottom edge of the screen, following its rounded
 *    corners, while a mix is building the queue or songs are being added. It is drawn behind
 *    the song cards (between the list background and the rows);
 *  - a soft blur + fade where the list meets the header at the top and the floating toolbar at
 *    the bottom, so rows dissolve under them instead of being cut.
 */

/** Animated values for [queueAssistantGlow]; read only in the draw phase. */
@Stable
class QueueGlowState internal constructor(
    internal val intensity: Animatable<Float, *>,
    internal val phase: Animatable<Float, *>
)

/**
 * Rises in with a soft spring while [active], keeps flowing, and sinks away when it stops.
 * The flow animation only runs while the glow is visible.
 */
@Composable
fun rememberQueueGlowState(active: Boolean): QueueGlowState {
    val intensity = remember { Animatable(0f) }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            intensity.animateTo(
                1f,
                spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow)
            )
        } else {
            intensity.animateTo(0f, tween(durationMillis = 900))
        }
    }
    LaunchedEffect(active) {
        if (!active) {
            // Let the colours keep drifting while the glow sinks away, then stop.
            phase.animateTo(phase.value + 0.15f, tween(durationMillis = 900, easing = LinearEasing))
            return@LaunchedEffect
        }
        while (true) {
            phase.animateTo(phase.value + 1f, tween(durationMillis = 5_200, easing = LinearEasing))
        }
    }
    return remember(intensity, phase) { QueueGlowState(intensity, phase) }
}

/**
 * Gemini-style edge glow along the bottom of this element. Put it on the list *after* its
 * background and before its content so it sits behind the rows.
 *
 * @param colors three or more hues that drift along the edge (primary, tertiary, secondary…).
 * @param cornerRadius radius of the screen / sheet corners the rim follows.
 */
fun Modifier.queueAssistantGlow(
    state: QueueGlowState,
    colors: List<Color>,
    cornerRadius: Dp = 32.dp,
    bottomInset: Dp = 0.dp
): Modifier = drawBehind {
    val k = state.intensity.value
    if (k <= 0.005f || colors.isEmpty()) return@drawBehind
    val phase = state.phase.value
    val w = size.width
    val h = size.height - bottomInset.toPx()
    if (w <= 0f || h <= 0f) return@drawBehind

    // 1) The bloom: large soft blobs sitting below the bottom edge, so only their curved tops
    //    show. They slide sideways and breathe, like the Gemini launch glow.
    val rise = h * (0.20f + 0.06f * sin(phase * 2f * PI.toFloat())) * k
    val blobCount = maxOf(colors.size, 3)
    for (i in 0 until blobCount) {
        val color = colors[i % colors.size]
        val t = (i + 0.5f) / blobCount
        val drift = 0.12f * sin((phase + i * 0.37f) * 2f * PI.toFloat())
        val cx = w * (t + drift)
        val radius = w * (0.46f + 0.08f * sin((phase * 1.3f + i * 0.21f) * 2f * PI.toFloat()))
        val cy = h + radius - rise
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = 0.55f * k),
                0.55f to color.copy(alpha = 0.22f * k),
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = radius
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
    }

    // 2) The rim: a light hugging the bottom edge and up the rounded corners, colours flowing
    //    along it. Layered strokes fake a glow without a blur pass.
    val r = cornerRadius.toPx().coerceAtMost(minOf(w, h) / 2f)
    val rimHeight = (r + h * 0.10f) * k
    val path = Path().apply {
        moveTo(0f, h - rimHeight)
        lineTo(0f, h - r)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(0f, h - 2 * r, 2 * r, h),
            startAngleDegrees = 180f,
            sweepAngleDegrees = -90f,
            forceMoveTo = false
        )
        lineTo(w - r, h)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(w - 2 * r, h - 2 * r, w, h),
            startAngleDegrees = 90f,
            sweepAngleDegrees = -90f,
            forceMoveTo = false
        )
        lineTo(w, h - rimHeight)
    }
    val shift = (phase % 1f) * w
    val stops = buildList {
        val all = colors + colors.first()
        all.forEachIndexed { index, c -> add(index.toFloat() / (all.size - 1) to c) }
    }.toTypedArray()
    val rimBrush = Brush.horizontalGradient(
        colorStops = *stops,
        startX = -shift,
        endX = w * 2f - shift,
        tileMode = TileMode.Repeated
    )
    val fadeUp = Brush.verticalGradient(
        0f to Color.Transparent,
        1f to Color.Black,
        startY = h - rimHeight,
        endY = h
    )
    // Offscreen so the vertical fade can mask the coloured strokes.
    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, h - rimHeight - 24f, w, h), androidx.compose.ui.graphics.Paint())
    listOf(18.dp to 0.10f, 9.dp to 0.22f, 3.dp to 0.85f).forEach { (width, alpha) ->
        drawPath(
            path = path,
            brush = rimBrush,
            alpha = alpha * k,
            style = Stroke(width = width.toPx(), cap = StrokeCap.Round)
        )
    }
    drawRect(brush = fadeUp, topLeft = Offset(0f, h - rimHeight - 24f), size = Size(w, rimHeight + 24f), blendMode = BlendMode.DstIn)
    drawContext.canvas.restore()
}

/**
 * Soft edges for a scrolling list: rows blur and fade as they pass under the header at the top
 * ([top]) and under the floating toolbar at the bottom ([bottom]).
 *
 * Android 12+: the content is recorded once, drawn sharp, and a blurred copy is laid over it
 * through a gradient mask that is opaque at the edges and clear in the middle (a progressive
 * blur). Older versions get the colour fade only.
 */
@Composable
fun Modifier.queueEdgeBlur(
    top: Dp,
    bottom: Dp,
    fadeColor: Color,
    blurRadius: Dp = 14.dp,
    topFadeAlpha: Float = 0.85f,
    bottomFadeAlpha: Float = 0.55f
): Modifier {
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val content = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val masked = rememberGraphicsLayer()
    return this.drawWithContent {
        val h = size.height
        if (h <= 0f) {
            drawContent()
            return@drawWithContent
        }
        val topPx = top.toPx().coerceAtMost(h / 3f)
        val bottomPx = bottom.toPx().coerceAtMost(h / 2f)

        if (supportsBlur) {
            content.record { this@drawWithContent.drawContent() }
            drawLayer(content)

            val r = blurRadius.toPx()
            blurred.renderEffect = BlurEffect(r, r, TileMode.Decal)
            blurred.record { drawLayer(content) }

            masked.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
            masked.record {
                drawLayer(blurred)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        (topPx / h) to Color.Transparent,
                        (1f - bottomPx / h) to Color.Transparent,
                        (1f - bottomPx * 0.35f / h) to Color.Black,
                        1f to Color.Black
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            drawLayer(masked)
        } else {
            drawContent()
        }

        // Colour fade on top of the blur so the edges read clean against the sheet.
        clipRect(bottom = topPx) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to fadeColor.copy(alpha = topFadeAlpha),
                    1f to fadeColor.copy(alpha = 0f),
                    startY = 0f,
                    endY = topPx
                )
            )
        }
        clipRect(top = h - bottomPx) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to fadeColor.copy(alpha = 0f),
                    1f to fadeColor.copy(alpha = bottomFadeAlpha),
                    startY = h - bottomPx,
                    endY = h
                )
            )
        }
    }
}
