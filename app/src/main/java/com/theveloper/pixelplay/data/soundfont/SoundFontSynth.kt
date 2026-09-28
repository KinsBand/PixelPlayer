package com.theveloper.pixelplay.data.soundfont

import com.theveloper.pixelplay.data.soundfont.SoundFont.Companion as G
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A General MIDI sample player for a [SoundFont]: 16 MIDI channels plus direct notes whose pitch
 * can glide continuously (used by the sampled guitar / bass strings and the notation demos).
 *
 * Per voice: 4-point interpolated sample playback with the SoundFont loop, the volume envelope
 * (delay / attack / hold / decay / sustain / release), the modulation envelope and both LFOs
 * (to pitch, filter and volume), a resonant low-pass filter, pan, exclusive classes (an open
 * hi-hat is choked by a closed one) and a reverb send. The mix goes through a stereo reverb and a
 * soft peak limiter, so dense parts never clip.
 *
 * Call [render] from one thread only (the audio thread); MIDI and note calls must be made on that
 * same thread, between renders.
 */
class SoundFontSynth(
    val font: SoundFont,
    val sampleRate: Int = 44_100,
    maxVoices: Int = 96,
) {
    private val sr = sampleRate.toFloat()

    class Channel(val index: Int) {
        var program = 0
        var bank = if (index == 9) SoundFont.DRUM_BANK else 0
        var volume = 100
        var expression = 127
        var pan = 64
        var reverb = 40
        var bendSemis = 0f
        var bendRange = 2f
        var sustain = false
        internal var rpnMsb = 127
        internal var rpnLsb = 127
        fun reset() {
            program = 0; bank = if (index == 9) SoundFont.DRUM_BANK else 0
            volume = 100; expression = 127; pan = 64; reverb = 40
            bendSemis = 0f; bendRange = 2f; sustain = false; rpnMsb = 127; rpnLsb = 127
        }
    }

    val channels = Array(16) { Channel(it) }
    private val voices = Array(maxVoices) { Voice() }
    private val scratch = ArrayList<SoundFont.Region>(8)
    private var serialCounter = 0L

    /** Overall level before the limiter. */
    var masterGain = 1.0f
    var reverbLevel = 0.9f

    private val reverb = Freeverb(sampleRate)
    private val blockL = FloatArray(BLOCK)
    private val blockR = FloatArray(BLOCK)
    private val sendL = FloatArray(BLOCK)
    private val sendR = FloatArray(BLOCK)
    private var limGain = 1f

    // ── MIDI ──────────────────────────────────────────────────────────────────

    fun midi(status: Int, d1: Int, d2: Int) {
        val ch = status and 0x0F
        when (status and 0xF0) {
            0x80 -> noteOff(ch, d1)
            0x90 -> if (d2 == 0) noteOff(ch, d1) else noteOn(ch, d1, d2)
            0xB0 -> controlChange(ch, d1, d2)
            0xC0 -> programChange(ch, d1)
            0xE0 -> pitchBend(ch, (d2 shl 7) or d1)
        }
    }

    fun programChange(ch: Int, program: Int) {
        channels[ch and 15].program = program and 127
    }

    fun controlChange(ch: Int, cc: Int, value: Int) {
        val c = channels[ch and 15]
        when (cc) {
            0 -> if (c.index != 9) c.bank = value
            7 -> c.volume = value
            10 -> c.pan = value
            11 -> c.expression = value
            64 -> {
                c.sustain = value >= 64
                if (!c.sustain) for (v in voices) if (v.active && v.channel == c.index && v.sustained) { v.sustained = false; v.release() }
            }
            91 -> c.reverb = value
            101 -> c.rpnMsb = value
            100 -> c.rpnLsb = value
            6 -> if (c.rpnMsb == 0 && c.rpnLsb == 0) c.bendRange = value.toFloat().coerceIn(0f, 24f)
            120 -> for (v in voices) if (v.active && v.channel == c.index) v.kill()
            121 -> { val keep = c.program; c.reset(); c.program = keep }
            123 -> for (v in voices) if (v.active && v.channel == c.index) v.release()
        }
    }

    fun pitchBend(ch: Int, value14: Int) {
        val c = channels[ch and 15]
        c.bendSemis = (value14 - 8192) / 8192f * c.bendRange
    }

    fun noteOn(ch: Int, key: Int, vel: Int) {
        val c = channels[ch and 15]
        val preset = font.preset(c.bank, c.program) ?: return
        startVoices(preset, key, vel.coerceIn(1, 127), key.toFloat(), c, null)
    }

    fun noteOff(ch: Int, key: Int) {
        val c = channels[ch and 15]
        for (v in voices) {
            if (!v.active || v.channel != c.index || v.key != key || v.released) continue
            if (c.sustain) v.sustained = true else v.release()
        }
    }

    fun allSoundOff() {
        for (v in voices) if (v.active) v.kill()
        for (c in channels) c.reset()
    }

    // ── Direct notes (sampled strings, notation demos) ─────────────────────────

    /** Extra shaping for a direct note. */
    class NoteStyle(
        /** Stereo position −1 … 1. */
        val pan: Float = 0f,
        val gain: Float = 1f,
        /** Skip this much of the sample's start (a legato note has no pick attack). */
        val skipMs: Float = 0f,
        /** Lowers the filter (palm mute, dead note); cents, ≤ 0. */
        val darkenCents: Float = 0f,
        /** Extra decay time constant in ms (palm mute, dead note); 0 = the sample's own. */
        val chokeMs: Float = 0f,
        /** Fades the attack in over this many ms (legato, soft onsets); 0 = the sample's own. */
        val fadeInMs: Float = 0f,
        val reverbSend: Float = 0.25f,
    )

    /** A group of voices started together (a stereo pair, layered zones). */
    inner class Note internal constructor() {
        internal val idx = IntArray(6)
        internal val serial = LongArray(6)
        internal var count = 0

        private inline fun each(block: (Voice) -> Unit) {
            for (i in 0 until count) {
                val v = voices[idx[i]]
                if (v.active && v.serial == serial[i]) block(v)
            }
        }

        val isActive: Boolean
            get() {
                for (i in 0 until count) {
                    val v = voices[idx[i]]
                    if (v.active && v.serial == serial[i]) return true
                }
                return false
            }

        /** Sounding pitch in (fractional) MIDI notes. */
        fun setPitch(pitch: Float) = each { it.pitch = pitch }

        fun release() = each { it.release() }

        /** Stops quickly (a new pick on the same string damps the old note). */
        fun damp(ms: Float) = each { it.releaseIn(ms) }
    }

    fun startNote(bank: Int, program: Int, key: Int, vel: Int, pitch: Float, style: NoteStyle): Note? {
        val preset = font.preset(bank, program) ?: return null
        return startVoices(preset, key.coerceIn(0, 127), vel.coerceIn(1, 127), pitch, null, style)
    }

    private fun startVoices(preset: SoundFont.Preset, key: Int, vel: Int, pitch: Float, ch: Channel?, style: NoteStyle?): Note? {
        font.regionsFor(preset, key, vel, scratch)
        if (scratch.isEmpty()) return null
        val note = Note()
        for (r in scratch) {
            val excl = r.gen[G.EXCLUSIVE_CLASS]
            if (excl != 0) {
                for (v in voices) if (v.active && v.exclusiveClass == excl && v.channel == (ch?.index ?: -1) && v.presetId == preset.hashCode()) v.releaseIn(8f)
            }
            val vi = allocate()
            val v = voices[vi]
            v.start(r, key, vel, pitch, ch, style, ++serialCounter, preset.hashCode())
            if (note.count < note.idx.size) {
                note.idx[note.count] = vi
                note.serial[note.count] = v.serial
                note.count++
            }
        }
        return note
    }

    private fun allocate(): Int {
        var best = -1
        for (i in voices.indices) if (!voices[i].active) return i
        // Steal: the quietest released voice, else the oldest.
        var bestScore = Float.MAX_VALUE
        for (i in voices.indices) {
            val v = voices[i]
            val score = (if (v.released) 0f else 10f) + v.amp + v.serial.toFloat() * 1e-9f
            if (score < bestScore) { bestScore = score; best = i }
        }
        voices[best].kill()
        return best
    }

    val activeVoices: Int get() = voices.count { it.active }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /** Adds [frames] stereo frames into [outL] / [outR] starting at [offset] (the arrays are not cleared). */
    fun render(outL: FloatArray, outR: FloatArray, offset: Int, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = min(BLOCK, frames - done)
            java.util.Arrays.fill(blockL, 0, n, 0f)
            java.util.Arrays.fill(blockR, 0, n, 0f)
            java.util.Arrays.fill(sendL, 0, n, 0f)
            java.util.Arrays.fill(sendR, 0, n, 0f)
            for (v in voices) if (v.active) v.render(n)
            reverb.process(sendL, sendR, blockL, blockR, n, reverbLevel)
            for (i in 0 until n) {
                var l = blockL[i] * masterGain
                var r = blockR[i] * masterGain
                // Soft peak limiter: instant attack, ~120 ms release, ceiling −1 dBFS.
                val peak = max(abs(l), abs(r))
                val target = if (peak * limGain > CEILING) CEILING / peak else 1f
                limGain = if (target < limGain) target else limGain + (target - limGain) * LIM_RELEASE
                l *= limGain
                r *= limGain
                outL[offset + done + i] += l
                outR[offset + done + i] += r
            }
            done += n
        }
    }

    // ── Voice ─────────────────────────────────────────────────────────────────

    internal inner class Voice {
        var active = false
        var serial = 0L
        var channel = -1
        var key = 0
        var presetId = 0
        var exclusiveClass = 0
        var released = false
        var sustained = false
        var pitch = 60f
        var amp = 0f

        private var chan: Channel? = null
        private lateinit var gen: IntArray
        private var vel = 100
        private var pos = 0.0
        private var start = 0
        private var end = 0
        private var loopStart = 0
        private var loopEnd = 0
        private var loopMode = 0
        private var rootKey = 60
        private var scaleTuning = 1f
        private var tuneCents = 0f
        private var rateRatio = 1.0
        private var baseGain = 1f
        private var panL = 0.707f
        private var panR = 0.707f
        private var sendGain = 0.2f
        private var styleGain = 1f

        // Volume envelope
        private var vStage = 0
        private var vTime = 0f
        private var vDelay = 0f; private var vAttack = 0f; private var vHold = 0f; private var vDecay = 0f
        private var vSustainDb = 0f; private var vRelease = 0f
        private var vDb = 0f // attenuation in the decay / sustain / release stages
        private var releaseStartDb = 0f
        private var releaseLen = 0f
        private var fadeIn = 0f
        private var chokeCoef = 1f
        private var chokeAmp = 1f

        // Modulation envelope (0..1)
        private var mStage = 0
        private var mTime = 0f
        private var mDelay = 0f; private var mAttack = 0f; private var mHold = 0f; private var mDecay = 0f
        private var mSustain = 1f; private var mRelease = 0f
        private var mVal = 0f; private var mRelStart = 0f

        // LFOs
        private var modLfoPhase = 0f; private var modLfoInc = 0f; private var modLfoDelay = 0f
        private var vibLfoPhase = 0f; private var vibLfoInc = 0f; private var vibLfoDelay = 0f
        private var lfoTime = 0f

        // Filter
        private var filterOn = false
        private var fcBase = 13500f
        private var qLin = 0.707f
        private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
        private var z1 = 0f; private var z2 = 0f
        private var lastFc = -1f

        fun start(r: SoundFont.Region, k: Int, velocity: Int, p: Float, ch: Channel?, style: NoteStyle?, s: Long, preset: Int) {
            active = true; released = false; sustained = false
            serial = s; presetId = preset
            gen = r.gen; chan = ch; channel = ch?.index ?: -1
            key = k; vel = velocity; pitch = p
            val sh = r.sample
            start = sh.start + gen[G.START_OFFSET] + gen[G.START_COARSE] * 32768
            end = sh.end + gen[G.END_OFFSET] + gen[G.END_COARSE] * 32768
            loopStart = sh.loopStart + gen[G.START_LOOP_OFFSET] + gen[G.START_LOOP_COARSE] * 32768
            loopEnd = sh.loopEnd + gen[G.END_LOOP_OFFSET] + gen[G.END_LOOP_COARSE] * 32768
            val limit = font.sampleCount - 1
            start = start.coerceIn(0, limit); end = end.coerceIn(start + 1, limit)
            loopStart = loopStart.coerceIn(start, end); loopEnd = loopEnd.coerceIn(loopStart, end)
            loopMode = gen[G.SAMPLE_MODES] and 3
            if (loopMode == 2 || loopEnd - loopStart < 4) loopMode = 0
            rootKey = if (gen[G.ROOT_KEY] in 0..127) gen[G.ROOT_KEY] else sh.originalPitch
            if (gen[G.KEYNUM] in 0..127) { key = gen[G.KEYNUM] }
            scaleTuning = gen[G.SCALE_TUNING] / 100f
            tuneCents = gen[G.COARSE_TUNE] * 100f + gen[G.FINE_TUNE] + sh.pitchCorrection
            rateRatio = sh.sampleRate.toDouble() / sampleRate
            exclusiveClass = gen[G.EXCLUSIVE_CLASS]
            val v = if (gen[G.VELOCITY] in 1..127) gen[G.VELOCITY] else vel

            // Level: initial attenuation (cB) and the default velocity curve (≈ 40·log10). Like
            // FluidSynth (and the EMU hardware SoundFonts were made on), attenuation counts at 0.4×:
            // GeneralUser GS and most banks are voiced for that.
            val attenCb = gen[G.ATTENUATION].coerceIn(0, 1440) * 0.4f
            val velGain = (v / 127f).let { it * it }
            baseGain = 10f.pow(-attenCb / 200f) * velGain
            styleGain = style?.gain ?: 1f
            var panPos = gen[G.PAN].coerceIn(-500, 500) / 500f
            style?.let { panPos = (panPos + it.pan).coerceIn(-1f, 1f) }
            ch?.let { panPos = (panPos + (it.pan - 64) / 64f).coerceIn(-1f, 1f) }
            val angle = (panPos + 1f) * 0.25f * PI.toFloat()
            panL = cos(angle); panR = sin(angle)
            val chanSend = (ch?.reverb ?: 40) / 127f
            sendGain = max(gen[G.REVERB_SEND].coerceIn(0, 1000) / 1000f, style?.reverbSend ?: chanSend * 0.6f)

            // Envelopes (timecents → samples).
            fun tc(g: Int, keyScale: Int = 0): Float {
                val t = gen[g] + gen.getOrElse(keyScale) { 0 }.let { if (keyScale == 0) 0 else it * (60 - key) }
                return (2.0.pow(t.coerceIn(-12000, 8000) / 1200.0) * sampleRate).toFloat()
            }
            vDelay = tc(G.DELAY_VOL_ENV); vAttack = tc(G.ATTACK_VOL_ENV)
            vHold = tc(G.HOLD_VOL_ENV, G.KEY_TO_VOL_ENV_HOLD); vDecay = tc(G.DECAY_VOL_ENV, G.KEY_TO_VOL_ENV_DECAY)
            vSustainDb = gen[G.SUSTAIN_VOL_ENV].coerceIn(0, 1440) / 10f
            vRelease = max(tc(G.RELEASE_VOL_ENV), 0.006f * sampleRate)
            vStage = STAGE_DELAY; vTime = 0f; vDb = 0f; amp = 0f
            fadeIn = (style?.fadeInMs ?: 0f) * sampleRate / 1000f
            chokeCoef = style?.chokeMs?.takeIf { it > 0f }?.let { exp(-BLOCK / (it * sampleRate / 1000f)) } ?: 1f
            chokeAmp = 1f

            mDelay = tc(G.DELAY_MOD_ENV); mAttack = tc(G.ATTACK_MOD_ENV)
            mHold = tc(G.HOLD_MOD_ENV, G.KEY_TO_MOD_ENV_HOLD); mDecay = tc(G.DECAY_MOD_ENV, G.KEY_TO_MOD_ENV_DECAY)
            mSustain = 1f - gen[G.SUSTAIN_MOD_ENV].coerceIn(0, 1000) / 1000f
            mRelease = max(tc(G.RELEASE_MOD_ENV), 0.006f * sampleRate)
            mStage = STAGE_DELAY; mTime = 0f; mVal = 0f

            fun lfoInc(g: Int) = (8.176 * 2.0.pow(gen[g].coerceIn(-16000, 4500) / 1200.0) / sampleRate).toFloat()
            modLfoInc = lfoInc(G.FREQ_MOD_LFO); vibLfoInc = lfoInc(G.FREQ_VIB_LFO)
            modLfoDelay = tc(G.DELAY_MOD_LFO); vibLfoDelay = tc(G.DELAY_VIB_LFO)
            modLfoPhase = 0f; vibLfoPhase = 0f; lfoTime = 0f

            // Filter: soft notes are darker (default velocity → cutoff), plus any style darkening.
            fcBase = gen[G.FILTER_FC].coerceIn(1500, 13500).toFloat() - 1200f * (1f - v / 127f) + (style?.darkenCents ?: 0f)
            val qDb = gen[G.FILTER_Q].coerceIn(0, 960) / 10f
            qLin = max(0.707f, 10f.pow(qDb / 20f) * 0.707f)
            filterOn = fcBase < 13400f || gen[G.MOD_ENV_TO_FILTER] != 0 || gen[G.MOD_LFO_TO_FILTER] != 0
            z1 = 0f; z2 = 0f; lastFc = -1f

            pos = start.toDouble()
            style?.skipMs?.takeIf { it > 0f }?.let { skip ->
                val jump = skip / 1000.0 * sh.sampleRate
                val limitPos = if (loopMode != 0) loopStart.toDouble() else end - 2.0
                pos = min(start + jump, max(start.toDouble(), limitPos))
            }
        }

        fun release() {
            if (released) return
            released = true
            beginRelease(vRelease)
        }

        fun releaseIn(ms: Float) {
            released = true
            beginRelease(max(1f, ms) * sampleRate / 1000f)
        }

        private fun beginRelease(len: Float) {
            releaseStartDb = when (vStage) {
                STAGE_DELAY -> 100f
                STAGE_ATTACK -> -20f * log10(max(amp, 1e-5f))
                else -> vDb
            }
            releaseLen = len
            vStage = STAGE_RELEASE; vTime = 0f
            mRelStart = mVal; mStage = STAGE_RELEASE; mTime = 0f
        }

        fun kill() { active = false; amp = 0f }

        /** Volume envelope amplitude after advancing [n] frames. */
        private fun volEnv(n: Int): Float {
            vTime += n
            while (true) {
                when (vStage) {
                    STAGE_DELAY -> if (vTime >= vDelay) { vTime -= vDelay; vStage = STAGE_ATTACK } else return 0f
                    STAGE_ATTACK -> if (vTime >= vAttack) { vTime -= vAttack; vStage = STAGE_HOLD } else return vTime / max(vAttack, 1f)
                    STAGE_HOLD -> if (vTime >= vHold) { vTime -= vHold; vStage = STAGE_DECAY } else { vDb = 0f; return 1f }
                    STAGE_DECAY -> {
                        vDb = 100f * vTime / max(vDecay, 1f)
                        if (vDb >= vSustainDb) { vDb = vSustainDb; vStage = STAGE_SUSTAIN }
                        return dbToAmp(vDb)
                    }
                    STAGE_SUSTAIN -> { vDb = vSustainDb; return dbToAmp(vDb) }
                    else -> {
                        val db = releaseStartDb + 100f * vTime / max(releaseLen, 1f)
                        if (db >= 96f) { active = false; return 0f }
                        vDb = db
                        return dbToAmp(db)
                    }
                }
            }
        }

        private fun modEnv(n: Int): Float {
            mTime += n
            while (true) {
                when (mStage) {
                    STAGE_DELAY -> if (mTime >= mDelay) { mTime -= mDelay; mStage = STAGE_ATTACK } else { mVal = 0f; return 0f }
                    STAGE_ATTACK -> if (mTime >= mAttack) { mTime -= mAttack; mStage = STAGE_HOLD } else { mVal = mTime / max(mAttack, 1f); return mVal }
                    STAGE_HOLD -> if (mTime >= mHold) { mTime -= mHold; mStage = STAGE_DECAY } else { mVal = 1f; return 1f }
                    STAGE_DECAY -> {
                        mVal = 1f - (1f - mSustain) * min(1f, mTime / max(mDecay, 1f))
                        if (mTime >= mDecay) mStage = STAGE_SUSTAIN
                        return mVal
                    }
                    STAGE_SUSTAIN -> { mVal = mSustain; return mVal }
                    else -> { mVal = mRelStart * max(0f, 1f - mTime / max(mRelease, 1f)); return mVal }
                }
            }
        }

        private fun tri(phase: Float): Float = if (phase < 0.5f) 4f * phase - 1f else 3f - 4f * phase

        fun render(n: Int) {
            val env0 = amp
            var env = volEnv(n)
            if (!active) { amp = 0f; return }
            if (fadeIn > 0f) env *= min(1f, (vStage.let { if (it == STAGE_DELAY) 0f else lfoTime + n } / fadeIn))
            if (chokeCoef < 1f) { chokeAmp *= chokeCoef; env *= chokeAmp; if (chokeAmp < 1e-4f) { active = false; amp = 0f; return } }
            val menv = modEnv(n)
            lfoTime += n
            var modLfo = 0f
            var vibLfo = 0f
            if (lfoTime > modLfoDelay) { modLfoPhase = (modLfoPhase + modLfoInc * n) % 1f; modLfo = tri(modLfoPhase) }
            if (lfoTime > vibLfoDelay) { vibLfoPhase = (vibLfoPhase + vibLfoInc * n) % 1f; vibLfo = tri(vibLfoPhase) }

            val c = chan
            val bend = c?.bendSemis ?: 0f
            val cents = scaleTuning * (pitch + bend - rootKey) * 100f + tuneCents +
                menv * gen[G.MOD_ENV_TO_PITCH] + modLfo * gen[G.MOD_LFO_TO_PITCH] + vibLfo * gen[G.VIB_LFO_TO_PITCH]
            val inc = rateRatio * 2.0.pow(cents / 1200.0)

            var chanGain = 1f
            if (c != null) {
                val vol = c.volume / 127f
                val expr = c.expression / 127f
                chanGain = vol * vol * expr * expr
            }
            val lfoVol = if (gen[G.MOD_LFO_TO_VOLUME] != 0) dbToAmp(-modLfo * gen[G.MOD_LFO_TO_VOLUME] / 10f) else 1f
            val target = env * baseGain * styleGain * chanGain * lfoVol
            amp = target

            if (filterOn) {
                val fc = fcBase + menv * gen[G.MOD_ENV_TO_FILTER] + modLfo * gen[G.MOD_LFO_TO_FILTER]
                if (abs(fc - lastFc) > 2f) { setFilter(fc); lastFc = fc }
            }

            // Ramp the gain across the block (no zipper noise).
            val g0 = if (env0 == 0f && vStage != STAGE_RELEASE) 0f else env0
            var g = g0
            val gStep = (target - g0) / n
            val lg = panL; val rg = panR
            val send = sendGain
            val data = font.samples
            val lastIndex = font.sampleCount - 1
            for (i in 0 until n) {
                val ip = pos.toInt()
                val frac = (pos - ip).toFloat()
                val xm1 = data.get((ip - 1).coerceIn(0, lastIndex)).toFloat()
                val x0 = data.get(ip.coerceIn(0, lastIndex)).toFloat()
                var i1 = ip + 1
                var i2 = ip + 2
                if (loopMode != 0 && !(loopMode == 3 && released)) {
                    if (i1 >= loopEnd) i1 -= loopEnd - loopStart
                    if (i2 >= loopEnd) i2 -= loopEnd - loopStart
                }
                val x1 = data.get(i1.coerceIn(0, lastIndex)).toFloat()
                val x2 = data.get(i2.coerceIn(0, lastIndex)).toFloat()
                // 4-point Hermite interpolation.
                val c1 = 0.5f * (x1 - xm1)
                val c2 = xm1 - 2.5f * x0 + 2f * x1 - 0.5f * x2
                val c3 = 0.5f * (x2 - xm1) + 1.5f * (x0 - x1)
                var s = ((c3 * frac + c2) * frac + c1) * frac + x0
                s *= (1f / 32768f)
                if (filterOn) {
                    val y = b0 * s + z1
                    z1 = b1 * s - a1 * y + z2
                    z2 = b2 * s - a2 * y
                    s = y
                }
                g += gStep
                val out = s * g
                blockL[i] += out * lg
                blockR[i] += out * rg
                sendL[i] += out * lg * send
                sendR[i] += out * rg * send

                pos += inc
                if (loopMode == 1 || (loopMode == 3 && !released)) {
                    if (pos >= loopEnd) pos -= (loopEnd - loopStart)
                } else if (pos >= end - 1) {
                    active = false
                    amp = 0f
                    return
                }
            }
        }

        private fun setFilter(fcCents: Float) {
            val hz = (8.176 * 2.0.pow(fcCents.coerceIn(1500f, 13500f) / 1200.0)).toFloat().coerceAtMost(sr * 0.45f)
            val w = 2f * PI.toFloat() * hz / sr
            val cw = cos(w)
            val alpha = sin(w) / (2f * qLin)
            val a0 = 1f + alpha
            b0 = (1f - cw) / 2f / a0
            b1 = (1f - cw) / a0
            b2 = b0
            a1 = -2f * cw / a0
            a2 = (1f - alpha) / a0
        }
    }

    private fun dbToAmp(db: Float): Float = if (db >= 96f) 0f else 10f.pow(-db / 20f)

    companion object {
        const val BLOCK = 32
        private const val STAGE_DELAY = 0
        private const val STAGE_ATTACK = 1
        private const val STAGE_HOLD = 2
        private const val STAGE_DECAY = 3
        private const val STAGE_SUSTAIN = 4
        private const val STAGE_RELEASE = 5
        private const val CEILING = 0.89f
        private const val LIM_RELEASE = 0.0002f
    }
}

