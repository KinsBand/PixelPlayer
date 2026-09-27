package com.theveloper.pixelplay.data.dsp

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class OutputDeviceType(val displayName: String) {
    BLUETOOTH("Bluetooth"),
    WIRED("Wired"),
    SPEAKER("Speaker"),
    USB("USB DAC"),
    OTHER("Audio Output")
}

data class OutputDeviceInfo(
    val id: Int,
    val type: OutputDeviceType,
    val name: String,
    val routingKey: String
) {
    companion object {
        val SPEAKER_DEFAULT = OutputDeviceInfo(
            id = 0,
            type = OutputDeviceType.SPEAKER,
            name = "Phone Speaker",
            routingKey = "internal:speaker"
        )
    }
}

/**
 * Tracks hardware audio output routing changes (Bluetooth, Wired, Speaker, USB DAC)
 * to enable automatic per-device EQ preset switching.
 */
@Singleton
class AudioOutputDeviceTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioOutputTracker"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _currentDevice = MutableStateFlow(OutputDeviceInfo.SPEAKER_DEFAULT)
    val currentDevice: StateFlow<OutputDeviceInfo> = _currentDevice.asStateFlow()

    private var deviceCallback: AudioDeviceCallback? = null

    init {
        detectCurrentOutputDevice()
        registerDeviceListener()
    }

    private fun registerDeviceListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    detectCurrentOutputDevice()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    detectCurrentOutputDevice()
                }
            }
            deviceCallback = callback
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
    }

    fun detectCurrentOutputDevice() {
        if (audioManager == null) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val primaryOutput = findPrimaryOutputDevice(devices)
                if (primaryOutput != null) {
                    val info = mapDeviceInfo(primaryOutput)
                    Timber.tag(TAG).d("Active audio output detected: ${info.name} (${info.type})")
                    _currentDevice.value = info
                    return
                }
            }

            // Fallback: check wired headset or bluetooth A2DP
            @Suppress("DEPRECATION")
            val isWired = audioManager.isWiredHeadsetOn
            @Suppress("DEPRECATION")
            val isBluetooth = audioManager.isBluetoothA2dpOn

            val fallbackInfo = when {
                isBluetooth -> OutputDeviceInfo(
                    id = 1,
                    type = OutputDeviceType.BLUETOOTH,
                    name = "Bluetooth Audio",
                    routingKey = "bt:default"
                )
                isWired -> OutputDeviceInfo(
                    id = 2,
                    type = OutputDeviceType.WIRED,
                    name = "Wired Headphones",
                    routingKey = "wired:headset"
                )
                else -> OutputDeviceInfo.SPEAKER_DEFAULT
            }
            _currentDevice.value = fallbackInfo
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error detecting audio output device")
        }
    }

    private fun findPrimaryOutputDevice(devices: Array<AudioDeviceInfo>): AudioDeviceInfo? {
        // Priority order for active playback listening:
        // 1. Bluetooth A2DP / LE Audio / Hearing Aid
        // 2. USB Headset / USB Device
        // 3. Wired Headset / Headphones
        // 4. Built-in Speaker
        val bluetooth = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                ))
        }
        if (bluetooth != null) return bluetooth

        val usb = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY)
        }
        if (usb != null) return usb

        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        if (wired != null) return wired

        val speaker = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        return speaker ?: devices.firstOrNull()
    }

    private fun mapDeviceInfo(device: AudioDeviceInfo): OutputDeviceInfo {
        val productName = device.productName?.toString()?.trim() ?: ""

        val type = when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputDeviceType.BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputDeviceType.WIRED
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> OutputDeviceType.USB
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputDeviceType.SPEAKER
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                )) {
                    OutputDeviceType.BLUETOOTH
                } else {
                    OutputDeviceType.OTHER
                }
            }
        }

        val name = when {
            productName.isNotEmpty() && productName != "Built-in Speaker" -> productName
            type == OutputDeviceType.BLUETOOTH -> "Bluetooth Device"
            type == OutputDeviceType.WIRED -> "Wired Headphones"
            type == OutputDeviceType.USB -> "USB DAC / Audio"
            type == OutputDeviceType.SPEAKER -> "Phone Speaker"
            else -> "Audio Output"
        }

        val key = when (type) {
            OutputDeviceType.BLUETOOTH -> if (productName.isNotEmpty()) "bt:$productName" else "bt:default"
            OutputDeviceType.WIRED -> "wired:headset"
            OutputDeviceType.USB -> if (productName.isNotEmpty()) "usb:$productName" else "usb:default"
            OutputDeviceType.SPEAKER -> "internal:speaker"
            OutputDeviceType.OTHER -> "other:$productName"
        }

        return OutputDeviceInfo(
            id = device.id,
            type = type,
            name = name,
            routingKey = key
        )
    }
}
