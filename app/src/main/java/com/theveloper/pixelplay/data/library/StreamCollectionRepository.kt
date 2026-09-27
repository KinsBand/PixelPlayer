package com.theveloper.pixelplay.data.library

import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SavedAlbumEntity
import com.theveloper.pixelplay.data.database.StreamAlbumEntity
import com.theveloper.pixelplay.data.database.StreamArtistEntity
import com.theveloper.pixelplay.data.database.StreamCollectionDao
import com.theveloper.pixelplay.data.metadata.SongMetadataGatherer
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.data.worker.collectArtistNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the songs you liked but only stream (in-app likes and YouTube Music / Spotify /
 * Apple Music likes) into Library albums and artists.
 *
 * The result is written to `stream_albums` / `stream_artists`; the Albums and Artists tab
 * queries `UNION` those tables with the local library, so a like or unlike shows up in both
 * tabs by itself (Room re-runs the paged queries when the tables change).
 *
 * A streamed song whose album or artist is already in the library is merged into it: it adds
 * to that card's "N streaming" count instead of creating a second card.
 */
@Singleton
class StreamCollectionRepository @Inject constructor(
    private val connected: ConnectedLibraryRepository,
    private val cloudSongDao: CloudSongDao,
    private val favoritesDao: FavoritesDao,
    private val musicDao: MusicDao,
    private val streamDao: StreamCollectionDao,
    private val metadataGatherer: SongMetadataGatherer,
    private val artistImageRepository: ArtistImageRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val mutex = Mutex()
    private val gatherTick = MutableStateFlow(0)
    private var gatherJob: Job? = null
    private var imageJob: Job? = null
    private val gatherAttempted = HashSet<String>()

    private val _streamSongs = MutableStateFlow<List<Song>>(emptyList())
    /** Liked songs that are only streamed (not local files, not downloaded). */
    val streamSongs: StateFlow<List<Song>> = _streamSongs.asStateFlow()

    private val _songsByAlbumId = MutableStateFlow<Map<Long, List<Song>>>(emptyMap())
    /** Streamed songs per album id: a stream album id, or the library album they merge into. */
    val songsByAlbumId: StateFlow<Map<Long, List<Song>>> = _songsByAlbumId.asStateFlow()

    private val _songsByArtistId = MutableStateFlow<Map<Long, List<Song>>>(emptyMap())
    val songsByArtistId: StateFlow<Map<Long, List<Song>>> = _songsByArtistId.asStateFlow()

    private val _albumIdByKey = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Album key ([CollectionKeys.albumKey]) -> id of the card it shows on (library or stream album). */
    val albumIdByKey: StateFlow<Map<String, Long>> = _albumIdByKey.asStateFlow()

    private val _artistIdByKey = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Normalised artist name -> id of the card it shows on (library or stream artist). */
    val artistIdByKey: StateFlow<Map<String, Long>> = _artistIdByKey.asStateFlow()

    private val _artistNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    /** Names of the stream-only artists, by id (used to open their profile by name). */
    val artistNames: StateFlow<Map<Long, String>> = _artistNames.asStateFlow()

    /** Library album id -> number of streamed liked songs merged into it. */
    val mergedAlbumCounts: StateFlow<Map<Long, Int>> = streamDao.observeMergedAlbumCounts()
        .map { rows -> rows.associate { it.targetId to it.streamCount } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Library artist id -> number of streamed liked songs merged into it. */
    val mergedArtistCounts: StateFlow<Map<Long, Int>> = streamDao.observeMergedArtistCounts()
        .map { rows -> rows.associate { it.targetId to it.streamCount } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        // Saving or unsaving an album rebuilds the collection so its card appears / goes away.
        scope.launch {
            streamDao.observeSavedAlbums()
                .map { rows -> rows.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collect { gatherTick.value = gatherTick.value + 1 }
        }
        scope.launch {
            combine(
                connected.likedSongs,
                cloudSongDao.getDownloadedSongIds().distinctUntilChanged(),
                // Room re-runs these on every write to the table (a sync writes thousands), so
                // only a real change in the counts may trigger a rebuild.
                musicDao.getAlbumCount().distinctUntilChanged(),
                musicDao.getArtistCount().distinctUntilChanged(),
                gatherTick
            ) { liked, downloaded, _, _, _ -> liked to downloaded.toHashSet() }
                .debounce(800)
                .collect { (liked, downloaded) ->
                    try {
                        rebuild(liked, downloaded)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Could not rebuild streamed collection")
                    }
                }
        }
    }

    fun artistName(id: Long): String? = _artistNames.value[id]

    /** What we know about an online album opened from the player, by its stream album id. */
    data class OnlineAlbumHint(
        val title: String,
        val artist: String,
        val artUri: String?,
        val year: Int,
        /** The song that was playing; shown on the page even if the tracklist can't be found. */
        val seedSong: Song
    )

    private val onlineAlbumHints = java.util.concurrent.ConcurrentHashMap<Long, OnlineAlbumHint>()

    /**
     * Album page id for an online song's album: the library or stream album it already shows on,
     * or a new stream album id (remembered with [hint] so the album page can load it by name).
     */
    fun albumIdForOnlineSong(song: Song): Long {
        val artist = song.albumArtist?.trim()?.takeIf { it.isNotBlank() }
            ?: song.artist.trim().ifBlank { "Unknown artist" }
        val title = song.album.trim().takeUnless { CollectionKeys.isPlaceholderAlbum(it) }
            ?: song.title.trim()
        val key = CollectionKeys.albumKey(artist, title)
        _albumIdByKey.value[key]?.let { return it }
        val id = CollectionKeys.streamAlbumId(key)
        onlineAlbumHints[id] = OnlineAlbumHint(title, artist, song.albumArtUriString, song.year, song.copy(albumId = id))
        return id
    }

    fun onlineAlbumHint(id: Long): OnlineAlbumHint? = onlineAlbumHints[id]

    fun observeIsSaved(albumId: Long): Flow<Boolean> = streamDao.observeIsSaved(albumId)

    suspend fun savedAlbum(albumId: Long): SavedAlbumEntity? = streamDao.getSavedAlbumById(albumId)

    /** Saves [album] to Library → Albums. Returns the id its card uses. */
    suspend fun saveAlbum(album: Album, trackCount: Int): Long {
        val artist = album.albumArtist?.takeIf { it.isNotBlank() } ?: album.artist
        val key = CollectionKeys.albumKey(artist, album.title)
        val id = if (CollectionKeys.isStreamAlbumId(album.id)) album.id else CollectionKeys.streamAlbumId(key)
        streamDao.insertSavedAlbum(
            SavedAlbumEntity(
                id = id,
                albumKey = key,
                title = album.title,
                artistName = artist,
                albumArtUriString = album.albumArtUriString,
                year = album.year,
                trackCount = trackCount.coerceAtLeast(1),
                savedAt = System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun unsaveAlbum(albumId: Long) = streamDao.deleteSavedAlbum(albumId)

    private suspend fun rebuild(liked: List<Song>, downloadedIds: Set<String>) = mutex.withLock {
        val downloadedVideos = downloadedIds.mapNotNullTo(HashSet()) { id ->
            id.takeIf { it.startsWith("yt_") }?.removePrefix("yt_")
        }
        val streamed = liked
            .asSequence()
            // Library songs have numeric ids. (Song.isLocal can't be used: Spotify / Apple Music
            // songs have no YouTube id or http path, so they report as LOCAL.)
            .filter { it.id.toLongOrNull() == null && !it.isDownloaded }
            .filter { it.id !in downloadedIds && (it.youtubeId == null || it.youtubeId !in downloadedVideos) }
            .map { metadataGatherer.applyCached(it) }
            .distinctBy { it.id }
            .toList()
            .let { candidates ->
                // A liked stream of a song you already have as a file or download (often a
                // different video of it) is the same song: don't list it twice.
                if (candidates.isEmpty()) return@let candidates
                val owned = musicDao.getTitleArtistRows().mapTo(HashSet()) { RecordingKeys.of(it.title, it.artistName) }
                candidates.filterNot { RecordingKeys.of(it) in owned }
            }
        _streamSongs.value = streamed
        requestMissingBasics(streamed)

        val delimiters = userPreferencesRepository.artistDelimitersFlow.first()
        val wordDelimiters = userPreferencesRepository.artistWordDelimitersFlow.first()
        val likedAt = runCatching { favoritesDao.getAllFavoritesOnce() }.getOrDefault(emptyList())
            .associate { it.songId to it.timestamp }

        // Library rows to merge into.
        val localAlbumByKey = HashMap<String, Long>()
        musicDao.getAlbumKeyRows().forEach { row ->
            val title = CollectionKeys.normalizeAlbum(row.title)
            localAlbumByKey.putIfAbsent(CollectionKeys.normalizeArtist(row.artistName) + "|" + title, row.id)
            row.albumArtist?.takeIf { it.isNotBlank() }?.let {
                localAlbumByKey.putIfAbsent(CollectionKeys.normalizeArtist(it) + "|" + title, row.id)
            }
        }
        val localArtistByKey = HashMap<String, Long>()
        musicDao.getArtistNameRows().forEach { row ->
            localArtistByKey.putIfAbsent(CollectionKeys.normalizeArtist(row.name), row.id)
        }

        class AlbumAcc(val key: String, val title: String, val albumArtist: String) {
            val songs = ArrayList<Song>()
            var art: String? = null
            var year = 0
            var addedAt = 0L
        }
        class ArtistAcc(val key: String, val name: String) {
            val songs = ArrayList<Song>()
            var addedAt = 0L
        }
        val albums = LinkedHashMap<String, AlbumAcc>()
        val artists = LinkedHashMap<String, ArtistAcc>()

        streamed.forEach { song ->
            val names = runCatching {
                collectArtistNames(song.artist, song.title, delimiters, wordDelimiters, extractFromTitle = false)
            }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotBlank() }
                .ifEmpty { listOf(song.artist.trim().ifBlank { "Unknown artist" }) }
            val albumArtist = song.albumArtist?.trim()?.takeIf { it.isNotBlank() } ?: names.first()
            val albumTitle = song.album.trim().takeUnless { CollectionKeys.isPlaceholderAlbum(it) }
                ?: song.title.trim()
            val added = likedAt[song.id] ?: 0L

            val albumKey = CollectionKeys.albumKey(albumArtist, albumTitle)
            val album = albums.getOrPut(albumKey) { AlbumAcc(albumKey, albumTitle, albumArtist) }
            album.songs += song
            if (album.art == null) album.art = song.albumArtUriString?.takeIf { it.isNotBlank() }
            if (song.year > album.year) album.year = song.year
            if (added > album.addedAt) album.addedAt = added

            names.distinctBy { CollectionKeys.normalizeArtist(it) }.forEach { name ->
                val key = CollectionKeys.normalizeArtist(name)
                if (key.isBlank()) return@forEach
                val artist = artists.getOrPut(key) { ArtistAcc(key, name) }
                artist.songs += song
                if (added > artist.addedAt) artist.addedAt = added
            }
        }

        // Saved albums always get a card, even with none of their songs liked.
        val saved = runCatching { streamDao.getSavedAlbumsOnce() }.getOrDefault(emptyList())
        val savedTrackCounts = HashMap<String, Int>()
        saved.forEach { row ->
            savedTrackCounts[row.albumKey] = row.trackCount
            val album = albums.getOrPut(row.albumKey) { AlbumAcc(row.albumKey, row.title, row.artistName) }
            if (album.art == null) album.art = row.albumArtUriString?.takeIf { it.isNotBlank() }
            if (row.year > album.year) album.year = row.year
            if (row.savedAt > album.addedAt) album.addedAt = row.savedAt
        }

        val existingArtists = streamDao.getArtistsOnce().associateBy { it.id }
        val artistRows = artists.values.map { acc ->
            val id = CollectionKeys.streamArtistId(acc.name)
            StreamArtistEntity(
                id = id,
                name = acc.name,
                imageUrl = existingArtists[id]?.imageUrl,
                trackCount = acc.songs.distinctBy { it.id }.size,
                dateAdded = acc.addedAt,
                mergedInto = localArtistByKey[acc.key]
            )
        }
        val artistIdByKey = artists.keys.zip(artistRows).associate { (key, row) -> key to (row.mergedInto ?: row.id) }

        val albumRows = albums.values.map { acc ->
            val merged = localAlbumByKey[acc.key]
            StreamAlbumEntity(
                id = CollectionKeys.streamAlbumId(acc.key),
                albumKey = acc.key,
                title = acc.title,
                artistName = acc.albumArtist,
                artistId = artistIdByKey[CollectionKeys.normalizeArtist(acc.albumArtist)]
                    ?: CollectionKeys.streamArtistId(acc.albumArtist),
                albumArtist = acc.albumArtist,
                albumArtUriString = acc.art,
                songCount = maxOf(acc.songs.distinctBy { it.id }.size, savedTrackCounts[acc.key] ?: 0),
                dateAdded = acc.addedAt,
                year = acc.year,
                mergedInto = merged
            )
        }

        // In-memory lookups for album / artist pages.
        _songsByAlbumId.value = albums.values.zip(albumRows).associate { (acc, row) ->
            (row.mergedInto ?: row.id) to acc.songs.sortedWith(trackOrder)
        }
        _songsByArtistId.value = artists.values.zip(artistRows).associate { (acc, row) ->
            (row.mergedInto ?: row.id) to acc.songs.toList()
        }
        _artistNames.value = artistRows.filter { it.mergedInto == null }.associate { it.id to it.name }
        _albumIdByKey.value = albums.values.zip(albumRows).associate { (acc, row) -> acc.key to (row.mergedInto ?: row.id) }
        _artistIdByKey.value = artistIdByKey

        // Write only what changed, so the Library tabs don't reload for nothing.
        val oldAlbums = streamDao.getAlbumsOnce().associateBy { it.id }
        val newAlbumIds = albumRows.mapTo(HashSet()) { it.id }
        val albumUpserts = albumRows.filter { oldAlbums[it.id] != it }
        val albumRemovals = oldAlbums.keys.filterNot { it in newAlbumIds }
        val newArtistIds = artistRows.mapTo(HashSet()) { it.id }
        val artistUpserts = artistRows.filter { existingArtists[it.id] != it }
        val artistRemovals = existingArtists.keys.filterNot { it in newArtistIds }
        if (albumUpserts.isNotEmpty() || albumRemovals.isNotEmpty() ||
            artistUpserts.isNotEmpty() || artistRemovals.isNotEmpty()
        ) {
            streamDao.applyDiff(albumUpserts, albumRemovals, artistUpserts, artistRemovals)
        }
        fetchMissingArtistImages(artistRows)
    }

    /** Streamed likes often arrive without an album; look a batch up, then rebuild. */
    private fun requestMissingBasics(streamed: List<Song>) {
        if (gatherJob?.isActive == true) return
        val targets = streamed
            .filter { metadataGatherer.needsBasics(it) && gatherAttempted.add(it.id) }
            .take(GATHER_BATCH)
        if (targets.isEmpty()) return
        gatherJob = scope.launch {
            var changed = false
            runCatching { metadataGatherer.gatherBasics(targets) { changed = true } }
            if (changed) gatherTick.value = gatherTick.value + 1
        }
    }

    private fun fetchMissingArtistImages(rows: List<StreamArtistEntity>) {
        if (imageJob?.isActive == true) return
        val missing = rows.filter { it.mergedInto == null && it.imageUrl.isNullOrBlank() }
            .sortedByDescending { it.trackCount }
            .take(IMAGE_BATCH)
        if (missing.isEmpty()) return
        imageJob = scope.launch {
            missing.forEach { row ->
                val url = runCatching { artistImageRepository.getArtistImageUrl(row.name, row.id) }.getOrNull()
                if (!url.isNullOrBlank()) streamDao.updateArtistImage(row.id, url)
            }
        }
    }

    private val trackOrder = compareBy<Song>({ it.discNumber ?: 1 }, { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }, { it.title.lowercase() })

    companion object {
        private const val TAG = "StreamCollection"
        private const val GATHER_BATCH = 60
        private const val IMAGE_BATCH = 24
    }
}
