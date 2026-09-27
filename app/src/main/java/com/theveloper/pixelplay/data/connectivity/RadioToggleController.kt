package com.theveloper.pixelplay.data.connectivity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import com.theveloper.pixelplay.data.recognition.shizuku.ShizukuManager
import com.theveloper.pixelplay.data.recognition.shizuku.ShizukuStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when a radio toggle was tapped. */
sealed interface RadioToggleResult {
    /** The radio was switched directly. */
    data object Done : RadioToggleResult

    /**
     * Android doesn't let this app flip the switch itself: open this in-app system panel
     * (Wi-Fi / Internet panel, or the "turn on Bluetooth?" prompt) so the user can do it
     * without leaving PixelPlayer.
     */
    data class OpenPanel(val intent: Intent) : RadioToggleResult

    /** Shizuku is installed but PixelPlayer isn't allowed yet; the permission prompt was shown. */
    data object ShizukuPermissionRequested : RadioToggleResult
}

/**
 * Turns Wi-Fi, mobile data and Bluetooth on/off for real.
 *
 * Since Android 10 (Wi-Fi), 13 (Bluetooth) and always (mobile data) normal apps are not
 * allowed to switch these radios. The only way to do it without root is Shizuku, which
 * PixelPlayer already supports: with Shizuku running and allowed, the toggle runs the same
 * `svc` / `cmd` commands the system quick settings use. Without it, the best Android allows is
 * the older direct APIs on older versions, then the system's in-app panel.
 */
@Singleton
class RadioToggleController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizuku: ShizukuManager
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    val shizukuStatus: StateFlow<ShizukuStatus> get() = shizuku.status

    /** True when a tap switches radios directly (Shizuku ready). */
    fun canToggleDirectly(): Boolean {
        shizuku.refreshStatus()
        return shizuku.status.value == ShizukuStatus.READY
    }

    val hasMobileData: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private val _mobileDataEnabled = MutableStateFlow(readMobileDataEnabled())
    val mobileDataEnabled: StateFlow<Boolean> = _mobileDataEnabled.asStateFlow()

    init {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                _mobileDataEnabled.value = readMobileDataEnabled()
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(MOBILE_DATA), true, observer)
            defaultDataSubId()?.let { sub ->
                context.contentResolver.registerContentObserver(Settings.Global.getUriFor("$MOBILE_DATA$sub"), false, observer)
            }
        }
    }

    fun refreshMobileData() {
        _mobileDataEnabled.value = readMobileDataEnabled()
    }

    // ── Wi-Fi ──────────────────────────────────────────────────────────────────────────

    suspend fun setWifi(enable: Boolean): RadioToggleResult {
        shellToggle("svc wifi ${onOff(enable)}")?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val ok = runCatching { wifiManager?.setWifiEnabled(enable) == true }.getOrDefault(false)
            if (ok) return RadioToggleResult.Done
        }
        return RadioToggleResult.OpenPanel(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
            else Intent(Settings.ACTION_WIFI_SETTINGS)
        )
    }

    // ── Mobile data ────────────────────────────────────────────────────────────────────

    suspend fun setMobileData(enable: Boolean): RadioToggleResult {
        shellToggle("svc data ${onOff(enable)}")?.let {
            if (it == RadioToggleResult.Done) refreshMobileData()
            return it
        }
        return RadioToggleResult.OpenPanel(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        )
    }

    // ── Bluetooth ──────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun setBluetooth(enable: Boolean): RadioToggleResult {
        // `cmd bluetooth_manager` exists on Android 13+, `svc bluetooth` on older builds.
        shellToggle("cmd bluetooth_manager ${onOff(enable)} || svc bluetooth ${onOff(enable)}")?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            val ok = runCatching {
                if (enable) bluetoothAdapter?.enable() == true else bluetoothAdapter?.disable() == true
            }.getOrDefault(false)
            if (ok) return RadioToggleResult.Done
        }
        return RadioToggleResult.OpenPanel(
            // Turning on: the system "Allow PixelPlayer to turn on Bluetooth?" prompt, in place.
            // Turning off has no public prompt, so the Bluetooth page it is.
            if (enable) Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            else Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )
    }

    // ── Output switcher ────────────────────────────────────────────────────────────────

    /**
     * The system media output switcher (pick a Bluetooth speaker / headphones / cast device
     * and connect for real). Returns false when this Android version doesn't have one.
     */
    fun showOutputSwitcher(): Boolean {
        if (Build.VERSION.SDK_INT >= 34) {
            val shown = runCatching {
                android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher()
            }.getOrDefault(false)
            if (shown) return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching {
                context.sendBroadcast(
                    Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                        .setPackage("com.android.systemui")
                        .putExtra("package_name", context.packageName)
                )
                true
            }.getOrDefault(false)
        }
        return false
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────

    /** Runs [command] through Shizuku. Null = Shizuku not installed/running, try the next way. */
    private suspend fun shellToggle(command: String): RadioToggleResult? {
        shizuku.refreshStatus()
        return when (shizuku.status.value) {
            ShizukuStatus.READY -> {
                val result = shizuku.executeShell(command)
                if (result.isSuccess) RadioToggleResult.Done
                else {
                    Timber.w(result.exceptionOrNull(), "Radio toggle via Shizuku failed: %s", command)
                    null
                }
            }
            ShizukuStatus.NEEDS_PERMISSION -> {
                shizuku.requestPermission()
                RadioToggleResult.ShizukuPermissionRequested
            }
            ShizukuStatus.UNAVAILABLE -> null
        }
    }

    private fun onOff(enable: Boolean) = if (enable) "enable" else "disable"

    private fun defaultDataSubId(): Int? = runCatching {
        SubscriptionManager.getDefaultDataSubscriptionId().takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
    }.getOrNull()

    private fun readMobileDataEnabled(): Boolean = runCatching {
        val resolver = context.contentResolver
        val perSim = defaultDataSubId()?.let { Settings.Global.getInt(resolver, "$MOBILE_DATA$it", -1) } ?: -1
        if (perSim >= 0) perSim == 1 else Settings.Global.getInt(resolver, MOBILE_DATA, 0) == 1
    }.getOrDefault(false)

    private companion object {
        const val MOBILE_DATA = "mobile_data"
    }
}
