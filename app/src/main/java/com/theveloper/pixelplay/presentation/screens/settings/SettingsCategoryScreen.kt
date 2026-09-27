package com.theveloper.pixelplay.presentation.screens.settings

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.FileExplorerDialog
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel


import androidx.compose.runtime.CompositionLocalProvider
import com.theveloper.pixelplay.presentation.screens.LocalHighlightSettingKey
import com.theveloper.pixelplay.presentation.screens.LocalHighlightScrollRequest

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsCategoryScreen(
    categoryId: String,
    highlight: String? = null,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    statsViewModel: com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val category = SettingsCategory.fromId(categoryId)
    if (category == null) {
        android.util.Log.w("SettingsCategoryScreen", "Unknown settings category: $categoryId")
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.settings_unknown_category))
        }
        return
    }

    // Some categories are rendered by their own dedicated screen rather than by this
    // host. Reaching them through settings_category/{id} (a saved deep link, a search
    // result, or a restored back stack) used to render a top bar over an empty body.
    // Redirect to the real destination instead, replacing this entry so Back still
    // returns to the settings root.
    val selfHostedRoute = when (category) {
        SettingsCategory.ABOUT -> Screen.About.route
        SettingsCategory.EQUALIZER -> Screen.Equalizer.route
        SettingsCategory.DEVICE_CAPABILITIES -> Screen.DeviceCapabilities.route
        else -> null
    }
    if (selfHostedRoute != null) {
        LaunchedEffect(selfHostedRoute) {
            navController.navigateSafelyReplacing(
                route = selfHostedRoute,
                patternToPop = Screen.SettingsCategory.route
            )
        }
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    val context = LocalContext.current

    // State Collection (Duplicated from SettingsScreen for now to ensure functionality)
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val currentAiApiKey by settingsViewModel.currentAiApiKey.collectAsStateWithLifecycle()
    val currentAiModel by settingsViewModel.currentAiModel.collectAsStateWithLifecycle()
    val currentPath by settingsViewModel.currentPath.collectAsStateWithLifecycle()
    val directoryChildren by settingsViewModel.currentDirectoryChildren.collectAsStateWithLifecycle()
    val availableStorages by settingsViewModel.availableStorages.collectAsStateWithLifecycle()
    val selectedStorageIndex by settingsViewModel.selectedStorageIndex.collectAsStateWithLifecycle()
    val isLoadingDirectories by settingsViewModel.isLoadingDirectories.collectAsStateWithLifecycle()
    val isExplorerPriming by settingsViewModel.isExplorerPriming.collectAsStateWithLifecycle()
    val isExplorerReady by settingsViewModel.isExplorerReady.collectAsStateWithLifecycle()
    val isCurrentDirectoryResolved by settingsViewModel.isCurrentDirectoryResolved.collectAsStateWithLifecycle()
    val dataTransferProgress by settingsViewModel.dataTransferProgress.collectAsStateWithLifecycle()
    val explorerRoot = settingsViewModel.explorerRoot()

    // Local State
    var showExplorerSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsViewModel.dataTransferEvents.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }



    // TopBar Animations (identical to SettingsScreen)
    // TopBar Animations (identical to SettingsScreen)
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    
    val categoryTitle = stringResource(category.titleRes)
    val isLongTitle = categoryTitle.length > 13
    
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = if (isLongTitle) 200.dp else 180.dp //for 2 lines use 220 and make text use \n

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    
    val titleMaxLines = if (isLongTitle) 2 else 1

    val topBarHeight = remember(maxTopBarHeightPx) { Animatable(maxTopBarHeightPx) }
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
                                (lazyListState.firstVisibleItemIndex > 0 ||
                                        lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight =
                        (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    // Scrolls a search-highlighted row into view. All category content lives inside a
    // single LazyColumn item, so animateScrollToItem cannot target an individual row —
    // the row reports its own position and we scroll by the delta instead. The target
    // rest position sits just below the fully expanded top bar, because a programmatic
    // scroll bypasses the nested-scroll connection and so does not collapse it.
    val highlightScrollRequest: (Float) -> Unit = remember(coroutineScope, lazyListState) {
        { rowWindowY ->
            coroutineScope.launch {
                val desiredY = maxTopBarHeightPx + HIGHLIGHT_TOP_MARGIN_PX
                val delta = rowWindowY - desiredY
                if (kotlin.math.abs(delta) > HIGHLIGHT_SCROLL_EPSILON_PX) {
                    runCatching { lazyListState.animateScrollBy(delta) }
                }
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                    lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue =
                    if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
        modifier =
            Modifier.nestedScroll(nestedScrollConnection).fillMaxSize()
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
        ) {
            item {
               CompositionLocalProvider(
                   LocalHighlightSettingKey provides highlight,
                   LocalHighlightScrollRequest provides highlightScrollRequest
               ) {
                   Column(
                        modifier = Modifier.background(Color.Transparent)
                   ) {
                        when (category) {
                            SettingsCategory.LIBRARY -> {
                                LibrarySettingsContent(
                                    navController = navController,
                                    playerViewModel = playerViewModel,
                                    settingsViewModel = settingsViewModel,
                                    uiState = uiState,
                                    onShowExplorerSheet = { showExplorerSheet = true }
                                )
                            }
                            SettingsCategory.GENERAL -> {
                                GeneralSettingsContent(
                                    settingsViewModel = settingsViewModel,
                                    uiState = uiState
                                )
                            }
                            // "Player & Lyrics"; the id is still "lyrics".
                            SettingsCategory.LYRICS -> {
                                PlayerLyricsSettingsContent(
                                    navController = navController,
                                    playerViewModel = playerViewModel,
                                    settingsViewModel = settingsViewModel,
                                    uiState = uiState
                                )
                            }
                            SettingsCategory.APPEARANCE -> {
                                AppearanceSettingsContent(
                                    navController = navController,
                                    settingsViewModel = settingsViewModel,
                                    uiState = uiState
                                )
                            }
                            SettingsCategory.PLAYBACK -> {
                                PlaybackSettingsContent(
                                    navController = navController,
                                    settingsViewModel = settingsViewModel,
                                    uiState = uiState
                                )
                            }
                            SettingsCategory.SERVICES -> {
                                ServicesSettingsContent(
                                    navController = navController,
                                    settingsViewModel = settingsViewModel
                                )
                            }
                            SettingsCategory.WIDGETS -> {
                                // Backed by its own ViewModel, so it takes neither
                                // settingsViewModel nor the shared uiState.
                                WidgetsSettingsContent()
                            }
                            SettingsCategory.AI_INTEGRATION -> {
                                AiSettingsContent(
                                    settingsViewModel = settingsViewModel,
                                    uiState = uiState
                                )
                            }
                            SettingsCategory.BACKUP_RESTORE -> {
                                BackupSettingsContent(
                                    settingsViewModel = settingsViewModel,
                                    uiState = uiState
                                )
                            }
                            SettingsCategory.DEVELOPER -> {
                                DeveloperSettingsContent(
                                    navController = navController,
                                    playerViewModel = playerViewModel,
                                    settingsViewModel = settingsViewModel,
                                    statsViewModel = statsViewModel
                                )
                            }
                            // Unreachable, kept for `when` exhaustiveness. The first three
                            // are redirected to their own screens at the top of this
                            // composable; the last two are merged pages that fromId()
                            // already resolves to Player & Lyrics and General.
                            SettingsCategory.ABOUT,
                            SettingsCategory.EQUALIZER,
                            SettingsCategory.DEVICE_CAPABILITIES,
                            SettingsCategory.NOW_PLAYING,
                            SettingsCategory.NAVIGATION -> Unit
                        }
                   }
               }
            }

            item {
                // Spacer handled by contentPadding
                Spacer(Modifier.height(1.dp))
            }
        }

        CollapsibleCommonTopBar(
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBackClick,
            title = categoryTitle,
            maxLines = titleMaxLines
        )
    }

    BackupTransferProgressDialogHost(progress = dataTransferProgress)

    // Dialogs
    FileExplorerDialog(
        visible = showExplorerSheet,
        currentPath = currentPath,
        directoryChildren = directoryChildren,
        availableStorages = availableStorages,
        selectedStorageIndex = selectedStorageIndex,
        isLoading = isLoadingDirectories,
        isPriming = isExplorerPriming,
        isReady = isExplorerReady,
        isCurrentDirectoryResolved = isCurrentDirectoryResolved,
        isAtRoot = settingsViewModel.isAtRoot(),
        rootDirectory = explorerRoot,
        onNavigateTo = settingsViewModel::loadDirectory,
        onNavigateUp = settingsViewModel::navigateUp,
        onNavigateHome = { settingsViewModel.loadDirectory(explorerRoot) },
        onToggleAllowed = settingsViewModel::toggleDirectoryAllowed,
        onRefresh = settingsViewModel::refreshExplorer,
        onStorageSelected = settingsViewModel::selectStorage,
        onDone = {
            settingsViewModel.applyPendingDirectoryRuleChanges()
            showExplorerSheet = false
        },
        onDismiss = {
            settingsViewModel.applyPendingDirectoryRuleChanges()
            showExplorerSheet = false
        }
    )
}

/** Gap left between the expanded top bar and a search-highlighted row, in px. */
private const val HIGHLIGHT_TOP_MARGIN_PX = 48f
/** Below this delta the row is already close enough; don't animate. */
private const val HIGHLIGHT_SCROLL_EPSILON_PX = 4f
