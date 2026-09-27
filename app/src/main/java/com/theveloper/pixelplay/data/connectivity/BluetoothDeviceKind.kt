package com.theveloper.pixelplay.data.connectivity

import android.bluetooth.BluetoothClass

/**
 * What a Bluetooth device is, so the connect menu can show the right icon
 * (speaker, TV, headphones, phone, remote, …). Decided from the device's Bluetooth
 * class first, then from its name when the class is missing or too generic.
 */
enum class BluetoothDeviceKind(val label: String, val isAudioOutput: Boolean) {
    HEADPHONES("Headphones", true),
    EARBUDS("Earbuds", true),
    HEADSET("Headset", true),
    SPEAKER("Speaker", true),
    SOUNDBAR("Soundbar", true),
    TV("TV", true),
    CAR("Car", true),
    HIFI("Hi-fi", true),
    PHONE("Phone", false),
    TABLET("Tablet", false),
    COMPUTER("Computer", false),
    WATCH("Watch", false),
    REMOTE("Remote", false),
    KEYBOARD("Keyboard", false),
    MOUSE("Mouse", false),
    GAME_CONTROLLER("Controller", false),
    NETWORK("Network", false),
    PRINTER("Printer", false),
    HEALTH("Health", false),
    UNKNOWN("Bluetooth device", false);

    companion object {
        /**
         * @param majorClass [BluetoothClass.getMajorDeviceClass], or null when unknown.
         * @param deviceClass [BluetoothClass.getDeviceClass], or null when unknown.
         */
        fun classify(majorClass: Int?, deviceClass: Int?, name: String?): BluetoothDeviceKind {
            fromName(name)?.let { byName ->
                // The name is more specific than a generic class ("uncategorized", "audio/video").
                if (majorClass == null || majorClass == BluetoothClass.Device.Major.UNCATEGORIZED ||
                    majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO || majorClass == BluetoothClass.Device.Major.MISC
                ) return byName
            }
            val byClass = when (deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> HEADPHONES
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> HEADSET
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> SPEAKER
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> HIFI
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> CAR
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR,
                BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX -> TV
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_GAMING_TOY,
                BluetoothClass.Device.TOY_CONTROLLER -> GAME_CONTROLLER
                BluetoothClass.Device.PHONE_SMART,
                BluetoothClass.Device.PHONE_CELLULAR,
                BluetoothClass.Device.PHONE_CORDLESS -> PHONE
                BluetoothClass.Device.COMPUTER_LAPTOP,
                BluetoothClass.Device.COMPUTER_DESKTOP,
                BluetoothClass.Device.COMPUTER_SERVER -> COMPUTER
                BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA,
                BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA -> TABLET
                BluetoothClass.Device.WEARABLE_WRIST_WATCH -> WATCH
                PERIPHERAL_KEYBOARD, PERIPHERAL_KEYBOARD_POINTING -> KEYBOARD
                PERIPHERAL_POINTING -> MOUSE
                PERIPHERAL_REMOTE_CONTROL -> REMOTE
                PERIPHERAL_GAMEPAD, PERIPHERAL_JOYSTICK -> GAME_CONTROLLER
                else -> null
            }
            if (byClass != null) return byClass
            val byMajor = when (majorClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> SPEAKER
                BluetoothClass.Device.Major.PHONE -> PHONE
                BluetoothClass.Device.Major.COMPUTER -> COMPUTER
                BluetoothClass.Device.Major.WEARABLE -> WATCH
                BluetoothClass.Device.Major.PERIPHERAL -> REMOTE
                BluetoothClass.Device.Major.NETWORKING -> NETWORK
                BluetoothClass.Device.Major.IMAGING -> PRINTER
                BluetoothClass.Device.Major.HEALTH -> HEALTH
                BluetoothClass.Device.Major.TOY -> GAME_CONTROLLER
                else -> null
            }
            return fromName(name) ?: byMajor ?: UNKNOWN
        }

        private fun fromName(name: String?): BluetoothDeviceKind? {
            val n = name?.lowercase()?.trim().orEmpty()
            if (n.isEmpty()) return null
            fun has(vararg words: String) = words.any { n.contains(it) }
            return when {
                has("soundbar", "sound bar", "beam", "arc", "playbar") -> SOUNDBAR
                has("tv", "bravia", "chromecast", "fire stick", "firetv", "fire tv", "roku", "shield", "apple tv", "projector") -> TV
                has("buds", "airpods", "earbud", "pods", "ear (", "freebuds", "galaxy buds", "wf-", "tws") -> EARBUDS
                has("headphone", "wh-", "qc", "quietcomfort", "studio", "momentum", "headset", "over-ear") -> HEADPHONES
                has("car", "sync", "uconnect", "carplay", "audi", "bmw", "toyota", "ford", "honda", "mazda", "hyundai", "kia", "tesla", "mercedes", "vw ", "volkswagen") -> CAR
                has("speaker", "jbl", "boom", "megaboom", "wonderboom", "soundlink", "sonos", "flip", "charge", "xtreme", "home mini", "nest", "echo", "homepod") -> SPEAKER
                has("remote") -> REMOTE
                has("watch", "band", "fitbit", "garmin") -> WATCH
                has("keyboard", "keys") -> KEYBOARD
                has("mouse", "trackpad", "mx master") -> MOUSE
                has("controller", "gamepad", "dualsense", "dualshock", "xbox", "joy-con", "8bitdo") -> GAME_CONTROLLER
                has("iphone", "pixel", "galaxy s", "galaxy a", "oneplus", "phone", "xperia") -> PHONE
                has("ipad", "tab ", "tablet") -> TABLET
                has("macbook", "laptop", "desktop", "pc", "thinkpad", "surface") -> COMPUTER
                else -> null
            }
        }

        // Peripheral minor classes (not all are public constants on every API level).
        private const val PERIPHERAL_KEYBOARD = 0x0540
        private const val PERIPHERAL_POINTING = 0x0580
        private const val PERIPHERAL_KEYBOARD_POINTING = 0x05C0
        private const val PERIPHERAL_JOYSTICK = 0x0504
        private const val PERIPHERAL_GAMEPAD = 0x0508
        private const val PERIPHERAL_REMOTE_CONTROL = 0x050C
    }
}

/** A Bluetooth device the menu can show: nearby, paired, or connected before. */
data class BluetoothPeer(
    val name: String,
    val address: String?,
    val kind: BluetoothDeviceKind,
    val isConnected: Boolean = false,
    val isBonded: Boolean = false,
    /** Last time this phone was connected to it (ms), or null if never seen connected. */
    val lastConnectedAt: Long? = null,
    val batteryPercent: Int? = null,
    /** When the scan first heard it this session (ms); newest first in "nearby". */
    val firstSeenAt: Long? = null
) {
    val key: String get() = address?.takeIf { it.isNotBlank() } ?: "name:${name.lowercase()}"
}
