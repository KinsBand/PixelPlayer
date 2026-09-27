package com.theveloper.pixelplay.presentation.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Where a service keeps the cookie we need after a normal sign-in in its own web page. */
data class WebLoginTarget(
    val title: String,
    val startUrl: String,
    /** URLs whose cookies are checked after each page load. */
    val cookieUrls: List<String>,
    val cookieName: String,
    val hint: String
) {
    companion object {
        val Spotify = WebLoginTarget(
            title = "Sign in to Spotify",
            // Like Spotube: sign in on accounts.spotify.com and stay there (it ends on /status).
            // Going on to open.spotify.com makes phones try to open the Spotify app instead.
            startUrl = "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Faccounts.spotify.com%2Fen%2Fstatus",
            cookieUrls = listOf("https://accounts.spotify.com", "https://open.spotify.com", "https://www.spotify.com", "https://spotify.com"),
            cookieName = "sp_dc",
            hint = "Google sign-in may be blocked inside apps. Use email, phone or another option, or paste the cookie instead."
        )
        val AppleMusic = WebLoginTarget(
            title = "Sign in to Apple Music",
            startUrl = "https://music.apple.com/login",
            cookieUrls = listOf("https://music.apple.com", "https://apple.com"),
            cookieName = "media-user-token",
            hint = "Sign in with your Apple ID. If the page says the browser isn't supported, paste the cookie instead."
        )

        fun readCookie(urls: List<String>, name: String): String? {
            val manager = CookieManager.getInstance()
            return urls.firstNotNullOfOrNull { url ->
                manager.getCookie(url)?.split(';')?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.takeIf { it.length > 20 }
            }
        }

        /** Expires the cookie in the WebView so the session only lives in the encrypted vault. */
        fun forgetCookie(target: WebLoginTarget) {
            val manager = CookieManager.getInstance()
            target.cookieUrls.forEach { url ->
                val host = android.net.Uri.parse(url).host ?: return@forEach
                val domain = "." + host.split('.').takeLast(2).joinToString(".")
                manager.setCookie(url, "${target.cookieName}=; Max-Age=0; Path=/; Domain=$domain")
                manager.setCookie(url, "${target.cookieName}=; Max-Age=0; Path=/")
            }
            manager.flush()
        }
    }
}

/**
 * Full-screen sign-in page for a music service. As soon as the service sets its session
 * cookie, [onCookie] is called once with its value and the page closes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ServiceWebLoginDialog(target: WebLoginTarget, onCookie: (String) -> Unit, onDismiss: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf(target.startUrl) }
    var delivered by remember { mutableStateOf(false) }
    val deliver: () -> Unit = {
        if (!delivered) {
            WebLoginTarget.readCookie(target.cookieUrls, target.cookieName)?.let { value ->
                delivered = true
                onCookie(value)
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false,
        securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Column(Modifier.weight(1f)) {
                        Text(target.title, style = MaterialTheme.typography.titleMedium)
                        Text(android.net.Uri.parse(currentUrl).host.orEmpty(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(target.hint, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // Some sign-in pages refuse obvious in-app browsers.
                            settings.userAgentString = settings.userAgentString.replace("; wv", "")
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                    loading = true; currentUrl = url
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    loading = false; currentUrl = url
                                    CookieManager.getInstance().flush()
                                    deliver()
                                }
                                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                    currentUrl = url
                                    deliver()
                                }
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    // Keep sign-in inside the page; never hand URLs to other apps.
                                    return request.url.scheme !in setOf("http", "https")
                                }
                            }
                            loadUrl(target.startUrl)
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() }
                )
            }
        }
    }
}
