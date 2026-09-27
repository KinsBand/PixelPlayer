package com.theveloper.pixelplay.data.drumkit

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log

/**
 * Finds an electronic drum kit plugged in over USB (class-compliant USB MIDI through an OTG
 * cable), or a Bluetooth / other MIDI device already paired with Android, and streams its hits.
 *
 * MIDI arrives on a background thread, is parsed there with the kit's own timestamps
 * (System.nanoTime clock), and hits are handed to [onHit] on the main thread. Status changes go
 * to [onStatus] on the main thread.
 */
class DrumKitConnection(
    context: Context,
    private val onHit: (DrumHit) -> Unit,
    private val onStatus: (Status) -> Unit,
) {
    sealed interface Status {
        /** This phone has no MIDI support. */
        data object Unsupported : Status
        data object NoKit : Status
        data class Connecting(val name: String) : Status
        data class Connected(val name: String, val usb: Boolean) : Status
        data class Failed(val name: String, val message: String) : Status
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var manager: MidiManager? = null
    private var device: MidiDevice? = null
    private var deviceInfo: MidiDeviceInfo? = null
    private val ports = ArrayList<MidiOutputPort>()
    private val parser = DrumMidiParser()
    private var started = false

    var status: Status = Status.NoKit
        private set

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            if (this@DrumKitConnection.device == null) tryOpen(device)
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            if (deviceInfo?.id == device.id) {
                close()
                post(Status.NoKit)
                // Another kit may still be plugged in.
                pickDevice()?.let { tryOpen(it) }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        val pm = appContext.packageManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            post(Status.Unsupported)
            return
        }
        val mm = appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        if (mm == null) {
            post(Status.Unsupported)
            return
        }
        manager = mm
        val t = HandlerThread("drum-kit-midi", android.os.Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }
        thread = t
        val h = Handler(t.looper)
        handler = h
        @Suppress("DEPRECATION")
        mm.registerDeviceCallback(callback, h)
        h.post { pickDevice()?.let { tryOpen(it) } ?: post(Status.NoKit) }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { manager?.unregisterDeviceCallback(callback) }
        close()
        thread?.quitSafely()
        thread = null
        handler = null
        manager = null
    }

    @Suppress("DEPRECATION")
    private fun allDevices(): List<MidiDeviceInfo> {
        val mm = manager ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= 33) {
            mm.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
        } else {
            mm.devices.toList()
        }
    }

    /** A device that sends MIDI to us: USB first, then Bluetooth, then anything else. */
    private fun pickDevice(): MidiDeviceInfo? = allDevices()
        .filter { it.outputPortCount > 0 }
        .sortedBy {
            when (it.type) {
                MidiDeviceInfo.TYPE_USB -> 0
                MidiDeviceInfo.TYPE_BLUETOOTH -> 1
                else -> 2
            }
        }
        .firstOrNull()

    private fun nameOf(info: MidiDeviceInfo): String {
        val p = info.properties
        return p.getString(MidiDeviceInfo.PROPERTY_PRODUCT)?.takeIf { it.isNotBlank() }
            ?: p.getString(MidiDeviceInfo.PROPERTY_NAME)?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(p.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER), "MIDI device").joinToString(" ")
    }

    private fun tryOpen(info: MidiDeviceInfo) {
        val mm = manager ?: return
        if (info.outputPortCount <= 0) return
        val name = nameOf(info)
        post(Status.Connecting(name))
        mm.openDevice(info, { dev ->
            if (dev == null) {
                post(Status.Failed(name, "Couldn't open the kit"))
                return@openDevice
            }
            if (!started) {
                runCatching { dev.close() }
                return@openDevice
            }
            device = dev
            deviceInfo = info
            val receiver = object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    val t = if (timestamp > 0) timestamp else System.nanoTime()
                    parser.feed(msg, offset, count, t) { hit -> main.post { onHit(hit) } }
                }
            }
            for (i in 0 until info.outputPortCount) {
                dev.openOutputPort(i)?.let { port ->
                    port.connect(receiver)
                    ports += port
                }
            }
            if (ports.isEmpty()) {
                post(Status.Failed(name, "The kit has no MIDI output"))
            } else {
                post(Status.Connected(name, info.type == MidiDeviceInfo.TYPE_USB))
            }
        }, handler)
    }

    private fun close() {
        ports.forEach { runCatching { it.close() } }
        ports.clear()
        runCatching { device?.close() }
        device = null
        deviceInfo = null
    }

    private fun post(s: Status) {
        main.post {
            status = s
            onStatus(s)
        }
        if (s is Status.Failed) Log.w(TAG, "${s.name}: ${s.message}")
    }

    private companion object {
        const val TAG = "DrumKit"
    }
}
