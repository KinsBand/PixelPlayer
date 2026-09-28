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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
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
 * Songs without an analysis still adapt: the genre tag, how fast and how densely the words come
 * (lyric pacing), shouted / exclaimed lines and the song's sections stand in for the audio.
 *
 * The voice is visible at three levels:
 *   - the whole song: size, line height, tracking and base weight of every line
 *     ([SongExpressionProfile.applyTo]);
 *   - each word: weight, size and spacing spans ([buildLineExpression]);
 *   - motion: how far words and letters lift while sung and how bouncy a new line's arrival is
 *     ([SongExpressionProfile.motion], read by the lyric line renderer).
 * Effects stay bounded so lines remain readable: weights snap to 100 steps inside 200..800 and the
 * current line is always drawn a step heavier than its words' resting weights (activeLyricWeight).
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
    /** Whole-song size multiplier (0.94..1.1): driving songs read bigger, calm ones smaller. */
    val sizeScale: Float = 1f,
    /** Whole-song line-height multiplier: calm songs breathe, dense songs pack tighter. */
    val lineHeightScale: Float = 1f,
    /** How this song's words and lines move (see [LyricMotion]). */
    val motion: LyricMotion = LyricMotion.Default,
) {
    /** The song's voice applied to a whole line style: size, line height, tracking, weight. */
    fun applyTo(style: TextStyle): TextStyle {
        val size = style.fontSize
        val lineHeight = style.lineHeight
        val weight = style.fontWeight?.weight ?: FontWeight.Normal.weight
        return style.copy(
            fontSize = if (size.isSp) size * sizeScale else size,
            lineHeight = if (lineHeight.isSp) lineHeight * (sizeScale * lineHeightScale) else lineHeight,
            letterSpacing = if (style.letterSpacing.isUnspecified || style.letterSpacing.value == 0f) {
                baseTracking.em
            } else style.letterSpacing,
            fontWeight = FontWeight(snapWeight((weight + baseWeightShift).toFloat()))
        )
    }

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

/**
 * How lyrics move while they're sung. Read by the line renderer every frame, so it only holds
 * plain numbers. [Default] is the look without adaptive typography; each song's profile derives
 * its own (calm songs float gently, driving songs punch and bounce).
 */
@Immutable
data class LyricMotion(
    /** Peak lift of the word being sung, as a fraction of its line height. */
    val wordLift: Float,
    /** Peak extra scale of the word being sung (0.05 = +5 %). */
    val wordScale: Float,
    /** Peak lift of the letter being sung, as a fraction of its line height. */
    val letterLift: Float,
    /** How many letters behind the cursor are still settling back down. */
    val letterWave: Float,
    /** Damping of a new line's arrival spring (lower = bouncier). */
    val lineDamping: Float,
    /** Stiffness of a new line's arrival spring (higher = snappier). */
    val lineStiffness: Float,
    /** How far below its resting place a new line starts (dp). */
    val lineRise: Float,
) {
    companion object {
        val Default = LyricMotion(
            wordLift = 0.07f,
            wordScale = 0.045f,
            letterLift = 0.09f,
            letterWave = 2.5f,
            lineDamping = 0.62f,
            lineStiffness = 380f,
            lineRise = 10f,
        )

        /** Reduced motion: no lifts, no bounce. */
        val Still = LyricMotion(0f, 0f, 0f, 0f, 1f, 600f, 0f)
    }
}

/** The motion lyric lines use; the lyrics sheet provides the song's own. */
val LocalLyricMotion = compositionLocalOf { LyricMotion.Default }

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

        val stats = lyricStats(lyrics)
        val genre = genreVoice(song.genre)
        // A small, stable per-song nudge so two songs of the same genre and pace still differ.
        val seed = ((song.id.hashCode() and 0x7fffffff) % 1000) / 1000f - 0.5f

        // Energy: the analysis when there is one; otherwise genre + how dense / shouty the words
        // are + the loudness envelope's average.
        val estimatedEnergy = run {
            var e = genre?.energy ?: 0.5f
            stats?.let { st ->
                // ~1 word/s is a ballad, ~3.5 words/s is rap / punk.
                val pace = ((st.wordsPerSecond - 1f) / 2.5f).coerceIn(0f, 1f)
                e = e * 0.55f + pace * 0.45f
                e += st.shoutRatio * 0.35f
            }
            sorted?.let { e = e * 0.7f + (it.average().toFloat() / 0.8f).coerceIn(0f, 1f) * 0.3f }
            (e + seed * 0.08f).coerceIn(0f, 1f)
        }
        val energy = (analysis?.energy ?: estimatedEnergy).coerceIn(0f, 1f)
        val estimatedValence = ((genre?.valence ?: 0.5f) - (if (minorKey) 0.12f else 0f) +
            (stats?.exclaimRatio ?: 0f) * 0.2f + seed * 0.06f).coerceIn(0f, 1f)
        val valence = (analysis?.valence ?: estimatedValence).coerceIn(0f, 1f)
        val bpm = analysis?.bpm

        val baseWeightShift = (((energy - 0.5f) * 450f) / 100f).roundToInt().coerceIn(-2, 2) * 100
        val baseTracking = when {
            energy <= 0.35f && valence <= 0.45f -> 0.022f   // calm, sombre: airy
            energy <= 0.35f -> 0.012f
            energy >= 0.8f -> -0.014f                        // driving: tight
            energy >= 0.65f -> -0.007f
            else -> 0.002f
        }
        val swing = ((0.7f + dynamics * 0.6f) * (0.8f + valence * 0.4f)).coerceIn(0.5f, 1.5f)

        val wordDurations = lyrics?.synced.orEmpty().flatMap { line ->
            line.words.orEmpty().mapNotNull { w -> w.endTime?.let { (it - w.time).takeIf { d -> d in 40..8000 } } }
        }.sorted()
        val typicalWordMs = when {
            wordDurations.size >= 8 -> wordDurations[wordDurations.size / 2].toFloat()
            stats != null && stats.wordsPerSecond > 0f -> (1000f / stats.wordsPerSecond).coerceIn(160f, 900f)
            else -> 330f
        }
        val stretch = when {
            bpm != null && bpm < 85 -> 1.3f
            bpm != null && bpm < 110 -> 1.1f
            bpm != null && bpm > 150 -> 0.6f
            bpm != null && bpm > 125 -> 0.8f
            bpm != null -> 1f
            // No tempo: long typical words mean a slow song.
            typicalWordMs > 480f -> 1.25f
            typicalWordMs < 240f -> 0.75f
            else -> 1f
        }

        val sizeScale = (0.95f + energy * 0.12f + (stats?.shoutRatio ?: 0f) * 0.04f).coerceIn(0.94f, 1.1f)
        val lineHeightScale = when {
            energy <= 0.35f -> 1.08f
            stats != null && stats.wordsPerLine >= 9f -> 0.95f
            energy >= 0.75f -> 0.97f
            else -> 1f
        }

        // Motion: energy sets how far words jump, valence how bouncy lines land, tempo how
        // snappy. Calm, sad songs float; bright, driving songs punch.
        val tempoSnap = when {
            bpm != null -> ((bpm - 70f) / 90f).coerceIn(0f, 1f)
            else -> ((350f - typicalWordMs) / 250f + 0.5f).coerceIn(0f, 1f)
        }
        val motion = LyricMotion(
            wordLift = (0.04f + energy * 0.08f) * (0.8f + dynamics * 0.4f),
            wordScale = 0.02f + energy * 0.05f + valence * 0.015f,
            letterLift = 0.05f + energy * 0.07f,
            letterWave = 1.5f + (1f - tempoSnap) * 2.5f,
            lineDamping = (0.8f - valence * 0.25f - energy * 0.1f).coerceIn(0.45f, 0.85f),
            lineStiffness = 220f + tempoSnap * 380f,
            lineRise = 6f + energy * 10f,
        )

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
            sizeScale = sizeScale,
            lineHeightScale = lineHeightScale,
            motion = motion,
        )
    }

    /** Pacing and emphasis read from the lyrics themselves. */
    internal class LyricStats(
        val wordsPerSecond: Float,
        val wordsPerLine: Float,
        /** Share of words written in capitals (2+ letters). */
        val shoutRatio: Float,
        /** Share of lines with "!". */
        val exclaimRatio: Float,
    )

    internal fun lyricStats(lyrics: Lyrics?): LyricStats? {
        val lines = lyrics?.synced?.filter { it.line.isNotBlank() }?.takeIf { it.size >= 4 } ?: return null
        var words = 0
        var shouted = 0
        var exclaimed = 0
        var singingMs = 0L
        for ((i, line) in lines.withIndex()) {
            val tokens = line.line.split(' ', '\t').filter { t -> t.any(Char::isLetter) }
            words += tokens.size
            shouted += tokens.count { t ->
                val letters = t.filter(Char::isLetter)
                letters.length >= 2 && letters.all(Char::isUpperCase)
            }
            if (line.line.contains('!')) exclaimed++
            val next = lines.getOrNull(i + 1)?.time ?: (line.time + 4_000)
            // Cap each line's share so long instrumental gaps don't read as slow singing.
            singingMs += (next - line.time).toLong().coerceIn(0L, 8_000L)
        }
        if (words == 0 || singingMs <= 0L) return null
        // If every line is shouted it's a styling choice, not emphasis.
        val shoutRatio = (shouted.toFloat() / words).let { if (it > 0.8f) 0.3f else it }
        return LyricStats(
            wordsPerSecond = words / (singingMs / 1000f),
            wordsPerLine = words.toFloat() / lines.size,
            shoutRatio = shoutRatio.coerceIn(0f, 1f),
            exclaimRatio = exclaimed.toFloat() / lines.size,
        )
    }

    internal class GenreVoice(val energy: Float, val valence: Float)

    /** Rough energy / mood of a genre tag, for songs that haven't been analysed. */
    internal fun genreVoice(genre: String?): GenreVoice? {
        val g = genre?.lowercase()?.trim()?.takeIf { it.isNotEmpty() && it != "unknown" && it != "<unknown>" }
            ?: return null
        fun has(vararg keys: String) = keys.any { g.contains(it) }
        return when {
            has("metal", "hardcore", "punk", "grind", "thrash") -> GenreVoice(0.92f, 0.35f)
            has("drum and bass", "dnb", "dubstep", "hardstyle", "techno", "trance") -> GenreVoice(0.88f, 0.55f)
            has("edm", "electro", "house", "dance") -> GenreVoice(0.8f, 0.7f)
            has("rap", "hip hop", "hip-hop", "trap", "drill", "grime") -> GenreVoice(0.75f, 0.5f)
            has("rock", "grunge", "garage") -> GenreVoice(0.75f, 0.5f)
            has("reggaeton", "latin", "funk", "disco", "afro") -> GenreVoice(0.72f, 0.8f)
            has("k-pop", "kpop", "j-pop", "jpop", "pop") -> GenreVoice(0.65f, 0.72f)
            has("r&b", "rnb", "soul") -> GenreVoice(0.5f, 0.58f)
            has("country", "folk", "bluegrass") -> GenreVoice(0.45f, 0.6f)
            has("indie", "alternative") -> GenreVoice(0.55f, 0.48f)
            has("blues") -> GenreVoice(0.42f, 0.35f)
            has("jazz", "bossa", "swing") -> GenreVoice(0.38f, 0.6f)
            has("lofi", "lo-fi", "chill", "ambient", "downtempo") -> GenreVoice(0.25f, 0.5f)
            has("classical", "orchestral", "soundtrack", "score", "piano") -> GenreVoice(0.25f, 0.45f)
            has("ballad", "acoustic", "singer-songwriter") -> GenreVoice(0.3f, 0.42f)
            has("emo", "sad", "slowcore") -> GenreVoice(0.4f, 0.22f)
            else -> null
        }
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
 * The per-word styles for a line. [restWeight] is the line's resting weight (the user's weight
 * with the song's base shift already applied by [SongExpressionProfile.applyTo]); the current-line
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
        val expr = (0.55f * intensity + 0.25f * held + section + 0.3f * cue).coerceIn(-1f, 1f) * profile.swing

        // Explicit cues get a guaranteed step so they read even after snapping to 100s.
        val cueStep = when {
            cue < 0f -> -100f   // (backing vocals) sit back
            cue >= 0.6f -> 100f // SHOUTED
            else -> 0f
        }
        val weight = snapWeight(restWeight.weight + (expr * 260f) + held * 90f + cueStep)
        val restW = FontWeight(weight)
        val scale = (1f + expr * 0.12f + if (cue < 0f) -0.08f else 0f).coerceIn(0.86f, 1.16f)
        val tracking = (profile.baseTracking + held * 0.07f * profile.stretch - if (expr > 0.5f) 0.006f else 0f)
            .coerceIn(-0.02f, 0.09f)

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
