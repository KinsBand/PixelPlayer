package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.lyrics.LetterTimingEstimator
import com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode
import com.theveloper.pixelplay.data.model.SyncedPhoneme
import com.theveloper.pixelplay.data.model.SyncedWord
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Smooth lyric timing shared by the lyrics sheet, the split (face-to-face) view and the
 * cover overlay.
 *
 * The player publishes its position in coarse ticks, so anything driven straight from it moves
 * in steps. [LyricsClock] turns those ticks into a per-frame position: between ticks it advances
 * with the real frame clock, never runs backwards for small corrections (it eases them out by
 * holding briefly), and jumps immediately on seeks. It stops advancing when the ticks stop
 * (pause), so nothing drifts ahead of the audio.
 *
 * Read [LyricsClock.now] only inside draw / layout lambdas or derived state: the value changes
 * every frame, and reading it there invalidates just the drawing, not composition.
 */
@Stable
class LyricsClock internal constructor(
    private val frameState: MutableLongState,
    private val overrideState: State<Long?>
) {
    fun now(): Long = overrideState.value ?: frameState.longValue
}

/** Frame-interpolated lyrics position (ms, sync offset applied). */
@Composable
fun rememberLyricsClock(
    positionFlow: StateFlow<Long>,
    syncOffsetMs: Int,
    positionOverrideMs: Long? = null
): LyricsClock {
    val frameState = remember { mutableLongStateOf((positionFlow.value + syncOffsetMs).coerceAtLeast(0L)) }
    val override = rememberUpdatedState(positionOverrideMs)
    val offset = rememberUpdatedState(syncOffsetMs)
    val clock = remember(frameState) { LyricsClock(frameState, override) }

    LaunchedEffect(positionFlow, frameState) {
        // Subscribe to the flow instead of only reading .value: a StateFlow made with
        // stateIn(WhileSubscribed) (the album cover's scrub-aware position) never updates
        // without a collector, which froze the cover lyrics on their first line.
        var latest = positionFlow.value
        launch { positionFlow.collect { latest = it } }
        var lastSample = latest
        var lastSampleFrameMs = -1L
        var lastOffset = offset.value
        var intervalEstimate = 250.0 // ms between ticks while playing; learned below
        var displayed = (lastSample + lastOffset).coerceAtLeast(0L).toDouble()
        while (true) {
            val stalled = withFrameNanos { frameNanos ->
                val frameMs = frameNanos / 1_000_000L
                val sample = latest
                val currentOffset = offset.value
                if (lastSampleFrameMs < 0) lastSampleFrameMs = frameMs
                if (sample != lastSample || currentOffset != lastOffset) {
                    val gap = (frameMs - lastSampleFrameMs).coerceAtLeast(1L)
                    val delta = sample - lastSample
                    // Learn the tick interval only from normal playback ticks.
                    if (delta in 1..2_000 && gap in 16..2_000) {
                        intervalEstimate = intervalEstimate * 0.8 + gap * 0.2
                    }
                    lastSample = sample
                    lastOffset = currentOffset
                    lastSampleFrameMs = frameMs
                }
                val sinceSample = (frameMs - lastSampleFrameMs).toDouble()
                // Extrapolate at most ~1.3 ticks ahead: if ticks stop (paused, buffering) the
                // clock stops too instead of drifting away from the audio.
                val leadCap = (intervalEstimate * 1.3).coerceIn(120.0, 1_200.0)
                val lead = sinceSample.coerceAtMost(leadCap)
                val target = (lastSample + lastOffset).coerceAtLeast(0L) + lead
                displayed = when {
                    // Seek (either way) or a big correction: jump.
                    abs(target - displayed) > SEEK_JUMP_MS -> target
                    // Small backwards correction: hold still until the audio catches up
                    // rather than visibly stepping back.
                    target < displayed -> displayed
                    else -> target
                }
                val rounded = displayed.toLong()
                if (rounded != frameState.longValue) frameState.longValue = rounded
                // Past the extrapolation cap the displayed value cannot change again until a
                // new tick or offset arrives, so further frames would be pure scheduling cost.
                sinceSample > leadCap
            }
            if (stalled) {
                // Paused/buffering: stop requesting frames until the position or offset moves.
                val stallSample = lastSample
                val stallOffset = lastOffset
                kotlinx.coroutines.flow.merge(
                    positionFlow.filter { it != stallSample }.map { latest = it },
                    snapshotFlow { offset.value }.filter { it != stallOffset }.map { }
                ).first()
            }
        }
    }
    return clock
}

