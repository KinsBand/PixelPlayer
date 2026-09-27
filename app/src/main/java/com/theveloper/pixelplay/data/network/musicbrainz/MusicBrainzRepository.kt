package com.theveloper.pixelplay.data.network.musicbrainz

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/** Best MusicBrainz recording candidate for a local track. */
data class MbRecordingMatch(
    val recordingMbid: String,
    val title: String,
    val artist: String,
    /** Combined relevance score (see MusicBrainzRepository.scoreCandidate); higher is better. */
    val score: Int
)

/** Work payload of a single work lookup (composer, lyricist, songwriter). */
data class MbWorkDetails(
    val workMbid: String,
    val title: String,
    val language: String?,
    val iswcs: List<String>,
    val composers: List<String>,
    val lyricists: List<String>,
    val songwriters: List<String>
)

/** Enrichment payload of a single recording lookup. */
data class MbRecordingDetails(
    val recordingMbid: String,
    val isrcs: List<String>,
    val genres: List<String>,
    val tags: List<String>,
    val releaseMbids: List<String>,
    val workMbid: String?
)

/** Editorial payload of a single release lookup. */
data class MbReleaseDetails(
    val releaseMbid: String,
    val date: String?,
    val country: String?,
    val label: String?,
    val upcEan: String?,
    val catalogueNumber: String?,
    val releaseType: String?
)

/**
 * Repository for MusicBrainz + Cover Art Archive.
 *
 * All API traffic goes through [MusicBrainzRateLimiter] (strict 1 req/s,
 * shared by both services) and honors HTTP 429/503 with a single
 * Retry-After backoff retry (MusicBrainz responds 503 when over quota).
 * Public functions never throw for network/API failures — they log and
 * return null/empty so callers can treat it as "no data".
 */
