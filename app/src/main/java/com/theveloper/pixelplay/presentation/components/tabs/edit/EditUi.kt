package com.theveloper.pixelplay.presentation.components.tabs.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.theveloper.pixelplay.data.songsterr.TabParser
import com.theveloper.pixelplay.data.songsterr.edit.DrumEditOps
import com.theveloper.pixelplay.data.songsterr.edit.TabDraftStore
import com.theveloper.pixelplay.presentation.components.tabs.InstrumentIcon
import com.theveloper.pixelplay.presentation.components.tabs.TabPracticeController
import com.theveloper.pixelplay.presentation.components.tabs.TabUiState
import com.theveloper.pixelplay.presentation.components.tabs.partLabel
import com.theveloper.pixelplay.ui.theme.MotionTokens

// ═════════════════════════════════════════════════════════════════════════════
// "Edit a copy" page (inside the Instruments sheet)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Shown when Edit is tapped in the Instruments sheet: which version the copy is made from, and
 * the parts that can be edited (drums for now; the rest say "Soon").
 */
@Composable
fun EditStartPage(
    controller: TabPracticeController,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val meta = controller.meta ?: controller.loaded?.meta
    val tab = controller.loaded
    val songId = meta?.songId
    val rev = tab?.meta?.revisionId ?: meta?.revisionId
    var drafted by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(songId, rev) {
        if (songId != null && rev != null) drafted = TabDraftStore.draftedParts(context, songId, rev)
    }
    val versions = controller.revisions
    val number = versions?.let { list -> list.indexOfFirst { it.revisionId == rev }.takeIf { it >= 0 }?.let { list.size - it } }
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Edit a copy", style = MaterialTheme.typography.titleLarge, color = onBg)
        Text(
            buildString {
                append("Based on ")
                append(number?.let { "Version $it" } ?: "the version on screen")
                controller.tabAuthor?.let { append(" · Tab by $it") }
            },
            style = MaterialTheme.typography.bodyMedium, color = onBg.copy(alpha = 0.7f),
        )
        Text(
            "Your changes become the next version when you publish.",
            style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(4.dp))
        val tracks = meta?.tracks?.filter { !it.isEmpty && !it.isVocal }.orEmpty()
        tracks.forEach { t ->
            val editable = t.isDrums
            val (person, part) = partLabel(t)
            val hasDraft = t.index in drafted
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(onBg.copy(alpha = if (editable) 0.08f else 0.04f))
                    .clickable(enabled = editable && !controller.editStarting) {
                        controller.requestEditOf(t.index)
                        onStart()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { if (!editable) stateDescription = "Editing this part is coming soon" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentIcon(t, onBg.copy(alpha = if (editable) 1f else 0.38f), size = 26.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        part, style = MaterialTheme.typography.titleSmall,
                        color = onBg.copy(alpha = if (editable) 1f else 0.38f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val sub = listOfNotNull(person.takeIf { it.isNotBlank() }, if (hasDraft) "Draft waiting" else null).joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = if (editable) 0.6f else 0.3f), maxLines = 1)
                    }
                }
                if (editable) {
                    Box(
                        Modifier.clip(CircleShape).background(accent).padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(if (hasDraft) "Continue" else "Edit", style = MaterialTheme.typography.labelLarge, color = onAccent) }
                } else {
                    Text("Soon", style = MaterialTheme.typography.labelLarge, color = onBg.copy(alpha = 0.38f))
                }
            }
        }
        if (tab == null && controller.state !is TabUiState.Ready) {
            Text("Loading parts…", style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = 0.6f))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Edit header (top of the screen while editing)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Replaces the song header while editing: ✕ (leave) top-left, ✓ (review & publish) top-right,
 * the song minimised in the middle (no Next song), and the caret's bar · beat under it.
 * Back also asks before leaving.
 */
