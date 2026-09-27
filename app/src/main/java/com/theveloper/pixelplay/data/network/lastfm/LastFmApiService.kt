package com.theveloper.pixelplay.data.network.lastfm

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the Last.fm API.
 * Base URL: https://ws.audioscrobbler.com/
 */
interface LastFmApiService {

    @GET("2.0/")
    suspend fun trackGetInfo(
        @Query("method") method: String = "track.getInfo",
        @Query("api_key") apiKey: String,
        @Query("artist") artist: String,
        @Query("track") track: String,
        @Query("format") format: String = "json"
    ): LastFmTrackResponse

    @GET("2.0/")
    suspend fun artistGetInfo(
        @Query("method") method: String = "artist.getInfo",
        @Query("api_key") apiKey: String,
        @Query("artist") artist: String,
        @Query("format") format: String = "json"
    ): LastFmArtistResponse
}
