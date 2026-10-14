package com.theveloper.pixelplay.data.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A signed-in Supabase session. */
data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch millis when [accessToken] stops working. */
    val expiresAt: Long,
    val userId: String,
    val email: String
) {
    val isExpiringSoon: Boolean get() = System.currentTimeMillis() > expiresAt - 60_000L
}

/** What the server said about the stored archive. */
data class CloudObjectInfo(val updatedAt: String?, val sizeBytes: Long?)

/** An error the server explained (shown to the user as-is). */
class SupabaseException(message: String, val status: Int, val code: String? = null) : IOException(message) {
    /** The project has no 'pixelplay' storage bucket yet. */
    val isBucketMissing: Boolean get() = code == BUCKET_MISSING
    companion object { const val BUCKET_MISSING = "bucket_missing" }
}

/**
 * Minimal Supabase REST client: GoTrue auth (email + password) and Storage objects. Plain OkHttp
 * so no SDK or Gradle plugin is needed.
 */
class SupabaseClient(
    private val baseUrl: String,
    private val anonKey: String,
    httpClient: OkHttpClient
) {
    // Archives can be several MB on slow mobile links.
    private val http = httpClient.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val base = baseUrl.trim().trimEnd('/')

    val isConfigured: Boolean get() = base.startsWith("https://") && anonKey.isNotBlank()

    // ── Auth ──────────────────────────────────────────────────────────────────────────────

    /**
     * Creates the account. Returns the session, or null when the project requires the user to
     * confirm their email first.
     */
    suspend fun signUp(email: String, password: String): SupabaseSession? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password)
        val response = execute(authRequest("/auth/v1/signup").post(body.toString().toRequestBody(json)))
        if (response.has("access_token")) parseSession(response, email) else null
    }

    suspend fun signIn(email: String, password: String): SupabaseSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password)
        parseSession(execute(authRequest("/auth/v1/token?grant_type=password").post(body.toString().toRequestBody(json))), email)
    }

    suspend fun refresh(session: SupabaseSession): SupabaseSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("refresh_token", session.refreshToken)
        parseSession(
            execute(authRequest("/auth/v1/token?grant_type=refresh_token").post(body.toString().toRequestBody(json))),
            session.email
        )
    }

    suspend fun signOut(session: SupabaseSession) = withContext(Dispatchers.IO) {
        runCatching {
            execute(
                authRequest("/auth/v1/logout")
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .post(ByteArray(0).toRequestBody(null))
            )
        }
        Unit
    }

    suspend fun sendPasswordReset(email: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email)
        execute(authRequest("/auth/v1/recover").post(body.toString().toRequestBody(json)))
        Unit
    }

    // ── Storage ───────────────────────────────────────────────────────────────────────────

    suspend fun upload(session: SupabaseSession, bucket: String, path: String, file: File) = withContext(Dispatchers.IO) {
        fun attempt() {
            val request = storageRequest(session, "/storage/v1/object/$bucket/$path")
                .header("x-upsert", "true")
                .header("cache-control", "no-cache")
                .post(file.asRequestBody("application/zip".toMediaType()))
            execute(request)
        }
        try {
            attempt()
        } catch (e: SupabaseException) {
            // A project without the bucket: try to create it once (works when the project lets
            // signed-in users do that), then retry. Otherwise the message says what to run.
            if (!e.isBucketMissing || !createBucket(session, bucket)) throw e
            attempt()
        }
        Unit
    }

    /** Creates a private bucket. True when it now exists (created, or already there). */
    private fun createBucket(session: SupabaseSession, bucket: String): Boolean = try {
        val body = JSONObject().put("id", bucket).put("name", bucket).put("public", false)
        http.newCall(
            storageRequest(session, "/storage/v1/bucket")
                .post(body.toString().toRequestBody(json)).build()
        ).execute().use { it.isSuccessful || it.code == 409 }
    } catch (_: IOException) {
        false
    }

    /** Downloads to [target]; returns false when nothing is stored there yet. */
    suspend fun download(session: SupabaseSession, bucket: String, path: String, target: File): Boolean = withContext(Dispatchers.IO) {
        val request = storageRequest(session, "/storage/v1/object/authenticated/$bucket/$path").get().build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404 || response.code == 400 && response.peekBody(2048).string().let {
                    it.contains("not_found", true) || it.contains("Bucket not found", true)
                }) {
                return@withContext false
            }
            if (!response.isSuccessful) throw errorFrom(response)
            val body = response.body ?: return@withContext false
            target.parentFile?.mkdirs()
            target.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            true
        }
    }

    /** Last-modified time and size of an object, or null when it doesn't exist. */
    suspend fun info(session: SupabaseSession, bucket: String, folder: String, name: String): CloudObjectInfo? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("prefix", folder)
                .put("search", name)
                .put("limit", 10)
                .put("offset", 0)
            val request = storageRequest(session, "/storage/v1/object/list/$bucket")
                .post(body.toString().toRequestBody(json))
            val array = try {
                executeArray(request)
            } catch (e: SupabaseException) {
                if (e.isBucketMissing) return@withContext null else throw e
            }
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .firstOrNull { it.optString("name") == name }
                ?.let { item ->
                    CloudObjectInfo(
                        updatedAt = item.optString("updated_at").ifBlank { item.optString("created_at") }.ifBlank { null },
                        sizeBytes = item.optJSONObject("metadata")?.optLong("size", -1L)?.takeIf { it >= 0 }
                    )
                }
        }

    suspend fun delete(session: SupabaseSession, bucket: String, path: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("prefixes", JSONArray().put(path))
        execute(storageRequest(session, "/storage/v1/object/$bucket").delete(body.toString().toRequestBody(json)))
        Unit
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────────────────

    private fun authRequest(path: String): Request.Builder {
        check(isConfigured) { "Account server isn't set up yet (Settings → Profile → Server)" }
        return Request.Builder()
            .url(base + path)
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
    }

    private fun storageRequest(session: SupabaseSession, path: String): Request.Builder {
        check(isConfigured) { "Account server isn't set up yet (Settings → Profile → Server)" }
        return Request.Builder()
            .url(base + path)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
    }

    private fun execute(builder: Request.Builder): JSONObject =
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFrom(response.code, text)
            if (text.isBlank() || !text.trimStart().startsWith("{")) JSONObject() else JSONObject(text)
        }

    private fun executeArray(builder: Request.Builder): JSONArray =
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFrom(response.code, text)
            if (text.trimStart().startsWith("[")) JSONArray(text) else JSONArray()
        }

    private fun errorFrom(response: Response): SupabaseException =
        errorFrom(response.code, response.body?.string().orEmpty())

    private fun errorFrom(status: Int, text: String): SupabaseException {
        val obj = runCatching { JSONObject(text) }.getOrNull()
        val code = obj?.optString("error_code")?.ifBlank { null } ?: obj?.optString("error")?.ifBlank { null }
        val message = listOf("msg", "error_description", "message", "error")
            .firstNotNullOfOrNull { key -> obj?.optString(key)?.takeIf { it.isNotBlank() } }
            ?: "Server error ($status)"
        val missingBucket = message.contains("Bucket not found", true)
        return SupabaseException(
            friendly(message, code, status), status,
            if (missingBucket) SupabaseException.BUCKET_MISSING else code
        )
    }

    private fun friendly(message: String, code: String?, status: Int): String = when {
        code == "invalid_credentials" || message.contains("Invalid login credentials", true) ->
            "Wrong email or password"
        code == "email_not_confirmed" || message.contains("Email not confirmed", true) ->
            "Confirm your email first (check your inbox), then sign in"
        code == "user_already_exists" || message.contains("already registered", true) ->
            "There's already an account with that email. Sign in instead"
        code == "weak_password" || message.contains("Password should be", true) -> message
        message.contains("Bucket not found", true) ->
            "Server storage isn't set up yet. Open Profile → Advanced → Copy setup SQL and run it once in Supabase's SQL editor"
        status == 403 && message.contains("row-level security", true) ->
            "Server storage policies are missing. Open Profile → Advanced → Copy setup SQL and run it once in Supabase's SQL editor"
        status == 429 -> "Too many attempts. Wait a minute and try again"
        else -> message
    }

    private fun parseSession(obj: JSONObject, fallbackEmail: String): SupabaseSession {
        val user = obj.optJSONObject("user") ?: JSONObject()
        val expiresIn = obj.optLong("expires_in", 3600L)
        val expiresAt = obj.optLong("expires_at", 0L).takeIf { it > 0 }?.let { it * 1000L }
            ?: (System.currentTimeMillis() + expiresIn * 1000L)
        return SupabaseSession(
            accessToken = obj.getString("access_token"),
            refreshToken = obj.optString("refresh_token"),
            expiresAt = expiresAt,
            userId = user.optString("id"),
            email = user.optString("email").ifBlank { fallbackEmail }
        )
    }
}
