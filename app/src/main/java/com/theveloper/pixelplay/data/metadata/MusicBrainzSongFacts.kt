package com.theveloper.pixelplay.data.metadata

import com.theveloper.pixelplay.data.network.musicbrainz.MbRecording
import com.theveloper.pixelplay.data.network.musicbrainz.MbWorkDetails

/** A MusicBrainz recording (and its work), as MusicBrainz's vote in [MetadataConsensus]. */
internal object MusicBrainzSongFacts {
    private const val MAX_TAGS = 15

    /**
     * [releaseMbid] is the release picked for the song's album; [isrc] the code the recording
     * was found by, kept when the recording lists it.
     */
    fun toGathered(
        recording: MbRecording,
        work: MbWorkDetails?,
        releaseMbid: String?,
        isrc: String?,
        now: Long
    ): GatheredMetadata {
        val release = recording.releases.firstOrNull { it.id == releaseMbid }
        // The recording's own first release date, else the earliest release that lists it.
        val firstRelease = recording.firstReleaseDate?.takeIf { it.length >= 4 }
            ?: recording.releases.mapNotNull { it.date?.takeIf { date -> date.length >= 4 } }.minOrNull()
        return GatheredMetadata(
            genre = recording.genres.filter { it.name.isNotBlank() }.maxByOrNull { it.count }?.name
                ?.let(MetadataConsensus::displayGenre),
            album = release?.title?.takeIf(String::isNotBlank),
            artist = recording.artistName.takeIf(String::isNotBlank),
            durationMs = recording.length?.takeIf { it > 0 },
            year = firstRelease?.take(4)?.toIntOrNull(),
            releaseDate = firstRelease,
            isrc = isrc?.takeIf { code -> recording.isrcs.any { it.equals(code, ignoreCase = true) } }
                ?: recording.isrcs.firstOrNull(),
            tags = recording.tags.filter { it.name.isNotBlank() }.sortedByDescending { it.count }
                .map { it.name }.distinct().take(MAX_TAGS),
            composer = work?.composers?.joinToString(", ")?.takeIf(String::isNotBlank),
            lyricist = work?.lyricists?.joinToString(", ")?.takeIf(String::isNotBlank),
            songwriter = work?.songwriters?.joinToString(", ")?.takeIf(String::isNotBlank),
            // "zxx": no lyrics.
            language = work?.language?.takeIf { it.isNotBlank() && it != "zxx" && it != "mul" },
            recordingMbid = recording.id.takeIf(String::isNotBlank),
            releaseMbid = release?.id,
            workMbid = work?.workMbid?.takeIf(String::isNotBlank),
            sources = listOf(MetadataConsensus.MUSICBRAINZ),
            at = now,
            deepAt = now,
            found = true
        )
    }
}
