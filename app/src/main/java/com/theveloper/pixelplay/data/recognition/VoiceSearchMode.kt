package com.theveloper.pixelplay.data.recognition

/**
 * The two unified voice and audio search modes:
 * - [HUM_AND_SING]: User vocal performance (humming melodies or singing words) routed to Google Hum to Search.
 * - [LISTEN_AND_NOW_PLAYING]: Ambient music recognition (Google Sound Search) combined with Pixel Now Playing history and voice text search.
 */
enum class VoiceSearchMode(val displayName: String) {
    /** Humming melodies or singing lyrics, powered by Google Hum to Search. */
    HUM_AND_SING("Hum & Sing"),

    /** Music playing nearby (Google Sound Search) and Pixel Now Playing ambient detections. */
    LISTEN_AND_NOW_PLAYING("Listen & Now Playing");

    val isHumOrSing: Boolean get() = this == HUM_AND_SING
    val isListenOrNowPlaying: Boolean get() = this == LISTEN_AND_NOW_PLAYING
}
