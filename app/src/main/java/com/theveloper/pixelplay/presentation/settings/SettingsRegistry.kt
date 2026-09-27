package com.theveloper.pixelplay.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.navigation.Screen

/**
 * One searchable setting, declared once.
 *
 * This is deliberately plain data: no Compose types, no resolved strings, no lambdas.
 * That makes it unit-testable on the JVM (see SettingsRegistryTest) and means the
 * search index is no longer rebuilt on every recomposition of the settings root.
 *
 * @param key            Stable id. Must match the `settingKey` passed to the composable
 *                       that renders this setting, which is what lets search deep-link
 *                       and highlight it. SettingsRegistryTest enforces the match.
 * @param categoryId     [com.theveloper.pixelplay.presentation.model.SettingsCategory] id
 *                       of the screen that renders this setting. Wrong values send search
 *                       results to a screen that does not contain the setting.
 * @param titleRes       Label shown in search results. Should be the same resource the
 *                       row itself uses, so the two cannot drift.
 * @param descriptionRes Supporting line, or null when the control has no subtitle.
 * @param route          Set only for settings that live on their own screen rather than
 *                       inside a category. When present, search navigates here directly
 *                       and `categoryId` is not used for navigation.
 * @param keywords       Extra search terms. English-only today; moving these to a string
 *                       resource is tracked as part of the Phase 7 search work.
 */
@Immutable
data class SettingEntry(
    val key: String,
    val categoryId: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int? = null,
    val route: String? = null,
    val keywords: List<String> = emptyList()
)

/**
 * The single source of truth for what settings exist and where they live.
 *
 * Adding a setting means adding an entry here AND passing the same `key` as `settingKey`
 * to the composable that renders it. SettingsRegistryTest fails the build if those two
 * sides disagree, which is what stopped this list drifting out of sync with the screens.
 */
object SettingsRegistry {