@Composable
fun EditHeader(
    editor: TabEditController,
    songTitle: String,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    fun requestExit() {
        if (editor.isDirty) confirmExit = true else editor.exit(keepDraft = false)
    }
    BackHandler { if (editor.hasSelection) editor.clearSelection() else requestExit() }

    Column(
        modifier = modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            HeaderButton(
                icon = Icons.Rounded.Close,
                description = "Leave edit mode",
                container = onBg.copy(alpha = 0.1f),
                tint = onBg,
                modifier = Modifier.align(Alignment.CenterStart),
            ) { requestExit() }

            // The song, minimised and centred (there's no Next song in edit mode).
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 220.dp)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(onBg.copy(alpha = 0.08f))
                    .padding(start = 6.dp, end = 16.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "Editing $songTitle" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape)) { cover() }
                Spacer(Modifier.width(8.dp))
                Text(
                    songTitle, style = MaterialTheme.typography.titleSmall, color = onBg,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            HeaderButton(
                icon = Icons.Rounded.Check,
                description = "Review and publish",
                container = if (editor.isDirty) accent else onBg.copy(alpha = 0.1f),
                tint = if (editor.isDirty) onAccent else onBg.copy(alpha = 0.38f),
                enabled = editor.isDirty,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) { editor.reviewing = true }
        }
        Spacer(Modifier.height(8.dp))
        CaretInfo(editor, onBg, accent)
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave edit mode?") },
            text = { Text("Keep the draft on this phone to carry on later, or throw the changes away.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; editor.exit(keepDraft = true) }) { Text("Keep draft") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false; editor.exit(keepDraft = false) }) { Text("Discard") }
                    TextButton(onClick = { confirmExit = false }) { Text("Cancel") }
                }
            },
        )
    }
    if (editor.reviewing) {
        ReviewSheet(editor, onBg, accent, onAccent, containerColor)
    }
}

@Composable
private fun HeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    container: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint) }
}

/** "Bar 5 · Beat 2" (or "3 beats selected") and how full that bar is. */
@Composable
private fun CaretInfo(editor: TabEditController, onBg: Color, accent: Color) {
    @Suppress("UNUSED_VARIABLE") val v = editor.version
    val caret = editor.caret
    val sel = editor.selection
    val (written, expected) = editor.caretBarFill()
    val beatLen = (DrumEditOps.WHOLE / editor.signature().second).coerceAtLeast(1)
    val text = if (sel != null) {
        val n = editor.selectionCount()
        val bars = if (sel.first.measure == sel.second.measure) "bar ${sel.first.measure + 1}"
        else "bars ${sel.first.measure + 1}–${sel.second.measure + 1}"
        "$n beat${if (n == 1) "" else "s"} selected · $bars"
    } else {
        "Bar ${caret.measure + 1} of ${editor.barCount} · Beat ${caret.tick / beatLen + 1}"
    }
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = onBg,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(4.dp))
    Box(Modifier.width(160.dp)) { BarFillMeter(written, expected, onBg, accent) }
}

/** Undo + redo, in place of the skip button on the play row while editing. */
@Composable
fun EditUndoRedo(editor: TabEditController, size: Dp, container: Color, tint: Color) {
    val haptics = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple(Icons.AutoMirrored.Rounded.Undo, "Undo", editor.canUndo) to { editor.undo() },
            Triple(Icons.AutoMirrored.Rounded.Redo, "Redo", editor.canRedo) to { editor.redo() },
        ).forEach { (spec, action) ->
            val (icon, label, enabled) = spec
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(container)
                    .clickable(enabled = enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        action()
                    }
                    .semantics { contentDescription = label; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(26.dp)) }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Tools (replace the practice panel while editing)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Edit tools: palette chips, the palette's buttons (or the selection's actions while beats are
 * selected), then two rows of drum pads that scroll sideways. Only drum tools: no clefs, keys
 * or accidentals.
 */
@Composable
fun EditToolsPanel(
    editor: TabEditController,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteChips(editor, onBg, accent, onAccent)
        AnimatedContent(
            targetState = editor.hasSelection,
            transitionSpec = {
                fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
            },
            label = "toolsOrSelection",
        ) { selecting ->
            if (selecting) SelectionRow(editor, onBg, accent, onAccent) else PaletteRow(editor, onBg, accent, onAccent)
        }
        DrumPadBoard(editor, onBg, accent, onAccent)
    }
}

