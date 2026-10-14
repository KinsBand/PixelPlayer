package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.account.CloudAccountRepository
import com.theveloper.pixelplay.data.account.CloudAccountState
import com.theveloper.pixelplay.data.account.RestoreSummary
import com.theveloper.pixelplay.data.ytmusic.YouTubeMusicAuthManager
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.ytmusic.YouTubeMusicRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Long-running account actions; the matching button shows progress while one runs. */
enum class ProfileOperation { SignIn, SignUp, ResetPassword, Save, Restore, Delete, SignOut, Refresh }

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val account: CloudAccountRepository,
    private val youTubeMusic: YouTubeMusicRepository,
    private val youTubeAuth: YouTubeMusicAuthManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val state: StateFlow<CloudAccountState> = account.state

    val customAvatarUri: StateFlow<String?> =
        userPreferencesRepository.customAvatarUriFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCustomAvatarUri(uri: String?) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomAvatarUri(uri)
        }
    }

    /**
     * The Google profile picture, when the account email is a Gmail address and the Google
     * account is connected (YouTube Music). Null otherwise, so Gravatar / the initial show.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val googleAvatarUrl: StateFlow<String?> =
        combine(account.state, youTubeAuth.isLoggedInFlow) { s, connected ->
            if (connected && s.email?.let(::isGoogleMail) == true) s.email else null
        }
            .distinctUntilChanged()
            .mapLatest { email -> if (email == null) null else runCatching { youTubeMusic.accountPhotoUrl() }.getOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _operation = MutableStateFlow<ProfileOperation?>(null)
    val operation: StateFlow<ProfileOperation?> = _operation.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Set after a restore finishes; the screen offers a restart. */
    private val _restoreResult = MutableStateFlow<RestoreSummary?>(null)
    val restoreResult: StateFlow<RestoreSummary?> = _restoreResult.asStateFlow()

    /** After signing in on a device that has never saved: offer the account's stored copy. */
    private val _offerRestore = MutableStateFlow(false)
    val offerRestore: StateFlow<Boolean> = _offerRestore.asStateFlow()

    /** Called when the Profile screen opens: fetches what's stored in the account. */
    fun refreshCloudInfo() {
        if (account.state.value.isSignedIn) run(ProfileOperation.Refresh, quiet = true) { account.refreshCloudInfo() }
    }

    fun signIn(email: String, password: String) = run(ProfileOperation.SignIn) {
        account.signIn(email, password)
        _messages.emit("Signed in as ${account.state.value.email}")
        offerRestoreIfUseful()
    }

    fun signUp(email: String, password: String) = run(ProfileOperation.SignUp) {
        when (account.signUp(email, password)) {
            CloudAccountRepository.SignUpResult.SignedIn -> {
                _messages.emit("Account created. Saving your library…")
                runCatching { account.saveNow() }
                    .onSuccess { _messages.emit("Your library is saved to your account") }
                    .onFailure { _messages.emit(it.message ?: "Couldn't save yet") }
            }
            CloudAccountRepository.SignUpResult.ConfirmEmail ->
                _messages.emit("Check your inbox to confirm your email, then sign in")
        }
    }

    fun sendPasswordReset(email: String) = run(ProfileOperation.ResetPassword) {
        account.sendPasswordReset(email)
        _messages.emit("If that email has an account, a reset link is on its way")
    }

    fun saveNow() = run(ProfileOperation.Save) {
        val summary = account.saveNow()
        _messages.emit(
            buildString {
                append("Saved")
                if (summary.onlineSongs > 0) append(" · ${summary.onlineSongs} online songs")
                if (summary.includesSessions) append(" · service sign-ins (encrypted)")
            }
        )
    }

    fun restore() = run(ProfileOperation.Restore) {
        _offerRestore.value = false
        val result = account.restore()
        if (result == null) _messages.emit("Nothing saved in this account yet") else _restoreResult.value = result
    }

    fun deleteCloudCopy() = run(ProfileOperation.Delete) {
        account.deleteCloudCopy()
        _messages.emit("Saved copy deleted from your account")
    }

    fun signOut() = run(ProfileOperation.SignOut) {
        account.signOut()
        _offerRestore.value = false
        _messages.emit("Signed out. Your music stays on this phone")
    }

    fun setAutoSave(enabled: Boolean) = account.setAutoSave(enabled)

    fun setServer(url: String, anonKey: String) {
        account.setServer(url, anonKey)
        viewModelScope.launch { _messages.emit("Server saved") }
    }

    fun dismissRestoreOffer() { _offerRestore.value = false }
    fun dismissRestoreResult() { _restoreResult.value = null }

    private fun isGoogleMail(email: String): Boolean =
        email.trim().lowercase().substringAfterLast('@').let { it == "gmail.com" || it == "googlemail.com" }

    private suspend fun offerRestoreIfUseful() {
        val now = account.state.value
        if (now.lastSavedAt == null && now.cloudUpdatedAt != null) _offerRestore.value = true
    }

    private fun run(op: ProfileOperation, quiet: Boolean = false, block: suspend () -> Unit) {
        if (_operation.value != null && !quiet) return
        viewModelScope.launch {
            if (!quiet) _operation.value = op
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val message = when (e) {
                    is com.theveloper.pixelplay.data.account.SupabaseException -> e.message
                    is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
                        "Can't reach the account server. Check your connection"
                    else -> e.message
                } ?: "Something went wrong"
                if (!quiet) _messages.emit(message)
            } finally {
                if (!quiet) _operation.value = null
            }
        }
    }
}
