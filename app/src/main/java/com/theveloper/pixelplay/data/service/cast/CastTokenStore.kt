package com.theveloper.pixelplay.data.service.cast

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CastResource {
    data class Song(val songId: String) : CastResource
    data class Artwork(val songId: String) : CastResource
}

interface CastTokenStore {
    fun generateToken(resource: CastResource, expiryMs: Long = 3600_000L): String
    fun validateToken(token: String): CastResource?
    fun revokeToken(token: String)
    fun purgeExpired()
}

@Singleton
class CastTokenStoreImpl @Inject constructor() : CastTokenStore {
    private val secureRandom = SecureRandom()
    private val tokens = ConcurrentHashMap<String, TokenEntry>()

    private data class TokenEntry(
        val resource: CastResource,
        val expiryTimestamp: Long
    )

    override fun generateToken(resource: CastResource, expiryMs: Long): String {
        val bytes = ByteArray(16) // 128-bit
        secureRandom.nextBytes(bytes)
        val token = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val expiryTimestamp = System.currentTimeMillis() + expiryMs
        tokens[token] = TokenEntry(resource, expiryTimestamp)
        return token
    }

    override fun validateToken(token: String): CastResource? {
        // Constant-time validation of the token string to prevent timing attacks
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        var matchedEntry: TokenEntry? = null
        var matchedKey: String? = null

        for ((key, entry) in tokens) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            if (MessageDigest.isEqual(keyBytes, tokenBytes)) {
                matchedEntry = entry
                matchedKey = key
            }
        }

        if (matchedEntry == null || matchedKey == null) {
            return null
        }

        if (System.currentTimeMillis() > matchedEntry.expiryTimestamp) {
            tokens.remove(matchedKey)
            return null
        }
        return matchedEntry.resource
    }

    override fun revokeToken(token: String) {
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        var keyToRemove: String? = null
        for (key in tokens.keys) {
            if (MessageDigest.isEqual(key.toByteArray(Charsets.UTF_8), tokenBytes)) {
                keyToRemove = key
                break
            }
        }
        if (keyToRemove != null) {
            tokens.remove(keyToRemove)
        }
    }

    override fun purgeExpired() {
        val now = System.currentTimeMillis()
        val iterator = tokens.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now > entry.value.expiryTimestamp) {
                iterator.remove()
            }
        }
    }
}
