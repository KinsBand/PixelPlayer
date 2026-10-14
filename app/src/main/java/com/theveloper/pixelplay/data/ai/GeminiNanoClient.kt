package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.ai.nano.NanoEngine
import com.theveloper.pixelplay.data.ai.nano.NanoStatus
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Nano for [AiHandler]'s general requests, on the real on-device model ([NanoEngine],
 * ML Kit GenAI Prompt API). Replaces the earlier stand-in that returned random song IDs.
 *
 * Short, structured jobs (queue prompt, AI playlist, lyrics translation) go through their own
 * readers with small prompts; this path is for anything else and fails fast (so [AiHandler]
 * falls back to the local engine) when Nano isn't ready or the prompt doesn't fit.
 */
@Singleton
class GeminiNanoClient @Inject constructor(
    @Suppress("unused") private val json: Json,
    private val engine: NanoEngine,
) {
    private val MAX_PROMPT_CHARS = 12_000

    /** True once AICore reports Gemini Nano downloaded and ready on this phone. */
    fun isAvailable(@Suppress("UNUSED_PARAMETER") context: Context): Boolean =
        engine.status.value == NanoStatus.Ready

    /**
     * Runs [prompt] with [systemPrompt] on the on-device model. Throws when Nano can't answer
     * (not ready, busy, over quota, too long, too slow) so the caller falls back.
     */
    suspend fun generateContent(
        systemPrompt: String,
        prompt: String,
        temperature: Float? = null,
        @Suppress("UNUSED_PARAMETER") topP: Float? = null,
        @Suppress("UNUSED_PARAMETER") topK: Int? = null
    ): String {
        if (prompt.length > MAX_PROMPT_CHARS) {
            Timber.tag("GeminiNanoClient").w("Prompt too long for on-device model (${prompt.length} chars)")
            throw IllegalArgumentException("Prompt too long for Gemini Nano")
        }
        return engine.generate(
            prefix = systemPrompt,
            input = prompt,
            maxOutputTokens = 1024,
            temperature = temperature ?: 0.4f,
            timeoutMs = 15_000L,
        ) ?: throw IllegalStateException("Gemini Nano couldn't answer")
    }
}
