package com.theveloper.pixelplay.data.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioModelsTest {
    private fun station(
        id: String,
        name: String = id,
        lat: Double? = null,
        lon: Double? = null,
        state: String? = null,
        clicks: Int = 0,
    ) = RadioStation(uuid = id, name = name, streamUrl = "https://example.com/$id", lat = lat, lon = lon, state = state, clickCount = clicks)

    @Test fun `parses FM frequency from station names`() {
        assertEquals(RadioFrequency(RadioBand.FM, 104.5), RadioFrequency.parse("Triple M 104.5"))
        assertEquals(RadioFrequency(RadioBand.FM, 88.1), RadioFrequency.parse("Radio 88,1 FM"))
        assertEquals(RadioFrequency(RadioBand.FM, 102.1), RadioFrequency.parse("4ZZZ 102.1"))
    }

    @Test fun `parses AM only when marked as AM`() {
        assertEquals(RadioFrequency(RadioBand.AM, 612.0), RadioFrequency.parse("ABC Brisbane 612 AM"))
        assertEquals(RadioFrequency(RadioBand.AM, 1116.0), RadioFrequency.parse("SEN AM1116"))
        assertNull(RadioFrequency.parse("Hits 1999"))
    }

    @Test fun `ignores numbers that are not broadcast frequencies`() {
        assertNull(RadioFrequency.parse("Radio 2000"))
        assertNull(RadioFrequency.parse("Top 40 Hits"))
        assertNull(RadioFrequency.parse("Version 1.2.3"))
        assertNull(RadioFrequency.parse("Hits 123.4"))
    }

    @Test fun `distance between Brisbane and Sydney is about 730 km`() {
        val km = RadioGeo.distanceKm(-27.47, 153.03, -33.87, 151.21)
        assertTrue(km in 700.0..760.0, "was $km")
    }

    @Test fun `local keeps nearby stations nearest first and widens when sparse`() {
        val home = RadioHome(countryCode = "AU", state = "Queensland", lat = -27.47, lon = 153.03)
        val near = station("near", lat = -27.50, lon = 153.00)
        val mid = station("mid", lat = -27.00, lon = 153.10)
        val gold = station("gold", lat = -28.00, lon = 153.43) // ~70 km
        val sydney = station("syd", lat = -33.87, lon = 151.21)
        val noGeo = station("nogeo", state = "Queensland")
        val result = RadioScopeFilter.local(listOf(sydney, gold, mid, near, noGeo), home, minResults = 3)
        assertEquals(listOf("near", "mid", "gold", "nogeo"), result.map { it.uuid })
    }

    @Test fun `local is empty without coordinates`() {
        val home = RadioHome(countryCode = "AU")
        assertTrue(RadioScopeFilter.local(listOf(station("a", lat = 1.0, lon = 1.0)), home).isEmpty())
    }

    @Test fun `FM dial places stations at their frequency and seeks in order`() {
        val a = station("a", name = "Alpha 88.0")
        val b = station("b", name = "Bravo 98.0")
        val c = station("c", name = "Charlie 107.9")
        val web = station("web", name = "Internet Only")
        val dial = DialLayout.build(RadioBand.FM, listOf(c, web, a, b))
        assertEquals(listOf("a", "b", "c"), dial.stops.map { it.station.uuid })
        assertEquals(((98.0 - 87.5) / (108.0 - 87.5)).toFloat(), dial.stops[1].position, 1e-4f)
        assertEquals("b", dial.next(dial.stops[0].position)?.station?.uuid)
        assertEquals("a", dial.previous(dial.stops[1].position)?.station?.uuid)
        // Wraps around like a real seek.
        assertEquals("a", dial.next(dial.stops[2].position)?.station?.uuid)
        assertEquals("98.0", dial.readout(dial.stops[1].position))
    }

    @Test fun `web dial orders west to east and labels groups`() {
        val ny = station("ny", lat = 40.7, lon = -74.0)
        val london = station("ldn", lat = 51.5, lon = -0.1)
        val tokyo = station("tyo", lat = 35.7, lon = 139.7)
        val dial = DialLayout.build(RadioBand.WEB, listOf(tokyo, london, ny)) { s ->
            RadioGeo.continentOf(s.lat!!, s.lon!!)
        }
        assertEquals(listOf("ny", "ldn", "tyo"), dial.stops.map { it.station.uuid })
        assertEquals(0f, dial.stops.first().position)
        assertEquals(1f, dial.stops.last().position)
        assertEquals(listOf("N. America", "Europe", "Asia"), dial.marks.map { it.second })
    }

    @Test fun `continents are roughly right`() {
        assertEquals("Oceania", RadioGeo.continentOf(-27.5, 153.0))
        assertEquals("Oceania", RadioGeo.continentOf(-41.3, 174.8))
        assertEquals("Europe", RadioGeo.continentOf(48.9, 2.35))
        assertEquals("Africa", RadioGeo.continentOf(-1.3, 36.8))
        assertEquals("Asia", RadioGeo.continentOf(28.6, 77.2))
        assertEquals("S. America", RadioGeo.continentOf(-23.5, -46.6))
        assertEquals("N. America", RadioGeo.continentOf(19.4, -99.1))
    }

    @Test fun `clustering merges points in the same cell and sorts by popularity`() {
        val a = station("a", lat = 0.0, lon = 0.0, clicks = 1)
        val b = station("b", lat = 0.0, lon = 0.1, clicks = 50)
        val far = station("far", lat = 10.0, lon = 10.0)
        val clusters = RadioMapMath.cluster(listOf(a, b, far), cellPx = 40f) { s ->
            (s.lon!!.toFloat() * 100f + 5f) to (-s.lat!!.toFloat() * 100f + 5f)
        }
        assertEquals(2, clusters.size)
        val big = clusters.first { it.stations.size == 2 }
        assertEquals(listOf("b", "a"), big.stations.map { it.uuid })
    }

    @Test fun `bounds trim outliers`() {
        val stations = (0 until 40).map { station("s$it", lat = -27.0 - it * 0.01, lon = 153.0 + it * 0.01) } +
            station("stray", lat = 51.5, lon = -0.1)
        val b = RadioMapMath.bounds(stations)
        assertNotNull(b)
        assertTrue(b!![2] < 0, "north edge should ignore the stray London station, was ${b[2]}")
    }
}
