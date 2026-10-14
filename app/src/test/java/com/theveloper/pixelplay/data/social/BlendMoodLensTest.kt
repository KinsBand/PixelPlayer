package com.theveloper.pixelplay.data.social

import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards mood blends: picking a vibe on a blend page must mix a new blend from everyone's songs
 * that fit the mood, including friends' songs (which have no genre or audio features).
 */
class BlendMoodLensTest {
    private fun song(id: String, artist: String, genre: String? = null) =
        Song.emptySong().copy(id = id, title = id, artist = artist, genre = genre)

    // Stand-in for MusicVibeFilters.score: "chill" fits, "loud" is rated and doesn't, no genre = unknown (0.0).
    private val fit: (Song) -> Double = { s ->
        when (s.genre) {
            "chill" -> 3.0
            "loud" -> -1.0
            else -> 0.0
        }
    }

    private val library = listOf(
        song("c1", "Calm", "chill"), song("c2", "Calm", "chill"), song("c3", "Calm", "chill"), song("c4", "Calm", "loud"),
        song("n1", "Noise", "loud"), song("n2", "Noise", "loud"), song("n3", "Noise", "chill"),
        song("x1", "Thin"), // no metadata: must not count against anyone
    )
    private val lens = BlendMixGenerator.moodLens(library, threshold = 2.0, fit = fit)

    @Test fun `friend song with no metadata fits through an artist that is mostly that mood in your library`() {
        assertTrue(lens.fits(song("spotify-1", "Calm")))
        assertFalse(lens.fits(song("spotify-2", "Noise")))
        assertFalse(lens.fits(song("spotify-3", "Unknown artist")))
    }

    @Test fun `your rated copy of the same song decides, even for a mostly-matching artist`() {
        // Same title + artist as c4 (rated loud): a friend's copy must not sneak in via the artist.
        assertFalse(lens.fits(song("c4", "Calm")))
        // Same as n3 (rated chill): fits even though Noise is mostly loud.
        assertTrue(lens.fits(song("n3", "Noise")))
    }

    @Test fun `unrated library songs don't drag an artist's share down`() {
        val thinLens = BlendMixGenerator.moodLens(
            listOf(song("a1", "Mixed", "chill")) + (1..5).map { song("t$it", "Mixed") },
            threshold = 2.0, fit = fit
        )
        assertTrue(thinLens.fits(song("friend", "Mixed")))
    }

    @Test fun `your side is topped up from your library when you've played few songs in the mood`() {
        val mine = listOf(song("c1", "Calm", "chill"), song("n1", "Noise", "loud"))
        val side = BlendMixGenerator.mineForMood(mine, lens, size = 3)
        assertEquals("c1", side.first().id)
        assertFalse(side.any { it.id == "n1" })
        assertEquals(3, side.size)
        assertEquals(side.size, side.distinctBy { it.id }.size)
    }

    @Test fun `a mood blend is re-mixed from both sides, not today's blend filtered`() {
        val mine = BlendMixGenerator.mineForMood((1..12).map { song("m$it", "MyChill$it", "chill") }, lens)
        val friendAll = (1..12).map { song("f$it", "Calm") } + (1..12).map { song("g$it", "Noise") }
        val blend = BlendMixGenerator.build("SPOTIFY:jo", "Jo", null, mine, friendAll.filter(lens::fits), day = 2026284)
        assertTrue(blend.songs.any { it.id.startsWith("f") }, "friend's mood songs missing")
        assertTrue(blend.songs.any { it.id.startsWith("m") }, "your mood songs missing")
        assertFalse(blend.songs.any { it.id.startsWith("g") || it.id == "n1" }, "off-mood song in the mood blend")
    }
}
