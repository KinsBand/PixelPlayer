package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.scoped.OnlineDotGreen
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

/** Online state + real flips (offline <-> online), provided once by MainActivity. */
class ConnectivityUi(
    val isOnline: StateFlow<Boolean>,
    val flips: SharedFlow<Boolean>,
    /** Downloaded song ids + fully cached video ids (see OfflineAvailability). */
    val offlinePlayableIds: StateFlow<Set<String>>,
    /** The "Disable blur" setting: offline cards then use a scrim only. */
    val blurDisabled: StateFlow<Boolean>,
)

val LocalConnectivityUi = staticCompositionLocalOf<ConnectivityUi?> { null }

/** Thin status stroke: the outline trace and the Settings header line share it. */
val ConnectivityStrokeWidth = 1.5.dp

private val alwaysOnline = MutableStateFlow(true)

/** Current online state; true when nothing provides it (previews). */
@Composable
fun rememberIsOnlineState(): State<Boolean> {
    val ui = LocalConnectivityUi.current
    return (ui?.isOnline ?: alwaysOnline).collectAsState()
}

/** Green when online (the app's presence green), the theme's error red when offline. */
@Composable
fun connectivityStatusColor(online: Boolean): Color =
    if (online) OnlineDotGreen else MaterialTheme.colorScheme.error

/**
 * One red/green trace around an outline. [progress] runs 0 → 2 (two laps): the head goes round
 * twice while the drawn tail grows, so the outline is completely filled at the end of lap two.
 * [alpha] then dissolves it.
 */
class StatusTrace {
    val progress = Animatable(0f)
    val alpha = Animatable(0f)
    var online by mutableStateOf(true)

    suspend fun run(online: Boolean, reducedMotion: Boolean) {
        // A new flip mid-trace: let the current one dissolve quickly, then start over.
        if (alpha.value > 0f) {
            alpha.animateTo(0f, tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate))
        }
        this.online = online
        if (reducedMotion) {
            progress.snapTo(2f)
            alpha.animateTo(1f, tween(MotionTokens.DurationShort4))
            delay(MotionTokens.DurationLong2.toLong())
        } else {
            progress.snapTo(0f)
            alpha.snapTo(1f)
            progress.animateTo(2f, tween(MotionTokens.DurationLong2 * 2, easing = MotionTokens.Emphasized))
        }
        alpha.animateTo(0f, tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedAccelerate))
    }
}

/** A trace that plays on every connectivity flip. */
@Composable
fun rememberStatusTrace(): StatusTrace {
    val ui = LocalConnectivityUi.current
    val reduced = rememberSystemReducedMotion()
    val trace = remember { StatusTrace() }
    LaunchedEffect(ui, reduced) {
        // collectLatest: a new flip cancels the running trace; run() then dissolves it from
        // where it is before starting the new colour.
        ui?.flips?.collectLatest { online -> trace.run(online, reduced) }
    }
    return trace
}

/**
 * Draws [trace] on top of the content, following [shape] (read in the draw phase, so an
 * animated shape is followed frame by frame). The path is inset by half the stroke, so the
 * whole line stays visible even when the layer clips to the same shape.
 */
@Composable
fun Modifier.statusOutlineTrace(trace: StatusTrace, shape: () -> Shape): Modifier {
    val color = connectivityStatusColor(trace.online)
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    return this.drawWithContent {
        drawContent()
        val a = trace.alpha.value
        if (a <= 0f) return@drawWithContent
        val strokePx = ConnectivityStrokeWidth.toPx()
        val inner = Size(size.width - strokePx, size.height - strokePx)
        if (inner.width <= 0f || inner.height <= 0f) return@drawWithContent
        path.reset()
        when (val outline = shape().createOutline(inner, layoutDirection, this)) {
            is Outline.Generic -> path.addPath(outline.path)
            is Outline.Rounded -> path.addRoundRect(outline.roundRect)
            is Outline.Rectangle -> path.addRect(outline.rect)
        }
        measure.setPath(path, false)
        val length = measure.length
        if (length <= 0f) return@drawWithContent
        val p = trace.progress.value.coerceIn(0f, 2f)
        segment.reset()
        if (p >= 2f) {
            segment.addPath(path)
        } else {
            val head = length * (p % 1f)
            val tail = length * (p / 2f)
            val start = head - tail
            if (start >= 0f) {
                measure.getSegment(start, head, segment, true)
            } else {
                measure.getSegment(length + start, length, segment, true)
                measure.getSegment(0f, head, segment, true)
            }
        }
        translate(strokePx / 2f, strokePx / 2f) {
            drawPath(segment, color, alpha = a, style = Stroke(width = strokePx, cap = StrokeCap.Round))
        }
    }
}

