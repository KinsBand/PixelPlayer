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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

    DiscordStatusSetting()

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


/**
 * "Discord status": shows what you play in PixelPlayer as "Listening to" on your Discord
 * profile. Signs in with your own Discord application (official OAuth2, no user token).
 */
@Composable
private fun DiscordStatusSetting(
    viewModel: com.theveloper.pixelplay.presentation.viewmodel.DiscordPresenceViewModel =
        androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val connected = status is com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Status.Connected

    val subtitle = when (val s = status) {
        is com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Status.Connected ->
            if (enabled) "On \u00B7 ${s.userName}" else "Off \u00B7 connected as ${s.userName}"
        is com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Status.Waiting -> s.message
        is com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Status.Error -> s.message
        else -> "Show what you're playing on your Discord profile"
    }

    SettingsSubsection(title = "Discord", addBottomSpace = false) {
        SettingsItem(
            settingKey = "discord_status",
            title = "Discord status",
            subtitle = subtitle,
            onClick = { showDialog = true },
            leadingIcon = { Icon(Icons.Rounded.AccountCircle, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingContent = if (connected) {
                {
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = { viewModel.setEnabled(it) },
                        modifier = Modifier.semantics { contentDescription = "Show listening on Discord" }
                    )
                }
            } else null
        )
    }

    if (showDialog) {
        var clientId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Discord status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (connected) {
                        Text(
                            "Songs you play in PixelPlayer show as \u201CListening to\u201D on your Discord profile. " +
                                "The status clears 30 seconds after you pause.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "1. In the Discord Developer Portal, create an application.\n" +
                                "2. Under OAuth2, turn on Public Client and add these redirects:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        viewModel.redirectUris.forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("3. Paste its Application ID here and connect.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it.filter(Char::isDigit) },
                            label = { Text("Application ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Discord may only allow status updates for approved apps. If it refuses, you'll see why here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    (error ?: (status as? com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Status.Error)?.message)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                if (connected) {
                    TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.common_save)) }
                } else {
                    TextButton(
                        enabled = clientId.length >= 17,
                        onClick = {
                            viewModel.error.value = null
                            viewModel.connect(clientId) { url -> uriHandler.openUri(url) }
                        }
                    ) { Text("Connect") }
                }
            },
            dismissButton = {
                if (connected) {
                    TextButton(onClick = { viewModel.disconnect(); showDialog = false }) { Text("Disconnect") }
                } else {
                    TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        )
    }
}
