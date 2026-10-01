package com.theveloper.pixelplay.data.analysis

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.theveloper.pixelplay.data.analysis.dsp.Resampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * MediaExtractor + MediaCodec PCM decoder for the analysis pipeline.
 * This is the ONLY Android-coupled file of the analysis package.
 *
 * Unlike utils/AudioDecoder (interleaved samples with no format info),
 * this decoder reports the sample rate, downmixes to mono by averaging
 * channels, decimates to [targetSampleRate] when the source rate is
 * higher, and stops after [maxSeconds] of audio. Any failure (no audio
 * track, codec error, unsupported PCM encoding) yields null — callers
 * treat that as "analysis unavailable" for the track.
 */
object PcmDecoder {

    private const val TAG = "PcmDecoder"

    /** Extra decode budget for [decodeTail] beyond the requested window. */
    const val TAIL_SLACK_SECONDS = 60
    private const val TIMEOUT_US = 10_000L
    private const val ENCODING_PCM_16BIT = 2
    private const val ENCODING_PCM_FLOAT = 4

    /**
     * Decodes the last [windowSeconds] of [uri] (or the whole file when it is
     * shorter). Used for end-of-track descriptors — trailing silence, fade-out —
     * which [decode]'s head-only window cannot see on tracks longer than its cap.
     * Returns null when the container reports no duration (not seekable by time).
     */
    suspend fun decodeTail(
        context: Context,
        uri: Uri,
        windowSeconds: Int = 75,
        targetSampleRate: Int = 22050
    ): PcmAudio? = decode(
        context = context,
        uri = uri,
        // Seeking lands on the previous sync frame, so allow generous slack: the
        // tail descriptors are only valid if decoding actually reaches end-of-stream.
        maxSeconds = windowSeconds + TAIL_SLACK_SECONDS,
        targetSampleRate = targetSampleRate,
        startFromEndSeconds = windowSeconds
    )

