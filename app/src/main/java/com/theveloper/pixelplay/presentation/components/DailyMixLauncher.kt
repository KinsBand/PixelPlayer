package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import kotlinx.coroutines.flow.first
import com.theveloper.pixelplay.presentation.viewmodel.OnlineFilterSuggestion
import com.theveloper.pixelplay.presentation.viewmodel.mergeLocalAndOnline
import com.theveloper.pixelplay.presentation.viewmodel.normalizeMixQuery
import com.theveloper.pixelplay.presentation.viewmodel.shouldOfferCustom
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.ui.draw.clipToBounds
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import kotlinx.collections.immutable.ImmutableList
import java.util.Locale

/**
 * Daily Mix entry point on Home: one full-width card, the top centrepiece.
 *
 * Home's mood chips sit in the card's top row ([topRow]); under them, the Daily Mix row
 * (collage, "Daily Mix" + mood/top artists, an arrow and Play). Tapping the row opens the
 * Daily Mix sheet ([DailyMixSheet]) from the bottom of the screen; the mini player and the
 * bottom bar move up to sit on top of it.
 */
@Composable
fun DailyMixLauncher(
    songs: ImmutableList<Song>,
    isPlaying: Boolean,
    sheetOpen: Boolean,
    selection: MixFilterSelection,
    onPlayMix: () -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown above the Daily Mix row inside the same card (Home's mood chips). */
    topRow: (@Composable () -> Unit)? = null,
    /** Extra line under the title, e.g. the mood the mix is leaning toward. */
    moodLabel: String? = null
) {
    val topArtists = remember(songs) { topArtistNames(songs) }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            if (topRow != null) {
                topRow()
                Spacer(Modifier.height(8.dp))
            }
            DailyMixBar(
                songs = songs,
                subtitle = if (selection.isEmpty) "" else describeSelection(selection),
                // No filters: "mood · [♪] 25 · top artists", with the shared count badge.
                songCount = if (selection.isEmpty) songs.size else null,
                subtitlePrefix = moodLabel,
                subtitleSuffix = topArtists.takeIf { it.isNotEmpty() }?.joinToString(", "),
                isPlaying = isPlaying,
                sheetOpen = sheetOpen,
                onPlayMix = onPlayMix,
                onOpenSheet = onOpenSheet
            )
        }
    }
}

/** Compact bar: collage · title + subtitle · arrow · Play. The bar body opens the sheet. */
@Composable
private fun DailyMixBar(
    songs: ImmutableList<Song>,
    subtitle: String,
    /** Set: the subtitle is "prefix · [♪] count · suffix" instead of [subtitle]. */
    songCount: Int? = null,
    subtitlePrefix: String? = null,
    subtitleSuffix: String? = null,
    isPlaying: Boolean,
    sheetOpen: Boolean,
    onPlayMix: () -> Unit,
    onOpenSheet: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val arrowRotation by animateFloatAsState(
        targetValue = if (sheetOpen) 180f else 0f,
        animationSpec = tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized),
        label = "dailyMixArrow"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Open Daily Mix filters",
                onClick = onOpenSheet
            )
            .semantics {
                stateDescription = if (sheetOpen) "Daily Mix sheet open" else "Daily Mix sheet closed"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MixArtCollage(
                songs = songs,
                size = 64,
                corner = 0,
                shape = MaterialTheme.shapes.large
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Mix",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (songCount != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (subtitlePrefix != null) {
                            Text(
                                text = subtitlePrefix + CompactMeta.SEPARATOR,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        CompactCountBadge(
                            count = songCount,
                            trailing = subtitleSuffix,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onPlayMix,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Shuffle else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Shuffle Daily Mix" else "Play Daily Mix"
                )
            }
        }
    }
}

/**
 * The Daily Mix sheet, top to bottom:
 *  1. "View full Daily Mix" (with Play for whatever the filters leave).
 *  2. The mixes made from your filters, in one horizontal row (artists, then genre/era/… mixes).
 *  3. The filter chips: Artist, Genre, Era, Mood, Activity, Tempo, Energy, Album, Language,
 *     Country, Plays, Added, Length, Source. Each opens a picker with a search box on top;
 *     most also take anything you type.
 */
