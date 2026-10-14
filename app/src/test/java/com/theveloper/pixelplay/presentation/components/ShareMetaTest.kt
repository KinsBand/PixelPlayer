package com.theveloper.pixelplay.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The share sheet must never offer a blank or placeholder album / artist / genre. */
class ShareMetaTest {

    @Test
    fun `blank and null values are hidden`() {
        assertNull(shareMetaOrNull(null))
        assertNull(shareMetaOrNull(""))
        assertNull(shareMetaOrNull("   "))
    }

    @Test
    fun `placeholders from online sources are hidden whatever the case`() {
        assertNull(shareMetaOrNull("<unknown>"))
        assertNull(shareMetaOrNull("Unknown Artist"))
        assertNull(shareMetaOrNull("YouTube Music"))
        assertNull(shareMetaOrNull("youtube"))
        assertNull(shareMetaOrNull("UNKNOWN"))
    }

    @Test
    fun `real values are kept and trimmed`() {
        assertEquals("Random Access Memories", shareMetaOrNull("  Random Access Memories "))
        assertEquals("Daft Punk", shareMetaOrNull("Daft Punk"))
        // A word that merely contains a placeholder is a real value.
        assertEquals("Unknown Mortal Orchestra", shareMetaOrNull("Unknown Mortal Orchestra"))
    }
}
