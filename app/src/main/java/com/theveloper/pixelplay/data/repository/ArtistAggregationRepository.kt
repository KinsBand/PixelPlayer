package com.theveloper.pixelplay.data.repository

import android.content.Context
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.CreditsAndRelease
import com.theveloper.pixelplay.data.model.DownloadState
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackSource
import com.theveloper.pixelplay.data.network.deezer.DeezerAlbum
import com.theveloper.pixelplay.data.network.deezer.DeezerApiService
import com.theveloper.pixelplay.data.network.deezer.DeezerArtist
import com.theveloper.pixelplay.data.network.deezer.DeezerTrack
import com.theveloper.pixelplay.data.network.itunes.ITunesAlbum
import com.theveloper.pixelplay.data.network.itunes.ITunesApiService
import com.theveloper.pixelplay.data.network.itunes.ITunesSong
import com.theveloper.pixelplay.data.youtube.YouTubeMusicApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy audience metrics. Only [totalFollowers] is real now (Deezer fans); every other field is 0.
 * Kept so older callers compile — the made-up listener, stream and payout numbers are gone (B1).
 */
data class CrossPlatformArtistMetrics(
    val totalMonthlyListeners: Long,
    val totalFollowers: Long,
    val spotifyListeners: Long,
    val spotifyFollowers: Long,
    val itunesListeners: Long,
    val itunesFollowers: Long,
    val youtubeMusicListeners: Long,
    val youtubeMusicFollowers: Long,
    val estimatedGrossPayout: Double,
    val spotifyPayout: Double,
    val itunesPayout: Double,
    val youtubeMusicPayout: Double
)

/** Legacy per-track stream breakdown. No longer estimated: always zeros. */
data class SongStreamsBreakdown(
    val songId: String,
    val title: String,
    val artist: String,
    val combinedStreams: Long,
    val spotifyStreams: Long,
    val itunesStreams: Long,
    val youtubeMusicStreams: Long
)

/** Legacy wrapper; use [ArtistTrack]. */
data class SongWithStreams(
    val song: Song,
    val streams: SongStreamsBreakdown
)

/**
 * Release item for Albums and Singles/EPs.
 */
data class ArtistAlbumItem(
    val id: String,
    val collectionId: Long,
    val title: String,
    val artist: String,
    val releaseDateFormatted: String,
    val releaseYear: Int?,
    val coverArtUrl: String?,
    val trackCount: Int,
    val isSingleOrEp: Boolean,
    /** ISO `yyyy-MM-dd` when known; used by Latest release and Timeline. */
    val releaseDate: String? = null
)

/**
 * A playlist of real songs shown on the artist page.
 */
data class ArtistPlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverArtUrl: String?,
    val isArtistRadio: Boolean = false,
    val songs: List<Song> = emptyList()
)

/**
 * Related artist for Fans Also Like (and a candidate in "Not this artist?").
 */
data class RelatedArtistItem(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val fanCount: Long = 0L
)

enum class FansFilterType {
    ARTISTS,
    SONGS,
    PLAYLISTS
}

/** Thrown when no catalogue source could be reached at all (usually: offline). */
class ArtistCatalogUnavailableException : Exception("Couldn't reach any music catalogue")

/**
 * Aggregated online profile data for the Artist screen. Every value here comes from a real source
 * (iTunes/Apple Music, Deezer, YouTube Music); nothing is estimated.
 */
data class ArtistCrossPlatformData(
    val artistName: String,
    val effectiveImageUrl: String?,
    val metrics: CrossPlatformArtistMetrics,
    val popularTracks: List<SongWithStreams>,
    val popularReleases: List<ArtistAlbumItem>,
    val singlesAndEPs: List<ArtistAlbumItem>,
    val featuringPlaylists: List<ArtistPlaylistItem>,
    val fansAlsoLikeArtists: List<RelatedArtistItem>,
    val fansAlsoLikeSongs: List<Song>,
    val fansAlsoLikePlaylists: List<ArtistPlaylistItem>,
    /** The whole catalogue, versions folded, most popular first. */
    val tracks: List<ArtistTrack> = emptyList(),
    /** Songs where the artist is featured, or on other artists' releases. */
    val appearsOn: List<ArtistTrack> = emptyList(),
    /** Deezer fans; null when the artist isn't on Deezer. */
    val fanCount: Long? = null,
    val deezerArtistId: Long? = null,
    /** Other Deezer artists with this name, for "Not this artist?". */
    val deezerCandidates: List<RelatedArtistItem> = emptyList(),
    /** Most common catalogue genres, most frequent first. */
    val genres: List<String> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis()
)

