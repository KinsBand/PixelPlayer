package com.theveloper.pixelplay.data.recognition

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.theveloper.pixelplay.presentation.viewmodel.VoiceSearchStateHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads the song Google's song search found (Listen / Hum) off the Google app's result page, so
 * it lands in Recently heard and the user is brought back to where they were.
 *
 * Scoped as tightly as Android allows: its config only listens to the Google app
 * (`res/xml/song_search_reader_config.xml`), and it does nothing unless
 * [VoiceSearchStateHolder.activeExternalSearch] says PixelPlayer just opened Google's song search
 * (a ~45 s window). Nothing else is read or stored. Separate from the Dynamic Island's
 * [com.theveloper.pixelplay.ui.overlay.CutoutIslandAccessibilityService], which reads nothing.
 */
@AndroidEntryPoint
class SongSearchResultReader : AccessibilityService() {

    @Inject
    lateinit var voiceSearchStateHolder: VoiceSearchStateHolder

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val confirmer = SongSearchDelivery.Confirmer()
    private var deliveryJob: Job? = null
    private val recheck = Runnable { scan() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        Timber.d("SongSearchResultReader: connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != SongSearchDelivery.GSA_PACKAGE) return
        scan()
    }

    private fun scan() {
        handler.removeCallbacks(recheck)
        if (voiceSearchStateHolder.activeExternalSearch() == null) {
            confirmer.reset()
            return
        }
        if (deliveryJob?.isActive == true) return
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        if (root.packageName?.toString() != SongSearchDelivery.GSA_PACKAGE) return

        val snapshot = collectTexts(root)
        val result = GsaResultParser.parse(snapshot.texts, snapshot.searchBox)
        when (val step = confirmer.offer(result, SystemClock.elapsedRealtime())) {
            is SongSearchDelivery.Step.Deliver -> {
                deliveryJob = scope.launch {
                    val delivered = SongSearchDelivery.deliver(
                        context = applicationContext,
                        holder = voiceSearchStateHolder,
                        result = step.result
                    )
                    if (delivered) confirmer.reset()
                }
            }
            // The page may settle without firing another event; look again shortly.
            is SongSearchDelivery.Step.Recheck -> handler.postDelayed(recheck, step.afterMs)
            SongSearchDelivery.Step.Idle -> Unit
        }
    }

    private class Snapshot(val texts: List<String>, val searchBox: String?)

    /** Visible texts and content descriptions in tree (reading) order; capped in size and depth. */
    private fun collectTexts(root: AccessibilityNodeInfo): Snapshot {
        val texts = ArrayList<String>()
        var searchBox: String? = null
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = stack.removeAt(stack.lastIndex)
            visited++
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val isEditText = node.className?.toString()?.contains("EditText") == true
            if (isEditText) {
                if (searchBox == null && text.isNotEmpty()) searchBox = text
            } else {
                if (text.isNotEmpty()) texts.add(text)
                if (desc.isNotEmpty() && desc != text) texts.add(desc)
            }
            if (depth < MAX_DEPTH) {
                // Push children in reverse so they pop in order.
                for (i in node.childCount - 1 downTo 0) {
                    val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                    stack.addLast(child to depth + 1)
                }
            }
        }
        return Snapshot(texts, searchBox)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        handler.removeCallbacks(recheck)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val MAX_NODES = 800
        private const val MAX_DEPTH = 40

        /** True while the system has the service bound (it is switched on and running). */
        @Volatile
        var isConnected: Boolean = false
            private set

        fun component(context: Context) = ComponentName(context, SongSearchResultReader::class.java)

        /** Whether the user (or Shizuku) has switched the reader on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val ours = component(context)
            return enabled.split(':').any { ComponentName.unflattenFromString(it.trim()) == ours }
        }
    }
}
