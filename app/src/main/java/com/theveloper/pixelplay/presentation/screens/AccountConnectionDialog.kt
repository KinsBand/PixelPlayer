package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.ServiceSheet
import com.theveloper.pixelplay.presentation.components.ServiceSheetPrimaryButton
import com.theveloper.pixelplay.presentation.viewmodel.*

/** Connect a music service: a short bottom sheet per service (one sign-in button; manual options folded away). */
@Composable
fun AccountConnectionDialog(service: ExternalServiceAccount, viewModel: AccountsViewModel, onDismiss: () -> Unit) {
    when (service) {
        ExternalServiceAccount.SPOTIFY -> SpotifyConnectionSheet(viewModel, onDismiss)
        ExternalServiceAccount.YOUTUBE_MUSIC -> YouTubeConnectionSheet(viewModel, onDismiss)
        ExternalServiceAccount.APPLE_MUSIC -> AppleMusicConnectionSheet(viewModel, onDismiss)
    }
}

private val SpotifyGreen = Color(0xFF1DB954)
private val YouTubeRed = Color(0xFFFF0033)
private val AppleMusicRed = Color(0xFFFA2D48)

/** Spotify: sign in (web-player session, no developer app), or paste the sp_dc cookie. */
@Composable
private fun SpotifyConnectionSheet(viewModel: AccountsViewModel, onDismiss: () -> Unit) {
    var cookie by remember { mutableStateOf("") }
    var showLogin by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    if (showLogin) {
        ServiceWebLoginDialog(
            target = WebLoginTarget.Spotify,
            onCookie = { value ->
                showLogin = false
                viewModel.connectSpotifyWeb(value) { onDismiss() }
            },
            onDismiss = { showLogin = false }
        )
        return
    }
    ServiceSheet(
        title = stringResource(R.string.service_sheet_spotify),
        status = stringResource(R.string.service_sheet_spotify_status),
        icon = painterResource(R.drawable.ic_source_spotify),
        brandColor = SpotifyGreen,
        onDismiss = onDismiss,
        busy = busy,
        error = error,
        moreOptions = {
            OutlinedTextField(
                value = cookie,
                onValueChange = { cookie = it },
                label = { Text(stringResource(R.string.service_sheet_spotify_cookie)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(
                enabled = !busy && cookie.isNotBlank(),
                onClick = { viewModel.connectSpotifyWeb(cookie) { cookie = ""; onDismiss() } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.service_sheet_connect)) }
        }
    ) {
        ServiceSheetPrimaryButton(
            text = stringResource(R.string.service_sheet_sign_in),
            enabled = !busy,
            onClick = {
                val cached = WebLoginTarget.readCookie(WebLoginTarget.Spotify.cookieUrls, WebLoginTarget.Spotify.cookieName)
                if (cached != null) viewModel.connectSpotifyWeb(cached) { onDismiss() } else showLogin = true
            }
        )
    }
}

/** YouTube Music: sign in with Google, or paste a cookie / request headers. */
@Composable
private fun YouTubeConnectionSheet(viewModel: AccountsViewModel, onDismiss: () -> Unit) {
    var credential by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("0") }
    var showLogin by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (showLogin) {
        ServiceWebLoginDialog(WebLoginTarget.YouTubeMusic, onCookie = { value ->
            showLogin = false
            viewModel.connectYouTube(value, account) { onDismiss() }
        }, onDismiss = { showLogin = false })
        return
    }
    ServiceSheet(
        title = stringResource(R.string.service_sheet_youtube_music),
        status = stringResource(R.string.service_sheet_youtube_music_status),
        icon = painterResource(R.drawable.ic_source_youtube_music),
        brandColor = YouTubeRed,
        onDismiss = onDismiss,
        busy = busy,
        error = error,
        moreOptions = {
            OutlinedTextField(
                value = credential,
                onValueChange = { credential = it },
                label = { Text(stringResource(R.string.service_sheet_youtube_credential)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 5
            )
            OutlinedTextField(
                value = account,
                onValueChange = { account = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.service_sheet_youtube_account)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ytmusicapi.readthedocs.io/en/stable/setup/browser.html")))
                }) { Text(stringResource(R.string.service_sheet_setup_guide)) }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    enabled = !busy && credential.isNotBlank(),
                    onClick = { viewModel.connectYouTube(credential, account) { credential = ""; onDismiss() } }
                ) { Text(stringResource(R.string.service_sheet_connect)) }
            }
        }
    ) {
        ServiceSheetPrimaryButton(
            text = stringResource(R.string.service_sheet_sign_in),
            enabled = !busy,
            onClick = {
                val cached = WebLoginTarget.readCookie(
                    WebLoginTarget.YouTubeMusic.cookieUrls,
                    WebLoginTarget.YouTubeMusic.cookieName,
                    captureFullCookie = true
                )
                if (cached != null) viewModel.connectYouTube(cached, account) { onDismiss() } else showLogin = true
            }
        )
    }
}

/** Apple Music: sign in on music.apple.com (web player), or paste the media-user-token. */
@Composable
private fun AppleMusicConnectionSheet(viewModel: AccountsViewModel, onDismiss: () -> Unit) {
    var token by remember { mutableStateOf("") }
    var showLogin by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    if (showLogin) {
        ServiceWebLoginDialog(
            target = WebLoginTarget.AppleMusic,
            onCookie = { value ->
                showLogin = false
                viewModel.connectAppleMusic(value) { onDismiss() }
            },
            onDismiss = { showLogin = false }
        )
        return
    }
    ServiceSheet(
        title = stringResource(R.string.service_sheet_apple_music),
        status = stringResource(R.string.service_sheet_apple_music_status),
        icon = painterResource(R.drawable.ic_source_apple_music),
        brandColor = AppleMusicRed,
        onDismiss = onDismiss,
        busy = busy,
        error = error,
        moreOptions = {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.service_sheet_apple_token)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(
                enabled = !busy && token.isNotBlank(),
                onClick = { viewModel.connectAppleMusic(token) { token = ""; onDismiss() } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.service_sheet_connect)) }
        }
    ) {
        ServiceSheetPrimaryButton(
            text = stringResource(R.string.service_sheet_sign_in),
            enabled = !busy,
            onClick = {
                val cached = WebLoginTarget.readCookie(WebLoginTarget.AppleMusic.cookieUrls, WebLoginTarget.AppleMusic.cookieName)
                if (cached != null) viewModel.connectAppleMusic(cached) { onDismiss() } else showLogin = true
            }
        )
    }
}
