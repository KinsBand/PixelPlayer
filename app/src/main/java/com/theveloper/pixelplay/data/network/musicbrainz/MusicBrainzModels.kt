package com.theveloper.pixelplay.data.network.musicbrainz

import com.google.gson.annotations.SerializedName

/**
 * Gson models for the MusicBrainz ws/2 JSON API and the Cover Art Archive API.
 *
 * Every constructor parameter has a default so Kotlin generates a no-arg
 * constructor, which Gson uses (applying the defaults for absent fields).
 * Fields that MusicBrainz frequently omits or sends as JSON null are
 * Kotlin-nullable to stay tolerant of partial responses.
 */

// ─── Recordings (search + lookup share the same shape) ──────────────────────

data class MbRecordingSearchResponse(
    @SerializedName("recordings") val recordings: List<MbRecording> = emptyList(),
    @SerializedName("count") val count: Int = 0
)

data class MbRecording(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    // Only present on search results (0..100 relevance); 0 on lookups.
    @SerializedName("score") val score: Int = 0,
    // Duration in milliseconds; MusicBrainz often sends null / omits it.
    @SerializedName("length") val length: Long? = null,
    // Earliest release date of any release containing the recording ("1975-10-31", "1975").
    @SerializedName("first-release-date") val firstReleaseDate: String? = null,
    @SerializedName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    @SerializedName("releases") val releases: List<MbRelease> = emptyList(),
    @SerializedName("isrcs") val isrcs: List<String> = emptyList(),
    @SerializedName("tags") val tags: List<MbTag> = emptyList(),
    @SerializedName("genres") val genres: List<MbTag> = emptyList(),
    @SerializedName("relations") val relations: List<MbRelation> = emptyList()
) {
    /** Joined display artist from the artist-credit phrase. */
    val artistName: String
        get() = artistCredit.joinToString("") { it.name }.trim()
}

/** Non-MBID lookup by ISRC: every recording carrying the code (usually one). */
data class MbIsrcResponse(
    @SerializedName("isrc") val isrc: String = "",
    @SerializedName("recordings") val recordings: List<MbRecording> = emptyList()
)

data class MbArtistCredit(
    // The credited name as it appears in the artist-credit phrase.
    @SerializedName("name") val name: String = "",
    @SerializedName("artist") val artist: MbArtist? = null
)

data class MbArtist(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = ""
)

/** Used for both `tags` and `genres` arrays (same {name, count} shape). */
data class MbTag(
    @SerializedName("name") val name: String = "",
    @SerializedName("count") val count: Int = 0
)

// ─── Work entity ────────────────────────────────────────────────────────────

data class MbWork(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("language") val language: String? = null,
    @SerializedName("iswcs") val iswcs: List<String> = emptyList(),
    @SerializedName("relations") val relations: List<MbRelation> = emptyList()
)

data class MbRelation(
    @SerializedName("type") val type: String = "",
    @SerializedName("direction") val direction: String? = null,
    @SerializedName("target-type") val targetType: String? = null,
    @SerializedName("artist") val artist: MbArtist? = null,
    @SerializedName("work") val work: MbWork? = null,
    @SerializedName("attributes") val attributes: List<String> = emptyList()
)

// ─── Releases ───────────────────────────────────────────────────────────────

data class MbRelease(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    // Partial ISO date: "1995", "1995-04" or "1995-04-21"; lexicographically comparable.
    @SerializedName("date") val date: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("cover-art-archive") val coverArtArchive: MbCoverArtSummary? = null
)

/** CAA summary block embedded in release data (no extra inc needed). */
data class MbCoverArtSummary(
    @SerializedName("artwork") val artwork: Boolean = false,
    @SerializedName("front") val front: Boolean = false,
    @SerializedName("back") val back: Boolean = false,
    @SerializedName("count") val count: Int = 0
)

// ─── Release lookup (labels + release-group + media) ────────────────────────

data class MbReleaseLookupResponse(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("date") val date: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("barcode") val barcode: String? = null,
    @SerializedName("label-info") val labelInfo: List<MbLabelInfo> = emptyList(),
    @SerializedName("release-group") val releaseGroup: MbReleaseGroup? = null,
    @SerializedName("media") val media: List<MbMedium> = emptyList()
)

data class MbMedium(
    @SerializedName("position") val position: Int = 1,
    @SerializedName("format") val format: String? = null,
    @SerializedName("track-count") val trackCount: Int = 0,
    @SerializedName("tracks") val tracks: List<MbTrack> = emptyList()
)

data class MbTrack(
    @SerializedName("id") val id: String = "",
    @SerializedName("number") val number: String = "",
    @SerializedName("position") val position: Int = 0,
    @SerializedName("title") val title: String = ""
)

data class MbLabelInfo(
    @SerializedName("catalog-number") val catalogNumber: String? = null,
    @SerializedName("label") val label: MbLabel? = null
)

data class MbLabel(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = ""
)

data class MbReleaseGroup(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("primary-type") val primaryType: String? = null,
    @SerializedName("secondary-types") val secondaryTypes: List<String> = emptyList(),
    @SerializedName("first-release-date") val firstReleaseDate: String? = null
)

// ─── Cover Art Archive ──────────────────────────────────────────────────────

data class CaaReleaseResponse(
    @SerializedName("images") val images: List<CaaImage> = emptyList(),
    @SerializedName("release") val release: String? = null
)

data class CaaImage(
    @SerializedName("id") val id: String = "",
    @SerializedName("image") val image: String = "",
    @SerializedName("front") val front: Boolean = false,
    @SerializedName("back") val back: Boolean = false,
    @SerializedName("thumbnails") val thumbnails: CaaThumbnails? = null
)

data class CaaThumbnails(
    @SerializedName("250") val px250: String? = null,
    @SerializedName("500") val px500: String? = null,
    @SerializedName("small") val small: String? = null,
    @SerializedName("large") val large: String? = null,
    @SerializedName("1200") val px1200: String? = null
)

