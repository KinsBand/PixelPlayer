package com.theveloper.pixelplay.data.dsp

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

enum class AuditionSlot {
    SLOT_A,
    SLOT_B,
    BYPASS
}

data class AuditionState(
    val activeSlot: AuditionSlot = AuditionSlot.SLOT_A,
    val slotABands: List<Int> = List(10) { 0 },
    val slotAName: String = "Slot A",
    val slotBBands: List<Int> = List(10) { 0 },
    val slotBName: String = "Slot B",
    val isLoudnessCompEnabled: Boolean = true
) {
    /**
     * Calculates the estimated linear loudness compensation multiplier needed when
     * comparing against Flat/Bypass.
     */
    fun calculateBypassCompensationGain(currentBands: List<Int>): Float {
        if (!isLoudnessCompEnabled || currentBands.isEmpty()) return 1.0f

        var sumSqLinear = 0.0
        for (b in currentBands) {
            val linear = 10.0.pow(b.toDouble() / 20.0)
            sumSqLinear += linear * linear
        }
        val rmsLinear = sqrt(sumSqLinear / currentBands.size.toDouble()).toFloat()
        // Bound compensation gain to sensible +6 dB max to prevent runaway boost
        return rmsLinear.coerceIn(0.7f, 2.0f)
    }
}
