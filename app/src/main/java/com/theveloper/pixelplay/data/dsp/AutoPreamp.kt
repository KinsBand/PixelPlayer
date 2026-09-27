package com.theveloper.pixelplay.data.dsp

import kotlin.math.max
import kotlin.math.pow

/**
 * Headroom compensation and pre-amp gain processor.
 *
 * When an equalizer boosts any frequency band above 0 dB, digital clipping can occur on
 * modern mastered audio files that peak near 0 dBFS.
 *
 * Auto-Preamp dynamically attenuates the master digital pre-amp gain by exactly the amount
 * needed to guarantee headroom (offsetting positive boosts) without clipping.
 */
class AutoPreamp(
    var isAutoPreampEnabled: Boolean = true,
    var manualPreampDb: Float = 0f
) {
    companion object {
        const val SAFETY_MARGIN_DB = 0.5f // 0.5 dB extra headroom for inter-sample peaks
        const val MIN_PREAMP_DB = -20f
        const val MAX_PREAMP_DB = 6f

        /**
         * Calculates required headroom attenuation (in dB, <= 0) based on max band boost.
         */
        fun calculateAutoPreampDb(maxBoostDb: Float): Float {
            return if (maxBoostDb > 0f) {
                -(maxBoostDb + SAFETY_MARGIN_DB)
            } else {
                0f
            }.coerceIn(MIN_PREAMP_DB, 0f)
        }

        /**
         * Calculates max boost across a list of graphic EQ band levels (-15 to +15).
         */
        fun maxGraphicBoost(levels: List<Int>): Float {
            val maxLevel = levels.maxOrNull() ?: 0
            return max(0f, maxLevel.toFloat())
        }

        /**
         * Calculates max boost across a list of parametric bands.
         */
        fun maxParametricBoost(bands: List<ParametricBand>): Float {
            var maxBoost = 0f
            for (band in bands) {
                if (band.enabled && band.gainDb > maxBoost) {
                    maxBoost = band.gainDb
                }
            }
            return maxBoost
        }
    }

    private var currentLinearGain = 1.0f
    private var targetLinearGain = 1.0f
    private var calculatedEffectiveDb = 0f

    /**
     * Gets the effective pre-amp gain in dB currently being applied.
     */
    val effectivePreampDb: Float
        get() = calculatedEffectiveDb

    /**
     * Updates target preamp gain given current EQ max boost.
     */
    fun updatePreamp(maxBoostDb: Float): Float {
        val targetDb = if (isAutoPreampEnabled) {
            calculateAutoPreampDb(maxBoostDb)
        } else {
            manualPreampDb.coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB)
        }
        calculatedEffectiveDb = targetDb
        targetLinearGain = 10.0.pow((targetDb / 20.0).toDouble()).toFloat()
        return targetDb
    }

    /**
     * In-place buffer processing with smooth gain interpolation to prevent zipper noise.
     */
    fun processInterleaved(buffer: FloatArray, frameCount: Int, channels: Int) {
        val totalSamples = frameCount * channels
        if (totalSamples == 0) return

        // If linear gain is stationary, apply fast vector multiply
        if (kotlin.math.abs(currentLinearGain - targetLinearGain) < 0.0001f) {
            currentLinearGain = targetLinearGain
            if (kotlin.math.abs(currentLinearGain - 1.0f) > 0.001f) {
                for (i in 0 until totalSamples) {
                    buffer[i] *= currentLinearGain
                }
            }
            return
        }

        // Smooth gain ramp across frames
        val gainStep = (targetLinearGain - currentLinearGain) / totalSamples.toFloat()
        for (i in 0 until totalSamples) {
            currentLinearGain += gainStep
            buffer[i] *= currentLinearGain
        }
        currentLinearGain = targetLinearGain
    }

    fun reset() {
        currentLinearGain = targetLinearGain
    }
}
