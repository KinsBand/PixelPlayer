package com.theveloper.pixelplay.ui.overlay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.math.hypot
import kotlin.math.log10

/**
 * Live band energies of what the player is outputting, for the island's wave ring.
 *
 * Reads the FFT of the app's own audio session with [Visualizer] (about 20 captures a second)
 * and folds it into [BANDS] log-spaced bands, each normalised against its own slowly decaying
 * peak so quiet and loud tracks both move the ring.
 *
 * [levels] is null when there is nothing live to show: paused, no session yet, or the
 * Visualizer can't be used (it needs the microphone permission, RECORD_AUDIO, even though it
 * only reads this app's own output). The ring then falls back to a gentle synthetic motion.
 */
class IslandAudioReactor(private val context: Context) {

    private val _levels = MutableStateFlow<FloatArray?>(null)
    val levels: StateFlow<FloatArray?> = _levels.asStateFlow()

    private var visualizer: Visualizer? = null
    private var sessionId = 0
    private val peaks = FloatArray(BANDS) { MIN_PEAK }

    /** Starts, keeps or stops capture so it only runs while [active] on a real session. */
    fun update(audioSessionId: Int, active: Boolean) {
        if (!active || audioSessionId == 0) {
            release()
            return
        }
        if (visualizer != null && sessionId == audioSessionId) return
        release()

        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        try {
            val v = Visualizer(audioSessionId)
            v.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = CAPTURE_SIZE.coerceIn(range[0], range[1])
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) _levels.value = toBands(fft)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true
            )
            v.enabled = true
            visualizer = v
            sessionId = audioSessionId
        } catch (e: Exception) {
            // Some devices refuse the effect (audio offload, OEM restrictions). Not fatal.
            Timber.w(e, "Island visualizer unavailable for session $audioSessionId")
            release()
        }
    }

    fun release() {
        visualizer?.let {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                Timber.w(e, "Error releasing island visualizer")
            }
        }
        visualizer = null
        sessionId = 0
        _levels.value = null
    }

    private fun toBands(fft: ByteArray): FloatArray {
        // Layout: [0] = DC real, [1] = Nyquist real, then (re, im) pairs for bins 1 until n/2.
        val bins = fft.size / 2
        val out = FloatArray(BANDS)
        for (b in 0 until BANDS) {
            val start = BAND_EDGES[b].coerceAtMost(bins - 1)
            val end = BAND_EDGES[b + 1].coerceIn(start + 1, bins)
            var sum = 0f
            for (k in start until end) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                sum += hypot(re, im)
            }
            val magnitude = sum / (end - start)
            // dB-ish so a kick drum doesn't flatten everything else.
            val db = (20f * log10(magnitude + 1f) / 42f).coerceIn(0f, 1f)
            peaks[b] = maxOf(db, peaks[b] * PEAK_DECAY, MIN_PEAK)
            out[b] = (db / peaks[b]).coerceIn(0f, 1f)
        }
        return out
    }

    companion object {
        const val BANDS = 8
        private const val CAPTURE_SIZE = 256
        private const val PEAK_DECAY = 0.985f
        private const val MIN_PEAK = 0.18f

        /** FFT bin edges for a 256-sample capture (~172 Hz per bin at 44.1 kHz), bass first. */
        private val BAND_EDGES = intArrayOf(1, 2, 3, 5, 8, 13, 21, 34, 64)
    }
}
