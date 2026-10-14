package com.theveloper.pixelplay.presentation.screens.radio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The radio map's base map: **OpenFreeMap** vector tiles (OpenStreetMap data), drawn by
 * **MapLibre Native** (open source, BSD). Neither needs an API key, an account or a sign-up,
 * and OpenFreeMap has no request limits and allows use in apps.
 *
 * (The previous CARTO raster tiles now require a CARTO-issued API key under CARTO's basemap
 * terms, which is why the map moved.)
 *
 * Map data goes through the app's OkHttp client, so offline mode and the network policy apply;
 * MapLibre keeps its own on-disk tile cache, so places you've looked at load instantly.
 */
@Singleton
class RadioMapTiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    @Volatile
    private var initialized = false

    /** MapLibre must be set up once before the first MapView is created. Main thread. */
    fun ensureInitialized() {
        if (initialized) return
        MapLibre.getInstance(context)
        runCatching { HttpRequestUtil.setOkHttpClient(okHttpClient) }
        initialized = true
    }

    fun styleUrl(dark: Boolean): String = if (dark) STYLE_DARK else STYLE_LIGHT

    companion object {
        /** Colourful street map, close to the old Voyager look. */
        const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
        const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

        /** Required by OpenFreeMap / OpenMapTiles / OpenStreetMap. */
        const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles · © OpenStreetMap"
        const val ATTRIBUTION_URL = "https://www.openstreetmap.org/copyright"

        const val MIN_ZOOM = 1.0
        const val MAX_ZOOM = 17.0
    }
}
