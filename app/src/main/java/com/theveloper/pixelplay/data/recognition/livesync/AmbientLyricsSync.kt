package com.theveloper.pixelplay.data.recognition.livesync

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Where the music playing nearby is in the recognised song. */
sealed interface LiveLyricsState {
    data object Idle : LiveLyricsState
    /** Listening and matching; nothing is shown as live yet. */
    data object Syncing : LiveLyricsState
    /**
     * Locked on: the song position is [anchorPositionMs] at [anchorNanos] (System.nanoTime)
     * and moves in real time from there. Only reached after two separate listens agree.
     */
    data class Live(val anchorPositionMs: Long, val anchorNanos: Long) : LiveLyricsState {
        fun positionNow(nowNanos: Long = System.nanoTime()): Long =
            anchorPositionMs + (nowNanos - anchorNanos) / 1_000_000
    }
    /** Couldn't be sure (or the music stopped / changed): lyrics stay static, tap to jump. */
    data class Unavailable(val reason: String) : LiveLyricsState
}

/**
 * Syncs the recognised song's lyrics to music playing nearby.
 *
 * Google's song search only says *which* song; this finds *where*: it listens through the
 * mic, decodes the song's own audio and lines the two up (chroma + rhythm, see
 * [AudioAligner]). It is deliberately strict, so the lyrics are never shown out of sync:
 *  - a match only counts if it stands far out from every other place in the song;
 *  - live sync starts only when two separate listens agree to within [AGREE_MS];
 *  - while live it listens again every [RECHECK_EVERY_MS] and corrects itself; if the music
 *    stops, changes or can't be confirmed twice in a row, live sync ends (lyrics go static).
 */
