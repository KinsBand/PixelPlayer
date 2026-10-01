package com.theveloper.pixelplay.data.presence

import com.theveloper.pixelplay.data.accounts.AccountVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** What's playing, as the player reports it. */
data class NowPlayingPresence(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String?,
    /** Public https artwork only (local files have none Discord can show). */
    val artworkUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
)

/**
 * Shows what PixelPlayer is playing as a "Listening to" status on your Discord profile.
 *
 * Uses only Discord's official OAuth2 + HTTP API: you sign in with your own Discord
 * application (Developer Portal → Applications, like the Spotify developer-app sign-in) and the
 * status is set through a *headless session* (`POST /users/@me/headless-sessions`, scope
 * `activities.write`). No user token, no gateway/self-bot connection.
 *
 * Headless sessions end by themselves after ~20 minutes, so the session is refreshed while
 * music keeps playing, cleared ~30 s after pausing, and deleted when playback stops.
 *
 * Note: Discord may restrict `activities.write` to approved applications. When it refuses the
 * scope, [status] explains that instead of failing silently.
 */
@Singleton
class DiscordPresenceManager @Inject constructor(
    private val vault: AccountVault,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "DiscordPresence"
        const val AUTH_ENDPOINT = "https://discord.com/oauth2/authorize"
        const val TOKEN_ENDPOINT = "https://discord.com/api/oauth2/token"
        const val API = "https://discord.com/api/v10"
        const val REDIRECT_PATH = "/discord-callback"
        /** Register these redirects in your Discord application's OAuth2 settings. */
        val REDIRECT_PORTS = intArrayOf(8891, 8892, 8893)
        fun redirectUri(port: Int): String = "http://127.0.0.1:$port$REDIRECT_PATH"
        val REGISTERED_REDIRECT_URIS: List<String> get() = REDIRECT_PORTS.map { redirectUri(it) }
        const val SCOPES = "identify activities.write"

        private const val KEY_ENABLED = "discord.enabled"
        private const val KEY_CLIENT = "discord.client"
        private const val KEY_ACCESS = "discord.access"
        private const val KEY_REFRESH = "discord.refresh"
        private const val KEY_EXPIRY = "discord.expiry"
        private const val KEY_USER = "discord.user"

        /** Clear the status after this long paused. */
        private const val PAUSE_CLEAR_MS = 30_000L
        /** Coalesce rapid skips into one update. */
        private const val UPDATE_DEBOUNCE_MS = 1_500L
        /** Headless sessions expire after ~20 min; refresh well before. */
        private const val SESSION_REFRESH_MS = 10 * 60_000L
        private val JSON = "application/json".toMediaType()
    }

    sealed interface Status {
        data object NotConnected : Status
        data class Waiting(val message: String) : Status
        data class Connected(val userName: String) : Status
        data class Error(val message: String) : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authMutex = Mutex()
    private val sessionMutex = Mutex()
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var sessionToken: String? = null
    @Volatile private var lastSent: NowPlayingPresence? = null
    private var pendingJob: Job? = null
    private var refreshJob: Job? = null

    private val _status = MutableStateFlow<Status>(initialStatus())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** User setting: show my listening on Discord. Off by default. */
    val enabled = vault.revision.map { vault.get(KEY_ENABLED) == "true" }.distinctUntilChanged()

    val isConnected: Boolean get() = vault.get(KEY_ACCESS).isNotBlank()

    private fun initialStatus(): Status =
        if (vault.get(KEY_ACCESS).isNotBlank()) Status.Connected(vault.get(KEY_USER).ifBlank { "Discord" }) else Status.NotConnected

    fun setEnabled(on: Boolean) {
        vault.put(KEY_ENABLED to on.toString())
        if (!on) clearNow()
    }

    // ---- Playback -------------------------------------------------------------------------

    /**
     * Called by the player on play/pause, track change and seeks. Cheap: the network work is
     * debounced and runs off the main thread.
     */
    fun onPlayback(now: NowPlayingPresence?) {
        if (vault.get(KEY_ENABLED) != "true" || !isConnected) return
        synchronized(this) {
            pendingJob?.cancel()
            pendingJob = scope.launch {
                when {
                    now == null -> clearSession()
                    now.isPlaying -> {
                        delay(UPDATE_DEBOUNCE_MS)
                        sendActivity(now)
                        scheduleRefresh()
                    }
                    else -> {
                        refreshJob?.cancel()
                        delay(PAUSE_CLEAR_MS)
                        clearSession()
                    }
                }
            }
        }
    }

    /** Playback stopped / service destroyed: remove the status right away. */
    fun clearNow() {
        synchronized(this) {
            pendingJob?.cancel()
            refreshJob?.cancel()
            pendingJob = scope.launch { clearSession() }
        }
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                delay(SESSION_REFRESH_MS)
                val current = lastSent ?: return@launch
                // Keep the progress bar honest: advance the position by the time that passed.
                sendActivity(current.copy(positionMs = current.positionMs + SESSION_REFRESH_MS))
            }
        }
    }

    private suspend fun sendActivity(now: NowPlayingPresence): Unit = sessionMutex.withLock<Unit> {
        val clientId = vault.get(KEY_CLIENT)
        val token = accessToken() ?: return@withLock
        val start = System.currentTimeMillis() - now.positionMs.coerceAtLeast(0)
        val activity = JSONObject()
            .put("type", 2) // LISTENING
            .put("name", "PixelPlayer")
            .put("application_id", clientId)
            .put("platform", "android")
            .put("details", now.title.take(128).ifBlank { "Unknown title" })
            .put("state", now.artist.take(128).ifBlank { "Unknown artist" })
            .put("timestamps", JSONObject().apply {
                put("start", start)
                if (now.durationMs > 0) put("end", start + now.durationMs)
            })
        val art = now.artworkUrl?.takeIf { it.startsWith("https://") }
        if (art != null) {
            activity.put("assets", JSONObject()
                .put("large_image", art)
                .apply { now.album?.takeIf { it.isNotBlank() }?.let { put("large_text", it.take(128)) } })
        }
        val body = JSONObject().put("activities", JSONArray().put(activity))
        sessionToken?.let { body.put("token", it) }
        try {
            val response = request(token, "$API/users/@me/headless-sessions", body)
            sessionToken = JSONObject(response).optString("token").ifBlank { sessionToken }
            lastSent = now
            if (_status.value !is Status.Connected) _status.value = Status.Connected(vault.get(KEY_USER).ifBlank { "Discord" })
        } catch (e: CancellationException) {
            throw e
        } catch (e: DiscordHttpException) {
            Timber.tag(TAG).w("Presence update refused: ${e.code} ${e.body}")
            if (e.code == 400 || e.code == 404) sessionToken = null // expired session: start a new one next time
            _status.value = Status.Error(when (e.code) {
                401 -> "Discord sign-in expired. Connect again."
                403 -> "Discord didn't allow setting your status (activities.write). Your Discord app may need Discord's approval for this."
                429 -> "Discord is rate-limiting status updates. It will retry."
                else -> "Couldn't update your Discord status (${e.code})."
            })
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Presence update failed")
        }
    }

    private suspend fun clearSession(): Unit = sessionMutex.withLock<Unit> {
        val session = sessionToken ?: return@withLock
        sessionToken = null
        lastSent = null
        val token = accessToken() ?: return@withLock
        try {
            request(token, "$API/users/@me/headless-sessions/delete", JSONObject().put("token", session))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Clearing Discord status failed (it expires by itself)")
        }
    }

    private class DiscordHttpException(val code: Int, val body: String) : IOException("Discord HTTP $code")

    private suspend fun request(accessToken: String, url: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrl()
        require(parsed.scheme == "https" && parsed.host == "discord.com")
        val call = Request.Builder().url(parsed)
            .header("Authorization", "Bearer $accessToken")
            .post(body.toString().toRequestBody(JSON))
            .build()
        okHttpClient.newCall(call).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw DiscordHttpException(response.code, text.take(300))
            text
        }
    }

    // ---- Sign-in (OAuth2 authorization code + PKCE, loopback redirect) ---------------------

    private fun randomToken(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun openLoopbackListener(): ServerSocket {
        val loopback = InetAddress.getByName("127.0.0.1")
        var last: IOException? = null
        for (port in REDIRECT_PORTS) {
            try { return ServerSocket(port, 4, loopback).apply { soTimeout = 300_000 } } catch (e: IOException) { last = e }
        }
        throw IllegalStateException("Discord sign-in needs one of these local ports to be free: " +
            REDIRECT_PORTS.joinToString(", ") + ".", last)
    }

    /**
     * Starts sign-in with your Discord application's client ID (the Application ID, a long
     * number). Returns the URL to open in the browser; the result arrives in [status].
     */
    suspend fun getAuthorizationUrl(clientId: String): String = withContext(Dispatchers.IO) {
        authMutex.withLock {
            val id = clientId.trim()
            require(id.matches(Regex("\\d{17,20}"))) { "Enter your Discord application's Application ID." }
            listener?.close()
            val server = openLoopbackListener()
            listener = server
            val redirect = redirectUri(server.localPort)
            val verifier = randomToken()
            val state = randomToken()
            _status.value = Status.Waiting("Waiting for Discord sign-in…")
            scope.launch { awaitCallback(server, id, redirect, verifier, state) }
            val challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
            AUTH_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("client_id", id)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("redirect_uri", redirect)
                .addQueryParameter("scope", SCOPES)
                .addQueryParameter("state", state)
                .addQueryParameter("code_challenge", challenge)
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("prompt", "consent")
                .build().toString()
        }
    }

    private suspend fun awaitCallback(server: ServerSocket, clientId: String, redirect: String, verifier: String, state: String) {
        try {
            server.use {
                var done = false
                val deadline = System.currentTimeMillis() + 300_000
                while (!done && System.currentTimeMillis() < deadline) {
                    server.soTimeout = (deadline - System.currentTimeMillis()).coerceIn(1, 300_000).toInt()
                    server.accept().use connection@{ socket ->
                        socket.soTimeout = 5_000
                        val line = try { socket.getInputStream().bufferedReader().readLine().orEmpty() } catch (_: IOException) { return@connection }
                        val uri = ("http://127.0.0.1" + line.split(' ').getOrElse(1) { "" }).toHttpUrlOrNull() ?: return@connection
                        if (!line.startsWith("GET ") || uri.encodedPath != REDIRECT_PATH || uri.queryParameter("state") != state) return@connection
                        done = true
                        val message = try {
                            val code = uri.queryParameter("code")
                            when {
                                uri.queryParameter("error") != null -> {
                                    val err = uri.queryParameter("error").orEmpty()
                                    if (err == "invalid_scope") "Discord refused the activities.write permission for this app. It may need Discord's approval."
                                    else "Discord access was not granted."
                                }
                                code == null -> "Sign-in was not completed."
                                else -> { exchangeCode(code, clientId, redirect, verifier); null }
                            }
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { e.message ?: "Discord sign-in failed." }
                        _status.value = if (message == null) Status.Connected(vault.get(KEY_USER).ifBlank { "Discord" }) else Status.Error(message)
                        val page = (message ?: "Discord connected. Return to PixelPlayer.").toByteArray()
                        try {
                            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\n" +
                                "Content-Length: ${page.size}\r\nConnection: close\r\n\r\n").toByteArray() + page)
                        } catch (_: IOException) { }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (listener === server) _status.value = Status.Error("Sign-in expired or was interrupted. Connect again.")
        } finally {
            if (listener === server) listener = null
        }
    }

    private suspend fun exchangeCode(code: String, clientId: String, redirect: String, verifier: String) {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirect)
            .add("code_verifier", verifier)
            .build()
        requestToken(form, clientId, oldRefresh = "")
        // Name to show in settings.
        runCatching {
            val me = okHttpClient.newCall(Request.Builder().url("$API/users/@me")
                .header("Authorization", "Bearer ${vault.get(KEY_ACCESS)}").build()).execute().use { JSONObject(it.body.string()) }
            vault.put(KEY_USER to me.optString("global_name").ifBlank { me.optString("username") })
        }
        vault.put(KEY_ENABLED to "true")
    }

    private fun requestToken(form: FormBody, clientId: String, oldRefresh: String): String {
        okHttpClient.newCall(Request.Builder().url(TOKEN_ENDPOINT).post(form).build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val error = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                throw IOException(when (error) {
                    "invalid_client" -> "Discord rejected the Application ID. Turn on \"Public Client\" in your Discord app's OAuth2 settings."
                    "invalid_grant" -> "Discord sign-in expired. Connect again."
                    "invalid_scope" -> "Discord refused the activities.write permission for this app."
                    else -> "Discord sign-in failed (${response.code})."
                })
            }
            val json = JSONObject(text)
            val token = json.getString("access_token")
            vault.put(
                KEY_CLIENT to clientId,
                KEY_ACCESS to token,
                KEY_REFRESH to json.optString("refresh_token", oldRefresh),
                KEY_EXPIRY to (System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000).toString()
            )
            return token
        }
    }

    private suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        authMutex.withLock {
            val token = vault.get(KEY_ACCESS)
            if (token.isNotBlank() && System.currentTimeMillis() < (vault.get(KEY_EXPIRY).toLongOrNull() ?: 0) - 60_000) return@withLock token
            val refresh = vault.get(KEY_REFRESH)
            if (refresh.isBlank()) return@withLock null
            try {
                requestToken(FormBody.Builder()
                    .add("client_id", vault.get(KEY_CLIENT))
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refresh)
                    .build(), vault.get(KEY_CLIENT), refresh)
            } catch (e: IOException) {
                _status.value = Status.Error(e.message ?: "Discord sign-in expired. Connect again.")
                null
            }
        }
    }

    suspend fun disconnect() {
        clearSession()
        authMutex.withLock {
            listener?.close(); listener = null
            vault.clear("discord.")
        }
        _status.value = Status.NotConnected
    }
}
