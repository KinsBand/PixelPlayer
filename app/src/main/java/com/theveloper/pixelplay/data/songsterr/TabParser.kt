package com.theveloper.pixelplay.data.songsterr

import com.theveloper.pixelplay.data.songsterr.model.RevisionBeat
import com.theveloper.pixelplay.data.songsterr.model.RevisionMeasure
import com.theveloper.pixelplay.data.songsterr.model.RevisionNote
import com.theveloper.pixelplay.data.songsterr.model.RevisionTrack
import java.util.IdentityHashMap
import kotlin.math.roundToLong

/**
 * Turns a Songsterr part ([RevisionTrack]) into a render-ready model.
 *
 * - Every voice is read and placed on an exact tick grid (triplets, dots, grace notes line up).
 * - The time signature carries forward; a pickup bar ("anacrusis") keeps its real length and
 *   bar numbers start after it, like Songsterr.
 * - Drums keep Songsterr's staff position (`string`) and articulation id, so the 5-line staff
 *   shows every instrument where Songsterr puts it, with the right notehead.
 * - Guitar: ties, hammer-on vs pull-off, slide direction and bend amounts are resolved here.
 *   [transpose] shifts every fret by that many semitones.
 */
object TabParser {

    /** Ticks per whole note. 3840 × 7 divides evenly by 2, 3, 5 and 7 (septuplets). */
    const val TICKS_PER_WHOLE = 26_880L
    const val TICKS_PER_QUARTER = TICKS_PER_WHOLE / 4

    // ─── Instrument classification ───────────────────────────────────────────

    enum class InstrumentFamily { GUITAR, BASS, DRUMS, OTHER }

    /** Songsterr instrument ids are 0-based General MIDI programs; 1024 is the drum kit. */
    fun familyOf(instrumentId: Int?): InstrumentFamily = when (instrumentId) {
        1024 -> InstrumentFamily.DRUMS
        in 24..31 -> InstrumentFamily.GUITAR
        in 32..39 -> InstrumentFamily.BASS
        else -> InstrumentFamily.OTHER
    }

    // ─── Drums ───────────────────────────────────────────────────────────────

    enum class DrumGlyph { HEAD, X, X_CIRCLE, SLASH_CIRCLE, DIAMOND, DIAMOND_OPEN, X_HAT, X_CHOKE, TRIANGLE }

    /**
     * A drum kit piece as Songsterr names it. Ids above 87 are Guitar Pro 7 articulations
     * (rim shot, half hi-hat, chokes); [gm] is the General MIDI sound used for playback.
     * [staffPos] is only a fallback: Songsterr sends the real position with every note.
     */
    data class DrumArticulation(
        val id: Int,
        val name: String,
        val gm: Int,
        val glyph: DrumGlyph,
        /** Staff position in line spaces: 0 = top line, 4 = bottom line, 0.5 steps. */
        val staffPos: Float,
    )

