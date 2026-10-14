package com.theveloper.pixelplay.data.ai.gemma

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.theveloper.pixelplay.data.network.NetworkAccessPolicy
import com.theveloper.pixelplay.data.network.NetworkDecision
import com.theveloper.pixelplay.data.network.NetworkPurpose
import com.theveloper.pixelplay.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Which on-device engine answers AI requests. */
enum class OnDeviceEngine { GEMINI_NANO, GEMMA }

/** One model's download, for the AI settings cards. */
data class GemmaDownloadState(
    val status: Status = Status.NOT_DOWNLOADED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    /** Why it stopped, in plain words; null when nothing went wrong. */
    val message: String? = null,
) {
    enum class Status { NOT_DOWNLOADED, DOWNLOADING, PAUSED, READY, FAILED }

    val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * Downloads, keeps and removes the Gemma 4 phone models ([GemmaModel]), and remembers which
 * engine the user picked (Gemini Nano or Gemma) and which Gemma model.
 *
 * Downloads run in the app scope (they carry on when you leave Settings), resume from where they
 * stopped (HTTP Range), go through [NetworkAccessPolicy] (offline mode blocks them) and, with
 * "Wi-Fi only" on (the default), pause when the phone leaves Wi-Fi. A finished file is checked
 * against the size the server reported before it's marked ready.
 */
@Singleton
class GemmaModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val networkPolicy: NetworkAccessPolicy,
) {
    private val directory = File(context.noBackupFilesDir, "gemma-models")
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val jobs = HashMap<GemmaModel, Job>()

    private val _states = MutableStateFlow(GemmaModel.entries.associateWith { initialState(it) })
    val states: StateFlow<Map<GemmaModel, GemmaDownloadState>> = _states.asStateFlow()

    private val _engine = MutableStateFlow(
        runCatching { OnDeviceEngine.valueOf(prefs.getString(KEY_ENGINE, null) ?: "") }.getOrDefault(OnDeviceEngine.GEMINI_NANO)
    )
    val engine: StateFlow<OnDeviceEngine> = _engine.asStateFlow()

    private val _selected = MutableStateFlow(GemmaModel.byId(prefs.getString(KEY_MODEL, null)) ?: GemmaModel.recommendedFor(context))
    val selected: StateFlow<GemmaModel> = _selected.asStateFlow()

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun setEngine(engine: OnDeviceEngine) {
        _engine.value = engine
        prefs.edit().putString(KEY_ENGINE, engine.name).apply()
    }

    fun select(model: GemmaModel) {
        _selected.value = model
        prefs.edit().putString(KEY_MODEL, model.id).apply()
    }

    fun setWifiOnly(on: Boolean) {
        _wifiOnly.value = on
        prefs.edit().putBoolean(KEY_WIFI_ONLY, on).apply()
    }

    fun modelFile(model: GemmaModel): File = File(directory, model.fileName)
    private fun partFile(model: GemmaModel): File = File(directory, model.fileName + ".part")

    fun isReady(model: GemmaModel): Boolean = modelFile(model).let { it.isFile && it.length() > 0 }

    /** The Gemma model to answer with: Gemma picked, and the selected model on the phone. */
    fun activeModel(): GemmaModel? =
        _selected.value.takeIf { _engine.value == OnDeviceEngine.GEMMA && isReady(it) }

    /** Space the models use on the phone (finished + partial files). */
    fun storageBytes(model: GemmaModel): Long =
        (modelFile(model).takeIf { it.isFile }?.length() ?: 0L) + (partFile(model).takeIf { it.isFile }?.length() ?: 0L)

    fun download(model: GemmaModel) {
        if (jobs[model]?.isActive == true || isReady(model)) return
        jobs[model] = appScope.launch(Dispatchers.IO) {
            try {
                fetch(model)
            } catch (e: CancellationException) {
                setState(model, GemmaDownloadState(GemmaDownloadState.Status.PAUSED, partBytes(model), model.sizeBytes))
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Gemma download failed")
                setState(model, GemmaDownloadState(GemmaDownloadState.Status.FAILED, partBytes(model), model.sizeBytes, "Download stopped. Tap to resume"))
            }
        }
    }

    /** Pauses a running download (the partial file is kept; Download resumes it). */
    fun pause(model: GemmaModel) {
        jobs.remove(model)?.cancel()
    }

    fun delete(model: GemmaModel) {
        pause(model)
        runCatching { modelFile(model).delete() }
        runCatching { partFile(model).delete() }
        setState(model, GemmaDownloadState())
    }

    private fun initialState(model: GemmaModel): GemmaDownloadState = when {
        isReady(model) -> GemmaDownloadState(GemmaDownloadState.Status.READY, modelFile(model).length(), modelFile(model).length())
        partFile(model).let { it.isFile && it.length() > 0 } ->
            GemmaDownloadState(GemmaDownloadState.Status.PAUSED, partFile(model).length(), model.sizeBytes)
        else -> GemmaDownloadState(totalBytes = model.sizeBytes)
    }

    private fun setState(model: GemmaModel, state: GemmaDownloadState) {
        _states.update { it + (model to state) }
    }

    private fun partBytes(model: GemmaModel): Long = partFile(model).takeIf { it.isFile }?.length() ?: 0L

    private suspend fun fetch(model: GemmaModel) = withContext(Dispatchers.IO) {
        if (networkPolicy.getDecision(NetworkPurpose.Update) != NetworkDecision.Allowed) {
            setState(model, GemmaDownloadState(GemmaDownloadState.Status.FAILED, partBytes(model), model.sizeBytes, "Offline mode is on"))
            return@withContext
        }
        if (!networkOk()) {
            setState(model, GemmaDownloadState(GemmaDownloadState.Status.PAUSED, partBytes(model), model.sizeBytes,
                if (_wifiOnly.value) "Waiting for Wi-Fi" else "No internet connection"))
            return@withContext
        }
        directory.mkdirs()
        val part = partFile(model)
        val existing = part.takeIf { it.isFile }?.length() ?: 0L
        val connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PixelPlayer (Android)")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false
                416 -> { part.delete(); setState(model, GemmaDownloadState(GemmaDownloadState.Status.FAILED, 0, model.sizeBytes, "Download restarted. Tap to try again")); return@withContext }
                else -> {
                    setState(model, GemmaDownloadState(GemmaDownloadState.Status.FAILED, existing, model.sizeBytes, "Server error ($code). Try again later"))
                    return@withContext
                }
            }
            val startAt = if (append) existing else 0L
            val remaining = connection.contentLengthLong
            val total = if (remaining > 0) startAt + remaining else model.sizeBytes
            setState(model, GemmaDownloadState(GemmaDownloadState.Status.DOWNLOADING, startAt, total))
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var written = startAt
                    var lastReported = written
                    while (true) {
                        coroutineContext.ensureActive()
                        if (!networkOk()) {
                            setState(model, GemmaDownloadState(GemmaDownloadState.Status.PAUSED, written, total,
                                if (_wifiOnly.value) "Paused: waiting for Wi-Fi" else "Paused: no internet connection"))
                            return@withContext
                        }
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                        if (written - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = written
                            setState(model, GemmaDownloadState(GemmaDownloadState.Status.DOWNLOADING, written, total))
                        }
                    }
                }
            }
            val finalSize = part.length()
            if (remaining > 0 && finalSize != total) {
                setState(model, GemmaDownloadState(GemmaDownloadState.Status.PAUSED, finalSize, total, "Download stopped early. Tap to resume"))
                return@withContext
            }
            val target = modelFile(model)
            if (!part.renameTo(target)) {
                setState(model, GemmaDownloadState(GemmaDownloadState.Status.FAILED, finalSize, total, "Couldn't save the model"))
                return@withContext
            }
            setState(model, GemmaDownloadState(GemmaDownloadState.Status.READY, target.length(), target.length()))
            Timber.tag(TAG).i("%s ready (%d bytes)", model.displayName, target.length())
        } finally {
            connection.disconnect()
        }
    }

    private fun networkOk(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !_wifiOnly.value || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private companion object {
        const val TAG = "GemmaModels"
        const val PREFS = "gemma_models"
        const val KEY_ENGINE = "engine"
        const val KEY_MODEL = "model"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val PROGRESS_STEP_BYTES = 4L * 1024 * 1024
    }
}
