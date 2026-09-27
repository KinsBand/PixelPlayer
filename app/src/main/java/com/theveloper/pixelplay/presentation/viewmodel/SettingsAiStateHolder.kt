package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.database.AiUsageDao
import com.theveloper.pixelplay.data.database.AiUsageEntity
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the AI settings cluster of [SettingsViewModel].
 * Owns the provider-aware AI state streams and their setters so the
 * ViewModel only forwards the public members used by the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsAiStateHolder(
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val aiUsageDao: AiUsageDao,
    private val scope: CoroutineScope
) {

    // ─── Provider ───────────────────────────────────────────────────────

    val aiProvider: StateFlow<String> = aiPreferencesRepository.aiProvider
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "GEMINI")

    // ─── Provider-dependent settings ────────────────────────────────────

    val currentAiApiKey: StateFlow<String> = aiProvider
        .flatMapLatest { provider -> aiPreferencesRepository.getApiKey(AiProvider.fromString(provider)) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    val currentAiModel: StateFlow<String> = aiProvider
        .flatMapLatest { provider -> aiPreferencesRepository.getModel(AiProvider.fromString(provider)) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    val currentAiSystemPrompt: StateFlow<String> = aiProvider
        .flatMapLatest { provider -> aiPreferencesRepository.getSystemPrompt(AiProvider.fromString(provider)) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT)

    val currentAiBaseUrl: StateFlow<String> = aiProvider
        .flatMapLatest { provider ->
            val p = AiProvider.fromString(provider)
            if (p.hasConfigurableUrl) aiPreferencesRepository.getBaseUrl(p)
            else flowOf("")
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    fun onAiProviderChange(provider: String) {
        scope.launch {
            aiPreferencesRepository.setAiProvider(provider)
        }
    }

    fun onAiModelChange(model: String) {
        scope.launch {
            val provider = AiProvider.fromString(aiProvider.value)
            aiPreferencesRepository.setModel(provider, model)
        }
    }

    fun onAiSystemPromptChange(prompt: String) {
        scope.launch {
            val provider = AiProvider.fromString(aiProvider.value)
            aiPreferencesRepository.setSystemPrompt(provider, prompt)
        }
    }

    fun resetAiSystemPrompt() {
        scope.launch {
            val provider = AiProvider.fromString(aiProvider.value)
            aiPreferencesRepository.resetSystemPrompt(provider)
        }
    }

    // ─── Generation parameters ──────────────────────────────────────────

    val aiTemperature: StateFlow<Float> = aiPreferencesRepository.aiTemperature
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.7f)
    val aiTopP: StateFlow<Float> = aiPreferencesRepository.aiTopP
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.95f)
    val aiTopK: StateFlow<Int> = aiPreferencesRepository.aiTopK
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 64)
    val aiMaxTokens: StateFlow<Int> = aiPreferencesRepository.aiMaxTokens
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 4096)
    val aiPresencePenalty: StateFlow<Float> = aiPreferencesRepository.aiPresencePenalty
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0f)
    val aiFrequencyPenalty: StateFlow<Float> = aiPreferencesRepository.aiFrequencyPenalty
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0f)

    fun onAiTemperatureChange(value: Float) {
        scope.launch { aiPreferencesRepository.setAiTemperature(value) }
    }
    fun onAiTopPChange(value: Float) {
        scope.launch { aiPreferencesRepository.setAiTopP(value) }
    }
    fun onAiTopKChange(value: Int) {
        scope.launch { aiPreferencesRepository.setAiTopK(value) }
    }
    fun onAiMaxTokensChange(value: Int) {
        scope.launch { aiPreferencesRepository.setAiMaxTokens(value) }
    }
    fun onAiPresencePenaltyChange(value: Float) {
        scope.launch { aiPreferencesRepository.setAiPresencePenalty(value) }
    }
    fun onAiFrequencyPenaltyChange(value: Float) {
        scope.launch { aiPreferencesRepository.setAiFrequencyPenalty(value) }
    }

    // ─── Song data configuration ────────────────────────────────────────

    val aiSampleSize: StateFlow<Int> = aiPreferencesRepository.aiSampleSize
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 40)
    val aiDigestMode: StateFlow<String> = aiPreferencesRepository.aiDigestMode
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "safe")
    val aiIncludeExtendedFields: StateFlow<Boolean> = aiPreferencesRepository.aiIncludeExtendedFields
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    fun onAiSampleSizeChange(value: Int) {
        scope.launch { aiPreferencesRepository.setAiSampleSize(value) }
    }
    fun onAiDigestModeChange(mode: String) {
        scope.launch { aiPreferencesRepository.setAiDigestMode(mode) }
    }
    fun onAiIncludeExtendedFieldsChange(enabled: Boolean) {
        scope.launch { aiPreferencesRepository.setAiIncludeExtendedFields(enabled) }
    }

    // ─── Token safety ───────────────────────────────────────────────────

    val isSafeTokenLimitEnabled: StateFlow<Boolean> = aiPreferencesRepository.isSafeTokenLimitEnabled
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), true)

    fun setSafeTokenLimitEnabled(enabled: Boolean) {
        scope.launch {
            aiPreferencesRepository.setSafeTokenLimitEnabled(enabled)
        }
    }

    // ─── Usage stats ────────────────────────────────────────────────────

    val recentAiUsage: StateFlow<List<AiUsageEntity>> = aiUsageDao.getRecentUsages(20)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPromptTokens: StateFlow<Int> = aiUsageDao.getTotalPromptTokens()
        .map { it ?: 0 }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    val totalOutputTokens: StateFlow<Int> = aiUsageDao.getTotalOutputTokens()
        .map { it ?: 0 }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    val totalThoughtTokens: StateFlow<Int> = aiUsageDao.getTotalThoughtTokens()
        .map { it ?: 0 }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    fun clearAiUsageData() {
        scope.launch {
            aiUsageDao.clearUsage()
        }
    }
}
