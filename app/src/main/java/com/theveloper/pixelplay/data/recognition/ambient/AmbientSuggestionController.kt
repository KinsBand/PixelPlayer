package com.theveloper.pixelplay.data.recognition.ambient

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.HeardSongsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One microphone owner, scoped to the explicitly started microphone foreground service. */
@Singleton
class AmbientSuggestionController @Inject constructor(
    private val speechRecognition: OnDeviceSpeechRecognition,
    private val dualPlayerEngine: DualPlayerEngine,
    private val heardSongsRepository: HeardSongsRepository,
    private val musicRepository: MusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var session: CoroutineScope? = null
    private var recognizer: SpeechRecognizer? = null
    private var watchdog: Job? = null
    private var generation = 0
    private var recognitionAttempt = 0
    private var failures = 0
    private var chimeEnabled = true
    private var currentPlayingSongId: String? = null
    private val resolving = mutableSetOf<String>()
    private val resolutionSlots = Semaphore(2)
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow("Listen is off")
    val status: StateFlow<String> = _status.asStateFlow()

    // Main thread only: SpeechRecognizer requires all lifecycle calls on the main looper.
    fun start(onUnavailable: (String) -> Unit) {
        if (session != null) return
        if (!speechRecognition.isAvailable()) {
            _status.value = "On-device speech recognition is unavailable. Install a speech language model to use Listen."
            onUnavailable(_status.value)
            return
        }
        val active = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        session = active
        val token = ++generation
        failures = 0
        // Listen never lowers the music: the player keeps full volume and ignores the brief
        // audio-focus blips the recognizer causes each time it restarts.
        dualPlayerEngine.setAmbientListenActive(true)
        active.launch {
            userPreferencesRepository.ambientAudioChimeEnabledFlow.collect { chimeEnabled = it }
        }
        beginRecognition(token, onUnavailable)
    }

    private fun beginRecognition(token: Int, onUnavailable: (String) -> Unit) {
        if (token != generation || session == null) return
        val attempt = ++recognitionAttempt
        var finished = false
        fun finish(error: Int? = null, results: Bundle? = null) {
            if (finished || token != generation || attempt != recognitionAttempt) return
            finished = true
            watchdog?.cancel()
            val routineEnd = error == null || error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            failures = if (routineEnd) 0 else failures + 1
            // Always-on: only stop for errors that retrying can never fix. Transient failures
            // (busy recognizer, audio glitches, client errors) back off and keep listening.
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                stop()
                persistEnabled(false)
                _status.value = "Listen stopped: check microphone access and the installed speech language model."
                onUnavailable(_status.value)
                return
            }
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstNotNullOfOrNull { ConversationalIntentFilter.parse(it) }
                ?.let { suggestion ->
                    val key = "${suggestion.cleanTitle}|${suggestion.artist}"
                    if (resolving.size < 8 && resolving.add(key)) session?.launch {
                        try {
                            resolutionSlots.withPermit { resolveSuggestion(suggestion) }
                        } finally { resolving.remove(key) }
                    }
                }
            session?.launch {
                delay(if (routineEnd) 150L else (1_000L shl failures.coerceAtMost(3)))
                beginRecognition(token, onUnavailable)
            }
        }
        try {
            recognizer?.destroy()
            recognizer = speechRecognition.create().apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (!finished && token == generation && attempt == recognitionAttempt) {
                            dualPlayerEngine.markAmbientRecognizerStarting()
                            _status.value = "Listening for song mentions"
                        }
                    }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) = finish(error)
                    override fun onResults(results: Bundle?) = finish(results = results)
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                dualPlayerEngine.markAmbientRecognizerStarting()
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                })
            }
            watchdog = session?.launch {
                delay(30_000)
                finish(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            }
        } catch (e: Exception) {
            Timber.w(e, "Unable to start on-device Listen session")
            finish(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    private suspend fun resolveSuggestion(suggestion: ExtractedSongSuggestion) {
        try {
            val started = System.nanoTime()
            val local = withTimeoutOrNull(750) {
                musicRepository.searchSongs(suggestion.cleanTitle).first()
            }.orEmpty()
            val localMatch = AmbientSongMatcher.bestMatch(local, suggestion)
            val match = localMatch ?: withTimeoutOrNull(8_000) {
                val query = listOfNotNull(suggestion.cleanTitle, suggestion.artist).joinToString(" ")
                AmbientSongMatcher.bestMatch(youTubeRepository.searchSongs(query), suggestion)
            } ?: return
            currentCoroutineContext().ensureActive()
            val added = heardSongsRepository.addOrUpvote(match, localMatch == null,
                suggestion.rawText, currentPlayingSongId)
            Timber.tag("StreamingLatency").d("heard_suggestion_ms=%d local=%b",
                (System.nanoTime() - started) / 1_000_000, localMatch != null)
            if (added && chimeEnabled) {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
                try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100); delay(150) }
                finally { tone.release() }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Timber.w(e, "Could not resolve a spoken song suggestion") }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean, currentSongId: String?) {
        currentPlayingSongId = currentSongId.takeIf { isPlaying }
    }

    fun stop() {
        generation++
        session?.cancel()
        session = null
        watchdog = null
        resolving.clear()
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        dualPlayerEngine.setAmbientListenActive(false)
        _status.value = "Listen is off"
    }

    /** Remembers whether Listen should be on, so it comes back whenever the app is opened. */
    fun persistEnabled(enabled: Boolean) {
        persistScope.launch { userPreferencesRepository.setAmbientSuggestionsEnabled(enabled) }
    }
}
