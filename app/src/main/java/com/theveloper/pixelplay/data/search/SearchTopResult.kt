package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.library.RecordingKeys
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.repository.ArtistCatalog
import com.theveloper.pixelplay.data.repository.LyricsLookupMetadata

/**
 * Picks the "Top result" hero card: the artist, song or album whose name matches the query
 * (almost) exactly. Accents, case, punctuation and spacing are ignored; a song or album also
 * matches "title artist" / "artist title". Below [THRESHOLD] there is no top result.
 */
object SearchTopResult {

    const val THRESHOLD = 0.95

    /** Only the first few of each kind are scored: the search already ranks by relevance. */
    private const val MAX_CANDIDATES = 30

    private val SPACES = Regex("""\s+""")
    private val TITLE_SPLIT = Regex("""\s+[-–—]\s+""")

    data class Pick(val item: SearchResultItem, val score: Double)

    /**
     * 1.0 = same text; 0.99 = same once spaces are ignored ("ac dc" / "acdc");
     * 0.97 = same words in another order; otherwise how much of the longer text the shorter
     * one covers when one contains the other (always under the threshold unless nearly equal).
     */
    fun score(query: String, candidate: String): Double {
        val q = ArtistCatalog.fold(query).replace(SPACES, " ")
        val c = ArtistCatalog.fold(candidate).replace(SPACES, " ")
        if (q.isEmpty() || c.isEmpty()) return 0.0
        if (q == c) return 1.0
        val qs = q.replace(" ", "")
        val cs = c.replace(" ", "")
        if (qs == cs) return 0.99
        val qWords = q.split(' ').toSortedSet()
        if (qWords == c.split(' ').toSortedSet()) return 0.97
        val (short, long) = if (qs.length <= cs.length) qs to cs else cs to qs
        return if (long.contains(short)) short.length.toDouble() / long.length else 0.0
    }

    /** Best match among artists, songs and albums in [items], or null below [THRESHOLD]. */
    fun pick(query: String, items: List<SearchResultItem>): Pick? {
        if (query.isBlank()) return null
        var bestArtist: Pick? = null
        var bestSong: Pick? = null
        var bestAlbum: Pick? = null
        var artists = 0
        var songs = 0
        var albums = 0
        for (item in items) {
            when (item) {
                is SearchResultItem.ArtistItem -> if (artists++ < MAX_CANDIDATES) {
                    val s = score(query, item.artist.name)
                    if (s > (bestArtist?.score ?: 0.0)) bestArtist = Pick(item, s)
                }
                is SearchResultItem.SongItem -> if (songs++ < MAX_CANDIDATES) {
                    val s = titleArtistScore(query, item.song.title, item.song.artist)
                    if (s > (bestSong?.score ?: 0.0)) bestSong = Pick(item, s)
                }
                is SearchResultItem.AlbumItem -> if (albums++ < MAX_CANDIDATES) {
                    val s = titleArtistScore(query, item.album.title, item.album.artist)
                    if (s > (bestAlbum?.score ?: 0.0)) bestAlbum = Pick(item, s)
                }
                else -> Unit
            }
        }
        val candidates = listOfNotNull(bestArtist, bestSong, bestAlbum).filter { it.score >= THRESHOLD }
        if (candidates.isEmpty()) return null
        val top = candidates.maxOf { it.score }
        val tied = candidates.filter { it.score >= top - 1e-9 }
        if (tied.size == 1) return tied.first()
        // An exact artist wins a tie only when the results have songs by that artist
        // ("Radiohead"); otherwise the song does ("Creep" also being some band's name).
        val artist = tied.firstOrNull { it.item is SearchResultItem.ArtistItem }
        if (artist != null) {
            val name = RecordingKeys.primaryArtist((artist.item as SearchResultItem.ArtistItem).artist.name)
            val hasSongs = items.any {
                it is SearchResultItem.SongItem && RecordingKeys.primaryArtist(it.song.artist) == name
            }
            if (hasSongs) return artist
        }
        return tied.firstOrNull { it.item is SearchResultItem.SongItem }
            ?: tied.firstOrNull { it.item is SearchResultItem.AlbumItem }
            ?: tied.first()
    }

    private fun titleArtistScore(query: String, rawTitle: String, artist: String): Double {
        var title = runCatching { LyricsLookupMetadata.cleanTitle(rawTitle) }.getOrDefault(rawTitle)
        // "Radiohead - Creep" (a video title) -> "Creep".
        val parts = title.split(TITLE_SPLIT, limit = 2)
        if (parts.size == 2 && ArtistCatalog.fold(parts[0]) == ArtistCatalog.fold(artist)) title = parts[1]
        val main = runCatching { RecordingKeys.primaryArtist(artist) }.getOrDefault(artist)
        return maxOf(
            score(query, title),
            score(query, "$title $main"),
            score(query, "$main $title")
        )
    }
}
