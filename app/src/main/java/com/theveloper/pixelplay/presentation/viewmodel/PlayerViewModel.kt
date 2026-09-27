package com.theveloper.pixelplay.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Trace
import android.util.Log
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.Animatable
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.model.LibraryTabId
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.Timeline
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.FolderSource
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.HeardSongItem
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.toLibraryTabIdOrNull
import com.theveloper.pixelplay.data.repository.HeardSongsRepository
import com.theveloper.pixelplay.data.provider.SharedArtworkContentProvider
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.MusicNotificationProvider
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.service.cast.CastRemotePlaybackState
import com.theveloper.pixelplay.data.service.player.CastPlayer
import com.theveloper.pixelplay.data.service.http.MediaFileHttpServerService
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.utils.ValidatedLyricsImport
import com.theveloper.pixelplay.utils.LocalArtworkUri
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.StorageType
import com.theveloper.pixelplay.utils.StorageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil.imageLoader
import coil.memory.MemoryCache
import dagger.Lazy

private const val CAST_LOG_TAG = "PlayerCastTransfer"
private const val ENABLE_FOLDERS_SOURCE_SWITCHING = true
private const val HOME_MIX_PREVIEW_LIMIT = 48
private const val EXTERNAL_SONG_ID_PREFIX = "external:"

internal fun List<Song>.toPlaybackQueue(): ImmutableList<Song> = when (this) {
    is PersistentList<Song> -> this
    is ImmutableList<Song> -> this
    else -> this.toPersistentList()
}

internal fun ImmutableList<Song>.asPersistentPlaybackQueue(): PersistentList<Song> =
    this as? PersistentList<Song> ?: this.toPersistentList()

@Suppress("DEPRECATION")
internal fun ImmutableList<Song>.replaceSong(updatedSong: Song): ImmutableList<Song> {
    val index = indexOfFirst { it.id == updatedSong.id }
    if (index == -1) return this
    return asPersistentPlaybackQueue().set(index, updatedSong)
}

@Suppress("DEPRECATION")
private fun ImmutableList<Song>.removeSongById(songId: String): ImmutableList<Song> {
    val index = indexOfFirst { it.id == songId }
    if (index == -1) return this
    return asPersistentPlaybackQueue().removeAt(index)
}

@Suppress("DEPRECATION")
private fun ImmutableList<Song>.moveSong(fromIndex: Int, toIndex: Int): ImmutableList<Song> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    val movedSong = this[fromIndex]
    return asPersistentPlaybackQueue()
        .removeAt(fromIndex)
        .add(toIndex, movedSong)
}

private fun moveQueueIndex(index: Int, fromIndex: Int, toIndex: Int): Int {
    if (index == C.INDEX_UNSET || fromIndex == toIndex) return index
    return when {
        index == fromIndex -> toIndex
        fromIndex < toIndex && index in (fromIndex + 1)..toIndex -> index - 1
        toIndex < fromIndex && index in toIndex until fromIndex -> index + 1
        else -> index
    }
}

private data class AiUiSnapshot(
    val showAiPlaylistSheet: Boolean,
    val isGeneratingAiPlaylist: Boolean,
    val aiStatus: String?,
    val aiError: String?,
)

private data class SortOptionsSnapshot(
    val songSort: SortOption,
    val albumSort: SortOption,
    val artistSort: SortOption,
    val folderSort: SortOption,
    val favoriteSort: SortOption,
)

