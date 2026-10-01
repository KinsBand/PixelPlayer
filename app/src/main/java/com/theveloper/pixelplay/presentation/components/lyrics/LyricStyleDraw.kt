package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/*
 * Drawing for the lyric animation styles. Everything here is a pure function of the text
 * layout, the sung position (characters) and the playback clock, called from draw lambdas:
 * nothing recomposes per frame, nothing re-measures text, and the timed parts follow the
 * clock (they freeze on pause and jump on seek without replaying).
 */

/** Which word the singing is on: the active one, or the last one sung while resting. */
internal class WordCursor(val index: Int, val active: Boolean, val progress: Float)

internal fun wordCursor(starts: IntArray, ends: IntArray, p: Float): WordCursor {
    var idx = -1
    for (i in starts.indices) {
        if (ends[i] <= starts[i]) continue
        if (p > starts[i]) idx = i else break
    }
    if (idx < 0) return WordCursor(-1, false, 0f)
    val s = starts[idx]
    val e = ends[idx]
    return if (p < e) WordCursor(idx, true, ((p - s) / (e - s).coerceAtLeast(1)).coerceIn(0f, 1f))
    else WordCursor(idx, false, 1f)
}

/** The previous non-empty word before [index] (-1 = none). */
internal fun previousWord(starts: IntArray, ends: IntArray, index: Int): Int {
    var i = index - 1
    while (i >= 0) {
        if (ends[i] > starts[i]) return i
        i--
    }
    return -1
}

/**
 * 0 → 1 over the word's accent (a short, bounded settle), timed from the clock when timing is
 * known; otherwise estimated from how far the word has filled.
 */
internal fun accentProgress(
    index: Int,
    cursor: WordCursor,
    timeline: LyricWordTimeline?,
    nowMs: Long?,
    accentMs: Int,
): Float {
    if (index < 0) return 1f
    if (timeline != null && nowMs != null && index < timeline.size) {
        val start = timeline.startsMs[index]
        val dur = boundedDuration(accentMs, timeline.endsMs[index] - start, 0.5f)
        if (dur <= 0) return 1f
        return ((nowMs - start).toFloat() / dur).coerceIn(0f, 1f)
    }
    return if (cursor.index == index && cursor.active) (cursor.progress / 0.3f).coerceIn(0f, 1f) else 1f
}

/** One rect per visual line the character range [s, e) covers. */
internal fun wordRects(result: TextLayoutResult, s: Int, e: Int, length: Int): List<Rect> {
    if (e <= s || length == 0) return emptyList()
    val first = result.getLineForOffset(s.coerceIn(0, length - 1))
    val last = result.getLineForOffset((e - 1).coerceIn(0, length - 1))
    val out = ArrayList<Rect>(last - first + 1)
    for (line in first..last) {
        val ls = result.getLineStart(line)
        val le = result.getLineEnd(line, visibleEnd = true)
        val a = s.coerceIn(ls, le)
        val b = e.coerceIn(a, le)
        if (b <= a) continue
        val xa = result.getHorizontalPosition(a, usePrimaryDirection = true)
        val xb = result.getHorizontalPosition(b, usePrimaryDirection = true)
        if (abs(xb - xa) < 1f) continue
        out += Rect(minOf(xa, xb), result.getLineTop(line), maxOf(xa, xb), result.getLineBottom(line))
    }
    return out
}

/** x of a fractional character position on its visual line (RTL aware). */
internal fun xAt(result: TextLayoutResult, p: Float, length: Int): Float {
    if (length == 0) return 0f
    val i = floor(p).toInt().coerceIn(0, length - 1)
    val line = result.getLineForOffset(i)
    val le = result.getLineEnd(line, visibleEnd = true)
    val rtl = result.getParagraphDirection(i) == ResolvedTextDirection.Rtl
    val a = result.getHorizontalPosition(i, usePrimaryDirection = true)
    val b = if (i + 1 < le) result.getHorizontalPosition(i + 1, usePrimaryDirection = true)
    else if (rtl) result.getLineLeft(line) else result.getLineRight(line)
    return a + (b - a) * (p - i)
}

/**
 * Erases (DstOut) everything after sung position [p] on every visual line, with a soft edge of
 * [feather] px. Must run in the highlight layer right after its text is drawn.
 */
