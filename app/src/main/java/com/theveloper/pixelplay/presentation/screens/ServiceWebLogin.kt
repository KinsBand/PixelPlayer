package com.theveloper.pixelplay.presentation.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.spotify.web.SpotifyAuthCodeDetector
import com.theveloper.pixelplay.presentation.components.SongCoverLoadingIndicator
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/** Where a service keeps the cookie we need after a normal sign-in in its own web page. */
data class WebLoginTarget(
    val title: String,
    val startUrl: String,
    /** URLs whose cookies are checked after each page load. */
    val cookieUrls: List<String>,
    val cookieName: String,
    val hint: String,
    /** User-Agent for the sign-in page; null keeps the WebView's sanitized User-Agent. */
    val userAgent: String? = null,
    val captureFullCookie: Boolean = false
) {
    companion object {
        val Spotify = WebLoginTarget(
            title = "Sign in to Spotify",
            startUrl = "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F",
            cookieUrls = listOf(
                "https://accounts.spotify.com",
                "https://open.spotify.com",
                "https://www.spotify.com",
                "https://spotify.com"
            ),
            cookieName = "sp_dc",
            hint = "Sign in to your Spotify account. Once signed in, PixelPlayer automatically connects and syncs.",
            userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
            captureFullCookie = false
        )
        val YouTubeMusic = WebLoginTarget(
            title = "Sign in to YouTube Music",
            startUrl = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F",
            cookieUrls = listOf(
                "https://music.youtube.com",
                "https://www.youtube.com",
                "https://youtube.com"
            ),
            cookieName = "SAPISID",
            hint = "Sign in with your Google account. Your YouTube Music playlists and liked songs will sync automatically.",
            userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
            captureFullCookie = true
        )
        val AppleMusic = WebLoginTarget(
            title = "Sign in to Apple Music",
            startUrl = "https://music.apple.com/login",
            cookieUrls = listOf("https://music.apple.com", "https://apple.com"),
            cookieName = "media-user-token",
            hint = "Sign in with your Apple ID. If the page says the browser isn't supported, paste the cookie instead.",
            captureFullCookie = false
        )

        /**
         * Strips embedded WebView identifiers ("; wv" and "Version/4.0") from the User-Agent
         * so third-party OAuth providers (e.g. Google Sign-In) don't reject the login page
         * with 403: disallowed_useragent.
         */
        fun sanitizeUserAgent(raw: String): String {
            return raw
                .replace(Regex(";\\s*wv", RegexOption.IGNORE_CASE), "")
                .replace(Regex("Version/\\d+\\.\\d+\\s*", RegexOption.IGNORE_CASE), "")
        }

        fun readCookie(urls: List<String>, name: String, captureFullCookie: Boolean = false): String? {
            val manager = CookieManager.getInstance()
            manager.flush()
            return urls.firstNotNullOfOrNull { url ->
                val raw = manager.getCookie(url) ?: return@firstNotNullOfOrNull null
                val pairs = raw.split(';').map { it.trim() }
                val hasMatch = if (captureFullCookie && (name == "SAPISID" || name.contains("SAPISID"))) {
                    pairs.any {
                        (it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") || it.startsWith("__Secure-1PAPISID=")) &&
                            it.substringAfter('=').trim().trim('"').length > 10
                    }
                } else {
                    pairs.any { it.startsWith("$name=") && it.substringAfter('=').trim().trim('"').length > 10 }
                }
                if (!hasMatch) return@firstNotNullOfOrNull null
                if (captureFullCookie) {
                    raw.trim().takeIf { it.isNotBlank() }
                } else {
                    pairs.firstOrNull { it.startsWith("$name=") }
                        ?.substringAfter('=')
                        ?.trim()
                        ?.trim('"')
                        ?.takeIf { it.length > 20 }
                }
            }
        }

        /** Expires the cookie in the WebView so the session only lives in the encrypted vault. */
        fun forgetCookie(target: WebLoginTarget) {
            val manager = CookieManager.getInstance()
            target.cookieUrls.forEach { url ->
                val host = Uri.parse(url).host ?: return@forEach
                val domain = "." + host.split('.').takeLast(2).joinToString(".")
                if (target.captureFullCookie) {
                    listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID", "SID", "HSID", "SSID", "LOGIN_INFO", "APISID", "__Secure-1PSID", "__Secure-3PSID").forEach { name ->
                        manager.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$domain")
                        manager.setCookie(url, "$name=; Max-Age=0; Path=/")
                    }
                } else {
                    manager.setCookie(url, "${target.cookieName}=; Max-Age=0; Path=/; Domain=$domain")
                    manager.setCookie(url, "${target.cookieName}=; Max-Age=0; Path=/")
                }
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
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0.1f) }
    var currentUrl by remember { mutableStateOf(target.startUrl) }
    var delivered by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var autoFillStatus by remember { mutableStateOf<String?>(null) }

    val detectedCode by SpotifyAuthCodeDetector.detectedCode.collectAsStateWithLifecycle()

    val deliver: () -> Unit = {
        if (!delivered) {
            WebLoginTarget.readCookie(target.cookieUrls, target.cookieName, target.captureFullCookie)?.let { value ->
                delivered = true
                onCookie(value)
            }
        }
    }

    // Activate Spotify OTP Code Detector (incoming emails, SMS, notifications)
    DisposableEffect(target) {
        if (target == WebLoginTarget.Spotify) {
            SpotifyAuthCodeDetector.startListening()
        }
        onDispose {
            if (target == WebLoginTarget.Spotify) {
                SpotifyAuthCodeDetector.stopListening()
            }
        }
    }

    // Monitor clipboard for copied verification codes while dialog is open
    LaunchedEffect(target) {
        if (target == WebLoginTarget.Spotify) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            while (true) {
                val clip = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                SpotifyAuthCodeDetector.onClipboard(clip)
                delay(1000)
            }
        }
    }

    fun injectCode(code: String, onResult: ((Boolean) -> Unit)? = null) {
        val cleanCode = code.replace(Regex("""\D"""), "").take(6)
        if (cleanCode.length < 4) {
            onResult?.invoke(false)
            return
        }
        val js = """
            (function(code) {
                if (!code) return JSON.stringify({ success: false, reason: 'empty_code' });
                var clean = code.replace(/[^0-9]/g, '').slice(0, 6);
                if (clean.length < 4) return JSON.stringify({ success: false, reason: 'too_short' });

                function setNativeValue(element, value) {
                    try {
                        var valueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                        if (valueSetter && valueSetter.set) {
                            valueSetter.set.call(element, value);
                        } else {
                            element.value = value;
                        }
                    } catch (e) {
                        element.value = value;
                    }
                    try {
                        if (element._valueTracker) {
                            element._valueTracker.setValue('');
                        }
                    } catch (e) {}
                }

                function dispatchKeyAndInputEvents(element, char) {
                    var keyCode = char.charCodeAt(0);

                    try {
                        element.dispatchEvent(new KeyboardEvent('keydown', {
                            key: char,
                            code: 'Digit' + char,
                            keyCode: keyCode,
                            which: keyCode,
                            charCode: keyCode,
                            bubbles: true,
                            cancelable: true
                        }));
                    } catch (e) {}

                    try {
                        element.dispatchEvent(new KeyboardEvent('keypress', {
                            key: char,
                            code: 'Digit' + char,
                            keyCode: keyCode,
                            which: keyCode,
                            charCode: keyCode,
                            bubbles: true,
                            cancelable: true
                        }));
                    } catch (e) {}

                    try {
                        element.dispatchEvent(new InputEvent('beforeinput', {
                            inputType: 'insertText',
                            data: char,
                            bubbles: true,
                            cancelable: true
                        }));
                    } catch (e) {}

                    setNativeValue(element, char);

                    try {
                        element.dispatchEvent(new InputEvent('input', {
                            inputType: 'insertText',
                            data: char,
                            bubbles: true
                        }));
                    } catch (e) {
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                    }

                    try {
                        element.dispatchEvent(new KeyboardEvent('keyup', {
                            key: char,
                            code: 'Digit' + char,
                            keyCode: keyCode,
                            which: keyCode,
                            charCode: keyCode,
                            bubbles: true
                        }));
                    } catch (e) {}

                    try {
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                    } catch (e) {}
                }

                function triggerPaste(target, text) {
                    if (!target) return false;
                    try {
                        var dt = new DataTransfer();
                        dt.setData('text/plain', text);
                        var pasteEv = new ClipboardEvent('paste', {
                            clipboardData: dt,
                            bubbles: true,
                            cancelable: true
                        });
                        target.dispatchEvent(pasteEv);
                        return true;
                    } catch (e) {
                        return false;
                    }
                }

                function trySubmit() {
                    var selectors = [
                        'button[type="submit"]',
                        'button[data-testid*="submit"]',
                        'button[data-testid*="login"]',
                        'button[data-testid*="otp"]',
                        'button[data-encore-id="buttonPrimary"]',
                        'button#login-button',
                        'button#submit-button',
                        'button[data-testid="auth-accept"]'
                    ];
                    for (var i = 0; i < selectors.length; i++) {
                        var btn = document.querySelector(selectors[i]);
                        if (btn && !btn.disabled) {
                            btn.click();
                            return true;
                        }
                    }
                    var form = document.querySelector('form');
                    if (form) {
                        try {
                            form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                            return true;
                        } catch(e) {}
                    }
                    return false;
                }

                function findInputs() {
                    var allInputs = Array.from(document.querySelectorAll('input')).filter(function(i) {
                        var type = (i.type || 'text').toLowerCase();
                        return type !== 'hidden' && type !== 'checkbox' && type !== 'radio' &&
                               type !== 'submit' && type !== 'button' && type !== 'password' &&
                               !i.disabled && !i.readOnly && i.offsetParent !== null;
                    });

                    // 1. Separate digit inputs
                    var digitInputs = allInputs.filter(function(i) {
                        var aria = (i.getAttribute('aria-label') || '').toLowerCase();
                        var id = (i.id || '').toLowerCase();
                        var name = (i.name || '').toLowerCase();
                        var testId = (i.getAttribute('data-testid') || '').toLowerCase();
                        return i.maxLength === 1 || i.getAttribute('maxlength') === '1' ||
                               aria.indexOf('digit') !== -1 || testId.indexOf('digit') !== -1 ||
                               id.indexOf('digit') !== -1 || name.indexOf('digit') !== -1 ||
                               aria.indexOf('code') !== -1;
                    });

                    if (digitInputs.length >= clean.length) {
                        return { type: 'multi', elements: digitInputs.slice(0, clean.length) };
                    }

                    if (allInputs.length === clean.length) {
                        return { type: 'multi', elements: allInputs };
                    }

                    // 2. Single code input
                    var singleInputs = allInputs.filter(function(i) {
                        var id = (i.id || '').toLowerCase();
                        var name = (i.name || '').toLowerCase();
                        var ph = (i.placeholder || '').toLowerCase();
                        var aria = (i.getAttribute('aria-label') || '').toLowerCase();
                        var testId = (i.getAttribute('data-testid') || '').toLowerCase();
                        var auto = (i.getAttribute('autocomplete') || '').toLowerCase();
                        var mode = (i.getAttribute('inputmode') || '').toLowerCase();
                        return id.indexOf('code') !== -1 || id.indexOf('otp') !== -1 ||
                               name.indexOf('code') !== -1 || name.indexOf('otp') !== -1 ||
                               ph.indexOf('code') !== -1 || aria.indexOf('code') !== -1 ||
                               testId.indexOf('code') !== -1 || testId.indexOf('otp') !== -1 ||
                               auto.indexOf('one-time-code') !== -1 || mode === 'numeric';
                    });

                    if (singleInputs.length > 0) {
                        return { type: 'single', elements: [singleInputs[0]] };
                    }

                    // 3. Fallback: exactly 1 visible input on entire page
                    if (allInputs.length === 1) {
                        return { type: 'single', elements: [allInputs[0]] };
                    }

                    return null;
                }

                var targets = findInputs();
                if (!targets) {
                    return JSON.stringify({ success: false, reason: 'not_found' });
                }

                if (targets.type === 'multi') {
                    var digits = targets.elements;
                    triggerPaste(digits[0], clean);
                    if (digits[0].parentElement) {
                        triggerPaste(digits[0].parentElement, clean);
                    }

                    for (var i = 0; i < digits.length; i++) {
                        (function(idx, char) {
                            setTimeout(function() {
                                try {
                                    digits[idx].focus();
                                    dispatchKeyAndInputEvents(digits[idx], char);
                                } catch(e) {}
                                if (idx === digits.length - 1) {
                                    setTimeout(trySubmit, 350);
                                }
                            }, idx * 60);
                        })(i, clean[i]);
                    }
                    return JSON.stringify({ success: true, mode: 'multi', count: digits.length });
                } else {
                    var input = targets.elements[0];
                    try {
                        input.focus();
                        triggerPaste(input, clean);
                        setNativeValue(input, clean);
                        try {
                            input.dispatchEvent(new InputEvent('beforeinput', { inputType: 'insertText', data: clean, bubbles: true, cancelable: true }));
                        } catch(e) {}
                        try {
                            input.dispatchEvent(new InputEvent('input', { inputType: 'insertText', data: clean, bubbles: true }));
                        } catch(e) {
                            input.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        setTimeout(trySubmit, 350);
                    } catch(e) {}
                    return JSON.stringify({ success: true, mode: 'single' });
                }
            })('$cleanCode');
        """.trimIndent()

        webViewInstance?.evaluateJavascript(js) { res ->
            val success = res != null && res.contains("\"success\":true")
            if (success) {
                autoFillStatus = "✓ Code $cleanCode auto-filled into form!"
            } else if (autoFillStatus?.startsWith("✓") != true) {
                autoFillStatus = "Code $cleanCode copied • Tap Auto-fill or paste"
            }
            onResult?.invoke(success)
        }
    }

    // Automatically copy to clipboard and inject code into Spotify login webview when detected
    LaunchedEffect(detectedCode) {
        detectedCode?.let { code ->
            SpotifyAuthCodeDetector.copyToClipboard(context, code)
            autoFillStatus = "Code $code copied • Auto-filling…"
            var filled = false
            for (attempt in 0..7) {
                if (filled) break
                injectCode(code) { success ->
                    if (success) filled = true
                }
                delay(600)
            }
        }
    }

    // Actively poll for the session cookie every 200ms during redirects/auth handoffs
    // so we can dismiss the webview the exact instant the cookie is set.
    LaunchedEffect(delivered) {
        while (!delivered) {
            deliver()
            delay(200)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top App Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = target.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Secure connection",
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = Uri.parse(currentUrl).host.orEmpty().ifBlank { Uri.parse(target.startUrl).host.orEmpty() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    hasError = false
                                    errorMessage = null
                                    loading = true
                                    progress = 0.1f
                                    webViewInstance?.reload()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reload",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl.ifBlank { target.startUrl }))
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Open in browser",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Progress indicator
                        val animatedProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                            label = "webProgress"
                        )
                        if (loading && animatedProgress < 1f) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        } else {
                            Spacer(Modifier.height(2.5.dp))
                        }
                    }
                }

                // Hint banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = target.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Detected Code Auto-Fill Banner
                AnimatedVisibility(
                    visible = detectedCode != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val rawCode = detectedCode.orEmpty()
                    val formattedCode = if (rawCode.length == 6) "${rawCode.take(3)} ${rawCode.takeLast(3)}" else rawCode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = AbsoluteSmoothCornerShape(16.dp, 60),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Code: $formattedCode",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = autoFillStatus ?: "Copied to clipboard • Auto-filling…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    detectedCode?.let {
                                        SpotifyAuthCodeDetector.copyToClipboard(context, it)
                                        Toast.makeText(context, "Code $it copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    detectedCode?.let {
                                        SpotifyAuthCodeDetector.copyToClipboard(context, it)
                                        injectCode(it)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Auto-fill",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // WebView and states
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(androidx.compose.ui.graphics.Color(0xFF121212))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                                setBackgroundColor(android.graphics.Color.parseColor("#121212"))
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    setSupportMultipleWindows(false)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    loadsImagesAutomatically = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    mediaPlaybackRequiresUserGesture = false
                                    userAgentString = target.userAgent ?: WebLoginTarget.sanitizeUserAgent(userAgentString)
                                }
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        progress = newProgress / 100f
                                        if (newProgress >= 100) {
                                            loading = false
                                        }
                                        deliver()
                                    }

                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        android.util.Log.d("ServiceWebLogin", "JS Console: ${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                        loading = true
                                        currentUrl = url
                                        hasError = false
                                        errorMessage = null
                                        deliver()
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        loading = false
                                        currentUrl = url
                                        CookieManager.getInstance().flush()
                                        deliver()
                                        // Auto-inject code if page finished loading after code arrived
                                        detectedCode?.let { injectCode(it) }
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                        currentUrl = url
                                        CookieManager.getInstance().flush()
                                        deliver()
                                    }

                                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                        if (request.isForMainFrame) {
                                            loading = false
                                            hasError = true
                                            errorMessage = error.description?.toString() ?: "Failed to connect to sign-in page"
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val scheme = request.url.scheme?.lowercase().orEmpty()
                                        // Block intents trying to open external apps (Spotify, YouTube, YouTube Music) during web login
                                        if (scheme in setOf("intent", "spotify", "android-app", "vnd.youtube", "vnd.youtube.music")) {
                                            return true
                                        }
                                        return scheme !in setOf("http", "https")
                                    }
                                }
                                loadUrl(target.startUrl)
                            }
                        },
                        update = {
                            webViewInstance = it
                        },
                        onRelease = {
                            it.stopLoading()
                            it.destroy()
                            webViewInstance = null
                        }
                    )

                    // Initial loading indicator before first page paint
                    if (loading && progress < 0.4f && !hasError && !delivered) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color(0xFF121212)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SongCoverLoadingIndicator(
                                    size = 44.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Loading sign-in page…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Error overlay
                    if (hasError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = AbsoluteSmoothCornerShape(24.dp, 60)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Unable to Load Sign-In",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = errorMessage ?: "Could not connect to the service. Please check your internet connection.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl.ifBlank { target.startUrl }))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Open Browser")
                                        }
                                        Button(
                                            onClick = {
                                                hasError = false
                                                errorMessage = null
                                                loading = true
                                                progress = 0.1f
                                                webViewInstance?.loadUrl(target.startUrl)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Try Again")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Success state overlay
                    if (delivered) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color(0xCC000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                shape = AbsoluteSmoothCornerShape(24.dp, 60),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    SongCoverLoadingIndicator(
                                        size = 28.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Sign-in successful! Connecting…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
