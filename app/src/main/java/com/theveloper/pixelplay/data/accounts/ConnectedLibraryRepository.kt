package com.theveloper.pixelplay.data.accounts

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.google.gson.Gson
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.spotify.*
import com.theveloper.pixelplay.data.ytmusic.*
import com.theveloper.pixelplay.data.applemusic.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Edits the user made inside PixelPlayer. They are kept as an overlay on top of whatever the
 * service returns, so they survive stale or partial sync responses, and are pushed back to
 * YouTube Music until the service confirms them. Nullable fields: Gson ignores Kotlin defaults.
 */
data class PlaylistEdits(val added: List<Song>? = null, val removed: List<String>? = null,
    val order: List<String>? = null, val title: String? = null) {
    val isEmpty get() = added.isNullOrEmpty() && removed.isNullOrEmpty() && order == null && title == null
}

data class ConnectedPlaylist(val id: String, val remoteId: String, val source: String, val title: String,
    val coverUrl: String? = null, val songs: List<Song> = emptyList(), val friendId: String? = null,
    val ownerName: String = "", val error: String? = null, val manual: Boolean = false,
    /** Local edit overlay; [songs] and [title] already include it. */
    val edits: PlaylistEdits? = null,
    /** What the service last returned, kept only while [edits] exist. */
    val remoteSongs: List<Song>? = null, val remoteTitle: String? = null,
    /** YouTube Music playlist row ids aligned with the remote song list (needed to remove/move). */
    val setVideoIds: List<String?>? = null) {
    val remote: List<Song> get() = remoteSongs ?: songs
    val isEditable: Boolean get() = source == "YOUTUBE_MUSIC"
}
data class ConnectedSnapshot(val playlists: List<ConnectedPlaylist> = emptyList(),
    val spotifyLikes: List<Song> = emptyList(), val youtubeLikes: List<Song> = emptyList(),
    /** Apple Music favourites. Nullable: Gson leaves it null in snapshots saved before Apple Music. */
    val appleMusicLikes: List<Song>? = null,
    val aliases: Map<String, String> = emptyMap(), val syncedAt: Long = 0,
    /** Likes changed in the app that still have to reach YouTube Music: song id -> liked. */
    val pendingLikes: Map<String, Boolean>? = null,
    /** Playlists removed in the app (playlist id -> info). Sync never brings these back. */
    val deletedPlaylists: Map<String, DeletedPlaylist>? = null,
    /** Removed playlists that still have to be deleted / unsaved on YouTube Music (playlist ids). */
    val pendingRemoteDeletes: List<String>? = null)

/** Tombstone for a playlist the user removed. [remote] = the user also asked to delete it on the service. */
data class DeletedPlaylist(val id: String, val remoteId: String, val source: String, val title: String,
    val coverUrl: String? = null, val deletedAt: Long = 0, val remote: Boolean = false)

private fun Song.youTubeVideoId(): String? = youtubeId?.takeIf { it.isNotBlank() }
    ?: id.takeIf { it.startsWith("yt_") }?.removePrefix("yt_")?.takeIf { it.isNotBlank() && !it.startsWith("unavailable:") }

