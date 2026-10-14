package com.theveloper.pixelplay.data.ai.nano

import android.os.Build

/**
 * AI features (Gemini Nano, AI playlists, AI settings) are only shown on Google Pixel phones.
 * Other phones get the same app without any AI entry points.
 */
object DeviceAi {
    val isPixel: Boolean by lazy {
        Build.MANUFACTURER.equals("Google", ignoreCase = true) &&
            Build.MODEL.contains("Pixel", ignoreCase = true)
    }
}
