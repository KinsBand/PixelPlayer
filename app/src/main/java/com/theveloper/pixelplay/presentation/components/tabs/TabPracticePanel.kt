package com.theveloper.pixelplay.presentation.components.tabs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.sin
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SheetState
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.style.TextOverflow
import com.theveloper.pixelplay.data.songsterr.SongsterrJson
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer3
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.songsterr.TabParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class PracticeSheet { SPEED, COUNT_IN, TONE, SHARE, HELP, DRUMS }

/**
 * Practice panel under the lyrics toolbar on the Instruments page. The toolbar's Instruments
 * button opens and closes it ([TabPracticeController.panelExpanded]); swiping the open panel
 * down closes it too.
 *
 * Row 1: speed · ORIG./SYNTH · loop. Row 2: count-in · metronome · pitch & transpose · drum kit
 * (drum parts, or whenever a kit is plugged in) · help · share. Row 3: the instruments list
 * (switch part, mute, hold to solo).
 * Play / pause is the sheet's normal play button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabPracticePanel(
    controller: TabPracticeController,
    onBackgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<PracticeSheet?>(null) }
    val expanded = controller.panelExpanded
    val dragThreshold = with(LocalDensity.current) { 24.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        // Opened and closed by the Instruments button in the toolbar (or swipe the open panel down).
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                fadeIn(tween(220, delayMillis = 60)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                fadeOut(tween(140)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (total > dragThreshold * 2) controller.panelExpanded = false
                                total = 0f
                            },
                            onVerticalDrag = { _, amount -> total += amount },
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Row 1: speed · ORIG./SYNTH · loop
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PanelTile(
                        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                        label = "${(controller.speed * 100).roundToInt()}%",
                        active = controller.speed != 1f,
                        onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                        onClick = { sheet = PracticeSheet.SPEED },
                    )
                    SoundToggle(controller, onBackgroundColor, accentColor, onAccentColor, Modifier.weight(2f)) {
                        controller.changeSound(it, scope)
                    }
                    PanelTile(
                        icon = Icons.Rounded.Repeat,
                        label = if (controller.loopSelecting) "Pick bars" else null,
                        active = controller.loopActive || controller.loopSelecting,
                        onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                        onClick = controller::toggleLoop,
                    )
                }
                // Row 2: count-in · metronome · pitch & transpose · help · share
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelTile(
                        icon = Icons.Rounded.Timer3,
                        label = if (controller.countIn) (controller.countInBeats?.let { "$it" } ?: "1 bar") else null,
                        active = controller.countIn,
                        onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                        onClick = { sheet = PracticeSheet.COUNT_IN },
                    )
                    PanelTile(
                        custom = { tint -> MetronomeIcon(tint) },
                        active = controller.metronome,
                        onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                        onClick = controller::toggleMetronome,
                    )
                    PanelTile(
                        icon = Icons.Rounded.GraphicEq,
                        label = when {
                            controller.pitch != 0 && controller.transpose != 0 -> "${signed(controller.pitch)}/${signed(controller.transpose)}"
                            controller.pitch != 0 -> signed(controller.pitch)
                            controller.transpose != 0 -> "T${signed(controller.transpose)}"
                            else -> null
                        },
                        active = controller.pitch != 0 || controller.transpose != 0,
                        onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                        onClick = { sheet = PracticeSheet.TONE },
                    )
                    if (controller.isDrumsView || controller.drums.connected) {
                        val drums = controller.drums
                        PanelTile(
                            custom = { tint -> DrumIcon(tint) },
                            label = drumTileLabel(drums),
                            active = drums.connected && drums.scoringEnabled,
                            onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                            onClick = { sheet = PracticeSheet.DRUMS },
                        )
                    }
                    PanelTile(
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        active = false,
                        onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                        onClick = { sheet = PracticeSheet.HELP },
                    )
                    PanelTile(
                        icon = Icons.Rounded.Share,
                        active = controller.pdfUri != null,
                        onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                        onClick = { sheet = PracticeSheet.SHARE },
                    )
                }
                // Row 3: instruments (full width)
                val ready = controller.state as? TabUiState.Ready
                val current = ready?.tab?.metaTrack
                val mutedCount = controller.muted.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(onBackgroundColor.copy(alpha = 0.08f))
                        .clickable { controller.openPicker() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InstrumentIcon(current, onBackgroundColor)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            current?.let { partLabel(it).second } ?: "Instruments",
                            color = onBackgroundColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(current?.let { partLabel(it).first }.orEmpty().ifBlank { "Instruments" })
                                if (mutedCount > 0) append(" · $mutedCount muted")
                            },
                            color = onBackgroundColor.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("Instruments  ›", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Instruments sheet: opens by itself when there's no default instrument, or from the
    // Instruments button. Back arrow, swipe down or picking a part closes it.
    if (controller.picking) {
        InstrumentsSheet(controller, onBackgroundColor, accentColor, onAccentColor, containerColor)
    }

    val open = sheet ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { sheet = null },
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = onBackgroundColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (open) {
                PracticeSheet.SPEED -> SpeedSheet(controller, onBackgroundColor, accentColor, onAccentColor)
                PracticeSheet.COUNT_IN -> CountInSheet(controller, onBackgroundColor, accentColor, onAccentColor)
                PracticeSheet.TONE -> {
                    StepperSheet(
                        title = "Shift pitch",
                        unit = "semitones (audio)",
                        value = controller.pitch,
                        onChange = controller::changePitch,
                        resetLabel = "Shift to original pitch",
                        onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                    )
                    if (!controller.isDrumsView) {
                        Spacer(Modifier.height(4.dp))
                        StepperSheet(
                            title = "Transpose tab",
                            unit = "semitones (frets)",
                            value = controller.transpose,
                            onChange = { controller.changeTranspose(it, scope) },
                            resetLabel = "Original key",
                            onBg = onBackgroundColor, accent = accentColor, onAccent = onAccentColor,
                        )
                    }
                }
                PracticeSheet.SHARE -> ShareSheet(controller, onBackgroundColor) { sheet = null }
                PracticeSheet.HELP -> NotationHelp(controller, onBackgroundColor)
                PracticeSheet.DRUMS -> DrumKitSheet(controller, onBackgroundColor, accentColor, onAccentColor)
            }
        }
    }
}

private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

@Composable
private fun RowScope.PanelTile(
    icon: ImageVector? = null,
    custom: (@Composable (Color) -> Unit)? = null,
    label: String? = null,
    active: Boolean,
    enabled: Boolean = true,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
) {
    val bg = if (active) accent else onBg.copy(alpha = 0.08f)
    val tint = (if (active) onAccent else onBg).copy(alpha = if (enabled) 1f else 0.35f)
    Column(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        custom?.invoke(tint)
        if (label != null) Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MetronomeIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.36f, h * 0.12f)
            lineTo(w * 0.64f, h * 0.12f)
            lineTo(w * 0.82f, h * 0.9f)
            lineTo(w * 0.18f, h * 0.9f)
            close()
        }
        drawPath(body, tint, style = Stroke(width = w * 0.08f))
        drawLine(tint, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.76f, h * 0.25f), strokeWidth = w * 0.08f)
        drawLine(tint, Offset(w * 0.28f, h * 0.72f), Offset(w * 0.72f, h * 0.72f), strokeWidth = w * 0.06f)
    }
}

@Composable
private fun SoundToggle(
    controller: TabPracticeController,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    modifier: Modifier,
    onChange: (TabSound) -> Unit,
) {
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(onBg.copy(alpha = 0.08f)),
    ) {
        listOf(TabSound.ORIGINAL to "ORIG.", TabSound.SYNTH to "SYNTH").forEach { (mode, label) ->
            val on = controller.sound == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) accent else Color.Transparent)
                    .clickable { onChange(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) onAccent else onBg.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Sheets ───────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.SpeedSheet(controller: TabPracticeController, onBg: Color, accent: Color, onAccent: Color) {
    Text("Playback speed", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onBg)
    val pct = (controller.speed * 100).roundToInt()
    Text("$pct%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
    Slider(
        value = pct.toFloat(),
        onValueChange = { controller.changeSpeed(it.roundToInt() / 100f) },
        valueRange = 10f..200f,
        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
    )
    val presets = listOf(15, 30, 40, 50, 60, 70, 80, 90, 100)
    presets.chunked(5).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { p ->
                val on = p == pct
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) accent else onBg.copy(alpha = 0.08f))
                        .clickable { controller.changeSpeed(p / 100f) },
                    contentAlignment = Alignment.Center,
                ) { Text("$p%", color = if (on) onAccent else onBg, fontWeight = FontWeight.Bold) }
            }
            if (row.size < 5) {
                Row(Modifier.weight(1f).height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-5, 5).forEach { step ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(onBg.copy(alpha = 0.08f))
                                .clickable { controller.changeSpeed((pct + step) / 100f) },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (step > 0) "+" else "−", color = onBg, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.StepperSheet(
    title: String,
    unit: String,
    value: Int,
    onChange: (Int) -> Unit,
    resetLabel: String,
    onBg: Color,
    accent: Color,
    onAccent: Color,
) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onBg)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        StepButton("−", onBg) { onChange(value - 1) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
            Text(if (value > 0) "+$value" else "$value", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = onBg)
            Text(unit, fontSize = 12.sp, color = onBg.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        }
        StepButton("+", onBg) { onChange(value + 1) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(accent)
            .clickable { onChange(0) },
        contentAlignment = Alignment.Center,
    ) { Text(resetLabel, color = onAccent, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun StepButton(label: String, onBg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(onBg.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 26.sp, color = onBg, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ColumnScope.CountInSheet(controller: TabPracticeController, onBg: Color, accent: Color, onAccent: Color) {
    Text("Count-in", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onBg)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Click before playing", color = onBg, modifier = Modifier.weight(1f))
        Switch(
            checked = controller.countIn,
            onCheckedChange = { controller.countIn = it },
            colors = SwitchDefaults.colors(checkedThumbColor = onAccent, checkedTrackColor = accent),
        )
    }
    val beats = controller.countInBeats
    Text(
        if (beats == null) "1 bar" else "$beats ${if (beats == 1) "click" else "clicks"}",
        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (controller.countIn) accent else onBg.copy(alpha = 0.5f),
    )
    Slider(
        value = (beats ?: 4).toFloat(),
        onValueChange = {
            controller.countInBeats = it.roundToInt()
            controller.countIn = true
        },
        valueRange = 1f..16f,
        steps = 14,
        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(onBg.copy(alpha = 0.08f))
            .clickable {
                controller.countInBeats = null
                controller.countIn = true
            },
        contentAlignment = Alignment.Center,
    ) { Text("One bar (follows the time signature)", color = onBg, fontWeight = FontWeight.SemiBold) }
}

/** "Person | gear | Rhythm Guitar" → ("Person", "Rhythm Guitar"). */
internal fun partLabel(t: SongsterrJson.MetaTrack): Pair<String, String> {
    val pieces = t.name.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    val person = if (pieces.size >= 2) pieces.first() else ""
    val part = pieces.lastOrNull()?.takeIf { pieces.size >= 2 } ?: t.name.ifBlank { t.instrument }
    return person to part.ifBlank { t.instrument.ifBlank { "Track ${t.index + 1}" } }
}

