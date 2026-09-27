package com.theveloper.pixelplay.utils

import android.app.Activity
import android.view.Display
import kotlin.math.abs

/**
 * Asks the system for the display's fastest refresh rate at the current resolution
 * (e.g. 120 Hz instead of the 60 Hz many devices fall back to for "idle" apps), so
 * scrolling, sheet drags and animations render at the panel's full frame rate.
 *
 * Only the refresh rate changes — the resolution stays whatever the user picked — and the
 * system can still override it (battery saver, thermal limits, "Smooth display" turned off).
 */
object HighRefreshRate {

    fun apply(activity: Activity) {
        val display: Display = activity.display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: return

        val window = activity.window
        val params = window.attributes
        val alreadyPreferred = params.preferredDisplayModeId == best.modeId &&
            abs(params.preferredRefreshRate - best.refreshRate) < 0.5f
        if (alreadyPreferred) return

        params.preferredDisplayModeId = best.modeId
        params.preferredRefreshRate = best.refreshRate
        window.attributes = params
    }
}
