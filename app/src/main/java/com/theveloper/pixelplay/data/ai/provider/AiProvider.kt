package com.theveloper.pixelplay.data.ai.provider

/**
 * Enum representing available AI providers
 */
enum class AiProvider(val displayName: String, val requiresApiKey: Boolean, val hasConfigurableUrl: Boolean = false) {
    GEMINI_NANO("Gemini Nano (On-Device)", requiresApiKey = false),
    LOCAL_FALLBACK("Local Deterministic Engine", requiresApiKey = false);
    
    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name == value } ?: GEMINI_NANO
        }
    }
}
