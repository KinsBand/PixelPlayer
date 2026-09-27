package com.theveloper.pixelplay.data.recognition.ambient

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.spotify.TrackMatching

/** A search result is a candidate, not evidence that the spoken song was found. */
internal object AmbientSongMatcher {
    fun bestMatch(songs: List<Song>, mention: ExtractedSongSuggestion): Song? = songs
        .filter { TrackMatching.normalize(mention.cleanTitle).isNotBlank() &&
            TrackMatching.normalize(mention.cleanTitle) == TrackMatching.normalize(it.title) }
        .filter { (TrackMatching.modifiers(it.title) - TrackMatching.modifiers(mention.cleanTitle)).isEmpty() }
        .filter { song -> mention.artist?.let { artist ->
            val expected = TrackMatching.normalize(artist)
            expected.isNotBlank() && (expected == TrackMatching.normalize(song.artist) ||
                song.artist.split(Regex(",| & | feat[.]? ", RegexOption.IGNORE_CASE))
                    .any { TrackMatching.normalize(it) == expected })
        } ?: true }
        .maxByOrNull { if (TrackMatching.normalize(it.title) == TrackMatching.normalize(mention.cleanTitle)) 1 else 0 }
}
