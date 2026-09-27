package com.theveloper.pixelplay.data.spotify.web

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotifyWebParsersTest {

    @Test
    fun `totp matches the known value for the bundled secret`() {
        assertEquals("841929", SpotifyTotp.code(SpotifyTotp.BUNDLED_SECRET, 1_790_000_000L))
        assertEquals(6, SpotifyTotp.code(SpotifyTotp.BUNDLED_SECRET, 0L).length)
    }

    @Test
    fun `bundle secret and hash extraction`() {
        val js = """a={secret:"ab\"c",version:61},b={"version":62,"secret":'xyz'};f("getTrack","query","${"a".repeat(64)}")"""
        val secrets = SpotifyWebBundle.findSecrets(js)
        assertEquals(62 to "xyz", secrets.first())
        assertEquals("ab\"c", secrets[1].second)
        assertEquals("a".repeat(64), SpotifyWebBundle.findQueryHash(js, "getTrack"))
        assertNull(SpotifyWebBundle.findQueryHash(js, "libraryV3"))
    }

    @Test
    fun `library keeps playlists and skips liked songs and folders`() {
        val items = JSONArray("""[
          {"item":{"_uri":"spotify:collection:tracks","data":{"__typename":"PseudoPlaylist","name":"Liked Songs"}}},
          {"item":{"data":{"__typename":"Folder","name":"Stuff"}}},
          {"item":{"data":{"__typename":"Playlist","uri":"spotify:playlist:abc123","name":"Road trip",
            "images":{"items":[{"sources":[{"url":"https://i.scdn.co/big","width":640},{"url":"https://i.scdn.co/mid","width":300}]}]},
            "ownerV2":{"data":{"username":"friend1","name":"Sam"}},"currentUserCapabilities":{"canEditItems":false}}}}
        ]""")
        val list = SpotifyWebParsers.libraryPlaylists(items)
        assertEquals(1, list.size)
        with(list.single()) {
            assertEquals("abc123", id); assertEquals("Road trip", title); assertEquals("https://i.scdn.co/mid", coverUrl)
            assertEquals("friend1", ownerId); assertEquals("Sam", ownerName)
        }
    }

    @Test
    fun `track parsing handles both duration keys and missing tracks`() {
        val t = SpotifyWebParsers.track(JSONObject("""{"__typename":"Track","uri":"spotify:track:T1","name":"Ride",
            "artists":{"items":[{"profile":{"name":"A"}},{"profile":{"name":"B"}}]},
            "albumOfTrack":{"name":"Album","coverArt":{"sources":[{"url":"https://x/300","width":300}]}},
            "duration":{"totalMilliseconds":214506}}"""), "p:0")
        assertEquals("T1", t.id); assertEquals("A, B", t.artistName); assertEquals(214506, t.durationMs)
        val missing = SpotifyWebParsers.track(null, "p:1")
        assertTrue(missing.id.startsWith("unavailable:"))
    }

    @Test
    fun `live feed parsing keeps the newest activity per user`() {
        val json = JSONObject("""{"entities":[
          {"userEntity":{"uri":"spotify:user:u1","activity":{"entityUri":"spotify:track:old","timestamp":"2026-09-23T08:00:00Z","isPlaying":false}}},
          {"followEntity":{"uri":"spotify:user:u1","activity":{"entityUri":"spotify:track:new","timestamp":"2026-09-23T08:05:00Z","isPlaying":true,"contextUri":"spotify:playlist:p"}}},
          {"userEntity":{"uri":"spotify:artist:x","activity":{"entityUri":"spotify:track:z","timestamp":"2026-09-23T08:05:00Z"}}}
        ]}""")
        val friends = SpotifySocialParsers.feed(json)
        assertEquals(1, friends.size)
        val a = friends.single().activity!!
        assertEquals("spotify:track:new", a.trackUri); assertTrue(a.isPlaying); assertTrue(a.fromLiveFeed)
    }

    @Test
    fun `buddy list and profile parsing`() {
        val buddy = SpotifySocialParsers.buddyList(JSONObject("""{"friends":[{"timestamp":1790000000000,
            "user":{"uri":"spotify:user:u2","name":"Jo","imageUrl":"https://img"},
            "track":{"uri":"spotify:track:t","name":"Song","artist":{"name":"Band"},"album":{"name":"LP"},"context":{"uri":"spotify:playlist:p","name":"Mix"}}}]}"""))
        assertEquals("Jo", buddy.single().name); assertEquals("Band", buddy.single().activity!!.artist)
        val profile = SpotifySocialParsers.profile("u2", JSONObject("""{"name":"Jo","followers_count":3,
            "public_playlists":[{"uri":"spotify:playlist:pp1","name":"Jo's mix","image_url":"https://c","owner_uri":"spotify:user:u2","owner_name":"Jo"}]}"""))
        assertEquals(listOf("pp1"), profile.playlists.map { it.id })
        val following = SpotifySocialParsers.following(JSONObject("""{"profiles":[{"uri":"spotify:user:u3","name":"Al"},{"uri":"spotify:artist:a","name":"Band"}]}"""))
        assertEquals(listOf("u3"), following.map { it.userId })
    }

    @Test
    fun `sp_dc parsing accepts cookie strings`() {
        val v = "A".repeat(60)
        assertEquals(v, SpotifyWebSession.parseSpDc("sp_t=1; sp_dc=$v; x=2"))
        assertEquals(v, SpotifyWebSession.parseSpDc(" $v "))
    }
}
