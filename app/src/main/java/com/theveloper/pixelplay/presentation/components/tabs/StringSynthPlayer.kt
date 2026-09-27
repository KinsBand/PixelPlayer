package com.theveloper.pixelplay.presentation.components.tabs

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Process
import android.util.Log
import com.theveloper.pixelplay.data.songsterr.StringScore
import com.theveloper.pixelplay.data.songsterr.StringSynth
import com.theveloper.pixelplay.data.songsterr.StringSynthEngine
import kotlin.math.abs
import kotlin.math.max

/**
 * Streams [StringSynthEngine] (the modelled guitar / bass strings) to an [AudioTrack] next to
 * the MIDI [MediaPlayer] that plays the drums, keys, clicks and count-in.
 *
 * The MIDI player stays the master clock (the cursor follows it). Every frame [follow] compares
 * what the strings are playing *now* (AudioTrack timestamp) with what the MIDI player is
 * playing now (MediaPlayer timestamp) and nudges the strings' score clock, so both stay
 * together within a few ms. A nudge only moves when future notes start; notes already ringing
 * are untouched, so nothing clicks.
 */
internal class StringSynthPlayer(score: StringScore) {

    private val sr = StringSynth.SAMPLE_RATE
    private val engine = StringSynthEngine(score, sr)
    private val track: AudioTrack
    private val chunkFrames = 256
    private val chunk = ShortArray(chunkFrames * 2)
    @Volatile private var running = false
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
        track.play()
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (running) {
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

    fun release() {
        running = false
        runCatching { thread?.join(300) }
        thread = null
        runCatching { track.pause() }
        runCatching { track.flush() }
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
        val settle = 250_000_000L // let a nudge reach the speaker before judging again
        if (samples >= 4 && abs(errEma) > threshold && nowNanos - lastNudgeAt > settle) {
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
            runCatching { StringSynthPlayer(score) }
                .onFailure { Log.w(TAG, "String synth unavailable: ${it.message}") }
                .getOrNull()
    }
}