    private val ARTICULATIONS: Map<Int, DrumArticulation> = listOf(
        DrumArticulation(35, "Bass Drum 2", 35, DrumGlyph.HEAD, 4f),
        DrumArticulation(36, "Bass Drum 1", 36, DrumGlyph.HEAD, 3.5f),
        DrumArticulation(37, "Side Stick", 37, DrumGlyph.X, 1.5f),
        DrumArticulation(38, "Snare", 38, DrumGlyph.HEAD, 1.5f),
        DrumArticulation(39, "Hand Clap", 39, DrumGlyph.X, 1.5f),
        DrumArticulation(40, "Electric Snare", 40, DrumGlyph.HEAD, 1.5f),
        DrumArticulation(91, "Rim Shot Snare", 40, DrumGlyph.DIAMOND, 1.5f),
        DrumArticulation(41, "Very Low Tom", 41, DrumGlyph.HEAD, 3f),
        DrumArticulation(43, "Floor Tom", 43, DrumGlyph.HEAD, 3f),
        DrumArticulation(45, "Low Tom", 45, DrumGlyph.HEAD, 2.5f),
        DrumArticulation(47, "Mid Tom", 47, DrumGlyph.HEAD, 2f),
        DrumArticulation(48, "High Tom", 48, DrumGlyph.HEAD, 1f),
        DrumArticulation(50, "High Floor Tom", 50, DrumGlyph.HEAD, 0.5f),
        DrumArticulation(42, "Closed Hi Hat", 42, DrumGlyph.X, -0.5f),
        DrumArticulation(92, "Half Hi Hat", 46, DrumGlyph.SLASH_CIRCLE, -0.5f),
        DrumArticulation(46, "Open Hi Hat", 46, DrumGlyph.X_CIRCLE, -0.5f),
        DrumArticulation(44, "Foot Hi Hat", 44, DrumGlyph.X, 4.5f),
        DrumArticulation(49, "High Crash", 49, DrumGlyph.X, -1f),
        DrumArticulation(57, "Medium Crash", 57, DrumGlyph.X, -0.5f),
        DrumArticulation(97, "High Crash (Choke)", 49, DrumGlyph.X_CHOKE, -1f),
        DrumArticulation(98, "Medium Crash (Choke)", 57, DrumGlyph.X_CHOKE, -0.5f),
        DrumArticulation(52, "China", 52, DrumGlyph.X_HAT, -1.5f),
        DrumArticulation(96, "China (Choke)", 52, DrumGlyph.X_CHOKE, -1.5f),
        DrumArticulation(55, "Splash", 55, DrumGlyph.X, -1f),
        DrumArticulation(95, "Splash (Choke)", 55, DrumGlyph.X_CHOKE, -1f),
        DrumArticulation(51, "Ride", 51, DrumGlyph.X, 0f),
        DrumArticulation(59, "Ride Edge", 59, DrumGlyph.X, 0f),
        DrumArticulation(53, "Ride Bell", 53, DrumGlyph.DIAMOND_OPEN, 0f),
        DrumArticulation(93, "Ride (Choke)", 51, DrumGlyph.X_CHOKE, 0f),
        DrumArticulation(54, "Tambourine", 54, DrumGlyph.X, 0.5f),
        DrumArticulation(56, "Cowbell", 56, DrumGlyph.TRIANGLE, 0.5f),
    ).associateBy { it.id }

    /** Every named kit piece, in kit order (bass drums, snares, toms, hi-hats, cymbals, percussion). */
    val drumArticulations: List<DrumArticulation> get() = ARTICULATIONS.values.toList()

    fun drumArticulation(id: Int): DrumArticulation = ARTICULATIONS[id]
        ?: DrumArticulation(id, "Percussion $id", if (id in 27..87) id else 38, DrumGlyph.HEAD, 1.5f)

    // ─── Conversion ──────────────────────────────────────────────────────────

    private class Ctx(
        val isDrums: Boolean,
        val numStrings: Int,
        val tuning: List<Int>,
        val capo: Int,
        val transpose: Int,
        val resolved: Resolved,
    )

