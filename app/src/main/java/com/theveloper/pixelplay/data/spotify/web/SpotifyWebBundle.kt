package com.theveloper.pixelplay.data.spotify.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the Spotify web player's own JavaScript to recover values Spotify rotates from time to
 * time: the TOTP secret used to sign token requests and the persisted GraphQL query hashes.
 * This is what lets the web connection heal itself instead of waiting for an app update.
 */
@Singleton
class SpotifyWebBundle @Inject constructor(baseHttp: OkHttpClient) {
    private val http = com.theveloper.pixelplay.data.network.BrowserHttp.from(baseHttp)

    companion object {
        const val WEB_PLAYER = "https://open.spotify.com/"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private const val CACHE_MS = 15 * 60_000L
        private const val MAX_SCRIPTS = 12

        private const val JS_STRING = """(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')"""
        private val SECRET_FIRST = Regex("""\{\s*["']?secret["']?\s*:\s*($JS_STRING)\s*,\s*["']?version["']?\s*:\s*(\d+)\s*\}""")
        private val VERSION_FIRST = Regex("""\{\s*["']?version["']?\s*:\s*(\d+)\s*,\s*["']?secret["']?\s*:\s*($JS_STRING)\s*\}""")
        private val SCRIPT_SRC = Regex("""<script[^>]+src=["']([^"']+\.m?js[^"']*)["']""", RegexOption.IGNORE_CASE)

        /** Undoes JavaScript string escaping (enough for the secret literals Spotify ships). */
        internal fun unescapeJs(literal: String): String {
            val body = literal.substring(1, literal.length - 1)
            val out = StringBuilder(body.length)
            var i = 0
            while (i < body.length) {
                val c = body[i]
                if (c != '\\' || i == body.lastIndex) { out.append(c); i++; continue }
                when (val n = body[i + 1]) {
                    'n' -> { out.append('\n'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    'r' -> { out.append('\r'); i += 2 }
                    'x' -> { body.substring(i + 2, minOf(i + 4, body.length)).toIntOrNull(16)?.let { out.append(it.toChar()) }; i += 4 }
                    'u' -> { body.substring(i + 2, minOf(i + 6, body.length)).toIntOrNull(16)?.let { out.append(it.toChar()) }; i += 6 }
                    else -> { out.append(n); i += 2 }
                }
            }
            return out.toString()
        }

        /** All `{secret, version}` pairs in [source], newest version first. */
        internal fun findSecrets(source: String): List<Pair<Int, String>> {
            val found = linkedMapOf<Int, String>()
            SECRET_FIRST.findAll(source).forEach { m -> m.groupValues[2].toIntOrNull()?.let { found[it] = unescapeJs(m.groupValues[1]) } }
            VERSION_FIRST.findAll(source).forEach { m -> m.groupValues[1].toIntOrNull()?.let { found[it] = unescapeJs(m.groupValues[2]) } }
            return found.entries.sortedByDescending { it.key }.map { it.key to it.value }
        }

        /** The 64-hex persisted query hash for [operation], as it appears in the bundle. */
        internal fun findQueryHash(source: String, operation: String): String? =
            Regex("""["']${Regex.escape(operation)}["']\s*,\s*["'](?:query|mutation)["']\s*,\s*["']([0-9a-f]{64})["']""")
                .find(source)?.groupValues?.get(1)
    }

    private val mutex = Mutex()
    @Volatile private var cached: List<String> = emptyList()
    @Volatile private var cachedAt = 0L

    /** Web-player scripts (main bundle first). Cached for a few minutes; [force] refetches. */
    suspend fun scripts(force: Boolean = false): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!force && cached.isNotEmpty() && System.currentTimeMillis() - cachedAt < CACHE_MS) return@withLock cached
            val html = get(WEB_PLAYER, accept = "text/html,application/xhtml+xml")
            val base = WEB_PLAYER.toHttpUrlOrNull()!!
            val urls = SCRIPT_SRC.findAll(html).mapNotNull { base.resolve(it.groupValues[1])?.toString() }
                .filter { it.contains("spotifycdn.com") || it.contains("open.spotify.com") || it.contains("scdn.co") }
                .distinct()
                .sortedByDescending { it.contains("web-player") }
                .take(MAX_SCRIPTS)
                .toList()
            val bodies = urls.mapNotNull { url -> runCatching { get(url, accept = "*/*") }.getOrNull() }
            if (bodies.isEmpty()) throw java.io.IOException("Could not read the Spotify web player.")
            cached = bodies
            cachedAt = System.currentTimeMillis()
            bodies
        }
    }

    suspend fun latestSecret(): Pair<Int, String>? = scripts(force = true).firstNotNullOfOrNull { findSecrets(it).firstOrNull() }

    suspend fun queryHash(operation: String): String? {
        scripts().firstNotNullOfOrNull { findQueryHash(it, operation) }?.let { return it }
        return scripts(force = true).firstNotNullOfOrNull { findQueryHash(it, operation) }
    }

    private fun get(url: String, accept: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", accept).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Spotify web player returned ${response.code}")
            return response.body.string()
        }
    }
}
