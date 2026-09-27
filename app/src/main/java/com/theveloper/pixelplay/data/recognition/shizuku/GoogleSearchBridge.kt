package com.theveloper.pixelplay.data.recognition.shizuku

import android.content.Context
import android.content.Intent
import android.net.Uri
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

    suspend fun grantNotificationListenerAccess(): Boolean = withContext(Dispatchers.IO) {
        if (shizukuManager.status.value != ShizukuStatus.READY) return@withContext false
        val pkg = context.packageName
        val service = "$pkg/.data.recognition.ambient.NowPlayingNotificationListener"
        val cmd = "cmd notification set_notification_listener_access_granted $service 1"
        val result = shizukuManager.executeShell(cmd)
        result.isSuccess
    }
}
