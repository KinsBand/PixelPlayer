package com.theveloper.pixelplay.data.youtube

import com.grack.nanojson.JsonWriter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import timber.log.Timber

/**
 * NewPipe's VISIONOS player request (`YoutubeStreamHelper.getVisionOsPlayerResponse`) without
 * two costs it adds to every cold manifest:
 *
 * - It first asks `www.youtube.com/youtubei/v1/visitor_id` for new visitor data: a second
 *   sequential round trip, usually on a second TLS connection. A real client keeps its visitor
 *   data, so it is fetched once, from the player request's own host (which also opens the
 *   connection the player request reuses), kept on disk, and renewed after
 *   [VISITOR_MAX_AGE_MS] or when YouTube answers with a sign-in check.
 * - The full player response is ~70 KB (~15 KB gzipped) of video formats, captions, tracking
 *   and config the audio path never reads. [FIELD_MASK] cuts it to ~25 KB (~3.4 KB gzipped),
 *   one TCP flight on a new connection. If YouTube rejects the mask (HTTP 400) and the same
 *   request works without it, requests go out unmasked for the rest of the session.
 *
 * Client name, version, device and user agent still come from NewPipe, so they follow its
 * updates. Requests run through [NewPipeExecution], so a skip cancels them.
 */
internal class VisionOsPlayer(
    private val store: InnerTubeVersionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val visitorLock = Mutex()
    /** Visitor data and when it was issued. */
    @Volatile private var visitor: Pair<String, Long>? = null
    @Volatile private var visitorLoaded = false
    @Volatile private var fieldMaskEnabled = true
    /** When the player host was last contacted, to tell whether its connection is still pooled. */
    @Volatile private var lastContactAt = 0L

    /**
     * The player response for [videoId], or null when YouTube returned nothing usable.
     * [cpn] is the playback nonce the stream URLs will carry.
     */
    suspend fun playerResponse(videoId: String, cpn: String): JSONObject? {
        val (visitorData, fetchedNow) = visitorData(rejected = null)
        Timber.tag("StreamingLatency").d("visionos_visitor=%s field_mask=%b",
            if (fetchedNow) "fetched" else "reused", fieldMaskEnabled)
        val root = request(videoId, cpn, visitorData, fieldMaskEnabled) ?: return null
        if (fetchedNow || !isSignInCheck(root)) return root
        // Remembered visitor data can be retired by YouTube; renew it and try once more.
        Timber.tag(TAG).d("Visitor data refused for %s; renewing", videoId)
        val (renewed, _) = visitorData(rejected = visitorData)
        return request(videoId, cpn, renewed, fieldMaskEnabled)
    }

    /**
     * Opens the player host's connection ahead of the first tap by renewing visitor data,
     * unless that host was contacted recently enough for its connection to still be pooled.
     */
    suspend fun warmUp() {
        fun recentlyContacted() = clock() - lastContactAt < WARM_CONNECTION_MS
        if (recentlyContacted() || YouTubeRateLimit.isLimited(clock())) return
        try {
            // A play may have contacted the host while this waited for the lock.
            visitorLock.withLock { if (!recentlyContacted()) fetchVisitor(clock()) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Player warm-up skipped")
        }
    }

    /** Returns visitor data and whether it was fetched by this call. */
    private suspend fun visitorData(rejected: String?): Pair<String, Boolean> = visitorLock.withLock {
        val now = clock()
        current(now)?.takeIf { it != rejected }?.let { return@withLock it to false }
        fetchVisitor(now) to true
    }

    private fun current(now: Long): String? {
        if (!visitorLoaded) {
            visitor = visitor ?: store.loadVisitor()
            visitorLoaded = true
        }
        val (value, issuedAt) = visitor ?: return null
        return value.takeIf { now - issuedAt in 0 until VISITOR_MAX_AGE_MS }
    }

    /** Call with [visitorLock] held. */
    private suspend fun fetchVisitor(now: Long): String {
        val value = NewPipeExecution.run {
            YoutubeParsingHelper.getVisitorDataFromInnertube(
                InnertubeClientRequestInfo.ofVisionOsClient(),
                Localization.DEFAULT,
                ContentCountry.DEFAULT,
                headers(fieldMask = false),
                YoutubeParsingHelper.YOUTUBEI_V1_GAPIS_URL,
                null,
                false
            )
        }
        lastContactAt = clock()
        visitor = value to now
        visitorLoaded = true
        store.saveVisitor(value, now)
        return value
    }

    private suspend fun request(videoId: String, cpn: String, visitorData: String, fieldMask: Boolean): JSONObject? {
        val response = NewPipeExecution.run { post(videoId, cpn, visitorData, fieldMask) }
        lastContactAt = clock()
        val code = response.responseCode()
        if (code == 400 && fieldMask) {
            val unmasked = request(videoId, cpn, visitorData, fieldMask = false)
            if (unmasked != null) {
                fieldMaskEnabled = false
                Timber.tag(TAG).w("Player field mask rejected; sending full requests from now on")
            }
            return unmasked
        }
        if (code !in 200..299) return null
        return try {
            JSONObject(response.responseBody())
        } catch (_: org.json.JSONException) {
            null
        }
    }

    private fun post(videoId: String, cpn: String, visitorData: String, fieldMask: Boolean): Response {
        val client = InnertubeClientRequestInfo.ofVisionOsClient()
        client.clientInfo.visitorData = visitorData
        // Same body NewPipe builds: client context, then video id, nonce and content checks.
        val body = YoutubeParsingHelper.prepareJsonBuilder(Localization.DEFAULT, ContentCountry.DEFAULT, client, null)
            .value(YoutubeParsingHelper.VIDEO_ID, videoId)
            .value(YoutubeParsingHelper.CPN, cpn)
            .value(YoutubeParsingHelper.CONTENT_CHECK_OK, true)
            .value(YoutubeParsingHelper.RACY_CHECK_OK, true)
            .done()
        val url = YoutubeParsingHelper.YOUTUBEI_V1_GAPIS_URL + "player?" +
            YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER + "&t=" +
            YoutubeParsingHelper.generateTParameter() + "&id=" + videoId
        return NewPipe.getDownloader().postWithContentTypeJson(
            url, headers(fieldMask), JsonWriter.string(body).toByteArray(Charsets.UTF_8), Localization.DEFAULT
        )
    }

    private fun headers(fieldMask: Boolean): Map<String, List<String>> = buildMap {
        put("User-Agent", listOf(YoutubeParsingHelper.getVisionOsUserAgent(Localization.DEFAULT)))
        put("X-Goog-Api-Format-Version", listOf("2"))
        if (fieldMask) put("X-Goog-FieldMask", listOf(FIELD_MASK))
    }

    private fun isSignInCheck(root: JSONObject): Boolean =
        root.optJSONObject("playabilityStatus")?.optString("status") == "LOGIN_REQUIRED"

    internal companion object {
        private const val TAG = "VisionOsPlayer"

        /** Only what [InnerTubeParser.directStreams] and the identity checks read. */
        const val FIELD_MASK = "playabilityStatus(status,reason),videoDetails(videoId,lengthSeconds)," +
            "streamingData(expiresInSeconds,adaptiveFormats(itag,url,signatureCipher,mimeType,bitrate," +
            "averageBitrate,contentLength,type,audioSampleRate,audioChannels,approxDurationMs))"

        /** Visitor data is renewed after this long even if YouTube still accepts it. */
        const val VISITOR_MAX_AGE_MS = 12L * 60 * 60 * 1000

        /** Shorter than the shared OkHttp pool's 5 minute keep-alive. */
        const val WARM_CONNECTION_MS = 4L * 60 * 1000
    }
}
