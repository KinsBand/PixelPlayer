package com.theveloper.pixelplay.data.spotify.web

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The 6-digit code the Spotify web player sends with every token request.
 *
 * The web player ships a versioned secret string. Each character is XOR-ed with
 * `(index % 33) + 9`, the resulting numbers are joined as decimal text, and that text is the
 * HMAC-SHA1 key of a standard 30-second TOTP.
 */
object SpotifyTotp {
    /** Secret the web player has used since January 2026 (version 61), as character codes. */
    const val BUNDLED_VERSION = 61
    val BUNDLED_SECRET: String = intArrayOf(
        44, 55, 47, 42, 70, 40, 34, 114, 76, 74, 50, 111, 120, 97, 75, 76, 94, 102, 43, 69, 49, 120, 118, 80, 64, 78
    ).joinToString("") { it.toChar().toString() }

    internal fun key(secret: String): ByteArray =
        secret.mapIndexed { i, c -> (c.code xor ((i % 33) + 9)).toString() }.joinToString("").toByteArray(Charsets.US_ASCII)

    fun code(secret: String, epochSeconds: Long): String {
        val counter = epochSeconds / 30
        val msg = ByteArray(8) { i -> (counter ushr (8 * (7 - i))).toByte() }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key(secret), "HmacSHA1")) }.doFinal(msg)
        val offset = mac[mac.size - 1].toInt() and 0x0F
        val binary = ((mac[offset].toInt() and 0x7F) shl 24) or
            ((mac[offset + 1].toInt() and 0xFF) shl 16) or
            ((mac[offset + 2].toInt() and 0xFF) shl 8) or
            (mac[offset + 3].toInt() and 0xFF)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }
}