@Composable
fun DailyMixSheet(
    model: DailyMixSheetModel,
    selection: MixFilterSelection,
    /** Online songs that fit the filters are being fetched (the progress bar shows). */
    isFetchingOnline: Boolean = false,
    dailyMixSongs: ImmutableList<Song>,
    currentSongId: String?,
    artistImageFor: (String) -> String?,
    onSelectionChange: (MixFilterSelection) -> Unit,
    onOpenFullDailyMix: () -> Unit,
    onPlayFiltered: () -> Unit,
    onPlayGeneratedMix: (GeneratedMix) -> Unit,
    onOpenGeneratedMix: (GeneratedMix) -> Unit,
    onOpenArtist: (String) -> Unit,
    /** Online suggestions while typing in a filter / the Artist search (see MixOnlineSearchViewModel). */
    onlineSuggest: suspend (MixFilterField, String) -> List<OnlineFilterSuggestion> = { _, _ -> emptyList() },
    /** Whether a field's picker looks anything up online. */
    searchesOnline: (MixFilterField) -> Boolean = { false },
    isOnline: Boolean = true,
    /** Plays a mix for an artist that has no artist mix here (found by search; topped up online). */
    onPlayArtistByName: (String) -> Unit = {},
    /** The artist whose mix is being fetched (a spinner shows on its bubble). */
    loadingArtist: String? = null,
) {
    // The model lags a moment behind a new pick while it's rebuilt off the main thread.
    val building = model.selection != selection
    // Mix | Artist tabs over the results row.
    var resultsTab by rememberSaveable { mutableIntStateOf(0) }
    // The open filter (its picker overlays the sheet) and where its chip sits, for the move.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    // Live chip positions (root coordinates): the picker opens from, and returns to, wherever
    // the chip sits now, even after the row scrolled.
    val chipBounds = remember { androidx.compose.runtime.mutableStateMapOf<String, Rect>() }
    var sheetOrigin by remember { mutableStateOf(Offset.Zero) }
    // 0 = picker closed (on its chip), 1 = open. Shared with the sheet for the background blur.
    val pickerMove = remember { Animatable(0f) }
    val blurReducedMotion = rememberSystemReducedMotion()
    // The chip that has lifted out of the row (it *is* the picker's pill). Stays set until the
    // pill has flown back, so the row re-opens its gap as the pill lands.
    var liftedField by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(picking) {
        val open = picking
        if (open != null) {
            liftedField = open
        } else if (liftedField != null) {
            androidx.compose.runtime.snapshotFlow { pickerMove.value }.first { it <= 0.001f }
            liftedField = null
        }
    }
    // Artist tab: the chips give way to a full-width artist search.
    var artistQuery by rememberSaveable { mutableStateOf("") }
    val artistSearchMode = resultsTab == 1
    // While a picker is open the sheet grows to everything between the status bar and the
    // keyboard (animated with the pill), so the choices list has room above the pill.
    val density = LocalDensity.current
    val windowHeightPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val statusTopPx = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
    val bottomInsetPx = androidx.compose.foundation.layout.WindowInsets.ime
        .union(androidx.compose.foundation.layout.WindowInsets.navigationBars).getBottom(density)
    val sheetChromePx = with(density) { SheetChromeAllowance.roundToPx() }
    val grownHeightPx = (windowHeightPx - statusTopPx - bottomInsetPx - sheetChromePx).coerceAtLeast(0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { sheetOrigin = it.positionInRoot() }
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // Content stays at its natural height and sits at the bottom; the extra height
                // (0 → grown while the picker opens) goes above it.
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                val natural = placeable.height
                val target = maxOf(natural, grownHeightPx).coerceAtMost(constraints.maxHeight)
                val f = if (picking != null || liftedField != null) pickerMove.value.coerceIn(0f, 1f) else 0f
                val height = natural + ((target - natural) * f).toInt()
                layout(placeable.width, height) { placeable.place(0, height - natural) }
            }
            .padding(top = 12.dp)
            .graphicsLayer {
                // A soft blur behind an open filter (Android 12+; below that the scrim alone).
                val radius = pickerMove.value.coerceIn(0f, 1f) * PickerBlurRadius.toPx()
                renderEffect = if (!blurReducedMotion && radius > 0.5f &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                ) {
                    androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Decal)
                } else null
            },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FullDailyMixRow(
            songs = if (selection.isEmpty) dailyMixSongs else model.filtered,
            title = "View full Daily Mix",
            subtitle = when {
                selection.isEmpty -> ""
                building -> "Mixing…"
                model.filtered.isEmpty() -> "Nothing matches yet — loosen a filter"
                model.foundOnline > 0 -> "found ${model.foundOnline} online · ${describeSelection(selection)}"
                else -> describeSelection(selection)
            },
            // "[♪] n" in front of the subtitle whenever there are songs to count.
            songCount = when {
                selection.isEmpty -> dailyMixSongs.size
                building || model.filtered.isEmpty() -> null
                else -> model.filtered.size
            },
            playEnabled = if (selection.isEmpty) dailyMixSongs.isNotEmpty() else model.filtered.isNotEmpty() && !building,
            onOpen = onOpenFullDailyMix,
            onPlay = onPlayFiltered
        )

        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MixArtistTabs(
                    selected = resultsTab,
                    onSelect = { resultsTab = it },
                )
                Spacer(Modifier.weight(1f))
                val count = if (resultsTab == 0) model.generatedMixes.size else model.artistMixes.size
                if (count > 0 && !building) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val progressAlpha by animateFloatAsState(if (building || isFetchingOnline) 1f else 0f, label = "dailyMixBuilding")
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(3.dp)
                    .graphicsLayer { alpha = progressAlpha }
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(6.dp))
            if (artistSearchMode && artistQuery.isNotBlank()) {
                ArtistSearchResultsRow(
                    query = artistQuery,
                    localArtists = remember(model.artistMixes, model.options) {
                        (model.artistMixes.map { it.title } +
                            model.options.optionsFor(MixFilterField.Artist).map { it.value }).distinct()
                    },
                    artistMixes = model.artistMixes,
                    isOnline = isOnline,
                    onlineSuggest = onlineSuggest,
                    artistImageFor = artistImageFor,
                    loadingArtist = loadingArtist,
                    onPlayMix = onPlayGeneratedMix,
                    onPlayArtistByName = onPlayArtistByName,
                    onOpenArtist = onOpenArtist,
                )
            } else MixResultsRow(
                artistMixes = if (resultsTab == 1) model.artistMixes else persistentListOf(),
                generatedMixes = if (resultsTab == 0) model.generatedMixes else persistentListOf(),
                emptyText = if (resultsTab == 0) "No mixes for these filters yet." else "No artist mixes for these filters.",
                currentSongId = currentSongId,
                artistImageFor = artistImageFor,
                onPlayMix = onPlayGeneratedMix,
                onOpenMix = onOpenGeneratedMix,
                onOpenArtist = onOpenArtist,
                onClearFilters = { onSelectionChange(MixFilterSelection()) }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        MixFilterChipRow(
            options = model.options,
            selection = selection,
            onSelectionChange = onSelectionChange,
            chipBounds = chipBounds,
            liftedField = liftedField,
            pickerMove = pickerMove,
            artistSearchMode = artistSearchMode,
            artistQuery = artistQuery,
            onArtistQueryChange = { artistQuery = it.take(60) },
            onOpenField = { field -> picking = field.name }
        )
    }
    MixFilterPickerOverlay(
        field = picking?.let { name -> MixFilterField.entries.firstOrNull { it.name == name } },
        options = model.options,
        selection = selection,
        chipBoundsFor = { name -> chipBounds[name] },
        sheetOrigin = sheetOrigin,
        move = pickerMove,
        onPick = { field, value ->
            picking = null
            onSelectionChange(selection.with(field, value))
        },
        onClear = { field -> onSelectionChange(selection.with(field, null)) },
        onClose = { picking = null },
        onlineSuggest = onlineSuggest,
        searchesOnline = searchesOnline,
        isOnline = isOnline,
    )
    }
}

