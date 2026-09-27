package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.data.equalizer.GenreEqualizerPresets
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GenreAdaptationTest {
    @Test fun `specific genres win over broad words and aliases are normalized`() {
        assertEquals("genre_deep_house", GenreEqualizerPresets.forGenre("Deep-House / Electronic").name)
        assertEquals("genre_drum_and_bass", GenreEqualizerPresets.forGenre("DnB").name)
        assertEquals("genre_rnb", GenreEqualizerPresets.forGenre("R&B").name)
        assertEquals("genre_k_pop", GenreEqualizerPresets.forGenre("K-Pop").name)
    }
    @Test fun `missing and unrecognized metadata returns flat after a known genre`() {
        assertNotEquals(EqualizerPreset.FLAT, GenreEqualizerPresets.forGenre("Metal"))
        for (tag in listOf(null, "", "Unknown", "Music", "popcorn", "housekeeping")) {
            assertEquals(EqualizerPreset.FLAT, GenreEqualizerPresets.forGenre(tag))
        }
    }
    @Test fun `every genre is resolvable with conservative ten band curves and headroom`() {
        assertTrue(GenreEqualizerPresets.all.size >= 60)
        assertEquals(GenreEqualizerPresets.all.size, GenreEqualizerPresets.all.map { it.name }.distinct().size)
        GenreEqualizerPresets.all.forEach { preset ->
            assertEquals(preset, EqualizerPreset.fromName(preset.name))
            assertEquals(10, preset.bandLevels.size)
            assertTrue(preset.bandLevels.all { it in -4..4 })
            assertTrue(preset.preAmpDb + (preset.bandLevels.maxOrNull() ?: 0) <= 0)
        }
    }
}
