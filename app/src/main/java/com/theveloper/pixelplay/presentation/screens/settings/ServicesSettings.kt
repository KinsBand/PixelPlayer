package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
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
import com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Readiness
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
        ListenBrainzDialog(settingsViewModel, listenBrainzToken) { showListenBrainzDialog = false }
    }
}


/**
 * "Discord status": shows what you play in PixelPlayer as your status on Discord, through the
 * Discord app on this phone (Discord Social SDK). No Discord sign-in in PixelPlayer.
 */
@Composable
private fun DiscordStatusSetting(
    viewModel: com.theveloper.pixelplay.presentation.viewmodel.DiscordPresenceViewModel =
        androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val ready = readiness == com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Readiness.READY

    val subtitle = when (val s = status) {
        is com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Status.Error -> s.message
        else -> when (readiness) {
            com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Readiness.READY ->
                stringResource(if (enabled) R.string.discord_status_subtitle_on else R.string.discord_status_subtitle_off)
            com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Readiness.NO_SDK_IN_BUILD ->
                stringResource(R.string.discord_status_no_sdk)
            com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Readiness.DISCORD_NOT_INSTALLED ->
                stringResource(R.string.discord_status_install_short)
            com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Readiness.NO_APPLICATION_ID ->
                stringResource(R.string.discord_status_need_id_short)
        }
    }

    SettingsSubsection(title = "Discord", addBottomSpace = false) {
        SettingsItem(
            settingKey = "discord_status",
            title = "Discord status",
            subtitle = subtitle,
            onClick = { showDialog = true },
            leadingIcon = { Icon(Icons.Rounded.AccountCircle, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingContent = if (ready) {
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
        DiscordStatusDialog(viewModel) { showDialog = false }
    }
}

/** ListenBrainz sheet (token in, save / turn off); shared by Settings and the Profile service tiles. */
@Composable
internal fun ListenBrainzDialog(
    settingsViewModel: SettingsViewModel,
    listenBrainzToken: String,
    onDismiss: () -> Unit
) {
    var tokenInput by remember { mutableStateOf(listenBrainzToken) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val connected = listenBrainzToken.isNotBlank()
    com.theveloper.pixelplay.presentation.components.ServiceSheet(
        title = stringResource(R.string.settings_listenbrainz_title),
        status = stringResource(if (connected) R.string.service_sheet_listenbrainz_on else R.string.service_sheet_listenbrainz_off),
        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.History),
        brandColor = ListenBrainzOrange,
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it.trim() },
            label = { Text(stringResource(R.string.settings_listenbrainz_token_label)) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        com.theveloper.pixelplay.presentation.components.ServiceSheetPrimaryButton(
            text = stringResource(R.string.common_save),
            enabled = tokenInput.isNotBlank() && tokenInput != listenBrainzToken,
            onClick = {
                settingsViewModel.setListenBrainzToken(tokenInput)
                onDismiss()
            }
        )
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://listenbrainz.org/settings/"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Text(stringResource(R.string.service_sheet_listenbrainz_get_token)) }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (connected) {
                TextButton(onClick = {
                    settingsViewModel.setListenBrainzToken("")
                    onDismiss()
                }) { Text(stringResource(R.string.settings_listenbrainz_turn_off), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private val ListenBrainzOrange = androidx.compose.ui.graphics.Color(0xFFEB743B)
private val DiscordBlurple = androidx.compose.ui.graphics.Color(0xFF5865F2)

/**
 * Discord status sheet: one line of state, then only what's needed next (the switch when ready,
 * "Get Discord" when it's missing, the Application ID field when it isn't set). The ID is kept
 * only on this phone (never built into the app). Shared with the Profile tiles.
 */
@Composable
internal fun DiscordStatusDialog(
    viewModel: com.theveloper.pixelplay.presentation.viewmodel.DiscordPresenceViewModel,
    onDismiss: () -> Unit
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val savedId by viewModel.applicationId.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Discord may have been installed while the app was in the background.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        viewModel.refreshReadiness()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshReadiness()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var idInput by remember(savedId) { mutableStateOf(savedId) }
    var editingId by remember { mutableStateOf(false) }
    val showIdField = readiness == Readiness.NO_APPLICATION_ID || (readiness == Readiness.READY && editingId)
    val shownError = error ?: (status as? com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Status.Error)?.message

    com.theveloper.pixelplay.presentation.components.ServiceSheet(
        title = "Discord",
        status = when (readiness) {
            Readiness.READY -> stringResource(if (enabled) R.string.discord_status_subtitle_on else R.string.discord_status_subtitle_off)
            Readiness.NO_SDK_IN_BUILD -> stringResource(R.string.discord_status_no_sdk)
            Readiness.DISCORD_NOT_INSTALLED -> stringResource(R.string.discord_status_install_short)
            Readiness.NO_APPLICATION_ID -> stringResource(R.string.discord_status_need_id_short)
        },
        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Forum),
        brandColor = DiscordBlurple,
        onDismiss = onDismiss,
        error = shownError,
        moreOptions = if (readiness == Readiness.READY && !editingId) {
            {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { editingId = true }) { Text(stringResource(R.string.discord_status_change_id)) }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.disconnect(); onDismiss() }) {
                        Text(stringResource(R.string.discord_status_forget_id), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else null
    ) {
        when (readiness) {
            Readiness.READY -> if (!editingId) {
                androidx.compose.material3.Surface(
                    shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(20.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { viewModel.setEnabled(it) })
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.discord_status_switch),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.Switch(checked = enabled, onCheckedChange = null)
                    }
                }
            }
            Readiness.DISCORD_NOT_INSTALLED -> com.theveloper.pixelplay.presentation.components.ServiceSheetPrimaryButton(
                text = stringResource(R.string.discord_status_get_discord),
                onClick = {
                    val market = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.discord"))
                    val web = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.discord"))
                    try {
                        context.startActivity(market)
                    } catch (_: android.content.ActivityNotFoundException) {
                        context.startActivity(web)
                    }
                }
            )
            Readiness.NO_SDK_IN_BUILD, Readiness.NO_APPLICATION_ID -> Unit
        }
        if (showIdField) {
            OutlinedTextField(
                value = idInput,
                onValueChange = { idInput = it.filter(Char::isDigit).take(20) },
                label = { Text(stringResource(R.string.discord_status_id_label)) },
                supportingText = { Text(stringResource(R.string.discord_status_id_help)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (editingId) {
                    TextButton(onClick = { editingId = false; idInput = savedId }) { Text(stringResource(R.string.common_cancel)) }
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                }
                com.theveloper.pixelplay.presentation.components.ServiceSheetPrimaryButton(
                    text = stringResource(R.string.common_save),
                    enabled = idInput.length >= 17,
                    onClick = { if (viewModel.saveApplicationId(idInput)) editingId = false },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
