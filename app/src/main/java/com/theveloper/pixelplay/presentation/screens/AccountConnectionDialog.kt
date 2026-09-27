package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.spotify.SpotifyAuthManager
import com.theveloper.pixelplay.presentation.viewmodel.*

@Composable
fun AccountConnectionDialog(service: ExternalServiceAccount, viewModel: AccountsViewModel, onDismiss: () -> Unit) {
    when (service) {
        ExternalServiceAccount.SPOTIFY -> SpotifyConnectionDialog(viewModel, onDismiss)
        ExternalServiceAccount.YOUTUBE_MUSIC -> YouTubeConnectionDialog(viewModel, onDismiss)
        ExternalServiceAccount.APPLE_MUSIC -> AppleMusicConnectionDialog(viewModel, onDismiss)
    }
}

/**
 * Spotify: sign in normally (web-player session, no developer app needed), paste the sp_dc
 * cookie, or, under "Advanced", use your own developer app like before.
 */
@Composable
private fun SpotifyConnectionDialog(viewModel: AccountsViewModel, onDismiss: () -> Unit) {
    var cookie by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AlertDialog(properties = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Connect Spotify") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!advanced) {
                Text("Sign in with your Spotify account. This brings in all your playlists and liked songs, and lets you see what friends are listening to. " +
                    "PixelPlayer keeps only the sign-in cookie, encrypted on this phone. It uses Spotify's web player, so it may need renewing now and then.")
                Button(enabled = !busy, onClick = { showLogin = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign in with Spotify") }
                OutlinedTextField(value = cookie, onValueChange = { cookie = it }, label = { Text("Or paste the sp_dc cookie") },
                    supportingText = { Text("From open.spotify.com in a desktop browser: Developer Tools → Application → Cookies.") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { advanced = true }) { Text("Advanced: use a developer app instead") }
            } else {
                Text("In your Spotify developer dashboard, create an app and register all of these redirect URIs exactly: " +
                    SpotifyAuthManager.REGISTERED_REDIRECT_URIS.joinToString("  ") +
                    ". Sign-in uses whichever of those ports is free, so register every one. Add your account to its allowed users. " +
                    "Developer apps can't read friends' playlists or activity.")
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.spotify.com/dashboard"))) }) { Text("Open setup guide") }
                OutlinedTextField(value = clientId, onValueChange = { clientId = it }, label = { Text("Spotify client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { advanced = false }) { Text("Back to normal sign-in") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = {
            if (advanced) TextButton(enabled = !busy && clientId.isNotBlank(), onClick = {
                viewModel.connectSpotify(clientId.trim()) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); onDismiss() }
            }) { Text("Connect") }
            else TextButton(enabled = !busy && cookie.isNotBlank(), onClick = {
                viewModel.connectSpotifyWeb(cookie) { cookie = ""; onDismiss() }
            }) { Text("Connect") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
    if (showLogin) ServiceWebLoginDialog(WebLoginTarget.Spotify, onCookie = { value ->
        showLogin = false
        viewModel.connectSpotifyWeb(value) { onDismiss() }
    }, onDismiss = { showLogin = false })
}

@Composable
private fun YouTubeConnectionDialog(viewModel: AccountsViewModel, onDismiss: () -> Unit) {
    var credential by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("0") }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AlertDialog(properties = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn), onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Connect YouTube Music") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sign in to music.youtube.com in your browser. In Developer Tools → Network, select a successful browse request. Paste its Cookie value, copied request headers, or browser.json contents here. Copied headers also select the matching Google account. The session is checked before saving. This unofficial connection may need renewing.")
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ytmusicapi.readthedocs.io/en/stable/setup/browser.html"))) }) { Text("Open setup guide") }
            OutlinedTextField(value = credential, onValueChange = { credential = it }, label = { Text("Cookie or browser request headers") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 5)
            OutlinedTextField(value = account, onValueChange = { account = it }, label = { Text("Google account index (usually 0)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } }, confirmButton = { TextButton(enabled = !busy && credential.isNotBlank(), onClick = {
            viewModel.connectYouTube(credential, account) { credential = ""; onDismiss() }
        }) { Text("Connect") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}

/**
 * Apple Music: sign in on music.apple.com (web-player session, no developer membership), or
 * paste the media-user-token cookie.
 */
@Composable
private fun AppleMusicConnectionDialog(viewModel: AccountsViewModel, onDismiss: () -> Unit) {
    var token by remember { mutableStateOf("") }
    var showLogin by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    AlertDialog(properties = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Connect Apple Music") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sign in with your Apple ID to bring in your Apple Music playlists and favourites. Songs play through PixelPlayer's matched sources. " +
                "PixelPlayer keeps only the sign-in cookie, encrypted on this phone. It uses Apple's web player, so it may need renewing.")
            Button(enabled = !busy, onClick = { showLogin = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign in with Apple Music") }
            OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Or paste the media-user-token cookie") },
                supportingText = { Text("From music.apple.com in a desktop browser: Developer Tools → Application → Cookies.") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = !busy && token.isNotBlank(), onClick = {
            viewModel.connectAppleMusic(token) { token = ""; onDismiss() }
        }) { Text("Connect") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
    if (showLogin) ServiceWebLoginDialog(WebLoginTarget.AppleMusic, onCookie = { value ->
        showLogin = false
        viewModel.connectAppleMusic(value) { onDismiss() }
    }, onDismiss = { showLogin = false })
}
