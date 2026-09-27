package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
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
        mode == LyricsHighlightMode.WORD -> null
        !word.phonemes.isNullOrEmpty() -> word.phonemes
        mode == LyricsHighlightMode.PHONEME ->
            LetterTimingEstimator.estimate(word.copy(endTime = end.toInt())).takeIf { it.isNotEmpty() }
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
 * One lyric line as whole text (natural spacing and kerning — no per-word boxes), with the sung
 * part painted over a dim copy. The unsung part is erased per visual line with a soft edge that
 * moves every frame, following the real glyph positions, so the fill works across wrapped lines.
 */
@Composable
fun SmoothLyricLine(
    text: String,
    style: TextStyle,
    baseColor: Color,
    highlightColor: Color,
    textAlign: TextAlign,
    sungChars: () -> Float,
    modifier: Modifier = Modifier
) {
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val featherPx = remember(style, density) {
        with(density) { (style.fontSize.takeIf { it.isSp }?.toPx() ?: 16.dp.toPx()) * 0.55f }
    }
    val length = text.length
    Box(modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = baseColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = { layout.value = it }
        )
        Text(
            text = text,
            style = style,
            color = highlightColor,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val p = sungChars()
                    if (p <= 0f) return@drawWithContent
                    drawContent()
                    if (p >= length) return@drawWithContent
                    val result = layout.value ?: return@drawWithContent
                    val w = size.width
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
                    }
                }
        )
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
    modifier: Modifier = Modifier
) {
    var shown by remember(text) { mutableStateOf(text) }
    Box(modifier = modifier) {
        Text(
            text = text,
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
            text = shown,
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
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = emphasis().coerceIn(0f, 1f) }
        )
    }
}
