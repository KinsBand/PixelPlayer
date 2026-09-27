package com.theveloper.pixelplay.ui.overlay

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.presentation.components.ISLAND_IMMERSIVE_TEXT_SCALE
import com.theveloper.pixelplay.presentation.components.LyricsAlignment
import com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefs
import com.theveloper.pixelplay.presentation.components.LyricsFont
import com.theveloper.pixelplay.presentation.components.player.CoverLyricsColors
import com.theveloper.pixelplay.presentation.components.player.CoverSyncedLyrics
import com.theveloper.pixelplay.presentation.components.player.resolveCoverLyricsColors
import com.theveloper.pixelplay.presentation.components.rememberEffectiveLyricsFont
import com.theveloper.pixelplay.presentation.components.rememberLyricsDisplayPrefs
import com.theveloper.pixelplay.presentation.components.rememberLyricsImmersiveState
import com.theveloper.pixelplay.presentation.components.resetsLyricsImmersive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The camera-cutout island.
 *
 * One surface, one height. Everything - tap to open, drag, flick, collapse - moves the same
 * [Animatable] height, and every other property (width, corners, colour, shadow, content
 * fade) is derived from it in the layout/draw phase. So the island always grows out of the
 * pill around the camera and shrinks back into it, and a drag follows the finger 1:1.
 *
 * The window never resizes per frame: it is grown to fit before an animation starts and
 * trimmed to the resting size once it settles (see [OverlayWindowSpec]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayCutoutWidgetLayout(
    state: OverlayCutoutState,
    positionFlow: StateFlow<Long>,
    audioLevels: StateFlow<FloatArray?>,
    geometry: CutoutGeometry,
    display: IslandDisplay,
    onLevelChange: (CutoutExpansionLevel) -> Unit,
    onWindowSpec: (OverlayWindowSpec) -> Unit,
    onOpenApp: () -> Unit,
    onOpenFullScreenPlayer: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenLyricsScreen: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme

    // Sized from the display's natural (portrait) frame, never the window or the current
    // rotation: turning the phone doesn't change the metrics, so nothing re-settles or moves.
    val statusHint = if (state.coversStatusBar) 0.dp else STATUS_HINT_HEIGHT
    val metrics = remember(geometry, display.naturalWidthPx, display.naturalHeightPx, display.cornerRadiusPx, density.density, statusHint) {
        IslandMetrics(
            geometry = geometry,
            display = display.copy(rotation = android.view.Surface.ROTATION_0),
            density = density.density,
            statusHintHeight = statusHint
        )
    }
    // The window's real size as laid out (natural frame). Animations wait for this to catch
    // up with a requested resize instead of guessing a number of frames.
    val windowSize = remember { mutableStateOf(IntSize.Zero) }

    val height = remember { Animatable(metrics.height(state.expansionLevel), Dp.VectorConverter) }
    val motion = remember { IslandMotion() }
    val scope = rememberCoroutineScope()
    val windowCallback by rememberUpdatedState(onWindowSpec)
    val levelCallback by rememberUpdatedState(onLevelChange)

    fun settleTo(level: CutoutExpansionLevel, velocity: Dp = 0.dp) {
        motion.job?.cancel()
        motion.target = level
        motion.job = scope.launch {
            val target = metrics.height(level)
            if (level == CutoutExpansionLevel.COLLAPSED && height.value <= metrics.pillSize + 1.dp) {
                // Already the pill: just make sure the window is the small touch strip.
                height.snapTo(target)
                windowCallback(OverlayWindowSpec.Collapsed)
                return@launch
            }
            // Grow the window first (never shrink it mid-motion) and wait until it has really
            // been laid out at the new size, then animate inside it. Starting early let the
            // first frames play inside the old, small window: the jitter/jump on opening.
            val spec = metrics.windowFor(maxOf(height.value, target))
            windowCallback(spec)
            val needed = IslandWindowPlacement.drawingRect(spec, metrics.geometry, metrics.display, metrics.density)
            withTimeoutOrNull(300L) {
                snapshotFlow { windowSize.value }.first { size ->
                    size.width >= needed.width - 2 && size.height >= needed.height - 2
                }
            }
            withFrameMillis { }
            height.animateTo(
                targetValue = target,
                animationSpec = if (level == CutoutExpansionLevel.COLLAPSED) {
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
                } else {
                    spring(dampingRatio = 0.82f, stiffness = 360f)
                },
                initialVelocity = velocity
            )
            windowCallback(
                if (level == CutoutExpansionLevel.COLLAPSED) OverlayWindowSpec.Collapsed
                else metrics.windowFor(target)
            )
        }
    }

    // Level changes that come from outside the drag (a tap on the pill, the header ring,
    // the native touch path in the service).
    LaunchedEffect(state.expansionLevel) {
        if (!motion.dragging && motion.target != state.expansionLevel) {
            settleTo(state.expansionLevel)
        }
    }
    // Rotation / cutout change: re-settle at the same level with the new sizes.
    LaunchedEffect(metrics) {
        val target = motion.target
        if (target != null && !motion.dragging) settleTo(target)
    }

    fun requestLevel(level: CutoutExpansionLevel) {
        levelCallback(level)
        settleTo(level)
    }

    // Which content to show follows the surface's live height, so dragging between levels
    // swaps the content under the finger rather than only once the drag is released.
    val contentLevel by remember(metrics) {
        derivedStateOf {
            val h = height.value
            CutoutExpansionLevel.entries
                .filter { it != CutoutExpansionLevel.COLLAPSED }
                .minBy { kotlin.math.abs(metrics.height(it).value - h.value) }
        }
    }
    val isOpen by remember(metrics) {
        derivedStateOf { height.value > metrics.pillSize + 1.dp }
    }

    val dragModifier = Modifier.pointerInput(metrics) {
        val tracker = VelocityTracker()
        fun release() {
            motion.dragging = false
            val velocityDp = with(density) { tracker.calculateVelocity().y.toDp() }
            val level = metrics.snapLevel(height.value, velocityDp.value)
            if (level != motion.target) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            levelCallback(level)
            settleTo(level, velocityDp)
        }
        detectVerticalDragGestures(
            onDragStart = {
                motion.job?.cancel()
                motion.dragging = true
                motion.dragHeight = height.value
                tracker.resetTracking()
                windowCallback(OverlayWindowSpec.FullScreen)
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                // Accumulate here rather than reading height.value: snapTo lands a frame
                // later, and reading it back would drop part of every fast move.
                motion.dragHeight += with(density) { dragAmount.toDp() }
                val next = rubberBand(motion.dragHeight, metrics.pillSize, metrics.maxHeight)
                scope.launch { height.snapTo(next) }
            },
            onDragEnd = { release() },
            onDragCancel = { release() }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { windowSize.value = it }
            .then(dragModifier)
    ) {
        // Collapsed: the whole (small) window is the touch target, so a slightly
        // off-centre tap on the camera still registers.
        if (!isOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            requestLevel(CutoutExpansionLevel.LEVEL_1_SINGLE)
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenApp()
                        }
                    )
            )
        }

        IslandSurface(
            height = { height.value },
            metrics = metrics,
            // Same background as the lyrics sheet (the album's primary container), so the
            // island takes on each new song's colours exactly like the lyrics menu does.
            containerColor = colors.primaryContainer,
            outlineColor = colors.onPrimaryContainer.copy(alpha = 0.18f)
        ) {
            IslandContent(
                state = state,
                metrics = metrics,
                contentLevel = contentLevel,
                isOpen = isOpen,
                positionFlow = positionFlow,
                onTitleClick = onOpenFullScreenPlayer,
                onArtistClick = { onOpenArtist(state.song?.artistId ?: -1L) },
                onHandleClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenLyricsScreen()
                },
                onSeekTo = onSeekTo,
                onPlayPauseToggle = onPlayPauseToggle,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious
            )
        }

        CutoutRing(
            metrics = metrics,
            audioLevels = audioLevels,
            isPlaying = state.isPlaying,
            isOpen = isOpen,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                requestLevel(
                    if (isOpen) CutoutExpansionLevel.COLLAPSED else CutoutExpansionLevel.LEVEL_1_SINGLE
                )
            },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenApp()
            }
        )
    }
}

