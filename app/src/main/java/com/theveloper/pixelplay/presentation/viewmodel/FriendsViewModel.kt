package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.social.FriendActivityRepository
import com.theveloper.pixelplay.data.social.BlendMix
import com.theveloper.pixelplay.data.social.BlendMixGenerator
import com.theveloper.pixelplay.data.social.FriendAttribution
import com.theveloper.pixelplay.data.social.FriendFollowController
import com.theveloper.pixelplay.data.social.FriendQueueAttribution
import com.theveloper.pixelplay.data.social.toPlayableSong
import com.theveloper.pixelplay.data.social.FriendPresence
import com.theveloper.pixelplay.data.social.FriendTrack
import com.theveloper.pixelplay.data.social.presence
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@Immutable
data class FriendPlaylistUi(
    /** PixelPlayer playlist id when it's saved; null for a public playlist not saved yet. */
    val playlistId: String?,
    val remoteId: String,
    val source: String,
    val title: String,
    val coverUrl: String?,
    val songCount: Int,
    val durationMs: Long?,
)

@Immutable
data class FriendUi(
    val id: String,
    val name: String,
    /** Name on the platform, shown as a hint while renaming. */
    val platformName: String,
    val source: String,
    val avatarUrl: String?,
    val presence: FriendPresence,
    /** Current song when live, otherwise the last one seen. */
    val track: FriendTrack?,
    val playlists: List<FriendPlaylistUi>,
) {
    val isOnline: Boolean get() = presence == FriendPresence.LISTENING_NOW
    val attribution: FriendAttribution get() = FriendAttribution(id, name, avatarUrl)
}

/** One Friends Mix song and the friend who played it most recently. */
@Immutable
data class FriendsMixEntry(val song: Song, val friend: FriendUi, val playedAt: Long)

/** A song friends played that you never have. */
@Immutable
data class DiscoverySong(val song: Song, val track: FriendTrack, val friend: FriendUi)

/** An artist friends played that you never have. */
@Immutable
data class DiscoveryArtist(val name: String, val coverUrl: String?, val friends: List<FriendUi>, val plays: Int)

