package com.theveloper.pixelplay.data.radio

import java.text.Normalizer
import java.util.Locale

/**
 * In-memory station search that answers while you type (well under a millisecond for a few
 * thousand stations). Every station's searchable text is folded once (lower case, accents
 * stripped), so a query is a plain scan of pre-built strings with no allocation per station.
 *
 * Ranking: name starts with the query > a word in the name starts with it > the name contains
 * it > a genre tag or the place matches; ties go to the more popular station, and stations in
 * [preferCountry] come first within each band.
 */
class RadioSearchIndex(stations: List<RadioStation>) {
    private val entries: Array<Item> = stations.distinctBy { it.uuid }.map { s ->
        Item(
            station = s,
            name = fold(s.name),
            // Short forms too, so "comm" finds community radio and "nsw" finds New South Wales.
            tags = fold((s.tags + s.tags.map(RadioLabels::shortTag)).joinToString(" ")),
            place = fold(listOfNotNull(s.state, s.shortState, s.country, s.language).joinToString(" ")),
        )
    }.toTypedArray()

    val size: Int get() = entries.size

    fun search(query: String, limit: Int = 60, preferCountry: String? = null): List<RadioStation> {
        val q = fold(query).trim()
        if (q.isEmpty() || entries.isEmpty()) return emptyList()
        val words = q.split(' ').filter { it.isNotEmpty() }
        val hits = ArrayList<Hit>()
        for (item in entries) {
            val rank = rank(item, q, words)
            if (rank < 0) continue
            val home = preferCountry != null && item.station.countryCode.equals(preferCountry, ignoreCase = true)
            hits += Hit(item.station, rank * 2 + if (home) 0 else 1)
        }
        hits.sortWith(compareBy<Hit> { it.band }.thenByDescending { it.station.clickCount })
        return hits.asSequence().take(limit).map { it.station }.toList()
    }

    /** 0 = best. -1 = no match. Every query word has to match somewhere. */
    private fun rank(item: Item, q: String, words: List<String>): Int {
        val name = item.name
        if (name.startsWith(q)) return 0
        if (containsWordStart(name, q)) return 1
        if (name.contains(q)) return 2
        // Multi-word queries ("triple j brisbane", "jazz paris") can be spread over fields.
        var allMatch = true
        var nameHits = 0
        for (w in words) {
            when {
                name.contains(w) -> nameHits++
                item.tags.contains(w) || item.place.contains(w) -> Unit
                else -> { allMatch = false; break }
            }
        }
        if (!allMatch) return -1
        return if (nameHits > 0) 3 else 4
    }

    private class Item(val station: RadioStation, val name: String, val tags: String, val place: String)

    private class Hit(val station: RadioStation, val band: Int)

    companion object {
        private val MARKS = Regex("\\p{Mn}+")

        fun fold(text: String): String {
            val lower = text.lowercase(Locale.ROOT)
            val ascii = lower.all { it.code < 128 }
            val base = if (ascii) lower else MARKS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
            // Punctuation becomes a space so "triple-j" finds "triple j".
            val sb = StringBuilder(base.length)
            var lastSpace = true
            for (c in base) {
                if (c.isLetterOrDigit()) {
                    sb.append(c); lastSpace = false
                } else if (!lastSpace) {
                    sb.append(' '); lastSpace = true
                }
            }
            return sb.toString().trimEnd()
        }

        private fun containsWordStart(haystack: String, needle: String): Boolean {
            var i = haystack.indexOf(needle)
            while (i >= 0) {
                if (i == 0 || haystack[i - 1] == ' ') return true
                i = haystack.indexOf(needle, i + 1)
            }
            return false
        }
    }
}
