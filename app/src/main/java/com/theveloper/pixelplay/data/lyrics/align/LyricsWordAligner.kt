package com.theveloper.pixelplay.data.lyrics.align

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.util.AtomicFile
import com.theveloper.pixelplay.data.analysis.PcmDecoder
import com.theveloper.pixelplay.data.diagnostics.HeapPressure
import com.theveloper.pixelplay.data.lyrics.LyricsTiming
import com.theveloper.pixelplay.data.lyrics.autosync.LyricsAudioSources
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device word and letter sync for line-synced lyrics.
 *
 * For the song that is playing, with line-synced lyrics and no word timing yet:
 *  1. A saved result for the same lyrics and model applies at once.
 *  2. Otherwise, once the user has settled on the song (and the phone is not in battery saver,
 *     hot, low on battery or low on memory), the model is fetched if needed (unmetered network
 *     only, see [AlignmentModelManager]), the song's audio is decoded to 16 kHz in one streaming
 *     pass through [ChunkedEmissions], and [LyricsAlignmentBuilder] aligns and checks every line.
 *  3. The aligned lyrics are saved in `noBackupFilesDir/lyrics-align-v1/` and published on
 *     [published]; `LyricsStateHolder` swaps them in for the current song.
 *
 * Audio comes from the same places as the automatic offset ([LyricsAudioSources]): local and
 * downloaded files, the stream proxy's whole-song copy for YouTube-backed songs (never a second
 * download), and other streams only on unmetered networks.
 *
 * One song at a time. Moving to another song cancels unfinished work. The model is loaded for
 * one song and closed straight after, so it doesn't sit in memory.
 */
