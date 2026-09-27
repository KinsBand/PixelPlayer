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

/** The three ways of browsing the same scope. */
enum class RadioViewMode(val label: String) {
    LIST("List"),
    DIAL("Dial"),
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
) {
    val hasGeo: Boolean get() = lat != null && lon != null

    /** "FM 102.1" / "AM 612" / null, read from the station name. */
    val frequency: RadioFrequency? by lazy { RadioFrequency.parse(name) }

    /** "Brisbane, QLD · Indie" style subtitle. */
    val subtitle: String
        get() = listOfNotNull(
            state?.takeIf { it.isNotBlank() } ?: country?.takeIf { it.isNotBlank() },
            tags.firstOrNull()?.replaceFirstChar { it.uppercase() },
        ).joinToString(" · ")
}

enum class RadioBand(val label: String) { FM("FM"), AM("AM"), WEB("WEB") }

/** A broadcast frequency parsed out of a station name like "Triple M 104.5" or "ABC 612 AM". */
data class RadioFrequency(val band: RadioBand, val value: Double) {
    val label: String
        get() = when (band) {
            RadioBand.FM -> "%.1f MHz".format(value)
            RadioBand.AM -> "${value.roundToInt()} kHz"
            RadioBand.WEB -> ""
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
     * Rough continent for grouping and dial labels. Box-based, so borders are approximate —
     * good enough to say "Europe" on a dial, not for anything precise.
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
        val sameStateNoGeo = if (home.state.isNullOrBlank()) emptyList() else stations.filter {
            !it.hasGeo && it.state.equals(home.state, ignoreCase = true)
        }
        return (picked.sortedBy { it.second }.map { it.first } + sameStateNoGeo).distinctBy { it.uuid }
    }

    /** Groups stations for the section headers: city/state/country/continent depending on scope. */
    fun groupKey(scope: RadioScope, station: RadioStation): String = when (scope) {
        RadioScope.LOCAL -> ""
        RadioScope.REGION -> station.state?.takeIf { it.isNotBlank() } ?: "Other"
        RadioScope.COUNTRY -> station.state?.takeIf { it.isNotBlank() } ?: "Nationwide / other"
        RadioScope.WORLD -> {
            val lat = station.lat
            val lon = station.lon
            if (lat != null && lon != null) RadioGeo.continentOf(lat, lon) else station.country ?: "Other"
        }
    }
}

/** One stop on the tuner scale. [position] is 0..1 along the dial. */
data class DialStop(val station: RadioStation, val position: Float, val label: String)

