package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.deezer.DeezerApiService
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import com.theveloper.pixelplay.presentation.navigation.RouteRequests
import com.theveloper.pixelplay.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The artist picker for songs with two or more artists: pictures for artists that have none,
 * and the combined "collab" page, every song (library + online) credited to all of them.
 */
@HiltViewModel
class CollabArtistsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val youTube: YouTubeRepository,
    private val deezer: DeezerApiService,
    private val connectivity: ConnectivityStateHolder,
) : ViewModel() {
    private val _images = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Pictures found online, by normalised artist name. */
    val images: StateFlow<Map<String, String>> = _images.asStateFlow()

    private val _openingCollab = MutableStateFlow(false)
    val openingCollab: StateFlow<Boolean> = _openingCollab.asStateFlow()

    /** Fetches a picture (Deezer) for each artist that has none. */
    fun loadMissingImages(artists: List<Artist>) {
        if (!connectivity.isOnline.value) return
        artists.filter { it.effectiveImageUrl.isNullOrBlank() }.forEach { artist ->
            val key = normalizeMixQuery(artist.name)
            if (key.isBlank() || _images.value.containsKey(key)) return@forEach
            viewModelScope.launch {
                val url = try {
                    withTimeoutOrNull(5_000) { deezer.searchArtist(artist.name, limit = 1) }
                        ?.data?.firstOrNull()?.let { it.pictureMedium ?: it.picture }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (url != null) _images.update { it + (key to url) }
            }
        }
    }

    /** Builds the collab page for [artists] (starting with [song]) and opens it. */
    fun openCollab(song: Song, artists: List<Artist>, onOpened: () -> Unit) {
        if (_openingCollab.value || artists.size < 2) return
        _openingCollab.value = true
        viewModelScope.launch {
            try {
                val names = artists.map { it.name }
                val songs = collabSongs(song, names)
                val title = names.joinToString(" × ")
                val playlist = Playlist(
                    // Route-safe id (names can have spaces and symbols).
                    id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}collab_" +
                        names.joinToString("|") { normalizeMixQuery(it) }.hashCode().toUInt().toString(16),
                    name = title,
                    songIds = songs.map { it.id }
                )
                PlaylistViewModel.registerTransientPlaylist(playlist, songs)
                onOpened()
                RouteRequests.open(Screen.PlaylistDetail.createRoute(playlist.id))
            } finally {
                _openingCollab.value = false
            }
        }
    }

    /** Every song credited to all of [names]: the library first, then online (if connected). */
    private suspend fun collabSongs(current: Song, names: List<String>): List<Song> {
        val keys = names.map { normalizeMixQuery(it) }.filter { it.isNotBlank() }
        fun creditsAll(song: Song): Boolean {
            val credited = buildString {
                append(normalizeMixQuery(song.artist)).append(' ')
                append(normalizeMixQuery(song.displayArtist)).append(' ')
                song.artists.forEach { append(normalizeMixQuery(it.name)).append(' ') }
                append(normalizeMixQuery(song.title)) // "(feat. X)" in the title
            }
            return keys.all { credited.contains(it) }
        }
        val library = try {
            musicRepository.getAllSongsOnce().filter(::creditsAll)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val online = if (!connectivity.isOnline.value) emptyList() else try {
            withTimeoutOrNull(8_000) { youTube.searchSongs(names.joinToString(" ")) }.orEmpty().filter(::creditsAll)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val seen = HashSet<String>()
        return (listOf(current) + library + online).filter { song ->
            seen.add(song.id) && seen.add("t:" + normalizeMixQuery(song.title) + "|" + normalizeMixQuery(song.displayArtist))
        }
    }
}
