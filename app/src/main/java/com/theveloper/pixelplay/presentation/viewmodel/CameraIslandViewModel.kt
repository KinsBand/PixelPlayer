package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Camera hole island switch in Settings > Appearance. */
@HiltViewModel
class CameraIslandViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val enableCutoutOverlayFlow = userPreferencesRepository.enableCutoutOverlayFlow

    fun setEnableCutoutOverlay(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setEnableCutoutOverlay(enabled)
        }
    }
}
