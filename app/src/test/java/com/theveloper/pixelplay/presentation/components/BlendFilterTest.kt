package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.data.social.BlendMixGenerator
import com.theveloper.pixelplay.data.social.FriendPresence
import com.theveloper.pixelplay.presentation.viewmodel.FriendUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Guards the blend contributor chips: who shows, in what order, and that shared songs aren't lost. */
class BlendFilterTest {
    private fun friend(id: String, name: String) =
        FriendUi(id, name, name, "SPOTIFY", null, FriendPresence.OFFLINE, null, emptyList())

    @Test fun `one-on-one blend offers You and the friend`() {
        val chips = blendFilterChips(
            mapOf("a" to BlendMixGenerator.ME, "b" to "SPOTIFY:jo", "c" to BlendMixGenerator.BOTH),
            listOf(friend("SPOTIFY:jo", "Jo")), "You"
        )
        assertEquals(listOf(BlendMixGenerator.ME to "You", "SPOTIFY:jo" to "Jo"), chips)
    }

    @Test fun `friends follow the friends list order`() {
        val chips = blendFilterChips(
            mapOf("a" to "SPOTIFY:b", "b" to "SPOTIFY:a"),
            listOf(friend("SPOTIFY:a", "Amy"), friend("SPOTIFY:b", "Ben")), "You"
        )
        assertEquals(listOf("SPOTIFY:a" to "Amy", "SPOTIFY:b" to "Ben"), chips)
    }

    @Test fun `no contributors means no chips`() {
        assertTrue(blendFilterChips(emptyMap(), emptyList(), "You").isEmpty())
    }

    @Test fun `shared songs show under both people`() {
        assertTrue(blendSongMatches(BlendMixGenerator.BOTH, BlendMixGenerator.ME))
        assertTrue(blendSongMatches(BlendMixGenerator.BOTH, "SPOTIFY:jo"))
        assertTrue(blendSongMatches("SPOTIFY:jo", "SPOTIFY:jo"))
        assertFalse(blendSongMatches("SPOTIFY:jo", BlendMixGenerator.ME))
        assertFalse(blendSongMatches(null, BlendMixGenerator.ME))
    }

    @Test fun `multi-person filtering matches any selected contributor`() {
        val selected = setOf(BlendMixGenerator.ME, "SPOTIFY:jo")
        assertTrue(blendSongMatchesAny(BlendMixGenerator.ME, selected))
        assertTrue(blendSongMatchesAny("SPOTIFY:jo", selected))
        assertTrue(blendSongMatchesAny(BlendMixGenerator.BOTH, selected))
        assertFalse(blendSongMatchesAny("SPOTIFY:other", selected))
        assertFalse(blendSongMatchesAny(null, selected))
    }

    @Test fun `empty filter matches all contributors`() {
        assertTrue(blendSongMatchesAny(BlendMixGenerator.ME, emptySet()))
        assertTrue(blendSongMatchesAny("SPOTIFY:jo", emptySet()))
        assertTrue(blendSongMatchesAny(BlendMixGenerator.BOTH, emptySet()))
        assertTrue(blendSongMatchesAny(null, emptySet()))
    }
}
