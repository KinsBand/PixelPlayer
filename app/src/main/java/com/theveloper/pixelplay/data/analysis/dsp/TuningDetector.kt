package com.theveloper.pixelplay.data.analysis.dsp

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Estimates overall A4 reference tuning frequency (default standard = 440.0 Hz).
 * Analyzes spectral energy peaks around musical pitch centers and measures fractional deviation.
 */
object TuningDetector {

    private const val WINDOW_SIZE = 4096
    private const val HOP_SIZE = 2048
    private const val MIN_FREQ = 110.0 // A2
    private const val MAX_FREQ = 1760.0 // A6

    /**
     * Returns detected tuning frequency in Hz (e.g. 440.0, 442.5, 432.0).
     */
    fun detectTuningFrequency(samples: FloatArray, sampleRate: Int): Float {
        if (samples.isEmpty() || sampleRate <= 0 || samples.size < WINDOW_SIZE * 2) {
            return 440.0f
        }

        val frameCount = (samples.size - WINDOW_SIZE) / HOP_SIZE + 1
        if (frameCount < 2) return 440.0f

        val frame = FloatArray(WINDOW_SIZE)
        val devBins = DoubleArray(100) // -50 cents to +50 cents bin distribution
        var totalEnergy = 0.0

        for (fr in 0 until frameCount) {
            val offset = fr * HOP_SIZE
            samples.copyInto(frame, 0, offset, offset + WINDOW_SIZE)
            val mag = Fft.magnitudes(frame)

            for (k in 1 until mag.size) {
                val freq = k * sampleRate.toDouble() / WINDOW_SIZE
                if (freq < MIN_FREQ || freq > MAX_FREQ) continue

                val magVal = mag[k].toDouble()
                if (magVal < 1e-4) continue

                // Exact MIDI pitch number (floating)
                val midiExact = 69.0 + 12.0 * log2(freq / 440.0)
                val midiNearest = midiExact.roundToInt()
                val centDiff = (midiExact - midiNearest) * 100.0 // -50 to +50

                val binIdx = (centDiff + 50.0).toInt().coerceIn(0, 99)
                devBins[binIdx] += magVal * magVal
                totalEnergy += magVal * magVal
            }
        }

        if (totalEnergy <= 1e-9) return 440.0f

        // Peak bin in cent deviation histogram
        var maxBin = 50
        for (i in devBins.indices) {
            if (devBins[i] > devBins[maxBin]) {
                maxBin = i
            }
        }

        val dominantCentDev = maxBin - 50.0 // -50..+50 cents
        val tuningHz = 440.0 * 2.0.pow(dominantCentDev / 1200.0)

        return tuningHz.toFloat().coerceIn(400.0f, 480.0f)
    }
}
