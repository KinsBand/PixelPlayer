package com.theveloper.pixelplay.data.recognition

import android.content.Context
import android.content.Intent
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.presentation.viewmodel.ArmedSearch
import com.theveloper.pixelplay.presentation.viewmodel.VoiceSearchStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Shared by [SongSearchResultReader] (accessibility) and
 * [com.theveloper.pixelplay.data.recognition.shizuku.GsaDumpPoller] (Shizuku `uiautomator dump`):
 * confirms a parsed result, hands it to [VoiceSearchStateHolder] and returns the user to the app
 * they were in. Never sends BACK (from Google's results that goes to Google home, not the caller).
 */
object SongSearchDelivery {
    const val GSA_PACKAGE = "com.google.android.googlequicksearchbox"

    /** The same song must be read twice at least this far apart (the page is still loading before). */
    private const val CONFIRM_MS = 500L
    /** A search-box-only reading must hold this long, so the song panel gets a chance to load. */
    private const val QUERY_SETTLE_MS = 3_000L

    sealed interface Step {
        data class Deliver(val result: GsaParseResult) : Step
        data class Recheck(val afterMs: Long) : Step
        data object Idle : Step
    }

    /** Debounces readings: a result is delivered once it's been seen twice, far enough apart. */
    class Confirmer {
        private var candidate: GsaParseResult? = null
        private var firstSeenAt = 0L
        private val triedQueries = mutableSetOf<String>()

        fun reset() {
            candidate = null
            firstSeenAt = 0L
            triedQueries.clear()
        }

        fun offer(result: GsaParseResult, nowElapsed: Long): Step {
            if (result == GsaParseResult.None) return Step.Idle
            if (result is GsaParseResult.QueryOnly && result.query.lowercase() in triedQueries) return Step.Idle
            val window = if (result is GsaParseResult.Song) CONFIRM_MS else QUERY_SETTLE_MS
            if (result != candidate) {
                candidate = result
                firstSeenAt = nowElapsed
                return Step.Recheck(window + 50L)
            }
            val elapsed = nowElapsed - firstSeenAt
            if (elapsed < window) return Step.Recheck(window - elapsed + 50L)
            if (result is GsaParseResult.QueryOnly) triedQueries += result.query.lowercase()
            return Step.Deliver(result)
        }
    }

    /**
     * Delivers [result] and, if the holder accepted it, brings the user back. Returns true once
     * delivered. [shell] (Shizuku) is used to relaunch when given, as the poller has no
     * background-activity-start exemption; the accessibility service does.
     */
    suspend fun deliver(
        context: Context,
        holder: VoiceSearchStateHolder,
        result: GsaParseResult,
        shell: (suspend (String) -> Boolean)? = null
    ): Boolean {
        val armed = when (result) {
            is GsaParseResult.Song -> holder.deliverExternalResult(result.title, result.artist)
            is GsaParseResult.QueryOnly -> holder.deliverExternalQuery(result.query)
            GsaParseResult.None -> null
        } ?: return false
        Timber.d("SongSearchDelivery: delivered %s, returning to %s", result, armed.returnPackage)
        returnToCaller(context, armed, shell)
        return true
    }

    /**
     * Brings back the app the user was in. A null return package means it couldn't be known
     * (launched from outside PixelPlayer without Shizuku), so the user stays on Google's result
     * instead of being sent somewhere guessed.
     */
    suspend fun returnToCaller(
        context: Context,
        armed: ArmedSearch,
        shell: (suspend (String) -> Boolean)? = null
    ) {
        val pkg = armed.returnPackage ?: return
        val intent = if (pkg == context.packageName) {
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
        } else {
            context.packageManager.getLaunchIntentForPackage(pkg) ?: run {
                Timber.d("SongSearchDelivery: no launch intent for %s", pkg)
                return
            }
        }
        // Same flags the launcher uses, so the existing task comes to the front as it was.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        val component = intent.component
        if (shell != null && component != null) {
            val launched = shell(
                "am start -a ${Intent.ACTION_MAIN} -c ${Intent.CATEGORY_LAUNCHER} " +
                    "-f 0x${Integer.toHexString(intent.flags)} -n '${component.flattenToShortString()}'"
            )
            if (launched) return
        }
        withContext(Dispatchers.Main) {
            runCatching { context.startActivity(intent) }
                .onFailure { Timber.w(it, "SongSearchDelivery: could not return to %s", pkg) }
        }
    }
}
