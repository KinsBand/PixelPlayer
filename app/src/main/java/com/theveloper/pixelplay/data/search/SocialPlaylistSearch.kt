package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.accounts.ConnectedPlaylist
import com.theveloper.pixelplay.data.accounts.ConnectedSnapshot
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.social.FriendProfile
import java.text.Normalizer
import java.util.Locale

/**
 * Search over playlists that live outside the local database: your Spotify / YouTube Music /
 * Apple Music playlists, the playlists you saved from friends, friends' public playlists, and
 * the friends themselves (matched by name).
 *
 * Pure function over snapshots so it runs instantly while typing (no network).
 */
object SocialPlaylistSearch {

    /** Prefix for a friend's public playlist that isn't saved in PixelPlayer yet. */
    const val FRIEND_PUBLIC_PREFIX = "friendpublic:"

    fun friendPublicId(source: String, remoteId: String) = "$FRIEND_PUBLIC_PREFIX$source:$remoteId"

    /** Returns (source, remoteId) for an id made by [friendPublicId], else null. */
    fun parseFriendPublicId(id: String): Pair<String, String>? {
        if (!id.startsWith(FRIEND_PUBLIC_PREFIX)) return null
        val rest = id.removePrefix(FRIEND_PUBLIC_PREFIX)
        val source = rest.substringBefore(':', "")
        val remoteId = rest.substringAfter(':', "")
        return if (source.isBlank() || remoteId.isBlank()) null else source to remoteId
    }

    /** Lowercase, accents removed, whitespace collapsed. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .trim()

    /** Every word of [query] appears somewhere in [text]. */
    fun matches(text: String, normalizedQueryTerms: List<String>): Boolean {
        if (normalizedQueryTerms.isEmpty()) return false
        val haystack = normalize(text)
        return normalizedQueryTerms.all { haystack.contains(it) }
    }

    fun search(
        query: String,
        snapshot: ConnectedSnapshot,
        profiles: Map<String, FriendProfile>,
    ): List<SearchResultItem> {
        val terms = normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()

        // Your own playlists on the connected services.
        val own = snapshot.playlists
            .filter { it.friendId == null && matches(it.title, terms) }
            .map { SearchResultItem.PlaylistItem(it.toPlaylist(ownerName = it.ownerName)) }

        // Friends: everyone with a saved playlist or a live-activity profile.
        val saved = snapshot.playlists.filter { it.friendId != null }.groupBy { it.friendId!! }
        val friendIds = (saved.keys + profiles.keys).distinct()
        val friendItems = mutableListOf<SearchResultItem.FriendItem>()
        val friendPlaylists = mutableListOf<SearchResultItem.PlaylistItem>()
        for (id in friendIds) {
            val profile = profiles[id]
            val mine = saved[id].orEmpty()
            val name = snapshot.aliases[id]
                ?: profile?.displayName?.takeIf { it.isNotBlank() }
                ?: mine.firstOrNull()?.ownerName?.takeIf { it.isNotBlank() }
                ?: "Friend"
            val savedPlaylists = mine.map { it.toPlaylist(ownerName = name) }
            val savedKeys = mine.mapTo(HashSet()) { it.source + ":" + it.remoteId }
            val publicPlaylists = profile?.publicPlaylists.orEmpty()
                .filter { (it.source + ":" + it.remoteId) !in savedKeys && it.remoteId.isNotBlank() }
                .map {
                    Playlist(
                        id = friendPublicId(it.source, it.remoteId),
                        name = it.title,
                        songIds = emptyList(),
                        coverImageUri = it.coverUrl,
                        source = it.source,
                        ownerName = name,
                        friendId = id
                    )
                }
            val all = savedPlaylists + publicPlaylists
            val nameMatches = matches(name, terms) ||
                (profile?.displayName?.let { matches(it, terms) } == true)
            if (nameMatches) {
                friendItems += SearchResultItem.FriendItem(
                    friendId = id,
                    name = name,
                    avatarUrl = profile?.avatarUrl,
                    source = id.substringBefore(':', "").ifBlank { mine.firstOrNull()?.source.orEmpty() },
                    playlists = all
                )
            }
            // A friend's playlist shows when its title matches, or when the friend's name does.
            all.filter { nameMatches || matches(it.name, terms) }
                .forEach { friendPlaylists += SearchResultItem.PlaylistItem(it) }
        }
        return own + friendPlaylists + friendItems
    }

    private fun ConnectedPlaylist.toPlaylist(ownerName: String) = Playlist(
        id = id,
        name = title,
        songIds = songs.map { it.id },
        coverImageUri = coverUrl ?: songs.firstOrNull()?.albumArtUriString,
        source = source,
        ownerName = ownerName,
        friendId = friendId
    )

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
}
