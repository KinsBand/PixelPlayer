package com.theveloper.pixelplay.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.theveloper.pixelplay.data.preferences.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume

/*
 * App Lock ("Lock App to Screen"): exit protection for group listening, not an entry lock.
 *
 * - The saved preference (DataStore) is kept apart from the live session. The live session is
 *   Android's own lock task / screen pinning state, read from ActivityManager, never saved, so a
 *   crash, reboot, update or process death can't leave the owner trapped: Android ends pinning
 *   with the task, and the app only asks to pin again when it is opened.
 * - Inside the app nothing is locked and nothing asks for a fingerprint. Only turning the lock
 *   on, turning it off and ending the locked session ask, through Android's own BiometricPrompt
 *   (strong biometric, or the phone's PIN / pattern / password). The app never sees biometric
 *   data, only success or failure.
 * - A normal app can't block Home / Recents itself. Screen pinning is what keeps it on screen:
 *   Android asks the user to confirm pinning, and its own unpin gesture asks for the device PIN
 *   when "Ask for PIN before unpinning" is on in Android's settings. Where pinning isn't
 *   available, only Back at the app's root is held (the one exit the app legitimately owns).
 */

object AppLockPrefKeys {
    val ENABLED = booleanPreferencesKey("app_lock_enabled_v1")
}

fun appLockEnabledFlow(context: Context): Flow<Boolean> =
    context.applicationContext.dataStore.data
        .map { it[AppLockPrefKeys.ENABLED] ?: false }
        .distinctUntilChanged()

@Composable
fun rememberAppLockEnabled(): Boolean {
    val context = LocalContext.current
    val flow = remember(context) { appLockEnabledFlow(context) }
    val enabled by flow.collectAsStateWithLifecycle(initialValue = false)
    return enabled
}

/**
 * The lock is "armed" while the setting is on and the owner hasn't ended this session: Back at
 * the app's root is held, and the lock indicator shows.
 */
@Composable
fun rememberAppLockArmed(): Boolean {
    val enabled = rememberAppLockEnabled()
    val sessionEnded by AppLock.sessionEnded.collectAsStateWithLifecycle()
    return enabled && !sessionEnded
}

fun Context.findHostActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

object AppLock {
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private val _pinned = MutableStateFlow(false)
    /** Android is keeping the app pinned to the screen right now. */
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()

    private val _sessionEnded = MutableStateFlow(false)
    /** The owner unlocked this visit; the lock re-arms the next time the app is opened. */
    val sessionEnded: StateFlow<Boolean> = _sessionEnded.asStateFlow()

    /** One prompt at a time: a double tap must not stack two biometric dialogs. */
    private val promptMutex = Mutex()

    fun refresh(context: Context) {
        _pinned.value = isPinned(context)
    }

    private fun isPinned(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        return manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /** True when the phone has a fingerprint (strong biometric) or a PIN / pattern / password. */
    fun canAuthenticateOwner(context: Context): Boolean {
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Android's own biometric prompt, with the device credential as fallback. Returns true only
     * on success; a cancel or an error returns false. A single failed fingerprint keeps the
     * prompt open for another try.
     */
    private suspend fun authenticate(activity: Activity, title: String, subtitle: String): Boolean {
        if (!promptMutex.tryLock()) return false
        try {
            return suspendCancellableCoroutine<Boolean> { continuation ->
                val cancel = CancellationSignal()
                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(AUTHENTICATORS)
                    .setConfirmationRequired(false)
                    .build()
                prompt.authenticate(
                    cancel,
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                )
                continuation.invokeOnCancellation { cancel.cancel() }
            }
        } finally {
            promptMutex.unlock()
        }
    }

    /** Asks Android to pin the app. Android shows its own confirmation; nothing else happens here. */
    private fun requestPin(activity: Activity) {
        if (isPinned(activity)) {
            refresh(activity)
            return
        }
        runCatching { activity.startLockTask() }
        refresh(activity)
    }

    private fun releasePin(activity: Activity) {
        if (isPinned(activity)) runCatching { activity.stopLockTask() }
        refresh(activity)
    }

    /**
     * Settings toggle. Turning on needs the owner's authentication, then pins; turning off needs
     * it too, then unpins. On a failed or cancelled prompt nothing changes and false is returned.
     */
    suspend fun setEnabled(activity: Activity, enable: Boolean): Boolean {
        val context = activity.applicationContext
        val canAuthenticate = canAuthenticateOwner(activity)
        if (enable) {
            if (!canAuthenticate) {
                toast(context, "Set up a fingerprint or screen lock on this phone first")
                return false
            }
            val ok = authenticate(
                activity,
                title = "Turn on App Lock",
                subtitle = "Confirm it's you to lock PixelPlayer to the screen"
            )
            if (!ok) return false
            context.dataStore.edit { it[AppLockPrefKeys.ENABLED] = true }
            _sessionEnded.value = false
            requestPin(activity)
            toast(context, "App lock enabled ✓")
            return true
        }
        // With no fingerprint or screen lock left on the phone there is nothing to confirm with
        // (and Android's own unpin needs no PIN either), so don't trap the owner.
        if (canAuthenticate) {
            val ok = authenticate(
                activity,
                title = "Turn off App Lock",
                subtitle = "Confirm it's you to turn off App Lock"
            )
            if (!ok) return false
        }
        releasePin(activity)
        context.dataStore.edit { it[AppLockPrefKeys.ENABLED] = false }
        _sessionEnded.value = false
        toast(context, "App lock turned off")
        return true
    }

    /** "End locked session": the owner authenticates, the pin is released, the setting stays on. */
    suspend fun endSession(activity: Activity): Boolean {
        if (canAuthenticateOwner(activity)) {
            val ok = authenticate(
                activity,
                title = "Unlock PixelPlayer",
                subtitle = "Confirm it's you to end the locked session"
            )
            if (!ok) return false
        }
        releasePin(activity)
        _sessionEnded.value = true
        toast(activity.applicationContext, "Unlocked · you can leave the app")
        return true
    }

    /** Lock again now (no authentication: locking is never the risky direction). */
    fun lockNow(activity: Activity) {
        _sessionEnded.value = false
        requestPin(activity)
    }

    /** A new visit to the app with the setting on: re-arm and ask Android to pin again. */
    internal fun onNewVisit(activity: Activity) {
        _sessionEnded.value = false
        requestPin(activity)
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Call once from the main activity's content. With the setting on, each visit to the app
 * (cold start, or coming back after leaving) asks Android to pin it again. Pauses that aren't a
 * visit (the biometric prompt itself, permission dialogs, Android's pin confirmation) don't count,
 * so there are no prompt loops, and nothing about playback is touched.
 */
@Composable
fun AppLockSessionEffect(activity: Activity) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val appContext = activity.applicationContext

    DisposableEffect(lifecycleOwner, activity) {
        var newVisit = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> newVisit = true
                Lifecycle.Event.ON_RESUME -> {
                    AppLock.refresh(activity)
                    if (newVisit) {
                        newVisit = false
                        scope.launch {
                            if (!appLockEnabledFlow(appContext).first()) return@launch
                            // Let the window settle: pinning is only granted to the resumed,
                            // focused activity.
                            delay(400)
                            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                AppLock.onNewVisit(activity)
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android's own unpin gesture ends pinning without telling the app; keep the state current
    // while the app is on screen.
    LaunchedEffect(lifecycleOwner, activity) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                AppLock.refresh(activity)
                delay(1_000)
            }
        }
    }
}
