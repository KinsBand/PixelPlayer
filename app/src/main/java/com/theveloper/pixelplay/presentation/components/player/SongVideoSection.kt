package com.theveloper.pixelplay.presentation.components.player

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicVideo
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.VideoType
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.presentation.components.PixelVideoPlayer
import com.theveloper.pixelplay.presentation.components.SongVideoPlaybackController
import com.theveloper.pixelplay.presentation.viewmodel.SongVideoViewModel
import kotlinx.coroutines.delay

/** How long the tap-to-reveal overlay stays up before fading out again. */
private const val VIDEO_OVERLAY_TIMEOUT_MS = 3500L

/** Double-tap on the left / right third of the video seeks by this much. */
private const val VIDEO_DOUBLE_TAP_SEEK_MS = 10_000L

/** Matches AlbumCarouselSection's item corner so the video reads as the album art's replacement. */
private val VideoCorner = 18.dp

private val SpringySpec = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

/**
 * Drop-in replacement for the album cover section while the player is in Video mode.
 *
 * Inline, it occupies the *exact* box FullPlayerAlbumCoverSection would have occupied — same
 * vertical padding, same carousel-style-derived size, same corner radius — so switching
 * Audio <-> Video never reflows the rest of the player. By default the video fills that box
 * (cropping the sides of a 16:9 clip) instead of sitting in a thin letterbox; a button in the
 * bottom-left switches back to the whole frame.
 *
 * With [fullscreen] it fills the window instead (the caller rotates the device to landscape).
 * Both layouts share one composition structure, so the embedded player is never torn down
 * when switching between them — the caller keeps this section in a movable content slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongVideoSection(
    song: Song,
    playback: SongVideoPlaybackController,
    carouselStyle: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    viewModel: SongVideoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var versionSheetOpen by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }
    // Bumped on every interaction with the overlay so the auto-hide timer restarts.
    var interactionTick by remember { mutableIntStateOf(0) }
    var fillCover by rememberSaveable { mutableStateOf(true) }
    // -1 / +1 while the double-tap seek bubble is showing on that side.
    var seekHint by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    val bump: () -> Unit = {
        overlayVisible = true
        interactionTick++
    }

    LaunchedEffect(song.id, song.title, song.displayArtist) {
        overlayVisible = true
        viewModel.search(song)
    }
    LaunchedEffect(state.error) {
        if (state.error != null && state.selected == null) playback.stop(resumeAudio = true)
    }
    DisposableEffect(playback) {
        onDispose {
            playback.stop()
            viewModel.cancelSearch()
        }
    }
    LaunchedEffect(fullscreen) { bump() }
    // Auto-hide, but never while the version sheet is open, and never while the video is
    // paused — the centre button has to stay reachable to resume.
    LaunchedEffect(overlayVisible, versionSheetOpen, playback.isPlaying, interactionTick) {
        if (overlayVisible && !versionSheetOpen && playback.isPlaying) {
            delay(VIDEO_OVERLAY_TIMEOUT_MS)
            overlayVisible = false
        }
    }
    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(650)
            seekHint = null
        }
    }

    val openInYouTube: () -> Unit = {
        playback.player?.pause()
        val url = state.selected?.id?.let { "https://www.youtube.com/watch?v=$it" }
            ?: "https://www.youtube.com/results?search_query=" +
            Uri.encode("${song.displayArtist} ${song.title} ${state.type.searchQuerySuffix}")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        Unit
    }

    // One structure for both layouts (only modifiers change) so the player view survives
    // switching in and out of full screen.
    BoxWithConstraints(
        modifier = if (fullscreen) {
            modifier.fillMaxSize().background(Color.Black)
        } else {
            modifier.fillMaxWidth().padding(vertical = 8.dp)
        }
    ) {
        // Mirrors FullPlayerAlbumCoverSection's carouselHeight so the footprint is identical.
        val coverSize = when (carouselStyle) {
            CarouselStyle.NO_PEEK -> maxWidth
            CarouselStyle.ONE_PEEK -> maxWidth * 0.8f
            CarouselStyle.TWO_PEEK -> maxWidth * 0.6f
            else -> maxWidth * 0.8f
        }
        val fill by animateFloatAsState(
            targetValue = if (fillCover) 1f else 0f,
            animationSpec = tween(320),
            label = "videoFill"
        )
        // Fit: full 16:9 frame across the box. Fill: 16:9 frame as tall as the box, sides cropped.
        val videoWidth = lerp(coverSize, coverSize * (16f / 9f), fill)
        val videoHeight = lerp(coverSize * (9f / 16f), coverSize, fill)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(
                    if (fullscreen) Modifier.fillMaxSize()
                    else Modifier.size(coverSize).clip(RoundedCornerShape(VideoCorner))
                )
                .background(Color.Black)
        ) {
            val selected = state.selected
            val isSearching = state.songId != song.id || state.loading

            // The player view is composed straight away (even while the search is still
            // running) so the embedded YouTube page boots in parallel with the lookup, and it is
            // not keyed by video id so switching versions reuses the already-initialised player.
            val showPlayer = isSearching || selected != null
            if (showPlayer) {
                PixelVideoPlayer(
                    videoId = if (isSearching) null else selected?.id,
                    playback = playback,
                    onPlayerError = { id, error ->
                        val tryNext = error == PlayerConstants.PlayerError.VIDEO_NOT_FOUND ||
                            error == PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER
                        viewModel.playbackFailed(
                            id,
                            if (error == null) "Audio player is not ready. Return to Audio and try again."
                            else "This video cannot play here. Choose another version or open YouTube.",
                            tryNext
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(
                            if (fullscreen) Modifier.fillMaxSize()
                            else Modifier.requiredSize(videoWidth, videoHeight)
                        )
                )
            }
            val waitingForFirstFrame = selected != null && !isSearching &&
                playback.durationMs == 0L && !playback.isPlaying
            if (isSearching || waitingForFirstFrame) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            } else if (selected == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.error ?: "No matching versions found.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.search(song, refresh = true) }) {
                            Text("Retry", color = Color.White)
                        }
                        TextButton(onClick = { versionSheetOpen = true }) {
                            Text("Other versions", color = Color.White)
                        }
                    }
                }
            }

            if (selected != null && !isSearching) {
                // Swallows touches so the embedded player never handles them itself. Tap toggles
                // the overlay; double-tap on the left / right third seeks back / forward.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { overlayVisible = !overlayVisible },
                                onDoubleTap = { offset ->
                                    val third = size.width / 3f
                                    when {
                                        offset.x < third -> {
                                            playback.seekTo(playback.currentPositionMs() - VIDEO_DOUBLE_TAP_SEEK_MS)
                                            seekHint = -1 to System.nanoTime()
                                        }
                                        offset.x > third * 2 -> {
                                            playback.seekTo(playback.currentPositionMs() + VIDEO_DOUBLE_TAP_SEEK_MS)
                                            seekHint = 1 to System.nanoTime()
                                        }
                                        else -> playback.togglePlayPause()
                                    }
                                }
                            )
                        }
                )

                // Double-tap seek feedback, on the side that was tapped.
                AnimatedVisibility(
                    visible = seekHint != null,
                    modifier = Modifier
                        .align(if ((seekHint?.first ?: 1) < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = if (fullscreen) 72.dp else 20.dp),
                    enter = fadeIn() + scaleIn(initialScale = 0.7f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f)
                ) {
                    Text(
                        text = if ((seekHint?.first ?: 1) < 0) "−10s" else "+10s",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                AnimatedVisibility(
                    visible = overlayVisible,
                    modifier = Modifier.matchParentSize(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    VideoControlsOverlay(
                        song = song,
                        typeLabel = state.type.displayName,
                        playback = playback,
                        fullscreen = fullscreen,
                        fillCover = fillCover,
                        onVersionClick = {
                            bump()
                            versionSheetOpen = true
                        },
                        onPrevious = {
                            bump()
                            playback.stop()
                            onPrevious()
                        },
                        onPlayPause = {
                            bump()
                            playback.togglePlayPause()
                        },
                        onNext = {
                            bump()
                            playback.stop()
                            onNext()
                        },
                        onToggleFill = {
                            bump()
                            fillCover = !fillCover
                        },
                        onToggleFullscreen = {
                            bump()
                            onToggleFullscreen()
                        },
                        onInteraction = bump
                    )
                }
            }
        }
    }

    if (versionSheetOpen) {
        VideoVersionSheet(
            song = song,
            selectedType = state.type,
            loading = state.loading,
            candidates = state.candidates,
            selectedId = state.selected?.id,
            onTypeSelected = { type ->
                if (type != state.type) {
                    playback.player?.pause()
                    viewModel.search(song, type)
                }
            },
            onVideoSelected = { video ->
                playback.player?.pause()
                viewModel.select(video)
                versionSheetOpen = false
            },
            onOpenInYouTube = {
                versionSheetOpen = false
                openInYouTube()
            },
            onDismiss = { versionSheetOpen = false }
        )
    }
}

/** Everything drawn over the video while the overlay is showing. */
@Composable
private fun VideoControlsOverlay(
    song: Song,
    typeLabel: String,
    playback: SongVideoPlaybackController,
    fullscreen: Boolean,
    fillCover: Boolean,
    onVersionClick: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleFill: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onInteraction: () -> Unit,
) {
    val edge = if (fullscreen) 20.dp else 12.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.6f),
                    0.3f to Color.Black.copy(alpha = 0.12f),
                    0.7f to Color.Black.copy(alpha = 0.12f),
                    1f to Color.Black.copy(alpha = 0.65f)
                )
            )
    ) {
        // Top: title (full screen only) + version chip
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(edge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fullscreen) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.displayArtist,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            VideoVersionChip(label = typeLabel, onClick = onVersionClick)
        }

        // Centre: previous · play/pause · next
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 40.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoTransportButton(
                iconRes = R.drawable.rounded_skip_previous_filled_24,
                contentDescription = "Previous",
                size = if (fullscreen) 60.dp else 50.dp,
                iconSize = if (fullscreen) 32.dp else 28.dp,
                onClick = onPrevious
            )
            VideoTransportButton(
                iconRes = if (playback.isPlaying) R.drawable.rounded_pause_filled_24
                else R.drawable.rounded_play_arrow_filled_24,
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                size = if (fullscreen) 84.dp else 72.dp,
                iconSize = if (fullscreen) 44.dp else 38.dp,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                // Morphs: round while paused, squircle while playing (like the main play button).
                cornerPercent = if (playback.isPlaying) 30 else 50,
                onClick = onPlayPause
            )
            VideoTransportButton(
                iconRes = R.drawable.rounded_skip_next_filled_24,
                contentDescription = "Next",
                size = if (fullscreen) 60.dp else 50.dp,
                iconSize = if (fullscreen) 32.dp else 28.dp,
                onClick = onNext
            )
        }

        // Bottom: fit/fill (inline) or the seek bar (full screen), full-screen toggle bottom-right.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = edge, vertical = if (fullscreen) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fullscreen) {
                VideoSeekBar(
                    playback = playback,
                    onInteraction = onInteraction,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
            } else {
                VideoIconButton(
                    icon = if (fillCover) Icons.Rounded.FitScreen else Icons.Rounded.CropFree,
                    contentDescription = if (fillCover) "Show whole video" else "Fill the cover",
                    onClick = onToggleFill
                )
                Spacer(Modifier.weight(1f))
            }
            VideoIconButton(
                icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = if (fullscreen) "Exit full screen" else "Full screen",
                onClick = onToggleFullscreen
            )
        }
    }
}

