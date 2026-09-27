package com.theveloper.pixelplay.presentation.components.tabs

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.theveloper.pixelplay.data.songsterr.RenderedTrack
import com.theveloper.pixelplay.data.songsterr.TabSection
import java.io.File

/** Prints the whole part (same notation as the screen) to an A4 PDF in Downloads. */
object TabPdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36f

    /** Returns a content Uri for the saved PDF, or null on failure. Call off the main thread. */
    fun export(
        context: Context,
        track: RenderedTrack,
        sections: List<TabSection>,
        title: String,
        artist: String,
        partName: String,
    ): Uri? = runCatching {
        val metrics = ScoreMetrics(dp = 0.7f, sp = 0.7f)
        val painter = AndroidScorePainter()
        val colors = ScoreColors(
            ink = 0xFF111111.toInt(),
            faint = 0xFF777777.toInt(),
            accent = 0xFF1B7A33.toInt(),
            cursor = 0,
            loop = 0,
        )
        val layout = ScoreLayoutEngine.layout(
            track, sections, PAGE_W - MARGIN * 2 + 20 * metrics.dp, metrics, painter,
            expanded = sections.map { it.index }.toSet(),
        )
        val renderer = ScoreRenderer(track, metrics, colors)
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var canvas = page.canvas
        painter.canvas = canvas

        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            color = 0xFF111111.toInt()
        }
        head.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        head.textSize = 20f
        canvas.drawText(title, PAGE_W / 2f, MARGIN + 16f, head)
        head.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        head.textSize = 12f
        canvas.drawText(artist, PAGE_W / 2f, MARGIN + 34f, head)
        head.textSize = 10f
        head.color = 0xFF666666.toInt()
        canvas.drawText(partName, PAGE_W / 2f, MARGIN + 50f, head)

        var y = MARGIN + 64f
        for (s in layout.systems) {
            if (s.isCollapsed) continue
            if (y + s.height > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page.canvas
                painter.canvas = canvas
                y = MARGIN
            }
            canvas.save()
            canvas.translate(MARGIN - 10 * metrics.dp, y)
            renderer.draw(painter, s)
            canvas.restore()
            y += s.height
        }
        doc.finishPage(page)

        val safe = "$artist - $title ($partName)".replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        val name = "$safe.pdf"
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.also { u ->
                context.contentResolver.openOutputStream(u)?.use { doc.writeTo(it) }
            }
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val file = File(dir, name)
            file.outputStream().use { doc.writeTo(it) }
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }.getOrElse { Uri.fromFile(file) }
        }
        doc.close()
        uri
    }.getOrNull()
}
