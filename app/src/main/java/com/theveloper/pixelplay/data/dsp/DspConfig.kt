package com.theveloper.pixelplay.data.dsp

/**
 * Immutable snapshot of DSP parameters for atomic, thread-safe consumption by [DspAudioProcessor].
 */
data class DspConfig(
    val isEnabled: Boolean = false,
    val preampLinearGain: Float = 1.0f,
    val effectivePreampDb: Float = 0f,
    val eqCoefficients: List<BiquadCoefficients> = emptyList(),
    val subsonicEnabled: Boolean = false,
    val subsonicCutoffHz: Float = 25f,
    val ultrasonicEnabled: Boolean = false,
    val ultrasonicCutoffHz: Float = 20000f,
    val crossfeedStrength: CrossfeedStrength = CrossfeedStrength.OFF,
    val limiterEnabled: Boolean = true,
    val softSaturationEnabled: Boolean = true,
    val loudnessCompGain: Float = 1.0f
) {
    companion object {
        val BYPASS = DspConfig()
    }
}
