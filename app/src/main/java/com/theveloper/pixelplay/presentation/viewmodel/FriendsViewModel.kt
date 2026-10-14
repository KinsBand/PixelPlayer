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
import com.theveloper.pixelplay.data.spotify.toSong
import com.theveloper.pixelplay.data.social.mineForMood
import com.theveloper.pixelplay.data.social.moodLens
import com.theveloper.pixelplay.data.social.FriendPresence
import com.theveloper.pixelplay.data.social.FriendTrack
import com.theveloper.pixelplay.data.social.presence
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
    /** Several people can add songs (when the platform says so). */
    val collaborative: Boolean = false,
    /** Who made it ("Spotify" for editorial playlists); null = unknown. */
    val ownerName: String? = null,
    /** When PixelPlayer last saw it change (ms); null = not seen changing. */
    val lastUpdated: Long? = null,
    /** Saves on the platform; -1 = unknown. */
    val saves: Int = -1,
    val description: String? = null,
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
    /** You follow each other on the platform (shows the "Friends" badge). */
    val isMutual: Boolean = false,
    /** Last time PixelPlayer saw them listening (ms); 0 = never. Orders Offline. */
    val lastActiveAt: Long = 0L,
    /** Pinned to the top of their section (Online / Offline). */
    val pinned: Boolean = false,
) {
    val isOnline: Boolean get() = presence == FriendPresence.LISTENING_NOW
    val attribution: FriendAttribution get() = FriendAttribution(id, name, avatarUrl)
}

/**
 * Someone's public profile in the friend history sheet: [userId] is a friend-style id
 * ("SPOTIFY:<id>"); [playlists] are their public playlists (saved ones carry their playlist id).
 */
@Immutable
data class SocialProfileUi(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val stats: com.theveloper.pixelplay.data.social.FriendSocialStats,
    val playlists: List<FriendPlaylistUi>,
)

/** The Add friend sheet's Spotify people search. Blank [query] = [results] are suggestions. */
@Immutable
data class UserSearchState(
    val query: String = "",
    val results: List<com.theveloper.pixelplay.data.social.SocialPerson> = emptyList(),
    /** First page (or suggestions) loading. */
    val isLoading: Boolean = false,
    /** A further page loading. */
    val isPaging: Boolean = false,
    val hasMore: Boolean = false,
    /** The last request failed (first page or a further one). */
    val failed: Boolean = false,
    /** People Spotify returned so far (paging offset; [results] can be fewer after ranking drops non-matches). */
    val fetched: Int = 0,
)

/** Add friend → a friend's chip: their followers + following, filtered locally by the search field. */
sealed interface FriendCircleState {
    data object Loading : FriendCircleState
    data class Loaded(val people: List<com.theveloper.pixelplay.data.social.SocialPerson>) : FriendCircleState
    data object Failed : FriendCircleState
}

/**
 * A blend re-mixed for one mood: built from everyone's songs that fit the vibe (not the daily
 * blend with non-matching songs hidden). [friendId] null = the custom blend.
 */
