package com.theveloper.pixelplay.data.accounts

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.spotify.SpotifyToYouTubeResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CatalogPlaybackResolverTest {
    private val matcher = mockk<SpotifyToYouTubeResolver>()
    private val resolver = CatalogPlaybackResolver(matcher)

    private fun spotifySong(id: String) = Song.emptySong().copy(
        id = "spotify_$id", title = "Title $id", artist = "Artist", duration = 200_000,
        contentUriString = "spotify://$id"
    )

    @Test fun `registered songs are matched once with their metadata`() = runBlocking {
        coEvery { matcher.resolveSpotifyTrackToVideoId("abc", "Title abc", "Artist", 200_000, null) } returns "dQw4w9WgXcQ"
        resolver.register(listOf(spotifySong("abc")))

        assertEquals("dQw4w9WgXcQ", resolver.videoIdFor("spotify://abc"))
        assertEquals("dQw4w9WgXcQ", resolver.videoIdFor("spotify://abc"))
        coVerify(exactly = 1) { matcher.resolveSpotifyTrackToVideoId(any(), any(), any(), any(), any()) }
    }

    @Test fun `an unregistered uri can only use a stored match`() = runBlocking {
        coEvery { matcher.cachedVideoId("xyz", null) } returns "yt_aaaaaaaaaaa"
        coEvery { matcher.cachedVideoId("am:1", null) } returns null

        assertEquals("aaaaaaaaaaa", resolver.videoIdFor("spotify://xyz"))
        assertNull(resolver.videoIdFor("applemusic://1"))
        coVerify(exactly = 0) { matcher.resolveSpotifyTrackToVideoId(any(), any(), any(), any(), any()) }
    }

    @Test fun `songs that already carry a match need no lookup`() = runBlocking {
        resolver.register(listOf(spotifySong("abc").copy(youtubeId = "bbbbbbbbbbb")))
        assertEquals("bbbbbbbbbbb", resolver.videoIdFor("spotify://abc"))
    }

    @Test fun `catalog uris map to the repositories' song ids`() {
        assertEquals("spotify_abc", CatalogPlaybackResolver.songIdForUri("spotify://abc"))
        assertEquals("applemusic_123", CatalogPlaybackResolver.songIdForUri("applemusic://123"))
        assertEquals("deezer_9", CatalogPlaybackResolver.songIdForUri("deezer://9"))
        assertNull(CatalogPlaybackResolver.songIdForUri("youtube://dQw4w9WgXcQ"))
        assertNull(CatalogPlaybackResolver.songIdForUri("spotify://"))
    }
}
