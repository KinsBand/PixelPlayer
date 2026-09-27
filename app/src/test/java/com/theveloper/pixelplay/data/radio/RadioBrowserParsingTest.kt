package com.theveloper.pixelplay.data.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioBrowserParsingTest {
    private val json = """
        [
          {"stationuuid":"u1","name":"4ZZZ 102.1","url":"http://a/pls","url_resolved":"http://a/stream",
           "favicon":"https://a/icon.png","tags":"community,indie","country":"Australia","countrycode":"AU",
           "state":"Queensland","codec":"MP3","bitrate":128,"hls":0,"clickcount":40,"geo_lat":-27.46,"geo_long":153.02},
          {"stationuuid":"u2","name":"4zzz 102.1 ","url":"http://b","url_resolved":"","countrycode":"AU","geo_lat":null,"geo_long":null},
          {"stationuuid":"u3","name":"HLS Station","url":"https://c/live.m3u8","hls":1,"favicon":"","geo_lat":0,"geo_long":0},
          {"stationuuid":"","name":"No id","url":"http://d"},
          {"stationuuid":"u5","name":"   ","url":"http://e"}
        ]
    """.trimIndent()

    @Test fun `parses stations, drops unusable and duplicate entries`() {
        val stations = RadioBrowserRepository.parseStations(json)
        assertEquals(listOf("u1", "u3"), stations.map { it.uuid })
        val zzz = stations[0]
        assertEquals("http://a/stream", zzz.streamUrl)
        assertEquals(listOf("community", "indie"), zzz.tags)
        assertEquals(-27.46, zzz.lat!!, 1e-9)
        assertEquals(RadioFrequency(RadioBand.FM, 102.1), zzz.frequency)
        val hls = stations[1]
        assertTrue(hls.isHls)
        assertEquals("https://c/live.m3u8", hls.streamUrl)
        assertNull(hls.favicon)
        assertNull(hls.lat, "0,0 means unknown")
    }

    @Test fun `station survives a round trip through preset storage`() {
        val original = RadioBrowserRepository.parseStations(json).first()
        val restored = RadioBrowserRepository.parseStation(RadioBrowserRepository.toJson(original))
        assertEquals(original, restored)
    }
}
