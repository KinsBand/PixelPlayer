package com.theveloper.pixelplay.data.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * High-precision Media3 [AudioProcessor] DSP pipeline executing:
 * 1. Master Pre-Amp Gain & Auto-Preamp Headroom Compensation
 * 2. Subsonic Low-Cut Rumble Filter
 * 3. Multi-Band Cascaded Biquad EQ (Graphic 10-Band or Parametric PEQ)
 * 4. Ultrasonic High-Cut Smoothing Filter
 * 5. Bauer/Meier Stereo Crossfeed
 * 6. True Peak Limiter & Analog Soft Saturation
 *
 * All operations execute in 32-bit floating point with zero allocations per audio frame.
 */
@UnstableApi
class DspAudioProcessor(
    private val configProvider: () -> DspConfig
) : AudioProcessor {

    companion object {
        private val NATIVE_ORDER = ByteOrder.nativeOrder()
        private const val MAX_BANDS = 32
    }

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET

    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded: Boolean = false

    // Internal working float buffer for DSP math
    private var floatProcessingBuffer = FloatArray(4096)

    // DSP components
    private val biquadFilters = Array(MAX_BANDS) { BiquadFilter() }
    private val subsonicFilter = SubsonicFilter()
    private val ultrasonicFilter = UltrasonicFilter()
    private val stereoCrossfeed = StereoCrossfeed()
    private val truePeakLimiter = TruePeakLimiter()
    private val autoPreamp = AutoPreamp()

    // Cached state to detect sample rate or config updates
    private var currentSampleRate: Float = 0f
    private var lastConfig: DspConfig? = null

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if ((inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) ||
            inputAudioFormat.channelCount !in 1..2
        ) {
            // Passthrough unsupported formats or channel counts without processing
            this.inputAudioFormat = AudioFormat.NOT_SET
            this.outputAudioFormat = AudioFormat.NOT_SET
            return inputAudioFormat
        }

        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat // preserves sample rate and encoding
        currentSampleRate = inputAudioFormat.sampleRate.toFloat()

        subsonicFilter.setSampleRate(currentSampleRate)
        ultrasonicFilter.setSampleRate(currentSampleRate)
        stereoCrossfeed.setSampleRate(currentSampleRate)
        truePeakLimiter.setSampleRate(currentSampleRate)

        return outputAudioFormat
    }

    override fun isActive(): Boolean {
        return outputAudioFormat != AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val config = configProvider()
        syncConfig(config)

        val channels = inputAudioFormat.channelCount
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) 4 else 2
        val bytesPerFrame = channels * bytesPerSample
        val frameCount = remaining / bytesPerFrame
        val sampleCount = frameCount * channels

        if (floatProcessingBuffer.size < sampleCount) {
            floatProcessingBuffer = FloatArray(sampleCount)
        }

        // 1. Read input into 32-bit float internal buffer
        val duplicateInput = inputBuffer.duplicate().order(NATIVE_ORDER)
        if (isFloat) {
            val floatBuffer = duplicateInput.asFloatBuffer()
            floatBuffer.get(floatProcessingBuffer, 0, sampleCount)
        } else {
            val shortBuffer = duplicateInput.asShortBuffer()
            val invShortMax = 1f / 32768f
            for (i in 0 until sampleCount) {
                floatProcessingBuffer[i] = shortBuffer.get() * invShortMax
            }
        }
        inputBuffer.position(inputBuffer.limit())

        // 2. Execute DSP Pipeline if active
        if (config.isEnabled) {
            // A. Preamp gain & Loudness compensation
            val totalPreampMultiplier = config.preampLinearGain * config.loudnessCompGain
            if (kotlin.math.abs(totalPreampMultiplier - 1.0f) > 0.001f) {
                for (i in 0 until sampleCount) {
                    floatProcessingBuffer[i] *= totalPreampMultiplier
                }
            }

            // B. Subsonic Filter
            if (config.subsonicEnabled) {
                subsonicFilter.processInterleaved(floatProcessingBuffer, frameCount, channels)
            }

            // C. Equalizer Biquad Filters
            val activeFilterCount = minOf(config.eqCoefficients.size, MAX_BANDS)
            for (b in 0 until activeFilterCount) {
                val filter = biquadFilters[b]
                if (filter.coefficients !== BiquadCoefficients.BYPASS) {
                    if (channels == 2) {
                        for (i in 0 until frameCount) {
                            val idx = i * 2
                            floatProcessingBuffer[idx] = filter.processLeft(floatProcessingBuffer[idx])
                            floatProcessingBuffer[idx + 1] = filter.processRight(floatProcessingBuffer[idx + 1])
                        }
                    } else {
                        for (i in 0 until frameCount) {
                            floatProcessingBuffer[i] = filter.processLeft(floatProcessingBuffer[i])
                        }
                    }
                }
            }

            // D. Ultrasonic Filter
            if (config.ultrasonicEnabled) {
                ultrasonicFilter.processInterleaved(floatProcessingBuffer, frameCount, channels)
            }

            // E. Stereo Crossfeed (Stereo only)
            if (channels == 2 && config.crossfeedStrength != CrossfeedStrength.OFF) {
                stereoCrossfeed.processInterleaved(floatProcessingBuffer, frameCount, channels)
            }

            // F. True Peak Limiter & Soft Saturation
            if (config.limiterEnabled) {
                truePeakLimiter.processInterleaved(floatProcessingBuffer, frameCount, channels)
            }
        }

        // 3. Write back to output buffer
        val outputByteCount = frameCount * bytesPerFrame
        val out = ensureOutputBuffer(outputByteCount)

        if (isFloat) {
            val outFloat = out.asFloatBuffer()
            outFloat.put(floatProcessingBuffer, 0, sampleCount)
        } else {
            val outShort = out.asShortBuffer()
            for (i in 0 until sampleCount) {
                val clamped = floatProcessingBuffer[i].coerceIn(-1.0f, 1.0f)
                val s = (clamped * 32767f).roundToInt().toShort()
                outShort.put(s)
            }
        }
        out.position(0)
        out.limit(outputByteCount)
        outputBuffer = out
    }

    private fun syncConfig(config: DspConfig) {
        if (config === lastConfig) return
        lastConfig = config

        // Subsonic
        subsonicFilter.setFilterEnabled(config.subsonicEnabled)
        subsonicFilter.setCutoff(config.subsonicCutoffHz)

        // Ultrasonic
        ultrasonicFilter.setFilterEnabled(config.ultrasonicEnabled)
        ultrasonicFilter.setCutoff(config.ultrasonicCutoffHz)

        // Crossfeed
        stereoCrossfeed.setCrossfeedStrength(config.crossfeedStrength)

        // Limiter
        truePeakLimiter.isEnabled = config.limiterEnabled
        truePeakLimiter.isSoftSaturationEnabled = config.softSaturationEnabled

        // EQ coefficients
        val eqList = config.eqCoefficients
        for (i in 0 until MAX_BANDS) {
            if (i < eqList.size) {
                biquadFilters[i].coefficients = eqList[i]
            } else {
                biquadFilters[i].coefficients = BiquadCoefficients.BYPASS
            }
        }
    }

    private fun ensureOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(NATIVE_ORDER)
        } else {
            buffer.clear()
        }
        return buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        for (filter in biquadFilters) {
            filter.reset()
        }
        subsonicFilter.reset()
        ultrasonicFilter.reset()
        stereoCrossfeed.reset()
        truePeakLimiter.reset()
        autoPreamp.reset()
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
        currentSampleRate = 0f
        lastConfig = null
    }
}
