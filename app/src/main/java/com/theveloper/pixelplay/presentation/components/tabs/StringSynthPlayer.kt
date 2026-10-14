package com.theveloper.pixelplay.presentation.components.tabs

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Process
import android.util.Log
import com.theveloper.pixelplay.data.soundfont.MidiEvents
import com.theveloper.pixelplay.data.soundfont.SampledScoreEngine
import com.theveloper.pixelplay.data.soundfont.ScorePcmSource
import com.theveloper.pixelplay.data.soundfont.SoundFont
import com.theveloper.pixelplay.data.songsterr.StringScore
import com.theveloper.pixelplay.data.songsterr.StringSynth
import com.theveloper.pixelplay.data.songsterr.StringSynthEngine
import kotlin.math.abs
import kotlin.math.max

/**
 * Streams a [ScorePcmSource] to an [AudioTrack] next to the MIDI [MediaPlayer]:
 *  - [StringSynthEngine]: the modelled guitar / bass strings, while the MIDI player plays the
 *    drums, keys, clicks and count-in; or
 *  - [SampledScoreEngine]: every part on recorded SoundFont instruments (the MIDI player is then
 *    muted and only serves as the clock).
 *
 * The MIDI player stays the master clock (the cursor follows it). Every frame [follow] compares
 * what the strings are playing *now* (AudioTrack timestamp) with what the MIDI player is
 * playing now (MediaPlayer timestamp) and nudges the strings' score clock, so both stay
 * together within a few ms. A nudge only moves when future notes start; notes already ringing
 * are untouched, so nothing clicks.
 */
internal class StringSynthPlayer(private val engine: ScorePcmSource) {

    private val sr = StringSynth.SAMPLE_RATE
    private val track: AudioTrack
    private val chunkFrames = 256
    private val chunk = ShortArray(chunkFrames * 2)
    @Volatile private var running = false
    @Volatile private var paused = false
    private var startedAt = 0L
    private var thread: Thread? = null

    /** Shift changes as (frame from which it applies, shift ms), oldest first. */
    private val shifts = ArrayList<Pair<Long, Double>>()
    @Volatile private var pendingNudgeMs = 0.0

    private val ts = AudioTimestamp()
    private var errEma = 0.0
    private var lastNudgeAt = 0L
    private var samples = 0

    init {
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        // ~120 ms: enough headroom for a busy phone, small enough that nudges land quickly.
        val bytes = max(minBuf * 2, sr * 4 * 120 / 1000)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        synchronized(shifts) { shifts += 0L to 0.0 }
    }

    /** Fills the buffer before [start] so sound begins at once. */
    fun prime() {
        val capacity = track.bufferSizeInFrames
        var written = 0
        while (written + chunkFrames <= capacity) {
            engine.render(chunk, chunkFrames)
            val r = track.write(chunk, 0, chunk.size, AudioTrack.WRITE_NON_BLOCKING)
            if (r <= 0) break
            written += r / 2
        }
    }

