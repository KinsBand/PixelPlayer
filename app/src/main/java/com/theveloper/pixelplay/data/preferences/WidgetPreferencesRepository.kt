package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the per-widget customisation exposed by the Visual Widgets settings screen.
 *
 * This sits on the app's shared [DataStore] rather than on a file of its own so that the
 * existing preference backup and restore path picks it up without changes.
 *
 * Keys are namespaced per [WidgetKind] (`widget_<kind>_<field>_v1`) so that placing two
 * different widgets does not make them share a look. Turntable-specific options are stored
 * once, unprefixed, because there is only one turntable design.
 */
@Singleton
class WidgetPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private object Keys {
        fun backgroundStyle(kind: WidgetKind) =
            stringPreferencesKey("widget_${kind.storageKey}_background_v1")

        fun cornerRadius(kind: WidgetKind) =
            intPreferencesKey("widget_${kind.storageKey}_corner_v1")

        fun accentSource(kind: WidgetKind) =
            stringPreferencesKey("widget_${kind.storageKey}_accent_v1")

        fun fixedAccent(kind: WidgetKind) =
            intPreferencesKey("widget_${kind.storageKey}_accent_color_v1")

        fun showTitle(kind: WidgetKind) =
            booleanPreferencesKey("widget_${kind.storageKey}_show_title_v1")

        fun showArtist(kind: WidgetKind) =
            booleanPreferencesKey("widget_${kind.storageKey}_show_artist_v1")

        fun showPrevNext(kind: WidgetKind) =
            booleanPreferencesKey("widget_${kind.storageKey}_show_prev_next_v1")

        fun showShuffle(kind: WidgetKind) =
            booleanPreferencesKey("widget_${kind.storageKey}_show_shuffle_v1")

        fun showRepeat(kind: WidgetKind) =
            booleanPreferencesKey("widget_${kind.storageKey}_show_repeat_v1")

        fun showFavorite(kind: WidgetKind) =
            booleanPreferencesKey("widget_${kind.storageKey}_show_favorite_v1")

        fun progressStyle(kind: WidgetKind) =
            stringPreferencesKey("widget_${kind.storageKey}_progress_v1")

        val TURNTABLE_DISC_SCALE = floatPreferencesKey("widget_turntable_disc_scale_v1")
        val TURNTABLE_LABEL_SCALE = floatPreferencesKey("widget_turntable_label_scale_v1")
        val TURNTABLE_SPIN_MODE = stringPreferencesKey("widget_turntable_spin_mode_v1")
        val TURNTABLE_SPIN_RPM = floatPreferencesKey("widget_turntable_spin_rpm_v1")
        val TURNTABLE_CLOCKWISE = booleanPreferencesKey("widget_turntable_clockwise_v1")
        val TURNTABLE_SHOW_BADGES = booleanPreferencesKey("widget_turntable_badges_v1")
        val TURNTABLE_SHOW_TONEARM = booleanPreferencesKey("widget_turntable_tonearm_v1")
        val TURNTABLE_SHOW_GROOVES = booleanPreferencesKey("widget_turntable_grooves_v1")
        val TURNTABLE_SHOW_SHEEN = booleanPreferencesKey("widget_turntable_sheen_v1")

