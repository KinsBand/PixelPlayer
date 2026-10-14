package com.theveloper.pixelplay.presentation.viewmodel.exts

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import com.theveloper.pixelplay.data.dsp.TimeStretchGuard
import com.theveloper.pixelplay.data.service.player.HiResSampleRateCapAudioProcessor
import com.theveloper.pixelplay.data.service.player.SurroundDownmixProcessor

/**
 * How a deck's speed slider changes the sound.
 * - [ON]: time-stretch (Sonic): tempo changes, key stays. Post-limited by [TimeStretchGuard].
 * - [OFF]: vinyl / turntable: plain resampling, pitch follows speed (`pitch = speed`), so there
 *   are no time-stretch phase artifacts at all.
 */
enum class KeyLockMode { ON, OFF }

@OptIn(UnstableApi::class)
class DeckController(
    private val context: Context,
    private val dspEngineManager: com.theveloper.pixelplay.data.dsp.DspEngineManager? = null
) {
    var player: ExoPlayer? = null
        private set

    private var speed = 1f
    var keyLockMode: KeyLockMode = KeyLockMode.ON
        private set

    fun loadSong(songUri: Uri) {
        release()
        player = buildSafePlayer().apply {
            setMediaItem(MediaItem.fromUri(songUri))
            prepare()
        }
        // A new song keeps the deck's speed and key-lock setting.
        applyPlaybackParameters()
    }

    private fun buildSafePlayer(): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                val processors = mutableListOf<androidx.media3.common.audio.AudioProcessor>(
                    HiResSampleRateCapAudioProcessor(),
                    SurroundDownmixProcessor()
                )
                dspEngineManager?.let { processors.add(it.createAudioProcessor()) }
                // Speed / pitch always in software, with a limiter after Sonic.
                return TimeStretchGuard.buildSink(context, false, *processors.toTypedArray())
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Audio-only player
            }

            override fun buildTextRenderers(
                context: Context,
                eventListener: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Audio-only player
            }

            override fun buildCameraMotionRenderers(
                context: Context,
                extensionRendererMode: Int,
                out: ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Audio-only player
            }
        }.setEnableAudioFloatOutput(false)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, false)
                val offloadDisabledPrefs = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    )
                    .build()
                setTrackSelectionParameters(
                    trackSelectionParameters
                        .buildUpon()
                        .setAudioOffloadPreferences(offloadDisabledPrefs)
                        .build()
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
            }
    }

    fun playPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun seek(progress: Float) {
        val duration = player?.duration?.takeIf { it > 0 } ?: return
        val position = (duration * progress).toLong()
        player?.seekTo(position)
    }

    fun setSpeed(speed: Float) {
        this.speed = speed
        applyPlaybackParameters()
    }

    fun setKeyLockMode(mode: KeyLockMode) {
        keyLockMode = mode
        applyPlaybackParameters()
    }

    private fun applyPlaybackParameters() {
        val pitch = if (keyLockMode == KeyLockMode.ON) 1f else speed
        player?.playbackParameters = PlaybackParameters(speed, pitch)
    }

    fun nudge(amountMs: Long) {
        val duration = player?.duration ?: return
        val currentPosition = player?.currentPosition ?: return
        val newPosition = (currentPosition + amountMs).coerceIn(0, duration)
        player?.seekTo(newPosition)
    }

    fun setDeckVolume(deckVolume: Float) {
        player?.volume = deckVolume
    }

    fun getProgress(): Float {
        val duration = player?.duration?.takeIf { it > 0 } ?: return 0f
        val position = player?.currentPosition ?: return 0f
        return (position.toFloat() / duration).coerceIn(0f, 1f)
    }

    fun release() {
        player?.release()
        player = null
    }
}
