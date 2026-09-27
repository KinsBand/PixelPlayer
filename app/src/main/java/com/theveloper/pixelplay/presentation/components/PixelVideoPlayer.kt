package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.withResumed
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Starts the system WebView engine once, ahead of Video mode. The first WebView an app creates
 * loads Chromium (hundreds of ms); doing it while the user is still on the full player makes the
 * embedded YouTube player open noticeably faster.
 */
object VideoPlayerWarmup {
    @Volatile private var warmed = false

    fun warm(context: android.content.Context) {
        if (warmed) return
        warmed = true
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                android.webkit.WebView(context.applicationContext).apply {
                    // Touch the network stack too, so DNS / TLS for YouTube is warm.
                    settings.javaScriptEnabled = false
                    loadUrl("about:blank")
                }.also { it.destroy() }
            }.onFailure { warmed = false }
        }
    }
}

/** One audio handoff per song, retained while choosing alternate performances. */
class SongVideoPlaybackController(
    private val acquireAudio: () -> ((resume: Boolean, resumePositionMs: Long?) -> Unit)?
) {
    var player: YouTubePlayer? = null
    var releaseVideo: (() -> Unit)? = null
    private var releaseAudio: ((resume: Boolean, resumePositionMs: Long?) -> Unit)? = null

    /**
     * Mirrors the embedded player's transport state so the full player's own play/pause
     * button (and the overlay's centre button) can drive and reflect the video.
     */
    var isPlaying by mutableStateOf(false)
        internal set

    /** Last position the embedded player reported, in ms. Used to automatically resume
     *  audio at the exact position when switching back to Audio mode. */
    var positionMs by mutableLongStateOf(0L)
        internal set

    /** Length of the loaded video in ms (0 until YouTube reports it). Music videos often have
     *  intros/outros, so this is what the timeline uses in Video mode, not the song's length. */
    var durationMs by mutableLongStateOf(0L)
        internal set

    /** A seek requested before the player was ready; applied as the start offset on load. */
    private var pendingStartMs: Long? = null

    /** When [positionMs] was last set from a report or a seek (uptime ms). */
    private var positionStampMs = android.os.SystemClock.uptimeMillis()

    /**
     * After a seek, the embedded player keeps reporting the old time for a moment. Reports far
     * from the target are ignored until one lands near it (or this window runs out), so the
     * timeline doesn't jump back and forth after a seek.
     */
    private var seekTargetMs: Long? = null
    private var seekGuardUntilMs = 0L

    /**
     * The playback position right now, not just the last report: YouTube reports the time a
     * few times a second, so while playing the gap since the last report is added (capped).
     * Use this for the timeline, seeks relative to "now", and the hand-off back to audio.
     */
    fun currentPositionMs(): Long {
        val base = positionMs
        if (!isPlaying) return base
        val elapsed = (android.os.SystemClock.uptimeMillis() - positionStampMs).coerceIn(0L, MAX_EXTRAPOLATION_MS)
        val estimate = base + elapsed
        return if (durationMs > 0L) estimate.coerceAtMost(durationMs) else estimate
    }

    /** Called by the player view for every time report. */
    internal fun onReportedPosition(reportedMs: Long) {
        val now = android.os.SystemClock.uptimeMillis()
        val target = seekTargetMs
        if (target != null) {
            val landed = kotlin.math.abs(reportedMs - target) <= SEEK_LANDED_TOLERANCE_MS
            if (!landed && now < seekGuardUntilMs) return
            seekTargetMs = null
        }
        positionMs = reportedMs
        positionStampMs = now
    }

    /** Seeks the video, or queues the position if the player hasn't loaded yet. */
    fun seekTo(positionMs: Long) {
        val target = if (durationMs > 0L) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)
        this.positionMs = target
        positionStampMs = android.os.SystemClock.uptimeMillis()
        val current = player
        if (current != null && durationMs > 0L) {
            seekTargetMs = target
            seekGuardUntilMs = positionStampMs + SEEK_GUARD_MS
            current.seekTo(target / 1000f)
        } else {
            pendingStartMs = target
        }
    }

    /** Called right before a new video id is loaded; returns the start offset in seconds. */
    internal fun beginLoad(): Float {
        val start = pendingStartMs ?: positionMs
        pendingStartMs = null
        durationMs = 0L
        positionMs = start
        positionStampMs = android.os.SystemClock.uptimeMillis()
        seekTargetMs = null
        return start / 1000f
    }

    /**
     * The duration arrived. If the load started past the end of this video (the audio was
     * further in than the video is long), start the video from the beginning instead of
     * landing on its last frame.
     */
    internal fun onDurationKnown(durationMs: Long) {
        this.durationMs = durationMs
        if (positionMs >= durationMs - 1_000L) {
            positionMs = 0L
            positionStampMs = android.os.SystemClock.uptimeMillis()
            player?.let {
                seekTargetMs = 0L
                seekGuardUntilMs = positionStampMs + SEEK_GUARD_MS
                it.seekTo(0f)
            }
        }
    }

    private companion object {
        const val MAX_EXTRAPOLATION_MS = 1_500L
        const val SEEK_GUARD_MS = 2_000L
        const val SEEK_LANDED_TOLERANCE_MS = 1_200L
    }

    fun prepare(): Boolean {
        if (releaseAudio == null) releaseAudio = acquireAudio()
        return releaseAudio != null
    }

    /** Toggles the embedded player. Used by both the overlay and the main transport row. */
    fun togglePlayPause() {
        val current = player ?: return
        if (isPlaying) {
            current.pause()
        } else if (prepare()) {
            current.play()
        }
    }

    fun stop(resumeAudio: Boolean = false, resumePositionMs: Long? = null) {
        player?.pause()
        isPlaying = false
        if (resumeAudio) {
            releaseVideo?.invoke()
            releaseVideo = null
        }
        val release = releaseAudio
        releaseAudio = null
        val targetMs = if (resumeAudio) (resumePositionMs ?: positionMs) else null
        release?.invoke(resumeAudio, targetMs)
    }
}

