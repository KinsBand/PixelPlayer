package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.model.MixIntelligence
import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchDiscoveryTest {

    private fun song(genre: String? = null, year: Int = 0, energy: Float? = null, valence: Float? = null) =
        Song.emptySong().copy(
            genre = genre,
            year = year,
            mixIntelligence = MixIntelligence(energy = energy, valence = valence)
        )

    @Test fun `profile splits multi-genre tags, drops junk, the artist's own name and decade tags`() {
        val songs = listOf(
            song("Post-Punk/New Wave", 1985),
            song("Post-Punk", 1982),
            song("The Cure", 1989),
            song("Seen Live", 1987)
        )
        val profile = SearchDiscovery.soundProfile(songs, extraTags = listOf("80s", "gothic"), artistName = "The Cure")
        // Last.fm's top tag weighs most, then your songs' own genres.
        assertEquals(listOf("gothic", "post-punk", "new wave"), profile.genres)
        assertFalse(profile.genres.any { it == "the cure" || it == "seen live" || it == "80s" })
        assertEquals(1980, profile.decade)
    }

    @Test fun `mood comes from analysed features when no song has a mood tag`() {
        val sad = List(3) { song("indie", 2005, energy = 0.3f, valence = 0.2f) }
        assertEquals("melancholic", SearchDiscovery.featureMood(sad))
        assertEquals("melancholic", SearchDiscovery.soundProfile(sad).moods.first())
        val happy = List(3) { song("pop", 2015, energy = 0.8f, valence = 0.8f) }
        assertEquals("upbeat", SearchDiscovery.featureMood(happy))
        // Fewer than three analysed songs: no guess.
        assertNull(SearchDiscovery.featureMood(sad.take(2)))
    }

    @Test fun `mood searches describe the sound and never name the artist`() {
        val profile = SearchDiscovery.SoundProfile(
            genres = listOf("post-punk", "new wave", "the cure tribute"),
            moods = listOf("melancholic"),
            decade = 1980
        )
        val queries = SearchDiscovery.moodQueries(profile, "The Cure")
        assertEquals(listOf("melancholic post-punk", "80s post-punk", "melancholic new wave"), queries)
    }

    @Test fun `without genres the mood searches use the mood alone`() {
        val profile = SearchDiscovery.SoundProfile(genres = emptyList(), moods = listOf("dreamy"), decade = 2010)
        assertEquals(listOf("dreamy songs", "2010s dreamy songs"), SearchDiscovery.moodQueries(profile, "Beach House"))
        assertTrue(SearchDiscovery.moodQueries(SearchDiscovery.SoundProfile(emptyList(), emptyList(), null), "X").isEmpty())
    }

    @Test fun `decade labels`() {
        assertEquals("80s", SearchDiscovery.decadeLabel(1980))
        assertEquals("2010s", SearchDiscovery.decadeLabel(2010))
    }

    @Test fun `anchors walk through the top artists, two per rotation`() {
        val pool = listOf("a", "b", "c", "d", "e", "f")
        assertEquals(listOf("a", "b"), SearchDiscovery.anchorsFor(pool, slot = 0))
        assertEquals(listOf("b", "c"), SearchDiscovery.anchorsFor(pool, slot = 1))
        assertEquals(listOf("f", "a"), SearchDiscovery.anchorsFor(pool, slot = 5))
        assertEquals(listOf("a", "b"), SearchDiscovery.anchorsFor(listOf("a", "b"), slot = 7))
    }

    @Test fun `rotation slot changes at each period boundary`() {
        val period = SearchDiscovery.ROTATION_PERIOD_MS
        assertEquals(SearchDiscovery.rotationSlot(0), SearchDiscovery.rotationSlot(period - 1))
        assertEquals(SearchDiscovery.rotationSlot(0) + 1, SearchDiscovery.rotationSlot(period))
        assertEquals(period, SearchDiscovery.msUntilNextSlot(0))
    }

    private val similar = listOf("Joy Division", "Siouxsie and the Banshees", "The Smiths", "Echo & the Bunnymen",
        "New Order", "Bauhaus", "Depeche Mode", "Interpol", "Editors", "She Wants Revenge").map { SimilarArtist(it) }

    @Test fun `similar artists come first, then mood searches, skipping excluded artists`() {
        val tiles = SearchDiscovery.buildSuggestions(
            artistName = "The Cure", artistId = 1, similar = similar,
            moodQueries = listOf("melancholic post-punk", "80s post-punk"),
            slot = 0, total = 3, exclude = setOf("Joy Division", "The Smiths", "Bauhaus", "Interpol", "Editors")
        )
        assertEquals(3, tiles.size)
        assertEquals(listOf(HubSuggestionKind.ARTIST, HubSuggestionKind.ARTIST, HubSuggestionKind.MOOD), tiles.map { it.kind })
        assertTrue(tiles.none { it.query in setOf("Joy Division", "The Smiths", "Bauhaus", "Interpol", "Editors", "The Cure") })
    }

    @Test fun `same rotation gives the same tiles, other rotations reshuffle`() {
        fun tiles(slot: Long) = SearchDiscovery.buildSuggestions("The Cure", 1, similar, listOf("melancholic post-punk", "80s post-punk"), slot, 3)
        assertEquals(tiles(4), tiles(4))
        val distinct = (0L until 10L).map { tiles(it).map { t -> t.query } }.toSet()
        assertTrue(distinct.size > 1)
    }

    @Test fun `missing artists or moods are filled by the other kind`() {
        val onlyMoods = SearchDiscovery.buildSuggestions("X", 1, emptyList(), listOf("a", "b", "c", "d"), 0, 3)
        assertEquals(List(3) { HubSuggestionKind.MOOD }, onlyMoods.map { it.kind })
        val onlyArtists = SearchDiscovery.buildSuggestions("X", 1, similar, emptyList(), 0, 4)
        assertEquals(List(4) { HubSuggestionKind.ARTIST }, onlyArtists.map { it.kind })
    }
}
