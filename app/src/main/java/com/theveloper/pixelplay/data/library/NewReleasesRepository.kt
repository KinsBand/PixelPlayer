package com.theveloper.pixelplay.data.library

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.network.itunes.ITunesApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "New from your artists": recent releases (last [WINDOW_DAYS] days) by the artists you
 * listen to most, from the iTunes catalogue. Each artist is checked at most once a day and
 * the results are kept on disk, so the Artists tab opens instantly.
 */
@Singleton
class NewReleasesRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val iTunesApiService: ITunesApiService
) {
    data class Release(
        val collectionId: Long,
        val title: String,
        val artist: String,
        val artworkUrl: String?,
        val releasedAt: Long,
        val trackCount: Int
    ) {
        val kind: String
            get() = when {
                title.contains("live", ignoreCase = true) && Regex("""\blive\b""", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "Live"
                trackCount in 1..3 || title.endsWith("- Single", ignoreCase = true) -> "Single"
                trackCount in 4..6 || title.endsWith("- EP", ignoreCase = true) -> "EP"
                else -> "Album"
            }

        val displayTitle: String
            get() = title.removeSuffix(" - Single").removeSuffix(" - EP")

        fun daysAgo(now: Long = System.currentTimeMillis()): Int =
            ChronoUnit.DAYS.between(Instant.ofEpochMilli(releasedAt), Instant.ofEpochMilli(now)).toInt().coerceAtLeast(0)
    }

    private data class Entry(val releases: List<Release>, val at: Long)

    private val file = AtomicFile(File(context.noBackupFilesDir, "new-releases-v1.json"))
    private val gson = Gson()
    private val lock = Mutex()
    private var cache: MutableMap<String, Entry>? = null
    private val network = Semaphore(2)

    /** Releases from the last [WINDOW_DAYS] days by [artists], newest first. */
    suspend fun forArtists(artists: List<String>, limitArtists: Int = 15): List<Release> = withContext(Dispatchers.IO) {
        val wanted = artists.map { it.trim() }.filter { it.isNotBlank() }
            .distinctBy { CollectionKeys.normalizeArtist(it) }
            .take(limitArtists)
        if (wanted.isEmpty()) return@withContext emptyList()
        val entries = load()
        val now = System.currentTimeMillis()
        var changed = false
        coroutineScope {
            wanted.map { artist ->
                async {
                    val key = CollectionKeys.normalizeArtist(artist)
                    val cached = entries[key]
                    if (cached != null && now - cached.at < REFRESH_MS) return@async
                    val fetched = network.withPermit { fetch(artist) } ?: return@async
                    lock.withLock { entries[key] = Entry(fetched, now) }
                    changed = true
                }
            }.awaitAll()
        }
        if (changed) save(entries)
        val cutoff = now - WINDOW_DAYS * DAY_MS
        wanted.flatMap { entries[CollectionKeys.normalizeArtist(it)]?.releases.orEmpty() }
            .filter { it.releasedAt in cutoff..now }
            .distinctBy { it.collectionId }
            .sortedByDescending { it.releasedAt }
    }

    private suspend fun fetch(artist: String): List<Release>? = try {
        val wanted = CollectionKeys.normalizeArtist(artist)
        iTunesApiService.searchAlbums(artist, "album", 40).results
            .filter { CollectionKeys.normalizeArtist(it.artistName) == wanted }
            .mapNotNull { album ->
                val released = album.releaseDate?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    ?: return@mapNotNull null
                Release(album.collectionId, album.collectionName, album.artistName, album.artworkUrl, released, album.trackCount)
            }
            .sortedByDescending { it.releasedAt }
            .take(8)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).d(e, "New releases lookup failed for %s", artist)
        null
    }

    private suspend fun load(): MutableMap<String, Entry> = lock.withLock {
        cache?.let { return it }
        val loaded: MutableMap<String, Entry> = runCatching {
            if (!file.baseFile.exists()) return@runCatching null
            val type = object : TypeToken<Map<String, Entry>>() {}.type
            file.openRead().bufferedReader().use { gson.fromJson<Map<String, Entry>>(it, type) }?.toMutableMap()
        }.getOrNull() ?: mutableMapOf()
        cache = loaded
        loaded
    }

    private suspend fun save(entries: Map<String, Entry>) = lock.withLock {
        runCatching {
            val out = file.startWrite()
            try {
                out.write(gson.toJson(entries).toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }.onFailure { Timber.tag(TAG).w(it, "Could not save new releases") }
    }

    companion object {
        private const val TAG = "NewReleases"
        const val WINDOW_DAYS = 45L
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val REFRESH_MS = 20L * 60 * 60 * 1000
    }
}
