package com.theveloper.pixelplay.data.spotify

import com.theveloper.pixelplay.data.database.TrackMappingDao
import com.theveloper.pixelplay.data.database.TrackMappingEntity
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class SpotifyToYouTubeResolver @Inject constructor(
    private val trackMappingDao: TrackMappingDao,
    private val youTubeRepository: YouTubeRepository
) {
    companion object {
        private const val TAG = "SpotifyToYTResolver"
    }

    fun isValidMatch(
        spotifyTitle: String,
        ytTitle: String,
        spotifyDurationMs: Int,
        ytDurationMs: Int
    ): Boolean {
        // 1. Duration check: Reject if difference is greater than 15 seconds (15000 ms)
        if (spotifyDurationMs > 0 && ytDurationMs > 0) {
            val durationDiffSeconds = abs(spotifyDurationMs - ytDurationMs) / 1000
            if (durationDiffSeconds > 15) {
                Timber.tag(TAG).d("Rejected match due to duration diff: %ds (%s vs %s)", durationDiffSeconds, spotifyTitle, ytTitle)
                return false
            }
        }

        // 2. Reject version variants the source track does not have (live, remix, karaoke...).
        //    Whole-word match so "Alive"/"Oliver" do not count as "live".
        val sourceMods = TrackMatching.modifiers(spotifyTitle)
        val extraMods = TrackMatching.modifiers(ytTitle) - sourceMods
        if (extraMods.isNotEmpty() || (sourceMods.contains("live") && !TrackMatching.modifiers(ytTitle).contains("live"))) {
            Timber.tag(TAG).d("Rejected match due to variant mismatch %s: %s vs %s", extraMods, spotifyTitle, ytTitle)
            return false
        }

        // 3. Titles must overlap once decorations like "(feat. X)" or "- Remastered" are removed.
        return TrackMatching.titlesMatch(spotifyTitle, ytTitle)
    }

    /** Best candidate instead of the first one: title/artist agreement plus duration closeness. */
    private fun pickBest(
        candidates: List<com.theveloper.pixelplay.data.model.Song>,
        title: String,
        artistName: String,
        durationMs: Int
    ): com.theveloper.pixelplay.data.model.Song? = candidates
        .filter { isValidMatch(title, it.title, durationMs, it.duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()) }
        .maxByOrNull { candidate ->
            var score = 0.0
            if (TrackMatching.artistsOverlap(artistName, candidate.artist)) score += 2.0
            if (TrackMatching.normalize(candidate.title) == TrackMatching.normalize(title)) score += 1.0
            if (durationMs > 0 && candidate.duration > 0) {
                score += 1.0 - (abs(durationMs - candidate.duration) / 15_000.0).coerceIn(0.0, 1.0)
            }
            score
        }

    suspend fun resolveSpotifyTrackToVideoId(
        spotifyId: String,
        title: String,
        artistName: String,
        durationMs: Int,
        isrc: String?
    ): String? = withContext(Dispatchers.IO) {
        // Step 1: Check local track_mappings database cache
        val cached = trackMappingDao.getMappingBySpotifyId(spotifyId)
            ?: isrc?.let { trackMappingDao.getMappingByIsrc(it) }

        if (cached != null && cached.ytVideoId.isNotBlank()) {
            Timber.tag(TAG).d("Found cached YT Video ID for Spotify track %s: %s", spotifyId, cached.ytVideoId)
            return@withContext cached.ytVideoId.removePrefix("yt_")
        }

        var winningVideoId: String? = null

        // Step 2: Query by ISRC Code (Highest Precision)
        if (!isrc.isNullOrBlank()) {
            try {
                val isrcSearchResults = youTubeRepository.searchSongs(query = isrc)
                // An ISRC hit is already the exact recording; only guard against obvious junk.
                val isrcCandidate = pickBest(isrcSearchResults, title, artistName, durationMs)
                    ?: isrcSearchResults.firstOrNull { candidate ->
                        durationMs <= 0 || candidate.duration <= 0 || abs(durationMs - candidate.duration) <= 15_000
                    }
                if (isrcCandidate != null) {
                    winningVideoId = (isrcCandidate.youtubeId ?: isrcCandidate.id).removePrefix("yt_")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "ISRC search query failed for %s", isrc)
            }
        }

        // Step 3: Fallback to Title + Artist Search
        if (winningVideoId == null) {
            try {
                val query = "$title $artistName".trim()
                val searchResults = youTubeRepository.searchSongs(query = query)
                val bestCandidate = pickBest(searchResults, title, artistName, durationMs)
                winningVideoId = bestCandidate?.let { (it.youtubeId ?: it.id).removePrefix("yt_") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Fallback search query failed for %s - %s", title, artistName)
            }
        }

        // Step 4: Cache resolved YT Video ID in SQLite database
        if (!winningVideoId.isNullOrBlank()) {
            trackMappingDao.insertMapping(
                TrackMappingEntity(
                    spotifyId = spotifyId,
                    ytVideoId = winningVideoId,
                    isrc = isrc
                )
            )
            Timber.tag(TAG).d("Resolved and cached Spotify track %s -> YT Video ID %s", spotifyId, winningVideoId)
        }

        return@withContext winningVideoId
    }

    /** A previously stored match only; never searches. */
    suspend fun cachedVideoId(spotifyId: String, isrc: String? = null): String? = withContext(Dispatchers.IO) {
        (trackMappingDao.getMappingBySpotifyId(spotifyId) ?: isrc?.let { trackMappingDao.getMappingByIsrc(it) })
            ?.ytVideoId?.takeIf { it.isNotBlank() }?.removePrefix("yt_")
    }

    suspend fun resolveSpotifyTrackToStreamUrl(
        spotifyId: String,
        title: String,
        artistName: String,
        durationMs: Int,
        isrc: String?
    ): String? = withContext(Dispatchers.IO) {
        val videoId = resolveSpotifyTrackToVideoId(spotifyId, title, artistName, durationMs, isrc)
            ?: return@withContext null
        youTubeRepository.resolveStreamUrl(videoId)
    }
}
