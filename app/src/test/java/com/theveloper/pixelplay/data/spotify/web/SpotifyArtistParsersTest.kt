package com.theveloper.pixelplay.data.spotify.web

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the defensive parsing of Spotify's artist search / artist page. The fixtures are
 * hand-written to the expected shape (not captured responses), so this checks robustness:
 * missing blocks give empty lists, bad items are skipped, exact-name matching only.
 */
class SpotifyArtistParsersTest {
    @Test fun `exact name match only, accents ignored`() {
        val data = JSONObject("""{"searchV2":{"artists":{"items":[
            {"data":{"uri":"spotify:artist:AAA","profile":{"name":"Beyonce Tribute"}}},
            {"data":{"uri":"spotify:artist:BBB","profile":{"name":"Beyoncé"}}}]}}}""")
        assertEquals("BBB", SpotifyArtistParsers.artistIdFromSearch(data, "beyonce"))
        assertNull(SpotifyArtistParsers.artistIdFromSearch(data, "Someone Else"))
        assertNull(SpotifyArtistParsers.artistIdFromSearch(null, "x"))
    }

    @Test fun `missing artistUnion gives empty extras`() {
        val extras = SpotifyArtistParsers.extras(JSONObject("{}"), "id", 5L)
        assertTrue(extras.playlists.isEmpty() && extras.concerts.isEmpty() && extras.merch.isEmpty() && extras.relatedArtists.isEmpty())
        assertEquals(5L, extras.fetchedAt)
    }

    @Test fun `bad items are skipped, playlists deduped`() {
        val data = JSONObject("""{"artistUnion":{
            "profile":{"playlistsV2":{"items":[{"data":{"uri":"spotify:playlist:P1","name":"Mine"}},{"data":{"uri":"bad","name":"X"}}]}},
            "relatedContent":{
              "featuringV2":{"items":[{"data":{"uri":"spotify:playlist:P1","name":"Mine again"}},{"data":{"uri":"spotify:playlist:P2","name":"This Is"}}]},
              "relatedArtists":{"items":[{"id":"R1","profile":{"name":"Rel"}},{"id":"R2","profile":{}}]}},
            "goods":{
              "events":{"concerts":{"items":[
                {"uri":"spotify:concert:C1","date":{"isoString":"2026-10-13T20:00:00-04:00"},"venue":"Arena","location":{"city":"Orlando","coordinates":{"latitude":28.5,"longitude":-81.3}}},
                {"uri":"spotify:concert:C2","date":{"isoString":"soon"},"location":{"city":"Nowhere"}}]}},
              "merch":{"items":[{"name":"Vinyl","url":"https://shop.example/v","uri":"m1"},{"name":"No link"}]}}}}""")
        val extras = SpotifyArtistParsers.extras(data, "A", 0L)
        assertEquals(listOf("P1", "P2"), extras.playlists.map { it.id })
        assertEquals(listOf("R1"), extras.relatedArtists.map { it.id })
        val show = extras.concerts.single()
        assertEquals("2026-10-13", show.date)
        assertEquals("20:00", show.time)
        assertEquals("Orlando", show.city)
        assertEquals(28.5, show.latitude)
        assertEquals(listOf("Vinyl"), extras.merch.map { it.name })
    }
}
