package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * One tile in the speed dial row. A tile is either a curated mix (2x2 collage)
 * or a single song (one cover), so both kinds can share the same row.
 */
@Immutable
sealed interface SpeedDialTile {
    val key: String
    val label: String
    val caption: String

    @Immutable
    data class MixTile(
        val mix: GeneratedMix
    ) : SpeedDialTile {
        override val key: String get() = "mix_${mix.id}"
        override val label: String get() = mix.title
        override val caption: String get() = "${mix.songs.size} tracks"
    }

    @Immutable
    data class SongTile(
        val song: Song
    ) : SpeedDialTile {
        override val key: String get() = "song_${song.id}"
        override val label: String get() = song.title
        override val caption: String get() = song.displayArtist
    }
}

/**
 * Speed dial — a single horizontally scrollable row of square tiles, mixing
 * curated mixes and individual songs.
 */
@Composable
fun SpeedDialRow(
    tiles: ImmutableList<SpeedDialTile>,
    currentSongId: String?,
    onTileClick: (SpeedDialTile) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Speed dial",
    tileSize: Int = 132
) {
    if (tiles.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = tiles, key = { it.key }) { tile ->
                SpeedDialTileCard(
                    tile = tile,
                    isCurrent = when (tile) {
                        is SpeedDialTile.SongTile -> tile.song.id == currentSongId
                        is SpeedDialTile.MixTile ->
                            currentSongId != null && tile.mix.songs.any { it.id == currentSongId }
                    },
                    size = tileSize,
                    onClick = { onTileClick(tile) }
                )
            }
        }
    }
}

@Composable
private fun SpeedDialTileCard(
    tile: SpeedDialTile,
    isCurrent: Boolean,
    size: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .width(size.dp)
            .clip(shape)
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(size.dp)) {
            when (tile) {
                is SpeedDialTile.MixTile -> MixArtCollage(
                    songs = tile.mix.songs,
                    size = size,
                    corner = 0
                )
                is SpeedDialTile.SongTile -> SmartImage(
                    model = tile.song.albumArtUriString,
                    contentDescription = null,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.size(size.dp)
                )
            }

            // Scrim so the label stays legible over any artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = tile.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tile.caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Interleaves curated mixes with individual songs so the row alternates
 * between the two kinds rather than front-loading all the mixes.
 */
fun buildSpeedDialTiles(
    mixes: List<GeneratedMix>,
    songs: List<Song>,
    maxTiles: Int = 12
): ImmutableList<SpeedDialTile> {
    if (mixes.isEmpty() && songs.isEmpty()) return persistentListOf()

    val mixTiles = mixes.map { SpeedDialTile.MixTile(it) as SpeedDialTile }
    val songTiles = songs.distinctBy { it.id }.map { SpeedDialTile.SongTile(it) as SpeedDialTile }

    val result = ArrayList<SpeedDialTile>(maxTiles)
    var mixIndex = 0
    var songIndex = 0
    while (result.size < maxTiles && (mixIndex < mixTiles.size || songIndex < songTiles.size)) {
        if (mixIndex < mixTiles.size) result.add(mixTiles[mixIndex++])
        if (result.size < maxTiles && songIndex < songTiles.size) result.add(songTiles[songIndex++])
        if (result.size < maxTiles && songIndex < songTiles.size) result.add(songTiles[songIndex++])
    }
    return result.toImmutableList()
}