/**
 * Rotation lock. The window is placed on the camera edge in landscape (see
 * [IslandWindowPlacement]); this lays the island out at its portrait size and turns it so
 * it looks and responds exactly as it does in portrait. Touches follow the turn.
 */
@Composable
fun IslandRotationFrame(degrees: Float, content: @Composable () -> Unit) {
    val quarterTurn = degrees == 90f || degrees == -90f
    androidx.compose.ui.layout.Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val cw = if (quarterTurn) h else w
        val ch = if (quarterTurn) w else h
        val placeables = measurables.map { it.measure(Constraints.fixed(cw, ch)) }
        layout(w, h) {
            placeables.forEach { p ->
                p.placeWithLayer((w - cw) / 2, (h - ch) / 2) {
                    rotationZ = degrees
                    transformOrigin = TransformOrigin.Center
                }
            }
        }
    }
}

private class IslandMotion {
    var job: Job? = null
    var target: CutoutExpansionLevel? = null
    var dragging = false
    var dragHeight: Dp = 0.dp
}

/** Past either end the surface still moves, at a third of the finger's speed. */
private fun rubberBand(value: Dp, min: Dp, max: Dp): Dp = when {
    value < min -> min - (min - value) * 0.33f
    value > max -> max + (value - max) * 0.33f
    else -> value
}

