package com.theveloper.pixelplay.data.ytmusic

import org.json.JSONObject

/** Parsed locally; never log this object or the imported headers. */
class YouTubeMusicSession(val cookie: String, val account: String) {
    val sapisid: String
        get() {
            val values = cookie.split(';').mapNotNull {
                val pair = it.trim().split('=', limit = 2)
                if (pair.size == 2) pair[0] to pair[1] else null
            }.toMap()
            return values["SAPISID"]?.takeIf { it.isNotBlank() }
                ?: values["__Secure-3PAPISID"]?.takeIf { it.isNotBlank() }
                ?: values["__Secure-1PAPISID"].orEmpty()
        }

    companion object {
        fun parse(input: String, account: String = "0"): YouTubeMusicSession {
            require(input.length < 65536) { "The browser headers are too large." }
            val text = input.trim()
            val headers = when {
                text.startsWith("{") -> {
                    val json = try { JSONObject(text) } catch (_: Exception) {
                        throw IllegalArgumentException("Paste a Cookie value, request headers, or browser.json headers.")
                    }
                    json.keys().asSequence().associate { it.lowercase(java.util.Locale.ROOT) to json.optString(it) }
                }
                text.lineSequence().any { it.trim().startsWith("cookie:", ignoreCase = true) } ->
                    text.lineSequence().mapNotNull { line ->
                        val split = line.indexOf(':')
                        if (split > 0) line.substring(0, split).trim().lowercase(java.util.Locale.ROOT) to line.substring(split + 1).trim() else null
                    }.toMap()
                else -> mapOf("cookie" to text)
            }
            val cookie = headers["cookie"].orEmpty().trim()
            require(cookie.isNotBlank() && cookie.all { it.code in 32..126 }) { "Paste the Cookie request header from music.youtube.com." }
            val index = (headers["x-goog-authuser"] ?: account).trim()
            require(index.toIntOrNull()?.let { it in 0..99 } == true) { "Account index must be between 0 and 99." }
            return YouTubeMusicSession(cookie, index).also {
                require(it.sapisid.isNotBlank()) { "The browser session is missing SAPISID. Sign in to music.youtube.com first." }
            }
        }
    }
}