/** How full the caret's bar is (always full while editing, but shows when a bar was off). */
@Composable
private fun BarFillMeter(written: Long, expected: Long, onBg: Color, accent: Color) {
    val frac = if (expected <= 0) 0f else (written.toFloat() / expected).coerceIn(0f, 1f)
    val over = written > expected
    val anim by animateFloatAsState(frac, tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized), label = "barFill")
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(onBg.copy(alpha = 0.12f))
            .semantics { contentDescription = if (over) "Bar too long" else "Bar ${(frac * 100).toInt()} percent full" },
    ) {
        Box(
            Modifier.fillMaxWidth(anim).height(6.dp).clip(CircleShape)
                .background(if (over) MaterialTheme.colorScheme.error else accent),
        )
    }
}

@Composable
private fun PaletteChips(editor: TabEditController, onBg: Color, accent: Color, onAccent: Color) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EditPalette.entries.forEach { p ->
            val on = editor.palette == p
            Box(
                Modifier.height(36.dp).clip(CircleShape)
                    .background(if (on) accent else onBg.copy(alpha = 0.08f))
                    .clickable { editor.palette = p }
                    .padding(horizontal = 14.dp)
                    .semantics { role = Role.Tab; stateDescription = if (on) "Selected" else "" },
                contentAlignment = Alignment.Center,
            ) { Text(p.label, style = MaterialTheme.typography.labelLarge, color = if (on) onAccent else onBg) }
        }
    }
}

/** Actions for the selected beats (long-press and drag on the tab, or the Select tool). */
@Composable
private fun SelectionRow(editor: TabEditController, onBg: Color, accent: Color, onAccent: Color) {
    val canPaste = TabEditController.clipboard.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolButton("Copy", "Copy the selected beats", onBg, accent, onAccent, onClick = { editor.copySelection() }) {
            Icon(Icons.Rounded.ContentCopy, null, tint = it)
        }
        ToolButton("Cut", "Cut the selected beats", onBg, accent, onAccent, onClick = { editor.cutSelection() }) {
            Icon(Icons.Rounded.ContentCut, null, tint = it)
        }
        ToolButton("Paste", "Paste over the selection", onBg, accent, onAccent, enabled = canPaste, onClick = { editor.paste() }) {
            Icon(Icons.Rounded.ContentPaste, null, tint = it)
        }
        ToolButton("Delete", "Delete the selected beats", onBg, accent, onAccent, onClick = { editor.deleteSelection() }) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = it)
        }
        ToolButton("Whole bar", "Select the whole bar", onBg, accent, onAccent, onClick = { editor.selectBar() }) {
            Icon(Icons.Rounded.SelectAll, null, tint = it)
        }
        ToolButton("Done", "Clear the selection", onBg, accent, onAccent, selected = true, onClick = {
            editor.clearSelection(); editor.selectMode = false
        }) { Icon(Icons.Rounded.Check, null, tint = it) }
    }
}

