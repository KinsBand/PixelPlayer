package com.theveloper.pixelplay.data.repository

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import androidx.core.net.toUri
import com.google.gson.Gson
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.lyrics.LrcLibApiService
import com.theveloper.pixelplay.data.network.lyrics.LrcLibResponse
import com.theveloper.pixelplay.data.network.lyrics.NetEaseLyricsProvider
import com.theveloper.pixelplay.data.network.lyrics.wordsync.AmllTtmlClient
import com.theveloper.pixelplay.data.network.lyrics.wordsync.KugouKrcProvider
import com.theveloper.pixelplay.data.network.lyrics.wordsync.MusixmatchRichSyncProvider
import com.theveloper.pixelplay.data.network.lyrics.wordsync.NetEaseYrcProvider
import com.theveloper.pixelplay.data.network.lyrics.wordsync.QqMusicQrcProvider
import com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsCandidate
import com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsParsers
import com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsPrefs
import com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsProvider
import com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsResult
import com.theveloper.pixelplay.data.lyrics.LyricsTiming
import com.theveloper.pixelplay.data.model.LyricsTimingEvidence
import com.theveloper.pixelplay.data.preferences.dataStore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import com.theveloper.pixelplay.utils.LyricsImportSecurity
import com.theveloper.pixelplay.utils.LyricsImportValidationResult
import com.theveloper.pixelplay.utils.LogUtils
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.MultiLangRomanizer
import com.theveloper.pixelplay.utils.NetworkRetryUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import okhttp3.OkHttpClient
import okhttp3.Request

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

private val EMBEDDED_LYRICS_KEYS = listOf("LYRICS", "SYNCEDLYRICS", "TTML", "UNSYNCEDLYRICS")

private fun Lyrics.isValid(): Boolean = !synced.isNullOrEmpty() || !plain.isNullOrEmpty()

internal fun parseBestEmbeddedLyricsField(propertyMap: Map<String, Array<String>>?): Lyrics? {
    var firstPlainLyrics: Lyrics? = null

    EMBEDDED_LYRICS_KEYS.forEach { key ->
        propertyMap?.get(key).orEmpty().forEach { field ->
            if (field.isBlank()) return@forEach

            val parsedLyrics = LyricsUtils.parseLyrics(field)
            if (!parsedLyrics.isValid()) return@forEach

            val localLyrics = parsedLyrics.copy(areFromRemote = false)
            if (!localLyrics.synced.isNullOrEmpty()) return localLyrics
            if (firstPlainLyrics == null) firstPlainLyrics = localLyrics
        }
    }

    return firstPlainLyrics
}

/**
 * LyricsData for JSON disk cache (matches Rhythm's format)
 */
private data class LyricsData(
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val wordByWordLyrics: String? = null
) {
    fun hasLyrics(): Boolean =
        !plainLyrics.isNullOrBlank() ||
            !syncedLyrics.isNullOrBlank() ||
            !wordByWordLyrics.isNullOrBlank()
}

private data class RemoteSearchStrategy(
    val name: String,
    val request: suspend () -> Array<LrcLibResponse>?
)

private data class RemoteSearchBatch(
    val strategyName: String,
    val responses: List<LrcLibResponse>
)

private enum class RemoteLyricsMatchMode {
    AUTOMATIC,
    /**
     * Second automatic pass used only for time-synced results: wider duration window (different
     * encodes / trimmed silence of the same recording), but title AND artist must match strongly.
     */
    RELAXED_SYNCED,
    CANDIDATE
}

/** A lyrics result chosen by the automatic remote pipeline. */
private data class RemoteLyricsHit(
    val rawLyrics: String,
    val lyrics: Lyrics,
    val provider: String,
    val response: LrcLibResponse? = null
) {
    val isSynced: Boolean get() = !lyrics.synced.isNullOrEmpty()
}

/** Results of a batch of remote requests plus whether any request failed (network, 5xx, ...). */
private data class RemoteSearchOutcome(
    val responses: List<LrcLibResponse>,
    val hadFailure: Boolean
)

