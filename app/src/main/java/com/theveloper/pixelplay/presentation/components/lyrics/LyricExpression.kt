package com.theveloper.pixelplay.presentation.components.lyrics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.theveloper.pixelplay.data.database.EnrichmentDao
import com.theveloper.pixelplay.data.database.TrackAnalysisEntity
import com.theveloper.pixelplay.data.lyrics.SongSectionKind
import com.theveloper.pixelplay.data.lyrics.SongStructure
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Adaptive expressive typography.
 *
 * With Expressive typography on, every song gets its own typographic "voice", and every word
 * inside it gets its own weight, size and spacing:
 *
 *  Song level (from the on-device audio analysis when the song has one):
 *   - energy     → heavier, tighter type for high-energy songs, lighter and airier for calm ones;
 *   - mood       → valence (and minor keys) sets how much words may swing: bright songs bounce,
 *                  dark songs stay restrained;
 *   - dynamics   → how much the loudness moves over the song scales every per-word effect;
 *   - tempo      → slow songs let held notes stretch further, fast songs keep words compact.
 *
 *  Word level:
 *   - loudness   → the song's loudness envelope at that word (louder than the song usually is
 *                  → bolder and slightly bigger; quieter → lighter);
 *   - held notes → a word sung much longer than the song's typical word is spread out (tracking)
 *                  and gets a little extra weight;
 *   - section    → chorus / hook / drop lines lift, intros / outros settle;
 *   - the words  → ALL CAPS or "!" push harder, (backing vocals) in brackets sit back.
 *
 * Songs without an analysis still adapt: timing, sections and the words themselves drive it.
 * Everything is kept subtle enough that lines stay readable and never jump around: effects are
 * clamped, weights are snapped to 100 steps, and the current line is always drawn a step heavier
 * than its words' resting weights (see activeLyricWeight).
 */

@Immutable
class SongExpressionProfile internal constructor(
    val songId: String,
    /** 0..1 */
    val energy: Float,
    /** 0..1 (sad / tense → happy / bright). */
    val valence: Float,
    val bpm: Int?,
    /** 0..1: how much the loudness moves through the song. */
    val dynamics: Float,
    /** Song-level weight shift in font-weight units (multiples of 100). */
    val baseWeightShift: Int,
    /** Song-level letter spacing (em). */
    val baseTracking: Float,
    /** How strongly per-word effects apply (0.4..1.4). */
    val swing: Float,
    /** How far held words may stretch (0.6..1.3). */
    val stretch: Float,
    private val envelope: FloatArray?,
    private val durationMs: Long,
    private val envelopeMid: Float,
    private val envelopeHigh: Float,
    val typicalWordMs: Float,
    private val structure: SongStructure?,
) {
    /** Loudness at [ms] relative to this song: -1 (quiet for this song) .. 1 (its loudest parts). */
    fun intensityAt(ms: Long): Float {
        val env = envelope ?: return 0f
        if (durationMs <= 0 || env.isEmpty()) return 0f
        val center = ((ms.toDouble() / durationMs) * env.size).toInt()
        // ~±120 ms window so a single transient doesn't dominate.
        val half = ((120.0 / durationMs) * env.size).toInt().coerceAtLeast(1)
        var sum = 0f
        var n = 0
        for (i in (center - half)..(center + half)) {
            if (i in env.indices) { sum += env[i]; n++ }
        }
        if (n == 0) return 0f
        val v = sum / n
        val span = (envelopeHigh - envelopeMid).coerceAtLeast(0.02f)
        return ((v - envelopeMid) / span).coerceIn(-1f, 1f)
    }

    fun sectionAt(ms: Long): SongSectionKind? {
        val s = structure ?: return null
        val i = s.indexAt(ms)
        return s.sections.getOrNull(i)?.kind
    }
}

/** The current song's profile; null when expressive typography (or adaptation) is off. */
val LocalLyricExpression = compositionLocalOf<SongExpressionProfile?> { null }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LyricExpressionEntryPoint {
    fun enrichmentDao(): EnrichmentDao
}

/** Builds (and caches per song) the expression profile. Null while [enabled] is false. */
@Composable
fun rememberSongExpressionProfile(
    song: Song?,
    lyrics: Lyrics?,
    structure: SongStructure?,
    durationMs: Long,
    enabled: Boolean,
): State<SongExpressionProfile?> {
    val context = LocalContext.current.applicationContext
    return produceState<SongExpressionProfile?>(
        initialValue = if (enabled && song != null) LyricExpressionEngine.cached(song.id, structure) else null,
        song?.id, lyrics, structure, durationMs, enabled
    ) {
        if (!enabled || song == null) {
            value = null
            return@produceState
        }
        value = LyricExpressionEngine.load(context, song, lyrics, structure, durationMs)
    }
}

object LyricExpressionEngine {
    private val cache = ConcurrentHashMap<String, SongExpressionProfile>()

    internal fun cached(songId: String, structure: SongStructure?): SongExpressionProfile? =
        cache[cacheKey(songId, structure)]

