package com.theveloper.pixelplay.data.network.ticketmaster

import com.google.gson.annotations.SerializedName

/**
 * Ticketmaster Discovery API v2 responses, only the fields the artist tour card reads.
 * https://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/
 */
data class TmAttractionsResponse(
    @SerializedName("_embedded") val embedded: TmAttractionsEmbedded? = null
)

data class TmAttractionsEmbedded(
    @SerializedName("attractions") val attractions: List<TmAttraction> = emptyList()
)

data class TmAttraction(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("upcomingEvents") val upcomingEvents: Map<String, Any?>? = null,
    @SerializedName("classifications") val classifications: List<TmClassification> = emptyList()
)

data class TmClassification(
    @SerializedName("segment") val segment: TmNamed? = null
)

data class TmNamed(
    @SerializedName("name") val name: String? = null
)

data class TmEventsResponse(
    @SerializedName("_embedded") val embedded: TmEventsEmbedded? = null
)

data class TmEventsEmbedded(
    @SerializedName("events") val events: List<TmEvent> = emptyList()
)

data class TmEvent(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("dates") val dates: TmDates? = null,
    @SerializedName("_embedded") val embedded: TmEventEmbedded? = null
)

data class TmDates(
    @SerializedName("start") val start: TmStart? = null,
    @SerializedName("status") val status: TmStatus? = null
)

data class TmStart(
    @SerializedName("localDate") val localDate: String? = null,
    @SerializedName("localTime") val localTime: String? = null
)

data class TmStatus(
    @SerializedName("code") val code: String? = null
)

data class TmEventEmbedded(
    @SerializedName("venues") val venues: List<TmVenue> = emptyList()
)

data class TmVenue(
    @SerializedName("name") val name: String? = null,
    @SerializedName("city") val city: TmNamed? = null,
    @SerializedName("state") val state: TmNamed? = null,
    @SerializedName("country") val country: TmCountry? = null,
    @SerializedName("location") val location: TmLocation? = null
)

data class TmCountry(
    @SerializedName("name") val name: String? = null,
    @SerializedName("countryCode") val countryCode: String? = null
)

/** Ticketmaster sends coordinates as strings. */
data class TmLocation(
    @SerializedName("latitude") val latitude: String? = null,
    @SerializedName("longitude") val longitude: String? = null
)
