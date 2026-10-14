package com.theveloper.pixelplay.presentation.components.lyrics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.EnrichmentDao
import com.theveloper.pixelplay.data.database.TrackAnalysisEntity
import com.theveloper.pixelplay.data.lyrics.SongSectionKind
import com.theveloper.pixelplay.data.lyrics.SongStructure
import com.theveloper.pixelplay.data.lyrics.voice.SongVoice
import com.theveloper.pixelplay.data.lyrics.voice.SongVoiceAnalyzer
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
 *  Song level:
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
 * Where the song's facts come from, best first — so EVERY song gets a voice, local or streamed:
 *   1. the library analysis (track_analysis) of a local song, or of the downloaded copy of a
 *      streamed one: energy, valence, tempo, key;
 *   2. the song's own measured voice ([SongVoice]): the whole-song loudness envelope, energy,
 *      dynamics, tempo and key, measured once from the local file, the download or the stream
 *      proxy's complete copy of a streamed song;
 *   3. metadata carried by the song (mix intelligence energy/valence/mood, tempo, key/mode);
 *   4. estimates: genre / sub-genre / tags, lyric pacing (synced OR plain lyrics), shouted and
 *      exclaimed lines, the song's sections.
 *
 * The voice is visible at three levels:
 *   - the whole song: size, line height, tracking and base weight of every line
 *     ([SongExpressionProfile.applyTo]);
 *   - each word: weight, size and spacing spans ([buildLineExpression], or
 *     [buildUntimedLineExpression] for plain, unsynced lyrics);
 *   - motion: how far words and letters lift while sung and how bouncy a new line's arrival is
 *     ([SongExpressionProfile.motion], read by the lyric line renderer).
 * Effects stay bounded so lines remain readable: weights snap to 100 steps inside 200..800 and the
 * current line is always drawn a step heavier than its words' resting weights (activeLyricWeight).
 *
 * Stability: one song play keeps one voice on every surface (sheet, split view, island). A voice
 * measured after the song started only replaces the estimate if it lands within
 * [LyricExpressionEngine.UPGRADE_WINDOW_MS], so lines never re-wrap in the middle of a song; later
 * results apply from the next play.
 */

