package com.theveloper.pixelplay.ui.overlay

import android.graphics.Rect
import android.os.Build
import android.view.DisplayCutout
import android.view.Surface
import android.view.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dimensions and geometry for the Camera Cutout Overlay Widget.
 *
 * Specific Pixel 10 Pro Reference Measures:
 * - Display: 6.3" LTPO OLED, 20:9 aspect ratio
 * - Full Native Resolution: 1280 x 2856 px (~495 ppi)
 * - Default High Resolution: 960 x 2142 px (~371 ppi), Display scaled at 1080x2410 (420 dpi)
 * - Status Bar Height / Cutout Bottom: 172 px (~65.5 dp @ 420 dpi / ~56 dp @ 495 dpi)
 * - Camera Cutout Center: X = 540 px (centerX), Y = 86 px (centerY @ 420 dpi, ~32.8 dp)
 * - Camera Cutout Physical Diameter: ~3.50 mm (Circumference: ~11.00 mm)
 * - Visual Button Diameter: ~34 dp
 */
object OverlayCutoutDimensions {
    // Pixel 10 Pro default measurements
    val DEFAULT_SCREEN_WIDTH: Dp = 412.dp
    val DEFAULT_SCREEN_HEIGHT: Dp = 919.dp
    val DEFAULT_CUTOUT_DIAMETER: Dp = 34.dp
    val DEFAULT_TOUCH_WIDTH: Dp = 90.dp
    val DEFAULT_CUTOUT_CENTER_Y: Dp = 33.dp
    val DEFAULT_STATUS_BAR_HEIGHT: Dp = 66.dp

    // Drag Handle Dimensions
    val HANDLE_WIDTH: Dp = 38.dp
    val HANDLE_HEIGHT: Dp = 4.dp
    val HANDLE_TOUCH_AREA_HEIGHT: Dp = 26.dp

    /**
     * Resolves the camera cutout geometry dynamically from Android DisplayCutout,
     * falling back to the Pixel 10 Pro hardware geometry if the cutout API is unavailable.
     */
    /**
     * Geometry from cutout rects already mapped into the natural (portrait) frame — see
     * [IslandWindowPlacement.toNatural]. Null when no rect sits along the natural top edge.
     */
    fun resolveNaturalCutoutGeometry(naturalRects: List<Rect>, density: Density): CutoutGeometry? {
        val rect = naturalRects.filter { !it.isEmpty && it.top < 150 }.minByOrNull { it.top } ?: return null
        return with(density) {
            CutoutGeometry(
                centerXDp = rect.exactCenterX().toDp(),
                centerYDp = rect.exactCenterY().toDp(),
                topDp = rect.top.toDp(),
                widthDp = rect.width().toDp().coerceAtLeast(DEFAULT_CUTOUT_DIAMETER),
                heightDp = rect.height().toDp().coerceAtLeast(DEFAULT_CUTOUT_DIAMETER),
                bottomDp = rect.bottom.toDp(),
                isRealHardwareCutout = true
            )
        }
    }

    /** No cutout reported: assume a centred punch-hole on the natural top edge. */
    fun fallbackGeometry(density: Density, naturalWidthPx: Int): CutoutGeometry = with(density) {
        CutoutGeometry(
            centerXDp = (naturalWidthPx / 2f).toDp(),
            centerYDp = DEFAULT_CUTOUT_CENTER_Y,
            topDp = 0.dp,
            widthDp = DEFAULT_CUTOUT_DIAMETER,
            heightDp = DEFAULT_STATUS_BAR_HEIGHT,
            bottomDp = DEFAULT_STATUS_BAR_HEIGHT,
            isRealHardwareCutout = false
        )
    }

    fun resolveCutoutGeometry(
        displayCutout: DisplayCutout?,
        density: Density,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): CutoutGeometry {
        val cutoutRect: Rect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && displayCutout != null) {
            val rects = displayCutout.boundingRects
            rects.firstOrNull { it.top == 0 || it.top < 150 }
                ?: displayCutout.boundingRectTop
        } else {
            null
        }

