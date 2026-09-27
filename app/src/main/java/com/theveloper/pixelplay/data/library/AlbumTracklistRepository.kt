package com.theveloper.pixelplay.data.library

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.network.itunes.ITunesApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The full official tracklist of an album (from the iTunes catalogue), used to show which
 * tracks of an album you have and which are missing ("7 of 12").
 *
 * Results (including misses) are cached on disk, so each album is looked up at most once a
 * week and an album page opens without waiting on the network the second time.
 */
@Singleton
class AlbumTracklistRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val iTunesApiService: ITunesApiService
) {
    data class Track(
        val trackId: Long,
        val title: String,
        val artist: String,
        val number: Int,
        val disc: Int,
        val durationMs: Long
    )

    data class Tracklist(
        val collectionId: Long,
        val title: String,
        val artist: String,
        val year: Int,
        val artworkUrl: String?,
        val tracks: List<Track>
    ) {
        val size: Int get() = tracks.size
    }

    private data class Entry(val tracklist: Tracklist?, val at: Long)

    private val file = AtomicFile(File(context.noBackupFilesDir, "album-tracklists-v1.json"))
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, Entry>()
    private val inFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Tracklist?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadLock = Mutex()
    @Volatile private var loaded = false
    private var saveJob: Job? = null
    private val network = Semaphore(2)

    /** Cached result only (null when unknown or not looked up yet). Never touches the network. */
    fun cached(albumArtist: String, albumTitle: String): Tracklist? =
        cache[CollectionKeys.albumKey(albumArtist, albumTitle)]?.tracklist

    /** Finds the tracklist, from cache or the iTunes catalogue. Null when there is no match. */
    suspend fun find(albumArtist: String, albumTitle: String): Tracklist? {
        if (albumArtist.isBlank() || albumTitle.isBlank()) return null
        ensureLoaded()
        val key = CollectionKeys.albumKey(albumArtist, albumTitle)
        cache[key]?.let { entry ->
            val age = System.currentTimeMillis() - entry.at
            if (entry.tracklist != null && age < HIT_TTL_MS) return entry.tracklist
            if (entry.tracklist == null && age < MISS_TTL_MS) return null
        }
        val job = inFlight.getOrPut(key) {
            scope.async {
                try {
                    network.withPermit { lookup(albumArtist, albumTitle) }
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        return try {
            val result = job.await()
            cache[key] = Entry(result, System.currentTimeMillis())
            scheduleSave()
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network errors are not cached as misses.
            Timber.tag(TAG).d(e, "Tracklist lookup failed for %s", key)
            null
        }
    }

    private suspend fun lookup(albumArtist: String, albumTitle: String): Tracklist? = withContext(Dispatchers.IO) {
        val wantedTitle = CollectionKeys.normalizeAlbum(albumTitle)
        val wantedArtist = CollectionKeys.normalizeArtist(albumArtist)
        val candidates = iTunesApiService.searchAlbums("$albumArtist $albumTitle", "album", 15).results
        val match = candidates
            .filter { CollectionKeys.normalizeAlbum(it.collectionName) == wantedTitle }
            .filter { artistMatches(CollectionKeys.normalizeArtist(it.artistName), wantedArtist) }
            // Prefer the standard edition (fewest bonus tracks) when several editions match.
            .minByOrNull { it.trackCount.takeIf { count -> count > 0 } ?: Int.MAX_VALUE }
            ?: return@withContext null
        val tracks = iTunesApiService.lookupAlbumTracks(match.collectionId, "song").results
            .filter { it.wrapperType == "track" && !it.trackName.isNullOrBlank() }
            .map { track ->
                Track(
                    trackId = track.trackId,
                    title = track.trackName.orEmpty(),
                    artist = track.artistName ?: match.artistName,
                    number = track.trackNumber ?: 0,
                    disc = 1,
                    durationMs = track.trackTimeMillis ?: 0L
                )
            }
            .sortedBy { it.number }
        if (tracks.isEmpty()) return@withContext null
        Tracklist(
            collectionId = match.collectionId,
            title = match.collectionName,
            artist = match.artistName,
            year = match.releaseDate?.take(4)?.toIntOrNull() ?: 0,
            artworkUrl = match.artworkUrl,
            tracks = tracks
        )
    }

    private fun artistMatches(candidate: String, wanted: String): Boolean =
        candidate == wanted || (wanted.length >= 3 && (candidate.contains(wanted) || wanted.contains(candidate)))

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                runCatching {
                    if (file.baseFile.exists()) {
                        val type = object : TypeToken<Map<String, Entry>>() {}.type
                        val stored: Map<String, Entry>? = file.openRead().bufferedReader().use { gson.fromJson(it, type) }
                        stored?.forEach { (k, v) -> cache.putIfAbsent(k, v) }
                    }
                }.onFailure { Timber.tag(TAG).w(it, "Could not read tracklist cache") }
            }
            loaded = true
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(2_000)
            val snapshot = cache.entries
                .sortedByDescending { it.value.at }
                .take(MAX_ENTRIES)
                .associate { it.key to it.value }
            runCatching {
                val out = file.startWrite()
                try {
                    out.write(gson.toJson(snapshot).toByteArray())
                    file.finishWrite(out)
                } catch (e: Exception) {
                    file.failWrite(out)
                    throw e
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not save tracklist cache") }
        }
    }

    companion object {
        private const val TAG = "AlbumTracklists"
        private const val HIT_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_ENTRIES = 600

        private val bracketedFeature = Regex("""\s*[(\[]\s*(?:feat\.?|ft\.?|featuring|with)\s+[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
        private val trailingFeature = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)

        /** Title comparison that ignores "(Remastered)", "feat. X", punctuation and case. */
        fun sameTrack(a: String, b: String): Boolean = normalizeTrack(a) == normalizeTrack(b)

        fun normalizeTrack(title: String): String {
            val noFeat = title
                .replace(bracketedFeature, "")
                .replace(trailingFeature, "")
            return CollectionKeys.normalizeAlbum(noFeat)
        }
    }
}
