package com.theveloper.pixelplay.presentation.components.voicesearch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

/**
 * Microphone button that reacts to your voice.
 *
 * - Idle: plain mic.
 * - Listening: the mic fills in, a soft halo breathes with the input level (smoothed with a
 *   spring, so it flows instead of jittering) and two rings ripple outwards.
 * - Processing: a short arc sweeps around the mic.
 * - Error: the mic turns the error colour.
 *
 * The level is read in the draw phase through [level], so loud/quiet changes only redraw —
 * they never recompose or re-layout the search bar.
 */
@Composable
fun VoiceMicButton(
    onClick: () -> Unit,
    listening: Boolean,
    modifier: Modifier = Modifier,
    level: () -> Float = { 0f },
    processing: Boolean = false,
    error: Boolean = false,
    size: Dp = 28.dp,
    iconSize: Dp = 20.dp,
    icon: ImageVector = Icons.Rounded.Mic,
    contentDescription: String? = "Voice search"
) {
    val colors = MaterialTheme.colorScheme

    // Smoothed 0..1 level: fast attack, gentle release.
    val smoothLevel = remember { Animatable(0f) }
    LaunchedEffect(listening) {
        if (!listening) {
            smoothLevel.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
            return@LaunchedEffect
        }
        snapshotFlow { level().coerceIn(0f, 1f) }.collectLatest { target ->
            smoothLevel.animateTo(
                target,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = if (target > smoothLevel.value) 900f else 180f
                )
            )
        }
    }

    val activeProgress by animateFloatAsState(
        targetValue = if (listening || processing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "mic_active"
    )
    val coreColor by animateColorAsState(
        targetValue = when {
            error -> colors.errorContainer
            listening -> colors.primary
            processing -> colors.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "mic_core"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            error -> colors.onErrorContainer
            listening -> colors.onPrimary
            processing -> colors.onPrimaryContainer
            else -> colors.primary
        },
        animationSpec = tween(220),
        label = "mic_tint"
    )

    // The looping animations only exist while the mic is busy, so an idle button costs no frames.
    val animating = listening || processing
    val ripplePhaseState = if (animating) {
        rememberInfiniteTransition(label = "mic_waves").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "mic_ripple"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val spinState = if (processing) {
        rememberInfiniteTransition(label = "mic_spin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "mic_spin"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val waveColor = colors.primary
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2f
                val c = center
                val active = activeProgress
                if (listening && active > 0.01f) {
                    val lvl = smoothLevel.value
                    // Two staggered ripples; louder input makes them stronger.
                    for (k in 0 until 2) {
                        val p = (ripplePhaseState.value + k * 0.5f) % 1f
                        val radius = r * (1f + 0.8f * p)
                        val alpha = (1f - p) * (0.14f + 0.26f * lvl) * active
                        drawCircle(waveColor.copy(alpha = alpha), radius, c)
                    }
                    // Level halo that breathes with the voice.
                    drawCircle(
                        waveColor.copy(alpha = 0.22f * active),
                        r * (1f + 0.55f * lvl) * active.coerceAtLeast(0.6f),
                        c
                    )
                }
                if (processing && !listening) {
                    val stroke = 2.dp.toPx()
                    val arcR = r + stroke
                    drawArc(
                        color = waveColor,
                        startAngle = spinState.value,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(c.x - arcR, c.y - arcR),
                        size = Size(arcR * 2, arcR * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val s = 1f + 0.12f * smoothLevel.value * activeProgress
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .drawBehind { drawCircle(coreColor) }
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
