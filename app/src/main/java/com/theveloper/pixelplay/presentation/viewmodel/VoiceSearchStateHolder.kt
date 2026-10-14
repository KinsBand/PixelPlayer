package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.FavoritesEntity
import com.theveloper.pixelplay.data.model.HeardSongItem
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.recognition.GsaResultParser
import com.theveloper.pixelplay.data.recognition.RecentlyHeardEntry
import com.theveloper.pixelplay.data.recognition.RecentlyHeardRepository
import com.theveloper.pixelplay.data.recognition.RecentlyHeardSource
import com.theveloper.pixelplay.data.recognition.SongSearchDelivery
import com.theveloper.pixelplay.data.recognition.SongSearchResultReader
import com.theveloper.pixelplay.data.recognition.VoiceSearchMode
import com.theveloper.pixelplay.data.recognition.VoiceSearchResolver
import com.theveloper.pixelplay.data.recognition.shizuku.GoogleSearchBridge
import com.theveloper.pixelplay.data.recognition.shizuku.GsaDumpPoller
import com.theveloper.pixelplay.data.recognition.shizuku.PixelNowPlayingBridge
import com.theveloper.pixelplay.data.recognition.shizuku.ShizukuManager
import com.theveloper.pixelplay.data.recognition.shizuku.ShizukuStatus
import com.theveloper.pixelplay.data.repository.HeardSongsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * A Google song search (Listen / Hum) PixelPlayer just opened, whose result page may be read by
 * [SongSearchResultReader] (or, via Shizuku, [GsaDumpPoller]) until [deadlineElapsed].
 */
data class ArmedSearch(
    val mode: VoiceSearchMode,
    val launchedAtElapsed: Long,
    val deadlineElapsed: Long,
    /** App to bring back once the song is read; null = unknown, the user stays on Google. */
    val returnPackage: String?
)

