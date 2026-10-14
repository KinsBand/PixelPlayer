package com.theveloper.pixelplay.presentation.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/** What a drag / pinch on the Adjust screen's cover does. */
enum class CoverAdjustMode(val label: String) {
    /** Uniform zoom + pan (aspect ratio locked). */
    Transform("Transform"),
    /** Independent horizontal / vertical scaling: two-axis pinch or the edge / corner handles. */
    Stretch("Stretch"),
    /** Stack more images over the cover; tap a layer (or its chip) to edit it. */
    Layers("Layers")
}

/**
 * One image in the cover stack. Layer 1 ([isBase]) is the cover photo itself; the rest are
 * overlays drawn over it in list order.
 *
 * Immutable on purpose: the editor swaps in `copy()`s so every change is a snapshot write and
 * Compose recomposes exactly what changed (mutating `var`s inside a state list would not).
 *
 * Units:
 *  - [scaleX] / [scaleY] multiply the layer's natural size. The base layer's natural size
 *    covers the square (so 1 = "fill"); an overlay's natural size fits 60% of it.
 *  - [offset] is normalised to the viewport side (0.1 = 10% of the cover). For the base layer
 *    it's in the layer's own (unrotated) frame, matching the legacy crop pan.
 *  - [rotationDegrees] counts quarter turns upward (90, 180, 270, 360…) so the rotate animation
 *    always turns the same way. The base layer turns the whole cover around its centre (as the
 *    Adjust screen always has); overlays turn around their own centre.
 */
@Immutable
data class CoverArtLayer(
    val id: String = UUID.randomUUID().toString(),
    val imageBitmap: ImageBitmap,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val offset: Offset = Offset.Zero,
    val rotationDegrees: Int = 0,
    val isFlipped: Boolean = false,
    val filterId: String? = null,
    /** Preset strength, 0..100 (100 = the preset as designed). */
    val filterStrength: Int = 100,
    /** Adjust-tab tools (brightness, vignette, sharpen…), applied after the preset. */
    val adjust: CoverAdjustments = CoverAdjustments(),
    val isBase: Boolean = false
) {
    val quarterTurns: Int get() = Math.floorMod(rotationDegrees / 90, 4)
    /** 0, 90, 180 or 270. */
    val normalizedRotation: Int get() = quarterTurns * 90
    val isUniform: Boolean get() = abs(scaleX - scaleY) < 1e-4f
    val hasEdits: Boolean
        get() = scaleX != 1f || scaleY != 1f || offset != Offset.Zero ||
            normalizedRotation != 0 || isFlipped || filterId != null || !adjust.isDefault

    /** Strength or adjust tools the legacy save fields (filter id only) can't express. */
    val hasLookBeyondPreset: Boolean
        get() = (filterId != null && filterStrength < 100) || !adjust.isDefault

    /** The same layer with its transform and filter cleared. */
    fun reset(keepFilter: Boolean = false): CoverArtLayer = CoverArtLayer(
        id = id,
        imageBitmap = imageBitmap,
        isBase = isBase,
        filterId = if (keepFilter) filterId else null,
        filterStrength = if (keepFilter) filterStrength else 100,
        adjust = if (keepFilter) adjust else CoverAdjustments()
    )
}

/** Edge / corner handles shown around the focused layer in [CoverAdjustMode.Stretch]. */
enum class StretchHandle(val dirX: Int, val dirY: Int) {
    Left(-1, 0), Right(1, 0), Top(0, -1), Bottom(0, 1),
    TopLeft(-1, -1), TopRight(1, -1), BottomLeft(-1, 1), BottomRight(1, 1)
}

/**
 * Geometry shared by the live canvas, its gestures and the exporter, so what you see is
 * exactly what gets saved. Everything works in a square viewport of side `side` px; positions
 * are relative to the viewport centre unless noted.
 */
object CoverLayerGeometry {
    /** Overlays first appear fitted inside this fraction of the cover. */
    const val OVERLAY_FIT = 0.6f
    private const val OVERLAY_MIN_SCALE = 0.08f
    private const val OVERLAY_MAX_SCALE = 25f
    private const val BASE_MAX_SCALE = 60f
    /** Overlays may be dragged until only their centre's margin is left on the cover. */
    private const val OVERLAY_MAX_OFFSET = 0.6f

    /** Unscaled size: the base layer covers the square, overlays fit [OVERLAY_FIT] of it. */
    fun naturalSize(layer: CoverArtLayer, side: Float): Size {
        val w = layer.imageBitmap.width.toFloat().coerceAtLeast(1f)
        val h = layer.imageBitmap.height.toFloat().coerceAtLeast(1f)
        val ratio = w / h
        return if (layer.isBase) {
            if (ratio > 1f) Size(side * ratio, side) else Size(side, side / ratio)
        } else {
            val box = side * OVERLAY_FIT
            if (ratio > 1f) Size(box, box / ratio) else Size(box * ratio, box)
        }
    }

