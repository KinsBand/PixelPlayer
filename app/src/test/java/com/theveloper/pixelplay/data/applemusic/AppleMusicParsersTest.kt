package com.theveloper.pixelplay.data.applemusic

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppleMusicParsersTest {

    @Test
    fun `library song prefers catalog id and isrc`() {
        val item = JSONObject("""{"id":"i.lib1","type":"library-songs",
          "attributes":{"name":"Song","artistName":"Artist","albumName":"Album","durationInMillis":200000,
            "artwork":{"url":"https://a/{w}x{h}bb.jpg"},"playParams":{"id":"i.lib1","catalogId":"123"}},
          "relationships":{"catalog":{"data":[{"id":"456","attributes":{"isrc":"USABC1234567"}}]}}}""")
        val t = AppleMusicParsers.track(item, "p:0")
        assertEquals("456", t.id); assertEquals("USABC1234567", t.isrc); assertEquals("https://a/600x600bb.jpg", t.coverUrl)
        assertEquals("applemusic_456", t.toSong().id)
    }

    @Test
    fun `uploaded songs without catalog still map`() {
        val t = AppleMusicParsers.track(JSONObject("""{"id":"i.up","attributes":{"name":"Demo","artistName":"Me"}}"""), "p:1")
        assertEquals("i.up", t.id); assertEquals(null, t.isrc)
    }

    @Test
    fun `playlists and favourites detection`() {
        val p = AppleMusicParsers.playlist(JSONObject("""{"id":"p.abc","attributes":{"name":"Chill","canEdit":true,"playParams":{"globalId":"pl.u-xyz"}}}"""))!!
        assertEquals("pl.u-xyz", p.catalogId); assertTrue(p.canEdit)
        assertTrue(AppleMusicParsers.isFavoritesPlaylist("Favourite Songs"))
    }

    @Test
    fun `user token parsing`() {
        val v = "B".repeat(80)
        assertEquals(v, AppleMusicWebSession.parseUserToken("itspod=1; media-user-token=$v"))
    }
}
