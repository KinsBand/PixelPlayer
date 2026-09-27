package com.theveloper.pixelplay.data.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the free, community-run Radio Browser directory (https://www.radio-browser.info):
 * ~50k stations with country, state, tags and (for many) coordinates. No API key.
 *
 * The API is served by several mirrors; we try them in order and stick with whichever answered last.
 */
@Singleton
class RadioBrowserRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    @Volatile
    private var preferredServer = 0

    /** Stations in a country, most listened first. */
    suspend fun byCountry(countryCode: String, limit: Int = 1000): List<RadioStation> =
        search(mapOf("countrycode" to countryCode.uppercase(), "limit" to limit.toString()))

    /** Stations in one state/territory of a country, most listened first. */
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
     * Stations with coordinates within [radiusKm] of a point. The caller still filters by
     * distance, so a mirror that ignores the geo parameters only costs precision, not correctness.
     */
    suspend fun nearby(lat: Double, lon: Double, radiusKm: Double, limit: Int = 500): List<RadioStation> =
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

    /** Name search, optionally inside a country. */
    suspend fun searchByName(query: String, countryCode: String? = null, limit: Int = 100): List<RadioStation> =
        search(
            buildMap {
                put("name", query)
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
        return parseStations(body)
    }

    private suspend fun get(path: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (attempt in SERVERS.indices) {
            val index = (preferredServer + attempt) % SERVERS.size
            val url = buildUrl(SERVERS[index], path, params)
            try {
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} from ${url.host}")
                    val text = response.body?.string() ?: throw IOException("Empty body from ${url.host}")
                    preferredServer = index
                    return@withContext text
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag(TAG).w("Radio Browser mirror %s failed: %s", url.host, e.message)
                lastError = e
            }
        }
        throw lastError ?: IOException("No Radio Browser server reachable")
    }

    private fun buildUrl(server: String, path: String, params: Map<String, String>): HttpUrl {
        val builder = "https://$server/".toHttpUrl().newBuilder().addPathSegments(path)
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    companion object {
        private const val TAG = "RadioBrowser"

        /** `all.` is round-robin DNS over every mirror; the named ones are fallbacks. */
        private val SERVERS = listOf(
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
        }
    }
}
