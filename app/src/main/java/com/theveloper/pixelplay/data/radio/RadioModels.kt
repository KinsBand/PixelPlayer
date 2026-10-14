package com.theveloper.pixelplay.data.radio

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How far out the radio browser looks. Each step widens the station pool:
 * stations near you → your state/territory → your country → everything.
 */
enum class RadioScope(val label: String) {
    LOCAL("Local"),
    REGION("Region"),
    COUNTRY("Country"),
    WORLD("World");
}

/** The two ways of browsing the same scope. */
enum class RadioViewMode(val label: String) {
    LIST("List"),
    MAP("Map");
}

/** One internet radio station, as listed by Radio Browser (radio-browser.info). */
data class RadioStation(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val homepage: String? = null,
    val favicon: String? = null,
    val tags: List<String> = emptyList(),
    val country: String? = null,
    val countryCode: String? = null,
    val state: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val bitrate: Int = 0,
    val isHls: Boolean = false,
    val votes: Int = 0,
    val clickCount: Int = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    /** ISO 3166-2 subdivision ("AU-NSW"), when the directory knows it. Spelling-proof, unlike [state]. */
    val subdivisionCode: String? = null,
) {
    val hasGeo: Boolean get() = lat != null && lon != null

    /** "FM 102.1" / "AM 612" / null, read from the station name. */
    val frequency: RadioFrequency? by lazy { RadioFrequency.parse(name) }

    /** "NSW" for "New South Wales" (any spelling), else the state as listed. */
    val shortState: String? by lazy {
        RadioLabels.shortState(state, countryCode)
            ?: subdivisionCode?.takeIf { it.length in 4..6 && it[2] == '-' }?.substring(3)
    }

    /** First genre tag, shortened: "Comm", "Talk", "Rock". */
    val shortTag: String? by lazy { tags.firstOrNull()?.let(RadioLabels::shortTag) }

    /** "NSW · Comm" style subtitle: short enough for a chip or a pin tray. */
    val subtitle: String
        get() = listOfNotNull(
            shortState ?: country?.takeIf { it.isNotBlank() },
            shortTag,
        ).joinToString(" · ")
}

enum class RadioBand(val label: String) { FM("FM"), AM("AM") }

/** A broadcast frequency parsed out of a station name like "Triple M 104.5" or "ABC 612 AM". */
data class RadioFrequency(val band: RadioBand, val value: Double) {
    val label: String
        get() = when (band) {
            RadioBand.FM -> "%.1f MHz".format(value)
            RadioBand.AM -> "${value.roundToInt()} kHz"
        }

    companion object {
        const val FM_MIN = 87.5
        const val FM_MAX = 108.0
        const val AM_MIN = 530.0
        const val AM_MAX = 1710.0

        private val FM_REGEX = Regex("""(?<![\d.,])(8[7-9]|9\d|10[0-8])[.,](\d)(?![\d.,])""")
        private val AM_REGEX = Regex("""(?<![\d.,])(\d{3,4})\s*(?:AM|kHz)\b""", RegexOption.IGNORE_CASE)
        private val AM_PREFIX_REGEX = Regex("""\bAM\s*(\d{3,4})(?![\d.,])""", RegexOption.IGNORE_CASE)

        fun parse(name: String): RadioFrequency? {
            FM_REGEX.find(name)?.let { m ->
                val mhz = "${m.groupValues[1]}.${m.groupValues[2]}".toDouble()
                if (mhz in FM_MIN..FM_MAX) return RadioFrequency(RadioBand.FM, mhz)
            }
            val am = AM_REGEX.find(name)?.groupValues?.get(1)
                ?: AM_PREFIX_REGEX.find(name)?.groupValues?.get(1)
            am?.toDoubleOrNull()?.let { khz ->
                if (khz in AM_MIN..AM_MAX) return RadioFrequency(RadioBand.AM, khz)
            }
            return null
        }
    }
}

/** Where "local" is. Coordinates are optional: without them LOCAL falls back to the region. */
data class RadioHome(
    val countryCode: String?,
    val countryName: String? = null,
    val state: String? = null,
    val city: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    /** True when the user picked this by hand instead of it coming from the device. */
    val isManual: Boolean = false,
) {
    val hasCoordinates: Boolean get() = lat != null && lon != null

    val label: String
        get() = listOfNotNull(city, state, countryName ?: countryCode)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { "Unknown location" }
}

