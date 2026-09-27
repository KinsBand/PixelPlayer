package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
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
import androidx.compose.ui.unit.DpSize
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.preferences.WidgetConfig
import com.theveloper.pixelplay.data.preferences.WidgetKind

class GridWidget2x2 : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = loadWidgetConfig(context, WidgetKind.GRID_2X2)
        provideContent {
            val playerInfo = currentState<PlayerInfo>()
            GlanceTheme {
                GridWidget2x2Content(
                    playerInfo = playerInfo,
                    context = context,
                    config = config
                )
            }
        }
    }

    @Composable
    private fun GridWidget2x2Content(
        playerInfo: PlayerInfo,
        context: Context,
        config: WidgetConfig
    ) {
        val content = config.content
        val isPlaying = playerInfo.isPlaying
        val albumArtBitmapData = playerInfo.albumArtBitmapData
        val albumArtUri = playerInfo.albumArtUri

        val colors = playerInfo.getWidgetColors(config.appearance)

        val itemCornerRadius = 16.dp

        val size = LocalSize.current
        val minSide = min(size.width, size.height)
        
        val dynamicIconSize = (minSide.value * 0.14f).dp
        val dynamicPlayIconSize = (minSide.value * 0.16f).dp
        val albumArtSize = (minSide.value * 0.40f).dp

        Box(
            modifier = GlanceModifier
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(minSide)
                    .widgetSurface(
                        context = context,
                        appearance = config.appearance,
                        colors = colors,
                        outlineColor = outlineColorFor(context, config.appearance, playerInfo),
                        size = DpSize(minSide, minSide)
                    )
                    .padding(16.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    // Top
                    Row(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                    ) {
                        AlbumArtImage(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight(),
                            bitmapData = albumArtBitmapData,
                            albumArtUri = albumArtUri,
                            size = albumArtSize, // Used for optimization and placeholder size
                            context = context,
                            cornerRadius = itemCornerRadius
                        )

                        Spacer(GlanceModifier.width(6.dp))

                        // Play/Pause Button
                        PlayPauseButton(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight(),
                            isPlaying = isPlaying,
                            backgroundColor = colors.playPauseBackground,
                            iconColor = colors.playPauseIcon,
                            cornerRadius = itemCornerRadius,
                            iconSize = dynamicPlayIconSize
                        )
                    }

                    if (content.showPrevNext) {
                        Spacer(GlanceModifier.height(6.dp))

                        // Bottom
                        Row(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxWidth()
                        ) {
                            PreviousButton(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight(),
                                backgroundColor = colors.prevNextBackground,
                                iconColor = colors.prevNextIcon,
                                cornerRadius = itemCornerRadius,
                                iconSize = dynamicIconSize
                            )

                            Spacer(GlanceModifier.width(6.dp))

                            NextButton(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight(),
                                backgroundColor = colors.prevNextBackground,
                                iconColor = colors.prevNextIcon,
                                cornerRadius = itemCornerRadius,
                                iconSize = dynamicIconSize
                            )
                        }
                    }
                }
            }
        }
    }
}
