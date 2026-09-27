package com.theveloper.pixelplay.ui.glancewidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Small bitmaps for widget chrome that Glance has no modifier for.
 *
 * Glance covers a solid background and a corner radius natively, so those never come through
 * here. What it has no primitive for is a **border** and a **progress bar**: there is no
 * stroke modifier, and `defaultWeight()` splits space evenly rather than by a fraction, so a
 * bar that is 37% full cannot be expressed as layout. Both are drawn here instead and applied
 * with `background(ImageProvider(bitmap))`.
 *
 * Everything is cached and tiny — a 400x8px progress bar is ~13 KB — but the cache still
 * matters, because these are re-requested on every widget update, which during playback means
 * several times a second.
 */
internal object WidgetSurfaceRenderer {

    private const val CACHE_BYTES = 2 * 1024 * 1024 // 2 MiB

    /** Beyond this the bitmap costs more than the chrome is worth; it is scaled to fit. */
    private const val MAX_EDGE_PX = 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun clearCache() = cache.evictAll()

    /**
     * A rounded rectangle outline on a transparent field — the `OUTLINED` background style.
     *
     * Drawn rather than declared because Glance has no border modifier and a `<shape>` drawable
     * would bake in one fixed corner radius, while the radius here is a user setting.
     */
    fun outline(
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float,
        color: Int,
        strokePx: Float,
    ): Bitmap? {
        val w = widthPx.coerceIn(1, MAX_EDGE_PX)
        val h = heightPx.coerceIn(1, MAX_EDGE_PX)
        val stroke = strokePx.coerceAtLeast(1f)
        val key = "outline|$w|$h|${radiusPx.toInt()}|$color|${stroke.toInt()}"

        cache.get(key)?.let { if (!it.isRecycled) return it }

        return draw(key, w, h) { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                this.color = color
            }
            // Inset by half the stroke so the line sits fully inside the bitmap rather than
            // being clipped in half at the edges.
            val inset = stroke / 2f
            val rect = RectF(inset, inset, w - inset, h - inset)
            val radius = radiusPx.coerceIn(0f, min(rect.width(), rect.height()) / 2f)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    /**
     * A progress bar filled to [fraction], straight or wavy.
     *
     * The wave is drawn only across the played portion and flattens out over the last few
     * pixels, so the crest never reads as overshooting the playhead.
     */
    fun progress(
        widthPx: Int,
        heightPx: Int,
        fraction: Float,
        activeColor: Int,
        trackColor: Int,
        wavy: Boolean,
    ): Bitmap? {
        val w = widthPx.coerceIn(1, MAX_EDGE_PX)
        val h = heightPx.coerceIn(2, 64)
        val f = fraction.coerceIn(0f, 1f)
        // Quantised to 1%: a bar redrawn for every millisecond of playback would defeat the
        // cache, and a hundredth of a bar's width is below what anyone can see.
        val step = (f * 100f).toInt()
        val key = "progress|$w|$h|$step|$activeColor|$trackColor|${if (wavy) 1 else 0}"

        cache.get(key)?.let { if (!it.isRecycled) return it }

        return draw(key, w, h) { canvas ->
            val thickness = if (wavy) max(2f, h * 0.28f) else h.toFloat()
            val centreY = h / 2f
            val playedWidth = w * step / 100f

            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = trackColor
                style = Paint.Style.STROKE
                strokeWidth = thickness
                strokeCap = Paint.Cap.ROUND
            }
            val active = Paint(track).apply { color = activeColor }

            // Track first, full width, then the played portion over the top.
            canvas.drawLine(thickness / 2f, centreY, w - thickness / 2f, centreY, track)
            if (playedWidth <= thickness) return@draw

            if (!wavy) {
                canvas.drawLine(thickness / 2f, centreY, playedWidth, centreY, active)
                return@draw
            }

            val amplitude = (h - thickness) / 2f
            val wavelength = max(8f, h * 1.6f)
            val path = Path()
            var x = thickness / 2f
            path.moveTo(x, centreY)
            while (x < playedWidth) {
                // Damp the wave as it approaches the playhead so it lands flat.
                val remaining = ((playedWidth - x) / wavelength).coerceIn(0f, 1f)
                val y = centreY + sin(x / wavelength * 2f * Math.PI).toFloat() * amplitude * remaining
                path.lineTo(x, y)
                x += 1f
            }
            canvas.drawPath(path, active)
        }
    }

    private inline fun draw(key: String, w: Int, h: Int, block: (Canvas) -> Unit): Bitmap? =
        try {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            block(Canvas(bitmap))
            cache.put(key, bitmap)
            bitmap
        } catch (e: OutOfMemoryError) {
            Timber.tag("WidgetSurface").w(e, "Out of memory drawing widget chrome")
            cache.evictAll()
            null
        } catch (e: Exception) {
            Timber.tag("WidgetSurface").e(e, "Failed to draw widget chrome")
            null
        }
}
