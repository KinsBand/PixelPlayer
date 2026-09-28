package com.theveloper.pixelplay.data.youtube

import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import timber.log.Timber
import java.util.Locale

/**
 * A second, independent zero-JavaScript player request: the visionOS 0.1 profile sent to the
 * YouTube Music player endpoint (`music.youtube.com/youtubei/v1/player`) with a Safari user
 * agent. This is the top automatic client in Metrolist's InnerTubeX (`VISIONOS_0_1`), which in
 * its August 2026 Android benchmark passed anonymous 90-second playback with seeks in both
 * directions. [VisionOsPlayer] (NewPipe's visionOS 1.02 profile on `youtubei.googleapis.com`
 * with the native app user agent) stays the first choice; this one is the backup that doesn't
 * share its failure modes: different host, version, device and user agent.
 *
 * Like the primary it needs no signature or `n` transform, so a player-script change that
 * breaks full extraction leaves it working. URLs it returns must be fetched with the same
 * [USER_AGENT] (see [YouTubeHttp.userAgentFor]).
 *
 * Requests run through NewPipe's downloader inside [NewPipeExecution], so a skip or a winning
 * alternative cancels them, exactly like the primary's.
 */
internal class VisionOsMusicPlayer {
    @Volatile private var fieldMaskEnabled = true

    /** The player response for [videoId], or null when YouTube returned nothing usable. */
    suspend fun playerResponse(videoId: String, visitorData: String?): JSONObject? =
        request(videoId, visitorData, fieldMaskEnabled)

    private suspend fun request(videoId: String, visitorData: String?, fieldMask: Boolean): JSONObject? {
        val response = NewPipeExecution.run {
            NewPipe.getDownloader().postWithContentTypeJson(
                URL, headers(visitorData, fieldMask), body(videoId, visitorData).toByteArray(Charsets.UTF_8),
                Localization.DEFAULT
            )
        }
        val code = response.responseCode()
        if (code == 400 && fieldMask) {
            val unmasked = request(videoId, visitorData, fieldMask = false)
            if (unmasked != null) {
                fieldMaskEnabled = false
                Timber.tag(TAG).w("Music player field mask rejected; sending full requests from now on")
            }
            return unmasked
        }
        if (code == 429) YouTubeRateLimit.report()
        if (code !in 200..299) return null
        return try {
            JSONObject(response.responseBody())
        } catch (_: org.json.JSONException) {
            null
        }
    }

    private fun headers(visitorData: String?, fieldMask: Boolean): Map<String, List<String>> = buildMap {
        put("User-Agent", listOf(USER_AGENT))
        put("X-Goog-Api-Format-Version", listOf("1"))
        put("X-YouTube-Client-Name", listOf(CLIENT_ID))
        put("X-YouTube-Client-Version", listOf(CLIENT_VERSION))
        put("Origin", listOf(ORIGIN))
        put("X-Origin", listOf(ORIGIN))
        put("Referer", listOf("$ORIGIN/"))
        put("Accept-Language", listOf("en-US,en;q=0.9"))
        visitorData?.let { put("X-Goog-Visitor-Id", listOf(it)) }
        if (fieldMask) put("X-Goog-FieldMask", listOf(VisionOsPlayer.FIELD_MASK))
    }

    internal companion object {
        private const val TAG = "VisionOsMusicPlayer"
        const val ORIGIN = "https://music.youtube.com"
        const val URL = "$ORIGIN/youtubei/v1/player?prettyPrint=false"
        const val CLIENT_NAME = "VISIONOS"
        const val CLIENT_ID = "101"
        const val CLIENT_VERSION = "0.1"
        const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.5 Safari/605.1.15"

        fun body(videoId: String, visitorData: String?): String {
            val client = JSONObject()
                .put("clientName", CLIENT_NAME)
                .put("clientVersion", CLIENT_VERSION)
                .put("osName", "VISION_OS")
                .put("osVersion", "1.3")
                .put("deviceMake", "Apple")
                .put("deviceModel", "RealityDevice14,1")
                .put("platform", "MOBILE")
                .put("hl", "en")
                .put("gl", Locale.getDefault().country.takeIf { it.length == 2 } ?: "US")
            visitorData?.let { client.put("visitorData", it) }
            return JSONObject()
                .put("context", JSONObject().put("client", client))
                .put("videoId", videoId)
                .put("contentCheckOk", true)
                .put("racyCheckOk", true)
                .toString()
        }
    }
}
