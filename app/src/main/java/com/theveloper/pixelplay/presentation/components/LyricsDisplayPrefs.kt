package com.theveloper.pixelplay.presentation.components

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.ui.theme.MontserratFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap

/*
 * Single source of truth for how lyrics are displayed — the full lyrics sheet and the
 * album-cover overlay both read from here, so a setting chosen in the lyrics menu or in
 * Settings applies identically to both surfaces.
 *
 * Keys are the ones the app already writes (the lyrics menu writes lyrics_alignment,
 * show_lyrics_translation, show_lyrics_romanization and lyrics_highlight_mode; Experimental
 * writes the animated/blur keys). Only lyrics_font_v1 and lyrics_text_size_v1 are new.
 */

// ── Alignment ──────────────────────────────────────────────────────────────────────────

enum class LyricsAlignment(val key: String) {
    LEFT("left"), CENTER("center"), RIGHT("right");

    val textAlign: TextAlign
        get() = when (this) {
            LEFT -> TextAlign.Left
            CENTER -> TextAlign.Center
            RIGHT -> TextAlign.Right
        }

    val horizontalAlignment: Alignment.Horizontal
        get() = when (this) {
            LEFT -> Alignment.Start
            CENTER -> Alignment.CenterHorizontally
            RIGHT -> Alignment.End
        }

    /** Alignment of a line's content inside its row box. */
    val boxAlignment: Alignment
        get() = when (this) {
            LEFT -> Alignment.TopStart
            CENTER -> Alignment.TopCenter
            RIGHT -> Alignment.TopEnd
        }

    /** Horizontal pivot for scale transforms, so a line grows from the edge it is aligned to. */
    val pivotX: Float
        get() = when (this) {
            LEFT -> 0f
            CENTER -> 0.5f
            RIGHT -> 1f
        }

    fun flowArrangement(spacing: Dp): Arrangement.Horizontal = when (this) {
        LEFT -> Arrangement.spacedBy(spacing, Alignment.Start)
        CENTER -> Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
        RIGHT -> Arrangement.spacedBy(spacing, Alignment.End)
    }

