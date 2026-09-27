package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.database.AiCacheDao
import com.theveloper.pixelplay.data.database.AiCacheEntity
import com.theveloper.pixelplay.data.database.AiUsageDao
import com.theveloper.pixelplay.data.database.AiUsageEntity
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.network.NetworkAccessPolicy
import com.theveloper.pixelplay.data.network.NetworkPurpose
import com.theveloper.pixelplay.data.network.NetworkDecision
import com.theveloper.pixelplay.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiHandler @Inject constructor(
    private val preferencesRepo: AiPreferencesRepository,
    private val nanoClient: GeminiNanoClient,
    private val localGenerator: LocalHeuristicPlaylistGenerator,
    private val musicRepository: MusicRepository,
    private val networkAccessPolicy: NetworkAccessPolicy,
    private val cacheDao: AiCacheDao,
    private val usageDao: AiUsageDao,
    private val promptEngine: AiSystemPromptEngine,
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope
) {
    // Cache TTL: 30 minutes — prevents stale results from being served indefinitely
    private val CACHE_TTL_MS = 1000L * 60 * 30

    private fun String.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun generateContent(
        prompt: String,
        type: AiSystemPromptType = AiSystemPromptType.GENERAL,
        temperature: Float = 0.7f,
        contextData: String = ""
    ): String {
        val userProviderStr = preferencesRepo.aiProvider.first()
        val userProvider = AiProvider.fromString(userProviderStr)

        val basePersona = preferencesRepo.getSystemPrompt(userProvider).first()
            .ifBlank { AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT }
        val combinedSystemPrompt = promptEngine.buildPrompt(basePersona, type, contextData)

        val hash = (userProvider.name + combinedSystemPrompt + prompt).sha256()

        cacheDao.getCache(hash)?.let { cached ->
            val age = System.currentTimeMillis() - cached.timestamp
            if (age < CACHE_TTL_MS) {
                return cached.responseJson
            }
        }

        val now = System.currentTimeMillis()

        // Verify if we should run on-device Gemini Nano (not in offline mode and model available)
        val isOffline = networkAccessPolicy.getDecision(NetworkPurpose.Analytics) == NetworkDecision.OfflineMode
        val useNano = !isOffline && nanoClient.isAvailable(context) && userProvider == AiProvider.GEMINI_NANO

        val responseJson = if (useNano) {
            try {
                val params = getGenerationParams()
                nanoClient.generateContent(
                    systemPrompt = combinedSystemPrompt,
                    prompt = prompt,
                    temperature = params.temperature,
                    topP = params.topP,
                    topK = params.topK
                )
            } catch (e: Exception) {
                Timber.tag("AiHandler").w(e, "On-device Gemini Nano failed, falling back to local heuristic generator")
                executeLocalFallback(prompt)
            }
        } else {
            Timber.tag("AiHandler").d("Gemini Nano unavailable or offline mode active. Using local heuristic generator.")
            executeLocalFallback(prompt)
        }

        // Track usage metrics
        appScope.launch {
            runCatching {
                val estimatedPromptTokens = (combinedSystemPrompt.length + prompt.length) / 4
                val estimatedOutputTokens = responseJson.length / 4
                usageDao.insertUsage(
                    AiUsageEntity(
                        timestamp = now,
                        provider = if (useNano) "Gemini Nano" else "Local Fallback",
                        model = if (useNano) "gemini-nano" else "heuristic-engine",
                        promptType = type.name,
                        promptTokens = estimatedPromptTokens,
                        outputTokens = estimatedOutputTokens,
                        thoughtTokens = 0
                    )
                )
            }.onFailure { error ->
                Timber.tag("AiHandler").e(error, "Failed to persist AI usage")
            }
        }

        cacheDao.insert(AiCacheEntity(promptHash = hash, responseJson = responseJson, timestamp = System.currentTimeMillis()))
        return responseJson
    }

    private suspend fun executeLocalFallback(prompt: String): String {
        // Extract user search query from <query> tags in prompt
        val queryRegex = """<query>(.*?)</query>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val userQuery = queryRegex.find(prompt)?.groupValues?.get(1)?.trim() ?: ""

        // Extract candidate IDs from prompt
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        val candidateIds = idRegex.findAll(prompt).map { it.groupValues[1] }.toSet()

        val allSongs = musicRepository.getAudioFiles().first()
        val candidateSongs = allSongs.filter { it.id in candidateIds }
        val pool = if (candidateSongs.isNotEmpty()) candidateSongs else allSongs

        val rankedSongs = localGenerator.generate(
            userPrompt = userQuery,
            allSongs = pool,
            limit = 30
        )

        return rankedSongs.joinToString(prefix = "[", postfix = "]") { "\"${it.id}\"" }
    }

    private suspend fun getGenerationParams(): GenerationParams {
        return GenerationParams(
            temperature = preferencesRepo.aiTemperature.first(),
            topP = preferencesRepo.aiTopP.first(),
            topK = preferencesRepo.aiTopK.first(),
            maxTokens = preferencesRepo.aiMaxTokens.first(),
            presencePenalty = preferencesRepo.aiPresencePenalty.first(),
            frequencyPenalty = preferencesRepo.aiFrequencyPenalty.first(),
        )
    }

    private data class GenerationParams(
        val temperature: Float,
        val topP: Float,
        val topK: Int,
        val maxTokens: Int,
        val presencePenalty: Float,
        val frequencyPenalty: Float,
    )
}
