package com.theveloper.pixelplay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import com.theveloper.pixelplay.data.diagnostics.HeapPressure
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnosticsController
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.presentation.viewmodel.LibraryStateHolder
import com.theveloper.pixelplay.presentation.viewmodel.ThemeStateHolder
import com.theveloper.pixelplay.utils.AlbumArtCacheManager
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.utils.MediaMetadataRetrieverPool
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PixelPlayApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var localArtworkCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.LocalArtworkCoilFetcher.Factory>

    @Inject
    lateinit var themeStateHolder: dagger.Lazy<ThemeStateHolder>

    @Inject
    lateinit var artistImageRepository: dagger.Lazy<ArtistImageRepository>

    @Inject
    lateinit var libraryStateHolder: dagger.Lazy<LibraryStateHolder>

    @Inject
    lateinit var userPreferencesRepository: dagger.Lazy<UserPreferencesRepository>

    @Inject
    lateinit var advancedPerformanceDiagnosticsController: dagger.Lazy<AdvancedPerformanceDiagnosticsController>

    @Inject
    lateinit var newPipeDownloader: dagger.Lazy<com.theveloper.pixelplay.data.youtube.NewPipeDownloader>

    @Inject
    lateinit var downloadedLibraryIndexer: dagger.Lazy<com.theveloper.pixelplay.data.library.DownloadedLibraryIndexer>

    @Inject
    lateinit var streamCollectionRepository: dagger.Lazy<com.theveloper.pixelplay.data.library.StreamCollectionRepository>

    @Inject
    lateinit var downloadCoordinator: dagger.Lazy<com.theveloper.pixelplay.data.youtube.DownloadCoordinator>

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // AÑADE EL COMPANION OBJECT
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelplay_music_channel"
        lateinit var instance: PixelPlayApplication
            private set
    }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            libraryStateHolder.get().restoreAfterTrimIfNeeded()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(base))
    }

    override fun onCreate() {
        instance = this
        super.onCreate()

        // Benchmark variant intentionally restarts/kills app process during tests.
        // Avoid persisting those events as user-facing crash reports.
        if (BuildConfig.BUILD_TYPE != "benchmark") {
            CrashHandler.install(this)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Release tree: only WARN/ERROR/WTF - no DEBUG/VERBOSE/INFO
            Timber.plant(ReleaseTree())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val playbackChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PixelPlayer Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val downloadChannel = NotificationChannel(
                "pixelplay_download_channel",
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(playbackChannel)
            notificationManager.createNotificationChannel(downloadChannel)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        advancedPerformanceDiagnosticsController.get().start(startupScope)
        installHeapPressureTrimmers()

        runCatching {
            org.schabi.newpipe.extractor.NewPipe.init(newPipeDownloader.get())
        }.onFailure {
            Timber.tag("PixelPlayApp").e(it, "Failed to initialize NewPipe Extractor")
        }

        startupScope.launch {
            // Background synced-lyrics backfill for the whole library (periodic, KEEP).
            runCatching {
                com.theveloper.pixelplay.data.worker.LyricsBackfillWorker.schedule(this@PixelPlayApplication)
            }.onFailure {
                Timber.tag("PixelPlayApp").w(it, "Failed to schedule lyrics backfill")
            }
        }

        startupScope.launch {
            // Library tabs: keep downloads and streamed likes in Albums / Artists.
            // Started a little after launch so they never compete with the first frame.
            kotlinx.coroutines.delay(2_000)
            runCatching { downloadedLibraryIndexer.get().start() }
                .onFailure { Timber.tag("PixelPlayApp").w(it, "Download indexer failed to start") }
            runCatching { streamCollectionRepository.get().start() }
                .onFailure { Timber.tag("PixelPlayApp").w(it, "Streamed collection failed to start") }
            // Wi-Fi-only "download all liked songs": asks when Wi-Fi connects.
            runCatching { downloadCoordinator.get().start() }
                .onFailure { Timber.tag("PixelPlayApp").w(it, "Download coordinator failed to start") }
        }

        startupScope.launch {
            AlbumArtUtils.migrateLegacyCacheLocation(this@PixelPlayApplication)
            val savedLimit = runCatching {
                userPreferencesRepository.get().albumArtCacheLimitMbFlow.first()
            }.getOrNull()
            if (savedLimit != null) {
                AlbumArtCacheManager.configuredCacheLimitMb = savedLimit.toLong()
            }
        }
    }

    /**
     * Java-heap watchdog (see [HeapPressure]): `onTrimMemory` below only reacts to system RAM
     * pressure, never to this process approaching its own heap limit.
     */
    private fun installHeapPressureTrimmers() {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        HeapPressure.register("app-caches") { level ->
            // These holders are touched from the main thread; trim them there.
            mainHandler.post {
                runCatching { imageLoader.get().memoryCache?.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) }
                runCatching { themeStateHolder.get().trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) }
                runCatching { artistImageRepository.get().clearCache() }
                runCatching { MediaMetadataRetrieverPool.clear() }
                if (level == HeapPressure.Level.CRITICAL) {
                    runCatching { imageLoader.get().memoryCache?.clear() }
                    // With no UI on screen (background playback) the library lists are not
                    // needed; they reload in onStart via restoreAfterTrimIfNeeded().
                    val uiVisible = ProcessLifecycleOwner.get().lifecycle.currentState
                        .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                    if (!uiVisible) {
                        runCatching { libraryStateHolder.get().trimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) }
                    }
                }
            }
        }
        HeapPressure.start(startupScope)
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get().newBuilder()
            .components {
                add(localArtworkCoilFetcherFactory.get())
            }
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // System is short on RAM too: let the data-layer caches shrink as well.
            HeapPressure.trimNow(
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) HeapPressure.Level.CRITICAL
                else HeapPressure.Level.ELEVATED
            )
        }

        imageLoader.get().memoryCache?.trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            themeStateHolder.get().trimMemory(level)
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            artistImageRepository.get().clearCache()
            MediaMetadataRetrieverPool.clear()
        }

        libraryStateHolder.get().trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            imageLoader.get().memoryCache?.clear()
        }
    }

    // 3. Sobrescribe el método para proveer la configuración de WorkManager
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}
