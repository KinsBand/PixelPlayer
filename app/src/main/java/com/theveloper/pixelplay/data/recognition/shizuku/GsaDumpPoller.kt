package com.theveloper.pixelplay.data.recognition.shizuku

import com.theveloper.pixelplay.data.recognition.SongSearchDelivery
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shizuku fallback for when [com.theveloper.pixelplay.data.recognition.SongSearchResultReader]
 * isn't running: dumps the screen with `uiautomator dump` every [INTERVAL_MS] while a song search
 * is armed, and hands the Google app's texts (document order) plus its search box text to
 * [onSnapshot]. Stops when [onSnapshot] returns true or [isActive] turns false.
 */
@Singleton
class GsaDumpPoller @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    suspend fun poll(
        isActive: () -> Boolean,
        shouldDump: () -> Boolean,
        onSnapshot: suspend (texts: List<String>, searchBox: String?) -> Boolean
    ) {
        delay(INTERVAL_MS)
        while (isActive()) {
            if (shouldDump() && shizukuManager.status.value == ShizukuStatus.READY) {
                val xml = shizukuManager.executeShell(
                    "uiautomator dump $DUMP_PATH >/dev/null && cat $DUMP_PATH"
                ).getOrNull()
                if (xml != null && xml.contains("package=\"${SongSearchDelivery.GSA_PACKAGE}\"")) {
                    val (texts, searchBox) = parseDump(xml)
                    if (onSnapshot(texts, searchBox)) return
                }
            }
            delay(INTERVAL_MS)
        }
    }

    companion object {
        private const val INTERVAL_MS = 1_200L
        private const val DUMP_PATH = "/data/local/tmp/pp_gsa.xml"

        private val NODE = Regex("""<node\b([^>]*?)/?>""")
        private val ATTR = Regex("""([\w:-]+)="([^"]*)"""")
        private val NUMERIC_ENTITY = Regex("""&#(x?[0-9A-Fa-f]+);""")

        /**
         * Texts (`text=`, then `content-desc=` when different) of the Google app's nodes in
         * document order, and the first EditText's text as the search box.
         */
        fun parseDump(xml: String): Pair<List<String>, String?> {
            val texts = ArrayList<String>()
            var searchBox: String? = null
            for (node in NODE.findAll(xml)) {
                val attrs = ATTR.findAll(node.groupValues[1])
                    .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
                if (attrs["package"] != SongSearchDelivery.GSA_PACKAGE) continue
                val text = attrs["text"]?.trim().orEmpty()
                val desc = attrs["content-desc"]?.trim().orEmpty()
                if (attrs["class"]?.contains("EditText") == true) {
                    if (searchBox == null && text.isNotEmpty()) searchBox = text
                    continue
                }
                if (text.isNotEmpty()) texts.add(text)
                if (desc.isNotEmpty() && desc != text) texts.add(desc)
            }
            return texts to searchBox
        }

        private fun unescape(value: String): String {
            val numeric = NUMERIC_ENTITY.replace(value) { m ->
                val code = m.groupValues[1]
                val cp = runCatching {
                    if (code.startsWith("x")) code.substring(1).toInt(16) else code.toInt()
                }.getOrNull()
                if (cp != null) String(Character.toChars(cp)) else m.value
            }
            return numeric
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
        }
    }
}