private data class RemoteLyricsMatch(
    val response: LrcLibResponse,
    val score: Int
)

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lrcLibApiService: LrcLibApiService,
    private val lyricsDao: com.theveloper.pixelplay.data.database.LyricsDao,
    private val okHttpClient: OkHttpClient
) : LyricsRepository {


    companion object {
        private const val TAG = "LyricsRepository"
        
        // Cache sizes (matching Rhythm)
        // Word-synced lyrics (per-word timing objects) can be ~100 KB each on the heap; 150 of
        // them was ~15 MB kept for songs that are no longer playing.
        private const val MAX_LYRICS_CACHE_SIZE = 40
        
        // API rate limiting constants (matching Rhythm)
        private const val LRCLIB_MIN_DELAY = 100L
        private const val MAX_CALLS_PER_MINUTE = 30
        private const val NETWORK_RETRY_ATTEMPTS = 3
        private const val NETWORK_RETRY_INITIAL_DELAY_MS = 500L

        /** Stand-in LRC used to rank NetEase candidates before their lyrics are downloaded. */
        private const val SYNCED_PLACEHOLDER = "[00:00.00]\u266A"

        /** Per word-timed provider budget (search + up to [MAX_WORD_FETCHES] downloads). */
        private const val WORD_PROVIDER_TIMEOUT_MS = 6_000L
        private const val MAX_WORD_FETCHES = 2

        private val BRACKETED_QUALIFIER_REGEX = Regex("""[\(\[\{\uFF08\uFF3B\uFF5B\u3010\u300E\u300C\u3014\u3008\u300A]([^)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]*)[\)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]""")
        private val FEATURE_QUALIFIER_REGEX = Regex("""\b(feat(?:uring)?|ft)\.?\b""", RegexOption.IGNORE_CASE)
        private val TITLE_SEPARATOR_REGEX = Regex("""\s*[-\u2013\u2014:\uFF0D\u00B7\u30FB]\s*""")
        private val TIMING_VARIANT_KEYWORDS = setOf(
            "remix",
            "mix",
            "mashup",
            "bootleg",
            "edit",
            "extended",
            "radio",
            "club",
            "vip",
            "dub",
            "live",
            "acoustic",
            "unplugged",
            "sped",
            "slowed",
            "nightcore",
            "instrumental",
            "karaoke",
            "cover",
            "demo",
            "version",
            "rework",
            "flip",
            "refix",
            "opening",
            "ending",
            "op",
            "ed",
            "theme",
            "tv",
            "size",
            "ver",
            "full",
            "movie",
            "ost",
            "soundtrack",
            "background",
            "bgm",
            "short",
            "long",
            "reprise",
            "intro",
            "outro",
            "medley",
            "bonus"
        )
        private val TITLE_DROP_QUALIFIERS = setOf(
            "explicit",
            "clean",
            "mono",
            "stereo",
            "official audio",
            "official video",
            "hi-res",
            "high-res",
            "mqa"
        )
        private val UNKNOWN_ARTISTS = setOf(
            "",
            "<unknown>",
            "unknown",
            "unknown artist",
            "various artists",
            "various"
        )
        private val ARTIST_CONNECTOR_TOKENS = setOf(
            "feat",
            "featuring",
            "ft",
            "and",
            "with",
            "x",
            "vs",
            "the"
        )
    }

    // Repository scope for background tasks
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Thread-safe LRU cache to avoid race conditions across concurrent lyrics requests.
    private val lyricsCache = LruCache<String, Lyrics>(MAX_LYRICS_CACHE_SIZE).also { cache ->
        com.theveloper.pixelplay.data.diagnostics.HeapPressure.register("lyrics-cache") { level ->
            if (level == com.theveloper.pixelplay.data.diagnostics.HeapPressure.Level.CRITICAL) cache.evictAll() else cache.trimToSize(8)
        }
    }

    // Thread-safe rate limiting state.
    private val lastApiCalls = ConcurrentHashMap<String, Long>()
    private val apiCallCounts = ConcurrentHashMap<String, RateLimitWindow>()

    // Gson for JSON cache
    private val gson = Gson()

    // Secondary synced source, only queried when LRCLIB has no synced match.
    private val netEaseProvider = NetEaseLyricsProvider(okHttpClient)

    // Remembers songs for which no synced lyrics were found, so background work backs off
    // (6h -> 1d -> 3d -> 7d -> 14d) instead of re-querying every play, yet keeps retrying:
    // LRCLIB and NetEase gain new synced uploads over time.
    private val syncedMissStore by lazy { SyncedLyricsMissStore(File(context.filesDir, "lyrics/_synced_misses.json"), gson) }

    // Word-timed sources, tried before (and in parallel with) the line-timed LRCLIB/NetEase LRC
    // pipeline. AMLL TTML DB is looked up with the NetEase and QQ ids those searches return.
    private val amllClient = AmllTtmlClient(okHttpClient)
    private val netEaseYrcProvider = NetEaseYrcProvider(okHttpClient, amllClient, netEaseProvider)
    private val qqQrcProvider = QqMusicQrcProvider(okHttpClient, amllClient)
    private val kugouKrcProvider = KugouKrcProvider(okHttpClient)
    private val musixmatchProvider = MusixmatchRichSyncProvider(okHttpClient)

    // Same back-off as synced misses, but for "no word-timed version anywhere" — so songs that
    // only exist as line LRC don't hit four extra providers on every play.
    private val wordMissStore by lazy { SyncedLyricsMissStore(File(context.filesDir, "lyrics/_word_misses.json"), gson) }

    /**
     * Executes multiple remote search strategies in parallel and returns the first non-empty result set.
     * This keeps search responsive while preserving the existing "first useful strategy wins" behavior.
     */
    private suspend fun runSearchStrategiesFast(
        strategies: List<RemoteSearchStrategy>
    ): List<LrcLibResponse> = coroutineScope {
        if (strategies.isEmpty()) return@coroutineScope emptyList()

        val channel = Channel<RemoteSearchBatch>(capacity = strategies.size)
        val jobs = strategies.map { strategy ->
            launch {
                val responses = runCatching {
                    withNetworkRetry(operationName = "lrclib_strategy:${strategy.name}") {
                        strategy.request()
                    }
                }.getOrElse { error ->
                    Log.d(
                        TAG,
                        "Strategy ${strategy.name} failed after retries: ${error.message}"
                    )
                    null
                }
                    ?.toList()
                    .orEmpty()
                channel.trySend(
                    RemoteSearchBatch(
                        strategyName = strategy.name,
                        responses = responses
                    )
                )
            }
        }

        repeat(strategies.size) {
            val batch = channel.receive()
            if (batch.responses.isNotEmpty()) {
                Log.d(TAG, "Fast search hit from strategy: ${batch.strategyName} (${batch.responses.size} results)")
                jobs.forEach { it.cancel() }
                channel.close()
                return@coroutineScope batch.responses.distinctBy { it.id }
            }
        }

        channel.close()
        emptyList()
    }

    private suspend fun <T> withNetworkRetry(
        operationName: String,
        maxAttempts: Int = NETWORK_RETRY_ATTEMPTS,
        initialDelayMs: Long = NETWORK_RETRY_INITIAL_DELAY_MS,
        shouldRetry: (Throwable) -> Boolean = { it is IOException || (it is HttpException && (it.code() == 429 || it.code() >= 500)) },
        block: suspend () -> T
    ): T {
        return NetworkRetryUtils.withNetworkRetry(
            operationName = operationName,
            maxAttempts = maxAttempts,
            initialDelayMs = initialDelayMs,
            shouldRetry = shouldRetry,
            onRetry = { attempt, attempts, throwable ->
                Log.d(
                    TAG,
                    "Retrying $operationName after failure ($attempt/$attempts): ${throwable.message}"
                )
            },
            block = block
        )
    }

    private fun Int.isRetryableHttpStatusCode(): Boolean {
        return this == 429 || this in 500..599
    }

    /**
     * Calculate delay needed before next API call (matching Rhythm)
     */
    private data class RateLimitWindow(
        val windowStartMillis: Long,
        val count: Int
    )

    private fun calculateApiDelay(apiName: String, currentTime: Long): Long {
        val lastCall = lastApiCalls[apiName] ?: 0L
        val minDelay = when (apiName.lowercase()) {
            "lrclib" -> LRCLIB_MIN_DELAY
            else -> 250L
        }

        val timeSinceLastCall = currentTime - lastCall
        if (timeSinceLastCall < minDelay) {
            return minDelay - timeSinceLastCall
        }

        // Check if we're making too many calls in the current fixed 60s window
        val window = apiCallCounts[apiName]
        val callsInLastMinute = if (window != null && (currentTime - window.windowStartMillis) < 60000L) {
            window.count
        } else {
            0
        }
        if (callsInLastMinute >= MAX_CALLS_PER_MINUTE) {
            // Exponential backoff
            return minDelay * 2
        }

        return 0L
    }

    /**
     * Update last API call timestamp (matching Rhythm)
     */
    private fun updateLastApiCall(apiName: String, timestamp: Long) {
        lastApiCalls[apiName] = timestamp

        val currentWindow = apiCallCounts[apiName]
        val updatedWindow = if (currentWindow == null || (timestamp - currentWindow.windowStartMillis) >= 60000L) {
            RateLimitWindow(
                windowStartMillis = timestamp,
                count = 1
            )
        } else {
            currentWindow.copy(count = currentWindow.count + 1)
        }
        apiCallCounts[apiName] = updatedWindow
    }

    /**
     * Main lyrics fetching method with source preference support (matching Rhythm)
     */
    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        
        Log.d(TAG, "===== FETCH LYRICS START: ${song.displayArtist} - ${song.title} (forceRefresh=$forceRefresh, source=$sourcePreference) =====")

        // Check in-memory cache unless force refresh (early return - matching Rhythm)
        if (!forceRefresh) {
            lyricsCache.get(cacheKey)?.let { cached ->
                Log.d(TAG, "===== RETURNING IN-MEMORY CACHED LYRICS =====")
                return@withContext cached
            }
            Log.d(TAG, "===== NO IN-MEMORY CACHE HIT, proceeding to fetch =====")
        } else {
            Log.d(TAG, "===== FORCE REFRESH - BYPASSING IN-MEMORY CACHE =====")
        }

        if (!forceRefresh) {
            loadStoredLyrics(song, cacheKey, includeMemoryCache = false)?.let { stored ->
                lyricsCache.put(cacheKey, stored.first)
                Log.d(TAG, "===== RETURNING STORED LYRICS WITHOUT REMOTE FETCH =====")
                return@withContext stored.first
            }
        }

        // Define source fetchers (matching Rhythm pattern)
        val fetchFromLocal: suspend () -> Lyrics? = {
            findLocalLyricsFile(song)
        }

        val fetchFromEmbedded: suspend () -> Lyrics? = {
            loadEmbeddedLyricsFromMetadata(song)
        }

        val fetchFromAPI: suspend () -> Lyrics? = {
            fetchLyricsFromAPI(song)
        }

        // Try sources in order based on preference. Synced lyrics win immediately; plain lyrics
        // are kept as a fallback while the remaining *local* sources are checked for a synced
        // version. The network is skipped when a local plain version already exists — the caller
        // then upgrades it to synced in the background (upgradeToSynced), so the sheet is never
        // blocked on the network just to replace lyrics it can already show.
        val orderedSources: List<Pair<String, suspend () -> Lyrics?>> = when (sourcePreference) {
            LyricsSourcePreference.API_FIRST -> listOf("API" to fetchFromAPI, "Embedded" to fetchFromEmbedded, "Local" to fetchFromLocal)
            LyricsSourcePreference.EMBEDDED_FIRST -> listOf("Embedded" to fetchFromEmbedded, "Local" to fetchFromLocal, "API" to fetchFromAPI)
            LyricsSourcePreference.LOCAL_FIRST -> listOf("Local" to fetchFromLocal, "Embedded" to fetchFromEmbedded, "API" to fetchFromAPI)
        }

        var plainFallback: Pair<String, Lyrics>? = null
        for ((sourceName, fetcher) in orderedSources) {
            if (sourceName == "API" && plainFallback != null) continue
            try {
                val lyrics = fetcher()
                if (lyrics == null || !lyrics.isValid()) continue

                if (!lyrics.synced.isNullOrEmpty()) {
                    Log.d(TAG, "Found synced lyrics from $sourceName for: ${song.displayArtist} - ${song.title}")
                    lyricsCache.put(cacheKey, lyrics)
                    return@withContext lyrics
                }
                if (plainFallback == null) plainFallback = sourceName to lyrics
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from source $sourceName: ${e.message}")
            }
        }

        plainFallback?.let { (sourceName, lyrics) ->
            Log.d(TAG, "Only plain lyrics found (from $sourceName) for: ${song.displayArtist} - ${song.title}")
            lyricsCache.put(cacheKey, lyrics)
            return@withContext lyrics
        }

        // No lyrics found from any source
        Log.d(TAG, "No lyrics found from any source for: ${song.displayArtist} - ${song.title}")
        return@withContext null
    }

    override suspend fun getStoredLyrics(song: Song): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        loadStoredLyrics(song, cacheKey, includeMemoryCache = true)?.also { stored ->
            lyricsCache.put(cacheKey, stored.first)
        }
    }

    /**
     * Fetches lyrics from the remote providers and persists the result.
     *
     * Always prefers time-synced lyrics: LRCLIB (exact lookup + every search strategy, merged),
     * then a relaxed synced pass, then NetEase. Plain lyrics are only returned when no synced
     * match exists anywhere.
     */
    private suspend fun fetchLyricsFromAPI(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        // JSON disk cache first (matching Rhythm) — but only short-circuit on synced lyrics.
        val cachedJson = loadLocalLyricsJson(song)
        if (cachedJson != null && !cachedJson.synced.isNullOrEmpty()) {
            Log.d(TAG, "===== LOADED SYNCED LYRICS FROM JSON DISK CACHE =====")
            return@withContext cachedJson
        }

        val result = findBestRemoteLyrics(song, requireSynced = false)
        val hit = result.hit ?: return@withContext cachedJson
        persistRemoteHit(song, hit)
        hit.lyrics
    }

    private data class RemotePipelineResult(
        val hit: RemoteLyricsHit?,
        val hadFailure: Boolean
    )

    private suspend fun awaitLrcLibRateLimit() {
        val delayNeeded = calculateApiDelay("lrclib", System.currentTimeMillis())
        if (delayNeeded > 0) {
            Log.d(TAG, "Rate limiting: waiting ${delayNeeded}ms before API call")
            delay(delayNeeded)
        }
        updateLastApiCall("lrclib", System.currentTimeMillis())
    }

    /**
     * The automatic remote pipeline. Never throws for network problems; instead reports
     * [RemotePipelineResult.hadFailure] so a miss is only remembered when every provider
     * actually answered.
     */
    private suspend fun findBestRemoteLyrics(
        song: Song,
        requireSynced: Boolean
    ): RemotePipelineResult = withContext(Dispatchers.IO) {
        val lookup = song.forLyricsLookup()
        val cleanArtist = lookup.displayArtist.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        val cleanTitle = lookup.title.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()

        coroutineScope {
            // 0. Word-timed sources run alongside the line pipeline, so they add no latency when
            //    they miss; a word-timed hit always wins over a line-timed one.
            val wordDeferred = if (wordMissStore.shouldRetry(lookup.id)) {
                async { findWordTimedLyricsSafely(lookup, cleanTitle, cleanArtist) }
            } else {
                null
            }
            val line = findBestLineLyrics(song, requireSynced)
            val word = wordDeferred?.await()
            when {
                word?.hit != null -> wordMissStore.clear(lookup.id)
                word != null && !word.hadFailure -> wordMissStore.recordMiss(lookup.id)
            }

            val hit = word?.hit ?: line.hit
            when {
                hit?.isSynced == true -> syncedMissStore.clear(lookup.id)
                !line.hadFailure -> syncedMissStore.recordMiss(lookup.id)
            }
            if (hit != null) {
                Log.d(TAG, "Remote lyrics for '${lookup.title}': provider=${hit.provider} synced=${hit.isSynced}")
            } else {
                Log.d(TAG, "No remote lyrics for '${lookup.title}' (hadFailure=${line.hadFailure})")
            }
            RemotePipelineResult(hit, line.hadFailure)
        }
    }

    /**
     * The line-timed pipeline (LRCLIB, NetEase LRC, plain fallback). Never throws for network
     * problems; instead reports [RemotePipelineResult.hadFailure]. Miss bookkeeping is done by
     * [findBestRemoteLyrics].
     */
    private suspend fun findBestLineLyrics(
        song: Song,
        requireSynced: Boolean
    ): RemotePipelineResult = withContext(Dispatchers.IO) {
        awaitLrcLibRateLimit()
        @Suppress("NAME_SHADOWING") val song = song.forLyricsLookup()

        val cleanArtist = song.displayArtist.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        val cleanTitle = song.title.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        var hadFailure = false
        val collected = LinkedHashMap<Int, LrcLibResponse>()

        fun finish(hit: RemoteLyricsHit?): RemotePipelineResult = RemotePipelineResult(hit, hadFailure)

        // 1. LRCLIB exact lookup: one request, server-side duration matching, best precision.
        if (song.duration > 0 && cleanTitle.isNotBlank() && !isUnknownArtist(cleanArtist)) {
            val exact = runCatching {
                withNetworkRetry(operationName = "lrclib_get_lyrics") {
                    lrcLibApiService.getLyrics(
                        trackName = cleanTitle,
                        artistName = cleanArtist,
                        albumName = song.album.trim().takeIf { it.isNotBlank() },
                        duration = (song.duration / 1000).toInt().takeIf { it > 0 }
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!error.isLrcLibNoMatch()) hadFailure = true
            }.getOrNull()
            exact?.let { collected[it.id] = it }
            pickBestSynced(song, collected.values)?.let { return@withContext finish(toHit(it, "lrclib_exact")) }
        }

        // 2. Every LRCLIB search strategy, merged — the first non-empty batch is often not the
        //    one that contains the synced upload.
        val outcome = runSearchStrategiesAll(buildAutomaticSearchStrategies(cleanTitle, cleanArtist))
        hadFailure = hadFailure || outcome.hadFailure
        outcome.responses.forEach { collected.putIfAbsent(it.id, it) }

        if (collected.isEmpty()) {
            // Aggressive fallback: drop the artist and cut the title at the first separator.
            val separators = charArrayOf('-', ',', '(', ')', ':', '－', '·', '・')
            val index = cleanTitle.indexOfAny(separators)
            if (index > 0) {
                val superCleanTitle = cleanTitle.substring(0, index).trim()
                if (superCleanTitle.isNotEmpty()) {
                    runCatching {
                        withNetworkRetry(operationName = "lrclib_super_clean_search") {
                            lrcLibApiService.searchLyrics(trackName = superCleanTitle)
                        }
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        hadFailure = true
                    }.getOrNull()?.forEach { collected.putIfAbsent(it.id, it) }
                }
            }
        }

        pickBestSynced(song, collected.values)?.let { return@withContext finish(toHit(it, "lrclib")) }

        // 3. NetEase — synced only.
        val netEaseHit = runCatching { searchNetEaseSynced(song, cleanTitle, cleanArtist) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                // NetEase is a best-effort secondary source: its outages don't block recording
                // a miss (LRCLIB answered), they only mean the next retry may do better.
                Log.d(TAG, "NetEase lookup failed: ${error.message}")
            }
            .getOrNull()
        if (netEaseHit != null) return@withContext finish(netEaseHit)

        // 4. Same search the user runs by hand from Lyrics options (title + artist). It succeeds
        //    far more often than the strict pipeline, so run it automatically before giving up.
        val titleArtistHits = runCatching { titleArtistSearchHits(song) }
            .onFailure { error -> if (error is CancellationException) throw error }
            .getOrDefault(emptyList())
        titleArtistHits.firstOrNull { it.isSynced }?.let { return@withContext finish(it) }

        // 5. Plain fallback.
        if (!requireSynced) {
            rankRemoteLyricsMatches(song, collected.values.toList(), RemoteLyricsMatchMode.AUTOMATIC)
                .firstNotNullOfOrNull { toHit(it.response, "lrclib_plain") }
                ?.let { return@withContext finish(it) }
            titleArtistHits.firstOrNull()?.let { return@withContext finish(it) }
        }

        finish(null)
    }

    // ── Word-timed sources ───────────────────────────────────────────────────────────────────

    private data class WordPipelineResult(val hit: RemoteLyricsHit?, val hadFailure: Boolean)

    private suspend fun enabledWordProviders(): List<WordLyricsProvider> {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        val wordSources = prefs?.get(WordLyricsPrefs.WORD_SOURCES_ENABLED) ?: true
        val musixmatch = prefs?.get(WordLyricsPrefs.MUSIXMATCH_ENABLED) ?: false
        return buildList {
            if (musixmatch) add(musixmatchProvider)
            if (wordSources) {
                add(qqQrcProvider)
                add(netEaseYrcProvider)
                add(kugouKrcProvider)
            }
        }
    }

    private suspend fun findWordTimedLyricsSafely(
        song: Song,
        cleanTitle: String,
        cleanArtist: String,
        mode: RemoteLyricsMatchMode = RemoteLyricsMatchMode.AUTOMATIC
    ): WordPipelineResult = try {
        findWordTimedLyrics(song, cleanTitle, cleanArtist, mode)
    } catch (timeout: TimeoutCancellationException) {
        WordPipelineResult(null, hadFailure = true)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (e: Exception) {
        Log.d(TAG, "Word-timed lookup failed: ${e.message}")
        WordPipelineResult(null, hadFailure = true)
    }

    /**
     * Queries every enabled word-timed provider in parallel (each with its own time budget),
     * ranks candidates with the same title/artist/duration rules as LRCLIB, downloads the best
     * few, and returns the most preferred word-timed result (AMLL > Musixmatch > QQ > NetEase > Kugou).
     */
    private suspend fun findWordTimedLyrics(
        song: Song,
        cleanTitle: String,
        cleanArtist: String,
        mode: RemoteLyricsMatchMode = RemoteLyricsMatchMode.AUTOMATIC
    ): WordPipelineResult {
        if (cleanTitle.isBlank() || song.duration <= 0) return WordPipelineResult(null, hadFailure = false)
        val providers = enabledWordProviders()
        // Nothing asked = not a miss; keeps the back-off clean for when the user enables a source.
        if (providers.isEmpty()) return WordPipelineResult(null, hadFailure = true)
        val artist = if (isUnknownArtist(cleanArtist)) "" else {
            cleanArtist.split(" feat.", " ft.", " featuring", " & ", ", ", "; ", " / ", " x ").first().trim()
        }

        val failures = AtomicInteger(0)
        val results = coroutineScope {
            providers.map { provider ->
                async {
                    try {
                        withTimeout(WORD_PROVIDER_TIMEOUT_MS) {
                            searchAndFetchWordTimed(provider, song, cleanTitle, artist, mode)
                        }
                    } catch (timeout: TimeoutCancellationException) {
                        failures.incrementAndGet()
                        Log.d(TAG, "Word provider ${provider.source.key} timed out")
                        null
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (e: Exception) {
                        failures.incrementAndGet()
                        Log.d(TAG, "Word provider ${provider.source.key} failed: ${e.message}")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val hit = results
            .sortedBy { it.source.ordinal }
            .firstNotNullOfOrNull(::toWordHit)
        return WordPipelineResult(hit, hadFailure = failures.get() > 0)
    }

    private suspend fun searchAndFetchWordTimed(
        provider: WordLyricsProvider,
        song: Song,
        title: String,
        artist: String,
        mode: RemoteLyricsMatchMode
    ): WordLyricsResult? {
        val candidates = withNetworkRetry(operationName = "word_search_${provider.source.key}", maxAttempts = 2) {
            provider.search(title, artist, song.duration)
        }
        if (candidates.isEmpty()) return null
        val byId = candidates.associateBy { wordPlaceholderResponse(it).id }
        val responses = candidates.map(::wordPlaceholderResponse)
        val modes = if (mode == RemoteLyricsMatchMode.CANDIDATE) {
            listOf(RemoteLyricsMatchMode.CANDIDATE)
        } else {
            listOf(RemoteLyricsMatchMode.AUTOMATIC, RemoteLyricsMatchMode.RELAXED_SYNCED)
        }
        val ordered = modes
            .flatMap { rankRemoteLyricsMatches(song, responses, it) }
            .map { it.response.id }
            .distinct()
            .take(MAX_WORD_FETCHES)
        for (id in ordered) {
            val candidate = byId[id] ?: continue
            val result = withNetworkRetry(operationName = "word_fetch_${provider.source.key}", maxAttempts = 2) {
                provider.fetch(candidate)
            } ?: continue
            return result
        }
        return null
    }

    /** Metadata-only stand-in so word candidates go through [rankRemoteLyricsMatches]. */
    private fun wordPlaceholderResponse(candidate: WordLyricsCandidate, syncedLyrics: String = SYNCED_PLACEHOLDER): LrcLibResponse {
        val hash = "${candidate.source.key}:${candidate.id}".hashCode()
        val id = -((hash.toLong() and 0x3fffffffL).toInt() + 1)
        return LrcLibResponse(
            id = id,
            name = candidate.name,
            artistName = candidate.artistName,
            albumName = candidate.albumName,
            duration = candidate.durationSeconds,
            plainLyrics = null,
            syncedLyrics = syncedLyrics
        )
    }

    /**
     * Turns a word-timed result into a hit whose raw text is exactly what gets stored: the TTML
     * document for AMLL, native timing JSON (keeps word end times) for everything else. The raw
     * text is re-parsed through the normal path so what is stored is guaranteed to load back.
     */
    private fun toWordHit(result: WordLyricsResult): RemoteLyricsHit? {
        val lyrics = result.lyrics.copy(
            areFromRemote = true,
            timing = LyricsTimingEvidence(
                lyricsHash = LyricsTiming.textHash(result.lyrics),
                source = "online:${result.source.key}"
            )
        )
        val raw = result.rawTtml ?: LyricsTiming.encode(lyrics)
        val reparsed = LyricsUtils.parseLyrics(raw).copy(areFromRemote = true)
        if (!WordLyricsParsers.isWordTimed(reparsed)) return null
        return RemoteLyricsHit(raw, reparsed, "word_${result.source.key}", wordPlaceholderResponse(result.candidate, raw))
    }

    private fun hasWordTiming(lyrics: Lyrics?): Boolean =
        lyrics?.synced?.any { line -> !line.words.isNullOrEmpty() } == true

    /**
     * Replaces stored line-timed lyrics with a word-timed version of the *same* lyrics (text
     * overlap check), so a manual pick or a local .lrc is never swapped for a different song.
     */
    private suspend fun upgradeLineToWordTimed(song: Song, current: Lyrics, ignoreBackoff: Boolean): Lyrics? {
        if (!ignoreBackoff && !wordMissStore.shouldRetry(song.id)) return null
        val lookup = song.forLyricsLookup()
        val cleanArtist = lookup.displayArtist.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        val cleanTitle = lookup.title.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        val result = findWordTimedLyricsSafely(lookup, cleanTitle, cleanArtist)
        val hit = result.hit
        if (hit == null) {
            if (!result.hadFailure) wordMissStore.recordMiss(song.id)
            return null
        }
        if (!sameLyricsText(current, hit.lyrics)) {
            Log.d(TAG, "Word-timed '${lookup.title}' from ${hit.provider} doesn't match the stored lyrics; kept stored")
            wordMissStore.recordMiss(song.id)
            return null
        }
        wordMissStore.clear(song.id)
        persistRemoteHit(song, hit)
        return hit.lyrics
    }

    private fun sameLyricsText(a: Lyrics, b: Lyrics): Boolean {
        fun tokens(lyrics: Lyrics): Set<String> =
            (lyrics.synced?.map { it.line } ?: lyrics.plain.orEmpty())
                .flatMap { line ->
                    normalizeForMatch(line).split(' ').filter { it.isNotBlank() }
                }
                .toSet()
        val left = tokens(a)
        val right = tokens(b)
        if (left.isEmpty() || right.isEmpty()) return false
        val shared = left.count { it in right }.toFloat()
        return shared / left.size >= 0.6f && shared / right.size >= 0.4f
    }

    /**
     * Background version of the manual "Search lyrics" (title + artist) flow. Results are ordered
     * by the candidate ranking when the song's duration allows it, then by a loose title check,
     * synced first. Never throws for HTTP problems.
     */
    private suspend fun titleArtistSearchHits(originalSong: Song): List<RemoteLyricsHit> {
        val song = originalSong.forLyricsLookup()
        val title = song.title.trim()
        if (title.isBlank()) return emptyList()
        val artist = song.displayArtist.trim().takeIf { it.isNotBlank() && !isUnknownArtist(it) }
        val query = listOfNotNull(title, artist).joinToString(" ")
        val strategies = buildList {
            add(RemoteSearchStrategy("bg_manual_query") { lrcLibApiService.searchLyrics(query = query) })
            if (artist != null) {
                add(RemoteSearchStrategy("bg_manual_track+artist") {
                    lrcLibApiService.searchLyrics(trackName = title, artistName = artist)
                })
            }
        }
        val responses = runSearchStrategiesAll(strategies).responses.filter { hasLyrics(it) }
        if (responses.isEmpty()) return emptyList()

        val ranked = rankRemoteLyricsMatches(song, responses, RemoteLyricsMatchMode.CANDIDATE).map { it.response }
        val songTitle = normalizeForMatch(baseTitleForMatching(title))
        val looseTitle = responses.filter { response ->
            val candidate = normalizeForMatch(baseTitleForMatching(response.name))
            songTitle.isNotBlank() && candidate.isNotBlank() &&
                (candidate == songTitle || containsWholePhrase(candidate, songTitle) || containsWholePhrase(songTitle, candidate))
        }.sortedByDescending { hasSyncedLyrics(it) }
        val ordered = (ranked + looseTitle).distinctBy { it.id }
            .ifEmpty { responses.sortedByDescending { hasSyncedLyrics(it) } }
        return ordered.mapNotNull { toHit(it, "lrclib_title_artist") }
    }

    /** LRCLIB answers 404 when nothing matches and 400 when a lookup's parameters are unusable. */
    private fun Throwable.isLrcLibNoMatch(): Boolean =
        this is HttpException && (code() == 404 || code() == 400)

    private fun buildAutomaticSearchStrategies(
        cleanTitle: String,
        cleanArtist: String
    ): List<RemoteSearchStrategy> {
        val simplifiedArtist = cleanArtist.split(" feat.", " ft.", " featuring", " & ", " , ", ", ", "; ", " / ", " x ").first().trim()
        val simplifiedTitle = cleanTitle.split(" feat.", " ft.", " featuring", " (").first().trim()
        val artistKnown = !isUnknownArtist(cleanArtist)

        return buildList {
            if (artistKnown) {
                add(RemoteSearchStrategy("track+artist") {
                    lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                })
                add(RemoteSearchStrategy("combined_query") {
                    lrcLibApiService.searchLyrics(query = "$cleanArtist $cleanTitle")
                })
                if (simplifiedArtist != cleanArtist || simplifiedTitle != cleanTitle) {
                    add(RemoteSearchStrategy("simplified_track+artist") {
                        lrcLibApiService.searchLyrics(trackName = simplifiedTitle, artistName = simplifiedArtist)
                    })
                }
            }
            // Title only: results are still filtered by artist + duration during ranking, and this
            // catches uploads whose artist string is formatted differently ("A & B" vs "A, B").
            if (cleanTitle.isNotBlank()) {
                add(RemoteSearchStrategy("track_only") {
                    lrcLibApiService.searchLyrics(trackName = cleanTitle)
                })
            }

            if (MultiLangRomanizer.isScriptThatNeedsRomanization(cleanTitle)) {
                val romanTitle = romanizeForMatch(cleanTitle)
                if (romanTitle != cleanTitle) {
                    add(RemoteSearchStrategy("romanized_track") {
                        lrcLibApiService.searchLyrics(trackName = romanTitle, artistName = cleanArtist.takeIf { artistKnown })
                    })
                }
            }

            val smartTitle = cleanTitleSmart(cleanTitle)
            if (smartTitle != cleanTitle && smartTitle.isNotBlank()) {
                add(RemoteSearchStrategy("smart_track_only") {
                    lrcLibApiService.searchLyrics(trackName = smartTitle)
                })
            }
        }
    }

    /** Runs every strategy in parallel (bounded by [timeoutMs] each) and merges all results. */
    private suspend fun runSearchStrategiesAll(
        strategies: List<RemoteSearchStrategy>,
        timeoutMs: Long = 12_000L
    ): RemoteSearchOutcome = coroutineScope {
        if (strategies.isEmpty()) return@coroutineScope RemoteSearchOutcome(emptyList(), hadFailure = false)
        val failures = AtomicInteger(0)
        val batches = strategies.map { strategy ->
            async {
                withTimeoutOrNull(timeoutMs) {
                    runCatching {
                        withNetworkRetry(operationName = "lrclib_strategy:${strategy.name}") { strategy.request() }
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.d(TAG, "Strategy ${strategy.name} failed after retries: ${error.message}")
                        failures.incrementAndGet()
                    }.getOrNull()?.toList().orEmpty()
                } ?: run {
                    failures.incrementAndGet()
                    emptyList()
                }
            }
        }.awaitAll()
        val merged = batches.flatten().distinctBy { it.id }
        val failed = failures.get()
        RemoteSearchOutcome(
            responses = merged,
            hadFailure = failed == strategies.size || (failed > 0 && merged.isEmpty())
        )
    }

    /** Best automatic synced match: strict pass first, then the relaxed synced pass. */
    private fun pickBestSynced(song: Song, responses: Collection<LrcLibResponse>): LrcLibResponse? {
        val synced = responses.filter { hasSyncedLyrics(it) }
        if (synced.isEmpty()) return null
        return (rankRemoteLyricsMatches(song, synced, RemoteLyricsMatchMode.AUTOMATIC) +
            rankRemoteLyricsMatches(song, synced, RemoteLyricsMatchMode.RELAXED_SYNCED))
            .asSequence()
            .map { it.response }
            .firstOrNull { response ->
                LyricsUtils.parseLyrics(response.syncedLyrics).synced?.isNotEmpty() == true
            }
    }

    private fun toHit(response: LrcLibResponse, provider: String): RemoteLyricsHit? {
        response.syncedLyrics?.takeIf { it.isNotBlank() }?.let { raw ->
            val parsed = LyricsUtils.parseLyrics(raw).copy(areFromRemote = true)
            if (!parsed.synced.isNullOrEmpty()) return RemoteLyricsHit(raw, parsed, provider, response)
        }
        response.plainLyrics?.takeIf { it.isNotBlank() }?.let { raw ->
            val parsed = LyricsUtils.parseLyrics(raw).copy(areFromRemote = true)
            if (parsed.isValid()) return RemoteLyricsHit(raw, parsed, provider, response)
        }
        return null
    }

    /**
     * NetEase fallback: metadata search, rank candidates with the same title/artist/duration
     * rules as LRCLIB, then fetch LRC for the best few until one is really synced.
     */
    private suspend fun searchNetEaseSynced(
        song: Song,
        cleanTitle: String,
        cleanArtist: String,
        mode: RemoteLyricsMatchMode = RemoteLyricsMatchMode.AUTOMATIC,
        maxLyricFetches: Int = 3
    ): RemoteLyricsHit? {
        if (cleanTitle.isBlank()) return null
        val artistKnown = !isUnknownArtist(cleanArtist)
        val simplifiedArtist = cleanArtist.split(" feat.", " ft.", " featuring", " & ", ", ", "; ", " / ", " x ").first().trim()
        val queries = buildList {
            if (artistKnown) add("$cleanTitle $simplifiedArtist")
            add(cleanTitle)
        }.distinct()

        val candidates = LinkedHashMap<Long, NetEaseLyricsProvider.Candidate>()
        var searchFailures = 0
        for (query in queries) {
            val found = runCatching {
                withNetworkRetry(operationName = "netease_search") { netEaseProvider.search(query) }
            }.onFailure { if (it is CancellationException) throw it; searchFailures++ }
                .getOrNull()
                .orEmpty()
            found.forEach { candidates.putIfAbsent(it.id, it) }
            // The artist+title query is precise enough; only widen when it found nothing usable.
            if (candidates.isNotEmpty()) break
        }
        if (candidates.isEmpty()) {
            if (searchFailures == queries.size) throw IOException("NetEase search unavailable")
            return null
        }

        // Rank on metadata with a placeholder so the stricter synced duration window applies.
        val placeholders = candidates.values.associateBy { netEaseProvider.toResponse(it, SYNCED_PLACEHOLDER).id }
        val metaResponses = candidates.values.map { netEaseProvider.toResponse(it, SYNCED_PLACEHOLDER) }
        val modes = if (mode == RemoteLyricsMatchMode.CANDIDATE) {
            listOf(RemoteLyricsMatchMode.CANDIDATE)
        } else {
            listOf(RemoteLyricsMatchMode.AUTOMATIC, RemoteLyricsMatchMode.RELAXED_SYNCED)
        }
        val ordered = modes
            .flatMap { rankRemoteLyricsMatches(song, metaResponses, it) }
            .map { it.response.id }
            .distinct()
            .take(maxLyricFetches)

        for (id in ordered) {
            val candidate = placeholders[id] ?: continue
            val lrc = runCatching {
                withNetworkRetry(operationName = "netease_lyric") { netEaseProvider.fetchSyncedLrc(candidate.id) }
            }.onFailure { if (it is CancellationException) throw it }
                .getOrNull()
                ?: continue
            toHit(netEaseProvider.toResponse(candidate, lrc), "netease")
                ?.takeIf { it.isSynced }
                ?.let { return it }
        }
        return null
    }

    /** Writes a remote result to the lyrics table, the JSON cache and the in-memory cache. */
    private fun persistRemoteHitToCaches(song: Song, hit: RemoteLyricsHit) {
        lyricsCache.put(generateCacheKey(song.id), hit.lyrics)
        saveLocalLyricsJson(song, hit.lyrics)
    }

    private suspend fun persistRemoteHit(song: Song, hit: RemoteLyricsHit) {
        song.id.toLongOrNull()?.let { songId ->
            runCatching {
                lyricsDao.insert(
                    com.theveloper.pixelplay.data.database.LyricsEntity(
                        songId = songId,
                        content = hit.rawLyrics,
                        isSynced = hit.isSynced,
                        source = "remote"
                    )
                )
            }.onFailure { Log.w(TAG, "Could not persist remote lyrics for ${song.id}: ${it.message}") }
        } ?: Log.d(TAG, "Non-numeric song id ${song.id}: lyrics cached in JSON only")
        persistRemoteHitToCaches(song, hit)
    }

    override suspend fun upgradeToSynced(song: Song, ignoreBackoff: Boolean): Lyrics? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        val stored = loadStoredLyrics(song, cacheKey, includeMemoryCache = true)
        if (stored != null && !stored.first.synced.isNullOrEmpty()) {
            if (hasWordTiming(stored.first)) return@withContext stored.first
            // Line-timed: look for a word-timed version of the same lyrics.
            val upgraded = upgradeLineToWordTimed(song, stored.first, ignoreBackoff)
            return@withContext upgraded ?: stored.first
        }

        if (!ignoreBackoff && !syncedMissStore.shouldRetry(song.id)) return@withContext null

        // A sidecar .lrc may have appeared since the last scan — cheap, check it first.
        findLocalLyricsFile(song)?.takeIf { !it.synced.isNullOrEmpty() }?.let { local ->
            lyricsCache.put(cacheKey, local)
            return@withContext local
        }

        val result = findBestRemoteLyrics(song, requireSynced = true)
        val hit = result.hit?.takeIf { it.isSynced } ?: return@withContext null
        persistRemoteHit(song, hit)
        hit.lyrics
    }

    override suspend fun prefetchLyrics(song: Song): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheKey = generateCacheKey(song.id)
            val stored = loadStoredLyrics(song, cacheKey, includeMemoryCache = true)
            if (stored != null) {
                lyricsCache.put(cacheKey, stored.first)
                if (!stored.first.synced.isNullOrEmpty()) {
                    if (!hasWordTiming(stored.first)) {
                        upgradeLineToWordTimed(song, stored.first, ignoreBackoff = false)
                    }
                    return@withContext true
                }
                return@withContext upgradeToSynced(song, ignoreBackoff = false) != null
            }

            findLocalLyricsFile(song)?.let { local ->
                lyricsCache.put(cacheKey, local)
                if (!local.synced.isNullOrEmpty()) return@withContext true
            }

            if (!syncedMissStore.shouldRetry(song.id)) return@withContext false
            val hit = findBestRemoteLyrics(song, requireSynced = false).hit ?: return@withContext false
            persistRemoteHit(song, hit)
            hit.isSynced
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Prefetch failed for ${song.title}: ${e.message}")
            false
        }
    }

    /** A word-timed result for the manual pick list (lenient candidate matching). */
    private suspend fun wordTimedCandidateResult(originalSong: Song): LyricsSearchResult? {
        val song = originalSong.forLyricsLookup()
        val cleanArtist = song.displayArtist.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        val cleanTitle = song.title.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        val hit = findWordTimedLyricsSafely(song, cleanTitle, cleanArtist, RemoteLyricsMatchMode.CANDIDATE).hit ?: return null
        val record = hit.response ?: return null
        return LyricsSearchResult(record, hit.lyrics, hit.rawLyrics)
    }

    /** A NetEase synced result for the manual pick list (lenient candidate matching). */
    private suspend fun netEaseCandidateResult(originalSong: Song): LyricsSearchResult? {
        val song = originalSong.forLyricsLookup()
        val cleanArtist = song.displayArtist.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        val cleanTitle = song.title.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        val hit = runCatching {
            searchNetEaseSynced(song, cleanTitle, cleanArtist, mode = RemoteLyricsMatchMode.CANDIDATE, maxLyricFetches = 2)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return null
        val record = hit.response ?: LrcLibResponse(
            id = -1,
            name = song.title,
            artistName = song.displayArtist,
            albumName = song.album,
            duration = song.duration / 1000.0,
            plainLyrics = null,
            syncedLyrics = hit.rawLyrics
        )
        return LyricsSearchResult(record, hit.lyrics, hit.rawLyrics)
    }

    private fun hasLyrics(response: LrcLibResponse): Boolean =
        !response.plainLyrics.isNullOrBlank() || !response.syncedLyrics.isNullOrBlank()

    private fun hasSyncedLyrics(response: LrcLibResponse): Boolean =
        !response.syncedLyrics.isNullOrBlank()

    private fun rankRemoteLyricsMatches(
        song: Song,
        responses: List<LrcLibResponse>,
        mode: RemoteLyricsMatchMode
    ): List<RemoteLyricsMatch> {
        val songDurationSeconds = song.duration / 1000.0
        if (songDurationSeconds <= 0.0) return emptyList()

        return responses
            .mapNotNull { response ->
                val score = remoteLyricsMatchScore(
                    song = song,
                    response = response,
                    mode = mode,
                    songDurationSeconds = songDurationSeconds
                ) ?: return@mapNotNull null
                RemoteLyricsMatch(response, score)
            }
            .sortedWith(
                compareByDescending<RemoteLyricsMatch> { it.score }
                    .thenByDescending { hasSyncedLyrics(it.response) }
                    .thenBy { abs(it.response.duration - songDurationSeconds) }
            )
    }

    private fun remoteLyricsMatchScore(
        song: Song,
        response: LrcLibResponse,
        mode: RemoteLyricsMatchMode,
        songDurationSeconds: Double
    ): Int? {
        if (!hasLyrics(response) || response.duration <= 0.0) return null
        if (!variantDescriptorsCompatible(song, response)) return null

        val hasSynced = hasSyncedLyrics(response)
        val durationTolerance = remoteDurationToleranceSeconds(songDurationSeconds, hasSynced, mode)
        val durationDiff = abs(response.duration - songDurationSeconds)
        if (durationDiff > durationTolerance) return null

        val titleScore = titleMatchScore(song.title, response.name, mode) ?: return null
        val artistScore = artistMatchScore(song.displayArtist, response.artistName)
        if (!isUnknownArtist(song.displayArtist) && artistScore == null) return null

        if (mode == RemoteLyricsMatchMode.RELAXED_SYNCED) {
            // The wider duration window is only safe when we are sure it is the same song.
            if (!hasSynced) return null
            if (titleScore < 58) return null
            if (isUnknownArtist(song.displayArtist) || (artistScore ?: 0) < 22) return null
        }

        val durationScore = (durationTolerance - durationDiff).coerceAtLeast(0.0).toInt()
        val syncedScore = if (hasSynced) 10 else 0
        return titleScore + (artistScore ?: 0) + durationScore + syncedScore
    }

    private fun remoteDurationToleranceSeconds(
        songDurationSeconds: Double,
        hasSyncedLyrics: Boolean,
        mode: RemoteLyricsMatchMode
    ): Double {
        return when (mode) {
            RemoteLyricsMatchMode.AUTOMATIC -> {
                if (hasSyncedLyrics) {
                    (songDurationSeconds * 0.02).coerceIn(5.0, 8.0)
                } else {
                    (songDurationSeconds * 0.04).coerceIn(8.0, 15.0)
                }
            }
            RemoteLyricsMatchMode.RELAXED_SYNCED -> (songDurationSeconds * 0.05).coerceIn(9.0, 15.0)
            RemoteLyricsMatchMode.CANDIDATE -> 15.0
        }
    }

    private fun titleMatchScore(songTitle: String, responseTitle: String, mode: RemoteLyricsMatchMode): Int? {
        val songBase = baseTitleForMatching(songTitle)
        val responseBase = baseTitleForMatching(responseTitle)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 70

        // Attempt Romanized match for non-Latin scripts
        if (MultiLangRomanizer.isScriptThatNeedsRomanization(songBase) || 
            MultiLangRomanizer.isScriptThatNeedsRomanization(responseBase)) {
            val songRoman = normalizeForMatch(romanizeForMatch(songBase))
            val responseRoman = normalizeForMatch(romanizeForMatch(responseBase))
            if (songRoman == responseRoman && songRoman.isNotBlank()) return 65
        }

        val songTokens = matchTokens(songBase)
        val responseTokens = matchTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        if (songTokens.size == 1 || responseTokens.size == 1) {
            if (songTokens == responseTokens) return 60
            
            // Fuzzy match for single token CJK: if one contains the other
            val s1 = songBase.replace(" ", "")
            val s2 = responseBase.replace(" ", "")
            if (s1.isNotBlank() && s2.isNotBlank()) {
                if (s1.contains(s2) || s2.contains(s1)) return 55
            }
            
            return null
        }

        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            return if (mode == RemoteLyricsMatchMode.AUTOMATIC) 58 else 54
        }

        val overlap = songTokens.intersect(responseTokens).size
        val songCoverage = overlap.toDouble() / songTokens.size
        val responseCoverage = overlap.toDouble() / responseTokens.size
        val requiredSongCoverage = if (mode == RemoteLyricsMatchMode.AUTOMATIC) 0.85 else 0.75
        val requiredResponseCoverage = if (mode == RemoteLyricsMatchMode.AUTOMATIC) 0.70 else 0.55

        return if (songCoverage >= requiredSongCoverage && responseCoverage >= requiredResponseCoverage) {
            45
        } else {
            null
        }
    }

    private fun artistMatchScore(songArtist: String, responseArtist: String): Int? {
        if (isUnknownArtist(songArtist)) return 0

        val songBase = normalizeForMatch(songArtist)
        val responseBase = normalizeForMatch(responseArtist)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 30
        
        // Attempt Romanized match
        if (MultiLangRomanizer.isScriptThatNeedsRomanization(songBase) || 
            MultiLangRomanizer.isScriptThatNeedsRomanization(responseBase)) {
            val songRoman = normalizeForMatch(romanizeForMatch(songBase))
            val responseRoman = normalizeForMatch(romanizeForMatch(responseBase))
            if (songRoman == responseRoman && songRoman.isNotBlank()) return 28
        }

        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            return 22
        }

        val songTokens = artistTokens(songBase)
        val responseTokens = artistTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        val overlap = songTokens.intersect(responseTokens).size
        val smallerArtistCoverage = overlap.toDouble() / minOf(songTokens.size, responseTokens.size)
        return if (smallerArtistCoverage >= 0.5) 12 else null
    }

    private fun variantDescriptorsCompatible(song: Song, response: LrcLibResponse): Boolean {
        val songVariants = timingVariantTokens(song.title) + timingVariantTokensFromFileName(song)
        val responseVariants = timingVariantTokens(response.name)

        if (songVariants.isEmpty()) {
            return responseVariants.isEmpty()
        }

        return responseVariants == songVariants
    }

    private fun baseTitleForMatching(title: String): String {
        var base = title.replace(Regex("""^\s*\d{1,3}\s*[\._-]\s+"""), "")

        base = BRACKETED_QUALIFIER_REGEX.replace(base) { match ->
            val qualifier = match.groupValues.getOrNull(1).orEmpty()
            if (shouldDropTitleQualifier(qualifier)) " " else " $qualifier "
        }

        var parts = TITLE_SEPARATOR_REGEX.split(base)
        while (parts.size > 1 && shouldDropTitleQualifier(parts.last())) {
            parts = parts.dropLast(1)
        }

        return normalizeForMatch(parts.joinToString(" "))
    }

    private fun shouldDropTitleQualifier(value: String): Boolean {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return true
        return FEATURE_QUALIFIER_REGEX.containsMatchIn(value) ||
            timingVariantTokens(value).isNotEmpty() ||
            normalized in TITLE_DROP_QUALIFIERS
    }

    private fun timingVariantTokens(value: String): Set<String> {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return emptySet()

        val tokens = matchTokens(normalized)
        val variants = tokens
            .filter { it in TIMING_VARIANT_KEYWORDS }
            .toMutableSet()

        if (Regex("""\bmash\s+up\b""").containsMatchIn(normalized)) {
            variants += "mashup"
        }
        if ("versus" in tokens || "vs" in tokens) {
            variants += "mashup"
        }

        return variants
    }

    private fun timingVariantTokensFromFileName(song: Song): Set<String> {
        val fileName = songFileName(song)
        if (fileName.isBlank()) return emptySet()

        val variants = BRACKETED_QUALIFIER_REGEX
            .findAll(fileName)
            .flatMap { match -> timingVariantTokens(match.groupValues.getOrNull(1).orEmpty()) }
            .toMutableSet()

        val titleBase = baseTitleForMatching(song.title)
        if (titleBase.isBlank()) return variants

        TITLE_SEPARATOR_REGEX.split(fileName).forEach { part ->
            val normalizedPart = normalizeForMatch(part)
            if (normalizedPart.startsWith("$titleBase ")) {
                variants += timingVariantTokens(normalizedPart.removePrefix(titleBase).trim())
            }
        }

        return variants
    }

    private fun songFileName(song: Song): String {
        if (song.path.isBlank()) return ""
        return runCatching { File(song.path).nameWithoutExtension }.getOrDefault("")
    }

    private fun artistTokens(normalizedArtist: String): Set<String> =
        matchTokens(normalizedArtist)
            .filterNot { it in ARTIST_CONNECTOR_TOKENS }
            .toSet()

    private fun matchTokens(normalizedValue: String): Set<String> =
        normalizedValue
            .split(' ')
            .filter { it.isNotBlank() }
            .toSet()

    private fun containsWholePhrase(haystack: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        return Regex("""(?:^|\s)${Regex.escape(needle)}(?:\s|$)""").containsMatchIn(haystack)
    }

    private fun normalizeForMatch(value: String): String {
        val withoutDiacritics = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

        return withoutDiacritics
            .replace("&", " and ")
            .replace(Regex("""[\u2019'`]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun isUnknownArtist(value: String): Boolean =
        normalizeForMatch(value) in UNKNOWN_ARTISTS

    /**
     * Find local .lrc file next to the music file (matching Rhythm)
     */
    private suspend fun findLocalLyricsFile(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val songFile = File(song.path)
            val directory = songFile.parentFile ?: return@withContext null
            val songNameWithoutExt = songFile.nameWithoutExtension

            if (directory.exists()) {
                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                    val lyricsFile = File(directory, "$songNameWithoutExt.$extension")
                    if (!lyricsFile.exists() || !lyricsFile.canRead()) continue

                    val validated = readValidatedLocalLyrics(lyricsFile)
                    if (validated != null) {
                        Log.d(TAG, "===== FOUND LOCAL LYRICS FILE: ${lyricsFile.name} =====")
                        return@withContext validated.parsedLyrics
                    }
                }

                val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")

                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                    val alternativeLyricsFile = File(directory, "${cleanArtist}_${cleanTitle}.$extension")
                    if (!alternativeLyricsFile.exists() || !alternativeLyricsFile.canRead()) continue

                    val validated = readValidatedLocalLyrics(alternativeLyricsFile)
                    if (validated != null) {
                        Log.d(TAG, "===== FOUND LOCAL LYRICS FILE (alt pattern): ${alternativeLyricsFile.name} =====")
                        return@withContext validated.parsedLyrics
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for local lyrics file", e)
        }
        return@withContext null
    }

    private fun readValidatedLocalLyrics(file: File): com.theveloper.pixelplay.utils.ValidatedLyricsImport? {
        return when (val validation = LyricsImportSecurity.validateLocalLyricsFile(file)) {
            is LyricsImportValidationResult.Valid -> validation.value
            is LyricsImportValidationResult.Invalid -> null
        }
    }

    /**
     * Save lyrics to JSON disk cache (matching Rhythm)
     */
    private fun saveLocalLyricsJson(song: Song, lyrics: Lyrics) {
        try {
            val fileName = "${song.id}.json"
            val lyricsDir = File(context.filesDir, "lyrics")
            lyricsDir.mkdirs()

            val wordByWordLyrics = lyrics.synced
                ?.takeIf { lines -> lines.any { !it.words.isNullOrEmpty() } }
                ?.let { lines ->
                    // Native timing JSON keeps word end times (enhanced LRC only has starts).
                    if (lines.any { line -> line.words.orEmpty().any { it.endTime != null } }) {
                        encodeNativeTimingOrNull(lyrics) ?: toWordByWordLrc(lines)
                    } else {
                        toWordByWordLrc(lines)
                    }
                }

            val lyricsData = LyricsData(
                plainLyrics = lyrics.plain?.joinToString("\n"),
                syncedLyrics = lyrics.synced?.joinToString("\n") { "[${formatTimestamp(it.time)}]${it.line}" },
                wordByWordLyrics = wordByWordLyrics
            )

            val file = File(lyricsDir, fileName)
            val json = gson.toJson(lyricsData)
            file.writeText(json)
            Log.d(TAG, "Saved lyrics to JSON cache: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics to JSON cache: ${e.message}", e)
        }
    }

    /**
     * Load lyrics from JSON disk cache (matching Rhythm)
     */
    private suspend fun loadLocalLyricsJson(song: Song): Lyrics? {
        try {
            val data = readLyricsJsonCache(song) ?: return null
            if (data.hasLyrics()) {
                val rawLyrics = data.wordByWordLyrics ?: data.syncedLyrics ?: data.plainLyrics
                val parsed = LyricsUtils.parseLyrics(rawLyrics)
                if (parsed.isValid()) {
                    val hasWordTimestamps = parsed.synced?.any { !it.words.isNullOrEmpty() } == true
                    if (!hasWordTimestamps && data.wordByWordLyrics.isNullOrBlank()) {
                        // Legacy cache may have flattened word-by-word lines.
                        // Recover richer raw lyrics from DB when available.
                        val persistedContent = song.id.toLongOrNull()
                            ?.let { lyricsDao.getLyrics(it)?.content }
                            ?.takeIf { it.isNotBlank() }
                        if (persistedContent != null) {
                            val recovered = LyricsUtils.parseLyrics(persistedContent)
                            val recoveredHasWords = recovered.synced?.any { !it.words.isNullOrEmpty() } == true
                            if (recovered.isValid() && recoveredHasWords) {
                                saveLocalLyricsJson(song, recovered)
                                return recovered
                            }
                        }

                        if (looksLikeFlattenedWordByWordCache(parsed)) {
                            // Force a remote re-fetch instead of serving degraded cache.
                            return null
                        }
                    }
                    return parsed
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading JSON cache: ${e.message}", e)
        }
        return null
    }

    /** Native timing JSON when it validates (so it will load back), else null. */
    private fun encodeNativeTimingOrNull(lyrics: Lyrics): String? = runCatching {
        val clean = lyrics.copy(synced = lyrics.synced?.let(WordLyricsParsers::normalize))
        if (LyricsTiming.validate(clean).isNotEmpty()) return null
        LyricsTiming.encode(clean)
    }.getOrNull()

    private fun formatTimestamp(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (timeMs % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    private fun toWordByWordLrc(lines: List<SyncedLine>): String {
        return lines.joinToString("\n") { line ->
            val linePrefix = "[${formatTimestamp(line.time)}]"
            val words = line.words
            if (words.isNullOrEmpty()) {
                linePrefix + line.line
            } else {
                val wordsPart = words.mapIndexed { index, word ->
                    val separator = if (index > 0 && word.startsNewWord) " " else ""
                    "$separator<${formatTimestamp(word.time)}>${word.word}"
                }
                    .joinToString("")
                linePrefix + wordsPart
            }
        }
    }

    private fun looksLikeFlattenedWordByWordCache(lyrics: Lyrics): Boolean {
        val synced = lyrics.synced ?: return false
        var suspiciousLines = 0

        for (line in synced) {
            val text = line.line
            if (text.isBlank() || text.any { it.isWhitespace() }) continue

            val hasLongLatinRun = Regex("[A-Za-z]{10,}").containsMatchIn(text)
            if (hasLongLatinRun) {
                suspiciousLines += 1
                if (suspiciousLines >= 2) return true
            }
        }

        return false
    }

    /**
     * Load embedded lyrics from audio file metadata
     */
    private suspend fun loadEmbeddedLyricsFromMetadata(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        if (song.contentUriString.isEmpty()) {
            return@withContext null
        }

        // Then try to read from file metadata
        return@withContext try {
            val uri = song.contentUriString.toUri()
            val tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                LogUtils.w(this@LyricsRepositoryImpl, "Could not create temp file from URI: ${song.contentUriString}")
                return@withContext null
            }

            try {
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val metadata = TagLib.getMetadata(fd.detachFd())
                    val propertyMap = metadata?.propertyMap
                    val parsedLyrics = parseBestEmbeddedLyricsField(propertyMap)

                    if (parsedLyrics != null) {
                        Log.d(TAG, "===== FOUND EMBEDDED LYRICS =====")
                        parsedLyrics
                    } else {
                        null
                    }
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error reading lyrics from file metadata")
            null
        }
    }

    /**
     * Returns persisted lyrics without network. All stored copies are considered (song tag,
     * lyrics table, JSON cache, memory) and a synced copy always beats a plain one — otherwise
     * plain lyrics from the file tag would hide synced lyrics fetched later into the table.
     */
    private suspend fun loadStoredLyrics(
        song: Song,
        cacheKey: String,
        includeMemoryCache: Boolean
    ): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        var plainFallback: Pair<Lyrics, String>? = null
        var lineFallback: Pair<Lyrics, String>? = null

        // Word-timed beats line-timed beats plain, across every stored copy — otherwise line
        // lyrics in the file tag would hide a word-timed upgrade stored in the lyrics table.
        fun consider(rawLyrics: String?): Pair<Lyrics, String>? {
            val raw = rawLyrics?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val parsed = parseStoredLyrics(raw) ?: return null
            if (!parsed.synced.isNullOrEmpty()) {
                if (hasWordTiming(parsed)) return parsed to raw
                if (lineFallback == null) lineFallback = parsed to raw
                return null
            }
            if (plainFallback == null) plainFallback = parsed to raw
            return null
        }

        consider(song.lyrics)?.let { return@withContext it }

        song.id.toLongOrNull()
            ?.let { runCatching { lyricsDao.getLyrics(it)?.content }.getOrNull() }
            ?.let { consider(it) }
            ?.let { return@withContext it }

        runCatching { readLyricsJsonCache(song) }.getOrNull()
            ?.takeIf { it.hasLyrics() }
            ?.let { data -> consider(data.wordByWordLyrics ?: data.syncedLyrics ?: data.plainLyrics) }
            ?.let { return@withContext it }

        if (includeMemoryCache) {
            lyricsCache.get(cacheKey)?.let { cached ->
                lyricsToRawContent(cached)?.let { rawLyrics ->
                    if (!cached.synced.isNullOrEmpty()) {
                        if (hasWordTiming(cached)) return@withContext cached to rawLyrics
                        if (lineFallback == null) lineFallback = cached to rawLyrics
                    } else if (plainFallback == null) {
                        plainFallback = cached to rawLyrics
                    }
                }
            }
        }

        lineFallback ?: plainFallback
    }

    private fun parseStoredLyrics(rawLyrics: String): Lyrics? {
        val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics)
        return parsedLyrics
            .takeIf { it.isValid() }
            ?.copy(areFromRemote = false)
    }

    private fun lyricsToRawContent(lyrics: Lyrics): String? {
        val syncedLyrics = lyrics.synced
        if (!syncedLyrics.isNullOrEmpty()) {
            val hasWordTimestamps = syncedLyrics.any { !it.words.isNullOrEmpty() }
            val hasWordEnds = syncedLyrics.any { line -> line.words.orEmpty().any { it.endTime != null } }
            return if (hasWordEnds) {
                encodeNativeTimingOrNull(lyrics) ?: toWordByWordLrc(syncedLyrics)
            } else if (hasWordTimestamps) {
                toWordByWordLrc(syncedLyrics)
            } else {
                syncedLyrics.joinToString("\n") { line ->
                    "[${formatTimestamp(line.time)}]${line.line}"
                }
            }
        }

        return lyrics.plain
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
            ?.takeIf { it.isNotBlank() }
    }

    private fun readLyricsJsonCache(song: Song): LyricsData? {
        val fileName = "${song.id}.json"
        val file = File(context.filesDir, "lyrics/$fileName")
        if (!file.exists()) return null

        val json = file.readText()
        return gson.fromJson(json, LyricsData::class.java)
    }

    // ========== Original methods (kept for backward compatibility) ==========

    override suspend fun fetchFromRemote(song: Song): Result<Pair<Lyrics, String>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(this@LyricsRepositoryImpl, "Fetching lyrics from remote for: ${song.title}")

            val cacheKey = generateCacheKey(song.id)
            loadStoredLyrics(song, cacheKey, includeMemoryCache = true)?.let { stored ->
                lyricsCache.put(cacheKey, stored.first)
                if (!stored.first.synced.isNullOrEmpty()) {
                    LogUtils.d(
                        this@LyricsRepositoryImpl,
                        "Skipping remote lyrics fetch because stored synced lyrics already exist for: ${song.title}"
                    )
                    return@withContext Result.success(stored)
                }
                // Stored copy is plain: an explicit fetch always tries for a synced version.
                val upgraded = upgradeToSynced(song, ignoreBackoff = true)
                val upgradedRaw = upgraded?.let { lyricsToRawContent(it) }
                return@withContext if (upgraded != null && upgradedRaw != null) {
                    Result.success(upgraded to upgradedRaw)
                } else {
                    Result.success(stored)
                }
            }

            // Synced-first automatic pipeline (LRCLIB exact + all searches, then NetEase).
            findBestRemoteLyrics(song, requireSynced = false).hit?.let { hit ->
                persistRemoteHit(song, hit)
                return@withContext Result.success(hit.lyrics to hit.rawLyrics)
            }

            // Last resort: the more lenient candidate search, then pick the best match
            val searchResult = searchRemote(song)
            if (searchResult.isSuccess) {
                val (_, results) = searchResult.getOrThrow()
                if (results.isNotEmpty()) {
                    // Pick the first result (already sorted by synced priority)
                    val best = results.first()
                    val rawLyricsToSave = best.rawLyrics

                    try {
                        lyricsDao.insert(
                             com.theveloper.pixelplay.data.database.LyricsEntity(
                                 songId = song.id.toLong(),
                                 content = rawLyricsToSave,
                                 isSynced = !best.lyrics.synced.isNullOrEmpty(),
                                 source = "remote"
                             )
                        )
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Skipping DB update for non-numeric ID: ${song.id}")
                    }

                    lyricsCache.put(cacheKey, best.lyrics)
                    saveLocalLyricsJson(song, best.lyrics)
                    LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics for: ${song.title}")

                    return@withContext Result.success(Pair(best.lyrics, rawLyricsToSave))
                }
            }

            // Fallback: Try the exact match API (less likely to succeed, but worth a shot).
            // A 400/404 here just means "no match" — never surface it as a server error.
            val lookup = song.forLyricsLookup()
            val response = if (lookup.title.isBlank() || lookup.displayArtist.isBlank()) null else runCatching {
                withNetworkRetry(operationName = "lrclib_get_lyrics") {
                    lrcLibApiService.getLyrics(
                        trackName = lookup.title,
                        artistName = lookup.displayArtist,
                        albumName = lookup.album.trim().takeIf { it.isNotBlank() },
                        duration = (song.duration / 1000).toInt().takeIf { it > 0 }
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!error.isLrcLibNoMatch()) throw error
            }.getOrNull()

            val exactMatch = response
                ?.let { rankRemoteLyricsMatches(lookup, listOf(it), RemoteLyricsMatchMode.AUTOMATIC).firstOrNull()?.response }

            if (exactMatch != null) {
                val rawLyricsToSave = exactMatch.syncedLyrics ?: exactMatch.plainLyrics
                    ?: return@withContext Result.failure(NoLyricsFoundException())

                val parsedLyrics = LyricsUtils.parseLyrics(rawLyricsToSave).copy(areFromRemote = true)
                if (!parsedLyrics.isValid()) {
                    return@withContext Result.failure(LyricsException("Parsed lyrics are empty"))
                }

                try {
                    lyricsDao.insert(
                        com.theveloper.pixelplay.data.database.LyricsEntity(
                            songId = song.id.toLong(),
                            content = rawLyricsToSave,
                            isSynced = !parsedLyrics.synced.isNullOrEmpty(),
                            source = "remote"
                        )
                    )
                } catch (e: NumberFormatException) {
                    Log.w(TAG, "Skipping DB update for non-numeric ID in fallback: ${song.id}")
                }

                lyricsCache.put(cacheKey, parsedLyrics)
                saveLocalLyricsJson(song, parsedLyrics)
                LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics (exact match) for: ${song.title}")

                Result.success(Pair(parsedLyrics, rawLyricsToSave))
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException())
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error fetching lyrics from remote")
            when {
                e.isLrcLibNoMatch() -> Result.failure(NoLyricsFoundException())
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.lyrics_failed_to_fetch_from_remote), e))
            }
        }
    }

    override suspend fun searchRemote(song: Song): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        @Suppress("NAME_SHADOWING") val song = song.forLyricsLookup()
        try {
            LogUtils.d(this@LyricsRepositoryImpl, "Searching remote for lyrics for: ${song.title} by ${song.displayArtist}")

            val combinedQuery = "${song.title} ${song.displayArtist}"
            val cleanTitle = song.title.trim()
            val cleanArtist = song.displayArtist.trim()

            // FAST STRATEGY: run all requests in parallel, keep first non-empty batch
            // FAST STRATEGY: run all requests in parallel, keep first non-empty batch
            val strategies = buildList {
                add(RemoteSearchStrategy("query+artist") {
                    lrcLibApiService.searchLyrics(query = combinedQuery, artistName = cleanArtist)
                })
                add(RemoteSearchStrategy("track+artist") {
                    lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                })
                
                // Smart title cleanup strategy
                val smartTitle = cleanTitleSmart(cleanTitle)
                if (smartTitle != cleanTitle && smartTitle.isNotBlank()) {
                    LogUtils.d(this@LyricsRepositoryImpl, "Adding smart search strategy for: '$smartTitle' (orig: '$cleanTitle')")
                    add(RemoteSearchStrategy("smart_track_only") {
                        lrcLibApiService.searchLyrics(trackName = smartTitle)
                    })
                }

                if (cleanTitle.isNotBlank()) {
                    add(RemoteSearchStrategy("track_only") {
                        lrcLibApiService.searchLyrics(trackName = cleanTitle)
                    })
                    add(RemoteSearchStrategy("query_title_only") {
                        lrcLibApiService.searchLyrics(query = cleanTitle)
                    })
                }
            }

            val uniqueResults = runSearchStrategiesFast(strategies)

            if (uniqueResults.isNotEmpty()) {
                val rankedMatches = rankRemoteLyricsMatches(
                    song = song,
                    responses = uniqueResults,
                    mode = RemoteLyricsMatchMode.CANDIDATE
                )
                var results = rankedMatches.mapNotNull { match ->
                    val response = match.response
                    val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                    val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                    if (!parsedLyrics.isValid()) {
                        LogUtils.w(this@LyricsRepositoryImpl, "Parsed lyrics are empty for: ${song.title}")
                        return@mapNotNull null
                    }
                    val hasSynced = !response.syncedLyrics.isNullOrEmpty()
                    LogUtils.d(this@LyricsRepositoryImpl, "  Found: ${response.name} by ${response.artistName} (synced: $hasSynced)")
                    LyricsSearchResult(response, parsedLyrics, rawLyrics)
                }

                if (results.none { !it.lyrics.synced.isNullOrEmpty() }) {
                    netEaseCandidateResult(song)?.let { results = results + it }
                }
                wordTimedCandidateResult(song)?.let { results = listOf(it) + results }

                if (results.isNotEmpty()) {
                    val syncedCount = results.count { !it.record.syncedLyrics.isNullOrEmpty() }
                    LogUtils.d(this@LyricsRepositoryImpl, "Found ${results.size} lyrics for: ${song.title} ($syncedCount with synced)")
                    Result.success(Pair(combinedQuery, results))
                } else {
                    LogUtils.d(this@LyricsRepositoryImpl, "No matching lyrics found for: ${song.title}")
                    Result.failure(NoLyricsFoundException(combinedQuery))
                }
            } else {
                val fallback = listOfNotNull(wordTimedCandidateResult(song), netEaseCandidateResult(song))
                if (fallback.isNotEmpty()) {
                    Result.success(Pair(combinedQuery, fallback))
                } else {
                    LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                    Result.failure(NoLyricsFoundException(combinedQuery))
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error searching remote for lyrics")
            when {
                e.isLrcLibNoMatch() -> Result.failure(NoLyricsFoundException("${song.title} ${song.displayArtist}"))
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.lyrics_failed_to_search), e))
            }
        }
    }

    override suspend fun searchRemoteByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.trim()
            val cleanArtist = artist?.trim()?.takeIf { it.isNotBlank() }
            val query = listOfNotNull(
                cleanTitle.takeIf { it.isNotBlank() },
                cleanArtist
            ).joinToString(" ")

            LogUtils.d(this@LyricsRepositoryImpl, "Manual lyrics search: title=$title, artist=$artist")

            val strategies = buildList {
                add(RemoteSearchStrategy("manual_query") { lrcLibApiService.searchLyrics(query = query) })
                if (!cleanArtist.isNullOrBlank()) {
                    add(
                        RemoteSearchStrategy("manual_track+artist") {
                            lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                        }
                    )
                }
            }

            // Run both in parallel and take the first non-empty result set.
            val responses = runSearchStrategiesFast(strategies)

            if (responses.isEmpty()) {
                return@withContext Result.failure(NoLyricsFoundException(query))
            }

            val results = responses.mapNotNull { response ->
                val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                val parsed = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                if (!parsed.isValid()) return@mapNotNull null

                LyricsSearchResult(response, parsed, rawLyrics)
            }.sortedByDescending { !it.record.syncedLyrics.isNullOrEmpty() }

            if (results.isEmpty()) {
                Result.failure(NoLyricsFoundException(query))
            } else {
                Result.success(Pair(query, results))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LogUtils.e(this@LyricsRepositoryImpl, e, "Manual search failed")
            if (e.isLrcLibNoMatch()) return@withContext Result.failure(NoLyricsFoundException("$title ${artist.orEmpty()}".trim()))
            Result.failure(LyricsException(context.getString(R.string.lyrics_failed_to_search), e)
            )
        }
    }

    override suspend fun updateLyrics(songId: Long, lyricsContent: String): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Updating lyrics for songId: $songId")

        val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)
        if (!parsedLyrics.isValid()) {
            LogUtils.w(this@LyricsRepositoryImpl, "Attempted to save empty lyrics for songId: $songId")
            return@withContext
        }

        lyricsDao.insert(
             com.theveloper.pixelplay.data.database.LyricsEntity(
                 songId = songId,
                 content = lyricsContent,
                 isSynced = parsedLyrics.synced?.isNotEmpty() == true,
                 source = "manual"
             )
        )

        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.put(cacheKey, parsedLyrics)
        // A stale (possibly word-timed) JSON cache would otherwise outrank the user's choice.
        runCatching { File(context.filesDir, "lyrics/$songId.json").takeIf { it.exists() }?.delete() }
        LogUtils.d(this@LyricsRepositoryImpl, "Updated and cached lyrics for songId: $songId")
    }

    override suspend fun resetLyrics(songId: Long): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting lyrics for songId: $songId")
        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.remove(cacheKey)
        syncedMissStore.clear(songId.toString())
        wordMissStore.clear(songId.toString())
        try {
            lyricsDao.deleteLyrics(songId)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing lyrics from DB for ID: $songId", e)
        }
        
        // Also remove JSON cache
        try {
            val file = File(context.filesDir, "lyrics/${songId}.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting JSON cache: ${e.message}")
        }
    }

    override suspend fun resetAllLyrics(): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting all lyrics")
        lyricsCache.evictAll()
        syncedMissStore.clearAll()
        wordMissStore.clearAll()
        lyricsDao.deleteAll()
        
        // Also clear JSON cache directory
        try {
            val lyricsDir = File(context.filesDir, "lyrics")
            if (lyricsDir.exists()) {
                lyricsDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing JSON cache: ${e.message}")
        }
    }

    override suspend fun scanAndAssignLocalLrcFiles(
        songs: List<Song>,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Starting bulk scan for .lrc files for ${songs.size} songs")
        val updatedCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)
        val total = songs.size

        val idsWithPersistedLyrics = songs
            .mapNotNull { it.id.toLongOrNull() }
            .chunked(900)
            .flatMap { chunk -> lyricsDao.getSongIdsWithLyrics(chunk) }
            .toHashSet()

        // Only scan songs that don't already have persisted lyrics.
        val songsToScan = songs.filter { song ->
            val songId = song.id.toLongOrNull()
            song.lyrics.isNullOrBlank() && (songId == null || songId !in idsWithPersistedLyrics)
        }
        val skippedCount = total - songsToScan.size
        processedCount.addAndGet(skippedCount)
        
        LogUtils.d(this@LyricsRepositoryImpl, "Skipping $skippedCount songs that already have lyrics. Scanning ${songsToScan.size} songs.")
        
        onProgress(processedCount.get(), total)
        
        if (songsToScan.isEmpty()) {
            return@withContext 0
        }

        val semaphore = Semaphore(8) // Limit concurrency

        coroutineScope {
            songsToScan.map { song ->
                async {
                    semaphore.withPermit {
                        try {
                            // Find lyrics file
                            val songFile = File(song.path)
                            val directory = songFile.parentFile
                            
                            if (directory != null && directory.exists()) {
                                var foundFile: File? = null
                                
                                // Strategy 1: Exact match name
                                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                                    val exactMatch = File(directory, "${songFile.nameWithoutExtension}.$extension")
                                    if (exactMatch.exists() && exactMatch.canRead()) {
                                        foundFile = exactMatch
                                        break
                                    }
                                }
                                
                                // Strategy 2: Artist - Title
                                if (foundFile == null) {
                                    val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                                        val altMatch = File(directory, "${cleanArtist}_${cleanTitle}.$extension")
                                        if (altMatch.exists() && altMatch.canRead()) {
                                            foundFile = altMatch
                                            break
                                        }
                                    }
                                }
                                
                                if (foundFile != null) {
                                    val validated = readValidatedLocalLyrics(foundFile)
                                    if (validated != null) {
                                        try {
                                            lyricsDao.insert(
                                                 com.theveloper.pixelplay.data.database.LyricsEntity(
                                                     songId = song.id.toLong(),
                                                     content = validated.sanitizedContent,
                                                     isSynced = validated.parsedLyrics.synced?.isNotEmpty() == true,
                                                     source = "local_file"
                                                 )
                                            )
                                            updatedCount.incrementAndGet()
                                            LogUtils.d(this@LyricsRepositoryImpl, "Auto-assigned lyrics from ${foundFile.name}")
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Skipping DB update for ID in scanner: ${song.id}", e)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error scanning lyrics for ${song.title}: ${e.message}")
                        }
                        
                        val current = processedCount.incrementAndGet()
                        if (current % 20 == 0 || current == total) {
                            onProgress(current, total)
                        }
                    }
                }
            }.awaitAll()
        }
        
        LogUtils.d(this@LyricsRepositoryImpl, "Bulk scan complete. Updated ${updatedCount.get()} songs.")
        return@withContext updatedCount.get()
    }

    override fun clearCache() {
        LogUtils.d(this, "Clearing lyrics in-memory cache")
        lyricsCache.evictAll()
    }

    private fun generateCacheKey(songId: String): String = songId

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else "temp_audio"
                    } else "temp_audio"
                } ?: "temp_audio"

                val tempFile = File.createTempFile("lyrics_", "_$fileName", context.cacheDir)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                tempFile
            }
        } catch (e: Exception) {
            LogUtils.e(this, e, "Error creating temp file from URI")
            null
        }
    }

    private fun cleanTitleSmart(title: String): String {
        // 1. Remove leading digits/spaces/dots/hyphens (e.g., "01 ", "01. ", "01 - ")
        // \uFF0D is the fullwidth hyphen-minus character used in fullwidth/CJK text.
        var cleaned = title.replace(Regex("^[\\d\\s.\\-\\uFF0D]+"), "")
        
        // 2. Truncate at first special char (-, (, ), Asian brackets)
        // "Taare Ginn - Envy" -> "Taare Ginn "
        // "Song (Feat. X)" -> "Song "
        val splitRegex = Regex("""[-\(\[\{\uFF08\uFF3B\uFF5B\u3010\u300E\u300C\u3014\u3008\u300A]""")
        cleaned = cleaned.split(splitRegex).firstOrNull() ?: cleaned
        
        // 3. Trim whitespace
        return cleaned.trim()
    }

    private fun romanizeForMatch(text: String): String {
        return when {
            MultiLangRomanizer.isJapanese(text) -> MultiLangRomanizer.romanizeJapanese(text) ?: text
            MultiLangRomanizer.isChinese(text) -> MultiLangRomanizer.romanizeChinese(text) ?: text
            MultiLangRomanizer.isKorean(text) -> MultiLangRomanizer.romanizeKorean(text)
            else -> text
        }
    }
}

data class LyricsSearchResult(val record: LrcLibResponse, val lyrics: Lyrics, val rawLyrics: String)

data class NoLyricsFoundException(val query: String? = null) : Exception()

class LyricsException(message: String, cause: Throwable? = null) : Exception(message, cause)
