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
import kotlinx.coroutines.withContext
import com.theveloper.pixelplay.data.spotify.toSong
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.theveloper.pixelplay.data.repository.ArtistProfileSection
import com.theveloper.pixelplay.data.repository.CrossPlatformArtistMetrics
import com.theveloper.pixelplay.data.tour.ArtistTour
import com.theveloper.pixelplay.data.tour.TourRepository
import java.net.URLDecoder
import java.util.Locale
import javax.inject.Inject

/**
 * The artist page's sections. Each loads on its own; one is "loaded" once its data has arrived,
 * even when it turned out empty. Until then the page shows that section's skeleton.
 */
enum class ArtistSection { TOUR, SONGS, RELEASES, APPEARS_ON, VIDEOS, FANS, GENRES, ABOUT, SPOTIFY, SIMILAR, LIVE, YT_PLAYLISTS, HUB }

/** One of the artist's playlists on YouTube Music (opened through its browse id). */
@Immutable
data class ArtistYtPlaylist(val browseId: String, val title: String, val coverUrl: String?)

/** The on-device "Ask about the artist" box: idle, thinking, or the last answer. */
@Immutable
data class ArtistAiUiState(
    /** Gemini Nano is on this phone and ready: the box only shows then. */
    val available: Boolean = false,
    val thinking: Boolean = false,
    val question: String? = null,
    val answer: String? = null,
)

/** All songs filters (one at a time). */
enum class ArtistSongFilter { LIKED, ON_DEVICE, NOT_YOURS }

/** Whether a catalogue song is already yours, and how. */
enum class TrackOwnership { NONE, LIKED, DOWNLOADED, LOCAL }

