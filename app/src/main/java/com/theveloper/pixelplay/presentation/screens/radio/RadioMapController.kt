package com.theveloper.pixelplay.presentation.screens.radio

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import com.theveloper.pixelplay.data.radio.RadioHome
import com.theveloper.pixelplay.data.radio.RadioMapMath
import com.theveloper.pixelplay.data.radio.RadioMapSpot
import com.theveloper.pixelplay.data.radio.RadioScope
import com.theveloper.pixelplay.data.radio.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max

/**
 * Screen position of the map: normalised Web Mercator (0..1, see [RadioMapMath]) → pixels.
 * The map never rotates or tilts, so it is a plain scale + offset.
 */
@Immutable
data class RadioMapTransform(
    val centerX: Double,
    val centerY: Double,
    /** Pixels per normalised unit (the width of the whole world in px). */
    val scale: Double,
    val originX: Float,
    val originY: Float,
) {
    fun screenX(normX: Double): Float {
        var dx = normX - centerX
        // Nearest copy of the world, so pins near the date line don't jump across the screen.
        if (dx > 0.5) dx -= 1.0 else if (dx < -0.5) dx += 1.0
        return (originX + dx * scale).toFloat()
    }

    fun screenY(normY: Double): Float = (originY + (normY - centerY) * scale).toFloat()
}

/** Pins that overlap on screen, drawn as one. [spots] is most popular first. */
@Immutable
data class RadioMapCluster(val id: String, val spots: List<RadioMapSpot>) {
    val lead: RadioMapSpot get() = spots.first()
    val x: Double = RadioMapMath.normX(spots.first().lon)
    val y: Double = RadioMapMath.normY(spots.first().lat)
    val isSingle: Boolean get() = spots.size == 1
}

/**
 * Owns the radio map's state: pins, clusters, selection and the camera. The base map is
 * MapLibre (OpenFreeMap tiles, no API key); the pins are Compose, laid over the map and moved
 * with it every frame (see RadioMap), so they keep the station-logo look.
 *
 * Lives in the view model, so the camera, pins and selection survive list ↔ map switches;
 * the MapLibre map itself is attached while the map is on screen.
 */
class RadioMapController(val tiles: RadioMapTiles) {

    /** Where the camera is, in screen terms. Updated every frame while the map moves. */
    val transform = mutableStateOf<RadioMapTransform?>(null)

    /**
     * [transform] as of the last time the camera stopped. Deciding *which* pins exist reads this
     * (recomposes when the map settles); *where* they are reads [transform] (layout only).
     */
    val settled = mutableStateOf<RadioMapTransform?>(null)

    /** Clusters for the current zoom. Recomputed only when the zoom changes. */
    val clusters = mutableStateOf<List<RadioMapCluster>>(emptyList())

    /** The spot whose stations the tray lists. */
    val selectedSpotId = mutableStateOf<String?>(null)
    val playingUuid = mutableStateOf<String?>(null)
    val isPlaying = mutableStateOf(false)
    val home = mutableStateOf<RadioHome?>(null)

    private var map: MapLibreMap? = null
    private var spots: List<RadioMapSpot> = emptyList()
    private var spotsById: Map<String, RadioMapSpot> = emptyMap()
    private var shownStations: List<RadioStation>? = null
    private var shownHome: RadioHome? = null
    private var clusteredAtScale = -1.0
    private var clusterThresholdPx = 160f

    /** Where the camera was when the map last left the screen; the next map starts there. */
    var savedCamera: CameraPosition? = null
        private set

    /** What the camera was last framed for (scope + area); see RadioMap. */
    var framedKey: Any? = null

    /** The last "show this station" request handled; see RadioMap. */
    var handledFocusRequest: Int = 0

    /** A request that arrived before the map was ready. */
    private var pendingFocus: RadioStation? = null

    /** Called when a pin is tapped (null = selection cleared). */
    var onSpotSelected: (RadioMapSpot?) -> Unit = {}

    val isAttached: Boolean get() = map != null

    fun spot(id: String?): RadioMapSpot? = id?.let { spotsById[it] }

    // ---- Map lifecycle ------------------------------------------------------------------------

