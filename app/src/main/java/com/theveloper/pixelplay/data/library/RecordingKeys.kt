package com.theveloper.pixelplay.data.library

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.LyricsLookupMetadata
import java.util.Locale

/**
 * "Is this the same recording?" keys, so a downloaded copy and the online version you liked
 * (often a different video, with "(Official Video)" in the title or an "Artist - Topic"
 * channel) are recognised as one song.
 *
 * Version words ("Live", "Remix", "Acoustic"…) are kept, so different versions stay apart.
 * Cleaning is applied to every song (a download keeps the raw online title).
 */
object RecordingKeys {
    private val nonAlnum = Regex("""[^\p{L}\p{N}]+""")
    private val artistSplit = Regex("""\s*(?:,|&|;|\bfeat\.?|\bft\.|\bfeaturing\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE)
    private val titleSplit = Regex("""\s+[-–—]\s+""")

    private fun norm(value: String): String =
        nonAlnum.replace(value.lowercase(Locale.ROOT), " ").trim().replace(Regex("\\s+"), " ")

    /** Main artist only: "A, B feat. C" -> "a"; "ArtistVEVO" / "Artist - Topic" -> "artist". */
    fun primaryArtist(artist: String): String {
        val cleaned = runCatching { LyricsLookupMetadata.cleanArtist(artist) }.getOrDefault(artist)
        return norm(cleaned.split(artistSplit).firstOrNull { it.isNotBlank() } ?: cleaned)
    }

    fun of(song: Song): String = of(song.title, song.artist)

    /** Title + main artist after removing video decorations and a leading "Artist - ". */
    fun of(title: String, artist: String): String {
        val mainArtist = primaryArtist(artist)
        var cleanTitle = runCatching { LyricsLookupMetadata.cleanTitle(title) }.getOrDefault(title)
        val parts = cleanTitle.split(titleSplit, limit = 2)
        if (parts.size == 2) {
            val left = norm(parts[0])
            if (left.isNotBlank() && mainArtist.isNotBlank() && (left == mainArtist || left.contains(mainArtist) || mainArtist.contains(left))) {
                cleanTitle = parts[1]
            }
        }
        return norm(AlbumTracklistRepository.normalizeTrack(cleanTitle)) + "|" + mainArtist
    }
}
