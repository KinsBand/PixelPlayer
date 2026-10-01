package com.theveloper.pixelplay.data.lyrics.autosync

import com.theveloper.pixelplay.data.analysis.dsp.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-frame audio features on a ~10 ms grid, produced by [VocalFeatureExtractor].
 *
 * Frame `k` is centred on song time [firstFrameMs] + `k` · [hopMs]. Only the first [frameCount]
 * entries of [onset] and the first [frameCount] · [bands] entries of [logMel] are meaningful.
 */
class VocalFeatureTrack(
    /** Centre time of frame 0, in ms of song time. */
    val firstFrameMs: Double,
    /** Distance between frames in ms (about 10). */
    val hopMs: Double,
    /** Onset strength of sustained (harmonic) sound in the vocal band. */
    val onset: FloatArray,
    /** Mel spectrogram, frame-major, stored as round(ln(1 + magnitude) · [LOG_MEL_SCALE]). */
    val logMel: ShortArray,
    val bands: Int,
    val frameCount: Int
) {
    fun frameAt(timeMs: Double): Double = (timeMs - firstFrameMs) / hopMs

    /** Linear mel magnitude of [frame], [band]. */
    fun mel(frame: Int, band: Int): Float = MEL_DECODE[logMel[frame * bands + band].toInt()]

    /** ln(1 + magnitude) of [frame], [band]. */
    fun logMelValue(frame: Int, band: Int): Float = logMel[frame * bands + band] / LOG_MEL_SCALE

    companion object {
        const val LOG_MEL_SCALE = 2048f

        /** Stored value → linear magnitude. Stored values are never negative (ln(1 + x) ≥ 0). */
        private val MEL_DECODE = FloatArray(Short.MAX_VALUE + 1) { i -> (exp(i / LOG_MEL_SCALE.toDouble()) - 1.0).toFloat() }
    }
}

/**
 * Streams decoded mono PCM (any sample rate) into a [VocalFeatureTrack] without holding the
 * song in memory: the result costs 68 bytes per 10 ms frame (≈ 1.6 MB for four minutes).
 *
 * Per 10 ms hop:
 *  1. Anti-aliased integer decimation to 14–22 kHz (windowed-sinc FIR; only the kept samples are
 *     computed).
 *  2. Hann-windowed FFT (32–46 ms), magnitudes up to [MAX_HZ].
 *  3. [BANDS] mel bands from [MIN_HZ] to [MAX_HZ] — where a singer's fundamental, formants and
 *     most of their energy sit. These are kept (log-quantised to 16 bits) for the second pass in
 *     [VocalActivity], which removes the song's repeating accompaniment.
 *  4. Harmonic/percussive split (Fitzgerald median filtering): a median over ±[HPSS_HALF_WINDOW]
 *     frames keeps sustained sound (voice, pads), a median across neighbouring bands keeps
 *     broadband clicks (drums); a soft Wiener mask keeps the sustained part.
 *  5. SuperFlux-style onset strength on the log of that: the rise over the maximum of the
 *     neighbouring bands two frames earlier, which ignores vibrato wobble.
 *
 * Pure Kotlin (no Android), so it runs in local JVM unit tests.
 */
