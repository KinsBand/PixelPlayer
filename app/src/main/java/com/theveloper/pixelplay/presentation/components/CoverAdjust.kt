package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.Immutable
import kotlin.math.abs

/**
 * Per-layer "Adjust" tools for cover art (Google Photos style) and the resolved look of a layer:
 * preset at a strength, then these adjustments.
 *
 * Values are whole steps: -100..100 for bipolar tools, 0..100 for the rest; 0 is always "off".
 * Colour-only tools ([needsShader] = false) are a 4x5 colour matrix and work on every Android
 * version; the rest run in the AGSL adjust stage (CoverAdjustShader, Android 13+).
 */
enum class CoverAdjustTool(
    val id: String,
    val label: String,
    val min: Int,
    val needsShader: Boolean
) {
    Brightness("brightness", "Brightness", -100, false),
    Contrast("contrast", "Contrast", -100, false),
    UltraHdr("ultra_hdr", "Ultra HDR", 0, true),
    Tone("tone", "Tone", -100, true),
    WhitePoint("white_point", "White point", -100, false),
    Highlights("highlights", "Highlights", -100, true),
    Shadows("shadows", "Shadows", -100, true),
    BlackPoint("black_point", "Black point", -100, false),
    Saturation("saturation", "Saturation", -100, false),
    Warmth("warmth", "Warmth", -100, false),
    Tint("tint", "Tint", -100, false),
    BlueTone("blue_tone", "Blue tone", -100, true),
    Vignette("vignette", "Vignette", -100, true),
    Pop("pop", "Pop", 0, true),
    Sharpen("sharpen", "Sharpen", 0, true),
    Denoise("denoise", "Denoise", 0, true);

    val max: Int get() = 100

    /** Shown on this device: shader tools need Android 13. */
    val isAvailable: Boolean get() = !needsShader || CoverFilters.effectsSupported

    companion object {
        val available: List<CoverAdjustTool> get() = entries.filter { it.isAvailable }
    }
}

/** Adjust values of one layer. Only non-zero values are stored, so equality means "same look". */
@Immutable
data class CoverAdjustments(private val values: Map<CoverAdjustTool, Int> = emptyMap()) {

    operator fun get(tool: CoverAdjustTool): Int = values[tool] ?: 0

    fun with(tool: CoverAdjustTool, value: Int): CoverAdjustments {
        val v = value.coerceIn(tool.min, tool.max)
        val next = values.toMutableMap()
        if (v == 0) next.remove(tool) else next[tool] = v
        return CoverAdjustments(next)
    }

    val isDefault: Boolean get() = values.isEmpty()

    /** Something the AGSL adjust stage has to do (and can do on this device). */
    val needsShaderStage: Boolean
        get() = CoverFilters.effectsSupported && values.keys.any { it.needsShader }

