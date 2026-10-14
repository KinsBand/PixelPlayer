package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * The quick "riffle" every shuffle button plays when tapped: the arrows flip across once
 * (a full turn around the vertical axis, so they visibly cross) while the glyph dips and
 * springs back. About 450 ms, Material emphasized-decelerate. Nothing plays when the system
 * has animations turned off.
 *
 * Usage: `val spin = rememberShuffleSpin()`, call [play] in the click handler, and put
 * `Modifier.shuffleSpin(spin)` on the shuffle Icon. The progress is read only inside
 * `graphicsLayer {}`, so a spin never recomposes the button.
 */
@Stable
class ShuffleSpin internal constructor(
    private val scope: CoroutineScope,
    private val enabled: Boolean,
) {
    internal val progress = Animatable(0f)

    fun play() {
        if (!enabled) return
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(SPIN_DURATION_MS, easing = MotionTokens.EmphasizedDecelerate))
        }
    }

    private companion object {
        const val SPIN_DURATION_MS = 450
    }
}

@Composable
fun rememberShuffleSpin(): ShuffleSpin {
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberSystemReducedMotion()
    return remember(scope, reducedMotion) { ShuffleSpin(scope, enabled = !reducedMotion) }
}

/** Applies [spin] to a shuffle glyph (see [ShuffleSpin]). */
fun Modifier.shuffleSpin(spin: ShuffleSpin): Modifier = graphicsLayer {
    val p = spin.progress.value
    if (p > 0f && p < 1f) {
        rotationY = 360f * p
        val dip = 1f - 0.2f * sin(PI * p).toFloat()
        scaleX = dip
        scaleY = dip
        cameraDistance = 12f * density
    }
}

/**
 * Home's Shuffle pill (Quick picks, Fresh Finds): looks like a FilledTonalButton with the
 * shuffle glyph + label. Tap plays the shuffle (with the spin); hold opens Soon so the shuffle
 * can be scheduled in Up next instead (null [onLongClick] = tap only).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShuffleSoonButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    longClickLabel: String? = null,
) {
    val spin = rememberShuffleSpin()
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    spin.play()
                    onClick()
                },
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick?.let { hold ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        spin.play()
                        hold()
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = ButtonDefaults.MinHeight)
                .padding(ButtonDefaults.ContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.shuffleSpin(spin))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
