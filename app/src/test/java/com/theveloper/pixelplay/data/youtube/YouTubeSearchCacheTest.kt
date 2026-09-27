package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.FavoritesDao
import io.ktor.client.HttpClient
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.search.SearchInfo

class YouTubeSearchCacheTest {
    @AfterEach
    fun cleanup() = unmockkStatic(SearchInfo::class)

    private fun service() = YouTubeMusicApiService(
        mockk<HttpClient>(),
        mockk<FavoritesDao> { coEvery { getFavoriteSongIdsOnce() } returns emptyList() },
        mockk<CloudSongDao> { coEvery { getAllOnce() } returns emptyList() },
    )

    @Test
    fun `repeated normalized search uses cache`() = runTest {
        mockkStatic(SearchInfo::class)
        val info = mockk<SearchInfo> { every { relatedItems } returns emptyList() }
        every { SearchInfo.getInfo(any(), any<org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler>()) } returns info
        val api = service()
        assertTrue(api.searchSongs("hello").isSuccess)
        assertTrue(api.searchSongs(" HELLO ").isSuccess)
        verify(exactly = 1) { SearchInfo.getInfo(any(), any<org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler>()) }
    }

    @Test
    fun `result hydration queries only returned IDs and exposes memory results`() = runTest {
        mockkStatic(SearchInfo::class)
        val item = mockk<org.schabi.newpipe.extractor.stream.StreamInfoItem> {
            every { url } returns "https://www.youtube.com/watch?v=abcdefghijk"
            every { name } returns "A song"
            every { uploaderName } returns "Artist - Topic"
            every { thumbnails } returns emptyList()
            every { duration } returns 180
        }
        val info = mockk<SearchInfo> { every { relatedItems } returns listOf(item) }
        every { SearchInfo.getInfo(any(), any<org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler>()) } returns info
        val favorites = mockk<FavoritesDao> { coEvery { getFavoriteIdsAmong(listOf("yt_abcdefghijk")) } returns listOf("yt_abcdefghijk") }
        val cloud = mockk<CloudSongDao> { coEvery { getByIds(listOf("yt_abcdefghijk")) } returns emptyList() }
        val api = YouTubeMusicApiService(mockk<HttpClient>(), favorites, cloud)
        assertTrue(api.searchSongs("a song").getOrThrow().single().isFavorite)
        assertEquals("yt_abcdefghijk", api.cachedSongs(" A SONG ").single().id)
        assertNull(api.cachedSongs("a song").single().bitrate)
        assertNull(api.cachedSongs("a song").single().sampleRate)
        val timings = LongArray(1_000) {
            val started = System.nanoTime()
            api.cachedSongs("a song")
            System.nanoTime() - started
        }.sorted()
        println("Memory search lookup (mocked catalog): p50=${timings[500] / 1_000_000.0}ms p95=${timings[950] / 1_000_000.0}ms")
        verify(exactly = 1) { SearchInfo.getInfo(any(), any<org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler>()) }
        coVerify(exactly = 0) { cloud.getAllOnce() }
        coVerify(exactly = 0) { favorites.getFavoriteSongIdsOnce() }
    }

    @Test
    fun `cancelled search is propagated and never cached`() = runTest {
        mockkStatic(SearchInfo::class)
        every { SearchInfo.getInfo(any(), any<org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler>()) } throws CancellationException("superseded")
        val api = service()
        repeat(2) {
            try {
                api.searchSongs("hello")
                fail<Unit>("Cancellation should propagate")
            } catch (_: CancellationException) {
                // Expected: a cancelled query must not become an empty cached success.
            }
        }
        verify(exactly = 2) { SearchInfo.getInfo(any(), any<org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler>()) }
    }
}