        return with(density) {
            if (cutoutRect != null && !cutoutRect.isEmpty) {
                val centerXDp = cutoutRect.centerX().toDp()
                val centerYDp = cutoutRect.centerY().toDp()
                val widthDp = cutoutRect.width().toDp().coerceAtLeast(DEFAULT_CUTOUT_DIAMETER)
                val heightDp = cutoutRect.height().toDp().coerceAtLeast(DEFAULT_CUTOUT_DIAMETER)
                val topDp = cutoutRect.top.toDp()
                val bottomDp = cutoutRect.bottom.toDp()

                CutoutGeometry(
                    centerXDp = centerXDp,
                    centerYDp = centerYDp,
                    topDp = topDp,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    bottomDp = bottomDp,
                    isRealHardwareCutout = true
                )
            } else {
                val centerXDp = (screenWidthPx / 2).toDp()
                CutoutGeometry(
                    centerXDp = centerXDp,
                    centerYDp = DEFAULT_CUTOUT_CENTER_Y,
                    topDp = 0.dp,
                    widthDp = DEFAULT_CUTOUT_DIAMETER,
                    heightDp = DEFAULT_STATUS_BAR_HEIGHT,
                    bottomDp = DEFAULT_STATUS_BAR_HEIGHT,
                    isRealHardwareCutout = false
                )
            }
        }
    }

}

/**
 * Every size the island uses, derived from the real cutout so the header row is centred on
 * the camera and the pill sits exactly on the hole. Heights are for the island surface
 * (not the window); the window is sized from these plus [SHADOW_MARGIN].
 */
