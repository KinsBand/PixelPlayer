package com.theveloper.pixelplay.presentation.components.player

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode
import com.theveloper.pixelplay.data.lyrics.highlightedLyricRanges
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import com.theveloper.pixelplay.presentation.components.COVER_IMMERSIVE_TEXT_SCALE
import com.theveloper.pixelplay.presentation.components.LyricsAlignment
import com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefs
import com.theveloper.pixelplay.presentation.components.LyricsFont
import com.theveloper.pixelplay.presentation.components.clusterSyncedWords
import com.theveloper.pixelplay.presentation.components.lyricsBlurRadius
import com.theveloper.pixelplay.presentation.components.lyricsFontFamilyAtWeight
import com.theveloper.pixelplay.presentation.components.rememberEffectiveLyricsFont
import com.theveloper.pixelplay.presentation.components.rememberLyricsDisplayPrefs
import com.theveloper.pixelplay.presentation.components.rememberLyricsImmersiveState
import com.theveloper.pixelplay.presentation.components.resetsLyricsImmersive
import com.theveloper.pixelplay.presentation.components.resolveCurrentLineIndex
import com.theveloper.pixelplay.presentation.components.resolveDisplayLineIndex
import com.theveloper.pixelplay.presentation.components.resolveLineEndTimeMs
import com.theveloper.pixelplay.presentation.components.resolveLineSingEndMs
import com.theveloper.pixelplay.presentation.components.withInstrumentalBreaks
import com.theveloper.pixelplay.presentation.components.sanitizeLyricLineText
import com.theveloper.pixelplay.presentation.components.sanitizeSyncedWords
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Synced lyrics drawn over the (blurred, dimmed) album cover.
 *
 * All display settings come from [rememberLyricsDisplayPrefs] — the same source the full
 * lyrics sheet reads — so alignment, translation, romanization, highlight mode, animated
 * lyrics, blur and font always match what the user picked in the lyrics menu / Settings.
 *
 * Motion: a single rolling stack. `animatedIndex` eases toward the current line index and
 * every visual property (position, scale, alpha, blur, weight, colour) is a function of
 * `distance = index − animatedIndex`, read in the layout/draw phase. A line physically
 * travels next → current → past, bolding in and un-bolding out on the way; nothing is
 * duplicated, so there is no ghosting.
 *
 * Immersive lyrics + auto-hide delay (lyrics menu): after the delay with no touch on the
 * cover, the lyrics grow and the current line takes focus, as in the sheet; a touch (or the
 * menu's "disable immersive once") turns it off again.
 *
 * Any tap opens the full lyrics sheet.
 */
@Composable
fun AlbumCoverLyricsOverlay(
    lyrics: Lyrics?,
    isLoadingLyrics: Boolean,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    onOpenLyricsSheet: () -> Unit,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    isImmersiveTemporarilyDisabled: Boolean = false
) {
    val prefs by rememberLyricsDisplayPrefs()
    val effectiveFont by rememberEffectiveLyricsFont(lyrics, prefs.font)

    val hasSynced = !isLoadingLyrics && !lyrics?.synced.isNullOrEmpty()
    val immersiveState = rememberLyricsImmersiveState(
        prefs = prefs,
        active = hasSynced && !isImmersiveTemporarilyDisabled
    )
    val immersive = immersiveState.isImmersive
    val immersiveTextScale by animateFloatAsState(
        targetValue = if (immersive) COVER_IMMERSIVE_TEXT_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "coverImmersiveTextScale"
    )

    // Position is only read inside lambdas / derived state, never at this level, so the
    // overlay does not recompose on every playback tick.
    // Frame-interpolated position (sync offset applied): the word / letter fill glides between
    // the player's coarse position ticks instead of stepping.
    val lyricsClock = com.theveloper.pixelplay.presentation.components.lyrics.rememberLyricsClock(
        positionFlow = playbackPositionFlow,
        syncOffsetMs = lyricsSyncOffset
    )
    val positionProvider: () -> Long = remember(lyricsClock) { { lyricsClock.now() } }

    val colors = remember(contentColor, accentColor) { resolveCoverLyricsColors(contentColor, accentColor) }
    val openLabel = stringResource(R.string.cover_lyrics_open_label)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .resetsLyricsImmersive(immersiveState)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = openLabel,
                role = Role.Button,
                onClick = onOpenLyricsSheet
            ),
        contentAlignment = Alignment.Center
    ) {
        val baseSize = (maxHeight.value * 0.062f).coerceIn(15f, 26f) * prefs.textSize.multiplier * immersiveTextScale
        val lineStyle = MaterialTheme.typography.titleMedium.copy(
            fontFamily = null,
            fontSize = baseSize.sp,
            lineHeight = (baseSize * 1.28f).sp,
            textMotion = TextMotion.Animated
        )

        when {
            isLoadingLyrics -> CoverStatus(
                showSpinner = true,
                text = stringResource(R.string.lyrics_loading),
                colors = colors,
                style = lineStyle
            )

            lyrics == null || (lyrics.synced.isNullOrEmpty() && lyrics.plain.isNullOrEmpty()) -> CoverStatus(
                showSpinner = false,
                text = stringResource(R.string.lyrics_no_lyrics),
                colors = colors,
                style = lineStyle
            )

            !lyrics.synced.isNullOrEmpty() -> CoverSyncedLyrics(
                lines = lyrics.synced,
                positionProvider = positionProvider,
                prefs = prefs,
                font = effectiveFont,
                style = lineStyle,
                colors = colors,
                immersive = immersive
            )

            else -> CoverPlainLyrics(
                lines = lyrics.plain.orEmpty(),
                alignment = prefs.alignment,
                font = effectiveFont,
                style = lineStyle,
                colors = colors
            )
        }
    }
}

