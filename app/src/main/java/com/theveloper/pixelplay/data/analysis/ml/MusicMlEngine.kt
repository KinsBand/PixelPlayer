package com.theveloper.pixelplay.data.analysis.ml

import android.content.Context
import com.theveloper.pixelplay.data.analysis.dsp.Resampler
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

@Singleton
class MusicMlEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private var yamnetInterpreter: Interpreter? = null
    private var vggishInterpreter: Interpreter? = null

    private val maintenanceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var idleRelease: kotlinx.coroutines.Job? = null

    init {
        com.theveloper.pixelplay.data.diagnostics.HeapPressure.register("music-ml") {
            // Never block the heap watchdog on a running native inference.
            maintenanceScope.launch { releaseModels() }
        }
    }

    @Synchronized
    private fun keepModelsWarm() {
        idleRelease?.cancel()
        idleRelease = maintenanceScope.launch {
            kotlinx.coroutines.delay(60_000)
            val owner = kotlinx.coroutines.currentCoroutineContext()
            synchronized(this@MusicMlEngine) {
                // Cancellation may arrive while this job is waiting for inference's monitor.
                if (owner.isActive) releaseModels()
            }
        }
    }

    @Synchronized
    private fun releaseModels() {
        yamnetInterpreter?.close()
        yamnetInterpreter = null
        vggishInterpreter?.close()
        vggishInterpreter = null
    }

    // prepare() loads models on the background analysis path, never during injection.

    /** Downloads models if missing, then loads them into interpreters. */
    suspend fun prepare(): Boolean {
        return try {
            val yamnetFile = File(context.cacheDir, YAMNET_FILENAME)
            val vggishFile = File(context.cacheDir, VGGISH_FILENAME)

            if (!yamnetFile.exists()) {
                Timber.tag(TAG).i("Downloading YAMNet model...")
                downloadModel(YAMNET_URL, yamnetFile)
            }
            if (!vggishFile.exists()) {
                Timber.tag(TAG).i("Downloading VGGish model...")
                downloadModel(VGGISH_URL, vggishFile)
            }

            loadModels()
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to download or load ML models")
            false
        }
    }

    @Synchronized
    private fun loadModels() {
        keepModelsWarm()
        val yamnetFile = File(context.cacheDir, YAMNET_FILENAME)
        if (yamnetFile.exists() && yamnetInterpreter == null) {
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            yamnetInterpreter = Interpreter(yamnetFile, options)
            Timber.tag(TAG).d("YAMNet loaded successfully.")
        }

        val vggishFile = File(context.cacheDir, VGGISH_FILENAME)
        if (vggishFile.exists() && vggishInterpreter == null) {
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            vggishInterpreter = Interpreter(vggishFile, options)
            Timber.tag(TAG).d("VGGish loaded successfully.")
        }
    }

    private fun downloadModel(url: String, targetFile: File) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download model: HTTP ${response.code}")
            val body = response.body
            
            val tempFile = File.createTempFile("model_download_", ".tflite", context.cacheDir)
            FileOutputStream(tempFile).use { out ->
                body.byteStream().use { input ->
                    input.copyTo(out)
                }
            }
            if (!tempFile.renameTo(targetFile)) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    throw Exception("Failed to rename temporary model file to target: ${targetFile.absolutePath}")
                }
            }
        }
    }

    /** Classifies Genre and Mood from raw PCM audio using YAMNet. */
    @Synchronized
    fun classifyGenreAndMood(samples: FloatArray, sourceSampleRate: Int): Pair<String?, String?> {
        keepModelsWarm()
        // Models may have been unloaded while idle/under pressure; reload from disk on demand.
        val interpreter = yamnetInterpreter ?: run { runCatching { loadModels() }; yamnetInterpreter } ?: return null to null
        
        // Resample input to 16kHz (YAMNet requirement)
        // Bound input before resampling; a long file otherwise creates a large discarded copy.
        val windowSize = (sourceSampleRate.toLong() * 15).coerceIn(0, samples.size.toLong()).toInt()
        val window = if (samples.size > windowSize) samples.copyOf(windowSize) else samples
        val audio16k = Resampler.resampleLinear(window, sourceSampleRate, 16000)
        if (audio16k.isEmpty()) return null to null

        // YAMNet accepts floating samples. Limit input size to first 15 seconds to avoid memory issues.
        val maxSamples = 16000 * 15
        val inputSamples = if (audio16k.size > maxSamples) audio16k.copyOf(maxSamples) else audio16k

        // YAMNet input shape is [num_samples], output shape is [1, 521] for scores
        val inputBuffer = ByteBuffer.allocateDirect(inputSamples.size * 4).apply {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().put(inputSamples)
        }

        val outputMap = HashMap<Int, Any>()
        val scores = Array(1) { FloatArray(521) }
        outputMap[0] = scores

        try {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)
            val topScoreIndex = scores[0].indices.maxByOrNull { scores[0][it] } ?: -1
            
            if (topScoreIndex != -1 && scores[0][topScoreIndex] > 0.15f) {
                val label = YAMNET_CLASSES.getOrNull(topScoreIndex)
                return mapYamnetClassToGenreMood(label)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error running YAMNet classification")
        }

        return null to null
    }

    /**
     * Extracts a 128-dimensional embedding from raw PCM audio using VGGish log-mel features.
     * Returns null when VGGish isn't loaded or fails: a different kind of vector (the log-mel
     * summary used to be stored as a "fallback") would sit in the same table and make cosine
     * similarity between tracks meaningless.
     */
    @Synchronized
    fun extractEmbedding(samples: FloatArray, sourceSampleRate: Int): FloatArray? {
        keepModelsWarm()
        // Bound input before resampling; a long file otherwise creates a large discarded copy.
        val windowSize = (sourceSampleRate.toLong() * 15).coerceIn(0, samples.size.toLong()).toInt()
        val window = if (samples.size > windowSize) samples.copyOf(windowSize) else samples
        val audio16k = Resampler.resampleLinear(window, sourceSampleRate, 16000)
        if (audio16k.isEmpty()) return null

        val embedding = FloatArray(128)
        val interpreter = vggishInterpreter ?: run { runCatching { loadModels() }; vggishInterpreter } ?: return null

        try {
            run {
                // Compute log-mel spectrogram (96 frames x 64 mel bins)
                val melSpectrogram = computeLogMelSpectrogram(audio16k, 16000, 96, 64)
                val inputBuffer = ByteBuffer.allocateDirect(1 * 96 * 64 * 4).apply {
                    order(ByteOrder.nativeOrder())
                    asFloatBuffer().put(melSpectrogram)
                }

                val outputs = HashMap<Int, Any>()
                val outputScores = Array(1) { FloatArray(128) }
                outputs[0] = outputScores

                interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
                outputScores[0].copyInto(embedding)
            }

            // L2 normalize
            var norm = 0f
            for (v in embedding) norm += v * v
            norm = Math.sqrt(norm.toDouble()).toFloat()
            if (norm > 1e-6f) {
                for (i in embedding.indices) embedding[i] /= norm
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error extracting embedding")
            return null
        }

        return embedding.takeIf { vector -> vector.any { it != 0f } }
    }

    private fun computeLogMelSpectrogram(
        samples: FloatArray,
        sampleRate: Int,
        numFrames: Int,
        numMelBins: Int
    ): FloatArray {
        val windowSize = (sampleRate * 0.025).toInt() // 25ms
        val hopSize = (sampleRate * 0.010).toInt() // 10ms
        val totalNeeded = (numFrames - 1) * hopSize + windowSize
        val audio = if (samples.size < totalNeeded) samples.copyOf(totalNeeded) else samples

        val result = FloatArray(numFrames * numMelBins)
        val frame = FloatArray(windowSize)

        for (f in 0 until numFrames) {
            val offset = f * hopSize
            if (offset + windowSize > audio.size) break
            audio.copyInto(frame, 0, offset, offset + windowSize)
            val mag = com.theveloper.pixelplay.data.analysis.dsp.Fft.magnitudes(frame)

            // Map FFT magnitude to mel bins
            for (m in 0 until numMelBins) {
                var sum = 0f
                val startBin = (m * mag.size / numMelBins).coerceIn(0, mag.size - 1)
                val endBin = ((m + 1) * mag.size / numMelBins).coerceIn(startBin + 1, mag.size)
                for (k in startBin until endBin) {
                    sum += mag[k]
                }
                val logMel = Math.log(1.0 + sum).toFloat()
                result[f * numMelBins + m] = logMel
            }
        }
        return result
    }


    private fun mapYamnetClassToGenreMood(className: String?): Pair<String?, String?> {
        if (className == null) return null to null
        
        val genre = when {
            className.contains("rock", ignoreCase = true) -> "Rock"
            className.contains("pop", ignoreCase = true) -> "Pop"
            className.contains("jazz", ignoreCase = true) -> "Jazz"
            className.contains("hip hop", ignoreCase = true) -> "Hip Hop"
            className.contains("electronic", ignoreCase = true) -> "Electronic"
            className.contains("classical", ignoreCase = true) -> "Classical"
            className.contains("folk", ignoreCase = true) -> "Folk"
            className.contains("country", ignoreCase = true) -> "Country"
            className.contains("blues", ignoreCase = true) -> "Blues"
            className.contains("reggae", ignoreCase = true) -> "Reggae"
            className.contains("metal", ignoreCase = true) -> "Metal"
            className.contains("singing", ignoreCase = true) -> "Vocal"
            else -> null
        }

        val mood = when {
            className.contains("happy", ignoreCase = true) -> "Happy"
            className.contains("sad", ignoreCase = true) -> "Sad"
            className.contains("angry", ignoreCase = true) -> "Energetic"
            className.contains("relax", ignoreCase = true) -> "Relaxed"
            className.contains("calm", ignoreCase = true) -> "Calm"
            className.contains("screaming", ignoreCase = true) -> "Aggressive"
            className.contains("cheering", ignoreCase = true) -> "Upbeat"
            className.contains("silence", ignoreCase = true) -> "Ambient"
            else -> null
        }

        return genre to mood
    }

    companion object {
        private const val TAG = "MusicMlEngine"

        private const val YAMNET_FILENAME = "yamnet.tflite"
        private const val VGGISH_FILENAME = "vggish.tflite"

        private const val YAMNET_URL = "https://storage.googleapis.com/tfhub-lite-models/google/lite-model/yamnet/classification/tflite/1.tflite"
        private const val VGGISH_URL = "https://storage.googleapis.com/tfhub-lite-models/google/lite-model/vggish/tflite/1.tflite"

        // Map class index to name (abbreviated list of common YAMNet classes of interest)
        private val YAMNET_CLASSES = List(521) { index ->
            when (index) {
                0 -> "Speech"
                137 -> "Singing"
                300 -> "Pop music"
                301 -> "Rock music"
                302 -> "Heavy metal"
                304 -> "Hip hop music"
                309 -> "Country"
                311 -> "Jazz"
                314 -> "Classical music"
                317 -> "Electronic music"
                326 -> "Reggae"
                else -> "Other"
            }
        }
    }
}