/** A catalogue track as the artist page shows it. */
@Immutable
data class ArtistTrackItem(
    val track: ArtistTrack,
    /** What plays on tap: your own copy when you have one, else the catalogue song. */
    val playSong: Song,
    val ownership: TrackOwnership,
    val plays: Int = 0,
    /** Liked, whatever else you have of it (ownership shows only the strongest: local > download > like). */
    val isLiked: Boolean = false,
    /** [RecordingKeys.of] of the catalogue song, worked out once (now-playing match on the page). */
    val recordingKey: String = ""
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
    val offlineSongs: List<Song>,
    /** Songs you've liked (any copy). */
    val liked: Int = 0
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
    /** The sort's natural order flipped (direction button). */
    val songSortReversed: Boolean = false,
    /** One ownership filter at a time; null = all songs. */
    val songFilter: ArtistSongFilter? = null,
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
    /** Live performances (YouTube), shown right under Music videos. */
    val liveVideos: List<TrackVideo> = emptyList(),
    /** The artist's playlists on YouTube Music, merged with [spotifyPlaylists] on the page. */
    val ytPlaylists: List<ArtistYtPlaylist> = emptyList(),
    /** Official website, streaming / social / community links, band members (MusicBrainz). */
    val hub: com.theveloper.pixelplay.data.artist.ArtistHubInfo = com.theveloper.pixelplay.data.artist.ArtistHubInfo(),
    /** AUD per unit by currency code, for merch prices (live, daily; offline fallback). */
    val audRates: Map<String, Double> = com.theveloper.pixelplay.data.artist.MerchPriceConverter.FALLBACK_RATES,
    val ai: ArtistAiUiState = ArtistAiUiState(),
    val bio: String? = null,
    /** Where [bio] came from ("Last.fm" / "Wikipedia"); null when there's no bio. */
    val bioSource: String? = null,
    /** Tour dates (Ticketmaster + Spotify concerts, merged); null until loaded or when there's no source. */
    val tour: ArtistTour? = null,
    /** The artist's playlists on Spotify (own, "This Is", featuring…); empty without a Spotify sign-in. */
    val spotifyPlaylists: List<com.theveloper.pixelplay.data.spotify.web.SpotifyArtistPlaylist> = emptyList(),
    /** Merch from the artist's Spotify page (the Merch tab); empty without a Spotify sign-in. */
    val merch: List<com.theveloper.pixelplay.data.spotify.web.SpotifyMerchItem> = emptyList(),
    /** "Similar": Spotify related artists (or Last.fm's), resolved on Deezer, plus a few of their songs. */
    val similarArtists: List<RelatedArtistItem> = emptyList(),
    val similarSongs: List<Song> = emptyList(),
    /** Similar's chip (Artists / Songs). */
    val activeSimilarFilter: FansFilterType = FansFilterType.ARTISTS,

    /** Sections whose data has arrived (see [ArtistSection]). */
    val loadedSections: Set<ArtistSection> = emptySet(),

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
    private val tourRepository: TourRepository,
    private val spotifyArtistRepository: com.theveloper.pixelplay.data.spotify.web.SpotifyArtistRepository,
    private val spotifyRepository: com.theveloper.pixelplay.data.spotify.SpotifyRepository,
    /** The radio map's base map (OpenFreeMap tiles), reused for the tour map. */
    val mapTiles: com.theveloper.pixelplay.presentation.screens.radio.RadioMapTiles,
    private val connectedLibrary: com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository,
    private val artistHubRepository: com.theveloper.pixelplay.data.artist.ArtistHubRepository,
    private val exchangeRates: com.theveloper.pixelplay.data.artist.ExchangeRateRepository,
    private val nanoEngine: com.theveloper.pixelplay.data.ai.nano.NanoEngine,
    private val gemmaEngine: com.theveloper.pixelplay.data.ai.gemma.GemmaLiteRtEngine,
    private val gemmaModels: com.theveloper.pixelplay.data.ai.gemma.GemmaModelManager,
    private val youTubeMusicApi: com.theveloper.pixelplay.data.youtube.YouTubeMusicApiService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private val _artistColorScheme = MutableStateFlow<ColorSchemePair?>(null)
    val artistColorScheme: StateFlow<ColorSchemePair?> = _artistColorScheme.asStateFlow()

    // Inputs of the song list.
    private val localSongsFlow = MutableStateFlow<List<Song>>(emptyList())
    private val profileFlow = MutableStateFlow<ArtistCrossPlatformData?>(null)
    /** True once the catalogue has arrived (or failed), so All songs and Appears on can resolve. */
    private val catalogueReady = MutableStateFlow(false)
    private val partialMutex = Mutex()
    private val playsFlow = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val tracksFlow = MutableStateFlow<List<ArtistTrackItem>>(emptyList())
    private val queryFlow = MutableStateFlow("")
    private val sortFlow = MutableStateFlow(ArtistSongSort.POPULAR)
    private val directionFlow = MutableStateFlow(false)
    private val filterFlow = MutableStateFlow<ArtistSongFilter?>(null)

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
            combine(profileFlow, catalogueReady) { profile, ready -> profile to ready },
            localSongsFlow,
            musicRepository.getFavoriteSongIdsFlow(),
            downloadCoordinator.downloadedIds,
            playsFlow
        ) { (profile, ready), local, favorites, downloaded, plays ->
            buildItems(profile, local, favorites, downloaded, plays) to ready
        }
            .flowOn(Dispatchers.Default)
            .catch { e -> Log.e(TAG, "Building artist tracks failed", e) }
            .onEach { (built, ready) ->
                tracksFlow.value = built.tracks
                _uiState.update {
                    it.copy(
                        tracks = built.tracks,
                        appearsOn = built.appearsOn,
                        completion = built.completion,
                        // Marked here, with the built list, so the section never flashes empty
                        // between "catalogue arrived" and "tracks built".
                        loadedSections = if (ready) {
                            it.loadedSections + ArtistSection.SONGS + ArtistSection.APPEARS_ON
                        } else {
                            it.loadedSections
                        }
                    )
                }
            }
            .launchIn(viewModelScope)

        // Sort + search, off the main thread; typing is debounced (C4).
        combine(
            tracksFlow,
            queryFlow.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
            sortFlow,
            directionFlow,
            filterFlow
        ) { tracks, query, sort, reversed, filter ->
            val playsByKey = HashMap<String, Int>()
            tracks.forEach { item ->
                if (item.plays > 0) playsByKey[ArtistCatalog.baseKey(item.track.song.title)] = item.plays
            }
            val sorted = ArtistCatalog.sort(tracks.map { it.track }, sort, playsByKey, reversed)
            val found = ArtistCatalog.search(sorted, query)
            val byId = tracks.associateBy { it.track.song.id }
            found.mapNotNull { byId[it.song.id] }
                .filter { item ->
                    when (filter) {
                        null -> true
                        ArtistSongFilter.LIKED -> item.isLiked
                        ArtistSongFilter.ON_DEVICE -> item.ownership == TrackOwnership.LOCAL || item.ownership == TrackOwnership.DOWNLOADED
                        ArtistSongFilter.NOT_YOURS -> item.ownership == TrackOwnership.NONE
                    }
                }
                // "Plays" lists only songs you've actually played.
                .let { list -> if (sort == ArtistSongSort.YOUR_PLAYS) list.filter { it.plays > 0 } else list }
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
     *
     * With nothing saved, each section is shown as soon as its own sources answer (releases, then
     * related artists, then the full catalogue) instead of all at once. Videos, the bio and tour
     * dates load alongside, never waiting on the catalogue.
     */
    private fun loadOnlineCrossPlatformData(artistName: String, force: Boolean = false) {
        aggregationJob?.cancel()
        if (!force) {
            catalogueReady.value = false
            _uiState.update { it.copy(loadedSections = emptySet()) }
            loadExtras(artistName)
            loadTour(artistName)
            loadSpotifyExtras(artistName)
        }
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
                markCatalogueLoaded()
                if (!cached.isStale && !force) {
                    _uiState.update { it.copy(isAggregating = false, isOffline = false) }
                    return@launch
                }
            }
            // Partial sections only when nothing is on screen yet: over a saved catalogue they'd
            // blank the sections that haven't refetched yet.
            val progressive = cached == null
            var partial = emptyProfile(artistName)
            val onSection: (suspend (ArtistProfileSection) -> Unit)? = if (!progressive) null else ({ section ->
                partialMutex.withLock {
                    partial = when (section) {
                        is ArtistProfileSection.Releases -> partial.copy(
                            popularReleases = section.albums,
                            singlesAndEPs = section.singlesAndEPs,
                            effectiveImageUrl = section.effectiveImageUrl,
                            fanCount = section.fanCount,
                            deezerArtistId = section.deezerArtistId,
                            deezerCandidates = section.deezerCandidates
                        )
                        is ArtistProfileSection.RelatedArtists -> partial.copy(fansAlsoLikeArtists = section.artists)
                        is ArtistProfileSection.Catalogue -> partial.copy(
                            tracks = section.tracks,
                            appearsOn = section.appearsOn,
                            genres = section.genres
                        )
                    }
                    applyProfile(partial, System.currentTimeMillis())
                    val done = when (section) {
                        is ArtistProfileSection.Releases -> setOf(ArtistSection.RELEASES)
                        is ArtistProfileSection.RelatedArtists -> setOf(ArtistSection.FANS)
                        is ArtistProfileSection.Catalogue -> setOf(ArtistSection.GENRES)
                    }
                    _uiState.update { it.copy(loadedSections = it.loadedSections + done) }
                    if (section is ArtistProfileSection.Catalogue) catalogueReady.value = true
                }
            })
            try {
                val profile = artistAggregationRepository.getArtistProfileData(
                    artistName = artistName,
                    forceRefresh = true,
                    localTitles = localSongsFlow.value.mapTo(HashSet()) { it.title },
                    onSection = onSection
                )
                partialMutex.withLock { applyProfile(profile, profile.fetchedAt) }
                _uiState.update { it.copy(isAggregating = false, isOffline = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w(TAG, "Artist catalogue unavailable: ${e.message}")
                _uiState.update {
                    it.copy(isAggregating = false, isOffline = true, hasSavedCatalogue = cached != null)
                }
            }
            markCatalogueLoaded()
        }
    }

    /** Every catalogue section has resolved (filled, empty, or failed). */
    private fun markCatalogueLoaded() {
        catalogueReady.value = true
        _uiState.update {
            it.copy(loadedSections = it.loadedSections + ArtistSection.RELEASES + ArtistSection.FANS + ArtistSection.GENRES)
        }
    }

    private fun emptyProfile(artistName: String) = ArtistCrossPlatformData(
        artistName = artistName,
        effectiveImageUrl = null,
        metrics = CrossPlatformArtistMetrics(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0, 0.0, 0.0, 0.0),
        popularTracks = emptyList(),
        popularReleases = emptyList(),
        singlesAndEPs = emptyList(),
        featuringPlaylists = emptyList(),
        fansAlsoLikeArtists = emptyList(),
        fansAlsoLikeSongs = emptyList(),
        fansAlsoLikePlaylists = emptyList()
    )

    /** Tour dates. Without a Ticketmaster key the tour section is resolved (empty) straight away. */
    // Events = Ticketmaster shows + Spotify concerts. Null = that source hasn't answered yet.
    // (All touched on the main thread, from viewModelScope.)
    private var ticketmasterShows: List<com.theveloper.pixelplay.data.tour.ArtistShow>? = null
    private var spotifyShows: List<com.theveloper.pixelplay.data.tour.ArtistShow>? = null
    private var tourJob: Job? = null
    private var spotifyJob: Job? = null
    private var similarJob: Job? = null
    // Similar's sources: Spotify related artists and Last.fm similar artists. Null = not answered yet.
    private var spotifyRelatedNames: List<String>? = null
    private var lastFmSimilarNames: List<String>? = null

    private fun loadTour(artistName: String, force: Boolean = false) {
        tourJob?.cancel()
        ticketmasterShows = null
        if (!tourRepository.isConfigured) {
            ticketmasterShows = emptyList()
            publishTour()
            return
        }
        tourJob = viewModelScope.launch {
            val tour = try {
                tourRepository.getTour(artistName, forceRefresh = force)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }
            ticketmasterShows = tour?.shows.orEmpty()
            publishTour()
        }
    }

    /** Shows what's arrived so far (merged); the section counts as loaded once both sources answered. */
    private fun publishTour() {
        val tm = ticketmasterShows
        val sp = spotifyShows
        val merged = com.theveloper.pixelplay.data.tour.TourMerge.merge(tm.orEmpty(), sp.orEmpty())
        val done = tm != null && sp != null
        _uiState.update {
            it.copy(
                tour = if (merged.isEmpty() && done) null else ArtistTour(merged, System.currentTimeMillis()),
                loadedSections = if (done) it.loadedSections + ArtistSection.TOUR else it.loadedSections
            )
        }
    }

    /** Spotify extras (playlists, concerts, merch, related artists); empty and loaded at once when signed out. */
    private fun loadSpotifyExtras(artistName: String, force: Boolean = false) {
        spotifyJob?.cancel()
        spotifyShows = null
        spotifyRelatedNames = null
        if (!spotifyArtistRepository.isAvailable) {
            spotifyShows = emptyList()
            spotifyRelatedNames = emptyList()
            _uiState.update { it.copy(spotifyPlaylists = emptyList(), merch = emptyList(), loadedSections = it.loadedSections + ArtistSection.SPOTIFY) }
            publishTour()
            maybeLoadSimilar()
            return
        }
        spotifyJob = viewModelScope.launch {
            val extras = try {
                spotifyArtistRepository.extras(artistName, force)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }
            spotifyShows = extras?.concerts.orEmpty()
            spotifyRelatedNames = extras?.relatedArtists.orEmpty().map { it.name }
            _uiState.update {
                it.copy(
                    spotifyPlaylists = extras?.playlists.orEmpty(),
                    merch = extras?.merch.orEmpty(),
                    loadedSections = it.loadedSections + ArtistSection.SPOTIFY
                )
            }
            publishTour()
            maybeLoadSimilar()
        }
    }

    /**
     * Similar, once both name sources answered: Spotify's related artists, else Last.fm's similar
     * artists; minus the artist itself. (The page also hides any already in Fans also like.)
     */
    private fun maybeLoadSimilar() {
        val spotify = spotifyRelatedNames ?: return
        val lastFm = lastFmSimilarNames ?: return
        val self = ArtistCatalog.fold(_uiState.value.artist?.name.orEmpty())
        val names = spotify.ifEmpty { lastFm }.filter { ArtistCatalog.fold(it) != self }
        similarJob?.cancel()
        if (names.isEmpty()) {
            _uiState.update { it.copy(similarArtists = emptyList(), similarSongs = emptyList(), loadedSections = it.loadedSections + ArtistSection.SIMILAR) }
            return
        }
        similarJob = viewModelScope.launch {
            val (artists, songs) = try {
                artistAggregationRepository.similarFor(names)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                emptyList<RelatedArtistItem>() to emptyList<Song>()
            }
            _uiState.update {
                it.copy(similarArtists = artists, similarSongs = songs, loadedSections = it.loadedSections + ArtistSection.SIMILAR)
            }
        }
    }

    private var aiJob: Job? = null

    /**
     * Answers [question] about the artist with Gemini Nano, on the phone only (no network): the
     * bio, genres, members and best-known songs are given as context. If Nano is busy or slow,
     * a simple answer from the same local data is shown instead.
     */
    fun askAi(question: String) {
        val q = question.trim()
        if (q.isBlank()) return
        val state = _uiState.value
        val name = state.artist?.name ?: return
        aiJob?.cancel()
        _uiState.update { it.copy(ai = it.ai.copy(thinking = true, question = q, answer = null)) }
        aiJob = viewModelScope.launch {
            val members = state.hub.members
            val context = buildString {
                appendLine("Artist: $name")
                state.bio?.takeIf { it.isNotBlank() }?.let { appendLine("Bio: ${it.take(AI_BIO_CHARS)}") }
                if (state.genres.isNotEmpty()) appendLine("Genres: ${state.genres.joinToString(", ")}")
                if (members.isNotEmpty()) {
                    appendLine("Members: " + members.joinToString("; ") { m ->
                        listOfNotNull(m.name, m.role.takeIf { it.isNotBlank() }, "past member".takeIf { !m.isCurrent }).joinToString(", ")
                    })
                }
                val top = state.tracks.sortedByDescending { it.track.popularity ?: 0 }.take(8).map { it.track.song.title }
                if (top.isNotEmpty()) appendLine("Popular songs: ${top.joinToString(", ")}")
                val releases = (state.popularReleases + state.singlesAndEPs).take(10)
                    .map { r -> listOfNotNull(r.title, r.releaseYear?.toString()).joinToString(" ") }
                if (releases.isNotEmpty()) appendLine("Releases: ${releases.joinToString(", ")}")
                append("Question: $q")
            }
            val answer = try {
                if (gemmaModels.activeModel() != null) {
                    // Gemma 4 with tools: it can look up the band's members (their photos appear in
                    // Members right away), search Wikipedia and read a page before answering.
                    val tools = com.theveloper.pixelplay.data.ai.gemma.ArtistAgentTools(artistHubRepository) { roster ->
                        if (roster.isNotEmpty() && _uiState.value.artist?.name == name) {
                            _uiState.update { it.copy(hub = it.hub.copy(members = roster)) }
                        }
                    }
                    gemmaEngine.generate(
                        prefix = AI_GEMMA_PREFIX.replace("{artist}", name),
                        input = context,
                        temperature = 0.3f,
                        timeoutMs = 45_000L,
                        tools = listOf(tools),
                    )
                } else {
                    nanoEngine.generate(prefix = AI_PREFIX, input = context, maxOutputTokens = 220, temperature = 0.3f, timeoutMs = 9_000L)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }?.trim()?.takeIf { it.isNotBlank() }
                ?: com.theveloper.pixelplay.data.artist.ArtistAiEngine().query(
                    artistName = name, bio = state.bio, prompt = q, genres = state.genres, members = members
                )
            _uiState.update { it.copy(ai = it.ai.copy(thinking = false, answer = answer)) }
        }
    }

    fun setSimilarFilter(filter: FansFilterType) {
        _uiState.update { it.copy(activeSimilarFilter = filter) }
    }

    /** A Spotify playlist's songs (first 100) for opening / playing it from the artist page. Empty on failure. */
    suspend fun spotifyPlaylistSongs(playlistId: String): List<Song> = try {
        withContext(Dispatchers.IO) {
            spotifyRepository.getPlaylistTracks(playlistId, maxTracks = SPOTIFY_PLAYLIST_MAX).map { it.toSong() }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Log.w(TAG, "Spotify playlist couldn't load", e)
        emptyList()
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

    /** Videos and the Last.fm bio load alongside the catalogue and never block it. */
    private fun loadExtras(artistName: String) {
        extrasJob?.cancel()
        lastFmSimilarNames = null
        aiJob?.cancel()
        _uiState.update { it.copy(ai = ArtistAiUiState(available = it.ai.available)) }
        extrasJob = viewModelScope.launch {
            // Live performances, after the music videos (so the same video isn't shown twice).
            launch {
                val live = try {
                    videoRepository.searchArtistVideos(artistName, com.theveloper.pixelplay.data.model.VideoType.LIVE_PERFORMANCE)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    emptyList()
                }
                _uiState.update {
                    it.copy(liveVideos = live.take(MAX_VIDEOS), loadedSections = it.loadedSections + ArtistSection.LIVE)
                }
            }
            // The artist's YouTube Music playlists (titles naming the artist), next to Spotify's.
            launch {
                val needle = ArtistCatalog.fold(artistName)
                val playlists = try {
                    youTubeMusicApi.searchCollections(artistName, com.theveloper.pixelplay.data.model.SearchFilterType.PLAYLISTS)
                        .filterIsInstance<com.theveloper.pixelplay.data.model.SearchResultItem.PlaylistItem>()
                        .mapNotNull { item ->
                            val browseId = item.browseId ?: return@mapNotNull null
                            item.playlist.name.takeIf { needle.isNotBlank() && needle in ArtistCatalog.fold(it) }
                                ?.let { ArtistYtPlaylist(browseId, it, item.playlist.coverImageUri) }
                        }
                        .distinctBy { it.browseId }
                        .take(MAX_YT_PLAYLISTS)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    emptyList()
                }
                _uiState.update { it.copy(ytPlaylists = playlists, loadedSections = it.loadedSections + ArtistSection.YT_PLAYLISTS) }
            }
            // Website, socials and band members (also feeds the About bio's Wikipedia fallback).
            val hubDeferred = async {
                try {
                    artistHubRepository.hubFor(artistName)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    com.theveloper.pixelplay.data.artist.ArtistHubInfo()
                }
            }
            launch {
                val hub = hubDeferred.await()
                _uiState.update { it.copy(hub = hub, loadedSections = it.loadedSections + ArtistSection.HUB) }
            }
            // Merch prices in AUD with today's rates.
            launch {
                exchangeRates.refreshIfStale()
                _uiState.update { it.copy(audRates = exchangeRates.rates.value) }
            }
            // The Ask box only appears where Gemini Nano is ready.
            launch {
                val ready = try {
                    nanoEngine.refreshStatus() == com.theveloper.pixelplay.data.ai.nano.NanoStatus.Ready
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    false
                }
                _uiState.update { it.copy(ai = it.ai.copy(available = ready)) }
            }
            launch {
                val videos = try {
                    videoRepository.searchArtistVideos(artistName)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    emptyList()
                }
                _uiState.update {
                    it.copy(videos = videos.take(MAX_VIDEOS), loadedSections = it.loadedSections + ArtistSection.VIDEOS)
                }
            }
            launch {
                val info = try {
                    lastFmRepository.getArtistInfo(artistName)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    null
                }
                val lastFmBio = info?.bio
                    ?.substringBefore("<a href")
                    ?.substringBefore(" Read more on Last.fm")
                    ?.trim()
                    ?.takeIf { b -> b.length > 40 }
                if (info != null) {
                    _uiState.update {
                        it.copy(
                            bio = lastFmBio,
                            bioSource = lastFmBio?.let { "Last.fm" },
                            genres = mergeGenres(it.genres, info.tags.map { tag -> tag.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } })
                        )
                    }
                }
                lastFmSimilarNames = info?.similarArtists.orEmpty()
                maybeLoadSimilar()
                // No Last.fm bio: Wikipedia (keyless). The screen then falls back to a summary
                // from your library, so About is never empty.
                if (lastFmBio == null) {
                    val wiki = try {
                        artistHubRepository.wikipediaBio(artistName, hubDeferred.await())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        null
                    }
                    if (wiki != null) _uiState.update { it.copy(bio = wiki, bioSource = "Wikipedia") }
                }
                _uiState.update { it.copy(loadedSections = it.loadedSections + ArtistSection.ABOUT) }
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
                plays = playsByKey[key] ?: 0,
                isLiked = track.song.id in favorites ||
                    (mine != null && (mine.id in favorites || mine.isFavorite)),
                recordingKey = com.theveloper.pixelplay.data.library.RecordingKeys.of(track.song)
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
                    .map { it.playSong },
                liked = tracks.count { it.isLiked }
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

    /** Flips the current sort's direction (the fixed button at the start of the chip row). */
    fun toggleSongSortDirection() {
        val next = !directionFlow.value
        directionFlow.value = next
        _uiState.update { it.copy(songSortReversed = next) }
    }

    /** One ownership filter at a time; selecting the active one again clears it (null). */
    fun setSongFilter(filter: ArtistSongFilter?) {
        filterFlow.value = filter
        _uiState.update { it.copy(songFilter = filter) }
    }

    fun setFansFilter(filter: FansFilterType) {
        _uiState.update { it.copy(activeFansFilter = filter) }
    }

    /** False without a Ticketmaster key: the page leaves the tour section out entirely. */
    val isTourConfigured: Boolean get() = tourRepository.isConfigured

    /** Re-fetches the catalogue now, ignoring the saved copy. */
    fun refreshCatalogue() {
        val name = _uiState.value.artist?.name ?: return
        loadOnlineCrossPlatformData(name, force = true)
        loadTour(name, force = true)
        loadSpotifyExtras(name, force = true)
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
        // All seeds shuffled together: the mix used to always open with the artist's #1 song.
        val seeds = (top + yours + similar).shuffled().distinctBy { it.id }
        // Catalogue songs are matched on YouTube before playback starts: keep the first batch short.
        return seeds.take(MAX_MIX_SEEDS)
    }

    /** Songs on [release] that aren't liked yet (Like albums). Null when its tracklist couldn't load. */
    suspend fun songsToLike(release: ArtistAlbumItem, favoriteIds: Set<String>): List<Song>? =
        releaseSongs(release).takeIf { it.isNotEmpty() }?.filterNot { it.id in favoriteIds || it.isFavorite }

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
        /** Songs read from a Spotify playlist opened on the artist page (memory cap). */
        const val SPOTIFY_PLAYLIST_MAX = 100
        const val MAX_VIDEOS = 12
        const val MAX_YT_PLAYLISTS = 8
        const val AI_BIO_CHARS = 1_800
        const val AI_GEMMA_PREFIX = "You are the music assistant inside PixelPlayer, answering a question about {artist}. " +
            "Use the facts given first. If they aren't enough, use your tools: getBandRosterAndPfps for who is in a band " +
            "or who plays what, searchArtistWebInfo for other facts, and readWebpageMarkdown only when a snippet isn't enough. " +
            "Answer in two to four short sentences. Never make up facts or links; if you can't find it, say so."
        const val AI_PREFIX = "You answer questions about a music artist using only the facts given. " +
            "Answer in two to four short sentences. If the facts don't say, reply that you don't know."
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