@Composable
fun PixelVideoPlayer(
    videoId: String?,
    playback: SongVideoPlaybackController,
    onPlayerError: (String, PlayerConstants.PlayerError?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val owner = LocalLifecycleOwner.current
    val latestError by rememberUpdatedState(onPlayerError)
    // The view is created once and reused for every version the user picks, so the
    // IFrame API (the slow part: a WebView page load) only boots once per song.
    val currentVideoId by rememberUpdatedState(videoId)
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var view by remember { mutableStateOf<YouTubePlayerView?>(null) }

    LaunchedEffect(videoId, player) {
        if (videoId == null) return@LaunchedEffect
        player?.let {
            owner.lifecycle.withResumed {
                if (playback.prepare()) it.loadVideo(videoId, playback.beginLoad())
                else latestError(videoId, null)
            }
        }
    }
    DisposableEffect(owner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player?.pause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            player?.pause()
            playback.isPlaying = false
            if (playback.player === player) {
                playback.player = null
                playback.releaseVideo = null
            }
            view?.release()
        }
    }
    AndroidView(
        factory = { context ->
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                view = this
                playback.releaseVideo = {
                    view?.release()
                    view = null
                    playback.player = null
                    playback.isPlaying = false
                }
                initialize(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        if (view == null) return
                        player = youTubePlayer
                        playback.player = youTubePlayer
                    }
                    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                        if (view == null) return
                        playback.onReportedPosition((second * 1000f).toLong().coerceAtLeast(0L))
                    }
                    override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                        if (view == null) return
                        val ms = (duration * 1000f).toLong().coerceAtLeast(0L)
                        val first = playback.durationMs == 0L && ms > 0L
                        if (first) playback.onDurationKnown(ms) else playback.durationMs = ms
                    }
                    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                        playback.isPlaying = false
                        currentVideoId?.let { latestError(it, error) }
                    }
                    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                        if (state == PlayerConstants.PlayerState.PLAYING &&
                            (view == null || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !playback.prepare())) {
                            youTubePlayer.pause()
                            playback.isPlaying = false
                            return
                        }
                        playback.isPlaying = state == PlayerConstants.PlayerState.PLAYING
                    }
                }, IFramePlayerOptions.Builder(context)
                    // Native chrome off: the section's own overlay (version / prev / play / next)
                    // is the only video UI, so the two never stack.
                    .controls(0)
                    .rel(0)
                    .origin("https://${context.packageName}")
                    .build())
            }
        },
        // Sizing is the caller's job — the section sizes this to the album cover's box.
        modifier = modifier,
    )
}
