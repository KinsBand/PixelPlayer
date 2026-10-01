package com.theveloper.pixelplay.data.lyrics.autosync

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.theveloper.pixelplay.data.analysis.PcmDecoder
import com.theveloper.pixelplay.data.diagnostics.HeapPressure
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Automatic lyrics timing: measures how far a song's synced lyrics are from its actual singing
 * and publishes the correction to [LyricsAutoOffsets].
 *
 * - Runs once per song and lyric version. The result (applied, already in sync, or unsure) is
 *   saved in `noBackupFilesDir/lyrics-auto-offset-v1/`, so a song is never analysed twice unless
 *   its lyric timing changes.
 * - Works for local, downloaded and streamed songs without downloading any song twice: see
 *   [LyricsAudioSources] for where the audio is read from.
 * - One analysis at a time, in the background. A saved result applies at once; a new
 *   measurement starts 2 s after the song's lyrics load, so skipping through songs costs
 *   nothing. Moving to another song cancels the unfinished analysis of the previous one and
 *   of the look-ahead for the next song.
 * - Roughly 1–2 s of background CPU per song on a phone (decode + features; about 0.3 s on a
 *   desktop JVM), ~3 MB of transient memory, and skipped while [HeapPressure] reports the heap
 *   is under pressure.
 * - No model, no network: see [VocalFeatureExtractor], [VocalActivity], [LyricsOffsetEstimator].
 */