@UnstableApi
@SuppressLint("LogNotTimber")
@OptIn(coil.annotation.ExperimentalCoilApi::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    val syncManager: SyncManager, // Inyectar SyncManager
    private val streamCollection: com.theveloper.pixelplay.data.library.StreamCollectionRepository,

    private val dualPlayerEngine: DualPlayerEngine,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val continuousMixRuntime: com.theveloper.pixelplay.data.ContinuousMixRuntime,
    private val dailyMixStateHolder: DailyMixStateHolder,
    private val lyricsStateHolder: LyricsStateHolder,
    private val castStateHolder: CastStateHolder,
    private val castRouteStateHolder: CastRouteStateHolder,
    private val queueStateHolder: QueueStateHolder,
    private val queueUndoStateHolder: QueueUndoStateHolder,
    private val playlistDismissUndoStateHolder: PlaylistDismissUndoStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val sleepTimerStateHolder: SleepTimerStateHolder,
    private val searchStateHolder: SearchStateHolder,
    private val aiStateHolder: AiStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val folderNavigationStateHolder: FolderNavigationStateHolder,
    private val libraryTabsStateHolder: LibraryTabsStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val songRemovalStateHolder: SongRemovalStateHolder,
    val themeStateHolder: ThemeStateHolder,
    val multiSelectionStateHolder: MultiSelectionStateHolder,
    val playlistSelectionStateHolder: PlaylistSelectionStateHolder,
    private val playbackDispatchStateHolder: PlaybackDispatchStateHolder,
    private val mediaControllerSyncStateHolder: MediaControllerSyncStateHolder,
    private val sessionToken: SessionToken,
    private val mediaControllerFactory: com.theveloper.pixelplay.data.media.MediaControllerFactory,
    private val castTokenStore: com.theveloper.pixelplay.data.service.cast.CastTokenStore,
    private val genreCategorizerEngine: com.theveloper.pixelplay.data.analysis.GenreCategorizerEngine,
    private val heardSongsRepository: HeardSongsRepository
) : ViewModel() {

    val heardSongs: StateFlow<List<HeardSongItem>> = heardSongsRepository.heardSongs
    val latestHeardSongEvent: SharedFlow<HeardSongItem> = heardSongsRepository.latestHeardEvent

    val ambientSuggestionsEnabled: StateFlow<Boolean> =
        com.theveloper.pixelplay.data.recognition.ambient.AmbientListeningService.isRunning

    fun approveHeardSong(item: HeardSongItem) {
        addSelectedToQueue(listOf(item.song))
        heardSongsRepository.dismiss(item.id)
    }

    fun playNextHeardSong(item: HeardSongItem) {
        addSelectedAsNext(listOf(item.song))
        heardSongsRepository.dismiss(item.id)
    }

    fun dismissHeardSong(id: String) {
        heardSongsRepository.dismiss(id)
    }

    fun clearAllHeardSongs() {
        heardSongsRepository.clearAll()
    }

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()

    // Dedicated queue flow so the player sheet's MiniPlayer branch does not
    // recompose whenever the queue changes. Consumers that actually need the
    // queue (FullPlayer carousel, queue sheet) collect this narrower flow
    // directly, keeping the unrelated subtree stable.
    val queueFlow: StateFlow<ImmutableList<Song>> = _playerUiState
        .map { it.currentPlaybackQueue }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = persistentListOf()
        )

    private val _showNoInternetDialog = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val showNoInternetDialog: SharedFlow<Unit> = _showNoInternetDialog.asSharedFlow()

    val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState
    val albumArtPaletteStyle: StateFlow<AlbumArtPaletteStyle> = themePreferencesRepository
        .albumArtPaletteStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AlbumArtPaletteStyle.default
        )
    /**
     * High-frequency playback position should not force global UI recomposition.
     * Keep a dedicated position flow for real-time UI elements (seek bars, lyrics timing).
     */
    val currentPlaybackPosition: StateFlow<Long> = playbackStateHolder.currentPosition
    val playbackHistory = listeningStatsTracker.playbackHistory

    // Removed: _masterAllSongs was a duplicate of libraryStateHolder.allSongs
    // All reads now delegate to libraryStateHolder.allSongs

    // Lyrics load callback for LyricsStateHolder
    private val lyricsLoadCallback = object : LyricsLoadCallback {
        override fun onLoadingStarted(songId: String) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(isLoadingLyrics = true, lyrics = null)
            }
        }

        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(
                    isLoadingLyrics = false,
                    // Drops a leading "Title - Artist" row, which needs the song to recognise.
                    lyrics = lyrics?.let {
                        com.theveloper.pixelplay.utils.LyricsCleanup.clean(it, state.currentSong?.title, state.currentSong?.artist)
                    }
                )
            }
        }
    }



    private val _playlistPickerStorageFilter = MutableStateFlow(com.theveloper.pixelplay.data.model.StorageFilter.OFFLINE)
    val playlistPickerStorageFilter: StateFlow<com.theveloper.pixelplay.data.model.StorageFilter> = _playlistPickerStorageFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistPickerFavoriteSongs: Flow<PagingData<Song>> = combine(
        libraryStateHolder.currentSongSortOption,
        _playlistPickerStorageFilter
    ) { sortOption, storageFilter ->
        sortOption to storageFilter
    }
        .flatMapLatest { (sortOption, storageFilter) ->
            musicRepository.getPaginatedFavoriteSongs(
                sortOption = sortOption,
                storageFilter = storageFilter
            )
        }
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistPickerSongs: Flow<PagingData<Song>> = combine(
        libraryStateHolder.currentSongSortOption,
        _playlistPickerStorageFilter
    ) { sortOption, storageFilter ->
        sortOption to storageFilter
    }
        .flatMapLatest { (sortOption, storageFilter) ->
            musicRepository.getPaginatedSongs(
                sortOption = sortOption,
                storageFilter = storageFilter
            )
        }
        .cachedIn(viewModelScope)

    private val offlinePlaybackObserverJob = viewModelScope.launch {
        connectivityStateHolder.offlinePlaybackBlocked.collect {
            Timber.w("Received offline blocked event. Showing dialog.")
            _showNoInternetDialog.emit(Unit)
        }
    }

    private suspend fun refreshArtwork(updatedArtUri: String) {
        val currentState = playbackStateHolder.stablePlayerState.value
        val currentSong = currentState.currentSong
        // Check if it matches, ignoring query params for comparison
        val currentUriClean = currentSong?.albumArtUriString?.substringBefore('?')
        val updatedUriClean = updatedArtUri.substringBefore('?')
        
        if (currentUriClean == updatedUriClean) {
            Timber.d("PlayerViewModel: Embedded art updated for current song, forcing refresh")
            
            // 1. Invalidate Coil cache for the BASE uri (without params)
            // This ensures next time we load it without params, it's fresh too.
            val baseUri = currentUriClean
            
            // Remove from Memory Cache
            context.imageLoader.memoryCache?.keys?.forEach { key ->
                if (key.toString().contains(baseUri)) {
                    context.imageLoader.memoryCache?.remove(key)
                }
            }
            // Remove from Disk Cache
            context.imageLoader.diskCache?.remove(baseUri)

            // 2. Extract Colors (using base URI)
            themeStateHolder.extractAndGenerateColorScheme(updatedArtUri.toUri(), updatedArtUri, isPreload = false)
            
            // 3. FORCE UI REFRESH by updating the URI with a version timestamp
            // This forces SmartImage to see a "new" model and reload.
            // We keep the quality param if it exists, or add a version param.
            val newUri = if (updatedArtUri.contains("?")) {
                "$updatedArtUri&v=${System.currentTimeMillis()}"
            } else {
                "$updatedArtUri?v=${System.currentTimeMillis()}"
            }
            
            val updatedSong = currentSong.copy(albumArtUriString = newUri)
            
            // Update State
            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(currentSong = updatedSong)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSongArtists: StateFlow<List<Artist>> = stablePlayerState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .flatMapLatest { songId ->
            val idLong = songId?.toLongOrNull()
            if (idLong == null) flowOf(emptyList())
            else musicRepository.getArtistsForSong(idLong)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sheetState = MutableStateFlow(PlayerSheetState.COLLAPSED)
    val sheetState: StateFlow<PlayerSheetState> = _sheetState.asStateFlow()
    private val _isSheetVisible = MutableStateFlow(false)
    private val _bottomBarHeight = MutableStateFlow(0)
    val bottomBarHeight: StateFlow<Int> = _bottomBarHeight.asStateFlow()
    private val _predictiveBackCollapseFraction = MutableStateFlow(0f)
    val predictiveBackCollapseFraction: StateFlow<Float> = _predictiveBackCollapseFraction.asStateFlow()
    private val _predictiveBackSwipeEdge = MutableStateFlow<Int?>(null)
    val predictiveBackSwipeEdge: StateFlow<Int?> = _predictiveBackSwipeEdge.asStateFlow()
    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()
    private val _isCastSheetVisible = MutableStateFlow(false)
    val isCastSheetVisible: StateFlow<Boolean> = _isCastSheetVisible.asStateFlow()

    sealed interface BottomSheetState {
        data object Hidden : BottomSheetState
        data object AudioOutput : BottomSheetState
        data object Queue : BottomSheetState
    }

    private val _bottomSheetState = MutableStateFlow<BottomSheetState>(BottomSheetState.Hidden)
    val bottomSheetState: StateFlow<BottomSheetState> = _bottomSheetState.asStateFlow()

    fun onOpenAudioOutputSheet() {
        _bottomSheetState.value = BottomSheetState.AudioOutput
        _isCastSheetVisible.value = true
        _isQueueSheetVisible.value = false
    }

    fun onOpenQueueSheet() {
        _bottomSheetState.value = BottomSheetState.Queue
        _isQueueSheetVisible.value = true
        _isCastSheetVisible.value = false
    }

    fun onCloseBottomSheet() {
        _bottomSheetState.value = BottomSheetState.Hidden
        _isQueueSheetVisible.value = false
        _isCastSheetVisible.value = false
    }

    val playerContentExpansionFraction = Animatable(0f)

    private val _isMiniPlayerDismissing = MutableStateFlow(false)
    val isMiniPlayerDismissing: StateFlow<Boolean> = _isMiniPlayerDismissing.asStateFlow()

    fun setMiniPlayerDismissing(dismissing: Boolean) {
        _isMiniPlayerDismissing.value = dismissing
    }

    // AI Ecosystem: States delegated to AiStateHolder for centralized management
    val showAiPlaylistSheet: StateFlow<Boolean> = aiStateHolder.showAiPlaylistSheet
    val isGeneratingAiPlaylist: StateFlow<Boolean> = aiStateHolder.isGeneratingAiPlaylist
    val aiSuccess: StateFlow<Boolean> = aiStateHolder.aiSuccess
    val aiStatus: StateFlow<String?> = aiStateHolder.aiStatus
    val aiError: StateFlow<String?> = aiStateHolder.aiError

    private val _selectedSongForInfo = MutableStateFlow<Song?>(null)
    val selectedSongForInfo: StateFlow<Song?> = _selectedSongForInfo.asStateFlow()

    // Theme & Colors - delegated to ThemeStateHolder
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.currentAlbumArtColorSchemePair
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.activePlayerColorSchemePair
    val currentThemedAlbumArtUri: StateFlow<String?> = themeStateHolder.currentAlbumArtUri

    val playerThemePreference: StateFlow<String> = themePreferencesRepository.playerThemePreferenceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemePreference.ALBUM_ART
        )

    val navBarCornerRadius: StateFlow<Int> = userPreferencesRepository.navBarCornerRadiusFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 32)

    val navBarStyle: StateFlow<String> = userPreferencesRepository.navBarStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = NavBarStyle.DEFAULT
        )

    val navBarCompactMode: StateFlow<Boolean> = userPreferencesRepository.navBarCompactModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val libraryNavigationMode: StateFlow<String> = userPreferencesRepository.libraryNavigationModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryNavigationMode.TAB_ROW
        )

    val carouselStyle: StateFlow<String> = userPreferencesRepository.carouselStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CarouselStyle.NO_PEEK
        )

    val hasActiveAiProviderApiKey: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    val hasGeminiApiKey: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    val fullPlayerLoadingTweaks: StateFlow<FullPlayerLoadingTweaks> = userPreferencesRepository.fullPlayerLoadingTweaksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FullPlayerLoadingTweaks()
        )

    val showPlayerFileInfo: StateFlow<Boolean> = userPreferencesRepository.showPlayerFileInfoFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * Whether tapping the background of the player sheet toggles its state.
     * When disabled, users must use gestures or buttons to expand/collapse.
     */
    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hapticsEnabled: StateFlow<Boolean> = userPreferencesRepository.hapticsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Lyrics sync offset - now managed by LyricsStateHolder
    val currentSongLyricsSyncOffset: StateFlow<Int> = lyricsStateHolder.currentSongSyncOffset

    // Lyrics source preference (API_FIRST, EMBEDDED_FIRST, LOCAL_FIRST)
    val lyricsSourcePreference: StateFlow<LyricsSourcePreference> = userPreferencesRepository.lyricsSourcePreferenceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LyricsSourcePreference.EMBEDDED_FIRST
        )

    val immersiveLyricsEnabled: StateFlow<Boolean> = userPreferencesRepository.immersiveLyricsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val immersiveLyricsTimeout: StateFlow<Long> = userPreferencesRepository.immersiveLyricsTimeoutFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 4000L
        )

    private val _isImmersiveTemporarilyDisabled = MutableStateFlow(false)
    val isImmersiveTemporarilyDisabled: StateFlow<Boolean> = _isImmersiveTemporarilyDisabled.asStateFlow()

    fun setImmersiveTemporarilyDisabled(disabled: Boolean) {
        _isImmersiveTemporarilyDisabled.value = disabled
    }

    val albumArtQuality: StateFlow<AlbumArtQuality> = userPreferencesRepository.albumArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumArtQuality.MEDIUM)

    fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        lyricsStateHolder.setSyncOffset(songId, offsetMs)
    }

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /** Mini player left→right song-change wave (Appearance → Mini player song transition). */
    private val miniPlayerSongTransition: Flow<Boolean> = userPreferencesRepository.miniPlayerSongTransitionFlow

    val disableBlurAllOver: StateFlow<Boolean> = userPreferencesRepository.disableBlurAllOverFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val coverLyricsEnabled: StateFlow<Boolean> = userPreferencesRepository.coverLyricsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val useAnimatedLyrics: StateFlow<Boolean> = userPreferencesRepository.useAnimatedLyricsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val animatedLyricsBlurEnabled: StateFlow<Boolean> = userPreferencesRepository.animatedLyricsBlurEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val animatedLyricsBlurStrength: StateFlow<Float> = userPreferencesRepository.animatedLyricsBlurStrengthFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 2.5f
        )

    fun toggleCoverLyrics() {
        viewModelScope.launch {
            val nextState = !coverLyricsEnabled.value
            userPreferencesRepository.setCoverLyricsEnabled(nextState)
            if (nextState && stablePlayerState.value.lyrics == null && !stablePlayerState.value.isLoadingLyrics) {
                fetchLyricsForCurrentSong(forcePickResults = false)
            }
        }
    }

    fun setCoverLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCoverLyricsEnabled(enabled)
            if (enabled && stablePlayerState.value.lyrics == null && !stablePlayerState.value.isLoadingLyrics) {
                fetchLyricsForCurrentSong(forcePickResults = false)
            }
        }
    }



    private val _isInitialThemePreloadComplete = MutableStateFlow(false)

    val isEndOfTrackTimerActive: StateFlow<Boolean> = sleepTimerStateHolder.isEndOfTrackTimerActive
    val activeTimerValueDisplay: StateFlow<String?> = sleepTimerStateHolder.activeTimerValueDisplay
    val activeTimerDurationMinutes: StateFlow<Int?> = sleepTimerStateHolder.activeTimerDurationMinutes
    val playCount: StateFlow<Float> = sleepTimerStateHolder.playCount

    // Lyrics search UI state - managed by LyricsStateHolder
    val lyricsSearchUiState: StateFlow<LyricsSearchUiState> = lyricsStateHolder.searchUiState




    // Toast Events
    private val _toastEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val toastEvents = _toastEvents.asSharedFlow()

    // MediaStore write-permission request (needed for metadata editing without MANAGE_EXTERNAL_STORAGE).
    // Owned by MetadataEditStateHolder (the only producer/consumer); re-exposed here for the UI.
    val writePermissionRequest: SharedFlow<android.content.IntentSender> = metadataEditStateHolder.writePermissionRequest

    // MediaStore delete-permission request (for deletion without MANAGE_EXTERNAL_STORAGE).
    // Owned by SongRemovalStateHolder (the only producer/consumer); re-exposed here for the UI.
    val deletePermissionRequest: SharedFlow<android.content.IntentSender> = songRemovalStateHolder.deletePermissionRequest

    private val _albumNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val albumNavigationRequests = _albumNavigationRequests.asSharedFlow()
    private val _artistNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val artistNavigationRequests = _artistNavigationRequests.asSharedFlow()

    /** Artist profile requests by *name*, for online artists that have no local library id. */
    private val _artistNameNavigationRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val artistNameNavigationRequests = _artistNameNavigationRequests.asSharedFlow()
    private val _lyricsOpenRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lyricsOpenRequests = _lyricsOpenRequests.asSharedFlow()
    private val _searchNavDoubleTapEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val searchNavDoubleTapEvents = _searchNavDoubleTapEvents.asSharedFlow()
    
    // New event for scrolling to a specific index in the songs list
    private val _scrollToIndexEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToIndexEvent = _scrollToIndexEvent.asSharedFlow()
    
    private var albumNavigationJob: Job? = null
    private var artistNavigationJob: Job? = null

    fun requestLocateCurrentSong() {
        val currentSong = stablePlayerState.value.currentSong ?: return

        viewModelScope.launch {
            try {
                val sortOption = playerUiState.value.currentSongSortOption
                
                // Logic must match effectiveStorageFilter in LibraryStateHolder
                val baseFilter = playerUiState.value.currentStorageFilter
                val hideLocal = playerUiState.value.hideLocalMedia
                val storageFilter = if (hideLocal) {
                    com.theveloper.pixelplay.data.model.StorageFilter.ONLINE
                } else {
                    baseFilter
                }

                val sortedIds = musicRepository.getSongIdsSorted(sortOption, storageFilter)

                val unifiedId = currentSong.id.toLongOrNull()
                    ?: currentSong.contentUriString
                        .takeIf { it.isNotBlank() }
                        ?.let { musicRepository.getSongIdByContentUri(it) }

                val index = unifiedId?.let { sortedIds.indexOf(it) } ?: -1

                if (index != -1) {
                    _scrollToIndexEvent.emit(index)
                } else {
                    sendToast(context.getString(R.string.player_view_model_song_not_found_in_list))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to locate current song")
                sendToast(context.getString(R.string.player_view_model_could_not_locate_song))
            }
        }
    }

    fun requestLocateFavoriteSong() {
        val currentSong = stablePlayerState.value.currentSong ?: return

        viewModelScope.launch {
            try {
                val sortOption = playerUiState.value.currentFavoriteSortOption

                val baseFilter = playerUiState.value.currentStorageFilter
                val hideLocal = playerUiState.value.hideLocalMedia
                val storageFilter = if (hideLocal) {
                    com.theveloper.pixelplay.data.model.StorageFilter.ONLINE
                } else {
                    baseFilter
                }

                val sortedIds = musicRepository.getFavoriteSongIdsSorted(sortOption, storageFilter)

                val unifiedId = currentSong.id.toLongOrNull()
                    ?: currentSong.contentUriString
                        .takeIf { it.isNotBlank() }
                        ?.let { musicRepository.getSongIdByContentUri(it) }

                val index = unifiedId?.let { sortedIds.indexOf(it) } ?: -1

                if (index != -1) {
                    _scrollToIndexEvent.emit(index)
                } else {
                    sendToast(context.getString(R.string.player_view_model_song_not_found_in_list))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to locate favorite song")
                sendToast(context.getString(R.string.player_view_model_could_not_locate_song))
            }
        }
    }

    fun showAndPlaySongFromLibrary(
        song: Song,
        queueName: String = "Library",
        isVoluntaryPlay: Boolean = true
    ) = playbackDispatchStateHolder.showAndPlaySongFromLibrary(song, queueName, isVoluntaryPlay)

    fun showAndPlaySongFromFavorites(
        song: Song,
        queueName: String = "Liked Songs",
        isVoluntaryPlay: Boolean = true
    ) = playbackDispatchStateHolder.showAndPlaySongFromFavorites(song, queueName, isVoluntaryPlay)

    suspend fun getSongsForCurrentLibrarySelection(): List<Song> =
        playbackDispatchStateHolder.getSongsForCurrentLibrarySelection()

    suspend fun getSongsForCurrentFavoriteSelection(): List<Song> =
        playbackDispatchStateHolder.getSongsForCurrentFavoriteSelection()

    val castRoutes: StateFlow<List<MediaRouter.RouteInfo>> = castStateHolder.castRoutes
    val selectedRoute: StateFlow<MediaRouter.RouteInfo?> = castStateHolder.selectedRoute
    /** Pre-mapped so UI composables don't create a new Flow on every recomposition. */
    val selectedRouteName: StateFlow<String?> = castStateHolder.selectedRoute
        .map { it?.name }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val routeVolume: StateFlow<Int> = castStateHolder.routeVolume
    val isRefreshingRoutes: StateFlow<Boolean> = castStateHolder.isRefreshingRoutes

    // Connectivity state delegated to ConnectivityStateHolder
    val isWifiEnabled: StateFlow<Boolean> = connectivityStateHolder.isWifiEnabled
    val isWifiRadioOn: StateFlow<Boolean> = connectivityStateHolder.isWifiRadioOn
    val wifiName: StateFlow<String?> = connectivityStateHolder.wifiName
    val isBluetoothEnabled: StateFlow<Boolean> = connectivityStateHolder.isBluetoothEnabled
    val bluetoothName: StateFlow<String?> = connectivityStateHolder.bluetoothName
    val bluetoothAudioDeviceStates: StateFlow<List<BluetoothAudioDeviceState>> = connectivityStateHolder.bluetoothAudioDeviceStates
    val bluetoothAudioDevices: StateFlow<List<String>> = connectivityStateHolder.bluetoothAudioDevices



    // Connectivity is now managed by ConnectivityStateHolder

    // Cast state is now managed by CastStateHolder
    private val sessionManager: SessionManager? get() = castStateHolder.sessionManager

    val isRemotePlaybackActive: StateFlow<Boolean> = castStateHolder.isRemotePlaybackActive
    val isCastConnecting: StateFlow<Boolean> = castStateHolder.isCastConnecting
    val remotePosition: StateFlow<Long> = castStateHolder.remotePosition

    private val _trackVolume = MutableStateFlow(1.0f)
    val trackVolume: StateFlow<Float> = _trackVolume.asStateFlow()

    init {
        // Initialize helper classes with our coroutine scope
        listeningStatsTracker.initialize(viewModelScope)
        dailyMixStateHolder.initialize(viewModelScope)
        lyricsStateHolder.initialize(viewModelScope, lyricsLoadCallback, playbackStateHolder.stablePlayerState)
        playbackStateHolder.initialize(
            coroutineScope = viewModelScope,
            onCastSeekBlocked = {
                sendToast(context.getString(R.string.cast_seek_unavailable_for_format))
            }
        )
        themeStateHolder.initialize(viewModelScope)
        playbackDispatchStateHolder.initialize(playbackDispatchCallbacks())
        mediaControllerSyncStateHolder.initialize(controllerSyncCallbacks())

        // On cold start, the MediaController connects asynchronously, leaving stablePlayerState.currentSong
        // null until that happens. Pre-load the palette from the persisted snapshot so the mini player
        // has the correct colors immediately on first render, before the controller is ready.
        viewModelScope.launch {
            val snapshot = runCatching {
                userPreferencesRepository.getPlaybackQueueSnapshotOnce()
            }.getOrNull() ?: return@launch

            val currentItem = if (snapshot.currentMediaId != null) {
                snapshot.items.find { it.mediaId == snapshot.currentMediaId }
            } else {
                snapshot.items.getOrNull(snapshot.currentIndex)
            } ?: return@launch

            val artworkUri = currentItem.artworkUri?.takeIf { it.isNotBlank() } ?: return@launch

            themeStateHolder.extractAndGenerateColorScheme(
                albumArtUriAsUri = artworkUri.toUri(),
                currentSongUriString = artworkUri,
                isPreload = false
            )
        }

        stablePlayerState
            .map { it.currentSong?.albumArtUriString?.takeIf { uri -> uri.isNotBlank() } }
            .distinctUntilChanged()
            // mapLatest cancels in-flight extraction for songs that are skipped over during a
            // rapid next/previous burst, so only the latest song's palette is computed. Combined
            // with the neighbor preloading below, the latest song is usually already a cache hit,
            // so the color resolves immediately instead of after a backlog of intermediate songs.
            .mapLatest { artworkUri ->
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = artworkUri?.toUri(),
                    currentSongUriString = artworkUri,
                    isPreload = false
                )
            }
            .launchIn(viewModelScope)

        // Preload neighbor album-art palettes so a skip lands on an already-cached color scheme
        // (instant memory-cache hit) and the color animation starts in step with the carousel
        // instead of trailing it. ensureAlbumColorScheme runs off-thread (IO -> Default) and
        // dedups in-flight work, so this adds no main-thread cost. Bounded to ±radius neighbors.
        combine(
            stablePlayerState.map { it.currentMediaItemIndex }.distinctUntilChanged(),
            queueFlow
        ) { index, queue -> index to queue }
            // Collapse rapid skip bursts: mapLatest cancels the pending delay whenever the index
            // changes again within the window, so we only quantize neighbor palettes once the user
            // settles on a song — never for every intermediate song flicked past. Keeps the heavy
            // Celebi work off the critical path during a burst.
            .mapLatest { pair ->
                kotlinx.coroutines.delay(220)
                pair
            }
            .onEach { (index, queue) ->
                if (index !in queue.indices) return@onEach
                val radius = 1
                for (offset in -radius..radius) {
                    if (offset == 0) continue
                    queue.getOrNull(index + offset)
                        ?.albumArtUriString
                        ?.takeIf { it.isNotBlank() }
                        ?.let { themeStateHolder.ensureAlbumColorScheme(it) }
                }
            }
            .launchIn(viewModelScope)

        // Prefetch lyrics (synced first) for the next few songs in the queue once the user
        // settles on a track, so every upcoming song already has synced lines when it starts.
        combine(
            stablePlayerState.map { it.currentMediaItemIndex }.distinctUntilChanged(),
            queueFlow
        ) { index, queue -> index to queue }
            .mapLatest { pair ->
                kotlinx.coroutines.delay(LYRICS_PREFETCH_SETTLE_MS)
                pair
            }
            .onEach { (index, queue) ->
                if (index !in queue.indices) return@onEach
                val upcoming = queue.subList(index + 1, minOf(queue.size, index + 1 + LYRICS_PREFETCH_AHEAD))
                lyricsStateHolder.prefetchUpcoming(upcoming.toList())
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            lyricsStateHolder.songUpdates.collect { update: Pair<com.theveloper.pixelplay.data.model.Song, com.theveloper.pixelplay.data.model.Lyrics?> ->
                val song = update.first
                val lyrics = update.second
                // Check if this update is relevant to the currently playing song OR the selected song
                if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                    // MERGE FIX: if song comes back empty (e.g. from reset), preserve current metadata
                    val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                    val safeSong = if (song.title.isEmpty() && currentSong != null) {
                        currentSong.copy(lyrics = "")
                    } else {
                        song
                    }
                    updateSongInStates(safeSong, lyrics)
                }
                if (_selectedSongForInfo.value?.id == song.id) {
                    val currentSelected = _selectedSongForInfo.value
                    if (song.title.isEmpty() && currentSelected != null) {
                        _selectedSongForInfo.value = currentSelected.copy(lyrics = "")
                    } else {
                        _selectedSongForInfo.value = song
                    }
                }
            }
        }

        lyricsStateHolder.messageEvents
            .onEach { msg: String -> _toastEvents.emit(msg) }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .flatMapLatest { songId ->
                    if (songId.isNullOrBlank()) flowOf(null)
                    else musicRepository.getSong(songId)
                }
                .collect { repositorySong ->
                    val currentState = playbackStateHolder.stablePlayerState.value
                    val currentSong = currentState.currentSong ?: return@collect
                    if (repositorySong == null || repositorySong.id != currentSong.id) {
                        return@collect
                    }

                    val hydratedSong = currentSong.withRepositoryHydration(repositorySong)
                    val persistedLyrics = parsePersistedLyrics(hydratedSong.lyrics)
                    val persistedAreSynced = !persistedLyrics?.synced.isNullOrEmpty()
                    // Plain lyrics from the file tag must not cancel an in-flight load: the
                    // repository may already hold a synced version for this song.
                    val shouldApplyPersistedLyrics = currentState.lyrics == null &&
                        persistedLyrics != null &&
                        (persistedAreSynced || !currentState.isLoadingLyrics)
                    val shouldRefreshSong = hydratedSong != currentSong
                    val shouldReloadLyrics =
                        !shouldApplyPersistedLyrics &&
                            currentState.lyrics == null &&
                            hydratedSong.improvesLyricsLookupComparedTo(currentSong)

                    if (shouldApplyPersistedLyrics || shouldReloadLyrics) {
                        lyricsStateHolder.cancelLoading()
                    }

                    if (shouldRefreshSong || shouldApplyPersistedLyrics) {
                        updateSongInStates(
                            updatedSong = hydratedSong,
                            newLyrics = if (shouldApplyPersistedLyrics) persistedLyrics else null,
                            isLoadingLyrics = if (shouldApplyPersistedLyrics) false else null
                        )

                        if (_selectedSongForInfo.value?.id == hydratedSong.id) {
                            _selectedSongForInfo.value = hydratedSong
                        }
                    }

                    if (shouldReloadLyrics) {
                        lyricsStateHolder.loadLyricsForSong(hydratedSong, lyricsSourcePreference.value)
                    } else if (shouldApplyPersistedLyrics && !persistedAreSynced) {
                        lyricsStateHolder.upgradeInBackground(hydratedSong)
                    }
                }
        }
    }

    fun setTrackVolume(volume: Float) {
        mediaController?.let {
            val clampedVolume = volume.coerceIn(0f, 1f)
            it.volume = clampedVolume
            _trackVolume.value = clampedVolume
        }
    }

    fun sendToast(message: String) {
        viewModelScope.launch {
            _toastEvents.emit(message)
        }
    }

    /**
     * Bundles the ViewModel-owned state accessors that [MetadataEditStateHolder] needs to drive
     * UI updates for the metadata-edit cluster, without that holder depending on this ViewModel.
     */
    private fun metadataEditCallbacks() = MetadataEditCallbacks(
        scope = viewModelScope,
        getUiState = { _playerUiState.value },
        updateUiState = { mutation -> _playerUiState.update(mutation) },
        getSelectedSongForInfo = { _selectedSongForInfo.value },
        setSelectedSongForInfo = { _selectedSongForInfo.value = it },
        sendToast = ::sendToast,
        reloadLyricsForCurrentSong = ::loadLyricsForCurrentSong,
    )

    /**
     * Bundles the ViewModel-owned collaborators that [SongRemovalStateHolder]'s device-deletion
     * entry points need (toasts, media-controller queue cleanup, and the full library+player
     * removal routine), without that holder depending on this ViewModel.
     */
    private fun songRemovalCallbacks() = SongRemovalCallbacks(
        scope = viewModelScope,
        sendToast = ::sendToast,
        removeFromMediaControllerQueue = ::removeFromMediaControllerQueue,
        removeSong = ::removeSong,
    )

    /**
     * Bundles the ViewModel-owned collaborators that [QueueStateHolder]'s shuffle entry points
     * need (source resolution + shuffled-playback dispatch), without that holder depending on
     * this ViewModel.
     */
    private fun shufflePlaybackCallbacks() = ShufflePlaybackCallbacks(
        scope = viewModelScope,
        currentStorageFilter = { playerUiState.value.currentStorageFilter },
        albums = { libraryStateHolder.albums.value },
        artists = { libraryStateHolder.artists.value },
        playShuffled = { songs, queueName -> playSongsShuffled(songs, queueName, startAtZero = true) },
    )

    /**
     * Bundles the ViewModel collaborators that [QueueStateHolder]'s album/artist play entry
     * points need to dispatch sequential playback and reveal the player sheet.
     */
    private fun playbackSourceCallbacks() = PlaybackSourceCallbacks(
        scope = viewModelScope,
        playSongs = { songs, startSong, queueName, playlistId ->
            playSongs(songs, startSong, queueName, playlistId)
        },
        showSheet = { _isSheetVisible.value = true },
    )

    /**
     * Bundles the ViewModel-owned collaborators that [PlaybackDispatchStateHolder] needs
     * (media controller, UI state, player sheet, toasts/dialog events, the crossfade
     * transition job, listening stats, predictive back), without that holder depending on
     * this ViewModel. Supplied once via its initialize().
     */
    private fun playbackDispatchCallbacks() = PlaybackDispatchCallbacks(
        scope = viewModelScope,
        getController = { mediaController },
        getUiState = { _playerUiState.value },
        updateUiState = { mutation -> _playerUiState.update(mutation) },
        showSheet = { _isSheetVisible.value = true },
        collapseSheetState = { _sheetState.value = PlayerSheetState.COLLAPSED },
        showPlayer = ::showPlayer,
        sendToast = ::sendToast,
        emitToast = { _toastEvents.emit(it) },
        showNoInternetDialog = { _showNoInternetDialog.tryEmit(Unit) },
        cancelTransitionScheduler = { mediaControllerSyncStateHolder.cancelTransitionScheduler() },
        incrementSongScore = ::incrementSongScore,
        resetPredictiveBackState = ::resetPredictiveBackState,
    )

    /**
     * Bundles the ViewModel-owned collaborators that [MediaControllerSyncStateHolder] needs
     * (media controller, UI state, player sheet, track volume, toasts/dialog events, lyrics
     * loading, EOT sleep-timer cancel, manual shuffle), without that holder depending on
     * this ViewModel. Supplied once via its initialize().
     */
    private fun controllerSyncCallbacks() = ControllerSyncCallbacks(
        scope = viewModelScope,
        getController = { mediaController },
        getUiState = { _playerUiState.value },
        updateUiState = { mutation -> _playerUiState.update(mutation) },
        showSheet = { _isSheetVisible.value = true },
        setTrackVolume = { _trackVolume.value = it },
        emitToast = { _toastEvents.emit(it) },
        showNoInternetDialog = { _showNoInternetDialog.emit(Unit) },
        cancelSleepTimerForEot = { cancelSleepTimer(suppressDefaultToast = true) },
        resetLyricsSearchState = ::resetLyricsSearchState,
        loadLyricsForCurrentSong = ::loadLyricsForCurrentSong,
        toggleShuffle = { toggleShuffle() },
    )

    /**
     * Bundles the ViewModel-owned collaborators that [MultiSelectionStateHolder]'s batch
     * actions need (queue dispatch, player sheet, toasts, favorites snapshot), without that
     * holder depending on this ViewModel.
     */
    private fun selectionActionCallbacks() = SelectionActionCallbacks(
        scope = viewModelScope,
        playSongs = { songs, startSong, queueName -> playSongs(songs, startSong, queueName) },
        addSongToQueue = ::addSongToQueue,
        addSongNextToQueue = ::addSongNextToQueue,
        showSheet = { _isSheetVisible.value = true },
        emitToast = { _toastEvents.emit(it) },
        favoriteSongIds = { favoriteSongIds.value },
    )

    fun onSearchNavIconDoubleTapped() {
        _searchNavDoubleTapEvents.tryEmit(Unit)
    }


    // Last Library Tab Index
    val lastLibraryTabIndexFlow: StateFlow<Int> =
        userPreferencesRepository.lastLibraryTabIndexFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0 // Default to Songs tab
        )

    val libraryTabsFlow: StateFlow<List<String>> = userPreferencesRepository.libraryTabsOrderFlow
        .map { orderJson ->
            val storedOrder = orderJson?.let {
                runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull()
            } ?: emptyList()
            normalizeLibraryTabs(storedOrder)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_LIBRARY_TABS)

    private val _loadedTabs = MutableStateFlow(emptySet<String>())
    private var lastBlockedDirectories: Set<String>? = null

    private val _currentLibraryTabId = MutableStateFlow(LibraryTabId.SONGS)
    val currentLibraryTabId: StateFlow<LibraryTabId> = _currentLibraryTabId.asStateFlow()

    private val _isSortingSheetVisible = MutableStateFlow(false)
    val isSortingSheetVisible: StateFlow<Boolean> = _isSortingSheetVisible.asStateFlow()

    val availableSortOptions: StateFlow<List<SortOption>> =
        currentLibraryTabId.map { tabId ->
            Trace.beginSection("PlayerViewModel.availableSortOptionsMapping")
            try {
                when (tabId) {
                    LibraryTabId.SONGS -> SortOption.SONGS
                    LibraryTabId.ALBUMS -> SortOption.ALBUMS
                    LibraryTabId.ARTISTS -> SortOption.ARTISTS
                    LibraryTabId.PLAYLISTS -> SortOption.PLAYLISTS
                    LibraryTabId.FOLDERS -> SortOption.FOLDERS
                    LibraryTabId.LIKED -> SortOption.LIKED
                    // The Genres tab sorts inside the tab (by listening, A–Z, songs).
                    LibraryTabId.GENRES -> emptyList()
                }
            } finally {
                Trace.endSection()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SortOption.SONGS
        )

    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _isInitialDataLoaded = MutableStateFlow(false)

    // Public read-only access to all songs (using _masterAllSongs declared at class level)
    // Library State - delegated to LibraryStateHolder
    val allSongsFlow: StateFlow<ImmutableList<Song>> = libraryStateHolder.allSongs

    // Genres StateFlow - delegated to LibraryStateHolder
    val genres: StateFlow<ImmutableList<Genre>> = libraryStateHolder.genres
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val paletteRegenerationTargets: StateFlow<List<Song>> = musicRepository.getDistinctAlbumArtSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val homeMixPreviewSongs: StateFlow<ImmutableList<Song>> = musicRepository.getHomeMixPreviewSongs(
        limit = HOME_MIX_PREVIEW_LIMIT
    ).map { it.toImmutableList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val songCountFlow: StateFlow<Int> = musicRepository.getSongCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val hasCloudSongsFlow: StateFlow<Boolean?> = musicRepository.getCloudSongCountFlow()
        .map<Int, Boolean?> { it > 0 }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val albumsFlow: StateFlow<ImmutableList<Album>> = libraryStateHolder.albums
    val artistsFlow: StateFlow<ImmutableList<Artist>> = libraryStateHolder.artists

    var searchQuery by mutableStateOf("")
        private set

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    private var mediaController: MediaController? = null
    private val _isMediaControllerReady = MutableStateFlow(false)
    val isMediaControllerReady: StateFlow<Boolean> = _isMediaControllerReady.asStateFlow()
    // SessionToken injected via constructor
    private val mediaControllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (command.customAction == MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE) {
                val enabled = args.getBoolean(
                    MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED,
                    false
                )
                viewModelScope.launch {
                    if (enabled != playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        toggleShuffle()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }
    private val mediaControllerFuture: ListenableFuture<MediaController> =
        mediaControllerFactory.create(context, sessionToken, mediaControllerListener)
    val playbackAudioMetadata: StateFlow<PlaybackAudioMetadata> =
        mediaControllerSyncStateHolder.playbackAudioMetadata

    val favoriteSongIds: StateFlow<Set<String>> = musicRepository
        .getFavoriteSongIdsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isCurrentSongFavorite: StateFlow<Boolean> = combine(
        stablePlayerState
            .map { it.currentSong }
            .distinctUntilChanged { old, new ->
                old?.id == new?.id &&
                    old?.contentUriString == new?.contentUriString &&
                    old?.path == new?.path
            }
            .flatMapLatest { song ->
                kotlinx.coroutines.flow.flow {
                    emit(resolveFavoriteSongId(song))
                }
            },
        favoriteSongIds
    ) { favoriteSongId, ids ->
        favoriteSongId?.let { ids.contains(it) } ?: false
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ---------------------------------------------------------------------------
    // FullPlayerSlice — consolidates 11 independent flows into ONE subscription.
    // Previously FullPlayerContent had ~13 separate collectAsStateWithLifecycle()
    // calls. Each emission from any of them caused a recompose of the entire 2k-line
    // composable. Now a single collect + distinctUntilChanged batches all settings.
    // ---------------------------------------------------------------------------
    data class FullPlayerSlice(
        val currentSongArtists: List<Artist> = emptyList(),
        val lyricsSyncOffset: Int = 0,
        val albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM,
        val audioMetadata: PlaybackAudioMetadata = PlaybackAudioMetadata(),
        val showPlayerFileInfo: Boolean = true,
        val immersiveLyricsEnabled: Boolean = false,
        val immersiveLyricsTimeout: Long = 4000L,
        val isImmersiveTemporarilyDisabled: Boolean = false,
        val isRemotePlaybackActive: Boolean = false,
        val selectedRouteName: String? = null,
        val isBluetoothEnabled: Boolean = false,
        val bluetoothName: String? = null
    )

    // Intermediate combine #1: 5 settings flows
    private val fullPlayerSlicePart1 = combine(
        currentSongArtists,
        currentSongLyricsSyncOffset,
        albumArtQuality,
        playbackAudioMetadata,
        showPlayerFileInfo
    ) { artists: List<Artist>, syncOffset: Int, artQuality: AlbumArtQuality,
        audioMeta: PlaybackAudioMetadata, showFileInfo: Boolean ->
        FullPlayerSlicePart1(artists, syncOffset, artQuality, audioMeta, showFileInfo)
    }

    private data class BluetoothSlice(val enabled: Boolean, val name: String?)

    private val bluetoothSlice = combine(isBluetoothEnabled, bluetoothName) { bt, btName ->
        BluetoothSlice(bt, btName)
    }

    // Intermediate combine #2: remaining flows (≤5 for Kotlin type inference)
    private val fullPlayerSlicePart2 = combine(
        immersiveLyricsEnabled,
        immersiveLyricsTimeout,
        isImmersiveTemporarilyDisabled,
        isRemotePlaybackActive,
        combine(selectedRouteName, bluetoothSlice) { route, bt -> route to bt }
    ) { immersive: Boolean, immersiveTimeout: Long, immersiveDisabled: Boolean,
        remotePb: Boolean, routeAndBt: Pair<String?, BluetoothSlice> ->
        val (routeName, bt) = routeAndBt
        FullPlayerSlicePart2(immersive, immersiveTimeout, immersiveDisabled, remotePb, routeName, bt.enabled, bt.name)
    }

    private data class FullPlayerSlicePart1(
        val currentSongArtists: List<Artist>,
        val lyricsSyncOffset: Int,
        val albumArtQuality: AlbumArtQuality,
        val audioMetadata: PlaybackAudioMetadata,
        val showPlayerFileInfo: Boolean
    )

    private data class FullPlayerSlicePart2(
        val immersiveLyricsEnabled: Boolean,
        val immersiveLyricsTimeout: Long,
        val isImmersiveTemporarilyDisabled: Boolean,
        val isRemotePlaybackActive: Boolean,
        val selectedRouteName: String?,
        val isBluetoothEnabled: Boolean,
        val bluetoothName: String?
    )

    val fullPlayerSlice: StateFlow<FullPlayerSlice> = combine(
        fullPlayerSlicePart1,
        fullPlayerSlicePart2
    ) { p1, p2 ->
        FullPlayerSlice(
            currentSongArtists = p1.currentSongArtists,
            lyricsSyncOffset = p1.lyricsSyncOffset,
            albumArtQuality = p1.albumArtQuality,
            audioMetadata = p1.audioMetadata,
            showPlayerFileInfo = p1.showPlayerFileInfo,
            immersiveLyricsEnabled = p2.immersiveLyricsEnabled,
            immersiveLyricsTimeout = p2.immersiveLyricsTimeout,
            isImmersiveTemporarilyDisabled = p2.isImmersiveTemporarilyDisabled,
            isRemotePlaybackActive = p2.isRemotePlaybackActive,
            selectedRouteName = p2.selectedRouteName,
            isBluetoothEnabled = p2.isBluetoothEnabled,
            bluetoothName = p2.bluetoothName
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FullPlayerSlice())

    // ---------------------------------------------------------------------------
    // PlayerConfigSlice — consolidates 7 infrequently-changing preference flows
    // into ONE subscription. Previously the player sheet had 7 separate
    // collectAsStateWithLifecycle() calls for config values, each causing a full
    // sheet recomposition when any preference changed.
    // ---------------------------------------------------------------------------
    data class PlayerConfigSlice(
        val navBarCornerRadius: Int = 32,
        val navBarStyle: String = NavBarStyle.DEFAULT,
        val carouselStyle: String = CarouselStyle.NO_PEEK,
        val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
        val tapBackgroundClosesPlayer: Boolean = false,
        val useSmoothCorners: Boolean = true,
        val playerThemePreference: String = ThemePreference.ALBUM_ART,
        val miniPlayerSongTransition: Boolean = true
    )

    private val playerConfigSlicePart1 = combine(
        navBarCornerRadius,
        navBarStyle,
        carouselStyle,
        fullPlayerLoadingTweaks,
        tapBackgroundClosesPlayer
    ) { radius, style, carousel, tweaks, tapClose ->
        PlayerConfigSlicePart1(radius, style, carousel, tweaks, tapClose)
    }

    private data class PlayerConfigSlicePart1(
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val carouselStyle: String,
        val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
        val tapBackgroundClosesPlayer: Boolean
    )

    val playerConfigSlice: StateFlow<PlayerConfigSlice> = combine(
        playerConfigSlicePart1,
        useSmoothCorners,
        playerThemePreference,
        miniPlayerSongTransition
    ) { p1, smoothCorners, themePref, miniSongTransition ->
        PlayerConfigSlice(
            navBarCornerRadius = p1.navBarCornerRadius,
            navBarStyle = p1.navBarStyle,
            carouselStyle = p1.carouselStyle,
            fullPlayerLoadingTweaks = p1.fullPlayerLoadingTweaks,
            tapBackgroundClosesPlayer = p1.tapBackgroundClosesPlayer,
            useSmoothCorners = smoothCorners,
            playerThemePreference = themePref,
            miniPlayerSongTransition = miniSongTransition
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerConfigSlice())

    // Daily mix state is now managed by DailyMixStateHolder
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.dailyMixSongs
    val yourMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.yourMixSongs

    fun removeFromDailyMix(songId: String) {
        dailyMixStateHolder.removeFromDailyMix(songId)
    }

    /**
     * Observes a song by ID from Room DB, combined with the latest favorite status.
     * Uses direct Room query instead of scanning the full in-memory list.
     */
    fun observeSong(songId: String?): Flow<Song?> {
        if (songId == null) return flowOf(null)
        return combine(
            musicRepository.getSong(songId),
            favoriteSongIds
        ) { song, favorites ->
            song?.copy(isFavorite = favorites.contains(songId))
        }.distinctUntilChanged()
    }



    private fun updateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.updateDailyMix(
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    fun shuffleAllSongs(queueName: String = "All Songs (Shuffled)") =
        queueStateHolder.shuffleAll(queueName, shufflePlaybackCallbacks())

    /**
     * Called from Quick Settings tile. Unlike shuffleAllSongs(), this always starts
     * fresh playback regardless of current state, and correctly handles the case
     * where the MediaController isn't ready yet (cold start from tile).
     *
     * Queries a bounded random sample directly from the repository so the tile does
     * not depend on the eager in-memory song cache being populated first.
     */
    fun triggerShuffleAllFromTile() = playbackDispatchStateHolder.triggerShuffleAllFromTile()

    fun playRandomSong() =
        queueStateHolder.playRandom(shufflePlaybackCallbacks())

    fun shuffleFavoriteSongs() =
        queueStateHolder.shuffleFavorites(shufflePlaybackCallbacks())

    fun shuffleRandomAlbum() =
        queueStateHolder.shuffleRandomAlbum(shufflePlaybackCallbacks())

    fun shuffleRandomArtist() =
        queueStateHolder.shuffleRandomArtist(shufflePlaybackCallbacks())


    private fun loadPersistedDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.loadPersistedDailyMix()
    }

    fun forceUpdateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.forceUpdate(
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    private var castSongUiSyncJob: Job? = null
    private var lastCastSongUiSyncedId: String? = null

    private fun incrementSongScore(song: Song) {
        listeningStatsTracker.onVoluntarySelection(song.id)
    }

    // MIN_SESSION_LISTEN_MS, currentSession, and ListeningStatsTracker class
    // have been moved to ListeningStatsTracker.kt for better modularity


    fun updatePredictiveBackCollapseFraction(fraction: Float) {
        _predictiveBackCollapseFraction.value = fraction.coerceIn(0f, 1f)
    }

    fun updatePredictiveBackSwipeEdge(edge: Int?) {
        _predictiveBackSwipeEdge.value = edge
    }

    fun resetPredictiveBackState() {
        _predictiveBackCollapseFraction.value = 0f
        _predictiveBackSwipeEdge.value = null
    }

    fun updateQueueSheetVisibility(visible: Boolean) {
        _isQueueSheetVisible.value = visible
        if (!visible && _bottomSheetState.value is BottomSheetState.Queue) {
            _bottomSheetState.value = BottomSheetState.Hidden
        } else if (visible && _bottomSheetState.value !is BottomSheetState.Queue) {
            _bottomSheetState.value = BottomSheetState.Queue
        }
    }

    fun updateCastSheetVisibility(visible: Boolean) {
        _isCastSheetVisible.value = visible
        if (!visible && _bottomSheetState.value is BottomSheetState.AudioOutput) {
            _bottomSheetState.value = BottomSheetState.Hidden
        } else if (visible && _bottomSheetState.value !is BottomSheetState.AudioOutput) {
            _bottomSheetState.value = BottomSheetState.AudioOutput
        }
    }

    // Helper to resolve stored sort keys against the allowed group
    private fun resolveSortOption(
        optionKey: String?,
        allowed: Collection<SortOption>,
        fallback: SortOption
    ): SortOption {
        return SortOption.fromStorageKey(optionKey, allowed, fallback)
    }

    private data class FolderSourceState(
        val source: FolderSource,
        val rootPath: String,
        val isSdCardAvailable: Boolean
    )

    private fun resolveFolderSourceState(preferredSource: FolderSource): FolderSourceState {
        val storages = StorageUtils.getAvailableStorages(context)
        val internalPath = storages
            .firstOrNull { it.storageType == StorageType.INTERNAL }
            ?.path
            ?.path
            ?: android.os.Environment.getExternalStorageDirectory().path
        val sdPath = StorageUtils.getSdCardStorage(context)
            ?.path
            ?.path

        val effectiveSource = if (!ENABLE_FOLDERS_SOURCE_SWITCHING) {
            FolderSource.INTERNAL
        } else if (preferredSource == FolderSource.SD_CARD && sdPath == null) {
            FolderSource.INTERNAL
        } else {
            preferredSource
        }

        val resolvedRootPath = if (effectiveSource == FolderSource.SD_CARD) sdPath!! else internalPath
        return FolderSourceState(
            source = effectiveSource,
            rootPath = resolvedRootPath,
            isSdCardAvailable = sdPath != null
        )
    }

    // Connectivity refresh delegated to ConnectivityStateHolder
    fun refreshLocalConnectionInfo(refreshBluetoothDevices: Boolean = false) {
        connectivityStateHolder.refreshLocalConnectionInfo(refreshBluetoothDevices)
    }

    init {
        Log.i("PlayerViewModel", "init started.")

        // Cast initialization if already connected
        val currentSession = sessionManager?.currentCastSession
        if (currentSession != null) {
            castStateHolder.setCastPlayer(CastPlayer(currentSession, context.contentResolver, castTokenStore))
            castStateHolder.setRemotePlaybackActive(true)
        }



        viewModelScope.launch {
            userPreferencesRepository.migrateTabOrder()
        }

        viewModelScope.launch {
            userPreferencesRepository.ensureLibrarySortDefaults()
        }

        viewModelScope.launch {
            val legacyFavoriteIds = userPreferencesRepository.favoriteSongIdsFlow.first()
            if (legacyFavoriteIds.isNotEmpty()) {
                val roomFavoriteIds = musicRepository.getFavoriteSongIdsOnce()
                if (roomFavoriteIds.isEmpty()) {
                    legacyFavoriteIds.forEach { songId ->
                        musicRepository.setFavoriteStatus(songId, true)
                    }
                }
                userPreferencesRepository.clearFavoriteSongIds()
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.isFoldersPlaylistViewFlow.collect { isPlaylistView ->
                folderNavigationStateHolder.setFoldersPlaylistViewState(
                    isPlaylistView = isPlaylistView,
                    updateUiState = { mutation -> _playerUiState.update(mutation) }
                )
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.foldersSourceFlow.collect { preferredSource ->
                val resolved = resolveFolderSourceState(preferredSource)
                if (resolved.source != preferredSource) {
                    userPreferencesRepository.setFoldersSource(resolved.source)
                }

                _playerUiState.update { currentState ->
                    val sourceChanged = currentState.folderSource != resolved.source ||
                            currentState.folderSourceRootPath != resolved.rootPath
                    currentState.copy(
                        folderSource = resolved.source,
                        folderSourceRootPath = resolved.rootPath,
                        isSdCardAvailable = resolved.isSdCardAvailable,
                        currentFolderPath = if (sourceChanged) null else currentState.currentFolderPath,
                        currentFolder = if (sourceChanged) null else currentState.currentFolder
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                userPreferencesRepository.folderBackGestureNavigationFlow,
                userPreferencesRepository.isAlbumsListViewFlow,
            ) { gestureNav, albumsList ->
                Pair(gestureNav, albumsList)
            }.collect { (gestureNav, albumsList) ->
                _playerUiState.update {
                    it.copy(
                        folderBackGestureNavigationEnabled = gestureNav,
                        isAlbumsListView = albumsList,
                    )
                }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.blockedDirectoriesFlow
                .distinctUntilChanged()
                .collect { blocked ->
                    if (lastBlockedDirectories == null) {
                        lastBlockedDirectories = blocked
                        return@collect
                    }

                    if (blocked != lastBlockedDirectories) {
                        lastBlockedDirectories = blocked
                        onBlockedDirectoriesChanged()
                    }
                }
        }

        viewModelScope.launch {
            combine(libraryTabsFlow, lastLibraryTabIndexFlow) { tabs, index ->
                tabs.getOrNull(index)?.toLibraryTabIdOrNull()
                    ?: tabs.firstOrNull()?.toLibraryTabIdOrNull()
                    ?: LibraryTabId.PLAYLISTS
            }.collect { tabId ->
                _currentLibraryTabId.value = tabId
            }
        }

        // Load initial sort options ONCE at startup.
        viewModelScope.launch {
            val initialSongSort = resolveSortOption(
                userPreferencesRepository.songsSortOptionFlow.first(),
                SortOption.SONGS,
                SortOption.SongTitleAZ
            )
            val initialAlbumSort = resolveSortOption(
                userPreferencesRepository.albumsSortOptionFlow.first(),
                SortOption.ALBUMS,
                SortOption.AlbumTitleAZ
            )
            val initialArtistSort = resolveSortOption(
                userPreferencesRepository.artistsSortOptionFlow.first(),
                SortOption.ARTISTS,
                SortOption.ArtistNameAZ
            )
            val initialFolderSort = resolveSortOption(
                userPreferencesRepository.foldersSortOptionFlow.first(),
                SortOption.FOLDERS,
                SortOption.FolderNameAZ
            )
            val initialLikedSort = resolveSortOption(
                userPreferencesRepository.likedSongsSortOptionFlow.first(),
                SortOption.LIKED,
                SortOption.LikedSongDateLiked
            )

            _playerUiState.update {
                it.copy(
                    currentSongSortOption = initialSongSort,
                    currentAlbumSortOption = initialAlbumSort,
                    currentArtistSortOption = initialArtistSort,
                    currentFolderSortOption = initialFolderSort,
                    currentFavoriteSortOption = initialLikedSort
                )
            }
            // Also update the dedicated flow for favorites to ensure consistency
            // _currentFavoriteSortOptionStateFlow.value = initialLikedSort // Delegated to LibraryStateHolder

            sortSongs(initialSongSort, persist = false)
            sortAlbums(initialAlbumSort, persist = false)
            sortArtists(initialArtistSort, persist = false)
            sortFolders(initialFolderSort, persist = false)
            sortFavoriteSongs(initialLikedSort, persist = false)
        }

        viewModelScope.launch {
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            if (isPersistent) {
                // If persistent shuffle is on, read the last used shuffle state (On/Off)
                val savedShuffle = userPreferencesRepository.isShuffleOnFlow.first()
                // Update the UI state so the shuffle button reflects the saved setting immediately
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = savedShuffle) }
            }
        }

        // launchColorSchemeProcessor() - Handled by ThemeStateHolder and on-demand calls

        loadPersistedDailyMix()
        loadSearchHistory()

        viewModelScope.launch {
            isSyncingStateFlow.collect { isSyncing ->
                val oldSyncingLibraryState = _playerUiState.value.isSyncingLibrary
                _playerUiState.update { it.copy(isSyncingLibrary = isSyncing) }

                if (oldSyncingLibraryState && !isSyncing) {
                    Log.i("PlayerViewModel", "Sync completed. Calling resetAndLoadInitialData from isSyncingStateFlow observer.")
                    resetAndLoadInitialData("isSyncingStateFlow observer")
                }
            }
        }

        viewModelScope.launch {
            if (!isSyncingStateFlow.value && !_isInitialDataLoaded.value && libraryStateHolder.allSongs.value.isEmpty()) {
                Log.i("PlayerViewModel", "Initial check: Sync not active and initial data not loaded. Calling resetAndLoadInitialData.")
                resetAndLoadInitialData("Initial Check")
            }
        }

        mediaControllerFuture.addListener({
            try {
                mediaController = mediaControllerFuture.get()
                // Pass controller to PlaybackStateHolder
                playbackStateHolder.setMediaController(mediaController)
                _isMediaControllerReady.value = true


                mediaControllerSyncStateHolder.setupMediaControllerListeners(mediaController)
                mediaControllerSyncStateHolder.flushPendingRepeatMode()
                syncShuffleStateWithSession(playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                // Execute any pending action that was queued while the controller was connecting
                playbackDispatchStateHolder.flushPendingPlaybackAction()
            } catch (e: Exception) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false, isLoadingLibraryCategories = false) }
                Log.e("PlayerViewModel", "Error setting up MediaController", e)
            }
        }, ContextCompat.getMainExecutor(context))


        // Start Cast discovery
        castStateHolder.startDiscovery()

        // Observe selection for HTTP server management
        viewModelScope.launch {
            castStateHolder.selectedRoute.collect { route ->
                if (route != null && !route.isDefault && route.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)) {
                    castTransferStateHolder.primeHttpServerStart()
                } else if (route?.isDefault == true) {
                    val hasActiveRemoteSession = castStateHolder.castSession.value?.remoteMediaClient != null ||
                            castStateHolder.isRemotePlaybackActive.value ||
                            castStateHolder.isCastConnecting.value
                    if (hasActiveRemoteSession) {
                        return@collect
                    }
                    context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                }
            }
        }

        // Initialize connectivity monitoring (WiFi/Bluetooth)
        connectivityStateHolder.initialize()

        // Initialize sleep timer state holder
        sleepTimerStateHolder.initialize(
            scope = viewModelScope,
            toastEmitter = { msg -> _toastEvents.emit(msg) },
            mediaControllerProvider = { mediaController },
            currentSongIdProvider = { stablePlayerState.map { it.currentSong?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null) },
            songTitleResolver = { songId -> libraryStateHolder.allSongsById.value[songId]?.title ?: "Unknown" }
        )

        // Initialize SearchStateHolder
        searchStateHolder.initialize(viewModelScope)

        // Collect SearchStateHolder flows
        viewModelScope.launch {
            combine(
                searchStateHolder.searchResults,
                searchStateHolder.selectedSearchFilter,
                searchStateHolder.searchHistory,
            ) { results, filter, history ->
                Triple(results, filter, history)
            }.collect { (results, filter, history) ->
                _playerUiState.update {
                    it.copy(
                        searchResults = results,
                        selectedSearchFilter = filter,
                        searchHistory = history,
                    )
                }
            }
        }

        // Initialize AiStateHolder
        aiStateHolder.initialize(
            scope = viewModelScope,
            allSongsProvider = { musicRepository.getAllSongsOnce() },
            favoriteSongIdsProvider = { favoriteSongIds.value },
            toastEmitter = { msg -> viewModelScope.launch { _toastEvents.emit(msg) } },
            playSongsCallback = { songs, startSong, queueName -> playSongs(songs, startSong, queueName) },
            openPlayerSheetCallback = { _isSheetVisible.value = true }
        )

        // Collect AiStateHolder flows for playlist generation state
        viewModelScope.launch {
            combine(
                aiStateHolder.showAiPlaylistSheet,
                aiStateHolder.isGeneratingAiPlaylist,
                aiStateHolder.aiStatus,
                aiStateHolder.aiError,
            ) { show, generating, status, error ->
                AiUiSnapshot(
                    showAiPlaylistSheet = show,
                    isGeneratingAiPlaylist = generating,
                    aiStatus = status,
                    aiError = error
                )
            }.collect { snapshot ->
                _playerUiState.update {
                    it.copy(
                        showAiPlaylistSheet = snapshot.showAiPlaylistSheet,
                        isGeneratingAiPlaylist = snapshot.isGeneratingAiPlaylist,
                        aiStatus = snapshot.aiStatus,
                        aiError = snapshot.aiError
                    )
                }
            }
        }

        // Initialize LibraryStateHolder
        libraryStateHolder.initialize(viewModelScope)

        // Sync library folders and loading states
        viewModelScope.launch {
            combine(
                libraryStateHolder.musicFolders,
                libraryStateHolder.isLoadingLibrary,
                libraryStateHolder.isLoadingCategories,
            ) { folders, loadingLibrary, loadingCategories ->
                Triple(folders, loadingLibrary, loadingCategories)
            }.collect { (folders, loadingLibrary, loadingCategories) ->
                _playerUiState.update {
                    it.copy(
                        musicFolders = folders,
                        isLoadingInitialSongs = loadingLibrary,
                        isLoadingLibraryCategories = loadingCategories,
                    )
                }
            }
        }

        // Sync sort options and storage filter
        viewModelScope.launch {
            combine(
                libraryStateHolder.currentSongSortOption,
                libraryStateHolder.currentAlbumSortOption,
                libraryStateHolder.currentArtistSortOption,
                libraryStateHolder.currentFolderSortOption,
                libraryStateHolder.currentFavoriteSortOption,
            ) { songSort, albumSort, artistSort, folderSort, favoriteSort ->
                SortOptionsSnapshot(songSort, albumSort, artistSort, folderSort, favoriteSort)
            }.collect { snapshot ->
                _playerUiState.update {
                    it.copy(
                        currentSongSortOption = snapshot.songSort,
                        currentAlbumSortOption = snapshot.albumSort,
                        currentArtistSortOption = snapshot.artistSort,
                        currentFolderSortOption = snapshot.folderSort,
                        currentFavoriteSortOption = snapshot.favoriteSort,
                    )
                }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentStorageFilter.collect { filter ->
                _playerUiState.update { it.copy(currentStorageFilter = filter) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.hideLocalMediaFlow.collect { hide ->
                _playerUiState.update { it.copy(hideLocalMedia = hide) }
            }
        }


        castTransferStateHolder.initialize(
            scope = viewModelScope,
            getCurrentQueue = { _playerUiState.value.currentPlaybackQueue },
            updateQueue = { newQueue ->
                _playerUiState.update {
                    it.copy(currentPlaybackQueue = newQueue.toPlaybackQueue())
                }
            },
            getSongsByIdMap = { libraryStateHolder.allSongsById.value },
            onTransferBackComplete = { startProgressUpdates() },
            onSheetVisible = { _isSheetVisible.value = true },
            onDisconnect = { disconnect() },
            onCastError = { message ->
                viewModelScope.launch { _toastEvents.emit(message) }
            },
            onSongChanged = { uriString ->
                castSongUiSyncJob?.cancel()
                castSongUiSyncJob = viewModelScope.launch {
                    delay(220)
                    val currentSongId = stablePlayerState.value.currentSong?.id
                    if (currentSongId != null && currentSongId == lastCastSongUiSyncedId) {
                        return@launch
                    }
                    loadLyricsForCurrentSong()
                    uriString?.toUri()?.let { uri ->
                        themeStateHolder.extractAndGenerateColorScheme(uri, uriString)
                    }
                    if (currentSongId != null) {
                        lastCastSongUiSyncedId = currentSongId
                    }
                }
            }
        )



        viewModelScope.launch {
            // Repeat preference is only a startup restore value.
            // Keeping a live collector here creates a feedback path:
            // player -> DataStore -> collector -> player, which can cause
            // repeat mode oscillation if a transient player state is persisted.
            val savedRepeatMode = userPreferencesRepository.repeatModeFlow.first()
            mediaControllerSyncStateHolder.applyPreferredRepeatMode(savedRepeatMode)
        }

        viewModelScope.launch {
            stablePlayerState
                .map { it.isShuffleEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    syncShuffleStateWithSession(enabled)
                }
        }

        // Auto-hide undo bar when a new song starts playing
        playlistDismissUndoStateHolder.observeUndoStateAgainstPlayback(
            scope = viewModelScope,
            currentSongIdFlow = stablePlayerState.map { it.currentSong?.id },
            getUiState = { _playerUiState.value },
            onHideDismissUndoBar = { hideDismissUndoBar() }
        )

        Trace.endSection() // End PlayerViewModel.init
    }

    fun onMainActivityStart() {
        Trace.beginSection("PlayerViewModel.onMainActivityStart")
        try {
            preloadThemesAndInitialData()
            checkAndUpdateDailyMixIfNeeded()
        } finally {
            Trace.endSection()
        }
    }


    private fun checkAndUpdateDailyMixIfNeeded() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.checkAndUpdateIfNeeded(
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    private fun preloadThemesAndInitialData() {
        Trace.beginSection("PlayerViewModel.preloadThemesAndInitialData")
        try {
            viewModelScope.launch {
                _isInitialThemePreloadComplete.value = false
                if (isSyncingStateFlow.value && !_isInitialDataLoaded.value) {
                    // Sync is active - defer to sync completion handler
                } else if (!_isInitialDataLoaded.value && libraryStateHolder.allSongs.value.isEmpty()) {
                    resetAndLoadInitialData("preloadThemesAndInitialData")
                }
                _isInitialThemePreloadComplete.value = true
            }
        } finally {
            Trace.endSection()
        }
    }

    private fun loadInitialLibraryDataParallel() {
        libraryStateHolder.loadSongsFromRepository()
        libraryStateHolder.loadAlbumsFromRepository()
        libraryStateHolder.loadArtistsFromRepository()
        libraryStateHolder.loadFoldersFromRepository()
    }

    private fun resetAndLoadInitialData(caller: String = "Unknown") {
        Trace.beginSection("PlayerViewModel.resetAndLoadInitialData")
        try {
            Log.d("PlayerViewModel", "resetAndLoadInitialData called by $caller")
            loadInitialLibraryDataParallel()
            updateDailyMix()
        } finally {
            Trace.endSection()
        }
    }

    fun loadSongsIfNeeded() = libraryStateHolder.loadSongsIfNeeded()
    fun loadAlbumsIfNeeded() = libraryStateHolder.loadAlbumsIfNeeded()
    fun loadArtistsIfNeeded() = libraryStateHolder.loadArtistsIfNeeded()
    fun loadFoldersFromRepository() = libraryStateHolder.loadFoldersFromRepository()

    fun setStorageFilter(filter: com.theveloper.pixelplay.data.model.StorageFilter) {
        libraryStateHolder.setStorageFilter(filter)
    }

    fun setPlaylistPickerStorageFilter(filter: com.theveloper.pixelplay.data.model.StorageFilter) {
        _playlistPickerStorageFilter.value = filter
    }

    fun setHideLocalMedia(hide: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHideLocalMedia(hide)
        }
    }

    fun toggleStorageFilter() {
        val current = _playerUiState.value.currentStorageFilter
        val next = when (current) {
            com.theveloper.pixelplay.data.model.StorageFilter.ALL -> com.theveloper.pixelplay.data.model.StorageFilter.ONLINE
            com.theveloper.pixelplay.data.model.StorageFilter.ONLINE -> com.theveloper.pixelplay.data.model.StorageFilter.OFFLINE
            com.theveloper.pixelplay.data.model.StorageFilter.OFFLINE -> com.theveloper.pixelplay.data.model.StorageFilter.ALL
        }
        setStorageFilter(next)
    }

    fun showAndPlaySong(
        song: Song,
        contextSongs: List<Song>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true,
        cancelPendingQueueBuild: Boolean = true,
        playlistId: String? = null,
        indexInQueue: Int? = null
    ) = playbackDispatchStateHolder.showAndPlaySong(
        song, contextSongs, queueName, isVoluntaryPlay, cancelPendingQueueBuild, playlistId, indexInQueue
    )

    fun showAndPlaySong(song: Song) = playbackDispatchStateHolder.showAndPlaySong(song)

    fun playAlbum(album: Album) =
        queueStateHolder.playAlbum(album, playbackSourceCallbacks())

    fun playArtist(artist: Artist) =
        queueStateHolder.playArtist(artist, playbackSourceCallbacks())

    fun removeSongFromQueue(songId: String) {
        queueUndoStateHolder.removeSongFromQueue(
            scope = viewModelScope,
            mediaController = mediaController,
            songId = songId,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) }
        )
        // During a mix, taking a song out of the queue says it doesn't fit the vibe: the mix
        // steers away from similar songs (a soft, session-only signal; undo clears it).
        _playerUiState.value.lastRemovedQueueSong?.takeIf { it.id == songId }
            ?.let(continuousMixRuntime::noteRemovedFromQueue)
    }

    fun undoRemoveSongFromQueue() {
        _playerUiState.value.lastRemovedQueueSong?.let(continuousMixRuntime::noteRestoredToQueue)
        queueUndoStateHolder.undoRemoveSongFromQueue(
            mediaController = mediaController,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) }
        )
    }

    fun hideQueueItemUndoBar() {
        queueUndoStateHolder.hideQueueItemUndoBar { mutation ->
            _playerUiState.update(mutation)
        }
    }

    fun reorderQueueItem(fromIndex: Int, toIndex: Int) {
        mediaController?.let { controller ->
            if (fromIndex >= 0 && fromIndex < controller.mediaItemCount &&
                toIndex >= 0 && toIndex < controller.mediaItemCount) {
                val currentIndexBeforeMove = controller.currentMediaItemIndex
                    .takeIf { it != C.INDEX_UNSET }
                    ?: playbackStateHolder.stablePlayerState.value.currentMediaItemIndex
                val updatedCurrentIndex = moveQueueIndex(currentIndexBeforeMove, fromIndex, toIndex)

                // Move the item in the MediaController's timeline.
                // This is the source of truth for playback.
                controller.moveMediaItem(fromIndex, toIndex)

                // Optimistically mirror the committed move in UI state. The drag preview stays
                // local while dragging, so this single state update does not add per-frame work.
                _playerUiState.update { state ->
                    val updatedQueue = state.currentPlaybackQueue.moveSong(fromIndex, toIndex)
                    if (updatedQueue === state.currentPlaybackQueue) {
                        state
                    } else {
                        state.copy(currentPlaybackQueue = updatedQueue)
                    }
                }

                playbackStateHolder.updateStablePlayerState { state ->
                    if (updatedCurrentIndex == C.INDEX_UNSET ||
                        state.currentMediaItemIndex == updatedCurrentIndex
                    ) {
                        state
                    } else {
                        state.copy(currentMediaItemIndex = updatedCurrentIndex)
                    }
                }
            }
        }
    }

    fun togglePlayerSheetState(resetPredictiveState: Boolean = true) {
        _sheetState.value = if (_sheetState.value == PlayerSheetState.COLLAPSED) {
            PlayerSheetState.EXPANDED
        } else {
            PlayerSheetState.COLLAPSED
        }
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun expandPlayerSheet(resetPredictiveState: Boolean = true) {
        _sheetState.value = PlayerSheetState.EXPANDED
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun collapsePlayerSheet(resetPredictiveState: Boolean = true) {
        _sheetState.value = PlayerSheetState.COLLAPSED
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    /**
     * Cover tap in the player. Library songs open their library album. Online songs (YouTube,
     * Spotify, Apple Music) only carry a hashed album id that no table knows, so their album is
     * looked up by name instead: the card it already shows on, or a stream album page that loads
     * the album's tracklist online.
     */
    fun triggerAlbumNavigationFromPlayer(song: Song) {
        val isLibrarySong = song.id.toLongOrNull() != null
        if (isLibrarySong) {
            triggerAlbumNavigationFromPlayer(song.albumId)
            return
        }
        triggerAlbumNavigationFromPlayer(streamCollection.albumIdForOnlineSong(song))
    }

    fun triggerAlbumNavigationFromPlayer(albumId: Long) {
        if (albumId == -1L) {
            Log.d("AlbumDebug", "triggerAlbumNavigationFromPlayer ignored invalid albumId=$albumId")
            return
        }

        val existingJob = albumNavigationJob
        if (existingJob != null && existingJob.isActive) {
            Log.d("AlbumDebug", "triggerAlbumNavigationFromPlayer ignored; navigation already in progress for albumId=$albumId")
            return
        }

        albumNavigationJob?.cancel()
        albumNavigationJob = viewModelScope.launch {
            val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
            Log.d(
                "AlbumDebug",
                "triggerAlbumNavigationFromPlayer: albumId=$albumId, songId=${currentSong?.id}, title=${currentSong?.title}"
            )
            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _albumNavigationRequests.emit(albumId)
        }
    }

    fun triggerArtistNavigationFromPlayer(artistId: Long, artistName: String? = null) {
        val songForArtist = playbackStateHolder.stablePlayerState.value.currentSong
        if (songForArtist != null && !songForArtist.isLocal) {
            // Online / downloaded songs have no library artist (their id is just a name hash), so
            // open the artist profile by name instead of dropping the tap.
            val name = artistName?.takeIf { it.isNotBlank() }
                ?: songForArtist.artists.firstOrNull { it.id == artistId }?.name?.takeIf { it.isNotBlank() }
                ?: songForArtist.artists.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                ?: songForArtist.artist.takeIf { it.isNotBlank() }
            if (name != null) {
                val existing = artistNavigationJob
                if (existing != null && existing.isActive) return
                artistNavigationJob = viewModelScope.launch {
                    collapsePlayerSheet()
                    withTimeoutOrNull(900) {
                        awaitSheetState(PlayerSheetState.COLLAPSED)
                        awaitPlayerCollapse()
                    }
                    _artistNameNavigationRequests.emit(name)
                }
                return
            }
        }

        if (artistId == 0L) {
            Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer ignored invalid artistId=$artistId")
            return
        }

        val existingJob = artistNavigationJob
        if (existingJob != null && existingJob.isActive) {
            Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer ignored; navigation already in progress for artistId=$artistId")
            return
        }

        artistNavigationJob?.cancel()
        artistNavigationJob = viewModelScope.launch {
            var resolvedId = artistId
            val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
            
            if (resolvedId == -1L && currentSong != null) {
                val idFromName = musicRepository.getArtistIdByName(currentSong.artist)
                if (idFromName != null) {
                    resolvedId = idFromName
                }
            }

            if (resolvedId == 0L || resolvedId == -1L) {
                Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer: could not resolve artistId for name=${currentSong?.artist}")
                return@launch
            }

            Log.d(
                "ArtistDebug",
                "triggerArtistNavigationFromPlayer: artistId=$resolvedId, songId=${currentSong?.id}, title=${currentSong?.title}"
            )
            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _artistNavigationRequests.emit(resolvedId)
        }
    }

    suspend fun awaitSheetState(target: PlayerSheetState) {
        sheetState.first { it == target }
    }

    suspend fun awaitPlayerCollapse(threshold: Float = 0.1f, timeoutMillis: Long = 800L) {
        withTimeoutOrNull(timeoutMillis) {
            snapshotFlow { playerContentExpansionFraction.value }
                .first { it <= threshold }
        }
    }

    // rebuildPlayerQueue functionality moved to PlaybackStateHolder (simplified)
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) =
        playbackDispatchStateHolder.playSongs(songsToPlay, startSong, queueName, playlistId)

    fun playSongsShuffled(
        songsToPlay: List<Song>,
        queueName: String = "None",
        playlistId: String? = null,
        startAtZero: Boolean = false
    ) = playbackDispatchStateHolder.playSongsShuffled(songsToPlay, queueName, playlistId, startAtZero)

    fun playExternalUri(uri: Uri) = playbackDispatchStateHolder.playExternalUri(uri)

    fun showPlayer() {
        if (stablePlayerState.value.currentSong != null) {
            _isSheetVisible.value = true
        }
    }

    fun openLyricsSheet() {
        showPlayer()
        _lyricsOpenRequests.tryEmit(Unit)
    }

    private fun syncShuffleStateWithSession(enabled: Boolean) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, enabled)
        }
        controller.sendCustomCommand(
            SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE, Bundle()),
            args
        )
    }

    fun toggleShuffle(currentSongOverride: Song? = null) {
        playbackDispatchStateHolder.cancelPendingFullQueuePlayback()
        val currentQueue = _playerUiState.value.currentPlaybackQueue.toList()
        val currentSong = currentSongOverride
            ?: playbackStateHolder.stablePlayerState.value.currentSong
            ?: mediaController?.currentMediaItem?.let { mediaControllerSyncStateHolder.resolveSongFromMediaItem(it) }
            ?: currentQueue.firstOrNull()

        playbackStateHolder.toggleShuffle(
            currentSongs = currentQueue,
            currentSong = currentSong,
            currentQueueSourceName = _playerUiState.value.currentQueueSourceName,
            updateQueueCallback = { newQueue ->
                _playerUiState.update { it.copy(currentPlaybackQueue = newQueue.toPlaybackQueue()) }
            }
        )
    }

    fun cycleRepeatMode() {
        playbackStateHolder.cycleRepeatMode()
    }

    private suspend fun setFavoriteStatusEverywhere(songId: String, isFavorite: Boolean) {
        musicRepository.setFavoriteStatus(songId, isFavorite)
    }

    fun toggleFavorite() {
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong ?: return
        viewModelScope.launch {
            val favoriteSongId = resolveFavoriteSongId(currentSong) ?: return@launch
            val currentlyFavorite = favoriteSongIds.value.contains(favoriteSongId)
            val newFavState = !currentlyFavorite
            if (newFavState && (currentSong.id.startsWith("yt_") || currentSong.youtubeId != null || currentSong.contentUriString.startsWith("youtube://") || !currentSong.isLocal)) {
                musicRepository.saveCloudSong(currentSong)
            }
            setFavoriteStatusEverywhere(favoriteSongId, newFavState)
        }
    }

    fun toggleFavoriteSpecificSong(song: Song, removing: Boolean = false) {
        viewModelScope.launch {
            val favoriteSongId = resolveFavoriteSongId(song) ?: run {
                android.util.Log.e("PlayerViewModel", "Could not resolve song ID for favorite toggle")
                return@launch
            }
            
            // Query DB directly as fallback to avoid race conditions with StateFlow initialization
            val currentlyFavorite = musicRepository.getFavoriteSongIdsOnce().contains(favoriteSongId)
            
            val targetFavoriteState = if (removing) false else !currentlyFavorite
            if (targetFavoriteState && (song.id.startsWith("yt_") || song.youtubeId != null || song.contentUriString.startsWith("youtube://") || !song.isLocal)) {
                musicRepository.saveCloudSong(song)
            }
            setFavoriteStatusEverywhere(favoriteSongId, targetFavoriteState)
        }
    }

    private suspend fun resolveFavoriteSongId(song: Song?): String? {
        song ?: return null
        if (song.id.toLongOrNull() != null) {
            return song.id
        }

        if (song.id.startsWith("yt_") || song.youtubeId != null || song.contentUriString.startsWith("youtube://") || !song.isLocal) {
            return song.id
        }

        val contentUriCandidates = buildList {
            if (song.id.startsWith(EXTERNAL_SONG_ID_PREFIX)) {
                add(song.id.removePrefix(EXTERNAL_SONG_ID_PREFIX))
            }
            add(song.contentUriString)
        }.filter { it.isNotBlank() }.distinct()

        for (candidate in contentUriCandidates) {
            musicRepository.getSongIdByContentUri(candidate)?.let { return it.toString() }
            parseMediaStoreAudioId(candidate)?.let { return it.toString() }
        }

        val pathCandidates = buildList {
            add(song.path)
            contentUriCandidates.forEach { candidate ->
                parseFileUriPath(candidate)?.let(::add)
            }
        }.filter { it.isNotBlank() }.distinct()

        for (candidate in pathCandidates) {
            musicRepository.getSongByPath(candidate)?.id?.takeIf { it.toLongOrNull() != null }?.let {
                return it
            }
        }

        return null
    }

    private fun parseMediaStoreAudioId(uriString: String): Long? {
        val normalizedUri = uriString.substringBefore('?').substringBefore('#')
        if (
            !normalizedUri.startsWith("content://media/", ignoreCase = true) ||
            !normalizedUri.contains("/audio/media/", ignoreCase = true)
        ) {
            return null
        }

        return normalizedUri.substringAfterLast('/').toLongOrNull()?.takeIf { it > 0L }
    }

    private fun parseFileUriPath(uriString: String): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return uri.takeIf { it.scheme == "file" }?.path?.takeIf { it.isNotBlank() }
    }

    private var mixGeneration = 0L
    private var continuousMixJob: kotlinx.coroutines.Job? = null
    private val _activeMixFlavor = kotlinx.coroutines.flow.MutableStateFlow<com.theveloper.pixelplay.data.MixFlavor?>(null)
    val activeMixFlavor: kotlinx.coroutines.flow.StateFlow<com.theveloper.pixelplay.data.MixFlavor?> = _activeMixFlavor.asStateFlow()
    private val _mixStatus = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val mixStatus: kotlinx.coroutines.flow.StateFlow<String?> = _mixStatus
    init {
        viewModelScope.launch { continuousMixRuntime.flavor.collect { _activeMixFlavor.value = it } }
        viewModelScope.launch { continuousMixRuntime.status.collect { _mixStatus.value = it } }
    }

    /** True while a mix is planning / adding songs. */
    val isMixWorking: StateFlow<Boolean> = continuousMixRuntime.working

    /** Short pulse whenever songs are added to the queue (by a mix, the user, anything). */
    private val _queueGrowthPulse = MutableStateFlow(false)
    private var queueGrowthPulseToken = 0L

    /**
     * Queue is "busy": a mix is building it or songs were just added. Drives the animated
     * outline on the full player's queue button.
     */
    val isQueueBusy: StateFlow<Boolean> = combine(continuousMixRuntime.working, _queueGrowthPulse) { mixing, grew ->
        mixing || grew
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            var lastSize = -1
            _playerUiState.map { it.currentPlaybackQueue.size }.distinctUntilChanged().collect { size ->
                val grew = lastSize > 0 && size > lastSize
                lastSize = size
                if (grew) {
                    // Each addition extends the pulse; only the latest one switches it off.
                    val token = ++queueGrowthPulseToken
                    _queueGrowthPulse.value = true
                    viewModelScope.launch {
                        delay(1_600)
                        if (token == queueGrowthPulseToken) _queueGrowthPulse.value = false
                    }
                }
            }
        }
    }


    /**
     * The mix button (full player and queue): tap cycles No mix → Normal Mix → Smart Mix → No mix.
     * Holding the button jumps straight to Smart Mix ([activateSmartMix]).
     */
    fun cycleMixMode() {
        when (_activeMixFlavor.value) {
            // Plain shuffle already shows as the "on" (normal) state, so it moves on to Smart Mix.
            null -> startContinuousMix(
                if (playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                    com.theveloper.pixelplay.data.MixFlavor.SMART
                } else {
                    com.theveloper.pixelplay.data.MixFlavor.NORMAL
                }
            )
            com.theveloper.pixelplay.data.MixFlavor.NORMAL -> switchContinuousMix(com.theveloper.pixelplay.data.MixFlavor.SMART)
            // "No mix": the mix's own upcoming songs leave the queue straight away.
            com.theveloper.pixelplay.data.MixFlavor.SMART -> stopContinuousMix(clearUpcoming = true)
        }
    }

    /**
     * Normal ↔ Smart while a mix is playing: the running mix switches in place (its session,
     * skips and steers carry over) and its upcoming songs are swapped for the new flavour's as
     * soon as the first one is ready. Starts the mix when none is running yet.
     */
    fun switchContinuousMix(flavor: com.theveloper.pixelplay.data.MixFlavor) {
        val running = continuousMixRuntime.flavor.value
        if (running == null || continuousMixJob?.isActive == true) {
            startContinuousMix(flavor)
            return
        }
        if (running == flavor) return
        _playerUiState.update { it.copy(currentQueueSourceName = flavor.title) }
        if (!continuousMixRuntime.switchFlavor(flavor)) {
            startContinuousMix(flavor)
            return
        }
        _activeMixFlavor.value = flavor
    }

    // ── Play history (shown above the current song in the queue) ─────────────────────────

    private val _playHistory = MutableStateFlow<List<Song>>(emptyList())
    /** Songs played before the current one, oldest first, newest (the previous song) last. */
    val playHistory: StateFlow<List<Song>> = _playHistory.asStateFlow()

    init {
        // Session history: every time the playing song changes, the one before it is added.
        viewModelScope.launch {
            var previous: Song? = null
            stablePlayerState.map { it.currentSong }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collect { song ->
                    val last = previous
                    if (last != null && song != null && last.id != song.id) addToPlayHistory(last)
                    previous = song
                }
        }
        // Seed with what was played before this session (the listening-stats history).
        viewModelScope.launch {
            try {
                val entries = withTimeoutOrNull(6_000) {
                    playbackHistory.first { it.isNotEmpty() }
                }.orEmpty()
                val ids = entries.sortedBy { it.timestamp }.map { it.songId }
                    .asReversed().distinct().take(PLAY_HISTORY_MAX).asReversed()
                if (ids.isEmpty()) return@launch
                val byId = withContext(Dispatchers.IO) { musicRepository.getSongsByIds(ids).first() }
                    .associateBy { it.id }
                val seeded = ids.mapNotNull(byId::get)
                _playHistory.update { session ->
                    val sessionIds = session.map { it.id }.toSet()
                    (seeded.filterNot { it.id in sessionIds } + session).takeLast(PLAY_HISTORY_MAX)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Couldn't load play history", e)
            }
        }
    }

    private fun addToPlayHistory(song: Song) {
        _playHistory.update { list ->
            (list.filterNot { it.id == song.id } + song).takeLast(PLAY_HISTORY_MAX)
        }
    }

    fun removeFromPlayHistory(songId: String) {
        _playHistory.update { list -> list.filterNot { it.id == songId } }
    }

    fun clearPlayHistory() {
        _playHistory.value = emptyList()
    }

    /**
     * Plays a song from the history now: it is inserted right after the current song and
     * started, so the rest of the queue (and the mix) carries on after it.
     */
    fun playFromHistory(song: Song) {
        viewModelScope.launch {
            try {
                val item = playbackDispatchStateHolder.buildResolvedPlaybackMediaItem(song)
                val controller = mediaController ?: return@launch
                val insertAt = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
                controller.addMediaItem(insertAt, item)
                controller.seekTo(insertAt, 0L)
                controller.prepare()
                controller.play()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Couldn't play from history", e)
                _toastEvents.emit("Couldn't play that song")
            }
        }
    }

    // ── Queue mix buttons (row under the current song in the queue) ──────────────────────

    private val _queueMixFilterId = MutableStateFlow<String?>(null)
    /**
     * The mix button last used to rebuild "up next", for highlighting it. It stays selected while
     * the mix it started carries on (skipping songs doesn't reset it); cleared when the mix ends.
     */
    val queueMixFilterId: StateFlow<String?> = _queueMixFilterId.asStateFlow()

    private val _queueMixBusy = MutableStateFlow(false)
    /** True while an "up next" mix is being built. */
    val queueMixBusy: StateFlow<Boolean> = _queueMixBusy.asStateFlow()

    private var queueMixJob: Job? = null

    /** Filters for the queue's mix row: the Your Music presets plus the user's own filters. */
    fun queueMixFilters(): List<com.theveloper.pixelplay.presentation.library.VibeFilter> =
        com.theveloper.pixelplay.presentation.library.QueueVibeMix.filters(
            com.theveloper.pixelplay.presentation.library.QueueVibeMix.loadCustomFilters(context)
        )

    /**
     * Replaces everything after the playing song with a mix built around it.
     *
     * [filterId] is [com.theveloper.pixelplay.presentation.library.QueueVibeMix.SIMILAR_ID] or a
     * Your Music filter id. The song pool follows the mix mode: with Smart Mix on, the library
     * is topped up with online songs related to the playing one; otherwise only the library
     * is used. The playing song keeps playing.
     */
    /**
     * A queue mix button was tapped. Tapping the vibe that is already locked turns the lock off
     * (the mix goes back to following what's played); anything else builds up next from it.
     */
    fun onQueueMixChip(filterId: String) {
        val locked = dailyMixStateHolder.lockedVibe
        if (filterId != com.theveloper.pixelplay.presentation.library.QueueVibeMix.SIMILAR_ID &&
            filterId == _queueMixFilterId.value && locked?.id == filterId
        ) {
            clearVibeLock()
        } else {
            mixUpNextFromCurrent(filterId)
        }
    }

    /** Ends a vibe lock; the running mix keeps going, following what's played. */
    fun clearVibeLock() {
        val label = dailyMixStateHolder.lockedVibe?.label ?: return
        dailyMixStateHolder.clearVibe()
        _queueMixFilterId.value = null
        continuousMixRuntime.refreshFuture(keepAutomatic = 2)
        viewModelScope.launch { _toastEvents.emit("$label lock off · the mix follows what you play") }
    }

    fun mixUpNextFromCurrent(filterId: String) {
        val current = stablePlayerState.value.currentSong ?: return
        queueMixJob?.cancel()
        _queueMixFilterId.value = filterId
        _queueMixBusy.value = true
        queueMixJob = viewModelScope.launch {
            try {
                val smart = _activeMixFlavor.value == com.theveloper.pixelplay.data.MixFlavor.SMART
                val online = if (!smart) emptyList() else try {
                    kotlinx.coroutines.withTimeoutOrNull(8_000) {
                        dailyMixStateHolder.nextMixBatch(
                            com.theveloper.pixelplay.data.MixFlavor.SMART,
                            listOf(current),
                            setOf(com.theveloper.pixelplay.data.mixIdentity(current))
                        )
                    }.orEmpty()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
                // Local analysis (BPM, key, energy) joined in, so vibe filters can use it.
                val pool = dailyMixStateHolder.analysed((allSongsFlow.value + online).filterNot(dailyMixStateHolder::isDisliked))
                val customFilters = withContext(Dispatchers.IO) {
                    com.theveloper.pixelplay.presentation.library.QueueVibeMix.loadCustomFilters(context)
                }
                // What the mixes have learned (taste, snoozes, removals, skips) orders these too.
                val learned = try {
                    dailyMixStateHolder.personalAdjustments(pool)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyMap()
                }
                val picks = withContext(Dispatchers.Default) {
                    com.theveloper.pixelplay.presentation.library.QueueVibeMix.build(
                        filterId = filterId,
                        current = current,
                        pool = pool,
                        likedIds = favoriteSongIds.value,
                        customFilters = customFilters,
                        seed = System.nanoTime(),
                        adjust = learned
                    )
                }
                val label = if (filterId == com.theveloper.pixelplay.presentation.library.QueueVibeMix.SIMILAR_ID) {
                    "Similar"
                } else {
                    com.theveloper.pixelplay.presentation.library.QueueVibeMix.filters(customFilters)
                        .firstOrNull { it.id == filterId }?.label ?: "Mix"
                }
                if (picks.isEmpty()) {
                    _toastEvents.emit("No songs in your library match \"$label\" yet")
                    return@launch
                }
                val items = picks.map { song ->
                    if (song.youtubeId != null || song.id.startsWith("yt_") || com.theveloper.pixelplay.data.accounts.CatalogTracks.isCatalogSongId(song.id)) {
                        try {
                            musicRepository.saveCloudSong(song)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Still playable from its stream URI; only the library record is missing.
                        }
                    }
                    // Tagged so these songs don't steer the continuous mix afterwards: the filter
                    // applies once, to this song, and a mix re-plan never removes them.
                    com.theveloper.pixelplay.data.MixQueueMetadata.markFilterPick(
                        playbackDispatchStateHolder.buildResolvedPlaybackMediaItem(song)
                    )
                }
                val controller = mediaController ?: return@launch
                // If the user skipped while this was being built, it continues from wherever
                // playback is now rather than being thrown away.
                val index = controller.currentMediaItemIndex
                val count = controller.mediaItemCount
                if (_activeMixFlavor.value != null) {
                    // During a mix, songs the user queued themselves stay (they play first);
                    // only the mix's own songs and earlier mix-button songs are replaced.
                    for (i in count - 1 downTo index + 1) {
                        if (!com.theveloper.pixelplay.data.MixQueueMetadata.userPick(controller.getMediaItemAt(i))) {
                            controller.removeMediaItem(i)
                        }
                    }
                } else if (index + 1 < count) {
                    // No mix: the rest of the queue is the album / playlist that was playing.
                    controller.removeMediaItems(index + 1, count)
                }
                controller.addMediaItems(items)
                // A vibe button locks that vibe for the rest of the mix: when these songs run
                // out, the continuous mix keeps to it (tap the button again to unlock).
                // "Similar" is about this one song, so it ends any lock instead.
                val vibeFilter = customFilters.let { com.theveloper.pixelplay.presentation.library.QueueVibeMix.filters(it) }
                    .firstOrNull { it.id == filterId }
                if (vibeFilter != null) dailyMixStateHolder.lockVibe(vibeFilter) else dailyMixStateHolder.clearVibe()
                _toastEvents.emit(
                    if (vibeFilter != null) "Up next: $label · ${items.size} songs · the mix stays $label"
                    else "Up next: $label · ${items.size} songs"
                )
                // When these run out the mix keeps going instead of the queue ending: the
                // continuous mix picks up from what was played and keeps learning from likes,
                // dislikes, removals and skips. An already-running mix just carries on after them.
                if (_activeMixFlavor.value == null) {
                    startContinuousMix(com.theveloper.pixelplay.data.MixFlavor.NORMAL, keepVibe = true)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Queue mix failed", e)
                _toastEvents.emit("Couldn't build that mix")
            } finally {
                _queueMixBusy.value = false
            }
        }
    }

    fun activateSmartMix() {
        // Holding the mix button while Smart Mix is already on leaves it running.
        if (_activeMixFlavor.value == com.theveloper.pixelplay.data.MixFlavor.SMART) return
        switchContinuousMix(com.theveloper.pixelplay.data.MixFlavor.SMART)
    }

    // ── Queue options: steer the mix with a typed song or artist ─────────────────────────

    private val _mixPrompt = MutableStateFlow<String?>(null)
    /** What the user typed in the queue's prompt box, while it steers the running mix. */
    val mixPrompt: StateFlow<String?> = _mixPrompt.asStateFlow()
    private var mixPromptJob: Job? = null

    init {
        // The mix ended (stopped, or a new queue was played): its prompt and the queue mix button
        // it was continuing from no longer apply. Declared after both flows it touches.
        viewModelScope.launch {
            continuousMixRuntime.flavor.drop(1).collect { flavor ->
                if (flavor == null && continuousMixJob?.isActive != true) {
                    _mixPrompt.value = null
                    _queueMixFilterId.value = null
                    dailyMixStateHolder.clearVibe()
                }
            }
        }
    }

    /**
     * "More of this": finds songs for [text] (library first, then online) and steers the mix
     * toward them. Starts Smart Mix from the playing song when no mix is running; otherwise the
     * running mix re-plans its upcoming songs, keeping the next one so the change is gradual.
     */
    fun steerMixWithPrompt(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        mixPromptJob?.cancel()
        mixPromptJob = viewModelScope.launch {
            val matches = try {
                dailyMixStateHolder.resolveMixPrompt(query)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Mix prompt lookup failed", e)
                emptyList()
            }
            if (matches.isEmpty()) {
                _toastEvents.emit("Couldn't find \"$query\"")
                return@launch
            }
            dailyMixStateHolder.steerMixTowards(matches)
            val running = _activeMixFlavor.value
            if (running == null) {
                startContinuousMix(com.theveloper.pixelplay.data.MixFlavor.SMART, keepPrompt = true)
            } else {
                continuousMixRuntime.refreshFuture(keepAutomatic = 1)
            }
            _mixPrompt.value = query
            _toastEvents.emit("${(running ?: com.theveloper.pixelplay.data.MixFlavor.SMART).title} · more like $query")
        }
    }

    /** Removes the typed steer; the running mix goes back to following what's played. */
    fun clearMixPrompt() {
        mixPromptJob?.cancel()
        if (_mixPrompt.value == null) return
        _mixPrompt.value = null
        dailyMixStateHolder.clearMixPromptSteer()
        continuousMixRuntime.refreshFuture(keepAutomatic = 2)
    }

    /**
     * Tapping a song in the queue: jump to it inside the current queue so a running mix simply
     * continues from there. Only falls back to loading a new queue when the song isn't in it.
     */
    fun playQueueItem(song: Song, queueIndex: Int) {
        val controller = mediaController
        if (controller != null && controller.isConnected && !isRemotePlaybackActive.value) {
            val count = controller.mediaItemCount
            val target = if (queueIndex in 0 until count && controller.getMediaItemAt(queueIndex).mediaId == song.id) {
                queueIndex
            } else {
                (0 until count).filter { controller.getMediaItemAt(it).mediaId == song.id }
                    .minByOrNull { kotlin.math.abs(it - queueIndex) } ?: -1
            }
            if (target >= 0) {
                if (target != controller.currentMediaItemIndex || controller.playbackState == Player.STATE_ENDED) {
                    controller.seekTo(target, 0L)
                }
                if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                controller.play()
                incrementSongScore(song)
                return
            }
        }
        val ui = _playerUiState.value
        showAndPlaySong(
            song = song,
            contextSongs = ui.currentPlaybackQueue,
            queueName = ui.currentQueueSourceName,
            indexInQueue = queueIndex
        )
    }

    /** Ends the mix; [clearUpcoming] also takes its upcoming songs out of the queue ("No mix"). */
    fun stopContinuousMix(clearUpcoming: Boolean = false) {
        dailyMixStateHolder.clearMixPromptSteer()
        dailyMixStateHolder.clearVibe()
        _mixPrompt.value = null
        _queueMixFilterId.value = null
        continuousMixRuntime.stop(clearUpcoming)
        dailyMixStateHolder.deactivateMix()
        mixGeneration++
        continuousMixJob?.cancel()
        continuousMixJob = null
        _activeMixFlavor.value = null
        _mixStatus.value = null
        if (playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
            toggleShuffle()
        }
    }

    fun startContinuousMix(
        flavor: com.theveloper.pixelplay.data.MixFlavor,
        initial: List<Song> = emptyList(),
        keepPrompt: Boolean = false,
        keepVibe: Boolean = false
    ) {
        continuousMixJob?.cancel()
        continuousMixRuntime.stop()
        dailyMixStateHolder.activateMix(flavor.name.lowercase(java.util.Locale.ROOT), keepPrompt, keepVibe)
        val generation = ++mixGeneration
        _activeMixFlavor.value = flavor
        continuousMixJob = viewModelScope.launch {
            var handedToService = false
            var queueRevision = playbackDispatchStateHolder.queueRevision
            fun sessionIsCurrent() = generation == mixGeneration &&
                queueRevision == playbackDispatchStateHolder.queueRevision &&
                _playerUiState.value.currentQueueSourceName == flavor.title
            try {
                _mixStatus.value = "Preparing ${flavor.title}…"
                val currentQueue = _playerUiState.value.currentPlaybackQueue
                val currentSong = stablePlayerState.value.currentSong
                val isAlreadyPlaying = initial.isEmpty() && currentSong != null && currentQueue.isNotEmpty()

                if (isAlreadyPlaying) {
                    _playerUiState.update { it.copy(currentQueueSourceName = flavor.title) }

                    if (flavor == com.theveloper.pixelplay.data.MixFlavor.NORMAL) {
                        if (playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                            toggleShuffle()
                        }
                    }
                    // Smart Mix's online songs are found and added by the mix runtime, which
                    // resolves several at once and adds the first within about a second (this
                    // used to fetch and resolve one song at a time here before handing over).
                    if (!sessionIsCurrent()) return@launch
                    _mixStatus.value = flavor.title
                } else {
                    val seeds = initial.ifEmpty { dailyMixStateHolder.yourMixSongs.value }
                    var firstBatch = when {
                        // An explicit list (a Your Music vibe mix) plays as given, in its order.
                        flavor == com.theveloper.pixelplay.data.MixFlavor.NORMAL && initial.isNotEmpty() ->
                            initial.filterNot(dailyMixStateHolder::isDisliked).take(com.theveloper.pixelplay.data.MixRefillPolicy.MAX_QUEUE)
                        // Normal Mix from nothing: planned by the mix engine, seeded by the top of
                        // Your Mix, so it learns and sequences like the rest of the mix.
                        flavor == com.theveloper.pixelplay.data.MixFlavor.NORMAL ->
                            dailyMixStateHolder.nextMixBatch(flavor, seeds.take(6), emptySet(), limit = 24)
                                .ifEmpty { seeds.filterNot(dailyMixStateHolder::isDisliked).take(24) }
                        else -> dailyMixStateHolder.nextMixBatch(flavor, seeds.takeLast(6), seeds.map { com.theveloper.pixelplay.data.mixIdentity(it) }.toSet())
                    }
                    // Nothing in the library to start from: start from online songs instead.
                    if (firstBatch.isEmpty() && flavor == com.theveloper.pixelplay.data.MixFlavor.NORMAL) {
                        firstBatch = dailyMixStateHolder.nextMixBatch(com.theveloper.pixelplay.data.MixFlavor.SMART, seeds.takeLast(6), emptySet())
                    }
                    if (firstBatch.isEmpty()) {
                        _mixStatus.value = null
                        _toastEvents.emit("Couldn't start a mix: add some music or check your connection")
                        _activeMixFlavor.value = null
                        return@launch
                    }
                    firstBatch.forEach { musicRepository.saveCloudSong(it) }
                    _mixStatus.value = flavor.title
                    playSongs(firstBatch, firstBatch.first(), flavor.title)
                    kotlinx.coroutines.withTimeoutOrNull(15_000) {
                        _playerUiState.first { it.currentQueueSourceName == flavor.title }
                    } ?: run {
                        _activeMixFlavor.value = null
                        return@launch
                    }
                    kotlinx.coroutines.withTimeoutOrNull(30_000) {
                        while (mediaController?.mediaItemCount != firstBatch.size || mediaController?.currentMediaItem?.mediaId !in firstBatch.map { it.id }) {
                            if (_playerUiState.value.currentQueueSourceName != flavor.title) return@withTimeoutOrNull
                            kotlinx.coroutines.delay(100)
                        }
                    } ?: run {
                        _activeMixFlavor.value = null
                        return@launch
                    }
                }

                queueRevision = playbackDispatchStateHolder.queueRevision
                if (!isAlreadyPlaying) {
                    val controller = mediaController ?: return@launch
                    val songs = _playerUiState.value.currentPlaybackQueue.associateBy { it.id }
                    for (index in controller.currentMediaItemIndex + 1 until controller.mediaItemCount) {
                        val item = controller.getMediaItemAt(index)
                        val song = songs[item.mediaId] ?: continue
                        val tagged = dailyMixStateHolder.attributeMixSong(song, item, queueRevision)
                        if (!sessionIsCurrent()) return@launch
                        if (index < controller.mediaItemCount && controller.getMediaItemAt(index) == item) {
                            controller.replaceMediaItem(index, tagged)
                        }
                    }
                }
                // Starting over a queue a previous mix left songs in: those are swapped for this one's.
                continuousMixRuntime.start(flavor, _playerUiState.value.currentPlaybackQueue, replaceLeftovers = isAlreadyPlaying)
                handedToService = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _mixStatus.value = "Mix refill unavailable: ${error.message ?: "connection failed"}"
            } finally {
                if (generation == mixGeneration && !handedToService) {
                    _activeMixFlavor.value = null
                    dailyMixStateHolder.deactivateMix()
                }
            }
        }
    }
    fun addSongToQueue(song: Song) {
        playbackDispatchStateHolder.addSongToQueue(song)
    }

    fun addSongNextToQueue(song: Song) {
        playbackDispatchStateHolder.addSongNextToQueue(song)
    }

    // =====================================================
    // Multi-Selection Batch Operations — delegated to
    // [MultiSelectionStateHolder]; the ViewModel only supplies the
    // playback/toast collaborators via [selectionActionCallbacks].
    // =====================================================

    fun playSelectedSongs(songs: List<Song>) =
        multiSelectionStateHolder.playSelectedSongs(songs, selectionActionCallbacks())

    fun addSelectedToQueue(songs: List<Song>) =
        multiSelectionStateHolder.addSelectedToQueue(songs, selectionActionCallbacks())

    fun addSelectedAsNext(songs: List<Song>) =
        multiSelectionStateHolder.addSelectedAsNext(songs, selectionActionCallbacks())

    fun playSelectedAlbums(albums: List<Album>) =
        multiSelectionStateHolder.playSelectedAlbums(albums, selectionActionCallbacks())

    fun addSelectedAlbumsAsNext(albums: List<Album>) =
        multiSelectionStateHolder.addSelectedAlbumsAsNext(albums, selectionActionCallbacks())

    fun addSelectedAlbumsToQueue(albums: List<Album>) =
        multiSelectionStateHolder.addSelectedAlbumsToQueue(albums, selectionActionCallbacks())

    fun likeSelectedSongs(songs: List<Song>) =
        multiSelectionStateHolder.likeSelectedSongs(songs, selectionActionCallbacks())

    fun unlikeSelectedSongs(songs: List<Song>) =
        multiSelectionStateHolder.unlikeSelectedSongs(songs, selectionActionCallbacks())

    fun shareSelectedAsZip(songs: List<Song>) =
        multiSelectionStateHolder.shareSelectedAsZip(songs, selectionActionCallbacks())

    suspend fun getSongsForGenres(genres: List<Genre>): List<Song> =
        multiSelectionStateHolder.getSongsForGenres(genres)

    suspend fun getSongsForAlbums(albums: List<Album>): List<Song> =
        multiSelectionStateHolder.getSongsForAlbums(albums)

    fun playSelectedGenres(genres: List<Genre>) =
        multiSelectionStateHolder.playSelectedGenres(genres, selectionActionCallbacks())

    fun addSelectedGenresToQueue(genres: List<Genre>) =
        multiSelectionStateHolder.addSelectedGenresToQueue(genres, selectionActionCallbacks())

    fun addSelectedGenresAsNext(genres: List<Genre>) =
        multiSelectionStateHolder.addSelectedGenresAsNext(genres, selectionActionCallbacks())

    /**
     * Deletes all selected songs from device with confirmation.
     * Delegated to [SongRemovalStateHolder]; the ViewModel only supplies the
     * UI-state collaborators via [songRemovalCallbacks].
     */
    fun deleteSelectedFromDevice(activity: Activity, songs: List<Song>, onComplete: () -> Unit) {
        songRemovalStateHolder.deleteSelectedFromDevice(activity, songs, onComplete, songRemovalCallbacks())
    }

    fun deleteFromDevice(activity: Activity, song: Song, onResult: (Boolean) -> Unit = {}) {
        songRemovalStateHolder.deleteFromDevice(activity, song, onResult, songRemovalCallbacks())
    }

    /** Called from the UI after the user approves or denies the MediaStore delete request. */
    fun onDeletePermissionResult(granted: Boolean) {
        songRemovalStateHolder.onDeletePermissionResult(granted, songRemovalCallbacks())
    }

    suspend fun removeSong(song: Song) {
        toggleFavoriteSpecificSong(song, true)
        val wasCurrent = stablePlayerState.value.currentSong?.id == song.id
        // Only the song that was playing resets the position; deleting any other song must
        // not touch the player (this used to reset the slider and close the player sheet
        // for every delete, back when the playing song could not be deleted at all).
        if (wasCurrent) playbackStateHolder.setCurrentPosition(0L)
        _playerUiState.update { currentState ->
            currentState.copy(
                // The source name is kept: clearing it ended a running mix whenever any
                // song was deleted.
                currentPlaybackQueue = currentState.currentPlaybackQueue.removeSongById(song.id)
            )
        }
        // Close the player only when nothing is left to play.
        if (_playerUiState.value.currentPlaybackQueue.isEmpty()) {
            _isSheetVisible.value = false
        }
        songRemovalStateHolder.removeSongFromLibrary(song)
    }

    private fun removeFromMediaControllerQueue(songId: String) {
        val controller = mediaController ?: return

        try {
            // Get the current timeline and media item count
            val timeline = controller.currentTimeline
            val mediaItemCount = timeline.windowCount

            // Find the media item to remove by iterating through windows
            for (i in 0 until mediaItemCount) {
                val window = timeline.getWindow(i, Timeline.Window())
                if (window.mediaItem.mediaId == songId) {
                    // Remove the media item by index
                    controller.removeMediaItem(i)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("MediaController", "Error removing from queue: ${e.message}")
        }
    }

    /**
     * Signal from the player sheet whether the slider-bearing UI is currently
     * rendered. Drives the position-ticker's resolution (250 ms vs 1 s).
     */
    fun setSliderUiMounted(mounted: Boolean) {
        playbackStateHolder.setSliderUiMounted(mounted)
    }

    fun playPause() = playbackDispatchStateHolder.playPause()

    private data class VideoAudioResumePoint(val songId: String, val position: Long, val wasPlaying: Boolean)
    private var videoAudioResumePoint: VideoAudioResumePoint? = null

    /** Temporarily lend audio ownership to the visible YouTube player without replacing the queue. */
    fun suspendAudioForVideo(songId: String, onAudioResumed: () -> Unit): ((resume: Boolean, resumePositionMs: Long?) -> Unit)? {
        val controller = mediaController ?: return null
        if (stablePlayerState.value.currentSong?.id != songId || controller.currentMediaItem?.mediaId != songId ||
            isRemotePlaybackActive.value) return null
        val resumePoint = videoAudioResumePoint?.takeIf { it.songId == songId && !controller.playWhenReady }
            ?: VideoAudioResumePoint(songId, controller.currentPosition, controller.playWhenReady)
        videoAudioResumePoint = resumePoint
        controller.pause()
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    videoAudioResumePoint = null
                    onAudioResumed()
                }
            }
        }
        controller.addListener(listener)
        var released = false
        return { resume, resumePositionMs ->
            if (!released) {
                released = true
                controller.removeListener(listener)
                if (resume) videoAudioResumePoint = null
                if (resume && stablePlayerState.value.currentSong?.id == songId &&
                    controller.currentMediaItem?.mediaId == songId && !controller.playWhenReady) {
                    val targetPos = resumePositionMs ?: resumePoint.position
                    val duration = controller.duration
                    val clamped = if (duration > 0L) targetPos.coerceIn(0L, duration) else targetPos.coerceAtLeast(0L)
                    playbackStateHolder.seekTo(clamped)
                    if (resumePoint.wasPlaying) controller.play()
                }
            }
        }
    }

    fun seekTo(position: Long) {
        playbackStateHolder.seekTo(position)
    }

    private fun refreshAutomaticMixFuture() = continuousMixRuntime.refreshFuture()

    var mixDiscoveryBalance: Double
        get() = dailyMixStateHolder.discoveryBalance
        set(value) { dailyMixStateHolder.discoveryBalance = value; refreshAutomaticMixFuture() }
    fun mixDecisionSummary() = dailyMixStateHolder.decisionSummary()

    /** Focused (0) ↔ Varied (1): how soon an artist may come back in the mix. */
    var mixVariety: Double
        get() = dailyMixStateHolder.variety
        set(value) { dailyMixStateHolder.variety = value; refreshAutomaticMixFuture() }

    /** Energy for the mix: null = follow the music, else 0…1 (Calm 0.25 · Steady 0.55 · Hype 0.85). */
    var mixEnergyTarget: Double?
        get() = dailyMixStateHolder.energyTarget
        set(value) { dailyMixStateHolder.energyTarget = value; refreshAutomaticMixFuture() }

    /** Why the mix picked each of its queued songs, and whether it's a discovery (by song id). */
    val mixSongInsights get() = dailyMixStateHolder.mixInsights

    /** How the mix has been received lately (skips, finishes, ranking accuracy); blank if too little data. */
    suspend fun mixQualityReport(): String = dailyMixStateHolder.qualityReport()
    fun resetMixLearning() { viewModelScope.launch { dailyMixStateHolder.resetLearning(); refreshAutomaticMixFuture() } }
    fun resetMixExclusions() { dailyMixStateHolder.resetFeedback(); refreshAutomaticMixFuture() }
    /**
     * "More like this": steers the mix towards the playing song. It shows as the queue's
     * "More like …" steer (with ✕), so it can be seen and cleared like a typed prompt.
     */
    fun moreLikeCurrentSong() {
        val song = stablePlayerState.value.currentSong ?: return
        dailyMixStateHolder.moreLike(song)
        if (_activeMixFlavor.value != null) _mixPrompt.value = song.title
        refreshAutomaticMixFuture()
    }
    /** Recent mix feedback (exclusions, snoozes, removals), newest first, for Tune this mix. */
    val mixFeedbackLog get() = dailyMixStateHolder.feedbackLog

    /** Undoes one item of [mixFeedbackLog] and refreshes the upcoming mix songs. */
    fun undoMixFeedbackItem(id: String) {
        if (dailyMixStateHolder.undoFeedback(id)) refreshAutomaticMixFuture()
    }

    /**
     * Plays a Your Music vibe mix in its planned order and keeps going as a continuous mix
     * locked to that vibe, so it doesn't stop when the list runs out.
     */
    fun playVibeMix(songs: List<Song>, filter: com.theveloper.pixelplay.presentation.library.VibeFilter) {
        if (songs.isEmpty()) return
        dailyMixStateHolder.lockVibe(filter)
        _queueMixFilterId.value = filter.id
        startContinuousMix(com.theveloper.pixelplay.data.MixFlavor.NORMAL, initial = songs, keepVibe = true)
    }

    fun heardCurrentSongTooMuch() {
        val song = stablePlayerState.value.currentSong ?: return
        dailyMixStateHolder.heardTooMuch(song)
        viewModelScope.launch { _toastEvents.emit("Snoozed for 2 weeks. Undo from the broken-heart button.") }
        refreshAutomaticMixFuture()
        listeningStatsTracker.markEndReason(com.theveloper.pixelplay.data.MixEndReason.INTERRUPTION)
        playbackStateHolder.nextSong()
    }
    fun dislikeCurrentSongEverywhere() {
        val song = stablePlayerState.value.currentSong ?: return
        listeningStatsTracker.markEndReason(com.theveloper.pixelplay.data.MixEndReason.DISLIKE)
        dailyMixStateHolder.dislikeEverywhere(song)
        skipRejectedCurrentSong()
    }

    fun undoMixFeedback() {
        if (dailyMixStateHolder.undoMixFeedback()) {
            _mixStatus.value = "Feedback undone"
            refreshAutomaticMixFuture()
        }
    }

    fun dislikeCurrentSong() {
        val song = stablePlayerState.value.currentSong ?: return
        val sourceId = mediaController?.currentMediaItem?.localConfiguration?.uri
            ?.takeIf { it.scheme == "youtube" }?.host?.removePrefix("yt_")
        listeningStatsTracker.markEndReason(com.theveloper.pixelplay.data.MixEndReason.DISLIKE)
        dailyMixStateHolder.dislike(song.copy(youtubeId = song.youtubeId ?: sourceId))
        viewModelScope.launch { _toastEvents.emit("Won't play again in this mix. Undo from the broken-heart button.") }
        skipRejectedCurrentSong()
    }

    private fun skipRejectedCurrentSong() {
        val controller = mediaController ?: return
        val index = controller.currentMediaItemIndex
        // Remove queued aliases too, including a batch fetched before this feedback.
        val rejectedIds = _playerUiState.value.currentPlaybackQueue
            .filter { dailyMixStateHolder.isDisliked(it) }.map { it.id }.toSet()
        for (i in controller.mediaItemCount - 1 downTo index + 1) {
            if (com.theveloper.pixelplay.data.MixQueueMetadata.automatic(controller.getMediaItemAt(i)) && controller.getMediaItemAt(i).mediaId in rejectedIds) controller.removeMediaItem(i)
        }
        if (index + 1 < controller.mediaItemCount) controller.seekTo(index + 1, 0L)
        else {
            continuousMixRuntime.resumeWhenRefilled(controller.playWhenReady)
            controller.pause()
        }
        if (index in 0 until controller.mediaItemCount) controller.removeMediaItem(index)
        refreshAutomaticMixFuture()
    }

    fun nextSong() {
        listeningStatsTracker.markEndReason(com.theveloper.pixelplay.data.MixEndReason.SKIP)
        playbackStateHolder.nextSong()
    }

    fun previousSong() {
        playbackStateHolder.previousSong()
    }

    private fun startProgressUpdates() {
        playbackStateHolder.startProgressUpdates()
    }

    private fun stopProgressUpdates() {
        playbackStateHolder.stopProgressUpdates()
    }

    fun observeSongs(songIds: List<String>): Flow<List<Song>> {
        return musicRepository.getSongsByIds(songIds)
    }

    fun searchSongs(query: String): Flow<List<Song>> {
        return musicRepository.searchSongs(query)
    }

    suspend fun getSongs(songIds: List<String>) : List<Song>{
        return musicRepository.getSongsByIds(songIds).first()
    }

    //Sorting
    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortSongs(sortOption, persist)
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortAlbums(sortOption, persist)
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortArtists(sortOption, persist)
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortFavoriteSongs(sortOption, persist)
    }

    fun sortFolders(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortFolders(sortOption, persist)
    }

    fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFoldersPlaylistView(isPlaylistView)
            folderNavigationStateHolder.setFoldersPlaylistViewState(
                isPlaylistView = isPlaylistView,
                updateUiState = { mutation -> _playerUiState.update(mutation) }
            )
        }
    }

    fun setFoldersSource(source: FolderSource) {
        if (!ENABLE_FOLDERS_SOURCE_SWITCHING) return
        viewModelScope.launch {
            userPreferencesRepository.setFoldersSource(source)
        }
    }

    fun navigateToFolder(path: String) {
        folderNavigationStateHolder.navigateToFolder(
            path = path,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            onFolderChanged = { folderPath ->
                folderNavigationStateHolder.hydrateCurrentFolderSongsIfNeeded(
                    scope = viewModelScope,
                    folderPath = folderPath,
                    getUiState = { _playerUiState.value },
                    updateUiState = { mutation -> _playerUiState.update(mutation) },
                    requiresHydration = { song -> playbackDispatchStateHolder.songRequiresHydration(song) },
                    hydrateSongs = { songs -> playbackDispatchStateHolder.hydrateSongsIfNeeded(songs) }
                )
            }
        )
    }

    fun navigateBackFolder() {
        folderNavigationStateHolder.navigateBackFolder(
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            onFolderChanged = { folderPath ->
                folderNavigationStateHolder.hydrateCurrentFolderSongsIfNeeded(
                    scope = viewModelScope,
                    folderPath = folderPath,
                    getUiState = { _playerUiState.value },
                    updateUiState = { mutation -> _playerUiState.update(mutation) },
                    requiresHydration = { song -> playbackDispatchStateHolder.songRequiresHydration(song) },
                    hydrateSongs = { songs -> playbackDispatchStateHolder.hydrateSongsIfNeeded(songs) }
                )
            }
        )
    }

    fun setAlbumsListView(isList: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumsListView(isList)
        }
    }

    val searchError = searchStateHolder.searchError

    fun updateSearchFilter(filterType: SearchFilterType) {
        searchStateHolder.updateSearchFilter(filterType)
    }

    /** Touch-down on an online song row: its stream work starts before the tap lands. */
    fun onSongPressed(song: Song) = searchStateHolder.onSongPressed(song)

    fun onSongPressCancelled(song: Song) = searchStateHolder.onSongPressCancelled(song)

    fun loadSearchHistory(limit: Int = 15) {
        searchStateHolder.loadSearchHistory(limit)
    }

    fun onSearchQuerySubmitted(query: String) {
        searchStateHolder.onSearchQuerySubmitted(query)
    }

    fun performSearch(query: String) {
        searchStateHolder.performSearch(query)
    }

    fun triggerGenreCategorization() {
        viewModelScope.launch {
            genreCategorizerEngine.categorizeSongsIfNeeded()
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        searchStateHolder.deleteSearchHistoryItem(query)
    }

    fun clearSearchHistory() {
        searchStateHolder.clearSearchHistory()
    }

    // --- AI Playlist Generation ---

    // --- AI Playlist Generation ---

    fun showAiPlaylistSheet() {
        aiStateHolder.showAiPlaylistSheet()
    }

    fun dismissAiPlaylistSheet() {
        aiStateHolder.dismissAiPlaylistSheet()
    }

    fun clearAiPlaylistError() {
        aiStateHolder.clearAiPlaylistError()
    }

    fun generateAiPlaylist(
        prompt: String,
        minLength: Int,
        maxLength: Int,
        saveAsPlaylist: Boolean = false,
        playlistName: String? = null
    ) {
        aiStateHolder.generateAiPlaylist(
            prompt = prompt,
            minLength = minLength,
            maxLength = maxLength,
            saveAsPlaylist = saveAsPlaylist,
            playlistName = playlistName
        )
    }

    fun regenerateDailyMixWithPrompt(prompt: String) {
        aiStateHolder.regenerateDailyMixWithPrompt(prompt)
    }

    fun retryLastPlaylistGeneration() {
        aiStateHolder.retryLastPlaylistGeneration()
    }

    fun clearQueueExceptCurrent() {
        mediaController?.let { controller ->
            val currentSongIndex = controller.currentMediaItemIndex
            if (currentSongIndex == C.INDEX_UNSET) return@let
            val indicesToRemove = (0 until controller.mediaItemCount)
                .filter { it != currentSongIndex }
                .sortedDescending()

            for (index in indicesToRemove) {
                controller.removeMediaItem(index)
            }
        }
    }

    fun selectRoute(route: MediaRouter.RouteInfo) {
        castRouteStateHolder.selectRoute(route) { message ->
            viewModelScope.launch { _toastEvents.emit(message) }
        }
    }

    fun disconnect(resetConnecting: Boolean = true) {
        castRouteStateHolder.disconnect(resetConnecting = resetConnecting)
    }

    fun setRouteVolume(volume: Int) {
        castRouteStateHolder.setRouteVolume(volume)
    }

    fun refreshCastRoutes() {
        castRouteStateHolder.refreshCastRoutes(viewModelScope)
    }



    override fun onCleared() {
        val controllerToRelease = mediaController
        mediaControllerSyncStateHolder.clearMediaControllerPlaybackListeners(controllerToRelease)
        playbackStateHolder.clearMediaController(controllerToRelease)
        controllerToRelease?.release()
        mediaController = null
        mediaControllerFuture.cancel(true)
        super.onCleared()
        playbackDispatchStateHolder.onCleared()
        castSongUiSyncJob?.cancel()
        stopProgressUpdates()
        playbackStateHolder.onCleared()
        dailyMixStateHolder.onCleared()
        lyricsStateHolder.onCleared()
        themeStateHolder.onCleared()
        castTransferStateHolder.onCleared()
        castStateHolder.onCleared()
        searchStateHolder.onCleared()
        aiStateHolder.onCleared()
        libraryStateHolder.onCleared(viewModelScope)
        sleepTimerStateHolder.onCleared()
        connectivityStateHolder.onCleared()
        queueUndoStateHolder.onCleared()
        playlistDismissUndoStateHolder.onCleared()
    }

    // Sleep Timer Control Functions - delegated to SleepTimerStateHolder
    fun setSleepTimer(durationMinutes: Int) {
        sleepTimerStateHolder.setSleepTimer(durationMinutes)
    }

    fun playCounted(count: Int) {
        sleepTimerStateHolder.playCounted(count)
    }

    fun cancelCountedPlay() {
        sleepTimerStateHolder.cancelCountedPlay()
    }

    fun setEndOfTrackTimer(enable: Boolean) {
        val currentSongId = stablePlayerState.value.currentSong?.id
        sleepTimerStateHolder.setEndOfTrackTimer(enable, currentSongId)
    }

    fun cancelSleepTimer(overrideToastMessage: String? = null, suppressDefaultToast: Boolean = false) {
        sleepTimerStateHolder.cancelSleepTimer(overrideToastMessage, suppressDefaultToast)
    }

    fun dismissPlaylistAndShowUndo() {
        setMiniPlayerDismissing(false)
        playlistDismissUndoStateHolder.dismissPlaylistAndShowUndo(
            scope = viewModelScope,
            currentSong = playbackStateHolder.stablePlayerState.value.currentSong,
            queue = _playerUiState.value.currentPlaybackQueue,
            queueName = _playerUiState.value.currentQueueSourceName,
            position = playbackStateHolder.currentPosition.value,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            disconnectRemoteIfNeeded = {
                val hasCastSession = castStateHolder.castSession.value != null
                val shouldDisconnectRemote = hasCastSession ||
                    castStateHolder.isRemotePlaybackActive.value ||
                    castStateHolder.isCastConnecting.value
                if (shouldDisconnectRemote) {
                    if (hasCastSession) {
                        castTransferStateHolder.skipNextTransferBack()
                    }
                    disconnect()
                }
            },
            clearPlayback = {
                mediaController?.stop()
                mediaController?.clearMediaItems()
            },
            clearStablePlaybackState = {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        playWhenReady = false,
                        totalDuration = 0L
                    )
                }
            },
            setCurrentPosition = { playbackStateHolder.setCurrentPosition(it) },
            setSheetVisible = { _isSheetVisible.value = it }
        )
    }

    fun hideDismissUndoBar() {
        playlistDismissUndoStateHolder.hideDismissUndoBar { mutation ->
            _playerUiState.update(mutation)
        }
    }

    fun undoDismissPlaylist() {
        setMiniPlayerDismissing(false)
        playlistDismissUndoStateHolder.undoDismissPlaylist(
            scope = viewModelScope,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            playSongs = { songs, startSong, queueName ->
                playSongs(songs, startSong, queueName)
            },
            seekTo = { position -> mediaController?.seekTo(position) },
            setSheetVisible = { _isSheetVisible.value = it },
            setSheetCollapsed = { _sheetState.value = PlayerSheetState.COLLAPSED },
            emitToast = { message -> _toastEvents.emit(message) }
        )
    }

    fun getSongUrisForGenre(genreId: String): Flow<List<String>> {
        return musicRepository.getMusicByGenre(genreId).map { songs ->
            songs.take(4).mapNotNull { it.albumArtUriString?.takeIf { uri -> uri.isNotBlank() } }
        }
    }

    fun saveLastLibraryTabIndex(tabIndex: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveLastLibraryTabIndex(tabIndex)
        }
    }

    fun showSortingSheet() {
        libraryTabsStateHolder.showSortingSheet(_isSortingSheetVisible)
    }

    fun hideSortingSheet() {
        libraryTabsStateHolder.hideSortingSheet(_isSortingSheetVisible)
    }

    fun onLibraryTabSelected(tabIndex: Int) {
        libraryTabsStateHolder.onLibraryTabSelected(
            tabIndex = tabIndex,
            libraryTabs = libraryTabsFlow.value,
            loadedTabs = _loadedTabs,
            currentLibraryTabId = _currentLibraryTabId,
            saveLastTabIndex = { index -> userPreferencesRepository.saveLastLibraryTabIndex(index) },
            scope = viewModelScope,
            loadSongs = { loadSongsIfNeeded() },
            loadAlbums = { loadAlbumsIfNeeded() },
            loadArtists = { loadArtistsIfNeeded() },
            loadFolders = { loadFoldersFromRepository() }
        )
    }

    private val _pendingLibraryTab = MutableStateFlow<String?>(null)
    /** A Library tab another screen asked to open ("GENRES", "ARTIST"…); consumed by LibraryScreen. */
    val pendingLibraryTab: StateFlow<String?> = _pendingLibraryTab.asStateFlow()

    /** Opens [tabKey] the next time the Library shows (also remembered as the last tab). */
    fun requestLibraryTab(tabKey: String) {
        val index = libraryTabsFlow.value.indexOf(tabKey)
        if (index >= 0) viewModelScope.launch { userPreferencesRepository.saveLastLibraryTabIndex(index) }
        _pendingLibraryTab.value = tabKey
    }

    fun consumePendingLibraryTab() {
        _pendingLibraryTab.value = null
    }

    fun saveLibraryTabsOrder(tabs: List<String>) {
        viewModelScope.launch {
            val orderJson = Json.encodeToString(tabs)
            userPreferencesRepository.saveLibraryTabsOrder(orderJson)
        }
    }

    fun resetLibraryTabsOrder() {
        viewModelScope.launch {
            userPreferencesRepository.resetLibraryTabsOrder()
        }
    }

    fun selectSongForInfo(song: Song) {
        _selectedSongForInfo.value = song
        viewModelScope.launch {
            val hydrated = withContext(Dispatchers.IO) {
                musicRepository.getSong(song.id).first()
            } ?: return@launch
            if (_selectedSongForInfo.value?.id == song.id) {
                _selectedSongForInfo.value = hydrated
            }
        }
    }

    private fun loadLyricsForCurrentSong() {
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong ?: return
        // Delegate to LyricsStateHolder
        lyricsStateHolder.loadLyricsForSong(currentSong, lyricsSourcePreference.value)
    }

    fun saveBatchMetadata(
        songs: List<Song>,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
        composer: String?,
        genre: String?,
        lyrics: String?,
        trackNumber: Int?,
        discNumber: Int?,
        replayGainTrackGainDb: String?,
        replayGainAlbumGainDb: String?,
        coverArtUpdate: CoverArtUpdate?
    ) = metadataEditStateHolder.saveBatchMetadata(
        songs, title, artist, album, albumArtist, composer, genre, lyrics,
        trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate,
        metadataEditCallbacks()
    )

    fun editSongMetadata(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newAlbumArtist: String,
        newComposer: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        newReplayGainTrackGainDb: String? = null,
        newReplayGainAlbumGainDb: String? = null,
        coverArtUpdate: CoverArtUpdate?,
    ) = metadataEditStateHolder.editSongMetadata(
        song, newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics,
        newTrackNumber, newDiscNumber, newReplayGainTrackGainDb, newReplayGainAlbumGainDb, coverArtUpdate,
        metadataEditCallbacks()
    )

    /** Called from the UI after the user approves or denies the MediaStore write permission. */
    fun onWritePermissionResult(granted: Boolean) =
        metadataEditStateHolder.onWritePermissionResult(granted, metadataEditCallbacks())

    fun saveLyricsToFile(song: Song, lyrics: Lyrics, preferSynced: Boolean) =
        metadataEditStateHolder.saveLyricsToFile(song, lyrics, preferSynced, metadataEditCallbacks())

    suspend fun forceRegenerateAlbumPaletteForSong(song: Song): Boolean {
        val albumArtUri = song.albumArtUriString?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            // Full reset: clear all cached variants for this URI and recreate every style from scratch.
            themeStateHolder.forceRegenerateColorScheme(
                uriString = albumArtUri,
                regenerateAllStyles = true
            )
            true
        }.getOrDefault(false)
    }

    private fun updateSongInStates(
        updatedSong: Song,
        newLyrics: Lyrics? = null,
        isLoadingLyrics: Boolean? = null
    ) {
        // Update the queue first
        val currentQueue = _playerUiState.value.currentPlaybackQueue
        val updatedQueue = currentQueue.replaceSong(updatedSong)

        if (updatedQueue !== currentQueue) {
            _playerUiState.update { it.copy(currentPlaybackQueue = updatedQueue) }
        }

        // Then, update the stable state
        playbackStateHolder.updateStablePlayerState { state ->
            // Only update lyrics if they are explicitly passed
            val finalLyrics = newLyrics?.let {
                com.theveloper.pixelplay.utils.LyricsCleanup.clean(it, updatedSong.title, updatedSong.artist)
            } ?: state.lyrics
            state.copy(
                currentSong = updatedSong,
                lyrics = if (state.currentSong?.id == updatedSong.id) finalLyrics else state.lyrics,
                isLoadingLyrics = isLoadingLyrics ?: state.isLoadingLyrics
            )
        }
    }

    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    fun fetchLyricsForCurrentSong(forcePickResults: Boolean = false) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.fetchLyricsForSong(currentSong, forcePickResults, lyricsSourcePreference.value) { resId ->
            context.getString(resId)
        }
    }

    /**
     * Manual search lyrics using query provided by user (title and artist)
     */
    fun searchLyricsManually(title: String, artist: String? = null) {
        lyricsStateHolder.searchLyricsManually(title, artist)
    }

    fun acceptLyricsSearchResultForCurrentSong(result: LyricsSearchResult) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.acceptLyricsSearchResult(result, currentSong)
    }

    fun resetLyricsForCurrentSong() {
        val songId = stablePlayerState.value.currentSong?.id?.toLongOrNull() ?: return
        lyricsStateHolder.resetLyrics(songId)
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    fun resetAllLyrics() {
        lyricsStateHolder.resetAllLyrics()
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    /**
     * Procesa la letra importada de un archivo, la guarda y actualiza la UI.
     * @param songId El ID de la canción para la que se importa la letra.
     * @param lyricsContent El contenido de la letra como String.
     */
    fun importLyricsFromFile(songId: Long, validatedImport: ValidatedLyricsImport) {
        val currentSong = stablePlayerState.value.currentSong
        lyricsStateHolder.importLyricsFromFile(songId, validatedImport, currentSong)
    }

    fun translateLyricsViaAi() {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.translateLyricsViaAi(
            currentSong = currentSong,
            lyricsObj = stablePlayerState.value.lyrics,
            cb = LyricsTranslationCallbacks(
                translate = { rawLyrics -> aiStateHolder.translateLyrics(rawLyrics) },
                getString = { resId -> context.getString(resId) },
                getErrorString = { detail -> context.getString(R.string.ai_state_error_generic, detail) }
            )
        )
    }

    /**
     * Resetea el estado de la búsqueda de letras a Idle.
     */
    fun resetLyricsSearchState() {
        lyricsStateHolder.resetSearchState()
    }

    private fun onBlockedDirectoriesChanged() {
        viewModelScope.launch {
            musicRepository.invalidateCachesDependentOnAllowedDirectories()
            resetAndLoadInitialData("Blocked directories changed")
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            val controller = mediaController ?: return@launch
            val mediaItem = playbackDispatchStateHolder.buildResolvedPlaybackMediaItem(song)

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()

            _isSheetVisible.value = true
            _sheetState.value = PlayerSheetState.EXPANDED
        }
    }

    fun prepareBenchmarkPlayerFromLibrary() {
        viewModelScope.launch {
            repeat(90) { attempt ->
                val controllerReady = mediaController != null
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getAllSongsOnce()
                }
                Log.i(
                    "PixelPlayBenchmark",
                    "prepare player attempt=$attempt controllerReady=$controllerReady songs=${songs.size}"
                )
                if (controllerReady && songs.isNotEmpty()) {
                    playSongs(songs, songs.first(), "Benchmark Player")
                    delay(700L)
                    collapsePlayerSheet()
                    Log.i("PixelPlayBenchmark", "Benchmark player prepared with ${songs.first().title}")
                    return@launch
                }
                delay(500L)
            }
            Log.w("PixelPlayBenchmark", "Unable to prepare benchmark player from library")
        }
    }

    fun batchEditGenre(songs: List<Song>, newGenre: String) =
        metadataEditStateHolder.batchEditGenre(songs, newGenre, metadataEditCallbacks())

    // Custom Genres Names
    val customGenres: StateFlow<Set<String>> = userPreferencesRepository.customGenresFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val customGenreIcons: StateFlow<Map<String, Int>> = userPreferencesRepository.customGenreIconsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val isGenreGridView: StateFlow<Boolean> = userPreferencesRepository.isGenreGridViewFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun toggleGenreViewMode() {
        viewModelScope.launch {
            userPreferencesRepository.setGenreGridView(!isGenreGridView.value)
        }
    }

    fun addCustomGenre(genre: String, iconResId: Int? = null) {
        viewModelScope.launch {
            userPreferencesRepository.addCustomGenre(genre, iconResId)
        }
    }
}