    /** [thresholdPx]: pins closer than this on screen merge into a cluster (about 58 dp). */
    fun attach(map: MapLibreMap, thresholdPx: Float) {
        this.map = map
        clusterThresholdPx = thresholdPx
        map.addOnCameraMoveListener { updateTransform() }
        map.addOnCameraIdleListener {
            updateTransform()
            settled.value = transform.value
            savedCamera = map.cameraPosition
        }
        map.addOnMapClickListener {
            if (selectedSpotId.value != null) clearSelection()
            false
        }
        updateTransform()
        settled.value = transform.value
        pendingFocus?.let { station ->
            pendingFocus = null
            focusStation(station, animate = false)
        }
    }

    fun detach() {
        map?.let { savedCamera = it.cameraPosition }
        map = null
    }

    /** Recomputes the pixel transform from two projected points (exact, whatever the density). */
    fun updateTransform() {
        val m = map ?: return
        val target = m.cameraPosition.target ?: return
        val projection = m.projection
        val p0 = projection.toScreenLocation(target)
        // A point due east of the target; Mercator x is linear in longitude.
        val lon1 = if (target.longitude > 170.0) target.longitude - 1.0 else target.longitude + 1.0
        val p1 = projection.toScreenLocation(LatLng(target.latitude, lon1))
        val nx0 = RadioMapMath.normX(target.longitude)
        val nx1 = RadioMapMath.normX(lon1)
        val dn = nx1 - nx0
        if (dn == 0.0) return
        val scale = (p1.x - p0.x) / dn
        if (!scale.isFinite() || scale <= 0.0) return
        transform.value = RadioMapTransform(
            centerX = nx0,
            centerY = RadioMapMath.normY(target.latitude),
            scale = scale,
            originX = p0.x,
            originY = p0.y,
        )
        // Re-cluster when the zoom has changed by more than ~10%.
        if (clusteredAtScale <= 0.0 || abs(ln(scale / clusteredAtScale)) > 0.1) {
            recluster(scale)
            settled.value = transform.value
        }
    }

    // ---- Pins ---------------------------------------------------------------------------------

    /** Replaces the pins. Cheap to call repeatedly: does nothing when the list hasn't changed. */
    suspend fun showStations(stations: List<RadioStation>, home: RadioHome?) {
        if (stations === shownStations && home == shownHome) return
        shownStations = stations
        shownHome = home
        this.home.value = home
        val newSpots = withContext(Dispatchers.Default) { RadioMapMath.spots(stations) }
        spots = newSpots
        spotsById = newSpots.associateBy { it.id }
        if (selectedSpotId.value?.let { it !in spotsById } == true) selectedSpotId.value = null
        clusteredAtScale = -1.0
        transform.value?.let { recluster(it.scale) } ?: run { clusters.value = newSpots.map { RadioMapCluster(it.id, listOf(it)) } }
    }

    /**
     * Groups spots that would overlap on screen at [scale]. Grid-based (one pass), so a few
     * thousand stations take a couple of milliseconds. Each group is drawn at its most popular
     * spot; the playing and selected stations always keep a pin of their own.
     */
    private fun recluster(scale: Double) {
        clusteredAtScale = scale
        val cell = clusterThresholdPx / scale
        val keep = setOfNotNull(selectedSpotId.value) +
            (playingUuid.value?.let { uuid -> spots.filter { s -> s.stations.any { it.uuid == uuid } }.map { it.id } } ?: emptyList())
        val cells = LinkedHashMap<Long, MutableList<RadioMapSpot>>()
        val out = ArrayList<RadioMapCluster>()
        for (spot in spots) {
            if (spot.id in keep) {
                out += RadioMapCluster(spot.id, listOf(spot))
                continue
            }
            val cx = floor(RadioMapMath.normX(spot.lon) / cell).toLong()
            val cy = floor(RadioMapMath.normY(spot.lat) / cell).toLong()
            cells.getOrPut((cy shl 32) xor (cx and 0xffffffffL)) { mutableListOf() }.add(spot)
        }
        cells.values.forEach { members ->
            val sorted = members.sortedByDescending { it.totalClicks }
            out += RadioMapCluster(if (sorted.size == 1) sorted[0].id else "cluster_" + sorted[0].id, sorted)
        }
        clusters.value = out
    }

