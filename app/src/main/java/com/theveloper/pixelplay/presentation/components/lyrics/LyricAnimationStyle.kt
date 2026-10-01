package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.theveloper.pixelplay.data.model.SyncedWord
import com.theveloper.pixelplay.ui.theme.MotionTokens

/**
 * How synced lyrics move and highlight (Settings → Lyrics → Animation style).
 *
 * Every lyrics surface draws through the same renderer ([SmoothLyricLine] / [LyricLineLayers]);
 * the style only changes what that renderer paints and how lines hand over. Order, names and
 * descriptions are the ones shown in the selector.
 */
enum class LyricsAnimationStyle(val key: String, val title: String, val description: String) {
    CLEAN("clean", "Clean", "Smooth scrolling with precise word highlighting."),
    SOFT("soft", "Soft", "Gentle focus changes and a feathered highlight."),
    GLASS("glass", "Glass", "A frosted highlight that follows each word."),
    KARAOKE("karaoke", "Karaoke", "Clear colour progression for singing along."),
    FLOW("flow", "Flow", "Fluid highlights with gently elastic movement."),
    CINEMATIC("cinematic", "Cinematic", "Slow light sweeps and elegant line transitions."),
    PLAYFUL("playful", "Playful", "Small word pops and lively letter waves."),
    ACOUSTIC("acoustic", "Acoustic", "Warm ink-like text with a drawn underline."),
    NEON("neon", "Neon", "Glowing words with a travelling light band."),
    PERFORMANCE("performance", "Performance", "Expressive phrase movement and vocal accents.");

    val spec: LyricStyleSpec get() = specFor(this)