private const val SEEK_JUMP_MS = 450.0

// ── Word / letter fill ─────────────────────────────────────────────────────────────────────

/**
 * How far into [word] the singing is at [positionMs], in characters (0 … word.length),
 * as a continuous value.
 *
 * - Word mode, or no letter timing: the whole word sweeps linearly between its start and end.
 * - Letter mode (and Auto when the source has measured letters): the sweep follows the letter
 *   spans, so it slows down on held vowels and moves quickly over short consonants. Letter mode
 *   uses [LetterTimingEstimator] when a word has no measured letters.
 */
fun wordFillChars(
    word: SyncedWord,
    positionMs: Long,
    fallbackEndMs: Long,
    mode: LyricsHighlightMode
): Float {
    val length = word.word.length
    if (length == 0) return 0f
    if (positionMs <= word.time) return 0f
    val end = (word.endTime?.toLong() ?: fallbackEndMs).coerceAtLeast(word.time + 1L)
    if (positionMs >= end) return length.toFloat()

    val spans: List<SyncedPhoneme>? = when {
        mode == LyricsHighlightMode.WORD || mode == LyricsHighlightMode.AUTO -> null
        mode == LyricsHighlightMode.PHONEME ->
            if (!word.phonemes.isNullOrEmpty()) word.phonemes
            else LetterTimingEstimator.estimate(word.copy(endTime = end.toInt())).takeIf { it.isNotEmpty() }
        else -> null
    }
    if (spans == null) {
        val f = (positionMs - word.time).toFloat() / (end - word.time).toFloat()
        return length * f.coerceIn(0f, 1f)
    }
    var reached = 0f
    for (span in spans) {
        val start = span.characterStart.coerceIn(0, length)
        val stop = span.characterEnd.coerceIn(start, length)
        when {
            positionMs < span.time -> return reached
            positionMs < span.endTime -> {
                val f = (positionMs - span.time).toFloat() / (span.endTime - span.time).coerceAtLeast(1).toFloat()
                return start + (stop - start) * f.coerceIn(0f, 1f)
            }
            else -> reached = stop.toFloat()
        }
    }
    return reached.coerceAtMost(length.toFloat())
}

/** Pixel x of a fractional character position, following the real glyph advances. */
internal fun TextLayoutResult.xForChars(chars: Float, textLength: Int, rtl: Boolean): Float {
    val width = size.width.toFloat()
    if (textLength == 0) return 0f
    if (chars <= 0f) return if (rtl) width else 0f
    if (chars >= textLength) return if (rtl) 0f else width
    val i = floor(chars).toInt().coerceIn(0, textLength - 1)
    val t = chars - i
    val a = getHorizontalPosition(i, usePrimaryDirection = true)
    val b = if (i + 1 >= textLength) (if (rtl) 0f else width) else getHorizontalPosition(i + 1, usePrimaryDirection = true)
    return a + (b - a) * t
}

// ── Whole-line rendering ───────────────────────────────────────────────────────────────────

/**
 * A line's display text plus where each timed word sits in it. The text is the line's own
 * text whenever every word can be found in it in order (so spacing, apostrophes and
 * punctuation are exactly as written); otherwise the words are joined with single spaces.
 */
class LyricWordLayout(val text: String, val starts: IntArray, val ends: IntArray)

private val NO_SPACE_BEFORE = charArrayOf('\'', '\u2019', ',', '.', '!', '?', ';', ':', ')', ']', '}', '\u2026', '-')
private val NO_SPACE_AFTER = charArrayOf('(', '[', '{', '\u201C', '\u2018', '-')

