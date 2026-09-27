package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.connectivity.BluetoothConnectOutcome
import com.theveloper.pixelplay.data.connectivity.BluetoothDeviceActions
import com.theveloper.pixelplay.data.connectivity.BluetoothDeviceHistory
import com.theveloper.pixelplay.data.connectivity.BluetoothDeviceKind
import com.theveloper.pixelplay.data.connectivity.BluetoothDevicePrefs
import com.theveloper.pixelplay.data.connectivity.BluetoothDeviceSettings
import com.theveloper.pixelplay.data.connectivity.BluetoothRenameOutcome
import com.theveloper.pixelplay.data.connectivity.BluetoothPeer
import com.theveloper.pixelplay.data.connectivity.ProximityReading
import com.theveloper.pixelplay.data.connectivity.RadioToggleController
import com.theveloper.pixelplay.data.connectivity.RadioToggleResult
import com.theveloper.pixelplay.data.recognition.shizuku.ShizukuStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The connect menu's radio switches, continuous Bluetooth scanning and device history.
 * Radios are switched for real when possible (see [RadioToggleController]).
 */
@HiltViewModel
class ConnectionControlsViewModel @Inject constructor(
    private val connectivity: ConnectivityStateHolder,
    private val radios: RadioToggleController,
    private val history: BluetoothDeviceHistory,
    private val deviceActions: BluetoothDeviceActions,
    private val devicePrefs: BluetoothDevicePrefs
) : ViewModel() {

    /** What the user set per device (name, type, spatial audio), keyed like [BluetoothPeer.key]. */
    val deviceSettings: StateFlow<Map<String, BluetoothDeviceSettings>> = devicePrefs.settings

    /** Connects in progress; scanning pauses meanwhile (discovery makes connecting fail). */
    private var connecting = 0

    val mobileDataEnabled: StateFlow<Boolean> = radios.mobileDataEnabled
    val hasMobileData: Boolean get() = radios.hasMobileData
    val shizukuStatus: StateFlow<ShizukuStatus> = radios.shizukuStatus
    val isScanning: StateFlow<Boolean> = connectivity.isBluetoothDiscovering
    val nearby: StateFlow<List<BluetoothPeer>> = connectivity.nearbyBluetoothDevices

    /** Smoothed distance bands, keyed like [BluetoothPeer.key] (address, or "name:<lowercase>"). */
    val proximity: StateFlow<Map<String, ProximityReading>> = connectivity.proximity

    /**
     * Every device this phone has connected to before: paired devices plus the ones seen
     * connected while PixelPlayer ran, newest connection first, then paired-only by name.
     */
    val previouslyConnected: StateFlow<List<BluetoothPeer>> = combine(
        connectivity.pairedBluetoothDevices,
        history.entries,
        connectivity.bluetoothAudioDeviceStates,
        devicePrefs.settings
    ) { paired, seen, current, settings ->
        val connectedKeys = current.filter { it.isConnected }.map { it.address ?: "name:${it.name.lowercase()}" }.toSet()
        val byKey = linkedMapOf<String, BluetoothPeer>()
        seen.forEach { e ->
            byKey[e.key] = BluetoothPeer(
                name = e.name,
                address = e.address,
                kind = e.kind,
                isConnected = e.key in connectedKeys,
                lastConnectedAt = e.lastConnectedAt
            )
        }
        paired.forEach { p ->
            val existing = byKey[p.key]
            byKey[p.key] = existing?.copy(isBonded = true, kind = p.kind, batteryPercent = p.batteryPercent)
                ?: p.copy(isConnected = p.key in connectedKeys)
        }
        byKey.values
            // The user's own name and device type win; forgotten ones stay hidden until they connect again.
            .mapNotNull { peer ->
                val set = settings[peer.key] ?: return@mapNotNull peer
                if (set.hidden && !peer.isConnected) return@mapNotNull null
                peer.copy(name = set.nickname ?: peer.name, kind = set.kind ?: peer.kind)
            }
            .sortedWith(
            compareByDescending<BluetoothPeer> { it.isConnected }
                .thenByDescending { it.lastConnectedAt ?: 0L }
                .thenBy { it.name.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var scanJob: Job? = null

    /**
     * Keeps scanning for devices while the menu is open: restarts discovery whenever the
     * previous scan finishes (a scan lasts ~12 s). Stops as soon as the menu closes.
     */
    fun startAutoScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            while (isActive) {
                // Checked every second: classic discovery (finds new devices) is restarted the
                // moment the last one ends, and the BLE scan keeps signal strength live. Found
                // devices are only added, never cleared, so the list doesn't flicker.
                if (connecting == 0) {
                    if (!connectivity.isBluetoothDiscovering.value) connectivity.startBluetoothScan()
                    connectivity.startProximityScan()
                }
                connectivity.tickProximity()
                delay(1_000)
            }
        }
    }

    fun stopAutoScan() {
        scanJob?.cancel()
        scanJob = null
        connectivity.stopBluetoothScan()
        connectivity.stopProximityScan()
    }

    fun forget(peer: BluetoothPeer) = history.forget(peer.key)

    // ── Device actions (connect, rename, forget, contacts, spatial audio, type) ─────────

    fun connect(peer: BluetoothPeer, onResult: (BluetoothConnectOutcome) -> Unit) {
        val address = peer.address ?: return onResult(BluetoothConnectOutcome.Failed("This device has no address"))
        viewModelScope.launch {
            connecting++
            val outcome = try {
                deviceActions.connect(address, isAudio = peer.kind.isAudioOutput)
            } finally {
                connecting--
            }
            onResult(outcome)
        }
    }

    /** Saves the name in PixelPlayer and tries to rename it for the whole system. */
    fun rename(peer: BluetoothPeer, name: String): BluetoothRenameOutcome {
        val clean = name.trim()
        devicePrefs.update(peer.key) { it.copy(nickname = clean.takeIf { n -> n.isNotEmpty() }) }
        val address = peer.address ?: return BluetoothRenameOutcome.APP_ONLY
        if (clean.isEmpty()) return BluetoothRenameOutcome.APP_ONLY
        return deviceActions.renameInSystem(address, clean)
    }

    fun requestCompanionLink(peer: BluetoothPeer, onPrompt: (android.content.IntentSender) -> Unit, onFailure: (String) -> Unit) {
        val address = peer.address ?: return onFailure("This device has no address")
        deviceActions.requestCompanionLink(address, onPrompt, onFailure)
    }

    /**
     * Unpairs the device. Returns true when it was unpaired; false when Android refused, in
     * which case it's just hidden from this list.
     */
    fun forgetDevice(peer: BluetoothPeer): Boolean {
        val unpaired = peer.address?.let { deviceActions.unpair(it) } == true
        history.forget(peer.key)
        if (!unpaired) devicePrefs.update(peer.key) { it.copy(hidden = true) }
        return unpaired
    }

    fun phonebookAccess(peer: BluetoothPeer): Boolean? = peer.address?.let { deviceActions.phonebookAccess(it) }

    fun setPhonebookAccess(peer: BluetoothPeer, allow: Boolean): Boolean =
        peer.address?.let { deviceActions.setPhonebookAccess(it, allow) } == true

    val spatialAudioSupported: Boolean get() = deviceActions.spatialAudioSupported()

    fun setSpatialAudio(peer: BluetoothPeer, enabled: Boolean) =
        devicePrefs.update(peer.key) { it.copy(spatialAudio = enabled) }

    fun setDeviceKind(peer: BluetoothPeer, kind: BluetoothDeviceKind) =
        devicePrefs.update(peer.key) { it.copy(kind = kind) }

    fun refreshMobileData() = radios.refreshMobileData()

    fun toggleWifi(currentlyOn: Boolean, onResult: (RadioToggleResult) -> Unit) {
        viewModelScope.launch { onResult(radios.setWifi(!currentlyOn)) }
    }

    fun toggleBluetooth(currentlyOn: Boolean, onResult: (RadioToggleResult) -> Unit) {
        viewModelScope.launch { onResult(radios.setBluetooth(!currentlyOn)) }
    }

    fun toggleMobileData(onResult: (RadioToggleResult) -> Unit) {
        viewModelScope.launch { onResult(radios.setMobileData(!radios.mobileDataEnabled.value)) }
    }

    fun showOutputSwitcher(): Boolean = radios.showOutputSwitcher()

    override fun onCleared() {
        stopAutoScan()
        super.onCleared()
    }
}