@Composable
private fun VideoSeekBar(
    playback: SongVideoPlaybackController,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = playback.durationMs
    var scrub by remember { mutableStateOf<Float?>(null) }
    // Live seeking while dragging (throttled), so the frame follows the thumb.
    var lastLiveSeekAt by remember { mutableLongStateOf(0L) }
    val progress = scrub ?: if (duration > 0L) playback.currentPositionMs().toFloat() / duration else 0f
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatVideoTime(if (scrub != null) (scrub!! * duration).toLong() else playback.positionMs),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = {
                scrub = it
                onInteraction()
                val now = android.os.SystemClock.uptimeMillis()
                if (duration > 0L && now - lastLiveSeekAt >= LIVE_SEEK_INTERVAL_MS) {
                    lastLiveSeekAt = now
                    playback.seekTo((it * duration).toLong())
                }
            },
            onValueChangeFinished = {
                scrub?.let { playback.seekTo((it * duration).toLong()) }
                scrub = null
            },
            enabled = duration > 0L,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        Text(
            text = formatVideoTime(duration),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private const val LIVE_SEEK_INTERVAL_MS = 250L

private fun formatVideoTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun VideoVersionChip(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.SmartDisplay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
        Icon(
            painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
            contentDescription = "Change video",
            tint = Color.White,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(18.dp)
        )
    }
}

/** Round (or squircle) transport button with a springy press. */
@Composable
private fun VideoTransportButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 28.dp,
    container: Color = Color.Black.copy(alpha = 0.5f),
    content: Color = Color.White,
    cornerPercent: Int = 50,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, SpringySpec, label = "videoBtnScale")
    val corner by animateIntAsState(
        targetValue = cornerPercent,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "videoBtnCorner"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = content),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun VideoIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, SpringySpec, label = "videoIconScale")
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = interaction, indication = ripple(color = Color.White), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

