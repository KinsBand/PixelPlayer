package com.theveloper.pixelplay.data.analysis.dsp

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChromaprintJniTest {
    @Test fun `missing Android native library cannot produce a simulated AcoustID fingerprint`() {
        assertNull(ChromaprintJni.generateFingerprint(FloatArray(4096) { 0.1f }, 44_100))
    }
    @Test fun `invalid PCM is rejected`() {
        assertNull(ChromaprintJni.generateFingerprint(floatArrayOf(), 44_100))
        assertNull(ChromaprintJni.generateFingerprint(floatArrayOf(Float.NaN), 44_100))
        assertNull(ChromaprintJni.generateFingerprint(floatArrayOf(1f), 0))
    }
}
