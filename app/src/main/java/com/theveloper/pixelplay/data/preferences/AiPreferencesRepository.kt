package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    // ─── Default system prompts ──────────────────────────────────────────────

    companion object {
        val DEFAULT_SYSTEM_PROMPT = """
            You are 'Vibe-Engine', a professional music curator.
            Analyze the user's request and listening profile to provide perfect music recommendations.
            Always prioritize flow, emotional resonance, and discovery.
        """.trimIndent()

        val DEFAULT_DEEPSEEK_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GROQ_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_MISTRAL_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_NVIDIA_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_KIMI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GLM_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OPENAI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OPENROUTER_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
    }

    // ─── Preference keys ─────────────────────────────────────────────────────

    private object Keys {
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val SAFE_TOKEN_LIMIT = booleanPreferencesKey("safe_token_limit")
        val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
        val AI_TOP_P = floatPreferencesKey("ai_top_p")
        val AI_TOP_K = intPreferencesKey("ai_top_k")
        val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val AI_PRESENCE_PENALTY = floatPreferencesKey("ai_presence_penalty")
        val AI_FREQUENCY_PENALTY = floatPreferencesKey("ai_frequency_penalty")
        val AI_SAMPLE_SIZE = intPreferencesKey("ai_sample_size")
        val AI_DIGEST_MODE = stringPreferencesKey("ai_digest_mode")
        val AI_INCLUDE_EXTENDED_FIELDS = booleanPreferencesKey("ai_include_extended_fields")

        fun getApiKey(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_api_key")
        fun getModel(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_model")
        fun getSystemPrompt(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_system_prompt")
        fun getBaseUrl(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_base_url")
    }

    // ─── Per-provider accessors (api key, model, system prompt, base url) ────

    // Generic accessors for AiHandler
    fun getApiKey(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getApiKey(provider)]?.trim() ?: "" }

    fun getModel(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getModel(provider)] ?: "" }

    fun getSystemPrompt(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.getSystemPrompt(provider)] ?: DEFAULT_SYSTEM_PROMPT
        }

    fun getBaseUrl(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getBaseUrl(provider)] ?: "" }

    suspend fun setApiKey(provider: AiProvider, apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.getApiKey(provider)] = apiKey.trim() }
    }

    suspend fun setModel(provider: AiProvider, model: String) {
        dataStore.edit { preferences -> preferences[Keys.getModel(provider)] = model }
    }

    suspend fun setSystemPrompt(provider: AiProvider, prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.getSystemPrompt(provider)] = prompt }
    }

    suspend fun resetSystemPrompt(provider: AiProvider) {
        dataStore.edit { preferences ->
            preferences[Keys.getSystemPrompt(provider)] = DEFAULT_SYSTEM_PROMPT
        }
    }

    suspend fun setBaseUrl(provider: AiProvider, url: String) {
        dataStore.edit { preferences -> preferences[Keys.getBaseUrl(provider)] = url.trim() }
    }

    // ─── Provider selection ──────────────────────────────────────────────────

    val aiProvider: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PROVIDER] ?: "GEMINI_NANO" }

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { preferences -> preferences[Keys.AI_PROVIDER] = provider }
    }

    // ─── Token safety ────────────────────────────────────────────────────────

    val isSafeTokenLimitEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] ?: true }

    suspend fun setSafeTokenLimitEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] = enabled }
    }

    // ─── Generation parameters ───────────────────────────────────────────────

    val aiTemperature: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_TEMPERATURE] ?: 0.7f }

    suspend fun setAiTemperature(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_TEMPERATURE] = value }
    }

    val aiTopP: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_TOP_P] ?: 0.95f }

    suspend fun setAiTopP(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_TOP_P] = value }
    }

    val aiTopK: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AI_TOP_K] ?: 64 }

    suspend fun setAiTopK(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AI_TOP_K] = value }
    }

    val aiMaxTokens: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AI_MAX_TOKENS] ?: 4096 }

    suspend fun setAiMaxTokens(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AI_MAX_TOKENS] = value }
    }

    val aiPresencePenalty: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PRESENCE_PENALTY] ?: 0.0f }

    suspend fun setAiPresencePenalty(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_PRESENCE_PENALTY] = value }
    }

    val aiFrequencyPenalty: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_FREQUENCY_PENALTY] ?: 0.0f }

    suspend fun setAiFrequencyPenalty(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_FREQUENCY_PENALTY] = value }
    }

    // ─── Song data configuration ─────────────────────────────────────────────

    val aiSampleSize: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AI_SAMPLE_SIZE] ?: 40 }

    suspend fun setAiSampleSize(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AI_SAMPLE_SIZE] = value }
    }

    val aiDigestMode: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_DIGEST_MODE] ?: "safe" }

    suspend fun setAiDigestMode(mode: String) {
        dataStore.edit { preferences -> preferences[Keys.AI_DIGEST_MODE] = mode }
    }

    val aiIncludeExtendedFields: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AI_INCLUDE_EXTENDED_FIELDS] ?: false }

    suspend fun setAiIncludeExtendedFields(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_INCLUDE_EXTENDED_FIELDS] = enabled }
    }
}
