package com.theveloper.pixelplay.data.songsterr.edit

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.theveloper.pixelplay.data.songsterr.TabParser

/**
 * Edits a Songsterr drum part **in its raw JSON** (the exact object the CDN sent), so every
 * field the app doesn't model survives untouched. The screen re-reads the edited JSON with
 * `SongsterrJson.trackFrom` + `TabParser.toRenderModel` after each change.
 *
 * Positions are (bar, tick) in the first voice; ticks use [TabParser.TICKS_PER_WHOLE]. Bars are
 * kept full: a shorter note leaves rests after it, a longer one writes over what follows.
 */
object DrumEditOps {

    const val WHOLE = TabParser.TICKS_PER_WHOLE
    private val TYPES = intArrayOf(1, 2, 4, 8, 16, 32, 64)

    // ── Reading ─────────────────────────────────────────────────────────────

    fun measures(raw: JsonObject): JsonArray =
        raw.getAsJsonArrayOrNull("measures") ?: JsonArray().also { raw.add("measures", it) }

    fun measure(raw: JsonObject, m: Int): JsonObject? = measures(raw).let { a ->
        if (m in 0 until a.size()) a[m].takeIf { it.isJsonObject }?.asJsonObject else null
    }

    /** Time signature in force at bar [m] (it carries forward from the last bar that sets one). */
    fun signatureAt(raw: JsonObject, m: Int): Pair<Int, Int> {
        var sig = 4 to 4
        val all = measures(raw)
        for (i in 0..m.coerceAtMost(all.size() - 1)) {
            val s = all[i].asJsonObject.getAsJsonArrayOrNull("signature") ?: continue
            if (s.size() >= 2) {
                val n = s[0].asIntOrNull() ?: continue
                val d = s[1].asIntOrNull() ?: continue
                if (n > 0 && d > 0) sig = n to d
            }
        }
        return sig
    }

    fun barTicks(raw: JsonObject, m: Int): Long = signatureAt(raw, m).let { (n, d) -> n * WHOLE / d }

    fun beats(measure: JsonObject): JsonArray {
        val voices = measure.getAsJsonArrayOrNull("voices") ?: JsonArray().also { measure.add("voices", it) }
        if (voices.size() == 0) voices.add(JsonObject().apply { add("beats", JsonArray()) })
        val v = voices[0].asJsonObject
        return v.getAsJsonArrayOrNull("beats") ?: JsonArray().also { v.add("beats", it) }
    }

    fun isGrace(b: JsonObject): Boolean {
        val g = b.get("graceNote") ?: return false
        if (g.isJsonNull) return false
        if (g.isJsonPrimitive && g.asJsonPrimitive.isBoolean) return g.asBoolean
        return true
    }

    fun isRest(b: JsonObject): Boolean {
        if (b.bool("rest")) return true
        val notes = b.getAsJsonArrayOrNull("notes") ?: return true
        return notes.size() == 0 || notes.all { it.isJsonObject && it.asJsonObject.bool("rest") }
    }

    /** Same maths as `TabParser.beatTicks`, on the raw beat. */
    fun beatTicks(b: JsonObject): Long {
        b.getAsJsonArrayOrNull("duration")?.let { d ->
            val n = d.getOrNull(0)?.asIntOrNull()
            val den = d.getOrNull(1)?.asIntOrNull()
            if (n != null && den != null && den != 0) return (n.toDouble() * WHOLE / den).toLong().coerceAtLeast(1)
        }
        val type = (b.get("type")?.asIntOrNull() ?: 4).coerceAtLeast(1)
        var ticks = WHOLE.toDouble() / type
        var add = ticks
        val dots = b.get("dots")?.asIntOrNull() ?: if (b.bool("dotted")) 1 else 0
        repeat(dots.coerceIn(0, 3)) { add /= 2; ticks += add }
        b.get("tuplet")?.asIntOrNull()?.takeIf { it > 1 }?.let { n -> ticks *= Integer.highestOneBit(n).toDouble() / n }
        return ticks.toLong().coerceAtLeast(1)
    }