// ── Surface ───────────────────────────────────────────────────────────────────────────

/**
 * The morphing body. Size, position, corners, colour and shadow are read from [height] in
 * the layout and draw phases only, so an animation or drag frame never recomposes.
 * Content is laid out at the full expanded width and revealed by the clip, so text never
 * reflows while the island grows.
 */
@Composable
private fun IslandSurface(
    height: () -> Dp,
    metrics: IslandMetrics,
    containerColor: Color,
    outlineColor: Color,
    content: @Composable () -> Unit
) {
    val pillColor = Color.Black
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val h = height()
                val c = metrics.morphFraction(h)
                val widthEase = FastOutSlowInEasing.transform(c)
                val w = lerp(metrics.pillSize, metrics.expandedWidth, widthEase).roundToPx()
                val top = lerp(metrics.pillTop, metrics.surfaceTop, widthEase).roundToPx()
                val hPx = h.roundToPx().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w.coerceAtLeast(1), hPx))
                // Centre moves from the camera (pill) to the middle of the screen (open).
                // The camera position is absolute, so the pill lands on the hole in every
                // window size and never jumps sideways when the window is swapped.
                val pillCenter = metrics.cameraCenterXPx - metrics.windowLeftPx(constraints.maxWidth)
                val openCenter = constraints.maxWidth / 2f
                val centerX = pillCenter + (openCenter - pillCenter) * widthEase
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((centerX - placeable.width / 2f).roundToInt(), top)
                }
            }
            .graphicsLayer {
                val c = metrics.morphFraction(height())
                // Pill → open: the top corners become the screen's own corner radius (the
                // surface touches the top and both sides), the bottom corners a softer card.
                val top = lerp(metrics.pillSize / 2, metrics.screenCornerRadius, c).toPx()
                val bottom = lerp(metrics.pillSize / 2, metrics.bottomCornerRadius, c).toPx()
                shape = RoundedCornerShape(
                    topStart = top,
                    topEnd = top,
                    bottomEnd = bottom,
                    bottomStart = bottom
                )
                clip = true
                shadowElevation = (14.dp * c).toPx()
            }
            .drawBehind {
                val c = metrics.morphFraction(height())
                // Black while it is the pill (it is the camera), the song's surface once open.
                // Fully opaque, so the status bar underneath is hidden while the island is open.
                drawRect(lerp(pillColor, containerColor, c))
                if (c > 0f) {
                    // Hairline only along the bottom edge, where the island floats over the app.
                    val bottom = lerp(metrics.pillSize / 2, metrics.bottomCornerRadius, c).toPx()
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        color = outlineColor.copy(alpha = 0.5f * c),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - bottom * 2).coerceAtLeast(0f)),
                        size = androidx.compose.ui.geometry.Size(size.width, bottom * 2),
                        cornerRadius = CornerRadius(bottom, bottom),
                        style = Stroke(width = stroke)
                    )
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .requiredWidth(metrics.expandedWidth)
                .fillMaxSize()
                .graphicsLayer {
                    val c = metrics.morphFraction(height())
                    val reveal = ((c - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    alpha = reveal
                    val s = 0.94f + 0.06f * reveal
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
        ) {
            content()
        }
    }
}

