package com.theveloper.pixelplay.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics
import com.theveloper.pixelplay.data.model.FolderSource
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.PlaybackQueueSnapshot
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.StorageFilter
import com.theveloper.pixelplay.data.model.TransitionSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemePreference {
    const val DEFAULT = "default"
    const val DYNAMIC = "dynamic"
    const val ALBUM_ART = "album_art"
    const val GLOBAL = "global"
}

object AppThemeMode {
    const val FOLLOW_SYSTEM = "follow_system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

const val MIN_NAV_BAR_CORNER_RADIUS = 0
const val MAX_NAV_BAR_CORNER_RADIUS = 60

internal fun sanitizeNavBarCornerRadius(radius: Int): Int =
    radius.coerceIn(MIN_NAV_BAR_CORNER_RADIUS, MAX_NAV_BAR_CORNER_RADIUS)

/**
 * Album art quality settings for developer options.
 * Controls maximum resolution for album artwork in player view.
 * Thumbnails in lists always use low resolution for performance.
 *
 * @property maxSize Maximum size in pixels (0 = original size)
 * @property label Human-readable label for UI
 */
enum class AlbumArtQuality(val maxSize: Int, val label: String) {
    LOW(256, "Low (256px) - Better performance"),
    MEDIUM(512, "Medium (512px) - Balanced"),
    HIGH(800, "High (800px) - Best quality"),
    ORIGINAL(0, "Original - Maximum quality")
}

data class AdvancedPerformanceDiagnosticsSettings(
    val enabled: Boolean,
    val sessionStartedEpochMs: Long?,
    val expiresAtEpochMs: Long?
) {
    fun isActive(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        enabled && expiresAtEpochMs?.let { nowEpochMs < it } == true
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    private val backupExcludedKeyNames = setOf(
        PreferencesKeys.Onboarding.INITIAL_SETUP_DONE.name
    )

    // ─── Preference keys ────────────────────────────────────────────────────
    // Keys are grouped by feature area; each group mirrors the section order of
    // the flows/setters below. Key name strings must never change (no migrations).

    private object PreferencesKeys {

        // ─── Onboarding & dialogs ───
        object Onboarding {
            val APP_REBRAND_DIALOG_SHOWN = booleanPreferencesKey("app_rebrand_dialog_shown")
            val BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED =
                booleanPreferencesKey("beta_05_clean_install_disclaimer_dismissed")
            val BACKUP_INFO_DISMISSED = booleanPreferencesKey("backup_info_dismissed")
            val INITIAL_SETUP_DONE = booleanPreferencesKey("initial_setup_done")
        }

        // ─── Playback ───
        object Playback {
            val REPEAT_MODE = intPreferencesKey("repeat_mode")
            val IS_SHUFFLE_ON = booleanPreferencesKey("is_shuffle_on")
            val PERSISTENT_SHUFFLE_ENABLED = booleanPreferencesKey("persistent_shuffle_enabled")
            val IS_CROSSFADE_ENABLED = booleanPreferencesKey("is_crossfade_enabled")
            val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")
            val HI_FI_MODE_ENABLED = booleanPreferencesKey("hi_fi_mode_enabled")
            val KEEP_PLAYING_IN_BACKGROUND = booleanPreferencesKey("keep_playing_in_background")
            val DISABLE_CAST_AUTOPLAY = booleanPreferencesKey("disable_cast_autoplay")
            val RESUME_ON_HEADSET_RECONNECT = booleanPreferencesKey("resume_on_headset_reconnect")
            val SHOW_QUEUE_HISTORY = booleanPreferencesKey("show_queue_history")
            val PLAYBACK_QUEUE_SNAPSHOT = stringPreferencesKey("playback_queue_snapshot_v1")
            val REPLAYGAIN_ENABLED = booleanPreferencesKey("replaygain_enabled")
            val REPLAYGAIN_USE_ALBUM_GAIN = booleanPreferencesKey("replaygain_use_album_gain")
            val PAUSE_ON_VOLUME_ZERO = booleanPreferencesKey("pause_on_volume_zero")
            val AMBIENT_SUGGESTIONS_ENABLED = booleanPreferencesKey("ambient_suggestions_enabled")
            val AMBIENT_DUCKING_FACTOR = floatPreferencesKey("ambient_ducking_factor")
            val AMBIENT_CHIME_ENABLED = booleanPreferencesKey("ambient_chime_enabled")
        }

        // ─── Full player loading tweaks ───
        object FullPlayer {
            val FULL_PLAYER_SHOW_FILE_INFO = booleanPreferencesKey("full_player_show_file_info")
            val FULL_PLAYER_DELAY_ALBUM = booleanPreferencesKey("full_player_delay_album")
            val FULL_PLAYER_DELAY_METADATA = booleanPreferencesKey("full_player_delay_metadata")
            val FULL_PLAYER_DELAY_PROGRESS = booleanPreferencesKey("full_player_delay_progress")
            val FULL_PLAYER_DELAY_CONTROLS = booleanPreferencesKey("full_player_delay_controls")
            val FULL_PLAYER_PLACEHOLDERS = booleanPreferencesKey("full_player_placeholders")
            val FULL_PLAYER_PLACEHOLDER_TRANSPARENT =
                booleanPreferencesKey("full_player_placeholder_transparent")
            val FULL_PLAYER_PLACEHOLDERS_ON_CLOSE =
                booleanPreferencesKey("full_player_placeholders_on_close")
            val FULL_PLAYER_SWITCH_ON_DRAG_RELEASE =
                booleanPreferencesKey("full_player_switch_on_drag_release")
            val FULL_PLAYER_DELAY_THRESHOLD = intPreferencesKey("full_player_delay_threshold_percent")
            val FULL_PLAYER_CLOSE_THRESHOLD = intPreferencesKey("full_player_close_threshold_percent")
            // Kept only for one-time cleanup after removing the legacy player sheet.
            val USE_PLAYER_SHEET_V2 = booleanPreferencesKey("use_player_sheet_v2")
        }

        // ─── Transitions ───
        object Transitions {
            val GLOBAL_TRANSITION_SETTINGS = stringPreferencesKey("global_transition_settings_json")
        }

        // ─── Favorites ───
        object Favorites {
            val FAVORITE_SONG_IDS = stringSetPreferencesKey("favorite_song_ids")
        }

        // ─── Playlists ───
        object Playlists {
            val PLAYLIST_SONG_ORDER_MODES = stringPreferencesKey("playlist_song_order_modes")
            val USER_PLAYLISTS = stringPreferencesKey("user_playlists_json_v1")
        }

        // ─── Directories ───
        object Directories {
            val ALLOWED_DIRECTORIES = stringSetPreferencesKey("allowed_directories")
            val BLOCKED_DIRECTORIES = stringSetPreferencesKey("blocked_directories")
        }

        // ─── Library sync ───
        object LibrarySync {
            val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
            val DIRECTORY_RULES_VERSION = intPreferencesKey("directory_rules_version")
            val LAST_APPLIED_DIRECTORY_RULES_VERSION =
                intPreferencesKey("last_applied_directory_rules_version")
        }

        // ─── Daily mix ───
        object DailyMix {
            val DAILY_MIX_SONG_IDS = stringPreferencesKey("daily_mix_song_ids")
            val YOUR_MIX_SONG_IDS = stringPreferencesKey("your_mix_song_ids")
            val LAST_DAILY_MIX_UPDATE = longPreferencesKey("last_daily_mix_update")
        }

        // ─── Filtering ───
        object Filtering {
            val MIN_SONG_DURATION = intPreferencesKey("min_song_duration_ms")
            val MIN_TRACKS_PER_ALBUM = intPreferencesKey("min_tracks_per_album")
        }

        // ─── Sort options ───
        object SortOptions {
            val SONGS_SORT_OPTION = stringPreferencesKey("songs_sort_option")
            val SONGS_SORT_OPTION_MIGRATED = booleanPreferencesKey("songs_sort_option_migrated_v2")
            val ALBUMS_SORT_OPTION = stringPreferencesKey("albums_sort_option")
            val ARTISTS_SORT_OPTION = stringPreferencesKey("artists_sort_option")
            val PLAYLISTS_SORT_OPTION = stringPreferencesKey("playlists_sort_option")
            val FOLDERS_SORT_OPTION = stringPreferencesKey("folders_sort_option")
            val LIKED_SONGS_SORT_OPTION = stringPreferencesKey("liked_songs_sort_option")
        }

        // ─── Library UI state ───
        object LibraryUi {
            val LAST_LIBRARY_TAB_INDEX = intPreferencesKey("last_library_tab_index")
            val LAST_STORAGE_FILTER = stringPreferencesKey("last_storage_filter")
            val MOCK_GENRES_ENABLED = booleanPreferencesKey("mock_genres_enabled")
            val LIBRARY_TABS_ORDER = stringPreferencesKey("library_tabs_order")
            val IS_FOLDER_FILTER_ACTIVE = booleanPreferencesKey("is_folder_filter_active")
            val IS_FOLDERS_PLAYLIST_VIEW = booleanPreferencesKey("is_folders_playlist_view")
            val SHOW_TELEGRAM_CLOUD_PLAYLISTS = booleanPreferencesKey("show_telegram_cloud_playlists")
            val HIDE_LOCAL_MEDIA = booleanPreferencesKey("hide_local_media")
            val TELEGRAM_TOPIC_DISPLAY_MODE = stringPreferencesKey("telegram_topic_display_mode")
            val FOLDERS_SOURCE = stringPreferencesKey("folders_source")
            val FOLDER_BACK_GESTURE_NAVIGATION = booleanPreferencesKey("folder_back_gesture_navigation")
            val IS_GENRE_GRID_VIEW = booleanPreferencesKey("is_genre_grid_view")
            val IS_ALBUMS_LIST_VIEW = booleanPreferencesKey("is_albums_list_view")
        }

        // ─── Navigation bar & appearance ───
        object Navigation {
            val NAV_BAR_CORNER_RADIUS = intPreferencesKey("nav_bar_corner_radius")
            val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
            val NAV_BAR_COMPACT_MODE = booleanPreferencesKey("nav_bar_compact_mode")
            val LIBRARY_NAVIGATION_MODE = stringPreferencesKey("library_navigation_mode")
            val CAROUSEL_STYLE = stringPreferencesKey("carousel_style")
            val LAUNCH_TAB = stringPreferencesKey("launch_tab")
            val USE_SMOOTH_CORNERS = booleanPreferencesKey("use_smooth_corners")
            val MINI_PLAYER_SONG_TRANSITION = booleanPreferencesKey("mini_player_song_transition")
            val SHOW_SCROLLBAR = booleanPreferencesKey("show_scrollbar")
        }

        // ─── Cutout Overlay ───
        object CutoutOverlay {
            val ENABLE_CUTOUT_OVERLAY = booleanPreferencesKey("enable_cutout_overlay")
        }

        // ─── Offline & network policy ───
        object OfflineNetwork {
            val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
            val LYRICS_INTEGRATION_ENABLED = booleanPreferencesKey("lyrics_integration_enabled")
        }

        // ─── Metadata enrichment ───
        object Enrichment {
            val ENRICHMENT_ENABLED = booleanPreferencesKey("enrichment_enabled")
            val LASTFM_API_KEY = stringPreferencesKey("lastfm_api_key")
            val LISTENBRAINZ_TOKEN = stringPreferencesKey("listenbrainz_token")
            val LISTENBRAINZ_PENDING = stringPreferencesKey("listenbrainz_pending_listens")
            val ENRICHMENT_WIFI_ONLY = booleanPreferencesKey("enrichment_wifi_only")
            val AUTO_ENRICH_AFTER_SYNC = booleanPreferencesKey("auto_enrich_after_sync")
            val SPOTIFY_CLIENT_ID = stringPreferencesKey("spotify_client_id")
            val SPOTIFY_CLIENT_SECRET = stringPreferencesKey("spotify_client_secret")
            val SPOTIFY_ACCESS_TOKEN = stringPreferencesKey("spotify_access_token")
            val SPOTIFY_REFRESH_TOKEN = stringPreferencesKey("spotify_refresh_token")
            val SPOTIFY_TOKEN_EXPIRES_AT = longPreferencesKey("spotify_token_expires_at")
            val YTMUSIC_SAPISID = stringPreferencesKey("ytmusic_sapisid")
            val YTMUSIC_SID = stringPreferencesKey("ytmusic_sid")
            val YTMUSIC_FULL_COOKIE = stringPreferencesKey("ytmusic_full_cookie")
        }

        // ─── Multi-artist ───
        object MultiArtist {
            val ARTIST_DELIMITERS = stringPreferencesKey("artist_delimiters")
            val ARTIST_WORD_DELIMITERS = stringPreferencesKey("artist_word_delimiters")
            val EXTRACT_ARTISTS_FROM_TITLE = booleanPreferencesKey("extract_artists_from_title")
            val GROUP_BY_ALBUM_ARTIST = booleanPreferencesKey("group_by_album_artist")
            val ARTIST_SETTINGS_RESCAN_REQUIRED =
                booleanPreferencesKey("artist_settings_rescan_required")
        }

        // ─── Lyrics ───
        object Lyrics {
            val LYRICS_SYNC_OFFSETS = stringPreferencesKey("lyrics_sync_offsets_json")
            val LYRICS_SOURCE_PREFERENCE = stringPreferencesKey("lyrics_source_preference")
            val AUTO_SCAN_LRC_FILES = booleanPreferencesKey("auto_scan_lrc_files")
            val IMMERSIVE_LYRICS_ENABLED = booleanPreferencesKey("immersive_lyrics_enabled")
            val IMMERSIVE_LYRICS_TIMEOUT = longPreferencesKey("immersive_lyrics_timeout")
            val USE_ANIMATED_LYRICS = booleanPreferencesKey("use_animated_lyrics")
            val ANIMATED_LYRICS_BLUR_ENABLED = booleanPreferencesKey("animated_lyrics_blur_enabled")
            val ANIMATED_LYRICS_BLUR_STRENGTH = floatPreferencesKey("animated_lyrics_blur_strength")
            val COVER_LYRICS_ENABLED = booleanPreferencesKey("cover_lyrics_enabled")
        }

        // ─── Custom genres ───
        object CustomGenres {
            val CUSTOM_GENRES = stringSetPreferencesKey("custom_genres")
            val CUSTOM_GENRE_ICONS = stringPreferencesKey("custom_genre_icons")
        }

        // ─── Collage ───
        object Collage {
            val COLLAGE_PATTERN = stringPreferencesKey("collage_pattern")
            val COLLAGE_AUTO_ROTATE = booleanPreferencesKey("collage_auto_rotate")
        }

        // ─── Quick settings / last playlist ───
        object QuickSettings {
            val LAST_PLAYLIST_ID = stringPreferencesKey("last_playlist_id")
            val LAST_PLAYLIST_NAME = stringPreferencesKey("last_playlist_name")
        }

        // ─── Developer options ───
        object Developer {
            val ALBUM_ART_QUALITY = stringPreferencesKey("album_art_quality")
            val ALBUM_ART_CACHE_LIMIT_MB = intPreferencesKey("album_art_cache_limit_mb")
            val TAP_BACKGROUND_CLOSES_PLAYER = booleanPreferencesKey("tap_background_closes_player")
            val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
            val DISABLE_BLUR_ALL_OVER = booleanPreferencesKey("disable_blur_all_over")
            val ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED =
                booleanPreferencesKey("advanced_performance_diagnostics_enabled")
            val ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT =
                longPreferencesKey("advanced_performance_diagnostics_started_at_epoch_ms")
            val ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT =
                longPreferencesKey("advanced_performance_diagnostics_expires_at_epoch_ms")
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /** Shorthand to map a single value out of the DataStore. */
    private fun <T> pref(transform: (Preferences) -> T): Flow<T> =
        dataStore.data.map(transform)

    /** Decode a JSON string preference, returning [default] on missing or malformed data. */
    private inline fun <reified T> decodeJsonPref(
        preferences: Preferences,
        key: Preferences.Key<String>,
        default: T
    ): T = preferences[key]
        ?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
        ?: default

    /** Read the current JSON map stored at [key], apply [block], and persist the result. */
    private suspend inline fun <reified V> editJsonMap(
        key: Preferences.Key<String>,
        crossinline block: MutableMap<String, V>.() -> Unit
    ) {
        dataStore.edit { preferences ->
            val current = decodeJsonPref(preferences, key, emptyMap<String, V>()).toMutableMap()
            current.block()
            preferences[key] = json.encodeToString(current)
        }
    }

    // ─── Onboarding & dialogs ─────────────────────────────────────────────────

    val appRebrandDialogShownFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Onboarding.APP_REBRAND_DIALOG_SHOWN] ?: false }

    suspend fun setAppRebrandDialogShown(wasShown: Boolean) {
        dataStore.edit { it[PreferencesKeys.Onboarding.APP_REBRAND_DIALOG_SHOWN] = wasShown }
    }

    val enableCutoutOverlayFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.CutoutOverlay.ENABLE_CUTOUT_OVERLAY] ?: false }