fun buildLyricWordLayout(lineText: String, words: List<SyncedWord>): LyricWordLayout {
    val starts = IntArray(words.size)
    val ends = IntArray(words.size)
    var cursor = 0
    var mapped = true
    for ((i, word) in words.withIndex()) {
        val token = word.word.trim()
        if (token.isEmpty()) {
            starts[i] = cursor; ends[i] = cursor; continue
        }
        val idx = lineText.indexOf(token, cursor)
        // Only skip over spaces / punctuation between words, never over letters.
        if (idx < 0 || lineText.substring(cursor, idx).any { it.isLetterOrDigit() }) {
            mapped = false
            break
        }
        starts[i] = idx
        ends[i] = idx + token.length
        cursor = ends[i]
    }
    if (mapped && lineText.isNotBlank()) return LyricWordLayout(lineText, starts, ends)

    // Fallback: rebuild the text from the words. A space goes between words that start a new
    // word, except before closing punctuation / apostrophes ("I" + "'m") and after openers.
    val sb = StringBuilder()
    for ((i, word) in words.withIndex()) {
        val token = word.word.trim()
        val needsSpace = sb.isNotEmpty() && word.startsNewWord &&
            token.firstOrNull()?.let { it in NO_SPACE_BEFORE } != true && sb.last() !in NO_SPACE_AFTER
        if (needsSpace) sb.append(' ')
        starts[i] = sb.length
        sb.append(token)
        ends[i] = sb.length
    }
    return LyricWordLayout(sb.toString(), starts, ends)
}

/**
 * The continuous sung position in the whole line, in characters of [LyricWordLayout.text].
 * Between two words it rests at the end of the last sung word.
 */
fun sungCharsInLine(
    layout: LyricWordLayout,
    words: List<SyncedWord>,
    positionMs: Long,
    lineEndMs: Long,
    mode: LyricsHighlightMode
): Float {
    if (words.isEmpty()) return 0f
    if (positionMs >= lineEndMs) return layout.text.length.toFloat()
    var pos = 0f
    for ((i, word) in words.withIndex()) {
        if (positionMs < word.time) return pos
        val fallbackEnd = words.getOrNull(i + 1)?.time?.toLong() ?: lineEndMs
        val end = (word.endTime?.toLong() ?: fallbackEnd).coerceAtLeast(word.time + 1L)
        if (positionMs >= end) {
            pos = layout.ends[i].toFloat()
            continue
        }
        val chars = wordFillChars(word, positionMs, fallbackEnd, mode)
        val span = (layout.ends[i] - layout.starts[i]).coerceAtLeast(0)
        val ratio = if (word.word.trim().isEmpty()) 0f else chars / word.word.length.coerceAtLeast(1)
        return layout.starts[i] + span * ratio.coerceIn(0f, 1f)
    }
    return pos
}

/** Replaces the space at every soft wrap with a newline: same length, fixed line breaks. */
/** [text] with [spans] applied (ranges clamped to the text so a stale range can't crash). */
private fun styled(text: String, spans: List<AnnotatedString.Range<SpanStyle>>): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(text)
    val safe = spans.mapNotNull { r ->
        val s = r.start.coerceIn(0, text.length)
        val e = r.end.coerceIn(s, text.length)
        if (e > s) AnnotatedString.Range(r.item, s, e) else null
    }
    return AnnotatedString(text, spanStyles = safe)
}

private fun withHardBreaks(text: String, result: TextLayoutResult): String {
    if (result.lineCount <= 1) return text
    val chars = text.toCharArray()
    for (line in 0 until result.lineCount - 1) {
        val end = result.getLineEnd(line, visibleEnd = false)
        if (end in 1..chars.size && chars[end - 1] == ' ') chars[end - 1] = '\n'
    }
    return String(chars)
}

/**
 * CompositionLocal carrying the active [LyricsHighlightMode] across lyrics composables.
 * Defaults to [LyricsHighlightMode.AUTO].
 */
val LocalLyricsHighlightMode = compositionLocalOf { LyricsHighlightMode.AUTO }

