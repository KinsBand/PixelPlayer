package com.theveloper.pixelplay.data.network.spotify

import android.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SpotifyTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
)

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyTrackContainer
)

@Serializable
data class SpotifyTrackContainer(
    val items: List<SpotifyTrackItem> = emptyList()
)

@Serializable
data class SpotifyTrackItem(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistRef> = emptyList()
)

@Serializable
data class SpotifyArtistRef(
    val name: String
)

@Serializable
data class SpotifyAudioFeatures(
    val id: String,
    val danceability: Float,
    val energy: Float,
    val key: Int,
    val loudness: Float,
    val speechiness: Float,
    val acousticness: Float,
    val instrumentalness: Float,
    val liveness: Float,
    val valence: Float,
    val tempo: Float
)

@Singleton
class SpotifyService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedToken: String? = null
    private var tokenExpiryTimeMs: Long = 0L

    private suspend fun getAccessToken(): String? {
        val clientId = preferencesRepository.spotifyClientIdFlow.first()
        val clientSecret = preferencesRepository.spotifyClientSecretFlow.first()

        if (clientId.isBlank() || clientSecret.isBlank()) {
            Timber.tag(TAG).d("Spotify Client ID or Secret is empty. Skipping token generation.")
            return null
        }

        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryTimeMs) {
            return cachedToken
        }

        val authHeader = "Basic " + Base64.encodeToString(
            "$clientId:$clientSecret".toByteArray(),
            Base64.NO_WRAP
        )

        val requestBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(requestBody)
            .header("Authorization", authHeader)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).e("Failed to retrieve Spotify token: %d", response.code)
                    return null
                }
                val body = response.body.string().ifEmpty { return null }
                val tokenResponse = json.decodeFromString<SpotifyTokenResponse>(body)
                cachedToken = tokenResponse.access_token
                tokenExpiryTimeMs = System.currentTimeMillis() + (tokenResponse.expires_in * 1000) - 60000 // Buffer 1 min
                cachedToken
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during Spotify authentication")
            null
        }
    }

    suspend fun searchTrackAndGetFeatures(title: String, artist: String): SpotifyAudioFeatures? {
        val token = getAccessToken() ?: return null

        // Step 1: Search for track ID
        val query = "track:$title artist:$artist"
        val searchUrl = "https://api.spotify.com/v1/search".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("type", "track")
            ?.addQueryParameter("limit", "1")
            ?.build() ?: return null

        val searchRequest = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $token")
            .build()

        val trackId = try {
            okHttpClient.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Spotify Search failed: %d", response.code)
                    return null
                }
                val body = response.body.string().ifEmpty { return null }
                val searchResult = json.decodeFromString<SpotifySearchResponse>(body)
                searchResult.tracks.items.firstOrNull()?.id
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error executing Spotify track search")
            null
        } ?: return null

        // Step 2: Get Audio Features
        val featuresUrl = "https://api.spotify.com/v1/audio-features/$trackId"
        val featuresRequest = Request.Builder()
            .url(featuresUrl)
            .header("Authorization", "Bearer $token")
            .build()

        return try {
            okHttpClient.newCall(featuresRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Spotify Audio Features call failed: %d", response.code)
                    return null
                }
                val body = response.body.string().ifEmpty { return null }
                json.decodeFromString<SpotifyAudioFeatures>(body)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching Spotify audio features")
            null
        }
    }

    companion object {
        private const val TAG = "SpotifyService"
    }
}
