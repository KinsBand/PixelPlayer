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

    // Compiled once. These used to be built on every call; blends and matching run them for
    // thousands of songs, and compiling an ICU pattern each time churned native memory until it
    // failed (PatternSyntaxException: U_MEMORY_ALLOCATION_ERROR, then OutOfMemoryError).
    private val MARKS = Regex("\\p{M}+")
    private val BRACKETS = Regex("\\(.*?\\)|\\[.*?]")
    private val FEATURING = Regex("\\b(feat\\.?|ft\\.?|featuring)\\b.*")
    private val NOT_LETTER_DIGIT_SPACE = Regex("[^\\p{L}\\p{N} ]")
    private val SPACES = Regex("\\s+")
    private val ARTIST_SPLIT = Regex("[,&/]|\\bx\\b|\\band\\b|\\bfeat\\.?\\b")

    fun stripAccents(value: String): String {
        // Plain ASCII has no accents: skip the Unicode normalisation (most titles).
        if (value.all { it.code < 128 }) return value
        return Normalizer.normalize(value, Normalizer.Form.NFD).replace(MARKS, "")
    }

    /** Lower-case core title without "(...)", "[...]", " - suffix", "feat." parts. */
    fun normalize(title: String): String {
        var t = stripAccents(title).lowercase(Locale.ROOT).trim()
        t = t.replace(BRACKETS, " ")
        t = t.substringBefore(" - ")
        t = t.replace(FEATURING, " ")
        t = t.replace(NOT_LETTER_DIGIT_SPACE, " ")
        return t.replace(SPACES, " ").trim()
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
                .split(ARTIST_SPLIT)
                .map { it.replace(NOT_LETTER_DIGIT_SPACE, "").trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
        val a = tokens(source)
        val b = tokens(candidate)
        return a.any { x -> b.any { y -> x == y || x.contains(y) || y.contains(x) } }
    }
}
