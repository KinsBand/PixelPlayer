package com.theveloper.pixelplay.data.songsterr

import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Builds a Standard MIDI File from a tab so Android's built-in General MIDI synth can play it
 * ("SYNTH" mode). Includes an optional click track and a one-bar count-in.
 */
object TabMidi {

    private const val PPQ = 480
    private const val TICK_DIV = (TabParser.TICKS_PER_QUARTER / PPQ).toInt() // 14

    data class Options(
        val speed: Float = 1f,
        /** Semitones added to every pitched note (pitch shift). Ignored for drums. */
        val pitchShift: Int = 0,
        val metronome: Boolean = false,
        val countIn: Boolean = false,
        /** Count-in length in beats; null = one bar. */
        val countInBeats: Int? = null,
        /** Mute the part itself (click only). */
        val muteInstrument: Boolean = false,
        /**
         * Guitar and bass parts are played by [StringSynth] (modelled strings) instead of the
         * General MIDI synth; they come back in [Result.strings].
         */
        val realStrings: Boolean = false,
    )

    data class Result(
        val bytes: ByteArray,
        /** Start of each rendered bar in the file (ms), in file order. */
        val entryStartMs: DoubleArray,
        /** Timeline entry of each rendered bar (a loop repeats the same entries). */
        val entrySeq: IntArray,
        val countInMs: Double,
        val totalMs: Double,
        /** Guitar / bass notes for [StringSynth] (null when [Options.realStrings] is off or there are none). */
        val strings: StringScore? = null,
    ) {
        /** Timeline entry + fraction for a playback position in this file. */
        fun locate(ms: Double): Pair<Int, Float>? {
            if (entryStartMs.isEmpty() || ms < countInMs) return null
            var i = 0
            while (i + 1 < entryStartMs.size && entryStartMs[i + 1] <= ms) i++
            val end = if (i + 1 < entryStartMs.size) entryStartMs[i + 1] else totalMs
            val f = ((ms - entryStartMs[i]) / (end - entryStartMs[i]).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
            return entrySeq[i] to f.toFloat()
        }
    }

    private class Ev(val tick: Long, val order: Int, val data: ByteArray)

    /** One instrument to play. All parts of a song share bar lengths and tempo. */
    data class Part(val track: RenderedTrack, val muted: Boolean = false, val isVocal: Boolean = false)

    private val DYNAMICS = mapOf(
        "ppp" to 30, "pp" to 42, "p" to 55, "mp" to 68, "mf" to 82, "f" to 96, "ff" to 110, "fff" to 124,
    )

    fun build(
        timeline: TabTimeline,
        fromEntry: Int,
        toEntryExclusive: Int,
        options: Options,
        parts: List<Part> = listOf(Part(timeline.track)),
    ): Result {
        val from = fromEntry.coerceIn(0, timeline.entries.size)
        val to = toEntryExclusive.coerceIn(from, timeline.entries.size)
        return build(timeline, (from until to).toList(), options, parts)
    }

    /**
     * Renders timeline entries in the given order. A loop is written out several times in one
     * file, so it repeats without any gap.
     */
    fun build(
        timeline: TabTimeline,
        sequence: List<Int>,
        options: Options,
        parts: List<Part> = listOf(Part(timeline.track)),
    ): Result {
        val track = timeline.track
        val speed = options.speed.coerceIn(0.1f, 3f)
        val events = ArrayList<Ev>()
        val seq = sequence.filter { it in timeline.entries.indices }

        // Channels: drums on 10 (index 9), everything else on its own channel.
        val free = ArrayDeque((0..15).filter { it != 9 })
        val channels = parts.map { p -> if (p.track.isDrums) 9 else (free.removeFirstOrNull() ?: 15) }
        parts.forEachIndexed { i, p ->
            if (p.track.isDrums) return@forEachIndexed
            val program = if (p.isVocal) 53 else p.track.instrumentId.takeIf { it in 0..127 } ?: 29
            events += Ev(0, 0, byteArrayOf((0xC0 or channels[i]).toByte(), program.toByte()))
        }

        var tick = 0L
        var ms = 0.0
        var currentBpm = -1.0
        fun tempo(bpm: Double) {
            if (bpm == currentBpm) return
            currentBpm = bpm
            val us = (60_000_000.0 / (bpm * speed)).roundToLong().coerceIn(1, 0xFFFFFF)
            events += Ev(tick, 0, byteArrayOf(0xFF.toByte(), 0x51, 0x03, (us shr 16).toByte(), (us shr 8).toByte(), us.toByte()))
        }
        fun msFor(ticks: Long): Double = ticks.toDouble() / PPQ * 60_000.0 / (currentBpm * speed)

        fun click(at: Long, accent: Boolean) {
            val note = if (accent) 76 else 77
            events += Ev(at, 2, byteArrayOf(0x99.toByte(), note.toByte(), (if (accent) 120 else 90).toByte()))
            events += Ev(at + PPQ / 8, 1, byteArrayOf(0x89.toByte(), note.toByte(), 0))
        }

        // Count-in: one bar of the first bar's metre.
        val firstEntry = seq.firstOrNull()?.let { timeline.entries[it] }
        var countInMs = 0.0
        if (options.countIn && firstEntry != null) {
            val m = track.measures[firstEntry.measure]
            tempo(firstEntry.bpm)
            val barBeats = m.timeSignature[0].coerceAtLeast(1)
            val beats = (options.countInBeats ?: barBeats).coerceIn(1, 32)
            val beatTicks = PPQ * 4L / m.timeSignature[1].coerceAtLeast(1)
            repeat(beats) { b -> click(tick + b * beatTicks, b % barBeats == 0) }
            val barTicks = beats * beatTicks
            countInMs = msFor(barTicks)
            ms += countInMs
            tick += barTicks
        }

        val strings = if (options.realStrings) StringScoreBuilder(options.pitchShift) else null
        val starts = DoubleArray(seq.size)
        val velocities = IntArray(parts.size) { 96 }
        val openNotes = HashMap<Int, Long>() // channel*128+pitch -> end tick of the sounding note (ties)
        val tieEnds = HashMap<Int, Int>() // channel*128+pitch -> index of its note-off event

        for ((si, k) in seq.withIndex()) {
            val e = timeline.entries[k]
            val m = track.measures[e.measure]
            tempo(e.bpm)
            starts[si] = ms
            val barStart = tick

            if (options.metronome) {
                val beatTicks = PPQ * 4L / m.timeSignature[1].coerceAtLeast(1)
                val barTicks = m.lengthTicks / TICK_DIV
                var b = 0L
                var n = 0
                while (b < barTicks) {
                    click(barStart + b, n == 0)
                    b += beatTicks
                    n++
                }
            }

            if (!options.muteInstrument) {
                parts.forEachIndexed { pi, part ->
                    if (part.muted) return@forEachIndexed
                    val pm = part.track.measures.getOrNull(e.measure) ?: return@forEachIndexed
                    val ch = channels[pi]
                    val isDrums = part.track.isDrums
                    val shift = if (isDrums) 0 else options.pitchShift
                    if (strings != null && !part.isVocal && strings.wants(part.track)) {
                        // Modelled strings: note times in file ms at this bar's tempo.
                        val quarterMs = 60_000.0 / (currentBpm * speed)
                        fun at(ticks: Long) = ms + ticks.toDouble() / TabParser.TICKS_PER_QUARTER * quarterMs
                        for (slot in pm.slots) {
                            for (beat in slot.beats) {
                                beat.velocity?.let { v -> DYNAMICS[v.lowercase()]?.let { velocities[pi] = it } }
                                if (beat.isRest) continue
                                val lenMs = beat.durationTicks.toDouble() / TabParser.TICKS_PER_QUARTER * quarterMs
                                strings.addBeat(pi, part.track, beat, at(beat.onsetTicks), lenMs, quarterMs, velocities[pi])
                            }
                            for (g in slot.graces) strings.addGrace(pi, part.track, g, at(slot.onsetTicks), quarterMs, velocities[pi])
                        }
                        return@forEachIndexed
                    }
                    for (slot in pm.slots) {
                        for (beat in slot.beats) {
                            beat.velocity?.let { v -> DYNAMICS[v.lowercase()]?.let { velocities[pi] = it } }
                            if (beat.isRest) continue
                            val velocity = velocities[pi]
                            val on = barStart + beat.onsetTicks / TICK_DIV
                            var len = max(1L, beat.durationTicks / TICK_DIV)
                            if (beat.palmMute || beat.notes.all { it.staccato }) len = max(1L, len / 2)
                            if (isDrums) len = max(1L, minOf(len, PPQ / 4L))
                            for (n in beat.notes) {
                                val pitch = (n.pitch + shift).coerceIn(0, 127)
                                val key = ch * 128 + pitch
                                if (n.isTie && !isDrums) {
                                    // Continue the sounding note instead of striking again.
                                    val idx = tieEnds[key]
                                    if (idx != null && openNotes[key] != null) {
                                        val newEnd = on + len
                                        events[idx] = Ev(newEnd, 1, events[idx].data)
                                        openNotes[key] = newEnd
                                        continue
                                    }
                                }
                                var vel = velocity
                                if (n.isGhost) vel = (vel * 0.55).roundToInt()
                                if (n.accent == 1) vel += 14
                                if (n.accent == 2) vel += 24
                                val noteLen = if (n.isDead) max(1L, PPQ / 16L) else len
                                events += Ev(on, 2, byteArrayOf((0x90 or ch).toByte(), pitch.toByte(), vel.coerceIn(1, 127).toByte()))
                                events += Ev(on + noteLen, 1, byteArrayOf((0x80 or ch).toByte(), pitch.toByte(), 0))
                                if (!isDrums) {
                                    tieEnds[key] = events.lastIndex
                                    openNotes[key] = on + noteLen
                                }
                            }
                        }
                        // Flams and other grace notes: just before the beat.
                        for (g in slot.graces) for (n in g.notes) {
                            val at = max(0L, barStart + slot.onsetTicks / TICK_DIV - PPQ / 16)
                            val pitch = (n.pitch + shift).coerceIn(0, 127)
                            events += Ev(at, 2, byteArrayOf((0x90 or ch).toByte(), pitch.toByte(), (velocities[pi] * 0.6).roundToInt().coerceIn(1, 127).toByte()))
                            events += Ev(at + PPQ / 16, 1, byteArrayOf((0x80 or ch).toByte(), pitch.toByte(), 0))
                        }
                    }
                }
            }

            val barTicks = m.lengthTicks / TICK_DIV
            tick = barStart + barTicks
            ms += msFor(barTicks)
        }
        // End-of-track after the last sound.
        val endTick = max(tick, events.maxOfOrNull { it.tick } ?: 0L)
        if (strings != null) {
            // With every part on the string synth the file can be empty; a silent held note
            // (channel volume 0) keeps the MIDI player running as the clock to the end.
            val ch = free.removeFirstOrNull() ?: 15
            events += Ev(0, 0, byteArrayOf((0xB0 or ch).toByte(), 7, 0))
            events += Ev(0, 2, byteArrayOf((0x90 or ch).toByte(), 60, 1))
            events += Ev(endTick, 1, byteArrayOf((0x80 or ch).toByte(), 60, 0))
        }
        events += Ev(endTick + PPQ / 4, 3, byteArrayOf(0xFF.toByte(), 0x2F, 0x00))

        return Result(write(events), starts, seq.toIntArray(), countInMs, ms, strings?.build()?.takeUnless { it.isEmpty })
    }

    private fun write(events: List<Ev>): ByteArray {
        val sorted = events.sortedWith(compareBy({ it.tick }, { it.order }))
        val track = ByteArrayOutputStream()
        var last = 0L
        for (e in sorted) {
            vlq(track, e.tick - last)
            track.write(e.data)
            last = e.tick
        }
        val body = track.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray())
        out.write(int32(6))
        out.write(byteArrayOf(0, 0, 0, 1, (PPQ shr 8).toByte(), PPQ.toByte()))
        out.write("MTrk".toByteArray())
        out.write(int32(body.size))
        out.write(body)
        return out.toByteArray()
    }

    private fun int32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun vlq(out: ByteArrayOutputStream, value: Long) {
        var v = value.coerceAtLeast(0)
        val stack = ArrayList<Int>()
        stack += (v and 0x7F).toInt()
        v = v shr 7
        while (v > 0) {
            stack += ((v and 0x7F) or 0x80).toInt()
            v = v shr 7
        }
        for (i in stack.indices.reversed()) out.write(stack[i])
    }
}