internal data class ActiveWordState(
    val activeIndex: Int,
    val wordStart: Int,
    val wordEnd: Int,
    val fillProgress: Float
)

internal fun findWordBoundaries(text: String): Pair<IntArray, IntArray> {
    if (text.isEmpty()) return Pair(IntArray(0), IntArray(0))
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()
    var inWord = false
    var start = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val charCount = Character.charCount(cp)
        val isSpace = Character.isWhitespace(cp)
        val isCjk = cp in 0x4e00..0x9fff || cp in 0x3040..0x30ff || cp in 0xac00..0xd7af

        if (isCjk) {
            if (inWord) {
                starts.add(start)
                ends.add(i)
                inWord = false
            }
            starts.add(i)
            ends.add(i + charCount)
        } else if (!isSpace) {
            if (!inWord) {
                inWord = true
                start = i
            }
        } else {
            if (inWord) {
                inWord = false
                starts.add(start)
                ends.add(i)
            }
        }
        i += charCount
    }
    if (inWord) {
        starts.add(start)
        ends.add(text.length)
    }
    return Pair(starts.toIntArray(), ends.toIntArray())
}

internal fun resolveWordState(starts: IntArray, ends: IntArray, p: Float): ActiveWordState {
    if (starts.isEmpty()) return ActiveWordState(-1, 0, 0, 0f)
    for (i in starts.indices) {
        val s = starts[i]
        val e = ends[i]
        if (p > s && p < e) {
            val span = (e - s).toFloat().coerceAtLeast(1f)
            val fill = ((p - s) / span).coerceIn(0f, 1f)
            return ActiveWordState(i, s, e, fill)
        }
    }
    return ActiveWordState(-1, 0, 0, 0f)
}

// ── Sung-word / sung-letter motion ────────────────────────────────────────────────────────

/** One piece of a line drawn lifted: [rect] in text coordinates, moved up by [dy] and scaled. */
internal class LiftedPiece(val rect: Rect, val dy: Float, val scale: Float)

