package com.theveloper.pixelplay.data.preferences

/**
 * Value types behind the Visual Widgets settings screen.
 *
 * Everything here is plain data — no Android, no Compose, no resources — so the same
 * definitions can be read by the settings UI, by the Glance widgets and by unit tests.
 * Human-readable labels live in `strings_widget.xml` and are resolved by the UI via
 * [labelRes]; the [storageKey] is what actually goes into DataStore and must never change
 * once shipped.
 */

/** The widgets a user can place, and therefore customise, one by one. */
enum class WidgetKind(
    val storageKey: String,
    val labelRes: Int,
) {
    ADAPTIVE("adaptive", com.theveloper.pixelplay.R.string.widget_kind_adaptive),
    BAR_4X1("bar_4x1", com.theveloper.pixelplay.R.string.widget_kind_bar),
    CONTROL_4X2("control_4x2", com.theveloper.pixelplay.R.string.widget_kind_control),
    GRID_2X2("grid_2x2", com.theveloper.pixelplay.R.string.widget_kind_grid),
    TURNTABLE("turntable", com.theveloper.pixelplay.R.string.widget_kind_turntable);

    companion object {
        fun fromStorageKey(value: String?): WidgetKind =
            entries.firstOrNull { it.storageKey == value } ?: ADAPTIVE
    }
}

