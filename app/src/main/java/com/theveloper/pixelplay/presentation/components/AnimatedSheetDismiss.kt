package com.theveloper.pixelplay.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

/**
 * Close a ModalBottomSheet from a button *inside* it with the same slide-down animation that
 * swipe / scrim tap / Back get.
 *
 * Calling `onDismiss()` directly removes the sheet from composition on the next frame, so it
 * just vanishes. This hides it first and then calls [onDismiss] — always, even if the hide is
 * interrupted (the sheet must end up closed). Repeated taps while it is closing are ignored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAnimatedSheetDismiss(
    sheetState: SheetState,
    onDismiss: () -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    return remember(sheetState, scope) {
        var dismissing = false
        val dismiss: () -> Unit = {
            if (!dismissing) {
                dismissing = true
                scope.launch {
                    try {
                        sheetState.hide()
                    } finally {
                        dismissing = false
                        latestOnDismiss()
                    }
                }
            }
        }
        dismiss
    }
}
