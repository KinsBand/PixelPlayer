package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Guards the artist page's All songs sorts: direction, unknown values last, Album grouping. */
class ArtistCatalogSortTest {
    private fun track(
        title: String,
        popularity: Int? = null,
        date: String? = null,
        album: String = "",
        cover: String? = null,
        trackNo: Int = 0
    ) = ArtistTrack(
        song = Song.emptySong().copy(id = title, title = title, album = album, albumArtUriString = cover, trackNumber = trackNo),
        popularity = popularity,
        releaseDate = date
    )

    private fun titles(list: List<ArtistTrack>) = list.map { it.song.title }

    @Test fun `popular keeps unknown popularity last in both directions`() {
        val tracks = listOf(track("a", 80), track("b", null), track("c", 20))
        assertEquals(listOf("a", "c", "b"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.POPULAR)))
        assertEquals(listOf("c", "a", "b"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.POPULAR, reversed = true)))
    }

    @Test fun `newest flips but undated stays last`() {
        val tracks = listOf(track("old", date = "2001-01-01"), track("none"), track("new", date = "2024-05-01"))
        assertEquals(listOf("new", "old", "none"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.NEWEST)))
        assertEquals(listOf("old", "new", "none"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.NEWEST, reversed = true)))
    }

    @Test fun `a to z reverses`() {
        val tracks = listOf(track("b"), track("a"), track("c"))
        assertEquals(listOf("a", "b", "c"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.A_Z)))
        assertEquals(listOf("c", "b", "a"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.A_Z, reversed = true)))
    }

    @Test fun `album groups by cover, newest album first, track order inside`() {
        val tracks = listOf(
            track("b1", date = "2019-01-01", album = "B", cover = "https://x/b.jpg"),
            track("a2", date = "2024-01-01", album = "A", cover = "https://x/a.jpg", trackNo = 2),
            track("c1", album = "C"),
            track("loose"),
            track("a1", date = "2024-01-01", album = "A Deluxe", cover = "https://x/a.jpg", trackNo = 1),
        )
        assertEquals(listOf("a1", "a2", "b1", "c1", "loose"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.ALBUM)))
        // Reversed flips dated albums only; undated and ungrouped stay last; track order kept.
        assertEquals(listOf("b1", "a1", "a2", "c1", "loose"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.ALBUM, reversed = true)))
    }

    @Test fun `cover size variants share one group`() {
        val a = track("x", cover = "https://e-cdns-images.dzcdn.net/images/cover/abc/250x250-000000-80-0-0.jpg")
        val b = track("y", cover = "https://e-cdns-images.dzcdn.net/images/cover/abc/1000x1000-000000-80-0-0.jpg")
        assertEquals(ArtistCatalog.albumKey(a), ArtistCatalog.albumKey(b))
        // A path whose last segment is an id (not a size) keeps it.
        val c = track("z", cover = "https://i.scdn.co/image/111")
        val d = track("w", cover = "https://i.scdn.co/image/222")
        assert(ArtistCatalog.albumKey(c) != ArtistCatalog.albumKey(d))
    }

    @Test fun `plays sort is most played first`() {
        val tracks = listOf(track("a"), track("b"))
        val plays = mapOf(ArtistCatalog.baseKey("b") to 5, ArtistCatalog.baseKey("a") to 1)
        assertEquals(listOf("b", "a"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.YOUR_PLAYS, plays)))
        assertEquals(listOf("a", "b"), titles(ArtistCatalog.sort(tracks, ArtistSongSort.YOUR_PLAYS, plays, reversed = true)))
    }
}
