package com.theveloper.pixelplay.data.dsp

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Represents a single band in a Parametric Equalizer (PEQ).
 */
@Serializable
@Immutable
data class ParametricBand(
    val id: Int,
    val enabled: Boolean = true,
    val type: FilterType = FilterType.PEAKING,
    val frequency: Float, // 20 Hz to 20,000 Hz
    val gainDb: Float,    // -15 dB to +15 dB
    val q: Float = 1.414f // 0.1 to 10.0 (bandwidth Q factor)
) {
    companion object {
        // Standard ISO 10-band frequencies
        val DEFAULT_GRAPHIC_FREQUENCIES = listOf(
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )

        /**
         * Converts a list of 10 graphic EQ band levels (-15 to +15 dB) to 10 parametric bands.
         */
        fun fromGraphicLevels(levels: List<Int>): List<ParametricBand> {
            val count = minOf(levels.size, DEFAULT_GRAPHIC_FREQUENCIES.size)
            return (0 until count).map { index ->
                ParametricBand(
                    id = index,
                    enabled = true,
                    type = when (index) {
                        0 -> FilterType.LOW_SHELF
                        count - 1 -> FilterType.HIGH_SHELF
                        else -> FilterType.PEAKING
                    },
                    frequency = DEFAULT_GRAPHIC_FREQUENCIES[index],
                    gainDb = levels[index].toFloat(),
                    q = 1.414f
                )
            }
        }

        /**
         * Default flat 5-band PEQ setup for audiophile custom tuning.
         */
        fun defaultAudiophileBands(): List<ParametricBand> {
            return listOf(
                ParametricBand(0, true, FilterType.LOW_SHELF, 80f, 0f, 0.707f),
                ParametricBand(1, true, FilterType.PEAKING, 250f, 0f, 1.414f),
                ParametricBand(2, true, FilterType.PEAKING, 1000f, 0f, 1.414f),
                ParametricBand(3, true, FilterType.PEAKING, 4000f, 0f, 1.414f),
                ParametricBand(4, true, FilterType.HIGH_SHELF, 10000f, 0f, 0.707f)
            )
        }
    }
}