    fun drawnSize(layer: CoverArtLayer, side: Float): Size {
        val n = naturalSize(layer, side)
        return Size(n.width * layer.scaleX, n.height * layer.scaleY)
    }

    /** Quarter-turn rotation, clockwise on screen (y points down). */
    fun rotate(p: Offset, degrees: Int): Offset = when (Math.floorMod(degrees, 360)) {
        90 -> Offset(-p.y, p.x)
        180 -> Offset(-p.x, -p.y)
        270 -> Offset(p.y, -p.x)
        else -> p
    }

    fun inverseRotate(p: Offset, degrees: Int): Offset = rotate(p, 360 - Math.floorMod(degrees, 360))

    /** Where the layer's centre sits on screen, relative to the viewport centre. */
    fun screenCenter(layer: CoverArtLayer, side: Float): Offset {
        val local = layer.offset * side
        return if (layer.isBase) rotate(local, layer.normalizedRotation) else local
    }

    /** Screen-space half width / height (rotations are quarter turns, so it stays axis aligned). */
    fun screenHalfExtents(layer: CoverArtLayer, side: Float): Size {
        val d = drawnSize(layer, side)
        return if (layer.quarterTurns % 2 == 1) Size(d.height / 2f, d.width / 2f) else Size(d.width / 2f, d.height / 2f)
    }

    /** Screen bounds in viewport coordinates (top-left origin). */
    fun screenBounds(layer: CoverArtLayer, side: Float): Rect {
        val c = screenCenter(layer, side) + Offset(side / 2f, side / 2f)
        val h = screenHalfExtents(layer, side)
        return Rect(c.x - h.width, c.y - h.height, c.x + h.width, c.y + h.height)
    }

    /** Top-most layer under [point] (viewport coordinates), or null. */
    fun hitTest(layers: List<CoverArtLayer>, point: Offset, side: Float): CoverArtLayer? =
        layers.asReversed().firstOrNull { screenBounds(it, side).contains(point) }

    /** Where a stretch handle is drawn / grabbed: on the layer's edge, pulled inside the viewport. */
    fun handlePosition(layer: CoverArtLayer, handle: StretchHandle, side: Float, inset: Float): Offset {
        val b = screenBounds(layer, side)
        val x = when (handle.dirX) { -1 -> b.left; 1 -> b.right; else -> b.center.x }
        val y = when (handle.dirY) { -1 -> b.top; 1 -> b.bottom; else -> b.center.y }
        return Offset(x.coerceIn(inset, side - inset), y.coerceIn(inset, side - inset))
    }

    /**
     * Applies a gesture step expressed in screen space: [zoomX] / [zoomY] along the screen
     * axes and [pan] in px. Screen axes are mapped onto the layer's own axes (a quarter-turned
     * layer stretches the other way round), and the result is clamped.
     */
    fun applyScreenTransform(layer: CoverArtLayer, zoomX: Float, zoomY: Float, pan: Offset, side: Float): CoverArtLayer {
        if (side <= 0f) return layer
        val swap = layer.quarterTurns % 2 == 1
        val lx = if (swap) zoomY else zoomX
        val ly = if (swap) zoomX else zoomY
        val localPan = if (layer.isBase) inverseRotate(pan, layer.normalizedRotation) else pan
        val next = layer.copy(
            scaleX = (layer.scaleX * lx).takeIf { it.isFinite() } ?: layer.scaleX,
            scaleY = (layer.scaleY * ly).takeIf { it.isFinite() } ?: layer.scaleY,
            offset = layer.offset + localPan / side
        )
        return clamp(next, side)
    }

    /** Dragging a stretch handle: the opposite edge stays put while this one follows the finger. */
    fun applyHandleDrag(layer: CoverArtLayer, handle: StretchHandle, drag: Offset, side: Float): CoverArtLayer {
        val half = screenHalfExtents(layer, side)
        val minSide = side * 0.04f
        var zoomX = 1f
        var zoomY = 1f
        var shift = Offset.Zero
        if (handle.dirX != 0 && half.width > 0f) {
            val w = half.width * 2f
            val nw = (w + handle.dirX * drag.x).coerceAtLeast(minSide)
            zoomX = nw / w
            shift += Offset((nw - w) / 2f * handle.dirX, 0f)
        }
        if (handle.dirY != 0 && half.height > 0f) {
            val h = half.height * 2f
            val nh = (h + handle.dirY * drag.y).coerceAtLeast(minSide)
            zoomY = nh / h
            shift += Offset(0f, (nh - h) / 2f * handle.dirY)
        }
        return applyScreenTransform(layer, zoomX, zoomY, shift, side)
    }

