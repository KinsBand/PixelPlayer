package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.library.LikedSongsRepository
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The shared liked list for screens that only need to show it (Home, the Playlists tab). */
@HiltViewModel
class LikedSongsViewModel @Inject constructor(repository: LikedSongsRepository) : ViewModel() {
    /** Every liked song, newest like first. */
    val likedSongs: StateFlow<List<Song>> = repository.likedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The newest [RECENT_LIMIT] likes, for Home's Recently Liked row. */
    val recentlyLiked: StateFlow<List<Song>> = repository.likedSongs
        .map { it.take(RECENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until the list has loaded once. */
    val likedCount: StateFlow<Int?> = repository.likedSongs
        .map<List<Song>, Int?> { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object { const val RECENT_LIMIT = 30 }
}
