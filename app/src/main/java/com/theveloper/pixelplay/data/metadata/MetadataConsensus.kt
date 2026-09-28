package com.theveloper.pixelplay.data.metadata

import java.util.Locale

/**
 * Merges what several catalogues found for one song by weighted vote, field by field, instead
 * of trusting one source and only filling its gaps from the next ([METHOD]).
 *
 * Each source's value is a vote, weighted by how reliable that source is for that field.
 * Values that mean the same (compared loosely: case and punctuation, "- Single", "(Deluxe
 * Edition)" and "(Remastered)" on albums, "Hip-Hop/Rap" and "Rap/Hip Hop" for genres, durations
 * within 2 s) pool their weight; the heaviest pool wins, spelled as its most trusted source
 * spells it. Ties go to the more trusted source. The share of the weight that agreed is kept
 * per field and disagreements are listed, so the song info can say how sure a value is.
 *
 * The year is the exception to voting: MusicBrainz's first release date is the earliest
 * release of that recording, so it wins when no source has an earlier one (a 2011 remaster
 * album doesn't make a 1975 song a 2011 one).
 */
object MetadataConsensus {
    const val METHOD = "consensus-v1"

    const val DEEZER = "Deezer"
    const val ITUNES = "iTunes"
    const val MUSICBRAINZ = "MusicBrainz"

    /** Combines single-source results (each with its source as the only entry of `sources`). */
    fun combine(results: List<GatheredMetadata>): GatheredMetadata {
        val found = results.filter { it.found }
        if (found.isEmpty()) return GatheredMetadata(found = false)
        val bySource = found.associateBy { it.sources.firstOrNull().orEmpty() }
        val agreement = linkedMapOf<String, Float>()
        val conflicts = mutableListOf<String>()

        fun <T : Any> field(
            name: String,
            order: List<String>,
            read: (GatheredMetadata) -> T?,
            same: (T, T) -> Boolean = { a, b -> a == b },
            show: (T) -> String = { it.toString() }
        ): T? {
            val votes = order.mapNotNull { source ->
                val result = bySource[source] ?: return@mapNotNull null
                read(result)?.let { Vote(source, it, weight(source, name)) }
            } + bySource.filterKeys { it !in order }.mapNotNull { (source, result) ->
                read(result)?.let { Vote(source, it, weight(source, name)) }
            }
            val tally = tally(name, votes, same, show) ?: return null
            tally.agreement?.let { agreement[name] = it }
            tally.conflict?.let { conflicts += it }
            return tally.value
        }

        val usual = listOf(DEEZER, ITUNES, MUSICBRAINZ)
        val numbering = listOf(ITUNES, DEEZER, MUSICBRAINZ)

        val genre = field("genre", usual, { it.genre?.takeIf(String::isNotBlank) },
            same = { a, b -> genreKey(a) == genreKey(b) })
        val album = field("album", usual, { it.album?.takeIf(String::isNotBlank) },
            same = { a, b -> albumKey(a) == albumKey(b) }, show = { "\"$it\"" })
        val artist = field("artist", usual, { it.artist?.takeIf(String::isNotBlank) },
            same = { a, b -> nameKey(a) == nameKey(b) }, show = { "\"$it\"" })
        val albumArtist = field("albumArtist", usual, { it.albumArtist?.takeIf(String::isNotBlank) },
            same = { a, b -> nameKey(a) == nameKey(b) }, show = { "\"$it\"" })
        val durationMs = field("duration", usual, { it.durationMs?.takeIf { d -> d > 0 } },
            same = { a, b -> kotlin.math.abs(a - b) <= 2_000 }, show = { "${it / 1000} s" })
        val trackNumber = field("trackNumber", numbering, { it.trackNumber?.takeIf { n -> n > 0 } })
        val discNumber = field("discNumber", numbering, { it.discNumber?.takeIf { n -> n > 0 } })
        // Explicit if any source says so: mislabelling a clean song costs less than the reverse.
        val explicit = found.mapNotNull { it.explicit }.takeIf { it.isNotEmpty() }?.any { it }
        val isrc = field("isrc", usual, { it.isrc?.uppercase(Locale.ROOT)?.takeIf { code -> code.length == 12 } })
        val (year, releaseDate) = originalRelease(bySource, agreement, conflicts)

        fun <T> first(order: List<String>, read: (GatheredMetadata) -> T?): T? =
            (order + bySource.keys).firstNotNullOfOrNull { source -> bySource[source]?.let(read) }

        return GatheredMetadata(
            genre = genre,
            album = album,
            artist = artist,
            durationMs = durationMs,
            year = year,
            bpm = first(usual) { it.bpm },
            mood = first(usual) { it.mood },
            albumArtist = albumArtist,
            trackNumber = trackNumber,
            discNumber = discNumber,
            releaseDate = releaseDate,
            // iTunes covers go up to 3000 px, Deezer's to 1000 px.
            coverUrl = first(numbering) { it.coverUrl },
            isrc = isrc,
            label = first(usual) { it.label },
            upc = first(usual) { it.upc },
            explicit = explicit,
            gainDb = first(usual) { it.gainDb },
            tags = first(listOf(MUSICBRAINZ)) { it.tags.takeIf(List<String>::isNotEmpty) }.orEmpty(),
            composer = first(listOf(MUSICBRAINZ)) { it.composer },
            lyricist = first(listOf(MUSICBRAINZ)) { it.lyricist },
            songwriter = first(listOf(MUSICBRAINZ)) { it.songwriter },
            language = first(listOf(MUSICBRAINZ)) { it.language },
            recordingMbid = first(listOf(MUSICBRAINZ)) { it.recordingMbid },
            releaseMbid = first(listOf(MUSICBRAINZ)) { it.releaseMbid },
            workMbid = first(listOf(MUSICBRAINZ)) { it.workMbid },
            agreement = agreement,
            conflicts = conflicts,
            sources = found.flatMap { it.sources }.distinct(),
            deepAt = found.maxOf { it.deepAt },
            found = true
        )
    }

