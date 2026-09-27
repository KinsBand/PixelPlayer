package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import kotlinx.collections.immutable.ImmutableList

/**
 * Condensed Daily Mix entry point.
 *
 * The card itself is a single full-width bar: the label on the left, a **Mix**
 * button that starts the mix straight away, and **Open** at the far right which
 * expands the filter sheet. The sheet is not a modal — it grows out of the
 * bottom edge of the bar and pushes the rest of Home down, so the two read as
 * one connected surface.
 */
@Composable
fun DailyMixLauncher(
    songs: ImmutableList<Song>,
    filterPool: ImmutableList<Song>,
    isPlaying: Boolean,
    currentSongId: String?,
    onPlayMix: () -> Unit,
    onOpenDailyMixScreen: () -> Unit,
    onPlayGeneratedMix: (GeneratedMix) -> Unit,
    onOpenGeneratedMix: (GeneratedMix) -> Unit = onPlayGeneratedMix,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable(
        saver = MixFilterSelectionSaver
    ) { mutableStateOf(MixFilterSelection()) }

    val options = remember(filterPool) { buildMixFilterOptions(filterPool) }
    val generatedMixes = remember(filterPool, selection) {
        buildGeneratedMixes(filterPool, selection)
    }

    val bottomCorner by animateDpAsState(
        targetValue = if (expanded) 0.dp else 22.dp,
        animationSpec = tween(durationMillis = 220),
        label = "DailyMixBarBottomCorner"
    )

    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- The bar ---------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 0.dp,
                        bottomStart = bottomCorner,
                        bottomEnd = bottomCorner
                    )
                )
                .background(colors.primaryContainer)
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Mix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (selection.isEmpty) {
                        "${songs.size} tracks for you"
                    } else {
                        describeSelection(selection)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Mix — plays the mix immediately, no sheet.
            Surface(
                onClick = onPlayMix,
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Shuffle else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Mix",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Open — expands the filter sheet below.
            Surface(
                onClick = { expanded = !expanded },
                shape = CircleShape,
                color = colors.surface.copy(alpha = 0.55f),
                contentColor = colors.onPrimaryContainer,
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) "Close mix filters" else "Open mix filters",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ---- The connected sheet ---------------------------------------
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(240)) + fadeIn(tween(180)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(120))
        ) {
            MixFilterSheet(
                options = options,
                selection = selection,
                onSelectionChange = { selection = it },
                generatedMixes = generatedMixes,
                currentSongId = currentSongId,
                onMixClick = onPlayGeneratedMix,
                onOpenMix = onOpenGeneratedMix,
                onViewAll = onOpenDailyMixScreen
            )
        }
    }
}

/**
 * The sheet body. Header is one row: Country and Era pinned left, Genre and
 * Mood pinned right, with the drag handle and title occupying the gap between
 * them. Below it, generated playlist cards in a horizontal row.
 */
@Composable
private fun MixFilterSheet(
    options: MixFilterOptions,
    selection: MixFilterSelection,
    onSelectionChange: (MixFilterSelection) -> Unit,
    generatedMixes: ImmutableList<GeneratedMix>,
    currentSongId: String?,
    onMixClick: (GeneratedMix) -> Unit,
    onOpenMix: (GeneratedMix) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var openField by remember { mutableStateOf<MixFilterField?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = 26.dp,
                    bottomEnd = 26.dp
                )
            )
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header: [Country][Era]  — handle + title —  [Genre][Mood]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MixFilterButton(
                field = MixFilterField.Country,
                selection = selection,
                options = options,
                isOpen = openField == MixFilterField.Country,
                onToggle = { openField = if (openField == MixFilterField.Country) null else it },
                modifier = Modifier.weight(1f)
            )
            MixFilterButton(
                field = MixFilterField.Era,
                selection = selection,
                options = options,
                isOpen = openField == MixFilterField.Era,
                onToggle = { openField = if (openField == MixFilterField.Era) null else it },
                modifier = Modifier.weight(1f)
            )

            // Centre gap: drag handle over the sheet title.
            Column(
                modifier = Modifier.width(74.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Text(
                    text = "Mix filters",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            MixFilterButton(
                field = MixFilterField.Genre,
                selection = selection,
                options = options,
                isOpen = openField == MixFilterField.Genre,
                onToggle = { openField = if (openField == MixFilterField.Genre) null else it },
                modifier = Modifier.weight(1f)
            )
            MixFilterButton(
                field = MixFilterField.Mood,
                selection = selection,
                options = options,
                isOpen = openField == MixFilterField.Mood,
                onToggle = { openField = if (openField == MixFilterField.Mood) null else it },
                modifier = Modifier.weight(1f)
            )
        }

        // The open field's option list.
        AnimatedVisibility(
            visible = openField != null,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(tween(140)),
            exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(tween(100))
        ) {
            val field = openField
            if (field != null) {
                MixFilterOptionList(
                    field = field,
                    options = options.optionsFor(field),
                    selected = selection.valueOf(field),
                    onPick = { value ->
                        onSelectionChange(selection.with(field, value))
                        openField = null
                    }
                )
            }
        }

        // Generated playlist cards in a horizontal scrolling row.
        if (generatedMixes.isEmpty()) {
            Text(
                text = "Nothing in your library matches those filters yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 20.dp)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(generatedMixes, key = { it.id }) { mix ->
                    GeneratedMixCard(
                        mix = mix,
                        isCurrent = currentSongId != null && mix.songs.any { it.id == currentSongId },
                        onClick = { onOpenMix(mix) },
                        onPlayClick = { onMixClick(mix) }
                    )
                }
            }
        }

        Text(
            text = "See the full Daily Mix",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onViewAll)
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * One of the four header buttons. Once a value is picked the button shows that
 * value in place of the field name, which is how the user tells at a glance
 * what the sheet is filtered by.
 */
@Composable
private fun MixFilterButton(
    field: MixFilterField,
    selection: MixFilterSelection,
    options: MixFilterOptions,
    isOpen: Boolean,
    onToggle: (MixFilterField) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val value = selection.valueOf(field)
    val hasOptions = options.optionsFor(field).isNotEmpty()
    val isActive = value != null

    val container = when {
        isActive -> colors.secondaryContainer
        isOpen -> colors.surfaceVariant
        else -> colors.surfaceContainerHighest
    }
    val content = when {
        isActive -> colors.onSecondaryContainer
        hasOptions -> colors.onSurface
        else -> colors.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = modifier
            .heightIn(min = 34.dp)
            .clickable(enabled = hasOptions) { onToggle(field) }
    ) {
        Text(
            text = value ?: field.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 9.dp)
        )
    }
}

