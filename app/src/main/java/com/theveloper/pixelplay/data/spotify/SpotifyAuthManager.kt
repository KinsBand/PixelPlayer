package com.theveloper.pixelplay.data.spotify

import java.util.Base64
import com.theveloper.pixelplay.data.accounts.AccountVault
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyAuthManager @Inject constructor(
    private val vault: AccountVault,
    private val preferencesRepository: UserPreferencesRepository,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
        const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
        const val REDIRECT_PATH = "/spotify-callback"

        /**
         * Fixed loopback redirects keep dashboard setup explicit. Register each entry below;
         * sign-in binds the first free port. Spotify also documents dynamic loopback ports,
         * but this app deliberately uses the listed, fully specified redirect URIs.
         */
        val REDIRECT_PORTS = intArrayOf(8888, 8889, 8890)

        fun redirectUri(port: Int): String = "http://127.0.0.1:$port$REDIRECT_PATH"

        val REGISTERED_REDIRECT_URIS: List<String> get() = REDIRECT_PORTS.map { redirectUri(it) }
        const val SCOPES = "user-library-read playlist-read-private playlist-read-collaborative"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var listener: ServerSocket? = null
    val status = MutableStateFlow<String?>(null)
    /** Signed in either with a developer app (OAuth) or with the web-player session (sp_dc). */
    val isLoggedInFlow = vault.revision.map {
        vault.get("spotify.access").isNotBlank() || vault.get(com.theveloper.pixelplay.data.spotify.web.SpotifyWebSession.KEY_SP_DC).isNotBlank()
    }.distinctUntilChanged()
    fun generateCodeVerifier(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    /**
     * Binds the first free port from the redirects shown in the account setup dialog.
     */
    private fun openLoopbackListener(): ServerSocket {
        val loopback = InetAddress.getByName("127.0.0.1")
        var lastFailure: IOException? = null
        for (port in REDIRECT_PORTS) {
            try {
                return ServerSocket(port, 4, loopback).apply { soTimeout = 300_000 }
            } catch (e: IOException) {
                lastFailure = e
            }
        }
        throw IllegalStateException(
            "Spotify sign-in needs one of these local ports to be free: " +
                REDIRECT_PORTS.joinToString(", ") + ". Close whatever is using them and try again.",
            lastFailure
        )
    }

    suspend fun getAuthorizationUrl(clientId: String): String = withContext(Dispatchers.IO) { mutex.withLock {
        require(clientId.matches(Regex("[a-zA-Z0-9]{32}"))) { "Enter your Spotify developer app client ID." }
        listener?.close()
        val server = openLoopbackListener()
        listener = server
        val redirect = redirectUri(server.localPort)
        val verifier = generateCodeVerifier()
        val state = generateCodeVerifier()
        vault.put("spotify.pendingClient" to clientId, "spotify.verifier" to verifier, "spotify.state" to state,
            "spotify.redirect" to redirect, "spotify.started" to System.currentTimeMillis().toString())
        status.value = "Waiting for Spotify sign-in…"
        scope.launch {
            try {
                server.use {
                    var completed = false
                    val deadline = System.currentTimeMillis() + 300_000
                    while (!completed && System.currentTimeMillis() < deadline) {
                        server.soTimeout = (deadline - System.currentTimeMillis()).coerceIn(1, 300_000).toInt()
                        server.accept().use connection@ { socket ->
                            socket.soTimeout = 5_000
                            val line = try { socket.getInputStream().bufferedReader().readLine().orEmpty() }
                                catch (_: IOException) { return@connection }
                            val uri = ("http://127.0.0.1" + line.split(' ').getOrElse(1) { "" }).toHttpUrlOrNull()
                                ?: return@connection
                            val valid = line.startsWith("GET ") && uri.encodedPath == REDIRECT_PATH && uri.queryParameter("state") == state
                            var failure: String? = null
                            val success = if (valid) {
                                completed = true
                                try {
                                    if (uri.queryParameter("error") != null) {
                                        failure = "Spotify access was not granted. Return to PixelPlayer and connect again."
                                        false
                                    } else uri.queryParameter("code")?.let { exchangeCodeForToken(it, clientId, state) } ?: false
                                } catch (e: CancellationException) { throw e }
                                  catch (e: Exception) { failure = e.message ?: "Spotify token exchange failed. Try connecting again."; false }
                            } else false
                            val message = if (success) "Spotify connected. Return to PixelPlayer." else failure ?: "Sign-in was not completed. Return to PixelPlayer and try again."
                            if (completed && listener === server) status.value = message
                            val bytes = message.toByteArray()
                            try {
                                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes)
                            } catch (_: IOException) { /* A closed browser must not undo a successful login. */ }
                        }
                    }
                }
            } catch (e: Exception) {
                if (listener === server) status.value = "Sign-in expired or interrupted. Please connect again."
            } finally {
                mutex.withLock {
                    if (listener === server) { listener = null; vault.put("spotify.verifier" to "", "spotify.state" to "", "spotify.pendingClient" to "") }
                }
            }
        }
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        AUTH_ENDPOINT.toHttpUrl().newBuilder().addQueryParameter("client_id", clientId)
            .addQueryParameter("response_type", "code").addQueryParameter("redirect_uri", redirect)
            .addQueryParameter("code_challenge_method", "S256").addQueryParameter("code_challenge", challenge)
            .addQueryParameter("state", state).addQueryParameter("scope", SCOPES).build().toString()
    } }

    private suspend fun exchangeCodeForToken(code: String, clientId: String, state: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val verifier = vault.get("spotify.verifier")
            if (verifier.isBlank() || state != vault.get("spotify.state") || clientId != vault.get("spotify.pendingClient") ||
                System.currentTimeMillis() - (vault.get("spotify.started").toLongOrNull() ?: 0) > 300_000) return@withLock false
            val form = FormBody.Builder().add("client_id", clientId).add("grant_type", "authorization_code")
                .add("code", code).add("redirect_uri", vault.get("spotify.redirect")).add("code_verifier", verifier).build()
            vault.put("spotify.verifier" to "")
            val connected = requestToken(form, "", clientId) != null
            if (connected) preferencesRepository.clearSpotifyAuthTokens()
            connected
        }
    }
    private fun requestToken(form: FormBody, oldRefresh: String, clientId: String): String? {
        okHttpClient.newCall(Request.Builder().url(TOKEN_ENDPOINT).post(form).build()).execute().use { response ->
            if (!response.isSuccessful) {
                val error = runCatching { JSONObject(response.body.string()).optString("error") }.getOrDefault("")
                throw IOException(when (error) {
                    "invalid_client" -> "Spotify rejected the client ID. Check your developer app settings."
                    "invalid_grant" -> "Spotify authorization expired or was revoked. Connect again with the registered redirect URI."
                    else -> "Spotify sign-in failed (${response.code}). Check your connection and developer app access."
                })
            }
            val json = JSONObject(response.body.string())
            val token = json.getString("access_token")
            vault.put("spotify.client" to clientId, "spotify.access" to token, "spotify.refresh" to json.optString("refresh_token", oldRefresh),
                "spotify.expiry" to (System.currentTimeMillis() + json.getLong("expires_in") * 1000).toString())
            return token
        }
    }
    suspend fun refreshAccessTokenIfNeeded(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val token = vault.get("spotify.access")
            if (token.isNotBlank() && System.currentTimeMillis() < (vault.get("spotify.expiry").toLongOrNull() ?: 0) - 60_000) return@withLock token
            val refresh = vault.get("spotify.refresh")
            if (refresh.isBlank()) return@withLock null
            requestToken(FormBody.Builder().add("client_id", vault.get("spotify.client"))
                .add("grant_type", "refresh_token").add("refresh_token", refresh).build(), refresh, vault.get("spotify.client"))
        }
    }
    suspend fun logout() = withContext(Dispatchers.IO) {
        mutex.withLock { listener?.close(); listener = null; vault.clear("spotify."); preferencesRepository.clearSpotifyAuthTokens() }
        status.value = null
    }
}