@Composable
internal fun InstrumentIcon(t: SongsterrJson.MetaTrack?, tint: Color) {
    when {
        t == null -> Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        t.isVocal -> Icon(Icons.Rounded.Mic, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        t.isDrums -> Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val st = Stroke(width = w * 0.08f)
            drawOval(tint, topLeft = Offset(w * 0.12f, h * 0.3f), size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.24f), style = st)
            drawLine(tint, Offset(w * 0.12f, h * 0.42f), Offset(w * 0.12f, h * 0.78f), w * 0.08f)
            drawLine(tint, Offset(w * 0.88f, h * 0.42f), Offset(w * 0.88f, h * 0.78f), w * 0.08f)
            drawArc(tint, 0f, 180f, false, topLeft = Offset(w * 0.12f, h * 0.62f), size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.3f), style = st)
            drawLine(tint, Offset(w * 0.3f, h * 0.05f), Offset(w * 0.5f, h * 0.36f), w * 0.07f)
            drawLine(tint, Offset(w * 0.72f, h * 0.05f), Offset(w * 0.55f, h * 0.36f), w * 0.07f)
        }
        else -> Canvas(Modifier.size(24.dp)) {
            // Guitar / bass: body, sound hole, neck.
            val w = size.width
            val h = size.height
            val st = Stroke(width = w * 0.08f)
            drawCircle(tint, radius = w * 0.2f, center = Offset(w * 0.34f, h * 0.7f), style = st)
            drawCircle(tint, radius = w * 0.15f, center = Offset(w * 0.47f, h * 0.5f), style = st)
            drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.38f, h * 0.64f))
            drawLine(tint, Offset(w * 0.52f, h * 0.46f), Offset(w * 0.88f, h * 0.1f), w * (if (t.isBass) 0.11f else 0.08f))
        }
    }
}

