package com.theveloper.pixelplay.data.database

import kotlinx.serialization.Serializable

enum class ProvenanceSource {
    USER_EDITED,
    EMBEDDED,
    API
}

@Serializable
data class MetadataProvenance(
    val field: String,
    val source: ProvenanceSource,
    val timestamp: Long
)
