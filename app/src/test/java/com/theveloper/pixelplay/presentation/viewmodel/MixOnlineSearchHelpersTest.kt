package com.theveloper.pixelplay.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixOnlineSearchHelpersTest {
    @Test fun `normalizing ignores case, accents and extra spaces`() {
        assertEquals("beyonce knowles", normalizeMixQuery("  Beyoncé   KNOWLES "))
    }

    @Test fun `online results already listed locally are dropped`() {
        val merged = mergeLocalAndOnline(
            local = listOf("Beyoncé", "Adele"),
            online = listOf(OnlineFilterSuggestion("BEYONCE"), OnlineFilterSuggestion("Björk"), OnlineFilterSuggestion("bjork"))
        )
        assertEquals(listOf("Björk"), merged.map { it.value })
    }

    @Test fun `custom row only for custom fields with new text`() {
        assertTrue(shouldOfferCustom(true, "rainy day", listOf("Happy")))
        assertFalse(shouldOfferCustom(true, "  happy ", listOf("Happy")))
        assertFalse(shouldOfferCustom(true, "   ", listOf("Happy")))
        assertFalse(shouldOfferCustom(false, "rainy day", emptyList()))
    }
}