    /** Pure CPU work; call off the main thread. */
    fun toRenderModel(track: RevisionTrack, family: InstrumentFamily, transpose: Int = 0): RenderedTrack {
        val isDrums = family == InstrumentFamily.DRUMS
        val numStrings = if (isDrums) 5 else (track.strings ?: track.tuning.size).takeIf { it > 0 } ?: 6

        val tempo = track.automations?.tempo.orEmpty()
            .sortedWith(compareBy({ it.measure }, { it.position }))
            .map { TempoMark(it.measure, it.bpm * 4.0 / it.type.coerceAtLeast(1)) }
            .distinctBy { it.measure }
        val tempoByMeasure = tempo.associate { it.measure to it.bpmQuarter }
        val initialBpm = tempo.firstOrNull()?.bpmQuarter ?: 120.0

        val ctx = Ctx(
            isDrums = isDrums,
            numStrings = numStrings,
            tuning = track.tuning,
            capo = track.capo ?: 0,
            transpose = if (isDrums) 0 else transpose,
            resolved = if (isDrums) Resolved.EMPTY else resolveLinks(track.measures),
        )

        var signature = listOf(4, 4)
        var bpm = initialBpm
        val measures = track.measures.mapIndexed { mIdx, m ->
            val changed = m.signature != null && m.signature != signature
            if (m.signature != null) signature = m.signature
            tempoByMeasure[mIdx]?.let { bpm = it }
            toRenderMeasure(
                m = m,
                index = mIdx,
                number = if (track.anacrusis) mIdx.takeIf { it > 0 } else mIdx + 1,
                isPickup = track.anacrusis && mIdx == 0,
                signature = signature,
                showSignature = mIdx == 0 || changed,
                tempoBpm = tempoByMeasure[mIdx]?.takeIf { mIdx > 0 },
                bpm = bpm,
                ctx = ctx,
            )
        }

        return RenderedTrack(
            name = track.name?.takeIf { it.isNotBlank() } ?: track.instrument ?: "Track",
            instrument = track.instrument.orEmpty(),
            instrumentId = track.instrumentId ?: if (isDrums) 1024 else 29,
            family = family,
            numStrings = numStrings,
            tuning = track.tuning,
            capo = track.capo?.takeIf { it > 0 },
            transpose = ctx.transpose,
            bpm = initialBpm.roundToLong().toInt(),
            tempo = tempo,
            hasPickup = track.anacrusis,
            measures = measures,
        )
    }

    fun beatTicks(b: RevisionBeat): Long {
        b.duration?.let { d -> return (d[0].toDouble() * TICKS_PER_WHOLE / d[1]).roundToLong().coerceAtLeast(1) }
        val type = (b.type ?: 4).coerceAtLeast(1)
        var ticks = TICKS_PER_WHOLE.toDouble() / type
        var add = ticks
        repeat(b.dots.coerceIn(0, 3)) { add /= 2; ticks += add }
        b.tuplet?.let { n -> ticks *= Integer.highestOneBit(n).toDouble() / n }
        return ticks.roundToLong().coerceAtLeast(1)
    }

    private fun toRenderMeasure(
        m: RevisionMeasure,
        index: Int,
        number: Int?,
        isPickup: Boolean,
        signature: List<Int>,
        showSignature: Boolean,
        tempoBpm: Double?,
        bpm: Double,
        ctx: Ctx,
    ): RenderedMeasure {
        val nominal = signature[0] * TICKS_PER_WHOLE / signature[1].coerceAtLeast(1)
        val slotBeats = sortedMapOf<Long, MutableList<RenderedBeat>>()
        val slotGraces = sortedMapOf<Long, MutableList<RenderedBeat>>()
        var longestVoice = 0L

        m.voices.forEachIndexed { vIdx, voice ->
            if (vIdx > 0 && voice.beats.all { it.rest || it.notes.all { n -> n.rest } }) return@forEachIndexed
            var t = 0L
            val pendingGraces = mutableListOf<RenderedBeat>()
            voice.beats.forEachIndexed { bIdx, b ->
                val ticks = beatTicks(b)
                val isGrace = b.graceNote != null
                val rb = toRenderBeat(b, bIdx, vIdx, if (isGrace) t else t, ticks, isGrace, ctx)
                if (isGrace) {
                    pendingGraces += rb
                } else {
                    if (!(vIdx > 0 && rb.isRest)) slotBeats.getOrPut(t) { mutableListOf() } += rb
                    if (pendingGraces.isNotEmpty()) {
                        slotGraces.getOrPut(t) { mutableListOf() } += pendingGraces
                        pendingGraces.clear()
                    }
                    t += ticks
                }
            }
            longestVoice = maxOf(longestVoice, t)
        }

        // A pickup bar is as long as what's written in it; any other bar is at least full length.
        val length = (if (isPickup && longestVoice in 1 until nominal) longestVoice else maxOf(nominal, longestVoice))
            .coerceAtLeast(1)
        val onsets = (slotBeats.keys + slotGraces.keys).toSortedSet().toList()
        val slots = onsets.mapIndexed { i, onset ->
            val next = onsets.getOrNull(i + 1) ?: length
            BeatSlot(
                onsetTicks = onset,
                spanTicks = (next - onset).coerceAtLeast(1),
                beats = slotBeats[onset].orEmpty(),
                graces = slotGraces[onset].orEmpty(),
            )
        }
        val allBeats = slots.flatMap { it.beats }
        val isEmpty = allBeats.all { it.isRest } && slots.all { it.graces.isEmpty() }

        return RenderedMeasure(
            index = index,
            number = number,
            isPickup = isPickup,
            timeSignature = signature,
            showSignature = showSignature,
            marker = m.marker?.text?.takeIf { it.isNotBlank() },
            repeatStart = m.repeatStart,
            repeatCount = m.repeat,
            alternateEnding = m.alternateEnding,
            doubleBar = m.doubleBarline,
            tempoBpm = tempoBpm,
            bpm = bpm,
            lengthTicks = length,
            slots = slots,
            beats = allBeats,
            isEmpty = isEmpty,
            contentKey = contentKey(slots, isEmpty, signature, length),
        )
    }