/** Mix | Artist: general mixes and artist mixes, one at a time. */
@Composable
private fun MixArtistTabs(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = rememberSystemReducedMotion()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf("Mix", "Artist").forEachIndexed { index, label ->
            val isSelected = index == selected
            val spec = if (reducedMotion) androidx.compose.animation.core.snap<androidx.compose.ui.graphics.Color>()
                else tween<androidx.compose.ui.graphics.Color>(MotionTokens.DurationMedium1, easing = MotionTokens.Emphasized)
            val bg by androidx.compose.animation.animateColorAsState(
                if (isSelected) colors.secondaryContainer else colors.surfaceContainerHigh, spec, label = "mixTabBg"
            )
            val fg by androidx.compose.animation.animateColorAsState(
                if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant, spec, label = "mixTabFg"
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    }
                    .clickable { onSelect(index) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = fg)
            }
        }
    }
}

/** Top of the sheet: the whole Daily Mix (or what the filters leave), with Play. */
@Composable
private fun FullDailyMixRow(
    songs: ImmutableList<Song>,
    title: String,
    subtitle: String,
    playEnabled: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    songCount: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MixArtCollage(songs = songs, size = 52, corner = 0, shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (songCount != null) {
                    CompactCountBadge(
                        count = songCount,
                        trailing = subtitle.ifBlank { null },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                } else {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FilledIconButton(
                onClick = onPlay,
                enabled = playEnabled,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play this mix")
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
    }
}

private fun fieldIcon(field: MixFilterField): ImageVector = when (field) {
    MixFilterField.Artist -> Icons.Rounded.Person
    MixFilterField.Genre -> Icons.Rounded.Category
    MixFilterField.Era -> Icons.Rounded.History
    MixFilterField.Mood -> Icons.Rounded.Mood
    MixFilterField.Activity -> Icons.AutoMirrored.Rounded.DirectionsRun
    MixFilterField.Tempo -> Icons.Rounded.Speed
    MixFilterField.Energy -> Icons.Rounded.Bolt
    MixFilterField.Album -> Icons.Rounded.Album
    MixFilterField.Language -> Icons.Rounded.Translate
    MixFilterField.Country -> Icons.Rounded.Public
    MixFilterField.Plays -> Icons.Rounded.BarChart
    MixFilterField.Added -> Icons.Rounded.Schedule
    MixFilterField.Length -> Icons.Rounded.Timer
    MixFilterField.Source -> Icons.Rounded.PhoneAndroid
}

/** Fields that always get a chip, because you can type your own value. */
private val AlwaysShownFields = setOf(
    MixFilterField.Artist, MixFilterField.Genre, MixFilterField.Era, MixFilterField.Mood, MixFilterField.Activity
)

/**
 * The sheet's bottom row: a clear-all icon pinned at the start (disabled when nothing is set, so
 * the row never jumps), a divider, then one scrolling chip per filter.
 */
/** How soft the sheet behind an open filter gets ("a tiny bit"). */
private val PickerBlurRadius = 6.dp

@Composable
private fun MixFilterChipRow(
    options: MixFilterOptions,
    selection: MixFilterSelection,
    onSelectionChange: (MixFilterSelection) -> Unit,
    /** Where each chip sits (root coordinates), kept current as the row scrolls. */
    chipBounds: MutableMap<String, Rect>,
    /** The chip that is out of the row (it's the picker's pill right now), if any. */
    liftedField: String?,
    /** 0 = pill on the chip, 1 = pill centred: the lifted chip's slot closes as this grows. */
    pickerMove: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    /** Artist tab: a full-width artist search replaces the chips. */
    artistSearchMode: Boolean,
    artistQuery: String,
    onArtistQueryChange: (String) -> Unit,
    /** A chip was tapped and has been scrolled to the middle of the row. */
    onOpenField: (MixFilterField) -> Unit,
) {
    val fields = remember(options, selection) {
        MixFilterField.entries.filter {
            it in AlwaysShownFields || options.optionsFor(it).isNotEmpty() || selection.valueOf(it) != null
        }
    }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val rowScope = rememberCoroutineScope()
    val rowReducedMotion = rememberSystemReducedMotion()
    // Brings the tapped chip to the middle of the row (as far as the ends allow), then opens it.
    fun centreThenOpen(field: MixFilterField, index: Int) {
        rowScope.launch {
            fun delta(): Float? {
                val info = rowState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
                val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                return item.offset + item.size / 2f - centre
            }
            if (delta() == null) {
                if (rowReducedMotion) rowState.scrollToItem(index) else rowState.animateScrollToItem(index)
            }
            delta()?.let { d ->
                if (kotlin.math.abs(d) > 1f) {
                    if (rowReducedMotion) rowState.scrollBy(d) else rowState.animateScrollBy(d)
                }
            }
            // One frame for the moved chip to report its new position.
            androidx.compose.runtime.withFrameNanos { }
            onOpenField(field)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clear all: only there once a filter is set; it slides in from the start of the row.
        androidx.compose.animation.AnimatedVisibility(
            visible = !selection.isEmpty,
            enter = androidx.compose.animation.expandHorizontally(
                tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate), expandFrom = Alignment.Start
            ) + androidx.compose.animation.fadeIn(tween(MotionTokens.DurationShort4)),
            exit = androidx.compose.animation.shrinkHorizontally(
                tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate), shrinkTowards = Alignment.Start
            ) + androidx.compose.animation.fadeOut(tween(MotionTokens.DurationShort3))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onSelectionChange(MixFilterSelection()) },
                    modifier = Modifier.semantics { contentDescription = "Clear all mix filters" }
                ) {
                    Icon(Icons.Rounded.FilterAltOff, contentDescription = null)
                }
                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
        // Mix tab: the chips. Artist tab: one full-width "Search any artist" bar.
        androidx.compose.animation.Crossfade(
            targetState = artistSearchMode,
            animationSpec = if (rowReducedMotion) androidx.compose.animation.core.snap()
                else tween(MotionTokens.DurationMedium1, easing = MotionTokens.Emphasized),
            modifier = Modifier.weight(1f),
            label = "mixRowMode"
        ) { searchMode ->
        if (searchMode) {
            ArtistSearchBar(query = artistQuery, onQueryChange = onArtistQueryChange)
        } else
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 8.dp, end = 16.dp),
            // Locked while a chip is out of the row, so the pill can land back in its slot.
            userScrollEnabled = liftedField == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(fields, key = { _, f -> f.name }) { index, field ->
                val selected = selection.valueOf(field)
                ExpressiveFilterChip(
                    label = selected ?: field.label,
                    selected = selected != null,
                    onClick = { centreThenOpen(field, index) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier
                        // The lifted chip's slot closes (neighbours slide together) as the
                        // pill moves out, and re-opens as it flies back. The chip itself stays
                        // in the list (same key/index), centred in its slot, drawn by the pill.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val slot = if (field.name == liftedField) (1f - pickerMove.value).coerceIn(0f, 1f) else 1f
                            val width = (placeable.width * slot).toInt()
                            layout(width, placeable.height) { placeable.place((width - placeable.width) / 2, 0) }
                        }
                        .graphicsLayer { alpha = if (field.name == liftedField) 0f else 1f }
                        .onGloballyPositioned { chipBounds[field.name] = it.boundsInRoot() }
                        .semantics {
                            contentDescription = "Filter by ${field.label}"
                            stateDescription = selected ?: "Any"
                        }
                )
            }
        }
        }
    }
}