private enum class InstrumentsPage { PARTS, LISTINGS, HISTORY }

/**
 * The instruments sheet. Top row: back arrow · full-width button naming who made this tab
 * (tap for the song's other Songsterr tabs) · history (every version of this tab).
 * Below: the song's parts, or the other tabs, or the versions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstrumentsSheet(
    controller: TabPracticeController,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    containerColor: Color,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by remember { mutableStateOf(InstrumentsPage.PARTS) }
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { controller.closePicker() }
    }
    ModalBottomSheet(
        onDismissRequest = { controller.closePicker() },
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = onBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    description = if (page == InstrumentsPage.PARTS) "Close" else "Back to parts",
                    onBg = onBg, accent = accent, onAccent = onAccent, active = false,
                ) { if (page == InstrumentsPage.PARTS) close() else page = InstrumentsPage.PARTS }

                val listingsOpen = page == InstrumentsPage.LISTINGS
                val fg = if (listingsOpen) onAccent else onBg
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (listingsOpen) accent else onBg.copy(alpha = 0.08f))
                        .clickable {
                            page = if (listingsOpen) InstrumentsPage.PARTS else InstrumentsPage.LISTINGS
                            if (!listingsOpen) controller.loadAlternatives(scope)
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            controller.tabAuthor?.let { "Tab by $it" } ?: "Songsterr tab",
                            color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (controller.source?.revisionId != null) "Older version · tap for other tabs" else "Tap for other tabs of this song",
                            color = fg.copy(alpha = 0.65f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = fg.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                }

                SheetIconButton(
                    icon = Icons.Rounded.History,
                    description = "Versions of this tab",
                    onBg = onBg, accent = accent, onAccent = onAccent,
                    active = page == InstrumentsPage.HISTORY,
                    enabled = controller.currentSongId != null,
                ) {
                    page = if (page == InstrumentsPage.HISTORY) InstrumentsPage.PARTS else InstrumentsPage.HISTORY
                    if (page == InstrumentsPage.HISTORY) controller.loadRevisions(scope)
                }
            }

            when (page) {
                InstrumentsPage.PARTS -> InstrumentPicker(controller, onBg, accent, Modifier.fillMaxWidth())
                InstrumentsPage.LISTINGS -> ListingsPage(controller, onBg, accent) { page = InstrumentsPage.PARTS }
                InstrumentsPage.HISTORY -> HistoryPage(controller, onBg, accent) { page = InstrumentsPage.PARTS }
            }
        }
    }
}

@Composable
private fun SheetIconButton(
    icon: ImageVector,
    description: String,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (active) accent else onBg.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = description,
            tint = (if (active) onAccent else onBg).copy(alpha = if (enabled) 1f else 0.35f),
        )
    }
}

@Composable
private fun SheetMessage(text: String, onBg: Color, loading: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = onBg.copy(alpha = 0.7f))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, color = onBg.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** The song's other Songsterr listings; the one on screen is ticked. */
@Composable
private fun ListingsPage(controller: TabPracticeController, onBg: Color, accent: Color, onPicked: () -> Unit) {
    val list = controller.alternatives
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Other tabs of this song", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onBg)
        Text(
            "If this tab looks wrong, another upload of the song may fit better.",
            color = onBg.copy(alpha = 0.6f), fontSize = 12.sp,
        )
        when {
            list == null || controller.alternativesLoading -> SheetMessage("Searching Songsterr…", onBg, loading = true)
            list.isEmpty() -> SheetMessage("No other tabs found for this song.", onBg)
            else -> list.forEach { alt ->
                val current = alt.songId == controller.currentSongId
                ChoiceRow(
                    title = alt.title,
                    subtitle = buildList {
                        add(alt.artist)
                        if (alt.parts > 0) add("${alt.parts} ${if (alt.parts == 1) "part" else "parts"}")
                        if (alt.hasDrums) add("drums")
                    }.joinToString(" · "),
                    selected = current,
                    onBg = onBg, accent = accent,
                ) {
                    controller.chooseListing(alt.songId)
                    onPicked()
                }
            }
        }
    }
}

