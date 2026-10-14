package com.theveloper.pixelplay.data.ai.nano

import com.theveloper.pixelplay.data.ai.gemma.GemmaLiteRtEngine
import com.theveloper.pixelplay.data.ai.gemma.GemmaModelManager
import com.theveloper.pixelplay.data.ai.gemma.OnDeviceEngine
import com.theveloper.pixelplay.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [NanoEngine] the app uses: Gemma 4 (LiteRT-LM) when it's picked in AI settings and the model
 * is on the phone, Gemini Nano (AICore) otherwise. Every on-device feature (Ask about the artist,
 * lyrics translation, mix prompts) goes through here, so switching engines needs no other change.
 */
@Singleton
class RoutingNanoEngine @Inject constructor(
    private val nano: MlKitNanoEngine,
    private val gemma: GemmaLiteRtEngine,
    private val models: GemmaModelManager,
    @AppScope appScope: CoroutineScope,
) : NanoEngine {

    private fun useGemma(): Boolean = models.activeModel() != null

    override val status: StateFlow<NanoStatus> =
        combine(nano.status, models.engine, models.states, models.selected) { nanoStatus, engine, states, selected ->
            if (engine == OnDeviceEngine.GEMMA) {
                if (models.isReady(selected)) NanoStatus.Ready else NanoStatus.Downloadable
            } else nanoStatus
        }.stateIn(appScope, SharingStarted.Eagerly, nano.status.value)

    override suspend fun refreshStatus(): NanoStatus =
        if (models.engine.value == OnDeviceEngine.GEMMA) {
            if (models.isReady(models.selected.value)) NanoStatus.Ready else NanoStatus.Downloadable
        } else nano.refreshStatus()

    override fun startDownload() {
        if (models.engine.value == OnDeviceEngine.GEMMA) models.download(models.selected.value) else nano.startDownload()
    }

    override fun warmUp() {
        if (useGemma()) gemma.warmUp() else nano.warmUp()
    }

    override suspend fun modelName(): String? =
        models.activeModel()?.displayName ?: nano.modelName()

    override suspend fun generate(
        prefix: String,
        input: String,
        maxOutputTokens: Int,
        temperature: Float,
        timeoutMs: Long,
    ): String? =
        if (useGemma()) {
            // Gemma answers in a few seconds once loaded; give it at least 15 s (loading isn't counted).
            gemma.generate(prefix, input, temperature, timeoutMs.coerceAtLeast(15_000L))
        } else {
            nano.generate(prefix, input, maxOutputTokens, temperature, timeoutMs)
        }
}
