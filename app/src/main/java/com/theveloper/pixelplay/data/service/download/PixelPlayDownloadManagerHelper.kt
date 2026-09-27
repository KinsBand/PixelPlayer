package com.theveloper.pixelplay.data.service.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
object PixelPlayDownloadManagerHelper {
    private var downloadManager: DownloadManager? = null
    private var downloadCache: SimpleCache? = null

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            val databaseProvider = StandaloneDatabaseProvider(context)
            val cacheDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            downloadCache = SimpleCache(
                cacheDir,
                NoOpCacheEvictor(),
                databaseProvider
            )

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("PixelPlayer/1.0 (Android; Music Player)")

            downloadManager = DownloadManager(
                context,
                databaseProvider,
                downloadCache!!,
                dataSourceFactory,
                Executors.newFixedThreadPool(3)
            ).apply {
                maxParallelDownloads = 3
            }
        }
        return downloadManager!!
    }

    @Synchronized
    fun getDownloadCache(context: Context): SimpleCache {
        getDownloadManager(context)
        return downloadCache!!
    }
}
