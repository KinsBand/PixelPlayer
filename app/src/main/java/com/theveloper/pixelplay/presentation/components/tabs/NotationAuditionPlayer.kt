package com.theveloper.pixelplay.presentation.components.tabs

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.theveloper.pixelplay.data.soundfont.MidiEvents
import com.theveloper.pixelplay.data.soundfont.SampledScoreEngine
import com.theveloper.pixelplay.data.soundfont.ScorePcmSource
import com.theveloper.pixelplay.data.soundfont.SoundFont
import com.theveloper.pixelplay.data.songsterr.RenderedTrack
import com.theveloper.pixelplay.data.songsterr.StringSynth
import com.theveloper.pixelplay.data.songsterr.StringSynthEngine
import com.theveloper.pixelplay.data.songsterr.TabMidi
import com.theveloper.pixelplay.data.songsterr.TabTimeline
import kotlin.math.max

/**
 * Plays notation examples for the Notation Help sheet: a demo bar of one mark, or a bar of the
 * song itself. Each is rendered through the same path as tab playback (tab → [TabMidi] →
 * recorded instruments, or the modelled strings before the instrument sounds are downloaded).
 *
 * The audio track stays open while the sheet is showing, so a tap sounds at once; [release] it
 * when the sheet closes.
 */
internal class NotationAuditionPlayer {
    private val sr = StringSynth.SAMPLE_RATE
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var source: ScorePcmSource? = null
    @Volatile private var endFrame = 0L
    @Volatile private var generation = 0

    /** Key of the example playing now (for the row's play / stop icon), or null. */
    var playingKey by mutableStateOf<String?>(null)
        private set

    /**
     * Plays [entries] (timeline entries; all by default) of [track] as the only part. Returns
     * false when nothing can sound it (no instrument sounds yet and not a guitar / bass).
     */
    fun play(key: String, track: RenderedTrack, font: SoundFont?, entries: List<Int>? = null): Boolean {
        val tl = TabTimeline(track)
        val seq = entries ?: tl.entries.indices.toList()
        if (seq.isEmpty()) return false
        val res = runCatching {
            TabMidi.build(tl, seq, TabMidi.Options(realStrings = true), listOf(TabMidi.Part(track)))
        }.getOrElse { Log.w(TAG, "Demo build failed: ${it.message}"); return false }
        val src: ScorePcmSource = when {
            font != null -> SampledScoreEngine(font, MidiEvents.parse(res.bytes), res.strings, sr)
            res.strings != null -> StringSynthEngine(res.strings, sr)
            else -> return false
        }
        if (!ensureStarted()) return false
        endFrame = ((res.totalMs + TAIL_MS) * sr / 1000.0).toLong()
        val gen = ++generation
        source = src
        playingKey = key
        // Clear the playing mark when this example ends (unless another one replaced it).
        main.postDelayed({ if (generation == gen) { source = null; playingKey = null } }, (res.totalMs + TAIL_MS).toLong())
        return true
    }

    fun stop() {
        generation++
        source = null
        playingKey = null
    }

    fun release() {
        stop()
        running = false
        runCatching { thread?.join(300) }
        thread = null
        track?.let { t ->
            runCatching { t.pause() }
            runCatching { t.flush() }
            runCatching { t.release() }
        }
        track = null
    }

    private fun ensureStarted(): Boolean {
        if (running && track != null) return true
        val t = runCatching {
            val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
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
                // Small buffer: a tap should sound at once.
                .setBufferSizeInBytes(max(minBuf, sr * 4 * 60 / 1000))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrElse { Log.w(TAG, "Audition track unavailable: ${it.message}"); return false }
        track = t
        running = true
        t.play()
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val frames = 256
            val chunk = ShortArray(frames * 2)
            while (running) {
                val src = source
                if (src != null && src.framesRendered < endFrame) {
                    src.render(chunk, frames)
                } else {
                    java.util.Arrays.fill(chunk, 0)
                }
                var off = 0
                while (running && off < chunk.size) {
                    val r = t.write(chunk, off, chunk.size - off)
                    if (r < 0) { running = false; break }
                    off += r
                }
            }
        }, "NotationAudition").apply { start() }
        return true
    }

    private companion object {
        const val TAG = "NotationAudition"
        const val TAIL_MS = 1200.0
    }
}