/**
 * The small black pill on the camera plus a coloured wave ring around it that moves with the
 * music: each of the [IslandAudioReactor.BANDS] bands drives one lobe count around the
 * circle, so bass swells the ring broadly and highs add fine ripples. Always on top, in both
 * states; tap toggles the island, hold opens the app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CutoutRing(
    metrics: IslandMetrics,
    audioLevels: StateFlow<FloatArray?>,
    isPlaying: Boolean,
    isOpen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val ringColor by animateColorAsState(
        targetValue = when {
            isPlaying -> scheme.primary
            isOpen -> scheme.outline.copy(alpha = 0.55f)
            else -> scheme.outline.copy(alpha = 0.4f)
        },
        animationSpec = tween(durationMillis = 450),
        label = "cutoutRing"
    )
    val wave = rememberWaveMotion(audioLevels, isPlaying)
    val path = remember { androidx.compose.ui.graphics.Path() }
    val echoPath = remember { androidx.compose.ui.graphics.Path() }
    val boxSize = metrics.pillSize + metrics.ringReach * 2

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // Pinned on the camera itself (absolute), not the window's centre.
                val placeable = measurable.measure(Constraints())
                val top = (metrics.pillTop - metrics.ringReach).coerceAtLeast(0.dp).roundToPx()
                val cx = metrics.cameraCenterXPx - metrics.windowLeftPx(constraints.maxWidth)
                layout(constraints.maxWidth, top + placeable.height) {
                    placeable.place((cx - placeable.width / 2f).roundToInt(), top)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .size(boxSize)
                .drawBehind {
                    wave.frame.longValue // redraw on every wave frame (draw phase only)
                    val center = this.center
                    val pillRadius = metrics.pillSize.toPx() / 2
                    drawCircle(Color.Black, radius = pillRadius, center = center)

                    val base = pillRadius + 2.5.dp.toPx() + wave.energy * 1.dp.toPx()
                    val amplitude = 3.5.dp.toPx()
                    buildWavePath(path, center, base, amplitude, wave.bands, wave.phase, 0f)
                    buildWavePath(echoPath, center, base, amplitude * 0.7f, wave.bands, wave.phase * 0.8f, 1.9f)
                    drawPath(
                        echoPath,
                        color = ringColor.copy(alpha = ringColor.alpha * 0.35f),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawPath(
                        path,
                        color = ringColor,
                        style = Stroke(width = 1.6.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    )
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        )
    }
}

private const val WAVE_POINTS = 120

/** A closed ring whose radius at angle θ is [base] plus a sum of band-weighted lobes. */
private fun buildWavePath(
    path: androidx.compose.ui.graphics.Path,
    center: androidx.compose.ui.geometry.Offset,
    base: Float,
    amplitude: Float,
    bands: FloatArray,
    phase: Float,
    phaseOffset: Float
) {
    path.reset()
    val twoPi = (2 * Math.PI).toFloat()
    for (p in 0..WAVE_POINTS) {
        val theta = twoPi * p / WAVE_POINTS
        var d = 0f
        for (i in bands.indices) {
            val lobes = i + 2
            // Alternate directions so the ring shimmers instead of just spinning.
            val dir = if (i % 2 == 0) 1f else -1f
            d += bands[i] * kotlin.math.sin(lobes * theta + dir * phase * (1f + i * 0.35f) + i * 1.3f + phaseOffset)
        }
        d = (d / bands.size * 2.4f).coerceIn(-0.45f, 1f)
        val r = base + amplitude * d
        val x = center.x + r * kotlin.math.cos(theta)
        val y = center.y + r * kotlin.math.sin(theta)
        if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

/** Smoothed band levels + a running phase, advanced on the frame clock. */
private class WaveMotion(bandCount: Int) {
    val bands = FloatArray(bandCount)
    var phase = 0f
    var energy = 0f
    val frame = mutableLongStateOf(0L)
}

/**
 * Follows the live levels with a fast attack and slow release so beats punch and fade.
 * Without live audio (no microphone permission, or the device refuses the Visualizer) it
 * breathes on its own while playing. The frame loop stops once paused and settled.
 */
@Composable
private fun rememberWaveMotion(levels: StateFlow<FloatArray?>, isPlaying: Boolean): WaveMotion {
    val motion = remember { WaveMotion(IslandAudioReactor.BANDS) }
    LaunchedEffect(isPlaying) {
        var last = 0L
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 16f else (now - last).coerceIn(1L, 64L).toFloat()
            last = now
            val live = levels.value
            var total = 0f
            for (i in motion.bands.indices) {
                val target = when {
                    !isPlaying -> 0f
                    live != null -> live.getOrElse(i) { 0f }
                    else -> (0.32f + 0.22f * kotlin.math.sin(now * 0.0035f + i * 1.7f) *
                        kotlin.math.sin(now * 0.0013f + i * 0.9f)).coerceIn(0f, 1f) * 0.7f
                }
                val current = motion.bands[i]
                val rate = if (target > current) 0.55f else 0.12f
                val k = 1f - Math.pow((1f - rate).toDouble(), (dt / 16f).toDouble()).toFloat()
                motion.bands[i] = current + (target - current) * k
                total += motion.bands[i]
            }
            motion.energy = total / motion.bands.size
            motion.phase += dt * 0.0011f * (0.5f + motion.energy)
            motion.frame.longValue = now
            if (!isPlaying && motion.energy < 0.005f) break
        }
    }
    return motion
}

// ── Content ───────────────────────────────────────────────────────────────────────────

@Composable
private fun IslandContent(
    state: OverlayCutoutState,
    metrics: IslandMetrics,
    contentLevel: CutoutExpansionLevel,
    isOpen: Boolean,
    positionFlow: StateFlow<Long>,
    onTitleClick: () -> Unit,
    onArtistClick: () -> Unit,
    onHandleClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    // Same settings as the lyrics sheet and the cover, straight from the lyrics menu:
    // animated lyrics, blur on/off and strength, highlight mode, translation, romanization,
    // text size, font, immersive lyrics + auto-hide delay, and the per-song sync offset.
    // Only alignment is fixed (centre): the island is a small symmetric surface around the
    // camera, and a left/right-aligned line would sit under the camera hole.
    val prefs by rememberLyricsDisplayPrefs()
    val islandPrefs = remember(prefs) { prefs.copy(alignment = LyricsAlignment.CENTER) }
    // Same font as the sheet and cover, with the same per-song glyph fallback.
    val font by rememberEffectiveLyricsFont(state.lyrics, prefs.font)
    // Text and highlight exactly as on the album cover / lyrics sheet: on-primary-container
    // text, with the bright warm accent picked against the primary container.
    val lyricColors = remember(scheme.onPrimaryContainer, scheme.primaryContainer, scheme.tertiary) {
        resolveCoverLyricsColors(
            scheme.onPrimaryContainer,
            com.theveloper.pixelplay.presentation.components.resolveBrightWarmColor(
                background = scheme.primaryContainer,
                preferredWarm = scheme.tertiary
            )
        )
    }
    val position = rememberSmoothPosition(positionFlow, isPlaying = state.isPlaying, running = isOpen)
    val syncOffset = rememberUpdatedState(state.lyricsSyncOffsetMs)
    val positionProvider: () -> Long = remember(position) {
        { (position.value + syncOffset.value).coerceAtLeast(0L) }
    }
    // A tap on a line seeks to where that line starts once the offset is applied, exactly
    // like the sheet (seekTimestampForLine).
    val seekToLine: (Long) -> Unit = { lineTime ->
        onSeekTo((lineTime - syncOffset.value).coerceAtLeast(0L))
    }

    // Immersive lyrics: after the auto-hide delay with no touch on the island, the controls
    // tuck away and the lyrics grow, as in the sheet. Any touch brings them back.
    val immersiveState = rememberLyricsImmersiveState(
        prefs = prefs,
        active = isOpen && state.syncedLines.isNotEmpty()
    )
    val immersive = immersiveState.isImmersive

    Column(modifier = Modifier.fillMaxSize().resetsLyricsImmersive(immersiveState)) {
        IslandHeader(
            state = state,
            metrics = metrics,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick
        )
        if (metrics.statusHintHeight > 0.dp) {
            StatusBarCoverHint(height = metrics.statusHintHeight)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isOpen) {
                Crossfade(
                    targetState = contentLevel,
                    animationSpec = tween(durationMillis = 220),
                    label = "islandContentLevel"
                ) { level ->
                    LevelContent(
                        level = level,
                        state = state,
                        prefs = islandPrefs,
                        font = font,
                        lyricColors = lyricColors,
                        positionProvider = positionProvider,
                        immersive = immersive,
                        onSeekTo = seekToLine,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious
                    )
                }
            }
        }

        // Tap: open the app's lyrics screen. Drag anywhere on the island resizes it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.handleHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onHandleClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(scheme.onPrimaryContainer.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun IslandHeader(
    state: OverlayCutoutState,
    metrics: IslandMetrics,
    onTitleClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.headerHeight)
            // Clear of the screen's curved corners at the top.
            .padding(horizontal = maxOf(18.dp, metrics.screenCornerRadius * 0.5f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTitleClick
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            SlidingText(
                text = state.song?.title ?: "Nothing playing",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = scheme.onPrimaryContainer,
                textAlign = TextAlign.Start
            )
        }

        // Keeps the text clear of the camera and its wave ring.
        Spacer(modifier = Modifier.width(metrics.pillSize + metrics.ringReach * 2 + 12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onArtistClick
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            SlidingText(
                text = state.song?.artist ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                textAlign = TextAlign.End
            )
        }
    }
}

private val STATUS_HINT_HEIGHT = 34.dp

/**
 * Shown while the island uses the plain "Appear on top" window: Android always draws the
 * status bar over those windows, so the time and icons show through the header. Turning on
 * the PixelPlayer island accessibility service moves the island above the status bar.
 */
@Composable
private fun StatusBarCoverHint(height: Dp) {
    val scheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 16.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(scheme.onPrimaryContainer.copy(alpha = 0.12f))
                .clickable {
                    runCatching {
                        context.startActivity(OverlayWidgetManager.createAccessibilitySettingsIntent())
                    }
                }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Cover the status bar: turn on the PixelPlayer island in Accessibility ›",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/** Title/artist: a track change slides the new text up in place of the old. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlidingText(text: String, style: TextStyle, color: Color, textAlign: TextAlign) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(300)))
                .togetherWith(slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(200)))
        },
        label = "islandHeaderText"
    ) { value ->
        Text(
            text = value,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1800)
        )
    }
}