    /**
     * Keeps a layer usable: the base never shrinks below "cover" on either axis and can't be
     * dragged to leave a gap; overlays keep a sane size and can't be lost off the edge.
     */
    fun clamp(layer: CoverArtLayer, side: Float): CoverArtLayer {
        if (side <= 0f) return layer
        return if (layer.isBase) {
            val sx = layer.scaleX.coerceIn(1f, BASE_MAX_SCALE)
            val sy = layer.scaleY.coerceIn(1f, BASE_MAX_SCALE)
            val n = naturalSize(layer, side)
            val maxX = max(0f, (n.width * sx - side) / 2f) / side
            val maxY = max(0f, (n.height * sy - side) / 2f) / side
            layer.copy(
                scaleX = sx,
                scaleY = sy,
                offset = Offset(layer.offset.x.coerceIn(-maxX, maxX), layer.offset.y.coerceIn(-maxY, maxY))
            )
        } else {
            layer.copy(
                scaleX = layer.scaleX.coerceIn(OVERLAY_MIN_SCALE, OVERLAY_MAX_SCALE),
                scaleY = layer.scaleY.coerceIn(OVERLAY_MIN_SCALE, OVERLAY_MAX_SCALE),
                offset = Offset(
                    layer.offset.x.coerceIn(-OVERLAY_MAX_OFFSET, OVERLAY_MAX_OFFSET),
                    layer.offset.y.coerceIn(-OVERLAY_MAX_OFFSET, OVERLAY_MAX_OFFSET)
                )
            )
        }
    }
}

/**
 * State for the cover Adjust screen: the layer stack, which layer is focused and the mode.
 * All edits go through here so the screen, the form preview and Save read one source of truth.
 */
@Stable
class CoverEditorState {
    /** Layer 1: the cover photo. Null until it has loaded. */
    var base: CoverArtLayer? by mutableStateOf(null)
        private set

    /** Layers 2…n, bottom to top. */
    val overlays = mutableStateListOf<CoverArtLayer>()

    var focusedLayerId: String? by mutableStateOf(null)
        private set

    var mode: CoverAdjustMode by mutableStateOf(CoverAdjustMode.Transform)

    /** Base first, then overlays bottom → top (draw order). */
    val layers: List<CoverArtLayer>
        get() = buildList {
            base?.let { add(it) }
            addAll(overlays)
        }

    val focusedLayer: CoverArtLayer?
        get() = overlays.firstOrNull { it.id == focusedLayerId } ?: base

    /** Something differs from the untouched photo (drives the Reset button). */
    val isAdjusted: Boolean get() = overlays.isNotEmpty() || base?.hasEdits == true

    /**
     * The legacy crop pipeline (uniform scale + pan + flip/rotate/filter on one photo) can't
     * express this cover, so Save renders the stack to an image first.
     */
    val needsComposite: Boolean
        get() = overlays.isNotEmpty() || base?.isUniform == false || base?.hasLookBeyondPreset == true

    // ── Base layer, in the legacy save pipeline's terms ──
    val cropScale: Float get() = base?.scaleX ?: 1f
    val cropOffset: Offset get() = base?.offset ?: Offset.Zero
    val isBaseFlipped: Boolean get() = base?.isFlipped ?: false
    val baseRotationDegrees: Int get() = base?.normalizedRotation ?: 0
    val baseFilterId: String? get() = base?.filterId

    /** Changes whenever anything that Save writes changes (used for "unsaved changes"). */
    val signature: String
        get() = layers.joinToString("|") {
            "${it.id}:${it.scaleX}:${it.scaleY}:${it.offset.x}:${it.offset.y}:${it.normalizedRotation}:${it.isFlipped}:${it.look.key}"
        }

    /** The cover photo loaded (or reloaded): keeps the current transform, creates Layer 1 if needed. */
    fun setBaseImage(bitmap: ImageBitmap) {
        val current = base
        base = current?.copy(imageBitmap = bitmap) ?: CoverArtLayer(imageBitmap = bitmap, isBase = true)
        if (focusedLayer == null || focusedLayerId == null) focusedLayerId = base?.id
    }

    /** A new photo replaces Layer 1: upright and unmirrored; the filter is kept, overlays stay. */
    fun resetBaseForNewImage() {
        base = base?.reset(keepFilter = true)
    }

    /** The photo was removed: nothing left to layer over. */
    fun clear() {
        base = null
        overlays.clear()
        focusedLayerId = null
        mode = CoverAdjustMode.Transform
    }

    /** Reset button: back to the untouched photo (overlays removed). */
    fun resetAll() {
        base = base?.reset()
        overlays.clear()
        focusedLayerId = base?.id
    }

    fun focus(id: String) {
        if (base?.id == id || overlays.any { it.id == id }) focusedLayerId = id
    }

