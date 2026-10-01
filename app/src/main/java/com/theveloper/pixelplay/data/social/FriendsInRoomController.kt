package com.theveloper.pixelplay.data.social

import android.content.Context
import com.theveloper.pixelplay.data.MixVibe
import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.accounts.MusicSources
import com.theveloper.pixelplay.data.library.RecordingKeys
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * "Friends in the room": the friends who are here in person, picked from the queue's options
 * menu. While any are picked, the queue goes: one or two of your songs (random), then one song
 * from *each* friend in a random order, then back to you. Every friend song is the one from
 * their playlists + last 7 days that best fits what's playing ([AdaptiveMix.vibeFitNow]: feel,
 * transition, a locked vibe, queue removals / skips / hand-queued songs, More like this,
 * Energy, Focused ↔ Varied, and nothing you excluded), tagged with the friend so the queue row
 * shows their picture and name.
 *
 * It keeps going whatever builds the queue (Similar, a vibe filter, Local Mix, Smart Mix, a new
 * album or playlist): [sync] runs on every song change *and* every queue change, so a friend
 * song that a rebuild knocked out of up next is put straight back, and a friend whose unplayed
 * songs have run out starts over on songs that haven't played recently instead of stopping.
 */
@Singleton
class FriendsInRoomController @Inject constructor(
    @ApplicationContext context: Context,
    private val library: ConnectedLibraryRepository,
    private val activity: FriendActivityRepository,
    private val adaptive: dagger.Lazy<com.theveloper.pixelplay.data.AdaptiveMix>,
) {
    private val prefs = context.getSharedPreferences("friends_in_room", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _selected = MutableStateFlow(prefs.getStringSet(KEY_SELECTED, emptySet()).orEmpty().toSet())
    /** Friend ids checked as being in the room. Empty = off. */
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** Changes when friends' playlists load or change, so a waiting turn can be filled. */
    val poolChanges: StateFlow<*> get() = library.snapshot

    // Session bookkeeping (in memory: a new app session starts fresh).
    private val used = HashSet<String>()
    private val picksPerFriend = HashMap<String, Int>()
    private var mySongsSinceFriend = 0
    /** Your songs before the next round of friends: 1 or 2, drawn fresh after every round. */
    private var myTurnLength = nextTurnLength()
    /** Friends still to play in the current round (one song each), in a random order. */
    private val round = ArrayDeque<String>()
    /** The last few songs played (newest last): what a friend pick has to fit. */
    private val playedContext = ArrayDeque<Song>()
    private var lastSongId: String? = null
    /** The friend song lined up next and not yet played. */
    private var pending: Song? = null
    /** Times [pending] was put back after a rebuild removed it; given up after [MAX_REINSERTS]. */
    private var pendingReinserts = 0
    /** Most recent plays (ids and recording keys), never repeated when a pool starts over. */
    private val recent = ArrayDeque<String>()
    /** Up next as last seen, to tell a rebuild apart from the user removing or skipping past a song. */
    private var lastUpcoming: List<Song> = emptyList()

    /** Saves who's in the room; their unsaved public playlists are saved so their songs join the pool. */
    @Synchronized
    fun setSelection(ids: Set<String>) {
        val wasEmpty = _selected.value.isEmpty()
        _selected.value = ids
        prefs.edit().putStringSet(KEY_SELECTED, ids).apply()
        picksPerFriend.keys.retainAll(ids)
        round.retainAll(ids)
        when {
            ids.isEmpty() -> { mySongsSinceFriend = 0; pending = null; round.clear() }
            // Just switched on: the first round of friends starts straight away.
            wasEmpty -> mySongsSinceFriend = myTurnLength
        }
        gatherPublicPlaylists(ids)
    }

    fun gatherPublicPlaylists(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            val saved = library.snapshot.value.playlists.mapTo(hashSetOf()) { it.source + ":" + it.remoteId }
            for (id in ids) {
                val profile = activity.profiles.value[id] ?: continue
                val name = library.snapshot.value.aliases[id] ?: profile.displayName.ifBlank { "Friend" }
                for (playlist in profile.publicPlaylists.orEmpty()) {
                    if (playlist.source + ":" + playlist.remoteId in saved) continue
                    val link = MusicSources.playlistUrl(playlist.source, playlist.remoteId) ?: continue
                    try {
                        library.addFriendPlaylist(name, link, knownFriendId = id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Skipped; tried again next time the selection is saved.
                    }
                }
            }
        }
    }

    /**
     * Call on every current-song change and every queue change with the songs after the
     * playing one. Returns a friend song to put next (already tagged with its friend), or null
     * when nothing needs to change.
     */
    @Synchronized
    fun sync(current: Song?, upcoming: List<Song>): Song? {
        if (current == null) return null
        val ids = _selected.value
        val previousUpcoming = lastUpcoming
        lastUpcoming = upcoming
        if (current.id != lastSongId) {
            lastSongId = current.id
            markPlayed(current)
            val waiting = pending
            if (waiting != null && sameSong(waiting, current)) {
                pending = null
                pendingReinserts = 0
                mySongsSinceFriend = 0
                // Round over: back to you for 1–2 songs.
                if (round.isEmpty()) myTurnLength = nextTurnLength()
            } else {
                if (ids.isNotEmpty()) mySongsSinceFriend++
                // Jumped past the friend song in the queue: that was a skip, don't bring it back.
                if (waiting != null) {
                    val waitingAt = previousUpcoming.indexOfFirst { sameSong(it, waiting) }
                    val currentAt = previousUpcoming.indexOfFirst { it.id == current.id }
                    if (waitingAt >= 0 && currentAt > waitingAt) dropPending()
                }
            }
        } else {
            // Same song, and up next lost only the friend song: the user removed it by hand.
            val waiting = pending
            if (waiting != null && upcoming.none { sameSong(it, waiting) } &&
                previousUpcoming.any { sameSong(it, waiting) } &&
                previousUpcoming.filterNot { sameSong(it, waiting) }.map { it.id } == upcoming.map { it.id }
            ) {
                dropPending()
            }
        }
        if (ids.isEmpty()) {
            pending = null
            return null
        }
        pending?.let { waiting ->
            if (upcoming.any { sameSong(it, waiting) }) return null
            // A new queue, a mix button or a mix re-plan removed it: put the same song back.
            if (pendingReinserts < MAX_REINSERTS) {
                pendingReinserts++
                return waiting
            }
            // It won't stay in the queue (not playable here): pick another one instead.
            pending = null
            pendingReinserts = 0
        }
        // Mid-round the next friend goes straight after; otherwise wait for your 1–2 songs.
        if (round.isEmpty()) {
            if (mySongsSinceFriend < myTurnLength) return null
            round.addAll(ids.shuffled())
        }
        val pick = pickNext(current, ids) ?: return null
        pending = pick.first
        pendingReinserts = 0
        return pick.first
    }

    /**
     * The user didn't want the waiting friend song (removed it or skipped past it): that
     * friend's turn is used up, the rest of the round carries on.
     */
    private fun dropPending() {
        pending = null
        pendingReinserts = 0
        mySongsSinceFriend = 0
        if (round.isEmpty()) myTurnLength = nextTurnLength()
    }

    private fun nextTurnLength() = if (Random.nextDouble() < 0.5) 1 else 2

    private fun markPlayed(song: Song) {
        playedContext.removeAll { it.id == song.id }
        playedContext.addLast(song)
        while (playedContext.size > CONTEXT_SONGS) playedContext.removeFirst()
        val key = RecordingKeys.of(song)
        used += song.id
        used += key
        recent.addLast(song.id)
        recent.addLast(key)
        while (recent.size > RECENT_KEEP * 2) recent.removeFirst()
    }

    private fun sameSong(a: Song, b: Song): Boolean =
        a.id == b.id || RecordingKeys.of(a) == RecordingKeys.of(b)

    /**
     * The next friend of the round (skipping any with nothing left to offer) and, from their
     * pool, the song that best fits what's playing now.
     */
    private fun pickNext(current: Song, ids: Set<String>): Pair<Song, String>? {
        val snapshot = library.snapshot.value
        val history = activity.history.value
        val recentSet = recent.toHashSet()
        fun poolOf(friendId: String): List<Song> {
            // Their last 7 days of listening and all their playlists, together.
            val fromHistory = history[friendId].orEmpty().mapNotNull { it.toPlayableSong() }
            val songs = interleaveSongLists(listOf(fromHistory, interleaveSongLists(
                snapshot.playlists.filter { it.friendId == friendId }.map { it.songs })))
                .filter { it.contentUriString.isNotBlank() || it.youtubeId != null }
                .distinctBy { it.id }
            return songs.filter { it.id !in used && RecordingKeys.of(it) !in used }
                // Everything of theirs has played this session: start over, skipping recent plays.
                .ifEmpty { songs.filter { it.id !in recentSet && RecordingKeys.of(it) !in recentSet } }
                .ifEmpty { songs.filter { it.id != current.id } }
        }
        while (round.isNotEmpty()) {
            val friendId = round.removeFirst()
            if (friendId !in ids) continue
            val pool = poolOf(friendId)
            if (pool.isEmpty()) continue
            val best = bestFit(current, pool) ?: continue
            used += best.id
            used += RecordingKeys.of(best)
            picksPerFriend[friendId] = (picksPerFriend[friendId] ?: 0) + 1
            val profile = activity.profiles.value[friendId]
            val name = snapshot.aliases[friendId]
                ?: profile?.displayName?.takeIf { it.isNotBlank() }
                ?: snapshot.playlists.firstOrNull { it.friendId == friendId }?.ownerName?.takeIf { it.isNotBlank() }
                ?: "Friend"
            FriendQueueAttribution.tag(listOf(best.id), friendId, name, profile?.avatarUrl)
            return best to friendId
        }
        return null
    }

    /** The candidate that fits best; a little jitter so the same context doesn't always give the same song. */
    private fun bestFit(current: Song, pool: List<Song>): Song? {
        val candidates = pool.shuffled().take(MAX_CANDIDATES)
        val context = (playedContext.filter { it.id != current.id } + current).takeLast(CONTEXT_SONGS)
        val scores = try {
            adaptive.get().vibeFitNow(candidates, context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fall back to the plain transition fit.
            candidates.associate { it.id to (try { MixVibe.components(current, it).values.sum() } catch (_: Exception) { 0.0 }) }
        }
        // Songs the listener excluded are missing from the scores: never picked.
        return candidates.filter { it.id in scores }
            .maxByOrNull { (scores[it.id] ?: 0.0) + Random.nextDouble(0.0, JITTER) }
    }

    private companion object {
        const val KEY_SELECTED = "selected"
        /** Songs of context a friend pick has to fit (the current one counts most). */
        const val CONTEXT_SONGS = 3
        const val MAX_REINSERTS = 3
        const val RECENT_KEEP = 25
        const val MAX_CANDIDATES = 120
        const val JITTER = 0.6
    }
}
