package com.theveloper.pixelplay.data.network.listenbrainz

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query

interface ListenBrainzService {

    @GET("1/similar-recordings/{mbid}")
    suspend fun getSimilarRecordings(
        @Path("mbid") mbid: String,
        @Query("count") count: Int = 10
    ): ListenBrainzSimilarRecordingsResponse

    @POST("1/submit-listens")
    suspend fun submitListen(
        @Header("Authorization") userToken: String,
        @Body payload: ListenBrainzSubmitPayload
    ): ListenBrainzSubmitResponse
}

data class ListenBrainzSimilarRecordingsResponse(
    val mbids: List<SimilarRecordingMbid> = emptyList()
)

data class SimilarRecordingMbid(
    val recording_mbid: String = "",
    val score: Float = 0f
)

data class ListenBrainzSubmitPayload(
    val listen_type: String = "single",
    val payload: List<ListenPayloadItem>
)

data class ListenPayloadItem(
    val listened_at: Long,
    val track_metadata: ListenTrackMetadata
)

data class ListenTrackMetadata(
    val artist_name: String,
    val track_name: String,
    val release_name: String? = null,
    val additional_info: Map<String, String> = emptyMap()
)

data class ListenBrainzSubmitResponse(
    val status: String = ""
)
