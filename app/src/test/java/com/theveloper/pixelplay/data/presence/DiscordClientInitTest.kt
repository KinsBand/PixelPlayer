package com.theveloper.pixelplay.data.presence

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Guards the Discord reconnect: a dropped client (null id) or another app id means re-create it. */
class DiscordClientInitTest {
    @Test fun `no client yet or dropped client needs init`() = assertTrue(needsDiscordClientInit(null, 42L))
    @Test fun `another application id needs init`() = assertTrue(needsDiscordClientInit(7L, 42L))
    @Test fun `same live client is reused`() = assertFalse(needsDiscordClientInit(42L, 42L))
}