/** How a widget fills the space behind its content. */
enum class WidgetBackgroundStyle(
    val storageKey: String,
    val labelRes: Int,
) {
    /** A flat surface colour — the current behaviour of every shipped widget. */
    SOLID("solid", com.theveloper.pixelplay.R.string.widget_background_solid),

    /** Nothing behind the content, so the wallpaper shows through. */
    TRANSPARENT("transparent", com.theveloper.pixelplay.R.string.widget_background_transparent),

    /** A surface-coloured outline over a transparent body. */
    OUTLINED("outlined", com.theveloper.pixelplay.R.string.widget_background_outlined);

    companion object {
        val default: WidgetBackgroundStyle = SOLID

        fun fromStorageKey(value: String?): WidgetBackgroundStyle =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}

/** Where a widget takes its accent colour from. */
enum class WidgetAccentSource(
    val storageKey: String,
    val labelRes: Int,
) {
    /** Follow the system's dynamic colour, via `GlanceTheme`. */
    MATERIAL_YOU("material_you", com.theveloper.pixelplay.R.string.widget_accent_material_you),

    /** Follow the palette extracted from the current album art. */
    ALBUM_ART("album_art", com.theveloper.pixelplay.R.string.widget_accent_album_art),

    /** Always use [WidgetAppearance.fixedAccentColor]. */
    FIXED("fixed", com.theveloper.pixelplay.R.string.widget_accent_fixed);

    companion object {
        val default: WidgetAccentSource = ALBUM_ART

        fun fromStorageKey(value: String?): WidgetAccentSource =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}

/**
 * How the turntable's record turns.
 *
 * App widgets cannot animate: every visible change is a fresh `RemoteViews` pushed to the
 * launcher. These modes are therefore a straight trade of smoothness against how often the
 * app has to wake up and re-render.
 */
enum class WidgetSpinMode(
    val storageKey: String,
    val labelRes: Int,
    val descriptionRes: Int,
) {
    /**
     * Frames pushed continuously while audio plays, and not at all otherwise.
     * See `TurntableSpinController` for the frame budget.
     */
    SMOOTH("smooth", com.theveloper.pixelplay.R.string.widget_spin_smooth, com.theveloper.pixelplay.R.string.widget_spin_smooth_desc),

    /** One step every couple of seconds; reads as turning rather than spinning. */
    TICK("tick", com.theveloper.pixelplay.R.string.widget_spin_tick, com.theveloper.pixelplay.R.string.widget_spin_tick_desc),

    /** No extra updates at all: the angle is derived from playback position. */
    POSITION("position", com.theveloper.pixelplay.R.string.widget_spin_position, com.theveloper.pixelplay.R.string.widget_spin_position_desc),

    /** The record never turns. */
    OFF("off", com.theveloper.pixelplay.R.string.widget_spin_off, com.theveloper.pixelplay.R.string.widget_spin_off_desc);

    companion object {
        val default: WidgetSpinMode = SMOOTH

        fun fromStorageKey(value: String?): WidgetSpinMode =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}

/** Optional progress treatment along the bottom of the content widgets. */
enum class WidgetProgressStyle(
    val storageKey: String,
    val labelRes: Int,
) {
    NONE("none", com.theveloper.pixelplay.R.string.widget_progress_none),
    LINE("line", com.theveloper.pixelplay.R.string.widget_progress_line),
    WAVY("wavy", com.theveloper.pixelplay.R.string.widget_progress_wavy);

    companion object {
        val default: WidgetProgressStyle = LINE

        fun fromStorageKey(value: String?): WidgetProgressStyle =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}

/** Appearance shared by every widget kind. */
data class WidgetAppearance(
    val backgroundStyle: WidgetBackgroundStyle = WidgetBackgroundStyle.default,
    val cornerRadiusDp: Int = DEFAULT_CORNER_RADIUS_DP,
    val accentSource: WidgetAccentSource = WidgetAccentSource.default,
    val fixedAccentColor: Int = DEFAULT_FIXED_ACCENT,
) {
    companion object {
        const val DEFAULT_CORNER_RADIUS_DP = 28
        const val MIN_CORNER_RADIUS_DP = 0
        const val MAX_CORNER_RADIUS_DP = 48

        /** The deep indigo the turntable mock is built around. */
        const val DEFAULT_FIXED_ACCENT = 0xFF1B0A33.toInt()
    }
}

/** Which transport controls and text a widget shows. */
data class WidgetContentOptions(
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showPrevNext: Boolean = true,
    val showShuffle: Boolean = false,
    val showRepeat: Boolean = false,
    val showFavorite: Boolean = true,
    val progressStyle: WidgetProgressStyle = WidgetProgressStyle.default,
)

/** Everything specific to the turntable widget. */
data class TurntableOptions(
    /** Disc diameter as a fraction of the widget's shorter edge. */
    val discScale: Float = DEFAULT_DISC_SCALE,
    /** Label (album art) diameter as a fraction of the disc diameter. */
    val labelScale: Float = DEFAULT_LABEL_SCALE,
    val spinMode: WidgetSpinMode = WidgetSpinMode.default,
    /** Cosmetic, not literal: 33⅓ rpm is far too fast to read at widget frame rates. */
    val spinRpm: Float = DEFAULT_SPIN_RPM,
    val clockwise: Boolean = true,
    /** The floating play and favourite bubbles that sit on the disc's edge. */
    val showBadges: Boolean = true,
    val showTonearm: Boolean = false,
    val showGrooves: Boolean = true,
    val showSheen: Boolean = true,
) {
    /** Degrees per millisecond, signed by direction. Zero when the record is static. */
    val degreesPerMs: Float
        get() {
            if (spinMode == WidgetSpinMode.OFF) return 0f
            val magnitude = spinRpm * 360f / 60_000f
            return if (clockwise) magnitude else -magnitude
        }

    companion object {
        const val DEFAULT_DISC_SCALE = 0.82f
        const val MIN_DISC_SCALE = 0.55f
        const val MAX_DISC_SCALE = 1.0f

        const val DEFAULT_LABEL_SCALE = 0.34f
        const val MIN_LABEL_SCALE = 0.18f
        const val MAX_LABEL_SCALE = 0.58f

        const val DEFAULT_SPIN_RPM = 8f
        const val MIN_SPIN_RPM = 2f
        const val MAX_SPIN_RPM = 24f
    }
}

/** The full customisation state for one widget kind. */
data class WidgetConfig(
    val kind: WidgetKind,
    val appearance: WidgetAppearance = WidgetAppearance(),
    val content: WidgetContentOptions = WidgetContentOptions(),
    val turntable: TurntableOptions = TurntableOptions(),
)
