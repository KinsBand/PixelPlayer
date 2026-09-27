package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.metadata.*
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.SongMetadataViewModel

@Composable
fun CompleteMetadataButton(song: Song, viewModel: SongMetadataViewModel = hiltViewModel()) {
    var opened by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { viewModel.load(song); opened = true }, modifier = Modifier.fillMaxWidth()) { Text("Complete metadata · Needs, wants and mixing") }
    if (!opened) return
    val document by viewModel.document.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var tier by remember { mutableStateOf(MetadataPriority.NEED) }
    var editing by remember { mutableStateOf<MetadataField?>(null) }
    var custom by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { opened = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                TextButton(onClick = { opened = false }) { Text("Back to song") }
                Text(song.title, style = MaterialTheme.typography.headlineSmall)
                Text("Tap a field to record a correction. Automatic sources keep their own claims.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataPriority.entries.forEach { priority -> FilterChip(selected = tier == priority, onClick = { tier = priority }, label = { Text(when(priority) {
                        MetadataPriority.NEED -> "Needs"; MetadataPriority.WANT -> "Wants"; MetadataPriority.HELPFUL -> "Helpful"; MetadataPriority.ALGORITHM -> "Algorithm"; MetadataPriority.SESSION -> "Session"
                    }) }) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { custom = true }) { Text("Add custom field") }
                val standard = SongMetadataCatalogue.fields.filter { it.priority == tier }
                val extra = if (tier == MetadataPriority.HELPFUL) document?.claims.orEmpty().keys.filter { it.startsWith("custom.") }.map { key -> MetadataField(key, key.removePrefix("custom."), "Custom", tier, MetadataScope.USER, listOf("Manual")) } else emptyList()
                LazyColumn(Modifier.weight(1f)) {
                    (standard + extra).groupBy { it.group }.forEach { (group, fields) ->
                        item { Text(group, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp)) }
                        items(fields, key = { it.key }) { field ->
                            val claim = document?.takeIf { it.songId == song.id }?.selected(field.key)
                            ListItem(headlineContent = { Text(field.label) }, supportingContent = {
                                Column {
                                    Text(claim?.value ?: "Unknown")
                                    Text(if (claim == null) "Sources: ${field.sources.joinToString(" · ")}" else "${claim.source} · ${claim.state.name.lowercase().replace('_', ' ')}" + if (claim.locked) " · manual lock" else "", style = MaterialTheme.typography.labelSmall)
                                }
                            }, modifier = Modifier.clickable { editing = field })
                        }
                    }
                }
            }
        }
        editing?.let { field ->
            var value by remember(field.key) { mutableStateOf(document?.selected(field.key)?.value.orEmpty()) }
            AlertDialog(onDismissRequest = { editing = null }, title = { Text(field.label) }, text = {
                OutlinedTextField(value, { value = it }, label = { Text("Value (empty means unknown)") }, modifier = Modifier.fillMaxWidth())
            }, confirmButton = { TextButton(onClick = { viewModel.save(song, field.key, value); editing = null }) { Text("Save correction") } },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
        }
        if (custom) {
            var key by remember { mutableStateOf("") }
            var value by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { custom = false }, title = { Text("Custom metadata") }, text = { Column {
                OutlinedTextField(key, { key = it }, label = { Text("Field name") })
                OutlinedTextField(value, { value = it }, label = { Text("Value") })
            } }, confirmButton = { TextButton(enabled = key.isNotBlank(), onClick = {
                val normalized = key.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(100)
                if (normalized.isNotBlank()) { viewModel.save(song, "custom.$normalized", value); tier = MetadataPriority.HELPFUL; custom = false }
            }) { Text("Add") } }, dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } })
        }
    }
}
