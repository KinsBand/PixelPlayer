package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchVersionGroupingTest {

    private fun song(id: String, title: String, artist: String) =
        Song.emptySong().copy(id = id, title = title, artist = artist)

    private fun item(id: String, title: String, artist: String) =
        SearchResultItem.SongItem(song(id, title, artist))

    private fun songs(results: List<SearchResultItem>) = results.filterIsInstance<SearchResultItem.SongItem>()

    @Test fun `query naming the artist folds live, remaster and other artists' covers under the original`() {
        val results = listOf(
            item("a", "Lovesong", "The Cure"),
            item("b", "Lovesong (Live)", "The Cure"),
            item("c", "Lovesong", "Adele"),
            item("d", "Lovesong - 2010 Remaster", "The Cure"),
            item("e", "Lovesong", "311"),
            item("f", "Pictures of You", "The Cure")
        )
        val grouped = songs(SearchVersionGrouping.group(results, "The Cure lovesong"))
        assertEquals(listOf("a", "f"), grouped.map { it.song.id })
        val lovesong = grouped.first()
        assertTrue(lovesong.isOfficialArtist)
        // The Cure's own cuts first (search order), then covers (search order).
        assertEquals(listOf("b", "d", "c", "e"), lovesong.versions.map { it.id })
        assertTrue(grouped[1].versions.isEmpty())
    }

    @Test fun `same title by different artists stays apart when the query names no artist`() {
        val results = listOf(item("a", "Hello", "Adele"), item("b", "Hello", "Lionel Richie"))
        val grouped = songs(SearchVersionGrouping.group(results, "hello"))
        assertEquals(listOf("a", "b"), grouped.map { it.song.id })
        assertTrue(grouped.all { it.versions.isEmpty() })
        assertFalse(grouped.first().isOfficialArtist)
    }

    @Test fun `karaoke and cover uploads fold under the original even without an artist in the query`() {
        val results = listOf(
            item("a", "Hello", "Adele"),
            item("k", "Hello (Karaoke Version)", "Sing King"),
            item("c", "Hello (Adele cover)", "Some Singer")
        )
        val grouped = songs(SearchVersionGrouping.group(results, "hello"))
        assertEquals(listOf("a"), grouped.map { it.song.id })
        assertEquals(listOf("k", "c"), grouped.first().versions.map { it.id })
    }

    @Test fun `the original is preferred over an earlier live cut`() {
        val results = listOf(
            item("live", "Creep (Live at Glastonbury)", "Radiohead"),
            item("orig", "Creep", "Radiohead")
        )
        val grouped = songs(SearchVersionGrouping.group(results, "creep"))
        assertEquals("orig", grouped.single().song.id)
        assertEquals(listOf("live"), grouped.single().versions.map { it.id })
    }

    @Test fun `video titles with the artist prefix and channel suffix join the same family`() {
        val results = listOf(
            item("v", "The Cure - Lovesong (Official Video)", "TheCureVEVO"),
            item("a", "Lovesong", "The Cure")
        )
        val grouped = songs(SearchVersionGrouping.group(results, "lovesong"))
        assertEquals(1, grouped.size)
        assertEquals(setOf("v", "a"), (listOf(grouped.single().song) + grouped.single().versions).map { it.id }.toSet())
    }

    @Test fun `non-song results keep their places and a family sits where its first member was`() {
        val artist = SearchResultItem.ArtistItem(Artist(1L, "The Cure", 0))
        val results = listOf(
            item("x", "Friday I'm in Love", "The Cure"),
            artist,
            item("a", "Lovesong", "The Cure"),
            item("b", "Lovesong (Acoustic)", "The Cure")
        )
        val grouped = SearchVersionGrouping.group(results, "the cure")
        assertEquals(3, grouped.size)
        assertEquals("x", (grouped[0] as SearchResultItem.SongItem).song.id)
        assertEquals(artist, grouped[1])
        assertEquals(listOf("b"), (grouped[2] as SearchResultItem.SongItem).versions.map { it.id })
    }

    @Test fun `badges name the version kind and covers`() {
        val original = song("a", "Lovesong", "The Cure")
        assertEquals(
            SearchVersionGrouping.VersionKind.LIVE,
            SearchVersionGrouping.versionBadge(song("b", "Lovesong (Live)", "The Cure"), original)?.kind
        )
        assertEquals(
            SearchVersionGrouping.VersionKind.REMASTERED,
            SearchVersionGrouping.versionBadge(song("c", "Lovesong - 2010 Remaster", "The Cure"), original)?.kind
        )
        assertEquals(
            SearchVersionGrouping.VersionKind.COVER,
            SearchVersionGrouping.versionBadge(song("d", "Lovesong", "Adele"), original)?.kind
        )
        assertNull(SearchVersionGrouping.versionBadge(song("e", "Lovesong", "The Cure"), original))
    }

    @Test fun `a single song or no songs comes back unchanged`() {
        val one = listOf(item("a", "Lovesong", "The Cure"))
        assertEquals(one, SearchVersionGrouping.group(one, "lovesong"))
        assertEquals(emptyList<SearchResultItem>(), SearchVersionGrouping.group(emptyList(), "x"))
    }
}
