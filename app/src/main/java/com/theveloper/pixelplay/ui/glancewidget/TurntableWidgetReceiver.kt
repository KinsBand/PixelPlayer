package com.theveloper.pixelplay.ui.glancewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class TurntableWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TurntableWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // The loop checks for placed widgets on its own, but stopping here means the last
        // turntable to be removed does not leave a frame pump running until its next tick.
        if (AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, javaClass))
                .isEmpty()
        ) {
            TurntableSpinController.stop()
            TurntableSpinController.invalidateRendering()
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        TurntableSpinController.stop()
        TurntableSpinController.invalidateRendering()
    }
}
