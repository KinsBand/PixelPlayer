package com.theveloper.pixelplay.data.presence

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.theveloper.pixelplay.data.accounts.AccountVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
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
 * Shows what PixelPlayer is playing as your status on Discord, through the Discord Social SDK's
 * Rich Presence (the unauthenticated path): PixelPlayer hands the status to the **Discord app on
 * this phone**, which shows it. No Discord sign-in in PixelPlayer, no tokens, no network calls
 * from PixelPlayer for this.
 *
 * Needs: a build that includes the SDK ([DiscordSocialSdkBridge.available]), the Discord app
 * installed (and signed in), and a Discord Application ID typed in the app (stored in the
 * encrypted [AccountVault], never built into the APK, so a copy of the app given to someone
 * else doesn't carry it).
 *
 * (The earlier OAuth2 + headless-session design needed the `activities.write` scope, which
 * Discord doesn't give to apps, so it could never sign in.)
 */
@Singleton
class DiscordPresenceManager @Inject constructor(
    private val vault: AccountVault,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "DiscordPresence"
        private const val DISCORD_PACKAGE = "com.discord"

        private const val KEY_ENABLED = "discord.enabled"
        private const val KEY_CLIENT = "discord.client"
        /** Left over from the old sign-in; blanked on first run. */
        private val LEGACY_KEYS = listOf("discord.access", "discord.refresh", "discord.expiry", "discord.user")

        /** Clear the status after this long paused. */
        private const val PAUSE_CLEAR_MS = 30_000L
        /** Coalesce rapid skips into one update. */
        private const val UPDATE_DEBOUNCE_MS = 1_500L
        /** How often the SDK's callbacks run while a status is set. */
        private const val PUMP_INTERVAL_MS = 500L
        /** Keep running callbacks this long after clearing, so the clear goes through. */
        private const val PUMP_TAIL_MS = 10_000L
        private const val CONNECTED_LABEL = "Discord app"
        /** After an SDK error, try once more this long later (if still playing). */
        private const val RETRY_AFTER_ERROR_MS = 5_000L
        private const val MAX_ERROR_RETRIES = 1
    }

    sealed interface Status {
        data object NotConnected : Status
        data class Waiting(val message: String) : Status
        data class Connected(val userName: String) : Status
        data class Error(val message: String) : Status
    }

    /** What's missing before the status can work. */
    enum class Readiness { READY, NO_SDK_IN_BUILD, DISCORD_NOT_INSTALLED, NO_APPLICATION_ID }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The SDK isn't documented as thread-safe: every native call runs on this one thread. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sdkDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var pendingJob: Job? = null
    private var pumpJob: Job? = null
    private var pumpStopJob: Job? = null

    // Touched only on sdkDispatcher.
    private var initializedAppId: Long? = null
    private var lastSent: DiscordActivityFields? = null
    private var retriedWithoutImage = false

    /** The last playing state handed to us, so a rebuilt client can show it again. */
    @Volatile private var lastNow: NowPlayingPresence? = null
    private var retryJob: Job? = null
    @Volatile private var errorRetries = 0

    init {
        if (LEGACY_KEYS.any { vault.get(it).isNotBlank() }) LEGACY_KEYS.forEach(vault::clear)
    }

    private val _readiness = MutableStateFlow(computeReadiness())
    val readinessFlow: StateFlow<Readiness> = _readiness.asStateFlow()

    private val _status = MutableStateFlow<Status>(initialStatus())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** User setting: show my listening on Discord. Off by default. */
    val enabled = vault.revision.map { vault.get(KEY_ENABLED) == "true" }.distinctUntilChanged()

    /** The Application ID typed in the app ("" when none). */
    val applicationIdInput = vault.revision.map { vault.get(KEY_CLIENT) }.distinctUntilChanged()

    val isConnected: Boolean get() = readiness() == Readiness.READY

    fun readiness(): Readiness = _readiness.value

    /** Re-checks the build, the Discord app and the ID (the dialog calls this when it opens / resumes). */
    fun refreshReadiness() {
        _readiness.value = computeReadiness()
        if (_status.value !is Status.Error || _readiness.value != Readiness.READY) _status.value = initialStatus()
    }

    private fun computeReadiness(): Readiness = when {
        !DiscordSocialSdkBridge.available -> Readiness.NO_SDK_IN_BUILD
        !isDiscordInstalled() -> Readiness.DISCORD_NOT_INSTALLED
        applicationId() == null -> Readiness.NO_APPLICATION_ID
        else -> Readiness.READY
    }

    private fun applicationId(): Long? = parseDiscordApplicationId(vault.get(KEY_CLIENT))

    private fun isDiscordInstalled(): Boolean = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(DISCORD_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(DISCORD_PACKAGE, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    // _readiness is declared before _status, so it's set by the time this first runs.
    private fun initialStatus(): Status =
        if (_readiness.value == Readiness.READY) Status.Connected(CONNECTED_LABEL) else Status.NotConnected

    fun setEnabled(on: Boolean) {
        vault.put(KEY_ENABLED to on.toString())
        if (!on) clearNow()
    }

    /**
     * Saves the Application ID typed in the app ("" removes it). Returns false when it isn't a
     * valid ID (nothing is saved then).
     */
    fun setApplicationId(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            clearNow()
            vault.clear(KEY_CLIENT)
            refreshReadiness()
            return true
        }
        if (parseDiscordApplicationId(trimmed) == null) return false
        vault.put(KEY_CLIENT to trimmed)
        refreshReadiness()
        return true
    }

    // ---- Playback -------------------------------------------------------------------------

    /**
     * Called by the player on play/pause, track change and seeks. Cheap: the work is debounced
     * and runs off the main thread.
     */
    fun onPlayback(now: NowPlayingPresence?) {
        lastNow = now
        if (vault.get(KEY_ENABLED) != "true") return
        // Discord may have been installed (or the ID typed) since the last check.
        if (!isConnected) refreshReadiness()
        if (!isConnected) return
        synchronized(this) {
            retryJob?.cancel()
            errorRetries = 0
            pendingJob?.cancel()
            pendingJob = scope.launch {
                when {
                    now == null -> clearStatus()
                    now.isPlaying -> {
                        delay(UPDATE_DEBOUNCE_MS)
                        sendActivity(now)
                    }
                    else -> {
                        delay(PAUSE_CLEAR_MS)
                        clearStatus()
                    }
                }
            }
        }
    }

    /** Playback stopped / service destroyed: remove the status right away. */
    fun clearNow() {
        synchronized(this) {
            pendingJob?.cancel()
            pendingJob = scope.launch { clearStatus() }
        }
    }

    private suspend fun sendActivity(now: NowPlayingPresence) {
        val appId = applicationId() ?: return
        val fields = discordActivityFields(now, System.currentTimeMillis())
        withContext(sdkDispatcher) {
            try {
                if (needsDiscordClientInit(initializedAppId, appId)) {
                    if (initializedAppId != null) DiscordSocialSdkBridge.nativeShutdown()
                    if (!DiscordSocialSdkBridge.nativeInit(appId)) {
                        _status.value = Status.Error("Couldn't start Discord status.")
                        return@withContext
                    }
                    initializedAppId = appId
                }
                retriedWithoutImage = false
                push(fields)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "Discord status update failed")
                _status.value = Status.Error("Couldn't update your Discord status.")
                // The client may be dead (e.g. its Activity is gone): rebuild it on the next try.
                dropClient()
                scheduleRetry()
                return@withContext
            }
        }
        startPump()
    }

    /** On sdkDispatcher: shuts the native client down so the next update creates a fresh one. */
    private fun dropClient() {
        // No callbacks into a client that's going away.
        synchronized(this) {
            pumpStopJob?.cancel()
            pumpJob?.cancel()
            pumpJob = null
        }
        if (initializedAppId != null) {
            runCatching { DiscordSocialSdkBridge.nativeShutdown() }
                .onFailure { Timber.tag(TAG).w(it, "Discord shutdown failed") }
            initializedAppId = null
        }
        lastSent = null
    }

    /** One retry a few seconds later while something is still playing (cancelled by the next update). */
    private fun scheduleRetry() {
        synchronized(this) {
            // One retry per run of errors (reset when a status goes through or playback changes).
            if (errorRetries >= MAX_ERROR_RETRIES) return
            errorRetries++
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(RETRY_AFTER_ERROR_MS)
                val now = lastNow
                if (now != null && now.isPlaying && vault.get(KEY_ENABLED) == "true" && isConnected) sendActivity(now)
            }
        }
    }

    /**
     * MainActivity handed the SDK a new Activity (app reopened after being closed while music kept
     * playing). The old client was bound to the destroyed Activity and stops working without
     * telling us, which is why only a force stop used to fix it: drop it and resend what's playing.
     */
    fun onHostActivityChanged() {
        scope.launch {
            withContext(sdkDispatcher) { dropClient() }
            refreshReadiness()
            val now = lastNow
            if (now != null && now.isPlaying) onPlayback(now)
        }
    }

    /** The music service is going away: clear the status and shut the client down (next playback re-inits). */
    fun release() {
        synchronized(this) {
            pendingJob?.cancel()
            retryJob?.cancel()
            pendingJob = scope.launch {
                clearStatus()
                withContext(sdkDispatcher) { dropClient() }
            }
        }
    }

    /** On sdkDispatcher. */
    private fun push(fields: DiscordActivityFields) {
        DiscordSocialSdkBridge.nativeUpdate(
            listening = true,
            details = fields.details,
            state = fields.state,
            startMs = fields.startMs,
            endMs = fields.endMs,
            largeImage = fields.largeImage,
            largeText = fields.largeText,
        )
        lastSent = fields
    }

    private suspend fun clearStatus() {
        withContext(sdkDispatcher) {
            if (initializedAppId == null) return@withContext
            try {
                DiscordSocialSdkBridge.nativeClear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "Clearing Discord status failed")
            }
            lastSent = null
        }
        stopPumpLater()
    }

    private fun startPump() {
        synchronized(this) {
            pumpStopJob?.cancel()
            if (pumpJob?.isActive == true) return
            pumpJob = scope.launch(sdkDispatcher) {
                while (isActive) {
                    try {
                        DiscordSocialSdkBridge.nativeRunCallbacks()
                        DiscordSocialSdkBridge.nativeTakeError()?.let(::onSdkError)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Timber.tag(TAG).w(e, "Discord callbacks failed")
                    }
                    delay(PUMP_INTERVAL_MS)
                }
            }
        }
    }

    private fun stopPumpLater() {
        synchronized(this) {
            pumpStopJob?.cancel()
            pumpStopJob = scope.launch {
                delay(PUMP_TAIL_MS)
                synchronized(this@DiscordPresenceManager) { pumpJob?.cancel(); pumpJob = null }
            }
        }
    }

    /** On sdkDispatcher (from the pump). */
    private fun onSdkError(message: String) {
        val last = lastSent
        // Discord's docs show uploaded asset keys; a web cover may be refused. Try once without it.
        if (last != null && last.largeImage != null && !retriedWithoutImage) {
            retriedWithoutImage = true
            Timber.tag(TAG).w("Discord refused the status with a cover; retrying without it")
            push(last.copy(largeImage = null))
            return
        }
        Timber.tag(TAG).w("Discord status error: %s", message)
        _status.value = Status.Error("Discord couldn't show your status: $message")
        // Rebuild the client on the next update instead of keeping a broken one until a force stop.
        dropClient()
        scheduleRetry()
    }

    /** Turns the status off and forgets the typed Application ID. */
    suspend fun disconnect() {
        clearStatus()
        withContext(sdkDispatcher) {
            if (initializedAppId != null) {
                runCatching { DiscordSocialSdkBridge.nativeShutdown() }
                initializedAppId = null
            }
        }
        vault.put(KEY_ENABLED to "false")
        vault.clear(KEY_CLIENT)
        refreshReadiness()
        _status.value = Status.NotConnected
    }
}
