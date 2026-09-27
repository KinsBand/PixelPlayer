package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.spotify.SpotifyToYouTubeResolver
import com.theveloper.pixelplay.data.youtube.SongDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectedLibraryViewModel @Inject constructor(val library: ConnectedLibraryRepository,
    private val downloads: SongDownloadManager, private val resolver: SpotifyToYouTubeResolver) : ViewModel() {
    val likes = library.likedSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteIds = library.favoriteIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val notice = MutableStateFlow<String?>(null)
    val downloading = MutableStateFlow<Set<String>>(emptySet())
    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { notice.value = e.message ?: "Please try again." }
    }
    fun refresh() = run { library.sync() }
    fun rename(id: String, name: String) = run { library.renameFriend(id, name) }
    fun add(name: String, link: String, done: () -> Unit) = run { library.addFriendPlaylist(name, link); done() }
    fun download(song: Song) {
        if (song.id in downloading.value) return
        downloading.update { it + song.id }
        run {
            try {
                val downloadable = if (com.theveloper.pixelplay.data.accounts.CatalogTracks.isCatalogUri(song.contentUriString)) {
                    val id = resolver.resolveSpotifyTrackToVideoId(com.theveloper.pixelplay.data.accounts.CatalogTracks.matchKey(song.id), song.title, song.artist, song.duration.toInt(), song.creditsAndRelease.isrc)
                        ?: error("No matching audio was found for this track.")
                    song.copy(youtubeId = id)
                } else song
                downloads.downloadSong(downloadable).getOrThrow()
                notice.value = "Downloaded ${song.title}"
            } finally { downloading.update { it - song.id } }
        }
    }
}

