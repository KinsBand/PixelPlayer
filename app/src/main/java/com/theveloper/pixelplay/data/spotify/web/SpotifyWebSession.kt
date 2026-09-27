package com.theveloper.pixelplay.data.spotify.web

import com.theveloper.pixelplay.data.accounts.AccountVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class SpotifyWebException(message: String, val status: Int = 0) : IOException(message)

/**
 * Spotify connection that works like the web player: the `sp_dc` cookie from a normal
 * sign-in is traded for a short-lived web-player token. No developer app is needed, and it
 * unlocks friends' activity and any public playlist.
 *
 * Everything lives in [AccountVault] under `spotify.web.`, so the existing Spotify logout
 * (which clears `spotify.`) signs this out too.
 */
@Singleton
class SpotifyWebSession @Inject constructor(
    private val vault: AccountVault,
    baseHttp: OkHttpClient,
    private val bundle: SpotifyWebBundle
) {
    /** Browser-like client: the shared one replaces the User-Agent, which Spotify rejects. */
    private val http = com.theveloper.pixelplay.data.network.BrowserHttp.from(baseHttp)

    companion object {
        const val KEY_SP_DC = "spotify.web.spdc"
        private const val KEY_TOKEN = "spotify.web.token"
        private const val KEY_EXPIRY = "spotify.web.expiry"
        private const val KEY_CLIENT_ID = "spotify.web.clientId"
        private const val KEY_SECRET_VERSION = "spotify.web.totpVersion"
        private const val KEY_SECRET = "spotify.web.totpSecret"
        const val KEY_USERNAME = "spotify.web.username"
        const val KEY_DISPLAY_NAME = "spotify.web.displayName"
        private const val TOKEN_URL = "https://open.spotify.com/api/token"

        /** Accepts the bare value, `sp_dc=…`, or a whole Cookie header / cookie string. */
        fun parseSpDc(input: String): String {
            val trimmed = input.trim().removePrefix("Cookie:").trim()
            val fromPairs = trimmed.split(';').map { it.trim() }
                .firstOrNull { it.startsWith("sp_dc=") }?.removePrefix("sp_dc=")
            val value = (fromPairs ?: trimmed).trim().trim('"')
            require(value.length >= 40 && value.none { it.isWhitespace() || it == ';' }) {
                "That doesn't look like an sp_dc cookie (it's usually 150+ characters). Copy the Value of the sp_dc row " +
                    "from open.spotify.com → Developer Tools → Application → Cookies."
            }
            return value
        }
    }

    private val mutex = Mutex()

    val isLoggedInFlow = vault.revision.map { vault.get(KEY_SP_DC).isNotBlank() }.distinctUntilChanged()
    val hasSession: Boolean get() = vault.get(KEY_SP_DC).isNotBlank()
    val clientId: String get() = vault.get(KEY_CLIENT_ID)
    val username: String get() = vault.get(KEY_USERNAME)

    /** Validates [spDc] by fetching a signed-in token, then stores it. */
    suspend fun connect(spDc: String) = withContext(Dispatchers.IO) {
        val value = parseSpDc(spDc)
        mutex.withLock {
            val token = fetchToken(value)
            vault.put(KEY_SP_DC to value, KEY_TOKEN to token.accessToken, KEY_EXPIRY to token.expiresAt.toString(),
                KEY_CLIENT_ID to token.clientId)
        }
    }

    fun rememberProfile(username: String, displayName: String) {
        vault.put(KEY_USERNAME to username, KEY_DISPLAY_NAME to displayName)
    }

    /** A valid web-player token, refreshed a minute before it expires (or now, with [force]). */
    suspend fun token(force: Boolean = false): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val spDc = vault.get(KEY_SP_DC)
            if (spDc.isBlank()) throw SpotifyWebException("Spotify isn't connected. Sign in again in Accounts.", 401)
            val cached = vault.get(KEY_TOKEN)
            val expiry = vault.get(KEY_EXPIRY).toLongOrNull() ?: 0L
            if (!force && cached.isNotBlank() && System.currentTimeMillis() < expiry - 60_000) return@withLock cached
            val token = fetchToken(spDc)
            vault.put(KEY_TOKEN to token.accessToken, KEY_EXPIRY to token.expiresAt.toString(), KEY_CLIENT_ID to token.clientId)
            token.accessToken
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        mutex.withLock { vault.clear("spotify.web.") }
    }

    private data class WebToken(val accessToken: String, val expiresAt: Long, val clientId: String)

    /** Tries the stored secret, then the bundled one, then whatever the live web player uses now. */
    private suspend fun fetchToken(spDc: String): WebToken {
        val candidates = linkedMapOf<Int, String>()
        vault.get(KEY_SECRET_VERSION).toIntOrNull()?.let { v -> vault.get(KEY_SECRET).takeIf { it.isNotEmpty() }?.let { candidates[v] = it } }
        candidates.putIfAbsent(SpotifyTotp.BUNDLED_VERSION, SpotifyTotp.BUNDLED_SECRET)
        var lastError: Exception? = null
        for ((version, secret) in candidates) {
            try { return requestToken(spDc, version, secret) }
            catch (e: SpotifyWebException) { if (e.status == 401) throw e; lastError = e }
            catch (e: IOException) { lastError = e }
        }
        // Spotify rotated the secret: read the current one from the web player and remember it.
        val fresh = runCatching { bundle.latestSecret() }.getOrNull()
        if (fresh != null && candidates[fresh.first] != fresh.second) {
            val token = requestToken(spDc, fresh.first, fresh.second)
            vault.put(KEY_SECRET_VERSION to fresh.first.toString(), KEY_SECRET to fresh.second)
            return token
        }
        throw lastError ?: SpotifyWebException("Spotify sign-in failed. Try again in a moment.")
    }

    private fun requestToken(spDc: String, version: Int, secret: String): WebToken {
        val code = SpotifyTotp.code(secret, serverTimeSeconds())
        var failure: SpotifyWebException? = null
        for (reason in listOf("transport", "init")) {
            val url = TOKEN_URL.toHttpUrl().newBuilder()
                .addQueryParameter("reason", reason)
                .addQueryParameter("productType", "web-player")
                .addQueryParameter("totp", code)
                .addQueryParameter("totpServer", code)
                .addQueryParameter("totpVer", version.toString())
                .build()
            val request = Request.Builder().url(url)
                .header("User-Agent", SpotifyWebBundle.USER_AGENT)
                .header("Accept", "application/json")
                .header("App-Platform", "WebPlayer")
                .header("Referer", "https://open.spotify.com/")
                .header("Cookie", "sp_dc=$spDc")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = runCatching { response.body.string().take(200) }.getOrDefault("")
                        .let { body -> runCatching { JSONObject(body).optJSONObject("error")?.optString("message") ?: JSONObject(body).optString("error") }.getOrNull() ?: body }
                        .ifBlank { "no details" }
                    failure = SpotifyWebException("Spotify refused the sign-in request (${response.code}: $detail).", response.code)
                    return@use
                }
                val json = JSONObject(response.body.string())
                val token = json.optString("accessToken")
                if (token.isBlank()) { failure = SpotifyWebException("Spotify returned no token."); return@use }
                // An anonymous token means Spotify didn't accept the cookie at all.
                if (json.optBoolean("isAnonymous", false)) {
                    throw SpotifyWebException("Spotify says this sp_dc cookie isn't signed in. Copy it again from open.spotify.com " +
                        "while you're logged in (the whole value, from the sp_dc row), or use Sign in with Spotify.", 401)
                }
                return WebToken(token, json.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 3_000_000),
                    json.optString("clientId"))
            }
        }
        throw failure ?: SpotifyWebException("Spotify sign-in failed.")
    }

    /** Spotify checks the code against its own clock, so use the Date header from open.spotify.com. */
    private fun serverTimeSeconds(): Long = runCatching {
        val request = Request.Builder().url(SpotifyWebBundle.WEB_PLAYER).head()
            .header("User-Agent", SpotifyWebBundle.USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            response.headers.getDate("Date")?.time?.div(1000)
        }
    }.getOrNull() ?: (System.currentTimeMillis() / 1000)
}