    companion object {
        /** What the lyrics menu shows as selected on a fresh install. */
        val DEFAULT = LEFT

        fun fromKey(key: String?): LyricsAlignment = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

// ── Font and size ──────────────────────────────────────────────────────────────────────

/** Only fonts the app already ships or already uses. */
enum class LyricsFont(val key: String, val isVariable: Boolean) {
    /** Platform default (fontFamily = null). Full Unicode — the #2427 behaviour. */
    SYSTEM("system", false),

    /** gflex_variable.ttf with ROND 100 — the app's GoogleSansRounded. */
    GOOGLE_SANS_ROUNDED("google_sans_rounded", true),

    /** gflex_variable.ttf with ROND 0, as used by ExpressiveTopBarContent. */
    GOOGLE_SANS_FLEX("google_sans_flex", true),

    /** genre_variable.ttf (Roboto Flex), as used on the Stats screen. */
    ROBOTO_FLEX("roboto_flex", true),

    /** MontserratFamily — fetched through the Google Fonts provider. */
    MONTSERRAT("montserrat", false);

    companion object {
        val DEFAULT = SYSTEM
        fun fromKey(key: String?): LyricsFont = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

enum class LyricsTextSize(val key: String, val multiplier: Float) {
    SMALL("small", 0.87f), MEDIUM("medium", 1f), LARGE("large", 1.15f), EXTRA_LARGE("xl", 1.3f);

    companion object {
        val DEFAULT = MEDIUM
        fun fromKey(key: String?): LyricsTextSize = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Resting weight of the lyric lines. The active line is always drawn a clear step heavier
 * ([activeWeight]) so the hierarchy survives any choice.
 */
enum class LyricsFontWeight(val key: String, val weight: Int) {
    LIGHT("light", 300), REGULAR("regular", 400), MEDIUM("medium", 500), SEMIBOLD("semibold", 600);

    val fontWeight: FontWeight get() = FontWeight(weight)

    companion object {
        val DEFAULT = REGULAR
        fun fromKey(key: String?): LyricsFontWeight = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Line spacing, applied as a multiplier on the style's line height. */
enum class LyricsLineSpacing(val key: String, val multiplier: Float) {
    TIGHT("tight", 0.9f), NORMAL("normal", 1f), RELAXED("relaxed", 1.18f);

    companion object {
        val DEFAULT = NORMAL
        fun fromKey(key: String?): LyricsLineSpacing = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * The weight the current line is drawn at, for a given resting weight: +300, capped at 900.
 * Regular (400) → Bold (700), which is exactly the look the sheet always had.
 */
fun activeLyricWeight(rest: FontWeight?): FontWeight {
    val base = rest?.weight ?: FontWeight.Normal.weight
    return FontWeight((base + 300).coerceAtMost(900))
}

// ── Preferences ────────────────────────────────────────────────────────────────────────

@Immutable
data class LyricsDisplayPrefs(
    val alignment: LyricsAlignment = LyricsAlignment.DEFAULT,
    val showTranslation: Boolean = true,
    val showRomanization: Boolean = true,
    val highlightMode: LyricsHighlightMode = LyricsHighlightMode.AUTO,
    val useAnimatedLyrics: Boolean = false,
    val animatedLyricsBlurEnabled: Boolean = true,
    val animatedLyricsBlurStrength: Float = 2.5f,
    val disableBlurAllOver: Boolean = false,
    val coverLyricsEnabled: Boolean = false,
    /**
     * Expressive typography (toggle beside Immersive lyrics). When off, every lyrics surface uses
     * the default look and the choices below are kept but not applied.
     */
    val expressiveTypography: Boolean = false,
    /** Expressive typography adapts to each song's energy, mood, loudness and timing. */
    val adaptiveTypography: Boolean = true,
    /** Effective values: what lyrics surfaces draw with (defaults while expressive is off). */
    val font: LyricsFont = LyricsFont.DEFAULT,
    val textSize: LyricsTextSize = LyricsTextSize.DEFAULT,
    val fontWeight: LyricsFontWeight = LyricsFontWeight.DEFAULT,
    val lineSpacing: LyricsLineSpacing = LyricsLineSpacing.DEFAULT,
    /** The user's saved choices, shown in the pickers whether or not expressive is on. */
    val chosenFont: LyricsFont = LyricsFont.DEFAULT,
    val chosenTextSize: LyricsTextSize = LyricsTextSize.DEFAULT,
    val chosenFontWeight: LyricsFontWeight = LyricsFontWeight.DEFAULT,
    val chosenLineSpacing: LyricsLineSpacing = LyricsLineSpacing.DEFAULT,
    /** Lyrics sheet header shown as the compact Now / Next bar instead of the full card. */
    val headerCollapsed: Boolean = false,
    /** Face-to-face split view in immersive lyrics (top half turned 180°). */
    val splitFaceView: Boolean = false,
    /** Show the song structure strip (Intro, Verse, Chorus…) under the header. */
    val showSongStructure: Boolean = true,
    /** Immersive lyrics (lyrics menu → Immersive lyrics). Same key the sheet reads. */
    val immersiveEnabled: Boolean = false,
    /** Auto-hide delay before immersive kicks in (lyrics menu → Auto-hide delay). */
    val immersiveTimeoutMs: Long = DEFAULT_IMMERSIVE_TIMEOUT_MS,
) {
    /** Same gate the lyrics sheet uses: blur only exists as part of animated lyrics. */
    val lineBlurActive: Boolean
        get() = useAnimatedLyrics && animatedLyricsBlurEnabled && !disableBlurAllOver
}

object LyricsDisplayPrefKeys {
    val ALIGNMENT = stringPreferencesKey("lyrics_alignment")
    val SHOW_TRANSLATION = booleanPreferencesKey("show_lyrics_translation")
    val SHOW_ROMANIZATION = booleanPreferencesKey("show_lyrics_romanization")
    val HIGHLIGHT_MODE = stringPreferencesKey("lyrics_highlight_mode")
    val USE_ANIMATED = booleanPreferencesKey("use_animated_lyrics")
    val BLUR_ENABLED = booleanPreferencesKey("animated_lyrics_blur_enabled")
    val BLUR_STRENGTH = floatPreferencesKey("animated_lyrics_blur_strength")
    val DISABLE_BLUR_ALL_OVER = booleanPreferencesKey("disable_blur_all_over")
    val COVER_LYRICS_ENABLED = booleanPreferencesKey("cover_lyrics_enabled")
    val FONT = stringPreferencesKey("lyrics_font_v1")
    val TEXT_SIZE = stringPreferencesKey("lyrics_text_size_v1")
    val EXPRESSIVE_TYPOGRAPHY = booleanPreferencesKey("lyrics_expressive_typography_v1")
    val ADAPTIVE_TYPOGRAPHY = booleanPreferencesKey("lyrics_adaptive_typography_v1")
    val FONT_WEIGHT = stringPreferencesKey("lyrics_font_weight_v1")
    val LINE_SPACING = stringPreferencesKey("lyrics_line_spacing_v1")
    val HEADER_COLLAPSED = booleanPreferencesKey("lyrics_header_collapsed_v1")
    val SPLIT_FACE_VIEW = booleanPreferencesKey("lyrics_split_face_v1")
    val SHOW_SONG_STRUCTURE = booleanPreferencesKey("lyrics_show_song_structure")
    // Written by SettingsViewModel.setImmersiveLyricsEnabled / setImmersiveLyricsTimeout
    // (UserPreferencesRepository.PreferencesKeys.Lyrics) — same keys, same defaults.
    val IMMERSIVE_ENABLED = booleanPreferencesKey("immersive_lyrics_enabled")
    val IMMERSIVE_TIMEOUT = longPreferencesKey("immersive_lyrics_timeout")
}

const val DEFAULT_IMMERSIVE_TIMEOUT_MS = 4000L

/** "Off" immersive delay: immersive never kicks in by itself (swipe the controls down to hide them). */
const val IMMERSIVE_TIMEOUT_OFF = 0L

fun lyricsDisplayPrefsFlow(dataStore: DataStore<Preferences>): Flow<LyricsDisplayPrefs> =
    dataStore.data
        .map { p ->
            val chosenFont = LyricsFont.fromKey(p[LyricsDisplayPrefKeys.FONT])
            val chosenSize = LyricsTextSize.fromKey(p[LyricsDisplayPrefKeys.TEXT_SIZE])
            val chosenWeight = LyricsFontWeight.fromKey(p[LyricsDisplayPrefKeys.FONT_WEIGHT])
            val chosenSpacing = LyricsLineSpacing.fromKey(p[LyricsDisplayPrefKeys.LINE_SPACING])
            // Not set yet: on for anyone who already picked a font or size before the toggle
            // existed, so an existing choice doesn't silently disappear.
            val expressive = p[LyricsDisplayPrefKeys.EXPRESSIVE_TYPOGRAPHY]
                ?: (chosenFont != LyricsFont.DEFAULT || chosenSize != LyricsTextSize.DEFAULT)
            LyricsDisplayPrefs(
                alignment = LyricsAlignment.fromKey(p[LyricsDisplayPrefKeys.ALIGNMENT]),
                showTranslation = p[LyricsDisplayPrefKeys.SHOW_TRANSLATION] ?: true,
                showRomanization = p[LyricsDisplayPrefKeys.SHOW_ROMANIZATION] ?: true,
                highlightMode = LyricsHighlightMode.fromName(p[LyricsDisplayPrefKeys.HIGHLIGHT_MODE]),
                useAnimatedLyrics = p[LyricsDisplayPrefKeys.USE_ANIMATED] ?: false,
                animatedLyricsBlurEnabled = p[LyricsDisplayPrefKeys.BLUR_ENABLED] ?: true,
                animatedLyricsBlurStrength = p[LyricsDisplayPrefKeys.BLUR_STRENGTH] ?: 2.5f,
                disableBlurAllOver = p[LyricsDisplayPrefKeys.DISABLE_BLUR_ALL_OVER] ?: false,
                coverLyricsEnabled = p[LyricsDisplayPrefKeys.COVER_LYRICS_ENABLED] ?: false,
                expressiveTypography = expressive,
                adaptiveTypography = p[LyricsDisplayPrefKeys.ADAPTIVE_TYPOGRAPHY] ?: true,
                font = if (expressive) chosenFont else LyricsFont.DEFAULT,
                textSize = if (expressive) chosenSize else LyricsTextSize.DEFAULT,
                fontWeight = if (expressive) chosenWeight else LyricsFontWeight.DEFAULT,
                lineSpacing = if (expressive) chosenSpacing else LyricsLineSpacing.DEFAULT,
                chosenFont = chosenFont,
                chosenTextSize = chosenSize,
                chosenFontWeight = chosenWeight,
                chosenLineSpacing = chosenSpacing,
                headerCollapsed = p[LyricsDisplayPrefKeys.HEADER_COLLAPSED] ?: false,
                splitFaceView = p[LyricsDisplayPrefKeys.SPLIT_FACE_VIEW] ?: false,
                showSongStructure = p[LyricsDisplayPrefKeys.SHOW_SONG_STRUCTURE] ?: true,
                immersiveEnabled = p[LyricsDisplayPrefKeys.IMMERSIVE_ENABLED] ?: false,
                immersiveTimeoutMs = p[LyricsDisplayPrefKeys.IMMERSIVE_TIMEOUT] ?: DEFAULT_IMMERSIVE_TIMEOUT_MS,
            )
        }
        .distinctUntilChanged()

@Composable
fun rememberLyricsDisplayPrefs(): State<LyricsDisplayPrefs> {
    val context = LocalContext.current
    val flow = remember(context) { lyricsDisplayPrefsFlow(context.applicationContext.dataStore) }
    return flow.collectAsStateWithLifecycle(initialValue = LyricsDisplayPrefs())
}

suspend fun Context.editLyricsDisplayPrefs(block: (MutablePreferences) -> Unit) {
    applicationContext.dataStore.edit { block(it) }
}

// ── Immersive ──────────────────────────────────────────────────────────────────────────

/**
 * Immersive lyrics outside the sheet (album cover, cutout island). Same rule as the sheet:
 * when immersive lyrics is on, the auto-hide delay after the
 * last touch switches the surface into immersive; any touch switches it back and restarts
 * the delay.
 */
@Stable
class LyricsImmersiveState internal constructor() {
    var isImmersive by mutableStateOf(false)
        internal set
    internal var lastInteraction by mutableLongStateOf(SystemClock.uptimeMillis())

    fun onInteraction() {
        lastInteraction = SystemClock.uptimeMillis()
        isImmersive = false
    }
}

/**
 * @param active false while the surface can't be immersive (no synced lyrics, hidden,
 * "disable immersive once" from the lyrics menu, …).
 */
@Composable
fun rememberLyricsImmersiveState(prefs: LyricsDisplayPrefs, active: Boolean): LyricsImmersiveState {
    val state = remember { LyricsImmersiveState() }
    val enabled = prefs.immersiveEnabled && active
    LaunchedEffect(enabled, prefs.immersiveTimeoutMs, state.lastInteraction) {
        if (enabled && prefs.immersiveTimeoutMs > IMMERSIVE_TIMEOUT_OFF) {
            delay(prefs.immersiveTimeoutMs)
            state.isImmersive = true
        } else {
            state.isImmersive = false
        }
    }
    return state
}

/** Any press inside this node counts as an interaction. Observes only — never consumes. */
fun Modifier.resetsLyricsImmersive(state: LyricsImmersiveState): Modifier =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) state.onInteraction()
            }
        }
    }

/** Text grows in immersive, like the sheet (×1.4 there); smaller canvases use less. */
const val SHEET_IMMERSIVE_TEXT_SCALE = 1.4f
const val COVER_IMMERSIVE_TEXT_SCALE = 1.2f
const val ISLAND_IMMERSIVE_TEXT_SCALE = 1.15f

// ── Shared motion helpers ──────────────────────────────────────────────────────────────

/**
 * Blur for a line [distance] lines away from the current one. Same mapping as the lyrics
 * sheet (distance × strength), capped at [max]. Fractional distances interpolate, so a line
 * sharpens continuously as it rolls into the centre.
 */
fun lyricsBlurRadius(distance: Float, strength: Float, max: Dp): Dp {
    val d = kotlin.math.abs(distance)
    if (d < 0.05f || strength <= 0f) return 0.dp
    return (d * strength).coerceAtMost(max.value).dp
}

/**
 * The line to show in the centre: the last line that has started. Unlike
 * [resolveCurrentLineIndex], this stays on the last sung line through instrumental gaps
 * instead of returning -1. Returns -1 only before the first line.
 */
internal fun resolveDisplayLineIndex(lines: List<SyncedLine>, position: Long): Int {
    if (lines.isEmpty() || position < lines[0].time) return -1
    var lo = 0
    var hi = lines.lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (lines[mid].time <= position) lo = mid else hi = mid - 1
    }
    return lo
}

// ── Font families ──────────────────────────────────────────────────────────────────────

private const val ROUNDED_AXIS = 100f
private val lyricsFamilyCache = ConcurrentHashMap<String, FontFamily>()
private val LYRICS_SHEET_WEIGHTS = intArrayOf(200, 300, 400, 500, 600, 700, 800, 900)

@OptIn(ExperimentalTextApi::class)
private fun variableFont(font: LyricsFont, weight: Int): Font {
    val settings = when (font) {
        LyricsFont.GOOGLE_SANS_ROUNDED -> FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("ROND", ROUNDED_AXIS)
        )
        LyricsFont.GOOGLE_SANS_FLEX -> FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("ROND", 0f)
        )
        else -> FontVariation.Settings(FontVariation.weight(weight))
    }
    val resId = if (font == LyricsFont.ROBOTO_FLEX) R.font.genre_variable else R.font.gflex_variable
    return Font(resId = resId, weight = FontWeight(weight), variationSettings = settings)
}

/**
 * A family carrying normal → bold instances, for surfaces that pick their own weights
 * (the lyrics sheet). `null` means the platform font.
 */
fun lyricsFontFamily(font: LyricsFont): FontFamily? = when (font) {
    LyricsFont.SYSTEM -> null
    LyricsFont.MONTSERRAT -> MontserratFamily
    else -> lyricsFamilyCache.getOrPut("${font.key}_all") {
        FontFamily(LYRICS_SHEET_WEIGHTS.map { variableFont(font, it) })
    }
}

/**
 * A single-instance family at exactly [weight], for animating weight on a variable font.
 * Callers should quantise [weight] so only a handful of instances are ever created.
 */
fun lyricsFontFamilyAtWeight(font: LyricsFont, weight: Int): FontFamily? = when (font) {
    LyricsFont.SYSTEM -> null
    LyricsFont.MONTSERRAT -> MontserratFamily
    else -> lyricsFamilyCache.getOrPut("${font.key}_$weight") {
        FontFamily(variableFont(font, weight))
    }
}

// ── Glyph coverage ─────────────────────────────────────────────────────────────────────

/**
 * Resolves the font actually used for a song's lyrics: the chosen font if it has a glyph for
 * every character in the lyrics (lines, words, translations, romanizations), otherwise
 * [LyricsFont.SYSTEM]. Decided once per song so the cover and the sheet always agree.
 * Returns SYSTEM until the check has run, which is the safe answer.
 */
@Composable
fun rememberEffectiveLyricsFont(lyrics: Lyrics?, font: LyricsFont): State<LyricsFont> {
    val context = LocalContext.current.applicationContext
    // A result already computed for this exact lyrics object (the cover and the sheet ask
    // for the same song) is used immediately, so switching views never flashes the system font.
    val initial = remember(lyrics, font) {
        when {
            font == LyricsFont.SYSTEM || lyrics == null -> font
            LyricsGlyphCoverage.cached(font, lyrics) == true -> font
            else -> LyricsFont.SYSTEM
        }
    }
    return produceState(initialValue = initial, lyrics, font) {
        value = if (font == LyricsFont.SYSTEM || lyrics == null) {
            font
        } else {
            withContext(Dispatchers.Default) {
                if (LyricsGlyphCoverage.supportsCached(context, font, lyrics)) font else LyricsFont.SYSTEM
            }
        }
    }
}

internal object LyricsGlyphCoverage {
    private val coverage = ConcurrentHashMap<LyricsFont, BitSet>()
    private val results = ConcurrentHashMap<String, Boolean>()

    private fun resultKey(font: LyricsFont, lyrics: Lyrics) = "${font.key}:${System.identityHashCode(lyrics)}"

    fun cached(font: LyricsFont, lyrics: Lyrics): Boolean? = results[resultKey(font, lyrics)]

    fun supportsCached(context: Context, font: LyricsFont, lyrics: Lyrics): Boolean {
        val key = resultKey(font, lyrics)
        results[key]?.let { return it }
        val supported = supports(context, font, lyrics)
        if (results.size > 64) results.clear()
        results[key] = supported
        return supported
    }

    fun supports(context: Context, font: LyricsFont, lyrics: Lyrics): Boolean {
        val check: (Int) -> Boolean = when (font) {
            LyricsFont.SYSTEM -> return true
            // Downloadable font: no local file to inspect. Montserrat covers Latin,
            // Latin Extended, Cyrillic and Vietnamese.
            LyricsFont.MONTSERRAT -> { cp -> isMontserratCodePoint(cp) }
            else -> {
                val bits = coverage.getOrPut(font) { loadCmap(context, font) }
                if (bits.isEmpty) return false
                ({ cp -> bits.get(cp) })
            }
        }
        return lyricsText(lyrics).all { text -> textSupported(text, check) }
    }

    private fun lyricsText(lyrics: Lyrics): Sequence<String> = sequence {
        lyrics.synced?.forEach { line ->
            yield(line.line)
            line.translation?.let { yield(it) }
            line.romanization?.let { yield(it) }
            line.words?.forEach { yield(it.word) }
        }
        lyrics.plain?.forEach { yield(it) }
    }

    private fun textSupported(text: String, check: (Int) -> Boolean): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (isIgnorable(cp)) continue
            if (!check(cp)) return false
        }
        return true
    }

    private fun isIgnorable(cp: Int): Boolean =
        Character.isWhitespace(cp) ||
            Character.isISOControl(cp) ||
            cp == 0x00A0 ||
            cp in 0x200B..0x200F ||
            cp in 0x2028..0x202F ||
            cp == 0x2060 ||
            cp in 0xFE00..0xFE0F ||
            cp == 0xFEFF

    private fun isMontserratCodePoint(cp: Int): Boolean =
        cp in 0x0020..0x024F ||
            cp in 0x0300..0x036F ||
            cp in 0x0400..0x04FF ||
            cp in 0x1E00..0x1EFF ||
            cp in 0x2000..0x206F ||
            cp in 0x20A0..0x20CF ||
            cp in 0x2100..0x214F

    private fun loadCmap(context: Context, font: LyricsFont): BitSet {
        val resId = if (font == LyricsFont.ROBOTO_FLEX) R.font.genre_variable else R.font.gflex_variable
        return runCatching {
            val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
            parseCmap(ByteBuffer.wrap(bytes))
        }.getOrElse { BitSet() }
    }

    /** Reads the Unicode cmap (format 12 preferred, else format 4) into a code point set. */
    internal fun parseCmap(buf: ByteBuffer): BitSet {
        val result = BitSet()
        val numTables = buf.getShort(4).toInt() and 0xFFFF
        var cmapOffset = -1
        for (t in 0 until numTables) {
            val rec = 12 + t * 16
            if (buf.getInt(rec) == 0x636D6170 /* 'cmap' */) {
                cmapOffset = buf.getInt(rec + 8)
                break
            }
        }
        if (cmapOffset < 0) return result

        val subtables = buf.getShort(cmapOffset + 2).toInt() and 0xFFFF
        var fmt12 = -1
        var fmt4 = -1
        for (s in 0 until subtables) {
            val rec = cmapOffset + 4 + s * 8
            val platform = buf.getShort(rec).toInt() and 0xFFFF
            val encoding = buf.getShort(rec + 2).toInt() and 0xFFFF
            val offset = cmapOffset + buf.getInt(rec + 4)
            val format = buf.getShort(offset).toInt() and 0xFFFF
            val unicode = platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10))
            if (!unicode) continue
            if (format == 12 && fmt12 < 0) fmt12 = offset
            if (format == 4 && fmt4 < 0) fmt4 = offset
        }