internal fun DrawScope.eraseAfter(result: TextLayoutResult, p: Float, feather: Float) {
    val w = size.width
    for (line in 0 until result.lineCount) {
        val ls = result.getLineStart(line)
        val le = result.getLineEnd(line, visibleEnd = true)
        val top = result.getLineTop(line)
        val bottom = result.getLineBottom(line)
        if (bottom <= top) continue
        if (p >= le) continue
        if (p <= ls) {
            drawRect(Color.Black, Offset(0f, top), Size(w, bottom - top), blendMode = BlendMode.DstOut)
            continue
        }
        val rtl = result.getParagraphDirection(ls) == ResolvedTextDirection.Rtl
        val i = floor(p).toInt().coerceIn(ls, (le - 1).coerceAtLeast(ls))
        val t = p - i
        val a = result.getHorizontalPosition(i, usePrimaryDirection = true)
        val b = if (i + 1 < le) result.getHorizontalPosition(i + 1, usePrimaryDirection = true)
        else if (rtl) result.getLineLeft(line) else result.getLineRight(line)
        val x = a + (b - a) * t
        val f = feather.coerceAtLeast(0.5f)
        if (!rtl) {
            drawRect(
                brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = x - f, endX = x + f * 0.35f),
                topLeft = Offset(x - f, top),
                size = Size((w - (x - f)).coerceAtLeast(0f), bottom - top),
                blendMode = BlendMode.DstOut
            )
        } else {
            drawRect(
                brush = Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = x - f * 0.35f, endX = x + f),
                topLeft = Offset(0f, top),
                size = Size((x + f).coerceAtMost(w), bottom - top),
                blendMode = BlendMode.DstOut
            )
        }
    }
}

/** Erases the character range [from, to) with [alpha] (0 = nothing, 1 = all). */
internal fun DrawScope.eraseRange(result: TextLayoutResult, from: Int, to: Int, length: Int, alpha: Float) {
    if (alpha <= 0.001f) return
    for (r in wordRects(result, from, to, length)) {
        drawRect(Color.Black.copy(alpha = alpha.coerceIn(0f, 1f)), r.topLeft, r.size, blendMode = BlendMode.DstOut)
    }
}

