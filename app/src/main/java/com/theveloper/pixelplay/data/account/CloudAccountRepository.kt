package com.theveloper.pixelplay.data.account

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.data.worker.CloudAccountSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The PixelPlay account as the Profile screen shows it. */
data class CloudAccountState(
    val isConfigured: Boolean = false,
    val serverUrl: String = "",
    val email: String? = null,
    val userId: String? = null,
    val autoSave: Boolean = true,
    /** Epoch millis of this device's last successful save, or null. */
    val lastSavedAt: Long? = null,
    /** What the last save contained. */
    val lastSummary: SnapshotSummary? = null,
    /** Server timestamp of the stored copy (ISO-8601), when known. */
    val cloudUpdatedAt: String? = null,
    val cloudSizeBytes: Long? = null,
    /** False when the sign-in predates session encryption (sessions won't be included). */
    val canEncryptSessions: Boolean = false
) {
    val isSignedIn: Boolean get() = email != null && userId != null
}

/**
 * Email + password account backed by Supabase. Signing in keeps a refreshable session and a
 * password-derived key (both in keystore-encrypted prefs) so saves can run in the background
 * without asking for the password again.
 */
@Singleton
@Suppress("DEPRECATION")
class CloudAccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val snapshot: CloudLibrarySnapshot
) {
    private val plain = context.getSharedPreferences("pixelplay_cloud_account", Context.MODE_PRIVATE)
    private val secure by lazy {
        EncryptedSharedPreferences.create(
            context,
            "pixelplay_cloud_account_secure",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val mutex = Mutex()

    private val _state = MutableStateFlow(CloudAccountState())
    val state: StateFlow<CloudAccountState> = _state.asStateFlow()

    init {
        _state.value = readState()
    }

    // ── Server settings ───────────────────────────────────────────────────────────────────

    val serverUrl: String get() = plain.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: CloudAccountConfig.DEFAULT_URL
    private val anonKey: String get() = plain.getString(KEY_ANON, null)?.takeIf { it.isNotBlank() } ?: CloudAccountConfig.DEFAULT_ANON_KEY
    val hasCustomServer: Boolean get() = !plain.getString(KEY_URL, null).isNullOrBlank()

    /** A blank [anonKey] keeps the current one (the field is never pre-filled). */
    fun setServer(url: String, anonKey: String) {
        plain.edit()
            .putString(KEY_URL, url.trim().trimEnd('/'))
            .apply {
                if (anonKey.isNotBlank()) putString(KEY_ANON, anonKey.trim())
                else if (plain.getString(KEY_ANON, null).isNullOrBlank()) putString(KEY_ANON, this@CloudAccountRepository.anonKey)
            }
            .apply()
        refreshState()
    }

    private fun client() = SupabaseClient(serverUrl, anonKey, httpClient)

    // ── Sign in / out ─────────────────────────────────────────────────────────────────────

    sealed interface SignUpResult {
        data object SignedIn : SignUpResult
        data object ConfirmEmail : SignUpResult
    }

    suspend fun signUp(email: String, password: String): SignUpResult = mutex.withLock {
        val session = client().signUp(email.trim(), password) ?: return@withLock SignUpResult.ConfirmEmail
        storeSignIn(session, password)
        SignUpResult.SignedIn
    }

    suspend fun signIn(email: String, password: String) = mutex.withLock {
        val session = client().signIn(email.trim(), password)
        storeSignIn(session, password)
    }

    suspend fun sendPasswordReset(email: String) = client().sendPasswordReset(email.trim())

    suspend fun signOut() = mutex.withLock {
        loadSession()?.let { runCatching { client().signOut(it) } }
        secure.edit().clear().commit()
        plain.edit().remove(KEY_LAST_SAVED).remove(KEY_LAST_SUMMARY).apply()
        CloudAccountSyncWorker.cancel(context)
        refreshState()
    }

    private suspend fun storeSignIn(session: SupabaseSession, password: String) {
        val key = withContext(Dispatchers.Default) { SessionCrypto.deriveKey(password, session.userId) }
        saveSession(session)
        secure.edit().putString(KEY_SESSION_KEY, Base64.encodeToString(key, Base64.NO_WRAP)).commit()
        if (plain.getBoolean(KEY_AUTO_SAVE, true)) CloudAccountSyncWorker.schedule(context)
        refreshState()
        runCatching { refreshCloudInfoLocked() }
    }

    // ── Save / restore ────────────────────────────────────────────────────────────────────

    /** Packs the library and uploads it, replacing the stored copy. */
    suspend fun saveNow(): SnapshotSummary = mutex.withLock {
        val session = validSession()
        val archive = File(context.cacheDir, "cloud_account/upload.zip")
        try {
            val summary = snapshot.build(archive, sessionKey())
            client().upload(session, CloudAccountConfig.BUCKET, objectPath(session), archive)
            plain.edit()
                .putLong(KEY_LAST_SAVED, System.currentTimeMillis())
                .putString(KEY_LAST_SUMMARY, "${summary.sections}|${summary.onlineSongs}|${summary.preferenceFiles}|${summary.includesSessions}")
                .apply()
            refreshState()
            runCatching { refreshCloudInfoLocked() }
            summary
        } finally {
            archive.delete()
        }
    }

    /** Downloads the stored copy and restores it. Null when the account has nothing saved yet. */
    suspend fun restore(): RestoreSummary? = mutex.withLock {
        val session = validSession()
        val archive = File(context.cacheDir, "cloud_account/download.zip")
        try {
            if (!client().download(session, CloudAccountConfig.BUCKET, objectPath(session), archive)) return@withLock null
            snapshot.apply(archive, sessionKey())
        } finally {
            archive.delete()
        }
    }

    /** Removes the stored copy from the server (the account itself stays). */
    suspend fun deleteCloudCopy() = mutex.withLock {
        val session = validSession()
        client().delete(session, CloudAccountConfig.BUCKET, objectPath(session))
        plain.edit().remove(KEY_LAST_SAVED).remove(KEY_LAST_SUMMARY).apply()
        refreshState()
        _state.update { it.copy(cloudUpdatedAt = null, cloudSizeBytes = null) }
    }

    suspend fun refreshCloudInfo() = mutex.withLock { refreshCloudInfoLocked() }

    private suspend fun refreshCloudInfoLocked() {
        val session = validSession()
        val info = client().info(session, CloudAccountConfig.BUCKET, session.userId, CloudAccountConfig.ARCHIVE_NAME)
        _state.update { it.copy(cloudUpdatedAt = info?.updatedAt, cloudSizeBytes = info?.sizeBytes) }
    }

    fun setAutoSave(enabled: Boolean) {
        plain.edit().putBoolean(KEY_AUTO_SAVE, enabled).apply()
        if (enabled && _state.value.isSignedIn) CloudAccountSyncWorker.schedule(context) else CloudAccountSyncWorker.cancel(context)
        refreshState()
    }

    /** Called by the background worker: saves only when signed in with auto-save on. */
    suspend fun autoSaveIfEnabled(): Boolean {
        val current = _state.value
        if (!current.isSignedIn || !current.autoSave || !current.isConfigured) return false
        saveNow()
        return true
    }

    // ── Session plumbing ──────────────────────────────────────────────────────────────────

    private fun objectPath(session: SupabaseSession) = "${session.userId}/${CloudAccountConfig.ARCHIVE_NAME}"

    private fun sessionKey(): ByteArray? =
        secure.getString(KEY_SESSION_KEY, null)?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }

    /** The stored session, refreshed when it's about to expire. Throws when signed out. */
    private suspend fun validSession(): SupabaseSession {
        val session = loadSession() ?: throw IllegalStateException("Sign in to your PixelPlay account first")
        if (!session.isExpiringSoon) return session
        return try {
            client().refresh(session).also { saveSession(it) }
        } catch (e: SupabaseException) {
            if (e.status in 400..499) {
                // Refresh token revoked or expired: the user has to sign in again.
                secure.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).commit()
                refreshState()
                throw IllegalStateException("Your sign-in expired. Sign in again", e)
            }
            throw e
        }
    }

    private fun loadSession(): SupabaseSession? {
        val access = secure.getString(KEY_ACCESS, null) ?: return null
        val refresh = secure.getString(KEY_REFRESH, null) ?: return null
        val userId = secure.getString(KEY_USER_ID, null) ?: return null
        val email = secure.getString(KEY_EMAIL, null) ?: return null
        return SupabaseSession(access, refresh, secure.getLong(KEY_EXPIRES, 0L), userId, email)
    }

    private fun saveSession(session: SupabaseSession) {
        secure.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES, session.expiresAt)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .commit()
    }

    private fun refreshState() {
        val fresh = readState()
        _state.update { fresh.copy(cloudUpdatedAt = it.cloudUpdatedAt, cloudSizeBytes = it.cloudSizeBytes) }
    }

    private fun readState(): CloudAccountState {
        val session = runCatching { loadSession() }.getOrNull()
        val summary = plain.getString(KEY_LAST_SUMMARY, null)?.split('|')?.takeIf { it.size == 4 }?.let {
            SnapshotSummary(it[0].toIntOrNull() ?: 0, it[1].toIntOrNull() ?: 0, it[2].toIntOrNull() ?: 0, it[3] == "true")
        }
        return CloudAccountState(
            isConfigured = client().isConfigured,
            serverUrl = serverUrl,
            email = session?.email,
            userId = session?.userId,
            autoSave = plain.getBoolean(KEY_AUTO_SAVE, true),
            lastSavedAt = plain.getLong(KEY_LAST_SAVED, 0L).takeIf { it > 0 },
            lastSummary = summary,
            canEncryptSessions = runCatching { sessionKey() != null }.getOrDefault(false)
        )
    }

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_ANON = "server_anon_key"
        const val KEY_AUTO_SAVE = "auto_save"
        const val KEY_LAST_SAVED = "last_saved_at"
        const val KEY_LAST_SUMMARY = "last_summary"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_SESSION_KEY = "session_key"
    }
}
