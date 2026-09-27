package com.theveloper.pixelplay.data.songsterr.model

import com.google.gson.annotations.SerializedName

data class SongsterrSong(
    @SerializedName("songId") val songId: Long,
    @SerializedName("artistId") val artistId: Long,
    @SerializedName("artist") val artist: String,
    @SerializedName("title") val title: String,
    @SerializedName("hasChords") val hasChords: Boolean = false,
    @SerializedName("hasPlayer") val hasPlayer: Boolean = false,
    @SerializedName("tracks") val tracks: List<SongsterrTrack> = emptyList(),
    @SerializedName("defaultTrack") val defaultTrack: Int = 0,
    @SerializedName("popularTrack") val popularTrack: Int = 0,
    @SerializedName("isJunk") val isJunk: Boolean = false,
)

data class SongsterrTrack(
    @SerializedName("instrumentId") val instrumentId: Int = 0,
    @SerializedName("instrument") val instrument: String = "",
    @SerializedName("views") val views: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("tuning") val tuning: List<Int> = emptyList(),
    @SerializedName("difficulty") val difficulty: Int? = null,
    @SerializedName("hash") val hash: String = "",
    // partId is the track identifier used in the CloudFront CDN URL — distinct from the list index
    @SerializedName("partId") val partId: Int? = null,
)
