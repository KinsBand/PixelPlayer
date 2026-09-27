package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.repository.MusicRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchStateHolderLatencyTest {
    @Test fun `changing query or filter immediately clears obsolete results`() = runTest {
        val repository = mockk<MusicRepository>()
        val song = com.theveloper.pixelplay.data.model.SearchResultItem.SongItem(
            com.theveloper.pixelplay.data.model.Song.emptySong().copy(id = "old"))
        every { repository.searchAll(any(), any()) } returns flow { emit(listOf(song)); awaitCancellation() }
        val holder = SearchStateHolder(repository)
        holder.initialize(backgroundScope)
        holder.performSearch("old")
        runCurrent()
        org.junit.jupiter.api.Assertions.assertEquals(listOf(song), holder.searchResults.value)
        holder.performSearch("new")
        org.junit.jupiter.api.Assertions.assertTrue(holder.searchResults.value.isEmpty())
        runCurrent()
        holder.updateSearchFilter(SearchFilterType.ARTISTS)
        org.junit.jupiter.api.Assertions.assertTrue(holder.searchResults.value.isEmpty())
        runCurrent()
        verify { repository.searchAll("new", SearchFilterType.ARTISTS) }
        holder.onCleared()
    }
    @Test fun `first query is not dropped and repository collection has no debounce`() = runTest {
        val repository = mockk<MusicRepository>()
        every { repository.searchAll(any(), any()) } returns flow { emit(emptyList()); awaitCancellation() }
        val holder = SearchStateHolder(repository)
        holder.initialize(backgroundScope)
        holder.performSearch("hello") // Before the launched collector has started.
        runCurrent()
        verify(exactly = 1) { repository.searchAll("hello", SearchFilterType.ALL) }
        holder.performSearch("world")
        runCurrent()
        verify(exactly = 1) { repository.searchAll("world", SearchFilterType.ALL) }
        holder.updateSearchFilter(SearchFilterType.SONGS)
        runCurrent()
        verify(exactly = 1) { repository.searchAll("world", SearchFilterType.SONGS) }
        holder.onCleared()
    }
}
