package com.theveloper.pixelplay.data.ai.nano

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.theveloper.pixelplay.di.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NanoEngine] on the ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt`).
 *
 * - FAST model preference: every job here is short (read a prompt, translate a few lines).
 * - The fixed instructions go in as a [PromptPrefix] where AICore can cache them (much quicker
 *   repeat calls); elsewhere they're simply put in front of the input.
 * - Input is kept inside the model's token limit; anything longer is refused (null) rather than
 *   cut mid-sentence, so callers chunk their input.
 */
@Singleton
class MlKitNanoEngine @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
) : NanoEngine {

    /**
     * The client in use. AICore doesn't offer every model on every phone (e.g. a Pixel 10 Pro
     * may have no FAST/STABLE model while FULL or the default works), so [refreshStatus] tries
     * each of [candidates] in order and keeps the first one AICore can run.
     */
    private val candidates: List<Candidate> = listOf(
        Candidate("fast/stable") { configured(fast = true, preview = false) },
        Candidate("full/stable") { configured(fast = false, preview = false) },
        Candidate("default") { Generation.getClient() },
        Candidate("full/preview") { configured(fast = false, preview = true) },
        Candidate("fast/preview") { configured(fast = true, preview = true) },
    )

    @Volatile private var model: GenerativeModel = candidates.first().create()
    @Volatile private var modelLabel: String = candidates.first().label

    private fun configured(fast: Boolean, preview: Boolean): GenerativeModel =
        Generation.getClient(
            generationConfig {
                // Fully qualified: inside this builder `modelConfig` is also the property name.
                modelConfig = com.google.mlkit.genai.prompt.modelConfig {
                    preference = if (fast) ModelPreference.FAST else ModelPreference.FULL
                    releaseStage = if (preview) ModelReleaseStage.PREVIEW else ModelReleaseStage.STABLE
                }
            }
        )

    private val _status = MutableStateFlow<NanoStatus>(NanoStatus.Unknown)
    override val status: StateFlow<NanoStatus> = _status.asStateFlow()

    private val requestLock = Mutex()
    private var downloadJob: Job? = null
    private var warmedUp = false
    @Volatile private var cachingAvailable: Boolean? = null
    @Volatile private var tokenLimit: Int? = null

    init {
        appScope.launch { refreshStatus() }
    }

    override suspend fun refreshStatus(): NanoStatus {
        if (_status.value is NanoStatus.Downloading && downloadJob?.isActive == true) return _status.value
        // Best result across the candidates: Ready > Downloading > Downloadable > Unsupported.
        var best: Pair<GenerativeModel, NanoStatus>? = null
        var lastError: String? = null
        var bestLabel = modelLabel
        for (candidate in candidates) {
            val client = if (candidate.label == modelLabel) model else runCatching { candidate.create() }.getOrNull() ?: continue
            val status = try {
                when (client.checkStatus()) {
                    FeatureStatus.AVAILABLE -> NanoStatus.Ready
                    FeatureStatus.DOWNLOADABLE -> NanoStatus.Downloadable
                    FeatureStatus.DOWNLOADING -> NanoStatus.Downloading(null)
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GenAiException) {
                Timber.tag(TAG).w(e, "checkStatus failed for ${candidate.label} (code ${e.errorCode})")
                lastError = e.message?.takeIf { it.isNotBlank() }
                null
            } catch (e: Exception) {
                // AICore missing entirely, or the binding failed.
                Timber.tag(TAG).w(e, "checkStatus failed for ${candidate.label}")
                null
            }
            Timber.tag(TAG).d("Nano ${candidate.label} -> $status")
            if (status != null && (best == null || rank(status) > rank(best.second))) {
                best?.first?.takeIf { it !== model }?.let { runCatching { it.close() } }
                best = client to status
                bestLabel = candidate.label
            } else if (client !== model) {
                runCatching { client.close() }
            }
            if (status == NanoStatus.Ready) break
        }
        val next = best?.let { (client, status) ->
            if (client !== model) {
                val old = model
                model = client
                modelLabel = bestLabel
                warmedUp = false
                cachingAvailable = null
                tokenLimit = null
                runCatching { old.close() }
            }
            status
        } ?: NanoStatus.Unsupported(lastError ?: UNSUPPORTED_REASON)
        _status.value = next
        // AICore was already fetching it (e.g. another app asked first): follow its progress.
        if (next is NanoStatus.Downloading) startDownload()
        return next
    }

    override fun startDownload() {
        if (downloadJob?.isActive == true) return
        downloadJob = appScope.launch {
            var total = 0L
            try {
                model.download().collect { step ->
                    when (step) {
                        is DownloadStatus.DownloadStarted -> {
                            total = step.bytesToDownload
                            _status.value = NanoStatus.Downloading(if (total > 0) 0f else null)
                        }
                        is DownloadStatus.DownloadProgress -> {
                            _status.value = NanoStatus.Downloading(
                                if (total > 0) (step.totalBytesDownloaded.toFloat() / total).coerceIn(0f, 1f) else null
                            )
                        }
                        DownloadStatus.DownloadCompleted -> _status.value = NanoStatus.Ready
                        is DownloadStatus.DownloadFailed -> {
                            Timber.tag(TAG).w(step.e, "Gemini Nano download failed")
                            _status.value = NanoStatus.Error(step.e.message?.takeIf { it.isNotBlank() } ?: "Download failed")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Gemini Nano download failed")
                _status.value = NanoStatus.Error(e.message?.takeIf { it.isNotBlank() } ?: "Download failed")
            }
            if (_status.value is NanoStatus.Downloading) refreshStatus()
        }
    }

    override fun warmUp() {
        if (warmedUp || _status.value != NanoStatus.Ready) return
        appScope.launch {
            runCatching { model.warmup() }
                .onSuccess { warmedUp = true }
                .onFailure { if (it !is CancellationException) Timber.tag(TAG).d(it, "warmup failed") }
        }
    }

    override suspend fun modelName(): String? =
        if (_status.value != NanoStatus.Ready) null
        else runCatching { model.getBaseModelName() }.getOrNull()

    override suspend fun generate(
        prefix: String,
        input: String,
        maxOutputTokens: Int,
        temperature: Float,
        timeoutMs: Long,
    ): String? {
        if (_status.value != NanoStatus.Ready) return null
        return withTimeoutOrNull(timeoutMs) {
            requestLock.withLock {
                try {
                    val caching = cachingAvailable ?: runCatching { model.isCachingFeatureAvailable() }
                        .getOrDefault(false).also { cachingAvailable = it }
                    val request = if (caching) {
                        generateContentRequest(TextPart(input)) {
                            promptPrefix = PromptPrefix(prefix)
                            this.temperature = temperature
                            topK = 16
                            this.maxOutputTokens = maxOutputTokens
                        }
                    } else {
                        generateContentRequest(TextPart(prefix + "\n\n" + input)) {
                            this.temperature = temperature
                            topK = 16
                            this.maxOutputTokens = maxOutputTokens
                        }
                    }
                    // Stay inside the model's context: refuse rather than truncate.
                    val limit = tokenLimit ?: runCatching { model.getTokenLimit() }.getOrDefault(DEFAULT_TOKEN_LIMIT)
                        .also { tokenLimit = it }
                    val used = runCatching { model.countTokens(request).totalTokens }
                        .getOrDefault((prefix.length + input.length) / 3)
                    if (used + maxOutputTokens > limit) {
                        Timber.tag(TAG).d("Prompt too long for Nano ($used + $maxOutputTokens > $limit)")
                        return@withLock null
                    }
                    val started = System.currentTimeMillis()
                    val text = model.generateContent(request).candidates.firstOrNull()?.text
                    Timber.tag(TAG).d("Nano answered in ${System.currentTimeMillis() - started} ms ($used tokens in)")
                    text?.takeIf { it.isNotBlank() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GenAiException) {
                    // Busy, over the per-app quota, used from the background, request too large…
                    // all mean "use the local result this time".
                    Timber.tag(TAG).w("Nano request failed (code ${e.errorCode}): ${e.message}")
                    null
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Nano request failed")
                    null
                }
            }
        }
    }

    private fun rank(s: NanoStatus): Int = when (s) {
        NanoStatus.Ready -> 3
        is NanoStatus.Downloading -> 2
        NanoStatus.Downloadable -> 1
        else -> 0
    }

    /** One model choice to try, in order (see [candidates]). */
    private class Candidate(val label: String, val create: () -> GenerativeModel)

    private companion object {
        const val TAG = "GeminiNano"
        const val DEFAULT_TOKEN_LIMIT = 4_000
        const val UNSUPPORTED_REASON =
            "Gemini Nano isn't available on this phone (it needs AICore, a supported device and a locked bootloader)"
    }
}
