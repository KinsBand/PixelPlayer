package com.theveloper.pixelplay.data.youtube.model

import kotlinx.serialization.Serializable

@Serializable
data class InnerTubeSearchRequest(
    val context: InnerTubeContext,
    val query: String,
    val params: String? = "egWKAQI=" // Filter for Songs in YouTube Music search
)

@Serializable
data class InnerTubeContext(
    val client: InnerTubeClient
)

@Serializable
data class InnerTubeClient(
    val clientName: String = "WEB_REMIX",
    val clientVersion: String = "1.20240101.01.00",
    val hl: String = "en",
    val gl: String = "US"
)
