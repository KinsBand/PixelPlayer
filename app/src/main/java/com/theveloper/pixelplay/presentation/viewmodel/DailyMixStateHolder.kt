package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.CloudSongEntity
import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.model.DownloadState
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Daily Mix and Your Mix state.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Generate and update daily/your mixes
 * - Persist and restore mix state
 * - Check if mix needs updating based on day change
 */
@Singleton
class DailyMixStateHolder @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val adaptiveMix: com.theveloper.pixelplay.data.AdaptiveMix,
    private val musicRepository: MusicRepository,
    private val cloudSongDao: CloudSongDao,
    private val connectedLibraryRepository: ConnectedLibraryRepository,
    private val youtubeRepository: YouTubeRepository
) {
    var discoveryBalance: Double
        get() = adaptiveMix.discoveryBalance
        set(value) { adaptiveMix.discoveryBalance = value }
    var variety: Double
        get() = adaptiveMix.variety
        set(value) { adaptiveMix.variety = value }
    suspend fun qualityReport() = adaptiveMix.qualityReport()
    var energyTarget: Double?
        get() = adaptiveMix.energyTarget
        set(value) { adaptiveMix.energyTarget = value }
    val mixInsights get() = adaptiveMix.insights
    suspend fun analysed(songs: List<Song>) = adaptiveMix.analysed(songs)
    fun heardTooMuch(song: Song) = adaptiveMix.heardTooMuch(song)
    fun moreLike(song: Song) = adaptiveMix.moreLike(song)
    suspend fun resetLearning() = adaptiveMix.resetLearning()
    fun resetFeedback() = adaptiveMix.resetFeedback()
    fun decisionSummary() = adaptiveMix.decisionSummary()
    suspend fun attributeMixSong(song: Song, item: androidx.media3.common.MediaItem, revision: Long) = adaptiveMix.attribute(song, item, revision)
    fun activateMix(mixId: String, keepPrompt: Boolean = false, keepVibe: Boolean = false) =
        adaptiveMix.activate(mixId, keepPrompt, keepVibe)
    fun lockVibe(filter: com.theveloper.pixelplay.presentation.library.VibeFilter) = adaptiveMix.lockVibe(filter)
    fun clearVibe() = adaptiveMix.clearVibe()
    val lockedVibe get() = adaptiveMix.vibe
    val feedbackLog get() = adaptiveMix.feedbackLog
    fun undoFeedback(id: String) = adaptiveMix.undoFeedback(id)
    suspend fun personalAdjustments(songs: List<Song>) = adaptiveMix.personalAdjustments(songs)
    suspend fun resolveMixPrompt(query: String) = adaptiveMix.resolvePrompt(query)
    fun steerMixTowards(songs: List<Song>) = adaptiveMix.steerTowards(songs)
    fun clearMixPromptSteer() = adaptiveMix.clearPromptSteer()
    fun deactivateMix() = adaptiveMix.deactivate()
    fun undoMixFeedback() = adaptiveMix.undo()
    fun dislikeEverywhere(song: Song) = adaptiveMix.dislikeEverywhere(song)
    val feedbackRevision get() = adaptiveMix.revision

    fun dislike(song: Song) {
        adaptiveMix.dislike(song)
        removeFromDailyMix(song.id)
        _yourMixSongs.update { songs -> songs.filterNot(adaptiveMix::isDisliked).toImmutableList() }
    }
    fun isDisliked(song: Song) = adaptiveMix.isDisliked(song)

    suspend fun nextMixBatch(flavor: com.theveloper.pixelplay.data.MixFlavor, seeds: List<Song>, excluded: Set<String>, limit: Int = 12) =
        adaptiveMix.next(flavor, seeds, excluded, limit = limit)
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    /**
     * Initialize with coroutine scope from ViewModel.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    /**
     * Remove a song from the daily mix.
     */
    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            currentList.filterNot { it.id == songId }.toImmutableList()
        }
    }

    /**
     * Aggregates local songs, cloud songs, connected library songs, and online recommendations.
     */
    private suspend fun getAllCandidateSongs(favoriteIds: Set<String>): List<Song> {
        val localSongs = musicRepository.getAllSongsOnce()
        val cloudEntities = try { cloudSongDao.getAllOnce() } catch (e: Exception) { emptyList() }
        val cloudSongs = cloudEntities.map { cloud ->
            Song(
                id = cloud.id,
                title = cloud.title,
                artist = cloud.artist,
                artistId = cloud.artist.hashCode().toLong(),
                album = cloud.album ?: "YouTube Music",
                albumId = (cloud.album ?: "YouTube Music").hashCode().toLong(),
                path = cloud.localFilePath ?: "",
                contentUriString = cloud.contentUriString,
                albumArtUriString = cloud.thumbnailUrl,
                duration = cloud.duration,
                youtubeId = cloud.youtubeId,
                downloadState = if (cloud.isDownloaded) DownloadState.DOWNLOADED else DownloadState.NOT_DOWNLOADED,
                isFavorite = favoriteIds.contains(cloud.id)
            )
        }

        val snapshot = connectedLibraryRepository.snapshot.value
        val connectedSongs = snapshot.spotifyLikes + snapshot.youtubeLikes + snapshot.playlists.flatMap { it.songs }

        val discoveredOnlineSongs = mutableListOf<Song>()
        val engagement = dailyMixManager.getAllEngagementStats()
        val seeds = (localSongs + cloudSongs + connectedSongs).distinctBy { it.id }
            .sortedWith(compareByDescending<Song> { engagement[it.id]?.lastPlayedTimestamp ?: 0L }
                .thenByDescending { it.id in favoriteIds })
            .filter { (engagement[it.id]?.totalPlayDurationMs ?: 0L) >= 30_000L || it.id in favoriteIds }
            .distinctBy { it.artist.lowercase() }.take(6)
        if (seeds.isNotEmpty()) {
            try {
                seeds.mapNotNull { it.youtubeId }.distinct().take(2).forEach { id ->
                    discoveredOnlineSongs += youtubeRepository.relatedSongs(id)
                }
                val topArtists = seeds.map { it.artist.trim() }
                    .filter { it.isNotBlank() && it != "Unknown Artist" }
                    .distinct().take(2)
                for (artist in topArtists) {
                    discoveredOnlineSongs += youtubeRepository.searchSongs("$artist songs").take(5)
                }
            } catch (e: Exception) {
                // Ignore network issues, proceed with available local & cloud tracks
            }
        }

        return (localSongs + cloudSongs + connectedSongs + discoveredOnlineSongs)
            .filter { it.title.isNotBlank() && (it.contentUriString.isNotBlank() || it.youtubeId != null) }
            .distinctBy { it.id }
    }

    /**
     * Update the daily mix with new songs.
     * Combines local tracks with online/cloud tracks.
     */
    fun updateDailyMix(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            val favoriteIds = favoriteSongIdsFlow.first()
            val allSongs = getAllCandidateSongs(favoriteIds)
            if (allSongs.isNotEmpty()) {
                // Generate daily mix
                val mix = dailyMixManager.generateDailyMix(allSongs, favoriteIds)
                _dailyMixSongs.value = mix.toImmutableList()
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

                // Generate your mix
                val yourMix = dailyMixManager.generateYourMix(allSongs, favoriteIds)
                _yourMixSongs.value = yourMix.toImmutableList()
                userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })

                // Persist picked online tracks to CloudSongDao so Room can resolve them across reboots
                val onlineSongsToPersist = (mix + yourMix).filter { !it.isLocal || it.youtubeId != null }
                if (onlineSongsToPersist.isNotEmpty()) {
                    val entities = onlineSongsToPersist.map { song ->
                        CloudSongEntity(
                            id = song.id,
                            title = song.title,
                            artist = song.artist,
                            album = song.album,
                            duration = song.duration,
                            thumbnailUrl = song.albumArtUriString,
                            youtubeId = song.youtubeId ?: song.id.takeIf { it.startsWith("yt_") }?.removePrefix("yt_"),
                            sourceType = if (song.id.startsWith("spotify_")) "spotify" else "youtube",
                            contentUriString = song.contentUriString.ifBlank { "youtube://${song.youtubeId ?: song.id.removePrefix("yt_")}" },
                            dateAdded = System.currentTimeMillis(),
                            isDownloaded = song.downloadState == DownloadState.DOWNLOADED
                        )
                    }
                    try {
                        cloudSongDao.upsertAll(entities)
                    } catch (e: Exception) {
                        // Ignore DB save errors
                    }
                }
            } else {
                _yourMixSongs.value = persistentListOf()
            }
        }
    }

    /**
     * Load persisted daily mix from storage using direct DB queries by IDs
     * with connected library fallback.
     */
    fun loadPersistedDailyMix() {
        // Load Daily Mix
        scope?.launch {
            val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
            if (dailyMixIds.isNotEmpty() && _dailyMixSongs.value.isEmpty()) {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(dailyMixIds).first()
                }
                val songMap = songs.associateBy { it.id }.toMutableMap()
                val missingIds = dailyMixIds.filterNot { songMap.containsKey(it) }
                if (missingIds.isNotEmpty()) {
                    val snapshot = connectedLibraryRepository.snapshot.value
                    val connectedSongs = (snapshot.spotifyLikes + snapshot.youtubeLikes + snapshot.playlists.flatMap { it.songs })
                        .associateBy { it.id }
                    songMap.putAll(connectedSongs)
                }
                if (songMap.isNotEmpty()) {
                    val orderedSongs = dailyMixIds.mapNotNull { songMap[it] }
                    _dailyMixSongs.value = orderedSongs.toImmutableList()
                }
            }
        }

        // Load Your Mix
        scope?.launch {
            val yourMixIds = userPreferencesRepository.yourMixSongIdsFlow.first()
            if (yourMixIds.isNotEmpty() && _yourMixSongs.value.isEmpty()) {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(yourMixIds).first()
                }
                val songMap = songs.associateBy { it.id }.toMutableMap()
                val missingIds = yourMixIds.filterNot { songMap.containsKey(it) }
                if (missingIds.isNotEmpty()) {
                    val snapshot = connectedLibraryRepository.snapshot.value
                    val connectedSongs = (snapshot.spotifyLikes + snapshot.youtubeLikes + snapshot.playlists.flatMap { it.songs })
                        .associateBy { it.id }
                    songMap.putAll(connectedSongs)
                }
                if (songMap.isNotEmpty()) {
                    val orderedSongs = yourMixIds.mapNotNull { songMap[it] }
                    _yourMixSongs.value = orderedSongs.toImmutableList()
                }
            }
        }
    }

    /**
     * Force update the daily mix regardless of day.
     */
    fun forceUpdate(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            updateDailyMix(favoriteSongIdsFlow)
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Check if daily mix needs updating (new day) and update if so.
     */
    fun checkAndUpdateIfNeeded(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val lastUpdateDay = Calendar.getInstance().apply {
                timeInMillis = lastUpdate
            }.get(Calendar.DAY_OF_YEAR)

            if (today != lastUpdateDay) {
                updateDailyMix(favoriteSongIdsFlow)
                userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
            }
        }
    }

    /**
     * Set the daily mix songs directly (used for AI-generated mixes).
     */
    fun setDailyMixSongs(songs: List<Song>) {
        _dailyMixSongs.value = songs.toImmutableList()
        scope?.launch {
            userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
        }
    }

    /**
     * Get a candidate pool for AI playlist generation.
     */
    suspend fun getCandidatePool(
        allSongs: List<Song>,
        favoriteIds: Set<String>,
        maxSize: Int = 100
    ): List<Song> {
        return dailyMixManager.generateDailyMix(allSongs, favoriteIds, maxSize)
    }

    fun onCleared() {
        updateJob?.cancel()
        scope = null
    }
}
