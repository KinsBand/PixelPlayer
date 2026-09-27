package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

/**
 * Accounts & Services: everything that connects PixelPlayer to an outside service.
 *
 * Linked accounts keep their own screen (AccountsScreen); this page is the one place to
 * reach it, next to ListenBrainz scrobbling, which used to sit inside Playback.
 */
@Composable
internal fun ServicesSettingsContent(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    SettingsSubsection(title = stringResource(R.string.settings_accounts_section)) {
        SettingsItem(
            settingKey = "accounts",
            title = stringResource(R.string.settings_category_accounts_title),
            subtitle = stringResource(R.string.settings_category_accounts_subtitle),
            leadingIcon = { Icon(Icons.Rounded.AccountCircle, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { navController.navigateSafely(Screen.Accounts.route) }
        )
    }

    val listenBrainzToken by settingsViewModel.listenBrainzToken.collectAsStateWithLifecycle()
    var showListenBrainzDialog by remember { mutableStateOf(false) }

    SettingsSubsection(
        title = stringResource(R.string.settings_scrobbling_section),
        addBottomSpace = false
    ) {
        SettingsItem(
            settingKey = "listenbrainz_scrobbling",
            title = stringResource(R.string.settings_listenbrainz_title),
            subtitle = if (listenBrainzToken.isBlank()) {
                stringResource(R.string.settings_listenbrainz_off)
            } else {
                stringResource(R.string.settings_listenbrainz_on)
            },
            onClick = { showListenBrainzDialog = true },
            leadingIcon = { Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    if (showListenBrainzDialog) {
        var tokenInput by remember { mutableStateOf(listenBrainzToken) }
        AlertDialog(
            onDismissRequest = { showListenBrainzDialog = false },
            title = { Text(stringResource(R.string.settings_listenbrainz_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.settings_listenbrainz_dialog_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text(stringResource(R.string.settings_listenbrainz_token_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsViewModel.setListenBrainzToken(tokenInput)
                    showListenBrainzDialog = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (listenBrainzToken.isNotBlank()) settingsViewModel.setListenBrainzToken("")
                    showListenBrainzDialog = false
                }) {
                    Text(
                        if (listenBrainzToken.isNotBlank()) {
                            stringResource(R.string.settings_listenbrainz_turn_off)
                        } else {
                            stringResource(R.string.common_cancel)
                        }
                    )
                }
            }
        )
    }
}