// ── Synced ─────────────────────────────────────────────────────────────────────────────

private const val INTRO_INDEX = -1
private const val WINDOW_RADIUS = 3

/**
 * The rolling synced-lyrics stack. Also used by the camera-cutout island
 * (ui/overlay), which passes its own [windowRadius], row height budget and a seek tap.
 */
@Composable
internal fun CoverSyncedLyrics(
    lines: List<SyncedLine>,
    positionProvider: () -> Long,
    prefs: LyricsDisplayPrefs,
    font: LyricsFont,
    style: TextStyle,
    colors: CoverLyricsColors,
    windowRadius: Int = WINDOW_RADIUS,
    rowMaxHeightFraction: Float = 0.46f,
    horizontalPadding: Dp = 20.dp,
    onLineClick: ((SyncedLine) -> Unit)? = null,
    immersive: Boolean = false
) {
    // Long pauses get a music-note row, like the lyrics sheet; short ones keep the line lit.
    val lines = remember(lines) { withInstrumentalBreaks(lines) }
    // Instrumental intro / outro get the lyrics sheet's music-note bubbles, like blank
    // (instrumental) lines in the middle of the song.
    val hasIntro = lines.first().time > 0 && lines.first().line.isNotBlank()
    val hasOutro = lines.last().line.isNotBlank()
    val outroIndex = lines.size
    val outroStartMs = remember(lines) { resolveLineEndTimeMs(lines.last(), Int.MAX_VALUE) }
    val targetIndex by remember(lines, hasOutro, outroStartMs) {
        derivedStateOf {
            val position = positionProvider()
            if (hasOutro && position >= outroStartMs) outroIndex else resolveDisplayLineIndex(lines, position)
        }
    }
    // Between the end of a line and the start of the next, keep the last line in the
    // centre but dim it, instead of blanking the cover.
    val inGap by remember(lines) {
        derivedStateOf {
            val position = positionProvider()
            !(hasOutro && position >= outroStartMs) &&
                resolveDisplayLineIndex(lines, position) >= 0 && resolveCurrentLineIndex(lines, position) == -1
        }
    }
    val gapDim = animateFloatAsState(
        targetValue = if (inGap) 0.7f else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "coverLyricsGapDim"
    )

    val reducedMotion = rememberReducedMotion()
    val useAnimated by rememberUpdatedState(prefs.useAnimatedLyrics)
    val animatedIndex = remember(lines) { Animatable(targetIndex.toFloat()) }

    LaunchedEffect(targetIndex, lines) {
        val target = targetIndex.toFloat()
        if (reducedMotion || abs(target - animatedIndex.value) > windowRadius) {
            animatedIndex.snapTo(target)
            return@LaunchedEffect
        }
        val duration = if (useAnimated) {
            // Pace the roll to the song: the gap to the next line, bounded for a small canvas.
            val current = lines.getOrNull(targetIndex)?.time ?: 0
            val next = lines.getOrNull(targetIndex + 1)?.time ?: (current + 1000)
            (next - current).coerceIn(250, 900)
        } else {
            350
        }
        animatedIndex.animateTo(target, tween(durationMillis = duration, easing = FastOutSlowInEasing))
    }

    // Immersive mirrors the sheet: the current line scales less (1.02 instead of 1.06) because
    // the text itself is already larger, and the neighbours recede further.
    val profile = when {
        prefs.useAnimatedLyrics && immersive -> ImmersiveAnimatedProfile
        prefs.useAnimatedLyrics -> AnimatedProfile
        immersive -> ImmersiveStaticProfile
        else -> StaticProfile
    }
    val window = remember(targetIndex, lines.size, hasIntro, hasOutro, windowRadius) {
        val low = max(if (hasIntro) INTRO_INDEX else 0, targetIndex - windowRadius)
        val high = min(if (hasOutro) outroIndex else lines.lastIndex, targetIndex + windowRadius)
        (low..high).toList()
    }
    // Same bubbles as the sheet, sized to this canvas's text.
    val noteScale = (style.fontSize.value / 28f).coerceIn(0.45f, 1f)
    val latestPosition by rememberUpdatedState(positionProvider)
    val rowSpacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(horizontal = horizontalPadding),
        content = {
            window.forEach { index ->
                key(index) {
                    val distance: () -> Float = { index - animatedIndex.value }
                    val rowModifier = Modifier
                        .layoutId(index)
                        .fillMaxWidth()
                        .coverRowEffects(
                            distance = distance,
                            profile = profile,
                            prefs = prefs,
                            gapDim = if (index == targetIndex) gapDim else null
                        )
                        .then(
                            if (onLineClick != null && index in lines.indices) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onLineClick(lines[index]) }
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp)

                    if (index == INTRO_INDEX || index == outroIndex || lines[index].line.isBlank()) {
                        // Instrumental: intro, outro or a blank line — animated music notes,
                        // lit while that section is playing.
                        val startMs = when (index) {
                            INTRO_INDEX -> 0L
                            outroIndex -> outroStartMs
                            else -> lines[index].time.toLong()
                        }
                        val endMs = when (index) {
                            INTRO_INDEX -> lines.first().time.toLong()
                            outroIndex -> Long.MAX_VALUE
                            else -> lines.getOrNull(index + 1)?.time?.toLong() ?: Long.MAX_VALUE
                        }
                        val active by remember(startMs, endMs) {
                            derivedStateOf { latestPosition() in startMs until endMs }
                        }
                        Box(rowModifier, contentAlignment = Alignment.Center) {
                            com.theveloper.pixelplay.utils.MusicNoteBubbles(
                                active = active,
                                color = colors.highlight,
                                sizeScale = noteScale
                            )
                        }
                    } else {
                        CoverLyricLine(
                            line = lines[index],
                            nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE,
                            relation = when {
                                index < targetIndex -> LineRelation.PAST
                                index > targetIndex -> LineRelation.UPCOMING
                                else -> LineRelation.CURRENT
                            },
                            positionProvider = positionProvider,
                            distance = distance,
                            prefs = prefs,
                            font = font,
                            style = style,
                            colors = colors,
                            modifier = rowModifier
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxHeight = (constraints.maxHeight * rowMaxHeightFraction).roundToInt().coerceAtLeast(1)
        )
        val placeables = measurables.map { it.measure(childConstraints) }
        val ids = IntArray(measurables.size) { measurables[it].layoutId as Int }
        val tops = IntArray(placeables.size)
        val centers = FloatArray(placeables.size)
        var y = 0
        placeables.forEachIndexed { i, p ->
            tops[i] = y
            centers[i] = y + p.height / 2f
            y += p.height + rowSpacingPx
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            // Read here, not during measure: each animation frame only re-places rows.
            val focus = interpolateFocus(ids, centers, animatedIndex.value)
            val offset = constraints.maxHeight / 2f - focus
            placeables.forEachIndexed { i, p ->
                p.place(0, (tops[i] + offset).roundToInt())
            }
        }
    }
}

/** Centre of the stack at a fractional line index, interpolating between neighbouring rows. */
private fun interpolateFocus(ids: IntArray, centers: FloatArray, index: Float): Float {
    if (ids.isEmpty()) return 0f
    if (index <= ids.first()) return centers.first()
    if (index >= ids.last()) return centers.last()
    for (k in 0 until ids.lastIndex) {
        val a = ids[k]
        val b = ids[k + 1]
        if (index >= a && index <= b) {
            val t = (index - a) / (b - a).toFloat()
            return centers[k] + (centers[k + 1] - centers[k]) * t
        }
    }
    return centers.last()
}

private enum class LineRelation { PAST, CURRENT, UPCOMING }

/** Scale, alpha and blur from the live distance — layer phase only, no recomposition. */
private fun Modifier.coverRowEffects(
    distance: () -> Float,
    profile: CoverMotionProfile,
    prefs: LyricsDisplayPrefs,
    gapDim: State<Float>?
): Modifier = graphicsLayer {
    val d = abs(distance())
    val s = profile.scale(d)
    scaleX = s
    scaleY = s
    transformOrigin = TransformOrigin(prefs.alignment.pivotX, 0.5f)

    var a = profile.alpha(d)
    if (gapDim != null) a *= gapDim.value
    if (prefs.lineBlurActive) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = lyricsBlurRadius(d, prefs.animatedLyricsBlurStrength, 6.dp).toPx()
            renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
        } else {
            // Modifier/RenderEffect blur is unavailable before Android 12: keep the depth
            // cue with a little extra fade instead.
            a *= 1f - 0.15f * d.coerceAtMost(1f)
        }
    } else {
        renderEffect = null
    }
    alpha = a
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverLyricLine(
    line: SyncedLine,
    nextTime: Int,
    relation: LineRelation,
    positionProvider: () -> Long,
    distance: () -> Float,
    prefs: LyricsDisplayPrefs,
    font: LyricsFont,
    style: TextStyle,
    colors: CoverLyricsColors,
    modifier: Modifier
) {
    val alignment = prefs.alignment
    val emphasis: () -> Float = { (1f - abs(distance())).coerceIn(0f, 1f) }

    // Variable fonts animate real weight in steps of 50 (≈7 instances, each cached);
    // other fonts cross-fade Normal ↔ Bold. Either way the layout is reserved at bold
    // weight, so text never reflows while it thickens.
    val weightStep by remember(font) {
        derivedStateOf { if (font.isVariable) quantizeWeight(emphasis()) else 0 }
    }

    val sanitizedLine = remember(line.line) { sanitizeLyricLineText(line.line) }
    val words = remember(line.words) { line.words?.let(::sanitizeSyncedWords)?.takeIf { it.isNotEmpty() } }
    val useWordTiming = words != null && prefs.highlightMode != LyricsHighlightMode.LINE
    // The fill follows the singing (the line itself stays current until the next row).
    val lineEndTime = remember(line, nextTime) { resolveLineSingEndMs(line, nextTime) }

    val sizeFactor = when {
        sanitizedLine.length > 90 -> 0.78f
        sanitizedLine.length > 60 -> 0.88f
        else -> 1f
    }
    val rowStyle = if (sizeFactor == 1f) style else style.copy(
        fontSize = style.fontSize * sizeFactor,
        lineHeight = style.lineHeight * sizeFactor
    )

    Column(modifier = modifier, horizontalAlignment = alignment.horizontalAlignment) {
        // The whole line as one text in three layers (bold reserve, "at rest", "current") that
        // share the same line breaks, so spacing, apostrophes and kerning are natural and
        // nothing reflows while the line thickens. The sung part is painted over the current
        // layer word by word / letter by letter.
        val boldFamily: FontFamily? = lyricsFontFamilyAtWeight(font, 700)
        val restFamily: FontFamily?
        val restWeight: FontWeight
        val activeFamily: FontFamily?
        val activeWeight: FontWeight
        if (font.isVariable) {
            restFamily = lyricsFontFamilyAtWeight(font, weightStep)
            restWeight = FontWeight(weightStep)
            activeFamily = restFamily
            activeWeight = restWeight
        } else {
            restFamily = lyricsFontFamilyAtWeight(font, 400)
            restWeight = FontWeight.Normal
            activeFamily = boldFamily
            activeWeight = FontWeight.Bold
        }
        val wordLayout = remember(sanitizedLine, words) {
            words?.let { com.theveloper.pixelplay.presentation.components.lyrics.buildLyricWordLayout(sanitizedLine, it) }
        }
        val displayText = if (useWordTiming && wordLayout != null) wordLayout.text else sanitizedLine
        val sung = rememberLineFill(
            layout = if (useWordTiming) wordLayout else null,
            words = words,
            textLength = displayText.length,
            relation = relation,
            mode = prefs.highlightMode,
            positionProvider = positionProvider,
            lineEndTime = lineEndTime
        )
        com.theveloper.pixelplay.presentation.components.lyrics.LyricLineLayers(
            text = displayText,
            reserveStyle = rowStyle.copy(fontFamily = boldFamily, fontWeight = FontWeight.Bold),
            restStyle = rowStyle.copy(fontFamily = restFamily, fontWeight = restWeight),
            activeStyle = rowStyle.copy(fontFamily = activeFamily, fontWeight = activeWeight),
            restColor = colors.text,
            unsungColor = colors.unsung,
            highlightColor = colors.highlight,
            textAlign = alignment.textAlign,
            emphasis = emphasis,
            sungChars = sung,
            modifier = Modifier.fillMaxWidth()
        )

        // Romanization and translation: only when the line has one and the lyrics-menu toggle
        // is on. They open and close with the line as it becomes current, so only the centre
        // line carries them and no space is reserved for lines without them.
        val secondaryStyle = rowStyle.copy(
            fontSize = rowStyle.fontSize * 0.75f,
            lineHeight = rowStyle.lineHeight * 0.75f,
            fontWeight = FontWeight.Normal,
            fontFamily = lyricsFontFamilyAtWeight(font, 400)
        )
        val romanization = line.romanization?.takeIf { prefs.showRomanization && it.isNotBlank() }
        val translation = line.translation?.takeIf { prefs.showTranslation && it.isNotBlank() }
        if (romanization != null) {
            Text(
                text = romanization,
                style = secondaryStyle,
                color = colors.text.copy(alpha = 0.85f),
                textAlign = alignment.textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().collapseWith(emphasis).padding(top = 4.dp)
            )
        }
        if (translation != null) {
            Text(
                text = translation,
                style = secondaryStyle,
                color = colors.text.copy(alpha = 0.62f),
                textAlign = alignment.textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().collapseWith(emphasis).padding(top = 2.dp)
            )
        }
    }
}

/**
 * How much of the line is sung, as a continuous character position read in the draw phase.
 * Past lines are fully sung and upcoming lines unsung (their colour then fades with emphasis);
 * only the current line follows the per-frame clock, so the fill glides instead of stepping.
 * Without word timing ([layout] null) the whole line lights up as it arrives.
 */
@Composable
private fun rememberLineFill(
    layout: com.theveloper.pixelplay.presentation.components.lyrics.LyricWordLayout?,
    words: List<SyncedWord>?,
    textLength: Int,
    relation: LineRelation,
    mode: LyricsHighlightMode,
    positionProvider: () -> Long,
    lineEndTime: Long
): () -> Float {
    val full = textLength.toFloat()
    val latestProvider by rememberUpdatedState(positionProvider)
    return remember(layout, words, relation, mode, lineEndTime, textLength) {
        when {
            layout == null || words == null -> { { full } }
            relation == LineRelation.PAST -> { { full } }
            relation == LineRelation.UPCOMING -> { { 0f } }
            else -> {
                {
                    val position = latestProvider()
                    if (position >= lineEndTime) {
                        full
                    } else {
                        com.theveloper.pixelplay.presentation.components.lyrics.sungCharsInLine(
                            layout, words, position, lineEndTime, mode
                        )
                    }
                }
            }
        }
    }
}

/** Height and alpha follow [fraction]; used to open secondary lines with their parent. */
private fun Modifier.collapseWith(fraction: () -> Float): Modifier =
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val f = fraction().coerceIn(0f, 1f)
            val height = (placeable.height * f).roundToInt()
            layout(placeable.width, height) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            val f = fraction().coerceIn(0f, 1f)
            alpha = f * f
            clip = true
        }

