package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.metadata.*
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongMetadataViewModel @Inject constructor(private val store: SongMetadataStore) : ViewModel() {
    val document = MutableStateFlow<SongMetadataDocument?>(null)
    val error = MutableStateFlow<String?>(null)
    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = e.message ?: "Metadata could not be saved." }
    }
    fun load(song: Song) = run { document.value = store.read(song) }
    fun save(song: Song, key: String, value: String) = run { document.value = store.setManual(song, key, value) }
}