private fun easeOutCubic(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

private fun easeInOutSine(t: Float): Float =
    (0.5f - 0.5f * kotlin.math.cos(Math.PI * t.coerceIn(0f, 1f))).toFloat()

/**
 * How lifted the word being sung is at fill [u] (0..1): it rises quickly as the word starts,
 * floats while it's held and settles back as it ends, so the next word takes over without a jump.
 */
internal fun wordLiftAmount(u: Float): Float {
    if (u <= 0f || u >= 1f) return 0f
    val rise = easeOutCubic(u / 0.22f)
    val settle = 1f - easeInOutSine(((u - 0.68f) / 0.32f).coerceIn(0f, 1f))
    return rise * settle
}

/**
 * How lifted a letter [d] characters behind the singing cursor is: it rises while it's sung
 * (0..1) and eases back down over the next [wave] letters, so a soft wave follows the voice.
 */
internal fun letterLiftAmount(d: Float, wave: Float): Float = when {
    d <= 0f -> 0f
    d <= 1f -> easeOutCubic(d)
    wave <= 0f -> 0f
    d <= 1f + wave -> 1f - easeInOutSine((d - 1f) / wave)
    else -> 0f
}

/** The pieces of the line that should be drawn lifted right now (empty = draw it flat). */
internal fun liftedPieces(
    result: TextLayoutResult,
    p: Float,
    length: Int,
    text: String,
    mode: LyricsHighlightMode,
    wordStarts: IntArray,
    wordEnds: IntArray,
    motion: LyricMotion,
): List<LiftedPiece> {
    if (p <= 0f || p >= length || length == 0) return emptyList()
    if (mode == LyricsHighlightMode.PHONEME) {
        if (motion.letterLift <= 0f) return emptyList()
        val cursor = floor(p).toInt()
        val from = (cursor - kotlin.math.ceil(motion.letterWave).toInt() - 1).coerceAtLeast(0)
        val to = cursor.coerceAtMost(length - 1)
        if (from > to) return emptyList()
        val out = ArrayList<LiftedPiece>(to - from + 1)
        for (i in from..to) {
            if (text[i].isWhitespace()) continue
            val amount = letterLiftAmount(p - i, motion.letterWave)
            if (amount < 0.02f) continue
            val box = result.getBoundingBox(i)
            if (box.width <= 0f || box.height <= 0f) continue
            out += LiftedPiece(box, box.height * motion.letterLift * amount, 1f + motion.letterLift * 0.6f * amount)
        }
        return out
    }
    if (motion.wordLift <= 0f && motion.wordScale <= 0f) return emptyList()
    val active = resolveWordState(wordStarts, wordEnds, p)
    if (active.activeIndex < 0) return emptyList()
    val amount = wordLiftAmount(active.fillProgress)
    if (amount < 0.02f) return emptyList()
    val out = ArrayList<LiftedPiece>(2)
    // A word can wrap over two visual lines: lift each part on its own line.
    val firstLine = result.getLineForOffset(active.wordStart.coerceIn(0, length - 1))
    val lastLine = result.getLineForOffset((active.wordEnd - 1).coerceIn(0, length - 1))
    for (line in firstLine..lastLine) {
        val ls = result.getLineStart(line)
        val le = result.getLineEnd(line, visibleEnd = true)
        val s = active.wordStart.coerceIn(ls, le)
        val e = active.wordEnd.coerceIn(s, le)
        if (e <= s) continue
        val xa = result.getHorizontalPosition(s, usePrimaryDirection = true)
        val xb = result.getHorizontalPosition(e, usePrimaryDirection = true)
        val top = result.getLineTop(line)
        val bottom = result.getLineBottom(line)
        if (abs(xb - xa) < 1f) continue
        out += LiftedPiece(
            rect = Rect(minOf(xa, xb), top, maxOf(xa, xb), bottom),
            dy = (bottom - top) * motion.wordLift * amount,
            scale = 1f + motion.wordScale * amount
        )
    }
    return out
}

private val LayerPaint = Paint()

/**
 * Draws [layer] (the text plus anything masked onto it) with [pieces] lifted: the rest of the
 * line in place, each piece moved up and scaled around its own centre. Each piece gets its own
 * layer, so a mask drawn by [layer] moves with the glyphs it belongs to and never touches the
 * neighbouring text.
 */
private fun ContentDrawScope.drawWithLifts(pieces: List<LiftedPiece>, layer: ContentDrawScope.() -> Unit) {
    if (pieces.isEmpty()) {
        layer()
        return
    }
    val self = this
    val bounds = Rect(0f, 0f, size.width, size.height)
    withTransform({ pieces.forEach { clipRect(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom, ClipOp.Difference) } }) {
        drawContext.canvas.saveLayer(bounds, LayerPaint)
        self.layer()
        drawContext.canvas.restore()
    }
    pieces.forEach { piece ->
        val c = piece.rect.center
        withTransform({
            translate(0f, -piece.dy)
            scale(piece.scale, piece.scale, pivot = c)
            clipRect(piece.rect.left, piece.rect.top, piece.rect.right, piece.rect.bottom)
        }) {
            drawContext.canvas.saveLayer(piece.rect.inflate(piece.rect.height), LayerPaint)
            self.layer()
            drawContext.canvas.restore()
        }
    }
}

/**
 * One lyric line as whole text (natural spacing and kerning — no per-word boxes), with the sung
 * part painted over a dim copy.
 *
 * For [LyricsHighlightMode.WORD] and [LyricsHighlightMode.AUTO] modes, words are treated as
 * cohesive units: the word being sung fills with continuous progressive alpha and a fluid bloom,
 * and lifts and swells a little while it's held before settling back as the next word starts.
 *
 * For [LyricsHighlightMode.PHONEME] mode the fill is a soft letter-level wipe, and a small wave of
 * lifted letters follows the voice.
 *
 * How far words and letters move comes from [motion] (each song's own with adaptive typography).
 */
@Composable
fun SmoothLyricLine(
    text: String,
    style: TextStyle,
    baseColor: Color,
    highlightColor: Color,
    textAlign: TextAlign,
    sungChars: () -> Float,
    modifier: Modifier = Modifier,
    mode: LyricsHighlightMode = LocalLyricsHighlightMode.current,
    wordLayout: LyricWordLayout? = null,
    /** Per-word styles (adaptive expressive typography). Ranges index into [text]. */
    spans: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
    motion: LyricMotion = LocalLyricMotion.current
) {
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val styledText = remember(text, spans) { styled(text, spans) }
    val density = LocalDensity.current
    val featherPx = remember(style, density) {
        with(density) { (style.fontSize.takeIf { it.isSp }?.toPx() ?: 16.dp.toPx()) * 0.55f }
    }
    val defaultBoundaries = remember(text) { findWordBoundaries(text) }
    val wordStarts = wordLayout?.starts ?: defaultBoundaries.first
    val wordEnds = wordLayout?.ends ?: defaultBoundaries.second
    val length = text.length
    val motionState = rememberUpdatedState(motion)

    fun piecesNow(p: Float): List<LiftedPiece> {
        val result = layout.value ?: return emptyList()
        return liftedPieces(result, p, length, text, mode, wordStarts, wordEnds, motionState.value)
    }

    Box(modifier = modifier) {
        Text(
            text = styledText,
            style = style,
            color = baseColor,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                // The unsung copy moves with the sung one, so a lifted word never leaves a ghost.
                .drawWithContent {
                    val pieces = piecesNow(sungChars())
                    drawWithLifts(pieces) { drawContent() }
                },
            onTextLayout = { layout.value = it }
        )
        Text(
            text = styledText,
            style = style,
            color = highlightColor,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val p = sungChars()
                    if (p <= 0f) return@drawWithContent
                    if (p >= length) {
                        drawContent()
                        return@drawWithContent
                    }
                    val result = layout.value
                    if (result == null) {
                        drawContent()
                        return@drawWithContent
                    }
                    val pieces = piecesNow(p)
                    drawWithLifts(pieces) {
                        drawContent()
                        drawSungMask(result, p, mode, wordStarts, wordEnds, featherPx, highlightColor)
                    }
                }
        )
    }
}

