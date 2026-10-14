package com.theveloper.pixelplay.presentation.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Guards the profile picture lookup: the email is hashed (never sent as-is) and normalised first. */
class GravatarUrlTest {
    @Test fun `same address in any case or spacing gives the same url`() {
        assertEquals(gravatarUrl("Someone@Example.com "), gravatarUrl("someone@example.com"))
    }

    @Test fun `url carries a sha-256 hash, not the email`() {
        val url = gravatarUrl("someone@example.com")!!
        assertEquals(false, url.contains("example"))
        val hash = url.substringAfter("/avatar/").substringBefore("?")
        assertEquals(64, hash.length)
        assertEquals(true, url.endsWith("d=404"))
    }

    @Test fun `not an email gives no url`() {
        assertNull(gravatarUrl("not an email"))
    }
}
