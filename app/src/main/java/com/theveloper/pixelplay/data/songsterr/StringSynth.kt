package com.theveloper.pixelplay.data.songsterr

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * A physically modelled guitar / bass for "SYNTH" playback.
 *
 * Every string of every part is its own digital waveguide (extended Karplus–Strong): a delay
 * line one period long with a loss filter, read with 3rd-order Lagrange interpolation so its
 * length (the pitch) can glide continuously. Because the string keeps vibrating while its
 * length changes, bends, releases, vibrato, slides, hammer-ons and pull-offs behave like a
 * real string instead of re-triggering a sample.
 *
 * - Pitch comes from tuning + capo + fret; brightness and sustain depend on the string (wound
 *   strings are darker and slightly inharmonic) and the fret (short vibrating length at high
 *   frets = less sustain).
 * - Pick: a plucked-shape excitation with the pick position's comb, filtered by how hard it's
 *   hit; picking a ringing string damps it first. Hammer / pull / tap: small excitation, no
 *   damping. Palm mute, dead notes, ghost notes, accents, harmonics (natural, artificial,
 *   pinch, tapped), let ring, staccato, tremolo picking, trills, whammy dips and strums.
 * - Each part runs through its own amp / body: nylon & steel acoustic bodies, clean / jazz
 *   electric pickups, overdrive and distortion with a cabinet, finger / pick / slap / fretless
 *   bass. A small room reverb and a limiter finish the mix.
 *
 * Pure Kotlin (no Android), so it can be tested on the JVM.
 */
object StringSynth {
    const val SAMPLE_RATE = 44_100

    enum class Tone { NYLON, STEEL, JAZZ, CLEAN, MUTED, OVERDRIVE, DISTORTION, BASS_FINGER, BASS_PICK, FRETLESS, SLAP }

    /** General MIDI program (0-based) → tone; null = not a string instrument we model. */
    fun toneFor(program: Int, family: TabParser.InstrumentFamily): Tone? = when (program) {
        24 -> Tone.NYLON
        25 -> Tone.STEEL
        26 -> Tone.JAZZ
        27, 31 -> Tone.CLEAN
        28 -> Tone.MUTED
        29 -> Tone.OVERDRIVE
        30 -> Tone.DISTORTION
        32, 33, 38, 39 -> Tone.BASS_FINGER
        34 -> Tone.BASS_PICK
        35 -> Tone.FRETLESS
        36, 37 -> Tone.SLAP
        // Other GM sounds (piano, strings…) stay on the MIDI synth.
        in 0..127 -> null
        else -> when (family) {
            TabParser.InstrumentFamily.GUITAR -> Tone.OVERDRIVE
            TabParser.InstrumentFamily.BASS -> Tone.BASS_FINGER
            else -> null
        }
    }

    val Tone.isBass: Boolean get() = this == Tone.BASS_FINGER || this == Tone.BASS_PICK || this == Tone.FRETLESS || this == Tone.SLAP
}

enum class Attack {
    /** Picked or plucked (damps a ringing string first). */
    PICK,
    /** Hammer-on: the fretting finger strikes the string. */
    HAMMER,
    /** Pull-off: the finger plucks the string as it leaves. */
    PULL,
    /** Right-hand tap. */
    TAP,
    /** Legato slide arriving: no new attack. */
    SLIDE,
    /** Tied note: the string just keeps sounding. */
    TIE,
}

