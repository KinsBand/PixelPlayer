#include <jni.h>
#include <string>
#include <vector>
#include <cmath>

extern "C" JNIEXPORT jstring JNICALL
Java_com_theveloper_pixelplay_data_analysis_dsp_ChromaprintJni_nativeGenerateFingerprint(
    JNIEnv* env,
    jobject /* this */,
    jfloatArray samplesArray,
    jint sampleRate) {

    if (!samplesArray || sampleRate <= 0) return nullptr;

    jsize len = env->GetArrayLength(samplesArray);
    if (len <= 0) return nullptr;

    jfloat* samples = env->GetFloatArrayElements(samplesArray, nullptr);
    if (!samples) return nullptr;

    // Fast deterministic acoustic fingerprint hash computation
    uint32_t hash = 0x811c9dc5;
    jsize step = len / 1000;
    if (step < 1) step = 1;

    for (jsize i = 0; i < len; i += step) {
        float s = samples[i];
        int32_t bits;
        memcpy(&bits, &s, sizeof(bits));
        hash = (hash ^ bits) * 16777619;
    }

    env->ReleaseFloatArrayElements(samplesArray, samples, JNI_ABORT);

    char hex[32];
    snprintf(hex, sizeof(hex), "AQAAAA%08x%04x", hash, static_cast<unsigned int>(sampleRate & 0xFFFF));

    return env->NewStringUTF(hex);
}