    fun start() {
        if (running) return
        running = true
        startedAt = System.nanoTime()
        track.play()
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (running) {
                if (paused) {
                    // Held: nothing to render until resume (checked every few ms).
                    runCatching { Thread.sleep(4) }
                    continue
                }
                val nudge = pendingNudgeMs
                if (nudge != 0.0) {
                    pendingNudgeMs = 0.0
                    engine.shiftMs += nudge
                    synchronized(shifts) {
                        shifts += engine.framesRendered to engine.shiftMs
                        if (shifts.size > 16) shifts.removeAt(0)
                    }
                }
                engine.render(chunk, chunkFrames)
                var off = 0
                while (running && off < chunk.size) {
                    val r = track.write(chunk, off, chunk.size - off)
                    if (r < 0) {
                        running = false
                        break
                    }
                    off += r
                }
            }
        }, "StringSynth").apply { start() }
    }

    /** Holds the strings where they are (buffer, ringing notes and clock are kept). */
    fun pause() {
        if (!running || paused) return
        paused = true
        runCatching { track.pause() }
    }

    /** Carries on from exactly where [pause] stopped. */
    fun resume() {
        if (!running || !paused) return
        runCatching { track.play() }
        // Re-judge the clock from scratch: the MIDI player restarts with its own latency.
        samples = 0
        errEma = 0.0
        startedAt = System.nanoTime()
        paused = false
    }

    fun release() {
        running = false
        paused = false
        // Pause + flush first so a write blocked on a full buffer returns at once.
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { thread?.join(300) }
        thread = null
        runCatching { track.release() }
    }

    /** Score ms the listener hears right now, from the AudioTrack's output timestamp. */
    private fun audibleMs(nowNanos: Long): Double? {
        if (!runCatching { track.getTimestamp(ts) }.getOrDefault(false)) return null
        if (ts.framePosition <= 0) return null
        val frame = ts.framePosition + (nowNanos - ts.nanoTime) * sr / 1_000_000_000L
        val shift = synchronized(shifts) { shifts.lastOrNull { it.first <= frame }?.second ?: shifts.first().second }
        return frame * 1000.0 / sr + shift
    }

    /**
     * Keeps the strings on the MIDI player's clock. [midiMs] = file ms playing now; [precise]
     * = it came from the player's output timestamp (tighter threshold).
     */
    fun follow(midiMs: Double, precise: Boolean, nowNanos: Long) {
        val heard = audibleMs(nowNanos) ?: return
        val err = heard - midiMs
        // A huge error means a restart the other side hasn't seen yet: wait.
        if (abs(err) > 2_000) return
        errEma = if (samples == 0) err else errEma * 0.8 + err * 0.2
        samples++
        val threshold = if (precise) 8.0 else 30.0
        // Just after a start / resume the two players' start latencies differ: correct quickly
        // so the first notes don't flam, then settle into gentle tracking.
        val warm = nowNanos - startedAt < 1_000_000_000L
        val settle = if (warm) 80_000_000L else 250_000_000L // let a nudge reach the speaker first
        val minSamples = if (warm) 2 else 4
        if (samples >= minSamples && abs(errEma) > threshold && nowNanos - lastNudgeAt > settle) {
            pendingNudgeMs -= errEma
            lastNudgeAt = nowNanos
            samples = 0
            errEma = 0.0
        }
    }

    companion object {
        private const val TAG = "StringSynthPlayer"

        /** File ms the MIDI player outputs now, and whether it's from its audio timestamp. */
        fun midiNowMs(mp: MediaPlayer, nowNanos: Long): Pair<Double, Boolean>? {
            val stamp = runCatching { mp.timestamp }.getOrNull()
            if (stamp != null && stamp.mediaClockRate > 0f) {
                val anchor = if (Build.VERSION.SDK_INT >= 29) stamp.anchorSystemNanoTime else {
                    @Suppress("DEPRECATION")
                    stamp.anchorSytemNanoTime
                }
                val ms = stamp.anchorMediaTimeUs / 1000.0 + (nowNanos - anchor) / 1_000_000.0 * stamp.mediaClockRate
                return ms to true
            }
            return runCatching { mp.currentPosition.toDouble() to false }.getOrNull()
        }

        fun create(score: StringScore): StringSynthPlayer? =
            runCatching { StringSynthPlayer(StringSynthEngine(score, StringSynth.SAMPLE_RATE)) }
                .onFailure { Log.w(TAG, "String synth unavailable: ${it.message}") }
                .getOrNull()

        /** Every part on recorded instruments: the MIDI file's parts plus guitar / bass [strings]. */
        fun createSampled(
            font: SoundFont,
            midiFile: ByteArray,
            strings: StringScore?,
            style: com.theveloper.pixelplay.data.soundfont.SoundStyle = com.theveloper.pixelplay.data.soundfont.SoundStyle.current,
            /** Speed the MIDI file was built at (TabMidi.Options.speed). */
            speed: Float = 1f,
        ): StringSynthPlayer? =
            runCatching {
                StringSynthPlayer(SampledScoreEngine(font, MidiEvents.parse(midiFile), strings, StringSynth.SAMPLE_RATE, style, speed))
            }
                .onFailure { Log.w(TAG, "Sampled synth unavailable: ${it.message}") }
                .getOrNull()
    }
}
