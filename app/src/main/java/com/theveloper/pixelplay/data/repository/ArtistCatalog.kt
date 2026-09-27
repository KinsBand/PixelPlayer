package com.theveloper.pixelplay.data.repository

import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.model.Song
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Sort orders offered by the artist page's song list. */
enum class ArtistSongSort(val label: String) {
    POPULAR("Popular"),
    NEWEST("Newest"),
    YOUR_PLAYS("Your plays"),
    A_Z("A–Z")
}

/**
 * One song in an artist's catalogue. Different recordings of the same song (Live, Remastered,
 * Radio Edit, the copy on a deluxe edition…) are folded into one track; the others are in [versions].
 */
@Immutable
data class ArtistTrack(
    val song: Song,
    /** 0–100, from Deezer's popularity rank relative to the artist's biggest song. Null = unknown. */
    val popularity: Int? = null,
    val deezerRank: Long? = null,
    /** ISO date, `yyyy-MM-dd` (or just the year when that's all a source gives). */
    val releaseDate: String? = null,
    val versions: List<Song> = emptyList()
) {
    val releaseYear: Int?
        get() = releaseDate?.take(4)?.toIntOrNull()
}

/** Pure catalogue helpers: title keys, version grouping, popularity, sorting and search. */
object ArtistCatalog {

    /** Most tracks an artist page keeps (C4). Sorted by popularity before the cut. */
    const val MAX_TRACKS = 1_000

    private val FEAT = Regex("""(?i)[(\[]\s*(feat\.?|ft\.?|featuring|with)\s[^)\]]*[)\]]|\s(feat\.?|ft\.?|featuring)\s.*$""")
    private val VERSION_WORDS = listOf(
        "remaster", "remastered", "live", "acoustic", "remix", "mix", "radio edit", "edit", "demo",
        "instrumental", "extended", "version", "mono", "stereo", "deluxe", "unplugged", "session",
        "sped up", "slowed", "re-recorded", "taylor's version", "single version", "album version",
        "bonus track", "reprise", "orchestral", "piano"
    )
    private val BRACKETED = Regex("""[(\[]([^)\]]*)[)\]]""")
    private val DASH_SUFFIX = Regex("""\s[-–—]\s(.*)$""")
    private val MARKS = Regex("""\p{Mn}+""")
    private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")

    /** Lowercase, accents stripped, punctuation collapsed: used for search matching. */
    fun fold(text: String): String =
        MARKS.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
            .replace(NON_ALNUM, " ")
            .trim()

    /** Key that tells two copies of the *same recording* apart from other versions. */
    fun exactKey(title: String): String = fold(FEAT.replace(title, "")).replace(" ", "")

    /** The version a title names ("Live", "2011 Remaster"…), or null for the original. */
    fun versionTag(title: String): String? {
        val parts = BRACKETED.findAll(title).map { it.groupValues[1] } +
            (DASH_SUFFIX.find(title)?.groupValues?.get(1)?.let { sequenceOf(it) } ?: emptySequence())
        return parts.map { it.trim() }.firstOrNull { part ->
            val lower = part.lowercase(Locale.ROOT)
            VERSION_WORDS.any { word -> lower.contains(word) }
        }
    }

    /** Key shared by every version of a song. */
    fun baseKey(title: String): String {
        var t = FEAT.replace(title, "")
        t = BRACKETED.replace(t) { match ->
            val inner = match.groupValues[1].lowercase(Locale.ROOT)
            if (VERSION_WORDS.any { inner.contains(it) }) "" else match.value
        }
        DASH_SUFFIX.find(t)?.let { match ->
            val tail = match.groupValues[1].lowercase(Locale.ROOT)
            if (VERSION_WORDS.any { tail.contains(it) }) t = t.substring(0, match.range.first)
        }
        return fold(t).replace(" ", "").ifBlank { exactKey(title) }
    }

    /** Deezer rank → 0–100. Square root spreads the long tail so deep cuts aren't all "1". */
    fun popularity(rank: Long?, maxRank: Long): Int? {
        if (rank == null || rank <= 0L || maxRank <= 0L) return null
        return (100.0 * sqrt(rank.toDouble() / maxRank.toDouble())).roundToInt().coerceIn(1, 100)
    }

    /**
     * Folds versions together (B4). The representative is the original (no version tag) with the
     * highest rank; the group keeps the best popularity and the representative's release date
     * (or the earliest known one).
     */
    fun groupVersions(tracks: List<ArtistTrack>): List<ArtistTrack> {
        val groups = LinkedHashMap<String, MutableList<ArtistTrack>>()
        for (track in tracks) {
            groups.getOrPut(baseKey(track.song.title)) { mutableListOf() } += track
        }
        return groups.values.map { group ->
            if (group.size == 1) return@map group.first()
            val primary = group.sortedWith(
                compareBy<ArtistTrack> { if (versionTag(it.song.title) == null) 0 else 1 }
                    .thenByDescending { it.deezerRank ?: -1L }
                    .thenBy { it.releaseDate ?: "9999" }
            ).first()
            val others = group.filter { it !== primary }
                .distinctBy { exactKey(it.song.title) + "|" + it.song.album.lowercase(Locale.ROOT) }
                .map { it.song }
            primary.copy(
                popularity = group.mapNotNull { it.popularity }.maxOrNull(),
                deezerRank = group.mapNotNull { it.deezerRank }.maxOrNull(),
                releaseDate = primary.releaseDate ?: group.mapNotNull { it.releaseDate }.minOrNull(),
                versions = others
            )
        }
    }

    fun sort(
        tracks: List<ArtistTrack>,
        sort: ArtistSongSort,
        playsByKey: Map<String, Int> = emptyMap()
    ): List<ArtistTrack> {
        val byTitle = compareBy<ArtistTrack> { it.song.title.lowercase(Locale.ROOT) }
        val byPopularity = compareByDescending<ArtistTrack> { it.popularity ?: -1 }
        return when (sort) {
            ArtistSongSort.POPULAR -> tracks.sortedWith(byPopularity.then(compareByDescending { it.releaseDate ?: "" }).then(byTitle))
            ArtistSongSort.NEWEST -> tracks.sortedWith(
                compareBy<ArtistTrack> { if (it.releaseDate == null) 1 else 0 }
                    .then(compareByDescending { it.releaseDate ?: "" })
                    .then(byPopularity)
                    .then(byTitle)
            )
            ArtistSongSort.YOUR_PLAYS -> tracks.sortedWith(
                compareByDescending<ArtistTrack> { playsByKey[baseKey(it.song.title)] ?: 0 }
                    .then(byPopularity)
                    .then(byTitle)
            )
            ArtistSongSort.A_Z -> tracks.sortedWith(byTitle)
        }
    }

    /** Every word of [query] must appear in the title, album or a version's title (accents ignored). */
    fun search(tracks: List<ArtistTrack>, query: String): List<ArtistTrack> {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return tracks
        return tracks.filter { track ->
            val haystack = buildString {
                append(fold(track.song.title)).append(' ')
                append(fold(track.song.album)).append(' ')
                track.versions.forEach { append(fold(it.title)).append(' ') }
                track.releaseYear?.let { append(it) }
            }
            words.all { haystack.contains(it) }
        }
    }
}
