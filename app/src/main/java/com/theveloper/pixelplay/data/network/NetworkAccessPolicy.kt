package com.theveloper.pixelplay.data.network

import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
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
    suspend fun getDecision(purpose: NetworkPurpose): NetworkDecision {
        val isOffline = userPreferencesRepository.offlineModeFlow.first()
        if (isOffline) {
            return NetworkDecision.OfflineMode
        }

        if (purpose == NetworkPurpose.Lyrics) {
            val lyricsIntegration = userPreferencesRepository.lyricsIntegrationEnabledFlow.first()
            if (!lyricsIntegration) {
                return NetworkDecision.IntegrationDisabled
            }
        }

        if (purpose == NetworkPurpose.Enrichment) {
            val enrichmentEnabled = userPreferencesRepository.enrichmentEnabledFlow.first()
            if (!enrichmentEnabled) {
                return NetworkDecision.IntegrationDisabled
            }
        }

        return NetworkDecision.Allowed
    }
}