/** The Artist tab's full-width search, in place of the filter chips. */
@Composable
private fun ArtistSearchBar(query: String, onQueryChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp)
            .height(48.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Search any artist", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search any artist" }
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear artist search", tint = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * Artist search results, in place of the artist bubbles (the bar below never moves): matching
 * artists from this mix's pool first (instant), then online artists (Deezer, after a short
 * pause) with a cloud badge. Tap plays the artist's mix, long-press opens the artist page.
 */
@Composable
private fun ArtistSearchResultsRow(
    query: String,
    localArtists: List<String>,
    artistMixes: ImmutableList<GeneratedMix>,
    isOnline: Boolean,
    onlineSuggest: suspend (MixFilterField, String) -> List<OnlineFilterSuggestion>,
    artistImageFor: (String) -> String?,
    loadingArtist: String?,
    onPlayMix: (GeneratedMix) -> Unit,
    onPlayArtistByName: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val trimmed = query.trim()
    val local = remember(localArtists, trimmed) {
        val n = normalizeMixQuery(trimmed)
        localArtists.filter { normalizeMixQuery(it).contains(n) }
            .sortedBy { if (normalizeMixQuery(it).startsWith(n)) 0 else 1 }
            .take(20)
    }
    var online by remember { mutableStateOf<List<OnlineFilterSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(trimmed, isOnline) {
        online = emptyList()
        if (!isOnline || trimmed.length < 2) { searching = false; return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(OnlineSearchDebounceMs)
        online = mergeLocalAndOnline(local, onlineSuggest(MixFilterField.Artist, trimmed))
        searching = false
    }
    val mixesByName = remember(artistMixes) { artistMixes.associateBy { normalizeMixQuery(it.title) } }
    if (local.isEmpty() && online.isEmpty()) {
        Text(
            text = when {
                searching -> "Searching…"
                !isOnline -> "No artist called “$trimmed” here · offline"
                else -> "No artists match “$trimmed”"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp)
        )
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(local, key = { "l_$it" }) { name ->
            val mix = mixesByName[normalizeMixQuery(name)]
            ArtistMixItem(
                name = name,
                imageUrl = artistImageFor(name),
                isCurrent = false,
                loading = loadingArtist == name,
                onClick = { if (mix != null) onPlayMix(mix) else onPlayArtistByName(name) },
                onLongClick = { onOpenArtist(name) },
                modifier = Modifier.animateItem()
            )
        }
        items(online, key = { "o_" + it.value }) { suggestion ->
            ArtistMixItem(
                name = suggestion.value,
                imageUrl = suggestion.imageUrl,
                isCurrent = false,
                online = true,
                loading = loadingArtist == suggestion.value,
                onClick = { onPlayArtistByName(suggestion.value) },
                onLongClick = { onOpenArtist(suggestion.value) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

/** Debounce before an online look-up while typing. */
private const val OnlineSearchDebounceMs = 300L

/** Handle + paddings above the sheet content, kept clear when the sheet grows. */
private val SheetChromeAllowance = 88.dp

/** Gap kept between an open filter's choices and the top of the sheet (below the handle). */
private val PickerTopGap = 12.dp

/**
 * A filter's picker, laid over the sheet. Opening moves a search pill from the tapped chip to the
 * middle of the row, with Clear on its left and Close on its right; the choices rise above it,
 * "Any" first (nearest the pill), and drag down to see the rest. The pill is the search box: it
 * isn't focused on open; tap it to type (it widens), and it narrows again when it loses focus.
 * Close / Back / tapping outside move it back to the chip.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.MixFilterPickerOverlay(
    field: MixFilterField?,
    options: MixFilterOptions,
    selection: MixFilterSelection,
    /** A chip's current bounds by field name, in root coordinates. */
    chipBoundsFor: (String) -> Rect?,
    /** The sheet's top-left, in root coordinates. */
    sheetOrigin: Offset,
    /** 0 = on the chip, 1 = centred; owned by the sheet so it can blur behind. */
    move: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    onPick: (MixFilterField, String?) -> Unit,
    onClear: (MixFilterField) -> Unit,
    onClose: () -> Unit,
    onlineSuggest: suspend (MixFilterField, String) -> List<OnlineFilterSuggestion>,
    searchesOnline: (MixFilterField) -> Boolean,
    isOnline: Boolean,
) {
    val reducedMotion = rememberSystemReducedMotion()
    val motionScheme = MaterialTheme.motionScheme
    // What's drawn: stays set while closing so the exit can play.
    var shownField by remember { mutableStateOf(field) }
    if (field != null && field != shownField) shownField = field
    val open = field != null
    // [move]: 0 = sitting on the chip, 1 = centred. Spatial spring (it's a moving, morphing
    // element); a reopen/close mid-way retargets from wherever it is.
    // Choices list: grows up out of the pill.
    val list = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (reducedMotion) {
            move.snapTo(if (open) 1f else 0f)
            list.snapTo(if (open) 1f else 0f)
        } else if (open) {
            launch { move.animateTo(1f, motionScheme.defaultSpatialSpec<Float>()) }
            list.animateTo(1f, tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate))
        } else {
            launch { list.animateTo(0f, tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) }
            move.animateTo(0f, motionScheme.fastSpatialSpec<Float>())
        }
        if (!open) shownField = null
    }
    androidx.activity.compose.BackHandler(enabled = open) { onClose() }
    val current = shownField ?: return
    val bounds = chipBoundsFor(current.name) ?: return
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val fieldOptions = options.optionsFor(current)
    val selected = selection.valueOf(current)

    var query by rememberSaveable(current) { mutableStateOf("") }
    // Expanded (wide search bar) while open; the keyboard only comes up when the bar is tapped.
    var searching by remember(current) { mutableStateOf(false) }
    var hadFocus by remember(current) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Grow from the chip into the wide bar as it opens; shrink back as it returns to the chip.
    LaunchedEffect(current, open) {
        searching = open
        if (!open) focusManager.clearFocus()
    }
    val trimmed = query.trim()
    val shown = remember(fieldOptions, trimmed) {
        if (trimmed.isEmpty()) fieldOptions
        else {
            val q = trimmed.lowercase(Locale.ROOT)
            fieldOptions.filter { it.value.lowercase(Locale.ROOT).contains(q) }
                .sortedBy { if (it.value.lowercase(Locale.ROOT).startsWith(q)) 0 else 1 }
        }
    }
    val offerCustom = shouldOfferCustom(current.allowsCustom, trimmed, fieldOptions.map { it.value })

    // Online matches below the local ones (Artist / Album / Genre), after a short pause.
    var online by remember(current) { mutableStateOf<List<OnlineFilterSuggestion>>(emptyList()) }
    // 0 = nothing to show, 1 = searching, 2 = done, 3 = offline
    var onlineStatus by remember(current) { mutableIntStateOf(0) }
    LaunchedEffect(current, trimmed, isOnline) {
        online = emptyList()
        if (!searchesOnline(current) || trimmed.length < 2) { onlineStatus = 0; return@LaunchedEffect }
        if (!isOnline && current != MixFilterField.Genre) { onlineStatus = 3; return@LaunchedEffect }
        onlineStatus = 1
        kotlinx.coroutines.delay(OnlineSearchDebounceMs)
        online = mergeLocalAndOnline(shown.map { it.value }, onlineSuggest(current, trimmed))
        onlineStatus = 2
    }
    // The best match always sits right above the pill (and the keyboard) while typing.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(trimmed) { if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) listState.scrollToItem(0) }

    // Clear / Close: fade in after the move starts, out faster than in.
    val sideAlpha by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (reducedMotion) androidx.compose.animation.core.snap<Float>() else if (open)
            tween<Float>(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)
        else tween<Float>(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate),
        label = "pickerSideButtons"
    )

    Box(Modifier.matchParentSize()) {
        // Scrim: dims the sheet; tap to close.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = move.value.coerceIn(0f, 1f) }
                .background(colors.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
        )
        // Everything above the chip row's bottom edge: the choices, then the pill row.
        val barBottom = with(density) { (bounds.bottom - sheetOrigin.y).coerceAtLeast(0f).toDp() }
        // The choices never rise past the sheet's own top edge: they start a small gap below it
        // and scroll inside, however long the list is or wherever the sheet is mid-grow.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(barBottom)
                .padding(top = PickerTopGap)
                .clipToBounds()
                .semantics { paneTitle = "${current.label} filter" },
            verticalArrangement = Arrangement.Bottom
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        val p = list.value
                        alpha = p
                        scaleY = 0.85f + 0.15f * p
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceContainerHigh),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
            ) {
                // Item 0 sits right above the pill.
                if (trimmed.isEmpty()) {
                    // The current choice first (nearest the bar), then "Any", then the rest.
                    if (selected != null) {
                        val selectedOption = fieldOptions.firstOrNull { it.value == selected }
                        item(key = "o_$selected") {
                            PickerRow(
                                label = selected,
                                count = selectedOption?.count ?: 0,
                                isSelected = true,
                                icon = if (selectedOption == null) Icons.Rounded.Edit else null,
                                onClick = { onPick(current, null) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                    item(key = "any") {
                        PickerRow(
                            label = "Any ${current.label.lowercase(Locale.ROOT)}",
                            count = 0,
                            isSelected = selected == null,
                            onClick = { onPick(current, null) },
                            modifier = Modifier.animateItem()
                        )
                    }
                } else if (offerCustom) {
                    item(key = "custom") {
                        PickerRow(
                            label = "Use “$trimmed”",
                            count = 0,
                            isSelected = false,
                            icon = Icons.Rounded.Edit,
                            emphasize = true,
                            onClick = { onPick(current, trimmed) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                items(
                    if (trimmed.isEmpty() && selected != null) shown.filterNot { it.value == selected } else shown,
                    key = { "o_" + it.value }
                ) { option ->
                    PickerRow(
                        label = option.value,
                        count = option.count,
                        isSelected = option.value == selected,
                        onClick = { onPick(current, if (option.value == selected) null else option.value) },
                        modifier = Modifier.animateItem()
                    )
                }
                // From online (reverse layout: these sit above the local matches, header on top).
                items(online, key = { "w_" + it.value }) { suggestion ->
                    PickerRow(
                        label = suggestion.value,
                        supporting = suggestion.subtitle,
                        count = 0,
                        isSelected = suggestion.value == selected,
                        icon = Icons.Rounded.Cloud,
                        onClick = { onPick(current, suggestion.value) },
                        modifier = Modifier.animateItem()
                    )
                }
                if (onlineStatus != 0 && searchesOnline(current) && current != MixFilterField.Genre) {
                    item(key = "online_header") {
                        Text(
                            text = when (onlineStatus) {
                                1 -> "From online · Searching…"
                                3 -> "From online · Offline"
                                else -> if (online.isEmpty()) "From online · Nothing new" else "From online"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
                                .animateItem()
                        )
                    }
                }
                if (shown.isEmpty() && !offerCustom && trimmed.isNotEmpty() && online.isEmpty() && onlineStatus != 1) {
                    item(key = "none") {
                        Text(
                            "Nothing called “$trimmed” here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // The pill row, centred on the chip row's height.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { bounds.height.toDp() }.coerceAtLeast(48.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear: only when there is something to clear (typed text or a chosen value).
                // Its slot keeps its width so the bar never jumps.
                Box(Modifier.size(48.dp).graphicsLayer { alpha = sideAlpha }, contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selected != null || query.isNotEmpty(),
                        enter = androidx.compose.animation.fadeIn(
                            tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)
                        ) + androidx.compose.animation.scaleIn(
                            tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate),
                            initialScale = 0.8f
                        ),
                        exit = androidx.compose.animation.fadeOut(
                            tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)
                        ) + androidx.compose.animation.scaleOut(
                            tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate),
                            targetScale = 0.8f
                        )
                    ) {
                        androidx.compose.material3.FilledTonalIconButton(
                            onClick = {
                                query = ""
                                onClear(current)
                            },
                            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.secondaryContainer,
                                contentColor = colors.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Rounded.FilterAltOff, contentDescription = "Clear ${current.label} filter")
                        }
                    }
                }
                // Where the pill rests (the centre of this slot), in root coordinates.
                var restCenterX by remember { mutableStateOf(bounds.center.x) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { restCenterX = it.boundsInRoot().center.x },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                // Starts over the chip, travels to the centre.
                                translationX = (1f - move.value) * (bounds.center.x - restCenterX)
                            }
                            .animateContentSize(
                                if (reducedMotion) androidx.compose.animation.core.snap<IntSize>()
                                else motionScheme.defaultSpatialSpec<IntSize>()
                            )
                            .then(if (searching) Modifier.fillMaxWidth() else Modifier)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHighest)
                            .clickable(enabled = !searching) { searching = true }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // No per-filter icon (the chips have none); the search glyph shows while typing.
                        if (searching) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        if (searching) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        current.searchHint,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it.take(60) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                                    cursorBrush = SolidColor(colors.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        when {
                                            offerCustom -> onPick(current, trimmed)
                                            shown.isNotEmpty() && trimmed.isNotEmpty() -> onPick(current, shown.first().value)
                                            else -> focusManager.clearFocus()
                                        }
                                    }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { state ->
                                            // Narrow back once the box loses focus (the typed text stays).
                                            // Losing focus hides the keyboard; the bar stays wide while open.
                                            if (state.isFocused) hadFocus = true
                                            else if (hadFocus) hadFocus = false
                                        }
                                )
                            }
                        } else {
                            Text(
                                text = if (query.isNotEmpty()) query else selected ?: current.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                        }
                    }
                }
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = onClose,
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.surfaceContainerHighest,
                        contentColor = colors.onSurface
                    ),
                    modifier = Modifier.graphicsLayer { alpha = sideAlpha }
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close filter")
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    emphasize: Boolean = false,
    modifier: Modifier = Modifier,
    /** Second line (e.g. an online album's artist). */
    supporting: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) colors.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasize || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) colors.onSecondaryContainer else if (emphasize) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (count > 0) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = colors.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
    }
}

