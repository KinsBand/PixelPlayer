package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.accounts.ConnectedPlaylist
import com.theveloper.pixelplay.data.spotify.SpotifyAuthManager
import com.theveloper.pixelplay.data.ytmusic.YouTubeMusicAuthManager
import com.theveloper.pixelplay.data.ytmusic.YouTubeMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ExternalServiceAccount { SPOTIFY, YOUTUBE_MUSIC, APPLE_MUSIC }
data class ExternalAccountUiModel(
    val service: ExternalServiceAccount,
    val title: String,
    val accountLabel: String,
    val syncedContentLabel: String,
    /** Your own playlists on this service (friends' are counted in [friendPlaylistCount]). */
    val playlists: List<ConnectedPlaylist> = emptyList(),
    val friendPlaylistCount: Int = 0,
    val isLoggingOut: Boolean = false
)

/** A friend with saved playlists, as listed on the Accounts page. */
data class AccountFriendUi(
    val id: String,
    val name: String,
    /** Source of their playlists ("SPOTIFY", ...), for the platform badge. */
    val source: String,
    val avatarUrl: String?,
    val playlistIds: List<String>,
    /** When their least recently updated playlist was last read (ms); 0 = never. */
    val oldestFetchedAt: Long,
    /** Their saved playlists (same order as [playlistIds]), for the inline dropdown on Accounts. */
    val playlists: List<ConnectedPlaylist> = emptyList()
)

/**
 * Where a service sign-in has got to, for the determinate progress on its Accounts tile.
 * [progress] is the share of the handshake done once this step starts.
 */
enum class ConnectStage(val progress: Float) {
    CHECKING(0.15f),
    PROFILE(0.4f),
    SYNCING(0.65f),
    DONE(1f)
}

/** A friend hidden with "Hide friend" (or removed from the Friends screen). */
data class HiddenFriendUi(val id: String, val name: String, val source: String?, val playlistCount: Int)

data class AccountsUiState(
    val connectedAccounts: List<ExternalAccountUiModel> = emptyList(),
    val disconnectedServices: List<ExternalServiceAccount> = emptyList(),
    val friends: List<AccountFriendUi> = emptyList(),
    val hiddenFriends: List<HiddenFriendUi> = emptyList()
)

/** Keys for the refresh buttons on the Accounts page, so each shows its own spinner. */
object AccountRefreshKeys {
    fun mine(service: ExternalServiceAccount) = "mine:${service.name}"
    fun friends(service: ExternalServiceAccount) = "friends:${service.name}"
    fun all(service: ExternalServiceAccount) = "all:${service.name}"
    fun friend(id: String) = "friend:$id"
    const val ALL_FRIENDS = "friends:*"
}

