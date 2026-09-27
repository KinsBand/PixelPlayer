package com.theveloper.pixelplay.data.dsp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow

/**
 * Singleton manager for PixelPlayer's Audiophile 32-bit Float DSP Engine.
 *
 * Coordinates real-time configuration between Compose UI, ViewModel, and Media3 [DspAudioProcessor].
 */
@Singleton
class DspEngineManager @Inject constructor() {

    private val atomicConfig = AtomicReference(DspConfig())

    private val _configFlow = MutableStateFlow(DspConfig())
    val configFlow: StateFlow<DspConfig> = _configFlow.asStateFlow()

    // Engine settings
    private var isEngineEnabled: Boolean = true
    private var isAutoPreampEnabled: Boolean = true
    private var manualPreampDb: Float = 0f
    private var sampleRate: Float = 48000f

    // Filters and bands
    private var isParametricMode: Boolean = false
    private var graphicBands: List<Int> = List(10) { 0 }
    private var parametricBands: List<ParametricBand> = ParametricBand.fromGraphicLevels(graphicBands)

    private var subsonicEnabled: Boolean = false
    private var subsonicCutoffHz: Float = 25f
    private var ultrasonicEnabled: Boolean = false
    private var ultrasonicCutoffHz: Float = 20000f

    private var crossfeedStrength: CrossfeedStrength = CrossfeedStrength.OFF
    private var limiterEnabled: Boolean = true
    private var softSaturationEnabled: Boolean = true
    private var auditionLoudnessMultiplier: Float = 1.0f

    init {
        recalculateAndPublish()
    }

    /**
     * Factory method creating a new [DspAudioProcessor] instance bound to this manager.
     */
    fun createAudioProcessor(): DspAudioProcessor {
        return DspAudioProcessor { atomicConfig.get() }
    }

    fun setEngineEnabled(enabled: Boolean) {
        isEngineEnabled = enabled
        recalculateAndPublish()
    }

    fun setAutoPreampEnabled(enabled: Boolean) {
        isAutoPreampEnabled = enabled
        recalculateAndPublish()
    }

    fun setManualPreampDb(gainDb: Float) {
        manualPreampDb = gainDb.coerceIn(AutoPreamp.MIN_PREAMP_DB, AutoPreamp.MAX_PREAMP_DB)
        recalculateAndPublish()
    }

    fun setGraphicBands(bands: List<Int>) {
        graphicBands = bands
        if (!isParametricMode) {
            parametricBands = ParametricBand.fromGraphicLevels(bands)
        }
        recalculateAndPublish()
    }

    fun setParametricBands(bands: List<ParametricBand>) {
        parametricBands = bands
        recalculateAndPublish()
    }

    fun setParametricMode(enabled: Boolean) {
        isParametricMode = enabled
        if (enabled && parametricBands.isEmpty()) {
            parametricBands = ParametricBand.fromGraphicLevels(graphicBands)
        }
        recalculateAndPublish()
    }

    fun setSubsonicFilter(enabled: Boolean, cutoffHz: Float) {
        subsonicEnabled = enabled
        subsonicCutoffHz = cutoffHz.coerceIn(15f, 60f)
        recalculateAndPublish()
    }

    fun setUltrasonicFilter(enabled: Boolean, cutoffHz: Float) {
        ultrasonicEnabled = enabled
        ultrasonicCutoffHz = cutoffHz.coerceIn(16000f, 30000f)
        recalculateAndPublish()
    }

    fun setCrossfeedStrength(strength: CrossfeedStrength) {
        crossfeedStrength = strength
        recalculateAndPublish()
    }

    fun setLimiterSettings(limiter: Boolean, softSaturation: Boolean) {
        limiterEnabled = limiter
        softSaturationEnabled = softSaturation
        recalculateAndPublish()
    }

    fun setAuditionLoudnessMultiplier(multiplier: Float) {
        auditionLoudnessMultiplier = multiplier.coerceIn(0.25f, 4.0f)
        recalculateAndPublish()
    }

    fun setSampleRate(rate: Float) {
        if (rate > 0f) {
            sampleRate = rate
            recalculateAndPublish()
        }
    }

