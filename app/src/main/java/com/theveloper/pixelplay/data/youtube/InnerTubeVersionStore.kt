package com.theveloper.pixelplay.data.youtube

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the YouTube Music web client version scraped from the homepage, so a cold start
 * can search immediately instead of downloading the whole homepage first. Only values that
 * were really served by YouTube are stored; a rejected one is cleared by the caller.
 *
 * Also keeps the visitor data used by the native player request, so the first play after a
 * restart doesn't have to fetch it before asking for the audio manifest.
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

    /** Visitor data and the time it was issued, or null if none was stored. */
    open fun loadVisitor(): Pair<String, Long>? {
        val prefs = prefs ?: return null
        val savedAt = prefs.getLong(KEY_VISITOR_SAVED_AT, 0L).takeIf { it > 0L } ?: return null
        val value = prefs.getString(KEY_VISITOR, null)?.takeIf { it.isNotBlank() } ?: return null
        return value to savedAt
    }

    open fun saveVisitor(visitorData: String, now: Long = System.currentTimeMillis()) {
        prefs?.edit()?.putString(KEY_VISITOR, visitorData)?.putLong(KEY_VISITOR_SAVED_AT, now)?.apply()
    }

    open fun clearVisitor() {
        prefs?.edit()?.remove(KEY_VISITOR)?.remove(KEY_VISITOR_SAVED_AT)?.apply()
    }

    private companion object {
        const val KEY_VERSION = "web_remix_version"
        const val KEY_SAVED_AT = "saved_at"
        const val KEY_VISITOR = "visionos_visitor_data"
        const val KEY_VISITOR_SAVED_AT = "visionos_visitor_saved_at"
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