    private fun toRenderBeat(
        b: RevisionBeat,
        index: Int,
        voice: Int,
        onset: Long,
        ticks: Long,
        isGrace: Boolean,
        ctx: Ctx,
    ): RenderedBeat {
        val notes = b.notes
            .filter { !it.rest && (it.fret != null || it.dead || it.tie) }
            .map { n -> toRenderNote(n, ctx) }
            .distinctBy { if (ctx.isDrums) it.fret.toLong() else it.row.toLong() }
        return RenderedBeat(
            index = index,
            voice = voice,
            onsetTicks = onset,
            durationTicks = ticks,
            durationFraction = ticks.toDouble() / TICKS_PER_WHOLE,
            type = b.type ?: 4,
            dots = b.dots,
            tuplet = b.tuplet,
            tupletStart = b.tupletStart,
            tupletStop = b.tupletStop,
            beamStart = b.beamStart,
            beamStop = b.beamStop,
            isRest = b.rest || notes.isEmpty(),
            isGrace = isGrace,
            graceOnBeat = b.graceNote == "onBeat",
            palmMute = b.palmMute,
            letRing = b.letRing,
            vibrato = b.vibrato,
            wideVibrato = b.wideVibrato,
            pickStroke = b.pickStroke,
            velocity = b.velocity,
            chordText = b.chordText,
            text = b.text,
            tapping = b.tapping,
            upStroke = b.upStroke,
            downStroke = b.downStroke,
            tremoloPicking = b.tremoloPicking,
            tremoloBar = b.tremoloBar,
            notes = notes,
        )
    }

