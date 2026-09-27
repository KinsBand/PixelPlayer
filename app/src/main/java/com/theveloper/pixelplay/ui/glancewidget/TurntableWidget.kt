package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.preferences.TurntableOptions
import com.theveloper.pixelplay.data.preferences.WidgetConfig
import com.theveloper.pixelplay.data.preferences.WidgetContentOptions
import com.theveloper.pixelplay.data.preferences.WidgetKind
import timber.log.Timber
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A vinyl record on the home screen: a dark disc with the current album art as its label,
 * turning while the music plays, with a play and a favourite bubble resting on its edge.
 *
 * The disc is a bitmap composed by [TurntableRenderer], because `RemoteViews` cannot rotate a
 * view; [TurntableSpinController] decides what angle it is drawn at and how often a new frame
 * is pushed. The bubbles are real Glance elements rather than part of that bitmap so they stay
 * tappable and stay upright while the record turns beneath them.
 *
 * Sizing is fully continuous rather than bucketed: `SizeMode.Exact` gives the real cell size,
 * and every dimension below is a fraction of the widget's shorter edge, so the same layout
 * holds from a 1x1 cell to a tablet's 6x4. The only thing that changes with shape is whether
 * there is room to put text and transport controls beside the disc.
 */
class TurntableWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read once per update, outside composition. Shared with the other four widgets so
        // they all resolve their settings the same way.
        val config = loadWidgetConfig(context, WidgetKind.TURNTABLE)

        // Placing a widget while music is already playing has to start the spin loop too,
        // not just a play/pause event, so every update re-asserts what the loop should be
        // doing. Both start and stop are idempotent.
        runCatching {
            val state = getAppWidgetState(context, PlayerInfoStateDefinition, id)
            TurntableSpinController.onPlaybackStateChanged(
                context = context,
                isPlaying = state.isPlaying,
                options = config.turntable,
            )
        }.onFailure { Timber.tag(TAG).d(it, "Could not reconcile turntable spin state") }

        provideContent {
            val playerInfo = currentState<PlayerInfo>()
            GlanceTheme {
                TurntableContent(
                    context = context,
                    playerInfo = playerInfo,
                    config = config,
                    size = LocalSize.current,
                )
            }
        }
    }

    @Composable
    private fun TurntableContent(
        context: Context,
        playerInfo: PlayerInfo,
        config: WidgetConfig,
        size: DpSize,
    ) {
        val options = config.turntable
        val content = config.content
        val palette = TurntablePalette.resolve(context, config.appearance, playerInfo)

        // Aspect decides layout, the shorter edge decides scale. A 4x1 strip gets the disc
        // on one side and text on the other; a square cell gets the disc and nothing else.
        val aspect = if (size.height.value > 0f) size.width.value / size.height.value else 1f
        val wantsSideContent = content.showTitle || content.showArtist || content.showPrevNext

        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                aspect >= WIDE_ASPECT && wantsSideContent -> WideLayout(
                    context = context,
                    playerInfo = playerInfo,
                    options = options,
                    content = content,
                    palette = palette,
                    size = size,
                )

                aspect <= TALL_ASPECT && wantsSideContent -> TallLayout(
                    context = context,
                    playerInfo = playerInfo,
                    options = options,
                    content = content,
                    palette = palette,
                    size = size,
                )

                else -> TurntableStage(
                    context = context,
                    playerInfo = playerInfo,
                    options = options,
                    content = content,
                    palette = palette,
                    stage = min(size.width, size.height),
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Layouts
    // ---------------------------------------------------------------------------------

    /** The disc on its own, filling the cell. This is the default and the reference design. */
    @Composable
    private fun TurntableStage(
        context: Context,
        playerInfo: PlayerInfo,
        options: TurntableOptions,
        content: WidgetContentOptions,
        palette: TurntablePalette,
        stage: Dp,
    ) {
        val discDp = stage * options.discScale.coerceIn(
            TurntableOptions.MIN_DISC_SCALE,
            TurntableOptions.MAX_DISC_SCALE,
        )

        Box(
            modifier = GlanceModifier.size(stage),
            contentAlignment = Alignment.Center,
        ) {
            Disc(
                context = context,
                playerInfo = playerInfo,
                options = options,
                palette = palette,
                discDp = discDp,
            )

            if (options.showBadges) {
                val favouriteDp = discDp * BADGE_FRACTION
                val playDp = discDp * PLAY_BADGE_FRACTION

                // Park each bubble on the disc's 45° edge: half the stage, minus the disc's
                // radius projected onto one axis, minus half the bubble.
                val favouriteInset = edgeInset(stage, discDp, favouriteDp)
                val playInset = edgeInset(stage, discDp, playDp)

                if (content.showFavorite) {
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        FavouriteBadge(
                            context = context,
                            isFavorite = playerInfo.isFavorite,
                            palette = palette,
                            diameter = favouriteDp,
                            modifier = GlanceModifier.padding(
                                top = favouriteInset,
                                end = favouriteInset,
                            ),
                        )
                    }
                }

                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    PlayBadge(
                        context = context,
                        isPlaying = playerInfo.isPlaying,
                        palette = palette,
                        side = playDp,
                        modifier = GlanceModifier.padding(
                            bottom = playInset,
                            start = playInset,
                        ),
                    )
                }
            }
        }
    }

    /** Disc on the left, text and transport on the right. Used on wide, short cells. */
    @Composable
    private fun WideLayout(
        context: Context,
        playerInfo: PlayerInfo,
        options: TurntableOptions,
        content: WidgetContentOptions,
        palette: TurntablePalette,
        size: DpSize,
    ) {
        val stage = size.height
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TurntableStage(
                context = context,
                playerInfo = playerInfo,
                options = options,
                // The bubbles would collide with the text column at this shape.
                content = content.copy(showFavorite = false),
                palette = palette,
                stage = stage,
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackText(
                    context = context,
                    playerInfo = playerInfo,
                    content = content,
                    palette = palette,
                    stage = stage,
                )
                if (content.showPrevNext) {
                    Spacer(GlanceModifier.height(6.dp))
                    TransportRow(
                        playerInfo = playerInfo,
                        content = content,
                        palette = palette,
                        stage = stage,
                    )
                }
            }
        }
    }

    /** Disc on top, text below. Used on narrow, tall cells. */
    @Composable
    private fun TallLayout(
        context: Context,
        playerInfo: PlayerInfo,
        options: TurntableOptions,
        content: WidgetContentOptions,
        palette: TurntablePalette,
        size: DpSize,
    ) {
        val stage = size.width
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TurntableStage(
                context = context,
                playerInfo = playerInfo,
                options = options,
                content = content,
                palette = palette,
                stage = stage,
            )
            Spacer(GlanceModifier.height(8.dp))
            TrackText(
                context = context,
                playerInfo = playerInfo,
                content = content,
                palette = palette,
                stage = stage,
            )
            if (content.showPrevNext) {
                Spacer(GlanceModifier.height(6.dp))
                TransportRow(
                    playerInfo = playerInfo,
                    content = content,
                    palette = palette,
                    stage = stage,
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Pieces
    // ---------------------------------------------------------------------------------

    @Composable
    private fun Disc(
        context: Context,
        playerInfo: PlayerInfo,
        options: TurntableOptions,
        palette: TurntablePalette,
        discDp: Dp,
    ) {
        val density = context.resources.displayMetrics.density
        val discPx = (discDp.value * density).toInt()
            .coerceIn(1, TurntableRenderer.MAX_RENDER_PX)
        val labelPx = max(1, (discPx * options.labelScale).toInt())

        val angle = TurntableSpinController.currentAngle(options, playerInfo.currentPositionMs)

        val frame = TurntableRenderer.renderFrame(
            spec = TurntableRenderer.DiscSpec(
                sizePx = discPx,
                discColor = palette.disc,
                labelColor = palette.label,
                labelGlyphColor = palette.labelGlyph,
                grooveColor = palette.groove,
                labelFraction = options.labelScale,
                showGrooves = options.showGrooves,
                showSheen = options.showSheen,
                showTonearm = options.showTonearm,
                tonearmColor = palette.tonearm,
                artworkKey = artworkKey(playerInfo),
            ),
            angleDeg = angle,
            artwork = {
                TurntableRenderer.prepareArtwork(
                    source = resolveTurntableArtwork(context, playerInfo, labelPx),
                    labelPx = labelPx,
                )
            },
        )

        val description = context.getString(R.string.widget_turntable_disc_cd)

        Box(
            modifier = GlanceModifier
                .size(discDp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            if (frame != null) {
                Image(
                    provider = ImageProvider(frame),
                    contentDescription = description,
                    modifier = GlanceModifier.size(discDp),
                )
            } else {
                // Rendering can legitimately fail under memory pressure. A plain disc is a
                // better answer than an empty cell.
                Box(
                    modifier = GlanceModifier
                        .size(discDp)
                        .background(Color(palette.disc))
                        .cornerRadius(discDp / 2),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_music_placeholder),
                        contentDescription = description,
                        modifier = GlanceModifier.size(discDp * 0.3f),
                        colorFilter = ColorFilter.tint(ColorProvider(Color(palette.label))),
                    )
                }
            }
        }
    }

    @Composable
    private fun FavouriteBadge(
        context: Context,
        isFavorite: Boolean,
        palette: TurntablePalette,
        diameter: Dp,
        modifier: GlanceModifier = GlanceModifier,
    ) {
        Box(
            modifier = modifier
                .size(diameter)
                .background(Color(palette.favoriteBadgeBackground))
                .cornerRadius(diameter / 2)
                .clickable(
                    actionRunCallback<PlayerControlActionCallback>(
                        actionParametersOf(PlayerActions.key to PlayerActions.FAVORITE),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(
                    if (isFavorite) R.drawable.rounded_favorite_24
                    else R.drawable.round_favorite_border_24,
                ),
                contentDescription = context.getString(
                    if (isFavorite) R.string.widget_turntable_unfavorite_cd
                    else R.string.widget_turntable_favorite_cd,
                ),
                modifier = GlanceModifier.size(diameter * 0.52f),
                colorFilter = ColorFilter.tint(ColorProvider(Color(palette.favoriteBadgeIcon))),
            )
        }
    }

    @Composable
    private fun PlayBadge(
        context: Context,
        isPlaying: Boolean,
        palette: TurntablePalette,
        side: Dp,
        modifier: GlanceModifier = GlanceModifier,
    ) {
        Box(
            modifier = modifier
                .size(side)
                .background(Color(palette.playBadgeBackground))
                .cornerRadius(side * 0.3f)
                .clickable(
                    actionRunCallback<PlayerControlActionCallback>(
                        actionParametersOf(PlayerActions.key to PlayerActions.PLAY_PAUSE),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(
                    if (isPlaying) R.drawable.rounded_pause_filled_24
                    else R.drawable.rounded_play_arrow_filled_24,
                ),
                contentDescription = context.getString(
                    if (isPlaying) R.string.common_pause else R.string.common_play,
                ),
                modifier = GlanceModifier.size(side * 0.5f),
                colorFilter = ColorFilter.tint(ColorProvider(Color(palette.playBadgeIcon))),
            )
        }
    }

    @Composable
    private fun TrackText(
        context: Context,
        playerInfo: PlayerInfo,
        content: WidgetContentOptions,
        palette: TurntablePalette,
        stage: Dp,
    ) {
        if (!content.showTitle && !content.showArtist) return

        val titleSp = (stage.value * 0.14f).coerceIn(11f, 18f).sp
        val artistSp = (stage.value * 0.11f).coerceIn(10f, 15f).sp

        Column {
            if (content.showTitle) {
                Text(
                    text = playerInfo.songTitle.ifEmpty { context.getString(R.string.app_name) },
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(palette.title)),
                        fontSize = titleSp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            if (content.showArtist) {
                Text(
                    text = playerInfo.artistName.ifEmpty {
                        context.getString(R.string.widget_tap_to_open)
                    },
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(palette.artist)),
                        fontSize = artistSp,
                    ),
                )
            }
        }
    }

    @Composable
    private fun TransportRow(
        playerInfo: PlayerInfo,
        content: WidgetContentOptions,
        palette: TurntablePalette,
        stage: Dp,
    ) {
        val button = (stage.value * 0.26f).coerceIn(28f, 44f).dp
        val icon = button * 0.5f
        val corner = button / 2

        Row(verticalAlignment = Alignment.CenterVertically) {
            PreviousButton(
                modifier = GlanceModifier.size(button),
                backgroundColor = ColorProvider(Color(palette.playBadgeBackground)),
                iconColor = ColorProvider(Color(palette.playBadgeIcon)),
                cornerRadius = corner,
                iconSize = icon,
            )
            Spacer(GlanceModifier.width(6.dp))
            PlayPauseButton(
                modifier = GlanceModifier.size(button),
                isPlaying = playerInfo.isPlaying,
                backgroundColor = ColorProvider(Color(palette.playBadgeBackground)),
                iconColor = ColorProvider(Color(palette.playBadgeIcon)),
                cornerRadius = corner,
                iconSize = icon,
            )
            Spacer(GlanceModifier.width(6.dp))
            NextButton(
                modifier = GlanceModifier.size(button),
                backgroundColor = ColorProvider(Color(palette.playBadgeBackground)),
                iconColor = ColorProvider(Color(palette.playBadgeIcon)),
                cornerRadius = corner,
                iconSize = icon,
            )
            if (content.showShuffle) {
                Spacer(GlanceModifier.width(6.dp))
                ShuffleButton(
                    modifier = GlanceModifier.size(button),
                    backgroundColor = ColorProvider(Color(palette.favoriteBadgeBackground)),
                    iconColor = ColorProvider(Color(palette.favoriteBadgeIcon)),
                    cornerRadius = corner,
                )
            }
            if (content.showRepeat) {
                Spacer(GlanceModifier.width(6.dp))
                RepeatButton(
                    modifier = GlanceModifier.size(button),
                    backgroundColor = ColorProvider(Color(palette.favoriteBadgeBackground)),
                    iconRes = when (playerInfo.repeatMode) {
                        1 -> R.drawable.rounded_repeat_one_24
                        else -> R.drawable.rounded_repeat_24
                    },
                    iconColor = ColorProvider(Color(palette.favoriteBadgeIcon)),
                    cornerRadius = corner,
                )
            }
        }
    }

    private companion object {
        private const val TAG = "TurntableWidget"

        /** Width ≥ 1.6× height: room for a text column beside the disc. */
        private const val WIDE_ASPECT = 1.6f

        /** Height ≥ ~1.4× width: room for text under the disc. */
        private const val TALL_ASPECT = 0.7f

        private const val BADGE_FRACTION = 0.26f
        private const val PLAY_BADGE_FRACTION = 0.32f

        /**
         * Distance from the stage's corner at which a bubble of [badge] diameter sits centred
         * on the edge of a [disc]-wide record. Clamped at zero for the degenerate case where
         * the disc fills the whole stage.
         */
        fun edgeInset(stage: Dp, disc: Dp, badge: Dp): Dp {
            val projected = (disc.value / 2f) / sqrt(2f)
            val inset = stage.value / 2f - projected - badge.value / 2f
            return max(0f, inset).dp
        }
    }
}

/**
 * Album art for the label, as a [Bitmap].
 *
 * Mirrors what `AlbumArtImage` does for the other widgets — bytes first, then the URI — but
 * returns the bitmap rather than an `ImageProvider`, because the artwork has to be drawn into
 * the disc rather than shown as its own view.
 */
internal fun resolveTurntableArtwork(
    context: Context,
    playerInfo: PlayerInfo,
    targetPx: Int,
): Bitmap? {
    playerInfo.albumArtBitmapData?.let { data ->
        val key = "turntable:${AlbumArtBitmapCache.getKey(data)}"
        AlbumArtBitmapCache.getBitmap(key)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)

            var sample = 1
            if (bounds.outHeight > targetPx || bounds.outWidth > targetPx) {
                val halfHeight = bounds.outHeight / 2
                val halfWidth = bounds.outWidth / 2
                while (halfHeight / sample >= targetPx && halfWidth / sample >= targetPx) {
                    sample *= 2
                }
            }

            BitmapFactory.decodeByteArray(
                data,
                0,
                data.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inJustDecodeBounds = false
                },
            )?.also { AlbumArtBitmapCache.putBitmap(key, it) }
        } catch (e: Exception) {
            Timber.tag("TurntableWidget").e(e, "Failed to decode turntable artwork bytes")
            null
        }
    }

    val uri = playerInfo.albumArtUri ?: return null
    val key = "turntable:uri:$uri"
    AlbumArtBitmapCache.getBitmap(key)?.let { return it }
    return decodeWidgetAlbumArtBitmap(
        context = context,
        rawUri = uri,
        targetWidthPx = targetPx,
        targetHeightPx = targetPx,
    )?.also { AlbumArtBitmapCache.putBitmap(key, it) }
}

/** Identifies the current artwork so cached discs are dropped when the track changes. */
internal fun artworkKey(playerInfo: PlayerInfo): String =
    playerInfo.albumArtBitmapData?.let { AlbumArtBitmapCache.getKey(it) }
        ?: playerInfo.albumArtUri
        ?: "none"
