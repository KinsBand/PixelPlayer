package com.theveloper.pixelplay.presentation.components.tabs.edit

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.theveloper.pixelplay.data.songsterr.SongsterrJson
import com.theveloper.pixelplay.data.songsterr.TabParser
import com.theveloper.pixelplay.data.songsterr.edit.DrumEditOps
import com.theveloper.pixelplay.data.songsterr.edit.TabDraft
import com.theveloper.pixelplay.data.songsterr.edit.TabDraftStore
import com.theveloper.pixelplay.data.soundfont.SoundFontStore
import com.theveloper.pixelplay.presentation.components.tabs.TabPracticeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the next note goes: bar + ticks into it (first voice). */
data class EditCaret(val measure: Int, val tick: Long)

/** The palette shown in the tools' first row. */
enum class EditPalette(val label: String) {
    NOTES("Notes"), ACCENTS("Accents"), DYNAMICS("Dynamics"), BAR("Bar"), REPEATS("Repeats"), TEXT("Text")
}

/**
 * Edit mode for one part (drums for now). Holds the draft (Songsterr's raw JSON, so nothing is
 * lost), the caret, the input length, undo / redo, and autosaves the draft. Every change is
 * shown at once through [TabPracticeController.applyEditedTrack].
 */
@Stable
class TabEditController internal constructor(
    private val context: Context,
    val practice: TabPracticeController,
    private val scope: CoroutineScope,
    val songId: Long,
    val baseRevisionId: Long,
    val baseVersionLabel: String,
    val trackIndex: Int,
    val partName: String,
    private val baseRaw: JsonObject,
    private var raw: JsonObject,
    /** True when an earlier unpublished draft was restored. */
    val restored: Boolean,
) {
    private val staffPos: Map<Int, Double> = DrumEditOps.staffPositions(baseRaw)
    private val undoStack = ArrayDeque<JsonObject>()
    private val redoStack = ArrayDeque<JsonObject>()
    private var saveJob: Job? = null
    private var lastTap: Triple<Long, EditCaret, Long>? = null
    private val audition = DrumAudition()

    var caret by mutableStateOf(EditCaret(0, 0))
        private set
    /** Length of new notes, in ticks (quarter by default). */
    var inputType by mutableIntStateOf(4)
        private set
    var dotted by mutableStateOf(false)
        private set
    /** Taps stack on one beat (hand + foot) instead of moving on. */
    var chord by mutableStateOf(false)
    var palette by mutableStateOf(EditPalette.NOTES)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    /** Bars that differ from the version this copy was made from. */
    var changedBars by mutableStateOf<Set<Int>>(emptySet())
        private set
    /** Bumps on every change (the score and tools read it). */
    var version by mutableIntStateOf(0)
        private set
    var barCount by mutableIntStateOf(DrumEditOps.measures(raw).size())
        private set
    var message by mutableStateOf("")
    var reviewing by mutableStateOf(false)
    /** Kit pieces on the beat under the caret (pads light up). */
    var piecesAtCaret by mutableStateOf<Set<Int>>(emptySet())
        private set

    val inputTicks: Long get() = (DrumEditOps.WHOLE / inputType).let { if (dotted) it * 3 / 2 else it }
    val isDirty: Boolean get() = changedBars.isNotEmpty() || barCount != DrumEditOps.measures(baseRaw).size()

    init {
        refreshCaretInfo()
        if (restored) {
            // Show the restored draft straight away.
            recomputeChanges()
            val snapshot = raw.deepCopy()
            scope.launch {
                val track = withContext(Dispatchers.Default) { SongsterrJson.trackFrom(snapshot) }
                practice.applyEditedTrack(track)
            }
        }
    }

    // ── Caret ───────────────────────────────────────────────────────────────

    /** Tap on the tab: moves the caret (and drops the selection), or extends it in Select mode. */
    fun tapAt(measure: Int, tick: Long) {
        if (selectMode) extendSelection(measure, tick) else moveCaret(measure, tick)
    }

    fun moveCaret(measure: Int, tick: Long) {
        clearSelection()
        val m = measure.coerceIn(0, barCount - 1)
        val beat = DrumEditOps.beatCovering(raw, m, tick)
        caret = EditCaret(m, beat?.start ?: 0L)
        refreshCaretInfo()
    }

    fun stepForward() {
        val c = caret
        val beat = DrumEditOps.beatAt(raw, c.measure, c.tick)
        val next = c.tick + (beat?.ticks ?: inputTicks)
        caret = if (next >= DrumEditOps.barTicks(raw, c.measure)) {
            if (c.measure + 1 < barCount) EditCaret(c.measure + 1, 0) else c
        } else EditCaret(c.measure, next)
        refreshCaretInfo()
    }

    fun stepBack() {
        val c = caret
        val meas = DrumEditOps.measure(raw, c.measure) ?: return
        val prev = DrumEditOps.placed(meas).lastOrNull { it.start < c.tick }
        caret = when {
            prev != null -> EditCaret(c.measure, prev.start)
            c.measure > 0 -> {
                val pm = DrumEditOps.measure(raw, c.measure - 1)
                EditCaret(c.measure - 1, pm?.let { DrumEditOps.placed(it).lastOrNull()?.start } ?: 0L)
            }
            else -> c
        }
        refreshCaretInfo()
    }

    // ── Selection ───────────────────────────────────────────────────────────

    private var selAnchor by mutableStateOf<EditCaret?>(null)
    private var selHead by mutableStateOf<EditCaret?>(null)
    /** Taps extend the selection instead of moving the caret. */
    var selectMode by mutableStateOf(false)
    val hasSelection: Boolean get() = selAnchor != null && selHead != null

    /** First and last selected beats (beat starts), in order. */
    val selection: Pair<EditCaret, EditCaret>?
        get() {
            val a = selAnchor ?: return null
            val h = selHead ?: return null
            return if (a.measure < h.measure || (a.measure == h.measure && a.tick <= h.tick)) a to h else h to a
        }

    private fun snap(measure: Int, tick: Long): EditCaret {
        val m = measure.coerceIn(0, barCount - 1)
        return EditCaret(m, DrumEditOps.beatCovering(raw, m, tick)?.start ?: 0L)
    }

    private fun EditCaret.pos() = DrumEditOps.Pos(measure, tick)

    /** Where the selection's last beat ends (ticks into its bar), for drawing. */
    fun selectionEndTick(): Long {
        val last = selection?.second ?: return 0L
        val b = DrumEditOps.beatCovering(raw, last.measure, last.tick)
        return (b?.start ?: last.tick) + (b?.ticks ?: inputTicks)
    }

    fun selectionCount(): Int = selection?.let { (a, b) -> DrumEditOps.beatsBetween(raw, a.pos(), b.pos()).size } ?: 0

    /** Long-press on the tab: starts a selection on that beat. */
    fun startSelection(measure: Int, tick: Long) {
        val p = snap(measure, tick)
        selAnchor = p
        selHead = p
        caret = p
        refreshCaretInfo()
    }

    /** Drag / Select-mode tap: moves the selection's free end to that beat. */
    fun extendSelection(measure: Int, tick: Long) {
        if (selAnchor == null) { startSelection(measure, tick); return }
        val p = snap(measure, tick)
        if (p != selHead) selHead = p
    }

    fun clearSelection() {
        selAnchor = null
        selHead = null
    }

    /** Selects every beat of the caret's bar. */
    fun selectBar() {
        val m = caret.measure
        val meas = DrumEditOps.measure(raw, m) ?: return
        selAnchor = EditCaret(m, 0)
        selHead = EditCaret(m, DrumEditOps.placed(meas).lastOrNull()?.start ?: 0L)
    }

    /** Copies the selection (or the beat at the caret); returns how many beats. */
    fun copySelection(): Int {
        val (a, b) = selection ?: (caret to caret)
        val beats = DrumEditOps.copyRange(raw, a.pos(), b.pos())
        if (beats.isNotEmpty()) clipboard = beats
        return beats.size
    }

    fun cutSelection() {
        val (a, b) = selection ?: return
        copySelection()
        edit { DrumEditOps.clearRange(it, a.pos(), b.pos()) }
        clearSelection()
        moveCaretKeep(a)
    }

    fun deleteSelection() {
        val (a, b) = selection ?: return
        edit { DrumEditOps.clearRange(it, a.pos(), b.pos()) }
        clearSelection()
        moveCaretKeep(a)
    }

    /** Pastes the copied beats at the selection's start (or the caret), writing over what's there. */
    fun paste() {
        val clip = clipboard
        if (clip.isEmpty()) return
        val at = selection?.first ?: caret
        var end = DrumEditOps.Pos(at.measure, at.tick)
        edit { end = DrumEditOps.pasteAt(it, at.pos(), clip) }
        clearSelection()
        moveCaretKeep(EditCaret(end.measure, end.tick))
    }

    private fun moveCaretKeep(c: EditCaret) {
        caret = snap(c.measure, c.tick)
        refreshCaretInfo()
    }

    private fun refreshCaretInfo() {
        piecesAtCaret = DrumEditOps.piecesAt(raw, caret.measure, caret.tick)
    }

    // ── Entry ───────────────────────────────────────────────────────────────

    /** A pad was tapped: toggles that kit piece at the caret, sounds it, and moves on. */
    fun tapPad(art: Int) {
        val now = SystemClock.uptimeMillis()
        val quick = lastTap?.takeIf { now - it.first < CHORD_WINDOW_MS }
        // A second pad within the chord window lands on the same beat as the first.
        val at = if (!chord && quick != null) quick.second else caret
        var added = false
        edit {
            added = DrumEditOps.toggleNote(it, at.measure, at.tick, inputTicks, art, staffPosFor(art))
        }
        if (added) sound(at)
        if (!chord && added && quick == null) {
            lastTap = Triple(now, at, 0L)
            caret = at
            stepForwardOrAppend()
        } else {
            if (quick != null) lastTap = null
            refreshCaretInfo()
        }
    }

    private fun stepForwardOrAppend() {
        val c = caret
        val beat = DrumEditOps.beatAt(raw, c.measure, c.tick)
        val next = c.tick + (beat?.ticks ?: inputTicks)
        if (next >= DrumEditOps.barTicks(raw, c.measure) && c.measure == barCount - 1) {
            // Writing past the last bar adds one.
            edit { DrumEditOps.insertBar(it, barCount) }
        }
        stepForward()
    }

    /** Puts a rest of the input length at the caret and moves on. */
    fun addRest() {
        val c = caret
        edit {
            DrumEditOps.clearAt(it, c.measure, c.tick)
            DrumEditOps.setLengthAt(it, c.measure, c.tick, inputTicks)
        }
        stepForwardOrAppend()
    }

    /** Clears the beat at the caret; on a rest it steps back instead. */
    fun deleteAtCaret() {
        if (hasSelection) { deleteSelection(); return }
        val c = caret
        var cleared = false
        edit { cleared = DrumEditOps.clearAt(it, c.measure, c.tick) }
        if (!cleared) stepBack() else refreshCaretInfo()
    }

    /** New input length; a note under the caret takes it too. */
    fun chooseLength(type: Int) {
        inputType = type
        applyLengthToCaret()
    }

    fun toggleDot() {
        dotted = !dotted
        applyLengthToCaret()
    }

    private fun applyLengthToCaret() {
        val c = caret
        val b = DrumEditOps.beatAt(raw, c.measure, c.tick) ?: return
        if (DrumEditOps.isRest(b.beat) || b.ticks == inputTicks) return
        edit { DrumEditOps.setLengthAt(it, c.measure, c.tick, inputTicks) }
    }

    fun toggleFlag(flag: DrumEditOps.NoteFlag) {
        val c = caret
        edit { DrumEditOps.toggleFlag(it, c.measure, c.tick, flag) }
    }

    fun toggleVelocity(v: String) {
        val c = caret
        edit { DrumEditOps.toggleVelocity(it, c.measure, c.tick, v) }
    }

    fun setBeatText(text: String?) {
        val c = caret
        edit { DrumEditOps.setBeatText(it, c.measure, c.tick, text) }
    }

    // ── Bars ────────────────────────────────────────────────────────────────

    fun insertBarBefore() = edit { DrumEditOps.insertBar(it, caret.measure) }.also { moveCaret(caret.measure, 0) }
    fun insertBarAfter() { edit { DrumEditOps.insertBar(it, caret.measure + 1) }; moveCaret(caret.measure + 1, 0) }
    fun duplicateBar() { edit { DrumEditOps.duplicateBar(it, caret.measure) }; moveCaret(caret.measure + 1, 0) }
    fun deleteBar() {
        clearSelection()
        val m = caret.measure
        edit { DrumEditOps.deleteBar(it, m) }
        moveCaret(m.coerceAtMost(barCount - 1), 0)
    }
    fun setSignature(num: Int, den: Int) = edit { DrumEditOps.setSignature(it, caret.measure, num, den) }
    fun signature(): Pair<Int, Int> = DrumEditOps.signatureAt(raw, caret.measure)
    fun toggleRepeatStart() = edit { DrumEditOps.toggleRepeatStart(it, caret.measure) }
    fun setRepeatEnd(times: Int?) = edit { DrumEditOps.setRepeatEnd(it, caret.measure, times) }
    fun setMarker(text: String?) = edit { DrumEditOps.setMarker(it, caret.measure, text) }
    fun toggleDoubleBar() = edit { DrumEditOps.toggleDoubleBar(it, caret.measure) }

    /** Written / expected length of the caret's bar (the fill meter). */
    fun caretBarFill(): Pair<Long, Long> = DrumEditOps.fill(raw, caret.measure)

    // ── Undo / redo ─────────────────────────────────────────────────────────

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        clearSelection()
        redoStack.addLast(raw)
        raw = prev
        afterChange()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        clearSelection()
        undoStack.addLast(raw)
        raw = next
        afterChange()
    }

    private inline fun edit(op: (JsonObject) -> Unit) {
        val before = raw.deepCopy()
        op(raw)
        if (before == raw) return
        undoStack.addLast(before)
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        afterChange()
    }

    private fun afterChange() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        barCount = DrumEditOps.measures(raw).size()
        if (caret.measure >= barCount) caret = EditCaret(barCount - 1, 0)
        version++
        refreshCaretInfo()
        val snapshot = raw.deepCopy()
        scope.launch {
            val track = withContext(Dispatchers.Default) { SongsterrJson.trackFrom(snapshot) }
            practice.applyEditedTrack(track)
        }
        recomputeChanges()
        scheduleSave()
    }

    private fun recomputeChanges() {
        val snapshot = raw.deepCopy()
        scope.launch {
            changedBars = withContext(Dispatchers.Default) {
                val a = DrumEditOps.measures(baseRaw)
                val b = DrumEditOps.measures(snapshot)
                (0 until b.size()).filter { i -> i >= a.size() || a[i] != b[i] }.toSet()
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        val snapshot = raw.deepCopy()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            TabDraftStore.save(context, TabDraft(songId, baseRevisionId, trackIndex, snapshot, message = message))
        }
    }

    // ── Review numbers ──────────────────────────────────────────────────────

    data class Summary(val barsChanged: Int, val notesAdded: Int, val notesRemoved: Int, val barsBefore: Int, val barsAfter: Int)

    fun summary(): Summary {
        val a = DrumEditOps.measures(baseRaw)
        val b = DrumEditOps.measures(raw)
        fun count(arr: com.google.gson.JsonArray, i: Int): Map<String, Int> {
            if (i !in 0 until arr.size()) return emptyMap()
            val out = HashMap<String, Int>()
            DrumEditOps.placed(arr[i].asJsonObject).forEach { p ->
                if (DrumEditOps.isRest(p.beat)) return@forEach
                p.beat.getAsJsonArray("notes")?.forEach { n ->
                    val f = n.asJsonObject.get("fret")?.asInt ?: return@forEach
                    val key = "${p.start}:$f"
                    out[key] = (out[key] ?: 0) + 1
                }
            }
            return out
        }
        var added = 0
        var removed = 0
        for (i in changedBars) {
            val before = count(a, i)
            val after = count(b, i)
            added += after.count { (k, v) -> v > (before[k] ?: 0) }
            removed += before.count { (k, v) -> v > (after[k] ?: 0) }
        }
        return Summary(changedBars.size, added, removed, a.size(), b.size())
    }

    // ── Sound ───────────────────────────────────────────────────────────────

    private fun staffPosFor(art: Int): Double = staffPos[art] ?: TabParser.drumArticulation(art).staffPos.toDouble()

    /** Plays the beat at [at] (all its kit pieces) on its own. */
    private fun sound(at: EditCaret) {
        val pieces = DrumEditOps.piecesAt(raw, at.measure, at.tick)
        if (pieces.isEmpty()) return
        val font = if (practice.sampledSounds) SoundFontStore.fontOrNull() else null
        audition.playPieces(pieces.toList(), font, ::staffPosFor)
    }

    /** Auditions one kit piece without writing it (long-press preview). */
    fun preview(art: Int) {
        val font = if (practice.sampledSounds) SoundFontStore.fontOrNull() else null
        audition.playPieces(listOf(art), font, ::staffPosFor)
    }

    // ── Leaving ─────────────────────────────────────────────────────────────

    /** Leaves edit mode. [keepDraft] = false throws the draft away. The tab goes back to the version it was. */
    fun exit(keepDraft: Boolean) {
        saveJob?.cancel()
        audition.release()
        val snapshot = raw.deepCopy()
        scope.launch {
            if (keepDraft && isDirty) {
                TabDraftStore.save(context, TabDraft(songId, baseRevisionId, trackIndex, snapshot, message = message))
            } else {
                TabDraftStore.delete(context, songId, baseRevisionId, trackIndex)
            }
            practice.endEditing(SongsterrJson.trackFrom(baseRaw))
        }
    }

    /** Saves the draft now on its own scope (the page is going away) and stops the sounds. */
    fun saveNowAndForget() {
        saveJob?.cancel()
        audition.release()
        if (!isDirty) return
        val snapshot = raw.deepCopy()
        val msg = message
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            TabDraftStore.save(context, TabDraft(songId, baseRevisionId, trackIndex, snapshot, message = msg))
        }
    }

    /** The draft's part JSON, for publishing. */
    fun draftJson(): String = raw.toString()

    companion object {
        /** Copied beats; shared by every song, so a groove can be copied from one tab into another. */
        var clipboard by mutableStateOf<List<JsonObject>>(emptyList())
            private set
        private const val MAX_UNDO = 200
        private const val SAVE_DELAY_MS = 1000L
        private const val CHORD_WINDOW_MS = 250L

        /**
         * Starts editing the part on screen: restores a waiting draft of it, or copies the
         * version on screen. Returns null when the part's JSON can't be read.
         */
        suspend fun start(context: Context, practice: TabPracticeController, scope: CoroutineScope): TabEditController? {
            val tab = practice.loaded ?: return null
            val json = practice.rawPartJson() ?: return null
            val base = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
            val songId = tab.meta.songId
            val rev = tab.meta.revisionId
            val draft = TabDraftStore.load(context, songId, rev, tab.trackIndex)
            val versions = practice.revisions
            val number = versions?.let { list ->
                val i = list.indexOfFirst { it.revisionId == rev }
                if (i >= 0) list.size - i else null
            }
            val partName = tab.metaTrack?.let { com.theveloper.pixelplay.presentation.components.tabs.partLabel(it).second } ?: "Part"
            return TabEditController(
                context = context.applicationContext,
                practice = practice,
                scope = scope,
                songId = songId,
                baseRevisionId = rev,
                baseVersionLabel = number?.let { "v$it" } ?: "this version",
                trackIndex = tab.trackIndex,
                partName = partName,
                baseRaw = base,
                raw = draft?.part ?: base.deepCopy(),
                restored = draft != null,
            ).also { ed -> draft?.message?.let { ed.message = it } }
        }
    }
}
