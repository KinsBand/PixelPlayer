package com.theveloper.pixelplay.presentation.screens.artist

import com.theveloper.pixelplay.presentation.components.subcomps.rememberShuffleSpin
import com.theveloper.pixelplay.presentation.components.subcomps.shuffleSpin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.remember

import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.repository.ArtistAlbumItem
import com.theveloper.pixelplay.presentation.components.CollectionCard
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.launch

/** Height the FAB stack takes (small FAB + gap + FAB), so the list can pad past it. */
val ArtistMixFabStackHeight = 40.dp + 12.dp + 56.dp

/**
 * Bottom-right actions on the artist page: Mix all songs (the FAB) and, above it, a smaller
 * Mix albums button that opens [MixAlbumsSheet]. Each appears once it has something to mix.
 */
@Composable
fun ArtistMixFabs(
    showMixAll: Boolean,
    showMixAlbums: Boolean,
    onMixAll: () -> Unit,
    onMixAlbums: () -> Unit,
    modifier: Modifier = Modifier,
    artistName: String = "",
) {
    val enter = scaleIn(
        tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate),
        initialScale = 0.6f,
        transformOrigin = TransformOrigin(1f, 1f)
    ) + fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate))
    val exit = scaleOut(
        tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate),
        targetScale = 0.6f,
        transformOrigin = TransformOrigin(1f, 1f)
    ) + fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))

    Column(
        modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(visible = showMixAlbums, enter = enter, exit = exit) {
            SmallFloatingActionButton(
                onClick = onMixAlbums,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(Icons.Rounded.Album, contentDescription = "Mix albums")
            }
        }
        AnimatedVisibility(visible = showMixAll, enter = enter, exit = exit) {
            val mixSpin = rememberShuffleSpin()
            FloatingActionButton(
                onClick = { mixSpin.play(); onMixAll() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                // The endless artist mix, started from a random song.
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.artist_mix_fab_cd, artistName),
                    modifier = Modifier.shuffleSpin(mixSpin)
                )
            }
        }
    }
}

/**
 * The artist's albums, newest to oldest, left to right. Shuffle on a card plays that album
 * shuffled; tapping the card opens the album (the sheet closes first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixAlbumsSheet(
    artistName: String,
    albums: List<ArtistAlbumItem>,
    onShuffle: (ArtistAlbumItem) -> Unit,
    onOpen: (ArtistAlbumItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val newestFirst = albums.sortedByDescending { it.releaseDate ?: it.releaseYear?.toString().orEmpty() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Mix albums",
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "${newestFirst.size} album${if (newestFirst.size == 1) "" else "s"} by $artistName · newest first",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(newestFirst, key = { it.id }) { album ->
                    val albumSpin = rememberShuffleSpin()
                    CollectionCard(
                        title = album.title,
                        subtitle = listOfNotNull(
                            album.releaseYear?.toString(),
                            album.trackCount.takeIf { it > 0 }?.let { "$it songs" }
                        ).joinToString(" · "),
                        subtitleContent = { ReleaseMetaLine(prefix = album.releaseYear?.toString(), trackCount = album.trackCount) },
                        coverModel = album.coverArtUrl,
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                                onOpen(album)
                            }
                        },
                        onAction = { albumSpin.play(); onShuffle(album) },
                        actionLabel = "Shuffle ${album.title}",
                        actionContent = {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp).shuffleSpin(albumSpin))
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate),
                            fadeOutSpec = tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)
                        )
                    )
                }
            }
        }
    }

}

/**
 * Like whole releases instead of the whole discography: albums and singles newest first, each with
 * a heart that likes every song on it. "Like all" stays as the first row for when you do want it.
 * Tapping a card opens the release (the sheet closes first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikeAlbumsSheet(
    artistName: String,
    releases: List<ArtistAlbumItem>,
    isLiked: (ArtistAlbumItem) -> Boolean,
    busyIds: Set<String>,
    notOwnedCount: Int,
    onLikeRelease: (ArtistAlbumItem) -> Unit,
    onLikeAll: () -> Unit,
    onOpen: (ArtistAlbumItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val newestFirst = remember(releases) {
        releases.distinctBy { it.id }.sortedByDescending { it.releaseDate ?: it.releaseYear?.toString().orEmpty() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    stringResource(R.string.artist_like_albums),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold)
                )
                Text(
                    stringResource(R.string.artist_like_albums_subtitle, artistName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (notOwnedCount > 0) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onLikeAll()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.artist_like_all_songs, notOwnedCount))
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(newestFirst, key = { it.id }) { release ->
                    val liked = isLiked(release)
                    val busy = release.id in busyIds
                    CollectionCard(
                        title = release.title,
                        subtitle = listOfNotNull(
                            release.releaseYear?.toString(),
                            release.trackCount.takeIf { it > 0 }?.let { "$it songs" }
                        ).joinToString(" · "),
                        subtitleContent = { ReleaseMetaLine(prefix = release.releaseYear?.toString(), trackCount = release.trackCount) },
                        coverModel = release.coverArtUrl,
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                                onOpen(release)
                            }
                        },
                        // Already all liked, or still loading its songs: the heart does nothing.
                        onAction = { if (!busy && !liked) onLikeRelease(release) },
                        actionLabel = stringResource(R.string.artist_like_album_cd, release.title),
                        actionContent = {
                            if (busy) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate),
                            fadeOutSpec = tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)
                        )
                    )
                }
            }
        }
    }
}
