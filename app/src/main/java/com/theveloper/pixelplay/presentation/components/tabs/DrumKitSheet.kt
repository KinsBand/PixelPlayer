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
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.drumkit.AudioRoute
import com.theveloper.pixelplay.data.drumkit.DrumKitConnection
import com.theveloper.pixelplay.data.drumkit.DrumMap
import com.theveloper.pixelplay.data.drumkit.DrumPiece
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
 * and the pad map (automatic, with every pad editable).
 */
@Composable
internal fun ColumnScope.DrumKitSheet(controller: TabPracticeController, onBg: Color, accent: Color, onAccent: Color) {
    val d = controller.drums
    LaunchedEffect(Unit) { d.refreshRoute() }
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Drum kit", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onBg, modifier = Modifier.align(Alignment.CenterHorizontally))
        val status = d.status
        val (statusText, statusSub) = when (status) {
            is DrumKitConnection.Status.Connected -> status.name to (if (status.usb) "Connected over USB" else "Connected") + " · " + d.map.profile.name
            is DrumKitConnection.Status.Connecting -> status.name to "Connecting…"
            is DrumKitConnection.Status.Failed -> status.name to status.message
            DrumKitConnection.Status.NoKit -> "No kit connected" to "Plug the kit's USB port into your phone with a USB-OTG cable (for a DTX450K, the module's USB TO HOST port)."
            DrumKitConnection.Status.Unsupported -> "MIDI isn't available" to "This phone doesn't support MIDI devices."
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(onBg.copy(alpha = 0.06f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    when (status) {
                        is DrumKitConnection.Status.Connected -> Color(0xFF34C759)
                        is DrumKitConnection.Status.Connecting -> Color(0xFFFFB020)
                        else -> onBg.copy(alpha = 0.3f)
                    },
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(statusText, color = onBg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(statusSub, color = onBg.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }

        if (!d.hasScore) {
            Text(
                "Open a drum part to score your playing.",
                color = onBg.copy(alpha = 0.6f), fontSize = 13.sp,
            )
        }

        // Scoring on/off
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(onBg.copy(alpha = 0.06f))
                .clickable { d.toggleScoring() }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Score my playing", color = onBg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Notes turn green (in time), amber (early, late or off dynamics) or red (missed)", color = onBg.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Switch(
                checked = d.scoringEnabled,
                onCheckedChange = { d.toggleScoring() },
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = onAccent),
            )
        }

        CalibrationSection(controller, d, onBg, accent, onAccent)

        PadMapSection(d, onBg, accent)
    }
}

@Composable
private fun CalibrationSection(controller: TabPracticeController, d: DrumSession, onBg: Color, accent: Color, onAccent: Color) {
    val synth = controller.sound == TabSound.SYNTH
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(onBg.copy(alpha = 0.06f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Delay (${d.route.label})", color = onBg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Allows for the time from your hit to the phone, and from the phone to your ears. " +
                if (d.calibratedRoute) "Set by the tap test." else "Not measured yet — run the tap test once.",
            color = onBg.copy(alpha = 0.6f), fontSize = 12.sp,
        )
        if (d.route == AudioRoute.BLUETOOTH) {
            Text(
                "Bluetooth headphones add 150–250 ms and it can drift. Wired headphones or the speaker score much more reliably.",
                color = Color(0xFFFFB020), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        LatencyRow("ORIG.", d.latencyOrig, onBg, highlighted = !synth) { d.setLatency(false, it) }
        LatencyRow("SYNTH", d.latencySynth, onBg, highlighted = synth) { d.setLatency(true, it) }

        val mode = d.calibrating
        if (mode != null) {
            val done = d.calibrationProgress
            val countIn = DrumSession.CALIBRATION_COUNT_IN
            Text(
                if (done < countIn) "Listen… ${countIn - done}" else "Hit a pad on every click",
                color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
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
                    if (r.latencyMs < -100) "Not enough hits (${r.pairs}) — try again, hitting on each click."
                    else "Delay set to ${r.latencyMs} ms (${r.pairs} hits, ±${r.spreadMs.toInt()} ms).",
                    color = onBg.copy(alpha = 0.8f), fontSize = 12.sp,
                )
            }
            SheetButton(
                if (d.connected) "Tap test (${if (synth) "SYNTH" else "ORIG."})" else "Connect the kit to run the tap test",
                if (d.connected) accent else onBg.copy(alpha = 0.1f),
                if (d.connected) onAccent else onBg.copy(alpha = 0.5f),
            ) {
                if (d.connected) {
                    controller.pauseAll()
                    d.startCalibration(synth)
                }
            }
        }
    }
}

@Composable
private fun LatencyRow(label: String, value: Int, onBg: Color, highlighted: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = onBg.copy(alpha = if (highlighted) 1f else 0.6f), fontSize = 13.sp,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f),
        )
        SmallStep("−", onBg) { onChange(value - 5) }
        Text("$value ms", color = onBg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
        SmallStep("+", onBg) { onChange(value + 5) }
    }
}

@Composable
private fun SmallStep(label: String, onBg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(onBg.copy(alpha = 0.1f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = onBg, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SheetButton(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PadMapSection(d: DrumSession, onBg: Color, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pads", color = onBg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Mapped automatically — hit a pad to find it here, tap a row to change it.",
                    color = onBg.copy(alpha = 0.6f), fontSize = 12.sp,
                )
            }
            Text(
                "Reset", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { d.resetMap() }.padding(8.dp),
            )
        }
        val map = d.map
        val seen = d.judge.seenNotes.keys
        // Pads the kit has sent first (most useful), then the rest of the profile.
        val notes = (seen.toList() + map.knownNotes()).distinct()
        for (note in notes) {
            PadRow(note, map, note in seen, d.lastNote == note, d.lastNoteSerial, onBg, accent) { d.assign(note, it) }
        }
    }
}

@Composable
private fun PadRow(
    note: Int,
    map: DrumMap,
    seen: Boolean,
    isLast: Boolean,
    serial: Int,
    onBg: Color,
    accent: Color,
    onAssign: (DrumPiece?) -> Unit,
) {
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(serial) {
        if (isLast) {
            flash = true
            delay(220)
            flash = false
        }
    }
    val bg by animateColorAsState(
        if (flash) accent.copy(alpha = 0.35f) else onBg.copy(alpha = if (seen) 0.08f else 0.04f),
        animationSpec = tween(if (flash) 60 else 400), label = "pad_flash",
    )
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).clickable { menu = true }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(40.dp).clip(RoundedCornerShape(8.dp)).background(onBg.copy(alpha = 0.1f)).padding(vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) { Text("$note", color = onBg, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Text(
                map.pieceFor(note)?.label ?: if (map.isPinned(note)) "Ignored" else "Not mapped",
                color = onBg.copy(alpha = if (map.pieceFor(note) != null) 1f else 0.5f),
                fontSize = 14.sp, modifier = Modifier.weight(1f),
            )
            Text(
                when (map.sourceOf(note)) {
                    DrumMap.Source.MANUAL -> "Set by you"
                    DrumMap.Source.LEARNED -> "Learned"
                    DrumMap.Source.PROFILE -> "Kit default"
                    DrumMap.Source.NONE -> ""
                },
                color = when (map.sourceOf(note)) {
                    DrumMap.Source.LEARNED -> accent
                    else -> onBg.copy(alpha = 0.5f)
                },
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DrumPiece.entries.filter { it != DrumPiece.OTHER }.forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = { menu = false; onAssign(p) })
            }
            DropdownMenuItem(text = { Text("Ignore this pad") }, onClick = { menu = false; onAssign(null) })
        }
    }
}
