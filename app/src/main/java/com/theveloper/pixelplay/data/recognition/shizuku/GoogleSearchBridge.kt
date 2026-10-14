package com.theveloper.pixelplay.data.recognition.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.theveloper.pixelplay.data.recognition.SongSearchResultReader
import com.theveloper.pixelplay.data.recognition.ambient.NowPlayingNotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSearchBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    companion object {
        private const val GSA_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val SOUND_SEARCH_ACTIVITY = "$GSA_PACKAGE/com.google.android.apps.gsa.soundsearch.AudioSearchActivity"
        private const val SOUND_SEARCH_ACTION = "$GSA_PACKAGE.action.INTENT_SOUND_SEARCH"
        private const val MUSIC_SEARCH_ACTION = "$GSA_PACKAGE.MUSIC_SEARCH"
        private val RESUMED_ACTIVITY = Regex("""ActivityRecord\{\S+\s+u\d+\s+([A-Za-z0-9_.]+)/""")
    }

    /**
     * `am start` can exit 0 yet print "Error: Activity class ... does not exist" (or
     * "Error type 3"); treat that output as a failure too.
     */
    private suspend fun amStart(args: String): Boolean {
        val result = shizukuManager.executeShell("am start $args")
        val output = result.getOrNull() ?: return false
        if (output.contains("Error", ignoreCase = false)) {
            Timber.d("GoogleSearchBridge: am start %s reported: %s", args, output)
            return false
        }
        return true
    }

    suspend fun launchHumOrSingSearch(): Result<Unit> = withContext(Dispatchers.IO) {
        if (shizukuManager.status.value == ShizukuStatus.READY) {
            // First attempt: internal AudioSearchActivity via Shizuku shell
            if (amStart("-n $SOUND_SEARCH_ACTIVITY")) {
                Timber.d("GoogleSearchBridge: Launched hum search via AudioSearchActivity")
                return@withContext Result.success(Unit)
            }

            // Second attempt: action intent via Shizuku
            if (amStart("-a $SOUND_SEARCH_ACTION")) {
                Timber.d("GoogleSearchBridge: Launched hum search via SOUND_SEARCH_ACTION")
                return@withContext Result.success(Unit)
            }
        }

        // Fallback: Launch via Android Intent directly
        withContext(Dispatchers.Main) {
            try {
                val intent = Intent(SOUND_SEARCH_ACTION).apply {
                    setPackage(GSA_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(Unit)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(MUSIC_SEARCH_ACTION).apply {
                        setPackage(GSA_PACKAGE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    Result.success(Unit)
                } catch (e2: Exception) {
                    try {
                        // Chrome or web intent fallback
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                        Result.success(Unit)
                    } catch (e3: Exception) {
                        Timber.e(e3, "GoogleSearchBridge: Failed to launch hum search")
                        Result.failure(e3)
                    }
                }
            }
        }
    }

    suspend fun launchSoundSearch(): Result<Unit> = withContext(Dispatchers.IO) {
        launchHumOrSingSearch()
    }

    /**
     * Makes sure Now Playing notifications reach [NowPlayingNotificationListener].
     * Already allowed → true. With Shizuku it is granted silently; otherwise Android's
     * notification-access screen opens (returns false — the user has to flip it there).
     */
    suspend fun ensureNotificationListenerAccess(): Boolean {
        if (NowPlayingNotificationListener.isAccessGranted(context)) return true
        val component = NowPlayingNotificationListener.component(context).flattenToString()
        if (shizukuManager.status.value == ShizukuStatus.READY) {
            val granted = withContext(Dispatchers.IO) {
                shizukuManager.executeShell(
                    "cmd notification allow_listener $component"
                ).isSuccess
            }
            if (granted && NowPlayingNotificationListener.isAccessGranted(context)) return true
        }
        withContext(Dispatchers.Main) {
            runCatching {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        component
                    )
                } else {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.recoverCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { Timber.w(it, "GoogleSearchBridge: could not open notification access settings") }
        }
        return false
    }

    /** Kept for older callers. */
    suspend fun grantNotificationListenerAccess(): Boolean = ensureNotificationListenerAccess()

    /**
     * Makes sure [SongSearchResultReader] (reads the found song off Google's result page) is on.
     * Already on → true. With Shizuku it is switched on silently by appending our component to
     * `enabled_accessibility_services` (other services, e.g. the Dynamic Island one, are kept).
     * Without Shizuku → false; the voice sheet explains how to turn it on by hand.
     */
    suspend fun ensureResultReaderEnabled(): Boolean {
        if (SongSearchResultReader.isEnabled(context)) return true
        if (shizukuManager.status.value != ShizukuStatus.READY) return false
        val ours = SongSearchResultReader.component(context)
        val flattened = ours.flattenToString()
        return withContext(Dispatchers.IO) {
            val current = shizukuManager.executeShell("settings get secure enabled_accessibility_services")
                .getOrNull() ?: return@withContext false
            val existing = current.trim()
                .takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
                .orEmpty()
                .split(':')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val present = existing.any { ComponentName.unflattenFromString(it) == ours }
            if (!present) {
                val value = (existing + flattened).joinToString(":")
                val put = shizukuManager.executeShell("settings put secure enabled_accessibility_services '$value'")
                if (put.isFailure) {
                    Timber.w(put.exceptionOrNull(), "GoogleSearchBridge: could not enable the result reader")
                    return@withContext false
                }
            }
            shizukuManager.executeShell("settings put secure accessibility_enabled 1")
            // Give the system a moment to bind the service before Google opens.
            withTimeoutOrNull(1_500L) {
                while (!SongSearchResultReader.isConnected) delay(100L)
            }
            SongSearchResultReader.isEnabled(context)
        }
    }

    /**
     * Package of the app on top right now (Shizuku only), parsed from
     * `ActivityRecord{a1b2c3 u0 com.foo/.Bar t12}`. Null without Shizuku or if unreadable.
     */
    suspend fun foregroundPackage(): String? {
        if (shizukuManager.status.value != ShizukuStatus.READY) return null
        val line = withContext(Dispatchers.IO) {
            shizukuManager.executeShell(
                "dumpsys activity activities | grep -m1 -E \"topResumedActivity|mResumedActivity\""
            ).getOrNull()
        } ?: return null
        return RESUMED_ACTIVITY.find(line)?.groupValues?.get(1)
    }
}
