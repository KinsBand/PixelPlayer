package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.search.LinkResolution
import com.theveloper.pixelplay.data.search.MediaService
import com.theveloper.pixelplay.data.search.MediaTargetType
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongCoverLoadingIndicator
import com.theveloper.pixelplay.presentation.viewmodel.MediaLinkUiState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * The card on top of Search for a pasted Spotify / YouTube Music link: the service logo and kind
 * ("Spotify album"), then the item itself once it's been read. Tap opens exactly that item
 * (artist page, album, playlist page, or plays the song).
 */
@Composable
fun MediaLinkCard(
    state: MediaLinkUiState,
    onOpen: (LinkResolution) -> Unit,
    modifier: Modifier = Modifier,
) {
    val link = when (state) {
        is MediaLinkUiState.Resolving -> state.link
        is MediaLinkUiState.Resolved -> state.link
        is MediaLinkUiState.Failed -> state.link
        MediaLinkUiState.None -> return
    }
    val result = (state as? MediaLinkUiState.Resolved)?.result
    val canOpen = result != null && when (result) {
        is LinkResolution.Track -> result.song != null
        is LinkResolution.Playlist -> result.songs.isNotEmpty()
        else -> true
    }
    val kind = stringResource(
        when (link.type) {
            MediaTargetType.ARTIST -> R.string.search_link_kind_artist
            MediaTargetType.ALBUM -> R.string.search_link_kind_album
            MediaTargetType.TRACK -> R.string.search_link_kind_song
            MediaTargetType.PLAYLIST -> R.string.search_link_kind_playlist
        },
        if (link.service == MediaService.SPOTIFY) "Spotify" else "YouTube Music"
    )
    Surface(
        onClick = { if (result != null && canOpen) onOpen(result) },
        enabled = canOpen,
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val coverShape = if (link.type == MediaTargetType.ARTIST) CircleShape else AbsoluteSmoothCornerShape(12.dp, 60)
            Box(
                Modifier.size(56.dp).clip(coverShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state is MediaLinkUiState.Resolving -> SongCoverLoadingIndicator(size = 28.dp, color = MaterialTheme.colorScheme.primary)
                    state is MediaLinkUiState.Failed -> Icon(Icons.Rounded.LinkOff, contentDescription = null)
                    result?.coverUrl != null -> SmartImage(model = result.coverUrl, contentDescription = null, shape = coverShape, modifier = Modifier.size(56.dp))
                    else -> Icon(
                        painterResource(if (link.service == MediaService.SPOTIFY) R.drawable.ic_source_spotify else R.drawable.ic_source_youtube_music),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(if (link.service == MediaService.SPOTIFY) R.drawable.ic_source_spotify else R.drawable.ic_source_youtube_music),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(kind, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                Text(
                    text = when (state) {
                        is MediaLinkUiState.Resolving -> stringResource(R.string.search_link_reading)
                        is MediaLinkUiState.Failed -> stringResource(R.string.search_link_failed)
                        else -> result?.title.orEmpty()
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                result?.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (canOpen) {
                Icon(
                    if (result is LinkResolution.Track) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(if (result is LinkResolution.Track) R.string.search_link_play_cd else R.string.search_link_open_cd)
                )
            }
        }
    }
}
