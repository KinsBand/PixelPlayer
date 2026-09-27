package com.theveloper.pixelplay.presentation.components.tabs

/**
 * The few drawing calls the tab renderer needs. The app implements it on
 * `android.graphics.Canvas` (screen and PDF); keeping it tiny keeps the renderer testable.
 * Colours are ARGB ints; all sizes are in pixels.
 */
interface ScorePainter {
    enum class Align { LEFT, CENTER, RIGHT }
    enum class Font { REGULAR, BOLD, ITALIC, SERIF_ITALIC, SERIF_BOLD, SERIF_BOLD_ITALIC }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int)
    fun circle(cx: Float, cy: Float, r: Float, color: Int, fill: Boolean, stroke: Float = 1f)
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rotationDeg: Float, color: Int, fill: Boolean, stroke: Float = 1f)
    /** Poly-line through (x0, y0, x1, y1, …). */
    fun path(points: FloatArray, closed: Boolean, color: Int, fill: Boolean, stroke: Float = 1f)
    fun quad(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, color: Int, stroke: Float)
    fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, color: Int, stroke: Float)
    fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int, fill: Boolean = true, radius: Float = 0f, stroke: Float = 1f)
    fun text(s: String, x: Float, baseline: Float, size: Float, color: Int, align: Align = Align.LEFT, font: Font = Font.REGULAR)
    fun measure(s: String, size: Float, font: Font = Font.REGULAR): Float
}

/** Colours for one render. */
data class ScoreColors(
    val ink: Int,
    val faint: Int,
    val accent: Int,
    val cursor: Int,
    val loop: Int,
    /** Drum kit scoring: in time / early-late or dynamics / missed. */
    val hitGood: Int = 0xFF34C759.toInt(),
    val hitAmber: Int = 0xFFFFB020.toInt(),
    val hitMiss: Int = 0xFFFF453A.toInt(),
)

/** Pixel scale: [dp] px per dp, [sp] px per sp. */
data class ScoreMetrics(val dp: Float, val sp: Float)
