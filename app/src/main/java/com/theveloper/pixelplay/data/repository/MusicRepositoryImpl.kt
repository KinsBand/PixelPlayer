package com.theveloper.pixelplay.data.repository

// import kotlinx.coroutines.withContext // May not be needed for Flow transformations

// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import timber.log.Timber

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.search.MusicSearchQuery
import com.theveloper.pixelplay.data.youtube.searchIdentity
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import androidx.paging.insertHeaderItem
import com.theveloper.pixelplay.data.model.DownloadState
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import com.theveloper.pixelplay.data.database.SearchHistoryEntity
import com.theveloper.pixelplay.data.database.toAlbum
import com.theveloper.pixelplay.data.database.toArtist
import com.theveloper.pixelplay.data.database.toSearchHistoryItem
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.FolderSource
import com.theveloper.pixelplay.data.model.StorageFilter
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.ui.theme.GenreThemeUtils
import com.theveloper.pixelplay.utils.DirectoryFilterUtils
import com.theveloper.pixelplay.utils.LogUtils
import com.theveloper.pixelplay.utils.StorageType
import com.theveloper.pixelplay.utils.StorageUtils
import com.theveloper.pixelplay.utils.toHexString
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.filter
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val musicDao: MusicDao,
    private val lyricsRepository: LyricsRepository,
    private val songRepository: SongRepository,
    private val favoritesDao: FavoritesDao,
    private val artistImageRepository: ArtistImageRepository,
    private val folderTreeBuilder: FolderTreeBuilder,
    private val youTubeRepository: com.theveloper.pixelplay.data.youtube.YouTubeRepository,
    private val cloudSongDao: com.theveloper.pixelplay.data.database.CloudSongDao,
    private val metadataGatherer: com.theveloper.pixelplay.data.metadata.SongMetadataGatherer,
    private val streamCollectionDao: com.theveloper.pixelplay.data.database.StreamCollectionDao
) : MusicRepository {

    companion object {
        /** Maximum number of search results to load at once to avoid memory issues with large libraries. */
        private const val SEARCH_RESULTS_LIMIT = 100
        private const val UNKNOWN_GENRE_NAME = "Unknown"
        private const val UNKNOWN_GENRE_ID = "unknown"
    }

    private val directoryScanMutex = Mutex()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val defaultLibraryPagingConfig = PagingConfig(
        pageSize = 50,
        enablePlaceholders = true,
        maxSize = 250
    )
    // Tracks the active prefetch job so a new flow emission cancels the previous one.
    @Volatile private var prefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchSongId: Long? = null


    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }

    /** Cached directory filter — recomputed when allowed/blocked dirs preferences change or when the set of song folders changes. */
    data class CachedDirFilter(val allowedParentDirs: List<String> = emptyList(), val applyFilter: Boolean = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cachedDirFilter: StateFlow<CachedDirFilter> = combine(
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow
    ) { allowed, blocked -> allowed to blocked }
        .flatMapLatest { (allowed, blocked) ->
            if (blocked.isEmpty()) {
                // No blocked dirs => no directory filter is applied. Avoid observing the
                // songs table at all in this (common) case.
                flowOf(CachedDirFilter(emptyList(), false))
            } else {
                // Recompute whenever the set of song folders changes (e.g. after a sync),
                // so the filter never freezes at a stale/empty snapshot. Previously this
                // was computed once, eagerly at construction — before the first sync — which
                // on some devices left allowedParentDirs empty while applyFilter stayed true,
                // making queue-building queries (getSongIdsSorted) return nothing and collapse
                // the playback queue to just the tapped song.
                musicDao.getDistinctParentDirectoriesFlow()
                    .distinctUntilChanged()
                    .map { parentDirs ->
                        val (dirs, apply) = DirectoryFilterUtils.computeAllowedParentDirs(
                            allowedDirs = allowed,
                            blockedDirs = blocked,
                            getAllParentDirs = { parentDirs },
                            normalizePath = ::normalizePath
                        )
                        CachedDirFilter(dirs, apply)
                    }
            }
        }
        .stateIn(repositoryScope, SharingStarted.Eagerly, CachedDirFilter())



    private fun List<Artist>.missingImageCandidates(): List<Pair<Long, String>> =
        asSequence()
            .filter { it.effectiveImageUrl.isNullOrBlank() && it.name.isNotBlank() }
            .map { it.id to it.name }
            .distinctBy { (_, name) -> name.trim().lowercase() }
            .toList()

    /**
     * One shared full-library list for every collector (library tabs, main VM, Your Music,
     * mashups, AI, voice search, [getAllSongsOnce]). Each collector used to run its own
     * full-table query and parse ~8 JSON columns per song, so the app held several complete,
     * independent copies of the library, and every write to `songs` re-built all of them at
     * once. The cache is dropped 5 s after the last collector leaves.
     */
    private val sharedAudioFiles: kotlinx.coroutines.flow.SharedFlow<List<Song>> by lazy {
        buildAudioFilesFlow().shareIn(
            scope = repositoryScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
                replayExpirationMillis = 0
            ),
            replay = 1
        )
    }

    override fun getAudioFiles(): Flow<List<Song>> = sharedAudioFiles

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildAudioFilesFlow(): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getAllSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.distinctUntilChanged().conflate().mapLatest { entities ->
            entities.mapIndexed { index, entity ->
                if (index % 128 == 0) kotlinx.coroutines.yield()
                entity.toSong()
            }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedSongs(sortOption: SortOption, storageFilter: com.theveloper.pixelplay.data.model.StorageFilter): Flow<PagingData<Song>> {
        return songRepository.getPaginatedSongs(sortOption, storageFilter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedAlbums(
        sortOption: SortOption,
        storageFilter: StorageFilter,
        minTracks: Int
    ): Flow<PagingData<Album>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultLibraryPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getAlbumsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                filterMode = storageFilter.toFilterMode(),
                                sortOrder = sortOption.storageKey,
                                minTracks = minTracks
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toAlbum() }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedArtists(
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): Flow<PagingData<Artist>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultLibraryPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getArtistsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                filterMode = storageFilter.toFilterMode(),
                                sortOrder = sortOption.storageKey
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toArtist() }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedFavoriteSongs(sortOption: SortOption, storageFilter: StorageFilter): Flow<PagingData<Song>> {
        return cloudSongDao.getFavoritedCloudSongs()
            .distinctUntilChanged()
            .flatMapLatest { cloudEntities ->
                val cloudSongs = when (storageFilter) {
                    StorageFilter.OFFLINE -> cloudEntities.filter { it.isDownloaded }
                    StorageFilter.ONLINE -> cloudEntities.filter { !it.isDownloaded }
                    StorageFilter.ALL -> cloudEntities
                }.map { cloud ->
                    Song(
                        id = cloud.id,
                        title = cloud.title,
                        artist = cloud.artist,
                        artistId = cloud.artist.hashCode().toLong(),
                        album = cloud.album ?: "YouTube Music",
                        albumId = (cloud.album ?: "YouTube Music").hashCode().toLong(),
                        path = "",
                        contentUriString = cloud.contentUriString,
                        albumArtUriString = cloud.thumbnailUrl,
                        duration = cloud.duration,
                        youtubeId = cloud.youtubeId,
                        downloadState = if (cloud.isDownloaded) DownloadState.DOWNLOADED else DownloadState.NOT_DOWNLOADED,
                        isFavorite = true
                    )
                }
                songRepository.getPaginatedFavoriteSongs(sortOption, storageFilter).map { pagingData ->
                    var resultPagingData = pagingData
                    for (cloudSong in cloudSongs.reversed()) {
                        resultPagingData = resultPagingData.insertHeaderItem(item = cloudSong)
                    }
                    resultPagingData
                }
            }.flowOn(Dispatchers.IO)
    }

    override suspend fun getFavoriteSongsOnce(storageFilter: StorageFilter): List<Song> = withContext(Dispatchers.IO) {
        val localFavorites = songRepository.getFavoriteSongsOnce(storageFilter)
        val cloudEntities = cloudSongDao.getFavoritedCloudSongsOnce()
        val cloudFavorites = cloudEntities.map { cloud ->
            Song(
                id = cloud.id,
                title = cloud.title,
                artist = cloud.artist,
                artistId = cloud.artist.hashCode().toLong(),
                album = cloud.album ?: "YouTube Music",
                albumId = (cloud.album ?: "YouTube Music").hashCode().toLong(),
                path = "",
                contentUriString = cloud.contentUriString,
                albumArtUriString = cloud.thumbnailUrl,
                duration = cloud.duration,
                youtubeId = cloud.youtubeId,
                downloadState = if (cloud.isDownloaded) com.theveloper.pixelplay.data.model.DownloadState.DOWNLOADED else com.theveloper.pixelplay.data.model.DownloadState.NOT_DOWNLOADED,
                isFavorite = true
            )
        }
        (localFavorites + cloudFavorites).distinctBy { it.id }
    }

    override suspend fun getFavoriteSongsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val allFavorites = getFavoriteSongsOnce(storageFilter)
        val sorted = when (sortOption) {
            SortOption.SongTitleAZ, SortOption.LikedSongTitleAZ -> allFavorites.sortedBy { it.title.lowercase() }
            SortOption.SongTitleZA, SortOption.LikedSongTitleZA -> allFavorites.sortedByDescending { it.title.lowercase() }
            SortOption.SongArtist, SortOption.LikedSongArtist -> allFavorites.sortedBy { it.artist.lowercase() }
            SortOption.SongArtistDesc, SortOption.LikedSongArtistDesc -> allFavorites.sortedByDescending { it.artist.lowercase() }
            SortOption.SongDateAdded, SortOption.LikedSongDateLiked -> allFavorites.sortedByDescending { it.dateAdded }
            SortOption.SongDateAddedAsc, SortOption.LikedSongDateLikedAsc -> allFavorites.sortedBy { it.dateAdded }
            else -> allFavorites
        }
        if (offset >= sorted.size) {
            emptyList()
        } else {
            sorted.subList(offset, kotlin.math.min(offset + limit, sorted.size))
        }
    }

    override fun getFavoriteSongCountFlow(storageFilter: StorageFilter): Flow<Int> {
        return combine(
            songRepository.getFavoriteSongCountFlow(storageFilter),
            cloudSongDao.getFavoritedCloudSongs().map { it.size }
        ) { localCount, cloudCount ->
            localCount + cloudCount
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun isLibraryEmpty(): Flow<Boolean> = combine(
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow
    ) { allowed, blocked -> allowed to blocked }.flatMapLatest { (allowed, blocked) ->
        flow {
            val (dirs, filtered) = computeAllowedDirs(allowed, blocked)
            emit(musicDao.hasVisibleSongs(dirs, filtered))
        }.flatMapLatest { it }
    }.map { !it }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun getSongCountFlow(): Flow<Int> {
        return musicDao.getSongCount().distinctUntilChanged()
    }

    override fun getCloudSongCountFlow(): Flow<Int> {
        return musicDao.getCloudSongCount().distinctUntilChanged()
    }

    override suspend fun getRandomSongs(limit: Int): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getRandomSongs(limit, filter.allowedParentDirs, filter.applyFilter).map { it.toSong() }
    }

    override suspend fun getSongsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getSongsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toSong() }
    }

    override suspend fun getAlbumsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter,
        minTracks: Int
    ): List<Album> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getAlbumsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            minTracks = minTracks,
            limit = limit,
            offset = offset
        ).map { it.toAlbum() }
    }

    override suspend fun getArtistsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Artist> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getArtistsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toArtist() }
    }

    override suspend fun getFirstPlayableSong(): Song? = withContext(Dispatchers.IO) {
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
        val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
        val (allowedParentDirs, applyDirectoryFilter) =
            computeAllowedDirs(allowedDirs, blockedDirs)
        musicDao.getFirstPlayableSong(
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter
        )?.toSong()
    }



    /**
     * Compute allowed parent directories by subtracting blocked dirs from all known dirs.
     * Returns Pair(allowedDirs, applyFilter) for use with Room DAO filtered queries.
     */
    private suspend fun computeAllowedDirs(
        allowedDirs: Set<String>,
        blockedDirs: Set<String>
    ): Pair<List<String>, Boolean> {
        return DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowedDirs,
            blockedDirs = blockedDirs,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
    }

    private fun StorageFilter.toFilterMode(): Int = when (this) {
        StorageFilter.ALL -> 0
        StorageFilter.OFFLINE -> 1
        StorageFilter.ONLINE -> 2
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAlbums(storageFilter: StorageFilter, minTracks: Int): Flow<List<Album>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            val (allowedParentDirs, applyFilter) = computeAllowedDirs(allowedDirs, blockedDirs)
            musicDao.getAlbums(allowedParentDirs, applyFilter, storageFilter.toFilterMode(), minTracks)
                .map { entities -> entities.map { it.toAlbum() } }
                .distinctUntilChanged()
        }.conflate().flowOn(Dispatchers.IO)
    }

    override fun getAlbumById(id: Long): Flow<Album?> {
        return musicDao.getAlbumById(id).map { it?.toAlbum() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getArtists(storageFilter: StorageFilter): Flow<List<Artist>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            val (allowedParentDirs, applyFilter) = computeAllowedDirs(allowedDirs, blockedDirs)
            musicDao.getArtistsWithSongCountsFiltered(
                allowedParentDirs = allowedParentDirs,
                applyDirectoryFilter = applyFilter,
                filterMode = storageFilter.toFilterMode()
            )
                .distinctUntilChanged()
                .map { entities ->
                    val artists = entities.map { it.toArtist() }
                    // Trigger prefetch for missing images (non-blocking)
                    val missingImages = artists.missingImageCandidates()
                    if (missingImages.isNotEmpty()) {
                        // Cancel any in-flight prefetch before starting a new one — the flow
                        // can emit multiple times during sync, and concurrent launches would
                        // create N × artist-count coroutines simultaneously.
                        prefetchJob?.cancel()
                        prefetchJob = repositoryScope.launch {
                            artistImageRepository.prefetchArtistImages(missingImages)
                        }
                    }
                    artists
                }
        }.conflate().flowOn(Dispatchers.IO)
    }

    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
        return musicDao.getSongsByAlbumId(albumId).map { entities ->
            entities.map { it.toSong() }.sortedBy { it.trackNumber }
        }.flowOn(Dispatchers.IO)
    }

    override fun getArtistById(artistId: Long): Flow<Artist?> {
        return musicDao.getArtistById(artistId).map { it?.toArtist() }
    }

    override suspend fun getArtistIdByName(name: String): Long? = withContext(Dispatchers.IO) {
        musicDao.getArtistIdByName(name)
    }

    override fun getArtistsForSong(songId: Long): Flow<List<Artist>> {
        return musicDao.getArtistsForSong(songId)
            .map { entities -> entities.map { it.toArtist() } }
            .distinctUntilChanged()
            .onEach { artists ->
                val missingImages = artists.missingImageCandidates()
                if (missingImages.isNotEmpty()) {
                    val isNewSong = currentSongArtistPrefetchSongId != songId
                    if (isNewSong) {
                        currentSongArtistPrefetchJob?.cancel()
                        currentSongArtistPrefetchSongId = songId
                    } else if (currentSongArtistPrefetchJob?.isActive == true) {
                        // Room re-emits as artist rows are updated; keep the current song batch
                        // alive so one successful image write does not cancel the remaining fetches.
                        return@onEach
                    }

                    currentSongArtistPrefetchJob = repositoryScope.launch {
                        artistImageRepository.prefetchArtistImages(missingImages)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        return musicDao.getSongsForArtist(artistId).map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        LogUtils.d(this, "getAllUniqueAudioDirectories")
        directoryScanMutex.withLock {
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null
            )?.use { c ->
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    File(c.getString(dataColumn)).parent?.let { directories.add(it) }
                }
            }
            LogUtils.i(this, "Found ${directories.size} unique audio directories")
            return@withLock directories
        }
    }

    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
        return musicDao.getAllUniqueAlbumArtUrisFromSongs().map { uriStrings ->
            uriStrings.mapNotNull { it.toUri() }
        }.flowOn(Dispatchers.IO)
    }

    // --- Métodos de Búsqueda ---

    override fun searchState(query: String): Flow<SearchState> {
        if (query.isBlank()) return flowOf(SearchState.Idle)
        return flow {
            val localList = searchSongs(query, titleOnly = false).firstOrNull() ?: emptyList()
            val localOnly = localList.filter { it.isLocalOrDownloaded }
            emit(SearchState.Loading(localResults = localOnly))

            try {
                val onlineSongs = youTubeRepository.searchSongs(query)
                emit(SearchState.Success(localResults = localOnly, onlineResults = onlineSongs))
            } catch (e: Exception) {
                emit(SearchState.PartialFailure(localResults = localOnly, message = e.message ?: "Failed to fetch online results"))
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchSongs(query: String, titleOnly: Boolean): Flow<List<Song>> {
        if (query.isBlank()) return flowOf(emptyList())
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.searchSongsLimited(
                        query = query,
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter,
                        limit = SEARCH_RESULTS_LIMIT,
                        titleOnly = titleOnly
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchAlbums(query: String, minTracks: Int): Flow<List<Album>> {
        if (query.isBlank()) return flowOf(emptyList())
        val library = searchDirectories().flatMapLatest { dirs -> musicDao.searchAlbums(query, dirs.allowedParentDirs, dirs.applyFilter, minTracks) }
        // Albums of liked songs you only stream (StreamCollectionRepository).
        val streamed = streamCollectionDao.searchAlbums(query, minTracks)
        return combine(library, streamed) { entities, streamRows ->
            entities.map { it.toAlbum() } + streamRows.map { row ->
                Album(
                    id = row.id,
                    title = row.title,
                    artist = row.artistName,
                    year = row.year,
                    dateAdded = row.dateAdded,
                    albumArtUriString = row.albumArtUriString,
                    songCount = row.songCount,
                    albumArtist = row.albumArtist
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) return flowOf(emptyList())
        val library = searchDirectories().flatMapLatest { dirs -> musicDao.searchArtists(query, dirs.allowedParentDirs, dirs.applyFilter) }
        val streamed = streamCollectionDao.searchArtists(query)
        return combine(library, streamed) { entities, streamRows ->
            entities.map { it.toArtist() } + streamRows.map { row ->
                Artist(id = row.id, name = row.name, songCount = row.trackCount, imageUrl = row.imageUrl)
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) return emptyList()
        return playlistPreferencesRepository.userPlaylistsFlow.first()
            .filter { playlist ->
                playlist.name.contains(query, ignoreCase = true)
            }
    }

    private fun searchDirectories(): Flow<CachedDirFilter> = combine(
        userPreferencesRepository.allowedDirectoriesFlow, userPreferencesRepository.blockedDirectoriesFlow
    ) { allowed, blocked ->
        val (dirs, apply) = computeAllowedDirs(allowed, blocked)
        CachedDirFilter(dirs, apply)
    }

    private fun searchIntentSongs(intent: MusicSearchQuery): Flow<List<Song>> {
        if (intent.tempo == null && !intent.lyrics && intent.terms.length < 5) return searchSongs(intent.terms)
        return searchDirectories().flatMapLatest { directories ->
            val pattern = MusicSearchQuery.likePattern(intent.terms)
            if (intent.tempo != null) {
                musicDao.searchTempoCandidates(pattern, directories.allowedParentDirs, directories.applyFilter).map { rows ->
                    val matches = rows.mapNotNull { row ->
                        val bpm = row.tempo()
                        if (bpm != null && bpm.isFinite() && bpm in intent.tempo) row.id to bpm else null
                    }.sortedBy { kotlin.math.abs(it.second - (intent.tempo.start + intent.tempo.endInclusive) / 2) }
                        .take(SEARCH_RESULTS_LIMIT)
                    val songs = musicDao.getSongsByIdsListSimple(matches.map { it.first }).associateBy { it.id }
                    matches.mapNotNull { (id, bpm) -> songs[id]?.toSong()?.let { song ->
                        song.copy(musicalFeatures = song.musicalFeatures.copy(bpm = bpm))
                    } }
                }
            } else if (intent.terms.isBlank()) flowOf(emptyList()) else {
                val lyricMatches = musicDao.searchLyricSongs(pattern, directories.allowedParentDirs,
                    directories.applyFilter, SEARCH_RESULTS_LIMIT).map { rows -> rows.map { it.toSong() } }
                if (intent.lyrics) lyricMatches else combine(searchSongs(intent.terms).onStart { emit(emptyList()) },
                    lyricMatches.onStart { emit(emptyList()); delay(150) }) { titles, lyrics ->
                    (titles + lyrics).distinctBy { it.id }.take(SEARCH_RESULTS_LIMIT)
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) return flowOf(emptyList())
        val intent = MusicSearchQuery.parse(query)
        val terms = intent.terms
        return flow {
            val minTracks = userPreferencesRepository.minTracksPerAlbumFlow.first()
            val local: Flow<List<SearchResultItem>> = when (filterType) {
                SearchFilterType.ALL -> combine(
                    searchIntentSongs(intent).onStart { emit(emptyList()) },
                    searchAlbums(terms, minTracks).onStart { emit(emptyList()) },
                    searchArtists(terms).onStart { emit(emptyList()) },
                    flow { emit(searchPlaylists(terms)) }.onStart { emit(emptyList()) }
                ) { songs, albums, artists, playlists ->
                    buildList {
                        songs.forEach { add(SearchResultItem.SongItem(it)) }
                        albums.forEach { add(SearchResultItem.AlbumItem(it)) }
                        artists.forEach { add(SearchResultItem.ArtistItem(it)) }
                        playlists.forEach { add(SearchResultItem.PlaylistItem(it)) }
                    }
                }
                SearchFilterType.SONGS -> searchIntentSongs(intent).map { songs ->
                    songs.map { SearchResultItem.SongItem(it) }
                }
                SearchFilterType.ALBUMS -> searchAlbums(terms, minTracks).map { albums ->
                    albums.map { SearchResultItem.AlbumItem(it) }
                }
                SearchFilterType.ARTISTS -> searchArtists(terms).map { artists ->
                    artists.map { SearchResultItem.ArtistItem(it) }
                }
                SearchFilterType.PLAYLISTS -> flow {
                    emit(searchPlaylists(terms).map { SearchResultItem.PlaylistItem(it) })
                }
            }
            // One online request per query, independent of Room invalidations.
            val online = flow<List<SearchResultItem>> {
                val cached = youTubeRepository.cachedItems(intent.onlineQuery, filterType)
                emit(cached)
                try {
                    // Keep local/cache first paint immediate; debounce only new network work.
                    if (cached.isEmpty()) kotlinx.coroutines.delay(150)
                    emit(youTubeRepository.searchItems(intent.onlineQuery, filterType))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("MusicRepo").w(e, "Online search failed")
                }
            }
            // Artist discovery runs independently, so a slow artist request never blocks songs.
            // Only "All" gets the extra artist discovery row. The Songs/Albums/Playlists tabs must
            // contain only their own category (artists used to leak into every tab).
            val artists = if (filterType != SearchFilterType.ALL) flowOf(emptyList()) else combine(
                (if (filterType == SearchFilterType.ALL) flowOf(emptyList()) else
                    searchArtists(terms).map { list -> list.map { SearchResultItem.ArtistItem(it) } }).onStart { emit(emptyList()) },
                flow<List<SearchResultItem>> {
                    emit(youTubeRepository.cachedItems(terms, SearchFilterType.ARTISTS))
                    if (terms.isNotBlank()) try {
                        delay(150)
                        emit(youTubeRepository.searchItems(terms, SearchFilterType.ARTISTS))
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e
                    } catch (e: Exception) { Timber.w(e, "Artist search unavailable") }
                }
            ) { localArtists, remoteArtists -> localArtists + remoteArtists }
            val lyricCandidates = flow<List<SearchResultItem>> {
                emit(emptyList())
                if (intent.tempo == null && (filterType == SearchFilterType.ALL || filterType == SearchFilterType.SONGS) &&
                    (intent.lyrics || terms.split(' ').size >= 5)) try {
                    delay(250)
                    emit(youTubeRepository.searchLyricCandidates(terms).map { SearchResultItem.SongItem(it) })
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                } catch (e: Exception) { Timber.w(e, "Lyric candidate search unavailable") }
            }
            combine(local.onStart { emit(emptyList()) }, online, artists, lyricCandidates) { localItems, onlineItems, artistItems, lyricItems ->
                (localItems + onlineItems + artistItems + lyricItems).distinctBy { it.searchIdentity() }
            }.collect { emit(it) }
        }.flowOn(Dispatchers.IO)
    }
    override suspend fun addSearchHistoryItem(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
        }
    }

    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
        return withContext(Dispatchers.IO) {
            searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
        }
    }

    override suspend fun deleteSearchHistoryItemByQuery(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    override suspend fun clearSearchHistory() {
        withContext(Dispatchers.IO) {
            searchHistoryDao.clearAll()
        }
    }

    override fun getMusicByGenre(genreId: String): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.mockGenresEnabledFlow,
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { mockEnabled, allowedDirs, blockedDirs ->
            Triple(mockEnabled, allowedDirs, blockedDirs)
        }.flatMapLatest { (mockEnabled, allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                val genreName = if (mockEnabled) "Mock" else genreId
                emit(
                    if (genreName.equals("unknown", ignoreCase = true)) {
                        musicDao.getSongsWithNullGenre(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    } else {
                        // getSongsByGenreContaining uses a LIKE query so that a song stored as
                        // "Rock, Pop" is returned when browsing either "Rock" or "Pop".
                        musicDao.getSongsByGenreContaining(
                            genreName = genreName,
                            genrePrefix = "$genreName,%",          // "Rock,..." / "Rock, ..."
                            genreSuffixWithSpace = "%, $genreName", // "..., Rock"
                            genreSuffix = "%,$genreName",          // "...,Rock"
                            genreMiddleWithSpace = "%, $genreName,%", // "..., Rock,..."
                            genreMiddle = "%,$genreName,%",        // "...,Rock,..."
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    }
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return flowOf(emptyList())
        // Separate local numeric IDs from cloud string IDs
        val localIds = songIds.mapNotNull { it.toLongOrNull() }
        val cloudIds = songIds.filter { it.toLongOrNull() == null }
        
        return favoritesDao.getFavoriteSongIds().flatMapLatest<List<String>, List<Song>> { favoriteIdsList ->
            val favIds = favoriteIdsList.toSet()
            if (localIds.isNotEmpty()) {
                musicDao.getSongsByIds(localIds, emptyList(), false).map { entities ->
                    val localSongs = entities.associate { it.id.toString() to it.toSong() }
                    // Also resolve cloud songs
                    val cloudSongs = cloudIds.mapNotNull { id ->
                        cloudSongDao.getById(id)?.let { cloud ->
                            com.theveloper.pixelplay.data.model.Song(
                                id = cloud.id,
                                title = cloud.title,
                                artist = cloud.artist,
                                artistId = cloud.artist.hashCode().toLong(),
                                album = cloud.album ?: "YouTube Music",
                                albumId = (cloud.album ?: "YouTube Music").hashCode().toLong(),
                                path = "",
                                contentUriString = cloud.contentUriString,
                                albumArtUriString = cloud.thumbnailUrl,
                                duration = cloud.duration,
                                youtubeId = cloud.youtubeId,
                                downloadState = if (cloud.isDownloaded) com.theveloper.pixelplay.data.model.DownloadState.DOWNLOADED else com.theveloper.pixelplay.data.model.DownloadState.NOT_DOWNLOADED,
                                isFavorite = favIds.contains(cloud.id)
                            ).let(metadataGatherer::applyCached) // genre, BPM, mood, … gathered earlier
                        }
                    }.associateBy { it.id }
                    
                    val allSongs = localSongs + cloudSongs
                    songIds.mapNotNull { allSongs[it] }
                }
            } else if (cloudIds.isNotEmpty()) {
                flow {
                    val cloudSongs = cloudIds.mapNotNull { id ->
                        cloudSongDao.getById(id)?.let { cloud ->
                            com.theveloper.pixelplay.data.model.Song(
                                id = cloud.id,
                                title = cloud.title,
                                artist = cloud.artist,
                                artistId = cloud.artist.hashCode().toLong(),
                                album = cloud.album ?: "YouTube Music",
                                albumId = (cloud.album ?: "YouTube Music").hashCode().toLong(),
                                path = "",
                                contentUriString = cloud.contentUriString,
                                albumArtUriString = cloud.thumbnailUrl,
                                duration = cloud.duration,
                                youtubeId = cloud.youtubeId,
                                downloadState = if (cloud.isDownloaded) com.theveloper.pixelplay.data.model.DownloadState.DOWNLOADED else com.theveloper.pixelplay.data.model.DownloadState.NOT_DOWNLOADED,
                                isFavorite = favIds.contains(cloud.id)
                            ).let(metadataGatherer::applyCached) // genre, BPM, mood, … gathered earlier
                        }
                    }
                    emit(cloudSongs)
                }
            } else {
                flowOf<List<Song>>(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getSongByPath(path: String): Song? {
        return withContext(Dispatchers.IO) {
            musicDao.getSongByPath(path)?.toSong()
        }
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    // Implementación de las nuevas funciones suspend para carga única
    override suspend fun getAllSongsOnce(): List<Song> = withContext(Dispatchers.IO) {
        // Reuse the live shared list when something is already observing the library, instead
        // of materialising another full copy (same query, same directory filter).
        sharedAudioFiles.replayCache.firstOrNull()?.let { return@withContext it }
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
        val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
        val (allowedParentDirs, applyDirectoryFilter) =
            computeAllowedDirs(allowedDirs, blockedDirs)
        musicDao.getAllSongs(
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter
        ).first().map { it.toSong() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDistinctAlbumArtSongs(): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getDistinctAlbumArtSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getHomeMixPreviewSongs(limit: Int): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getHomeMixPreviewSongs(
                        limit = limit,
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override suspend fun getAllAlbumsOnce(storageFilter: StorageFilter, minTracks: Int): List<Album> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getAlbumsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = SortOption.AlbumTitleAZ.storageKey,
            filterMode = storageFilter.toFilterMode(),
            minTracks = minTracks,
            limit = Int.MAX_VALUE,
            offset = 0
        ).map { it.toAlbum() }
    }

    override suspend fun getAllArtistsOnce(): List<Artist> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getArtistsWithSongCountsFiltered(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            filterMode = StorageFilter.ALL.toFilterMode()
        ).first().map { it.toArtist() }
    }

    override suspend fun setFavoriteStatus(songId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (isFavorite) {
            favoritesDao.setFavorite(
                com.theveloper.pixelplay.data.database.FavoritesEntity(
                    songId = songId,
                    isFavorite = true
                )
            )
            if ((songId.startsWith("yt_") || songId.startsWith("youtube://")) && cloudSongDao.getById(songId) == null) {
                val videoId = songId.removePrefix("yt_")
                cloudSongDao.upsert(
                    com.theveloper.pixelplay.data.database.CloudSongEntity(
                        id = songId,
                        title = "YouTube Track ($videoId)",
                        artist = "YouTube Artist",
                        album = "YouTube Music",
                        duration = 0L,
                        thumbnailUrl = null,
                        youtubeId = videoId,
                        sourceType = "youtube",
                        contentUriString = "youtube://$videoId"
                    )
                )
            }
        } else {
            favoritesDao.removeFavorite(songId)
        }
    }

    override suspend fun setFavoriteStatusBatch(songs: List<Song>, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val ids = songs.map { it.id }.distinct()
        if (ids.isEmpty()) return@withContext
        if (isFavorite) {
            // Online songs need a cloud_songs row so the liked list can show them.
            songs.distinctBy { it.id }.forEach { song ->
                val online = song.id.startsWith("yt_") || song.id.startsWith("spotify_") ||
                    song.contentUriString.startsWith("youtube://") || song.contentUriString.startsWith("spotify://")
                if (online && cloudSongDao.getById(song.id) == null) saveCloudSong(song)
            }
            val now = System.currentTimeMillis()
            favoritesDao.insertAll(ids.mapIndexed { index, id ->
                com.theveloper.pixelplay.data.database.FavoritesEntity(songId = id, isFavorite = true, timestamp = now + index)
            })
        } else {
            ids.chunked(500).forEach { favoritesDao.removeFavorites(it) }
        }
    }

    override suspend fun saveCloudSong(song: Song) = withContext(Dispatchers.IO) {
        if (song.youtubeId != null || song.id.startsWith("yt_") || song.contentUriString.startsWith("youtube://") || song.contentUriString.startsWith("spotify://")) {
            val videoId = song.youtubeId ?: song.id.takeIf { it.startsWith("yt_") }?.removePrefix("yt_")
            val existing = cloudSongDao.getById(song.id)
            cloudSongDao.upsert(
                com.theveloper.pixelplay.data.database.CloudSongEntity(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    thumbnailUrl = song.albumArtUriString,
                    youtubeId = videoId,
                    sourceType = if (song.id.startsWith("spotify_")) "spotify" else "youtube",
                    contentUriString = song.contentUriString.ifBlank { "youtube://$videoId" },
                    isDownloaded = existing?.isDownloaded ?: (song.downloadState == com.theveloper.pixelplay.data.model.DownloadState.DOWNLOADED),
                    localSongId = existing?.localSongId,
                    localFilePath = existing?.localFilePath,
                    localContentUri = existing?.localContentUri,
                    dateAdded = existing?.dateAdded ?: System.currentTimeMillis()
                )
            )
            // Look up genre, duration, album, BPM, mood and credits for the saved song in the background.
            metadataGatherer.gatherDeepInBackground(song)
        }
    }

    override suspend fun getFavoriteSongIdsOnce(): Set<String> = withContext(Dispatchers.IO) {
        favoritesDao.getFavoriteSongIdsOnce().toSet()
    }

    override fun getFavoriteSongIdsFlow(): Flow<Set<String>> {
        return favoritesDao.getFavoriteSongIds()
            .map { ids -> ids.toSet() }
            .distinctUntilChanged()
    }

    override fun getFavoriteSongIdsByRecentFlow(): Flow<List<String>> =
        favoritesDao.getFavoriteSongIdsByRecent()

    override suspend fun toggleFavoriteStatus(songId: String): Boolean = withContext(Dispatchers.IO) {
        val isFav = favoritesDao.isFavorite(songId) ?: false
        val newFav = !isFav
        setFavoriteStatus(songId, newFav)
        return@withContext newFav
    }

    override fun getSong(songId: String): Flow<Song?> {
        val longId = songId.toLongOrNull()
        return if (longId != null) {
            musicDao.getSongById(longId).map { it?.toSong() }.flowOn(Dispatchers.IO)
        } else {
            // Cloud song — look up from cloud_songs table
            flow {
                val cloud = cloudSongDao.getById(songId)
                val isFav = favoritesDao.isFavorite(songId) ?: false
                emit(cloud?.let {
                    Song(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        artistId = it.artist.hashCode().toLong(),
                        album = it.album ?: "YouTube Music",
                        albumId = (it.album ?: "YouTube Music").hashCode().toLong(),
                        path = "",
                        contentUriString = it.contentUriString,
                        albumArtUriString = it.thumbnailUrl,
                        duration = it.duration,
                        youtubeId = it.youtubeId,
                        downloadState = if (it.isDownloaded) com.theveloper.pixelplay.data.model.DownloadState.DOWNLOADED else com.theveloper.pixelplay.data.model.DownloadState.NOT_DOWNLOADED,
                        isFavorite = isFav
                    )
                })
            }.flowOn(Dispatchers.IO)
        }
    }

    override fun getGenres(): Flow<List<Genre>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    combine(
                        musicDao.getUniqueGenres(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        ),
                        musicDao.hasUnknownGenre(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    ) { genreNames, hasUnknown ->
                        val knownGenres = genreNames
                            .asSequence()
                            .flatMap { raw -> raw.split(",") } // split "Rock, Pop" → ["Rock", "Pop"]
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { buildGenre(it) }
                            .distinctBy { it.id }
                            .sortedBy { it.name.lowercase() }
                            .toList()
                        val unknownAlreadyPresent = knownGenres.any { it.id == UNKNOWN_GENRE_ID }
                        if (hasUnknown && !unknownAlreadyPresent) {
                            knownGenres + buildGenre(UNKNOWN_GENRE_NAME)
                        } else {
                            knownGenres
                        }
                    }
                )
            }.flatMapLatest { it }
        }.conflate().flowOn(Dispatchers.IO)
    }

    private fun buildGenre(genreName: String): Genre {
        val id = if (genreName.equals(UNKNOWN_GENRE_NAME, ignoreCase = true)) {
            UNKNOWN_GENRE_ID
        } else {
            genreName
                .lowercase()
                .replace(" ", "_")
                .replace("/", "_")
        }
        val lightThemeColor = GenreThemeUtils.getGenreThemeColor(id, isDark = false)
        val darkThemeColor = GenreThemeUtils.getGenreThemeColor(id, isDark = true)
        return Genre(
            id = id,
            name = genreName,
            lightColorHex = lightThemeColor.container.toHexString(),
            onLightColorHex = lightThemeColor.onContainer.toHexString(),
            darkColorHex = darkThemeColor.container.toHexString(),
            onDarkColorHex = darkThemeColor.onContainer.toHexString()
        )
    }

    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? {
        return lyricsRepository.getLyrics(song, sourcePreference, forceRefresh)
    }

    override suspend fun getStoredLyrics(song: Song): Pair<Lyrics, String>? {
        return lyricsRepository.getStoredLyrics(song)
    }

    override suspend fun upgradeLyricsToSynced(song: Song, ignoreBackoff: Boolean): Lyrics? {
        return lyricsRepository.upgradeToSynced(song, ignoreBackoff)
    }

    override suspend fun prefetchLyrics(song: Song): Boolean {
        return lyricsRepository.prefetchLyrics(song)
    }

    /**
     * Obtiene la letra de una canción desde la API de LRCLIB, la persiste en la base de datos
     * y la devuelve como un objeto Lyrics parseado.
     *
     * @param song La canción para la cual se buscará la letra.
     * @return Un objeto Result que contiene el objeto Lyrics si se encontró, o un error.
     */
    override suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>> {
        return lyricsRepository.fetchFromRemote(song)
    }

    override suspend fun searchRemoteLyrics(song: Song): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemote(song)
    }

    override suspend fun searchRemoteLyricsByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemoteByQuery(title, artist)
    }

    override suspend fun updateLyrics(songId: Long, lyrics: String) {
        lyricsRepository.updateLyrics(songId, lyrics)
    }

    override suspend fun resetLyrics(songId: Long) {
        lyricsRepository.resetLyrics(songId)
    }

    override suspend fun resetAllLyrics() {
        lyricsRepository.resetAllLyrics()
    }

    override fun getMusicFolders(storageFilter: StorageFilter): Flow<List<MusicFolder>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.isFolderFilterActiveFlow,
            userPreferencesRepository.foldersSourceFlow
        ) { allowedDirs, blockedDirs, isFolderFilterActive, folderSource ->
            FolderFlowConfig(
                allowedDirs = allowedDirs,
                blockedDirs = blockedDirs,
                isFolderFilterActive = isFolderFilterActive,
                folderSource = folderSource
            )
        }.flatMapLatest { config ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) = computeAllowedDirs(
                    allowedDirs = config.allowedDirs,
                    blockedDirs = config.blockedDirs
                )
                emit(
                    musicDao.getFolderSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter,
                        filterMode = storageFilter.toFilterMode()
                    ).map { folderSongs ->
                        folderTreeBuilder.buildFolderTree(
                            folderSongs = folderSongs,
                            allowedDirs = config.allowedDirs,
                            blockedDirs = config.blockedDirs,
                            isFolderFilterActive = config.isFolderFilterActive,
                            folderSource = config.folderSource,
                            context = context
                        )
                    }
                )
            }.flatMapLatest { it }
        }.conflate().flowOn(Dispatchers.IO)
    }

    private data class FolderFlowConfig(
        val allowedDirs: Set<String>,
        val blockedDirs: Set<String>,
        val isFolderFilterActive: Boolean,
        val folderSource: FolderSource
    )

    override suspend fun deleteById(id: Long) {
        musicDao.deleteById(id)
    }

    override suspend fun getSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.theveloper.pixelplay.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }

    override suspend fun getFavoriteSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.theveloper.pixelplay.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getFavoriteSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }

    override suspend fun getSongIdByContentUri(contentUri: String): Long? =
        withContext(Dispatchers.IO) {
            musicDao.getSongIdByContentUri(contentUri)
        }
}