    fun select(cluster: RadioMapCluster) {
        if (cluster.isSingle) {
            selectedSpotId.value = cluster.lead.id
            onSpotSelected(cluster.lead)
        } else {
            zoomInto(cluster)
        }
    }

    fun clearSelection() {
        selectedSpotId.value = null
        onSpotSelected(null)
    }

    // ---- Camera -------------------------------------------------------------------------------

    /**
     * Frames the scope: around home for LOCAL, the stations' extent for REGION/COUNTRY, the whole
     * world for WORLD. [paddingPx] keeps pins off the edges.
     */
    fun frame(scope: RadioScope, stations: List<RadioStation>, home: RadioHome?, paddingPx: Int, animate: Boolean) {
        val m = map ?: return
        val hLat = home?.lat
        val hLon = home?.lon
        val bounds: DoubleArray? = when {
            scope == RadioScope.WORLD -> null
            scope == RadioScope.LOCAL && hLat != null && hLon != null -> {
                // About 150 km around home.
                val dLat = 0.7
                val dLon = 0.7 / max(cos(Math.toRadians(hLat)), 0.2)
                doubleArrayOf(hLat - dLat, hLon - dLon, hLat + dLat, hLon + dLon)
            }
            else -> RadioMapMath.bounds(stations) ?: if (hLat != null && hLon != null) {
                doubleArrayOf(hLat - 3.0, hLon - 3.0, hLat + 3.0, hLon + 3.0)
            } else null
        }
        val update = if (bounds == null) {
            CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 10.0), RadioMapTiles.MIN_ZOOM)
        } else {
            // Never frame tighter than about 4 km.
            val minSpan = 0.04
            val south = minOf(bounds[0], bounds[2] - minSpan)
            val north = max(bounds[2], bounds[0] + minSpan)
            val west = minOf(bounds[1], bounds[3] - minSpan)
            val east = max(bounds[3], bounds[1] + minSpan)
            CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(north, east, south, west), paddingPx)
        }
        if (animate) m.animateCamera(update, 700) else m.moveCamera(update)
    }

    fun zoomBy(delta: Double) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(delta), 300)
    }

    /** Centres on a spot, zooming in if the map is far out. */
    fun focus(spot: RadioMapSpot) {
        val m = map ?: return
        val zoom = max(m.cameraPosition.zoom, 10.0)
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(spot.lat, spot.lon), zoom), 600)
    }

    /**
     * Flies to [station], selects its pin and opens its tray. Waits for the map if it isn't
     * on screen yet.
     */
    fun focusStation(station: RadioStation, animate: Boolean = true) {
        val lat = station.lat
        val lon = station.lon
        if (lat == null || lon == null) return
        val m = map
        if (m == null) {
            pendingFocus = station
            savedCamera = CameraPosition.Builder().target(LatLng(lat, lon)).zoom(11.0).build()
            return
        }
        val spot = spots.firstOrNull { s -> s.stations.any { it.uuid == station.uuid } }
        if (spot != null) {
            selectedSpotId.value = spot.id
            clusteredAtScale = -1.0 // give the selected spot its own pin
            transform.value?.let { recluster(it.scale) }
            onSpotSelected(spot)
        }
        val zoom = max(m.cameraPosition.zoom, 11.0)
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom)
        if (animate) m.animateCamera(update, 900) else m.moveCamera(update)
    }

    private fun zoomInto(cluster: RadioMapCluster) {
        val m = map ?: return
        val lats = cluster.spots.map { it.lat }
        val lons = cluster.spots.map { it.lon }
        val spanLat = (lats.max() - lats.min())
        val spanLon = (lons.max() - lons.min())
        if (spanLat < 0.002 && spanLon < 0.002) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lats.average(), lons.average()), m.cameraPosition.zoom + 2.0), 500)
        } else {
            val pad = 0.15
            val bounds = LatLngBounds.from(
                lats.max() + spanLat * pad, lons.max() + spanLon * pad,
                lats.min() - spanLat * pad, lons.min() - spanLon * pad,
            )
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, (clusterThresholdPx).toInt()), 600)
        }
    }

    fun shutdown() {
        detach()
    }
}