    private fun cacheKey(songId: String, structure: SongStructure?) =
        "$songId:${structure?.lyricsFingerprint.orEmpty()}:${structure?.sections?.size ?: 0}"

    suspend fun load(
        context: Context,
        song: Song,
        lyrics: Lyrics?,
        structure: SongStructure?,
        durationMs: Long,
    ): SongExpressionProfile {
        val key = cacheKey(song.id, structure)
        cache[key]?.let { return it }
        val analysis = song.id.toLongOrNull()?.let { id ->
            withContext(Dispatchers.IO) {
                runCatching {
                    EntryPointAccessors.fromApplication(context, LyricExpressionEntryPoint::class.java)
                        .enrichmentDao()
                        .getAnalysis(id)
                }.getOrNull()
            }
        }
        val profile = withContext(Dispatchers.Default) {
            build(song, analysis, lyrics, structure, durationMs.takeIf { it > 0 } ?: song.duration)
        }
        if (cache.size > 48) cache.clear()
        cache[key] = profile
        return profile
    }

    /** Pure, testable profile builder. */
    fun build(
        song: Song,
        analysis: TrackAnalysisEntity?,
        lyrics: Lyrics?,
        structure: SongStructure?,
        durationMs: Long,
    ): SongExpressionProfile {
        val envelope = analysis?.waveform?.takeIf { it.size >= 16 }?.let { bytes ->
            FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
        }
        val sorted = envelope?.filter { it > 0.01f }?.sorted()
        val mid = sorted?.percentile(0.5f) ?: 0.5f
        val high = sorted?.percentile(0.92f) ?: 0.9f
        val dynamics = sorted?.let { s ->
            val mean = s.average().toFloat()
            val sd = sqrt(s.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / s.size.coerceAtLeast(1))
            (sd / 0.18f).coerceIn(0f, 1f)
        } ?: 0.5f

        val minorKey = analysis?.musicKey?.let { k ->
            val lk = k.lowercase()
            lk.contains("minor") || lk.endsWith("m") && !lk.endsWith("maj")
        } ?: false
        val energy = (analysis?.energy ?: sorted?.let { (it.average().toFloat() / 0.8f).coerceIn(0f, 1f) } ?: 0.5f)
            .coerceIn(0f, 1f)
        val valence = (analysis?.valence ?: if (minorKey) 0.38f else 0.5f).coerceIn(0f, 1f)
        val bpm = analysis?.bpm

        val baseWeightShift = when {
            energy >= 0.72f -> 100
            energy <= 0.32f -> -100
            else -> 0
        }
        val baseTracking = when {
            energy <= 0.35f && valence <= 0.45f -> 0.012f   // calm, sombre: airy
            energy <= 0.35f -> 0.006f
            energy >= 0.75f -> -0.006f                       // driving: tight
            else -> 0f
        }
        val swing = ((0.55f + dynamics * 0.55f) * (0.75f + valence * 0.5f)).coerceIn(0.4f, 1.4f)
        val stretch = when {
            bpm == null -> 1f
            bpm < 85 -> 1.3f
            bpm < 110 -> 1.1f
            bpm > 150 -> 0.6f
            bpm > 125 -> 0.8f
            else -> 1f
        }

        val wordDurations = lyrics?.synced.orEmpty().flatMap { line ->
            line.words.orEmpty().mapNotNull { w -> w.endTime?.let { (it - w.time).takeIf { d -> d in 40..8000 } } }
        }.sorted()
        val typicalWordMs = if (wordDurations.size >= 8) wordDurations[wordDurations.size / 2].toFloat() else 330f

        return SongExpressionProfile(
            songId = song.id,
            energy = energy,
            valence = valence,
            bpm = bpm,
            dynamics = dynamics,
            baseWeightShift = baseWeightShift,
            baseTracking = baseTracking,
            swing = swing,
            stretch = stretch,
            envelope = envelope,
            durationMs = durationMs,
            envelopeMid = mid,
            envelopeHigh = high,
            typicalWordMs = typicalWordMs,
            structure = structure,
        )
    }

    private fun List<Float>.percentile(p: Float): Float =
        if (isEmpty()) 0.5f else this[((size - 1) * p).roundToInt().coerceIn(0, size - 1)]
}

/** Per-word looks for one line: resting spans and current-line spans (same ranges, heavier). */
@Immutable
class LineExpression(
    val restSpans: List<AnnotatedString.Range<SpanStyle>>,
    val activeSpans: List<AnnotatedString.Range<SpanStyle>>,
) {
    companion object {
        val None = LineExpression(emptyList(), emptyList())
    }
}

/**
 * Word ranges + times for [text]. Uses the synced words when their layout maps onto the text,
 * otherwise splits on spaces and spreads the line's time across words by length.
 */