class LyricsAutoSync private constructor(
    context: Context,
    /** Finds a song's audio (local, downloaded, stream cache, network); null in unit tests. */
    private val audioSources: () -> LyricsAudioSources?
) {

    private val appContext = context.applicationContext

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val directory = File(appContext.noBackupFilesDir, "lyrics-auto-offset-v1")
    private val inFlight = ConcurrentHashMap<String, Job>()

    /** Decoding hops to the IO dispatcher, so this (not the dispatcher) keeps analyses one at a time. */
    private val analysisLock = Mutex()

    @Volatile
    private var lookAhead: Job? = null

    @Volatile
    private var current: Job? = null

    /** Why the last analysis of a song ended the way it did (for logs and diagnostics). */
    data class Record(
        val songId: String,
        val fingerprint: String,
        val verdict: OffsetEstimate.Verdict,
        val offsetMs: Int,
        val measuredOffsetMs: Int,
        val confidence: Float,
        val reason: String,
        val analysedAt: Long
    )

    /**
     * Makes sure [song]'s automatic offset matches [lyrics]: publishes the saved result, or
     * measures it. Cheap to call on every lyrics load. [lookAhead] requests (the next song in the
     * queue) run only when nothing more urgent is waiting and give way to the current song.
     */
    fun request(song: Song, lyrics: Lyrics?, lookAhead: Boolean = false) {
        val synced = lyrics?.synced
        val starts = if (synced.isNullOrEmpty()) LongArray(0) else LyricsOffsetEstimator.lineStarts(synced)
        if (starts.size < LyricsOffsetEstimator.Config().minLines) {
            LyricsAutoOffsets.remove(song.id)
            return
        }
        val fingerprint = fingerprint(starts)
        val published = LyricsAutoOffsets.get(song.id)
        if (published != null) {
            if (published.lyricsFingerprint == fingerprint) return
            // The lyrics changed (e.g. upgraded to another source): the old offset no longer applies.
            LyricsAutoOffsets.remove(song.id)
        }
        val key = "${song.id}|$fingerprint"
        if (inFlight[key]?.isActive == true) return
        if (!lookAhead) {
            // The user moved on: stop working on the previous song and on the look-ahead.
            this.lookAhead?.takeIf { it.isActive }?.cancel()
            this.current?.takeIf { it.isActive }?.cancel()
        }

        val job = scope.launch {
            try {
                readRecord(song.id)?.takeIf { it.fingerprint == fingerprint }?.let {
                    publish(it)
                    return@launch
                }
                // Don't start decoding while the user is still skipping through songs.
                if (!lookAhead) delay(SETTLE_MS)
                if (song.duration > MAX_SONG_MS) {
                    val skipped = record(song, fingerprint, OffsetEstimate.Verdict.UNSUPPORTED, "longer than ${MAX_SONG_MS / 60_000} min")
                    saveRecord(skipped)
                    publish(skipped)
                    return@launch
                }
                // May wait (outside the lock) for a streamed song's copy to be complete.
                val uri = obtainAudio(song, lookAhead) ?: return@launch
                val record = analysisLock.withLock { measure(song, uri, starts, fingerprint) } ?: return@launch
                saveRecord(record)
                publish(record)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Lyrics auto-sync failed for %s", song.id)
            } finally {
                coroutineContext[Job]?.let { inFlight.remove(key, it) }
            }
        }
        inFlight[key] = job
        if (lookAhead) this.lookAhead = job else this.current = job
    }

    /** Forgets the saved result for [songId] (e.g. when its lyrics are reset). */
    fun forget(songId: String) {
        LyricsAutoOffsets.remove(songId)
        runCatching { recordFile(songId).delete() }
    }

    private fun publish(record: Record) {
        if (record.verdict == OffsetEstimate.Verdict.APPLY && record.offsetMs != 0) {
            LyricsAutoOffsets.put(
                record.songId,
                LyricsAutoOffsets.Entry(record.offsetMs, record.confidence, record.fingerprint)
            )
        } else {
            LyricsAutoOffsets.remove(record.songId)
        }
    }

    /**
     * A decodable URI for [song]'s audio, or null to try again another time (nothing is saved).
     * Local, downloaded and stream-cached songs are immediate. A song streaming from YouTube is
     * read from the proxy's copy once playing has made it complete, so its audio is never
     * downloaded twice; the look-ahead doesn't wait for that. Other streams are read over the
     * network only on unmetered connections.
     */
    private suspend fun obtainAudio(song: Song, lookAhead: Boolean): Uri? {
        val sources = audioSources()
        val source = if (sources != null) {
            try {
                sources.resolve(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not find audio for %s", song.id)
                null
            }
        } else {
            localAudioUri(song)?.let { LyricsAudioSources.Source.Ready(it, "local") }
        } ?: return null

        return when (source) {
            is LyricsAudioSources.Source.Ready -> source.uri
            is LyricsAudioSources.Source.Network -> {
                if (sources?.isUnmetered() == true) source.uri
                else null.also { Timber.tag(TAG).d("%s streams over a metered network: waiting for Wi-Fi", song.id) }
            }
            is LyricsAudioSources.Source.StreamCache -> {
                val s = sources ?: return null
                s.cachedStreamFile(source.videoId)?.let { return Uri.fromFile(it) }
                if (s.completeStreamCache(source.videoId)) s.cachedStreamFile(source.videoId)?.let { return Uri.fromFile(it) }
                if (lookAhead) return null
                // Playing the song fills the cache. Check back until it's whole; moving to
                // another song cancels this, and the next play finds the finished copy.
                val deadline = System.currentTimeMillis() + minOf(song.duration + STREAM_WAIT_EXTRA_MS, MAX_STREAM_WAIT_MS)
                while (System.currentTimeMillis() < deadline) {
                    delay(STREAM_POLL_MS)
                    s.cachedStreamFile(source.videoId)?.let { return Uri.fromFile(it) }
                }
                Timber.tag(TAG).d("%s: stream copy never completed; will retry on a later play", song.id)
                null
            }
        }
    }

    private suspend fun measure(song: Song, uri: Uri, starts: LongArray, fingerprint: String): Record? {
        if (HeapPressure.isElevated() || HeapPressure.headroomBytes() < MIN_HEADROOM_BYTES) {
            Timber.tag(TAG).d("Skipping %s: heap under pressure", song.id)
            return null // not saved: retried next time
        }

        val started = System.nanoTime()
        var extractor: VocalFeatureExtractor? = null
        var rate = 0
        val delivered = PcmDecoder.stream(
            context = appContext,
            uri = uri,
            maxSeconds = (VocalFeatureExtractor.MAX_DURATION_MS / 1000).toInt()
        ) { samples, count, sampleRate ->
            val ex = extractor ?: runCatching {
                VocalFeatureExtractor(sampleRate, timeOriginMs = 0.0, expectedDurationMs = song.duration)
            }.getOrNull()?.also { extractor = it; rate = sampleRate } ?: return@stream false
            ex.push(samples, 0, count)
            !ex.isFull
        }
        val features = extractor?.takeIf { delivered != null }?.finish()
            ?: return if (uri.scheme == "http" || uri.scheme == "https") {
                null // a network hiccup: try again another time
            } else {
                record(song, fingerprint, OffsetEstimate.Verdict.UNSUPPORTED, "could not decode")
            }
        val decodedMs = (System.nanoTime() - started) / 1_000_000

        val estimate = LyricsOffsetEstimator.estimate(features, starts)
        val totalMs = (System.nanoTime() - started) / 1_000_000
        Timber.tag(TAG).i(
            "%s \"%s\": %s offset=%d ms (measured %d ms, confidence %.2f, peak %.1f, margin %.2f, halves %d ms, %d lines, %d Hz) in %d ms (decode+features %d ms) — %s",
            song.id, song.title, estimate.verdict, estimate.offsetMs, estimate.measuredOffsetMs,
            estimate.confidence, estimate.peakScore, estimate.margin, estimate.halvesDisagreementMs,
            estimate.linesUsed, rate, totalMs, decodedMs, estimate.reason
        )
        return Record(
            songId = song.id,
            fingerprint = fingerprint,
            verdict = estimate.verdict,
            offsetMs = estimate.offsetMs,
            measuredOffsetMs = estimate.measuredOffsetMs,
            confidence = estimate.confidence,
            reason = estimate.reason,
            analysedAt = System.currentTimeMillis()
        )
    }

    private fun record(song: Song, fingerprint: String, verdict: OffsetEstimate.Verdict, reason: String) =
        Record(song.id, fingerprint, verdict, 0, 0, 0f, reason, System.currentTimeMillis())

    /** A readable local file for [song], or null for streams (YouTube, Drive, other URLs). */
    private fun localAudioUri(song: Song): Uri? {
        val content = song.contentUriString
        if (content.startsWith("content://") || content.startsWith("file://")) return Uri.parse(content)
        val path = song.path
        if (path.isNotBlank() && !path.contains("://")) {
            val file = File(path)
            if (file.isFile && file.canRead()) return Uri.fromFile(file)
        }
        return null
    }

    // ─── Storage ───────────────────────────────────────────────────────────────────────

    private fun recordFile(songId: String) = File(directory, sha1(songId) + ".json")

    private fun readRecord(songId: String): Record? {
        val json = try {
            AtomicFile(recordFile(songId)).openRead().bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: Exception) {
            return null
        }
        if (json.optInt("v") != LyricsOffsetEstimator.VERSION || json.optString("songId") != songId) return null
        val verdict = runCatching { OffsetEstimate.Verdict.valueOf(json.optString("verdict")) }.getOrNull() ?: return null
        return Record(
            songId = songId,
            fingerprint = json.optString("fingerprint"),
            verdict = verdict,
            offsetMs = json.optInt("offset"),
            measuredOffsetMs = json.optInt("measured"),
            confidence = json.optDouble("confidence", 0.0).toFloat(),
            reason = json.optString("reason"),
            analysedAt = json.optLong("at")
        )
    }

    private fun saveRecord(record: Record) {
        directory.mkdirs()
        val atomic = AtomicFile(recordFile(record.songId))
        val out = try { atomic.startWrite() } catch (_: Exception) { return }
        try {
            val json = JSONObject()
                .put("v", LyricsOffsetEstimator.VERSION)
                .put("songId", record.songId)
                .put("fingerprint", record.fingerprint)
                .put("verdict", record.verdict.name)
                .put("offset", record.offsetMs)
                .put("measured", record.measuredOffsetMs)
                .put("confidence", record.confidence.toDouble())
                .put("reason", record.reason)
                .put("at", record.analysedAt)
            out.write(json.toString().toByteArray())
            atomic.finishWrite(out)
        } catch (_: Exception) {
            atomic.failWrite(out)
        }
    }

    private fun fingerprint(starts: LongArray): String =
        sha1("v${LyricsOffsetEstimator.VERSION}:" + starts.joinToString(","))

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "LyricsAutoSync"
        private const val MAX_SONG_MS = 20L * 60 * 1000
        private const val MIN_HEADROOM_BYTES = 48L * 1024 * 1024
        private const val SETTLE_MS = 2_000L
        private const val STREAM_POLL_MS = 10_000L
        private const val STREAM_WAIT_EXTRA_MS = 2L * 60 * 1000
        private const val MAX_STREAM_WAIT_MS = 20L * 60 * 1000

        @Volatile
        private var instance: LyricsAutoSync? = null

        /**
         * Installed once from `Application.onCreate`. [audioSources] is read lazily (it pulls in
         * the stream proxies), the first time a song is analysed.
         */
        fun install(context: Context, audioSources: () -> LyricsAudioSources? = { null }): LyricsAutoSync =
            instance ?: synchronized(this) {
                instance ?: LyricsAutoSync(context, audioSources).also { instance = it }
            }

        /** Null until [install] ran (e.g. in unit tests), which turns auto-sync into a no-op. */
        fun get(): LyricsAutoSync? = instance
    }
}
