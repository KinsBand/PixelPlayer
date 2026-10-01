package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.recognition.RecentlyHeardEntry
import com.theveloper.pixelplay.data.recognition.RecentlyHeardRepository
import com.theveloper.pixelplay.data.recognition.VoiceSearchResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "Recently heard" card on Search and the Recently heard screen. */
@HiltViewModel
class RecentlyHeardViewModel @Inject constructor(
    private val repository: RecentlyHeardRepository,
    private val resolver: VoiceSearchResolver
) : ViewModel() {

    val entries: StateFlow<List<RecentlyHeardEntry>> = repository.entries

    /** Songs matched this session, by entry key, so repeat taps don't search again. */
    private val resolved = mutableMapOf<String, Song>()

    /** Entry id currently being looked up (a spinner shows on that row). */
    private val _resolvingId = MutableStateFlow<String?>(null)
    val resolvingId: StateFlow<String?> = _resolvingId.asStateFlow()

    /**
     * Finds the real song for [entry] (library first, then online) and hands it to [onSong];
     * [onMissing] when neither has it.
     */
    fun withSong(entry: RecentlyHeardEntry, onMissing: () -> Unit = {}, onSong: (Song) -> Unit) {
        resolved[entry.key]?.let { onSong(it); return }
        viewModelScope.launch {
            _resolvingId.value = entry.id
            val song = try {
                resolver.findSong(entry.title, entry.artist)
            } catch (_: Exception) {
                null
            } finally {
                if (_resolvingId.value == entry.id) _resolvingId.value = null
            }
            if (song == null) {
                onMissing()
            } else {
                resolved[entry.key] = song
                repository.attachSong(entry.id, song.id, song.albumArtUriString)
                onSong(song)
            }
        }
    }

    fun remove(entry: RecentlyHeardEntry) = repository.remove(entry.id)
}