/** Scrollable list of values for the field the user just tapped. */
@Composable
private fun MixFilterOptionList(
    field: MixFilterField,
    options: ImmutableList<String>,
    selected: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                MixOptionChip(
                    label = "Any",
                    isSelected = selected == null,
                    onClick = { onPick(null) }
                )
            }
            items(options) { option ->
                MixOptionChip(
                    label = option,
                    isSelected = option == selected,
                    onClick = { onPick(if (option == selected) null else option) }
                )
            }
        }
    }
}

@Composable
private fun MixOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) colors.primary else colors.surfaceContainerHighest,
        contentColor = if (isSelected) colors.onPrimary else colors.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/** A generated playlist card for horizontal row: 2x2 art collage with play button overlay, title, subtitle. */
@Composable
private fun GeneratedMixCard(
    mix: GeneratedMix,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isCurrent) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            MixArtCollage(
                songs = mix.songs,
                size = 124,
                corner = 14,
                modifier = Modifier.size(124.dp)
            )

            // Play button overlay in bottom right
            Surface(
                onClick = onPlayClick,
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(34.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${mix.title}",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Text(
            text = mix.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = mix.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 2x2 album-art collage for a mix. Falls back to fewer tiles when the mix has
 * fewer than four distinct covers.
 */
@Composable
fun MixArtCollage(
    songs: ImmutableList<Song>,
    size: Int,
    corner: Int,
    modifier: Modifier = Modifier
) {
    val covers = remember(songs) {
        songs.map { it.albumArtUriString }
            .distinct()
            .take(4)
    }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(colors.surfaceVariant)
    ) {
        if (covers.size < 4) {
            SmartImage(
                model = covers.firstOrNull(),
                contentDescription = null,
                shape = RoundedCornerShape(corner.dp),
                modifier = Modifier.size(size.dp)
            )
        } else {
            Column {
                Row {
                    SmartImage(
                        model = covers[0],
                        contentDescription = null,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.size((size / 2).dp)
                    )
                    SmartImage(
                        model = covers[1],
                        contentDescription = null,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.size((size / 2).dp)
                    )
                }
                Row {
                    SmartImage(
                        model = covers[2],
                        contentDescription = null,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.size((size / 2).dp)
                    )
                    SmartImage(
                        model = covers[3],
                        contentDescription = null,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.size((size / 2).dp)
                    )
                }
            }
        }
    }
}

/** Keeps the four chosen filters across configuration change / process death. */
private val MixFilterSelectionSaver =
    androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.MutableState<MixFilterSelection>, String>(
        save = { state ->
            val v = state.value
            listOf(v.country ?: "", v.era ?: "", v.genre ?: "", v.mood ?: "")
        },
        restore = { saved ->
            mutableStateOf(
                MixFilterSelection(
                    country = saved.getOrNull(0)?.takeIf { it.isNotEmpty() },
                    era = saved.getOrNull(1)?.takeIf { it.isNotEmpty() },
                    genre = saved.getOrNull(2)?.takeIf { it.isNotEmpty() },
                    mood = saved.getOrNull(3)?.takeIf { it.isNotEmpty() }
                )
            )
        }
    )

