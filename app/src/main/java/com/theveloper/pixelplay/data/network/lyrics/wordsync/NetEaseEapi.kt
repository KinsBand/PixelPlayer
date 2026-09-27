package com.theveloper.pixelplay.data.network.lyrics.wordsync

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * NetEase "eapi" request signing, used only for the word-timed lyric endpoint
 * (`/api/song/lyric/v1`) when the plain `/api/` form does not return YRC.
 *
 * params = HEX_UPPER( AES-128-ECB-PKCS7( "$path-36cd479b6b5-$json-36cd479b6b5-$md5" ) )
 * where md5 = md5_hex_lower("nobody${path}use${json}md5forencrypt").
 */
internal object NetEaseEapi {
    private val KEY = "e82ckenh8dichen8".toByteArray(Charsets.US_ASCII)
    private const val SEPARATOR = "-36cd479b6b5-"

    fun params(path: String, json: String): String {
        val digest = md5Hex("nobody${path}use${json}md5forencrypt")
        val message = "$path$SEPARATOR$json$SEPARATOR$digest"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY, "AES"))
        return cipher.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    /** Inverse of [params], for tests. */
    internal fun decodeParamsForTest(hex: String): String {
        val bytes = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES"))
        return cipher.doFinal(bytes).toString(Charsets.UTF_8)
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
