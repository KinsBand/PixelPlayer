package com.theveloper.pixelplay.presentation.components.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.drumkit.AudioRoute
import com.theveloper.pixelplay.data.drumkit.DrumKitConnection
import com.theveloper.pixelplay.data.drumkit.DrumMap
import com.theveloper.pixelplay.data.drumkit.DrumPiece
import com.theveloper.pixelplay.data.drumkit.KitLayoutRules
import com.theveloper.pixelplay.data.drumkit.PadEntry
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.delay

/** A small drum for the practice panel's kit tile. */
@Composable
internal fun DrumIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.08f)
        drawOval(tint, Offset(w * 0.14f, h * 0.36f), Size(w * 0.72f, h * 0.22f), style = stroke)
        drawLine(tint, Offset(w * 0.14f, h * 0.47f), Offset(w * 0.14f, h * 0.8f), strokeWidth = w * 0.08f)
        drawLine(tint, Offset(w * 0.86f, h * 0.47f), Offset(w * 0.86f, h * 0.8f), strokeWidth = w * 0.08f)
        drawArc(tint, 0f, 180f, false, Offset(w * 0.14f, h * 0.68f), Size(w * 0.72f, h * 0.22f), style = stroke)
        drawLine(tint, Offset(w * 0.3f, h * 0.1f), Offset(w * 0.5f, h * 0.42f), strokeWidth = w * 0.07f)
        drawLine(tint, Offset(w * 0.72f, h * 0.1f), Offset(w * 0.55f, h * 0.42f), strokeWidth = w * 0.07f)
    }
}

/** Label for the practice panel's kit tile. */
internal fun drumTileLabel(d: DrumSession): String? = when {
    d.calibrating != null -> "Tap test"
    d.connected && d.liveAccuracy != null && d.isScoring -> "${(d.liveAccuracy!! * 100).toInt()}%"
    d.connected -> "Kit"
    else -> null
}

