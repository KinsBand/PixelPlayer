package com.theveloper.pixelplay.data.network.acoustid

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AcoustIdRecording(
    val id: String, // MusicBrainz Recording ID
    val title: String? = null,
    val artists: List<AcoustIdArtist>? = null
)

@Serializable
data class AcoustIdArtist(
    val id: String,
    val name: String
)

@Serializable
data class AcoustIdResultRecording(
    val id: String,
    val title: String? = null
)

@Serializable
data class AcoustIdResultRecord(
    val id: String,
    val score: Double,
    val recordings: List<AcoustIdRecording> = emptyList()
)

@Serializable
data class AcoustIdResponse(
    val status: String,
    val results: List<AcoustIdResultRecord> = emptyList()
)

@Singleton
class AcoustIdService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookupFingerprint(
        durationSeconds: Int,
        fingerprint: String
    ): String? {
        val url = "https://api.acoustid.org/v2/lookup".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("client", CLIENT_KEY)
            ?.addQueryParameter("meta", "recordings")
            ?.addQueryParameter("duration", durationSeconds.toString())
            ?.addQueryParameter("fingerprint", fingerprint)
            ?.addQueryParameter("format", "json")
            ?.build() ?: return null

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PixelPlay/1.0.0")
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("AcoustID lookup failed with status: %d", response.code)
                    return null
                }
                val bodyString = response.body.string().ifEmpty { return null }
                val result = json.decodeFromString<AcoustIdResponse>(bodyString)
                if (result.status != "ok") {
                    Timber.tag(TAG).w("AcoustID API returned status: %s", result.status)
                    return null
                }
                
                // Return the best match (highest score with a recording MBID)
                result.results
                    .sortedByDescending { it.score }
                    .firstNotNullOfOrNull { record ->
                        record.recordings.firstOrNull()?.id
                    }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error executing AcoustID API call")
            null
        }
    }

    companion object {
        private const val TAG = "AcoustIdService"
        // Public API key for AcoustID API matching.
        private const val CLIENT_KEY = "8XaZGNxH"
    }
}
