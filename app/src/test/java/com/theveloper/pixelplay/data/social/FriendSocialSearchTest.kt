package com.theveloper.pixelplay.data.social

import com.theveloper.pixelplay.data.spotify.web.SpotifySocialParsers
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Profile links in search, and Spotify's people search results. */
class FriendSocialSearchTest {
    @Test fun `profile links and URIs give the user id`() {
        assertEquals("abc123", FriendSocialGraph.profileIdIn("https://open.spotify.com/user/abc123?si=xyz"))
        assertEquals("abc123", FriendSocialGraph.profileIdIn("https://open.spotify.com/intl-de/user/abc123"))
        assertEquals("abc123", FriendSocialGraph.profileIdIn("spotify:user:abc123"))
    }

    @Test fun `plain names are a search, not a link`() {
        assertNull(FriendSocialGraph.profileIdIn("jane doe"))
    }

    @Test fun `people search keeps users and reads paging`() {
        val data = JSONObject("""{"searchV2":{"users":{"totalCount":3,"items":[
            {"data":{"__typename":"User","uri":"spotify:user:jane","displayName":"Jane","avatar":{"sources":[{"url":"a.jpg"}]}}},
            {"data":{"__typename":"User","uri":"spotify:user:joe","displayName":""}}
        ]}}}""")
        val page = SpotifySocialParsers.userSearch(data, offset = 0)
        assertEquals(listOf("jane", "joe"), page.people.map { it.userId })
        assertEquals("Jane", page.people[0].name)
        assertEquals("a.jpg", page.people[0].avatarUrl)
        assertEquals("joe", page.people[1].name) // blank name falls back to the id
        assertTrue(page.hasMore)
    }

    @Test fun `last page has no more`() {
        val data = JSONObject("""{"searchV2":{"users":{"totalCount":1,"items":[
            {"data":{"__typename":"User","uri":"spotify:user:jane","displayName":"Jane"}}
        ]}}}""")
        assertFalse(SpotifySocialParsers.userSearch(data, offset = 0).hasMore)
    }
}
