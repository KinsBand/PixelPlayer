package com.theveloper.pixelplay.data.radio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Station lists the radio has already loaded, so it never waits on the network twice.
 *
 * Two levels:
 *  - **Memory**, shared by every radio screen for the life of the process: switching scope,
 *    leaving and coming back, or opening the map is instant.
 *  - **Disk** (`cache/radio_stations/`), so the first open after a restart shows the last list
 *    straight away while a fresh copy loads in the background (stale-while-revalidate).
 *
 * Entries older than [FRESH_FOR_MS] are still served, just marked stale so the caller refreshes.
 */
@Singleton
class RadioStationCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    data class Entry(val stations: List<RadioStation>, val fetchedAt: Long) {
        val isStale: Boolean get() = System.currentTimeMillis() - fetchedAt > FRESH_FOR_MS
    }

    private val dir = File(context.cacheDir, "radio_stations")
    private val memory = ConcurrentHashMap<String, Entry>()

    /** Bumped on every change; lets the search index know when to rebuild. */
    @Volatile
    var version: Int = 0
        private set

    /** Memory only. Never touches the disk, so it is safe on the main thread. */
    fun peek(key: String): Entry? = memory[key]

    /** Memory, then disk. The disk copy is promoted to memory. */
    suspend fun get(key: String): Entry? {
        memory[key]?.let { return it }
        val fromDisk = withContext(Dispatchers.IO) { readDisk(key) } ?: return null
        // Another caller may have stored a fresher copy while we were reading.
        val winner = memory.merge(key, fromDisk) { current, disk -> if (current.fetchedAt >= disk.fetchedAt) current else disk }
        version++
        return winner
    }

    suspend fun put(key: String, stations: List<RadioStation>) {
        val entry = Entry(stations, System.currentTimeMillis())
        memory[key] = entry
        version++
        withContext(Dispatchers.IO) { writeDisk(key, entry) }
    }

    /** Every station in memory, de-duplicated; what the instant search looks through. */
    fun allStations(): List<RadioStation> {
        val seen = HashSet<String>()
        val out = ArrayList<RadioStation>()
        memory.values.forEach { entry -> entry.stations.forEach { if (seen.add(it.uuid)) out += it } }
        return out
    }

    private fun fileFor(key: String): File =
        File(dir, key.map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.') it else '_' }.joinToString("") + ".json")

    private fun readDisk(key: String): Entry? {
        val file = fileFor(key)
        if (!file.isFile) return null
        if (System.currentTimeMillis() - file.lastModified() > DISCARD_AFTER_MS) {
            file.delete()
            return null
        }
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("stations") ?: JSONArray()
            val list = ArrayList<RadioStation>(arr.length())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let(RadioBrowserRepository::parseStation)?.let(list::add)
            }
            Entry(list, root.optLong("fetchedAt", file.lastModified()))
        }.onFailure { Timber.tag(TAG).d(it, "bad cache file %s", file.name) }.getOrNull()
    }

    private fun writeDisk(key: String, entry: Entry) {
        runCatching {
            if (!dir.isDirectory) dir.mkdirs()
            val arr = JSONArray()
            entry.stations.forEach { arr.put(RadioBrowserRepository.toJson(it)) }
            val root = JSONObject().put("fetchedAt", entry.fetchedAt).put("stations", arr)
            val target = fileFor(key)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }.onFailure { Timber.tag(TAG).d(it, "couldn't cache %s", key) }
    }

    companion object {
        private const val TAG = "RadioCache"
        /** Station lists change slowly; refresh in the background after this. */
        const val FRESH_FOR_MS = 6L * 60 * 60 * 1000
        /** Disk copies older than this are thrown away instead of shown. */
        private const val DISCARD_AFTER_MS = 14L * 24 * 60 * 60 * 1000

        /**
         * Cache key for a scope at a home. Nearby lists are keyed on a ~11 km grid so a small move
         * doesn't throw the list away. Null when the scope can't be loaded for this home.
         */
        fun keyFor(scope: RadioScope, home: RadioHome): String? {
            val cc = home.countryCode?.uppercase()
            val lat = home.lat
            val lon = home.lon
            return when (scope) {
                RadioScope.LOCAL ->
                    if (lat != null && lon != null) "local_%.1f_%.1f_%s".format(java.util.Locale.ROOT, lat, lon, home.state.orEmpty()) else null
                RadioScope.REGION -> when {
                    cc != null && !home.state.isNullOrBlank() -> "region_${cc}_${home.state}"
                    lat != null && lon != null -> "region_%.1f_%.1f".format(java.util.Locale.ROOT, lat, lon)
                    else -> null
                }
                RadioScope.COUNTRY -> cc?.let { "country_$it" }
                RadioScope.WORLD -> "world"
            }
        }

        const val WORLD_MAP_KEY = "world_map"
    }
}
