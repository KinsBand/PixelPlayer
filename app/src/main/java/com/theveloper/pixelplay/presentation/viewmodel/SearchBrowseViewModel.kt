package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.youtube.InnerTubeClient
import com.theveloper.pixelplay.data.youtube.YouTubeMusicApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchBrowseState(val songs: List<Song> = emptyList(), val loading: Boolean = false,
    val error: String? = null, val continuation: String? = null)

@HiltViewModel
class SearchBrowseViewModel @Inject constructor(
    private val client: InnerTubeClient,
    private val api: YouTubeMusicApiService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchBrowseState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var currentId: String? = null
    private val visited = mutableSetOf<String>()

    fun open(id: String) {
        request?.cancel()
        currentId = id
        visited.clear()
        mutableState.value = SearchBrowseState()
        loadMore()
    }

    fun close() { request?.cancel(); currentId = null }

    fun loadMore() {
        val id = currentId ?: return
        if (mutableState.value.loading) return
        val before = mutableState.value
        mutableState.value = before.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val page = client.browse(id, before.continuation)
                val hydrated = api.hydrate(page.songs)
                if (currentId != id) return@launch
                before.continuation?.let(visited::add)
                mutableState.value = SearchBrowseState((before.songs + hydrated).distinctBy { it.id },
                    continuation = page.continuation?.takeUnless { it in visited })
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (currentId == id) mutableState.value = before.copy(error = "Couldn't load tracks. Check your connection and try again.")
            }
        }
    }
}