private fun VideoType.icon(): ImageVector = when (this) {
    VideoType.MUSIC_VIDEO -> Icons.Rounded.MusicVideo
    VideoType.LIVE_PERFORMANCE -> Icons.Rounded.LiveTv
    VideoType.LYRIC_VIDEO -> Icons.Rounded.Subtitles
    VideoType.ACOUSTIC -> Icons.Rounded.Mic
    VideoType.REMIX -> Icons.Rounded.GraphicEq
    VideoType.COVER -> Icons.Rounded.People
    VideoType.ALL -> Icons.Rounded.VideoLibrary
}

/**
 * The "which video" picker, styled like the app's other player sheets: version pills on top,
 * the videos found for that version underneath (updates live when the version changes), and
 * "Open in YouTube" at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VideoVersionSheet(
    song: Song,
    selectedType: VideoType,
    loading: Boolean,
    candidates: List<com.theveloper.pixelplay.data.model.TrackVideo>,
    selectedId: String?,
    onTypeSelected: (VideoType) -> Unit,
    onVideoSelected: (com.theveloper.pixelplay.data.model.TrackVideo) -> Unit,
    onOpenInYouTube: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                text = "Video",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Text(
                text = "${song.title} · ${song.displayArtist}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoType.entries.forEach { type ->
                    val selected = type == selectedType
                    val corner by animateIntAsState(if (selected) 30 else 50, label = "typePill")
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(corner))
                            .background(if (selected) colors.primary else colors.surfaceContainerHighest)
                            .clickable { onTypeSelected(type) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.Check else type.icon(),
                            contentDescription = null,
                            tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) colors.onPrimary else colors.onSurface,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Videos found",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary
            )
            Spacer(Modifier.height(8.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                candidates.isEmpty() -> Text(
                    text = "No videos found for this version.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(candidates, key = { it.id }) { video ->
                        val isSelected = video.id == selectedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) colors.secondaryContainer else colors.surfaceContainer)
                                .clickable { onVideoSelected(video) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 104.dp, height = 58.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceContainerHighest)
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = video.channel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Playing",
                                    tint = colors.primary,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onOpenInYouTube,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Open in YouTube", modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
