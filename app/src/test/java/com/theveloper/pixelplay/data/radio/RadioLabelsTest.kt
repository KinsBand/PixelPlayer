package com.theveloper.pixelplay.data.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioLabelsTest {
    private fun station(id: String, state: String? = null, cc: String? = "AU", iso: String? = null, tags: List<String> = emptyList()) =
        RadioStation(uuid = id, name = id, streamUrl = "https://example.com/$id", state = state, countryCode = cc, subdivisionCode = iso, tags = tags)

    @Test fun `australian states shorten`() {
        assertEquals("NSW", RadioLabels.shortState("New South Wales", "AU"))
        assertEquals("VIC", RadioLabels.shortState("Victoria", "AU"))
        assertEquals("ACT", RadioLabels.shortState("Australian Capital Territory", "AU"))
        assertEquals("QLD", RadioLabels.shortState("queensland", "AU"))
        assertEquals("CA", RadioLabels.shortState("California", "US"))
        // Unknown names stay as they are.
        assertEquals("Bavaria", RadioLabels.shortState("Bavaria", "DE"))
    }

    @Test fun `every spelling of a state is the same state`() {
        val home = RadioHome(countryCode = "AU", state = "New South Wales")
        listOf(
            station("a", state = "New South Wales"),
            station("b", state = "NSW"),
            station("c", state = "nsw"),
            station("d", state = "Sydney"),
            station("e", state = "AU-NSW"),
            station("f", iso = "AU-NSW"),
            station("g", state = "Newcastle, NSW"),
            station("h", state = "New South Wales", cc = null),
        ).forEach { assertTrue(RadioLabels.inSameState(it, home), "${it.uuid} should be in NSW") }
        listOf(
            station("x", state = "Victoria"),
            station("y", state = "Melbourne"),
            station("z", state = "New South Wales", cc = "US"),
            station("w"),
        ).forEach { assertFalse(RadioLabels.inSameState(it, home), "${it.uuid} should not be in NSW") }
    }

    @Test fun `tags shorten to chip size`() {
        assertEquals("Comm", RadioLabels.shortTag("community radio"))
        assertEquals("Comm", RadioLabels.shortTag("Community"))
        assertEquals("Talk", RadioLabels.shortTag("News/Talk"))
        assertEquals("News", RadioLabels.shortTag("news"))
        assertEquals("Rock", RadioLabels.shortTag("Classic Rock"))
        assertEquals("ABC", RadioLabels.shortTag("abc"))
        assertEquals("Dance", RadioLabels.shortTag("dance"))
        assertEquals("R&B", RadioLabels.shortTag("R&B"))
        listOf("community radio", "adult contemporary", "electronic", "university radio", "multicultural", "something very long indeed")
            .forEach { assertTrue(RadioLabels.shortTag(it).length <= 6, "$it -> ${RadioLabels.shortTag(it)}") }
    }

    @Test fun `subtitle uses the short forms`() {
        val s = station("s", state = "New South Wales", tags = listOf("community radio", "news"))
        assertEquals("NSW · Comm", s.subtitle)
    }

    @Test fun `region collects every spelling and unlabelled stations nearby`() {
        val home = RadioHome(countryCode = "AU", state = "New South Wales", lat = -33.87, lon = 151.21)
        val country = listOf(
            station("full", state = "New South Wales"),
            station("abbr", state = "NSW"),
            station("city", state = "Sydney"),
            station("vic", state = "Victoria"),
        )
        val nearby = listOf(
            RadioStation(uuid = "nolabel", name = "n", streamUrl = "u", countryCode = "AU", lat = -33.9, lon = 151.0),
            RadioStation(uuid = "far", name = "f", streamUrl = "u", countryCode = "AU", lat = -12.4, lon = 130.8),
        )
        val ids = RadioScopeFilter.region(country, home, nearby).map { it.uuid }.toSet()
        assertEquals(setOf("full", "abbr", "city", "nolabel"), ids)
    }

    @Test fun `local finds stations without coordinates that name the town`() {
        val home = RadioHome(countryCode = "AU", state = "New South Wales", city = "Wollongong", lat = -34.42, lon = 150.89)
        val stations = listOf(
            RadioStation(uuid = "geo", name = "Near", streamUrl = "u", lat = -34.43, lon = 150.88),
            RadioStation(uuid = "town", name = "Wollongong Community FM", streamUrl = "u", countryCode = "AU"),
            RadioStation(uuid = "state", name = "Other", streamUrl = "u", countryCode = "AU", state = "NSW"),
        )
        assertEquals(listOf("geo", "town", "state"), RadioScopeFilter.local(stations, home, minResults = 1).map { it.uuid })
    }

    @Test fun `stream titles split into title and artist and skip junk`() {
        assertEquals("Wonderwall" to "Oasis", RadioNowPlaying.parseStreamTitle("Oasis - Wonderwall", "Triple M"))
        assertEquals("Song" to "Artist", RadioNowPlaying.parseStreamTitle("'Artist - Song (2019)'", null))
        assertNull(RadioNowPlaying.parseStreamTitle("Triple M", "Triple M"))
        assertNull(RadioNowPlaying.parseStreamTitle("Commercial break - back soon", null))
        assertNull(RadioNowPlaying.parseStreamTitle("https://station.example", null))
        assertNull(RadioNowPlaying.parseStreamTitle("Breakfast with Kate", null))
        assertEquals("Stormy Weather" to "Etta James", RadioNowPlaying.parseStreamTitle("Etta James - Stormy Weather", null))
    }
}