@Composable
private fun PaletteRow(editor: TabEditController, onBg: Color, accent: Color, onAccent: Color) {
    var dialog by remember { mutableStateOf<String?>(null) }
    val canPaste = TabEditController.clipboard.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (editor.palette) {
            EditPalette.NOTES -> {
                listOf(1 to "Whole", 2 to "Half", 4 to "Quarter", 8 to "8th", 16 to "16th", 32 to "32nd").forEach { (t, label) ->
                    ToolButton(label, lengthName(t), onBg, accent, onAccent, selected = editor.inputType == t, onClick = { editor.chooseLength(t) }) { tint ->
                        NoteGlyph(t, tint)
                    }
                }
                ToolButton("Dot", "Dotted", onBg, accent, onAccent, selected = editor.dotted, onClick = { editor.toggleDot() }) { ToolText("•", it) }
                ToolButton("Rest", "Rest at the caret", onBg, accent, onAccent, onClick = { editor.addRest() }) { ToolText("𝄽", it, fallback = "R") }
                ToolButton("Chord", "Stack notes on one beat", onBg, accent, onAccent, selected = editor.chord, onClick = { editor.chord = !editor.chord }) {
                    Icon(Icons.Rounded.Layers, null, tint = it)
                }
                ToolButton("Select", "Select beats by tapping the tab", onBg, accent, onAccent, selected = editor.selectMode, onClick = {
                    editor.selectMode = !editor.selectMode
                    if (editor.selectMode) editor.startSelection(editor.caret.measure, editor.caret.tick)
                }) { Icon(Icons.Rounded.SelectAll, null, tint = it) }
                ToolButton("Copy", "Copy the beat at the caret", onBg, accent, onAccent, onClick = { editor.copySelection() }) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = it)
                }
                ToolButton("Paste", "Paste at the caret", onBg, accent, onAccent, enabled = canPaste, onClick = { editor.paste() }) {
                    Icon(Icons.Rounded.ContentPaste, null, tint = it)
                }
                ToolButton("Delete", "Delete the beat at the caret", onBg, accent, onAccent, onClick = { editor.deleteAtCaret() }) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = it)
                }
            }
            EditPalette.ACCENTS -> {
                ToolButton("Accent", "Accent", onBg, accent, onAccent, onClick = { editor.toggleFlag(DrumEditOps.NoteFlag.ACCENT) }) { ToolText(">", it) }
                ToolButton("Heavy", "Heavy accent", onBg, accent, onAccent, onClick = { editor.toggleFlag(DrumEditOps.NoteFlag.HEAVY_ACCENT) }) { ToolText("^", it) }
                ToolButton("Ghost", "Ghost note", onBg, accent, onAccent, onClick = { editor.toggleFlag(DrumEditOps.NoteFlag.GHOST) }) { ToolText("( )", it) }
                ToolButton("Staccato", "Staccato", onBg, accent, onAccent, onClick = { editor.toggleFlag(DrumEditOps.NoteFlag.STACCATO) }) { ToolText("•", it) }
            }
            EditPalette.DYNAMICS -> {
                listOf(
                    "ppp" to "Softest", "pp" to "Very soft", "p" to "Soft", "mp" to "Med. soft",
                    "mf" to "Med. loud", "f" to "Loud", "ff" to "Very loud", "fff" to "Loudest",
                ).forEach { (v, label) ->
                    ToolButton(label, "Dynamic $v", onBg, accent, onAccent, onClick = { editor.toggleVelocity(v) }) { tint -> DynamicText(v, tint) }
                }
            }
            EditPalette.BAR -> {
                val (n, d) = editor.signature()
                ToolButton("Time", "Time signature $n over $d", onBg, accent, onAccent, onClick = { dialog = "sig" }) { ToolText("$n/$d", it) }
                ToolButton("Bar before", "Insert a bar before", onBg, accent, onAccent, onClick = { editor.insertBarBefore() }) {
                    Icon(Icons.Rounded.AddCircleOutline, null, tint = it)
                }
                ToolButton("Bar after", "Insert a bar after", onBg, accent, onAccent, onClick = { editor.insertBarAfter() }) {
                    Icon(Icons.Rounded.AddCircle, null, tint = it)
                }
                ToolButton("Copy bar", "Duplicate this bar", onBg, accent, onAccent, onClick = { editor.duplicateBar() }) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = it)
                }
                ToolButton("Select bar", "Select this bar", onBg, accent, onAccent, onClick = { editor.selectBar() }) {
                    Icon(Icons.Rounded.SelectAll, null, tint = it)
                }
                ToolButton("Delete bar", "Delete this bar", onBg, accent, onAccent, onClick = { editor.deleteBar() }) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = it)
                }
                ToolButton("Double line", "Double bar line", onBg, accent, onAccent, onClick = { editor.toggleDoubleBar() }) { ToolText("‖", it) }
            }
            EditPalette.REPEATS -> {
                ToolButton("Start", "Start repeat", onBg, accent, onAccent, onClick = { editor.toggleRepeatStart() }) { ToolText("‖:", it) }
                listOf(2, 3, 4).forEach { k ->
                    ToolButton("End ×$k", "End repeat, play $k times", onBg, accent, onAccent, onClick = { editor.setRepeatEnd(k) }) { ToolText(":‖", it) }
                }
                ToolButton("No end", "Remove end repeat", onBg, accent, onAccent, onClick = { editor.setRepeatEnd(null) }) {
                    Icon(Icons.Rounded.Close, null, tint = it)
                }
                ToolButton("Section", "Section name", onBg, accent, onAccent, onClick = { dialog = "marker" }) {
                    Icon(Icons.Rounded.BookmarkBorder, null, tint = it)
                }
            }
            EditPalette.TEXT -> {
                ToolButton("Text", "Text above the beat", onBg, accent, onAccent, onClick = { dialog = "text" }) {
                    Icon(Icons.Rounded.TextFields, null, tint = it)
                }
                ToolButton("Section", "Section name", onBg, accent, onAccent, onClick = { dialog = "marker" }) {
                    Icon(Icons.Rounded.BookmarkBorder, null, tint = it)
                }
            }
        }
    }
    when (dialog) {
        "sig" -> SignatureDialog(editor.signature(), onDismiss = { dialog = null }) { n, d -> editor.setSignature(n, d); dialog = null }
        "marker" -> TextDialog("Section name", "Verse, Chorus…", onDismiss = { dialog = null }) { editor.setMarker(it); dialog = null }
        "text" -> TextDialog("Text above the beat", "e.g. Half-time feel", onDismiss = { dialog = null }) { editor.setBeatText(it); dialog = null }
    }
}

