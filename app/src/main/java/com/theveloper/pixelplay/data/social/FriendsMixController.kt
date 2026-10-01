package com.theveloper.pixelplay.data.social

import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Queue name used for the Friends Mix; the live top-up runs only while this queue is playing. */
const val FRIENDS_MIX_QUEUE_NAME = "Friends Mix"

/**
 * Keeps the Friends Mix growing through the week: while it's the playing queue, every new song a
 * friend plays (that isn't already in the mix) is handed back to be added to the end of the queue,
 * tagged with that friend.
 */
@Singleton
class FriendsMixController @Inject constructor(
    private val activity: FriendActivityRepository,
    private val library: ConnectedLibraryRepository,
) {
    /**
     * New friend plays as songs, starting from what's already in the history (those are in the
     * mix already). Keeps friend activity polling while collected.
     */
    fun liveAdditions(): Flow<Song> = channelFlow {
        launch { activity.polling.collect { } }
        var seen: Set<String>? = null
        activity.history.collect { history ->
            val hidden = library.snapshot.value.hiddenFriends.orEmpty().toSet()
            val all = history.filterKeys { it !in hidden }
                .flatMap { (friendId, tracks) -> tracks.map { friendId to it } }
            val keys = all.mapTo(hashSetOf()) { (_, track) -> track.key }
            val previous = seen
            seen = (previous.orEmpty() + keys)
            if (previous == null) return@collect
            val fresh = all.filter { (_, track) -> track.key !in previous }
                .sortedBy { (_, track) -> track.playedAt }
                .distinctBy { (_, track) -> track.key }
            for ((friendId, track) in fresh) {
                val song = track.toPlayableSong() ?: continue
                val profile = activity.profiles.value[friendId]
                val name = library.snapshot.value.aliases[friendId]
                    ?: profile?.displayName?.takeIf { it.isNotBlank() } ?: "Friend"
                FriendQueueAttribution.tag(listOf(song.id), friendId, name, profile?.avatarUrl)
                send(song)
            }
        }
    }
}
