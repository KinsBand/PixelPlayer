package com.theveloper.pixelplay.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

// AboutTopBar removed, replaced by CollapsibleCommonTopBar

@androidx.annotation.OptIn(UnstableApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
) {
    val context = LocalContext.current
    val versionName: String = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "N/A"
    } catch (_: Exception) {
        "N/A"
    }

    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        transitionState.targetState = true
    }
    val transition = rememberTransition(transitionState, label = "AboutAppearTransition")

    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) },
    ) { if (it) 1f else 0f }

    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
    ) { if (it) 0.dp else 40.dp }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    // Condensed from the start: back button and title on one row with the bar colour.
    val maxTopBarHeight = minTopBarHeight

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - (
            (topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx).coerceAtLeast(1f)
            ).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (
                    !isScrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            },
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                bottom = MiniPlayerHeight +
                    WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 12.dp,
            ),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "hero_card") {
                AboutHeroCard(
                    versionName = versionName,
                    onVersionLongPress = {
                        navController.navigateSafely(Screen.EasterEgg.route)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                )
            }

            // Device information and developer options used to be rows on the settings
            // root. They are rarely needed, so they live here now.
            item(key = "system_links") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    SettingsSectionHeader(title = stringResource(R.string.settings_system_section))
                    SettingsGroupCard {
                        MainSettingsCategoryRow(
                            title = stringResource(SettingsCategory.DEVICE_CAPABILITIES.titleRes),
                            subtitle = stringResource(SettingsCategory.DEVICE_CAPABILITIES.subtitleRes),
                            icon = SettingsCategory.DEVICE_CAPABILITIES.icon,
                            iconRes = SettingsCategory.DEVICE_CAPABILITIES.iconRes,
                            colors = getCategoryColors(SettingsCategory.DEVICE_CAPABILITIES),
                            onClick = { navController.navigateSafely(Screen.DeviceCapabilities.route) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        MainSettingsCategoryRow(
                            title = stringResource(SettingsCategory.DEVELOPER.titleRes),
                            subtitle = stringResource(SettingsCategory.DEVELOPER.subtitleRes),
                            icon = SettingsCategory.DEVELOPER.icon,
                            iconRes = SettingsCategory.DEVELOPER.iconRes,
                            colors = getCategoryColors(SettingsCategory.DEVELOPER),
                            onClick = {
                                navController.navigateSafely(
                                    Screen.SettingsCategory.createRoute(SettingsCategory.DEVELOPER.id)
                                )
                            }
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.about_screen_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigationIconClick,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp
        )
    }
}

@Composable
private fun AboutHeroCard(
    versionName: String,
    onVersionLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Surface(
        modifier = modifier,
        shape = heroShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.pixelplay_base_monochrome),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp).size(28.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.about_app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onVersionLongPress()
                                },
                            )
                        },
                ) {
                    Text(
                        text = stringResource(R.string.about_version_format, versionName),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                CommunitySignalsRow()

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SocialChip(
                        label = stringResource(R.string.about_github_label),
                        subtitle = stringResource(R.string.about_github_subtitle),
                        iconRes = R.drawable.github,
                        contentDescription = stringResource(R.string.about_cd_open_github_repo),
                        onClick = { openUrl(context, "https://github.com/KinsBand/PixelPlayer") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialChip(
    label: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        shape = AbsoluteSmoothCornerShape(14.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunitySignalsRow() {
    val labels = listOf(
        stringResource(R.string.about_signal_open_source) to Icons.Rounded.Public,
        stringResource(R.string.about_signal_community_first) to Icons.Rounded.AutoAwesome,
        stringResource(R.string.about_signal_material3) to Icons.Rounded.Palette,
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { (label, icon) ->
            Surface(
                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val uri = try {
        url.toUri()
    } catch (_: Throwable) {
        return
    }

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Ignore if no handler is available.
    }
}
