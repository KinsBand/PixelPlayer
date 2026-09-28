package com.theveloper.pixelplay.utils

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isNavigationBarVisible
import androidx.compose.foundation.layout.isStatusBarVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.preferences.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * App-wide "full screen" options: hide the status bar (time, battery…) and/or the gesture
 * (navigation) bar while PixelPlayer is open. A swipe in from the top or bottom edge shows
 * them for a moment; they hide again on their own. Whatever else brings them back while the
 * option is on (the back gesture, a dialog, returning to the app) hides them again.
 */
data class SystemBarsPrefs(
    val hideStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
)

object SystemBarsPrefKeys {
    val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar_v1")
    val HIDE_NAVIGATION_BAR = booleanPreferencesKey("hide_navigation_bar_v1")
}

fun systemBarsPrefsFlow(context: Context): Flow<SystemBarsPrefs> =
    context.applicationContext.dataStore.data
        .map { p ->
            SystemBarsPrefs(
                hideStatusBar = p[SystemBarsPrefKeys.HIDE_STATUS_BAR] ?: false,
                hideNavigationBar = p[SystemBarsPrefKeys.HIDE_NAVIGATION_BAR] ?: false,
            )
        }
        .distinctUntilChanged()

@Composable
fun rememberSystemBarsPrefs(): SystemBarsPrefs {
    val context = LocalContext.current
    val flow = remember(context) { systemBarsPrefsFlow(context) }
    val prefs by flow.collectAsStateWithLifecycle(initialValue = SystemBarsPrefs())
    return prefs
}

suspend fun Context.setHideStatusBar(hide: Boolean) {
    applicationContext.dataStore.edit { it[SystemBarsPrefKeys.HIDE_STATUS_BAR] = hide }
}

suspend fun Context.setHideNavigationBar(hide: Boolean) {
    applicationContext.dataStore.edit { it[SystemBarsPrefKeys.HIDE_NAVIGATION_BAR] = hide }
}

/** Shows or hides the bars on [activity]'s window. Safe to call repeatedly. */
fun applySystemBarsVisibility(activity: Activity, prefs: SystemBarsPrefs) {
    val window = activity.window ?: return
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    // Swipe from an edge shows the bars briefly over the app, then they hide again.
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    if (prefs.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
    else controller.show(WindowInsetsCompat.Type.statusBars())

    if (prefs.hideNavigationBar) controller.hide(WindowInsetsCompat.Type.navigationBars())
    else controller.show(WindowInsetsCompat.Type.navigationBars())
}

/** How long a bar that came back is left before hiding it again (lets a transition finish). */
private const val REHIDE_DELAY_MS = 250L

/**
 * Call once from the activity's content. Applies the saved choice, and re-applies it when the
 * app comes back to the foreground (dialogs, the share sheet, permission prompts and the
 * keyboard can bring the bars back).
 *
 * It also watches the bars themselves: the back gesture (Pixel's edge swipe, predictive back)
 * and screen transitions can make a hidden bar visible again without the app losing focus or
 * pausing, so nothing else would notice. Whenever a bar the user chose to hide becomes visible,
 * it's hidden again straight away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemBarsVisibilityEffect(activity: Activity) {
    val prefs = rememberSystemBarsPrefs()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(prefs) { applySystemBarsVisibility(activity, prefs) }

    val statusBarShown = WindowInsets.isStatusBarVisible
    val navigationBarShown = WindowInsets.isNavigationBarVisible
    LaunchedEffect(statusBarShown, navigationBarShown, prefs) {
        val unwanted = (prefs.hideStatusBar && statusBarShown) ||
            (prefs.hideNavigationBar && navigationBarShown)
        if (unwanted) {
            delay(REHIDE_DELAY_MS)
            applySystemBarsVisibility(activity, prefs)
        }
    }

    DisposableEffect(lifecycleOwner, prefs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) applySystemBarsVisibility(activity, prefs)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
