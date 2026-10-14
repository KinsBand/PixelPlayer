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
import kotlinx.coroutines.sync.withPermit
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
    val setVideoIds: List<String?>? = null,
    /** When its songs were last read from the service (ms); 0 = unknown / before this was kept. */
    val fetchedAt: Long = 0) {
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
    val pendingRemoteDeletes: List<String>? = null,
    /** Friends removed in the app (friend ids). The friends feed never brings these back. */
    val hiddenFriends: List<String>? = null,
    /** Friends' playlists pinned into Your playlists (playlist ids). */
    val pinnedFriendPlaylists: List<String>? = null,
    /** Display names of [hiddenFriends] (friend id -> name), so Accounts can list and restore them. */
    val hiddenFriendNames: Map<String, String>? = null,
    /** Friends pinned to the top of their Friends section (friend ids). Null in snapshots saved before pins. */
    val pinnedFriends: List<String>? = null,
    /** People you blocked (friend ids): hidden like [hiddenFriends] and kept out of people lists. */
    val blockedFriends: List<String>? = null)

/**
 * Tombstone for a playlist the user removed. [remote] = the user also asked to delete it on the service.
 * [friendId] is set when it was removed by hiding that friend (null in tombstones saved before this).
 */
data class DeletedPlaylist(val id: String, val remoteId: String, val source: String, val title: String,
    val coverUrl: String? = null, val deletedAt: Long = 0, val remote: Boolean = false,
    val friendId: String? = null)

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
    private val cloudSongDao: com.theveloper.pixelplay.data.database.CloudSongDao,
    /** YouTube Music song search for exports (lazy: only built when an export needs it). */
    private val ytSearch: dagger.Lazy<com.theveloper.pixelplay.data.youtube.YouTubeMusicApiService>) {
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
                }.map { p ->
                    // Friends' playlists saved before the cap could hold thousands of songs: trim them
                    // so the snapshot (held in memory all the time) stays small. The next refresh re-reads them.
                    if (p.friendId != null && p.edits == null && p.songs.size > FRIEND_PLAYLIST_MAX_SONGS) {
                        p.copy(songs = p.songs.take(FRIEND_PLAYLIST_MAX_SONGS), remoteSongs = null,
                            setVideoIds = p.setVideoIds?.take(FRIEND_PLAYLIST_MAX_SONGS))
                    } else p
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
            .copy(error = if (songs.any { it.contentUriString.isBlank() }) "Some tracks are unavailable from this service." else null,
                fetchedAt = System.currentTimeMillis())
    } catch (e: CancellationException) { throw e }
      catch (e: OutOfMemoryError) {
          // Last resort: one playlist too big to read. Its half-read songs are already garbage, so
          // keep the previous copy instead of taking the whole app down (this used to crash on sync).
          val base = old ?: fresh
          fresh.copy(songs = base.songs, edits = base.edits, remoteSongs = base.remoteSongs, remoteTitle = base.remoteTitle,
              setVideoIds = base.setVideoIds, title = base.edits?.title ?: fresh.title, fetchedAt = System.currentTimeMillis(),
              error = "This playlist is too big to load in full.")
      }
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
            // Friends' playlists keep at most FRIEND_PLAYLIST_MAX_SONGS songs (rows stay aligned).
            val tracks = youtube.getPlaylistTracks(fresh.remoteId)
                .let { if (fresh.friendId != null && it.size > FRIEND_PLAYLIST_MAX_SONGS) it.take(FRIEND_PLAYLIST_MAX_SONGS) else it }
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

    /**
     * Syncs every connected service, or only [only] (a source such as "SPOTIFY") when given. A
     * single-service sync re-lists that account's playlists and likes but leaves friends'
     * playlists alone; those are refreshed with [refreshPlaylists].
     */
    suspend fun sync(only: String? = null) = withContext(Dispatchers.IO) {
        loaded.await()
        lock.withLock {
            syncing.value = true; message.value = null
            try {
                var next = mutable.value
                val failures = mutableListOf<String>()
                if ((only == null || only == "SPOTIFY") && spotifyAuth.isLoggedInFlow.first()) try {
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
                if ((only == null || only == MusicSources.APPLE_MUSIC) && appleMusicAuth.hasSession) try {
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
                if ((only == null || only == "YOUTUBE_MUSIC") && youtubeAuth.isLoggedInFlow.first()) try {
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
                // Manual playlists are re-read here. Friends' playlists used to be re-downloaded in full
                // on every sync (opening a friend saves all their public playlists), which held
                // thousands of songs at once and ran out of memory. Now: a friend's playlist is re-read
                // at most once a day (pinned ones too), only a few per sync (oldest first), and capped.
                val now = System.currentTimeMillis()
                val friendDue = if (only != null) emptySet() else next.playlists
                    .filter { it.manual && it.friendId != null && now - it.fetchedAt >= FRIEND_PLAYLIST_REFRESH_MS }
                    .sortedBy { it.fetchedAt }
                    .take(FRIEND_PLAYLISTS_PER_SYNC)
                    .mapTo(HashSet()) { it.id }
                val refreshed = next.playlists.map { p ->
                    val cap = if (p.friendId != null) FRIEND_PLAYLIST_MAX_SONGS else Int.MAX_VALUE
                    if (!p.manual || (p.friendId != null && p.id !in friendDue) || (only != null && p.source != only)) p
                    else if (p.source == "SPOTIFY") preserve(p, p) { spotify.getPlaylistTracks(p.remoteId, cap).map { it.toSong() } }
                    else if (p.source == MusicSources.APPLE_MUSIC) preserve(p, p) { appleMusic.getPlaylistTracks(p.remoteId).take(cap).map { it.toSong() } }
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

    /** Result of adding songs to a playlist on a streaming service. */
    data class ServiceAddResult(val added: Int, val alreadyThere: Int, val notFound: List<Song>)

    /**
     * Your own playlists on Spotify, YouTube Music and Apple Music that songs can be added to
     * (friends' playlists and editorial "Spotify" playlists are left out).
     */
    fun addablePlaylists(snapshot: ConnectedSnapshot = mutable.value): List<ConnectedPlaylist> =
        snapshot.playlists.filter { p ->
            p.friendId == null && when (p.source) {
                "YOUTUBE_MUSIC" -> true
                "SPOTIFY" -> !p.ownerName.equals("Spotify", ignoreCase = true)
                MusicSources.APPLE_MUSIC -> p.remoteId.startsWith("p.")
                else -> false
            }
        }

    /**
     * Adds [songs] to a playlist on its service. YouTube Music goes through the edit overlay (so it
     * works offline and syncs later). Spotify and Apple Music match each song on that service
     * (ISRC, then title + artist), add the matches, then refresh the playlist.
     */
    suspend fun addSongsToService(playlistId: String, songs: List<Song>): ServiceAddResult = withContext(Dispatchers.IO) {
        loaded.await()
        val playlist = mutable.value.playlists.find { it.id == playlistId || it.remoteId == playlistId } ?: error("Playlist not found.")
        val present = playlist.songs.mapTo(hashSetOf()) { it.id }
        val fresh = songs.distinctBy { it.id }.filter { it.id !in present }
        val already = songs.distinctBy { it.id }.size - fresh.size
        when (playlist.source) {
            "YOUTUBE_MUSIC" -> {
                if (fresh.isNotEmpty()) addSongs(playlistId, fresh)
                ServiceAddResult(fresh.size, already, emptyList())
            }
            "SPOTIFY" -> {
                val notFound = mutableListOf<Song>()
                val ids = fresh.mapNotNull { song ->
                    val direct = song.id.removePrefix("spotify_").takeIf { song.id.startsWith("spotify_") && !it.startsWith("unavailable") }
                    (direct ?: runCatching { spotify.findTrackId(song.title, song.displayArtist, song.creditsAndRelease.isrc) }.getOrNull())
                        .also { if (it == null) notFound += song }
                }
                val remoteIds = playlist.songs.mapTo(hashSetOf()) { it.id.removePrefix("spotify_") }
                val toAdd = ids.filter { it !in remoteIds }.distinct()
                spotify.addTracksToPlaylist(playlist.remoteId, toAdd)
                if (toAdd.isNotEmpty()) syncPlaylist(playlistId)
                ServiceAddResult(toAdd.size, already + (ids.size - toAdd.size), notFound)
            }
            MusicSources.APPLE_MUSIC -> {
                val notFound = mutableListOf<Song>()
                val ids = fresh.mapNotNull { song ->
                    val direct = song.id.removePrefix("applemusic_").takeIf { song.id.startsWith("applemusic_") && !it.startsWith("unavailable") }
                    (direct ?: runCatching { appleMusic.findCatalogSongId(song.title, song.displayArtist, song.creditsAndRelease.isrc) }.getOrNull())
                        .also { if (it == null) notFound += song }
                }
                val remoteIds = playlist.songs.mapTo(hashSetOf()) { it.id.removePrefix("applemusic_") }
                val toAdd = ids.filter { it !in remoteIds }.distinct()
                appleMusic.addSongsToLibraryPlaylist(playlist.remoteId, toAdd)
                if (toAdd.isNotEmpty()) syncPlaylist(playlistId)
                ServiceAddResult(toAdd.size, already + (ids.size - toAdd.size), notFound)
            }
            else -> error("This playlist can't be edited.")
        }
    }

    data class ExportResult(
        val remotePlaylistId: String,
        val addedCount: Int,
        val notFoundCount: Int,
        val message: String
    )

    /**
     * Exports [songs] into a brand new playlist on [service] (Spotify or YouTube Music)
     * and triggers a background sync to incorporate it into PixelPlayer's connected library.
     */
    suspend fun exportPlaylistToService(
        name: String,
        songs: List<Song>,
        service: String,
        /** "Matching 40 of 120…" style updates for the export sheet. */
        onProgress: (String) -> Unit = {},
    ): ExportResult = withContext(Dispatchers.IO) {
        loaded.await()
        val unique = songs.distinctBy { it.id }
        // How TuneMyMusic-style transfers stay quick: match every song first (several at a time,
        // exact ISRC before a title search, cached per session), and only then create the playlist
        // and add the matches in big batches. Nothing is created when nothing matched, and a
        // failure says which step it was instead of leaving an empty playlist with "400".
        val matched = java.util.concurrent.atomic.AtomicInteger(0)
        fun tick() {
            val n = matched.incrementAndGet()
            if (n == unique.size || n % 5 == 0) onProgress("Matching $n of ${unique.size}…")
        }
        when (service) {
            MusicSources.SPOTIFY -> {
                val ids = unique.mapConcurrently(EXPORT_MATCH_CONCURRENCY) { song ->
                    val direct = song.id.removePrefix("spotify_").takeIf { song.id.startsWith("spotify_") && !it.startsWith("unavailable") }
                    (direct ?: runCatching { spotify.findTrackId(song.title, song.displayArtist, song.creditsAndRelease.isrc) }.getOrNull())
                        .also { tick() }
                }
                val found = ids.filterNotNull().distinct()
                val notFound = ids.count { it == null }
                if (found.isEmpty()) error("None of these songs were found on Spotify.")
                onProgress("Creating the playlist…")
                val remoteId = exportStep("create the Spotify playlist") { spotify.createPlaylist(name) }
                onProgress("Adding ${found.size} songs…")
                exportStep("add songs on Spotify") { spotify.addTracksToPlaylist(remoteId, found) }
                scope.launch { sync(MusicSources.SPOTIFY) }
                val msg = if (notFound == 0) "Exported ${found.size} songs to Spotify"
                    else "Exported ${found.size} songs to Spotify ($notFound not found)"
                ExportResult(remotePlaylistId = remoteId, addedCount = found.size, notFoundCount = notFound, message = msg)
            }
            MusicSources.YOUTUBE_MUSIC -> {
                val videoIds = unique.mapConcurrently(EXPORT_MATCH_CONCURRENCY) { song ->
                    (song.youTubeVideoId()
                        ?: song.youtubeId?.takeIf { it.isNotBlank() }
                        ?: cloudSongDao.getById(song.id)?.youtubeId?.takeIf { it.isNotBlank() }
                        // Songs from your phone or other services: find them on YouTube Music.
                        ?: findYouTubeVideoId(song))
                        .also { tick() }
                }
                val found = videoIds.filterNotNull().distinct()
                val notFound = videoIds.count { it == null }
                if (found.isEmpty()) error("None of these songs were found on YouTube Music.")
                onProgress("Creating the playlist…")
                val remoteId = exportStep("create the YouTube Music playlist") { youtube.createPlaylist(name) }.removePrefix("VL")
                onProgress("Adding ${found.size} songs…")
                val actions = org.json.JSONArray()
                found.forEach { vid ->
                    actions.put(org.json.JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", vid).put("dedupeOption", "DEDUPE_OPTION_SKIP"))
                }
                exportStep("add songs on YouTube Music") { youtube.editPlaylist(remoteId, actions) }
                scope.launch { sync(MusicSources.YOUTUBE_MUSIC) }
                val msg = if (notFound == 0) "Exported ${found.size} songs to YouTube Music"
                    else "Exported ${found.size} songs to YouTube Music ($notFound not found)"
                ExportResult(remotePlaylistId = remoteId, addedCount = found.size, notFoundCount = notFound, message = msg)
            }
            else -> error("Unsupported service for export: $service")
        }
    }

    /** Runs one export step; a failure names the step ("Couldn't add songs on Spotify (…)"). */
    private suspend fun <T> exportStep(what: String, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw IllegalStateException("Couldn't $what (${e.message ?: e.javaClass.simpleName}).", e)
    }

    /** A YouTube Music song for [song] by title + artist, checked like Spotify matches. */
    private suspend fun findYouTubeVideoId(song: Song): String? {
        val results = ytSearch.get().searchSongs("${song.title} ${song.displayArtist}".trim()).getOrNull().orEmpty()
        val sourceMods = com.theveloper.pixelplay.data.spotify.TrackMatching.modifiers(song.title)
        return results.firstOrNull { c ->
            com.theveloper.pixelplay.data.spotify.TrackMatching.titlesMatch(song.title, c.title) &&
                com.theveloper.pixelplay.data.spotify.TrackMatching.artistsOverlap(song.displayArtist, c.displayArtist) &&
                com.theveloper.pixelplay.data.spotify.TrackMatching.modifiers(c.title) == sourceMods
        }?.let { it.youtubeId ?: it.id.takeIf { id -> id.startsWith("yt_") }?.removePrefix("yt_") }
    }

    private suspend fun <T, R> List<T>.mapConcurrently(limit: Int, transform: suspend (T) -> R): List<R> =
        kotlinx.coroutines.coroutineScope {
            val gate = kotlinx.coroutines.sync.Semaphore(limit)
            map { item -> async { gate.withPermit { transform(item) } } }.awaitAll()
        }

    /**
     * Pushes added songs and/or removed song IDs to a remote playlist (Spotify or YouTube Music).
     */
    suspend fun syncRemoteChanges(
        service: String,
        remotePlaylistId: String,
        addedSongs: List<Song>,
        removedSongIds: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        loaded.await()
        when (service) {
            MusicSources.SPOTIFY -> {
                if (addedSongs.isNotEmpty()) {
                    val ids = addedSongs.distinctBy { it.id }.mapNotNull { song ->
                        val direct = song.id.removePrefix("spotify_").takeIf { song.id.startsWith("spotify_") && !it.startsWith("unavailable") }
                        direct ?: runCatching { spotify.findTrackId(song.title, song.displayArtist, song.creditsAndRelease.isrc) }.getOrNull()
                    }
                    if (ids.isNotEmpty()) {
                        spotify.addTracksToPlaylist(remotePlaylistId, ids)
                    }
                }
                scope.launch { sync(MusicSources.SPOTIFY) }
            }
            MusicSources.YOUTUBE_MUSIC -> {
                val bareId = remotePlaylistId.removePrefix("VL")
                val actions = org.json.JSONArray()
                if (addedSongs.isNotEmpty()) {
                    val videoIds = addedSongs.distinctBy { it.id }.mapNotNull { song ->
                        song.youTubeVideoId()
                            ?: song.youtubeId?.takeIf { it.isNotBlank() }
                            ?: cloudSongDao.getById(song.id)?.youtubeId?.takeIf { it.isNotBlank() }
                    }
                    videoIds.forEach { vid ->
                        actions.put(org.json.JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", vid).put("dedupeOption", "DEDUPE_OPTION_SKIP"))
                    }
                }
                if (removedSongIds.isNotEmpty()) {
                    val tracks = runCatching { youtube.getPlaylistTracks(bareId) }.getOrNull().orEmpty()
                    val removedVids = removedSongIds.map { it.removePrefix("yt_") }.toSet()
                    tracks.filter { it.videoId in removedVids && it.setVideoId != null }.forEach {
                        actions.put(org.json.JSONObject().put("action", "ACTION_REMOVE_VIDEO").put("setVideoId", it.setVideoId).put("removedVideoId", it.videoId))
                    }
                }
                if (actions.length() > 0) {
                    youtube.editPlaylist(bareId, actions)
                }
                scope.launch { sync(MusicSources.YOUTUBE_MUSIC) }
            }
        }
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
            val updated = readPlaylist(target)
            lock.withLock {
                val current = mutable.value
                val nextPlaylists = current.playlists.map { if (it.id == playlistId) updated else it }
                save(current.copy(playlists = nextPlaylists, syncedAt = System.currentTimeMillis()))
            }
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { message.value = e.message ?: "Sync failed for ${target.title}" }
        finally { _syncingPlaylists.update { it - playlistId } }
    }
    /**
     * Re-reads one saved playlist from its service and returns the updated copy (not saved).
     * Pending edits are kept on top of the fresh list; friends' playlists stay capped at
     * [FRIEND_PLAYLIST_MAX_SONGS].
     */
    private suspend fun readPlaylist(target: ConnectedPlaylist): ConnectedPlaylist {
        val cap = if (target.friendId != null) FRIEND_PLAYLIST_MAX_SONGS else Int.MAX_VALUE
        return if (target.source == "SPOTIFY") preserve(target, target) { spotify.getPlaylistTracks(target.remoteId, cap).map { it.toSong() } }
        else if (target.source == MusicSources.APPLE_MUSIC) preserve(target, target) { appleMusic.getPlaylistTracks(target.remoteId).take(cap).map { it.toSong() } }
        else preserveYouTube(target, target, target.remoteTitle ?: target.title)
    }

    /**
     * Updates the given saved playlists in place (friends' or your own), one at a time: each is
     * re-read from its service and swapped into the snapshot as soon as it's done. Nothing is
     * removed or re-imported, and the account itself isn't re-listed. Returns how many failed.
     */
    suspend fun refreshPlaylists(playlistIds: Collection<String>): Int = withContext(Dispatchers.IO) {
        loaded.await()
        var failed = 0
        for (playlistId in playlistIds.distinct()) {
            val target = mutable.value.playlists.find { it.id == playlistId } ?: continue
            _syncingPlaylists.update { it + playlistId }
            try {
                if (target.source == "YOUTUBE_MUSIC" && target.edits != null) {
                    try { pushLock.withLock { pushPlaylistEdits(target) } } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                }
                val updated = readPlaylist(target)
                if (updated.error != null && updated.fetchedAt == target.fetchedAt) failed++
                lock.withLock {
                    val current = mutable.value
                    // Skip it if it was removed (e.g. friend hidden) while it was being read.
                    if (current.playlists.any { it.id == playlistId }) {
                        save(current.copy(playlists = current.playlists.map { if (it.id == playlistId) updated else it }))
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { failed++ }
            finally { _syncingPlaylists.update { it - playlistId } }
        }
        failed
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

    /**
     * Removes friends from PixelPlayer: their saved playlists are removed (and remembered, so a
     * sync doesn't import them again) and the friend ids are hidden from the friends feed.
     * Nothing changes on the platform.
     */
    suspend fun removeFriends(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        loaded.await()
        lock.withLock {
            val current = mutable.value
            val now = System.currentTimeMillis()
            val removed = current.playlists.filter { it.friendId != null && it.friendId in ids }
            val tombstones = removed.associate { p ->
                p.id to DeletedPlaylist(p.id, p.remoteId, p.source, p.title, p.coverUrl, now, remote = false, friendId = p.friendId)
            }
            val names = ids.associateWith { id ->
                current.aliases[id] ?: removed.firstOrNull { it.friendId == id }?.ownerName.orEmpty()
            }
            val removedIds = removed.mapTo(hashSetOf()) { it.id }
            save(current.copy(
                playlists = current.playlists.filterNot { it.id in removedIds },
                deletedPlaylists = current.deletedPlaylists.orEmpty() + tombstones,
                aliases = current.aliases - ids.toSet(),
                hiddenFriends = (current.hiddenFriends.orEmpty() + ids).distinct(),
                hiddenFriendNames = current.hiddenFriendNames.orEmpty() + names,
                pinnedFriendPlaylists = current.pinnedFriendPlaylists?.filterNot { it in removedIds }?.ifEmpty { null },
                // A removed friend is no longer pinned (restoring doesn't re-pin).
                pinnedFriends = current.pinnedFriends?.filterNot { it in ids }?.ifEmpty { null }
            ))
        }
    }

    /**
     * Undoes [removeFriends] for one friend: they show in the friends feed again, and the
     * playlists that were removed with them are put back (as placeholders, then re-read from the
     * service one at a time). Playlists hidden before this was tracked can't be matched to the
     * friend and stay under Hidden playlists.
     */
    /** Blocks people: removed like [removeFriends], and remembered so people lists leave them out. */
    suspend fun blockFriends(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        removeFriends(ids)
        lock.withLock {
            val current = mutable.value
            save(current.copy(blockedFriends = (current.blockedFriends.orEmpty() + ids).distinct()))
        }
    }

    suspend fun restoreFriend(id: String) = withContext(Dispatchers.IO) {
        loaded.await()
        val restoredIds = lock.withLock {
            val current = mutable.value
            val name = current.hiddenFriendNames?.get(id)
            val tombstones = current.deletedPlaylists.orEmpty().values.filter { it.friendId == id }
            val existing = current.playlists.mapTo(hashSetOf()) { it.id }
            val placeholders = tombstones.filter { it.id !in existing }.map { t ->
                ConnectedPlaylist(t.id, t.remoteId, t.source, t.title, t.coverUrl, friendId = id,
                    ownerName = name.orEmpty(), manual = true)
            }
            save(current.copy(
                playlists = current.playlists + placeholders,
                deletedPlaylists = (current.deletedPlaylists.orEmpty() - tombstones.map { it.id }.toSet()).ifEmpty { null },
                hiddenFriends = current.hiddenFriends?.minus(id)?.ifEmpty { null },
                hiddenFriendNames = current.hiddenFriendNames?.minus(id)?.ifEmpty { null },
                // Restoring someone from Hidden also unblocks them.
                blockedFriends = current.blockedFriends?.minus(id)?.ifEmpty { null },
                aliases = if (!name.isNullOrBlank() && id !in current.aliases) current.aliases + (id to name) else current.aliases
            ))
            placeholders.map { it.id }
        }
        if (restoredIds.isNotEmpty()) refreshPlaylists(restoredIds)
        Unit
    }

    /** Pins (or unpins) a friend's saved playlist into Your playlists. */
    suspend fun setFriendPlaylistPinned(playlistId: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        loaded.await()
        lock.withLock {
            val current = mutable.value
            val list = current.pinnedFriendPlaylists.orEmpty()
            val next = if (pinned) (list + playlistId).distinct() else list - playlistId
            save(current.copy(pinnedFriendPlaylists = next.ifEmpty { null }))
        }
    }

    /**
     * Renames several friends in one save. A null or blank name removes the alias, so the friend
     * shows their platform name again.
     */
    suspend fun renameFriends(changes: Map<String, String?>) = withContext(Dispatchers.IO) {
        if (changes.isEmpty()) return@withContext
        loaded.await()
        lock.withLock {
            val current = mutable.value
            val aliases = current.aliases.toMutableMap()
            changes.forEach { (id, name) ->
                val trimmed = name?.trim()
                if (trimmed.isNullOrBlank()) aliases.remove(id) else aliases[id] = trimmed
            }
            save(current.copy(aliases = aliases))
        }
    }

    /** Pins (or unpins) friends to the top of their Friends section. */
    suspend fun setFriendsPinned(ids: Collection<String>, pinned: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        loaded.await()
        lock.withLock {
            val current = mutable.value
            val list = current.pinnedFriends.orEmpty()
            val next = if (pinned) (list + ids).distinct() else list - ids.toSet()
            save(current.copy(pinnedFriends = next.ifEmpty { null }))
        }
    }

    suspend fun renameFriend(id: String, name: String) = withContext(Dispatchers.IO) {
        require(name.trim().isNotBlank()) { "Enter a friend's name." }
        loaded.await(); lock.withLock { save(mutable.value.copy(aliases = mutable.value.aliases + (id to name.trim()))) }
    }
    /**
     * The songs of a public playlist, fetched without saving the playlist (friends in the room
     * use these for a day). Playable songs are stored as cloud songs so the mix can resolve them,
     * the same as any online find; they don't appear in your library.
     */
    suspend fun fetchPublicPlaylistSongs(source: String, remoteId: String): List<Song> = withContext(Dispatchers.IO) {
        val songs = when (source) {
            "SPOTIFY" -> spotify.getPlaylistTracks(remoteId).map { it.toSong() }
            "YOUTUBE_MUSIC" -> youtube.getPlaylistTracks(remoteId).map { it.toSong() }
            MusicSources.APPLE_MUSIC -> if (!appleMusicAuth.hasSession) emptyList()
                else appleMusic.getPlaylistTracks(remoteId).map { it.toSong() }
            else -> emptyList()
        }
        songs.filter { it.contentUriString.isNotBlank() }.forEach { music.saveCloudSong(it) }
        songs
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
                    preserve(mutable.value.playlists.find { it.id == base.id }, base) { spotify.getPlaylistTracks(id, FRIEND_PLAYLIST_MAX_SONGS).map { it.toSong() } }
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
                    preserve(mutable.value.playlists.find { it.id == base.id }, base) { appleMusic.getPlaylistTracks(id).take(FRIEND_PLAYLIST_MAX_SONGS).map { it.toSong() } }
                }
                else -> error("Use a Spotify, YouTube Music or Apple Music playlist link.")
            }
            save(mutable.value.copy(playlists = mutable.value.playlists.filterNot { it.id == playlist.id } + playlist,
                aliases = if (knownFriendId != null) mutable.value.aliases else mutable.value.aliases + (friendId to name.trim()),
                hiddenFriends = mutable.value.hiddenFriends?.minus(friendId)?.ifEmpty { null },
                deletedPlaylists = (mutable.value.deletedPlaylists.orEmpty() - playlist.id).ifEmpty { null }))
            playlist.id
        }
    }
}

/** A friend's saved playlist is re-read from the service at most this often (sync). */
private const val FRIEND_PLAYLIST_REFRESH_MS = 24L * 60 * 60 * 1000

/** How many friends' playlists one sync re-reads (oldest first); the rest wait for later syncs. */
private const val FRIEND_PLAYLISTS_PER_SYNC = 3

/**
 * Most songs kept for a friend's playlist. Enough for blends and browsing; stops one 5,000-song
 * playlist from holding thousands of songs in memory (and in the saved snapshot).
 */
private const val FRIEND_PLAYLIST_MAX_SONGS = 1_000

/** Songs matched at once during an export (the Spotify client still paces its own requests). */
private const val EXPORT_MATCH_CONCURRENCY = 4