/** Artist mixes first, then facet mixes, in one keyed row; crossfades to an empty state. */
@Composable
private fun MixResultsRow(
    artistMixes: ImmutableList<GeneratedMix>,
    generatedMixes: ImmutableList<GeneratedMix>,
    currentSongId: String?,
    artistImageFor: (String) -> String?,
    onPlayMix: (GeneratedMix) -> Unit,
    onOpenMix: (GeneratedMix) -> Unit,
    onOpenArtist: (String) -> Unit,
    onClearFilters: () -> Unit,
    emptyText: String = "Nothing in your library matches those filters yet."
) {
    val reducedMotion = rememberSystemReducedMotion()
    val itemFadeIn = if (reducedMotion) null else
        tween<Float>(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)
    val itemFadeOut = if (reducedMotion) null else
        tween<Float>(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)
    val itemPlacement = if (reducedMotion) null else
        tween<IntOffset>(MotionTokens.DurationMedium1, easing = MotionTokens.Emphasized)

    val isEmpty = artistMixes.isEmpty() && generatedMixes.isEmpty()

    AnimatedContent(
        targetState = isEmpty,
        transitionSpec = {
            if (reducedMotion) {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            } else {
                (fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)))
                    .using(null)
            }
        },
        label = "DailyMixResults"
    ) { empty ->
        if (empty) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClearFilters) { Text("Clear filters") }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(artistMixes, key = { it.id }) { mix ->
                    ArtistMixItem(
                        name = mix.title,
                        imageUrl = artistImageFor(mix.title),
                        isCurrent = currentSongId != null && mix.songs.any { it.id == currentSongId },
                        onClick = { onPlayMix(mix) },
                        onLongClick = { onOpenArtist(mix.title) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = itemFadeIn,
                            placementSpec = itemPlacement,
                            fadeOutSpec = itemFadeOut
                        )
                    )
                }
                items(generatedMixes, key = { it.id }) { mix ->
                    GeneratedMixCard(
                        mix = mix,
                        isCurrent = currentSongId != null && mix.songs.any { it.id == currentSongId },
                        onClick = { onOpenMix(mix) },
                        onPlayClick = { onPlayMix(mix) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = itemFadeIn,
                            placementSpec = itemPlacement,
                            fadeOutSpec = itemFadeOut
                        )
                    )
                }
            }
        }
    }
}