private fun smoothstep(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun lerpRect(a: Rect, b: Rect, t: Float) =
    Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

/** Everything a style needs to draw one frame of one line. */
internal class StyleFrame(
    val result: TextLayoutResult,
    val p: Float,
    val length: Int,
    val starts: IntArray,
    val ends: IntArray,
    val timeline: LyricWordTimeline?,
    val nowMs: Long?,
    val style: LyricsAnimationStyle,
    val reduced: Boolean,
    val featherPx: Float,
    val highlight: Color,
) {
    val spec = style.spec
    val cursor = wordCursor(starts, ends, p)
    val prev = if (cursor.index >= 0) previousWord(starts, ends, cursor.index) else -1
    /** Accent / handover progress of the current word (1 = settled). */
    val accent = if (cursor.index >= 0 && cursor.active) {
        accentProgress(cursor.index, cursor, timeline, nowMs, spec.accentMs)
    } else 1f

    fun rectsOf(i: Int): List<Rect> = if (i < 0) emptyList() else wordRects(result, starts[i], ends[i], length)

    fun wordDurationMs(i: Int): Long? =
        if (timeline != null && i in 0 until timeline.size) timeline.endsMs[i] - timeline.startsMs[i] else null
}

// ── Behind the text (drawn in the unsung layer, before its glyphs) ─────────────────────────

internal fun DrawScope.drawStyleBehind(f: StyleFrame) {
    if (f.cursor.index < 0) return
    when (f.spec.word) {
        WordTreatment.CAPSULE -> drawCapsule(f, liquid = false)
        WordTreatment.LIQUID -> drawCapsule(f, liquid = true)
        WordTreatment.HALO -> drawHalo(f, strength = 0.20f, residual = true)
        WordTreatment.GLOW -> drawHalo(f, strength = 0.34f, residual = true)
        WordTreatment.SPOTLIGHT -> drawSpotlight(f)
        else -> Unit
    }
}

/**
 * Glass capsule / Flow liquid. The capsule moves from the previous word to the current one
 * within the bounded accent; across a line wrap it fades out and back in instead of sliding
 * diagonally. The liquid variant stretches from the previous word and contracts to the new one.
 */
private fun DrawScope.drawCapsule(f: StyleFrame, liquid: Boolean) {
    val current = f.rectsOf(f.cursor.index).firstOrNull() ?: return
    val lineH = current.height
    val padX = lineH * 0.3f
    val padY = lineH * 0.04f
    fun pad(r: Rect) = Rect(r.left - padX, r.top + padY, r.right + padX, r.bottom - padY)
    val target = pad(current)
    val previous = f.rectsOf(f.prev).lastOrNull()?.let(::pad)
    val t = if (f.reduced) 1f else MotionTokens.Emphasized.transform(f.accent)
    val sameLine = previous != null && abs(previous.center.y - target.center.y) < lineH * 0.5f
    val fill = f.highlight.copy(alpha = if (liquid) 0.16f else 0.15f)
    val edge = f.highlight.copy(alpha = 0.32f)
    val radius = CornerRadius(target.height / 2f, target.height / 2f)
    fun capsule(r: Rect, alpha: Float) {
        if (alpha <= 0.01f) return
        drawRoundRect(fill.copy(alpha = fill.alpha * alpha), r.topLeft, r.size, radius)
        if (!liquid) {
            drawRoundRect(edge.copy(alpha = edge.alpha * alpha), r.topLeft, r.size, radius, style = Stroke(width = 1.dp.toPx()))
        }
    }
    when {
        previous == null -> capsule(target, if (f.reduced) 1f else smoothstep(f.accent))
        !sameLine -> {
            // Wrap: fade out where it was, fade in where it goes.
            capsule(previous, 1f - t)
            capsule(target, t)
        }
        liquid -> {
            // Stretch: the trailing edge stays on the previous word, then catches up.
            val gap = abs(target.left - previous.right)
            if (gap > lineH * 1.5f) {
                capsule(previous, 1f - t); capsule(target, t)
            } else {
                val leftEdge = lerp(minOf(previous.left, target.left), target.left, t)
                val rightEdge = lerp(maxOf(previous.right, target.right), target.right, t)
                capsule(Rect(leftEdge, target.top, rightEdge, target.bottom), 1f)
            }
        }
        else -> capsule(lerpRect(previous, target, t), 1f)
    }
}

/**
 * Soft halo (Cinematic) / coloured glow (Neon) behind the active word. The previous word's
 * light fades out within the accent, so it never reads as active; spread is capped by the
 * line height so neighbouring words stay distinct.
 */
private fun DrawScope.drawHalo(f: StyleFrame, strength: Float, residual: Boolean) {
    val t = if (f.reduced) 1f else smoothstep(f.accent)
    fun glow(r: Rect, alpha: Float) {
        if (alpha <= 0.01f) return
        val radius = maxOf(r.width * 0.62f, r.height * 0.85f)
        val c = r.center
        drawOval(
            brush = Brush.radialGradient(
                listOf(f.highlight.copy(alpha = strength * alpha), f.highlight.copy(alpha = strength * 0.35f * alpha), Color.Transparent),
                center = c, radius = radius
            ),
            topLeft = Offset(c.x - radius, c.y - r.height * 0.9f),
            size = Size(radius * 2f, r.height * 1.8f)
        )
    }
    val active = if (f.cursor.active) 1f else 0.55f
    f.rectsOf(f.cursor.index).forEach { glow(it, t * active) }
    if (residual && !f.reduced && f.prev >= 0 && t < 1f) {
        f.rectsOf(f.prev).forEach { glow(it, (1f - t) * 0.6f) }
    }
}

/** Soft: a feathered pool of light under the sung edge. */
private fun DrawScope.drawSpotlight(f: StyleFrame) {
    if (!f.cursor.active) return
    val rect = f.rectsOf(f.cursor.index).firstOrNull() ?: return
    val x = if (f.reduced) rect.center.x else xAt(f.result, f.p, f.length)
    val c = Offset(x, rect.center.y)
    val radius = rect.height * 1.2f
    drawCircle(
        brush = Brush.radialGradient(listOf(f.highlight.copy(alpha = 0.16f), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c
    )
}

// ── The sung layer (highlight copy, offscreen) ─────────────────────────────────────────────

/**
 * Erases the unsung part of the highlight copy for [f]'s style and paints the style's letter
 * treatment on the sung glyphs. Runs in the highlight layer right after its text.
 */
internal fun DrawScope.drawStyledSung(f: StyleFrame) {
    val spec = f.spec
    val c = f.cursor
    val wordUnit = spec.word == WordTreatment.CAPSULE || spec.word == WordTreatment.HALO
    // 1. What's sung.
    if (c.index >= 0 && c.active && wordUnit) {
        // Glass / Cinematic light the word as a unit: Glass quickly, Cinematic across the word.
        eraseAfter(f.result, f.ends[c.index].toFloat(), 0f)
        val lit = if (spec.word == WordTreatment.CAPSULE) smoothstep(f.accent) else smoothstep(c.progress)
        eraseRange(f.result, f.starts[c.index], f.ends[c.index], f.length, 1f - lit)
    } else {
        eraseAfter(f.result, f.p, f.featherPx * spec.featherScale)
    }
    // 2. Completed words: visibly sung but quieter than the active word.
    if (spec.completedQuiet > 0f && c.index >= 0) {
        val completedEnd = if (c.active) f.starts[c.index] else f.ends[c.index]
        eraseRange(f.result, 0, completedEnd, f.length, spec.completedQuiet)
    }
    if (c.index < 0) return
    val rects = f.rectsOf(c.index)
    if (rects.isEmpty()) return
    // 3. Letter treatment on the word being sung (SrcAtop: only on glyph pixels).
    if (c.active) when (spec.letter) {
        LetterTreatment.LIGHT_SWEEP -> if (!f.reduced) sweep(f, rects, width = 1.2f, alpha = 0.26f)
        LetterTreatment.WEIGHT_PASS -> if (!f.reduced) sweep(f, rects, width = 0.55f, alpha = 0.34f)
        LetterTreatment.SHIMMER -> if (!f.reduced) sweep(f, rects, width = 2.2f, alpha = 0.18f)
        LetterTreatment.ILLUMINATE -> rects.forEach {
            drawRect(Color.White.copy(alpha = 0.14f), it.topLeft, it.size, blendMode = BlendMode.SrcAtop)
        }
        LetterTreatment.INK_RICHNESS -> {
            // Ink deepens as the word is sung: thin at the start, full at the end.
            val ink = 0.55f + 0.45f * c.progress
            val x = xAt(f.result, f.p, f.length)
            rects.forEach { r ->
                val right = if (x in r.left..r.right) x else r.right
                drawRect(Color.Black.copy(alpha = 1f - ink), r.topLeft, Size((right - r.left).coerceAtLeast(0f), r.height), blendMode = BlendMode.DstOut)
            }
        }
        LetterTreatment.VOWEL_GLOW -> vowelGlow(f)
        else -> Unit
    }
    // Performance fallback: long held words get a modest, duration-based brightness accent.
    if (c.active && f.style == LyricsAnimationStyle.PERFORMANCE && isLongWord(f, c.index)) {
        rects.forEach { drawRect(Color.White.copy(alpha = 0.12f * sin(Math.PI * c.progress).toFloat()), it.topLeft, it.size, blendMode = BlendMode.SrcAtop) }
    }
    // 4. Acoustic underline: drawn from the word's start to the sung edge; the finished one fades.
    if (spec.word == WordTreatment.UNDERLINE) underline(f)
}

/** A band of light moving through the word with its progress. */
private fun DrawScope.sweep(f: StyleFrame, rects: List<Rect>, width: Float, alpha: Float) {
    val first = rects.first()
    val total = rects.sumOf { it.width.toDouble() }.toFloat().coerceAtLeast(1f)
    var remaining = total * f.cursor.progress
    var x = first.left
    var y = first
    for (r in rects) {
        if (remaining <= r.width) { x = r.left + remaining; y = r; break }
        remaining -= r.width
        x = r.right; y = r
    }
    val band = y.height * width
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent, 0.5f to Color.White.copy(alpha = alpha), 1f to Color.Transparent,
            startX = x - band, endX = x + band
        ),
        topLeft = Offset(y.left, y.top),
        size = Size(y.width, y.height),
        blendMode = BlendMode.SrcAtop
    )
}

private fun DrawScope.underline(f: StyleFrame) {
    val c = f.cursor
    val stroke = (f.featherPx / 0.55f) * 0.06f
    fun line(r: Rect, toX: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val y = (r.bottom - stroke * 2.2f).coerceAtLeast(r.top)
        drawLine(
            color = f.highlight.copy(alpha = alpha),
            start = Offset(r.left, y), end = Offset(toX.coerceIn(r.left, r.right), y),
            strokeWidth = stroke.coerceAtLeast(1f), cap = StrokeCap.Round
        )
    }
    val rects = f.rectsOf(c.index)
    if (c.active) {
        val x = xAt(f.result, f.p, f.length)
        rects.forEach { r -> line(r, if (x in r.left..r.right) x else if (x > r.right) r.right else r.left, 0.9f) }
        // Previous underline fades as the new stroke begins.
        if (f.prev >= 0 && !f.reduced) f.rectsOf(f.prev).forEach { line(it, it.right, 0.9f * (1f - f.accent)) }
    } else {
        // Resting after a word: its stroke stays until the next word starts.
        rects.forEach { line(it, it.right, 0.9f) }
    }
}

private fun DrawScope.vowelGlow(f: StyleFrame) {
    val timeline = f.timeline ?: return
    val now = f.nowMs ?: return
    val i = f.cursor.index
    val phonemes = timeline.measured.getOrNull(i) ?: return
    val wordStart = f.starts[i]
    for (ph in phonemes) {
        if (!ph.soundType.contains("vowel", ignoreCase = true)) continue
        if (now < ph.time || now >= ph.endTime) continue
        val s = (wordStart + ph.characterStart).coerceIn(f.starts[i], f.ends[i])
        val e = (wordStart + ph.characterEnd).coerceIn(s, f.ends[i])
        wordRects(f.result, s, e, f.length).forEach { r ->
            drawRect(Color.White.copy(alpha = 0.22f), r.topLeft, r.size, blendMode = BlendMode.SrcAtop)
        }
    }
}

internal fun isLongWord(f: StyleFrame, i: Int): Boolean {
    val timeline = f.timeline ?: return (f.ends[i] - f.starts[i]) >= 7
    if (timeline.size < 2) return false
    val durations = LongArray(timeline.size) { timeline.endsMs[it] - timeline.startsMs[it] }.sorted()
    val median = durations[durations.size / 2].coerceAtLeast(1L)
    return (timeline.endsMs[i] - timeline.startsMs[i]) > median * 1.8f
}

// ── Movement of words and letters ──────────────────────────────────────────────────────────

/**
 * Pieces of the line drawn moved for the style (empty = drawn flat). Playful pops the new word
 * and runs a tiny letter wave; Performance lifts long held words a little. Everything else keeps
 * letters stationary. Letter pieces follow grapheme clusters, so emoji and combining marks
 * never split.
 */
internal fun DrawScope.styledPieces(f: StyleFrame, fontPx: Float): List<LiftedPiece> {
    if (f.reduced) return emptyList()
    val c = f.cursor
    if (c.index < 0 || !c.active) return emptyList()
    val dur = f.wordDurationMs(c.index)
    val dense = dur != null && dur < MotionTokens.DurationShort4
    return when (f.style) {
        LyricsAnimationStyle.PLAYFUL -> {
            val popPeak = if (dense) 0.025f else 0.05f
            val a = f.accent
            val pop = if (a < 0.35f) 1f - (1f - a / 0.35f).let { it * it * it } else 1f - smoothstep((a - 0.35f) / 0.65f)
            val scale = 1f + popPeak * pop
            if (dense) {
                f.rectsOf(c.index).map { LiftedPiece(it, 0f, scale) }
            } else {
                // Wave: about 1.5 dp at a normal lyric size, scaled with the text, capped 1–2 dp.
                val wavePx = (1.5.dp.toPx() * (fontPx / 24.dp.toPx())).coerceIn(1.dp.toPx(), 2.dp.toPx())
                graphemePieces(f, c.index) { clusterStart ->
                    val amount = letterLiftAmount(f.p - clusterStart, 2f)
                    amount * wavePx
                }.map { LiftedPiece(it.first, it.second, scale) }
            }
        }
        LyricsAnimationStyle.PERFORMANCE -> {
            if (dense || !isLongWord(f, c.index)) emptyList()
            else {
                val amount = wordLiftAmount(c.progress)
                f.rectsOf(c.index).map { LiftedPiece(it, it.height * 0.05f * amount, 1f + 0.025f * amount) }
            }
        }
        else -> emptyList()
    }
}

/** (cluster rect, lift px) for each grapheme cluster of word [i]. */
private inline fun graphemePieces(f: StyleFrame, i: Int, lift: (Int) -> Float): List<Pair<Rect, Float>> {
    val text = f.result.layoutInput.text.text
    val s = f.starts[i].coerceIn(0, text.length)
    val e = f.ends[i].coerceIn(s, text.length)
    if (e <= s) return emptyList()
    val bi = android.icu.text.BreakIterator.getCharacterInstance()
    bi.setText(text.substring(s, e))
    val out = ArrayList<Pair<Rect, Float>>()
    var start = bi.first()
    var end = bi.next()
    while (end != android.icu.text.BreakIterator.DONE) {
        val cs = s + start
        val ce = s + end
        if (!text[cs].isWhitespace()) {
            val dy = lift(cs)
            if (dy > 0.1f) {
                wordRects(f.result, cs, ce, f.length).firstOrNull()?.let { out += it to dy }
            }
        }
        start = end
        end = bi.next()
    }
    return out
}