/**
 * A looping loading trace around [shape] while [active]: a head with a tail of about a quarter
 * of the outline travels round, in [color], with the same thin stroke as the status trace.
 * When [active] turns false it fades out from wherever it is. Reduced motion: a still outline.
 * Progress and alpha are read in the draw phase only, so the loop never recomposes.
 */
@Composable
fun Modifier.loadingOutlineTrace(active: Boolean, color: Color, shape: () -> Shape): Modifier {
    val reduced = rememberSystemReducedMotion()
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(active, reduced) {
        if (active) {
            alpha.animateTo(if (reduced) 0.6f else 1f, tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate))
            if (!reduced) {
                while (true) {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(MotionTokens.DurationLong2 * 3, easing = MotionTokens.Standard))
                }
            }
        } else {
            alpha.animateTo(0f, tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate))
        }
    }
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    return this.drawWithContent {
        drawContent()
        val a = alpha.value
        if (a <= 0f) return@drawWithContent
        val strokePx = ConnectivityStrokeWidth.toPx()
        val inner = Size(size.width - strokePx, size.height - strokePx)
        if (inner.width <= 0f || inner.height <= 0f) return@drawWithContent
        path.reset()
        when (val outline = shape().createOutline(inner, layoutDirection, this)) {
            is Outline.Generic -> path.addPath(outline.path)
            is Outline.Rounded -> path.addRoundRect(outline.roundRect)
            is Outline.Rectangle -> path.addRect(outline.rect)
        }
        measure.setPath(path, false)
        val length = measure.length
        if (length <= 0f) return@drawWithContent
        segment.reset()
        if (reduced) {
            segment.addPath(path)
        } else {
            val head = length * progress.value.coerceIn(0f, 1f)
            val start = head - length * LOADING_TRACE_TAIL
            if (start >= 0f) {
                measure.getSegment(start, head, segment, true)
            } else {
                measure.getSegment(length + start, length, segment, true)
                measure.getSegment(0f, head, segment, true)
            }
        }
        translate(strokePx / 2f, strokePx / 2f) {
            drawPath(segment, color, alpha = a, style = Stroke(width = strokePx, cap = StrokeCap.Round))
        }
    }
}

/** Share of the outline the loading trace's tail covers. */
private const val LOADING_TRACE_TAIL = 0.25f

/**
 * "Online" / "Offline" that slides out from behind the settings button (right → left), holds,
 * then slides back. Plays each time [replayKey] changes and on every connectivity flip.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectivityPeek(replayKey: Any?, modifier: Modifier = Modifier) {
    val online by rememberIsOnlineState()
    val ui = LocalConnectivityUi.current
    val reduced = rememberSystemReducedMotion()
    val shown = remember { Animatable(0f) } // 0 = hidden behind the button, 1 = fully out
    var trigger by remember { mutableStateOf(0) }
    LaunchedEffect(ui) { ui?.flips?.collect { trigger++ } }
    LaunchedEffect(replayKey, trigger, reduced) {
        if (reduced) {
            shown.snapTo(1f); delay(PeekHoldMs); shown.snapTo(0f); return@LaunchedEffect
        }
        // Retargets from wherever it is (no snap) if re-triggered mid-way.
        shown.animateTo(1f, tween(MotionTokens.DurationMedium4, easing = MotionTokens.EmphasizedDecelerate))
        delay(PeekHoldMs)
        shown.animateTo(0f, tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedAccelerate))
    }
    val color by animateColorAsState(
        connectivityStatusColor(online),
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "peekColor"
    )
    // Width follows the slide, so the button never has a gap next to it while hidden.
    Box(
        modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0))
                val f = shown.value
                val width = (placeable.width * f).toInt()
                layout(width, placeable.height) {
                    // Right-aligned: the text is revealed from the button's edge leftwards.
                    placeable.placeRelative(width - placeable.width, 0)
                }
            }
    ) {
        Text(
            text = if (online) "Online" else "Offline",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer { alpha = shown.value }
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

private const val PeekHoldMs = 2000L

/** Always-visible "Online" / "Offline" for the Settings header. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectivityStatusLabel(modifier: Modifier = Modifier) {
    val online by rememberIsOnlineState()
    val color by animateColorAsState(
        connectivityStatusColor(online),
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "statusLabelColor"
    )
    Text(
        text = if (online) "Online" else "Offline",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
