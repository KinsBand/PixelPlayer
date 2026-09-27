package com.theveloper.pixelplay.data.dsp

/**
 * Subsonic low-cut rumble filter.
 *
 * Cuts frequencies below human hearing (e.g. 20-40 Hz) that waste amplifier headroom,
 * heat voice coils, and cause mechanical speaker/headphone excursion distortion.
 */
class SubsonicFilter(
    var isEnabled: Boolean = false,
    var cutoffHz: Float = 25f
) {
    private val filter = BiquadFilter()
    private var sampleRate: Float = 44100f

    fun setSampleRate(rate: Float) {
        if (rate <= 0f) return
        sampleRate = rate
        updateFilter()
    }

    fun setCutoff(cutoff: Float) {
        cutoffHz = cutoff.coerceIn(15f, 60f)
        updateFilter()
    }

    fun setFilterEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) filter.reset()
    }

    private fun updateFilter() {
        if (!isEnabled || sampleRate <= 0f) {
            filter.coefficients = BiquadCoefficients.BYPASS
            return
        }
        filter.coefficients = BiquadCoefficients.calculateHighPass(
            sampleRate = sampleRate,
            frequency = cutoffHz,
            q = 0.7071f // 2nd-order Butterworth maximally flat
        )
    }

    fun processFrame(left: Float, right: Float): Pair<Float, Float> {
        if (!isEnabled) return Pair(left, right)
        return Pair(filter.processLeft(left), filter.processRight(right))
    }

    fun processInterleaved(buffer: FloatArray, frameCount: Int, channels: Int) {
        if (!isEnabled) return
        if (channels == 2) {
            for (i in 0 until frameCount) {
                val idx = i * 2
                buffer[idx] = filter.processLeft(buffer[idx])
                buffer[idx + 1] = filter.processRight(buffer[idx + 1])
            }
        } else {
            for (i in 0 until frameCount) {
                buffer[i] = filter.processLeft(buffer[i])
            }
        }
    }

    fun reset() {
        filter.reset()
    }
}

/**
 * Ultrasonic high-cut smoothing filter.
 *
 * Removes ultrasonic noise, intermodulation hash, and delta-sigma DAC noise shaping
 * above 18-22 kHz without altering audible spectrum.
 */
class UltrasonicFilter(
    var isEnabled: Boolean = false,
    var cutoffHz: Float = 20000f
) {
    private val filter = BiquadFilter()
    private var sampleRate: Float = 44100f

    fun setSampleRate(rate: Float) {
        if (rate <= 0f) return
        sampleRate = rate
        updateFilter()
    }

    fun setCutoff(cutoff: Float) {
        cutoffHz = cutoff.coerceIn(16000f, 30000f)
        updateFilter()
    }

    fun setFilterEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) filter.reset()
    }

    private fun updateFilter() {
        if (!isEnabled || sampleRate <= 0f) {
            filter.coefficients = BiquadCoefficients.BYPASS
            return
        }
        filter.coefficients = BiquadCoefficients.calculateLowPass(
            sampleRate = sampleRate,
            frequency = cutoffHz,
            q = 0.7071f // 2nd-order Butterworth maximally flat
        )
    }

    fun processFrame(left: Float, right: Float): Pair<Float, Float> {
        if (!isEnabled) return Pair(left, right)
        return Pair(filter.processLeft(left), filter.processRight(right))
    }

    fun processInterleaved(buffer: FloatArray, frameCount: Int, channels: Int) {
        if (!isEnabled) return
        if (channels == 2) {
            for (i in 0 until frameCount) {
                val idx = i * 2
                buffer[idx] = filter.processLeft(buffer[idx])
                buffer[idx + 1] = filter.processRight(buffer[idx + 1])
            }
        } else {
            for (i in 0 until frameCount) {
                buffer[i] = filter.processLeft(buffer[i])
            }
        }
    }

    fun reset() {
        filter.reset()
    }
}
