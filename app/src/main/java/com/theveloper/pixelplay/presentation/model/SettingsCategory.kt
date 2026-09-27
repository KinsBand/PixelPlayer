package com.theveloper.pixelplay.presentation.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.ui.graphics.vector.ImageVector
import com.theveloper.pixelplay.R

/**
 * Settings pages.
 *
 * The root shows ten rows (see SettingsScreen): General, Appearance, Player & Lyrics,
 * Widgets, Playback, Library, Accounts & Services, AI, Backup and About.
 *
 * [NOW_PLAYING] and [NAVIGATION] are no longer pages of their own. Their settings were
 * folded into [LYRICS] ("Player & Lyrics"), [APPEARANCE] and [GENERAL]. The constants and
 * ids are kept so code, saved deep links and restored back stacks that still name them
 * keep working: [fromId] resolves them to the page that now holds their settings.
 */
enum class SettingsCategory(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
) {
    GENERAL(
        id = "general",
        titleRes = R.string.settings_category_general_title,
        subtitleRes = R.string.settings_category_general_subtitle,
        icon = Icons.Rounded.Settings
    ),
    LIBRARY(
        id = "library",
        titleRes = R.string.settings_category_music_management_title,
        subtitleRes = R.string.settings_category_music_management_subtitle,
        icon = Icons.Rounded.LibraryMusic
    ),
    /** "Player & Lyrics". The id stays "lyrics" so existing links keep working. */
    LYRICS(
        id = "lyrics",
        titleRes = R.string.settings_category_player_lyrics_title,
        subtitleRes = R.string.settings_category_player_lyrics_subtitle,
        iconRes = R.drawable.rounded_lyrics_24
    ),
    APPEARANCE(
        id = "appearance",
        titleRes = R.string.settings_category_appearance_title,
        subtitleRes = R.string.settings_category_appearance_subtitle,
        icon = Icons.Rounded.Palette
    ),
    PLAYBACK(
        id = "playback",
        titleRes = R.string.settings_category_playback_title,
        subtitleRes = R.string.settings_category_playback_subtitle,
        icon = Icons.Rounded.MusicNote // Using MusicNote again or maybe PlayCircle if available
    ),
    /** Merged into [LYRICS]; [fromId] never returns it. */
    NOW_PLAYING(
        id = "now_playing",
        titleRes = R.string.settings_category_now_playing_title,
        subtitleRes = R.string.settings_category_now_playing_subtitle,
        icon = Icons.Rounded.PlayCircle
    ),
    /** Split between [GENERAL] and [APPEARANCE]; [fromId] resolves it to [GENERAL]. */
    NAVIGATION(
        id = "navigation",
        titleRes = R.string.settings_category_navigation_title,
        subtitleRes = R.string.settings_category_navigation_subtitle,
        icon = Icons.Rounded.Explore
    ),
    WIDGETS(
        id = "widgets",
        titleRes = R.string.settings_category_widgets_island_title,
        subtitleRes = R.string.settings_category_widgets_subtitle,
        icon = Icons.Rounded.Widgets
    ),
    SERVICES(
        id = "services",
        titleRes = R.string.settings_category_services_title,
        subtitleRes = R.string.settings_category_services_subtitle,
        icon = Icons.Rounded.AccountCircle
    ),
    AI_INTEGRATION(
        id = "ai",
        titleRes = R.string.settings_category_ai_title,
        subtitleRes = R.string.settings_category_ai_subtitle,
        iconRes = R.drawable.gemini_ai
    ),
    BACKUP_RESTORE(
        id = "backup_restore",
        titleRes = R.string.settings_category_backup_title,
        subtitleRes = R.string.settings_category_backup_subtitle,
        iconRes = R.drawable.rounded_upload_file_24
    ),
    DEVELOPER(
        id = "developer",
        titleRes = R.string.settings_category_developer_title,
        subtitleRes = R.string.settings_category_developer_subtitle,
        icon = Icons.Rounded.DeveloperMode
    ),
    EQUALIZER(
        id = "equalizer",
        titleRes = R.string.settings_category_equalizer_title,
        subtitleRes = R.string.settings_category_equalizer_subtitle,
        icon = Icons.Rounded.GraphicEq
    ),
    DEVICE_CAPABILITIES(
        id = "device_capabilities",
        titleRes = R.string.settings_category_device_capabilities_title,
        subtitleRes = R.string.settings_category_device_capabilities_subtitle,
        icon = Icons.Rounded.DeveloperBoard // Placeholder, maybe Memory or SettingsInputComponent
    ),
    ABOUT(
        id = "about",
        titleRes = R.string.settings_category_about_title,
        subtitleRes = R.string.settings_category_about_subtitle,
        icon = Icons.Rounded.Info
    );

    /**
     * The page that actually renders this category's settings. Only differs for the two
     * categories that were folded into others.
     */
    fun canonical(): SettingsCategory = when (this) {
        NOW_PLAYING -> LYRICS
        NAVIGATION -> GENERAL
        else -> this
    }

    companion object {
        /**
         * Ids that shipped before Appearance/Behavior were split into Appearance,
         * Now Playing and Navigation. Kept so saved deep links, restored back stacks
         * and shortcuts created by older builds still resolve. Safe to drop once those
         * builds are out of circulation.
         */
        private val LEGACY_ID_ALIASES = mapOf(
            "player" to GENERAL,
            "behavior" to GENERAL
        )

        /** Resolves an id (current, merged or legacy) to the page that renders it. */
        fun fromId(id: String): SettingsCategory? =
            (entries.find { it.id == id } ?: LEGACY_ID_ALIASES[id])?.canonical()
    }
}
