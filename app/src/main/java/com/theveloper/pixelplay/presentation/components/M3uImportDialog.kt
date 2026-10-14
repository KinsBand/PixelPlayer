package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.viewmodel.M3uImportMode
import com.theveloper.pixelplay.presentation.viewmodel.M3uImportPreview

/**
 * Where an imported .m3u goes: a new playlist, added to one of yours (only the songs it doesn't
 * have yet), or replacing one's songs. A playlist with the file's name is picked by default.
 */
@Composable
fun M3uImportDialog(
    preview: M3uImportPreview,
    onDismiss: () -> Unit,
    onConfirm: (mode: M3uImportMode, targetId: String?, newName: String) -> Unit
) {
    val hasTargets = preview.targets.isNotEmpty()
    var mode by remember(preview) {
        mutableStateOf(if (preview.suggestedTargetId != null) M3uImportMode.MERGE else M3uImportMode.NEW)
    }
    var targetId by remember(preview) { mutableStateOf(preview.suggestedTargetId ?: preview.targets.firstOrNull()?.id) }
    var name by remember(preview) { mutableStateOf(preview.result.name) }
    val result = preview.result
    val missing = result.totalEntries - result.matchedCount

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FileOpen, contentDescription = null) },
        title = { Text("Import \"${result.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = buildString {
                        // What actually goes into the playlist (repeats in the file count once).
                        val unique = result.songIds.distinct().size
                        append("Found ${result.matchedCount} of ${result.totalEntries} songs")
                        if (unique < result.matchedCount) append(" · ${result.matchedCount - unique} repeats")
                        if (result.onlineCount > 0) append(" · ${result.onlineCount} added from online")
                        if (missing > 0) append(" · $missing couldn't be found")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ModeRow("New playlist", mode == M3uImportMode.NEW, enabled = true) { mode = M3uImportMode.NEW }
                ModeRow("Add to a playlist (merge)", mode == M3uImportMode.MERGE, enabled = hasTargets) { mode = M3uImportMode.MERGE }
                ModeRow("Replace a playlist's songs", mode == M3uImportMode.REPLACE, enabled = hasTargets) { mode = M3uImportMode.REPLACE }

                AnimatedVisibility(
                    visible = mode == M3uImportMode.NEW,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AnimatedVisibility(
                    visible = mode != M3uImportMode.NEW && hasTargets,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        items(preview.targets, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = playlist.id == targetId,
                                        role = Role.RadioButton,
                                        onClick = { targetId = playlist.id }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = playlist.id == targetId, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (playlist.id == preview.suggestedTargetId) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${playlist.songIds.size} songs" +
                                            if (playlist.id == preview.suggestedTargetId) " · same name as the file" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(mode, targetId, name) },
                enabled = when (mode) {
                    M3uImportMode.NEW -> name.isNotBlank()
                    else -> targetId != null
                }
            ) {
                Text(
                    when (mode) {
                        M3uImportMode.NEW -> "Create"
                        M3uImportMode.MERGE -> "Add songs"
                        M3uImportMode.REPLACE -> "Replace"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ModeRow(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