private fun lengthName(t: Int) = when (t) {
    1 -> "Whole note"; 2 -> "Half note"; 4 -> "Quarter note"; 8 -> "Eighth note"; 16 -> "Sixteenth note"; else -> "Thirty-second note"
}

/** A real tool button: tonal tile with the tool's icon / glyph and its name underneath. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolButton(
    label: String,
    description: String,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "toolPress")
    val container = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHighest
    val tint = (if (selected) onAccent else MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else 0.38f)
    Column(
        Modifier
            .width(68.dp)
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.medium)
            .background(container.copy(alpha = if (enabled) container.alpha else container.alpha * 0.5f))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                contentDescription = description
                role = Role.Button
                if (selected) stateDescription = "On"
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) { content(tint) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToolText(text: String, tint: Color, small: Boolean = false, fallback: String? = null) {
    // Music symbols outside the BMP may be missing from the device font: use the fallback then.
    val paint = remember { android.graphics.Paint() }
    val shown = if (fallback != null && !paint.hasGlyph(text)) fallback else text
    Text(
        shown,
        style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
        color = tint, maxLines = 2, textAlign = TextAlign.Center,
    )
}

@Composable
private fun DynamicText(text: String, tint: Color) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold),
        color = tint,
    )
}

/** A note of [type] (1 whole … 32 thirty-second) drawn small, so no music font is needed. */
@Composable
private fun NoteGlyph(type: Int, tint: Color) {
    Canvas(Modifier.size(width = 22.dp, height = 28.dp)) {
        val headW = size.width * 0.46f
        val headH = size.height * 0.24f
        val cx = size.width * 0.38f
        val cy = size.height * 0.78f
        val hollow = type <= 2
        rotateEllipse(cx, cy, headW, headH, tint, hollow)
        if (type >= 2) {
            val sx = cx + headW / 2 - 1.dp.toPx()
            drawLine(tint, Offset(sx, cy - headH * 0.2f), Offset(sx, size.height * 0.08f), 1.6.dp.toPx())
            val flags = when (type) { 8 -> 1; 16 -> 2; 32 -> 3; else -> 0 }
            repeat(flags) { i ->
                val y = size.height * 0.08f + i * size.height * 0.16f
                drawLine(tint, Offset(sx, y), Offset(sx + size.width * 0.38f, y + size.height * 0.18f), 1.6.dp.toPx())
            }
        }
    }
}

private fun DrawScope.rotateEllipse(cx: Float, cy: Float, w: Float, h: Float, color: Color, hollow: Boolean) {
    val topLeft = Offset(cx - w / 2, cy - h / 2)
    if (hollow) drawOval(color, topLeft, Size(w, h), style = Stroke(1.6.dp.toPx()))
    else drawOval(color, topLeft, Size(w, h))
}

