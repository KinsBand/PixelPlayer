package com.theveloper.pixelplay.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.nio.charset.Charset
import java.text.Normalizer

private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

fun Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}

/** Mojibake markers; allocated once (this runs for every text field of every song loaded). */
private val SUSPICIOUS_METADATA_PATTERNS = listOf("Ã", "â", "�", "ð", "Ÿ")

/**
 * Attempts to fix incorrectly encoded metadata strings that frequently appear when
 * tags are saved using Windows-1252/ISO-8859-1 but are later read as UTF-8. This results
 * in characters such as "Ã", "â" or replacement symbols appearing instead of expected
 * punctuation. The function re-encodes the text when those patterns are detected and
 * removes stray control characters while keeping the original text when no adjustment
 * is necessary.
 */
fun String?.normalizeMetadataText(): String? {
    if (this == null) return null
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return trimmed

    // Fast path: plain ASCII without NUL can't contain mojibake and is already NFC.
    // Most titles, artists and albums take this path with no extra allocation.
    if (trimmed.all { it.code in 1..0x7F }) return trimmed

    val needsFix = SUSPICIOUS_METADATA_PATTERNS.any { trimmed.contains(it) }

    val reencoded = if (needsFix) {
        runCatching {
            String(trimmed.toByteArray(WINDOWS_1252), Charsets.UTF_8).trim()
        }.getOrNull()
    } else null

    val candidate = reencoded?.takeIf { it.isNotEmpty() } ?: trimmed

    val cleaned = candidate.replace("\u0000", "")

    // isNormalized is a cheap scan; skip the copy when the text is already NFC.
    return if (Normalizer.isNormalized(cleaned, Normalizer.Form.NFC)) cleaned
    else Normalizer.normalize(cleaned, Normalizer.Form.NFC)
}

fun String?.normalizeMetadataTextOrEmpty(): String {
    return normalizeMetadataText() ?: ""
}

/**
 * Escape sequence for delimiters in artist names.
 * Use double backslash (\\) before a delimiter to prevent splitting at that position.
 * Example: "AC\\\\DC" with delimiter "/" won't split, but "Artist1/Artist2" will.
 */
private const val ESCAPE_SEQUENCE = "\\\\"

/**
 * Placeholder used internally during parsing to preserve escaped delimiters.
 */
private const val ESCAPE_PLACEHOLDER = "\u0000ESCAPED\u0000"

/** Stands in for a protected artist name while splitting. */
private const val PROTECT_PLACEHOLDER = "\u0001KEEP\u0001"

/** Lookahead: the separator isn't followed by the word "the". */
private const val NOT_BEFORE_THE = "(?!\\s*the\\s)"

/**
 * Default word-based delimiters for splitting multi-artist strings.
 * These are matched case-insensitively with word boundaries.
 */
val DEFAULT_WORD_DELIMITERS = listOf(
    // Features and collaborations
    "featuring", "feat.", "feat", "ft.", "ft", "fea.", "with", "w/", "duet with",
    "vs.", "vs", "v.", "versus", "meets", "presents", "pres.",
    // Producers
    "produced by", "prod. by", "prod.", "prod",
    // Joiners, only when written with spaces around them ("A & B", never "W&W" or "AC/DC")
    "x", "×", "&", "+", "/", "and",
    // Other languages ("with" / "and")
    "con", "avec", "mit", "und"
)

/**
 * Real artist names that contain a separator. They are never split, whatever the
 * separators are set to. (Names like "X & the Y" / "Tyler, the Creator" don't need to be
 * listed: a separator right before "the" never splits.)
 */
