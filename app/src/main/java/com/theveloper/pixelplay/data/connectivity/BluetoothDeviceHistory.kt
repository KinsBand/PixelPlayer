package com.theveloper.pixelplay.data.connectivity

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every Bluetooth device this phone has been connected to while PixelPlayer was running,
 * with when it was last connected. Kept on the device only (no backup), newest first,
 * capped at [MAX_ENTRIES]. Paired devices are merged in by the connect menu, so devices
 * connected before PixelPlayer was installed still show up.
 */
@Singleton
class BluetoothDeviceHistory @Inject constructor(
    @ApplicationContext context: Context
) {
    data class Entry(
        val address: String?,
        val name: String,
        val majorClass: Int?,
        val deviceClass: Int?,
        val lastConnectedAt: Long,
        val timesConnected: Int
    ) {
        val key: String get() = address?.takeIf { it.isNotBlank() } ?: "name:${name.lowercase()}"
        val kind: BluetoothDeviceKind get() = BluetoothDeviceKind.classify(majorClass, deviceClass, name)
    }

    private val prefs = context.getSharedPreferences("bluetooth_device_history_v1", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** Records a connection. Repeated calls for an already-connected device only move it up once a minute. */
    @Synchronized
    fun recordConnected(address: String?, name: String, majorClass: Int?, deviceClass: Int?, at: Long = System.currentTimeMillis()) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val key = address?.takeIf { it.isNotBlank() } ?: "name:${cleanName.lowercase()}"
        val current = _entries.value
        val existing = current.firstOrNull { it.key == key }
        if (existing != null && at - existing.lastConnectedAt < 60_000L && existing.name == cleanName) return
        val updated = Entry(
            address = address?.takeIf { it.isNotBlank() } ?: existing?.address,
            name = cleanName,
            majorClass = majorClass ?: existing?.majorClass,
            deviceClass = deviceClass ?: existing?.deviceClass,
            lastConnectedAt = at,
            timesConnected = (existing?.timesConnected ?: 0) + if (existing == null || at - existing.lastConnectedAt > 10 * 60_000L) 1 else 0
        )
        val next = (listOf(updated) + current.filterNot { it.key == key }).take(MAX_ENTRIES)
        _entries.value = next
        save(next)
    }

    @Synchronized
    fun forget(key: String) {
        val next = _entries.value.filterNot { it.key == key }
        _entries.value = next
        save(next)
    }

    private fun load(): List<Entry> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Entry(
                address = o.optString("address").takeIf { it.isNotBlank() },
                name = o.optString("name"),
                majorClass = if (o.has("major")) o.optInt("major") else null,
                deviceClass = if (o.has("class")) o.optInt("class") else null,
                lastConnectedAt = o.optLong("last"),
                timesConnected = o.optInt("count", 1)
            ).takeIf { it.name.isNotBlank() }
        }.sortedByDescending { it.lastConnectedAt }
    }.getOrDefault(emptyList())

    private fun save(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject().apply {
                e.address?.let { put("address", it) }
                put("name", e.name)
                e.majorClass?.let { put("major", it) }
                e.deviceClass?.let { put("class", it) }
                put("last", e.lastConnectedAt)
                put("count", e.timesConnected)
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 60
    }
}