    val entries: List<SettingEntry> = listOf(

        // Appearance
        SettingEntry(
            key = "app_theme",
            categoryId = "appearance",
            titleRes = R.string.settings_theme_mode_inline_title,
            descriptionRes = R.string.settings_app_theme_subtitle,
            keywords = listOf("dark", "light", "theme", "system", "appearance", "black", "colors")
        ),
        SettingEntry(
            key = "app_language",
            categoryId = "general",
            titleRes = R.string.settings_app_language_title,
            descriptionRes = R.string.settings_app_language_subtitle,
            keywords = listOf("language", "locale", "english", "spanish", "german", "french", "russian", "chinese", "translation")
        ),
        SettingEntry(
            key = "smooth_corners",
            categoryId = "appearance",
            titleRes = R.string.settings_smooth_corners_title,
            descriptionRes = R.string.settings_smooth_corners_subtitle,
            keywords = listOf("corners", "rounded", "ui", "shape", "smooth")
        ),
        SettingEntry(
            key = "disable_blur",
            categoryId = "appearance",
            titleRes = R.string.settings_disable_blur_all_over_title,
            descriptionRes = R.string.settings_disable_blur_all_over_subtitle,
            keywords = listOf("blur", "effects", "glassmorphism", "performance", "battery")
        ),
        SettingEntry(
            key = "mini_player_song_transition",
            categoryId = "appearance",
            titleRes = R.string.settings_mini_player_song_transition_title,
            descriptionRes = R.string.settings_mini_player_song_transition_subtitle,
            keywords = listOf("mini player", "animation", "transition", "wave", "skip", "song change", "motion")
        ),
        SettingEntry(
            key = "show_scrollbar",
            categoryId = "appearance",
            titleRes = R.string.settings_show_scrollbar_title,
            descriptionRes = R.string.settings_show_scrollbar_subtitle,
            keywords = listOf("scrollbar", "scroll", "list", "fast scroll")
        ),
        SettingEntry(
            key = "navbar_style",
            categoryId = "appearance",
            titleRes = R.string.settings_navbar_style_title,
            descriptionRes = R.string.settings_navbar_style_subtitle,
            keywords = listOf("navbar", "navigation bar", "style", "full width")
        ),
        SettingEntry(
            key = "compact_mode",
            categoryId = "appearance",
            titleRes = R.string.settings_compact_mode_title,
            descriptionRes = R.string.settings_compact_mode_subtitle,
            keywords = listOf("compact", "navbar", "icons")
        ),
        SettingEntry(
            key = "navbar_corner_radius",
            categoryId = "appearance",
            titleRes = R.string.settings_navbar_corner_title,
            descriptionRes = R.string.settings_navbar_corner_subtitle,
            keywords = listOf("navbar", "corner", "radius", "dp")
        ),

        // General
        SettingEntry(
            key = "default_tab",
            categoryId = "general",
            titleRes = R.string.settings_default_tab_title,
            descriptionRes = R.string.settings_default_tab_subtitle,
            keywords = listOf("default tab", "launch", "home", "library", "start")
        ),
        SettingEntry(
            key = "library_navigation",
            categoryId = "general",
            titleRes = R.string.settings_library_navigation_title,
            descriptionRes = R.string.settings_library_navigation_subtitle,
            keywords = listOf("library navigation", "tabs", "pill", "grid")
        ),

        // Player & Lyrics (id "lyrics")
        SettingEntry(
            key = "player_theme",
            categoryId = "lyrics",
            titleRes = R.string.settings_player_theme_title,
            descriptionRes = R.string.settings_player_theme_subtitle,
            keywords = listOf("player theme", "album art", "dynamic", "floating player")
        ),
        SettingEntry(
            key = "show_player_file_info",
            categoryId = "lyrics",
            titleRes = R.string.settings_show_player_file_info_title,
            descriptionRes = R.string.settings_show_player_file_info_subtitle,
            keywords = listOf("file info", "codec", "bitrate", "sample rate", "audio details")
        ),
        SettingEntry(
            key = "palette_style",
            categoryId = "lyrics",
            titleRes = R.string.settings_album_art_palette_title,
            route = Screen.PaletteStyle.route,
            keywords = listOf("palette", "colors", "style", "album art colors", "tonal")
        ),
        SettingEntry(
            key = "carousel_style",
            categoryId = "lyrics",
            titleRes = R.string.settings_carousel_style_title,
            descriptionRes = R.string.settings_carousel_style_subtitle,
            keywords = listOf("carousel", "album", "peek", "carousel style")
        ),

        // General (haptics)
        SettingEntry(
            key = "haptic_feedback",
            categoryId = "general",
            titleRes = R.string.settings_haptic_feedback_title,
            descriptionRes = R.string.settings_haptic_feedback_subtitle,
            keywords = listOf("haptic", "vibration", "feedback", "vibrate")
        ),

        // Music library
        SettingEntry(
            key = "excluded_directories",
            categoryId = "library",
            titleRes = R.string.settings_excluded_directories_title,
            descriptionRes = R.string.settings_excluded_directories_subtitle,
            keywords = listOf("exclude", "folders", "skip", "scanning", "directory")
        ),
        SettingEntry(
            key = "artists",
            categoryId = "library",
            titleRes = R.string.settings_artists_title,
            descriptionRes = R.string.settings_artists_subtitle,
            keywords = listOf("artists", "delimiters", "separators", "split")
        ),
        SettingEntry(
            key = "min_song_duration",
            categoryId = "library",
            titleRes = R.string.settings_min_song_duration,
            keywords = listOf("minimum song duration", "skip short", "filters", "seconds")
        ),
        SettingEntry(
            key = "min_tracks_per_album",
            categoryId = "library",
            titleRes = R.string.settings_min_tracks_per_album,
            keywords = listOf("minimum tracks", "album filter", "tracks count")
        ),
        SettingEntry(
            key = "cache_limit",
            categoryId = "library",
            titleRes = R.string.settings_album_art_cache_limit,
            keywords = listOf("album art cache", "limit", "mb", "cache size")
        ),
        SettingEntry(
            key = "refresh_library",
            categoryId = "library",
            titleRes = R.string.settings_refresh_library_title,
            descriptionRes = R.string.settings_refresh_library_subtitle,
            keywords = listOf("refresh", "scan", "rescan", "update library", "new files")
        ),

        // Playback
        SettingEntry(
            key = "equalizer",
            categoryId = "playback",
            titleRes = R.string.settings_category_equalizer_title,
            descriptionRes = R.string.settings_category_equalizer_subtitle,
            route = Screen.Equalizer.route,
            keywords = listOf("equalizer", "eq", "bass boost", "presets", "frequency")
        ),
        SettingEntry(
            key = "hifi_mode",
            categoryId = "playback",
            titleRes = R.string.settings_hifi_mode_title,
            keywords = listOf("hifi", "float 32-bit", "audio track", "high fidelity")
        ),
        SettingEntry(
            key = "replaygain",
            categoryId = "playback",
            titleRes = R.string.settings_replaygain_enable_title,
            descriptionRes = R.string.settings_replaygain_enable_subtitle,
            keywords = listOf("replaygain", "volume", "normalization", "gain")
        ),
        SettingEntry(
            key = "replaygain_mode",
            categoryId = "playback",
            titleRes = R.string.settings_gain_mode_title,
            descriptionRes = R.string.settings_gain_mode_subtitle,
            keywords = listOf("gain mode", "track gain", "album gain")
        ),
        SettingEntry(
            key = "crossfade",
            categoryId = "playback",
            titleRes = R.string.settings_crossfade_title,
            descriptionRes = R.string.settings_crossfade_subtitle,
            keywords = listOf("crossfade", "transitions", "overlap", "fade")
        ),
        SettingEntry(
            key = "crossfade_duration",
            categoryId = "playback",
            titleRes = R.string.settings_crossfade_duration_title,
            keywords = listOf("crossfade duration", "overlap time", "seconds")
        ),
        SettingEntry(
            key = "persistent_shuffle",
            categoryId = "playback",
            titleRes = R.string.settings_persistent_shuffle_title,
            descriptionRes = R.string.settings_persistent_shuffle_subtitle,
            keywords = listOf("persistent shuffle", "remember shuffle")
        ),
        SettingEntry(
            key = "queue_history",
            categoryId = "playback",
            titleRes = R.string.settings_show_queue_history_title,
            descriptionRes = R.string.settings_show_queue_history_subtitle,
            keywords = listOf("queue history", "previous songs")
        ),
        SettingEntry(
            key = "cast_autoplay",
            categoryId = "playback",
            titleRes = R.string.settings_cast_autoplay_title,
            descriptionRes = R.string.settings_cast_autoplay_subtitle,
            keywords = listOf("cast", "autoplay", "chromecast", "connect")
        ),
        SettingEntry(
            key = "pause_on_zero",
            categoryId = "playback",
            titleRes = R.string.settings_pause_on_volume_zero,
            descriptionRes = R.string.settings_pause_on_volume_zero_desc,
            keywords = listOf("pause volume", "volume zero", "mute pause")
        ),
        SettingEntry(
            key = "headphones_resume",
            categoryId = "playback",
            titleRes = R.string.settings_headphones_resume_title,
            descriptionRes = R.string.settings_headphones_resume_subtitle,
            keywords = listOf("headphones", "resume", "headset", "reconnect", "unplug")
        ),
        SettingEntry(
            key = "keep_playing",
            categoryId = "playback",
            titleRes = R.string.settings_keep_playing_title,
            descriptionRes = R.string.settings_keep_playing_subtitle,
            keywords = listOf("background play", "keep playing", "recents", "stop")
        ),
        SettingEntry(
            key = "battery_optimization",
            categoryId = "playback",
            titleRes = R.string.settings_battery_optimization_title,
            descriptionRes = R.string.settings_battery_optimization_subtitle,
            keywords = listOf("battery", "optimization", "interruption", "background play")
        ),

        // Lyrics
        SettingEntry(
            key = "lyrics_priority",
            categoryId = "lyrics",
            titleRes = R.string.settings_lyrics_source_priority_title,
            descriptionRes = R.string.settings_lyrics_source_priority_subtitle,
            keywords = listOf("lyrics priority", "embedded first", "online first", "local lrc")
        ),
        SettingEntry(
            key = "reset_lyrics",
            categoryId = "lyrics",
            titleRes = R.string.settings_reset_imported_lyrics_title,
            descriptionRes = R.string.settings_reset_imported_lyrics_subtitle,
            keywords = listOf("reset lyrics", "clear lyrics", "imported lyrics")
        ),
        SettingEntry(
            key = "auto_scan_lrc",
            categoryId = "lyrics",
            titleRes = R.string.settings_auto_scan_lrc_title,
            descriptionRes = R.string.settings_auto_scan_lrc_subtitle,
            keywords = listOf("scan lrc", "local lyrics", "automatic search")
        ),
        SettingEntry(
            key = "lyrics_integration",
            categoryId = "lyrics",
            titleRes = R.string.settings_lyrics_lrclib_title,
            descriptionRes = R.string.settings_lyrics_lrclib_subtitle,
            keywords = listOf("lrclib", "online lyrics", "lyrics search", "missing lyrics")
        ),
        SettingEntry(
            key = "immersive_lyrics",
            categoryId = "lyrics",
            titleRes = R.string.settings_immersive_lyrics_title,
            descriptionRes = R.string.settings_immersive_lyrics_subtitle,
            keywords = listOf("immersive", "lyrics", "auto hide", "large text")
        ),
        SettingEntry(
            key = "auto_hide_delay",
            categoryId = "lyrics",
            titleRes = R.string.settings_auto_hide_delay_title,
            descriptionRes = R.string.settings_auto_hide_delay_subtitle,
            keywords = listOf("auto hide", "lyrics delay", "timeout", "immersive delay", "off", "never hide")
        ),
        SettingEntry(
            key = "cover_lyrics",
            categoryId = "lyrics",
            titleRes = R.string.settings_cover_lyrics_title,
            descriptionRes = R.string.settings_cover_lyrics_subtitle,
            keywords = listOf("cover lyrics", "album art lyrics", "lyrics on cover", "artwork")
        ),
        SettingEntry(
            key = "lyrics_font",
            categoryId = "lyrics",
            titleRes = R.string.settings_lyrics_font_title,
            descriptionRes = R.string.settings_lyrics_font_subtitle,
            keywords = listOf("lyrics font", "typeface", "google sans", "roboto flex", "montserrat")
        ),
        SettingEntry(
            key = "lyrics_text_size",
            categoryId = "lyrics",
            titleRes = R.string.settings_lyrics_size_title,
            descriptionRes = R.string.settings_lyrics_size_subtitle,
            keywords = listOf("lyrics size", "text size", "bigger lyrics", "smaller lyrics")
        ),
        SettingEntry(
            key = "animated_lyrics",
            categoryId = "lyrics",
            titleRes = R.string.settings_tweak_animated_lyrics,
            keywords = listOf("animated lyrics", "spring", "lyrics effects", "experimental")
        ),
        SettingEntry(
            key = "animated_lyrics_blur",
            categoryId = "lyrics",
            titleRes = R.string.settings_tweak_lyrics_blur,
            keywords = listOf("blur", "lyrics blur", "experimental")
        ),
        SettingEntry(
            key = "animated_lyrics_blur_strength",
            categoryId = "lyrics",
            titleRes = R.string.settings_tweak_blur_strength,
            keywords = listOf("blur strength", "blur amount")
        ),
        SettingEntry(
            key = "player_load_after_open",
            categoryId = "lyrics",
            titleRes = R.string.settings_tweak_load_after_open,
            keywords = listOf("loading tweaks", "player lag", "delay", "experimental")
        ),
        SettingEntry(
            key = "player_placeholders",
            categoryId = "lyrics",
            titleRes = R.string.settings_tweak_placeholders,
            keywords = listOf("placeholders", "loading", "experimental")
        ),
        SettingEntry(
            key = "album_art_quality",
            categoryId = "lyrics",
            titleRes = R.string.settings_tweak_album_art_quality,
            keywords = listOf("album art", "resolution", "quality", "artwork")
        ),

        // AI features
        SettingEntry(
            key = "offline_mode",
            categoryId = "ai",
            titleRes = R.string.settings_offline_mode_title,
            descriptionRes = R.string.settings_offline_mode_subtitle,
            keywords = listOf("offline", "network", "disable internet", "ai suggestions")
        ),
        SettingEntry(
            key = "safe_token",
            categoryId = "ai",
            titleRes = R.string.settings_safe_token_title,
            keywords = listOf("safe token", "cheap", "context limit")
        ),

        // Backup & restore
        SettingEntry(
            key = "export_backup",
            categoryId = "backup_restore",
            titleRes = R.string.settings_export_backup_title,
            keywords = listOf("export", "backup", "pxpl", "save settings")
        ),
        SettingEntry(
            key = "import_backup",
            categoryId = "backup_restore",
            titleRes = R.string.settings_import_backup_title,
            keywords = listOf("import", "restore", "load backup")
        ),

        // Accounts & Services
        SettingEntry(
            key = "accounts",
            categoryId = "services",
            titleRes = R.string.settings_category_accounts_title,
            descriptionRes = R.string.settings_category_accounts_subtitle,
            route = Screen.Accounts.route,
            keywords = listOf("accounts", "google drive", "cloud")
        ),

        // Device information
        SettingEntry(
            key = "device_capabilities",
            categoryId = "device_capabilities",
            titleRes = R.string.settings_category_device_capabilities_title,
            descriptionRes = R.string.settings_category_device_capabilities_subtitle,
            route = Screen.DeviceCapabilities.route,
            keywords = listOf("device capabilities", "hardware decoders", "storage specs", "codecs")
        ),

        // Visual widgets
        SettingEntry(
            key = "widget_background",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_background_title,
            descriptionRes = R.string.settings_widget_background_subtitle,
            keywords = listOf("widget", "background", "transparent", "solid", "outline", "home screen")
        ),
        SettingEntry(
            key = "widget_corner",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_corner_title,
            keywords = listOf("widget", "corner", "radius", "rounded", "shape")
        ),
        SettingEntry(
            key = "widget_accent",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_accent_title,
            descriptionRes = R.string.settings_widget_accent_subtitle,
            keywords = listOf("widget", "accent", "colour", "color", "material you", "album art")
        ),
        SettingEntry(
            key = "turntable_spin_mode",
            categoryId = "widgets",
            titleRes = R.string.settings_turntable_spin_mode_title,
            keywords = listOf("turntable", "vinyl", "record", "spin", "rotate", "animation", "battery")
        ),
        SettingEntry(
            key = "turntable_spin_speed",
            categoryId = "widgets",
            titleRes = R.string.settings_turntable_spin_speed_title,
            keywords = listOf("turntable", "spin", "speed", "rpm", "fast", "slow")
        ),
        SettingEntry(
            key = "turntable_direction",
            categoryId = "widgets",
            titleRes = R.string.settings_turntable_direction_title,
            keywords = listOf("turntable", "direction", "clockwise", "anticlockwise", "reverse")
        ),
        SettingEntry(
            key = "turntable_disc_size",
            categoryId = "widgets",
            titleRes = R.string.settings_turntable_disc_scale_title,
            keywords = listOf("turntable", "record", "disc", "size", "scale", "bigger", "smaller")
        ),
        SettingEntry(
            key = "turntable_label_size",
            categoryId = "widgets",
            titleRes = R.string.settings_turntable_label_scale_title,
            keywords = listOf("turntable", "album art", "label", "size", "cover")
        ),
        SettingEntry(
            key = "turntable_badges",
            categoryId = "widgets",
            titleRes = R.string.settings_turntable_badges_title,
            descriptionRes = R.string.settings_turntable_badges_subtitle,
            keywords = listOf("turntable", "buttons", "bubbles", "play", "favourite", "favorite")
        ),
        SettingEntry(
            key = "turntable_tonearm",
            categoryId = "widgets",
            titleRes = R.string.settings_turntable_tonearm_title,
            keywords = listOf("turntable", "tonearm", "arm", "needle", "stylus")
        ),
        SettingEntry(
            key = "turntable_grooves",
            categoryId = "widgets",
            titleRes = R.string.settings_turntable_grooves_title,
            keywords = listOf("turntable", "grooves", "rings", "vinyl", "texture")
        ),
        SettingEntry(
            key = "turntable_sheen",
            categoryId = "widgets",
            titleRes = R.string.settings_turntable_sheen_title,
            keywords = listOf("turntable", "sheen", "highlight", "gloss", "shine", "light")
        ),
        SettingEntry(
            key = "widget_show_title",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_show_title,
            keywords = listOf("widget", "song title", "text", "show")
        ),
        SettingEntry(
            key = "widget_show_artist",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_show_artist,
            keywords = listOf("widget", "artist", "text", "show")
        ),
        SettingEntry(
            key = "widget_show_prev_next",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_show_prev_next,
            keywords = listOf("widget", "previous", "next", "skip", "controls", "buttons")
        ),
        SettingEntry(
            key = "widget_show_shuffle",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_show_shuffle,
            keywords = listOf("widget", "shuffle", "controls", "buttons")
        ),
        SettingEntry(
            key = "widget_show_repeat",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_show_repeat,
            keywords = listOf("widget", "repeat", "loop", "controls", "buttons")
        ),
        SettingEntry(
            key = "widget_show_favorite",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_show_favorite,
            keywords = listOf("widget", "favourite", "favorite", "like", "heart")
        ),
        SettingEntry(
            key = "widget_progress",
            categoryId = "widgets",
            titleRes = R.string.settings_widget_progress_title,
            keywords = listOf("widget", "progress", "bar", "wavy", "seek")
        ),

        // Developer
        SettingEntry(
            key = "dev_test_setup",
            categoryId = "developer",
            titleRes = R.string.settings_test_setup_title,
            descriptionRes = R.string.settings_test_setup_subtitle,
            keywords = listOf("setup", "onboarding", "test setup", "first run", "wizard")
        ),
        SettingEntry(
            key = "dev_daily_mix",
            categoryId = "developer",
            titleRes = R.string.settings_force_daily_mix_title,
            descriptionRes = R.string.settings_force_daily_mix_subtitle,
            keywords = listOf("regenerate daily mix", "recommendations", "mix")
        ),
        SettingEntry(
            key = "dev_stats",
            categoryId = "developer",
            titleRes = R.string.settings_force_stats_title,
            descriptionRes = R.string.settings_force_stats_subtitle,
            keywords = listOf("recalculate stats", "clear stats cache")
        ),
        SettingEntry(
            key = "dev_palette",
            categoryId = "developer",
            titleRes = R.string.settings_force_palette_title,
            descriptionRes = R.string.settings_force_palette_subtitle,
            keywords = listOf("regenerate palettes", "album colors")
        ),
        SettingEntry(
            key = "dev_trigger_crash",
            categoryId = "developer",
            titleRes = R.string.settings_trigger_crash_title,
            descriptionRes = R.string.settings_trigger_crash_subtitle,
            keywords = listOf("crash app", "test crash", "diagnostic")
        ),

        // Added 2026-09-23: settings that were on screen but missing from search.
        SettingEntry(
            key = "hide_status_bar",
            categoryId = "general",
            titleRes = R.string.settings_hide_status_bar_title,
            descriptionRes = R.string.settings_hide_status_bar_subtitle,
            keywords = listOf("full screen", "fullscreen", "status bar", "immersive", "hide", "clock", "notifications")
        ),
        SettingEntry(
            key = "hide_navigation_bar",
            categoryId = "general",
            titleRes = R.string.settings_hide_gesture_bar_title,
            descriptionRes = R.string.settings_hide_gesture_bar_subtitle,
            keywords = listOf("full screen", "fullscreen", "gesture bar", "navigation bar", "system bar", "hide", "immersive")
        ),
        SettingEntry(
            key = "ambient_suggestions",
            categoryId = "playback",
            titleRes = R.string.settings_ambient_suggestions_title,
            descriptionRes = R.string.settings_ambient_suggestions_subtitle,
            keywords = listOf("ambient", "listen", "voice", "microphone", "song mentions", "suggestions", "speech")
        ),
        SettingEntry(
            key = "ambient_chime",
            categoryId = "playback",
            titleRes = R.string.settings_ambient_chime_title,
            descriptionRes = R.string.settings_ambient_chime_subtitle,
            keywords = listOf("chime", "sound", "tone", "ambient", "detection")
        ),
        SettingEntry(
            key = "listenbrainz_scrobbling",
            categoryId = "services",
            titleRes = R.string.settings_listenbrainz_title,
            descriptionRes = R.string.settings_listenbrainz_off,
            keywords = listOf("scrobble", "scrobbling", "listenbrainz", "last.fm", "lastfm", "history", "token")
        ),
        SettingEntry(
            key = "lyrics_split_face",
            categoryId = "lyrics",
            titleRes = R.string.settings_lyrics_split_face_title,
            descriptionRes = R.string.settings_lyrics_split_face_subtitle,
            keywords = listOf("face to face", "split", "share", "both sides", "upside down", "karaoke", "table")
        ),
        SettingEntry(
            key = "camera_cutout_island_overlay",
            categoryId = "appearance",
            titleRes = R.string.settings_camera_island_title,
            descriptionRes = R.string.settings_camera_island_subtitle,
            keywords = listOf("island", "dynamic island", "camera", "cutout", "punch hole", "overlay", "hud", "floating lyrics")
        ),
        SettingEntry(
            key = "camera_cutout_island_tap_access",
            categoryId = "appearance",
            titleRes = R.string.settings_camera_island_tap_title,
            keywords = listOf("island", "tap", "accessibility", "camera hole", "touch")
        ),
        SettingEntry(
            key = "download_all_liked_songs",
            categoryId = "library",
            titleRes = R.string.settings_download_liked_title,
            keywords = listOf("download", "offline", "liked", "favorites", "save", "all songs")
        ),
        SettingEntry(
            key = "lyrics_song_structure",
            categoryId = "lyrics",
            titleRes = R.string.settings_lyrics_song_structure_title,
            descriptionRes = R.string.settings_lyrics_song_structure_subtitle,
            keywords = listOf("structure", "verse", "chorus", "intro", "sections")
        ),
        SettingEntry(
            key = "liked_downloads_wifi_only",
            categoryId = "library",
            titleRes = R.string.settings_download_liked_wifi_only_title,
            descriptionRes = R.string.settings_download_liked_wifi_only_subtitle,
            keywords = listOf("wifi", "wi-fi", "mobile data", "download", "liked")
        )
    )

    /** Entries indexed by [SettingEntry.key]; keys are unique (enforced by test). */
    val byKey: Map<String, SettingEntry> = entries.associateBy { it.key }

    /** Entries rendered inside the given category screen, in declaration order. */
    fun forCategory(categoryId: String): List<SettingEntry> =
        entries.filter { it.categoryId == categoryId && it.route == null }
}