/**
 * Erases the unsung part of the highlight copy (DstOut) and adds the bloom on the word being sung.
 * Must run right after the highlight text is drawn, in the same layer.
 */
private fun DrawScope.drawSungMask(
    result: TextLayoutResult,
    p: Float,
    mode: LyricsHighlightMode,
    wordStarts: IntArray,
    wordEnds: IntArray,
    featherPx: Float,
    highlightColor: Color,
) {
    val w = size.width

    if (mode == LyricsHighlightMode.PHONEME) {
        for (line in 0 until result.lineCount) {
            val ls = result.getLineStart(line)
            val le = result.getLineEnd(line, visibleEnd = true)
            val top = result.getLineTop(line)
            val bottom = result.getLineBottom(line)
            if (p >= le) continue
            if (p <= ls) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(0f, top),
                    size = Size(w, bottom - top),
                    blendMode = BlendMode.DstOut
                )
                continue
            }
            val rtl = result.getParagraphDirection(ls) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl
            val i = floor(p).toInt().coerceIn(ls, (le - 1).coerceAtLeast(ls))
            val t = p - i
            val a = result.getHorizontalPosition(i, usePrimaryDirection = true)
            val b = if (i + 1 < le) {
                result.getHorizontalPosition(i + 1, usePrimaryDirection = true)
            } else {
                if (rtl) result.getLineLeft(line) else result.getLineRight(line)
            }
            val x = a + (b - a) * t
            val feather = featherPx
            if (!rtl) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black,
                        startX = x - feather,
                        endX = x + feather * 0.35f
                    ),
                    topLeft = Offset(x - feather, top),
                    size = Size((w - (x - feather)).coerceAtLeast(0f), bottom - top),
                    blendMode = BlendMode.DstOut
                )
            } else {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black,
                        1f to Color.Transparent,
                        startX = x - feather * 0.35f,
                        endX = x + feather
                    ),
                    topLeft = Offset(0f, top),
                    size = Size((x + feather).coerceAtMost(w), bottom - top),
                    blendMode = BlendMode.DstOut
                )
            }
            // A soft glow riding the wipe edge, so the letter being sung shines as it fills.
            val glowR = (bottom - top) * 0.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(highlightColor.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(x, (top + bottom) * 0.5f),
                    radius = glowR
                ),
                radius = glowR,
                center = Offset(x, (top + bottom) * 0.5f),
                blendMode = BlendMode.DstOver
            )
        }
        return
    }

    // WORD and AUTO modes: cohesive word-level progressive highlighting
    val activeWord = resolveWordState(wordStarts, wordEnds, p)

    for (line in 0 until result.lineCount) {
        val ls = result.getLineStart(line)
        val le = result.getLineEnd(line, visibleEnd = true)
        val top = result.getLineTop(line)
        val bottom = result.getLineBottom(line)
        val lineH = bottom - top
        if (lineH <= 0f) continue

        if (p <= ls) {
            drawRect(
                color = Color.Black,
                topLeft = Offset(0f, top),
                size = Size(w, lineH),
                blendMode = BlendMode.DstOut
            )
            continue
        }

        if (p >= le) {
            continue
        }

        val rtl = result.getParagraphDirection(ls) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl

        if (activeWord.activeIndex != -1 && activeWord.wordEnd > ls && activeWord.wordStart < le) {
            val kStart = activeWord.wordStart.coerceIn(ls, le)
            val kEnd = activeWord.wordEnd.coerceIn(kStart, le)
            val xA = result.getHorizontalPosition(kStart, usePrimaryDirection = true)
            val xB = result.getHorizontalPosition(kEnd, usePrimaryDirection = true)
            val wordLeft = minOf(xA, xB)
            val wordRight = maxOf(xA, xB)
            val wordW = (wordRight - wordLeft).coerceAtLeast(0f)

            val u = activeWord.fillProgress
            // Smoothstep easing for fluid progressive fill
            val activeWordFill = (u * u * (3f - 2f * u)).coerceIn(0f, 1f)

            // Mask active word with continuous progressive alpha (keeping glyphs 100% intact)
            val eraseAlpha = (1f - activeWordFill).coerceIn(0f, 1f)
            if (eraseAlpha > 0.001f && wordW > 0f) {
                drawRect(
                    color = Color.Black.copy(alpha = eraseAlpha),
                    topLeft = Offset(wordLeft, top),
                    size = Size(wordW, lineH),
                    blendMode = BlendMode.DstOut
                )
            }

            // Erase future unsung portion of this visual line
            if (!rtl) {
                val futureLeft = wordRight
                val futureW = (w - futureLeft).coerceAtLeast(0f)
                if (futureW > 0f) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(futureLeft, top),
                        size = Size(futureW, lineH),
                        blendMode = BlendMode.DstOut
                    )
                }
            } else {
                val futureW = wordLeft.coerceAtLeast(0f)
                if (futureW > 0f) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(0f, top),
                        size = Size(futureW, lineH),
                        blendMode = BlendMode.DstOut
                    )
                }
            }

            // Fluid radiant bloom/glow brush
            val bloomIntensity = sin(Math.PI * u).toFloat().coerceIn(0f, 1f)
            if (bloomIntensity > 0.01f && wordW > 0f) {
                val glowCenter = wordLeft + wordW * u
                val glowRadius = (wordW * 0.9f).coerceAtLeast(featherPx * 2.5f)

                // Ambient luminous halo behind the active word
                val haloRadius = (wordW * 0.85f).coerceAtLeast(lineH)
                val haloCenter = Offset(wordLeft + wordW * 0.5f, (top + bottom) * 0.5f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            highlightColor.copy(alpha = 0.28f * bloomIntensity),
                            highlightColor.copy(alpha = 0.08f * bloomIntensity),
                            Color.Transparent
                        ),
                        center = haloCenter,
                        radius = haloRadius
                    ),
                    radius = haloRadius,
                    center = haloCenter,
                    blendMode = BlendMode.DstOver
                )

                // Luminous radiant bloom over the active word glyphs
                val glyphBloomBrush = Brush.horizontalGradient(
                    0.0f to highlightColor.copy(alpha = 0f),
                    0.3f to highlightColor.copy(alpha = 0.35f * bloomIntensity),
                    0.5f to Color.White.copy(alpha = 0.55f * bloomIntensity),
                    0.7f to highlightColor.copy(alpha = 0.35f * bloomIntensity),
                    1.0f to highlightColor.copy(alpha = 0f),
                    startX = (glowCenter - glowRadius).coerceAtLeast(0f),
                    endX = (glowCenter + glowRadius).coerceAtMost(w)
                )
                drawRect(
                    brush = glyphBloomBrush,
                    topLeft = Offset(wordLeft, top),
                    size = Size(wordW, lineH),
                    blendMode = BlendMode.SrcAtop
                )
            }
        } else {
            // Singing is resting between words on this line
            val pInt = floor(p).toInt().coerceIn(ls, le)
            val splitX = result.getHorizontalPosition(pInt, usePrimaryDirection = true)

            if (!rtl) {
                val futureW = (w - splitX).coerceAtLeast(0f)
                if (futureW > 0f) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(splitX, top),
                        size = Size(futureW, lineH),
                        blendMode = BlendMode.DstOut
                    )
                }
            } else {
                val futureW = splitX.coerceAtLeast(0f)
                if (futureW > 0f) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(0f, top),
                        size = Size(futureW, lineH),
                        blendMode = BlendMode.DstOut
                    )
                }
            }
        }
    }
}

