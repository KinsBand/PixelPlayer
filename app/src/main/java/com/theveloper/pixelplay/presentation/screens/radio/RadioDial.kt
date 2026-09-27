package com.theveloper.pixelplay.presentation.screens.radio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.radio.DialLayout
import com.theveloper.pixelplay.data.radio.DialStop
import com.theveloper.pixelplay.data.radio.RadioBand
import com.theveloper.pixelplay.data.radio.RadioScope
import com.theveloper.pixelplay.data.radio.RadioScopeFilter
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.presentation.viewmodel.RadioUiState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** How much of the dial one full turn of the knob covers. */
private const val KNOB_TURN_FRACTION = 0.25f
/** Pause on a station this long before it starts playing, so spinning past doesn't start ten streams. */
private const val TUNE_SETTLE_MS = 450L
private const val SCAN_DWELL_MS = 7_000L

/**
 * A tuner that works like a real radio: FM/AM stations sit at their broadcast frequency,
 * everything else goes on a virtual WEB band ordered west → east. Drag the scale or turn the
 * knob; it snaps to the nearest station and starts playing once you stop.
 */
@Composable
fun RadioDial(
    state: RadioUiState,
    presets: List<RadioStation?>,
    playingUuid: String?,
    isPlaying: Boolean,
    bottomPadding: Dp,
    onTune: (RadioStation) -> Unit,
    onPlayPause: () -> Unit,
    onSavePreset: (Int, RadioStation) -> Unit,
) {
    val stations = state.stations
    val bandCounts = remember(stations) {
        mapOf(
            RadioBand.FM to stations.count { it.frequency?.band == RadioBand.FM },
            RadioBand.AM to stations.count { it.frequency?.band == RadioBand.AM },
            RadioBand.WEB to stations.size,
        )
    }
    // Real frequencies only mean something close to home; further out everything goes on WEB.
    val defaultBand = remember(state.scope, bandCounts) {
        if (state.scope <= RadioScope.REGION && (bandCounts[RadioBand.FM] ?: 0) >= 3) RadioBand.FM else RadioBand.WEB
    }
    var band by remember(defaultBand) { mutableStateOf(defaultBand) }
    val layout = remember(band, stations, state.scope) {
        DialLayout.build(band, stations) { s ->
            if (state.scope == RadioScope.WORLD || state.scope == RadioScope.COUNTRY) RadioScopeFilter.groupKey(state.scope, s) else ""
        }
    }
    val tolerance = when (band) {
        RadioBand.FM -> DialLayout.FM_STEP * 1.5f
        RadioBand.AM -> DialLayout.AM_STEP * 1.5f
        RadioBand.WEB -> if (layout.stops.size > 1) 0.5f / (layout.stops.size - 1) else 1f
    }

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val needle = remember(layout) {
        val start = layout.stops.firstOrNull { it.station.uuid == playingUuid }?.position
            ?: layout.stops.firstOrNull()?.position
            ?: 0.5f
        Animatable(start)
    }
    var dragging by remember { mutableStateOf(false) }
    var scanning by remember(layout) { mutableStateOf(false) }
    var lastTickUuid by remember { mutableStateOf<String?>(null) }
    // Opening the dial shouldn't start a stream on its own; only a turn, seek or scan does.
    var touched by remember(layout) { mutableStateOf(false) }

    val tuned: DialStop? = layout.nearest(needle.value)?.takeIf { abs(it.position - needle.value) <= tolerance }
    val currentOnTune by rememberUpdatedState(onTune)
    val currentPlaying by rememberUpdatedState(playingUuid)

    // Settled on a station → play it (unless it's already on).
    LaunchedEffect(tuned?.station?.uuid, dragging) {
        val stop = tuned ?: return@LaunchedEffect
        if (!touched || dragging || stop.station.uuid == currentPlaying) return@LaunchedEffect
        delay(TUNE_SETTLE_MS)
        currentOnTune(stop.station)
    }

    // Tick as the needle passes each station, like a detent.
    LaunchedEffect(tuned?.station?.uuid) {
        val uuid = tuned?.station?.uuid
        if (touched && uuid != null && uuid != lastTickUuid) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        lastTickUuid = uuid
    }

    LaunchedEffect(scanning, layout) {
        while (scanning) {
            val next = layout.next(needle.value) ?: break
            needle.animateTo(next.position, spring(stiffness = Spring.StiffnessLow))
            delay(SCAN_DWELL_MS)
        }
    }

    fun moveTo(position: Float) {
        touched = true
        scanning = false
        scope.launch { needle.animateTo(position.coerceIn(0f, 1f), spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
    }

    fun nudge(delta: Float) {
        touched = true
        scanning = false
        scope.launch { needle.snapTo((needle.value + delta).coerceIn(0f, 1f)) }
    }

    fun snapToNearest() {
        layout.nearest(needle.value)?.let { moveTo(it.position) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Band selector
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioBand.entries.forEach { b ->
                val count = bandCounts[b] ?: 0
                FilterChip(
                    selected = band == b,
                    enabled = count > 0,
                    onClick = { band = b },
                    label = { Text("${b.label} · $count") }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        DisplayWindow(
            layout = layout,
            position = needle.value,
            tuned = tuned,
            isLive = tuned != null && tuned.station.uuid == playingUuid && isPlaying,
            scanning = scanning,
        )
        Spacer(Modifier.height(16.dp))

        if (layout.stops.isEmpty()) {
            Text(
                if (state.loading) "Warming up the tubes…" else "No stations on ${band.label} here. Try another band or scope.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }

        DialScale(
            layout = layout,
            position = needle.value,
            playingUuid = playingUuid,
            onDragStart = { dragging = true; scanning = false },
            onDrag = { delta -> nudge(delta) },
            onDragEnd = { dragging = false; snapToNearest() },
            onTap = { pos -> moveTo(layout.nearest(pos)?.position ?: pos) },
        )
        Spacer(Modifier.height(16.dp))

        // SEEK ◀◀  -  ▶/❚❚  +  ▶▶ SEEK
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalIconButton(onClick = { layout.previous(needle.value)?.let { moveTo(it.position) } }) {
                Icon(Icons.Rounded.FastRewind, contentDescription = "Seek back")
            }
            FilledTonalIconButton(onClick = { nudge(-fineStep(band, layout)); scope.launch { delay(600); if (!dragging) snapToNearestIfClose(layout, needle.value, tolerance) { moveTo(it) } } }) {
                Icon(Icons.Rounded.Remove, contentDescription = "Tune down")
            }
            FilledIconButton(
                onClick = {
                    val t = tuned
                    if (t != null && t.station.uuid != playingUuid) onTune(t.station) else onPlayPause()
                },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    if (isPlaying && tuned?.station?.uuid == playingUuid) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
            FilledTonalIconButton(onClick = { nudge(fineStep(band, layout)); scope.launch { delay(600); if (!dragging) snapToNearestIfClose(layout, needle.value, tolerance) { moveTo(it) } } }) {
                Icon(Icons.Rounded.Add, contentDescription = "Tune up")
            }
            FilledTonalIconButton(onClick = { layout.next(needle.value)?.let { moveTo(it.position) } }) {
                Icon(Icons.Rounded.FastForward, contentDescription = "Seek forward")
            }
        }
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TuningKnob(
                modifier = Modifier.size(150.dp),
                onRotate = { turns ->
                    dragging = true
                    touched = true
                    scanning = false
                    scope.launch { needle.snapTo((needle.value + turns * KNOB_TURN_FRACTION).coerceIn(0f, 1f)) }
                },
                onRelease = { dragging = false; snapToNearest() },
            )
            Spacer(Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilterChip(
                    selected = scanning,
                    enabled = layout.stops.size > 1,
                    onClick = { touched = true; scanning = !scanning },
                    label = { Text(if (scanning) "SCANNING" else "SCAN") }
                )
                Text(
                    "Plays each station\nfor a few seconds",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionHeader("Presets", hint = "hold to save the tuned station")
        PresetRow(
            presets = presets,
            playingUuid = playingUuid,
            onPlay = { s ->
                // Jump the needle to the preset if it's on this band; either way, play it.
                layout.stops.firstOrNull { it.station.uuid == s.uuid }?.let { moveTo(it.position) }
                onTune(s)
            },
            onLongPress = { slot -> tuned?.station?.let { onSavePreset(slot, it) } },
        )
    }
}

private fun fineStep(band: RadioBand, layout: DialLayout): Float = when (band) {
    RadioBand.FM -> DialLayout.FM_STEP
    RadioBand.AM -> DialLayout.AM_STEP
    RadioBand.WEB -> if (layout.stops.size > 1) 1f / (layout.stops.size - 1) else 0f
}

private inline fun snapToNearestIfClose(layout: DialLayout, position: Float, tolerance: Float, move: (Float) -> Unit) {
    layout.nearest(position)?.takeIf { abs(it.position - position) <= tolerance }?.let { move(it.position) }
}

/** The glowing window: band, frequency/channel, station name, signal and LIVE. */
@Composable
private fun DisplayWindow(
    layout: DialLayout,
    position: Float,
    tuned: DialStop?,
    isLive: Boolean,
    scanning: Boolean,
) {
    val glow = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.background(
                Brush.verticalGradient(listOf(glow.copy(alpha = 0.14f), Color.Transparent))
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        layout.band.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = glow,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tuned?.label?.let { if (layout.band == RadioBand.WEB) "CH $it" else it } ?: layout.readout(position),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 34.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (layout.band != RadioBand.WEB) {
                        Text(
                            if (layout.band == RadioBand.FM) " MHz" else " kHz",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        LiveBadge(active = isLive)
                        Spacer(Modifier.height(4.dp))
                        SignalBars(strength = if (tuned == null) 0 else signalFor(tuned.station))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        tuned != null -> tuned.station.name
                        scanning -> "· · · scanning · · ·"
                        else -> "· · · static · · ·"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                    color = if (tuned != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tuned?.station?.subtitle?.ifBlank { null } ?: " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Signal" is really stream quality: bitrate, nudged by popularity. 1..4 bars. */
private fun signalFor(s: RadioStation): Int = when {
    s.bitrate >= 192 -> 4
    s.bitrate >= 128 -> 3
    s.bitrate >= 64 -> 2
    s.bitrate > 0 -> 1
    else -> if (s.clickCount > 100) 3 else 2
}

@Composable
private fun SignalBars(strength: Int) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..4).forEach { i ->
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i <= strength) on else off)
            )
        }
    }
}

/** The printed scale: ticks, labels, a dot per station and the red needle. */
@Composable
private fun DialScale(
    layout: DialLayout,
    position: Float,
    playingUuid: String?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (Float) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val dot = MaterialTheme.colorScheme.tertiary
    val playing = MaterialTheme.colorScheme.primary
    val needleColor = MaterialTheme.colorScheme.error
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = tick)
    val currentLayout by rememberUpdatedState(layout)
    // pointerInput(Unit) outlives recompositions; read the latest callbacks through state.
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val tap by rememberUpdatedState(onTap)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragStart() },
                        onDragEnd = { dragEnd() },
                        onDragCancel = { dragEnd() },
                    ) { change, dragAmount ->
                        change.consume()
                        drag(dragAmount / size.width)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> tap((offset.x / size.width).coerceIn(0f, 1f)) }
                }
        ) {
            val w = size.width
            val baseY = size.height * 0.55f

            // Minor/major ticks
            val ticks = 100
            for (i in 0..ticks) {
                val x = w * i / ticks
                val major = i % 10 == 0
                val mid = i % 5 == 0
                val len = when {
                    major -> 18f
                    mid -> 12f
                    else -> 7f
                }
                drawLine(tick.copy(alpha = if (major) 0.9f else 0.45f), Offset(x, baseY - len), Offset(x, baseY), strokeWidth = if (major) 2.5f else 1.5f)
            }
            drawLine(tick.copy(alpha = 0.6f), Offset(0f, baseY), Offset(w, baseY), strokeWidth = 2f)

            // Labels under the scale
            currentLayout.marks.forEach { (p, text) ->
                val layoutResult = measurer.measure(text, labelStyle, maxLines = 1)
                val x = (w * p - layoutResult.size.width / 2f).coerceIn(0f, w - layoutResult.size.width)
                drawText(layoutResult, topLeft = Offset(x, baseY + 8f))
            }

            // Station dots above the scale
            currentLayout.stops.forEach { stop ->
                val x = w * stop.position
                val isOn = stop.station.uuid == playingUuid
                drawCircle(if (isOn) playing else dot, radius = if (isOn) 7f else 4.5f, center = Offset(x, baseY - 30f))
            }

            // Needle
            val nx = w * position
            drawLine(needleColor, Offset(nx, 0f), Offset(nx, size.height), strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
}

/**
 * A rotary knob. Drag in a circle to tune; [onRotate] gets signed turns
 * (clockwise positive, 1f = a full revolution).
 */
@Composable
private fun TuningKnob(
    modifier: Modifier = Modifier,
    onRotate: (Float) -> Unit,
    onRelease: () -> Unit,
) {
    var angle by remember { mutableStateOf(0f) }
    val rotate by rememberUpdatedState(onRotate)
    val release by rememberUpdatedState(onRelease)
    val ring = MaterialTheme.colorScheme.surfaceContainerHighest
    val face = MaterialTheme.colorScheme.secondaryContainer
    val grip = MaterialTheme.colorScheme.onSecondaryContainer
    val marker = MaterialTheme.colorScheme.primary

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .weight(1f)
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    var last = 0f
                    detectDragGestures(
                        onDragStart = { start ->
                            last = atan2(start.y - size.height / 2f, start.x - size.width / 2f)
                        },
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    ) { change, _ ->
                        change.consume()
                        val p = change.position
                        val now = atan2(p.y - size.height / 2f, p.x - size.width / 2f)
                        var d = now - last
                        // Unwrap across ±π so crossing the left side doesn't jump a full turn.
                        if (d > PI) d -= (2 * PI).toFloat()
                        if (d < -PI) d += (2 * PI).toFloat()
                        last = now
                        angle += d
                        rotate(d / (2 * PI).toFloat())
                    }
                }
        ) {
            val r = min(size.width, size.height) / 2f
            val c = center
            drawCircle(ring, radius = r, center = c)
            drawCircle(face, radius = r * 0.82f, center = c)
            // Knurled grip
            val grips = 24
            for (i in 0 until grips) {
                val a = angle + i * (2 * PI / grips).toFloat()
                drawLine(
                    grip.copy(alpha = 0.25f),
                    Offset(c.x + cos(a) * r * 0.66f, c.y + sin(a) * r * 0.66f),
                    Offset(c.x + cos(a) * r * 0.80f, c.y + sin(a) * r * 0.80f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
            // Position marker
            val a = angle - (PI / 2).toFloat()
            drawLine(
                marker,
                Offset(c.x + cos(a) * r * 0.2f, c.y + sin(a) * r * 0.2f),
                Offset(c.x + cos(a) * r * 0.6f, c.y + sin(a) * r * 0.6f),
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
            drawCircle(grip.copy(alpha = 0.3f), radius = r * 0.82f, center = c, style = Stroke(width = 2f))
        }
        Text(
            "TUNE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
