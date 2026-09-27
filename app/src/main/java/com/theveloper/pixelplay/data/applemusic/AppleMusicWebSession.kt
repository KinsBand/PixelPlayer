package com.theveloper.pixelplay.data.applemusic

import com.theveloper.pixelplay.data.accounts.AccountVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class AppleMusicException(message: String, val status: Int = 0) : IOException(message)

/**
 * Apple Music connection that works like music.apple.com: the web player's public developer
 * token plus the signed-in user's `media-user-token` cookie. No paid developer membership.
 * Stored in [AccountVault] under `applemusic.`.
 */
@Singleton
class AppleMusicWebSession @Inject constructor(
    private val vault: AccountVault,
    baseHttp: OkHttpClient
) {
    /** Browser-like client: the shared one replaces the User-Agent, which Apple's web API rejects. */
    private val http = com.theveloper.pixelplay.data.network.BrowserHttp.from(baseHttp)

    companion object {
        const val KEY_USER_TOKEN = "applemusic.web.mut"
        private const val KEY_DEV_TOKEN = "applemusic.web.devToken"
        private const val KEY_DEV_EXP = "applemusic.web.devTokenExp"
        private const val KEY_STOREFRONT = "applemusic.web.storefront"
        const val API = "https://amp-api.music.apple.com"
        const val WEB = "https://music.apple.com"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private val SCRIPT_SRC = Regex("""<script[^>]+src=["']([^"']*index[~-][^"']*\.js)["']""", RegexOption.IGNORE_CASE)
        private val JWT = Regex("""eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}""")

        fun parseUserToken(input: String): String {
            val trimmed = input.trim().removePrefix("Cookie:").trim()
            val fromPairs = trimmed.split(';').map { it.trim() }
                .firstOrNull { it.startsWith("media-user-token=") }?.removePrefix("media-user-token=")
            val value = (fromPairs ?: trimmed).trim().trim('"')
            require(value.length >= 40 && value.none { it.isWhitespace() || it == ';' }) {
                "That doesn't look like a media-user-token. Copy the value of the media-user-token cookie from music.apple.com."
            }
            return value
        }

        /** JWT claims, or null if [token] isn't a readable JWT. */
        internal fun claims(token: String, part: Int = 1): JSONObject? = runCatching {
            val bytes = java.util.Base64.getUrlDecoder().decode(token.split('.')[part].padEnd((token.split('.')[part].length + 3) / 4 * 4, '='))
            JSONObject(String(bytes, Charsets.UTF_8))
        }.getOrNull()

        /** The web player's developer token among all JWTs found in its script. */
        internal fun pickDeveloperToken(script: String): String? = JWT.findAll(script).map { it.value }.firstOrNull { jwt ->
            val header = claims(jwt, 0)
            val payload = claims(jwt, 1)
            header?.optString("alg") == "ES256" && (payload?.optLong("exp") ?: 0L) * 1000 > System.currentTimeMillis()
        }
    }

    private val mutex = Mutex()

    val isLoggedInFlow = vault.revision.map { vault.get(KEY_USER_TOKEN).isNotBlank() }.distinctUntilChanged()
    val hasSession: Boolean get() = vault.get(KEY_USER_TOKEN).isNotBlank()
    val userToken: String get() = vault.get(KEY_USER_TOKEN)
    val storefront: String get() = vault.get(KEY_STOREFRONT).ifBlank { "us" }

    suspend fun connect(userTokenInput: String) = withContext(Dispatchers.IO) {
        val token = parseUserToken(userTokenInput)
        // Check it before saving: the storefront call only works for a signed-in user.
        val sf = JSONObject(rawGet("$API/v1/me/storefront", developerToken(), token))
            .optJSONArray("data")?.optJSONObject(0)?.optString("id")?.ifBlank { null }
            ?: throw AppleMusicException("Apple Music didn't accept this sign-in.", 401)
        vault.put(KEY_USER_TOKEN to token, KEY_STOREFRONT to sf)
    }

    suspend fun logout() = withContext(Dispatchers.IO) { vault.clear("applemusic.") }

    /** Web-player developer token, read from music.apple.com and cached until a day before it expires. */
    suspend fun developerToken(force: Boolean = false): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cached = vault.get(KEY_DEV_TOKEN)
            val exp = vault.get(KEY_DEV_EXP).toLongOrNull() ?: 0L
            if (!force && cached.isNotBlank() && System.currentTimeMillis() < exp - 86_400_000L) return@withLock cached
            val fresh = scrapeDeveloperToken()
            val freshExp = (claims(fresh)?.optLong("exp") ?: 0L) * 1000
            vault.put(KEY_DEV_TOKEN to fresh, KEY_DEV_EXP to freshExp.toString())
            fresh
        }
    }

    private fun scrapeDeveloperToken(): String {
        val html = plainGet(WEB)
        val base = WEB.toHttpUrlOrNull()!!
        val scripts = SCRIPT_SRC.findAll(html).mapNotNull { base.resolve(it.groupValues[1])?.toString() }.distinct().toList()
        // The token sometimes sits inline in the page itself.
        pickDeveloperToken(html)?.let { return it }
        for (url in scripts) {
            val body = runCatching { plainGet(url) }.getOrNull() ?: continue
            pickDeveloperToken(body)?.let { return it }
        }
        throw AppleMusicException("Couldn't read Apple Music's web player. Try again later.")
    }

    private fun plainGet(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use {
            if (!it.isSuccessful) throw AppleMusicException("music.apple.com returned ${it.code}.", it.code)
            return it.body.string()
        }
    }

    private fun rawGet(url: String, devToken: String, userToken: String): String {
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $devToken")
            .header("media-user-token", userToken)
            .header("Origin", WEB)
            .header("Referer", "$WEB/")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use {
            if (!it.isSuccessful) throw AppleMusicException(when (it.code) {
                401, 403 -> "Apple Music sign-in expired. Reconnect Apple Music in Accounts."
                404 -> "Apple Music couldn't find that."
                429 -> "Apple Music is rate-limiting requests. Try again in a minute."
                else -> "Apple Music request failed (${it.code})."
            }, it.code)
            return it.body.string()
        }
    }

    /**
     * Authenticated GET on amp-api. [pathOrUrl] may be a full URL or a `/v1/...` path (Apple's
     * `next` links are paths). Refreshes the developer token once on 401 and waits once on 429.
     */
    suspend fun get(pathOrUrl: String): JSONObject = withContext(Dispatchers.IO) {
        val url = if (pathOrUrl.startsWith("http")) pathOrUrl else API + pathOrUrl
        val user = userToken.ifBlank { throw AppleMusicException("Apple Music isn't connected.", 401) }
        var forced = false
        var waited = false
        while (true) {
            try {
                return@withContext JSONObject(rawGet(url, developerToken(force = forced), user))
            } catch (e: AppleMusicException) {
                when {
                    e.status == 401 && !forced -> { forced = true }
                    e.status == 429 && !waited -> { waited = true; delay(3_000) }
                    else -> throw e
                }
            }
        }
        JSONObject() // not reached: the loop returns or throws
    }
}
