package com.theveloper.pixelplay.data.social

import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Guards the 1-on-1 blend's per-song attribution (drives the blend page's contributor filter). */
class BlendMixContributorsTest {
    private fun song(id: String, artist: String) = Song.emptySong().copy(id = id, title = id, artist = artist)

    @Test fun `every song in the blend has a side`() {
        val mine = (1..20).map { song("m$it", "MyArtist$it") } + song("shared", "Both")
        val theirs = (1..20).map { song("f$it", "TheirArtist$it") } + song("shared", "Both")
        val blend = BlendMixGenerator.build("SPOTIFY:jo", "Jo", null, mine, theirs, day = 2026283)
        assertTrue(blend.songs.isNotEmpty())
        blend.songs.forEach { assertTrue(it.id in blend.contributors, "no side for ${it.id}") }
        assertEquals(BlendMixGenerator.BOTH, blend.contributors["shared"])
        blend.songs.filter { it.id.startsWith("m") }.forEach { assertEquals(BlendMixGenerator.ME, blend.contributors[it.id]) }
        blend.songs.filter { it.id.startsWith("f") }.forEach { assertEquals("SPOTIFY:jo", blend.contributors[it.id]) }
    }
}
