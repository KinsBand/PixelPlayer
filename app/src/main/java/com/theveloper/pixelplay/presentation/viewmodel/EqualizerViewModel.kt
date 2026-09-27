package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.dsp.AuditionSlot
import com.theveloper.pixelplay.data.dsp.AuditionState
import com.theveloper.pixelplay.data.dsp.AudioOutputDeviceTracker
import com.theveloper.pixelplay.data.dsp.AutoEqDatabase
import com.theveloper.pixelplay.data.dsp.AutoEqHeadphoneProfile
import com.theveloper.pixelplay.data.dsp.CrossfeedStrength
import com.theveloper.pixelplay.data.dsp.DspEngineManager
import com.theveloper.pixelplay.data.dsp.FilterType
import com.theveloper.pixelplay.data.dsp.OutputDeviceInfo
import com.theveloper.pixelplay.data.dsp.ParametricBand
import com.theveloper.pixelplay.data.equalizer.EqualizerManager
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.data.preferences.EqualizerPreferencesRepository
import com.theveloper.pixelplay.data.preferences.EqualizerViewMode
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

data class EqualizerUiState(
    val isEnabled: Boolean = false,
    val adaptToGenre: Boolean = false,
    val currentPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bandLevels: List<Int> = List(10) { 0 },
    val editingPresetName: String? = null,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Float = 0f,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Float = 0f,
    val loudnessEnhancerEnabled: Boolean = false,
    val loudnessEnhancerStrength: Float = 0f,
    val isBassBoostSupported: Boolean = true,
    val isVirtualizerSupported: Boolean = true,
    val isLoudnessEnhancerSupported: Boolean = true,
    val viewMode: EqualizerViewMode = EqualizerViewMode.SLIDERS,
    val isBassBoostDismissed: Boolean = false,
    val isVirtualizerDismissed: Boolean = false,
    val isLoudnessDismissed: Boolean = false,
    val customPresets: List<EqualizerPreset> = emptyList(),
    val pinnedPresetsNames: List<String> = emptyList(),

    // Phase 1: Auto-Preamp, AutoEq, Output Auto-Switching, A/B, Ergonomics
    val autoPreampEnabled: Boolean = true,
    val manualPreampDb: Float = 0f,
    val effectivePreampDb: Float = 0f,
    val autoSwitchDeviceEnabled: Boolean = true,
    val currentOutputDevice: OutputDeviceInfo = OutputDeviceInfo.SPEAKER_DEFAULT,
    val devicePresetMap: Map<String, String> = emptyMap(),
    val auditionState: AuditionState = AuditionState(),
    val fineTuneBandIndex: Int? = null,
    val showAutoEqSheet: Boolean = false,
    val autoEqSearchQuery: String = "",

    // Phase 2: Pro Audiophile DSP Engine
    val dspEngineEnabled: Boolean = true,
    val isParametricMode: Boolean = false,
    val parametricBands: List<ParametricBand> = emptyList(),
    val subsonicEnabled: Boolean = false,
    val subsonicCutoffHz: Float = 25f,
    val ultrasonicEnabled: Boolean = false,
    val ultrasonicCutoffHz: Float = 20000f,
    val crossfeedStrength: CrossfeedStrength = CrossfeedStrength.OFF,
    val limiterEnabled: Boolean = true,
    val softSaturationEnabled: Boolean = true,
    val frequencyResponseCurve: List<Pair<Float, Float>> = emptyList()
) {
    // Computed property for accessible presets (Pinned)
    val accessiblePresets: List<EqualizerPreset>
        get() {
            return pinnedPresetsNames.mapNotNull { name ->
                customPresets.find { it.name == name }
                    ?: AutoEqDatabase.findById(name)?.toEqualizerPreset()
                    ?: EqualizerPreset.fromName(name)
            }
        }

    // Computed property for All Available Presets (for Edit Sheet)
    val allAvailablePresets: List<EqualizerPreset>
        get() = EqualizerPreset.ALL_PRESETS + customPresets
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager,
    private val equalizerPreferencesRepository: EqualizerPreferencesRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    private val dspEngineManager: DspEngineManager,
    private val audioOutputDeviceTracker: AudioOutputDeviceTracker,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    companion object {
        private const val TAG = "EqualizerViewModel"
        private const val SLIDER_PERSIST_DEBOUNCE_MS = 150L
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    private val _systemVolume = MutableStateFlow(0f)
    val systemVolume: StateFlow<Float> = _systemVolume.asStateFlow()

    private var persistBandLevelsJob: Job? = null
    private var persistBassBoostJob: Job? = null
    private var persistVirtualizerJob: Job? = null
    private var persistLoudnessJob: Job? = null
    private var persistPreampJob: Job? = null

    init {
        initializeEqualizer()
        observeEqualizerState()
        viewModelScope.launch {
            equalizerPreferencesRepository.adaptToGenreFlow.collect { enabled ->
                _uiState.update { it.copy(adaptToGenre = enabled) }
            }
        }
        observeDspAndErgonomicsState()
        observeAudioOutputRouting()
        loadSystemVolume()
    }

    private fun loadSystemVolume() {
        try {
            val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            _systemVolume.value = if (max > 0) current.toFloat() / max.toFloat() else 0.5f
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load system volume")
        }
    }

    fun setSystemVolume(percent: Float) {
        viewModelScope.launch {
            try {
                val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val target = (percent * max).roundToInt().coerceIn(0, max)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
                _systemVolume.value = percent
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to set system volume")
            }
        }
    }

    private fun initializeEqualizer() {
        viewModelScope.launch {
            Timber.tag(TAG).d("Initializing equalizer...")

            if (!equalizerManager.isAttached) {
                val enabled = equalizerPreferencesRepository.equalizerEnabledFlow.first()
                val presetName = equalizerPreferencesRepository.equalizerPresetFlow.first()
                val customBands = equalizerPreferencesRepository.equalizerCustomBandsFlow.first()
                val bassBoostEnabled = equalizerPreferencesRepository.bassBoostEnabledFlow.first()
                val bassBoost = equalizerPreferencesRepository.bassBoostStrengthFlow.first()
                val virtualizerEnabled = equalizerPreferencesRepository.virtualizerEnabledFlow.first()
                val virtualizer = equalizerPreferencesRepository.virtualizerStrengthFlow.first()
                val loudnessEnabled = equalizerPreferencesRepository.loudnessEnhancerEnabledFlow.first()
                val loudnessStrength = equalizerPreferencesRepository.loudnessEnhancerStrengthFlow.first()

                equalizerManager.restoreState(
                    enabled, presetName, customBands,
                    bassBoostEnabled, bassBoost,
                    virtualizerEnabled, virtualizer,
                    loudnessEnabled, loudnessStrength
                )

                val initialSessionId = dualPlayerEngine.getAudioSessionId()
                if (initialSessionId != 0) {
                    equalizerManager.attachToAudioSessionIfNeeded(initialSessionId)
                }
            } else {
                Timber.tag(TAG).d("Equalizer already attached by service, skipping restore.")
            }

            _uiState.update { current ->
                current.copy(
                    isBassBoostSupported = equalizerManager.isBassBoostSupported(),
                    isVirtualizerSupported = equalizerManager.isVirtualizerSupported(),
                    isLoudnessEnhancerSupported = equalizerManager.isLoudnessEnhancerSupported()
                )
            }

            dualPlayerEngine.activeAudioSessionId.collect { sessionId ->
                if (sessionId != 0) {
                    Timber.tag(TAG).d("Audio Session ID changed to $sessionId.")
                    _uiState.update { current ->
                        current.copy(
                            isBassBoostSupported = equalizerManager.isBassBoostSupported(),
                            isVirtualizerSupported = equalizerManager.isVirtualizerSupported(),
                            isLoudnessEnhancerSupported = equalizerManager.isLoudnessEnhancerSupported()
                        )
                    }
                }
            }
        }
    }

    private fun observeEqualizerState() {
        viewModelScope.launch {
            combine(
                equalizerPreferencesRepository.equalizerEnabledFlow,
                equalizerPreferencesRepository.equalizerPresetFlow,
                equalizerPreferencesRepository.equalizerCustomBandsFlow,
                equalizerPreferencesRepository.bassBoostEnabledFlow,
                equalizerPreferencesRepository.bassBoostStrengthFlow,
                equalizerPreferencesRepository.virtualizerEnabledFlow,
                equalizerPreferencesRepository.virtualizerStrengthFlow,
                equalizerPreferencesRepository.loudnessEnhancerEnabledFlow,
                equalizerPreferencesRepository.loudnessEnhancerStrengthFlow,
                equalizerPreferencesRepository.bassBoostDismissedFlow,
                equalizerPreferencesRepository.virtualizerDismissedFlow,
                equalizerPreferencesRepository.loudnessDismissedFlow,
                equalizerPreferencesRepository.equalizerViewModeFlow,
                equalizerPreferencesRepository.customPresetsFlow,
                equalizerPreferencesRepository.pinnedPresetsFlow
            ) { values ->
                val enabled = values[0] as Boolean
                val presetName = values[1] as String
                val customBands = (values[2] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
                val bbEnabled = values[3] as Boolean
                val bbStrength = values[4] as Int
                val vEnabled = values[5] as Boolean
                val vStrength = values[6] as Int
                val lEnabled = values[7] as Boolean
                val lStrength = values[8] as Int
                val bbDismissed = values[9] as Boolean
                val vDismissed = values[10] as Boolean
                val lDismissed = values[11] as Boolean
                val viewMode = values[12] as EqualizerViewMode
                val customPresets = (values[13] as? List<*>)?.filterIsInstance<EqualizerPreset>() ?: emptyList()
                val pinnedPresets = (values[14] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                val currentPreset = if (presetName == "custom") {
                    EqualizerPreset.custom(customBands)
                } else {
                    customPresets.find { it.name == presetName }
                        ?: AutoEqDatabase.findById(presetName)?.toEqualizerPreset()
                        ?: EqualizerPreset.fromName(presetName)
                }

                _uiState.value.copy(
                    isEnabled = enabled,
                    currentPreset = currentPreset,
                    bandLevels = if (currentPreset.name == "custom") customBands else currentPreset.bandLevels,
                    bassBoostEnabled = bbEnabled,
                    bassBoostStrength = bbStrength.toFloat(),
                    virtualizerEnabled = vEnabled,
                    virtualizerStrength = vStrength.toFloat(),
                    loudnessEnhancerEnabled = lEnabled,
                    loudnessEnhancerStrength = lStrength.toFloat(),
                    isBassBoostDismissed = bbDismissed,
                    isVirtualizerDismissed = vDismissed,
                    isLoudnessDismissed = lDismissed,
                    viewMode = viewMode,
                    customPresets = customPresets,
                    pinnedPresetsNames = pinnedPresets
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun observeDspAndErgonomicsState() {
        // Auto-preamp & Manual preamp
        viewModelScope.launch {
            combine(
                equalizerPreferencesRepository.autoPreampEnabledFlow,
                equalizerPreferencesRepository.manualPreampDbFlow
            ) { autoPreamp, manualDb ->
                dspEngineManager.setAutoPreampEnabled(autoPreamp)
                dspEngineManager.setManualPreampDb(manualDb)
                Pair(autoPreamp, manualDb)
            }.collect { (autoPreamp, manualDb) ->
                _uiState.update { it.copy(autoPreampEnabled = autoPreamp, manualPreampDb = manualDb) }
            }
        }

        // DSP Engine Flow (Effective preamp, response curve)
        viewModelScope.launch {
            dspEngineManager.configFlow.collect { config ->
                val responseCurve = dspEngineManager.calculateFrequencyResponse(100)
                _uiState.update {
                    it.copy(
                        effectivePreampDb = config.effectivePreampDb,
                        frequencyResponseCurve = responseCurve
                    )
                }
            }
        }

        // DSP Engine Enabled
        viewModelScope.launch {
            equalizerPreferencesRepository.dspEngineEnabledFlow.collect { enabled ->
                dspEngineManager.setEngineEnabled(enabled)
                _uiState.update { it.copy(dspEngineEnabled = enabled) }
            }
        }

        // Parametric Mode & Bands
        viewModelScope.launch {
            combine(
                equalizerPreferencesRepository.parametricModeEnabledFlow,
                equalizerPreferencesRepository.parametricBandsFlow
            ) { peqEnabled, bands ->
                dspEngineManager.setParametricMode(peqEnabled)
                dspEngineManager.setParametricBands(bands)
                Pair(peqEnabled, bands)
            }.collect { (peqEnabled, bands) ->
                _uiState.update { it.copy(isParametricMode = peqEnabled, parametricBands = bands) }
            }
        }

        // Cleanup Filters (Subsonic & Ultrasonic)
        viewModelScope.launch {
            combine(
                equalizerPreferencesRepository.subsonicFilterEnabledFlow,
                equalizerPreferencesRepository.subsonicCutoffHzFlow,
                equalizerPreferencesRepository.ultrasonicFilterEnabledFlow,
                equalizerPreferencesRepository.ultrasonicCutoffHzFlow
            ) { subOn, subCutoff, ultraOn, ultraCutoff ->
                dspEngineManager.setSubsonicFilter(subOn, subCutoff)
                dspEngineManager.setUltrasonicFilter(ultraOn, ultraCutoff)
                listOf(subOn, subCutoff, ultraOn, ultraCutoff)
            }.collect { values ->
                _uiState.update {
                    it.copy(
                        subsonicEnabled = values[0] as Boolean,
                        subsonicCutoffHz = values[1] as Float,
                        ultrasonicEnabled = values[2] as Boolean,
                        ultrasonicCutoffHz = values[3] as Float
                    )
                }
            }
        }

        // Crossfeed Strength
        viewModelScope.launch {
            equalizerPreferencesRepository.crossfeedStrengthFlow.collect { strength ->
                dspEngineManager.setCrossfeedStrength(strength)
                _uiState.update { it.copy(crossfeedStrength = strength) }
            }
        }

        // Limiter & Soft Saturation
        viewModelScope.launch {
            combine(
                equalizerPreferencesRepository.limiterEnabledFlow,
                equalizerPreferencesRepository.softSaturationEnabledFlow
            ) { limiter, saturation ->
                dspEngineManager.setLimiterSettings(limiter, saturation)
                Pair(limiter, saturation)
            }.collect { (limiter, saturation) ->
                _uiState.update { it.copy(limiterEnabled = limiter, softSaturationEnabled = saturation) }
            }
        }

        // Auditioning State
        viewModelScope.launch {
            combine(
                equalizerPreferencesRepository.auditionSlotABandsFlow,
                equalizerPreferencesRepository.auditionSlotBBandsFlow,
                equalizerPreferencesRepository.auditionActiveSlotFlow,
                equalizerPreferencesRepository.auditionLoudnessCompFlow
            ) { slotA, slotB, activeSlot, loudnessComp ->
                AuditionState(
                    activeSlot = activeSlot,
                    slotABands = slotA,
                    slotBBands = slotB,
                    isLoudnessCompEnabled = loudnessComp
                )
            }.collect { auditionState ->
                _uiState.update { it.copy(auditionState = auditionState) }
            }
        }
    }

    private fun observeAudioOutputRouting() {
        viewModelScope.launch {
            combine(
                audioOutputDeviceTracker.currentDevice,
                equalizerPreferencesRepository.autoSwitchDeviceEnabledFlow,
                equalizerPreferencesRepository.devicePresetMapFlow
            ) { device, autoSwitch, deviceMap ->
                Triple(device, autoSwitch, deviceMap)
            }.collect { (device, autoSwitch, deviceMap) ->
                _uiState.update {
                    it.copy(
                        currentOutputDevice = device,
                        autoSwitchDeviceEnabled = autoSwitch,
                        devicePresetMap = deviceMap
                    )
                }

                // Automatic preset loading when audio route changes
                if (autoSwitch && !_uiState.value.adaptToGenre) {
                    val targetPresetName = deviceMap[device.routingKey]
                    if (!targetPresetName.isNullOrEmpty() && targetPresetName != _uiState.value.currentPreset.name) {
                        Timber.tag(TAG).d("Auto-switching to preset $targetPresetName for route ${device.name}")
                        val presetToApply = _uiState.value.allAvailablePresets.find { it.name == targetPresetName }
                            ?: AutoEqDatabase.findById(targetPresetName)?.toEqualizerPreset()
                            ?: EqualizerPreset.fromName(targetPresetName)
                        selectPreset(presetToApply)
                    }
                }
            }
        }
    }

    fun cycleViewMode() {
        viewModelScope.launch {
            val currentMode = _uiState.value.viewMode
            val nextMode = when (currentMode) {
                EqualizerViewMode.SLIDERS -> EqualizerViewMode.GRAPH
                EqualizerViewMode.GRAPH -> EqualizerViewMode.HYBRID
                EqualizerViewMode.HYBRID -> EqualizerViewMode.SLIDERS
            }
            equalizerPreferencesRepository.setEqualizerViewMode(nextMode)
        }
    }

    fun setEnabled(enabled: Boolean) {
        equalizerManager.setEnabled(enabled)
        dspEngineManager.setEngineEnabled(enabled)
        _uiState.update { current ->
            current.copy(isEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setEqualizerEnabled(enabled)
        }
    }

    fun toggleEqualizer() {
        setEnabled(!_uiState.value.isEnabled)
    }

    fun setAdaptToGenre(enabled: Boolean) {
        equalizerManager.genreAdaptationSuppressed = !enabled
        _uiState.update { it.copy(adaptToGenre = enabled) }
        if (enabled) {
            dspEngineManager.setParametricMode(false)
            dspEngineManager.setAutoPreampEnabled(true)
        }
        viewModelScope.launch { equalizerPreferencesRepository.setAdaptToGenre(enabled) }
    }

    private fun stopGenreAdaptationForManualEdit() {
        equalizerManager.genreAdaptationSuppressed = true
        _uiState.update { it.copy(adaptToGenre = false) }
        viewModelScope.launch { equalizerPreferencesRepository.setAdaptToGenre(false) }
    }

    fun selectPreset(preset: EqualizerPreset) {
        stopGenreAdaptationForManualEdit()
        persistBandLevelsJob?.cancel()
        equalizerManager.applyPreset(preset)
        dspEngineManager.setGraphicBands(preset.bandLevels)

        _uiState.update { current ->
            current.copy(
                currentPreset = preset,
                bandLevels = preset.bandLevels,
                editingPresetName = null
            )
        }
        viewModelScope.launch {
            equalizerPreferencesRepository.setEqualizerPreset(preset.name)
            if (!preset.isCustom) {
                equalizerPreferencesRepository.setEqualizerCustomBands(preset.bandLevels)
            }
        }
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        stopGenreAdaptationForManualEdit()
        if (bandIndex !in _uiState.value.bandLevels.indices) return
        val clampedLevel = level.coerceIn(-15, 15)

        equalizerManager.setBandLevel(bandIndex, clampedLevel)
        val updatedBands = equalizerManager.bandLevels.value
        dspEngineManager.setGraphicBands(updatedBands)

        _uiState.update { current ->
            val editingName = current.editingPresetName
                ?: current.currentPreset.name.takeIf { current.currentPreset.isCustom && it != "custom" }
            current.copy(
                currentPreset = EqualizerPreset.custom(updatedBands),
                bandLevels = updatedBands,
                editingPresetName = editingName
            )
        }

        persistBandLevelsJob?.cancel()
        persistBandLevelsJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setEqualizerCustomBands(updatedBands)
            equalizerPreferencesRepository.setEqualizerPreset("custom")
        }
    }

    fun resetBandToZero(bandIndex: Int) {
        setBandLevel(bandIndex, 0)
    }

    // ==========================================
    // Phase 1: Auto-Preamp & Headroom Compensation
    // ==========================================
    fun setAutoPreampEnabled(enabled: Boolean) {
        if (!enabled) stopGenreAdaptationForManualEdit()
        viewModelScope.launch {
            equalizerPreferencesRepository.setAutoPreampEnabled(enabled)
            dspEngineManager.setAutoPreampEnabled(enabled)
        }
    }

    fun setManualPreampDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(-20f, 6f)
        _uiState.update { it.copy(manualPreampDb = clamped) }
        dspEngineManager.setManualPreampDb(clamped)

        persistPreampJob?.cancel()
        persistPreampJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setManualPreampDb(clamped)
        }
    }

    // ==========================================
    // Phase 1: AutoEq Headphone Presets
    // ==========================================
    fun setAutoEqSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showAutoEqSheet = visible) }
    }

    fun setAutoEqSearchQuery(query: String) {
        _uiState.update { it.copy(autoEqSearchQuery = query) }
    }

    fun selectAutoEqProfile(profile: AutoEqHeadphoneProfile) {
        val preset = profile.toEqualizerPreset()
        selectPreset(preset)
        if (!_uiState.value.autoPreampEnabled) {
            setManualPreampDb(profile.recommendedPreampDb)
        }
        setAutoEqSheetVisible(false)
    }

    // ==========================================
    // Phase 1: Hardware Output Auto-Switching
    // ==========================================
    fun setAutoSwitchDeviceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setAutoSwitchDeviceEnabled(enabled)
        }
    }

    fun assignCurrentPresetToCurrentDevice() {
        val device = _uiState.value.currentOutputDevice
        val presetName = _uiState.value.currentPreset.name
        viewModelScope.launch {
            equalizerPreferencesRepository.setDevicePresetMapping(device.routingKey, presetName)
        }
    }

    fun setDevicePresetMapping(routingKey: String, presetName: String) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setDevicePresetMapping(routingKey, presetName)
        }
    }

    // ==========================================
    // Phase 1: UI Ergonomics (Fine-Tuning)
    // ==========================================
    fun openFineTuneBand(bandIndex: Int) {
        if (bandIndex in _uiState.value.bandLevels.indices) {
            _uiState.update { it.copy(fineTuneBandIndex = bandIndex) }
        }
    }

    fun dismissFineTune() {
        _uiState.update { it.copy(fineTuneBandIndex = null) }
    }

    fun adjustFineTuneValue(delta: Int) {
        val bandIndex = _uiState.value.fineTuneBandIndex ?: return
        val currentLevel = _uiState.value.bandLevels.getOrElse(bandIndex) { 0 }
        setBandLevel(bandIndex, (currentLevel + delta).coerceIn(-15, 15))
    }

    // ==========================================
    // Phase 1: A/B Auditioning & Loudness Match
    // ==========================================
    fun setAuditionSlot(slot: AuditionSlot) {
        stopGenreAdaptationForManualEdit()
        viewModelScope.launch {
            equalizerPreferencesRepository.setAuditionActiveSlot(slot)
            val currentState = _uiState.value.auditionState

            when (slot) {
                AuditionSlot.SLOT_A -> {
                    dspEngineManager.setAuditionLoudnessMultiplier(1.0f)
                    val bands = currentState.slotABands
                    applyBandsDirectly(bands)
                }
                AuditionSlot.SLOT_B -> {
                    dspEngineManager.setAuditionLoudnessMultiplier(1.0f)
                    val bands = currentState.slotBBands
                    applyBandsDirectly(bands)
                }
                AuditionSlot.BYPASS -> {
                    val previousBands = _uiState.value.bandLevels
                    val comp = if (currentState.isLoudnessCompEnabled) {
                        currentState.calculateBypassCompensationGain(previousBands)
                    } else {
                        1.0f
                    }
                    dspEngineManager.setAuditionLoudnessMultiplier(comp)
                    applyBandsDirectly(List(10) { 0 })
                }
            }
        }
    }

    fun copyCurrentToAuditionSlot(slot: AuditionSlot) {
        viewModelScope.launch {
            val bands = _uiState.value.bandLevels
            when (slot) {
                AuditionSlot.SLOT_A -> equalizerPreferencesRepository.setAuditionSlotABands(bands)
                AuditionSlot.SLOT_B -> equalizerPreferencesRepository.setAuditionSlotBBands(bands)
                AuditionSlot.BYPASS -> { /* Bypass is fixed flat */ }
            }
        }
    }

    fun setAuditionLoudnessComp(enabled: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setAuditionLoudnessComp(enabled)
            if (_uiState.value.auditionState.activeSlot == AuditionSlot.BYPASS) {
                val comp = if (enabled) {
                    _uiState.value.auditionState.calculateBypassCompensationGain(_uiState.value.bandLevels)
                } else 1.0f
                dspEngineManager.setAuditionLoudnessMultiplier(comp)
            }
        }
    }

    private fun applyBandsDirectly(bands: List<Int>) {
        equalizerManager.applyPreset(EqualizerPreset.custom(bands))
        dspEngineManager.setGraphicBands(bands)
        _uiState.update {
            it.copy(
                bandLevels = bands,
                currentPreset = EqualizerPreset.custom(bands)
            )
        }
    }

    // ==========================================
    // Phase 2: Pro Audiophile DSP Engine
    // ==========================================
    fun setDspEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setDspEngineEnabled(enabled)
            dspEngineManager.setEngineEnabled(enabled)
        }
    }

    fun setParametricMode(enabled: Boolean) {
        if (enabled) stopGenreAdaptationForManualEdit()
        viewModelScope.launch {
            equalizerPreferencesRepository.setParametricModeEnabled(enabled)
            dspEngineManager.setParametricMode(enabled)
            if (enabled && _uiState.value.parametricBands.isEmpty()) {
                val initialBands = ParametricBand.fromGraphicLevels(_uiState.value.bandLevels)
                equalizerPreferencesRepository.setParametricBands(initialBands)
                dspEngineManager.setParametricBands(initialBands)
            }
        }
    }

    fun updateParametricBand(band: ParametricBand) {
        viewModelScope.launch {
            val current = _uiState.value.parametricBands.toMutableList()
            val idx = current.indexOfFirst { it.id == band.id }
            if (idx != -1) {
                current[idx] = band
            } else {
                current.add(band)
            }
            equalizerPreferencesRepository.setParametricBands(current)
            dspEngineManager.setParametricBands(current)
            _uiState.update { it.copy(parametricBands = current) }
        }
    }

    fun addParametricBand() {
        viewModelScope.launch {
            val current = _uiState.value.parametricBands.toMutableList()
            val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
            current.add(ParametricBand(id = nextId, enabled = true, type = FilterType.PEAKING, frequency = 1000f, gainDb = 0f, q = 1.414f))
            equalizerPreferencesRepository.setParametricBands(current)
            dspEngineManager.setParametricBands(current)
            _uiState.update { it.copy(parametricBands = current) }
        }
    }

    fun removeParametricBand(bandId: Int) {
        viewModelScope.launch {
            val current = _uiState.value.parametricBands.filter { it.id != bandId }
            equalizerPreferencesRepository.setParametricBands(current)
            dspEngineManager.setParametricBands(current)
            _uiState.update { it.copy(parametricBands = current) }
        }
    }

    fun setSubsonicFilter(enabled: Boolean, cutoffHz: Float) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setSubsonicFilter(enabled, cutoffHz)
            dspEngineManager.setSubsonicFilter(enabled, cutoffHz)
        }
    }

    fun setUltrasonicFilter(enabled: Boolean, cutoffHz: Float) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setUltrasonicFilter(enabled, cutoffHz)
            dspEngineManager.setUltrasonicFilter(enabled, cutoffHz)
        }
    }

    fun setCrossfeedStrength(strength: CrossfeedStrength) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setCrossfeedStrength(strength)
            dspEngineManager.setCrossfeedStrength(strength)
        }
    }

    fun setLimiterSettings(limiter: Boolean, softSaturation: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setLimiterSettings(limiter, softSaturation)
            dspEngineManager.setLimiterSettings(limiter, softSaturation)
        }
    }

    // ==========================================
    // Custom Presets & Pinning
    // ==========================================
    fun saveCurrentAsCustomPreset(name: String) {
        viewModelScope.launch {
            val bands = equalizerManager.bandLevels.value
            val preset = EqualizerPreset(name, name, bands, true)
            equalizerPreferencesRepository.saveCustomPreset(preset)
            togglePinPreset(name)
            selectPreset(preset)
        }
    }

    fun deleteCustomPreset(preset: EqualizerPreset) {
        viewModelScope.launch {
            equalizerPreferencesRepository.deleteCustomPreset(preset.name)
            if (_uiState.value.currentPreset.name == preset.name) {
                selectPreset(EqualizerPreset.FLAT)
            }
        }
    }

    fun renameCustomPreset(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        viewModelScope.launch {
            equalizerPreferencesRepository.renameCustomPreset(oldName, newName)
        }
    }

    fun updateCustomPresetBands(presetName: String) {
        viewModelScope.launch {
            val bands = equalizerManager.bandLevels.value
            equalizerPreferencesRepository.updateCustomPresetBands(presetName, bands)
            selectPreset(EqualizerPreset(presetName, presetName, bands, true))
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        equalizerManager.setBassBoostEnabled(enabled)
        _uiState.update { current ->
            current.copy(bassBoostEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setBassBoostEnabled(enabled)
        }
    }

    fun setBassBoostStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, 1000)
        equalizerManager.setBassBoostStrength(clampedStrength)
        _uiState.update { current ->
            current.copy(bassBoostStrength = clampedStrength.toFloat())
        }

        persistBassBoostJob?.cancel()
        persistBassBoostJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setBassBoostStrength(clampedStrength)
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        equalizerManager.setVirtualizerEnabled(enabled)
        _uiState.update { current ->
            current.copy(virtualizerEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setVirtualizerEnabled(enabled)
        }
    }

    fun setVirtualizerStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, 1000)
        equalizerManager.setVirtualizerStrength(clampedStrength)
        _uiState.update { current ->
            current.copy(virtualizerStrength = clampedStrength.toFloat())
        }

        persistVirtualizerJob?.cancel()
        persistVirtualizerJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setVirtualizerStrength(clampedStrength)
        }
    }

    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        equalizerManager.setLoudnessEnhancerEnabled(enabled)
        _uiState.update { current ->
            current.copy(loudnessEnhancerEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setLoudnessEnhancerEnabled(enabled)
        }
    }

    fun setLoudnessEnhancerStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, 1000)
        equalizerManager.setLoudnessEnhancerStrength(clampedStrength)
        _uiState.update { current ->
            current.copy(loudnessEnhancerStrength = clampedStrength.toFloat())
        }

        persistLoudnessJob?.cancel()
        persistLoudnessJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setLoudnessEnhancerStrength(clampedStrength)
        }
    }

    fun setBassBoostDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setBassBoostDismissed(dismissed)
        }
    }

    fun setVirtualizerDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setVirtualizerDismissed(dismissed)
        }
    }

    fun setLoudnessDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setLoudnessDismissed(dismissed)
        }
    }

    fun updatePinnedPresetsOrder(newOrder: List<String>) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setPinnedPresets(newOrder)
        }
    }

    fun resetPinnedPresetsToDefault() {
        viewModelScope.launch {
            val defaultOrder = EqualizerPreset.ALL_PRESETS.map { it.name }
            equalizerPreferencesRepository.setPinnedPresets(defaultOrder)
        }
    }

    fun togglePinPreset(presetName: String) {
        viewModelScope.launch {
            val currentPinned = _uiState.value.pinnedPresetsNames.toMutableList()
            if (currentPinned.contains(presetName)) {
                currentPinned.remove(presetName)
            } else {
                currentPinned.add(presetName)
            }
            equalizerPreferencesRepository.setPinnedPresets(currentPinned)
        }
    }

    fun reattachToPlayer() {
        viewModelScope.launch {
            val audioSessionId = dualPlayerEngine.getAudioSessionId()
            Timber.tag(TAG).d("Reattaching equalizer to new audio session: $audioSessionId")
            equalizerManager.attachToAudioSessionIfNeeded(audioSessionId)
        }
    }

    private fun persistLatestStateAsync() {
        val latest = _uiState.value
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                equalizerPreferencesRepository.setEqualizerEnabled(latest.isEnabled)
                equalizerPreferencesRepository.setEqualizerPreset(latest.currentPreset.name)
                equalizerPreferencesRepository.setEqualizerCustomBands(equalizerManager.bandLevels.value)
                equalizerPreferencesRepository.setBassBoostEnabled(latest.bassBoostEnabled)
                equalizerPreferencesRepository.setBassBoostStrength(latest.bassBoostStrength.toInt().coerceIn(0, 1000))
                equalizerPreferencesRepository.setVirtualizerEnabled(latest.virtualizerEnabled)
                equalizerPreferencesRepository.setVirtualizerStrength(latest.virtualizerStrength.toInt().coerceIn(0, 1000))
                equalizerPreferencesRepository.setLoudnessEnhancerEnabled(latest.loudnessEnhancerEnabled)
                equalizerPreferencesRepository.setLoudnessEnhancerStrength(latest.loudnessEnhancerStrength.toInt().coerceIn(0, 1000))
                equalizerPreferencesRepository.setAutoPreampEnabled(latest.autoPreampEnabled)
                equalizerPreferencesRepository.setManualPreampDb(latest.manualPreampDb)
                equalizerPreferencesRepository.setDspEngineEnabled(latest.dspEngineEnabled)
            }.onFailure { error ->
                Timber.tag(TAG).w(error, "Failed to flush equalizer state during onCleared")
            }
        }
    }

    override fun onCleared() {
        persistBandLevelsJob?.cancel()
        persistBassBoostJob?.cancel()
        persistVirtualizerJob?.cancel()
        persistLoudnessJob?.cancel()
        persistPreampJob?.cancel()
        persistLatestStateAsync()
        super.onCleared()
        Timber.tag(TAG).d("ViewModel cleared")
    }
}