@Composable
private fun LevelContent(
    level: CutoutExpansionLevel,
    state: OverlayCutoutState,
    prefs: LyricsDisplayPrefs,
    font: LyricsFont,
    lyricColors: CoverLyricsColors,
    positionProvider: () -> Long,
    immersive: Boolean,
    onSeekTo: (Long) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val typography = MaterialTheme.typography
    val immersiveScale by animateFloatAsState(
        targetValue = if (immersive) ISLAND_IMMERSIVE_TEXT_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "islandImmersiveTextScale"
    )
    val sizeMultiplier = prefs.textSize.multiplier * immersiveScale
    fun lyricStyle(size: Float) = typography.titleMedium.copy(
        fontSize = (size * sizeMultiplier).sp,
        lineHeight = (size * sizeMultiplier * 1.25f).sp,
        textMotion = TextMotion.Animated
    )
    val onLineClick: (SyncedLine) -> Unit = { onSeekTo(it.time.toLong()) }
    val showControls = level == CutoutExpansionLevel.LEVEL_3_SIX || level == CutoutExpansionLevel.LEVEL_4_FULL

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val lines = state.syncedLines
            if (lines.isEmpty()) {
                Text(
                    text = if (level == CutoutExpansionLevel.LEVEL_1_SINGLE) state.fallbackLine else "No synced lyrics",
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                // Keyed on the song so a track change starts a fresh roll instead of
                // animating from the old song's line index.
                androidx.compose.runtime.key(state.song?.id) {
                    when (level) {
                        CutoutExpansionLevel.LEVEL_1_SINGLE -> CoverSyncedLyrics(
                            lines = lines,
                            positionProvider = positionProvider,
                            prefs = prefs,
                            font = font,
                            style = lyricStyle(16f),
                            colors = lyricColors,
                            windowRadius = 1,
                            rowMaxHeightFraction = 1f,
                            horizontalPadding = 16.dp,
                            onLineClick = onLineClick,
                            immersive = immersive
                        )
                        CutoutExpansionLevel.LEVEL_2_THREE -> CoverSyncedLyrics(
                            lines = lines,
                            positionProvider = positionProvider,
                            prefs = prefs,
                            font = font,
                            style = lyricStyle(16f),
                            colors = lyricColors,
                            windowRadius = 2,
                            rowMaxHeightFraction = 0.5f,
                            horizontalPadding = 16.dp,
                            onLineClick = onLineClick,
                            immersive = immersive
                        )
                        CutoutExpansionLevel.LEVEL_3_SIX -> CoverSyncedLyrics(
                            lines = lines,
                            positionProvider = positionProvider,
                            prefs = prefs,
                            font = font,
                            style = lyricStyle(17f),
                            colors = lyricColors,
                            windowRadius = 3,
                            rowMaxHeightFraction = 0.4f,
                            horizontalPadding = 16.dp,
                            onLineClick = onLineClick,
                            immersive = immersive
                        )
                        else -> CoverSyncedLyrics(
                            lines = lines,
                            positionProvider = positionProvider,
                            prefs = prefs,
                            font = font,
                            style = lyricStyle(24f),
                            colors = lyricColors,
                            windowRadius = 6,
                            rowMaxHeightFraction = 0.3f,
                            horizontalPadding = 20.dp,
                            onLineClick = onLineClick,
                            immersive = immersive
                        )
                    }
                }
            }
        }

        // Controls auto-hide in immersive, like the sheet's controls.
        AnimatedVisibility(
            visible = showControls && !immersive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            IslandControls(
                isPlaying = state.isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious
            )
        }
    }
}

