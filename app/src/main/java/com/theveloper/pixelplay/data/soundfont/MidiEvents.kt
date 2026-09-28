package com.theveloper.pixelplay.data.soundfont

/**
 * Channel events of a Standard MIDI File, in playback order with their time in ms (tempo changes
 * applied). Meta and SysEx events are dropped. Format 0 and 1 files are both read.
 */
class MidiEvents private constructor(
    val timesMs: DoubleArray,
    /** status | d1 << 8 | d2 << 16 */
    val packed: IntArray,
) {
    val size: Int get() = timesMs.size

    fun status(i: Int) = packed[i] and 0xFF
    fun data1(i: Int) = (packed[i] shr 8) and 0xFF
    fun data2(i: Int) = (packed[i] shr 16) and 0xFF

    /** Index of the first event at or after [ms]. */
    fun indexAt(ms: Double): Int {
        var lo = 0
        var hi = timesMs.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (timesMs[mid] < ms) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        fun parse(bytes: ByteArray): MidiEvents {
            fun u8(i: Int) = bytes[i].toInt() and 0xFF
            fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
            fun u32(i: Int) = (u8(i) shl 24) or (u8(i + 1) shl 16) or (u8(i + 2) shl 8) or u8(i + 3)
            require(bytes.size >= 14 && String(bytes, 0, 4, Charsets.US_ASCII) == "MThd") { "Not a MIDI file" }
            val headerLen = u32(4)
            val tracks = u16(10)
            val division = u16(12)
            require(division and 0x8000 == 0) { "SMPTE time is not supported" }
            val ppq = division.coerceAtLeast(1)

            // (tick, order, packed) for channel events; (tick, usPerQuarter) for tempo.
            class Raw(val tick: Long, val seq: Int, val packed: Int)
            val raws = ArrayList<Raw>()
            val tempos = ArrayList<Pair<Long, Int>>()
            var p = 8 + headerLen
            var seq = 0
            repeat(tracks) {
                if (p + 8 > bytes.size || String(bytes, p, 4, Charsets.US_ASCII) != "MTrk") return@repeat
                val len = u32(p + 4)
                var i = p + 8
                val end = minOf(bytes.size, i + len)
                var tick = 0L
                var running = 0
                fun vlq(): Long {
                    var v = 0L
                    while (i < end) {
                        val b = u8(i++)
                        v = (v shl 7) or (b and 0x7F).toLong()
                        if (b and 0x80 == 0) break
                    }
                    return v
                }
                while (i < end) {
                    tick += vlq()
                    if (i >= end) break
                    var status = u8(i)
                    if (status < 0x80) status = running else i++
                    when {
                        status == 0xFF -> {
                            val type = u8(i++)
                            val l = vlq().toInt()
                            if (type == 0x51 && l == 3 && i + 3 <= end) tempos += tick to ((u8(i) shl 16) or (u8(i + 1) shl 8) or u8(i + 2))
                            i += l
                            if (type == 0x2F) i = end
                        }
                        status == 0xF0 || status == 0xF7 -> { val l = vlq().toInt(); i += l }
                        status >= 0x80 -> {
                            running = status
                            val type = status and 0xF0
                            val d1 = if (i < end) u8(i++) else 0
                            val d2 = if (type != 0xC0 && type != 0xD0 && i < end) u8(i++) else 0
                            raws += Raw(tick, seq++, status or (d1 shl 8) or (d2 shl 16))
                        }
                        else -> i++
                    }
                }
                p += 8 + len
            }
            raws.sortWith(compareBy<Raw>({ it.tick }, { it.seq }))
            tempos.sortBy { it.first }

            val times = DoubleArray(raws.size)
            var tIdx = 0
            var usPerQ = 500_000
            var lastTick = 0L
            var ms = 0.0
            for ((k, r) in raws.withIndex()) {
                while (tIdx < tempos.size && tempos[tIdx].first <= r.tick) {
                    ms += (tempos[tIdx].first - lastTick) * usPerQ / 1000.0 / ppq
                    lastTick = tempos[tIdx].first
                    usPerQ = tempos[tIdx].second
                    tIdx++
                }
                times[k] = ms + (r.tick - lastTick) * usPerQ / 1000.0 / ppq
            }
            return MidiEvents(times, IntArray(raws.size) { raws[it].packed })
        }
    }
}
