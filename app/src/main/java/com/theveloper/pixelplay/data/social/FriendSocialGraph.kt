package com.theveloper.pixelplay.data.social

import com.theveloper.pixelplay.data.accounts.MusicSources
import com.theveloper.pixelplay.data.spotify.web.SpotifyPublicProfile
import com.theveloper.pixelplay.data.spotify.web.SpotifySocialRepository
import com.theveloper.pixelplay.data.spotify.web.SpotifyWebSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Someone in a friend's followers / following list. */
data class SocialPerson(
    /** Platform user id (Spotify user id). */
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    /** Their own follower count; 0 (or -1 from search) when the platform didn't say. */
    val followers: Int,
    /** How many people they follow; -1 = the platform didn't say. */
    val following: Int = -1,
) {
    val friendId: String get() = "${MusicSources.SPOTIFY}:$userId"
}

/** A friend's follower and following counts; -1 = the platform didn't say. */
data class FriendSocialStats(val followers: Int, val following: Int)

/** Someone's public Spotify profile: who they are, their counts and public playlists. */
data class SocialProfile(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val stats: FriendSocialStats,
    val playlists: List<com.theveloper.pixelplay.data.spotify.SpotifyPlaylist>,
) {
    val friendId: String get() = "${MusicSources.SPOTIFY}:$userId"
}

/** One page of people search; [hasMore] = another page can be asked for. */
data class SocialSearchPage(val people: List<SocialPerson>, val hasMore: Boolean)

/**
 * Who follows whom on Spotify: your own following / followers (for the mutual "Friends" badge),
 * a friend's counts, their followers / following lists, and following or unfollowing someone on
 * your real Spotify account. Spotify friends only; other platforms report nothing here.
 */
