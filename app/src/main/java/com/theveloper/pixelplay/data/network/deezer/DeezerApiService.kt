package com.theveloper.pixelplay.data.network.deezer

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Deezer API.
 * Used primarily for fetching artist artwork, fan count, related artists, and top tracks.
 */
interface DeezerApiService {

    /**
     * Search for an artist by name.
     * @param query Artist name to search for
     * @param limit Maximum number of results to return
     * @return Search response containing list of matching artists
     */
    @GET("search/artist")
    suspend fun searchArtist(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1
    ): DeezerSearchResponse

    @GET("artist/{id}/related")
    suspend fun getRelatedArtists(
        @Path("id") artistId: Long,
        @Query("limit") limit: Int = 20
    ): DeezerArtistResponse

    @GET("artist/{id}/top")
    suspend fun getArtistTopTracks(
        @Path("id") artistId: Long,
        @Query("limit") limit: Int = 50
    ): DeezerTrackResponse

    /** The artist's discography (albums, singles, EPs, compilations), paged with [index]. */
    @GET("artist/{id}/albums")
    suspend fun getArtistAlbums(
        @Path("id") artistId: Long,
        @Query("limit") limit: Int = 100,
        @Query("index") index: Int = 0
    ): DeezerAlbumResponse

    /** Every track on an album, each with its own popularity `rank`. */
    @GET("album/{id}/tracks")
    suspend fun getAlbumTracks(
        @Path("id") albumId: Long,
        @Query("limit") limit: Int = 200
    ): DeezerTrackResponse
}
