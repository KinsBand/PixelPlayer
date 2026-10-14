package com.theveloper.pixelplay.data.artist

import android.content.Context
import com.google.gson.JsonParser
import com.theveloper.pixelplay.data.network.NetworkAccessPolicy
import com.theveloper.pixelplay.data.network.NetworkDecision
import com.theveloper.pixelplay.data.network.NetworkPurpose
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live AUD exchange rates for merch prices, fetched at most once a day from Frankfurter
 * (European Central Bank reference rates; keyless, no account) and kept on the phone.
 * [rates] maps a currency code to how many AUD one unit is worth. Until a fetch succeeds,
 * offline, or with enrichment turned off it holds [MerchPriceConverter.FALLBACK_RATES].
 */
@Singleton
class ExchangeRateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val networkPolicy: NetworkAccessPolicy,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val _rates = MutableStateFlow(MerchPriceConverter.FALLBACK_RATES)
    val rates: StateFlow<Map<String, Double>> = _rates.asStateFlow()
    private val mutex = Mutex()
    private var loadedFromDisk = false

    /** Loads the saved rates and refreshes them when they're more than a day old. Cheap to call often. */
    suspend fun refreshIfStale() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loadedFromDisk) {
                loadedFromDisk = true
                prefs.getString(KEY_RATES, null)?.let(::parseSaved)?.let { _rates.value = MerchPriceConverter.FALLBACK_RATES + it }
            }
            val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
            if (System.currentTimeMillis() - fetchedAt < MAX_AGE_MS) return@withLock
            if (networkPolicy.getDecision(NetworkPurpose.Enrichment) != NetworkDecision.Allowed) return@withLock
            try {
                val request = Request.Builder().url(URL).get().build()
                val body = okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withLock
                    response.body?.string()
                } ?: return@withLock
                val perAud = JsonParser.parseString(body).asJsonObject.getAsJsonObject("rates")
                    ?.entrySet()
                    ?.mapNotNull { (code, value) -> value.asDouble.takeIf { it > 0.0 }?.let { code to 1.0 / it } }
                    ?.toMap()
                    .orEmpty()
                if (perAud.isEmpty()) return@withLock
                _rates.value = MerchPriceConverter.FALLBACK_RATES + perAud
                prefs.edit()
                    .putString(KEY_RATES, perAud.entries.joinToString(";") { "${it.key}=${it.value}" })
                    .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                    .apply()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.d("Exchange rates not refreshed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun parseSaved(saved: String): Map<String, Double> =
        saved.split(';').mapNotNull { part ->
            val code = part.substringBefore('=', "")
            val value = part.substringAfter('=', "").toDoubleOrNull()
            if (code.length == 3 && value != null && value > 0.0) code to value else null
        }.toMap()

    private companion object {
        const val PREFS = "merch_exchange_rates"
        const val KEY_RATES = "aud_per_unit"
        const val KEY_FETCHED_AT = "fetched_at"
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        const val URL = "https://api.frankfurter.app/latest?from=AUD&to=USD,GBP,EUR,CAD,NZD,JPY"
    }
}
