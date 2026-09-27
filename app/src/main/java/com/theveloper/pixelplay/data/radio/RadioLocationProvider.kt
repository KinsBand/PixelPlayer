package com.theveloper.pixelplay.data.radio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Works out where "local" is for the radio.
 *
 * With coarse location granted: last known (or a fresh network) fix, reverse-geocoded to a
 * state/territory. Without it: the mobile network's country, then the SIM's, then the locale's,
 * so REGION/COUNTRY still work and only LOCAL asks for permission.
 */
@Singleton
class RadioLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun resolveHome(): RadioHome {
        val location = if (hasLocationPermission()) currentLocation() else null
        if (location != null) {
            val geocoded = reverseGeocode(location.latitude, location.longitude)
            val cc = geocoded?.countryCode ?: fallbackCountryCode()
            return RadioHome(
                countryCode = cc,
                countryName = geocoded?.countryName ?: countryName(cc),
                state = geocoded?.adminArea,
                city = geocoded?.locality ?: geocoded?.subAdminArea,
                lat = location.latitude,
                lon = location.longitude,
            )
        }
        val cc = fallbackCountryCode()
        return RadioHome(countryCode = cc, countryName = countryName(cc))
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? = withContext(Dispatchers.IO) {
        val lm = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val lastKnown = providers
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        // A city-level fix from the last hour is plenty for "stations near you".
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 60 * 60 * 1000L) {
            return@withContext lastKnown
        }
        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        } ?: return@withContext lastKnown
        val fresh = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        fresh ?: lastKnown
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(lat: Double, lon: Double): android.location.Address? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching { Geocoder(context, Locale.ENGLISH).getFromLocation(lat, lon, 1)?.firstOrNull() }
                .onFailure { Timber.tag("RadioLocation").d(it, "reverse geocode failed") }
                .getOrNull()
        }

    private fun fallbackCountryCode(): String? {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return listOf(
            runCatching { tm?.networkCountryIso }.getOrNull(),
            runCatching { tm?.simCountryIso }.getOrNull(),
            Locale.getDefault().country,
        ).firstOrNull { !it.isNullOrBlank() && it.length == 2 }?.uppercase()
    }

    private fun countryName(cc: String?): String? =
        cc?.let {
            runCatching { Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH) }
                .getOrNull()
                ?.takeIf { n -> n.isNotBlank() && n != it }
        }
}