/** The tuner's scale: where each station sits and what's printed under the ticks. */
data class DialLayout(
    val band: RadioBand,
    val stops: List<DialStop>,
    /** Scale labels (0..1 position → text), e.g. "88", "92" or "Europe". */
    val marks: List<Pair<Float, String>>,
) {
    fun nearest(position: Float): DialStop? = stops.minByOrNull { kotlin.math.abs(it.position - position) }

    fun next(position: Float): DialStop? =
        stops.firstOrNull { it.position > position + EPS } ?: stops.firstOrNull()

    fun previous(position: Float): DialStop? =
        stops.lastOrNull { it.position < position - EPS } ?: stops.lastOrNull()

    /** Text shown in the display window for a needle position. */
    fun readout(position: Float): String = when (band) {
        RadioBand.FM -> "%.1f".format(RadioFrequency.FM_MIN + position * (RadioFrequency.FM_MAX - RadioFrequency.FM_MIN))
        RadioBand.AM -> "${(RadioFrequency.AM_MIN + position * (RadioFrequency.AM_MAX - RadioFrequency.AM_MIN)).roundToInt()}"
        RadioBand.WEB -> "CH ${(position * (stops.size.coerceAtLeast(1) - 1)).roundToInt() + 1}"
    }

    companion object {
        private const val EPS = 0.0005f

        /** One FM step (0.1 MHz) as a fraction of the dial. */
        val FM_STEP: Float = (0.1 / (RadioFrequency.FM_MAX - RadioFrequency.FM_MIN)).toFloat()
        /** One AM step (9 kHz) as a fraction of the dial. */
        val AM_STEP: Float = (9.0 / (RadioFrequency.AM_MAX - RadioFrequency.AM_MIN)).toFloat()

        /**
         * FM/AM use real frequencies. WEB is a virtual band: every station gets its own channel,
         * ordered west → east when coordinates exist (so the dial matches the map), labelled by
         * [groupOf] at the first station of each group.
         */
        fun build(
            band: RadioBand,
            stations: List<RadioStation>,
            groupOf: (RadioStation) -> String = { "" },
        ): DialLayout = when (band) {
            RadioBand.FM -> {
                val range = RadioFrequency.FM_MAX - RadioFrequency.FM_MIN
                val stops = stations
                    .filter { it.frequency?.band == RadioBand.FM }
                    .distinctBy { it.frequency!!.value }
                    .map { s ->
                        val f = s.frequency!!
                        DialStop(s, ((f.value - RadioFrequency.FM_MIN) / range).toFloat(), "%.1f".format(f.value))
                    }
                    .sortedBy { it.position }
                val marks = (88..108 step 2).map { mhz ->
                    ((mhz - RadioFrequency.FM_MIN) / range).toFloat() to mhz.toString()
                }
                DialLayout(band, stops, marks)
            }
            RadioBand.AM -> {
                val range = RadioFrequency.AM_MAX - RadioFrequency.AM_MIN
                val stops = stations
                    .filter { it.frequency?.band == RadioBand.AM }
                    .distinctBy { it.frequency!!.value }
                    .map { s ->
                        val f = s.frequency!!
                        DialStop(s, ((f.value - RadioFrequency.AM_MIN) / range).toFloat(), f.value.roundToInt().toString())
                    }
                    .sortedBy { it.position }
                val marks = listOf(600, 800, 1000, 1200, 1400, 1600).map { khz ->
                    ((khz - RadioFrequency.AM_MIN) / range).toFloat() to khz.toString()
                }
                DialLayout(band, stops, marks)
            }
            RadioBand.WEB -> {
                val ordered = stations.sortedWith(
                    compareBy<RadioStation>({ it.lon ?: Double.MAX_VALUE }, { it.name.lowercase() })
                )
                val n = ordered.size
                val stops = ordered.mapIndexed { i, s ->
                    DialStop(s, if (n <= 1) 0.5f else i.toFloat() / (n - 1), "${i + 1}")
                }
                val marks = mutableListOf<Pair<Float, String>>()
                var lastGroup: String? = null
                var lastPos = -1f
                for (stop in stops) {
                    val g = groupOf(stop.station)
                    // Skip labels that would sit on top of the previous one.
                    if (g.isNotBlank() && g != lastGroup && stop.position - lastPos > 0.12f) {
                        marks += stop.position to g
                        lastPos = stop.position
                    }
                    lastGroup = g
                }
                DialLayout(band, stops, marks)
            }
        }
    }
}

/** A dot on the map: one station, or several close together on screen. */
data class MapCluster(
    val x: Float,
    val y: Float,
    val lat: Double,
    val lon: Double,
    val stations: List<RadioStation>,
)

object RadioMapMath {
    /** Equirectangular projection: lon/lat → 0..1 world coordinates (north up). */
    fun worldX(lon: Double): Double = (lon + 180.0) / 360.0
    fun worldY(lat: Double): Double = (90.0 - lat) / 180.0
    fun lonOf(worldX: Double): Double = worldX * 360.0 - 180.0
    fun latOf(worldY: Double): Double = 90.0 - worldY * 180.0

    /**
     * Grid clustering in screen space: stations whose projected points fall in the same
     * [cellPx] cell merge into one cluster, placed at the cell's average position.
     * [project] maps a station to screen x/y (or null if it has no coordinates).
     */
    fun cluster(
        stations: List<RadioStation>,
        cellPx: Float,
        project: (RadioStation) -> Pair<Float, Float>?,
    ): List<MapCluster> {
        if (cellPx <= 0f) return emptyList()
        val cells = LinkedHashMap<Long, MutableList<Triple<RadioStation, Float, Float>>>()
        for (s in stations) {
            val (x, y) = project(s) ?: continue
            val cx = kotlin.math.floor(x / cellPx).toLong()
            val cy = kotlin.math.floor(y / cellPx).toLong()
            cells.getOrPut((cx shl 32) xor (cy and 0xffffffffL)) { mutableListOf() }.add(Triple(s, x, y))
        }
        return cells.values.map { members ->
            MapCluster(
                x = members.map { it.second }.average().toFloat(),
                y = members.map { it.third }.average().toFloat(),
                lat = members.mapNotNull { it.first.lat }.average(),
                lon = members.mapNotNull { it.first.lon }.average(),
                // Most popular first so the preview sheet leads with the big stations.
                stations = members.map { it.first }.sortedByDescending { it.clickCount },
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