val PROTECTED_ARTIST_NAMES = listOf(
    "Simon & Garfunkel", "Simon and Garfunkel", "Hall & Oates", "Hall and Oates", "Daryl Hall & John Oates",
    "Mumford & Sons", "Mumford and Sons", "Earth, Wind & Fire", "Earth, Wind and Fire", "Earth Wind & Fire",
    "Crosby, Stills, Nash & Young", "Crosby, Stills & Nash", "Emerson, Lake & Palmer", "Blood, Sweat & Tears",
    "Peter, Paul and Mary", "Black Country, New Road", "Lost & Found", "Years & Years", "Above & Beyond",
    "Chase & Status", "Iron & Wine", "She & Him", "Belle & Sebastian", "Angus & Julia Stone", "Matt and Kim",
    "Of Monsters and Men", "Of Mice & Men", "Coheed and Cambria", "Brooks & Dunn", "Big & Rich", "Dan + Shay",
    "Maddie & Tae", "Love and Theft", "Sam & Dave", "Ike & Tina Turner", "Peaches & Herb", "Captain & Tennille",
    "Sonny & Cher", "Ashford & Simpson", "Loggins & Messina", "Seals & Crofts", "Zager & Evans", "Sly & Robbie",
    "Eric B. & Rakim", "Rodgers & Hammerstein", "Macklemore & Ryan Lewis", "Aly & AJ", "Aly & Fila", "Nico & Vinz",
    "Jack & Jack", "Lil Nas X", "Chloe x Halle", "Florence + the Machine", "Mike + the Mechanics", "AC/DC",
    "Kool & the Gang", "Marina and the Diamonds",
    "Siouxsie and the Banshees", "Derek and the Dominos", "Huey Lewis & the News", "Bob Marley & the Wailers",
)

/** [PROTECTED_ARTIST_NAMES] as whole-name patterns, longest first, compiled once. */
private val ProtectedNameRegexes: List<Regex> by lazy {
    PROTECTED_ARTIST_NAMES.sortedByDescending { it.length }.map { name ->
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }
}

/**
 * Splits an artist string by the given character delimiters and word delimiters,
 * respecting escaped delimiters.
 *
 * @param delimiters List of character delimiter strings to split by (e.g., ["/", ";", ","])
 * @param wordDelimiters List of word-based delimiters to split by (e.g., ["feat.", "ft.", "vs."])
 *        These are matched case-insensitively with surrounding whitespace.
 *        The single-letter "x" is handled specially — only matched when surrounded by spaces.
 * @return List of individual artist names, trimmed and with escaped delimiters restored.
 *         Returns a single-element list with the original string if no splitting occurs.
 *
 * Examples:
 * - "Artist1/Artist2".splitArtistsByDelimiters(listOf("/")) -> ["Artist1", "Artist2"]
 * - "AC\\DC".splitArtistsByDelimiters(listOf("/")) -> ["AC/DC"] (escaped)
 * - "Drake feat. Rihanna".splitArtistsByDelimiters(listOf(), listOf("feat.")) -> ["Drake", "Rihanna"]
 * - "Marshmello x Bastille".splitArtistsByDelimiters(listOf(), listOf("x")) -> ["Marshmello", "Bastille"]
 */