object RadioGeo {
    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle distance in km. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1).toRad()
        val dLon = (lon2 - lon1).toRad()
        val a = sin(dLat / 2).pow(2) + cos(lat1.toRad()) * cos(lat2.toRad()) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun distanceKm(home: RadioHome, station: RadioStation): Double? {
        val hLat = home.lat ?: return null
        val hLon = home.lon ?: return null
        val sLat = station.lat ?: return null
        val sLon = station.lon ?: return null
        return distanceKm(hLat, hLon, sLat, sLon)
    }

    fun formatDistance(km: Double): String = when {
        km < 10 -> "%.1f km".format(km)
        else -> "${km.roundToInt()} km"
    }

    /**
     * Rough continent for grouping the world list. Box-based, so borders are approximate —
     * good enough for a section header, not for anything precise.
     */
    fun continentOf(lat: Double, lon: Double): String = when {
        lon < -25 -> if (lat > 13) "N. America" else "S. America"
        lon > 110 && lat < -8 -> "Oceania"
        lon > 150 || lon < -150 -> "Oceania"
        lat >= 35 && lon < 45 -> "Europe"
        lon >= 60 || (lat >= 12 && lon >= 35) -> "Asia"
        else -> "Africa"
    }

    private fun Double.toRad() = this * PI / 180.0
}

object RadioScopeFilter {
    /** Radius that counts as "near you". */
    const val LOCAL_RADIUS_KM = 60.0
    /** Used for REGION when the state is unknown. */
    const val REGION_FALLBACK_RADIUS_KM = 400.0

    /**
     * Stations near [home], nearest first. When the radius holds too few stations it widens
     * (60 → 120 → 250 km) so a small town still gets something to listen to.
     * Stations in the same state without coordinates go at the end.
     */
    fun local(stations: List<RadioStation>, home: RadioHome, minResults: Int = 8): List<RadioStation> {
        if (!home.hasCoordinates) return emptyList()
        val withDistance = stations.mapNotNull { s -> RadioGeo.distanceKm(home, s)?.let { s to it } }
        var picked = emptyList<Pair<RadioStation, Double>>()
        for (radius in listOf(LOCAL_RADIUS_KM, LOCAL_RADIUS_KM * 2, LOCAL_RADIUS_KM * 4)) {
            picked = withDistance.filter { it.second <= radius }
            if (picked.size >= minResults) break
        }
        // Stations that don't list coordinates but say where they are: the home town in the
        // name or state field ("2SER Sydney", state "Sydney") first, then the rest of the state.
        val city = home.city?.let(RadioLabels::fold)?.takeIf { it.length >= 3 }
        val noGeo = stations.filter { !it.hasGeo }
        val inTown = if (city == null) emptyList() else noGeo.filter { s ->
            RadioLabels.fold(s.name).contains(city) || s.state?.let(RadioLabels::fold)?.contains(city) == true
        }
        val sameStateNoGeo = if (home.state.isNullOrBlank()) emptyList() else noGeo
            .filter { RadioLabels.inSameState(it, home) }
            .sortedByDescending { it.clickCount }
        return (picked.sortedBy { it.second }.map { it.first } + inTown + sameStateNoGeo).distinctBy { it.uuid }
    }

    /**
     * Everything in the home state, whatever spelling the directory uses for it ("New South
     * Wales", "NSW", "Sydney", "AU-NSW"), plus nearby stations that list no state at all.
     * Most popular first.
     */
    fun region(country: List<RadioStation>, home: RadioHome, nearby: List<RadioStation> = emptyList()): List<RadioStation> {
        val inState = if (home.state.isNullOrBlank()) emptyList() else country.filter { RadioLabels.inSameState(it, home) }
        val hasCoords = home.hasCoordinates
        val unlabelledNearby = if (!hasCoords) emptyList() else nearby.filter { s ->
            s.state.isNullOrBlank() && s.subdivisionCode.isNullOrBlank() &&
                (s.countryCode == null || s.countryCode.equals(home.countryCode, ignoreCase = true)) &&
                (RadioGeo.distanceKm(home, s) ?: Double.MAX_VALUE) <= REGION_FALLBACK_RADIUS_KM
        }
        val fallback = if (inState.isEmpty() && hasCoords) nearby.filter { s ->
            (RadioGeo.distanceKm(home, s) ?: Double.MAX_VALUE) <= REGION_FALLBACK_RADIUS_KM
        } else emptyList()
        return (inState + unlabelledNearby + fallback).distinctBy { it.uuid }.sortedByDescending { it.clickCount }
    }

