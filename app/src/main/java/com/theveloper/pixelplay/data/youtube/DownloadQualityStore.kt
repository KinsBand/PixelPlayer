package com.theveloper.pixelplay.data.youtube

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which [DownloadQuality] each downloaded song was saved at (song id -> quality), so a playlist's
 * download menu can say "12 songs" on High / Medium / Low. A small file of its own: the downloads
 * table has no quality column, and this avoids a database migration. Songs downloaded before this
 * existed are worked out from their file instead (see [classifyDownloadQuality]).
 */
@Singleton
class DownloadQualityStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "download-qualities-v1.json"))
    private val _qualities = MutableStateFlow(load())
    val qualities: StateFlow<Map<String, DownloadQuality>> = _qualities.asStateFlow()

    fun get(songId: String): DownloadQuality? = _qualities.value[songId]

    @Synchronized
    fun record(songId: String, quality: DownloadQuality) {
        if (_qualities.value[songId] == quality) return
        _qualities.update { it + (songId to quality) }
        save(_qualities.value)
    }

    @Synchronized
    fun forget(songId: String) {
        if (songId !in _qualities.value) return
        _qualities.update { it - songId }
        save(_qualities.value)
    }

    private fun load(): Map<String, DownloadQuality> = try {
        if (!file.baseFile.exists()) emptyMap() else {
            val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            json.keys().asSequence().mapNotNull { id ->
                DownloadQuality.entries.firstOrNull { it.name == json.optString(id) }?.let { id to it }
            }.toMap()
        }
    } catch (e: Exception) {
        Timber.w(e, "Download qualities unreadable; starting fresh")
        emptyMap()
    }

    private fun save(map: Map<String, DownloadQuality>) {
        val stream = try { file.startWrite() } catch (e: Exception) { Timber.w(e, "Can't save download qualities"); return }
        try {
            val json = JSONObject()
            map.forEach { (id, quality) -> json.put(id, quality.name) }
            stream.write(json.toString().toByteArray())
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
        }
    }
}

/**
 * Best guess at the quality of a download made before qualities were recorded, from its container
 * ("opus", "webm", "m4a", "mp4", a MIME type or a file extension) and average bitrate (kbps):
 * under 90 kbps = Low (the smallest rendition is ~48–70), Opus/WebM = High (the best rendition is
 * usually Opus ~160), AAC/M4A = Medium (~128). Null = can't tell.
 */
fun classifyDownloadQuality(container: String?, bitrateKbps: Int?): DownloadQuality? {
    val kind = container?.lowercase().orEmpty()
    if (bitrateKbps != null && bitrateKbps in 1 until 90) return DownloadQuality.LOW
    return when {
        "opus" in kind || "webm" in kind -> DownloadQuality.HIGH
        "m4a" in kind || "mp4" in kind || "aac" in kind -> DownloadQuality.MEDIUM
        else -> null
    }
}