internal fun Song.withRepositoryHydration(repositorySong: Song): Song {
    if (id != repositorySong.id) return this

    val hydratedArtworkUri = when {
        repositorySong.albumArtUriString.isNullOrBlank() -> albumArtUriString
        albumArtUriString.isNullOrBlank() -> repositorySong.albumArtUriString
        areEquivalentArtworkUrisForSong(id, albumArtUriString, repositorySong.albumArtUriString) ->
            albumArtUriString
        else -> repositorySong.albumArtUriString
    }

    return repositorySong.copy(
        contentUriString = repositorySong.contentUriString.ifBlank { contentUriString },
        albumArtUriString = hydratedArtworkUri,
        duration = repositorySong.duration.takeIf { it > 0L } ?: duration,
        lyrics = repositorySong.lyrics ?: lyrics
    )
}

internal fun areEquivalentArtworkUrisForSong(
    songId: String,
    firstUri: String?,
    secondUri: String?
): Boolean {
    if (firstUri == secondUri) return true
    if (firstUri.isNullOrBlank() || secondUri.isNullOrBlank()) return false

    val targetSongId = songId.toLongOrNull() ?: return false

    fun resolveUriSongId(uri: String): Long? {
        return LocalArtworkUri.parseSongId(uri)
            ?: SharedArtworkContentProvider.parseSongId(uri)
    }

    val firstSongId = resolveUriSongId(firstUri)
    val secondSongId = resolveUriSongId(secondUri)
    return firstSongId == targetSongId && secondSongId == targetSongId
}

