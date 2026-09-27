package com.theveloper.pixelplay.ui.overlay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/**
 * AccessibilityService that provides a trusted TYPE_ACCESSIBILITY_OVERLAY window
 * context for the Dynamic Island widget.
 *
 * In Android 12+ (including Android 15/16 on Google Pixel 10 Pro), standard
 * TYPE_APPLICATION_OVERLAY windows are positioned BEHIND the system StatusBar
 * and NotificationShade in the input dispatcher window hierarchy. Consequently,
 * touches directly over the front camera cutout (Y in [0, 172 px]) are intercepted
 * and eaten by SystemUI's PhoneStatusBarView.
 *
 * An AccessibilityService is granted the capability to host TYPE_ACCESSIBILITY_OVERLAY
 * windows, which are placed at layer 33 (ABOVE the status bar at layer 19), allowing
 * seamless and instant tap, hold, and drag gestures directly on the camera cutout.
 */
class CutoutIslandAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CutoutIslandAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null

        private val listeners = mutableListOf<(Boolean) -> Unit>()

        fun addStateListener(listener: (Boolean) -> Unit) {
            synchronized(listeners) {
                if (!listeners.contains(listener)) {
                    listeners.add(listener)
                }
            }
        }

        fun removeStateListener(listener: (Boolean) -> Unit) {
            synchronized(listeners) {
                listeners.remove(listener)
            }
        }

        internal fun notifyStateChanged(running: Boolean) {
            val listCopy = synchronized(listeners) { listeners.toList() }
            listCopy.forEach { it(running) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.d("CutoutIslandAccessibilityService: connected and active")
        instance = this
        notifyStateChanged(true)
        // If the overlay service isn't running yet (app closed, or the user just came
        // back from Accessibility settings), start it now so the island is added through
        // this service's window manager. It stops itself if the overlay preference is off.
        OverlayWidgetManager.startOverlayService(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("CutoutIslandAccessibilityService: destroyed")
        instance = null
        notifyStateChanged(false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: we do not intercept or inspect any accessibility events
    }

    override fun onInterrupt() {
        // No-op
    }
}
