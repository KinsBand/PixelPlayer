package com.theveloper.pixelplay.presentation.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.connectivity.ProximityReading
import com.theveloper.pixelplay.data.connectivity.ProximityTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class BluetoothAudioDeviceState(
    val name: String,
    val address: String? = null,
    val isConnected: Boolean = false,
    val batteryPercent: Int? = null,
    /** Bluetooth class of the device, for the device-type icon. Null when unknown. */
    val majorClass: Int? = null,
    val deviceClass: Int? = null
) {
    val kind: com.theveloper.pixelplay.data.connectivity.BluetoothDeviceKind
        get() = com.theveloper.pixelplay.data.connectivity.BluetoothDeviceKind.classify(majorClass, deviceClass, name)
}

/**
 * Manages WiFi and Bluetooth connectivity state.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - WiFi state tracking (enabled, radio state, network name)
 * - Bluetooth state tracking (enabled, active device name, discovered/connected audio devices)
 * - System callback registration and lifecycle management
 */
@Singleton
class ConnectivityStateHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceHistory: com.theveloper.pixelplay.data.connectivity.BluetoothDeviceHistory
) {
    // WiFi State
    private val _isWifiEnabled = MutableStateFlow(false)
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    private val _isWifiRadioOn = MutableStateFlow(false)
    val isWifiRadioOn: StateFlow<Boolean> = _isWifiRadioOn.asStateFlow()

    private val _wifiName = MutableStateFlow<String?>(null)
    val wifiName: StateFlow<String?> = _wifiName.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Bluetooth State
    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _bluetoothName = MutableStateFlow<String?>(null)
    val bluetoothName: StateFlow<String?> = _bluetoothName.asStateFlow()

    private val _bluetoothAudioDeviceStates = MutableStateFlow<List<BluetoothAudioDeviceState>>(emptyList())
    val bluetoothAudioDeviceStates: StateFlow<List<BluetoothAudioDeviceState>> = _bluetoothAudioDeviceStates.asStateFlow()

    private val _bluetoothAudioDevices = MutableStateFlow<List<String>>(emptyList())
    val bluetoothAudioDevices: StateFlow<List<String>> = _bluetoothAudioDevices.asStateFlow()

    /** True while the phone is actively searching for Bluetooth devices. */
    private val _isBluetoothDiscovering = MutableStateFlow(false)
    val isBluetoothDiscovering: StateFlow<Boolean> = _isBluetoothDiscovering.asStateFlow()

    /** Every named device found by the current/last scan (all types, not only audio). */
    private val _nearbyBluetoothDevices = MutableStateFlow<List<com.theveloper.pixelplay.data.connectivity.BluetoothPeer>>(emptyList())
    val nearbyBluetoothDevices: StateFlow<List<com.theveloper.pixelplay.data.connectivity.BluetoothPeer>> = _nearbyBluetoothDevices.asStateFlow()

    /** Devices paired with this phone (the system's own "previously connected" list). */
    private val _pairedBluetoothDevices = MutableStateFlow<List<com.theveloper.pixelplay.data.connectivity.BluetoothPeer>>(emptyList())
    val pairedBluetoothDevices: StateFlow<List<com.theveloper.pixelplay.data.connectivity.BluetoothPeer>> = _pairedBluetoothDevices.asStateFlow()

    /** Devices this phone connected to while PixelPlayer was running, newest first. */
    val bluetoothConnectionHistory get() = deviceHistory.entries

    private val nearbyAll = linkedMapOf<String, com.theveloper.pixelplay.data.connectivity.BluetoothPeer>()

    // ---- Proximity (signal strength → distance band) ----
    private val proximityTracker = ProximityTracker()

    /** Smoothed distance readings keyed by address and by `"name:<lowercase>"` (see [BluetoothPeer.key]). */
    val proximity: StateFlow<Map<String, ProximityReading>> = proximityTracker.readings

    private var leScanCallback: ScanCallback? = null
    /** Android silently blocks apps that start more than 5 BLE scans in 30 s; stay under that. */
    private val leScanStarts = ArrayDeque<Long>()

    /**
     * Starts a low-power BLE scan used only for signal strength. Much lighter than classic discovery
     * and it keeps hearing earbuds that advertise while connected. Runs only while the menu is open.
     */
    @SuppressLint("MissingPermission")
    fun startProximityScan(): Boolean {
        if (leScanCallback != null) return true
        if (!canStartBluetoothDiscovery()) return false
        val scanner = safeBluetoothCall<android.bluetooth.le.BluetoothLeScanner?>(null) { bluetoothAdapter?.bluetoothLeScanner } ?: return false
        val now = System.currentTimeMillis()
        while (leScanStarts.isNotEmpty() && now - leScanStarts.first() > 30_000) leScanStarts.removeFirst()
        if (leScanStarts.size >= 4) return false
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = recordScanResult(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { recordScanResult(it) }
            override fun onScanFailed(errorCode: Int) { leScanCallback = null }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()
        val started = runCatching { scanner.startScan(null, settings, callback) }.isSuccess
        if (started) {
            leScanCallback = callback
            leScanStarts.addLast(now)
        }
        return started
    }

    @SuppressLint("MissingPermission")
    fun stopProximityScan() {
        val callback = leScanCallback ?: return
        leScanCallback = null
        if (!hasBluetoothScanPermission()) return
        safeBluetoothCall<Unit>(Unit) { bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback) }
    }

    /** Marks silent devices out of range. Called every couple of seconds while the menu is open. */
    fun tickProximity() = proximityTracker.tick()

    /** Stores a calibrated 1 m reference for a device (from "hold next to phone"), or clears it. */
    fun setProximityCalibration(key: String, txPowerAt1m: Int?) = proximityTracker.setCalibration(key, txPowerAt1m)

    private fun recordScanResult(result: ScanResult) {
        val address = safeBluetoothCall("") { result.device?.address.orEmpty() }
        // The advertised name needs no extra permission, unlike BluetoothDevice.getName().
        val name = result.scanRecord?.deviceName
        val tx = result.scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
            ?: result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
        proximityTracker.record(address, name, result.rssi, tx)
    }

    /** Starts a Bluetooth scan unless one is already running. Returns false when it can't. */
    fun startBluetoothScan(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        updateBluetoothEnabledState()
        if (!canStartBluetoothDiscovery()) return false
        if (isBluetoothDiscoveryActive(adapter)) {
            _isBluetoothDiscovering.value = true
            return true
        }
        startBluetoothDiscovery(adapter)
        _isBluetoothDiscovering.value = isBluetoothDiscoveryActive(adapter)
        return true
    }

    /** Stops a running scan (scanning slows Bluetooth audio, so it only runs while the menu is open). */
    fun stopBluetoothScan() {
        val adapter = bluetoothAdapter ?: return
        if (isBluetoothDiscoveryActive(adapter)) cancelBluetoothDiscovery(adapter)
        _isBluetoothDiscovering.value = false
    }

    @SuppressLint("MissingPermission")
    private fun refreshPairedDevices() {
        if (!hasBluetoothConnectPermission() || !_isBluetoothEnabled.value) {
            _pairedBluetoothDevices.value = emptyList()
            return
        }
        val bonded = safeBluetoothCall<Set<BluetoothDevice>>(emptySet()) { bluetoothAdapter?.bondedDevices.orEmpty() }
        val localNames = resolveLocalBluetoothDeviceNames()
        _pairedBluetoothDevices.value = bonded.mapNotNull { device ->
            val name = safeBluetoothCall("") { device.name?.trim().orEmpty() }
            if (name.isEmpty() || isOwnBluetoothDeviceName(name, localNames)) return@mapNotNull null
            val cls = safeBluetoothCall<BluetoothClass?>(null) { device.bluetoothClass }
            com.theveloper.pixelplay.data.connectivity.BluetoothPeer(
                name = name,
                address = safeBluetoothCall("") { device.address.orEmpty() }.takeIf { it.isNotEmpty() },
                kind = com.theveloper.pixelplay.data.connectivity.BluetoothDeviceKind.classify(cls?.majorDeviceClass, cls?.deviceClass, name),
                isBonded = true,
                batteryPercent = resolveBatteryPercent(device)
            )
        }.sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    private fun recordNearby(device: BluetoothDevice, rssi: Int? = null) {
        if (!hasBluetoothConnectPermission()) return
        val name = safeBluetoothCall("") { device.name?.trim().orEmpty() }
        if (name.isEmpty() || isOwnBluetoothDeviceName(name)) return
        val cls = safeBluetoothCall<BluetoothClass?>(null) { device.bluetoothClass }
        val peer = com.theveloper.pixelplay.data.connectivity.BluetoothPeer(
            name = name,
            address = safeBluetoothCall("") { device.address.orEmpty() }.takeIf { it.isNotEmpty() },
            kind = com.theveloper.pixelplay.data.connectivity.BluetoothDeviceKind.classify(cls?.majorDeviceClass, cls?.deviceClass, name),
            isBonded = safeBluetoothCall(false) { device.bondState == BluetoothDevice.BOND_BONDED }
        )
        // Keep every device found while the menu is open: a rescan only adds new ones, and a
        // device already listed keeps its place (newest found first).
        val existing = nearbyAll[peer.key]
        val merged = peer.copy(firstSeenAt = existing?.firstSeenAt ?: System.currentTimeMillis())
        if (merged != existing) {
            nearbyAll[peer.key] = merged
            _nearbyBluetoothDevices.value = nearbyAll.values.sortedByDescending { it.firstSeenAt ?: 0L }
        }
        if (rssi != null) proximityTracker.record(peer.address, name, rssi)
    }

    @SuppressLint("MissingPermission")
    private fun recordConnectedInHistory(device: BluetoothDevice) {
        if (!hasBluetoothConnectPermission()) return
        val name = safeBluetoothCall("") { device.name?.trim().orEmpty() }
        if (name.isEmpty() || isOwnBluetoothDeviceName(name)) return
        val cls = safeBluetoothCall<BluetoothClass?>(null) { device.bluetoothClass }
        deviceHistory.recordConnected(
            address = safeBluetoothCall("") { device.address.orEmpty() },
            name = name,
            majorClass = cls?.majorDeviceClass,
            deviceClass = cls?.deviceClass
        )
    }

    /** Looks up the class of a connected audio device by address, for its icon and the history. */
    @SuppressLint("MissingPermission")
    private fun classOf(address: String?): BluetoothClass? {
        if (address.isNullOrBlank() || !hasBluetoothConnectPermission()) return null
        return safeBluetoothCall<BluetoothClass?>(null) {
            if (BluetoothAdapter.checkBluetoothAddress(address)) bluetoothAdapter?.getRemoteDevice(address)?.bluetoothClass else null
        }
    }
    
    // Offline Barrier Event
    // Event to signal that playback was blocked due to offline status
    // Using extraBufferCapacity to ensure the event isn't lost if no collectors are immediately suspended
    private val _offlinePlaybackBlocked = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val offlinePlaybackBlocked: SharedFlow<Unit> = _offlinePlaybackBlocked.asSharedFlow()
    
    fun triggerOfflineBlockedEvent() {
        _offlinePlaybackBlocked.tryEmit(Unit)
    }

    /**
     * Manually refresh local connection info (e.g. WiFi SSID).
     */
    fun refreshLocalConnectionInfo(refreshBluetoothDevices: Boolean = false) {
        updateWifiInfo()
        if (refreshBluetoothDevices) {
            refreshBluetoothAudioDevices()
        } else {
            updateAudioDevices()
        }
    }

    // System services
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val audioManager: android.media.AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    // Callbacks and receivers
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiStateReceiver: BroadcastReceiver? = null
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null

    private var isInitialized = false
    private val discoveredBluetoothAudioDevices = linkedMapOf<String, BluetoothAudioDeviceState>()

    /**
     * Initialize connectivity monitoring. Should be called once from ViewModel.
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        // Initial state check
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        updateWifiRadioState()
        _isWifiEnabled.value = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (_isWifiEnabled.value) {
            updateWifiInfo()
        }
        
        _isOnline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        updateBluetoothEnabledState()

        // Register WiFi network callback
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            // Track all valid networks to handle rapid switching
            private val availableNetworks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                // Network is available, but waiting for capability check
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
                if (hasInternet && isValidated) {
                    availableNetworks.add(network)
                } else {
                    availableNetworks.remove(network)
                }
                
                checkConnectivity()
                
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    _isWifiEnabled.value = true
                    updateWifiInfo()
                }
            }

            override fun onLost(network: Network) {
                availableNetworks.remove(network)
                checkConnectivity()
                
                val currentNetwork = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
                _isWifiEnabled.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (!_isWifiEnabled.value) _wifiName.value = null
            }
            
            private fun checkConnectivity() {
                _isOnline.value = availableNetworks.isNotEmpty()
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback!!)

        // Register receivers
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                     updateWifiRadioState()
                }
            }
        }
        context.registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))

        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        updateBluetoothEnabledState()
                        if (_isBluetoothEnabled.value) {
                            updateAudioDevices()
                        } else {
                            _isBluetoothDiscovering.value = false
                            leScanCallback = null
                            proximityTracker.clear()
                            nearbyAll.clear()
                            _nearbyBluetoothDevices.value = emptyList()
                            _pairedBluetoothDevices.value = emptyList()
                            discoveredBluetoothAudioDevices.clear()
                            _bluetoothAudioDeviceStates.value = emptyList()
                            _bluetoothAudioDevices.value = emptyList()
                            updateBluetoothName(emptyList())
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> updateAudioDevices()
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _isBluetoothDiscovering.value = true
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _isBluetoothDiscovering.value = false
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        extractBluetoothDevice(intent)?.let { recordConnectedInHistory(it) }
                        updateAudioDevices()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> updateAudioDevices()
                    BluetoothDevice.ACTION_FOUND -> {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        extractBluetoothDevice(intent)?.let { recordNearby(it, rssi.takeIf { r -> r != Short.MIN_VALUE.toInt() }) }
                        extractBluetoothDevice(intent)
                            ?.takeIf { it.isAudioOutputCandidate() }
                            ?.toBluetoothAudioDeviceState(isConnected = false)
                            ?.let { deviceState ->
                                discoveredBluetoothAudioDevices[deviceState.uniqueKey()] = deviceState
                                updateAudioDevices()
                            }
                    }
                }
            }
        }
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )

        // Audio Device Callback
        audioDeviceCallback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateAudioDevices()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateAudioDevices()
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        updateAudioDevices()
    }

    private fun updateWifiRadioState() {
        _isWifiRadioOn.value = wifiManager?.isWifiEnabled == true
    }

    private fun updateWifiInfo() {
        _wifiName.value = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && hasFineLocationPermission()) {
            readConnectedWifiSsid()
        } else {
            // On Android 12+ we intentionally avoid SSID reads so the cast flow does not look like location usage.
            "WiFi Connected"
        }
    }

    private fun updateBluetoothName(connectedAudioDevices: List<String>) {
        if (!_isBluetoothEnabled.value) {
            _bluetoothName.value = null
            return
        }

        _bluetoothName.value = _bluetoothName.value
            ?.takeIf { it in connectedAudioDevices }
            ?: connectedAudioDevices.firstOrNull()
    }

    private fun updateAudioDevices() {
        updateBluetoothEnabledState()
        if (!_isBluetoothEnabled.value) {
            discoveredBluetoothAudioDevices.clear()
            _bluetoothAudioDeviceStates.value = emptyList()
            _bluetoothAudioDevices.value = emptyList()
            updateBluetoothName(emptyList())
            return
        }

        val localDeviceNames = resolveLocalBluetoothDeviceNames()
        val connectedDevices = collectConnectedBluetoothDevices()
        val availableDevices = discoveredBluetoothAudioDevices.values
            .map { sanitizeBluetoothAudioDeviceState(it, localDeviceNames) }
            .filterNotNull()
        val connectedDeviceKeys = connectedDevices.mapTo(mutableSetOf()) { it.uniqueKey() }

        val mergedDevices = linkedMapOf<String, BluetoothAudioDeviceState>()
        connectedDevices.forEach { mergedDevices[it.uniqueKey()] = it }
        availableDevices.forEach { deviceState ->
            val key = deviceState.uniqueKey()
            val existing = mergedDevices[key]
            if (existing == null) {
                mergedDevices[key] = deviceState
            } else {
                mergedDevices[key] = existing.mergeWith(deviceState)
            }
        }

        val bluetoothDevices = mergedDevices.values
            .sortedWith(
                compareByDescending<BluetoothAudioDeviceState> { it.uniqueKey() in connectedDeviceKeys }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        // Fill in the device class (for the icon) and remember every connected device.
        val withClass = bluetoothDevices.map { state ->
            if (state.majorClass != null) state else {
                val cls = classOf(state.address)
                if (cls == null) state else state.copy(majorClass = cls.majorDeviceClass, deviceClass = cls.deviceClass)
            }
        }
        withClass.filter { it.uniqueKey() in connectedDeviceKeys }.forEach { state ->
            deviceHistory.recordConnected(state.address, state.name, state.majorClass, state.deviceClass)
        }
        _bluetoothAudioDeviceStates.value = withClass
        _bluetoothAudioDevices.value = withClass.map(BluetoothAudioDeviceState::name)
        updateBluetoothName(connectedDevices.map(BluetoothAudioDeviceState::name))
        refreshPairedDevices()
    }

    @SuppressLint("MissingPermission")
    private fun safeGetConnectedDevices(profile: Int): List<BluetoothDevice> {
        if (!hasBluetoothConnectPermission()) return emptyList()
        return safeBluetoothCall(emptyList()) { bluetoothManager.getConnectedDevices(profile) }
    }

    private fun collectConnectedBluetoothDevices(): List<BluetoothAudioDeviceState> {
        val connectedDevices = linkedMapOf<String, BluetoothAudioDeviceState>()
        val localDeviceNames = resolveLocalBluetoothDeviceNames()
        val audioDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)

        for (device in audioDevices) {
            if (
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            ) {
                val name = device.productName?.toString()?.trim().orEmpty()
                if (name.isNotEmpty() && !isOwnBluetoothDeviceName(name, localDeviceNames)) {
                    val address = device.address.trim().orEmpty().takeIf { it.isNotEmpty() }
                    val key = bluetoothDeviceKey(address, name)
                    connectedDevices[key] = BluetoothAudioDeviceState(
                        name = name,
                        address = address,
                        isConnected = true
                    )
                }
            }
        }

        if (hasBluetoothConnectPermission()) {
            safeGetConnectedDevices(BluetoothProfile.A2DP)
                .mapNotNull { it.toBluetoothAudioDeviceState(isConnected = true) }
                .forEach { deviceState ->
                    val key = deviceState.uniqueKey()
                    connectedDevices[key] = connectedDevices[key]?.mergeWith(deviceState) ?: deviceState
                }

            safeGetConnectedDevices(BluetoothProfile.HEADSET)
                .mapNotNull { it.toBluetoothAudioDeviceState(isConnected = true) }
                .forEach { deviceState ->
                    val key = deviceState.uniqueKey()
                    connectedDevices[key] = connectedDevices[key]?.mergeWith(deviceState) ?: deviceState
                }
        }

        return connectedDevices.values.toList()
    }

    private fun refreshBluetoothAudioDevices() {
        // Found devices (audio and nearby) are kept across refreshes (only cleared when
        // Bluetooth turns off), so they don't vanish and reappear every rescan.
        // The nearby list is kept across refreshes (only cleared when Bluetooth turns off),
        // so devices don't vanish and reappear every rescan.
        updateAudioDevices()

        val adapter = bluetoothAdapter ?: return
        if (!canStartBluetoothDiscovery()) return

        if (isBluetoothDiscoveryActive(adapter)) {
            cancelBluetoothDiscovery(adapter)
        }
        startBluetoothDiscovery(adapter)
    }

    private fun sanitizeBluetoothAudioDeviceState(
        deviceState: BluetoothAudioDeviceState,
        localDeviceNames: Set<String> = resolveLocalBluetoothDeviceNames()
    ): BluetoothAudioDeviceState? {
        val normalizedName = deviceState.name.trim()
        if (normalizedName.isEmpty() || isOwnBluetoothDeviceName(normalizedName, localDeviceNames)) return null

        return deviceState.copy(
            name = normalizedName,
            address = deviceState.address?.trim()?.takeIf { it.isNotEmpty() },
            batteryPercent = deviceState.batteryPercent?.takeIf { it in 0..100 }
        )
    }

    private fun isOwnBluetoothDeviceName(
        name: String,
        localDeviceNames: Set<String> = resolveLocalBluetoothDeviceNames()
    ): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return true

        return localDeviceNames.any { it.equals(normalizedName, ignoreCase = true) }
    }

    private fun updateBluetoothEnabledState() {
        _isBluetoothEnabled.value = if (!hasBluetoothConnectPermission()) {
            false
        } else {
            readBluetoothEnabledState()
        }
    }

    private fun resolveLocalBluetoothAdapterName(): String? {
        if (!hasBluetoothConnectPermission()) return null
        return readLocalBluetoothAdapterName()
    }

    private fun resolveLocalBluetoothDeviceNames(): Set<String> {
        return buildSet {
            resolveLocalBluetoothAdapterName()?.let { add(it) }
            Build.MODEL.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothDiscoveryLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
            hasFineLocationPermission()
    }

    private fun canStartBluetoothDiscovery(): Boolean {
        return _isBluetoothEnabled.value &&
            hasBluetoothScanPermission() &&
            hasBluetoothDiscoveryLocationPermission()
    }

    private fun BluetoothAudioDeviceState.uniqueKey(): String {
        return bluetoothDeviceKey(address, name)
    }

    private fun BluetoothAudioDeviceState.mergeWith(other: BluetoothAudioDeviceState): BluetoothAudioDeviceState {
        return copy(
            name = if (name.isNotBlank()) name else other.name,
            address = address ?: other.address,
            isConnected = isConnected || other.isConnected,
            batteryPercent = batteryPercent ?: other.batteryPercent,
            majorClass = majorClass ?: other.majorClass,
            deviceClass = deviceClass ?: other.deviceClass
        )
    }

    private fun bluetoothDeviceKey(address: String?, name: String): String {
        return address?.takeIf { it.isNotBlank() } ?: "name:${name.lowercase()}"
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toBluetoothAudioDeviceState(isConnected: Boolean): BluetoothAudioDeviceState? {
        if (!hasBluetoothConnectPermission()) return null

        val deviceName = safeBluetoothCall("") { name?.trim().orEmpty() }
        val localDeviceNames = resolveLocalBluetoothDeviceNames()
        if (deviceName.isEmpty() || isOwnBluetoothDeviceName(deviceName, localDeviceNames)) return null

        val cls = safeBluetoothCall<BluetoothClass?>(null) { bluetoothClass }
        return BluetoothAudioDeviceState(
            name = deviceName,
            address = safeBluetoothCall("") { address?.trim().orEmpty() }
                .takeIf { it.isNotEmpty() },
            isConnected = isConnected,
            batteryPercent = resolveBatteryPercent(this),
            majorClass = cls?.majorDeviceClass,
            deviceClass = cls?.deviceClass
        )
    }

    private fun resolveBatteryPercent(device: BluetoothDevice): Int? {
        if (!hasBluetoothConnectPermission()) return null

        return runCatching {
            val batteryMethod = device.javaClass.methods.firstOrNull { method ->
                method.name == "getBatteryLevel" && method.parameterCount == 0
            } ?: device.javaClass.declaredMethods.firstOrNull { method ->
                method.name == "getBatteryLevel" && method.parameterCount == 0
            }

            batteryMethod
                ?.apply { isAccessible = true }
                ?.invoke(device) as? Int
        }.getOrNull()?.takeIf { it in 0..100 }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.isAudioOutputCandidate(): Boolean {
        if (!hasBluetoothConnectPermission()) return false

        val deviceClass = safeBluetoothCall<BluetoothClass?>(null) { bluetoothClass } ?: return false
        return deviceClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            deviceClass.hasService(BluetoothClass.Service.RENDER)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun readConnectedWifiSsid(): String? {
        if (!hasFineLocationPermission()) return null

        val info = wifiManager?.connectionInfo ?: return null
        if (info.supplicantState != android.net.wifi.SupplicantState.COMPLETED) return null

        var ssid = info.ssid
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return ssid
    }

    @SuppressLint("MissingPermission")
    private fun readBluetoothEnabledState(): Boolean {
        if (!hasBluetoothConnectPermission()) return false

        return safeBluetoothCall(false) { bluetoothAdapter?.isEnabled ?: false }
    }

    @SuppressLint("MissingPermission")
    private fun readLocalBluetoothAdapterName(): String? {
        if (!hasBluetoothConnectPermission()) return null

        return safeBluetoothCall("") { bluetoothAdapter?.name?.trim().orEmpty() }
            .takeIf { it.isNotEmpty() }
    }

    @SuppressLint("MissingPermission")
    private fun isBluetoothDiscoveryActive(adapter: BluetoothAdapter): Boolean {
        if (!hasBluetoothScanPermission()) return false

        return runCatching { adapter.isDiscovering }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun cancelBluetoothDiscovery(adapter: BluetoothAdapter) {
        if (!hasBluetoothScanPermission()) return

        runCatching { adapter.cancelDiscovery() }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery(adapter: BluetoothAdapter) {
        if (!hasBluetoothScanPermission()) return

        runCatching { adapter.startDiscovery() }
    }

    private inline fun <T> safeBluetoothCall(defaultValue: T, block: () -> T): T {
        return runCatching(block).getOrDefault(defaultValue)
    }

    @Suppress("DEPRECATION")
    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    /**
     * Cleanup resources. Should be called from ViewModel's onCleared.
     */
    fun onCleared() {
        stopProximityScan()
        networkCallback?.let { 
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        wifiStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        bluetoothStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        // Was never unregistered: every PlayerViewModel re-initialised this singleton and
        // added another callback, so each audio route event fanned out to all of them.
        audioDeviceCallback?.let {
            runCatching { audioManager.unregisterAudioDeviceCallback(it) }
        }
        audioDeviceCallback = null
        networkCallback = null
        wifiStateReceiver = null
        bluetoothStateReceiver = null
        if (hasBluetoothScanPermission()) {
            bluetoothAdapter?.takeIf { isBluetoothDiscoveryActive(it) }?.let {
                cancelBluetoothDiscovery(it)
            }
        }
        discoveredBluetoothAudioDevices.clear()
        _bluetoothAudioDeviceStates.value = emptyList()
        _bluetoothAudioDevices.value = emptyList()
        isInitialized = false
    }
}
