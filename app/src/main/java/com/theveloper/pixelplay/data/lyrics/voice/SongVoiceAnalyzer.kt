package com.theveloper.pixelplay.data.lyrics.voice

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.theveloper.pixelplay.data.analysis.PcmDecoder
import com.theveloper.pixelplay.data.analysis.dsp.BpmDetector
import com.theveloper.pixelplay.data.analysis.dsp.KeyDetector
import com.theveloper.pixelplay.data.analysis.dsp.OnsetEnvelope
import com.theveloper.pixelplay.data.diagnostics.HeapPressure
import com.theveloper.pixelplay.data.lyrics.autosync.LyricsAudioSources
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.presentation.components.lyricsDisplayPrefsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What adaptive expressive typography hears in a song's audio: its loudness over the WHOLE song
 * (one value every [SongVoiceAnalyzer.STEP_MS]), overall energy, how much the loudness moves
 * (dynamics), tempo and key.
 *
 * Unlike the library analysis (track_analysis), this exists for every song that can be decoded
 * on the phone — local files, downloads AND streamed songs once the stream proxy holds them whole —
 * and it covers the full length (the library waveform stops at the 180 s decode window).
 */
class SongVoice(
    val songId: String,
    /** Loudness per [stepMs], 0..1 (0 = -60 dBFS or quieter, 1 = 0 dBFS). Sample 0 = position 0. */
    val envelope: FloatArray,
    val stepMs: Int,
    /** 0..1, same scale as the library analysis' energy. */
    val energy: Float,
    /** 0..1: how much the loudness moves through the song. */
    val dynamics: Float,
    val bpm: Int?,
    /** e.g. "Am", "C#" (minor keys end in "m"); null when unsure. */
    val musicKey: String?,
    val analysedAt: Long,
) {
    /** How much of the song the envelope covers. */
    val spanMs: Long get() = envelope.size.toLong() * stepMs
}

/**
 * Measures a [SongVoice] once per song, in the background, and keeps it on disk
 * (`noBackupFilesDir/lyric-voice-v1/`), so a song is decoded once ever.
 *
 * Mirrors [com.theveloper.pixelplay.data.lyrics.autosync.LyricsAutoSync]:
 * - audio comes from [LyricsAudioSources] (local file, download, the stream proxy's complete copy;
 *   other streams only on unmetered networks), so no song is downloaded twice;
 * - one analysis at a time, 2.5 s after the user settles on a song; moving on cancels it;
 * - a streamed song is measured once playing has made its copy complete; the result then applies
 *   from the next play unless it lands early in the song (see LyricExpressionEngine);
 * - one streaming decode pass (~1–2 s CPU on a phone, ~9 MB transient for the tempo/key window),
 *   skipped while the heap is under pressure;
 * - idle unless Expressive + Adaptive typography are on.
 */
