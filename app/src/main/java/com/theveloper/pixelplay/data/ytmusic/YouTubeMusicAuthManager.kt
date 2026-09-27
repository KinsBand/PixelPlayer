package com.theveloper.pixelplay.data.ytmusic

import com.theveloper.pixelplay.data.accounts.AccountVault
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicAuthManager @Inject constructor(
    private val vault: AccountVault,
    private val preferencesRepository: UserPreferencesRepository
) {
    val isLoggedInFlow = vault.revision.map { cookieHeader.isNotBlank() }.distinctUntilChanged()
    val cookieHeader get() = vault.get("yt.cookie")
    val accountIndex get() = vault.get("yt.account").ifBlank { "0" }
    val sapisid get() = cookieHeader.split(';').map { it.trim().split('=', limit = 2) }
        .firstOrNull { it.size == 2 && it[0] == "SAPISID" }?.get(1)?.takeIf { it.isNotBlank() }
        ?: cookieHeader.split(';').map { it.trim().split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "__Secure-3PAPISID" }?.get(1)?.takeIf { it.isNotBlank() }
        ?: cookieHeader.split(';').map { it.trim().split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "__Secure-1PAPISID" }?.get(1).orEmpty()
    suspend fun connect(cookie: String, account: String = "0", validate: suspend (YouTubeMusicSession) -> Unit) = withContext(Dispatchers.IO) {
        val session = YouTubeMusicSession.parse(cookie, account)
        validate(session)
        ensureActive()
        vault.put("yt.cookie" to session.cookie, "yt.account" to session.account)
        preferencesRepository.clearYtMusicCookies()
    }
    suspend fun logout() = withContext(Dispatchers.IO) { vault.clear("yt."); preferencesRepository.clearYtMusicCookies() }
    fun generateSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
        val timestamp = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$timestamp $sapisid $origin".toByteArray())
        return "${timestamp}_" + digest.joinToString("") { "%02x".format(it) }
    }
}

