package com.theveloper.pixelplay.data.network.deezer

import com.google.gson.annotations.SerializedName

/**
 * Response from Deezer artist search API.
 */
data class DeezerSearchResponse(
    @SerializedName("data") val data: List<DeezerArtist> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

/**
 * Artist data from Deezer API.
 * Contains multiple image sizes for different use cases.
 */
data class DeezerArtist(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("picture") val picture: String? = null,
    @SerializedName("picture_small") val pictureSmall: String? = null,
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_big") val pictureBig: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null,
    @SerializedName("nb_album") val albumCount: Int = 0,
    @SerializedName("nb_fan") val fanCount: Int = 0
)

data class DeezerTrack(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("rank") val rank: Long = 0L,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("isrc") val isrc: String? = null,
    @SerializedName("track_position") val trackPosition: Int = 0,
    @SerializedName("disk_number") val diskNumber: Int = 0,
    @SerializedName("explicit_lyrics") val explicitLyrics: Boolean = false,
    @SerializedName("artist") val artist: DeezerArtistRef? = null,
    @SerializedName("album") val album: DeezerAlbum? = null
)

data class DeezerArtistRef(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("name") val name: String = ""
)

/** Album from `artist/{id}/albums` (or nested in a track). */
data class DeezerAlbum(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("title") val title: String = "",
    @SerializedName("cover_medium") val coverMedium: String? = null,
    @SerializedName("cover_big") val coverBig: String? = null,
    @SerializedName("cover_xl") val coverXl: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    /** "album", "single", "ep" or "compile". */
    @SerializedName("record_type") val recordType: String? = null,
    @SerializedName("fans") val fans: Long = 0L
)

data class DeezerAlbumResponse(
    @SerializedName("data") val data: List<DeezerAlbum> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("next") val next: String? = null
)

data class DeezerTrackResponse(
    @SerializedName("data") val data: List<DeezerTrack> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

data class DeezerArtistResponse(
    @SerializedName("data") val data: List<DeezerArtist> = emptyList(),
    @SerializedName("total") val total: Int = 0
)