    /** Groups stations for the section headers: city/state/country/continent depending on scope. */
    fun groupKey(scope: RadioScope, station: RadioStation): String = when (scope) {
        RadioScope.LOCAL -> ""
        RadioScope.REGION -> station.shortState?.takeIf { it.isNotBlank() } ?: "Other"
        RadioScope.COUNTRY -> station.shortState?.takeIf { it.isNotBlank() } ?: "Nationwide / other"
        RadioScope.WORLD -> {
            val lat = station.lat
            val lon = station.lon
            if (lat != null && lon != null) RadioGeo.continentOf(lat, lon) else station.country ?: "Other"
        }
    }
}

/**
 * Web Mercator maths for the radio map (the projection OpenStreetMap-style tiles use).
 * Normalised coordinates run 0..1 left→right (180°W→180°E) and top→bottom (85°N→85°S).
 */
object RadioMapMath {
    /** Mercator's usable latitude limit; tiles stop here. */
    const val MAX_LAT = 85.05112878

    fun normX(lon: Double): Double = ((lon + 180.0) / 360.0).coerceIn(0.0, 1.0)

    fun normY(lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val rad = clamped * PI / 180.0
        val y = (1.0 - kotlin.math.ln(kotlin.math.tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
        return y.coerceIn(0.0, 1.0)
    }

    fun lonOf(normX: Double): Double = normX * 360.0 - 180.0

    fun latOf(normY: Double): Double {
        val n = PI - 2.0 * PI * normY
        return 180.0 / PI * kotlin.math.atan(0.5 * (kotlin.math.exp(n) - kotlin.math.exp(-n)))
    }

    /**
     * Stations that share (almost) the same coordinates — many directories pin every station of a
     * city to the city centre — are one map spot, so they never sit on top of each other.
     * [precision] is in degrees (0.002° ≈ 200 m). Spots keep the most popular station first.
     */
    fun spots(stations: List<RadioStation>, precision: Double = 0.002): List<RadioMapSpot> {
        val cells = LinkedHashMap<Long, MutableList<RadioStation>>()
        for (s in stations) {
            val lat = s.lat ?: continue
            val lon = s.lon ?: continue
            val cy = kotlin.math.floor((lat + 90.0) / precision).toLong()
            val cx = kotlin.math.floor((lon + 180.0) / precision).toLong()
            cells.getOrPut((cy shl 32) xor (cx and 0xffffffffL)) { mutableListOf() }.add(s)
        }
        return cells.values.map { members ->
            val sorted = members.sortedByDescending { it.clickCount }
            val lead = sorted.first()
            RadioMapSpot(
                id = "spot_" + lead.uuid,
                lat = members.mapNotNull { it.lat }.average(),
                lon = members.mapNotNull { it.lon }.average(),
                stations = sorted,
            )
        }
    }

    /**
     * Bounding box (minLat, minLon, maxLat, maxLon) of the stations, trimming the outer
     * [trim] fraction on each side so one mis-tagged station doesn't zoom the map out to the world.
     */
    fun bounds(stations: List<RadioStation>, trim: Double = 0.05): DoubleArray? {
        val geo = stations.filter { it.hasGeo }
        if (geo.isEmpty()) return null
        val lats = geo.map { it.lat!! }.sorted()
        val lons = geo.map { it.lon!! }.sorted()
        val cut = if (geo.size >= 20) (geo.size * trim).toInt() else 0
        return doubleArrayOf(
            lats[cut], lons[cut],
            lats[lats.size - 1 - cut], lons[lons.size - 1 - cut],
        )
    }
}

/** One pin on the map: every station at (nearly) the same place. */
data class RadioMapSpot(
    val id: String,
    val lat: Double,
    val lon: Double,
    /** Most popular first; the first one's logo is the pin. */
    val stations: List<RadioStation>,
) {
    val lead: RadioStation get() = stations.first()
    val totalClicks: Long get() = stations.sumOf { it.clickCount.toLong() }
}
