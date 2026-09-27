package com.theveloper.pixelplay.data.network

import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkPurpose {
    Lyrics,
    Artwork,
    Update,
    Analytics,
    Enrichment
}

enum class NetworkDecision {
    Allowed,
    OfflineMode,
    IntegrationDisabled
}

@Singleton
class NetworkAccessPolicy @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    // Latest preference values, mirrored so OkHttp interceptors can decide without blocking a
    // thread on DataStore. Null until the first value is read.
    @Volatile private var offlineMode: Boolean? = null
    @Volatile private var lyricsIntegrationEnabled: Boolean? = null
    @Volatile private var enrichmentEnabled: Boolean? = null

    init {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { userPreferencesRepository.offlineModeFlow.collect { offlineMode = it } }
        scope.launch { userPreferencesRepository.lyricsIntegrationEnabledFlow.collect { lyricsIntegrationEnabled = it } }
        scope.launch { userPreferencesRepository.enrichmentEnabledFlow.collect { enrichmentEnabled = it } }
    }

    suspend fun getDecision(purpose: NetworkPurpose): NetworkDecision =
        decide(
            purpose,
            offline = { userPreferencesRepository.offlineModeFlow.first() },
            lyrics = { userPreferencesRepository.lyricsIntegrationEnabledFlow.first() },
            enrichment = { userPreferencesRepository.enrichmentEnabledFlow.first() }
        )

    /**
     * Non-blocking decision from the mirrored preferences, or null while a value it needs has
     * not been read yet (callers then fall back to [getDecision], so offline mode is never
     * bypassed during startup).
     */
    fun decisionNow(purpose: NetworkPurpose): NetworkDecision? {
        val offline = offlineMode ?: return null
        if (offline) return NetworkDecision.OfflineMode
        return when (purpose) {
            NetworkPurpose.Lyrics -> lyricsIntegrationEnabled?.let {
                if (it) NetworkDecision.Allowed else NetworkDecision.IntegrationDisabled
            }
            NetworkPurpose.Enrichment -> enrichmentEnabled?.let {
                if (it) NetworkDecision.Allowed else NetworkDecision.IntegrationDisabled
            }
            else -> NetworkDecision.Allowed
        }
    }

    private suspend inline fun decide(
        purpose: NetworkPurpose,
        offline: () -> Boolean,
        lyrics: () -> Boolean,
        enrichment: () -> Boolean
    ): NetworkDecision {
        if (offline()) return NetworkDecision.OfflineMode
        if (purpose == NetworkPurpose.Lyrics && !lyrics()) return NetworkDecision.IntegrationDisabled
        if (purpose == NetworkPurpose.Enrichment && !enrichment()) return NetworkDecision.IntegrationDisabled
        return NetworkDecision.Allowed
    }
}
