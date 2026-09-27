package com.theveloper.pixelplay.data.diagnostics

import timber.log.Timber

/**
 * Tap-to-audio timing for the most recent play request, logged as one line under the
 * `StreamingLatency` tag when its audio starts:
 *
 * `tap_to_audio_ms=612 song=yt_… stages=dispatch:8,manifest:341(visionos),first_bytes:402(network),audio:612`
 *
 * Stage times are milliseconds since the tap. Only the latest request is tracked (a newer tap
 * replaces it), stages are recorded once, and nothing is logged for a request whose audio never
 * starts. No URLs or queries are recorded. Monotonic time, so clock changes can't skew it.
 */
object PlaybackTrace {
    private class Trace(val songId: String, val startedNanos: Long) {
        val stages = LinkedHashMap<String, String>()
    }

    private val lock = Any()
    private var active: Trace? = null
    private var nanoTime: () -> Long = System::nanoTime
    private var sink: (String) -> Unit = { line -> Timber.tag("StreamingLatency").d(line) }

    /** A play request for [songId] was made (the user's tap). */
    fun begin(songId: String) = synchronized(lock) {
        active = Trace(songId, nanoTime())
    }

    /** Records [stage] for the active request, once. [detail] is a short label (e.g. provider). */
    fun mark(stage: String, detail: String? = null) = synchronized(lock) {
        val trace = active ?: return@synchronized
        if (stage in trace.stages) return@synchronized
        val ms = (nanoTime() - trace.startedNanos) / 1_000_000
        trace.stages[stage] = if (detail == null) "$ms" else "$ms($detail)"
    }

    /** Audio for [songId] started playing; logs and ends the trace if it is the active one. */
    fun audioStarted(songId: String) {
        val line = synchronized(lock) {
            val trace = active?.takeIf { it.songId == songId } ?: return
            active = null
            val total = (nanoTime() - trace.startedNanos) / 1_000_000
            trace.stages["audio"] = "$total"
            "tap_to_audio_ms=$total song=${trace.songId} stages=" +
                trace.stages.entries.joinToString(",") { (stage, value) -> "$stage:$value" }
        }
        sink(line)
    }

    internal fun setForTest(nanoTime: () -> Long, sink: (String) -> Unit) = synchronized(lock) {
        this.nanoTime = nanoTime
        this.sink = sink
        active = null
    }
}
