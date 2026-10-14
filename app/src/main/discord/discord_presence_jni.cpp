// PixelPlayer ↔ Discord Social SDK bridge (Rich Presence, unauthenticated path: the status is
// handed to the Discord app installed on the phone). Called from
// com.theveloper.pixelplay.data.presence.DiscordSocialSdkBridge, always on one thread.
#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

#include <android/log.h>
#include <jni.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <optional>
#include <string>

#define LOG_TAG "PixelDiscord"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {
std::shared_ptr<discordpp::Client> gClient;
std::mutex gErrorMutex;
std::string gLastError;

std::optional<std::string> toString(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::nullopt;
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::nullopt;
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

void setError(const std::string& message) {
    std::lock_guard<std::mutex> lock(gErrorMutex);
    gLastError = message;
}
}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_theveloper_pixelplay_data_presence_DiscordSocialSdkBridge_nativeInit(JNIEnv*, jobject, jlong applicationId) {
    gClient = std::make_shared<discordpp::Client>();
    gClient->SetApplicationId(static_cast<uint64_t>(applicationId));
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_theveloper_pixelplay_data_presence_DiscordSocialSdkBridge_nativeUpdate(
    JNIEnv* env, jobject, jboolean listening, jstring details, jstring state,
    jlong startMs, jlong endMs, jstring largeImage, jstring largeText) {
    if (!gClient) return;
    discordpp::Activity activity;
    activity.SetType(listening ? discordpp::ActivityTypes::Listening : discordpp::ActivityTypes::Playing);
    if (auto d = toString(env, details)) activity.SetDetails(*d);
    if (auto s = toString(env, state)) activity.SetState(*s);
    // Member list / profile show the song title rather than just the app name.
    activity.SetStatusDisplayType(discordpp::StatusDisplayTypes::Details);

    discordpp::ActivityTimestamps timestamps;
    if (startMs > 0) timestamps.SetStart(static_cast<uint64_t>(startMs));
    if (endMs > 0) timestamps.SetEnd(static_cast<uint64_t>(endMs));
    activity.SetTimestamps(timestamps);

    auto image = toString(env, largeImage);
    auto text = toString(env, largeText);
    if (image || text) {
        discordpp::ActivityAssets assets;
        if (image) assets.SetLargeImage(*image);
        if (text) assets.SetLargeText(*text);
        activity.SetAssets(assets);
    }

    gClient->UpdateRichPresence(activity, [](discordpp::ClientResult result) {
        if (!result.Successful()) {
            LOGW("UpdateRichPresence failed");
            setError(result.Error());
        }
    });
}

JNIEXPORT void JNICALL
Java_com_theveloper_pixelplay_data_presence_DiscordSocialSdkBridge_nativeClear(JNIEnv*, jobject) {
    if (!gClient) return;
    gClient->ClearRichPresence();
}

JNIEXPORT void JNICALL
Java_com_theveloper_pixelplay_data_presence_DiscordSocialSdkBridge_nativeRunCallbacks(JNIEnv*, jobject) {
    discordpp::RunCallbacks();
}

JNIEXPORT jstring JNICALL
Java_com_theveloper_pixelplay_data_presence_DiscordSocialSdkBridge_nativeTakeError(JNIEnv* env, jobject) {
    std::string message;
    {
        std::lock_guard<std::mutex> lock(gErrorMutex);
        message.swap(gLastError);
    }
    if (message.empty()) return nullptr;
    return env->NewStringUTF(message.c_str());
}

JNIEXPORT void JNICALL
Java_com_theveloper_pixelplay_data_presence_DiscordSocialSdkBridge_nativeShutdown(JNIEnv*, jobject) {
    gClient.reset();
}

}  // extern "C"
