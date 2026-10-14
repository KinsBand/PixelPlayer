package com.theveloper.pixelplay.data.artist

import java.util.Locale

/**
 * Represents a member of a band or musical ensemble.
 *
 * @property name Full name of the band member.
 * @property role Primary instrument or role in the group (e.g., "Lead Vocals", "Drums", "Lead Guitar").
 * @property imageUrl Optional photo URL for the member.
 */
data class BandMember(
    val name: String,
    val role: String,
    val imageUrl: String? = null,
    /** Still in the band (false = a past member). */
    val isCurrent: Boolean = true
)

/**
 * Record of an interactive on-device AI Q&A interaction with the user.
 *
 * @property prompt The user's question or topic inquiry.
 * @property response The locally generated contextual response.
 * @property timestamp Epoch millis timestamp of the query.
 */
data class ArtistAiQuery(
    val prompt: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * On-device local knowledge assistant for artists in the Artist Hub.
 *
 * Evaluates contextual metadata (artist biography, genres, discography references,
 * band members, and career milestones) completely on-device without making external
 * network requests, ensuring total privacy and instantaneous response times.
 */
class ArtistAiEngine {

    /**
     * Primary query handler that analyzes the artist profile and produces
     * a tailored contextual answer to the user's prompt.
     *
     * @param artistName Name of the artist.
     * @param bio The biographical text (from Last.fm, Wikipedia, or local cache).
     * @param prompt The question or prompt entered by the user.
     * @return Formatted response string.
     */
    fun query(artistName: String, bio: String?, prompt: String): String {
        return query(
            artistName = artistName,
            bio = bio,
            prompt = prompt,
            genres = emptyList(),
            careerSummary = null,
            members = emptyList()
        )
    }

    /**
     * Extended contextual query handler accepting optional genres, career summary,
     * and explicit band members.
     */
    fun query(
        artistName: String,
        bio: String?,
        prompt: String,
        genres: List<String> = emptyList(),
        careerSummary: String? = null,
        members: List<BandMember> = emptyList()
    ): String {
        val cleanBio = sanitizeBio(bio)
        val normalizedPrompt = prompt.trim().lowercase(Locale.ROOT)

        if (cleanBio.isNullOrBlank() && genres.isEmpty() && members.isEmpty()) {
            return "I don't have enough local biographical data stored for $artistName yet. " +
                "Once artist metadata is cached, I can answer questions about their history, sound, members, and releases right here on your device."
        }

        val sentences = cleanBio?.let { extractSentences(it) } ?: emptyList()

        return when {
            // MEMBERS & LINEUP
            isMembersQuery(normalizedPrompt) -> {
                answerMembersQuery(artistName, sentences, members)
            }

            // ORIGINS & FORMATION
            isOriginOrFormationQuery(normalizedPrompt) -> {
                answerOriginQuery(artistName, sentences, cleanBio)
            }

            // GENRES & MUSICAL STYLE
            isGenreOrStyleQuery(normalizedPrompt) -> {
                answerGenreQuery(artistName, sentences, genres)
            }

            // ALBUMS & DISCOGRAPHY
            isDiscographyQuery(normalizedPrompt) -> {
                answerDiscographyQuery(artistName, sentences)
            }

            // SUMMARY & GENERAL OVERVIEW
            isSummaryQuery(normalizedPrompt) -> {
                answerSummaryQuery(artistName, cleanBio, careerSummary, genres)
            }

            // INFLUENCES & IMPACT
            isInfluenceQuery(normalizedPrompt) -> {
                answerInfluenceQuery(artistName, sentences)
            }

            // CONTEXTUAL MATCHING
            else -> {
                answerContextualQuery(artistName, normalizedPrompt, sentences, genres)
            }
        }
    }

    private fun isMembersQuery(prompt: String): Boolean {
        return listOf("member", "members", "lineup", "who is in", "who are in", "frontman", "lead singer",
            "drummer", "guitarist", "bassist", "personnel", "musicians").any { prompt.contains(it) }
    }

    private fun isOriginOrFormationQuery(prompt: String): Boolean {
        return listOf("where from", "where are", "origin", "hometown", "born", "city", "country",
            "formed", "founded", "formation", "start", "began", "when was", "when did").any { prompt.contains(it) }
    }

    private fun isGenreOrStyleQuery(prompt: String): Boolean {
        return listOf("genre", "genres", "style", "sound", "what kind of music", "type of music", "vibe").any {
            prompt.contains(it)
        }
    }

    private fun isDiscographyQuery(prompt: String): Boolean {
        return listOf("album", "albums", "discography", "releases", "records", "songs", "tracks", "hits",
            "popular", "breakthrough", "debut").any { prompt.contains(it) }
    }

    private fun isSummaryQuery(prompt: String): Boolean {
        return listOf("who is", "who are", "tell me about", "about", "bio", "overview", "summary",
            "introduce", "describe", "story").any { prompt.contains(it) }
    }

    private fun isInfluenceQuery(prompt: String): Boolean {
        return listOf("influence", "inspired", "roots", "legacy", "impact", "inspiration").any {
            prompt.contains(it)
        }
    }

    private fun answerMembersQuery(
        artistName: String,
        sentences: List<String>,
        members: List<BandMember>
    ): String {
        if (members.isNotEmpty()) {
            val list = members.joinToString("\n") { "• ${it.name} (${it.role})" }
            return "Here is the lineup for $artistName:\n$list"
        }

        // Look for sentences in the bio mentioning members or lineup
        val memberKeywords = listOf("member", "consists of", "formed by", "features", "lineup", "vocals", "guitar", "bass", "drums", "keyboard")
        val relevant = sentences.filter { s -> memberKeywords.any { s.contains(it, ignoreCase = true) } }

        return if (relevant.isNotEmpty()) {
            val details = relevant.take(2).joinToString(" ")
            "According to local biography notes for $artistName: $details"
        } else {
            "$artistName performs primarily under this name, and specific individual lineup details aren't explicitly cataloged in the local biography cache."
        }
    }

    private fun answerOriginQuery(
        artistName: String,
        sentences: List<String>,
        cleanBio: String?
    ): String {
        val originKeywords = listOf("formed in", "hails from", "based in", "born in", "native of", "founded in", "from ")
        val relevant = sentences.filter { s -> originKeywords.any { s.contains(it, ignoreCase = true) } }

        return if (relevant.isNotEmpty()) {
            val facts = relevant.take(2).joinToString(" ")
            "$facts"
        } else {
            val year = cleanBio?.let { extractYear(it) }
            if (year != null) {
                "$artistName's career began around $year, according to local records."
            } else {
                "Specific origin and founding location details for $artistName aren't specified in the local biography cache."
            }
        }
    }

    private fun answerGenreQuery(
        artistName: String,
        sentences: List<String>,
        genres: List<String>
    ): String {
        val genreText = if (genres.isNotEmpty()) {
            genres.joinToString(", ")
        } else {
            null
        }

        val styleSentences = sentences.filter { s ->
            listOf("genre", "style", "sound", "known for", "pioneer", "fusion", "characterized").any {
                s.contains(it, ignoreCase = true)
            }
        }.take(2).joinToString(" ")

        return when {
            genreText != null && styleSentences.isNotBlank() -> {
                "$artistName is recognized for $genreText music. $styleSentences"
            }
            genreText != null -> {
                "$artistName primarily works within: $genreText."
            }
            styleSentences.isNotBlank() -> {
                styleSentences
            }
            else -> {
                "Genre classifications for $artistName aren't currently available locally."
            }
        }
    }

    private fun answerDiscographyQuery(artistName: String, sentences: List<String>): String {
        val albumSentences = sentences.filter { s ->
            listOf("album", "released", "debut", "record", "single", "hit", "chart", "billboard", "ep").any {
                s.contains(it, ignoreCase = true)
            }
        }

        return if (albumSentences.isNotEmpty()) {
            val topSentences = albumSentences.take(3).joinToString(" ")
            "Notable release highlights for $artistName: $topSentences"
        } else {
            "Information about $artistName's discography and key records is being indexed from your library and catalogue."
        }
    }

    private fun answerSummaryQuery(
        artistName: String,
        cleanBio: String?,
        careerSummary: String?,
        genres: List<String>
    ): String {
        if (!careerSummary.isNullOrBlank()) {
            return careerSummary
        }

        if (!cleanBio.isNullOrBlank()) {
            val sentences = extractSentences(cleanBio)
            val summary = sentences.take(3).joinToString(" ")
            val genreSuffix = if (genres.isNotEmpty()) {
                "\n\nGenres: ${genres.joinToString(", ")}"
            } else ""
            return "$summary$genreSuffix"
        }

        return if (genres.isNotEmpty()) {
            "$artistName is an artist known for ${genres.joinToString(", ")}."
        } else {
            "$artistName is indexed in your music collection."
        }
    }

    private fun answerInfluenceQuery(artistName: String, sentences: List<String>): String {
        val influenceSentences = sentences.filter { s ->
            listOf("influence", "inspired", "roots", "tradition", "legacy", "impact", "cited").any {
                s.contains(it, ignoreCase = true)
            }
        }

        return if (influenceSentences.isNotEmpty()) {
            influenceSentences.take(2).joinToString(" ")
        } else {
            "Specific artistic influences for $artistName are not detailed in the local biographical overview."
        }
    }

    private fun answerContextualQuery(
        artistName: String,
        prompt: String,
        sentences: List<String>,
        genres: List<String>
    ): String {
        if (sentences.isEmpty()) {
            return "Based on your library, $artistName is part of your collection" +
                if (genres.isNotEmpty()) " (${genres.joinToString(", ")})" else "" + "."
        }

        // Tokenize prompt excluding stop words
        val stopWords = setOf("a", "an", "the", "in", "on", "at", "for", "of", "to", "and", "or", "is",
            "are", "was", "were", "what", "how", "when", "where", "why", "who", "which", "do", "does", "did",
            "can", "could", "would", "tell", "me", "about", "artist")
        val queryTokens = prompt.split(Regex("""\W+"""))
            .filter { it.length > 2 && it !in stopWords }

        if (queryTokens.isEmpty()) {
            return sentences.take(2).joinToString(" ")
        }

        // Score sentences by token overlap
        val scored = sentences.map { sentence ->
            val lower = sentence.lowercase(Locale.ROOT)
            val score = queryTokens.count { token -> lower.contains(token) }
            sentence to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }

        return if (scored.isNotEmpty()) {
            scored.take(2).joinToString(" ") { it.first }
        } else {
            // Graceful fallback using first bio sentence
            val leadSentence = sentences.firstOrNull() ?: "$artistName is in your music catalogue."
            "Here is what is recorded locally for $artistName: $leadSentence"
        }
    }

    /**
     * Sanitizes raw biographical strings by stripping HTML tags, Last.fm licensing
     * boilerplate, and normalizing whitespace.
     */
    fun sanitizeBio(rawBio: String?): String? {
        if (rawBio.isNullOrBlank()) return null

        var text = rawBio
            // Strip HTML tags like <a href="...">...</a> or <br/>
            .replace(Regex("""<[^>]*>"""), " ")
            // Strip common Last.fm trailing boilerplate
            .replace(Regex("""(?i)Read more on Last\.fm.*"""), "")
            .replace(Regex("""(?i)User-contributed text is available under the Creative Commons.*"""), "")
            // Normalize whitespace
            .replace(Regex("""\s+"""), " ")
            .trim()

        return text.takeIf { it.isNotBlank() }
    }

    /**
     * Splits text into individual sentences.
     */
    fun extractSentences(text: String): List<String> {
        return text.split(Regex("""(?<=[.!?])\s+(?=[A-Z0-9"'])"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Attempts to find a year (e.g. 1970-2029) mentioned in the text.
     */
    fun extractYear(text: String): String? {
        val match = Regex("""\b(19\d{2}|20[0-2]\d)\b""").find(text)
        return match?.value
    }
}
