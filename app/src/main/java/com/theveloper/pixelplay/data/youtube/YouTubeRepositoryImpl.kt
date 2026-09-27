package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchResultItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepositoryImpl @Inject constructor(
    private val apiService: YouTubeMusicApiService,
    private val streamExtractor: YouTubeStreamExtractor,
    private val innerTube: InnerTubeClient
) : YouTubeRepository {
    override suspend fun searchLyricCandidates(query: String): List<Song> =
        apiService.searchSongs("$query lyrics", broad = true).getOrThrow()
    override fun cachedSongs(query: String): List<Song> = apiService.cachedSongs(query)
    override fun cachedItems(query: String, filter: SearchFilterType): List<SearchResultItem> =
        innerTube.cached(query, filter).ifEmpty { super.cachedItems(query, filter) }

    override suspend fun searchItems(query: String, filter: SearchFilterType): List<SearchResultItem> {
        suspend fun extracted(): List<SearchResultItem> {
            if (filter != SearchFilterType.ALL && filter != SearchFilterType.SONGS)
                return apiService.searchCollections(query, filter)
            val music = apiService.searchSongs(query)
            return music.getOrNull().orEmpty().ifEmpty {
                apiService.searchSongs(query, broad = true).getOrThrow()
            }.map { SearchResultItem.SongItem(it) }
        }
        val direct = hedgedLookup(
            primary = { withTimeoutOrNull(3_000) { innerTube.search(query, filter) }.orEmpty() },
            fallback = { withTimeoutOrNull(12_000) { extracted() }.orEmpty() }
        )
        val songs = direct.filterIsInstance<SearchResultItem.SongItem>().map { it.song }
        val hydrated = apiService.hydrate(songs).associateBy { it.id }
        val items = direct.map { if (it is SearchResultItem.SongItem) it.copy(song = hydrated[it.song.id] ?: it.song) else it }
        if (filter == SearchFilterType.ALL && songs.isEmpty() && items.isNotEmpty()) {
            // Preserve collection matches even if song extraction is unavailable.
            val fallback = try { withTimeoutOrNull(12_000) { extracted() }.orEmpty() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Timber.w(e, "Supplementary song search unavailable"); emptyList() }
            return (items + fallback).distinctBy { it.searchIdentity() }
        }
        return items
    }
    override suspend fun relatedSongs(videoId: String): List<Song> = streamExtractor.relatedSongs(videoId)
        .filter { item ->
            val artist = item.uploaderName.orEmpty()
            val title = item.name.orEmpty()
            item.duration in 30..900 && (artist.endsWith(" - Topic") || artist.endsWith("VEVO", true) ||
                title.contains("official audio", true) || title.contains("official music", true))
        }.mapNotNull(apiService::mapToSong)

    override suspend fun searchSongs(query: String): List<Song> {
        return searchItems(query, SearchFilterType.SONGS).filterIsInstance<SearchResultItem.SongItem>().map { it.song }
    }

    override suspend fun resolveStreamUrl(videoId: String): String? {
        return streamExtractor.getStreamUrl(videoId)
    }
}