    private fun recalculateAndPublish() {
        // 1. Calculate max boost
        val maxBoostDb = if (isParametricMode) {
            AutoPreamp.maxParametricBoost(parametricBands)
        } else {
            AutoPreamp.maxGraphicBoost(graphicBands)
        }

        // 2. Preamp headroom calculation
        val effectivePreampDb = if (isAutoPreampEnabled) {
            AutoPreamp.calculateAutoPreampDb(maxBoostDb)
        } else {
            manualPreampDb
        }
        val preampLinear = 10.0.pow((effectivePreampDb / 20.0).toDouble()).toFloat()

        // 3. Compute Biquad Coefficients for active EQ mode
        val coefficients = mutableListOf<BiquadCoefficients>()
        if (isParametricMode) {
            for (band in parametricBands) {
                if (band.enabled && kotlin.math.abs(band.gainDb) > 0.01f) {
                    coefficients.add(
                        BiquadCoefficients.create(
                            type = band.type,
                            sampleRate = sampleRate,
                            frequency = band.frequency,
                            gainDb = band.gainDb,
                            q = band.q
                        )
                    )
                }
            }
        } else {
            val count = minOf(graphicBands.size, ParametricBand.DEFAULT_GRAPHIC_FREQUENCIES.size)
            for (i in 0 until count) {
                val gain = graphicBands[i].toFloat()
                if (kotlin.math.abs(gain) > 0.01f) {
                    val freq = ParametricBand.DEFAULT_GRAPHIC_FREQUENCIES[i]
                    val type = when (i) {
                        0 -> FilterType.LOW_SHELF
                        count - 1 -> FilterType.HIGH_SHELF
                        else -> FilterType.PEAKING
                    }
                    coefficients.add(
                        BiquadCoefficients.create(
                            type = type,
                            sampleRate = sampleRate,
                            frequency = freq,
                            gainDb = gain,
                            q = 1.414f
                        )
                    )
                }
            }
        }

        val snapshot = DspConfig(
            isEnabled = isEngineEnabled,
            preampLinearGain = preampLinear,
            effectivePreampDb = effectivePreampDb,
            eqCoefficients = coefficients,
            subsonicEnabled = subsonicEnabled,
            subsonicCutoffHz = subsonicCutoffHz,
            ultrasonicEnabled = ultrasonicEnabled,
            ultrasonicCutoffHz = ultrasonicCutoffHz,
            crossfeedStrength = crossfeedStrength,
            limiterEnabled = limiterEnabled,
            softSaturationEnabled = softSaturationEnabled,
            loudnessCompGain = auditionLoudnessMultiplier
        )

        atomicConfig.set(snapshot)
        _configFlow.value = snapshot
    }

    /**
     * Calculates the overall frequency response curve in dB across [points] logarithmic frequency samples.
     * Evaluates all active filters (EQ, subsonic, ultrasonic) plus preamp gain.
     */
    fun calculateFrequencyResponse(points: Int = 100): List<Pair<Float, Float>> {
        val config = atomicConfig.get()
        val result = ArrayList<Pair<Float, Float>>(points)
        val minFreq = 20f
        val maxFreq = 20000f
        val logMin = log10(minFreq)
        val logMax = log10(maxFreq)

        // Precompute cleanup filter coefficients
        val subCoeff = if (config.subsonicEnabled) {
            BiquadCoefficients.calculateHighPass(sampleRate, config.subsonicCutoffHz, 0.7071f)
        } else null

        val ultraCoeff = if (config.ultrasonicEnabled) {
            BiquadCoefficients.calculateLowPass(sampleRate, config.ultrasonicCutoffHz, 0.7071f)
        } else null

        for (p in 0 until points) {
            val logF = logMin + (p.toFloat() / (points - 1)) * (logMax - logMin)
            val freq = 10.0.pow(logF.toDouble()).toFloat()

            var totalDb = if (config.isEnabled) config.effectivePreampDb else 0f

            if (config.isEnabled) {
                for (coeff in config.eqCoefficients) {
                    totalDb += coeff.magnitudeResponseDb(freq, sampleRate)
                }
                subCoeff?.let { totalDb += it.magnitudeResponseDb(freq, sampleRate) }
                ultraCoeff?.let { totalDb += it.magnitudeResponseDb(freq, sampleRate) }
            }

            result.add(Pair(freq, totalDb.coerceIn(-30f, 30f)))
        }

        return result
    }
}