    private fun toRenderNote(n: RevisionNote, ctx: Ctx): RenderedNote {
        if (ctx.isDrums) {
            val id = n.fret ?: 38
            val art = drumArticulation(id)
            return RenderedNote(
                row = 0,
                fret = id,
                label = "",
                staffPos = n.string?.toFloat() ?: art.staffPos,
                pitch = art.gm,
                isDead = n.dead,
                isGhost = n.ghost,
                isTie = n.tie,
                accent = n.accentuated,
                staccato = n.staccato,
                drumGlyph = art.glyph,
            )
        }
        val string = (n.string?.toInt() ?: 0).coerceIn(0, (ctx.numStrings - 1).coerceAtLeast(0))
        val written = n.fret ?: ctx.resolved.tieFret[n] ?: 0
        val nextWritten = ctx.resolved.nextFret[n]
        val fret = shift(written, ctx.transpose, n.harmonic == "natural")
        val next = nextWritten?.let { shift(it, ctx.transpose, false) }
        val hpLabel = if (n.hp) { if (next != null && next < fret) "p" else "h" } else null

        var slideBefore: String? = null
        var slideAfter: String? = null
        when (n.slide) {
            "below" -> slideBefore = "/"
            "above" -> slideBefore = "\\"
            "upwards" -> slideAfter = "/"
            "downwards" -> slideAfter = "\\"
            "legato", "shift", "belowlegato", "belowshift", "abovelegato", "aboveshift" ->
                slideAfter = if (next != null && next < fret) "\\" else "/"
            null -> Unit
            else -> slideAfter = "/"
        }

        val label = when {
            n.dead -> "x"
            n.harmonic == "natural" -> "<$fret>"
            n.harmonic != null && n.harmonicFret != null -> "$fret<${formatFret(n.harmonicFret)}>"
            n.tie || n.ghost -> "($fret)"
            else -> fret.toString()
        }

        val bendMax = n.bend?.let { b -> maxOf(b.tone, b.points.maxOfOrNull { it.tone } ?: 0) } ?: 0
        val bendEnd = n.bend?.points?.lastOrNull()?.tone ?: bendMax
        val bendStart = n.bend?.points?.firstOrNull()?.tone ?: 0
        val openString = ctx.tuning.getOrNull(string) ?: STANDARD_TUNING.getOrElse(string) { 40 }

        return RenderedNote(
            row = string,
            fret = fret,
            label = label,
            pitch = (openString + ctx.capo + fret).coerceIn(0, 127),
            isDead = n.dead,
            isGhost = n.ghost,
            isTie = n.tie,
            accent = n.accentuated,
            hpLabel = hpLabel,
            slide = n.slide,
            slideBefore = slideBefore,
            slideAfter = slideAfter,
            harmonic = n.harmonic,
            harmonicLabel = when (n.harmonic) {
                null, "natural" -> null
                "pinch" -> "P.H."
                "artificial" -> "A.H."
                "tapped" -> "T.H."
                "semi" -> "S.H."
                else -> "Harm."
            },
            bendTone = bendMax,
            bendLabel = if (n.bend != null && bendMax > 0) bendLabel(bendMax) else null,
            bendRelease = n.bend != null && bendEnd < bendMax,
            preBend = n.bend != null && bendStart > 0,
            staccato = n.staccato,
            vibrato = n.vibrato,
            wideVibrato = n.wideVibrato,
            trill = n.trill,
            bendPoints = n.bend?.let { b ->
                val pts = b.points.sortedBy { it.position }
                when {
                    pts.isNotEmpty() -> pts.flatMap { listOf(it.position, it.tone) }
                    b.tone > 0 -> listOf(0, 0, 30, b.tone, 60, b.tone)
                    else -> emptyList()
                }
            }.orEmpty(),
            nextFret = next,
            harmonicFret = n.harmonicFret,
        )
    }

    private val STANDARD_TUNING = listOf(64, 59, 55, 50, 45, 40, 35, 30)

    private fun shift(fret: Int, by: Int, fixed: Boolean): Int {
        if (by == 0 || fixed) return fret
        var f = fret + by
        while (f < 0) f += 12
        return f
    }

    /** 100 = full, 50 = ½, 150 = 1½ … */
    fun bendLabel(tone: Int): String {
        val q = Math.round(tone / 25.0).toInt()
        val whole = q / 4
        val frac = listOf("", "¼", "½", "¾")[q % 4]
        return when {
            q <= 0 -> "0"
            whole == 1 && frac.isEmpty() -> "full"
            whole == 0 -> frac
            else -> "$whole$frac"
        }
    }

    private fun formatFret(v: Double): String =
        if (v == Math.floor(v)) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

    // ─── Cross-beat links (ties, hammer-on/pull-off, slide direction) ───────

    private class Resolved(
        val tieFret: IdentityHashMap<RevisionNote, Int>,
        val nextFret: IdentityHashMap<RevisionNote, Int>,
    ) {
        companion object {
            val EMPTY = Resolved(IdentityHashMap(), IdentityHashMap())
        }
    }

