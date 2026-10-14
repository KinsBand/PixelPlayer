package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * AGSL side of the cover filters (Android 13+). One shader runs every [CoverFilterEffect] stage,
 * after the preset's colour matrix:
 * aberration -> bloom -> (carve -> threshold -> duotone) -> paper -> flare -> grain -> vignette -> ornament.
 *
 * Everything is measured in image-relative units (UV, or the short side = 1), never pixels, so a
 * 220px card thumbnail, the live canvas and the 1024px export show the same picture.
 * The shader is static on purpose: the result is baked into the saved JPEG.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object CoverFilterShader {

    const val INPUT = "image"

    private const val SOURCE = """
uniform shader image;
uniform float2 size;
uniform float duotone;
uniform float3 dark;
uniform float3 light;
uniform float threshold;
uniform float carve;
uniform float paper;
uniform float bloom;
uniform float aberration;
uniform float flare;
uniform float2 flarePos;
uniform float3 flareColor;
uniform float grain;
uniform float grainScale;
uniform float vignette;
uniform float3 vignetteColor;
uniform float ornament;
uniform float4 ornamentColor;
uniform float deboss;

float hash(float2 q) {
    q = fract(q * float2(0.1031, 0.1030));
    q += dot(q, q.yx + 33.33);
    return fract((q.x + q.y) * q.x);
}

float vnoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 q) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(q);
        q = q * 2.03 + float2(17.0, 9.0);
        a *= 0.5;
    }
    return s;
}

float4 sampleAt(float2 p) {
    return float4(image.eval(clamp(p, float2(0.5), size - float2(0.5))));
}

float3 unpremulRgb(float4 c) {
    return c.a > 0.0001 ? c.rgb / c.a : float3(0.0);
}

float luma(float3 c) {
    return dot(c, float3(0.2126, 0.7152, 0.0722));
}

// 1 on a stroke of half-width w around d = 0, antialiased over aa.
float stroke(float d, float w, float aa) {
    return 1.0 - smoothstep(w, w + aa, abs(d));
}

float disc(float d, float aa) {
    return 1.0 - smoothstep(0.0, aa, d);
}

// ---- Corner ornaments. q: distance from the nearest corner, short side = 1. ----

float filigree(float2 q, float aa) {
    float r = length(q);
    float a = atan(q.y, q.x);
    float zone = disc(r - 0.19, aa) * step(0.05, r);
    float rs = r + 0.006 * sin(a * 26.0);
    float arcs = stroke(fract(rs / 0.034) - 0.5, 0.06, aa / 0.034) * zone;
    float2 c1 = float2(0.2, 0.045);
    float2 c2 = c1.yx;
    float curl = max(stroke(length(q - c1) - 0.026, 0.0035, aa), stroke(length(q - c2) - 0.026, 0.0035, aa));
    curl = max(curl, max(disc(length(q - c1) - 0.009, aa), disc(length(q - c2) - 0.009, aa)));
    float fleur = disc(length(q - float2(0.03)) - 0.022, aa);
    float petals = stroke(length(q - float2(0.075)) - 0.02, 0.003, aa);
    return max(max(arcs, curl), max(fleur, petals));
}

float botanical(float2 q, float aa) {
    float s = (q.x + q.y) * 0.70710678;
    float t = (q.y - q.x) * 0.70710678;
    float len = 0.27;
    float on = step(0.015, s) * disc(s - len, aa);
    float tc = t - 0.035 * sin(s * 9.0);
    float stem = stroke(tc, 0.0022 * (1.2 - s / len), aa) * on;
    float spacing = 0.022;
    float du = (fract(s / spacing) - 0.5) * spacing;
    float leafLen = 0.055 * (1.0 - s / len) + 0.008;
    float e = length(float2(du / 0.0065, (abs(tc) - leafLen * 0.5 - 0.003) / (leafLen * 0.5)));
    float leaves = disc(e - 1.0, aa / 0.0065) * on;
    float2 dm = q - float2(0.235, 0.06);
    float wings = disc(length(float2(abs(dm.x) - 0.011, dm.y) / float2(0.011, 0.008)) - 1.0, aa / 0.008);
    float body = disc(length(dm / float2(0.003, 0.012)) - 1.0, aa / 0.003);
    return max(max(stem, leaves), max(wings, body));
}

float lshape(float2 q, float a, float len, float w, float aa) {
    float h = stroke(q.y - a, w, aa) * step(a - w, q.x) * disc(q.x - len, aa);
    float v = stroke(q.x - a, w, aa) * step(a - w, q.y) * disc(q.y - len, aa);
    return max(h, v);
}

float bookbinding(float2 q, float aa) {
    float m = lshape(q, 0.03, 0.26, 0.004, aa);
    m = max(m, lshape(q, 0.052, 0.19, 0.0025, aa));
    m = max(m, lshape(q, 0.07, 0.13, 0.0025, aa));
    float dia = abs(q.x - 0.11) + abs(q.y - 0.11);
    m = max(m, stroke(dia - 0.03, 0.003, aa));
    m = max(m, disc(dia - 0.012, aa));
    return m;
}

float leafGrid(float2 q, float aa) {
    float zone = disc(q.x + q.y - 0.24, aa) * step(0.02, q.x + q.y);
    float cell = 0.045;
    float2 r = float2(q.x + q.y, q.y - q.x) * 0.70710678 / cell;
    float2 f = fract(r) - 0.5;
    float lens = max(length(f - float2(0.0, 0.32)), length(f + float2(0.0, 0.32))) - 0.45;
    float outline = stroke(lens, 0.035, aa / cell);
    float rib = stroke(f.y, 0.025, aa / cell) * step(abs(f.x), 0.3);
    return max(outline, rib) * zone;
}

float gear(float2 q, float2 c, float rad, float teeth, float aa) {
    float2 d = q - c;
    float r = length(d);
    float a = atan(d.y, d.x);
    float edge = rad + 0.009 * smoothstep(-0.25, 0.25, sin(a * teeth));
    float rim = stroke(r - edge, 0.003, aa);
    float hub = stroke(r - rad * 0.35, 0.003, aa);
    float spokes = stroke(sin(a * 2.0) * r, 0.0025, aa) * step(rad * 0.35, r) * step(r, rad - 0.004);
    return max(rim, max(hub, spokes));
}

float gears(float2 q, float aa) {
    return max(gear(q, float2(0.075), 0.05, 12.0, aa), gear(q, float2(0.165, 0.045), 0.03, 8.0, aa));
}

float ornamentMask(float2 q, float kind, float aa) {
    if (kind < 0.5) return 0.0;
    if (kind < 1.5) return filigree(q, aa);
    if (kind < 2.5) return botanical(q, aa);
    if (kind < 3.5) return bookbinding(q, aa);
    if (kind < 4.5) return leafGrid(q, aa);
    return gears(q, aa);
}

half4 main(float2 p) {
    float minSide = min(size.x, size.y);
    float2 uv = p / size;
    float2 pu = p / minSide;
    float aa = 1.2 / minSide;

    float4 src = sampleAt(p);
    float alpha = src.a;
    float3 c = unpremulRgb(src);

    if (aberration > 0.0) {
        float2 off = (uv - 0.5) * aberration * 0.012 * minSide;
        c.r = unpremulRgb(sampleAt(p + off)).r;
        c.b = unpremulRgb(sampleAt(p - off)).b;
    }

    if (bloom > 0.0) {
        float3 acc = float3(0.0);
        for (int i = 0; i < 12; i++) {
            float fi = float(i);
            float ang = fi * 2.3999632;
            float rad = (0.004 + 0.003 * fi) * minSide;
            float3 s = unpremulRgb(sampleAt(p + float2(cos(ang), sin(ang)) * rad));
            acc += max(s - 0.55, 0.0);
        }
        c += bloom * acc / 12.0 * 2.2;
    }

    if (duotone > 0.0) {
        float l = clamp(luma(c), 0.0, 1.0);
        if (carve > 0.0) {
            float warp = fbm(pu * 5.0) * 2.0;
            float gouge = stroke(fract(pu.y * 48.0 + warp) - 0.5, 0.16, 0.1);
            float mid = smoothstep(0.12, 0.5, l) * (1.0 - smoothstep(0.6, 0.85, l));
            l = mix(l, 1.0, carve * gouge * mid);
            l += carve * (vnoise(pu * 90.0) - 0.5) * 0.18;
        }
        if (threshold > 0.0) {
            float w = 0.5 / max(threshold, 0.5);
            l = smoothstep(0.5 - w, 0.5 + w, l);
        }
        c = mix(c, mix(dark, light, clamp(l, 0.0, 1.0)), duotone);
    }

    if (paper > 0.0) {
        float fibre = fbm(pu * float2(160.0, 22.0));
        float blotch = fbm(pu * 4.0 + float2(3.1, 7.7));
        float tex = 0.55 * fibre + 0.45 * blotch;
        c *= 1.0 - paper * (tex - 0.3) * 0.45;
        float speck = smoothstep(0.78, 0.86, vnoise(pu * 70.0)) * paper * 0.5;
        c = mix(c, max(c, light), speck);
    }

    if (flare > 0.0) {
        float2 fp = flarePos * size / minSide;
        float2 centre = 0.5 * size / minSide;
        float d = length(pu - fp);
        float glow = exp(-d * d * 14.0) * 0.85 + 0.1 * stroke(d - 0.2, 0.012, 0.03);
        glow += 0.08 * disc(length(pu - (2.0 * centre - fp)) - 0.03, 0.03);
        float3 add = clamp(flareColor * flare * glow, 0.0, 1.0);
        c = 1.0 - (1.0 - clamp(c, 0.0, 1.0)) * (1.0 - add);
    }

    if (grain > 0.0) {
        float g = vnoise(pu * grainScale) * 0.65 + vnoise(pu * grainScale * 2.37 + 11.0) * 0.35 - 0.5;
        c += g * grain * 2.0;
    }

    if (vignette > 0.0) {
        float v = smoothstep(0.35, 1.0, length((uv - 0.5) * 2.0) * 0.75);
        c = mix(c, c * vignetteColor, v * vignette);
    }

    if (ornament > 0.5) {
        float2 q = min(p, size - p) / minSide;
        float m = ornamentMask(q, ornament, aa) * ornamentColor.a;
        if (deboss > 0.5) {
            float ms = ornamentMask(q + float2(0.004), ornament, aa) * ornamentColor.a;
            c = c * (1.0 - 0.45 * m) + 0.25 * max(ms - m, 0.0);
        } else {
            c = mix(c, ornamentColor.rgb, m);
        }
    }

    c = clamp(c, 0.0, 1.0);
    return half4(half3(c * alpha), half(alpha));
}
"""

    private fun RuntimeShader.setColor(name: String, argb: Int) = setFloatUniform(
        name,
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f
    )

    fun create(effect: CoverFilterEffect, width: Float, height: Float): RuntimeShader =
        RuntimeShader(SOURCE).apply { applyUniforms(effect, width, height) }

    /** Writes every uniform; lets the preview reuse one compiled shader. */
    fun RuntimeShader.applyUniforms(effect: CoverFilterEffect, width: Float, height: Float) {
        run {
            setFloatUniform("size", width, height)
            setFloatUniform("duotone", effect.duotone)
            setColor("dark", effect.dark)
            setColor("light", effect.light)
            setFloatUniform("threshold", effect.threshold)
            setFloatUniform("carve", effect.carve)
            setFloatUniform("paper", effect.paper)
            setFloatUniform("bloom", effect.bloom)
            setFloatUniform("aberration", effect.aberration)
            setFloatUniform("flare", effect.flare)
            setFloatUniform("flarePos", effect.flareX, effect.flareY)
            setColor("flareColor", effect.flareColor)
            setFloatUniform("grain", effect.grain)
            setFloatUniform("grainScale", effect.grainScale)
            setFloatUniform("vignette", effect.vignette)
            setColor("vignetteColor", effect.vignetteColor)
            setFloatUniform("ornament", effect.ornament.shaderValue)
            setFloatUniform(
                "ornamentColor",
                ((effect.ornamentColor shr 16) and 0xFF) / 255f,
                ((effect.ornamentColor shr 8) and 0xFF) / 255f,
                (effect.ornamentColor and 0xFF) / 255f,
                effect.ornamentAlpha
            )
            setFloatUniform("deboss", if (effect.deboss) 1f else 0f)
        }
    }

    fun renderEffect(effect: CoverFilterEffect, width: Float, height: Float): RenderEffect =
        RenderEffect.createRuntimeShaderEffect(create(effect, width, height), INPUT)

    // Built effects are reused: the graphicsLayer block re-runs on every size/state read.
    private val previewCache = LruCache<String, androidx.compose.ui.graphics.RenderEffect>(48)

    fun previewEffect(preset: CoverFilterPreset, effect: CoverFilterEffect, width: Float, height: Float):
        androidx.compose.ui.graphics.RenderEffect {
        val key = "${preset.id}:${width.roundToInt()}x${height.roundToInt()}"
        previewCache.get(key)?.let { return it }
        return renderEffect(effect, width, height).asComposeRenderEffect().also { previewCache.put(key, it) }
    }

    // Main-thread preview only: one compiled shader per stage, re-uniformed for each new look.
    // A RenderEffect keeps its own copy of the uniforms, so cached effects stay as they were.
    private var presetPreviewShader: RuntimeShader? = null
    private var adjustPreviewShader: RuntimeShader? = null
    private val lookCache = LruCache<String, androidx.compose.ui.graphics.RenderEffect>(32)

    /** Preview of a whole look's GPU stages (preset effect, then adjust), or null when it has none. */
    fun previewEffect(look: CoverLook, width: Float, height: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (!look.needsGpu) return null
        val key = "${look.key}:${width.roundToInt()}x${height.roundToInt()}"
        lookCache.get(key)?.let { return it }
        val presetStage = look.presetEffect?.let { effect ->
            val shader = presetPreviewShader ?: RuntimeShader(SOURCE).also { presetPreviewShader = it }
            shader.applyUniforms(effect, width, height)
            RenderEffect.createRuntimeShaderEffect(shader, INPUT)
        }
        val adjustStage = if (look.adjust.needsShaderStage) {
            val shader = adjustPreviewShader ?: CoverAdjustShader.newShader().also { adjustPreviewShader = it }
            with(CoverAdjustShader) { shader.applyUniforms(look.adjust, width, height) }
            RenderEffect.createRuntimeShaderEffect(shader, CoverAdjustShader.INPUT)
        } else null
        val combined = chain(presetStage, adjustStage) ?: return null
        return combined.asComposeRenderEffect().also { lookCache.put(key, it) }
    }

    /** Export: fresh shaders (any thread). */
    fun renderEffect(look: CoverLook, width: Float, height: Float): RenderEffect? {
        val presetStage = look.presetEffect?.let { renderEffect(it, width, height) }
        val adjustStage = if (look.adjust.needsShaderStage) {
            RenderEffect.createRuntimeShaderEffect(CoverAdjustShader.create(look.adjust, width, height), CoverAdjustShader.INPUT)
        } else null
        return chain(presetStage, adjustStage)
    }

    /** [first] runs on the image, then [second] on its result. */
    private fun chain(first: RenderEffect?, second: RenderEffect?): RenderEffect? = when {
        first != null && second != null -> RenderEffect.createChainEffect(second, first)
        else -> first ?: second
    }
}

