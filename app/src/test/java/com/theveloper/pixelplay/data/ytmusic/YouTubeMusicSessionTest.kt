package com.theveloper.pixelplay.data.ytmusic

import com.theveloper.pixelplay.data.accounts.AccountVault
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class YouTubeMusicSessionTest {
    @Test fun `accepts raw cookies header blocks and browser JSON`() {
        assertEquals("abc=def", YouTubeMusicSession.parse("SAPISID=abc=def; SID=xyz").sapisid)
        val headers = YouTubeMusicSession.parse("Cookie: SAPISID=abc; SID=xyz\r\nX-Goog-AuthUser: 2")
        assertEquals("2", headers.account)
        assertEquals("abc", headers.sapisid)
        val json = YouTubeMusicSession.parse("""{"Cookie":"__Secure-3PAPISID=abc","X-Goog-AuthUser":"1"}""")
        assertEquals("1", json.account)
        assertEquals("abc", json.sapisid)
    }

    @Test fun `rejects empty signing cookies malformed JSON and header injection`() {
        for (input in listOf("SAPISID=", "SID=xyz", "SAPISID=abc\nInjected=yes", "{broken",
            """{"Cookie":"SAPISID=abc\r\nX-Evil: yes"}""")) {
            assertThrows(IllegalArgumentException::class.java) { YouTubeMusicSession.parse(input) }
        }
        assertThrows(IllegalArgumentException::class.java) { YouTubeMusicSession.parse("SAPISID=abc", "100") }
        assertEquals("valid", YouTubeMusicSession.parse("SAPISID=; __Secure-3PAPISID=valid").sapisid)
        assertEquals("secure1", YouTubeMusicSession.parse("SAPISID=; __Secure-1PAPISID=secure1").sapisid)
    }

    @Test fun `failed validation preserves the existing account and library`() = runTest {
        val vault = mockk<AccountVault>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>(relaxed = true)
        val manager = YouTubeMusicAuthManager(vault, preferences)
        for (failure in listOf(java.io.IOException("Offline"), CancellationException("Cancelled"))) {
            try {
                manager.connect("SAPISID=new", "0") { throw failure }
                fail<Unit>("Expected validation failure")
            } catch (actual: Exception) {
                assertEquals(failure::class, actual::class)
                assertEquals(failure.message, actual.message)
            }
        }
        verify(exactly = 0) { vault.put(*anyVararg()) }
        verify(exactly = 0) { vault.clear(any()) }
        coVerify(exactly = 0) { preferences.clearYtMusicCookies() }
    }

    @Test fun `only validated session is published`() = runTest {
        val vault = mockk<AccountVault>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>(relaxed = true)
        val manager = YouTubeMusicAuthManager(vault, preferences)
        manager.connect("Cookie: SAPISID=new\nX-Goog-AuthUser: 3", "0") {
            assertEquals("3", it.account)
            verify(exactly = 0) { vault.put(*anyVararg()) }
        }
        verify { vault.put("yt.cookie" to "SAPISID=new", "yt.account" to "3") }
        coVerify(exactly = 1) { preferences.clearYtMusicCookies() }
    }
}
