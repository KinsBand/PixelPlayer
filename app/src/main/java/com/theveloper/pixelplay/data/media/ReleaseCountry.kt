package com.theveloper.pixelplay.data.media

import java.util.Locale

/**
 * Normalizes the release-country tag written by taggers into a stable ISO-3166
 * alpha-2 code, and renders that code back as a human-readable name.
 *
 * Taggers are inconsistent here: MusicBrainz Picard writes alpha-2 codes
 * ("AU", "GB", "XW"), while hand-tagged files and some rippers write full
 * names ("Australia", "United Kingdom") or the odd alpha-3 ("AUS", "GBR").
 * All three are folded to the alpha-2 code so the Home filter groups them as
 * one bucket instead of three.
 */
object ReleaseCountry {

    /** MusicBrainz pseudo-territories that are not real ISO countries. */
    private const val WORLDWIDE = "XW"
    private const val EUROPE = "XE"

    private val isoCodes: Set<String> by lazy {
        Locale.getISOCountries().toSet()
    }

    /** Lowercased English display name -> alpha-2 code, e.g. "australia" -> "AU". */
    private val nameToCode: Map<String, String> by lazy {
        buildMap {
            Locale.getISOCountries().forEach { code ->
                val locale = Locale.Builder().setRegion(code).build()
                val english = locale.getDisplayCountry(Locale.ENGLISH)
                if (english.isNotBlank()) put(english.lowercase(Locale.ROOT), code)
                val device = locale.displayCountry
                if (device.isNotBlank()) put(device.lowercase(Locale.ROOT), code)
            }
            // Common variants taggers write that don't match a display name exactly.
            put("uk", "GB")
            put("great britain", "GB")
            put("england", "GB")
            put("usa", "US")
            put("u.s.a.", "US")
            put("united states of america", "US")
            put("south korea", "KR")
            put("russia", "RU")
            put("worldwide", WORLDWIDE)
            put("europe", EUROPE)
        }
    }

    /** Alpha-3 -> alpha-2, built from the JVM's own ISO tables. */
    private val alpha3ToAlpha2: Map<String, String> by lazy {
        buildMap {
            Locale.getISOCountries().forEach { code ->
                val iso3 = try {
                    Locale.Builder().setRegion(code).build().isO3Country
                } catch (_: Exception) {
                    ""
                }
                if (iso3.isNotBlank()) put(iso3.uppercase(Locale.ROOT), code)
            }
        }
    }

    /**
     * Returns the alpha-2 code for a raw tag value, or null when it can't be
     * resolved to a real territory.
     */
    fun normalize(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        // Some taggers write multiple values, e.g. "GB; US" or "GB/US". Take the first.
        val first = value.split(';', '/', ',').first().trim().takeIf { it.isNotEmpty() } ?: return null
        val upper = first.uppercase(Locale.ROOT)

        return when {
            upper == WORLDWIDE || upper == EUROPE -> upper
            upper.length == 2 && upper in isoCodes -> upper
            upper.length == 3 -> alpha3ToAlpha2[upper]
            else -> nameToCode[first.lowercase(Locale.ROOT)]
        }
    }

    /**
     * Human-readable label for a normalized code — what the Country filter
     * button shows. Falls back to the code itself for unknown territories.
     */
    fun displayName(code: String?): String? {
        val value = code?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return when (value) {
            WORLDWIDE -> "Worldwide"
            EUROPE -> "Europe"
            else -> Locale.Builder().setRegion(value).build()
                .displayCountry
                .takeIf { it.isNotBlank() && it != value }
                ?: value
        }
    }
}
