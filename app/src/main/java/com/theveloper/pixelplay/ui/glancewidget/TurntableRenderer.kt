package com.theveloper.pixelplay.ui.glancewidget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the turntable disc as a single [Bitmap] so it can be handed to Glance as an
 * `ImageProvider`.
 *
 * Home-screen widgets cannot animate, and `RemoteViews` has no rotation primitive, so the
 * only way to show a spinning record is to publish a fresh, already-rotated frame. Doing
 * that 8 times a second means the per-frame cost has to stay small, hence the split below:
 *
 *  - The **base disc** (vinyl body, grooves, album-art label, spindle) never changes while a
 *    track is playing, so it is composed once and kept in [baseCache], keyed by everything
 *    that can affect its pixels.
 *  - Each **frame** then costs one bitmap allocation, one rotated blit of that cached base,
 *    and — when enabled — a fixed light sheen and tonearm drawn *after* the rotation, so they
 *    stay put while the record turns underneath them.
 *
 * The output bitmap is intentionally not reused between frames: it is serialised into a
 * `RemoteViews` and read asynchronously by the launcher process, so mutating it afterwards
 * can tear a frame that is still being marshalled.
 */
object TurntableRenderer {

    /**
     * Upper bound on the rendered edge, in pixels.
     *
     * RemoteViews bitmaps are copied across a binder transaction into the launcher, and the
     * system caps the total bitmap memory a widget may hold. 512px square in ARGB_8888 is
     * ~1 MiB, which is comfortably inside that budget even with several instances placed.
     */
    const val MAX_RENDER_PX = 512

    /** Smallest edge worth rendering; below this the disc is illegible anyway. */
    private const val MIN_RENDER_PX = 64

    private const val BASE_CACHE_BYTES = 6 * 1024 * 1024 // 6 MiB

    private val baseCache = object : LruCache<String, Bitmap>(BASE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val overlayCache = object : LruCache<String, Bitmap>(BASE_CACHE_BYTES / 3) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Everything that decides what a disc looks like. Used both to draw it and, via
     * [cacheKey], to decide whether a cached base can be reused.
     */
    data class DiscSpec(
        val sizePx: Int,
        /** Colour of the vinyl body. */
        val discColor: Int,
        /** Colour behind the label when there is no artwork. */
        val labelColor: Int,
        /** Colour of the glyph drawn on an artwork-less label. */
        val labelGlyphColor: Int,
        /** Groove colour; alpha is applied per ring. */
        val grooveColor: Int,
        /** Label diameter as a fraction of the disc diameter. */
        val labelFraction: Float,
        val showGrooves: Boolean,
        val showSheen: Boolean,
        val showTonearm: Boolean,
        val tonearmColor: Int,
        /** Identity of the artwork, so a new track invalidates the cached base. */
        val artworkKey: String,
    ) {
        fun cacheKey(): String = buildString {
            append(sizePx).append('|')
            append(discColor).append('|')
            append(labelColor).append('|')
            append(labelGlyphColor).append('|')
            append(grooveColor).append('|')
            append((labelFraction * 1000).toInt()).append('|')
            append(if (showGrooves) '1' else '0')
            append('|').append(artworkKey)
        }
    }

    /**
     * Returns a square frame of the disc rotated by [angleDeg], or `null` if the requested
     * size is unusable or the device is too low on memory to allocate it.
     *
     * Callers treat `null` as "skip this frame" rather than as an error: a dropped frame is
     * invisible, a crash inside a widget update is not.
     */
    fun renderFrame(
        spec: DiscSpec,
        angleDeg: Float,
        artwork: () -> Bitmap?,
    ): Bitmap? {
        val edge = spec.sizePx.coerceIn(MIN_RENDER_PX, MAX_RENDER_PX)
        if (edge < MIN_RENDER_PX) return null

        val normalisedSpec = spec.copy(sizePx = edge)
        val base = renderBase(normalisedSpec, artwork) ?: return null

        return try {
            val frame = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)
            val centre = edge / 2f

            canvas.save()
            canvas.rotate(angleDeg, centre, centre)
            canvas.drawBitmap(base, 0f, 0f, null)
            canvas.restore()

            // Composited outside the rotation: a light source and a tonearm that spun with
            // the record would read as a wobble rather than as rotation.
            renderOverlay(normalisedSpec)?.let { canvas.drawBitmap(it, 0f, 0f, null) }

            frame
        } catch (e: OutOfMemoryError) {
            Timber.tag(TAG).w(e, "Out of memory rendering turntable frame at %dpx", edge)
            baseCache.evictAll()
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to render turntable frame")
            null
        }
    }