/** Round artist photo + name. Tap plays the artist mix, long-press opens the artist page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistMixItem(
    name: String,
    imageUrl: String?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Found online (not in this mix's pool): a small cloud badge. */
    online: Boolean = false,
    /** Its mix is being fetched: a spinner over the photo. */
    loading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .width(88.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "Play",
                onLongClickLabel = "Open $name"
            )
            .semantics(mergeDescendants = true) { contentDescription = "Play $name mix" }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .then(
                    if (isCurrent) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier
                )
                .padding(if (isCurrent) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(colors.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                SmartImage(
                    model = imageUrl,
                    contentDescription = null,
                    shape = CircleShape,
                    placeholderResId = R.drawable.rounded_artist_24,
                    errorResId = R.drawable.rounded_artist_24,
                    indicatorSize = 24.dp,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSecondaryContainer
                )
            }
            if (loading) {
                Box(
                    Modifier.fillMaxSize().background(colors.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    SongCoverLoadingIndicator(
                        size = 28.dp,
                        color = colors.inverseOnSurface
                    )
                }
            }
        }
        if (online) {
            Icon(
                Icons.Rounded.Cloud,
                contentDescription = "Found online",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A generated playlist card: 2x2 art collage with a play button, title and subtitle. */
@Composable
private fun GeneratedMixCard(
    mix: GeneratedMix,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val container = if (isCurrent) colors.secondaryContainer else colors.surfaceContainer
    val onContainer = if (isCurrent) colors.onSecondaryContainer else colors.onSurface
    val onContainerVariant = if (isCurrent) colors.onSecondaryContainer else colors.onSurfaceVariant

    Column(
        modifier = modifier
            .width(140.dp)
            .clip(MaterialTheme.shapes.large)
            .offlineVeil(rememberSongsOfflineBlocked(mix.songs), MaterialTheme.shapes.large)
            .background(container)
            .clickable(onClickLabel = "Open ${mix.title}", onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(124.dp)) {
            MixArtCollage(
                songs = mix.songs,
                size = 124,
                corner = 0,
                shape = MaterialTheme.shapes.medium
            )
            Surface(
                onClick = onPlayClick,
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${mix.title}"
                    )
                }
            }
        }
        Text(
            text = mix.title,
            style = MaterialTheme.typography.titleSmall,
            color = onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // "[♪] 25 · artist" (the count is the music note, not "25 tracks").
        CompactCountBadge(
            count = mix.songs.size,
            trailing = mix.subtitle.ifBlank { null },
            style = MaterialTheme.typography.bodySmall,
            color = onContainerVariant
        )
    }
}

/**
 * 2x2 album-art collage for a mix. Falls back to a single cover when the mix has
 * fewer than four distinct covers. Pass [shape] to use a theme shape; [corner] is
 * kept for existing callers and only used when [shape] is not given.
 */
@Composable
fun MixArtCollage(
    songs: ImmutableList<Song>,
    size: Int,
    corner: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(corner.dp)
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
            .clip(shape)
            .background(colors.surfaceContainerHigh)
    ) {
        if (covers.size < 4) {
            SmartImage(
                model = covers.firstOrNull(),
                contentDescription = null,
                shape = shape,
                indicatorSize = (size * 0.45f).coerceIn(20f, 36f).dp,
                modifier = Modifier.size(size.dp)
            )
        } else {
            val tileSize = size / 2
            val tileIndicatorSize = (tileSize * 0.45f).coerceIn(14f, 24f).dp
            Column {
                Row {
                    CollageTile(covers[0], tileSize, tileIndicatorSize)
                    CollageTile(covers[1], tileSize, tileIndicatorSize)
                }
                Row {
                    CollageTile(covers[2], tileSize, tileIndicatorSize)
                    CollageTile(covers[3], tileSize, tileIndicatorSize)
                }
            }
        }
    }
}

@Composable
private fun CollageTile(cover: String?, size: Int, indicatorSize: Dp) {
    SmartImage(
        model = cover,
        contentDescription = null,
        indicatorSize = indicatorSize,
        modifier = Modifier.size(size.dp)
    )
}

/** Keeps the chosen filters across configuration change / process death. */
val MixFilterSelectionSaver =
    androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.MutableState<MixFilterSelection>, String>(
        save = { state -> state.value.toSaved() },
        restore = { saved -> mutableStateOf(MixFilterSelection.fromSaved(saved)) }
    )