    /** Stable text form for change signatures and effect caches. */
    val key: String
        get() = values.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.id}=${it.value}" }

    /** -1..1 (or 0..1) value the maths uses. */
    fun unit(tool: CoverAdjustTool): Float = this[tool] / 100f

    /** Colour-only tools as one 4x5 matrix (identity when none are set). */
    fun matrix(): FloatArray {
        var m = CoverLookMath.identity()
        fun then(next: FloatArray) { m = CoverFilters.concat(next, m) }

        val bp = unit(CoverAdjustTool.BlackPoint)
        if (bp > 0f) {
            // Raise the black point: everything below it goes to black, the rest is stretched.
            val b = bp * 0.25f * 255f
            val s = 255f / (255f - b)
            then(floatArrayOf(s, 0f, 0f, 0f, -b * s, 0f, s, 0f, 0f, -b * s, 0f, 0f, s, 0f, -b * s, 0f, 0f, 0f, 1f, 0f))
        } else if (bp < 0f) {
            // Lift the blacks (faded look).
            val k = -bp * 0.22f
            then(floatArrayOf(1f - k, 0f, 0f, 0f, k * 255f, 0f, 1f - k, 0f, 0f, k * 255f, 0f, 0f, 1f - k, 0f, k * 255f, 0f, 0f, 0f, 1f, 0f))
        }
        val wp = unit(CoverAdjustTool.WhitePoint)
        if (wp != 0f) {
            val s = 1f + wp * 0.3f
            then(CoverFilters.channels(s, s, s))
        }
        val br = unit(CoverAdjustTool.Brightness)
        if (br != 0f) {
            // Mostly a lift, plus a little gain so highlights move too.
            then(CoverFilters.concat(CoverFilters.brightness(br * 48f), CoverFilters.channels(1f + br * 0.12f, 1f + br * 0.12f, 1f + br * 0.12f)))
        }
        val ct = unit(CoverAdjustTool.Contrast)
        if (ct != 0f) then(CoverFilters.contrast(1f + ct * if (ct > 0f) 0.6f else 0.55f))
        val sat = unit(CoverAdjustTool.Saturation)
        if (sat != 0f) then(CoverFilters.saturation(1f + sat))
        val warm = unit(CoverAdjustTool.Warmth)
        if (warm != 0f) {
            then(CoverFilters.concat(CoverFilters.offset(10f * warm, 2f * warm, -10f * warm), CoverFilters.channels(1f + 0.1f * warm, 1f, 1f - 0.1f * warm)))
        }
        val tint = unit(CoverAdjustTool.Tint)
        if (tint != 0f) then(CoverFilters.offset(7f * tint, -12f * tint, 7f * tint)) // + = magenta, − = green
        return m
    }
}

/**
 * Everything that decides how one layer looks: the preset at [strength] (0..100), then the
 * [adjust] tools. Pipeline (preview and export are the same):
 * colour [matrix] (preset lerped by strength, then adjust matrix) -> [presetEffect] -> adjust stage.
 */
@Immutable
data class CoverLook(
    val preset: CoverFilterPreset?,
    val strength: Int = 100,
    val adjust: CoverAdjustments = CoverAdjustments()
) {
    private val t: Float get() = (strength.coerceIn(0, 100)) / 100f

    /** Nothing to draw differently from the plain photo. */
    val isIdentity: Boolean get() = (preset == null || strength <= 0) && adjust.isDefault

    /** The preset's own matrix at [strength], then the adjust tools' matrix. */
    val matrix: FloatArray
        get() {
            val presetMatrix = preset?.let { CoverLookMath.lerp(CoverLookMath.identity(), it.activeMatrix, t) }
                ?: CoverLookMath.identity()
            return CoverFilters.concat(adjust.matrix(), presetMatrix)
        }

    /** Null when [matrix] is the identity (lets callers skip the colour filter). */
    val matrixOrNull: FloatArray? get() = if (isIdentity) null else matrix

    /** The preset's shader stage with every strength scaled by [strength], or null. */
    val presetEffect: CoverFilterEffect?
        get() {
            val e = preset?.activeEffect ?: return null
            if (strength <= 0) return null
            if (strength >= 100) return e
            val s = t
            return e.copy(
                duotone = e.duotone * s,
                carve = e.carve * s,
                paper = e.paper * s,
                bloom = e.bloom * s,
                aberration = e.aberration * s,
                flare = e.flare * s,
                grain = e.grain * s,
                vignette = e.vignette * s,
                ornamentAlpha = e.ornamentAlpha * s
            )
        }

    /** Anything that needs the GPU (Android 13+) to look right. */
    val needsGpu: Boolean get() = presetEffect != null || adjust.needsShaderStage

    /** Stable id for caches and change signatures. */
    val key: String get() = "${preset?.id}@$strength[${adjust.key}]"

    companion object {
        val None = CoverLook(null)
    }
}

/** The look a layer is drawn with. */
val CoverArtLayer.look: CoverLook
    get() = CoverLook(CoverFilters.byId(filterId), filterStrength, adjust)

internal object CoverLookMath {
    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun lerp(a: FloatArray, b: FloatArray, t: Float): FloatArray =
        FloatArray(20) { i -> a[i] + (b[i] - a[i]) * t }

    fun isIdentity(m: FloatArray): Boolean {
        val id = identity()
        return m.indices.all { abs(m[it] - id[it]) < 1e-5f }
    }
}
