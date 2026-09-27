package com.theveloper.pixelplay.data.model

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants

enum class VideoType(val displayName: String, val searchQuerySuffix: String) {
    MUSIC_VIDEO("Music Video", "official music video"),
    LIVE_PERFORMANCE("Live Performance", "live performance"),
    LYRIC_VIDEO("Lyric Video", "lyric video"),
    ACOUSTIC("Acoustic", "acoustic version"),
    REMIX("Remix", "remix"),
    COVER("Covers", "cover"),
    ALL("All versions", "")
}

data class TrackVideo(val id: String, val title: String, val channel: String, val thumbnailUrl: String)

// setPlaybackQuality is a no-op in the IFrame API. Playback speed IS supported.
enum class PlaybackSpeed(val displayName: String, val rate: PlayerConstants.PlaybackRate) {
    RATE_0_5("0.5x", PlayerConstants.PlaybackRate.RATE_0_5),
    RATE_1("1x", PlayerConstants.PlaybackRate.RATE_1),
    RATE_1_5("1.5x", PlayerConstants.PlaybackRate.RATE_1_5),
    RATE_2("2x", PlayerConstants.PlaybackRate.RATE_2)
}

sealed interface VideoPlayerState {
    data object Loading : VideoPlayerState
    data class Ready(val videoId: String) : VideoPlayerState
    data class Error(val message: String) : VideoPlayerState
}
