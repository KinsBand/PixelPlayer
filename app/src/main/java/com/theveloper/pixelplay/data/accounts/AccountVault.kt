package com.theveloper.pixelplay.data.accounts

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Keystore-backed credentials. Never fall back to plaintext on a keystore failure. */
@Singleton
@Suppress("DEPRECATION")
class AccountVault @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "connected_accounts",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    val revision = MutableStateFlow(0L)
    fun get(key: String): String = prefs.getString(key, "").orEmpty()
    @Synchronized fun put(vararg values: Pair<String, String>) {
        check(prefs.edit().apply { values.forEach { (k, v) -> putString(k, v) } }.commit())
        revision.value++
    }
    @Synchronized fun clear(prefix: String) {
        check(prefs.edit().apply { prefs.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) } }.commit())
        revision.value++
    }
}
