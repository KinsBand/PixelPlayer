package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SearchBrowseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBrowseSheet(id: String, title: String, player: PlayerViewModel, onDismiss: () -> Unit,
    viewModel: SearchBrowseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(id) {
        viewModel.open(id)
        onDispose { viewModel.close() }
    }
    // Add Song from the lyrics screen: a tap opens the action sheet over this one; once an
    // action is picked (session over), close this sheet too so it can't cover the lyrics.
    val songTap = com.theveloper.pixelplay.presentation.components.LocalSongPrimaryTap.current
    val addSongSession by player.addSongSession.collectAsStateWithLifecycle()
    var sawSession by remember { mutableStateOf(addSongSession != null) }
    LaunchedEffect(addSongSession) {
        if (addSongSession != null) sawSession = true else if (sawSession) onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            contentPadding = PaddingValues(bottom = 32.dp)) {
            items(state.songs, key = { it.id }) { song ->
                EnhancedSongListItem(song = song, isPlaying = false, isCurrentSong = false,
                    showMoreOptionsButton = false, onMoreOptionsClick = {},
                    onClick = {
                        if (songTap != null) {
                            songTap.onSongTap(song, state.songs, title)
                        } else {
                            player.showAndPlaySong(song, state.songs, title); onDismiss()
                        }
                    })
            }
            if (state.loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp)) }
            if (state.error != null) item {
                Text(state.error.orEmpty(), modifier = Modifier.padding(16.dp))
                TextButton(onClick = viewModel::loadMore) { Text("Retry") }
            }
            if (!state.loading && state.error == null && state.songs.isEmpty()) item {
                Text("No playable tracks on this page.", modifier = Modifier.padding(16.dp))
            }
            if (!state.loading && state.error == null && state.continuation != null) item {
                TextButton(onClick = viewModel::loadMore) { Text("Load more tracks") }
            }
        }
    }
}
