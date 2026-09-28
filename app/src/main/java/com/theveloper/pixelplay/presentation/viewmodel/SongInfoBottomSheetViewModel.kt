package com.theveloper.pixelplay.presentation.viewmodel

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.toArtist
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.AudioMeta
import com.theveloper.pixelplay.utils.AudioMetaUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import com.theveloper.pixelplay.data.database.EnrichmentDao
import com.theveloper.pixelplay.data.database.TrackAnalysisEntity
import com.theveloper.pixelplay.data.database.TrackEditorialEntity
import com.theveloper.pixelplay.data.database.ExternalIdEntity
import com.theveloper.pixelplay.data.database.GenreEntity
import com.theveloper.pixelplay.data.database.TagEntity
import com.theveloper.pixelplay.data.database.MoodEntity
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.enrichment.EnrichmentRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.analysis.ml.SimilarityEngine
import com.theveloper.pixelplay.data.youtube.SongDownloadManager
import com.theveloper.pixelplay.data.youtube.DownloadNotificationManager
import com.theveloper.pixelplay.data.youtube.DownloadProgress
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.CloudSongEntity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@HiltViewModel
class SongInfoBottomSheetViewModel @Inject constructor(
    private val musicDao: MusicDao,
    private val enrichmentDao: EnrichmentDao,
    private val similarityEngine: SimilarityEngine,
    private val enrichmentRepository: EnrichmentRepository,
    private val lyricsRepository: LyricsRepository,
    @ApplicationContext private val appContext: Context,
    private val songDownloadManager: SongDownloadManager,
    private val downloadCoordinator: com.theveloper.pixelplay.data.youtube.DownloadCoordinator,
    private val downloadNotificationManager: DownloadNotificationManager,
    private val cloudSongDao: CloudSongDao,
    private val metadataGatherer: com.theveloper.pixelplay.data.metadata.SongMetadataGatherer,
) : ViewModel() {

    /**
     * The song as shown on the Details tab: duration, genre, album and artist filled in for
     * online songs (cached, looked up once), so every song shows the same four basics.
     */
    private val _detailsSong = MutableStateFlow<Song?>(null)
    val detailsSong: StateFlow<Song?> = _detailsSong.asStateFlow()

    /**
     * Where a song's audio file is on this device: the file itself for library songs, the
     * downloaded copy for downloaded online songs, and null for online songs that only stream
     * (their "youtube://…" address isn't a path, so the Details tab hides the Path row).
     */
    private val _localFilePath = MutableStateFlow<String?>(null)
    val localFilePath: StateFlow<String?> = _localFilePath.asStateFlow()

    fun loadDetails(song: Song) {
        _detailsSong.value = metadataGatherer.applyCached(song)
        _localFilePath.value = song.path.takeIf { song.isLocal && it.isNotBlank() }
        viewModelScope.launch(Dispatchers.IO) {
            _localFilePath.value = resolveLocalFilePath(song)
            val current = _detailsSong.value ?: song
            if (!song.isLocal) {
                // Someone is reading this song's details: worth asking MusicBrainz too.
                val filled = runCatching { metadataGatherer.gatherDeep(current, timeoutMs = 15_000) }.getOrNull()
                if (filled != null && filled != current && _detailsSong.value?.id == song.id) _detailsSong.value = filled
            }
        }
    }

    private suspend fun resolveLocalFilePath(song: Song): String? {
        if (song.isLocal) return song.path.takeIf { it.isNotBlank() }
        if (!isCloudSong(song)) return song.path.takeIf { File(it).isFile }
        val entity = cloudSongDao.getById(song.id)
            ?: song.youtubeId?.let { cloudSongDao.getDownloadsByVideoId(it).firstOrNull() }
        return entity?.let { e ->
            (e.localFilePath ?: e.localSongId)
                ?.takeIf { e.isDownloaded && it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile && it.canRead() && it.length() > 0 }
                ?.absolutePath
        }
    }

    private val _trackAnalysis = MutableStateFlow<TrackAnalysisEntity?>(null)
    val trackAnalysis: StateFlow<TrackAnalysisEntity?> = _trackAnalysis.asStateFlow()

    private val _trackEditorial = MutableStateFlow<TrackEditorialEntity?>(null)
    val trackEditorial: StateFlow<TrackEditorialEntity?> = _trackEditorial.asStateFlow()

    private val _trackLyrics = MutableStateFlow<Lyrics?>(null)
    val trackLyrics: StateFlow<Lyrics?> = _trackLyrics.asStateFlow()

    private val _externalIds = MutableStateFlow<List<ExternalIdEntity>>(emptyList())
    val externalIds: StateFlow<List<ExternalIdEntity>> = _externalIds.asStateFlow()

    private val _trackGenres = MutableStateFlow<List<GenreEntity>>(emptyList())
    val trackGenres: StateFlow<List<GenreEntity>> = _trackGenres.asStateFlow()

    private val _trackTags = MutableStateFlow<List<TagEntity>>(emptyList())
    val trackTags: StateFlow<List<TagEntity>> = _trackTags.asStateFlow()

    private val _trackMoods = MutableStateFlow<List<MoodEntity>>(emptyList())
    val trackMoods: StateFlow<List<MoodEntity>> = _trackMoods.asStateFlow()

    private val _similarTracks = MutableStateFlow<List<Song>>(emptyList())
    val similarTracks: StateFlow<List<Song>> = _similarTracks.asStateFlow()

    // Download state
    private val _downloadEvent = Channel<String>(Channel.BUFFERED)
    val downloadEvent = _downloadEvent.receiveAsFlow()

    val downloadUiState: StateFlow<Map<String, DownloadProgress>> = songDownloadManager.downloadProgressMap
    val downloadedIds: StateFlow<Set<String>> = downloadCoordinator.downloadedIds

    private val _isEnriching = MutableStateFlow(false)
    val isEnriching: StateFlow<Boolean> = _isEnriching.asStateFlow()

    fun loadAnalysisAndSimilarTracks(song: Song) {
        val songId = song.id.toLongOrNull() ?: return
        viewModelScope.launch {
            // Load initial cached data
            loadAllMetadataFromDatabase(song, songId)

            // Auto-enrich if missing analysis or editorial info
            val hasAnalysis = _trackAnalysis.value != null
            val hasEditorial = _trackEditorial.value != null
            if (!hasAnalysis || !hasEditorial) {
                _isEnriching.value = true
                withContext(Dispatchers.IO) {
                    runCatching {
                        enrichmentRepository.enrichSong(songId, forceOnline = false, forceAnalysis = false)
                    }
                }
                // Re-load data after enrichment completes
                loadAllMetadataFromDatabase(song, songId)
                _isEnriching.value = false
            }

            val similarIds = similarityEngine.getSimilarTracks(songId, limit = 5)
            if (similarIds.isNotEmpty()) {
                val similarEntities = withContext(Dispatchers.IO) {
                    musicDao.getSongsByIdsListSimple(similarIds)
                }
                _similarTracks.value = similarEntities.map { it.toSong() }
            } else {
                _similarTracks.value = emptyList()
            }
        }
    }

    private suspend fun loadAllMetadataFromDatabase(song: Song, songId: Long) {
        withContext(Dispatchers.IO) {
            _trackAnalysis.value = enrichmentDao.getAnalysis(songId)
            _trackEditorial.value = enrichmentDao.getEditorial(songId)
            _trackLyrics.value = lyricsRepository.getStoredLyrics(song)?.first
            _externalIds.value = enrichmentDao.getExternalIds(songId)
            _trackGenres.value = enrichmentDao.getGenresForTrack(songId)
            _trackTags.value = enrichmentDao.getTagsForTrack(songId)
            _trackMoods.value = enrichmentDao.getMoodsForTrack(songId)
        }
    }

    fun triggerManualLyricsSearch(song: Song) {
        viewModelScope.launch {
            _isEnriching.value = true
            val lyrics = withContext(Dispatchers.IO) {
                lyricsRepository.getLyrics(song, forceRefresh = true)
            }
            _trackLyrics.value = lyrics
            _isEnriching.value = false
        }
    }

    data class SongLocationInfo(
        val label: String,
        val value: String,
        val isCloud: Boolean,
    )

    enum class ToneTarget {
        Ringtone,
        Notification,
        Alarm,
    }

    sealed interface ToneActionResult {
        data class Success(val message: String) : ToneActionResult
        data class NeedsSystemWritePermission(val message: String) : ToneActionResult
        data class Error(val message: String) : ToneActionResult
    }

    private val _audioMeta = MutableStateFlow<AudioMeta?>(null)
    private val _resolvedArtists = MutableStateFlow<List<Artist>>(emptyList())
    val resolvedArtists: StateFlow<List<Artist>> = _resolvedArtists.asStateFlow()

    val audioMeta: StateFlow<AudioMeta?> = _audioMeta.asStateFlow()

    fun loadArtistsForSong(song: Song) {
        val refs = song.artists
        if (refs.isEmpty() || refs.size < 2) {
            _resolvedArtists.value = emptyList()
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ids = refs.map { it.id }.filter { it > 0L }.distinct()
            val entitiesById = if (ids.isNotEmpty()) {
                musicDao.getArtistsByIds(ids).associateBy { it.id }
            } else {
                emptyMap()
            }
            val resolved = refs.map { ref ->
                entitiesById[ref.id]?.toArtist()
                    ?: Artist(id = ref.id, name = ref.name, songCount = 0)
            }
            _resolvedArtists.value = resolved
        }
    }

    fun loadAudioMeta(song: Song) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Downloaded online songs: read the downloaded file, not the "youtube://…" address.
            val filePath = if (song.isLocal) song.path else resolveLocalFilePath(song) ?: song.path
            val meta = AudioMetaUtils.getAudioMetadata(
                musicDao = musicDao,
                id = song.id.toLongOrNull() ?: -1L,
                filePath = filePath,
                deepScan = false
            )
            // Streaming-only online songs: the rendition being streamed (format + bitrate).
            val streamed = if (meta.mimeType == null && meta.bitrate == null) {
                com.theveloper.pixelplay.data.youtube.StreamFormatRegistry.get(song.youtubeId ?: song.id)?.let {
                    AudioMeta(mimeType = it.codecMimeType, bitrate = it.bitrate.takeIf { b -> b > 0 }, sampleRate = null)
                }
            } else null
            _audioMeta.value = streamed ?: meta
        }
    }

    fun getSongLocationInfo(song: Song): SongLocationInfo {
        val provider = getCloudProviderLabel(song.contentUriString)
        return if (provider != null) {
            SongLocationInfo(
                label = "Provider",
                value = provider,
                isCloud = true,
            )
        } else {
            SongLocationInfo(
                label = "Path",
                value = song.path,
                isCloud = false,
            )
        }
    }

    fun hasSystemWritePermission(): Boolean {
        return Settings.System.canWrite(appContext)
    }

    fun createSystemWriteSettingsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${appContext.packageName}")
        }
    }

    fun setSongAsTone(song: Song, target: ToneTarget, onComplete: (ToneActionResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                setSongAsToneInternal(song, target)
            }
            onComplete(result)
        }
    }

    fun isSongEditable(song: Song): Boolean {
        if (getCloudProviderLabel(song.contentUriString) != null) return false

        if (song.path.isNotBlank()) {
            val file = File(song.path)
            return file.exists() && file.isFile
        }

        val uri = song.contentUriString
        return uri.startsWith("content://") || uri.startsWith("file://")
    }

    private fun getCloudProviderLabel(contentUriString: String): String? {
        val normalized = contentUriString.lowercase().trim()
        return when {
            normalized.startsWith("gdrive://") || normalized.startsWith("gdrive:") -> "Google Drive"
            else -> null
        }
    }

    private suspend fun setSongAsToneInternal(song: Song, target: ToneTarget): ToneActionResult {
        if (getCloudProviderLabel(song.contentUriString) != null) {
            return ToneActionResult.Error(
                appContext.getString(R.string.song_info_ringtone_local_only)
            )
        }

        val ringtoneUri = runCatching { resolveMediaStoreAudioUri(song) }.getOrNull()
            ?: return ToneActionResult.Error(
                appContext.getString(R.string.song_info_ringtone_missing_file)
            )

        if (!Settings.System.canWrite(appContext)) {
            return ToneActionResult.NeedsSystemWritePermission(
                appContext.getString(R.string.song_info_ringtone_permission_prompt)
            )
        }

        return runCatching {
            markAsToneCandidate(ringtoneUri, target)
            RingtoneManager.setActualDefaultRingtoneUri(
                appContext,
                target.ringtoneManagerType,
                ringtoneUri,
            )
            ToneActionResult.Success(
                appContext.getString(
                    R.string.song_info_tone_success,
                    song.title,
                    appContext.getString(target.successLabelResId),
                )
            )
        }.getOrElse { throwable ->
            ToneActionResult.Error(
                appContext.getString(
                    R.string.song_info_ringtone_failed,
                    throwable.localizedMessage ?: throwable.javaClass.simpleName
                )
            )
        }
    }

    private suspend fun resolveMediaStoreAudioUri(song: Song): Uri? {
        song.id.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let { id ->
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            }
            ?.takeIf(::mediaStoreAudioExists)
            ?.let { return it }

        song.contentUriString
            .takeIf { it.startsWith("content://") }
            ?.toUri()
            ?.takeIf { it.authority == MediaStore.AUTHORITY }
            ?.let { return it }

        findMediaStoreAudioUriByPath(song.path)?.let { return it }

        val file = File(song.path)
        if (!file.exists()) return null

        return scanAudioFile(file, song.mimeType)
            ?.takeIf { it.authority == MediaStore.AUTHORITY }
            ?: findMediaStoreAudioUriByPath(song.path)
    }

    private fun findMediaStoreAudioUriByPath(path: String): Uri? {
        if (path.isBlank()) return null
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(path)

        return runCatching {
            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        }.getOrNull()
    }

    private suspend fun scanAudioFile(file: File, mimeType: String?): Uri? =
        suspendCancellableCoroutine { continuation ->
            val mimeTypes = mimeType
                ?.takeIf { it.isNotBlank() }
                ?.let { arrayOf(it) }
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(file.absolutePath),
                mimeTypes,
            ) { _, uri ->
                if (continuation.isActive) {
                    continuation.resume(uri)
                }
            }
        }

    private fun mediaStoreAudioExists(uri: Uri): Boolean {
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } == true
        }.getOrDefault(false)
    }

    private fun markAsToneCandidate(uri: Uri, target: ToneTarget) {
        runCatching {
            val values = ContentValues().apply {
                when (target) {
                    ToneTarget.Ringtone -> put(MediaStore.Audio.Media.IS_RINGTONE, true)
                    ToneTarget.Notification -> put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
                    ToneTarget.Alarm -> put(MediaStore.Audio.Media.IS_ALARM, true)
                }
            }
            appContext.contentResolver.update(uri, values, null, null)
        }
    }

    private val ToneTarget.ringtoneManagerType: Int
        get() = when (this) {
            ToneTarget.Ringtone -> RingtoneManager.TYPE_RINGTONE
            ToneTarget.Notification -> RingtoneManager.TYPE_NOTIFICATION
            ToneTarget.Alarm -> RingtoneManager.TYPE_ALARM
        }

    private val ToneTarget.successLabelResId: Int
        get() = when (this) {
            ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_label
            ToneTarget.Notification -> R.string.song_info_tone_notification_label
            ToneTarget.Alarm -> R.string.song_info_tone_alarm_label
        }

    /**
     * Initiates download for an online/cloud song.
     * Shows notification with progress and updates cloud_songs table when complete.
     */
    fun downloadSong(song: Song) {
        if (song.youtubeId == null && !song.id.startsWith("yt_") && !song.id.startsWith("spotify_")) {
            viewModelScope.launch {
                _downloadEvent.send("This song cannot be downloaded")
            }
            return
        }

        // Hand off to the app-scoped coordinator: running the transfer in this ViewModel's
        // scope cancelled it as soon as the options sheet closed.
        downloadCoordinator.download(song)
        viewModelScope.launch { _downloadEvent.send("Downloading ${song.title}…") }
    }

    /**
     * Saves online song metadata to cloud_songs table so it persists even without downloading.
     */
    fun persistCloudSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            if (song.youtubeId != null || song.id.startsWith("yt_")) {
                val videoId = song.youtubeId ?: song.id.removePrefix("yt_")
                cloudSongDao.upsert(
                    CloudSongEntity(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        duration = song.duration,
                        thumbnailUrl = song.albumArtUriString,
                        youtubeId = videoId,
                        sourceType = "youtube",
                        contentUriString = song.contentUriString
                    )
                )
            }
        }
    }

    /**
     * Returns true if the song is an online/cloud song (not downloaded locally).
     */
    fun isCloudSong(song: Song): Boolean {
        return song.youtubeId != null || song.id.startsWith("yt_") || song.id.startsWith("spotify_")
    }
}
