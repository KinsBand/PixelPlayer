package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lets a screen hide the bottom navigation bar for a while (e.g. while Your Music search is
 * open, so the keyboard and results aren't covered). Read by MainActivity next to the
 * per-route hidden list; the bar slides out/in with its usual animation.
 */
object NavBarVisibility {
    var hiddenByScreen by mutableStateOf(false)
}
