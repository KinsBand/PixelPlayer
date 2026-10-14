package com.theveloper.pixelplay.data.tour

import com.theveloper.pixelplay.data.repository.ArtistCatalog

/**
 * Pure: Ticketmaster shows + Spotify concerts as one list. The same show from both (same date and
 * city — the city is compared up to its first comma, accents/case ignored) keeps the Ticketmaster
 * copy (coordinates, ticket link), taking a missing start time from Spotify. Sorted by date, then
 * time. Tested in TourMergeTest.
 */
object TourMerge {
    private fun cityKey(city: String): String = ArtistCatalog.fold(city.substringBefore(','))

    private fun key(show: ArtistShow): String = show.date + "|" + cityKey(show.city)

    fun merge(ticketmaster: List<ArtistShow>, spotify: List<ArtistShow>): List<ArtistShow> {
        val byKey = LinkedHashMap<String, ArtistShow>()
        ticketmaster.forEach { byKey.putIfAbsent(key(it), it) }
        spotify.forEach { show ->
            val k = key(show)
            val existing = byKey[k]
            if (existing == null) byKey[k] = show
            else if (existing.time == null && show.time != null) byKey[k] = existing.copy(time = show.time)
        }
        return byKey.values.sortedWith(compareBy<ArtistShow> { it.date }.thenBy { it.time ?: "99:99" })
    }
}
