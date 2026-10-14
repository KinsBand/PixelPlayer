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

    @Test fun `continents are roughly right`() {
        assertEquals("Oceania", RadioGeo.continentOf(-27.5, 153.0))
        assertEquals("Oceania", RadioGeo.continentOf(-41.3, 174.8))
        assertEquals("Europe", RadioGeo.continentOf(48.9, 2.35))
        assertEquals("Africa", RadioGeo.continentOf(-1.3, 36.8))
        assertEquals("Asia", RadioGeo.continentOf(28.6, 77.2))
        assertEquals("S. America", RadioGeo.continentOf(-23.5, -46.6))
        assertEquals("N. America", RadioGeo.continentOf(19.4, -99.1))
    }

    @Test fun `stations at the same spot share one pin, most popular first`() {
        val a = station("a", lat = -27.4710, lon = 153.0310, clicks = 1)
        val b = station("b", lat = -27.4715, lon = 153.0314, clicks = 50)
        val far = station("far", lat = -33.87, lon = 151.21)
        val noGeo = station("nogeo")
        val spots = RadioMapMath.spots(listOf(a, b, far, noGeo))
        assertEquals(2, spots.size)
        val city = spots.first { it.stations.size == 2 }
        assertEquals(listOf("b", "a"), city.stations.map { it.uuid })
        assertEquals("b", city.lead.uuid)
    }

    @Test fun `mercator projection round trips and keeps the equator in the middle`() {
        assertEquals(0.5, RadioMapMath.normX(0.0), 1e-9)
        assertEquals(0.5, RadioMapMath.normY(0.0), 1e-9)
        assertEquals(0.0, RadioMapMath.normX(-180.0), 1e-9)
        assertEquals(1.0, RadioMapMath.normX(180.0), 1e-9)
        // North is up: Brisbane (south) sits below the middle, London above.
        assertTrue(RadioMapMath.normY(-27.47) > 0.5)
        assertTrue(RadioMapMath.normY(51.5) < 0.5)
        assertEquals(-27.47, RadioMapMath.latOf(RadioMapMath.normY(-27.47)), 1e-6)
        assertEquals(153.03, RadioMapMath.lonOf(RadioMapMath.normX(153.03)), 1e-6)
    }

    @Test fun `bounds trim outliers`() {
        val stations = (0 until 40).map { station("s$it", lat = -27.0 - it * 0.01, lon = 153.0 + it * 0.01) } +
            station("stray", lat = 51.5, lon = -0.1)
        val b = RadioMapMath.bounds(stations)
        assertNotNull(b)
        assertTrue(b!![2] < 0, "north edge should ignore the stray London station, was ${b[2]}")
    }
}
