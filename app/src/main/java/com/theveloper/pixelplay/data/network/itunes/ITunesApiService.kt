package com.theveloper.pixelplay.data.network.itunes

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class ITunesResponse<T>(
    @SerializedName("resultCount") val resultCount: Int = 0,
    @SerializedName("results") val results: List<T> = emptyList()
)

data class ITunesAlbum(
    @SerializedName("collectionId") val collectionId: Long = 0L,
    @SerializedName("collectionName") val collectionName: String = "",
    @SerializedName("artistName") val artistName: String = "",
    @SerializedName("collectionType") val collectionType: String? = null,
    @SerializedName("artworkUrl100") val artworkUrl100: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("trackCount") val trackCount: Int = 0,
    @SerializedName("primaryGenreName") val primaryGenreName: String? = null,
    @SerializedName("copyright") val copyright: String? = null
) {
    /** The 100x100 artwork upgraded to [ArtworkUrls.DEFAULT_SIZE] (Apple serves up to 3000 px). */
    val artworkUrl: String?
        get() = com.theveloper.pixelplay.data.metadata.ArtworkUrls.upgrade(artworkUrl100)
}

data class ITunesSong(
    @SerializedName("trackId") val trackId: Long = 0L,
    @SerializedName("trackName") val trackName: String? = null,
    @SerializedName("artistName") val artistName: String? = null,
    @SerializedName("collectionId") val collectionId: Long? = null,
    @SerializedName("collectionName") val collectionName: String? = null,
    @SerializedName("artworkUrl100") val artworkUrl100: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("trackTimeMillis") val trackTimeMillis: Long? = null,
    @SerializedName("previewUrl") val previewUrl: String? = null,
    @SerializedName("trackNumber") val trackNumber: Int? = null,
    @SerializedName("primaryGenreName") val primaryGenreName: String? = null,
    @SerializedName("wrapperType") val wrapperType: String? = null
) {
    val artworkUrl: String?
        get() = com.theveloper.pixelplay.data.metadata.ArtworkUrls.upgrade(artworkUrl100)
}

interface ITunesApiService {

    @GET("search")
    suspend fun searchAlbums(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 50
    ): ITunesResponse<ITunesAlbum>

    @GET("search")
    suspend fun searchSongs(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 50
    ): ITunesResponse<ITunesSong>

    @GET("lookup")
    suspend fun lookupAlbumTracks(
        @Query("id") collectionId: Long,
        @Query("entity") entity: String = "song"
    ): ITunesResponse<ITunesSong>
}
