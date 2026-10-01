package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackVideo
import com.theveloper.pixelplay.data.network.lastfm.LastFmRepository
import com.theveloper.pixelplay.data.repository.ArtistAggregationRepository
import com.theveloper.pixelplay.data.repository.ArtistAlbumItem
import com.theveloper.pixelplay.data.repository.ArtistCatalog
import com.theveloper.pixelplay.data.repository.ArtistCrossPlatformData
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.data.repository.ArtistPlaylistItem
import com.theveloper.pixelplay.data.repository.ArtistSongSort
import com.theveloper.pixelplay.data.repository.ArtistTrack
import com.theveloper.pixelplay.data.repository.FansFilterType
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.RelatedArtistItem
import com.theveloper.pixelplay.data.repository.VideoRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.youtube.DownloadCoordinator
import com.theveloper.pixelplay.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.Locale
import javax.inject.Inject

/** Whether a catalogue song is already yours, and how. */
enum class TrackOwnership { NONE, LIKED, DOWNLOADED, LOCAL }

/** A catalogue track as the artist page shows it. */
@Immutable
data class ArtistTrackItem(
    val track: ArtistTrack,
    /** What plays on tap: your own copy when you have one, else the catalogue song. */
    val playSong: Song,
    val ownership: TrackOwnership,
    val plays: Int = 0
) {
    val key: String get() = track.song.id
}

@Immutable
data class ArtistListeningUi(
    val totalDurationMs: Long,
    val plays: Int,
    val firstPlayedAt: Long?,
    val streakDays: Int,
    val shareOfListening: Float,
    val topSong: Song?,
    val topSongPlays: Int
)

@Immutable
data class DiscographyCompletion(
    val owned: Int,
    val total: Int,
    val downloaded: Int,
    /** Liked online songs that aren't downloaded yet. */
    val likedNotDownloaded: List<Song>,
    /** Songs you don't have at all (for Like all). */
    val notOwned: List<Song>,
    /** Your local and downloaded copies (for Shuffle downloaded). */
    val offlineSongs: List<Song>
) {
    val fraction: Float get() = if (total == 0) 0f else owned.toFloat() / total
}

/**
 * Holds the full UI state for ArtistDetailScreen.
 */
data class ArtistDetailUiState(
    val artist: Artist? = null,
    /** Your own songs by the artist (library files, downloads and liked streams). */
    val songs: List<Song> = emptyList(),
    val albumSections: List<ArtistAlbumSection> = emptyList(),
    val effectiveImageUrl: String? = null,

    // Catalogue
    val tracks: List<ArtistTrackItem> = emptyList(),
    /** [tracks] with the current sort and search applied. */
    val visibleTracks: List<ArtistTrackItem> = emptyList(),
    val songSort: ArtistSongSort = ArtistSongSort.POPULAR,
    val songQuery: String = "",
    val appearsOn: List<ArtistTrackItem> = emptyList(),
    val popularReleases: List<ArtistAlbumItem> = emptyList(),
    val singlesAndEPs: List<ArtistAlbumItem> = emptyList(),
    val latestRelease: ArtistAlbumItem? = null,
    val fansAlsoLikeArtists: List<RelatedArtistItem> = emptyList(),
    val fansAlsoLikeSongs: List<Song> = emptyList(),
    val fansAlsoLikePlaylists: List<ArtistPlaylistItem> = emptyList(),
    val activeFansFilter: FansFilterType = FansFilterType.ARTISTS,
    val fanCount: Long? = null,
    val genres: List<String> = emptyList(),
    val deezerArtistId: Long? = null,
    val deezerCandidates: List<RelatedArtistItem> = emptyList(),

    // Personal + extras
    val listening: ArtistListeningUi? = null,
    val completion: DiscographyCompletion? = null,
    val videos: List<TrackVideo> = emptyList(),
    val bio: String? = null,

    val isLoading: Boolean = false,
    /** Online catalogue still loading (search already works on your own songs). */
    val isAggregating: Boolean = false,
    /** The catalogue couldn't be reached; showing saved data and/or your songs. */
    val isOffline: Boolean = false,
    /** True when the offline view includes a saved catalogue. */
    val hasSavedCatalogue: Boolean = false,
    /** When the catalogue shown was fetched. */
    val catalogueUpdatedAt: Long? = null,
    val error: String? = null
)

