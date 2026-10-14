package com.theveloper.pixelplay.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Guards the ASCII fast path added for list performance: output must match the old behaviour. */
class NormalizeMetadataTextTest {

    @Test fun `plain ascii is only trimmed and not copied`() {
        val title = "Lovesong"
        assertSame(title, title.normalizeMetadataText())
        assertEquals("Lovesong", "  Lovesong \n".normalizeMetadataText())
    }

    @Test fun `null and blank`() {
        assertNull((null as String?).normalizeMetadataText())
        assertEquals("", "   ".normalizeMetadataText())
        assertEquals("", (null as String?).normalizeMetadataTextOrEmpty())
    }

    @Test fun `nul characters are still removed`() {
        assertEquals("AB", "A\u0000B".normalizeMetadataText())
    }

    @Test fun `mojibake is still repaired`() {
        assertEquals("Café", "CafÃ©".normalizeMetadataText())
    }

    @Test fun `decomposed accents are still composed to NFC`() {
        assertEquals("Beyoncé", "Beyoncé".normalizeMetadataText())
        assertEquals("Beyoncé", "Beyoncé".normalizeMetadataText())
    }
}
