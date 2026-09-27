package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.library.isFavoritesPlaylistName
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/** Which streaming service a platform playlists screen shows. */
enum class PlaylistPlatform(val source: String, val label: String, val iconRes: Int, val brand: Color) {
    SPOTIFY("SPOTIFY", "Spotify", R.drawable.ic_source_spotify, Color(0xFF1DB954)),
    YOUTUBE_MUSIC("YOUTUBE_MUSIC", "YT Music", R.drawable.ic_source_youtube_music, Color(0xFFFF0000));

    companion object {
        fun from(value: String?): PlaylistPlatform =
            entries.firstOrNull { it.source.equals(value, true) || it.name.equals(value, true) } ?: YOUTUBE_MUSIC
    }
}

/**
 * All of one service's playlists: its logo, name and playlist count centred at the top, then one
 * row per playlist. Tapping a row opens the normal playlist screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformPlaylistsScreen(
    platform: PlaylistPlatform,
    navController: NavController,
    onBackClick: () -> Unit,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playlists = remember(uiState.playlists, platform) {
        uiState.playlists.filter {
            it.source.equals(platform.source, true) && it.friendId == null && !isFavoritesPlaylistName(it.name)
        }
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        onClick = onBackClick
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = MiniPlayerHeight + bottomInset + 24.dp)
        ) {
            item(key = "platform_header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(platform.brand.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(platform.iconRes),
                            contentDescription = platform.label,
                            tint = platform.brand,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        platform.label,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (playlists.size == 1) "1 playlist" else "${playlists.size} playlists",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (playlists.isEmpty()) {
                item(key = "platform_empty") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No ${platform.label} playlists yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = { navController.navigateSafely(Screen.Accounts.route) }) {
                            Text("Connect ${platform.label}")
                        }
                    }
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                PlatformPlaylistRow(playlist) {
                    navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                }
            }
        }
    }
}

@Composable
private fun PlatformPlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = playlist.coverImageUri,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (playlist.songIds.size == 1) "1 song" else "${playlist.songIds.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
