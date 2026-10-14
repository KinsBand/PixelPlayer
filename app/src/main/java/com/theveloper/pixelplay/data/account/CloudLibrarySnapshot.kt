package com.theveloper.pixelplay.data.account

import android.content.Context
import android.net.Uri
import android.os.Build
import com.theveloper.pixelplay.data.accounts.AccountVault
import com.theveloper.pixelplay.data.backup.BackupManager
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.model.RestoreResult
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.database.CloudSongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What a save put in the archive (shown on the Profile screen). */
data class SnapshotSummary(
    val sections: Int,
    val onlineSongs: Int,
    val preferenceFiles: Int,
    val includesSessions: Boolean
)

/** What a restore brought back. */
data class RestoreSummary(
    val restoredSections: Int,
    val failedSections: List<String>,
    val onlineSongs: Int,
    val preferenceFiles: Int,
    val sessionsRestored: Boolean,
    /** Sessions were in the archive but couldn't be decrypted (password changed since). */
    val sessionsLocked: Boolean
)

/**
 * Packs everything worth keeping into one zip for the PixelPlay account, and unpacks it:
 *
 *  - `library.pxpl`   — the regular backup (all [BackupSection]s: playlists, liked songs,
 *                        settings, lyrics, history, stats, equalizer, transitions…)
 *  - `cloud_songs.json` — metadata for online songs (YouTube Music / Spotify / catalogue) so liked
 *                        and playlisted online songs resolve on a fresh install
 *  - `prefs.json`     — the app's other small stores (Practice, radio favourites, Your Music
 *                        filters, video choices, tabs…); device-only and secret stores excluded
 *  - `sessions.enc`   — connected-service logins, encrypted with the account password key
 */
