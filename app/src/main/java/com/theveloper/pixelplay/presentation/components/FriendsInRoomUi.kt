package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.viewmodel.FriendUi
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.launch

/**
 * The Friends row in the queue's options menu: title "Friends" and, below it, one row of the
 * pictures of the friends checked as being here. Tapping opens [FriendsInRoomSheet].
 */
@Composable
internal fun FriendsInRoomRow(
    friends: List<FriendUi>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val description = if (friends.isEmpty()) "Pick friends in the room"
    else "Friends in the room: " + friends.joinToString { it.name }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Friends", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.size(6.dp))
                AnimatedContent(
                    targetState = friends,
                    transitionSpec = {
                        fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                            fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                    },
                    contentKey = { list -> list.map { it.id } },
                    label = "friendsInRoomAvatars"
                ) { checked ->
                    if (checked.isEmpty()) {
                        Text(
                            "Tap to pick who's here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(checked, key = { it.id }) { friend ->
                                FriendAvatar(friend, size = 30.dp, showPresence = false)
                            }
                        }
                    }
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

/** Check off who's here in person; Save keeps the selection (their playlists join the mix). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendsInRoomSheet(
    friends: List<FriendUi>,
    selected: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var checked by remember(selected) { mutableStateOf(selected) }
    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Who's here?",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                "Songs from their playlists get mixed into your queue when they fit the mood.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
            )
            if (friends.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Add a friend's playlist from Library › Friends to start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Online friends first, then the usual order.
                val ordered = remember(friends) { friends.sortedBy { if (it.isOnline) 0 else 1 } }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(ordered, key = { it.id }) { friend ->
                        val isChecked = friend.id in checked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .toggleable(
                                    value = isChecked,
                                    role = Role.Checkbox,
                                    onValueChange = { on -> checked = if (on) checked + friend.id else checked - friend.id }
                                )
                                .padding(horizontal = 24.dp, vertical = 6.dp)
                                .animateItem(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FriendAvatar(friend, size = 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(friend.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val count = friend.playlists.size
                                Text(
                                    if (count == 1) "1 playlist" else "$count playlists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(checked = isChecked, onCheckedChange = null)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 24.dp,
                        top = 8.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { checked = emptySet() }, enabled = checked.isNotEmpty()) { Text("Clear") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { close { onSave(checked) } }) { Text("Save") }
            }
        }
    }
}
