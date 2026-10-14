package com.theveloper.pixelplay.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

private fun NavController.isReadyForNavigation(): Boolean {
    return runCatching {
        // We allow navigation if the current entry is at least STARTED.
        // This is safer than strictly RESUMED as transitions can sometimes delay RESUMED state.
        currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    }.getOrDefault(false)
}

fun NavController.navigateSafely(route: String): Boolean {
    if (!isReadyForNavigation()) return false
    navigate(route) {
        launchSingleTop = true
    }
    return true
}

fun NavController.navigateSafely(
    route: String,
    builder: NavOptionsBuilder.() -> Unit
): Boolean {
    if (!isReadyForNavigation()) return false
    navigate(route) {
        launchSingleTop = true
        builder()
    }
    return true
}

fun NavController.navigateSafelyReplacing(
    route: String,
    patternToPop: String,
    builder: NavOptionsBuilder.() -> Unit = {}
): Boolean {
    if (!isReadyForNavigation()) return false
    navigate(route) {
        launchSingleTop = false
        popUpTo(patternToPop) {
            inclusive = true
        }
        builder()
    }
    return true
}

fun NavController.navigateToTopLevelSafely(route: String): Boolean {
    val startDestinationId = runCatching { graph.startDestinationId }.getOrNull() ?: return false
    navigate(route) {
        popUpTo(startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
    return true
}

private val lastSafePopAt = java.util.WeakHashMap<NavController, Long>()

/** Long enough to cover a pop transition (350 ms) at normal animator scale. */
private const val SAFE_POP_DEBOUNCE_MS = 400L

/**
 * Back arrow / close button pop that can't break the exit animation:
 *
 * - Never pops the last screen. A plain popBackStack() on the start destination empties the
 *   NavHost and leaves a blank screen (easy to hit by double-tapping a back arrow).
 * - When [from] is given (the NavBackStackEntry that owns the button), it only pops if that
 *   entry is still on top, so a second tap while the screen is sliding out is ignored.
 * - Without [from], taps within one transition of the previous pop are ignored, so a
 *   double tap can't skip a screen (A -> B -> C, tap tap -> B, not A).
 */
fun NavController.popBackStackSafely(from: NavBackStackEntry? = null): Boolean {
    val current = currentBackStackEntry ?: return false
    if (previousBackStackEntry == null) return false
    val now = android.os.SystemClock.uptimeMillis()
    if (from != null) {
        if (from.id != current.id) return false
    } else {
        val last = lastSafePopAt[this]
        if (last != null && now - last < SAFE_POP_DEBOUNCE_MS) return false
    }
    lastSafePopAt[this] = now
    return popBackStack()
}
