package com.theveloper.pixelplay.presentation.components

import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.theveloper.pixelplay.data.youtube.VideoRendition
import kotlinx.coroutines.delay

/** Drives ExoPlayer through the same controller API as the YouTube embed. */
@OptIn(UnstableApi::class)
class ExoVideoTransport(val exoPlayer: ExoPlayer) : VideoTransport {
    override fun play() = exoPlayer.play()
    override fun pause() = exoPlayer.pause()
    override fun seekToSeconds(seconds: Float) = exoPlayer.seekTo((seconds * 1000f).toLong().coerceAtLeast(0L))
}

/**
 * Video mode's own player (ExoPlayer), used when NewPipe found the video's streams, so the
 * quality can really change. A new [videoId] starts a fresh load (at the audio's position,
 * like the embed); a new [rendition] for the same video switches quality in place, keeping
 * the position and the play/pause state. Sizing is the caller's job.
 */
@OptIn(UnstableApi::class)
@Composable
fun PixelExoVideoPlayer(
    videoId: String?,
    rendition: VideoRendition?,
    dataSourceFactory: DataSource.Factory,
    playback: SongVideoPlaybackController,
    onPlayerError: (videoId: String, message: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestError by rememberUpdatedState(onPlayerError)
    val currentVideoId by rememberUpdatedState(videoId)
    val exo = remember { ExoPlayer.Builder(context.applicationContext).build() }
    var loadedVideoId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(exo, playback) {
        val transport = ExoVideoTransport(exo)
        playback.player = transport
        playback.releaseVideo = {
            exo.stop()
            playback.player = null
            playback.isPlaying = false
            playback.isLoading = false
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !playback.prepare()) {
                    exo.pause()
                    playback.isPlaying = false
                    return
                }
                playback.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> playback.isLoading = true
                    Player.STATE_READY -> {
                        playback.isLoading = false
                        val duration = exo.duration
                        if (duration != C.TIME_UNSET && duration > 0L) {
                            if (playback.durationMs == 0L) playback.onDurationKnown(duration)
                            else playback.durationMs = duration
                        }
                        playback.onReportedPosition(exo.currentPosition)
                    }
                    Player.STATE_ENDED -> {
                        playback.isLoading = false
                        playback.isPlaying = false
                    }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playback.isPlaying = false
                playback.isLoading = false
                currentVideoId?.let { latestError(it, error.errorCodeName) }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
            playback.isPlaying = false
            playback.isLoading = false
            if (playback.player === transport) {
                playback.player = null
                playback.releaseVideo = null
            }
        }
    }

    // Load a new video, or switch quality of the current one.
    LaunchedEffect(videoId, rendition) {
        if (videoId == null || rendition == null) return@LaunchedEffect
        owner.lifecycle.withResumed {
            val newVideo = loadedVideoId != videoId
            val startMs: Long
            val playWhenReady: Boolean
            if (newVideo) {
                startMs = (playback.beginLoad() * 1000f).toLong()
                playWhenReady = true
            } else {
                // Quality change: same spot, same play/pause state; spinner while it re-buffers.
                playback.isLoading = true
                startMs = playback.currentPositionMs()
                playWhenReady = exo.playWhenReady
            }
            val factory = ProgressiveMediaSource.Factory(dataSourceFactory)
            val video = factory.createMediaSource(MediaItem.fromUri(rendition.videoUrl))
            val source = rendition.audioUrl
                ?.let { MergingMediaSource(video, factory.createMediaSource(MediaItem.fromUri(it))) }
                ?: video
            exo.setMediaSource(source, startMs)
            exo.prepare()
            val canPlay = playback.prepare()
            exo.playWhenReady = playWhenReady && canPlay
            loadedVideoId = videoId
            if (newVideo && !canPlay) latestError(videoId, "Audio player is not ready")
        }
    }

    // Position reports for the timeline (the embed reports a few times a second, too).
    LaunchedEffect(exo) {
        while (true) {
            if (exo.isPlaying) playback.onReportedPosition(exo.currentPosition)
            delay(POSITION_REPORT_MS)
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                player = exo
                // Keep the last frame while switching quality instead of flashing black.
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(AndroidColor.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier,
    )
}

private const val POSITION_REPORT_MS = 250L
