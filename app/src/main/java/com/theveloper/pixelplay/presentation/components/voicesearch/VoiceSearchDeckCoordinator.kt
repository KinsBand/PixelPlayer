package com.theveloper.pixelplay.presentation.components.voicesearch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class VoiceSearchDeckCoordinator(
    val animatable: Animatable<Float, AnimationVector1D>
) {
    val progress: Float
        get() = animatable.value

    val isDisplaced: Boolean
        get() = progress > 0.01f

    /**
     * Offset to move the bottom deck (navigation bar and mini player) upward.
     */
    fun calculateDeckTranslationY(sheetHeightPx: Float): Float {
        return -sheetHeightPx * progress
    }

    /**
     * Offset to pull up the active search/mic row.
     */
    fun calculateRowPullUpY(liftPx: Float): Float {
        return -liftPx * progress
    }
}

@Composable
fun rememberVoiceSearchDeckCoordinator(
    isOpen: Boolean
): VoiceSearchDeckCoordinator {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    return remember(animatable) {
        VoiceSearchDeckCoordinator(animatable)
    }
}

/**
 * Swipe down on the sheet to close it. The drag drives the shared [VoiceSearchDeckCoordinator]
 * progress directly, so the sheet and the lifted bottom deck (Home / Search / Library + mini
 * player) follow the finger together. Releasing past a third of the way down, or flinging
 * down, closes the sheet; otherwise it springs back open.
 *
 * Apply this *before* the sheet's own translation layer so drag deltas are measured in
 * untranslated coordinates.
 */
fun Modifier.voiceSearchSwipeToDismiss(
    coordinator: VoiceSearchDeckCoordinator,
    sheetHeightPx: () -> Float,
    onDismiss: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val dragState = rememberDraggableState { delta ->
        val height = sheetHeightPx()
        if (height > 0f) {
            val target = (coordinator.animatable.value - delta / height).coerceIn(0f, 1f)
            scope.launch { coordinator.animatable.snapTo(target) }
        }
    }
    draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            if (coordinator.animatable.value < 0.67f || velocity > 1200f) {
                latestOnDismiss()
            } else {
                coordinator.animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    )
}
