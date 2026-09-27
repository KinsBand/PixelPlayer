package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.DpSize
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.preferences.WidgetConfig
import com.theveloper.pixelplay.data.preferences.WidgetKind
import kotlin.text.ifEmpty

class BarWidget4x1 : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = loadWidgetConfig(context, WidgetKind.BAR_4X1)
        provideContent {
            val playerInfo = currentState<PlayerInfo>()
            GlanceTheme {
                BarWidget4x1Content(
                    playerInfo = playerInfo,
                    context = context,
                    config = config,
                    size = LocalSize.current
                )
            }
        }
    }

    @Composable
    private fun BarWidget4x1Content(
        playerInfo: PlayerInfo,
        context: Context,
        config: WidgetConfig,
        size: DpSize
    ) {
        val content = config.content
        val title = playerInfo.songTitle.ifEmpty { context.getString(R.string.app_name) }
        val artist = playerInfo.artistName.ifEmpty { context.getString(R.string.widget_tap_to_open) }
        val isPlaying = playerInfo.isPlaying
        val albumArtBitmapData = playerInfo.albumArtBitmapData
        val albumArtUri = playerInfo.albumArtUri

        val colors = playerInfo.getWidgetColors(config.appearance)

        val albumArtCornerRadius = 16.dp
        val playButtonCornerRadius = if (isPlaying) 16.dp else 20.dp
        val controlButtonCornerRadius = 16.dp

        Box(
            modifier = GlanceModifier
                .widgetSurface(
                    context = context,
                    appearance = config.appearance,
                    colors = colors,
                    outlineColor = outlineColorFor(context, config.appearance, playerInfo),
                    size = size
                )
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.Start
                ) {
                    AlbumArtImage(
                        modifier = GlanceModifier.size(44.dp),
                        bitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        size = 44.dp,
                        context = context,
                        cornerRadius = albumArtCornerRadius
                    )

                    Spacer(GlanceModifier.width(8.dp))

                    // Text is still given the weight when it is switched off, so the controls
                    // stay end-aligned instead of sliding across to meet the album art.
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        if (content.showTitle) {
                            Text(
                                text = title,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                ),
                                maxLines = 1
                            )
                        }
                        if (content.showTitle && content.showArtist) {
                            Spacer(GlanceModifier.height(2.dp))
                        }
                        if (content.showArtist) {
                            Text(
                                text = artist,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = colors.artist
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(GlanceModifier.width(8.dp))

                    if (content.showPrevNext) {
                        PreviousButton(
                            modifier = GlanceModifier.size(40.dp),
                            backgroundColor = colors.prevNextBackground,
                            iconColor = colors.prevNextIcon,
                            cornerRadius = controlButtonCornerRadius
                        )

                        Spacer(GlanceModifier.width(6.dp))
                    }

                    // Play/Pause Button
                    PlayPauseButton(
                        modifier = GlanceModifier.size(40.dp),
                        isPlaying = isPlaying,
                        backgroundColor = colors.playPauseBackground,
                        iconColor = colors.playPauseIcon,
                        cornerRadius = playButtonCornerRadius
                    )

                    if (content.showPrevNext) {
                        Spacer(GlanceModifier.width(6.dp))

                        NextButton(
                            modifier = GlanceModifier.size(40.dp),
                            backgroundColor = colors.prevNextBackground,
                            iconColor = colors.prevNextIcon,
                            cornerRadius = controlButtonCornerRadius
                        )
                    }
                }

                WidgetProgressBar(
                    context = context,
                    appearance = config.appearance,
                    playerInfo = playerInfo,
                    style = content.progressStyle,
                    widthDp = size.width - 32.dp
                )
            }
        }
    }
}
