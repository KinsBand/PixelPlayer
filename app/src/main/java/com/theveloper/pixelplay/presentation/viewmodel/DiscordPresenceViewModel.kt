package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.presence.DiscordPresenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings → Accounts & Services → Discord status. */
@HiltViewModel
class DiscordPresenceViewModel @Inject constructor(
    private val manager: DiscordPresenceManager,
) : ViewModel() {
    val status: StateFlow<DiscordPresenceManager.Status> = manager.status
    val enabled: StateFlow<Boolean> = manager.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val redirectUris: List<String> get() = DiscordPresenceManager.REGISTERED_REDIRECT_URIS

    /** Builds the sign-in URL for [clientId]; [open] receives it (or an error message via status). */
    fun connect(clientId: String, open: (String) -> Unit) {
        viewModelScope.launch {
            try {
                open(manager.getAuthorizationUrl(clientId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Couldn't start Discord sign-in."
            }
        }
    }

    val error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun setEnabled(on: Boolean) = manager.setEnabled(on)

    fun disconnect() {
        viewModelScope.launch { manager.disconnect() }
    }
}
