package com.theveloper.pixelplay.data.service.player

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.flac.FlacExtractor
import com.theveloper.pixelplay.data.diagnostics.PerformanceMetrics
import com.theveloper.pixelplay.data.model.Curve
import com.theveloper.pixelplay.data.model.TransitionMode
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.data.stream.YouTubeStreamProxy
import com.theveloper.pixelplay.data.youtube.downloadedAudioFile
import com.theveloper.pixelplay.utils.envelope
import com.theveloper.pixelplay.utils.fadeOutGain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

import androidx.core.net.toUri
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics

data class ActiveDecoderInfo(
    val name: String,
    val isHardware: Boolean
)

internal fun shouldResumeAfterTransientAudioFocusLoss(
    masterPlayWhenReady: Boolean,
    masterIsPlaying: Boolean,
    transitionRunning: Boolean,
    auxiliaryPlayWhenReady: Boolean,
    auxiliaryIsPlaying: Boolean
): Boolean {
    return masterPlayWhenReady ||
        masterIsPlaying ||
        (transitionRunning && (auxiliaryPlayWhenReady || auxiliaryIsPlaying))
}

internal fun shouldDisableAudioOffloadByDefaultForDevice(
    manufacturer: String,
    brand: String,
    model: String,
    hardware: String,
    sdkInt: Int
): Boolean {
    val manufacturerName = manufacturer.trim().lowercase()
    val brandName = brand.trim().lowercase()
    val modelName = model.trim().lowercase()
    val hardwareName = hardware.trim().lowercase()

    val isXiaomiFamilyDevice = manufacturerName == "xiaomi" ||
        brandName == "xiaomi" ||
        brandName == "redmi" ||
        brandName == "poco"
    if (isXiaomiFamilyDevice && sdkInt >= 36) return true

    // Google Pixel devices on SDK 37+ (Android 16 QPR / 17 preview) exhibit an audio
    // offload HAL bug where the Opus position counter jumps ~49 seconds at a time,
    // causing audible skips and incorrect position restoration on player rebuild.
    val isGooglePixelDevice = manufacturerName == "google" || brandName == "google"
    if (isGooglePixelDevice && sdkInt >= 37) return true

    val isLavaDevice =
        manufacturerName == "lava" ||
            brandName == "lava"
    val looksLikeMtkHardware =
        hardwareName.startsWith("mt") ||
            hardwareName.contains("mediatek") ||
            hardwareName.contains("mtk")
    val isReportedLxxFamily = modelName.startsWith("lxx") && isLavaDevice
    val isMtkLavaVariant = isLavaDevice && looksLikeMtkHardware

    return sdkInt >= 35 && (isReportedLxxFamily || isMtkLavaVariant)
}

internal fun shouldTriggerAudioOffloadStallFallback(
    audioOffloadEnabled: Boolean,
    transitionRunning: Boolean,
    isCurrentMasterPlayer: Boolean,
    mediaIdMatches: Boolean,
    playbackState: Int,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackSuppressionReason: Int
): Boolean {
    return audioOffloadEnabled &&
        !transitionRunning &&
        isCurrentMasterPlayer &&
        mediaIdMatches &&
        playWhenReady &&
        !isPlaying &&
        playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED
}

/**
 * Decides whether an early STATE_BUFFERING (within ~500ms of audio playing) should be read
 * as a HAL offload reset and trigger disabling offload for the session.
 *
 * The buffering is NOT treated as a HAL reset when it is explained by a recent user seek
 * ([isPostSeekBuffering]) or by a just-finished crossfade ([isPostTransitionBuffering]) —
 * in those cases the buffering is expected, and disabling offload would needlessly drop the
 * battery saving and rebuild the player (an audible glitch).
 */
internal fun shouldDisableAudioOffloadOnEarlyBuffering(
    audioOffloadEnabled: Boolean,
    transitionRunning: Boolean,
    lastPlayingAtMs: Long,
    timeSincePlayingMs: Long,
    isPostSeekBuffering: Boolean,
    isPostTransitionBuffering: Boolean,
    isPostMediaItemTransition: Boolean
): Boolean {
    return audioOffloadEnabled &&
        !transitionRunning &&
        lastPlayingAtMs > 0L &&
        timeSincePlayingMs < 500L &&
        !isPostSeekBuffering &&
        !isPostTransitionBuffering &&
        !isPostMediaItemTransition
}

/**
 * Manages two ExoPlayer instances (A and B) to enable seamless transitions.
 *
 * Player A is the designated "master" player. During a crossfade the MediaSession can
 * expose Player B early for UI continuity, while Player A remains alive to fade out.
 * Player B is the auxiliary player used to pre-buffer and fade in the next track.
 * After a transition, Player A adopts the state of Player B, ensuring continuity.
 */
