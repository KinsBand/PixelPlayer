package com.theveloper.pixelplay.data.spotify

import com.theveloper.pixelplay.data.accounts.AccountVault
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SpotifyAuthManagerTest {
    private val credentials = ConcurrentHashMap<String, String>().apply {
        put("spotify.client", "old-client")
        put("spotify.access", "old-access")
    }
    private val vault = mockk<AccountVault>().also { v ->
        every { v.revision } returns MutableStateFlow(0L)
        every { v.get(any<String>()) } answers { credentials[firstArg<String>()].orEmpty() }
        every { v.put(*anyVararg<Pair<String, String>>()) } answers {
            firstArg<Array<Pair<String, String>>>().forEach { (key, value) -> credentials[key] = value }
        }
        every { v.clear(any<String>()) } answers { credentials.keys.removeIf { it.startsWith(firstArg<String>()) }; Unit }
    }
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val clientId = "a".repeat(32)

    private fun callback(url: HttpUrl, state: String, tail: String): String {
        val redirect = url.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder()
            .addQueryParameter("state", state)
        val target = redirect.build().toString() + "&" + tail
        val connection = java.net.URI(target).toURL().openConnection().apply {
            connectTimeout = 5000; readTimeout = 5000
        }
        return connection.getInputStream().bufferedReader().use { it.readText() }
    }

    @Test fun `wrong state is ignored and denied consent preserves old account`() = runBlocking {
        val requests = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor {
            requests.incrementAndGet(); error("Token exchange must not run")
        }.build()
        val manager = SpotifyAuthManager(vault, preferences, http)
        try {
            val url = manager.getAuthorizationUrl(clientId).toHttpUrl()
            assertEquals("old-client", credentials["spotify.client"])
            callback(url, "wrong", "code=untrusted")
            val result = callback(url, url.queryParameter("state")!!, "error=access_denied")
            assertTrue(result.contains("not granted"))
            assertEquals(0, requests.get())
            assertEquals("old-access", credentials["spotify.access"])
        } finally { manager.logout() }
    }

    @Test fun `PKCE callback exchanges code using matching verifier and commits new client`() = runBlocking {
        var posted: FormBody? = null
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            posted = chain.request().body as FormBody
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"access_token":"new-access","refresh_token":"refresh","expires_in":3600}""".toResponseBody()).build()
        }.build()
        val manager = SpotifyAuthManager(vault, preferences, http)
        try {
            val url = manager.getAuthorizationUrl(clientId).toHttpUrl()
            val verifier = credentials["spotify.verifier"]!!
            val challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
            assertEquals(challenge, url.queryParameter("code_challenge"))
            assertTrue(callback(url, url.queryParameter("state")!!, "code=accepted").contains("connected"))
            val form = posted!!
            val values = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertEquals(verifier, values["code_verifier"])
            assertEquals(url.queryParameter("redirect_uri"), values["redirect_uri"])
            assertEquals(clientId, credentials["spotify.client"])
            assertEquals("new-access", credentials["spotify.access"])
        } finally { manager.logout() }
    }

    @Test fun `token error reaches browser and account status without overwriting old login`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(400).message("Bad Request")
                .body("""{"error":"invalid_client"}""".toResponseBody()).build()
        }.build()
        val manager = SpotifyAuthManager(vault, preferences, http)
        try {
            val url = manager.getAuthorizationUrl(clientId).toHttpUrl()
            val response = callback(url, url.queryParameter("state")!!, "code=accepted")
            assertTrue(response.contains("client ID"))
            assertEquals(response, manager.status.value)
            assertEquals("old-client", credentials["spotify.client"])
            assertEquals("old-access", credentials["spotify.access"])
        } finally { manager.logout() }
    }
}
