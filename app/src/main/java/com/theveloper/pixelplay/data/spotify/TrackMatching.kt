package com.theveloper.pixelplay.data.spotify

import java.text.Normalizer
import java.util.Locale

/**
 * Title/artist normalisation used to map a metadata track (Spotify, MusicBrainz...) onto an
 * audio source result. Same rules as Spotube audio-source plugins: strip accents and
 * decorations, compare the core title, and refuse variants (live/remix/karaoke...) the source
 * track does not have.
 */
object TrackMatching {
    private val MODIFIERS = listOf(
        "karaoke", "remix", "live", "instrumental", "acoustic", "reprise", "demo",
        "cover", "tribute", "orchestral", "piano", "sped up", "slowed", "nightcore", "8d"
    )
    private val modifierRegex = MODIFIERS.associateWith { Regex("\\b${Regex.escape(it)}\\b") }

    fun stripAccents(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Lower-case core title without "(...)", "[...]", " - suffix", "feat." parts. */
    fun normalize(title: String): String {
        var t = stripAccents(title).lowercase(Locale.ROOT).trim()
        t = t.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        t = t.substringBefore(" - ")
        t = t.replace(Regex("\\b(feat\\.?|ft\\.?|featuring)\\b.*"), " ")
        t = t.replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    fun modifiers(title: String): Set<String> {
        val lower = stripAccents(title).lowercase(Locale.ROOT)
        return modifierRegex.filterValues { it.containsMatchIn(lower) }.keys
    }

    fun titlesMatch(source: String, candidate: String): Boolean {
        val a = normalize(source)
        val b = normalize(candidate)
        if (a.isEmpty() || b.isEmpty()) return true
        return a == b || b.contains(a) || a.contains(b)
    }

    fun artistsOverlap(source: String, candidate: String): Boolean {
        val tokens = { v: String ->
            stripAccents(v).lowercase(Locale.ROOT).removeSuffix(" - topic")
                .split(Regex("[,&/]|\\bx\\b|\\band\\b|\\bfeat\\.?\\b"))
                .map { it.replace(Regex("[^\\p{L}\\p{N} ]"), "").trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
        val a = tokens(source)
        val b = tokens(candidate)
        return a.any { x -> b.any { y -> x == y || x.contains(y) || y.contains(x) } }
    }
}
