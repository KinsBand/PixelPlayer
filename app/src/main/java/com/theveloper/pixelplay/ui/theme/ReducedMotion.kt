package com.theveloper.pixelplay.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the system animator duration scale is 0 (animations turned off in
 * Developer options / Accessibility "Remove animations"). Callers swap their motion
 * for an instant change or a plain crossfade when this is true.
 *
 * Non-zero scales (0.5x, 2x…) are already applied by Compose to every tween/spring,
 * so nothing extra is needed for those.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}
