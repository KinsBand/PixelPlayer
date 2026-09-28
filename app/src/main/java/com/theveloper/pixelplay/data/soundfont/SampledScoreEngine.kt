package com.theveloper.pixelplay.data.soundfont

import com.theveloper.pixelplay.data.songsterr.Attack
import com.theveloper.pixelplay.data.songsterr.StringEvent
import com.theveloper.pixelplay.data.songsterr.StringPartScore
import com.theveloper.pixelplay.data.songsterr.StringScore
import com.theveloper.pixelplay.data.songsterr.StringSynth
import kotlin.math.roundToInt

/**
 * Something that renders a song's audio on its own score clock (file ms), which a player keeps in
 * step with the MIDI player's clock through [shiftMs].
 */
interface ScorePcmSource {
    /** Score time of the next rendered frame minus its frame time (ms); nudged to follow the clock. */
    var shiftMs: Double
    /** Frames rendered so far. */
    val framesRendered: Long
    /** Fills [out] with interleaved stereo 16-bit frames. Audio thread only. */
    fun render(out: ShortArray, frames: Int)
}

/**
 * Plays a tab with recorded instrument samples from a [SoundFont]:
 *  - every MIDI part of the file (drums, keys, strings, vocals, clicks, count-in) through the
 *    General MIDI presets, and
 *  - guitar and bass parts from their [StringScore]: the same per-string events the modelled
 *    strings use (bends, releases, vibrato, slides, hammer-ons, pull-offs, taps, harmonics, palm
 *    mutes, dead and ghost notes, let ring), played on real guitar / bass samples whose pitch
 *    glides continuously, one voice per string like a real instrument.
 */
