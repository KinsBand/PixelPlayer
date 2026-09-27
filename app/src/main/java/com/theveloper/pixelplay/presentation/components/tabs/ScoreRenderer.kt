package com.theveloper.pixelplay.presentation.components.tabs

import com.theveloper.pixelplay.data.songsterr.RenderedBeat
import com.theveloper.pixelplay.data.songsterr.RenderedNote
import com.theveloper.pixelplay.data.songsterr.RenderedTrack
import com.theveloper.pixelplay.data.songsterr.TabParser
import com.theveloper.pixelplay.presentation.components.tabs.ScorePainter.Align
import com.theveloper.pixelplay.presentation.components.tabs.ScorePainter.Font
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws one [ScoreSystem]: a 5-line percussion staff for drums, or tablature for guitar and
 * bass, with rhythm (stems, beams, flags, dots, tuplets), rests, bar lines, repeat signs,
 * time signatures, dynamics and playing techniques. Coordinates are relative to the system.
 */
/**
 * Where playback is, so repeat counts and multi-bar rests can count down live.
 * [tabRepeatPass] gives the passes already played of the tab's own repeat closing at a bar
 * (null when playback isn't inside it).
 */
class LiveCursor(
    val measure: Int,
    val tabRepeatPass: (closing: Int) -> Int? = { null },
)

/**
 * Colours from an electronic drum kit, per drawn bar: [colorOf] gives a note's colour (null =
 * normal ink) and [strays] the hits that weren't tab notes (drawn as small crosses).
 */
interface DrumMarks {
    fun colorOf(pm: PlacedMeasure, tick: Long, articulation: Int): Int?
    fun strays(pm: PlacedMeasure): List<Stray>

    class Stray(val tick: Long, val staffPos: Float, val color: Int)
}