@Singleton
class AmbientLyricsSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: dagger.Lazy<DualPlayerEngine>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _state = MutableStateFlow<LiveLyricsState>(LiveLyricsState.Idle)
    val state: StateFlow<LiveLyricsState> = _state.asStateFlow()

    /** Song id → its features, so "search again" / re-opening doesn't decode twice. */
    private val featureCache = object : LinkedHashMap<String, AudioFeatures>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioFeatures>) = size > 2
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts syncing to [song] (call while the app is on screen: the mic needs it). */
    fun start(song: Song) {
        job?.cancel()
        if (!hasMicPermission()) {
            _state.value = LiveLyricsState.Unavailable("Allow the microphone to follow the music")
            return
        }
        _state.value = LiveLyricsState.Syncing
        job = scope.launch {
            try {
                run(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Live lyrics sync failed")
                _state.value = LiveLyricsState.Unavailable("Couldn't follow the music")
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = LiveLyricsState.Idle
    }

    private suspend fun run(song: Song) = kotlinx.coroutines.coroutineScope {
        // The song's audio decodes while the first listen records.
        val reference = async(Dispatchers.IO) { referenceFeatures(song) }
        val first = listen()
        val songFeatures = withTimeoutOrNull(REFERENCE_TIMEOUT_MS) { reference.await() }
        if (songFeatures == null) {
            _state.value = LiveLyricsState.Unavailable("Couldn't load this song to follow it")
            return@coroutineScope
        }
        // Two listens that agree before anything is shown as live.
        var anchor: Pair<Double, Long>? = null // (song ms at clip start, clip start nanos)
        var attempts = 0
        var previous: Pair<Double, Long>? = first.takeIf { it.isMusic }?.let { matchOf(it, songFeatures) }
        while (anchor == null && attempts < MAX_LOCK_ATTEMPTS) {
            ensureActive()
            attempts++
            val clip = listen()
            val current = clip.takeIf { it.isMusic }?.let { matchOf(it, songFeatures) }
            val before = previous
            if (current != null && before != null) {
                val predicted = before.first + (current.second - before.second) / 1_000_000.0
                if (abs(predicted - current.first) <= AGREE_MS) anchor = current
            }
            previous = current
        }
        if (anchor == null) {
            _state.value = LiveLyricsState.Unavailable("Couldn't lock onto the music: tap a line to jump")
            return@coroutineScope
        }
        publish(anchor)

        // Keep checking while live; correct small drift, stop when it can't be confirmed.
        var misses = 0
        while (true) {
            delay(RECHECK_EVERY_MS)
            val live = _state.value as? LiveLyricsState.Live ?: return@coroutineScope
            val clip = listen()
            if (!clip.isMusic) {
                misses++
            } else {
                // Near where the song should be first (a repeated chorus can't fool this), then
                // anywhere (the music may have been skipped).
                val expectedAtClip = live.positionNow(clip.startNanos).toDouble()
                val match = matchNear(clip, songFeatures, expectedAtClip) ?: matchOf(clip, songFeatures)
                if (match == null) {
                    misses++
                } else {
                    val expected = live.positionNow(match.second).toDouble()
                    val drift = match.first - expected
                    if (abs(drift) <= RESYNC_MS) {
                        misses = 0
                        // Small drift: nudge to the fresh measurement.
                        if (abs(drift) > 40) publish(match)
                    } else {
                        // A confident jump (skipped ahead, started again): confirm once more first.
                        val confirm = listen().takeIf { it.isMusic }?.let { matchOf(it, songFeatures) }
                        if (confirm != null && abs(match.first + (confirm.second - match.second) / 1_000_000.0 - confirm.first) <= AGREE_MS) {
                            misses = 0
                            publish(confirm)
                        } else {
                            misses++
                        }
                    }
                }
            }
            if (misses >= MAX_MISSES) {
                _state.value = LiveLyricsState.Unavailable("Lost the music: tap a line to jump")
                return@coroutineScope
            }
            val position = (_state.value as? LiveLyricsState.Live)?.positionNow() ?: return@coroutineScope
            if (song.duration > 0 && position > song.duration + 2_000) {
                _state.value = LiveLyricsState.Unavailable("The song has ended")
                return@coroutineScope
            }
        }
    }

    private fun publish(anchor: Pair<Double, Long>) {
        _state.value = LiveLyricsState.Live(anchor.first.toLong(), anchor.second)
    }

    /** (song ms at the clip's first sample, the clip's first sample in nanoTime), if sure. */
    private fun matchOf(clip: Clip, song: AudioFeatures): Pair<Double, Long>? {
        val match = AudioAligner.align(AudioFingerprint.features(clip.samples), song) ?: return null
        Timber.tag(TAG).d("match %.0f ms score=%.2f z=%.1f runnerUp=%.2f", match.offsetMs, match.score, match.zScore, match.runnerUpRatio)
        return if (match.isConfident) match.offsetMs to clip.startNanos else null
    }

    private fun matchNear(clip: Clip, song: AudioFeatures, expectedMs: Double): Pair<Double, Long>? {
        val match = AudioAligner.alignNear(AudioFingerprint.features(clip.samples), song, expectedMs, NEAR_WINDOW_MS) ?: return null
        return if (match.isConfident) match.offsetMs to clip.startNanos else null
    }

    // ---- Microphone ---------------------------------------------------------------------------

    private class Clip(val samples: FloatArray, val startNanos: Long) {
        val isMusic: Boolean get() = AudioFingerprint.rms(samples) > SILENCE_RMS
    }

    @SuppressLint("MissingPermission") // checked in start()
    private suspend fun listen(seconds: Double = LISTEN_SECONDS): Clip = withContext(Dispatchers.IO) {
        val rate = AudioFingerprint.SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
        val record = runCatching {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, rate))
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            ?: AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, rate))
        val total = (seconds * rate).toInt()
        val pcm = ShortArray(total)
        var startNanos = 0L
        try {
            record.startRecording()
            val fallbackStart = System.nanoTime()
            var read = 0
            while (read < total) {
                ensureActive()
                val n = record.read(pcm, read, minOf(4096, total - read))
                if (n <= 0) break
                read += n
            }
            // Exact time of sample 0 from the recorder's own clock when available.
            val ts = AudioTimestamp()
            startNanos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                record.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS
            ) {
                ts.nanoTime - ts.framePosition * 1_000_000_000L / rate
            } else fallbackStart
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        Clip(FloatArray(total) { pcm[it] / 32768f }, startNanos)
    }

    // ---- The song's own audio -----------------------------------------------------------------

    private suspend fun referenceFeatures(song: Song): AudioFeatures {
        synchronized(featureCache) { featureCache[song.id] }?.let { return it }
        val uri = engine.get().resolveCloudUri(Uri.parse(song.contentUriString.ifBlank { song.path }))
        val samples = decodeMono16k(uri)
        val features = AudioFingerprint.features(samples)
        synchronized(featureCache) { featureCache[song.id] = features }
        return features
    }

    /** Decodes [uri] to 16 kHz mono floats (a 4-minute song is ~15 MB, freed right after). */
    private suspend fun decodeMono16k(uri: Uri): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        when (uri.scheme) {
            "http", "https" -> extractor.setDataSource(uri.toString())
            null, "" -> extractor.setDataSource(uri.toString()) // a plain file path
            "file" -> extractor.setDataSource(uri.path ?: uri.toString())
            else -> extractor.setDataSource(context, uri, null)
        }
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("No audio track")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val out = FloatGrowable()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                ensureActive()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        if (floatPcm) {
                            val fb = buffer.asFloatBuffer()
                            while (fb.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += fb.get() }
                                out.add(sum / channels)
                            }
                        } else {
                            val sb = buffer.asShortBuffer()
                            while (sb.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += sb.get() / 32768f }
                                out.add(sum / channels)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        resample(out.toArray(), sampleRate, AudioFingerprint.SAMPLE_RATE)
    }

    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || input.isEmpty()) return input
        val ratio = from.toDouble() / to
        val n = (input.size / ratio).toInt()
        // Average over each step (a cheap low-pass) rather than picking samples.
        return FloatArray(n) { i ->
            val start = i * ratio
            val end = minOf(input.size.toDouble(), start + ratio)
            var sum = 0f
            var count = 0
            var k = start.toInt()
            while (k < end) { sum += input[k]; count++; k++ }
            if (count == 0) input[minOf(start.toInt(), input.size - 1)] else sum / count
        }
    }

    private class FloatGrowable {
        private var data = FloatArray(1 shl 20)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }

    private companion object {
        const val TAG = "AmbientLyricsSync"
        const val LISTEN_SECONDS = 6.0
        /** Two listens must agree this closely before lyrics go live. */
        const val AGREE_MS = 150.0
        /** Larger drift than this while live is treated as a jump and re-confirmed. */
        const val RESYNC_MS = 400.0
        const val RECHECK_EVERY_MS = 15_000L
        const val MAX_LOCK_ATTEMPTS = 4
        const val MAX_MISSES = 2
        const val REFERENCE_TIMEOUT_MS = 45_000L
        const val SILENCE_RMS = 0.004f
        /** Rechecks look this far either side of where the song should be. */
        const val NEAR_WINDOW_MS = 1_000.0
    }
}