/**
 * The drum kit sheet: connection, scoring on/off, the delay allowed for (with the tap test),
 * an interactive drawing of the kit (live hits, tap a part then hit a pad to set it), and a
 * Pads button that opens the full pad list, grouped by kit part.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.DrumKitSheet(
    controller: TabPracticeController,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val d = controller.drums
    LaunchedEffect(Unit) { d.refreshRoute() }
    var showPads by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Drum kit",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = onBg,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        val status = d.status
        val (statusText, statusSub) = when (status) {
            is DrumKitConnection.Status.Connected -> status.name to (if (status.usb) "USB" else "Connected") + " · " + d.map.profile.name
            is DrumKitConnection.Status.Connecting -> status.name to "Connecting…"
            is DrumKitConnection.Status.Failed -> status.name to status.message
            DrumKitConnection.Status.NoKit -> "No kit connected" to null
            DrumKitConnection.Status.Unsupported -> "MIDI isn't available" to null
        }
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(onBg.copy(alpha = 0.06f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    when (status) {
                        is DrumKitConnection.Status.Connected -> accent
                        is DrumKitConnection.Status.Connecting -> accent.copy(alpha = 0.5f)
                        else -> onBg.copy(alpha = 0.3f)
                    },
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(statusText, style = MaterialTheme.typography.titleSmall, color = onBg)
                statusSub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = 0.7f)) }
            }
        }

        // Scoring on/off: the whole row toggles.
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(onBg.copy(alpha = 0.06f))
                .toggleable(value = d.scoringEnabled, role = Role.Switch, onValueChange = { d.toggleScoring() })
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Score my playing", style = MaterialTheme.typography.titleSmall, color = onBg, modifier = Modifier.weight(1f))
            Switch(
                checked = d.scoringEnabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = onAccent),
            )
        }

        CalibrationSection(controller, d, onBg, accent, onAccent)

        DrumKitDiagram(d, onBg, accent)

        val padCount = remember(d.map, d.lastNoteSerial) { (d.judge.seenNotes.keys + d.map.knownNotes()).distinct().size }
        FilledTonalButton(
            onClick = { showPads = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = onBg.copy(alpha = 0.1f), contentColor = onBg),
        ) {
            Text(stringResource(R.string.drum_kit_pads_count, padCount), style = MaterialTheme.typography.labelLarge)
        }
    }

    if (showPads) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val dismiss = com.theveloper.pixelplay.presentation.components.rememberAnimatedSheetDismiss(sheetState) { showPads = false }
        ModalBottomSheet(
            onDismissRequest = { showPads = false },
            sheetState = sheetState,
            containerColor = containerColor,
            contentColor = onBg,
        ) {
            com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
            PadMapSection(d, onBg, accent, onClose = dismiss)
        }
    }
}

@Composable
private fun CalibrationSection(controller: TabPracticeController, d: DrumSession, onBg: Color, accent: Color, onAccent: Color) {
    val synth = controller.sound == TabSound.SYNTH
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(onBg.copy(alpha = 0.06f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Delay (${d.route.label})", style = MaterialTheme.typography.titleSmall, color = onBg)
        if (d.route == AudioRoute.BLUETOOTH) {
            Text(stringResource(R.string.drum_kit_bt_short), style = MaterialTheme.typography.labelLarge, color = accent)
        }
        LatencyRow("ORIG.", d.latencyOrig, onBg, highlighted = !synth) { d.setLatency(false, it) }
        LatencyRow("SYNTH", d.latencySynth, onBg, highlighted = synth) { d.setLatency(true, it) }

        val mode = d.calibrating
        if (mode != null) {
            val done = d.calibrationProgress
            val countIn = DrumSession.CALIBRATION_COUNT_IN
            Text(
                if (done < countIn) "Listen… ${countIn - done}" else "Hit a pad on every click",
                style = MaterialTheme.typography.titleSmall, color = accent,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in countIn until DrumSession.CALIBRATION_TOTAL) {
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (k < done) accent else onBg.copy(alpha = 0.15f)))
                }
            }
            SheetButton("Cancel", onBg.copy(alpha = 0.1f), onBg) { d.cancelCalibration() }
        } else {
            d.calibrationResult?.let { r ->
                Text(
                    if (r.latencyMs < -100) "Not enough hits (${r.pairs})"
                    else "Delay set to ${r.latencyMs} ms (${r.pairs} hits, ±${r.spreadMs.toInt()} ms)",
                    style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = 0.8f),
                )
            }
            SheetButton(
                if (d.connected) "Tap test (${if (synth) "SYNTH" else "ORIG."})" else "Tap test",
                if (d.connected) accent else onBg.copy(alpha = 0.1f),
                if (d.connected) onAccent else onBg.copy(alpha = 0.5f),
                enabled = d.connected,
            ) {
                controller.pauseAll()
                d.startCalibration(synth)
            }
        }
    }
}

@Composable
private fun LatencyRow(label: String, value: Int, onBg: Color, highlighted: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = onBg.copy(alpha = if (highlighted) 1f else 0.6f),
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        SmallStep("−", "Less delay", onBg) { onChange(value - 5) }
        Text(
            "$value ms",
            style = MaterialTheme.typography.labelLarge,
            color = onBg,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
        SmallStep("+", "More delay", onBg) { onChange(value + 5) }
    }
}

@Composable
private fun SmallStep(label: String, description: String, onBg: Color, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = onBg.copy(alpha = 0.1f), contentColor = onBg),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun SheetButton(label: String, bg: Color, fg: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg, contentColor = fg,
            disabledContainerColor = bg, disabledContentColor = fg,
        ),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/**
 * Every pad the kit has sent or the profile knows, one card per kit part: a snare's several
 * notes share a card, and all toms share one. Tap a pad number to change what it plays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PadMapSection(d: DrumSession, onBg: Color, accent: Color, onClose: () -> Unit) {
    val map = d.map
    val seen = d.judge.seenNotes.keys
    val notes = (seen.toList() + map.knownNotes()).distinct()
    val groups = remember(map, notes) { KitLayoutRules.groupPads(notes, map) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.drum_kit_pads),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onBg,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { d.resetMap() }) { Text("Reset", color = accent, style = MaterialTheme.typography.labelLarge) }
            TextButton(onClick = onClose) { Text("Done", color = onBg, style = MaterialTheme.typography.labelLarge) }
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(groups, key = { it.title }) { group ->
                Column(
                    Modifier.fillMaxWidth().animateItem().clip(MaterialTheme.shapes.large)
                        .background(onBg.copy(alpha = 0.06f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(group.title, style = MaterialTheme.typography.titleSmall, color = onBg)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.entries.forEach { entry ->
                            PadChip(entry, map, d.lastNote == entry.note, d.lastNoteSerial, entry.note in seen, onBg, accent) {
                                d.assign(entry.note, it)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadChip(
    entry: PadEntry,
    map: DrumMap,
    isLast: Boolean,
    serial: Int,
    seen: Boolean,
    onBg: Color,
    accent: Color,
    onAssign: (DrumPiece?) -> Unit,
) {
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(serial) {
        if (isLast) {
            flash = true
            delay(MotionTokens.DurationShort4.toLong())
            flash = false
        }
    }
    val bg by animateColorAsState(
        if (flash) accent.copy(alpha = 0.35f) else onBg.copy(alpha = if (seen) 0.1f else 0.05f),
        animationSpec = tween(if (flash) MotionTokens.DurationShort1 else MotionTokens.DurationMedium4, easing = MotionTokens.Standard),
        label = "pad_flash",
    )
    var menu by remember { mutableStateOf(false) }
    val pieceLabel = entry.piece?.label ?: if (map.isPinned(entry.note)) "Ignored" else "Not mapped"
    val source = when (map.sourceOf(entry.note)) {
        DrumMap.Source.MANUAL -> "Set by you"
        DrumMap.Source.LEARNED -> "Learned"
        DrumMap.Source.PROFILE -> "Kit default"
        DrumMap.Source.NONE -> null
    }
    Box {
        Row(
            Modifier.clip(MaterialTheme.shapes.medium).background(bg).clickable(role = Role.Button) { menu = true }
                .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = "Pad ${entry.note}, $pieceLabel${source?.let { ", $it" } ?: ""}. Change" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${entry.note}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = onBg)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(pieceLabel, style = MaterialTheme.typography.bodyMedium, color = onBg.copy(alpha = if (entry.piece != null) 1f else 0.6f))
                source?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelSmall,
                        color = if (map.sourceOf(entry.note) == DrumMap.Source.LEARNED) accent else onBg.copy(alpha = 0.6f),
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DrumPiece.entries.filter { it != DrumPiece.OTHER }.forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = { menu = false; onAssign(p) })
            }
            DropdownMenuItem(text = { Text("Ignore this pad") }, onClick = { menu = false; onAssign(null) })
        }
    }
}
