package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.FriendUi
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * Rename one or many selected friends in one sheet: a row per friend (picture, name field, reset
 * to their platform name). Save writes every change at once; unchanged rows are skipped and a
 * blank (or reset) name goes back to the platform name (value null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendsRenameSheet(
    friends: List<FriendUi>,
    onSave: (Map<String, String?>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = rememberAnimatedSheetDismiss(sheetState, onDismiss)
    val focus = LocalFocusManager.current
    val drafts = remember(friends.map { it.id }) {
        mutableStateMapOf<String, String>().apply { friends.forEach { put(it.id, it.name) } }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                pluralStringResource(R.plurals.friends_rename_title, friends.size, friends.size),
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(friends, key = { _, f -> f.id }) { index, friend ->
                    val draft = drafts[friend.id].orEmpty()
                    val isLast = index == friends.lastIndex
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FriendAvatar(friend, size = 40.dp, showPresence = false)
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { drafts[friend.id] = it },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            placeholder = { Text(friend.platformName, maxLines = 1) },
                            keyboardOptions = KeyboardOptions(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focus.moveFocus(FocusDirection.Down) },
                                onDone = { focus.clearFocus() }
                            ),
                            trailingIcon = if (draft.trim() != friend.platformName) {
                                {
                                    IconButton(onClick = { drafts[friend.id] = friend.platformName }) {
                                        Icon(
                                            Icons.Rounded.RestartAlt,
                                            contentDescription = stringResource(R.string.friends_rename_reset_cd, friend.platformName)
                                        )
                                    }
                                }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val changes = friends.mapNotNull { f ->
                        val text = drafts[f.id].orEmpty().trim()
                        when {
                            text == f.name -> null
                            // Blank or the platform name: drop the alias.
                            text.isEmpty() || text == f.platformName -> f.id to null
                            else -> f.id to text
                        }
                    }.toMap()
                    if (changes.isNotEmpty()) onSave(changes)
                    dismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 52.dp)
            ) {
                Text(stringResource(R.string.friends_rename_save), fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Remove friends: who's being removed (overlapping pictures), what happens, and an optional
 * "Also unfollow on Spotify" switch (only when at least one of them is a Spotify friend).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendsRemoveSheet(
    friends: List<FriendUi>,
    canUnfollow: Boolean,
    onRemove: (unfollow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = rememberAnimatedSheetDismiss(sheetState, onDismiss)
    var unfollow by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Overlapping pictures of who's being removed (first 5).
            val shown = friends.take(5)
            Box(Modifier.height(44.dp).width((32 * (shown.size - 1) + 44).coerceAtLeast(44).dp)) {
                shown.forEachIndexed { i, friend ->
                    Box(
                        Modifier
                            .offset(x = (32 * i).dp)
                            .size(44.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                            .padding(2.dp)
                    ) { FriendAvatar(friend, size = 40.dp, showPresence = false) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                pluralStringResource(R.plurals.friends_remove_title, friends.size, friends.size),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.friends_remove_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (canUnfollow) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = unfollow, role = Role.Switch, onValueChange = { unfollow = it })
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.friends_remove_unfollow), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.friends_remove_unfollow_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        // The row is the toggle (state semantics on the row); the switch only shows it.
                        Switch(checked = unfollow, onCheckedChange = null)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onRemove(canUnfollow && unfollow)
                    dismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(stringResource(R.string.friends_remove_confirm), fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}
