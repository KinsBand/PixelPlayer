package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class SearchFilterType {
    ALL,
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
    /** Spotify people (and friends) by name, username, profile link or spotify:user: URI. */
    PROFILES
}
