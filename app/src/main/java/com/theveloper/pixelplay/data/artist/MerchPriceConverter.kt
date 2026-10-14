package com.theveloper.pixelplay.data.artist

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utility for parsing and converting international merchandise price strings into AUD.
 *
 * Handles price strings from diverse merch providers (Shopify, Bandcamp, Spotify Merch,
 * Warner/Universal stores, etc.) and formats them into clean, rounded AUD prices without cents clutter.
 */
object MerchPriceConverter {

    /** Approximate exchange rates relative to AUD. */
    const val RATE_AUD: Double = 1.00
    const val RATE_GBP_TO_AUD: Double = 1.95
    const val RATE_EUR_TO_AUD: Double = 1.65
    const val RATE_CAD_TO_AUD: Double = 1.12
    const val RATE_USD_TO_AUD: Double = 1.55

    /** Offline fallback (AUD per unit), replaced by live rates from [ExchangeRateRepository]. */
    val FALLBACK_RATES: Map<String, Double> = mapOf(
        "AUD" to RATE_AUD,
        "USD" to RATE_USD_TO_AUD,
        "GBP" to RATE_GBP_TO_AUD,
        "EUR" to RATE_EUR_TO_AUD,
        "CAD" to RATE_CAD_TO_AUD,
        "NZD" to 0.91,
        "JPY" to 0.0102,
    )

    /**
     * [rawPrice] in whole Australian dollars, "$X AUD", using [rates] (AUD per unit, keyed by
     * currency code). Null for a blank price; the raw text when no amount can be read.
     */
    fun formatAudPrice(rawPrice: String?, rates: Map<String, Double>): String? {
        if (rawPrice.isNullOrBlank()) return null
        val trimmed = rawPrice.trim()
        val amount = parseAmount(trimmed) ?: return trimmed
        val code = detectCurrency(trimmed)
        val rate = rates[code] ?: FALLBACK_RATES[code] ?: RATE_USD_TO_AUD
        return "\$${(amount * rate).roundToInt()} AUD"
    }

    /** ISO code of the currency in a price string (USD when only a bare "$" is there). */
    fun detectCurrency(rawPrice: String): String {
        val upper = rawPrice.uppercase(Locale.ROOT)
        return when {
            upper.contains("CAD") || upper.contains("CA$") || upper.contains("C$") -> "CAD"
            upper.contains("NZD") || upper.contains("NZ$") -> "NZD"
            upper.contains("AUD") || upper.contains("A$") -> "AUD"
            upper.contains("£") || upper.contains("GBP") -> "GBP"
            upper.contains("€") || upper.contains("EUR") -> "EUR"
            upper.contains("¥") || upper.contains("JPY") -> "JPY"
            else -> "USD"
        }
    }

    /**
     * Converts raw price strings from merchandise services (e.g. "$35.00", "USD 35.00",
     * "£25.00", "€30.00", "$35.00 USD", "AUD 45.00", "$45.00 AUD", "45 AUD", etc.)
     * to a standardized Australian Dollar representation formatted as "$X AUD" (rounded to whole dollar).
     *
     * Currency detection hierarchy:
     * - If contains "CAD" / "CA$": conversion rate ~ 1.12 AUD per CAD.
     * - If contains "AUD" or "A$": already AUD, format as "$X AUD".
     * - If contains "£" or "GBP": conversion rate ~ 1.95 AUD per GBP.
     * - If contains "€" or "EUR": conversion rate ~ 1.65 AUD per EUR.
     * - If contains "$" or "USD" or default: conversion rate ~ 1.55 AUD per USD.
     *
     * @param rawPrice Raw price string from merch metadata, or null.
     * @return Formatted string like "$54 AUD", or null if [rawPrice] is null/blank,
     *         or [rawPrice] if unable to parse numeric amount.
     */
    fun formatAudPrice(rawPrice: String?): String? {
        if (rawPrice.isNullOrBlank()) return null

        val trimmed = rawPrice.trim()
        val numericAmount = parseAmount(trimmed) ?: return trimmed

        val rate = detectConversionRate(trimmed)
        val audAmount = (numericAmount * rate).roundToInt()

        return "\$$audAmount AUD"
    }

    /**
     * Parses numeric amount from a raw price string, stripping currency symbols
     * and handling thousands separators and decimal markers.
     */
    fun parseAmount(rawPrice: String?): Double? {
        if (rawPrice.isNullOrBlank()) return null

        // Match numeric sequences with optional thousands/decimals
        val match = Regex("""(?:\d{1,3}(?:[,\s]\d{3})+|\d+)(?:[.,]\d+)?""").find(rawPrice)
            ?: Regex("""\d+""").find(rawPrice)
            ?: return null

        val rawNum = match.value.replace(" ", "")

        val normalized = when {
            rawNum.contains(",") && rawNum.contains(".") -> {
                if (rawNum.lastIndexOf(",") > rawNum.lastIndexOf(".")) {
                    // European style: 1.250,50 -> 1250.50
                    rawNum.replace(".", "").replace(",", ".")
                } else {
                    // Standard style: 1,250.50 -> 1250.50
                    rawNum.replace(",", "")
                }
            }
            rawNum.contains(",") -> {
                val parts = rawNum.split(",")
                if (parts.size == 2 && parts[1].length <= 2) {
                    // Decimal comma: 35,00 -> 35.00
                    rawNum.replace(",", ".")
                } else {
                    // Thousands comma: 1,000 -> 1000
                    rawNum.replace(",", "")
                }
            }
            else -> rawNum
        }

        return normalized.toDoubleOrNull()
    }

    /**
     * Detects currency and returns conversion rate to AUD.
     */
    fun detectConversionRate(rawPrice: String): Double {
        val upper = rawPrice.uppercase(Locale.ROOT)
        return when {
            // Check CAD before AUD/USD to avoid "CA$" matching "A$" or "$"
            upper.contains("CAD") || upper.contains("CA$") || upper.contains("C$") -> RATE_CAD_TO_AUD
            upper.contains("AUD") || upper.contains("A$") -> RATE_AUD
            upper.contains("£") || upper.contains("\u00A3") || upper.contains("GBP") -> RATE_GBP_TO_AUD
            upper.contains("€") || upper.contains("\u20AC") || upper.contains("EUR") -> RATE_EUR_TO_AUD
            upper.contains("USD") || upper.contains("US$") || upper.contains("$") -> RATE_USD_TO_AUD
            else -> RATE_USD_TO_AUD
        }
    }
}
