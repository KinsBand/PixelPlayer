package com.theveloper.pixelplay.data.dsp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Real-time spectrum of what PixelPlayer is outputting, for the Equalizer screen.
 *
 * Reads the FFT of the app's own audio session with [Visualizer] (about 20 captures a
 * second, taken after the EQ and effects, so it shows what you actually hear) and folds it
 * into [BARS] log-spaced bars from [MIN_HZ] to [MAX_HZ], each 0..1 on a dB scale.
 *
 * Android files Visualizer under the microphone permission (RECORD_AUDIO) even though it only
 * reads this app's own output; [status] reports [Status.NEEDS_PERMISSION] until it's granted.
 */
class LiveSpectrumAnalyzer(private val context: Context) {

    enum class Status { IDLE, NEEDS_PERMISSION, LIVE, UNAVAILABLE }

    private val _bars = MutableStateFlow<FloatArray?>(null)
    /** Latest bar levels (0..1, bass first), or null when nothing is being captured. */
    val bars: StateFlow<FloatArray?> = _bars.asStateFlow()

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var visualizer: Visualizer? = null
    private var sessionId = 0

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Starts, keeps or stops capture so it only runs while [active] on a real session. */
    fun update(audioSessionId: Int, active: Boolean) {
        if (!hasPermission()) {
            stop()
            _status.value = Status.NEEDS_PERMISSION
            return
        }
        if (!active || audioSessionId == 0) {
            stop()
            _status.value = Status.IDLE
            return
        }
        if (visualizer != null && sessionId == audioSessionId) {
            _status.value = Status.LIVE
            return
        }
        stop()

        try {
            val v = Visualizer(audioSessionId)
            v.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = CAPTURE_SIZE.coerceIn(range[0], range[1])
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null && samplingRate > 0) _bars.value = toBars(fft, samplingRate / 1000f)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true
            )
            v.enabled = true
            visualizer = v
            sessionId = audioSessionId
            _status.value = Status.LIVE
        } catch (e: Exception) {
            // Some outputs refuse the effect (audio offload, OEM restrictions). Not fatal.
            Timber.w(e, "Spectrum unavailable for session $audioSessionId")
            stop()
            _status.value = Status.UNAVAILABLE
        }
    }

    fun release() {
        stop()
        _status.value = Status.IDLE
    }

    private fun stop() {
        visualizer?.let {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                Timber.w(e, "Error releasing spectrum visualizer")
            }
        }
        visualizer = null
        sessionId = 0
        _bars.value = null
    }

    /**
     * [fft] layout: [0] = DC real, [1] = Nyquist real, then (re, im) pairs for bins
     * 1 until n/2. Bars narrower than one FFT bin (the low end) interpolate between bins.
     */
    private fun toBars(fft: ByteArray, sampleRateHz: Float): FloatArray {
        val n = fft.size
        val bins = n / 2
        if (bins < 2) return FloatArray(BARS)
        val binHz = sampleRateHz / n

        fun binMagnitude(k: Int): Float {
            val i = k.coerceIn(1, bins - 1)
            return hypot(fft[2 * i].toFloat(), fft[2 * i + 1].toFloat())
        }

        val out = FloatArray(BARS)
        for (b in 0 until BARS) {
            val lo = EDGES_HZ[b]
            val hi = EDGES_HZ[b + 1]
            val centre = sqrt(lo * hi)
            val kLo = lo / binHz
            val kHi = hi / binHz
            val magnitude = if (kHi - kLo < 1f) {
                val kc = centre / binHz
                val k0 = floor(kc).toInt()
                val frac = kc - k0
                binMagnitude(k0) * (1f - frac) + binMagnitude(k0 + 1) * frac
            } else {
                var peak = 0f
                var k = kLo.toInt().coerceAtLeast(1)
                val end = kHi.toInt().coerceAtMost(bins - 1)
                while (k <= end) {
                    peak = max(peak, binMagnitude(k))
                    k++
                }
                peak
            }
            // Music falls off toward the treble; tilt it back so the top end isn't always flat.
            val tilt = TILT_DB_PER_OCTAVE * (ln(centre / 1000f) / LN2)
            val db = 20f * log10(magnitude + 0.001f) + tilt
            out[b] = ((db - FLOOR_DB) / (CEIL_DB - FLOOR_DB)).coerceIn(0f, 1f)
        }
        return out
    }

    companion object {
        const val BARS = 40
        const val MIN_HZ = 25f
        const val MAX_HZ = 20_000f
        private const val CAPTURE_SIZE = 1024
        private const val FLOOR_DB = 4f
        private const val CEIL_DB = 42f
        private const val TILT_DB_PER_OCTAVE = 2.5f
        private val LN2 = ln(2f)

        /** Log-spaced bar edges, [BARS] + 1 of them. */
        private val EDGES_HZ = FloatArray(BARS + 1) { i ->
            MIN_HZ * (MAX_HZ / MIN_HZ).pow(i / BARS.toFloat())
        }
    }
}
