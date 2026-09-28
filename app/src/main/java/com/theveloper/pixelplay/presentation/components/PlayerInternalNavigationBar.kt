package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.BottomNavItem
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.scoped.CustomNavigationBarItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.Modifier
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import com.theveloper.pixelplay.presentation.components.voicesearch.VoiceCapturePhase
import com.theveloper.pixelplay.presentation.components.voicesearch.VoiceSpeechCaptureState

internal val NavBarContentHeight = 90.dp // Altura del contenido de la barra de navegación
internal val NavBarCompactContentHeight = 64.dp
internal val NavBarContentHeightFullWidth = NavBarContentHeight // Altura del contenido de la barra de navegación en modo completo
private val MainScreenBottomGradientExtraHeight = MiniPlayerHeight + MiniPlayerBottomSpacer + 8.dp
// Some OEM freeform/floating-window modes can report a bottom inset close to the whole window height.
internal val MaxNavigationBarBottomInset = 96.dp

internal fun sanitizeNavigationBarBottomInset(systemNavBarInset: Dp): Dp {
    if (!systemNavBarInset.value.isFinite()) return 0.dp
    return systemNavBarInset.coerceIn(0.dp, MaxNavigationBarBottomInset)
}

internal fun calculatePlayerSheetCollapsedTargetY(
    containerHeightPx: Float,
    collapsedContentHeightPx: Float,
    bottomMarginPx: Float,
    bottomSpacerPx: Float
): Float {
    val safeContainerHeightPx = containerHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeCollapsedContentHeightPx = collapsedContentHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomMarginPx = bottomMarginPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomSpacerPx = bottomSpacerPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val maxTargetY = (safeContainerHeightPx - safeCollapsedContentHeightPx).coerceAtLeast(0f)

    return (safeContainerHeightPx - safeCollapsedContentHeightPx - safeBottomMarginPx - safeBottomSpacerPx)
        .coerceIn(0f, maxTargetY)
}

internal fun resolveNavBarContentHeight(compactMode: Boolean): Dp =
    if (compactMode) NavBarCompactContentHeight else NavBarContentHeight

internal fun resolveMainScreenBottomGradientHeight(compactMode: Boolean): Dp =
    resolveNavBarContentHeight(compactMode) + MainScreenBottomGradientExtraHeight

internal fun resolveNavBarSurfaceHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp {
    val contentHeight = resolveNavBarContentHeight(compactMode)
    return if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        contentHeight + systemNavBarInset
    } else {
        contentHeight
    }
}

internal fun resolveNavBarOccupiedHeight(
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp = resolveNavBarContentHeight(compactMode) + systemNavBarInset

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp,
    onSearchIconDoubleTap: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit = {},
    voiceCapture: VoiceSpeechCaptureState? = null
) {
    val navBarInsetPadding = sanitizeNavigationBarBottomInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
    // Maintain invariant: bottomBarPadding + innerRowPadding = the sanitized system nav bar inset.
    // This prevents nav items from appearing behind the gesture bar during style transitions,
    // e.g. FULL_WIDTH→DEFAULT where bottomBarPadding starts at 0 and animates to systemNavBarInset.
    val innerRowPadding = (navBarInsetPadding - bottomBarPadding).coerceAtLeast(0.dp)
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)

    val rowModifier = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = innerRowPadding, start = 12.dp, end = 12.dp)
    } else {
        modifier
            .padding(start = 10.dp, end = 10.dp, bottom = innerRowPadding)
            .fillMaxWidth()
    }
    val isOnSearchScreen = currentRoute == Screen.Search.route

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val scope = rememberCoroutineScope()
        var lastSearchTapTimestamp by remember { mutableStateOf(0L) }
        navItems.forEach { item ->
            val isSelected = currentRoute != null && currentRoute == item.screen.route
            val isSearchItem = item.screen.route == Screen.Search.route

            val targetWeight = when {
                isSearchItem -> if (isOnSearchScreen) 2.0f else 1.0f
                else -> if (isOnSearchScreen) 0.5f else 1.0f
            }
            val animatedWeight by animateFloatAsState(
                targetValue = targetWeight,
                animationSpec = tween(
                    durationMillis = 380,
                    easing = MotionTokens.EmphasizedEasing
                ),
                label = "nav_item_weight"
            )

            val isCompactOrSearch = compactMode || isOnSearchScreen

            val selectedColor = MaterialTheme.colorScheme.primary
            val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            val indicatorColorFromTheme = MaterialTheme.colorScheme.secondaryContainer

            val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                item.selectedIconResId
            } else {
                item.iconResId
            }
            val localizedLabel = stringResource(id = item.labelResId)
            val iconLambda: @Composable () -> Unit = remember(iconPainterResId, localizedLabel) {
                {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = localizedLabel
                    )
                }
            }
            val selectedIconLambda: @Composable () -> Unit = remember(iconPainterResId, localizedLabel) {
                {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = localizedLabel
                    )
                }
            }
            val labelLambda: (@Composable () -> Unit)? = if (isCompactOrSearch) {
                null
            } else {
                remember(localizedLabel) {
                    { Text(localizedLabel) }
                }
            }
            val onClickLambda: () -> Unit = remember(item.screen.route, navController, scope) {
                click@{
                    if (!latestNavigationEnabled) {
                        lastSearchTapTimestamp = 0L
                        return@click
                    }

                    val itemRoute = item.screen.route
                    val isSearchTab = itemRoute == Screen.Search.route
                    val isAlreadySelected = latestCurrentRoute == itemRoute

                    if (isSearchTab) {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                        lastSearchTapTimestamp = now

                        if (!isAlreadySelected) {
                            if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                lastSearchTapTimestamp = 0L
                                return@click
                            }
                        }

                        if (isDoubleTap) {
                            lastSearchTapTimestamp = 0L
                            if (isAlreadySelected) {
                                latestOnSearchIconDoubleTap()
                            } else {
                                scope.launch {
                                    delay(160L)
                                    latestOnSearchIconDoubleTap()
                                }
                            }
                        }
                    } else if (!isAlreadySelected) {
                        lastSearchTapTimestamp = 0L
                        navController.navigateToTopLevelSafely(itemRoute)
                    } else {
                        lastSearchTapTimestamp = 0L
                    }
                }
            }

            if (isSearchItem) {
                AnimatedContent(
                    targetState = isOnSearchScreen,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(380, easing = MotionTokens.EmphasizedEasing)) togetherWith
                            fadeOut(animationSpec = tween(380, easing = MotionTokens.EmphasizedEasing))
                        ).using(SizeTransform(clip = false))
                    },
                    label = "search_morph",
                    modifier = Modifier.weight(animatedWeight)
                ) { expanded ->
                    if (expanded) {
                        InlineNavSearchBar(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onVoiceSearchClick = onVoiceSearchClick,
                            voiceCapture = voiceCapture,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (compactMode) 48.dp else 56.dp)
                        )
                    } else {
                        CustomNavigationBarItem(
                            modifier = Modifier.fillMaxWidth(),
                            selected = isSelected,
                            onClick = onClickLambda,
                            enabled = currentRoute != null,
                            compactMode = compactMode,
                            icon = iconLambda,
                            selectedIcon = selectedIconLambda,
                            label = labelLambda,
                            contentDescription = localizedLabel,
                            alwaysShowLabel = true,
                            selectedIconColor = selectedColor,
                            unselectedIconColor = unselectedColor,
                            selectedTextColor = selectedColor,
                            unselectedTextColor = unselectedColor,
                            indicatorColor = indicatorColorFromTheme
                        )
                    }
                }
            } else {
                CustomNavigationBarItem(
                    modifier = Modifier.weight(animatedWeight),
                    selected = isSelected,
                    onClick = onClickLambda,
                    enabled = currentRoute != null,
                    compactMode = isCompactOrSearch,
                    icon = iconLambda,
                    selectedIcon = selectedIconLambda,
                    label = labelLambda,
                    contentDescription = localizedLabel,
                    alwaysShowLabel = true,
                    selectedIconColor = selectedColor,
                    unselectedIconColor = unselectedColor,
                    selectedTextColor = selectedColor,
                    unselectedTextColor = unselectedColor,
                    indicatorColor = indicatorColorFromTheme
                )
            }
        }
    }
}