/**
 * The Adjust tab's GPU tools, run after the preset stage:
 * denoise -> sharpen -> Ultra HDR / Pop (local contrast) -> tone -> highlights / shadows ->
 * blue tone -> vignette. Units are image-relative like the preset shader, except the 1–2 px
 * neighbourhoods of sharpen and denoise, which are pixel-scale by nature.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object CoverAdjustShader {

    const val INPUT = "image"

    private const val SOURCE = """
uniform shader image;
uniform float2 size;
uniform float tone;
uniform float highlights;
uniform float shadows;
uniform float hdr;
uniform float blueTone;
uniform float vignette;
uniform float pop;
uniform float sharpen;
uniform float denoise;

float4 at(float2 p) {
    return float4(image.eval(clamp(p, float2(0.5), size - float2(0.5))));
}

float3 rgbOf(float4 c) {
    return c.a > 0.0001 ? c.rgb / c.a : float3(0.0);
}

float luma(float3 c) {
    return dot(c, float3(0.2126, 0.7152, 0.0722));
}

float3 blurAt(float2 p, float r) {
    float3 acc = float3(0.0);
    for (int i = 0; i < 12; i++) {
        float fi = float(i);
        float ang = fi * 2.3999632;
        float rad = r * sqrt((fi + 0.5) / 12.0);
        acc += rgbOf(at(p + float2(cos(ang), sin(ang)) * rad));
    }
    return acc / 12.0;
}

half4 main(float2 p) {
    float minSide = min(size.x, size.y);
    float2 uv = p / size;
    float4 src = at(p);
    float alpha = src.a;
    float3 c = rgbOf(src);

    if (denoise > 0.0) {
        float r = max(1.0, minSide / 360.0) * (1.0 + denoise);
        float3 acc = c;
        float wsum = 1.0;
        for (int i = 0; i < 8; i++) {
            float ang = float(i) * 0.7853982;
            float3 s = rgbOf(at(p + float2(cos(ang), sin(ang)) * r));
            float3 d = s - c;
            float w = exp(-dot(d, d) * 40.0);
            acc += s * w;
            wsum += w;
        }
        c = mix(c, acc / wsum, denoise);
    }

    if (sharpen > 0.0) {
        float r = max(1.0, minSide / 700.0);
        float3 n = rgbOf(at(p + float2(r, 0.0))) + rgbOf(at(p - float2(r, 0.0))) +
            rgbOf(at(p + float2(0.0, r))) + rgbOf(at(p - float2(0.0, r)));
        c += (c - n * 0.25) * sharpen * 1.6;
    }

    if (hdr > 0.0 || pop > 0.0) {
        float3 detail = c - blurAt(p, minSide * 0.03);
        if (hdr > 0.0) {
            c += detail * hdr * 0.9;
            float l = luma(c);
            float lift = (1.0 - smoothstep(0.0, 0.45, l)) * 0.18 * hdr;
            float tame = smoothstep(0.6, 1.0, l) * 0.22 * hdr;
            c = c + lift - c * tame;
        }
        if (pop > 0.0) {
            c += detail * pop * 0.7;
            float l = luma(c);
            float mid = clamp(1.3 - abs(l - 0.5) * 2.0, 0.0, 1.0);
            c = mix(float3(l), c, 1.0 + pop * 0.35 * mid);
        }
    }

    if (tone != 0.0) {
        c = pow(clamp(c, 0.0, 1.0), float3(exp2(-tone * 0.7)));
    }

    if (highlights != 0.0 || shadows != 0.0) {
        float l = clamp(luma(c), 0.0, 1.0);
        float hw = smoothstep(0.45, 1.0, l);
        float sw = 1.0 - smoothstep(0.0, 0.55, l);
        if (highlights > 0.0) c += highlights * 0.28 * hw * (1.0 - c);
        else c += highlights * 0.28 * hw * c;
        if (shadows > 0.0) c += shadows * 0.3 * sw * (1.0 - c);
        else c += shadows * 0.3 * sw * c;
    }

    if (blueTone != 0.0) {
        float w = smoothstep(0.02, 0.25, c.b - max(c.r, c.g * 0.9));
        float l = luma(c);
        c = mix(float3(l), c, 1.0 + blueTone * w * 0.9);
        c.b += blueTone * w * 0.06;
    }

    if (vignette != 0.0) {
        float v = smoothstep(0.3, 1.0, length((uv - 0.5) * 2.0) * 0.75);
        if (vignette > 0.0) c *= 1.0 - vignette * v * 0.85;
        else c = mix(c, float3(1.0), -vignette * v * 0.75);
    }

    c = clamp(c, 0.0, 1.0);
    return half4(half3(c * alpha), half(alpha));
}
"""

    fun newShader(): RuntimeShader = RuntimeShader(SOURCE)

    fun create(adjust: CoverAdjustments, width: Float, height: Float): RuntimeShader =
        newShader().apply { applyUniforms(adjust, width, height) }

    fun RuntimeShader.applyUniforms(adjust: CoverAdjustments, width: Float, height: Float) {
        setFloatUniform("size", width, height)
        setFloatUniform("tone", adjust.unit(CoverAdjustTool.Tone))
        setFloatUniform("highlights", adjust.unit(CoverAdjustTool.Highlights))
        setFloatUniform("shadows", adjust.unit(CoverAdjustTool.Shadows))
        setFloatUniform("hdr", adjust.unit(CoverAdjustTool.UltraHdr))
        setFloatUniform("blueTone", adjust.unit(CoverAdjustTool.BlueTone))
        setFloatUniform("vignette", adjust.unit(CoverAdjustTool.Vignette))
        setFloatUniform("pop", adjust.unit(CoverAdjustTool.Pop))
        setFloatUniform("sharpen", adjust.unit(CoverAdjustTool.Sharpen))
        setFloatUniform("denoise", adjust.unit(CoverAdjustTool.Denoise))
    }
}

/**
 * Live preview of [preset]'s shader effect. Put it AFTER any transform graphicsLayer in the chain
 * (so it runs on the untransformed image, like the export), on the same element as the Image whose
 * colorFilter is [CoverFilterPreset.activeMatrix]. No-op below Android 13 or when there is no effect.
 */
