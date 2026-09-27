package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.model.*
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HybridYouTubeRepositoryTest {
    private val api = mockk<YouTubeMusicApiService>()
    private val inner = mockk<InnerTubeClient>()
    private val repository = YouTubeRepositoryImpl(api, mockk(), inner)
    private val song = Song.emptySong().copy(id = "yt_abcdefghijk", title = "Track")
    init { coEvery { api.hydrate(any()) } answers { firstArg() } }

    @Test fun `direct music results are hydrated without extra extractor calls`() = runTest {
        coEvery { inner.search("track", SearchFilterType.SONGS) } returns listOf(SearchResultItem.SongItem(song))
        coEvery { api.hydrate(listOf(song)) } returns listOf(song.copy(isFavorite = true))
        assertTrue(repository.searchSongs("track").single().isFavorite)
        coVerify(exactly = 0) { api.searchSongs(any(), any()) }
    }
    @Test fun `music failure falls through to broad public search`() = runTest {
        coEvery { inner.search(any(), any()) } throws java.io.IOException("unavailable")
        coEvery { api.hydrate(emptyList()) } returns emptyList()
        coEvery { api.searchSongs("snippet", false) } returns Result.failure(java.io.IOException("music unavailable"))
        coEvery { api.searchSongs("snippet", true) } returns Result.success(listOf(song))
        assertEquals(listOf(song), repository.searchSongs("snippet"))
    }
    @Test fun `cancellation never triggers a fallback request`() = runTest {
        coEvery { inner.search(any(), any()) } throws CancellationException("new query")
        try { repository.searchSongs("old"); fail<Unit>("Expected cancellation") } catch (_: CancellationException) { }
        coVerify(exactly = 0) { api.searchSongs(any(), any()) }
    }
    @Test fun `typed collections use extractor fallback without leaking songs`() = runTest {
        val artist = SearchResultItem.ArtistItem(Artist(-1, "Artist", 0), "UCartist")
        coEvery { inner.search(any(), SearchFilterType.ARTISTS) } returns emptyList()
        coEvery { api.hydrate(emptyList()) } returns emptyList()
        coEvery { api.searchCollections("artist", SearchFilterType.ARTISTS) } returns listOf(artist)
        assertEquals(listOf(artist), repository.searchItems("artist", SearchFilterType.ARTISTS))
        coVerify(exactly = 0) { api.searchSongs(any(), any()) }
    }
}