private fun quantizeWeight(emphasis: Float): Int {
    val raw = 400f + 300f * emphasis.coerceIn(0f, 1f)
    return ((raw / 50f).roundToInt() * 50).coerceIn(400, 700)
}

// ── Motion profile ─────────────────────────────────────────────────────────────────────

/** Values at distance 0, 1, 2, 3+ from the current line; linear in between. */
private class CoverMotionProfile(private val scales: FloatArray, private val alphas: FloatArray) {
    fun scale(distance: Float) = sample(scales, distance)
    fun alpha(distance: Float) = sample(alphas, distance)

    private fun sample(values: FloatArray, distance: Float): Float {
        val d = distance.coerceIn(0f, (values.size - 1).toFloat())
        val i = d.toInt().coerceAtMost(values.size - 2)
        val t = d - i
        return values[i] + (values[i + 1] - values[i]) * t
    }
}

private val StaticProfile = CoverMotionProfile(
    scales = floatArrayOf(1f, 0.94f, 0.90f, 0.88f),
    alphas = floatArrayOf(1f, 0.45f, 0.25f, 0f)
)

private val AnimatedProfile = CoverMotionProfile(
    scales = floatArrayOf(1.06f, 0.92f, 0.85f, 0.82f),
    alphas = floatArrayOf(1f, 0.55f, 0.25f, 0f)
)

