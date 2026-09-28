package com.theveloper.pixelplay.data.soundfont

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The recorded-instrument bank used by the tab Synth and the notation demos: GeneralUser GS
 * 2.0.3 by S. Christian Collins (free for any use, see its license). It isn't shipped in the APK;
 * it's downloaded once (about 31 MB) from this app's own release, checked against its SHA-256,
 * and kept in app storage. Until then the older engines keep working.
 */
object SoundFontStore {
    private const val TAG = "SoundFontStore"

    /** This app's own copy (the author asks not to link his download files directly). */
    const val DOWNLOAD_URL =
        "https://github.com/KinsBand/PixelPlayer/releases/download/soundfont-generaluser-gs-2.0.3/GeneralUser-GS.sf2"
    private const val SHA256 = "9575028c7a1f589f5770fccc8cff2734566af40cd26ed836944e9a5152688cfe"
    const val SIZE_BYTES = 32_319_396L
    private const val FILE_NAME = "GeneralUser-GS-2.0.3.sf2"

    sealed interface State {
        data object Missing : State
        data class Downloading(val progress: Float) : State
        /** File present; [SoundFont] loading or loaded. */
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var download: Job? = null
    private val loadMutex = Mutex()
    @Volatile private var font: SoundFont? = null
    @Volatile private var checked = false

    private fun file(context: Context) = File(File(context.applicationContext.filesDir, "soundfonts"), FILE_NAME)

    /** The loaded bank, or null if it isn't downloaded / loaded yet. Never blocks. */
    fun fontOrNull(): SoundFont? = font

    /**
     * Looks for the downloaded file once and starts loading it in the background, so the first
     * Synth play doesn't wait. Cheap to call often.
     */
    fun prepare(context: Context) {
        if (checked) return
        checked = true
        val f = file(context)
        if (f.isFile && f.length() == SIZE_BYTES) {
            _state.value = State.Ready
            scope.launch { load(context) }
        }
    }

    /** Loads (maps and indexes) the bank; null if it isn't downloaded or is unreadable. */
    suspend fun load(context: Context): SoundFont? = withContext(Dispatchers.IO) {
        font?.let { return@withContext it }
        loadMutex.withLock {
            font?.let { return@withLock it }
            val f = file(context)
            if (!f.isFile) return@withLock null
            runCatching { SoundFont.load(f) }
                .onSuccess { font = it; _state.value = State.Ready }
                .onFailure {
                    Log.w(TAG, "SoundFont unreadable, removing it: ${it.message}")
                    f.delete()
                    _state.value = State.Failed("The instrument sounds were damaged. Download them again.")
                }
                .getOrNull()
        }
    }

    /** Downloads the bank (no-op while a download runs or once it's there). [from] is for tests. */
    fun download(context: Context, from: String = DOWNLOAD_URL) {
        if (download?.isActive == true || font != null) return
        val app = context.applicationContext
        download = scope.launch {
            val target = file(app)
            if (target.isFile && target.length() == SIZE_BYTES) {
                load(app)
                return@launch
            }
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, "$FILE_NAME.part")
            _state.value = State.Downloading(0f)
            try {
                var url = URL(from)
                var conn: HttpURLConnection
                var hops = 0
                // GitHub release downloads redirect to a storage host; follow at most a few hops.
                while (true) {
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "PixelPlayer")
                    }
                    val code = conn.responseCode
                    if (code in 300..399 && hops < 5) {
                        val next = conn.getHeaderField("Location") ?: error("Redirect without a location")
                        conn.disconnect()
                        url = URL(url, next)
                        hops++
                        continue
                    }
                    if (code != 200) error("Server replied $code")
                    break
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var got = 0L
                conn.inputStream.use { input ->
                    part.outputStream().buffered(1 shl 16).use { out ->
                        val buf = ByteArray(1 shl 16)
                        var lastReport = 0L
                        while (isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            got += n
                            if (got - lastReport > 256 * 1024) {
                                lastReport = got
                                _state.value = State.Downloading((got.toFloat() / SIZE_BYTES).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                conn.disconnect()
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (got != SIZE_BYTES || hash != SHA256) {
                    part.delete()
                    error("The download was incomplete or damaged")
                }
                if (!part.renameTo(target)) error("Couldn't save the instrument sounds")
                if (load(app) == null) error("Couldn't open the instrument sounds")
            } catch (e: Exception) {
                part.delete()
                Log.w(TAG, "SoundFont download failed: ${e.message}")
                _state.value = State.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Download failed")
            }
        }
    }

    /** Deletes the downloaded bank (frees about 31 MB). */
    fun delete(context: Context) {
        download?.cancel()
        font = null
        file(context).delete()
        _state.value = State.Missing
    }
}