    fun update(layer: CoverArtLayer) {
        if (base?.id == layer.id) {
            base = layer
            return
        }
        val index = overlays.indexOfFirst { it.id == layer.id }
        if (index >= 0) overlays[index] = layer
    }

    fun updateFocused(transform: (CoverArtLayer) -> CoverArtLayer) {
        focusedLayer?.let { update(transform(it)) }
    }

    fun rotateFocused() = updateFocused { it.copy(rotationDegrees = it.rotationDegrees + 90) }

    fun toggleFlipFocused() = updateFocused { it.copy(isFlipped = !it.isFlipped) }

    /** Picks a preset (at full strength) or clears it (null). */
    fun setFocusedFilter(filterId: String?) = updateFocused { it.copy(filterId = filterId, filterStrength = 100) }

    /**
     * Preset strength 0..100. 0 previews the photo without the preset while the slider is held;
     * the panel clears the preset (setFocusedFilter(null)) once the finger lifts at 0.
     */
    fun setFocusedStrength(strength: Int) = updateFocused {
        if (it.filterId == null) it else it.copy(filterStrength = strength.coerceIn(0, 100))
    }

    fun setFocusedAdjust(tool: CoverAdjustTool, value: Int) =
        updateFocused { it.copy(adjust = it.adjust.with(tool, value)) }

    /** Adds an image on top of the stack and focuses it. */
    fun addOverlay(bitmap: ImageBitmap) {
        val layer = CoverArtLayer(imageBitmap = bitmap)
        overlays.add(layer)
        focusedLayerId = layer.id
    }

    fun removeOverlay(id: String) {
        val index = overlays.indexOfFirst { it.id == id }
        if (index < 0) return
        overlays.removeAt(index)
        if (focusedLayerId == id) {
            focusedLayerId = overlays.getOrNull(index - 1)?.id ?: overlays.firstOrNull()?.id ?: base?.id
        }
    }

    /** Re-stacks overlays (z-order). Indices are into [overlays]. */
    fun moveOverlay(from: Int, to: Int) {
        if (from == to || from !in overlays.indices || to !in overlays.indices) return
        overlays.add(to, overlays.removeAt(from))
    }
}

@Composable
fun rememberCoverEditorState(): CoverEditorState = remember { CoverEditorState() }

/** Renders a layer stack to a square bitmap with the same maths as the live canvas. */
object CoverLayerCompositor {

    fun render(layers: List<CoverArtLayer>, size: Int = 1024): android.graphics.Bitmap {
        val side = size.toFloat()
        val target = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(target)
        canvas.drawColor(android.graphics.Color.BLACK)
        layers.forEach { layer ->
            val source = layer.imageBitmap.asAndroidBitmap()
            if (source.width <= 0 || source.height <= 0) return@forEach
            val drawn = CoverLayerGeometry.drawnSize(layer, side)
            val panX = layer.offset.x * side
            val panY = layer.offset.y * side
            val cx = side / 2f + panX
            val cy = side / 2f + panY
            // Shader presets are baked on the GPU first (grade + effect); otherwise the matrix
            // is applied by the paint below, as before.
            val look = layer.look
            val baked = CoverFilterRenderer.bake(source, look)
            val drawSource = baked ?: source
            val matrix = android.graphics.Matrix().apply {
                postScale(drawn.width / drawSource.width, drawn.height / drawSource.height)
                postTranslate((side - drawn.width) / 2f + panX, (side - drawn.height) / 2f + panY)
                // Mirror around the layer's own centre (the canvas's negative scaleX).
                if (layer.isFlipped) postScale(-1f, 1f, cx, cy)
                val degrees = layer.normalizedRotation.toFloat()
                if (degrees != 0f) {
                    // Base: the whole cover turns around its centre. Overlays: around their own.
                    if (layer.isBase) postRotate(degrees, side / 2f, side / 2f) else postRotate(degrees, cx, cy)
                }
            }
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
            if (baked == null) look.matrixOrNull?.let {
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(it))
            }
            canvas.drawBitmap(drawSource, matrix, paint)
            baked?.recycle()
        }
        return target
    }

    /**
     * Renders to a JPEG in the cache and returns it. The playlist save pipeline then treats it
     * like any picked photo (scale 1, no pan, no extra edits), so it's copied in unchanged.
     */
    suspend fun renderToFile(context: Context, layers: List<CoverArtLayer>, size: Int = 1024): File =
        withContext(Dispatchers.Default) {
            val bitmap = render(layers, size)
            try {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "cover_composites").apply { mkdirs() }
                    // Old composites are only needed until their save has copied them.
                    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000L }
                        ?.forEach { it.delete() }
                    val file = File(dir, "cover_composite_${UUID.randomUUID()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    file
                }
            } finally {
                bitmap.recycle()
            }
        }
}
