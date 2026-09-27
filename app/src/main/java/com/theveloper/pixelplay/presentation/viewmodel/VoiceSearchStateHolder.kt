package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.FavoritesEntity
import com.theveloper.pixelplay.data.model.HeardSongItem
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
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
    private val favoritesDao: FavoritesDao
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

    val nowPlayingHistory: StateFlow<List<HeardSongItem>> = heardSongsRepository.heardSongs

    init {
        // Collect latest heard events from Now Playing
        scope.launch {
            heardSongsRepository.latestHeardEvent.collect { item ->
                if (_isSheetOpen.value && _recognizedSong.value == null) {
                    resolveAndShowSong(item.song.title, item.song.artist)
                }
            }
        }
    }

    fun openSheet(initialMode: VoiceSearchMode = VoiceSearchMode.LISTEN_AND_NOW_PLAYING) {
        cancelResolution()
        _isSheetOpen.value = true
        _currentMode.value = initialMode
        _recognizedSong.value = null
        _syncedLyrics.value = null

        // If opening in Listen & Now Playing mode, refresh Now Playing history from Shizuku
        if (initialMode == VoiceSearchMode.LISTEN_AND_NOW_PLAYING) {
            scope.launch {
                pixelNowPlayingBridge.fetchNowPlayingHistory()
            }
        }
    }

    fun closeSheet() {
        cancelResolution()
        _isSheetOpen.value = false
        _isListening.value = false
        _recognizedSong.value = null
        _syncedLyrics.value = null
    }

    fun switchMode(mode: VoiceSearchMode) {
        cancelResolution()
        _currentMode.value = mode
        _recognizedSong.value = null
        _syncedLyrics.value = null

        if (mode == VoiceSearchMode.LISTEN_AND_NOW_PLAYING) {
            scope.launch {
                pixelNowPlayingBridge.fetchNowPlayingHistory()
            }
        }
    }

    fun triggerGoogleHumOrSing() {
        scope.launch {
            _isListening.value = true
            googleSearchBridge.launchHumOrSingSearch()
        }
    }

    fun triggerGoogleSoundSearch() {
        scope.launch {
            _isListening.value = true
            googleSearchBridge.launchSoundSearch()
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
            _isListening.value = false
            val (resolved, lyrics) = voiceSearchResolver.resolve(title, artist)
            if (generation != resolutionGeneration) return@launch
            _recognizedSong.value = resolved
            _syncedLyrics.value = lyrics
        }
    }

    /**
     * Handles a transcript spoken into the sheet ("play Blinding Lights by The Weeknd").
     * Strips the command words, splits "title by artist", and resolves it like any other match.
     */
    fun onSpokenQuery(transcript: String) {
        val (title, artist) = parseSpokenQuery(transcript)
        if (title.isBlank()) return
        resolveAndShowSong(title, artist)
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
}