class LyricsWordAligner private constructor(
    context: Context,
    private val audioSources: () -> LyricsAudioSources?
) {
    private val appContext = context.applicationContext
    val models = AlignmentModelManager(appContext)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val directory = File(appContext.noBackupFilesDir, "lyrics-align-v1")
    private val inFlight = ConcurrentHashMap<String, Job>()
    private val analysisLock = Mutex()

    @Volatile
    private var current: Job? = null

    /** Aligned lyrics, in audio time, for the songs touched this session. */
    data class Published(val songId: String, val key: String, val lyrics: Lyrics, val summary: String)

    private val _published = MutableStateFlow<Map<String, Published>>(emptyMap())
    val published: StateFlow<Map<String, Published>> = _published.asStateFlow()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var downloadJob: Job? = null

    /**
     * Downloads the word-sync model now, on Wi-Fi or mobile data (the Settings "Download" button).
     * Progress is in [AlignmentModelManager.state]. A second tap while it runs does nothing.
     */
    fun downloadModel() {
        if (downloadJob?.isActive == true || models.isReady()) return
        downloadJob = downloadScope.launch {
            try {
                models.downloadNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "Model download failed")
            }
        }
    }

    /** Pauses a download started with [downloadModel]; the next download resumes from there. */
    fun cancelModelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    /** Removes the model (and every partial download) from the phone. */
    fun deleteModel() {
        cancelModelDownload()
        models.delete()
    }

    /** What the aligner is doing for the current song (for the lyrics sheet and logs). */
    enum class Phase { IDLE, WAITING_FOR_MODEL, WAITING_FOR_AUDIO, ALIGNING, DONE, FAILED }

    private val _phase = MutableStateFlow<Pair<String?, Phase>>(null to Phase.IDLE)
    val phase: StateFlow<Pair<String?, Phase>> = _phase.asStateFlow()

    /**
     * Makes sure [song]'s [lyrics] get word timing if they can. Cheap to call on every lyrics
     * load. [offsetMs] is read when the alignment actually starts, so an automatic offset measured
     * meanwhile is taken into account.
     */
    fun request(song: Song, lyrics: Lyrics, offsetMs: suspend () -> Int) {
        val key = keyOf(lyrics, models.modelVersion) ?: return
        if (_published.value[song.id]?.key == key) return
        val jobKey = "${song.id}|$key"
        if (inFlight[jobKey]?.isActive == true) return
        current?.takeIf { it.isActive }?.cancel()

        val job = scope.launch {
            try {
                readRecord(song.id)?.takeIf { it.key == key }?.let { record ->
                    record.lyrics?.let { publish(song.id, key, it, record.reason) }
                    _phase.value = song.id to if (record.lyrics != null) Phase.DONE else Phase.FAILED
                    return@launch
                }
                delay(SETTLE_MS)
                if (song.duration > MAX_SONG_MS) {
                    saveRecord(Record(song.id, key, null, "longer than ${MAX_SONG_MS / 60_000} min"))
                    return@launch
                }
                if (!deviceCanRun()) return@launch // not saved: tried again on a later play

                _phase.value = song.id to Phase.WAITING_FOR_MODEL
                val modelFile = models.ensure() ?: return@launch
                _phase.value = song.id to Phase.WAITING_FOR_AUDIO
                val uri = obtainAudio(song) ?: return@launch
                val record = analysisLock.withLock {
                    _phase.value = song.id to Phase.ALIGNING
                    align(song, uri, lyrics, key, offsetMs(), modelFile)
                } ?: return@launch
                saveRecord(record)
                record.lyrics?.let { publish(song.id, key, it, record.reason) }
                _phase.value = song.id to if (record.lyrics != null) Phase.DONE else Phase.FAILED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Includes native/ORT errors and OOM: never let alignment take the app down.
                Timber.tag(TAG).w(e, "Word alignment failed for %s", song.id)
            } finally {
                coroutineContext[Job]?.let { inFlight.remove(jobKey, it) }
            }
        }
        inFlight[jobKey] = job
        current = job
    }

    /** Forgets the saved alignment of [songId] (e.g. its lyrics were edited or reset). */
    fun forget(songId: String) {
        _published.update { it - songId }
        runCatching { recordFile(songId).delete() }
    }

    /** Deletes every saved alignment (the model stays). */
    fun clearCache() {
        _published.value = emptyMap()
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    private fun publish(songId: String, key: String, lyrics: Lyrics, summary: String) {
        _published.update { current ->
            val base = if (current.size >= MAX_PUBLISHED && songId !in current) {
                current.entries.drop(current.size - MAX_PUBLISHED / 2).associate { it.key to it.value }
            } else current
            base + (songId to Published(songId, key, lyrics, summary))
        }
    }

    private suspend fun align(song: Song, uri: Uri, lyrics: Lyrics, key: String, offsetMs: Int, modelFile: File): Record? {
        if (HeapPressure.isElevated() || HeapPressure.headroomBytes() < MIN_HEADROOM_BYTES) {
            Timber.tag(TAG).d("Skipping %s: heap under pressure", song.id)
            return null
        }
        val started = System.nanoTime()
        val model = OnnxCtcModel(modelFile)
        try {
            val emissions = ChunkedEmissions(model, maxFrames = (MAX_SONG_MS / FRAME_MS).toInt())
            var resampler: StreamingResampler? = null
            var modelError: Throwable? = null
            val delivered = PcmDecoder.stream(appContext, uri, maxSeconds = (MAX_SONG_MS / 1000).toInt()) { samples, count, rate ->
                try {
                    val r = resampler ?: StreamingResampler(rate, model.sampleRate) { s, c -> emissions.push(s, c) }
                        .also { resampler = it }
                    r.push(samples, 0, count)
                    !emissions.isFull
                } catch (t: Throwable) {
                    modelError = t
                    false
                }
            }
            modelError?.let { throw it }
            if (delivered == null) {
                return if (uri.scheme == "http" || uri.scheme == "https") null
                else Record(song.id, key, null, "could not decode")
            }
            resampler?.finish()
            val em = emissions.finish(FRAME_MS.toDouble())
            val inferenceMs = (System.nanoTime() - started) / 1_000_000

            val result = LyricsAlignmentBuilder.build(lyrics, em, offsetMs, song.duration, models.modelVersion)
            val totalMs = (System.nanoTime() - started) / 1_000_000
            Timber.tag(TAG).i(
                "%s \"%s\": %s — %d letter, %d word, %d line; offset %d ms; %d frames; %d ms (decode+model %d ms)",
                song.id, song.title, result.reason, result.letterLines, result.wordLines, result.lineLines,
                offsetMs, em.frames, totalMs, inferenceMs
            )
            result.outcomes.filter { it.level == LyricsAlignmentBuilder.Level.LINE }.take(12).forEach {
                Timber.tag(TAG).d("  line %d kept: %s", it.index, it.reason)
            }
            val summary = "${result.letterLines} letter · ${result.wordLines} word · ${result.lineLines} line"
            return Record(song.id, key, result.lyrics, if (result.lyrics != null) summary else result.reason)
        } finally {
            model.close()
        }
    }

    /** A decodable URI for [song], or null to try again on a later play (nothing is saved). */
    private suspend fun obtainAudio(song: Song): Uri? {
        val sources = audioSources() ?: return null
        val source = try {
            sources.resolve(song)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return when (source) {
            is LyricsAudioSources.Source.Ready -> source.uri
            is LyricsAudioSources.Source.Network -> source.uri.takeIf { sources.isUnmetered() }
            is LyricsAudioSources.Source.StreamCache -> {
                sources.cachedStreamFile(source.videoId)?.let { return Uri.fromFile(it) }
                if (sources.completeStreamCache(source.videoId)) sources.cachedStreamFile(source.videoId)?.let { return Uri.fromFile(it) }
                // Playing the song fills the proxy's copy; check back until it's whole.
                val deadline = System.currentTimeMillis() + minOf(song.duration + STREAM_WAIT_EXTRA_MS, MAX_STREAM_WAIT_MS)
                while (System.currentTimeMillis() < deadline) {
                    delay(STREAM_POLL_MS)
                    sources.cachedStreamFile(source.videoId)?.let { return Uri.fromFile(it) }
                }
                null
            }
        }
    }

    /** Battery saver, heat, low battery, low-RAM phones and low free memory all postpone alignment. */
    private fun deviceCanRun(): Boolean {
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isPowerSaveMode == true) return false.also { Timber.tag(TAG).d("Battery saver on: alignment postponed") }
        if ((power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE) {
            return false.also { Timber.tag(TAG).d("Phone is warm: alignment postponed") }
        }
        val battery = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (battery != null) {
            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level in 0 until MIN_BATTERY_PERCENT && !battery.isCharging) return false
        }
        val activity = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (activity.isLowRamDevice) return false
        val memory = ActivityManager.MemoryInfo().also { activity.getMemoryInfo(it) }
        if (memory.lowMemory || memory.totalMem < MIN_TOTAL_RAM_BYTES || memory.availMem < MIN_AVAILABLE_RAM_BYTES) return false
        return true
    }

    // ─── Storage ───────────────────────────────────────────────────────────────────────

    private class Record(val songId: String, val key: String, val lyrics: Lyrics?, val reason: String)

    private fun recordFile(songId: String) = File(directory, sha1(songId) + ".json")

    private fun readRecord(songId: String): Record? {
        val json = try {
            AtomicFile(recordFile(songId)).openRead().bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: Exception) {
            return null
        }
        if (json.optInt("v") != VERSION || json.optString("songId") != songId) return null
        val lyrics = json.optString("lyrics").takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching { cacheJson.decodeFromString(Lyrics.serializer(), raw) }.getOrNull()
                ?.takeIf { LyricsTiming.validate(it).isEmpty() }
        }
        return Record(songId, json.optString("key"), lyrics, json.optString("reason"))
    }

    private fun saveRecord(record: Record) {
        directory.mkdirs()
        val atomic = AtomicFile(recordFile(record.songId))
        val out = try { atomic.startWrite() } catch (_: Exception) { return }
        try {
            val json = JSONObject()
                .put("v", VERSION)
                .put("songId", record.songId)
                .put("key", record.key)
                .put("reason", record.reason)
                .put("at", System.currentTimeMillis())
            record.lyrics?.let { json.put("lyrics", cacheJson.encodeToString(Lyrics.serializer(), it)) }
            out.write(json.toString().toByteArray())
            atomic.finishWrite(out)
        } catch (_: Exception) {
            atomic.failWrite(out)
        }
    }

    companion object {
        private const val TAG = "LyricsWordAlign"

        /**
         * Compact cache format: defaults (and so nulls) are left out. A letter-synced song is a
         * few hundred KB this way, about half the size of [LyricsTiming.encode], which matters for
         * long, wordy songs.
         */
        private val cacheJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        private const val VERSION = 1
        private const val FRAME_MS = 20L
        private const val MAX_SONG_MS = 12L * 60 * 1000
        private const val SETTLE_MS = 3_000L
        private const val MAX_PUBLISHED = 64
        private const val MIN_HEADROOM_BYTES = 48L * 1024 * 1024
        private const val MIN_BATTERY_PERCENT = 20
        private const val MIN_TOTAL_RAM_BYTES = 3L * 1024 * 1024 * 1024
        private const val MIN_AVAILABLE_RAM_BYTES = 600L * 1024 * 1024
        private const val STREAM_POLL_MS = 10_000L
        private const val STREAM_WAIT_EXTRA_MS = 2L * 60 * 1000
        private const val MAX_STREAM_WAIT_MS = 20L * 60 * 1000

        /**
         * Identifies line-synced lyrics that can be aligned: their line times and text plus the
         * model version. Null for lyrics that already have word timing (or are aligned output).
         */
        fun keyOf(lyrics: Lyrics?, modelVersion: String = AlignmentModelManager.MODEL_VERSION): String? {
            val lines = lyrics?.synced
            if (lines.isNullOrEmpty()) return null
            if (lyrics.timing?.source == LyricsAlignmentBuilder.SOURCE) return null
            if (lines.any { !it.words.isNullOrEmpty() }) return null
            if (lines.count { it.line.isNotBlank() } < 2) return null
            val text = buildString {
                append("v").append(VERSION).append('|').append(modelVersion)
                for (line in lines) append('\n').append(line.time).append('|').append(line.line)
            }
            return sha1(text)
        }

        private fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        @Volatile
        private var instance: LyricsWordAligner? = null

        /** Installed once from `Application.onCreate`, like [com.theveloper.pixelplay.data.lyrics.autosync.LyricsAutoSync]. */
        fun install(context: Context, audioSources: () -> LyricsAudioSources? = { null }): LyricsWordAligner =
            instance ?: synchronized(this) {
                instance ?: LyricsWordAligner(context, audioSources).also { instance = it }
            }

        fun get(): LyricsWordAligner? = instance
    }
}
