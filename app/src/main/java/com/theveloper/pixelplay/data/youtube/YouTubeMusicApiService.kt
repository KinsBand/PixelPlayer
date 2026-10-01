package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.DownloadState
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchResultItem
import io.ktor.client.HttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicApiService @Inject constructor(
    @Suppress("unused") private val ktorClient: HttpClient, // Kept for DI compatibility
    private val favoritesDao: com.theveloper.pixelplay.data.database.FavoritesDao,
    private val cloudSongDao: com.theveloper.pixelplay.data.database.CloudSongDao
) {
    private data class CachedSearch(val songs: List<Song>, val expiresAt: Long)
    private val searchLocks = com.theveloper.pixelplay.utils.KeyedMutex<String>()
    /** Memory-only first paint; fresh lookup still refreshes flags and expired results. */
    fun cachedSongs(query: String): List<Song> = synchronized(searchCache) {
        searchCache[query.trim().lowercase(java.util.Locale.ROOT)]?.songs.orEmpty()
    }
    private val searchCache = object : LinkedHashMap<String, CachedSearch>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSearch>): Boolean = size > 64
    }

    /**
     * Searches YouTube Music for songs matching the provided query.
     * Uses NewPipe Extractor (open-source, no API key required).
     */
    suspend fun searchSongs(query: String, broad: Boolean = false): Result<List<Song>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val started = System.nanoTime()
            var cacheHit = false
            val key = (if (broad) "outer:" else "") + query.trim().lowercase(java.util.Locale.ROOT)
            val rawSongs = searchLocks.withKey(key) {
                synchronized(searchCache) { searchCache[key] }
                    ?.takeIf { it.expiresAt > System.currentTimeMillis() }
                    ?.let { cacheHit = true; return@withKey it.songs }
                val contentFilter = listOf(if (broad) "videos" else "music_songs")
                val searchQH = ServiceList.YouTube
                    .searchQHFactory
                    .fromQuery(query, contentFilter, "")
                val searchInfo = NewPipeExecution.run { SearchInfo.getInfo(
                    ServiceList.YouTube,
                    searchQH
                ) }

                ensureActive()
                val songs = searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { mapToSong(it) }
                synchronized(searchCache) { searchCache[key] = CachedSearch(songs, System.currentTimeMillis() + 5 * 60_000) }
                songs
            }
            val favoriteIds = if (rawSongs.isEmpty()) emptySet() else favoritesDao.getFavoriteIdsAmong(rawSongs.map { it.id }).toSet()
            val downloadedSongsMap = if (rawSongs.isEmpty()) emptyMap() else cloudSongDao.getByIds(rawSongs.map { it.id }).associateBy { it.id }

            val hydrated = rawSongs.map { song ->
                val isFav = favoriteIds.contains(song.id)
                val cloudEntity = downloadedSongsMap[song.id]
                val dlState = if (cloudEntity?.isDownloaded == true) DownloadState.DOWNLOADED else DownloadState.NOT_DOWNLOADED
                song.copy(isFavorite = isFav, downloadState = dlState)
            }
            synchronized(searchCache) { searchCache[key]?.let { searchCache[key] = it.copy(songs = hydrated) } }
            Timber.tag("StreamingLatency").d("search_ms=%d cache_hit=%b results=%d", (System.nanoTime() - started) / 1_000_000, cacheHit, hydrated.size)
            Result.success(hydrated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Search failed for query: $query")
            Result.failure(e)
        }
    }

    /** Next NewPipe page per normalized query, for "Show more" in search. */
    private val moreSongPages = object : LinkedHashMap<String, org.schabi.newpipe.extractor.Page?>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, org.schabi.newpipe.extractor.Page?>): Boolean = size > 16
    }

    /**
     * Loads the next page of song results for [query]. The first call for a query walks past
     * page one (already on screen) and returns page two. Returns the songs plus whether
     * another page exists.
     */
    suspend fun moreSongs(query: String): Result<Pair<List<Song>, Boolean>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList<Song>() to false)
        try {
            val key = query.trim().lowercase(java.util.Locale.ROOT)
            val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query, listOf("music_songs"), "")
            val known = synchronized(moreSongPages) { moreSongPages.containsKey(key) }
            val page: org.schabi.newpipe.extractor.Page? = if (known) {
                synchronized(moreSongPages) { moreSongPages[key] }
            } else {
                val first = NewPipeExecution.run { SearchInfo.getInfo(ServiceList.YouTube, handler) }
                first.nextPage
            }
            ensureActive()
            if (!org.schabi.newpipe.extractor.Page.isValid(page)) {
                synchronized(moreSongPages) { moreSongPages[key] = null }
                return@withContext Result.success(emptyList<Song>() to false)
            }
            val more = NewPipeExecution.run { SearchInfo.getMoreItems(ServiceList.YouTube, handler, page) }
            ensureActive()
            val next = more.nextPage
            synchronized(moreSongPages) { moreSongPages[key] = next }
            val songs = hydrate(more.items.filterIsInstance<StreamInfoItem>().mapNotNull { mapToSong(it) })
            Result.success(songs to org.schabi.newpipe.extractor.Page.isValid(next))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Loading more songs failed for query: $query")
            Result.failure(e)
        }
    }

    /** Forget paging for [query] so the next "Show more" starts from page two again. */
    fun resetMoreSongs(query: String) {
        synchronized(moreSongPages) { moreSongPages.remove(query.trim().lowercase(java.util.Locale.ROOT)) }
    }

    suspend fun hydrate(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return songs
        val ids = songs.map { it.id }
        val favorites = favoritesDao.getFavoriteIdsAmong(ids).toSet()
        val downloads = cloudSongDao.getByIds(ids).associateBy { it.id }
        return songs.map { song -> song.copy(isFavorite = song.id in favorites,
            downloadState = if (downloads[song.id]?.isDownloaded == true) DownloadState.DOWNLOADED else DownloadState.NOT_DOWNLOADED) }
    }

    suspend fun searchCollections(query: String, filter: SearchFilterType): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val contentFilter = when (filter) {
            SearchFilterType.ARTISTS -> "music_artists"
            SearchFilterType.ALBUMS -> "music_albums"
            SearchFilterType.PLAYLISTS -> "music_playlists"
            else -> return@withContext emptyList()
        }
        val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query, listOf(contentFilter), "")
        val info = NewPipeExecution.run(NewPipeExecution.Lane.BACKGROUND) { SearchInfo.getInfo(ServiceList.YouTube, handler) }
        ensureActive()
        info.relatedItems.mapNotNull { item ->
            val url = item.url.toHttpUrlOrNull() ?: return@mapNotNull null
            val art = item.thumbnails.maxByOrNull { it.height }?.url
            val localId = item.url.hashCode().toLong() or Long.MIN_VALUE
            when (item) {
                is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> {
                    val browseId = url.pathSegments.lastOrNull()?.takeIf { it.startsWith("UC") } ?: return@mapNotNull null
                    SearchResultItem.ArtistItem(
                        Artist(localId, item.name, 0, art), browseId)
                }
                is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> {
                    val playlistId = url.queryParameter("list") ?: return@mapNotNull null
                    if (filter == SearchFilterType.ALBUMS)
                        SearchResultItem.AlbumItem(
                            Album(localId, item.name, item.uploaderName.orEmpty(), 0, 0, art,
                                item.streamCount.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()), "VL$playlistId")
                    else SearchResultItem.PlaylistItem(
                        Playlist("ytbrowse_$playlistId", item.name, emptyList(),
                            coverImageUri = art, source = "YOUTUBE_MUSIC"), "VL$playlistId")
                }
                else -> null
            }
        }
    }

    internal fun mapToSong(item: StreamInfoItem): Song? {
        val videoId = try {
            item.url.substringAfter("watch?v=").substringBefore("&")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not extract video ID from: ${item.url}")
            return null
        }

        if (!Regex("[A-Za-z0-9_-]{11}").matches(videoId)) return null

        val rawTitle = item.name ?: "Unknown Track"
        val rawArtist = item.uploaderName ?: "Unknown Artist"
        val (finalTitle, finalArtist) = parseTitleAndArtist(rawTitle, rawArtist)

        val thumbnailUrl = item.thumbnails.maxByOrNull { it.height.toLong() * it.width }?.url

        val durationMs = if (item.duration > 0) item.duration * 1000L else 0L

        val artistId = finalArtist.hashCode().toLong()
        val albumName = "YouTube Music"

        return Song(
            id = "yt_$videoId",
            title = finalTitle,
            artist = finalArtist,
            artistId = artistId,
            artists = listOf(ArtistRef(id = artistId, name = finalArtist, isPrimary = true)),
            album = albumName,
            albumId = albumName.hashCode().toLong(),
            albumArtist = finalArtist,
            path = "",
            contentUriString = "youtube://$videoId",
            albumArtUriString = thumbnailUrl,
            duration = durationMs,
            mimeType = "audio/mp4",
            bitrate = null,
            sampleRate = null,
            youtubeId = videoId,
            downloadState = DownloadState.NOT_DOWNLOADED,
            isFavorite = false
        )
    }

    private fun parseTitleAndArtist(rawTitle: String, rawArtist: String): Pair<String, String> {
        var title = rawTitle.replace(Regex("""(?i)\(official video\)|\[official video\]|\(official music video\)|\[hd\]|\(official audio\)|\(lyrics\)|\[lyrics\]|\(audio\)|\(visualizer\)"""), "").trim()
        var artist = rawArtist.trim()

        // Remove " - Topic" suffix that YouTube Music channels often have
        if (artist.endsWith(" - Topic")) {
            artist = artist.removeSuffix(" - Topic").trim()
        }

        if ((artist == "Unknown Artist" || artist.isBlank()) && title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            artist = parts[0].trim()
            title = parts[1].trim()
        }
        if (artist.isBlank()) artist = "YouTube Artist"
        if (title.isBlank()) title = rawTitle
        return Pair(title, artist)
    }

    companion object {
        private const val TAG = "YouTubeMusicApiService"
    }
}

