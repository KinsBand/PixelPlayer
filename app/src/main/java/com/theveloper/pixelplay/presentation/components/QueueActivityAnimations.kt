package com.theveloper.pixelplay.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Queue activity feedback: an outline that runs around the queue button while a mix is
 * building the queue (or songs were just added), and a staggered entrance for rows that
 * arrive in the queue sheet, so you can watch the songs being added one after another.
 */

/**
 * A light that travels around [shape] while [active], over a faint full ring. Fades in and
 * out; nothing is drawn (and no animation runs) while idle. Draw phase only — no
 * recomposition per frame.
 */
fun Modifier.queueBusyOutline(
    active: Boolean,
    shape: Shape,
    color: Color,
    strokeWidth: Dp = 2.5.dp,
    lapMillis: Int = 1_400
): Modifier = composed {
    val appear by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = if (active) 260 else 480),
        label = "queueBusyOutlineAppear"
    )
    val angle = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            angle.snapTo(0f)
            angle.animateTo(360f, tween(durationMillis = lapMillis, easing = LinearEasing))
        }
    }
    val matrix = remember { android.graphics.Matrix() }

    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val strokePx = strokeWidth.toPx()
        val shader = SweepGradientShader(
            center = Offset(size.width / 2f, size.height / 2f),
            colors = listOf(
                color.copy(alpha = 0f),
                color.copy(alpha = 0f),
                color.copy(alpha = 0.55f),
                color,
                color.copy(alpha = 0f)
            ),
            colorStops = listOf(0f, 0.45f, 0.72f, 0.86f, 1f)
        )
        val brush = object : ShaderBrush() {
            override fun createShader(size: Size): Shader = shader
        }
        onDrawWithContent {
            drawContent()
            val a = appear
            if (a <= 0.01f) return@onDrawWithContent
            matrix.setRotate(angle.value, center.x, center.y)
            shader.setLocalMatrix(matrix)
            // Faint full ring so the shape reads as "busy" even between sweeps.
            drawOutline(outline, color = color.copy(alpha = 0.28f * a), style = Stroke(strokePx))
            // Soft glow + the travelling light.
            drawOutline(outline, brush = brush, alpha = 0.35f * a, style = Stroke(strokePx * 2.6f))
            drawOutline(outline, brush = brush, alpha = a, style = Stroke(strokePx))
        }
    }
}

/**
 * Remembers which queue rows the user has already seen, so only rows that genuinely
 * arrive animate in. Rows that arrive together are staggered, so a mix filling the queue
 * reads as songs being added one after another.
 */
@Stable
class QueueEntranceTracker {
    private val known = HashSet<Long>()
    private val arrivals = HashMap<Long, Arrival>()
    private var burstStart = 0L
    private var burstCount = 0
    private var primed = false

    private class Arrival(val at: Long, val delayMs: Int)

    /**
     * Call with the current row keys. When [animate] is false (sheet hidden, keys not ready)
     * the keys are only recorded, so opening the sheet never replays the whole queue.
     */
    fun update(keys: List<Long>, animate: Boolean) {
        if (!animate || !primed) {
            known.addAll(keys)
            primed = primed || animate
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - burstStart > BURST_WINDOW_MS) {
            burstStart = now
            burstCount = 0
        }
        for (key in keys) {
            if (known.add(key)) {
                arrivals[key] = Arrival(now, burstCount.coerceAtMost(MAX_STAGGER_STEPS) * STAGGER_MS)
                burstCount++
            }
        }
        if (arrivals.size > 128) arrivals.entries.removeAll { now - it.value.at > FRESH_MS }
        if (known.size > 4_096) {
            known.clear()
            known.addAll(keys)
        }
    }

    /** Stagger delay for a freshly added row (once), or null when the row isn't new. */
    fun claim(key: Long): Int? {
        val arrival = arrivals.remove(key) ?: return null
        return if (SystemClock.uptimeMillis() - arrival.at <= FRESH_MS) arrival.delayMs else null
    }

    private companion object {
        const val STAGGER_MS = 70
        const val MAX_STAGGER_STEPS = 12
        const val BURST_WINDOW_MS = 900L
        const val FRESH_MS = 2_000L
    }
}