    /** Drops every cached disc. Called on low memory and when widget styling changes. */
    fun clearCache() {
        baseCache.evictAll()
        overlayCache.evictAll()
    }

    // ---------------------------------------------------------------------------------
    // Base disc
    // ---------------------------------------------------------------------------------

    /**
     * The record itself, unrotated, cached.
     *
     * Public because the in-app preview animates the rotation with Compose rather than by
     * re-rasterising: it draws this bitmap under a `rotationZ` layer and lays [renderOverlay]
     * over the top, which is the same composition [renderFrame] performs for the widget.
     */
    fun renderBase(spec: DiscSpec, artwork: () -> Bitmap?): Bitmap? {
        val key = spec.cacheKey()
        baseCache.get(key)?.let { if (!it.isRecycled) return it }

        return try {
            val edge = spec.sizePx
            val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Only resolved on a cache miss: decoding and scaling artwork on every frame
            // would undo the point of caching the disc at all.
            drawDisc(canvas, edge, spec, artwork())
            baseCache.put(key, bitmap)
            bitmap
        } catch (e: OutOfMemoryError) {
            Timber.tag(TAG).w(e, "Out of memory composing turntable base disc")
            baseCache.evictAll()
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to compose turntable base disc")
            null
        }
    }

    /**
     * The parts that do not turn with the record — the light sheen and the tonearm — on a
     * transparent bitmap of the same size, or `null` when neither is enabled.
     */
    fun renderOverlay(spec: DiscSpec): Bitmap? {
        if (!spec.showSheen && !spec.showTonearm) return null
        val edge = spec.sizePx.coerceIn(MIN_RENDER_PX, MAX_RENDER_PX)
        val key = buildString {
            append(edge).append('|')
            append(spec.discColor).append('|')
            append(spec.tonearmColor).append('|')
            append(if (spec.showSheen) '1' else '0')
            append(if (spec.showTonearm) '1' else '0')
        }
        overlayCache.get(key)?.let { if (!it.isRecycled) return it }

        return try {
            val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (spec.showSheen) drawSheen(canvas, edge, spec.discColor)
            if (spec.showTonearm) drawTonearm(canvas, edge, spec)
            overlayCache.put(key, bitmap)
            bitmap
        } catch (e: OutOfMemoryError) {
            Timber.tag(TAG).w(e, "Out of memory composing turntable overlay")
            overlayCache.evictAll()
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to compose turntable overlay")
            null
        }
    }

    private fun drawDisc(canvas: Canvas, edge: Int, spec: DiscSpec, artwork: Bitmap?) {
        val centre = edge / 2f
        val radius = centre
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Vinyl body.
        paint.style = Paint.Style.FILL
        paint.color = spec.discColor
        canvas.drawCircle(centre, centre, radius, paint)

        val labelRadius = radius * spec.labelFraction.coerceIn(0.14f, 0.62f)

        if (spec.showGrooves) {
            drawGrooves(canvas, centre, radius, labelRadius, spec.grooveColor)
        }

        // Label: artwork when we have it, a tinted disc with a play glyph when we do not.
        if (artwork != null && !artwork.isRecycled && artwork.width > 0 && artwork.height > 0) {
            drawArtworkLabel(canvas, centre, labelRadius, artwork)
        } else {
            paint.shader = null
            paint.color = spec.labelColor
            canvas.drawCircle(centre, centre, labelRadius, paint)
            drawPlayGlyph(canvas, centre, labelRadius, spec.labelGlyphColor)
        }

        // A thin rim keeps the label from bleeding into the vinyl on dark artwork.
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, edge * 0.004f)
        paint.color = withAlpha(spec.grooveColor, 0.35f)
        canvas.drawCircle(centre, centre, labelRadius, paint)

