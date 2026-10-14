package com.theveloper.pixelplay.presentation.components

import android.os.Build
import androidx.compose.runtime.Immutable

/**
 * Colour-grading presets for playlist cover art.
 *
 * Each preset is a 4x5 colour matrix in the layout shared by `android.graphics.ColorMatrix`
 * and Compose's `androidx.compose.ui.graphics.ColorMatrix` (row-major, offsets in 0..255),
 * so the exact same numbers drive the live preview (Compose `ColorFilter.colorMatrix`) and the
 * saved JPEG (`ColorMatrixColorFilter` in PlaylistViewModel.saveCoverImageToInternalStorage).
 *
 * A preset can also carry a [CoverFilterEffect]: texture/light effects (grain, bloom, duotone
 * paper, ornaments…) run by an AGSL shader on Android 13+ (see CoverFilterShader). Below 13 the
 * [matrix] alone is the whole look, so it is always a sensible stand-alone approximation.
 */
@Immutable
data class CoverFilterPreset(
    val id: String,
    val label: String,
    val categoryId: String,
    val matrix: FloatArray,
    val effect: CoverFilterEffect? = null
) {
    /** The effect that will actually run on this device, or null. */
    val activeEffect: CoverFilterEffect? get() = if (CoverFilters.effectsSupported) effect else null

    /** Colour matrix to apply before [activeEffect] (or alone when there is no effect). */
    val activeMatrix: FloatArray get() = activeEffect?.grade ?: matrix

    override fun equals(other: Any?): Boolean = other is CoverFilterPreset && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

enum class CoverOrnament(val shaderValue: Float) {
    None(0f), Filigree(1f), Botanical(2f), Bookbinding(3f), Leaf(4f), Gears(5f)
}

/**
 * Shader parameters. All strengths are 0..1-ish; 0 switches a stage off. Colours are ARGB ints
 * (alpha ignored) — they are image-processing data (paper / ink colours), not UI colours.
 * Positions are normalised to the image (0..1) so thumbnail, canvas and export look the same.
 */
@Immutable
data class CoverFilterEffect(
    /** Matrix used instead of the preset's fallback [CoverFilterPreset.matrix] when the shader runs. */
    val grade: FloatArray? = null,
    /** Duotone mix: maps luminance onto [dark]..[light]. */
    val duotone: Float = 0f,
    val dark: Int = 0xFF000000.toInt(),
    val light: Int = 0xFFFFFFFF.toInt(),
    /** Silhouette steepness for the duotone (1 = soft, 5 = near hard threshold). */
    val threshold: Float = 0f,
    /** Lino-cut / worn-plate gouge marks inside the duotone. */
    val carve: Float = 0f,
    /** Paper fibre + blotch texture. */
    val paper: Float = 0f,
    val bloom: Float = 0f,
    val aberration: Float = 0f,
    val flare: Float = 0f,
    val flareX: Float = 0.8f,
    val flareY: Float = 0.2f,
    val flareColor: Int = 0xFFFFE0B0.toInt(),
    val grain: Float = 0f,
    /** Grain cells across the image's short side. */
    val grainScale: Float = 260f,
    val vignette: Float = 0f,
    /** Colour the edges are multiplied towards (black = plain darkening). */
    val vignetteColor: Int = 0xFF000000.toInt(),
    val ornament: CoverOrnament = CoverOrnament.None,
    val ornamentColor: Int = 0xFF000000.toInt(),
    val ornamentAlpha: Float = 1f,
    /** Press the ornament into the paper (shadow + highlight) instead of printing it. */
    val deboss: Boolean = false,
)

@Immutable
data class CoverFilterCategory(val id: String, val label: String)

object CoverFilters {

    /** AGSL RuntimeShader needs Android 13 (API 33). */
    val effectsSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val categories: List<CoverFilterCategory> = listOf(
        CoverFilterCategory("eras", "Eras"),
        CoverFilterCategory("mood", "Mood"),
        CoverFilterCategory("mono", "Mono"),
        CoverFilterCategory("time", "Time"),
        CoverFilterCategory("prints", "Prints"),
    )

    val presets: List<CoverFilterPreset> = listOf(
        // Eras: film stocks and print looks, newest first.
        CoverFilterPreset("era_lofi", "Lo-Fi", "eras",
            concat(offset(26f, 22f, 30f), concat(channels(0.95f, 0.98f, 1.02f), concat(contrast(0.78f), saturation(0.75f)))),
            CoverFilterEffect(bloom = 0.3f, grain = 0.03f, vignette = 0.15f)),
        CoverFilterPreset("era_digicam", "Digicam", "eras",
            concat(offset(-6f, 0f, 10f), concat(brightness(8f), concat(contrast(1.3f), saturation(1.05f)))),
            CoverFilterEffect(aberration = 0.2f, grain = 0.025f, vignette = 0.25f)),
        CoverFilterPreset("era_grunge", "Grunge", "eras",
            concat(brightness(-14f), concat(contrast(1.35f), saturation(0.45f))),
            CoverFilterEffect(grain = 0.12f, grainScale = 180f, vignette = 0.3f)),
        CoverFilterPreset("era_90s", "90s", "eras",
            concat(offset(-4f, 6f, 2f), concat(channels(0.98f, 1.03f, 1.0f), concat(contrast(0.94f), saturation(0.9f))))),
        CoverFilterPreset("era_synth", "Synth", "eras",
            concat(offset(-6f, -4f, 16f), concat(channels(1.08f, 0.92f, 1.12f), concat(contrast(1.15f), saturation(1.55f)))),
            CoverFilterEffect(bloom = 0.45f, aberration = 0.6f, vignette = 0.2f)),
        CoverFilterPreset("era_80s", "80s", "eras",
            concat(channels(1.08f, 0.95f, 1.07f), concat(contrast(1.12f), saturation(1.3f)))),
        CoverFilterPreset("era_vinyl", "Vinyl", "eras",
            concat(offset(20f, 14f, 0f), concat(channels(1.03f, 0.98f, 0.84f), concat(contrast(0.82f), saturation(0.7f)))),
            CoverFilterEffect(grain = 0.07f, vignette = 0.15f)),
        CoverFilterPreset("era_70s", "70s", "eras",
            concat(offset(22f, 10f, -12f), concat(channels(1.04f, 0.98f, 0.82f), concat(contrast(0.88f), saturation(0.78f))))),
        CoverFilterPreset("era_psyche", "Psyche", "eras",
            concat(offset(12f, 4f, -10f), concat(channels(1.08f, 1.0f, 0.88f), concat(contrast(1.08f), saturation(1.25f)))),
            CoverFilterEffect(bloom = 0.4f, aberration = 0.35f, grain = 0.04f)),
        CoverFilterPreset("era_60s", "60s", "eras",
            concat(offset(16f, 16f, 8f), concat(channels(1.04f, 1.02f, 0.84f), concat(contrast(0.9f), saturation(0.62f))))),
        CoverFilterPreset("era_50s", "50s", "eras",
            concat(offset(12f, 6f, -4f), concat(contrast(1.06f), saturation(0.3f)))),

        // Mood
        CoverFilterPreset("mood_warm", "Warm", "mood",
            concat(offset(10f, 4f, -8f), channels(1.06f, 1.0f, 0.9f))),
        CoverFilterPreset("mood_cool", "Cool", "mood",
            concat(offset(-8f, 2f, 12f), channels(0.92f, 1.0f, 1.08f))),
        CoverFilterPreset("mood_vivid", "Vivid", "mood",
            concat(contrast(1.1f), saturation(1.45f))),
        CoverFilterPreset("mood_fade", "Fade", "mood",
            concat(offset(18f, 18f, 18f), concat(contrast(0.8f), saturation(0.75f)))),
        CoverFilterPreset("mood_drama", "Drama", "mood",
            concat(brightness(-10f), concat(contrast(1.3f), saturation(0.85f)))),
        CoverFilterPreset("mood_euphoria", "Euphoria", "mood",
            concat(offset(16f, 10f, -6f), concat(channels(1.06f, 1.02f, 0.92f), concat(contrast(1.05f), saturation(1.25f)))),
            CoverFilterEffect(bloom = 0.35f, flare = 0.3f, flareX = 0.8f, flareY = 0.15f, flareColor = 0xFFFFD9A0.toInt())),
        CoverFilterPreset("mood_melancholy", "Melancholy", "mood",
            concat(offset(-6f, 0f, 10f), concat(channels(0.94f, 0.98f, 1.06f), concat(contrast(0.9f), saturation(0.6f)))),
            CoverFilterEffect(vignette = 0.55f)),
        CoverFilterPreset("mood_distortion", "Distortion", "mood",
            concat(brightness(-16f), concat(contrast(1.5f), saturation(1.1f))),
            CoverFilterEffect(aberration = 0.5f, grain = 0.16f, grainScale = 150f, vignette = 0.2f)),
        CoverFilterPreset("mood_ethereal", "Ethereal", "mood",
            concat(offset(18f, 14f, 22f), concat(channels(1.02f, 0.98f, 1.04f), concat(contrast(0.82f), saturation(0.85f)))),
            CoverFilterEffect(bloom = 0.7f, grain = 0.02f)),
        CoverFilterPreset("mood_nocturne", "Nocturne", "mood",
            concat(offset(-10f, -14f, 8f), concat(channels(1.02f, 0.85f, 1.15f), concat(contrast(1.2f), saturation(0.9f)))),
            CoverFilterEffect(bloom = 0.2f, vignette = 0.75f, vignetteColor = 0xFF260C33.toInt())),
        CoverFilterPreset("mood_acoustic", "Acoustic", "mood",
            concat(offset(4f, 3f, 0f), concat(contrast(0.97f), saturation(0.96f)))),

        // Mono
        CoverFilterPreset("mono_bw", "B&W", "mono", saturation(0f)),
        CoverFilterPreset("mono_noir", "Noir", "mono",
            concat(brightness(-12f), concat(contrast(1.4f), saturation(0f)))),
        CoverFilterPreset("mono_silver", "Silver", "mono",
            concat(offset(14f, 16f, 20f), concat(contrast(0.85f), saturation(0f)))),
        CoverFilterPreset("mono_sepia", "Sepia", "mono", sepia()),
        CoverFilterPreset("mono_halide", "Halide", "mono",
            concat(contrast(1.05f), saturation(0f)),
            CoverFilterEffect(grain = 0.09f, grainScale = 240f)),
        CoverFilterPreset("mono_ink", "Ink", "mono",
            concat(brightness(-20f), concat(contrast(1.85f), saturation(0f))),
            CoverFilterEffect(grain = 0.03f, vignette = 0.35f)),
        CoverFilterPreset("mono_charcoal", "Charcoal", "mono",
            concat(offset(26f, 26f, 26f), concat(contrast(0.7f), saturation(0f))),
            CoverFilterEffect(paper = 0.2f, grain = 0.04f)),
        CoverFilterPreset("mono_infrared", "Infrared", "mono",
            concat(brightness(6f), concat(contrast(1.1f), mono(-0.25f, 1.55f, -0.3f))),
            CoverFilterEffect(bloom = 0.55f, grain = 0.04f)),
        CoverFilterPreset("mono_daguerre", "Daguerre", "mono",
            concat(offset(10f, 2f, -14f), concat(contrast(0.92f), sepia())),
            CoverFilterEffect(paper = 0.25f, grain = 0.05f, vignette = 0.45f, vignetteColor = 0xFF59381A.toInt())),
        CoverFilterPreset("mono_cyanotype", "Cyanotype", "mono",
            duotoneMatrix(0xFF102C5E.toInt(), 0xFFE4ECEE.toInt(), 1.15f),
            CoverFilterEffect(paper = 0.15f)),

        // Time: list order is the day, dawn (left) to night (right).
        CoverFilterPreset("time_dawn", "Dawn", "time",
            concat(offset(-18f, -2f, 22f), concat(channels(0.86f, 0.97f, 1.12f), concat(contrast(0.92f), saturation(0.85f)))),
            CoverFilterEffect(vignette = 0.2f, vignetteColor = 0xFF333373.toInt())),
        CoverFilterPreset("time_sunrise", "Sunrise", "time",
            concat(offset(20f, 10f, 0f), concat(channels(1.06f, 1.0f, 0.9f), concat(contrast(0.86f), saturation(0.95f)))),
            CoverFilterEffect(bloom = 0.35f, flare = 0.25f, flareX = 0.15f, flareY = 0.55f, flareColor = 0xFFFFC8A0.toInt())),
        CoverFilterPreset("time_dew", "Dew", "time",
            concat(channels(0.97f, 1.04f, 1.05f), concat(contrast(1.06f), saturation(1.15f)))),
        CoverFilterPreset("time_noon", "Noon", "time",
            concat(offset(-2f, 0f, 6f), concat(brightness(4f), concat(contrast(1.28f), saturation(1.0f))))),
        CoverFilterPreset("time_golden", "Golden", "time",
            concat(offset(24f, 12f, -16f), concat(channels(1.1f, 1.0f, 0.8f), concat(contrast(1.02f), saturation(1.2f)))),
            CoverFilterEffect(bloom = 0.25f, flare = 0.45f, flareX = 0.85f, flareY = 0.25f, flareColor = 0xFFFFC870.toInt())),
        CoverFilterPreset("time_sundown", "Sundown", "time",
            concat(offset(14f, -4f, -14f), concat(channels(1.16f, 0.86f, 0.78f), concat(brightness(-8f), concat(contrast(1.3f), saturation(1.25f))))),
            CoverFilterEffect(flare = 0.3f, flareX = 0.5f, flareY = 0.75f, flareColor = 0xFFFF6A3A.toInt(), vignette = 0.35f)),
        CoverFilterPreset("time_dusk", "Dusk", "time",
            concat(offset(4f, -6f, 24f), concat(channels(0.98f, 0.86f, 1.18f), concat(contrast(1.1f), saturation(1.15f)))),
            CoverFilterEffect(bloom = 0.15f, vignette = 0.3f, vignetteColor = 0xFF401A66.toInt())),
        CoverFilterPreset("time_midnight", "Midnight", "time",
            concat(offset(-14f, -10f, 2f), concat(channels(0.85f, 0.9f, 1.04f), concat(contrast(1.3f), saturation(0.6f)))),
            CoverFilterEffect(grain = 0.04f, vignette = 0.55f)),
        CoverFilterPreset("time_neon", "Neon", "time",
            concat(offset(-8f, -10f, 0f), concat(channels(1.08f, 0.92f, 1.1f), concat(contrast(1.35f), saturation(1.4f)))),
            CoverFilterEffect(bloom = 0.5f, aberration = 0.25f, vignette = 0.5f)),

        // Prints: duotone silhouettes on coloured paper. The matrix is a flat duotone fallback;
        // with the shader the grade only sets exposure and the shader does the duotone.
        printPreset("print_magenta", "Magenta", dark = 0xFF140A12.toInt(), light = 0xFFE01F8A.toInt(), push = 1.7f,
            CoverFilterEffect(grade = concat(brightness(-6f), contrast(1.25f)), threshold = 3.5f,
                paper = 0.35f, grain = 0.05f, ornament = CoverOrnament.Filigree, ornamentAlpha = 0.95f)),
        printPreset("print_botanic", "Botanic", dark = 0xFF0B2A5C.toInt(), light = 0xFFE6EEF0.toInt(), push = 1.2f,
            CoverFilterEffect(grade = contrast(1.05f), threshold = 1.4f, bloom = 0.35f,
                paper = 0.3f, grain = 0.04f, ornament = CoverOrnament.Botanical, ornamentAlpha = 0.85f)),
        printPreset("print_vellum", "Vellum", dark = 0xFF1C1A18.toInt(), light = 0xFFEEE2C6.toInt(), push = 1.9f,
            CoverFilterEffect(grade = contrast(1.2f), threshold = 4.5f, paper = 0.45f, grain = 0.03f,
                vignette = 0.25f, vignetteColor = 0xFFD8C59A.toInt())),
        printPreset("print_ochre", "Ochre", dark = 0xFF3A2210.toInt(), light = 0xFFC88A2A.toInt(), push = 1.4f,
            CoverFilterEffect(grade = contrast(1.1f), threshold = 2.2f, paper = 0.3f,
                flare = 0.35f, flareX = 0.25f, flareY = 0.2f, flareColor = 0xFFFFE6B0.toInt(),
                ornament = CoverOrnament.Bookbinding, ornamentAlpha = 0.6f, deboss = true)),
        printPreset("print_lino", "Lino", dark = 0xFF0E120E.toInt(), light = 0xFF1F4D33.toInt(), push = 1.6f,
            CoverFilterEffect(grade = contrast(1.15f), threshold = 3f, carve = 0.8f, paper = 0.25f,
                ornament = CoverOrnament.Leaf, ornamentAlpha = 0.9f)),
        printPreset("print_rust", "Rust", dark = 0xFF242222.toInt(), light = 0xFFB54A1E.toInt(), push = 1.5f,
            CoverFilterEffect(grade = contrast(1.1f), threshold = 2.5f, carve = 0.35f, paper = 0.7f, grain = 0.08f,
                ornament = CoverOrnament.Gears, ornamentColor = 0xFF5A3A2A.toInt(), ornamentAlpha = 0.55f)),
    )

    private val byIdMap: Map<String, CoverFilterPreset> = presets.associateBy { it.id }

    fun byId(id: String?): CoverFilterPreset? = id?.let { byIdMap[it] }

    fun inCategory(categoryId: String): List<CoverFilterPreset> = presets.filter { it.categoryId == categoryId }

    /** A Prints preset: duotone fallback matrix + the effect, with the duotone colours filled in. */
    private fun printPreset(id: String, label: String, dark: Int, light: Int, push: Float, effect: CoverFilterEffect) =
        CoverFilterPreset(
            id, label, "prints",
            duotoneMatrix(dark, light, push),
            effect.copy(
                duotone = 1f, dark = dark, light = light,
                // Ornaments default to the ink colour unless the preset picked its own.
                ornamentColor = if (effect.ornamentColor == 0xFF000000.toInt()) dark else effect.ornamentColor
            )
        )

    // ---- 4x5 colour-matrix helpers --------------------------------------------------------

    internal const val LR = 0.2126f
    internal const val LG = 0.7152f
    internal const val LB = 0.0722f

    internal fun saturation(s: Float): FloatArray {
        val inv = 1f - s
        val r = inv * LR
        val g = inv * LG
        val b = inv * LB
        return floatArrayOf(
            r + s, g, b, 0f, 0f,
            r, g + s, b, 0f, 0f,
            r, g, b + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Scales around mid-grey so contrast doesn't shift overall brightness. */
    internal fun contrast(c: Float): FloatArray {
        val t = 128f * (1f - c)
        return floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    internal fun brightness(b: Float): FloatArray = offset(b, b, b)

    internal fun offset(r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, r,
        0f, 1f, 0f, 0f, g,
        0f, 0f, 1f, 0f, b,
        0f, 0f, 0f, 1f, 0f,
    )

    internal fun channels(r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
        r, 0f, 0f, 0f, 0f,
        0f, g, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Grey from custom channel weights (e.g. an infrared-style green-heavy mix). */
    private fun mono(r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
        r, g, b, 0f, 0f,
        r, g, b, 0f, 0f,
        r, g, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Luminance mapped linearly from [dark] (black) to [light] (white), after a contrast push. */
    private fun duotoneMatrix(dark: Int, light: Int, push: Float): FloatArray {
        fun ch(c: Int, shift: Int) = ((c shr shift) and 0xFF).toFloat()
        val d = floatArrayOf(ch(dark, 16), ch(dark, 8), ch(dark, 0))
        val l = floatArrayOf(ch(light, 16), ch(light, 8), ch(light, 0))
        val out = FloatArray(20)
        for (row in 0 until 3) {
            val k = (l[row] - d[row]) / 255f
            out[row * 5 + 0] = LR * k
            out[row * 5 + 1] = LG * k
            out[row * 5 + 2] = LB * k
            out[row * 5 + 4] = d[row]
        }
        out[18] = 1f
        return concat(out, contrast(push))
    }

    private fun sepia(): FloatArray = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Returns `a ∘ b`: the colour is transformed by [b] first, then by [a]. */
    internal fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) sum += a[row * 5 + k] * b[k * 5 + col]
                if (col == 4) sum += a[row * 5 + 4]
                out[row * 5 + col] = sum
            }
        }
        return out
    }
}