@Singleton
class FriendSocialGraph @Inject constructor(
    private val social: SpotifySocialRepository,
    private val session: SpotifyWebSession,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadLock = Mutex()
    @Volatile private var loadedAt = 0L

    private val _myFollowing = MutableStateFlow<Set<String>>(emptySet())
    private val _myFollowers = MutableStateFlow<Set<String>>(emptySet())

    /** Spotify user ids you follow. */
    val myFollowing: StateFlow<Set<String>> = _myFollowing.asStateFlow()

    /** Spotify user ids that follow you. */
    val myFollowers: StateFlow<Set<String>> = _myFollowers.asStateFlow()

    private val _myAvatarUrl = MutableStateFlow<String?>(null)

    /** Your own Spotify profile picture (blend song badges), once loaded. */
    val myAvatarUrl: StateFlow<String?> = _myAvatarUrl.asStateFlow()

    /** Friend ids ("SPOTIFY:<id>") where you follow each other. */
    val mutualFriendIds: StateFlow<Set<String>> = combine(_myFollowing, _myFollowers) { following, followers ->
        mutualIds(following, followers)
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** The signed-in user's Spotify id (blank when not connected). */
    val myUserId: String get() = session.username

    fun supports(friendId: String): Boolean = session.hasSession && spotifyUserId(friendId) != null

    /** Spotify is connected, so people can be searched and followed. */
    val canSearch: Boolean get() = session.hasSession

    /** Re-reads who you follow and who follows you (at most every 10 min unless [force]). */
    fun refresh(force: Boolean = false) {
        scope.launch { load(force) }
    }

    private suspend fun load(force: Boolean) = loadLock.withLock {
        if (!session.hasSession) return@withLock
        val me = session.username.ifBlank { return@withLock }
        if (!force && System.currentTimeMillis() - loadedAt < RELOAD_MS) return@withLock
        try {
            if (_myAvatarUrl.value == null) {
                _myAvatarUrl.value = try { social.profile(me).avatarUrl } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            }
            _myFollowing.value = social.myFollowing(force).mapTo(HashSet()) { it.userId }
            _myFollowers.value = social.people(me, followers = true, force = force).mapTo(HashSet()) { it.userId }
            loadedAt = System.currentTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Couldn't load Spotify follow graph")
        }
    }

    /** [friendId]'s follower / following counts, or null when they aren't a Spotify friend. */
    suspend fun stats(friendId: String): FriendSocialStats? = withContext(Dispatchers.IO) {
        val userId = spotifyUserId(friendId) ?: return@withContext null
        if (!session.hasSession) return@withContext null
        val profile = social.profile(userId)
        FriendSocialStats(profile.followers, profile.followingCount)
    }

    /** [friendId]'s followers ([followers] = true) or the people they follow. Throws when it can't load. */
    suspend fun people(friendId: String, followers: Boolean, force: Boolean = false): List<SocialPerson> = withContext(Dispatchers.IO) {
        val userId = requireNotNull(spotifyUserId(friendId)) { "Not a Spotify friend" }
        social.people(userId, followers, force).map { SocialPerson(it.userId, it.name, it.avatarUrl, it.followers, it.followingCount) }
    }

    /** [friendId]'s public profile (name, picture, counts, public playlists). Throws when it can't load. */
    suspend fun profile(friendId: String, force: Boolean = false): SocialProfile = withContext(Dispatchers.IO) {
        val userId = requireNotNull(spotifyUserId(friendId)) { "Not a Spotify account" }
        val p = social.profile(userId, force)
        SocialProfile(p.userId, p.name, p.avatarUrl, FriendSocialStats(p.followers, p.followingCount), p.playlists)
    }

    /**
     * Spotify people search, one page at a time. A profile link ("open.spotify.com/user/…") or
     * URI ("spotify:user:…") goes straight to that person. Throws when Spotify refuses.
     */
    suspend fun searchUsers(query: String, offset: Int, limit: Int = SEARCH_PAGE_SIZE): SocialSearchPage = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isBlank() || !session.hasSession) return@withContext SocialSearchPage(emptyList(), false)
        profileIdIn(term)?.let { id ->
            if (offset > 0) return@withContext SocialSearchPage(emptyList(), false)
            val p = social.profile(id)
            return@withContext SocialSearchPage(listOf(SocialPerson(p.userId, p.name, p.avatarUrl, p.followers, p.followingCount)), false)
        }
        val page = social.searchUsers(term, offset, limit)
        SocialSearchPage(page.people.map { SocialPerson(it.userId, it.name, it.avatarUrl, it.followers, it.followingCount) }, page.hasMore)
    }

    /**
     * People you might add, for an empty search: those who follow you that you don't follow back,
     * then people your mutual friends follow. You and anyone you already follow are left out.
     */
    suspend fun suggestions(limit: Int = 30): List<SocialPerson> = withContext(Dispatchers.IO) {
        if (!session.hasSession) return@withContext emptyList()
        val me = session.username.ifBlank { return@withContext emptyList() }
        load(force = false)
        val following = _myFollowing.value
        val out = LinkedHashMap<String, SocialPerson>()
        fun addAll(list: List<com.theveloper.pixelplay.data.spotify.web.SpotifyPublicProfile>) = list.forEach { p ->
            if (p.userId != me && p.userId !in following && p.userId !in out) {
                out[p.userId] = SocialPerson(p.userId, p.name, p.avatarUrl, p.followers, p.followingCount)
            }
        }
        try { addAll(social.people(me, followers = true)) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        // Friends of friends: a few mutual friends' following lists (cached for 10 min each).
        for (friendId in mutualFriendIds.value.take(SUGGESTION_FRIENDS)) {
            if (out.size >= limit) break
            val id = spotifyUserId(friendId) ?: continue
            try { addAll(social.people(id, followers = false)) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        out.values.take(limit)
    }

    /** Follows or unfollows [person] on Spotify, then updates the badges. */
    suspend fun setFollowing(person: SocialPerson, follow: Boolean) = withContext(Dispatchers.IO) {
        // Optimistic: the button flips straight away and flips back if Spotify refuses.
        val before = _myFollowing.value
        _myFollowing.update { if (follow) it + person.userId else it - person.userId }
        try {
            social.setFollowing(SpotifyPublicProfile(person.userId, person.name, person.avatarUrl, person.followers, emptyList()), follow)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _myFollowing.value = before
            throw e
        }
    }

    companion object {
        private const val RELOAD_MS = 10 * 60_000L
        const val SEARCH_PAGE_SIZE = 20
        private const val SUGGESTION_FRIENDS = 3
        private val PROFILE_LINK = Regex("""(?:open\.spotify\.com/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?user/|spotify:user:)([^/?#\s]+)""", RegexOption.IGNORE_CASE)

        /** The Spotify user id in a profile link or URI, or null when [text] is a plain search. */
        fun profileIdIn(text: String): String? =
            PROFILE_LINK.find(text)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() }

        /** The Spotify user id in a friend id ("SPOTIFY:<id>"), or null for other platforms. */
        fun spotifyUserId(friendId: String): String? =
            friendId.takeIf { it.startsWith("${MusicSources.SPOTIFY}:") }?.substringAfter(':')?.takeIf { it.isNotBlank() }

        /** Friend ids of people in both sets (you follow them and they follow you). */
        fun mutualIds(following: Set<String>, followers: Set<String>): Set<String> =
            following.filterTo(HashSet()) { it in followers }.mapTo(HashSet()) { "${MusicSources.SPOTIFY}:$it" }
    }
}