@OptIn(UnstableApi::class)
@Singleton
class DualPlayerEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gdriveStreamProxy: com.theveloper.pixelplay.data.gdrive.GDriveStreamProxy,
    private val youTubeStreamProxy: YouTubeStreamProxy,
    private val cloudSongDao: com.theveloper.pixelplay.data.database.CloudSongDao,
    private val connectivityStateHolder: com.theveloper.pixelplay.presentation.viewmodel.ConnectivityStateHolder,
    private val dspEngineManager: com.theveloper.pixelplay.data.dsp.DspEngineManager,
    private val bluetoothDevicePrefs: com.theveloper.pixelplay.data.connectivity.BluetoothDevicePrefs,
    private val catalogPlaybackResolver: com.theveloper.pixelplay.data.accounts.CatalogPlaybackResolver
) {
    private companion object {
        /** How long after a Listen recognizer restart its focus request is treated as a blip. */
        const val AMBIENT_FOCUS_BLIP_WINDOW_MS = 2_500L
        private const val AUDIO_OFFLOAD_STALL_FALLBACK_MS = 4_000L
        // Grace window after a crossfade/transition during which the STATE_BUFFERING
        // "HAL offload reset" heuristic is suppressed. Right after the player swap the new
        // master (the former auxiliary) has just started, so a brief buffering blip there
        // must NOT be mistaken for a HAL underflow — doing so would disable audio offload
        // for the whole session (losing the battery saving) and rebuild the player (an
        // audible glitch right after the fade). This keeps offload enabled across crossfades.
        private const val POST_TRANSITION_OFFLOAD_GUARD_MS = 2_000L
        private const val MAX_AUXILIARY_TIMELINE_ITEMS = 200
        private const val DOWNLOAD_SWAP_DEBOUNCE_MS = 300L
        /** About 13 s of 160 kbps Opus: enough to cover URL resolution on a slow network. */
        private const val NEXT_SONG_HEAD_BYTES = 256 * 1024
        private const val LOCAL_SWAP_SEEK_WINDOW_MS = 1_000L
        private val LOCAL_MEDIA_SCHEMES = setOf("content", "file", "android.resource")
        // Spotify / Apple Music / Deezer songs are matched to YouTube audio when opened, so a
        // catalog queue can start after matching only its first song.
        private val CATALOG_SCHEMES = com.theveloper.pixelplay.data.accounts.CatalogPlaybackResolver.SCHEMES
        private val REMOTE_MEDIA_SCHEMES = setOf("http", "https", "gdrive", "youtube") + CATALOG_SCHEMES
        // Subset of REMOTE_MEDIA_SCHEMES: schemes that need proxy resolution.
        // http/https resolve directly and must NOT enter the resolvedUriCache lookup path.
        private val CLOUD_PROXY_SCHEMES = setOf("gdrive", "youtube") + CATALOG_SCHEMES
        /** Resolved on every open: downloads, signed URLs and the proxy port can all change. */
        private val UNCACHED_PROXY_SCHEMES = setOf("youtube") + CATALOG_SCHEMES
    }

    data class TransitionTarget(
        val mediaItem: MediaItem,
        val absoluteIndex: Int,
        val queueSize: Int
    )

    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var hiFiModeEnabled: Boolean = false
        private set
    private var audioOffloadEnabled = !shouldDisableAudioOffloadByDefault()
    private var transitionJob: Job? = null
    private var bufferingFallbackJob: Job? = null
    private var transitionRunning = false
    private var streamRecoveryJob: Job? = null
    private var streamRecoveryId: String? = null
    private var streamRecoveryAttempts = 0
    private var preResolutionJob: Job? = null
    private var queueSnapshot: List<MediaItem> = emptyList()
    private var activeWindowStartIndex = 0
    private var activePlayerUsesWindowedQueue = false
    private var preparedWindowStartIndex = 0
    private var preparedPlayerUsesWindowedQueue = false

    private lateinit var playerA: ExoPlayer
    private var playerB: ExoPlayer? = null

    private val onPlayerSwappedListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionDisplayPlayerListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionFinishedListeners = mutableListOf<() -> Unit>()

    private var onPlayerAboutToBeReleasedListener: ((Player) -> Unit)? = null

    fun setOnPlayerAboutToBeReleasedListener(listener: (Player) -> Unit) {
        onPlayerAboutToBeReleasedListener = listener
    }
    
    // Active Audio Session ID Flow
    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    private val _activeDecoderInfo = MutableStateFlow<ActiveDecoderInfo?>(null)
    val activeDecoderInfo: StateFlow<ActiveDecoderInfo?> = _activeDecoderInfo.asStateFlow()

    // Audio Focus Management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isFocusLossPause = false
    private var lastPlayWhenReadyAtMs: Long = 0L
    private var lastPlayingAtMs: Long = 0L
    // Used to distinguish a STATE_BUFFERING caused by a user seek from a real HAL offload
    // reset (where audio underflows mid-playback). Without this, seeking shortly after
    // playback starts re-enters BUFFERING within the HAL-reset window and triggers a full
    // player rebuild, which leaves the MediaSession briefly pointing at the released player
    // and silently drops any subsequent seeks.
    private var lastSeekAtMs: Long = 0L
    // Used to distinguish a STATE_BUFFERING caused by a song transition from a real HAL offload reset.
    private var lastMediaItemTransitionAtMs: Long = 0L
    // Diagnostics: timestamp when the master player entered STATE_BUFFERING, used to
    // measure buffering->ready (playback prepare) durations for the performance report.
    private var bufferingStartedAtMs: Long = 0L
    private var awaitingFirstAudioFor: String? = null
    // Diagnostics: timestamp when the most recent crossfade/transition started.
    private var transitionStartedAtMs: Long = 0L
    // Timestamp when the most recent crossfade/transition finished. Used to give the new
    // master a grace window before the HAL-offload-reset heuristic can fire, so a crossfade
    // can never spuriously disable audio offload (battery) or trigger a player rebuild.
    private var lastTransitionFinishedAtMs: Long = 0L

    /**
     * Whether ExoPlayer audio offload is currently enabled for this session. Exposed
     * read-only for the diagnostic performance report. Offload is disabled at runtime
     * when a HAL stall/reset is detected (see [disableAudioOffloadForSession]).
     */
    val isAudioOffloadEnabled: Boolean
        get() = audioOffloadEnabled

    /** Lightweight, allocation-cheap snapshot of the live audio format, for diagnostics. */
    data class AudioFormatSnapshot(
        val sampleMimeType: String?,
        val sampleRate: Int,
        val channelCount: Int,
        val pcmEncoding: Int,
        val bitrate: Int
    )

    /** Returns the current master-player audio format, or null when nothing is decoding. */
    fun currentAudioFormatSnapshot(): AudioFormatSnapshot? {
        if (!::playerA.isInitialized) return null
        val format = playerA.audioFormat ?: return null
        fun Int.orZero() = if (this == Format.NO_VALUE) 0 else this
        val bitrate = when {
            format.averageBitrate != Format.NO_VALUE -> format.averageBitrate
            format.peakBitrate != Format.NO_VALUE -> format.peakBitrate
            else -> 0
        }
        return AudioFormatSnapshot(
            sampleMimeType = format.sampleMimeType,
            sampleRate = format.sampleRate.orZero(),
            channelCount = format.channelCount.orZero(),
            pcmEncoding = format.pcmEncoding.orZero(),
            bitrate = bitrate
        )
    }

    /**
     * Set by MusicService once ReplayGain for the incoming track is known.
     * The crossfade loop reads this at the end instead of hard-coding 1f,
     * so the incoming track reaches its correct RG volume without a jump.
     * Reset to null after each transition.
     */
    var incomingTrackReplayGainVolume: Float? = null
    /** Observational events; consumers must not use this lossy stream for durable history. */
    private val _playbackEvents = kotlinx.coroutines.flow.MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val playbackEvents: kotlinx.coroutines.flow.SharedFlow<PlaybackEvent> = _playbackEvents
    private var focusPauseCause: InterruptionCause? = null
    val currentInterruptionCause: InterruptionCause? get() = focusPauseCause

    /** Explicit transport commands supersede a pending system-triggered resume. */
    fun cancelPendingFocusResume() {
        isFocusLossPause = false
        focusPauseCause = null
    }

    fun pauseForOutputDisconnect() {
        if (isReleased || !::playerA.isInitialized) return
        focusPauseCause = InterruptionCause.OUTPUT_DISCONNECTED
        isFocusLossPause = false
        val wasTransitioning = transitionRunning
        val volume = playerA.volume
        playerA.pause()
        playerB?.pause()
        cancelNext()
        if (!wasTransitioning) playerA.volume = volume
    }

    fun playbackCapabilities(): PlaybackCapabilities {
        val format = currentAudioFormatSnapshot()
        return PlaybackCapabilities(
            decodedSampleRate = format?.sampleRate?.takeIf { it > 0 },
            decodedPcmEncoding = format?.pcmEncoding?.takeIf { it > 0 },
        )
    }

    private fun publishPlaybackEvent(kind: PlaybackEventKind, cause: InterruptionCause? = null,
        mediaId: String? = playerA.currentMediaItem?.mediaId, positionMs: Long = playerA.currentPosition) {
        _playbackEvents.tryEmit(PlaybackEvent(kind, mediaId, positionMs, SystemClock.elapsedRealtime(), cause))
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusPauseCause = InterruptionCause.AUDIO_FOCUS
                Timber.tag("TransitionDebug").d("AudioFocus LOSS. Pausing.")
                isFocusLossPause = false
                playerA.playWhenReady = false
                playerB?.playWhenReady = false
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (isAmbientListenFocusBlip()) {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS_TRANSIENT from Listen recognizer. Ignoring.")
            } else {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS_TRANSIENT. Pausing.")
                focusPauseCause = InterruptionCause.AUDIO_FOCUS
                val auxiliaryPlayer = playerB
                isFocusLossPause = shouldResumeAfterTransientAudioFocusLoss(
                    masterPlayWhenReady = playerA.playWhenReady,
                    masterIsPlaying = playerA.isPlaying,
                    transitionRunning = transitionRunning,
                    auxiliaryPlayWhenReady = auxiliaryPlayer?.playWhenReady == true,
                    auxiliaryIsPlaying = auxiliaryPlayer?.isPlaying == true
                )
                playerA.playWhenReady = false
                auxiliaryPlayer?.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (isAmbientListenFocusBlip()) {
                Timber.tag("TransitionDebug").d("AudioFocus CAN_DUCK from Listen recognizer. Keeping full volume.")
            } else {
                // setWillPauseWhenDucked(true) turns off system auto-ducking, so duck here
                // for navigation prompts, notifications and similar.
                Timber.tag("TransitionDebug").d("AudioFocus CAN_DUCK. Ducking.")
                isFocusDucked = true
                duckForAmbientVoice(0.2f, 150L)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.tag("TransitionDebug").d("AudioFocus GAIN. Resuming if paused by loss.")
                if (isFocusDucked) {
                    isFocusDucked = false
                    restoreAmbientVoiceDuck(250L)
                }
                if (isFocusLossPause) {
                    isFocusLossPause = false
                    playerA.playWhenReady = true
                    if (transitionRunning) playerB?.playWhenReady = true
                }
            }
        }
    }

    // Listener to attach to the active master player (playerA)
    private val masterPlayerListener = object : Player.Listener, AnalyticsListener, ExoPlayer.AudioOffloadListener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                publishPlaybackEvent(PlaybackEventKind.PLAY)
                focusPauseCause = null
            } else {
                val cause = if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
                    InterruptionCause.OUTPUT_DISCONNECTED else focusPauseCause ?: InterruptionCause.MANUAL
                publishPlaybackEvent(if (cause == InterruptionCause.MANUAL) PlaybackEventKind.PAUSE else PlaybackEventKind.INTERRUPTION, cause)
            }
            if (playWhenReady) {
                lastPlayWhenReadyAtMs = SystemClock.elapsedRealtime()
                requestAudioFocus()
                scheduleAudioOffloadFallbackIfNeeded(playerA)
            } else {
                cancelAudioOffloadFallback()
                // Keep focus across user pauses so a quick resume doesn't have to re-acquire it.
                // Focus is abandoned explicitly on AUDIOFOCUS_LOSS and on release(); anything in
                // between (user pause/play) keeps the request alive to avoid contention races
                // that occasionally caused press-play to auto-pause after a short wait.
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            // Downloads back off while the playing song itself is still arriving over the network.
            val scheme = playerA.currentMediaItem?.localConfiguration?.uri?.scheme
            com.theveloper.pixelplay.data.stream.PlaybackBandwidthGate.streamingCurrentSong =
                isLoading && scheme != null && scheme !in LOCAL_MEDIA_SCHEMES
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                lastPlayingAtMs = SystemClock.elapsedRealtime()
                cancelAudioOffloadFallback()
            }
        }

        /**
         * Fires when ExoPlayer believes the audio HAL is producing output via
         * offload and the renderer thread can stop polling — at that point the
         * CPU genuinely doesn't need a wake lock to keep playing audio. When
         * [sleepingForOffload] flips back to false (track change, format
         * mismatch, fallback path), restore [C.WAKE_MODE_LOCAL] so the
         * non-offload PCM path keeps the CPU awake correctly.
         *
         * Battery: this is what actually lets the SoC race-to-sleep during
         * music playback. The static [C.WAKE_MODE_LOCAL] we set at build time
         * is the safe default; this callback is the dynamic optimisation.
         */
        @Suppress("UnsafeOptInUsageError")
        override fun onSleepingForOffloadChanged(sleepingForOffload: Boolean) {
            if (!::playerA.isInitialized) return
            // Only override the wake mode for local media. Remote schemes need
            // C.WAKE_MODE_NETWORK to keep the wifi lock; we never want to drop
            // that to NONE.
            val baseMode = wakeModeFor(playerA.currentMediaItem)
            val desiredMode = if (sleepingForOffload && baseMode == C.WAKE_MODE_LOCAL) {
                C.WAKE_MODE_NONE
            } else {
                baseMode
            }
            if (currentWakeMode == desiredMode) return

            try {
                playerA.setWakeMode(desiredMode)
                playerB?.setWakeMode(desiredMode)
                currentWakeMode = desiredMode
                Timber.tag("DualPlayerEngine").d(
                    "Wake mode -> %d (sleepingForOffload=%b)",
                    desiredMode,
                    sleepingForOffload
                )
            } catch (e: Exception) {
                Timber.tag("DualPlayerEngine").w(e, "Failed to apply offload-aware wake mode")
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            val isHardware = AudioDecoderPolicy.isLikelyHardwareDecoder(decoderName)
            _activeDecoderInfo.value = ActiveDecoderInfo(decoderName, isHardware)
            PerformanceMetrics.recordTiming(
                PerformanceMetrics.Timings.AUDIO_DECODER_INIT,
                initializationDurationMs
            )
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "audio_decoder_initialized"
            ) {
                mapOf(
                    "decoderName" to decoderName,
                    "isHardware" to isHardware.toString(),
                    "initializationDurationMs" to initializationDurationMs.toString()
                )
            }
            Timber.tag("DualPlayerEngine").d("Audio decoder initialized: %s (Hardware: %b)", decoderName, isHardware)
        }

        override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
            val timeline = eventTime.timeline
            if (eventTime.windowIndex !in 0 until timeline.windowCount) return
            val mediaId = timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem.mediaId
            if (awaitingFirstAudioFor != mediaId) return
            awaitingFirstAudioFor = null
            Timber.tag("StreamingLatency").d("player_transition_to_audio_ms=%d",
                SystemClock.elapsedRealtime() - lastMediaItemTransitionAtMs)
            com.theveloper.pixelplay.data.diagnostics.PlaybackTrace.audioStarted(mediaId)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            // Record the live format (channels, sample rate, bit depth) as the report's
            // source of multichannel / bit-depth data — these aren't stored in the library DB.
            PerformanceMetrics.recordPlaybackFormat(
                channelCount = if (format.channelCount == Format.NO_VALUE) 0 else format.channelCount,
                sampleRate = if (format.sampleRate == Format.NO_VALUE) 0 else format.sampleRate,
                pcmEncoding = if (format.pcmEncoding == Format.NO_VALUE) 0 else format.pcmEncoding
            )
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "audio_format_changed"
            ) {
                mapOf(
                    "mime" to (format.sampleMimeType ?: "unknown"),
                    "sampleRate" to format.sampleRate.toString(),
                    "channels" to format.channelCount.toString(),
                    "pcmEncoding" to format.pcmEncoding.toString(),
                    "bitrate" to format.bitrate.toString()
                )
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != 0 && _activeAudioSessionId.value != audioSessionId) {
                _activeAudioSessionId.value = audioSessionId
                AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                    type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                    name = "audio_session_changed"
                ) {
                    mapOf("audioSessionId" to audioSessionId.toString())
                }
                Timber.tag("TransitionDebug").d("Master audio session changed: %d", audioSessionId)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem?.mediaId != streamRecoveryId) {
                streamRecoveryJob?.cancel()
                streamRecoveryId = mediaItem?.mediaId
                streamRecoveryAttempts = 0
            }
            lastMediaItemTransitionAtMs = SystemClock.elapsedRealtime()
            awaitingFirstAudioFor = mediaItem?.mediaId
            cancelAudioOffloadFallback()
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "media_item_transition",
                elapsedRealtimeMs = lastMediaItemTransitionAtMs
            ) {
                mapOf(
                    "reason" to reason.toString(),
                    "scheme" to (mediaItem?.localConfiguration?.uri?.scheme ?: "unknown")
                )
            }
            
            // If the transition was not automatic (e.g. user skip or playlist change),
            // immediately cancel any background crossfade logic to ensure responsiveness.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                cancelNext()
            }

            applyWakeModeForCurrentItem()
            // The item that just stopped being current can now take its downloaded file.
            if (!transitionRunning) applyLocalSources(includeCurrent = false)

            // YouTube preparation is owned by MusicService's readiness-gated prewarmer.
            // Resolving it here as well competed with cold startup after just 600 ms.
            // Keep this legacy neighbour preparation for Drive only.
            preResolutionJob?.cancel()
            preResolutionJob = scope.launch {
                delay(600) // Wait for user to stop skipping/navigating
                try {
                    val currentIndex = playerA.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        // Resolve each neighbour directly — no intermediate list allocation.
                        if (currentIndex + 1 < playerA.mediaItemCount) {
                            playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri
                                ?.takeIf { it.scheme == "gdrive" }
                                ?.let { resolveCloudUri(it) }
                        }
                        if (currentIndex - 1 >= 0) {
                            playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri
                                ?.takeIf { it.scheme == "gdrive" }
                                ?.let { resolveCloudUri(it) }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("DualPlayerEngine").w(e, "Pre-resolution error")
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            publishPlaybackEvent(PlaybackEventKind.ERROR)
            val player = playerA
            val item = player.currentMediaItem ?: return
            val uri = item.localConfiguration?.uri ?: return
            if (uri.scheme != "youtube" || transitionRunning || streamRecoveryAttempts >= 2) return
            val decoderFailure = error.errorCode in 4001..4005
            val transportFailure = error.errorCode in 2000..2008
            if (!decoderFailure && !transportFailure) return
            // The stream already broke; if the song has since been downloaded, resume from disk.
            if (localSourceFor(item) != null) {
                scope.launch { applyLocalSources(includeCurrent = true) }
                return
            }
            val videoId = uri.host?.removePrefix("yt_") ?: return
            val failedIndex = player.currentMediaItemIndex
            streamRecoveryAttempts++
            streamRecoveryJob?.cancel()
            streamRecoveryJob = scope.launch {
                delay(300L * streamRecoveryAttempts)
                if (player !== playerA || player.currentMediaItem != item || player.currentMediaItemIndex != failedIndex || transitionRunning) return@launch
                if (decoderFailure) youTubeStreamProxy.useCompatibleRendition(videoId)
                else youTubeStreamProxy.invalidateStream(videoId)
                // Recreate the extractor from time, allowing a codec fallback without
                // ever resuming a byte offset in the wrong container.
                val position = player.currentPosition
                val index = player.currentMediaItemIndex
                val shouldPlay = player.playWhenReady
                player.replaceMediaItem(index, item.buildUpon().setUri(uri).setMimeType(null)
                    .setCustomCacheKey("youtube:$videoId:recovery:$streamRecoveryAttempts").build())
                player.seekTo(index, position)
                player.prepare()
                player.playWhenReady = shouldPlay
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (transitionRunning) return
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || queueSnapshot.isEmpty()) {
                refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) publishPlaybackEvent(PlaybackEventKind.NATURAL_END)
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    val now = SystemClock.elapsedRealtime()
                    if (bufferingStartedAtMs == 0L) bufferingStartedAtMs = now
                    AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                        type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                        name = "playback_buffering",
                        elapsedRealtimeMs = now
                    )
                    val timeSincePlayingMs = now - lastPlayingAtMs
                    val timeSinceSeekMs = now - lastSeekAtMs
                    val timeSinceTransitionMs = now - lastTransitionFinishedAtMs
                    val timeSinceMediaItemTransitionMs = now - lastMediaItemTransitionAtMs
                    val isPostSeekBuffering = lastSeekAtMs > 0L && timeSinceSeekMs < 1_500L
                    val isPostTransitionBuffering = lastTransitionFinishedAtMs > 0L &&
                        timeSinceTransitionMs < POST_TRANSITION_OFFLOAD_GUARD_MS
                    val isPostMediaItemTransition = lastMediaItemTransitionAtMs > 0L &&
                        timeSinceMediaItemTransitionMs < 2_000L
                    if (shouldDisableAudioOffloadOnEarlyBuffering(
                            audioOffloadEnabled = audioOffloadEnabled,
                            transitionRunning = transitionRunning,
                            lastPlayingAtMs = lastPlayingAtMs,
                            timeSincePlayingMs = timeSincePlayingMs,
                            isPostSeekBuffering = isPostSeekBuffering,
                            isPostTransitionBuffering = isPostTransitionBuffering,
                            isPostMediaItemTransition = isPostMediaItemTransition
                        )
                    ) {
                        disableAudioOffloadForSession(
                            reason = "HAL offload reset detected: STATE_BUFFERING after ${timeSincePlayingMs}ms of playback"
                        )
                    } else {
                        scheduleAudioOffloadFallbackIfNeeded(playerA)
                    }
                }
                Player.STATE_READY -> {
                    if (bufferingStartedAtMs > 0L) {
                        val prepareDurationMs = SystemClock.elapsedRealtime() - bufferingStartedAtMs
                        PerformanceMetrics.recordTiming(
                            PerformanceMetrics.Timings.PLAYBACK_PREPARE,
                            prepareDurationMs
                        )
                        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                            name = "playback_ready_after_buffering"
                        ) {
                            mapOf("prepareDurationMs" to prepareDurationMs.toString())
                        }
                        bufferingStartedAtMs = 0L
                    }
                    scheduleAudioOffloadFallbackIfNeeded(playerA)
                }
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    bufferingStartedAtMs = 0L
                    cancelAudioOffloadFallback()
                    // (An ended queue keeps its streamed item: Replay seeks to 0, and that seek is
                    // where the item moves to its downloaded file — see onPositionDiscontinuity.)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val kind = when (reason) {
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> PlaybackEventKind.NATURAL_END
                Player.DISCONTINUITY_REASON_SEEK -> if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex)
                    PlaybackEventKind.SEEK else PlaybackEventKind.SKIP
                else -> null
            }
            kind?.let { publishPlaybackEvent(it, mediaId = oldPosition.mediaItem?.mediaId, positionMs = oldPosition.positionMs) }
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                lastSeekAtMs = SystemClock.elapsedRealtime()
                // A seek back to the start (replay / restart) is already a discontinuity, so the
                // playing stream can switch to its downloaded file here without being heard.
                if (newPosition.positionMs < LOCAL_SWAP_SEEK_WINDOW_MS && currentHasPendingLocalSource()) {
                    scope.launch { applyLocalSources(includeCurrent = true) }
                }
            }
        }
    }

    private fun addMasterPlayerListeners(player: ExoPlayer) {
        player.addListener(masterPlayerListener)
        player.addAnalyticsListener(masterPlayerListener)
        player.addAudioOffloadListener(masterPlayerListener)
    }

    private fun removeMasterPlayerListeners(player: ExoPlayer) {
        player.removeListener(masterPlayerListener)
        player.removeAnalyticsListener(masterPlayerListener)
        player.removeAudioOffloadListener(masterPlayerListener)
    }

    fun addPlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.add(listener)
    }

    fun removePlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.remove(listener)
    }

    fun addTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.add(listener)
    }

    fun removeTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.remove(listener)
    }

    fun addTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.add(listener)
    }

    /**
     * Notifies the engine that an external caller (UI seek, etc.) is about to issue a
     * seek through the MediaController. Used to mark the upcoming STATE_BUFFERING as
     * seek-driven so the HAL-reset heuristic does not trigger a player rebuild that
     * would race with the in-flight seek command.
     *
     * Setting this here (synchronously, before the seek dispatches) is more reliable
     * than waiting for onPositionDiscontinuity, which is delivered on the next event
     * batch and can race with onPlaybackStateChanged on some Media3 versions.
     */
    fun notifyExternalSeekInitiated() {
        lastSeekAtMs = SystemClock.elapsedRealtime()
    }

    fun removeTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.remove(listener)
    }

    val masterPlayer: Player
        get() {
            initialize()
            return playerA
        }

    fun isTransitionRunning(): Boolean = transitionRunning

    fun isUsingWindowedQueue(): Boolean = activePlayerUsesWindowedQueue

    fun getFullQueue(): List<MediaItem> = ensureQueueSnapshot()

    fun getCurrentAbsoluteIndex(): Int {
        if (!::playerA.isInitialized) return 0
        val mediaItem = playerA.currentMediaItem ?: return playerA.currentMediaItemIndex.coerceAtLeast(0)
        val snapshot = ensureQueueSnapshot()
        val index = resolveCurrentAbsoluteIndex(mediaItem, snapshot)
        return if (index == C.INDEX_UNSET) {
            if (activePlayerUsesWindowedQueue) {
                (activeWindowStartIndex + playerA.currentMediaItemIndex).coerceIn(0, (snapshot.size - 1).coerceAtLeast(0))
            } else {
                playerA.currentMediaItemIndex.coerceAtLeast(0)
            }
        } else {
            index
        }
    }

    fun triggerAdjacentPreResolution() {
        if (!::playerA.isInitialized) return
        preResolutionJob?.cancel()
        val currentIndex = playerA.currentMediaItemIndex
        if (currentIndex != C.INDEX_UNSET) {
            val adjacentCloudUris = mutableListOf<Uri>()
            if (currentIndex + 1 < playerA.mediaItemCount) {
                playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri?.let { uri ->
                    if (uri.scheme in REMOTE_MEDIA_SCHEMES) adjacentCloudUris.add(uri)
                }
            }
            if (currentIndex - 1 >= 0) {
                playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri?.let { uri ->
                    if (uri.scheme in REMOTE_MEDIA_SCHEMES) adjacentCloudUris.add(uri)
                }
            }

            if (adjacentCloudUris.isNotEmpty()) {
                preResolutionJob = scope.launch {
                    delay(600) // Wait for user to stop skipping/navigating
                    try {
                        for (uriToResolve in adjacentCloudUris) {
                            resolveCloudUri(uriToResolve)
                        }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Error during pre-resolution triggered manually")
                    }
                }
            }
        }
    }

    fun getAudioSessionId(): Int = if (::playerA.isInitialized) playerA.audioSessionId else 0

    private var isReleased = false
    private val resolvedUriCache = LruCache<String, Uri>(100)

    // Whether the OS classifies this as a low-RAM device. Used to cap the player's max
    // prefetch depth so hi-res/lossless buffering (and the second player during a crossfade)
    // can't balloon peak memory on constrained hardware. Cached: it never changes at runtime.
    private val isLowRamDevice: Boolean by lazy {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }

    fun initialize() {
        if (!isReleased && ::playerA.isInitialized && playerA.applicationLooper.thread.isAlive) return
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        if (::playerA.isInitialized) {
            removeMasterPlayerListeners(playerA)
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            try { playerA.release() } catch (e: Exception) { /* Ignore */ }
        }
        playerB?.let { try { it.release() } catch (e: Exception) { /* Ignore */ } }
        playerB = null

        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)

        _activeAudioSessionId.value = playerA.audioSessionId
        isReleased = false
        queueSnapshot = emptyList()
        activeWindowStartIndex = 0
        activePlayerUsesWindowedQueue = false
        resetPreparedWindowState()
        observeDownloadedSources()
        observeSpatialAudio()
        // Bind the loopback stream proxy now (off the main thread) so the first online tap
        // does not also pay for the server's cold start.
        youTubeStreamProxy.startIfNeeded()
        // Likewise open the manifest host's connection, so a first tap is one request.
        if (connectivityStateHolder.isOnline.value) {
            scope.launch(Dispatchers.IO) { youTubeStreamProxy.warmUp() }
        }
    }

    // ── Spatial audio per Bluetooth device (connect menu → device settings) ──

    /** C.SPATIALIZATION_BEHAVIOR_AUTO (system decides) or _NEVER (the user turned it off for this device). */
    @Volatile
    private var spatializationBehavior: Int = C.SPATIALIZATION_BEHAVIOR_AUTO

    private fun buildMusicAudioAttributes(): Media3AudioAttributes = Media3AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .setSpatializationBehavior(spatializationBehavior)
        .build()

    private var spatialAudioJob: Job? = null

    /**
     * While a Bluetooth device whose spatial audio the user turned off is connected, our
     * playback asks Android not to spatialize it; otherwise the system default applies.
     */
    private fun observeSpatialAudio() {
        spatialAudioJob?.cancel()
        spatialAudioJob = scope.launch {
            kotlinx.coroutines.flow.combine(
                connectivityStateHolder.bluetoothAudioDeviceStates,
                bluetoothDevicePrefs.settings
            ) { devices, settings ->
                val off = devices.filter { it.isConnected }.any { d ->
                    val key = d.address?.takeIf { it.isNotBlank() } ?: "name:${d.name.lowercase()}"
                    settings[key]?.spatialAudio == false
                }
                if (off) C.SPATIALIZATION_BEHAVIOR_NEVER else C.SPATIALIZATION_BEHAVIOR_AUTO
            }
                .distinctUntilChanged()
                .collect { behavior ->
                    if (behavior == spatializationBehavior) return@collect
                    spatializationBehavior = behavior
                    val attrs = buildMusicAudioAttributes()
                    for (player in listOfNotNull(if (::playerA.isInitialized) playerA else null, playerB)) {
                        runCatching { player.setAudioAttributes(attrs, false) }
                    }
                }
        }
    }

    private var downloadedSourcesJob: Job? = null

    /**
     * Downloaded audio keyed by media id and by bare YouTube video id. Updated from the downloads
     * table; read on the main thread by [applyLocalSources].
     */
    @Volatile private var localSourcesById: Map<String, Uri> = emptyMap()
    @Volatile private var localSourcesByVideoId: Map<String, Uri> = emptyMap()
    /** True once [localSourcesByVideoId] reflects the downloads table, so reopens can skip the DB. */
    @Volatile private var localSourcesLoaded = false

    private fun localSourceFor(item: MediaItem): Uri? {
        val uri = item.localConfiguration?.uri ?: return null
        if (uri.scheme != "youtube") return null
        return localSourcesById[item.mediaId]
            ?: uri.host?.removePrefix("yt_")?.let { localSourcesByVideoId[it] }
    }

    /**
     * Seamless handover to downloaded files. A finished download must never be heard, so:
     *  - the item that is playing is never touched (it keeps streaming from the network/cache to
     *    its end — no replace, no seek, no prepare, so no gap or re-buffer);
     *  - every other queued item on the master player is pointed at its local file right away;
     *  - the playing item picks up its file the next time it becomes current, after a seek back
     *    to its start, or once playback has ended (see the listener hooks).
     * The auxiliary crossfade player is left alone: it is rebuilt from the master on each
     * transition, so it inherits the local URIs from there.
     */
    private fun observeDownloadedSources() {
        downloadedSourcesJob?.cancel()
        downloadedSourcesJob = scope.launch {
            cloudSongDao.observeDownloads().collectLatest { downloads ->
                // Several rows change when one download completes; collectLatest + this delay
                // coalesces them into a single pass.
                delay(DOWNLOAD_SWAP_DEBOUNCE_MS)
                val files = withContext(Dispatchers.IO) {
                    downloads.mapNotNull { download ->
                        download.downloadedAudioFile()?.let { download to Uri.fromFile(it) }
                    }
                }
                val byId = files.associate { (download, uri) -> download.id to uri }
                val byVideoId = files.mapNotNull { (download, uri) ->
                    download.youtubeId?.removePrefix("yt_")?.let { it to uri }
                }.toMap()
                if (byId == localSourcesById && byVideoId == localSourcesByVideoId) {
                    localSourcesLoaded = true
                    return@collectLatest
                }
                localSourcesById = byId
                localSourcesByVideoId = byVideoId
                localSourcesLoaded = true
                // Do not mutate the timeline halfway through a crossfade.
                while (transitionRunning) delay(100)
                applyLocalSources(includeCurrent = false)
            }
        }
    }

    /**
     * Points queued stream items at their downloaded files. The playing item is only swapped when
     * [includeCurrent] is set — callers do that only at moments where playback is already
     * discontinuous (a seek to the start, or the queue has ended), so the swap is inaudible.
     */
    private fun applyLocalSources(includeCurrent: Boolean) {
        if (!::playerA.isInitialized || transitionRunning) return
        if (localSourcesById.isEmpty() && localSourcesByVideoId.isEmpty()) return
        val player = playerA
        val currentIndex = player.currentMediaItemIndex
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val local = localSourceFor(item) ?: continue
            val isCurrent = index == currentIndex
            if (isCurrent && !includeCurrent) continue
            val replacement = item.buildUpon().setUri(local).setMimeType(null).build()
            if (!isCurrent) {
                // Non-current items have no active renderer, so this is a pure timeline edit.
                player.replaceMediaItem(index, replacement)
                continue
            }
            val position = player.currentPosition
            val shouldPlay = player.playWhenReady
            // Clear the remote container hint; offline audio may use another container.
            player.replaceMediaItem(index, replacement)
            player.seekTo(index, position)
            player.prepare()
            player.playWhenReady = shouldPlay
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "local_source_swap_current"
            ) { mapOf("positionMs" to position.toString()) }
        }
    }

    /** True when the playing item streams but a finished download of it exists. */
    private fun currentHasPendingLocalSource(): Boolean {
        if (!::playerA.isInitialized) return false
        val item = playerA.currentMediaItem ?: return false
        return localSourceFor(item) != null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return

        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            // Let the system queue our request behind a transient holder instead of failing.
            // Pairs with the AUDIOFOCUS_GAIN handler below: on DELAYED we pause and mark the
            // pause as focus-driven so the eventual GAIN callback resumes playback.
            .setAcceptsDelayedFocusGain(true)
            // Disable system auto-ducking so CAN_DUCK reaches the listener above. That lets
            // Listen's recognizer blips be ignored while real ducking requests still duck.
            .setWillPauseWhenDucked(true)
            .build()

        val result = audioManager.requestAudioFocus(request)
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                audioFocusRequest = request
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                audioFocusRequest = request
                focusPauseCause = InterruptionCause.AUDIO_FOCUS
                isFocusLossPause = true
                playerA.playWhenReady = false
                if (transitionRunning) playerB?.playWhenReady = false
            }
            else -> {
                Timber.tag("TransitionDebug").w("AudioFocus Request Failed: $result")
                playerA.playWhenReady = false
            }
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    private fun scheduleAudioOffloadFallbackIfNeeded(player: ExoPlayer) {
        cancelAudioOffloadFallback()
        if (!audioOffloadEnabled || transitionRunning || !player.playWhenReady || player.isPlaying) return
        if (!isLikelyLocalMedia(player.currentMediaItem)) return

        val watchedMediaId = player.currentMediaItem?.mediaId ?: return
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return
        bufferingFallbackJob = scope.launch {
            delay(AUDIO_OFFLOAD_STALL_FALLBACK_MS)

            val currentMediaId = player.currentMediaItem?.mediaId
            val shouldFallback = shouldTriggerAudioOffloadStallFallback(
                audioOffloadEnabled = audioOffloadEnabled,
                transitionRunning = transitionRunning,
                isCurrentMasterPlayer = player === playerA,
                mediaIdMatches = currentMediaId == watchedMediaId,
                playbackState = player.playbackState,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackSuppressionReason = player.playbackSuppressionReason
            )
            if (!shouldFallback) return@launch

            disableAudioOffloadForSession(
                reason = "Local media did not produce audio for " +
                    "${AUDIO_OFFLOAD_STALL_FALLBACK_MS}ms (state=${player.playbackState})"
            )
        }
    }

    private fun cancelAudioOffloadFallback() {
        bufferingFallbackJob?.cancel()
        bufferingFallbackJob = null
    }

    private fun isLikelyLocalMedia(mediaItem: MediaItem?): Boolean {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return scheme == null || scheme in LOCAL_MEDIA_SCHEMES
    }

    private fun wakeModeFor(mediaItem: MediaItem?): Int {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return if (scheme != null && scheme in REMOTE_MEDIA_SCHEMES) {
            C.WAKE_MODE_NETWORK
        } else {
            C.WAKE_MODE_LOCAL
        }
    }

    private var currentWakeMode: Int = C.WAKE_MODE_LOCAL

    private fun applyWakeModeForCurrentItem() {
        if (!::playerA.isInitialized) return
        val mode = wakeModeFor(playerA.currentMediaItem)
        if (currentWakeMode == mode) return
        
        try {
            playerA.setWakeMode(mode)
            playerB?.setWakeMode(mode)
            currentWakeMode = mode
            Timber.tag("DualPlayerEngine").d("Wake mode updated to %d", mode)
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").w(e, "Failed to update wake mode")
        }
    }

    private fun shouldDisableAudioOffloadByDefault(): Boolean {
        return shouldDisableAudioOffloadByDefaultForDevice(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            hardware = Build.HARDWARE,
            sdkInt = Build.VERSION.SDK_INT
        )
    }

    private fun disableAudioOffloadForSession(reason: String) {
        if (!audioOffloadEnabled) return
        if (transitionRunning) {
            Timber.tag("DualPlayerEngine").w("Skipping offload fallback during active transition. %s", reason)
            return
        }

        audioOffloadEnabled = false
        PerformanceMetrics.recordOffloadFallback(reason, SystemClock.elapsedRealtime())
        rebuildPlayersPreservingMasterState(
            logMessage = "Audio offload disabled for current session. $reason"
        )
    }

    private fun rebuildPlayersPreservingMasterState(logMessage: String) {
        cancelAudioOffloadFallback()
        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
            name = "player_rebuild_start"
        ) {
            mapOf("reason" to logMessage)
        }

        val desiredPlayWhenReady = playerA.playWhenReady
        // Guard against snapshotting a position that landed during a bad early-startup seek
        // (e.g. an offload stall rebuild firing while the player is at a spurious offset).
        // Positions under 5s on first playback are more likely noise than intent.
        val positionMs = if (playerA.currentPosition > 5_000L) playerA.currentPosition else 0L
        val currentIndex = playerA.currentMediaItemIndex.coerceAtLeast(0)
        // Pre-sized ArrayList avoids the IntRange object and the extra copy produced by .map.
        val mediaItemCount = playerA.mediaItemCount
        val mediaItems = ArrayList<MediaItem>(mediaItemCount)
        for (i in 0 until mediaItemCount) mediaItems.add(playerA.getMediaItemAt(i))
        val repeatMode = playerA.repeatMode
        val shuffleMode = playerA.shuffleModeEnabled
        val volume = playerA.volume
        val pauseAtEnd = playerA.pauseAtEndOfMediaItems
        val playbackParameters: PlaybackParameters = playerA.playbackParameters

        removeMasterPlayerListeners(playerA)
        onPlayerAboutToBeReleasedListener?.invoke(playerA)
        playerA.release()
        playerB?.release()
        playerB = null

        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)
        playerA.volume = volume
        playerA.pauseAtEndOfMediaItems = pauseAtEnd
        playerA.playbackParameters = playbackParameters

        if (mediaItems.isNotEmpty()) {
            playerA.setMediaItems(mediaItems, currentIndex, positionMs)
            playerA.repeatMode = repeatMode
            playerA.shuffleModeEnabled = shuffleMode
            playerA.prepare()
            playerA.playWhenReady = desiredPlayWhenReady
            applyWakeModeForCurrentItem()
        }

        _activeAudioSessionId.value = playerA.audioSessionId
        onPlayerSwappedListeners.forEach { it(playerA) }

        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
            name = "player_rebuild_end"
        ) {
            mapOf("audioSessionId" to playerA.audioSessionId.toString())
        }
        Timber.tag("DualPlayerEngine").d(logMessage)
    }

    /**
     * Bound each player's allocations even for high-bitrate lossless tracks during overlap.
     * On a connection that can't keep up, repeated rebuffers wait for more audio before
     * resuming instead of stopping again every second ([RebufferBackoff]).
     */
    private fun buildAdaptiveLoadControl(): LoadControl {
        val profile = loadControlBufferProfileFor(isLowRamDevice, Runtime.getRuntime().maxMemory())
        val defaults = DefaultLoadControl.Builder()
            .setBufferDurationsMs(profile.minBufferMs, profile.maxBufferMs,
                profile.bufferForPlaybackMs, profile.bufferForPlaybackAfterRebufferMs)
            .setTargetBufferBytes(profile.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            // Audio samples are all keyframes, so the resume rewind lands inside this buffer.
            .setBackBuffer(profile.backBufferMs, /* retainBackBufferFromKeyframe= */ true)
            .build()
        return RebufferBackoffLoadControl(defaults)
    }

    private fun buildPlayer(): ExoPlayer {
        val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoderInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            AudioDecoderPolicy.selectPlatformDecoders(mimeType, decoderInfos)
        }
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(hiFiModeEnabled)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            HiResSampleRateCapAudioProcessor(),
                            SurroundDownmixProcessor(),
                            dspEngineManager.createAudioProcessor()
                        )
                    )
                    .build()
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip video renderers to save memory and "renderers" count.
            }

            override fun buildTextRenderers(
                context: Context,
                eventListener: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip text renderers.
            }

            override fun buildCameraMotionRenderers(
                context: Context,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip camera motion renderers.
            }
        }.setEnableAudioFloatOutput(hiFiModeEnabled)
         .setMediaCodecSelector(mediaCodecSelector)
         .setEnableDecoderFallback(true)
         .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = buildMusicAudioAttributes()
            
        val resolver = object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri
                val scheme = uri.scheme
                if (scheme in CLOUD_PROXY_SCHEMES) {
                    val originalUri = uri.toString()
                    val cached = if (scheme in UNCACHED_PROXY_SCHEMES) null else resolvedUriCache.get(originalUri)
                    if (cached != null) {
                        return dataSpec.buildUpon().setUri(cached).build()
                    }
                    Timber.tag("DualPlayerEngine").d("resolveDataSpec: Cache MISS for %s — resolving synchronously", scheme)
                    val resolvedUri = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        resolveCloudUri(uri)
                    }
                    if (resolvedUri != uri) {
                        return dataSpec.buildUpon().setUri(resolvedUri).build()
                    }
                    throw java.io.IOException("Failed to resolve cloud URI: $uri")
                }
                return dataSpec
            }
        }
        
        // The stream proxies reconnect upstream on their own and are silent meanwhile; the
        // player waits for them instead of timing out and reopening (see CloudStreamProxy).
        val proxyHttpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(5_000)
            .setReadTimeoutMs(com.theveloper.pixelplay.data.stream.CloudStreamProxy.PLAYER_READ_TIMEOUT_MS)
        val httpFactory = LoopbackRoutingDataSource.Factory(
            local = proxyHttpFactory,
            remote = DefaultHttpDataSource.Factory()
        )
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val resolvingFactory = ResolvingDataSource.Factory(dataSourceFactory, resolver)
        val extractorsFactory = DefaultExtractorsFactory()
            // FLAG_WORKAROUND_IGNORE_EDIT_LISTS intentionally removed: it breaks Opus files
            // by discarding the edit list that encodes the pre-skip (encoder delay), causing
            // ExoPlayer to seek ~44-52s into the track on first playback.
            // FLAG_ENABLE_CONSTANT_BITRATE_SEEKING (not _ALWAYS): fallback-only CBR seeking
            // so VBR MP3s with proper Xing/VBRI headers still use their seek table and land
            // on the exact frame instead of jumping ±30 s on a VBR file.
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setFlacExtractorFlags(FlacExtractor.FLAG_DISABLE_ID3_METADATA)

        val loadControl = buildAdaptiveLoadControl()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .build().apply {
            // Warm the next queued item while the current track plays, even without crossfade.
            setPreloadConfiguration(ExoPlayer.PreloadConfiguration(if (isLowRamDevice) 2_000_000L else 5_000_000L))
            setAudioAttributes(audioAttributes, false)
            val offloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (audioOffloadEnabled) {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    } else {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    }
                )
                .build()
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(offloadPreferences)
                .build()
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            playWhenReady = false
        }
    }

    private fun getOrCreateAuxiliaryPlayer(): ExoPlayer {
        playerB?.let { return it }
        return buildPlayer().also { player ->
            player.setWakeMode(currentWakeMode)
            playerB = player
        }
    }

    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        if (::playerA.isInitialized) {
            playerA.pauseAtEndOfMediaItems = shouldPause
        }
    }

    fun getNextTransitionTarget(currentMediaItem: MediaItem, repeatMode: Int): TransitionTarget? {
        val snapshot = ensureQueueSnapshot()
        if (snapshot.isEmpty()) return null

        val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(currentMediaItem, snapshot)
        if (currentAbsoluteIndex == C.INDEX_UNSET) return null

        val targetIndex = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> currentAbsoluteIndex
            else -> currentAbsoluteIndex + 1
        }

        val targetItem = snapshot.getOrNull(targetIndex) ?: return null
        return TransitionTarget(
            mediaItem = targetItem,
            absoluteIndex = targetIndex,
            queueSize = snapshot.size
        )
    }

    fun setHiFiMode(enabled: Boolean) {
        if (hiFiModeEnabled == enabled) return
        if (enabled && !HiFiCapabilityChecker.isSupported()) {
            Timber.tag("DualPlayerEngine").w("Hi-Fi mode requested but device does not support PCM_FLOAT")
            return
        }
        hiFiModeEnabled = enabled
        rebuildPlayersPreservingMasterState("Hi-Fi mode set to $enabled")
    }

    /**
     * Resolves [item]'s manifest in advance and caches its first bytes; no player mutation.
     * With [headBytes] 0 only the manifest (a few KB) is fetched, for songs further ahead.
     */
    suspend fun prewarmNextStream(item: MediaItem, headBytes: Int = NEXT_SONG_HEAD_BYTES) {
        val uri = item.localConfiguration?.uri ?: return
        if (!connectivityStateHolder.isOnline.value) return
        val videoId = when (uri.scheme) {
            "youtube" -> uri.host?.removePrefix("yt_")
            // Match the next catalog song ahead of time, so its transition is not a search.
            in CATALOG_SCHEMES -> catalogPlaybackResolver.videoIdFor(uri.toString())
            else -> null
        } ?: return
        withContext(Dispatchers.IO) {
            if (cloudSongDao.getDownloadsByVideoId(videoId).any { it.downloadedAudioFile() != null }) return@withContext
            // Also cache the next song's first bytes so its transition needs no network wait.
            youTubeStreamProxy.prewarm(videoId, headBytes = headBytes)
        }
    }

    suspend fun resolveCloudUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        if (uri.scheme !in UNCACHED_PROXY_SCHEMES) resolvedUriCache.get(uriString)?.let { return@withContext it }

        val resolved: Uri? = when (uri.scheme) {
            "gdrive" -> resolveGDriveUriAsync(uriString)
            "youtube" -> resolveYouTubeUriAsync(uriString)
            in CATALOG_SCHEMES -> catalogPlaybackResolver.videoIdFor(uriString)
                ?.let { videoId -> resolveYouTubeUriAsync("youtube://$videoId") }
            else -> null
        }

        if (resolved != null) {
            if (uri.scheme !in UNCACHED_PROXY_SCHEMES) resolvedUriCache.put(uriString, resolved)
            return@withContext resolved
        }
        uri
    }

    private suspend fun resolveYouTubeUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        val videoId = Uri.parse(uriString).host?.removePrefix("yt_") ?: return@withContext null
        // ExoPlayer can open a source several times while starting one track; once the
        // downloads table has been observed, answer from memory instead of querying Room.
        val downloaded = if (localSourcesLoaded) {
            localSourcesByVideoId[videoId]?.takeIf { uri ->
                uri.path?.let { java.io.File(it) }?.let { it.isFile && it.canRead() && it.length() > 0 } == true
            }
        } else {
            cloudSongDao.getDownloadsByVideoId(videoId).firstNotNullOfOrNull { it.downloadedAudioFile() }
                ?.let { Uri.fromFile(it) }
        }
        downloaded?.let { return@withContext it }
        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }
        if (!youTubeStreamProxy.ensureReady(5_000L)) return@withContext null
        // A song with cached leading bytes starts from them while the proxy resolves its URL
        // in parallel, so don't wait for the manifest here.
        if (!youTubeStreamProxy.hasCachedHead(videoId)) youTubeStreamProxy.prewarm(videoId)
        youTubeStreamProxy.resolveUri(uriString)?.toUri()
    }

    private suspend fun resolveGDriveUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }
        if (!gdriveStreamProxy.ensureReady(5_000L)) return@withContext null
        gdriveStreamProxy.resolveGDriveUri(uriString)?.toUri()
    }

    suspend fun resolveMediaItem(mediaItem: MediaItem): MediaItem {
        val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val scheme = uri.scheme
        // Use CLOUD_PROXY_SCHEMES: http/https resolve directly via ExoPlayer and never
        // reach resolveCloudUri, so checking them wastes an IO dispatch.
        // Keep the logical YouTube URI in the queue. Every reopen must check offline
        // storage and fresh manifests, including items prepared before a download.
        if (scheme in UNCACHED_PROXY_SCHEMES) return mediaItem
        if (scheme !in CLOUD_PROXY_SCHEMES) return mediaItem
        val resolvedUri = resolveCloudUri(uri)
        return if (resolvedUri == uri) mediaItem else mediaItem.buildUpon().setUri(resolvedUri).build()
    }

    suspend fun prepareNext(target: TransitionTarget, startPositionMs: Long = 0L) {
        prepareNext(target.mediaItem, target.absoluteIndex, startPositionMs)
    }

    suspend fun prepareNext(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        val preferredIndex = findMediaItemIndex(
            items = ensureQueueSnapshot(),
            mediaId = mediaItem.mediaId,
            preferAfterExclusive = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, queueSnapshot)
        )
        prepareNext(mediaItem, preferredIndex, startPositionMs)
    }

    private suspend fun prepareNext(mediaItem: MediaItem, preferredAbsoluteIndex: Int, startPositionMs: Long = 0L) {
        try {
            val snapshot = ensureQueueSnapshot()
            val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, snapshot)
            val targetIndex = when {
                preferredAbsoluteIndex in snapshot.indices &&
                    snapshot[preferredAbsoluteIndex].mediaId == mediaItem.mediaId -> preferredAbsoluteIndex
                else -> findMediaItemIndex(snapshot, mediaItem.mediaId, currentAbsoluteIndex)
            }
            val resolvedItem = resolveMediaItem(mediaItem)
            val auxiliaryPlayer = getOrCreateAuxiliaryPlayer()

            auxiliaryPlayer.stop()
            auxiliaryPlayer.clearMediaItems()

            if (targetIndex != C.INDEX_UNSET && snapshot.isNotEmpty()) {
                val count = snapshot.size
                val (start, end) = auxiliaryWindowBounds(targetIndex, count)
                val windowItems = ArrayList<MediaItem>(end - start)
                for (i in start until end) {
                    val item = snapshot[i]
                    windowItems.add(if (i == targetIndex) resolvedItem else item)
                }
                preparedWindowStartIndex = start
                preparedPlayerUsesWindowedQueue = count > MAX_AUXILIARY_TIMELINE_ITEMS
                auxiliaryPlayer.setMediaItems(windowItems, targetIndex - start, startPositionMs)
            } else {
                // Fallback for single item if not found in current timeline
                resetPreparedWindowState()
                auxiliaryPlayer.setMediaItem(resolvedItem)
                auxiliaryPlayer.seekTo(startPositionMs)
            }

            auxiliaryPlayer.prepare()
            auxiliaryPlayer.volume = 0f
            auxiliaryPlayer.pause()
        } catch (e: Exception) {
            resetPreparedWindowState()
            Timber.tag("TransitionDebug").e(e, "Failed to prepare next player")
        }
    }

    fun cancelNext() {
        val shouldPublishMasterPlayer = transitionRunning
        transitionJob?.cancel()
        transitionRunning = false
        resetPreparedWindowState()
        playerB?.takeIf { it.mediaItemCount > 0 }?.let { auxiliaryPlayer ->
            try {
                auxiliaryPlayer.stop()
                auxiliaryPlayer.clearMediaItems()
            } catch (e: Exception) { /* Ignore */ }
        }
        if (::playerA.isInitialized) {
            playerA.volume = 1f
            if (shouldPublishMasterPlayer) {
                onPlayerSwappedListeners.forEach { it(playerA) }
            }
        }
        incomingTrackReplayGainVolume = null
        setPauseAtEndOfMediaItems(false)
    }

    fun performTransition(settings: TransitionSettings) {
        transitionJob?.cancel()
        transitionRunning = true
        transitionStartedAtMs = SystemClock.elapsedRealtime()
        transitionJob = scope.launch {
            try {
                performOverlapTransition(settings)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.tag("TransitionDebug").e(e, "Error performing transition")
                }
                playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                playerB?.stop()
            } finally {
                transitionRunning = false
                lastTransitionFinishedAtMs = SystemClock.elapsedRealtime()
                if (transitionStartedAtMs > 0L) {
                    PerformanceMetrics.recordTiming(
                        PerformanceMetrics.Timings.TRANSITION,
                        SystemClock.elapsedRealtime() - transitionStartedAtMs
                    )
                    transitionStartedAtMs = 0L
                }
                onTransitionFinishedListeners.forEach { it() }
            }
        }
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        val auxiliaryPlayer = playerB
        if (auxiliaryPlayer == null || auxiliaryPlayer.mediaItemCount == 0) {
            playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
            return
        }

        if (auxiliaryPlayer.playbackState == Player.STATE_IDLE) auxiliaryPlayer.prepare()
        if (auxiliaryPlayer.playbackState == Player.STATE_BUFFERING) {
            if (!awaitPlayerReady(auxiliaryPlayer, 3000L)) {
                playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                return
            }
        }

        val outgoingStartVolume = playerA.volume.coerceIn(0f, 1f)
        auxiliaryPlayer.volume = 0f
        if (!playerA.isPlaying && playerA.playbackState == Player.STATE_READY) playerA.play()

        val outgoingPlayer = playerA
        val incomingPlayer = auxiliaryPlayer

        incomingPlayer.repeatMode = outgoingPlayer.repeatMode
        incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
        outgoingPlayer.pauseAtEndOfMediaItems = true
        incomingPlayer.pauseAtEndOfMediaItems = false

        // FADE_IN_OUT is sequential: the outgoing track fades out over the first half,
        // and the incoming track only starts (and fades in) over the second half, so
        // none of its opening is spent at zero volume. SMOOTH forces the S-curve its
        // documentation promises; OVERLAP uses the configured curves.
        val sequential = settings.mode == TransitionMode.FADE_IN_OUT
        val curveIn = if (settings.mode == TransitionMode.SMOOTH) Curve.S_CURVE else settings.curveIn
        val curveOut = if (settings.mode == TransitionMode.SMOOTH) Curve.S_CURVE else settings.curveOut

        var incomingStarted = false
        fun startIncoming() {
            if (incomingStarted) return
            incomingStarted = true
            incomingPlayer.playWhenReady = true
            incomingPlayer.play()
            onTransitionDisplayPlayerListeners.forEach { it(incomingPlayer) }
        }
        if (!sequential) startIncoming()

        val duration = settings.durationMs.toLong().coerceAtLeast(500L)
        val stepMs = 32L
        val startedAtMs = SystemClock.elapsedRealtime()

        while (true) {
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtMost(duration)
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val outProgress = if (sequential) (progress * 2f).coerceAtMost(1f) else progress
            val inProgress = if (sequential) (progress * 2f - 1f).coerceAtLeast(0f) else progress
            if (sequential && progress >= 0.5f) startIncoming()

            val volIn = envelope(inProgress, curveIn)
            val volOut = fadeOutGain(outProgress, curveOut)
            val incomingTarget = incomingTrackReplayGainVolume ?: 1f
            incomingPlayer.volume = (volIn * incomingTarget).coerceIn(0f, 1f)
            outgoingPlayer.volume = (volOut * outgoingStartVolume).coerceIn(0f, 1f)

            if (elapsed >= duration) break
            delay(stepMs)
        }
        // A very short sequential fade can finish between ticks; never swap to a
        // player that was never started.
        startIncoming()

        outgoingPlayer.volume = 0f
        incomingPlayer.volume = incomingTrackReplayGainVolume ?: 1f
        incomingTrackReplayGainVolume = null

        removeMasterPlayerListeners(outgoingPlayer)

        playerA = incomingPlayer
        playerB = outgoingPlayer
        activeWindowStartIndex = preparedWindowStartIndex
        activePlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue
        resetPreparedWindowState()

        playerA.pauseAtEndOfMediaItems = false
        playerB?.pauseAtEndOfMediaItems = false
        addMasterPlayerListeners(playerA)
        if (playerA.playWhenReady) requestAudioFocus()

        onPlayerSwappedListeners.forEach { it(playerA) }
        _activeAudioSessionId.value = playerA.audioSessionId

        playerB?.pause()
        playerB?.stop()
        playerB?.clearMediaItems()

        setPauseAtEndOfMediaItems(false)
    }

    private fun ensureQueueSnapshot(): List<MediaItem> {
        // Single guard: isEmpty() short-circuits the windowed-queue size check, so
        // refreshQueueSnapshotFromMaster() is called at most once per invocation.
        if (queueSnapshot.isEmpty() ||
            (!activePlayerUsesWindowedQueue && queueSnapshot.size != playerA.mediaItemCount)
        ) {
            refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
        }
        return queueSnapshot
    }

    private fun refreshQueueSnapshotFromMaster(windowStartIndex: Int, usesWindowedQueue: Boolean) {
        if (!::playerA.isInitialized) return

        val count = playerA.mediaItemCount
        if (count <= 0) {
            queueSnapshot = emptyList()
            activeWindowStartIndex = 0
            activePlayerUsesWindowedQueue = false
            return
        }

        val items = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            items.add(playerA.getMediaItemAt(i))
        }

        queueSnapshot = items
        activeWindowStartIndex = windowStartIndex
        activePlayerUsesWindowedQueue = usesWindowedQueue
    }

    private fun resolveCurrentAbsoluteIndex(mediaItem: MediaItem, snapshot: List<MediaItem>): Int {
        if (snapshot.isEmpty()) return C.INDEX_UNSET

        val playerIndex = playerA.currentMediaItemIndex
        if (activePlayerUsesWindowedQueue) {
            val absoluteIndex = activeWindowStartIndex + playerIndex
            if (absoluteIndex in snapshot.indices &&
                snapshot[absoluteIndex].mediaId == mediaItem.mediaId
            ) {
                return absoluteIndex
            }
        } else if (playerIndex in snapshot.indices &&
            snapshot[playerIndex].mediaId == mediaItem.mediaId
        ) {
            return playerIndex
        }

        return findMediaItemIndex(snapshot, mediaItem.mediaId, preferAfterExclusive = C.INDEX_UNSET)
    }

    private fun findMediaItemIndex(
        items: List<MediaItem>,
        mediaId: String,
        preferAfterExclusive: Int
    ): Int {
        var fallback = C.INDEX_UNSET
        for (i in items.indices) {
            if (items[i].mediaId == mediaId) {
                if (preferAfterExclusive != C.INDEX_UNSET && i > preferAfterExclusive) return i
                if (fallback == C.INDEX_UNSET) fallback = i
            }
        }
        return fallback
    }

    private fun auxiliaryWindowBounds(targetIndex: Int, count: Int): Pair<Int, Int> {
        if (count <= MAX_AUXILIARY_TIMELINE_ITEMS) return 0 to count

        val halfWindow = MAX_AUXILIARY_TIMELINE_ITEMS / 2
        var start = (targetIndex - halfWindow).coerceAtLeast(0)
        var end = (start + MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtMost(count)
        start = (end - MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtLeast(0)
        return start to end
    }

    private fun resetPreparedWindowState() {
        preparedWindowStartIndex = 0
        preparedPlayerUsesWindowedQueue = false
    }

    private suspend fun awaitPlayerReady(player: ExoPlayer, timeoutMs: Long): Boolean {
        if (player.playbackState == Player.STATE_READY) return true
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_BUFFERING) {
                            player.removeListener(this)
                            if (cont.isActive) cont.resume(playbackState == Player.STATE_READY)
                        }
                    }
                }
                player.addListener(listener)
                cont.invokeOnCancellation { player.removeListener(listener) }
            }
        } ?: false
    }


    private var isAmbientVoiceDucked: Boolean = false
    private var isFocusDucked: Boolean = false
    @Volatile private var ambientListenActive: Boolean = false
    @Volatile private var ambientRecognizerStartAtMs: Long = 0L

    /** Listen (always-on speech recognition) is running; playback must never dip for it. */
    fun setAmbientListenActive(active: Boolean) {
        ambientListenActive = active
        if (!active) ambientRecognizerStartAtMs = 0L
    }

    /** Called right before each recognizer (re)start so its audio-focus request can be ignored. */
    fun markAmbientRecognizerStarting() {
        ambientRecognizerStartAtMs = SystemClock.elapsedRealtime()
    }

    // A focus loss that lands just after Listen restarts its recognizer, outside a call, is the
    // recognizer itself. Calls, alarms and other apps outside that window still pause/duck.
    private fun isAmbientListenFocusBlip(): Boolean =
        ambientListenActive &&
            audioManager.mode == AudioManager.MODE_NORMAL &&
            SystemClock.elapsedRealtime() - ambientRecognizerStartAtMs < AMBIENT_FOCUS_BLIP_WINDOW_MS
    private var preDuckVolumeA: Float = 1.0f
    private var preDuckVolumeB: Float = 1.0f
    private var ambientVoiceDuckJob: Job? = null

    /**
     * Smoothly ducks the playback volume of the active player(s) during voice capture.
     *
     * @param targetFactor Target volume multiplier (e.g. 0.15f for -18 dB reduction)
     * @param durationMs Transition duration in milliseconds
     */
    fun duckForAmbientVoice(targetFactor: Float = 0.15f, durationMs: Long = 150L) {
        if (isReleased || !::playerA.isInitialized) return
        ambientVoiceDuckJob?.cancel()
        if (!isAmbientVoiceDucked) {
            preDuckVolumeA = playerA.volume
            preDuckVolumeB = playerB?.volume ?: 1.0f
            isAmbientVoiceDucked = true
        }
        val startA = playerA.volume
        val targetA = (preDuckVolumeA * targetFactor).coerceIn(0f, 1.0f)
        val startB = playerB?.volume ?: 0f
        val targetB = (preDuckVolumeB * targetFactor).coerceIn(0f, 1.0f)

        val steps = (durationMs / 20L).coerceAtLeast(1).toInt()
        ambientVoiceDuckJob = scope.launch(Dispatchers.Main) {
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                if (::playerA.isInitialized) {
                    playerA.volume = startA + (targetA - startA) * progress
                }
                playerB?.let { b ->
                    b.volume = startB + (targetB - startB) * progress
                }
                delay(20)
            }
        }
    }

    /**
     * Smoothly restores the playback volume to its pre-ducking level.
     *
     * @param durationMs Transition duration in milliseconds
     */
    fun restoreAmbientVoiceDuck(durationMs: Long = 200L) {
        if (isReleased || !::playerA.isInitialized || !isAmbientVoiceDucked) return
        ambientVoiceDuckJob?.cancel()
        val startA = playerA.volume
        val targetA = preDuckVolumeA
        val startB = playerB?.volume ?: 0f
        val targetB = preDuckVolumeB

        val steps = (durationMs / 20L).coerceAtLeast(1).toInt()
        ambientVoiceDuckJob = scope.launch(Dispatchers.Main) {
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                if (::playerA.isInitialized) {
                    playerA.volume = startA + (targetA - startA) * progress
                }
                playerB?.let { b ->
                    b.volume = startB + (targetB - startB) * progress
                }
                delay(20)
            }
            isAmbientVoiceDucked = false
        }
    }

    fun release() {
        ambientVoiceDuckJob?.cancel()
        isAmbientVoiceDucked = false
        isFocusDucked = false
        transitionJob?.cancel()
        preResolutionJob?.cancel()
        cancelAudioOffloadFallback()
        scope.coroutineContext[Job]?.cancel()
        abandonAudioFocus()
        if (::playerA.isInitialized) {
            removeMasterPlayerListeners(playerA)
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            playerA.release()
        }
        playerB?.release()
        playerB = null
        youTubeStreamProxy.stop()
        gdriveStreamProxy.stop()
        isReleased = true
    }
}
