package com.theveloper.pixelplay.presentation.components.tabs

import com.theveloper.pixelplay.data.songsterr.BeatSlot
import com.theveloper.pixelplay.data.songsterr.RenderedMeasure
import com.theveloper.pixelplay.data.songsterr.RenderedTrack
import com.theveloper.pixelplay.data.songsterr.TabParser
import com.theveloper.pixelplay.data.songsterr.TabSection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One beat column placed in a system. [x] is where its notes are centred. */
class PlacedSlot(val slot: BeatSlot, val x: Float)

/** A bar placed in a system. */
class PlacedMeasure(
    val measure: RenderedMeasure,
    val x: Float,
    val width: Float,
    val slots: List<PlacedSlot>,
    /** Opens a folded riff (draws a start-repeat sign). */
    val foldStart: Boolean,
    /** Closes a folded riff played [repeat] times. */
    val foldEnd: Boolean,
    val repeat: Int,
    /** Several identical empty bars drawn as one multi-bar rest. */
    val multiRest: Int,
    /** Every real bar this drawn bar stands for (folded riffs stand for several). */
    val realMeasures: List<Int>,
    val showSignature: Boolean,
    /** Where note spacing ends (before the closing bar line). */
    val contentRight: Float,
    /** First real bar of the folded riff this bar belongs to. */
    val segmentStart: Int = measure.index,
    /** Bars in one pass of that riff. */
    val segmentLength: Int = 1,
) {
    val right: Float get() = x + width

    /** Which pass of its folded riff real bar [real] is (0-based), or null if it isn't in it. */
    fun passOf(real: Int): Int? {
        if (repeat <= 1 || segmentLength <= 0) return null
        val k = real - segmentStart
        if (k < 0 || k >= segmentLength * repeat) return null
        return k / segmentLength
    }

    /**
     * x of a moment [tick] ticks into the bar. A multi-bar rest is swept once over all its
     * bars, so pass the real bar being played as [real].
     */
    fun xAt(tick: Long, real: Int? = null): Float {
        val frac = tick.toFloat() / measure.lengthTicks.coerceAtLeast(1)
        if (multiRest > 1) {
            val k = (real?.let { realMeasures.indexOf(it) } ?: 0).coerceAtLeast(0)
            val left = x + (contentRight - x) * 0.08f
            val right = contentRight.coerceAtLeast(left + 1f)
            return left + (right - left) * ((k + frac) / multiRest)
        }
        if (slots.isEmpty()) return x + width * frac
        var i = slots.indexOfLast { it.slot.onsetTicks <= tick }
        if (i < 0) i = 0
        val a = slots[i]
        val nextOnset = slots.getOrNull(i + 1)?.slot?.onsetTicks ?: measure.lengthTicks
        val nextX = slots.getOrNull(i + 1)?.x ?: contentRight
        val span = (nextOnset - a.slot.onsetTicks).coerceAtLeast(1)
        val f = ((tick - a.slot.onsetTicks).toFloat() / span).coerceIn(0f, 1f)
        return a.x + (nextX - a.x) * f
    }
}

/** A line of music, or a collapsed "same as …" section. */
class ScoreSystem(
    val index: Int,
    val sectionIndex: Int,
    val measures: List<PlacedMeasure>,
    /** Section name shown above this line (first line of a section only). */
    val sectionLabel: String?,
    /** Set for a collapsed section: the text to show instead of music. */
    val collapsedText: String? = null,
    val height: Float,
    // Vertical layout (px from the top of the system).
    val sectionTop: Float = 0f,
    val numberBaseline: Float = 0f,
    val endingTop: Float = 0f,
    val textBaseline: Float = 0f,
    val chordBaseline: Float = 0f,
    val bendTop: Float = 0f,
    val marksCenter: Float = 0f,
    val staffTop: Float = 0f,
    val staffBottom: Float = 0f,
    /** Distance between staff lines. */
    val gap: Float = 0f,
    val pmCenter: Float = 0f,
    val stemTop: Float = 0f,
    val stemBottom: Float = 0f,
    val tupletCenter: Float = 0f,
    val dynamicsBaseline: Float = 0f,
    val isLastSystem: Boolean = false,
) {
    val isCollapsed: Boolean get() = collapsedText != null
    val left: Float get() = measures.firstOrNull()?.x ?: 0f
    val right: Float get() = measures.lastOrNull()?.right ?: 0f
}

