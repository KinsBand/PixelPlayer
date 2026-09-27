@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import android.content.Context
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SourceBadge
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.AccountsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ExternalAccountUiModel
import com.theveloper.pixelplay.presentation.viewmodel.ExternalServiceAccount
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

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction =
            1f - (
                (topBarHeight.value - minTopBarHeightPx) /
                    (maxTopBarHeightPx - minTopBarHeightPx)
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

            if (uiState.connectedAccounts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.accounts_linked_services),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                items(
                    items = uiState.connectedAccounts,
                    key = { it.service.name }
                ) { account ->
                    ConnectedAccountCard(
                        account = account,
                        syncingPlaylists = syncingPlaylists,
                        onSyncLibrary = { viewModel.sync() },
                        onSyncPlaylist = { playlistId -> viewModel.syncPlaylist(playlistId) },
                        onPlaylistClick = { playlist -> openedPlaylist = playlist },
                        onLogout = { viewModel.logout(account.service) }
                    )
                }
            }

            if (hiddenPlaylists.isNotEmpty()) {
                item(key = "hidden_playlists_header") {
                    Column(modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                        Text(
                            text = "Hidden playlists",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Playlists you removed. Sync won't add them back unless you restore them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(items = hiddenPlaylists, key = { "hidden:${it.id}" }) { hidden ->
                    HiddenPlaylistRow(
                        playlist = hidden,
                        onRestore = { coroutineScope.launch { viewModel.library.restorePlaylist(hidden.id) } }
                    )
                }
            }

            if (uiState.disconnectedServices.isNotEmpty()) {
                item {
                    EmptyAccountsCard(
                        disconnectedServices = uiState.disconnectedServices,
                        onConnect = { service ->
                            connecting = service
                        }
                    )
                }
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

@Composable
private fun ConnectedAccountCard(
    account: ExternalAccountUiModel,
    syncingPlaylists: Set<String>,
    onSyncLibrary: () -> Unit,
    onSyncPlaylist: (String) -> Unit,
    onPlaylistClick: (ConnectedPlaylist) -> Unit,
    onLogout: () -> Unit
) {
    val statusConnected = stringResource(R.string.accounts_status_connected)
    val logOut = stringResource(R.string.cloud_cd_logout)
    val palette = servicePalette(account.service)
    val cardShape = AbsoluteSmoothCornerShape(28.dp, 60)

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Service Icon, Name, Status Badge, Logout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
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

                Spacer(Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = account.accountLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = AbsoluteSmoothCornerShape(12.dp, 60),
                    color = palette.statusContainer
                ) {
                    Text(
                        text = statusConnected,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.statusTint,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = onLogout,
                    enabled = !account.isLoggingOut,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (account.isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
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

            // Playlist row header with count and "Sync all"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${account.playlists.size} saved playlists",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onSyncLibrary,
                    enabled = !account.isLoggingOut
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Sync all", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Horizontal row of playlists
            if (account.playlists.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
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
                Surface(
                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No playlists synced yet. Tap 'Sync all' to retrieve your playlists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
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
                            playerViewModel?.playSongs(playable, playable.first(), playlist.title)
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
                                playerViewModel?.playSongs(playable, song, playlist.title)
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

@Composable
private fun EmptyAccountsCard(
    disconnectedServices: List<ExternalServiceAccount>,
    onConnect: (ExternalServiceAccount) -> Unit
) {
    val noLinkedTitle = "Connect a music account"
    val noLinkedBody = "Bring your playlists and liked songs into one library."
    val connectTemplate = stringResource(R.string.accounts_connect_service)
    Card(
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = noLinkedTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = noLinkedBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            disconnectedServices.forEach { service ->
                FilledTonalButton(
                    onClick = { onConnect(service) },
                    shape = AbsoluteSmoothCornerShape(18.dp, 60),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(
                        painter = painterResource(serviceIconRes(service)),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    TightWrapText(
                        text = connectTemplate.format(serviceDisplayName(service)),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                    )
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
                    text = com.theveloper.pixelplay.data.accounts.MusicSources.displayName(playlist.source) +
                        if (playlist.remote) " · deleted from account" else " · hidden on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            TextButton(onClick = onRestore) { Text("Restore") }
        }
    }
}