    suspend fun setEnableCutoutOverlay(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CutoutOverlay.ENABLE_CUTOUT_OVERLAY] = enabled }
    }

    val beta05CleanInstallDisclaimerDismissedFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Onboarding.BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED] ?: false }

    suspend fun setBeta05CleanInstallDisclaimerDismissed(dismissed: Boolean) {
        dataStore.edit { it[PreferencesKeys.Onboarding.BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED] = dismissed }
    }

    val backupInfoDismissedFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Onboarding.BACKUP_INFO_DISMISSED] ?: false }

    suspend fun setBackupInfoDismissed(dismissed: Boolean) {
        dataStore.edit { it[PreferencesKeys.Onboarding.BACKUP_INFO_DISMISSED] = dismissed }
    }

    val initialSetupDoneFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Onboarding.INITIAL_SETUP_DONE] ?: false }

    suspend fun setInitialSetupDone(isDone: Boolean) {
        dataStore.edit { it[PreferencesKeys.Onboarding.INITIAL_SETUP_DONE] = isDone }
    }

    // ─── Playback ─────────────────────────────────────────────────────────────

    val repeatModeFlow: Flow<Int> =
        pref { it[PreferencesKeys.Playback.REPEAT_MODE] ?: Player.REPEAT_MODE_OFF }

    suspend fun setRepeatMode(@Player.RepeatMode mode: Int) {
        dataStore.edit { it[PreferencesKeys.Playback.REPEAT_MODE] = mode }
    }

    val isShuffleOnFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.IS_SHUFFLE_ON] ?: false }

    suspend fun setShuffleOn(on: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.IS_SHUFFLE_ON] = on }
    }

    val persistentShuffleEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.PERSISTENT_SHUFFLE_ENABLED] ?: false }

    suspend fun setPersistentShuffleEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.PERSISTENT_SHUFFLE_ENABLED] = enabled }
    }

    val isCrossfadeEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.IS_CROSSFADE_ENABLED] ?: false }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.IS_CROSSFADE_ENABLED] = enabled }
    }

    val crossfadeDurationFlow: Flow<Int> =
        pref { (it[PreferencesKeys.Playback.CROSSFADE_DURATION] ?: 2000).coerceIn(1000, 12000) }

    suspend fun setCrossfadeDuration(duration: Int) {
        dataStore.edit { it[PreferencesKeys.Playback.CROSSFADE_DURATION] = duration.coerceIn(1000, 12000) }
    }

    val hiFiModeEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.HI_FI_MODE_ENABLED] ?: false }

    suspend fun setHiFiModeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.HI_FI_MODE_ENABLED] = enabled }
    }

    val keepPlayingInBackgroundFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.KEEP_PLAYING_IN_BACKGROUND] ?: true }

    suspend fun setKeepPlayingInBackground(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.KEEP_PLAYING_IN_BACKGROUND] = enabled }
    }

    val disableCastAutoplayFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.DISABLE_CAST_AUTOPLAY] ?: false }

    suspend fun setDisableCastAutoplay(disabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.DISABLE_CAST_AUTOPLAY] = disabled }
    }

    val resumeOnHeadsetReconnectFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.RESUME_ON_HEADSET_RECONNECT] ?: false }

    suspend fun setResumeOnHeadsetReconnect(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.RESUME_ON_HEADSET_RECONNECT] = enabled }
    }

    val showQueueHistoryFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.SHOW_QUEUE_HISTORY] ?: false }

    suspend fun setShowQueueHistory(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.SHOW_QUEUE_HISTORY] = show }
    }

    val playbackQueueSnapshotFlow: Flow<PlaybackQueueSnapshot?> =
        pref { preferences ->
            preferences[PreferencesKeys.Playback.PLAYBACK_QUEUE_SNAPSHOT]?.let { raw ->
                runCatching { json.decodeFromString<PlaybackQueueSnapshot>(raw) }.getOrNull()
            }
        }

    suspend fun getPlaybackQueueSnapshotOnce(): PlaybackQueueSnapshot? =
        playbackQueueSnapshotFlow.first()

    suspend fun setPlaybackQueueSnapshot(snapshot: PlaybackQueueSnapshot?) {
        dataStore.edit { preferences ->
            if (snapshot == null || snapshot.items.isEmpty()) {
                preferences.remove(PreferencesKeys.Playback.PLAYBACK_QUEUE_SNAPSHOT)
            } else {
                preferences[PreferencesKeys.Playback.PLAYBACK_QUEUE_SNAPSHOT] = json.encodeToString(snapshot)
            }
        }
    }

    val replayGainEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.REPLAYGAIN_ENABLED] ?: false }

    val replayGainUseAlbumGainFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.REPLAYGAIN_USE_ALBUM_GAIN] ?: false }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.REPLAYGAIN_ENABLED] = enabled }
    }

    suspend fun setReplayGainUseAlbumGain(useAlbumGain: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.REPLAYGAIN_USE_ALBUM_GAIN] = useAlbumGain }
    }

    val pauseOnVolumeZeroFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.PAUSE_ON_VOLUME_ZERO] ?: false }

    suspend fun setPauseOnVolumeZero(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.PAUSE_ON_VOLUME_ZERO] = enabled }
    }

    val ambientSuggestionsEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.AMBIENT_SUGGESTIONS_ENABLED] ?: false }

    suspend fun setAmbientSuggestionsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.AMBIENT_SUGGESTIONS_ENABLED] = enabled }
    }

    val ambientDuckingFactorFlow: Flow<Float> =
        pref { it[PreferencesKeys.Playback.AMBIENT_DUCKING_FACTOR] ?: 0.15f }

    suspend fun setAmbientDuckingFactor(factor: Float) {
        dataStore.edit { it[PreferencesKeys.Playback.AMBIENT_DUCKING_FACTOR] = factor.coerceIn(0.05f, 0.5f) }
    }

    val ambientAudioChimeEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Playback.AMBIENT_CHIME_ENABLED] ?: true }

    suspend fun setAmbientAudioChimeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Playback.AMBIENT_CHIME_ENABLED] = enabled }
    }

    // ─── Full player loading tweaks ───────────────────────────────────────────

    val showPlayerFileInfoFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.FullPlayer.FULL_PLAYER_SHOW_FILE_INFO] ?: true }

    suspend fun setShowPlayerFileInfo(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.FullPlayer.FULL_PLAYER_SHOW_FILE_INFO] = show }
    }

    val fullPlayerLoadingTweaksFlow: Flow<FullPlayerLoadingTweaks> =
        pref { preferences ->
            val delayAlbum = preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_ALBUM] ?: true
            val delayMetadata = preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_METADATA] ?: true
            val delayProgress = preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_PROGRESS] ?: true
            val delayControls = preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_CONTROLS] ?: true
            FullPlayerLoadingTweaks(
                delayAll = delayAlbum && delayMetadata && delayProgress && delayControls,
                delayAlbumCarousel = delayAlbum,
                delaySongMetadata = delayMetadata,
                delayProgressBar = delayProgress,
                delayControls = delayControls,
                showPlaceholders = preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_PLACEHOLDERS] ?: true,
                transparentPlaceholders =
                    preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] ?: false,
                applyPlaceholdersOnClose =
                    preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_PLACEHOLDERS_ON_CLOSE] ?: false,
                switchOnDragRelease =
                    preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_SWITCH_ON_DRAG_RELEASE] ?: true,
                contentAppearThresholdPercent =
                    preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_THRESHOLD] ?: 98,
                contentCloseThresholdPercent =
                    preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_CLOSE_THRESHOLD] ?: 0
            )
        }

    /** Sets all four delay flags to [enabled] atomically. */
    suspend fun setDelayAllFullPlayerContent(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_ALBUM] = enabled
            preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_METADATA] = enabled
            preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_PROGRESS] = enabled
            preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_CONTROLS] = enabled
        }
    }

    suspend fun setDelayAlbumCarousel(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_ALBUM] = enabled }
    }

    suspend fun setDelaySongMetadata(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_METADATA] = enabled }
    }

    suspend fun setDelayProgressBar(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_PROGRESS] = enabled }
    }

    suspend fun setDelayControls(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_CONTROLS] = enabled }
    }

    suspend fun setFullPlayerPlaceholders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_PLACEHOLDERS] = enabled
            if (!enabled) preferences[PreferencesKeys.FullPlayer.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = false
        }
    }

    suspend fun setTransparentPlaceholders(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FullPlayer.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = enabled }
    }

    suspend fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FullPlayer.FULL_PLAYER_PLACEHOLDERS_ON_CLOSE] = enabled }
    }

    suspend fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FullPlayer.FULL_PLAYER_SWITCH_ON_DRAG_RELEASE] = enabled }
    }

    suspend fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        dataStore.edit {
            it[PreferencesKeys.FullPlayer.FULL_PLAYER_DELAY_THRESHOLD] = thresholdPercent.coerceIn(0, 100)
        }
    }

    suspend fun setFullPlayerCloseThreshold(thresholdPercent: Int) {
        dataStore.edit {
            it[PreferencesKeys.FullPlayer.FULL_PLAYER_CLOSE_THRESHOLD] = thresholdPercent.coerceIn(0, 100)
        }
    }

    /** Removes the deprecated player sheet V2 preference key. */
    suspend fun clearDeprecatedPlayerSheetPreference() {
        dataStore.edit { it.remove(PreferencesKeys.FullPlayer.USE_PLAYER_SHEET_V2) }
    }

    // ─── Transitions ──────────────────────────────────────────────────────────

    val globalTransitionSettingsFlow: Flow<TransitionSettings> =
        pref { preferences ->
            val duration = (preferences[PreferencesKeys.Playback.CROSSFADE_DURATION] ?: 2000).coerceIn(1000, 12000)
            val settings = decodeJsonPref(preferences, PreferencesKeys.Transitions.GLOBAL_TRANSITION_SETTINGS, TransitionSettings())
            settings.copy(durationMs = duration)
        }

    suspend fun saveGlobalTransitionSettings(settings: TransitionSettings) {
        dataStore.edit { it[PreferencesKeys.Transitions.GLOBAL_TRANSITION_SETTINGS] = json.encodeToString(settings) }
    }

    // ─── Favorites ────────────────────────────────────────────────────────────

    val favoriteSongIdsFlow: Flow<Set<String>> =
        pref { it[PreferencesKeys.Favorites.FAVORITE_SONG_IDS] ?: emptySet() }

    /**
     * Adds or removes [songId] from favorites depending on [isFavorite].
     * Prefer this over [toggleFavoriteSong] when the desired state is known.
     */
    suspend fun setFavoriteSong(songId: String, isFavorite: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.Favorites.FAVORITE_SONG_IDS] ?: emptySet()
            preferences[PreferencesKeys.Favorites.FAVORITE_SONG_IDS] =
                if (isFavorite) current + songId else current - songId
        }
    }

    /** Toggles [songId] in the favorites set. */
    suspend fun toggleFavoriteSong(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.Favorites.FAVORITE_SONG_IDS] ?: emptySet()
            preferences[PreferencesKeys.Favorites.FAVORITE_SONG_IDS] =
                if (songId in current) current - songId else current + songId
        }
    }

    suspend fun clearFavoriteSongIds() {
        dataStore.edit { it[PreferencesKeys.Favorites.FAVORITE_SONG_IDS] = emptySet() }
    }

    // ─── Playlists ────────────────────────────────────────────────────────────

    val playlistSongOrderModesFlow: Flow<Map<String, String>> =
        pref { preferences ->
            decodeJsonPref(preferences, PreferencesKeys.Playlists.PLAYLIST_SONG_ORDER_MODES, emptyMap())
        }

    suspend fun setPlaylistSongOrderMode(playlistId: String, modeValue: String) {
        editJsonMap<String>(PreferencesKeys.Playlists.PLAYLIST_SONG_ORDER_MODES) { put(playlistId, modeValue) }
    }

    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) {
        dataStore.edit { preferences ->
            if (modes.isEmpty()) {
                preferences.remove(PreferencesKeys.Playlists.PLAYLIST_SONG_ORDER_MODES)
            } else {
                preferences[PreferencesKeys.Playlists.PLAYLIST_SONG_ORDER_MODES] = json.encodeToString(modes)
            }
        }
    }

    suspend fun clearPlaylistSongOrderMode(playlistId: String) {
        editJsonMap<String>(PreferencesKeys.Playlists.PLAYLIST_SONG_ORDER_MODES) { remove(playlistId) }
    }

    // Legacy DataStore playlist payload kept only for one-time migration and old backup compatibility.
    val legacyUserPlaylistsFlow: Flow<List<Playlist>> =
        pref { preferences ->
            decodeJsonPref(preferences, PreferencesKeys.Playlists.USER_PLAYLISTS, emptyList())
        }

    suspend fun getLegacyUserPlaylistsOnce(): List<Playlist> = legacyUserPlaylistsFlow.first()

    suspend fun clearLegacyUserPlaylists() {
        dataStore.edit { it.remove(PreferencesKeys.Playlists.USER_PLAYLISTS) }
    }

    // ─── Directories ──────────────────────────────────────────────────────────

    val allowedDirectoriesFlow: Flow<Set<String>> =
        pref { it[PreferencesKeys.Directories.ALLOWED_DIRECTORIES] ?: emptySet() }.distinctUntilChanged()

    val blockedDirectoriesFlow: Flow<Set<String>> =
        pref { it[PreferencesKeys.Directories.BLOCKED_DIRECTORIES] ?: emptySet() }.distinctUntilChanged()

    suspend fun updateAllowedDirectories(allowedPaths: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Directories.ALLOWED_DIRECTORIES] = allowedPaths
            preferences[PreferencesKeys.LibrarySync.LAST_SYNC_TIMESTAMP] = 0L
            preferences[PreferencesKeys.LibrarySync.DIRECTORY_RULES_VERSION] =
                incrementWrapped(preferences[PreferencesKeys.LibrarySync.DIRECTORY_RULES_VERSION])
        }
    }

    suspend fun updateDirectorySelections(allowedPaths: Set<String>, blockedPaths: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Directories.ALLOWED_DIRECTORIES] = allowedPaths
            preferences[PreferencesKeys.Directories.BLOCKED_DIRECTORIES] = blockedPaths
            preferences[PreferencesKeys.LibrarySync.LAST_SYNC_TIMESTAMP] = 0L
            preferences[PreferencesKeys.LibrarySync.DIRECTORY_RULES_VERSION] =
                incrementWrapped(preferences[PreferencesKeys.LibrarySync.DIRECTORY_RULES_VERSION])
        }
    }

    // ─── Library sync ─────────────────────────────────────────────────────────

    val lastSyncTimestampFlow: Flow<Long> =
        pref { it[PreferencesKeys.LibrarySync.LAST_SYNC_TIMESTAMP] ?: 0L }

    val directoryRulesVersionFlow: Flow<Int> =
        pref { it[PreferencesKeys.LibrarySync.DIRECTORY_RULES_VERSION] ?: 0 }

    val lastAppliedDirectoryRulesVersionFlow: Flow<Int> =
        pref { it[PreferencesKeys.LibrarySync.LAST_APPLIED_DIRECTORY_RULES_VERSION] ?: 0 }

    suspend fun getLastSyncTimestamp(): Long = lastSyncTimestampFlow.first()
    suspend fun getDirectoryRulesVersion(): Int = directoryRulesVersionFlow.first()
    suspend fun getLastAppliedDirectoryRulesVersion(): Int =
        lastAppliedDirectoryRulesVersionFlow.first()

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        dataStore.edit { it[PreferencesKeys.LibrarySync.LAST_SYNC_TIMESTAMP] = timestamp }
    }

    suspend fun markDirectoryRulesVersionApplied(version: Int) {
        dataStore.edit { it[PreferencesKeys.LibrarySync.LAST_APPLIED_DIRECTORY_RULES_VERSION] = version }
    }

    // ─── Daily mix ────────────────────────────────────────────────────────────

    val dailyMixSongIdsFlow: Flow<List<String>> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.DailyMix.DAILY_MIX_SONG_IDS]
            if (jsonString != null) {
                try {
                    json.decodeFromString<List<String>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    suspend fun saveDailyMixSongIds(songIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DailyMix.DAILY_MIX_SONG_IDS] = json.encodeToString(songIds)
        }
    }

    val yourMixSongIdsFlow: Flow<List<String>> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.DailyMix.YOUR_MIX_SONG_IDS]
            if (jsonString != null) {
                try {
                    json.decodeFromString<List<String>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    suspend fun saveYourMixSongIds(songIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DailyMix.YOUR_MIX_SONG_IDS] = json.encodeToString(songIds)
        }
    }

    val lastDailyMixUpdateFlow: Flow<Long> =
        pref { it[PreferencesKeys.DailyMix.LAST_DAILY_MIX_UPDATE] ?: 0L }

    suspend fun saveLastDailyMixUpdateTimestamp(timestamp: Long) {
        dataStore.edit { it[PreferencesKeys.DailyMix.LAST_DAILY_MIX_UPDATE] = timestamp }
    }

    // ─── Filtering ────────────────────────────────────────────────────────────

    val minSongDurationFlow: Flow<Int> =
        pref { (it[PreferencesKeys.Filtering.MIN_SONG_DURATION] ?: 10000).coerceIn(0, 120000) }

    suspend fun setMinSongDuration(durationMs: Int) {
        dataStore.edit { it[PreferencesKeys.Filtering.MIN_SONG_DURATION] = durationMs.coerceIn(0, 120000) }
    }

    suspend fun getMinSongDuration(): Int {
        return minSongDurationFlow.first()
    }

    val minTracksPerAlbumFlow: Flow<Int> =
        pref { it[PreferencesKeys.Filtering.MIN_TRACKS_PER_ALBUM] ?: 1 }

    suspend fun setMinTracksPerAlbum(minTracks: Int) {
        dataStore.edit { it[PreferencesKeys.Filtering.MIN_TRACKS_PER_ALBUM] = minTracks }
    }

    // ─── Sort options ─────────────────────────────────────────────────────────

    val songsSortOptionFlow: Flow<String> =
        pref { SortOption.fromStorageKey(it[PreferencesKeys.SortOptions.SONGS_SORT_OPTION], SortOption.SONGS, SortOption.SongTitleAZ).storageKey }

    val albumsSortOptionFlow: Flow<String> =
        pref { SortOption.fromStorageKey(it[PreferencesKeys.SortOptions.ALBUMS_SORT_OPTION], SortOption.ALBUMS, SortOption.AlbumTitleAZ).storageKey }

    val artistsSortOptionFlow: Flow<String> =
        pref { SortOption.fromStorageKey(it[PreferencesKeys.SortOptions.ARTISTS_SORT_OPTION], SortOption.ARTISTS, SortOption.ArtistNameAZ).storageKey }

    val playlistsSortOptionFlow: Flow<String> =
        pref { SortOption.fromStorageKey(it[PreferencesKeys.SortOptions.PLAYLISTS_SORT_OPTION], SortOption.PLAYLISTS, SortOption.PlaylistNameAZ).storageKey }

    val foldersSortOptionFlow: Flow<String> =
        pref { SortOption.fromStorageKey(it[PreferencesKeys.SortOptions.FOLDERS_SORT_OPTION], SortOption.FOLDERS, SortOption.FolderNameAZ).storageKey }

    val likedSongsSortOptionFlow: Flow<String> =
        pref { SortOption.fromStorageKey(it[PreferencesKeys.SortOptions.LIKED_SONGS_SORT_OPTION], SortOption.LIKED, SortOption.LikedSongDateLiked).storageKey }

    suspend fun setSongsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SortOptions.SONGS_SORT_OPTION] = optionKey
            preferences[PreferencesKeys.SortOptions.SONGS_SORT_OPTION_MIGRATED] = true
        }
    }

    suspend fun setAlbumsSortOption(optionKey: String) {
        dataStore.edit { it[PreferencesKeys.SortOptions.ALBUMS_SORT_OPTION] = optionKey }
    }

    suspend fun setArtistsSortOption(optionKey: String) {
        dataStore.edit { it[PreferencesKeys.SortOptions.ARTISTS_SORT_OPTION] = optionKey }
    }

    suspend fun setPlaylistsSortOption(optionKey: String) {
        dataStore.edit { it[PreferencesKeys.SortOptions.PLAYLISTS_SORT_OPTION] = optionKey }
    }

    suspend fun setFoldersSortOption(optionKey: String) {
        dataStore.edit { it[PreferencesKeys.SortOptions.FOLDERS_SORT_OPTION] = optionKey }
    }

    suspend fun setLikedSongsSortOption(optionKey: String) {
        dataStore.edit { it[PreferencesKeys.SortOptions.LIKED_SONGS_SORT_OPTION] = optionKey }
    }

    suspend fun ensureLibrarySortDefaults() {
        dataStore.edit { preferences ->
            val songsMigrated = preferences[PreferencesKeys.SortOptions.SONGS_SORT_OPTION_MIGRATED] ?: false
            val rawSongSort = preferences[PreferencesKeys.SortOptions.SONGS_SORT_OPTION]
            val shouldForceSongDefault = !songsMigrated &&
                (rawSongSort.isNullOrBlank() ||
                    rawSongSort == SortOption.SongTitleZA.storageKey ||
                    rawSongSort == SortOption.SongTitleZA.displayName)

            preferences[PreferencesKeys.SortOptions.SONGS_SORT_OPTION] =
                if (shouldForceSongDefault) SortOption.SongTitleAZ.storageKey
                else SortOption.fromStorageKey(rawSongSort, SortOption.SONGS, SortOption.SongTitleAZ).storageKey

            if (!songsMigrated) preferences[PreferencesKeys.SortOptions.SONGS_SORT_OPTION_MIGRATED] = true

            migrateSortPreference(preferences, PreferencesKeys.SortOptions.SONGS_SORT_OPTION, SortOption.SONGS, SortOption.SongTitleAZ)
            migrateSortPreference(preferences, PreferencesKeys.SortOptions.ALBUMS_SORT_OPTION, SortOption.ALBUMS, SortOption.AlbumTitleAZ)
            migrateSortPreference(preferences, PreferencesKeys.SortOptions.ARTISTS_SORT_OPTION, SortOption.ARTISTS, SortOption.ArtistNameAZ)
            migrateSortPreference(preferences, PreferencesKeys.SortOptions.PLAYLISTS_SORT_OPTION, SortOption.PLAYLISTS, SortOption.PlaylistNameAZ)
            migrateSortPreference(preferences, PreferencesKeys.SortOptions.FOLDERS_SORT_OPTION, SortOption.FOLDERS, SortOption.FolderNameAZ)
            migrateSortPreference(preferences, PreferencesKeys.SortOptions.LIKED_SONGS_SORT_OPTION, SortOption.LIKED, SortOption.LikedSongDateLiked)
        }
    }

    private fun migrateSortPreference(
        preferences: MutablePreferences,
        key: Preferences.Key<String>,
        allowed: Collection<SortOption>,
        fallback: SortOption
    ) {
        val resolved = SortOption.fromStorageKey(preferences[key], allowed, fallback)
        if (preferences[key] != resolved.storageKey) preferences[key] = resolved.storageKey
    }

    // ─── Library UI state ─────────────────────────────────────────────────────

    val lastLibraryTabIndexFlow: Flow<Int> =
        pref { it[PreferencesKeys.LibraryUi.LAST_LIBRARY_TAB_INDEX] ?: 0 }

    suspend fun saveLastLibraryTabIndex(tabIndex: Int) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.LAST_LIBRARY_TAB_INDEX] = tabIndex }
    }

    val lastStorageFilterFlow: Flow<StorageFilter> =
        pref { preferences ->
            when (preferences[PreferencesKeys.LibraryUi.LAST_STORAGE_FILTER]) {
                "ONLINE"  -> StorageFilter.ONLINE
                "OFFLINE" -> StorageFilter.OFFLINE
                else      -> StorageFilter.ALL
            }
        }

    suspend fun saveLastStorageFilter(filter: StorageFilter) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.LAST_STORAGE_FILTER] = filter.name }
    }

    val mockGenresEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.LibraryUi.MOCK_GENRES_ENABLED] ?: false }

    suspend fun setMockGenresEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.MOCK_GENRES_ENABLED] = enabled }
    }

    val libraryTabsOrderFlow: Flow<String?> =
        pref { it[PreferencesKeys.LibraryUi.LIBRARY_TABS_ORDER] }

    suspend fun saveLibraryTabsOrder(order: String) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.LIBRARY_TABS_ORDER] = order }
    }

    suspend fun resetLibraryTabsOrder() {
        dataStore.edit { it.remove(PreferencesKeys.LibraryUi.LIBRARY_TABS_ORDER) }
    }

    suspend fun migrateTabOrder() {
        dataStore.edit { preferences ->
            val orderJson = preferences[PreferencesKeys.LibraryUi.LIBRARY_TABS_ORDER] ?: return@edit
            val order = runCatching {
                json.decodeFromString<MutableList<String>>(orderJson)
            }.getOrNull() ?: return@edit  // Abort on malformed data; don't overwrite user data.

            if ("FOLDERS" !in order) {
                val insertAfter = order.indexOf("LIKED").takeIf { it != -1 } ?: order.lastIndex
                order.add(insertAfter + 1, "FOLDERS")
                preferences[PreferencesKeys.LibraryUi.LIBRARY_TABS_ORDER] = json.encodeToString(order)
            }
        }
    }

    val isFolderFilterActiveFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.LibraryUi.IS_FOLDER_FILTER_ACTIVE] ?: false }

    suspend fun setFolderFilterActive(isActive: Boolean) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.IS_FOLDER_FILTER_ACTIVE] = isActive }
    }

    val isFoldersPlaylistViewFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.LibraryUi.IS_FOLDERS_PLAYLIST_VIEW] ?: false }

    suspend fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.IS_FOLDERS_PLAYLIST_VIEW] = isPlaylistView }
    }

    val showTelegramCloudPlaylistsFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.LibraryUi.SHOW_TELEGRAM_CLOUD_PLAYLISTS] ?: true }

    suspend fun setShowTelegramCloudPlaylists(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.SHOW_TELEGRAM_CLOUD_PLAYLISTS] = show }
    }

    val hideLocalMediaFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.LibraryUi.HIDE_LOCAL_MEDIA] ?: false }.distinctUntilChanged()

    suspend fun setHideLocalMedia(hide: Boolean) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.HIDE_LOCAL_MEDIA] = hide }
    }

    val telegramTopicDisplayModeFlow: Flow<TelegramTopicDisplayMode> =
        pref { TelegramTopicDisplayMode.fromStorageKey(it[PreferencesKeys.LibraryUi.TELEGRAM_TOPIC_DISPLAY_MODE]) }

    suspend fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.TELEGRAM_TOPIC_DISPLAY_MODE] = mode.storageKey }
    }

    val foldersSourceFlow: Flow<FolderSource> =
        pref { FolderSource.fromStorageKey(it[PreferencesKeys.LibraryUi.FOLDERS_SOURCE]) }

    suspend fun setFoldersSource(source: FolderSource) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.FOLDERS_SOURCE] = source.storageKey }
    }

    val folderBackGestureNavigationFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.LibraryUi.FOLDER_BACK_GESTURE_NAVIGATION] ?: true }

    suspend fun setFolderBackGestureNavigation(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.FOLDER_BACK_GESTURE_NAVIGATION] = enabled }
    }

    val isGenreGridViewFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.LibraryUi.IS_GENRE_GRID_VIEW] ?: true }

    suspend fun setGenreGridView(isGrid: Boolean) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.IS_GENRE_GRID_VIEW] = isGrid }
    }

    val isAlbumsListViewFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.LibraryUi.IS_ALBUMS_LIST_VIEW] ?: false }

    suspend fun setAlbumsListView(isList: Boolean) {
        dataStore.edit { it[PreferencesKeys.LibraryUi.IS_ALBUMS_LIST_VIEW] = isList }
    }

    // ─── Navigation bar & appearance ──────────────────────────────────────────

    val navBarCornerRadiusFlow: Flow<Int> =
        pref { sanitizeNavBarCornerRadius(it[PreferencesKeys.Navigation.NAV_BAR_CORNER_RADIUS] ?: 32) }

    suspend fun setNavBarCornerRadius(radius: Int) {
        dataStore.edit { it[PreferencesKeys.Navigation.NAV_BAR_CORNER_RADIUS] = sanitizeNavBarCornerRadius(radius) }
    }

    val navBarStyleFlow: Flow<String> =
        pref { it[PreferencesKeys.Navigation.NAV_BAR_STYLE] ?: NavBarStyle.DEFAULT }

    suspend fun setNavBarStyle(style: String) {
        dataStore.edit { it[PreferencesKeys.Navigation.NAV_BAR_STYLE] = style }
    }

    val navBarCompactModeFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Navigation.NAV_BAR_COMPACT_MODE] ?: false }

    suspend fun setNavBarCompactMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Navigation.NAV_BAR_COMPACT_MODE] = enabled }
    }

    val libraryNavigationModeFlow: Flow<String> =
        pref { it[PreferencesKeys.Navigation.LIBRARY_NAVIGATION_MODE] ?: LibraryNavigationMode.TAB_ROW }

    suspend fun setLibraryNavigationMode(mode: String) {
        dataStore.edit { it[PreferencesKeys.Navigation.LIBRARY_NAVIGATION_MODE] = mode }
    }

    val carouselStyleFlow: Flow<String> =
        pref { it[PreferencesKeys.Navigation.CAROUSEL_STYLE] ?: CarouselStyle.NO_PEEK }

    suspend fun setCarouselStyle(style: String) {
        dataStore.edit { it[PreferencesKeys.Navigation.CAROUSEL_STYLE] = style }
    }

    val launchTabFlow: Flow<String> =
        pref { it[PreferencesKeys.Navigation.LAUNCH_TAB] ?: LaunchTab.HOME }

    suspend fun setLaunchTab(tab: String) {
        dataStore.edit { it[PreferencesKeys.Navigation.LAUNCH_TAB] = tab }
    }

    val useSmoothCornersFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Navigation.USE_SMOOTH_CORNERS] ?: false }

    suspend fun setUseSmoothCorners(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Navigation.USE_SMOOTH_CORNERS] = enabled }
    }

    /** Left-to-right "wave" when the song changes in the collapsed mini player. On by default. */
    val miniPlayerSongTransitionFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Navigation.MINI_PLAYER_SONG_TRANSITION] ?: true }

    suspend fun setMiniPlayerSongTransition(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Navigation.MINI_PLAYER_SONG_TRANSITION] = enabled }
    }

    val showScrollbarFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Navigation.SHOW_SCROLLBAR] ?: true }

    suspend fun setShowScrollbar(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Navigation.SHOW_SCROLLBAR] = enabled }
    }

    // ─── Offline & network policy ─────────────────────────────────────────────

    val offlineModeFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.OfflineNetwork.OFFLINE_MODE] ?: false }

    suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.OfflineNetwork.OFFLINE_MODE] = enabled }
    }

    val lyricsIntegrationEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.OfflineNetwork.LYRICS_INTEGRATION_ENABLED] ?: true }

    suspend fun setLyricsIntegrationEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.OfflineNetwork.LYRICS_INTEGRATION_ENABLED] = enabled }
    }

    // ─── Metadata enrichment ──────────────────────────────────────────────────

    val enrichmentEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Enrichment.ENRICHMENT_ENABLED] ?: true }

    suspend fun setEnrichmentEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Enrichment.ENRICHMENT_ENABLED] = enabled }
    }

    val lastFmApiKeyFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.LASTFM_API_KEY] ?: "" }

    suspend fun setLastFmApiKey(apiKey: String) {
        dataStore.edit { it[PreferencesKeys.Enrichment.LASTFM_API_KEY] = apiKey.trim() }
    }

    /** ListenBrainz user token; blank means scrobbling is off. */
    val listenBrainzTokenFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.LISTENBRAINZ_TOKEN] ?: "" }

    suspend fun setListenBrainzToken(token: String) {
        dataStore.edit { it[PreferencesKeys.Enrichment.LISTENBRAINZ_TOKEN] = token.trim() }
    }

    /** JSON queue of listens that could not be sent yet (see Scrobbler). */
    val listenBrainzPendingFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.LISTENBRAINZ_PENDING] ?: "" }

    suspend fun setListenBrainzPending(json: String) {
        dataStore.edit { it[PreferencesKeys.Enrichment.LISTENBRAINZ_PENDING] = json }
    }

    val spotifyClientIdFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.SPOTIFY_CLIENT_ID] ?: "" }

    suspend fun setSpotifyClientId(clientId: String) {
        dataStore.edit { it[PreferencesKeys.Enrichment.SPOTIFY_CLIENT_ID] = clientId.trim() }
    }

    val spotifyClientSecretFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.SPOTIFY_CLIENT_SECRET] ?: "" }

    suspend fun setSpotifyClientSecret(clientSecret: String) {
        dataStore.edit { it[PreferencesKeys.Enrichment.SPOTIFY_CLIENT_SECRET] = clientSecret.trim() }
    }

    val spotifyAccessTokenFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.SPOTIFY_ACCESS_TOKEN] ?: "" }

    val spotifyRefreshTokenFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.SPOTIFY_REFRESH_TOKEN] ?: "" }

    val spotifyTokenExpiresAtFlow: Flow<Long> =
        pref { it[PreferencesKeys.Enrichment.SPOTIFY_TOKEN_EXPIRES_AT] ?: 0L }

    suspend fun setSpotifyAuthTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        dataStore.edit {
            it[PreferencesKeys.Enrichment.SPOTIFY_ACCESS_TOKEN] = accessToken
            it[PreferencesKeys.Enrichment.SPOTIFY_REFRESH_TOKEN] = refreshToken
            it[PreferencesKeys.Enrichment.SPOTIFY_TOKEN_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun clearSpotifyAuthTokens() {
        dataStore.edit {
            it.remove(PreferencesKeys.Enrichment.SPOTIFY_ACCESS_TOKEN)
            it.remove(PreferencesKeys.Enrichment.SPOTIFY_REFRESH_TOKEN)
            it.remove(PreferencesKeys.Enrichment.SPOTIFY_TOKEN_EXPIRES_AT)
        }
    }

    val ytMusicSapisidFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.YTMUSIC_SAPISID] ?: "" }

    val ytMusicSidFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.YTMUSIC_SID] ?: "" }

    val ytMusicFullCookieFlow: Flow<String> =
        pref { it[PreferencesKeys.Enrichment.YTMUSIC_FULL_COOKIE] ?: "" }

    suspend fun setYtMusicCookies(sapisid: String, sid: String, fullCookie: String) {
        dataStore.edit {
            it[PreferencesKeys.Enrichment.YTMUSIC_SAPISID] = sapisid
            it[PreferencesKeys.Enrichment.YTMUSIC_SID] = sid
            it[PreferencesKeys.Enrichment.YTMUSIC_FULL_COOKIE] = fullCookie
        }
    }

    suspend fun clearYtMusicCookies() {
        dataStore.edit {
            it.remove(PreferencesKeys.Enrichment.YTMUSIC_SAPISID)
            it.remove(PreferencesKeys.Enrichment.YTMUSIC_SID)
            it.remove(PreferencesKeys.Enrichment.YTMUSIC_FULL_COOKIE)
        }
    }

    val enrichmentWifiOnlyFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Enrichment.ENRICHMENT_WIFI_ONLY] ?: false }

    suspend fun setEnrichmentWifiOnly(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Enrichment.ENRICHMENT_WIFI_ONLY] = enabled }
    }

    val autoEnrichAfterSyncFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Enrichment.AUTO_ENRICH_AFTER_SYNC] ?: true }

    suspend fun setAutoEnrichAfterSync(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Enrichment.AUTO_ENRICH_AFTER_SYNC] = enabled }
    }

    // ─── Multi-artist settings ────────────────────────────────────────────────

    val artistDelimitersFlow: Flow<List<String>> =
        pref {
            normalizeLegacyDefaultArtistDelimiters(
                decodeJsonPref(it, PreferencesKeys.MultiArtist.ARTIST_DELIMITERS, DEFAULT_ARTIST_DELIMITERS)
            )
        }

    suspend fun setArtistDelimiters(delimiters: List<String>) {
        if (delimiters.isEmpty()) return
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MultiArtist.ARTIST_DELIMITERS] = json.encodeToString(delimiters)
            preferences[PreferencesKeys.MultiArtist.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    suspend fun resetArtistDelimitersToDefault() = setArtistDelimiters(DEFAULT_ARTIST_DELIMITERS)

    val artistWordDelimitersFlow: Flow<List<String>> =
        pref { decodeJsonPref(it, PreferencesKeys.MultiArtist.ARTIST_WORD_DELIMITERS, DEFAULT_ARTIST_WORD_DELIMITERS) }

    suspend fun setArtistWordDelimiters(delimiters: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MultiArtist.ARTIST_WORD_DELIMITERS] = json.encodeToString(delimiters)
            preferences[PreferencesKeys.MultiArtist.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    suspend fun resetArtistWordDelimitersToDefault() = setArtistWordDelimiters(DEFAULT_ARTIST_WORD_DELIMITERS)

    val extractArtistsFromTitleFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.MultiArtist.EXTRACT_ARTISTS_FROM_TITLE] ?: true }

    suspend fun setExtractArtistsFromTitle(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MultiArtist.EXTRACT_ARTISTS_FROM_TITLE] = enabled
            preferences[PreferencesKeys.MultiArtist.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    val groupByAlbumArtistFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.MultiArtist.GROUP_BY_ALBUM_ARTIST] ?: false }

    suspend fun setGroupByAlbumArtist(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MultiArtist.GROUP_BY_ALBUM_ARTIST] = enabled
            preferences[PreferencesKeys.MultiArtist.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    val artistSettingsRescanRequiredFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.MultiArtist.ARTIST_SETTINGS_RESCAN_REQUIRED] ?: false }

    suspend fun clearArtistSettingsRescanRequired() {
        dataStore.edit { it[PreferencesKeys.MultiArtist.ARTIST_SETTINGS_RESCAN_REQUIRED] = false }
    }

    // ─── Lyrics ───────────────────────────────────────────────────────────────

    /**
     * Per-song lyrics sync offsets in milliseconds, stored as a JSON map.
     * Positive = lyrics appear later; negative = lyrics appear earlier.
     */
    private val lyricsSyncOffsetsFlow: Flow<Map<String, Int>> =
        pref { decodeJsonPref(it, PreferencesKeys.Lyrics.LYRICS_SYNC_OFFSETS, emptyMap()) }

    fun getLyricsSyncOffsetFlow(songId: String): Flow<Int> =
        lyricsSyncOffsetsFlow.map { it[songId] ?: 0 }

    suspend fun getLyricsSyncOffset(songId: String): Int =
        getLyricsSyncOffsetFlow(songId).first()

    suspend fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        editJsonMap<Int>(PreferencesKeys.Lyrics.LYRICS_SYNC_OFFSETS) {
            if (offsetMs == 0) remove(songId) else put(songId, offsetMs)
        }
    }

    val lyricsSourcePreferenceFlow: Flow<LyricsSourcePreference> =
        pref { LyricsSourcePreference.fromName(it[PreferencesKeys.Lyrics.LYRICS_SOURCE_PREFERENCE]) }

    suspend fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        dataStore.edit { it[PreferencesKeys.Lyrics.LYRICS_SOURCE_PREFERENCE] = preference.name }
    }

    val autoScanLrcFilesFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Lyrics.AUTO_SCAN_LRC_FILES] ?: false }

    suspend fun setAutoScanLrcFiles(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Lyrics.AUTO_SCAN_LRC_FILES] = enabled }
    }

    val immersiveLyricsEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Lyrics.IMMERSIVE_LYRICS_ENABLED] ?: false }

    suspend fun setImmersiveLyricsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Lyrics.IMMERSIVE_LYRICS_ENABLED] = enabled }
    }

    val immersiveLyricsTimeoutFlow: Flow<Long> =
        pref { it[PreferencesKeys.Lyrics.IMMERSIVE_LYRICS_TIMEOUT] ?: 4000L }

    suspend fun setImmersiveLyricsTimeout(timeout: Long) {
        dataStore.edit { it[PreferencesKeys.Lyrics.IMMERSIVE_LYRICS_TIMEOUT] = timeout }
    }

    val useAnimatedLyricsFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Lyrics.USE_ANIMATED_LYRICS] ?: false }

    suspend fun setUseAnimatedLyrics(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Lyrics.USE_ANIMATED_LYRICS] = enabled }
    }

    val animatedLyricsBlurEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Lyrics.ANIMATED_LYRICS_BLUR_ENABLED] ?: true }

    suspend fun setAnimatedLyricsBlurEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Lyrics.ANIMATED_LYRICS_BLUR_ENABLED] = enabled }
    }

    val animatedLyricsBlurStrengthFlow: Flow<Float> =
        pref { it[PreferencesKeys.Lyrics.ANIMATED_LYRICS_BLUR_STRENGTH] ?: 2.5f }

    suspend fun setAnimatedLyricsBlurStrength(strength: Float) {
        dataStore.edit { it[PreferencesKeys.Lyrics.ANIMATED_LYRICS_BLUR_STRENGTH] = strength }
    }

    val coverLyricsEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Lyrics.COVER_LYRICS_ENABLED] ?: false }

    suspend fun setCoverLyricsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Lyrics.COVER_LYRICS_ENABLED] = enabled }
    }

    // ─── Custom genres ────────────────────────────────────────────────────────

    val customGenresFlow: Flow<Set<String>> =
        pref { it[PreferencesKeys.CustomGenres.CUSTOM_GENRES] ?: emptySet() }

    val customGenreIconsFlow: Flow<Map<String, Int>> =
        pref { decodeJsonPref(it, PreferencesKeys.CustomGenres.CUSTOM_GENRE_ICONS, emptyMap()) }

    suspend fun addCustomGenre(genre: String, iconResId: Int? = null) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.CustomGenres.CUSTOM_GENRES] ?: emptySet()
            preferences[PreferencesKeys.CustomGenres.CUSTOM_GENRES] = current + genre

            if (iconResId != null) {
                val icons = decodeJsonPref(preferences, PreferencesKeys.CustomGenres.CUSTOM_GENRE_ICONS, emptyMap<String, Int>())
                    .toMutableMap()
                icons[genre] = iconResId
                preferences[PreferencesKeys.CustomGenres.CUSTOM_GENRE_ICONS] = json.encodeToString(icons)
            }
        }
    }

    // ─── Collage ──────────────────────────────────────────────────────────────

    val collagePatternFlow: Flow<CollagePattern> =
        pref { CollagePattern.fromStorageKey(it[PreferencesKeys.Collage.COLLAGE_PATTERN]) }

    suspend fun setCollagePattern(pattern: CollagePattern) {
        dataStore.edit { it[PreferencesKeys.Collage.COLLAGE_PATTERN] = pattern.storageKey }
    }

    val collageAutoRotateFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Collage.COLLAGE_AUTO_ROTATE] ?: false }

    suspend fun setCollageAutoRotate(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Collage.COLLAGE_AUTO_ROTATE] = enabled }
    }

    // ─── Quick settings / last playlist ──────────────────────────────────────

    val lastPlaylistIdFlow: Flow<String?> =
        pref { it[PreferencesKeys.QuickSettings.LAST_PLAYLIST_ID]?.takeIf { id -> id.isNotBlank() } }

    val lastPlaylistNameFlow: Flow<String?> =
        pref { it[PreferencesKeys.QuickSettings.LAST_PLAYLIST_NAME] }

    suspend fun setLastPlaylist(playlistId: String, playlistName: String) {
        dataStore.edit {
            it[PreferencesKeys.QuickSettings.LAST_PLAYLIST_ID] = playlistId
            it[PreferencesKeys.QuickSettings.LAST_PLAYLIST_NAME] = playlistName
        }
    }

    suspend fun clearLastPlaylist() {
        dataStore.edit {
            it.remove(PreferencesKeys.QuickSettings.LAST_PLAYLIST_ID)
            it.remove(PreferencesKeys.QuickSettings.LAST_PLAYLIST_NAME)
        }
    }

    // ─── Developer options ────────────────────────────────────────────────────

    /**
     * Album art quality for player view.
     * Thumbnails in lists always use low resolution (256 px) for performance.
     */
    val albumArtQualityFlow: Flow<AlbumArtQuality> =
        pref { preferences ->
            preferences[PreferencesKeys.Developer.ALBUM_ART_QUALITY]
                ?.let { runCatching { AlbumArtQuality.valueOf(it) }.getOrNull() }
                ?: AlbumArtQuality.MEDIUM
        }

    suspend fun setAlbumArtQuality(quality: AlbumArtQuality) {
        dataStore.edit { it[PreferencesKeys.Developer.ALBUM_ART_QUALITY] = quality.name }
    }

    val albumArtCacheLimitMbFlow: Flow<Int> =
        pref { it[PreferencesKeys.Developer.ALBUM_ART_CACHE_LIMIT_MB] ?: DEFAULT_ALBUM_ART_CACHE_LIMIT_MB }

    suspend fun setAlbumArtCacheLimitMb(limitMb: Int) {
        dataStore.edit { it[PreferencesKeys.Developer.ALBUM_ART_CACHE_LIMIT_MB] = limitMb.coerceIn(50, 1500) }
    }

    /** Whether tapping the player sheet background closes it. Defaults to false to avoid accidental dismissal. */
    val tapBackgroundClosesPlayerFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Developer.TAP_BACKGROUND_CLOSES_PLAYER] ?: false }

    suspend fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Developer.TAP_BACKGROUND_CLOSES_PLAYER] = enabled }
    }

    val hapticsEnabledFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Developer.HAPTICS_ENABLED] ?: true }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Developer.HAPTICS_ENABLED] = enabled }
    }

    val disableBlurAllOverFlow: Flow<Boolean> =
        pref { it[PreferencesKeys.Developer.DISABLE_BLUR_ALL_OVER] ?: false }

    suspend fun setDisableBlurAllOver(disabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.Developer.DISABLE_BLUR_ALL_OVER] = disabled }
    }

    val advancedPerformanceDiagnosticsSettingsFlow: Flow<AdvancedPerformanceDiagnosticsSettings> =
        pref { preferences ->
            AdvancedPerformanceDiagnosticsSettings(
                enabled = preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] ?: false,
                sessionStartedEpochMs =
                    preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT],
                expiresAtEpochMs =
                    preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT]
            )
        }.distinctUntilChanged()

    suspend fun setAdvancedPerformanceDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            if (enabled) {
                val now = System.currentTimeMillis()
                preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] = true
                preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT] = now
                preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT] =
                    now + AdvancedPerformanceDiagnostics.DEFAULT_SESSION_DURATION_MS
            } else {
                preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] = false
                preferences.remove(PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT)
                preferences.remove(PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT)
            }
        }
    }

    suspend fun disableExpiredAdvancedPerformanceDiagnostics(nowEpochMs: Long = System.currentTimeMillis()) {
        dataStore.edit { preferences ->
            val enabled = preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] ?: false
            val expiresAt = preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT]
            if (enabled && (expiresAt == null || nowEpochMs >= expiresAt)) {
                preferences[PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] = false
                preferences.remove(PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT)
                preferences.remove(PreferencesKeys.Developer.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT)
            }
        }
    }

    // ─── Backup / restore ─────────────────────────────────────────────────────

    suspend fun clearPreferencesByKeys(keyNames: Set<String>) {
        if (keyNames.isEmpty()) return
        dataStore.edit { preferences ->
            preferences.asMap().keys
                .filter { key -> key.name in keyNames && key.name !in backupExcludedKeyNames }
                .forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    preferences.remove(key as Preferences.Key<Any>)
                }
        }
    }

    suspend fun clearPreferencesExceptKeys(excludedKeyNames: Set<String>) {
        val protected = excludedKeyNames + backupExcludedKeyNames
        dataStore.edit { preferences ->
            preferences.asMap().keys
                .filterNot { key -> key.name in protected }
                .forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    preferences.remove(key as Preferences.Key<Any>)
                }
        }
    }

    suspend fun exportPreferencesForBackup(): List<PreferenceBackupEntry> {
        val snapshot = dataStore.data.first().asMap()
        return snapshot.mapNotNull { (key, value) ->
            if (key.name in backupExcludedKeyNames) return@mapNotNull null
            when (value) {
                is String  -> PreferenceBackupEntry(key = key.name, type = "string",     stringValue = value)
                is Int     -> PreferenceBackupEntry(key = key.name, type = "int",        intValue = value)
                is Long    -> PreferenceBackupEntry(key = key.name, type = "long",       longValue = value)
                is Boolean -> PreferenceBackupEntry(key = key.name, type = "boolean",    booleanValue = value)
                is Float   -> PreferenceBackupEntry(key = key.name, type = "float",      floatValue = value)
                is Double  -> PreferenceBackupEntry(key = key.name, type = "double",     doubleValue = value)
                is Set<*>  -> PreferenceBackupEntry(
                    key = key.name, type = "string_set",
                    stringSetValue = value.filterIsInstance<String>().toSet()
                )
                else -> null
            }
        }
    }

    suspend fun importPreferencesFromBackup(
        entries: List<PreferenceBackupEntry>,
        clearExisting: Boolean = true
    ) {
        dataStore.edit { preferences ->
            if (clearExisting) {
                preferences.asMap().keys
                    .filterNot { key -> key.name in backupExcludedKeyNames }
                    .forEach { key ->
                        @Suppress("UNCHECKED_CAST")
                        preferences.remove(key as Preferences.Key<Any>)
                    }
            }
            entries.forEach { entry ->
                if (entry.key in backupExcludedKeyNames) return@forEach
                when (entry.type) {
                    "string"     -> entry.stringValue?.let { preferences[stringPreferencesKey(entry.key)] = it }
                    "int"        -> (entry.intValue ?: entry.doubleValue?.toInt() ?: entry.longValue?.toInt())
                        ?.let { preferences[intPreferencesKey(entry.key)] = it }
                    "long"       -> (entry.longValue ?: entry.doubleValue?.toLong() ?: entry.intValue?.toLong())
                        ?.let { preferences[longPreferencesKey(entry.key)] = it }
                    "boolean"    -> entry.booleanValue?.let { preferences[booleanPreferencesKey(entry.key)] = it }
                    "float"      -> (entry.floatValue ?: entry.doubleValue?.toFloat())
                        ?.let { preferences[floatPreferencesKey(entry.key)] = it }
                    "double"     -> (entry.doubleValue ?: entry.floatValue?.toDouble())
                        ?.let { preferences[androidx.datastore.preferences.core.doublePreferencesKey(entry.key)] = it }
                    "string_set" -> entry.stringSetValue?.let { preferences[stringSetPreferencesKey(entry.key)] = it }
                }
            }
        }
    }

    // ─── Companion ────────────────────────────────────────────────────────────

    companion object {
        /** Default character delimiters for splitting multi-artist tags. */
        val DEFAULT_ARTIST_DELIMITERS = listOf(";")

        private val LEGACY_DEFAULT_ARTIST_DELIMITERS = listOf("/", ";", ",", "+", "&")

        /** Default word-based delimiters matched case-insensitively with whitespace boundaries. */
        val DEFAULT_ARTIST_WORD_DELIMITERS = listOf(
            "featuring", "feat.", "feat", "ft.", "ft",
            "vs.", "vs", "versus", "with", "prod.", "prod"
        )

        const val DEFAULT_ALBUM_ART_CACHE_LIMIT_MB = 200
    }

    // ─── Private utilities ────────────────────────────────────────────────────

    private fun normalizeLegacyDefaultArtistDelimiters(delimiters: List<String>): List<String> =
        if (delimiters == LEGACY_DEFAULT_ARTIST_DELIMITERS) DEFAULT_ARTIST_DELIMITERS else delimiters

    /** Increments [value] by 1, wrapping back to 0 on overflow. */
    private fun incrementWrapped(value: Int?) =
        if (value == null || value == Int.MAX_VALUE) 0 else value + 1
}