@Immutable
data class ArtistAlbumSection(
    val albumId: Long,
    val title: String,
    val year: Int?,
    val albumArtUriString: String?,
    val songs: List<Song>
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val artistImageRepository: ArtistImageRepository,
    private val artistAggregationRepository: ArtistAggregationRepository,
    val themeStateHolder: ThemeStateHolder,
    private val streamCollection: com.theveloper.pixelplay.data.library.StreamCollectionRepository,
    private val streamCollectionDao: com.theveloper.pixelplay.data.database.StreamCollectionDao,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val downloadCoordinator: DownloadCoordinator,
    private val videoRepository: VideoRepository,
    private val lastFmRepository: LastFmRepository,
    private val connectedLibrary: com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private val _artistColorScheme = MutableStateFlow<ColorSchemePair?>(null)
    val artistColorScheme: StateFlow<ColorSchemePair?> = _artistColorScheme.asStateFlow()

    // Inputs of the song list.
    private val localSongsFlow = MutableStateFlow<List<Song>>(emptyList())
    private val profileFlow = MutableStateFlow<ArtistCrossPlatformData?>(null)
    private val playsFlow = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val tracksFlow = MutableStateFlow<List<ArtistTrackItem>>(emptyList())
    private val queryFlow = MutableStateFlow("")
    private val sortFlow = MutableStateFlow(ArtistSongSort.POPULAR)

    private var currentLoadJob: Job? = null
    private var aggregationJob: Job? = null
    private var extrasJob: Job? = null
    private var onlineLoadedFor: String? = null

    init {
        savedStateHandle.getStateFlow<String?>("artistId", null)
            .onEach { idString ->
                if (idString != null) {
                    val decoded = try {
                        URLDecoder.decode(idString, "UTF-8")
                    } catch (e: Exception) {
                        idString
                    }

                    val artistId = decoded.toLongOrNull()
                    if (decoded.startsWith(Screen.ArtistDetail.NAME_PREFIX)) {
                        // Online artist (no local library entry): build the profile from its name.
                        loadArtistByName(decoded.removePrefix(Screen.ArtistDetail.NAME_PREFIX))
                    } else if (artistId != null &&
                        com.theveloper.pixelplay.data.library.CollectionKeys.isStreamArtistId(artistId)
                    ) {
                        // Artist that only has liked songs you stream: open the profile by name.
                        viewModelScope.launch {
                            val name = streamCollection.artistName(artistId)
                                ?: streamCollectionDao.getArtistById(artistId)?.name
                            if (name != null) {
                                loadArtistByName(name)
                            } else {
                                _uiState.update { it.copy(error = context.getString(R.string.artist_detail_id_not_found), isLoading = false) }
                            }
                        }
                    } else if (artistId != null) {
                        loadArtistData(artistId)
                    } else {
                        loadArtistByName(decoded)
                    }
                } else {
                    _uiState.update { it.copy(error = context.getString(R.string.artist_detail_id_not_found), isLoading = false) }
                }
            }
            .launchIn(viewModelScope)

        // Catalogue + your songs + likes + downloads + plays → the annotated track list.
        combine(
            profileFlow,
            localSongsFlow,
            musicRepository.getFavoriteSongIdsFlow(),
            downloadCoordinator.downloadedIds,
            playsFlow
        ) { profile, local, favorites, downloaded, plays ->
            buildItems(profile, local, favorites, downloaded, plays)
        }
            .flowOn(Dispatchers.Default)
            .catch { e -> Log.e(TAG, "Building artist tracks failed", e) }
            .onEach { built ->
                tracksFlow.value = built.tracks
                _uiState.update {
                    it.copy(tracks = built.tracks, appearsOn = built.appearsOn, completion = built.completion)
                }
            }
            .launchIn(viewModelScope)

        // Sort + search, off the main thread; typing is debounced (C4).
        combine(
            tracksFlow,
            queryFlow.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
            sortFlow
        ) { tracks, query, sort ->
            val playsByKey = HashMap<String, Int>()
            tracks.forEach { item ->
                if (item.plays > 0) playsByKey[ArtistCatalog.baseKey(item.track.song.title)] = item.plays
            }
            val sorted = ArtistCatalog.sort(tracks.map { it.track }, sort, playsByKey)
            val found = ArtistCatalog.search(sorted, query)
            val byId = tracks.associateBy { it.track.song.id }
            found.mapNotNull { byId[it.song.id] }
        }
            .flowOn(Dispatchers.Default)
            .onEach { visible -> _uiState.update { it.copy(visibleTracks = visible) } }
            .launchIn(viewModelScope)
    }

    // ── Loading ─────────────────────────────────────────────────────────────────────────────

    private fun loadArtistData(id: Long) {
        aggregationJob?.cancel()
        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                combine(musicRepository.getArtistById(id), musicRepository.getSongsForArtist(id)) { artist, songs ->
                    artist to songs
                }
                    .catch { e ->
                        _uiState.update {
                            it.copy(
                                error = context.getString(R.string.artist_error_loading_artist, e.localizedMessage ?: ""),
                                isLoading = false
                            )
                        }
                    }
                    .collect { (artist, librarySongs) ->
                        if (artist == null) {
                            // Not a library artist: streamed songs (Spotify, YouTube Music, friends'
                            // playlists) carry a name-hash id. Find the name among songs we know
                            // and open the real artist, never a made-up "Artist <id>".
                            val name = artistNameForUnknownId(id)
                            if (name != null) {
                                loadArtistByName(name)
                            } else {
                                _uiState.update { it.copy(error = context.getString(R.string.artist_detail_id_not_found), isLoading = false) }
                            }
                            return@collect
                        }
                        // Liked songs by this artist that you only stream.
                        val streamedSongs = streamCollection.songsByArtistId.value[artist.id].orEmpty()
                        val libraryIds = librarySongs.mapTo(HashSet()) { it.id }
                        val localSongs = librarySongs + streamedSongs.filterNot { it.id in libraryIds }

                        val effectiveUrl = try {
                            artistImageRepository.getEffectiveArtistImageUrl(artistId = artist.id, artistName = artist.name)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            artist.effectiveImageUrl
                        }
                        _artistColorScheme.value = colorSchemeFor(effectiveUrl)
                        val sections = buildAlbumSections(localSongs)
                        _uiState.update {
                            it.copy(
                                artist = artist.copy(
                                    imageUrl = if (artist.customImageUri.isNullOrBlank()) effectiveUrl else artist.imageUrl
                                ),
                                songs = sections.flatMap { s -> s.songs },
                                albumSections = sections,
                                effectiveImageUrl = effectiveUrl,
                                isLoading = false
                            )
                        }
                        setLocalSongs(localSongs)
                        // Library changes re-emit here; the catalogue only loads once per artist.
                        if (onlineLoadedFor != artist.name) {
                            onlineLoadedFor = artist.name
                            loadOnlineCrossPlatformData(artist.name)
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.artist_error_loading_artist, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    /** Name of an artist id that isn't in the artists table, from connected / friends' playlists. */
    private suspend fun artistNameForUnknownId(id: Long): String? = kotlinx.coroutines.withContext(Dispatchers.Default) {
        streamCollection.artistName(id)?.let { return@withContext it }
        for (playlist in connectedLibrary.snapshot.value.playlists) {
            for (song in playlist.songs) {
                song.artists.firstOrNull { it.id == id && it.name.isNotBlank() }?.let { return@withContext it.name }
                if (song.artistId == id && song.artist.isNotBlank()) return@withContext song.artist
            }
        }
        null
    }

    private fun loadArtistByName(artistName: String) {
        aggregationJob?.cancel()
        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val syntheticArtist = Artist(
                id = artistName.hashCode().toLong(),
                name = artistName,
                songCount = 0,
                imageUrl = null,
                customImageUri = null
            )
            val effectiveUrl = try {
                artistImageRepository.getArtistImageUrl(artistName, syntheticArtist.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }
            _artistColorScheme.value = colorSchemeFor(effectiveUrl)
            _uiState.update {
                it.copy(
                    artist = syntheticArtist.copy(imageUrl = effectiveUrl),
                    effectiveImageUrl = effectiveUrl,
                    isLoading = false
                )
            }

            // Any local songs for this artist name, plus liked songs you only stream.
            val librarySongs = try {
                musicRepository.searchSongs(artistName, titleOnly = false).firstOrNull()
                    ?.filter { it.artist.contains(artistName, ignoreCase = true) } ?: emptyList()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                emptyList()
            }
            val streamedSongs = streamCollection.songsByArtistId.value[
                com.theveloper.pixelplay.data.library.CollectionKeys.streamArtistId(artistName)
            ].orEmpty()
            val libraryIds = librarySongs.mapTo(HashSet()) { it.id }
            val localSongs = librarySongs + streamedSongs.filterNot { it.id in libraryIds }
            val sections = buildAlbumSections(localSongs)
            _uiState.update { it.copy(songs = sections.flatMap { s -> s.songs }, albumSections = sections) }
            setLocalSongs(localSongs)

            onlineLoadedFor = artistName
            loadOnlineCrossPlatformData(artistName)
        }
    }

    private fun setLocalSongs(songs: List<Song>) {
        localSongsFlow.value = songs
        refreshListening()
    }

    /**
     * Saved catalogue first (instant, also offline), then a background refresh when it's older
     * than a day or [force]d.
     */
    private fun loadOnlineCrossPlatformData(artistName: String, force: Boolean = false) {
        aggregationJob?.cancel()
        aggregationJob = viewModelScope.launch {
            _uiState.update { it.copy(isAggregating = true) }
            val cached = try {
                artistAggregationRepository.getCachedProfile(artistName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }
            if (cached != null) {
                applyProfile(cached.data, cached.savedAt)
                if (!cached.isStale && !force) {
                    _uiState.update { it.copy(isAggregating = false, isOffline = false) }
                    loadExtras(artistName)
                    return@launch
                }
            }
            try {
                val profile = artistAggregationRepository.getArtistProfileData(
                    artistName = artistName,
                    forceRefresh = true,
                    localTitles = localSongsFlow.value.mapTo(HashSet()) { it.title }
                )
                applyProfile(profile, profile.fetchedAt)
                _uiState.update { it.copy(isAggregating = false, isOffline = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w(TAG, "Artist catalogue unavailable: ${e.message}")
                _uiState.update {
                    it.copy(isAggregating = false, isOffline = true, hasSavedCatalogue = cached != null)
                }
            }
            loadExtras(artistName)
        }
    }

    private suspend fun applyProfile(profile: ArtistCrossPlatformData, fetchedAt: Long) {
        val effectiveImage = _uiState.value.effectiveImageUrl
            ?: profile.effectiveImageUrl
            ?: profile.popularReleases.firstOrNull()?.coverArtUrl
        if (_artistColorScheme.value == null && !effectiveImage.isNullOrBlank()) {
            _artistColorScheme.value = colorSchemeFor(effectiveImage)
        }
        val latest = (profile.popularReleases + profile.singlesAndEPs)
            .filter { it.releaseDate != null }
            .maxByOrNull { it.releaseDate!! }
        profileFlow.value = profile
        _uiState.update { current ->
            current.copy(
                effectiveImageUrl = effectiveImage,
                popularReleases = profile.popularReleases,
                singlesAndEPs = profile.singlesAndEPs,
                latestRelease = latest,
                fansAlsoLikeArtists = profile.fansAlsoLikeArtists,
                fansAlsoLikeSongs = profile.fansAlsoLikeSongs,
                fansAlsoLikePlaylists = profile.fansAlsoLikePlaylists,
                fanCount = profile.fanCount,
                genres = mergeGenres(profile.genres, current.genres),
                deezerArtistId = profile.deezerArtistId,
                deezerCandidates = profile.deezerCandidates,
                catalogueUpdatedAt = fetchedAt
            )
        }
        refreshListening()
    }

    /** Videos and the Last.fm bio load after the catalogue and never block it. */
    private fun loadExtras(artistName: String) {
        extrasJob?.cancel()
        extrasJob = viewModelScope.launch {
            launch {
                val videos = try {
                    videoRepository.searchArtistVideos(artistName)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    emptyList()
                }
                _uiState.update { it.copy(videos = videos.take(MAX_VIDEOS)) }
            }
            launch {
                val info = try {
                    lastFmRepository.getArtistInfo(artistName)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    null
                }
                if (info != null) {
                    _uiState.update {
                        it.copy(
                            bio = info.bio?.substringBefore(" Read more on Last.fm")?.takeIf { b -> b.length > 40 },
                            genres = mergeGenres(it.genres, info.tags.map { tag -> tag.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } })
                        )
                    }
                }
            }
        }
    }

    private fun mergeGenres(first: List<String>, second: List<String>): List<String> {
        val localGenres = localSongsFlow.value.mapNotNull { it.genre?.trim() }
            .filter { it.isNotBlank() && !it.equals("unknown", true) && !it.startsWith("<") }
        return (first + localGenres + second)
            .filterNot { it.equals("seen live", true) || it.equals(_uiState.value.artist?.name, true) }
            .distinctBy { ArtistCatalog.fold(it) }
            .take(MAX_GENRE_CHIPS)
    }

    /** Your plays of every id the artist's songs are known by. */
    private fun refreshListening() {
        viewModelScope.launch {
            val profile = profileFlow.value
            val local = localSongsFlow.value
            val songsById = HashMap<String, Song>()
            local.forEach { songsById[it.id] = it }
            profile?.tracks?.forEach { t ->
                songsById.putIfAbsent(t.song.id, t.song)
                t.versions.forEach { v -> songsById.putIfAbsent(v.id, v) }
            }
            if (songsById.isEmpty()) return@launch
            val listening = try {
                playbackStatsRepository.loadArtistListening(songsById.keys)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w(TAG, "Listening stats failed: ${e.message}")
                return@launch
            }
            // Plays per song, merged across the ids a song is known by (file, stream, catalogue).
            val playsByKey = HashMap<String, Int>()
            listening.playsBySongId.forEach { (id, count) ->
                val song = songsById[id] ?: return@forEach
                val key = ArtistCatalog.baseKey(song.title)
                playsByKey[key] = (playsByKey[key] ?: 0) + count
            }
            playsFlow.value = playsByKey
            val topKey = playsByKey.maxByOrNull { it.value }
            val topSong = topKey?.let { (key, _) ->
                local.firstOrNull { ArtistCatalog.baseKey(it.title) == key }
                    ?: songsById.values.firstOrNull { ArtistCatalog.baseKey(it.title) == key }
            }
            _uiState.update {
                it.copy(
                    listening = if (listening.totalDurationMs <= 0L) null else ArtistListeningUi(
                        totalDurationMs = listening.totalDurationMs,
                        plays = listening.totalPlays,
                        firstPlayedAt = listening.firstPlayedAt,
                        streakDays = listening.currentStreakDays,
                        shareOfListening = listening.shareOfListening,
                        topSong = topSong,
                        topSongPlays = topKey?.value ?: 0
                    )
                )
            }
        }
    }

    // ── Track list building ───────────────────────────────────────────────────────────────

    private data class BuiltItems(
        val tracks: List<ArtistTrackItem>,
        val appearsOn: List<ArtistTrackItem>,
        val completion: DiscographyCompletion?
    )

    private fun buildItems(
        profile: ArtistCrossPlatformData?,
        local: List<Song>,
        favorites: Set<String>,
        downloaded: Set<String>,
        playsByKey: Map<String, Int>
    ): BuiltItems {
        val localByKey = HashMap<String, Song>()
        // Prefer a real file, then a download, then a liked stream.
        local.sortedBy { song ->
            when {
                song.isLocal -> 0
                song.isDownloaded || song.id in downloaded -> 1
                else -> 2
            }
        }.forEach { localByKey.putIfAbsent(ArtistCatalog.baseKey(it.title), it) }

        fun ownershipOf(catalogSong: Song, mine: Song?): TrackOwnership = when {
            mine != null && mine.isLocal -> TrackOwnership.LOCAL
            mine != null && (mine.isDownloaded || mine.id in downloaded) -> TrackOwnership.DOWNLOADED
            catalogSong.id in downloaded -> TrackOwnership.DOWNLOADED
            mine != null -> TrackOwnership.LIKED
            catalogSong.id in favorites -> TrackOwnership.LIKED
            else -> TrackOwnership.NONE
        }

        fun item(track: ArtistTrack): ArtistTrackItem {
            val key = ArtistCatalog.baseKey(track.song.title)
            val mine = localByKey[key]
            return ArtistTrackItem(
                track = track,
                playSong = mine ?: track.song,
                ownership = ownershipOf(track.song, mine),
                plays = playsByKey[key] ?: 0
            )
        }

        val catalog = profile?.tracks.orEmpty()
        val catalogKeys = catalog.mapTo(HashSet()) { ArtistCatalog.baseKey(it.song.title) }
        // Your songs the catalogue doesn't list (demos, bootlegs, other tagging) stay searchable.
        val localOnly = local
            .filter { ArtistCatalog.baseKey(it.title) !in catalogKeys }
            .distinctBy { ArtistCatalog.baseKey(it.title) }
            .map { ArtistTrack(song = it, releaseDate = it.year.takeIf { y -> y > 0 }?.toString()) }
        val tracks = (catalog + localOnly).distinctBy { it.song.id }.map(::item)
        val appearsOn = profile?.appearsOn.orEmpty().map(::item)

        val completion = if (profile == null || catalog.isEmpty()) null else {
            val owned = tracks.count { it.ownership != TrackOwnership.NONE }
            DiscographyCompletion(
                owned = owned,
                total = tracks.size,
                downloaded = tracks.count { it.ownership == TrackOwnership.LOCAL || it.ownership == TrackOwnership.DOWNLOADED },
                likedNotDownloaded = tracks.filter { it.ownership == TrackOwnership.LIKED && downloadCoordinator.isOnlineSong(it.playSong) }
                    .map { it.playSong },
                notOwned = tracks.filter { it.ownership == TrackOwnership.NONE }.map { it.track.song },
                offlineSongs = tracks.filter { it.ownership == TrackOwnership.LOCAL || it.ownership == TrackOwnership.DOWNLOADED }
                    .map { it.playSong }
            )
        }
        return BuiltItems(tracks, appearsOn, completion)
    }

    // ── Actions ───────────────────────────────────────────────────────────────────────────

    fun setSongQuery(query: String) {
        queryFlow.value = query
        _uiState.update { it.copy(songQuery = query) }
    }

    fun setSongSort(sort: ArtistSongSort) {
        sortFlow.value = sort
        _uiState.update { it.copy(songSort = sort) }
    }

    fun setFansFilter(filter: FansFilterType) {
        _uiState.update { it.copy(activeFansFilter = filter) }
    }

    /** Re-fetches the catalogue now, ignoring the saved copy. */
    fun refreshCatalogue() {
        val name = _uiState.value.artist?.name ?: return
        loadOnlineCrossPlatformData(name, force = true)
    }

    /** "Not this artist?": use another Deezer artist with this name, then refetch. */
    fun chooseDeezerArtist(candidate: RelatedArtistItem) {
        val name = _uiState.value.artist?.name ?: return
        artistAggregationRepository.setDeezerOverride(name, candidate.id.toLongOrNull())
        loadOnlineCrossPlatformData(name, force = true)
    }

    /** Downloads every liked song by the artist that isn't on the device yet. */
    fun downloadLikedSongs(): Int {
        val songs = _uiState.value.completion?.likedNotDownloaded.orEmpty()
        songs.forEach(downloadCoordinator::download)
        return songs.size
    }

    /** Seeds for the artist mix: their biggest songs, your favourites of theirs, and fans-also-like. */
    fun artistMixSeeds(): List<Song> {
        val state = _uiState.value
        val top = state.tracks.sortedByDescending { it.track.popularity ?: 0 }.take(14).map { it.playSong }
        val yours = state.tracks.filter { it.plays > 0 }.sortedByDescending { it.plays }.take(8).map { it.playSong }
        val similar = state.fansAlsoLikeSongs.shuffled().take(10)
        val seeds = (top.take(1) + (top.drop(1) + yours + similar).shuffled()).distinctBy { it.id }
        // Catalogue songs are matched on YouTube before playback starts: keep the first batch short.
        return seeds.take(MAX_MIX_SEEDS)
    }

    /** A release's tracklist (for Latest release → Play), your own copies swapped in. */
    suspend fun releaseSongs(release: ArtistAlbumItem): List<Song> {
        val songs = try {
            artistAggregationRepository.getOnlineAlbumWithSongs(release.collectionId, release.title, release.artist).second
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            emptyList()
        }
        val mine = localSongsFlow.value.associateBy { ArtistCatalog.baseKey(it.title) }
        return songs.map { mine[ArtistCatalog.baseKey(it.title)] ?: it }
    }

    private suspend fun colorSchemeFor(url: String?): ColorSchemePair? {
        if (url.isNullOrBlank()) return null
        return try {
            themeStateHolder.getOrGenerateColorScheme(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            null
        }
    }

    fun setCustomImage(sourceUri: Uri) {
        val artistId = _uiState.value.artist?.id ?: return
        viewModelScope.launch {
            try {
                val internalPath = artistImageRepository.setCustomArtistImage(context, artistId, sourceUri)
                if (!internalPath.isNullOrBlank()) {
                    val oldEffectiveUrl = _uiState.value.effectiveImageUrl

                    if (!oldEffectiveUrl.isNullOrBlank() && oldEffectiveUrl != internalPath) {
                        themeStateHolder.forceRegenerateColorScheme(oldEffectiveUrl)
                    }
                    val newScheme = try {
                        themeStateHolder.forceRegenerateColorScheme(internalPath)
                        themeStateHolder.getOrGenerateColorScheme(internalPath)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        null
                    }

                    _artistColorScheme.value = newScheme
                    _uiState.update { state ->
                        val effectiveUrlWithBust = "$internalPath?t=${System.currentTimeMillis()}"
                        state.copy(
                            effectiveImageUrl = effectiveUrlWithBust,
                            artist = state.artist?.copy(customImageUri = internalPath)
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set custom image: ${e.message}")
            }
        }
    }

    fun clearCustomImage() {
        val artist = _uiState.value.artist ?: return
        viewModelScope.launch {
            try {
                val oldEffectiveUrl = _uiState.value.effectiveImageUrl
                artistImageRepository.clearCustomArtistImage(context, artist.id)

                val deezerUrl = artistImageRepository.getArtistImageUrl(artist.name, artist.id)
                val newEffectiveUrl = deezerUrl.takeIf { !it.isNullOrBlank() }

                if (!oldEffectiveUrl.isNullOrBlank()) {
                    themeStateHolder.forceRegenerateColorScheme(oldEffectiveUrl)
                }
                _artistColorScheme.value = colorSchemeFor(newEffectiveUrl)
                _uiState.update { state ->
                    state.copy(
                        effectiveImageUrl = newEffectiveUrl,
                        artist = state.artist?.copy(customImageUri = null, imageUrl = deezerUrl)
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear custom image: ${e.message}")
            }
        }
    }

    fun removeSongFromAlbumSection(songId: String) {
        _uiState.update { currentState ->
            val updatedAlbumSections = currentState.albumSections.map { section ->
                section.copy(songs = section.songs.filterNot { it.id == songId })
            }.filter { it.songs.isNotEmpty() }

            currentState.copy(
                albumSections = updatedAlbumSections,
                songs = currentState.songs.filterNot { it.id == songId }
            )
        }
        localSongsFlow.update { songs -> songs.filterNot { it.id == songId } }
    }

    private companion object {
        const val TAG = "ArtistDebug"
        const val SEARCH_DEBOUNCE_MS = 150L
        const val MAX_VIDEOS = 12
        const val MAX_GENRE_CHIPS = 8
        const val MAX_MIX_SEEDS = 20
    }
}

private val songDisplayComparator = compareBy<Song> { it.discNumber ?: 1 }
    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
    .thenBy { it.title.lowercase() }

private fun buildAlbumSections(songs: List<Song>): List<ArtistAlbumSection> {
    if (songs.isEmpty()) return emptyList()

    val sections = songs
        .groupBy { it.albumId to it.album }
        .map { (key, albumSongs) ->
            val sortedSongs = albumSongs.sortedWith(songDisplayComparator)
            val albumYear = albumSongs.mapNotNull { song -> song.year.takeIf { it > 0 } }.maxOrNull()
            val albumArtUri = albumSongs.firstNotNullOfOrNull { it.albumArtUriString }
            ArtistAlbumSection(
                albumId = key.first,
                title = (key.second.takeIf { it.isNotBlank() } ?: "Unknown Album"),
                year = albumYear,
                albumArtUriString = albumArtUri,
                songs = sortedSongs
            )
        }

    val (withYear, withoutYear) = sections.partition { it.year != null }
    val withYearSorted = withYear.sortedWith(
        compareByDescending<ArtistAlbumSection> { it.year ?: Int.MIN_VALUE }
            .thenBy { it.title.lowercase() }
    )
    val withoutYearSorted = withoutYear.sortedBy { it.title.lowercase() }

    return withYearSorted + withoutYearSorted
}
