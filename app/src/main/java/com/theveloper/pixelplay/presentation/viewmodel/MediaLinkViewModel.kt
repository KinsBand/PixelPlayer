package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.search.LinkResolution
import com.theveloper.pixelplay.data.search.MediaLinkResolver
import com.theveloper.pixelplay.data.search.ParsedMediaUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A Spotify / YouTube Music link pasted into Search: being read, read, or not readable. */
sealed interface MediaLinkUiState {
    data object None : MediaLinkUiState
    data class Resolving(val link: ParsedMediaUrl) : MediaLinkUiState
    data class Resolved(val link: ParsedMediaUrl, val result: LinkResolution) : MediaLinkUiState
    data class Failed(val link: ParsedMediaUrl) : MediaLinkUiState
}

@HiltViewModel
class MediaLinkViewModel @Inject constructor(
    private val resolver: MediaLinkResolver,
) : ViewModel() {
    private val _state = MutableStateFlow<MediaLinkUiState>(MediaLinkUiState.None)
    val state: StateFlow<MediaLinkUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun resolve(link: ParsedMediaUrl) {
        val current = _state.value
        if (current is MediaLinkUiState.Resolving && current.link == link) return
        if (current is MediaLinkUiState.Resolved && current.link == link) return
        job?.cancel()
        _state.value = MediaLinkUiState.Resolving(link)
        job = viewModelScope.launch {
            val result = resolver.resolve(link)
            _state.value = if (result != null) MediaLinkUiState.Resolved(link, result) else MediaLinkUiState.Failed(link)
        }
    }

    /** The search box no longer holds the link or its resolved search: drop the card. */
    fun clear() {
        job?.cancel()
        _state.value = MediaLinkUiState.None
    }
}
