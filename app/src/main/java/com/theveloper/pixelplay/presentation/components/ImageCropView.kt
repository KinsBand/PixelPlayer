package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.drag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.max

/** A quick two-finger tap (both fingers down and up without moving) toggles the flip. */
private const val TWO_FINGER_TAP_TIMEOUT_MS = 300L

@Composable
fun ImageCropView(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    pan: Offset = Offset.Zero, // Normalized (relative to Viewport Width/Height)
    enabled: Boolean = true,
    /** Mirrors the image horizontally (around its own centre, before the pan is applied). */
    isFlipped: Boolean = false,
    /** Non-null enables the two-finger-tap flip gesture. */
    onFlipToggle: (() -> Unit)? = null,
    /** Live preview of a colour-grading preset (see [CoverFilters]). */
    colorFilter: ColorFilter? = null,
    onCrop: (Float, Offset) -> Unit // scale, normalizedPan
) {
    val density = LocalDensity.current
    
    // Use updated state to avoid stale closure in pointerInput
    val currentScaleState by rememberUpdatedState(scale)
    val currentPanState by rememberUpdatedState(pan)
    val currentOnCropState by rememberUpdatedState(onCrop)
    val currentOnFlipToggle by rememberUpdatedState(onFlipToggle)

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f) // Enforce square container
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        
        // Image Dimensions
        val bitmapW = imageBitmap.width.toFloat()
        val bitmapH = imageBitmap.height.toFloat()
        val bitmapRatio = if(bitmapH > 0) bitmapW / bitmapH else 1f
        val viewportRatio = if (viewportHeight > 0) viewportWidth / viewportHeight else 1f
        
        // Calculate Base Dimensions (Zoom = 1.0f)
        // The image should COVER the viewport completely minimalistically.
        val (baseW, baseH) = if (bitmapRatio > viewportRatio) {
            // Image is wider than viewport: Height constrains
            val h = viewportHeight
            val w = h * bitmapRatio
            w to h
        } else {
            // Image is taller than viewport: Width constrains
            val w = viewportWidth
            val h = w / bitmapRatio
            w to h
        }
        
        // Current Pan in Pixels
        // pan input is normalized: panX * viewportWidth
        val currentPanX = pan.x * viewportWidth
        val currentPanY = pan.y * viewportHeight

        fun applyTransform(zoom: Float, panDelta: Offset) {
            // 1. Scale: no upper ceiling, high magnification is allowed; never below "cover".
            val proposed = currentScaleState * zoom
            val newScale = if (proposed.isFinite()) proposed.coerceAtLeast(1f) else currentScaleState

            // 2. Pan limits for the new scale (keep the viewport covered).
            val newW = baseW * newScale
            val newH = baseH * newScale
            val newMaxPanX = max(0f, (newW - viewportWidth) / 2f)
            val newMaxPanY = max(0f, (newH - viewportHeight) / 2f)

            val panPxX = currentPanState.x * viewportWidth
            val panPxY = currentPanState.y * viewportHeight
            val newPanPxX = (panPxX + panDelta.x).coerceIn(-newMaxPanX, newMaxPanX)
            val newPanPxY = (panPxY + panDelta.y).coerceIn(-newMaxPanY, newMaxPanY)

            val normX = if (viewportWidth > 0) newPanPxX / viewportWidth else 0f
            val normY = if (viewportHeight > 0) newPanPxY / viewportHeight else 0f
            currentOnCropState(newScale, Offset(normX, normY))
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enabled) {
                        Modifier.pointerInput(baseW, baseH, viewportWidth, viewportHeight) {
                            // Pinch-zoom + drag (like detectTransformGestures) plus a two-finger
                            // tap that flips the image horizontally.
                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                val touchSlop = viewConfiguration.touchSlop
                                var zoomAcc = 1f
                                var panAcc = Offset.Zero
                                var pastTouchSlop = false
                                var maxPointers = 1
                                var lastUptime = firstDown.uptimeMillis
                                do {
                                    val event = awaitPointerEvent()
                                    maxPointers = max(maxPointers, event.changes.count { it.pressed })
                                    event.changes.forEach { lastUptime = max(lastUptime, it.uptimeMillis) }
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (!canceled) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (!pastTouchSlop) {
                                            zoomAcc *= zoomChange
                                            panAcc += panChange
                                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                            val zoomMotion = abs(1f - zoomAcc) * centroidSize
                                            if (zoomMotion > touchSlop || panAcc.getDistance() > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }
                                        if (pastTouchSlop) {
                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                applyTransform(zoomChange, panChange)
                                            }
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                } while (!canceled && event.changes.any { it.pressed })

                                val isTwoFingerTap = maxPointers == 2 && !pastTouchSlop &&
                                    lastUptime - firstDown.uptimeMillis <= TWO_FINGER_TAP_TIMEOUT_MS
                                if (isTwoFingerTap) currentOnFlipToggle?.invoke()
                            }
                        }
                    } else Modifier
                )
        ) {
            // Render Image at Base Size, centered, then transform.
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .requiredSize(
                        width = with(density) { baseW.toDp() },
                        height = with(density) { baseH.toDp() }
                    )
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = if (isFlipped) -scale else scale
                        scaleY = scale
                        translationX = currentPanX
                        translationY = currentPanY
                    }
            )
        }
    }
}

