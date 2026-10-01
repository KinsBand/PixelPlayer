package com.theveloper.pixelplay.data.spotify.web

import com.theveloper.pixelplay.data.accounts.AccountVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted GraphQL queries the web player uses. Spotify rotates these hashes every few months;
 * when one stops working, [SpotifyPathfinder] reads the current one from the web player and
 * remembers it, so only this table ever needs a manual update.
 */
object SpotifyQueries {
    const val LIBRARY = "libraryV3"
    const val LIBRARY_TRACKS = "fetchLibraryTracks"
    const val PLAYLIST = "fetchPlaylist"
    const val PROFILE = "profileAttributes"
    const val TRACK = "getTrack"
    /** No default hash: read from the web player on first use. */
    const val SEARCH_TRACKS = "searchTracks"
    const val ADD_TO_PLAYLIST = "addToPlaylist"

    val defaults = mapOf(
        LIBRARY to "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455",
        LIBRARY_TRACKS to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
        PLAYLIST to "cd2275433b29f7316176e7b5b5e098ae7744724e1a52d63549c76636b3257749",
        PROFILE to "53bcb064f6cd18c23f752bc324a791194d20df612d8e1239c735144ab0399ced",
        TRACK to "612585ae06ba435ad26369870deaae23b5c8800a256cd8a57e08eddc25a37294"
    )
}

/** Authenticated calls to Spotify's web-player APIs (GraphQL "pathfinder" and spclient REST). */
@Singleton
class SpotifyPathfinder @Inject constructor(
    private val session: SpotifyWebSession,
    private val bundle: SpotifyWebBundle,
    private val vault: AccountVault,
    baseHttp: OkHttpClient
) {
    private val http = com.theveloper.pixelplay.data.network.BrowserHttp.from(baseHttp)

    companion object {
        const val QUERY_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
        private val JSON = "application/json".toMediaType()
    }

    /** Runs a persisted query and returns its `data` object. */
    suspend fun query(operation: String, variables: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        var hash = vault.get("spotify.web.hash.$operation").ifBlank { SpotifyQueries.defaults[operation].orEmpty() }
        var healed = false
        if (hash.isBlank()) {
            // Operations without a built-in hash: look it up in the web player right away.
            healed = true
            hash = bundle.queryHash(operation)
                ?: throw SpotifyWebException("Spotify changed its web API ($operation). An app update may be needed.")
            vault.put("spotify.web.hash.$operation" to hash)
        }
        while (true) {
            val body = JSONObject()
                .put("variables", variables)
                .put("operationName", operation)
                .put("extensions", JSONObject().put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", hash)))
            val json = try {
                JSONObject(send("POST", QUERY_URL, body.toString()))
            } catch (e: SpotifyWebException) {
                // Old hashes are sometimes answered with a 400/404 instead of a GraphQL error.
                if (!healed && (e.status == 400 || e.status == 404)) null else throw e
            }
            val notFound = json == null || json.optJSONArray("errors")?.let { errors ->
                (0 until errors.length()).any { i -> errors.optJSONObject(i)?.optString("message").orEmpty().contains("PersistedQueryNotFound", true) }
            } == true
            if (notFound && !healed) {
                healed = true
                val fresh = bundle.queryHash(operation)
                    ?: throw SpotifyWebException("Spotify changed its web API ($operation). An app update may be needed.")
                vault.put("spotify.web.hash.$operation" to fresh)
                hash = fresh
                continue
            }
            if (json == null) throw SpotifyWebException("Spotify changed its web API ($operation). An app update may be needed.")
            return@withContext json.optJSONObject("data")
                ?: throw SpotifyWebException(json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.ifBlank { null }
                    ?: "Spotify returned no data for $operation.")
        }
        JSONObject() // not reached: the loop above always returns or throws
    }

    /** GET/POST to spclient-style REST endpoints with the web token. Returns the body text. */
    suspend fun rest(method: String, url: String, jsonBody: String? = null): String = withContext(Dispatchers.IO) {
        send(method, url, jsonBody)
    }

    /** One request; refreshes the token once on 401 and backs off once on 429. */
    private suspend fun send(method: String, url: String, jsonBody: String?): String {
        var refreshed = false
        var waited = false
        while (true) {
            val token = session.token(force = refreshed)
            val builder = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("App-Platform", "WebPlayer")
                .header("User-Agent", SpotifyWebBundle.USER_AGENT)
                .header("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
            session.clientId.takeIf { it.isNotBlank() }?.let { builder.header("Client-Id", it) }
            when (method) {
                "POST" -> builder.post((jsonBody ?: "{}").toRequestBody(JSON))
                else -> builder.get()
            }
            val result = http.newCall(builder.build()).execute().use { response ->
                when {
                    response.code == 401 && !refreshed -> null
                    response.code == 429 && !waited -> {
                        val retryAfter = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 10) ?: 3
                        -retryAfter
                    }
                    !response.isSuccessful -> throw SpotifyWebException(when (response.code) {
                        401 -> "Spotify sign-in expired. Reconnect Spotify in Accounts."
                        403 -> "Spotify didn't allow this request."
                        404 -> "Spotify couldn't find that."
                        429 -> "Spotify is rate-limiting requests. Try again in a minute."
                        else -> "Spotify request failed (${response.code})."
                    }, response.code)
                    else -> response.body.string()
                }
            }
            when (result) {
                null -> { refreshed = true }
                is Long -> { waited = true; delay(-result * 1000) }
                is String -> return result
            }
        }
    }
}
