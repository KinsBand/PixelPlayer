package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.data.analysis.dsp.Fft
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.BAND_COUNT
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.FRAME_SIZE
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.HOP_SIZE
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.MAX_BIN
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.MIN_BIN
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.PEAKS_PER_SECOND
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.PEAK_NEIGHBOURHOOD_BINS
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.PEAK_NEIGHBOURHOOD_FRAMES
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.SAMPLE_RATE
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.SPECTRUM_BINS
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.WHITENING_WIDTH
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Constellation peaks for one signal: parallel arrays, sorted by time then
 * frequency. [count] entries are valid; the arrays may be longer.
 */
class PeakSet(
    @JvmField val frames: IntArray,
    @JvmField val bins: IntArray,
    @JvmField val count: Int
)

/**
 * STFT + spectral whitening + constellation peak picking.
 *
 * Deliberately allocation-light: the per-frame FFT buffers are reused across
 * the whole signal, because indexing a three-minute track runs this ~11 000
 * times and the existing [Fft.magnitudes] helper allocates three arrays per
 * call. [Fft.fft] itself is in-place and reusable, so this uses that directly.
 *
 * The same code path must run for both the indexed reference and the live
 * query. If they ever diverge, the hashes stop being comparable and matching
 * silently degrades rather than failing loudly.
 */
object SpectralFrontEnd {

