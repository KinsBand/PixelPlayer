package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.backup.BackupManager
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.model.BackupTransferProgressUpdate
import com.theveloper.pixelplay.data.backup.model.BackupHistoryEntry
import com.theveloper.pixelplay.data.backup.model.RestorePlan
import com.theveloper.pixelplay.data.backup.model.ValidationError
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.database.AiUsageDao
import com.theveloper.pixelplay.data.database.AiUsageEntity
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.preferences.AlbumArtColorAccuracy
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.AppLanguage
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.worker.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.service.player.HiFiCapabilityChecker
import com.theveloper.pixelplay.utils.AppLocaleManager
import java.io.File

data class SettingsUiState(
    val isLoadingDirectories: Boolean = false,
    val appLanguageTag: String = AppLanguage.SYSTEM.tag,
    val appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM,
    val playerThemePreference: String = ThemePreference.ALBUM_ART,
    val albumArtPaletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default,
    val albumArtColorAccuracy: Int = AlbumArtColorAccuracy.DEFAULT,
    val mockGenresEnabled: Boolean = false,
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val navBarCompactMode: Boolean = false,
    val carouselStyle: String = CarouselStyle.NO_PEEK,
    val libraryNavigationMode: String = LibraryNavigationMode.TAB_ROW,
    val launchTab: String = LaunchTab.HOME,
    val keepPlayingInBackground: Boolean = true,
    val disableCastAutoplay: Boolean = false,
    val pauseOnVolumeZero: Boolean = false,
    val resumeOnHeadsetReconnect: Boolean = false,
    val showQueueHistory: Boolean = true,
    val isCrossfadeEnabled: Boolean = false,
    val hiFiModeEnabled: Boolean = false,
    val hiFiModeDeviceSupported: Boolean = true,
    val crossfadeDuration: Int = 2000,
    val persistentShuffleEnabled: Boolean = false,
    val folderBackGestureNavigation: Boolean = true,
    val lyricsSourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
    val autoScanLrcFiles: Boolean = false,
    val blockedDirectories: Set<String> = emptySet(),
    val appRebrandDialogShown: Boolean = false,
    val beta05CleanInstallDisclaimerDismissed: Boolean? = null,
    val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
    val showPlayerFileInfo: Boolean = true,
    val offlineMode: Boolean = false,
    val lyricsIntegrationEnabled: Boolean = true,
    val ambientSuggestionsEnabled: Boolean = false,
    val ambientDuckingFactor: Float = 0.15f,
    val ambientAudioChimeEnabled: Boolean = true,
    // Developer Options
    val albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM,
    val albumArtCacheLimitMb: Int = 200,
    val tapBackgroundClosesPlayer: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val immersiveLyricsEnabled: Boolean = false,
    val immersiveLyricsTimeout: Long = 4000L,
    val useAnimatedLyrics: Boolean = false,
    val animatedLyricsBlurEnabled: Boolean = true,
    val animatedLyricsBlurStrength: Float = 2.5f,
    val disableBlurAllOver: Boolean = false,
    val backupInfoDismissed: Boolean = false,
    val isDataTransferInProgress: Boolean = false,
    val restorePlan: RestorePlan? = null,
    val backupHistory: List<BackupHistoryEntry> = emptyList(),
    val backupValidationErrors: List<ValidationError> = emptyList(),
    val isInspectingBackup: Boolean = false,
    val collagePattern: CollagePattern = CollagePattern.default,
    val collageAutoRotate: Boolean = false,
    val minSongDuration: Int = 10000,
    val minTracksPerAlbum: Int = 1,
    val replayGainEnabled: Boolean = false,
    val replayGainUseAlbumGain: Boolean = false,
    val isSafeTokenLimitEnabled: Boolean = true,
    val showScrollbar: Boolean = true
)

// Helper classes for consolidated combine() collectors to reduce coroutine overhead
private sealed interface SettingsUiUpdate {
    data class Group1(
        val appRebrandDialogShown: Boolean,
        val appThemeMode: String,
        val playerThemePreference: String,
        val albumArtPaletteStyle: AlbumArtPaletteStyle,
        val albumArtColorAccuracy: Int,
        val mockGenresEnabled: Boolean,
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val navBarCompactMode: Boolean,
        val libraryNavigationMode: String,
        val carouselStyle: String,
        val launchTab: String,
        val showPlayerFileInfo: Boolean
    ) : SettingsUiUpdate

