package com.theveloper.pixelplay.data.network.ticketmaster

import retrofit2.http.GET
import retrofit2.http.Query

/** Ticketmaster Discovery API v2. The free key allows 5,000 calls a day, so results are cached. */
interface TicketmasterApiService {

    /** Artists ("attractions") matching a name, music only. */
    @GET("discovery/v2/attractions.json")
    suspend fun searchAttractions(
        @Query("keyword") keyword: String,
        @Query("apikey") apiKey: String,
        @Query("classificationName") classification: String = "music",
        @Query("size") size: Int = 10
    ): TmAttractionsResponse

    /** One artist's shows, soonest first. */
    @GET("discovery/v2/events.json")
    suspend fun getEvents(
        @Query("attractionId") attractionId: String,
        @Query("apikey") apiKey: String,
        @Query("startDateTime") startDateTime: String? = null,
        @Query("sort") sort: String = "date,asc",
        @Query("size") size: Int = 200
    ): TmEventsResponse
}