fun String.splitArtistsByDelimiters(
    delimiters: List<String>,
    wordDelimiters: List<String> = DEFAULT_WORD_DELIMITERS
): List<String> {
    if ((delimiters.isEmpty() && wordDelimiters.isEmpty()) || this.isBlank()) {
        return listOf(this.trim()).filter { it.isNotEmpty() }
    }

    // Sort delimiters by length descending to handle longer delimiters first
    val sortedDelimiters = delimiters.sortedByDescending { it.length }

    var working = this

    // Real names that contain a separator are kept whole (see PROTECTED_ARTIST_NAMES).
    val protectedMappings = mutableMapOf<String, String>()
    if (working.any { it == '&' || it == '+' || it == ',' || it == '/' || it == ' ' }) {
        ProtectedNameRegexes.forEachIndexed { index, nameRegex ->
            working = nameRegex.replace(working) { match ->
                val placeholder = "${PROTECT_PLACEHOLDER}${index}${PROTECT_PLACEHOLDER}"
                protectedMappings[placeholder] = match.value
                placeholder
            }
        }
    }

    // Replace escaped delimiters with placeholders
    val escapedMappings = mutableMapOf<String, String>()
    sortedDelimiters.forEachIndexed { index, delimiter ->
        val escapedDelimiter = ESCAPE_SEQUENCE + delimiter
        val placeholder = "${ESCAPE_PLACEHOLDER}${index}${ESCAPE_PLACEHOLDER}"
        escapedMappings[placeholder] = delimiter
        working = working.replace(escapedDelimiter, placeholder)
    }

    // Build combined regex pattern:
    // 1. Word delimiters: matched case-insensitively with whitespace boundaries
    //    Single-char word delimiters (like "x") require spaces on both sides
    // 2. Character delimiters: matched literally (with optional surrounding whitespace)
    val patternParts = mutableListOf<String>()

    // Word delimiters first (longer ones first to avoid partial matches)
    val sortedWordDelimiters = wordDelimiters.sortedByDescending { it.length }
    for (wd in sortedWordDelimiters) {
        val escaped = Regex.escape(wd)
        if (wd.length == 1) {
            // Single-char word delimiters (e.g., "x") — require spaces on both sides.
            // Never right before "the" ("Kool & the Gang", "Florence + the Machine").
            patternParts.add("\\s+$escaped\\s+$NOT_BEFORE_THE")
        } else {
            // Multi-char word delimiters — require word boundary or whitespace
            patternParts.add("\\s+$escaped\\s+$NOT_BEFORE_THE|\\s+$escaped$|^$escaped\\s+")
        }
    }

    // Character delimiters
    if (sortedDelimiters.isNotEmpty()) {
        val charPattern = sortedDelimiters.joinToString("|") { Regex.escape(it) }
        // "Tyler, the Creator": a separator right before "the" doesn't split.
        patternParts.add("(?:$charPattern)$NOT_BEFORE_THE")
    }

    if (patternParts.isEmpty()) {
        return listOf(this.trim()).filter { it.isNotEmpty() }
    }

    val combinedPattern = patternParts.joinToString("|")
    val regex = Regex(combinedPattern, RegexOption.IGNORE_CASE)

    // Split by combined pattern
    val parts = working.split(regex)

    // Restore escaped delimiters and trim each part
    return parts
        .map { part ->
            var restored = part
            escapedMappings.forEach { (placeholder, delimiter) ->
                restored = restored.replace(placeholder, delimiter)
            }
            protectedMappings.forEach { (placeholder, name) ->
                restored = restored.replace(placeholder, name)
            }
            restored.trim()
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .ifEmpty { if (this.trim().isNotEmpty()) listOf(this.trim()) else emptyList() }
}

/**
 * Extracts featured artists from a song title and returns the cleaned title + extracted artists.
 *
 * Detects patterns like:
 * - "Song (feat. Artist)" / "Song [feat. Artist]"
 * - "Song (ft. Artist1 & Artist2)" / "Song [with Artist]"
 *
 * @param delimiters Character delimiters to further split extracted artist strings
 * @param wordDelimiters Word delimiters to further split extracted artist strings
 * @return Pair of (cleaned title, list of extracted artist names). Empty list if no artists found.
 */
fun String.extractArtistsFromTitle(
    delimiters: List<String> = emptyList(),
    wordDelimiters: List<String> = DEFAULT_WORD_DELIMITERS
): Pair<String, List<String>> {
    if (this.isBlank()) return this to emptyList()

    // Match patterns like (feat. ...), [ft. ...], (with ...), etc.
    val featureKeywords = listOf(
        "featuring", "feat\\.", "feat", "ft\\.", "ft", "fea\\.", "duet with", "with", "w/",
        "vs\\.", "vs", "versus", "con", "avec", "prod\\. by", "prod\\.", "prod", "produced by"
    )
    val keywordPattern = featureKeywords.joinToString("|")
    val bracketPattern = Regex(
        """[\(\[]\\?\s*(?:$keywordPattern)\s+(.+?)\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    val extractedArtists = mutableListOf<String>()
    var cleanedTitle = this

    bracketPattern.findAll(this).forEach { match ->
        val artistString = match.groupValues[1]
        // Split the extracted artist string by delimiters (handles "Artist1 & Artist2" inside parens)
        val artists = artistString.splitArtistsByDelimiters(delimiters, wordDelimiters)
        extractedArtists.addAll(artists)
        cleanedTitle = cleanedTitle.replace(match.value, "")
    }

    return cleanedTitle.trim() to extractedArtists.distinct()
}

/**
 * Joins a list of artist names into a display string.
 * 
 * @param separator The separator to use between artist names (default: ", ")
 * @return A formatted string with all artist names joined.
 */
fun List<String>.joinArtistsForDisplay(separator: String = ", "): String {
    return this.joinToString(separator)
}
