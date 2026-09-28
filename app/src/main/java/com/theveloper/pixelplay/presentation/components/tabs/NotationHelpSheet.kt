package com.theveloper.pixelplay.presentation.components.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.songsterr.NotationCatalog
import com.theveloper.pixelplay.data.songsterr.TabParser
import com.theveloper.pixelplay.data.soundfont.SoundFontStore

/**
 * Notation Help: every mark for the loaded part's instrument, the ones this song uses
 * highlighted (with the bar they first appear in), and a tap on any of them plays it.
 */
@Composable
internal fun NotationHelpSheet(controller: TabPracticeController, onBg: Color, accent: Color, onAccent: Color) {
    val context = LocalContext.current
    val ready = controller.state as? TabUiState.Ready
    Text("Notation Help", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onBg)
    if (ready == null) return
    val track = ready.track
    val family = track.family
    val items = remember(family) { NotationCatalog.itemsFor(family) }
    val used = remember(track) { NotationCatalog.usedIn(track) }
    val usedCount = items.count { it.id in used }
    val player = remember { NotationAuditionPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }

    val soundState by SoundFontStore.state.collectAsStateWithLifecycle()
    LaunchedEffect(soundState) {
        if (soundState == SoundFontStore.State.Ready && SoundFontStore.load(context) != null) controller.onSampledSoundsReady()
    }
    var onlyUsed by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    val familyName = when (family) {
        TabParser.InstrumentFamily.GUITAR -> "guitar"
        TabParser.InstrumentFamily.BASS -> "bass"
        TabParser.InstrumentFamily.DRUMS -> "drums"
        TabParser.InstrumentFamily.OTHER -> track.instrument.ifBlank { "this instrument" }.lowercase()
    }
    Text(
        "All ${items.size} marks for $familyName. Tap one to hear it; the ones in this song are highlighted.",
        color = onBg.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center,
    )

    fun play(key: String, block: () -> Boolean) {
        if (player.playingKey == key) { player.stop(); return }
        if (controller.isPlaying) controller.pauseAll()
        val ok = block()
        hint = if (ok) null else "Download the instrument sounds above to hear ${if (family == TabParser.InstrumentFamily.DRUMS) "the drums" else "this instrument"}."
    }
    val font = if (controller.sampledSounds) SoundFontStore.fontOrNull() else null

    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InstrumentSoundsCard(soundState, onBg, accent, onAccent, hint) { SoundFontStore.download(context) }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("All · ${items.size}", !onlyUsed, onBg, accent, onAccent) { onlyUsed = false }
            FilterPill("In this song · $usedCount", onlyUsed, onBg, accent, onAccent) { onlyUsed = true }
        }

        val shown = if (onlyUsed) items.filter { it.id in used } else items
        if (shown.isEmpty()) {
            Text("This part doesn't use any of these marks.", color = onBg.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        var lastGroup: NotationCatalog.Group? = null
        for (item in shown) {
            if (item.group != lastGroup) {
                lastGroup = item.group
                Text(
                    item.group.title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            val bar = used[item.id]
            NotationRow(
                item = item,
                usedAtBar = bar?.let { m -> track.measures.getOrNull(m)?.number ?: (m + 1) },
                playing = player.playingKey == item.id,
                playingBar = player.playingKey == "bar:${item.id}",
                onBg = onBg,
                accent = accent,
                onPlay = {
                    play(item.id) {
                        val demo = NotationCatalog.demoTrack(item, track) ?: return@play false
                        player.play(item.id, demo, font)
                    }
                },
                onPlayBar = bar?.let { m ->
                    {
                        play("bar:${item.id}") {
                            val tl = ready.timeline
                            val entry = tl.entries.indexOfFirst { it.measure == m }
                            entry >= 0 && player.play("bar:${item.id}", track, font, listOf(entry))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun InstrumentSoundsCard(
    state: SoundFontStore.State,
    onBg: Color, accent: Color, onAccent: Color,
    hint: String?,
    onDownload: () -> Unit,
) {
    if (state == SoundFontStore.State.Ready) {
        hint?.let { Text(it, color = onBg.copy(alpha = 0.7f), fontSize = 12.sp) }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = 0.12f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Real instrument sounds", color = onBg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Download recorded guitars, basses, drums and keys (${SoundFontStore.SIZE_BYTES / 1_000_000} MB, once) to hear every mark and to make Synth playback sound real.",
            color = onBg.copy(alpha = 0.75f), fontSize = 12.sp,
        )
        when (state) {
            is SoundFontStore.State.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.2f),
                )
                Text("Downloading… ${(state.progress * 100).toInt()}%", color = onBg.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            else -> {
                if (state is SoundFontStore.State.Failed) Text(state.message, color = onBg, fontSize = 12.sp)
                Row(
                    Modifier.clip(CircleShape).background(accent).clickable(onClick = onDownload).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = onAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state is SoundFontStore.State.Failed) "Try again" else "Download", color = onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        hint?.let { Text(it, color = onBg, fontSize = 12.sp) }
        Text("Sounds: GeneralUser GS by S. Christian Collins", color = onBg.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onBg: Color, accent: Color, onAccent: Color, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) onAccent else onBg,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent else onBg.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun NotationRow(
    item: NotationCatalog.Item,
    usedAtBar: Int?,
    playing: Boolean,
    playingBar: Boolean,
    onBg: Color,
    accent: Color,
    onPlay: () -> Unit,
    onPlayBar: (() -> Unit)?,
) {
    val used = usedAtBar != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (used) accent.copy(alpha = 0.14f) else onBg.copy(alpha = 0.05f))
            .clickable(onClick = onPlay)
            .semantics {
                role = Role.Button
                contentDescription = "${item.name}${if (used) ", used in this song" else ""}. Play example"
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
            val drumId = item.drumId
            if (drumId != null) {
                DrumSymbol(TabParser.drumArticulation(drumId), onBg)
            } else {
                Text(
                    item.symbol, color = if (used) accent else onBg, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, color = onBg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (item.drumId == null) Text(item.description, color = onBg.copy(alpha = 0.7f), fontSize = 12.sp)
            if (usedAtBar != null) {
                Text(
                    if (playingBar) "■ Playing bar $usedAtBar" else "In this song · hear bar $usedAtBar",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(CircleShape)
                        .let { m -> if (onPlayBar != null) m.clickable(onClick = onPlayBar) else m }
                        .padding(vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (playing) accent else onBg.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (playing) Color.White else onBg,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The kit piece's notehead on a small staff, as in the score. */
@Composable
private fun DrumSymbol(art: TabParser.DrumArticulation, onBg: Color) {
    val painter = remember { AndroidScorePainter() }
    Canvas(Modifier.width(64.dp).height(52.dp)) {
        val gap = 6.dp.toPx()
        val top = (size.height - 4 * gap) / 2
        val ink = onBg.toArgb()
        for (i in 0 until 5) drawLine(onBg.copy(alpha = 0.45f), Offset(0f, top + i * gap), Offset(size.width, top + i * gap), 1.dp.toPx())
        drawIntoCanvas { c ->
            painter.canvas = c.nativeCanvas
            val pos = art.staffPos
            val cx = size.width / 2
            if (pos <= -1f) { var l = -1; while (l >= pos) { painter.line(cx - gap, top + l * gap, cx + gap, top + l * gap, 1.dp.toPx(), ink); l-- } }
            if (pos >= 5f) { var l = 5; while (l <= pos) { painter.line(cx - gap, top + l * gap, cx + gap, top + l * gap, 1.dp.toPx(), ink); l++ } }
            legendHead(painter, art.glyph, cx, top + pos * gap, gap, ink)
            painter.canvas = null
        }
    }
}
