package com.theveloper.pixelplay.data.spotify.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Detects incoming Spotify login / verification OTP codes from emails, SMS,
 * notifications, and clipboard, allowing PixelPlayer to auto-fill them into the web login.
 */
object SpotifyAuthCodeDetector {

    private val _detectedCode = MutableStateFlow<String?>(null)
    val detectedCode: StateFlow<String?> = _detectedCode.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    @Volatile
    private var lastSeenClipboard: String? = null

    @Volatile
    private var lastCodeTimestamp = 0L

    fun startListening() {
        _isListening.value = true
        _detectedCode.value = null
        lastCodeTimestamp = 0L
    }

    fun stopListening() {
        _isListening.value = false
        _detectedCode.value = null
    }

    /**
     * Inspects notification text (title, content, bigText) from email clients,
     * SMS, or Spotify to identify 6-digit verification codes.
     */
    fun onNotification(packageName: String, text: String) {
        if (!_isListening.value) return
        val code = extractVerificationCode(packageName, text) ?: return
        Timber.d("SpotifyAuthCodeDetector: detected code from notification (%s): %s", packageName, code)
        lastCodeTimestamp = System.currentTimeMillis()
        _detectedCode.value = code
    }

    /**
     * Inspects clipboard contents when user copies a code or returns from their email app.
     */
    fun onClipboard(text: String?) {
        if (!_isListening.value) return
        val trimmed = text?.trim() ?: return
        if (trimmed == lastSeenClipboard) return
        lastSeenClipboard = trimmed

        val code = extractCodeFromClipboard(trimmed) ?: return
        Timber.d("SpotifyAuthCodeDetector: detected code from clipboard: %s", code)
        lastCodeTimestamp = System.currentTimeMillis()
        _detectedCode.value = code
    }

    fun clearCode() {
        _detectedCode.value = null
    }

    fun setManualCode(code: String) {
        _detectedCode.value = code
    }

    fun markClipboardSeen(code: String) {
        lastSeenClipboard = code.trim()
    }

    /**
     * Copies the detected verification code into the Android system clipboard so that
     * Gboard / Samsung keyboard immediately surfaces the 1-tap OTP paste chip, and native
     * long-press paste has the code ready.
     */
    fun copyToClipboard(context: Context, code: String) {
        try {
            markClipboardSeen(code)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Spotify Code", code)
            clipboard?.setPrimaryClip(clip)
            Timber.d("SpotifyAuthCodeDetector: copied code %s to system clipboard", code)
        } catch (e: Exception) {
            Timber.w(e, "Failed to copy code to system clipboard")
        }
    }

    private fun extractVerificationCode(packageName: String, text: String): String? {
        return extractCode(packageName, text)
    }

    private fun extractCode(packageName: String, text: String): String? {
        if (text.isBlank()) return null
        val isRelevantSource = isRelevantPackage(packageName) ||
            text.contains("spotify", ignoreCase = true) ||
            text.contains("verification", ignoreCase = true) ||
            text.contains("security code", ignoreCase = true) ||
            text.contains("one-time", ignoreCase = true)

        if (!isRelevantSource) return null

        // 1. Explicit Spotify verification code phrases:
        // "Spotify code is 123456", "Your code: 123456", "Spotify code: 123456"
        val explicitRegex = Regex("""(?i)(?:spotify|code|security|verification|login).*?(\b\d{3}[-\s]?\d{3}\b)""")
        explicitRegex.find(text)?.let { match ->
            return match.groupValues[1].replace(Regex("""[-\s]"""), "")
        }

        // 2. Reverse pattern: "123456 is your Spotify verification code"
        val reverseRegex = Regex("""(?i)(\b\d{3}[-\s]?\d{3}\b).*?(?:spotify|verification|security|code)""")
        reverseRegex.find(text)?.let { match ->
            return match.groupValues[1].replace(Regex("""[-\s]"""), "")
        }

        // 3. Fallback: Standalone 6-digit number if source is explicitly Spotify or an email client
        if (isEmailClient(packageName) || packageName.contains("spotify", ignoreCase = true)) {
            val standaloneRegex = Regex("""\b(\d{6})\b""")
            standaloneRegex.find(text)?.let { match ->
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun extractCodeFromClipboard(text: String): String? {
        if (text.length > 250) return null // Avoid large blobs
        // Exactly 6 digits or 3-3 digits
        val exactMatch = Regex("""^\s*(\d{3}[-\s]?\d{3})\s*$""").find(text)
        if (exactMatch != null) {
            return exactMatch.groupValues[1].replace(Regex("""[-\s]"""), "")
        }
        // Text containing a 6 digit code with spotify or code keywords
        val textMatch = Regex("""(?i)(?:spotify|code|pin).*?(\b\d{3}[-\s]?\d{3}\b)""").find(text)
        if (textMatch != null) {
            return textMatch.groupValues[1].replace(Regex("""[-\s]"""), "")
        }
        // Standalone 6-digit code in text
        val standalone = Regex("""\b(\d{6})\b""").find(text)
        return standalone?.groupValues?.get(1)
    }

    private fun isEmailClient(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower in EMAIL_PACKAGES ||
            lower.contains("mail") ||
            lower.contains("email") ||
            lower.contains("outlook")
    }

    private fun isRelevantPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return isEmailClient(lower) ||
            lower in MESSAGING_PACKAGES ||
            lower.contains("spotify") ||
            lower.contains("message") ||
            lower.contains("sms")
    }

    private val EMAIL_PACKAGES = setOf(
        "com.google.android.gm",              // Gmail
        "com.microsoft.office.outlook",       // Outlook
        "com.yahoo.mobile.client.android.mail",// Yahoo Mail
        "com.samsung.android.email.provider", // Samsung Email
        "ch.protonmail.android",              // Proton Mail
        "com.fsck.k9",                        // K-9 / Thunderbird
        "com.readdle.spark",                  // Spark
        "com.my.mail",                        // myMail
        "ru.yandex.mail",                     // Yandex Mail
        "com.zoho.mail"                       // Zoho
    )

    private val MESSAGING_PACKAGES = setOf(
        "com.google.android.apps.messaging",  // Google Messages
        "com.samsung.android.messaging"       // Samsung Messages
    )
}
