package com.theveloper.pixelplay.presentation.screens.stats.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Turns a rendered share card into a PNG and hands it to the system. Everything stays on the
 * device: the image goes to the app cache (for the share sheet / clipboard) or to Pictures.
 */
object StatsShareExporter {

    private const val CACHE_DIR = "shared_stats"
    private const val GALLERY_DIR = "PixelPlayer"
    private const val MAX_CACHED_CARDS = 6

    /** Writes [bitmap] to the cache and returns a FileProvider uri for it. */
    suspend fun writeToCache(context: Context, bitmap: Bitmap, name: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        // Keep the folder small: drop the oldest cards.
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_CACHED_CARDS - 1)
            ?.forEach { it.delete() }
        val file = File(dir, "$name.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun share(context: Context, uri: Uri, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun copyToClipboard(context: Context, uri: Uri, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, label, uri))
    }

    /** Saves to Pictures/PixelPlayer through MediaStore (no storage permission needed on API 29+). */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, name: String): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_DIR")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } ?: false
        }.onFailure { Timber.w(it, "Saving stats card failed") }.getOrDefault(false)
        if (written) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            resolver.delete(uri, null, null)
        }
        written
    }

    /** Hardware bitmaps can't be compressed everywhere; copy to a software bitmap first. */
    fun toSoftware(bitmap: Bitmap): Bitmap =
        if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
}
