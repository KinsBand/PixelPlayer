package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.network.NetworkAccessPolicy
import com.theveloper.pixelplay.data.network.NetworkDecision
import com.theveloper.pixelplay.data.network.NetworkPurpose
import com.theveloper.pixelplay.data.youtube.YouTubeHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object YouTubeNetworkModule {

    /**
     * Shares the app client's connection pool and dispatcher but drops its interceptors.
     * The app-wide interceptor rewrites every User-Agent to "PixelPlayer/1.0 ...", which breaks
     * both NewPipe's InnerTube requests (they set client-specific UAs) and googlevideo audio
     * downloads (403). Offline mode is still honoured.
     */
    @Provides
    @Singleton
    @YouTubeOkHttpClient
    fun provideYouTubeOkHttpClient(
        base: OkHttpClient,
        networkAccessPolicy: NetworkAccessPolicy
    ): OkHttpClient = base.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            // Mirrored preferences: no thread blocking per request once they have been read.
            val decision = networkAccessPolicy.decisionNow(NetworkPurpose.Update)
                ?: kotlinx.coroutines.runBlocking { networkAccessPolicy.getDecision(NetworkPurpose.Update) }
            if (decision != NetworkDecision.Allowed) {
                throw IOException("Network request blocked by policy: $decision")
            }
            val request = chain.request()
            // NewPipe sets its own UA for InnerTube calls; only fill it in when absent.
            if (request.header("User-Agent").isNullOrBlank()) {
                chain.proceed(
                    request.newBuilder().header("User-Agent", YouTubeHttp.DESKTOP_USER_AGENT).build()
                )
            } else {
                chain.proceed(request)
            }
        }
        .build()
}
