package com.theveloper.pixelplay.data.library

import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.CloudSongEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.worker.collectArtistNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps downloaded online songs in the library tables (`songs`, `albums`, `artists`,
 * `song_artist_cross_ref`) so the Albums and Artists tabs pick them up and keep them.
 *
 * Why this exists:
 * - The MediaStore sync removes every LOCAL row it cannot find in MediaStore. Downloads live
 *   in the app's own folder, so they are stored as [SourceType.DOWNLOAD] and repaired here
 *   after every sync (a REBUILD clears all rows, including theirs).
 * - The Artists tab counts songs through `song_artist_cross_ref`, which downloads never had.
 * - Albums and artists are matched to existing library rows by normalised name, so a
 *   download joins the album you already own instead of creating a twin.
 */
@Singleton
class DownloadedLibraryIndexer @Inject constructor(
    private val musicDao: MusicDao,
    private val cloudSongDao: CloudSongDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager,
    private val duplicateRepair: LibraryDuplicateRepair
) {
    data class Input(
        val cloudSongId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val albumArtist: String? = null,
        val albumArtUri: String?,
        val durationMs: Long,
        val genre: String? = null,
        val year: Int = 0,
        val trackNumber: Int = 0,
        val file: File,
        val mimeType: String? = null,
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val isFavorite: Boolean = false
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    /** Starts watching downloads and library syncs. Safe to call more than once. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // Any download added, removed or moved -> reconcile (debounced for batch downloads).
            cloudSongDao.observeDownloads()
                .map { rows -> rows.mapTo(HashSet()) { it.id to (it.localFilePath ?: it.localSongId) } }
                .distinctUntilChanged()
                .debounce(1_500)
                .collect { runCatchingReconcile("downloads changed") }
        }
        scope.launch {
            // A finished library sync may have removed or rebuilt rows: put downloads back.
            var wasSyncing = false
            syncManager.isSyncing.collect { syncing ->
                if (wasSyncing && !syncing) {
                    delay(750)
                    runCatchingReconcile("library sync finished")
                }
                wasSyncing = syncing
            }
        }
    }

    /** Adds (or refreshes) one downloaded song. Returns the library song id. */
    suspend fun index(input: Input): Long = mutex.withLock { indexLocked(input, settings()) }

    /** Makes the library tables match the downloads on disk. */
    suspend fun reconcile() = mutex.withLock { reconcileLocked() }

    private suspend fun runCatchingReconcile(reason: String) {
        try {
            reconcile()
            // After downloads are in place (and after every library sync): fold duplicate
            // artists / albums left by older versions into one.
            duplicateRepair.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Reconcile failed (%s)", reason)
        }
    }

    private data class Settings(
        val artistDelimiters: List<String>,
        val wordDelimiters: List<String>
    )

    private suspend fun settings() = Settings(
        artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first(),
        wordDelimiters = userPreferencesRepository.artistWordDelimitersFlow.first()
    )

    private suspend fun reconcileLocked() {
        val settings = settings()
        val downloads = cloudSongDao.getAllOnce().filter { it.isDownloaded }
        val keepIds = HashSet<Long>(downloads.size)
        var removedAny = false
        var added = 0

        downloads.forEachIndexed { index, row ->
            if (index % 100 == 99) yield()
            val songId = libraryId(row.id)
            val path = row.localFilePath ?: row.localSongId
            val file = path?.takeIf { it.isNotBlank() }?.let(::File)
            val present = file != null && file.isFile && file.length() > 0
            if (!present) {
                // Only give up on the download when its folder is readable (storage mounted)
                // and the file is really gone; never on a transient storage error.
                val folderReadable = file?.parentFile?.let { it.isDirectory && it.canRead() } == true
                if (file == null || folderReadable) {
                    cloudSongDao.updateDownloadStatus(row.id, false, null, null)
                    removeSong(songId)
                    removedAny = true
                } else {
                    keepIds += songId
                }
                return@forEachIndexed
            }
            keepIds += songId
            val existing = musicDao.getSongByIdOnce(songId)
            if (existing == null) {
                indexLocked(row.toInput(file!!), settings)
                added++
            } else if (!SourceType.isAppDownload(existing.filePath) && !SourceType.isAppDownload(existing.contentUriString)) {
                // The id is taken by a different (MediaStore) song: never touch that row.
                Timber.tag(TAG).w("Library id %d of download %s is used by another song", songId, row.id)
            } else if (needsRegrouping(existing, row.album)) {
                // Indexed by an older version (album id = title hash, e.g. one shared
                // "YouTube Music" album for every download): group it properly now.
                val regrouped = existing.toInput(row.id, file!!).let { input ->
                    // The library row may carry an album named after the song; the cloud row
                    // knows whether there really was an album.
                    if (CollectionKeys.isPlaceholderAlbum(row.album)) input.copy(album = null) else input
                }
                indexLocked(regrouped, settings)
                removedAny = true // old album / artist rows may now be empty
                added++
            } else {
                musicDao.markSongAsDownload(songId)
                if (musicDao.countCrossRefsForSong(songId) == 0) {
                    writeCrossRefs(songId, existing.artistName, existing.title, settings)
                }
            }
        }

        // Download rows whose cloud record is gone (download deleted elsewhere).
        musicDao.getDownloadSongIds().filterNot { it in keepIds }.forEach { stale ->
            removeSong(stale)
            removedAny = true
        }
        if (removedAny) {
            musicDao.deleteOrphanedAlbums()
            musicDao.deleteOrphanedArtists()
        }
        if (added > 0 || removedAny) {
            Timber.tag(TAG).i("Reconciled downloads: %d added, removed=%s", added, removedAny)
        }
    }

    private suspend fun removeSong(songId: Long) {
        musicDao.deleteCrossRefsForSong(songId)
        musicDao.deleteById(songId)
    }

    private suspend fun indexLocked(input: Input, settings: Settings): Long {
        val songId = libraryId(input.cloudSongId)
        musicDao.getSongByIdOnce(songId)?.let { taken ->
            if (!SourceType.isAppDownload(taken.filePath) && !SourceType.isAppDownload(taken.contentUriString)) {
                Timber.tag(TAG).w("Library id %d is used by another song; download not indexed", songId)
                return songId
            }
        }
        val artistNames = artistNames(input.artist, input.title, settings)
        val artistIds = artistNames.map { name -> ensureArtist(name) }
        val primaryArtistId = artistIds.first()

        val albumArtistName = input.albumArtist?.let(CollectionKeys::cleanArtistName)?.trim()?.takeIf { it.isNotBlank() }
            ?: artistNames.first()
        // No album information (a video, or the "YouTube Music" placeholder): the song goes into
        // the artist's "Singles" album instead of a one-song album named after the song.
        val albumTitle = input.album?.trim()?.takeUnless { CollectionKeys.isPlaceholderAlbum(it) }
            ?: CollectionKeys.SINGLES_ALBUM_TITLE
        val albumId = ensureAlbum(
            title = albumTitle,
            albumArtist = albumArtistName,
            artistName = input.artist.ifBlank { albumArtistName },
            artistId = primaryArtistId,
            artUri = input.albumArtUri,
            year = input.year
        )

        val parentDir = input.file.parentFile?.absolutePath.orEmpty()
        val previous = musicDao.getSongByIdOnce(songId)
        val entity = SongEntity(
            id = songId,
            title = input.title,
            artistName = CollectionKeys.cleanArtistName(input.artist).ifBlank { input.artist },
            artistId = primaryArtistId,
            albumArtist = input.albumArtist?.let(CollectionKeys::cleanArtistName)?.takeIf { it.isNotBlank() },
            albumName = albumTitle,
            albumId = albumId,
            contentUriString = android.net.Uri.fromFile(input.file).toString(),
            albumArtUriString = input.albumArtUri ?: previous?.albumArtUriString,
            duration = input.durationMs.takeIf { it > 0 } ?: previous?.duration ?: 0L,
            genre = input.genre?.takeIf { it.isNotBlank() } ?: previous?.genre,
            filePath = input.file.absolutePath,
            parentDirectoryPath = parentDir,
            isFavorite = input.isFavorite || previous?.isFavorite == true,
            lyrics = previous?.lyrics,
            trackNumber = input.trackNumber.takeIf { it > 0 } ?: previous?.trackNumber ?: 0,
            year = input.year.takeIf { it > 0 } ?: previous?.year ?: 0,
            dateAdded = previous?.dateAdded ?: System.currentTimeMillis(),
            mimeType = input.mimeType ?: previous?.mimeType ?: guessMime(input.file),
            bitrate = input.bitrate ?: previous?.bitrate,
            sampleRate = input.sampleRate ?: previous?.sampleRate,
            sourceType = SourceType.DOWNLOAD
        )
        musicDao.insertSongs(listOf(entity))
        musicDao.replaceCrossRefsForSong(
            songId,
            artistIds.distinct().mapIndexed { index, id -> SongArtistCrossRef(songId, id, isPrimary = index == 0) }
        )
        return songId
    }

    private suspend fun writeCrossRefs(songId: Long, artist: String, title: String, settings: Settings) {
        val ids = artistNames(artist, title, settings).map { ensureArtist(it) }.distinct()
        musicDao.replaceCrossRefsForSong(songId, ids.mapIndexed { i, id -> SongArtistCrossRef(songId, id, i == 0) })
    }

    private fun artistNames(artist: String, title: String, settings: Settings): List<String> {
        val raw = CollectionKeys.cleanArtistName(artist).ifBlank { "Unknown artist" }
        return runCatching {
            collectArtistNames(raw, title, settings.artistDelimiters, settings.wordDelimiters, extractFromTitle = false)
        }.getOrDefault(emptyList())
            .map { CollectionKeys.cleanArtistName(it).trim() }
            .filter { it.isNotBlank() }
            .distinctBy { CollectionKeys.normalizeArtist(it) }
            .ifEmpty { listOf(raw) }
    }

    /** Reuses the library artist with the same name, or creates one in the download id range. */
    private suspend fun ensureArtist(name: String): Long {
        musicDao.getArtistIdByNormalizedName(name)?.let { return it }
        val id = CollectionKeys.downloadArtistId(name)
        musicDao.insertArtistsIgnoreConflicts(listOf(ArtistEntity(id = id, name = name, trackCount = 0)))
        return id
    }

    /** Reuses the library album with the same (album artist, title) key, or creates one. */
    private suspend fun ensureAlbum(
        title: String,
        albumArtist: String,
        artistName: String,
        artistId: Long,
        artUri: String?,
        year: Int
    ): Long {
        val key = CollectionKeys.albumKey(albumArtist, title)
        val wantedTitle = CollectionKeys.normalizeAlbum(title)
        val existing = (musicDao.getAlbumsForArtistNameOnce(albumArtist) +
            if (!artistName.equals(albumArtist, ignoreCase = true)) musicDao.getAlbumsForArtistNameOnce(artistName) else emptyList())
            .filter { CollectionKeys.normalizeAlbum(it.title) == wantedTitle }
            // Skip albums made by older versions for downloads only; they are being replaced.
            .firstOrNull { isDownloadAlbumId(it.id) || musicDao.countNonDownloadSongsInAlbum(it.id) > 0 }
        val id = existing?.id ?: CollectionKeys.downloadAlbumId(key)
        if (existing == null) {
            musicDao.insertAlbumsIgnoreConflicts(
                listOf(
                    AlbumEntity(
                        id = id,
                        title = title,
                        artistName = albumArtist,
                        artistId = artistId,
                        albumArtUriString = artUri,
                        songCount = 0,
                        dateAdded = System.currentTimeMillis(),
                        year = year,
                        albumArtist = albumArtist
                    )
                )
            )
        }
        musicDao.fillAlbumBlanks(id, artUri, year, albumArtist)
        return id
    }

    private fun isDownloadAlbumId(id: Long) =
        id >= CollectionKeys.DOWNLOAD_ALBUM_BASE && id < CollectionKeys.DOWNLOAD_ALBUM_BASE + (1L shl 39)

    /** Old download rows: album id outside the download range, in an album with no other songs. */
    private suspend fun needsRegrouping(existing: SongEntity, cloudAlbum: String?): Boolean {
        val id = existing.albumId
        // Indexed before "Singles": a one-song album named after the song.
        if (CollectionKeys.isPlaceholderAlbum(cloudAlbum) &&
            existing.albumName != CollectionKeys.SINGLES_ALBUM_TITLE &&
            CollectionKeys.normalizeAlbum(existing.albumName) == CollectionKeys.normalizeAlbum(existing.title)
        ) return true
        // Indexed with a channel-style artist ("X - Topic", "XVEVO").
        if (CollectionKeys.cleanArtistName(existing.artistName) != existing.artistName.trim()) return true
        if (isDownloadAlbumId(id)) return false
        if (CollectionKeys.isPlaceholderAlbum(existing.albumName)) return true
        // A library (MediaStore) album the download joined: keep it.
        return musicDao.countNonDownloadSongsInAlbum(id) == 0
    }

    private fun SongEntity.toInput(cloudSongId: String, file: File) = Input(
        cloudSongId = cloudSongId,
        title = title,
        artist = artistName,
        album = albumName,
        albumArtist = albumArtist,
        albumArtUri = albumArtUriString,
        durationMs = duration,
        genre = genre,
        year = year,
        trackNumber = trackNumber,
        file = file,
        mimeType = mimeType,
        bitrate = bitrate,
        sampleRate = sampleRate,
        isFavorite = isFavorite
    )

    private fun CloudSongEntity.toInput(file: File) = Input(
        cloudSongId = id,
        title = title,
        artist = artist,
        album = album,
        albumArtUri = thumbnailUrl,
        durationMs = duration,
        file = file
    )

    private fun guessMime(file: File): String? = when (file.extension.lowercase()) {
        "m4a", "mp4", "aac" -> "audio/mp4"
        "webm", "weba" -> "audio/webm"
        "opus", "ogg" -> "audio/ogg"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        else -> null
    }

    companion object {
        private const val TAG = "DownloadIndexer"

        /** Library (SongEntity) id of the downloaded copy of online song [cloudSongId]. */
        fun libraryId(cloudSongId: String): Long = cloudSongId.hashCode().toLong() and 0x7FFFFFFFFFFFFFFFL
    }
}