    private fun resolveLinks(measures: List<RevisionMeasure>): Resolved {
        val tieFret = IdentityHashMap<RevisionNote, Int>()
        val nextFret = IdentityHashMap<RevisionNote, Int>()
        val voiceCount = measures.maxOfOrNull { it.voices.size } ?: 0
        for (v in 0 until voiceCount) {
            val lastFret = HashMap<Int, Int>()
            val waiting = HashMap<Int, RevisionNote>()
            for (m in measures) {
                val voice = m.voices.getOrNull(v) ?: continue
                for (b in voice.beats) {
                    for (n in b.notes) {
                        if (n.rest || (n.dead && n.fret == null)) continue
                        val s = n.string?.toInt() ?: continue
                        val fret = n.fret ?: lastFret[s]?.also { if (n.tie) tieFret[n] = it } ?: continue
                        waiting.remove(s)?.let { prev -> nextFret[prev] = fret }
                        lastFret[s] = fret
                        if (n.hp || n.slide != null) waiting[s] = n
                    }
                }
            }
        }
        return Resolved(tieFret, nextFret)
    }

    private fun contentKey(slots: List<BeatSlot>, isEmpty: Boolean, signature: List<Int>, length: Long): String {
        if (isEmpty) return "rest:${signature[0]}/${signature[1]}:$length"
        val sb = StringBuilder()
        sb.append(length).append('#')
        for (slot in slots) {
            sb.append(slot.onsetTicks).append('[')
            for (g in slot.graces) g.notes.forEach { sb.append('g').append(it.row).append(':').append(it.fret).append(',') }
            for (b in slot.beats) {
                sb.append(b.voice).append('/').append(b.durationTicks).append('t').append(b.type)
                if (b.isRest) sb.append('r')
                if (b.palmMute) sb.append('m')
                if (b.letRing) sb.append('l')
                b.velocity?.let { sb.append('v').append(it) }
                b.chordText?.let { sb.append('c').append(it) }
                for (n in b.notes) {
                    sb.append('|').append(n.row).append(':').append(n.fret)
                    if (n.isTie) sb.append('t')
                    if (n.isDead) sb.append('x')
                    if (n.isGhost) sb.append('o')
                    if (n.hpLabel != null) sb.append(n.hpLabel)
                    if (n.slide != null) sb.append('s').append(n.slide)
                    if (n.bendTone > 0) sb.append('b').append(n.bendTone)
                    if (n.accent > 0) sb.append('a').append(n.accent)
                    if (n.harmonic != null) sb.append('h').append(n.harmonic)
                    if (n.staffPos != 0f) sb.append('@').append(n.staffPos)
                }
                sb.append(';')
            }
            sb.append(']')
        }
        return sb.toString()
    }
}

// ─── Render model ────────────────────────────────────────────────────────────

data class TempoMark(val measure: Int, val bpmQuarter: Double)

data class RenderedTrack(
    val name: String,
    val instrument: String = "",
    /** 0-based General MIDI program, 1024 = drums. */
    val instrumentId: Int = 29,
    val family: TabParser.InstrumentFamily,
    /** Tab lines (guitar/bass) or 5 (drum staff). */
    val numStrings: Int,
    val tuning: List<Int>,
    val capo: Int? = null,
    val transpose: Int = 0,
    val bpm: Int,
    val tempo: List<TempoMark> = emptyList(),
    val hasPickup: Boolean = false,
    val measures: List<RenderedMeasure>,
) {
    val isDrums: Boolean get() = family == TabParser.InstrumentFamily.DRUMS
}

