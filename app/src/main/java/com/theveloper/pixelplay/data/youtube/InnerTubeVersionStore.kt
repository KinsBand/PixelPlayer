package com.theveloper.pixelplay.data.youtube

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the YouTube Music web client version scraped from the homepage, so a cold start
 * can search immediately instead of downloading the whole homepage first. Only values that
 * were really served by YouTube are stored; a rejected one is cleared by the caller.
 */
@Singleton
open class InnerTubeVersionStore @Inject constructor(@ApplicationContext context: Context?) {
    private val prefs = context?.getSharedPreferences("innertube_client", Context.MODE_PRIVATE)

    open fun load(now: Long = System.currentTimeMillis()): String? {
        val prefs = prefs ?: return null
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        if (savedAt <= 0L || now - savedAt !in 0..MAX_AGE_MS) return null
        return prefs.getString(KEY_VERSION, null)?.takeIf { it.isNotBlank() }
    }

    open fun save(version: String, now: Long = System.currentTimeMillis()) {
        prefs?.edit()?.putString(KEY_VERSION, version)?.putLong(KEY_SAVED_AT, now)?.apply()
    }

    open fun clear() {
        prefs?.edit()?.remove(KEY_VERSION)?.remove(KEY_SAVED_AT)?.apply()
    }

    private companion object {
        const val KEY_VERSION = "web_remix_version"
        const val KEY_SAVED_AT = "saved_at"
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
