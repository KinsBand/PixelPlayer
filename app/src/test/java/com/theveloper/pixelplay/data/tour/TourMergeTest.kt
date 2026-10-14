package com.theveloper.pixelplay.data.tour

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Guards the merged Events list: one entry per date + city, Ticketmaster wins, sorted by date. */
class TourMergeTest {
    private fun show(id: String, date: String, city: String, time: String? = null, lat: Double? = null) = ArtistShow(
        id = id, date = date, time = time, venue = "V", city = city, region = null, country = "US",
        countryCode = "US", latitude = lat, longitude = lat, ticketUrl = null
    )

    @Test fun `same date and city keeps ticketmaster and fills its time`() {
        val merged = TourMerge.merge(
            listOf(show("tm", "2026-10-13", "Orlando", lat = 1.0)),
            listOf(show("sp", "2026-10-13", "Orlando, FL", time = "20:00"))
        )
        val only = merged.single()
        assertEquals("tm", only.id)
        assertEquals("20:00", only.time)
        assertEquals(1.0, only.latitude)
    }

    @Test fun `different shows are all kept, sorted by date`() {
        val merged = TourMerge.merge(
            listOf(show("b", "2026-10-16", "The Woodlands")),
            listOf(show("a", "2026-10-14", "Duluth"), show("c", "2026-10-19", "Phoenix"))
        )
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test fun `spotify only works without ticketmaster`() {
        assertEquals(1, TourMerge.merge(emptyList(), listOf(show("s", "2026-10-13", "Orlando"))).size)
    }
}