@Singleton
class VoiceSearchStateHolder @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val gsaDumpPoller: GsaDumpPoller,
    private val shizukuManager: ShizukuManager,
    private val googleSearchBridge: GoogleSearchBridge,
    private val pixelNowPlayingBridge: PixelNowPlayingBridge,
    private val heardSongsRepository: HeardSongsRepository,
    private val voiceSearchResolver: VoiceSearchResolver,
    private val favoritesDao: FavoritesDao,
    private val recentlyHeardRepository: RecentlyHeardRepository,
    private val ambientLyricsSync: com.theveloper.pixelplay.data.recognition.livesync.AmbientLyricsSync,
) {
    /** Live lyrics for the recognised song, following the music playing nearby. */
    val liveLyrics: StateFlow<com.theveloper.pixelplay.data.recognition.livesync.LiveLyricsState> = ambientLyricsSync.state

    /**
     * Starts following the music for the recognised song (only when it has synced lyrics).
     * Call while the sheet is on screen: the microphone needs the app in the foreground.
     */
    fun startLiveLyrics() {
        val song = _recognizedSong.value ?: return
        if (_syncedLyrics.value?.synced.isNullOrEmpty()) return
        val current = ambientLyricsSync.state.value
        if (current is com.theveloper.pixelplay.data.recognition.livesync.LiveLyricsState.Syncing ||
            current is com.theveloper.pixelplay.data.recognition.livesync.LiveLyricsState.Live
        ) return
        ambientLyricsSync.start(song)
    }

    fun stopLiveLyrics() = ambientLyricsSync.stop()

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

    private val _pendingExternalSearch = MutableStateFlow<ArmedSearch?>(null)
    /** Non-null while Google's song search result may be read for us (see [ArmedSearch]). */
    val pendingExternalSearch: StateFlow<ArmedSearch?> = _pendingExternalSearch.asStateFlow()
    private var armTimeoutJob: kotlinx.coroutines.Job? = null
    private var dumpPollerJob: kotlinx.coroutines.Job? = null
    /** Song we just delivered from Google ourselves, so its Recently-heard announcement isn't resolved twice. */
    private var externalDeliveredKey: String? = null

    init {
        // A song Now Playing just recognised (its notification) shows as the match card.
        scope.launch {
            recentlyHeardRepository.newlyHeard.collect { entry ->
                val alreadyShown = entry.key == externalDeliveredKey
                externalDeliveredKey = null
                if (alreadyShown) return@collect
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
        disarmExternalSearch()
        listeningJob?.cancel()
        listeningJob = null
        externalLaunchAt = 0L
        _isListening.value = false
        _activeAction.value = null
    }

    private fun beginListening(mode: VoiceSearchMode, block: suspend () -> Unit) {
        cancelResolution()
        disarmExternalSearch()
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
        armExternalSearch(VoiceSearchMode.HUM_AND_SING)
        externalLaunchAt = android.os.SystemClock.elapsedRealtime()
        if (googleSearchBridge.launchHumOrSingSearch().isFailure) disarmExternalSearch()
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
            armExternalSearch(VoiceSearchMode.LISTEN_AND_NOW_PLAYING)
            externalLaunchAt = android.os.SystemClock.elapsedRealtime()
            if (googleSearchBridge.launchSoundSearch().isFailure) disarmExternalSearch()
        }
    }

    /** Kept for older callers. */
    fun triggerGoogleSoundSearch() = triggerNowPlayingListen()

    /**
     * Back from Google's song search: stop showing "listening" — unless its result page is still
     * being read for us (armed and before the deadline); the reader brings the user back itself.
     */
    fun onHostResumed() {
        if (activeExternalSearch() != null) return
        val launchedAt = externalLaunchAt
        if (launchedAt != 0L && android.os.SystemClock.elapsedRealtime() - launchedAt > 1_000L) {
            stopListening()
        }
    }

    /** The armed Google song search, or null when none is armed or its deadline has passed. */
    fun activeExternalSearch(): ArmedSearch? =
        _pendingExternalSearch.value?.takeIf { SystemClock.elapsedRealtime() < it.deadlineElapsed }

    /**
     * Called right before Google's song search opens. Works out where to return afterwards,
     * makes sure the result reader is on (silently with Shizuku), arms, and with Shizuku also
     * starts the `uiautomator dump` poller, which only dumps while the reader isn't running.
     */
    private suspend fun armExternalSearch(mode: VoiceSearchMode) {
        disarmExternalSearch()
        val returnPackage = resolveReturnPackage()
        googleSearchBridge.ensureResultReaderEnabled()
        val now = SystemClock.elapsedRealtime()
        val armed = ArmedSearch(
            mode = mode,
            launchedAtElapsed = now,
            deadlineElapsed = now + EXTERNAL_RESULT_WINDOW_MS,
            returnPackage = returnPackage
        )
        _pendingExternalSearch.value = armed
        armTimeoutJob = scope.launch {
            delay(EXTERNAL_RESULT_WINDOW_MS)
            if (_pendingExternalSearch.value === armed) disarmExternalSearch()
        }
        if (shizukuManager.status.value == ShizukuStatus.READY) startDumpPoller(armed)
    }

    /**
     * Where to send the user once the song is read:
     * - With Shizuku: whichever app is on top right now (before Google opens), from dumpsys —
     *   PixelPlayer itself, or the app the camera island was used over.
     * - Without Shizuku: PixelPlayer when one of its screens is showing (ProcessLifecycleOwner
     *   is STARTED only while one of our activities is visible; the island overlay and services
     *   don't count). Otherwise we can't know the other app, so null: the user stays on
     *   Google's result rather than being sent somewhere guessed.
     */
    private suspend fun resolveReturnPackage(): String? {
        googleSearchBridge.foregroundPackage()
            ?.takeIf { it != SongSearchDelivery.GSA_PACKAGE }
            ?.let { return it }
        val ownScreenShowing = ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
        return if (ownScreenShowing) appContext.packageName else null
    }

    private fun startDumpPoller(armed: ArmedSearch) {
        dumpPollerJob?.cancel()
        dumpPollerJob = scope.launch {
            val confirmer = SongSearchDelivery.Confirmer()
            gsaDumpPoller.poll(
                isActive = { _pendingExternalSearch.value === armed && activeExternalSearch() != null },
                // The accessibility reader is quicker and cheaper; dump only while it isn't running.
                shouldDump = { !SongSearchResultReader.isConnected }
            ) { texts, searchBox ->
                val parsed = GsaResultParser.parse(texts, searchBox)
                when (val step = confirmer.offer(parsed, SystemClock.elapsedRealtime())) {
                    is SongSearchDelivery.Step.Deliver -> SongSearchDelivery.deliver(
                        context = appContext,
                        holder = this@VoiceSearchStateHolder,
                        result = step.result,
                        shell = { command ->
                            shizukuManager.executeShell(command).getOrNull()?.contains("Error") == false
                        }
                    )
                    else -> false
                }
            }
        }
    }

    private fun disarmExternalSearch() {
        _pendingExternalSearch.value = null
        armTimeoutJob?.cancel()
        armTimeoutJob = null
        dumpPollerJob?.cancel()
        dumpPollerJob = null
    }

    /**
     * The song read off Google's result page: saved to Recently heard (announced, so the island
     * and radio see it too), shown as the match, and the search disarmed. Returns the armed
     * search (for its return package), or null when nothing was armed or it had expired.
     */
    fun deliverExternalResult(title: String, artist: String): ArmedSearch? {
        val armed = activeExternalSearch() ?: return null
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isEmpty()) return null
        // Disarm first so the reader and the poller can't both deliver. The poller job isn't
        // cancelled: it may be the caller, about to bring the user back; it stops on its own.
        _pendingExternalSearch.value = null
        armTimeoutJob?.cancel()
        armTimeoutJob = null
        dumpPollerJob = null
        externalDeliveredKey = RecentlyHeardEntry.keyOf(cleanTitle, cleanArtist)
        recentlyHeardRepository.record(
            title = cleanTitle,
            artist = cleanArtist,
            source = RecentlyHeardSource.SONG_SEARCH,
            announce = true
        )
        resolveAndShowSong(cleanTitle, cleanArtist)
        return armed
    }

    /**
     * Only the search box was readable (e.g. "Whirr Color Change"). Delivered only when it
     * names exactly one library song; otherwise nothing is recorded and the search stays armed
     * in case the song panel still loads.
     */
    suspend fun deliverExternalQuery(query: String): ArmedSearch? {
        val armed = activeExternalSearch() ?: return null
        val song = voiceSearchResolver.findConfidentMatch(query) ?: return null
        if (activeExternalSearch() !== armed) return null
        return deliverExternalResult(song.title, song.artist)
    }

    /**
     * Whether the voice sheet should explain the result reader before Listen / Hum: only once,
     * only without Shizuku (which turns it on silently) and only while it's off.
     */
    fun shouldExplainResultReader(): Boolean {
        if (shizukuManager.status.value == ShizukuStatus.READY) return false
        if (SongSearchResultReader.isEnabled(appContext)) return false
        return !voicePrefs().getBoolean(KEY_READER_EXPLAINED, false)
    }

    fun markResultReaderExplained() {
        voicePrefs().edit().putBoolean(KEY_READER_EXPLAINED, true).apply()
    }

    private fun voicePrefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        ambientLyricsSync.stop()
        _recognizedSong.value = null
        _syncedLyrics.value = null
    }

    private companion object {
        const val LISTEN_TIMEOUT_MS = 30_000L
        const val ON_DEVICE_WAIT_MS = 8_000L
        /** How long Google's result page may be read after it opens (listening + loading). */
        const val EXTERNAL_RESULT_WINDOW_MS = 45_000L
        const val PREFS_NAME = "voice_search_prefs"
        const val KEY_READER_EXPLAINED = "song_search_reader_explained"
    }
}
