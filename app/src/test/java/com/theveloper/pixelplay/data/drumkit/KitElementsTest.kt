package com.theveloper.pixelplay.data.drumkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KitElementsTest {

    private val gm = DrumMap(KitProfiles.GM)

    @Test
    fun `a snare that sends 38 and 39 is one card, not two`() {
        val groups = KitLayoutRules.groupPads(listOf(38, 39, 40), gm)
        val snare = groups.single { it.title == "Snare" }
        assertEquals(listOf(38, 39, 40), snare.entries.map { it.note }.sorted())
        assertEquals(1, groups.count { it.title == "Snare" })
    }

    @Test
    fun `all toms share one card, highest first`() {
        val groups = KitLayoutRules.groupPads(listOf(43, 45, 47, 48), gm)
        val toms = groups.single { it.title == "Toms" }
        assertEquals(listOf(48, 47, 45, 43), toms.entries.map { it.note })
    }

    @Test
    fun `ignored and unknown pads go last`() {
        val map = gm.withManual(42, null)
        val groups = KitLayoutRules.groupPads(listOf(36, 42, 99), map)
        assertEquals(KitLayoutRules.UNMAPPED_TITLE, groups.last().title)
        assertEquals(listOf(42, 99), groups.last().entries.map { it.note })
    }

    @Test
    fun `optional parts are only drawn when the kit has them`() {
        val dtx = DrumMap(KitProfiles.YAMAHA_DTX400)
        val parts = KitLayoutRules.visibleElements(dtx, listOf(36, 38))
        assertFalse(KitElement.CHINA in parts)
        assertFalse(KitElement.TOM_3 in parts)
        assertTrue(KitElement.CRASH_2 in parts) // in the DTX400 profile
        assertTrue(KitElement.SNARE in parts)
    }

    @Test
    fun `a hit lights the part its piece belongs to`() {
        assertEquals(KitElement.SNARE, KitElement.of(DrumPiece.SNARE_RIM))
        assertEquals(KitElement.HIHAT, KitElement.of(DrumPiece.HH_OPEN))
        assertEquals(KitElement.RIDE, KitElement.of(DrumPiece.RIDE_BELL))
        assertEquals(null, KitElement.of(DrumPiece.OTHER))
    }
}
