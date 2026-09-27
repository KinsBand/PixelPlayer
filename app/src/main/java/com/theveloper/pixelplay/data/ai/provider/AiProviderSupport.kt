package com.theveloper.pixelplay.data.ai.provider

internal class AiProviderException(
    val providerName: String,
    val statusCode: Int? = null,
    val requestedModel: String? = null,
    val providerCode: String? = null,
    val providerType: String? = null,
    val rawBody: String? = null,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    fun isModelUnavailable(): Boolean = false
    fun isBillingIssue(): Boolean = false
    fun isApiKeyIssue(): Boolean = false
    fun shouldCooldown(): Boolean = false
}

internal object AiProviderSupport {
    fun buildProviderChain(primary: AiProvider): List<AiProvider> {
        return listOf(AiProvider.GEMINI_NANO, AiProvider.LOCAL_FALLBACK)
    }

    fun selectRecoveryModel(
        currentModel: String,
        defaultModel: String,
        availableModels: List<String>
    ): String? = null

    fun createException(
        providerName: String,
        statusCode: Int?,
        transportMessage: String?,
        responseBody: String?,
        requestedModel: String?,
        cause: Throwable? = null
    ): AiProviderException {
        return AiProviderException(providerName = providerName, message = transportMessage ?: "Unknown error")
    }

    fun wrapThrowable(providerName: String, throwable: Throwable, requestedModel: String? = null): AiProviderException {
        return AiProviderException(providerName = providerName, message = throwable.message ?: "Unknown error", cause = throwable)
    }
}