private val ImmersiveStaticProfile = CoverMotionProfile(
    scales = floatArrayOf(1f, 0.94f, 0.90f, 0.88f),
    alphas = floatArrayOf(1f, 0.35f, 0.15f, 0f)
)

private val ImmersiveAnimatedProfile = CoverMotionProfile(
    scales = floatArrayOf(1.02f, 0.92f, 0.85f, 0.82f),
    alphas = floatArrayOf(1f, 0.45f, 0.18f, 0f)
)

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

// ── Plain lyrics and status ────────────────────────────────────────────────────────────

@Composable
private fun CoverPlainLyrics(
    lines: List<String>,
    alignment: LyricsAlignment,
    font: LyricsFont,
    style: TextStyle,
    colors: CoverLyricsColors
) {
    // Unsynced: there is no "current" line, so don't pretend there is one.
    val preview = remember(lines) { lines.map(::sanitizeLyricLineText).filter { it.isNotBlank() }.take(4) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = alignment.horizontalAlignment
    ) {
        preview.forEach { text ->
            Text(
                text = text,
                style = style.copy(fontFamily = lyricsFontFamilyAtWeight(font, 500), fontWeight = FontWeight.Medium),
                color = colors.text.copy(alpha = 0.85f),
                textAlign = alignment.textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.cover_lyrics_not_synced),
            style = style.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            color = colors.text.copy(alpha = 0.6f),
            textAlign = alignment.textAlign,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CoverStatus(
    showSpinner: Boolean,
    text: String,
    colors: CoverLyricsColors,
    style: TextStyle
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = colors.highlight,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
        } else {
            Icon(
                painter = painterResource(R.drawable.rounded_lyrics_24),
                contentDescription = null,
                tint = colors.text.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = text,
            style = style.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            color = colors.text.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}

// ── Colour ─────────────────────────────────────────────────────────────────────────────

internal data class CoverLyricsColors(val text: Color, val unsung: Color, val highlight: Color)

/**
 * The lyrics sit on blurred artwork under a 52 % black scrim. Its brightest realistic case
 * (a white cover) comes out around mid-grey, so colours are checked against that: the text
 * colour falls back to white and the accent is lightened until each reaches 3:1 (the WCAG
 * threshold for large text).
 */
internal fun resolveCoverLyricsColors(content: Color, accent: Color): CoverLyricsColors {
    val worstBackground = Color(0xFF7A7A7A)
    val text = if (contrast(content, worstBackground) >= 3f) content else Color.White
    var highlight = accent
    var t = 0f
    while (contrast(highlight, worstBackground) < 3f && t < 1f) {
        t = (t + 0.15f).coerceAtMost(1f)
        highlight = lerp(accent, Color.White, t)
    }
    return CoverLyricsColors(text = text, unsung = text.copy(alpha = 0.5f), highlight = highlight)
}

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}
