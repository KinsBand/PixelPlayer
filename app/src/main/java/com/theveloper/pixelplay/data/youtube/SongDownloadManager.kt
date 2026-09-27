package com.theveloper.pixelplay.data.youtube

import android.content.Context
import android.os.Environment
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.theveloper.pixelplay.data.stream.awaitResponse
import com.theveloper.pixelplay.di.YouTubeOkHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadProgress {
    data object Idle : DownloadProgress()
    data class Resolving(val songId: String) : DownloadProgress()
    data class Downloading(val songId: String, val percent: Int) : DownloadProgress()
    /** Paused by the user; the bytes so far are kept and [SongDownloadManager] resumes from them. */
    data class Paused(val songId: String, val percent: Int) : DownloadProgress()
    data class Tagging(val songId: String) : DownloadProgress()
    data class Scanning(val songId: String) : DownloadProgress()
    data class Completed(val songId: String, val filePath: String) : DownloadProgress()
    data class Failed(val songId: String, val error: String) : DownloadProgress()
}

@Singleton
class SongDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youTubeStreamExtractor: YouTubeStreamExtractor,
    private val spotifyResolver: com.theveloper.pixelplay.data.spotify.SpotifyToYouTubeResolver,
    private val cloudSongDao: CloudSongDao,
    private val musicDao: com.theveloper.pixelplay.data.database.MusicDao,
    private val okHttpClient: OkHttpClient,
    @YouTubeOkHttpClient private val youTubeHttpClient: OkHttpClient,
    private val metadataGatherer: com.theveloper.pixelplay.data.metadata.SongMetadataGatherer,
    private val libraryIndexer: com.theveloper.pixelplay.data.library.DownloadedLibraryIndexer
) {
    private val downloadLocks = Array(32) { Mutex() }
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadProgressMap = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, DownloadProgress>> = _downloadProgressMap.asStateFlow()

    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgressMap
        .map { map -> map.values.lastOrNull() ?: DownloadProgress.Idle }
        .stateIn(managerScope, SharingStarted.Eagerly, DownloadProgress.Idle)

    private fun updateProgress(songId: String, progress: DownloadProgress) {
        _downloadProgressMap.update { current -> current + (songId to progress) }
        if (progress is DownloadProgress.Completed || progress is DownloadProgress.Failed) {
            managerScope.launch {
                delay(2000)
                _downloadProgressMap.update { current -> if (current[songId] == progress) current - songId else current }
            }
        }
    }

    private val downloadDir: File by lazy {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "music")
        File(baseDir, "PixelPlayer").also { dir ->
            dir.mkdirs()
            // Downloads are indexed by the app itself (DownloadedLibraryIndexer). Keep media
            // scanners out, or older Android versions import every file a second time.
            runCatching { File(dir, ".nomedia").takeUnless { it.exists() }?.createNewFile() }
        }
    }

    /**
     * Downloads an online song to local storage.
     * Pipeline: resolve stream URL -> download -> tag metadata -> scan to MediaStore
     */
    suspend fun downloadSong(song: Song, quality: DownloadQuality? = null): Result<File> =
        downloadLocks[(song.id.hashCode() and Int.MAX_VALUE) % downloadLocks.size].withLock {
            downloadLocked(song, quality)
        }

    /** Recent download speed (bytes/s) measured from real transfers, or 0 before the first one. */
    @Volatile
    private var measuredBytesPerSecond: Long = 0L

    /** Rough seconds to download [bytes] now: measured speed, else a guess from the network type. */
    fun estimateSeconds(bytes: Long): Long {
        val speed = measuredBytesPerSecond.takeIf { it > 0 } ?: run {
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            val kbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 } ?: 4_000
            // Link bandwidth is optimistic; assume about a third of it for a single transfer.
            kbps * 1000L / 8 / 3
        }
        return (bytes / speed.coerceAtLeast(1)).coerceAtLeast(1)
    }

    /** High / Medium / Low for [song] with exact (or estimated) sizes, for the download menu. */
    suspend fun downloadOptions(song: Song): List<DownloadOption> = withContext(Dispatchers.IO) {
        val saved = cloudSongDao.getById(song.id)
        val videoId = resolveVideoId(song, saved) ?: return@withContext emptyList()
        val manifest = youTubeStreamExtractor.streamManifest(videoId)
        DownloadQuality.entries.mapNotNull { quality ->
            val stream = quality.pick(manifest) ?: return@mapNotNull null
            val size = stream.contentLength.takeIf { it > 0 }
                ?: (stream.bitrate.toLong() / 8 * (song.duration / 1000).coerceAtLeast(1))
            DownloadOption(quality, stream.codecLabel(), stream.bitrate / 1000, size)
        }
    }

    private suspend fun resolveVideoId(song: Song, saved: com.theveloper.pixelplay.data.database.CloudSongEntity?): String? =
        (song.youtubeId ?: saved?.youtubeId ?: if (com.theveloper.pixelplay.data.accounts.CatalogTracks.isCatalogSongId(song.id)) {
            spotifyResolver.resolveSpotifyTrackToVideoId(
                com.theveloper.pixelplay.data.accounts.CatalogTracks.matchKey(song.id), song.title, song.artist,
                song.duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), song.creditsAndRelease.isrc
            )
        } else song.id.removePrefix("yt_"))?.removePrefix("yt_")

    // Background-priority threads: a download must never steal CPU from audio decoding/rendering.
    private suspend fun downloadLocked(song: Song, quality: DownloadQuality?): Result<File> = withContext(com.theveloper.pixelplay.data.stream.PlaybackBandwidthGate.downloadDispatcher) {
        val songId = song.id
        var stagingFile: File? = null
        try {
            val saved = cloudSongDao.getById(songId)
            val savedFile = saved?.takeIf { it.isDownloaded }?.let {
                File(it.localFilePath ?: it.localSongId ?: "")
            }
            if (savedFile != null && savedFile.isFile && savedFile.canRead() && savedFile.length() > 0) {
                updateProgress(songId, DownloadProgress.Completed(songId, savedFile.absolutePath))
                return@withContext Result.success(savedFile)
            }
            // Step 1: Resolve stream URL
            updateProgress(songId, DownloadProgress.Resolving(songId))
            val videoId = resolveVideoId(song, saved)
                ?: throw java.io.IOException("No matching audio source")

            // Prefer the MP4/AAC rendition because jaudiotagger can tag it; fall back to the
            // WebM/Opus rendition (untagged; metadata still lives in the library DB) instead of
            // failing, since some videos only expose Opus.
            // Extraction is the flakiest step (player-script changes, throttled clients), so a
            // miss is retried with a forced re-extraction before the download is failed.
            var manifest = youTubeStreamExtractor.streamManifest(videoId)
            var manifestAttempt = 0
            while (manifest.isEmpty() && manifestAttempt < MANIFEST_RETRIES) {
                manifestAttempt++
                delay(700L * manifestAttempt)
                youTubeStreamExtractor.invalidate(videoId)
                manifest = youTubeStreamExtractor.streamManifest(videoId, forceRefresh = true)
            }
            // A quality picked in the download menu wins; otherwise the default rule below.
            val stream: YouTubeAudioStream = quality?.pick(manifest)
                ?: YouTubeAudioStream.select(manifest.filter { it.container == "mp4" }, AudioQualityPreset.AUTO)
                    ?: YouTubeAudioStream.select(manifest, AudioQualityPreset.AUTO)
                    ?: run {
                        val error = "No downloadable audio stream is available"
                        updateProgress(songId, DownloadProgress.Failed(songId, error))
                        return@withContext Result.failure(Exception(error))
                    }
            val renditionKey = stream.renditionKey()

            // Step 2: Download audio file
            updateProgress(songId, DownloadProgress.Downloading(songId, 0))
            val sourceKey = sourceKeyFor(songId)
            val fileName = "${sanitizeFileName(videoId)}-$sourceKey.${stream.fileExtension}"
            val outputFile = File(downloadDir, fileName)
            // Same filesystem, genuine extension for the tagger, invisible to the library. The name
            // is fixed per song + rendition so a paused download resumes from the same bytes.
            val partial = partialFile(sourceKey, renditionKey, stream.fileExtension)
            if (!partial.exists()) partial.createNewFile()
            stagingFile = partial

            // Resumable: a failed attempt keeps the bytes already written and continues from
            // there with a re-extracted URL for the *same* rendition (same itag => same bytes).
            var currentStream: YouTubeAudioStream = stream
            var attempt = 0
            val maxAttempts = 4
            while (true) {
                try {
                    downloadFile(currentStream, partial, songId)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    Timber.tag(TAG).w(e, "Download attempt $attempt failed for song: $songId (have ${partial.length()} bytes)")
                    if (attempt >= maxAttempts) throw e
                    delay(1000L * attempt)
                    val refreshed = youTubeStreamExtractor.streamManifest(videoId, forceRefresh = true)
                    // A failed re-extraction is not fatal: retry the URL we already have.
                    if (refreshed.isEmpty()) continue
                    val same = selectRendition(refreshed, renditionKey)
                    currentStream = if (same != null && same.sameBytesAs(currentStream)) {
                        same
                    } else {
                        // Different file on the server side: restart from zero.
                        java.io.FileOutputStream(partial).use { }
                        same ?: selectRendition(refreshed.filter { it.container == stream.container }, null)
                            ?: throw java.io.IOException("Could not re-resolve audio stream")
                    }
                }
            }

            // Verify the bytes are real, decodable audio before we keep them (a byte-complete
            // transfer can still be an HTML error page or a truncated container). Same idea as
            // Spotube's lossless-sources probe: only accept a source that served playable audio.
            if (!isPlayableAudio(partial)) {
                youTubeStreamExtractor.invalidate(videoId)
                throw java.io.IOException("Downloaded file is not playable audio")
            }

            // Step 3: Tag metadata (best effort: YouTube serves DASH-style MP4 files that some
            // tag writers reject; an untagged file still plays and is indexed via the DB).
            // Tagging happens on a copy so a tag writer failure can never damage the audio.
            updateProgress(songId, DownloadProgress.Tagging(songId))
            if (stream.container == "mp4") {
                val tagged = File.createTempFile(".tag-", ".${stream.fileExtension}", downloadDir)
                try {
                    partial.copyTo(tagged, overwrite = true)
                    tagMetadata(tagged, song)
                    if (isPlayableAudio(tagged)) {
                        java.nio.file.Files.move(tagged.toPath(), partial.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Tagging failed for %s; keeping untagged audio", songId)
                } finally {
                    tagged.delete()
                }
            }
            currentCoroutineContext().ensureActive()
            // Atomic rename happens only after transfer (and tagging, when possible) succeeded.
            java.nio.file.Files.move(partial.toPath(), outputFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            stagingFile = null

            // Step 4: Add it to the library (the app indexes its own downloads; see
            // DownloadedLibraryIndexer — MediaStore does not index the app's folder).
            updateProgress(songId, DownloadProgress.Scanning(songId))

            // Step 5: Update cloud song record and local DB library sync
            val ytId = videoId
            val localUriStr = android.net.Uri.fromFile(outputFile).toString()
            cloudSongDao.upsert(
                com.theveloper.pixelplay.data.database.CloudSongEntity(
                    id = songId,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    thumbnailUrl = song.albumArtUriString,
                    youtubeId = ytId,
                    sourceType = saved?.sourceType ?: com.theveloper.pixelplay.data.accounts.CatalogTracks.sourceType(song.id),
                    contentUriString = saved?.contentUriString ?: song.contentUriString.ifBlank { "youtube://$ytId" },
                    isDownloaded = true,
                    localSongId = outputFile.absolutePath,
                    localFilePath = outputFile.absolutePath,
                    localContentUri = localUriStr
                )
            )
            // (The upsert above already records the local file: a second UPDATE here made every
            // downloads observer — including the player's source swap — fire twice.)

            var actualBitrate = (stream.bitrate / 1000).takeIf { it > 0 } ?: 128
            var actualSampleRate = 44100
            try {
                val audioFile = AudioFileIO.read(outputFile)
                actualBitrate = audioFile.audioHeader.bitRateAsNumber.toInt()
                actualSampleRate = audioFile.audioHeader.sampleRateAsNumber
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not read audio header for bitrate/sampleRate")
            }

            // Album, album artist, year and genre from the metadata lookup the tagging step
            // already ran, so the download joins the right album instead of "YouTube Music".
            val filled = runCatching { metadataGatherer.applyCached(song) }.getOrDefault(song)
            // Never fail a finished download just because the library index could not be updated.
            try {
                libraryIndexer.index(
                    com.theveloper.pixelplay.data.library.DownloadedLibraryIndexer.Input(
                        cloudSongId = songId,
                        title = song.title,
                        artist = song.artist,
                        album = filled.album.takeUnless {
                            com.theveloper.pixelplay.data.library.CollectionKeys.isPlaceholderAlbum(it)
                        } ?: song.album,
                        albumArtist = filled.albumArtist ?: song.albumArtist,
                        albumArtUri = song.albumArtUriString ?: filled.albumArtUriString,
                        durationMs = song.duration.takeIf { it > 0 } ?: filled.duration,
                        genre = filled.genre ?: song.genre,
                        year = filled.year.takeIf { it > 0 } ?: song.year,
                        trackNumber = filled.trackNumber.takeIf { it > 0 } ?: song.trackNumber,
                        file = outputFile,
                        mimeType = stream.mimeType,
                        bitrate = actualBitrate,
                        sampleRate = actualSampleRate,
                        isFavorite = song.isFavorite
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Downloaded %s but could not add it to the library index", songId)
            }

            updateProgress(songId, DownloadProgress.Completed(songId, outputFile.absolutePath))
            Timber.tag(TAG).i("Download completed: ${outputFile.absolutePath}")
            Result.success(outputFile)

        } catch (e: CancellationException) {
            if (pauseRequested.remove(songId)) {
                // Paused: keep the partial file so resuming continues from here.
                val percent = (_downloadProgressMap.value[songId] as? DownloadProgress.Downloading)?.percent ?: 0
                stagingFile = null
                _downloadProgressMap.update { it + (songId to DownloadProgress.Paused(songId, percent)) }
            } else {
                _downloadProgressMap.update { it - songId }
            }
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Download failed for song: $songId")
            updateProgress(songId, DownloadProgress.Failed(songId, e.message ?: "Unknown error"))
            Result.failure(e)
        } finally {
            stagingFile?.delete()
        }
    }

    private fun YouTubeAudioStream.sameBytesAs(other: YouTubeAudioStream): Boolean =
        contentLength > 0 && contentLength == other.contentLength && renditionKey() == other.renditionKey()

    /**
     * Downloads [stream] into [outputFile], appending to whatever is already there.
     * Uses NewPipe's googlevideo request shape and 10 MiB range chunks (like yt-dlp), so long
     * tracks are not throttled or cut off mid-transfer.
     */
    private suspend fun downloadFile(stream: YouTubeAudioStream, outputFile: File, songId: String) {
        val total = stream.contentLength.takeIf { it > 0 }
        var position = outputFile.length()
        if (total != null && position > total) {
            java.io.FileOutputStream(outputFile).use { }
            position = 0L
        }
        var lastEmittedPercent = -1
        val startPosition = position
        val startedAt = android.os.SystemClock.elapsedRealtime()
        FileOutputStream(outputFile, true).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (total == null || position < total) {
                currentCoroutineContext().ensureActive()
                val chunkEnd = total?.let { YouTubeHttp.chunkEnd(position, it - 1) }
                val request = YouTubeHttp.playbackRequest(stream.url, position, chunkEnd)
                val finished = youTubeHttpClient.newCall(request).awaitResponse().use { response ->
                    val ok = response.code == 206 || (response.code == 200 && position == 0L)
                    if (!ok) throw java.io.IOException("Download failed: HTTP ${response.code}")
                    val type = response.body.contentType()?.toString()?.substringBefore(';')
                    if (type != null && !type.startsWith("audio/") && type !in setOf("video/mp4", "application/octet-stream")) {
                        throw java.io.IOException("Response is not audio")
                    }
                    val expected = chunkEnd?.let { it - position + 1 } ?: response.body.contentLength()
                    var readInChunk = 0L
                    response.body.byteStream().use { input ->
                        while (expected < 0 || readInChunk < expected) {
                            currentCoroutineContext().ensureActive()
                            val max = if (expected < 0) buffer.size.toLong() else minOf(buffer.size.toLong(), expected - readInChunk)
                            val readStartedAt = android.os.SystemClock.elapsedRealtime()
                            val read = input.read(buffer, 0, max.toInt())
                            if (read < 0) break
                            // While the playing song is still streaming in, pace the download so
                            // its buffer always wins the bandwidth.
                            val pause = com.theveloper.pixelplay.data.stream.PlaybackBandwidthGate.pauseAfterRead(
                                read, android.os.SystemClock.elapsedRealtime() - readStartedAt
                            )
                            if (pause > 0) delay(pause)
                            output.write(buffer, 0, read)
                            readInChunk += read
                            position += read
                            val percent = if (total != null) (position * 100 / total).toInt().coerceIn(0, 99) else 0
                            // Emit only when the visible percentage changes: one emission per 64 KiB
                            // read meant hundreds of StateFlow updates (and UI recompositions) per second.
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                updateProgress(songId, DownloadProgress.Downloading(songId, percent))
                            }
                        }
                    }
                    if (expected >= 0) DownloadIntegrity.requireComplete(readInChunk, expected)
                    total == null // unknown size: a single open-ended request is the whole file
                }
                if (finished) break
            }
            output.fd.sync()
        }
        if (total != null) DownloadIntegrity.requireComplete(outputFile.length(), total)
        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
        val transferred = position - startPosition
        // Only transfers big enough to say something about the connection.
        if (elapsedMs > 500 && transferred > 256 * 1024) {
            val speed = transferred * 1000 / elapsedMs
            measuredBytesPerSecond = if (measuredBytesPerSecond == 0L) speed else (measuredBytesPerSecond * 2 + speed) / 3
        }
    }

    private suspend fun readArtwork(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > 8 * 1024 * 1024) throw java.io.IOException("Artwork exceeds 8 MiB")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private suspend fun tagMetadata(file: File, original: Song) {
        // Fill album, genre, track / disc number, album artist, cover etc. first (cached after
        // the first lookup, so usually instant). Title and artist stay exactly as the user saw them.
        val song = runCatching { metadataGatherer.gather(original, timeoutMs = 6_000) }.getOrDefault(original)
            .copy(title = original.title, artist = original.artist)
        val extra = metadataGatherer.cachedFor(original)
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, song.title)
        tag.setField(FieldKey.ARTIST, song.artist)
        val album = song.album.ifEmpty { "YouTube Music" }
        tag.setField(FieldKey.ALBUM, album)
        if (song.year > 0) tag.setField(FieldKey.YEAR, song.year.toString())
        song.genre?.takeIf { it.isNotBlank() && it != "YouTube Music" }?.let { tag.setField(FieldKey.GENRE, it) }
        // Optional fields: some containers reject some keys, which must not fail the download.
        fun optional(key: FieldKey, value: String?) {
            if (!value.isNullOrBlank()) runCatching { tag.setField(key, value) }
                .onFailure { Timber.tag(TAG).d("Tag %s not written: %s", key, it.message) }
        }
        optional(FieldKey.ALBUM_ARTIST, song.albumArtist ?: extra?.albumArtist)
        optional(FieldKey.TRACK, song.trackNumber.takeIf { it > 0 }?.toString())
        optional(FieldKey.DISC_NO, song.discNumber?.takeIf { it > 0 }?.toString())
        optional(FieldKey.ISRC, song.creditsAndRelease.isrc ?: extra?.isrc)
        optional(FieldKey.RECORD_LABEL, song.creditsAndRelease.recordLabel ?: extra?.label)
        optional(FieldKey.BARCODE, song.creditsAndRelease.upcEan ?: extra?.upc)
        optional(FieldKey.BPM, song.musicalFeatures.bpm?.let { Math.round(it).toString() })
        optional(FieldKey.MOOD, song.mixIntelligence.mood?.takeUnless { extra?.moodEstimated == true })
        // Highest-resolution version of the cover (1400 px where the host allows), falling
        // back to the URL as given if the upgraded one fails.
        val artworkCandidates = listOfNotNull(
            com.theveloper.pixelplay.data.metadata.ArtworkUrls.upgrade(song.albumArtUriString),
            song.albumArtUriString,
            original.albumArtUriString
        ).filter(String::isNotBlank).distinct()
        val artworkBytes = artworkCandidates.firstOrNull()?.let {
          var lastError: Exception? = null
          var found: ByteArray? = null
          for (uri in artworkCandidates) {
            var bytes: ByteArray? = null
            try {
            for (attempt in 0..2) {
                try {
                    bytes = if (uri.startsWith("http://") || uri.startsWith("https://")) {
                        okHttpClient.newCall(Request.Builder().url(uri).build()).awaitResponse().use { response ->
                            if (!response.isSuccessful) throw java.io.IOException("Artwork HTTP ${response.code}")
                            response.body.byteStream().use { readArtwork(it) }
                        }
                    } else context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { readArtwork(it) }
                    if (bytes == null || bytes.isEmpty()) throw java.io.IOException("Empty artwork")
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (attempt == 2) throw error
                    delay(300L * (attempt + 1))
                }
            }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
            }
            if (bytes != null) { found = bytes; break }
          }
          found ?: throw (lastError ?: java.io.IOException("Artwork unavailable"))
        }
        artworkBytes?.let { bytes ->
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) throw java.io.IOException("Invalid artwork image")
            val artwork = ArtworkFactory.getNew().apply {
                binaryData = bytes
                mimeType = options.outMimeType ?: throw java.io.IOException("Unknown artwork format")
            }
            tag.setField(artwork)
        }
        currentCoroutineContext().ensureActive()
        audioFile.commit()
        val verified = AudioFileIO.read(file)
        if (verified.audioHeader.trackLength <= 0 ||
            verified.tag.getFirst(FieldKey.TITLE) != song.title ||
            verified.tag.getFirst(FieldKey.ARTIST) != song.artist ||
            verified.tag.getFirst(FieldKey.ALBUM) != album ||
            (artworkBytes != null && verified.tag.firstArtwork?.binaryData?.contentEquals(artworkBytes) != true)) {
            throw java.io.IOException("Audio metadata verification failed")
        }
    }

    /** Song ids whose next cancellation is a pause (keep bytes) rather than a cancel. */
    private val pauseRequested: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Call right before cancelling [songId]'s job to pause instead of cancel. */
    fun requestPause(songId: String) {
        pauseRequested += songId
    }

    /** Forget a paused/cancelled download: drop its progress and any bytes kept for resuming. */
    fun discard(songId: String) {
        pauseRequested -= songId
        _downloadProgressMap.update { it - songId }
        val key = sourceKeyFor(songId)
        downloadDir.listFiles { file -> file.name.startsWith(".partial-$key-") }?.forEach { it.delete() }
    }

    private fun sourceKeyFor(songId: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(songId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)

    private fun partialFile(sourceKey: String, renditionKey: String, extension: String): File =
        File(downloadDir, ".partial-$sourceKey-${renditionKey.hashCode().toUInt().toString(16)}.$extension")

    fun resetProgress() {
        _downloadProgressMap.value = emptyMap()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)  // Leave room for the artist and stable source ID
    }

    /** Uses the platform demuxer to confirm [file] contains at least one audio track. */
    private fun isPlayableAudio(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_AUDIO_BYTES) return false
        val extractor = android.media.MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Downloaded file failed demuxer check: %s", file.name)
            false
        } finally {
            extractor.release()
        }
    }

    companion object {
        private const val TAG = "SongDownloadManager"
        private const val MANIFEST_RETRIES = 2
        private const val MIN_AUDIO_BYTES = 16 * 1024L

        /** Library (SongEntity) id used for the downloaded copy of the online song [songId]. */
        fun localLibraryId(songId: String): Long =
            com.theveloper.pixelplay.data.library.DownloadedLibraryIndexer.libraryId(songId)
    }
}
