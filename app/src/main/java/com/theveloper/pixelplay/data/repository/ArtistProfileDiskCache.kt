package com.theveloper.pixelplay.data.repository

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Artist profiles saved on the device (B3) so the page opens instantly, works offline after a
 * restart (C1) and doesn't re-run dozens of catalogue requests every cold start.
 *
 * One small JSON file per artist under `files/artist_profiles/`. Entries older than [TTL_MS] are
 * still returned (marked stale) so the page can show them while it refreshes; entries older than
 * [MAX_AGE_MS] are deleted. A schema bump invalidates everything.
 */
class ArtistProfileDiskCache(context: Context) {

    data class Entry(val data: ArtistCrossPlatformData, val savedAt: Long) {
        val isStale: Boolean get() = System.currentTimeMillis() - savedAt > TTL_MS
    }

    private data class Stored(val schema: Int, val savedAt: Long, val data: ArtistCrossPlatformData)

    private val gson = Gson()
    private val dir = File(context.filesDir, "artist_profiles")

    fun read(artistName: String): Entry? = synchronized(this) {
        val file = fileFor(artistName)
        if (!file.isFile) return null
        return try {
            val stored = AtomicFile(file).openRead().bufferedReader().use { gson.fromJson(it, Stored::class.java) }
            when {
                stored == null || stored.schema != SCHEMA -> { file.delete(); null }
                System.currentTimeMillis() - stored.savedAt > MAX_AGE_MS -> { file.delete(); null }
                // Gson skips Kotlin's null checks: reject files missing required parts.
                !isComplete(stored) -> { file.delete(); null }
                else -> Entry(stored.data, stored.savedAt)
            }
        } catch (e: Exception) {
            Timber.w(e, "Unreadable artist cache for %s", artistName)
            file.delete()
            null
        }
    }

    fun write(artistName: String, data: ArtistCrossPlatformData) = synchronized(this) {
        try {
            dir.mkdirs()
            val atomic = AtomicFile(fileFor(artistName))
            val out = atomic.startWrite()
            try {
                out.write(gson.toJson(Stored(SCHEMA, System.currentTimeMillis(), data)).toByteArray())
                atomic.finishWrite(out)
            } catch (e: Exception) {
                atomic.failWrite(out)
                throw e
            }
            trim()
        } catch (e: Exception) {
            Timber.w(e, "Couldn't save artist cache for %s", artistName)
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun isComplete(stored: Stored): Boolean {
        val data = stored.data ?: return false
        return data.tracks != null && data.popularReleases != null && data.singlesAndEPs != null &&
            data.fansAlsoLikeArtists != null && data.appearsOn != null && data.genres != null
    }

    /** Keeps the folder bounded: the most recently saved [MAX_FILES] artists. */
    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
    }

    private fun fileFor(artistName: String): File {
        val key = artistName.trim().lowercase(Locale.ROOT)
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    companion object {
        const val TTL_MS = 24 * 60 * 60 * 1000L
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000L
        private const val MAX_FILES = 150
        private const val SCHEMA = 1
    }
}
