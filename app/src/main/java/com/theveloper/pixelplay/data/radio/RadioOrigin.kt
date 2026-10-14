package com.theveloper.pixelplay.data.radio

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The Radio screen's filters at the moment a station was started from it. */
data class RadioFilterSnapshot(
    val scope: RadioScope,
    val category: RadioCategory?,
    /** The section inside [category] (a short tag like "Rock"), if one was picked. */
    val section: String?,
    val query: String,
    val viewMode: RadioViewMode,
)

/**
 * Remembers, per station, the filters it was found with (for this app session), so the radio
 * player's Radio button can reopen the Radio screen exactly as it was.
 */
object RadioOrigin {
    private val byStation = ConcurrentHashMap<String, RadioFilterSnapshot>()

    fun record(stationUuid: String, snapshot: RadioFilterSnapshot) {
        byStation[stationUuid] = snapshot
    }

    fun of(stationUuid: String): RadioFilterSnapshot? = byStation[stationUuid]
}

/**
 * "Open Radio with the filters this station was found with", asked from the radio player.
 * Held until the Radio screen picks it up, so it survives the navigation (like [RadioMapFocus]).
 */
object RadioOpenRequest {
    data class Request(val station: RadioStation, val filters: RadioFilterSnapshot?)

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending

    fun request(station: RadioStation) {
        _pending.value = Request(station, RadioOrigin.of(station.uuid))
    }

    fun consume(): Request? = _pending.value.also { _pending.value = null }
}
