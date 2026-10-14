package com.theveloper.pixelplay.data.radio.timeshift

import kotlin.math.max

/**
 * Byte ↔ time bookkeeping for one recorded radio stream (pure Kotlin, unit-tested).
 *
 * The recording is a run of bytes from the start of the session. Times are "recording
 * milliseconds" from that start, worked out from the stream's bitrate (constant for MP3 and
 * close to it for AAC radio). The bytes are kept in fixed-size segments; once more than
 * [maxDurationMs] is kept, whole segments are dropped from the front.
 */
class RadioTimeShiftIndex(
    bitrateKbps: Int,
    val maxDurationMs: Long,
    private val segmentDurationMs: Long = DEFAULT_SEGMENT_MS,
) {
    /** Bytes per millisecond of audio (kbps / 8). Fixed once bytes have been written. */
    var bytesPerMs: Double = bytesPerMsFor(bitrateKbps)
        private set

    /** Bytes per segment file (about a minute of audio). */
    var segmentBytes: Long = segmentBytesFor(bytesPerMs)
        private set

    /** Total bytes recorded since the session started (the live edge). */
    var written: Long = 0
        private set

    /** The oldest byte still kept. */
    var firstAvailable: Long = 0
        private set

    /** The stream told us its bitrate before any audio arrived: use it. Ignored afterwards. */
    fun setBitrateIfEmpty(kbps: Int) {
        if (written > 0 || kbps <= 0) return
        bytesPerMs = bytesPerMsFor(kbps)
        segmentBytes = segmentBytesFor(bytesPerMs)
    }

    fun segmentOf(offset: Long): Long = offset / segmentBytes
    fun segmentStart(segment: Long): Long = segment * segmentBytes

    /**
     * Records [count] new bytes. Returns the segments that fell out of the kept window and
     * should be deleted (oldest first).
     */
    fun onWritten(count: Int): List<Long> {
        if (count <= 0) return emptyList()
        written += count
        val maxBytes = (bytesPerMs * maxDurationMs).toLong()
        val keepFrom = written - maxBytes
        if (keepFrom <= firstAvailable) return emptyList()
        // Only whole segments go: the segment holding keepFrom stays.
        val newFirstSegment = segmentOf(keepFrom)
        val oldFirstSegment = segmentOf(firstAvailable)
        if (newFirstSegment <= oldFirstSegment) return emptyList()
        firstAvailable = segmentStart(newFirstSegment)
        return (oldFirstSegment until newFirstSegment).toList()
    }

    fun msAt(offset: Long): Long = (offset / bytesPerMs).toLong()
    fun offsetAt(ms: Long): Long = (ms * bytesPerMs).toLong()

    val liveEdgeMs: Long get() = msAt(written)
    val firstAvailableMs: Long get() = msAt(firstAvailable)

    /**
     * Where playback asked to start at [atMs] begins: clamped into what's kept. Null = live,
     * which starts [liveLeadMs] back from the edge (so the player has audio straight away).
     */
    fun startOffsetFor(atMs: Long?, liveLeadMs: Long = DEFAULT_LIVE_LEAD_MS): Long {
        val wanted = if (atMs == null) written - offsetAt(liveLeadMs) else offsetAt(atMs)
        return wanted.coerceIn(firstAvailable, max(firstAvailable, written))
    }

    companion object {
        const val DEFAULT_SEGMENT_MS = 60_000L
        const val DEFAULT_LIVE_LEAD_MS = 3_000L
        const val DEFAULT_BITRATE_KBPS = 128
        private const val MIN_SEGMENT_BYTES = 64L * 1024

        fun bytesPerMsFor(kbps: Int): Double = (if (kbps > 0) kbps else DEFAULT_BITRATE_KBPS).coerceIn(16, 512) / 8.0

        private fun segmentBytesFor(bytesPerMs: Double): Long =
            max(MIN_SEGMENT_BYTES, (bytesPerMs * DEFAULT_SEGMENT_MS).toLong())
    }
}