        if (fmt12 >= 0) {
            val groups = buf.getInt(fmt12 + 12)
            for (g in 0 until groups) {
                val rec = fmt12 + 16 + g * 12
                val start = buf.getInt(rec)
                val end = buf.getInt(rec + 4)
                val glyph = buf.getInt(rec + 8)
                if (start in 0..0x10FFFF && end in start..0x10FFFF) {
                    result.set(if (glyph == 0) start + 1 else start, end + 1)
                }
            }
        } else if (fmt4 >= 0) {
            val segCount = (buf.getShort(fmt4 + 6).toInt() and 0xFFFF) / 2
            val endBase = fmt4 + 14
            val startBase = endBase + segCount * 2 + 2
            val deltaBase = startBase + segCount * 2
            val rangeBase = deltaBase + segCount * 2
            for (i in 0 until segCount) {
                val end = buf.getShort(endBase + i * 2).toInt() and 0xFFFF
                val start = buf.getShort(startBase + i * 2).toInt() and 0xFFFF
                val delta = buf.getShort(deltaBase + i * 2).toInt()
                val rangeOffsetPos = rangeBase + i * 2
                val rangeOffset = buf.getShort(rangeOffsetPos).toInt() and 0xFFFF
                if (start == 0xFFFF) continue
                for (c in start..end) {
                    val glyph = if (rangeOffset == 0) {
                        (c + delta) and 0xFFFF
                    } else {
                        val addr = rangeOffsetPos + rangeOffset + (c - start) * 2
                        val raw = buf.getShort(addr).toInt() and 0xFFFF
                        if (raw == 0) 0 else (raw + delta) and 0xFFFF
                    }
                    if (glyph != 0) result.set(c)
                }
            }
        }
        return result
    }
}
