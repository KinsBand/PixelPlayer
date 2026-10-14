package com.theveloper.pixelplay.data.ai.gemma

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import com.theveloper.pixelplay.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the downloaded Gemma 4 model ([GemmaModelManager.activeModel]) on the phone with Google's
 * LiteRT-LM. The model is loaded on first use (GPU, falling back to CPU; up to ~10 s) and closed
 * again after [IDLE_CLOSE_MS] without requests, because it holds ~2–4 GB of memory while loaded.
 * One request at a time; every call returns null instead of throwing.
 */
@Singleton
class GemmaLiteRtEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val models: GemmaModelManager,
) {
    private val lock = Mutex()
    private var engine: Engine? = null
    private var loadedModel: GemmaModel? = null
    private var idleJob: Job? = null

    /** Loads the model ahead of a request (e.g. when the artist page's Ask box shows). */
    fun warmUp() {
        appScope.launch(Dispatchers.Default) { lock.withLock { ensureEngine() } }
    }

    /**
     * One answer: [prefix] is the fixed instructions (system instruction), [input] the request.
     * Loading the model doesn't count against [timeoutMs]; answering does.
     */
    suspend fun generate(
        prefix: String,
        input: String,
        temperature: Float = 0.3f,
        timeoutMs: Long = 20_000L,
        tools: List<ToolSet> = emptyList(),
    ): String? = withContext(Dispatchers.Default) {
        lock.withLock {
            val engine = ensureEngine() ?: return@withLock null
            try {
                withTimeoutOrNull(timeoutMs) {
                    val config = ConversationConfig(
                        systemInstruction = Contents.of(prefix),
                        samplerConfig = SamplerConfig(topK = 16, topP = 0.95, temperature = temperature.toDouble()),
                        tools = tools.map { tool(it) },
                    )
                    engine.createConversation(config).use { conversation ->
                        conversation.sendMessage(input).toString().trim().takeIf { it.isNotBlank() }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Gemma request failed")
                null
            } finally {
                scheduleIdleClose()
            }
        }
    }

    /** The loaded engine for the active model, loading (or switching) it when needed. */
    private fun ensureEngine(): Engine? {
        val model = models.activeModel() ?: run { closeEngine(); return null }
        engine?.let { if (loadedModel == model) return it }
        closeEngine()
        val path = models.modelFile(model).absolutePath
        val created = listOf<() -> Backend>({ Backend.GPU() }, { Backend.CPU() }).firstNotNullOfOrNull { backend ->
            try {
                Engine(EngineConfig(modelPath = path, backend = backend(), cacheDir = context.cacheDir.path)).also { it.initialize() }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Gemma didn't load on this backend; trying the next")
                null
            }
        }
        engine = created
        loadedModel = if (created != null) model else null
        if (created != null) Timber.tag(TAG).i("%s loaded", model.displayName)
        return created
    }

    private fun scheduleIdleClose() {
        idleJob?.cancel()
        idleJob = appScope.launch {
            delay(IDLE_CLOSE_MS)
            lock.withLock { closeEngine() }
        }
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
        loadedModel = null
    }

    private companion object {
        const val TAG = "Gemma"
        const val IDLE_CLOSE_MS = 60_000L
    }
}
