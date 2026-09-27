package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Reports a finger landing on this element ([onPress]) and a press that turned into a scroll
 * or was otherwise cancelled ([onCancel]). Nothing is consumed, so clicks, long presses and
 * scrolling behave exactly as before; this only observes, to start work ~100 ms before a tap.
 */
fun Modifier.onPressObserved(key: Any?, onPress: () -> Unit, onCancel: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(key) {
            awaitEachGesture {
                // Initial pass: hear the press before any child handles it.
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                onPress()
                // Final pass: sees whether a parent (the list) consumed the gesture as a scroll.
                if (waitForUpOrCancellation(PointerEventPass.Final) == null) onCancel()
            }
        }
    )