        // Spindle hole.
        paint.style = Paint.Style.FILL
        paint.color = spec.discColor
        canvas.drawCircle(centre, centre, max(1.5f, edge * 0.012f), paint)
    }

    private fun drawGrooves(
        canvas: Canvas,
        centre: Float,
        radius: Float,
        labelRadius: Float,
        grooveColor: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(0.75f, radius * 0.006f)
        }
        val innerEdge = labelRadius * 1.12f
        val outerEdge = radius * 0.965f
        if (outerEdge <= innerEdge) return

        val rings = 16
        for (i in 0 until rings) {
            val t = i / (rings - 1f)
            val r = innerEdge + (outerEdge - innerEdge) * t
            // Grooves fade towards the label so the artwork stays the focal point.
            paint.color = withAlpha(grooveColor, 0.05f + 0.09f * t)
            canvas.drawCircle(centre, centre, r, paint)
        }
    }

    private fun drawArtworkLabel(canvas: Canvas, centre: Float, labelRadius: Float, art: Bitmap) {
        // Scale the shorter artwork edge up to the label diameter, then centre it, so a
        // non-square cover is cropped rather than squashed.
        val diameter = labelRadius * 2f
        val scale = max(diameter / art.width, diameter / art.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                centre - art.width * scale / 2f,
                centre - art.height * scale / 2f,
            )
        }
        val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.shader = shader
        }
        canvas.drawCircle(centre, centre, labelRadius, paint)
        paint.shader = null
    }

    /** The play-in-a-ring mark used when a track has no artwork. */
    private fun drawPlayGlyph(canvas: Canvas, centre: Float, labelRadius: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = max(1.5f, labelRadius * 0.09f)
        }
        canvas.drawCircle(centre, centre, labelRadius * 0.55f, paint)

        paint.style = Paint.Style.FILL
        val triangle = android.graphics.Path().apply {
            val h = labelRadius * 0.46f
            val w = labelRadius * 0.40f
            moveTo(centre - w * 0.35f, centre - h / 2f)
            lineTo(centre - w * 0.35f, centre + h / 2f)
            lineTo(centre + w * 0.65f, centre)
            close()
        }
        canvas.drawPath(triangle, paint)
    }

    // ---------------------------------------------------------------------------------
    // Fixed overlays
    // ---------------------------------------------------------------------------------

    /** A soft off-centre highlight, as if lit from the upper left. */
    private fun drawSheen(canvas: Canvas, edge: Int, discColor: Int) {
        val centre = edge / 2f
        val highlight = if (isLight(discColor)) Color.BLACK else Color.WHITE
        val gradient = RadialGradient(
            edge * 0.32f,
            edge * 0.28f,
            edge * 0.62f,
            intArrayOf(withAlpha(highlight, 0.14f), withAlpha(highlight, 0f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = gradient
        }

        // Clip the highlight to the disc so it does not spill onto the wallpaper.
        canvas.save()
        val clip = android.graphics.Path().apply {
            addCircle(centre, centre, centre, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clip)
        canvas.drawCircle(centre, centre, centre, paint)
        canvas.restore()
        paint.shader = null
    }

    /** A stylised tonearm resting on the record, anchored outside the top-right edge. */
    private fun drawTonearm(canvas: Canvas, edge: Int, spec: DiscSpec) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.tonearmColor
            style = Paint.Style.STROKE
            strokeWidth = max(2f, edge * 0.022f)
            strokeCap = Paint.Cap.ROUND
        }
        val pivotX = edge * 0.86f
        val pivotY = edge * 0.14f
        val headX = edge * 0.60f
        val headY = edge * 0.40f

        canvas.drawLine(pivotX, pivotY, headX, headY, paint)

        paint.style = Paint.Style.FILL
        canvas.drawCircle(pivotX, pivotY, edge * 0.045f, paint)
        canvas.drawCircle(headX, headY, edge * 0.028f, paint)
    }

    // ---------------------------------------------------------------------------------

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun isLight(color: Int): Boolean {
        val luminance =
            (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
        return luminance > 0.5f
    }

    /**
     * Downscales artwork to roughly the label size before it is used as a shader source.
     * Feeding a 1000px cover into a 120px label wastes both memory and fill rate on every
     * cache miss.
     */
    fun prepareArtwork(source: Bitmap?, labelPx: Int): Bitmap? {
        if (source == null || source.isRecycled) return null
        val target = labelPx.coerceAtLeast(MIN_RENDER_PX)
        val shortest = min(source.width, source.height)
        if (shortest <= target * 1.5f) return source
        val scale = target.toFloat() / shortest
        return try {
            Bitmap.createScaledBitmap(
                source,
                max(1, (source.width * scale).toInt()),
                max(1, (source.height * scale).toInt()),
                true,
            )
        } catch (e: OutOfMemoryError) {
            Timber.tag(TAG).w(e, "Out of memory scaling turntable artwork")
            source
        }
    }

    private const val TAG = "TurntableRenderer"
}
