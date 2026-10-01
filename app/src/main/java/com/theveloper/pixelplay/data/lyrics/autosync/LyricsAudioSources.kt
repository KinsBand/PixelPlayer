package com.theveloper.pixelplay.data.lyrics.autosync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import com.theveloper.pixelplay.data.accounts.CatalogPlaybackResolver
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.gdrive.GDriveStreamProxy
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stream.YouTubeStreamProxy
import com.theveloper.pixelplay.data.youtube.downloadedAudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where [LyricsAutoSync] can read a song's audio, cheapest first:
 *
 *  1. **On the phone already** ([Source.Ready]): a local file, a downloaded song (found through
 *     the downloads table even when the queued song still points at `youtube://`), or a streamed
 *     song the stream proxy has kept whole in its song cache. No network.
 *  2. **Streaming from YouTube** ([Source.StreamCache]), including Spotify / Apple Music / Deezer
 *     songs that play through their YouTube match: playing the song fills the proxy's song cache,
 *     so the analysis waits for that copy instead of downloading the audio a second time. On an
 *     unmetered network it may also ask the proxy to finish the copy early (paced, exactly as the
 *     player does for the next song).
 *  3. **Other streams** ([Source.Network]: Google Drive, plain URLs): read through their URL.
 *     Costs a second download, so [LyricsAutoSync] only does it on unmetered networks.
 */
@Singleton
class LyricsAudioSources @Inject constructor(
    @ApplicationContext context: Context,
    private val cloudSongDao: CloudSongDao,
    private val youTubeStreamProxy: YouTubeStreamProxy,
    private val gdriveStreamProxy: GDriveStreamProxy,
    private val catalogPlaybackResolver: CatalogPlaybackResolver
) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    sealed interface Source {
        /** Decodable now without any network: [kind] is "local", "downloaded" or "stream cache". */
        data class Ready(val uri: Uri, val kind: String) : Source

        /** A YouTube stream: available once the stream proxy holds the whole song. */
        data class StreamCache(val videoId: String) : Source

        /** Readable only over the network (a second download of the song). */
        data class Network(val uri: Uri) : Source
    }

    suspend fun resolve(song: Song): Source? {
        localUri(song)?.let { return Source.Ready(it, "local") }
        val content = song.contentUriString
        val scheme = content.substringBefore("://", "").lowercase()

        val videoId = song.youtubeId?.removePrefix("yt_")?.takeIf { it.isNotBlank() }
            ?: when (scheme) {
                "youtube" -> Uri.parse(content).host?.removePrefix("yt_")
                in CatalogPlaybackResolver.SCHEMES -> catalogPlaybackResolver.knownVideoId(content)
                else -> null
            }
        if (videoId != null) {
            downloadedFile(videoId)?.let { return Source.Ready(Uri.fromFile(it), "downloaded") }
            cachedStreamFile(videoId)?.let { return Source.Ready(Uri.fromFile(it), "stream cache") }
            return Source.StreamCache(videoId)
        }

        return when (scheme) {
            "gdrive" -> {
                if (!gdriveStreamProxy.ensureReady(5_000L)) null
                else gdriveStreamProxy.resolveGDriveUri(content)?.takeIf { it.isNotBlank() }?.let { Source.Network(Uri.parse(it)) }
            }
            "http", "https" -> Source.Network(Uri.parse(content))
            else -> null
        }
    }

    /** The whole song as kept by the stream proxy, or null while it isn't complete. */
    fun cachedStreamFile(videoId: String): File? =
        youTubeStreamProxy.bodyCache.lookup(videoId)?.file?.takeIf { it.isFile && it.canRead() && it.length() > 0 }

    /**
     * Asks the stream proxy to finish its copy of [videoId] now. Unmetered networks only (the
     * proxy checks too) and paced so it never competes with the song that's playing.
     */
    suspend fun completeStreamCache(videoId: String): Boolean =
        if (isUnmetered()) runCatching { youTubeStreamProxy.prefetchWhole(videoId) }.getOrDefault(false) else false

    fun isUnmetered(): Boolean = connectivity?.let { !it.isActiveNetworkMetered && it.activeNetwork != null } == true

    private suspend fun downloadedFile(videoId: String): File? =
        runCatching { cloudSongDao.getDownloadsByVideoId(videoId).firstNotNullOfOrNull { it.downloadedAudioFile() } }.getOrNull()

    private fun localUri(song: Song): Uri? {
        val content = song.contentUriString
        if (content.startsWith("content://") || content.startsWith("file://")) return Uri.parse(content)
        val path = song.path
        if (path.isNotBlank() && !path.contains("://")) {
            val file = File(path)
            if (file.isFile && file.canRead()) return Uri.fromFile(file)
        }
        return null
    }
}