    data class Group2(
        val keepPlayingInBackground: Boolean,
        val disableCastAutoplay: Boolean,
        val pauseOnVolumeZero: Boolean,
        val resumeOnHeadsetReconnect: Boolean,
        val showQueueHistory: Boolean,
        val isCrossfadeEnabled: Boolean,
        val hiFiModeEnabled: Boolean,
        val crossfadeDuration: Int,
        val persistentShuffleEnabled: Boolean,
        val folderBackGestureNavigation: Boolean,
        val lyricsSourcePreference: LyricsSourcePreference,
        val autoScanLrcFiles: Boolean,
        val blockedDirectories: Set<String>,
        val hapticsEnabled: Boolean,
        val immersiveLyricsEnabled: Boolean,
        val immersiveLyricsTimeout: Long,
        val animatedLyricsBlurEnabled: Boolean,
        val animatedLyricsBlurStrength: Float,
        val disableBlurAllOver: Boolean,
        val showScrollbar: Boolean
    ) : SettingsUiUpdate

    data class Group3(
        val collagePattern: CollagePattern,
        val collageAutoRotate: Boolean,
        val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
        val useAnimatedLyrics: Boolean,
        val backupInfoDismissed: Boolean,
        val beta05CleanInstallDisclaimerDismissed: Boolean,
        val isLoadingDirectories: Boolean,
        val albumArtQuality: AlbumArtQuality,
        val albumArtCacheLimitMb: Int,
        val tapBackgroundClosesPlayer: Boolean,
        val minSongDuration: Int,
        val minTracksPerAlbum: Int,
        val replayGainEnabled: Boolean,
        val replayGainUseAlbumGain: Boolean,
        val isSafeTokenLimitEnabled: Boolean,
        val offlineMode: Boolean,
        val lyricsIntegrationEnabled: Boolean
    ) : SettingsUiUpdate
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val syncManager: SyncManager,
    private val aiUsageDao: AiUsageDao,
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository,
    private val backupManager: BackupManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ─── Core UI state ────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // ─── Library & Sync ───────────────────────────────────────────────────────

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val syncProgress: StateFlow<SyncProgress> = syncManager.syncProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncProgress()
        )

    fun refreshLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.forceRefresh()
        }
    }

    /**
     * Performs a full library rescan - rescans all files from scratch.
     * Use when songs are missing or metadata is incorrect.
     */
    fun fullSyncLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.fullSync()
        }
    }

    /**
     * Completely rebuilds the database from scratch.
     * Clears all data including user edits (lyrics, favorites) and rescans.
     * Use when database is corrupted or as a last resort.
     */
    fun rebuildDatabase() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.rebuildDatabase()
        }
    }

    fun setMinSongDuration(durationMs: Int) {
        viewModelScope.launch {
            if (durationMs == _uiState.value.minSongDuration) return@launch
            userPreferencesRepository.setMinSongDuration(durationMs)
            // Trigger a library rescan so the change takes effect in the database
            syncManager.fullSync()
        }
    }

    fun setMinTracksPerAlbum(minTracks: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setMinTracksPerAlbum(minTracks)
        }
    }

    // ─── File Explorer & Directory Rules ──────────────────────────────────────

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val blockedDirectories = fileExplorerStateHolder.blockedDirectories
    val availableStorages = fileExplorerStateHolder.availableStorages
    val selectedStorageIndex = fileExplorerStateHolder.selectedStorageIndex
    val isLoadingDirectories = fileExplorerStateHolder.isLoading
    val isExplorerPriming = fileExplorerStateHolder.isPrimingExplorer
    val isExplorerReady = fileExplorerStateHolder.isExplorerReady
    val isCurrentDirectoryResolved = fileExplorerStateHolder.isCurrentDirectoryResolved
    private var hasPendingDirectoryRuleChanges = false
    private var latestDirectoryRuleUpdateJob: Job? = null

    fun toggleDirectoryAllowed(file: File) {
        hasPendingDirectoryRuleChanges = true
        latestDirectoryRuleUpdateJob = viewModelScope.launch {
            fileExplorerStateHolder.toggleDirectoryAllowed(file)
        }
    }

    fun applyPendingDirectoryRuleChanges() {
        if (!hasPendingDirectoryRuleChanges) return
        hasPendingDirectoryRuleChanges = false
        viewModelScope.launch {
            latestDirectoryRuleUpdateJob?.join()
            syncManager.forceRefresh()
        }
    }

    fun loadDirectory(file: File) {
        fileExplorerStateHolder.loadDirectory(file)
    }

    fun primeExplorer() {
        fileExplorerStateHolder.primeExplorerRoot()
    }

    fun openExplorer() {
        fileExplorerStateHolder.openExplorerRoot()
    }

    fun navigateUp() {
        fileExplorerStateHolder.navigateUp()
    }

    fun refreshExplorer() {
        fileExplorerStateHolder.refreshCurrentDirectory()
    }

    fun selectStorage(index: Int) {
        fileExplorerStateHolder.selectStorage(index)
    }

    fun refreshAvailableStorages() {
        fileExplorerStateHolder.refreshAvailableStorages()
    }

    fun isAtRoot(): Boolean = fileExplorerStateHolder.isAtRoot()

    fun explorerRoot(): File = fileExplorerStateHolder.rootDirectory()

    // ─── Appearance ───────────────────────────────────────────────────────────

    fun setAppThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferencesRepository.setAppThemeMode(mode)
        }
    }

    fun setAppLanguage(languageTag: String) {
        val normalized = AppLanguage.normalize(languageTag)
        AppLocaleManager.applyLanguage(context, normalized)
        _uiState.update { it.copy(appLanguageTag = normalized) }
    }

    // Método para guardar la preferencia de tema del reproductor
    fun setPlayerThemePreference(preference: String) {
        viewModelScope.launch {
            themePreferencesRepository.setPlayerThemePreference(preference)
        }
    }

    fun setAlbumArtPaletteStyle(style: AlbumArtPaletteStyle) {
        viewModelScope.launch {
            themePreferencesRepository.setAlbumArtPaletteStyle(style)
        }
    }

    fun setAlbumArtPaletteSettings(
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ) {
        viewModelScope.launch {
            themePreferencesRepository.setAlbumArtPaletteSettings(style, accuracyLevel)
        }
    }

    suspend fun getAlbumArtPalettePreview(
        uriString: String,
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ): ColorSchemePair? {
        return colorSchemeProcessor.getPreviewColorScheme(
            albumArtUri = uriString,
            paletteStyle = style,
            colorAccuracyLevel = accuracyLevel
        )
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setNavBarCompactMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCompactMode(enabled)
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch { userPreferencesRepository.setNavBarCornerRadius(radius) }
    }

    fun setLibraryNavigationMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLibraryNavigationMode(mode)
        }
    }

    fun setCarouselStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setCarouselStyle(style)
        }
    }

    fun setLaunchTab(tab: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLaunchTab(tab)
        }
    }

    fun setShowPlayerFileInfo(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowPlayerFileInfo(show)
        }
    }

    fun setShowScrollbar(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowScrollbar(enabled)
        }
    }

    fun setCollagePattern(pattern: CollagePattern) {
        viewModelScope.launch {
            userPreferencesRepository.setCollagePattern(pattern)
        }
    }

    fun setCollageAutoRotate(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCollageAutoRotate(enabled)
        }
    }

    fun setDisableBlurAllOver(disabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDisableBlurAllOver(disabled)
        }
    }

    // ─── Playback ─────────────────────────────────────────────────────────────

    fun setKeepPlayingInBackground(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepPlayingInBackground(enabled)
        }
    }

    fun setShowQueueHistory(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowQueueHistory(show)
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeEnabled(enabled)
        }
    }

    fun setCrossfadeDuration(duration: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeDuration(duration)
        }
    }

    fun setHiFiModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHiFiModeEnabled(enabled)
        }
    }

    fun setPersistentShuffleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPersistentShuffleEnabled(enabled)
        }
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setReplayGainEnabled(enabled)
        }
    }

    fun setReplayGainUseAlbumGain(useAlbumGain: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setReplayGainUseAlbumGain(useAlbumGain)
        }
    }

    // Full player loading tweaks

    fun setDelayAllFullPlayerContent(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayAllFullPlayerContent(enabled)
        }
    }

    fun setDelayAlbumCarousel(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayAlbumCarousel(enabled)
        }
    }

    fun setDelaySongMetadata(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelaySongMetadata(enabled)
        }
    }

    fun setDelayProgressBar(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayProgressBar(enabled)
        }
    }

    fun setDelayControls(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayControls(enabled)
        }
    }

    fun setFullPlayerPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerPlaceholders(enabled)
            if (!enabled) {
                userPreferencesRepository.setTransparentPlaceholders(false)
            }
        }
    }

    fun setTransparentPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTransparentPlaceholders(enabled)
        }
    }

    fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerPlaceholdersOnClose(enabled)
        }
    }

    fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerSwitchOnDragRelease(enabled)
        }
    }

    fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerAppearThreshold(thresholdPercent)
        }
    }

    fun setFullPlayerCloseThreshold(thresholdPercent: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerCloseThreshold(thresholdPercent)
        }
    }

    // ─── Lyrics ───────────────────────────────────────────────────────────────

    fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        viewModelScope.launch {
            userPreferencesRepository.setLyricsSourcePreference(preference)
        }
    }

    fun setAutoScanLrcFiles(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoScanLrcFiles(enabled)
        }
    }

    /** Automatic lyrics timing (measures each song's offset from its audio). On by default. */
    val lyricsAutoSyncEnabled: StateFlow<Boolean> = userPreferencesRepository.lyricsAutoSyncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setLyricsAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setLyricsAutoSyncEnabled(enabled)
        }
    }

    fun setLyricsIntegrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setLyricsIntegrationEnabled(enabled)
        }
    }

    fun setImmersiveLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveLyricsEnabled(enabled)
        }
    }

    fun setImmersiveLyricsTimeout(timeout: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveLyricsTimeout(timeout)
        }
    }

    fun setUseAnimatedLyrics(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseAnimatedLyrics(enabled)
        }
    }

    fun setAnimatedLyricsBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAnimatedLyricsBlurEnabled(enabled)
        }
    }

    fun setAnimatedLyricsBlurStrength(strength: Float) {
        viewModelScope.launch {
            userPreferencesRepository.setAnimatedLyricsBlurStrength(strength)
        }
    }

    // ─── Behavior ─────────────────────────────────────────────────────────────

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setOfflineMode(enabled)
        }
    }

    fun setDisableCastAutoplay(disabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDisableCastAutoplay(disabled)
        }
    }

    fun setPauseOnVolumeZero(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPauseOnVolumeZero(enabled)
        }
    }

    fun setResumeOnHeadsetReconnect(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setResumeOnHeadsetReconnect(enabled)
        }
    }

    fun setFolderBackGestureNavigation(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFolderBackGestureNavigation(enabled)
        }
    }

    fun setAppRebrandDialogShown(wasShown: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAppRebrandDialogShown(wasShown)
        }
    }

    fun setBeta05CleanInstallDisclaimerDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBeta05CleanInstallDisclaimerDismissed(dismissed)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHapticsEnabled(enabled)
        }
    }

    // ─── AI ───────────────────────────────────────────────────────────────────

    private val settingsAiStateHolder = SettingsAiStateHolder(aiPreferencesRepository, aiUsageDao, viewModelScope)

    val aiProvider: StateFlow<String> = settingsAiStateHolder.aiProvider
    val currentAiApiKey: StateFlow<String> = settingsAiStateHolder.currentAiApiKey
    val currentAiModel: StateFlow<String> = settingsAiStateHolder.currentAiModel
    val currentAiSystemPrompt: StateFlow<String> = settingsAiStateHolder.currentAiSystemPrompt
    val currentAiBaseUrl: StateFlow<String> = settingsAiStateHolder.currentAiBaseUrl
    val aiTemperature: StateFlow<Float> = settingsAiStateHolder.aiTemperature
    val aiTopP: StateFlow<Float> = settingsAiStateHolder.aiTopP
    val aiTopK: StateFlow<Int> = settingsAiStateHolder.aiTopK
    val aiMaxTokens: StateFlow<Int> = settingsAiStateHolder.aiMaxTokens
    val aiPresencePenalty: StateFlow<Float> = settingsAiStateHolder.aiPresencePenalty
    val aiFrequencyPenalty: StateFlow<Float> = settingsAiStateHolder.aiFrequencyPenalty
    val aiSampleSize: StateFlow<Int> = settingsAiStateHolder.aiSampleSize
    val aiDigestMode: StateFlow<String> = settingsAiStateHolder.aiDigestMode
    val aiIncludeExtendedFields: StateFlow<Boolean> = settingsAiStateHolder.aiIncludeExtendedFields
    val isSafeTokenLimitEnabled: StateFlow<Boolean> = settingsAiStateHolder.isSafeTokenLimitEnabled
    val recentAiUsage: StateFlow<List<AiUsageEntity>> = settingsAiStateHolder.recentAiUsage
    val totalPromptTokens: StateFlow<Int> = settingsAiStateHolder.totalPromptTokens
    val totalOutputTokens: StateFlow<Int> = settingsAiStateHolder.totalOutputTokens
    val totalThoughtTokens: StateFlow<Int> = settingsAiStateHolder.totalThoughtTokens

    fun onAiProviderChange(provider: String) = settingsAiStateHolder.onAiProviderChange(provider)
    fun onAiModelChange(model: String) = settingsAiStateHolder.onAiModelChange(model)
    fun onAiSystemPromptChange(prompt: String) = settingsAiStateHolder.onAiSystemPromptChange(prompt)
    fun resetAiSystemPrompt() = settingsAiStateHolder.resetAiSystemPrompt()
    fun onAiTemperatureChange(value: Float) = settingsAiStateHolder.onAiTemperatureChange(value)
    fun onAiTopPChange(value: Float) = settingsAiStateHolder.onAiTopPChange(value)
    fun onAiTopKChange(value: Int) = settingsAiStateHolder.onAiTopKChange(value)
    fun onAiMaxTokensChange(value: Int) = settingsAiStateHolder.onAiMaxTokensChange(value)
    fun onAiPresencePenaltyChange(value: Float) = settingsAiStateHolder.onAiPresencePenaltyChange(value)
    fun onAiFrequencyPenaltyChange(value: Float) = settingsAiStateHolder.onAiFrequencyPenaltyChange(value)
    fun onAiSampleSizeChange(value: Int) = settingsAiStateHolder.onAiSampleSizeChange(value)
    fun onAiDigestModeChange(mode: String) = settingsAiStateHolder.onAiDigestModeChange(mode)
    fun onAiIncludeExtendedFieldsChange(enabled: Boolean) = settingsAiStateHolder.onAiIncludeExtendedFieldsChange(enabled)
    fun setSafeTokenLimitEnabled(enabled: Boolean) = settingsAiStateHolder.setSafeTokenLimitEnabled(enabled)
    fun clearAiUsageData() = settingsAiStateHolder.clearAiUsageData()

    // ─── Backup & Restore ─────────────────────────────────────────────────────

    private val backupRestoreStateHolder = BackupRestoreStateHolder(
        backupManager = backupManager,
        syncManager = syncManager,
        uiState = _uiState,
        scope = viewModelScope,
        context = context
    )

    val dataTransferEvents: SharedFlow<String> = backupRestoreStateHolder.dataTransferEvents
    val dataTransferProgress: StateFlow<BackupTransferProgressUpdate?> = backupRestoreStateHolder.dataTransferProgress

    fun exportAppData(uri: Uri, sections: Set<BackupSection>) =
        backupRestoreStateHolder.exportAppData(uri, sections)

    fun inspectBackupFile(uri: Uri) = backupRestoreStateHolder.inspectBackupFile(uri)

    fun updateRestorePlanSelection(selectedModules: Set<BackupSection>) =
        backupRestoreStateHolder.updateRestorePlanSelection(selectedModules)

    fun restoreFromPlan(uri: Uri) = backupRestoreStateHolder.restoreFromPlan(uri)

    fun clearRestorePlan() = backupRestoreStateHolder.clearRestorePlan()

    fun removeBackupHistoryEntry(entry: BackupHistoryEntry) =
        backupRestoreStateHolder.removeBackupHistoryEntry(entry)

    fun setBackupInfoDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBackupInfoDismissed(dismissed)
        }
    }

    // ─── Developer ────────────────────────────────────────────────────────────

    // ─── Scrobbling ───────────────────────────────────────────────────────────

    val listenBrainzToken: StateFlow<String> = userPreferencesRepository.listenBrainzTokenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setListenBrainzToken(token: String) {
        viewModelScope.launch { userPreferencesRepository.setListenBrainzToken(token) }
    }

    val albumArtQuality: StateFlow<AlbumArtQuality> = userPreferencesRepository.albumArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumArtQuality.MEDIUM)

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val miniPlayerSongTransition: StateFlow<Boolean> = userPreferencesRepository.miniPlayerSongTransitionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAlbumArtQuality(quality: AlbumArtQuality) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumArtQuality(quality)
        }
    }

    fun setAlbumArtCacheLimitMb(limitMb: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumArtCacheLimitMb(limitMb)
            com.theveloper.pixelplay.utils.AlbumArtCacheManager.configuredCacheLimitMb = limitMb.toLong()
        }
    }

    fun setUseSmoothCorners(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseSmoothCorners(enabled)
        }
    }

    fun setMiniPlayerSongTransition(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setMiniPlayerSongTransition(enabled)
        }
    }

    fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTapBackgroundClosesPlayer(enabled)
        }
    }

    /**
     * Triggers a test crash to verify the crash handler is working correctly.
     * This should only be used for testing in Developer Options.
     */
    fun triggerTestCrash() {
        throw RuntimeException(context.getString(R.string.settings_dev_test_crash_message))
    }

    fun resetSetupFlow() {
        viewModelScope.launch {
            userPreferencesRepository.setInitialSetupDone(false)
        }
    }

    // ─── Init: uiState collectors ─────────────────────────────────────────────

    init {
        // One-time device capability check — result is cached inside HiFiCapabilityChecker
        _uiState.update {
            it.copy(
                hiFiModeDeviceSupported = HiFiCapabilityChecker.isSupported(),
                appLanguageTag = AppLocaleManager.currentLanguageTag(context)
            )
        }

        // Consolidated collectors using combine() to reduce coroutine overhead.
        // 3 combined flows mirror 50 preference streams into uiState instead of ~50
        // individual collector coroutines; the backup-history flow is collected
        // inside BackupRestoreStateHolder.

        // Group 1: Core UI settings (theme, navigation, appearance)
        viewModelScope.launch {
            combine<Any?, SettingsUiUpdate.Group1>(
                userPreferencesRepository.appRebrandDialogShownFlow,
                themePreferencesRepository.appThemeModeFlow,
                themePreferencesRepository.playerThemePreferenceFlow,
                themePreferencesRepository.albumArtPaletteStyleFlow,
                themePreferencesRepository.albumArtColorAccuracyFlow,
                userPreferencesRepository.mockGenresEnabledFlow,
                userPreferencesRepository.navBarCornerRadiusFlow,
                userPreferencesRepository.navBarStyleFlow,
                userPreferencesRepository.navBarCompactModeFlow,
                userPreferencesRepository.libraryNavigationModeFlow,
                userPreferencesRepository.carouselStyleFlow,
                userPreferencesRepository.launchTabFlow,
                userPreferencesRepository.showPlayerFileInfoFlow
            ) { values ->
                SettingsUiUpdate.Group1(
                    appRebrandDialogShown = values[0] as Boolean,
                    appThemeMode = values[1] as String,
                    playerThemePreference = values[2] as String,
                    albumArtPaletteStyle = values[3] as AlbumArtPaletteStyle,
                    albumArtColorAccuracy = values[4] as Int,
                    mockGenresEnabled = values[5] as Boolean,
                    navBarCornerRadius = values[6] as Int,
                    navBarStyle = values[7] as String,
                    navBarCompactMode = values[8] as Boolean,
                    libraryNavigationMode = values[9] as String,
                    carouselStyle = values[10] as String,
                    launchTab = values[11] as String,
                    showPlayerFileInfo = values[12] as Boolean
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        appRebrandDialogShown = update.appRebrandDialogShown,
                        appThemeMode = update.appThemeMode,
                        playerThemePreference = update.playerThemePreference,
                        albumArtPaletteStyle = update.albumArtPaletteStyle,
                        albumArtColorAccuracy = update.albumArtColorAccuracy,
                        mockGenresEnabled = update.mockGenresEnabled,
                        navBarCornerRadius = update.navBarCornerRadius,
                        navBarStyle = update.navBarStyle,
                        navBarCompactMode = update.navBarCompactMode,
                        libraryNavigationMode = update.libraryNavigationMode,
                        carouselStyle = update.carouselStyle,
                        launchTab = update.launchTab,
                        showPlayerFileInfo = update.showPlayerFileInfo
                    )
                }
            }
        }

        // Group 2: Playback and system settings
        viewModelScope.launch {
            combine<Any?, SettingsUiUpdate.Group2>(
                userPreferencesRepository.keepPlayingInBackgroundFlow,
                userPreferencesRepository.disableCastAutoplayFlow,
                userPreferencesRepository.pauseOnVolumeZeroFlow,
                userPreferencesRepository.resumeOnHeadsetReconnectFlow,
                userPreferencesRepository.showQueueHistoryFlow,
                userPreferencesRepository.isCrossfadeEnabledFlow,
                userPreferencesRepository.hiFiModeEnabledFlow,
                userPreferencesRepository.crossfadeDurationFlow,
                userPreferencesRepository.persistentShuffleEnabledFlow,
                userPreferencesRepository.folderBackGestureNavigationFlow,
                userPreferencesRepository.lyricsSourcePreferenceFlow,
                userPreferencesRepository.autoScanLrcFilesFlow,
                userPreferencesRepository.blockedDirectoriesFlow,
                userPreferencesRepository.hapticsEnabledFlow,
                userPreferencesRepository.immersiveLyricsEnabledFlow,
                userPreferencesRepository.immersiveLyricsTimeoutFlow,
                userPreferencesRepository.animatedLyricsBlurEnabledFlow,
                userPreferencesRepository.animatedLyricsBlurStrengthFlow,
                userPreferencesRepository.disableBlurAllOverFlow,
                userPreferencesRepository.showScrollbarFlow
            ) { values ->
                SettingsUiUpdate.Group2(
                    keepPlayingInBackground = values[0] as Boolean,
                    disableCastAutoplay = values[1] as Boolean,
                    pauseOnVolumeZero = values[2] as Boolean,
                    resumeOnHeadsetReconnect = values[3] as Boolean,
                    showQueueHistory = values[4] as Boolean,
                    isCrossfadeEnabled = values[5] as Boolean,
                    hiFiModeEnabled = values[6] as Boolean,
                    crossfadeDuration = values[7] as Int,
                    persistentShuffleEnabled = values[8] as Boolean,
                    folderBackGestureNavigation = values[9] as Boolean,
                    lyricsSourcePreference = values[10] as LyricsSourcePreference,
                    autoScanLrcFiles = values[11] as Boolean,
                    blockedDirectories = @Suppress("UNCHECKED_CAST") (values[12] as Set<String>),
                    hapticsEnabled = values[13] as Boolean,
                    immersiveLyricsEnabled = values[14] as Boolean,
                    immersiveLyricsTimeout = values[15] as Long,
                    animatedLyricsBlurEnabled = values[16] as Boolean,
                    animatedLyricsBlurStrength = values[17] as Float,
                    disableBlurAllOver = values[18] as Boolean,
                    showScrollbar = values[19] as Boolean
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        keepPlayingInBackground = update.keepPlayingInBackground,
                        disableCastAutoplay = update.disableCastAutoplay,
                        pauseOnVolumeZero = update.pauseOnVolumeZero,
                        resumeOnHeadsetReconnect = update.resumeOnHeadsetReconnect,
                        showQueueHistory = update.showQueueHistory,
                        isCrossfadeEnabled = update.isCrossfadeEnabled,
                        hiFiModeEnabled = update.hiFiModeEnabled,
                        crossfadeDuration = update.crossfadeDuration,
                        persistentShuffleEnabled = update.persistentShuffleEnabled,
                        folderBackGestureNavigation = update.folderBackGestureNavigation,
                        lyricsSourcePreference = update.lyricsSourcePreference,
                        autoScanLrcFiles = update.autoScanLrcFiles,
                        blockedDirectories = update.blockedDirectories,
                        hapticsEnabled = update.hapticsEnabled,
                        immersiveLyricsEnabled = update.immersiveLyricsEnabled,
                        immersiveLyricsTimeout = update.immersiveLyricsTimeout,
                        animatedLyricsBlurEnabled = update.animatedLyricsBlurEnabled,
                        animatedLyricsBlurStrength = update.animatedLyricsBlurStrength,
                        disableBlurAllOver = update.disableBlurAllOver,
                        showScrollbar = update.showScrollbar
                    )
                }
            }
        }

        // Group 3: Library filtering, developer options, collage, and remaining mirrors
        viewModelScope.launch {
            combine<Any?, SettingsUiUpdate.Group3>(
                userPreferencesRepository.collagePatternFlow,
                userPreferencesRepository.collageAutoRotateFlow,
                userPreferencesRepository.fullPlayerLoadingTweaksFlow,
                userPreferencesRepository.useAnimatedLyricsFlow,
                userPreferencesRepository.backupInfoDismissedFlow,
                userPreferencesRepository.beta05CleanInstallDisclaimerDismissedFlow,
                fileExplorerStateHolder.isLoading,
                userPreferencesRepository.albumArtQualityFlow,
                userPreferencesRepository.albumArtCacheLimitMbFlow,
                userPreferencesRepository.tapBackgroundClosesPlayerFlow,
                userPreferencesRepository.minSongDurationFlow,
                userPreferencesRepository.minTracksPerAlbumFlow,
                userPreferencesRepository.replayGainEnabledFlow,
                userPreferencesRepository.replayGainUseAlbumGainFlow,
                aiPreferencesRepository.isSafeTokenLimitEnabled,
                userPreferencesRepository.offlineModeFlow,
                userPreferencesRepository.lyricsIntegrationEnabledFlow
            ) { values ->
                SettingsUiUpdate.Group3(
                    collagePattern = values[0] as CollagePattern,
                    collageAutoRotate = values[1] as Boolean,
                    fullPlayerLoadingTweaks = values[2] as FullPlayerLoadingTweaks,
                    useAnimatedLyrics = values[3] as Boolean,
                    backupInfoDismissed = values[4] as Boolean,
                    beta05CleanInstallDisclaimerDismissed = values[5] as Boolean,
                    isLoadingDirectories = values[6] as Boolean,
                    albumArtQuality = values[7] as AlbumArtQuality,
                    albumArtCacheLimitMb = values[8] as Int,
                    tapBackgroundClosesPlayer = values[9] as Boolean,
                    minSongDuration = values[10] as Int,
                    minTracksPerAlbum = values[11] as Int,
                    replayGainEnabled = values[12] as Boolean,
                    replayGainUseAlbumGain = values[13] as Boolean,
                    isSafeTokenLimitEnabled = values[14] as Boolean,
                    offlineMode = values[15] as Boolean,
                    lyricsIntegrationEnabled = values[16] as Boolean
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        collagePattern = update.collagePattern,
                        collageAutoRotate = update.collageAutoRotate,
                        fullPlayerLoadingTweaks = update.fullPlayerLoadingTweaks,
                        useAnimatedLyrics = update.useAnimatedLyrics,
                        backupInfoDismissed = update.backupInfoDismissed,
                        beta05CleanInstallDisclaimerDismissed = update.beta05CleanInstallDisclaimerDismissed,
                        isLoadingDirectories = update.isLoadingDirectories,
                        albumArtQuality = update.albumArtQuality,
                        albumArtCacheLimitMb = update.albumArtCacheLimitMb,
                        tapBackgroundClosesPlayer = update.tapBackgroundClosesPlayer,
                        minSongDuration = update.minSongDuration,
                        minTracksPerAlbum = update.minTracksPerAlbum,
                        replayGainEnabled = update.replayGainEnabled,
                        replayGainUseAlbumGain = update.replayGainUseAlbumGain,
                        isSafeTokenLimitEnabled = update.isSafeTokenLimitEnabled,
                        offlineMode = update.offlineMode,
                        lyricsIntegrationEnabled = update.lyricsIntegrationEnabled
                    )
                }
            }
        }

        // Ambient Suggestions collector
        viewModelScope.launch {
            combine(
                com.theveloper.pixelplay.data.recognition.ambient.AmbientListeningService.isRunning,
                userPreferencesRepository.ambientDuckingFactorFlow,
                userPreferencesRepository.ambientAudioChimeEnabledFlow
            ) { enabled, duckFactor, chimeEnabled ->
                Triple(enabled, duckFactor, chimeEnabled)
            }.collect { (enabled, duckFactor, chimeEnabled) ->
                _uiState.update { state ->
                    state.copy(
                        ambientSuggestionsEnabled = enabled,
                        ambientDuckingFactor = duckFactor,
                        ambientAudioChimeEnabled = chimeEnabled
                    )
                }
            }
        }
    }

    fun setAmbientSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAmbientSuggestionsEnabled(enabled)
        }
    }

    fun setAmbientDuckingFactor(factor: Float) {
        viewModelScope.launch {
            userPreferencesRepository.setAmbientDuckingFactor(factor)
        }
    }

    fun setAmbientAudioChimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAmbientAudioChimeEnabled(enabled)
        }
    }
}
