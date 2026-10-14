package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.library.AlbumTracklistRepository
import com.theveloper.pixelplay.data.library.CollectionKeys
import com.theveloper.pixelplay.data.library.StreamCollectionRepository
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository // Importar MusicRepository
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Tracks of the official release you don't have yet (liked, downloaded or as files). */
    val missingTracks: List<Song> = emptyList(),
    /** Number of tracks on the official release; 0 when unknown. */
    val releaseTrackCount: Int = 0,
    /** How many of the songs shown are streamed likes (not files or downloads). */
    val streamingCount: Int = 0,
    /** Missing tracks being matched to audio right now (ids). */
    val busyTrackIds: Set<String> = emptySet(),
    /** One-off message for the screen (e.g. no audio found). */
    val message: String? = null,
    /** Album is in Library → Albums (saved with the heart, or a library album). */
    val isSaved: Boolean = false,
    /** Library albums are always in the library: their heart is shown filled but inactive. */
    val canToggleSave: Boolean = false,
    /** The official tracklist could not be loaded (offline / no match); the page offers Retry. */
    val tracklistFailed: Boolean = false,
    /** The official tracklist is being looked up (first load or Retry). */
    val isLoadingTracklist: Boolean = false,
    /** Resolving tracks to start "Play" / "Shuffle" on an album you don't own songs of. */
    val isStartingPlayback: Boolean = false
) {
    val ownedReleaseTracks: Int get() = (releaseTrackCount - missingTracks.size).coerceAtLeast(0)
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val artistAggregationRepository: com.theveloper.pixelplay.data.repository.ArtistAggregationRepository,
    private val streamCollection: StreamCollectionRepository,
    private val streamCollectionDao: com.theveloper.pixelplay.data.database.StreamCollectionDao,
    private val tracklists: AlbumTracklistRepository,
    private val youTubeResolver: com.theveloper.pixelplay.data.spotify.SpotifyToYouTubeResolver,
    private val downloadCoordinator: com.theveloper.pixelplay.data.youtube.DownloadCoordinator,
    private val innerTubeClient: com.theveloper.pixelplay.data.youtube.InnerTubeClient,
    private val youTubeMusicApiService: com.theveloper.pixelplay.data.youtube.YouTubeMusicApiService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private var tracklistJob: Job? = null
    private var onlineSaveJob: Job? = null
    private var tracklistFor: String? = null
    private var currentBrowsePayload: Screen.AlbumDetail.BrowsePayload? = null
    private var browseJob: Job? = null

    init {
        val albumIdString: String? = savedStateHandle.get("albumId")
        if (albumIdString != null) {
            val decoded = try { java.net.URLDecoder.decode(albumIdString, "UTF-8") } catch (e: Exception) { albumIdString }
            val albumId = decoded.toLongOrNull()
            if (decoded.startsWith(Screen.AlbumDetail.BROWSE_PREFIX)) {
                val payload = Screen.AlbumDetail.parseBrowsePayload(
                    decoded.removePrefix(Screen.AlbumDetail.BROWSE_PREFIX)
                )
                if (payload != null) {
                    currentBrowsePayload = payload
                    loadBrowseAlbum(payload)
                } else {
                    _uiState.update { it.copy(error = context.getString(R.string.album_detail_invalid_id), isLoading = false) }
                }
            } else if (decoded.startsWith(Screen.AlbumDetail.NAME_PREFIX)) {
                val (title, artist) = Screen.AlbumDetail.parseNamePayload(
                    decoded.removePrefix(Screen.AlbumDetail.NAME_PREFIX)
                )
                if (title.isNotBlank()) loadAlbumByName(title, artist)
                else _uiState.update { it.copy(error = context.getString(R.string.album_detail_invalid_id), isLoading = false) }
            } else if (albumId != null && CollectionKeys.isStreamAlbumId(albumId)) {
                loadStreamAlbum(albumId)
            } else if (albumId != null) {
                loadAlbumData(albumId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.album_detail_invalid_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.album_detail_id_not_found), isLoading = false) }
        }
    }

    /**
     * Album opened via YouTube Music browseId from search results.
     * Displays initial metadata immediately while fetching and hydrating the full tracklist.
     */
    private fun loadBrowseAlbum(payload: Screen.AlbumDetail.BrowsePayload) {
        val streamId = CollectionKeys.streamAlbumId(
            CollectionKeys.albumKey(payload.artist.ifBlank { "Unknown Artist" }, payload.title.ifBlank { "Album" })
        )
        val initialAlbum = Album(
            id = streamId,
            title = payload.title.ifBlank { "Album" },
            artist = payload.artist.ifBlank { "Unknown Artist" },
            year = payload.year,
            dateAdded = 0L,
            albumArtUriString = payload.artUrl,
            songCount = 0,
            albumArtist = payload.artist.ifBlank { "Unknown Artist" }
        )

        _uiState.update {
            it.copy(
                album = initialAlbum,
                isLoading = true,
                isLoadingTracklist = true,
                error = null,
                canToggleSave = true,
                tracklistFailed = false
            )
        }

        streamCollection.albumIdForName(payload.title, payload.artist, payload.artUrl, payload.year)

        if (onlineSaveJob == null) {
            onlineSaveJob = viewModelScope.launch {
                streamCollection.observeIsSaved(streamId).collect { saved ->
                    _uiState.update { it.copy(isSaved = saved) }
                }
            }
        }

        val transient = getTransientAlbum(payload.browseId)
        if (transient != null && transient.second.isNotEmpty()) {
            val (cachedAlbum, cachedSongs) = transient
            _uiState.update {
                it.copy(
                    album = cachedAlbum.copy(
                        id = streamId,
                        songCount = cachedSongs.size,
                        albumArtUriString = cachedAlbum.albumArtUriString ?: payload.artUrl
                    ),
                    songs = cachedSongs,
                    isLoading = false,
                    isLoadingTracklist = false,
                    releaseTrackCount = cachedSongs.size,
                    streamingCount = cachedSongs.size,
                    canToggleSave = true,
                    error = null
                )
            }
            return
        }

        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            try {
                val songsMap = LinkedHashMap<String, Song>()
                val seen = mutableSetOf<String>()
                var continuation: String? = null
                val maxPages = 10
                repeat(maxPages) {
                    val page = innerTubeClient.browse(payload.browseId, continuation)
                    youTubeMusicApiService.hydrate(page.songs).forEach { songsMap.putIfAbsent(it.id, it) }
                    val next = page.continuation ?: return@repeat
                    if (!seen.add(next)) return@repeat
                    continuation = next
                }

                val tracklist = songsMap.values.mapIndexed { index, song ->
                    song.copy(
                        album = if (song.album.isBlank() || song.album == "YouTube Music") initialAlbum.title else song.album,
                        albumId = streamId,
                        albumArtist = song.albumArtist?.takeIf { it.isNotBlank() } ?: initialAlbum.artist,
                        albumArtUriString = song.albumArtUriString ?: initialAlbum.albumArtUriString,
                        trackNumber = if (song.trackNumber > 0) song.trackNumber else (index + 1)
                    )
                }

                if (tracklist.isNotEmpty()) {
                    val finalAlbum = initialAlbum.copy(
                        songCount = tracklist.size,
                        albumArtUriString = initialAlbum.albumArtUriString ?: tracklist.firstOrNull()?.albumArtUriString
                    )
                    registerTransientAlbum(payload.browseId, finalAlbum, tracklist)
                    _uiState.update {
                        it.copy(
                            album = finalAlbum,
                            songs = tracklist,
                            isLoading = false,
                            isLoadingTracklist = false,
                            releaseTrackCount = tracklist.size,
                            streamingCount = tracklist.size,
                            canToggleSave = true,
                            error = null,
                            tracklistFailed = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingTracklist = false,
                            error = context.getString(R.string.album_detail_not_found),
                            tracklistFailed = true
                        )
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.e(e, "Failed to load browse album ${payload.browseId}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingTracklist = false,
                        error = context.getString(R.string.album_detail_error_loading_album, e.localizedMessage ?: ""),
                        tracklistFailed = true
                    )
                }
            }
        }
    }

    /**
     * Album opened by name (cloud songs, streams and unindexed files have no library album id):
     * a matching library album opens as usual; otherwise it opens as a stream album of that name,
     * which shows any liked songs of it and fills in the official tracklist from the catalogue.
     */
    private fun loadAlbumByName(title: String, artist: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val wantedTitle = CollectionKeys.normalizeAlbum(title)
            val wantedArtist = artist?.let { CollectionKeys.normalizeArtist(it) }?.takeIf { it.isNotBlank() }
            val local = runCatching {
                musicRepository.searchAlbums(title).first()
            }.getOrNull().orEmpty().filter { CollectionKeys.normalizeAlbum(it.title) == wantedTitle }
            val match = local.firstOrNull { album ->
                wantedArtist == null ||
                    CollectionKeys.normalizeArtist(album.albumArtist?.takeIf { it.isNotBlank() } ?: album.artist) == wantedArtist ||
                    CollectionKeys.normalizeArtist(album.artist) == wantedArtist
            } ?: local.singleOrNull()
            val id = match?.id ?: streamCollection.albumIdForName(title, artist)
            if (CollectionKeys.isStreamAlbumId(id)) loadStreamAlbum(id) else loadAlbumData(id)
        }
    }

    private val trackOrder = compareBy<Song> { it.discNumber ?: 1 }
        .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
        .thenBy { it.title.lowercase() }

    private fun loadAlbumData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val albumDetailsFlow = musicRepository.getAlbumById(id)
                val albumSongsFlow = musicRepository.getSongsForAlbum(id)
                // Liked songs of this album that you only stream.
                val streamedFlow = streamCollection.songsByAlbumId.map { it[id].orEmpty() }

                combine(albumDetailsFlow, albumSongsFlow, streamedFlow) { album, songs, streamed ->
                    if (album != null) {
                        val known = songs.mapTo(HashSet()) { AlbumTracklistRepository.normalizeTrack(it.title) }
                        val extra = streamed.filterNot { AlbumTracklistRepository.normalizeTrack(it.title) in known }
                        _uiState.value.copy(
                            album = album,
                            songs = (songs + extra).sortedWith(trackOrder),
                            streamingCount = extra.size,
                            isLoading = false,
                            error = null,
                            isSaved = true,
                            canToggleSave = false
                        )
                    } else {
                        null
                    }
                }
                    .catch { emit(null) }
                    .collect { localState ->
                        if (localState != null) {
                            _uiState.value = localState
                            loadMissingTracks(localState.album!!)
                        } else {
                            // Fallback: Check online album via ArtistAggregationRepository
                            try {
                                val (onlineAlbum, onlineSongs) = artistAggregationRepository.getOnlineAlbumWithSongs(id)
                                if (onlineSongs.isNotEmpty()) {
                                    _uiState.value = AlbumDetailUiState(
                                        album = onlineAlbum,
                                        songs = onlineSongs,
                                        isLoading = false,
                                        releaseTrackCount = onlineSongs.size,
                                        canToggleSave = true
                                    )
                                    // Its saved copy lives under the stream album id of its name.
                                    val savedId = CollectionKeys.streamAlbumId(
                                        CollectionKeys.albumKey(onlineAlbum.albumArtist ?: onlineAlbum.artist, onlineAlbum.title)
                                    )
                                    if (onlineSaveJob == null) onlineSaveJob = launch {
                                        streamCollection.observeIsSaved(savedId).collect { saved ->
                                            _uiState.update { it.copy(isSaved = saved) }
                                        }
                                    }
                                    loadMissingTracks(onlineAlbum)
                                } else {
                                    _uiState.value = AlbumDetailUiState(
                                        error = context.getString(R.string.album_detail_not_found),
                                        isLoading = false
                                    )
                                }
                            } catch (e: Exception) {
                                _uiState.value = AlbumDetailUiState(
                                    error = context.getString(R.string.album_detail_not_found),
                                    isLoading = false
                                )
                            }
                        }
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.album_detail_error_loading_album, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * A stream album: liked songs you only stream, an album you saved, or an online album opened
     * from the player (known only by name until its tracklist loads). Read-only.
     */
    private fun loadStreamAlbum(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val row = streamCollectionDao.getAlbumById(id)
            val saved = if (row == null) streamCollection.savedAlbum(id) else null
            val hint = if (row == null && saved == null) streamCollection.onlineAlbumHint(id) else null
            val album = when {
                row != null -> Album(
                    id = row.id,
                    title = row.title,
                    artist = row.artistName,
                    year = row.year,
                    dateAdded = row.dateAdded,
                    albumArtUriString = row.albumArtUriString,
                    songCount = row.songCount,
                    albumArtist = row.albumArtist
                )
                saved != null -> Album(
                    id = saved.id,
                    title = saved.title,
                    artist = saved.artistName,
                    year = saved.year,
                    dateAdded = saved.savedAt,
                    albumArtUriString = saved.albumArtUriString,
                    songCount = saved.trackCount,
                    albumArtist = saved.artistName
                )
                hint != null -> Album(
                    id = id,
                    title = hint.title,
                    artist = hint.artist,
                    year = hint.year,
                    dateAdded = 0L,
                    albumArtUriString = hint.artUri,
                    songCount = 1,
                    albumArtist = hint.artist
                )
                else -> null
            }
            if (album == null) {
                _uiState.value = AlbumDetailUiState(error = context.getString(R.string.album_detail_not_found), isLoading = false)
                return@launch
            }
            val seed = listOfNotNull(hint?.seedSong)
            launch {
                streamCollection.observeIsSaved(id).collect { isSaved ->
                    _uiState.update { it.copy(isSaved = isSaved, canToggleSave = true) }
                }
            }
            streamCollection.songsByAlbumId
                // Right after launch the collection hasn't been rebuilt yet (empty map): keep the
                // page loading instead of showing an album with no songs.
                .filter { byAlbum -> row == null || byAlbum.isNotEmpty() }
                .map { it[id].orEmpty() }
                .collect { liked ->
                    // The playing song stays on the page until the album's own songs arrive.
                    val known = liked.mapTo(HashSet()) { AlbumTracklistRepository.normalizeTrack(it.title) }
                    val songs = liked + seed.filterNot { AlbumTracklistRepository.normalizeTrack(it.title) in known }
                    _uiState.update {
                        it.copy(
                            album = album.copy(
                                songCount = maxOf(songs.size, album.songCount, 1),
                                // Keep a cover the catalogue filled in for albums opened by name.
                                albumArtUriString = album.albumArtUriString ?: it.album?.albumArtUriString
                            ),
                            songs = songs.sortedWith(trackOrder),
                            streamingCount = songs.size,
                            isLoading = false,
                            error = null
                        )
                    }
                    loadMissingTracks(album)
                }
        }
    }

    /** Heart on the album page: add the album to / remove it from Library → Albums. */
    fun toggleSaved() {
        val state = _uiState.value
        val album = state.album ?: return
        if (!state.canToggleSave) return
        viewModelScope.launch {
            if (state.isSaved) {
                val savedId = if (CollectionKeys.isStreamAlbumId(album.id)) album.id else CollectionKeys.streamAlbumId(
                    CollectionKeys.albumKey(album.albumArtist?.takeIf { it.isNotBlank() } ?: album.artist, album.title)
                )
                streamCollection.unsaveAlbum(savedId)
                _uiState.update { it.copy(message = "Removed from your albums") }
            } else {
                val tracks = maxOf(state.releaseTrackCount, state.songs.size + state.missingTracks.size, 1)
                streamCollection.saveAlbum(album, tracks)
                _uiState.update { it.copy(message = "Added to your albums") }
            }
        }
    }

    /** Retry after the tracklist lookup failed (offline, or no match). */
    fun retryTracklist() {
        val currentBrowse = currentBrowsePayload
        if (currentBrowse != null) {
            loadBrowseAlbum(currentBrowse)
            return
        }
        val album = _uiState.value.album ?: return
        if (_uiState.value.isLoadingTracklist) return
        tracklistJob?.cancel()
        tracklistFor = null
        tracklistJob = null
        _uiState.update { it.copy(tracklistFailed = false) }
        loadMissingTracks(album)
    }

    /**
     * Play / Shuffle for the whole album, including tracks you don't have yet: the first track is
     * matched to audio and starts right away ([onStart]); the rest are matched in order and
     * appended with [onAppend], so playback never waits for the whole album.
     */
    fun playWholeAlbum(shuffle: Boolean, onStart: (Song, List<Song>) -> Unit, onAppend: (Song) -> Unit) {
        val state = _uiState.value
        if (state.isStartingPlayback) return
        val owned = state.songs
        val all = (owned + state.missingTracks).let { if (shuffle) it.shuffled() else it.sortedWith(trackOrder) }
        if (all.isEmpty()) return
        if (state.missingTracks.isEmpty()) {
            onStart(if (shuffle) owned.random() else owned.first(), owned)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingPlayback = true) }
            var started = false
            try {
                for (track in all) {
                    val playable = resolveOrNull(track) ?: continue
                    if (!started) {
                        started = true
                        _uiState.update { it.copy(isStartingPlayback = false) }
                        onStart(playable, listOf(playable))
                    } else {
                        onAppend(playable)
                    }
                }
                if (!started) _uiState.update { it.copy(message = "Couldn't find audio for this album") }
            } finally {
                _uiState.update { it.copy(isStartingPlayback = false) }
            }
        }
    }

    /** Looks the official tracklist up once per album and works out which tracks are missing. */
    private fun loadMissingTracks(album: Album) {
        val albumArtist = album.albumArtist?.takeIf { it.isNotBlank() } ?: album.artist
        if (albumArtist.isBlank() || album.title.isBlank()) return
        val request = albumArtist + "|" + album.title
        if (tracklistFor == request && tracklistJob != null) {
            // Same album: recompute against the cached list when the owned songs change.
            tracklists.cached(albumArtist, album.title)?.let { applyTracklist(it, album) }
            return
        }
        tracklistFor = request
        tracklistJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTracklist = true, tracklistFailed = false) }
            // Same catalogue lookup for library, online and stream albums, so "Missing from your
            // library" shows on every album the release is found for.
            val tracklist = try {
                tracklists.find(albumArtist, album.title)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (tracklist == null) {
                _uiState.update { it.copy(isLoadingTracklist = false, tracklistFailed = true) }
                return@launch
            }
            _uiState.update { it.copy(isLoadingTracklist = false, tracklistFailed = false) }
            applyTracklist(tracklist, album)
        }
    }

    private fun applyTracklist(tracklist: AlbumTracklistRepository.Tracklist, album: Album) {
        // Always compare with the songs on screen now (they can change while the lookup runs).
        val ownedKeys = _uiState.value.songs.mapTo(HashSet()) { AlbumTracklistRepository.normalizeTrack(it.title) }
        val missing = tracklist.tracks
            .filterNot { AlbumTracklistRepository.normalizeTrack(it.title) in ownedKeys }
            .map { track ->
                // Apple Music ids are iTunes track ids; playing or liking one resolves the audio
                // (see resolvePlayable).
                Song(
                    id = "applemusic_${track.trackId}",
                    title = track.title,
                    artist = track.artist,
                    artistId = CollectionKeys.streamArtistId(track.artist),
                    album = tracklist.title,
                    albumId = album.id,
                    albumArtist = tracklist.artist,
                    path = "applemusic://${track.trackId}",
                    contentUriString = "applemusic://${track.trackId}",
                    albumArtUriString = album.albumArtUriString ?: tracklist.artworkUrl,
                    duration = track.durationMs,
                    trackNumber = track.number,
                    year = tracklist.year
                )
            }
        _uiState.update { state ->
            // Albums opened by name have no cover until the catalogue supplies one.
            val current = state.album
            val withArt = if (current != null && current.albumArtUriString.isNullOrBlank() && !tracklist.artworkUrl.isNullOrBlank()) {
                current.copy(albumArtUriString = tracklist.artworkUrl)
            } else current
            state.copy(album = withArt, missingTracks = missing, releaseTrackCount = tracklist.size)
        }
    }

    /**
     * Turns a missing catalogue track into a YouTube song the rest of the app can play,
     * like and download. Null when no matching audio was found.
     */
    suspend fun resolvePlayable(track: Song): Song? {
        val trackId = track.id.removePrefix("applemusic_")
        val videoId = runCatching {
            youTubeResolver.resolveSpotifyTrackToVideoId(
                "am:$trackId", track.title, track.artist,
                track.duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), null
            )
        }.getOrNull() ?: return null
        return track.copy(
            id = "yt_$videoId",
            youtubeId = videoId,
            path = "",
            contentUriString = "youtube://$videoId"
        )
    }

    /** Likes a missing track (after finding its audio). It then moves into the album's songs. */
    fun likeMissing(track: Song) = withResolved(track) { song ->
        musicRepository.saveCloudSong(song)
        musicRepository.setFavoriteStatus(song.id, true)
    }

    /** Downloads a missing track (after finding its audio). */
    fun downloadMissing(track: Song) = withResolved(track) { song ->
        downloadCoordinator.download(song)
    }

    /** Plays a missing track: [onReady] gets the playable song. */
    fun playMissing(track: Song, onReady: (Song) -> Unit) = withResolved(track) { song -> onReady(song) }

    /** "Get all": downloads every song of this album you don't have offline yet. */
    fun downloadAll() {
        val state = _uiState.value
        viewModelScope.launch {
            state.songs
                .filter { downloadCoordinator.isOnlineSong(it) && !downloadCoordinator.isDownloaded(it) }
                .forEach { downloadCoordinator.download(it) }
            state.missingTracks.forEach { track ->
                resolveOrNull(track)?.let { downloadCoordinator.download(it) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun withResolved(track: Song, action: suspend (Song) -> Unit) {
        if (track.id in _uiState.value.busyTrackIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyTrackIds = it.busyTrackIds + track.id) }
            try {
                val song = resolveOrNull(track)
                if (song == null) {
                    _uiState.update { it.copy(message = "Couldn't find audio for \"${track.title}\"") }
                } else {
                    action(song)
                }
            } finally {
                _uiState.update { it.copy(busyTrackIds = it.busyTrackIds - track.id) }
            }
        }
    }

    private suspend fun resolveOrNull(track: Song): Song? =
        if (track.id.startsWith("applemusic_")) resolvePlayable(track) else track

    fun update(songs: List<Song>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                songs = songs
            )
        }
    }

    companion object {
        private val transientBrowseAlbums = java.util.concurrent.ConcurrentHashMap<String, Pair<Album, List<Song>>>()
        private const val MAX_TRANSIENT_ALBUMS = 12

        fun registerTransientAlbum(browseId: String, album: Album, songs: List<Song>) {
            if (transientBrowseAlbums.size >= MAX_TRANSIENT_ALBUMS && !transientBrowseAlbums.containsKey(browseId)) {
                transientBrowseAlbums.clear()
            }
            transientBrowseAlbums[browseId] = album to songs
        }

        fun getTransientAlbum(browseId: String): Pair<Album, List<Song>>? =
            transientBrowseAlbums[browseId]
    }
}