@Immutable
data class FriendsDiscovery(val artists: List<DiscoveryArtist> = emptyList(), val songs: List<DiscoverySong> = emptyList()) {
    val isEmpty: Boolean get() = artists.isEmpty() && songs.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val library: ConnectedLibraryRepository,
    private val activity: FriendActivityRepository,
    private val follow: FriendFollowController,
    private val stats: PlaybackStatsRepository,
    private val music: MusicRepository,
    private val dailyMixManager: com.theveloper.pixelplay.data.DailyMixManager,
    private val inRoom: com.theveloper.pixelplay.data.social.FriendsInRoomController,
    private val customBlendSelection: com.theveloper.pixelplay.data.social.CustomBlendSelection,
) : ViewModel() {

    /** Re-judges live / "2 h ago" every 30 s even when no new data arrives. */
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }

    val friends: StateFlow<List<FriendUi>> = combine(library.snapshot, activity.profiles, activity.live, clock) { snapshot, profiles, live, now ->
        val hidden = snapshot.hiddenFriends.orEmpty().toSet()
        val saved = snapshot.playlists.filter { it.friendId != null && it.friendId !in hidden }.groupBy { it.friendId!! }
        (saved.keys + profiles.keys).distinct().filter { it !in hidden }.map { id ->
            val profile = profiles[id]
            val mine = saved[id].orEmpty()
            val platformName = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: mine.firstOrNull()?.ownerName?.takeIf { it.isNotBlank() } ?: "Friend"
            val savedUi = mine.map { p ->
                FriendPlaylistUi(p.id, p.remoteId, p.source, p.title, p.coverUrl ?: p.songs.firstOrNull()?.albumArtUriString,
                    p.songs.size, p.songs.sumOf { it.duration }.takeIf { it > 0 })
            }
            val savedRemote = savedUi.mapTo(hashSetOf()) { it.source + ":" + it.remoteId }
            val publicUi = profile?.publicPlaylists.orEmpty()
                .filter { it.source + ":" + it.remoteId !in savedRemote }
                .map { FriendPlaylistUi(null, it.remoteId, it.source, it.title, it.coverUrl, it.trackCount, it.durationMs) }
            val status = live[id]
            FriendUi(
                id = id,
                name = snapshot.aliases[id] ?: platformName,
                platformName = platformName,
                source = id.substringBefore(':', "").takeIf { it == "SPOTIFY" || it == "YOUTUBE_MUSIC" || it == "APPLE_MUSIC" }
                    ?: mine.firstOrNull()?.source.orEmpty(),
                avatarUrl = profile?.avatarUrl,
                presence = status.presence(profile, now),
                track = status?.track ?: activity.history.value[id]?.firstOrNull(),
                playlists = savedUi + publicUi,
            )
        }.sortedWith(
            // Friends with playlists first, then who's listening, then most recent, then name.
            // (Sort keys must be Longs: mixing Int 0 with Long timestamps crashed the sort.)
            compareBy<FriendUi> { if (it.playlists.isEmpty()) 1 else 0 }
                .thenBy { it.presence.ordinal }
                .thenByDescending { it.track?.playedAt ?: 0L }
                .thenBy { it.name.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val friendsById: StateFlow<Map<String, FriendUi>> = friends.map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Listening right now, in the usual order. */
    val onlineFriends: StateFlow<List<FriendUi>> = friends.map { list -> list.filter { it.isOnline } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Everyone else, in the usual order. */
    val offlineFriends: StateFlow<List<FriendUi>> = friends.map { list -> list.filterNot { it.isOnline } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Friends' playlists pinned into Your playlists. */
    val pinnedPlaylistIds: StateFlow<Set<String>> = library.snapshot
        .map { it.pinnedFriendPlaylists.orEmpty().toSet() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // ---- Blends: one daily mix per friend, from your listening and theirs ----------------------

    /** Local day; changes at midnight so the blends rebuild once a day. */
    private val day = clock.map { BlendMixGenerator.today(it) }.distinctUntilChanged()

    /**
     * Your side of every blend: your playback history (most played first, then most recent),
     * topped up with play counts from Daily Mix. Refreshed when the day or your stats change.
     */
    private val myTopSongs: Flow<List<Song>> = combine(day, stats.refreshFlow) { today, _ -> today }.mapLatest {
        try {
            val history = stats.loadPlaybackHistory(MY_HISTORY_LIMIT).map { it.songId }
            val firstSeen = HashMap<String, Int>().apply { history.forEachIndexed { i, id -> putIfAbsent(id, i) } }
            val fromHistory = history.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { firstSeen[it.key] ?: Int.MAX_VALUE })
                .map { it.key }
            val fromEngagement = dailyMixManager.getAllEngagementStats().entries
                .filter { it.value.playCount > 0 }
                .sortedWith(compareByDescending<Map.Entry<String, com.theveloper.pixelplay.data.DailyMixManager.SongEngagementStats>> { it.value.playCount }
                    .thenByDescending { it.value.lastPlayedTimestamp })
                .map { it.key }
            val ids = (fromHistory + fromEngagement).distinct().take(MY_TOP_SONGS)
            if (ids.isEmpty()) emptyList() else {
                val byId = music.getSongsByIds(ids).first().associateBy { it.id }
                ids.mapNotNull { byId[it] }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }.onStart { emit(emptyList()) }

    /**
     * Every friend's side of a blend: their recent plays (most repeated first), then songs from
     * playlists of theirs you saved. Keyed by friend id.
     */
    private val friendSongs: Flow<Map<String, List<Song>>> =
        combine(friends, activity.history, library.snapshot) { friendList, history, snapshot ->
            friendList.associate { friend ->
                val recent = history[friend.id].orEmpty()
                    .groupBy { it.key }
                    .values
                    .sortedByDescending { it.size }
                    .mapNotNull { plays -> plays.first().toPlayableSong() }
                // Every playlist of theirs, taken in turns so no single playlist dominates.
                val fromPlaylists = com.theveloper.pixelplay.data.social.interleaveSongLists(
                    snapshot.playlists.filter { it.friendId == friend.id }.map { it.songs }
                )
                // 7-day history and playlists woven together, history first in each pair.
                friend.id to com.theveloper.pixelplay.data.social.interleaveSongLists(listOf(recent, fromPlaylists))
            }
        }.flowOn(Dispatchers.Default)
            // Shared by the per-friend blends, the custom blend and the song counts.
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** One blend per friend, in the same order as the friends list (a thin one says it's still building). */
    val blends: StateFlow<List<BlendMix>> = combine(friends, friendSongs, myTopSongs, day) { friendList, songsByFriend, mine, today ->
        friendList.map { friend ->
            BlendMixGenerator.build(
                friendId = friend.id,
                friendName = friend.name,
                avatarUrl = friend.avatarUrl,
                mine = mine,
                friend = songsByFriend[friend.id].orEmpty(),
                day = today,
            )
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Custom blend: you + every friend you tick, one playlist of up to 100 songs -------------

    /** Friends ticked for the custom blend (only ones still in your friends list). */
    val customBlendFriendIds: StateFlow<Set<String>> = combine(customBlendSelection.selected, friends) { selected, list ->
        if (list.isEmpty()) selected else selected intersect list.mapTo(HashSet()) { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), customBlendSelection.selected.value)

    /** How many songs are known for each friend (their side of a blend). */
    val friendSongCounts: StateFlow<Map<String, Int>> = friendSongs
        .map { byFriend -> byFriend.mapValues { (_, songs) -> songs.distinctBy { it.id }.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** The custom blend for the ticked friends; rebuilt whenever the ticks, anyone's listening or the day change. */
    val customBlend: StateFlow<com.theveloper.pixelplay.data.social.CustomBlend?> =
        combine(customBlendFriendIds, friends, friendSongs, myTopSongs, day) { selected, friendList, songsByFriend, mine, today ->
            val members = buildList {
                add(com.theveloper.pixelplay.data.social.BlendMember(BlendMixGenerator.ME, "You", null, mine))
                friendList.filter { it.id in selected }.forEach { friend ->
                    add(com.theveloper.pixelplay.data.social.BlendMember(friend.id, friend.name, friend.avatarUrl, songsByFriend[friend.id].orEmpty()))
                }
            }
            if (members.size < 2) null else BlendMixGenerator.buildGroup(members, today)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleCustomBlendFriend(friendId: String) {
        customBlendSelection.toggle(friendId)
        // Their public playlists join the blend once saved (history is already here).
        if (friendId in customBlendSelection.selected.value) inRoom.gatherPublicPlaylists(setOf(friendId))
    }

    fun setCustomBlendFriends(friendIds: Set<String>) {
        customBlendSelection.set(friendIds)
        inRoom.gatherPublicPlaylists(friendIds)
    }

    /** Tags each friend's songs in the custom blend with that friend and returns the songs to play. */
    fun prepareCustomBlend(): List<Song> {
        val blend = customBlend.value ?: return emptyList()
        val byId = friends.value.associateBy { it.id }
        FriendQueueAttribution.tagEach(
            blend.contributors.mapNotNull { (songId, memberId) -> byId[memberId]?.let { songId to it.attribution } }.toMap()
        )
        return blend.songs
    }

    val hasLiveSource: Boolean get() = activity.hasSources

    /** Collect while friends are on screen to keep their status fresh. */
    val polling: Flow<Unit> get() = activity.polling

    val notice = MutableStateFlow<String?>(null)

    fun history(friendId: String): Flow<List<FriendTrack>> = activity.historyFor(friendId)

    fun rename(friendId: String, name: String) = launch { library.renameFriend(friendId, name) }

    /** Removes friends from PixelPlayer (their saved playlists too). Nothing changes on the platform. */
    fun removeFriends(ids: Set<String>) = launch {
        if (follow.session.value?.friendId?.let { it in ids } == true) follow.stop()
        library.removeFriends(ids)
        FriendQueueAttribution.forgetFriends(ids)
    }

    fun addPlaylist(name: String, link: String, done: () -> Unit) = launch { library.addFriendPlaylist(name, link); done() }

    /** Saves a friend's public playlist into the library, then hands back its playlist id. */
    fun savePublicPlaylist(friend: FriendUi, playlist: FriendPlaylistUi, opened: (String) -> Unit) = launch {
        val link = com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId)
            ?: "https://music.youtube.com/playlist?list=${playlist.remoteId}"
        opened(library.addFriendPlaylist(friend.name, link, knownFriendId = friend.id))
    }

    /** Pins (likes) or unpins a friend's playlist in Your playlists; an unsaved one is saved first. */
    fun setPinned(friend: FriendUi, playlist: FriendPlaylistUi, pinned: Boolean) = launch {
        val id = playlist.playlistId ?: run {
            if (!pinned) return@launch
            val link = com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId)
                ?: "https://music.youtube.com/playlist?list=${playlist.remoteId}"
            library.addFriendPlaylist(friend.name, link, knownFriendId = friend.id)
        }
        library.setFriendPlaylistPinned(id, pinned)
    }

    /** Pins or unpins by playlist id (the playlist screen of an already saved friend playlist). */
    fun setPinned(playlistId: String, pinned: Boolean) = launch { library.setFriendPlaylistPinned(playlistId, pinned) }

    private val gathering = mutableSetOf<String>()

    /**
     * Opening a friend's row saves their public playlists into the library in the background,
     * so the songs are gathered and each playlist opens instantly with its real song count.
     */
    fun gatherPlaylists(friend: FriendUi) {
        val pending = friend.playlists.filter { it.playlistId == null && (it.source + ":" + it.remoteId) !in gathering }
        if (pending.isEmpty()) return
        pending.forEach { gathering += it.source + ":" + it.remoteId }
        viewModelScope.launch {
            for (playlist in pending) {
                val link = com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId) ?: continue
                try { library.addFriendPlaylist(friend.name, link, knownFriendId = friend.id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { gathering -= playlist.source + ":" + playlist.remoteId } // retried next time the row opens
            }
        }
    }

    /** A friend's song as something PixelPlayer can play (matched to audio when it starts), or null if unknown. */
    fun songFor(track: FriendTrack): Song? = track.toPlayableSong()

    /** Marks [songs] as coming from [friend], so their queue rows show the friend. */
    fun tag(friend: FriendUi, songs: Collection<Song>) {
        FriendQueueAttribution.tag(songs.map { it.id }, friend.id, friend.name, friend.avatarUrl)
    }

    // ---- Friends Mix --------------------------------------------------------------------------

    /**
     * Every song all friends played in the last 7 days (the rolling history window), each once,
     * newest first, with the friend who played it most recently. Updates as friends keep listening.
     */
    val friendsMix: StateFlow<List<FriendsMixEntry>> = combine(activity.history, friendsById) { history, byId ->
        history.flatMap { (friendId, tracks) ->
            val friend = byId[friendId] ?: return@flatMap emptyList()
            tracks.mapNotNull { track -> track.toPlayableSong()?.let { FriendsMixEntry(it, friend, track.playedAt) } }
        }
            .sortedByDescending { it.playedAt }
            .distinctBy { it.song.id }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Tags every mix song with its friend and returns the songs, shuffled, ready to play. */
    fun prepareFriendsMix(): List<Song> {
        val entries = friendsMix.value
        FriendQueueAttribution.tagEach(entries.associate { it.song.id to it.friend.attribution })
        return entries.map { it.song }.shuffled()
    }

    // ---- New from friends (Home) -----------------------------------------------------------------

    /** What I've played: song keys and artist names, refreshed when stats change. */
    private val myListening: Flow<Pair<Set<String>, Set<String>>> = stats.refreshFlow
        .mapLatest { stats.loadPlaybackHistory(MY_HISTORY_LIMIT).map { it.songId }.distinct() }
        .flatMapLatest { ids -> if (ids.isEmpty()) flowOf(emptyList()) else music.getSongsByIds(ids) }
        .map { songs ->
            val keys = HashSet<String>(songs.size * 2)
            val artists = HashSet<String>()
            songs.forEach { song ->
                keys += song.id
                keys += songKey(song.title, song.artist)
                artistNames(song.artist).forEach { artists += it }
            }
            Pair<Set<String>, Set<String>>(keys, artists)
        }
        .catch { emit(Pair(emptySet(), emptySet())) }

    /**
     * Friends' last 7 days minus everything I've played: artists I've never played (most friends,
     * then most plays) and songs I've never played (newest first).
     */
    val discovery: StateFlow<FriendsDiscovery> = combine(activity.history, friendsById, myListening) { history, byId, (myKeys, myArtists) ->
        val plays = history.flatMap { (friendId, tracks) ->
            val friend = byId[friendId] ?: return@flatMap emptyList()
            tracks.map { friend to it }
        }
        val songs = plays
            .filter { (_, track) -> track.title.isNotBlank() && songKey(track.title, track.artist) !in myKeys }
            .sortedByDescending { (_, track) -> track.playedAt }
            .mapNotNull { (friend, track) ->
                val song = track.toPlayableSong() ?: return@mapNotNull null
                if (song.id in myKeys) null else DiscoverySong(song, track, friend)
            }
            .distinctBy { songKey(it.track.title, it.track.artist) }
            .take(MAX_DISCOVERY_SONGS)
        val artists = plays
            .mapNotNull { (friend, track) -> artistNames(track.artist).firstOrNull()?.let { Triple(it, friend, track) } }
            .filter { (artist, _, _) -> artist !in myArtists }
            .groupBy { (artist, _, _) -> artist }
            .map { (_, group) ->
                val newest = group.maxBy { it.third.playedAt }
                DiscoveryArtist(
                    name = displayArtist(newest.third.artist),
                    coverUrl = newest.third.coverUrl,
                    friends = group.map { it.second }.distinctBy { it.id },
                    plays = group.size,
                )
            }
            .sortedWith(compareByDescending<DiscoveryArtist> { it.friends.size }.thenByDescending { it.plays })
            .take(MAX_DISCOVERY_ARTISTS)
        FriendsDiscovery(artists, songs)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FriendsDiscovery())

    /** Tags the discovery songs with their friends and returns them for playback. */
    fun prepareDiscoverySongs(): List<Song> {
        val songs = discovery.value.songs
        FriendQueueAttribution.tagEach(songs.associate { it.song.id to it.friend.attribution })
        return songs.map { it.song }
    }

    // ---- Friends in the room (queue options) ---------------------------------------------------

    /** Friend ids checked as being here in person; their playlists feed the queue. */
    val inRoomIds: StateFlow<Set<String>> = inRoom.selected

    fun setInRoom(ids: Set<String>) = inRoom.setSelection(ids)

    // ---- Following ---------------------------------------------------------------------------

    val following: StateFlow<FriendFollowController.Session?> = follow.session

    fun toggleFollow(friend: FriendUi) {
        if (follow.isFollowing(friend.id)) follow.stop() else follow.follow(friend.id, friend.name)
    }

    fun stopFollowing() = follow.stop()

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { notice.value = e.message ?: "Please try again." }
    }

    private companion object {
        const val MY_HISTORY_LIMIT = 3_000
        private const val MY_TOP_SONGS = 150
        const val MAX_DISCOVERY_SONGS = 20
        const val MAX_DISCOVERY_ARTISTS = 10
        val ARTIST_SPLIT = Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bfeaturing\b|\bx\b|/|;)\s*""", RegexOption.IGNORE_CASE)

        fun artistNames(raw: String): List<String> =
            raw.split(ARTIST_SPLIT).map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }

        fun displayArtist(raw: String): String =
            raw.split(ARTIST_SPLIT).firstOrNull { it.isNotBlank() }?.trim() ?: raw

        fun songKey(title: String, artist: String): String =
            title.trim().lowercase(Locale.ROOT) + "|" + (artistNames(artist).firstOrNull() ?: "")
    }
}
