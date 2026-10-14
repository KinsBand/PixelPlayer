package com.theveloper.pixelplay.data.lyrics.training

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.youtube.SongDownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Export training set": copies every downloaded song that has synced lyrics, with its `.lrc`,
 * to `Download/PixelPlay-training/` so it can be uploaded as a private Kaggle / Drive dataset for
 * the word-sync fine-tune (tools/alignment/singing_finetune, `prepare_data.py lrc`).
 *
 * Downloads without a sidecar get one more lyrics lookup first. Songs already exported are
 * remembered and skipped, so running it again only adds what you downloaded since.
 */
@Singleton
class TrainingSetExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudSongDao: CloudSongDao,
    private val musicDao: MusicDao,
    private val downloads: dagger.Lazy<SongDownloadManager>,
) {
    data class Result(val exported: Int, val alreadyExported: Int, val noSyncedLyrics: Int, val failed: Int) {
        val folder: String get() = "Download/$FOLDER"
    }

    private val prefs by lazy { context.getSharedPreferences("training_export", Context.MODE_PRIVATE) }

    suspend fun export(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Result = withContext(Dispatchers.IO) {
        val rows = cloudSongDao.getAllOnce().filter { it.isDownloaded }
        val done = prefs.getStringSet(KEY_EXPORTED, emptySet()).orEmpty().toMutableSet()
        var exported = 0; var already = 0; var noLyrics = 0; var failed = 0
        rows.forEachIndexed { i, row ->
            onProgress(i, rows.size)
            val file = (row.localFilePath ?: row.localSongId)?.let(::File)?.takeIf { it.isFile && it.length() > 0 }
            if (file == null) { failed++; return@forEachIndexed }
            if (row.id in done) { already++; return@forEachIndexed }
            try {
                if (!LyricsSidecar.hasSynced(file)) {
                    // One more try for downloads made before sidecars existed (or offline).
                    val song = musicDao.getSongByPath(file.absolutePath)?.toSong()
                    if (song != null) downloads.get().saveLyricsFor(song, file)
                }
                if (!LyricsSidecar.hasSynced(file)) { noLyrics++; return@forEachIndexed }
                val base = safeName("${row.artist} - ${row.title}") + " [" + row.id.takeLast(6) + "]"
                copyToDownloads(file, "$base.${file.extension.ifBlank { "m4a" }}")
                copyToDownloads(LyricsSidecar.lrcFor(file), "$base.lrc", forceMime = "application/octet-stream")
                done += row.id
                exported++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Training export failed for %s", row.id)
                failed++
            }
        }
        prefs.edit().putStringSet(KEY_EXPORTED, done).apply()
        onProgress(rows.size, rows.size)
        Result(exported, already, noLyrics, failed)
    }

    /** Forget what was exported, so the next export copies everything again. */
    fun resetExported() = prefs.edit().remove(KEY_EXPORTED).apply()

    private fun copyToDownloads(src: File, name: String, forceMime: String? = null) {
        val mime = forceMime
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(src.extension.lowercase())
            ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("Could not create $name in Downloads")
        try {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                ?: throw java.io.IOException("Could not write $name")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    private fun safeName(s: String) = s.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").trim().take(100)

    companion object {
        const val FOLDER = "PixelPlay-training"
        private const val KEY_EXPORTED = "exported_ids"
    }
}