/** One note on one string, in file time (ms). */
class StringEvent(
    val startMs: Double,
    /** When the player stops the string (mute); +∞ = rings until the next note on this string. */
    var endMs: Double,
    /** Sounding pitch, MIDI note number (may be fractional). */
    val pitch: Float,
    val attack: Attack,
    /** 0..1 */
    val velocity: Float,
    val palmMute: Boolean = false,
    val dead: Boolean = false,
    val harmonic: Boolean = false,
    /** Bend curve: (fraction of the note 0..1, semitones) pairs. */
    val bend: FloatArray? = null,
    /** Semitones held from the previous note (a tie after a bend or slide). */
    val holdOffset: Float = 0f,
    /** Vibrato depth in semitones (0 = none). */
    val vibrato: Float = 0f,
    val vibratoRateHz: Float = 5.5f,
    /** Legato / shift slide to this pitch, starting at [slideStartMs] after the note starts. */
    val slideTo: Float? = null,
    val slideStartMs: Double = 0.0,
    val slideOut: Boolean = false,
    /** Slide in from this pitch over [slideInMs]. */
    val slideFrom: Float? = null,
    val slideInMs: Double = 0.0,
    /** Whammy-bar dip over the note. */
    val whammy: Boolean = false,
    /** Stepped (fretted) slides; false on fretless. */
    val stepped: Boolean = true,
    /** Let ring: the string isn't stopped at the end of the note. */
    val letRing: Boolean = false,
) {
    val durationMs: Double get() = endMs - startMs

    /** Semitones added to [pitch] at [t] ms into the note. */
    fun offsetAt(t: Double, nominalMs: Double): Float {
        var off = holdOffset
        val b = bend
        if (b != null && b.size >= 2) {
            val f = (t / nominalMs.coerceAtLeast(1.0)).toFloat()
            off = if (f <= b[0]) b[1] else if (f >= b[b.size - 2]) b[b.size - 1] else {
                var i = 0
                while (i + 3 < b.size && b[i + 2] < f) i += 2
                val u = ((f - b[i]) / (b[i + 2] - b[i]).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
                // Real bends are curved: ease in and out.
                val s = u * u * (3f - 2f * u)
                b[i + 1] + (b[i + 3] - b[i + 1]) * s
            }
        }
        slideFrom?.let { from ->
            if (t < slideInMs) {
                val u = (t / slideInMs.coerceAtLeast(1.0)).toFloat()
                off += glide(from - pitch, 0f, u)
            }
        }
        slideTo?.let { to ->
            if (t > slideStartMs) {
                val span = (nominalMs - slideStartMs).coerceAtLeast(20.0)
                val u = ((t - slideStartMs) / span).toFloat().coerceIn(0f, 1f)
                off += glide(0f, to - pitch, u)
            }
        }
        if (vibrato > 0f) {
            // Players push the string up and let it back: vibrato only goes sharp.
            val fade = ((t - 70.0) / 180.0).toFloat().coerceIn(0f, 1f)
            val ph = 2.0 * PI * vibratoRateHz * t / 1000.0
            off += vibrato * fade * (0.5f - 0.5f * cos(ph).toFloat())
        }
        if (whammy) {
            val u = (t / nominalMs.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
            off += (-1.5 * sin(PI * u)).toFloat()
        }
        return off
    }

    private fun glide(a: Float, b: Float, u: Float): Float {
        val raw = a + (b - a) * u
        if (!stepped) return raw
        // Fret by fret: sit on each fret, then move quickly to the next.
        val d = raw - a
        val sign = if (d < 0) -1f else 1f
        val m = abs(d)
        val whole = floor(m)
        val frac = m - whole
        val step = ((frac - 0.55f) / 0.45f).coerceIn(0f, 1f)
        return a + sign * (whole + step * step * (3f - 2f * step))
    }

    /** Last offset of the note (held by a following tie). */
    fun endOffset(nominalMs: Double): Float {
        var off = holdOffset
        bend?.let { if (it.size >= 2) off = it[it.size - 1] }
        slideTo?.let { if (!slideOut) off += it - pitch }
        return off
    }

    var nominalMs: Double = 0.0
}

class StringPartScore(
    val tone: StringSynth.Tone,
    val numStrings: Int,
    /** Per string (0 = highest), sorted by start. */
    val strings: Array<List<StringEvent>>,
    val pan: Float,
    val gain: Float,
)

class StringScore(val parts: List<StringPartScore>) {
    val isEmpty: Boolean get() = parts.all { p -> p.strings.all { it.isEmpty() } }
}

/**
 * Collects a tab's guitar / bass notes (while [TabMidi] walks the bars) into string events.
 */
class StringScoreBuilder(private val pitchShift: Int) {

    private class PartState(val track: RenderedTrack, val tone: StringSynth.Tone) {
        val n = track.numStrings.coerceIn(1, 12)
        val events = Array(n) { ArrayList<StringEvent>() }
        val legatoNext = BooleanArray(n)
        val legatoSlide = BooleanArray(n)
        val lastPitch = FloatArray(n)
    }

    private val parts = LinkedHashMap<Int, PartState>()

    fun wants(track: RenderedTrack): Boolean =
        !track.isDrums && track.family != TabParser.InstrumentFamily.OTHER &&
            StringSynth.toneFor(track.instrumentId, track.family) != null

    private fun part(index: Int, track: RenderedTrack): PartState = parts.getOrPut(index) {
        PartState(track, StringSynth.toneFor(track.instrumentId, track.family) ?: StringSynth.Tone.CLEAN)
    }

    /**
     * One beat of a guitar / bass part.
     * @param quarterMs one quarter note at the playing speed.
     * @param dynamic MIDI-style velocity from the dynamics marks (1..127).
     */
    fun addBeat(partIndex: Int, track: RenderedTrack, beat: RenderedBeat, onMs: Double, lenMs: Double, quarterMs: Double, dynamic: Int) {
        if (beat.isRest || beat.notes.isEmpty()) return
        val p = part(partIndex, track)
        val tone = p.tone
        val bass = with(StringSynth) { tone.isBass }
        val notes = beat.notes.filter { it.row in 0 until p.n }
        // Strum order: down = low string first. A plain chord still spreads a little.
        val up = beat.upStroke || beat.pickStroke?.contains("up", ignoreCase = true) == true
        val ordered = if (up) notes.sortedBy { it.row } else notes.sortedByDescending { it.row }
        val strumStep = when {
            ordered.size < 2 -> 0.0
            beat.upStroke || beat.downStroke -> 16.0
            ordered.size >= 3 -> 5.0
            else -> 1.5
        }
        ordered.forEachIndexed { k, n ->
            val at = onMs + k * strumStep
            addNote(p, bass, tone, beat, n, at, (lenMs - k * strumStep).coerceAtLeast(lenMs * 0.5), quarterMs, dynamic)
        }
    }

    /** Grace notes of a slot: quick notes just before [mainOnMs]. */
    fun addGrace(partIndex: Int, track: RenderedTrack, grace: RenderedBeat, mainOnMs: Double, quarterMs: Double, dynamic: Int) {
        if (grace.notes.isEmpty()) return
        val p = part(partIndex, track)
        val len = min(quarterMs / 8.0, 70.0).coerceAtLeast(25.0)
        val at = if (grace.graceOnBeat) mainOnMs else mainOnMs - len
        for (n in grace.notes) {
            if (n.row !in 0 until p.n) continue
            addNote(p, with(StringSynth) { p.tone.isBass }, p.tone, grace, n, at, len, quarterMs, (dynamic * 0.8).toInt(), grace = true)
        }
    }

    private fun addNote(
        p: PartState, bass: Boolean, tone: StringSynth.Tone, beat: RenderedBeat, n: RenderedNote,
        onMs: Double, lenIn: Double, quarterMs: Double, dynamic: Int, grace: Boolean = false,
    ) {
        val s = n.row
        val list = p.events[s]
        var len = lenIn
        if (n.staccato) len *= 0.5
        var pitch = (n.pitch + pitchShift).toFloat()
        var harmonic = false
        n.harmonic?.let { kind ->
            harmonic = true
            val touch = when (kind) {
                "natural" -> n.fret.toDouble()
                else -> n.harmonicFret?.let { hf -> if (kind == "tapped" || kind == "artificial") hf - n.fret else hf }
                    ?: if (kind == "pinch") 7.0 else 12.0
            }
            pitch += harmonicInterval(touch, natural = kind == "natural")
            if (kind == "natural") pitch -= n.fret // the open string's overtone
        }
        val prev = list.lastOrNull()
        val attack = when {
            n.isTie && prev != null -> Attack.TIE
            p.legatoNext[s] && prev != null && !grace && p.legatoSlide[s] -> Attack.SLIDE
            p.legatoNext[s] && prev != null -> if (pitch >= p.lastPitch[s]) Attack.HAMMER else Attack.PULL
            beat.tapping -> Attack.TAP
            else -> Attack.PICK
        }
        var vel = (dynamic / 127f).coerceIn(0.05f, 1f)
        if (n.isGhost) vel *= 0.5f
        if (n.accent == 1) vel = min(1f, vel * 1.18f)
        if (n.accent == 2) vel = min(1f, vel * 1.32f)

        val bend = n.bendPoints.takeIf { it.size >= 4 }?.let { pts ->
            val maxPos = max(60, pts.filterIndexed { i, _ -> i % 2 == 0 }.maxOrNull() ?: 60).toFloat()
            FloatArray(pts.size) { i -> if (i % 2 == 0) pts[i] / maxPos else pts[i] / 50f }
        }
        val hold = if (attack == Attack.TIE && bend == null && prev != null) prev.endOffset(prev.nominalMs) else 0f

        var slideTo: Float? = null
        var slideStart = 0.0
        var slideOut = false
        var slideFrom: Float? = null
        var slideIn = 0.0
        val slide = n.slide
        var legatoAfter = n.hpLabel != null
        var legatoIsSlide = false
        if (slide != null) {
            if (slide.startsWith("below")) slideFrom = pitch - 4f
            if (slide.startsWith("above")) slideFrom = pitch + 4f
            if (slideFrom != null) slideIn = min(90.0, len * 0.4)
            when {
                slide == "downwards" -> { slideTo = pitch - min(7, max(3, n.fret)); slideOut = true }
                slide == "upwards" -> { slideTo = pitch + 7f; slideOut = true }
                slide.endsWith("legato") || slide.endsWith("shift") -> {
                    n.nextFret?.let { nf -> slideTo = pitch + (nf - n.fret) }
                    legatoAfter = slide.endsWith("legato")
                    legatoIsSlide = true
                }
                slide != "below" && slide != "above" -> n.nextFret?.let { nf -> slideTo = pitch + (nf - n.fret) }
            }
            if (slideTo != null) slideStart = max(0.0, len - min(len * (if (slideOut) 0.6 else 0.5), if (slideOut) 220.0 else 160.0))
        }

        val vib = when {
            n.wideVibrato || beat.wideVibrato -> if (bass) 0.7f else 1.0f
            n.vibrato || beat.vibrato -> if (bass) 0.25f else 0.38f
            else -> 0f
        }
        val palm = beat.palmMute || tone == StringSynth.Tone.MUTED
        val stepped = tone != StringSynth.Tone.FRETLESS

        fun event(start: Double, dur: Double, pitchAt: Float, a: Attack, v: Float, withBend: Boolean = true): StringEvent =
            StringEvent(
                startMs = start,
                endMs = start + dur,
                pitch = pitchAt,
                attack = a,
                velocity = v,
                palmMute = palm,
                dead = n.isDead,
                harmonic = harmonic,
                bend = if (withBend) bend else null,
                holdOffset = hold,
                vibrato = vib,
                vibratoRateHz = if (n.wideVibrato || beat.wideVibrato) 4.6f else 5.6f,
                slideTo = if (withBend) slideTo else null,
                slideStartMs = slideStart,
                slideOut = slideOut,
                slideFrom = slideFrom,
                slideInMs = slideIn,
                whammy = beat.tremoloBar,
                stepped = stepped,
                letRing = beat.letRing,
            ).also { it.nominalMs = dur }

        when {
            beat.tremoloPicking && !n.isTie -> {
                val step = if (quarterMs / 8.0 >= 55.0) quarterMs / 8.0 else quarterMs / 4.0
                var t = 0.0
                var first = true
                while (t < len - 5.0) {
                    val d = min(step, len - t)
                    list += event(onMs + t, d, pitch, if (first) attack else Attack.PICK, vel * (if (first) 1f else 0.85f), withBend = false)
                    first = false
                    t += step
                }
            }
            n.trill && !n.isDead -> {
                val step = (quarterMs / 4.0).coerceAtLeast(45.0)
                var t = 0.0
                var k = 0
                while (t < len - 5.0) {
                    val d = min(step, len - t)
                    val a = if (k == 0) attack else if (k % 2 == 1) Attack.HAMMER else Attack.PULL
                    list += event(onMs + t, d, if (k % 2 == 1) pitch + 2f else pitch, a, vel, withBend = false)
                    t += step
                    k++
                }
            }
            else -> list += event(onMs, len, pitch, attack, vel)
        }
        p.legatoNext[s] = legatoAfter && !n.isDead
        p.legatoSlide[s] = legatoIsSlide
        p.lastPitch[s] = pitch
    }

    /** Pitch above the fretted note of a harmonic touched [fret] frets higher. */
    private fun harmonicInterval(fret: Double, @Suppress("UNUSED_PARAMETER") natural: Boolean): Float {
        fun near(x: Double, tol: Double = 0.3) = abs(fret - x) < tol
        return when {
            near(12.0) -> 12f
            near(7.0) || near(19.0) -> 19f
            near(5.0) || near(24.0) -> 24f
            near(4.0) || near(9.0) || near(16.0) -> 28f
            near(3.2, 0.4) -> 31f
            fret > 0 -> {
                // Node at 1/k of the string: the k-th harmonic.
                val k = Math.round(1.0 / (1.0 - 2.0.pow(-fret / 12.0))).toInt().coerceIn(2, 8)
                (12.0 * ln2(k.toDouble())).toFloat()
            }
            else -> 12f
        }
    }

    private fun ln2(x: Double) = kotlin.math.ln(x) / kotlin.math.ln(2.0)

    fun build(): StringScore? {
        if (parts.isEmpty()) return null
        val count = parts.size
        var guitarSide = 0
        val out = parts.values.mapIndexed { i, p ->
            for (list in p.events) {
                list.sortBy { it.startMs }
                for (k in list.indices) {
                    val e = list[k]
                    val next = list.getOrNull(k + 1)
                    val continues = next != null && next.attack != Attack.PICK && next.attack != Attack.TAP &&
                        next.startMs - e.endMs < 40.0
                    // Let ring / legato: keep sounding until the next note on this string.
                    if (continues || e.letRing) e.endMs = next?.startMs ?: Double.POSITIVE_INFINITY
                    else e.endMs += 12.0 // the fretting hand lifts just after the next beat
                    if (next != null && e.endMs > next.startMs) e.endMs = next.startMs
                }
            }
            val bass = with(StringSynth) { p.tone.isBass }
            val pan = when {
                bass || count == 1 -> 0f
                else -> if (guitarSide++ % 2 == 0) -0.35f else 0.35f
            }
            StringPartScore(
                tone = p.tone,
                numStrings = p.n,
                strings = Array(p.n) { s -> p.events[s].toList() },
                pan = pan,
                gain = if (bass) 1.0f else 0.8f,
            )
        }
        return StringScore(out)
    }

}

/**
 * Renders a [StringScore] in real time. Call [render] from the audio thread only.
 */
class StringSynthEngine(private val score: StringScore, private val sr: Int = StringSynth.SAMPLE_RATE) :
    com.theveloper.pixelplay.data.soundfont.ScorePcmSource {

    /** Score time of the next rendered frame (ms). Shifted by [nudge] to follow the MIDI player. */
    @Volatile override var shiftMs: Double = 0.0
    private var frame: Long = 0

    private val block = 32
    private val parts = score.parts.map { PartEngine(it, sr) }
    private val mixL = FloatArray(block)
    private val mixR = FloatArray(block)
    private val reverb = Reverb(sr)
    private var limEnv = 0f

    /** Frames rendered so far. */
    override val framesRendered: Long get() = frame

    /** Score ms at output frame [f] (with the current shift). */
    fun scoreMsAt(f: Long): Double = f * 1000.0 / sr + shiftMs

    /** Jumps the score clock so the next frame plays score time [ms]. */
    fun seekScore(ms: Double) {
        shiftMs = ms - frame * 1000.0 / sr
    }

    /** Fills [out] with interleaved stereo 16-bit frames. */
    override fun render(out: ShortArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = min(block, frames - done)
            java.util.Arrays.fill(mixL, 0, n, 0f)
            java.util.Arrays.fill(mixR, 0, n, 0f)
            val t0 = scoreMsAt(frame)
            val blockMs = n * 1000.0 / sr
            for (p in parts) p.render(t0, blockMs, n, mixL, mixR)
            for (i in 0 until n) { mixL[i] *= 0.7f; mixR[i] *= 0.7f }
            reverb.process(mixL, mixR, n)
            for (i in 0 until n) {
                // Peak limiter with a fast attack and slow release.
                val pk = max(abs(mixL[i]), abs(mixR[i]))
                limEnv = if (pk > limEnv) pk else limEnv * 0.99985f + pk * 0.00015f
                val g = if (limEnv > 0.9f) 0.9f / limEnv else 1f
                out[(done + i) * 2] = toPcm(mixL[i] * g)
                out[(done + i) * 2 + 1] = toPcm(mixR[i] * g)
            }
            frame += n
            done += n
        }
    }

    /** Float version (tests). */
    fun renderFloat(outL: FloatArray, outR: FloatArray, frames: Int) {
        val tmp = ShortArray(frames * 2)
        render(tmp, frames)
        for (i in 0 until frames) {
            outL[i] = tmp[2 * i] / 32768f
            outR[i] = tmp[2 * i + 1] / 32768f
        }
    }

    private fun toPcm(x: Float): Short = (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    // ─── One part: its strings plus its amp / body ─────────────────────────────

    private class PartEngine(val part: StringPartScore, sr: Int) {
        val tone = part.tone
        val voices = Array(part.numStrings) { s -> StringVoice(sr, tone, s, part.numStrings, part.strings[s]) }
        val buf = FloatArray(32)
        val fx = AmpChain(tone, sr)
        val gl = part.gain * sqrt((1f - part.pan) / 2f) * 1.41f
        val gr = part.gain * sqrt((1f + part.pan) / 2f) * 1.41f

        fun render(t0: Double, blockMs: Double, n: Int, l: FloatArray, r: FloatArray) {
            java.util.Arrays.fill(buf, 0, n, 0f)
            var any = false
            for (v in voices) if (v.render(t0, blockMs, n, buf)) any = true
            if (!any && fx.quiet) return
            fx.process(buf, n)
            for (i in 0 until n) {
                l[i] += buf[i] * gl
                r[i] += buf[i] * gr
            }
        }
    }

    // ─── One string (digital waveguide) ─────────────────────────────────────

    private class StringVoice(
        val sr: Int,
        val tone: StringSynth.Tone,
        val string: Int,
        val numStrings: Int,
        val events: List<StringEvent>,
    ) {
        private val size = 8192
        private val mask = size - 1
        private val line = FloatArray(size)
        private var w = 0
        private var lp = 0f
        private var apX = 0f
        private var apY = 0f

        private var next = 0
        private var cur: StringEvent? = null
        private var curPitch = 0f
        private var len = 100f // current loop delay in samples
        private var loss = 0.999f
        private var lossTarget = 0.999f
        private var damp = 0.3f
        private var dampTarget = 0.3f
        private var muted = true
        private var idleBlocks = 1000
        private var pickDamp = 0 // samples left in which the old vibration is damped
        private val exc = FloatArray(4096)
        private var excLen = 0
        private var excPos = 0
        private var clickLeft = 0
        private var clickAmp = 0f
        private var rng = 0x1234567 + string * 7919
        private var dcX = 0f
        private var dcY = 0f

        private val bass = with(StringSynth) { tone.isBass }
        /** Wound strings: the lower half of a guitar, every bass string. */
        private val wound = bass || string >= numStrings / 2
        private val allpass = if (wound) (if (bass) -0.22f else -0.12f) else 0f
        /** Where the pickup / ear reads the string, as a fraction of its length. */
        private val pickup = when (tone) {
            StringSynth.Tone.JAZZ -> 0.27f
            StringSynth.Tone.NYLON, StringSynth.Tone.STEEL -> 0f
            StringSynth.Tone.BASS_FINGER, StringSynth.Tone.FRETLESS -> 0.2f
            else -> 0.11f
        }

        private fun noise(): Float {
            rng = rng * 1103515245 + 12345
            return ((rng ushr 8) and 0xFFFF) / 32768f - 1f
        }

        /** Returns true if the string made sound in this block. */
        fun render(t0: Double, blockMs: Double, n: Int, out: FloatArray): Boolean {
            val t1 = t0 + blockMs
            // Start any note that begins in this block.
            while (next < events.size && events[next].startMs < t1) {
                val e = events[next++]
                if (e.endMs <= t0 && next < events.size && events[next].startMs < t1) continue
                start(e)
            }
            val e = cur
            if (e != null) {
                if (!muted && t0 >= e.endMs) mute()
                val t = t0 - e.startMs
                curPitch = e.pitch + e.offsetAt(t.coerceAtLeast(0.0), e.nominalMs)
                if (e.slideOut && t > e.slideStartMs) {
                    // Sliding off the neck: the note fades as it goes.
                    val u = ((t - e.slideStartMs) / (e.nominalMs - e.slideStartMs).coerceAtLeast(20.0)).coerceIn(0.0, 1.0)
                    lossTarget = min(lossTarget, lossFor(curPitch, (0.6 - 0.5 * u).coerceAtLeast(0.05)))
                }
            }
            if (idleBlocks > 60 && excPos >= excLen) return false

            val f0 = 440.0 * 2.0.pow((curPitch - 69.0) / 12.0)
            val target = targetLength(f0)
            val startLen = len
            // Pitch jumps (hammer-ons) glide over a couple of blocks; slow changes follow exactly.
            val endLen = if (abs(target - startLen) > startLen * 0.03f) startLen + (target - startLen) * 0.5f else target
            len = endLen
            loss += (lossTarget - loss) * 0.25f
            damp += (dampTarget - damp) * 0.25f

            var peak = 0f
            val g = loss
            val s = damp
            val a = allpass
            for (i in 0 until n) {
                val d = startLen + (endLen - startLen) * (i + 1) / n
                var x = read(d)
                if (pickDamp > 0) {
                    x *= 0.12f
                    pickDamp--
                }
                if (excPos < excLen) x += exc[excPos++]
                // Loss filter: one-pole low-pass (frequency-dependent damping).
                lp = (1f - s) * x + s * lp
                var y = lp * g
                if (a != 0f) {
                    // Stiffness: a first-order all-pass makes wound strings slightly inharmonic.
                    val ap = a * y + apX - a * apY
                    apX = y
                    apY = ap
                    y = ap
                }
                line[w] = y
                w = (w + 1) and mask
                var o = y
                if (pickup > 0f) o -= 0.55f * line[(w - 1 - (d * pickup).toInt()) and mask]
                if (clickLeft > 0) {
                    o += noise() * clickAmp
                    clickAmp *= 0.8f
                    clickLeft--
                }
                // DC blocker.
                val dc = o - dcX + 0.995f * dcY
                dcX = o
                dcY = dc
                out[i] += dc
                val ab = abs(dc)
                if (ab > peak) peak = ab
            }
            if (peak < 2e-5f && excPos >= excLen) {
                idleBlocks++
                if (idleBlocks == 61) {
                    java.util.Arrays.fill(line, 0f)
                    lp = 0f; apX = 0f; apY = 0f
                }
            } else idleBlocks = 0
            return true
        }

        /** Loop delay for [f0], minus the loss filter's and all-pass's phase delay (keeps it in tune). */
        private fun targetLength(f0: Double): Float {
            val period = sr / f0
            val wv = 2.0 * PI * f0 / sr
            val s = damp.toDouble()
            val lpDelay = atan2(s * sin(wv), 1.0 - s * cos(wv)) / wv
            var apDelay = 0.0
            if (allpass != 0f) {
                val a = allpass.toDouble()
                // H = (a + z^-1) / (1 + a z^-1)
                val nr = a + cos(wv); val ni = -sin(wv)
                val dr = 1.0 + a * cos(wv); val di = -a * sin(wv)
                val ph = atan2(ni, nr) - atan2(di, dr)
                apDelay = -ph / wv
            }
            return (period - lpDelay - apDelay).toFloat().coerceIn(3f, (size - 8).toFloat())
        }

        private fun read(d: Float): Float {
            // 3rd-order Lagrange interpolation at d samples back.
            val pos = w - d
            val i = floor(pos).toInt()
            val f = pos - i
            val xm1 = line[(i - 1) and mask]
            val x0 = line[i and mask]
            val x1 = line[(i + 1) and mask]
            val x2 = line[(i + 2) and mask]
            val fm1 = f + 1f
            val f1 = f - 1f
            val f2 = f - 2f
            return -xm1 * f * f1 * f2 / 6f + x0 * fm1 * f1 * f2 / 2f - x1 * fm1 * f * f2 / 2f + x2 * fm1 * f * f1 / 6f
        }

        private fun sustainSeconds(pitch: Float): Double {
            // Low open strings ring for a long time; high frets die sooner.
            val base = when (tone) {
                StringSynth.Tone.NYLON -> 3.2
                StringSynth.Tone.STEEL -> 5.0
                StringSynth.Tone.JAZZ -> 4.0
                StringSynth.Tone.CLEAN, StringSynth.Tone.MUTED -> 6.0
                StringSynth.Tone.OVERDRIVE, StringSynth.Tone.DISTORTION -> 7.0
                StringSynth.Tone.BASS_FINGER, StringSynth.Tone.BASS_PICK, StringSynth.Tone.SLAP -> 7.0
                StringSynth.Tone.FRETLESS -> 4.5
            }
            return base * 2.0.pow(-(pitch - 40.0) / 30.0)
        }

        private fun lossFor(pitch: Float, t60: Double): Float {
            val f0 = 440.0 * 2.0.pow((pitch - 69.0) / 12.0)
            val wv = 2.0 * PI * f0 / sr
            val s = damp.toDouble()
            // |H(w0)| of the low-pass already takes some energy each pass.
            val mag = (1 - s) / sqrt(1 - 2 * s * cos(wv) + s * s)
            return (10.0.pow(-3.0 / (f0 * t60)) / mag).coerceIn(0.5, 0.99995).toFloat()
        }

        private fun brightnessDamp(e: StringEvent, pitch: Float): Float {
            var s = when (tone) {
                StringSynth.Tone.NYLON -> 0.42f
                StringSynth.Tone.STEEL -> 0.18f
                StringSynth.Tone.JAZZ -> 0.45f
                StringSynth.Tone.CLEAN, StringSynth.Tone.MUTED -> 0.26f
                StringSynth.Tone.OVERDRIVE, StringSynth.Tone.DISTORTION -> 0.3f
                StringSynth.Tone.BASS_FINGER, StringSynth.Tone.FRETLESS -> 0.5f
                StringSynth.Tone.BASS_PICK -> 0.36f
                StringSynth.Tone.SLAP -> 0.22f
            }
            if (wound) s += 0.06f
            // Higher up the neck the vibrating length is short and the tone is rounder.
            s += ((pitch - 52f) / 48f).coerceIn(0f, 0.12f)
            if (e.palmMute) s = max(s, 0.62f)
            if (e.harmonic) s = 0.06f
            if (e.dead) s = 0.55f
            return s.coerceIn(0.02f, 0.85f)
        }

        private fun start(e: StringEvent) {
            cur = e
            muted = false
            idleBlocks = 0
            val pitch0 = e.pitch + e.offsetAt(0.0, e.nominalMs)
            curPitch = pitch0
            dampTarget = brightnessDamp(e, pitch0)
            val sus = when {
                e.dead -> 0.035
                e.palmMute -> if (bass) 0.5 else 0.3
                e.harmonic -> sustainSeconds(pitch0) * 1.2
                else -> sustainSeconds(pitch0)
            }
            if (e.attack == Attack.PICK || e.attack == Attack.TAP || e.attack == Attack.HAMMER || e.attack == Attack.PULL) {
                damp = dampTarget
            }
            lossTarget = lossFor(pitch0, sus)
            if (e.attack == Attack.PICK || e.attack == Attack.TAP) loss = lossTarget
            val f0 = 440.0 * 2.0.pow((pitch0 - 69.0) / 12.0)
            val newLen = targetLength(f0)
            when (e.attack) {
                Attack.TIE, Attack.SLIDE -> {
                    // Keep vibrating; only the pitch follows the note.
                }
                Attack.HAMMER -> {
                    len = newLen
                    excite(e, newLen, 0.22f * e.velocity, bright = 0.55f, pickPos = 0.5f, noiseMix = 0.1f)
                }
                Attack.PULL -> {
                    len = newLen
                    excite(e, newLen, 0.35f * e.velocity, bright = 0.7f, pickPos = 0.3f, noiseMix = 0.15f)
                }
                Attack.TAP -> {
                    len = newLen
                    excite(e, newLen, 0.5f * e.velocity, bright = 0.75f, pickPos = 0.45f, noiseMix = 0.1f)
                }
                Attack.PICK -> {
                    len = newLen
                    pickDamp = newLen.toInt()
                    val pickPos = when (tone) {
                        StringSynth.Tone.NYLON -> 0.2f
                        StringSynth.Tone.JAZZ -> 0.25f
                        StringSynth.Tone.BASS_FINGER, StringSynth.Tone.FRETLESS -> 0.22f
                        StringSynth.Tone.SLAP -> 0.08f
                        else -> 0.13f
                    }
                    val bright = when {
                        e.dead -> 0.5f
                        e.palmMute -> 0.45f
                        tone == StringSynth.Tone.NYLON || tone == StringSynth.Tone.BASS_FINGER || tone == StringSynth.Tone.FRETLESS -> 0.45f + 0.35f * e.velocity
                        tone == StringSynth.Tone.SLAP -> 0.95f
                        else -> 0.55f + 0.4f * e.velocity
                    }
                    excite(e, newLen, e.velocity, bright, pickPos, noiseMix = if (e.dead) 0.9f else 0.2f)
                    // The pick scraping the string.
                    val picked = tone != StringSynth.Tone.NYLON && tone != StringSynth.Tone.BASS_FINGER && tone != StringSynth.Tone.FRETLESS
                    clickLeft = if (picked) (sr / 1000) else (sr / 2000)
                    clickAmp = (if (picked) 0.05f else 0.02f) * e.velocity
                }
            }
        }

        /** Feeds one period of a pluck shape into the loop. */
        private fun excite(e: StringEvent, lenSamples: Float, amp: Float, bright: Float, pickPos: Float, noiseMix: Float) {
            val n = lenSamples.toInt().coerceIn(4, exc.size)
            val peak = (pickPos * n).toInt().coerceIn(1, n - 2)
            if (e.harmonic) {
                // A touched node leaves an almost pure tone.
                for (i in 0 until n) exc[i] = sin(PI * i / n).toFloat() * sin(PI * i / n).toFloat()
            } else {
                for (i in 0 until n) {
                    val tri = if (i <= peak) i.toFloat() / peak else (n - i).toFloat() / (n - peak)
                    exc[i] = tri * (1f - noiseMix) + noise() * noiseMix
                }
            }
            // Softer attacks are darker: low-pass the excitation (more passes = darker).
            val c = (1f - bright).coerceIn(0f, 0.95f)
            val passes = if (c > 0.5f) 3 else 2
            repeat(passes) {
                var z = exc[n - 1]
                for (i in 0 until n) {
                    z = (1f - c) * exc[i] + c * z
                    exc[i] = z
                }
            }
            // No DC in the loop.
            var mean = 0f
            for (i in 0 until n) mean += exc[i]
            mean /= n
            var mx = 1e-6f
            for (i in 0 until n) {
                exc[i] -= mean
                mx = max(mx, abs(exc[i]))
            }
            val scale = amp * 0.6f / mx
            for (i in 0 until n) exc[i] *= scale
            excLen = n
            excPos = 0
        }

        private fun mute() {
            muted = true
            // Finger lifts / palm touches: fast, dark decay.
            lossTarget = lossFor(curPitch, if (bass) 0.09 else 0.06)
            dampTarget = 0.65f
        }
    }

    // ─── Amp, body and cabinet per part ──────────────────────────────────────

    private class Biquad {
        var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f
        var z1 = 0f; var z2 = 0f
        fun process(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
        fun set(kind: Int, f: Double, q: Double, gainDb: Double, sr: Int): Biquad {
            val w = 2 * PI * f / sr
            val cw = cos(w)
            val sw = sin(w)
            val alpha = sw / (2 * q)
            val a = 10.0.pow(gainDb / 40)
            val (nb0, nb1, nb2, na0, na1, na2) = when (kind) {
                LP -> Six((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
                HP -> Six((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
                BP -> Six(alpha, 0.0, -alpha, 1 + alpha, -2 * cw, 1 - alpha)
                else -> Six(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a) // peak
            }
            b0 = (nb0 / na0).toFloat(); b1 = (nb1 / na0).toFloat(); b2 = (nb2 / na0).toFloat()
            a1 = (na1 / na0).toFloat(); a2 = (na2 / na0).toFloat()
            return this
        }
        private data class Six(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double)
        companion object { const val LP = 0; const val HP = 1; const val BP = 2; const val PEAK = 3 }
    }

    private class AmpChain(val tone: StringSynth.Tone, val sr: Int) {
        private val pre = ArrayList<Biquad>()
        private val post = ArrayList<Biquad>()
        private val body = ArrayList<Pair<Biquad, Float>>()
        private var drive = 0f
        private var outGain = 1f
        private var env = 0f
        var quiet = true
            private set

        init {
            fun bq(k: Int, f: Double, q: Double = 0.707, g: Double = 0.0) = Biquad().set(k, f, q, g, sr)
            when (tone) {
                StringSynth.Tone.NYLON -> {
                    body += bq(Biquad.BP, 98.0, 5.0) to 1.1f
                    body += bq(Biquad.BP, 196.0, 4.0) to 0.7f
                    body += bq(Biquad.BP, 410.0, 3.0) to 0.35f
                    post += bq(Biquad.LP, 5200.0)
                    post += bq(Biquad.HP, 70.0)
                    outGain = 0.55f
                }
                StringSynth.Tone.STEEL -> {
                    body += bq(Biquad.BP, 105.0, 6.0) to 1.0f
                    body += bq(Biquad.BP, 220.0, 5.0) to 0.6f
                    body += bq(Biquad.BP, 480.0, 3.0) to 0.3f
                    post += bq(Biquad.PEAK, 3500.0, 0.8, 3.0)
                    post += bq(Biquad.HP, 75.0)
                    outGain = 0.5f
                }
                StringSynth.Tone.JAZZ -> {
                    post += bq(Biquad.LP, 2600.0)
                    post += bq(Biquad.PEAK, 250.0, 0.8, 2.0)
                    drive = 1.2f; outGain = 1.1f
                }
                StringSynth.Tone.CLEAN, StringSynth.Tone.MUTED -> {
                    post += bq(Biquad.LP, 6500.0)
                    post += bq(Biquad.PEAK, 2800.0, 0.9, 2.0)
                    post += bq(Biquad.HP, 80.0)
                    drive = 1.4f; outGain = 0.75f
                }
                StringSynth.Tone.OVERDRIVE -> {
                    pre += bq(Biquad.HP, 180.0)
                    pre += bq(Biquad.PEAK, 800.0, 0.7, 6.0)
                    drive = 9f
                    post += bq(Biquad.LP, 4800.0)
                    post += bq(Biquad.LP, 6200.0)
                    post += bq(Biquad.PEAK, 120.0, 1.0, 4.0)
                    post += bq(Biquad.HP, 75.0)
                    outGain = 0.32f
                }
                StringSynth.Tone.DISTORTION -> {
                    pre += bq(Biquad.HP, 250.0)
                    pre += bq(Biquad.PEAK, 1000.0, 0.7, 6.0)
                    drive = 45f
                    post += bq(Biquad.LP, 4200.0)
                    post += bq(Biquad.LP, 5400.0)
                    post += bq(Biquad.PEAK, 700.0, 0.9, -4.0)
                    post += bq(Biquad.PEAK, 110.0, 1.0, 5.0)
                    post += bq(Biquad.HP, 70.0)
                    outGain = 0.26f
                }
                StringSynth.Tone.BASS_FINGER, StringSynth.Tone.FRETLESS -> {
                    post += bq(Biquad.LP, 2400.0)
                    post += bq(Biquad.PEAK, 90.0, 0.9, 3.0)
                    post += bq(Biquad.HP, 32.0)
                    drive = 1.3f; outGain = 1.2f
                }
                StringSynth.Tone.BASS_PICK -> {
                    post += bq(Biquad.LP, 4200.0)
                    post += bq(Biquad.PEAK, 1500.0, 0.8, 3.0)
                    post += bq(Biquad.HP, 35.0)
                    drive = 1.6f; outGain = 1.1f
                }
                StringSynth.Tone.SLAP -> {
                    post += bq(Biquad.LP, 7000.0)
                    post += bq(Biquad.PEAK, 600.0, 0.8, -5.0)
                    post += bq(Biquad.PEAK, 3000.0, 0.8, 4.0)
                    post += bq(Biquad.HP, 35.0)
                    drive = 1.4f; outGain = 1.1f
                }
            }
        }

        fun process(buf: FloatArray, n: Int) {
            var peak = 0f
            for (i in 0 until n) {
                var x = buf[i]
                if (body.isNotEmpty()) {
                    var b = x * 0.8f
                    for ((f, g) in body) b += f.process(x) * g
                    x = b
                }
                for (f in pre) x = f.process(x)
                if (drive > 0f) {
                    x = if (drive > 3f) {
                        // Two clipping stages, slightly asymmetric like a tube preamp.
                        val a = tanh(x * drive + 0.08f) - 0.0798f
                        tanh(a * 2.2f) * 0.9f
                    } else tanh(x * drive) / drive * 1.2f
                }
                for (f in post) x = f.process(x)
                x *= outGain
                buf[i] = x
                val ab = abs(x)
                if (ab > peak) peak = ab
            }
            env = max(peak, env * 0.97f)
            quiet = env < 1e-5f
        }
    }

    // ─── A small room ──────────────────────────────────────────────────────

    private class Reverb(sr: Int) {
        private val scale = sr / 44_100.0
        private val combL = intArrayOf(1116, 1188, 1277, 1356).map { Comb((it * scale).toInt()) }
        private val combR = intArrayOf(1139, 1211, 1300, 1379).map { Comb((it * scale).toInt()) }
        private val apL = intArrayOf(556, 441).map { AllPass((it * scale).toInt()) }
        private val apR = intArrayOf(579, 464).map { AllPass((it * scale).toInt()) }
        private val wet = 0.10f

        fun process(l: FloatArray, r: FloatArray, n: Int) {
            for (i in 0 until n) {
                val input = (l[i] + r[i]) * 0.5f * 0.2f
                var ol = 0f
                var or = 0f
                for (c in combL) ol += c.process(input)
                for (c in combR) or += c.process(input)
                for (a in apL) ol = a.process(ol)
                for (a in apR) or = a.process(or)
                l[i] += ol * wet
                r[i] += or * wet
            }
        }

        private class Comb(size: Int) {
            private val buf = FloatArray(size.coerceAtLeast(1))
            private var i = 0
            private var store = 0f
            fun process(x: Float): Float {
                val y = buf[i]
                store = y * 0.75f + store * 0.25f
                buf[i] = x + store * 0.78f
                i = (i + 1) % buf.size
                return y
            }
        }

        private class AllPass(size: Int) {
            private val buf = FloatArray(size.coerceAtLeast(1))
            private var i = 0
            fun process(x: Float): Float {
                val b = buf[i]
                val y = -x + b
                buf[i] = x + b * 0.5f
                i = (i + 1) % buf.size
                return y
            }
        }
    }
}
