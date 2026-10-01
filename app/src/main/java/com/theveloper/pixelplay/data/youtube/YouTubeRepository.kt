package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchResultItem

interface YouTubeRepository {
    suspend fun searchLyricCandidates(query: String): List<Song> = emptyList()
    fun cachedItems(query: String, filter: SearchFilterType): List<SearchResultItem> =
        if (filter == SearchFilterType.ALL || filter == SearchFilterType.SONGS)
            cachedSongs(query).map { SearchResultItem.SongItem(it) } else emptyList()
    suspend fun searchItems(query: String, filter: SearchFilterType): List<SearchResultItem> =
        if (filter == SearchFilterType.ALL || filter == SearchFilterType.SONGS)
            searchSongs(query).map { SearchResultItem.SongItem(it) } else emptyList()
    fun cachedSongs(query: String): List<Song> = emptyList()
    suspend fun relatedSongs(videoId: String): List<Song> = emptyList()
    suspend fun searchSongs(query: String): List<Song>
    /** Next page of song results for "Show more"; pair.second = another page exists. */
    suspend fun moreSongs(query: String, reset: Boolean = false): Pair<List<Song>, Boolean> = emptyList<Song>() to false
    suspend fun resolveStreamUrl(videoId: String): String?
}
