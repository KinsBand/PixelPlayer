package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Custom blend titles from friends' initials, and blend page ids read back correctly. */
class BlendTitlesTest {
    @Test fun `no friends keeps the generic name`() {
        assertEquals(CUSTOM_BLEND_NAME, customBlendTitleFromNames(emptyList()))
    }

    @Test fun `one friend uses their name`() {
        assertEquals("Jack", customBlendTitleFromNames(listOf("Jack")))
    }

    @Test fun `several friends use their initials`() {
        assertEquals("J · M · K", customBlendTitleFromNames(listOf("jack", "Mia", " kai")))
    }

    @Test fun `initials skip leading symbols and cap at five`() {
        assertEquals("A · B · C · D · E +2",
            customBlendTitleFromNames(listOf("@ann", "Ben", "Cy", "Di", "Ed", "Flo", "Gus")))
    }

    @Test fun `friends mix page ids read back as Friends Mix`() {
        assertEquals(BlendPageId.FriendsMix, parseBlendPageId(friendsMixPlaylistId(2026284)))
    }

    @Test fun `friend blend ids keep friend ids with colons`() {
        val id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}blend:SPOTIFY:user123:2026283"
        assertEquals(BlendPageId.Friend("SPOTIFY:user123"), parseBlendPageId(id))
    }

    @Test fun `custom blend ids are recognised`() {
        assertEquals(BlendPageId.Custom, parseBlendPageId("${PlaylistViewModel.GENERATED_MIX_PREFIX}blend:custom:12345:2026283"))
    }

    @Test fun `other playlists are not blends`() {
        assertNull(parseBlendPageId("local_42"))
        assertNull(parseBlendPageId("${PlaylistViewModel.GENERATED_MIX_PREFIX}daily:1"))
        assertNull(parseBlendPageId(null))
    }
}
