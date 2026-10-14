package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A sheet that docks at the bottom of the screen and *lifts* the bottom deck (the Home / Search /
 * Library bar and the mini player) so they sit right on top of it, instead of covering them.
 * Used by the Daily Mix sheet; the voice-search sheet works the same way.
 *
 * A screen shows one with [BottomDeckSheetSlot]; MainActivity draws it (above the screens, below
 * the lifted deck) and owns the slide/lift animation, Back and swipe-down-to-close.
 */
object BottomDeckSheet {
    /** True while a sheet is open (its content may still be composed while it slides away). */
    var isOpen by mutableStateOf(false)
        private set

    /** What the sheet shows; kept until the close animation has finished. */
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    private var owner: Any? = null
    private var onDismissRequest: (() -> Unit)? = null

    internal fun show(owner: Any, content: @Composable () -> Unit, onDismiss: () -> Unit) {
        this.owner = owner
        this.content = content
        this.onDismissRequest = onDismiss
        isOpen = true
    }

    internal fun hide(owner: Any) {
        if (this.owner === owner) isOpen = false
    }

    /** Back, swipe down, or something else needing the space: asks the owner to close. */
    fun dismiss() {
        if (!isOpen) return
        val callback = onDismissRequest
        isOpen = false
        callback?.invoke()
    }

    /** Called by the host once the sheet has fully slid away. */
    fun clearIfClosed() {
        if (!isOpen) {
            content = null
            owner = null
            onDismissRequest = null
        }
    }
}

/**
 * Shows [content] in the docked bottom sheet while [open] is true. The content keeps reading the
 * caller's latest state, so it updates like any other part of the screen. Leaving the screen
 * closes the sheet.
 */
@Composable
fun BottomDeckSheetSlot(
    open: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val owner = remember { Any() }
    val latestContent by rememberUpdatedState(content)
    val latestDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(open) {
        if (open) {
            BottomDeckSheet.show(owner, { latestContent() }, { latestDismiss() })
        } else {
            BottomDeckSheet.hide(owner)
        }
        onDispose { if (open) BottomDeckSheet.hide(owner) }
    }
}

/** The sheet's surface: rounded top, a drag handle, and room for the system navigation bar. */
@Composable
fun BottomDeckSheetSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        color = colors.surfaceContainerLow,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Rides on top of the keyboard while typing (otherwise just the nav bar).
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(top = 10.dp, bottom = 12.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.outlineVariant)
                )
            }
            content()
        }
    }
}
