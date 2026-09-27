package com.theveloper.pixelplay.data.network.listenbrainz

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListenBrainzRepository @Inject constructor(
    private val listenBrainzService: ListenBrainzService
) {

    /**
     * Read-only fetch of similar recording MBIDs without authentication.
     */
    suspend fun getSimilarRecordings(mbid: String): List<String> {
        if (mbid.isBlank()) return emptyList()
        return try {
            val response = listenBrainzService.getSimilarRecordings(mbid)
            response.mbids.map { it.recording_mbid }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "ListenBrainz getSimilarRecordings failed for %s", mbid)
            emptyList()
        }
    }

    /**
     * Opt-in scrobble submission using user API token.
     */
    suspend fun submitScrobble(
        userToken: String,
        artistName: String,
        trackName: String,
        releaseName: String?,
        recordingMbid: String?,
        listenedAtSeconds: Long = System.currentTimeMillis() / 1000L
    ): Boolean {
        if (userToken.isBlank() || artistName.isBlank() || trackName.isBlank()) return false
        return try {
            val additionalInfo = mutableMapOf<String, String>()
            if (!recordingMbid.isNullOrBlank()) {
                additionalInfo["recording_mbid"] = recordingMbid
            }

            val payload = ListenBrainzSubmitPayload(
                listen_type = "single",
                payload = listOf(
                    ListenPayloadItem(
                        listened_at = listenedAtSeconds,
                        track_metadata = ListenTrackMetadata(
                            artist_name = artistName,
                            track_name = trackName,
                            release_name = releaseName,
                            additional_info = additionalInfo
                        )
                    )
                )
            )

            val tokenHeader = if (userToken.startsWith("Token ")) userToken else "Token $userToken"
            val response = listenBrainzService.submitListen(tokenHeader, payload)
            response.status == "ok"
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "ListenBrainz submitScrobble failed for %s - %s", artistName, trackName)
            false
        }
    }

    companion object {
        private const val TAG = "ListenBrainzRepository"
    }
}