class VocalFeatureExtractor(
    private val inputSampleRate: Int,
    /** Song time of the first sample passed to [push]. */
    private val timeOriginMs: Double = 0.0,
    expectedDurationMs: Long = 0L,
    private val maxDurationMs: Long = MAX_DURATION_MS
) {
    init {
        require(inputSampleRate in 8_000..768_000) { "Unsupported sample rate $inputSampleRate" }
    }

    /** Integer decimation factor; the working rate stays above 14 kHz so 4 kHz is far from Nyquist. */
    val decimation: Int = max(1, inputSampleRate / 14_000)
    val workingRate: Double = inputSampleRate.toDouble() / decimation
    val fftSize: Int = if (workingRate > 18_000) 1024 else 512
    val hop: Int = (workingRate / 100.0).roundToInt()

    private val fir: FloatArray = designLowPass(decimation)
    private val firLen = fir.size
    private val ring = FloatArray(firLen * 2)
    private var ringPos = 0
    private var inputCount = 0L

    private val frame = FloatArray(fftSize)
    private var frameFill = 0
    private val window = FloatArray(fftSize) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))).toFloat() }
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val maxBin = min(fftSize / 2 - 1, (MAX_HZ * 1.15 * fftSize / workingRate).toInt() + 1)
    private val mag = FloatArray(maxBin + 1)
    private val bandStart = IntArray(BANDS)
    private val bandWeights = arrayOfNulls<FloatArray>(BANDS)

    /** Keeps magnitudes comparable between the 512- and 1024-point FFTs. */
    private val magnitudeGain = 512f / fftSize

    // Mel history for the time median (ring of HPSS_WINDOW frames).
    private val melHist = Array(HPSS_WINDOW) { FloatArray(BANDS) }
    private val medianScratch = FloatArray(HPSS_WINDOW)
    private val bandScratch = FloatArray(PERC_BAND_WINDOW)
    private val logRing = Array(3) { FloatArray(BANDS) }
    private var framesIn = 0

    private val maxFrames = (maxDurationMs / 10).toInt()
    private val initialFrames: Int =
        if (expectedDurationMs > 0) min(maxFrames + 64, (expectedDurationMs / 10).toInt() + 256) else min(maxFrames + 64, 30_000)
    private var onset = FloatArray(initialFrames)
    private var logMel = ShortArray(initialFrames * BANDS)

    /** True once [maxDurationMs] of audio has been consumed; further input is ignored. */
    val isFull: Boolean get() = framesIn >= maxFrames

    init {
        buildMelBank()
    }

    /** Feeds [count] mono samples in [-1, 1] from [samples], starting at [offset]. */
    fun push(samples: FloatArray, offset: Int = 0, count: Int = samples.size - offset) {
        val d = decimation
        var i = offset
        val end = offset + count
        while (i < end) {
            if (isFull) return
            val x = samples[i++]
            ring[ringPos] = x
            ring[ringPos + firLen] = x
            ringPos++
            if (ringPos == firLen) ringPos = 0
            inputCount++
            if (inputCount >= firLen && (inputCount - firLen) % d == 0L) {
                // ring[ringPos until ringPos + firLen] holds the last firLen samples, oldest first.
                var acc = 0f
                var k = ringPos
                for (t in 0 until firLen) acc += fir[t] * ring[k++]
                pushDecimated(acc)
            }
        }
    }

    private fun pushDecimated(x: Float) {
        frame[frameFill++] = x
        if (frameFill == fftSize) {
            processFrame()
            System.arraycopy(frame, hop, frame, 0, fftSize - hop)
            frameFill = fftSize - hop
        }
    }

    private fun processFrame() {
        for (i in 0 until fftSize) {
            re[i] = frame[i] * window[i]
            im[i] = 0f
        }
        Fft.fft(re, im)
        for (k in 0..maxBin) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k]) * magnitudeGain

        val index = framesIn
        ensureCapacity(index + 1)
        val mel = melHist[index % HPSS_WINDOW]
        val base = index * BANDS
        for (b in 0 until BANDS) {
            val w = bandWeights[b]!!
            val s = bandStart[b]
            var acc = 0f
            for (j in w.indices) acc += w[j] * mag[s + j]
            mel[b] = acc
            logMel[base + b] = min(Short.MAX_VALUE.toFloat(), ln(1f + acc) * VocalFeatureTrack.LOG_MEL_SCALE + 0.5f).toInt().toShort()
        }
        framesIn++
        // The harmonic median is centred, so the frame completed now is HPSS_HALF_WINDOW back.
        val centre = framesIn - 1 - HPSS_HALF_WINDOW
        if (centre >= 0) emitOnset(centre)
    }

    private fun emitOnset(centre: Int) {
        val available = min(framesIn, HPSS_WINDOW)
        val centreMel = melHist[centre % HPSS_WINDOW]
        val log = logRing[centre % 3]
        for (b in 0 until BANDS) {
            for (j in 0 until available) medianScratch[j] = melHist[j][b]
            val harmonic = median(medianScratch, available)
            var n = 0
            for (bb in b - PERC_HALF..b + PERC_HALF) if (bb in 0 until BANDS) bandScratch[n++] = centreMel[bb]
            val percussive = median(bandScratch, n)
            val h2 = harmonic * harmonic
            val mask = h2 / (h2 + percussive * percussive + 1e-12f)
            log[b] = ln(1f + centreMel[b] * mask)
        }
        var on = 0f
        if (centre >= 2) {
            val prev2 = logRing[(centre - 2) % 3]
            for (b in 0 until BANDS) {
                var ref = prev2[b]
                if (b > 0 && prev2[b - 1] > ref) ref = prev2[b - 1]
                if (b < BANDS - 1 && prev2[b + 1] > ref) ref = prev2[b + 1]
                val diff = log[b] - ref
                if (diff > 0f) on += diff
            }
        }
        onset[centre] = on
    }

    private fun ensureCapacity(frames: Int) {
        if (frames <= onset.size) return
        val next = min(maxFrames + 64, max(frames, onset.size + onset.size / 2))
        onset = onset.copyOf(next)
        logMel = logMel.copyOf(next * BANDS)
    }

    /** Finishes the stream and returns the features. The extractor must not be used afterwards. */
    fun finish(): VocalFeatureTrack {
        // The last HPSS_HALF_WINDOW frames never get a centred window and are left out.
        val count = max(0, framesIn - HPSS_HALF_WINDOW)
        return VocalFeatureTrack(
            firstFrameMs = timeOriginMs + frameCentreMs(0),
            hopMs = hop * decimation * 1000.0 / inputSampleRate,
            onset = onset,
            logMel = logMel,
            bands = BANDS,
            frameCount = count
        )
    }

    /**
     * Centre of frame [index] in ms after the first input sample. Decimated sample `j` stands for
     * input sample `j·D + (L − 1)/2` (the FIR is symmetric), and frame `k` covers decimated
     * samples `[k·hop, k·hop + N)`.
     */
    private fun frameCentreMs(index: Int): Double {
        val decimatedCentre = index.toDouble() * hop + (fftSize - 1) / 2.0
        val inputSample = decimatedCentre * decimation + (firLen - 1) / 2.0
        return inputSample * 1000.0 / inputSampleRate
    }

    private fun buildMelBank() {
        fun hzToMel(hz: Double) = 2595.0 * kotlin.math.log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)
        val lo = hzToMel(MIN_HZ)
        val hi = hzToMel(MAX_HZ)
        val binHz = workingRate / fftSize
        val edges = DoubleArray(BANDS + 2) { melToHz(lo + (hi - lo) * it / (BANDS + 1)) }
        for (b in 0 until BANDS) {
            val left = edges[b]
            val centre = edges[b + 1]
            val right = edges[b + 2]
            val first = (left / binHz).toInt().coerceIn(0, maxBin)
            val last = ((right / binHz).toInt() + 1).coerceIn(first, maxBin)
            val weights = FloatArray(last - first + 1)
            var sum = 0f
            for (k in first..last) {
                val f = k * binHz
                val w = when {
                    f <= left || f >= right -> 0.0
                    f <= centre -> (f - left) / (centre - left)
                    else -> (right - f) / (right - centre)
                }.toFloat()
                weights[k - first] = w
                sum += w
            }
            if (sum <= 0f) {
                // Band narrower than one FFT bin: use the nearest bin.
                val nearest = (centre / binHz).roundToInt().coerceIn(first, last)
                weights[nearest - first] = 1f
                sum = 1f
            }
            for (j in weights.indices) weights[j] /= sum
            bandStart[b] = first
            bandWeights[b] = weights
        }
    }

    companion object {
        const val MIN_HZ = 200.0
        const val MAX_HZ = 4_000.0
        const val BANDS = 32
        const val HPSS_HALF_WINDOW = 8
        const val HPSS_WINDOW = 2 * HPSS_HALF_WINDOW + 1
        private const val PERC_HALF = 2
        private const val PERC_BAND_WINDOW = 2 * PERC_HALF + 1

        /** Songs are analysed up to this length; later lines are ignored. */
        const val MAX_DURATION_MS = 10L * 60 * 1000

        /**
         * Windowed-sinc low-pass for decimation by [d] (Blackman window, 12·d + 1 taps, cut-off a
         * little below the new Nyquist, unity DC gain). `d == 1` is a pass-through.
         */
        fun designLowPass(d: Int): FloatArray {
            if (d <= 1) return floatArrayOf(1f)
            val taps = 12 * d + 1
            val cutoff = 0.42 / d // cycles per input sample
            val m = (taps - 1) / 2.0
            val h = DoubleArray(taps) { i ->
                val x = i - m
                val sinc = if (x == 0.0) 2 * cutoff else sin(2 * PI * cutoff * x) / (PI * x)
                val w = 0.42 - 0.5 * cos(2 * PI * i / (taps - 1)) + 0.08 * cos(4 * PI * i / (taps - 1))
                sinc * w
            }
            val sum = h.sum()
            return FloatArray(taps) { (h[it] / sum).toFloat() }
        }

        /** Median of the first [n] entries (sorts them in place; n is small). */
        internal fun median(values: FloatArray, n: Int): Float {
            if (n <= 0) return 0f
            for (i in 1 until n) {
                val v = values[i]
                var j = i - 1
                while (j >= 0 && values[j] > v) {
                    values[j + 1] = values[j]
                    j--
                }
                values[j + 1] = v
            }
            return if (n % 2 == 1) values[n / 2] else 0.5f * (values[n / 2 - 1] + values[n / 2])
        }
    }
}
