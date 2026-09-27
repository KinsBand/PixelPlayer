package com.theveloper.pixelplay.presentation.components.tabs

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** [ScorePainter] on an `android.graphics.Canvas` (screen via Compose, or a PDF page). */
class AndroidScorePainter : ScorePainter {
    var canvas: Canvas? = null

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private val faces = mapOf(
        ScorePainter.Font.REGULAR to Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
        ScorePainter.Font.BOLD to Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
        ScorePainter.Font.ITALIC to Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC),
        ScorePainter.Font.SERIF_ITALIC to Typeface.create(Typeface.SERIF, Typeface.ITALIC),
        ScorePainter.Font.SERIF_BOLD to Typeface.create(Typeface.SERIF, Typeface.BOLD),
        ScorePainter.Font.SERIF_BOLD_ITALIC to Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC),
    )

    private fun strokePaint(color: Int, width: Float): Paint = stroke.apply {
        this.color = color
        strokeWidth = width
    }

    private fun fillPaint(color: Int): Paint = fill.apply { this.color = color }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) {
        canvas?.drawLine(x1, y1, x2, y2, strokePaint(color, width))
    }

    override fun circle(cx: Float, cy: Float, r: Float, color: Int, fill: Boolean, stroke: Float) {
        canvas?.drawCircle(cx, cy, r, if (fill) fillPaint(color) else strokePaint(color, stroke))
    }

    override fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rotationDeg: Float, color: Int, fill: Boolean, stroke: Float) {
        val c = canvas ?: return
        c.save()
        c.rotate(rotationDeg, cx, cy)
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(rect, if (fill) fillPaint(color) else strokePaint(color, stroke))
        c.restore()
    }

    override fun path(points: FloatArray, closed: Boolean, color: Int, fill: Boolean, stroke: Float) {
        if (points.size < 4) return
        path.reset()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i + 1 < points.size) {
            path.lineTo(points[i], points[i + 1])
            i += 2
        }
        if (closed) path.close()
        canvas?.drawPath(path, if (fill) fillPaint(color) else strokePaint(color, stroke))
    }

    override fun quad(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, color: Int, stroke: Float) {
        path.reset()
        path.moveTo(x0, y0)
        path.quadTo(cx, cy, x1, y1)
        canvas?.drawPath(path, strokePaint(color, stroke))
    }

    override fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, color: Int, stroke: Float) {
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas?.drawArc(rect, startDeg, sweepDeg, false, strokePaint(color, stroke))
    }

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int, fill: Boolean, radius: Float, stroke: Float) {
        rect.set(left, top, right, bottom)
        val paint = if (fill) fillPaint(color) else strokePaint(color, stroke)
        if (radius > 0f) canvas?.drawRoundRect(rect, radius, radius, paint) else canvas?.drawRect(rect, paint)
    }

    private fun textPaint(size: Float, font: ScorePainter.Font): Paint = text.apply {
        textSize = size
        typeface = faces[font]
    }

    override fun text(s: String, x: Float, baseline: Float, size: Float, color: Int, align: ScorePainter.Align, font: ScorePainter.Font) {
        val p = textPaint(size, font)
        p.color = color
        p.textAlign = when (align) {
            ScorePainter.Align.LEFT -> Paint.Align.LEFT
            ScorePainter.Align.CENTER -> Paint.Align.CENTER
            ScorePainter.Align.RIGHT -> Paint.Align.RIGHT
        }
        canvas?.drawText(s, x, baseline, p)
    }

    override fun measure(s: String, size: Float, font: ScorePainter.Font): Float = textPaint(size, font).measureText(s)
}