        /** Bumped whenever anything above changes, so widgets know to drop render caches. */
        val REVISION = intPreferencesKey("widget_customization_revision_v1")
    }

    /**
     * Defaults differ per widget: the bar has no room for two lines of text, and the
     * turntable is designed to sit on the wallpaper rather than on a filled card.
     */
    private fun defaultsFor(kind: WidgetKind): WidgetConfig = when (kind) {
        WidgetKind.TURNTABLE -> WidgetConfig(
            kind = kind,
            appearance = WidgetAppearance(
                backgroundStyle = WidgetBackgroundStyle.TRANSPARENT,
                cornerRadiusDp = 0,
            ),
            content = WidgetContentOptions(
                showTitle = false,
                showArtist = false,
                showPrevNext = false,
                progressStyle = WidgetProgressStyle.NONE,
            ),
        )

        WidgetKind.BAR_4X1 -> WidgetConfig(
            kind = kind,
            content = WidgetContentOptions(
                showArtist = false,
                progressStyle = WidgetProgressStyle.NONE,
            ),
        )

        WidgetKind.GRID_2X2 -> WidgetConfig(
            kind = kind,
            appearance = WidgetAppearance(cornerRadiusDp = 28),
            content = WidgetContentOptions(
                showTitle = false,
                showArtist = false,
                progressStyle = WidgetProgressStyle.NONE,
            ),
        )

        else -> WidgetConfig(kind = kind)
    }

    /** Emits the current configuration for one widget kind. */
    fun configFlow(kind: WidgetKind): Flow<WidgetConfig> =
        dataStore.data.map { it.readConfig(kind) }.distinctUntilChanged()

    /** Emits every kind's configuration together, for the settings screen and previews. */
    val allConfigsFlow: Flow<Map<WidgetKind, WidgetConfig>> =
        dataStore.data.map { prefs ->
            WidgetKind.entries.associateWith { prefs.readConfig(it) }
        }.distinctUntilChanged()

    /**
     * Increments on every write. Widget renderers key their bitmap caches on this so a
     * styling change invalidates them without needing to compare whole config objects.
     */
    val revisionFlow: Flow<Int> =
        dataStore.data.map { it[Keys.REVISION] ?: 0 }.distinctUntilChanged()

    suspend fun readConfigOnce(kind: WidgetKind): WidgetConfig =
        dataStore.data.first().readConfig(kind)

    // -------------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------------

    suspend fun setBackgroundStyle(kind: WidgetKind, style: WidgetBackgroundStyle) =
        write { it[Keys.backgroundStyle(kind)] = style.storageKey }

    suspend fun setCornerRadius(kind: WidgetKind, radiusDp: Int) = write {
        it[Keys.cornerRadius(kind)] = radiusDp.coerceIn(
            WidgetAppearance.MIN_CORNER_RADIUS_DP,
            WidgetAppearance.MAX_CORNER_RADIUS_DP,
        )
    }

    suspend fun setAccentSource(kind: WidgetKind, source: WidgetAccentSource) =
        write { it[Keys.accentSource(kind)] = source.storageKey }

    suspend fun setFixedAccentColor(kind: WidgetKind, color: Int) =
        write { it[Keys.fixedAccent(kind)] = color }

    suspend fun setShowTitle(kind: WidgetKind, show: Boolean) =
        write { it[Keys.showTitle(kind)] = show }

    suspend fun setShowArtist(kind: WidgetKind, show: Boolean) =
        write { it[Keys.showArtist(kind)] = show }

    suspend fun setShowPrevNext(kind: WidgetKind, show: Boolean) =
        write { it[Keys.showPrevNext(kind)] = show }

    suspend fun setShowShuffle(kind: WidgetKind, show: Boolean) =
        write { it[Keys.showShuffle(kind)] = show }

    suspend fun setShowRepeat(kind: WidgetKind, show: Boolean) =
        write { it[Keys.showRepeat(kind)] = show }

    suspend fun setShowFavorite(kind: WidgetKind, show: Boolean) =
        write { it[Keys.showFavorite(kind)] = show }

    suspend fun setProgressStyle(kind: WidgetKind, style: WidgetProgressStyle) =
        write { it[Keys.progressStyle(kind)] = style.storageKey }

    suspend fun setDiscScale(scale: Float) = write {
        it[Keys.TURNTABLE_DISC_SCALE] =
            scale.coerceIn(TurntableOptions.MIN_DISC_SCALE, TurntableOptions.MAX_DISC_SCALE)
    }

    suspend fun setLabelScale(scale: Float) = write {
        it[Keys.TURNTABLE_LABEL_SCALE] =
            scale.coerceIn(TurntableOptions.MIN_LABEL_SCALE, TurntableOptions.MAX_LABEL_SCALE)
    }

    suspend fun setSpinMode(mode: WidgetSpinMode) =
        write { it[Keys.TURNTABLE_SPIN_MODE] = mode.storageKey }

    suspend fun setSpinRpm(rpm: Float) = write {
        it[Keys.TURNTABLE_SPIN_RPM] =
            rpm.coerceIn(TurntableOptions.MIN_SPIN_RPM, TurntableOptions.MAX_SPIN_RPM)
    }

    suspend fun setClockwise(clockwise: Boolean) =
        write { it[Keys.TURNTABLE_CLOCKWISE] = clockwise }

    suspend fun setShowBadges(show: Boolean) =
        write { it[Keys.TURNTABLE_SHOW_BADGES] = show }

    suspend fun setShowTonearm(show: Boolean) =
        write { it[Keys.TURNTABLE_SHOW_TONEARM] = show }

    suspend fun setShowGrooves(show: Boolean) =
        write { it[Keys.TURNTABLE_SHOW_GROOVES] = show }

    suspend fun setShowSheen(show: Boolean) =
        write { it[Keys.TURNTABLE_SHOW_SHEEN] = show }

    /** Restores one widget kind to the defaults in [defaultsFor]. */
    suspend fun resetKind(kind: WidgetKind) = write { prefs ->
        prefs.remove(Keys.backgroundStyle(kind))
        prefs.remove(Keys.cornerRadius(kind))
        prefs.remove(Keys.accentSource(kind))
        prefs.remove(Keys.fixedAccent(kind))
        prefs.remove(Keys.showTitle(kind))
        prefs.remove(Keys.showArtist(kind))
        prefs.remove(Keys.showPrevNext(kind))
        prefs.remove(Keys.showShuffle(kind))
        prefs.remove(Keys.showRepeat(kind))
        prefs.remove(Keys.showFavorite(kind))
        prefs.remove(Keys.progressStyle(kind))
        if (kind == WidgetKind.TURNTABLE) {
            prefs.remove(Keys.TURNTABLE_DISC_SCALE)
            prefs.remove(Keys.TURNTABLE_LABEL_SCALE)
            prefs.remove(Keys.TURNTABLE_SPIN_MODE)
            prefs.remove(Keys.TURNTABLE_SPIN_RPM)
            prefs.remove(Keys.TURNTABLE_CLOCKWISE)
            prefs.remove(Keys.TURNTABLE_SHOW_BADGES)
            prefs.remove(Keys.TURNTABLE_SHOW_TONEARM)
            prefs.remove(Keys.TURNTABLE_SHOW_GROOVES)
            prefs.remove(Keys.TURNTABLE_SHOW_SHEEN)
        }
    }

    private suspend inline fun write(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { prefs ->
            block(prefs)
            prefs[Keys.REVISION] = (prefs[Keys.REVISION] ?: 0) + 1
        }
    }

    // -------------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------------

    private fun Preferences.readConfig(kind: WidgetKind): WidgetConfig {
        val defaults = defaultsFor(kind)
        return WidgetConfig(
            kind = kind,
            appearance = WidgetAppearance(
                backgroundStyle = this[Keys.backgroundStyle(kind)]
                    ?.let(WidgetBackgroundStyle::fromStorageKey)
                    ?: defaults.appearance.backgroundStyle,
                cornerRadiusDp = this[Keys.cornerRadius(kind)]
                    ?: defaults.appearance.cornerRadiusDp,
                accentSource = this[Keys.accentSource(kind)]
                    ?.let(WidgetAccentSource::fromStorageKey)
                    ?: defaults.appearance.accentSource,
                fixedAccentColor = this[Keys.fixedAccent(kind)]
                    ?: defaults.appearance.fixedAccentColor,
            ),
            content = WidgetContentOptions(
                showTitle = this[Keys.showTitle(kind)] ?: defaults.content.showTitle,
                showArtist = this[Keys.showArtist(kind)] ?: defaults.content.showArtist,
                showPrevNext = this[Keys.showPrevNext(kind)] ?: defaults.content.showPrevNext,
                showShuffle = this[Keys.showShuffle(kind)] ?: defaults.content.showShuffle,
                showRepeat = this[Keys.showRepeat(kind)] ?: defaults.content.showRepeat,
                showFavorite = this[Keys.showFavorite(kind)] ?: defaults.content.showFavorite,
                progressStyle = this[Keys.progressStyle(kind)]
                    ?.let(WidgetProgressStyle::fromStorageKey)
                    ?: defaults.content.progressStyle,
            ),
            turntable = TurntableOptions(
                discScale = this[Keys.TURNTABLE_DISC_SCALE]
                    ?: TurntableOptions.DEFAULT_DISC_SCALE,
                labelScale = this[Keys.TURNTABLE_LABEL_SCALE]
                    ?: TurntableOptions.DEFAULT_LABEL_SCALE,
                spinMode = this[Keys.TURNTABLE_SPIN_MODE]
                    ?.let(WidgetSpinMode::fromStorageKey)
                    ?: WidgetSpinMode.default,
                spinRpm = this[Keys.TURNTABLE_SPIN_RPM] ?: TurntableOptions.DEFAULT_SPIN_RPM,
                clockwise = this[Keys.TURNTABLE_CLOCKWISE] ?: true,
                showBadges = this[Keys.TURNTABLE_SHOW_BADGES] ?: true,
                showTonearm = this[Keys.TURNTABLE_SHOW_TONEARM] ?: false,
                showGrooves = this[Keys.TURNTABLE_SHOW_GROOVES] ?: true,
                showSheen = this[Keys.TURNTABLE_SHOW_SHEEN] ?: true,
            ),
        )
    }
}
