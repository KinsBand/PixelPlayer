package com.theveloper.pixelplay.data.ai.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiProviderSupportTest {

    @Test
    fun `provider chain is the local-only fallback chain`() {
        val chain = AiProviderSupport.buildProviderChain(AiProvider.GEMINI_NANO)

        assertThat(chain).containsExactly(AiProvider.GEMINI_NANO, AiProvider.LOCAL_FALLBACK).inOrder()
    }

    @Test
    fun `provider chain contains every supported provider`() {
        val chain = AiProviderSupport.buildProviderChain(AiProvider.LOCAL_FALLBACK)

        assertThat(chain).containsExactlyElementsIn(AiProvider.entries)
    }

    @Test
    fun `select recovery model is disabled without cloud providers`() {
        val recovered = AiProviderSupport.selectRecoveryModel(
            currentModel = "llama3-8b-8192",
            defaultModel = "llama-3.1-8b-instant",
            availableModels = listOf("llama-3.1-8b-instant", "llama-3.3-70b-versatile")
        )

        assertThat(recovered).isNull()
    }

    @Test
    fun `provider exception keeps provider name and message`() {
        val e = AiProviderSupport.createException(
            providerName = "Gemini",
            statusCode = 400,
            transportMessage = "Bad Request",
            responseBody = null,
            requestedModel = "gemini-2.5-flash"
        )

        assertThat(e.providerName).isEqualTo("Gemini")
        assertThat(e.message).isEqualTo("Bad Request")
        // Cloud-provider classification was removed together with the provider stack.
        assertThat(e.isModelUnavailable()).isFalse()
        assertThat(e.isBillingIssue()).isFalse()
        assertThat(e.isApiKeyIssue()).isFalse()
    }
}