@Singleton
class MusicBrainzRepository @Inject constructor(
    private val musicBrainzApi: MusicBrainzApiService,
    private val coverArtArchiveApi: CoverArtArchiveApiService,
    private val rateLimiter: MusicBrainzRateLimiter,
    private val okHttpClient: OkHttpClient
) {

    // ─── Search ─────────────────────────────────────────────────────────

    /**
     * Finds the best matching MusicBrainz recording for a local track.
     * Returns null when no candidate is convincing — no match is better
     * than a wrong match.
     */
    suspend fun searchRecording(
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null
    ): MbRecordingMatch? {
        if (title.isBlank()) return null
        val query = buildRecordingQuery(title, artist, album)
        val response = runMbSafely("searchRecording") {
            musicBrainzApi.searchRecordings(query = query, limit = SEARCH_LIMIT)
        } ?: return null

        return response.recordings
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .mapNotNull { candidate ->
                val combined = scoreCandidate(candidate, title, artist, durationMs) ?: return@mapNotNull null
                MbRecordingMatch(
                    recordingMbid = candidate.id,
                    title = candidate.title,
                    artist = candidate.artistName,
                    score = combined
                )
            }
            .maxByOrNull { it.score }
    }

    // ─── Lookups ────────────────────────────────────────────────────────

    suspend fun fetchRecordingDetails(mbid: String): MbRecordingDetails? {
        if (mbid.isBlank()) return null
        val recording = lookupRecordingModel(mbid) ?: return null
        return toRecordingDetails(recording)
    }

    suspend fun lookupRecordingModel(mbid: String): MbRecording? {
        if (mbid.isBlank()) return null
        return runMbSafely("lookupRecordingModel") {
            musicBrainzApi.lookupRecording(mbid)
        }
    }

    /** Pure mapping of a looked-up recording to its enrichment payload. */
    fun toRecordingDetails(recording: MbRecording): MbRecordingDetails {
        val workMbid = recording.relations.find { it.targetType == "work" || it.work != null }?.work?.id
            ?: recording.relations.find { it.targetType == "work" || it.work != null }?.work?.id

        return MbRecordingDetails(
            recordingMbid = recording.id,
            isrcs = recording.isrcs,
            genres = recording.genres.sortedByDescending { it.count }.map { it.name },
            tags = recording.tags.sortedByDescending { it.count }.take(MAX_TAGS).map { it.name },
            releaseMbids = recording.releases.map { it.id }.filter { it.isNotBlank() },
            workMbid = workMbid
        )
    }

    suspend fun fetchWorkDetails(mbid: String): MbWorkDetails? {
        if (mbid.isBlank()) return null
        val work = runMbSafely("fetchWorkDetails") {
            musicBrainzApi.lookupWork(mbid)
        } ?: return null

        val composers = mutableListOf<String>()
        val lyricists = mutableListOf<String>()
        val songwriters = mutableListOf<String>()

        work.relations.forEach { rel ->
            val name = rel.artist?.name ?: return@forEach
            when (rel.type.lowercase()) {
                "composer" -> composers.add(name)
                "lyricist" -> lyricists.add(name)
                "writer" -> songwriters.add(name)
                "author" -> lyricists.add(name)
            }
        }

        return MbWorkDetails(
            workMbid = work.id,
            title = work.title,
            language = work.language,
            iswcs = work.iswcs,
            composers = composers.distinct(),
            lyricists = lyricists.distinct(),
            songwriters = songwriters.distinct()
        )
    }

    suspend fun fetchReleaseDetails(mbid: String): MbReleaseDetails? {
        if (mbid.isBlank()) return null
        val release = runMbSafely("fetchReleaseDetails") {
            musicBrainzApi.lookupRelease(mbid)
        } ?: return null

        val labelInfo = release.labelInfo.firstOrNull { !it.label?.name.isNullOrBlank() }
        val releaseGroup = release.releaseGroup

        return MbReleaseDetails(
            releaseMbid = release.id,
            date = release.date ?: releaseGroup?.firstReleaseDate,
            country = release.country,
            label = labelInfo?.label?.name,
            upcEan = release.barcode,
            catalogueNumber = labelInfo?.catalogNumber,
            releaseType = releaseGroup?.primaryType
        )
    }


    // ─── Cover Art Archive ──────────────────────────────────────────────

    /**
     * Front-cover URLs for a release, best size first (1200px thumbnail
     * preferred, then the full image, then the smaller thumbnails). Empty when
     * the release has no artwork (CAA answers 404).
     */
    suspend fun fetchCoverArtUrls(releaseMbid: String): List<String> {
        if (releaseMbid.isBlank()) return emptyList()
        val manifest = runMbSafely("fetchCoverArtUrls") {
            coverArtArchiveApi.getReleaseArtwork(releaseMbid)
        } ?: return emptyList()

        val fronts = manifest.images.filter { it.front }
        val pool = fronts.ifEmpty { manifest.images }
        return pool.mapNotNull { image ->
            image.thumbnails?.px1200
                ?: image.image.takeIf { it.isNotBlank() }
                ?: image.thumbnails?.px500
                ?: image.thumbnails?.large
                ?: image.thumbnails?.px250
                ?: image.thumbnails?.small
                ?: image.image.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Downloads cover art bytes via plain OkHttp (CAA redirects to
     * archive.org; OkHttp follows redirects by default).
     */
    suspend fun downloadCoverArt(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.isSuccessful) response.body.bytes() else {
                    Log.w(TAG, "downloadCoverArt HTTP ${response.code} for $url")
                    null
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "downloadCoverArt failed for $url: ${e.message}")
            null
        }
    }

    // ─── Best-release heuristic ─────────────────────────────────────────

    /**
     * Picks the most representative release for a recording:
     * 1. Prefer releases known to have a front cover (CAA summary flag).
     * 2. Within that pool, prefer the release whose title best matches the
     *    local album name (token coverage >= 0.5).
     * 3. Otherwise (and as tie-breaker) take the oldest dated release —
     *    earliest appearance is usually the canonical album/single.
     */
    fun pickBestRelease(releases: List<MbRelease>, localAlbum: String?): String? {
        if (releases.isEmpty()) return null

        val withFrontCover = releases.filter { it.coverArtArchive?.front == true }
        val pool = withFrontCover.ifEmpty { releases }

        if (!localAlbum.isNullOrBlank()) {
            val albumNorm = normalizeForMatch(localAlbum)
            val bestByAlbum = pool
                .map { it to tokenCoverage(albumNorm, normalizeForMatch(it.title)) }
                .filter { it.second >= 0.5 }
                .sortedWith(compareByDescending<Pair<MbRelease, Double>> { it.second }.thenBy { sortableDate(it.first) })
                .firstOrNull()
            if (bestByAlbum != null) return bestByAlbum.first.id
        }

        return pool.minByOrNull { sortableDate(it) }?.id
    }

    /** Undated releases sort last; partial ISO dates compare lexicographically. */
    private fun sortableDate(release: MbRelease): String =
        release.date?.takeIf { it.isNotBlank() } ?: "9999"

    // ─── Query building + scoring ───────────────────────────────────────

    private fun buildRecordingQuery(title: String, artist: String, album: String?): String {
        val clauses = mutableListOf("recording:\"${escapeLucene(title)}\"")
        if (artist.isNotBlank()) clauses += "artist:\"${escapeLucene(artist)}\""
        if (!album.isNullOrBlank()) clauses += "release:\"${escapeLucene(album)}\""
        return clauses.joinToString(" AND ")
    }

    private fun escapeLucene(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").trim()

    /**
     * Combined score (max ~125):
     *  - MusicBrainz Lucene score (0..100) weighted 0.45  → up to 45
     *  - title token coverage (0..1) weighted 40           → up to 40
     *  - artist token coverage (0..1) weighted 25          → up to 25
     *  - duration proximity                                → +15/+10/0/-10
     *
     * Returns null for candidates that should not be trusted:
     *  - hard reject when title coverage < 0.5
     *  - when MB score < 70, require strong title coverage (>= 0.85) and
     *    (when the local artist is known) artist coverage >= 0.5
     */
    private fun scoreCandidate(
        candidate: MbRecording,
        title: String,
        artist: String,
        durationMs: Long?
    ): Int? {
        val titleCoverage = tokenCoverage(normalizeForMatch(title), normalizeForMatch(candidate.title))
        if (titleCoverage < 0.5) return null

        val hasArtist = artist.isNotBlank()
        val artistCoverage = if (hasArtist) {
            tokenCoverageSmaller(normalizeForMatch(artist), normalizeForMatch(candidate.artistName))
        } else {
            1.0
        }

        if (candidate.score < MB_SCORE_TRUST_THRESHOLD) {
            val weakArtist = hasArtist && artistCoverage < 0.5
            if (titleCoverage < 0.85 || weakArtist) return null
        }

        val durationScore = durationComponent(candidate.length, durationMs)
        val combined = candidate.score * MB_SCORE_WEIGHT +
            titleCoverage * TITLE_WEIGHT +
            artistCoverage * ARTIST_WEIGHT +
            durationScore
        return combined.toInt()
    }

    private fun durationComponent(candidateLengthMs: Long?, localDurationMs: Long?): Int {
        if (candidateLengthMs == null || candidateLengthMs <= 0L) return 0
        if (localDurationMs == null || localDurationMs <= 0L) return 0
        val diffSeconds = abs(candidateLengthMs - localDurationMs) / 1000.0
        return when {
            diffSeconds <= 5.0 -> 15
            diffSeconds <= DURATION_TOLERANCE_SECONDS -> 10
            diffSeconds <= 20.0 -> 0
            else -> -10
        }
    }

    // ─── Token matching helpers (adapted from LyricsRepositoryImpl) ─────

    /** Fraction of `query`'s tokens present in `candidate`. */
    private fun tokenCoverage(normalizedQuery: String, normalizedCandidate: String): Double {
        val queryTokens = matchTokens(normalizedQuery)
        val candidateTokens = matchTokens(normalizedCandidate)
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
        return queryTokens.intersect(candidateTokens).size.toDouble() / queryTokens.size
    }

    /** Overlap relative to the smaller token set (featuring/duet tolerant). */
    private fun tokenCoverageSmaller(normalizedA: String, normalizedB: String): Double {
        val aTokens = matchTokens(normalizedA)
        val bTokens = matchTokens(normalizedB)
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0.0
        val overlap = aTokens.intersect(bTokens).size
        return overlap.toDouble() / minOf(aTokens.size, bTokens.size)
    }

    private fun matchTokens(normalizedValue: String): Set<String> =
        normalizedValue.split(' ').filter { it.isNotBlank() }.toSet()

    private fun normalizeForMatch(value: String): String {
        val withoutDiacritics = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

        return withoutDiacritics
            .replace("&", " and ")
            .replace(Regex("""[’'`]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    // ─── Rate limiting + error handling ─────────────────────────────────

    /** One permitted attempt, plus a single Retry-After backoff retry on 429/503. */
    private suspend fun <T> callWithRateLimit(block: suspend () -> T): T {
        return try {
            rateLimiter.withPermit { block() }
        } catch (e: HttpException) {
            if (e.code() == 429 || e.code() == 503) {
                val retryAfterMs = (e.response()?.headers()?.get("Retry-After")?.toLongOrNull()
                    ?: DEFAULT_RETRY_AFTER_SECONDS).coerceIn(1L, 30L) * 1000L
                Log.w(TAG, "MusicBrainz throttled (HTTP ${e.code()}), retrying after ${retryAfterMs}ms")
                delay(retryAfterMs)
                rateLimiter.withPermit { block() }
            } else {
                throw e
            }
        }
    }

    private suspend fun <T> runMbSafely(operation: String, block: suspend () -> T): T? {
        return try {
            callWithRateLimit(block)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "$operation failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "MusicBrainzRepository"
        const val SEARCH_LIMIT = 5
        const val MAX_TAGS = 15
        const val MB_SCORE_TRUST_THRESHOLD = 70
        const val MB_SCORE_WEIGHT = 0.45
        const val TITLE_WEIGHT = 40.0
        const val ARTIST_WEIGHT = 25.0
        const val DURATION_TOLERANCE_SECONDS = 10.0
        const val DEFAULT_RETRY_AFTER_SECONDS = 2L
    }
}
