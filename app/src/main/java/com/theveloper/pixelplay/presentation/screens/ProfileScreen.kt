package com.theveloper.pixelplay.presentation.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.account.CloudAccountConfig
import com.theveloper.pixelplay.data.account.CloudAccountState
import com.theveloper.pixelplay.data.account.RestoreSummary
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.ProfileOperation
import com.theveloper.pixelplay.presentation.viewmodel.ProfileViewModel
import java.time.OffsetDateTime

/**
 * Settings → Profile: an email + password PixelPlay account that keeps a copy of the library
 * (liked songs, playlists, Spotify / YouTube Music sign-ins, settings and the rest) so it can be
 * restored on a new phone.
 */
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    /** Needed for Accounts & services (opens connected accounts, scrobbling, Discord). */
    navController: androidx.navigation.NavController? = null,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val restoreResult by viewModel.restoreResult.collectAsStateWithLifecycle()
    val offerRestore by viewModel.offerRestore.collectAsStateWithLifecycle()
    val googleAvatar by viewModel.googleAvatarUrl.collectAsStateWithLifecycle()
    val customAvatarUri by viewModel.customAvatarUri.collectAsStateWithLifecycle()
    var showAvatarAdjustment by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshCloudInfo() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = 64.dp + statusBarHeight

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = headerHeight + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Who you are + one sync status pill.
            item(key = "header") {
                ProfileHeader(
                    state = state,
                    operation = operation,
                    googleAvatarUrl = googleAvatar,
                    customAvatarUri = customAvatarUri,
                    onAvatarClick = { showAvatarAdjustment = true },
                    onChangePassword = { state.email?.let(viewModel::sendPasswordReset) },
                    onSignOut = { confirmSignOut = true }
                )
            }

            // Nothing works until the server is set: that comes first, open.
            if (!state.isConfigured) {
                item(key = "server_first") {
                    ServerCard(state = state, startExpanded = true, onSave = viewModel::setServer)
                }
            }

            // 2. Cloud copy (signed in) or the sign-in card.
            item(key = "main") {
                AnimatedContent(
                    targetState = state.isSignedIn,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "profile_signed_in"
                ) { signedIn ->
                    if (signedIn) {
                        SignedInContent(
                            state = state,
                            operation = operation,
                            onSave = viewModel::saveNow,
                            onRestore = { confirmRestore = true },
                            onAutoSave = viewModel::setAutoSave,
                        )
                    } else {
                        SignInCard(
                            enabled = state.isConfigured,
                            operation = operation,
                            onSignIn = viewModel::signIn,
                            onSignUp = viewModel::signUp,
                            onForgot = viewModel::sendPasswordReset
                        )
                    }
                }
            }

            // 3. Connected accounts: one tile per service, status at a glance.
            if (navController != null) {
                item(key = "services") {
                    ConnectedAccountsSection(navController = navController)
                }
            }

            // Backup files (moved here from Settings): export / import a .pxpl file.
            item(key = "backup") {
                Column {
                    ProfileSectionLabel("Backup file")
                    val settingsViewModel: com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel = hiltViewModel()
                    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                    com.theveloper.pixelplay.presentation.screens.settings.BackupSettingsContent(
                        settingsViewModel = settingsViewModel,
                        uiState = settingsUiState
                    )
                }
            }

            // 5. Advanced: server keys (collapsed).
            if (state.isConfigured) {
                item(key = "server_last") {
                    Column {
                        ProfileSectionLabel("Advanced")
                        ServerCard(state = state, startExpanded = false, onSave = viewModel::setServer)
                    }
                }
            }

            // 6. Danger zone.
            if (state.isSignedIn) {
                item(key = "danger") {
                    Column {
                        ProfileSectionLabel("Danger zone")
                        DangerZoneCard(
                            enabled = operation == null && state.cloudUpdatedAt != null,
                            running = operation == ProfileOperation.Delete,
                            onDelete = { confirmDelete = true }
                        )
                    }
                }
            }
        }

        CollapsibleCommonTopBar(
            title = "Profile",
            collapseFraction = 1f,
            headerHeight = headerHeight,
            onBackClick = onBackClick,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp
        )
    }

    if (showAvatarAdjustment) {
        AvatarAdjustmentDialog(
            currentAvatarUri = customAvatarUri,
            email = state.email,
            googleAvatarUrl = googleAvatar,
            onSave = viewModel::setCustomAvatarUri,
            onDismiss = { showAvatarAdjustment = false }
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
            title = { Text("Restore from your account?") },
            text = {
                Text(
                    "Playlists, liked songs, settings and the rest on this phone are replaced with the copy " +
                        "saved ${cloudSavedLabel(state) ?: "in your account"}. Music files aren't touched."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; viewModel.restore() }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } }
        )
    }

    if (offerRestore) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreOffer,
            icon = { Icon(Icons.Rounded.CloudDone, contentDescription = null) },
            title = { Text("Bring your library over?") },
            text = {
                Text(
                    "This account has a copy saved ${cloudSavedLabel(state) ?: "earlier"}. Restore it on this phone, " +
                        "or keep this phone's library and save it over the account's copy."
                )
            },
            confirmButton = { TextButton(onClick = viewModel::restore) { Text("Restore") } },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::dismissRestoreOffer) { Text("Later") }
                    TextButton(onClick = { viewModel.dismissRestoreOffer(); viewModel.saveNow() }) { Text("Keep this phone's") }
                }
            }
        )
    }

    if (confirmDelete) {
        var typed by remember { mutableStateOf("") }
        val confirmed = typed.trim().equals("DELETE", ignoreCase = false)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete the saved copy?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Removes your library copy from the account. Nothing on this phone changes, and you stay signed in.")
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(12) },
                        label = { Text("Type DELETE to confirm") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.deleteCloudCopy() },
                    enabled = confirmed
                ) { Text("Delete", color = if (confirmed) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
            title = { Text("Sign out?") },
            text = { Text("Auto-save stops. Your music and the copy in your account both stay where they are.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }

    restoreResult?.let { result ->
        RestoreDoneDialog(result = result, onDismiss = viewModel::dismissRestoreResult, onRestart = { restartApp(context) })
    }
}

@Composable
private fun ProfileHeader(
    state: CloudAccountState,
    operation: ProfileOperation?,
    googleAvatarUrl: String?,
    customAvatarUri: String?,
    onAvatarClick: () -> Unit,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
      Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (state.isSignedIn) 12.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                val initial = state.email?.firstOrNull()?.uppercaseChar()
                if (initial != null) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                // The picture linked to the signed-in email (Gravatar); the initial stays underneath
                // when there's none or it can't load.
                EmailAvatarImage(state.email, googleAvatarUrl, Modifier.matchParentSize(), customAvatarUri = customAvatarUri)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // The whole email on one horizontal line: the text shrinks to fit instead of wrapping.
                OneLineFitText(
                    text = state.email ?: "Your PixelPlay account",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!state.isConfigured || !state.isSignedIn) {
                    Text(
                        text = if (!state.isConfigured) "Set up the account server below to get started"
                        else "Sign in to keep your library safe and move it between phones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                ProfileSyncPill(state, operation)
            }
        }
        if (state.isSignedIn) {
            // Account actions right under the email: change password, and sign out as an icon.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onChangePassword,
                    enabled = operation == null && state.email != null,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    OperationIcon(operation == ProfileOperation.ResetPassword, Icons.Rounded.Password, MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("Change password", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalIconButton(
                    onClick = onSignOut,
                    enabled = operation == null,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Sign out" }
                ) {
                    OperationIcon(operation == ProfileOperation.SignOut, Icons.AutoMirrored.Rounded.Logout, MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
      }
    }
}

@Composable
private fun SignInCard(
    enabled: Boolean,
    operation: ProfileOperation?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onForgot: (String) -> Unit
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordValid = password.length >= 8
    val confirmValid = !creating || confirm == password
    val busy = operation == ProfileOperation.SignIn || operation == ProfileOperation.SignUp

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    icon = {},
                    selected = !creating,
                    onClick = { creating = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Sign in") }
                SegmentedButton(
                    icon = {},
                    selected = creating,
                    onClick = { creating = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Create account") }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                enabled = enabled && !busy,
                isError = email.isNotBlank() && !emailValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                supportingText = if (creating) ({ Text("At least 8 characters. It also locks your saved sign-ins.") }) else null,
                singleLine = true,
                enabled = enabled && !busy,
                isError = creating && password.isNotEmpty() && !passwordValid,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(
                visible = creating,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    enabled = enabled && !busy,
                    isError = confirm.isNotEmpty() && !confirmValid,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    if (creating) onSignUp(email.trim(), password) else onSignIn(email.trim(), password)
                },
                enabled = enabled && !busy && emailValid && password.isNotEmpty() &&
                    (!creating || (passwordValid && confirmValid)),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (creating) "Create account" else "Sign in", fontWeight = FontWeight.Bold)
                }
            }
            if (!creating) {
                TextButton(
                    onClick = { onForgot(email.trim()) },
                    enabled = enabled && emailValid && operation == null,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Forgot password?") }
            }
        }
    }
}

@Composable
private fun SignedInContent(
    state: CloudAccountState,
    operation: ProfileOperation?,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onAutoSave: (Boolean) -> Unit,
) {
    var showWhatsSaved by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileSectionLabel("Cloud copy")
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // This phone | In your account, side by side in one row.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    StatusLine(
                        icon = Icons.Rounded.History,
                        title = "This phone",
                        value = state.lastSavedAt?.let { "Saved ${relative(it)}" } ?: "Not saved yet",
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    StatusLine(
                        icon = Icons.Rounded.CloudDone,
                        title = "In your account",
                        value = cloudSavedLabel(state)?.let { label ->
                            state.cloudSizeBytes?.let { "$label · ${Formatter.formatShortFileSize(LocalContext.current, it)}" } ?: label
                        } ?: "Nothing saved yet",
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                state.lastSummary?.let { summary ->
                    Text(
                        text = buildString {
                            append("Last save: ${summary.sections} library sections")
                            if (summary.onlineSongs > 0) append(", ${summary.onlineSongs} online songs")
                            if (summary.preferenceFiles > 0) append(", ${summary.preferenceFiles} app stores")
                            append(if (summary.includesSessions) ", service sign-ins (encrypted)" else ", no service sign-ins")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSave,
                        enabled = operation == null,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        OperationIcon(operation == ProfileOperation.Save, Icons.Rounded.CloudUpload, MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Save now", fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = onRestore,
                        enabled = operation == null && state.cloudUpdatedAt != null,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        OperationIcon(operation == ProfileOperation.Restore, Icons.Rounded.CloudDownload, MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Restore", fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAutoSave(!state.autoSave) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-save", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Every 12 hours when online, in the background",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.autoSave, onCheckedChange = onAutoSave)
                }
                if (!state.canEncryptSessions) {
                    Text(
                        "Sign out and back in to include your Spotify / YouTube Music sign-ins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                // "What's saved" folds into one row.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = if (showWhatsSaved) "Hide" else "Show") { showWhatsSaved = !showWhatsSaved }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("What your account keeps", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(if (showWhatsSaved) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                }
                AnimatedVisibility(visible = showWhatsSaved, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    WhatsSavedList()
                }
            }
        }
    }
}

@Composable
private fun OperationIcon(running: Boolean, icon: ImageVector, color: androidx.compose.ui.graphics.Color) {
    if (running) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StatusLine(icon: ImageVector, title: String, value: String, modifier: Modifier = Modifier) {
    // A compact tile so two fit side by side: icon + title on top, the value under it.
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WhatsSavedList() {
    run {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SavedItem(Icons.Rounded.Favorite, "Liked songs", "Local and online (YouTube Music, Spotify) likes")
            SavedItem(Icons.AutoMirrored.Rounded.QueueMusic, "Your playlists", "Including covers, order and online songs in them")
            SavedItem(Icons.Rounded.Lock, "Spotify, YouTube Music & Apple Music sign-ins", "Encrypted with your password before upload")
            SavedItem(Icons.Rounded.Settings, "Settings & sound", "Themes, equalizer, transitions, lyrics")
            SavedItem(Icons.Rounded.History, "History & everything else", "Stats, search history, Practice, radio favourites, filters")
            Text(
                "Music files themselves stay on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SavedItem(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServerCard(state: CloudAccountState, startExpanded: Boolean, onSave: (String, String) -> Unit) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    var url by rememberSaveable(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var key by remember { mutableStateOf("") }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isConfigured) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Dns, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.isConfigured) state.serverUrl.removePrefix("https://") else "Not set up",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Your account lives in a Supabase project. Paste its URL and anon key " +
                            "(Supabase → Project Settings → API).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Project URL") },
                        placeholder = { Text("https://xxxx.supabase.co") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("Anon key") },
                        placeholder = { Text(if (state.isConfigured) "Leave empty to keep the current key" else "eyJhbGciOi…") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The one-time storage setup (bucket + policies) for a fresh Supabase project.
                        TextButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("PixelPlay setup SQL", CloudAccountConfig.SETUP_SQL))
                            Toast.makeText(context, "Copied. Paste it in Supabase → SQL editor and run it once", Toast.LENGTH_LONG).show()
                        }) { Text("Copy setup SQL") }
                        FilledTonalButton(
                            onClick = { onSave(url, key) },
                            enabled = url.trim().startsWith("https://") && (key.isNotBlank() || state.isConfigured),
                            shape = CircleShape
                        ) { Text("Save server") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreDoneDialog(result: RestoreSummary, onDismiss: () -> Unit, onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CloudDone, contentDescription = null) },
        title = { Text(if (result.failedSections.isEmpty()) "Library restored" else "Mostly restored") },
        text = {
            Text(
                buildString {
                    append("${result.restoredSections} library sections")
                    if (result.onlineSongs > 0) append(", ${result.onlineSongs} online songs")
                    if (result.preferenceFiles > 0) append(", ${result.preferenceFiles} app stores")
                    append(".")
                    if (result.sessionsRestored) append("\nSpotify / YouTube Music sign-ins are back.")
                    if (result.sessionsLocked) append("\nSaved sign-ins couldn't be unlocked (the password changed since). Reconnect those services.")
                    if (result.failedSections.isNotEmpty()) append("\nNot restored: ${result.failedSections.joinToString()}.")
                    append("\n\nRestart PixelPlay to finish loading everything.")
                }
            )
        },
        confirmButton = { TextButton(onClick = onRestart) { Text("Restart now") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
    )
}

private fun cloudSavedLabel(state: CloudAccountState): String? {
    val raw = state.cloudUpdatedAt ?: return null
    val millis = runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull() ?: return "recently"
    return relative(millis)
}

private fun relative(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        .replaceFirstChar { it.lowercase() }

/** Relaunches the app so every store re-reads what the restore wrote. */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

/**
 * The account card at the top of Settings: avatar, email (or a sign-in prompt) and the last
 * save. Opens [ProfileScreen].
 */
@Composable
fun SettingsProfileCard(
    onClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val googleAvatar by viewModel.googleAvatarUrl.collectAsStateWithLifecycle()
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                val initial = state.email?.firstOrNull()?.uppercaseChar()
                if (initial != null) {
                    Text(
                        initial.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                }
                EmailAvatarImage(state.email, googleAvatar, Modifier.matchParentSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                OneLineFitText(
                    text = state.email ?: "Profile",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = when {
                        !state.isSignedIn -> "Accounts & services · sign in to save your library"
                        state.lastSavedAt != null -> "Library saved ${relative(state.lastSavedAt!!)}"
                        else -> "Signed in · tap to save your library"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 2
                )
            }
            Icon(
                Icons.Rounded.CloudDone.takeIf { state.isSignedIn } ?: Icons.Rounded.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** Small section heading between the profile cards. */
@Composable
private fun ProfileSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/** "Saved 2 h ago" / "Saving…" / "Not signed in" / "Not saved yet", as one pill. */
@Composable
private fun ProfileSyncPill(state: CloudAccountState, operation: ProfileOperation?) {
    val colors = MaterialTheme.colorScheme
    val (label, icon) = when {
        !state.isSignedIn -> "Not signed in" to Icons.Rounded.CloudOff
        operation == ProfileOperation.Save -> "Saving…" to Icons.Rounded.CloudUpload
        operation == ProfileOperation.Restore -> "Restoring…" to Icons.Rounded.CloudDownload
        state.lastSavedAt != null -> "Saved ${relative(state.lastSavedAt)}" to Icons.Rounded.CloudDone
        else -> "Not saved from this phone yet" to Icons.Rounded.Cloud
    }
    AnimatedContent(
        targetState = label to icon,
        transitionSpec = {
            fadeIn(androidx.compose.animation.core.tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium1,
                easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate)) togetherWith
                fadeOut(androidx.compose.animation.core.tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort3,
                    easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate))
        },
        label = "profileSyncPill"
    ) { (text, image) ->
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surface.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(image, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Connected accounts as a 2-column grid of service tiles (logo-style icon, status dot,
 * "Connected" / "Connect"). Spotify, YouTube Music and Apple Music open Accounts;
 * ListenBrainz and Discord open their input pop-ups directly.
 */
@Composable
private fun ConnectedAccountsSection(navController: androidx.navigation.NavController) {
    val accountsViewModel: com.theveloper.pixelplay.presentation.viewmodel.AccountsViewModel = hiltViewModel()
    val settingsViewModel: com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel = hiltViewModel()
    val discordViewModel: com.theveloper.pixelplay.presentation.viewmodel.DiscordPresenceViewModel = hiltViewModel()
    val accounts by accountsViewModel.uiState.collectAsStateWithLifecycle()
    val listenBrainzToken by settingsViewModel.listenBrainzToken.collectAsStateWithLifecycle()
    val discordStatus by discordViewModel.status.collectAsStateWithLifecycle()
    var showListenBrainz by rememberSaveable { mutableStateOf(false) }
    var showDiscord by rememberSaveable { mutableStateOf(false) }
    val connected = accounts.connectedAccounts.map { it.service }.toSet()
    val openAccounts = {
        navController.navigate(com.theveloper.pixelplay.presentation.navigation.Screen.Accounts.route) { launchSingleTop = true }
    }
    val tiles = listOf(
        ServiceTileModel("Spotify", Icons.Rounded.MusicNote,
            com.theveloper.pixelplay.presentation.viewmodel.ExternalServiceAccount.SPOTIFY in connected, openAccounts),
        ServiceTileModel("YouTube Music", Icons.Rounded.SmartDisplay,
            com.theveloper.pixelplay.presentation.viewmodel.ExternalServiceAccount.YOUTUBE_MUSIC in connected, openAccounts),
        ServiceTileModel("Apple Music", Icons.Rounded.LibraryMusic,
            com.theveloper.pixelplay.presentation.viewmodel.ExternalServiceAccount.APPLE_MUSIC in connected, openAccounts),
        ServiceTileModel("ListenBrainz", Icons.Rounded.GraphicEq, listenBrainzToken.isNotBlank()) { showListenBrainz = true },
        ServiceTileModel("Discord", Icons.Rounded.Forum,
            discordStatus is com.theveloper.pixelplay.data.presence.DiscordPresenceManager.Status.Connected) { showDiscord = true },
    )
    Column {
        ProfileSectionLabel("Connected accounts")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tile -> ServiceTile(tile, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    // Tapping ListenBrainz / Discord opens the value pop-up right here: no separate settings block.
    if (showListenBrainz) {
        com.theveloper.pixelplay.presentation.screens.settings.ListenBrainzDialog(
            settingsViewModel = settingsViewModel,
            listenBrainzToken = listenBrainzToken,
            onDismiss = { showListenBrainz = false }
        )
    }
    if (showDiscord) {
        com.theveloper.pixelplay.presentation.screens.settings.DiscordStatusDialog(
            viewModel = discordViewModel,
            onDismiss = { showDiscord = false }
        )
    }
}

private data class ServiceTileModel(
    val name: String,
    val icon: ImageVector,
    val connected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ServiceTile(tile: ServiceTileModel, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = tile.onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        modifier = modifier.semantics(mergeDescendants = true) {
            stateDescription = if (tile.connected) "Connected" else "Not connected"
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(tile.icon, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (tile.connected) com.theveloper.pixelplay.presentation.components.scoped.OnlineDotGreen else colors.outline)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (tile.connected) "Connected" else "Connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OperationIcon(running, icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Delete the cloud copy, in an error-tinted card (confirmation asks to type DELETE). */
@Composable
private fun DangerZoneCard(enabled: Boolean, running: Boolean, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
    ) {
        ProfileActionRow(
            icon = Icons.Rounded.DeleteOutline,
            title = "Delete cloud copy",
            subtitle = "Removes the saved library from your account. This phone keeps everything.",
            running = running,
            enabled = enabled,
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * One horizontal line that never wraps or cuts off: the font shrinks (down to half size) until
 * the whole text fits the available width.
 */
@Composable
private fun OneLineFitText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var scale by remember(text) { mutableFloatStateOf(1f) }
    Text(
        text = text,
        style = style.copy(
            fontSize = style.fontSize * scale,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight
        ),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { if (it.hasVisualOverflow && scale > 0.5f) scale = (scale * 0.92f).coerceAtLeast(0.5f) }
    )
}

/**
 * The profile picture for [email] from Gravatar (the public avatar service most email accounts
 * use), looked up by a SHA-256 hash of the address, never the address itself. Draws nothing when
 * the email has no picture (`d=404`) or offline, so whatever is under it (the initial) shows.
 */
@Composable
private fun EmailAvatarImage(
    email: String?,
    googleAvatarUrl: String?,
    modifier: Modifier = Modifier,
    customAvatarUri: String? = null
) {
    if (!customAvatarUri.isNullOrBlank()) {
        coil.compose.AsyncImage(
            model = customAvatarUri,
            contentDescription = "Profile picture",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
        return
    }
    // A connected Google (Gmail) account's own picture wins; otherwise the Gravatar for the email.
    val url = remember(email, googleAvatarUrl) {
        googleAvatarUrl?.let(::largeGooglePhoto) ?: email?.let(::gravatarUrl)
    } ?: return
    coil.compose.AsyncImage(
        model = url,
        contentDescription = "Profile picture",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier.clip(CircleShape)
    )
}

@Composable
private fun AvatarAdjustmentDialog(
    currentAvatarUri: String?,
    email: String?,
    googleAvatarUrl: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedUri by remember { mutableStateOf(currentAvatarUri) }
    // A photo just picked: framed in the circle, pinch / drag to adjust, saved cropped on Save.
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var cropScale by remember { mutableStateOf(1f) }
    var cropPan by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val saveScope = androidx.compose.runtime.rememberCoroutineScope()
    // Reuses the playlist-cover crop (same pan / zoom maths as ImageCropView).
    val cropSaver: com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            pickedUri = uri
            selectedUri = uri.toString()
        }
    }
    androidx.compose.runtime.LaunchedEffect(pickedUri) {
        cropScale = 1f
        cropPan = androidx.compose.ui.geometry.Offset.Zero
        preview = pickedUri?.let { decodeAvatarPreview(context, it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile Picture") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Tap the circle below to pick a photo from your gallery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Circular framed preview
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { galleryLauncher.launch("image/*") }
                        .padding(4.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val pickedPreview = preview
                    if (pickedPreview != null) {
                        // Pinch to zoom, drag to move; a tap still picks another photo.
                        com.theveloper.pixelplay.presentation.components.ImageCropView(
                            imageBitmap = pickedPreview,
                            scale = cropScale,
                            pan = cropPan,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            onCrop = { s, p -> cropScale = s; cropPan = p }
                        )
                    } else if (!selectedUri.isNullOrBlank()) {
                        coil.compose.AsyncImage(
                            model = selectedUri,
                            contentDescription = "Selected avatar",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        EmailAvatarImage(
                            email = email,
                            googleAvatarUrl = googleAvatarUrl,
                            modifier = Modifier.fillMaxSize(),
                            customAvatarUri = null
                        )
                    }

                    // Edit indicator overlay (hidden while adjusting a picked photo, so it's visible).
                    if (pickedPreview == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_edit_24),
                                contentDescription = "Choose image",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                if (preview != null) {
                    Text(
                        text = "Pinch and drag to frame it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!selectedUri.isNullOrBlank()) {
                    TextButton(onClick = { selectedUri = null; pickedUri = null }) {
                        Text("Reset to default", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    val picked = pickedUri
                    if (picked == null) {
                        onSave(selectedUri)
                        onDismiss()
                    } else {
                        // Save the framed square (cropped to the circle's view) as the picture.
                        saving = true
                        saveScope.launch {
                            val path = cropSaver.saveCoverImageToInternalStorage(
                                uri = picked,
                                uniqueId = "avatar_${System.currentTimeMillis()}",
                                cropScale = cropScale,
                                cropPanX = cropPan.x,
                                cropPanY = cropPan.y
                            )
                            saving = false
                            onSave(path?.let { android.net.Uri.fromFile(java.io.File(it)).toString() } ?: picked.toString())
                            onDismiss()
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** A photo picked for the avatar, decoded small (longest side ≤ 1024 px) for the crop preview. */
private suspend fun decodeAvatarPreview(context: android.content.Context, uri: android.net.Uri): androidx.compose.ui.graphics.ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
                ?.asImageBitmap()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

/** Google photo URLs end in "=s88-c…"; ask for a sharper 256 px crop instead. */
internal fun largeGooglePhoto(url: String): String =
    if (url.contains("googleusercontent.com") || url.contains("ggpht.com"))
        url.replace(Regex("=[swh]\\d+[^/?]*$"), "=s256-c")
    else url

/** Gravatar URL for [email] (trimmed, lower-cased, SHA-256), 200 px, no default image. */
internal fun gravatarUrl(email: String): String? {
    val normalized = email.trim().lowercase(java.util.Locale.ROOT).takeIf { it.contains('@') } ?: return null
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "https://www.gravatar.com/avatar/$hash?s=200&d=404"
}