    private val hannWindow: FloatArray = FloatArray(FRAME_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / (FRAME_SIZE - 1)))).toFloat()
    }

    /** Log-spaced band edges over the usable bin range, computed once. */
    private val bandEdges: IntArray = run {
        val edges = IntArray(BAND_COUNT + 1)
        val lo = MIN_BIN.toDouble()
        val hi = MAX_BIN.toDouble()
        for (b in 0..BAND_COUNT) {
            val f = b.toDouble() / BAND_COUNT
            edges[b] = round(lo * Math.pow(hi / lo, f)).toInt()
        }
        // Guarantee strictly increasing edges even if rounding collapses two.
        for (b in 1..BAND_COUNT) if (edges[b] <= edges[b - 1]) edges[b] = edges[b - 1] + 1
        edges
    }

    /**
     * Whitened log-magnitude spectrogram, row-major, `frames × SPECTRUM_BINS`.
     * Returns an empty array when [samples] is shorter than one frame.
     *
     * [samples] must already be mono at [SAMPLE_RATE].
     */
    fun spectrogram(samples: FloatArray): Array<FloatArray> {
        if (samples.size < FRAME_SIZE) return emptyArray()
        val frameCount = 1 + (samples.size - FRAME_SIZE) / HOP_SIZE

        val re = FloatArray(FRAME_SIZE)
        val im = FloatArray(FRAME_SIZE)
        val smoothed = FloatArray(SPECTRUM_BINS)
        val out = Array(frameCount) { FloatArray(SPECTRUM_BINS) }

        for (t in 0 until frameCount) {
            val base = t * HOP_SIZE
            for (i in 0 until FRAME_SIZE) {
                re[i] = samples[base + i] * hannWindow[i]
                im[i] = 0f
            }
            Fft.fft(re, im)

            val row = out[t]
            for (k in 0 until SPECTRUM_BINS) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                // log1p(mag * 1000) — the scale factor only shifts the curve,
                // but it keeps quiet frames off the flat part of log1p.
                row[k] = ln(1f + mag * 1000f)
            }
            movingAverage(row, smoothed, WHITENING_WIDTH)
            for (k in 0 until SPECTRUM_BINS) row[k] -= smoothed[k]
        }
        return out
    }

    /**
     * Constellation peaks: 2-D local maxima, one candidate per log-spaced band
     * per frame, then capped at [PEAKS_PER_SECOND] strongest per second.
     *
     * The density cap is not optional. Without it this produces roughly
     * 310 hashes per second, which is about ten times the storage the index
     * can justify.
     */
    fun pickPeaks(spectrogram: Array<FloatArray>): PeakSet {
        val frameCount = spectrogram.size
        if (frameCount == 0) return PeakSet(IntArray(0), IntArray(0), 0)

        // Candidate peaks, gathered per frame, band by band.
        val capacity = frameCount * BAND_COUNT
        val candFrame = IntArray(capacity)
        val candBin = IntArray(capacity)
        val candValue = FloatArray(capacity)
        var n = 0

        for (t in 0 until frameCount) {
            val row = spectrogram[t]
            for (b in 0 until BAND_COUNT) {
                val lo = bandEdges[b].coerceAtLeast(MIN_BIN)
                val hi = bandEdges[b + 1].coerceAtMost(MAX_BIN)
                if (hi <= lo) continue

                var bestBin = -1
                var bestValue = 0f
                for (k in lo until hi) {
                    val v = row[k]
                    if (v <= 0f || v <= bestValue) continue
                    if (isLocalMaximum(spectrogram, t, k, v)) {
                        bestValue = v
                        bestBin = k
                    }
                }
                if (bestBin >= 0) {
                    candFrame[n] = t
                    candBin[n] = bestBin
                    candValue[n] = bestValue
                    n++
                }
            }
        }
        if (n == 0) return PeakSet(IntArray(0), IntArray(0), 0)

        return capPerSecond(candFrame, candBin, candValue, n)
    }

    private fun isLocalMaximum(s: Array<FloatArray>, t: Int, k: Int, v: Float): Boolean {
        val t0 = (t - PEAK_NEIGHBOURHOOD_FRAMES).coerceAtLeast(0)
        val t1 = (t + PEAK_NEIGHBOURHOOD_FRAMES).coerceAtMost(s.size - 1)
        val k0 = (k - PEAK_NEIGHBOURHOOD_BINS).coerceAtLeast(0)
        val k1 = (k + PEAK_NEIGHBOURHOOD_BINS).coerceAtMost(SPECTRUM_BINS - 1)
        for (tt in t0..t1) {
            val row = s[tt]
            for (kk in k0..k1) if (row[kk] > v) return false
        }
        return true
    }

    /** Keep the [PEAKS_PER_SECOND] strongest candidates in each 1 s window. */
    private fun capPerSecond(
        frames: IntArray,
        bins: IntArray,
        values: FloatArray,
        n: Int
    ): PeakSet {
        val framesPerWindow = RecognitionConstants.FRAMES_PER_SECOND.toInt()
        val keptFrame = IntArray(n)
        val keptBin = IntArray(n)
        var kept = 0

        var start = 0
        while (start < n) {
            val windowIndex = frames[start] / framesPerWindow
            var end = start
            while (end < n && frames[end] / framesPerWindow == windowIndex) end++

            val size = end - start
            if (size <= PEAKS_PER_SECOND) {
                for (i in start until end) {
                    keptFrame[kept] = frames[i]; keptBin[kept] = bins[i]; kept++
                }
            } else {
                // Partial selection of the strongest PEAKS_PER_SECOND entries.
                val order = (start until end).sortedByDescending { values[it] }
                val chosen = order.take(PEAKS_PER_SECOND).sortedWith(
                    compareBy({ frames[it] }, { bins[it] })
                )
                for (i in chosen) {
                    keptFrame[kept] = frames[i]; keptBin[kept] = bins[i]; kept++
                }
            }
            start = end
        }
        return PeakSet(keptFrame, keptBin, kept)
    }

    /** Centred moving average of [src] into [dst], both length SPECTRUM_BINS. */
    private fun movingAverage(src: FloatArray, dst: FloatArray, width: Int) {
        val half = width / 2
        var sum = 0f
        var count = 0
        for (k in 0..half.coerceAtMost(src.size - 1)) { sum += src[k]; count++ }
        for (k in src.indices) {
            dst[k] = sum / count
            val drop = k - half
            val add = k + half + 1
            if (drop >= 0) { sum -= src[drop]; count-- }
            if (add < src.size) { sum += src[add]; count++ }
        }
    }
}