@Singleton
class ArtistAggregationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iTunesApiService: ITunesApiService,
    private val deezerApiService: DeezerApiService,
    private val youTubeMusicApiService: YouTubeMusicApiService,
    private val musicRepository: MusicRepository
) {
    // Weight by retained catalog entries; browsing more artists must not retain every discography.
    private val cache = object : android.util.LruCache<String, ArtistCrossPlatformData>(4_000) {
        override fun sizeOf(key: String, value: ArtistCrossPlatformData): Int =
            (value.tracks.size + value.appearsOn.size + value.popularReleases.size + value.singlesAndEPs.size +
                value.fansAlsoLikeSongs.size + value.fansAlsoLikePlaylists.sumOf { it.songs.size }).coerceAtLeast(1)
    }
    private val diskCache = ArtistProfileDiskCache(context)
    private val overrides = context.getSharedPreferences("artist_deezer_overrides", Context.MODE_PRIVATE)

    private fun cacheKey(artistName: String) = artistName.trim().lowercase(Locale.ROOT)

    /** Memory, then disk. The entry says whether it's older than a day. */
    suspend fun getCachedProfile(artistName: String): ArtistProfileDiskCache.Entry? = withContext(Dispatchers.IO) {
        cache.get(cacheKey(artistName))?.let { return@withContext ArtistProfileDiskCache.Entry(it, it.fetchedAt) }
        diskCache.read(artistName)?.also { cache.put(cacheKey(artistName), it.data) }
    }

    /** The Deezer artist the user picked with "Not this artist?", if any. */
    fun deezerOverride(artistName: String): Long? =
        overrides.getLong(cacheKey(artistName), -1L).takeIf { it > 0L }

    fun setDeezerOverride(artistName: String, deezerArtistId: Long?) {
        overrides.edit().apply {
            if (deezerArtistId == null) remove(cacheKey(artistName)) else putLong(cacheKey(artistName), deezerArtistId)
        }.apply()
        cache.remove(cacheKey(artistName))
    }

    /**
     * Builds the artist profile from iTunes, Deezer and YouTube Music. Results are kept in memory
     * and on disk for [ArtistProfileDiskCache.TTL_MS].
     *
     * @param localTitles titles of your own songs by the artist; they break ties when several
     *   Deezer artists share the name (C3).
     * @throws ArtistCatalogUnavailableException when every source failed.
     */
    suspend fun getArtistProfileData(
        artistName: String,
        forceRefresh: Boolean = false,
        localTitles: Set<String> = emptySet()
    ): ArtistCrossPlatformData = withContext(Dispatchers.IO) {
        val key = cacheKey(artistName)
        if (!forceRefresh) {
            cache.get(key)?.takeIf { System.currentTimeMillis() - it.fetchedAt < ArtistProfileDiskCache.TTL_MS }
                ?.let { return@withContext it }
            diskCache.read(artistName)?.takeIf { !it.isStale }?.let {
                cache.put(key, it.data)
                return@withContext it.data
            }
        }

        val profile = fetchProfile(artistName, localTitles, deezerOverride(artistName))
        cache.put(key, profile)
        diskCache.write(artistName, profile)
        profile
    }

    private suspend fun fetchProfile(
        artistName: String,
        localTitles: Set<String>,
        overrideId: Long?
    ): ArtistCrossPlatformData = coroutineScope {
        val failures = java.util.concurrent.atomic.AtomicInteger(0)
        suspend fun <T> attempt(label: String, fallback: T, block: suspend () -> T): T = try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.w(e, "Artist page: %s failed for %s", label, artistName)
            failures.incrementAndGet()
            fallback
        }

        val itunesAlbumsDeferred = async { attempt("iTunes albums", emptyList<ITunesAlbum>()) { iTunesApiService.searchAlbums(artistName, "album", 200).results } }
        val itunesSongsDeferred = async { attempt("iTunes songs", emptyList<ITunesSong>()) { iTunesApiService.searchSongs(artistName, "song", 200).results } }
        val deezerSearchDeferred = async { attempt("Deezer search", emptyList<DeezerArtist>()) { deezerApiService.searchArtist(artistName, 8).data } }
        val ytSongsDeferred = async {
            attempt("YouTube songs", emptyList<Song>()) {
                youTubeMusicApiService.searchSongs("$artistName official audio").getOrThrow()
            }
        }

        val rawItunesAlbums = itunesAlbumsDeferred.await()
        val searchedSongs = itunesSongsDeferred.await()
        val deezerCandidatesRaw = deezerSearchDeferred.await()
        val rawYtSongs = ytSongsDeferred.await()
        if (failures.get() >= 4) throw ArtistCatalogUnavailableException()

        // ── Which Deezer artist is this? (C3) ────────────────────────────────────────────
        val deezerArtist = chooseDeezerArtist(artistName, deezerCandidatesRaw, localTitles, overrideId)
        val deezerCandidates = deezerCandidatesRaw.map { it.toRelatedItem() }

        // ── Full tracklists: iTunes albums + Deezer discography (B2) ─────────────────────
        val filteredItunesAlbums = rawItunesAlbums.filter { isTargetArtist(it.artistName, artistName) }
            .distinctBy { it.collectionId }
        val albumGate = Semaphore(4)
        val itunesAlbumTracksDeferred = filteredItunesAlbums.take(MAX_ITUNES_ALBUM_LOOKUPS).map { album ->
            async {
                albumGate.withPermit {
                    attempt("iTunes album ${album.collectionId}", emptyList<ITunesSong>()) {
                        iTunesApiService.lookupAlbumTracks(album.collectionId).results
                            .filter { it.trackId > 0 && it.trackName != null }
                    }
                }
            }
        }
        val deezerRelatedDeferred = async {
            if (deezerArtist == null) emptyList() else attempt("Deezer related", emptyList<DeezerArtist>()) {
                deezerApiService.getRelatedArtists(deezerArtist.id, 20).data
            }
        }
        val deezerTopDeferred = async {
            if (deezerArtist == null) emptyList() else attempt("Deezer top", emptyList<DeezerTrack>()) {
                deezerApiService.getArtistTopTracks(deezerArtist.id, 100).data
            }
        }
        val deezerAlbums = if (deezerArtist == null) emptyList() else fetchDeezerAlbums(deezerArtist.id)
        val deezerGate = Semaphore(6)
        val deezerAlbumTracks: List<Pair<DeezerAlbum, List<DeezerTrack>>> = deezerAlbums
            .filter { it.recordType != "compile" }
            .take(MAX_DEEZER_ALBUM_LOOKUPS)
            .map { album ->
                async {
                    deezerGate.withPermit {
                        album to attemptQuiet { deezerApiService.getAlbumTracks(album.id).data }.orEmpty()
                    }
                }
            }.awaitAll()

        val rawItunesSongs = (searchedSongs + itunesAlbumTracksDeferred.awaitAll().flatten()).distinctBy { it.trackId }
        val deezerRelated = deezerRelatedDeferred.await()
        val deezerTop = deezerTopDeferred.await()

        // ── Catalogue ──────────────────────────────────────────────────────────────────────
        val itunesAlbumDates = filteredItunesAlbums.associate { it.collectionId to isoDate(it.releaseDate) }
        val ownItunes = rawItunesSongs.filter { isPrimaryArtist(it.artistName.orEmpty(), artistName) }
        val featuredItunes = rawItunesSongs.filter {
            !isPrimaryArtist(it.artistName.orEmpty(), artistName) && isTargetArtist(it.artistName.orEmpty(), artistName)
        }
        val deezerArtistId = deezerArtist?.id
        val ownDeezer: List<Pair<DeezerTrack, DeezerAlbum?>> = deezerAlbumTracks.flatMap { (album, tracks) ->
            tracks.filter { t -> t.artist == null || t.artist.id == deezerArtistId || isPrimaryArtist(t.artist.name, artistName) }
                .map { it to album }
        } + deezerTop.filter { it.artist?.id == deezerArtistId }.map { it to it.album }
        val featuredDeezer = deezerTop.filter { it.artist != null && it.artist.id != deezerArtistId }

        // Popularity comes from Deezer; each title keeps its best rank.
        val rankByExact = HashMap<String, Long>()
        (ownDeezer.map { it.first } + featuredDeezer).forEach { t ->
            val k = ArtistCatalog.exactKey(t.title)
            if (t.rank > (rankByExact[k] ?: 0L)) rankByExact[k] = t.rank
        }
        val dateByExact = HashMap<String, String>()
        fun noteDate(title: String, date: String?) {
            if (date.isNullOrBlank()) return
            val k = ArtistCatalog.exactKey(title)
            val known = dateByExact[k]
            if (known == null || date < known) dateByExact[k] = date
        }
        ownItunes.forEach { noteDate(it.trackName.orEmpty(), isoDate(it.releaseDate) ?: itunesAlbumDates[it.collectionId]) }
        ownDeezer.forEach { (t, album) -> noteDate(t.title, album?.releaseDate) }

        // One copy per recording: iTunes first (Apple ids open in the album page), then Deezer, then YouTube.
        val byExact = LinkedHashMap<String, Song>()
        ownItunes.forEach { s ->
            val song = itunesSong(s, artistName, itunesAlbumDates[s.collectionId])
            byExact.putIfAbsent(ArtistCatalog.exactKey(song.title), song)
        }
        ownDeezer.forEach { (t, album) ->
            byExact.putIfAbsent(ArtistCatalog.exactKey(t.title), deezerSong(t, album, artistName))
        }
        rawYtSongs.filter { isPrimaryArtist(it.artist, artistName) && !isCoverOrUnrelated(it.title) }
            .forEach { byExact.putIfAbsent(ArtistCatalog.exactKey(it.title), it) }

        val maxRank = rankByExact.values.maxOrNull() ?: 0L
        fun toTrack(song: Song): ArtistTrack {
            val k = ArtistCatalog.exactKey(song.title)
            val rank = rankByExact[k]
            return ArtistTrack(
                song = song,
                popularity = ArtistCatalog.popularity(rank, maxRank),
                deezerRank = rank,
                releaseDate = dateByExact[k] ?: song.year.takeIf { it > 0 }?.toString()
            )
        }
        val tracks = ArtistCatalog.sort(ArtistCatalog.groupVersions(byExact.values.map(::toTrack)), ArtistSongSort.POPULAR)
            .take(ArtistCatalog.MAX_TRACKS)

        val ownKeys = tracks.flatMap { t -> listOf(t.song) + t.versions }.mapTo(HashSet()) { ArtistCatalog.exactKey(it.title) }
        val appearsOnSongs = LinkedHashMap<String, Song>()
        featuredItunes.forEach { s ->
            val song = itunesSong(s, s.artistName ?: artistName, isoDate(s.releaseDate))
            val k = ArtistCatalog.exactKey(song.title)
            if (k !in ownKeys) appearsOnSongs.putIfAbsent(k, song)
        }
        featuredDeezer.forEach { t ->
            val k = ArtistCatalog.exactKey(t.title)
            if (k !in ownKeys) appearsOnSongs.putIfAbsent(k, deezerSong(t, t.album, t.artist?.name ?: artistName))
        }
        val appearsOn = ArtistCatalog.sort(appearsOnSongs.values.map(::toTrack), ArtistSongSort.POPULAR).take(MAX_APPEARS_ON)

        // ── Releases ───────────────────────────────────────────────────────────────────────
        val (albums, singlesAndEPs) = processReleases(artistName, filteredItunesAlbums)

        // ── Fans also like: real songs by related artists ────────────────────────────────────
        val fansAlsoLikeArtists = deezerRelated.map { it.toRelatedItem() }
        val relatedTop = deezerRelated.take(RELATED_FOR_SONGS).map { rel ->
            async {
                deezerGate.withPermit {
                    rel to attemptQuiet { deezerApiService.getArtistTopTracks(rel.id, 10).data }.orEmpty()
                        .map { deezerSong(it, it.album, it.artist?.name ?: rel.name) }
                }
            }
        }.awaitAll()
        val fansAlsoLikeSongs = interleave(relatedTop.map { it.second.take(3) }).take(18)
        val fansAlsoLikePlaylists = relatedTop.filter { it.second.isNotEmpty() }.take(RELATED_PLAYLISTS).map { (rel, songs) ->
            ArtistPlaylistItem(
                id = "deezer_top_${rel.id}",
                title = "${rel.name}: top songs",
                subtitle = "${songs.size} songs",
                coverArtUrl = rel.pictureXl ?: rel.pictureBig ?: songs.firstOrNull()?.albumArtUriString,
                songs = songs
            )
        }

        val genres = (ownItunes.mapNotNull { it.primaryGenreName } + filteredItunesAlbums.mapNotNull { it.primaryGenreName })
            .filter { it.isNotBlank() && !it.equals("Music", true) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(4).map { it.key }

        val fans = deezerArtist?.fanCount?.toLong()?.takeIf { it > 0 }
        ArtistCrossPlatformData(
            artistName = artistName,
            effectiveImageUrl = deezerArtist?.pictureXl ?: deezerArtist?.pictureBig,
            metrics = CrossPlatformArtistMetrics(0L, fans ?: 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0, 0.0, 0.0, 0.0),
            popularTracks = tracks.map { SongWithStreams(it.song, emptyBreakdown(it.song)) },
            popularReleases = albums,
            singlesAndEPs = singlesAndEPs,
            featuringPlaylists = emptyList(),
            fansAlsoLikeArtists = fansAlsoLikeArtists,
            fansAlsoLikeSongs = fansAlsoLikeSongs,
            fansAlsoLikePlaylists = fansAlsoLikePlaylists,
            tracks = tracks,
            appearsOn = appearsOn,
            fanCount = fans,
            deezerArtistId = deezerArtist?.id,
            deezerCandidates = deezerCandidates,
            genres = genres
        )
    }

    private suspend fun <T> attemptQuiet(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Timber.w(e, "Artist page request failed")
        null
    }

    private suspend fun fetchDeezerAlbums(artistId: Long): List<DeezerAlbum> {
        val all = mutableListOf<DeezerAlbum>()
        var index = 0
        for (pageNumber in 0 until MAX_DEEZER_ALBUM_PAGES) {
            val page = attemptQuiet { deezerApiService.getArtistAlbums(artistId, 100, index) } ?: break
            all += page.data
            if (page.next.isNullOrBlank() || page.data.isEmpty()) break
            index += page.data.size
        }
        return all.distinctBy { it.id }
    }

    /**
     * Exact name matches win; among several, the one whose top songs overlap most with your own
     * songs, then the one with most fans. A user pick ("Not this artist?") beats everything.
     */
    private suspend fun chooseDeezerArtist(
        artistName: String,
        candidates: List<DeezerArtist>,
        localTitles: Set<String>,
        overrideId: Long?
    ): DeezerArtist? {
        if (overrideId != null) candidates.firstOrNull { it.id == overrideId }?.let { return it }
        if (candidates.isEmpty()) return null
        val target = ArtistCatalog.fold(artistName)
        val exact = candidates.filter { ArtistCatalog.fold(it.name) == target }
        if (exact.isEmpty()) return candidates.first()
        if (exact.size == 1 || localTitles.isEmpty()) return exact.maxByOrNull { it.fanCount }
        val localKeys = localTitles.mapTo(HashSet()) { ArtistCatalog.baseKey(it) }
        val scored = coroutineScope {
            exact.take(3).map { candidate ->
                async {
                    val top = attemptQuiet { deezerApiService.getArtistTopTracks(candidate.id, 25).data }.orEmpty()
                    candidate to top.count { ArtistCatalog.baseKey(it.title) in localKeys }
                }
            }.awaitAll()
        }
        return scored.sortedWith(compareByDescending<Pair<DeezerArtist, Int>> { it.second }.thenByDescending { it.first.fanCount })
            .first().first
    }

    /**
     * Looks up an online album by its iTunes collectionId, returns Album model and tracklist Songs.
     */
    suspend fun getOnlineAlbumWithSongs(
        collectionId: Long,
        fallbackTitle: String? = null,
        fallbackArtist: String? = null
    ): Pair<Album, List<Song>> = withContext(Dispatchers.IO) {
        val tracksResponse = try {
            iTunesApiService.lookupAlbumTracks(collectionId, "song").results
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.w(e, "Failed to lookup album tracks for id=$collectionId")
            emptyList()
        }

        val albumTrack = tracksResponse.firstOrNull { it.wrapperType == "collection" }
        val songTracks = tracksResponse.filter { it.wrapperType == "track" || (it.trackId > 0 && it.trackName != null) }

        val title = albumTrack?.collectionName ?: fallbackTitle ?: "Album"
        val artist = albumTrack?.artistName ?: fallbackArtist ?: "Artist"
        val artworkUrl = albumTrack?.artworkUrl ?: songTracks.firstOrNull()?.artworkUrl
        val releaseYear = parseYear(albumTrack?.releaseDate)

        val album = Album(
            id = collectionId,
            title = title,
            artist = artist,
            year = releaseYear ?: 0,
            dateAdded = 0L,
            albumArtUriString = artworkUrl,
            songCount = songTracks.size.coerceAtLeast(1),
            albumArtist = artist
        )

        val songs = songTracks.mapIndexed { index, track ->
            itunesSong(track, artist, isoDate(albumTrack?.releaseDate)).copy(
                title = track.trackName ?: "Track ${index + 1}",
                album = title,
                albumId = collectionId,
                albumArtUriString = track.artworkUrl ?: artworkUrl,
                trackNumber = track.trackNumber ?: (index + 1),
                year = releaseYear ?: 0
            )
        }

        Pair(album, songs)
    }

    // ── Song builders ───────────────────────────────────────────────────────────────────────

    /**
     * iTunes track ids are Apple Music catalogue ids, so these are `applemusic_` catalogue songs:
     * the full song is matched on YouTube when played or downloaded (never the 30 s preview).
     */
    private fun itunesSong(track: ITunesSong, artistName: String, albumDate: String?): Song {
        val artist = track.artistName ?: artistName
        val date = isoDate(track.releaseDate) ?: albumDate
        return Song(
            id = "applemusic_${track.trackId}",
            title = track.trackName.orEmpty(),
            artist = artist,
            artistId = artistName.hashCode().toLong(),
            artists = listOf(ArtistRef(id = artistName.hashCode().toLong(), name = artistName, isPrimary = true)),
            album = track.collectionName.orEmpty(),
            albumId = track.collectionId ?: 0L,
            albumArtist = artistName,
            path = "",
            contentUriString = "applemusic://${track.trackId}",
            albumArtUriString = track.artworkUrl,
            duration = track.trackTimeMillis ?: 0L,
            genre = track.primaryGenreName,
            trackNumber = track.trackNumber ?: 0,
            year = date?.take(4)?.toIntOrNull() ?: 0,
            downloadState = DownloadState.NOT_DOWNLOADED,
            explicitSource = TrackSource.YOUTUBE_MUSIC
        )
    }

    /** Deezer tracks become `deezer_` catalogue songs, matched on YouTube like Apple ones. */
    private fun deezerSong(track: DeezerTrack, album: DeezerAlbum?, artistName: String): Song = Song(
        id = "deezer_${track.id}",
        title = track.title,
        artist = track.artist?.name?.takeIf { it.isNotBlank() } ?: artistName,
        artistId = artistName.hashCode().toLong(),
        artists = listOf(ArtistRef(id = artistName.hashCode().toLong(), name = artistName, isPrimary = true)),
        album = album?.title.orEmpty(),
        albumId = 0L,
        albumArtist = artistName,
        path = "",
        contentUriString = "deezer://${track.id}",
        albumArtUriString = album?.coverXl ?: album?.coverBig ?: album?.coverMedium,
        duration = track.duration * 1000L,
        trackNumber = track.trackPosition,
        discNumber = track.diskNumber.takeIf { it > 0 },
        year = album?.releaseDate?.take(4)?.toIntOrNull() ?: 0,
        downloadState = DownloadState.NOT_DOWNLOADED,
        explicitSource = TrackSource.YOUTUBE_MUSIC,
        creditsAndRelease = CreditsAndRelease(isrc = track.isrc)
    )

    private fun emptyBreakdown(song: Song) = SongStreamsBreakdown(song.id, song.title, song.artist, 0L, 0L, 0L, 0L)

    private fun DeezerArtist.toRelatedItem() = RelatedArtistItem(
        id = id.toString(),
        name = name,
        imageUrl = pictureXl ?: pictureBig ?: pictureMedium,
        fanCount = fanCount.toLong()
    )

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = mutableListOf<T>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { list -> list.getOrNull(i)?.let(out::add) }
        return out
    }

    // ── Matching ────────────────────────────────────────────────────────────────────────────

    /**
     * Where [target] is credited in [candidate] ("A feat. B", "A & B", "A, B", "A x B"), matched on
     * whole credits so "Tyler, The Creator" and "Lil Nas X" still work. Null when not credited.
     */
    private fun creditPosition(candidate: String, target: String): Int? {
        if (candidate.isBlank() || target.isBlank()) return null
        val c = candidate.trim().lowercase(Locale.ROOT)
        val t = target.trim().lowercase(Locale.ROOT)
        if (c == t) return 0
        val separator = """(?:\s*[,;&/]\s*|\s+(?:feat\.?|ft\.?|featuring|with|x|vs\.?)\s+|\s*[(\[]\s*(?:feat\.?|ft\.?|with)\s+)"""
        val pattern = Regex("(?:^|$separator)${Regex.escape(t)}(?=$|$separator|\\s*[)\\]])")
        return pattern.find(c)?.range?.first
    }

    /** The artist is credited anywhere on the song. */
    private fun isTargetArtist(candidate: String, target: String): Boolean = creditPosition(candidate, target) != null

    /** The artist is the first (main) credit. */
    private fun isPrimaryArtist(candidate: String, target: String): Boolean = creditPosition(candidate, target) == 0

    private fun isCoverOrUnrelated(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("karaoke") || lower.contains("tribute") ||
                lower.contains("cover") || lower.contains("reaction") ||
                lower.contains("instrumental remake") || lower.contains("review")
    }

    private fun processReleases(
        artistName: String,
        albums: List<ITunesAlbum>
    ): Pair<List<ArtistAlbumItem>, List<ArtistAlbumItem>> {
        val studioAlbums = mutableListOf<ArtistAlbumItem>()
        val singlesAndEPs = mutableListOf<ArtistAlbumItem>()

        val distinctAlbums = albums.distinctBy { it.collectionName.trim().lowercase(Locale.ROOT) }

        for (album in distinctAlbums) {
            val title = album.collectionName
            val isSingle = album.collectionType?.contains("single", ignoreCase = true) == true ||
                    album.trackCount <= 3 ||
                    title.contains(" - Single", ignoreCase = true) ||
                    title.contains(" - EP", ignoreCase = true)

            val item = ArtistAlbumItem(
                id = album.collectionId.toString(),
                collectionId = album.collectionId,
                title = title.replace(Regex(""" - (Single|EP)$"""), ""),
                artist = album.artistName.ifBlank { artistName },
                releaseDateFormatted = formatReleaseDate(album.releaseDate),
                releaseYear = parseYear(album.releaseDate),
                coverArtUrl = album.artworkUrl,
                trackCount = album.trackCount,
                isSingleOrEp = isSingle,
                releaseDate = isoDate(album.releaseDate)
            )

            if (isSingle) singlesAndEPs.add(item) else studioAlbums.add(item)
        }

        studioAlbums.sortByDescending { it.releaseDate ?: "" }
        singlesAndEPs.sortByDescending { it.releaseDate ?: "" }

        return Pair(studioAlbums, singlesAndEPs)
    }

    private fun isoDate(date: String?): String? =
        date?.take(10)?.takeIf { it.length >= 4 && it.take(4).all(Char::isDigit) }

    private fun formatReleaseDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(isoDate) ?: return isoDate.take(4)
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
        } catch (e: Exception) {
            isoDate.take(4)
        }
    }

    private fun parseYear(isoDate: String?): Int? {
        if (isoDate.isNullOrBlank()) return null
        return isoDate.take(4).toIntOrNull()
    }

    private companion object {
        const val MAX_ITUNES_ALBUM_LOOKUPS = 40
        const val MAX_DEEZER_ALBUM_PAGES = 3
        const val MAX_DEEZER_ALBUM_LOOKUPS = 80
        const val MAX_APPEARS_ON = 50
        const val RELATED_FOR_SONGS = 6
        const val RELATED_PLAYLISTS = 4
    }
}