/**
 * A lyric line in three stacked layers that always wrap identically:
 *  1. invisible copy in [reserveStyle] (the widest weight) — decides the size and line breaks;
 *  2. the "at rest" look in [restStyle] / [restColor], fading out as the line arrives;
 *  3. the "current" look: unsung glyphs in [unsungColor] with the sung part in [highlightColor]
 *     ([SmoothLyricLine]), fading in as the line arrives.
 * Layers 2 and 3 use the reserve layer's line breaks, so a narrower weight never wraps
 * differently while the two cross-fade.
 */
@Composable
fun LyricLineLayers(
    text: String,
    reserveStyle: TextStyle,
    restStyle: TextStyle,
    activeStyle: TextStyle,
    restColor: Color,
    unsungColor: Color,
    highlightColor: Color,
    textAlign: TextAlign,
    emphasis: () -> Float,
    sungChars: () -> Float,
    modifier: Modifier = Modifier,
    mode: LyricsHighlightMode = LocalLyricsHighlightMode.current,
    wordLayout: LyricWordLayout? = null,
    /**
     * Adaptive expressive typography: per-word styles at rest and on the current line (same
     * ranges; the current-line ones are heavier). The reserve layer uses the current-line ones,
     * the widest, so both looks share its line breaks.
     */
    restSpans: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
    activeSpans: List<AnnotatedString.Range<SpanStyle>> = emptyList()
) {
    var shown by remember(text) { mutableStateOf(text) }
    val reserveText = remember(text, activeSpans) { styled(text, activeSpans) }
    // Hard breaks swap a space for '\n' (same length), so span ranges stay valid.
    val restText = remember(shown, restSpans) { styled(shown, restSpans) }
    Box(modifier = modifier) {
        Text(
            text = reserveText,
            style = reserveStyle,
            color = Color.Transparent,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = { result ->
                val broken = withHardBreaks(text, result)
                if (broken != shown) shown = broken
            }
        )
        Text(
            text = restText,
            style = restStyle,
            color = restColor,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = (1f - emphasis()).coerceIn(0f, 1f) }
        )
        SmoothLyricLine(
            text = shown,
            style = activeStyle,
            baseColor = unsungColor,
            highlightColor = highlightColor,
            textAlign = textAlign,
            sungChars = sungChars,
            mode = mode,
            wordLayout = wordLayout,
            spans = activeSpans,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = emphasis().coerceIn(0f, 1f) }
        )
    }
}
