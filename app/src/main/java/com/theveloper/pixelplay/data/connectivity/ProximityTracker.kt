package com.theveloper.pixelplay.data.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Rough distance band from Bluetooth signal strength. RSSI is noisy, so we only show bands. */
enum class ProximityZone(val label: String, val bars: Int, val floorDbm: Int) {
    IMMEDIATE("Next to phone", 4, -50),
    NEAR("Nearby", 3, -70),
    ROOM("In the room", 2, -85),
    FAR("Far away", 1, Int.MIN_VALUE);

    companion object {
        fun of(rssi: Double): ProximityZone = entries.first { rssi > it.floorDbm }
    }
}

data class ProximityReading(
    val smoothedRssi: Double,
    val zone: ProximityZone,
    /** Estimated distance in metres, already rounded for display. */
    val approxMeters: Double,
    val lastSeenAt: Long,
    val isStale: Boolean = false
) {
    /** "Nearby · ~2 m", or "Out of range" when no signal was heard for a while. */
    val label: String
        get() = if (isStale) "Out of range" else "${zone.label} · ~${formatMeters(approxMeters)}"

    val bars: Int get() = if (isStale) 0 else zone.bars

    private fun formatMeters(m: Double): String =
        if (m < 10) (if (m % 1.0 == 0.0) m.toInt().toString() else m.toString()) else m.roundToInt().toString()
}

/**
 * Turns raw RSSI samples (BLE adverts + classic discovery) into steady proximity readings:
 * EMA smoothing, spike rejection, zone hysteresis and stale expiry.
 *
 * Readings are keyed by Bluetooth address and, when a name is known, also by
 * `"name:<lowercase name>"`, matching [BluetoothPeer.key]. BLE adverts often use a random
 * address, so the name key is what lets a connected pair of headphones show a distance.
 */
class ProximityTracker(
    private val alpha: Double = 0.25,
    private val spikeDb: Double = 20.0,
    private val hysteresisDb: Double = 3.0,
    private val samplesToSwitch: Int = 2,
    private val staleAfterMs: Long = 10_000,
    private val forgetAfterMs: Long = 60_000,
    private val pathLossExponent: Double = 2.7,
    private val defaultTxPowerAt1m: Int = -59
) {
    private class State(
        var ema: Double,
        var zone: ProximityZone,
        var pendingZone: ProximityZone? = null,
        var pendingCount: Int = 0,
        var txPower: Int?,
        var lastSeen: Long
    )

    private val states = HashMap<String, State>()
    /** Per-device reference power at 1 m (from calibration); overrides the advertised one. */
    private val calibration = HashMap<String, Int>()
    private val _readings = MutableStateFlow<Map<String, ProximityReading>>(emptyMap())
    val readings: StateFlow<Map<String, ProximityReading>> = _readings.asStateFlow()

    fun setCalibration(key: String, txPowerAt1m: Int?) = synchronized(this) {
        if (txPowerAt1m == null) calibration.remove(key) else calibration[key] = txPowerAt1m
    }

    /** Records one sample. [txPower] is the advertised TX power at 1 m, if any. */
    fun record(address: String?, name: String?, rssi: Int, txPower: Int? = null, now: Long = System.currentTimeMillis()) {
        if (rssi == 0 || rssi < -127 || rssi > 20) return
        val keys = buildList {
            address?.takeIf { it.isNotBlank() }?.let { add(it) }
            name?.trim()?.takeIf { it.isNotEmpty() }?.let { add("name:${it.lowercase()}") }
        }
        if (keys.isEmpty()) return
        val usableTx = txPower?.takeIf { it in -100..20 }
        synchronized(this) {
            keys.forEach { update(it, rssi.toDouble(), usableTx, now) }
            publish(now)
        }
    }

    /** Marks silent devices "Out of range" and drops ones gone for a long time. Call every few seconds. */
    fun tick(now: Long = System.currentTimeMillis()) = synchronized(this) {
        states.entries.removeAll { now - it.value.lastSeen > forgetAfterMs }
        publish(now)
    }

    fun clear() = synchronized(this) {
        states.clear()
        _readings.value = emptyMap()
    }

    private fun update(key: String, rssi: Double, txPower: Int?, now: Long) {
        val s = states[key]
        if (s == null || now - s.lastSeen > staleAfterMs) {
            states[key] = State(rssi, ProximityZone.of(rssi), txPower = txPower, lastSeen = now)
            return
        }
        // A single sample far from the average is almost always a reflection or a body in the way.
        if (abs(rssi - s.ema) > spikeDb) {
            s.lastSeen = now
            return
        }
        s.ema = alpha * rssi + (1 - alpha) * s.ema
        s.lastSeen = now
        if (txPower != null) s.txPower = txPower
        val candidate = ProximityZone.of(s.ema)
        if (candidate == s.zone) {
            s.pendingZone = null; s.pendingCount = 0
            return
        }
        // Only switch once the signal is clearly past the border, for a couple of samples.
        val border = if (candidate.ordinal < s.zone.ordinal) s.zone.let { entries(it.ordinal - 1).floorDbm } else s.zone.floorDbm
        val clear = if (candidate.ordinal < s.zone.ordinal) s.ema > border + hysteresisDb else s.ema < border - hysteresisDb
        if (!clear) { s.pendingZone = null; s.pendingCount = 0; return }
        if (s.pendingZone == candidate) s.pendingCount++ else { s.pendingZone = candidate; s.pendingCount = 1 }
        if (s.pendingCount >= samplesToSwitch) {
            s.zone = candidate; s.pendingZone = null; s.pendingCount = 0
        }
    }

    private fun entries(ordinal: Int): ProximityZone = ProximityZone.entries[ordinal.coerceIn(0, ProximityZone.entries.lastIndex)]

    private fun publish(now: Long) {
        _readings.value = states.mapValues { (key, s) ->
            val a = calibration[key] ?: s.txPower ?: defaultTxPowerAt1m
            ProximityReading(
                smoothedRssi = s.ema,
                zone = s.zone,
                approxMeters = roundMeters(10.0.pow((a - s.ema) / (10 * pathLossExponent))),
                lastSeenAt = s.lastSeen,
                isStale = now - s.lastSeen > staleAfterMs
            )
        }
    }

    private fun roundMeters(d: Double): Double = when {
        d < 0.5 -> 0.5
        d < 3 -> (d * 2).roundToInt() / 2.0
        else -> d.roundToInt().toDouble().coerceAtMost(30.0)
    }
}