/** Where a profile's song-level facts came from (best source used). */
enum class ExpressionSource { LIBRARY_ANALYSIS, MEASURED_VOICE, METADATA, ESTIMATE }

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
    /** Loudness envelope, sample 0 = position 0; covers [envelopeSpanMs]. */
    private val envelope: FloatArray?,
    private val envelopeSpanMs: Long,
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
    /** Best source of the song-level facts (diagnostics). */
    val source: ExpressionSource = ExpressionSource.ESTIMATE,
) {
    /** True when per-word loudness follows the actual audio. */
    val hasEnvelope: Boolean get() = envelope != null

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

    /**
     * Loudness at [ms] relative to this song: -1 (quiet for this song) .. 1 (its loudest parts).
     * 0 where the envelope has no data (no audio measured, or past what was analysed).
     */
    fun intensityAt(ms: Long): Float {
        val env = envelope ?: return 0f
        if (envelopeSpanMs <= 0 || env.isEmpty() || ms < 0 || ms >= envelopeSpanMs) return 0f
        val center = ((ms.toDouble() / envelopeSpanMs) * env.size).toInt()
        // ~±120 ms window so a single transient doesn't dominate.
        val half = ((120.0 / envelopeSpanMs) * env.size).toInt().coerceAtLeast(1)
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
    fun cloudSongDao(): CloudSongDao
}

/**
 * Builds (and caches per song) the expression profile. Null while [enabled] is false.
 *
 * Every surface (lyrics sheet, split view, camera island) calls this for the current song and gets
 * the same profile: missing inputs ([structure] null, [durationMs] unknown) are filled from what
 * other surfaces already supplied for this play. It also asks [SongVoiceAnalyzer] to measure the
 * song, and picks the measured voice up if it arrives early in the song.
 */
@Composable
fun rememberSongExpressionProfile(
    song: Song?,
    lyrics: Lyrics?,
    structure: SongStructure?,
    durationMs: Long,
    enabled: Boolean,
): State<SongExpressionProfile?> {
    val context = LocalContext.current.applicationContext
    // Bucketed so a duration that trickles in (streams) doesn't rebuild every frame.
    val durationKey = if (durationMs > 0) durationMs / 1000 else 0L
    val initial = remember(song?.id, lyrics, structure, enabled) {
        if (enabled && song != null) LyricExpressionEngine.cached(song.id, lyrics, structure) else null
    }
    return produceState(
        initialValue = initial,
        song?.id, lyrics, structure, durationKey, enabled
    ) {
        if (!enabled || song == null) {
            value = null
            return@produceState
        }
        val analyzer = SongVoiceAnalyzer.get()
        analyzer?.request(song)
        value = LyricExpressionEngine.load(context, song, lyrics, structure, durationMs)
        // A voice measured for this song while it's showing: adopt it if it's still early.
        analyzer?.updates?.collect { id ->
            if (id == song.id) value = LyricExpressionEngine.load(context, song, lyrics, structure, durationMs)
        }
    }
}

object LyricExpressionEngine {
    /** A measured voice that lands later than this after the song started waits for the next play. */
    const val UPGRADE_WINDOW_MS = 10_000L

    /** The library analysis decodes at most this much of a song (PcmDecoder.decode's default). */
    private const val LIBRARY_ANALYSIS_WINDOW_MS = 180_000L

    private val cache = ConcurrentHashMap<String, SongExpressionProfile>()

    /** What one play of the current song has settled on, shared by every surface. */
    private class Session(val songId: String, val startedAt: Long) {
        @Volatile var durationMs: Long = 0L
        @Volatile var structure: SongStructure? = null
    }

    @Volatile
    private var session: Session? = null

    private fun sessionFor(songId: String): Session {
        session?.takeIf { it.songId == songId }?.let { return it }
        return Session(songId, System.currentTimeMillis()).also { session = it }
    }

    internal fun cached(songId: String, lyrics: Lyrics?, structure: SongStructure?): SongExpressionProfile? {
        val s = session?.takeIf { it.songId == songId } ?: return null
        val voice = SongVoiceAnalyzer.get()?.cached(songId)?.takeIf { usable(it, s) }
        return cache[cacheKey(songId, lyrics, structure ?: s.structure, s.durationMs, voice)]
    }

    /** A voice is used for this play if it existed before the song started or arrived early. */
    private fun usable(voice: SongVoice, session: Session): Boolean =
        voice.analysedAt <= session.startedAt + UPGRADE_WINDOW_MS

    private fun cacheKey(
        songId: String,
        lyrics: Lyrics?,
        structure: SongStructure?,
        durationMs: Long,
        voice: SongVoice?,
    ): String = buildString {
        append(songId).append('|').append(lyricsFingerprint(lyrics))
        append('|').append(structure?.lyricsFingerprint.orEmpty()).append(':').append(structure?.sections?.size ?: 0)
        append('|').append(durationMs / 1000)
        append('|').append(voice?.analysedAt ?: 0L)
    }

    /** Cheap identity of the lyrics a profile was built from (they often arrive after the song). */
    private fun lyricsFingerprint(lyrics: Lyrics?): String {
        if (lyrics == null) return "-"
        val synced = lyrics.synced
        val plain = lyrics.plain
        return "s${synced?.size ?: 0}:${synced?.firstOrNull()?.time ?: 0}:${synced?.lastOrNull()?.time ?: 0}:" +
            "${synced?.sumOf { it.line.length } ?: 0}:w${synced?.count { !it.words.isNullOrEmpty() } ?: 0}:" +
            "p${plain?.size ?: 0}:${plain?.sumOf { it.length } ?: 0}"
    }

    suspend fun load(
        context: Context,
        song: Song,
        lyrics: Lyrics?,
        structure: SongStructure?,
        durationMs: Long,
    ): SongExpressionProfile {
        val s = sessionFor(song.id)
        // Fill in what this surface doesn't know from what another surface already supplied.
        if (structure != null) s.structure = structure
        val effectiveStructure = structure ?: s.structure
        if (s.durationMs <= 0L) {
            s.durationMs = durationMs.takeIf { it > 0 } ?: song.duration.takeIf { it > 0 } ?: 0L
        }
        val voice = SongVoiceAnalyzer.get()?.load(song.id)?.takeIf { usable(it, s) }
        val effectiveDuration = s.durationMs.takeIf { it > 0 }
            ?: voice?.spanMs
            ?: lyrics?.synced?.lastOrNull()?.time?.toLong()?.plus(4_000L)
            ?: 0L

        val key = cacheKey(song.id, lyrics, effectiveStructure, s.durationMs, voice)
        cache[key]?.let { return it }
        val analysis = withContext(Dispatchers.IO) { libraryAnalysis(context, song) }
        val profile = withContext(Dispatchers.Default) {
            build(song, analysis, voice, lyrics, effectiveStructure, effectiveDuration)
        }
        if (cache.size > 48) cache.clear()
        cache[key] = profile
        return profile
    }

    /**
     * The library analysis for [song]: its own row for a local song, or the row of the downloaded
     * copy for a streamed song that was downloaded.
     */
    private suspend fun libraryAnalysis(context: Context, song: Song): TrackAnalysisEntity? = runCatching {
        val entry = EntryPointAccessors.fromApplication(context, LyricExpressionEntryPoint::class.java)
        val dao = entry.enrichmentDao()
        song.id.toLongOrNull()?.let { id -> dao.getAnalysis(id)?.let { return@runCatching it } }
        val localId = entry.cloudSongDao().getById(song.id)?.localSongId?.toLongOrNull()
            ?: song.youtubeId?.removePrefix("yt_")?.takeIf { it.isNotBlank() }?.let { videoId ->
                entry.cloudSongDao().getDownloadsByVideoId(videoId).firstNotNullOfOrNull { it.localSongId?.toLongOrNull() }
            }
        localId?.let { dao.getAnalysis(it) }
    }.getOrNull()

    /** Pure, testable profile builder. */
    fun build(
        song: Song,
        analysis: TrackAnalysisEntity?,
        voice: SongVoice?,
        lyrics: Lyrics?,
        structure: SongStructure?,
        durationMs: Long,
    ): SongExpressionProfile {
        // Loudness envelope: the measured voice covers the whole song; the library waveform only
        // the analysed head (≤ 180 s), so it's mapped onto that window, not the whole song.
        val libraryWaveform = analysis?.waveform?.takeIf { it.size >= 16 }
        val envelope: FloatArray?
        val envelopeSpanMs: Long
        when {
            voice != null && voice.envelope.size >= 16 -> {
                envelope = voice.envelope
                envelopeSpanMs = voice.spanMs
            }
            libraryWaveform != null && durationMs > 0 -> {
                envelope = FloatArray(libraryWaveform.size) { (libraryWaveform[it].toInt() and 0xFF) / 255f }
                envelopeSpanMs = minOf(durationMs, LIBRARY_ANALYSIS_WINDOW_MS)
            }
            else -> {
                envelope = null
                envelopeSpanMs = 0L
            }
        }
        val sorted = envelope?.filter { it > 0.01f }?.sorted()?.takeIf { it.size >= 8 }
        val mid = sorted?.percentile(0.5f) ?: 0.5f
        val high = sorted?.percentile(0.92f) ?: 0.9f
        val dynamics = voice?.dynamics ?: sorted?.let { s ->
            val mean = s.average().toFloat()
            val sd = sqrt(s.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / s.size.coerceAtLeast(1))
            (sd / 0.18f).coerceIn(0f, 1f)
        } ?: 0.5f

        val mix = song.mixIntelligence
        val features = song.musicalFeatures
        val keyText = analysis?.musicKey ?: voice?.musicKey ?: features.key
        val minorKey = when {
            !features.mode.isNullOrBlank() && analysis?.musicKey == null && voice?.musicKey == null ->
                features.mode.orEmpty().lowercase().trim().let { it.contains("minor") || it == "m" || it == "0" }
            else -> keyText?.let { k ->
                val lk = k.lowercase().trim()
                lk.contains("minor") || (lk.endsWith("m") && !lk.endsWith("maj"))
            } ?: false
        }

        val stats = lyricStats(lyrics, durationMs)
        val genre = genreVoice(song.genre)
            ?: genreVoice(song.songInformation.subgenre)
            ?: genreVoice(song.userActivityStats.tags.joinToString(" "))
        val mood = moodVoice(mix.mood)
        // A small, stable per-song nudge so two songs of the same genre and pace still differ.
        val seed = ((song.id.hashCode() and 0x7fffffff) % 1000) / 1000f - 0.5f

        // Energy: measured (library analysis, then the song's voice), then metadata, otherwise
        // estimated from genre + mood + how dense / shouty the words are.
        val estimatedEnergy = run {
            var e = genre?.energy ?: mood?.energy ?: 0.5f
            if (genre != null && mood != null) e = e * 0.7f + mood.energy * 0.3f
            stats?.let { st ->
                // ~1 word/s is a ballad, ~3.5 words/s is rap / punk.
                val pace = ((st.wordsPerSecond - 1f) / 2.5f).coerceIn(0f, 1f)
                e = e * 0.55f + pace * 0.45f
                e += st.shoutRatio * 0.35f
            }
            (e + seed * 0.08f).coerceIn(0f, 1f)
        }
        val measuredEnergy = analysis?.energy ?: voice?.energy
        val energy = (measuredEnergy ?: mix.energy ?: estimatedEnergy).coerceIn(0f, 1f)
        val estimatedValence = ((mood?.valence ?: genre?.valence ?: 0.5f) - (if (minorKey) 0.12f else 0f) +
            (stats?.exclaimRatio ?: 0f) * 0.2f + seed * 0.06f).coerceIn(0f, 1f)
        val valence = (analysis?.valence ?: mix.valence ?: estimatedValence).coerceIn(0f, 1f)
        val bpm = analysis?.bpm ?: voice?.bpm ?: features.bpm?.roundToInt()?.takeIf { it in 40..250 }

        val source = when {
            analysis?.energy != null -> ExpressionSource.LIBRARY_ANALYSIS
            voice != null -> ExpressionSource.MEASURED_VOICE
            mix.energy != null || mix.valence != null || bpm != null -> ExpressionSource.METADATA
            else -> ExpressionSource.ESTIMATE
        }

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
            bpm != null -> (60_000f / bpm).coerceIn(200f, 700f)
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
            envelopeSpanMs = envelopeSpanMs,
            envelopeMid = mid,
            envelopeHigh = high,
            typicalWordMs = typicalWordMs,
            structure = structure,
            sizeScale = sizeScale,
            lineHeightScale = lineHeightScale,
            motion = motion,
            source = source,
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

    /**
     * Pacing and emphasis from synced lyrics (timed), or from plain lyrics spread over the song
     * ([durationMs]; ~75 % of a song is sung).
     */
    internal fun lyricStats(lyrics: Lyrics?, durationMs: Long = 0L): LyricStats? {
        val synced = lyrics?.synced?.filter { it.line.isNotBlank() }?.takeIf { it.size >= 4 }
        val texts: List<String>
        val singingMs: Long
        if (synced != null) {
            texts = synced.map { it.line }
            var ms = 0L
            for ((i, line) in synced.withIndex()) {
                val next = synced.getOrNull(i + 1)?.time ?: (line.time + 4_000)
                // Cap each line's share so long instrumental gaps don't read as slow singing.
                ms += (next - line.time).toLong().coerceIn(0L, 8_000L)
            }
            singingMs = ms
        } else {
            texts = lyrics?.plain.orEmpty()
                .flatMap { it.split('\n').take(1) } // a plain entry may carry its translation below
                .filter { it.isNotBlank() && !it.trim().startsWith("[") }
                .takeIf { it.size >= 4 } ?: return null
            singingMs = if (durationMs > 0) (durationMs * 0.75).toLong() else 0L
        }
        var words = 0
        var shouted = 0
        var exclaimed = 0
        for (text in texts) {
            val tokens = text.split(' ', '\t').filter { t -> t.any(Char::isLetter) }
            words += tokens.size
            shouted += tokens.count { t ->
                val letters = t.filter(Char::isLetter)
                letters.length >= 2 && letters.all(Char::isUpperCase)
            }
            if (text.contains('!')) exclaimed++
        }
        if (words == 0) return null
        // If every line is shouted it's a styling choice, not emphasis.
        val shoutRatio = (shouted.toFloat() / words).let { if (it > 0.8f) 0.3f else it }
        return LyricStats(
            // Plain lyrics with no known duration: a neutral pace.
            wordsPerSecond = if (singingMs > 0) words / (singingMs / 1000f) else 2f,
            wordsPerLine = words.toFloat() / texts.size,
            shoutRatio = shoutRatio.coerceIn(0f, 1f),
            exclaimRatio = exclaimed.toFloat() / texts.size,
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

    /** Rough energy / valence of a mood label (mix intelligence, ML mood). */
    internal fun moodVoice(mood: String?): GenreVoice? {
        val m = mood?.lowercase()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        fun has(vararg keys: String) = keys.any { m.contains(it) }
        return when {
            has("aggressive", "angry", "intense", "epic") -> GenreVoice(0.88f, 0.35f)
            has("party", "euphoric", "energetic", "hype", "upbeat") -> GenreVoice(0.82f, 0.78f)
            has("happy", "joy", "cheerful", "fun", "uplifting", "bright") -> GenreVoice(0.65f, 0.82f)
            has("romantic", "love", "sensual", "groovy") -> GenreVoice(0.5f, 0.62f)
            has("dark", "tense", "ominous") -> GenreVoice(0.6f, 0.25f)
            has("sad", "melanchol", "heartbreak", "lonely", "somber") -> GenreVoice(0.3f, 0.2f)
            has("calm", "relax", "chill", "peaceful", "dreamy", "mellow", "soft") -> GenreVoice(0.25f, 0.55f)
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

/**
 * Per-word looks for a plain (unsynced) lyric line. Plain lyrics carry no times, so loudness,
 * held notes and sections can't apply; what the words themselves say still does — SHOUTED,
 * "!", (backing vocals) — scaled by the song's swing, on top of the song-level voice.
 * Resting spans only: a plain line is never the current line.
 */
internal fun buildUntimedLineExpression(
    text: String,
    profile: SongExpressionProfile,
    restWeight: FontWeight,
): List<AnnotatedString.Range<SpanStyle>> {
    if (text.isBlank()) return emptyList()
    val out = ArrayList<AnnotatedString.Range<SpanStyle>>()
    var depth = 0
    var i = 0
    while (i < text.length) {
        while (i < text.length && text[i].isWhitespace()) i++
        val start = i
        val depthAtStart = depth + if (i < text.length && (text[i] == '(' || text[i] == '[')) 1 else 0
        while (i < text.length && !text[i].isWhitespace()) {
            if (text[i] == '(' || text[i] == '[') depth++
            if ((text[i] == ')' || text[i] == ']') && depth > 0) depth--
            i++
        }
        if (i <= start) continue
        val token = text.substring(start, i)
        val letters = token.filter { it.isLetter() }
        val cue = when {
            depthAtStart > 0 -> -0.6f
            letters.length >= 2 && letters.all { it.isUpperCase() } -> 0.6f
            token.contains('!') -> 0.4f
            else -> 0f
        }
        if (cue == 0f) continue
        val expr = (0.3f * cue).coerceIn(-1f, 1f) * profile.swing
        val cueStep = if (cue < 0f) -100f else if (cue >= 0.6f) 100f else 0f
        val weight = snapWeight(restWeight.weight + expr * 260f + cueStep)
        val scale = (1f + expr * 0.12f + if (cue < 0f) -0.08f else 0f).coerceIn(0.86f, 1.16f)
        out += AnnotatedString.Range(
            SpanStyle(fontWeight = FontWeight(weight), fontSize = scale.em),
            start, i
        )
    }
    return out
}

private fun snapWeight(raw: Float): Int = ((raw / 100f).roundToInt() * 100).coerceIn(200, 800)
