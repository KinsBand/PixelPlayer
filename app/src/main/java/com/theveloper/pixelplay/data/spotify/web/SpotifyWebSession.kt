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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

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
        private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
        private const val KEY_NUANCE = "spotify.web.nuance"
        private const val KEY_NUANCE_AT = "spotify.web.nuanceAt"
        private const val B32_PREFIX = "b32:"
        private const val NUANCE_CACHE_MS = 6 * 60 * 60_000L
        private const val NUANCE_FILE = "nuances.json"
        /** The secret list Spotube's Spotify plugin reads (GitHub API first, raw file as backup). */
        private val NUANCE_URLS = listOf(
            "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef",
            "https://gist.githubusercontent.com/raw/22ed9c6ba463899e933427f7de1f0eef/nuances.json"
        )

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

    /** One TOTP secret version, already turned into its HMAC key. */
    private class TotpKey(val version: Int, val key: ByteArray, val source: String)

    /**
     * Tries secrets in the order that keeps sign-in working longest, like Spotube's plugin:
     * the community "nuances" list (updated within hours of Spotify rotating), the secret we last
     * saw work, the bundled one, and finally whatever the live web player ships.
     */
    private suspend fun fetchToken(spDc: String): WebToken {
        val tried = mutableSetOf<String>()
        var lastError: Exception? = null
        suspend fun attempt(key: TotpKey?): WebToken? {
            if (key == null || !tried.add("${key.version}:${key.key.contentHashCode()}")) return null
            return try {
                requestToken(spDc, key).also { rememberWorking(key) }
            } catch (e: SpotifyWebException) {
                if (e.status == 401) throw e
                lastError = e; null
            } catch (e: IOException) { lastError = e; null }
        }
        attempt(runCatching { latestNuance(force = false) }.getOrNull())?.let { return it }
        attempt(storedKey())?.let { return it }
        attempt(TotpKey(SpotifyTotp.BUNDLED_VERSION, SpotifyTotp.key(SpotifyTotp.BUNDLED_SECRET), "bundled"))?.let { return it }
        // Spotify rotated the secret: re-read the list without cache, then the web player itself.
        attempt(runCatching { latestNuance(force = true) }.getOrNull())?.let { return it }
        attempt(runCatching { bundle.latestSecret() }.getOrNull()?.let { (v, s) -> TotpKey(v, SpotifyTotp.key(s), "web player") })
            ?.let { return it }
        throw lastError ?: SpotifyWebException("Spotify sign-in failed. Try again in a moment.")
    }

    private fun storedKey(): TotpKey? {
        val version = vault.get(KEY_SECRET_VERSION).toIntOrNull() ?: return null
        val stored = vault.get(KEY_SECRET).takeIf { it.isNotEmpty() } ?: return null
        val key = if (stored.startsWith(B32_PREFIX)) runCatching { SpotifyTotp.base32(stored.removePrefix(B32_PREFIX)) }.getOrNull()
            else SpotifyTotp.key(stored)
        return key?.let { TotpKey(version, it, "stored") }
    }

    private fun rememberWorking(key: TotpKey) {
        if (key.source == "stored") return
        val encoded = B32_PREFIX + base32Encode(key.key)
        vault.put(KEY_SECRET_VERSION to key.version.toString(), KEY_SECRET to encoded)
    }

    /**
     * Newest entry of the secret list Spotube reads (a GitHub gist of `{v, s}` where `s` is the
     * base32 HMAC key). Cached for a few hours so sign-in and refreshes don't hit GitHub each time.
     */
    private fun latestNuance(force: Boolean): TotpKey? {
        val cachedAt = vault.get(KEY_NUANCE_AT).toLongOrNull() ?: 0L
        val cached = vault.get(KEY_NUANCE)
        if (!force && cached.isNotBlank() && System.currentTimeMillis() - cachedAt < NUANCE_CACHE_MS) return parseNuance(cached)
        val body = NUANCE_URLS.firstNotNullOfOrNull { url ->
            runCatching {
                val request = Request.Builder().url(url)
                    .header("User-Agent", "PixelPlayer")
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .build()
                val text = http.newCall(request).execute().use { r -> if (r.isSuccessful) r.body.string() else null }
                // The GitHub API wraps the file; the raw URL returns it directly.
                if (text != null && text.trimStart().startsWith("{")) JSONObject(text).optJSONObject("files")
                    ?.optJSONObject(NUANCE_FILE)?.optString("content")?.takeIf { it.isNotBlank() } else text
            }.getOrNull()
        } ?: return cached.takeIf { it.isNotBlank() }?.let(::parseNuance)
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val newest = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            .filter { it.optString("s").isNotBlank() }
            .maxByOrNull { it.optInt("v") } ?: return null
        val entry = "${newest.optInt("v")}:${newest.optString("s")}"
        vault.put(KEY_NUANCE to entry, KEY_NUANCE_AT to System.currentTimeMillis().toString())
        return parseNuance(entry)
    }

    private fun parseNuance(entry: String): TotpKey? {
        val version = entry.substringBefore(':').toIntOrNull() ?: return null
        val key = runCatching { SpotifyTotp.base32(entry.substringAfter(':')) }.getOrNull() ?: return null
        return TotpKey(version, key, "nuances")
    }

    /**
     * Spotube sends only the cookie and a meaningless random User-Agent. A Chrome User-Agent from
     * a non-Chrome HTTP stack is what Spotify's bot checks look for, so that style goes first and
     * the old browser-style request is kept as a fallback.
     */
    private fun requestToken(spDc: String, totp: TotpKey): WebToken {
        val code = SpotifyTotp.codeForKey(totp.key, serverTimeSeconds())
        var failure: SpotifyWebException? = null
        var anonymous = false
        for (browserStyle in listOf(false, true)) for (reason in listOf("transport", "init")) {
            val url = TOKEN_URL.toHttpUrl().newBuilder()
                .addQueryParameter("reason", reason)
                .addQueryParameter("productType", "web-player")
                .addQueryParameter("totp", code)
                .addQueryParameter("totpServer", code)
                .addQueryParameter("totpVer", totp.version.toString())
                .build()
            val builder = Request.Builder().url(url).header("Cookie", "sp_dc=$spDc;")
            if (browserStyle) builder
                .header("User-Agent", SpotifyWebBundle.USER_AGENT)
                .header("Accept", "application/json")
                .header("App-Platform", "WebPlayer")
                .header("Referer", "https://open.spotify.com/")
            else builder.header("User-Agent", spotubeStyleUserAgent())
            http.newCall(builder.build()).execute().use { response ->
                val text = runCatching { response.body.string() }.getOrDefault("")
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") ?: JSONObject(text).optString("error") }
                        .getOrNull().orEmpty().ifBlank { text.take(200) }.ifBlank { "no details" }
                    failure = SpotifyWebException("Spotify refused the sign-in request (${response.code}: $detail).", response.code)
                    return@use
                }
                val json = runCatching { JSONObject(text) }.getOrNull()
                val token = json?.optString("accessToken").orEmpty()
                if (json == null || token.isBlank()) { failure = SpotifyWebException("Spotify returned no token."); return@use }
                // Anonymous means the cookie wasn't accepted; try the other request style before giving up.
                if (json.optBoolean("isAnonymous", false)) { anonymous = true; return@use }
                return WebToken(token, json.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 3_000_000),
                    json.optString("clientId"))
            }
        }
        if (anonymous) throw SpotifyWebException("Spotify says this sp_dc cookie isn't signed in. Sign in again " +
            "(or copy the whole sp_dc value again from open.spotify.com while you're logged in).", 401)
        throw failure ?: SpotifyWebException("Spotify sign-in failed.")
    }

    /** Same shape as Spotube's: epoch millis, a random number and 16 random bytes as digits. */
    private fun spotubeStyleUserAgent(): String = buildString {
        append(System.currentTimeMillis())
        append(Random.nextInt(100) * 1000)
        repeat(16) { append(Random.nextInt(256)) }
    }

    /** Spotify checks the code against its own clock: ask its server-time endpoint, then fall back to the Date header. */
    private fun serverTimeSeconds(): Long {
        runCatching {
            val request = Request.Builder().url(SERVER_TIME_URL).header("User-Agent", spotubeStyleUserAgent()).build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) JSONObject(response.body.string()).optLong("serverTime").takeIf { it > 0 } else null
            }
        }.getOrNull()?.let { return it }
        return runCatching {
            val request = Request.Builder().url(SpotifyWebBundle.WEB_PLAYER).head()
                .header("User-Agent", SpotifyWebBundle.USER_AGENT).build()
            http.newCall(request).execute().use { response ->
                response.headers.getDate("Date")?.time?.div(1000)
            }
        }.getOrNull() ?: (System.currentTimeMillis() / 1000)
    }

    private fun base32Encode(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = ((buffer shl 8) or (b.toInt() and 0xFF)) and 0xFFFF
            bits += 8
            while (bits >= 5) { out.append(alphabet[(buffer shr (bits - 5)) and 31]); bits -= 5 }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }
}
