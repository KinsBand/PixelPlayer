package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.MixIntelligence
import com.theveloper.pixelplay.data.model.MusicalFeatures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MixVibeTest {
    private fun song(id: String) = Song.emptySong().copy(id = id, title = id, artist = id)
    @Test fun `close energy and mood beat an abrupt jump with otherwise equal taste`() {
        val calm = song("seed").copy(mixIntelligence = MixIntelligence(energy = 0.1f, valence = 0.3f, mood = "calm"))
        val fit = song("fit").copy(mixIntelligence = calm.mixIntelligence)
        val jump = song("jump").copy(mixIntelligence = MixIntelligence(energy = 0.95f, valence = 0.9f))
        assertTrue(MixVibe.components(calm, fit).values.sum() > MixVibe.components(calm, jump).values.sum())
        val result = MixSequencePlanner.plan(listOf(jump, fit), listOf(calm), emptySet(), emptySet(), emptyList(), "smart", 0.0, 1000, limit = 1)
        assertEquals("fit", result.single().song.id)
    }
    @Test fun `missing and nonfinite features are neutral`() {
        val invalid = song("bad").copy(mixIntelligence = MixIntelligence(energy = Float.NaN, valence = 2f), musicalFeatures = MusicalFeatures(bpm = Float.POSITIVE_INFINITY))
        assertTrue(MixVibe.components(invalid, song("plain")).values.all { it == 0.0 })
    }
    @Test fun `half time tempo remains compatible`() {
        val a = song("a").copy(musicalFeatures = MusicalFeatures(bpm = 80f))
        val b = song("b").copy(musicalFeatures = MusicalFeatures(bpm = 160f))
        assertEquals(0.0, MixVibe.components(a, b).getValue("tempoCompatibility"), 0.00001)
    }
    @Test fun `recent direction outweighs older unrelated seeds`() {
        val old = song("old").copy(genre = "metal")
        val recent = song("recent").copy(genre = "ambient")
        assertTrue(MixVibe.sessionFit(song("a").copy(genre = "ambient"), listOf(old, recent)) >
            MixVibe.sessionFit(song("b").copy(genre = "metal"), listOf(old, recent)))
    }
}
