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
    val playlists: List<ConnectedPlaylist> = emptyList(),
    val isLoggingOut: Boolean = false
)
data class AccountsUiState(val connectedAccounts: List<ExternalAccountUiModel> = emptyList(), val disconnectedServices: List<ExternalServiceAccount> = emptyList())

@HiltViewModel
class AccountsViewModel @Inject constructor(private val spotifyAuthManager: SpotifyAuthManager,
    private val youtubeMusicAuthManager: YouTubeMusicAuthManager, private val youtubeMusicRepository: YouTubeMusicRepository,
    private val spotifyWebSession: com.theveloper.pixelplay.data.spotify.web.SpotifyWebSession,
    private val spotifyWebLibrary: com.theveloper.pixelplay.data.spotify.web.SpotifyWebLibrary,
    private val appleMusicSession: com.theveloper.pixelplay.data.applemusic.AppleMusicWebSession,
    val library: ConnectedLibraryRepository) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val spotifyStatus = spotifyAuthManager.status
    val syncingPlaylists = library.syncingPlaylists
    val uiState = combine(spotifyAuthManager.isLoggedInFlow, youtubeMusicAuthManager.isLoggedInFlow, appleMusicSession.isLoggedInFlow, library.snapshot) { s, y, a, snapshot ->
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
                connected.add(ExternalAccountUiModel(
                    service = service,
                    title = com.theveloper.pixelplay.data.accounts.MusicSources.displayName(service.name),
                    accountLabel = "Connected library",
                    syncedContentLabel = "${servicePlaylists.size} saved playlists",
                    playlists = servicePlaylists,
                    isLoggingOut = false
                ))
            } else disconnected.add(service)
        }
        AccountsUiState(connected, disconnected)
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
            ExternalServiceAccount.YOUTUBE_MUSIC -> youtubeMusicAuthManager.logout()
            ExternalServiceAccount.APPLE_MUSIC -> {
                appleMusicSession.logout()
                com.theveloper.pixelplay.presentation.screens.WebLoginTarget.forgetCookie(com.theveloper.pixelplay.presentation.screens.WebLoginTarget.AppleMusic)
            }
        }
    }
    fun connectYouTube(cookie: String, account: String, onSuccess: () -> Unit) = run {
        youtubeMusicAuthManager.connect(cookie, account, youtubeMusicRepository::validateSession)
        onSuccess()
        library.sync()
    }
    fun connectSpotify(clientId: String, open: (String) -> Unit) = run { open(spotifyAuthManager.getAuthorizationUrl(clientId)) }

    /** Sign-in with Spotify (web-player session): checks the cookie, reads the profile, then syncs. */
    fun connectSpotifyWeb(cookie: String, onSuccess: () -> Unit) = run {
        try {
            spotifyWebSession.connect(cookie)
            spotifyWebLibrary.me()
        } catch (e: Exception) {
            spotifyWebSession.logout()
            throw e
        } finally {
            com.theveloper.pixelplay.presentation.screens.WebLoginTarget.forgetCookie(com.theveloper.pixelplay.presentation.screens.WebLoginTarget.Spotify)
        }
        onSuccess()
        library.sync()
    }
    /** Sign-in with Apple Music (web-player session): checks the token, then syncs. */
    fun connectAppleMusic(token: String, onSuccess: () -> Unit) = run {
        try {
            appleMusicSession.connect(token)
        } finally {
            com.theveloper.pixelplay.presentation.screens.WebLoginTarget.forgetCookie(com.theveloper.pixelplay.presentation.screens.WebLoginTarget.AppleMusic)
        }
        onSuccess()
        library.sync()
    }
    fun sync() = run { library.sync() }
    fun syncPlaylist(playlistId: String) = run { library.syncPlaylist(playlistId) }
    suspend fun getSpotifyAuthUrl(clientId: String) = spotifyAuthManager.getAuthorizationUrl(clientId)
}
