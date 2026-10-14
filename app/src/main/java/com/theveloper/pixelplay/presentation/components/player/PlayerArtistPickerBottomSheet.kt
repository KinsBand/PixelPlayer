package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.CollabArtistsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.normalizeMixQuery
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

private data class PlayerArtistShortcutItem(
    val artist: Artist,
    val isPrimary: Boolean
)

/**
 * Tapping the artist of a song with two or more artists: one sideways row. First the
 * combined page (every song the artists made together), then each artist on their own,
 * each as a round picture with the name under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerArtistPickerBottomSheet(
    song: Song,
    artists: List<Artist>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    collabViewModel: CollabArtistsViewModel = hiltViewModel(),
) {
    val colorScheme = MaterialTheme.colorScheme
    val shortcutItems = remember(song.id, song.artistId, song.artists, artists) {
        val primaryArtist = song.primaryArtist
        val computedItems = artists.mapIndexed { index, artist ->
            val isPrimary = when {
                primaryArtist.id != 0L && primaryArtist.id != -1L -> artist.id == primaryArtist.id
                primaryArtist.name.isNotBlank() -> artist.name.equals(primaryArtist.name, ignoreCase = true)
                else -> index == 0
            }
            PlayerArtistShortcutItem(artist = artist, isPrimary = isPrimary)
        }
        val hasPrimary = computedItems.any { it.isPrimary }
        val normalizedItems = if (hasPrimary || computedItems.isEmpty()) computedItems
        else computedItems.mapIndexed { index, item -> item.copy(isPrimary = index == 0) }
        normalizedItems.sortedByDescending { it.isPrimary }
    }
    val onlineImages by collabViewModel.images.collectAsStateWithLifecycle()
    val openingCollab by collabViewModel.openingCollab.collectAsStateWithLifecycle()
    LaunchedEffect(artists) { collabViewModel.loadMissingImages(artists) }
    fun imageFor(artist: Artist): String? =
        artist.effectiveImageUrl?.takeIf { it.isNotBlank() } ?: onlineImages[normalizeMixQuery(artist.name)]

    val countLabel = when (shortcutItems.size) {
        1 -> stringResource(R.string.artist_picker_count_single)
        else -> stringResource(R.string.artist_picker_count_multiple, shortcutItems.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        },
        containerColor = colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = stringResource(R.string.artist_picker_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Everything they made together.
                if (shortcutItems.size >= 2) {
                    item(key = "collab") {
                        CollabArtistTile(
                            images = shortcutItems.map { imageFor(it.artist) },
                            names = shortcutItems.map { it.artist.name },
                            loading = openingCollab,
                            onClick = {
                                collabViewModel.openCollab(song, shortcutItems.map { it.artist }) { onDismiss() }
                            }
                        )
                    }
                }
                // 2. Each artist.
                items(shortcutItems, key = { "a_" + it.artist.id + "_" + it.artist.name }) { item ->
                    ArtistPickerTile(
                        name = item.artist.name,
                        imageUrl = imageFor(item.artist),
                        caption = stringResource(
                            if (item.isPrimary) R.string.artist_picker_primary_label else R.string.artist_picker_shortcut_label
                        ),
                        highlighted = item.isPrimary,
                        onClick = { onArtistClick(item.artist) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val TileWidth = 104.dp
private val PictureSize = 88.dp

@Composable
private fun ArtistPickerTile(
    name: String,
    imageUrl: String?,
    caption: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(TileWidth)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Open $name"
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(PictureSize)
                .then(if (highlighted) Modifier.border(2.dp, colorScheme.primary, CircleShape) else Modifier)
                .padding(if (highlighted) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            ArtistPicture(imageUrl, name, Modifier.fillMaxSize())
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) colorScheme.primary else colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** The combined page: the artists' pictures overlapped in one circle, names joined with ×. */
@Composable
private fun CollabArtistTile(
    images: List<String?>,
    names: List<String>,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val title = names.joinToString(" × ")
    Column(
        modifier = Modifier
            .width(TileWidth + 16.dp)
            .clip(MaterialTheme.shapes.large)
            .background(colorScheme.secondaryContainer)
            .clickable(enabled = !loading, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Open everything by $title together"
            }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(PictureSize), contentAlignment = Alignment.Center) {
            // Up to three pictures fanned out inside the circle.
            val shown = images.zip(names).take(3)
            val small: Dp = if (shown.size <= 2) 60.dp else 50.dp
            shown.forEachIndexed { index, (url, name) ->
                val spread = if (shown.size == 1) 0.dp else 26.dp
                val x = if (shown.size == 1) 0.dp else spread * (index.toFloat() / (shown.size - 1) * 2f - 1f)
                Box(
                    Modifier
                        .offset(x = x)
                        .size(small)
                        .border(2.dp, colorScheme.secondaryContainer, CircleShape)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHighest)
                ) {
                    ArtistPicture(url, name, Modifier.fillMaxSize())
                }
            }
            if (loading) {
                Box(
                    Modifier
                        .size(PictureSize)
                        .clip(CircleShape)
                        .background(colorScheme.scrim.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = colorScheme.inverseOnSurface, strokeWidth = 3.dp)
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSecondaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Row2(icon = true, text = "Together", color = colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun Row2(icon: Boolean, text: String, color: Color) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon) {
            Icon(Icons.Rounded.Groups, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun ArtistPicture(url: String?, name: String, modifier: Modifier) {
    SmartImage(
        model = url,
        contentDescription = name,
        modifier = modifier,
        placeholderResId = R.drawable.rounded_artist_24,
        errorResId = R.drawable.rounded_artist_24,
        shape = CircleShape,
        contentScale = ContentScale.Crop,
        targetSize = Size(220, 220),
        placeHolderBackgroundColor = Color.Transparent
    )
}
