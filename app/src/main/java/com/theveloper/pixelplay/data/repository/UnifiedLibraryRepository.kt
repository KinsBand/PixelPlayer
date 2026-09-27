package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedLibraryRepository @Inject constructor(private val musicRepository: MusicRepository,
    private val connected: ConnectedLibraryRepository) {
    val combinedLikedSongsFlow: Flow<List<Song>> = connected.likedSongs
    val combinedRecentlyPlayedFlow: Flow<List<Song>> = combine(musicRepository.getHomeMixPreviewSongs(20), combinedLikedSongsFlow) { recent, liked ->
        (recent + liked.take(20)).distinctBy { it.id }
    }
    suspend fun getUnifiedPlaylists(): List<Pair<String, List<Song>>> {
        connected.sync()
        return connected.snapshot.value.playlists.map { it.title to it.songs }
    }
}