// ── Layered cover canvas ─────────────────────────────────────────────────────────────────────

/** Per-event cap on a two-axis pinch step, so near-collinear fingers can't explode one axis. */
private const val MAX_AXIS_ZOOM_STEP = 1.25f

/**
 * The Adjust screen's cover: draws every [CoverArtLayer] (base first) in a square viewport and
 * routes gestures to the focused one.
 *
 *  - Drag pans, pinch zooms the focused layer. In [CoverAdjustMode.Stretch] the pinch scales
 *    each axis by how far the fingers spread along it, and edge / corner handles stretch from
 *    the opposite edge.
 *  - A tap focuses the top-most layer under the finger (an animated ring marks it).
 *  - A quick two-finger tap flips the focused layer.
 *
 * With [enabled] false it's a plain preview (the form's cover thumbnail).
 */
@Composable
fun CoverLayersCanvas(
    layers: List<CoverArtLayer>,
    modifier: Modifier = Modifier,
    focusedLayerId: String? = null,
    mode: CoverAdjustMode = CoverAdjustMode.Transform,
    enabled: Boolean = true,
    onFocusLayer: (String) -> Unit = {},
    onLayerChange: (CoverArtLayer) -> Unit = {},
    onFlipToggle: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val currentLayers by rememberUpdatedState(layers)
    val currentFocusedId by rememberUpdatedState(focusedLayerId)
    val currentMode by rememberUpdatedState(mode)
    val currentOnFocus by rememberUpdatedState(onFocusLayer)
    val currentOnChange by rememberUpdatedState(onLayerChange)
    val currentOnFlip by rememberUpdatedState(onFlipToggle)
    val handleInsetPx = with(density) { 14.dp.toPx() }
    val handleGrabRadiusPx = with(density) { 28.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val side = constraints.maxWidth.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enabled) {
                        Modifier.pointerInput(side) {
                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                val stack = currentLayers
                                val focused = stack.firstOrNull { it.id == currentFocusedId } ?: stack.firstOrNull()
                                    ?: return@awaitEachGesture
                                // Edits accumulate on a working copy for the whole gesture, so
                                // several pointer events within one frame never read a stale layer.
                                var working = focused

                                // Stretch handles take priority over the pinch / pan below.
                                if (currentMode == CoverAdjustMode.Stretch) {
                                    val handle = StretchHandle.entries.minByOrNull { h ->
                                        (CoverLayerGeometry.handlePosition(working, h, side, handleInsetPx) - firstDown.position).getDistance()
                                    }?.takeIf { h ->
                                        (CoverLayerGeometry.handlePosition(working, h, side, handleInsetPx) - firstDown.position)
                                            .getDistance() <= handleGrabRadiusPx
                                    }
                                    if (handle != null) {
                                        firstDown.consume()
                                        drag(firstDown.id) { change ->
                                            val delta = change.positionChange()
                                            if (delta != Offset.Zero) {
                                                working = CoverLayerGeometry.applyHandleDrag(working, handle, delta, side)
                                                currentOnChange(working)
                                            }
                                            change.consume()
                                        }
                                        return@awaitEachGesture
                                    }
                                }

                                val touchSlop = viewConfiguration.touchSlop
                                var zoomAcc = 1f
                                var panAcc = Offset.Zero
                                var pastTouchSlop = false
                                var maxPointers = 1
                                var lastUptime = firstDown.uptimeMillis
                                do {
                                    val event = awaitPointerEvent()
                                    maxPointers = max(maxPointers, event.changes.count { it.pressed })
                                    event.changes.forEach { lastUptime = max(lastUptime, it.uptimeMillis) }
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (!canceled) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (!pastTouchSlop) {
                                            zoomAcc *= zoomChange
                                            panAcc += panChange
                                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                            val zoomMotion = abs(1f - zoomAcc) * centroidSize
                                            if (zoomMotion > touchSlop || panAcc.getDistance() > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }
                                        if (pastTouchSlop) {
                                            val (zoomX, zoomY) = if (currentMode == CoverAdjustMode.Stretch) {
                                                event.axisZoom()
                                            } else {
                                                zoomChange to zoomChange
                                            }
                                            if (zoomX != 1f || zoomY != 1f || panChange != Offset.Zero) {
                                                working = CoverLayerGeometry.applyScreenTransform(working, zoomX, zoomY, panChange, side)
                                                currentOnChange(working)
                                            }
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                } while (!canceled && event.changes.any { it.pressed })

                                val quick = lastUptime - firstDown.uptimeMillis <= TWO_FINGER_TAP_TIMEOUT_MS
                                when {
                                    maxPointers == 2 && !pastTouchSlop && quick -> currentOnFlip?.invoke()
                                    maxPointers == 1 && !pastTouchSlop -> {
                                        CoverLayerGeometry.hitTest(currentLayers, firstDown.position, side)
                                            ?.takeIf { it.id != currentFocusedId }
                                            ?.let { currentOnFocus(it.id) }
                                    }
                                }
                            }
                        }
                    } else Modifier
                )
        ) {
            layers.forEach { layer ->
                key(layer.id) { CoverLayerImage(layer = layer, side = side) }
            }

            // Ring + handles: only once there's something to tell apart (or handles to grab).
            val focused = layers.firstOrNull { it.id == focusedLayerId } ?: layers.firstOrNull()
            if (enabled && focused != null && (layers.size > 1 || mode == CoverAdjustMode.Stretch)) {
                CoverLayerSelection(
                    layer = focused,
                    side = side,
                    showHandles = mode == CoverAdjustMode.Stretch,
                    handleInset = handleInsetPx
                )
            }
        }
    }
}

