package com.theveloper.pixelplay.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent record of songs for which no *synced* lyrics could be found online.
 *
 * Used so background work (queue prefetch, library backfill, plain -> synced upgrades) backs off
 * instead of re-querying the providers on every play, while still retrying forever with a
 * growing interval — LRCLIB and NetEase receive new synced uploads all the time.
 *
 * Only real "every provider answered, nothing matched" outcomes are recorded; network failures
 * are never counted as misses.
 */
internal class SyncedLyricsMissStore(
    private val file: File,
    private val gson: Gson,
    private val clock: () -> Long = System::currentTimeMillis
) {
    data class Miss(val attempts: Int, val lastAttemptAt: Long)

    private val misses: ConcurrentHashMap<String, Miss> by lazy { load() }
    private val writeLock = Any()

    fun shouldRetry(songId: String): Boolean {
        val miss = misses[songId] ?: return true
        return clock() - miss.lastAttemptAt >= backoffFor(miss.attempts)
    }

    fun recordMiss(songId: String) {
        val previous = misses[songId]
        misses[songId] = Miss(attempts = (previous?.attempts ?: 0) + 1, lastAttemptAt = clock())
        save()
    }

    fun clear(songId: String) {
        if (misses.remove(songId) != null) save()
    }

    fun clearAll() {
        misses.clear()
        save()
    }

    private fun load(): ConcurrentHashMap<String, Miss> {
        return try {
            if (!file.exists()) return ConcurrentHashMap()
            val type = object : TypeToken<Map<String, Miss>>() {}.type
            val parsed: Map<String, Miss>? = gson.fromJson(file.readText(), type)
            ConcurrentHashMap(parsed.orEmpty())
        } catch (e: Exception) {
            Log.w(TAG, "Resetting unreadable miss store: ${e.message}")
            ConcurrentHashMap()
        }
    }

    private fun save() {
        synchronized(writeLock) {
            try {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(gson.toJson(HashMap(misses)))
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist miss store: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "SyncedLyricsMissStore"
        private const val HOUR = 60L * 60L * 1000L

        /** 6h, 1d, 3d, 7d, then every 14d. */
        internal fun backoffFor(attempts: Int): Long = when {
            attempts <= 1 -> 6 * HOUR
            attempts == 2 -> 24 * HOUR
            attempts == 3 -> 72 * HOUR
            attempts == 4 -> 168 * HOUR
            else -> 336 * HOUR
        }
    }
}
