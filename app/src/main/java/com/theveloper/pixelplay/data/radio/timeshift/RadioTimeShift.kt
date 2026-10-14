package com.theveloper.pixelplay.data.radio.timeshift

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Which radio streams are recorded for rewinding, and the small URI convention used to ask the
 * player to start somewhere in the recording.
 *
 * A recorded station still plays its normal stream URL (so casting, the queue and the saved
 * session are unchanged). The player's data source sees that the URL is a registered radio
 * stream and reads it from the recording instead. To start at a point in the recording, the
 * player is given the same URL with a "#pp-shift=<ms>" fragment ("#pp-shift=live-<n>" = live).
 */
object RadioTimeShift {
    /** Kill switch: false plays every station straight from the network, as before. */
    const val ENABLED = true

    /** Rewind up to this far back (your choice: the session, up to 60 minutes). */
    const val MAX_REWIND_MS = 60L * 60 * 1000

    /** Behind live by more than this shows the Live button. */
    const val BEHIND_LIVE_THRESHOLD_MS = 10_000L

    private const val FRAGMENT_KEY = "pp-shift="
    private const val LIVE = "live"

    /** Stream URL → bitrate (kbps, 0 = unknown), for stations that can be recorded. */
    private val streams = ConcurrentHashMap<String, Int>()

    /** Called when a station is turned into a song: Icecast/Shoutcast streams can be recorded. */
    fun register(streamUrl: String, bitrateKbps: Int, isHls: Boolean) {
        if (!ENABLED || isHls || streamUrl.isBlank()) return
        val scheme = Uri.parse(streamUrl).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        streams[streamUrl] = bitrateKbps
    }

    fun bitrateFor(streamUrl: String): Int = streams[streamUrl] ?: 0

    /** The stream URL without our fragment. */
    fun baseUrl(uri: Uri): String {
        val s = uri.toString()
        val hash = s.indexOf("#$FRAGMENT_KEY")
        return if (hash >= 0) s.substring(0, hash) else s
    }

    /** Whether the player should read [uri] from the recording. */
    fun handles(uri: Uri?): Boolean = ENABLED && uri != null && streams.containsKey(baseUrl(uri))

    /** Where in the recording [uri] asks to start (ms), or null for live. */
    fun startMsOf(uri: Uri): Long? {
        val s = uri.toString()
        val at = s.indexOf("#$FRAGMENT_KEY").takeIf { it >= 0 } ?: return null
        val value = s.substring(at + FRAGMENT_KEY.length + 1)
        if (value.startsWith(LIVE)) return null
        return value.toLongOrNull()
    }

    private var liveNonce = 0

    /** The stream URL asking to start at [ms] in the recording (null = live). */
    fun uriFor(streamUrl: String, ms: Long?): String {
        val base = baseUrl(Uri.parse(streamUrl))
        return if (ms == null) "$base#$FRAGMENT_KEY$LIVE-${++liveNonce}" else "$base#$FRAGMENT_KEY${ms.coerceAtLeast(0)}"
    }
}