/** Two-axis pinch: how much the fingers spread along screen X and Y since the last event. */
private fun PointerEvent.axisZoom(): Pair<Float, Float> {
    val active = changes.filter { it.pressed && it.previousPressed }
    if (active.size < 2) return 1f to 1f
    fun spread(points: List<Offset>): Offset {
        val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
        return Offset(
            points.sumOf { abs(it.x - cx).toDouble() }.toFloat() / points.size,
            points.sumOf { abs(it.y - cy).toDouble() }.toFloat() / points.size
        )
    }
    val before = spread(active.map { it.previousPosition })
    val now = spread(active.map { it.position })
    // Fingers nearly lined up on an axis carry no information about that axis.
    val minSpread = 12f
    val zx = if (before.x > minSpread && now.x > 0f) now.x / before.x else 1f
    val zy = if (before.y > minSpread && now.y > 0f) now.y / before.y else 1f
    val lo = 1f / MAX_AXIS_ZOOM_STEP
    return zx.coerceIn(lo, MAX_AXIS_ZOOM_STEP) to zy.coerceIn(lo, MAX_AXIS_ZOOM_STEP)
}

@Composable
private fun CoverLayerImage(layer: CoverArtLayer, side: Float) {
    val density = LocalDensity.current
    val rotation by animateFloatAsState(
        targetValue = layer.rotationDegrees.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "cover_layer_rotation"
    )
    val look = remember(layer.filterId, layer.filterStrength, layer.adjust) { layer.look }
    val colorFilter = remember(look) {
        look.matrixOrNull?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
    }
    val natural = CoverLayerGeometry.naturalSize(layer, side)
    val image = @Composable {
        Image(
            bitmap = layer.imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
            modifier = Modifier
                .requiredSize(
                    width = with(density) { natural.width.toDp() },
                    height = with(density) { natural.height.toDp() }
                )
                .graphicsLayer {
                    // Scale / flip / own rotation happen around the layer centre, then the pan.
                    scaleX = if (layer.isFlipped) -layer.scaleX else layer.scaleX
                    scaleY = layer.scaleY
                    if (!layer.isBase) rotationZ = rotation
                    translationX = layer.offset.x * side
                    translationY = layer.offset.y * side
                }
                // Inner layer: the effect sees the untransformed image, like the export.
                .coverFilterEffect(look)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // The base layer turns the whole cover around its centre.
            .then(if (layer.isBase) Modifier.graphicsLayer { rotationZ = rotation } else Modifier),
        contentAlignment = Alignment.Center
    ) { image() }
}

/** Animated focus ring around the focused layer, plus stretch handles in Stretch mode. */
@Composable
private fun CoverLayerSelection(
    layer: CoverArtLayer,
    side: Float,
    showHandles: Boolean,
    handleInset: Float
) {
    val ringColor = MaterialTheme.colorScheme.primary
    val handleFill = MaterialTheme.colorScheme.primaryContainer
    // Snaps in from slightly outside each time the focus moves to another layer.
    val focusPulse = remember { Animatable(0f) }
    LaunchedEffect(layer.id) {
        focusPulse.snapTo(0f)
        focusPulse.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    val handlesAlpha by animateFloatAsState(
        targetValue = if (showHandles) 1f else 0f,
        label = "stretch_handles_alpha"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val pulse = focusPulse.value
        val inflate = (1f - pulse) * 14.dp.toPx()
        val inset = 3.dp.toPx()
        val raw = CoverLayerGeometry.screenBounds(layer, side)
        // Keep the ring on screen even when the layer overflows the cover (the base usually does).
        val bounds = Rect(
            left = (raw.left - inflate).coerceAtLeast(inset),
            top = (raw.top - inflate).coerceAtLeast(inset),
            right = (raw.right + inflate).coerceAtMost(size.width - inset),
            bottom = (raw.bottom + inflate).coerceAtMost(size.height - inset)
        )
        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas
        val alpha = pulse.coerceIn(0f, 1f)
        val corner = CornerRadius(14.dp.toPx())
        // Soft dark halo under the ring so it reads on light artwork too.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.28f * alpha),
            topLeft = bounds.topLeft,
            size = bounds.size,
            cornerRadius = corner,
            style = Stroke(width = 5.dp.toPx())
        )
        drawRoundRect(
            color = ringColor.copy(alpha = alpha),
            topLeft = bounds.topLeft,
            size = bounds.size,
            cornerRadius = corner,
            style = Stroke(width = lerp(5.dp.toPx(), 2.5.dp.toPx(), pulse.coerceIn(0f, 1f)))
        )
        if (handlesAlpha > 0f) {
            StretchHandle.entries.forEach { handle ->
                val center = CoverLayerGeometry.handlePosition(layer, handle, side, handleInset)
                val isCorner = handle.dirX != 0 && handle.dirY != 0
                if (isCorner) {
                    drawCircle(Color.Black.copy(alpha = 0.3f * handlesAlpha), radius = 9.dp.toPx(), center = center)
                    drawCircle(handleFill.copy(alpha = handlesAlpha), radius = 7.dp.toPx(), center = center)
                    drawCircle(ringColor.copy(alpha = handlesAlpha), radius = 7.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                } else {
                    // Edge handles are pills along their edge.
                    val long = 22.dp.toPx()
                    val short = 8.dp.toPx()
                    val pill = if (handle.dirX != 0) Size(short, long) else Size(long, short)
                    val topLeft = Offset(center.x - pill.width / 2f, center.y - pill.height / 2f)
                    val radius = CornerRadius(short / 2f)
                    drawRoundRect(handleFill.copy(alpha = handlesAlpha), topLeft, pill, radius)
                    drawRoundRect(ringColor.copy(alpha = handlesAlpha), topLeft, pill, radius, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
