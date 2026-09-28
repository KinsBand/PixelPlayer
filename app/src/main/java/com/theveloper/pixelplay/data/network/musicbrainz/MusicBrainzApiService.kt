package com.theveloper.pixelplay.data.network.musicbrainz

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the MusicBrainz ws/2 JSON API.
 * Base URL: https://musicbrainz.org/
 *
 * MusicBrainz policy: max 1 request/second, descriptive User-Agent required.
 * Both are enforced outside this interface (MusicBrainzRateLimiter + UA
 * interceptor on the default OkHttpClient).
 */
interface MusicBrainzApiService {

    /**
     * Search recordings with a Lucene query, e.g.
     * `recording:"Title" AND artist:"Name"`.
     */
    @GET("ws/2/recording/")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 5
    ): MbRecordingSearchResponse

    /**
     * Lookup a single recording with artist credits, releases, ISRCs,
     * community tags, genres, and work relationships.
     */
    @GET("ws/2/recording/{mbid}")
    suspend fun lookupRecording(
        @Path("mbid") mbid: String,
        @Query("inc") inc: String = "artists+releases+isrcs+tags+genres+work-rels",
        @Query("fmt") format: String = "json"
    ): MbRecording

    /**
     * Recordings carrying an ISRC. No `inc`: the chosen recording is looked up in full with
     * [lookupRecording] anyway.
     */
    @GET("ws/2/isrc/{isrc}")
    suspend fun lookupIsrc(
        @Path("isrc") isrc: String,
        @Query("fmt") format: String = "json"
    ): MbIsrcResponse

    /**
     * Lookup a single release with label info, track listings, media,
     * release groups, and artist credits.
     */
    @GET("ws/2/release/{mbid}")
    suspend fun lookupRelease(
        @Path("mbid") mbid: String,
        @Query("inc") inc: String = "labels+recordings+artist-credits+media+release-groups",
        @Query("fmt") format: String = "json"
    ): MbReleaseLookupResponse

    /**
     * Lookup a single work with artist relationships (composers, lyricists, writers).
     */
    @GET("ws/2/work/{mbid}")
    suspend fun lookupWork(
        @Path("mbid") mbid: String,
        @Query("inc") inc: String = "artist-rels",
        @Query("fmt") format: String = "json"
    ): MbWork
}

