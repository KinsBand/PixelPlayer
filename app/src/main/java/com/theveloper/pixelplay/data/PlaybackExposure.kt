package com.theveloper.pixelplay.data

/** Pure media interval accounting. Call discontinuity before accepting a new playhead. */
class PlaybackExposure(positionMs: Long, realtimeMs: Long, playing: Boolean) {
    private val intervals = mutableListOf<LongRange>()
    private var position = positionMs.coerceAtLeast(0)
    private var realtime = realtimeMs
    private var advancing = playing
    var activeMs = 0L
        private set
    var mediaMs = 0L
        private set
    var seeks = 0
        private set
    val uniqueMs: Long get() = intervals.sumOf { it.last - it.first }
    val repeatedMs: Long get() = (mediaMs - uniqueMs).coerceAtLeast(0)

    fun sample(positionMs: Long, nowMs: Long, playing: Boolean, speed: Float = 1f) {
        val end = positionMs.coerceAtLeast(0)
        val wall = (nowMs - realtime).coerceAtLeast(0)
        val delta = end - position
        // Unexpected jumps are censored, e.g. a remote player missing a seek callback.
        if (advancing && delta > 0 && delta <= wall * speed.coerceIn(0.1f, 8f) + 500) {
            activeMs += minOf(wall, (delta / speed.coerceIn(0.1f, 8f)).toLong())
            mediaMs += delta
            merge(position, end)
        }
        position = end
        realtime = nowMs
        advancing = playing
    }

    fun discontinuity(oldMs: Long, newMs: Long, nowMs: Long, playing: Boolean, speed: Float = 1f) {
        sample(oldMs, nowMs, playing, speed)
        position = newMs.coerceAtLeast(0)
        seeks++
    }

    fun coverage(durationMs: Long): Double = if (durationMs > 0) (uniqueMs.toDouble() / durationMs).coerceIn(0.0, 1.0) else 0.0

    private fun merge(start: Long, end: Long) {
        var left = start
        var right = end
        val iterator = intervals.iterator()
        while (iterator.hasNext()) {
            val interval = iterator.next()
            if (interval.first <= right && interval.last >= left) {
                left = minOf(left, interval.first)
                right = maxOf(right, interval.last)
                iterator.remove()
            }
        }
        intervals.add(left..right)
        intervals.sortBy { it.first }
    }
}

enum class MixEndReason { NATURAL, SKIP, DISLIKE, INTERRUPTION, ERROR, UNKNOWN }