/** Same shape language as the app's mini player: tonal skip buttons around a primary play. */
@Composable
private fun IslandControls(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IslandMetrics.CONTROLS_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            size = 46.dp,
            container = scheme.onPrimaryContainer.copy(alpha = 0.12f),
            onClick = onSkipPrevious
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        ControlButton(
            size = 54.dp,
            container = scheme.primary,
            onClick = onPlayPauseToggle
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (scaleIn(tween(220), initialScale = 0.6f) + fadeIn(tween(180)))
                        .togetherWith(scaleOut(tween(180), targetScale = 0.6f) + fadeOut(tween(140)))
                },
                label = "islandPlayPause"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        ControlButton(
            size = 46.dp,
            container = scheme.onPrimaryContainer.copy(alpha = 0.12f),
            onClick = onSkipNext
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ControlButton(
    size: Dp,
    container: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ── Position ──────────────────────────────────────────────────────────────────────────

/**
 * Playback position advanced on the frame clock between the service's coarse ticks, so the
 * lyric roll and word highlight move every frame. Never steps backwards for the small
 * corrections a tick brings; a real seek (a jump of more than half a second) is followed.
 * Only runs while the island is open.
 */
@Composable
private fun rememberSmoothPosition(
    positionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    running: Boolean
): State<Long> {
    val base by positionFlow.collectAsState()
    val out = remember { mutableLongStateOf(base) }
    LaunchedEffect(base, isPlaying, running) {
        val current = out.longValue
        val anchor = if (isPlaying && base in (current - 500)..current) current else base
        out.longValue = anchor
        if (!isPlaying || !running) return@LaunchedEffect
        val anchorAt = SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis { }
            val elapsed = (SystemClock.uptimeMillis() - anchorAt).coerceAtMost(2_000L)
            out.longValue = anchor + elapsed
        }
    }
    return out
}
