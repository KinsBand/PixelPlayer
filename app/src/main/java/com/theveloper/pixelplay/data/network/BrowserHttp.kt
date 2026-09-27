package com.theveloper.pixelplay.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * HTTP client for talking to web players (open.spotify.com, music.apple.com) as a browser.
 *
 * The app-wide client rewrites every User-Agent to "PixelPlayer/1.0 …". Spotify's web token
 * endpoint and page scripts reject that, so sign-in failed as "invalid". This client shares the
 * app's connection pool but drops those interceptors, so the browser headers we set are sent as-is.
 */
object BrowserHttp {
    fun from(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}
