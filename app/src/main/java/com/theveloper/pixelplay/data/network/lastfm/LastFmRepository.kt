package com.theveloper.pixelplay.data.network.lastfm

import android.util.Log
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Enrichment payload of a Last.fm track.getInfo call. */
data class LastFmTrackInfo(
    val tags: List<String>,
    val listeners: Long?,
    val playcount: Long?,
    val summary: String?
)

/** Enrichment payload of a Last.fm artist.getInfo call. */
data class LastFmArtistInfo(
    val bio: String?,
    val tags: List<String>,
    val similarArtists: List<String>
)

/**
 * Repository for Last.fm.
 *
 * The feature is silently OFF without a user-provided API key: every
 * public function returns null when [UserPreferencesRepository.lastFmApiKeyFlow]
 * is blank. Light rate limiting at 5 requests/second. Network/API failures
 * are logged and mapped to null — never thrown.
 */
@Singleton
class LastFmRepository @Inject constructor(
    private val api: LastFmApiService,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val rateLimitMutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun getTrackInfo(artist: String, title: String): LastFmTrackInfo? {
        val apiKey = currentApiKey() ?: return null
        if (artist.isBlank() || title.isBlank()) return null

        val response = runSafely("getTrackInfo") {
            api.trackGetInfo(apiKey = apiKey, artist = artist, track = title)
        } ?: return null
        if (response.error != 0) {
            Log.d(TAG, "getTrackInfo API error ${response.error}: ${response.message}")
            return null
        }
        val track = response.track ?: return null

        return LastFmTrackInfo(
            tags = track.topTags?.tags
                .orEmpty()
                .filter { it.count >= MIN_TAG_COUNT }
                .sortedByDescending { it.count }
                .take(MAX_TAGS)
                .map { it.name },
            listeners = track.listeners?.toLongOrNull(),
            playcount = track.playcount?.toLongOrNull(),
            summary = track.wiki?.summary?.let { stripHtml(it) }
        )
    }

    suspend fun getArtistInfo(artist: String): LastFmArtistInfo? {
        val apiKey = currentApiKey() ?: return null
        if (artist.isBlank()) return null

        val response = runSafely("getArtistInfo") {
            api.artistGetInfo(apiKey = apiKey, artist = artist)
        } ?: return null
        if (response.error != 0) {
            Log.d(TAG, "getArtistInfo API error ${response.error}: ${response.message}")
            return null
        }
        val artistInfo = response.artist ?: return null

        return LastFmArtistInfo(
            bio = artistInfo.bio?.summary?.let { stripHtml(it) },
            // Artist tags carry no count; just take the top entries.
            tags = artistInfo.tags?.tags.orEmpty().take(MAX_TAGS).map { it.name },
            similarArtists = artistInfo.similar?.artists.orEmpty().take(MAX_SIMILAR_ARTISTS).map { it.name }
        )
    }

    private suspend fun currentApiKey(): String? =
        userPreferencesRepository.lastFmApiKeyFlow.first().takeIf { it.isNotBlank() }

    private suspend fun <T> runSafely(operation: String, block: suspend () -> T): T? {
        return try {
            withRateLimit { block() }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "$operation failed: ${e.message}")
            null
        }
    }

    /** Light 5 requests/second spacing (short critical section). */
    private suspend fun <T> withRateLimit(block: suspend () -> T): T {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val waitMs = MIN_INTERVAL_MS - (now - lastRequestAt)
            if (waitMs > 0) {
                delay(waitMs)
            }
            lastRequestAt = System.currentTimeMillis()
        }
        return block()
    }

    private fun stripHtml(html: String): String? {
        val withoutTags = html
            .replace(Regex("""<a\s+[^>]*>(.*?)</a>"""), "$1")
            .replace(Regex("""<[^>]+>"""), " ")
        return decodeBasicEntities(withoutTags)
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun decodeBasicEntities(value: String): String =
        value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

    private companion object {
        const val TAG = "LastFmRepository"
        const val MIN_INTERVAL_MS = 200L
        const val MIN_TAG_COUNT = 10
        const val MAX_TAGS = 6
        const val MAX_SIMILAR_ARTISTS = 10
    }
}
