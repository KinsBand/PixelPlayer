package com.theveloper.pixelplay.data.connectivity

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the user set for one Bluetooth device inside PixelPlayer (device settings sheet in the
 * connect menu). Keyed like [BluetoothPeer.key].
 */
data class BluetoothDeviceSettings(
    /** Name shown in the app (also sent to the system when Android allows it). */
    val nickname: String? = null,
    /** "Audio device type" picked by the user; overrides the guess from the Bluetooth class. */
    val kind: BluetoothDeviceKind? = null,
    /** Spatial audio for PixelPlayer's playback on this device; null = system default (on). */
    val spatialAudio: Boolean? = null,
    /** Hidden from "Previously connected" after Forget (when Android wouldn't unpair it). */
    val hidden: Boolean = false,
)

@Singleton
class BluetoothDevicePrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("bluetooth_device_settings_v1", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Map<String, BluetoothDeviceSettings>> = _settings.asStateFlow()

    fun get(key: String): BluetoothDeviceSettings = _settings.value[key] ?: BluetoothDeviceSettings()

    @Synchronized
    fun update(key: String, transform: (BluetoothDeviceSettings) -> BluetoothDeviceSettings) {
        val next = _settings.value.toMutableMap()
        val value = transform(next[key] ?: BluetoothDeviceSettings())
        if (value == BluetoothDeviceSettings()) next.remove(key) else next[key] = value
        _settings.value = next
        save(next)
    }

    private fun load(): Map<String, BluetoothDeviceSettings> = runCatching {
        val root = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        root.keys().asSequence().associateWith { k ->
            val o = root.getJSONObject(k)
            BluetoothDeviceSettings(
                nickname = o.optString("nickname").takeIf { it.isNotBlank() },
                kind = o.optString("kind").takeIf { it.isNotBlank() }?.let { runCatching { BluetoothDeviceKind.valueOf(it) }.getOrNull() },
                spatialAudio = if (o.has("spatial")) o.optBoolean("spatial") else null,
                hidden = o.optBoolean("hidden", false),
            )
        }
    }.getOrDefault(emptyMap())

    private fun save(map: Map<String, BluetoothDeviceSettings>) {
        val root = JSONObject()
        map.forEach { (k, s) ->
            root.put(k, JSONObject().apply {
                s.nickname?.let { put("nickname", it) }
                s.kind?.let { put("kind", it.name) }
                s.spatialAudio?.let { put("spatial", it) }
                if (s.hidden) put("hidden", true)
            })
        }
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private companion object {
        const val KEY = "devices"
    }
}