    suspend fun decode(
        context: Context,
        uri: Uri,
        maxSeconds: Int = 180,
        targetSampleRate: Int = 22050,
        startFromEndSeconds: Int? = null
    ): PcmAudio? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex < 0) {
                Timber.tag(TAG).w("No audio track found for %s", uri)
                return@withContext null
            }
            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            if (startFromEndSeconds != null) {
                val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    trackFormat.getLong(MediaFormat.KEY_DURATION)
                } else {
                    -1L
                }
                if (durationUs <= 0L) {
                    Timber.tag(TAG).w("No duration for tail decode of %s", uri)
                    return@withContext null
                }
                val startUs = (durationUs - startFromEndSeconds * 1_000_000L).coerceAtLeast(0L)
                if (startUs > 0L) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime == null) {
                Timber.tag(TAG).w("No MIME type for %s", uri)
                return@withContext null
            }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            var outputFormat = decoder.outputFormat
            var sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var maxMonoSamples = minOf(maxSeconds.toLong() * sampleRate, MAX_MONO_SAMPLES.toLong()).toInt()

            // Size the buffer from the container duration so it is allocated once. Growing a
            // multi-MB FloatArray by doubling (1M -> 2M -> 4M floats) plus the final trim copy
            // briefly held ~3x the decoded audio on the heap.
            val mono = FloatArrayBuilder(
                initialCapacity = estimateMonoSamples(trackFormat, sampleRate, maxMonoSamples, startFromEndSeconds),
                hardCap = MAX_MONO_SAMPLES + (1 shl 16)
            )
            val bufferInfo = MediaCodec.BufferInfo()
            var sawEos = false

            while (!sawEos && mono.size < maxMonoSamples) {
                coroutineContext.ensureActive()
                val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outputIndex >= 0 || outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        outputFormat = decoder.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        maxMonoSamples = minOf(maxSeconds.toLong() * sampleRate, MAX_MONO_SAMPLES.toLong()).toInt()
                    } else {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            appendMono(mono, outputBuffer, bufferInfo, outputFormat, channelCount)
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawEos = true
                        }
                        if (mono.size >= maxMonoSamples) break
                    }
                    outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                }
            }

            if (mono.size == 0) {
                Timber.tag(TAG).w("Decoded no samples for %s", uri)
                return@withContext null
            }

            val bitDepth = try {
                if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    when (outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                        ENCODING_PCM_16BIT -> 16
                        ENCODING_PCM_FLOAT -> 32
                        else -> 16
                    }
                } else if (outputFormat.containsKey("bits-per-sample")) {
                    outputFormat.getInteger("bits-per-sample")
                } else 16
            } catch (_: Exception) { 16 }

            // Decimate to the working rate; never upsample (the detectors
            // are parameterized by sample rate and work at the source rate).
            return@withContext if (sampleRate > targetSampleRate) {
                PcmAudio(
                    // Resample straight from the decode buffer: no trimmed native-rate copy.
                    samples = Resampler.resample(mono.buffer, mono.size, sampleRate, targetSampleRate),
                    sampleRate = targetSampleRate,
                    bitDepth = bitDepth,
                    channelCount = channelCount,
                    mimeType = mime
                )
            } else {
                PcmAudio(
                    samples = mono.toFloatArray(),
                    sampleRate = sampleRate,
                    bitDepth = bitDepth,
                    channelCount = channelCount,
                    mimeType = mime
                )
            }.also {
                Timber.tag(TAG).d(
                    "Decoded %d mono samples @ %d Hz (from %d Hz, %d ch, %d-bit) for %s",
                    it.samples.size, it.sampleRate, sampleRate, channelCount, bitDepth, uri
                )
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) { throw cancellation } catch (t: Exception) {
            Timber.tag(TAG).w(t, "PCM decode failed for %s", uri)
            null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor?.release() }
        }
    }

    /**
     * Decodes [uri] from the start and hands the audio to [sink] as mono chunks at the source
     * sample rate, never holding more than one chunk: for analyses that only need a running
     * summary (the lyrics offset estimator keeps ~68 bytes per 10 ms), a whole song costs a few
     * kilobytes instead of tens of megabytes.
     *
     * The encoder delay declared by the container (MP3 LAME/Xing, MP4 gapless info) is skipped,
     * so sample 0 lines up with position 0 of the player, which trims it as well.
     *
     * [sink] receives (samples, count, sampleRate) and returns false to stop early. Returns the
     * number of mono samples delivered, or null when the file can't be decoded.
     */
    suspend fun stream(
        context: Context,
        uri: Uri,
        maxSeconds: Int,
        sink: (samples: FloatArray, count: Int, sampleRate: Int) -> Boolean
    ): Long? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex < 0) return@withContext null
            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            var skipFrames = if (trackFormat.containsKey(KEY_ENCODER_DELAY)) {
                trackFormat.getInteger(KEY_ENCODER_DELAY).coerceIn(0, 1 shl 16)
            } else 0

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            var outputFormat = decoder.outputFormat
            var sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val chunk = FloatArray(STREAM_CHUNK)
            var fill = 0
            var delivered = 0L
            var limit = maxSeconds.toLong() * sampleRate
            val bufferInfo = MediaCodec.BufferInfo()
            var sawEos = false
            var stop = false

            fun flush(): Boolean {
                if (fill == 0) return true
                val keepGoing = sink(chunk, fill, sampleRate)
                delivered += fill
                fill = 0
                return keepGoing
            }

            while (!sawEos && !stop) {
                coroutineContext.ensureActive()
                val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (!stop && (outputIndex >= 0 || outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)) {
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        outputFormat = decoder.outputFormat
                        val newRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        if (newRate != sampleRate) {
                            // A rate change after audio was delivered would corrupt the timeline.
                            if (delivered + fill > 0) { stop = true; break }
                            sampleRate = newRate
                            limit = maxSeconds.toLong() * sampleRate
                        }
                    } else {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING, ENCODING_PCM_16BIT)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            val channels = channelCount
                            when (pcmEncoding) {
                                ENCODING_PCM_16BIT -> {
                                    val shorts = outputBuffer.asShortBuffer()
                                    while (shorts.remaining() >= channels && !stop) {
                                        var sum = 0f
                                        for (c in 0 until channels) sum += shorts.get().toFloat()
                                        if (skipFrames > 0) { skipFrames--; continue }
                                        chunk[fill++] = sum / (channels * Short.MAX_VALUE.toFloat())
                                        if (fill == STREAM_CHUNK && !flush()) stop = true
                                        if (delivered + fill >= limit) stop = true
                                    }
                                }
                                ENCODING_PCM_FLOAT -> {
                                    val floats = outputBuffer.asFloatBuffer()
                                    while (floats.remaining() >= channels && !stop) {
                                        var sum = 0f
                                        for (c in 0 until channels) sum += floats.get()
                                        if (skipFrames > 0) { skipFrames--; continue }
                                        chunk[fill++] = sum / channels
                                        if (fill == STREAM_CHUNK && !flush()) stop = true
                                        if (delivered + fill >= limit) stop = true
                                    }
                                }
                                else -> throw UnsupportedOperationException("Unsupported PCM encoding: $pcmEncoding")
                            }
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawEos = true
                    }
                    if (stop) break
                    outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                }
            }
            flush()
            if (delivered == 0L) null else delivered
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Exception) {
            Timber.tag(TAG).w(t, "PCM stream failed for %s", uri)
            null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor?.release() }
        }
    }

    /** MediaFormat key for the encoder delay in frames ("encoder-delay"; public constant since API 30). */
    private const val KEY_ENCODER_DELAY = "encoder-delay"
    private const val STREAM_CHUNK = 8192

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return -1
    }

    /** Upper bound on decoded mono samples (16 MB of floats). */
    private const val MAX_MONO_SAMPLES = 4_000_000

    private fun estimateMonoSamples(
        trackFormat: MediaFormat,
        sampleRate: Int,
        maxMonoSamples: Int,
        startFromEndSeconds: Int?
    ): Int {
        val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
            trackFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            -1L
        }
        if (durationUs <= 0L || sampleRate <= 0) return minOf(maxMonoSamples, 1 shl 18)
        val seconds = if (startFromEndSeconds != null) {
            // Seeking lands on the previous sync frame: allow a little slack.
            minOf(durationUs / 1_000_000.0, startFromEndSeconds + 5.0)
        } else {
            durationUs / 1_000_000.0
        }
        val estimate = (seconds * sampleRate).toLong() + sampleRate // +1 s for rounding / priming
        return estimate.coerceIn(1L shl 12, maxMonoSamples.toLong() + (1 shl 14)).toInt()
    }

    /**
     * Downmixes one decoder output buffer straight into [out] (mono, [-1, 1]).
     * Reads the buffer in place: the old path allocated an interleaved FloatArray per output
     * buffer (hundreds per track) before averaging the channels.
     */
    private fun appendMono(
        out: FloatArrayBuilder,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        format: MediaFormat,
        channelCount: Int
    ) {
        val pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, ENCODING_PCM_16BIT)
        buffer.limit(bufferInfo.offset + bufferInfo.size)
        buffer.position(bufferInfo.offset)
        val channels = channelCount.coerceAtLeast(1)

        when (pcmEncoding) {
            ENCODING_PCM_16BIT -> {
                val shorts = buffer.asShortBuffer()
                if (channels == 1) {
                    while (shorts.hasRemaining()) out.add(shorts.get().toFloat() / Short.MAX_VALUE)
                } else {
                    while (shorts.remaining() >= channels) {
                        var sum = 0f
                        for (c in 0 until channels) sum += shorts.get().toFloat() / Short.MAX_VALUE
                        out.add(sum / channels)
                    }
                }
            }
            ENCODING_PCM_FLOAT -> {
                val floats = buffer.asFloatBuffer()
                if (channels == 1) {
                    while (floats.hasRemaining()) out.add(floats.get())
                } else {
                    while (floats.remaining() >= channels) {
                        var sum = 0f
                        for (c in 0 until channels) sum += floats.get()
                        out.add(sum / channels)
                    }
                }
            }
            else -> throw UnsupportedOperationException("Unsupported PCM encoding: $pcmEncoding")
        }
    }

    /** Minimal growable FloatArray (avoids boxing millions of samples). */
    private class FloatArrayBuilder(initialCapacity: Int, private val hardCap: Int) {
        private var data = FloatArray(maxOf(16, initialCapacity))
        var size = 0
            private set

        fun add(value: Float) {
            if (size == data.size) grow()
            data[size++] = value
        }

        private fun grow() {
            // 1.5x growth, never beyond the hard cap unless the caller insists (+1).
            val next = (data.size.toLong() * 3 / 2 + 16).coerceAtMost(hardCap.toLong()).toInt()
            data = data.copyOf(maxOf(next, data.size + 1))
        }

        /** Backing array; only the first [size] entries are samples. */
        val buffer: FloatArray get() = data

        /** Returns the samples; no copy when the estimate was exact. */
        fun toFloatArray(): FloatArray = if (size == data.size) data else data.copyOf(size)
    }
}
