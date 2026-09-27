package com.theveloper.pixelplay.data.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

enum class TrackSource {
    LOCAL,
    YOUTUBE_MUSIC,
    GDRIVE,
    OTHER
}

enum class DownloadState {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

@Immutable
@Parcelize
data class SongInformation(
    val subtitle: String? = null,
    val featuredArtists: List<String> = emptyList(),
    val releaseType: String? = null,
    val subgenre: String? = null,
    val language: String? = null,
    /**
     * ISO-3166 alpha-2 code of the release territory (plus the MusicBrainz
     * pseudo-codes XW/XE), normalized by [com.theveloper.pixelplay.data.media.ReleaseCountry].
     * Distinct from [language] — a German-language release can be a US release.
     */
    val releaseCountry: String? = null,
    val explicit: Boolean = false,
    val releaseDate: Long? = null,
    val recordingDate: Long? = null
) : Parcelable

@Immutable
@Parcelize
data class MusicalFeatures(
    val bpm: Float? = null,
    val key: String? = null,
    val mode: String? = null,
    val timeSignature: String? = null,
    val chordProgression: String? = null,
    val songStructure: String? = null,
    val tuning: String? = null,
    val capo: Int? = null,
    val leadVocals: List<String> = emptyList(),
    val backingVocals: List<String> = emptyList(),
    val instrumentList: List<String> = emptyList(),
    val syncedLyricsLrc: String? = null,
    val lyricist: String? = null
) : Parcelable

@Immutable
@Parcelize
data class CreditsAndRelease(
    val composer: String? = null,
    val songwriter: String? = null,
    val producer: String? = null,
    val mixingEngineer: String? = null,
    val masteringEngineer: String? = null,
    val recordingEngineer: String? = null,
    val arranger: String? = null,
    val sessionMusicians: List<String> = emptyList(),
    val recordLabel: String? = null,
    val publisher: String? = null,
    val copyright: String? = null,
    val licensing: String? = null,
    val isrc: String? = null,
    val upcEan: String? = null,
    val catalogueNumber: String? = null
) : Parcelable

@Immutable
@Parcelize
data class AudioTech(
    val codec: String? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val loudnessLufs: Float? = null,
    val dynamicRange: Float? = null,
    val replayGain: Float? = null,
    val fileSize: Long? = null,
    val checksum: String? = null
) : Parcelable

@Immutable
@Parcelize
data class UserActivityStats(
    val rating: Int? = null,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long? = null,
    val notes: String? = null,
    val colourLabel: String? = null,
    val tags: List<String> = emptyList()
) : Parcelable

@Immutable
@Parcelize
data class MixIntelligence(
    val mood: String? = null,
    val energy: Float? = null,
    val valence: Float? = null,
    val danceability: Float? = null,
    val acousticness: Float? = null,
    val instrumentalness: Float? = null,
    val liveness: Float? = null,
    val speechiness: Float? = null,
    val loudness: Float? = null,
    val tempoCategory: String? = null,
    val complexity: Float? = null,
    val intensity: Float? = null,
    val groove: Float? = null,
    val aggressiveness: Float? = null,
    val brightness: Float? = null,
    val warmth: Float? = null,
    val vocalPresence: Float? = null,
    val instrumentalFocus: Float? = null,
    val transitionScore: Float? = null,
    val introEnergy: Float? = null,
    val outroEnergy: Float? = null,
    val fadeLength: Long? = null,
    val introLength: Long? = null,
    val outroLength: Long? = null,
    val bestMixInPoint: Long? = null,
    val bestMixOutPoint: Long? = null,
    val beatGrid: String? = null,
    val beatStrength: Float? = null,
    val phraseLength: Int? = null,
    val downbeatPosition: Long? = null,
    val compatibleKeys: List<String> = emptyList(),
    val compatibleBpmRange: String? = null,
    val dynamicRangeCategory: String? = null,
    val silenceAtStart: Long? = null,
    val silenceAtEnd: Long? = null
) : Parcelable

@Immutable
@Parcelize
data class UserPreferences(
    val preferredTimeOfDay: String? = null,
    val preferredWeather: String? = null,
    val preferredActivity: String? = null,
    val preferredSeason: String? = null,
    val preferredVolume: Float? = null,
    val listeningContext: String? = null,
    val repeatFrequency: Int = 0,
    val completionRate: Float? = null,
    val skipPosition: Long? = null,
    val favouriteSections: List<String> = emptyList(),
    val listeningStreak: Int = 0,
    val recentlyOverplayed: Boolean = false,
    val varietyScore: Float? = null,
    val familiarityScore: Float? = null
) : Parcelable

@Immutable
@Parcelize
data class SongRelationships(
    val similarSongs: List<String> = emptyList(),
    val similarArtists: List<Long> = emptyList(),
    val similarAlbums: List<Long> = emptyList(),
    val commonPlaylistPairings: List<String> = emptyList(),
    val frequentlyPlayedBefore: List<String> = emptyList(),
    val frequentlyPlayedAfter: List<String> = emptyList(),
    val relatedGenres: List<String> = emptyList(),
    val relatedMoods: List<String> = emptyList()
) : Parcelable

@Immutable
@Parcelize
data class Song(
    val id: String,
    val title: String,
    /**
     * Legacy artist display string.
     * - With multi-artist parsing enabled by default, this typically contains only the primary artist for backward compatibility.
     * For accurate display of all artists, use the [artists] list and [displayArtist] property.
     */
    val artist: String,
    val artistId: Long, // Primary artist ID for backward compatibility
    val artists: List<ArtistRef> = emptyList(), // All artists for multi-artist support
    val album: String,
    val albumId: Long,
    val albumArtist: String? = null, // Album artist from metadata
    val path: String, // Added for direct file system access
    val contentUriString: String,
    val albumArtUriString: String?,
    val duration: Long,
    val genre: String? = null,
    val lyrics: String? = null,
    val isFavorite: Boolean = false,
    val downloadState: DownloadState = DownloadState.NOT_DOWNLOADED,
    val trackNumber: Int = 0,
    val discNumber: Int? = null,
    val year: Int = 0,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val gdriveFileId: String? = null, // Google Drive file ID
    val youtubeId: String? = null, // YouTube Music video ID
    val explicitSource: TrackSource? = null,
    val songInformation: SongInformation = SongInformation(),
    val musicalFeatures: MusicalFeatures = MusicalFeatures(),
    val creditsAndRelease: CreditsAndRelease = CreditsAndRelease(),
    val audioTech: AudioTech = AudioTech(),
    val userActivityStats: UserActivityStats = UserActivityStats(),
    val mixIntelligence: MixIntelligence = MixIntelligence(),
    val userPreferences: UserPreferences = UserPreferences(),
    val songRelationships: SongRelationships = SongRelationships()
) : Parcelable {

    val source: TrackSource
        get() = explicitSource ?: when {
            youtubeId != null -> TrackSource.YOUTUBE_MUSIC
            gdriveFileId != null -> TrackSource.GDRIVE
            path.startsWith("http://") || path.startsWith("https://") -> TrackSource.OTHER
            else -> TrackSource.LOCAL
        }

    val isLocal: Boolean
        get() = source == TrackSource.LOCAL

    val isDownloaded: Boolean
        get() = downloadState == DownloadState.DOWNLOADED

    val isLocalOrDownloaded: Boolean
        get() = isLocal || isDownloaded
    /**
     * Returns the display string for artists formatted with duration next to it, e.g. "Artist Name · 3:42"
     */
    val displayArtistWithDuration: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val durationStr = "%d:%02d".format(minutes, seconds)
            return "$displayArtist · $durationStr"
        }

