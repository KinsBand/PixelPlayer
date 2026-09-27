package com.theveloper.pixelplay.data.network.lastfm

import com.google.gson.annotations.SerializedName

/**
 * Gson models for the Last.fm API (ws.audioscrobbler.com/2.0/).
 *
 * All constructor parameters have defaults so Gson uses the generated
 * no-arg constructor and tolerates absent fields. Last.fm returns
 * numbers as JSON strings (listeners/playcount) and error payloads as
 * `{"error": N, "message": "..."}` with HTTP 200, so top-level models
 * carry the error fields too.
 */

// ─── track.getInfo ──────────────────────────────────────────────────────────

data class LastFmTrackResponse(
    @SerializedName("track") val track: LastFmTrack? = null,
    @SerializedName("error") val error: Int = 0,
    @SerializedName("message") val message: String? = null
)

data class LastFmTrack(
    @SerializedName("name") val name: String = "",
    @SerializedName("artist") val artist: LastFmArtistRef? = null,
    @SerializedName("listeners") val listeners: String? = null,
    @SerializedName("playcount") val playcount: String? = null,
    @SerializedName("toptags") val topTags: LastFmTagList? = null,
    @SerializedName("wiki") val wiki: LastFmWiki? = null
)

data class LastFmArtistRef(
    @SerializedName("name") val name: String = ""
)

data class LastFmTagList(
    @SerializedName("tag") val tags: List<LastFmTag> = emptyList()
)

data class LastFmTag(
    @SerializedName("name") val name: String = "",
    // Only present on track toptags (0..100); absent on artist tags.
    @SerializedName("count") val count: Int = 0
)

data class LastFmWiki(
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("content") val content: String? = null
)

// ─── artist.getInfo ─────────────────────────────────────────────────────────

data class LastFmArtistResponse(
    @SerializedName("artist") val artist: LastFmArtist? = null,
    @SerializedName("error") val error: Int = 0,
    @SerializedName("message") val message: String? = null
)

data class LastFmArtist(
    @SerializedName("name") val name: String = "",
    @SerializedName("tags") val tags: LastFmTagList? = null,
    @SerializedName("similar") val similar: LastFmSimilar? = null,
    @SerializedName("bio") val bio: LastFmBio? = null
)

data class LastFmSimilar(
    @SerializedName("artist") val artists: List<LastFmArtistRef> = emptyList()
)

data class LastFmBio(
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("content") val content: String? = null
)