    companion object {
        val DEFAULT = CLEAN
        fun fromKey(key: String?): LyricsAnimationStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** How a line takes over from the previous one. */
enum class LineHandover { EASE_OUT, SPRING, CROSSFADE }

/** What is drawn on / around the word being sung. */
enum class WordTreatment { FILL, SPOTLIGHT, CAPSULE, LIQUID, HALO, UNDERLINE, GLOW, POP }

/** What happens inside the letters of the word being sung. */
enum class LetterTreatment { NONE, LIGHT_SWEEP, WEIGHT_PASS, SHIMMER, WAVE, INK_RICHNESS, ILLUMINATE, VOWEL_GLOW }

/**
 * Plain values describing one preset. Read inside draw lambdas only, so nothing recomposes
 * per frame.
 */
@Immutable
data class LyricStyleSpec(
    val handover: LineHandover,
    /** Nominal line handover (ms): shortened for fast lyrics by [boundedDuration]. */
    val handoverMs: Int,
    /** Active line scale (1 = none), applied with graphicsLayer so wrapping never changes. */
    val activeScale: Float = 1f,
    /** Active line lift (dp) while it arrives. */
    val activeLiftDp: Float = 0f,
    /** Alpha multiplier for lines around the current one (1 = unchanged). */
    val neighbourAlpha: Float = 1f,
    /** Small blur (dp) on neighbouring lines (Soft), only where blur is available and allowed. */
    val neighbourBlurDp: Float = 0f,
    /** Extra emphasis for the next line (Karaoke anticipation). */
    val nextLineBoost: Float = 0f,
    /** Faint band of light behind the current line (Neon). */
    val lineBand: Boolean = false,
    val word: WordTreatment,
    val letter: LetterTreatment,
    /** Width of the soft edge on the fill, as a fraction of the base feather. */
    val featherScale: Float = 0.3f,
    /**
     * How much quieter completed words are than the active word (0 = same, as in Karaoke).
     * Implemented by thinning the sung colour back toward the resting colour.
     */
    val completedQuiet: Float = 0.3f,
    /** Nominal word accent (ms): shortened for short words by [boundedDuration]. */
    val accentMs: Int = MotionTokens.DurationShort3,
)

private fun specFor(style: LyricsAnimationStyle): LyricStyleSpec = when (style) {
    LyricsAnimationStyle.CLEAN -> LyricStyleSpec(
        handover = LineHandover.EASE_OUT, handoverMs = MotionTokens.DurationMedium4,
        word = WordTreatment.FILL, letter = LetterTreatment.NONE,
        featherScale = 0.22f, completedQuiet = 0.28f,
    )
    LyricsAnimationStyle.SOFT -> LyricStyleSpec(
        handover = LineHandover.CROSSFADE, handoverMs = MotionTokens.DurationMedium3,
        neighbourAlpha = 0.8f, neighbourBlurDp = 1.5f,
        word = WordTreatment.SPOTLIGHT, letter = LetterTreatment.LIGHT_SWEEP,
        featherScale = 1.7f, completedQuiet = 0.2f,
    )
    LyricsAnimationStyle.GLASS -> LyricStyleSpec(
        handover = LineHandover.EASE_OUT, handoverMs = MotionTokens.DurationMedium3,
        activeScale = 1.025f,
        word = WordTreatment.CAPSULE, letter = LetterTreatment.LIGHT_SWEEP,
        featherScale = 0.3f, completedQuiet = 0.25f, accentMs = MotionTokens.DurationShort4,
    )
    LyricsAnimationStyle.KARAOKE -> LyricStyleSpec(
        handover = LineHandover.EASE_OUT, handoverMs = MotionTokens.DurationMedium1,
        nextLineBoost = 0.28f,
        word = WordTreatment.FILL, letter = LetterTreatment.NONE,
        featherScale = 0.1f, completedQuiet = 0f,
    )
    LyricsAnimationStyle.FLOW -> LyricStyleSpec(
        handover = LineHandover.SPRING, handoverMs = MotionTokens.DurationMedium4,
        activeLiftDp = 4f,
        word = WordTreatment.LIQUID, letter = LetterTreatment.WEIGHT_PASS,
        featherScale = 0.6f, completedQuiet = 0.2f,
    )
    LyricsAnimationStyle.CINEMATIC -> LyricStyleSpec(
        handover = LineHandover.CROSSFADE, handoverMs = MotionTokens.DurationMedium4,
        neighbourAlpha = 0.8f,
        word = WordTreatment.HALO, letter = LetterTreatment.SHIMMER,
        featherScale = 1.1f, completedQuiet = 0.3f, accentMs = MotionTokens.DurationShort3,
    )
    LyricsAnimationStyle.PLAYFUL -> LyricStyleSpec(
        handover = LineHandover.SPRING, handoverMs = MotionTokens.DurationMedium2,
        activeScale = 1.02f, activeLiftDp = 3f,
        word = WordTreatment.POP, letter = LetterTreatment.WAVE,
        featherScale = 0.3f, completedQuiet = 0.2f,
    )
    LyricsAnimationStyle.ACOUSTIC -> LyricStyleSpec(
        handover = LineHandover.EASE_OUT, handoverMs = MotionTokens.DurationMedium4,
        neighbourAlpha = 0.75f,
        word = WordTreatment.UNDERLINE, letter = LetterTreatment.INK_RICHNESS,
        featherScale = 0.35f, completedQuiet = 0.3f,
    )
    LyricsAnimationStyle.NEON -> LyricStyleSpec(
        handover = LineHandover.EASE_OUT, handoverMs = MotionTokens.DurationMedium3,
        lineBand = true,
        word = WordTreatment.GLOW, letter = LetterTreatment.ILLUMINATE,
        featherScale = 0.4f, completedQuiet = 0.35f,
    )
    LyricsAnimationStyle.PERFORMANCE -> LyricStyleSpec(
        handover = LineHandover.EASE_OUT, handoverMs = MotionTokens.DurationMedium3,
        word = WordTreatment.FILL, letter = LetterTreatment.VOWEL_GLOW,
        featherScale = 0.25f, completedQuiet = 0.25f,
    )
}

/** The selected preset for every lyrics surface below it. */
val LocalLyricAnimationStyle = staticCompositionLocalOf { LyricsAnimationStyle.DEFAULT }

/**
 * True when the system asks for reduced motion (animator duration scale 0): lifts, springs,
 * waves and travelling decorations turn into stable highlighting; timed fills keep running.
 */
val LocalLyricReducedMotion = compositionLocalOf { false }

/**
 * Starting durations are never added to playback: a transition gets at most [fraction] of the
 * time available before the next unit starts, and snaps (0) when that would be under [floorMs].
 */
fun boundedDuration(nominalMs: Int, availableMs: Long, fraction: Float, floorMs: Int = MotionTokens.DurationShort2): Int {
    if (availableMs <= 0L) return nominalMs
    val cap = (availableMs * fraction).toInt()
    val d = minOf(nominalMs, cap)
    return if (d < floorMs) 0 else d
}

/** Autoscroll to the new current line for [style], bounded by the gap to the next line. */
fun LyricsAnimationStyle.scrollSpec(gapMs: Long, reducedMotion: Boolean): AnimationSpec<Float> {
    val s = spec
    if (reducedMotion) {
        val d = boundedDuration(MotionTokens.DurationShort3, gapMs, 0.4f)
        return if (d == 0) snap() else tween(d, easing = MotionTokens.Standard)
    }
    val d = boundedDuration(s.handoverMs, gapMs, 0.4f)
    if (d == 0) return snap()
    return when (s.handover) {
        // Small, quickly damped overshoot; a plain tween when the next line is close.
        LineHandover.SPRING -> if (gapMs < MotionTokens.DurationMedium4 * 2L) {
            tween(d, easing = MotionTokens.EmphasizedDecelerate)
        } else {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        }
        LineHandover.CROSSFADE -> tween(d, easing = MotionTokens.Emphasized)
        LineHandover.EASE_OUT -> tween(d, easing = MotionTokens.EmphasizedDecelerate)
    }
}

/** How precisely a line is timed; effects below the available tier stay off. */
enum class LyricTimingTier { UNTIMED, LINE, WORD, SUBWORD }

/** Letters with measured timing (never the estimator's guesses). */
fun SyncedWord.hasMeasuredPhonemes(): Boolean =
    phonemes?.any { it.alphabet != ESTIMATED_ALPHABET } == true

internal const val ESTIMATED_ALPHABET = "estimated-grapheme"

/**
 * Per-word timing for one line, so the renderer can time accents (pops, handovers, fades)
 * from the playback position instead of guessing from fill progress. Indexes match the
 * line's word layout.
 */
@Immutable
class LyricWordTimeline(
    val startsMs: LongArray,
    val endsMs: LongArray,
    /** Measured phonemes per word (null when none); estimated letters are excluded. */
    val measured: List<List<com.theveloper.pixelplay.data.model.SyncedPhoneme>?>,
) {
    val size: Int get() = startsMs.size

    companion object {
        fun of(words: List<SyncedWord>, lineEndMs: Long): LyricWordTimeline {
            val starts = LongArray(words.size)
            val ends = LongArray(words.size)
            for ((i, w) in words.withIndex()) {
                starts[i] = w.time.toLong()
                val next = words.getOrNull(i + 1)?.time?.toLong() ?: lineEndMs
                ends[i] = (w.endTime?.toLong() ?: next).coerceAtLeast(starts[i] + 1L)
            }
            val measured = words.map { w ->
                w.phonemes?.filter { it.alphabet != ESTIMATED_ALPHABET }?.takeIf { it.isNotEmpty() }
            }
            return LyricWordTimeline(starts, ends, measured)
        }
    }
}