@Singleton
class CloudLibrarySnapshot @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val cloudSongDao: CloudSongDao,
    private val accountVault: AccountVault
) {
    private val workDir: File get() = File(context.cacheDir, "cloud_account").apply { mkdirs() }

    suspend fun build(target: File, sessionKey: ByteArray?): SnapshotSummary = withContext(Dispatchers.IO) {
        val pxpl = File(workDir, "library.pxpl").apply { delete() }
        try {
            val sections = BackupSection.entries.toSet()
            backupManager.export(Uri.fromFile(pxpl), sections) { }.getOrThrow()

            // Online songs without the device-specific download fields.
            val cloudSongs = cloudSongDao.getAllOnce().map {
                it.copy(isDownloaded = false, localSongId = null, localFilePath = null, localContentUri = null)
            }
            val cloudSongsJson = JSONArray().apply { cloudSongs.forEach { put(it.toJson()) } }

            val prefs = exportPreferences()
            val sessions = sessionKey?.let { key ->
                accountVault.snapshot().takeIf { it.isNotEmpty() }?.let { values ->
                    SessionCrypto.encrypt(key, JSONObject(values as Map<*, *>).toString().toByteArray(Charsets.UTF_8))
                }
            }

            val manifest = JSONObject()
                .put("format", 1)
                .put("createdAt", System.currentTimeMillis())
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("sections", JSONArray(sections.map { it.key }))
                .put("onlineSongs", cloudSongs.size)
                .put("preferenceFiles", prefs.length())
                .put("sessions", sessions != null)

            target.parentFile?.mkdirs()
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                zip.putEntry(MANIFEST, manifest.toString().toByteArray())
                zip.putNextEntry(ZipEntry(LIBRARY))
                pxpl.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                zip.putEntry(CLOUD_SONGS, cloudSongsJson.toString().toByteArray())
                zip.putEntry(PREFS, prefs.toString().toByteArray())
                sessions?.let { zip.putEntry(SESSIONS, it) }
            }
            SnapshotSummary(
                sections = sections.size,
                onlineSongs = cloudSongs.size,
                preferenceFiles = prefs.length(),
                includesSessions = sessions != null
            )
        } finally {
            pxpl.delete()
        }
    }

    suspend fun apply(archive: File, sessionKey: ByteArray?): RestoreSummary = withContext(Dispatchers.IO) {
        val dir = File(workDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            unzip(archive, dir)

            // 1. Online songs first, so restored likes / playlists can resolve them.
            var onlineSongs = 0
            File(dir, CLOUD_SONGS).takeIf { it.isFile }?.let { file ->
                val array = JSONArray(file.readText())
                for (i in 0 until array.length()) {
                    val entity = array.optJSONObject(i)?.toCloudSong() ?: continue
                    // Never replace a local row (it may point at a downloaded file).
                    if (cloudSongDao.getById(entity.id) == null) {
                        cloudSongDao.insertOrReplace(entity)
                        onlineSongs++
                    }
                }
            }

            // 2. The regular backup.
            var restoredSections = 0
            val failed = ArrayList<String>()
            File(dir, LIBRARY).takeIf { it.isFile }?.let { pxpl ->
                val uri = Uri.fromFile(pxpl)
                val plan = backupManager.inspectBackup(uri).getOrThrow()
                val fullPlan = plan.copy(selectedModules = plan.availableModules)
                when (val result = backupManager.restore(uri, fullPlan) { }) {
                    is RestoreResult.Success -> restoredSections = fullPlan.selectedModules.size
                    is RestoreResult.PartialFailure -> {
                        restoredSections = result.succeeded.size
                        failed += result.failed.keys.map { it.label }
                    }
                    is RestoreResult.TotalFailure -> throw IllegalStateException(result.error)
                }
                // The temp file shouldn't show up in Settings → Backup's history.
                runCatching { backupManager.removeBackupHistoryEntry(uri.toString()) }
            }

            // 3. Small stores.
            val prefsCount = File(dir, PREFS).takeIf { it.isFile }?.let { importPreferences(JSONObject(it.readText())) } ?: 0

            // 4. Connected-service logins.
            var sessionsRestored = false
            var sessionsLocked = false
            File(dir, SESSIONS).takeIf { it.isFile }?.let { file ->
                if (sessionKey == null) {
                    sessionsLocked = true
                } else {
                    try {
                        val plain = SessionCrypto.decrypt(sessionKey, file.readBytes())
                        val obj = JSONObject(String(plain, Charsets.UTF_8))
                        val values = obj.keys().asSequence().associateWith { obj.optString(it) }
                        accountVault.restoreAll(values)
                        sessionsRestored = values.isNotEmpty()
                    } catch (_: Exception) {
                        sessionsLocked = true
                    }
                }
            }

            RestoreSummary(
                restoredSections = restoredSections,
                failedSections = failed,
                onlineSongs = onlineSongs,
                preferenceFiles = prefsCount,
                sessionsRestored = sessionsRestored,
                sessionsLocked = sessionsLocked
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── Shared preferences ────────────────────────────────────────────────────────────────

    private fun preferenceNames(): List<String> {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        return dir.listFiles { file -> file.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }
            ?.filterNot { name -> PREFS_DENYLIST.any { name.startsWith(it, ignoreCase = true) } }
            .orEmpty()
    }

    private fun exportPreferences(): JSONObject {
        val out = JSONObject()
        preferenceNames().forEach { name ->
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            // EncryptedSharedPreferences files hold secrets (and only decrypt on this device).
            if (all.keys.any { it.startsWith("__androidx_security_crypto") }) return@forEach
            val values = JSONObject()
            all.forEach { (key, value) ->
                val typed = when (value) {
                    is String -> JSONObject().put("t", "s").put("v", value)
                    is Boolean -> JSONObject().put("t", "b").put("v", value)
                    is Int -> JSONObject().put("t", "i").put("v", value)
                    is Long -> JSONObject().put("t", "l").put("v", value)
                    is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                    is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.filterIsInstance<String>()))
                    else -> null
                }
                if (typed != null) values.put(key, typed)
            }
            if (values.length() > 0) out.put(name, values)
        }
        return out
    }

    private fun importPreferences(data: JSONObject): Int {
        var files = 0
        data.keys().forEach { name ->
            if (PREFS_DENYLIST.any { name.startsWith(it, ignoreCase = true) }) return@forEach
            val values = data.optJSONObject(name) ?: return@forEach
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            values.keys().forEach { key ->
                val typed = values.optJSONObject(key) ?: return@forEach
                when (typed.optString("t")) {
                    "s" -> editor.putString(key, typed.optString("v"))
                    "b" -> editor.putBoolean(key, typed.optBoolean("v"))
                    "i" -> editor.putInt(key, typed.optInt("v"))
                    "l" -> editor.putLong(key, typed.optLong("v"))
                    "f" -> editor.putFloat(key, typed.optDouble("v").toFloat())
                    "set" -> {
                        val array = typed.optJSONArray("v") ?: JSONArray()
                        editor.putStringSet(key, (0 until array.length()).map { array.optString(it) }.toSet())
                    }
                }
            }
            if (editor.commit()) files++
        }
        return files
    }

    // ── Zip / JSON helpers ────────────────────────────────────────────────────────────────

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun unzip(archive: File, dir: File) {
        val allowed = setOf(MANIFEST, LIBRARY, CLOUD_SONGS, PREFS, SESSIONS)
        var total = 0L
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // Flat archive of known names only (no path traversal, no surprises).
                if (entry.isDirectory || entry.name !in allowed) continue
                File(dir, entry.name).outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= MAX_UNPACKED_BYTES) { "Account archive is too large" }
                        out.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private fun CloudSongEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("duration", duration)
        .put("thumbnailUrl", thumbnailUrl)
        .put("youtubeId", youtubeId)
        .put("sourceType", sourceType)
        .put("contentUriString", contentUriString)
        .put("dateAdded", dateAdded)

    private fun JSONObject.toCloudSong(): CloudSongEntity? {
        val id = optString("id").ifBlank { return null }
        return CloudSongEntity(
            id = id,
            title = optString("title"),
            artist = optString("artist"),
            album = optString("album").takeIf { has("album") && !isNull("album") },
            duration = optLong("duration"),
            thumbnailUrl = optString("thumbnailUrl").takeIf { has("thumbnailUrl") && !isNull("thumbnailUrl") },
            youtubeId = optString("youtubeId").takeIf { has("youtubeId") && !isNull("youtubeId") },
            sourceType = optString("sourceType").ifBlank { "youtube" },
            contentUriString = optString("contentUriString"),
            dateAdded = optLong("dateAdded", System.currentTimeMillis())
        )
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val LIBRARY = "library.pxpl"
        const val CLOUD_SONGS = "cloud_songs.json"
        const val PREFS = "prefs.json"
        const val SESSIONS = "sessions.enc"
        const val MAX_UNPACKED_BYTES = 300L * 1024 * 1024

        /**
         * Stores that must not travel: secrets (sessions travel encrypted separately), this
         * account's own state, per-device hardware state, framework bookkeeping and crash logs.
         */
        val PREFS_DENYLIST = listOf(
            "connected_accounts",
            "pixelplay_cloud_account",
            "gdrive",
            "innertube_client",
            "bluetooth",
            "crash",
            "androidx.",
            "__androidx",
            "WorkManager",
            "com.google.",
            "FirebaseHeartBeat",
            "admob",
            "_has_set_default_values"
        )
    }
}
