@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import com.theveloper.pixelplay.presentation.components.FriendsLiveGreen
import com.theveloper.pixelplay.presentation.viewmodel.ConnectStage
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.accounts.ConnectedPlaylist
import com.theveloper.pixelplay.data.accounts.MusicSources
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SourceBadge
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.AccountsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.AccountFriendUi
import com.theveloper.pixelplay.presentation.viewmodel.AccountRefreshKeys
import com.theveloper.pixelplay.presentation.viewmodel.ExternalAccountUiModel
import com.theveloper.pixelplay.presentation.viewmodel.ExternalServiceAccount
import com.theveloper.pixelplay.presentation.viewmodel.HiddenFriendUi
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun AccountsScreen(
    onBackClick: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel? = null,
    navController: NavController? = null
) {
    var connecting by remember { mutableStateOf<ExternalServiceAccount?>(null) }
    var openedPlaylist by remember { mutableStateOf<ConnectedPlaylist?>(null) }
    connecting?.let { AccountConnectionDialog(it, viewModel) { connecting = null } }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncingPlaylists by viewModel.syncingPlaylists.collectAsStateWithLifecycle()
    val favoriteIds by (playerViewModel?.favoriteSongIds ?: viewModel.library.favoriteIds).collectAsStateWithLifecycle(emptySet())
    val hiddenPlaylists by viewModel.library.deletedPlaylists.collectAsStateWithLifecycle(emptyList())
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshFailures by viewModel.refreshFailures.collectAsStateWithLifecycle()
    val connectStages by viewModel.connecting.collectAsStateWithLifecycle()
    // The service tile tapped last: its logout button shows above it and its playlists below.
    var selectedService by rememberSaveable { mutableStateOf<ExternalServiceAccount?>(null) }
    var openPanel by rememberSaveable { mutableStateOf<AccountsPanel?>(null) }
    var expandedFriendId by rememberSaveable { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    // Condensed from the start: back button and title on one row with the bar colour.
    val maxTopBarHeight = minTopBarHeight
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction =
            1f - (
                (topBarHeight.value - minTopBarHeightPx) /
                    (maxTopBarHeightPx - minTopBarHeightPx).coerceAtLeast(1f)
                ).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                    (
                        lazyListState.firstVisibleItemIndex > 0 ||
                            lazyListState.firstVisibleItemScrollOffset > 0
                        )
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
            val canExpand =
                lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx
            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(modifier = Modifier.nestedScroll(nestedScrollConnection).fillMaxSize()) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { AccountSyncStatus(viewModel) }

            // 1. One card for the three services: green = connected, grey = not. Tap a connected
            //    one to pick it (its logout button slides in above it, its playlists show below);
            //    tap a grey one to connect it.
            item(key = "services_header") {
                ServicesHeaderCard(
                    accounts = uiState.connectedAccounts,
                    connectStages = connectStages,
                    selected = selectedService,
                    onTileClick = { service ->
                        val isConnected = uiState.connectedAccounts.any { it.service == service }
                        if (isConnected) {
                            selectedService = if (selectedService == service) null else service
                        } else if (service !in connectStages) {
                            connecting = service
                        }
                    },
                    onLogout = { service ->
                        viewModel.logout(service)
                        if (selectedService == service) selectedService = null
                    }
                )
            }

            // 2. The picked service (or the first connected one): refresh buttons + its playlists.
            val shownAccount = uiState.connectedAccounts.firstOrNull { it.service == selectedService }
                ?: uiState.connectedAccounts.firstOrNull()
            if (shownAccount != null) {
                item(key = "service_details") {
                    ConnectedAccountCard(
                        account = shownAccount,
                        syncingPlaylists = syncingPlaylists,
                        refreshing = refreshing,
                        onRefreshMine = { viewModel.refreshMine(shownAccount.service) },
                        onRefreshFriends = { viewModel.refreshFriends(shownAccount.service) },
                        onRefreshAll = { viewModel.refreshService(shownAccount.service) },
                        onSyncPlaylist = { playlistId -> viewModel.syncPlaylist(playlistId) },
                        // Straight to the playlist page; the preview dialog only stays as a
                        // fallback for callers that have no navController.
                        onPlaylistClick = { playlist ->
                            if (navController != null) {
                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                            } else {
                                openedPlaylist = playlist
                            }
                        },
                        // Logout lives on the service tile above now.
                        onLogout = null,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            // 3. Friends | Hidden playlists, side by side at the bottom; the open one goes full width.
            item(key = "bottom_panels") {
                AccountsBottomPanels(
                    open = openPanel,
                    onToggle = { panel -> openPanel = if (openPanel == panel) null else panel },
                    friendsCount = uiState.friends.size,
                    hiddenCount = hiddenPlaylists.size + uiState.hiddenFriends.size,
                    friendsContent = {
                        FriendsSectionHeader(
                            isRefreshing = AccountRefreshKeys.ALL_FRIENDS in refreshing,
                            failures = refreshFailures,
                            onRefreshAll = { viewModel.refreshAllFriends() }
                        )
                        if (uiState.friends.isEmpty()) {
                            AccountsPanelEmpty(stringResource(R.string.accounts_friends_empty))
                        }
                        uiState.friends.forEach { friend ->
                            AccountFriendRow(
                                friend = friend,
                                isRefreshing = AccountRefreshKeys.friend(friend.id) in refreshing ||
                                    friend.playlistIds.any { it in syncingPlaylists },
                                expanded = expandedFriendId == friend.id,
                                onToggleExpanded = {
                                    expandedFriendId = if (expandedFriendId == friend.id) null else friend.id
                                },
                                onPlaylistClick = { playlist ->
                                    if (navController != null) {
                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                                    } else {
                                        openedPlaylist = playlist
                                    }
                                },
                                onRefresh = { viewModel.refreshFriend(friend) },
                                onHide = { viewModel.hideFriend(friend.id) }
                            )
                        }
                    },
                    hiddenContent = {
                        if (hiddenPlaylists.isEmpty() && uiState.hiddenFriends.isEmpty()) {
                            AccountsPanelEmpty(stringResource(R.string.accounts_hidden_empty))
                        }
                        hiddenPlaylists.forEach { hidden ->
                            HiddenPlaylistRow(
                                playlist = hidden,
                                onRestore = { coroutineScope.launch { viewModel.library.restorePlaylist(hidden.id) } }
                            )
                        }
                        if (uiState.hiddenFriends.isNotEmpty()) {
                            AccountsSectionTitle(stringResource(R.string.accounts_hidden_friends_title))
                            uiState.hiddenFriends.forEach { hidden ->
                                HiddenFriendRow(
                                    friend = hidden,
                                    isRestoring = AccountRefreshKeys.friend(hidden.id) in refreshing,
                                    onRestore = { viewModel.restoreFriend(hidden.id) }
                                )
                            }
                        }
                    }
                )
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.settings_category_accounts_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBackClick,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp
        )
    }

    openedPlaylist?.let { playlist ->
        OpenedPlaylistDialog(
            playlist = playlist,
            playerViewModel = playerViewModel,
            navController = navController,
            favoriteIds = favoriteIds,
            isSyncing = playlist.id in syncingPlaylists,
            onSync = { viewModel.syncPlaylist(playlist.id) },
            onDismiss = { openedPlaylist = null }
        )
    }
}

/**
 * One connected service: a single header row (logo, name with "Connected", your and friends'
 * playlist counts, log out), then the three refresh buttons and your own playlists.
 */
@Composable
private fun ConnectedAccountCard(
    account: ExternalAccountUiModel,
    syncingPlaylists: Set<String>,
    refreshing: Set<String>,
    onRefreshMine: () -> Unit,
    onRefreshFriends: () -> Unit,
    onRefreshAll: () -> Unit,
    onSyncPlaylist: (String) -> Unit,
    onPlaylistClick: (ConnectedPlaylist) -> Unit,
    /** Null = no logout button on the card (the Accounts header tile has it). */
    onLogout: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val statusConnected = stringResource(R.string.accounts_status_connected)
    val logOut = stringResource(R.string.cloud_cd_logout)
    val palette = servicePalette(account.service)
    val cardShape = AbsoluteSmoothCornerShape(28.dp, 60)
    val busy = account.isLoggingOut

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = AbsoluteSmoothCornerShape(14.dp, 60),
                    color = palette.iconContainer
                ) {
                    ServiceIcon(
                        service = account.service,
                        tint = palette.iconTint,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = AbsoluteSmoothCornerShape(10.dp, 60),
                            color = palette.statusContainer
                        ) {
                            Text(
                                text = statusConnected,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.statusTint,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.accounts_playlist_counts,
                            account.playlists.size,
                            account.friendPlaylistCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onLogout != null) {
                    IconButton(onClick = onLogout, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = logOut,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            val serviceName = account.title
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccountRefreshButton(
                    label = stringResource(R.string.accounts_refresh_mine),
                    description = stringResource(R.string.accounts_refresh_mine_cd, serviceName),
                    isRunning = AccountRefreshKeys.mine(account.service) in refreshing,
                    enabled = !busy,
                    onClick = onRefreshMine,
                    modifier = Modifier.weight(1f)
                )
                AccountRefreshButton(
                    label = stringResource(R.string.accounts_refresh_friends),
                    description = stringResource(R.string.accounts_refresh_friends_cd, serviceName),
                    isRunning = AccountRefreshKeys.friends(account.service) in refreshing,
                    enabled = !busy && account.friendPlaylistCount > 0,
                    onClick = onRefreshFriends,
                    modifier = Modifier.weight(1f)
                )
                AccountRefreshButton(
                    label = stringResource(R.string.accounts_refresh_all),
                    description = stringResource(R.string.accounts_refresh_all_cd, serviceName),
                    isRunning = AccountRefreshKeys.all(account.service) in refreshing,
                    enabled = !busy,
                    onClick = onRefreshAll,
                    modifier = Modifier.weight(1f)
                )
            }

            if (account.playlists.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(
                        items = account.playlists,
                        key = { it.id }
                    ) { playlist ->
                        AccountPlaylistCard(
                            playlist = playlist,
                            isSyncing = playlist.id in syncingPlaylists,
                            onSync = { onSyncPlaylist(playlist.id) },
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.accounts_no_own_playlists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A tonal "Mine" / "Friends" / "All" button; swaps its icon for a spinner while it runs. */
@Composable
private fun AccountRefreshButton(
    label: String,
    description: String,
    isRunning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !isRunning,
        shape = AbsoluteSmoothCornerShape(16.dp, 60),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description }
    ) {
        if (isRunning) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AccountPlaylistCard(
    playlist: ConnectedPlaylist,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.width(140.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(AbsoluteSmoothCornerShape(14.dp, 60))
            ) {
                SmartImage(
                    model = playlist.coverUrl ?: playlist.songs.firstOrNull()?.albumArtUriString,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = AbsoluteSmoothCornerShape(14.dp, 60)
                )

                // Sync button overlay at top-right
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(enabled = !isSyncing, onClick = onSync)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = "Sync ${playlist.title}",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${playlist.songs.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OpenedPlaylistDialog(
    playlist: ConnectedPlaylist,
    playerViewModel: PlayerViewModel?,
    navController: NavController?,
    favoriteIds: Set<String>,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val playable = remember(playlist.songs) { playlist.songs.filter { it.contentUriString.isNotBlank() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_back))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = playlist.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playlist.source.isNotBlank()) {
                        SourceBadge(playlist.source)
                    }
                }

                playlist.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        enabled = playable.isNotEmpty() && playerViewModel != null,
                        onClick = {
                            playerViewModel?.playSongs(playable, playable.first(), playlist.title, playlist.id)
                        },
                        shape = AbsoluteSmoothCornerShape(16.dp, 60),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "Play all (${playlist.songs.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (navController != null) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                            },
                            shape = AbsoluteSmoothCornerShape(16.dp, 60)
                        ) {
                            Text("Full View")
                        }
                    }

                    IconButton(
                        onClick = onSync,
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Sync, contentDescription = "Sync playlist")
                        }
                    }
                }

                if (playlist.remoteId.isNotBlank()) {
                    TextButton(
                        onClick = {
                            com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId)
                                ?.let { uriHandler.openUri(it) }
                        }
                    ) {
                        Text(
                            text = "Open in " + com.theveloper.pixelplay.data.accounts.MusicSources.displayName(playlist.source),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(playlist.songs.size) { index ->
                        val song = playlist.songs[index]
                        val isAvailable = song.contentUriString.isNotBlank()
                        val isFav = song.id in favoriteIds
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = if (isAvailable) song.artist else "Unavailable from this service",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                )
                            },
                            modifier = Modifier.clickable(enabled = isAvailable && playerViewModel != null) {
                                playerViewModel?.playSongs(playable, song, playlist.title, playlist.id)
                            },
                            trailingContent = {
                                IconButton(
                                    enabled = isAvailable && playerViewModel != null,
                                    onClick = {
                                        playerViewModel?.toggleFavoriteSpecificSong(song.copy(isFavorite = isFav))
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (isFav) "Unlike" else "Like",
                                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class ServicePalette(
    val iconContainer: Color,
    val iconTint: Color,
    val statusContainer: Color,
    val statusTint: Color,
    val primaryActionContainer: Color,
    val primaryActionTint: Color
)

@Composable
private fun servicePalette(service: ExternalServiceAccount): ServicePalette {
    return when (service) {
        ExternalServiceAccount.SPOTIFY -> ServicePalette(
            iconContainer = Color(0xFF1DB954),
            iconTint = Color.White,
            statusContainer = Color(0xFFE8F5E9),
            statusTint = Color(0xFF1B5E20),
            primaryActionContainer = Color(0xFF1DB954),
            primaryActionTint = Color.White
        )
        ExternalServiceAccount.YOUTUBE_MUSIC -> ServicePalette(
            iconContainer = Color(0xFFFF0000),
            iconTint = Color.White,
            statusContainer = Color(0xFFFFEBEE),
            statusTint = Color(0xFFB71C1C),
            primaryActionContainer = Color(0xFFFF0000),
            primaryActionTint = Color.White
        )
        ExternalServiceAccount.APPLE_MUSIC -> ServicePalette(
            iconContainer = Color(0xFFFA243C),
            iconTint = Color.White,
            statusContainer = Color(0xFFFFEBEE),
            statusTint = Color(0xFFB0102A),
            primaryActionContainer = Color(0xFFFA243C),
            primaryActionTint = Color.White
        )
    }
}

private fun serviceIconRes(service: ExternalServiceAccount): Int = when (service) {
    ExternalServiceAccount.SPOTIFY -> R.drawable.ic_source_spotify
    ExternalServiceAccount.YOUTUBE_MUSIC -> R.drawable.ic_source_youtube_music
    ExternalServiceAccount.APPLE_MUSIC -> R.drawable.ic_source_apple_music
}

private fun accountIcon(service: ExternalServiceAccount): ImageVector {
    return Icons.Rounded.MusicNote
}

@Composable
private fun ServiceIcon(service: ExternalServiceAccount, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(serviceIconRes(service)),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

@Composable
private fun serviceDisplayName(service: ExternalServiceAccount): String {
    return when (service) {
        ExternalServiceAccount.SPOTIFY -> "Spotify"
        ExternalServiceAccount.YOUTUBE_MUSIC -> "YouTube Music"
        ExternalServiceAccount.APPLE_MUSIC -> "Apple Music"
    }
}

@Composable
private fun AccountSyncStatus(viewModel: AccountsViewModel) {
    val status by viewModel.spotifyStatus.collectAsStateWithLifecycle()
    val message by viewModel.library.message.collectAsStateWithLifecycle()
    val syncing by viewModel.library.syncing.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (syncing) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun HiddenPlaylistRow(
    playlist: com.theveloper.pixelplay.data.accounts.DeletedPlaylist,
    onRestore: () -> Unit
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.theveloper.pixelplay.presentation.components.SmartImage(
                model = playlist.coverUrl,
                contentDescription = null,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(
                        if (playlist.remote) R.string.accounts_hidden_playlist_remote else R.string.accounts_hidden_playlist_local,
                        MusicSources.displayName(playlist.source)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            TextButton(onClick = onRestore) { Text(stringResource(R.string.accounts_restore)) }
        }
    }
}

@Composable
private fun AccountsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp)
            .semantics { heading() }
    )
}

/** "Friends" title with a Refresh all button, and a line when the last refresh missed some playlists. */
@Composable
private fun FriendsSectionHeader(
    isRefreshing: Boolean,
    failures: Int,
    onRefreshAll: () -> Unit
) {
    val refreshAllDescription = stringResource(R.string.accounts_refresh_all_friends_cd)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) { AccountsSectionTitle(stringResource(R.string.accounts_friends_title)) }
            TextButton(
                onClick = onRefreshAll,
                enabled = !isRefreshing,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = refreshAllDescription }
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.accounts_refresh_all_friends), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (failures > 0) {
            Text(
                text = pluralStringResource(R.plurals.accounts_refresh_failed, failures, failures),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * A friend with saved playlists: avatar, name, platform · count · last updated, refresh and hide.
 * Tapping the row drops down their playlists inline (tap one to open it).
 */
@Composable
private fun AccountFriendRow(
    friend: AccountFriendUi,
    isRefreshing: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPlaylistClick: (ConnectedPlaylist) -> Unit,
    onRefresh: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedState = stringResource(if (expanded) R.string.accounts_panel_expanded else R.string.accounts_panel_collapsed)
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized),
        label = "friendRowChevron"
    )
    val name = friend.name.ifBlank { stringResource(R.string.accounts_friend_unnamed) }
    val platform = MusicSources.displayName(friend.source)
    val count = pluralStringResource(R.plurals.accounts_friend_playlist_count, friend.playlistIds.size, friend.playlistIds.size)
    val updated = if (friend.oldestFetchedAt > 0) {
        stringResource(
            R.string.accounts_friend_updated,
            DateUtils.getRelativeTimeSpanString(
                friend.oldestFetchedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            ).toString()
        )
    } else {
        stringResource(R.string.accounts_friend_never_updated)
    }
    Surface(
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized))
    ) {
      Column {
        Row(
            modifier = Modifier
                .clickable(
                    onClickLabel = stringResource(R.string.accounts_friend_show_playlists_cd, name),
                    role = Role.Button,
                    onClick = onToggleExpanded
                )
                .semantics { stateDescription = expandedState }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FriendAvatar(avatarUrl = friend.avatarUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$platform · $count · $updated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = stringResource(R.string.accounts_refresh_friend_cd, name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onHide, enabled = !isRefreshing) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = stringResource(R.string.accounts_hide_friend_cd, name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp).rotate(chevronTurn)
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                friend.playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onPlaylistClick(playlist) }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmartImage(
                            model = playlist.coverUrl,
                            contentDescription = null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun FriendAvatar(avatarUrl: String?) {
    if (avatarUrl.isNullOrBlank()) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.rounded_person_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    } else {
        SmartImage(
            model = avatarUrl,
            contentDescription = null,
            shape = CircleShape,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
private fun HiddenFriendRow(
    friend: HiddenFriendUi,
    isRestoring: Boolean,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = friend.name.ifBlank { stringResource(R.string.accounts_friend_unnamed) }
    val count = pluralStringResource(R.plurals.accounts_friend_playlist_count, friend.playlistCount, friend.playlistCount)
    val restoreDescription = stringResource(R.string.accounts_restore_friend_cd, name)
    Surface(
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FriendAvatar(avatarUrl = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = friend.source?.let { "${MusicSources.displayName(it)} · $count" } ?: count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            TextButton(
                onClick = onRestore,
                enabled = !isRestoring,
                modifier = Modifier.semantics { contentDescription = restoreDescription }
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.accounts_restore))
                }
            }
        }
    }
}

/** The two bottom panels on Accounts. */
private enum class AccountsPanel { FRIENDS, HIDDEN }

/**
 * Spotify · YouTube Music · Apple Music in one card, one equal tile each. A connected tile is
 * tinted green with a green dot; a disconnected one is neutral grey. The picked connected
 * tile gets a logout button directly above it, exactly as wide as the tile.
 */
@Composable
private fun ServicesHeaderCard(
    accounts: List<ExternalAccountUiModel>,
    connectStages: Map<ExternalServiceAccount, ConnectStage>,
    selected: ExternalServiceAccount?,
    onTileClick: (ExternalServiceAccount) -> Unit,
    onLogout: (ExternalServiceAccount) -> Unit,
) {
    Card(
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .animateContentSize(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ExternalServiceAccount.entries.forEach { service ->
                val account = accounts.firstOrNull { it.service == service }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedVisibility(
                        visible = account != null && selected == service,
                        enter = expandVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) +
                            fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                        exit = shrinkVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) +
                            fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate))
                    ) {
                        val busy = account?.isLoggingOut == true
                        FilledTonalButton(
                            onClick = { onLogout(service) },
                            enabled = !busy,
                            shape = AbsoluteSmoothCornerShape(16.dp, 60),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = stringResource(R.string.accounts_logout_service_cd, serviceDisplayName(service)),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    ServiceTile(
                        service = service,
                        connected = account != null,
                        selected = selected == service,
                        stage = connectStages[service],
                        onClick = { onTileClick(service) }
                    )
                }
            }
        }
    }
}

/** One service in the header: logo, name, and its status (or the sign-in progress). */
@Composable
private fun ServiceTile(
    service: ExternalServiceAccount,
    connected: Boolean,
    selected: Boolean,
    stage: ConnectStage?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val palette = servicePalette(service)
    val container by animateColorAsState(
        targetValue = when {
            connected && selected -> FriendsLiveGreen.copy(alpha = 0.26f)
            connected -> FriendsLiveGreen.copy(alpha = 0.14f)
            else -> colors.surfaceContainerHighest
        },
        animationSpec = tween(MotionTokens.DurationMedium2),
        label = "serviceTileContainer"
    )
    val name = serviceDisplayName(service)
    val status = when {
        stage != null -> stringResource(stage.labelRes())
        connected -> stringResource(R.string.accounts_status_connected)
        else -> stringResource(R.string.accounts_not_connected)
    }
    Surface(
        onClick = onClick,
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$name, $status"
                if (connected) stateDescription = status
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = if (connected) palette.iconContainer else colors.onSurface.copy(alpha = 0.10f)
                ) {
                    ServiceIcon(
                        service = service,
                        tint = if (connected) palette.iconTint else colors.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                if (connected) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHigh)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(FriendsLiveGreen)
                    )
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (stage != null) {
                val progress by animateFloatAsState(
                    targetValue = stage.progress,
                    animationSpec = tween(MotionTokens.DurationLong2, easing = MotionTokens.Emphasized),
                    label = "serviceConnectProgress"
                )
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
            }
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = if (connected && stage == null) FriendsLiveGreen else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun ConnectStage.labelRes(): Int = when (this) {
    ConnectStage.CHECKING -> R.string.accounts_stage_checking
    ConnectStage.PROFILE -> R.string.accounts_stage_profile
    ConnectStage.SYNCING -> R.string.accounts_stage_syncing
    ConnectStage.DONE -> R.string.accounts_stage_done
}

/**
 * Friends | Hidden playlists side by side at the bottom of Accounts. Opening one makes it full
 * width with its list inside; the other drops below it as a full-width tile.
 */
@Composable
private fun AccountsBottomPanels(
    open: AccountsPanel?,
    onToggle: (AccountsPanel) -> Unit,
    friendsCount: Int,
    hiddenCount: Int,
    friendsContent: @Composable () -> Unit,
    hiddenContent: @Composable () -> Unit,
) {
    @Composable
    fun tile(panel: AccountsPanel, modifier: Modifier) {
        AccountsPanelCard(
            panel = panel,
            count = if (panel == AccountsPanel.FRIENDS) friendsCount else hiddenCount,
            expanded = open == panel,
            onToggle = { onToggle(panel) },
            modifier = modifier,
            content = if (panel == AccountsPanel.FRIENDS) friendsContent else hiddenContent
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (open) {
            null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tile(AccountsPanel.FRIENDS, Modifier.weight(1f))
                tile(AccountsPanel.HIDDEN, Modifier.weight(1f))
            }
            AccountsPanel.FRIENDS -> {
                tile(AccountsPanel.FRIENDS, Modifier.fillMaxWidth())
                tile(AccountsPanel.HIDDEN, Modifier.fillMaxWidth())
            }
            AccountsPanel.HIDDEN -> {
                tile(AccountsPanel.HIDDEN, Modifier.fillMaxWidth())
                tile(AccountsPanel.FRIENDS, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AccountsPanelCard(
    panel: AccountsPanel,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val title = stringResource(
        if (panel == AccountsPanel.FRIENDS) R.string.accounts_friends_title else R.string.accounts_hidden_playlists_title
    )
    val expandedState = stringResource(if (expanded) R.string.accounts_panel_expanded else R.string.accounts_panel_collapsed)
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized),
        label = "accountsPanelChevron"
    )
    Card(
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { stateDescription = expandedState }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(
                        imageVector = if (panel == AccountsPanel.FRIENDS) Icons.Rounded.Group else Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronTurn)
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { content() }
            }
        }
    }
}

@Composable
private fun AccountsPanelEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
    )
}
