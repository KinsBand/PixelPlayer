package com.theveloper.pixelplay.data.drumkit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** Where the sound is going (latency is kept per output). */
enum class AudioRoute(val label: String) {
    SPEAKER("Phone speaker"), WIRED("Wired headphones"), USB("USB audio"), BLUETOOTH("Bluetooth");

    companion object {
        fun current(context: Context): AudioRoute {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return SPEAKER
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return SPEAKER
            val types = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
            val bt = buildSet {
                add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
                if (Build.VERSION.SDK_INT >= 31) { add(AudioDeviceInfo.TYPE_BLE_HEADSET); add(AudioDeviceInfo.TYPE_BLE_SPEAKER) }
            }
            return when {
                types.any { it in bt } -> BLUETOOTH
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES in types || AudioDeviceInfo.TYPE_WIRED_HEADSET in types -> WIRED
                (Build.VERSION.SDK_INT >= 26 && AudioDeviceInfo.TYPE_USB_HEADSET in types) -> USB
                else -> SPEAKER
            }
        }
    }
}

/**
 * The tap test: plays 4 count-in clicks and then 16 clicks, the player hits a pad along with
 * them, and the average gap between each click and its hit is the latency to allow for.
 *
 * - For ORIG. the clicks are an AudioTrack whose presentation timestamps say exactly when each
 *   click leaves the speaker (the app's player reports its position the same way).
 * - For SYNTH the clicks are a MIDI file played through the same MediaPlayer synth, timed from
 *   its reported position, so the synth's own delay is included.
 */
class DrumCalibrator(private val context: Context) {
    class Result(val latencyMs: Int, val pairs: Int, val spreadMs: Double)

    private val clickTimes = ArrayList<Double>() // real ms (nanoTime / 1e6) each scored click is heard
    private val hits = ArrayList<Double>()
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    var running = false
        private set
    /** Clicks heard so far (for progress), 0..TOTAL. */
    var progress = 0
        private set

    private var mode = Mode.AUDIO
    private var t0Nanos = 0L
    private var synthStartNanos = 0L

    enum class Mode { AUDIO, SYNTH }

    fun start(mode: Mode): Boolean {
        stop()
        this.mode = mode
        clickTimes.clear()
        hits.clear()
        progress = 0
        running = true
        return if (mode == Mode.AUDIO) startAudio() else startSynth()
    }

