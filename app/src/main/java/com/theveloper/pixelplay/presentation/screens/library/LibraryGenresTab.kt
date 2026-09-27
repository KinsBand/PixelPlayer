package com.theveloper.pixelplay.presentation.screens.library

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.data.library.LibraryInsightsRepository
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.screens.ListExtraBottomGap
import com.theveloper.pixelplay.presentation.viewmodel.GenreCardUi
import com.theveloper.pixelplay.presentation.viewmodel.GenreSort
import com.theveloper.pixelplay.presentation.viewmodel.LibraryCollectionViewModel
import com.theveloper.pixelplay.presentation.viewmodel.MoodChipUi
import com.theveloper.pixelplay.ui.theme.GenreThemeUtils
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme

/**
 * Library › Genres (moved here from Search). Genre families ordered by how much you listen to
 * them, your "genre DNA" for the last 90 days, and mood mixes built from your own music.
 */
@Composable
fun LibraryGenresTab(
    viewModel: LibraryCollectionViewModel,
    bottomBarHeight: Dp,
    onGenreClick: (genreId: String) -> Unit,
    onMoodClick: (MoodChipUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.genresUi.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onGenresVisible() }
    // Genre DNA follows new plays only while this tab is on screen.
    LaunchedEffect(Unit) { viewModel.followInsights() }

    if (ui.isLoading && ui.cards.isEmpty()) {
        GenresSkeleton(bottomBarHeight = bottomBarHeight, modifier = modifier)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (insights.genreDna.isNotEmpty()) {
            item(key = "dna", span = { GridItemSpan(maxLineSpan) }) {
                GenreDnaBar(insights.genreDna, onFamilyClick = { onGenreClick(FAMILY_PREFIX + it) })
            }
        }
        if (ui.moods.isNotEmpty()) {
            item(key = "moods", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Moods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ui.moods, key = { "mood_${it.filter.id}" }) { mood ->
                            AssistChip(
                                onClick = { onMoodClick(mood) },
                                label = { Text("${mood.filter.label} · ${mood.songCount}") }
                            )
                        }
                    }
                }
            }
        }
        item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GenreSort.entries, key = { "sort_${it.name}" }) { option ->
                    FilterChip(
                        selected = ui.sort == option,
                        onClick = { viewModel.setGenreSort(option) },
                        label = { Text(option.label) }
                    )
                }
            }
        }
        items(ui.cards, key = { "genre_${it.familyId}" }) { card ->
            GenreFamilyCard(card = card, onClick = { onGenreClick(FAMILY_PREFIX + card.familyId) })
        }
        if (ui.unknownCount > 0) {
            item(key = "unknown", span = { GridItemSpan(maxLineSpan) }) {
                UnknownGenreCard(count = ui.unknownCount, onClick = { onGenreClick("unknown") })
            }
        }
        if (ui.cards.isEmpty() && ui.unknownCount == 0) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No genres yet. Like or download a few songs, or add music files, and they show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}

private const val FAMILY_PREFIX = "family:"

/** Placeholder grid shown while the genres are counted (same layout as the real tab). */
@Composable
private fun GenresSkeleton(bottomBarHeight: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "genresSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "genresSkeletonPulse"
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .alpha(pulse),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "dna_skeleton", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(20.dp)).background(block))
        }
        item(key = "sort_skeleton", span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Box(Modifier.width(92.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).background(block)) }
            }
        }
        items(6, key = { "genre_skeleton_$it" }) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp)).background(block))
        }
    }
}

@Composable
private fun GenreDnaBar(shares: List<LibraryInsightsRepository.GenreShare>, onFamilyClick: (String) -> Unit) {
    val isDark = LocalPixelPlayDarkTheme.current
    val top = shares.take(6)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Your genre DNA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Listening time · last 90 days", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                top.forEach { share ->
                    val color = GenreThemeUtils.getGenreThemeColor(share.familyId, isDark).container
                    Box(
                        Modifier
                            .weight(share.fraction.coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(color)
                            .clickable { onFamilyClick(share.familyId) }
                    )
                }
                val rest = 1f - top.sumOf { it.fraction.toDouble() }.toFloat()
                if (rest > 0.02f) Box(Modifier.weight(rest).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(top, key = { "dna_${it.familyId}" }) { share ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onFamilyClick(share.familyId) }) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(GenreThemeUtils.getGenreThemeColor(share.familyId, isDark).container))
                        Spacer(Modifier.width(4.dp))
                        Text("${share.label} ${(share.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreFamilyCard(card: GenreCardUi, onClick: () -> Unit) {
    val isDark = LocalPixelPlayDarkTheme.current
    val colors = remember(card.familyId, isDark) { GenreThemeUtils.getGenreThemeColor(card.familyId, isDark) }
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = colors.container,
        contentColor = colors.onContainer
    ) {
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            CoverCollage(card.covers, Modifier.align(Alignment.TopEnd).size(56.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    card.label.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 60.dp)
                )
                Text("${card.songCount} songs", style = MaterialTheme.typography.bodySmall, color = colors.onContainer.copy(alpha = 0.8f))
                if (card.listeningMs > 0) {
                    Text(formatListening(card.listeningMs) + " · 90 days", style = MaterialTheme.typography.labelSmall,
                        color = colors.onContainer.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(10.dp))
                if (card.topArtists.isNotEmpty()) {
                    Row {
                        card.topArtists.forEachIndexed { index, (name, image) ->
                            ArtistAvatar(name, image, Modifier.offset(x = (-8 * index).dp), border = colors.container)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (card.subgenres.isNotEmpty()) {
                    Text(
                        card.subgenres.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistAvatar(name: String, image: String?, modifier: Modifier, border: Color) {
    val context = LocalContext.current
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(border)
            .padding(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (image.isNullOrBlank()) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(
                model = remember(image) { ImageRequest.Builder(context).data(image).size(96).build() },
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CoverCollage(covers: List<String>, modifier: Modifier) {
    if (covers.isEmpty()) return
    val context = LocalContext.current
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        val cells = covers.take(4)
        if (cells.size < 4) {
            AsyncImage(
                model = remember(cells[0]) { ImageRequest.Builder(context).data(cells[0]).size(160).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                listOf(cells.subList(0, 2), cells.subList(2, 4)).forEach { rowCells ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        rowCells.forEach { url ->
                            AsyncImage(
                                model = remember(url) { ImageRequest.Builder(context).data(url).size(96).build() },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnknownGenreCard(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Unknown genre", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("$count songs without a genre", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onClick) { Text("Fill genres") }
        }
    }
}
