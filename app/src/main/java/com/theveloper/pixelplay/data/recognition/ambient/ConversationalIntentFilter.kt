package com.theveloper.pixelplay.data.recognition.ambient

import java.util.Locale

/**
 * Result of analyzing a spoken transcript for conversational song suggestions.
 */
data class ExtractedSongSuggestion(
    val rawText: String,
    val cleanTitle: String,
    val artist: String? = null,
    val isSuggestion: Boolean = true
)

/**
 * Filters and extracts song suggestions from spoken conversational speech.
 * Only triggers if suggestive phrases (e.g. "play...", "put on...", "how about...")
 * are detected to prevent false positives from ambient banter.
 */
object ConversationalIntentFilter {

    private val TRIGGER_PREFIXES = listOf(
        "can we play",
        "can you play",
        "can we listen to",
        "can you put on",
        "could you play",
        "could you put on",
        "how about",
        "what about",
        "have you heard",
        "put on",
        "play",
        "queue up",
        "queue",
        "let's listen to",
        "lets listen to",
        "let's play",
        "lets play",
        "i want to hear",
        "i wanna hear",
        "listen to",
        "i love the song", "i like the song", "the song", "that song",
        "we were talking about", "i love", "i like"
    ).sortedByDescending { it.length } // Match longest prefix first

    private val DISMISS_SUFFIXES = listOf(
        "please",
        "next",
        "now",
        "for me",
        "right now",
        "thanks"
    ).sortedByDescending { it.length }

    /**
     * Parses the transcript. Returns an [ExtractedSongSuggestion] if a suggestion
     * intent is detected with a non-empty song query, or null if the speech was
     * not a song suggestion.
     */
    fun parse(transcript: String): ExtractedSongSuggestion? {
        val trimmed = transcript.trim().lowercase(Locale.ROOT)
            .replace('’', '\'').replace(Regex("\\s+"), " ")
            .trimEnd('.', '!', '?', ',')
            .replace(Regex("^(?:hey|okay|ok|yeah|well)[, ]+"), "")
        if (trimmed.length < 4) return null

        // Find matching prefix
        var matchedPrefix: String? = null
        for (prefix in TRIGGER_PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                // Ensure word boundary after prefix (not e.g. "player" matching "play")
                if (trimmed.length == prefix.length || trimmed[prefix.length] == ' ') {
                    matchedPrefix = prefix
                    break
                }
            }
        }

        if (matchedPrefix == null) {
            return null
        }

        var query = trimmed.substring(matchedPrefix.length).trim()

        // Strip leading filler words
        val leadingFillers = listOf("some", "the song", "song", "track", "that song")
        for (filler in leadingFillers) {
            if (query.startsWith("$filler ")) {
                query = query.substring(filler.length).trim()
            }
        }

        // Strip trailing suffixes
        do {
            val suffix = DISMISS_SUFFIXES.firstOrNull { query.endsWith(" $it") }
            if (suffix != null) query = query.removeSuffix(" $suffix").trimEnd(' ', ',')
        } while (suffix != null)

        if (query.isBlank() || query.length < 2) {
            return null
        }

        // Check for artist delimiter e.g. "by [artist]" or "from [artist]"
        var songTitle = query
        var artistName: String? = null

        val artistDelimiters = listOf(" by ", " from ")
        for (delimiter in artistDelimiters) {
            val idx = query.indexOf(delimiter)
            if (idx > 0 && idx + delimiter.length < query.length) {
                songTitle = query.substring(0, idx).trim()
                artistName = query.substring(idx + delimiter.length).trim()
                break
            }
        }

        songTitle = songTitle.trim(' ', '"', '\'')
        // Generic statements need an artist to distinguish a song mention from ordinary chat.
        if (matchedPrefix in listOf("i love", "i like", "we were talking about") && artistName == null) return null
        if (songTitle.isBlank()) return null

        return ExtractedSongSuggestion(
            rawText = transcript,
            cleanTitle = songTitle,
            artist = artistName?.takeIf { it.isNotBlank() },
            isSuggestion = true
        )
    }
}
