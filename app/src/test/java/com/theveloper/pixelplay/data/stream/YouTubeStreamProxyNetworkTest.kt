package com.theveloper.pixelplay.data.stream

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.theveloper.pixelplay.data.youtube.YouTubeStreamExtractor
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class YouTubeStreamProxyNetworkTest {
    @TempDir lateinit var cacheDir: File

    @Test fun `only a change of default network drops stream urls`() {
        val callback = slot<ConnectivityManager.NetworkCallback>()
        val connectivity = mockk<ConnectivityManager>(relaxed = true)
        every { connectivity.registerDefaultNetworkCallback(capture(callback)) } just Runs
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        every { context.cacheDir } returns cacheDir
        val extractor = mockk<YouTubeStreamExtractor>(relaxed = true)
        YouTubeStreamProxy(OkHttpClient(), extractor, context)
        val wifi = mockk<Network>()
        val mobile = mockk<Network>()

        callback.captured.onAvailable(wifi) // announced on registration: nothing changed yet
        callback.captured.onAvailable(wifi)
        verify(exactly = 0) { extractor.onNetworkChanged() }

        callback.captured.onAvailable(mobile)
        verify(exactly = 1) { extractor.onNetworkChanged() }
    }
}