/** Freeverb (Jezar's public-domain design), stereo, a light room. */
internal class Freeverb(sampleRate: Int) {
    private val scale = sampleRate / 44_100f
    private val combL = COMBS.map { Comb((it * scale).toInt()) }
    private val combR = COMBS.map { Comb(((it + SPREAD) * scale).toInt()) }
    private val apL = ALLPASSES.map { Allpass((it * scale).toInt()) }
    private val apR = ALLPASSES.map { Allpass(((it + SPREAD) * scale).toInt()) }

    fun process(inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray, n: Int, level: Float) {
        if (level <= 0f) return
        for (i in 0 until n) {
            val input = (inL[i] + inR[i]) * 0.015f
            var l = 0f
            var r = 0f
            for (c in combL) l += c.process(input)
            for (c in combR) r += c.process(input)
            for (a in apL) l = a.process(l)
            for (a in apR) r = a.process(r)
            val wet = level * 0.5f
            outL[i] += (l * (1f + WIDTH) / 2f + r * (1f - WIDTH) / 2f) * wet
            outR[i] += (r * (1f + WIDTH) / 2f + l * (1f - WIDTH) / 2f) * wet
        }
    }

    private class Comb(size: Int) {
        private val buf = FloatArray(max(1, size))
        private var idx = 0
        private var store = 0f
        fun process(x: Float): Float {
            val out = buf[idx]
            store = out * (1f - DAMP) + store * DAMP
            buf[idx] = x + store * FEEDBACK
            if (++idx >= buf.size) idx = 0
            return out
        }
    }

    private class Allpass(size: Int) {
        private val buf = FloatArray(max(1, size))
        private var idx = 0
        fun process(x: Float): Float {
            val b = buf[idx]
            val out = -x + b
            buf[idx] = x + b * 0.5f
            if (++idx >= buf.size) idx = 0
            return out
        }
    }

    companion object {
        private val COMBS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASSES = intArrayOf(556, 441, 341, 225)
        private const val SPREAD = 23
        private const val FEEDBACK = 0.80f // room size
        private const val DAMP = 0.35f
        private const val WIDTH = 0.8f
    }
}

@Suppress("unused")
private fun rms(a: FloatArray, n: Int): Float { var s = 0.0; for (i in 0 until n) s += a[i] * a[i]; return sqrt(s / max(1, n)).toFloat() }