@androidx.compose.runtime.Immutable
data class IslandMetrics(
    val geometry: CutoutGeometry,
    /** The display in its natural (portrait) orientation. Never changes with rotation. */
    val display: IslandDisplay,
    val density: Float,
    /**
     * Extra row under the header while the island can't draw over the status bar (no
     * accessibility window): explains how to make it cover the time / battery icons.
     */
    val statusHintHeight: Dp = 0.dp
) {
    val screenWidth: Dp = (display.naturalWidthPx / density).dp
    val screenHeight: Dp = (display.naturalHeightPx / density).dp

    /** Camera centre, in natural-frame pixels. */
    val cameraCenterXPx: Float = geometry.centerXDp.value * density

    /**
     * Left edge of the window currently hosting the island, in natural-frame pixels, worked
     * out from the window's width in the same frame as the resize. The collapsed strip is
     * centred on the camera; every other window starts at 0. Using the same formula as the
     * service ([IslandWindowPlacement]) means the pill is drawn on the camera in both windows,
     * so swapping windows can never move it.
     */
    @Suppress("UNUSED_PARAMETER")
    fun windowLeftPx(windowWidthPx: Int): Int = 0 // The drawing window always starts at x = 0.

    /**
     * Open, the surface starts at the very top of the screen and spans its full width, so it
     * covers the status bar (time, notifications, signal, battery) edge to edge.
     */
    val surfaceTop: Dp = 0.dp
    val sideMargin: Dp = 0.dp

    /**
     * The screen's own corner radius. The open surface's top corners use it so the island
     * follows the display's curve exactly. Falls back to a typical radius when the system
     * doesn't report one (before Android 12).
     */
    val screenCornerRadius: Dp =
        if (display.cornerRadiusPx > 0) (display.cornerRadiusPx / density).dp
        else (screenWidth * 0.075f).coerceIn(20.dp, 48.dp)

    /** Bottom corners of the open surface (it floats over the app there). */
    val bottomCornerRadius: Dp = 30.dp

    /**
     * The black pill: only just bigger than the camera lens. The reported cutout rect is
     * padded well beyond the visible hole, so the lens is estimated from its short side and
     * capped; the coloured wave ring is drawn outside this, not as part of the black.
     */
    val pillSize: Dp = minOf(geometry.widthDp, geometry.heightDp).coerceIn(18.dp, 24.dp) + 2.dp

    /** Room the wave ring may use outside the pill (gap + stroke + peak amplitude). */
    val ringReach: Dp = 7.dp
    val pillTop: Dp = (geometry.centerYDp - pillSize / 2).coerceAtLeast(0.dp)

    /** Header row, vertically centred on the camera. */
    val headerHeight: Dp = ((geometry.centerYDp - surfaceTop) * 2).coerceAtLeast(pillSize + 8.dp)
    val handleHeight: Dp = 22.dp

    val expandedWidth: Dp = screenWidth - sideMargin * 2

    fun contentHeight(level: CutoutExpansionLevel): Dp = when (level) {
        CutoutExpansionLevel.COLLAPSED -> 0.dp
        // + handleHeight = the bottom pill outline, so the line sits exactly in its middle.
        CutoutExpansionLevel.LEVEL_1_SINGLE -> 42.dp
        CutoutExpansionLevel.LEVEL_2_THREE -> 104.dp
        CutoutExpansionLevel.LEVEL_3_SIX -> 176.dp + CONTROLS_HEIGHT
        CutoutExpansionLevel.LEVEL_4_FULL ->
            fullHeight - headerHeight - statusHintHeight - handleHeight
    }

    /** Surface height at rest for [level]. Collapsed is the pill. */
    fun height(level: CutoutExpansionLevel): Dp = when (level) {
        CutoutExpansionLevel.COLLAPSED -> pillSize
        CutoutExpansionLevel.LEVEL_4_FULL -> fullHeight
        else -> headerHeight + statusHintHeight + contentHeight(level) + handleHeight
    }

    private val fullHeight: Dp get() = screenHeight - surfaceTop - 16.dp

    /**
     * The pill outline along the bottom edge of the open island: tall enough to hold the
     * controls row (states 3 and 4) and the single lyric line (state 1).
     */
    val outlineHeight: Dp get() = maxOf(bottomCornerRadius * 2, CONTROLS_HEIGHT)

    /**
     * How visible the bottom pill outline is at [height]: shown in state 1 (around the lyric)
     * and states 3–4 (around the controls), hidden in state 2. Follows a drag smoothly.
     */
    fun outlineAlpha(height: Dp): Float {
        val h1 = height(CutoutExpansionLevel.LEVEL_1_SINGLE).value
        val h2 = height(CutoutExpansionLevel.LEVEL_2_THREE).value
        val h3 = height(CutoutExpansionLevel.LEVEL_3_SIX).value
        val h = height.value
        return when {
            h <= h1 -> morphFraction(height)
            h <= h2 -> 1f - (h - h1) / (h2 - h1)
            h <= h3 -> (h - h2) / (h3 - h2)
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    val maxHeight: Dp get() = fullHeight

    /**
     * 0 at the pill, 1 once the surface is as tall as level 1. Drives the morph (width,
     * corners, colour, content fade) so tap, drag and collapse all share one path.
     */
    fun morphFraction(height: Dp): Float {
        val span = (height(CutoutExpansionLevel.LEVEL_1_SINGLE) - pillSize).value
        if (span <= 0f) return 1f
        return ((height - pillSize).value / span).coerceIn(0f, 1f)
    }

    /**
     * Where a released drag lands: the rest height nearest to where the surface would coast
     * to with its current [velocityDpPerSec], so a flick moves on a level instead of snapping back.
     */
    fun snapLevel(height: Dp, velocityDpPerSec: Float): CutoutExpansionLevel {
        val projected = height.value + velocityDpPerSec * 0.12f
        return CutoutExpansionLevel.entries.minBy { kotlin.math.abs(height(it).value - projected) }
    }

    /** Window size that fits the surface at [height], with room for its shadow. */
    fun windowFor(height: Dp): OverlayWindowSpec =
        if (height >= fullHeight - 1.dp) OverlayWindowSpec.FullScreen
        else OverlayWindowSpec.Expanded(surfaceTop + height + SHADOW_MARGIN)

    companion object {
        val CONTROLS_HEIGHT: Dp = 64.dp
        val SHADOW_MARGIN: Dp = 18.dp
    }
}

/**
 * The display in its natural (portrait) orientation and its current rotation
 * ([Surface.ROTATION_0] … [Surface.ROTATION_270]).
 */
@androidx.compose.runtime.Immutable
data class IslandDisplay(
    val naturalWidthPx: Int,
    val naturalHeightPx: Int,
    val rotation: Int = Surface.ROTATION_0,
    /** Radius of the screen's own rounded corners at the top, in px (0 = unknown). */
    val cornerRadiusPx: Int = 0
)

/** A rectangle in natural-frame pixels. */
data class NaturalRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Rotation lock for the island. Everything is decided in the natural (portrait) frame and
 * then mapped to the current screen, so in landscape the window sits on the camera edge,
 * and its content is turned by [contentRotation] so it looks exactly as it does in portrait.
 */
object IslandWindowPlacement {

    fun naturalRect(
        spec: OverlayWindowSpec,
        geometry: CutoutGeometry,
        display: IslandDisplay,
        density: Float
    ): NaturalRect {
        val nw = display.naturalWidthPx
        val nh = display.naturalHeightPx
        return when (spec) {
            OverlayWindowSpec.Collapsed -> {
                val w = (OverlayCutoutDimensions.DEFAULT_TOUCH_WIDTH.value * density).toInt().coerceAtMost(nw)
                val h = maxOf(
                    ((geometry.bottomDp.value + 20f) * density).toInt(),
                    (OverlayCutoutDimensions.DEFAULT_STATUS_BAR_HEIGHT.value * density).toInt()
                )
                val cx = (geometry.centerXDp.value * density).toInt()
                NaturalRect((cx - w / 2).coerceIn(0, nw - w), 0, w, h)
            }
            is OverlayWindowSpec.Expanded ->
                NaturalRect(0, 0, nw, (spec.heightDp.value * density).toInt().coerceIn(1, nh))
            OverlayWindowSpec.FullScreen -> NaturalRect(0, 0, nw, nh)
        }
    }

    /**
     * The window the island is drawn in: always the full natural width from the top-left
     * corner, only the height follows [spec]. Its origin never moves, so a resize can't show
     * the previous frame shifted sideways (the open/close flick to the left).
     */
    fun drawingRect(
        spec: OverlayWindowSpec,
        geometry: CutoutGeometry,
        display: IslandDisplay,
        density: Float
    ): NaturalRect {
        val r = naturalRect(spec, geometry, display, density)
        return NaturalRect(0, 0, display.naturalWidthPx, r.height)
    }

    /** [rect] in current screen coordinates: x, y, width, height. */
    fun toScreen(rect: NaturalRect, display: IslandDisplay): Rect {
        val nw = display.naturalWidthPx
        val nh = display.naturalHeightPx
        val (l, t, w, h) = rect
        return when (display.rotation) {
            // Natural top edge on the left of the screen.
            Surface.ROTATION_90 -> Rect(t, nw - l - w, t + h, nw - l)
            Surface.ROTATION_180 -> Rect(nw - l - w, nh - t - h, nw - l, nh - t)
            // Natural top edge on the right of the screen.
            Surface.ROTATION_270 -> Rect(nh - t - h, l, nh - t, l + w)
            else -> Rect(l, t, l + w, t + h)
        }
    }

    /** Inverse of [toScreen], for cutout rects the system reports in screen coordinates. */
    fun toNatural(screen: Rect, display: IslandDisplay): Rect {
        val nw = display.naturalWidthPx
        val nh = display.naturalHeightPx
        return when (display.rotation) {
            Surface.ROTATION_90 -> Rect(nw - screen.bottom, screen.left, nw - screen.top, screen.right)
            Surface.ROTATION_180 -> Rect(nw - screen.right, nh - screen.bottom, nw - screen.left, nh - screen.top)
            Surface.ROTATION_270 -> Rect(screen.top, nh - screen.right, screen.bottom, nh - screen.left)
            else -> Rect(screen)
        }
    }

    /** Degrees to turn the content (graphicsLayer rotationZ) so it stays upright to the phone. */
    fun contentRotation(display: IslandDisplay): Float = when (display.rotation) {
        Surface.ROTATION_90 -> -90f
        Surface.ROTATION_180 -> 180f
        Surface.ROTATION_270 -> 90f
        else -> 0f
    }
}

/** The window's size. The island only ever animates inside a window at least this big. */
sealed interface OverlayWindowSpec {
    /** The small touch strip over the camera. */
    data object Collapsed : OverlayWindowSpec

    /** Full width, [heightDp] tall from the top of the screen. */
    data class Expanded(val heightDp: Dp) : OverlayWindowSpec

    data object FullScreen : OverlayWindowSpec
}

data class CutoutGeometry(
    val centerXDp: Dp,
    val centerYDp: Dp,
    val topDp: Dp,
    val widthDp: Dp,
    val heightDp: Dp,
    val bottomDp: Dp,
    val isRealHardwareCutout: Boolean
)

enum class CutoutExpansionLevel {
    COLLAPSED,
    LEVEL_1_SINGLE,
    LEVEL_2_THREE,
    LEVEL_3_SIX,
    LEVEL_4_FULL
}
