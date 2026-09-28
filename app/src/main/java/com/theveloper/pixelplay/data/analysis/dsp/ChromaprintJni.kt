package com.theveloper.pixelplay.data.analysis.dsp

import timber.log.Timber

object ChromaprintJni {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("chromaprint_jni")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Timber.tag("ChromaprintJni").w("Native chromaprint_jni library not found. Fingerprinting unavailable.")
        }
    }

    /**
     * Generates a Chromaprint fingerprint string from raw PCM samples.
     * Returns null when the native implementation is unavailable. Synthetic hashes are
     * not Chromaprint fingerprints and must never be submitted to AcoustID.
     */
    fun generateFingerprint(samples: FloatArray, sampleRate: Int): String? {
        if (samples.isEmpty() || sampleRate <= 0 || samples.any { !it.isFinite() }) return null
        if (isLoaded) {
            try {
                return nativeGenerateFingerprint(samples, sampleRate)?.takeIf { it.isNotBlank() }
            } catch (e: LinkageError) {
                isLoaded = false
                Timber.tag("ChromaprintJni").w(e, "Native fingerprinting unavailable")
            } catch (e: Exception) {
                Timber.tag("ChromaprintJni").e(e, "Error executing native generateFingerprint")
            }
        }
        
        return null
    }

    private external fun nativeGenerateFingerprint(samples: FloatArray, sampleRate: Int): String?
}
