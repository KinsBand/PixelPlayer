package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.preferences.WidgetAccentSource
import com.theveloper.pixelplay.data.preferences.WidgetBackgroundStyle
import com.theveloper.pixelplay.data.preferences.WidgetConfig
import com.theveloper.pixelplay.data.preferences.WidgetKind
import com.theveloper.pixelplay.data.preferences.WidgetPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.preferences.WidgetProgressStyle
import com.theveloper.pixelplay.data.preferences.WidgetSpinMode
import com.theveloper.pixelplay.ui.glancewidget.PlayerInfoStateDefinition
import com.theveloper.pixelplay.ui.glancewidget.TurntableRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Backs the Visual Widgets settings screen.
 *
 * Deliberately separate from `SettingsViewModel`: that one already carries a 58-field state
 * object shared by every category, and widget customisation is self-contained — it reads one
 * repository and one DataStore and is only ever observed by one screen.
 */
@HiltViewModel
class VisualWidgetsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val widgetPreferences: WidgetPreferencesRepository,
    val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val enableCutoutOverlayFlow = userPreferencesRepository.enableCutoutOverlayFlow

    fun setEnableCutoutOverlay(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setEnableCutoutOverlay(enabled)
        }
    }

    private val _selectedKind = MutableStateFlow(WidgetKind.TURNTABLE)

    /** Which widget the appearance and content controls currently apply to. */
    val selectedKind: StateFlow<WidgetKind> = _selectedKind.asStateFlow()

    val configs: StateFlow<Map<WidgetKind, WidgetConfig>> =
        widgetPreferences.allConfigsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WidgetKind.entries.associateWith { WidgetConfig(kind = it) },
        )

    /**
     * The same [PlayerInfo] the placed widgets are rendering from, read straight off the
     * Glance state store. Using it rather than the player's own state is what makes the
     * previews honest: if the widget has stale artwork, the preview shows stale artwork.
     */
    val playerInfo: StateFlow<PlayerInfo> = flow {
        emitAll(PlayerInfoStateDefinition.getDataStore(context, PREVIEW_STATE_KEY).data)
    }.catch { error ->
        Timber.tag(TAG).w(error, "Could not read widget state for preview")
        emit(PlayerInfo())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerInfo(),
    )

    fun selectKind(kind: WidgetKind) {
        _selectedKind.value = kind
    }

    // -------------------------------------------------------------------------------
    // Appearance
    // -------------------------------------------------------------------------------

    fun setBackgroundStyle(kind: WidgetKind, style: WidgetBackgroundStyle) =
        persist { widgetPreferences.setBackgroundStyle(kind, style) }

    fun setCornerRadius(kind: WidgetKind, radiusDp: Int) =
        persist { widgetPreferences.setCornerRadius(kind, radiusDp) }

    fun setAccentSource(kind: WidgetKind, source: WidgetAccentSource) =
        persist { widgetPreferences.setAccentSource(kind, source) }

    fun setFixedAccentColor(kind: WidgetKind, color: Int) =
        persist { widgetPreferences.setFixedAccentColor(kind, color) }

    // -------------------------------------------------------------------------------
    // Controls and content
    // -------------------------------------------------------------------------------

    fun setShowTitle(kind: WidgetKind, show: Boolean) =
        persist { widgetPreferences.setShowTitle(kind, show) }

    fun setShowArtist(kind: WidgetKind, show: Boolean) =
        persist { widgetPreferences.setShowArtist(kind, show) }

    fun setShowPrevNext(kind: WidgetKind, show: Boolean) =
        persist { widgetPreferences.setShowPrevNext(kind, show) }

    fun setShowShuffle(kind: WidgetKind, show: Boolean) =
        persist { widgetPreferences.setShowShuffle(kind, show) }

    fun setShowRepeat(kind: WidgetKind, show: Boolean) =
        persist { widgetPreferences.setShowRepeat(kind, show) }

    fun setShowFavorite(kind: WidgetKind, show: Boolean) =
        persist { widgetPreferences.setShowFavorite(kind, show) }

    fun setProgressStyle(kind: WidgetKind, style: WidgetProgressStyle) =
        persist { widgetPreferences.setProgressStyle(kind, style) }

    // -------------------------------------------------------------------------------
    // Turntable
    // -------------------------------------------------------------------------------

    fun setDiscScale(scale: Float) = persist { widgetPreferences.setDiscScale(scale) }

    fun setLabelScale(scale: Float) = persist { widgetPreferences.setLabelScale(scale) }

    fun setSpinMode(mode: WidgetSpinMode) = persist { widgetPreferences.setSpinMode(mode) }

    fun setSpinRpm(rpm: Float) = persist { widgetPreferences.setSpinRpm(rpm) }

    fun setClockwise(clockwise: Boolean) = persist { widgetPreferences.setClockwise(clockwise) }

    fun setShowBadges(show: Boolean) = persist { widgetPreferences.setShowBadges(show) }

    fun setShowTonearm(show: Boolean) = persist { widgetPreferences.setShowTonearm(show) }

    fun setShowGrooves(show: Boolean) = persist { widgetPreferences.setShowGrooves(show) }

    fun setShowSheen(show: Boolean) = persist { widgetPreferences.setShowSheen(show) }

    fun resetKind(kind: WidgetKind) = persist { widgetPreferences.resetKind(kind) }

    /**
     * Persists a change, then makes it visible on the home screen straight away.
     *
     * Widgets do not observe preferences themselves — they read the configuration once per
     * update — so a styling change is invisible until something pushes an update. Dropping
     * the render cache first is what stops the old disc being re-served from it.
     */
    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { Timber.tag(TAG).e(it, "Failed to save widget preference") }
            TurntableRenderer.clearCache()
            runCatching {
                context.sendBroadcast(
                    Intent(ACTION_WIDGET_UPDATE).setPackage(context.packageName),
                )
            }.onFailure { Timber.tag(TAG).w(it, "Could not broadcast widget refresh") }
        }
    }

    private companion object {
        private const val TAG = "VisualWidgetsVM"

        /** [PlayerInfoStateDefinition] ignores the file key; it keeps one shared store. */
        private const val PREVIEW_STATE_KEY = "widget_preview"

        private const val ACTION_WIDGET_UPDATE =
            "com.theveloper.pixelplay.ACTION_WIDGET_UPDATE_PLAYBACK_STATE"
    }
}