class ScoreLayout(
    val systems: List<ScoreSystem>,
    /** Real bar index → (system, bar in that system). */
    val placement: Map<Int, Pair<Int, Int>>,
    val width: Float,
)

/**
 * Lays a whole part out as wrapped lines across the given width (like Songsterr):
 * each section starts a new line, repeated riffs are drawn once between repeat signs with
 * "×N", repeated rests become one multi-bar rest, and a section identical to an earlier one
 * collapses to one line unless it's in [expanded].
 */
object ScoreLayoutEngine {

    fun layout(
        track: RenderedTrack,
        sections: List<TabSection>,
        widthPx: Float,
        m: ScoreMetrics,
        painter: ScorePainter,
        expanded: Set<Int> = emptySet(),
    ): ScoreLayout {
        val d = m.dp
        val isDrums = track.isDrums
        val margin = 10 * d
        val avail = widthPx - margin * 2
        val systems = ArrayList<ScoreSystem>()
        val placement = HashMap<Int, Pair<Int, Int>>()
        val fretSize = 12.5f * m.sp

        // ── Horizontal: natural widths for one drawn bar ────────────────────
        val quarter = TabParser.TICKS_PER_QUARTER.toFloat()
        val base = if (isDrums) 26 * d else 30 * d
        val minSlot = if (isDrums) 15 * d else 18 * d
        val maxSlot = if (isDrums) 58 * d else 62 * d

        class Draft(
            val measure: RenderedMeasure,
            val foldStart: Boolean,
            val foldEnd: Boolean,
            val repeat: Int,
            val multiRest: Int,
            val real: List<Int>,
            val showSig: Boolean,
            val segStart: Int = measure.index,
            val segLen: Int = 1,
        ) {
            var prefix = 0f
            var suffix = 0f
            val slotW = ArrayList<Float>()
            val slotLead = ArrayList<Float>()
            val natural: Float get() = prefix + slotW.sum() + suffix
        }

        fun measureDraft(dr: Draft) {
            val mm = dr.measure
            dr.prefix = 8 * d + (if (dr.showSig) 22 * d else 0f) + (if (dr.foldStart || mm.repeatStart) 10 * d else 0f)
            dr.suffix = 6 * d + (if (dr.foldEnd || mm.repeatCount != null) 10 * d else 0f)
            if (dr.multiRest > 1 || mm.isEmpty) {
                dr.slotW += if (dr.multiRest > 1) 92 * d else 56 * d
                dr.slotLead += 0f
                return
            }
            for (slot in mm.slots) {
                val lead = slot.graces.size * 10 * d
                var label = if (isDrums) 11 * d else 0f
                var extra = 0f
                for (b in slot.beats) {
                    if (b.dots > 0) extra = max(extra, (4 + 3 * b.dots) * d)
                    for (n in b.notes) {
                        if (isDrums) {
                            if (n.isGhost) label = max(label, 20 * d)
                        } else {
                            label = max(label, painter.measure(n.label, fretSize, ScorePainter.Font.BOLD))
                            if (n.slideBefore != null) extra += 6 * d
                            if (n.slideAfter != null || n.hpLabel != null) extra += 6 * d
                        }
                    }
                }
                if (label == 0f) label = 9 * d
                val byTime = (base * sqrt(slot.spanTicks / quarter)).coerceIn(minSlot, maxSlot)
                dr.slotW += max(byTime, lead + label + extra + 7 * d)
                dr.slotLead += lead + label / 2f + 3 * d
            }
            if (dr.slotW.isEmpty()) {
                dr.slotW += 40 * d
                dr.slotLead += 0f
            }
        }

        // ── Flow: sections → drafts ─────────────────────────────────────────
        val songLast = track.measures.lastIndex
        sections.forEach { section ->
            val ref = section.sameAs?.let { sections.getOrNull(it) }
            if (ref != null && section.index !in expanded) {
                // Collapsed: its bars point at the same spot in the section it repeats.
                val sysIndex = systems.size
                systems += ScoreSystem(
                    index = sysIndex,
                    sectionIndex = section.index,
                    measures = emptyList(),
                    sectionLabel = section.name,
                    collapsedText = "Same as ${ref.name} (${ref.barRange.lowercase()})",
                    height = 52 * d,
                )
                for (off in 0 until section.barCount) {
                    val refReal = ref.startMeasure + off
                    placement[refReal]?.let { placement[section.startMeasure + off] = it }
                }
                return@forEach
            }

            val drafts = ArrayList<Draft>()
            for (seg in section.segments) {
                val len = seg.measures.size
                val emptyRun = seg.repeat > 1 && len == 1 && seg.measures[0].isEmpty
                if (emptyRun) {
                    val mm = seg.measures[0]
                    drafts += Draft(mm, false, false, 1, seg.repeat, (0 until seg.repeat).map { mm.index + it }, mm.showSignature)
                    continue
                }
                seg.measures.forEachIndexed { j, mm ->
                    val real = (0 until seg.repeat).map { t -> mm.index + t * len }
                    drafts += Draft(
                        measure = mm,
                        foldStart = seg.repeat > 1 && j == 0,
                        foldEnd = seg.repeat > 1 && j == len - 1,
                        repeat = seg.repeat,
                        multiRest = 0,
                        real = real,
                        showSig = mm.showSignature,
                        segStart = seg.measures[0].index,
                        segLen = len,
                    )
                }
            }
            drafts.forEach(::measureDraft)

            // ── Line breaking (greedy) ─────────────────────────────────────
            var start = 0
            var first = true
            while (start < drafts.size) {
                var end = start
                var w = 0f
                while (end < drafts.size) {
                    val nw = drafts[end].natural
                    if (end > start && w + nw > avail) break
                    w += nw
                    end++
                }
                val line = drafts.subList(start, end)
                val lastOfSection = end >= drafts.size
                val natural = line.sumOf { it.natural.toDouble() }.toFloat()
                val stretch = when {
                    natural >= avail -> avail / natural // one very wide bar: squeeze
                    !lastOfSection || natural > avail * 0.72f -> 1f + (avail - natural) / line.sumOf { it.slotW.sum().toDouble() }.toFloat().coerceAtLeast(1f)
                    else -> 1f
                }
                val sysIndex = systems.size
                val placed = ArrayList<PlacedMeasure>()
                var x = margin
                for (dr in line) {
                    val slotScale = if (natural >= avail) stretch else stretch
                    val slots = ArrayList<PlacedSlot>()
                    var sx = x + dr.prefix * (if (natural >= avail) stretch else 1f)
                    val mm = dr.measure
                    if (dr.multiRest <= 1 && !mm.isEmpty) {
                        mm.slots.forEachIndexed { i, slot ->
                            val sw = dr.slotW[i] * slotScale
                            val lead = dr.slotLead[i] * min(1f, slotScale)
                            slots += PlacedSlot(slot, sx + lead)
                            sx += sw
                        }
                    } else {
                        sx += dr.slotW.sum() * slotScale
                    }
                    val contentRight = sx
                    val width = (sx - x) + dr.suffix * (if (natural >= avail) stretch else 1f)
                    val pm = PlacedMeasure(
                        measure = mm,
                        x = x,
                        width = max(width, 30 * d),
                        slots = slots,
                        foldStart = dr.foldStart,
                        foldEnd = dr.foldEnd,
                        repeat = dr.repeat,
                        multiRest = dr.multiRest,
                        realMeasures = dr.real,
                        showSignature = dr.showSig,
                        contentRight = contentRight,
                        segmentStart = dr.segStart,
                        segmentLength = dr.segLen,
                    )
                    dr.real.forEach { r -> placement.putIfAbsent(r, sysIndex to placed.size) }
                    placed += pm
                    x += pm.width
                }
                systems += verticalLayout(
                    index = sysIndex,
                    sectionIndex = section.index,
                    placed = placed,
                    label = if (first) section.name else null,
                    track = track,
                    m = m,
                    isLast = placed.any { it.measure.index == songLast },
                )
                first = false
                start = end
            }
        }
        return ScoreLayout(systems, placement, widthPx)
    }