/** Every saved version of this tab, newest first; the one on screen is ticked. */
@Composable
private fun HistoryPage(controller: TabPracticeController, onBg: Color, accent: Color, onPicked: () -> Unit) {
    val list = controller.revisions
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Versions of this tab", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onBg)
        Text(
            "Every edit saved on Songsterr, newest first. Older ones can have different parts.",
            color = onBg.copy(alpha = 0.6f), fontSize = 12.sp,
        )
        when {
            list == null || controller.revisionsLoading -> SheetMessage("Loading versions…", onBg, loading = true)
            list.isEmpty() -> SheetMessage("Couldn't load the versions of this tab.", onBg)
            else -> list.forEachIndexed { i, rev ->
                val number = list.size - i
                ChoiceRow(
                    title = buildString {
                        append("Version $number")
                        if (i == 0) append(" · latest")
                    },
                    subtitle = listOfNotNull(
                        rev.author,
                        rev.createdAt?.take(10),
                        rev.description,
                    ).joinToString(" · ").ifBlank { "Revision ${rev.revisionId}" },
                    selected = rev.revisionId == controller.currentRevisionId,
                    onBg = onBg, accent = accent,
                ) {
                    controller.chooseRevision(rev)
                    onPicked()
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onBg: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else onBg.copy(alpha = 0.06f))
            .clickable(enabled = !selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = onBg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = onBg.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Showing now", tint = accent)
    }
}

/**
 * Every part of the song, row by row. Tap a row to show its tab (and close the sheet); the star
 * makes that instrument open straight away for every song. Once a part is loaded, the speaker
 * mutes a part and holding it plays only that part (with the original audio this uses
 * Songsterr's synced backing/solo videos, so only parts that have one can be muted).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InstrumentPicker(
    controller: TabPracticeController,
    onBg: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val meta = controller.meta
    if (meta == null) {
        SheetMessage("Loading parts…", onBg, loading = true)
        return
    }
    val ready = controller.state as? TabUiState.Ready
    val original = controller.sound == TabSound.ORIGINAL
    val canMuteAny = ready != null
    Column(
        modifier = modifier
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Choose an instrument", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onBg)
        meta.tracks.filter { !it.isEmpty }.forEach { t ->
            val showing = ready != null && t.index == ready.tab.trackIndex
            val isMuted = t.index in controller.muted
            val (person, part) = partLabel(t)
            val family = controller.familyFor(t)
            val isDefault = !t.isVocal && controller.defaultFamily == family
            val canMute = canMuteAny && (!original || controller.canMuteInOriginal(t.index) || isMuted)
            val canSolo = canMuteAny && (!original || controller.canSoloInOriginal(t.index))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (showing) accent.copy(alpha = 0.18f) else onBg.copy(alpha = 0.06f))
                    .clickable(enabled = !t.isVocal) { controller.selectTrack(t.index) }
                    .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentIcon(t, if (isMuted || t.isVocal) onBg.copy(alpha = 0.45f) else onBg)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        person.ifBlank { part },
                        color = onBg.copy(alpha = if (isMuted) 0.5f else 1f),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val sub = buildList {
                        if (person.isNotBlank()) add(part)
                        if (t.instrument.isNotBlank() && t.instrument != part) add(t.instrument)
                        if (!t.isDrums && !t.isVocal) com.theveloper.pixelplay.presentation.components.tuningName(t.tuning)?.let { add(it) }
                        if (t.isVocal) add("no tab")
                    }.joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = onBg.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!t.isVocal) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { controller.toggleDefault(family) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isDefault) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = if (isDefault) "Default instrument" else "Make default",
                            tint = if (isDefault) accent else onBg.copy(alpha = 0.55f),
                        )
                    }
                }
                if (canMuteAny) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) accent else Color.Transparent)
                            .combinedClickable(
                                enabled = canMute || canSolo,
                                onClick = { if (canMute) controller.toggleMute(t.index, scope) },
                                onLongClick = { if (canSolo) controller.solo(t.index, scope) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isMuted) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = "Unmute",
                                tint = Color.White,
                            )
                        } else {
                            LiveSpeakerIcon(
                                sounding = t.index in controller.soundingParts,
                                tint = if (canMute || canSolo) onBg else onBg.copy(alpha = 0.3f),
                                waveTint = accent,
                                contentDescription = "Mute",
                            )
                        }
                    }
                }
            }
        }
        if (controller.muted.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(onBg.copy(alpha = 0.08f))
                    .clickable { controller.unmuteAll(scope) },
                contentAlignment = Alignment.Center,
            ) { Text("Unmute all", color = onBg, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ColumnScope.ShareSheet(controller: TabPracticeController, onBg: Color, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val ready = controller.state as? TabUiState.Ready
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            controller.pdfUri = uri
            onDone()
        }
    }
    Text("Share & PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onBg)
    fun makePdf(then: (Uri?) -> Unit) {
        val r = ready ?: return
        busy = true
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                TabPdfExporter.export(
                    context, r.track, r.sections,
                    r.tab.meta.title.ifBlank { controller.title },
                    r.tab.meta.artist.ifBlank { controller.artist },
                    r.tab.metaTrack?.displayName ?: r.track.name,
                )
            }
            busy = false
            then(uri)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        ShareButton("Share link", Icons.Rounded.Share, onBg, enabled = controller.songUrl != null) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, controller.songUrl)
            }
            runCatching { context.startActivity(Intent.createChooser(send, "Share tab").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            onDone()
        }
        ShareButton(if (busy) "Saving…" else "Save PDF", Icons.Rounded.PictureAsPdf, onBg, enabled = ready != null && !busy) {
            makePdf { uri ->
                Toast.makeText(context, if (uri != null) "PDF saved to Downloads" else "Couldn't save the PDF", Toast.LENGTH_SHORT).show()
            }
        }
        ShareButton("Share PDF", Icons.Rounded.Share, onBg, enabled = ready != null && !busy) {
            makePdf { uri ->
                if (uri == null) {
                    Toast.makeText(context, "Couldn't make the PDF", Toast.LENGTH_SHORT).show()
                    return@makePdf
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(Intent.createChooser(send, "Share PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                onDone()
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        ShareButton(
            if (controller.pdfUri != null) "Back to tab" else "Upload PDF",
            Icons.Rounded.PictureAsPdf, onBg, enabled = true,
        ) {
            if (controller.pdfUri != null) {
                controller.pdfUri = null
                onDone()
            } else {
                pickPdf.launch("application/pdf")
            }
        }
    }
}
@Composable
private fun RowScope.ShareButton(label: String, icon: ImageVector, onBg: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(onBg.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = onBg.copy(alpha = if (enabled) 1f else 0.4f))
        Text(label, color = onBg.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 13.sp)
    }
}

/** Legend of what each symbol means, for the loaded part. */
@Composable
private fun ColumnScope.NotationHelp(controller: TabPracticeController, onBg: Color) {
    Text("Notation Help", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onBg)
    val ready = controller.state as? TabUiState.Ready ?: return
    if (ready.track.isDrums) {
        // Every kit piece this part uses, at its staff position.
        val used = ready.track.measures.asSequence()
            .flatMap { it.beats.asSequence() }
            .flatMap { it.notes.asSequence() }
            .groupBy { it.fret }
            .map { (id, notes) -> Triple(id, notes.groupingBy { it.staffPos }.eachCount().maxByOrNull { it.value }?.key ?: 0f, notes.first().drumGlyph) }
            .sortedBy { it.second }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            used.forEach { (id, pos, glyph) -> DrumLegendItem(TabParser.drumArticulation(id).name, pos, glyph, onBg) }
        }
    } else {
        val rows = listOf(
            "5" to "Fret on that string", "(5)" to "Tied or ghost note", "x" to "Dead (muted) note",
            "H / P" to "Hammer-on / pull-off", "/  \\" to "Slide up / down", "<12>" to "Natural harmonic",
            "P.H." to "Pinch harmonic", "↑ full" to "Bend (¼, ½, full…)", "~~" to "Vibrato",
            "P.M. - - -" to "Palm mute", "let ring" to "Let the notes ring", ">" to "Accent",
            "⊓ / V" to "Down / up pick", "T" to "Tapping", "×2" to "Play the bars between repeat signs twice",
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (sym, meaning) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sym, modifier = Modifier.width(96.dp), color = onBg, fontWeight = FontWeight.Bold)
                    Text(meaning, color = onBg.copy(alpha = 0.75f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DrumLegendItem(name: String, pos: Float, glyph: TabParser.DrumGlyph, onBg: Color) {
    val density = LocalDensity.current
    val painter = remember { AndroidScorePainter() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
        Text(name, color = onBg, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.height(48.dp))
        Canvas(Modifier.width(78.dp).height(96.dp)) {
            val gap = 9.dp.toPx()
            val top = 30.dp.toPx()
            val ink = onBg.toArgb()
            for (i in 0 until 5) drawLine(onBg.copy(alpha = 0.5f), Offset(0f, top + i * gap), Offset(size.width, top + i * gap), 1.dp.toPx())
            drawIntoCanvas { c ->
                painter.canvas = c.nativeCanvas
                val y = top + pos * gap
                if (pos <= -1f) {
                    var l = -1
                    while (l >= pos) { painter.line(size.width / 2 - gap, top + l * gap, size.width / 2 + gap, top + l * gap, 1.dp.toPx(), ink); l-- }
                }
                if (pos >= 5f) {
                    var l = 5
                    while (l <= pos) { painter.line(size.width / 2 - gap, top + l * gap, size.width / 2 + gap, top + l * gap, 1.dp.toPx(), ink); l++ }
                }
                legendHead(painter, glyph, size.width / 2, y, gap, ink)
                painter.canvas = null
            }
        }
    }
}

/** Same noteheads as the score. */
private fun legendHead(p: ScorePainter, glyph: TabParser.DrumGlyph, x: Float, y: Float, u: Float, color: Int) {
    val st = 0.13f * u
    val xh = u * 0.46f
    fun cross() {
        p.line(x - xh, y - xh, x + xh, y + xh, st, color)
        p.line(x + xh, y - xh, x - xh, y + xh, st, color)
    }
    when (glyph) {
        TabParser.DrumGlyph.HEAD -> p.ellipse(x, y, u * 0.62f, u * 0.44f, -20f, color, true)
        TabParser.DrumGlyph.X -> cross()
        TabParser.DrumGlyph.X_CIRCLE -> { cross(); p.circle(x, y, u * 0.62f, color, false, st) }
        TabParser.DrumGlyph.SLASH_CIRCLE -> {
            p.circle(x, y, u * 0.55f, color, false, st)
            p.line(x - u * 0.42f, y + u * 0.42f, x + u * 0.42f, y - u * 0.42f, st, color)
        }
        TabParser.DrumGlyph.DIAMOND -> p.path(floatArrayOf(x, y - u * 0.55f, x + u * 0.55f, y, x, y + u * 0.55f, x - u * 0.55f, y), true, color, true)
        TabParser.DrumGlyph.DIAMOND_OPEN -> p.path(floatArrayOf(x, y - u * 0.55f, x + u * 0.55f, y, x, y + u * 0.55f, x - u * 0.55f, y), true, color, false, st)
        TabParser.DrumGlyph.X_HAT -> { cross(); p.path(floatArrayOf(x - u * 0.45f, y - u * 0.7f, x, y - u * 1.05f, x + u * 0.45f, y - u * 0.7f), false, color, false, st) }
        TabParser.DrumGlyph.X_CHOKE -> { cross(); p.circle(x + u * 0.8f, y + u * 0.55f, u * 0.13f, color, true) }
        TabParser.DrumGlyph.TRIANGLE -> p.path(floatArrayOf(x, y - u * 0.55f, x + u * 0.55f, y + u * 0.45f, x - u * 0.55f, y + u * 0.45f), true, color, true)
    }
}

// ── Options-menu section (first group on the Instruments page) ───────────────

/**
 * Instruments options shown first in the lyrics options menu while the Instruments page is
 * open: practice tools, back to the tab from a PDF, and nudging the tab timing.
 */
@Composable
fun TabOptionsSection(
    controller: TabPracticeController,
    contentColor: Color,
    accentColor: Color,
    itemBackgroundColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "Instruments",
            color = accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OptionPill(
                if (controller.panelExpanded) "Hide practice tools" else "Practice tools",
                contentColor, itemBackgroundColor, Modifier.weight(1f),
            ) { controller.panelExpanded = !controller.panelExpanded }
            if (controller.pdfUri != null) {
                OptionPill("Back to tab", contentColor, itemBackgroundColor, Modifier.weight(1f)) { controller.pdfUri = null }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(itemBackgroundColor)
                .clickable { controller.toggleRealStrings() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Real guitar & bass strings", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "SYNTH plays guitars and basses on modelled strings: bends, vibrato, slides, hammer-ons, harmonics, palm mutes",
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = controller.realStrings,
                onCheckedChange = { controller.toggleRealStrings() },
                colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
            )
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(itemBackgroundColor).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Tab timing", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Moves the cursor against the song: ${"%+.2f".format(controller.syncOffsetMs / 1000f)} s",
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
            TimingButton("−", contentColor) { controller.syncOffsetMs -= 100 }
            Spacer(Modifier.width(8.dp))
            TimingButton("+", contentColor) { controller.syncOffsetMs += 100 }
        }
    }
}

/**
 * Speaker for a part in the instruments list. While that part has a note sounding (live, as the
 * song plays), sound waves pulse out of it; they fade away when the part rests.
 */
@Composable
internal fun LiveSpeakerIcon(
    sounding: Boolean,
    tint: Color,
    waveTint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val level by animateFloatAsState(
        targetValue = if (sounding) 1f else 0f,
        animationSpec = if (sounding) tween(120) else tween(450),
        label = "speakerLevel",
    )
    val transition = rememberInfiniteTransition(label = "speakerWaves")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "speakerPhase",
    )
    Canvas(
        modifier
            .size(24.dp)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        val w = size.width
        val h = size.height
        // Body + cone, gently "pushed" in time with the waves.
        val push = level * 0.04f * sin(phase * 2f * PI.toFloat() * 2f)
        val body = Path().apply {
            moveTo(w * (0.12f + push), h * 0.38f)
            lineTo(w * (0.3f + push), h * 0.38f)
            lineTo(w * (0.52f + push), h * 0.18f)
            lineTo(w * (0.52f + push), h * 0.82f)
            lineTo(w * (0.3f + push), h * 0.62f)
            lineTo(w * (0.12f + push), h * 0.62f)
            close()
        }
        drawPath(body, tint)
        val cy = h * 0.5f
        val cx = w * 0.5f
        if (level < 0.01f) {
            // At rest: one faint static wave, like the usual speaker icon.
            drawArc(
                tint.copy(alpha = 0.55f), -45f, 90f, false,
                topLeft = Offset(cx - w * 0.2f, cy - h * 0.2f),
                size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.4f),
                style = Stroke(width = w * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            return@Canvas
        }
        // Three waves travelling outwards, each fading as it grows.
        for (k in 0 until 3) {
            val p = (phase + k / 3f) % 1f
            val r = w * (0.14f + 0.36f * p)
            val a = level * (1f - p) * (if (p < 0.12f) p / 0.12f else 1f)
            drawArc(
                (if (k == 0) waveTint else tint).copy(alpha = a.coerceIn(0f, 1f)),
                -50f, 100f, false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = w * 0.085f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun OptionPill(label: String, contentColor: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun TimingButton(label: String, contentColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(contentColor.copy(alpha = 0.1f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = contentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}
