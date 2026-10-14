package com.theveloper.pixelplay.data.radio.timeshift

import android.content.Context
import com.theveloper.pixelplay.di.YouTubeOkHttpClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Records the radio station you're listening to, so you can rewind (up to
 * [RadioTimeShift.MAX_REWIND_MS]). It owns the station's one HTTP connection: the audio goes to
 * minute-long segment files in the cache, and the player reads them back
 * ([RadioTimeShiftDataSource]), live at the end of the recording or anywhere earlier.
 *
 * It also reads the stream's ICY titles ("Artist - Title") and keeps them with the exact
 * position in the recording where they start, so the radio timeline can colour each song.
 *
 * The recording is deleted when you leave the station, and any left over (a crash) is deleted
 * the next time the app starts. Uses the streaming OkHttp client (no User-Agent rewriting, no
 * http → https upgrade, which plain Icecast servers don't support); offline mode still blocks it.
 */
@Singleton
class RadioTimeShiftRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    @YouTubeOkHttpClient private val httpClient: OkHttpClient,
) {
    /** A stream title and where in the recording it starts. Empty [raw] = no song (ident, ad). */
    data class TitleEvent(val positionMs: Long, val raw: String)

    /** What the radio timeline needs, refreshed about twice a second while recording. */
    data class State(
        val streamUrl: String,
        /** The end of the recording (the live edge), in recording ms. */
        val liveEdgeMs: Long,
        /** The oldest point still kept. */
        val firstAvailableMs: Long,
        /** MP3 / AAC streams can be started anywhere; other formats only play live. */
        val seekable: Boolean,
        val failed: Boolean,
    )

    /** One recorded station. Fields shared with the player thread are guarded by [lock]. */
    class Session internal constructor(val streamUrl: String, val dir: File, bitrateKbps: Int) {
        internal val lock = Object()
        internal val index = RadioTimeShiftIndex(bitrateKbps, RadioTimeShift.MAX_REWIND_MS)
        @Volatile internal var ended = false
        /** Paused for a long time: the connection is closed; it reopens when the player needs more. */
        @Volatile internal var suspended = false
        @Volatile internal var failure: IOException? = null
        @Volatile internal var call: Call? = null
        @Volatile internal var contentType: String? = null
        internal var job: Job? = null
        internal val titles = CopyOnWriteArrayList<TitleEvent>()

        /** Where the player's current item started in the recording (ms). */
        @Volatile var itemStartMs: Long = 0L
            internal set
        @Volatile internal var itemStartOffset: Long = 0L

        internal fun segmentFile(segment: Long) = File(dir, "$segment.seg")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = File(context.cacheDir, "radio_timeshift")

    @Volatile private var session: Session? = null

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    private val _titles = MutableStateFlow<List<TitleEvent>>(emptyList())
    /** The current recording's titles, oldest first. */
    val titles: StateFlow<List<TitleEvent>> = _titles.asStateFlow()

    init {
        // Left over from a crash or a killed process: nothing can rewind into it any more.
        scope.launch {
            root.listFiles()?.forEach { dir ->
                if (dir.name != session?.dir?.name) dir.deleteRecursively()
            }
        }
    }

    /** The recording for [streamUrl], starting one if needed (called on the player's thread). */
    fun sessionFor(streamUrl: String): Session = synchronized(this) {
        session?.let { s -> if (s.streamUrl == streamUrl && !s.ended && s.failure == null) return s }
        session?.let { stopLocked(it) }
        val dir = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        val s = Session(streamUrl, dir, RadioTimeShift.bitrateFor(streamUrl))
        session = s
        _titles.value = emptyList()
        _state.value = State(streamUrl, 0L, 0L, seekable = false, failed = false)
        s.job = scope.launch { record(s) }
        s
    }

    /** The current recording, if it is for [streamUrl]. */
    fun current(streamUrl: String? = null): Session? =
        session?.takeIf { streamUrl == null || it.streamUrl == streamUrl }

    /**
     * Paused for a long while: close the connection (no data or battery used) but keep what's
     * recorded. The player reading past the end reopens it ([resumeIfSuspended]).
     */
    fun suspendRecording() {
        val s = session ?: return
        // Locks on the session object only (never with the recorder lock), so the player's
        // thread, which holds s.lock when it asks to resume, can't deadlock with sessionFor().
        synchronized(s) {
            if (s.ended || s.suspended) return
            s.suspended = true
            s.call?.cancel()
            s.job?.cancel()
        }
    }

    /** The player wants audio past the end of a suspended recording: reconnect. */
    internal fun resumeIfSuspended(s: Session) {
        synchronized(s) {
            if (!s.suspended || s.ended || session !== s) return
            s.suspended = false
            s.job = scope.launch { record(s) }
        }
    }

    /** Stops recording and deletes it (leaving the station, or radio stopped). */
    fun stop() = synchronized(this) {
        session?.let { stopLocked(it) }
        session = null
        _state.value = null
        _titles.value = emptyList()
    }

    private fun stopLocked(s: Session) {
        s.ended = true
        s.call?.cancel()
        s.job?.cancel()
        synchronized(s.lock) { s.lock.notifyAll() }
        scope.launch { s.dir.deleteRecursively() }
    }

    // ---- Recording (IO thread) -------------------------------------------------------------

    private suspend fun record(s: Session) {
        var attempt = 0
        while (currentCoroutineContext().isActive && !s.ended && !s.suspended) {
            try {
                val request = Request.Builder()
                    .url(s.streamUrl)
                    .header("Icy-MetaData", "1")
                    .header("User-Agent", USER_AGENT)
                    .build()
                val call = httpClient.newCall(request)
                s.call = call
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.header("icy-br")?.substringBefore(',')?.trim()?.toIntOrNull()?.let { kbps ->
                        synchronized(s.lock) { s.index.setBitrateIfEmpty(kbps) }
                    }
                    s.contentType = response.header("Content-Type")
                    val metaInt = response.header("icy-metaint")?.trim()?.toIntOrNull() ?: 0
                    attempt = 0
                    pump(s, response.body.byteStream(), metaInt)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (s.ended || s.suspended) return
                attempt++
                Timber.tag(TAG).w("Radio recording dropped (attempt %d): %s", attempt, e.message)
                if (attempt > MAX_RECONNECTS) {
                    s.failure = e
                    synchronized(s.lock) { s.lock.notifyAll() }
                    publish(s, force = true)
                    return
                }
                delay((RECONNECT_BASE_MS shl (attempt - 1)).coerceAtMost(RECONNECT_MAX_MS))
            }
        }
    }

    private suspend fun pump(s: Session, input: InputStream, metaInt: Int) {
        val buffer = ByteArray(16 * 1024)
        var untilMeta = metaInt
        var segment = -1L
        var out: FileOutputStream? = null
        var lastPublish = 0L
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                if (s.ended || s.suspended) return
                val want = if (metaInt > 0) minOf(buffer.size, untilMeta) else buffer.size
                val n = input.read(buffer, 0, want)
                if (n < 0) throw IOException("Stream ended")
                // Write, splitting at segment boundaries.
                var done = 0
                while (done < n) {
                    val (seg, room) = synchronized(s.lock) {
                        val w = s.index.written
                        val sg = s.index.segmentOf(w)
                        sg to (s.index.segmentStart(sg + 1) - w)
                    }
                    if (seg != segment) {
                        out?.close()
                        out = FileOutputStream(s.segmentFile(seg), true)
                        segment = seg
                    }
                    val chunk = minOf((n - done).toLong(), room).toInt()
                    out!!.write(buffer, done, chunk)
                    done += chunk
                    val dropped = synchronized(s.lock) {
                        val d = s.index.onWritten(chunk)
                        s.lock.notifyAll()
                        d
                    }
                    dropped.forEach { s.segmentFile(it).delete() }
                }
                if (metaInt > 0) {
                    untilMeta -= n
                    if (untilMeta == 0) {
                        readMetadata(s, input)
                        untilMeta = metaInt
                    }
                }
                val now = System.currentTimeMillis()
                if (now - lastPublish >= PUBLISH_EVERY_MS) {
                    lastPublish = now
                    publish(s)
                }
            }
        } finally {
            out?.close()
        }
    }

    /** One ICY metadata block: a length byte (× 16), then "StreamTitle='…';StreamUrl='…';". */
    private fun readMetadata(s: Session, input: InputStream) {
        val lengthByte = input.read()
        if (lengthByte < 0) throw IOException("Stream ended")
        val length = lengthByte * 16
        if (length == 0) return
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n < 0) throw IOException("Stream ended")
            read += n
        }
        val raw = parseStreamTitle(bytes) ?: return
        if (s.titles.lastOrNull()?.raw == raw) return
        val position = synchronized(s.lock) { s.index.liveEdgeMs }
        s.titles += TitleEvent(position, raw)
        if (session === s) _titles.value = s.titles.toList()
    }

    private fun publish(s: Session, force: Boolean = false) {
        if (session !== s && !force) return
        val (edge, first) = synchronized(s.lock) { s.index.liveEdgeMs to s.index.firstAvailableMs }
        val type = s.contentType?.lowercase().orEmpty()
        _state.value = State(
            streamUrl = s.streamUrl,
            liveEdgeMs = edge,
            firstAvailableMs = first,
            seekable = SEEKABLE_TYPES.any { type.contains(it) },
            failed = s.failure != null,
        )
    }

    companion object {
        private const val TAG = "RadioTimeShift"
        private const val USER_AGENT = "PixelPlayer/1.0 (Android) radio"
        private const val MAX_RECONNECTS = 6
        private const val RECONNECT_BASE_MS = 500L
        private const val RECONNECT_MAX_MS = 8_000L
        private const val PUBLISH_EVERY_MS = 500L
        /** Formats the extractors can pick up from any byte (frame sync): MP3 and ADTS AAC. */
        private val SEEKABLE_TYPES = listOf("mpeg", "mp3", "aac", "aacp")

        /** "StreamTitle='Artist - Title';…" → "Artist - Title" ("" when the title is empty). */
        internal fun parseStreamTitle(block: ByteArray): String? {
            val end = block.indexOfFirst { it == 0.toByte() }.let { if (it < 0) block.size else it }
            val utf8 = String(block, 0, end, Charsets.UTF_8)
            // Many servers send Latin-1: fall back when UTF-8 decoding hit invalid bytes.
            val text = if (utf8.contains('\uFFFD')) String(block, 0, end, Charsets.ISO_8859_1) else utf8
            val key = "StreamTitle='"
            val start = text.indexOf(key).takeIf { it >= 0 } ?: return null
            val from = start + key.length
            val close = text.indexOf("';", from).let { if (it < 0) text.lastIndexOf('\'') else it }
            if (close < from) return ""
            return text.substring(from, close).trim()
        }

        /** For code that can't take constructor injection (the player's data source, Compose). */
        fun get(context: Context): RadioTimeShiftRecorder =
            EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).radioTimeShiftRecorder()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun radioTimeShiftRecorder(): RadioTimeShiftRecorder
    }
}
