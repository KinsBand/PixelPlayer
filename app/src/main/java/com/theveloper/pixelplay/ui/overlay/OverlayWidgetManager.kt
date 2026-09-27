package com.theveloper.pixelplay.ui.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object OverlayWidgetManager {

    /**
     * Checks if the app has permission to draw system alert window overlays.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Generates an intent to redirect the user to the system "Appear on top" permission screen.
     */
    fun createOverlaySettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Starts the OverlayCutoutService if permissions are satisfied.
     */
    fun startOverlayService(context: Context) {
        if (!canDrawOverlays(context) && !isAccessibilityServiceEnabled(context)) return

        val intent = Intent(context, OverlayCutoutService::class.java)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to start OverlayCutoutService")
        }
    }

    /**
     * Keeps the OverlayCutoutService in line with the "cutout overlay" preference.
     * Called from MainActivity (preference flow) and the Widgets settings toggle.
     * The service can draw either with the "Appear on top" permission or through
     * CutoutIslandAccessibilityService, so either one is enough to start it.
     */
    fun syncOverlayServiceState(context: Context, enabled: Boolean) {
        val canShow = canDrawOverlays(context) || isAccessibilityServiceEnabled(context)
        if (enabled && canShow) {
            startOverlayService(context)
        } else {
            stopOverlayService(context)
        }
    }

    /**
     * Stops the OverlayCutoutService.
     */
    fun stopOverlayService(context: Context) {
        val intent = Intent(context, OverlayCutoutService::class.java)
        try {
            context.stopService(intent)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to stop OverlayCutoutService")
        }
    }

    /**
     * Checks if CutoutIslandAccessibilityService is currently active and enabled.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (CutoutIslandAccessibilityService.isRunning) return true
        val expectedComponentName = android.content.ComponentName(context, CutoutIslandAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            if (componentNameString.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Generates an intent to redirect the user to Android Accessibility Settings.
     */
    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}

