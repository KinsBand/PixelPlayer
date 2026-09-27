package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.dsp.AuditionSlot
import com.theveloper.pixelplay.data.dsp.CrossfeedStrength
import com.theveloper.pixelplay.data.dsp.ParametricBand
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private object Keys {
        val ADAPT_TO_GENRE = booleanPreferencesKey("equalizer_adapt_to_genre")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_CUSTOM_BANDS = stringPreferencesKey("equalizer_custom_bands")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
        val LOUDNESS_ENHANCER_ENABLED = booleanPreferencesKey("loudness_enhancer_enabled")
        val LOUDNESS_ENHANCER_STRENGTH = intPreferencesKey("loudness_enhancer_strength")
        val BASS_BOOST_DISMISSED = booleanPreferencesKey("bass_boost_dismissed")
        val VIRTUALIZER_DISMISSED = booleanPreferencesKey("virtualizer_dismissed")
        val LOUDNESS_DISMISSED = booleanPreferencesKey("loudness_dismissed")
        val VIEW_MODE = stringPreferencesKey("equalizer_view_mode")
        val CUSTOM_PRESETS = stringPreferencesKey("custom_presets_json")
        val PINNED_PRESETS = stringPreferencesKey("pinned_presets_json")

        // Phase 1 & 2 Audiophile DSP & Ergonomics Keys
        val AUTO_PREAMP_ENABLED = booleanPreferencesKey("auto_preamp_enabled")
        val MANUAL_PREAMP_DB = floatPreferencesKey("manual_preamp_db")
        val DSP_ENGINE_ENABLED = booleanPreferencesKey("dsp_engine_enabled")
        val PARAMETRIC_MODE_ENABLED = booleanPreferencesKey("parametric_mode_enabled")
        val PARAMETRIC_BANDS = stringPreferencesKey("parametric_bands_json")
        val SUBSONIC_FILTER_ENABLED = booleanPreferencesKey("subsonic_filter_enabled")
        val SUBSONIC_CUTOFF_HZ = floatPreferencesKey("subsonic_cutoff_hz")
        val ULTRASONIC_FILTER_ENABLED = booleanPreferencesKey("ultrasonic_filter_enabled")
        val ULTRASONIC_CUTOFF_HZ = floatPreferencesKey("ultrasonic_cutoff_hz")
        val CROSSFEED_STRENGTH = intPreferencesKey("crossfeed_strength")
        val LIMITER_ENABLED = booleanPreferencesKey("limiter_enabled")
        val SOFT_SATURATION_ENABLED = booleanPreferencesKey("soft_saturation_enabled")
        val AUTO_SWITCH_DEVICE_ENABLED = booleanPreferencesKey("auto_switch_device_enabled")
        val DEVICE_PRESET_MAP = stringPreferencesKey("device_preset_map_json")
        val AUDITION_SLOT_A_BANDS = stringPreferencesKey("audition_slot_a_bands")
        val AUDITION_SLOT_B_BANDS = stringPreferencesKey("audition_slot_b_bands")
        val AUDITION_ACTIVE_SLOT = stringPreferencesKey("audition_active_slot")
        val AUDITION_LOUDNESS_COMP = booleanPreferencesKey("audition_loudness_comp")
    }

    val adaptToGenreFlow: Flow<Boolean> = dataStore.data.map { it[Keys.ADAPT_TO_GENRE] ?: false }

    suspend fun setAdaptToGenre(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ADAPT_TO_GENRE] = enabled
            if (enabled) {
                preferences[Keys.PARAMETRIC_MODE_ENABLED] = false
                preferences[Keys.AUTO_PREAMP_ENABLED] = true
            }
        }
    }

    val equalizerViewModeFlow: Flow<EqualizerViewMode> = dataStore.data.map { preferences ->
        val modeString = preferences[Keys.VIEW_MODE]
        if (modeString != null) {
            try {
                EqualizerViewMode.valueOf(modeString)
            } catch (_: Exception) {
                EqualizerViewMode.SLIDERS
            }
        } else {
            val isGraph = preferences[booleanPreferencesKey("is_graph_view")] ?: false
            if (isGraph) EqualizerViewMode.GRAPH else EqualizerViewMode.SLIDERS
        }
    }

    val equalizerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.EQUALIZER_ENABLED] ?: false
    }

    val equalizerPresetFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.EQUALIZER_PRESET] ?: "flat"
    }

    val equalizerCustomBandsFlow: Flow<List<Int>> = dataStore.data.map { preferences ->
        val stored = preferences[Keys.EQUALIZER_CUSTOM_BANDS]
        if (stored != null) {
            try {
                val decoded = json.decodeFromString<List<Int>>(stored)
                when {
                    decoded.size >= 10 -> decoded.take(10)
                    decoded.isEmpty() -> List(10) { 0 }
                    else -> decoded + List(10 - decoded.size) { 0 }
                }
            } catch (_: Exception) {
                List(10) { 0 }
            }
        } else {
            List(10) { 0 }
        }
    }

    val bassBoostStrengthFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.BASS_BOOST_STRENGTH] ?: 0
    }

    val virtualizerStrengthFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.VIRTUALIZER_STRENGTH] ?: 0
    }

    val bassBoostEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.BASS_BOOST_ENABLED] ?: false
    }

    val virtualizerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.VIRTUALIZER_ENABLED] ?: false
    }

    val loudnessEnhancerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LOUDNESS_ENHANCER_ENABLED] ?: false
    }

    val loudnessEnhancerStrengthFlow: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[Keys.LOUDNESS_ENHANCER_STRENGTH] ?: 0).coerceIn(0, 1000)
    }

    val bassBoostDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.BASS_BOOST_DISMISSED] ?: false
    }

    val virtualizerDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.VIRTUALIZER_DISMISSED] ?: false
    }

    val loudnessDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LOUDNESS_DISMISSED] ?: false
    }

    val customPresetsFlow: Flow<List<EqualizerPreset>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.CUSTOM_PRESETS]
        if (jsonString != null) {
            try {
                json.decodeFromString<List<EqualizerPreset>>(jsonString)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val pinnedPresetsFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.PINNED_PRESETS]
        if (jsonString != null) {
            try {
                json.decodeFromString<List<String>>(jsonString)
            } catch (_: Exception) {
                EqualizerPreset.ALL_PRESETS.map { it.name }
            }
        } else {
            EqualizerPreset.ALL_PRESETS.map { it.name }
        }
    }

    suspend fun setEqualizerViewMode(mode: EqualizerViewMode) =
        dataStore.edit { preferences ->
            preferences[Keys.VIEW_MODE] = mode.name
        }

    suspend fun setEqualizerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.EQUALIZER_ENABLED] = enabled
        }

    suspend fun setEqualizerPreset(preset: String) =
        dataStore.edit { preferences ->
            preferences[Keys.EQUALIZER_PRESET] = preset
        }

    suspend fun setEqualizerCustomBands(bands: List<Int>) =
        dataStore.edit { preferences ->
            val normalized = when {
                bands.size >= 10 -> bands.take(10)
                bands.isEmpty() -> List(10) { 0 }
                else -> bands + List(10 - bands.size) { 0 }
            }
            preferences[Keys.EQUALIZER_CUSTOM_BANDS] = json.encodeToString(normalized)
        }

    suspend fun setBassBoostStrength(strength: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_STRENGTH] = strength.coerceIn(0, 1000)
        }

    suspend fun setVirtualizerStrength(strength: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_STRENGTH] = strength.coerceIn(0, 1000)
        }

    suspend fun setBassBoostEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_ENABLED] = enabled
        }

    suspend fun setVirtualizerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_ENABLED] = enabled
        }

    suspend fun setLoudnessEnhancerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.LOUDNESS_ENHANCER_ENABLED] = enabled
        }

    suspend fun setLoudnessEnhancerStrength(strength: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.LOUDNESS_ENHANCER_STRENGTH] = strength.coerceIn(0, 1000)
        }

    suspend fun setBassBoostDismissed(dismissed: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_DISMISSED] = dismissed
        }

    suspend fun setVirtualizerDismissed(dismissed: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_DISMISSED] = dismissed
        }

    suspend fun setLoudnessDismissed(dismissed: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.LOUDNESS_DISMISSED] = dismissed
        }

    suspend fun setPinnedPresets(presetNames: List<String>) =
        dataStore.edit { preferences ->
            preferences[Keys.PINNED_PRESETS] = json.encodeToString(presetNames)
        }

    suspend fun saveCustomPreset(preset: EqualizerPreset) {
        val current = customPresetsFlow.first().toMutableList()
        current.removeAll { it.name == preset.name }
        current.add(preset)
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }
    }

    suspend fun deleteCustomPreset(presetName: String) {
        val current = customPresetsFlow.first().toMutableList()
        current.removeAll { it.name == presetName }
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }

        val pinned = pinnedPresetsFlow.first().toMutableList()
        if (pinned.remove(presetName)) {
            setPinnedPresets(pinned)
        }
    }

    suspend fun renameCustomPreset(oldName: String, newName: String) {
        val current = customPresetsFlow.first().toMutableList()
        val index = current.indexOfFirst { it.name == oldName }
        if (index == -1) return

        current[index] = current[index].copy(name = newName, displayName = newName)
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }

        val pinned = pinnedPresetsFlow.first().toMutableList()
        val pinnedIndex = pinned.indexOf(oldName)
        if (pinnedIndex != -1) {
            pinned[pinnedIndex] = newName
            setPinnedPresets(pinned)
        }

        val activePreset = dataStore.data.first()[Keys.EQUALIZER_PRESET]
        if (activePreset == oldName) {
            dataStore.edit { preferences ->
                preferences[Keys.EQUALIZER_PRESET] = newName
            }
        }
    }

    suspend fun updateCustomPresetBands(presetName: String, bandLevels: List<Int>) {
        val current = customPresetsFlow.first().toMutableList()
        val index = current.indexOfFirst { it.name == presetName }
        if (index == -1) return

        current[index] = current[index].copy(bandLevels = bandLevels)
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }
    }

    val autoPreampEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AUTO_PREAMP_ENABLED] ?: true
    }

    val manualPreampDbFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.MANUAL_PREAMP_DB] ?: 0f
    }

    val dspEngineEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.DSP_ENGINE_ENABLED] ?: true
    }

    val parametricModeEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.PARAMETRIC_MODE_ENABLED] ?: false
    }

    val parametricBandsFlow: Flow<List<ParametricBand>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.PARAMETRIC_BANDS]
        if (jsonString != null) {
            try {
                json.decodeFromString<List<ParametricBand>>(jsonString)
            } catch (_: Exception) {
                ParametricBand.defaultAudiophileBands()
            }
        } else {
            ParametricBand.defaultAudiophileBands()
        }
    }

    val subsonicFilterEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.SUBSONIC_FILTER_ENABLED] ?: false
    }

    val subsonicCutoffHzFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.SUBSONIC_CUTOFF_HZ] ?: 25f
    }

    val ultrasonicFilterEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.ULTRASONIC_FILTER_ENABLED] ?: false
    }

    val ultrasonicCutoffHzFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.ULTRASONIC_CUTOFF_HZ] ?: 20000f
    }

    val crossfeedStrengthFlow: Flow<CrossfeedStrength> = dataStore.data.map { preferences ->
        val ordinal = preferences[Keys.CROSSFEED_STRENGTH] ?: 0
        CrossfeedStrength.fromOrdinal(ordinal)
    }

    val limiterEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LIMITER_ENABLED] ?: true
    }

    val softSaturationEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.SOFT_SATURATION_ENABLED] ?: true
    }

    val autoSwitchDeviceEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AUTO_SWITCH_DEVICE_ENABLED] ?: true
    }

    val devicePresetMapFlow: Flow<Map<String, String>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.DEVICE_PRESET_MAP]
        if (jsonString != null) {
            try {
                json.decodeFromString<Map<String, String>>(jsonString)
            } catch (_: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }

    val auditionSlotABandsFlow: Flow<List<Int>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.AUDITION_SLOT_A_BANDS]
        if (jsonString != null) {
            try {
                json.decodeFromString<List<Int>>(jsonString)
            } catch (_: Exception) {
                List(10) { 0 }
            }
        } else {
            List(10) { 0 }
        }
    }

    val auditionSlotBBandsFlow: Flow<List<Int>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.AUDITION_SLOT_B_BANDS]
        if (jsonString != null) {
            try {
                json.decodeFromString<List<Int>>(jsonString)
            } catch (_: Exception) {
                List(10) { 0 }
            }
        } else {
            List(10) { 0 }
        }
    }

    val auditionActiveSlotFlow: Flow<AuditionSlot> = dataStore.data.map { preferences ->
        val str = preferences[Keys.AUDITION_ACTIVE_SLOT] ?: AuditionSlot.SLOT_A.name
        try {
            AuditionSlot.valueOf(str)
        } catch (_: Exception) {
            AuditionSlot.SLOT_A
        }
    }

    val auditionLoudnessCompFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AUDITION_LOUDNESS_COMP] ?: true
    }

    suspend fun setAutoPreampEnabled(enabled: Boolean) =
        dataStore.edit { preferences -> preferences[Keys.AUTO_PREAMP_ENABLED] = enabled }

    suspend fun setManualPreampDb(gainDb: Float) =
        dataStore.edit { preferences -> preferences[Keys.MANUAL_PREAMP_DB] = gainDb }

    suspend fun setDspEngineEnabled(enabled: Boolean) =
        dataStore.edit { preferences -> preferences[Keys.DSP_ENGINE_ENABLED] = enabled }

    suspend fun setParametricModeEnabled(enabled: Boolean) =
        dataStore.edit { preferences -> preferences[Keys.PARAMETRIC_MODE_ENABLED] = enabled }

    suspend fun setParametricBands(bands: List<ParametricBand>) =
        dataStore.edit { preferences -> preferences[Keys.PARAMETRIC_BANDS] = json.encodeToString(bands) }

    suspend fun setSubsonicFilter(enabled: Boolean, cutoffHz: Float) =
        dataStore.edit { preferences ->
            preferences[Keys.SUBSONIC_FILTER_ENABLED] = enabled
            preferences[Keys.SUBSONIC_CUTOFF_HZ] = cutoffHz
        }

    suspend fun setUltrasonicFilter(enabled: Boolean, cutoffHz: Float) =
        dataStore.edit { preferences ->
            preferences[Keys.ULTRASONIC_FILTER_ENABLED] = enabled
            preferences[Keys.ULTRASONIC_CUTOFF_HZ] = cutoffHz
        }

    suspend fun setCrossfeedStrength(strength: CrossfeedStrength) =
        dataStore.edit { preferences -> preferences[Keys.CROSSFEED_STRENGTH] = strength.ordinal }

    suspend fun setLimiterSettings(limiter: Boolean, softSaturation: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.LIMITER_ENABLED] = limiter
            preferences[Keys.SOFT_SATURATION_ENABLED] = softSaturation
        }

    suspend fun setAutoSwitchDeviceEnabled(enabled: Boolean) =
        dataStore.edit { preferences -> preferences[Keys.AUTO_SWITCH_DEVICE_ENABLED] = enabled }

    suspend fun setDevicePresetMapping(routingKey: String, presetName: String) =
        dataStore.edit { preferences ->
            val current = try {
                preferences[Keys.DEVICE_PRESET_MAP]?.let {
                    json.decodeFromString<Map<String, String>>(it)
                } ?: emptyMap()
            } catch (_: Exception) {
                emptyMap()
            }.toMutableMap()
            current[routingKey] = presetName
            preferences[Keys.DEVICE_PRESET_MAP] = json.encodeToString(current)
        }

    suspend fun setAuditionSlotABands(bands: List<Int>) =
        dataStore.edit { preferences -> preferences[Keys.AUDITION_SLOT_A_BANDS] = json.encodeToString(bands) }

    suspend fun setAuditionSlotBBands(bands: List<Int>) =
        dataStore.edit { preferences -> preferences[Keys.AUDITION_SLOT_B_BANDS] = json.encodeToString(bands) }

    suspend fun setAuditionActiveSlot(slot: AuditionSlot) =
        dataStore.edit { preferences -> preferences[Keys.AUDITION_ACTIVE_SLOT] = slot.name }

    suspend fun setAuditionLoudnessComp(enabled: Boolean) =
        dataStore.edit { preferences -> preferences[Keys.AUDITION_LOUDNESS_COMP] = enabled }
}