/** Applies the local overlay to the remote list. Songs not mentioned in [PlaylistEdits.order] keep their relative position at the end. */
internal fun applyPlaylistEdits(remote: List<Song>, edits: PlaylistEdits?): List<Song> {
    if (edits == null || edits.isEmpty) return remote
    val removed = edits.removed.orEmpty().toSet()
    val remoteIds = remote.mapTo(hashSetOf()) { it.id }
    val base = remote.filterNot { it.id in removed } +
        edits.added.orEmpty().filter { it.id !in remoteIds && it.id !in removed }.distinctBy { it.id }
    val order = edits.order ?: return base
    val rank = HashMap<String, Int>(order.size).apply { order.forEachIndexed { index, id -> putIfAbsent(id, index) } }
    return base.withIndex().sortedWith(compareBy({ rank[it.value.id] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
}

/** Drops overlay entries the service now reflects. Unconfirmed entries stay, whatever the service returned. */
internal fun confirmPlaylistEdits(edits: PlaylistEdits?, remote: List<Song>, remoteTitle: String?): PlaylistEdits? {
    if (edits == null) return null
    val remoteIds = remote.mapTo(hashSetOf()) { it.id }
    val remoteVideos = remote.mapNotNullTo(hashSetOf()) { it.youTubeVideoId() }
    val added = edits.added.orEmpty().filterNot { it.id in remoteIds || (it.youTubeVideoId()?.let { v -> v in remoteVideos } == true) }
    val removed = edits.removed.orEmpty().filter { it in remoteIds }
    val next = PlaylistEdits(added = added, removed = removed, title = edits.title?.takeUnless { it == remoteTitle })
    val order = edits.order?.takeUnless { applyPlaylistEdits(remote, next.copy(order = it)).map { s -> s.id } == applyPlaylistEdits(remote, next).map { s -> s.id } }
    return next.copy(order = order).takeUnless { it.isEmpty }
}

/**
 * Removes duplicate liked songs. Version words (live, remix…) are kept apart.
 *
 * Two entries are the same song when they share an id (YouTube video, ISRC, or a downloaded
 * copy's library id), or when title + main artist match after removing video decorations
 * ("(Official Video)", "Artist - Topic") and the durations agree (within 3 s, or one of them
 * is unknown). This catches a downloaded copy plus the online version liked separately.
 *
 * When entries collide, a library file or download is kept over a stream; otherwise the
 * first one. The result keeps the original order.
 *
 * [downloadedIds] are cloud song ids that have been downloaded (their library copy's id is
 * [com.theveloper.pixelplay.data.library.DownloadedLibraryIndexer.libraryId]).
 */
internal fun mergeLikedSongs(songs: List<Song>, downloadedIds: Set<String> = emptySet()): List<Song> {
    fun identities(song: Song): Set<String> = buildSet {
        add(song.youtubeId?.let { "yt:$it" } ?: song.id)
        song.id.takeIf { it.startsWith("yt_") }?.let { add("yt:" + it.removePrefix("yt_")) }
        song.id.toLongOrNull()?.let { add("lib:$it") }
        if (song.id in downloadedIds || song.downloadState == com.theveloper.pixelplay.data.model.DownloadState.DOWNLOADED) {
            add("lib:" + com.theveloper.pixelplay.data.library.DownloadedLibraryIndexer.libraryId(song.id))
        }
        song.creditsAndRelease.isrc?.takeIf { it.isNotBlank() }?.let { add("isrc:" + it.uppercase(Locale.ROOT)) }
    }
    fun isOnDevice(song: Song) = song.id.toLongOrNull() != null || song.id in downloadedIds ||
        song.downloadState == com.theveloper.pixelplay.data.model.DownloadState.DOWNLOADED

    // Decide which entries to keep, library files / downloads first.
    val order = songs.indices.sortedBy { if (isOnDevice(songs[it])) 0 else 1 }
    val seenIds = hashSetOf<String>()
    val seenRecordings = hashMapOf<String, MutableList<Long>>()
    val keep = BooleanArray(songs.size)
    for (index in order) {
        val song = songs[index]
        val ids = identities(song)
        val key = com.theveloper.pixelplay.data.library.RecordingKeys.of(song)
        val durations = seenRecordings.getOrPut(key) { mutableListOf() }
        val sameRecording = key.length > 1 && durations.isNotEmpty() && (song.duration <= 0 ||
            durations.any { it <= 0 || kotlin.math.abs(it - song.duration) <= 3000 })
        val duplicate = ids.any { it in seenIds } || sameRecording
        seenIds.addAll(ids)
        durations.add(song.duration)
        keep[index] = !duplicate
    }
    return songs.filterIndexed { index, _ -> keep[index] }
}

@Singleton
class ConnectedLibraryRepository @Inject constructor(@ApplicationContext context: Context,
    private val spotify: SpotifyRepository, private val youtube: YouTubeMusicRepository,
    private val spotifyAuth: SpotifyAuthManager, private val youtubeAuth: YouTubeMusicAuthManager,
    private val appleMusic: AppleMusicRepository,
    private val appleMusicAuth: AppleMusicWebSession,
    private val music: MusicRepository,
    private val downloadQueue: com.theveloper.pixelplay.data.youtube.DownloadQueue,
    private val offlineStore: com.theveloper.pixelplay.data.youtube.OfflinePlaylistStore,
    private val cloudSongDao: com.theveloper.pixelplay.data.database.CloudSongDao) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "connected-library-v1.json"))
    private val gson = Gson()
    private val lock = Mutex()
    /** Serialises pushes to YouTube Music. Order: lock -> pushLock, never the reverse. */
    private val pushLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(ConnectedSnapshot())
    val snapshot = mutable.asStateFlow()
    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private val _syncingPlaylists = MutableStateFlow<Set<String>>(emptySet())
    val syncingPlaylists = _syncingPlaylists.asStateFlow()
    private val loaded = scope.async {
        lock.withLock {
            if (file.baseFile.exists()) try {
                val stored = internSongs(file.openRead().bufferedReader().use { gson.fromJson(it, ConnectedSnapshot::class.java) })
                // Older snapshots may still hold podcast / Shorts shelves; hide them right away.
                val deleted = stored.deletedPlaylists.orEmpty()
                mutable.value = stored.copy(playlists = stored.playlists.filterNot {
                    (it.source == "YOUTUBE_MUSIC" && isHiddenYouTubeMusicPlaylist(it.remoteId, it.title)) || it.id in deleted
                })
            }
            catch (e: Exception) { message.value = "Saved connected library could not be read. Sync to restore it." }
        }
    }
    val favoriteIds = music.getFavoriteSongIdsFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val likedSongs: Flow<List<Song>> = music.getFavoriteSongIdsFlow()
        .flatMapLatest { ids ->
            combine(snapshot, music.getSongsByIds(ids.toList()), cloudSongDao.getDownloadedSongIds()) { cached, localSongs, downloaded ->
                mergeLikedSongs(
                    localSongs + cached.youtubeLikes + cached.spotifyLikes + cached.appleMusicLikes.orEmpty(),
                    downloaded.toHashSet()
                )
            }
        }.flowOn(Dispatchers.IO)

    init {
        observeFavorites()
        scope.launch {
            try {
            loaded.await()
            combine(spotifyAuth.isLoggedInFlow, youtubeAuth.isLoggedInFlow, appleMusicAuth.isLoggedInFlow) { s, y, a ->
                mapOf(MusicSources.SPOTIFY to s, MusicSources.YOUTUBE_MUSIC to y, MusicSources.APPLE_MUSIC to a)
            }
                .collect { loggedIn ->
                    lock.withLock {
                        val old = mutable.value
                        // Keep a service's playlists only while it's connected (unknown sources are kept).
                        save(old.copy(playlists = old.playlists.filter { loggedIn[it.source] ?: true },
                            spotifyLikes = if (loggedIn.getValue(MusicSources.SPOTIFY)) old.spotifyLikes else emptyList(),
                            youtubeLikes = if (loggedIn.getValue(MusicSources.YOUTUBE_MUSIC)) old.youtubeLikes else emptyList(),
                            appleMusicLikes = if (loggedIn.getValue(MusicSources.APPLE_MUSIC)) old.appleMusicLikes else null))
                    }
                    if (loggedIn.values.any { it }) sync()
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { message.value = e.message ?: "Could not save the connected library." }
        }
    }
    /**
     * Streams the snapshot straight to disk. Building it as one String first (then a byte copy)
     * needed ~3x the JSON size in heap at once — with thousands of playlist songs that alone
     * could push the app past its 256 MB limit.
     */
    private fun save(value: ConnectedSnapshot) {
        val compact = internSongs(value)
        val stream = file.startWrite()
        try {
            val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(stream, Charsets.UTF_8), 64 * 1024)
            gson.toJson(compact, writer)
            writer.flush() // finishWrite syncs and closes the stream itself
            file.finishWrite(stream)
            mutable.value = compact
        } catch (e: Exception) { file.failWrite(stream); throw e }
    }

    /**
     * The same song often sits in several playlists, the likes lists and the edit overlay, and
     * each copy is a separate (large) Song object after loading or syncing. Share one instance
     * per identical song so the snapshot doesn't hold the library several times over.
     */
    private fun internSongs(value: ConnectedSnapshot): ConnectedSnapshot {
        val pool = HashMap<String, Song>()
        fun one(song: Song): Song {
            val existing = pool.putIfAbsent(song.id, song) ?: return song
            return if (existing === song || existing == song) existing else song
        }
        fun many(songs: List<Song>?): List<Song>? = songs?.map(::one)
        return value.copy(
            playlists = value.playlists.map { p ->
                p.copy(songs = p.songs.map(::one), remoteSongs = many(p.remoteSongs),
                    edits = p.edits?.let { e -> e.copy(added = many(e.added)) })
            },
            spotifyLikes = value.spotifyLikes.map(::one),
            youtubeLikes = value.youtubeLikes.map(::one),
            appleMusicLikes = many(value.appleMusicLikes),
        )
    }
    private suspend fun preserve(old: ConnectedPlaylist?, fresh: ConnectedPlaylist, remoteTitle: String = fresh.title,
        fetch: suspend () -> List<Song>): ConnectedPlaylist = try {
        val songs = fetch()
        songs.filter { it.contentUriString.isNotBlank() }.forEach { music.saveCloudSong(it) }
        withRemote(fresh.copy(edits = old?.edits ?: fresh.edits), songs, null, remoteTitle)
            .copy(error = if (songs.any { it.contentUriString.isBlank() }) "Some tracks are unavailable from this service." else null)
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
          val base = old ?: fresh
          fresh.copy(songs = base.songs, edits = base.edits, remoteSongs = base.remoteSongs, remoteTitle = base.remoteTitle,
              setVideoIds = base.setVideoIds, title = base.edits?.title ?: fresh.title,
              error = e.message ?: "Sync failed. Previous tracks kept.")
      }

    /** YouTube Music variant that also keeps the playlist row ids needed for edits. */
    private suspend fun preserveYouTube(old: ConnectedPlaylist?, fresh: ConnectedPlaylist, remoteTitle: String = fresh.title): ConnectedPlaylist {
        var rowIds: List<String?>? = null
        val result = preserve(old, fresh, remoteTitle) {
            val tracks = youtube.getPlaylistTracks(fresh.remoteId)
            rowIds = tracks.map { it.setVideoId }
            tracks.map { it.toSong() }
        }
        return if (rowIds != null) result.copy(setVideoIds = rowIds) else result
    }

    /** Stores a fresh remote list, confirms whatever the service now reflects and re-applies the rest. */
    private fun withRemote(playlist: ConnectedPlaylist, remote: List<Song>, rowIds: List<String?>?, remoteTitle: String): ConnectedPlaylist {
        val edits = confirmPlaylistEdits(playlist.edits, remote, remoteTitle)
        return playlist.copy(songs = applyPlaylistEdits(remote, edits), title = edits?.title ?: remoteTitle,
            edits = edits, remoteSongs = if (edits == null) null else remote, remoteTitle = if (edits == null) null else remoteTitle,
            setVideoIds = rowIds ?: playlist.setVideoIds)
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        loaded.await()
        lock.withLock {
            syncing.value = true; message.value = null
            try {
                var next = mutable.value
                val failures = mutableListOf<String>()
                if (spotifyAuth.isLoggedInFlow.first()) try {
                    val owner = spotify.getCurrentUserId()
                    val deletedIds = next.deletedPlaylists.orEmpty()
                    val listed = spotify.getUserPlaylists()
                    val imported = listed.filterNot { "SPOTIFY:${it.id}" in deletedIds }.map { p ->
                        val id = "SPOTIFY:${p.id}"
                        val old = next.playlists.find { it.id == id }
                        preserve(old, ConnectedPlaylist(id, p.id, "SPOTIFY", p.title, p.coverUrl,
                            friendId = old?.friendId ?: p.ownerId.takeIf { it.isNotBlank() && it != owner && it != "spotify" }?.let { "SPOTIFY:$it" },
                            ownerName = p.ownerName, manual = old?.manual ?: false)) { spotify.getPlaylistTracks(p.id).map { it.toSong() } }
                    }
                    val likes = spotify.getLikedSongs().map { it.toSong() }
                    likes.filter { it.contentUriString.isNotBlank() }.forEach { music.saveCloudSong(it) }
                    next = next.copy(playlists = next.playlists.filter { it.source != "SPOTIFY" || (it.manual && listed.none { p -> p.id == it.remoteId }) } + imported, spotifyLikes = likes)
                } catch (e: CancellationException) { throw e } catch (e: Exception) { failures.add(e.message ?: "Spotify sync failed") }
                if (appleMusicAuth.hasSession) try {
                    val deletedIds = next.deletedPlaylists.orEmpty()
                    val listed = appleMusic.getUserPlaylists()
                    val imported = listed
                        .filterNot { "APPLE_MUSIC:${it.id}" in deletedIds || AppleMusicParsers.isFavoritesPlaylist(it.title) }
                        .map { p ->
                            val id = "APPLE_MUSIC:${p.id}"
                            val old = next.playlists.find { it.id == id }
                            // Someone else's playlist saved to your library (their name as curator) groups under them.
                            val curatorFriend = p.curatorName.takeIf { it.isNotBlank() && !p.canEdit }?.let { "APPLE_MUSIC:${it.lowercase()}" }
                            preserve(old, ConnectedPlaylist(id, p.id, MusicSources.APPLE_MUSIC, p.title, p.coverUrl,
                                friendId = old?.friendId ?: curatorFriend, ownerName = p.curatorName, manual = old?.manual ?: false)) {
                                appleMusic.getPlaylistTracks(p.id).map { it.toSong() }
                            }
                        }
                    val likes = appleMusic.getFavoriteSongs(listed).map { it.toSong() }
                    likes.filter { it.contentUriString.isNotBlank() }.forEach { music.saveCloudSong(it) }
                    next = next.copy(playlists = next.playlists.filter { it.source != MusicSources.APPLE_MUSIC || (it.manual && listed.none { p -> p.id == it.remoteId }) } + imported,
                        appleMusicLikes = likes)
                } catch (e: CancellationException) { throw e } catch (e: Exception) { failures.add(e.message ?: "Apple Music sync failed") }
                var likeChanges: Pair<Set<String>, Set<String>>? = null
                if (youtubeAuth.isLoggedInFlow.first()) try {
                    // Push edits made in the app first, so the fetch below already reflects them.
                    next.playlists.filter { it.source == "YOUTUBE_MUSIC" && it.edits != null }.forEach { p ->
                        try { pushLock.withLock { pushPlaylistEdits(p) } } catch (e: CancellationException) { throw e } catch (_: Exception) { /* retried next sync */ }
                    }
                    next = flushLikes(next)
                    next = flushRemoteDeletes(next)
                    val deletedIds = next.deletedPlaylists.orEmpty()
                    val listed = youtube.getUserPlaylists()
                    val imported = listed.filterNot { "YOUTUBE_MUSIC:${it.id}" in deletedIds }.map { p ->
                        val id = "YOUTUBE_MUSIC:${p.id}"
                        val old = next.playlists.find { it.id == id }
                        preserveYouTube(old, ConnectedPlaylist(id, p.id, "YOUTUBE_MUSIC", p.title, p.coverUrl, friendId = old?.friendId, ownerName = old?.ownerName.orEmpty(), manual = old?.manual ?: false))
                    }
                    val remoteLikes = youtube.getLikedSongs().map { it.toSong() }
                    remoteLikes.filter { it.contentUriString.isNotBlank() }.forEach { music.saveCloudSong(it) }
                    val pending = next.pendingLikes.orEmpty()
                    val likes = remoteLikes.filter { pending[it.id] != false }
                    val before = next.youtubeLikes.mapTo(hashSetOf()) { it.id }
                    val after = likes.mapTo(hashSetOf()) { it.id }
                    likeChanges = (after - before) to (before - after).filter { pending[it] != true }.toSet()
                    next = next.copy(playlists = next.playlists.filter {
                        it.source != "YOUTUBE_MUSIC" || isHiddenYouTubeMusicPlaylist(it.remoteId, it.title).not() && (it.manual && listed.none { p -> p.id == it.remoteId })
                    } + imported, youtubeLikes = likes)
                } catch (e: CancellationException) { throw e } catch (e: Exception) { failures.add(e.message ?: "YouTube Music sync failed") }
                val refreshed = next.playlists.map { p ->
                    if (!p.manual) p
                    else if (p.source == "SPOTIFY") preserve(p, p) { spotify.getPlaylistTracks(p.remoteId).map { it.toSong() } }
                    else if (p.source == MusicSources.APPLE_MUSIC) preserve(p, p) { appleMusic.getPlaylistTracks(p.remoteId).map { it.toSong() } }
                    else preserveYouTube(p, p, p.remoteTitle ?: p.title)
                }
                // Save the new remote likes before touching favorites so the favorites observer
                // recognises these as remote changes and does not echo them back.
                save(next.copy(playlists = refreshed, syncedAt = System.currentTimeMillis()))
                likeChanges?.let { (liked, unliked) ->
                    liked.forEach { id -> runCatching { music.setFavoriteStatus(id, true) } }
                    unliked.forEach { id -> runCatching { music.setFavoriteStatus(id, false) } }
                }
                message.value = failures.joinToString("\n").ifBlank { null }
            } finally { syncing.value = false }
        }
        scope.launch { autoDownloadOfflinePlaylists() }
        Unit
    }

    /** "Keep in sync": queue songs that were added to fully-downloaded playlists since the last sync. */
    private suspend fun autoDownloadOfflinePlaylists() {
        try {
            val offline = offlineStore.offlineIds.value
            if (offline.isEmpty() || !offlineStore.canDownloadNow()) return
            mutable.value.playlists.filter { it.id in offline }.forEach { playlist ->
                val ids = playlist.songs.map { it.id }
                val downloaded = ids.chunked(500).flatMap { cloudSongDao.getByIds(it) }
                    .filter { it.isDownloaded }.mapTo(hashSetOf()) { it.id }
                val missing = playlist.songs.filter { com.theveloper.pixelplay.data.youtube.OfflinePlaylistStore.needsDownload(it, downloaded) }
                if (missing.isNotEmpty()) downloadQueue.enqueueAll(missing, playlist.title)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }

    // ---- Likes: YouTube Music <-> PixelPlayer favorites ------------------------------------

    /** Pushes pending like changes; must be called with [lock] held. Returns the updated snapshot (not saved). */
    private suspend fun flushLikes(current: ConnectedSnapshot): ConnectedSnapshot {
        val pending = current.pendingLikes.orEmpty()
        if (pending.isEmpty() || !youtubeAuth.isLoggedInFlow.first()) return current
        val remaining = pending.toMutableMap()
        var likes = current.youtubeLikes
        // Batch likes from multi-select can be hundreds of calls: pace them so YouTube Music
        // doesn't rate-limit the account, and stop early (keep the rest pending) if it starts refusing.
        val paced = pending.size > 5
        var failuresInARow = 0
        for ((songId, liked) in pending) {
            if (failuresInARow >= 3) break
            val videoId = songId.removePrefix("yt_")
            if (paced) delay(150)
            try {
                youtube.setLiked(videoId, liked)
                failuresInARow = 0
                remaining.remove(songId)
                likes = if (liked) {
                    if (likes.any { it.id == songId }) likes
                    else likes + (music.getSongsByIds(listOf(songId)).first().firstOrNull() ?: continue)
                } else likes.filterNot { it.id == songId }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failuresInARow++ /* keep pending, retry later */ }
        }
        return current.copy(youtubeLikes = likes, pendingLikes = remaining.ifEmpty { null })
    }

    private suspend fun flushLikesNow() {
        lock.withLock {
            val updated = flushLikes(mutable.value)
            if (updated != mutable.value) save(updated)
        }
    }

    /** Mirrors like/unlike of YouTube songs made in the app to the YouTube Music account. */
    private fun observeFavorites() = scope.launch {
        loaded.await()
        var previous: Set<String>? = null
        music.getFavoriteSongIdsFlow().collect { ids ->
            val before = previous
            previous = ids
            if (before == null || !youtubeAuth.isLoggedInFlow.first()) return@collect
            val added = (ids - before).filter { it.startsWith("yt_") }
            val removed = (before - ids).filter { it.startsWith("yt_") }
            if (added.isEmpty() && removed.isEmpty()) return@collect
            try {
                lock.withLock {
                    val current = mutable.value
                    val remote = current.youtubeLikes.mapTo(hashSetOf()) { it.id }
                    val pending = current.pendingLikes.orEmpty().toMutableMap()
                    added.forEach { id -> if (id in remote) pending.remove(id) else pending[id] = true }
                    removed.forEach { id -> if (id !in remote) pending.remove(id) else pending[id] = false }
                    val next = current.copy(pendingLikes = pending.ifEmpty { null })
                    if (next != current) save(next)
                }
                flushLikesNow()
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { message.value = e.message ?: "Could not update YouTube Music likes." }
        }
    }

    // ---- Playlist editing ------------------------------------------------------------------

    fun isEditable(playlistId: String): Boolean = mutable.value.playlists.find { it.id == playlistId }?.isEditable == true

    private suspend fun editPlaylist(playlistId: String, change: (ConnectedPlaylist, PlaylistEdits) -> PlaylistEdits) = withContext(Dispatchers.IO) {
        loaded.await()
        val updated = lock.withLock {
            val current = mutable.value
            val playlist = current.playlists.find { it.id == playlistId } ?: return@withContext
            require(playlist.isEditable) { "This playlist can't be edited." }
            val remote = playlist.remote
            val remoteTitle = playlist.remoteTitle ?: playlist.title
            val edits = change(playlist, playlist.edits ?: PlaylistEdits()).takeUnless { it.isEmpty }
            val next = playlist.copy(edits = edits, songs = applyPlaylistEdits(remote, edits),
                title = edits?.title ?: remoteTitle,
                remoteSongs = if (edits == null) null else remote,
                remoteTitle = if (edits == null) null else remoteTitle)
            save(current.copy(playlists = current.playlists.map { if (it.id == playlistId) next else it }))
            next
        }
        // Best effort: push now; if it fails the overlay stays and the next sync retries.
        if (updated.edits != null) scope.launch {
            try { pushAndRefresh(playlistId) } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Saved in PixelPlayer. YouTube Music will be updated on the next sync (${e.message ?: "offline"})." }
        }
    }

    suspend fun addSongs(playlistId: String, songs: List<Song>) = editPlaylist(playlistId) { playlist, e ->
        val visible = playlist.songs.mapTo(hashSetOf()) { it.id }
        val remoteIds = playlist.remote.mapTo(hashSetOf()) { it.id }
        val fresh = songs.filter { it.id !in visible }.distinctBy { it.id }
        e.copy(added = e.added.orEmpty().filterNot { a -> fresh.any { it.id == a.id } } + fresh.filter { it.id !in remoteIds },
            removed = e.removed.orEmpty() - fresh.map { it.id }.toSet(),
            order = e.order?.let { it + fresh.map { s -> s.id } })
    }

    suspend fun removeSong(playlistId: String, songId: String) = editPlaylist(playlistId) { playlist, e ->
        val inRemote = playlist.remote.any { it.id == songId }
        e.copy(added = e.added.orEmpty().filterNot { it.id == songId },
            removed = if (inRemote) (e.removed.orEmpty() + songId).distinct() else e.removed.orEmpty(),
            order = e.order?.minus(songId))
    }

    suspend fun reorder(playlistId: String, orderedSongIds: List<String>) = editPlaylist(playlistId) { _, e -> e.copy(order = orderedSongIds) }

    suspend fun rename(playlistId: String, title: String) {
        require(title.isNotBlank()) { "Enter a playlist name." }
        editPlaylist(playlistId) { _, e -> e.copy(title = title.trim()) }
    }

    private suspend fun pushAndRefresh(playlistId: String) {
        if (!youtubeAuth.isLoggedInFlow.first()) return
        // Never hold pushLock while waiting for lock (sync takes them in the opposite order).
        val tracks = pushLock.withLock {
            val playlist = mutable.value.playlists.find { it.id == playlistId } ?: return
            if (playlist.source != "YOUTUBE_MUSIC" || playlist.edits == null) return
            pushPlaylistEdits(playlist)
        }
        lock.withLock {
            val current = mutable.value
            val latest = current.playlists.find { it.id == playlistId } ?: return
            // The service may lag behind; confirmPlaylistEdits keeps anything it doesn't show yet.
            val refreshed = withRemote(latest, tracks.map { it.toSong() }, tracks.map { it.setVideoId },
                latest.remoteTitle ?: latest.title)
            save(current.copy(playlists = current.playlists.map { if (it.id == playlistId) refreshed else it }))
        }
    }

    /**
     * Sends the difference between the overlay and YouTube Music as edit_playlist actions and
     * returns the refreshed remote tracks. Songs that only exist locally stay app-only.
     */
    private suspend fun pushPlaylistEdits(playlist: ConnectedPlaylist): List<YouTubeMusicTrack> {
        val edits = playlist.edits ?: return youtube.getPlaylistTracks(playlist.remoteId)
        var tracks = youtube.getPlaylistTracks(playlist.remoteId)
        val actions = org.json.JSONArray()
        edits.title?.let { actions.put(org.json.JSONObject().put("action", "ACTION_SET_PLAYLIST_NAME").put("playlistName", it)) }
        val removed = edits.removed.orEmpty().toSet()
        tracks.filter { "yt_${it.videoId}" in removed && it.setVideoId != null }.forEach {
            actions.put(org.json.JSONObject().put("action", "ACTION_REMOVE_VIDEO").put("setVideoId", it.setVideoId).put("removedVideoId", it.videoId))
        }
        val remoteVideos = tracks.mapTo(hashSetOf()) { it.videoId }
        edits.added.orEmpty().mapNotNull { it.youTubeVideoId() }.distinct().filter { it !in remoteVideos }.forEach {
            actions.put(org.json.JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", it).put("dedupeOption", "DEDUPE_OPTION_SKIP"))
        }
        if (actions.length() > 0) {
            youtube.editPlaylist(playlist.remoteId, actions)
            tracks = youtube.getPlaylistTracks(playlist.remoteId)
        }
        val order = edits.order
        if (order != null) {
            val rows = tracks.filter { it.setVideoId != null && "yt_${it.videoId}" !in removed }
            val desired = applyPlaylistEdits(rows.map { it.toSong() }, PlaylistEdits(order = order))
            val rowBySong = rows.groupBy { "yt_${it.videoId}" }.mapValues { it.value.toMutableList() }
            val desiredRows = desired.mapNotNull { song -> rowBySong[song.id]?.removeFirstOrNull()?.setVideoId }
            val current = rows.mapNotNull { it.setVideoId }.toMutableList()
            val moves = org.json.JSONArray()
            // Build the target order from the end: place each row directly before its successor.
            for (i in desiredRows.size - 2 downTo 0) {
                val moving = desiredRows[i]; val successor = desiredRows[i + 1]
                if (current.indexOf(moving) + 1 == current.indexOf(successor)) continue
                current.remove(moving); current.add(current.indexOf(successor), moving)
                moves.put(org.json.JSONObject().put("action", "ACTION_MOVE_VIDEO_BEFORE").put("setVideoId", moving).put("movedSetVideoIdSuccessor", successor))
            }
            if (moves.length() > 0) {
                youtube.editPlaylist(playlist.remoteId, moves)
                tracks = youtube.getPlaylistTracks(playlist.remoteId)
            }
        }
        return tracks
    }
    suspend fun syncPlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        loaded.await()
        val target = mutable.value.playlists.find { it.id == playlistId } ?: return@withContext
        _syncingPlaylists.update { it + playlistId }
        try {
            if (target.source == "YOUTUBE_MUSIC" && target.edits != null) {
                try { pushLock.withLock { pushPlaylistEdits(target) } } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
            val updated = if (target.source == "SPOTIFY") preserve(target, target) { spotify.getPlaylistTracks(target.remoteId).map { it.toSong() } }
                else if (target.source == MusicSources.APPLE_MUSIC) preserve(target, target) { appleMusic.getPlaylistTracks(target.remoteId).map { it.toSong() } }
                else preserveYouTube(target, target, target.remoteTitle ?: target.title)
            lock.withLock {
                val current = mutable.value
                val nextPlaylists = current.playlists.map { if (it.id == playlistId) updated else it }
                save(current.copy(playlists = nextPlaylists, syncedAt = System.currentTimeMillis()))
            }
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { message.value = e.message ?: "Sync failed for ${target.title}" }
        finally { _syncingPlaylists.update { it - playlistId } }
    }
    // ---- Removing playlists -----------------------------------------------------------------

    val deletedPlaylists: Flow<List<DeletedPlaylist>> = snapshot.map { s ->
        s.deletedPlaylists.orEmpty().values.sortedByDescending { it.deletedAt }
    }.distinctUntilChanged()

    fun isConnected(playlistId: String): Boolean = mutable.value.playlists.any { it.id == playlistId }

    /**
     * Removes a connected playlist from PixelPlayer and remembers it, so later syncs don't import
     * it again. With [alsoRemote], a YouTube Music playlist is also deleted from the account (or,
     * when someone else owns it, removed from the account's library). Offline -> retried on sync.
     */
    suspend fun deletePlaylist(playlistId: String, alsoRemote: Boolean) = withContext(Dispatchers.IO) {
        loaded.await()
        val removed = lock.withLock {
            val current = mutable.value
            val playlist = current.playlists.find { it.id == playlistId } ?: return@withContext
            val remote = alsoRemote && playlist.source == "YOUTUBE_MUSIC"
            val tombstone = DeletedPlaylist(playlist.id, playlist.remoteId, playlist.source, playlist.title,
                playlist.coverUrl, System.currentTimeMillis(), remote)
            save(current.copy(
                playlists = current.playlists.filterNot { it.id == playlistId },
                deletedPlaylists = current.deletedPlaylists.orEmpty() + (playlistId to tombstone),
                pendingRemoteDeletes = if (remote) (current.pendingRemoteDeletes.orEmpty() + playlistId).distinct() else current.pendingRemoteDeletes
            ))
            remote
        }
        if (removed) {
            try {
                lock.withLock {
                    val updated = flushRemoteDeletes(mutable.value)
                    if (updated != mutable.value) save(updated)
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { message.value = "Removed here. YouTube Music will be updated on the next sync (${e.message ?: "offline"})." }
        }
    }

    /** Deletes several connected playlists at once (Library multi-select). */
    suspend fun deletePlaylists(playlistIds: Collection<String>, alsoRemote: Boolean) {
        playlistIds.forEach { deletePlaylist(it, alsoRemote) }
    }

    /** Forgets a removal so the next sync imports the playlist again (if it still exists on the service). */
    suspend fun restorePlaylist(playlistId: String) {
        loaded.await()
        lock.withLock {
            val current = mutable.value
            save(current.copy(
                deletedPlaylists = (current.deletedPlaylists.orEmpty() - playlistId).ifEmpty { null },
                pendingRemoteDeletes = current.pendingRemoteDeletes?.minus(playlistId)?.ifEmpty { null }
            ))
        }
        scope.launch { runCatching { sync() } }
    }

    /** Sends pending remote deletions; must be called with [lock] held. Returns the updated snapshot (not saved). */
    private suspend fun flushRemoteDeletes(current: ConnectedSnapshot): ConnectedSnapshot {
        val pending = current.pendingRemoteDeletes.orEmpty()
        if (pending.isEmpty() || !youtubeAuth.isLoggedInFlow.first()) return current
        val remaining = pending.toMutableList()
        for (id in pending) {
            val tombstone = current.deletedPlaylists?.get(id)
            if (tombstone == null || tombstone.source != "YOUTUBE_MUSIC") { remaining.remove(id); continue }
            try {
                youtube.deleteOrRemovePlaylist(tombstone.remoteId)
                remaining.remove(id)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* keep pending, retry later */ }
        }
        return current.copy(pendingRemoteDeletes = remaining.ifEmpty { null })
    }

    suspend fun renameFriend(id: String, name: String) = withContext(Dispatchers.IO) {
        require(name.trim().isNotBlank()) { "Enter a friend's name." }
        loaded.await(); lock.withLock { save(mutable.value.copy(aliases = mutable.value.aliases + (id to name.trim()))) }
    }
    /**
     * Saves a friend's public playlist and returns its playlist id. Pass [knownFriendId] when the
     * friend already has a platform id (e.g. "SPOTIFY:<userId>" from the friends feed), so the
     * playlist groups under that friend and their display name/alias is left alone.
     */
    suspend fun addFriendPlaylist(name: String, link: String, knownFriendId: String? = null): String = withContext(Dispatchers.IO) {
        require(name.trim().isNotBlank()) { "Enter a friend's name." }
        val uri = Uri.parse(link.trim())
        require(uri.scheme == "https") { "Use a full HTTPS Spotify, YouTube Music or Apple Music playlist link." }
        loaded.await()
        lock.withLock {
            val friendId = knownFriendId
                ?: mutable.value.aliases.entries.find { it.value.equals(name.trim(), true) }?.key
                ?: mutable.value.playlists.firstOrNull { it.friendId != null && it.ownerName.equals(name.trim(), true) }?.friendId
                ?: java.util.UUID.randomUUID().toString()
            val playlist = when (uri.host) {
                "open.spotify.com" -> {
                    val segments = uri.pathSegments
                    val id = segments.getOrNull(segments.indexOf("playlist") + 1)?.takeIf { segments.contains("playlist") } ?: error("Use a Spotify playlist link.")
                    val p = spotify.getPlaylist(id)
                    val base = ConnectedPlaylist("SPOTIFY:$id", id, "SPOTIFY", p.title, p.coverUrl, friendId = friendId, ownerName = name.trim(), manual = true)
                    preserve(mutable.value.playlists.find { it.id == base.id }, base) { spotify.getPlaylistTracks(id).map { it.toSong() } }
                }
                "music.youtube.com", "www.youtube.com", "youtube.com" -> {
                    val id = uri.getQueryParameter("list") ?: error("Use a YouTube Music playlist link.")
                    val p = youtube.getPlaylist(id)
                    val base = ConnectedPlaylist("YOUTUBE_MUSIC:$id", id, "YOUTUBE_MUSIC", p.title, p.coverUrl, friendId = friendId, ownerName = name.trim(), manual = true)
                    preserveYouTube(mutable.value.playlists.find { it.id == base.id }, base)
                }
                "music.apple.com", "embed.music.apple.com" -> {
                    // https://music.apple.com/{storefront}/playlist/{slug}/{pl.xxxx}
                    val segments = uri.pathSegments
                    val id = segments.lastOrNull { it.startsWith("pl.") } ?: error("Use an Apple Music playlist link.")
                    val storefront = segments.firstOrNull()?.takeIf { it.length == 2 } ?: appleMusicAuth.storefront
                    if (!appleMusicAuth.hasSession) error("Connect Apple Music in Accounts to add Apple Music playlists.")
                    val p = appleMusic.getCatalogPlaylist(id, storefront)
                    val base = ConnectedPlaylist("APPLE_MUSIC:$id", id, MusicSources.APPLE_MUSIC, p.title, p.coverUrl, friendId = friendId, ownerName = name.trim(), manual = true)
                    preserve(mutable.value.playlists.find { it.id == base.id }, base) { appleMusic.getPlaylistTracks(id).map { it.toSong() } }
                }
                else -> error("Use a Spotify, YouTube Music or Apple Music playlist link.")
            }
            save(mutable.value.copy(playlists = mutable.value.playlists.filterNot { it.id == playlist.id } + playlist,
                aliases = if (knownFriendId != null) mutable.value.aliases else mutable.value.aliases + (friendId to name.trim()),
                deletedPlaylists = (mutable.value.deletedPlaylists.orEmpty() - playlist.id).ifEmpty { null }))
            playlist.id
        }
    }
}




