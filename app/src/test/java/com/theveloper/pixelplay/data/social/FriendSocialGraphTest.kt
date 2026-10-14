package com.theveloper.pixelplay.data.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Guards the mutual "Friends" badge: only people who follow you AND you follow get it. */
class FriendSocialGraphTest {
    @Test fun `mutual needs both directions`() {
        val mutual = FriendSocialGraph.mutualIds(following = setOf("a", "b", "c"), followers = setOf("b", "c", "d"))
        assertEquals(setOf("SPOTIFY:b", "SPOTIFY:c"), mutual)
    }

    @Test fun `one-way follows are not mutual`() {
        assertEquals(emptySet<String>(), FriendSocialGraph.mutualIds(setOf("a"), setOf("b")))
    }

    @Test fun `only Spotify friend ids map to a user id`() {
        assertEquals("jo", FriendSocialGraph.spotifyUserId("SPOTIFY:jo"))
        assertNull(FriendSocialGraph.spotifyUserId("YOUTUBE_MUSIC:UC123"))
        assertNull(FriendSocialGraph.spotifyUserId("SPOTIFY:"))
    }
}
