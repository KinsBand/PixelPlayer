package com.theveloper.pixelplay.data.songsterr

/**
 * When each bar plays.
 *
 * Repeat signs and alternate endings are unrolled into the order the bars are actually played.
 * Bar start times come from Songsterr's video sync points when there are any (they follow the
 * real recording, including tempo drift), otherwise from the tab's tempo map.
 */
class TabTimeline(
    val track: RenderedTrack,
    syncPointsSeconds: List<Double>? = null,
) {
    data class Entry(
        /** Index into [RenderedTrack.measures]. */
        val measure: Int,
        /** Start in the recording (ms), speed 1. */
        val startMs: Double,
        val durationMs: Double,
        /** Tempo in effect (quarter-note bpm) — used by the synth and metronome. */
        val bpm: Double,
    ) {
        val endMs: Double get() = startMs + durationMs
    }

    data class Position(
        val entry: Int,
        val measure: Int,
        /** Ticks into the bar. */
        val tick: Long,
        /** 0..1 through the bar. */
        val fraction: Float,
    )

    /** Bars in playing order. */
    val order: List<Int> = playOrder(track.measures)

    val usesSyncPoints: Boolean

    val entries: List<Entry>

    init {
        val measures = track.measures
        val points = syncPointsSeconds?.takeIf { it.size >= 2 && it.size >= order.size / 2 }
        usesSyncPoints = points != null
        val list = ArrayList<Entry>(order.size)
        var t = 0.0
        order.forEachIndexed { k, mi ->
            val m = measures[mi]
            val tempoMs = m.lengthTicks.toDouble() / TabParser.TICKS_PER_QUARTER * 60_000.0 / m.bpm.coerceAtLeast(1.0)
            val start = points?.getOrNull(k)?.let { it * 1000.0 } ?: t
            val nextPoint = points?.getOrNull(k + 1)?.let { it * 1000.0 }
            val dur = if (nextPoint != null && nextPoint > start) nextPoint - start else tempoMs
            list += Entry(mi, start, dur, m.bpm)
            t = start + dur
        }
        entries = list
    }

    val totalMs: Double get() = entries.lastOrNull()?.endMs ?: 0.0

    /** First time bar [measure] is played, or -1. */
    fun firstEntryOf(measure: Int): Int = entries.indexOfFirst { it.measure == measure }

    /** Last time bar [measure] is played at or before [fromEntry] (or the first time after it). */
    fun entryOfMeasureNear(measure: Int, fromEntry: Int): Int {
        var best = -1
        for (i in entries.indices) {
            if (entries[i].measure != measure) continue
            if (i <= fromEntry) best = i else return if (best >= 0) best else i
        }
        return best
    }

    fun locate(ms: Double): Position? {
        if (entries.isEmpty()) return null
        if (ms < entries.first().startMs) return Position(0, entries.first().measure, 0, 0f)
        var lo = 0
        var hi = entries.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (entries[mid].startMs <= ms) lo = mid else hi = mid - 1
        }
        val e = entries[lo]
        val f = ((ms - e.startMs) / e.durationMs).coerceIn(0.0, 1.0)
        val length = track.measures[e.measure].lengthTicks
        return Position(lo, e.measure, (f * length).toLong().coerceAtMost(length - 1), f.toFloat())
    }

    fun msAt(entry: Int, tick: Long): Double {
        val e = entries.getOrNull(entry) ?: return totalMs
        val length = track.measures[e.measure].lengthTicks.toDouble()
        return e.startMs + e.durationMs * (tick / length)
    }

    /**
     * For the tab's own repeat that closes at bar [closing]: how many passes are already done
     * when playing [entry], or null if [entry] isn't inside that repeat.
     */
    fun repeatPass(entry: Int, closing: Int): Int? {
        val e = entries.getOrNull(entry) ?: return null
        val measures = track.measures
        var start = closing
        while (start > 0 && !measures[start].repeatStart && measures[start - 1].repeatCount == null) start--
        if (e.measure !in start..closing) return null
        var done = 0
        var j = entry - 1
        while (j >= 0 && entries[j].measure in start..closing) {
            if (entries[j].measure == closing) done++
            j--
        }
        return done
    }

    companion object {
        /** Unrolls repeats and alternate endings (guarded against malformed data). */
        fun playOrder(measures: List<RenderedMeasure>): List<Int> {
            val order = ArrayList<Int>(measures.size)
            val passesDone = HashMap<Int, Int>()
            var repeatStart = 0
            var pass = 1
            var i = 0
            var jumped = false
            var guard = 0
            while (i < measures.size && guard++ < measures.size * 16) {
                val m = measures[i]
                if (m.repeatStart && !jumped) {
                    repeatStart = i
                    pass = 1
                }
                jumped = false
                if (m.alternateEnding.isNotEmpty() && pass !in m.alternateEnding) {
                    i++
                    continue
                }
                order += i
                val times = m.repeatCount
                if (times != null && times > 1) {
                    val done = passesDone[i] ?: 1
                    if (done < times) {
                        passesDone[i] = done + 1
                        pass = done + 1
                        i = repeatStart
                        jumped = true
                        continue
                    }
                    passesDone.remove(i)
                    pass = 1
                    repeatStart = i + 1
                }
                i++
            }
            return order
        }
    }
}
