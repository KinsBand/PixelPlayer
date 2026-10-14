package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.presence.DiscordPresenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings → Accounts & Services → Discord status (Discord Social SDK; no sign-in). */
@HiltViewModel
class DiscordPresenceViewModel @Inject constructor(
    private val manager: DiscordPresenceManager,
) : ViewModel() {
    val status: StateFlow<DiscordPresenceManager.Status> = manager.status
    val readiness: StateFlow<DiscordPresenceManager.Readiness> = manager.readinessFlow
    val enabled: StateFlow<Boolean> = manager.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** The Application ID typed in the app ("" when none). */
    val applicationId: StateFlow<String> =
        manager.applicationIdInput.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val error = MutableStateFlow<String?>(null)

    fun refreshReadiness() = manager.refreshReadiness()

    /** Saves the typed Application ID; returns false (and sets [error]) when it isn't valid. */
    fun saveApplicationId(input: String): Boolean {
        val ok = manager.setApplicationId(input)
        error.value = if (ok) null else "That isn't a Discord Application ID (a 17–20 digit number)."
        return ok
    }

    fun setEnabled(on: Boolean) = manager.setEnabled(on)

    /** Turns the status off and forgets the Application ID on this phone. */
    fun disconnect() {
        viewModelScope.launch { manager.disconnect() }
    }
}
