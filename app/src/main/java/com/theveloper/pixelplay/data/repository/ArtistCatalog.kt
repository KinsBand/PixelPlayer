package com.theveloper.pixelplay.data.repository

import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.model.Song
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Sort orders offered by the artist page's song list. Labels are string resources in the UI
 * (`ArtistSongSort.labelRes()`). ALBUM groups songs that share a cover / album, newest first.
 */
enum class ArtistSongSort {
    POPULAR,
    NEWEST,
    ALBUM,
    /** Shown as "Plays": only songs you've played, most played first. */
    YOUR_PLAYS,
    A_Z
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

    /** A size segment at the end of a cover URL ("…/250x250-000000-80-0-0.jpg", "…/100x100bb.jpg"). */
    private val COVER_SIZE_SEGMENT = Regex("""/\d{2,4}x\d{2,4}[^/]*$""")

    /**
     * What groups songs for the Album sort: the cover (same picture = same release, so standard and
     * deluxe editions with one cover stay together), else the album title, else "" (ungrouped, last).
     * Size variants of one cover URL map to the same key.
     */
    fun albumKey(track: ArtistTrack): String {
        val cover = track.song.albumArtUriString?.trim().orEmpty()
        if (cover.isNotEmpty()) return "cover:" + COVER_SIZE_SEGMENT.replace(cover.substringBefore('?'), "")
        val album = fold(track.song.album)
        return if (album.isEmpty()) "" else "album:$album"
    }

    /**
     * Primary key with unknown values always last, whichever way it's ordered. [descending] is the
     * effective direction (natural direction already flipped by the caller when reversed).
     */
    private fun <K : Comparable<K>> keyed(
        descending: Boolean,
        unknown: (ArtistTrack) -> Boolean,
        key: (ArtistTrack) -> K
    ): Comparator<ArtistTrack> = Comparator { a, b ->
        val ua = unknown(a)
        val ub = unknown(b)
        when {
            ua && ub -> 0
            ua -> 1
            ub -> -1
            descending -> key(b).compareTo(key(a))
            else -> key(a).compareTo(key(b))
        }
    }

    /**
     * Sorts the song list. [reversed] flips only the primary key of [sort] (its natural order is
     * Popular high→low, Newest new→old, Album newest album first, Plays most→least, A–Z A→Z); songs
     * whose primary value is unknown stay at the end both ways. Album: the albums' order flips,
     * songs inside an album stay in track order.
     */
    fun sort(
        tracks: List<ArtistTrack>,
        sort: ArtistSongSort,
        playsByKey: Map<String, Int> = emptyMap(),
        reversed: Boolean = false
    ): List<ArtistTrack> {
        val byTitle = compareBy<ArtistTrack> { it.song.title.lowercase(Locale.ROOT) }
        val byPopularity = compareByDescending<ArtistTrack> { it.popularity ?: -1 }
        return when (sort) {
            ArtistSongSort.POPULAR -> tracks.sortedWith(
                keyed(!reversed, { it.popularity == null }, { it.popularity ?: 0 })
                    .then(compareByDescending { it.releaseDate ?: "" })
                    .then(byTitle)
            )
            ArtistSongSort.NEWEST -> tracks.sortedWith(
                keyed(!reversed, { it.releaseDate == null }, { it.releaseDate.orEmpty() })
                    .then(byPopularity)
                    .then(byTitle)
            )
            ArtistSongSort.ALBUM -> sortByAlbum(tracks, reversed, byTitle)
            ArtistSongSort.YOUR_PLAYS -> tracks.sortedWith(
                keyed(!reversed, { false }, { playsByKey[baseKey(it.song.title)] ?: 0 })
                    .then(byPopularity)
                    .then(byTitle)
            )
            ArtistSongSort.A_Z -> tracks.sortedWith(
                keyed(reversed, { false }, { it.song.title.lowercase(Locale.ROOT) })
            )
        }
    }

    private fun sortByAlbum(tracks: List<ArtistTrack>, reversed: Boolean, byTitle: Comparator<ArtistTrack>): List<ArtistTrack> {
        val groups = LinkedHashMap<String, MutableList<ArtistTrack>>()
        for (track in tracks) groups.getOrPut(albumKey(track)) { mutableListOf() } += track
        val newest = groups.mapValues { (_, group) -> group.mapNotNull { it.releaseDate }.maxOrNull() }
        val order = groups.keys.sortedWith(Comparator { a, b ->
            // The ungrouped "" bucket is always last; then groups without a date; then by date.
            val ga = if (a.isEmpty()) 2 else if (newest[a] == null) 1 else 0
            val gb = if (b.isEmpty()) 2 else if (newest[b] == null) 1 else 0
            if (ga != gb) return@Comparator ga - gb
            val da = newest[a].orEmpty()
            val db = newest[b].orEmpty()
            val byDate = if (reversed) da.compareTo(db) else db.compareTo(da)
            if (byDate != 0) byDate else a.compareTo(b)
        })
        val inAlbum = compareBy<ArtistTrack> { it.song.discNumber ?: 1 }
            .thenBy { if (it.song.trackNumber > 0) 0 else 1 }
            .thenBy { it.song.trackNumber }
            .then(byTitle)
        return order.flatMap { key -> groups.getValue(key).sortedWith(inAlbum) }
    }

    /** Where each album group starts in an Album-sorted list (index → group key), for group headers. */
    fun albumGroupStarts(sorted: List<ArtistTrack>): Map<Int, String> {
        val starts = LinkedHashMap<Int, String>()
        var previous: String? = null
        sorted.forEachIndexed { index, track ->
            val key = albumKey(track)
            if (key != previous) starts[index] = key
            previous = key
        }
        return starts
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
