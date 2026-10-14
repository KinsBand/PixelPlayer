package com.theveloper.pixelplay.data.radio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Talks to the free, community-run Radio Browser directory (https://www.radio-browser.info):
 * ~50k stations with country, state, tags and (for many) coordinates. No API key.
 *
 * Speed, in order of what matters most:
 *  1. **One request per country.** A whole country's list (every station, not the top 1000) is
 *     fetched once and Local / Region / Country are all cut from it on the phone, so switching
 *     scope never waits on the network and nothing is missed because of how a state is spelled.
 *  2. **Warm, fastest mirror.** [warmUp] races every mirror once (DNS + TLS + a tiny request) and
 *     keeps the one that answered first, so the first real list doesn't pay the handshake.
 *  3. **Adaptive hedging.** If the preferred mirror hasn't answered within ~1.5× its usual
 *     time (250–900 ms), the next one is asked too; first answer wins, the rest are cancelled.
 *
 * The mirror list comes from `all.api.radio-browser.info/json/servers` (as the API asks), with
 * the known mirrors as a fallback.
 */
@Singleton
class RadioBrowserRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    @Volatile
    private var preferredServer = 0

    @Volatile
    private var servers: List<String> = DEFAULT_SERVERS

    /** Smoothed response time per mirror host, ms. */
    private val latency = java.util.concurrent.ConcurrentHashMap<String, Double>()

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val warmed = AtomicBoolean(false)
    private val discovered = AtomicBoolean(false)

    /** Short timeouts: with hedging, waiting long on one mirror only delays the answer. */
    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            // The API asks every client to say who it is.
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }

    /**
     * Opens connections to every mirror and remembers the fastest. Cheap (a few hundred bytes
     * each) and done once per app run; call it as soon as the radio might be used.
     */
    fun warmUp() {
        if (!warmed.compareAndSet(false, true)) return
        background.launch {
            discoverServers()
            val list = servers
            val winner = CompletableDeferred<Int>()
            val jobs = list.indices.map { i ->
                launch {
                    val started = System.nanoTime()
                    runCatching { fetchText(buildUrl(list[i], "json/stats", emptyMap())) }
                        .onSuccess {
                            record(list[i], (System.nanoTime() - started) / 1_000_000.0)
                            winner.complete(i)
                        }
                }
            }
            withTimeoutOrNull(5_000) { winner.await() }?.let { preferredServer = it }
            // Let the others finish so their latency is known too (and their connection pooled).
            withTimeoutOrNull(5_000) { jobs.forEach { it.join() } }
        }
    }

    /** Every station in a country (up to [limit]), most listened first. */
    suspend fun byCountry(countryCode: String, limit: Int = COUNTRY_LIMIT): List<RadioStation> =
        search(mapOf("countrycode" to countryCode.uppercase(), "limit" to limit.toString()))

    /** Stations in one state/territory of a country, most listened first (exact spelling only). */
    suspend fun byState(countryCode: String, state: String, limit: Int = 500): List<RadioStation> =
        search(
            mapOf(
                "countrycode" to countryCode.uppercase(),
                "state" to state,
                "stateExact" to "true",
                "limit" to limit.toString(),
            )
        )

    /**
     * Stations with coordinates within [radiusKm] of a point (`geo_lat`/`geo_long`/`geo_distance`,
     * metres). The caller still filters by distance, so a mirror that ignores the geo parameters
     * only costs precision, not correctness.
     */
    suspend fun nearby(lat: Double, lon: Double, radiusKm: Double, limit: Int = 1000): List<RadioStation> =
        search(
            mapOf(
                "geo_lat" to lat.toString(),
                "geo_long" to lon.toString(),
                "geo_distance" to (radiusKm * 1000).toLong().toString(),
                "has_geo_info" to "true",
                "limit" to limit.toString(),
            )
        )

    /** The most listened stations worldwide. [geoOnly] keeps only stations that can go on the map. */
    suspend fun top(limit: Int = 300, geoOnly: Boolean = false): List<RadioStation> =
        search(
            buildMap {
                put("limit", limit.toString())
                if (geoOnly) put("has_geo_info", "true")
            }
        )

    /** Name search (substring), optionally inside a country. */
    suspend fun searchByName(query: String, countryCode: String? = null, limit: Int = 100): List<RadioStation> =
        search(
            buildMap {
                put("name", query)
                put("limit", limit.toString())
                if (!countryCode.isNullOrBlank()) put("countrycode", countryCode.uppercase())
            }
        )

    /** Tag search (substring, so "community" finds "community radio"), optionally inside a country. */
    suspend fun searchByTag(query: String, countryCode: String? = null, limit: Int = 60): List<RadioStation> =
        search(
            buildMap {
                put("tag", query)
                put("limit", limit.toString())
                if (!countryCode.isNullOrBlank()) put("countrycode", countryCode.uppercase())
            }
        )

    /** Stations with a tag (genre), optionally inside a country. */
    suspend fun byTag(tag: String, countryCode: String? = null, limit: Int = 200): List<RadioStation> =
        search(
            buildMap {
                put("tag", tag)
                put("tagExact", "true")
                put("limit", limit.toString())
                if (!countryCode.isNullOrBlank()) put("countrycode", countryCode.uppercase())
            }
        )

    /**
     * Tells Radio Browser a station was played. This is how the directory ranks popularity
     * (the API asks clients to do it); failures are ignored.
     */
    suspend fun reportClick(uuid: String) {
        runCatching { get("json/url/$uuid", emptyMap()) }
            .onFailure { Timber.tag(TAG).d(it, "click report failed") }
    }

    private suspend fun search(params: Map<String, String>): List<RadioStation> {
        val body = get(
            "json/stations/search",
            mapOf(
                "hidebroken" to "true",
                "order" to "clickcount",
                "reverse" to "true",
            ) + params
        )
        return withContext(Dispatchers.Default) { parseStations(body) }
    }

    private suspend fun get(path: String, params: Map<String, String>): String = coroutineScope {
        if (!discovered.get()) background.launch { discoverServers() }
        val list = servers
        val first = preferredServer.coerceIn(0, list.lastIndex)
        val order = list.indices.map { (first + it) % list.size }
        val result = CompletableDeferred<String>()
        val failures = AtomicInteger(0)
        val jobs = ArrayList<Job>(order.size)
        var lastError: Exception? = null
        for ((attempt, index) in order.withIndex()) {
            if (result.isCompleted) break
            val host = list[index]
            val url = buildUrl(host, path, params)
            val job = launch {
                val started = System.nanoTime()
                try {
                    val text = fetchText(url)
                    record(host, (System.nanoTime() - started) / 1_000_000.0)
                    if (result.complete(text)) preferredServer = index
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w("Radio Browser mirror %s failed: %s", url.host, e.message)
                    latency[host] = SLOW_MS
                    lastError = e
                    if (failures.incrementAndGet() == order.size) {
                        result.completeExceptionally(lastError ?: IOException("No Radio Browser server reachable"))
                    }
                }
            }
            jobs += job
            if (attempt < order.lastIndex) {
                // Give this mirror a head start sized to how fast it usually is; move on early
                // if it fails, stop as soon as anyone answered.
                withTimeoutOrNull(hedgeDelay(host, attempt, path)) {
                    while (!result.isCompleted && job.isActive) delay(10)
                }
            }
        }
        try {
            result.await()
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    /** ~1.5× the mirror's usual time for small requests; big lists get longer before hedging. */
    private fun hedgeDelay(host: String, attempt: Int, path: String): Long {
        if (attempt > 0) return HEDGE_NEXT_MS
        val usual = latency[host] ?: return HEDGE_UNKNOWN_MS
        val big = path.startsWith("json/stations")
        val base = (usual * 1.5).toLong()
        return if (big) (base * 2).coerceIn(400L, 1_500L) else base.coerceIn(HEDGE_MIN_MS, HEDGE_MAX_MS)
    }

    private fun record(host: String, ms: Double) {
        latency.merge(host, ms) { old, new -> old * 0.7 + new * 0.3 }
    }

    /** Asks the round-robin name for the current mirror list. Keeps the defaults if it fails. */
    private suspend fun discoverServers() {
        if (!discovered.compareAndSet(false, true)) return
        runCatching {
            val text = fetchText("https://all.api.radio-browser.info/json/servers".toHttpUrl())
            val arr = JSONArray(text)
            val names = (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it)?.optString("name")?.takeIf { n -> n.endsWith(".radio-browser.info") } }
                .distinct()
            if (names.isNotEmpty()) {
                val current = servers.getOrNull(preferredServer)
                val merged = (names + DEFAULT_SERVERS.drop(1)).distinct()
                servers = merged
                preferredServer = current?.let { merged.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            }
        }.onFailure { Timber.tag(TAG).d("mirror discovery failed: %s", it.message) }
    }

    /** One HTTP GET, cancelled for real (socket closed) when the coroutine is cancelled. */
    private suspend fun fetchText(url: HttpUrl): String = suspendCancellableCoroutine { cont ->
        val call = client.newCall(Request.Builder().url(url).get().build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code} from ${url.host}")
                        val text = it.body?.string() ?: throw IOException("Empty body from ${url.host}")
                        if (cont.isActive) cont.resume(text)
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

    private fun buildUrl(server: String, path: String, params: Map<String, String>): HttpUrl {
        val builder = "https://$server/".toHttpUrl().newBuilder().addPathSegments(path)
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    companion object {
        private const val TAG = "RadioBrowser"
        private const val USER_AGENT = "PixelPlayer/1.0 (Android; +https://github.com/theovilardo/PixelPlayer)"

        /** Enough for any country's full list (the US, the biggest, has about 7–8k). */
        const val COUNTRY_LIMIT = 10_000

        /** First hedge when a mirror's speed isn't known yet. */
        private const val HEDGE_UNKNOWN_MS = 450L
        private const val HEDGE_MIN_MS = 250L
        private const val HEDGE_MAX_MS = 900L
        /** Head start for each further mirror. */
        private const val HEDGE_NEXT_MS = 1_200L
        /** What a failed mirror counts as, so it stops being first choice. */
        private const val SLOW_MS = 5_000.0

        /** `all.` is round-robin DNS over every mirror; the named ones are fallbacks. */
        private val DEFAULT_SERVERS = listOf(
            "all.api.radio-browser.info",
            "de1.api.radio-browser.info",
            "de2.api.radio-browser.info",
            "fi1.api.radio-browser.info",
        )

        internal fun parseStations(json: String): List<RadioStation> {
            val arr = JSONArray(json)
            val out = ArrayList<RadioStation>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                parseStation(o)?.let(out::add)
            }
            // The directory has many duplicates of big stations (one per stream quality).
            return out.distinctBy { it.name.trim().lowercase() to it.countryCode }
        }

        internal fun parseStation(o: JSONObject): RadioStation? {
            val uuid = o.optString("stationuuid").takeIf { it.isNotBlank() } ?: return null
            val stream = o.optString("url_resolved").takeIf { it.isNotBlank() }
                ?: o.optString("url").takeIf { it.isNotBlank() }
                ?: return null
            val name = o.optString("name").trim().takeIf { it.isNotBlank() } ?: return null
            return RadioStation(
                uuid = uuid,
                name = name,
                streamUrl = stream,
                homepage = o.optString("homepage").takeIf { it.isNotBlank() },
                favicon = o.optString("favicon").takeIf { it.startsWith("http") },
                tags = o.optString("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(6),
                country = o.optString("country").takeIf { it.isNotBlank() },
                countryCode = o.optString("countrycode").takeIf { it.isNotBlank() },
                state = o.optString("state").takeIf { it.isNotBlank() },
                language = o.optString("language").takeIf { it.isNotBlank() },
                codec = o.optString("codec").takeIf { it.isNotBlank() },
                bitrate = o.optInt("bitrate", 0),
                isHls = o.optInt("hls", 0) == 1,
                votes = o.optInt("votes", 0),
                clickCount = o.optInt("clickcount", 0),
                lat = o.optCoordinate("geo_lat", 90.0),
                lon = o.optCoordinate("geo_long", 180.0),
                subdivisionCode = o.optString("iso_3166_2").takeIf { it.length in 4..6 && !o.isNull("iso_3166_2") },
            )
        }

        private fun JSONObject.optCoordinate(key: String, limit: Double): Double? {
            if (isNull(key)) return null
            val v = optDouble(key, Double.NaN)
            // 0,0 is the directory's "unknown" more often than a real station in the Gulf of Guinea.
            return v.takeIf { !it.isNaN() && it in -limit..limit && it != 0.0 }
        }

        /** Station ↔ JSON for presets/favourites, so they still play if the directory is down. */
        fun toJson(s: RadioStation): JSONObject = JSONObject().apply {
            put("stationuuid", s.uuid)
            put("name", s.name)
            put("url_resolved", s.streamUrl)
            s.homepage?.let { put("homepage", it) }
            s.favicon?.let { put("favicon", it) }
            put("tags", s.tags.joinToString(","))
            s.country?.let { put("country", it) }
            s.countryCode?.let { put("countrycode", it) }
            s.state?.let { put("state", it) }
            s.language?.let { put("language", it) }
            s.codec?.let { put("codec", it) }
            put("bitrate", s.bitrate)
            put("hls", if (s.isHls) 1 else 0)
            put("votes", s.votes)
            put("clickcount", s.clickCount)
            s.lat?.let { put("geo_lat", it) }
            s.lon?.let { put("geo_long", it) }
            s.subdivisionCode?.let { put("iso_3166_2", it) }
        }
    }
}
