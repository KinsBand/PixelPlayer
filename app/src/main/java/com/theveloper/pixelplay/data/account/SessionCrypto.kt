package com.theveloper.pixelplay.data.account

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for connected-service sessions (Spotify / YouTube Music / Apple Music
 * cookies and tokens) before they leave the phone. The key comes from the account password, so
 * the server only ever stores ciphertext; changing the password means saving again.
 *
 * Blob layout: "PXS1" | 12-byte IV | AES-256-GCM ciphertext+tag.
 */
object SessionCrypto {
    private val MAGIC = "PXS1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Slow on purpose (~0.3–1s): run off the main thread, once per sign-in. */
    fun deriveKey(password: String, userId: String): ByteArray {
        // Unique per user; the user id is fixed for the account's lifetime.
        val salt = "pixelplay.sessions.v1:$userId".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return MAGIC + iv + cipher.doFinal(plain)
    }

    /** Throws if the key is wrong (e.g. the password changed since the sessions were saved). */
    fun decrypt(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size > MAGIC.size + IV_BYTES && blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Not an encrypted session blob"
        }
        val iv = blob.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(blob, MAGIC.size + IV_BYTES, blob.size - MAGIC.size - IV_BYTES)
    }
}
