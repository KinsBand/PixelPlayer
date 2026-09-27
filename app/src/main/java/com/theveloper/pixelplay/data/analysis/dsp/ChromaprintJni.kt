package com.theveloper.pixelplay.data.analysis.dsp

import timber.log.Timber

object ChromaprintJni {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("chromaprint_jni")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Timber.tag("ChromaprintJni").w("Native chromaprint_jni library not found. Using fallback fingerprinting.")
        }
    }

    /**
     * Generates a Chromaprint fingerprint string from raw PCM samples.
     * Falls back to a content-hash simulation if the native library is unavailable.
     */
    fun generateFingerprint(samples: FloatArray, sampleRate: Int): String? {
        if (isLoaded) {
            try {
                return nativeGenerateFingerprint(samples, sampleRate)
            } catch (e: Exception) {
                Timber.tag("ChromaprintJni").e(e, "Error executing native generateFingerprint")
            }
        }
        
        // Fallback: Generate a deterministic mock fingerprint based on samples structure
        if (samples.isEmpty()) return null
        val hash = samples.take(1000).fold(0) { acc, next -> (acc * 31 + next.hashCode()) }
        return "AQAAAA${hash.toString(16).padStart(8, '0')}"
    }

    private external fun nativeGenerateFingerprint(samples: FloatArray, sampleRate: Int): String?
}