@Immutable
data class MoodBlend(
    val friendId: String?,
    val vibeId: String,
    val songs: List<Song>,
    /** Song id -> who it came from ([BlendMixGenerator.ME], a friend id or [BlendMixGenerator.BOTH]). */
    val contributors: Map<String, String>,
    val sharedSongIds: Set<String>,
    /** Still mixing; the page keeps showing the old list (narrowed to the mood) until it's ready. */
    val building: Boolean,
)

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
    private val socialGraph: com.theveloper.pixelplay.data.social.FriendSocialGraph,
    private val pinnedBlends: com.theveloper.pixelplay.data.social.PinnedBlends,
    private val spotifyWeb: com.theveloper.pixelplay.data.spotify.web.SpotifyWebLibrary,
) : ViewModel() {

    private val _cachingPlaylists = MutableStateFlow<Set<String>>(emptySet())
    val cachingPlaylists: StateFlow<Set<String>> = _cachingPlaylists.asStateFlow()

    init {
        // Who you follow and who follows you, for the mutual "Friends" badge (cached for 10 min).
        socialGraph.refresh()
        // Auto-sync for followed friends starts in the init block at the end of the class: it
        // reads [friends], which isn't initialised yet at this point (init blocks and property
        // initialisers run top to bottom, and viewModelScope starts the collector immediately).
    }

    /** Re-judges live / "2 h ago" every 30 s even when no new data arrives. */
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }

    val friends: StateFlow<List<FriendUi>> = combine(library.snapshot, activity.profiles, activity.live, clock, socialGraph.mutualFriendIds) { snapshot, profiles, live, now, mutual ->
        val hidden = snapshot.hiddenFriends.orEmpty().toSet()
        val pins = snapshot.pinnedFriends.orEmpty().toSet()
        val saved = snapshot.playlists.filter { it.friendId != null && it.friendId !in hidden }.groupBy { it.friendId!! }
        (saved.keys + profiles.keys).distinct().filter { it !in hidden }.map { id ->
            val profile = profiles[id]
            val mine = saved[id].orEmpty()
            val platformName = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: mine.firstOrNull()?.ownerName?.takeIf { it.isNotBlank() } ?: "Friend"
            val savedUi = mine.map { p ->
                // Saved copies keep the platform details PixelPlayer last saw for the public playlist.
                val public = profile?.publicPlaylists?.firstOrNull { it.source == p.source && it.remoteId == p.remoteId }
                FriendPlaylistUi(p.id, p.remoteId, p.source, p.title, p.coverUrl ?: p.songs.firstOrNull()?.albumArtUriString,
                    p.songs.size, p.songs.sumOf { it.duration }.takeIf { it > 0 },
                    collaborative = public?.collaborative == true,
                    ownerName = public?.ownerName ?: p.ownerName.ifBlank { null },
                    lastUpdated = public?.lastUpdated,
                    saves = public?.saves ?: -1,
                    description = public?.description)
            }
            val savedRemote = savedUi.mapTo(hashSetOf()) { it.source + ":" + it.remoteId }
            val publicUi = profile?.publicPlaylists.orEmpty()
                .filter { it.source + ":" + it.remoteId !in savedRemote }
                .map {
                    FriendPlaylistUi(null, it.remoteId, it.source, it.title, it.coverUrl, it.trackCount, it.durationMs,
                        collaborative = it.collaborative, ownerName = it.ownerName, lastUpdated = it.lastUpdated,
                        saves = it.saves, description = it.description)
                }
            val status = live[id]
            val shownTrack = status?.track ?: activity.history.value[id]?.firstOrNull()
            FriendUi(
                id = id,
                name = snapshot.aliases[id] ?: platformName,
                platformName = platformName,
                source = id.substringBefore(':', "").takeIf { it == "SPOTIFY" || it == "YOUTUBE_MUSIC" || it == "APPLE_MUSIC" }
                    ?: mine.firstOrNull()?.source.orEmpty(),
                avatarUrl = profile?.avatarUrl,
                presence = status.presence(profile, now),
                track = shownTrack,
                playlists = savedUi + publicUi,
                isMutual = id in mutual,
                // From their last song only (FriendLive.seenAt is the poll time, not activity).
                lastActiveAt = maxOf(shownTrack?.lastSeenAt ?: 0L, shownTrack?.playedAt ?: 0L),
                pinned = id in pins,
            )
        }.sortedWith(
            // Pinned first, then who's listening, then last active, then name.
            // (Sort keys must be Longs: mixing Int 0 with Long timestamps crashed the sort.)
            compareByDescending<FriendUi> { it.pinned }
                .thenBy { it.presence.ordinal }
                .thenByDescending { it.lastActiveAt }
                .thenBy { it.name.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val friendsById: StateFlow<Map<String, FriendUi>> = friends.map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Listening right now, in the usual order. */
    val onlineFriends: StateFlow<List<FriendUi>> = friends.map { list -> list.filter { it.isOnline } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True while at least one friend is listening right now (drives the nav-bar dot). */
    val hasFriendsOnline: StateFlow<Boolean> = friends.map { list -> list.any { it.isOnline } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Everyone else, in the usual order. */
    val offlineFriends: StateFlow<List<FriendUi>> = friends.map { list ->
        // Pinned first, then whoever was listening most recently (not "has playlists").
        list.filterNot { it.isOnline }.sortedWith(
            compareByDescending<FriendUi> { it.pinned }
                .thenByDescending { it.lastActiveAt }
                .thenBy { it.name.lowercase() }
        )
    }
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
    /**
     * Who the friends are (id, name, picture) without their live status. [friends] re-emits every
     * 30 s (presence is re-judged on a clock); the heavy blend work keys on this instead, so it only
     * reruns when friends really change, not on every tick.
     */
    private data class FriendIdentity(val id: String, val name: String, val avatarUrl: String?)

    private val friendIdentities: Flow<List<FriendIdentity>> = friends
        .map { list -> list.map { FriendIdentity(it.id, it.name, it.avatarUrl) } }
        .distinctUntilChanged()

    /** Each friend's saved playlists' songs, re-emitted only when those change (not on every library save). */
    private val friendPlaylistSongs: Flow<Map<String, List<List<Song>>>> = library.snapshot
        .map { snapshot -> snapshot.playlists.filter { it.friendId != null }.groupBy({ it.friendId!! }, { it.songs }) }
        .distinctUntilChanged()

    private val friendSongs: Flow<Map<String, List<Song>>> =
        combine(friendIdentities, activity.history, friendPlaylistSongs) { friendList, history, playlistsByFriend ->
            friendList.associate { friend ->
                val recent = history[friend.id].orEmpty()
                    .groupBy { it.key }
                    .values
                    .sortedByDescending { it.size }
                    .mapNotNull { plays -> plays.first().toPlayableSong() }
                // Every playlist of theirs, taken in turns so no single playlist dominates.
                val fromPlaylists = com.theveloper.pixelplay.data.social.interleaveSongLists(
                    playlistsByFriend[friend.id].orEmpty()
                )
                // 7-day history and playlists woven together, history first in each pair.
                friend.id to com.theveloper.pixelplay.data.social.interleaveSongLists(listOf(recent, fromPlaylists))
            }
        }.flowOn(Dispatchers.Default)
            // Shared by the per-friend blends, the custom blend and the song counts.
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** One blend per friend, in the same order as the friends list (a thin one says it's still building). */
    val blends: StateFlow<List<BlendMix>> = combine(friendIdentities, friendSongs, myTopSongs, day) { friendList, songsByFriend, mine, today ->
        BlendInputs(friendList, songsByFriend, mine, today)
    }.mapLatest { (friendList, songsByFriend, mine, today) ->
        // mapLatest: new inputs cancel a build still running instead of queueing another.
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

    private data class BlendInputs(
        val friends: List<FriendIdentity>,
        val songsByFriend: Map<String, List<Song>>,
        val mine: List<Song>,
        val day: Int,
    )

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
        combine(customBlendFriendIds, friendIdentities, friendSongs, myTopSongs, day) { selected, friendList, songsByFriend, mine, today ->
            Pair(selected, BlendInputs(friendList, songsByFriend, mine, today))
        }.mapLatest { (selected, inputs) ->
            val (friendList, songsByFriend, mine, today) = inputs
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

    // ---- Mood blends: a vibe chip on a blend page mixes a fresh blend for that mood ---------------

    private data class MoodRequest(val friendId: String?, val vibeId: String)

    private data class MoodInputs(val inputs: BlendInputs, val selected: Set<String>)

    private val moodRequest = MutableStateFlow<MoodRequest?>(null)

    /** Picks the mood for a blend page ([friendId] null = the custom blend); a null [vibeId] clears it. */
    fun setBlendMood(friendId: String?, vibeId: String?) {
        moodRequest.value = vibeId?.let { MoodRequest(friendId, it) }
    }

    /** The last mood's lens (vibe id -> lens), so friends' new plays don't re-read the whole library. */
    @Volatile
    private var moodLensCache: Pair<String, com.theveloper.pixelplay.data.social.BlendMoodLens>? = null

    private suspend fun moodLens(filter: com.theveloper.pixelplay.presentation.library.VibeFilter): com.theveloper.pixelplay.data.social.BlendMoodLens {
        moodLensCache?.let { (id, lens) -> if (id == filter.id) return lens }
        val library = music.getAllSongsOnce()
        return BlendMixGenerator.moodLens(library, com.theveloper.pixelplay.presentation.library.MusicVibeFilters.MATCH_THRESHOLD) {
            com.theveloper.pixelplay.presentation.library.MusicVibeFilters.score(it, filter)
        }.also { moodLensCache = filter.id to it }
    }

    /**
     * The blend re-mixed for the picked mood: your songs and each friend's that fit the vibe go
     * through the same mixer as the daily blend (fair turns, shared songs first, artist caps).
     * Rebuilt when the mood, the custom blend's friends, anyone's listening or the day change;
     * a newer request cancels a mix still running.
     */
    val moodBlend: StateFlow<MoodBlend?> = moodRequest.flatMapLatest<MoodRequest?, MoodBlend?> { request ->
        val filter = request?.let { r -> com.theveloper.pixelplay.presentation.library.MusicVibeFilters.presets.firstOrNull { it.id == r.vibeId } }
        if (request == null || filter == null) return@flatMapLatest flowOf<MoodBlend?>(null)
        val building = MoodBlend(request.friendId, request.vibeId, emptyList(), emptyMap(), emptySet(), building = true)
        combine(friendIdentities, friendSongs, myTopSongs, day, customBlendFriendIds) { friendList, songsByFriend, mine, today, selected ->
            MoodInputs(BlendInputs(friendList, songsByFriend, mine, today), selected)
        }.mapLatest { (inputs, selected) ->
            val (friendList, songsByFriend, mine, today) = inputs
            // Friends not loaded yet: keep showing the building state.
            if (friendList.isEmpty()) return@mapLatest building
            val lens = try {
                moodLens(filter)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { return@mapLatest building.copy(building = false) }
            val myMood = BlendMixGenerator.mineForMood(mine, lens)
            val friendId = request.friendId
            if (friendId != null) {
                val friend = friendList.firstOrNull { it.id == friendId } ?: return@mapLatest building.copy(building = false)
                val mix = BlendMixGenerator.build(
                    friendId = friend.id,
                    friendName = friend.name,
                    avatarUrl = friend.avatarUrl,
                    mine = myMood,
                    friend = songsByFriend[friend.id].orEmpty().filter(lens::fits),
                    day = today,
                )
                building.copy(
                    songs = mix.songs,
                    contributors = mix.contributors,
                    sharedSongIds = mix.contributors.filterValues { it == BlendMixGenerator.BOTH }.keys,
                    building = false,
                )
            } else {
                val members = buildList {
                    add(com.theveloper.pixelplay.data.social.BlendMember(BlendMixGenerator.ME, "You", null, myMood))
                    friendList.filter { it.id in selected }.forEach { friend ->
                        add(com.theveloper.pixelplay.data.social.BlendMember(
                            friend.id, friend.name, friend.avatarUrl, songsByFriend[friend.id].orEmpty().filter(lens::fits)
                        ))
                    }
                }
                if (members.size < 2) return@mapLatest building.copy(building = false)
                val mix = BlendMixGenerator.buildGroup(members, today)
                building.copy(songs = mix.songs, contributors = mix.contributors, sharedSongIds = mix.sharedSongIds, building = false)
            }
        }.onStart { emit(building) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Tags each friend's songs in the mood blend with that friend (for the queue badges) and returns them. */
    fun prepareMoodBlend(songs: List<Song>): List<Song> {
        val blend = moodBlend.value ?: return songs
        val byId = friends.value.associateBy { it.id }
        FriendQueueAttribution.tagEach(
            blend.contributors.mapNotNull { (songId, memberId) -> byId[memberId]?.let { songId to it.attribution } }.toMap()
        )
        return songs
    }

    // ---- Pinned blends: liked into Your playlists, still rebuilt every day ---------------------

    /** Keys of pinned blends ([com.theveloper.pixelplay.data.social.PinnedBlends.friendKey] / CUSTOM). */
    val pinnedBlendKeys: StateFlow<Set<String>> = pinnedBlends.keys

    fun setBlendPinned(key: String, pinned: Boolean) = pinnedBlends.setPinned(key, pinned)

    /**
     * Today's version of every pinned blend, as playlists for Your playlists. Each one is registered
     * as the day's blend page, so tapping it opens the current mix.
     */
    val pinnedBlendPlaylists: StateFlow<List<com.theveloper.pixelplay.data.model.Playlist>> =
        // Nothing pinned: no blend is built just for Your playlists.
        pinnedBlends.keys.flatMapLatest { pinned ->
            if (pinned.isEmpty()) flowOf(emptyList())
            else combine(blends, customBlend, friends) { friendBlends, custom, friendList ->
                pinnedBlendList(pinned, friendBlends, custom, friendList)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun pinnedBlendList(
        keys: Set<String>,
        friendBlends: List<BlendMix>,
        custom: com.theveloper.pixelplay.data.social.CustomBlend?,
        friendList: List<FriendUi>,
    ): List<com.theveloper.pixelplay.data.model.Playlist> = buildList {
                friendBlends.filter { com.theveloper.pixelplay.data.social.PinnedBlends.friendKey(it.friendId) in keys && it.songs.isNotEmpty() }
                    .forEach { blend ->
                        val id = com.theveloper.pixelplay.presentation.components.registerBlendPlaylist(blend)
                        add(com.theveloper.pixelplay.data.model.Playlist(id = id, name = blend.friendName,
                            songIds = blend.songs.map { it.id }, coverImageUri = blend.songs.firstOrNull()?.albumArtUriString))
                    }
                if (com.theveloper.pixelplay.data.social.PinnedBlends.CUSTOM in keys && custom != null && custom.songs.isNotEmpty()) {
                    val title = com.theveloper.pixelplay.presentation.components.customBlendTitle(custom, friendList)
                    val id = com.theveloper.pixelplay.presentation.components.registerCustomBlendPlaylist(custom, title)
                    add(com.theveloper.pixelplay.data.model.Playlist(id = id, name = title,
                        songIds = custom.songs.map { it.id }, coverImageUri = custom.songs.firstOrNull()?.albumArtUriString))
                }
            }

    // ---- Social: mutual follows, counts, followers / following lists --------------------------

    /** Spotify user ids you follow (drives Follow / Following / Friends on people cards). */
    val myFollowingIds: StateFlow<Set<String>> = socialGraph.myFollowing

    /** Friend ids where you follow each other. */
    val mutualFriendIds: StateFlow<Set<String>> = socialGraph.mutualFriendIds

    val myUserId: String get() = socialGraph.myUserId

    /** Your Spotify picture (the "you added this" badge on blend songs); null until loaded. */
    val myAvatarUrl: StateFlow<String?> = socialGraph.myAvatarUrl

    fun supportsSocial(friendId: String): Boolean = socialGraph.supports(friendId)

    /** Spotify is connected: the Add friend sheet can search people. */
    val canSearchPeople: Boolean get() = socialGraph.canSearch

    /** A friend's follower / following counts; null when unknown (other platforms, offline). */
    suspend fun socialStats(friendId: String): com.theveloper.pixelplay.data.social.FriendSocialStats? =
        try { socialGraph.stats(friendId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /** A friend's followers or following; throws when it can't load (the sheet shows Retry). */
    suspend fun socialPeople(friendId: String, followers: Boolean, force: Boolean): List<com.theveloper.pixelplay.data.social.SocialPerson> {
        val blocked = library.snapshot.value.blockedFriends.orEmpty().toSet()
        return socialGraph.people(friendId, followers, force).filterNot { it.friendId in blocked }
    }

    /**
     * Blocks someone: unfollows them on Spotify (when it's a Spotify person), hides them from
     * Friends (their saved playlists too) and keeps them out of followers / following lists.
     * Undo from Accounts → Hidden.
     */
    fun blockPerson(friendId: String, name: String, avatarUrl: String?) = launch {
        val userId = com.theveloper.pixelplay.data.social.FriendSocialGraph.spotifyUserId(friendId)
        if (userId != null && socialGraph.supports(friendId) && userId in socialGraph.myFollowing.value) {
            try {
                socialGraph.setFollowing(com.theveloper.pixelplay.data.social.SocialPerson(userId, name, avatarUrl, -1), follow = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                timber.log.Timber.w(e, "Unfollow on block failed")
            }
        }
        if (follow.session.value?.friendId == friendId) follow.stop()
        library.blockFriends(listOf(friendId))
    }

    /** Hides someone from Friends (their saved playlists too); undo from Accounts → Hidden. */
    fun hidePerson(friendId: String) = launch {
        if (follow.session.value?.friendId == friendId) follow.stop()
        library.removeFriends(listOf(friendId))
    }

    /** Follows / unfollows someone on Spotify. */
    fun setPersonFollowing(person: com.theveloper.pixelplay.data.social.SocialPerson, follow: Boolean) =
        launch { socialGraph.setFollowing(person, follow) }

    /**
     * Anyone's public profile ([userId] = "SPOTIFY:<id>"), with their public playlists matched to
     * ones already saved in the library. Throws when it can't load (the sheet shows Retry).
     */
    suspend fun userProfile(userId: String, force: Boolean = false): SocialProfileUi {
        val profile = socialGraph.profile(userId, force)
        val saved = library.snapshot.value.playlists.associateBy { it.source + ":" + it.remoteId }
        val playlists = profile.playlists.map { p ->
            val key = com.theveloper.pixelplay.data.accounts.MusicSources.SPOTIFY + ":" + p.id
            val savedCopy = saved[key]
            FriendPlaylistUi(
                playlistId = savedCopy?.id,
                remoteId = p.id,
                source = com.theveloper.pixelplay.data.accounts.MusicSources.SPOTIFY,
                title = p.title,
                coverUrl = p.coverUrl ?: savedCopy?.coverUrl,
                songCount = savedCopy?.songs?.size ?: p.totalTracks,
                durationMs = savedCopy?.songs?.sumOf { it.duration }?.takeIf { it > 0 },
                collaborative = p.collaborative,
                ownerName = p.ownerName.ifBlank { null },
                saves = p.saves,
                description = p.description,
            )
        }
        return SocialProfileUi(profile.friendId, profile.name, profile.avatarUrl, profile.stats, playlists)
    }

    /**
     * Opens someone's public playlist from their profile. Saved = straight there. A friend's is
     * saved into the library (like from the Friends list). Anyone else's is read once and opened
     * as a read-only page, so browsing a stranger doesn't add them to your friends.
     */
    fun openAccountPlaylist(ownerId: String, playlist: FriendPlaylistUi, opened: (String) -> Unit) = launch {
        playlist.playlistId?.let { opened(it); return@launch }
        val friend = friends.value.firstOrNull { it.id == ownerId }
        if (friend != null) {
            savePublicPlaylist(friend, playlist, opened)
            return@launch
        }
        val songs = kotlinx.coroutines.withContext(Dispatchers.IO) {
            spotifyWeb.playlistTracks(playlist.remoteId, ACCOUNT_PLAYLIST_MAX_SONGS).map { it.toSong() }
        }
        if (songs.isEmpty()) {
            notice.value = "This playlist is empty or private."
            return@launch
        }
        val page = com.theveloper.pixelplay.data.model.Playlist(
            id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}social:${playlist.source}:${playlist.remoteId}",
            name = playlist.title,
            songIds = songs.map { it.id },
            coverImageUri = playlist.coverUrl ?: songs.firstOrNull()?.albumArtUriString,
        )
        PlaylistViewModel.registerTransientPlaylist(page, songs)
        opened(page.id)
    }

    /** Long press on a profile's playlist: pins it into Your playlists (saving it under its owner). */
    fun setAccountPlaylistPinned(ownerId: String, ownerName: String, playlist: FriendPlaylistUi, pinned: Boolean) {
        val friend = friends.value.firstOrNull { it.id == ownerId }
            ?: FriendUi(ownerId, ownerName, ownerName, com.theveloper.pixelplay.data.accounts.MusicSources.SPOTIFY,
                null, FriendPresence.UNKNOWN, null, emptyList())
        setPinned(friend, playlist, pinned)
    }

    // ---- Add friend: Spotify people search ------------------------------------------------------

    private val _userSearch = MutableStateFlow(UserSearchState())
    val userSearch: StateFlow<UserSearchState> = _userSearch.asStateFlow()
    private var userSearchJob: kotlinx.coroutines.Job? = null

    /** Starts a new search for [query] (blank = suggestions). The sheet debounces typing. */
    fun searchSpotifyUsers(query: String) {
        val term = query.trim()
        if (term == _userSearch.value.query && !_userSearch.value.failed && (_userSearch.value.results.isNotEmpty() || _userSearch.value.isLoading)) return
        userSearchJob?.cancel()
        _userSearch.value = UserSearchState(query = term, isLoading = true)
        userSearchJob = viewModelScope.launch {
            try {
                if (term.isBlank()) {
                    val people = socialGraph.suggestions()
                    _userSearch.value = UserSearchState(query = term, results = people, fetched = people.size)
                } else {
                    val page = socialGraph.searchUsers(term, offset = 0)
                    // Your friends and the strongest name matches first (Spotify's order breaks ties).
                    val ranked = com.theveloper.pixelplay.data.social.ProfileRanking.rank(term, page.people, profileRankContext())
                    _userSearch.value = UserSearchState(query = term, results = ranked, hasMore = page.hasMore, fetched = page.people.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _userSearch.value = UserSearchState(query = term, failed = true)
            }
        }
    }

    /** Next page of the current search (no-op while loading, at the end, or for suggestions). */
    fun loadMoreSpotifyUsers() {
        val state = _userSearch.value
        if (state.query.isBlank() || state.isLoading || state.isPaging || !state.hasMore) return
        _userSearch.value = state.copy(isPaging = true, failed = false)
        userSearchJob = viewModelScope.launch {
            try {
                val page = socialGraph.searchUsers(state.query, offset = state.fetched)
                // Only the new page is ranked and appended: rows already on screen don't jump.
                val ranked = com.theveloper.pixelplay.data.social.ProfileRanking.rank(state.query, page.people, profileRankContext())
                _userSearch.update { now ->
                    if (now.query != state.query) now
                    else now.copy(
                        results = (now.results + ranked).distinctBy { it.userId },
                        hasMore = page.hasMore && page.people.isNotEmpty(),
                        isPaging = false,
                        fetched = now.fetched + page.people.size,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _userSearch.update { now -> if (now.query != state.query) now else now.copy(isPaging = false, failed = true) }
            }
        }
    }

    /** Retry after a failed first page or further page. */
    fun retryUserSearch() {
        val state = _userSearch.value
        if (state.results.isEmpty()) {
            searchSpotifyUsers(state.query) // failed = true, so this restarts the first page
        } else {
            _userSearch.value = state.copy(failed = false)
            loadMoreSpotifyUsers()
        }
    }

    private val _refreshing = MutableStateFlow(false)
    /** True while pull-to-refresh is polling friends. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Pull-to-refresh: polls friend activity and playlists now, and re-reads who follows whom. */
    fun refreshNow() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                socialGraph.refresh(force = true)
                activity.refreshNow()
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
            } finally { _refreshing.value = false }
        }
    }

    val hasLiveSource: Boolean get() = activity.hasSources

    /** Collect while friends are on screen to keep their status fresh. */
    val polling: Flow<Unit> get() = activity.polling

    /** Checking who's online / playing (first poll after the screen opens). */
    val checkingActivity: StateFlow<Boolean> get() = activity.checking

    val notice = MutableStateFlow<String?>(null)

    fun history(friendId: String): Flow<List<FriendTrack>> = activity.historyFor(friendId)

    fun rename(friendId: String, name: String) = launch { library.renameFriend(friendId, name) }

    /** Renames several friends at once; a null / blank name goes back to their platform name. */
    fun renameFriends(changes: Map<String, String?>) = launch { library.renameFriends(changes) }

    /** Pins / unpins friends to the top of their section (Online or Offline). */
    fun setPinned(ids: Set<String>, pinned: Boolean) = launch { library.setFriendsPinned(ids, pinned) }

    /**
     * Removes friends from PixelPlayer (their saved playlists too). With [unfollowOnSpotify], also
     * unfollows the Spotify ones on your real Spotify account first; a failed unfollow doesn't stop
     * the removal (a notice says how many failed).
     */
    fun removeFriends(ids: Set<String>, unfollowOnSpotify: Boolean = false) = launch {
        var failed = 0
        if (unfollowOnSpotify) {
            val byId = friendsById.value
            for (id in ids) {
                val userId = com.theveloper.pixelplay.data.social.FriendSocialGraph.spotifyUserId(id) ?: continue
                if (!socialGraph.supports(id)) continue
                val friend = byId[id]
                try {
                    socialGraph.setFollowing(
                        com.theveloper.pixelplay.data.social.SocialPerson(userId, friend?.platformName ?: userId, friend?.avatarUrl, -1),
                        follow = false
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "Unfollow failed")
                    failed++
                }
            }
        }
        if (follow.session.value?.friendId?.let { it in ids } == true) follow.stop()
        library.removeFriends(ids)
        FriendQueueAttribution.forgetFriends(ids)
        if (failed > 0) unfollowFailed.value = failed
    }

    /** How many unfollows failed in the last Remove (the screen shows a notice); 0 = none. */
    val unfollowFailed = MutableStateFlow(0)

    /** People-search ranking context: your friends' names (aliases too), mutuals, who you follow. */
    private fun profileRankContext(): com.theveloper.pixelplay.data.social.ProfileRankContext {
        val names = HashMap<String, List<String>>()
        friends.value.forEach { f ->
            val userId = com.theveloper.pixelplay.data.social.FriendSocialGraph.spotifyUserId(f.id) ?: return@forEach
            names[userId] = listOf(f.name, f.platformName).distinct()
        }
        return com.theveloper.pixelplay.data.social.ProfileRankContext(
            friendNames = names,
            mutual = socialGraph.mutualFriendIds.value.mapNotNullTo(HashSet()) {
                com.theveloper.pixelplay.data.social.FriendSocialGraph.spotifyUserId(it)
            },
            following = socialGraph.myFollowing.value,
        )
    }

    // ---- Add friend: a friend's circle -----------------------------------------------------------

    private val _addFriendFilter = MutableStateFlow<String?>(null)
    /** The friend whose followers + following the Add friend sheet shows; null = Everyone. */
    val addFriendFilter: StateFlow<String?> = _addFriendFilter.asStateFlow()
    private val _friendCircle = MutableStateFlow<FriendCircleState>(FriendCircleState.Loading)
    val friendCircle: StateFlow<FriendCircleState> = _friendCircle.asStateFlow()
    private var friendCircleJob: kotlinx.coroutines.Job? = null

    /** Picks a friend chip (loads their circle) or Everyone (null). */
    fun setAddFriendFilter(friendId: String?, force: Boolean = false) {
        _addFriendFilter.value = friendId
        friendCircleJob?.cancel()
        if (friendId == null) return
        _friendCircle.value = FriendCircleState.Loading
        friendCircleJob = viewModelScope.launch {
            try {
                val people = kotlinx.coroutines.coroutineScope {
                    val followers = async { socialGraph.people(friendId, followers = true, force = force) }
                    val following = async { socialGraph.people(friendId, followers = false, force = force) }
                    (followers.await() + following.await())
                }.distinctBy { it.userId }.take(FRIEND_CIRCLE_MAX)
                _friendCircle.value = FriendCircleState.Loaded(people)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _friendCircle.value = FriendCircleState.Failed
            }
        }
    }

    /** The circle filtered by the sheet's text (local, no network per key). */
    fun rankCircle(query: String, people: List<com.theveloper.pixelplay.data.social.SocialPerson>) =
        com.theveloper.pixelplay.data.social.ProfileRanking.rank(query, people, profileRankContext())

    fun addPlaylist(name: String, link: String, done: () -> Unit) = launch { library.addFriendPlaylist(name, link); done() }

    /** Saves a friend's public playlist into the library, then hands back its playlist id. */
    fun savePublicPlaylist(friend: FriendUi, playlist: FriendPlaylistUi, opened: (String) -> Unit) = launch {
        val link = com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId)
            ?: "https://music.youtube.com/playlist?list=${playlist.remoteId}"
        opened(library.addFriendPlaylist(friend.name, link, knownFriendId = friend.id))
    }

    /**
     * Pins (likes) or unpins a friend's playlist in Your playlists; an unsaved one is saved first.
     * The heart flips at once ([pinnedPlaylistKeys]); saving can take a few seconds (all its songs
     * are read), and a failure flips it back with a notice.
     */
    fun setPinned(friend: FriendUi, playlist: FriendPlaylistUi, pinned: Boolean) {
        val key = playlist.source + ":" + playlist.remoteId
        withPendingPin(key, pinned) {
            val id = playlist.playlistId ?: run {
                if (!pinned) return@withPendingPin
                val link = com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId)
                    ?: "https://music.youtube.com/playlist?list=${playlist.remoteId}"
                library.addFriendPlaylist(friend.name, link, knownFriendId = friend.id)
            }
            library.setFriendPlaylistPinned(id, pinned)
        }
    }

    /** Pins or unpins by playlist id (the playlist screen of an already saved friend playlist). */
    fun setPinned(playlistId: String, pinned: Boolean) =
        withPendingPin(playlistId, pinned) { library.setFriendPlaylistPinned(playlistId, pinned) }

    /** Pins being saved right now: key ("SOURCE:remoteId", the saved playlist's id) -> pinned. */
    private val pendingPins = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /**
     * Pinned friend playlists by "SOURCE:remoteId" (the same as a saved playlist's id), including
     * pins still being saved, so hearts and the card's pinned mark update straight away.
     */
    val pinnedPlaylistKeys: StateFlow<Set<String>> = combine(pinnedPlaylistIds, pendingPins) { ids, pending ->
        (ids + pending.filterValues { it }.keys) - pending.filterValues { !it }.keys
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private fun withPendingPin(key: String, pinned: Boolean, block: suspend () -> Unit) {
        pendingPins.update { it + (key to pinned) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice.value = e.message ?: "Couldn't update this playlist. Please try again."
            } finally {
                pendingPins.update { it - key }
            }
        }
    }

    private val gathering = mutableSetOf<String>()

    /**
     * Opening a friend's row saves their public playlists into the library in the background,
     * so the songs are gathered and each playlist opens instantly with its real song count.
     */
    fun gatherPlaylists(friend: FriendUi) {
        // Only the first few, and not giant ones: each saved playlist is read in full and kept in the
        // library. The rest are saved when opened or pinned.
        val pending = friend.playlists
            .filter { it.playlistId == null && (it.source + ":" + it.remoteId) !in gathering && it.songCount <= GATHER_MAX_SONGS }
            .take(GATHER_PER_FRIEND)
        if (pending.isEmpty()) return
        val pendingKeys = pending.map { it.source + ":" + it.remoteId }
        pendingKeys.forEach { gathering += it }
        _cachingPlaylists.update { it + pendingKeys }
        viewModelScope.launch {
            try {
                for (playlist in pending) {
                    val link = com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId) ?: continue
                    try { library.addFriendPlaylist(friend.name, link, knownFriendId = friend.id) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { gathering -= playlist.source + ":" + playlist.remoteId } // retried next time the row opens
                }
            } finally {
                _cachingPlaylists.update { it - pendingKeys.toSet() }
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

    /**
     * Tags every mix song with its friend and returns them newest first, for the Friends Mix
     * playlist page (a stable order to browse and filter; its Shuffle button shuffles).
     */
    fun prepareFriendsMixPage(): List<Song> {
        val entries = friendsMix.value
        FriendQueueAttribution.tagEach(entries.associate { it.song.id to it.friend.attribution })
        return entries.map { it.song }
    }

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
    /** Mix it in (default) or Take turns. */
    val inRoomMode: StateFlow<com.theveloper.pixelplay.data.social.FriendsRoomMode> = inRoom.mode
    fun setInRoomMode(mode: com.theveloper.pixelplay.data.social.FriendsRoomMode) = inRoom.setMode(mode)
    /** Friends' share of the mix in Mix it in (default Even). */
    val inRoomShare: StateFlow<com.theveloper.pixelplay.data.social.FriendsShare> = inRoom.share
    fun setInRoomShare(share: com.theveloper.pixelplay.data.social.FriendsShare) = inRoom.setShare(share)

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

    init {
        // Followed friends (you follow them, or you follow each other): their public playlists
        // are cached in the background so they open instantly. Everyone else keeps the manual
        // Save / pin. [gatherPlaylists] skips ones already saved or in flight, and caps how many
        // and how large, so re-emissions (the 30 s clock) cost nothing.
        viewModelScope.launch {
            combine(friends, socialGraph.myFollowing) { friendList, followingIds ->
                friendList.filter { friend ->
                    friend.isMutual || friend.id in followingIds || friend.id.substringAfter(':') in followingIds
                }
            }.collect { followedFriends ->
                followedFriends.forEach { gatherPlaylists(it) }
            }
        }
    }

    private companion object {
        /** Most people kept for one friend's circle (followers + following; memory cap). */
        const val FRIEND_CIRCLE_MAX = 500
        const val MY_HISTORY_LIMIT = 3_000
        /** Public playlists saved in the background when a friend's row opens. */
        const val GATHER_PER_FRIEND = 4
        /** Bigger public playlists aren't saved in the background (open or pin them instead). */
        const val GATHER_MAX_SONGS = 1_000
        private const val MY_TOP_SONGS = 150
        const val MAX_DISCOVERY_SONGS = 20
        const val MAX_DISCOVERY_ARTISTS = 10
        /** Someone else's playlist opened from their profile is read up to this many songs. */
        const val ACCOUNT_PLAYLIST_MAX_SONGS = 1_000
        val ARTIST_SPLIT = Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bfeaturing\b|\bx\b|/|;)\s*""", RegexOption.IGNORE_CASE)

        fun artistNames(raw: String): List<String> =
            raw.split(ARTIST_SPLIT).map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }

        fun displayArtist(raw: String): String =
            raw.split(ARTIST_SPLIT).firstOrNull { it.isNotBlank() }?.trim() ?: raw

        fun songKey(title: String, artist: String): String =
            title.trim().lowercase(Locale.ROOT) + "|" + (artistNames(artist).firstOrNull() ?: "")
    }
}