@HiltViewModel
class AccountsViewModel @Inject constructor(private val spotifyAuthManager: SpotifyAuthManager,
    private val youtubeMusicAuthManager: YouTubeMusicAuthManager, private val youtubeMusicRepository: YouTubeMusicRepository,
    private val spotifyWebSession: com.theveloper.pixelplay.data.spotify.web.SpotifyWebSession,
    private val spotifyWebLibrary: com.theveloper.pixelplay.data.spotify.web.SpotifyWebLibrary,
    private val appleMusicSession: com.theveloper.pixelplay.data.applemusic.AppleMusicWebSession,
    private val friendActivity: com.theveloper.pixelplay.data.social.FriendActivityRepository,
    val library: ConnectedLibraryRepository) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val spotifyStatus = spotifyAuthManager.status
    val syncingPlaylists = library.syncingPlaylists
    /** Refresh buttons currently running ([AccountRefreshKeys]). */
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing = _refreshing.asStateFlow()
    /** Playlists the last refresh couldn't update (0 = all fine); shown under the friends header. */
    val refreshFailures = MutableStateFlow(0)

    /** Sign-ins in progress, per service, for the progress on its tile (absent = idle). */
    private val _connecting = MutableStateFlow<Map<ExternalServiceAccount, ConnectStage>>(emptyMap())
    val connecting = _connecting.asStateFlow()

    private fun stage(service: ExternalServiceAccount, stage: ConnectStage?) {
        _connecting.update { if (stage == null) it - service else it + (service to stage) }
    }

    /** Runs a sign-in with tile progress: [steps] moves the stages; DONE shows briefly, then clears. */
    private fun connectWithProgress(service: ExternalServiceAccount, steps: suspend (advance: (ConnectStage) -> Unit) -> Unit) = run {
        stage(service, ConnectStage.CHECKING)
        var ok = false
        try {
            steps { next -> stage(service, next) }
            ok = true
        } finally {
            if (ok) {
                stage(service, ConnectStage.DONE)
                kotlinx.coroutines.delay(600)
            }
            stage(service, null)
        }
    }

    val uiState = combine(spotifyAuthManager.isLoggedInFlow, youtubeMusicAuthManager.isLoggedInFlow, appleMusicSession.isLoggedInFlow, library.snapshot, friendActivity.profiles) { s, y, a, snapshot, profiles ->
        val connected = mutableListOf<ExternalAccountUiModel>()
        val disconnected = mutableListOf<ExternalServiceAccount>()
        ExternalServiceAccount.entries.forEach { service ->
            val loggedIn = when (service) {
                ExternalServiceAccount.SPOTIFY -> s
                ExternalServiceAccount.YOUTUBE_MUSIC -> y
                ExternalServiceAccount.APPLE_MUSIC -> a
            }
            if (loggedIn) {
                val servicePlaylists = snapshot.playlists.filter { it.source == service.name }
                val own = servicePlaylists.filter { it.friendId == null }
                connected.add(ExternalAccountUiModel(
                    service = service,
                    title = com.theveloper.pixelplay.data.accounts.MusicSources.displayName(service.name),
                    accountLabel = "Connected library",
                    syncedContentLabel = "${servicePlaylists.size} saved playlists",
                    playlists = own,
                    friendPlaylistCount = servicePlaylists.size - own.size,
                    isLoggingOut = false
                ))
            } else disconnected.add(service)
        }
        val hidden = snapshot.hiddenFriends.orEmpty().toSet()
        val friends = snapshot.playlists
            .filter { it.friendId != null && it.friendId !in hidden }
            .groupBy { it.friendId!! }
            .map { (id, playlists) ->
                AccountFriendUi(
                    id = id,
                    name = snapshot.aliases[id]?.takeIf { it.isNotBlank() }
                        ?: profiles[id]?.displayName?.takeIf { it.isNotBlank() }
                        ?: playlists.firstOrNull { it.ownerName.isNotBlank() }?.ownerName.orEmpty(),
                    source = playlists.first().source,
                    avatarUrl = profiles[id]?.avatarUrl,
                    playlistIds = playlists.map { it.id },
                    oldestFetchedAt = playlists.minOf { it.fetchedAt },
                    playlists = playlists
                )
            }
            .sortedBy { it.name.lowercase() }
        val tombstones = snapshot.deletedPlaylists.orEmpty().values
        val hiddenFriends = snapshot.hiddenFriends.orEmpty().map { id ->
            val theirs = tombstones.filter { it.friendId == id }
            HiddenFriendUi(id, snapshot.hiddenFriendNames?.get(id).orEmpty(), theirs.firstOrNull()?.source, theirs.size)
        }
        AccountsUiState(connected, disconnected, friends, hiddenFriends)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsUiState())
    private fun run(action: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true; error.value = null
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "Connection failed. Please try again." }
        finally { busy.value = false }
    }
    fun logout(service: ExternalServiceAccount) = run {
        when (service) {
            ExternalServiceAccount.SPOTIFY -> {
                spotifyAuthManager.logout()
                com.theveloper.pixelplay.presentation.screens.WebLoginTarget.forgetCookie(com.theveloper.pixelplay.presentation.screens.WebLoginTarget.Spotify)
            }
            ExternalServiceAccount.YOUTUBE_MUSIC -> {
                youtubeMusicAuthManager.logout()
                com.theveloper.pixelplay.presentation.screens.WebLoginTarget.forgetCookie(com.theveloper.pixelplay.presentation.screens.WebLoginTarget.YouTubeMusic)
            }
            ExternalServiceAccount.APPLE_MUSIC -> {
                appleMusicSession.logout()
                com.theveloper.pixelplay.presentation.screens.WebLoginTarget.forgetCookie(com.theveloper.pixelplay.presentation.screens.WebLoginTarget.AppleMusic)
            }
        }
    }
    fun connectYouTube(cookie: String, account: String = "0", onSuccess: () -> Unit) =
        connectWithProgress(ExternalServiceAccount.YOUTUBE_MUSIC) { advance ->
            youtubeMusicAuthManager.connect(cookie, account, youtubeMusicRepository::validateSession)
            onSuccess()
            advance(ConnectStage.SYNCING)
            library.sync()
        }
    fun connectSpotify(clientId: String, open: (String) -> Unit) = run { open(spotifyAuthManager.getAuthorizationUrl(clientId)) }

    /** Sign-in with Spotify (web-player session): checks the cookie, reads the profile, then syncs. */
    fun connectSpotifyWeb(cookie: String, onSuccess: () -> Unit) = connectWithProgress(ExternalServiceAccount.SPOTIFY) { advance ->
        try {
            spotifyWebSession.connect(cookie)
            advance(ConnectStage.PROFILE)
            // A signed-in (non-anonymous) token already proves the cookie works, which is all
            // Spotube checks. The profile is only for display, so a changed query can't block sign-in.
            runCatching { spotifyWebLibrary.me() }
        } catch (e: Exception) {
            spotifyWebSession.logout()
            com.theveloper.pixelplay.presentation.screens.WebLoginTarget.forgetCookie(com.theveloper.pixelplay.presentation.screens.WebLoginTarget.Spotify)
            throw e
        }
        onSuccess()
        advance(ConnectStage.SYNCING)
        library.sync()
    }
    /** Sign-in with Apple Music (web-player session): checks the token, then syncs. */
    fun connectAppleMusic(token: String, onSuccess: () -> Unit) =
        connectWithProgress(ExternalServiceAccount.APPLE_MUSIC) { advance ->
            appleMusicSession.connect(token)
            onSuccess()
            advance(ConnectStage.SYNCING)
            library.sync()
        }
    fun sync() = run { library.sync() }
    fun syncPlaylist(playlistId: String) = run { library.syncPlaylist(playlistId) }

    /** Runs one refresh button's work; tapping a button that's already running does nothing. */
    private fun refresh(key: String, action: suspend () -> Int) {
        if (key in _refreshing.value) return
        _refreshing.update { it + key }
        viewModelScope.launch {
            try { refreshFailures.value = action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message }
            finally { _refreshing.update { it - key } }
        }
    }

    private fun friendPlaylistIds(source: String?): List<String> =
        library.snapshot.value.playlists
            .filter { it.friendId != null && (source == null || it.source == source) && it.friendId !in library.snapshot.value.hiddenFriends.orEmpty() }
            .sortedBy { it.fetchedAt }
            .map { it.id }

    /** Re-lists your own playlists and likes on [service] only. */
    fun refreshMine(service: ExternalServiceAccount) = refresh(AccountRefreshKeys.mine(service)) { library.sync(service.name); 0 }

    /** Updates every friend's playlist saved from [service]. */
    fun refreshFriends(service: ExternalServiceAccount) = refresh(AccountRefreshKeys.friends(service)) {
        library.refreshPlaylists(friendPlaylistIds(service.name))
    }

    /** Your playlists on [service], then your friends' playlists from it. */
    fun refreshService(service: ExternalServiceAccount) = refresh(AccountRefreshKeys.all(service)) {
        library.sync(service.name)
        library.refreshPlaylists(friendPlaylistIds(service.name))
    }

    /** Updates every friend's playlists, on all platforms. */
    fun refreshAllFriends() = refresh(AccountRefreshKeys.ALL_FRIENDS) { library.refreshPlaylists(friendPlaylistIds(null)) }

    /** Updates one friend's playlists. */
    fun refreshFriend(friend: AccountFriendUi) = refresh(AccountRefreshKeys.friend(friend.id)) {
        library.refreshPlaylists(friend.playlistIds)
    }

    fun hideFriend(id: String) = viewModelScope.launch { library.removeFriends(listOf(id)) }
    fun restoreFriend(id: String) = refresh(AccountRefreshKeys.friend(id)) { library.restoreFriend(id); 0 }
    suspend fun getSpotifyAuthUrl(clientId: String) = spotifyAuthManager.getAuthorizationUrl(clientId)
}