fun Modifier.coverFilterEffect(preset: CoverFilterPreset?): Modifier {
    val p = preset ?: return this
    if (p.activeEffect == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return this.graphicsLayer {
        val effect = p.activeEffect
        renderEffect = if (
            effect != null && size.width >= 1f && size.height >= 1f &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            CoverFilterShader.previewEffect(p, effect, size.width, size.height)
        } else null
    }
}

/**
 * Live preview of a whole [CoverLook] (preset at its strength, then the Adjust tools' GPU stage).
 * Same placement rules as the preset overload; the Image's colorFilter must be [CoverLook.matrix].
 */
fun Modifier.coverFilterEffect(look: CoverLook?): Modifier {
    val l = look ?: return this
    if (!l.needsGpu || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return this.graphicsLayer {
        renderEffect = if (size.width >= 1f && size.height >= 1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            CoverFilterShader.previewEffect(l, size.width, size.height)
        } else null
    }
}

/** Export side: bakes matrix + effect on the GPU so the saved cover matches the preview. */
object CoverFilterRenderer {

    private const val TAG = "CoverFilterRenderer"
    private const val MAX_SIDE = 4096

    /**
     * Returns [source] with [preset]'s grade and shader effect applied, as a new software bitmap,
     * or null when there is nothing GPU-only to do (no effect, Android 12 or lower, or a failure).
     * On null, draw [source] with `ColorMatrixColorFilter(preset.matrix)` as before.
     * The result may be smaller than [source] (capped at [MAX_SIDE]); scale from its own size.
     * Call off the main thread.
     */
    fun bake(source: Bitmap, preset: CoverFilterPreset?): Bitmap? = bake(source, CoverLook(preset))

    /** Same as the preset overload for a whole [CoverLook]; on null draw with [CoverLook.matrixOrNull]. */
    fun bake(source: Bitmap, look: CoverLook): Bitmap? {
        if (!look.needsGpu) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (source.width <= 0 || source.height <= 0) return null
        return try {
            bakeApi33(source, look)
        } catch (e: Exception) {
            Log.w(TAG, "GPU bake failed for ${look.key}, falling back to the colour matrix", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun bakeApi33(source: Bitmap, look: CoverLook): Bitmap {
        val scale = min(1f, MAX_SIDE / max(source.width, source.height).toFloat())
        val w = max(1, (source.width * scale).roundToInt())
        val h = max(1, (source.height * scale).roundToInt())
        val reader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            renderer.isOpaque = false
            val node = RenderNode("CoverFilterBake")
            node.setPosition(0, 0, w, h)
            node.setRenderEffect(CoverFilterShader.renderEffect(look, w.toFloat(), h.toFloat()))
            val canvas = node.beginRecording()
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(look.matrix))
            }
            canvas.drawBitmap(source, null, Rect(0, 0, w, h), paint)
            node.endRecording()
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

            val image = reader.acquireNextImage() ?: error("No frame rendered")
            val copy: Bitmap? = image.use { frame ->
                val buffer = frame.hardwareBuffer ?: error("Frame has no HardwareBuffer")
                buffer.use { hb ->
                    val hardware = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB))
                        ?: error("wrapHardwareBuffer failed")
                    hardware.copy(Bitmap.Config.ARGB_8888, false).also { hardware.recycle() }
                }
            }
            return copy ?: error("Hardware bitmap copy failed")
        } finally {
            renderer.destroy()
            reader.close()
        }
    }
}