class SampledScoreEngine(
    font: SoundFont,
    private val midi: MidiEvents?,
    strings: StringScore?,
    private val sr: Int = 44_100,
) : ScorePcmSource {

    private val synth = SoundFontSynth(font, sr)

    @Volatile override var shiftMs: Double = 0.0
    private var frame = 0L
    override val framesRendered: Long get() = frame

    private var midiIndex = 0
    private val strings: List<StringVoice> = strings?.parts.orEmpty().flatMap { part ->
        part.strings.mapIndexed { s, events -> StringVoice(part, s, events) }
    }

    private val bufL = FloatArray(SoundFontSynth.BLOCK)
    private val bufR = FloatArray(SoundFontSynth.BLOCK)

    private fun scoreMsAt(f: Long): Double = f * 1000.0 / sr + shiftMs

    override fun render(out: ShortArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = minOf(SoundFontSynth.BLOCK, frames - done)
            val t = scoreMsAt(frame)
            val tEnd = scoreMsAt(frame + n)
            dispatchMidi(tEnd)
            for (sv in strings) sv.update(t, tEnd)
            java.util.Arrays.fill(bufL, 0, n, 0f)
            java.util.Arrays.fill(bufR, 0, n, 0f)
            synth.render(bufL, bufR, 0, n)
            for (i in 0 until n) {
                val o = (done + i) * 2
                out[o] = (bufL[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                out[o + 1] = (bufR[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            frame += n
            done += n
        }
    }

    /** Float render for tests and offline use (adds into the arrays). */
    fun renderFloat(outL: FloatArray, outR: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = minOf(SoundFontSynth.BLOCK, frames - done)
            val t = scoreMsAt(frame)
            val tEnd = scoreMsAt(frame + n)
            dispatchMidi(tEnd)
            for (sv in strings) sv.update(t, tEnd)
            synth.render(outL, outR, done, n)
            frame += n
            done += n
        }
    }

    private fun dispatchMidi(untilMs: Double) {
        val m = midi ?: return
        while (midiIndex < m.size && m.timesMs[midiIndex] < untilMs) {
            synth.midi(m.status(midiIndex), m.data1(midiIndex), m.data2(midiIndex))
            midiIndex++
        }
    }

    /** One string of one part: at most one sounding note, like the real string. */
    private inner class StringVoice(val part: StringPartScore, val stringIndex: Int, val events: List<StringEvent>) {
        private var next = 0
        private var note: SoundFontSynth.Note? = null
        private var current: StringEvent? = null
        private var stopped = true
        /** Semitones added to the event pitch for the preset playing it (see [GUITAR_HARMONICS]). */
        private var transpose = 0f
        private val bass = with(StringSynth) { part.tone.isBass }

        fun update(t: Double, tEnd: Double) {
            // Start every event that begins in this block (several in a fast trill).
            while (next < events.size && events[next].startMs < tEnd) {
                val e = events[next++]
                // Already over (the clock jumped past it): skip without sounding.
                if (e.endMs < t - 5.0 && e.endMs.isFinite()) continue
                start(e)
            }
            val e = current ?: return
            val n = note ?: return
            if (!stopped) {
                n.setPitch(e.pitch + transpose + e.offsetAt((t - e.startMs).coerceAtLeast(0.0), e.nominalMs))
                if (e.endMs.isFinite() && t >= e.endMs) {
                    // The fretting hand lifts: a quick, click-free stop.
                    n.damp(if (bass) 90f else 70f)
                    stopped = true
                }
            }
        }

        private fun start(e: StringEvent) {
            val prev = note
            val continues = (e.attack == Attack.TIE || e.attack == Attack.SLIDE) && prev != null && prev.isActive && !stopped
            if (continues) {
                current = e
                return
            }
            val legato = e.attack == Attack.HAMMER || e.attack == Attack.PULL || e.attack == Attack.SLIDE || e.attack == Attack.TIE
            prev?.damp(if (legato) 6f else 12f)

            val tone = part.tone
            var program = programFor(tone)
            var gain = part.gain
            var darken = 0f
            var choke = 0f
            var velocity = e.velocity
            when {
                e.dead -> {
                    // A muted "chk": the string's own sound, choked almost at once.
                    if (!bass && tone != StringSynth.Tone.OVERDRIVE && tone != StringSynth.Tone.DISTORTION) program = MUTED_GUITAR
                    darken = -2400f
                    choke = if (bass) 45f else 30f
                    velocity *= 0.8f
                }
                e.harmonic -> {
                    // Distorted pinch harmonics squeal on the amp; clean ones ring bell-like.
                    if (tone != StringSynth.Tone.OVERDRIVE && tone != StringSynth.Tone.DISTORTION) program = GUITAR_HARMONICS
                    gain *= 1.1f
                }
                e.palmMute -> when {
                    bass -> { darken = -1500f; choke = 220f }
                    tone == StringSynth.Tone.OVERDRIVE || tone == StringSynth.Tone.DISTORTION -> { darken = -1800f; choke = 150f }
                    else -> program = MUTED_GUITAR
                }
            }
            when (e.attack) {
                Attack.HAMMER, Attack.PULL, Attack.SLIDE, Attack.TIE -> velocity *= 0.78f
                Attack.TAP -> velocity *= 0.9f
                else -> Unit
            }
            // General MIDI "Guitar Harmonics" sounds two octaves above the key it's given, while
            // the event already carries the harmonic's real pitch.
            transpose = if (program == GUITAR_HARMONICS) -24f else 0f
            val key = (e.pitch + transpose).roundToInt().coerceIn(0, 127)
            val midiVel = (28 + velocity.coerceIn(0f, 1f) * 99f).roundToInt().coerceIn(1, 127)
            val style = SoundFontSynth.NoteStyle(
                pan = part.pan,
                gain = gain,
                // Legato notes have no pick attack: start past the sample's pick transient.
                skipMs = when (e.attack) { Attack.HAMMER, Attack.PULL, Attack.SLIDE, Attack.TIE -> 28f; Attack.TAP -> 12f; else -> 0f },
                fadeInMs = when (e.attack) { Attack.HAMMER, Attack.PULL, Attack.SLIDE, Attack.TIE -> 5f; Attack.TAP -> 3f; else -> 0f },
                darkenCents = darken,
                chokeMs = choke,
                reverbSend = if (bass) 0.08f else 0.2f,
            )
            val startPitch = e.pitch + transpose + e.offsetAt(0.0, e.nominalMs)
            note = synth.startNote(0, program, key, midiVel, startPitch, style)
            current = e
            stopped = false
        }
    }

    companion object {
        const val MUTED_GUITAR = 28
        const val GUITAR_HARMONICS = 31

        /** General MIDI program for a modelled string tone. */
        fun programFor(tone: StringSynth.Tone): Int = when (tone) {
            StringSynth.Tone.NYLON -> 24
            StringSynth.Tone.STEEL -> 25
            StringSynth.Tone.JAZZ -> 26
            StringSynth.Tone.CLEAN -> 27
            StringSynth.Tone.MUTED -> 28
            StringSynth.Tone.OVERDRIVE -> 29
            StringSynth.Tone.DISTORTION -> 30
            StringSynth.Tone.BASS_FINGER -> 33
            StringSynth.Tone.BASS_PICK -> 34
            StringSynth.Tone.FRETLESS -> 35
            StringSynth.Tone.SLAP -> 36
        }
    }
}