    /** A beat in the first voice with where it starts. Grace notes take no time. */
    data class Placed(val index: Int, val start: Long, val ticks: Long, val beat: JsonObject)

    fun placed(measure: JsonObject): List<Placed> {
        val out = ArrayList<Placed>()
        var t = 0L
        beats(measure).forEachIndexed { i, e ->
            val b = e.asJsonObject
            if (isGrace(b)) return@forEachIndexed
            val len = beatTicks(b)
            out += Placed(i, t, len, b)
            t += len
        }
        return out
    }

    /** How much of bar [m] is written, against how long it should be. */
    fun fill(raw: JsonObject, m: Int): Pair<Long, Long> {
        val meas = measure(raw, m) ?: return 0L to barTicks(raw, m)
        val written = placed(meas).sumOf { it.ticks }
        return written to barTicks(raw, m)
    }

    /** The beat starting exactly at [tick], if any. */
    fun beatAt(raw: JsonObject, m: Int, tick: Long): Placed? =
        measure(raw, m)?.let { placed(it) }?.firstOrNull { it.start == tick }

    /** The beat covering [tick] (starts at or before it). */
    fun beatCovering(raw: JsonObject, m: Int, tick: Long): Placed? =
        measure(raw, m)?.let { placed(it) }?.lastOrNull { it.start <= tick }

    /** Notes (articulation ids) on the beat starting at [tick]. */
    fun piecesAt(raw: JsonObject, m: Int, tick: Long): Set<Int> {
        val b = beatAt(raw, m, tick)?.beat ?: return emptySet()
        if (isRest(b)) return emptySet()
        return b.getAsJsonArrayOrNull("notes")?.mapNotNull { e ->
            e.takeIf { it.isJsonObject }?.asJsonObject?.takeIf { !it.bool("rest") }?.get("fret")?.asIntOrNull()
        }?.toSet().orEmpty()
    }

    /** The staff position this tab already uses for each kit piece (so new notes sit where the tab puts them). */
    fun staffPositions(raw: JsonObject): Map<Int, Double> {
        val out = HashMap<Int, Double>()
        for (me in measures(raw)) {
            val voices = me.asJsonObject.getAsJsonArrayOrNull("voices") ?: continue
            for (v in voices) for (be in v.asJsonObject.getAsJsonArrayOrNull("beats") ?: continue) {
                for (ne in be.asJsonObject.getAsJsonArrayOrNull("notes") ?: continue) {
                    val n = ne.asJsonObject
                    val f = n.get("fret")?.asIntOrNull() ?: continue
                    val s = n.get("string")?.asDoubleOrNull() ?: continue
                    out.putIfAbsent(f, s)
                }
            }
        }
        return out
    }

    // ── Building beats ──────────────────────────────────────────────────────

    /** Writes the length fields (type / dots / duration) for [ticks] on [b]. */
    fun setLength(b: JsonObject, ticks: Long) {
        b.remove("dotted"); b.remove("tuplet"); b.remove("tupletStart"); b.remove("tupletStop")
        var type = 0
        var dots = 0
        for (t in TYPES) {
            val base = WHOLE / t
            when (ticks) {
                base -> { type = t; dots = 0 }
                base * 3 / 2 -> { type = t; dots = 1 }
                base * 7 / 4 -> { type = t; dots = 2 }
                else -> continue
            }
            break
        }
        if (type == 0) type = TYPES.lastOrNull { WHOLE / it <= ticks } ?: 64
        b.addProperty("type", type)
        if (dots > 0) b.addProperty("dots", dots) else b.remove("dots")
        val g = gcd(ticks, WHOLE)
        b.add("duration", JsonArray().apply { add(ticks / g); add(WHOLE / g) })
    }

    fun restBeat(ticks: Long): JsonObject = JsonObject().apply {
        setLength(this, ticks)
        addProperty("rest", true)
        add("notes", JsonArray().apply { add(JsonObject().apply { addProperty("rest", true) }) })
    }

