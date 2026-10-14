package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Export a playlist: Spotify and YouTube Music side by side (tap a tile to export there), one
 * auto-sync switch for both, then "Export as file" and "Transfer" (TuneMyMusic) side by side.
 * While an export runs the sheet shows only its progress.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistExportBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    playlistName: String,
    songCount: Int,
    isSpotifyConnected: Boolean,
    isYouTubeMusicConnected: Boolean,
    onExportFile: () -> Unit,
    onExportSpotify: (autoSync: Boolean) -> Unit,
    onExportYouTubeMusic: (autoSync: Boolean) -> Unit,
    onOpenTuneMyMusic: () -> Unit,
    isExporting: Boolean,
    exportStatusText: String?
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var autoSync by rememberSaveable { mutableStateOf(true) }
    val anyConnected = isSpotifyConnected || isYouTubeMusicConnected

    ModalBottomSheet(
        onDismissRequest = { if (!isExporting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.playlist_export_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pluralStringResource(R.plurals.playlist_export_subtitle, songCount, playlistName, songCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedContent(
                targetState = isExporting,
                transitionSpec = {
                    fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                },
                label = "exportState"
            ) { exporting ->
                if (exporting) {
                    Surface(
                        shape = AbsoluteSmoothCornerShape(24.dp, 60),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = exportStatusText ?: stringResource(R.string.playlist_export_exporting),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Music services, side by side.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            ExportServiceTile(
                                name = stringResource(R.string.playlist_export_spotify),
                                iconRes = R.drawable.ic_source_spotify,
                                brandColor = Color(0xFF1DB954),
                                connected = isSpotifyConnected,
                                onClick = { onExportSpotify(autoSync) },
                                modifier = Modifier.weight(1f)
                            )
                            ExportServiceTile(
                                name = stringResource(R.string.playlist_export_youtube_music),
                                iconRes = R.drawable.ic_source_youtube_music,
                                brandColor = Color(0xFFFF0033),
                                connected = isYouTubeMusicConnected,
                                onClick = { onExportYouTubeMusic(autoSync) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // One auto-sync switch for whichever service you export to.
                        Surface(
                            shape = AbsoluteSmoothCornerShape(20.dp, 60),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AbsoluteSmoothCornerShape(20.dp, 60))
                                .clickable(enabled = anyConnected, role = Role.Switch) { autoSync = !autoSync }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.playlist_export_auto_sync),
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        stringResource(R.string.playlist_export_auto_sync_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = autoSync, onCheckedChange = { autoSync = it }, enabled = anyConnected)
                            }
                        }

                        // File + Transfer, side by side.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = onExportFile,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                shape = AbsoluteSmoothCornerShape(18.dp, 60)
                            ) {
                                Icon(painterResource(R.drawable.rounded_attach_file_24), contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.playlist_export_file), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(
                                onClick = onOpenTuneMyMusic,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                shape = AbsoluteSmoothCornerShape(18.dp, 60)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.playlist_export_transfer), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A square-ish service tile: brand icon, name, and "Export" (or "Connect first"). */
@Composable
private fun ExportServiceTile(
    name: String,
    iconRes: Int,
    brandColor: Color,
    connected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = AbsoluteSmoothCornerShape(24.dp, 60)
    Surface(
        shape = shape,
        color = if (connected) brandColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(shape)
            .clickable(enabled = connected, role = Role.Button, onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(52.dp).background(if (connected) brandColor else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = if (connected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(if (connected) R.string.playlist_export_action else R.string.playlist_export_connect_first),
                style = MaterialTheme.typography.labelMedium,
                color = if (connected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}
