package com.theveloper.pixelplay.data.presence

import android.app.Activity
import com.theveloper.pixelplay.BuildConfig
import timber.log.Timber

/**
 * Thin JNI wrapper around the Discord Social SDK (native, see app/src/main/discord). Every native
 * call must run on [DiscordPresenceManager]'s single SDK thread. The SDK itself is optional at
 * build time: without it [available] is false and nothing here touches native code.
 */
internal object DiscordSocialSdkBridge {
    private const val TAG = "DiscordSdk"

    val available: Boolean by lazy {
        if (!BuildConfig.DISCORD_SOCIAL_SDK) return@lazy false
        try {
            System.loadLibrary("pixelplay_discord")
            true
        } catch (e: UnsatisfiedLinkError) {
            Timber.tag(TAG).w(e, "Discord SDK library missing")
            false
        }
    }

    /**
     * MainActivity.onCreate: hands the SDK the activity. By reflection, so the app compiles
     * without the SDK.
     */
    fun attachActivity(activity: Activity) {
        if (!BuildConfig.DISCORD_SOCIAL_SDK) return
        try {
            val init = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit")
            val method = init.methods.firstOrNull { m ->
                m.name == "setEngineActivity" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0].isAssignableFrom(activity.javaClass)
            } ?: error("setEngineActivity not found")
            method.invoke(null, activity)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "setEngineActivity failed")
        }
    }

    /** Creates the client (once per [applicationId]). */
    external fun nativeInit(applicationId: Long): Boolean

    /** Sets the status. [endMs] 0 = no end; null image/text = none. */
    external fun nativeUpdate(
        listening: Boolean,
        details: String,
        state: String,
        startMs: Long,
        endMs: Long,
        largeImage: String?,
        largeText: String?,
    )

    /** Removes the status. */
    external fun nativeClear()

    /** Runs the SDK's pending callbacks (results of updates). */
    external fun nativeRunCallbacks()

    /** The last update's error, or null; clears it. */
    external fun nativeTakeError(): String?

    /** Drops the client. */
    external fun nativeShutdown()
}