class SongVoiceAnalyzer private constructor(
    context: Context,
    private val audioSources: () -> LyricsAudioSources?,
) {
    private val appContext = context.applicationContext

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val directory = File(appContext.noBackupFilesDir, DIRECTORY)
    private val analysisLock = Mutex()
    private val inFlight = ConcurrentHashMap<String, Job>()
    /** Songs that can't be decoded this session (not retried until the app restarts). */
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val memory = object : LinkedHashMap<String, SongVoice>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SongVoice>?): Boolean = size > MEMORY_ENTRIES
    }

    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Emits a song id whenever a new voice was measured for it. */
    val updates: SharedFlow<String> = _updates.asSharedFlow()

    @Volatile
    private var current: Job? = null

    @Volatile
    private var lookAhead: Job? = null

    /** The voice already in memory, if any. */
    fun cached(songId: String): SongVoice? = synchronized(memory) { memory[songId] }

    /** The saved voice for [songId] (memory, then disk), or null when it hasn't been measured. */
    suspend fun load(songId: String): SongVoice? {
        cached(songId)?.let { return it }
        val voice = withContext(Dispatchers.IO) { readVoice(songId) } ?: return null
        keep(voice)
        return voice
    }

    /**
     * Makes sure [song] gets a voice. Cheap to call on every song change and lyrics load.
     * [lookAhead] requests (the next song) never wait for a stream and give way to the current song.
     */
    fun request(song: Song, lookAhead: Boolean = false) {
        val id = song.id
        if (id.isBlank() || cached(id) != null || id in failed) return
        if (inFlight[id]?.isActive == true) return
        if (!lookAhead) {
            this.lookAhead?.takeIf { it.isActive }?.cancel()
            this.current?.takeIf { it.isActive }?.cancel()
        }
        val job = scope.launch {
            try {
                if (!isEnabled()) return@launch
                if (load(id) != null) return@launch
                if (!lookAhead) delay(SETTLE_MS)
                if (song.duration > MAX_SONG_MS) {
                    failed += id
                    return@launch
                }
                val uri = obtainAudio(song, lookAhead) ?: return@launch
                val voice = analysisLock.withLock { measure(id, uri) } ?: return@launch
                withContext(Dispatchers.IO) { saveVoice(voice) }
                keep(voice)
                _updates.tryEmit(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Song voice failed for %s", id)
            } finally {
                coroutineContext[Job]?.let { inFlight.remove(id, it) }
            }
        }
        inFlight[id] = job
        if (lookAhead) this.lookAhead = job else this.current = job
    }

    private fun keep(voice: SongVoice) = synchronized(memory) { memory[voice.songId] = voice }

    private suspend fun isEnabled(): Boolean = runCatching {
        val prefs = lyricsDisplayPrefsFlow(appContext.dataStore).first()
        prefs.expressiveTypography && prefs.adaptiveTypography
    }.getOrDefault(false)

    private suspend fun obtainAudio(song: Song, lookAhead: Boolean): Uri? {
        val sources = audioSources() ?: return null
        val source = try {
            sources.resolve(song)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "No audio for %s", song.id)
            null
        } ?: return null

        return when (source) {
            is LyricsAudioSources.Source.Ready -> source.uri
            is LyricsAudioSources.Source.Network -> source.uri.takeIf { sources.isUnmetered() }
            is LyricsAudioSources.Source.StreamCache -> {
                sources.cachedStreamFile(source.videoId)?.let { return Uri.fromFile(it) }
                if (lookAhead) return null
                // Playing the song fills the proxy's copy; check back until it's whole. Moving to
                // another song cancels this and the next play finds the finished copy.
                val deadline = System.currentTimeMillis() + minOf(song.duration + STREAM_WAIT_EXTRA_MS, MAX_STREAM_WAIT_MS)
                while (System.currentTimeMillis() < deadline) {
                    delay(STREAM_POLL_MS)
                    sources.cachedStreamFile(source.videoId)?.let { return Uri.fromFile(it) }
                }
                null
            }
        }
    }

    private suspend fun measure(songId: String, uri: Uri): SongVoice? {
        if (HeapPressure.isElevated() || HeapPressure.headroomBytes() < MIN_HEADROOM_BYTES) return null
        val started = System.nanoTime()

        val envelope = ByteArrayOutputStream(4096)
        var rate = 0
        var stepSamples = 1
        var acc = 0.0
        var accN = 0
        var totalSq = 0.0
        var totalN = 0L
        // A short mono window (~22 kHz) for tempo and key.
        var factor = 1
        var window: FloatArray? = null
        var windowFill = 0
        var decAcc = 0f
        var decN = 0

        val delivered = PcmDecoder.stream(appContext, uri, maxSeconds = (MAX_SONG_MS / 1000).toInt()) { samples, count, sampleRate ->
            if (rate == 0) {
                rate = sampleRate
                stepSamples = (sampleRate.toLong() * STEP_MS / 1000).toInt().coerceAtLeast(1)
                factor = (sampleRate / 22_050f).roundToInt().coerceAtLeast(1)
                window = runCatching { FloatArray(WINDOW_SECONDS * sampleRate / factor) }.getOrNull()
            }
            val w = window
            for (i in 0 until count) {
                val s = samples[i]
                acc += (s * s).toDouble()
                accN++
                if (accN >= stepSamples) {
                    envelope.write(quantize(acc / accN))
                    totalSq += acc
                    totalN += accN
                    acc = 0.0
                    accN = 0
                }
                if (w != null && windowFill < w.size) {
                    decAcc += s
                    if (++decN == factor) {
                        w[windowFill++] = decAcc / factor
                        decAcc = 0f
                        decN = 0
                    }
                }
            }
            true
        }
        if (accN > 0) {
            envelope.write(quantize(acc / accN))
            totalSq += acc
            totalN += accN
        }
        val bytes = envelope.toByteArray()
        if (delivered == null || rate == 0 || bytes.size < MIN_STEPS) {
            if (uri.scheme != "http" && uri.scheme != "https") failed += songId
            return null
        }

        val meanDb = if (totalN > 0 && totalSq > 0.0) (10.0 * log10(totalSq / totalN)).toFloat() else -100f
        // Same mapping as EnergyLoudness.energy (-40..-5 dBFS, gamma 0.75), so local analysis and
        // this voice agree on what "energetic" means.
        val energy = ((meanDb - QUIET_DBFS) / (LOUD_DBFS - QUIET_DBFS)).coerceIn(0f, 1f).pow(ENERGY_GAMMA)

        // Dynamics: spread of the (non-silent) loudness in dB. ~6 dB of movement = fully dynamic.
        val audibleDb = bytes.map { (it.toInt() and 0xFF) / 255f * 60f - 60f }.filter { it > -50f }
        val dynamics = if (audibleDb.size >= MIN_STEPS) {
            val mean = audibleDb.average().toFloat()
            val sd = sqrt(audibleDb.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / audibleDb.size)
            (sd / 6f).coerceIn(0f, 1f)
        } else 0.5f

        var bpm: Int? = null
        var key: String? = null
        val w = window
        val windowRate = if (rate > 0) rate / factor else 0
        if (w != null && windowRate > 0 && windowFill >= windowRate * 10) {
            val pcm = if (windowFill == w.size) w else w.copyOf(windowFill)
            bpm = runCatching {
                val onset = OnsetEnvelope.compute(pcm, windowRate)
                BpmDetector.detectBpm(onset.envelope, onset.envelopeSampleRate, onset.rawMean)
            }.getOrNull()
            key = runCatching { KeyDetector.detectKey(pcm, windowRate)?.key }.getOrNull()
        }
        window = null

        val voice = SongVoice(
            songId = songId,
            envelope = FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f },
            stepMs = STEP_MS,
            energy = energy,
            dynamics = dynamics,
            bpm = bpm,
            musicKey = key,
            analysedAt = System.currentTimeMillis(),
        )
        Timber.tag(TAG).i(
            "%s: voice energy=%.2f dynamics=%.2f bpm=%s key=%s (%d steps, %d Hz) in %d ms",
            songId, energy, dynamics, bpm ?: "-", key ?: "-", bytes.size, rate,
            (System.nanoTime() - started) / 1_000_000
        )
        return voice
    }

    // ─── Storage ───────────────────────────────────────────────────────────────────────

    private fun file(songId: String) = File(directory, sha1(songId) + ".bin")

    private fun readVoice(songId: String): SongVoice? = try {
        AtomicFile(file(songId)).openRead().use { raw ->
            val input = DataInputStream(raw.buffered())
            if (input.readInt() != FORMAT_VERSION) return null
            if (input.readUTF() != songId) return null
            val stepMs = input.readInt()
            val energy = input.readFloat()
            val dynamics = input.readFloat()
            val bpm = input.readInt().takeIf { it > 0 }
            val key = input.readUTF().takeIf { it.isNotEmpty() }
            val at = input.readLong()
            val size = input.readInt()
            if (size !in MIN_STEPS..MAX_STEPS || stepMs <= 0) return null
            val bytes = ByteArray(size)
            input.readFully(bytes)
            SongVoice(songId, FloatArray(size) { (bytes[it].toInt() and 0xFF) / 255f }, stepMs, energy, dynamics, bpm, key, at)
        }
    } catch (_: Exception) {
        null
    }

    private fun saveVoice(voice: SongVoice) {
        directory.mkdirs()
        val atomic = AtomicFile(file(voice.songId))
        val out = try { atomic.startWrite() } catch (_: Exception) { return }
        try {
            val data = DataOutputStream(out)
            data.writeInt(FORMAT_VERSION)
            data.writeUTF(voice.songId)
            data.writeInt(voice.stepMs)
            data.writeFloat(voice.energy)
            data.writeFloat(voice.dynamics)
            data.writeInt(voice.bpm ?: -1)
            data.writeUTF(voice.musicKey.orEmpty())
            data.writeLong(voice.analysedAt)
            data.writeInt(voice.envelope.size)
            data.write(ByteArray(voice.envelope.size) { (voice.envelope[it] * 255f).roundToInt().coerceIn(0, 255).toByte() })
            data.flush()
            atomic.finishWrite(out)
        } catch (_: Exception) {
            atomic.failWrite(out)
        }
        prune()
    }

    /** Keeps the store small: past [MAX_FILES] songs, the least recently written go. */
    private fun prune() {
        val files = directory.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES + PRUNE_SLACK).forEach { runCatching { it.delete() } }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "SongVoice"
        private const val DIRECTORY = "lyric-voice-v1"
        private const val FORMAT_VERSION = 1

        /** Envelope resolution. */
        const val STEP_MS = 100
        private const val WINDOW_SECONDS = 90
        private const val MIN_STEPS = 50
        private const val MAX_STEPS = 12_000
        private const val MAX_SONG_MS = 20L * 60 * 1000
        private const val MAX_FILES = 1500
        private const val PRUNE_SLACK = 100
        private const val MEMORY_ENTRIES = 48
        private const val MIN_HEADROOM_BYTES = 48L * 1024 * 1024
        private const val SETTLE_MS = 2_500L
        private const val STREAM_POLL_MS = 10_000L
        private const val STREAM_WAIT_EXTRA_MS = 2L * 60 * 1000
        private const val MAX_STREAM_WAIT_MS = 20L * 60 * 1000

        private const val QUIET_DBFS = -40f
        private const val LOUD_DBFS = -5f
        private const val ENERGY_GAMMA = 0.75f

        /** Mean square power of one step → 0..255 across -60..0 dBFS. */
        private fun quantize(meanSquare: Double): Int {
            if (meanSquare <= 1e-12) return 0
            val db = 10.0 * log10(meanSquare)
            return (((db + 60.0) / 60.0) * 255.0).roundToInt().coerceIn(0, 255)
        }

        @Volatile
        private var instance: SongVoiceAnalyzer? = null

        /** Installed once from `Application.onCreate`; [audioSources] is read lazily. */
        fun install(context: Context, audioSources: () -> LyricsAudioSources?): SongVoiceAnalyzer =
            instance ?: synchronized(this) {
                instance ?: SongVoiceAnalyzer(context, audioSources).also { instance = it }
            }

        /** Null until [install] ran (e.g. unit tests): song voices are then simply never measured. */
        fun get(): SongVoiceAnalyzer? = instance
    }
}