internal fun expressionWords(
    text: String,
    line: SyncedLine,
    lineEndMs: Long,
    words: List<SyncedWord>?,
    layout: LyricWordLayout?,
): List<ExprWord> {
    if (words != null && layout != null && layout.text == text && words.size == layout.starts.size) {
        return words.indices.mapNotNull { i ->
            val s = layout.starts[i]
            val e = layout.ends[i]
            if (e <= s) return@mapNotNull null
            val start = words[i].time.toLong()
            val end = (words[i].endTime?.toLong() ?: words.getOrNull(i + 1)?.time?.toLong() ?: lineEndMs)
                .coerceAtLeast(start + 1)
            ExprWord(s, e, start, end)
        }
    }
    // Estimate: split on whitespace, time ∝ letters.
    val ranges = ArrayList<IntArray>()
    var i = 0
    while (i < text.length) {
        while (i < text.length && text[i].isWhitespace()) i++
        val s = i
        while (i < text.length && !text[i].isWhitespace()) i++
        if (i > s) ranges += intArrayOf(s, i)
    }
    if (ranges.isEmpty()) return emptyList()
    val lineStart = line.time.toLong()
    val total = (lineEndMs - lineStart).coerceAtLeast(ranges.size * 120L)
    val letters = ranges.sumOf { (it[1] - it[0]).coerceAtLeast(1) }.toFloat()
    var t = lineStart.toFloat()
    return ranges.map { r ->
        val d = total * ((r[1] - r[0]).coerceAtLeast(1) / letters)
        val w = ExprWord(r[0], r[1], t.toLong(), (t + d).toLong())
        t += d
        w
    }
}

internal class ExprWord(val start: Int, val end: Int, val startMs: Long, val endMs: Long)

/**
 * The per-word styles for a line. [restWeight] is the user's resting weight; the current-line
 * spans are always a step heavier ([activeFor]) so the hierarchy holds word by word.
 */
internal fun buildLineExpression(
    text: String,
    words: List<ExprWord>,
    lineStartMs: Long,
    profile: SongExpressionProfile,
    restWeight: FontWeight,
    activeFor: (FontWeight?) -> FontWeight,
): LineExpression {
    if (words.isEmpty() || text.isBlank()) return LineExpression.None
    val section = when (profile.sectionAt(lineStartMs)) {
        SongSectionKind.CHORUS, SongSectionKind.HOOK, SongSectionKind.DROP -> 0.35f
        SongSectionKind.POST_CHORUS -> 0.25f
        SongSectionKind.PRE_CHORUS -> 0.15f
        SongSectionKind.BRIDGE, SongSectionKind.BREAKDOWN -> 0.1f
        SongSectionKind.INTRO, SongSectionKind.OUTRO -> -0.2f
        else -> 0f
    }
    val bracketDepth = IntArray(text.length)
    var depth = 0
    for (i in text.indices) {
        if (text[i] == '(' || text[i] == '[') depth++
        bracketDepth[i] = depth
        if ((text[i] == ')' || text[i] == ']') && depth > 0) depth--
    }

    val rest = ArrayList<AnnotatedString.Range<SpanStyle>>(words.size)
    val active = ArrayList<AnnotatedString.Range<SpanStyle>>(words.size)
    for (w in words) {
        val token = text.substring(w.start, w.end)
        val letters = token.filter { it.isLetter() }
        val cue = when {
            (bracketDepth.getOrNull(w.start) ?: 0) > 0 -> -0.6f                              // backing vocal
            letters.length >= 2 && letters.all { it.isUpperCase() } -> 0.6f                 // SHOUTED
            token.contains('!') -> 0.4f
            else -> 0f
        }
        val intensity = profile.intensityAt((w.startMs + w.endMs) / 2)
        val held = (((w.endMs - w.startMs) / profile.typicalWordMs) - 1.4f).coerceIn(0f, 2f) / 2f
        val expr = (0.5f * intensity + 0.2f * held + section + 0.25f * cue).coerceIn(-1f, 1f) * profile.swing

        // Explicit cues get a guaranteed step so they read even after snapping to 100s.
        val cueStep = when {
            cue < 0f -> -100f   // (backing vocals) sit back
            cue >= 0.6f -> 100f // SHOUTED
            else -> 0f
        }
        val weight = snapWeight(restWeight.weight + profile.baseWeightShift + (expr * 150f) + held * 60f + cueStep)
        val restW = FontWeight(weight)
        val scale = (1f + expr * 0.07f + if (cue < 0f) -0.05f else 0f).coerceIn(0.9f, 1.09f)
        val tracking = (profile.baseTracking + held * 0.05f * profile.stretch - if (expr > 0.5f) 0.004f else 0f)
            .coerceIn(-0.01f, 0.07f)

        rest += AnnotatedString.Range(
            SpanStyle(fontWeight = restW, fontSize = scale.em, letterSpacing = tracking.em),
            w.start, w.end
        )
        active += AnnotatedString.Range(
            SpanStyle(fontWeight = activeFor(restW), fontSize = scale.em, letterSpacing = tracking.em),
            w.start, w.end
        )
    }
    return LineExpression(rest, active)
}

private fun snapWeight(raw: Float): Int = ((raw / 100f).roundToInt() * 100).coerceIn(200, 800)