    private fun startAudio(): Boolean {
        val pcm = clickTrackPcm()
        val t = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        }.getOrNull() ?: return false.also { running = false }
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
        t0Nanos = 0L
        return true
    }

    private fun startSynth(): Boolean {
        val file = File(context.cacheDir, "drum_calibrate.mid").apply { writeBytes(clickMidi()) }
        val mp = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        }.getOrNull() ?: return false.also { running = false }
        player = mp
        synthStartNanos = 0L
        return true
    }

    /**
     * Call every frame while running. Returns the result once the clicks are over (then the
     * calibrator stops), or null.
     */
    fun frame(nowNanos: Long): Result? {
        if (!running) return null
        when (mode) {
            Mode.AUDIO -> {
                val t = track ?: return null
                val ts = AudioTimestamp()
                if (t.getTimestamp(ts) && ts.framePosition > 0) {
                    // Time frame 0 was (or would have been) heard.
                    val zero = ts.nanoTime - ts.framePosition * 1_000_000_000L / RATE
                    t0Nanos = if (t0Nanos == 0L) zero else (t0Nanos * 3 + zero) / 4
                }
                if (t0Nanos == 0L) return null
                clickTimes.clear()
                for (k in 0 until TOTAL) clickTimes += (t0Nanos + clickFrame(k) * 1_000_000_000L / RATE) / 1e6
            }
            Mode.SYNTH -> {
                val mp = player ?: return null
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                if (pos <= 0) return null
                val zero = nowNanos - pos * 1_000_000L
                synthStartNanos = if (synthStartNanos == 0L) zero else (synthStartNanos * 7 + zero) / 8
                clickTimes.clear()
                for (k in 0 until TOTAL) clickTimes += (synthStartNanos / 1e6) + LEAD_MS + k * INTERVAL_MS
            }
        }
        val now = nowNanos / 1e6
        progress = clickTimes.count { it <= now }.coerceAtMost(TOTAL)
        if (now > (clickTimes.lastOrNull() ?: return null) + 600) {
            val r = result()
            stop()
            return r
        }
        return null
    }

    fun onHit(hit: DrumHit) {
        if (running) hits += hit.timeNanos / 1e6
    }

    private fun result(): Result {
        val scored = clickTimes.drop(COUNT_IN)
        val diffs = scored.mapNotNull { c ->
            hits.minByOrNull { abs(it - c) }?.let { it - c }?.takeIf { abs(it) <= 300 }
        }
        if (diffs.size < 8) return Result(-1, diffs.size, 0.0)
        val sorted = diffs.sorted()
        val trim = sorted.drop(2).dropLast(2).ifEmpty { sorted }
        val mean = trim.average()
        val spread = trim.map { abs(it - mean) }.average()
        return Result(mean.toInt(), diffs.size, spread)
    }

    fun stop() {
        running = false
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private fun clickFrame(k: Int): Long = ((LEAD_MS + k * INTERVAL_MS) * RATE / 1000.0).toLong()

    private fun clickTrackPcm(): ShortArray {
        val totalMs = LEAD_MS + TOTAL * INTERVAL_MS + 500
        val out = ShortArray((totalMs * RATE / 1000).toInt())
        for (k in 0 until TOTAL) {
            val start = clickFrame(k).toInt()
            val freq = if (k < COUNT_IN) 1760.0 else 1320.0
            val n = RATE * 30 / 1000
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= out.size) break
                val t = i.toDouble() / RATE
                out[idx] = (sin(2 * PI * freq * t) * exp(-t * 110) * 0.9 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    /** A tiny Standard MIDI File: side-stick on channel 10 at the click times. */
    private fun clickMidi(): ByteArray {
        val ppq = 480
        val tempoUs = (INTERVAL_MS * 1000).toInt() // one click per quarter note
        val ev = ByteArrayOutputStream()
        fun vlq(v: Int) {
            var buffer = v and 0x7F
            var x = v shr 7
            while (x > 0) { buffer = (buffer shl 8) or 0x80 or (x and 0x7F); x = x shr 7 }
            while (true) { ev.write(buffer and 0xFF); if (buffer and 0x80 != 0) buffer = buffer shr 8 else break }
        }
        vlq(0); ev.write(byteArrayOf(0xFF.toByte(), 0x51, 3, (tempoUs shr 16).toByte(), (tempoUs shr 8).toByte(), tempoUs.toByte()))
        val leadTicks = (LEAD_MS / INTERVAL_MS * ppq).toInt()
        var pending = leadTicks
        for (k in 0 until TOTAL) {
            val noteNum = if (k < COUNT_IN) 76 else 37
            vlq(pending); ev.write(byteArrayOf(0x99.toByte(), noteNum.toByte(), 110))
            vlq(ppq / 8); ev.write(byteArrayOf(0x89.toByte(), noteNum.toByte(), 0))
            pending = ppq - ppq / 8
        }
        vlq(ppq); ev.write(byteArrayOf(0xFF.toByte(), 0x2F, 0))
        val data = ev.toByteArray()
        val out = ByteArrayOutputStream()
        fun int32(v: Int) { out.write(v ushr 24); out.write(v ushr 16 and 0xFF); out.write(v ushr 8 and 0xFF); out.write(v and 0xFF) }
        out.write("MThd".toByteArray()); int32(6); out.write(byteArrayOf(0, 0, 0, 1, (ppq shr 8).toByte(), ppq.toByte()))
        out.write("MTrk".toByteArray()); int32(data.size); out.write(data)
        return out.toByteArray()
    }

    companion object {
        const val RATE = 48_000
        const val COUNT_IN = 4
        const val TOTAL = COUNT_IN + 16
        const val INTERVAL_MS = 600.0
        const val LEAD_MS = 800.0
    }
}