    /**
     * Returns the display string for artists formatted with genre next to it, e.g. "Artist Name · Genre".
     * If genre is not available or unknown, falls back to [displayArtist].
     */
    val displayArtistWithGenre: String
        get() {
            val cleanGenre = genre?.trim()?.takeIf {
                it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) && !it.equals("<unknown>", ignoreCase = true)
            }
            return if (!cleanGenre.isNullOrBlank()) {
                if (displayArtist.isNotBlank()) {
                    "$displayArtist · $cleanGenre"
                } else {
                    cleanGenre
                }
            } else {
                displayArtist
            }
        }

    /**
     * Returns the display string for artists.
     * If multiple artists exist (populated during sync), joins them with ", ".
     * Falls back to the raw artist field (splitting is done at sync time, not display time).
     */
    val displayArtist: String
        get() {
            if (artists.isNotEmpty()) {
                return artists.sortedByDescending { it.isPrimary }.joinToString(", ") { it.name }
            }
            return artist
        }

    /**
     * Returns the primary artist from the artists list,
     * or creates one from the legacy artist field.
     */
    val primaryArtist: ArtistRef
        get() = artists.find { it.isPrimary }

            ?: artists.firstOrNull()
            ?: ArtistRef(id = artistId, name = artist, isPrimary = true)

    companion object {
        fun emptySong(): Song {
            return Song(
                id = "-1",
                title = "",
                artist = "",
                artistId = -1L,
                artists = emptyList(),
                album = "",
                albumId = -1L,
                albumArtist = null,
                path = "",
                contentUriString = "",
                albumArtUriString = null,
                duration = 0L,
                genre = null,
                lyrics = null,
                isFavorite = false,
                downloadState = DownloadState.NOT_DOWNLOADED,
                trackNumber = 0,
                discNumber = null,
                year = 0,
                dateAdded = 0,
                dateModified = 0,
                mimeType = "-",
                bitrate = 0,
                sampleRate = 0,
                gdriveFileId = null,
                youtubeId = null,
                songInformation = SongInformation(),
                musicalFeatures = MusicalFeatures(),
                creditsAndRelease = CreditsAndRelease(),
                audioTech = AudioTech(),
                userActivityStats = UserActivityStats(),
                mixIntelligence = MixIntelligence(),
                userPreferences = UserPreferences(),
                songRelationships = SongRelationships()
            )
        }
    }
}
