package com.theveloper.pixelplay.data.lyrics.align

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads and verifies the alignment model.
 *
 * The model is `facebook/wav2vec2-base-960h` (Apache-2.0), an English speech model with a
 * letter vocabulary, in the int8 ONNX export published by Xenova on Hugging Face. About 95 MB.
 * It is fetched once, resumed if interrupted, and checked against a pinned SHA-256 before use:
 * a file that doesn't match is deleted and never loaded.
 *
 * Networks: the automatic download (the first time a song needs it) runs on Wi-Fi, or on mobile
 * data too when [allowMobileData] is on. [downloadNow] is the user's own "Download" button in
 * Settings and runs on any connection, Wi-Fi or mobile data.
 *
 * This is a speech model, not one trained on singing (the plan's fine-tuned model is a later
 * step), so every result goes through [LyricsAlignmentBuilder]'s checks before it is shown.
 */
class AlignmentModelManager(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, "lyrics-align-models")
    private val modelFile = File(directory, MODEL_FILE)
    private val partFile = File(directory, "$MODEL_FILE.part")
    private val lock = Mutex()

    /** Lets the automatic download use mobile data as well as Wi-Fi (Settings switch). */
    @Volatile
    var allowMobileData: Boolean = false

    /** Where the model is, for the Settings row. */
    enum class Status { NOT_DOWNLOADED, DOWNLOADING, PAUSED, READY, FAILED }

    /** [downloadedBytes] of [totalBytes] (an estimate until the server reports the size). */
    data class ModelState(
        val status: Status,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = MODEL_BYTES_APPROX,
        /** Why the last attempt stopped, in plain words; null when nothing went wrong. */
        val message: String? = null,
    ) {
        val isReady: Boolean get() = status == Status.READY
        val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private fun initialState(): ModelState = when {
        isReady() -> ModelState(Status.READY, modelFile.length(), modelFile.length())
        partFile.isFile && partFile.length() > 0 -> ModelState(Status.PAUSED, partFile.length())
        else -> ModelState(Status.NOT_DOWNLOADED)
    }

    /** Bumped whenever the model (or anything that changes its output) changes; part of every cache key. */
    val modelVersion: String get() = MODEL_VERSION

    fun isReady(): Boolean = modelFile.isFile && modelFile.length() > 0

    fun modelFileOrNull(): File? = modelFile.takeIf { isReady() }

    /** Bytes the model uses on disk (including a partial download). */
    fun storageBytes(): Long = (if (modelFile.isFile) modelFile.length() else 0L) + (if (partFile.isFile) partFile.length() else 0L)

    fun delete() {
        runCatching { modelFile.delete() }
        runCatching { partFile.delete() }
        _state.value = ModelState(Status.NOT_DOWNLOADED)
    }

    /**
     * The model file, downloading it first when needed and allowed. Returns null when it isn't
     * there and can't be fetched now (metered network, offline, checksum mismatch); call again later.
     */
    suspend fun ensure(): File? {
        modelFileOrNull()?.let { return it }
        if (!autoDownloadAllowed()) {
            Timber.tag(TAG).d("Alignment model missing; waiting for Wi-Fi (or mobile data if allowed)")
            return null
        }
        return lock.withLock { modelFileOrNull() ?: fetch(anyNetwork = false) }
    }

    /**
     * The user tapped Download: fetch the model now over Wi-Fi or mobile data, whichever is
     * connected. Returns the file, or null if offline / interrupted / the checksum failed
     * ([state] says which). Cancelling the calling coroutine pauses it; the next call resumes.
     */
    suspend fun downloadNow(): File? {
        modelFileOrNull()?.let { return it }
        if (!isOnline()) {
            _state.value = _state.value.copy(status = Status.FAILED, message = "No internet connection")
            return null
        }
        return lock.withLock { modelFileOrNull() ?: fetch(anyNetwork = true) }
    }

    /** Wi-Fi (unmetered), or any connection when [allowMobileData] is on. */
    fun autoDownloadAllowed(): Boolean = isUnmetered() || (allowMobileData && isOnline())

    private suspend fun fetch(anyNetwork: Boolean): File? =
        withContext(Dispatchers.IO) {
            try {
                download(anyNetwork).also { file ->
                    if (file == null && _state.value.status == Status.DOWNLOADING) {
                        _state.value = _state.value.copy(status = Status.PAUSED)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = ModelState(Status.PAUSED, partBytes())
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Alignment model download failed")
                _state.value = ModelState(Status.FAILED, partBytes(), message = "Download interrupted. Tap to try again")
                null
            }
        }

    private fun partBytes(): Long = if (partFile.isFile) partFile.length() else 0L

    private suspend fun download(anyNetwork: Boolean): File? {
        directory.mkdirs()
        var existing = if (partFile.isFile) partFile.length() else 0L
        if (existing >= MODEL_BYTES_MAX) { partFile.delete(); existing = 0L }

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false
                416 -> { // stale partial file: start over next time
                    partFile.delete()
                    _state.value = ModelState(Status.FAILED, message = "Download interrupted. Tap to try again")
                    return null
                }
                else -> {
                    Timber.tag(TAG).w("Model download HTTP %d", code)
                    _state.value = ModelState(Status.FAILED, partBytes(), message = "Server error ($code). Try again later")
                    return null
                }
            }
            val remaining = connection.contentLengthLong
            val startAt = if (append) existing else 0L
            val total = if (remaining > 0) startAt + remaining else MODEL_BYTES_APPROX
            _state.value = ModelState(Status.DOWNLOADING, startAt, total)
            connection.inputStream.use { input ->
                FileOutputStream(partFile, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = startAt
                    var lastReported = written
                    while (true) {
                        coroutineContext.ensureActive()
                        val networkOk = if (anyNetwork) isOnline() else autoDownloadAllowed()
                        if (!networkOk) {
                            Timber.tag(TAG).d("Network not allowed any more; pausing model download at %d bytes", written)
                            _state.value = ModelState(
                                Status.PAUSED, written, total,
                                message = if (anyNetwork) "Paused: no internet connection" else "Paused: waiting for Wi-Fi",
                            )
                            return null
                        }
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                        if (written > MODEL_BYTES_MAX) {
                            partFile.delete()
                            _state.value = ModelState(Status.FAILED, message = "Unexpected file size. Try again later")
                            return null
                        }
                        if (written - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = written
                            _state.value = ModelState(Status.DOWNLOADING, written, maxOf(total, written))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        val hash = sha256(partFile)
        if (!hash.equals(MODEL_SHA256, ignoreCase = true)) {
            Timber.tag(TAG).w("Alignment model checksum mismatch (%s); deleting", hash)
            partFile.delete()
            _state.value = ModelState(Status.FAILED, message = "The download was damaged. Tap to try again")
            return null
        }
        if (!partFile.renameTo(modelFile)) {
            _state.value = ModelState(Status.FAILED, message = "Couldn't save the model. Check free space")
            return null
        }
        Timber.tag(TAG).i("Alignment model ready (%d bytes)", modelFile.length())
        _state.value = ModelState(Status.READY, modelFile.length(), modelFile.length())
        return modelFile
    }

    /** Any working connection: Wi-Fi, mobile data or Ethernet. */
    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** True on mobile data (or any metered connection) right now. */
    fun isOnMobileData(): Boolean = isOnline() && !isUnmetered()

    fun isUnmetered(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "LyricsAlignModel"
        const val MODEL_VERSION = "w2v2-base-960h-int8-v1"
        private const val MODEL_FILE = "wav2vec2-base-960h-int8.onnx"
        private const val MODEL_URL =
            "https://huggingface.co/Xenova/wav2vec2-base-960h/resolve/main/onnx/model_quantized.onnx"
        private const val MODEL_SHA256 = "cd5040c147381580ed73258143dd8e0c28e800a09e74ee42ee2b3e8cb4d760a3"
        /** Sanity bound on the download (the file is ≈95 MB). */
        private const val MODEL_BYTES_MAX = 160L * 1024 * 1024
        /** Shown before the server reports the real size. */
        const val MODEL_BYTES_APPROX = 95L * 1024 * 1024
        private const val PROGRESS_STEP_BYTES = 512L * 1024
    }
}