    /**
     * Year and release date. MusicBrainz's first release date wins when no other source has an
     * earlier year; otherwise the year is voted on like the other fields.
     */
    private fun originalRelease(
        bySource: Map<String, GatheredMetadata>,
        agreement: MutableMap<String, Float>,
        conflicts: MutableList<String>
    ): Pair<Int?, String?> {
        fun yearOf(g: GatheredMetadata): Int? =
            (g.year ?: g.releaseDate?.take(4)?.toIntOrNull())?.takeIf { it in 1900..2100 }
        val years = listOf(DEEZER, ITUNES, MUSICBRAINZ).mapNotNull { source ->
            bySource[source]?.let(::yearOf)?.let { Vote(source, it, weight(source, "year")) }
        }
        if (years.isEmpty()) return null to null
        val mbYear = years.firstOrNull { it.source == MUSICBRAINZ }?.value
        val chosen = if (mbYear != null && years.all { it.value >= mbYear }) {
            if (years.size > 1) {
                agreement["year"] = (years.filter { it.value == mbYear }.sumOf { it.weight } / years.sumOf { it.weight }).toFloat()
                if (years.any { it.value != mbYear }) {
                    conflicts += "year: " + years.joinToString(", ") { "${it.source} ${it.value}" } + " → $mbYear (first release)"
                }
            }
            MUSICBRAINZ
        } else {
            val tally = tally("year", years, { a, b -> a == b }) { it.toString() } ?: return null to null
            tally.agreement?.let { agreement["year"] = it }
            tally.conflict?.let { conflicts += it }
            years.first { it.value == tally.value }.source
        }
        val source = bySource.getValue(chosen)
        val year = yearOf(source)
        val date = source.releaseDate?.takeIf { it.take(4).toIntOrNull() == year }
        return year to date
    }

