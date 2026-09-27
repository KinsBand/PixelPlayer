package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.database.TempoSearchRow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TempoSearchRowTest {
    @Test fun `saved tempo overrides analyzed estimate`() {
        assertEquals(120.5f, TempoSearchRow(1, """{"bpm":120.5}""", 60f).tempo())
    }
    @Test fun `analysis remains searchable when song metadata has no tempo`() {
        assertEquals(120f, TempoSearchRow(1, null, 120f).tempo())
        assertEquals(120f, TempoSearchRow(1, "{}", 120f).tempo())
        assertEquals(120f, TempoSearchRow(1, "broken json", 120f).tempo())
    }
    @Test fun `unknown and invalid tempos never become matches`() {
        assertNull(TempoSearchRow(1, """{"bpm":null}""", null).tempo())
        assertNull(TempoSearchRow(1, """{"bpm":0}""", -1f).tempo())
        assertNull(TempoSearchRow(1, null, Float.NaN).tempo())
    }
}
