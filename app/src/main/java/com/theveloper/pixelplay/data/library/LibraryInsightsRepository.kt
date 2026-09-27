package com.theveloper.pixelplay.data.library

import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.metadata.SongMetadataGatherer
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The personal shelves of the Library tabs, built from your own listening history:
 * heavy rotation, albums to rediscover, albums to complete, your top artists, new releases
 * from them and your "genre DNA".
 *
 * Everything is computed from summaries (never by holding the whole library), capped at a
 * dozen items per shelf, and only refreshed while a Library tab asks for it.
 */
@Singleton
class LibraryInsightsRepository @Inject constructor(
    private val statsRepository: PlaybackStatsRepository,
    private val musicDao: MusicDao,
    private val cloudSongDao: CloudSongDao,
    private val streamCollection: StreamCollectionRepository,
    private val metadataGatherer: SongMetadataGatherer,
    private val tracklists: AlbumTracklistRepository,
    private val newReleases: NewReleasesRepository
) {
    data class AlbumCard(
        val id: Long,
        val title: String,
        val artist: String,
        val artUri: String?,
        val playTimeMs: Long = 0,
        val plays: Int = 0,
        val owned: Int = 0,
        val total: Int = 0
    )

    data class ArtistCard(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val playTimeMs: Long
    )

    data class GenreShare(val familyId: String, val label: String, val playTimeMs: Long, val fraction: Float)

    data class Insights(
        val heavyRotation: List<AlbumCard> = emptyList(),
        val rediscover: List<AlbumCard> = emptyList(),
        val completeThese: List<AlbumCard> = emptyList(),
        val topArtists: List<ArtistCard> = emptyList(),
        val newReleases: List<NewReleasesRepository.Release> = emptyList(),
        val genreDna: List<GenreShare> = emptyList(),
        /** Listening time per genre family id (last 90 days), used to order the Genres tab. */
        val genreListening: Map<String, Long> = emptyMap(),
        /** Listening time per artist id (last 90 days), used on the Artists tab rows. */
        val artistListening: Map<Long, Long> = emptyMap(),
        val computedAt: Long = 0
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var slowJob: Job? = null
    /** Stats version ([PlaybackStatsRepository.refreshFlow]) the current insights were built from. */
    @Volatile private var computedVersion = Long.MIN_VALUE

    private val _insights = MutableStateFlow(Insights())
    val insights: StateFlow<Insights> = _insights.asStateFlow()

    /**
     * Refreshes only when there are new plays since the last build or the shelves are older
     * than [STALE_MS]. Building reads the whole play history file, so it is never done just
     * because a tab was shown again.
     */
    fun ensureFresh() {
        if (needsRefresh()) scope.launch { refresh() }
    }

    /**
     * Keeps the shelves current while a Library tab that shows them is on screen. Call it from
     * a `LaunchedEffect`: it suspends until that tab leaves the screen. (It used to follow plays
     * forever after the first visit, re-reading the whole play history after every song.)
     */
    @OptIn(FlowPreview::class)
    suspend fun follow() {
        ensureFresh()
        statsRepository.refreshFlow.drop(1).debounce(FOLLOW_DEBOUNCE_MS).collect { refresh() }
    }

    private fun needsRefresh(): Boolean {
        val age = System.currentTimeMillis() - _insights.value.computedAt
        return age > STALE_MS || statsRepository.refreshFlow.value != computedVersion
    }

    private data class Resolved(
        val albumId: Long?,
        val albumKey: String,
        val albumTitle: String,
        val albumArtist: String,
        val artUri: String?,
        val artistIds: List<Pair<Long, String>>,
        val genre: String?
    )

    suspend fun refresh() = mutex.withLock {
        // Another caller may have rebuilt while this one waited for the lock.
        if (!needsRefresh()) return@withLock
        try {
            val version = statsRepository.refreshFlow.value
            withContext(Dispatchers.Default) { compute() }
            computedVersion = version
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not build library insights")
        }
    }

    private suspend fun compute() {
        val now = System.currentTimeMillis()
        val events = statsRepository.exportEventsForBackup()
        val recentCut = now - 30 * DAY
        val dnaCut = now - 90 * DAY
        val rediscoverCut = now - 180 * DAY

        // Aggregate per song first (small), then resolve songs once.
        class SongAgg { var recent = 0L; var dna = 0L; var old = 0L; var oldPlays = 0; var recentPlays = 0; var lastPlayed = 0L }
        val perSong = HashMap<String, SongAgg>()
        events.forEach { e ->
            val end = e.endTimestamp ?: e.timestamp
            val agg = perSong.getOrPut(e.songId) { SongAgg() }
            if (end >= recentCut) { agg.recent += e.durationMs; agg.recentPlays++ }
            if (end >= dnaCut) agg.dna += e.durationMs
            if (end < rediscoverCut) { agg.old += e.durationMs; agg.oldPlays++ }
            if (end > agg.lastPlayed) agg.lastPlayed = end
        }
        val resolved = resolveSongs(perSong.keys)

        // Albums.
        class AlbumAgg(val r: Resolved) { var recent = 0L; var recentPlays = 0; var old = 0L; var oldPlays = 0; var last = 0L }
        val albums = HashMap<String, AlbumAgg>()
        // Artists and genres.
        val artistTime = HashMap<Long, Long>()
        val artistNames = HashMap<Long, String>()
        val familyTime = HashMap<String, Long>()
        perSong.forEach { (songId, agg) ->
            val r = resolved[songId] ?: return@forEach
            val album = albums.getOrPut(r.albumKey) { AlbumAgg(r) }
            album.recent += agg.recent; album.recentPlays += agg.recentPlays
            album.old += agg.old; album.oldPlays += agg.oldPlays
            if (agg.lastPlayed > album.last) album.last = agg.lastPlayed
            r.artistIds.forEach { (id, name) ->
                artistTime[id] = (artistTime[id] ?: 0L) + agg.dna
                artistNames.putIfAbsent(id, name)
            }
            familyOf(r.genre)?.let { family -> familyTime[family] = (familyTime[family] ?: 0L) + agg.dna }
        }

        fun AlbumAgg.card(playTime: Long, plays: Int) = r.albumId?.let { id ->
            AlbumCard(id, r.albumTitle, r.albumArtist, r.artUri, playTime, plays)
        }

        val heavy = albums.values
            .filter { it.recent >= MIN_HEAVY_MS }
            .sortedByDescending { it.recent }
            .mapNotNull { it.card(it.recent, it.recentPlays) }
            .distinctBy { it.id }
            .take(SHELF)
        val rediscover = albums.values
            .filter { it.oldPlays >= 5 && it.last < rediscoverCut }
            .sortedByDescending { it.old }
            .mapNotNull { it.card(it.old, it.oldPlays) }
            .distinctBy { it.id }
            .take(SHELF)

        // Top artists this month.
        val monthArtistTime = HashMap<Long, Long>()
        perSong.forEach { (songId, agg) ->
            if (agg.recent <= 0) return@forEach
            resolved[songId]?.artistIds?.forEach { (id, _) -> monthArtistTime[id] = (monthArtistTime[id] ?: 0L) + agg.recent }
        }
        val topArtistIds = monthArtistTime.entries.sortedByDescending { it.value }.take(SHELF)
        val images = artistImages(topArtistIds.map { it.key })
        val topArtists = topArtistIds.map { (id, time) ->
            ArtistCard(id, artistNames[id].orEmpty(), images[id], time)
        }.filter { it.name.isNotBlank() }

        val totalFamily = familyTime.values.sum().coerceAtLeast(1L)
        val dna = familyTime.entries.sortedByDescending { it.value }.map { (family, time) ->
            GenreShare(family, familyLabel(family), time, time.toFloat() / totalFamily)
        }

        val previous = _insights.value
        _insights.value = previous.copy(
            heavyRotation = heavy,
            rediscover = rediscover,
            topArtists = topArtists,
            genreDna = dna,
            genreListening = familyTime,
            artistListening = artistTime,
            computedAt = now
        )

        // Network-backed shelves fill in afterwards.
        val artistsForReleases = (topArtists.map { it.name } +
            artistTime.entries.sortedByDescending { it.value }.mapNotNull { artistNames[it.key] })
        val candidates = albums.values
            .sortedByDescending { it.recent + it.old / 4 }
            .map { it.r }
        startSlowShelves(artistsForReleases, candidates)
    }

    private fun startSlowShelves(artists: List<String>, playedAlbums: List<Resolved>) {
        if (slowJob?.isActive == true) return
        slowJob = scope.launch {
            val releases = runCatching { newReleases.forArtists(artists) }.getOrDefault(emptyList())
            _insights.value = _insights.value.copy(newReleases = releases)
            val complete = runCatching { completeThese(playedAlbums) }.getOrDefault(emptyList())
            _insights.value = _insights.value.copy(completeThese = complete)
        }
    }

    /** Albums where you have at least 3 tracks but not the whole release. */
    private suspend fun completeThese(played: List<Resolved>): List<AlbumCard> {
        val streamed = streamCollection.songsByAlbumId.value
        // Candidates: albums you played (most first), then the biggest partial albums you own.
        val fromPlays = played.mapNotNull { r -> r.albumId?.let { id -> Triple(id, r.albumTitle, r.albumArtist) to r.artUri } }
        val owned = HashMap<Long, Int>()
        val seen = HashSet<Long>()
        val result = ArrayList<AlbumCard>()
        for ((info, art) in fromPlays.take(CANDIDATES)) {
            val (id, title, artist) = info
            if (!seen.add(id)) continue
            val count = owned.getOrPut(id) { ownedCount(id, streamed) }
            if (count < 3) continue
            val list = runCatching { tracklists.find(artist, title) }.getOrNull() ?: continue
            if (list.size in (count + 1)..60) {
                result += AlbumCard(id, title, artist, art ?: list.artworkUrl, owned = count, total = list.size)
            }
            if (result.size >= SHELF) break
        }
        return result.sortedByDescending { it.owned.toFloat() / it.total.coerceAtLeast(1) }
    }

    private suspend fun ownedCount(albumId: Long, streamed: Map<Long, List<com.theveloper.pixelplay.data.model.Song>>): Int {
        val library = if (CollectionKeys.isStreamAlbumId(albumId)) 0 else musicDao.countSongsInAlbum(albumId)
        return library + streamed[albumId].orEmpty().size
    }

    private suspend fun resolveSongs(ids: Collection<String>): Map<String, Resolved> {
        val out = HashMap<String, Resolved>(ids.size)
        val localIds = ids.mapNotNull { it.toLongOrNull() }
        localIds.chunked(500).forEach { chunk ->
            musicDao.getSongsByIdsListSimple(chunk).forEach { e -> out[e.id.toString()] = e.resolve() }
        }
        val cloudIds = ids.filter { it.toLongOrNull() == null }
        val albumIds = streamCollection.albumIdByKey.value
        val artistIds = streamCollection.artistIdByKey.value
        cloudIds.chunked(500).forEach { chunk ->
            cloudSongDao.getByIds(chunk).forEach { cloud ->
                val cached = metadataGatherer.cachedFor(
                    com.theveloper.pixelplay.data.model.Song(
                        id = cloud.id, title = cloud.title, artist = cloud.artist, artistId = 0,
                        album = cloud.album.orEmpty(), albumId = 0, path = "", contentUriString = cloud.contentUriString,
                        albumArtUriString = cloud.thumbnailUrl, duration = cloud.duration
                    )
                )
                val album = cloud.album?.takeUnless { CollectionKeys.isPlaceholderAlbum(it) }
                    ?: cached?.album?.takeUnless { CollectionKeys.isPlaceholderAlbum(it) }
                    ?: cloud.title
                // Same grouping as StreamCollectionRepository: album artist, else the first artist.
                val primary = cloud.artist.split(',', '&', ';').first().trim().ifBlank { cloud.artist.trim() }
                val artist = cached?.albumArtist?.takeIf { it.isNotBlank() } ?: primary
                val key = CollectionKeys.albumKey(artist, album)
                val artistId = artistIds[CollectionKeys.normalizeArtist(primary)]
                out[cloud.id] = Resolved(
                    albumId = albumIds[key],
                    albumKey = key,
                    albumTitle = album,
                    albumArtist = artist,
                    artUri = cloud.thumbnailUrl,
                    artistIds = listOfNotNull(artistId?.let { it to primary }),
                    genre = cached?.genre
                )
            }
        }
        return out
    }

    private fun SongEntity.resolve(): Resolved {
        val albumArtist = albumArtist?.takeIf { it.isNotBlank() } ?: artistName
        return Resolved(
            albumId = albumId,
            albumKey = "id:$albumId",
            albumTitle = albumName,
            albumArtist = albumArtist,
            artUri = albumArtUriString,
            artistIds = listOf(artistId to artistName),
            genre = genre
        )
    }

    private suspend fun artistImages(ids: List<Long>): Map<Long, String?> {
        if (ids.isEmpty()) return emptyMap()
        val local = ids.filter { !CollectionKeys.isStreamArtistId(it) }
        val result = HashMap<Long, String?>()
        if (local.isNotEmpty()) musicDao.getArtistsByIds(local).forEach { a ->
            result[a.id] = a.customImageUri?.takeIf { it.isNotBlank() } ?: a.imageUrl
        }
        return result
    }

    companion object {
        private const val TAG = "LibraryInsights"
        private const val DAY = 24L * 60 * 60 * 1000
        private const val STALE_MS = 10L * 60 * 1000
        private const val MIN_HEAVY_MS = 5L * 60 * 1000
        private const val SHELF = 12
        private const val CANDIDATES = 30
        private const val FOLLOW_DEBOUNCE_MS = 5_000L

        /** Genre family of a raw genre string; see [GenreFamilies.familyOf]. */
        fun familyOf(genre: String?): String? = GenreFamilies.familyOf(genre)

        fun familyLabel(family: String): String = GenreFamilies.familyLabel(family)
    }
}