@Composable
private fun SignatureDialog(current: Pair<Int, Int>, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    var n by remember { mutableStateOf(current.first) }
    var d by remember { mutableStateOf(current.second) }
    val dens = listOf(2, 4, 8, 16)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time signature from this bar") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Stepper("Beats", "$n", { if (n > 1) n-- }, { if (n < 16) n++ })
                Stepper("Note", "$d", { dens.indexOf(d).takeIf { it > 0 }?.let { d = dens[it - 1] } }, { dens.indexOf(d).takeIf { it < dens.lastIndex }?.let { d = dens[it + 1] } })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(n, d) }) { Text("Set $n/$d") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(value, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun TextDialog(title: String, hint: String, onDismiss: () -> Unit, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, placeholder = { Text(hint) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text(if (text.isBlank()) "Remove" else "Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Drum pads
// ═════════════════════════════════════════════════════════════════════════════

/** One pad: its kit pieces (first = default; long-press for the rest). */
private data class PadSpec(val key: String, val variants: List<Int>)

/**
 * Pads as columns of two (top, bottom). Four columns fit on screen (8 pads); swipe sideways
 * for the rest. The first four are the core kit: cymbals / hats / toms on top, kick / snare /
 * toms underneath, like sitting at the kit.
 */
private val PAD_COLUMNS: List<Pair<PadSpec, PadSpec?>> = listOf(
    PadSpec("crash", listOf(49, 57, 97, 98)) to PadSpec("kick", listOf(36, 35)),
    PadSpec("hihat", listOf(42, 92, 46)) to PadSpec("snare", listOf(38, 91, 37, 40)),
    PadSpec("ride", listOf(51, 59, 53, 93)) to PadSpec("floor", listOf(43, 41)),
    PadSpec("hitom", listOf(48, 50)) to PadSpec("midtom", listOf(47, 45)),
    PadSpec("pedal", listOf(44)) to PadSpec("lowtom", listOf(45, 41)),
    PadSpec("china", listOf(52, 96)) to PadSpec("splash", listOf(55, 95)),
    PadSpec("cowbell", listOf(56)) to PadSpec("tamb", listOf(54)),
    PadSpec("clap", listOf(39)) to null,
)

/** Short pad names that always fit (flat.io cuts its names off). */
internal fun padName(id: Int): String = when (id) {
    49 -> "Crash"; 57 -> "Crash 2"; 97 -> "Crash choke"; 98 -> "Crash 2 choke"
    51 -> "Ride"; 59 -> "Ride edge"; 53 -> "Ride bell"; 93 -> "Ride choke"
    52 -> "China"; 96 -> "China choke"; 55 -> "Splash"; 95 -> "Splash choke"
    42 -> "Hi-hat"; 92 -> "Half hat"; 46 -> "Open hat"; 44 -> "Pedal hat"
    48 -> "High tom"; 50 -> "High floor"; 47 -> "Mid tom"; 45 -> "Low tom"; 41 -> "Low floor"; 43 -> "Floor tom"
    38 -> "Snare"; 91 -> "Rim shot"; 37 -> "Side stick"; 40 -> "E-snare"; 39 -> "Clap"
    36 -> "Kick"; 35 -> "Kick 2"; 56 -> "Cowbell"; 54 -> "Tambourine"
    else -> TabParser.drumArticulation(id).name
}

@Composable
private fun DrumPadBoard(editor: TabEditController, onBg: Color, accent: Color, onAccent: Color) {
    // Each pad remembers the variant last picked (e.g. the hi-hat pad becomes "Open hat").
    val chosen = remember { mutableStateMapOf<String, Int>() }
    val lit = editor.piecesAtCaret
    var popupFor by remember { mutableStateOf<PadSpec?>(null) }
    val gap = 6.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val padW = (maxWidth - gap * 3) / 4
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            PAD_COLUMNS.forEach { (top, bottom) ->
                Column(Modifier.width(padW), verticalArrangement = Arrangement.spacedBy(gap)) {
                    listOf(top, bottom).forEach { spec ->
                        if (spec == null) {
                            RestPad(onBg) { editor.addRest() }
                            return@forEach
                        }
                        val id = chosen[spec.key] ?: spec.variants.first()
                        Box {
                            DrumPad(
                                id = id,
                                lit = spec.variants.any { it in lit },
                                hasVariants = spec.variants.size > 1,
                                onBg = onBg, accent = accent, onAccent = onAccent,
                                onTap = { editor.tapPad(id) },
                                onLongPress = {
                                    if (spec.variants.size > 1) popupFor = spec else editor.preview(id)
                                },
                            )
                            if (popupFor == spec) {
                                PadVariantPopup(
                                    spec = spec,
                                    current = id,
                                    onBg = onBg, accent = accent, onAccent = onAccent,
                                    onDismiss = { popupFor = null },
                                ) { pick ->
                                    chosen[spec.key] = pick
                                    popupFor = null
                                    editor.tapPad(pick)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DrumPad(
    id: Int,
    lit: Boolean,
    hasVariants: Boolean,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "padPress",
    )
    val container = if (lit) accent else MaterialTheme.colorScheme.secondaryContainer
    val content = if (lit) onAccent else MaterialTheme.colorScheme.onSecondaryContainer
    val art = TabParser.drumArticulation(id)
    Column(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTap()
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .semantics {
                contentDescription = "${padName(id)}. Adds at the caret" + if (hasVariants) ". Hold for more" else ""
                role = Role.Button
                if (lit) stateDescription = "On this beat"
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MiniStaff(art.staffPos, art.glyph, content, Modifier.size(width = 34.dp, height = 22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                padName(id), style = MaterialTheme.typography.labelMedium, color = content,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (hasVariants) {
                Spacer(Modifier.width(2.dp))
                Box(Modifier.size(4.dp).clip(CircleShape).background(content.copy(alpha = 0.6f)))
            }
        }
    }
}

@Composable
private fun RestPad(onBg: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(MaterialTheme.shapes.medium)
            .border(1.dp, onBg.copy(alpha = 0.25f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Rest. Adds a rest at the caret"; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Text("Rest", style = MaterialTheme.typography.labelLarge, color = onBg) }
}

/** Long-press on a pad: its variants (rim shot, open hat, choke …) in a row above it. */
@Composable
private fun PadVariantPopup(
    spec: PadSpec,
    current: Int,
    onBg: Color,
    accent: Color,
    onAccent: Color,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, with(density) { -64.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.9f) +
                fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
            exit = fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
        ) {
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                spec.variants.forEach { v ->
                    val on = v == current
                    val art = TabParser.drumArticulation(v)
                    val fg = if (on) onAccent else MaterialTheme.colorScheme.onSurface
                    Column(
                        Modifier
                            .width(72.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (on) accent else Color.Transparent)
                            .clickable { onPick(v) }
                            .padding(vertical = 6.dp)
                            .semantics { contentDescription = padName(v); role = Role.Button },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MiniStaff(art.staffPos, art.glyph, fg, Modifier.size(width = 34.dp, height = 22.dp))
                        Text(padName(v), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Five lines with the piece's notehead where the staff puts it. */
@Composable
private fun MiniStaff(staffPos: Float, glyph: TabParser.DrumGlyph, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        // Room for one ledger line above and below: positions −2 … 6.
        val gap = size.height / 8f
        val top = gap * 2f
        val line = 0.8.dp.toPx()
        for (i in 0..4) {
            val y = top + i * gap
            drawLine(tint.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), line)
        }
        val cx = size.width / 2f
        val cy = top + staffPos * gap
        // Ledger line for heads above / below the staff.
        if (staffPos < -0.5f || staffPos > 4.5f) {
            val ly = top + (if (staffPos < 0) -1f else 5f) * gap
            drawLine(tint.copy(alpha = 0.55f), Offset(cx - gap * 1.3f, ly), Offset(cx + gap * 1.3f, ly), line)
        }
        drawNotehead(glyph, cx, cy, gap, tint)
    }
}

private fun DrawScope.drawNotehead(glyph: TabParser.DrumGlyph, cx: Float, cy: Float, gap: Float, tint: Color) {
    val r = gap * 0.62f
    val stroke = 1.4.dp.toPx()
    fun cross() {
        drawLine(tint, Offset(cx - r, cy - r), Offset(cx + r, cy + r), stroke)
        drawLine(tint, Offset(cx - r, cy + r), Offset(cx + r, cy - r), stroke)
    }
    when (glyph) {
        TabParser.DrumGlyph.HEAD -> drawOval(tint, Offset(cx - r * 1.25f, cy - r * 0.85f), Size(r * 2.5f, r * 1.7f))
        TabParser.DrumGlyph.X, TabParser.DrumGlyph.X_HAT -> cross()
        TabParser.DrumGlyph.X_CIRCLE -> { cross(); drawCircle(tint, r * 1.35f, Offset(cx, cy), style = Stroke(stroke)) }
        TabParser.DrumGlyph.X_CHOKE -> { cross(); drawCircle(tint, r * 0.3f, Offset(cx + r * 1.8f, cy - r * 1.2f)) }
        TabParser.DrumGlyph.SLASH_CIRCLE -> {
            drawCircle(tint, r * 1.2f, Offset(cx, cy), style = Stroke(stroke))
            drawLine(tint, Offset(cx - r, cy + r), Offset(cx + r, cy - r), stroke)
        }
        TabParser.DrumGlyph.DIAMOND, TabParser.DrumGlyph.DIAMOND_OPEN -> {
            val p = Path().apply {
                moveTo(cx, cy - r * 1.2f); lineTo(cx + r * 1.2f, cy); lineTo(cx, cy + r * 1.2f); lineTo(cx - r * 1.2f, cy); close()
            }
            if (glyph == TabParser.DrumGlyph.DIAMOND) drawPath(p, tint) else drawPath(p, tint, style = Stroke(stroke))
        }
        TabParser.DrumGlyph.TRIANGLE -> {
            val p = Path().apply { moveTo(cx, cy - r * 1.2f); lineTo(cx + r * 1.2f, cy + r); lineTo(cx - r * 1.2f, cy + r); close() }
            drawPath(p, tint)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Review & publish
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(editor: TabEditController, onBg: Color, accent: Color, onAccent: Color, containerColor: Color) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val summary = remember(editor.version) { editor.summary() }
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { editor.reviewing = false }
    }
    ModalBottomSheet(
        onDismissRequest = { editor.reviewing = false },
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = onBg,
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Publish to Songsterr", style = MaterialTheme.typography.titleLarge, color = onBg)
            Text(
                buildString {
                    append("${editor.partName} · ${summary.barsChanged} bar${if (summary.barsChanged == 1) "" else "s"} changed")
                    append(" · +${summary.notesAdded} −${summary.notesRemoved} notes")
                    if (summary.barsAfter != summary.barsBefore) append(" · ${summary.barsBefore} → ${summary.barsAfter} bars")
                },
                style = MaterialTheme.typography.bodyMedium, color = onBg.copy(alpha = 0.75f),
            )
            val bars = editor.changedBars.sorted()
            if (bars.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bars.take(40).forEach { b ->
                        Box(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer)
                                .clickable { editor.moveCaret(b, 0); close() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .semantics { contentDescription = "Go to bar ${b + 1}" },
                        ) { Text("Bar ${b + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                    }
                }
            }
            OutlinedTextField(
                value = editor.message,
                onValueChange = { editor.message = it },
                label = { Text("What changed?") },
                placeholder = { Text("e.g. Fixed the chorus fills") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Publishing sends this as the next version of the tab on Songsterr, signed in with your Songsterr account. " +
                    "It arrives in the next update; your draft stays saved on this phone until then.",
                style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = 0.6f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(CircleShape).background(onBg.copy(alpha = 0.08f)).clickable { close() },
                    contentAlignment = Alignment.Center,
                ) { Text("Keep editing", style = MaterialTheme.typography.labelLarge, color = onBg) }
                Box(
                    Modifier.weight(1f).height(52.dp).clip(CircleShape).background(accent.copy(alpha = 0.35f))
                        .semantics { contentDescription = "Publish, coming in the next update" },
                    contentAlignment = Alignment.Center,
                ) { Text("Publish · soon", style = MaterialTheme.typography.labelLarge, color = onAccent.copy(alpha = 0.8f)) }
            }
        }
    }
}