    /** Rests that add up to [ticks], biggest first. */
    fun rests(ticks: Long): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        var left = ticks
        while (left > 0) {
            val t = TYPES.firstOrNull { WHOLE / it <= left }
            val len = if (t != null) WHOLE / t else left
            out += restBeat(len)
            left -= len
        }
        return out
    }

    internal fun makeRest(b: JsonObject) {
        val len = beatTicks(b)
        val keep = listOf("type", "duration", "dots", "tuplet", "tupletStart", "tupletStop")
        b.keySet().toList().filter { it !in keep }.forEach { b.remove(it) }
        setLength(b, len)
        b.addProperty("rest", true)
        b.add("notes", JsonArray().apply { add(JsonObject().apply { addProperty("rest", true) }) })
    }

    // ── Editing ─────────────────────────────────────────────────────────────

    /** Pads a short first voice with rests and trims one that runs past the bar. */
    fun refit(raw: JsonObject, m: Int) {
        val meas = measure(raw, m) ?: return
        meas.remove("rest")
        val bar = barTicks(raw, m)
        val arr = beats(meas)
        val list = placed(meas)
        val written = list.sumOf { it.ticks }
        if (written < bar) {
            rests(bar - written).forEach { arr.add(it) }
        } else if (written > bar) {
            // Drop whatever starts past the end; shorten the beat that crosses it.
            val cross = list.firstOrNull { it.start < bar && it.start + it.ticks > bar }
            val drop = list.filter { it.start >= bar }.map { it.index }.sortedDescending()
            drop.forEach { arr.remove(it) }
            cross?.let { setLength(it.beat, bar - it.start) }
        }
    }

    /** Makes sure a beat starts exactly at [tick] (splitting the one that covers it); returns its index. */
    fun slotAt(raw: JsonObject, m: Int, tick: Long): Int? {
        val meas = measure(raw, m) ?: return null
        refit(raw, m)
        val bar = barTicks(raw, m)
        if (tick !in 0 until bar) return null
        val list = placed(meas)
        list.firstOrNull { it.start == tick }?.let { return it.index }
        val cover = list.lastOrNull { it.start < tick } ?: return null
        val arr = beats(meas)
        val leftLen = tick - cover.start
        val rightLen = cover.ticks - leftLen
        setLength(cover.beat, leftLen)
        val right = rests(rightLen)
        right.asReversed().forEach { arr.insert(cover.index + 1, it) }
        return cover.index + 1
    }

    /** Changes the beat at [index] to [newTicks] (clipped to the bar), resting or overwriting what follows. */
    fun setBeatLength(raw: JsonObject, m: Int, index: Int, newTicks: Long) {
        val meas = measure(raw, m) ?: return
        val arr = beats(meas)
        val list = placed(meas)
        val me = list.firstOrNull { it.index == index } ?: return
        val bar = barTicks(raw, m)
        val len = newTicks.coerceAtMost(bar - me.start).coerceAtLeast(1)
        if (len == me.ticks) return
        if (len < me.ticks) {
            setLength(me.beat, len)
            rests(me.ticks - len).asReversed().forEach { arr.insert(index + 1, it) }
            return
        }
        val end = me.start + len
        // Remove every beat (and grace) after this one that starts before the new end.
        val after = list.filter { it.index > index && it.start < end }
        val straddle = after.lastOrNull()?.takeIf { it.start + it.ticks > end }
        val lastRemoved = after.lastOrNull()?.index ?: index
        for (i in lastRemoved downTo index + 1) arr.remove(i)
        setLength(me.beat, len)
        straddle?.let { s -> rests(s.start + s.ticks - end).asReversed().forEach { arr.insert(index + 1, it) } }
    }

    /**
     * Adds kit piece [art] at (bar, tick), or removes it if it's already there. A rest there
     * becomes a note of [ticks]. Returns true when a note was added.
     */
    fun toggleNote(raw: JsonObject, m: Int, tick: Long, ticks: Long, art: Int, staffPos: Double): Boolean {
        val idx = slotAt(raw, m, tick) ?: return false
        val meas = measure(raw, m) ?: return false
        val b = beats(meas)[idx].asJsonObject
        if (isRest(b)) {
            b.remove("rest")
            b.add("notes", JsonArray().apply { add(note(art, staffPos)) })
            setBeatLength(raw, m, idx, ticks)
            return true
        }
        val notes = b.getAsJsonArrayOrNull("notes") ?: JsonArray().also { b.add("notes", it) }
        val existing = (0 until notes.size()).firstOrNull { notes[it].asJsonObject.get("fret")?.asIntOrNull() == art }
        if (existing != null) {
            notes.remove(existing)
            if (notes.size() == 0 || isRest(b)) makeRest(b)
            return false
        }
        notes.add(note(art, staffPos))
        return true
    }

    private fun note(art: Int, staffPos: Double) = JsonObject().apply {
        addProperty("fret", art)
        add("string", if (staffPos % 1.0 == 0.0) JsonPrimitive(staffPos.toInt()) else JsonPrimitive(staffPos))
    }

    /** Turns the beat at (bar, tick) into a rest of the same length. */
    fun clearAt(raw: JsonObject, m: Int, tick: Long): Boolean {
        val p = beatAt(raw, m, tick) ?: return false
        if (isRest(p.beat)) return false
        makeRest(p.beat)
        return true
    }

    /** Sets the length of the beat at (bar, tick). */
    fun setLengthAt(raw: JsonObject, m: Int, tick: Long, ticks: Long): Boolean {
        val idx = slotAt(raw, m, tick) ?: return false
        setBeatLength(raw, m, idx, ticks)
        return true
    }

    enum class NoteFlag { ACCENT, HEAVY_ACCENT, GHOST, STACCATO }

    /** Toggles a mark on every note of the beat at (bar, tick). */
    fun toggleFlag(raw: JsonObject, m: Int, tick: Long, flag: NoteFlag): Boolean {
        val b = beatAt(raw, m, tick)?.beat ?: return false
        if (isRest(b)) return false
        val notes = b.getAsJsonArrayOrNull("notes") ?: return false
        val objs = notes.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
        when (flag) {
            NoteFlag.ACCENT, NoteFlag.HEAVY_ACCENT -> {
                val level = if (flag == NoteFlag.ACCENT) 1 else 2
                val on = objs.all { (it.get("accentuated")?.asIntOrNull() ?: 0) == level }
                objs.forEach { if (on) it.remove("accentuated") else it.addProperty("accentuated", level) }
            }
            NoteFlag.GHOST -> {
                val on = objs.all { it.bool("ghost") }
                objs.forEach { if (on) it.remove("ghost") else it.addProperty("ghost", true) }
            }
            NoteFlag.STACCATO -> {
                val on = objs.all { it.bool("staccato") }
                objs.forEach { if (on) it.remove("staccato") else it.addProperty("staccato", true) }
            }
        }
        return true
    }

    /** Dynamic ("mf", "ff" …) on the beat at (bar, tick); the same one again removes it. */
    fun toggleVelocity(raw: JsonObject, m: Int, tick: Long, velocity: String): Boolean {
        val idx = slotAt(raw, m, tick) ?: return false
        val b = beats(measure(raw, m)!!)[idx].asJsonObject
        if (b.get("velocity")?.takeIf { it.isJsonPrimitive }?.asString == velocity) b.remove("velocity")
        else b.addProperty("velocity", velocity)
        return true
    }

    /** Text above the beat at (bar, tick); blank removes it. */
    fun setBeatText(raw: JsonObject, m: Int, tick: Long, text: String?): Boolean {
        val idx = slotAt(raw, m, tick) ?: return false
        val b = beats(measure(raw, m)!!)[idx].asJsonObject
        if (text.isNullOrBlank()) b.remove("text") else b.add("text", JsonObject().apply { addProperty("text", text.trim()) })
        return true
    }

    // ── Ranges (select · copy · cut · paste · delete) ───────────────────────

    /** A beat position: bar + ticks into it. */
    data class Pos(val measure: Int, val tick: Long)

    /** Beats of the first voice from [from] to [toLast] (both beat starts, inclusive), bar by bar. */
    fun beatsBetween(raw: JsonObject, from: Pos, toLast: Pos): List<Pair<Int, Placed>> {
        val out = ArrayList<Pair<Int, Placed>>()
        for (m in from.measure..toLast.measure) {
            val meas = measure(raw, m) ?: continue
            for (p in placed(meas)) {
                if (m == from.measure && p.start < from.tick) continue
                if (m == toLast.measure && p.start > toLast.tick) continue
                out += m to p
            }
        }
        return out
    }

    /** Turns every beat in the range into a rest; returns how many had notes. */
    fun clearRange(raw: JsonObject, from: Pos, toLast: Pos): Int {
        var n = 0
        beatsBetween(raw, from, toLast).forEach { (_, p) ->
            if (!isRest(p.beat)) { makeRest(p.beat); n++ }
        }
        return n
    }

    /** Copies of the beats in the range, in order (their lengths travel with them). */
    fun copyRange(raw: JsonObject, from: Pos, toLast: Pos): List<JsonObject> =
        beatsBetween(raw, from, toLast).map { it.second.beat.deepCopy() }

    /**
     * Writes [beats] one after another from [at], over whatever is there, running on into the
     * next bars (and adding bars at the end when needed). A beat longer than what's left of a
     * bar is shortened to fit. Returns the position right after the last pasted beat.
     */
    fun pasteAt(raw: JsonObject, at: Pos, beats: List<JsonObject>): Pos {
        var m = at.measure
        var t = at.tick
        for (src in beats) {
            if (isGrace(src)) continue
            if (m >= measures(raw).size()) insertBar(raw, measures(raw).size())
            var bar = barTicks(raw, m)
            if (t >= bar) {
                m++; t = 0
                if (m >= measures(raw).size()) insertBar(raw, measures(raw).size())
                bar = barTicks(raw, m)
            }
            val fit = beatTicks(src).coerceAtMost(bar - t)
            val idx = slotAt(raw, m, t) ?: break
            // Make room first (while the slot still has its old length), then copy the beat in.
            setBeatLength(raw, m, idx, fit)
            val target = beats(measure(raw, m)!!)[idx].asJsonObject
            target.keySet().toList().forEach { target.remove(it) }
            for ((k, v) in src.deepCopy().entrySet()) target.add(k, v)
            setLength(target, fit)
            t += fit
        }
        if (t >= barTicks(raw, m)) {
            if (m + 1 < measures(raw).size()) return Pos(m + 1, 0)
            // Pasted to the very end: stay on the last beat.
            return Pos(m, measure(raw, m)?.let { placed(it).lastOrNull()?.start } ?: 0L)
        }
        return Pos(m, t)
    }

    // ── Bars ────────────────────────────────────────────────────────────────

    /** Inserts an empty bar at [at] (0 … size). */
    fun insertBar(raw: JsonObject, at: Int) {
        val arr = measures(raw)
        val pos = at.coerceIn(0, arr.size())
        val ticks = barTicks(raw, (pos - 1).coerceAtLeast(0).coerceAtMost(arr.size() - 1).coerceAtLeast(0))
        val bar = JsonObject().apply {
            add("voices", JsonArray().apply {
                add(JsonObject().apply { add("beats", JsonArray().apply { rests(ticks).forEach { add(it) } }) })
            })
        }
        arr.insert(pos, bar)
        shiftAutomations(raw, from = pos, by = 1)
    }

    /** Copies bar [m] right after itself (without its section marker). */
    fun duplicateBar(raw: JsonObject, m: Int) {
        val arr = measures(raw)
        val src = measure(raw, m) ?: return
        val copy = src.deepCopy().apply { remove("marker"); remove("signature"); remove("repeatStart") }
        arr.insert(m + 1, copy)
        shiftAutomations(raw, from = m + 1, by = 1)
    }

    /** Deletes bar [m] (never the last one left). A time signature it set moves to the next bar. */
    fun deleteBar(raw: JsonObject, m: Int): Boolean {
        val arr = measures(raw)
        if (arr.size() <= 1 || m !in 0 until arr.size()) return false
        val gone = arr[m].asJsonObject
        val next = measure(raw, m + 1)
        if (next != null) {
            gone.get("signature")?.let { if (!next.has("signature")) next.add("signature", it.deepCopy()) }
            gone.get("marker")?.let { if (!next.has("marker")) next.add("marker", it.deepCopy()) }
        }
        arr.remove(m)
        shiftAutomations(raw, from = m + 1, by = -1)
        return true
    }

    /** Sets bar [m]'s time signature and refits every bar it covers. */
    fun setSignature(raw: JsonObject, m: Int, num: Int, den: Int) {
        val meas = measure(raw, m) ?: return
        meas.add("signature", JsonArray().apply { add(num); add(den) })
        val arr = measures(raw)
        var i = m
        while (i < arr.size()) {
            if (i > m && arr[i].asJsonObject.has("signature")) break
            refit(raw, i)
            i++
        }
    }

    fun toggleRepeatStart(raw: JsonObject, m: Int) {
        val meas = measure(raw, m) ?: return
        if (meas.bool("repeatStart")) meas.remove("repeatStart") else meas.addProperty("repeatStart", true)
    }

    /** Repeat-end sign on bar [m], played [times] in total; null removes it. */
    fun setRepeatEnd(raw: JsonObject, m: Int, times: Int?) {
        val meas = measure(raw, m) ?: return
        meas.remove("repeatCount")
        if (times == null || times < 2) meas.remove("repeat") else meas.addProperty("repeat", times)
    }

    fun setMarker(raw: JsonObject, m: Int, text: String?) {
        val meas = measure(raw, m) ?: return
        if (text.isNullOrBlank()) meas.remove("marker")
        else meas.add("marker", JsonObject().apply { addProperty("text", text.trim()); addProperty("width", 0) })
    }

    fun toggleDoubleBar(raw: JsonObject, m: Int) {
        val meas = measure(raw, m) ?: return
        if (meas.bool("doubleBarline")) meas.remove("doubleBarline") else meas.addProperty("doubleBarline", true)
    }

    private fun shiftAutomations(raw: JsonObject, from: Int, by: Int) {
        val auto = raw.get("automations")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        for (key in auto.keySet()) {
            val list = auto.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
            for (e in list) {
                val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val mm = o.get("measure")?.asIntOrNull() ?: continue
                if (mm >= from) o.addProperty("measure", (mm + by).coerceAtLeast(0))
            }
        }
    }

    // ── JSON helpers ────────────────────────────────────────────────────────

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    private fun JsonObject.getAsJsonArrayOrNull(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { p ->
            val prim = p.asJsonPrimitive
            when {
                prim.isBoolean -> prim.asBoolean
                prim.isNumber -> prim.asInt != 0
                prim.isString -> prim.asString == "true"
                else -> false
            }
        } ?: false

    private fun JsonElement.asIntOrNull(): Int? =
        takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asNumber?.toDouble()?.toInt()

    private fun JsonElement.asDoubleOrNull(): Double? =
        takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

    private fun JsonArray.getOrNull(i: Int): JsonElement? = if (i in 0 until size()) get(i) else null

    /** Gson's JsonArray has no insert; rebuild the tail. */
    private fun JsonArray.insert(index: Int, e: JsonElement) {
        if (index >= size()) { add(e); return }
        val tail = (index until size()).map { get(it) }
        for (i in size() - 1 downTo index) remove(i)
        add(e)
        tail.forEach { add(it) }
    }
}
