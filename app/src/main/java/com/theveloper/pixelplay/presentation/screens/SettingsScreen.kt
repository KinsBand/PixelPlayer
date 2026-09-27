package com.theveloper.pixelplay.presentation.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.AppLanguage
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.settings.SettingsRegistry
import com.theveloper.pixelplay.presentation.settings.SettingsSearch
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class SettingRow(
    val key: String? = null,
    val title: String,
    val description: String,
    val categoryId: String,
    val route: String? = null,
    val keywords: List<String> = emptyList(),
    val isToggle: Boolean = false,
    val toggleChecked: Boolean = false,
    val onToggleChange: ((Boolean) -> Unit)? = null
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Animation effects
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) { transitionState.targetState = true }

    val transition = rememberTransition(transitionState, label = "SettingsAppearTransition")

    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) }
    ) { if (it) 1f else 0f }

    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
    ) { if (it) 0.dp else 40.dp }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()

    // Values Helper for summary badges
    val themeText = when (uiState.appThemeMode) {
        AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        else -> stringResource(R.string.settings_theme_follow_system)
    }

    val playerThemeText = when (uiState.playerThemePreference) {
        ThemePreference.ALBUM_ART -> stringResource(R.string.settings_player_theme_album_art)
        ThemePreference.DYNAMIC -> stringResource(R.string.settings_player_theme_dynamic)
        else -> uiState.playerThemePreference
    }

    val librarySummary = if (uiState.minSongDuration > 0) {
        stringResource(R.string.settings_summary_min_duration, uiState.minSongDuration / 1000)
    } else {
        stringResource(R.string.settings_category_music_management_subtitle)
    }

    val crossfadeText = if (uiState.isCrossfadeEnabled) {
        stringResource(R.string.settings_summary_crossfade_on, uiState.crossfadeDuration / 1000)
    } else {
        stringResource(R.string.settings_summary_crossfade_off)
    }

    val aiBadge = stringResource(R.string.settings_ai_badge_on_device)

    // Same source the language picker in General reads, so the badge always matches it.
    val languageText = AppLanguage.getLanguageOptions(context)[uiState.appLanguageTag]

    val listenBrainzToken by settingsViewModel.listenBrainzToken.collectAsStateWithLifecycle()
    val servicesText = if (listenBrainzToken.isNotBlank()) {
        stringResource(R.string.settings_summary_scrobbling_on)
    } else {
        null
    }

    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = isSearchActive || searchQuery.isNotEmpty()) {
        isSearchActive = false
        searchQuery = ""
    }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    // Derived, not written from a LaunchedEffect. The previous version restarted a
    // coroutine on every animation frame of the header just to assign this float.
    val collapseFraction by remember {
        derivedStateOf {
            1f - ((topBarHeight.value - minTopBarHeightPx) /
                (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    val searchRows = rememberSettingRows(uiState = uiState, settingsViewModel = settingsViewModel)

    // Normalising 57 rows is the expensive half of search, and it only changes when the
    // rows themselves do — not on every keystroke.
    val searchIndex = remember(searchRows) {
        searchRows.map { row ->
            SettingsSearch.Indexed(
                value = row,
                title = SettingsSearch.normalize(row.title),
                description = SettingsSearch.normalize(row.description),
                keywords = row.keywords.map { SettingsSearch.normalize(it) },
                categoryName = SettingsSearch.normalize(row.categoryId)
            )
        }
    }

    // Debounced so typing does not run a full scan per character on the composition
    // thread, and so the result list is not rebuilt mid-word.
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            debouncedQuery = ""
        } else {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            debouncedQuery = searchQuery
        }
    }

    val filteredRows = remember(debouncedQuery, searchIndex) {
        SettingsSearch.search(debouncedQuery, searchIndex)
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            }
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

        val imeBottomAboveNavBar = with(density) {
            (WindowInsets.ime.getBottom(density) - WindowInsets.navigationBars.getBottom(density))
                .coerceAtLeast(0)
                .toDp()
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + imeBottomAboveNavBar + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (searchQuery.isNotBlank()) {
                // "No results" is keyed on the debounced query, not the live one.
                // Keying it on searchQuery would flash the empty state for one debounce
                // interval after every keystroke, before the search has actually run.
                if (filteredRows.isEmpty()) {
                    if (debouncedQuery.isNotBlank()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_search_no_results,
                                        debouncedQuery
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredRows) { row ->
                        SearchResultRow(
                            row = row,
                            searchQuery = debouncedQuery,
                            onClick = {
                                if (row.route != null) {
                                    navController.navigateSafely(row.route)
                                } else {
                                    navController.navigateSafely(
                                        Screen.SettingsCategory.createRoute(row.categoryId, highlight = row.key)
                                    )
                                }
                            }
                        )
                    }
                }
            } else {
                // Always-visible way into search. The top-bar icon stays for when the
                // header is collapsed; this is what people see first.
                item(key = "search_pill") {
                    SettingsSearchPill(onClick = { isSearchActive = true })
                }

                item(key = "quick_preferences") {
                    QuickPreferencesHeroCard(
                        uiState = uiState,
                        settingsViewModel = settingsViewModel
                    )
                }

                // Ten rows in four groups. Every setting has exactly one home under
                // one of them; see SettingsRegistry for where each one lives.
                item(key = "group_your_app") {
                    SettingsSectionHeader(title = stringResource(R.string.settings_group_your_app))
                    SettingsGroupCard {
                        RootCategoryRow(SettingsCategory.GENERAL, currentValue = languageText) {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute(SettingsCategory.GENERAL.id))
                        }
                        RootRowDivider()
                        RootCategoryRow(SettingsCategory.APPEARANCE, currentValue = themeText) {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute(SettingsCategory.APPEARANCE.id))
                        }
                        RootRowDivider()
                        RootCategoryRow(SettingsCategory.LYRICS, currentValue = playerThemeText) {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute(SettingsCategory.LYRICS.id))
                        }
                        RootRowDivider()
                        RootCategoryRow(SettingsCategory.WIDGETS) {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute(SettingsCategory.WIDGETS.id))
                        }
                    }
                }

                item(key = "group_music") {
                    SettingsSectionHeader(title = stringResource(R.string.settings_group_music))
                    SettingsGroupCard {
                        RootCategoryRow(SettingsCategory.PLAYBACK, currentValue = crossfadeText) {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute(SettingsCategory.PLAYBACK.id))
                        }
                        RootRowDivider()
                        RootCategoryRow(SettingsCategory.LIBRARY, currentValue = librarySummary) {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute(SettingsCategory.LIBRARY.id))
                        }
                    }
                }

                item(key = "group_connections") {
                    SettingsSectionHeader(title = stringResource(R.string.settings_group_connections_data))
                    SettingsGroupCard {
                        RootCategoryRow(SettingsCategory.SERVICES, currentValue = servicesText) {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute(SettingsCategory.SERVICES.id))
                        }
                        RootRowDivider()
                        RootCategoryRow(SettingsCategory.AI_INTEGRATION, currentValue = aiBadge) {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute(SettingsCategory.AI_INTEGRATION.id))
                        }
                        RootRowDivider()
                        RootCategoryRow(SettingsCategory.BACKUP_RESTORE) {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute(SettingsCategory.BACKUP_RESTORE.id))
                        }
                    }
                }

                // About also holds Device Information and Developer options.
                item(key = "group_about") {
                    SettingsSectionHeader(title = stringResource(R.string.settings_group_about))
                    SettingsGroupCard {
                        RootCategoryRow(SettingsCategory.ABOUT, currentValue = versionName) {
                            navController.navigateSafely(Screen.About.route)
                        }
                    }
                }
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.common_settings),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigationIconClick,
            actions = {
                FilledIconButton(
                    modifier = Modifier.padding(end = 12.dp),
                    onClick = { isSearchActive = true },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.common_search)
                    )
                }
            }
        )

        // Animated In-Header Search Bar Overlay
        AnimatedVisibility(
            visible = isSearchActive || searchQuery.isNotEmpty(),
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
        ) {
            LaunchedEffect(Unit) {
                searchFocusRequester.requestFocus()
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .height(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.settings_search_cd_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPreferencesHeroCard(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Icon badge + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.settings_quick_actions_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Theme Mode: 3-option visual selector
            val themeModes = listOf(
                Triple(AppThemeMode.FOLLOW_SYSTEM, stringResource(R.string.settings_theme_follow_system), Icons.Rounded.BrightnessAuto),
                Triple(AppThemeMode.LIGHT, stringResource(R.string.settings_theme_light), Icons.Rounded.LightMode),
                Triple(AppThemeMode.DARK, stringResource(R.string.settings_theme_dark), Icons.Rounded.DarkMode)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themeModes.forEach { (mode, label, icon) ->
                    val isSelected = uiState.appThemeMode == mode
                    val containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                    }
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val border = if (isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    } else {
                        null
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                settingsViewModel.setAppThemeMode(mode)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = containerColor,
                        border = border
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // Quick Toggle Tiles (Haptics & Background Playback side-by-side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Haptics Tile
                QuickToggleTile(
                    title = stringResource(R.string.settings_haptic_feedback_title),
                    isEnabled = uiState.hapticsEnabled,
                    icon = Icons.Rounded.Vibration,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        settingsViewModel.setHapticsEnabled(!uiState.hapticsEnabled)
                    },
                    modifier = Modifier.weight(1f)
                )

                // 2. Background Playback Tile
                QuickToggleTile(
                    title = stringResource(R.string.settings_keep_playing_title),
                    isEnabled = uiState.keepPlayingInBackground,
                    icon = Icons.Rounded.PlayCircle,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        settingsViewModel.setKeepPlayingInBackground(!uiState.keepPlayingInBackground)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickToggleTile(
    title: String,
    isEnabled: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isEnabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
    }
    val contentColor = if (isEnabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (isEnabled) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }

    Surface(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(20.dp))
            .toggleable(
                value = isEnabled,
                role = Role.Switch,
                onValueChange = { onClick() }
            ),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Small status indicator dot
                Surface(
                    shape = CircleShape,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.size(8.dp)
                ) {}
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isEnabled) stringResource(R.string.settings_label_on) else stringResource(R.string.settings_label_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    row: SettingRow,
    searchQuery: String,
    onClick: () -> Unit
) {
    val onToggleChange = row.onToggleChange
    // A result row that toggles in place is a switch, not a button. Surface's own
    // onClick would report it as a button with no state, and the Switch inside is
    // non-interactive, so the checked state would never be announced. Driving the
    // input from Modifier.toggleable instead makes the row one TalkBack stop with
    // the correct role and state.
    val interactionModifier = if (row.isToggle && onToggleChange != null) {
        Modifier.toggleable(
            value = row.toggleChecked,
            role = Role.Switch
        ) { newValue -> onToggleChange(newValue) }
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(interactionModifier)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rememberHighlightedText(row.title, searchQuery),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (row.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = row.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.settings_search_result_section,
                        getCategoryDisplayName(row.categoryId)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (row.isToggle && row.onToggleChange != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = row.toggleChecked,
                    onCheckedChange = null
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun rememberHighlightedText(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary
    return remember(text, query, highlightColor) {
        if (query.isBlank()) {
            buildAnnotatedString { append(text) }
        } else {
            buildAnnotatedString {
                var currentIndex = 0
                val lowerText = text.lowercase()
                val lowerQuery = query.lowercase().trim()
                while (currentIndex < text.length) {
                    val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
                    if (matchIndex < 0) {
                        append(text.substring(currentIndex))
                        break
                    }
                    if (matchIndex > currentIndex) {
                        append(text.substring(currentIndex, matchIndex))
                    }
                    withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.ExtraBold)) {
                        append(text.substring(matchIndex, (matchIndex + lowerQuery.length).coerceAtMost(text.length)))
                    }
                    currentIndex = matchIndex + lowerQuery.length
                }
            }
        }
    }
}

@Composable
fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                content = content
            )
        }
    )
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

@Composable
fun MaintenanceRow(
    title: String,
    @Suppress("UNUSED_PARAMETER") description: String, // kept for callers; cards show titles only
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun MainSettingsCategoryRow(
    title: String,
    @Suppress("UNUSED_PARAMETER") subtitle: String, // kept for callers; cards show titles only
    icon: ImageVector? = null,
    iconRes: Int? = null,
    colors: Pair<Color, Color>,
    currentValue: String? = null,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Icon Container
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.first)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.second,
                    modifier = Modifier.size(20.dp)
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = colors.second,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (currentValue != null) {
            // Capped so a long translated value (German and Russian run ~40% longer
            // than English here) cannot squeeze the title column to nothing.
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .widthIn(max = 120.dp)
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Display name for a settings category, used by search results.
 *
 * Resolved from [SettingsCategory.titleRes] rather than a hand-written map, so it is
 * localised in every shipped locale and cannot drift from the category titles shown on
 * the rows themselves. Accounts has its own screen and no enum entry, so it is the one
 * explicit case.
 */
@Composable
fun getCategoryDisplayName(categoryId: String): String {
    SettingsCategory.fromId(categoryId)?.let { return stringResource(it.titleRes) }
    return if (categoryId == "accounts") {
        stringResource(R.string.settings_category_accounts_title)
    } else {
        categoryId
    }
}

/** A searchable row's live on/off state, for the settings that can be toggled inline. */
private class ToggleBinding(val checked: Boolean, val onChange: (Boolean) -> Unit)

/**
 * Maps a registry key to the state and setter that back it.
 *
 * Kept separate from [SettingsRegistry] on purpose: the registry stays plain data so it
 * can be unit-tested without Compose, while the live wiring lives here next to the
 * ViewModel. A key with no binding is simply not toggleable from search results.
 */
private fun toggleBindingFor(
    key: String,
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    useSmoothCorners: Boolean
): ToggleBinding? = when (key) {
    "smooth_corners" -> ToggleBinding(useSmoothCorners) { settingsViewModel.setUseSmoothCorners(it) }
    "disable_blur" -> ToggleBinding(uiState.disableBlurAllOver) { settingsViewModel.setDisableBlurAllOver(it) }
    "show_scrollbar" -> ToggleBinding(uiState.showScrollbar) { settingsViewModel.setShowScrollbar(it) }
    "folder_back_gesture" -> ToggleBinding(uiState.folderBackGestureNavigation) { settingsViewModel.setFolderBackGestureNavigation(it) }
    "tap_bg_closes" -> ToggleBinding(uiState.tapBackgroundClosesPlayer) { settingsViewModel.setTapBackgroundClosesPlayer(it) }
    "haptic_feedback" -> ToggleBinding(uiState.hapticsEnabled) { settingsViewModel.setHapticsEnabled(it) }
    "hifi_mode" -> ToggleBinding(uiState.hiFiModeEnabled) { settingsViewModel.setHiFiModeEnabled(it) }
    "replaygain" -> ToggleBinding(uiState.replayGainEnabled) { settingsViewModel.setReplayGainEnabled(it) }
    "crossfade" -> ToggleBinding(uiState.isCrossfadeEnabled) { settingsViewModel.setCrossfadeEnabled(it) }
    "persistent_shuffle" -> ToggleBinding(uiState.persistentShuffleEnabled) { settingsViewModel.setPersistentShuffleEnabled(it) }
    "queue_history" -> ToggleBinding(uiState.showQueueHistory) { settingsViewModel.setShowQueueHistory(it) }
    "cast_autoplay" -> ToggleBinding(!uiState.disableCastAutoplay) { settingsViewModel.setDisableCastAutoplay(!it) }
    "pause_on_zero" -> ToggleBinding(uiState.pauseOnVolumeZero) { settingsViewModel.setPauseOnVolumeZero(it) }
    "headphones_resume" -> ToggleBinding(uiState.resumeOnHeadsetReconnect) { settingsViewModel.setResumeOnHeadsetReconnect(it) }
    "keep_playing" -> ToggleBinding(uiState.keepPlayingInBackground) { settingsViewModel.setKeepPlayingInBackground(it) }
    "auto_scan_lrc" -> ToggleBinding(uiState.autoScanLrcFiles) { settingsViewModel.setAutoScanLrcFiles(it) }
    "lyrics_integration" -> ToggleBinding(uiState.lyricsIntegrationEnabled) { settingsViewModel.setLyricsIntegrationEnabled(it) }
    "immersive_lyrics" -> ToggleBinding(uiState.immersiveLyricsEnabled) { settingsViewModel.setImmersiveLyricsEnabled(it) }
    "offline_mode" -> ToggleBinding(uiState.offlineMode) { settingsViewModel.setOfflineMode(it) }
    "safe_token" -> ToggleBinding(uiState.isSafeTokenLimitEnabled) { settingsViewModel.setSafeTokenLimitEnabled(it) }
    else -> null
}

/**
 * Builds the search index from [SettingsRegistry].
 *
 * Previously this allocated 57 rows, ~114 stringResource lookups and 20 lambdas on every
 * recomposition — on a screen whose collapsing header recomposes per scroll frame. Now
 * the static half is resolved once per configuration and only the toggle bindings are
 * rebuilt when the state they read actually changes.
 */
@Composable
fun rememberSettingRows(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel
): List<SettingRow> {
    val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Static text depends only on the locale, so it survives every other state change.
    val staticRows = remember(context, configuration) {
        SettingsRegistry.entries.map { entry ->
            SettingRow(
                key = entry.key,
                title = context.getString(entry.titleRes),
                description = entry.descriptionRes?.let { context.getString(it) }.orEmpty(),
                categoryId = entry.categoryId,
                route = entry.route,
                keywords = entry.keywords
            )
        }
    }

    return remember(staticRows, uiState, useSmoothCorners) {
        staticRows.map { row ->
            val binding = toggleBindingFor(row.key.orEmpty(), uiState, settingsViewModel, useSmoothCorners)
            if (binding == null) {
                row
            } else {
                row.copy(
                    isToggle = true,
                    toggleChecked = binding.checked,
                    onToggleChange = binding.onChange
                )
            }
        }
    }
}

/** One row on the settings root, driven by the category's own title, icon and colours. */
@Composable
private fun RootCategoryRow(
    category: SettingsCategory,
    currentValue: String? = null,
    onClick: () -> Unit
) {
    MainSettingsCategoryRow(
        title = stringResource(category.titleRes),
        subtitle = stringResource(category.subtitleRes),
        icon = category.icon,
        iconRes = category.iconRes,
        colors = getCategoryColors(category),
        currentValue = currentValue,
        onClick = onClick
    )
}

@Composable
private fun RootRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * A tappable search field at the top of the settings root. It opens the same search
 * mode as the top-bar icon; it exists because an icon alone is easy to miss.
 */
@Composable
private fun SettingsSearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.settings_search_pill_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun getCategoryColors(category: SettingsCategory): Pair<Color, Color> {
    val colorScheme = MaterialTheme.colorScheme
    return when (category) {
        SettingsCategory.GENERAL -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        SettingsCategory.SERVICES -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        SettingsCategory.LIBRARY -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        SettingsCategory.LYRICS -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        SettingsCategory.APPEARANCE -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        SettingsCategory.PLAYBACK -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        SettingsCategory.NOW_PLAYING -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        SettingsCategory.NAVIGATION -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        SettingsCategory.WIDGETS -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        SettingsCategory.AI_INTEGRATION -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        SettingsCategory.BACKUP_RESTORE -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        SettingsCategory.DEVELOPER -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        SettingsCategory.EQUALIZER -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        SettingsCategory.DEVICE_CAPABILITIES -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        SettingsCategory.ABOUT -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
    }
}

/** Typing pause before settings search runs, in milliseconds. */
private const val SEARCH_DEBOUNCE_MS = 120L
