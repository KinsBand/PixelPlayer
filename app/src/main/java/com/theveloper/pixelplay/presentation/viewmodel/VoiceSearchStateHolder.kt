package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.FavoritesEntity
import com.theveloper.pixelplay.data.model.HeardSongItem
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.recognition.RecentlyHeardRepository
import com.theveloper.pixelplay.data.recognition.VoiceSearchMode
import com.theveloper.pixelplay.data.recognition.VoiceSearchResolver
import com.theveloper.pixelplay.data.recognition.shizuku.GoogleSearchBridge
import com.theveloper.pixelplay.data.recognition.shizuku.PixelNowPlayingBridge
import com.theveloper.pixelplay.data.recognition.shizuku.ShizukuManager
import com.theveloper.pixelplay.data.recognition.shizuku.ShizukuStatus
import com.theveloper.pixelplay.data.repository.HeardSongsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceSearchStateHolder @Inject constructor(
    private val shizukuManager: ShizukuManager,
    private val googleSearchBridge: GoogleSearchBridge,
    private val pixelNowPlayingBridge: PixelNowPlayingBridge,
    private val heardSongsRepository: HeardSongsRepository,
    private val voiceSearchResolver: VoiceSearchResolver,
    private val favoritesDao: FavoritesDao,
    private val recentlyHeardRepository: RecentlyHeardRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var resolutionJob: kotlinx.coroutines.Job? = null
    @Volatile private var resolutionGeneration = 0L

    private fun cancelResolution() {
        resolutionGeneration++
        resolutionJob?.cancel()
        resolutionJob = null
    }

    private val _isSheetOpen = MutableStateFlow(false)
    val isSheetOpen: StateFlow<Boolean> = _isSheetOpen.asStateFlow()

    private val _currentMode = MutableStateFlow(VoiceSearchMode.LISTEN_AND_NOW_PLAYING)
    val currentMode: StateFlow<VoiceSearchMode> = _currentMode.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedSong = MutableStateFlow<Song?>(null)
    val recognizedSong: StateFlow<Song?> = _recognizedSong.asStateFlow()

    private val _syncedLyrics = MutableStateFlow<Lyrics?>(null)
    val syncedLyrics: StateFlow<Lyrics?> = _syncedLyrics.asStateFlow()

    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuManager.status

    /** Which of the two sheet buttons is working right now (null = idle). */
    private val _activeAction = MutableStateFlow<VoiceSearchMode?>(null)
    val activeAction: StateFlow<VoiceSearchMode?> = _activeAction.asStateFlow()

    private var listeningJob: kotlinx.coroutines.Job? = null
    /** Elapsed-realtime when another app (Google song search) was opened for us. */
    private var externalLaunchAt = 0L

    init {
        // A song Now Playing just recognised (its notification) shows as the match card.
        scope.launch {
            recentlyHeardRepository.newlyHeard.collect { entry ->
                if (_isSheetOpen.value && _recognizedSong.value == null) {
                    resolveAndShowSong(entry.title, entry.artist)
                }
            }
        }
    }

    fun openSheet(initialMode: VoiceSearchMode = VoiceSearchMode.LISTEN_AND_NOW_PLAYING) {
        cancelResolution()
        stopListening()
        _isSheetOpen.value = true
        _currentMode.value = initialMode
        _recognizedSong.value = null
        _syncedLyrics.value = null
        // Import Now Playing's own history into Recently heard (Shizuku only; no-op otherwise).
        scope.launch { pixelNowPlayingBridge.fetchNowPlayingHistory() }
    }

    fun closeSheet() {
        cancelResolution()
        stopListening()
        _isSheetOpen.value = false
        _recognizedSong.value = null
        _syncedLyrics.value = null
    }

    fun switchMode(mode: VoiceSearchMode) {
        cancelResolution()
        _currentMode.value = mode
        _recognizedSong.value = null
        _syncedLyrics.value = null
    }

    private fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        externalLaunchAt = 0L
        _isListening.value = false
        _activeAction.value = null
    }

    private fun beginListening(mode: VoiceSearchMode, block: suspend () -> Unit) {
        cancelResolution()
        listeningJob?.cancel()
        _currentMode.value = mode
        _recognizedSong.value = null
        _syncedLyrics.value = null
        _isListening.value = true
        _activeAction.value = mode
        listeningJob = scope.launch {
            try {
                block()
                // Never stay stuck on "listening": give a result up to 30 s to arrive.
                delay(LISTEN_TIMEOUT_MS)
            } finally {
                if (_activeAction.value == mode) {
                    _isListening.value = false
                    _activeAction.value = null
                }
            }
        }
    }

    /** Hum & sing: Google's "Search a song" (hum, whistle or sing). */
    fun triggerGoogleHumOrSing() = beginListening(VoiceSearchMode.HUM_AND_SING) {
        externalLaunchAt = android.os.SystemClock.elapsedRealtime()
        googleSearchBridge.launchHumOrSingSearch()
    }

    /**
     * Listen: Android's Now Playing. With Shizuku it asks Now Playing to listen on demand and
     * waits for its notification; otherwise (or if nothing arrives) Google's song search — the
     * same engine Now Playing's own "Search" uses — is opened. Results from Now Playing are
     * captured from its notification either way, so notification access is checked first.
     */
    fun triggerNowPlayingListen() = beginListening(VoiceSearchMode.LISTEN_AND_NOW_PLAYING) {
        if (!googleSearchBridge.ensureNotificationListenerAccess()) {
            // Android's notification-access screen was opened; coming back resets the button.
            externalLaunchAt = android.os.SystemClock.elapsedRealtime()
            return@beginListening
        }
        val heardOnDevice = if (pixelNowPlayingBridge.triggerAmbientDetection()) {
            withTimeoutOrNull(ON_DEVICE_WAIT_MS) { recentlyHeardRepository.newlyHeard.first() }
        } else null
        if (heardOnDevice == null) {
            externalLaunchAt = android.os.SystemClock.elapsedRealtime()
            googleSearchBridge.launchSoundSearch()
        }
    }

    /** Kept for older callers. */
    fun triggerGoogleSoundSearch() = triggerNowPlayingListen()

    /** Back from Google's song search: stop showing "listening" (its result can't come back to us). */
    fun onHostResumed() {
        val launchedAt = externalLaunchAt
        if (launchedAt != 0L && android.os.SystemClock.elapsedRealtime() - launchedAt > 1_000L) {
            stopListening()
        }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun selectSong(song: Song) {
        resolveAndShowSong(song.title, song.artist)
    }

    fun resolveAndShowSong(title: String, artist: String) {
        cancelResolution()
        val generation = resolutionGeneration
        resolutionJob = scope.launch {
            listeningJob?.cancel()
            _isListening.value = false
            _activeAction.value = null
            val (resolved, lyrics) = voiceSearchResolver.resolve(title, artist)
            if (generation != resolutionGeneration) return@launch
            _recognizedSong.value = resolved
            _syncedLyrics.value = lyrics
        }
    }

    /** The spoken request with command words removed, e.g. "play X by Y please" -> "X Y". */
    fun cleanSpokenQuery(transcript: String): String {
        val (title, artist) = parseSpokenQuery(transcript)
        return listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun parseSpokenQuery(raw: String): Pair<String, String> {
        var text = raw.trim().trimEnd('.', '!', '?')
        val leading = Regex(
            "^(hey\\s+)?(please\\s+)?(can you\\s+|could you\\s+)?(play|find|search for|search|put on|look up|i want to hear|i want|queue|show me)\\s+",
            RegexOption.IGNORE_CASE
        )
        text = text.replace(leading, "")
        text = text.replace(Regex("\\s+please$", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("^the song\\s+", RegexOption.IGNORE_CASE), "")
        val byIndex = text.lowercase().lastIndexOf(" by ")
        return if (byIndex > 0) {
            text.substring(0, byIndex).trim() to text.substring(byIndex + 4).trim()
        } else {
            text.trim() to ""
        }
    }

    fun toggleFavorite() {
        val current = _recognizedSong.value ?: return
        val newFavState = !current.isFavorite
        _recognizedSong.value = current.copy(isFavorite = newFavState)

        scope.launch(Dispatchers.IO) {
            try {
                if (newFavState) {
                    favoritesDao.setFavorite(
                        FavoritesEntity(
                            songId = current.id,
                            isFavorite = true,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    favoritesDao.removeFavorite(current.id)
                }
            } catch (e: Exception) {
                Timber.e(e, "VoiceSearchStateHolder: Failed to toggle favorite for %s", current.id)
            }
        }
    }

    fun clearRecognizedSong() {
        _recognizedSong.value = null
        _syncedLyrics.value = null
    }

    private companion object {
        const val LISTEN_TIMEOUT_MS = 30_000L
        const val ON_DEVICE_WAIT_MS = 8_000L
    }
}
