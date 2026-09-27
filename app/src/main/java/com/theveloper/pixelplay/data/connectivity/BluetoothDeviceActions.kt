package com.theveloper.pixelplay.data.connectivity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.media.AudioManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** How a "connect" tap in the connect menu ended. */
sealed interface BluetoothConnectOutcome {
    data object Connected : BluetoothConnectOutcome
    data class Failed(val reason: String) : BluetoothConnectOutcome
}

/** How a rename ended. */
enum class BluetoothRenameOutcome {
    /** The system name changed too (every app, and Settings, show it). */
    SYSTEM,
    /** Saved in PixelPlayer; Android wants PixelPlayer linked to the device first to rename it everywhere. */
    APP_ONLY_CAN_LINK,
    /** Saved in PixelPlayer only. */
    APP_ONLY,
}

/**
 * Connect, rename, forget and contact-sharing for paired Bluetooth devices, done from inside
 * PixelPlayer.
 *
 * Android keeps most of these for the system Settings app (they need BLUETOOTH_PRIVILEGED),
 * so each action tries, in order: the public API, the older hidden API (still allowed on many
 * phones), then a fallback that works for normal apps. Nothing here opens Settings; when
 * Android refuses, the caller gets a clear result to show instead.
 */
@Singleton
class BluetoothDeviceActions @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    // ── Connect ───────────────────────────────────────────────────────────────────────

    /**
     * Connects a paired device. Waits up to [timeoutMs] for Android to report it connected
     * (audio devices: the A2DP / headset profile; others: the link itself).
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(address: String, isAudio: Boolean, timeoutMs: Long = 15_000): BluetoothConnectOutcome =
        withContext(Dispatchers.IO) {
            val adapter = adapter ?: return@withContext BluetoothConnectOutcome.Failed("This phone has no Bluetooth")
            if (!safe(false) { adapter.isEnabled }) return@withContext BluetoothConnectOutcome.Failed("Turn Bluetooth on first")
            val device = safe<BluetoothDevice?>(null) { adapter.getRemoteDevice(address) }
                ?: return@withContext BluetoothConnectOutcome.Failed("Unknown device")
            if (isConnected(device)) return@withContext BluetoothConnectOutcome.Connected

            // Discovery makes connecting slow and flaky; the menu restarts it afterwards.
            safe(Unit) { if (adapter.isDiscovering) adapter.cancelDiscovery() }

            val done = CompletableDeferred<Boolean>()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    if (d?.address != address) return
                    when (intent.action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> if (!isAudio) done.complete(true)
                        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                            if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) done.complete(true)
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            try {
                run {
                    // Detached: a blocking socket attempt must not hold up the answer.
                    val attempts = CoroutineScope(Dispatchers.IO).launch {
                        // 1. The profile's own connect (hidden API; allowed on many phones).
                        val profiles = if (isAudio) listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, PROFILE_LE_AUDIO)
                        else listOf(PROFILE_HID_HOST, BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
                        var asked = false
                        for (p in profiles) {
                            if (done.isCompleted) return@launch
                            asked = profileConnect(adapter, p, device) || asked
                        }
                        if (asked) delay(3_000)
                        // 2. Open a link to one of its services. Most headphones and speakers
                        // bring their audio up as soon as the phone links to them.
                        if (!done.isCompleted) rfcommPoke(device)
                        // Some devices connect their profiles a few seconds after the link.
                        if (!done.isCompleted) {
                            delay(2_000)
                            if (isConnected(device) && !isAudio) done.complete(true)
                        }
                    }
                    val ok = withTimeoutOrNull(timeoutMs) { done.await() } == true
                    attempts.cancel()
                    if (ok || isConnected(device) && !isAudio) BluetoothConnectOutcome.Connected
                    else BluetoothConnectOutcome.Failed(
                        if (safe(BluetoothDevice.BOND_NONE) { device.bondState } != BluetoothDevice.BOND_BONDED) "It isn't paired with this phone anymore"
                        else "No answer. Make sure it's on, nearby and not connected to another phone"
                    )
                }
            } finally {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }

    @SuppressLint("MissingPermission")
    private suspend fun profileConnect(adapter: BluetoothAdapter, profile: Int, device: BluetoothDevice): Boolean {
        val proxy = withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine<BluetoothProfile?> { cont ->
                val ok = safe(false) {
                    adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                            if (cont.isActive) cont.resume(proxy)
                        }
                        override fun onServiceDisconnected(p: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }, profile)
                }
                if (!ok && cont.isActive) cont.resume(null)
            }
        } ?: return false
        return try {
            val m = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
            (m.invoke(proxy, device) as? Boolean) == true
        } catch (t: Throwable) {
            Timber.d("Bluetooth profile %d connect not allowed: %s", profile, t.javaClass.simpleName)
            false
        } finally {
            safe(Unit) { adapter.closeProfileProxy(profile, proxy) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun rfcommPoke(device: BluetoothDevice) {
        val offered = safe<Array<ParcelUuid>?>(null) { device.uuids }?.map { it.uuid }.orEmpty()
        val candidates = (PREFERRED_RFCOMM.filter { it in offered } + PREFERRED_RFCOMM).distinct()
        for (uuid in candidates.take(3)) {
            val socket = safe<android.bluetooth.BluetoothSocket?>(null) { device.createInsecureRfcommSocketToServiceRecord(uuid) } ?: continue
            try {
                socket.connect()
                // Linked. Keep it a moment so the device's profiles can come up, then let go.
                Thread.sleep(2_500)
                return
            } catch (t: Throwable) {
                Timber.d("RFCOMM link to %s failed: %s", uuid, t.message)
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    /** Whether the phone currently has a link to [device] (hidden API, false when refused). */
    fun isConnected(device: BluetoothDevice): Boolean = runCatching {
        device.javaClass.getMethod("isConnected").invoke(device) as? Boolean
    }.getOrNull() == true

    // ── Rename ────────────────────────────────────────────────────────────────────────

    /** Tries to change the device's system name. PixelPlayer's own name is saved by the caller. */
    @SuppressLint("MissingPermission")
    fun renameInSystem(address: String, name: String): BluetoothRenameOutcome {
        val device = device(address) ?: return BluetoothRenameOutcome.APP_ONLY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val code = runCatching { device.setAlias(name) }.getOrElse { -1 }
            if (code == android.bluetooth.BluetoothStatusCodes.SUCCESS) return BluetoothRenameOutcome.SYSTEM
            // Android 12+ lets an app rename a device it is linked with ("companion device").
            return if (isCompanion(address)) BluetoothRenameOutcome.APP_ONLY else BluetoothRenameOutcome.APP_ONLY_CAN_LINK
        }
        val ok = runCatching {
            device.javaClass.getMethod("setAlias", String::class.java).invoke(device, name) as? Boolean
        }.getOrNull() == true
        return if (ok) BluetoothRenameOutcome.SYSTEM else BluetoothRenameOutcome.APP_ONLY
    }

    /** True when PixelPlayer is already linked with this device as a companion. */
    @Suppress("DEPRECATION")
    fun isCompanion(address: String): Boolean = runCatching {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        cdm.associations.any { it.equals(address, ignoreCase = true) }
    }.getOrDefault(false)

    /**
     * Asks Android to link PixelPlayer with the device (one system prompt, shown over the
     * app). [onPrompt] gets the prompt to launch; [onFailure] if Android can't show it.
     */
    @Suppress("DEPRECATION")
    fun requestCompanionLink(address: String, onPrompt: (IntentSender) -> Unit, onFailure: (String) -> Unit) {
        val cdm = runCatching { context.getSystemService(CompanionDeviceManager::class.java) }.getOrNull()
        if (cdm == null) {
            onFailure("This phone can't link devices to apps")
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        runCatching {
            cdm.associate(request, object : CompanionDeviceManager.Callback() {
                @Deprecated("Deprecated in Java")
                override fun onDeviceFound(chooserLauncher: IntentSender) = onPrompt(chooserLauncher)
                override fun onFailure(error: CharSequence?) = onFailure(error?.toString() ?: "Couldn't find the device")
            }, null)
        }.onFailure { onFailure(it.message ?: "Couldn't link the device") }
    }

    // ── Forget ────────────────────────────────────────────────────────────────────────

    /** Unpairs the device. False when Android doesn't let PixelPlayer do it. */
    @SuppressLint("MissingPermission")
    fun unpair(address: String): Boolean {
        val device = device(address) ?: return false
        if (safe(BluetoothDevice.BOND_NONE) { device.bondState } == BluetoothDevice.BOND_NONE) return true
        return runCatching { device.javaClass.getMethod("removeBond").invoke(device) as? Boolean }.getOrNull() == true
    }

    // ── Contacts & call history (phonebook access) ────────────────────────────────────

    /** true / false, or null when Android won't say. */
    fun phonebookAccess(address: String): Boolean? {
        val device = device(address) ?: return null
        val value = runCatching { device.javaClass.getMethod("getPhonebookAccessPermission").invoke(device) as? Int }.getOrNull()
        return when (value) {
            ACCESS_ALLOWED -> true
            ACCESS_REJECTED -> false
            else -> null
        }
    }

    /** False when Android doesn't let PixelPlayer change it. */
    fun setPhonebookAccess(address: String, allow: Boolean): Boolean {
        val device = device(address) ?: return false
        val ok = runCatching {
            device.javaClass.getMethod("setPhonebookAccessPermission", Int::class.javaPrimitiveType)
                .invoke(device, if (allow) ACCESS_ALLOWED else ACCESS_REJECTED) as? Boolean
        }.getOrNull() == true
        return ok && phonebookAccess(address) == allow
    }

    // ── Spatial audio ─────────────────────────────────────────────────────────────────

    /** The phone can spatialize audio at all (Android 12L+ with a spatializer). */
    fun spatialAudioSupported(): Boolean {
        if (Build.VERSION.SDK_INT < 32) return false
        return runCatching {
            val sp = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).spatializer
            sp.immersiveAudioLevel != android.media.Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
        }.getOrDefault(false)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────

    private fun device(address: String): BluetoothDevice? =
        runCatching { adapter?.getRemoteDevice(address) }.getOrNull()

    private inline fun <T> safe(fallback: T, block: () -> T): T =
        try { block() } catch (t: Throwable) { fallback }

    private companion object {
        const val PROFILE_HID_HOST = 4
        const val PROFILE_LE_AUDIO = 22
        const val ACCESS_ALLOWED = 1
        const val ACCESS_REJECTED = 2

        /** Hands-free, headset, serial port: RFCOMM services headphones and speakers offer. */
        val PREFERRED_RFCOMM = listOf(
            UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("00001108-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
        )
    }
}
