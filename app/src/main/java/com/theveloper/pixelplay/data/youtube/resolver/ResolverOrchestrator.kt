package com.theveloper.pixelplay.data.youtube.resolver

import android.net.Uri
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.youtube.YouTubeStreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolverOrchestrator @Inject constructor(
    private val streamExtractor: YouTubeStreamExtractor,
    private val cloudSongDao: CloudSongDao
) {
    private val cache = com.theveloper.pixelplay.utils.BoundedCache<String, PlaybackResolution>(128)

    suspend fun resolveTrack(song: Song): PlaybackResolution? = withContext(Dispatchers.IO) {
        val songId = song.id
        val cloudEntity = cloudSongDao.getById(songId)
        if (cloudEntity != null && cloudEntity.isDownloaded) {
            val localFile = File(cloudEntity.localFilePath ?: cloudEntity.localSongId ?: "")
            if (localFile.isFile && localFile.canRead() && localFile.length() > 0) {
                val fileUri = Uri.fromFile(localFile).toString()
                return@withContext PlaybackResolution(
                    uri = fileUri,
                    mimeType = if (localFile.extension == "webm") "audio/webm" else "audio/mp4",
                    bitrate = null,
                    expiresAt = Long.MAX_VALUE,
                    cacheKey = "local_$songId"
                )
            }
        }

        val videoId = song.youtubeId ?: song.id.removePrefix("yt_")
        if (videoId.isBlank()) {
            Timber.tag(TAG).w("Cannot resolve track with empty video ID: %s", song.id)
            return@withContext null
        }

        resolveVideoId(videoId)
    }

    suspend fun resolveVideoId(videoId: String): PlaybackResolution? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_")
        if (!Regex("[A-Za-z0-9_-]{11}").matches(cleanId)) return@withContext null

        val fullId = "yt_$cleanId"
        val cloudEntity = cloudSongDao.getById(fullId) ?: cloudSongDao.getById(cleanId)
        if (cloudEntity != null && cloudEntity.isDownloaded) {
            val localFile = File(cloudEntity.localFilePath ?: cloudEntity.localSongId ?: "")
            if (localFile.isFile && localFile.canRead() && localFile.length() > 0) {
                val fileUri = Uri.fromFile(localFile).toString()
                return@withContext PlaybackResolution(
                    uri = fileUri,
                    mimeType = if (localFile.extension == "webm") "audio/webm" else "audio/mp4",
                    bitrate = null,
                    expiresAt = Long.MAX_VALUE,
                    cacheKey = "local_$cleanId"
                )
            }
        }

        val now = System.currentTimeMillis()
        cache.removeIf { (it.expiresAt ?: Long.MAX_VALUE) <= now }
        cache[cleanId]?.let { cached ->
            val expiresAt = cached.expiresAt ?: Long.MAX_VALUE
            if (System.currentTimeMillis() < expiresAt) {
                return@withContext cached
            } else {
                cache.remove(cleanId)
            }
        }

        var stream: com.theveloper.pixelplay.data.youtube.YouTubeAudioStream? = null
        val maxRetries = 2
        for (attempt in 1..maxRetries) {
            stream = streamExtractor.getStream(cleanId)
            if (stream != null) break
            if (attempt < maxRetries) {
                delay(300L * attempt)
            }
        }

        if (stream == null) {
            Timber.tag(TAG).e("Failed to resolve stream URL for track video ID: %s", cleanId)
            return@withContext null
        }

        val resolution = PlaybackResolution(
            uri = stream.url,
            mimeType = stream.mimeType,
            bitrate = stream.bitrate / 1000,
            expiresAt = stream.expiresAt,
            cacheKey = "yt_$cleanId"
        )
        cache[cleanId] = resolution
        resolution
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId.removePrefix("yt_"))
        streamExtractor.invalidate(videoId)
    }

    companion object {
        private const val TAG = "ResolverOrchestrator"
    }
}