    private class Vote<T>(val source: String, val value: T, val weight: Double)
    private class Tally<T>(val value: T, val agreement: Float?, val conflict: String?)

    /** [votes] come in trust order, so the first vote of a group is its preferred spelling. */
    private fun <T : Any> tally(name: String, votes: List<Vote<T>>, same: (T, T) -> Boolean, show: (T) -> String): Tally<T>? {
        if (votes.isEmpty()) return null
        val groups = mutableListOf<MutableList<Vote<T>>>()
        for (vote in votes) {
            groups.firstOrNull { same(it.first().value, vote.value) }?.add(vote) ?: groups.add(mutableListOf(vote))
        }
        var winner = groups.first()
        for (group in groups) if (group.sumOf { it.weight } > winner.sumOf { it.weight } + 1e-9) winner = group
        val agreement = if (votes.size > 1) (winner.sumOf { it.weight } / votes.sumOf { it.weight }).toFloat() else null
        val conflict = if (groups.size > 1) {
            "$name: " + votes.joinToString(", ") { "${it.source} ${show(it.value)}" } + " → " + show(winner.first().value)
        } else null
        return Tally(winner.first().value, agreement, conflict)
    }

    /** How much a source's value counts for a field; 1.0 unless listed. */
    private fun weight(source: String, field: String): Double = when (source) {
        DEEZER -> when (field) {
            "genre" -> 0.8 // Deezer genres belong to the album and are broad.
            "trackNumber", "discNumber" -> 0.9
            else -> 1.0
        }
        ITUNES -> when (field) {
            "genre" -> 0.9
            else -> 1.0
        }
        MUSICBRAINZ -> when (field) {
            "genre" -> 1.2 // Voted on by the MusicBrainz community.
            "isrc" -> 1.2
            // A recording is on many releases; the one picked may not be the album meant.
            "album", "albumArtist", "trackNumber", "discNumber" -> 0.6
            "duration", "artist" -> 0.8
            else -> 1.0
        }
        else -> 0.7
    }

    private fun letters(value: String): String =
        value.lowercase(Locale.ROOT).replace("&", "and").replace(Regex("""[^\p{L}\p{N}]+"""), "")

    internal fun nameKey(value: String): String = letters(value)

    private val ALBUM_NOISE = Regex(
        """\s*(-\s*(single|ep)$|[(\[][^)\]]*(deluxe|edition|remaster|expanded|bonus|anniversary|version)[^)\]]*[)\]])""",
        RegexOption.IGNORE_CASE
    )

    internal fun albumKey(value: String): String = letters(value.replace(ALBUM_NOISE, ""))

    private val GENRE_SYNONYMS = mapOf(
        "hiphop" to "hiphop", "hiphoprap" to "hiphop", "raphiphop" to "hiphop", "rap" to "hiphop", "hiphopandrap" to "hiphop",
        "randb" to "rnb", "rnb" to "rnb", "randbsoul" to "rnb", "rnbsoul" to "rnb", "contemporaryrandb" to "rnb",
        "electronic" to "electronic", "electronica" to "electronic", "electro" to "electronic",
        "latin" to "latin", "latinmusic" to "latin",
        "soundtrack" to "soundtrack", "soundtracks" to "soundtrack", "filmsgames" to "soundtrack", "filmsandgames" to "soundtrack",
        "singersongwriter" to "singersongwriter"
    )

    internal fun genreKey(value: String): String = letters(value).let { GENRE_SYNONYMS[it] ?: it }

    /** MusicBrainz genres are lower case ("hip hop", "r&b"); shown title-cased. */
    fun displayGenre(genre: String): String {
        val special = mapOf("r&b" to "R&B", "edm" to "EDM", "uk" to "UK", "idm" to "IDM", "dj" to "DJ")
        return genre.trim().split(' ').joinToString(" ") { word ->
            special[word.lowercase(Locale.ROOT)] ?: word.split('-').joinToString("-") { part ->
                special[part.lowercase(Locale.ROOT)] ?: part.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        }
    }
}
