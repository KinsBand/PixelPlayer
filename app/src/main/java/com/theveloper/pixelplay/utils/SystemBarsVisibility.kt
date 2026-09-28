package com.theveloper.pixelplay.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.DialogWindowProvider
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
    applySystemBarsVisibility(window, prefs)
}

/** Same as the activity version, for any window (a bottom sheet's or a dialog's own window). */
fun applySystemBarsVisibility(window: Window, prefs: SystemBarsPrefs) {
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
@Composable
fun SystemBarsVisibilityEffect(activity: Activity) {
    val prefs = rememberSystemBarsPrefs()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(prefs) { applySystemBarsVisibility(activity, prefs) }

    val bars = rememberSystemBarsShown()
    LaunchedEffect(bars, prefs) {
        if (prefs.wantsHidden(bars)) {
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

/**
 * Which bars take up space in this window right now. A hidden bar has zero insets, including
 * while an edge swipe shows it for a moment on top of the app, so only a bar that really came
 * back counts.
 */
@Composable
private fun rememberSystemBarsShown(): Pair<Boolean, Boolean> {
    val density = LocalDensity.current
    val status = WindowInsets.statusBars.getTop(density) > 0
    val nav = WindowInsets.navigationBars
    // Bottom in portrait; left or right when the phone is sideways.
    val navigation = nav.getBottom(density) > 0 ||
        nav.getLeft(density, LayoutDirection.Ltr) > 0 ||
        nav.getRight(density, LayoutDirection.Ltr) > 0
    return status to navigation
}

private fun SystemBarsPrefs.wantsHidden(bars: Pair<Boolean, Boolean>): Boolean =
    (hideStatusBar && bars.first) || (hideNavigationBar && bars.second)

private fun View.dialogWindow(): Window? {
    var current: Any? = this
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = (current as? View)?.parent
    }
    return null
}

/**
 * Bottom sheets and dialogs have their own window, which doesn't inherit the hidden bars, so
 * with "Hide status bar" / "Hide gesture bar" on the bars came back while a sheet was open.
 * Call this first thing inside a sheet's content: it hides the chosen bars in the sheet's
 * window too, and again whenever they come back. Does nothing outside a dialog window.
 */
@Composable
fun KeepSystemBarsHiddenInDialog() {
    val view = LocalView.current
    val window = remember(view) { view.dialogWindow() } ?: return
    val prefs = rememberSystemBarsPrefs()
    val bars = rememberSystemBarsShown()
    LaunchedEffect(window, prefs) {
        if (prefs.hideStatusBar || prefs.hideNavigationBar) hideChosenBars(window, prefs)
    }
    LaunchedEffect(window, bars, prefs) {
        if (prefs.wantsHidden(bars)) {
            delay(REHIDE_DELAY_MS)
            hideChosenBars(window, prefs)
        }
    }
}

/** Only hides (a sheet should never show a bar the app itself keeps hidden). */
private fun hideChosenBars(window: Window, prefs: SystemBarsPrefs) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (prefs.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
    if (prefs.hideNavigationBar) controller.hide(WindowInsetsCompat.Type.navigationBars())
}