class ScoreRenderer(
    private val track: RenderedTrack,
    private val m: ScoreMetrics,
    private val c: ScoreColors,
) {
    private val d = m.dp
    private val sp = m.sp
    private val isDrums = track.isDrums
    private val lastMeasure = track.measures.lastIndex

    fun draw(p: ScorePainter, s: ScoreSystem, live: LiveCursor? = null, marks: DrumMarks? = null) {
        if (s.isCollapsed || s.measures.isEmpty()) return
        drawHeader(p, s)
        drawStaff(p, s)
        for ((i, pm) in s.measures.withIndex()) drawMeasureFrame(p, s, pm, i, live)
        for (pm in s.measures) {
            if (pm.multiRest > 1 || pm.measure.isEmpty) {
                drawBarRest(p, s, pm, live)
                continue
            }
            if (isDrums) drawDrumNotes(p, s, pm, marks) else drawTabNotes(p, s, pm)
            drawRests(p, s, pm)
            drawRhythm(p, s, pm)
            drawMarks(p, s, pm)
        }
        if (!isDrums) {
            drawConnections(p, s)
            drawRuns(p, s, "P.M.") { it.palmMute }
            drawRuns(p, s, "let ring") { it.letRing }
        }
    }

    // ── Header: section name, tempo ─────────────────────────────────────────

    private fun drawHeader(p: ScorePainter, s: ScoreSystem) {
        val baseline = s.sectionTop + 17 * d
        var x = s.left
        s.sectionLabel?.let {
            p.text(it, x, baseline, 16 * sp, c.ink, Align.LEFT, Font.SERIF_ITALIC)
            x += p.measure(it, 16 * sp, Font.SERIF_ITALIC) + 14 * d
        }
        for (pm in s.measures) {
            val bpm = pm.measure.tempoBpm ?: if (pm.measure.index == 0) track.bpm.toDouble() else null
            if (bpm != null) {
                val tx = max(x, pm.x + 4 * d)
                p.text("♩ = ${Math.round(bpm)}", tx, baseline, 12 * sp, c.faint, Align.LEFT, Font.REGULAR)
                x = tx + p.measure("♩ = 000", 12 * sp) + 8 * d
            }
        }
    }

    // ── Staff lines ─────────────────────────────────────────────────────────

    private fun lineY(s: ScoreSystem, i: Int) = s.staffTop + i * s.gap

    private fun lineCount() = if (isDrums) 5 else track.numStrings.coerceAtLeast(1)

    private fun drawStaff(p: ScorePainter, s: ScoreSystem) {
        val w = 0.9f * d
        if (isDrums) {
            for (i in 0 until 5) p.line(s.left, lineY(s, i), s.right, lineY(s, i), w, c.faint)
            return
        }
        // Tab: break each string line around the fret numbers.
        val gaps = Array(lineCount()) { ArrayList<FloatArray>() }
        val size = 12.5f * sp
        for (pm in s.measures) for (ps in pm.slots) {
            for (b in ps.slot.beats) for (n in b.notes) {
                val half = p.measure(n.label, size, Font.BOLD) / 2f + 1.5f * d
                gaps.getOrNull(n.row)?.add(floatArrayOf(ps.x - half, ps.x + half))
            }
            ps.slot.graces.forEachIndexed { gi, g ->
                val gx = graceX(ps, gi)
                for (n in g.notes) {
                    val half = p.measure(n.label, 9 * sp, Font.BOLD) / 2f + 1f * d
                    gaps.getOrNull(n.row)?.add(floatArrayOf(gx - half, gx + half))
                }
            }
        }
        for (i in 0 until lineCount()) {
            val y = lineY(s, i)
            var x = s.left
            for (g in gaps[i].sortedBy { it[0] }) {
                if (g[0] > x) p.line(x, y, g[0], y, w, c.faint)
                x = max(x, g[1])
            }
            if (x < s.right) p.line(x, y, s.right, y, w, c.faint)
        }
    }

    // ── Bar lines, repeats, signatures, numbers, endings ───────────────────

    private fun repeatDots(p: ScorePainter, s: ScoreSystem, x: Float) {
        val mid = (s.staffTop + s.staffBottom) / 2f
        val off = if (isDrums) s.gap / 2f else s.gap / 2f
        p.circle(x, mid - off, 1.9f * d, c.ink, true)
        p.circle(x, mid + off, 1.9f * d, c.ink, true)
    }

    private fun drawMeasureFrame(p: ScorePainter, s: ScoreSystem, pm: PlacedMeasure, i: Int, live: LiveCursor?) {
        val top = s.staffTop
        val bottom = s.staffBottom
        val mm = pm.measure
        val x0 = pm.x
        val x1 = pm.right
        val thin = 1f * d
        val thick = 3.2f * d

        // Opening line.
        if (pm.foldStart || mm.repeatStart) {
            p.line(x0 + 1.6f * d, top, x0 + 1.6f * d, bottom, thick, c.ink)
            p.line(x0 + 5.2f * d, top, x0 + 5.2f * d, bottom, thin, c.ink)
            repeatDots(p, s, x0 + 8.8f * d)
        } else if (i == 0) {
            p.line(x0, top, x0, bottom, thin, c.faint)
        }

        // Closing line.
        val closesRepeat = pm.foldEnd || mm.repeatCount != null
        when {
            closesRepeat -> {
                repeatDots(p, s, x1 - 8.8f * d)
                p.line(x1 - 5.2f * d, top, x1 - 5.2f * d, bottom, thin, c.ink)
                p.line(x1 - 1.6f * d, top, x1 - 1.6f * d, bottom, thick, c.ink)
                val times = if (pm.foldEnd) pm.repeat else mm.repeatCount ?: 2
                // While playing inside the repeat, count the repeats left down to 0.
                val done = live?.let { if (pm.foldEnd) pm.passOf(it.measure) else it.tabRepeatPass(mm.index) }
                if (done != null) {
                    val left = (times - 1 - done).coerceAtLeast(0)
                    p.text("×$left", x1 - 1 * d, s.numberBaseline, 12 * sp, c.cursor, Align.RIGHT, Font.BOLD)
                } else {
                    p.text("×$times", x1 - 1 * d, s.numberBaseline, 11 * sp, c.accent, Align.RIGHT, Font.BOLD)
                }
            }
            mm.index == lastMeasure -> {
                p.line(x1 - 5 * d, top, x1 - 5 * d, bottom, thin, c.ink)
                p.line(x1 - 1.6f * d, top, x1 - 1.6f * d, bottom, thick, c.ink)
            }
            mm.doubleBar -> {
                p.line(x1 - 3.5f * d, top, x1 - 3.5f * d, bottom, thin, c.faint)
                p.line(x1, top, x1, bottom, thin, c.faint)
            }
            else -> p.line(x1, top, x1, bottom, thin, c.faint)
        }

        // Bar number.
        val number = when {
            pm.multiRest > 1 -> mm.number?.let { "$it–${it + pm.multiRest - 1}" }
            else -> mm.number?.toString()
        }
        number?.let { p.text(it, x0 + (if (pm.foldStart) 3 else 2) * d, s.numberBaseline, 9 * sp, c.faint) }

        // Time signature.
        if (pm.showSignature) {
            val sx = x0 + (if (pm.foldStart || mm.repeatStart) 10 * d else 0f) + 16 * d
            val half = (bottom - top) / 2f
            val size = if (isDrums) s.gap * 2.35f else half * 1.05f
            val upper = top + half / 2f
            val lower = top + half * 1.5f
            val num = mm.timeSignature[0].toString()
            val den = mm.timeSignature[1].toString()
            p.text(num, sx, upper + size * 0.36f, size, c.ink, Align.CENTER, Font.SERIF_BOLD)
            p.text(den, sx, lower + size * 0.36f, size, c.ink, Align.CENTER, Font.SERIF_BOLD)
        }

        // Alternate ending bracket.
        if (mm.alternateEnding.isNotEmpty()) {
            val y = s.endingTop + 3 * d
            p.line(x0 + 2 * d, y, x1 - 2 * d, y, thin, c.faint)
            p.line(x0 + 2 * d, y, x0 + 2 * d, y + 9 * d, thin, c.faint)
            p.text(mm.alternateEnding.joinToString(" ") { "$it." }, x0 + 5 * d, y + 10 * d, 9 * sp, c.ink)
        }
    }

    /** Whole-bar rest or a multi-bar rest. */
    private fun drawBarRest(p: ScorePainter, s: ScoreSystem, pm: PlacedMeasure, live: LiveCursor?) {
        val mid = (s.staffTop + s.staffBottom) / 2f
        val left = pm.x + (if (pm.showSignature) 30 * d else 10 * d)
        val right = pm.contentRight - 4 * d
        if (pm.multiRest > 1) {
            p.rect(left, mid - 3.2f * d, right, mid + 3.2f * d, c.ink)
            p.line(left, mid - 7 * d, left, mid + 7 * d, 1.2f * d, c.ink)
            p.line(right, mid - 7 * d, right, mid + 7 * d, 1.2f * d, c.ink)
            // While resting here, count the bars left down to 0.
            val k = live?.let { pm.realMeasures.indexOf(it.measure) } ?: -1
            if (k >= 0) {
                p.text((pm.multiRest - 1 - k).toString(), (left + right) / 2f, s.staffTop - 5 * d, 15 * sp, c.cursor, Align.CENTER, Font.SERIF_BOLD)
            } else {
                p.text(pm.multiRest.toString(), (left + right) / 2f, s.staffTop - 5 * d, 14 * sp, c.ink, Align.CENTER, Font.SERIF_BOLD)
            }
        } else {
            val cx = (left + right) / 2f
            val y = if (isDrums) lineY(s, 1) else mid - 2.5f * d
            p.rect(cx - 6 * d, y, cx + 6 * d, y + 3.5f * d, c.ink)
        }
    }

    // ── Drum notes ──────────────────────────────────────────────────────────

    private fun posY(s: ScoreSystem, pos: Float) = s.staffTop + pos * s.gap

    /** x of the stem for a notehead centred at [x] (stems hang down on the left). */
    private fun stemX(s: ScoreSystem, x: Float) = if (isDrums) x - s.gap * 0.55f else x

    private fun drumHeadX(s: ScoreSystem, beat: RenderedBeat, x: Float): Map<RenderedNote, Float> {
        // A second (half a space apart) can't share a column: the lower head moves left of the stem.
        val sorted = beat.notes.sortedBy { it.staffPos }
        val out = IdentityHashMap<RenderedNote, Float>()
        var prev: RenderedNote? = null
        var prevFlipped = false
        for (n in sorted) {
            val flip = prev != null && !prevFlipped && abs(n.staffPos - prev.staffPos) < 0.99f
            out[n] = if (flip) x - s.gap * 1.12f else x
            prev = n
            prevFlipped = flip
        }
        return out
    }

    private fun drawDrumNotes(p: ScorePainter, s: ScoreSystem, pm: PlacedMeasure, marks: DrumMarks?) {
        val u = s.gap
        for (ps in pm.slots) {
            // Grace notes (flams): small heads just before the beat, with a slashed stem.
            ps.slot.graces.forEachIndexed { gi, g ->
                val gx = graceX(ps, gi)
                for (n in g.notes) {
                    val y = posY(s, n.staffPos)
                    ledgers(p, s, gx, n.staffPos, u * 0.55f)
                    drumHead(p, n.drumGlyph, gx, y, u * 0.62f, c.ink)
                    p.line(gx - u * 0.35f, y, gx - u * 0.35f, y + u * 2.2f, 0.9f * d, c.ink)
                    p.line(gx - u * 0.8f, y + u * 1.6f, gx + u * 0.1f, y + u * 0.9f, 0.9f * d, c.ink)
                }
            }
            for (b in ps.slot.beats) {
                if (b.isRest) continue
                val xs = drumHeadX(s, b, ps.x)
                for (n in b.notes) {
                    val x = xs[n] ?: ps.x
                    val y = posY(s, n.staffPos)
                    val ink = marks?.colorOf(pm, ps.slot.onsetTicks, n.fret) ?: c.ink
                    ledgers(p, s, x, n.staffPos, u * 0.95f)
                    if (ink != c.ink && ink != c.faint) p.circle(x, y, u * 0.95f, (ink and 0x00FFFFFF) or 0x38000000, true)
                    drumHead(p, n.drumGlyph, x, y, u, ink)
                    if (n.isGhost) {
                        p.arc(x, y, u * 0.95f, 120f, 120f, ink, 1f * d)
                        p.arc(x, y, u * 0.95f, -60f, 120f, ink, 1f * d)
                    }
                    if (b.dots > 0) {
                        val dotY = if (abs(n.staffPos - floor(n.staffPos)) < 0.01f) y - u * 0.5f else y
                        for (k in 0 until b.dots) p.circle(x + u * (0.95f + 0.45f * k), dotY, 0.14f * u + 0.5f * d, c.ink, true)
                    }
                }
            }
        }
        // Hits that weren't tab notes: a small cross where they landed.
        marks?.strays(pm)?.forEach { st ->
            val x = pm.xAt(st.tick)
            val y = posY(s, st.staffPos)
            val r = u * 0.42f
            p.line(x - r, y - r, x + r, y + r, 1.6f * d, st.color)
            p.line(x - r, y + r, x + r, y - r, 1.6f * d, st.color)
        }
    }

    private fun ledgers(p: ScorePainter, s: ScoreSystem, x: Float, pos: Float, half: Float) {
        if (pos <= -1f) {
            var l = -1
            while (l >= ceil(pos).toInt() - 0) {
                if (l < pos - 0.01f) break
                p.line(x - half, posY(s, l.toFloat()), x + half, posY(s, l.toFloat()), 1f * d, c.ink)
                l--
            }
        } else if (pos >= 5f) {
            var l = 5
            while (l <= floor(pos).toInt()) {
                p.line(x - half, posY(s, l.toFloat()), x + half, posY(s, l.toFloat()), 1f * d, c.ink)
                l++
            }
        }
    }

    /** [u] = one staff space. */
    private fun drumHead(p: ScorePainter, glyph: TabParser.DrumGlyph, x: Float, y: Float, u: Float, color: Int) {
        val st = max(1f, 0.13f * u)
        val xh = u * 0.46f
        fun cross() {
            p.line(x - xh, y - xh, x + xh, y + xh, st, color)
            p.line(x + xh, y - xh, x - xh, y + xh, st, color)
        }
        when (glyph) {
            TabParser.DrumGlyph.HEAD -> p.ellipse(x, y, u * 0.62f, u * 0.44f, -20f, color, true)
            TabParser.DrumGlyph.X -> cross()
            TabParser.DrumGlyph.X_CIRCLE -> {
                cross()
                p.circle(x, y, u * 0.62f, color, false, st * 0.8f)
            }
            TabParser.DrumGlyph.SLASH_CIRCLE -> {
                p.circle(x, y, u * 0.55f, color, false, st * 0.8f)
                p.line(x - u * 0.42f, y + u * 0.42f, x + u * 0.42f, y - u * 0.42f, st * 0.8f, color)
            }
            TabParser.DrumGlyph.DIAMOND -> p.path(floatArrayOf(x, y - u * 0.55f, x + u * 0.55f, y, x, y + u * 0.55f, x - u * 0.55f, y), true, color, true)
            TabParser.DrumGlyph.DIAMOND_OPEN -> p.path(floatArrayOf(x, y - u * 0.55f, x + u * 0.55f, y, x, y + u * 0.55f, x - u * 0.55f, y), true, color, false, st * 0.8f)
            TabParser.DrumGlyph.X_HAT -> {
                cross()
                p.path(floatArrayOf(x - u * 0.45f, y - u * 0.7f, x, y - u * 1.05f, x + u * 0.45f, y - u * 0.7f), false, color, false, st * 0.8f)
            }
            TabParser.DrumGlyph.X_CHOKE -> {
                cross()
                p.circle(x + u * 0.8f, y + u * 0.55f, u * 0.13f, color, true)
            }
            TabParser.DrumGlyph.TRIANGLE -> p.path(floatArrayOf(x, y - u * 0.55f, x + u * 0.55f, y + u * 0.45f, x - u * 0.55f, y + u * 0.45f), true, color, true)
        }
    }

    // ── Tab notes ───────────────────────────────────────────────────────────

    private fun graceX(ps: PlacedSlot, i: Int): Float = ps.x - (ps.slot.graces.size - i) * 10 * d - 5 * d

    private fun drawTabNotes(p: ScorePainter, s: ScoreSystem, pm: PlacedMeasure) {
        val size = 12.5f * sp
        for (ps in pm.slots) {
            ps.slot.graces.forEachIndexed { gi, g ->
                val gx = graceX(ps, gi)
                for (n in g.notes) p.text(n.label, gx, lineY(s, n.row) + 3.2f * sp, 9 * sp, c.ink, Align.CENTER, Font.BOLD)
            }
            for (b in ps.slot.beats) for (n in b.notes) {
                val y = lineY(s, n.row)
                val col = if (n.isGhost || n.isTie) c.faint else if (b.voice > 0) c.accent else c.ink
                p.text(n.label, ps.x, y + size * 0.36f, size, col, Align.CENTER, Font.BOLD)
                val half = p.measure(n.label, size, Font.BOLD) / 2f
                n.slideBefore?.let { slideMark(p, ps.x - half - 7 * d, y, it == "/") }
                if (n.slide == "upwards" || n.slide == "downwards") n.slideAfter?.let { slideMark(p, ps.x + half + 2 * d, y, it == "/") }
                n.bendLabel?.let { bend(p, s, ps.x + half + 1 * d, y - 4 * d, it, n.bendRelease, n.preBend) }
            }
        }
    }

    private fun slideMark(p: ScorePainter, x: Float, y: Float, up: Boolean) {
        val h = 3.5f * d
        if (up) p.line(x, y + h, x + 5 * d, y - h, 1.1f * d, c.ink) else p.line(x, y - h, x + 5 * d, y + h, 1.1f * d, c.ink)
    }

    private fun bend(p: ScorePainter, s: ScoreSystem, x: Float, fromY: Float, label: String, release: Boolean, preBend: Boolean) {
        val peakX = if (preBend) x else x + 10 * d
        val peakY = s.bendTop + 10 * d
        if (preBend) p.line(x, fromY, peakX, peakY, 1.1f * d, c.ink) else p.quad(x, fromY, peakX, fromY, peakX, peakY, c.ink, 1.1f * d)
        p.path(floatArrayOf(peakX - 3 * d, peakY + 4 * d, peakX, peakY, peakX + 3 * d, peakY + 4 * d), false, c.ink, false, 1.1f * d)
        p.text(label, peakX, s.bendTop + 7 * d, 9 * sp, c.ink, Align.CENTER, Font.BOLD)
        if (release) p.line(peakX + 2 * d, peakY, peakX + 10 * d, fromY, 1f * d, c.faint)
    }

    /** Hammer-on / pull-off arcs and legato slides to the next note on the same string. */
    private fun drawConnections(p: ScorePainter, s: ScoreSystem) {
        val size = 12.5f * sp
        val seqByVoice = HashMap<Int, MutableList<Pair<RenderedBeat, Float>>>()
        for (pm in s.measures) for (ps in pm.slots) for (b in ps.slot.beats) {
            if (!b.isRest) seqByVoice.getOrPut(b.voice) { ArrayList() } += b to ps.x
        }
        for (seq in seqByVoice.values) for (i in 0 until seq.size - 1) {
            val (beat, x0) = seq[i]
            val (next, x1) = seq[i + 1]
            for (n in beat.notes) {
                val legato = n.slide == "legato" || n.slide == "shift"
                val target = next.notes.firstOrNull { it.row == n.row }
                val y = lineY(s, n.row)
                val a = x0 + p.measure(n.label, size, Font.BOLD) / 2f + 1.5f * d
                if (target == null || x1 <= x0) {
                    // Next note is on another line: short marks instead.
                    n.hpLabel?.let { p.text(it, a + 3 * d, y - 5 * d, 9 * sp, c.accent, Align.CENTER, Font.BOLD) }
                    if (legato) n.slideAfter?.let { slideMark(p, a + 1 * d, y, it == "/") }
                    continue
                }
                val b = x1 - p.measure(target.label, size, Font.BOLD) / 2f - 1.5f * d
                if (b - a < 3 * d) continue
                if (legato) {
                    val rising = target.fret > n.fret
                    p.line(a, y + (if (rising) 3f else -3f) * d, b, y + (if (rising) -3f else 3f) * d, 1.1f * d, c.ink)
                }
                if (n.hpLabel != null || n.slide == "legato") {
                    val cy = y - 7 * d
                    p.quad(a, cy, (a + b) / 2f, cy - 7 * d, b, cy, c.ink, 1f * d)
                    n.hpLabel?.let { if (b - a > 9 * d) p.text(it.uppercase(), (a + b) / 2f, cy - 5.5f * d, 8 * sp, c.ink, Align.CENTER, Font.BOLD) }
                }
            }
        }
    }

    private fun drawRuns(p: ScorePainter, s: ScoreSystem, label: String, test: (RenderedBeat) -> Boolean) {
        val y = s.pmCenter
        var runStart: Float? = null
        var runEnd = 0f
        fun flush() {
            val st = runStart ?: return
            p.text(label, st - 4 * d, y + 3 * d, 9 * sp, c.ink, Align.LEFT, Font.ITALIC)
            val ls = st - 4 * d + p.measure(label, 9 * sp, Font.ITALIC) + 3 * d
            var x = ls
            while (x < runEnd - 3 * d) {
                p.line(x, y, min(x + 4 * d, runEnd), y, 1f * d, c.ink)
                x += 7 * d
            }
            if (runEnd > ls) p.line(runEnd, y - 3 * d, runEnd, y + 3 * d, 1f * d, c.ink)
            runStart = null
        }
        for (pm in s.measures) {
            if (pm.foldStart) flush()
            for (ps in pm.slots) {
                val on = ps.slot.beats.any { !it.isRest && test(it) }
                if (on) {
                    if (runStart == null) runStart = ps.x
                    runEnd = ps.x + 6 * d
                } else if (ps.slot.beats.any { !it.isRest }) flush()
            }
        }
        flush()
    }

    // ── Rests ───────────────────────────────────────────────────────────────

    private fun drawRests(p: ScorePainter, s: ScoreSystem, pm: PlacedMeasure) {
        val mid = (s.staffTop + s.staffBottom) / 2f
        val u = if (isDrums) s.gap else s.gap * 0.72f
        for (ps in pm.slots) for (b in ps.slot.beats) {
            if (!b.isRest || b.voice > 0) continue
            rest(p, b, ps.x, mid, u)
        }
    }

    private fun rest(p: ScorePainter, b: RenderedBeat, x: Float, mid: Float, u: Float) {
        val col = c.ink
        when {
            b.type <= 1 -> p.rect(x - 0.6f * u, mid - u, x + 0.6f * u, mid - 0.5f * u, col)
            b.type == 2 -> p.rect(x - 0.6f * u, mid - 0.5f * u, x + 0.6f * u, mid, col)
            b.type == 4 -> {
                p.path(
                    floatArrayOf(
                        x - 0.3f * u, mid - 1.5f * u,
                        x + 0.35f * u, mid - 0.75f * u,
                        x - 0.25f * u, mid - 0.1f * u,
                        x + 0.35f * u, mid + 0.6f * u,
                    ),
                    false, col, false, 0.28f * u,
                )
                p.quad(x + 0.35f * u, mid + 0.6f * u, x - 0.6f * u, mid + 0.3f * u, x, mid + 1.35f * u, col, 0.2f * u)
            }
            else -> {
                val flags = when {
                    b.type >= 64 -> 4
                    b.type >= 32 -> 3
                    b.type >= 16 -> 2
                    else -> 1
                }
                val top = mid - 0.9f * u
                p.line(x + 0.45f * u, top, x - 0.1f * u + (flags - 1) * -0.25f * u, mid + (0.6f + flags * 0.55f) * u, 0.16f * u, col)
                repeat(flags) { k ->
                    val fy = top + k * 0.9f * u
                    val fx = x + 0.45f * u - k * 0.25f * u
                    p.circle(fx - 0.55f * u, fy + 0.2f * u, 0.22f * u, col, true)
                    p.quad(fx - 0.55f * u, fy + 0.35f * u, fx - 0.2f * u, fy + 0.45f * u, fx, fy, col, 0.12f * u)
                }
            }
        }
        for (k in 0 until b.dots) p.circle(x + (0.95f + 0.45f * k) * u, mid - 0.5f * u, 0.14f * u, col, true)
    }

    // ── Rhythm: stems, beams, flags, tuplets ────────────────────────────────

    private fun level(type: Int) = when {
        type >= 64 -> 4
        type >= 32 -> 3
        type >= 16 -> 2
        type >= 8 -> 1
        else -> 0
    }

    private class Col(val beat: RenderedBeat, val x: Float, val topY: Float)

    private fun drawRhythm(p: ScorePainter, s: ScoreSystem, pm: PlacedMeasure) {
        val cols = ArrayList<Col>()
        for (ps in pm.slots) {
            val b = ps.slot.beats.firstOrNull { it.voice == 0 } ?: continue
            val sx = if (isDrums && !b.isRest) stemX(s, ps.x) else ps.x
            val top = if (isDrums && !b.isRest) posY(s, b.notes.minOf { it.staffPos }) else s.stemTop
            cols += Col(b, sx, top)
        }
        if (cols.isEmpty()) return
        val bottom = s.stemBottom
        val beamGap = 4 * d
        val beamW = 2.6f * d
        val stemW = 1f * d

        // Beam groups: Songsterr's beamStart/beamStop when present, else by beat.
        val useFlags = cols.any { it.beat.beamStart || it.beat.beamStop }
        val sig = pm.measure.timeSignature
        val groupTicks = if (sig[1] == 8 && sig[0] % 3 == 0) TabParser.TICKS_PER_WHOLE * 3 / 8 else TabParser.TICKS_PER_QUARTER
        val groups = ArrayList<MutableList<Col>>()
        var open: MutableList<Col>? = null
        var openKey = -1L
        for (col in cols) {
            val b = col.beat
            if (useFlags) {
                if (open != null) {
                    open += col
                    if (b.beamStop) open = null
                } else if (b.beamStart && level(b.type) > 0) {
                    open = mutableListOf(col)
                    groups += open
                } else {
                    groups += mutableListOf(col)
                }
            } else {
                val key = b.onsetTicks / groupTicks
                if (b.isRest || level(b.type) == 0) {
                    open = null
                    groups += mutableListOf(col)
                } else if (open != null && key == openKey) {
                    open += col
                } else {
                    open = mutableListOf(col)
                    openKey = key
                    groups += open
                }
            }
        }

        for (g in groups) {
            val notesInGroup = g.filter { !it.beat.isRest }
            if (notesInGroup.isEmpty() && g.size == 1) continue // lone rest: glyph only
            for (col in g) {
                val b = col.beat
                if (b.type <= 1) continue
                if (b.isRest) {
                    if (g.size > 1) p.line(col.x, bottom - 6 * d, col.x, bottom, stemW, c.ink)
                    continue
                }
                val top = if (isDrums) col.topY else if (b.type == 2) (s.stemTop + bottom) / 2f else s.stemTop
                p.line(col.x, top, col.x, bottom, stemW, c.ink)
                if (!isDrums && b.dots > 0) {
                    for (k in 0 until b.dots) p.circle(col.x + (4 + 3.5f * k) * d, bottom - 3 * d, 1.4f * d, c.ink, true)
                }
            }
            if (g.size == 1) {
                val col = g[0]
                if (!col.beat.isRest) repeat(level(col.beat.type)) { k ->
                    val y = bottom - k * beamGap
                    p.quad(col.x, y, col.x + 3 * d, y - 3 * d, col.x + 7 * d, y - 8 * d, c.ink, 1.4f * d)
                }
            } else {
                p.line(g.first().x, bottom, g.last().x, bottom, beamW, c.ink)
                for (lvl in 2..4) for (i in g.indices) {
                    val col = g[i]
                    if (level(col.beat.type) < lvl) continue
                    val y = bottom - (lvl - 1) * beamGap
                    val next = g.getOrNull(i + 1)
                    val prev = g.getOrNull(i - 1)
                    when {
                        next != null && level(next.beat.type) >= lvl -> p.line(col.x, y, next.x, y, beamW, c.ink)
                        prev != null && level(prev.beat.type) >= lvl -> Unit
                        next != null -> p.line(col.x, y, col.x + 5 * d, y, beamW, c.ink)
                        else -> p.line(col.x - 5 * d, y, col.x, y, beamW, c.ink)
                    }
                }
            }
        }

        // Tuplet brackets.
        var i = 0
        while (i < cols.size) {
            val n = cols[i].beat.tuplet
            if (n == null) { i++; continue }
            var j = i
            while (j + 1 < cols.size && cols[j + 1].beat.tuplet == n && !cols[j + 1].beat.tupletStart &&
                !cols[j].beat.tupletStop && (j - i + 1) < n * 2
            ) j++
            val a = cols[i].x
            val b = cols[j].x
            val y = s.tupletCenter
            val label = n.toString()
            val tw = p.measure(label, 9 * sp) + 6 * d
            val mid = (a + b) / 2f
            if (b - a > tw + 4 * d) {
                p.line(a, y - 4 * d, a, y, 1f * d, c.ink)
                p.line(a, y, mid - tw / 2f, y, 1f * d, c.ink)
                p.line(mid + tw / 2f, y, b, y, 1f * d, c.ink)
                p.line(b, y - 4 * d, b, y, 1f * d, c.ink)
            }
            p.text(label, mid, y + 3.2f * sp, 9 * sp, c.ink, Align.CENTER, Font.ITALIC)
            i = j + 1
        }
    }

    // ── Marks: accents, chords, text, dynamics, strokes ─────────────────────

    private fun drawMarks(p: ScorePainter, s: ScoreSystem, pm: PlacedMeasure) {
        for (ps in pm.slots) for (b in ps.slot.beats) {
            val x = ps.x
            b.chordText?.let { p.text(it, x - 5 * d, s.chordBaseline, 11 * sp, c.ink, Align.LEFT, Font.BOLD) }
            b.text?.let { p.text(it, x - 5 * d, s.textBaseline, 10 * sp, c.faint, Align.LEFT, Font.ITALIC) }
            b.velocity?.let { p.text(it, x, s.dynamicsBaseline, 13 * sp, c.ink, Align.CENTER, Font.SERIF_BOLD_ITALIC) }
            if (b.isRest) continue

            val parts = ArrayList<String>()
            when (b.notes.maxOfOrNull { it.accent } ?: 0) {
                1 -> parts += ">"
                2 -> parts += "^"
            }
            if (!isDrums && b.notes.any { it.staccato }) parts += "•"
            b.notes.firstNotNullOfOrNull { it.harmonicLabel }?.let { parts += it }
            if (b.notes.any { it.trill }) parts += "tr"
            if (b.tapping) parts += "T"
            if (b.tremoloPicking) parts += "≡"
            if (b.tremoloBar) parts += "w/bar"
            when (b.pickStroke) {
                "down" -> parts += "⊓"
                "up" -> parts += "V"
            }
            if (b.upStroke) parts += "↑"
            if (b.downStroke) parts += "↓"
            if (parts.isNotEmpty()) p.text(parts.joinToString(" "), x, s.marksCenter + 4 * sp, 10 * sp, c.ink, Align.CENTER, Font.BOLD)

            val wide = b.wideVibrato || b.notes.any { it.wideVibrato }
            if (b.vibrato || wide || b.notes.any { it.vibrato }) {
                val amp = (if (wide) 3f else 1.8f) * d
                val y = s.marksCenter
                var px = x + if (parts.isEmpty()) -4 * d else 9 * d
                repeat(4) { k ->
                    val nx = px + 3 * d
                    p.quad(px, y, px + 1.5f * d, y + if (k % 2 == 0) -amp else amp, nx, y, c.ink, 1.1f * d)
                    px = nx
                }
            }
        }
    }

    // ── Overlays (drawn over a system by the screen) ────────────────────────

    fun drawLoop(p: ScorePainter, s: ScoreSystem, from: Int, to: Int) {
        for (pm in s.measures) {
            if (pm.realMeasures.none { it in from..to }) continue
            p.rect(pm.x, s.staffTop - 1.2f * s.gap, pm.right, s.staffBottom + 1.2f * s.gap, c.loop, true, 4 * d)
        }
    }

    fun drawCursor(p: ScorePainter, s: ScoreSystem, x: Float, alpha: Float = 1f) {
        if (alpha <= 0.01f) return
        val top = s.staffTop - 1.2f * s.gap
        val bottom = s.staffBottom + 1.2f * s.gap
        val w = 7 * d
        fun a(color: Int): Int {
            val base = (color ushr 24) and 0xFF
            return (color and 0x00FFFFFF) or ((base * alpha).toInt().coerceIn(0, 255) shl 24)
        }
        p.rect(x - w, top, x + w, bottom, a(c.cursor and 0x33FFFFFF), true, w)
        p.rect(x - w, top, x + w, bottom, a(c.cursor), false, w, 2 * d)
        p.circle(x, top + 5 * d, 3 * d, a(c.cursor), true)
    }
}
