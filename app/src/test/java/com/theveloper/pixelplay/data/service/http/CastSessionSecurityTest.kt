package com.theveloper.pixelplay.data.service.http

import com.theveloper.pixelplay.data.service.cast.CastTokenStore
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastSessionSecurityTest {

    @Test
    fun `buildAccessPolicy enforces allowlist only when a device hint exists`() {
        val withHint = CastSessionSecurity.buildAccessPolicy("192.168.1.50")
        assertTrue(withHint.enforceClientAddressAllowlist)
        assertTrue(withHint.allowedClientAddresses.contains("192.168.1.50"))

        val withoutHint = CastSessionSecurity.buildAccessPolicy(null)
        assertFalse(withoutHint.enforceClientAddressAllowlist)
    }

    @Test
    fun `isAuthorizedClientAddress always allows loopback`() {
        val policy = CastAccessPolicy.EMPTY.copy(
            enforceClientAddressAllowlist = true,
            allowedClientAddresses = setOf("192.168.1.50")
        )

        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("127.0.0.1", policy))
        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("::1", policy))
        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("192.168.1.50", policy))
        assertFalse(CastSessionSecurity.isAuthorizedClientAddress("192.168.1.80", policy))
    }

    @Test
    fun `buildSongUrl places the token as a path segment`() {
        val tokenStore = mockk<CastTokenStore> {
            every { generateToken(any(), any()) } returns "tok123"
        }

        val url = CastSessionSecurity.buildSongUrl("http://192.168.1.10:8080", "abc/123", tokenStore)

        assertEquals("http://192.168.1.10:8080/song/tok123", url)
    }

    @Test
    fun `buildArtUrl uses the art path`() {
        val tokenStore = mockk<CastTokenStore> {
            every { generateToken(any(), any()) } returns "arttok"
        }

        val url = CastSessionSecurity.buildArtUrl("http://192.168.1.10:8080", "42", tokenStore)

        assertEquals("http://192.168.1.10:8080/art/arttok", url)
    }

    @Test
    fun `buildLoopbackSongUrl rewrites the host to loopback`() {
        val tokenStore = mockk<CastTokenStore> {
            every { generateToken(any(), any()) } returns "tok123"
        }

        val url = CastSessionSecurity.buildLoopbackSongUrl("http://192.168.1.10:8080", "42", tokenStore)

        assertEquals("http://127.0.0.1:8080/song/tok123", url)
    }

    @Test
    fun `redactAuthToken hides the token segment`() {
        assertEquals(
            "http://192.168.1.10:8080/song/<redacted>",
            CastSessionSecurity.redactAuthToken("http://192.168.1.10:8080/song/tok123")
        )
        // URLs without a song/art token segment are left untouched.
        assertEquals(
            "http://192.168.1.10:8080/other",
            CastSessionSecurity.redactAuthToken("http://192.168.1.10:8080/other")
        )
    }
}