/** Songs ahead of the current one whose lyrics are prefetched. */
private const val LYRICS_PREFETCH_AHEAD = 3

/** Wait after a track change before prefetching, so skip bursts don't trigger lookups. */
private const val LYRICS_PREFETCH_SETTLE_MS = 2_500L

internal fun Song.improvesLyricsLookupComparedTo(previousSong: Song): Boolean {
    return (previousSong.lyrics.isNullOrBlank() && !lyrics.isNullOrBlank()) ||
        (previousSong.path.isBlank() && path.isNotBlank()) ||
        (previousSong.contentUriString.isBlank() && contentUriString.isNotBlank())
}

internal fun parsePersistedLyrics(rawLyrics: String?): Lyrics? {
    val normalizedLyrics = rawLyrics?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parsedLyrics = LyricsUtils.parseLyrics(normalizedLyrics)
    return parsedLyrics.takeIf {
        !it.synced.isNullOrEmpty() || !it.plain.isNullOrEmpty()
    }
}






/**
 * Library tabs that are shown. Songs and Liked are no longer standalone tabs — they live as
 * pinned system playlists ("All songs" and "Liked") at the top of the Playlists tab.
 */
internal val DEFAULT_LIBRARY_TABS: List<String> = listOf("PLAYLISTS", "ALBUMS", "ARTIST", "GENRES", "FOLDERS")

/**
 * Keeps the user's stored order for the visible tabs, drops removed/unknown tabs (SONGS, LIKED)
 * and appends any visible tab that is missing from the stored order.
 */
internal fun normalizeLibraryTabs(storedOrder: List<String>): List<String> {
    val ordered = ArrayList<String>()
    storedOrder.filter { it in DEFAULT_LIBRARY_TABS }.forEach { if (it !in ordered) ordered.add(it) }
    // Genres is new (moved from Search): place it right after Artists for existing orders.
    if ("GENRES" !in ordered) {
        val artistIndex = ordered.indexOf("ARTIST")
        if (artistIndex >= 0) ordered.add(artistIndex + 1, "GENRES")
    }
    DEFAULT_LIBRARY_TABS.forEach { if (it !in ordered) ordered.add(it) }
    return ordered
}

/** How many previously played songs the queue's history keeps. */
private const val PLAY_HISTORY_MAX = 50
