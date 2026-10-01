package com.theveloper.pixelplay.data.recognition.shizuku

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.theveloper.pixelplay.data.recognition.ambient.NowPlayingNotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    }

    suspend fun launchHumOrSingSearch(): Result<Unit> = withContext(Dispatchers.IO) {
        if (shizukuManager.status.value == ShizukuStatus.READY) {
            // First attempt: internal AudioSearchActivity via Shizuku shell
            val res1 = shizukuManager.executeShell("am start -n $SOUND_SEARCH_ACTIVITY")
            if (res1.isSuccess) {
                Timber.d("GoogleSearchBridge: Launched hum search via AudioSearchActivity")
                return@withContext Result.success(Unit)
            }

            // Second attempt: action intent via Shizuku
            val res2 = shizukuManager.executeShell("am start -a $SOUND_SEARCH_ACTION")
            if (res2.isSuccess) {
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
}