    private fun verticalLayout(
        index: Int,
        sectionIndex: Int,
        placed: List<PlacedMeasure>,
        label: String?,
        track: RenderedTrack,
        m: ScoreMetrics,
        isLast: Boolean,
    ): ScoreSystem {
        val d = m.dp
        val isDrums = track.isDrums
        val beats = placed.flatMap { pm -> pm.slots.flatMap { it.slot.beats + it.slot.graces } }
        val notes = beats.flatMap { it.notes }
        val hasTempo = placed.any { it.measure.tempoBpm != null } || (label != null && placed.firstOrNull()?.measure?.index == 0)

        var y = 4 * d
        val sectionTop = y
        if (label != null || hasTempo) y += 24 * d
        val numberBaseline = y + 10 * d
        y += 13 * d
        val endingTop = y
        if (placed.any { it.measure.alternateEnding.isNotEmpty() }) y += 13 * d
        val textBaseline = y + 10 * d
        if (beats.any { it.text != null }) y += 13 * d
        val chordBaseline = y + 11 * d
        if (beats.any { it.chordText != null }) y += 14 * d
        val bendTop = y
        if (!isDrums && notes.any { it.bendLabel != null }) y += 22 * d
        val marksCenter = y + 6 * d
        val hasMarks = notes.any { it.accent > 0 || it.harmonicLabel != null || it.vibrato || it.wideVibrato || it.trill || (!isDrums && it.staccato) } ||
            beats.any { it.pickStroke != null || it.tapping || it.vibrato || it.wideVibrato || it.tremoloPicking || it.tremoloBar || it.upStroke || it.downStroke }
        if (hasMarks) y += 13 * d

        val gap = if (isDrums) 9 * d else 13 * d
        if (isDrums) {
            val minPos = notes.minOfOrNull { it.staffPos } ?: 0f
            if (minPos < 0f) y += (-minPos + 0.7f) * gap
        } else {
            y += 3 * d
        }
        val staffTop = y
        val staffBottom = if (isDrums) staffTop + 4 * gap else staffTop + (track.numStrings - 1).coerceAtLeast(1) * gap
        y = staffBottom
        if (isDrums) {
            val maxPos = notes.maxOfOrNull { it.staffPos } ?: 4f
            if (maxPos > 4f) y += (maxPos - 4f + 0.7f) * gap
        }
        y += 5 * d
        val pmCenter = y + 5 * d
        if (!isDrums && beats.any { it.palmMute || it.letRing }) y += 13 * d
        val stemTop = y
        val stemBottom = y + 22 * d
        y = stemBottom
        val tupletCenter = y + 7 * d
        if (beats.any { it.tuplet != null }) y += 14 * d
        val dynamicsBaseline = y + 13 * d
        if (beats.any { it.velocity != null }) y += 18 * d
        y += 10 * d

        return ScoreSystem(
            index = index,
            sectionIndex = sectionIndex,
            measures = placed,
            sectionLabel = label,
            height = y,
            sectionTop = sectionTop,
            numberBaseline = numberBaseline,
            endingTop = endingTop,
            textBaseline = textBaseline,
            chordBaseline = chordBaseline,
            bendTop = bendTop,
            marksCenter = marksCenter,
            staffTop = staffTop,
            staffBottom = staffBottom,
            gap = gap,
            pmCenter = pmCenter,
            stemTop = stemTop,
            stemBottom = stemBottom,
            tupletCenter = tupletCenter,
            dynamicsBaseline = dynamicsBaseline,
            isLastSystem = isLast,
        )
    }
}
