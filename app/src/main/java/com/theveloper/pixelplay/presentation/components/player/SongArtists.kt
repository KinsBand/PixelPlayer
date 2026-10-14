package com.theveloper.pixelplay.presentation.components.player

import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song

/** Separators in an artist line ("A, B & C feat. D"). Plain text splits: no Regex per call. */
private val ARTIST_SEPARATORS = arrayOf(",", "/", "&", ";", " feat. ", " ft. ", " with ")

/** The names in a song's artist line, trimmed, without blanks, "<unknown>" or repeats. */
fun parsedArtistNames(displayArtist: String): List<String> =
    displayArtist.split(*ARTIST_SEPARATORS)
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "<unknown>" }
        .distinct()

/**
 * Artists offered by the multi-artist picker for [song]: [resolved] library artists when there are
 * any; otherwise (online songs usually have no library entries) the names parsed from the artist
 * line, but only when there are 2+ of them. Empty = open the single artist directly.
 */
fun pickerArtistsFor(song: Song, resolved: List<Artist>): List<Artist> =
    resolved.ifEmpty {
        val names = parsedArtistNames(song.displayArtist)
        if (names.size > 1) names.map { Artist(id = it.hashCode().toLong(), name = it, songCount = 0) } else emptyList()
    }
