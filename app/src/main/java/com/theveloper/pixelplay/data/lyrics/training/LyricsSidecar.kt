package com.theveloper.pixelplay.data.lyrics.training

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A synced `.lrc` saved next to a downloaded song ("Song.m4a" + "Song.lrc").
 *
 * Downloads get one automatically when line-synced lyrics are found, so every song you download
 * is ready to use as word-sync training data (Settings › Lyrics › Export training set), and other
 * players pick the lyrics up too. Only line timing is written: that is all training needs.
 */
object LyricsSidecar {

    private val TIMESTAMP = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")

    /** path|lastModified -> has synced sidecar; keeps list rows from re-reading files. */
    private val cache = ConcurrentHashMap<String, Boolean>()

    fun lrcFor(audio: File): File = File(audio.parentFile, audio.nameWithoutExtension + ".lrc")

    /** True when [audio] has a sidecar .lrc with at least three timed lines. */
    fun hasSynced(audio: File): Boolean {
        val lrc = lrcFor(audio)
        if (!lrc.isFile) return false
        val key = "${lrc.absolutePath}|${lrc.lastModified()}"
        return cache.getOrPut(key) {
            runCatching {
                val head = lrc.inputStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    val n = input.read(buf)
                    if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
                }
                TIMESTAMP.findAll(head).take(3).count() >= 3
            }.getOrDefault(false)
        }
    }

    fun hasSynced(path: String?): Boolean = !path.isNullOrBlank() && hasSynced(File(path))

    /** Line-synced LRC text, or null when [lyrics] has no usable synced lines. */
    fun toLrc(lyrics: Lyrics?, title: String, artist: String): String? {
        val lines: List<SyncedLine> = lyrics?.synced.orEmpty().filter { it.line.isNotBlank() && it.time >= 0 }
        if (lines.size < 3) return null
        return buildString {
            if (title.isNotBlank()) append("[ti:").append(title.oneLine()).append("]\n")
            if (artist.isNotBlank()) append("[ar:").append(artist.oneLine()).append("]\n")
            append("[re:PixelPlayer]\n")
            lines.sortedBy { it.time }.forEach { l ->
                append('[').append(stamp(l.time)).append(']').append(l.line.oneLine()).append('\n')
            }
        }
    }

    /** Writes the sidecar for [audio]; returns true when a synced .lrc now exists. */
    fun write(audio: File, lyrics: Lyrics?, title: String, artist: String): Boolean {
        val text = toLrc(lyrics, title, artist) ?: return false
        val target = lrcFor(audio)
        val tmp = File(target.parentFile, target.name + ".tmp")
        return runCatching {
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
            target.isFile
        }.getOrElse { tmp.delete(); false }
    }

    fun delete(audio: File) {
        runCatching { lrcFor(audio).delete() }
    }

    private fun stamp(ms: Int): String {
        val cs = ms / 10
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%02d", cs / 6000, (cs / 100) % 60, cs % 100)
    }

    private fun String.oneLine() = replace('\n', ' ').replace('\r', ' ').trim()
}