@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp = 0.dp,
    onSearchIconDoubleTap: () -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onVoiceSearchClick: () -> Unit = {},
    /** Non-null while the voice sheet is open: the inline search bar shows the live transcript. */
    voiceCapture: VoiceSpeechCaptureState? = null
) {
    PlayerInternalNavigationItemsRow(
        navController = navController,
        navItems = navItems,
        currentRoute = currentRoute,
        navBarStyle = navBarStyle,
        compactMode = compactMode,
        bottomBarPadding = bottomBarPadding,
        onSearchIconDoubleTap = onSearchIconDoubleTap,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onVoiceSearchClick = onVoiceSearchClick,
        voiceCapture = voiceCapture,
        modifier = modifier
    )
}

@Composable
private fun InlineNavSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onVoiceSearchClick: () -> Unit = {},
    voiceCapture: VoiceSpeechCaptureState? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val voiceActive = voiceCapture != null
    val voicePhase = voiceCapture?.phase
    val voiceLive = voicePhase == VoiceCapturePhase.Listening || voicePhase == VoiceCapturePhase.Hearing

    // Focus the field once, when the user opens Search. Saved across activity recreation, so
    // coming back to PixelPlayer on the Search tab doesn't pop the keyboard up by itself.
    var autoFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Opened by voice search: the words are typed in by the microphone, so no keyboard.
        if (voiceActive || autoFocusDone) return@LaunchedEffect
        autoFocusDone = true
        delay(380L) // wait for 380ms navigation transition to settle before showing keyboard
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Leaving the app drops the field's focus and the keyboard. Otherwise, on return, the
    // focused field brought the keyboard back (or left its insets behind) and the screen acted
    // as if the search bar were being typed in.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(voiceActive) {
        if (voiceActive) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    val placeholder = when (voicePhase) {
        null -> stringResource(R.string.search_placeholder)
        VoiceCapturePhase.Idle -> "Tap the mic to speak"
        VoiceCapturePhase.NeedsPermission -> "Allow microphone access"
        VoiceCapturePhase.Listening, VoiceCapturePhase.Hearing -> "Listening\u2026"
        VoiceCapturePhase.Processing -> "Finding it\u2026"
        VoiceCapturePhase.Done -> stringResource(R.string.search_placeholder)
        VoiceCapturePhase.Error,
        VoiceCapturePhase.Unavailable -> voiceCapture?.errorMessage?.substringBefore(".") ?: "Didn't catch that"
    }
    val isVoiceProblem = voicePhase == VoiceCapturePhase.Error || voicePhase == VoiceCapturePhase.Unavailable

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search_cd_search_icon),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isVoiceProblem) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val micScale by animateFloatAsState(
                        targetValue = if (voiceLive) 1f + 0.3f * (voiceCapture?.level ?: 0f) else 1f,
                        animationSpec = tween(durationMillis = 90),
                        label = "voice_mic_level"
                    )
                    IconButton(
                        onClick = onVoiceSearchClick,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = micScale
                                scaleY = micScale
                            }
                            .clip(CircleShape)
                            .background(
                                if (voiceLive) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = if (voiceActive) "Speak again" else "Voice search",
                            tint = if (voiceLive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.search_cd_clear_search_query),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
