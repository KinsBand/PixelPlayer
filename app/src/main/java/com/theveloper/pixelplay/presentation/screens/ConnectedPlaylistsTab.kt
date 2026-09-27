@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.accounts.ConnectedPlaylist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.presentation.components.PlaylistItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.components.SourceBadge
import com.theveloper.pixelplay.presentation.viewmodel.*


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ConnectedPlaylistsTab(local: List<Playlist>, navController: NavController, player: PlayerViewModel,
    bottomPadding: Dp, isSelectionMode: Boolean, selectedIds: Set<String>, onLongPress: (Playlist) -> Unit,
    onSelect: (Playlist) -> Unit, onRefresh: () -> Unit, viewModel: ConnectedLibraryViewModel = hiltViewModel()) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val snapshot by viewModel.library.snapshot.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val syncing by viewModel.library.syncing.collectAsStateWithLifecycle()
    val error by viewModel.library.message.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<ConnectedPlaylist?>(null) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = syncing, onRefresh = { viewModel.refresh(); onRefresh() }) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Playlists", style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = !syncing, onClick = { viewModel.refresh(); onRefresh() }) { Text(if (syncing) "Syncing…" else "Sync") }
            }
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            ConnectedPlaylistCard(ConnectedPlaylist("combined-likes", "", "", "Liked Songs", songs = likes),
                subtitle = "${likes.size} songs · All your accounts") { opened = ConnectedPlaylist("combined-likes", "", "", "Liked Songs", songs = likes) }
        }
        item { Text("Your playlists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
        items(local.filter { !it.isAiGenerated && !it.isQueueGenerated }, key = { "local:${it.id}" }) { playlist ->
            PlaylistItem(playlist, player, onClick = { if (isSelectionMode) onSelect(playlist) else navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) },
                isAddingToPlaylist = false, isSelectionMode = isSelectionMode, isSelected = playlist.id in selectedIds,
                onLongPress = { onLongPress(playlist) }, onPlaylistSelectionToggle = { onSelect(playlist) })
        }
        items(snapshot.playlists.filter { it.friendId == null }, key = { it.id }) { p -> ConnectedPlaylistCard(p) { opened = p } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Friends", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { adding = true }) { Text("Add playlist") }
            }
        }
        if (snapshot.playlists.none { it.friendId != null }) item { Text("Add a friend's public playlist link to keep it here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        snapshot.playlists.filter { it.friendId != null }.groupBy { it.friendId!! }.forEach { (id, playlists) ->
            val name = snapshot.aliases[id] ?: playlists.first().ownerName.ifBlank { "Friend" }
            item(key = "friend:$id") { TextButton(onClick = { renaming = id to name }) { Text(name, style = MaterialTheme.typography.titleMedium) } }
            items(playlists, key = { it.id }) { p -> ConnectedPlaylistCard(p) { opened = p } }
        }
    }
    }
    if (adding) FriendPlaylistDialog(viewModel) { adding = false }
    renaming?.let { (id, name) ->
        var newName by remember(id) { mutableStateOf(name) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename friend") }, text = {
            OutlinedTextField(newName, { newName = it }, label = { Text("Name in your library") }, singleLine = true)
        }, confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = { viewModel.rename(id, newName); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
    opened?.let { original ->
        val playlist = if (original.id == "combined-likes") original.copy(songs = likes) else snapshot.playlists.find { it.id == original.id } ?: original
        val downloading by viewModel.downloading.collectAsStateWithLifecycle()
        Dialog(onDismissRequest = { opened = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { opened = null }) { Text("Back") }
                        Text(playlist.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (playlist.source.isNotBlank()) SourceBadge(playlist.source)
                    }
                    playlist.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId)?.let { url -> TextButton(onClick = { uriHandler.openUri(url) }) { Text("Open in " + com.theveloper.pixelplay.data.accounts.MusicSources.displayName(playlist.source)) } }
                    val playable = playlist.songs.filter { it.contentUriString.isNotBlank() }
                    TextButton(enabled = playable.isNotEmpty(), onClick = { player.playSongs(playable, playable.first(), playlist.title) }) { Text("Play all · ${playlist.songs.size} songs") }
                    LazyColumn(Modifier.weight(1f)) {
                        items(playlist.songs.size) { index ->
                            val song = playlist.songs[index]
                            val available = song.contentUriString.isNotBlank()
                            ListItem(headlineContent = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(if (available) song.artist else "Unavailable from this service") },
                                modifier = Modifier.clickable(enabled = available) { player.playSongs(playable, song, playlist.title) },
                                trailingContent = { Row {
                                    IconButton(enabled = available, onClick = { player.toggleFavoriteSpecificSong(song.copy(isFavorite = song.id in favoriteIds)) }) { Icon(if (song.id in favoriteIds) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like ${song.title}") }
                                    IconButton(enabled = available && song.id !in downloading, onClick = { viewModel.download(song) }) {
                                        if (song.id in downloading) CircularProgressIndicator(Modifier.size(20.dp)) else Icon(Icons.Rounded.Download, "Download ${song.title}")
                                    }
                                } })
                        }
                    }
                }
            }
        }
    }
    notice?.let { AlertDialog(onDismissRequest = { viewModel.notice.value = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { viewModel.notice.value = null }) { Text("OK") } }) }
}

@Composable
private fun ConnectedPlaylistCard(playlist: ConnectedPlaylist, subtitle: String = "${playlist.songs.size} songs" + if (playlist.error != null) " · Needs attention" else "", onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(20.dp, 60), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            com.theveloper.pixelplay.presentation.components.SmartImage(model = playlist.coverUrl ?: playlist.songs.firstOrNull()?.albumArtUriString, contentDescription = null, modifier = Modifier.size(48.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (playlist.source.isNotBlank()) SourceBadge(playlist.source)
        }
    }
}

@Composable
private fun FriendPlaylistDialog(viewModel: ConnectedLibraryViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) { if (notice != null) busy = false }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Add friend's playlist") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Friend's name") }, singleLine = true)
            OutlinedTextField(link, { link = it }, label = { Text("Public playlist link") }, singleLine = true)
            Text("Use the same name to group another playlist with this friend. Works with Spotify, YouTube Music and Apple Music links.", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(enabled = !busy && name.isNotBlank() && link.isNotBlank(), onClick = { busy = true; viewModel.add(name, link, onDismiss) }) { Text("Add") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}