data class RenderedMeasure(
    val index: Int,
    /** Printed bar number; null for a pickup bar. */
    val number: Int? = index + 1,
    val isPickup: Boolean = false,
    val timeSignature: List<Int>,
    val showSignature: Boolean = index == 0,
    val marker: String?,
    val repeatStart: Boolean,
    val repeatCount: Int?,
    val alternateEnding: List<Int> = emptyList(),
    val doubleBar: Boolean = false,
    /** Set when the tempo changes at this bar (quarter-note bpm). */
    val tempoBpm: Double? = null,
    /** Tempo in effect in this bar (quarter-note bpm). */
    val bpm: Double = 120.0,
    val lengthTicks: Long = TabParser.TICKS_PER_WHOLE,
    /** Onsets across all voices, in time order. */
    val slots: List<BeatSlot> = emptyList(),
    /** Every non-grace beat, in time order. */
    val beats: List<RenderedBeat>,
    val isEmpty: Boolean = false,
    /** Identical playing gives an identical key (riff and section folding). */
    val contentKey: String = "",
) {
    val totalDuration: Double get() = lengthTicks.toDouble() / TabParser.TICKS_PER_WHOLE
}

data class BeatSlot(
    val onsetTicks: Long,
    /** Time until the next onset (or the bar end); drives horizontal spacing. */
    val spanTicks: Long,
    val beats: List<RenderedBeat>,
    val graces: List<RenderedBeat>,
)

data class RenderedBeat(
    val index: Int,
    val voice: Int = 0,
    val onsetTicks: Long = 0,
    val durationTicks: Long = 0,
    val durationFraction: Double,
    val type: Int = 4,
    val dots: Int,
    val tuplet: Int?,
    val tupletStart: Boolean = false,
    val tupletStop: Boolean = false,
    val beamStart: Boolean = false,
    val beamStop: Boolean = false,
    val isRest: Boolean,
    val isGrace: Boolean = false,
    val graceOnBeat: Boolean = false,
    val palmMute: Boolean,
    val letRing: Boolean = false,
    val vibrato: Boolean,
    val wideVibrato: Boolean = false,
    val pickStroke: String?,
    val velocity: String?,
    val chordText: String? = null,
    val text: String? = null,
    val tapping: Boolean = false,
    val upStroke: Boolean = false,
    val downStroke: Boolean = false,
    val tremoloPicking: Boolean = false,
    val tremoloBar: Boolean = false,
    val notes: List<RenderedNote>,
)

data class RenderedNote(
    /** Guitar: string index (0 = highest). Drums: unused (see [staffPos]). */
    val row: Int,
    /** Guitar: fret (after transpose). Drums: Songsterr articulation id. */
    val fret: Int,
    /** Text drawn on the string: "5", "x", "(5)", "<12>", "5<17>". Empty for drums. */
    val label: String,
    /** Drums: staff position in line spaces (0 = top line, 4 = bottom line). */
    val staffPos: Float = 0f,
    /** Sounding MIDI note (guitar pitch or General MIDI drum sound). */
    val pitch: Int = 60,
    val isDead: Boolean = false,
    val isGhost: Boolean = false,
    val isTie: Boolean = false,
    /** 0 none, 1 accent (>), 2 heavy accent (^). */
    val accent: Int = 0,
    val hpLabel: String? = null,
    val slide: String? = null,
    val slideBefore: String? = null,
    val slideAfter: String? = null,
    val harmonic: String? = null,
    val harmonicLabel: String? = null,
    /** Hundredths of a whole tone (100 = full). */
    val bendTone: Int = 0,
    val bendLabel: String? = null,
    val bendRelease: Boolean = false,
    val preBend: Boolean = false,
    val staccato: Boolean = false,
    val vibrato: Boolean = false,
    val wideVibrato: Boolean = false,
    val trill: Boolean = false,
    val drumGlyph: TabParser.DrumGlyph = TabParser.DrumGlyph.HEAD,
    /** Bend curve as (position, tone) pairs; position 0..60 spans the note, tone 100 = full. */
    val bendPoints: List<Int> = emptyList(),
    /** Fret of the next note on this string when this note hammers, pulls or slides into it. */
    val nextFret: Int? = null,
    /** Fret the harmonic is touched at (artificial / tapped / pinch harmonics). */
    val harmonicFret: Double? = null,
) {
    val isHammerPull: Boolean get() = hpLabel != null
    val isAccented: Boolean get() = accent > 0
    val hasBend: Boolean get() = bendLabel != null
}
