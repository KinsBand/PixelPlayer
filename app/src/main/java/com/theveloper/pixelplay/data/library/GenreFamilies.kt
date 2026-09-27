package com.theveloper.pixelplay.data.library

import com.theveloper.pixelplay.data.GenreTaxonomy
import java.util.Locale

/**
 * The one place that turns a raw genre string into the tags and the genre family the Library
 * shows. The Genres cards, the genre pages, the subgenre chips, the genre DNA bar and the
 * listening shelves all go through here, so a song always lands in the same family everywhere,
 * whether it is a local file, a download or a streamed like.
 *
 * Rules (decided 2026-09-26):
 * - A song belongs to the family of its **first usable tag** only ("Rock, Pop" is Rock).
 * - A tag the taxonomy doesn't know goes to the closest family, else to [OTHER].
 * - No usable tag at all (blank, "Unknown", "Other", "YouTube Music"…) means unknown (null).
 */
object GenreFamilies {
    /** Family id for tags that match no known family. */
    const val OTHER = "other"

    /** Separators between tags: comma, semicolon, slash, pipe and the ID3v2.4 NUL. */
    private val SEPARATORS = Regex("\\s*[,;/|\\u0000]\\s*")
    private val ID3_CODE = Regex("^\\((\\d{1,3})\\)\\s*(.*)$")
    private val DIGITS = Regex("^\\d{1,3}$")
    private val SPACES = Regex("\\s+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Placeholders that say nothing about the music. */
    private val JUNK = setOf(
        "unknown", "unknown genre", "unknow", "other", "others", "genre", "genres", "misc", "miscellaneous",
        "none", "n a", "na", "null", "undefined", "default", "various", "youtube music", "youtube", "music",
        "spotify", "apple music", "soundcloud"
    )

    /** ID3v1 genre codes ("(17)" or "17" means Rock), including the Winamp extensions. */
    private val ID3V1 = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
        "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
        "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk",
        "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
        "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
        "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
        "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes",
        "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
        "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebop", "Latin", "Revival", "Celtic", "Bluegrass",
        "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic",
        "Humour", "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove",
        "Satire", "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle",
        "Duet", "Punk Rock", "Drum Solo", "A Cappella", "Euro-House", "Dance Hall"
    )

    private val tagMemo = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private const val MAX_MEMO = 4_096

    /** Lower-case key used to group spellings of the same tag ("Hip-Hop" == "hip hop"). */
    fun tagKey(tag: String): String =
        tag.lowercase(Locale.ROOT).replace("&", " and ").replace(NON_WORD, " ").trim().replace(SPACES, " ")

    /**
     * The usable tags of a raw genre string, in order, cleaned for display.
     * A compound store label that is itself a known genre ("Hip-Hop/Rap", "Pop/Rock",
     * "R&B/Soul") stays one tag instead of being split on the slash.
     */
    fun tags(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        tagMemo[raw]?.let { return it }
        val trimmed = raw.trim()
        val result = if (!trimmed.contains(',') && !trimmed.contains(';') && GenreTaxonomy.matchExact(trimmed) != null) {
            listOfNotNull(clean(trimmed))
        } else {
            trimmed.split(SEPARATORS).mapNotNull { clean(it) }.distinctBy { tagKey(it) }
        }
        if (tagMemo.size >= MAX_MEMO) tagMemo.clear()
        tagMemo[raw] = result
        return result
    }

    /** The first usable tag, which decides the family. */
    fun primaryTag(raw: String?): String? = tags(raw).firstOrNull()

    /** Family id of a raw genre string, [OTHER] for unknown tags, null when there is no usable tag. */
    fun familyOf(raw: String?): String? {
        val tag = primaryTag(raw) ?: return null
        return GenreTaxonomy.match(tag)?.family ?: OTHER
    }

    fun familyLabel(family: String): String = when (family) {
        OTHER -> "Other"
        "soul" -> "R&B & Soul"
        else -> GenreTaxonomy.genres.firstOrNull { it.id == family }?.label
            ?: family.split('_').joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.ROOT) } }
    }

    private fun clean(part: String): String? {
        var tag = part.trim().trim('"', '\'', '[', ']').replace(SPACES, " ")
        if (tag.isEmpty()) return null
        ID3_CODE.matchEntire(tag)?.let { match ->
            val rest = match.groupValues[2].trim()
            tag = rest.ifEmpty { ID3V1.getOrNull(match.groupValues[1].toInt()) ?: return null }
        }
        if (DIGITS.matches(tag)) tag = ID3V1.getOrNull(tag.toInt()) ?: return null
        if (tagKey(tag) in JUNK || tagKey(tag).isEmpty()) return null
        return tag
    }
}
